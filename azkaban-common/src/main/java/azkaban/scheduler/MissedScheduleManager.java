package azkaban.scheduler;

import azkaban.Constants.ConfigurationKeys;
import azkaban.ServiceProvider;
import azkaban.alert.Alerter;
import azkaban.executor.AlerterHolder;
import azkaban.flow.Flow;
import azkaban.flow.FlowUtils;
import azkaban.flow.NoSuchResourceException;
import azkaban.metrics.MetricsManager;
import azkaban.project.Project;
import azkaban.project.ProjectManager;
import azkaban.spi.AzkabanException;
import azkaban.trigger.builtin.ExecuteFlowAction;
import azkaban.utils.DaemonThreadFactory;
import azkaban.utils.Props;
import com.codahale.metrics.Counter;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import org.apache.commons.collections.CollectionUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.text.MessageFormat;
import java.util.List;
import java.util.concurrent.*;
import java.util.stream.Collectors;

import static org.apache.commons.lang.time.DateFormatUtils.format;

/**
 * Miss Schedule Manager is a failure recover manager for schedules. Schedule might be missed to
 * execute due to Trigger Scanner thread busy or WebServer down. This manager maintains a
 * configurable fixed sized thread pool to execute tasks including sending emails(alarm) or back
 * executing flow.
 *
 * @author lebronwang
 * @date 2023/12/05
 **/
@Singleton
public class MissedScheduleManager {

  private static final Logger LOG = LoggerFactory.getLogger(MissedScheduleManager.class);

  private ExecutorService missedScheduleExecutor;
  private final boolean missedScheduleManagerSwitch;
  private final ProjectManager projectManager;
  private final Alerter mailAlerter;

  private final int threadPoolSize;
  private final static int DEFAULT_THREAD_POOL_SIZE = 5;

  /**
   * the number of altered missed schedules
   */
  private final Counter missedScheduleAlertCounter;
  /**
   * total number of missed schedules
   */
  private final Counter missedScheduleCounter;
  /**
   * the number of missed schedules with no back execution enabled
   */
  private final Counter missedScheduleWithNonBackExecuteEnabledCounter;
  /**
   * the number of missed schedules with back execution enabled
   */
  private final Counter missedScheduleWithBackExecuteEnabledCounter;

  /**
   * the number of back execution when missed schedule is detected
   */
  private final Counter missedScheduleBackExecutionCounter;

  @Inject
  public MissedScheduleManager(final Props azkProps, final ProjectManager projectManager,
      final MetricsManager metricsManager) {
    this.projectManager = projectManager;
    this.mailAlerter = ServiceProvider.SERVICE_PROVIDER.getInstance(AlerterHolder.class)
        .get("email");
    if (mailAlerter == null) {
      String errorMsg = "Alerting missed schedules does not succeed, because of not finding alerter. ";
      LOG.error(errorMsg);
      throw new AzkabanException(errorMsg);
    }
    this.threadPoolSize = azkProps.getInt(ConfigurationKeys.MISSED_SCHEDULE_HANDLE_THREAD_POOL_SIZE,
        DEFAULT_THREAD_POOL_SIZE);
    this.missedScheduleManagerSwitch = azkProps.getBoolean(
        ConfigurationKeys.MISSED_SCHEDULE_MANAGER_SWITCH,
        true);
    this.missedScheduleAlertCounter = metricsManager.addCounter("missed-schedule-alter-count");
    this.missedScheduleCounter = metricsManager.addCounter("missed-schedule-count");
    this.missedScheduleWithNonBackExecuteEnabledCounter = metricsManager.addCounter(
        "missed-schedule-non-back-exec-count");
    this.missedScheduleWithBackExecuteEnabledCounter = metricsManager.addCounter(
        "missed-schedule-back-exec-count");
    this.missedScheduleBackExecutionCounter = metricsManager.addCounter(
        "missed-schedule-back-execution-count");

    if (this.missedScheduleManagerSwitch && this.threadPoolSize <= 0) {
      String errorMsg =
          "MissedScheduleManager is enabled but thread pool size is <= 0: " + this.threadPoolSize;
      LOG.error(errorMsg);
      throw new AzkabanException(errorMsg);
    }
  }

  /**
   * Put timestamps for Missed schedules
   *
   * @param missedScheduleTimeInMs timestamps of missed schedule
   * @param action                 execute flow action
   * @param backExecutionEnable    a schedule config from user, default true
   */
  public boolean addMissedSchedule(final List<Long> missedScheduleTimeInMs,
      final ExecuteFlowAction action,
      final boolean backExecutionEnable) throws NoSuchResourceException {

    if (!this.missedScheduleManagerSwitch) {
      LOG.warn("Missed Schedule manager is not enabled, can not add tasks.");
      return false;
    }

    int projectId = action.getProjectId();
    String flowName = action.getFlowName();
    Project project = FlowUtils.getProject(projectManager, projectId);
    Flow flow = FlowUtils.getFlow(project, flowName);
    LOG.info("received a missed schedule on times {} by action {} ", missedScheduleTimeInMs,
        action.toJson());

    List<String> failureRecipients = flow.getFailureEmails();
    if (action.getExecutionOptions().isFailureEmailsOverridden()) {
      failureRecipients = action.getExecutionOptions().getFailureEmails();
    }

    try {
      Future futureTask = this.missedScheduleExecutor.submit(
          new MissedScheduleOperationTask(missedScheduleTimeInMs, mailAlerter, failureRecipients,
              backExecutionEnable, action));
      this.missedScheduleCounter.inc(missedScheduleTimeInMs.size());
      if (!failureRecipients.isEmpty()) {
        this.missedScheduleAlertCounter.inc();
      }
      if (backExecutionEnable) {
        this.missedScheduleBackExecutionCounter.inc();
        this.missedScheduleWithBackExecuteEnabledCounter.inc(missedScheduleTimeInMs.size());
        LOG.info("Missed schedule task submitted with alerter {} and action {}", failureRecipients,
            action);
      } else {
        this.missedScheduleWithNonBackExecuteEnabledCounter.inc(missedScheduleTimeInMs.size());
      }
      return true;
    } catch (RejectedExecutionException e) {
      LOG.error("Failed to add more missed schedules tasks to the thread pool", e);
      return false;
    }
  }

  public void start() {
    if (this.missedScheduleManagerSwitch) {
      LOG.info("Missed Schedule Manager is ready to take tasks. ");
      this.missedScheduleExecutor = Executors.newFixedThreadPool(this.threadPoolSize,
          new DaemonThreadFactory("missed-schedule-task-pool"));
    } else {
      LOG.info("Missed Schedule Manager is disabled.");
      this.missedScheduleExecutor = null;
    }
  }

  public boolean stop() throws InterruptedException {
    if (this.missedScheduleExecutor != null) {
      this.missedScheduleExecutor.shutdown();
      if (!this.missedScheduleExecutor.awaitTermination(30, TimeUnit.SECONDS)) {
        this.missedScheduleExecutor.shutdownNow();
      }
    }
    return true;
  }

  /**
   * A MissedSchedule Task is a unit that store the critical information to execute a missedSchedule
   * operation. A Task is capable to 1. notice user 2. execute back execution if enabled feature
   * flag when a missed schedule happens.
   */
  public static class MissedScheduleOperationTask implements Runnable {

    private static final Logger LOG = LoggerFactory.getLogger(MissedScheduleOperationTask.class);
    private final Alerter alerter;
    /**
     * alertRecipients to notify users the schedules missed
     */
    private final List<String> alertRecipients;

    /**
     * alert title
     */
    private final String alertTitle;
    /**
     * alert body
     */
    private final String alertMessage;
    /**
     * back execute action if user disabled the config, the action could be null
     */
    private final ExecuteFlowAction executeFlowAction;

    public MissedScheduleOperationTask(final List<Long> missedScheduleTimesInMs,
        final Alerter alerter,
        final List<String> alertRecipients, final boolean backExecutionEnable,
        final ExecuteFlowAction executeFlowAction) {
      this.alerter = alerter;
      this.alertRecipients = alertRecipients;
      this.executeFlowAction = backExecutionEnable ? executeFlowAction : null;
      String missedScheduleTimestampToDate = formatTimestamps(missedScheduleTimesInMs);
      String flowName = executeFlowAction.getFlowName();
      String projectName = executeFlowAction.getProjectName();
      String submitUser = executeFlowAction.getSubmitUser();
      this.alertTitle = String.format("[%s:%s] %s %s", projectName, flowName, "WTSS",
          "未正常调度告警");
      final String userDep = executeFlowAction.getOtherOption().get("alertUserDeparment") == null ?
          "" : executeFlowAction.getOtherOption().get("alertUserDeparment") + "";
      // 按照部门通知的提示信息
      String informInfo;
      if (CollectionUtils.isEmpty(alertRecipients)) {
        informInfo = "请联系[" + userDep + "]部门大数据运维组, 或者";
      } else {
        informInfo = "请立即联系: ";
      }
      // 优化告警内容
      MessageFormat messageFormat = new MessageFormat(informInfo +
          " 工作流提交人：{0}， 你在项目 {1} 中的定时调度工作流 {2} 本计划在 {3} 执行"
          + "但是未按时调起！" + (backExecutionEnable ?
          "自动拉起将会执行，请核实调度状态。"
              : ""));

      this.alertMessage = messageFormat.format(new Object[]{submitUser, projectName, flowName,
          missedScheduleTimestampToDate});
    }

    /**
     * Chain timestamps using "," into human-readable string.
     *
     * @param timestamps timestamps in milliseconds
     * @return chained String
     */
    private String formatTimestamps(final List<Long> timestamps) {
      List<String> datesFromTimestamps = timestamps.stream()
          .map(timestamp -> format(timestamp, "yyyy-MM-dd HH:mm:ss")).collect(
              Collectors.toList());
      return String.join(",", datesFromTimestamps);
    }

    @Override
    public void run() {

      try {
        this.alerter.sendAlert(alertRecipients, this.alertTitle, this.alertMessage);

        if (this.executeFlowAction != null) {
          this.executeFlowAction.doAction();
        }
      } catch (InterruptedException e) {
        String warningMsg = "MissedScheduleTask thread is being interrupted, throwing out the exception";
        LOG.warn(warningMsg, e);
      } catch (Exception e) {
        LOG.warn("Error in executing task, it might due to fail to execute back execution flow", e);
      }
    }
  }
}
