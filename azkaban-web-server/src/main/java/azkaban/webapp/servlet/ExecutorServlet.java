/*
 * Copyright 2012 LinkedIn Corp.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */

package azkaban.webapp.servlet;

import static azkaban.Constants.ConfigurationKeys.WTSS_JOB_LOG_DIAGNOSIS_SCRIPT_PATH;
import static azkaban.ServiceProvider.SERVICE_PROVIDER;

import azkaban.Constants;
import azkaban.alert.Alerter;
import azkaban.batch.HoldBatchContext;
import azkaban.eventnotify.EventNotifyService;
import azkaban.executor.AlerterHolder;
import azkaban.executor.ConnectorParams;
import azkaban.executor.ExecutableFlow;
import azkaban.executor.ExecutableFlowBase;
import azkaban.executor.ExecutableJobInfo;
import azkaban.executor.ExecutableNode;
import azkaban.executor.ExecutingQueryParam;
import azkaban.executor.ExecutionControllerUtils;
import azkaban.executor.ExecutionCycle;
import azkaban.executor.ExecutionOptions;
import azkaban.executor.ExecutionOptions.FailureAction;
import azkaban.executor.Executor;
import azkaban.executor.ExecutorManagerAdapter;
import azkaban.executor.ExecutorManagerException;
import azkaban.executor.HistoryQueryParam;
import azkaban.executor.Status;
import azkaban.flow.Flow;
import azkaban.flow.FlowUtils;
import azkaban.flow.Node;
import azkaban.flowtrigger.FlowTriggerService;
import azkaban.flowtrigger.TriggerInstance;
import azkaban.history.ExecutionRecover;
import azkaban.i18n.utils.LoadJsonUtils;
import azkaban.jobid.jump.JobIdJumpService;
import azkaban.jobid.relation.JobIdRelationService;
import azkaban.log.LogFilterEntity;
import azkaban.log.diagnosis.entity.JobLogDiagnosis;
import azkaban.log.diagnosis.service.JobLogDiagnosisService;
import azkaban.log.diagnosis.service.JobLogDiagnosisServiceImpl;
import azkaban.project.Project;
import azkaban.project.ProjectManager;
import azkaban.project.ProjectManagerException;
import azkaban.scheduler.EventSchedule;
import azkaban.scheduler.EventScheduleServiceImpl;
import azkaban.scheduler.Schedule;
import azkaban.scheduler.ScheduleManager;
import azkaban.scheduler.ScheduleManagerException;
import azkaban.server.HttpRequestUtils;
import azkaban.server.session.Session;
import azkaban.sla.SlaOption;
import azkaban.system.SystemManager;
import azkaban.system.SystemUserManagerException;
import azkaban.system.common.TransitionService;
import azkaban.system.entity.WebankUser;
import azkaban.system.entity.WtssUser;
import azkaban.trigger.TriggerManager;
import azkaban.trigger.TriggerManagerException;
import azkaban.user.Permission;
import azkaban.user.Permission.Type;
import azkaban.user.SystemUserManager;
import azkaban.user.User;
import azkaban.user.UserManagerException;
import azkaban.utils.AlertUtil;
import azkaban.utils.DateUtils;
import azkaban.utils.ElasticUtils;
import azkaban.utils.ExternalLinkUtils;
import azkaban.utils.FileIOUtils;
import azkaban.utils.FileIOUtils.LogData;
import azkaban.utils.GsonUtils;
import azkaban.utils.HttpUtils;
import azkaban.utils.JSONUtils;
import azkaban.utils.LogErrorCodeFilterUtils;
import azkaban.utils.Pair;
import azkaban.utils.Props;
import azkaban.utils.WebUtils;
import azkaban.webapp.AzkabanWebServer;
import azkaban.webapp.WebMetrics;
import azkaban.webapp.plugin.PluginRegistry;
import azkaban.webapp.plugin.ViewerPlugin;
import com.google.common.collect.Lists;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.reflect.TypeToken;
import com.opencsv.CSVWriter;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.OutputStream;
import java.math.BigDecimal;
import java.sql.SQLException;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.StringJoiner;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.collections4.MapUtils;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang.ArrayUtils;
import org.apache.commons.lang.StringEscapeUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.linkis.errorcode.client.handler.LinkisErrorCodeHandler;
import org.joda.time.DateTime;
import org.joda.time.LocalDate;
import org.joda.time.format.DateTimeFormat;
import org.joda.time.format.DateTimeFormatter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


public class ExecutorServlet extends AbstractLoginAzkabanServlet {

    private static final Logger logger = LoggerFactory.getLogger(ExecutorServlet.class.getName());
    private static final String DATE_PATTERN = "MM/dd/yyyy hh:mm aa";
    public static final String INTERRUPTED_FLOW_FILE_PATH = "wtss.self.health.csv.path";
    public static final String INTERRUPTED_FLOW_FILE_NAME = "/flowInfos_#.csv";
    private static final long serialVersionUID = 1L;
    private WebMetrics webMetrics;
    private ProjectManager projectManager;
    private FlowTriggerService flowTriggerService;
    private ExecutorManagerAdapter executorManagerAdapter;
    private ScheduleManager scheduleManager;
    private TransitionService transitionService;
    private AlerterHolder alerterHolder;

    private EventNotifyService eventNotifyService;

    //历史补采停止集合
    private Map<String, String> repeatStopMap = new HashMap<>();

    private ExecutorService threadPoolService;

    private SystemManager systemManager;

    private JobIdJumpService jobIdJumpService;
    private JobIdRelationService jobIdRelationService;
    private TriggerManager triggerManager;

    private EventScheduleServiceImpl eventScheduleService;

    private HoldBatchContext holdBatchContext;

    private boolean holdBatchSwitch;
    private List<String> holdBatchWhiteList;

    private String interruptedFlowFilePath;

    private boolean checkRealNameSwitch;

    private JobLogDiagnosisService jobLogDiagnosisService;

    @Override
    public void destroy() {
        if (threadPoolService != null) {
            logger.info("shutdown threadPoolService.");
            threadPoolService.shutdown();
        }
    }

    @Override
    public void init(final ServletConfig config) throws ServletException {
        super.init(config);
        final AzkabanWebServer server = (AzkabanWebServer) getApplication();
        this.projectManager = server.getProjectManager();
        this.executorManagerAdapter = server.getExecutorManager();
        this.scheduleManager = server.getScheduleManager();
        this.transitionService = server.getTransitionService();
        this.flowTriggerService = server.getFlowTriggerService();
        this.eventNotifyService = server.getEventNotifyService();
        // TODO: reallocf fully guicify
        this.webMetrics = SERVICE_PROVIDER.getInstance(WebMetrics.class);
        this.jobIdJumpService = SERVICE_PROVIDER.getInstance(JobIdJumpService.class);
        this.jobIdRelationService = SERVICE_PROVIDER.getInstance(JobIdRelationService.class);
        this.alerterHolder = server.getAlerterHolder();
        Props props = executorManagerAdapter.getAzkabanProps();
        this.threadPoolService = new ThreadPoolExecutor(props.getInt(Constants.ALERT_CORE_POOL_SIZE, 5),
                props.getInt(Constants.ALERT_CORE_POOL_MX_SIZE, 10),
                props.getLong(Constants.ALERT_POOL_THREAD_KEEP_ALIVE_MS, 200L), TimeUnit.MILLISECONDS,
                new LinkedBlockingQueue<>(props.getInt(Constants.ALERT_POOL_QUEUE_SIZE, 10)));
        this.interruptedFlowFilePath = props.getString(INTERRUPTED_FLOW_FILE_PATH, "./interruptFlow");
        this.systemManager = transitionService.getSystemManager();
        this.triggerManager = server.getTriggerManager();
        this.eventScheduleService = server.getEventScheduleService();
        this.holdBatchContext = SERVICE_PROVIDER.getInstance(HoldBatchContext.class);
        this.holdBatchSwitch = props.getBoolean("azkaban.holdbatch.switch", false);
        this.holdBatchWhiteList = props.getStringList("hold.batch.whitelist");
        this.checkRealNameSwitch = props.getBoolean("realname.check.switch", true);

        this.jobLogDiagnosisService = SERVICE_PROVIDER.getInstance(
                JobLogDiagnosisServiceImpl.class);
    }

    @Override
    protected void handleGet(final HttpServletRequest req, final HttpServletResponse resp,
                             final Session session) throws ServletException, IOException {
        if ("/executor".equals(req.getRequestURI())) {
            if (hasParam(req, "ajax")) {
                handleAJAXAction(req, resp, session);
            } else if (hasParam(req, "execid")) {
                if (hasParam(req, "job") && !hasParam(req, "downloadLog")) {
                    handleExecutionJobDetailsPage(req, resp, session);
                } else if (hasParam(req, "downloadLog")) {
                    handleDownloadExecLog(req, resp, session);
                } else {
                    handleExecutionFlowPageByExecId(req, resp, session);
                }
            } else if (hasParam(req, "triggerinstanceid")) {
                handleExecutionFlowPageByTriggerInstanceId(req, resp, session);
            } else {
                handleExecutionsPage(req, resp, session);
            }
        } else if ("/recover".equals(req.getRequestURI())) {
            if (hasParam(req, "ajax")) {
                handleAJAXAction(req, resp, session);
            } else {
                ajaxHistoryRecoverPage(req, resp, session);
            }
        }
    }

    // handleAJAXAction 函数对请求参数进行解析
    private void handleAJAXAction(final HttpServletRequest req, final HttpServletResponse resp,
                                  final Session session) throws ServletException, IOException {
        final HashMap<String, Object> ret = new HashMap<>();
        final String ajaxName = getParam(req, "ajax");

        if (hasParam(req, "execid")) {
            final int execid = getIntParam(req, "execid");
            ExecutableFlow exFlow = null;

            try {
                exFlow = this.executorManagerAdapter.getExecutableFlow(execid);
            } catch (final ExecutorManagerException e) {
                ret.put("error",
                        "Error fetching execution '" + execid + "': " + e.getMessage());
            }

            if (exFlow == null) {
                ret.put("error", "Cannot find execution '" + execid + "'");
            } else {
                if ("fetchexecflow".equals(ajaxName)) {
                    ajaxFetchExecutableFlow(req, resp, ret, session.getUser(), exFlow);
                } else if ("fetchexecflowupdate".equals(ajaxName)) {
                    ajaxFetchExecutableFlowUpdate(req, resp, ret, session.getUser(),
                            exFlow);
                } else if ("cancelFlow".equals(ajaxName)) {
                    ajaxCancelFlow(req, resp, ret, session.getUser(), exFlow);
                } else if ("superKillFlow".equals(ajaxName)) {
                    ajaxSuperKillFlow(req, resp, ret, session.getUser(), exFlow);
                } else if ("pauseFlow".equals(ajaxName)) {
                    ajaxPauseFlow(req, resp, ret, session.getUser(), exFlow);
                } else if ("resumeFlow".equals(ajaxName)) {
                    ajaxResumeFlow(req, resp, ret, session.getUser(), exFlow);
                    // FIXME Added interface to set job stream to failed state.
                } else if ("ajaxSetFlowFailed".equals(ajaxName)) {
                    ajaxSetFlowFailed(req, resp, ret, session.getUser(), exFlow);
                    // FIXME Added interface to re-run tasks in FAILED_WAITING state.
                } else if ("ajaxRetryFailedJobs".equals(ajaxName)) {
                    ajaxRetryFailedJobs(req, resp, ret, session.getUser(), exFlow);
                    // FIXME Added interface to skip tasks in FAILED_WAITING state.
                } else if ("ajaxSkipFailedJobs".equals(ajaxName)) {
                    ajaxSkipFailedJobs(req, resp, ret, session.getUser(), exFlow);
                    // FIXME Added interface to close task execution.
                } else if ("ajaxDisableJob".equals(ajaxName)) {
                    ajaxDisableJob(req, resp, ret, session.getUser(), exFlow);
                } else if ("ajaxOpenJob".equals(ajaxName)) {
                    ajaxOpenJob(req, resp, ret, session.getUser(), exFlow);
                } else if ("ajaxSetJobFailed".equals(ajaxName)) {
                    ajaxSetJobFailed(req, resp, ret, session.getUser(), exFlow);
                } else if ("fetchExecFlowLogs".equals(ajaxName)) {
                    ajaxFetchExecFlowLogs(req, resp, ret, session.getUser(), exFlow);
                } else if ("fetchExecJobLogs".equals(ajaxName)) {
                    ajaxFetchJobLogs(req, resp, ret, session.getUser(), exFlow);
                    // FIXME Added interface to get the latest bytes of logs.
                } else if ("latestLogOffset".equals(ajaxName)) {
                    ajaxGetJobLatestLogOffset(req, resp, ret, session.getUser(), exFlow);
                    // FIXME Added interface to get job stream running parameters, including global variables for task output.
                } else if ("getOperationParameters".equals(ajaxName)) {
                    ajaxGetOperationParameters(req, resp, ret, session.getUser(), exFlow);
                } else if ("getJobIdRelation".equals(ajaxName)) {
                    ajaxGetJobIdRelation(req, resp, ret, session.getUser(), exFlow);
                } else if ("jumpToLogWebsite".equals(ajaxName)) {
                    ajaxJumpToLogWebsite(req, resp, ret, session.getUser(), session.getSessionId(), exFlow);
                } else if ("getEsLog".equals(ajaxName)) {
                    ajaxGetEsLog(req, resp, ret, session.getUser(), exFlow);
                } else if ("fetchExecJobStats".equals(ajaxName)) {
                    ajaxFetchJobStats(req, resp, ret, session.getUser(), exFlow);
                } else if ("retryFailedJobs".equals(ajaxName)) {
                    ajaxRestartFailed(req, resp, ret, session.getUser(), exFlow);
                    // FIXME Added interface to skip all tasks in FAILED_WAITING state.
                } else if ("skipAllFailedJobs".equals(ajaxName)) {
                    ajaxSkipAllFailedJobs(req, resp, ret, session.getUser(), exFlow);
                } else if ("flowInfo".equals(ajaxName)) {
                    ajaxFetchExecutableFlowInfo(req, resp, ret, session.getUser(), exFlow);
                } else if ("overtimeFlowKill".equals(ajaxName)) {
                    ajaxOvertimeFlowKill(req, resp, ret, session.getUser());
                } else if ("rerunJob".equals(ajaxName)) {
                    ajaxRerunJob(req, resp, ret, session.getUser(), exFlow);
                } else if ("skipFailedJob".equals(ajaxName)) {
                    ajaxSkipFailedJobs(req, resp, ret, session.getUser(), exFlow);
                } else if ("intelligentDiagnosis".equals(ajaxName)) {
                    // 智能诊断
                    failedJobDiagnosis(req, resp, ret, session.getUser(), exFlow);
                }
            }
            // FIXME Added interface to get scheduled job flow information.
        } else if ("fetchscheduledflowgraphNew".equals(ajaxName)) {
            final String projectName = getParam(req, "project");
            final String flowName = getParam(req, "flow");
            ajaxFetchscheduledflowgraphNew(projectName, flowName, ret, session.getUser());
        } else if ("downloadInterruptedFlowRecord".equals(ajaxName)) {
            ajaxDownloadInterruptedFlowRecord(req, resp, ret, session.getUser());
        } else if ("recordRunningFlow".equals(ajaxName)) {
            ajaxRecordRunningFlow(req, resp, ret);
        } else if ("fetchscheduledflowgraph".equals(ajaxName)) {
            final String projectName = getParam(req, "project");
            final String flowName = getParam(req, "flow");
            ajaxFetchScheduledFlowGraph(projectName, flowName, ret, session.getUser());
        } else if ("fetcheventscheduledflowgraph".equals(ajaxName)) {
            final String projectName = getParam(req, "project");
            final String flowName = getParam(req, "flow");
            ajaxFetchEventScheduledFlowGraph(projectName, flowName, ret, session.getUser());
        } else if ("reloadExecutors".equals(ajaxName)) {
            ajaxReloadExecutors(req, resp, ret);
        } else if ("enableQueueProcessor".equals(ajaxName)) {
            ajaxUpdateQueueProcessor(req, resp, ret, session.getUser(), true);
        } else if ("disableQueueProcessor".equals(ajaxName)) {
            ajaxUpdateQueueProcessor(req, resp, ret, session.getUser(), false);
        } else if ("getRunning".equals(ajaxName)) {
            final String projectName = getParam(req, "project");
            final String flowName = getParam(req, "flow");
            ajaxGetFlowRunning(req, resp, ret, session.getUser(), projectName,
                    flowName);
        } else if ("flowInfo".equals(ajaxName)) {
            final String projectName = getParam(req, "project");
            final String flowName = getParam(req, "flow");
            ajaxFetchFlowInfo(req, resp, ret, session.getUser(), projectName,
                    flowName);
            // FIXME Added interface to submit historical rerun tasks.
        } else if ("repeatCollection".equals(ajaxName)) {
            ajaxAttRepeatExecuteFlow(req, resp, ret, session.getUser());
            // FIXME Added interface to stop history and rerun ajax method.
        } else if ("stopRepeat".equals(ajaxName)) {
            ajaxStopRepeat(req, resp, ret, session.getUser());
            // FIXME Added interface to get historical rerun data.
        } else if ("historyRecover".equals(ajaxName)) {
            ajaxHistoryRecoverPage(req, resp, session);
            // FIXME Added interface to verify the existence of historical rerun tasks.
        } else if ("recoverParamVerify".equals(ajaxName)) {
            ajaxRecoverParamVerify(req, resp, ret, session.getUser());
            // FIXME Added interface to get information about tasks performed.
        } else if ("fetchexecutionflowgraphNew".equals(ajaxName)) {
            final String projectName = getParam(req, "project");
            final String flowName = getParam(req, "flow");
            ajaxFetchExecutionFlowGraphNew(projectName, flowName, ret, session.getUser());
            // FIXME Added interface to get information about tasks performed.
        } else if ("fetchexecutionflowgraph".equals(ajaxName)) {
            final String projectName = getParam(req, "project");
            final String flowName = getParam(req, "flow");
            ajaxFetchExecutionFlowGraph(req, projectName, flowName, ret, session.getUser());
            // FIXME Added interface to get all currently running job streams.
        } else if ("getExecutingFlowData".equals(ajaxName)) {
            ajaxGetExecutingFlowData(req, resp, ret, session.getUser());
            // FIXME Added interface to execute all job streams under the project.
        } else if ("executeAllFlow".equals(ajaxName)) {
            executeAllFlow(req, resp, ret, session.getUser());
            // FIXME Added interface to get all historical rerun task information.
        } else if ("ajaxExecuteAllHistoryRecoverFlow".equals(ajaxName)) {
            ajaxExecuteAllHistoryRecoverFlow(req, resp, ret, session.getUser());
            // FIXME Added interface to submit loop execution workflow.
        } else if ("submitCycleFlow".equals(ajaxName)) {
            ajaxSubmitCycleFlow(req, resp, ret, session.getUser());
            // FIXME Added interface to stop cyclic execution of workflow.
        } else if ("stopCycleFlow".equals(ajaxName)) {
            ajaxStopCycleFlow(req, resp, ret, session.getUser());
            // FIXME Added an interface to verify that the cyclic execution task already exists.
        } else if ("cycleParamVerify".equals(ajaxName)) {
            ajaxCycleParamVerify(req, resp, ret, session.getUser());
            // FIXME Added interface to perform cyclic execution tasks.
        } else if ("executeFlowCycleFromExecutor".equals(ajaxName)) {
            final String projectId = getParam(req, "projectId");
            final String flowId = getParam(req, "flow");
            try {
                String cycleFlowSubmitUserName = getParam(req, "cycleFlowSubmitUser");
                SystemUserManager systemUserManager = (SystemUserManager) (transitionService.getUserManager());
                User user = systemUserManager.getUser(cycleFlowSubmitUserName);
                ajaxAttemptExecuteFlow(req, resp, ret, user);
                if (ret.get("error") != null && ret.get("code") == null) {
                    logger.error("submit cycle flow failed: " + ret.get("error"));
                    updateCycleFlowKilled(projectId, flowId);
                }
            } catch (UserManagerException e) {
                ret.put("error", "executeFlowCycleFromExecutor error, fetch execute user failed");
                updateCycleFlowKilled(projectId, flowId);
            }
        }
        // FIXME Added interface to get job logs.
        else if ("extGetRecentJobLog".equals(ajaxName)) {
            extGetRecentJobLog(req, resp, ret, session.getUser());
            // FIXME Added interface to get job running status.
        } else if ("extGetRecentJobStatus".equals(ajaxName)) {
            extGetRecentJobStatus(req, resp, ret, session.getUser());
            // FIXME Added interface to submit a single execution task.
        } else if ("extExecuteFlow".equals(ajaxName)) {
            extExecuteFlow(req, resp, ret, session.getUser());
            // FIXME Added interface to terminate job stream.
        } else if ("extCancelFlow".equals(ajaxName)) {
            extCancelFlow(req, resp, ret, session.getUser());
        } else if ("reloadWebData".equals(ajaxName)) {
            try {
                reloadWebData(req);
            } catch (TriggerManagerException | ScheduleManagerException e) {
                logger.error(e.getMessage(), e);
            }
        } else if ("fetchFailedJobLog".equals(ajaxName)) {
            ajaxFetchFailedJobLog(req, resp, ret, session.getUser());
        } else if ("linkJobHook".equals(ajaxName)) {
            ajaxHandleLinkJobHook(req, resp, ret, session);
        } else if ("fetchFlowsReportMetrics".equals(ajaxName)) {
            // 获取工作流失败率统计
            ajaxFetchFlowsReportMetrics(req, session.getUser(), ret);
        } else if ("getTenantRunningFlows".equals(ajaxName)) {
            ajaxGetTenantRunningFlows(req, session.getUser(), ret);
        } else {
            final String projectName = getParam(req, "project");

            ret.put("project", projectName);
            if ("executeFlow".equals(ajaxName)) {
                ajaxAttemptExecuteFlow(req, resp, ret, session.getUser());
            }
            //容灾重跑
            if ("disasterToleranceRetry".equals(ajaxName)) {

                ajaxAttemptExecuteFlow(req, resp, ret, session.getUser());
            }
        }
        if (ret != null) {
            this.writeJSON(resp, ret);
        }
    }

    private void failedJobDiagnosis(HttpServletRequest req, HttpServletResponse resp,
                                    HashMap<String, Object> ret, User user, ExecutableFlow exFlow)
            throws ServletException {
        // 1. 参数校验
        final String jobId = this.getParam(req, "jobId");
        try {
            final ExecutableNode node = exFlow.getExecutableNodePath(jobId);
            if (node == null) {
                ret.put("error", "Job " + jobId + " doesn't exist in " + exFlow.getExecutionId());
                return;
            }
            final int attempt = this.getIntParam(req, "attempt", node.getAttempt());
            resp.setCharacterEncoding("utf-8");
            // 2. 查询DB
            JobLogDiagnosis jobLogDiagnosis = this.jobLogDiagnosisService.getJobLogDiagnosis(
                    exFlow.getExecutionId(), jobId, attempt);
            if (jobLogDiagnosis != null && StringUtils.isNotEmpty(jobLogDiagnosis.getLog())) {
                String diagnosisLog = jobLogDiagnosis.getLog();
                ret.put("data", StringEscapeUtils.escapeHtml(diagnosisLog));
                return;
            }
            // 3. 调用智能诊断工具
            Props prop = getApplication().getServerProps();
            String diagnosisScriptPath = prop.getString(WTSS_JOB_LOG_DIAGNOSIS_SCRIPT_PATH,
                    "/appcom/Install/AzkabanInstall/wtss-web/bin/wtss-analyze.sh");

            Map<String, String> diagnosisInfo = this.jobLogDiagnosisService.generateDiagnosisInfo(
                    diagnosisScriptPath, exFlow.getExecutionId(), jobId, attempt);

            if (diagnosisInfo.containsKey("error")) {
                ret.put("error", diagnosisInfo.get("error"));
                return;
            }

            String diagnosisLog = diagnosisInfo.get("data");
            ret.put("data", StringEscapeUtils.escapeHtml(diagnosisLog));
            // 将生成的诊断日志保存到数据库
            int updateResult = 0;
            if (!diagnosisLog.isEmpty()) {
                updateResult = this.jobLogDiagnosisService.updateJobLogDiagnosis(
                        exFlow.getExecutionId(), jobId, attempt, diagnosisLog);
            }

            if (updateResult < 1) {
                ret.put("error", "update result failed");
            }
        } catch (SQLException | IOException | InterruptedException e) {
            ret.put("error", e);
        }
    }


    private void ajaxRecordRunningFlow(HttpServletRequest req, HttpServletResponse resp, HashMap<String, Object> ret) throws ServletException, IOException {
        String hostname = getParam(req, "hostname");
        String executorId = null;
        try {
            executorId = executorManagerAdapter.getExecutorIdByHostname(hostname);
        } catch (ExecutorManagerException e) {
            logger.error("query executor info  failed", e);
            ret.put("error", "query executor info  failed");
            return;
        }
        boolean status = executorManagerAdapter.checkExecutorStatus(Integer.parseInt(executorId));
        if (status) {
            ret.put("error", "executor status is running");
            return;
        }
        ExecutingQueryParam executingQueryParam = new ExecutingQueryParam();
        executingQueryParam.setFuzzySearch(false);
        executingQueryParam.setExecutorId(executorId);
        executingQueryParam.setSearch("");
        executingQueryParam.setPreciseSearch(true);
        executingQueryParam.setFuzzySearch(false);
        executingQueryParam.setProjcontain("");
        executingQueryParam.setFlowcontain("");
        executingQueryParam.setUsercontain("");
        executingQueryParam.setFlowType("");
        executingQueryParam.setRecordRunningFlow(true);
        List<Map<String, String>> runningFlows = executorManagerAdapter.getExectingFlowsData(executingQueryParam);
        ArrayList<String[]> flowInfos = new ArrayList<>();
        LocalDateTime now = LocalDateTime.now();
        String time = now.format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
        Collections.sort(runningFlows, new Comparator<Map<String, String>>() {
            @Override
            public int compare(Map<String, String> o1, Map<String, String> o2) {
                return Integer.parseInt(o1.get("execId")) - Integer.parseInt(o2.get("execId"));
            }
        });
        for (Map<String, String> runningFlow : runningFlows) {
            String[] array = new String[7];
            array[0] = "exec_id: " + runningFlow.get("execId");
            array[1] = "project_id: " + runningFlow.get("projectId");
            array[2] = "flow_id: " + runningFlow.get("flowName");
            array[3] = "submit_user: " + runningFlow.get("submitUser");
            array[4] = "submit_time: " + runningFlow.get("startTime");
            array[5] = "executor_id: " + runningFlow.get("exectorId");
            array[6] = "record_time: " + time;
            flowInfos.add(array);
        }
        String file = "";
        if (!flowInfos.isEmpty()) {
            FileWriter fileWriter = null;
            CSVWriter csvWriter = null;
            try {
                File dir = new File(interruptedFlowFilePath);
                if (!dir.exists()) {
                    dir.mkdirs();
                }
                file = dir + INTERRUPTED_FLOW_FILE_NAME.replace("#", hostname);
                fileWriter = new FileWriter(file, true);
                csvWriter = new CSVWriter(fileWriter);
                csvWriter.writeAll(flowInfos);
            } catch (IOException e) {
                logger.error("write to csv failed", e);
                ret.put("error", "write to csv failed");
            } finally {
                IOUtils.closeQuietly(csvWriter);
                IOUtils.closeQuietly(fileWriter);
            }
        }
        ret.put("filePath", file);
    }

    private void ajaxDownloadInterruptedFlowRecord(HttpServletRequest req, HttpServletResponse resp, HashMap<String, Object> ret, User user) throws ServletException {
        if (!user.getRoles().contains("admin")) {
            ret.put("error", "No Access Permission");
            return;
        }
        String hostname = getParam(req, "hostname");
        BufferedInputStream bufferInStream = null;
        BufferedOutputStream bufferOutStream = null;
        FileInputStream inputStream = null;
        OutputStream outputStream = null;
        try {
            inputStream = new FileInputStream(interruptedFlowFilePath + INTERRUPTED_FLOW_FILE_NAME.replace("#", hostname));
            bufferInStream = new BufferedInputStream(inputStream);
            outputStream = resp.getOutputStream();
            bufferOutStream = new BufferedOutputStream(outputStream);

            resp.setContentType("text/csv");
            String headerKey = "Content-Disposition";
            String headerValue = String.format("attachment; filename=\"flowInfo.csv\"");
            resp.setHeader(headerKey, headerValue);

            final byte[] buffer = new byte[8192];
            int bytesRead = -1;

            while ((bytesRead = bufferInStream.read(buffer)) != -1) {
                bufferOutStream.write(buffer, 0, bytesRead);
            }

        } catch (IOException e) {
            logger.error("download flow file failed", e);
            ret.put("error", "download flow file failed");
        } finally {
            IOUtils.closeQuietly(bufferOutStream);
            IOUtils.closeQuietly(bufferInStream);
            IOUtils.closeQuietly(outputStream);
            IOUtils.closeQuietly(inputStream);
        }

    }

    private void ajaxRerunJob(HttpServletRequest req, HttpServletResponse resp,
                              HashMap<String, Object> ret,
                              User user, ExecutableFlow exFlow) {

        // 处于 hold 批状态中，拒绝操作
        if (this.holdBatchSwitch && StringUtils.isNotEmpty(this.holdBatchContext
                .isInBatch(exFlow.getProjectName(), exFlow.getId(), exFlow.getSubmitUser()))) {
            ret.put("error", "server is holding, reject all operation");
            return;
        }

        // 操作用户权限校验，需拥有项目的执行权限
        final Project project = getProjectAjaxByPermission(ret, exFlow.getProjectId(), user,
                Type.EXECUTE);
        if (project == null) {
            return;
        }

        // 工作流状态校验，需为 Running 状态，包括 running 和 failed_finishing
        Status flowStatus = exFlow.getStatus();
        switch (flowStatus) {
            case FAILED:
            case SUCCEEDED:
                ret.put("error", "Flow has already finished. Please re-execute.");
                return;
            case RUNNING:
            case FAILED_FINISHING:
                break;
            default:
                ret.put("error", "The flow is not running. ");
                return;
        }

        // 任务状态校验，需为 running 状态
        try {
            String response = this.executorManagerAdapter.retryExecutingJobs(exFlow,
                    user.getUserId(),
                    req.getParameter("jobId"));

            if (response == null) {
                ret.put("error", "Request Failed");
                return;
            }

            JsonObject responseJson = JsonParser.parseString(response).getAsJsonObject();
            if (responseJson.has(ConnectorParams.RESPONSE_ERROR)) {
                ret.put(ConnectorParams.RESPONSE_ERROR,
                        responseJson.get(ConnectorParams.RESPONSE_ERROR).getAsString());
            } else if (responseJson.has(ConnectorParams.STATUS_PARAM)) {
                ret.put(ConnectorParams.STATUS_PARAM,
                        responseJson.get(ConnectorParams.STATUS_PARAM).getAsString());
            }
        } catch (Exception e) {
            ret.put("error", e.getMessage());
        }
    }

    private void ajaxHandleLinkJobHook(
            HttpServletRequest req, HttpServletResponse resp, HashMap<String, Object> ret,
            Session session) {

        User user = session.getUser();
        try {
            String jobCode = getParam(req, "jobCode").trim();
            String prefixRules = getParam(req, "prefixRules").trim();
            String suffixRules = getParam(req, "suffixRules").trim();

            String[] jobCodeArray = jobCode.split("/");
            int jobCodeArrayLength = jobCodeArray.length;

            String jobCodePrefix = getApplication().getServerProps()
                    .getString(Constants.JobProperties.JOB_BUS_PATH_CODE_PREFIX);
            String clusterName;
            String projectName;

            String flowId;
            String jobId;

            if (!jobCode.startsWith(jobCodePrefix) || jobCodeArrayLength != 5) {
                ret.put("code", 500);
                ret.put(
                        "error",
                        "Error jobCode pattern, it should be 'WTSS/ClusterCode/projectName/flowId/jobId' ");
                return;
            } else {
                clusterName = jobCodeArray[1];
                projectName = jobCodeArray[2];
                Project project = getProjectAjaxByPermission(ret, projectName, user, Type.READ);
                if (project == null) {
                    ret.put("code", 500);
                    ret.put("error", "Project " + projectName + " dose not exist Or the user " +
                            user.getUserId() + " does not have permission of project " + projectName);
                    return;
                }

                flowId = jobCodeArray[3];
                Flow flow = project.getFlow(flowId);
                if (flow == null) {
                    ret.put("code", 500);
                    ret.put("error", "Flow " + flowId + " does not exist ");
                    return;
                }
                jobId = jobCodeArray[4];
                Node node = flow.getNode(jobId);
                if (node == null) {
                    ret.put("code", 500);
                    ret.put("error", "Job " + jobId + " does not exist ");
                    return;
                }

                this.executorManagerAdapter.linkJobHook(jobCode, prefixRules, suffixRules, user);

                ret.put("code", 200);
                ret.put(
                        "message",
                        "Link prefix rules["
                                + prefixRules
                                + "], suffix rules["
                                + suffixRules
                                + "] to Job{"
                                + jobCode
                                + "}");
            }

        } catch (ServletException e) {
            ret.put("code", 500);
            ret.put("error", e.getMessage());
        } catch (SQLException e) {
            ret.put("code", 500);
            ret.put("error", e.getMessage());
        }
    }

    /**
     * Fetch the log of recently failed job according to jobCode.
     *
     * @param req
     * @param resp
     * @param ret
     * @param user
     */
    private void ajaxFetchFailedJobLog(final HttpServletRequest req, final HttpServletResponse resp,
                                       final HashMap<String, Object> ret, final User user) throws ServletException {

        final String jobCode = this.getParam(req, "jobCode");
        String[] jobCodeArray = jobCode.split("/");
        int jobCodeArrayLength = jobCodeArray.length;
        final String projectName;
        final String flowId;
        final String jobId;

        if (!jobCode.startsWith("WTSS") || jobCodeArrayLength < 4 || jobCodeArrayLength > 5) {
            ret.put("error", "Error jobCode pattern, it should be 'WTSS/(集群名)/projectName/flowId' "
                    + "or 'WTSS/(集群名)/projectName//jobId'");
            return;
        } else {
            projectName = jobCodeArray[2];
            final Project project = getProjectAjaxByPermission(ret, projectName, user, Type.READ);
            if (project == null) {
                return;
            }
            try {
                if (jobCodeArrayLength == 4) {
                    // flow level
                    flowId = jobCodeArray[3];
                    Flow flow = project.getFlow(flowId);
                    if (flow == null) {
                        ret.put("error", "flow " + flowId + " does not exist");
                        return;
                    }
                    getFailedJobLogByFlowLevel(project, flow, ret);
                } else {
                    // job level
                    jobId = jobCodeArray[4];
                    getFailedJobLogByJobLevel(project, jobId, ret);
                }
            } catch (ExecutorManagerException | IOException e) {
                throw new ServletException(e);
            }
        }
    }

    /**
     * Get recently failed job log by jobCode (job level)
     *
     * @param project
     * @param jobId
     * @param ret
     */
    private void getFailedJobLogByJobLevel(Project project, String jobId,
                                           HashMap<String, Object> ret) throws ExecutorManagerException {

        ExecutableJobInfo lastFailedJob = this.executorManagerAdapter
                .getLastFailedJob(project, jobId);
        if (lastFailedJob == null) {
            ret.put("error", "no failed execution for job " + jobId);
            return;
        }

        LogData logData = this.executorManagerAdapter
                .getJobLogDataByJobId(lastFailedJob.getExecId(), jobId, lastFailedJob.getAttempt());
        ret.put("jobId", jobId);
        ret.put("jobLog", logData.getData());

    }

    /**
     * Get recently failed job log by jobCode (flow level)
     *
     * @param project
     * @param flow
     * @param ret
     */
    private void getFailedJobLogByFlowLevel(Project project, Flow flow,
                                            HashMap<String, Object> ret) throws ExecutorManagerException, IOException {
        List<ExecutableFlow> flows = this.executorManagerAdapter
                .getExecutableFlows(project.getId(), flow.getId());
        for (ExecutableFlow exFlow : flows) {
            List<ExecutableNode> exNodes = exFlow.getExecutableNodes();
            HashMap<String, String> failedJobLogMap = new HashMap<>();
            for (ExecutableNode exNode : exNodes) {
                getExecutableNodeLog(exNode, exFlow, failedJobLogMap, exNode.getId());
            }
            if (!failedJobLogMap.isEmpty()) {
                ret.put("log", failedJobLogMap);
                return;
            }
        }
    }

    private void getExecutableNodeLog(ExecutableNode exNode, ExecutableFlow exFlow,
                                      HashMap<String, String> failedJobLogMap, String jobId)
            throws ExecutorManagerException, IOException {
        if (!"flow".equals(exNode.getType())) {
            if (exNode.getStatus().equals(Status.FAILED)) {
                String jobLog = this.executorManagerAdapter
                        .getAllExecutionJobLog(exFlow, jobId, exNode.getAttempt());
                failedJobLogMap.put(jobId, jobLog);
            }
            return;
        } else {
            ExecutableFlowBase flowNode = (ExecutableFlowBase) exNode;
            List<ExecutableNode> exNodes = flowNode.getExecutableNodes();
            for (ExecutableNode node : exNodes) {
                getExecutableNodeLog(node, exFlow, failedJobLogMap, jobId + ":" + node.getId());
            }
        }
    }

    private void updateCycleFlowKilled(String prjectId, String flowId) {
        try {
            ExecutionCycle executionCycle = executorManagerAdapter.getExecutionCycleFlow(prjectId, flowId);
            if (executionCycle != null) {
                ExecutableFlow exFlow = this.executorManagerAdapter.getExecutableFlow(executionCycle.getCurrentExecId());
                executionCycle.setStatus(Status.KILLED);
                executionCycle.setEndTime(System.currentTimeMillis());
                executorManagerAdapter.updateExecutionFlow(executionCycle);
                ExecutionControllerUtils.alertOnCycleFlowInterrupt(exFlow, executionCycle, alerterHolder);
            }
        } catch (ExecutorManagerException e) {
            logger.error(String.format("update cycle flow %s : %s cancel status failed", prjectId, flowId));
        }
    }

    /**
     * 读取executingflowpage.vm及其子页面的国际化资源数据
     *
     * @return
     */
    private Map<String, Map<String, String>> loadExecutingflowpageI18nData() {
        Map<String, Map<String, String>> dataMap = new HashMap<>();
        String languageType = LoadJsonUtils.getLanguageType();
        Map<String, String> executingflowpageMap;
        Map<String, String> subPageMap1;
        Map<String, String> subPageMap2;
        Map<String, String> subPageMap3;
        Map<String, String> subPageMap4;
        if ("zh_CN".equalsIgnoreCase(languageType)) {
            // 添加国际化标签
            executingflowpageMap = LoadJsonUtils.transJson("/conf/azkaban-web-server-zh_CN.json",
                    "azkaban.webapp.servlet.velocity.executingflowpage.vm");

            subPageMap1 = LoadJsonUtils.transJson("/conf/azkaban-web-server-zh_CN.json",
                    "azkaban.webapp.servlet.velocity.nav.vm");

            subPageMap2 = LoadJsonUtils.transJson("/conf/azkaban-web-server-zh_CN.json",
                    "azkaban.webapp.servlet.velocity.flow-schedule-ecution-panel.vm");

            subPageMap3 = LoadJsonUtils.transJson("/conf/azkaban-web-server-zh_CN.json",
                    "azkaban.webapp.servlet.velocity.messagedialog.vm");

            subPageMap4 = LoadJsonUtils.transJson("/conf/azkaban-web-server-zh_CN.json",
                    "azkaban.webapp.servlet.velocity.flowgraphview.vm");
        } else {
            // 添加国际化标签
            executingflowpageMap = LoadJsonUtils.transJson("/conf/azkaban-web-server-en_US.json",
                    "azkaban.webapp.servlet.velocity.executingflowpage.vm");

            subPageMap1 = LoadJsonUtils.transJson("/conf/azkaban-web-server-en_US.json",
                    "azkaban.webapp.servlet.velocity.nav.vm");

            subPageMap2 = LoadJsonUtils.transJson("/conf/azkaban-web-server-en_US.json",
                    "azkaban.webapp.servlet.velocity.flow-schedule-ecution-panel.vm");

            subPageMap3 = LoadJsonUtils.transJson("/conf/azkaban-web-server-en_US.json",
                    "azkaban.webapp.servlet.velocity.messagedialog.vm");

            subPageMap4 = LoadJsonUtils.transJson("/conf/azkaban-web-server-en_US.json",
                    "azkaban.webapp.servlet.velocity.flowgraphview.vm");
        }

        dataMap.put("executingflowpage.vm", executingflowpageMap);
        dataMap.put("nav.vm", subPageMap1);
        dataMap.put("flow-schedule-ecution-panel.vm", subPageMap2);
        dataMap.put("messagedialog.vm", subPageMap3);
        dataMap.put("flowgraphview.vm", subPageMap4);

        return dataMap;
    }

    /**
     * 加载ExecutorServlet中的异常信息等国际化资源
     *
     * @return
     */
    private Map<String, String> loadExecutorServletI18nData() {
        String languageType = LoadJsonUtils.getLanguageType();
        Map<String, String> dataMap;
        if ("zh_CN".equalsIgnoreCase(languageType)) {
            dataMap = LoadJsonUtils.transJson("/conf/azkaban-web-server-zh_CN.json",
                    "azkaban.webapp.servlet.ExecutorServlet");
        } else {
            dataMap = LoadJsonUtils.transJson("/conf/azkaban-web-server-en_US.json",
                    "azkaban.webapp.servlet.ExecutorServlet");
        }
        return dataMap;
    }

    /**
     * <pre>
     * Enables queueProcessor if @param status is true
     * disables queueProcessor if @param status is false.
     * </pre>
     */
    private void ajaxUpdateQueueProcessor(final HttpServletRequest req, final HttpServletResponse resp,
                                          final HashMap<String, Object> returnMap, final User user,
                                          final boolean enableQueue) {
        boolean wasSuccess = false;
        if (HttpRequestUtils.hasPermission(user, Type.ADMIN)) {
            try {
                if (enableQueue) {
                    this.executorManagerAdapter.enableQueueProcessorThread();
                } else {
                    this.executorManagerAdapter.disableQueueProcessorThread();
                }
                returnMap.put(ConnectorParams.STATUS_PARAM, ConnectorParams.RESPONSE_SUCCESS);
                wasSuccess = true;
            } catch (final ExecutorManagerException e) {
                returnMap.put(ConnectorParams.RESPONSE_ERROR, e.getMessage());
            }
        } else {
            returnMap.put(ConnectorParams.RESPONSE_ERROR, "Only Admins are allowed to update queue processor");
        }
        if (!wasSuccess) {
            returnMap.put(ConnectorParams.STATUS_PARAM, ConnectorParams.RESPONSE_ERROR);
        }
    }

    /**
     * 首次执行或者单次执行的时候调用的接口
     *
     * @param projectName
     * @param flowName
     * @param ret
     * @param user
     * @throws ServletException
     */
    private void ajaxFetchscheduledflowgraphNew(final String projectName, final String flowName,
                                                final HashMap<String, Object> ret, final User user) throws ServletException {
        final Project project = getProjectAjaxByPermission(ret, projectName, user, Type.EXECUTE);
        Map<String, String> stringStringMap = loadExecutorServletI18nData();
        if (project == null) {
            ret.put("error", stringStringMap.get("permissionForAction") + projectName);
            return;
        }
        try {
            final ExecutionOptions executionOptions = new ExecutionOptions();
            final Flow flow = project.getFlow(flowName);
            if (flow == null) {
                ret.put("error", "Flow '" + flowName + "' cannot be found in project " + project);
                return;
            }
            final ExecutableFlow exFlow = new ExecutableFlow(project, flow);
            exFlow.setExecutionOptions(executionOptions);
            ret.put("submitTime", exFlow.getSubmitTime());
            ret.put("submitUser", exFlow.getSubmitUser());
            ret.put("execid", exFlow.getExecutionId());
            ret.put("projectId", exFlow.getProjectId());
            ret.put("project", project.getName());
            FlowUtils.applyDisabledJobs(executionOptions.getDisabledJobs(), exFlow);
            final Map<String, Object> flowObj = getExecutableNodeInfo(project, exFlow, exFlow.getExecutionId());
            ret.putAll(flowObj);
        } catch (final Exception ex) {
            throw new ServletException(ex);
        }
    }

    private void ajaxFetchScheduledFlowGraph(final String projectName, final String flowName,
                                             final HashMap<String, Object> ret, final User user) throws ServletException {
        final Project project = getProjectAjaxByPermission(ret, projectName, user, Type.READ);
        if (project == null) {
            ret.put("error", "Project '" + projectName + "' doesn't exist.");
            return;
        }
        try {
            final Schedule schedule = this.scheduleManager.getSchedule(project.getId(), flowName);
            // 要读取是否存在调度在执行,但是读取这个调度会拿到调度设置的一些旧数据,影响临时执行
            final ExecutionOptions executionOptions = schedule != null ? schedule.getExecutionOptions() : new ExecutionOptions();
            final Flow flow = project.getFlow(flowName);
            if (flow == null) {
                ret.put("error", "Flow '" + flowName + "' cannot be found in project " + project);
                return;
            }
            final ExecutableFlow exFlow = new ExecutableFlow(project, flow);
            exFlow.setExecutionOptions(executionOptions);
            ret.put("submitTime", exFlow.getSubmitTime());
            ret.put("submitUser", exFlow.getSubmitUser());
            ret.put("execid", exFlow.getExecutionId());
            ret.put("projectId", exFlow.getProjectId());
            ret.put("project", project.getName());
            FlowUtils.applyDisabledJobs(executionOptions.getDisabledJobs(), exFlow);
            final Map<String, Object> flowObj = getExecutableNodeInfo(project, exFlow, exFlow.getExecutionId());
            ret.putAll(flowObj);
        } catch (final ScheduleManagerException ex) {
            throw new ServletException(ex);
        }
    }

    private void ajaxFetchEventScheduledFlowGraph(final String projectName, final String flowName,
                                                  final HashMap<String, Object> ret, final User user) throws ServletException {
        final Project project = getProjectAjaxByPermission(ret, projectName, user, Type.EXECUTE);
        if (project == null) {
            ret.put("error", "Project '" + projectName + "' doesn't exist.");
            return;
        }
        try {
            final EventSchedule schedule = this.eventScheduleService.getEventSchedule(
                    project.getId(), flowName);
            // 要读取是否存在调度在执行,但是读取这个调度会拿到调度设置的一些旧数据,影响临时执行
            final ExecutionOptions executionOptions = schedule != null ? schedule.getExecutionOptions() : new ExecutionOptions();
            final Flow flow = project.getFlow(flowName);
            if (flow == null) {
                ret.put("error", "Flow '" + flowName + "' cannot be found in project " + project);
                return;
            }
            final ExecutableFlow exFlow = new ExecutableFlow(project, flow);
            exFlow.setExecutionOptions(executionOptions);
            ret.put("submitTime", exFlow.getSubmitTime());
            ret.put("submitUser", exFlow.getSubmitUser());
            ret.put("execid", exFlow.getExecutionId());
            ret.put("projectId", exFlow.getProjectId());
            ret.put("project", project.getName());
            FlowUtils.applyDisabledJobs(executionOptions.getDisabledJobs(), exFlow);
            final Map<String, Object> flowObj = getExecutableNodeInfo(project, exFlow, exFlow.getExecutionId());
            ret.putAll(flowObj);
        } catch (final ScheduleManagerException ex) {
            throw new ServletException(ex);
        }
    }

    /**
     * 通过请求重新加载执行节点
     *
     * @param req
     * @param resp
     * @param returnMap
     */
    /* Reloads executors from DB and azkaban.properties via executorManager */
    private void ajaxReloadExecutors(final HttpServletRequest req, final HttpServletResponse resp,
                                     final HashMap<String, Object> returnMap) {
        boolean wasSuccess = false;
//        if (HttpRequestUtils.hasPermission(user, Type.ADMIN)) {
//            try {
//                this.executorManagerAdapter.setupExecutors();
//                returnMap.put(ConnectorParams.STATUS_PARAM, ConnectorParams.RESPONSE_SUCCESS);
//                wasSuccess = true;
//            } catch (final ExecutorManagerException e) {
//                returnMap.put(ConnectorParams.RESPONSE_ERROR, "Failed to refresh the executors " + e.getMessage());
//            }
//        } else {
//            returnMap.put(ConnectorParams.RESPONSE_ERROR, "Only Admins are allowed to refresh the executors");
//        }
        try {
            this.executorManagerAdapter.setupExecutors();
            returnMap.put(ConnectorParams.STATUS_PARAM, ConnectorParams.RESPONSE_SUCCESS);
            wasSuccess = true;
        } catch (final ExecutorManagerException e) {
            returnMap.put(ConnectorParams.RESPONSE_ERROR, "Failed to refresh the executors " + e.getMessage());
        }
        if (!wasSuccess) {
            returnMap.put(ConnectorParams.STATUS_PARAM, ConnectorParams.RESPONSE_ERROR);
        }
    }

    @Override
    protected void handlePost(final HttpServletRequest req, final HttpServletResponse resp,
                              final Session session) throws ServletException, IOException {
        if (hasParam(req, "ajax")) {
            handleAJAXAction(req, resp, session);
        }
    }

    private void handleExecutionJobDetailsPage(final HttpServletRequest req, final HttpServletResponse resp,
                                               final Session session) throws ServletException, IOException {
        // FIXME globalization.
        final Page page = newPage(req, resp, session, "azkaban/webapp/servlet/velocity/jobdetailspage.vm");

        String languageType = LoadJsonUtils.getLanguageType();
        Map<String, String> jobdetailspageMap;
        Map<String, String> subPageMap1;
        Map<String, String> subPageMap2;
        Map<String, String> subPageMap3;
        if ("zh_CN".equalsIgnoreCase(languageType)) {
            jobdetailspageMap = LoadJsonUtils.transJson("/conf/azkaban-web-server-zh_CN.json",
                    "azkaban.webapp.servlet.velocity.jobdetailspage.vm");
            subPageMap1 = LoadJsonUtils.transJson("/conf/azkaban-web-server-zh_CN.json",
                    "azkaban.webapp.servlet.velocity.nav.vm");
            subPageMap2 = LoadJsonUtils.transJson("/conf/azkaban-web-server-zh_CN.json",
                    "azkaban.webapp.servlet.velocity.jobdetailsheader.vm");
            subPageMap3 = LoadJsonUtils.transJson("/conf/azkaban-web-server-zh_CN.json",
                    "azkaban.webapp.servlet.velocity.log-auto-refresh-option.vm");
        } else {
            jobdetailspageMap = LoadJsonUtils.transJson("/conf/azkaban-web-server-en_US.json",
                    "azkaban.webapp.servlet.velocity.jobdetailspage.vm");
            subPageMap1 = LoadJsonUtils.transJson("/conf/azkaban-web-server-en_US.json",
                    "azkaban.webapp.servlet.velocity.nav.vm");
            subPageMap2 = LoadJsonUtils.transJson("/conf/azkaban-web-server-en_US.json",
                    "azkaban.webapp.servlet.velocity.jobdetailsheader.vm");
            subPageMap3 = LoadJsonUtils.transJson("/conf/azkaban-web-server-en_US.json",
                    "azkaban.webapp.servlet.velocity.log-auto-refresh-option.vm");
        }

        jobdetailspageMap.forEach(page::add);
        subPageMap1.forEach(page::add);
        subPageMap2.forEach(page::add);
        subPageMap3.forEach(page::add);

        final User user = session.getUser();
        final int execId = getIntParam(req, "execid");
        final String jobId = StringEscapeUtils.escapeHtml(getParam(req, "job"));
        final int attempt = getIntParam(req, "attempt", 0);
        page.add("execid", execId);
        page.add("jobid", jobId);
        page.add("attempt", attempt);

        ExecutableFlow flow = null;
        ExecutableNode node = null;
        final String jobLinkUrl;
        try {
            flow = this.executorManagerAdapter.getExecutableFlow(execId);
            if (flow == null) {
                page.add("errorMsg", "Error loading executing flow " + execId + ": not found.");
                page.render();
                return;
            }

            node = flow.getExecutableNodePath(jobId);
            if (node == null) {
                page.add("errorMsg", "Job " + jobId + " doesn't exist in " + flow.getExecutionId());
                return;
            }

            jobLinkUrl = this.executorManagerAdapter.getJobLinkUrl(flow, jobId, attempt);

            final List<ViewerPlugin> jobViewerPlugins = PluginRegistry.getRegistry().getViewerPluginsForJobType(node.getType());
            page.add("jobViewerPlugins", jobViewerPlugins);
        } catch (final ExecutorManagerException e) {
            page.add("errorMsg", "Error loading executing flow: " + e.getMessage());
            page.render();
            return;
        }

        final int projectId = flow.getProjectId();
        final Project project = getProjectPageByPermission(page, projectId, user, Type.READ);
        if (project == null) {
            page.render();
            return;
        }
        String newJobName = StringUtils.isNotEmpty(node.getSourceNodeId()) ? node.getSourceNodeId() : node.getId();
        page.add("projectName", project.getName());
        page.add("flowid", flow.getId());
        page.add("parentflowid", node.getParentFlow().getFlowId());
        page.add("jobname", node.getId());
        page.add("newJobName", newJobName);
        page.add("jobLinkUrl", jobLinkUrl);
        page.add("jobType", node.getType());

        if (node.getStatus() == Status.FAILED || node.getStatus() == Status.KILLED) {
            page.add("jobFailed", true);
        } else {
            page.add("jobFailed", false);
        }
        page.add("currentlangType", languageType);
        page.render();
    }

    /**
     * 正在运行模块页面
     *
     * @param req
     * @param resp
     * @param session
     * @throws ServletException
     * @throws IOException
     */
    private void handleExecutionsPage(final HttpServletRequest req, final HttpServletResponse resp,
                                      final Session session) throws ServletException, IOException {
        // FIXME globalization.
        final Page page = newPage(req, resp, session, "azkaban/webapp/servlet/velocity/executionspage.vm");

        String languageType = LoadJsonUtils.getLanguageType();
        Map<String, String> executionspageMap;
        Map<String, String> subPageMap1;
        if ("zh_CN".equalsIgnoreCase(languageType)) {
            executionspageMap = LoadJsonUtils.transJson("/conf/azkaban-web-server-zh_CN.json",
                    "azkaban.webapp.servlet.velocity.executionspage.vm");
            subPageMap1 = LoadJsonUtils.transJson("/conf/azkaban-web-server-zh_CN.json",
                    "azkaban.webapp.servlet.velocity.nav.vm");
        } else {
            executionspageMap = LoadJsonUtils.transJson("/conf/azkaban-web-server-en_US.json",
                    "azkaban.webapp.servlet.velocity.executionspage.vm");
            subPageMap1 = LoadJsonUtils.transJson("/conf/azkaban-web-server-en_US.json",
                    "azkaban.webapp.servlet.velocity.nav.vm");
        }

        executionspageMap.forEach(page::add);
        subPageMap1.forEach(page::add);
        page.add("currentlangType", languageType);
        //获取用户权限
        User user = session.getUser();
        Set<String> userRoleSet = new HashSet<>();
        userRoleSet.addAll(user.getRoles());

        final List<Pair<ExecutableFlow, Optional<Executor>>> runningFlows = this.executorManagerAdapter.getActiveFlowsWithExecutor();
        page.add("runningFlows", runningFlows.isEmpty() ? null : runningFlows);

        final List<ExecutableFlow> finishedFlows = this.executorManagerAdapter.getRecentlyFinishedFlows();

        // FIXME Add permission judgment, admin user can view all flow history user user can only view their own flow history.
        if (userRoleSet.contains("admin")) {

            page.add("runningFlows", runningFlows.isEmpty() ? null : runningFlows);

            page.add("recentlyFinished", finishedFlows.isEmpty() ? null : finishedFlows);

        } else if (systemManager.isDepartmentMaintainer(user)) {
            List<Integer> projectIds = systemManager.getMaintainedProjects(user, 1);
            List<Pair<ExecutableFlow, Optional<Executor>>> maintainedRunningFlows = runningFlows.stream()
                    .filter(pair -> projectIds.contains(pair.getFirst().getProjectId())
                            || user.getUserId().equals(pair.getFirst().getSubmitUser()))
                    .collect(Collectors.toList());
            page.add("runningFlows", maintainedRunningFlows.isEmpty() ? null : maintainedRunningFlows);
            List<ExecutableFlow> maintainedFinishedFlows = finishedFlows.stream()
                    .filter(flow -> projectIds.contains(flow.getProjectId())
                            || user.getUserId().equals(flow.getSubmitUser()))
                    .collect(Collectors.toList());
            page.add("recentlyFinished", maintainedFinishedFlows.isEmpty() ? null : maintainedFinishedFlows);
        } else {

            final List<Pair<ExecutableFlow, Optional<Executor>>> userRunningFlows = runningFlows.stream()
                    .filter(pair ->
                            user.getUserId().equals(pair.getFirst().getSubmitUser())
                    ).collect(Collectors.toList());
            page.add("runningFlows", userRunningFlows.isEmpty() ? null : userRunningFlows);
            List<Integer> projectIds = this.executorManagerAdapter.fetchPermissionsProjectId(user.getUserId());

            final List<ExecutableFlow> userFinishedFlows = finishedFlows.stream()
                    .filter(finishFlow ->
                            hasPermission(finishFlow.getProjectId(), projectIds) || user.getUserId().equals(finishFlow.getSubmitUser())
                    )
                    .collect(Collectors.toList());

            page.add("recentlyFinished", userFinishedFlows.isEmpty() ? null : userFinishedFlows);

        }
        page.add("vmutils", new VelocityUtil(this.projectManager));
        page.render();
    }

    private void handleExecutionFlowPageByTriggerInstanceId(final HttpServletRequest req, final HttpServletResponse resp,
                                                            final Session session) throws ServletException, IOException {
        final Page page = newPage(req, resp, session, "azkaban/webapp/servlet/velocity/executingflowpage.vm");
        final User user = session.getUser();
        final String triggerInstanceId = getParam(req, "triggerinstanceid");

        final TriggerInstance triggerInst = this.flowTriggerService.findTriggerInstanceById(triggerInstanceId);

        if (triggerInst == null) {
            page.add("errorMsg", "Error loading trigger instance " + triggerInstanceId + " not found.");
            page.render();
            return;
        }

        // FIXME Load international resources.
        Map<String, Map<String, String>> dataMap = loadExecutingflowpageI18nData();
        dataMap.forEach((vm, data) -> data.forEach(page::add));

        page.add("triggerInstanceId", triggerInstanceId);
        page.add("execid", triggerInst.getFlowExecId());

        final int projectId = triggerInst.getProject().getId();
        final Project project = getProjectPageByPermission(page, projectId, user, Type.READ);

        if (project == null) {
            page.render();
            return;
        }

        addExternalLinkLabel(req, page);

        page.add("projectId", project.getId());
        page.add("projectName", project.getName());
        page.add("flowid", triggerInst.getFlowId());

        final Permission perm = this.getPermissionObject(project, user, Type.ADMIN);

        final boolean adminPerm = perm.isPermissionSet(Type.ADMIN);

        if (perm.isPermissionSet(Type.EXECUTE) || adminPerm) {
            page.add("execPerm", true);
        } else {
            page.add("execPerm", false);
        }
        if (perm.isPermissionSet(Type.SCHEDULE) || adminPerm) {
            page.add("schedulePerm", true);
        } else {
            page.add("schedulePerm", false);
        }
        String languageType = LoadJsonUtils.getLanguageType();

        page.add("currentlangType", languageType);

        page.render();
    }

    private void addExternalLinkLabel(final HttpServletRequest req, final Page page) {
        final Props props = getApplication().getServerProps();
        final String execExternalLinkURL = ExternalLinkUtils.getExternalAnalyzerOnReq(props, req);

        if (execExternalLinkURL.length() > 0) {
            page.add("executionExternalLinkURL", execExternalLinkURL);
            logger.debug("Added an External analyzer to the page");
            logger.debug("External analyzer url: " + execExternalLinkURL);

            final String execExternalLinkLabel =
                    props.getString(Constants.ConfigurationKeys.AZKABAN_SERVER_EXTERNAL_ANALYZER_LABEL, "External Analyzer");
            page.add("executionExternalLinkLabel", execExternalLinkLabel);
            logger.debug("External analyzer label set to : " + execExternalLinkLabel);
        }
    }

    private void handleExecutionFlowPageByExecId(final HttpServletRequest req, final HttpServletResponse resp,
                                                 final Session session) throws ServletException, IOException {
        final Page page = newPage(req, resp, session, "azkaban/webapp/servlet/velocity/executingflowpage.vm");
        final User user = session.getUser();
        final int execId = getIntParam(req, "execid");
        //当前节点的NestedId,如果查看整个工作流,则是空
        final String nodeNestedId = getParam(req, "nodeNestedId", "");
        final String flowId = getParam(req, "flow", null);

        Props props = getApplication().getServerProps();
        String yarnUrl = props.getString("yarn.url", "");
        String jobHistoryUrl = props.getString("job.history.url", "");
        String yarnUsername = props.getString("yarn.username", "hadoop");
        String yarnPassword = props.getString("yarn.password", "");

        page.add("execid", execId);
        page.add("triggerInstanceId", "-1");
        page.add("loginUser", user.getUserId());
        page.add("nodeNestedId", nodeNestedId);
        page.add("yarnUrl", yarnUrl);
        page.add("jobHistoryUrl", jobHistoryUrl);
        page.add("yarnUsername", yarnUsername);
        page.add("yarnPassword", yarnPassword);

        // 加载国际化资源
        Map<String, Map<String, String>> dataMap = loadExecutingflowpageI18nData();
        dataMap.forEach((vm, data) -> data.forEach(page::add));

        ExecutableFlow flow = null;
        try {
            flow = this.executorManagerAdapter.getExecutableFlow(execId);
            if (flow == null) {
                page.add("errorMsg", "Error loading executing flow " + execId + " not found.");
                page.render();
                return;
            }
        } catch (final ExecutorManagerException e) {
            page.add("errorMsg", "Error loading executing flow: " + e.getMessage());
            page.render();
            return;
        }

        final int projectId = flow.getProjectId();
        final Project project = getProjectPageByPermission(page, projectId, user, Type.READ);
        if (project == null) {
            page.render();
            return;
        }

        addExternalLinkLabel(req, page);

        page.add("projectId", project.getId());
        page.add("projectName", project.getName());
        page.add("flowid", flowId == null ? flow.getFlowId() : flowId);

        final Permission perm = this.getPermissionObject(project, user, Type.ADMIN);

        final boolean adminPerm = perm.isPermissionSet(Type.ADMIN);

        if (perm.isPermissionSet(Type.EXECUTE) || adminPerm) {
            page.add("execPerm", true);
        } else {
            page.add("execPerm", false);
        }
        if (perm.isPermissionSet(Type.SCHEDULE) || adminPerm) {
            page.add("schedulePerm", true);
        } else {
            page.add("schedulePerm", false);
        }
        String languageType = LoadJsonUtils.getLanguageType();
        page.add("currentlangType", languageType);
        page.render();
    }

    protected Project getProjectPageByPermission(final Page page, final int projectId,
                                                 final User user, final Permission.Type type) {
        final Project project = this.projectManager.getProject(projectId);

        Map<String, String> dataMap = loadExecutorServletI18nData();

        if (project == null) {
            page.add("errorMsg", dataMap.get("program") + project + dataMap.get("notExist"));
        } else if (!hasPermission(project, user, type)) {
            page.add("errorMsg", "User " + user.getUserId() + " doesn't have " + type.name()
                    + " permissions on " + project.getName());
        } else {
            return project;
        }

        return null;
    }

    protected Project getProjectAjaxByPermission(final Map<String, Object> ret, final String projectName,
                                                 final User user, final Permission.Type type) {
        final Project project = this.projectManager.getProject(projectName);

        Map<String, String> dataMap = loadExecutorServletI18nData();

        if (project == null) {
            ret.put("error", dataMap.get("program") + projectName + dataMap.get("notExist"));
        } else if (!hasPermission(project, user, type)) {
            ret.put("error", "User " + user.getUserId() + " doesn't have " + project.getName() + " of " + type.name()
                    + " permissions, please contact with the project creator.");
        } else {
            return project;
        }

        return null;
    }

    protected Project getProjectAjaxByPermission(final Map<String, Object> ret, final int projectId,
                                                 final User user, final Permission.Type type) {

        final Project project = this.projectManager.getProject(projectId);

        Map<String, String> dataMap = loadExecutorServletI18nData();
        if (project == null) {
            ret.put("error", dataMap.get("program") + projectId + dataMap.get("notExist"));
        } else if (!hasPermission(project, user, type)) {
            ret.put("error", "User '" + user.getUserId() + "' doesn't have " + type.name() + " permissions on " + project.getName());
        } else {
            return project;
        }

        return null;
    }

    private boolean isHolding(ExecutableFlow exFlow, HashMap<String, Object> ret) {
        if (!this.holdBatchSwitch) {
            return false;
        }
        String batchId = this.holdBatchContext
                .isInBatch(exFlow.getProjectName(), exFlow.getId(), exFlow.getSubmitUser());
        if (StringUtils.isNotEmpty(batchId) && !batchId
                .equals(exFlow.getOtherOption().get("lastBatchId") + "")) {
            ret.put("error", "server is holding, reject all operation");
            return true;
        }

        return false;
    }

    private void ajaxRestartFailed(final HttpServletRequest req, final HttpServletResponse resp,
                                   final HashMap<String, Object> ret, final User user,
                                   final ExecutableFlow exFlow) throws ServletException {
        if (isHolding(exFlow, ret)) {
            return;
        }

        final Project project = getProjectAjaxByPermission(ret, exFlow.getProjectId(), user, Type.EXECUTE);
        if (project == null) {
            return;
        }

        if (exFlow.getStatus() == Status.FAILED || exFlow.getStatus() == Status.SUCCEEDED) {
            ret.put("error", "Flow has already finished. Please re-execute.");
            return;
        }

        try {
            JsonObject json = HttpRequestUtils.parseRequestToJsonObject(req);
            this.executorManagerAdapter
                    .retryFailures(exFlow, user.getUserId(),
                            json.get("retryFailedJobs").getAsJsonArray().toString());
        } catch (final ExecutorManagerException e) {
            ret.put("error", e.getMessage());
        }
    }

    private void ajaxSkipAllFailedJobs(final HttpServletRequest req, final HttpServletResponse resp,
                                       final HashMap<String, Object> ret, final User user,
                                       final ExecutableFlow exFlow) throws ServletException {
        if (isHolding(exFlow, ret)) {
            return;
        }

        final Project project = getProjectAjaxByPermission(ret, exFlow.getProjectId(), user, Type.EXECUTE);
        if (project == null) {
            return;
        }

        if (exFlow.getStatus() == Status.FAILED || exFlow.getStatus() == Status.SUCCEEDED) {
            logger.error("Flow has already finished. Please re-execute.");
            ret.put("error", "Flow has already finished. Please re-execute.");
            return;
        }

        try {
            logger.info("execId: {}, user: {} use skip all failed jobs.", exFlow.getExecutionId(), user.getUserId());
            this.executorManagerAdapter.skipAllFailures(exFlow, user.getUserId());
        } catch (final ExecutorManagerException e) {
            ret.put("error", e.getMessage());
        }
    }

    /**
     * Gets the logs through plain text stream to reduce memory overhead.
     */
    private void ajaxFetchExecFlowLogs(final HttpServletRequest req, final HttpServletResponse resp,
                                       final HashMap<String, Object> ret, final User user,
                                       final ExecutableFlow exFlow) throws ServletException {
        final long startMs = System.currentTimeMillis();
        final Project project = getProjectAjaxByPermission(ret, exFlow.getProjectId(), user, Type.READ);
        if (project == null) {
            return;
        }

        final int offset = this.getIntParam(req, "offset");
        final int length = this.getIntParam(req, "length");

        resp.setCharacterEncoding("utf-8");

        //获取当前查看的工作流的执行状态
        ret.put("status", Status.isStatusFinished(exFlow.getStatus()) ? "Finish" : "Running");

        // 日志读取
        try {
            final LogData data = this.executorManagerAdapter.getExecutableFlowLog(exFlow, offset,
                    length);
            if (data == null) {
                ret.put("length", 0);
                ret.put("offset", offset);
                ret.put("data", "");
            } else {
                ret.put("length", data.getLength());
                ret.put("offset", data.getOffset());
                String htmlStr = StringEscapeUtils.escapeHtml(data.getData());
                htmlStr = StringEscapeUtils.unescapeHtml(htmlStr);
                ret.put("data", htmlStr);
            }
        } catch (Exception e) {
            logger.warn("Failed to get flow log", e);
            throw new ServletException("get executed LogData failed {}", e);
        }

        /*
         * We originally consider leverage Drop Wizard's Timer API {@link com.codahale.metrics.Timer}
         * to measure the duration time.
         * However, Timer will result in too many accompanying metrics (e.g., min, max, 99th quantile)
         * regarding one metrics. We decided to use gauge to do that and monitor how it behaves.
         */
        this.webMetrics.setFetchLogLatency(System.currentTimeMillis() - startMs);
    }

    /**
     * 获取 offset = fileSize - len
     *
     * @throws ServletException
     */
    private void ajaxGetJobLatestLogOffset(final HttpServletRequest req, final HttpServletResponse resp,
                                           final HashMap<String, Object> ret, final User user,
                                           final ExecutableFlow exFlow) throws ServletException {
        final Project project = getProjectAjaxByPermission(ret, exFlow.getProjectId(), user, Type.READ);
        if (project == null) {
            return;
        }
        Long len = this.getLongParam(req, "len");
        String jobId = this.getParam(req, "jobId");
        int attempt = this.getIntParam(req, "attempt");
        try {
            Long latestLogOffset = this.executorManagerAdapter.getLatestLogOffset(exFlow, jobId, len, attempt, user);
            ret.put("offset", latestLogOffset);
        } catch (final ExecutorManagerException | IOException e) {
            logger.error("get log offset failed.", e);
            ret.put("error", "get log offset failed, please try again.");
        }
    }

    /**
     * 获取作业流运行参数
     *
     * @throws ServletException
     */
    private void ajaxGetOperationParameters(final HttpServletRequest req, final HttpServletResponse resp,
                                            final HashMap<String, Object> ret, final User user,
                                            final ExecutableFlow exFlow) throws ServletException {
        final Project project = getProjectAjaxByPermission(ret, exFlow.getProjectId(), user, Type.READ);
        if (project == null) {
            return;
        }
        ret.put("flowParams", exFlow.getExecutionOptions().getFlowParameters());
        ret.put("jobOutputGlobalParams", exFlow.getJobOutputGlobalParam());
    }

    /**
     * 获取jobid 关系信息
     *
     * @throws ServletException
     */
    private void ajaxGetJobIdRelation(final HttpServletRequest req, final HttpServletResponse resp,
                                      final HashMap<String, Object> ret, final User user,
                                      final ExecutableFlow exFlow) throws ServletException {
        final Project project = getProjectAjaxByPermission(ret, exFlow.getProjectId(), user, Type.READ);
        if (project == null) {
            return;
        }
        try {
            Integer execId = getIntParam(req, "execid");
            String jobNamePath = getParam(req, "nested_id");
            ret.put("data", jobIdRelationService.getJobIdRelation(execId, jobNamePath));
        } catch (Exception e) {
            logger.error("get job id relation failed", e);
            ret.put("error", e.getMessage());
        }

    }

    /**
     * Jump to other system/page given jobID
     */
    public void ajaxJumpToLogWebsite(final HttpServletRequest req, final HttpServletResponse resp,
                                     final HashMap<String, Object> ret, User user, String sessionId,
                                     final ExecutableFlow exFlow) throws ServletException {
        try {
            String projectName = exFlow.getProjectName();
            String flowName = exFlow.getFlowId();
            String jobName = getParam(req, "jobName");
            String targetName = getParam(req, "targetName");
            String targetId = getParam(req, "targetId");
            if (user == null) {
                String msg = "found no user";
                logger.error("jump to log website failed: {} ", msg);
                ret.put("error", msg);
                return;
            }

            final Project project = getProjectAjaxByPermission(ret, projectName, user, Type.READ);
            if (project == null) {
                String msg = "found no legal project";
                logger.error("jump to log website failed:  {} ", msg);
                ret.put("error", msg);
                return;
            }

            // 获取外部系统URL
            String redirectUrl = jobIdJumpService.getRedirectUrl(targetName, targetId);
            if (StringUtils.isBlank(redirectUrl)) {
                String msg = String.format("failed to find redirect url for targetName: %s, targetId: %s", targetName, targetId);
                logger.error("jump to log website failed:  {} ", msg);
                ret.put("error", msg);
                return;
            }
            ret.put("location", redirectUrl);

            // 获取外部系统Cookie
            Map<String, Object> input = new HashMap<>();
            input.put("targetName", targetName);
            input.put("project", projectName);
            input.put("flowName", flowName);
            input.put("jobName", jobName);
            input.put("session.id", sessionId);
            input.put("submitUser", exFlow.getSubmitUser());
            String cookieString = jobIdJumpService.getRedirectCookieString(targetName, input);
            if (StringUtils.isNotBlank(cookieString)) {
                resp.setHeader("Set-Cookie", cookieString);
            }

            // 获取外部系统Header
            Map<String, String> headerKV = jobIdJumpService.getRedirectHeader(targetName);
            if (headerKV != null) {
                for (Map.Entry<String, String> entry : headerKV.entrySet()) {
                    if (StringUtils.isNotBlank(entry.getKey())) {
                        resp.setHeader(entry.getKey(), entry.getValue());
                    }
                }
            }

        } catch (Exception e) {
            logger.error("jump to log website failed", e);
            ret.put("error", e.getMessage());
        }

    }

    private void ajaxGetEsLog(final HttpServletRequest req, final HttpServletResponse resp,
                              final HashMap<String, Object> ret, final User user,
                              final ExecutableFlow exFlow) throws ServletException {
        final Project project = getProjectAjaxByPermission(ret, exFlow.getProjectId(), user,
                Type.READ);
        if (project == null) {
            return;
        }
        try {
            Props prop = getApplication().getServerProps();
            String execId = getParam(req, "execid", "");
            String jobId = getParam(req, "jobId", "");
            jobId = jobId.substring(jobId.lastIndexOf(":") + 1);
            int start = getIntParam(req, "start", 1);
            int limit = prop.getInt("azkaban.elastic.limit", 3000);
            ret.put("message", ElasticUtils
                    .getEsLog(
                            "log" + "_" + prop.get("azkaban.elastic.prefix") + "_" + jobId + "_" + execId,
                            prop.getStringList("azkaban.elastic.address"), start, limit));
        } catch (Exception e) {
            logger.error("get es log failed", e);
            ret.put("error", e.getMessage());
        }

    }


    /**
     * Gets the logs through ajax plain text stream to reduce memory overhead.
     */
    private void ajaxFetchJobLogs(final HttpServletRequest req, final HttpServletResponse resp,
                                  final HashMap<String, Object> ret, final User user,
                                  final ExecutableFlow exFlow) throws ServletException {
        final Project project = getProjectAjaxByPermission(ret, exFlow.getProjectId(), user, Type.READ);
        if (project == null) {
            return;
        }

        final int offset = this.getIntParam(req, "offset");
        final int length = this.getIntParam(req, "length");

        final String jobId = this.getParam(req, "jobId");
        resp.setCharacterEncoding("utf-8");


        Props prop = getApplication().getServerProps();
        boolean errorLogBtnSwitch = prop.getBoolean("errrorLogBtn.switch", false);
        boolean infoLogRedSwitch = prop.getBoolean("info.log.red.switch", true);

        try {
            final ExecutableNode node = exFlow.getExecutableNodePath(jobId);
            if (node == null) {
                ret.put("error", "Job " + jobId + " doesn't exist in " + exFlow.getExecutionId());
                return;
            }
            //获取当前查看的Job的执行状态
            ret.put("status", Status.isStatusFinished(node.getStatus()) ? "Finish" : "Running");

            final int attempt = this.getIntParam(req, "attempt", node.getAttempt());
            //获取前端传送过来的日志过滤类型
            final String logType = this.getParam(req, "logType");

            // 日志获取
            if ("refresh".equals(logType) || "".equals(logType)) {//不过滤日志
                final LogData data = this.executorManagerAdapter.getExecutionJobLog(exFlow, jobId, offset, length,
                        attempt);
                if (data == null) {
                    ret.put("length", 0);
                    ret.put("offset", offset);
                    ret.put("data", "");
                } else {
                    String logData = data.getData();

                    ret.put("length", data.getLength());
                    ret.put("offset", data.getOffset());
                    String htmlStr = StringEscapeUtils.escapeHtml(logData);
                    // FIXME Mark the exception log in red.
                    String formatLog = LogErrorCodeFilterUtils.handleErrorLogMarkedRed(htmlStr, infoLogRedSwitch);
                    ret.put("data", formatLog);
                }
                // FIXME Filter out error logs or INFO-level logs.
            } else if ("error".equals(logType) || "info".equals(logType)) {
                //获取Job所有日志
                final String data = this.executorManagerAdapter.getAllExecutionJobLog(exFlow, jobId, attempt);
                final List<LogFilterEntity> logFilterList = this.executorManagerAdapter.listAllLogFilter();
                //按级别过滤出需要的日志
                String logData = LogErrorCodeFilterUtils.handleLogDataFilter(data, logType, logFilterList);
                try {
                    LinkisErrorCodeHandler errorCodeHandler = LinkisErrorCodeHandler.getInstance();
                    logData = logData + errorCodeHandler.handle(logData).toString();
                } catch (Throwable e) {
                    logger.error("use LogErrorCodeHandler error , cause by {} ", e);
                }
                ret.put("length", 0);
                ret.put("offset", 0);
                String htmlStr = StringEscapeUtils.escapeHtml(logData);
                ret.put("data", htmlStr);
                // FIXME Filter out the applicationID in the yarn log.
            } else if ("yarn".equals(logType)) {
                //获取Job所有日志
                final String data = this.executorManagerAdapter.getAllExecutionJobLog(exFlow, jobId, attempt);

                //按级别过滤出需要的日志
                String logData = LogErrorCodeFilterUtils.handleYarnLogDataFilter(data);

                ret.put("length", 0);
                ret.put("offset", 0);
                String htmlStr = StringEscapeUtils.escapeHtml(logData);
                ret.put("data", htmlStr);
            }

            ret.put("errorLogBtnSwitch", errorLogBtnSwitch);

            if (hasParam(req, "taskAnalysisFlag")) {
                Object htmlLogStr = ret.get("data");
                if (htmlLogStr != null) {
                    String htmlStr = htmlLogStr.toString().replace("<font color='red'>", "");
                    htmlStr = htmlStr.replace("</font>", "");
                    ret.put("data", htmlStr);
                }

            }
        } catch (final ExecutorManagerException | IOException e) {
            logger.warn("fetch log failed.", e);
            ret.put("error", "fetch log failed, please try again. reason:" + e.getMessage());
        }
    }

    private void ajaxFetchJobStats(final HttpServletRequest req, final HttpServletResponse resp,
                                   final HashMap<String, Object> ret, final User user,
                                   final ExecutableFlow exFlow) throws ServletException {
        final Project project = getProjectAjaxByPermission(ret, exFlow.getProjectId(), user, Type.READ);
        if (project == null) {
            return;
        }

        final String jobId = this.getParam(req, "jobid");
        resp.setCharacterEncoding("utf-8");

        try {
            final ExecutableNode node = exFlow.getExecutableNodePath(jobId);
            if (node == null) {
                ret.put("error", "Job " + jobId + " doesn't exist in " + exFlow.getExecutionId());
                return;
            }

            final List<Object> jsonObj = this.executorManagerAdapter.getExecutionJobStats(exFlow, jobId, node.getAttempt());
            ret.put("jobStats", jsonObj);
        } catch (final ExecutorManagerException e) {
            ret.put("error", "Error retrieving stats for job " + jobId);
            return;
        }
    }

    private void ajaxFetchFlowInfo(final HttpServletRequest req, final HttpServletResponse resp,
                                   final HashMap<String, Object> ret, final User user,
                                   final String projectName, final String flowId) throws ServletException {
        final Project project = getProjectAjaxByPermission(ret, projectName, user, Type.READ);
        if (project == null) {
            return;
        }

        final Flow flow = project.getFlow(flowId);
        if (flow == null) {
            ret.put("error", "Error loading flow. Flow " + flowId + " doesn't exist in " + projectName);
            return;
        }

        ret.put("successEmails", flow.getSuccessEmails());
        ret.put("failureEmails", flow.getFailureEmails());

        Schedule sflow = null;
        try {
            for (final Schedule sched : this.scheduleManager.getSchedules()) {
                if (sched.getProjectId() == project.getId() && sched.getFlowName().equals(flowId)) {
                    sflow = sched;
                    break;
                }
            }
        } catch (final ScheduleManagerException e) {
            // TODO Auto-generated catch block
            throw new ServletException(e);
        }

        if (sflow != null) {
            ret.put("scheduled", sflow.getNextExecTime());
        }
    }

    private void ajaxFetchExecutableFlowInfo(final HttpServletRequest req, final HttpServletResponse resp,
                                             final HashMap<String, Object> ret, final User user,
                                             final ExecutableFlow exflow) throws ServletException {

        final Project project = getProjectAjaxByPermission(ret, exflow.getProjectId(), user, Type.READ);

        if (project == null) {
            return;
        }

        final Flow flow = project.getFlow(exflow.getFlowId());
        if (flow == null) {
            ret.put("error", "Error loading flow. Flow " + exflow.getFlowId() + " doesn't exist in " + exflow.getProjectId());
            return;
        }

        final ExecutionOptions options = exflow.getExecutionOptions();
        ret.put("flowType", exflow.getFlowType());
        ret.put("successEmails", options.getSuccessEmails());
        ret.put("failureEmails", options.getFailureEmails());
        ret.put("flowParam", options.getFlowParameters());
        // FIXME Returns the global variable parameters of the task output.
        ret.put("jobOutputGlobalParam", exflow.getJobOutputGlobalParam());
        ret.put("nsWtss", exflow.getNsWtss());
        // FIXME If it is a historical rerun task, the run_date date is returned and echoed to the job stream parameters.
        if (exflow.getFlowType() == 2 && options.getFlowParameters().get("run_date") == null) {
            setRunDate(ret, exflow);
        }

        final FailureAction action = options.getFailureAction();
        String failureAction = null;
        switch (action) {
            case FINISH_CURRENTLY_RUNNING:
                failureAction = "finishCurrent";
                break;
            case CANCEL_ALL:
                failureAction = "cancelImmediately";
                break;
            case FINISH_ALL_POSSIBLE:
                failureAction = "finishPossible";
                break;
            case FAILED_PAUSE:
                failureAction = "failedPause";
                break;
            default:
        }
        ret.put("failureAction", failureAction);

        ret.put("notifyFailureFirst", options.getNotifyOnFirstFailure());
        ret.put("notifyFailureLast", options.getNotifyOnLastFailure());

        ret.put("failureEmailsOverride", options.isFailureEmailsOverridden());
        ret.put("successEmailsOverride", options.isSuccessEmailsOverridden());

        ret.put("concurrentOptions", options.getConcurrentOption());
        ret.put("pipelineLevel", options.getPipelineLevel());
        ret.put("pipelineExecution", options.getPipelineExecutionId());
        ret.put("queueLevel", options.getQueueLevel());

        final HashMap<String, String> nodeStatus = new HashMap<>();
        for (final ExecutableNode node : exflow.getExecutableNodes()) {
            nodeStatus.put(node.getId(), node.getStatus().toString());
        }
        ret.put("nodeStatus", nodeStatus);
        ret.put("disabled", options.getDisabledJobs());
        ret.put("rerunAction", options.getRerunAction());

        // FIXME Returns alarm information, which is used to prepare to perform page data echo.
        boolean useTimeoutSetting;
        List<SlaOption> slaOptions = exflow.getSlaOptions();
        // Only the non scheduled scheduling type is displayed.
        if (exflow.getFlowType() != 3 && CollectionUtils.isNotEmpty(slaOptions)) {
            useTimeoutSetting = true;
            List<String> slaEmails = new ArrayList<>();
            String type = "FlowSucceed";
            String duration = "";
            String emailAction = "";
            String killAction = "";
            String level = "INFO";
            String slaAlertType = "email";
            for (SlaOption slaOption : slaOptions) {
                slaEmails = (List<String>) slaOption.getInfo().get(SlaOption.INFO_EMAIL_LIST);
                type = slaOption.getType();
                level = slaOption.getLevel();
                slaAlertType = (String) slaOption.getInfo().get(SlaOption.ALERT_TYPE);

                duration = (String) slaOption.getInfo().get(SlaOption.INFO_TIME_SET);
                emailAction = (String) slaOption.getInfo().get(SlaOption.INFO_EMAIL_ACTION_SET);
                killAction = (String) slaOption.getInfo().get(SlaOption.INFO_KILL_FLOW_ACTION_SET);

            }
            ret.put("slaEmails", slaEmails);
            ret.put("ruleType", type);
            ret.put("duration", duration);
            ret.put("emailAction", emailAction);
            ret.put("killAction", killAction);
            ret.put("slaAlertLevel", level);
            ret.put("slaAlertType", slaAlertType);
        } else {
            useTimeoutSetting = false;
            ret.put("slaEmails", null);
        }
        ret.put("useTimeoutSetting", useTimeoutSetting);

        // FIXME Returns the execution failure setting, which is used to prepare the page data for execution.
        Map<String, Object> otherOption = exflow.getOtherOption();

        if (MapUtils.isNotEmpty(otherOption)) {

            String failureAlertLevel = (String) otherOption.get("failureAlertLevel");
            String successAlertLevel = (String) otherOption.get("successAlertLevel");
            ret.put("failureAlertLevel", failureAlertLevel);
            ret.put("successAlertLevel", successAlertLevel);

            List<Map<String, String>> jobFailedRetryOptions = (List<Map<String, String>>) otherOption.get("jobFailedRetryOptions");
            ret.put("jobFailedRetryOptions", jobFailedRetryOptions);

            List<String> jobSkipList = (List<String>) otherOption.get("jobSkipFailedOptions");
            ret.put("jobSkipFailedOptions", jobSkipList);

            ret.put("otherOption", otherOption);
        }

    }

    private void ajaxCancelFlow(final HttpServletRequest req, final HttpServletResponse resp,
                                final HashMap<String, Object> ret, final User user, final ExecutableFlow exFlow)
            throws ServletException {
        final Project project = getProjectAjaxByPermission(ret, exFlow.getProjectId(), user, Type.EXECUTE);
        if (project == null) {
            return;
        }
        try {
            if (hasParam(req, "forceCancel")) {
                final boolean forceCancel = getBooleanParam(req, "forceCancel", false);
                if (forceCancel) {
                    this.executorManagerAdapter.forceCancelFlow(exFlow, user.getUserId() + (StringUtils.isEmpty(user.getNormalUser()) ? "" : "(" + user.getNormalUser() + ")"));
                    logger.info("User {} force cancelled flow {}", user.getUserId(), exFlow.getId());
                    return;
                }
            }
            this.executorManagerAdapter.cancelFlow(exFlow, user.getUserId() + (StringUtils.isEmpty(user.getNormalUser()) ? "" : "(" + user.getNormalUser() + ")"));
        } catch (final ExecutorManagerException e) {
            logger.warn("Failed to kill execFlow {}", exFlow.getExecutionId(), e);
            if (e.getReason() != null && e.getReason() == ExecutorManagerException.Reason.API_INVOKE) {
                logger.info("flow {} cancel get api Exception", exFlow.getExecutionId());
                ret.put("supportForceCancel", true);
            }
            ret.put("error", e.getMessage());
        }
    }

    private void ajaxSuperKillFlow(final HttpServletRequest req, final HttpServletResponse resp,
                                   final HashMap<String, Object> ret, final User user, final ExecutableFlow exFlow)
            throws ServletException {
        final Project project = getProjectAjaxByPermission(ret, exFlow.getProjectId(), user, Type.EXECUTE);
        if (project == null) {
            return;
        }

        try {
            this.executorManagerAdapter.superKillFlow(exFlow, user.getUserId() + (StringUtils.isEmpty(user.getNormalUser()) ? "" : "(" + user.getNormalUser() + ")"));
        } catch (final ExecutorManagerException e) {
            logger.warn("Failed to kill execFlow {}", exFlow.getExecutionId(), e);
            ret.put("error", e.getMessage());
        }
    }

    private void ajaxGetFlowRunning(final HttpServletRequest req,
                                    final HttpServletResponse resp, final HashMap<String, Object> ret, final User user,
                                    final String projectId, final String flowId) throws ServletException {
        final Project project = getProjectAjaxByPermission(ret, projectId, user, Type.EXECUTE);
        if (project == null) {
            return;
        }

        final List<Integer> refs = this.executorManagerAdapter.getRunningFlows(project.getId(), flowId);
        if (!refs.isEmpty()) {
            ret.put("execIds", refs);
        }
    }

    private void ajaxPauseFlow(final HttpServletRequest req, final HttpServletResponse resp,
                               final HashMap<String, Object> ret, final User user, final ExecutableFlow exFlow)
            throws ServletException {
        if (isHolding(exFlow, ret)) {
            return;
        }

        final Project project = getProjectAjaxByPermission(ret, exFlow.getProjectId(), user, Type.EXECUTE);
        if (project == null) {
            return;
        }

        // 获取工作流超时时长设置，请求没有传参则设为 0
        long requestTimeoutMs = getLongParam(req, "timeoutHour", 0) * 60 * 60 * 1000L;

        try {
            this.executorManagerAdapter.pauseFlow(exFlow, user.getUserId(), requestTimeoutMs);
        } catch (final ExecutorManagerException e) {
            logger.warn("Failed to pause execFlow {}", exFlow.getExecutionId(), e);
            ret.put("error", e.getMessage());
        }
    }

    private void ajaxResumeFlow(final HttpServletRequest req, final HttpServletResponse resp,
                                final HashMap<String, Object> ret, final User user, final ExecutableFlow exFlow)
            throws ServletException {
        if (isHolding(exFlow, ret)) {
            return;
        }

        final Project project = getProjectAjaxByPermission(ret, exFlow.getProjectId(), user, Type.EXECUTE);
        if (project == null) {
            return;
        }

        try {
            this.executorManagerAdapter.resumeFlow(exFlow, user.getUserId());
        } catch (final ExecutorManagerException e) {
            logger.warn("Failed to resume execFlow {}", exFlow.getExecutionId(), e);
            ret.put("resume", e.getMessage());
        }
    }

    private void ajaxSetFlowFailed(final HttpServletRequest req, final HttpServletResponse resp,
                                   final HashMap<String, Object> ret, final User user, final ExecutableFlow exFlow)
            throws ServletException {
        final Project project = getProjectAjaxByPermission(ret, exFlow.getProjectId(), user, Type.EXECUTE);
        if (project == null) {
            return;
        }
        try {
            List<Pair<String, String>> paramList = new ArrayList<>();
            paramList.add(new Pair<String, String>("flowFailed", getParam(req, "flowFailed")));
            paramList.add(new Pair<>(ConnectorParams.ACTION_PARAM, ConnectorParams.FLOW_FAILED_ACTION));
            paramList.add(new Pair<>(ConnectorParams.EXECID_PARAM, String.valueOf(exFlow.getExecutionId())));
            paramList.add(new Pair<>(ConnectorParams.USER_PARAM, user.getUserId()));
            this.executorManagerAdapter.setFlowFailed(exFlow, user.getUserId(), paramList);
        } catch (final Exception e) {
            logger.warn("Failed to set failed execFlow {}", exFlow.getExecutionId(), e);
            ret.put("error", e.getMessage());
        }
    }

    private void ajaxSetJobFailed(final HttpServletRequest req, final HttpServletResponse resp,
                                  final HashMap<String, Object> ret, final User user, final ExecutableFlow exFlow) {
        if (isHolding(exFlow, ret)) {
            return;
        }

        final Project project = getProjectAjaxByPermission(ret, exFlow.getProjectId(), user,
                Type.EXECUTE);
        if (project == null) {
            logger.warn("no permission, " + user);
            return;
        }

        ArrayList<Pair<String, String>> paramList = new ArrayList<>();
        try {
            paramList.add(new Pair<>("setJobFailed", getParam(req, "ajax")));
            paramList.add(new Pair<>(ConnectorParams.ACTION_PARAM, ConnectorParams.SET_JOB_FAILED));
            paramList.add(new Pair<>(ConnectorParams.EXECID_PARAM, exFlow.getExecutionId() + ""));
            paramList.add(new Pair<>(ConnectorParams.USER_PARAM, user.getUserId()));
            paramList.add(new Pair<>("setJob", getParam(req, "setJob")));

            String response = "";
            response = this.executorManagerAdapter.setJobFailed(exFlow, user.getUserId(),
                    paramList);
            ret.put("message", response);

        } catch (Exception e) {
            logger.warn("Failed to set failed execFlow execFlow {}", exFlow.getExecutionId(), e);
            ret.put("error", e.getMessage());
        }
    }

    private void ajaxOpenJob(final HttpServletRequest req, final HttpServletResponse resp,
                             final HashMap<String, Object> ret, final User user, final ExecutableFlow exFlow) {
        if (isHolding(exFlow, ret)) {
            return;
        }

        final Project project = getProjectAjaxByPermission(ret, exFlow.getProjectId(), user, Type.EXECUTE);
        if (project == null) {
            logger.error("no permission, " + user);
            return;
        }

        String response = null;
        try {
            JsonObject request = HttpRequestUtils.parseRequestToJsonObject(req);
            response = this.executorManagerAdapter.setJobOpen(exFlow, user.getUserId(), request.toString());
            if (response == null) {
                ret.put("error", "Request Failed");
            }
            JsonObject result = new JsonParser().parse(response).getAsJsonObject();
            if (result.has("error")) {
                ret.put("error", result.get("error").getAsString());
            } else {
                ret.put("openJob", result.get("openJob").getAsString());
                ret.put("status", result.get("status").getAsString());
            }
        } catch (final Exception e) {
            ret.put("error", e.getMessage());
        }

    }

    private void ajaxDisableJob(final HttpServletRequest req, final HttpServletResponse resp,
                                final HashMap<String, Object> ret, final User user, final ExecutableFlow exFlow)
            throws ServletException {
        if (isHolding(exFlow, ret)) {
            return;
        }

        final Project project = getProjectAjaxByPermission(ret, exFlow.getProjectId(), user, Type.EXECUTE);
        if (project == null) {
            logger.error("no permission, " + user);
            return;
        }
        String response = null;
        try {
            JsonObject request = HttpRequestUtils.parseRequestToJsonObject(req);
            response = this.executorManagerAdapter.setJobDisabled(exFlow, user.getUserId(), request.toString());
            if (response == null) {
                ret.put("error", "Request Failed");
            }
            JsonObject result = new JsonParser().parse(response).getAsJsonObject();
            if (result.has("error")) {
                ret.put("error", result.get("error").getAsString());
            }
        } catch (final Exception e) {
            ret.put("error", e.getMessage());
        }
    }

    private void ajaxRetryFailedJobs(final HttpServletRequest req, final HttpServletResponse resp,
                                     final HashMap<String, Object> ret, final User user, final ExecutableFlow exFlow)
            throws ServletException {
        if (isHolding(exFlow, ret)) {
            return;
        }

        final Project project = getProjectAjaxByPermission(ret, exFlow.getProjectId(), user, Type.EXECUTE);
        if (project == null) {
            logger.error("no permission, " + user);
            return;
        }
        String response = null;
        try {
            JsonObject request = HttpRequestUtils.parseRequestToJsonObject(req);
            response = this.executorManagerAdapter.retryFailedJobs(exFlow, user.getUserId(), request.toString());
            if (response == null) {
                ret.put("error", "Request Failed");
            }
            JsonObject result = new JsonParser().parse(response).getAsJsonObject();
            if (result.has("error")) {
                ret.put("error", result.get("error").getAsString());
            }
        } catch (final Exception e) {
            logger.warn("Failed to retry failed execFlow execFlow {}", exFlow.getExecutionId(), e);
            ret.put("error", e.getMessage());
        }
    }

    private void ajaxSkipFailedJobs(final HttpServletRequest req, final HttpServletResponse resp,
                                    final HashMap<String, Object> ret, final User user, final ExecutableFlow exFlow)
            throws ServletException {
        if (isHolding(exFlow, ret)) {
            return;
        }

        final Project project = getProjectAjaxByPermission(ret, exFlow.getProjectId(), user, Type.EXECUTE);
        if (project == null) {
            logger.error("no permission, " + user);
            return;
        }
        String response = null;
        try {
            JsonObject request = HttpRequestUtils.parseRequestToJsonObject(req);
            response = this.executorManagerAdapter.skipFailedJobs(exFlow, user.getUserId(), request.toString());
            if (response == null) {
                ret.put("error", "Request Failed");
            }
            JsonObject result = new JsonParser().parse(response).getAsJsonObject();
            if (result.has("error")) {
                ret.put("error", result.get("error").getAsString());
            }
        } catch (final Exception e) {
            logger.warn("Failed to skip execFlow execFlow {}", exFlow.getExecutionId(), e);
            ret.put("error", e.getMessage());
        }
    }

    private Map<String, Object> getExecutableFlowUpdateInfo(final ExecutableNode node,
                                                            final long lastUpdateTime) {
        final HashMap<String, Object> nodeObj = new HashMap<>();
        if (node instanceof ExecutableFlowBase) {
            final ExecutableFlowBase base = (ExecutableFlowBase) node;
            final ArrayList<Map<String, Object>> nodeList = new ArrayList<>();

            for (final ExecutableNode subNode : base.getExecutableNodes()) {
                final Map<String, Object> subNodeObj = getExecutableFlowUpdateInfo(subNode, lastUpdateTime);
                if (!subNodeObj.isEmpty()) {
                    nodeList.add(subNodeObj);
                }
            }

            if (!nodeList.isEmpty()) {
                nodeObj.put("flow", base.getFlowId());
                nodeObj.put("nodes", nodeList);
            }
        }

        if (node.getUpdateTime() > lastUpdateTime || !nodeObj.isEmpty()) {
            nodeObj.put("id", node.getId());
            nodeObj.put("status", node.getStatus());
            nodeObj.put("startTime", node.getStartTime());
            nodeObj.put("endTime", node.getEndTime());
            nodeObj.put("updateTime", node.getUpdateTime());

            nodeObj.put("attempt", node.getAttempt());
            if (node.getAttempt() > 0) {
                nodeObj.put("pastAttempts", node.getAttemptObjects());
            }
        }

        return nodeObj;
    }

    private Map<String, Object> getExecutableNodeInfo(final Project project, final ExecutableNode node, int executionId) {
        final HashMap<String, Object> nodeObj = new HashMap<>();
        nodeObj.put("id", node.getId());
        nodeObj.put("status", node.getStatus());
        nodeObj.put("startTime", node.getStartTime());
        nodeObj.put("endTime", node.getEndTime());
        nodeObj.put("updateTime", node.getUpdateTime());
        nodeObj.put("type", node.getType());
        if ("eventchecker".equals(node.getType())) {
            Props jobProp = this.projectManager.getProperties(project,
                    project.getFlow(node.getParentFlow().getFlowId()), node.getId(),
                    node.getJobSource());
            if (jobProp != null) {
                nodeObj.put("eventCheckerType", jobProp.get("msg.type"));
            }

        }

        // 存量数据修正
        /*if (node.getParentFlow() != null) {
            if (!node.isAutoDisabled()) {
                Props jobProp = this.projectManager.getProperties(project,
                        project.getFlow(node.getParentFlow().getFlowId()), node.getId(),
                        node.getJobSource());

                if (jobProp != null) {
                    boolean autoDisabled = jobProp.getBoolean("auto.disabled", false);
                    if (autoDisabled) {
                        node.setAutoDisabled(true);
                    }
                }
            }

        }*/
        nodeObj.put("autoDisabled", node.isAutoDisabled());
        nodeObj.put("outer", node.getOuter());
        if (node.getCondition() != null) {
            nodeObj.put("condition", node.getCondition());
        }
        nodeObj.put("nestedId", node.getNestedId());

        nodeObj.put("attempt", node.getAttempt());
        if (node.getAttempt() > 0) {
            nodeObj.put("pastAttempts", node.getAttemptObjects());
        }

        if (node.getInNodes() != null && !node.getInNodes().isEmpty()) {
            nodeObj.put("in", node.getInNodes());
        }

        if (node.getLabel() != null && !node.getLabel().isEmpty()) {
            nodeObj.put("tag", node.getLabel());
        }

        if (node instanceof ExecutableFlowBase) {
            final ExecutableFlowBase base = (ExecutableFlowBase) node;
            final ArrayList<Map<String, Object>> nodeList = new ArrayList<>();

            for (final ExecutableNode subNode : base.getExecutableNodes()) {
                final Map<String, Object> subNodeObj = getExecutableNodeInfo(project, subNode, executionId);
                if (!subNodeObj.isEmpty()) {
                    nodeList.add(subNodeObj);
                }
            }
            nodeObj.put("execid", executionId);
            nodeObj.put("flow", base.getFlowId());
            nodeObj.put("nodes", nodeList);
            nodeObj.put("flowId", base.getFlowId());
        }

        return nodeObj;
    }

    private void ajaxFetchExecutableFlowUpdate(final HttpServletRequest req, final HttpServletResponse resp,
                                               final HashMap<String, Object> ret, final User user,
                                               final ExecutableFlow exFlow) throws ServletException {

        final Long lastUpdateTime = Long.parseLong(getParam(req, "lastUpdateTime"));
        logger.info("Fetching " + exFlow.getExecutionId());

        final Project project = getProjectAjaxByPermission(ret, exFlow.getProjectId(), user, Type.READ);
        if (project == null) {
            return;
        }

        final Map<String, Object> map = getExecutableFlowUpdateInfo(exFlow, lastUpdateTime);
        map.put("status", exFlow.getStatus());
        map.put("startTime", exFlow.getStartTime());
        map.put("endTime", exFlow.getEndTime());
        map.put("updateTime", exFlow.getUpdateTime());
        ret.putAll(map);
    }

    //获取执行Flow列表
    private void ajaxFetchExecutableFlow(final HttpServletRequest req, final HttpServletResponse resp,
                                         final HashMap<String, Object> ret, final User user,
                                         final ExecutableFlow exFlow) throws ServletException {

        logger.info("Fetching " + exFlow.getExecutionId());

        final Project project = getProjectAjaxByPermission(ret, exFlow.getProjectId(), user, Type.READ);
        if (project == null) {
            return;
        }
        Flow flow = getProjectFlow(exFlow);

        ret.put("submitTime", exFlow.getSubmitTime());
        ret.put("submitUser", exFlow.getSubmitUser());
        ret.put("execid", exFlow.getExecutionId());
        ret.put("projectId", exFlow.getProjectId());
        ret.put("project", project.getName());
        //执行策略
        ret.put("executionStrategy", exFlow.getExecutionOptions().getFailureAction().toString());

        Long runDate = 0L;

        Map<String, String> repeatMap = exFlow.getRepeatOption();
        if (exFlow.getRunDate() != null) {
            logger.info("run_date: {}", exFlow.getRunDate());
            DateTimeFormatter dateTimeFormatter = DateTimeFormat.forPattern("yyyyMMdd");
            LocalDate localDate = LocalDate.parse(exFlow.getRunDate(), dateTimeFormatter);
            runDate = localDate.toDate().getTime();
        } else if (!repeatMap.isEmpty()) {

            Long recoverRunDate = Long.valueOf(String.valueOf(repeatMap.get("startTimeLong")));

            org.joda.time.LocalDateTime localDateTime = new org.joda.time.LocalDateTime(new Date(recoverRunDate)).minusDays(1);

            Date date = localDateTime.toDate();

            runDate = date.getTime();

        } else {
            Long startTime = exFlow.getStartTime();
            if (-1 != startTime) {
                org.joda.time.LocalDateTime localDateTime = new org.joda.time.LocalDateTime(new Date(startTime)).minusDays(1);

                Date date = localDateTime.toDate();

                runDate = date.getTime();
            } else {
                runDate = startTime;
            }
        }
        //如果当前查看的是子工作流节点,则只查询整个执行工作流的子工作流
        final String nodeNestedId = getParam(req, "nodeNestedId", "");
        Map<String, String> comments = new HashMap<>();
        if (null != flow) {
            comments = flow.getNodes().stream().filter(o -> StringUtils.isNotBlank(o.getComment())).collect(Collectors.toMap(o -> o.getId(), o -> o.getComment()));
        }
        if (org.apache.commons.lang.StringUtils.isNotBlank(nodeNestedId)) {
            ExecutableNode node = exFlow.getExecutableNodePath(nodeNestedId);
            if (node == null) {
                return;
            }
            final Map<String, Object> flowObj = getExecutableNodeInfo(node, runDate, exFlow.getExecutionId(), comments);
            ret.putAll(flowObj);
            return;
        }
        //查看整个执行工作流节点
        final Map<String, Object> flowObj = getExecutableNodeInfo(exFlow, runDate, exFlow.getExecutionId(), comments);
        ret.putAll(flowObj);
    }

    private Flow getProjectFlow(ExecutableFlow exFlow) {
        Flow flow = null;
        try {
            flow = this.projectManager.getProjectFlow(exFlow.getProjectId(), exFlow.getVersion(), exFlow.getFlowId());
        } catch (final ProjectManagerException e) {
            throw new RuntimeException("Could not load projects flows from store.", e);
        }
        return flow;
    }

    private void setRunDate(Map<String, Object> ret, ExecutableFlow exflow) {
        Map<String, String> repeatMap = exflow.getRepeatOption();
        long recoverRunDate = Long.valueOf(repeatMap.get("startTimeLong"));
        org.joda.time.LocalDateTime localDateTime = new org.joda.time.LocalDateTime(new Date(recoverRunDate)).minusDays(1);
        ((Map<String, String>) ret.get("flowParam")).put("run_date", localDateTime.toString("yyyyMMdd"));
    }

    // 提交工作流前的参数检测
    private void ajaxAttemptExecuteFlow(final HttpServletRequest req, final HttpServletResponse resp,
                                        final HashMap<String, Object> ret, final User user) throws ServletException {
        final String projectName = getParam(req, "project");
        final String flowId = getParam(req, "flow");

        Map<String, String> dataMap = loadExecutorServletI18nData();
        // 检查项目是否存在，工作流基于project这一层级
        final Project project = getProjectAjaxByPermission(ret, projectName, user, Type.EXECUTE);
        if (project == null) {
            return;
        }

        if (project.getProjectLock() == 1) {
            ret.put("error", dataMap.get("program") + projectName + dataMap.get("projectLocked"));
            return;
        }
        // 检查工作流是否存在
        ret.put("flow", flowId);
        final Flow flow = project.getFlow(flowId);
        if (flow == null) {
            ret.put("error", dataMap.get("flow") + flowId + dataMap.get("notExist") + project);
            return;
        }

        ajaxExecuteFlow(req, resp, ret, user);
    }

    /*  //立即执行 Flow 的方法
      private void ajaxExecuteFlow(final HttpServletRequest req,
          final HttpServletResponse resp, final HashMap<String, Object> ret, final User user)
          throws ServletException {
        final String projectName = getParam(req, "project");
        final String flowId = getParam(req, "flow");
        // 检测project是否存在
        final Project project =
            getProjectAjaxByPermission(ret, projectName, user, Type.EXECUTE);
        if (project == null) {
          ret.put("error", "项目 '" + projectName + "' 不存在.");
          return;
        }
        // 检查工作流是否存在
        ret.put("flow", flowId);
        final Flow flow = project.getFlow(flowId);
        if (flow == null) {
          ret.put("error", "工作流 '" + flowId + "' 不存在. "
              + project);
          return;
        }

        final ExecutableFlow exflow = FlowUtils.createExecutableFlow(project, flow);
        exflow.setSubmitUser(user.getUserId());
        //设置代理用户
        exflow.addAllProxyUsers(project.getProxyUsers());
        // 设置执行参数，比如执行成功邮件通知人，执行失败邮件通知人等
        final ExecutionOptions options = HttpRequestUtils.parseFlowOptions(req);
        exflow.setExecutionOptions(options);
        if (!options.isFailureEmailsOverridden()) {
          options.setFailureEmails(flow.getFailureEmails());
        }
        if (!options.isSuccessEmailsOverridden()) {
          options.setSuccessEmails(flow.getSuccessEmails());
        }
        options.setMailCreator(flow.getMailCreator());

        try {
          HttpRequestUtils.filterAdminOnlyFlowParams(this.userManager, options, user);
          final String message =
              this.executorManager.submitExecutableFlow(exflow, user.getUserId());
          ret.put("message", message);
        } catch (final Exception e) {
          e.printStackTrace();
          ret.put("error",
              "工作流 " + exflow.getFlowId() + " 提交失败. " + e.getMessage());
        }

        ret.put("execid", exflow.getExecutionId());
      }
    */
    private void executeAllFlow(final HttpServletRequest req, final HttpServletResponse resp,
                                final HashMap<String, Object> ret, final User user) throws ServletException {
        JsonObject request = HttpRequestUtils.parseRequestToJsonObject(req);
        String projectName = request.get("project").getAsString();
        Project project = getProjectAjaxByPermission(ret, projectName, user, Type.EXECUTE);
        Map<String, String> dataMap = loadExecutorServletI18nData();

        if (project == null) {
            ret.put("error", "Project '" + projectName + "' doesn't exist.");
            logger.error("Project '" + projectName + "' doesn't exist.");
            return;
        }
        if (project.getFlows() == null || project.getFlows().size() == 0) {
            ret.put("error", projectName + dataMap.get("haveNoFlows"));
            logger.error(projectName + ", 没有作业流。");
            return;
        }
        List<Flow> rootFlows = project.getAllRootFlows();
        StringBuilder sb = new StringBuilder();
        for (Flow flow : rootFlows) {
            try {
                execFlow(project, flow, ret, request, user, sb);
            } catch (ServletException se) {
                logger.warn("submit " + flow.getId() + " error." + se);
            }
        }
        ret.put("message", sb.toString());
        ret.put("code", "200");
    }

    private boolean execFlow(Project project, Flow flow, Map<String, Object> ret,
                             JsonObject request, User user, StringBuilder msg) throws ServletException {

        ret.put("flow", flow.getId());

        Map<String, String> dataMap = loadExecutorServletI18nData();

        ExecutableFlow exflow = FlowUtils.createExecutableFlow(project, flow);
        exflow.setSubmitUser(user.getUserId());
        //获取项目默认代理用户
        Set<String> proxyUserSet = project.getProxyUsers();
        //设置用户自己为代理用户
        proxyUserSet.add(user.getUserId());
        //设置提交用户的代理用户
        WtssUser wtssUser = null;
        try {
            wtssUser = transitionService.getSystemUserByUserName(user.getUserId());
        } catch (SystemUserManagerException e) {
            logger.error("get wtssUser failed, caused by: ", e);
        }
        if (wtssUser != null && wtssUser.getProxyUsers() != null) {
            String[] proxySplit = wtssUser.getProxyUsers().split("\\s*,\\s*");
            logger.info("add proxyUsers," + ArrayUtils.toString(proxySplit));
            exflow.addAllProxyUsers(Arrays.asList(proxySplit));
        }

        ExecutionOptions options = null;
        try {
            options = HttpRequestUtils.parseFlowOptions(request);
        } catch (Exception e) {
            ret.put("error", e.getMessage());
            return false;
        }
        final List<String> failureEmails = options.getFailureEmails();
        List<WebankUser> userList = systemManager.findAllWebankUserList(null);
        if (this.checkRealNameSwitch && WebUtils
                .checkEmailNotRealName(failureEmails, options.isFailureEmailsOverridden(), userList)) {
            ret.put("error", "Please configure the correct real-name user for failure email");
            return false;
        }
        final List<String> successEmails = options.getSuccessEmails();
        if (this.checkRealNameSwitch && WebUtils
                .checkEmailNotRealName(successEmails, options.isSuccessEmailsOverridden(), userList)) {
            ret.put("error", "Please configure the correct real-name user for success email");
            return false;
        }
        exflow.setExecutionOptions(options);
        if (!options.isFailureEmailsOverridden()) {
            options.setFailureEmails(flow.getFailureEmails());
        }
        if (!options.isSuccessEmailsOverridden()) {
            options.setSuccessEmails(flow.getSuccessEmails());
        }
        options.setMailCreator(flow.getMailCreator());

        //设置其他参数配置
        Map<String, Object> otherOptions = new HashMap<>();

        //设置失败重跑配置
        List<Map<String, String>> jobRetryList = new ArrayList<>();
        otherOptions.put("jobFailedRetryOptions", jobRetryList);
        exflow.setOtherOption(otherOptions);

        //设置失败跳过配置
        List<String> jobSkipList = new ArrayList<>();
        otherOptions.put("jobSkipFailedOptions", jobSkipList);
        otherOptions.put("jobSkipActionOptions", new ArrayList<String>());
        exflow.setOtherOption(otherOptions);

        //设置通用告警级别
        if (request.has("failureAlertLevel")) {
            otherOptions.put("failureAlertLevel", request.get("failureAlertLevel").getAsString());
        }
        if (request.has("successAlertLevel")) {
            otherOptions.put("successAlertLevel", request.get("successAlertLevel").getAsString());
        }

        //---超时告警设置---
        boolean useTimeoutSetting = false;
        if (request.has("useTimeoutSetting")) {
            useTimeoutSetting = request.get("useTimeoutSetting").getAsBoolean();
        }
        final List<SlaOption> slaOptions = new ArrayList<>();
        if (useTimeoutSetting) {
            String emailStr = "";
            if (request.has("slaEmails")) {
                emailStr = request.get("slaEmails").getAsString();
            }
            final String[] emailSplit = emailStr.split("\\s*,\\s*|\\s*;\\s*|\\s+");
            final List<String> slaEmails = Arrays.asList(emailSplit);
            Map<String, String> settings = new HashMap<>();
            try {
                if (request.has("settings")) {
                    settings = GsonUtils.jsonToJavaObject(request.getAsJsonObject("settings"), new TypeToken<Map<String, String>>() {
                    }.getType());
                }
            } catch (Exception e) {
                logger.error("没有找到超时告警信息");
            }
            //设置SLA 超时告警配置项
            for (final String set : settings.keySet()) {
                final SlaOption sla;
                try {
                    sla = AlertUtil.parseSlaSetting(settings.get(set), flow, project);
                } catch (final Exception e) {
                    logger.error("parse sla setting failed.");
                    msg.append(String.format("Error, flow:%s, msg:%s", flow.getId(), dataMap.get("resolveSlaFailed") + "<br/>"));
                    throw new ServletException(e);
                }
                if (sla != null) {
                    sla.getInfo().put(SlaOption.INFO_FLOW_NAME, flow.getId());
                    sla.getInfo().put(SlaOption.INFO_EMAIL_LIST, slaEmails);
                    sla.getInfo().put(SlaOption.INFO_TIME_SET, sla.getTimeSet());
                    sla.getInfo().put(SlaOption.INFO_EMAIL_ACTION_SET, sla.getEmailAction());
                    sla.getInfo().put(SlaOption.INFO_KILL_FLOW_ACTION_SET, sla.getKillAction());
                    slaOptions.add(sla);
                }
            }
        }
        exflow.setSlaOptions(slaOptions);

        //设置flowType
        //对于单次执行，假如提交的json中含有cycleErrorOption，表示是循环执行，需要设置flowType为4; 设置cycleOption
        if (request.has("cycleErrorOption")) {
            exflow.setFlowType(4);
            HashMap<String, String> cycleOption = new HashMap<>();
            cycleOption.put("cycleErrorOption", request.get("cycleErrorOption").getAsString());
            exflow.setCycleOption(cycleOption);
        }

        // 作业流重试告警
        boolean flowRetryAlertChecked = false;
        String flowRetryAlertLevel = "INFO";
        String alertMsg = "";
        Map<String, Object> flowRetryAlertOption = new HashMap<>();
        if (request.has("flowRetryAlertChecked")) {
            flowRetryAlertChecked = request.get("flowRetryAlertChecked").getAsBoolean();
            flowRetryAlertLevel = request.get("flowRetryAlertLevel").getAsString();
            alertMsg = request.get("alertMsg").getAsString();
        }
        flowRetryAlertOption.put("flowRetryAlertChecked", flowRetryAlertChecked);
        flowRetryAlertOption.put("flowRetryAlertLevel", flowRetryAlertLevel);
        flowRetryAlertOption.put("alertMsg", alertMsg);
        otherOptions.put("flowRetryAlertOption", flowRetryAlertOption);

        try {
            //设置告警用户部门信息
            String userDep = transitionService.getUserDepartmentByUsername(user.getUserId());
            otherOptions.put("alertUserDeparment", userDep);
            HttpRequestUtils.filterAdminOnlyFlowParams(options, user);
            final String message = this.executorManagerAdapter.submitExecutableFlow(exflow, user.getUserId());
            msg.append(String.format("Success, flow:%s, execId:%s .<br/>", flow.getId(), exflow.getExecutionId()));
        } catch (final Exception e) {
            logger.error("submit executableFlow failed, ", e);
            msg.append(String.format("Error, flow:%s, msg:%s .<br/>", flow.getId(), e.getMessage()));
            return false;
        }
        if (flowRetryAlertChecked && exflow.getExecutionId() != -1) {
            logger.info("add alert task to threadpool, current execId " + exflow.getExecutionId());
            this.threadPoolService.execute(() -> eventNotifyService.alertOnFLowStarted(exflow, flow));
        }
        ret.put(flow.getId(), exflow.getExecutionId());
        return true;
    }

    private void ajaxExecuteFlow(final HttpServletRequest req, final HttpServletResponse resp,
                                 final HashMap<String, Object> ret, final User user) throws ServletException {

        final String projectName = getParam(req, "project");
        final String flowId = getParam(req, "flow");
        Map<String, String> dataMap = loadExecutorServletI18nData();

        if (this.holdBatchSwitch && StringUtils.isNotEmpty(this.holdBatchContext
                .isInBatch(projectName, flowId, user.getUserId()))) {
            ret.put("info", "server is holding, reject all operation");
            return;
        }

        final Project project =
                getProjectAjaxByPermission(ret, projectName, user, Type.EXECUTE);
        if (project == null) {
            ret.put("error", "Project '" + projectName + "' doesn't exist.");
            return;
        }

        if (project.getProjectLock() == 1) {
            ret.put("error", dataMap.get("program") + projectName + dataMap.get("projectLocked"));
            return;
        }

        ret.put("flow", flowId);
        final Flow flow = project.getFlow(flowId);
        if (flow == null) {
            ret.put("error", "Flow '" + flowId + "' cannot be found in project "
                    + project);
            return;
        }

        final ExecutableFlow exflow = FlowUtils.createExecutableFlow(project, flow);
        int lastExecId = getIntParam(req, "lastExecId", -1);
        exflow.setLastExecId(lastExecId);
        if (lastExecId != -1) {
            try {
                ExecutableFlow lastFlow = this.executorManagerAdapter.getExecutableFlow(lastExecId);
                String propertiesVersion = getParam(req, "propertiesVersion", "");
                if ((lastFlow.getLastVersion() > 0 ? exflow.getVersion() != lastFlow
                        .getLastVersion() : exflow.getVersion() != lastFlow.getVersion()) && StringUtils
                        .isEmpty(propertiesVersion)) {
                    ret.put("warn", dataMap.get("versionChangeTips"));
                    return;
                }

                //设置上次执行版本
                if ("new".equals(propertiesVersion)) {
                    FlowUtils.compareAndCopyFlowWithSkip(exflow, lastFlow, true);
                    exflow.setLastVersion(exflow.getVersion());
                } else {
                    FlowUtils.compareAndCopyFlowWithSkip(exflow, lastFlow, false);
                    exflow.setFlowProps(lastFlow.getFlowPropsWithKey());
                    if (lastFlow.getLastVersion() > 0) {
                        exflow.setLastVersion(lastFlow.getLastVersion());
                    } else {
                        exflow.setLastVersion(lastFlow.getVersion());
                    }
                }

                //设置上次执行的时间相关内置参数及用户参数
                exflow.setLastParameterTime(lastFlow.getLastParameterTime());
                exflow.setUserProps(lastFlow.getUserProps());
            } catch (Exception e) {
                logger.error("get executable flow failed", e);
            }
        }
        exflow.setSubmitUser(user.getUserId());

        //获取项目默认代理用户
        Set<String> proxyUserSet = project.getProxyUsers();
        //设置用户代理用户
        proxyUserSet.add(user.getUserId());
        //设置提交用户的proxyUser
        WtssUser wtssUser = null;
        try {
            wtssUser = transitionService.getSystemUserByUserName(user.getUserId());
        } catch (SystemUserManagerException e) {
            logger.error("get wtssUser failed, caused by: ", e);
        }
        if (wtssUser != null && wtssUser.getProxyUsers() != null) {
            String[] proxySplit = wtssUser.getProxyUsers().split("\\s*,\\s*");
            logger.info("add proxyUsers," + ArrayUtils.toString(proxySplit));
            exflow.addAllProxyUsers(Arrays.asList(proxySplit));
        }

        ExecutionOptions options = null;
        try {
            options = HttpRequestUtils.parseFlowOptions(req);
        } catch (Exception e) {
            ret.put("error", e.getMessage());
            return;
        }

        if (req.getParameter("ajax").equals("disasterToleranceRetry")) {
            try {
                //获取不需要重跑的任务
                Props serverProps = getApplication().getServerProps();
                List<Object> jobs = HttpUtils.getFindgapisDisableJobs(serverProps, projectName, req.getParameter("flow"));
                options.setDisabledJobs(jobs);
            } catch (Exception e) {
                ret.put("error", "Failed to obtain disaster tolerance data");
                logger.error("获取容灾数据异常: project {},flow {}, Exception {}", projectName, req.getParameter("flow"), e);
                return;
            }

        }


        final List<String> failureEmails = options.getFailureEmails();
        List<WebankUser> userList = systemManager.findAllWebankUserList(null);
        if (this.checkRealNameSwitch && WebUtils
                .checkEmailNotRealName(failureEmails, options.isFailureEmailsOverridden(), userList)) {
            ret.put("info", "Please configure the correct real-name user for failure email");
            return;
        }
        final List<String> successEmails = options.getSuccessEmails();
        if (this.checkRealNameSwitch && WebUtils
                .checkEmailNotRealName(successEmails, options.isSuccessEmailsOverridden(), userList)) {
            ret.put("info", "Please configure the correct real-name user for success email");
            return;
        }


        if (hasParam(req, "disableOutterFlag")) {
            final String disableOutterFlag = getParam(req, "disableOutterFlag");
            logger.info("current request param disableOutterFlag=" + disableOutterFlag);
            if ("true".equals(disableOutterFlag)) {
                List<Flow> flows = project.getFlows();
                List<ExecutableNode> executableNodes = exflow.getExecutableNodes();
                Map<String, ExecutableNode> executableNodeMap = new HashMap<>();
                // 递归存放subflow中的节点
                recursionFindNode(executableNodes, executableNodeMap);
                logger.info("current executableNodeMap size is:" + executableNodeMap.size());
                for (Flow tempFlow : flows) {
                    for (Node node : tempFlow.getNodes()) {
                        if ("true".equals(node.getOuter())) {
                            if (executableNodeMap.containsKey(node.getId())) {
                                executableNodeMap.get(node.getId()).setStatus(Status.DISABLED);
                            }
                        }
                    }
                }
            }
        }

        exflow.setExecutionOptions(options);
        if (!options.isFailureEmailsOverridden()) {
            options.setFailureEmails(flow.getFailureEmails());
        }
        if (!options.isSuccessEmailsOverridden()) {
            options.setSuccessEmails(flow.getSuccessEmails());
        }
        options.setMailCreator(flow.getMailCreator());

        //设置其他参数配置
        Map<String, Object> otherOptions = new HashMap<>();

        //设置失败重跑配置
        Map<String, String> jobFailedRetrySettings = getParamGroup(req, "jobFailedRetryOptions");
        final List<Map<String, String>> jobRetryList = new ArrayList<>();
        for (final String set : jobFailedRetrySettings.keySet()) {
            String[] setOption = jobFailedRetrySettings.get(set).split(",");
            Map<String, String> jobOption = new HashMap<>();
            String jobName = setOption[0].trim();
            String interval = setOption[1].trim();
            String count = setOption[2].trim();
            if ("all_jobs".equals(jobName.split(" ")[0])) {
                Map<String, String> flowFailedRetryOption = new HashMap<>();
                flowFailedRetryOption.put("job.failed.retry.interval", interval);
                flowFailedRetryOption.put("job.failed.retry.count", count);
                exflow.setFlowFailedRetry(flowFailedRetryOption);
            }
            jobOption.put("jobName", jobName);
            jobOption.put("interval", interval);
            jobOption.put("count", count);
            jobRetryList.add(jobOption);


        }

        otherOptions.put("jobFailedRetryOptions", jobRetryList);

        // 定时调度测试执行
        // 任务跳过时间配置
        Map<String, String> jobCronExpressMap = new HashMap<>();
        try {
            for (Schedule schedule : this.scheduleManager.getSchedules()) {
                Object activeFlagObj = schedule.getOtherOption().get("activeFlag");
                boolean activeFlag;
                if (activeFlagObj instanceof Boolean) {
                    activeFlag = (boolean) activeFlagObj;
                } else {
                    logger.warn("can not cast active flag for Schedule[{}], skip",
                            schedule.getScheduleName());
                    continue;
                }

                // 调度失效直接跳过
                if (!activeFlag) {
                    continue;
                }

                if (schedule.getFlowName().equals(flowId) && schedule.getProjectName()
                        .equals(projectName)) {
                    jobCronExpressMap = (Map<String, String>) schedule.getOtherOption().get("job.cron.expression");
                }

                if (schedule.getProjectId() == exflow.getProjectId()) {
                    String parentFlowName = "";
                    String subflowName = "";
                    String scheduleFlowName = schedule.getFlowName();
                    // 查看该工作流的父级工作流是否有配置跳过执行策略
                    // 寻找直接父级工作流：
                    // 1. 获取项目下的所有工作流（包含子工作流）
                    List<Flow> flows = project.getFlows();
                    for (Flow projectFlow : flows) {
                        // 2. 排除当前工作流
                        if (exflow.getFlowId().equals(projectFlow.getId())) {
                            continue;
                        }
                        // 3. 如果工作流的节点包含有这个子工作流节点，说明该工作流为子工作流的直接父级工作流
                        Collection<Node> flowNodes = projectFlow.getNodes();
                        for (Node flowNode : flowNodes) {
                            String nodeType = flowNode.getType();
                            // 3.1. 获取子工作流节点
                            if ("flow".equals(nodeType)) {
                                // 3.2. 获取当前单次执行工作流的直接父级工作流名
                                if (exflow.getFlowId().equals(flowNode.getEmbeddedFlowId())) {
                                    parentFlowName = projectFlow.getId();
                                    subflowName = flowNode.getId();
                                }
                            }
                        }
                    }
                    // 存在父级工作流
                    if (StringUtils.isNotBlank(parentFlowName)) {
                        if (scheduleFlowName.equals(parentFlowName)) {
                            // 获取父级工作流调度任务跳过配置
                            Map<String, String> scheduleJobCronExpr = (Map<String, String>) schedule.getOtherOption()
                                    .get("job.cron.expression");
                            if (scheduleJobCronExpr != null && !scheduleJobCronExpr.isEmpty()) {
                                // 存在任务跳过配置，赋给单次执行工作流
                                Set<String> jobNames = scheduleJobCronExpr.keySet();
                                for (String jobName : jobNames) {
                                    String jobCronExpr = scheduleJobCronExpr.get(jobName);
                                    // 由于父工作流配置子工作流节点的任务名前会带有子工作流名前缀，因此需要将子工作流名去掉
                                    if (StringUtils.isNotBlank(subflowName) && jobName.contains(
                                            subflowName)) {
                                        jobName = jobName.replaceFirst(subflowName + ":", "");
                                    }
                                    jobCronExpressMap.put(jobName, jobCronExpr);
                                }
                                logger.info("job crontab expression for ExecutableFlow[{}:{}]: ",
                                        exflow.getProjectName(), exflow.getFlowId());
                                StringJoiner joiner = new StringJoiner(", ", "{", "}");
                                for (Map.Entry<String, String> entry : jobCronExpressMap.entrySet()) {
                                    joiner.add(entry.getKey() + " - " + entry.getValue());
                                }
                                logger.info(joiner.toString());
                            }
                        }
                    }
                }
            }
        } catch (ScheduleManagerException e) {
            logger.error("query schedules failed", e);
        }

        otherOptions.put("job.cron.expression", jobCronExpressMap);

        //set normal user
        otherOptions.put("normalSubmitUser", user.getNormalUser());

        exflow.setOtherOption(otherOptions);

        //设置失败跳过配置
        Map<String, String> jobSkipFailedSettings = getParamGroup(req, "jobSkipFailedOptions");
        String jobSkipActionOptions = getParam(req, "jobSkipActionOptions", "[]");
        final List<String> jobSkipActionOptionsList =
                (List<String>) JSONUtils.parseJSONFromStringQuiet(jobSkipActionOptions);

        final List<String> jobSkipList = new ArrayList<>();
        final List<String> jobSkipActionList = new ArrayList<>();
        for (final String set : jobSkipFailedSettings.keySet()) {
            String jobName = jobSkipFailedSettings.get(set).trim();
            if ("all_jobs".equals(jobName.split(" ")[0])) {
                exflow.setFailedSkipedAllJobs(true);
            }
            if (jobSkipActionOptionsList != null && jobSkipActionOptionsList.contains(jobName)) {
                jobSkipActionList.add(jobName);
            }
            jobSkipList.add(jobName);
        }

        otherOptions.put("jobSkipFailedOptions", jobSkipList);
        otherOptions.put("jobSkipActionOptions", jobSkipActionList);
        exflow.setOtherOption(otherOptions);

        //设置通用告警级别
        if (hasParam(req, "failureAlertLevel")) {
            otherOptions.put("failureAlertLevel", getParam(req, "failureAlertLevel"));
        }
        if (hasParam(req, "successAlertLevel")) {
            otherOptions.put("successAlertLevel", getParam(req, "successAlertLevel"));
        }

        //---超时告警设置---
        boolean useTimeoutSetting = false;
        if (hasParam(req, "useTimeoutSetting")) {
            useTimeoutSetting = Boolean.valueOf(getParam(req, "useTimeoutSetting"));
        }
        final List<SlaOption> slaOptions = new ArrayList<>();
        if (useTimeoutSetting) {
            String emailStr = "";
            if (hasParam(req, "slaEmails")) {
                emailStr = getParam(req, "slaEmails");
            }
            final String[] emailSplit = emailStr.split("\\s*,\\s*|\\s*;\\s*|\\s+");
            final List<String> slaEmails = Arrays.asList(emailSplit);
            Map<String, String> settings = getParamGroup(req, "settings");
            String alerterWay = getParam(req, "alerterWay", "0,1,2,3");
            //设置SLA 超时告警配置项
            for (final String set : settings.keySet()) {
                final SlaOption sla;
                try {
                    sla = AlertUtil.parseSlaSetting(settings.get(set), flow, project);
                } catch (final Exception e) {
                    logger.error("parse sla setting failed.");
                    throw new ServletException(e);
                }
                if (sla != null) {
                    sla.getInfo().put(SlaOption.INFO_FLOW_NAME, flowId);
                    sla.getInfo().put(SlaOption.INFO_EMAIL_LIST, slaEmails);
                    sla.getInfo().put(SlaOption.INFO_TIME_SET, sla.getTimeSet());
                    sla.getInfo().put(SlaOption.INFO_EMAIL_ACTION_SET, sla.getEmailAction());
                    sla.getInfo().put(SlaOption.INFO_KILL_FLOW_ACTION_SET, sla.getKillAction());
                    sla.getInfo().put(SlaOption.INFO_ALERTER_WAY, alerterWay);
                    slaOptions.add(sla);
                }
            }
        }
        exflow.setSlaOptions(slaOptions);
        //---超时告警设置---

        //设置flowType
        //对于单次执行，假如提交的json中含有cycleErrorOption，表示是循环执行，需要设置flowType为4; 设置cycleOption
        if (hasParam(req, "cycleErrorOption")) {
            exflow.setFlowType(4);
            HashMap<String, String> cycleOption = new HashMap<>();
            cycleOption.put("cycleErrorOption", getParam(req, "cycleErrorOption"));
            cycleOption.put("cycleFlowInterruptAlertLevel", getParam(req, "cycleFlowInterruptAlertLevel"));
            cycleOption.put("cycleFlowInterruptEmails", getParam(req, "cycleFlowInterruptEmails"));
            exflow.setCycleOption(cycleOption);
        }
        // 作业流重试告警
        boolean flowRetryAlertChecked = false;
        String flowRetryAlertLevel = "INFO";
        String alertMsg = "";
        Map<String, Object> flowRetryAlertOption = new HashMap<>();
        if (hasParam(req, "flowRetryAlertChecked")) {
            flowRetryAlertChecked = Boolean.valueOf(getParam(req, "flowRetryAlertChecked"));
            flowRetryAlertLevel = getParam(req, "flowRetryAlertLevel");
            alertMsg = getParam(req, "alertMsg");
        }
        flowRetryAlertOption.put("flowRetryAlertChecked", flowRetryAlertChecked);
        flowRetryAlertOption.put("flowRetryAlertLevel", flowRetryAlertLevel);
        flowRetryAlertOption.put("alertMsg", alertMsg);
        otherOptions.put("flowRetryAlertOption", flowRetryAlertOption);

        // lastexecid
        if (hasParam(req, "lastNsWtss")) {
            boolean lastNsWtss = Boolean.valueOf(getParam(req, "lastNsWtss"));
            exflow.setLastNsWtss(lastNsWtss);
            if (lastNsWtss) {
                exflow.setJobOutputGlobalParam(new ConcurrentHashMap<>(getParamGroup(req, "jobOutputParam")));
            } else {
                otherOptions.put("lastExecId", lastExecId);
            }
        }

        try {
            //设置告警用户部门信息
            String userDep = transitionService.getUserDepartmentByUsername(user.getUserId());
            otherOptions.put("alertUserDeparment", userDep);

            HttpRequestUtils.filterAdminOnlyFlowParams(options, user);
            final String message =
                    this.executorManagerAdapter.submitExecutableFlow(exflow, user.getUserId());

            if (Constants.HOLD_BATCH_REJECT.equals(message)) {
                logger.info("cycle flow submit in batch, reject!");
                return;
            }

            ret.put("message", message);
            String ajaxName = getParam(req, "ajax");
            // 是否启用作业流重跑告警
            if (flowRetryAlertChecked && exflow.getExecutionId() != -1 && "executeFlow"
                    .equals(ajaxName)) {
                logger.info("add alert task to threadpool, current execId " + exflow.getExecutionId());
                this.threadPoolService.execute(() -> eventNotifyService.alertOnFLowStarted(exflow, flow));
            }
        } catch (final Exception e) {
            logger.warn("Failed to execute Flow {}", exflow.getFlowId(), e);
            ret.put("code", "10006");
            ret.put("error", "Error submitting flow " + exflow.getFlowId() + ". " + e.getMessage());
            ret.put("message", "Execute Flow[" + exflow.getFlowId() + "Failed." + e.getMessage());
            return;
        }
        ret.put("code", "200");
        ret.put("message", "success");
        ret.put("execid", exflow.getExecutionId());
    }

    private String getJobCron(ExecutableNode node, Map<String, String> cronMap) {
        if (org.apache.commons.collections.MapUtils.isEmpty(cronMap) || node == null) {
            return null;
        }
        String cron;
        if (node instanceof ExecutableFlowBase) {
            cron = cronMap.get("subflow-" + ((ExecutableFlowBase) node).getFlowId());
        } else {
            cron = cronMap.get(node.getNestedId());
        }
        if (org.apache.commons.lang.StringUtils.isNotEmpty(cron)) {
            return cron;
        } else {
            return getJobCron(node.getParentFlow(), cronMap);
        }
    }


    /**
     * 递归存放subflow中的节点
     *
     * @param executableNodes
     * @param resultMap
     */
    private void recursionFindNode(List<ExecutableNode> executableNodes, Map<String, ExecutableNode> resultMap) {
        for (ExecutableNode executableNode : executableNodes) {
            if (executableNode instanceof ExecutableFlowBase) {
                ExecutableFlowBase subFlow = (ExecutableFlowBase) executableNode;
                List<ExecutableNode> subFlowExecutableNodes = subFlow.getExecutableNodes();
                recursionFindNode(subFlowExecutableNodes, resultMap);
            } else {
                resultMap.put(executableNode.getId(), executableNode);
            }
        }
    }

    private void ajaxExecuteAllHistoryRecoverFlow(final HttpServletRequest req, final HttpServletResponse resp,
                                                  final HashMap<String, Object> ret, final User user) throws ServletException {
        JsonObject request = HttpRequestUtils.parseRequestToJsonObject(req);
        String projectName = request.get("project").getAsString();
        Project project = getProjectAjaxByPermission(ret, projectName, user, Type.EXECUTE);
        if (project == null) {
            ret.put("error", "Project '" + projectName + "' doesn't exist.");
            logger.error("Project '" + projectName + "' doesn't exist.");
            return;
        }
        Map<String, String> dataMap = loadExecutorServletI18nData();

        if (project.getFlows() == null || project.getFlows().size() == 0) {
            ret.put("error", projectName + dataMap.get("haveNoFlows"));
            logger.error(projectName + ", 没有作业流。");
            return;
        }
        List<Flow> rootFlows = project.getAllRootFlows();
        StringBuilder sb = new StringBuilder();
        for (Flow flow : rootFlows) {
            try {
                executeHistoryRecoverFlow(project, flow, ret, request, user, sb);
            } catch (ServletException se) {
                logger.warn("submit HistoryRecoverFlow failed, " + se);
            }
        }
        ret.put("code", "200");
        ret.put("message", sb.toString());
    }

    private boolean executeHistoryRecoverFlow(Project project, Flow flow, Map<String, Object> ret, JsonObject json, User user, StringBuilder msg) throws ServletException {
        String projectName = project.getName();
        String flowId = flow.getId();
        ret.put("flow", flowId);
        ExecutionRecover nowRuningRecover = null;

        Map<String, String> dataMap = loadExecutorServletI18nData();
        //查询这个Flow的补采记录
        try {
            nowRuningRecover =
                    this.executorManagerAdapter.getHistoryRecoverFlowByPidAndFid(String.valueOf(project.getId()), flow.getId());
        } catch (ExecutorManagerException e) {
            logger.error(e.getMessage());
        }
        //校验这个Flow是否已经开始补采
        if (null != nowRuningRecover && (Status.RUNNING.equals(nowRuningRecover.getRecoverStatus())
                || Status.PREPARING.equals(nowRuningRecover.getRecoverStatus()))) {
            logger.error("This flow '" + flow.getId() + "' has running history recover.");
            msg.append(String.format("Error, flow:%s, msg: " + dataMap.get("haveHisReRun") + "<br/>", flow.getId()));
            return false;
        }
        final ExecutionOptions options = HttpRequestUtils.parseFlowOptions(json);
        final List<String> failureEmails = options.getFailureEmails();
        List<WebankUser> userList = systemManager.findAllWebankUserList(null);
        if (this.checkRealNameSwitch && WebUtils
                .checkEmailNotRealName(failureEmails, options.isFailureEmailsOverridden(), userList)) {
            msg.append(String.format(
                    "Error, flow:%s, msg: Please configure the correct real-name user for failure email<br/>",
                    flow.getId()));
            return false;
        }
        final List<String> successEmails = options.getSuccessEmails();
        if (this.checkRealNameSwitch && WebUtils
                .checkEmailNotRealName(successEmails, options.isSuccessEmailsOverridden(), userList)) {
            msg.append(String.format(
                    "Error, flow:%s, msg: Please configure the correct real-name user for success email<br/>",
                    flow.getId()));
            return false;
        }
        if (!options.isFailureEmailsOverridden()) {
            options.setFailureEmails(flow.getFailureEmails());
        }
        if (!options.isSuccessEmailsOverridden()) {
            options.setSuccessEmails(flow.getSuccessEmails());
        }
        options.setMailCreator(flow.getMailCreator());

        Map<String, String> reqMap = new HashMap<>();
        reqMap.put("projectName", projectName);
        reqMap.put("projectId", project.getId() + "");
        reqMap.put("flowId", flow.getId());
        //获取前端的历史重跑错误设置
        String recoverErrorOption = json.get("recoverErrorOption").getAsString();
        reqMap.put("recoverErrorOption", recoverErrorOption);
        final Map<String, Object> repeatOptionMap = new HashMap<>();
        try {
            //解析前端的参数 获取数据补采参数集合
            repeatOptionMap.putAll(repeatDateCompute(json));
        } catch (Exception e) {
            logger.error("数据补采输入参数处理异常!", e);
            msg.append(String.format("Error, flow:%s, msg: %s <br/>", flow.getId(), dataMap.get("dataCompensateinputParamError")));
            return false;
        }

        ExecutionRecover executionRecover = new ExecutionRecover();
        //组装历史补采数据
        executionRecover.setSubmitUser(user.getUserId());
        executionRecover.setRecoverStatus(Status.PREPARING);
        executionRecover.setRecoverStartTime(Long.valueOf(String.valueOf(repeatOptionMap.get("recoverStartTime"))));
        executionRecover.setRecoverEndTime(Long.valueOf(String.valueOf(repeatOptionMap.get("recoverEndTime"))));
        executionRecover.setExInterval(String.valueOf(repeatOptionMap.get("exInterval")));

        executionRecover.setProjectId(project.getId());
        executionRecover.setFlowId(flowId);
        executionRecover.setExecutionOptions(options);

        //repeatOptionMap.put("repeatOptionList", repeatOptionMap);
        executionRecover.setRepeatOption(repeatOptionMap);
        //放入用户代理
        executionRecover.setProxyUsers(ArrayUtils.toString(user.getProxyUsers()));
        executionRecover.setRecoverErrorOption(recoverErrorOption);
        int taskSize = 1;
        if (json.has("taskSize")) {
            taskSize = json.get("taskSize").getAsInt();
        }
        executionRecover.setTaskSize(taskSize);

        Map<String, Object> otherOptions = new HashMap<>();
        //设置通用告警级别failureAlertLevel
        if (json.has("failureAlertLevel")) {
            otherOptions.put("failureAlertLevel", json.get("failureAlertLevel").getAsString());
        }
        if (json.has("successAlertLevel")) {
            otherOptions.put("successAlertLevel", json.get("successAlertLevel").getAsString());
        }
        //设置失败重跑配置
        List<Map<String, String>> jobRetryList = new ArrayList<>();
        otherOptions.put("jobFailedRetryOptions", jobRetryList);

        //设置失败跳过配置
        List<String> jobSkipList = new ArrayList<>();
        otherOptions.put("jobSkipFailedOptions", jobSkipList);

        executionRecover.setOtherOption(otherOptions);
        ret.put("repeatOptionMap", repeatOptionMap);

        //---超时告警设置---
        boolean useTimeoutSetting = false;
        if (json.get("useTimeoutSetting").getAsBoolean()) {
            useTimeoutSetting = json.get("useTimeoutSetting").getAsBoolean();
        }
        final List<SlaOption> slaOptions = new ArrayList<>();
        if (useTimeoutSetting) {
            final String emailStr = json.get("slaEmails").getAsString();
            final String[] emailSplit = emailStr.split("\\s*,\\s*|\\s*;\\s*|\\s+");
            final List<String> slaEmails = Arrays.asList(emailSplit);
            final Map<String, String> settings = GsonUtils.jsonToJavaObject(json.getAsJsonObject("settings"), new TypeToken<Map<String, String>>() {
            }.getType());
            ;
            //设置SLA 超时告警配置项
            for (final String set : settings.keySet()) {
                SlaOption sla = null;
                try {
                    sla = AlertUtil.parseSlaSetting(settings.get(set), flow, project);
                } catch (final Exception e) {
                    logger.warn("parse sla setting failed." + e);
                }
                if (sla != null) {
                    sla.getInfo().put(SlaOption.INFO_FLOW_NAME, flowId);
                    sla.getInfo().put(SlaOption.INFO_EMAIL_LIST, slaEmails);
                    sla.getInfo().put(SlaOption.INFO_TIME_SET, sla.getTimeSet());
                    sla.getInfo().put(SlaOption.INFO_EMAIL_ACTION_SET, sla.getEmailAction());
                    sla.getInfo().put(SlaOption.INFO_KILL_FLOW_ACTION_SET, sla.getKillAction());
                    slaOptions.add(sla);
                }
            }
        }
        executionRecover.setSlaOptions(slaOptions);
        //---超时告警设置---

        //新增历史补采数据记录
        try {
            this.executorManagerAdapter.saveHistoryRecoverFlow(executionRecover);
            logger.info("提交历史重跑成功" + project.getId() + " - " + flowId);
            msg.append(String.format("Success, flow:%s, msg:" + dataMap.get("submitHisReRunSuccess") + "<br/>", flow.getId()));
        } catch (Exception e) {
            logger.error("新增历史重跑任务失败 ", e);
            msg.append(String.format("Error, flow:%s, msg:" + dataMap.get("submitHisReRunFail") + "。<br/>", flow.getId()));
            return false;
        }
        return true;
    }


    /**
     * 数据补采 前端入口方法
     *
     * @param req
     * @param resp
     * @param ret
     * @param user
     * @throws ServletException
     */
    private void ajaxAttRepeatExecuteFlow(final HttpServletRequest req, final HttpServletResponse resp,
                                          final HashMap<String, Object> ret, final User user) throws ServletException {
        JsonObject json = HttpRequestUtils.parseRequestToJsonObject(req);
        if (json == null) {
            logger.error("解析请求参数异常.");
            return;
        }
        final String projectName = json.get("project").getAsString();
        final String flowId = json.get("flow").getAsString();
        final String end = json.get("end").getAsString();
        if (checkEndTime(end)) {
            logger.error("The endtime cannot be later than the current time.");
            return;
        }

        final Project project = getProjectAjaxByPermission(ret, projectName, user, Type.EXECUTE);
        if (project == null) {
            logger.error("Project '" + projectName + "' doesn't exist.");
            return;
        }

        ret.put("flow", flowId);
        final Flow flow = project.getFlow(flowId);
        if (flow == null) {
            logger.error("Flow '" + flowId + "' cannot be found in project " + project);
            return;
        }

        ExecutionRecover nowRuningRecover = null;

        //查询这个Flow的补采记录
        try {
            nowRuningRecover =
                    this.executorManagerAdapter.getHistoryRecoverFlowByPidAndFid(String.valueOf(project.getId()), flow.getId());
        } catch (ExecutorManagerException e) {
            logger.error("get flow history recover failed, caused by:", e);
        }
        //校验这个Flow是否已经开始补采
        if (null != nowRuningRecover && (Status.RUNNING.equals(nowRuningRecover.getRecoverStatus())
                || Status.PREPARING.equals(nowRuningRecover.getRecoverStatus()))) {
            logger.error("This flow '" + flow.getId() + "' has running history recover.");
            return;
        }

        final ExecutionOptions options = HttpRequestUtils.parseFlowOptions(json);
        if (!options.isFailureEmailsOverridden()) {
            options.setFailureEmails(flow.getFailureEmails());
        }
        if (!options.isSuccessEmailsOverridden()) {
            options.setSuccessEmails(flow.getSuccessEmails());
        }
        options.setMailCreator(flow.getMailCreator());
        options.setConcurrentOption(ExecutionOptions.CONCURRENT_OPTION_IGNORE);
        Map<String, String> reqMap = new HashMap<>();
        reqMap.put("projectName", projectName);
        reqMap.put("projectId", project.getId() + "");
        reqMap.put("flowId", flow.getId());
        //获取前端的历史重跑错误设置
        String recoverErrorOption = json.get("recoverErrorOption").getAsString();
        reqMap.put("recoverErrorOption", recoverErrorOption);


        final Map<String, Object> repeatOptionMap = new HashMap<>();
        try {
            //解析前端的参数 获取数据补采参数集合
            repeatOptionMap.putAll(repeatDateCompute(json));
        } catch (Exception e) {
            logger.error("数据补采输入参数处理异常!", e);

        }

        //repeatOptionMap.put("flowOption", options);

        ExecutionRecover executionRecover = new ExecutionRecover();
        //组装历史补采数据
        executionRecover.setSubmitUser(user.getUserId());
        executionRecover.setRecoverStatus(Status.PREPARING);
        executionRecover.setRecoverStartTime(Long.valueOf(String.valueOf(repeatOptionMap.get("recoverStartTime"))));
        executionRecover.setRecoverEndTime(Long.valueOf(String.valueOf(repeatOptionMap.get("recoverEndTime"))));
        executionRecover.setExInterval(String.valueOf(repeatOptionMap.get("exInterval")));

        executionRecover.setProjectId(project.getId());
        executionRecover.setFlowId(flowId);
        executionRecover.setExecutionOptions(options);

        //repeatOptionMap.put("repeatOptionList", repeatOptionMap);
        executionRecover.setRepeatOption(repeatOptionMap);

        //设置提交用户的proxyUser
        WtssUser wtssUser = null;
        try {
            wtssUser = transitionService.getSystemUserByUserName(user.getUserId());
        } catch (SystemUserManagerException e) {
            logger.error("get wtssUser failed, caused by: ", e);
        }
        if (wtssUser != null && wtssUser.getProxyUsers() != null) {
            executionRecover.setProxyUsers(wtssUser.getProxyUsers());
        }

        executionRecover.setRecoverErrorOption(recoverErrorOption);
        List<Long> runDateTimeList = GsonUtils.jsonToJavaObject(json.getAsJsonArray("runDateTimeList"), new TypeToken<List<Long>>() {
        }.getType());
        List<Long> skipDateTimeList = GsonUtils.jsonToJavaObject(json.getAsJsonArray("skipDateTimeList"), new TypeToken<List<Long>>() {
        }.getType());
        executionRecover.setRunDateTimeList(runDateTimeList);
        executionRecover.setSkipDateTimeList(skipDateTimeList);
        int taskSize = 1;
        if (json.has("taskSize")) {
            taskSize = json.get("taskSize").getAsInt();
            executionRecover.setTaskSize(taskSize);
            String taskDistributeMethod = ExecutionRecover.TASK_UNIFORMLY_DISTRIBUTE;
            if (json.has(ExecutionRecover.TASK_DISTRIBUTE_METHOD)) {
                taskDistributeMethod = json.get(ExecutionRecover.TASK_DISTRIBUTE_METHOD).getAsString();
            }
            executionRecover.setTaskDistributeMethod(taskDistributeMethod);
        }

        int reRunTimeInterval = 0;
        if (json.has("reRunTimeInterval")) {
            reRunTimeInterval = json.get("reRunTimeInterval").getAsInt();
        }
        if (reRunTimeInterval > 0 && taskSize > 1) {
            ret.put("error", "reRunTimeInterval and taskSize cannot be set at the same time");
            return;
        }
        executionRecover.setReRunTimeInterval(reRunTimeInterval);

        boolean finishedAlert = true;
        if (json.has("finishedAlert")) {
            finishedAlert = json.get("finishedAlert").getAsBoolean();
        }
        executionRecover.setFinishedAlert(finishedAlert);

        if (json.has("currentVersionFlag") && json.get("currentVersionFlag").getAsBoolean()) {
            executionRecover.setProjectVersion(project.getVersion());
        }

        Map<String, Object> otherOptions = new HashMap<>();
        //设置通用告警级别failureAlertLevel
        if (json.has("failureAlertLevel")) {
            otherOptions.put("failureAlertLevel", json.get("failureAlertLevel").getAsString());
        }
        if (json.has("successAlertLevel")) {
            otherOptions.put("successAlertLevel", json.get("successAlertLevel").getAsString());
        }

        //设置失败重跑配置
        Map<String, String> jobFailedRetrySettings = new HashMap<>();
        if (json.has("jobFailedRetryOptions")) {
            jobFailedRetrySettings = GsonUtils.jsonToJavaObject(json.get("jobFailedRetryOptions").getAsJsonObject(), new TypeToken<Map<String, String>>() {
            }.getType());
        }
        final List<Map<String, String>> jobRetryList = new ArrayList<>();
        for (final String set : jobFailedRetrySettings.keySet()) {
            String[] setOption = jobFailedRetrySettings.get(set).split(",");
            Map<String, String> jobOption = new HashMap<>();
            String jobName = setOption[0].trim();
            String interval = setOption[1].trim();
            String count = setOption[2].trim();
            if ("all_jobs".equals(jobName.split(" ")[0])) {
                Map<String, String> flowFailedRetryOption = new HashMap<>();
                flowFailedRetryOption.put("job.failed.retry.interval", interval);
                flowFailedRetryOption.put("job.failed.retry.count", count);
                otherOptions.put("flowFailedRetryOption", flowFailedRetryOption);
            }
            jobOption.put("jobName", jobName);
            jobOption.put("interval", interval);
            jobOption.put("count", count);
            jobRetryList.add(jobOption);
        }
        otherOptions.put("jobFailedRetryOptions", jobRetryList);

        // 设置历史重跑告警
        otherOptions.put("historyRerunAlertLevel", json.get("historyRerunAlertLevel").getAsString());
        otherOptions.put("historyRerunAlertEmails", json.get("historyRerunAlertEmails").getAsString());


        //设置失败跳过配置
        Map<String, String> jobSkipFailedSettings = new HashMap<>();
        if (json.has("jobSkipFailedOptions")) {
            jobSkipFailedSettings = GsonUtils
                    .jsonToJavaObject(json.get("jobSkipFailedOptions").getAsJsonObject(),
                            new TypeToken<Map<String, String>>() {
                            }.getType());
        }
        List<String> jobSkipActionOptions = new ArrayList<>();
        if (json.has("jobSkipActionOptions")) {
            jobSkipActionOptions = new Gson()
                    .fromJson(json.get("jobSkipActionOptions").getAsString(),
                            new TypeToken<List<String>>() {
                            }.getType());
        }

        final List<String> jobSkipActionList = new ArrayList<>();
        final List<String> jobSkipList = new ArrayList<>();
        for (final String set : jobSkipFailedSettings.keySet()) {
            String jobName = jobSkipFailedSettings.get(set).trim();
            if ("all_jobs".equals(jobName.split(" ")[0])) {
                otherOptions.put("flowFailedSkiped", true);
            }
            if (jobSkipActionOptions.contains(jobName)) {
                jobSkipActionList.add(jobName);
            }
            jobSkipList.add(jobName);
        }

        otherOptions.put("jobSkipFailedOptions", jobSkipList);

        otherOptions.put("jobSkipActionOptions", jobSkipActionList);

        //set normal user
        otherOptions.put("normalSubmitUser", user.getNormalUser());

        //set execute time
        if (json.get("executeTimeBegin") != null) {
            otherOptions.put("repeatExecuteTimeBegin", json.get("executeTimeBegin").getAsString());
        }
        if (json.get("executeTimeEnd") != null) {
            otherOptions.put("repeatExecuteTimeEnd", json.get("executeTimeEnd").getAsString());
        }

        Map<String, String> jobCronExpressMap = new HashMap<>();
        try {
            for (Schedule schedule : this.scheduleManager.getSchedules()) {
                if (schedule.getFlowName().equals(flowId) && schedule.getProjectName().equals(projectName) && (Boolean) schedule.getOtherOption().get("activeFlag")) {
                    jobCronExpressMap = (Map<String, String>) schedule.getOtherOption().get("job.cron.expression");
                }
            }
        } catch (ScheduleManagerException e) {
            logger.error("query schedules failed", e);
        }

        otherOptions.put("job.cron.expression", jobCronExpressMap);

        executionRecover.setOtherOption(otherOptions);

        ret.put("repeatOptionMap", repeatOptionMap);

        //---超时告警设置---
        boolean useTimeoutSetting = false;
        if (json.has("useTimeoutSetting")) {
            useTimeoutSetting = json.get("useTimeoutSetting").getAsBoolean();
        }
        final List<SlaOption> slaOptions = new ArrayList<>();
        if (useTimeoutSetting) {
            final String emailStr = json.get("slaEmails").getAsString();
            final String[] emailSplit = emailStr.split("\\s*,\\s*|\\s*;\\s*|\\s+");
            final List<String> slaEmails = Arrays.asList(emailSplit);
            final Map<String, String> settings = GsonUtils.jsonToJavaObject(json.getAsJsonObject("settings"), new TypeToken<Map<String, String>>() {
            }.getType());
            ;
            //设置SLA 超时告警配置项
            for (final String set : settings.keySet()) {
                final SlaOption sla;
                try {
                    sla = AlertUtil.parseSlaSetting(settings.get(set), flow, project);
                } catch (final Exception e) {
                    logger.error("parse sla setting failed.");
                    throw new ServletException(e);
                }
                if (sla != null) {
                    sla.getInfo().put(SlaOption.INFO_FLOW_NAME, flowId);
                    sla.getInfo().put(SlaOption.INFO_EMAIL_LIST, slaEmails);
                    sla.getInfo().put(SlaOption.INFO_TIME_SET, sla.getTimeSet());
                    sla.getInfo().put(SlaOption.INFO_EMAIL_ACTION_SET, sla.getEmailAction());
                    sla.getInfo().put(SlaOption.INFO_KILL_FLOW_ACTION_SET, sla.getKillAction());
                    slaOptions.add(sla);
                }
            }
        }
        executionRecover.setSlaOptions(slaOptions);
        //---超时告警设置---

        if (json.has("lastExecId")) {
            int lastExecId = json.get("lastExecId").getAsInt();
            executionRecover.setLastExecId(lastExecId);
        }
        //新增历史补采数据记录
        try {
            Map<String, Object> recoverHandleMap = new HashMap<>();
            recoverHandleMap.putAll(repeatOptionMap);
            recoverHandleMap.put("project", project);
            recoverHandleMap.put("flow", flow);

            this.executorManagerAdapter.saveHistoryRecoverFlow(executionRecover);
            logger.info("提交历史重跑成功" + project.getId() + " - " + flowId);
        } catch (Exception e) {
            logger.info("历史补采线程终止： ", e);
        }

    }

    public static boolean checkEndTime(String end) {
        // end 不得晚于当前时间
        boolean flag = false;
        Date nowDate = new Date();
        try {
            SimpleDateFormat sdf = new SimpleDateFormat(FILTER_BY_REPEAT_DATE_PATTERN_NEW);
            Date parseDate = sdf.parse(end);
            flag = parseDate.after(nowDate);
        } catch (ParseException e) {
            logger.error("end time pattern error, end:{}", end);
            return flag;
        }
        return flag;
    }

    private void ajaxCycleParamVerify(HttpServletRequest req, HttpServletResponse resp, HashMap<String, Object> ret, User user)
            throws ServletException {

        JsonObject json = HttpRequestUtils.parseRequestToJsonObject(req);
        if (json == null) {
            json = new JsonObject();
            String projectName = getParam(req, "project");
            String flowId = getParam(req, "flow");
            json.addProperty("project", projectName);
            json.addProperty("flow", flowId);
        }
        Optional<Pair<Project, Flow>> validateCycleFlowRes = validateCycleFlowParams(json, user, ret);
        if (!validateCycleFlowRes.isPresent()) {
            return;
        }
        Project project = validateCycleFlowRes.get().getFirst();
        Flow flow = validateCycleFlowRes.get().getSecond();
        String flowId = flow.getId();

        if (this.holdBatchSwitch && StringUtils.isNotEmpty(this.holdBatchContext
                .isInBatch(project.getName(), flowId, user.getUserId()))) {
            ret.put("error", "server is holding, reject all operation");
            return;
        }
        //check is set business
        Map<String, String> dataMap = loadExecutorServletI18nData();
        boolean hasBusiness = this.projectManager.getFlowBusiness(project.getId(), "", "") != null
                || this.projectManager.getFlowBusiness(project.getId(), flowId, "") != null;
        if (getApplication().getServerProps().getBoolean("wtss.set.business.check", true)
                && !hasBusiness) {
            ret.put("error", dataMap.get("flow") + flow.getId() + dataMap.get("setBusiness"));
            return;
        }

        //check email name
        final boolean failureOverride = HttpRequestUtils
                .getBooleanParam(req, "failureEmailsOverride", false);
        final boolean successOverride = HttpRequestUtils
                .getBooleanParam(req, "successEmailsOverride", false);
        List<WebankUser> userList = systemManager.findAllWebankUserList(null);
        if (hasParam(req, "failureEmails")) {
            final String emails = getParam(req, "failureEmails");
            if (!emails.isEmpty()) {
                final String[] emailSplit = emails.split("\\s*,\\s*|\\s*;\\s*|\\s+");
                if (this.checkRealNameSwitch && WebUtils.checkEmailNotRealName(
                        Lists.newArrayList(emailSplit), failureOverride,
                        userList)) {
                    ret.put("error",
                            "Please configure the correct real-name user for failure email");
                    return;
                }
            }
        }
        if (hasParam(req, "successEmails")) {
            final String emails = getParam(req, "successEmails");
            if (!emails.isEmpty()) {
                final String[] emailSplit = emails.split("\\s*,\\s*|\\s*;\\s*|\\s+");
                if (this.checkRealNameSwitch && WebUtils.checkEmailNotRealName(
                        Lists.newArrayList(emailSplit), successOverride,
                        userList)) {
                    ret.put("error",
                            "Please configure the correct real-name user for success email");
                    return;
                }
            }
        }

        ExecutionRecover nowRuningRecover = null;
        try {
            nowRuningRecover =
                    this.executorManagerAdapter.getHistoryRecoverFlowByPidAndFid(String.valueOf(project.getId()), flow.getId());
        } catch (ExecutorManagerException e) {
            logger.error("获取历史重跑任务失败", e);
        }
        if (null != nowRuningRecover && (Status.RUNNING.equals(nowRuningRecover.getRecoverStatus())
                || Status.PREPARING.equals(nowRuningRecover.getRecoverStatus()))) {
            ret.put("error", "Exist history Re-Run job, please wait until that job run finished then commit again.");
            return;
        }

        boolean isCycleFlowRunning = isCycleFlowRunning(project.getId(), flow.getId());
        if (isCycleFlowRunning) {
            ExecutionCycle executionCycle = null;
            try {
                executionCycle = executorManagerAdapter.getExecutionCycleFlow(
                        String.valueOf(project.getId()), flowId);
            } catch (ExecutorManagerException e) {
                logger.error("Get cycle flow[" + flowId + "] error. Reason: " + e.getMessage());
                ret.put("error", "Get cycle flow[" + flowId + "] error. Reason: " + e.getMessage());
                return;
            }

            if (executionCycle != null) {
                try {
                    ret.put("alert",
                            "This flow [" + flowId + "] has running cycle. This cycle will be "
                                    + "stopped, and the new configuration will start at next new running cycle");
                    ExecutableFlow exFlow = this.executorManagerAdapter.getExecutableFlow(
                            executionCycle.getCurrentExecId());
                    executionCycle.setStatus(Status.KILLED);
                    executionCycle.setEndTime(System.currentTimeMillis());
                    executionCycle.setExecutionOptions(exFlow.getExecutionOptions());
                    executorManagerAdapter.updateExecutionFlow(executionCycle);
                    executorManagerAdapter.cancelFlow(exFlow, user.getUserId());
                    // ExecutionControllerUtils.alertOnCycleFlowInterrupt(exFlow, executionCycle, alerterHolder);
                    logger.info("Stop cycle flow[" + flowId + "] !");
                } catch (ExecutorManagerException e) {
                    logger.error(
                            "Stop cycle flow[" + flowId + "] error. Reason: " + e.getMessage());
                    ret.put("error",
                            "Stop cycle flow[" + flowId + "] error. Reason: " + e.getMessage());
                    return;
                }
            }
        }
    }

    private void ajaxStopCycleFlow(HttpServletRequest req, HttpServletResponse resp, Map<String, Object> ret, User user) throws ServletException {
        int id = getIntParam(req, "id");
        try {
            ExecutionCycle executionCycle = executorManagerAdapter.getExecutionCycleFlow(id);
            if (executionCycle != null) {
                ExecutableFlow exFlow = this.executorManagerAdapter.getExecutableFlow(executionCycle.getCurrentExecId());
                executionCycle.setStatus(Status.KILLED);
                executionCycle.setEndTime(System.currentTimeMillis());
                executionCycle.setExecutionOptions(exFlow.getExecutionOptions());
                executorManagerAdapter.updateExecutionFlow(executionCycle);
                executorManagerAdapter.cancelFlow(exFlow, user.getUserId());
                ExecutionControllerUtils.alertOnCycleFlowInterrupt(exFlow, executionCycle, alerterHolder);
            }
        } catch (ExecutorManagerException e) {
            ret.put("error", e.getMessage());
        }
    }

    private void ajaxSubmitCycleFlow(HttpServletRequest req, HttpServletResponse resp, Map<String, Object> ret, User user) throws ServletException {
        String projectName = getParam(req, "project");
        String flowId = getParam(req, "flow");
        final Project project = getProjectAjaxByPermission(ret, projectName, user, Type.EXECUTE);
        final Flow flow = project.getFlow(flowId);
        try {
            ExecutionCycle executionCycle = generateExecutionCycle(req, project, flow, user);
            executorManagerAdapter.saveExecutionCycleFlow(executionCycle);
            HashMap<String, Object> executeFlowRes = new HashMap<>();
            ajaxExecuteFlow(req, resp, executeFlowRes, user);
            boolean flag = "200".equals(executeFlowRes.get("code"));
            if (flag) {
                int currentExecId = (Integer) executeFlowRes.get("execid");
                executionCycle.setCurrentExecId(currentExecId);
                executionCycle.setStatus(Status.RUNNING);
            } else {
                executionCycle.setStatus(Status.FAILED);
            }
            executorManagerAdapter.updateExecutionFlow(executionCycle);
            logger.info("submit cycle flow success " + project.getId() + " - " + flow.getId());
        } catch (ExecutorManagerException e) {
            logger.info("submit cycle flow failed: " + e);
            ret.put("error", "submit cycle flow failed: " + e);
        }
    }

    private Optional<Pair<Project, Flow>> validateCycleFlowParams(JsonObject json, User user, Map<String, Object> ret) {
        final String projectName = json.get("project").getAsString();
        final String flowId = json.get("flow").getAsString();
        final Project project = getProjectAjaxByPermission(ret, projectName, user, Type.EXECUTE);
        if (project == null) {
            logger.error("Project '" + projectName + "' doesn't exist.");
            ret.put("error", "Project '" + projectName + "' doesn't exist.");
            return Optional.empty();
        }
        final Flow flow = project.getFlow(flowId);
        if (flow == null) {
            logger.error("Flow '" + flowId + "' cannot be found in project " + project);
            ret.put("error", "Flow '" + flowId + "' cannot be found in project " + project);
            return Optional.empty();
        }
        return Optional.of(new Pair<>(project, flow));
    }

    private boolean isCycleFlowRunning(int projectId, String flowId) {
        ExecutionCycle nowExecutionCycle;
        try {
            nowExecutionCycle = executorManagerAdapter.getExecutionCycleFlow(String.valueOf(projectId), flowId);
        } catch (ExecutorManagerException e) {
            nowExecutionCycle = null;
        }
        if (null != nowExecutionCycle && (Status.RUNNING.equals(nowExecutionCycle.getStatus())
                || Status.PREPARING.equals(nowExecutionCycle.getStatus()))) {
            logger.error("This flow '" + flowId + "' has running cycle");
            return true;
        }
        return false;
    }

    private ExecutionCycle generateExecutionCycle(HttpServletRequest req, Project project, Flow flow, User user) throws ServletException {
        ExecutionCycle executionCycle = new ExecutionCycle();
        //todo cycleOption 的处理
        Map<String, Object> cycleOption = new HashMap<>();
        //组装循环执行数据
        executionCycle.setSubmitUser(user.getUserId());
        executionCycle.setStatus(Status.PREPARING);
        executionCycle.setProjectId(project.getId());
        executionCycle.setFlowId(flow.getId());
        executionCycle.setStartTime(System.currentTimeMillis());
        ExecutionOptions executionOptions = generateExecutionOptions(req, flow);
        executionCycle.setExecutionOptions(executionOptions);
        executionCycle.setCycleOption(cycleOption);
        executionCycle.setProxyUsers(ArrayUtils.toString(user.getProxyUsers()));
        executionCycle.setCycleErrorOption(getParam(req, "cycleErrorOption"));
        Map<String, Object> otherOption = generateOtherOptions(req);
        executionCycle.setOtherOption(otherOption);
        List<SlaOption> slaOptions = generateSlaOptions(req, project, flow);
        executionCycle.setSlaOptions(slaOptions);
        return executionCycle;
    }

    private ExecutionOptions generateExecutionOptions(HttpServletRequest req, Flow flow) throws ServletException {
        final ExecutionOptions executionOptions = HttpRequestUtils.parseFlowOptions(req);
        if (!executionOptions.isFailureEmailsOverridden()) {
            executionOptions.setFailureEmails(flow.getFailureEmails());
        }
        if (!executionOptions.isSuccessEmailsOverridden()) {
            executionOptions.setSuccessEmails(flow.getSuccessEmails());
        }
        executionOptions.setMailCreator(flow.getMailCreator());
        return executionOptions;
    }

    private Map<String, Object> generateOtherOptions(HttpServletRequest req) throws ServletException {
        Map<String, Object> otherOptions = new HashMap<>();
        //通用告警设置
        if (hasParam(req, "failureAlertLevel")) {
            otherOptions.put("failureAlertLevel", getParam(req, "failureAlertLevel"));
        }
        if (hasParam(req, "successAlertLevel")) {
            otherOptions.put("successAlertLevel", getParam(req, "successAlertLevel"));
        }
        //设置失败重跑配置
        if (hasParam(req, "jobFailedRetryOptions")) {
            Map<String, String> jobFailedRetrySettings = getParamGroup(req, "jobFailedRetryOptions");
            final List<Map<String, String>> jobRetryList = new ArrayList<>();
            for (final String set : jobFailedRetrySettings.keySet()) {
                String[] setOption = jobFailedRetrySettings.get(set).split(",");
                Map<String, String> jobOption = new HashMap<>();
                String jobName = setOption[0].trim();
                String interval = setOption[1].trim();
                String count = setOption[2].trim();
                if ("all_jobs".equals(jobName.split(" ")[0])) {
                    Map<String, String> flowFailedRetryOption = new HashMap<>();
                    flowFailedRetryOption.put("job.failed.retry.interval", interval);
                    flowFailedRetryOption.put("job.failed.retry.count", count);
                    otherOptions.put("flowFailedRetryOption", flowFailedRetryOption);
                }
                jobOption.put("jobName", jobName);
                jobOption.put("interval", interval);
                jobOption.put("count", count);
                jobRetryList.add(jobOption);


            }
            otherOptions.put("jobFailedRetryOptions", jobRetryList);
        }
        //设置失败跳过配置
        if (hasParam(req, "jobSkipFailedOptions")) {
            Map<String, String> jobSkipFailedSettings = getParamGroup(req, "jobSkipFailedOptions");
            String jobSkipActionOptions = getParam(req, "jobSkipActionOptions", "[]");
            final List<String> jobSkipActionOptionsList =
                    (List<String>) JSONUtils.parseJSONFromStringQuiet(jobSkipActionOptions);
            final List<String> jobSkipList = new ArrayList<>();
            final List<String> jobSkipActionList = new ArrayList<>();
            for (final String set : jobSkipFailedSettings.keySet()) {
                String jobName = jobSkipFailedSettings.get(set).trim();
                if ("all_jobs".equals(jobName.split(" ")[0])) {
                    otherOptions.put("flowFailedSkiped", true);
                }
                if (jobSkipActionOptionsList != null && jobSkipActionOptionsList
                        .contains(jobName)) {
                    jobSkipActionList.add(jobName);
                }
                jobSkipList.add(jobName);
            }
            otherOptions.put("jobSkipFailedOptions", jobSkipList);
            otherOptions.put("jobSkipActionOptions", jobSkipActionList);
            return otherOptions;
        }
        return otherOptions;
    }

    private List<SlaOption> generateSlaOptions(HttpServletRequest req, Project project, Flow flow) throws ServletException {
        //---超时告警设置---
        final List<SlaOption> slaOptions = new ArrayList<>();
        if (hasParam(req, "useTimeoutSetting")) {
            final String emailStr = getParam(req, "slaEmails");
            final String[] emailSplit = emailStr.split("\\s*,\\s*|\\s*;\\s*|\\s+");
            final List<String> slaEmails = Arrays.asList(emailSplit);
            final Map<String, String> settings = getParamGroup(req, "settings");
            //设置SLA 超时告警配置项
            for (final String set : settings.keySet()) {
                final SlaOption sla;
                try {
                    sla = AlertUtil.parseSlaSetting(settings.get(set), flow, project);
                } catch (final Exception e) {
                    logger.error("parse sla setting failed.");
                    throw new ServletException(e);
                }
                if (sla != null) {
                    sla.getInfo().put(SlaOption.INFO_FLOW_NAME, flow.getId());
                    sla.getInfo().put(SlaOption.INFO_EMAIL_LIST, slaEmails);
                    sla.getInfo().put(SlaOption.INFO_TIME_SET, sla.getTimeSet());
                    sla.getInfo().put(SlaOption.INFO_EMAIL_ACTION_SET, sla.getEmailAction());
                    sla.getInfo().put(SlaOption.INFO_KILL_FLOW_ACTION_SET, sla.getKillAction());
                    slaOptions.add(sla);
                }
            }
        }
        return slaOptions;
    }

    /**
     * 执行每个数据补采的 Flow 方法
     *
     * @param reqMap
     * @param repeatMap
     * @param options
     * @param user
     * @return
     * @throws Exception
     */
    private ExecutableFlow ajaxRepeatExecuteFlow(final Map<String, String> reqMap, final Map<String, Object> repeatMap,
                                                 final ExecutionOptions options, final User user, final String userId) throws Exception {

        final String flowId = reqMap.get("flowId");

        final Project project = (Project) repeatMap.get("project");

        final Flow flow = project.getFlow(flowId);

        final ExecutableFlow exflow = new ExecutableFlow(project, flow);
        exflow.setSubmitUser(user.getUserId());

        //获取项目默认代理用户
        Set<String> proxyUserSet = project.getProxyUsers();
        //设置用户代理用户
        if (null != user) {
            proxyUserSet.add(user.getUserId());
            proxyUserSet.addAll(user.getProxyUsers());
        }
        //设置当前登录的用户的代理用户
        exflow.addAllProxyUsers(proxyUserSet);
        exflow.setExecutionOptions(options);

        //设置数据补采参数
        exflow.setRepeatOption((Map<String, String>) repeatMap.get("flowRecoverOptionMap"));
        //设置Flow类型为数据补采
        exflow.setFlowType(2);
//    exflow.setRepeatId(repeatId);

//    HttpRequestUtils.filterAdminOnlyFlowParams(this.userManager, options, user);
        //调用 Flow 执行逻辑处理方法
        final String message = this.executorManagerAdapter.submitExecutableFlow(exflow, user.getUserId());
        logger.info("历史补采执行Flow日志: " + message);

        return exflow;
    }

    private static final String FILTER_BY_REPEAT_DATE_PATTERN = "yyyy/MM/dd HH:mm";

    private static final String FILTER_BY_REPEAT_DATE_PATTERN_NEW = "yyyy/MM/dd";

    /**
     * 数据补采 时间计算方法
     *
     * @param jsonObject 开始时间，结束时间，执行间隔
     * @return 按照时间执行顺序返回一个时间参数集合
     */
    private Map<String, Object> repeatDateCompute(final JsonObject jsonObject) {

        Map<String, Object> repeatOptionMap = new HashMap<>();

        List<Map<String, String>> repeatTimeList = new LinkedList<>();
        Set<Long> timeList = new HashSet<>();
        List<Long> runDateTimeList = GsonUtils.jsonToJavaObject(jsonObject.getAsJsonArray("runDateTimeList"), new TypeToken<List<Long>>() {
        }.getType());
        timeList.addAll(runDateTimeList);
        try {
            String recoverNum = jsonObject.get("recoverNum").getAsString();
            String recoverInterval = jsonObject.get("recoverInterval").getAsString();

            final String begin = jsonObject.get("begin").getAsString();

            final long beginTimeLong =
                    DateTimeFormat.forPattern(FILTER_BY_REPEAT_DATE_PATTERN_NEW).parseDateTime(begin).getMillis();

            repeatOptionMap.put("recoverStartTime", beginTimeLong);

            final String end = jsonObject.get("end").getAsString();

            final long endTimeLong =
                    DateTimeFormat.forPattern(FILTER_BY_REPEAT_DATE_PATTERN_NEW).parseDateTime(end).getMillis();

            repeatOptionMap.put("recoverEndTime", endTimeLong);

            //拼接前端的执行间隔字符串
            String executeInterval = "";
            if (!"0".equals(recoverNum)) {
                executeInterval = recoverNum + recoverInterval;
            }
            //执行间隔记录
            repeatOptionMap.put("exInterval", executeInterval);

            //设置时区
            ZoneId zoneId = ZoneId.systemDefault();

            Instant startInstant = Instant.ofEpochMilli(beginTimeLong);
            //转换成LocalDateTime对象
            LocalDateTime startTime = LocalDateTime.ofInstant(startInstant, zoneId);
            logger.info("startTime: " + startTime);

            Instant endInstant = Instant.ofEpochMilli(endTimeLong);

            LocalDateTime endTime = LocalDateTime.ofInstant(endInstant, zoneId);
            logger.info("startTime: " + endTime);

            Duration duration = Duration.between(startTime, endTime);

            long betweenDay = duration.toDays();
            long betweenHours = duration.toHours();
            long betweenMin = duration.toMinutes();

            //记录第一天数据补采时间
            //if(startTime.isEqual(endTime) || (betweenMin >= 0 && betweenMin < 1440 )){
            if (repeatTimeList.size() == 0) {
                Map<String, String> jobStartTime = new HashMap<>();
                //组装repeatOption的参数
                jobStartTime.put("RepeatType", "RepeatFlow");
                Long tmp = startTime.atZone(zoneId).toInstant().toEpochMilli();
                jobStartTime.put("startTimeLong", tmp + "");
                timeList.add(tmp);
                jobStartTime.put("recoverStatus", Status.PREPARING.getNumVal() + "");
                jobStartTime.put("exeId", "");

                repeatTimeList.add(jobStartTime);
            }
            //判断开始时间是否时一个月的最后一天
            int startDay = startTime.getDayOfMonth();
            boolean isLastDay = false;
            if (startDay == getLastDayOfMonth(startTime)) {
                isLastDay = true;
            }

            //多天数据补采时间
            while (startTime.isBefore(endTime) && !startTime.isEqual(endTime)) {
                //先记录第一次执行时间再开始相加
                //执行间隔计算
                if ("month".equals(recoverInterval)) {

                    int oldDay = startTime.getDayOfMonth();
                    int oldLastDay = getLastDayOfMonth(startTime);

                    startTime = startTime.plus(Long.valueOf(recoverNum), ChronoUnit.MONTHS);

                    int newDay = startTime.getDayOfMonth();
                    int newLastDay = getLastDayOfMonth(startTime);

                    if (isLastDay) {
                        startTime = startTime.withDayOfMonth(newLastDay);
                    } else if (!isLastDay && oldDay < startDay) {
                        startTime = startTime.withDayOfMonth(startDay);
                    }


                } else if ("week".equals(recoverInterval)) {
                    startTime = startTime.plus(Long.valueOf(recoverNum), ChronoUnit.WEEKS);
                } else if ("day".equals(recoverInterval)) {
                    startTime = startTime.plus(Long.valueOf(recoverNum), ChronoUnit.DAYS);
                }

                Map<String, String> jobStartTime = new HashMap<>();
                //组装repeatOption的参数
                jobStartTime.put("RepeatType", "RepeatFlow");
                Long tmp = startTime.atZone(zoneId).toInstant().toEpochMilli();
                jobStartTime.put("startTimeLong", tmp + "");
                jobStartTime.put("recoverStatus", Status.PREPARING.getNumVal() + "");
                jobStartTime.put("exeId", "");
                //如果开始时间大于结束时间 就补记录了
                if (startTime.isBefore(endTime) || startTime.isEqual(endTime)) {
                    repeatTimeList.add(jobStartTime);
                    timeList.add(tmp);
                }

            }

        } catch (Exception e) {
            logger.warn("Failed to compute params {}", jsonObject, e);
        }
        List<Long> skipDateTimeList = GsonUtils.jsonToJavaObject(jsonObject.getAsJsonArray("skipDateTimeList"), new TypeToken<List<Long>>() {
        }.getType());
        if (CollectionUtils.isNotEmpty(skipDateTimeList)) {
            for (Long skipDateTime : skipDateTimeList) {
                timeList.removeIf(o -> skipDateTime.equals(o));
            }
        }
        repeatTimeList.clear();
        List<Long> flowDateList = new ArrayList<>(timeList);
        Collections.sort(flowDateList);
        // 检查是否设置逆序执行
        String reverseExecuteFlag = jsonObject.get("reverseExecuteFlag").getAsString();
        if ("true".equals(reverseExecuteFlag)) {
            Collections.reverse(flowDateList);
        }
        for (Long t : flowDateList) {
            Map<String, String> jobStartTime = new HashMap<>();
            //组装repeatOption的参数
            jobStartTime.put("RepeatType", "RepeatFlow");
            jobStartTime.put("startTimeLong", t + "");
            jobStartTime.put("recoverStatus", Status.PREPARING.getNumVal() + "");
            jobStartTime.put("exeId", "");
            repeatTimeList.add(jobStartTime);
        }
        repeatOptionMap.put("repeatTimeList", repeatTimeList);

        return repeatOptionMap;
    }


    /**
     * java8(别的版本获取2月有bug) 获取某月最后一天的23:59:59
     *
     * @return
     */
    public static int getLastDayOfMonth(LocalDateTime startTime) {

        //LocalDateTime localDateTime = LocalDateTime.ofInstant(Instant.ofEpochMilli(date.getTime()), ZoneId.systemDefault());;
        LocalDateTime endOfDay = startTime.with(TemporalAdjusters.lastDayOfMonth()).with(LocalTime.MAX);
        //Date dates = Date.from(endOfDay.atZone(ZoneId.systemDefault()).toInstant());
        return endOfDay.getDayOfMonth();
    }

    /**
     * 数据补采 执行间隔参数校验
     *
     * @param req
     * @return
     * @throws ServletException
     */
    private Boolean repeatOptionCheck(final HttpServletRequest req) throws ServletException {
//    String month = getParam(req, "month");
//    String day = getParam(req, "day");
//    String hour = "0";//getParam(req, "hour");
//    String min = "0";//getParam(req, "min");

        String recoverNum = getParam(req, "recoverNum");
        String recoverInterval = getParam(req, "recoverInterval");

        if ("0".equals(recoverNum)) {
            return false;
        } else if ("".equals(recoverNum) || "".equals(recoverInterval)) {
            return false;
        }


        String[] runDateTimeList = getParamValues(req, "runDateTimeList[]", new String[]{});
        String[] skipDateTimeList = getParamValues(req, "skipDateTimeList[]", new String[]{});
        String begin = getParam(req, "begin");
        final long beginTimeLong =
                DateTimeFormat.forPattern(FILTER_BY_REPEAT_DATE_PATTERN_NEW).parseDateTime(begin).getMillis();
        String end = getParam(req, "end");
        final long endTimeLong =
                DateTimeFormat.forPattern(FILTER_BY_REPEAT_DATE_PATTERN_NEW).parseDateTime(end).getMillis();
        for (String skipDateTime : skipDateTimeList) {
            if (Arrays.stream(runDateTimeList).anyMatch(o -> o.equals(skipDateTime)) || Long.parseLong(skipDateTime) < beginTimeLong || Long.parseLong(skipDateTime) > endTimeLong) {
                return false;
            }
        }

        return true;
    }

    /**
     * 处理数据补采终止的方法
     *
     * @param req
     * @param resp
     * @param ret
     * @param user
     * @throws ServletException
     */
    private void ajaxStopRepeat(final HttpServletRequest req, final HttpServletResponse resp,
                                final HashMap<String, Object> ret, final User user)
            throws ServletException {

        final String repeatId = getParam(req, "repeatId");

        this.repeatStopMap.put(repeatId, repeatId);
        ExecutionRecover executionRecover = null;
        try {
            Integer recoverId = Integer.valueOf(repeatId);
            synchronized (Constants.HISTORY_RERUN_LOCK) {
                logger.info("set history recover flow status to killed, recover id: {}.", recoverId);
                executionRecover = this.executorManagerAdapter.getHistoryRecoverFlow(recoverId);
                List<Status> activeStatusList = Arrays
                        .asList(Status.RUNNING, Status.PREPARING, Status.PAUSED);
                if (null != executionRecover && activeStatusList
                        .contains(executionRecover.getRecoverStatus())) {
                    executionRecover.setRecoverStatus(Status.KILLED);
                    executionRecover.setEndTime(System.currentTimeMillis());
                    executorManagerAdapter.updateHistoryRecover(executionRecover);
                }
            }
            //终止 Flow 的逻辑方法
            List<ExecutableFlow> executableFlows = this.executorManagerAdapter.getExecutableFlowByRepeatId(recoverId);
            for (ExecutableFlow executableFlow : executableFlows) {
                logger.info("cancel flow: {}", executableFlow.getExecutionId());
                this.executorManagerAdapter.cancelFlow(executableFlow, user.getUserId());
            }
        } catch (final ExecutorManagerException e) {
            ret.put("error", e.getMessage());
        } finally {
            // 状态已经终止, 状态为完成,触发告警
            try {
                if (null != executionRecover && executionRecover.isFinishedAlert()) {
                    Alerter mailAlerter = alerterHolder.get("email");
                    if (null == mailAlerter) {
                        mailAlerter = alerterHolder.get("default");
                    }
                    Project project = this.projectManager.getProject(executionRecover.getProjectId());
                    executionRecover.setProjectName(project.getName());
                    mailAlerter.alertOnHistoryRecoverFinish(executionRecover);
                } else {
                    logger.error("正在运行页面KILL历史重跑, executionRecover is null or alarm not set.");
                }
            } catch (Exception e) {
                logger.error("正在运行页面KILL历史重跑, 触发历史重跑告警失败, 失败原因:{}", e);
            }
        }

    }

    /**
     * 数据补采历史页面
     *
     * @param req
     * @param resp
     * @param session
     * @throws ServletException
     */
    private void ajaxHistoryRecoverPage(final HttpServletRequest req, final HttpServletResponse resp, final Session session)
            throws ServletException {
        final Page page =
                newPage(req, resp, session, "azkaban/webapp/servlet/velocity/history-recover-page.vm");

        String languageType = LoadJsonUtils.getLanguageType();
        Map<String, String> historyRecoverPageMap;
        Map<String, String> subPageMap1;
        Map<String, String> subPageMap2;
        if ("zh_CN".equalsIgnoreCase(languageType)) {
            // 添加国际化标签
            historyRecoverPageMap = LoadJsonUtils.transJson("/conf/azkaban-web-server-zh_CN.json",
                    "azkaban.webapp.servlet.velocity.history-recover-page.vm");
            subPageMap1 = LoadJsonUtils.transJson("/conf/azkaban-web-server-zh_CN.json",
                    "azkaban.webapp.servlet.velocity.nav.vm");
            subPageMap2 = LoadJsonUtils.transJson("/conf/azkaban-web-server-zh_CN.json",
                    "azkaban.webapp.servlet.velocity.messagedialog.vm");
        } else {
            historyRecoverPageMap = LoadJsonUtils.transJson("/conf/azkaban-web-server-en_US.json",
                    "azkaban.webapp.servlet.velocity.history-recover-page.vm");
            subPageMap1 = LoadJsonUtils.transJson("/conf/azkaban-web-server-en_US.json",
                    "azkaban.webapp.servlet.velocity.nav.vm");
            subPageMap2 = LoadJsonUtils.transJson("/conf/azkaban-web-server-en_US.json",
                    "azkaban.webapp.servlet.velocity.messagedialog.vm");
        }
        historyRecoverPageMap.forEach(page::add);
        subPageMap1.forEach(page::add);
        subPageMap2.forEach(page::add);
        page.add("currentlangType", languageType);
        int pageNum = getIntParam(req, "page", 1);
        final int pageSize = getIntParam(req, "size", 16);

        if (pageNum < 0) {
            pageNum = 1;
        }

        final User user = session.getUser();
        Set<String> userRoleSet = new HashSet<>();
        userRoleSet.addAll(user.getRoles());

        List<ExecutionRecover> historyRecover = new ArrayList<>();

        //添加权限判断 admin 用户能查看所有flow历史 user用户只能查看自己的flow历史
        if (userRoleSet.contains("admin")) {
            //查询数据补采全部记录
            try {
                historyRecover = this.executorManagerAdapter.listHistoryRecoverFlows(new HashMap(), (pageNum - 1) * pageSize, pageSize);
            } catch (ExecutorManagerException e) {
                logger.error("查询数据补采全部记录失败,原因为:", e);
            }
        } else {
            try {

                Map paramMap = new HashMap();

                paramMap.put("userName", user.getUserId());

                historyRecover =
                        this.executorManagerAdapter.listHistoryRecoverFlows(paramMap, (pageNum - 1) * pageSize, pageSize);
            } catch (ExecutorManagerException e) {
                logger.warn("Failed to listHistoryRecoverFlows, username {}", user.getUserId(), e);
            }
        }

        if (null != historyRecover && !historyRecover.isEmpty()) {
            historyRecover.stream().forEach(historyFlow -> {
                Map<String, Object> repeatMap = historyFlow.getRepeatOption();
                if (!repeatMap.isEmpty()) {
                    List<Map<String, String>> list = (List<Map<String, String>>) repeatMap.get("repeatTimeList");
                    for (Map<String, String> repMap : list) {
                        if (!repMap.get("exeId").isEmpty() && historyFlow.getNowExecutionId() == Integer.valueOf(repMap.get("exeId"))) {
                            historyFlow.setUpdateTime(Long.valueOf(String.valueOf(repMap.get("startTimeLong"))));
                        }
                    }
                }
            });
        }

        page.add("historyRecover", historyRecover.isEmpty() ? null : historyRecover);

        page.add("vmutils", new VelocityUtil(this.projectManager));

        page.add("size", pageSize);
        page.add("page", pageNum);

        if (pageNum == 1) {
            page.add("previous", new PageSelection(1, pageSize, true, false));
        } else {
            page.add("previous", new PageSelection(pageNum - 1, pageSize, false, false));
        }
        page.add("next", new PageSelection(pageNum + 1, pageSize, false, false));
        // Now for the 5 other values.
        int pageStartValue = 1;
        if (pageNum > 3) {
            pageStartValue = pageNum - 2;
        }

        page.add("page1", new PageSelection(pageStartValue, pageSize, false,
                pageStartValue == pageNum));
        pageStartValue++;
        page.add("page2", new PageSelection(pageStartValue, pageSize, false,
                pageStartValue == pageNum));
        pageStartValue++;
        page.add("page3", new PageSelection(pageStartValue, pageSize, false,
                pageStartValue == pageNum));
        pageStartValue++;
        page.add("page4", new PageSelection(pageStartValue, pageSize, false,
                pageStartValue == pageNum));
        pageStartValue++;
        page.add("page5", new PageSelection(pageStartValue, pageSize, false,
                pageStartValue == pageNum));
        pageStartValue++;


        page.render();

    }

//  /**
//   * 日志按级别分割处理方法
//   * @param logData
//   * @param logType
//   * @return
//   */
//  private String handleLogData(String logData, String logType){
//    String logResult = "";
//
//    String nowCutType = "";
//
//    StringBuilder sb = new StringBuilder();
//
//    String[] handleData = logData.split("\n");
//
//    if("error".equals(logType)){
//      String errorBegin = "";
//      for(String lineLog : handleData){
//        //ERROR日志切割
//        if(lineLog.contains(" ERROR - ")){
//          errorBegin = "START";
//          nowCutType = "ERROR";
//          sb.append(lineLog).append("\n");
//        }else if((lineLog.contains(" ERROR - ") || lineLog.contains(" INFO - ")) && "START".equals(errorBegin) && "ERROR".equals(nowCutType)){
//          errorBegin = "END";
//        }else if("START".equals(errorBegin) && "ERROR".equals(nowCutType)){
//          sb.append(lineLog).append("\n");
//        }
//        //INFO 日志里的异常情况
//        if(lineLog.contains(" INFO - ") && (lineLog.contains("Exception") || lineLog.contains("Error"))){
//          errorBegin = "START";
//          nowCutType = "INFO";
//          sb.append(lineLog).append("\n");
//        }else if(lineLog.contains(" INFO - ") && !lineLog.contains("Exception") && !lineLog.contains(" INFO - \t") && "INFO".equals(nowCutType)){
//          errorBegin = "END";
//        }else if("START".equals(errorBegin) && "INFO".equals(nowCutType)){
//          sb.append(lineLog).append("\n");
//        }
//      }
//    }else if("info".equals(logType)){
//      for(String lineLog : handleData){
//        if(lineLog.contains(" INFO - ") && !lineLog.contains("Exception") && !lineLog.contains(" INFO - \t") ){
//          sb.append(lineLog).append("\n");
//        }
//      }
//    }
//    logResult = sb.toString();
//
//    return logResult;
//  }

    /**
     * 数据补采 执行数据校验 通过之后才能执行数据补采
     *
     * @param req
     * @param resp
     * @param ret
     * @param user
     * @throws ServletException
     */
    private void ajaxRecoverParamVerify(final HttpServletRequest req, final HttpServletResponse resp,
                                        final HashMap<String, Object> ret, final User user) throws ServletException {

        final String projectName = getParam(req, "project");
        final String flowId = getParam(req, "flow");

        if (this.holdBatchSwitch && StringUtils.isNotEmpty(this.holdBatchContext
                .isInBatch(projectName, flowId, user.getUserId()))) {
            ret.put("error", "server is holding, reject all operation");
            return;
        }
        // 检查项目是否存在，工作流基于project这一层级
        final Project project = getProjectAjaxByPermission(ret, projectName, user, Type.EXECUTE);

        Map<String, String> dataMap = loadExecutorServletI18nData();

        if (project == null) {
            ret.put("error", "Project '" + projectName + "' doesn't exist.");
            return;
        }
        ret.put("flow", flowId);
        // 检查工作流是否存在
        final Flow flow = project.getFlow(flowId);
        if (flow == null) {
            ret.put("error", "Flow '" + flowId + "' cannot be found in project "
                    + project);
            return;
        }

        //check is set business
        boolean hasBusiness = this.projectManager.getFlowBusiness(project.getId(), "", "") != null
                || this.projectManager.getFlowBusiness(project.getId(), flowId, "") != null;
        if (getApplication().getServerProps().getBoolean("wtss.set.business.check", true)
                && !hasBusiness) {
            ret.put("error", dataMap.get("flow") + flow.getId() + dataMap.get("setBusiness"));
            return;
        }

        //check email name
        final boolean failureOverride = HttpRequestUtils
                .getBooleanParam(req, "failureEmailsOverride", false);
        final boolean successOverride = HttpRequestUtils
                .getBooleanParam(req, "successEmailsOverride", false);
        List<WebankUser> userList = systemManager.findAllWebankUserList(null);
        if (hasParam(req, "failureEmails")) {
            final String emails = getParam(req, "failureEmails");
            if (!emails.isEmpty()) {
                final String[] emailSplit = emails.split("\\s*,\\s*|\\s*;\\s*|\\s+");
                if (this.checkRealNameSwitch && WebUtils.checkEmailNotRealName(
                        Lists.newArrayList(emailSplit), failureOverride,
                        userList)) {
                    ret.put("error",
                            "Please configure the correct real-name user for failure email");
                    return;
                }
            }
        }
        if (hasParam(req, "successEmails")) {
            final String emails = getParam(req, "successEmails");
            if (!emails.isEmpty()) {
                final String[] emailSplit = emails.split("\\s*,\\s*|\\s*;\\s*|\\s+");
                if (this.checkRealNameSwitch && WebUtils.checkEmailNotRealName(
                        Lists.newArrayList(emailSplit), successOverride,
                        userList)) {
                    ret.put("error",
                            "Please configure the correct real-name user for success email");
                    return;
                }
            }
        }

        boolean isCycleFlowRunning = isCycleFlowRunning(project.getId(), flow.getId());
        if (isCycleFlowRunning) {
            ret.put("error", "Exist cyclic execution job, please wait until that job run finished then commit again.");
            return;
        }

        ExecutionRecover nowRuningRecover = null;
        //查询这个Flow的补采记录
        try {
            nowRuningRecover =
                    this.executorManagerAdapter.getHistoryRecoverFlowByPidAndFid(String.valueOf(project.getId()), flow.getId());
        } catch (ExecutorManagerException e) {
            logger.error("获取历史重跑任务失败", e);
        }
        //校验这个Flow是否已经开始补采
        if (null != nowRuningRecover && (Status.RUNNING.equals(nowRuningRecover.getRecoverStatus())
                || Status.PREPARING.equals(nowRuningRecover.getRecoverStatus()))) {
            logger.error("recover id: " + nowRuningRecover.getRecoverId() + ", status : " + nowRuningRecover.getRecoverStatus());
            ret.put("error", dataMap.get("existHisJob"));
            return;
        }

        //concurrentOption,
        final String concurrentOption = getParam(req, "concurrentOption");
        //判断flow是否真正运行
        if ("skip".equals(concurrentOption) && this.executorManagerAdapter.getRunningFlows(project.getId(), flowId).size() != 0) {
            ret.put("error", "flow: " + flowId + dataMap.get("runningCanNotCommit"));
            return;
        }

        //参数校验
        if (!repeatOptionCheck(req)) {
            ret.put("error", dataMap.get("dataCompensateinputParamError"));
            return;
        }

    }


    /**
     * Job日志下载处理方法
     * 根据 execId 和 jobId 查找到对应的日志，然后读取到web节点的temp中的
     */
    private void handleDownloadExecLog(final HttpServletRequest req, final HttpServletResponse resp, final Session session)
            throws ServletException, IOException {

        final User user = session.getUser();
        final int execId = getIntParam(req, "execid");
        final String jobName = getParam(req, "job");
        logger.info(user.getUserId() + " is downloading log execId: " + execId);

        FileInputStream inStream = null;
        OutputStream outStream = null;

        String logZipPath = "";

        Map<String, String> dataMap = loadExecutorServletI18nData();

        try {
            logZipPath = this.executorManagerAdapter.getJobLogByJobId(execId, jobName);

            final File logZipFile = new File(logZipPath);

            if (!logZipFile.exists()) {
                this.setErrorMessageInCookie(resp, dataMap.get("job") + jobName + dataMap.get("haveNoLog"));
                resp.sendRedirect(req.getContextPath() + "executor?execid=" + execId + "#jobslist");
                return;
            }

            // now set up HTTP response for downloading file
            inStream = new FileInputStream(logZipFile);

            resp.setContentType("application/zip");

            final String headerKey = "Content-Disposition";
            final String headerValue = String.format("attachment; filename=\"%s\"", logZipFile.getName());
            resp.setHeader(headerKey, headerValue);
            resp.setHeader("execId", execId + "");

            outStream = resp.getOutputStream();

            final byte[] buffer = new byte[8192];
            int bytesRead = -1;

            while ((bytesRead = inStream.read(buffer)) != -1) {
                outStream.write(buffer, 0, bytesRead);
            }

        } catch (final Throwable e) {
            logger.error("", e);
            throw new ServletException(e);
        } finally {
            IOUtils.closeQuietly(inStream);
            IOUtils.closeQuietly(outStream);
            if (!logZipPath.isEmpty()) {
                //先删除压缩包
                FileIOUtils.deleteDir(new File(logZipPath));
                logZipPath = logZipPath.replace(".zip", "");
                //再删除临时文件夹
                FileIOUtils.deleteDir(new File(logZipPath));
            }
        }

    }

    private Map<String, Object> getExecutableNodeInfo(final ExecutableNode node, final Long runDate,
                                                      int executionId, Map<String, String> comments) {

        final HashMap<String, Object> nodeObj = new HashMap<>();
        nodeObj.put("id", node.getId());
        //失败跳过特殊颜色
        if ("FAILED_SKIPPED".equals(node.getLastStatus())) {
            nodeObj.put("status", "FAILED_SKIPPED_DISABLED");
        } else {
            nodeObj.put("status", node.getStatus());
        }

        nodeObj.put("startTime", node.getStartTime());
        nodeObj.put("endTime", node.getEndTime());
        nodeObj.put("updateTime", node.getUpdateTime());
        nodeObj.put("type", node.getType());
        nodeObj.put("nestedId", node.getNestedId());
        nodeObj.put("runDate", runDate);
        String comment = StringUtils.isNotEmpty(comments.get(node.getId())) ? comments.get(node.getId()) : "";
        nodeObj.put("comment", comment);
        if (node.getCondition() != null) {
            nodeObj.put("condition", node.getCondition());
        }

        nodeObj.put("attempt", node.getAttempt());
        if (node.getAttempt() > 0) {
            nodeObj.put("pastAttempts", node.getAttemptObjects());
        }

        if (node.getInNodes() != null && !node.getInNodes().isEmpty()) {
            nodeObj.put("in", node.getInNodes());
        }
        nodeObj.put("isElasticNode", node.isElasticNode());
        nodeObj.put("sourceNodeId", node.getSourceNodeId());

        if (node instanceof ExecutableFlowBase) {
            final ExecutableFlowBase base = (ExecutableFlowBase) node;
            final ArrayList<Map<String, Object>> nodeList = new ArrayList<>();

            for (final ExecutableNode subNode : base.getExecutableNodes()) {
                final Map<String, Object> subNodeObj = getExecutableNodeInfo(subNode, runDate, executionId, comments);
                if (!subNodeObj.isEmpty()) {
                    nodeList.add(subNodeObj);
                }
            }
            nodeObj.put("execid", executionId);
            nodeObj.put("flow", base.getFlowId());
            nodeObj.put("nodes", nodeList);
            nodeObj.put("flowId", base.getFlowId());
        }

        return nodeObj;
    }

    public static class PageSelection {

        private final int page;
        private final int size;
        private final boolean disabled;
        private boolean selected;

        public PageSelection(final int page, final int size, final boolean disabled,
                             final boolean selected) {
            this.page = page;
            this.size = size;
            this.disabled = disabled;
            this.setSelected(selected);
        }

        public int getPage() {
            return this.page;
        }

        public int getSize() {
            return this.size;
        }

        public boolean getDisabled() {
            return this.disabled;
        }

        public boolean isSelected() {
            return this.selected;
        }

        public void setSelected(final boolean selected) {
            this.selected = selected;
        }
    }

    private Permission getPermissionObject(final Project project, final User user,
                                           final Permission.Type type) {
        final Permission perm = project.getCollectivePermission(user);

        for (final String roleName : user.getRoles()) {
            if ("admin".equals(roleName) || systemManager.isDepartmentMaintainer(user)) {
                perm.addPermission(Type.ADMIN);
            }
        }

        return perm;
    }

    /**
     * 临时执行或者单次执行
     *
     * @param projectName
     * @param flowName
     * @param ret
     * @param user
     * @throws ServletException
     */
    private void ajaxFetchExecutionFlowGraphNew(final String projectName, final String flowName,
                                                final HashMap<String, Object> ret, final User user) throws ServletException {

        final Project project = getProjectAjaxByPermission(ret, projectName, user, Type.EXECUTE);
        if (project == null) {
            return;
        }
        try {
            final ExecutionOptions executionOptions = new ExecutionOptions();
            final Flow flow = project.getFlow(flowName);
            if (flow == null) {
                ret.put("error", "Flow '" + flowName + "' cannot be found in project " + project);
                return;
            }
            final ExecutableFlow exFlow = new ExecutableFlow(project, flow);
            exFlow.setExecutionOptions(executionOptions);
            ret.put("submitTime", exFlow.getSubmitTime());
            ret.put("submitUser", exFlow.getSubmitUser());
            ret.put("execid", exFlow.getExecutionId());
            ret.put("projectId", exFlow.getProjectId());
            ret.put("project", project.getName());
            FlowUtils.applyDisabledJobs(executionOptions.getDisabledJobs(), exFlow);
            final Map<String, Object> flowObj = getExecutableNodeInfo(project, exFlow, exFlow.getExecutionId());
            ret.putAll(flowObj);
        } catch (final Exception ex) {
            throw new ServletException(ex);
        }
    }

//    private void ajaxFetchExecutionFlowGraph(final String projectName, final String flowName,
//        final HashMap<String, Object> ret, final User user) throws ServletException {
//
//        final Project project = getProjectAjaxByPermission(ret, projectName, user, Type.EXECUTE);
//        if (project == null) {
//            ret.put("error", "Project '" + projectName + "' doesn't exist.");
//            return;
//        }
//        try {
//            final Schedule schedule = this.scheduleManager.getSchedule(project.getId(), flowName);
//            // 读取是否存在调度在执行,但是读取这个调度会拿到调度设置的一些旧数据,影响临时执行
//            final ExecutionOptions executionOptions = schedule != null ? schedule.getExecutionOptions() : new ExecutionOptions();
//            // final ExecutionOptions executionOptions =  new ExecutionOptions();
//            final Flow flow = project.getFlow(flowName);
//            if (flow == null) {
//                ret.put("error", "Flow '" + flowName + "' cannot be found in project " + project);
//                return;
//            }
//            final ExecutableFlow exFlow = new ExecutableFlow(project, flow);
//            exFlow.setExecutionOptions(executionOptions);
//            ret.put("submitTime", exFlow.getSubmitTime());
//            ret.put("submitUser", exFlow.getSubmitUser());
//            ret.put("execid", exFlow.getExecutionId());
//            ret.put("projectId", exFlow.getProjectId());
//            ret.put("project", project.getName());
//            FlowUtils.applyDisabledJobs(executionOptions.getDisabledJobs(), exFlow);
//            final Map<String, Object> flowObj = getExecutableNodeInfo(exFlow, exFlow.getExecutionId());
//            ret.putAll(flowObj);
//        } catch (final ScheduleManagerException ex) {
//            throw new ServletException(ex);
//        }
//    }

    private void ajaxFetchExecutionFlowGraph(final HttpServletRequest req, final String projectName, final String flowName, final HashMap<String, Object> ret, final User user) throws ServletException {

        final Project project = getProjectAjaxByPermission(ret, projectName, user, Type.EXECUTE);
        if (project == null) {
            ret.put("error", "Project '" + projectName + "' doesn't exist.");
            return;
        }
        try {
            final Schedule schedule = this.scheduleManager.getSchedule(project.getId(), flowName);
            // 读取是否存在调度在执行,但是读取这个调度会拿到调度设置的一些旧数据,影响临时执行
            final ExecutionOptions executionOptions = schedule != null ? schedule.getExecutionOptions() : new ExecutionOptions();
            // final ExecutionOptions executionOptions =  new ExecutionOptions();
            final Flow flow = project.getFlow(flowName);
            if (flow == null) {
                ret.put("error", "Flow '" + flowName + "' cannot be found in project " + project);
                return;
            }
            final ExecutableFlow exFlow = new ExecutableFlow(project, flow);
            List<ExecutableFlowBase> elasticFlowList = new ArrayList<>();
            FlowUtils.getAllElasticFlow(exFlow, elasticFlowList);
            if (!CollectionUtils.isEmpty(elasticFlowList)) {
                int lastExecId = getIntParam(req, "lastExecId", -1);
                ExecutableFlow lastExecutableFlow = this.executorManagerAdapter.getExecutableFlow(lastExecId);
                FlowUtils.findAndReplace(lastExecutableFlow, exFlow, elasticFlowList);
            }
            exFlow.setExecutionOptions(executionOptions);
            ret.put("submitTime", exFlow.getSubmitTime());
            ret.put("submitUser", exFlow.getSubmitUser());
            ret.put("execid", exFlow.getExecutionId());
            ret.put("projectId", exFlow.getProjectId());
            ret.put("project", project.getName());
            FlowUtils.applyDisabledJobs(executionOptions.getDisabledJobs(), exFlow);
            final Map<String, Object> flowObj = getExecutableNodeInfo(project, exFlow, exFlow.getExecutionId());
            ret.putAll(flowObj);
        } catch (final Exception ex) {
            throw new ServletException(ex);
        }
    }

    private void ajaxGetTenantRunningFlows(final HttpServletRequest req, final User user,
                                           final HashMap<String, Object> ret) {

        List<ExecutableFlow> runningFlowList = new ArrayList<>();
        HashMap<String, List<Map<String, Object>>> data = new HashMap();

        try {
            // 检查调用接口的用户是否为系统管理员
            if (!(user.hasRole("admin"))) {
                ret.put("code", 403);
                ret.put("message", "Permission denied. Need ADMIN access.");
                return;
            }
            // 检查租户是否为空
            String departmentId = getParam(req, "departmentId");
            if (StringUtils.isEmpty(departmentId)) {
                ret.put("code", 400);
                ret.put("message", "Error params. Param departmentId can not be empty.  ");
                return;
            }

            // 查询部门运行中的工作流
            HistoryQueryParam queryParam = new HistoryQueryParam();
            String status = Status.nonFinishingStatusAfterFlowStartsSet.stream()
                    .map(s -> String.valueOf(s.getNumVal()))
                    .collect(Collectors.joining(","));
            queryParam.setStatus(status);
            queryParam.setFlowType(-1);
            List<String> deptIds = Arrays.asList(departmentId.split(","));
            for (String deptId : deptIds) {
                queryParam.setDepartmentId(deptId);
                List<ExecutableFlow> list = this.executorManagerAdapter.getExecutableFlows(queryParam, -1, -1);
                runningFlowList.addAll(list);
            }

            List<Map<String, Object>> runningFlowData = new ArrayList<>();
            List<Map<String, Object>> runningJobData = new ArrayList<>();
            for (ExecutableFlow flowInfo : runningFlowList) {
                // 工作流
                int execId = flowInfo.getExecutionId();
                HashMap<String, Object> flowMap = new HashMap<>();
                flowMap.put("execId", execId);
                flowMap.put("flowId", flowInfo.getFlowId());
                flowMap.put("flowType", flowInfo.getFlowType());
                flowMap.put("status", flowInfo.getStatus().getNumVal());
                flowMap.put("runtime", (DateTime.now().getMillis() - flowInfo.getStartTime()));
                runningFlowData.add(flowMap);
                // 任务
                for (ExecutableNode node : flowInfo.getExecutableNodes()) {
                    if (!(node instanceof ExecutableFlowBase)
                            && Status.isStatusRunning(node.getStatus())) {
                        HashMap<String, Object> jobMap = new HashMap<>();
                        String flowId = node.getParentFlow().getFlowId();
                        jobMap.put("execId", execId);
                        jobMap.put("flowId", flowId);
                        jobMap.put("jobId", node.getId());
                        jobMap.put("jobType", node.getType());
                        jobMap.put("status", node.getStatus().getNumVal());
                        jobMap.put("runtime", (DateTime.now().getMillis() - node.getStartTime()));
                        runningJobData.add(jobMap);
                    }
                }
            }
            data.put("runningFlows", runningFlowData);
            data.put("runningJobs", runningJobData);

        } catch (Exception e) {
            ret.put("code", 500);
            ret.put("message", e.getMessage());
            return;
        }

        ret.put("code", 200);
        ret.put("data", data);
    }


    /**
     * 工作流任务失败率上报
     *
     * @param req
     * @param user
     * @param ret
     */
    private void ajaxFetchFlowsReportMetrics(final HttpServletRequest req, final User user,
                                             final HashMap<String, Object> ret) {

        // 检查调用接口的用户是否为系统管理员
        if (!(user.hasRole("admin"))) {
            ret.put("code", 403);
            ret.put("message", "当前用户不为系统管理员，无法进行权限上报。");
            return;
        }

        logger.info("Starting to fetch data of flows failed rate");

        // 告警阈值
        final double flowFailedRateLimit = Double.parseDouble(req.getParameter("flowFailedRateLimit"));
        final double jobFailedRateLimit = Double.parseDouble(req.getParameter("jobFailedRateLimit"));

        long startTime;
        // 凌晨0点-9点之内不做通知，9点时间段需要统计0-9点之内的数据
        if (LocalTime.now().isAfter(LocalTime.of(9, 0))
                && LocalTime.now().isBefore(LocalTime.of(10, 0))) {
            startTime = DateUtils.getStartOfTodayInMills();
        }
        // 其它时间统计近一个小时
        else {
            startTime = System.currentTimeMillis() - (60 * 60 * 1000);
        }

        // 近1小时的工作流和任务
        List<ExecutableFlow> flowList;
        List<ExecutableJobInfo> jobList;
        try {
            flowList = this.executorManagerAdapter.fetchExecutableFlows(startTime);
            jobList = this.executorManagerAdapter.fetchExecutableJobInfo(startTime);
        } catch (SQLException e) {
            ret.put("code", 500);
            ret.put("message", "Get executable flows failed: " + e.getMessage());
            return;
        } catch (ExecutorManagerException e) {
            ret.put("code", 500);
            ret.put("message", "Get executable jobs failed: " + e.getMessage());
            return;
        }

        // 统计指标 <部门, <工作流/任务, 失败率>>
        Map<String, Map<String, Double>> reportMetrics = new HashMap<>();
        double flowFailedRate = 0, jobFailedRate = 0;

        // 统计工作流
        if (flowList != null && !flowList.isEmpty()) {

            // 根据部门、工作流失败和其他状态分组统计 <部门, <状态, 计数>>
            Map<String, Map<Status, Long>> flowStatusCount = flowList.stream()
                    .collect(Collectors.groupingBy(
                            ExecutableFlow::getSubmitDepartmentId,
                            Collectors.groupingBy(
                                    flow -> Status.isFailed(flow.getStatus()) ? Status.FAILED : flow.getStatus(),
                                    Collectors.counting()
                            )
                    ));

            // 统计每个部门的工作流失败率
            summaryDepartmentFailedRate(reportMetrics, flowStatusCount, "flow");

            // 统计整体工作流失败率
            long totalFailed = flowList.stream()
                    .filter(flow -> Status.isFailed(flow.getStatus()))
                    .count();
            flowFailedRate = totalFailed * 1.0 / flowList.size();
        }

        // 统计任务
        if (jobList != null && !jobList.isEmpty()) {

            // 根据部门、任务失败和其他状态分组统计
            Map<String, Map<Status, Long>> jobStatusCount = jobList.stream()
                    .collect(Collectors.groupingBy(
                            ExecutableJobInfo::getSubmitDepartmentId,
                            Collectors.groupingBy(
                                    job -> Status.isFailed(job.getStatus()) ? Status.FAILED : job.getStatus(),
                                    Collectors.counting()
                            )
                    ));

            // 统计每个部门的任务失败率
            summaryDepartmentFailedRate(reportMetrics, jobStatusCount, "job");

            // 统计整体任务失败率
            long totalFailed = jobList.stream()
                    .filter(job -> Status.isFailed(job.getStatus()))
                    .count();
            jobFailedRate = totalFailed * 1.0 / jobList.size();
        }

        // 封装数据
        Map<String, String> result = new TreeMap<>();
        if (Double.compare(jobFailedRate, jobFailedRateLimit) > 0 ||
                Double.compare(flowFailedRate, flowFailedRateLimit) > 0) {
            result.put("overallFailedRate", String.format("flow_failed_rate=%.4f, job_failed_rate=%.4f",
                    flowFailedRate, jobFailedRate));
        }
        for (Map.Entry<String, Map<String, Double>> entry : reportMetrics.entrySet()) {
            boolean alertFlag = entry.getValue().entrySet().stream()
                    .anyMatch(e -> {
                        String k = e.getKey();
                        double v = e.getValue();
                        return ((("flow_failed_rate").equals(k) && Double.compare(v, flowFailedRateLimit) > 0) ||
                                (("job_failed_rate").equals(k) && Double.compare(v, jobFailedRateLimit) > 0));

                    });
            if (alertFlag) {
                String failedRateInfo = entry.getValue().entrySet().stream().map(e -> e.getKey() + "=" + e.getValue())
                        .collect(Collectors.joining(", "));
                result.put(entry.getKey(), failedRateInfo);
            }
        }

        logger.info("fetch data of flows failed rate is successfully finished.");
        ret.put("code", 200);
        ret.put("data", result);
    }

    /**
     * 统计部门工作流、任务失败率
     *
     * @param reportMetrics
     * @param statusCountMap
     * @param type
     */
    private void summaryDepartmentFailedRate(Map<String, Map<String, Double>> reportMetrics,
                                             Map<String, Map<Status, Long>> statusCountMap, String type) {
        final String summaryKey = "job".equals(type) ? "job_failed_rate" : "flow_failed_rate";
        for (Map.Entry<String, Map<Status, Long>> entry : statusCountMap.entrySet()) {
            String departmentId = entry.getKey();
            Map<Status, Long> statusCount = entry.getValue();
            long total = statusCount.values().stream().mapToLong(Long::longValue).sum();
            if (total == 0) {
                continue;
            }
            double failedRate = statusCount.getOrDefault(Status.FAILED, 0L) * 1.0 / total;
            BigDecimal val = new BigDecimal(failedRate).setScale(4, BigDecimal.ROUND_HALF_UP);
            reportMetrics.computeIfAbsent(departmentId, k -> new HashMap<>()).put(summaryKey, val.doubleValue());
        }
    }


    /**
     * 对外开放的Job日志查询接口
     * 根据项目名称，工作流名称，任务名称获取任务日志
     *
     * @param req
     * @param resp
     * @param ret
     * @param user
     * @throws ServletException
     */
    private void extGetRecentJobLog(final HttpServletRequest req, final HttpServletResponse resp,
                                    final HashMap<String, Object> ret, final User user) throws ServletException {

        final String projectName = getParam(req, "projectName");
        final String flowName = getParam(req, "flowName");
        final String jobName = getParam(req, "jobName");

        final Project project = getProjectAjaxByPermission(ret, projectName, user, Type.READ);

        Map<String, String> dataMap = loadExecutorServletI18nData();

        if (project == null) {
            respMessageBuild("100001", ret, dataMap.get("project") + projectName + dataMap.get("notExist"), projectName);
            return;
        }

        final Flow flow = project.getFlow(flowName);
        if (flow == null) {
            respMessageBuild("100002", ret, dataMap.get("flow") + flowName + dataMap.get("notExist"), flowName);
            return;
        }

        resp.setCharacterEncoding("utf-8");

        try {
            final ExecutableFlow exFlow = this.executorManagerAdapter.getRecentExecutableFlow(project.getId(), flow.getId());

            final ExecutableNode node = exFlow.getExecutableNodePath(jobName);
            if (node == null) {
                respMessageBuild("100003", ret, dataMap.get("job") + jobName + dataMap.get("notExist"), jobName);
                return;
            }
            final int attempt = this.getIntParam(req, "attempt", node.getAttempt());
            //获取Job所有日志
            final String data = this.executorManagerAdapter.getAllExecutionJobLog(exFlow, jobName, attempt);

            String htmlStr = StringEscapeUtils.escapeHtml(data);
            htmlStr = StringEscapeUtils.unescapeHtml(htmlStr);
            respMessageBuild("200", ret, "success", jobName);
            ret.put("jobLog", htmlStr);

        } catch (final ExecutorManagerException | IOException e) {
            respMessageBuild("100004", ret, dataMap.get("getLatestLogFailed"), e.getMessage());
            throw new ServletException(e);
        }
    }

    /**
     * 对外开放的Job状态查询接口
     * 根据项目名称，工作流名称，任务名称获取任务状态
     *
     * @param req
     * @param resp
     * @param ret
     * @param user
     * @throws ServletException
     */
    private void extGetRecentJobStatus(final HttpServletRequest req, final HttpServletResponse resp,
                                       final HashMap<String, Object> ret, final User user) throws ServletException {

        final String projectName = getParam(req, "projectName");
        final String flowName = getParam(req, "flowName");
        final String jobName = getParam(req, "jobName");

        final Project project = getProjectAjaxByPermission(ret, projectName, user, Type.READ);
        if (project == null) {
            respMessageBuild("100001", ret, ret.get("error").toString(), projectName);
            return;
        }
        Map<String, String> dataMap = loadExecutorServletI18nData();
        final Flow flow = project.getFlow(flowName);
        if (flow == null) {
            respMessageBuild("100002", ret, dataMap.get("flow") + flowName + dataMap.get("notExist"), flowName);
            return;
        }

        resp.setCharacterEncoding("utf-8");

        try {
            final ExecutableFlow exFlow = this.executorManagerAdapter.getRecentExecutableFlow(project.getId(), flow.getId());

            final ExecutableNode node = exFlow.getExecutableNodePath(jobName);
            if (node == null) {
                respMessageBuild("100003", ret, dataMap.get("job") + jobName + dataMap.get("notExist"), flowName);
                return;
            }

            respMessageBuild("200", ret, "success");
            ret.put("jobStatus", node.getStatus());

        } catch (final ExecutorManagerException e) {
            respMessageBuild("100005", ret, dataMap.get("getLatestExecStatusFailed"), e.getMessage());
            throw new ServletException(e);
        }
    }

    /**
     * 对外开放的工作流执行接口
     * 根据项目名称，工作流名称，执行工作流
     *
     * @param req
     * @param resp
     * @param ret
     * @param user
     * @throws ServletException
     */
    private void extExecuteFlow(final HttpServletRequest req, final HttpServletResponse resp,
                                final HashMap<String, Object> ret, final User user) throws ServletException {

        final String projectName = getParam(req, "projectName");
        final String flowName = getParam(req, "flowName");

        final Project project = getProjectAjaxByPermission(ret, projectName, user, Type.EXECUTE);
        if (project == null) {
            respMessageBuild("100001", ret, ret.get("error").toString(), projectName);
            return;
        }

        Map<String, String> dataMap = loadExecutorServletI18nData();

        final Flow flow = project.getFlow(flowName);
        if (flow == null) {
            respMessageBuild("100002", ret, dataMap.get("flow") + flowName + dataMap.get("notExist"), flowName);
            return;
        }

        final ExecutableFlow exflow = FlowUtils.createExecutableFlow(project, flow);
        exflow.setSubmitUser(user.getUserId());
        //获取项目默认代理用户
        Set<String> proxyUserSet = project.getProxyUsers();
        //设置用户代理用户
        proxyUserSet.add(user.getUserId());
        proxyUserSet.addAll(user.getProxyUsers());
        //设置代理用户
        exflow.addAllProxyUsers(proxyUserSet);
        ExecutionOptions options = null;
        try {
            options = HttpRequestUtils.parseFlowOptions(req);
        } catch (Exception e) {
            ret.put("error", e.getMessage());
            return;
        }

        final List<String> failureEmails = options.getFailureEmails();
        List<WebankUser> userList = systemManager.findAllWebankUserList(null);
        if (this.checkRealNameSwitch && WebUtils
                .checkEmailNotRealName(failureEmails, options.isFailureEmailsOverridden(), userList)) {
            ret.put("error", "Please configure the correct real-name user for failure email");
            return;
        }
        final List<String> successEmails = options.getSuccessEmails();
        if (this.checkRealNameSwitch && WebUtils
                .checkEmailNotRealName(successEmails, options.isSuccessEmailsOverridden(), userList)) {
            ret.put("error", "Please configure the correct real-name user for success email");
            return;
        }
        exflow.setExecutionOptions(options);
        if (!options.isFailureEmailsOverridden()) {
            options.setFailureEmails(flow.getFailureEmails());
        }
        if (!options.isSuccessEmailsOverridden()) {
            options.setSuccessEmails(flow.getSuccessEmails());
        }
        options.setMailCreator(flow.getMailCreator());

        //设置其他参数配置
        Map<String, Object> otherOptions = new HashMap<>();

        //设置失败重跑配置
//    final Map<String, String> jobFailedRetrySettings = getParamGroup(req, "jobFailedRetryOptions");
//    final List<Map<String, String>> jobRetryList = new ArrayList<>();
//    for (final String set : jobFailedRetrySettings.keySet()) {
//      String[] setOption = jobFailedRetrySettings.get(set).split(",");
//      Map<String, String> jobOption = new HashMap<>();
//      jobOption.put("jobName", setOption[0].trim());
//      jobOption.put("interval", setOption[1].trim());
//      jobOption.put("count", setOption[2].trim());
//      jobRetryList.add(jobOption);
//    }
//
//    otherOptions.put("jobFailedRetryOptions", jobRetryList);
//    exflow.setOtherOption(otherOptions);

        //设置失败跳过配置
        final Map<String, String> jobSkipFailedSettings = getParamGroup(req, "jobSkipFailedOptions");
        final List<String> jobSkipList = new ArrayList<>();
        for (final String set : jobSkipFailedSettings.keySet()) {
            jobSkipList.add(jobSkipFailedSettings.get(set).trim());
        }

        otherOptions.put("jobSkipFailedOptions", jobSkipList);
        exflow.setOtherOption(otherOptions);

        //设置通用告警级别
        if (hasParam(req, "failureAlertLevel")) {
            otherOptions.put("failureAlertLevel", getParam(req, "failureAlertLevel"));
        }
        if (hasParam(req, "successAlertLevel")) {
            otherOptions.put("successAlertLevel", getParam(req, "successAlertLevel"));
        }
        try {
            //设置告警用户部门信息
            String userDep = transitionService.getUserDepartmentByUsername(user.getUserId());
            otherOptions.put("alertUserDeparment", userDep);
            HttpRequestUtils.filterAdminOnlyFlowParams(options, user);
            final String message = this.executorManagerAdapter.submitExecutableFlow(exflow, user.getUserId());
            ret.put("message", message);
        } catch (final Exception e) {
            logger.error(e.toString());
            respMessageBuild("100006", ret, dataMap.get("execFlow") + exflow.getFlowId() + dataMap.get("failed"), exflow.getFlowId(), e.getMessage());
            return;
        }
        respMessageBuild("200", ret, "success");
        ret.put("execid", exflow.getExecutionId());
    }

    /**
     * 对外开放的终止工作流接口
     * 根据项目名称，工作流名称，终止工作流
     *
     * @param req
     * @param resp
     * @param ret
     * @param user
     * @throws ServletException
     */
    private void extCancelFlow(final HttpServletRequest req, final HttpServletResponse resp,
                               final HashMap<String, Object> ret, final User user)
            throws ServletException {

        final String projectName = getParam(req, "projectName");
        final String flowName = getParam(req, "flowName");

        final Project project = getProjectAjaxByPermission(ret, projectName, user, Type.EXECUTE);
        if (project == null) {
            respMessageBuild("100001", ret, ret.get("error").toString(), projectName);
            return;
        }

        Map<String, String> dataMap = loadExecutorServletI18nData();

        final Flow flow = project.getFlow(flowName);
        if (flow == null) {
            respMessageBuild("100002", ret, dataMap.get("flow") + flowName + dataMap.get("notExist"), flowName);
            return;
        }

        resp.setCharacterEncoding("utf-8");
        int execId = 0;
        try {
            final ExecutableFlow exFlow = this.executorManagerAdapter.getRecentExecutableFlow(project.getId(), flow.getId());
            execId = exFlow.getExecutionId();
            this.executorManagerAdapter.cancelFlow(exFlow, user.getUserId());
        } catch (final ExecutorManagerException e) {
            respMessageBuild("100007", ret, dataMap.get("execId") + execId + dataMap.get("flow") + flowName + dataMap.get("stopFailed"), execId, flowName, e);
            if (e.getReason() != null && e.getReason() == ExecutorManagerException.Reason.API_INVOKE) {
                logger.info("flow {} cancel get api Exception", execId, e);
                ret.put("supportForceCancel", true);
            }
            return;
        }
        respMessageBuild("200", ret, "Execute Id: %s Flow %s Shutdown success. ", execId, flowName);
    }

    /**
     * 正在运行模块页面
     */
    private void ajaxGetExecutingFlowData(final HttpServletRequest req, final HttpServletResponse resp,
                                          final HashMap<String, Object> ret, final User user) throws ServletException, IOException {

        Set<String> userRoleSet = new HashSet<>();
        userRoleSet.addAll(user.getRoles());

        int page = getIntParam(req, "page", 1);
        if (page < 1) {
            page = 1;
        }

        int size = getIntParam(req, "size", 20);
        if (size < 0) {
            size = 20;
        }

        ExecutingQueryParam executingQueryParam = new ExecutingQueryParam();
        executingQueryParam.setSearch(getParam(req, "search", ""));
        executingQueryParam.setPreciseSearch(Boolean.valueOf(getParam(req, "preciseSearch", "false")));
        executingQueryParam.setFuzzySearch(Boolean.valueOf(getParam(req, "fuzzySearch", "false")));
        executingQueryParam.setProjcontain(getParam(req, "projcontain", ""));
        executingQueryParam.setFlowcontain(getParam(req, "flowcontain", ""));
        executingQueryParam.setUsercontain(getParam(req, "usercontain", ""));
        executingQueryParam.setFlowType(getParam(req, "flowType", ""));
        String startBeginTime = getParam(req, "startBeginTime", "");
        String startEndTime = getParam(req, "startEndTime", "");
        DateTimeFormatter dateTimeFormatter = DateTimeFormat.forPattern(DATE_PATTERN);
        if (!"".equals(startBeginTime)) {
            executingQueryParam.setStartBeginTime(dateTimeFormatter.parseDateTime(startBeginTime).getMillis());
        }
        if (!"".equals(startEndTime)) {
            executingQueryParam.setStartEndTime(dateTimeFormatter.parseDateTime(startEndTime).getMillis());
        }

        executingQueryParam.setSize(size);
        executingQueryParam.setPage(page);

        List<Map<String, String>> runningFlowsList = null;
        long total = 0;

        //添加权限判断 admin 用户能查看所有flow历史 user用户只能查看自己的flow历史
        try {
            if (userRoleSet.contains("admin")) {
                runningFlowsList = this.executorManagerAdapter.getExectingFlowsData(executingQueryParam);
                total = this.executorManagerAdapter.getExectingFlowsTotal(executingQueryParam);
                //运维管理员可以查看自己运维部门下所有人提交且正在运行的工作流
            } else if (systemManager.isDepartmentMaintainer(user)) {
                List<Integer> maintainedProjectIds = systemManager.getMaintainedProjects(user, 1);
                // #164643 查询该用户能查看的项目id
                List<Integer> projectIds = this.executorManagerAdapter.fetchPermissionsProjectId(user.getUserId());
                CollectionUtils.addAll(projectIds, maintainedProjectIds.iterator());
                executingQueryParam.setProjectIds(projectIds);
                runningFlowsList = this.executorManagerAdapter.getExectingFlowsData(executingQueryParam);
                total = this.executorManagerAdapter.getExectingFlowsTotal(executingQueryParam);
            } else {
                // 查询该用户能查看的项目id
                List<Integer> projectIds = this.executorManagerAdapter.fetchPermissionsProjectId(user.getUserId());
                executingQueryParam.setProjectIds(projectIds);
                runningFlowsList = this.executorManagerAdapter.getExectingFlowsData(executingQueryParam);
                total = this.executorManagerAdapter.getExectingFlowsTotal(executingQueryParam);
            }
        } catch (ExecutorManagerException e) {
            logger.error("fetch executing flows failed.", e);
            ret.put("error", "fetch executing flows failed");
            return;
        }
        ret.put("executingFlowData", runningFlowsList);
        ret.put("total", total);
        ret.put("page", page);
        ret.put("size", size);

    }

    private boolean hasPermission(int projectId, List<Integer> projectList) {
        if (projectList.contains(projectId)) {
            return true;
        }
        return false;
    }

    private boolean isOwner(Map<String, String> map, User user) {
        try {
            int flowType = Integer.parseInt(map.get("flowType"));
            if (flowType == 4) {
                String projectId = map.get("projectId");
                String flowName = map.get("flowName");
                ExecutionCycle cycleFlow = executorManagerAdapter.getExecutionCycleFlow(projectId, flowName);
                return user.getUserId().equals(cycleFlow.getSubmitUser());
            } else {
                return user.getUserId().equals(map.get("submitUser"));
            }
        } catch (Exception e) {
            return user.getUserId().equals(map.get("submitUser"));
        }
    }

    private boolean isMaintainedProject(Map<String, String> map, List<Integer> projectIds) {
        String projectId = map.get("projectId");
        return projectIds.stream()
                .anyMatch(id -> id.toString().equals(projectId));
    }

    /**
     * reload data for ha
     *
     * @param req
     * @throws ServletException
     */
    private void reloadWebData(final HttpServletRequest req)
            throws ServletException, TriggerManagerException, ScheduleManagerException {

        final String type = getParam(req, "reloadType");
        final int triggerId = getIntParam(req, "triggerId", -1);
        final String projectName = getParam(req, "projectName", "");
        final int scheduleId = getIntParam(req, "scheduleId", -1);
        switch (type) {
            case "runningExecutions":
                this.executorManagerAdapter.reloadWebData();
                break;
            case "deleteTrigger":
                this.scheduleManager.removeSchedule(triggerId);
                this.triggerManager.removeTriggerByWeb(triggerId);
                break;
            case "insertTrigger":
                this.triggerManager.insertTriggerByWeb(triggerId);
                break;
            case "updateTrigger":
                this.triggerManager.updateTriggerByWeb(triggerId);
                break;
            case "refreshProjectPermission":
                this.projectManager.refreshProjectPermission(projectName);
                break;
            case "reloadProject":
                this.projectManager.reloadProject(projectName);
                break;
            case "addEventSchedule":
                this.eventScheduleService.addEventScheduleByWeb(scheduleId);
                break;
            case "updateEventSchedule":
                this.eventScheduleService.updateEventScheduleByWeb(scheduleId);
                break;
            case "removeEventSchedule":
                this.eventScheduleService.removeEventScheduleByWeb(scheduleId);
                break;
            case "deleteProject":
                this.projectManager.deleteProjectByWeb(getIntParam(req, "projectId", -1));
                break;
            default:
        }

    }

    /**
     * 查询出运行时间大于一天的工作流，然后kill
     */
    private void ajaxOvertimeFlowKill(final HttpServletRequest req, final HttpServletResponse resp,
                                      final HashMap<String, Object> ret, final User user) throws IOException, ServletException {
        final List<Map<String, String>> runningFlowsList = this.executorManagerAdapter.getExectingFlowsData(null);
        List<Integer> execId = new ArrayList<>();
        for (Map<String, String> stringStringMap : runningFlowsList) {
            String duration = stringStringMap.get("duration");
            if (duration.contains("d")) {
                execId.add(Integer.parseInt(stringStringMap.get("execId")));
            }
        }
        final List<ExecutableFlow> flowList = this.executorManagerAdapter.getAllFlows();
        for (ExecutableFlow executableFlow : flowList) {
            if (execId.contains(executableFlow.getExecutionId())) {
                this.ajaxCancelFlow(req, resp, ret, user, executableFlow);
            }
        }

    }

}
