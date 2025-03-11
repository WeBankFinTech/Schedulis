package azkaban.sla;

import static azkaban.Constants.ConfigurationKeys.WTSS_OVERTIME_SCHEDULE_SCAN_INTERNAL;

import azkaban.Constants;
import azkaban.ServiceProvider;
import azkaban.alert.Alerter;
import azkaban.executor.AlerterHolder;
import azkaban.executor.ExecutableFlow;
import azkaban.executor.ExecutableNode;
import azkaban.executor.ExecutorLoader;
import azkaban.executor.ExecutorManagerException;
import azkaban.executor.Status;
import azkaban.executor.entity.JobPredictionExecutionInfo;
import azkaban.project.ProjectLoader;
import azkaban.project.entity.FlowBusiness;
import azkaban.scheduler.Schedule;
import azkaban.scheduler.ScheduleLoader;
import azkaban.server.AbstractAzkabanServer;
import azkaban.trigger.TriggerAction;
import azkaban.trigger.builtin.SlaAlertAction;
import azkaban.utils.JobUtils;
import azkaban.utils.Props;
import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import javax.inject.Inject;
import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.NotNull;
import org.joda.time.DateTime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 */
public class OvertimeScheduleScanner {



    private static final Logger logger = LoggerFactory.getLogger(OvertimeScheduleScanner.class);

    private final ScheduleLoader scheduleLoader;

    private final ExecutorLoader executorLoader;

    private final ProjectLoader projectLoader;

    /**
     * 超时时间点扫描线程
     */
    private ScheduledExecutorService overtimeScheduleScannerThread;

    /**
     * 超时调度扫描线程间隔时间
     */
    private final long overtimeScheduleScanThreadInterval;

    private final boolean predictionEnabled;

    private final long baseAlertTime;

    private final double predictionThreshold;

    private final String busPathalertLevel;

    private final long predictionScanThreadInterval;

    private ScheduledExecutorService predictionScheduleScannerThread;

    private final static String splitter = "_prediction_";

    private final static FlowBusiness emptyFlowBusiness = new FlowBusiness();

    private LoadingCache<JobPredictionExecutionInfo, JobPredictionExecutionInfo> jobPredictionExecutionInfoCache = CacheBuilder
            .newBuilder().maximumSize(1000)
            .expireAfterWrite(200, TimeUnit.MINUTES)
            .build(new CacheLoader<JobPredictionExecutionInfo, JobPredictionExecutionInfo>() {
                @Override
                public JobPredictionExecutionInfo load(@NotNull JobPredictionExecutionInfo jobPredictionExecutionInfo) throws Exception {
                    try {
                        JobPredictionExecutionInfo res = executorLoader.fetchJobPredictionExecutionInfo(jobPredictionExecutionInfo.getProjectId()
                                , jobPredictionExecutionInfo.getFlowId(), jobPredictionExecutionInfo.getJobId());
                        if (null != res) {
                            return res;
                        }
                    } catch (final Exception e) {
                        logger.warn("Failed to get JobPredictionExecutionInfo {} ", jobPredictionExecutionInfo, e);
                    }
                    return jobPredictionExecutionInfo;
                }
            });


    private LoadingCache<String, FlowBusiness> flowBusinessCache = CacheBuilder
            .newBuilder().maximumSize(1000)
            .expireAfterWrite(10, TimeUnit.MINUTES)
            .build(new CacheLoader<String, FlowBusiness>() {
                @Override
                public FlowBusiness load(@NotNull String key) throws Exception {
                    if (StringUtils.isBlank(key)) {
                        return emptyFlowBusiness;
                    }
                    try {
                        String[] projectAndFlow = key.split(splitter);
                        if (projectAndFlow.length != 2) {
                            return emptyFlowBusiness;
                        }
                        FlowBusiness flowBusiness = projectLoader.getFlowBusiness(Integer.parseInt(projectAndFlow[0]), projectAndFlow[1], null);
                        if (null != flowBusiness) {
                            return flowBusiness;
                        }
                    } catch (final Exception e) {
                        logger.warn("Failed to get FlowBusiness {} ", key, e);

                    }
                    return emptyFlowBusiness;
                }
            });

    /**
     * 告警插件
     */
    private final Alerter mailAlerter;


    @Inject
    public OvertimeScheduleScanner(final ScheduleLoader loader, final ExecutorLoader executorLoader, final ProjectLoader projectLoader) {
        this.scheduleLoader = loader;
        this.executorLoader = executorLoader;
        this.projectLoader = projectLoader;
        Props azkabanProperties = AbstractAzkabanServer.getAzkabanProperties();
        overtimeScheduleScanThreadInterval = azkabanProperties.getLong(
                WTSS_OVERTIME_SCHEDULE_SCAN_INTERNAL,
                10 * 60 * 1000);

        this.predictionEnabled = azkabanProperties.getBoolean(Constants.ConfigurationKeys.WTSS_PREDICTION_TIMEOUT_ENABLED, true);
        this.baseAlertTime = azkabanProperties.getLong(Constants.ConfigurationKeys.WTSS_PREDICTION_TIMEOUT_BASE_TIME, 15 * 60 * 1000);
        this.predictionThreshold = azkabanProperties.getDouble(Constants.ConfigurationKeys.WTSS_PREDICTION_TIMEOUT_THRESHOLD_RATIO, 1.4);
        this.busPathalertLevel = azkabanProperties.getString(
            Constants.ConfigurationKeys.WTSS_PREDICTION_TIMEOUT_BUS_PATH_ALERT_LEVEL, "MINOR");
        this.predictionScanThreadInterval = azkabanProperties.getLong(
                Constants.ConfigurationKeys.WTSS_PREDICTION_TIMEOUT_INTERVAL,
                10 * 60 * 1000);

        this.mailAlerter = ServiceProvider.SERVICE_PROVIDER.getInstance(AlerterHolder.class)
            .get("email");
        if (mailAlerter == null) {
            String errorMsg = "Scanning overtime schedules does not succeed, because of not finding alerter. ";
            logger.error(errorMsg);
        }
    }

    public void start() {
        // 在查询服务启动超时调度扫描线程
        logger.info("OvertimeScheduleScanThread is starting");
        this.overtimeScheduleScannerThread = new ScheduledThreadPoolExecutor(1);
        this.overtimeScheduleScannerThread.scheduleAtFixedRate(
                this::overtimeScheduleScanTask, 100, overtimeScheduleScanThreadInterval,
                TimeUnit.MILLISECONDS);
        if (predictionEnabled) {
            this.predictionScheduleScannerThread = new ScheduledThreadPoolExecutor(1);
            this.predictionScheduleScannerThread.scheduleAtFixedRate(this::predictionOvertime, 100, predictionScanThreadInterval, TimeUnit.MILLISECONDS);
        }
    }

    public void  stop() {
        if (null != overtimeScheduleScannerThread) {
            overtimeScheduleScannerThread.shutdownNow();
        }
        if (null != predictionScheduleScannerThread) {
            predictionScheduleScannerThread.shutdownNow();
        }
    }

    private void overtimeScheduleScanTask() {

        try {
            // 获取所有调度
            List<Schedule> allSchedules = scheduleLoader.loadAllSchedules();
            // 遍历调度，选出有效、配置了超时告警的调度进行处理
            overtimeScheduleScan(allSchedules);
        } catch (Exception e) {
            logger.warn("OvertimeScheduleScanTask failed. ", e);
        }
    }

    public void overtimeScheduleScan(List<Schedule> schedules) {
        logger.info("Scanning all schedules, size: {} ", schedules.size());

        for (Schedule schedule : schedules) {
            // 有效调度筛选
            Map<String, Object> otherOptionMap = schedule.getOtherOption();
            boolean isValidFlow = (Boolean) otherOptionMap.getOrDefault("validFlow", true);
            boolean activeFlag = (Boolean) otherOptionMap.getOrDefault("activeFlag", false);
            if (isValidFlow && activeFlag) {
                // 获取配置超时告警时间点的调度，此处的告警配置包含工作流级别以及任务级别
                List<SlaOption> slaOptions = schedule.getSlaOptions();
                if (slaOptions != null && !slaOptions.isEmpty()) {
                    // 存在告警配置
                    for (SlaOption slaOption : slaOptions) {
                        if (slaOption.getInfo().containsKey(SlaOption.INFO_ABS_TIME)) {
                            // 超时告警逻辑
                            try {
                                handleOvertimeSchedule(schedule, slaOption);
                            } catch (ExecutorManagerException e) {
                                logger.warn("Error handling overtime schedule{}", schedule, e);
                            }
                        }
                    }
                }
            }
        }
    }

    private void handleOvertimeSchedule(Schedule schedule, SlaOption slaOption)
            throws ExecutorManagerException {
        String[] absTime = slaOption.getInfo().get(SlaOption.INFO_ABS_TIME).toString()
                .split(":");
        DateTime time = new DateTime().withHourOfDay(Integer.parseInt(absTime[0]))
                .withMinuteOfHour(Integer.parseInt(absTime[1]));
        //获取调度工作流最近一次执行
        List<ExecutableFlow> executableFlows = executorLoader.fetchFlowHistory(
                schedule.getProjectId(), schedule.getFlowName());
        if (executableFlows != null && !executableFlows.isEmpty()) {
            // 有工作流执行记录，取工作流最近一次执行
            ExecutableFlow recentExecutableFlow = executableFlows.get(0);
            if (time.isBeforeNow()) {
                // 配置的超时时间未超过当前时间，即当前时间已经超过了设置的超时时间，需要检查工作流状态，进行告警
                logger.debug("handling overtime schedule{}", schedule);
                int execId = recentExecutableFlow.getExecutionId();
                final List<TriggerAction> actions = createActions(slaOption, execId);
                for (final TriggerAction action : actions) {
                    try {
                        if (!Status.isStatusFinished(recentExecutableFlow.getStatus())) {
                            action.doAction();
                        }
                    } catch (final Exception e) {
                        logger.warn("Failed to do action " + action.getDescription()
                                + " for flow " + schedule.getFlowName(), e);
                    }
                }
            }
        }
    }
    private List<TriggerAction> createActions(final SlaOption sla, int execId) {
        final List<TriggerAction> actions = new ArrayList<>();
        final List<String> slaActions = sla.getActions();
        for (final String act : slaActions) {
            TriggerAction action = null;
            if (act.equals(SlaOption.ACTION_ALERT)) {
                action = new SlaAlertAction(SlaOption.ACTION_ALERT, sla, execId,
                        SlaOption.INFO_ABS_TIME);
            } else {
                logger.info("Unknown action type " + act);
            }
            if (action != null) {
                actions.add(action);
            }
        }
        return actions;
    }


    public void predictionOvertime() {

        List<ExecutableFlow> executableFlows = null;
        try {
            executableFlows = executorLoader.fetchAllUnfinishedFlows();
        } catch (ExecutorManagerException e) {
            logger.warn("Failed to fetch flows", e);
            return;
        }
        logger.info("Start to prediction all running job overtime flows {}", executableFlows.size());

        for(ExecutableFlow executableFlow : executableFlows) {
            String flowId = executableFlow.getFlowId();
            int projectId = executableFlow.getProjectId();
            String projectName = executableFlow.getProjectName();
            List<ExecutableNode> executableNodes = executableFlow.getExecutableNodes();
            for (ExecutableNode executableNode : executableNodes) {
                String jobId = executableNode.getId();
                if (Status.isStatusRunning(executableNode.getStatus())) {
                    long duration = System.currentTimeMillis() - executableNode.getStartTime();
                    if (duration < this.baseAlertTime) {
                        return;
                    }
                    JobPredictionExecutionInfo jobPredictionExecutionKey = new JobPredictionExecutionInfo(projectId, flowId, jobId, 0, 0, 0,0, 0);
                    try {
                        JobPredictionExecutionInfo jobPredictionExecutionInfo = jobPredictionExecutionInfoCache.get(jobPredictionExecutionKey);
                        if (jobPredictionExecutionInfo.getDurationAvg() > 0
                                && jobPredictionExecutionInfo.getDurationMedian() > 0
                                && jobPredictionExecutionInfo.getDurationPercentile() > 0) {
                            if (duration > jobPredictionExecutionInfo.getDurationAvg() * this.predictionThreshold
                                    && duration > jobPredictionExecutionInfo.getDurationMedian() * this.predictionThreshold
                            && duration > jobPredictionExecutionInfo.getDurationPercentile() * this.predictionThreshold) {
                                logger.info("Job {} is running over 2 times of average duration, duration is {}", jobId, duration);
                                FlowBusiness flowBusiness = flowBusinessCache.get(projectId + splitter + flowId);

                                String submitUser = executableFlow.getSubmitUser();
                                String userDep =
                                    executableFlow.getOtherOption().get("alertUserDeparment")
                                        == null ?
                                        executableFlow.getSubmitDepartmentName()
                                        : executableFlow.getOtherOption().get("alertUserDeparment")
                                            + "";
                                String alertMsg = assembleAlertMsg(projectName, flowId, jobId,
                                    submitUser, duration);
                                List<String> alertReceiverList = new ArrayList<>();
                                alertReceiverList.add(submitUser);
                                String alertTitle = String.format("[%s:%s:%s] %s %s", projectName,
                                    flowId, jobId, "WTSS",
                                    "任务执行超时预警");

                                if (JobUtils.isBusPath(flowBusiness.getBusResLvl())){
                                    // busPathAlertLevel
                                    this.mailAlerter.sendAlert(alertReceiverList, userDep,
                                        alertTitle, alertMsg, busPathalertLevel);
                                } else {
                                    //minor
                                    this.mailAlerter.sendAlert(alertReceiverList, userDep,
                                        alertTitle, alertMsg, "MINOR");
                                }
                            }
                        }
                    } catch (Exception e) {
                        logger.warn("Failed to get job prediction execution info for flowId: {}, jobId: {}", flowId, jobId, e);
                    }
                }

            }
        }

    }

    private String assembleAlertMsg(String projectName, String flowName, String jobName,
        String submitUser,
        long duration) {

        MessageFormat messageFormat = new MessageFormat(
            "项目 {0} 工作流 {1} 中的任务 {2} 运行时长超过了平均时长的1.4倍！当前执行时长为 {3} ms。工作流提交人：{4}。");

        return messageFormat.format(new Object[]{projectName, flowName,
            jobName, duration, submitUser});
    }
}
