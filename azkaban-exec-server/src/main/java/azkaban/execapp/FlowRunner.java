/*
 * Copyright 2013 LinkedIn Corp
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

package azkaban.execapp;

import static azkaban.Constants.ConfigurationKeys.AZKABAN_SERVER_HOST_NAME;
import static azkaban.Constants.USER_DEFINED_PARAM;
import static azkaban.execapp.ConditionalWorkflowUtils.FAILED;
import static azkaban.execapp.ConditionalWorkflowUtils.PENDING;
import static azkaban.execapp.ConditionalWorkflowUtils.checkConditionOnJobStatus;
import static azkaban.project.DirectoryYamlFlowLoader.CONDITION_ON_JOB_STATUS_PATTERN;
import static azkaban.project.DirectoryYamlFlowLoader.CONDITION_VARIABLE_REPLACEMENT_PATTERN;

import azkaban.Constants;
import azkaban.Constants.ConfigurationKeys;
import azkaban.Constants.JobProperties;
import azkaban.ServiceProvider;
import azkaban.alert.Alerter;
import azkaban.event.Event;
import azkaban.event.EventData;
import azkaban.event.EventHandler;
import azkaban.event.EventListener;
import azkaban.execapp.event.FlowWatcher;
import azkaban.execapp.event.JobCallbackManager;
import azkaban.execapp.jmx.JmxJobMBeanManager;
import azkaban.execapp.metric.NumFailedJobMetric;
import azkaban.execapp.metric.NumRunningJobMetric;
import azkaban.executor.AlerterHolder;
import azkaban.executor.ConnectorParams;
import azkaban.executor.ExecutableFlow;
import azkaban.executor.ExecutableFlowBase;
import azkaban.executor.ExecutableNode;
import azkaban.executor.ExecutionControllerUtils;
import azkaban.executor.ExecutionOptions;
import azkaban.executor.ExecutionOptions.FailureAction;
import azkaban.executor.ExecutorLoader;
import azkaban.executor.ExecutorManagerException;
import azkaban.executor.Status;
import azkaban.flow.ConditionOnJobStatus;
import azkaban.flow.FlowProps;
import azkaban.flow.FlowUtils;
import azkaban.jobExecutor.ProcessJob;
import azkaban.jobtype.JobTypeManager;
import azkaban.metric.MetricReportManager;
import azkaban.project.FlowLoaderUtils;
import azkaban.project.ProjectLoader;
import azkaban.project.ProjectManagerException;
import azkaban.sla.SlaOption;
import azkaban.spi.AzkabanEventReporter;
import azkaban.spi.EventType;
import azkaban.utils.*;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.io.Files;
import com.google.common.util.concurrent.ThreadFactoryBuilder;
import com.webank.wedatasphere.schedulis.common.executor.ExecutionCycle;
import com.webank.wedatasphere.schedulis.common.jobExecutor.utils.SystemBuiltInParamJodeTimeUtils;
import com.webank.wedatasphere.schedulis.common.utils.LogUtils;
import com.webank.wedatasphere.schedulis.exec.execapp.KillFlowTrigger;
import java.io.File;
import java.io.IOException;
import java.security.AccessControlContext;
import java.security.AccessController;
import java.security.PrivilegedExceptionAction;
import java.security.ProtectionDomain;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import javax.script.ScriptEngine;
import javax.script.ScriptEngineManager;
import javax.script.ScriptException;
import com.google.gson.JsonObject;
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang.StringUtils;
import org.slf4j.LoggerFactory;


/**
 * Class that handles the running of a ExecutableFlow DAG
 */
public class FlowRunner extends EventHandler implements Runnable {

  // We check update every 5 minutes, just in case things get stuck. But for the
  // most part, we'll be idling.
  private static final long CHECK_WAIT_MS = 5 * 60 * 1000;
  private final ExecutableFlow flow;
  // Sync object for queuing
  private final Object mainSyncObj = new Object();
  private final JobTypeManager jobtypeManager;
  private final ExecutorLoader executorLoader;
  private final ProjectLoader projectLoader;
  private final int execId;
  private final File execDir;
  private final FailureAction failureAction;
  // Properties map
  private final Props azkabanProps;
  private final Map<String, Props> sharedProps = new HashMap<>();
  private final JobRunnerEventListener listener = new JobRunnerEventListener();
  private final FlowRunnerEventListener flowListener = new FlowRunnerEventListener();
  private final Set<JobRunner> activeJobRunners = Collections
          .newSetFromMap(new ConcurrentHashMap<JobRunner, Boolean>());
  // Thread safe swap queue for finishedExecutions.
  private final SwapQueue<ExecutableNode> finishedNodes;
  private final AzkabanEventReporter azkabanEventReporter;
  private final AlerterHolder alerterHolder;
  private org.slf4j.Logger logger;
  private File logFile;
  private ExecutorService executorService;
  private Thread flowRunnerThread;
  private int numJobThreads = 10;
  // Used for pipelining
  private Integer pipelineLevel = null;
  private Integer pipelineExecId = null;
  // Watches external flows for execution.
  private FlowWatcher watcher = null;
  private Set<String> proxyUsers = null;
  private boolean validateUserProxy;
  private String jobLogFileSize = "5MB";
  private int jobLogNumFiles = 8;
  private volatile boolean flowPaused = false;
  private volatile boolean flowFailed = false;
  private volatile boolean flowFinished = false;
  private volatile boolean flowKilled = false;

  private final ConcurrentHashMap<String, ExecutableNode> failedNodes;
  private ThreadPoolExecutor executorServiceForCheckers;
  // Used to control task timeout termination.
  private volatile boolean isTriggerStarted = false;

  private KillFlowTrigger killFlowTrigger;

  private volatile ConcurrentHashMap embeddedFlowSlaFlag = new ConcurrentHashMap<String, Boolean>();

  private volatile ConcurrentHashMap embeddedFlowTimeOutSlaFlag = new ConcurrentHashMap<String, Boolean>();

  // The following is state that will trigger a retry of all failed jobs
  private volatile boolean retryFailedJobs = false;

  private ExecutorService executorPriorityService;

  private EventListener cycleFlowRunnerEventListener;

  private long pausedStartTime;

  private long maxPausedTime;

  private volatile boolean isFailedPaused = false;

  private String loggerName;
  private String logFileName;
  /**
   * Constructor. This will create its own ExecutorService for thread pools
   */
  public FlowRunner(final ExecutableFlow flow, final ExecutorLoader executorLoader,
                    final ProjectLoader projectLoader, final JobTypeManager jobtypeManager,
                    final Props azkabanProps, final AzkabanEventReporter azkabanEventReporter, final
                    AlerterHolder alerterHolder)
          throws ExecutorManagerException {
    this(flow, executorLoader, projectLoader, jobtypeManager, null, azkabanProps,
            azkabanEventReporter, alerterHolder);
  }

  /**
   * Constructor. If executorService is null, then it will create it's own for thread pools.
   */
  public FlowRunner(final ExecutableFlow flow, final ExecutorLoader executorLoader,
                    final ProjectLoader projectLoader, final JobTypeManager jobtypeManager,
                    final ExecutorService executorService, final Props azkabanProps,
                    final AzkabanEventReporter azkabanEventReporter, final AlerterHolder alerterHolder)
          throws ExecutorManagerException {
    this.execId = flow.getExecutionId();
    this.flow = flow;
    this.executorLoader = executorLoader;
    this.projectLoader = projectLoader;
    this.execDir = new File(flow.getExecutionPath());
    this.jobtypeManager = jobtypeManager;

    final ExecutionOptions options = flow.getExecutionOptions();
    this.pipelineLevel = options.getPipelineLevel();
    this.pipelineExecId = options.getPipelineExecutionId();
    this.failureAction = options.getFailureAction();
    this.proxyUsers = flow.getProxyUsers();
    this.executorService = executorService;
    this.finishedNodes = new SwapQueue<>();
    this.failedNodes = new ConcurrentHashMap<>();
    this.azkabanProps = azkabanProps;
    this.alerterHolder = alerterHolder;

    // Add the flow listener only if a non-null eventReporter is available.
    if (azkabanEventReporter != null) {
      this.addListener(this.flowListener);
    }

    // Create logger and execution dir in flowRunner initialization instead of flow runtime to avoid NPE
    // where the uninitialized logger is used in flow preparing state
    createLogger(this.flow.getFlowId());
    this.azkabanEventReporter = azkabanEventReporter;
  }

  public EventListener getCycleFlowRunnerEventListener() {
    return this.cycleFlowRunnerEventListener;
  }

  public void setCycleFlowRunnerEventListener(EventListener cycleFlowRunnerEventListener) {
    this.cycleFlowRunnerEventListener = cycleFlowRunnerEventListener;
  }

  public FlowRunner setFlowWatcher(final FlowWatcher watcher) {
    this.watcher = watcher;
    return this;
  }

  public FlowRunner setNumJobThreads(final int jobs) {
    this.numJobThreads = jobs;
    return this;
  }

  public FlowRunner setJobLogSettings(final String jobLogFileSize, final int jobLogNumFiles) {
    this.jobLogFileSize = jobLogFileSize;
    this.jobLogNumFiles = jobLogNumFiles;

    return this;
  }

  public FlowRunner setValidateProxyUser(final boolean validateUserProxy) {
    this.validateUserProxy = validateUserProxy;
    return this;
  }

  public File getExecutionDir() {
    return this.execDir;
  }

  @VisibleForTesting
  AlerterHolder getAlerterHolder() {
    return this.alerterHolder;
  }

  public void shutdownNow(){
    kill();
    logger.info("killed all job process.");
    if (this.executorService != null) {
      logger.info("shutdown executorService.");
      this.executorService.shutdown();
    }
    if (this.executorServiceForCheckers != null) {
      logger.info("shutdown executorServiceForCheckers.");
      this.executorService.shutdown();
    }
    if (this.executorPriorityService != null){
      logger.info("shutdown executorPriorityService.");
      this.executorPriorityService.shutdown();
    }
  }

  private void createThreadPool(){
    if (this.executorService == null) {
      logger.info("create executorService. execId:" + execId);
      ThreadFactory threadFactory1 = new ThreadFactoryBuilder().setDaemon(true).build();
      this.executorService = new ThreadPoolExecutor(this.numJobThreads, this.numJobThreads,
          0L, TimeUnit.MILLISECONDS,
          new LinkedBlockingQueue<Runnable>(1024 * 100), threadFactory1);
    }
    if (this.executorServiceForCheckers == null) {
      logger.info("create executorServiceForCheckers. execId:" + execId);
      ThreadFactory threadFactory2 = new ThreadFactoryBuilder().setDaemon(true).build();
      int size = azkabanProps.getInt("checkers.num.threads", 10);
      this.executorServiceForCheckers = new ThreadPoolExecutor(size, size,
          0L, TimeUnit.MILLISECONDS,
          new LinkedBlockingQueue<Runnable>(1024 * 100), threadFactory2);
    }
    if (this.executorPriorityService == null){
      logger.info("create executorPriorityService. execId:" + execId);
      ThreadFactory threadFactory3 = new ThreadFactoryBuilder().setDaemon(true).build();
      this.executorPriorityService = Executors.newSingleThreadExecutor(threadFactory3);
    }
  }

  private void initNSWTSSValue(){
    Props flowProp = this.sharedProps.get(this.flow.getExecutableNode(((ExecutableFlowBase)flow).getStartNodes().get(0)).getPropsSource());
    String flowParamNsWtss = this.flow.getExecutionOptions().getFlowParameters().getOrDefault("ns_wtss", null);
    String flowPropNswtss = flowProp == null ? null: flowProp.getString("ns_wtss", null);
    if(flowParamNsWtss != null && flowParamNsWtss.equals("false")){
      this.flow.setNsWtss(false);
    } else if(flowPropNswtss != null && flowPropNswtss.equals("false")){
      this.flow.setNsWtss(false);
    }
    logger.info("nsWtss: " + this.flow.getNsWtss() + ", flowParamNsWtss: " + flowParamNsWtss + ", flowPropNswtss:" + flowPropNswtss);
  }

/**
   * rundate替换
   */
  private void runDateReplace(){
    //获取执行Flow节点
    ExecutableFlow ef = this.flow;
    // FIXME New feature, replace the run_date variable in the file before the job stream starts running.
    SystemBuiltInParamJodeTimeUtils sbipu = new SystemBuiltInParamJodeTimeUtils();
    if(null == ef.getParentFlow()){
      sbipu.run(this.execDir.getPath(), ef);
    }
  }
  
  private void alertOnIMSRegistStart(){
    try {
      // 注册并上报作业流开始
      Alerter mailAlerter = ServiceProvider.SERVICE_PROVIDER.getInstance(AlerterHolder.class).get("email");
      if(mailAlerter == null){

        mailAlerter = ServiceProvider.SERVICE_PROVIDER.getInstance(AlerterHolder.class).get("default");
      }
      mailAlerter.alertOnIMSRegistStart(this.flow, this.sharedProps, logger);
    } catch (Exception e) {
      logger.error("The flow report IMS faild in the end {} ", e);
    }
  }

  private void alertOnIMSRegistFinish(){
    try {
      // 上报作业流开始
      Alerter mailAlerter = ServiceProvider.SERVICE_PROVIDER.getInstance(AlerterHolder.class).get("email");
      if(mailAlerter == null){
        mailAlerter = ServiceProvider.SERVICE_PROVIDER.getInstance(AlerterHolder.class).get("default");
      }
      mailAlerter.alertOnIMSRegistFinish(this.flow, this.sharedProps, this.logger);
    }catch (Exception e) {
      logger.error("The flow report IMS faild in the end {} "+e);
    }
  }

  @Override
  public void run() {
    try {
	  // FIXME Create a thread pool and add a thread pool (executorServiceForCheckers) for running checker tasks.
      createThreadPool();
	    runDateReplace();
      this.logger.info("Fetching job and shared properties.");
      if (!FlowLoaderUtils.isAzkabanFlowVersion20(this.flow.getAzkabanFlowVersion())) {
        loadAllProperties();
        initNSWTSSValue();
      }
      // FIXME New function, reporting job stream status to IMS, relying on third-party services.
      alertOnIMSRegistStart();
      // FIXME Global variable settings for job runs.
      setSubmitUserProps(this.flow.getSubmitUser());
      // 设置系统内置变量
      setupFlowExecution();
      this.flow.setStartTime(System.currentTimeMillis());

      this.logger.info("Updating initial flow directory.");
      updateFlow();

      this.fireEventListeners(
              Event.create(this, EventType.FLOW_STARTED, new EventData(this.getExecutableFlow())));

      // FIXME When the task is submitted to the executor queue and the task status is preparing, the execution is terminated by the user, which causes the job stream to be in the killing state and cannot be turned to the killed state.
      if(this.flowKilled){
        logger.info(this.flow.getExecutionId() + " was killed. ");
        this.flow.setStatus(Status.KILLED);
      } else {
        runFlow();
      }
    } catch (final Throwable t) {
      if (this.logger != null) {
        this.logger
                .error(
                        "An error has occurred during the running of the flow. Quiting.",
                        t);
      }
      this.flow.setStatus(Status.FAILED);
    } finally {
      try {
        // FIXME New function, reporting job stream status to IMS, relying on third-party services.
        alertOnIMSRegistFinish();
        if (this.watcher != null) {
          this.logger.info("Watcher is attached. Stopping watcher.");
          this.watcher.stopWatcher();
          this.logger
                  .info("Watcher cancelled status is " + this.watcher.isWatchCancelled());
        }

        this.flow.setEndTime(System.currentTimeMillis());
        this.logger.info("Setting end time for flow " + this.execId + " to "
                + System.currentTimeMillis());
        if (this.executorServiceForCheckers != null) {
          logger.info("shutdown the thread pool, current active threads : " + this.executorServiceForCheckers.getActiveCount());
          this.executorServiceForCheckers.shutdown();
        }
        closeLogger();
        updateFlow();
      } finally {
        this.fireEventListeners(
                Event.create(this, EventType.FLOW_FINISHED, new EventData(this.flow)));
        // In polling model, executor will be responsible for sending alerting emails when a flow
        // finishes.
        // Todo jamiesjc: switch to event driven model and alert on FLOW_FINISHED event.
        if (this.azkabanProps.getBoolean(ConfigurationKeys.AZKABAN_POLL_MODEL, false)) {
          // 通用告警和sla告警
          ExecutionControllerUtils.alertUserOnFlowFinished(this.flow, this.alerterHolder,
                  ExecutionControllerUtils.getFinalizeFlowReasons("Flow finished", null));
        }
      }
    }
  }

  private void setSubmitUserProps(String userName){
    try {
      //对于循环执行特殊处理
      if (this.flow.getFlowType() == 4) {
        ExecutionCycle cycleFlow = this.executorLoader.getExecutionCycleFlow(String.valueOf(flow.getProjectId()), flow.getFlowId());
        String submitUser = cycleFlow.getSubmitUser();
        this.flow.setUserProps(this.executorLoader.getUserVariableByName(submitUser));
      } else {
        this.flow.setUserProps(this.executorLoader.getUserVariableByName(userName));
      }
    } catch (ExecutorManagerException em){
      logger.error("获取用户变量失败" + em);
    }
  }

  public Props getAzkabanProps() {
    return azkabanProps;
  }

  private void setupFlowExecution() {
    final int projectId = this.flow.getProjectId();
    final int version = this.flow.getVersion();
    final String flowId = this.flow.getFlowId();
    final Map<String, String> repeatMap = this.flow.getRepeatOption();

    Props commonFlowProps;

    // FIXME New feature: if it is a historical rerun task, other built-in date variables are calculated based on the historical rerun date.
    if(!repeatMap.isEmpty() && "RepeatFlow".equals(repeatMap.get("RepeatType"))){
      long repeatTime = Long.valueOf(repeatMap.get("startTimeLong"));
      commonFlowProps = FlowUtils.addRepeatCommonFlowProperties(null, repeatTime, this.flow);
    }else{
      // Add a bunch of common azkaban properties 给prop文件添加参数
      commonFlowProps = FlowUtils.addCommonFlowProperties(null, this.flow);
    }

    if (FlowLoaderUtils.isAzkabanFlowVersion20(this.flow.getAzkabanFlowVersion())) {
      final Props flowProps = loadPropsFromYamlFile(this.flow.getId());
      if (flowProps != null) {
        flowProps.setParent(commonFlowProps);
        commonFlowProps = flowProps;
      }
    } else {
      if (this.flow.getJobSource() != null) {
        final String source = this.flow.getJobSource();
        final Props flowProps = this.sharedProps.get(source);
        flowProps.setParent(commonFlowProps);
        commonFlowProps = flowProps;
      }
    }

    // If there are flow overrides, we apply them now.
    final Map<String, String> flowParam =
            this.flow.getExecutionOptions().getFlowParameters();
    if (flowParam != null && !flowParam.isEmpty()) {
      commonFlowProps = new Props(commonFlowProps, flowParam);
    }
    this.flow.setInputProps(commonFlowProps);

    if (this.watcher != null) {
      this.watcher.setLogger(this.logger);
    }

    // Avoid NPE in unit tests when the static app instance is not set
    if (AzkabanExecutorServer.getApp() != null) {
      this.logger
              .info("Assigned executor : " + AzkabanExecutorServer.getApp().getExecutorHostPort());
    }
    this.logger.info("Running execid:" + this.execId + " flow:" + flowId + " project:"
            + projectId + " version:" + version);
    if (this.pipelineExecId != null) {
      this.logger.info("Running simulateously with " + this.pipelineExecId
              + ". Pipelining level " + this.pipelineLevel);
    }

    // The current thread is used for interrupting blocks
    this.flowRunnerThread = Thread.currentThread();
    this.flowRunnerThread.setName("FlowRunner-exec-" + this.flow.getExecutionId());
  }

  private void updateFlow() {
    updateFlow(System.currentTimeMillis());
  }

  private synchronized void updateFlow(final long time) {
    try {
      this.flow.setUpdateTime(time);
      this.executorLoader.updateExecutableFlow(this.flow);
    } catch (final Exception e) {
      this.logger.error("Error updating flow.", e);
    }
  }

  /**
   * setup logger and execution dir for the flowId
   */
  private void createLogger(final String flowId) {
    this.loggerName = UUID.randomUUID().toString() + "." + this.execId + "." + flowId;
    this.logFileName = "_flow." + loggerName + ".log";
    this.logFile = new File(this.execDir, logFileName);
    LogUtils.createFlowLog(this.execDir.getAbsolutePath(), logFileName, loggerName);
    this.logger = LoggerFactory.getLogger(loggerName);
  }

  public void closeLogger() {
    if (this.logger != null) {
      LogUtils.stopLog(loggerName);
      try {
        this.executorLoader.uploadLogFile(this.execId, "", 0, this.logFile);
      } catch (final ExecutorManagerException e) {
        e.printStackTrace();
      }
    }
  }

  private void loadAllProperties() throws IOException {
    // First load all the properties
    this.flow.getPropsSource();
    for (final FlowProps fprops : this.flow.getFlowProps()) {
      final String source = fprops.getSource();
      final File propsPath = new File(this.execDir, source);
      final Props props = new Props(null, propsPath);
      this.sharedProps.put(source, props);
      if(source.contains("priority.properties")){
        this.flow.setOutputProps(props);
      }
    }

    // Resolve parents
    for (final FlowProps fprops : this.flow.getFlowProps()) {
      if (fprops.getInheritedSource() != null) {
        final String source = fprops.getSource();
        final String inherit = fprops.getInheritedSource();

        final Props props = this.sharedProps.get(source);
        final Props inherits = this.sharedProps.get(inherit);

        props.setParent(inherits);
      }
    }
  }


  /**
   * Main method that executes the jobs.
   */
  private void runFlow() throws Exception {
    this.logger.info("Starting flows");
    runReadyJob(this.flow);
    updateFlow();

    while (!this.flowFinished) {
      synchronized (this.mainSyncObj) {
        Thread.interrupted();
        if (this.flowPaused) {
          try {
            this.mainSyncObj.wait(CHECK_WAIT_MS);
          } catch (final InterruptedException e) {
          }
          if((System.currentTimeMillis() - this.pausedStartTime) > maxPausedTime){
            this.logger.warn("The pause timed out and the job flow was re executed.");
            reStart();
            updateFlow();
          }
          continue;
        } else {
          if (this.retryFailedJobs) {
            retryAllFailures();
          } else if (!progressGraph()) {
            try {
              this.mainSyncObj.wait(CHECK_WAIT_MS);
            } catch (final InterruptedException e) {
            }
          }
        }
      }
    }

    this.logger.info("Finishing up flow. Awaiting Termination");
    this.executorService.shutdown();

    updateFlow();
    this.logger.info("Finished Flow");
  }

  public long getMaxPausedTime() {
    return maxPausedTime;
  }

  public FlowRunner setMaxPausedTime(long maxPausedTime) {
    this.maxPausedTime = maxPausedTime;
    return this;
  }

  private void retryAllFailures() throws IOException {
    this.logger.info("Restarting all failed jobs");

    this.retryFailedJobs = false;
    this.flowKilled = false;
    this.flowFailed = false;
    this.flow.setStatus(Status.RUNNING);
    final ArrayList<ExecutableNode> retryJobs = new ArrayList<>();
    resetFailedState(this.flow, retryJobs);
    for (final ExecutableNode node : retryJobs) {
      this.logger.info("retryJob: " + node.getId() + "," + node.getStatus() + ", baseflow: " + (node instanceof ExecutableFlowBase));
      if (node.getStatus() == Status.READY
              || node.getStatus() == Status.DISABLED) {
        runReadyJob(node);
      } else if (Status.isSucceeded(node.getStatus())) {
        for (final String outNodeId : node.getOutNodes()) {
          final ExecutableFlowBase base = node.getParentFlow();
          runReadyJob(base.getExecutableNode(outNodeId));
        }
      }

      runReadyJob(node);
    }

    updateFlow();
  }

  /**
   * skipped all Failed_waiting job.
   */
  public String skippedAllFailures(String user) {
    this.logger.info("skipped all failures jobs by " + user);
    if(this.flowKilled){
      logger.warn("flow has been killed, can not skipped the failed jobs");
      return "作业流已被killed，不能执行跳过任务。";
    }
    synchronized (this.mainSyncObj){
      if(this.flowKilled){
        logger.warn("flow has been killed, can not skipped the failed jobs");
        return "作业流已被killed，不能执行跳过任务。";
      }
      stopKillFlowTrigger();
      if(FlowRunner.this.failureAction == FailureAction.FAILED_PAUSE) {
        List<String> nodes = new ArrayList<>();
        for(ExecutableNode node: this.failedNodes.values()){
          if(node.getStatus().equals(Status.FAILED_WAITING)){
            //还原flow状态为Running
            resetFlowStatus(node.getParentFlow(), node);
            node.setStatus(Status.FAILED_SKIPPED);
            node.setUpdateTime(System.currentTimeMillis());
            FlowRunner.this.finishedNodes.add(node);
            nodes.add(node.getNestedId());
            logger.info(String.format("job: %s, old status: %s -> new status %s.", node.getNestedId(), "FAILED_WAITING", node.getStatus()));
          }
        }
        updateFlow();
        for (String nodeName : nodes) {
          this.failedNodes.remove(nodeName);
        }
      }
    }
    interrupt();
    return "已执行跳过过所有FAILED_WAITING状态job";
  }


  public String retryFailedJobs(List<String> retryFailedJobs) throws Exception {
    if(this.flowKilled){
      logger.warn("flow has been killed, can not skipped the failed jobs");
      return "作业流已被killed，不能重试任务。";
    }
    synchronized (this.mainSyncObj) {
      if(this.flowKilled){
        logger.warn("flow has been killed, can not skipped the failed jobs");
        return "作业流已被killed，不能重试任务。";
      }
      ExecutableNode targetNode = this.flow.getExecutableNodePath(retryFailedJobs.get(0));
      if(targetNode != null && !targetNode.getStatus().equals(Status.FAILED_WAITING)){
        logger.warn("job:" + targetNode.getNestedId() + "不是FAILED_WAITING，不能重试。");
        return "job:" + targetNode.getNestedId() + "不是FAILED_WAITING，不能重试。";
      }
      stopKillFlowTrigger();
      resetFlowStatus(targetNode.getParentFlow(), targetNode);

      for (String nodePath : retryFailedJobs) {
        final ExecutableNode node = this.flow.getExecutableNodePath(nodePath);
        if (!node.getStatus().equals(Status.FAILED_WAITING)) {
          logger.warn("job:" + node.getNestedId() + "不是FAILED_WAITING，不能重试。");
          return "job:" + node.getNestedId() + "不是FAILED_WAITING，不能重试。";
        }
        this.logger.info("Restarting failed job: " + nodePath);
        this.flowKilled = false;
        node.resetForRetry();
        if ((node.getStatus() == Status.READY
                || node.getStatus() == Status.DISABLED)) {
          runReadyJob(node);
        }
      }
      updateFlow();
      return null;
    }
  }



  public void setFlowFailed(final boolean flowFailed){
    logger.info("setting flow: " + this.flow.getExecutionId() + " " + flowFailed);
    this.flowFailed = flowFailed;
  }

  private void jobSkippedHandle(ExecutableNode node, String user){
    if(node instanceof ExecutableFlowBase){
      if(node.getStatus().equals(Status.READY)){
        setJobSkipped(node, user);
      } else {
        for(ExecutableNode chiledNode: ((ExecutableFlowBase) node).getExecutableNodes()){
          jobSkippedHandle(chiledNode, user);
        }
      }
    } else {
      setJobSkipped(node, user);
    }
  }

  private void setJobSkipped(ExecutableNode node, String user){
    if (node.getStatus().equals(Status.READY)) {
      logger.info("setting job:" + node.getNestedId() + " status " + node.getStatus().toString() + " to status disabled.");
      node.setStatus(Status.DISABLED);
      node.setUpdateTime(System.currentTimeMillis());
    } else if (Status.isStatusRunning(node.getStatus())) {
      for (JobRunner jobRunner : this.activeJobRunners) {
        if (jobRunner.getNode().getNestedId().equals(node.getNestedId())) {
          logger.info("setting job:" + node.getNestedId() + " status " + node.getStatus().toString() + " to skipped.");
          jobRunner.skippedJob(user);
        }
      }
    }
  }

  /**
   * 1.Change the job status of ready to disabled.
   * 2.Change the running (running, queue, failed_retrying) job to the failed_skipped state.
   * @param nodePath
   * @param respMap
   * @throws ExecutorManagerException
   */
  public void setJobDisabled(String nodePath, Map<String, Object> respMap, String user) throws ExecutorManagerException{
    logger.info("disable job: " + this.flow.getExecutionId() + ", " + nodePath);
    if(this.flowKilled || this.flowFinished){
      logger.warn("flow has been killed or finished, can not skipped the failed jobs");
      respMap.put(ConnectorParams.RESPONSE_ERROR, "设置失败，作业流已经killed或者finished。");
      return;
    }
    synchronized (this.mainSyncObj) {
      ExecutableNode node = this.flow.getExecutableNodePath(nodePath);
      if(this.flowKilled || this.flowFinished){
        logger.warn("flow has been killed or finished, can not skipped the failed jobs");
        respMap.put(ConnectorParams.RESPONSE_ERROR, "设置失败，作业流已经killed或者finished。");
      } else if(node == null){
        logger.warn("job: " + this.flow.getExecutionId() + ", " + nodePath + " is not exists.");
        respMap.put(ConnectorParams.RESPONSE_ERROR, "job: " + this.flow.getExecutionId() + ", " + nodePath + " is not exists.");
      } else if(Status.isStatusFinished(node.getStatus())) {
        logger.warn("job: " + nodePath + " is not running.");
        respMap.put(ConnectorParams.RESPONSE_ERROR, "设置失败, 任务可能已经执行完成。");
      } else {
        jobSkippedHandle(node, user);
        updateFlow();
      }
    }
  }

  public boolean setFlowFailed(final JsonObject json){
    boolean flowFailed = json.get("flowFailed").getAsBoolean();
    boolean ret = true;
    synchronized (this.mainSyncObj) {
      if (!this.flowFinished && this.flowPaused) {
        this.setFlowFailed(flowFailed);
        this.flowPaused = false;
      } else {
        this.logger.warn("this flow:" + this.flow.getExecutionId() + " is not paused or has finished.");
        ret = false;
      }
      interrupt();
    }
    return ret;
  }

  
  private boolean progressGraph() throws IOException {
    this.finishedNodes.swap();
    // The following nodes are finished, so we'll collect a list of outnodes
    // that are candidates for running next.
    final HashSet<ExecutableNode> nodesToCheck = new HashSet<>();
    for (final ExecutableNode node : this.finishedNodes) {
      Set<String> outNodeIds = new HashSet<>();
      // FIXME If the node status is not FAILED_WAITING, you need to get its external nodes.
      if(!node.getStatus().equals(Status.FAILED_WAITING)) {
        outNodeIds = node.getOutNodes();
      }
      ExecutableFlowBase parentFlow = node.getParentFlow();

      // If a job is seen as failed or killed due to failing SLA, then we set the parent flow to
      // FAILED_FINISHING
      // FIXME Added judgment conditions. When the task status is FAILED_WAITING, the job flow status must also be changed to the FAILED_FINISHING status.
      if ((node.getStatus() == Status.FAILED_WAITING || node.getStatus() == Status.FAILED || (node.getStatus() == Status.KILLED && node
              .isKilledBySLA())) && nodeSkipFailedCheck(node)) {
        // The job cannot be retried or has run out of retry attempts. We will
        // fail the job and its flow now.
        if (!retryJobIfPossible(node)) {
          setFlowFailed(node);
        } else {
          nodesToCheck.add(node);
          continue;
        }
      }

      if (outNodeIds.isEmpty() && isFlowReadytoFinalize(parentFlow)) {
        // Todo jamiesjc: For conditional workflows, if conditionOnJobStatus is ONE_SUCCESS or
        // ONE_FAILED, some jobs might still be running when the end nodes have finished. In this
        // case, we need to kill all running jobs before finalizing the flow.
        finalizeFlow(parentFlow);
        finishExecutableNode(parentFlow);

        // If the parent has a parent, then we process
        if (!(parentFlow instanceof ExecutableFlow)) {
          outNodeIds = parentFlow.getOutNodes();
          parentFlow = parentFlow.getParentFlow();
        }
      }

      // Add all out nodes from the finished job. We'll check against this set
      // to
      // see if any are candidates for running.
      for (final String nodeId : outNodeIds) {
        final ExecutableNode outNode = parentFlow.getExecutableNode(nodeId);
        nodesToCheck.add(outNode);
      }
    }
    // FIXME New function, run according to the priority set by the node.
    boolean openPriority = false;
    List<ExecutableNode> priList = new ArrayList<>();
    for (final ExecutableNode node : nodesToCheck) {
      priList.add(node);
      prepareJobProperties(node);
      if(node.getInputProps() != null){
        String priLevel = node.getInputProps().get("priority");
        if(null != priLevel && verifyPriority(priLevel)){
          logger.warn("任务" + node.getId() + "优先级采参数设置错误！请设置priority的值为1,2,3,4,5这5个数字中的一个！");
          openPriority = false;
          node.getInputProps().put("priority","0");
          continue;
        }
        if(null != priLevel){
          openPriority = true;
        }
      }else {
        Props initProps = new Props();
        initProps.put("priority", 0);
        node.setInputProps(initProps);
      }
    }
    //启动优先级机制执行同一个父节点下面的Job
    if(openPriority){
      //按优先级排序
      Collections.sort(priList, new Comparator<ExecutableNode>() {
        @Override
        public int compare(ExecutableNode o1, ExecutableNode o2) {
          String priFString = o1.getInputProps().get("priority");
          String priSString = o2.getInputProps().get("priority");
          Integer priF = priFString == null ? 0 : Integer.valueOf(priFString);
          Integer priS = priSString == null ? 0 : Integer.valueOf(priSString);
          if(priF > priS){
            return -1;
          }
          return 1;
        }
      });

      boolean jobsRun = false;
      String preNodeId = "";
      for (final ExecutableNode node : priList) {
        if (notReadyToRun(node.getStatus())) {
          // Really shouldn't get in here.
          continue;
        }

        jobsRun |= runReadyJobByPriority(node, preNodeId);

        preNodeId = node.getId();
      }
      if (jobsRun || this.finishedNodes.getSize() > 0) {
        updateFlow();
        return true;
      }

    } else {
      // Runs candidate jobs. The code will check to see if they are ready to run
      // before
      // Instant kill or skip if necessary.
      boolean jobsRun = false;
      for (final ExecutableNode node : nodesToCheck) {
        if (notReadyToRun(node.getStatus())) {
          // Really shouldn't get in here.
          continue;
        }

        jobsRun |= runReadyJob(node);
      }
      if (jobsRun || this.finishedNodes.getSize() > 0) {
        updateFlow();
        return true;
      }
    }

    return false;
  }

  private void setFlowFailed(final ExecutableNode node) {
    boolean shouldFail = true;
    // As long as there is no outNodes or at least one outNode has conditionOnJobStatus of
    // ALL_SUCCESS, we should set the flow to failed. Otherwise, it could still statisfy the
    // condition of conditional workflows, so don't set the flow to failed.
    for (final String outNodeId : node.getOutNodes()) {
      if (node.getParentFlow().getExecutableNode(outNodeId).getConditionOnJobStatus()
              .equals(ConditionOnJobStatus.ALL_SUCCESS)) {
        shouldFail = true;
        break;
      } else {
        shouldFail = false;
      }
    }

    if (shouldFail) {
      propagateStatusAndAlert(node.getParentFlow(),
              node.getStatus() == Status.KILLED ? Status.KILLED : Status.FAILED_FINISHING);
      if (this.failureAction == FailureAction.CANCEL_ALL) {
        this.kill();
      }
      //节点状态是FAILED_WAITING不能将flow置为failed
      if(!node.getStatus().equals(Status.FAILED_WAITING)) {
        this.flowFailed = true;
      }
    }
  }

  private boolean notReadyToRun(final Status status) {
    return Status.isStatusFinished(status)
            || Status.isStatusRunning(status)
            || Status.KILLING == status;
  }

  private boolean runReadyJob(final ExecutableNode node) throws IOException {
    if (Status.isStatusFinished(node.getStatus())
            || Status.isStatusRunning(node.getStatus())) {
      return false;
    }

    final Status nextNodeStatus = getImpliedStatus(node, this.flow.getOtherOption());
    if (nextNodeStatus == null) {
      return false;
    }

    if (nextNodeStatus == Status.CANCELLED) {
      this.logger.info("Cancelling '" + node.getNestedId()
              + "' due to prior errors.");
      node.cancelNode(System.currentTimeMillis());
      finishExecutableNode(node);
    } else if (nextNodeStatus == Status.SKIPPED) {
      this.logger.info("Skipping disabled job '" + node.getId() + "'.");
      node.skipNode(System.currentTimeMillis());
      finishExecutableNode(node);
    } else if (nextNodeStatus == Status.FAILED_SKIPPED) {
      this.logger.info("Failed skipped job '" + node.getId() + "'.");
      node.faliedSkipedNode(System.currentTimeMillis());
      finishExecutableNode(node);
    }else if (nextNodeStatus == Status.READY) {
      if (node instanceof ExecutableFlowBase) {
        final ExecutableFlowBase flow = ((ExecutableFlowBase) node);
        this.logger.info("baseFlow :" + flow.getFlowId() + " , " + flow.getId() + " Running flow '" + flow.getNestedId() + "'.");
        flow.setStatus(Status.RUNNING);
        flow.setStartTime(System.currentTimeMillis());
        prepareJobProperties(flow);

        for (final String startNodeId : ((ExecutableFlowBase) node).getStartNodes()) {
          final ExecutableNode startNode = flow.getExecutableNode(startNodeId);
          runReadyJob(startNode);
        }
      } else {
        runExecutableNode(node);
      }
    }
    return true;
  }

  private boolean retryJobIfPossible(final ExecutableNode node) {
    if (node instanceof ExecutableFlowBase) {
      return false;
    }

    if (node.getRetries() > node.getAttempt()) {
      this.logger.info("Job '" + node.getId() + "' will be retried. Attempt "
              + node.getAttempt() + " of " + node.getRetries());
      node.setDelayedExecution(node.getRetryBackoff());
      node.resetForRetry();
      return true;
    } else {
      if (node.getRetries() > 0) {
        this.logger.info("Job '" + node.getId() + "' has run out of retry attempts");
        // Setting delayed execution to 0 in case this is manually re-tried.
        node.setDelayedExecution(0);
      }

      return false;
    }
  }

  /**
   * Recursively propagate status to parent flow. Alert on first error of the flow in new AZ
   * dispatching design.
   *
   * @param base the base flow
   * @param status the status to be propagated
   */
  private void propagateStatusAndAlert(final ExecutableFlowBase base, final Status status) {
    if (!Status.isStatusFinished(base.getStatus()) && base.getStatus() != Status.KILLING) {
      this.logger.info("Setting " + base.getNestedId() + " to " + status);
      boolean shouldAlert = false;
      if (base.getStatus() != status) {
        base.setStatus(status);
        shouldAlert = true;
      }
      if (base.getParentFlow() != null) {
        propagateStatusAndAlert(base.getParentFlow(), status);
      } else if (this.azkabanProps.getBoolean(ConfigurationKeys.AZKABAN_POLL_MODEL, false)) {
        // Alert on the root flow if the first error is encountered.
        // Todo jamiesjc: Add a new FLOW_STATUS_CHANGED event type and alert on that event.
        if (shouldAlert && base.getStatus() == Status.FAILED_FINISHING) {
          ExecutionControllerUtils.alertUserOnFirstError((ExecutableFlow) base, this.alerterHolder);
        }
      }
    }
  }

  private void finishExecutableNode(final ExecutableNode node) {
    this.finishedNodes.add(node);
    final EventData eventData = new EventData(node.getStatus(), node.getNestedId());
    fireEventListeners(Event.create(this, EventType.JOB_FINISHED, eventData));
  }

  private boolean isFlowReadytoFinalize(final ExecutableFlowBase flow) {
    // Only when all the end nodes are finished, the flow is ready to finalize.
    for (final String end : flow.getEndNodes()) {
      if (!Status.isStatusFinished(flow.getExecutableNode(end).getStatus())) {
        return false;
      }
    }
    return true;
  }

  private void finalizeFlow(final ExecutableFlowBase flow) {
    // FIXME If it is ExecutableFlow, id is the name of the job flow.
    final String id = flow == this.flow ? flow.getFlowId() : flow.getNestedId();

    // If it's not the starting flow, we'll create set of output props
    // for the finished flow.
    boolean succeeded = true;
    Props previousOutput = null;
    //最后一个节点成功就能确保作业流成功？
    List<String> skipFaultJobList = (ArrayList) this.flow.getOtherOption()
            .get("jobSkipFailedOptions");
      for (final ExecutableNode node : flow.getExecutableNodes()) {
          if (Status.isStatusFailed(node.getStatus()) || Status.KILLING.equals(node.getStatus())) {
              // FIXME Solve the problem that the last node of the sub-job stream fails to be set, and the sub-job stream is still failed.
            if (Status.FAILED.equals(node.getStatus()) && (this.flow.getFailedSkipedAllJobs() || (
                    null != skipFaultJobList && (skipFaultJobList.contains(node.getNestedId())
                            || skipFaultJobList.contains(node.getId()))) || (validJobInSkipFLow(node,
                    skipFaultJobList)))) {
                  logger.info("用户已设置错误跳过策略，跳过错误状态 Job:" + node.getNestedId() + " 继续执行。");
              } else {
                  succeeded = false;
                  break;
              }

          }
      }

      for (final String end : flow.getEndNodes()) {
          final ExecutableNode node = flow.getExecutableNode(end);
          Props output = node.getOutputProps();
          if (output != null) {
              output = Props.clone(output);
              output.setParent(previousOutput);
              previousOutput = output;
          }
      }
    //保证每个节点都是成功作业流才是成功
    // FIXME The flow 1.0 scenario needs to ensure that each task in the job stream is successful before the job stream is executed successfully.
//    if(!FlowLoaderUtils.isAzkabanFlowVersion20(this.flow.getAzkabanFlowVersion())) {
//      for (ExecutableNode executableNode : flow.getExecutableNodes()) {
//        if (Status.isStatusFailed(executableNode.getStatus()) || executableNode.getStatus().equals(Status.KILLING)) {
//          logger.warn("job: " + executableNode.getNestedId() + " is not succeesed.");
//          succeeded = false;
//          break;
//        }
//      }
//    }

    flow.setOutputProps(previousOutput);
    if (!succeeded && (flow.getStatus() == Status.RUNNING)) {
      flow.setStatus(Status.KILLED);
    }

    flow.setEndTime(System.currentTimeMillis());
    flow.setUpdateTime(System.currentTimeMillis());
    final long durationSec = (flow.getEndTime() - flow.getStartTime()) / 1000;
    switch (flow.getStatus()) {
      case FAILED_FINISHING:
        this.logger.info("Setting flow '" + id + "' status to FAILED in "
                + durationSec + " seconds");
        flow.setStatus(Status.FAILED);
        break;
      case KILLING:
        this.logger
                .info("Setting flow '" + id + "' status to KILLED in " + durationSec + " seconds");
        flow.setStatus(Status.KILLED);
        break;
      case FAILED:
      case KILLED:
      case CANCELLED:
      case FAILED_SUCCEEDED:
        this.logger.info("Flow '" + id + "' is set to " + flow.getStatus().toString()
                + " in " + durationSec + " seconds");
        break;
      default:
        flow.setStatus(Status.SUCCEEDED);
        this.logger.info("Flow '" + id + "' is set to " + flow.getStatus().toString()
                + " in " + durationSec + " seconds");
    }

    // If the finalized flow is actually the top level flow, than we finish
    // the main loop.
    if (flow instanceof ExecutableFlow) {
      this.flowFinished = true;
    }
  }
    private boolean validJobInSkipFLow(ExecutableNode node, List<String> list) {
        if (CollectionUtils.isEmpty(list)) {
            return false;
        }
        if (list.contains("subflow:" + node.getParentFlow().getFlowId())) {
            return true;
        }
        if (node.getParentFlow().getParentFlow() != null) {
            return validJobInSkipFLow(node.getParentFlow(), list);
        }
        return false;
    }

  /**
   *  解析job文件，获取里面的配置   .job > output变量 > 父作业流配置(包含azkaban内置变量，执行参数) > properties > userProperties
   * @param node
   * @throws IOException
   */
  private void prepareJobProperties(final ExecutableNode node) throws IOException {
    if (node instanceof ExecutableFlow) {
      return;
    }

    Props props = null;

    if (!FlowLoaderUtils.isAzkabanFlowVersion20(this.flow.getAzkabanFlowVersion())) {
      // 1. Shared properties (i.e. *.properties) for the jobs only. This takes
      // the
      // least precedence
      if (!(node instanceof ExecutableFlowBase)) {
        // 返回properties里的配置
        final String sharedProps = node.getPropsSource();
        if (sharedProps != null) {
          props = Props.clone(this.sharedProps.get(sharedProps));
        }
      }
    }

    // The following is the hiearchical ordering of dependency resolution
    // 2. Parent Flow Properties
    final ExecutableFlowBase parentFlow = node.getParentFlow();
    if (parentFlow != null) {
      final Props flowProps = Props.clone(parentFlow.getInputProps());
      flowProps.setEarliestAncestor(props);
      props = flowProps;
    }

    /**
     *     2.1 if it is a subflow,we need to put baseflow's executionOption to props
     *     ns_wtss is a namespace param ,if it is false ,the last outputParam is not
     *     a global variable
     */
    // FIXME Add global variables output by the task to props.
    if(props != null){
      props.putAll(this.flow.getExecutionOptions().getFlowParameters());
      props.putAll(this.flow.getJobOutputGlobalParam());
    }

    // 3.job Output Properties. The call creates a clone, so we can overwrite it.
    final Props outputProps = collectOutputProps(node);
    Props tmpOutputProps = null;
    if (outputProps != null && outputProps.size() != 0) {
      tmpOutputProps = Props.clone(outputProps);
      outputProps.setEarliestAncestor(props);
      props = outputProps;
    }

    // 4. The job source.
    final Props jobSource = loadJobProps(node);
    if (jobSource != null) {
      jobSource.setParent(props);
      props = jobSource;
    }

    // 只有字job才能有用户参数，子flow不可以，如果该用户存有用户配置， 将用户配置设置为properties配置的祖父级配置
    // FIXME Add user global parameters to Props.
    if((!(node instanceof ExecutableFlowBase)) && this.flow.getUserProps() != null && this.flow.getUserProps().size() != 0) {
      Props userProps = new Props();
      userProps.putAll(this.flow.getUserProps());
      //如果有properties配置，设置为prop的祖父级配置
      if (props != null) {
        props.setEarliestAncestor(userProps);
      }
    }
    // 全局变量开关
    String allFlag = props.getString("ns_wtss","true");
    if (tmpOutputProps != null) {
      if (!"false".equals(allFlag)) {
        setAllVar(tmpOutputProps);
      }
    }

    // FIXME Add some execution parameters to props, such as failed retry tasks, failed skip tasks, etc.
    setExecutionProps(node, props);

    // FIXME Add workflow execution root directory to props.
    props.put("flow.dir", this.execDir.getAbsolutePath());
    node.setInputProps(props);
  }

  private void setExecutionProps(ExecutableNode node, Props props){
    // 1、是否设置了跳过所有job
    // 2、job设置了失败跳过， 通过配置专递给job运行
    List<String> skipFaultJobList = (ArrayList) this.flow.getOtherOption().get("jobSkipFailedOptions");
    if(this.flow.getFailedSkipedAllJobs() || (skipFaultJobList != null && skipFaultJobList.contains(node.getId()))){
      logger.info("execId: " + this.flow.getExecutionId() + ", node: " +  node.getId() + ", 设置了失败跳过.");
      props.put("azkaban.jobSkipFailed", node.getId());
    }

    //设置了失败暂停 当job失败时状态改为FAILED_WAITING
    if(FlowRunner.this.failureAction == FailureAction.FAILED_PAUSE){
      logger.debug("execId: " + this.flow.getExecutionId() + "， 设置了失败暂停。");
      props.put("azkaban.failureAction", FailureAction.FAILED_PAUSE.toString());
    }

    //获取失败重跑配置并添加到 Job 的配置内容中
    if(null != this.flow.getOtherOption().get("jobFailedRetryOptions")){
      List<Map<String, String>> jobFailedRetryOptions = (List<Map<String, String>>)this.flow.getOtherOption().get("jobFailedRetryOptions");
      for(Map<String, String> map : jobFailedRetryOptions){
        if(node.getId().equals(map.get("jobName"))){
          props.put("job.failed.retry.interval", map.get("interval"));
          props.put("job.failed.retry.count", map.get("count"));
        }
      }
    }
    // 设置里flow失败重跑，所有job都要继承该配置
    Map<String, String> flowFailedRetryOption = this.flow.getFlowFailedRetry();
    if(flowFailedRetryOption != null && flowFailedRetryOption.size() != 0){
      Props flowFailedRetryProps = new Props(null);
      flowFailedRetryProps.putAll(flowFailedRetryOption);
      props.setEarliestAncestor(flowFailedRetryProps);
    }
  }

  private void setAllVar(Props outputProps){
//    this.flow.getExecutionOptions().getFlowParameters().putAll(outputProps.getFlattened());
    this.flow.addJobOutputGlobalParam(new ConcurrentHashMap<>(outputProps.getFlattened()));
    this.flow.getInputProps().putAll(outputProps);
  }

  /**
   * @param props This method is to put in any job properties customization before feeding to the
   * job.
   */
  private void customizeJobProperties(final Props props) {
    final boolean memoryCheck = this.flow.getExecutionOptions().getMemoryCheck();
    props.put(ProcessJob.AZKABAN_MEMORY_CHECK, Boolean.toString(memoryCheck));
  }
  //解析job文件获取配置
  private Props loadJobProps(final ExecutableNode node) throws IOException {
    Props props = null;
    if (FlowLoaderUtils.isAzkabanFlowVersion20(this.flow.getAzkabanFlowVersion())) {
      final String jobPath =
              node.getParentFlow().getFlowId() + Constants.PATH_DELIMITER + node.getId();
      int retrylimit = 0;
      while(retrylimit < 3){
        props = loadPropsFromYamlFile(jobPath);
        retrylimit++;
        this.logger.info("Job path loaded from yaml file " + jobPath);
        if(props != null) break;
      }
      if (props == null) {
        this.logger.info("Job props loaded from yaml file is empty for job " + node.getId());
        return props;
      }
    } else {
      final String source = node.getJobSource();
      if (source == null) {
        return null;
      }

      // load the override props if any
      try {
        props =
                this.projectLoader.fetchProjectProperty(this.flow.getProjectId(),
                        this.flow.getVersion(), node.getId() + Constants.JOB_OVERRIDE_SUFFIX);
      } catch (final ProjectManagerException e) {
        e.printStackTrace();
        this.logger.error("Error loading job override property for job "
                + node.getId());
      }

      final File path = new File(this.execDir, source);
      if (props == null) {
        // if no override prop, load the original one on disk
        try {
          props = new Props(null, path);
        } catch (final IOException e) {
          e.printStackTrace();
          this.logger.error("Error loading job file " + source + " for job "
                  + node.getId());
        }
      }
      // setting this fake source as this will be used to determine the location
      // of log files.
      if (path.getPath() != null) {
        props.setSource(path.getPath());
      }
    }

    customizeJobProperties(props);

    return props;
  }

  private Props loadPropsFromYamlFile(final String path) {
    File tempDir = null;
    Props props = null;
    try {
      tempDir = Files.createTempDir();
      props = FlowLoaderUtils.getPropsFromYamlFile(path, getFlowFile(tempDir));
    } catch (final Exception e) {
      this.logger.error("Failed to get props from flow file. " + e);
    } finally {
      if (tempDir != null && tempDir.exists()) {
        try {
          FileUtils.deleteDirectory(tempDir);
        } catch (final IOException e) {
          this.logger.error("Failed to delete temp directory." + e);
          tempDir.deleteOnExit();
        }
      }
    }
    return props;
  }


  private File getFlowFile(final File tempDir) throws Exception {
    final List<FlowProps> flowPropsList = ImmutableList.copyOf(this.flow.getFlowProps());
    // There should be exact one source (file name) for each flow file.
    if (flowPropsList.isEmpty() || flowPropsList.get(0) == null) {
      throw new ProjectManagerException(
              "Failed to get flow file source. Flow props is empty for " + this.flow.getId());
    }
    final String source = flowPropsList.get(0).getSource();
    List<File> files = new ArrayList<>();
    // FIXME Instead, get the '.flow' file from the workingdir directory and copy it to the tmp directory. Resolving the run_date variable substitution in the yaml file does not take effect.
    FileIOUtils.findFile(this.execDir, source, files);
    File flowFile = new File(tempDir, source);
    if(files.size() != 0){
      FileUtils.copyFile(files.get(0), flowFile);
    } else {
      logger.error("can not found " + source + " file at " + this.execDir.getAbsoluteFile());
      throw new Exception("can not found " + source + " file at " + this.execDir.getAbsoluteFile());
    }

    return flowFile;
  }

  @SuppressWarnings("FutureReturnValueIgnored")
  private void runExecutableNode(final ExecutableNode node) throws IOException {
    // Collect output props from the job's dependencies.
    prepareJobProperties(node);

    node.setStatus(Status.QUEUED);
    final JobRunner runner = createJobRunner(node);
    this.logger.info("Submitting job '" + node.getNestedId() + "' to run.");
    try {
      // FIXME Submit datachecker and eventchecker tasks to the executorServiceForCheckers thread pool to run.
      if(node.getType().equals("datachecker") || node.getType().equals("eventchecker")){
        this.executorServiceForCheckers.execute(runner);
      }else {
        this.executorService.execute(runner);
      }
      this.activeJobRunners.add(runner);

    } catch (final RejectedExecutionException e) {
      this.logger.error("", e);
    }
  }

  /**
   * Determines what the state of the next node should be. Returns null if the node should not be
   * run.获取前一个依赖节点的Node执行状态 使用一个参数配置来实现跳过Node状态错误继续执行
   */
  public Status getImpliedStatus(final ExecutableNode node) {
    // If it's running or finished with 'SUCCEEDED', than don't even
    // bother starting this job.
    if (Status.isStatusRunning(node.getStatus())
            || node.getStatus() == Status.SUCCEEDED) {
      return null;
    }

    // Go through the node's dependencies. If all of the previous job's
    // statuses is finished and not FAILED or KILLED, than we can safely
    // run this job.
    Status status = Status.READY;

    // Check if condition on job status is satisfied
    switch (checkConditionOnJobStatus(node)) {
      case FAILED:
        this.logger.info("Condition on job status: " + node.getConditionOnJobStatus() + " is "
                + "evaluated to false for " + node.getId());
        status = Status.CANCELLED;
        break;
      // Condition not satisfied yet, need to wait
      case PENDING:
        return null;
      default:
        break;
    }

    if (status != Status.CANCELLED && !isConditionOnRuntimeVariableMet(node)) {
      status = Status.CANCELLED;
    }

    // If it's disabled but ready to run, we want to make sure it continues
    // being disabled.
    if (node.getStatus() == Status.DISABLED
            || node.getStatus() == Status.SKIPPED) {
      return Status.SKIPPED;
    }

    // If the flow has failed, and we want to finish only the currently running
    // jobs, we just
    // kill everything else. We also kill, if the flow has been cancelled.
    if (this.flowFailed
            && this.failureAction == FailureAction.FINISH_CURRENTLY_RUNNING) {
      return Status.CANCELLED;
    } else if (isKilled()) {
      return Status.CANCELLED;
    }

    return status;
  }

  private Boolean isConditionOnRuntimeVariableMet(final ExecutableNode node) {
    final String condition = node.getCondition();
    if (condition == null) {
      return true;
    }

    String replaced = condition;
    // Replace the condition on job status macro with "true" to skip the evaluation by Script
    // Engine since it has already been evaluated.
    final Matcher jobStatusMatcher = CONDITION_ON_JOB_STATUS_PATTERN.matcher
            (condition);
    if (jobStatusMatcher.find()) {
      replaced = condition.replace(jobStatusMatcher.group(1), "true");
    }

    final Matcher variableMatcher = CONDITION_VARIABLE_REPLACEMENT_PATTERN.matcher(replaced);

    while (variableMatcher.find()) {
      final String value = findValueForJobVariable(node, variableMatcher.group(1),
              variableMatcher.group(2));
      if (value != null) {
        replaced = replaced.replace(variableMatcher.group(), "'" + value + "'");
      }
      this.logger.info("Resolved condition of " + node.getId() + " is " + replaced);
    }

    // Evaluate string expression using script engine
    return evaluateExpression(replaced);
  }

  private String findValueForJobVariable(final ExecutableNode node, final String jobName, final
  String variable) {
    // Get job output props
    final ExecutableNode target = node.getParentFlow().getExecutableNode(jobName);
    if (target == null) {
      this.logger.error("Not able to load props from output props file, job name " + jobName
              + " might be invalid.");
      return null;
    }

    final Props outputProps = target.getOutputProps();
    if (outputProps != null && outputProps.containsKey(variable)) {
      return outputProps.get(variable);
    }

    return null;
  }

  private boolean evaluateExpression(final String expression) {
    boolean result = false;
    final ScriptEngineManager sem = new ScriptEngineManager();
    final ScriptEngine se = sem.getEngineByName("JavaScript");

    // Restrict permission using the two-argument form of doPrivileged()
    try {
      final Object object = AccessController.doPrivileged(
              new PrivilegedExceptionAction<Object>() {
                @Override
                public Object run() throws ScriptException {
                  return se.eval(expression);
                }
              },
              new AccessControlContext(
                      new ProtectionDomain[]{new ProtectionDomain(null, null)}) // no permissions
      );
      if (object != null) {
        result = (boolean) object;
      }
    } catch (final Exception e) {
      this.logger.error("Failed to evaluate the condition.", e);
    }

    this.logger.info("Condition is evaluated to " + result);
    return result;
  }

  
  private Props collectOutputProps(final ExecutableNode node) {
    Props previousOutput = null;
    // Iterate the in nodes again and create the dependencies
    for (final String dependency : node.getInNodes()) {
      ExecutableNode executableNode = node.getParentFlow().getExecutableNode(dependency);
      // FIXME If it is preparing to execute the submitted task, it needs to inherit the parameters of the output of the last executed job stream. In order to solve the problem that after the task execution fails, the new task cannot inherit the parameters output by the old task.
	  Props output = executableNode.getOutputProps();
      int lastExecId = (int)this.flow.getOtherOption().getOrDefault("lastExecId", -1);
      try {
        if(output == null && lastExecId != -1 && !this.flow.getLastNsWtss()) {
          String jobId = executableNode.getId();
          if(executableNode instanceof ExecutableFlowBase){
            ExecutableFlowBase baseFlow = (ExecutableFlowBase)executableNode;
            jobId = baseFlow.getFlowId();
          }
          output = this.executorLoader.fetchExecutionJobOutputProps(lastExecId, jobId);
          logger.debug(jobId + ", output: " + output);
        }
      }catch (ExecutorManagerException e){
        logger.error("fetch job output param failed", e);
      }
      if (output != null && output.size() != 0) {
        output = Props.clone(output);
        output.setParent(previousOutput);
        previousOutput = output;
      }
    }

    return previousOutput;
  }

  private JobRunner createJobRunner(final ExecutableNode node) {
    // Load job file.
    final File path = new File(this.execDir, node.getJobSource());

    final JobRunner jobRunner =
            new JobRunner(node, path.getParentFile(), this.executorLoader,
                    this.jobtypeManager, this.azkabanProps);
    if (this.watcher != null) {
      jobRunner.setPipeline(this.watcher, this.pipelineLevel);
    }
    if (this.validateUserProxy) {
      jobRunner.setValidatedProxyUsers(this.proxyUsers);
    }

    jobRunner.setDelayStart(node.getDelayedExecution());
    jobRunner.setLogSettings(this.logger, this.jobLogFileSize, this.jobLogNumFiles);
    jobRunner.addListener(this.listener);

    if (JobCallbackManager.isInitialized()) {
      jobRunner.addListener(JobCallbackManager.getInstance());
    }

    configureJobLevelMetrics(jobRunner);

    return jobRunner;
  }

  /**
   * Configure Azkaban metrics tracking for a new jobRunner instance
   */
  private void configureJobLevelMetrics(final JobRunner jobRunner) {
    this.logger.info("Configuring Azkaban metrics tracking for jobrunner object");
    if (MetricReportManager.isAvailable()) {
      final MetricReportManager metricManager = MetricReportManager.getInstance();

      // Adding NumRunningJobMetric listener
      jobRunner.addListener((NumRunningJobMetric) metricManager
              .getMetricFromName(NumRunningJobMetric.NUM_RUNNING_JOB_METRIC_NAME));

      // Adding NumFailedJobMetric listener
      jobRunner.addListener((NumFailedJobMetric) metricManager
              .getMetricFromName(NumFailedJobMetric.NUM_FAILED_JOB_METRIC_NAME));

    }

    jobRunner.addListener(JmxJobMBeanManager.getInstance());
  }

  public void pause(final String user) {
    synchronized (this.mainSyncObj) {
      if (!this.flowFinished) {
        this.logger.info("Flow paused by " + user + ". If the pause is not cancelled after " + (double)this.maxPausedTime/1000/60 + " minutes, the flow will automatically resume running.");
        this.flowPaused = true;
        this.flow.setStatus(Status.PAUSED);
        this.pausedStartTime = System.currentTimeMillis();
        updateFlow();
      } else {
        this.logger.info("Cannot pause finished flow. Called by user " + user);
      }
    }

    interrupt();
  }

  /**
   * Reset the workflow to running when there are no failed job.
   * @param base
   * @param executableNode
   */
  private void resetFlowStatus(final ExecutableFlowBase base, final ExecutableNode executableNode) {
    int failedNodes = 0;
    for(ExecutableNode node: base.getExecutableNodes()){
      if(node.getStatus().equals(Status.FAILED_WAITING) || node.getStatus().equals(Status.FAILED_FINISHING)){
        if(!executableNode.getNestedId().equals(node.getNestedId())) {
          failedNodes++;
        }
      }
    }
    if(failedNodes == 0){
      //将flow状态改为Running, 子flow有job没跑完，父级flow肯定还是running
      if(!base.getStatus().equals(Status.PAUSED)){
        base.setStatus(Status.RUNNING);
      }
      if(base instanceof ExecutableFlow) {
        this.isFailedPaused = false;
      }
    }
    if(base.getParentFlow() != null) {
      resetFlowStatus(base.getParentFlow(), executableNode);
    }
  }

  /**
   *  Skip the failed_waiting status job.
   * @param skipFailedJobs
   */
  public String setSkipFailedJob(List<String> skipFailedJobs) {
    if(this.flowKilled){
      logger.warn("flow has been killed, can not skipped the failed jobs");
      return "flow has been killed.";
    }
    synchronized (this.mainSyncObj){
      String message = null;
      if(this.flowKilled){
        logger.warn("flow has been killed, can not skipped the failed jobs");
        return "作业流已被killed，不能执行跳过任务。";
      }
      ExecutableNode node = this.flow.getExecutableNodePath(skipFailedJobs.get(0));
      if(node != null && !node.getStatus().equals(Status.FAILED_WAITING)){
        message = "job: " + skipFailedJobs.get(0) + ", 状态不是FAILED_WAITING，不能设置跳过执行。";
        logger.warn(message);
        return message;
      }
      stopKillFlowTrigger();
      //还原flow状态为Running
      resetFlowStatus(node.getParentFlow(), node);

      if(FlowRunner.this.failureAction == FailureAction.FAILED_PAUSE) {
        ExecutableNode executableNode = this.flow.getExecutableNodePath(skipFailedJobs.get(0));
        if(!executableNode.getStatus().equals(Status.FAILED_WAITING)){
          logger.warn("job: " + skipFailedJobs.get(0) + "不是FAILED_WAITING，不能设置失败跳过。");
          message = "job: " + skipFailedJobs.get(0) + "不是FAILED_WAITING，不能设置失败跳过。";
        }
        if(executableNode.getStatus().equals(Status.FAILED_WAITING)){
          executableNode.setStatus(Status.FAILED_SKIPPED);
          executableNode.setUpdateTime(System.currentTimeMillis());
          updateFlow();
          FlowRunner.this.finishedNodes.add(executableNode);
        }
        for (String nodeName : skipFailedJobs) {
          this.failedNodes.remove(nodeName);
        }
        interrupt();
      } else{
        logger.warn("作业流不是暂停状态不能设置了失败跳过。");
        message = "作业流不是暂停状态不能设置了失败跳过。";
      }
      return message;
    }
  }

  public void resume(final String user) {
    synchronized (this.mainSyncObj) {
      if (!this.flowPaused) {
        this.logger.info("Cannot resume flow that isn't paused");
      } else {
        this.logger.info("Flow resumed by " + user);
        reStart();
        updateFlow();
      }
    }

    interrupt();
  }

  private void reStart(){
    this.flowPaused = false;
    if (this.flowFailed || this.isFailedPaused) {
      logger.info("set flow status FAILED_FINISHING");
      this.flow.setStatus(Status.FAILED_FINISHING);
    } else if (this.flowKilled) {
      logger.info("set flow status KILLING");
      this.flow.setStatus(Status.KILLING);
    } else {
      logger.info("set flow status RUNNING");
      this.flow.setStatus(Status.RUNNING);
    }
  }

  /**
   *  This method can only be used to kill the workflow
   *  when the workflow has been in the killing state and cannot be terminated.
   * @param user
   */
  public void superKill(String user){
    this.logger.info("execId: " + this.execId + ", flow killed by " + user);
    synchronized (this.mainSyncObj) {
      if (this.flowFinished) {
        this.logger.info("Flow already finished.");
        return;
      }
      if (this.watcher != null) {
        this.logger.info("Watcher is attached. Stopping watcher.");
        this.watcher.stopWatcher();
        this.logger
            .info("Watcher cancelled status is " + this.watcher.isWatchCancelled());
      }
      this.flow.setStatus(Status.KILLED);
      this.flow.setEndTime(System.currentTimeMillis());
      final long time = System.currentTimeMillis();
      for (final ExecutableNode node : this.flow.getExecutableNodes()) {
        switch (node.getStatus()) {
          case SUCCEEDED:
          case FAILED:
          case KILLED:
          case SKIPPED:
          case DISABLED:
          case FAILED_SKIPPED:
          case CANCELLED:
          case RETRIED_SUCCEEDED:
            continue;
            // case UNKNOWN:
          case READY:
            node.setStartTime(time);
            node.setEndTime(time);
            node.setUpdateTime(time);
            node.setStatus(Status.CANCELLED);
            break;
          default:
            node.setEndTime(time);
            node.setUpdateTime(time);
            node.setStatus(Status.KILLED);
            break;
        }
      }
      this.flowFinished = true;
      interrupt();
    }
  }

  public void kill(final String user) {
    this.logger.info("Flow killed by " + user);
    kill();
  }

  public void kill() {
    synchronized (this.mainSyncObj) {
      if (this.flowKilled) {
        return;
      }
      Status lastStatus = this.flow.getStatus();
      this.logger.info("Kill has been called on flow " + this.execId);
      this.flow.setStatus(Status.KILLING);
      // If the flow is paused, then we'll also unpause
      this.flowPaused = false;
      this.flowKilled = true;

      if (this.watcher != null) {
        this.logger.info("Watcher is attached. Stopping watcher.");
        this.watcher.stopWatcher();
        this.logger
                .info("Watcher cancelled status is " + this.watcher.isWatchCancelled());
      }

      this.logger.info("Killing " + this.activeJobRunners.size() + " jobs.");
      for (final JobRunner runner : this.activeJobRunners) {
        this.logger.info("killing job:" + runner.getNode().getNestedId());
        runner.kill();
        ExecutableNode node = runner.getNode();
        // FIXME New function. When the job stream is terminated, the FAILED_WAITING state job needs to be set to the killed state.
        if(node.getStatus().equals(Status.FAILED_WAITING)){
          this.logger.info(String.format("change job: %s , old status %s to KILLED", node.getNestedId(), node.getStatus()));
          node.setStatus(Status.KILLED);
          node.setUpdateTime(System.currentTimeMillis());
        }
      }
      /**
       * 因为设置了失败暂停的时候没有将执行失败的节点添加到finishedNodes里，导致前台点击kill的时候失败了
       * 现在kill的时候将失败节点重新添加到finishedNodes里
       */
      if(FlowRunner.this.failureAction == FailureAction.FAILED_PAUSE) {
        for(ExecutableNode failedNode: FlowRunner.this.failedNodes.values()){
          this.logger.info(String.format("change job: %s , old status %s", failedNode.getNestedId(), failedNode.getStatus()));
          if(failedNode.getStatus().equals(Status.FAILED_WAITING)){
            failedNode.setStatus(Status.KILLED);
            failedNode.setUpdateTime(System.currentTimeMillis());
          }
          FlowRunner.this.finishedNodes.add(failedNode);
        }
      }
      if (Status.FAILED.equals(lastStatus)) {
        this.flow.setStatus(Status.KILLED);
      }
      updateFlow();
    }
    interrupt();
  }

  public void retryFailures(final String user) {
    synchronized (this.mainSyncObj) {
      this.logger.info("Retrying failures invoked by " + user);
      // FIXME Add judgment. If the job stream has been completed, you cannot retry execution.
      if(this.flowFinished){
        this.logger.info("this flow was finished.");
        return;
      }
      // FIXME If you click to retry the task, you need to cancel the thread that timed out the job stream.
      stopKillFlowTrigger();
      this.retryFailedJobs = true;
      interrupt();
    }
  }

  private void stopKillFlowTrigger(){
    if(killFlowTrigger != null && killFlowTrigger.isAlive()) {
      killFlowTrigger.stopKillFLowTrigger();
      FlowRunner.this.isTriggerStarted = false;
      logger.info("stop killing workflow.");
    }
  }

  private void setDependentlinkFailed(ExecutableNode node, boolean flag){
    if(FlowLoaderUtils.isAzkabanFlowVersion20(this.flow.getAzkabanFlowVersion())){
      return;
    }
    node.setDependentlinkFailed(flag);
  }

  // FIXME There is a bug that the job stream has been in the killing state and cannot be turned to the killed state after clicking the failed retry after the job stream fails. It has been fixed.
  private void resetFailedState(final ExecutableFlowBase flow,
                                final List<ExecutableNode> nodesToRetry) {
    // bottom up
    final LinkedList<ExecutableNode> queue = new LinkedList<>();
    for (final String id : flow.getEndNodes()) {
      final ExecutableNode node = flow.getExecutableNode(id);
      queue.add(node);
    }

    long maxStartTime = -1;
    while (!queue.isEmpty()) {
      final ExecutableNode node = queue.poll();
      final Status oldStatus = node.getStatus();
      maxStartTime = Math.max(node.getStartTime(), maxStartTime);
      setDependentlinkFailed(node, false);
      this.logger.info("reset node: " + node.getNestedId() + " oldStatus is:" + oldStatus + " isDependentlinkFailed: " + node.isDependentlinkFailed());
      final long currentTime = System.currentTimeMillis();
      if (Status.isSucceeded(node.getStatus())) {
        // This is a candidate parent for restart
        nodesToRetry.add(node);
        continue;
      } else if (node.getStatus() == Status.RUNNING) {
        continue;
      } else if (node.getStatus() == Status.KILLING) {
        continue;
      }else if (node.getStatus() == Status.FAILED_RETRYING) {
        continue;
      } else if (node.getStatus() == Status.SKIPPED) {
        node.setStatus(Status.DISABLED);
        node.setEndTime(-1);
        node.setStartTime(-1);
        node.setUpdateTime(currentTime);
      } else if (node instanceof ExecutableFlowBase) {
        final ExecutableFlowBase base = (ExecutableFlowBase) node;
        switch (base.getStatus()) {
          case CANCELLED:
            node.setStatus(Status.READY);
            node.setEndTime(-1);
            node.setStartTime(-1);
            node.setUpdateTime(currentTime);
            // Break out of the switch. We'll reset the flow just like a normal
            // node
            break;
          case KILLED:
          case FAILED:
          case FAILED_FINISHING:
            resetFailedState(base, nodesToRetry);
            continue;
          case READY:
          case DISABLED:
            //继续找其inNodes
            break;
          default:
            // Continue the while loop. If the job is in a finished state that's
            // not
            // a failure, we don't want to reset the job.
            continue;
        }
      } else if (node.getStatus() == Status.CANCELLED) {
        // Not a flow, but killed
        node.setStatus(Status.READY);
        node.setStartTime(-1);
        node.setEndTime(-1);
        node.setUpdateTime(currentTime);
      } else if (node.getStatus() == Status.FAILED
              || node.getStatus() == Status.KILLED
              || node.getStatus().equals(Status.FAILED_WAITING)
              || node.getStatus().equals(Status.FAILED_SKIPPED)) {
        node.resetForRetry();
        nodesToRetry.add(node);
      }

      if (!(node instanceof ExecutableFlowBase)
              && node.getStatus() != oldStatus) {
        this.logger.info("Resetting job '" + node.getNestedId() + "' from "
                + oldStatus + " to " + node.getStatus());
      }

      for (final String inId : node.getInNodes()) {
        final ExecutableNode nodeUp = flow.getExecutableNode(inId);
        queue.add(nodeUp);
      }
    }

    // At this point, the following code will reset the flow
    final Status oldFlowState = flow.getStatus();
    if (maxStartTime == -1) {
      // Nothing has run inside the flow, so we assume the flow hasn't even
      // started running yet.
      flow.setStatus(Status.READY);
    } else {
      flow.setStatus(Status.RUNNING);

      // Add any READY start nodes. Usually it means the flow started, but the
      // start node has not.
      for (final String id : flow.getStartNodes()) {
        final ExecutableNode node = flow.getExecutableNode(id);
        if (node.getStatus() == Status.READY
                || node.getStatus() == Status.DISABLED) {
          nodesToRetry.add(node);
        }
      }
    }
    flow.setUpdateTime(System.currentTimeMillis());
    flow.setEndTime(-1);
    this.logger.info("Resetting flow '" + flow.getNestedId() + "' from "
            + oldFlowState + " to " + flow.getStatus());
  }


  private void interrupt() {
    // FIXME When the thread is not started, the job stream is terminated at this time, and a null pointer exception occurs. The solution is to increase non-empty judgment.
    if(this.flowRunnerThread != null) {
      this.flowRunnerThread.interrupt();
    }
  }

  public boolean isKilled() {
    return this.flowKilled;
  }

  public boolean isFlowFinished(){
    return this.flowFinished;
  }

  public ExecutableFlow getExecutableFlow() {
    return this.flow;
  }

  public File getFlowLogFile() {
    return this.logFile;
  }

  public File getJobLogFile(final String jobId, final int attempt) {
    final ExecutableNode node = this.flow.getExecutableNodePath(jobId);
    final File path = new File(this.execDir, node.getJobSource());

    final String logFileName = JobRunner.createLogFileName(node, attempt);
    final File logFile = new File(path.getParentFile(), logFileName);

    if (!logFile.exists()) {
      return null;
    }

    return logFile;
  }

  public File getJobAttachmentFile(final String jobId, final int attempt) {
    final ExecutableNode node = this.flow.getExecutableNodePath(jobId);
    final File path = new File(this.execDir, node.getJobSource());

    final String attachmentFileName =
            JobRunner.createAttachmentFileName(node, attempt);
    final File attachmentFile = new File(path.getParentFile(), attachmentFileName);
    if (!attachmentFile.exists()) {
      return null;
    }
    return attachmentFile;
  }

  public File getJobMetaDataFile(final String jobId, final int attempt) {
    final ExecutableNode node = this.flow.getExecutableNodePath(jobId);
    final File path = new File(this.execDir, node.getJobSource());

    final String metaDataFileName = JobRunner.createMetaDataFileName(node, attempt);
    final File metaDataFile = new File(path.getParentFile(), metaDataFileName);

    if (!metaDataFile.exists()) {
      return null;
    }

    return metaDataFile;
  }

  public boolean isRunnerThreadAlive() {
    if (this.flowRunnerThread != null) {
      return this.flowRunnerThread.isAlive();
    }
    return false;
  }

  public boolean isThreadPoolShutdown() {
    return this.executorService.isShutdown();
  }

  public int getNumRunningJobs() {
    return this.activeJobRunners.size();
  }

  public int getExecutionId() {
    return this.execId;
  }

  public Set<JobRunner> getActiveJobRunners() {
    return ImmutableSet.copyOf(this.activeJobRunners);
  }

  // Class helps report the flow start and stop events.
  private class FlowRunnerEventListener implements EventListener {

    public FlowRunnerEventListener() {
    }

    private synchronized Map<String, String> getFlowMetadata(final FlowRunner flowRunner) {
      final ExecutableFlow flow = flowRunner.getExecutableFlow();
      final Props props = ServiceProvider.SERVICE_PROVIDER.getInstance(Props.class);
      final Map<String, String> metaData = new HashMap<>();
      metaData.put("flowName", flow.getId());
      metaData.put("azkabanHost", props.getString(AZKABAN_SERVER_HOST_NAME, "unknown"));
      metaData.put("projectName", flow.getProjectName());
      metaData.put("submitUser", flow.getSubmitUser());
      metaData.put("executionId", String.valueOf(flow.getExecutionId()));
      metaData.put("startTime", String.valueOf(flow.getStartTime()));
      metaData.put("submitTime", String.valueOf(flow.getSubmitTime()));
      return metaData;
    }

    @Override
    public synchronized void handleEvent(final Event event) {
      if (event.getType() == EventType.FLOW_STARTED) {
        final FlowRunner flowRunner = (FlowRunner) event.getRunner();
        final ExecutableFlow flow = flowRunner.getExecutableFlow();
        FlowRunner.this.logger.info("Flow started: " + flow.getId());
        FlowRunner.this.azkabanEventReporter.report(event.getType(), getFlowMetadata(flowRunner));
      } else if (event.getType() == EventType.FLOW_FINISHED) {
        final FlowRunner flowRunner = (FlowRunner) event.getRunner();
        final ExecutableFlow flow = flowRunner.getExecutableFlow();
        FlowRunner.this.logger.info("Flow ended: " + flow.getId());
        final Map<String, String> flowMetadata = getFlowMetadata(flowRunner);
        flowMetadata.put("endTime", String.valueOf(flow.getEndTime()));
        flowMetadata.put("flowStatus", flow.getStatus().name());
        FlowRunner.this.azkabanEventReporter.report(event.getType(), flowMetadata);
      }
    }
  }

  private class JobRunnerEventListener implements EventListener {

    public JobRunnerEventListener() {
    }

    private synchronized Map<String, String> getJobMetadata(final JobRunner jobRunner) {
      final ExecutableNode node = jobRunner.getNode();
      final Props props = ServiceProvider.SERVICE_PROVIDER.getInstance(Props.class);
      final Map<String, String> metaData = new HashMap<>();
      metaData.put("jobId", node.getId());
      metaData.put("executionID", String.valueOf(node.getExecutableFlow().getExecutionId()));
      metaData.put("flowName", node.getExecutableFlow().getId());
      metaData.put("startTime", String.valueOf(node.getStartTime()));
      metaData.put("jobType", String.valueOf(node.getType()));
      metaData.put("azkabanHost", props.getString(AZKABAN_SERVER_HOST_NAME, "unknown"));
      metaData.put("jobProxyUser",
              jobRunner.getProps().getString(JobProperties.USER_TO_PROXY, null));
      return metaData;
    }

    @Override
    public synchronized void handleEvent(final Event event) {
      if (event.getType() == EventType.JOB_STATUS_CHANGED) {
        updateFlow();
      } else if (event.getType() == EventType.JOB_FINISHED) {
        final JobRunner jobRunner = (JobRunner) event.getRunner();
        final ExecutableNode node = jobRunner.getNode();
        // FIXME The task execution needs to update the database information.
        if (node.getLastStartTime() > 0) {
          node.setStartTime(node.getLastStartTime());
        }
        updateFlow();
        try {
          if(FlowRunner.this.executorServiceForCheckers!=null) {
            FlowRunner.this.logger.info("current active threads : " + FlowRunner.this.executorServiceForCheckers.getActiveCount());
          }
        } catch (Exception e){
          FlowRunner.this.logger.error("获取当前活跃的线程数失败:" + e);
        }
        final EventData eventData = event.getData();

        FlowRunner.this.logger.info("finished node: " + node.getNestedId());
        if (FlowRunner.this.azkabanEventReporter != null) {
          final Map<String, String> jobMetadata = getJobMetadata(jobRunner);
          jobMetadata.put("jobStatus", node.getStatus().name());
          jobMetadata.put("endTime", String.valueOf(node.getEndTime()));
          FlowRunner.this.azkabanEventReporter.report(event.getType(), jobMetadata);
        }
        final long seconds = (node.getEndTime() - node.getStartTime()) / 1000;
        synchronized (FlowRunner.this.mainSyncObj) {
          FlowRunner.this.logger.info("Job " + eventData.getNestedId() + " finished with status "
                  + eventData.getStatus() + " in " + seconds + " seconds");

          // Cancellation is handled in the main thread, but if the flow is
          // paused, the main thread is paused too.
          // This unpauses the flow for cancellation.
          if (FlowRunner.this.flowPaused && eventData.getStatus() == Status.FAILED
              && FlowRunner.this.failureAction == FailureAction.CANCEL_ALL) {
            FlowRunner.this.flowPaused = false;
          }
          FlowRunner.this.finishedNodes.add(node);
          FlowRunner.this.activeJobRunners.remove(jobRunner);
          // FIXME Added task processing for FAILED_WAITING status.
          failedWaitingJobHandle(node);
          setUserDefined(node);
          node.getParentFlow().setUpdateTime(System.currentTimeMillis());
          interrupt();
          fireEventListeners(event);
        }
        // FIXME Added sla alarms for job and sub-job streams.
        handelJobFinishAlter(jobRunner);
      } else if (event.getType() == EventType.JOB_STARTED) {
        final EventData eventData = event.getData();
        FlowRunner.this.logger.info("Job Started: " + eventData.getNestedId());
        final JobRunner jobRunner = (JobRunner) event.getRunner();
        if (FlowRunner.this.azkabanEventReporter != null) {
          FlowRunner.this.azkabanEventReporter.report(event.getType(), getJobMetadata(jobRunner));
        }
        // FIXME Added job and subflow execution timeout alarms.
        handleJobAndEmbeddedFlowExecTimeoutAlter(jobRunner, eventData);
      }
    }
  }

  private void setUserDefined(ExecutableNode node){
    if(node.getOutputProps() != null && node.getOutputProps().containsKey(USER_DEFINED_PARAM)) {
      FlowRunner.this.flow.setComment(node.getOutputProps().getString(USER_DEFINED_PARAM, ""));
    }
  }

  private void failedWaitingJobHandle(ExecutableNode node){
    try {
      if(node.getStatus().equals(Status.FAILED_WAITING) && FlowRunner.this.failureAction == FailureAction.FAILED_PAUSE){
        FlowRunner.this.isFailedPaused = true;
        FlowRunner.this.failedNodes.put(node.getNestedId(), node);
        if(!FlowRunner.this.isTriggerStarted){
          killFlowTrigger = new KillFlowTrigger(FlowRunner.this, FlowRunner.this.logger);
          killFlowTrigger.start();
          FlowRunner.this.isTriggerStarted = true;
        }
        ExecutionControllerUtils.handleFlowPausedAlert(FlowRunner.this.flow, alerterHolder, node.getNestedId());
      } else if(Status.isStatusSucceeded(node.getStatus()) && FlowRunner.this.failureAction == FailureAction.FAILED_PAUSE){
        FlowRunner.this.failedNodes.remove(node.getNestedId());
      }
    }catch (RuntimeException re){
      FlowRunner.this.logger.error("add failed node failed", re);
    }
  }

  /**
   * job or subflow execute timeout alarm.
   * @param jobRunner
   * @param eventData
   */
  private void  handleJobAndEmbeddedFlowExecTimeoutAlter(final JobRunner jobRunner, final EventData eventData){
    // add job level checker
    final TriggerManager triggerManager = ServiceProvider.SERVICE_PROVIDER
            .getInstance(TriggerManager.class);

    // 获取非子工作流的job超时告警job名
    List<String> jobAlerts = SlaOption.getJobLevelSLAOptions(FlowRunner.this.flow).stream()
            .map(x -> (String)x.getInfo().get("JobName")).collect(Collectors.toList());
    String parentFlow = jobRunner.getNode().getParentFlow().getNestedId();
    FlowRunner.this.logger.info("alert jobs is " + jobAlerts.toString() + " job name: " + eventData.getNestedId() + " parent flow " + parentFlow);
    // job超时告警
    if(jobAlerts.contains(eventData.getNestedId())) {
      triggerManager.addTrigger(FlowRunner.this.flow.getExecutionId(), SlaOption.getJobLevelSLAOptions(FlowRunner.this.flow), eventData.getNestedId());
    }
    // 获取子flow
    List<String> embeddedFlowAlerts = SlaOption.getJobLevelSLAOptions(FlowRunner.this.flow).stream()
            .filter(x -> x.getInfo().getOrDefault(SlaOption.INFO_EMBEDDED_ID, null) != null)
            .map(x -> (String)x.getInfo().get("JobName")).collect(Collectors.toList());
    // 子flow超时告警
    for(String embeddedFlow: embeddedFlowAlerts){
      if(parentFlow.equals(embeddedFlow) || parentFlow.startsWith(embeddedFlow + ":")){
        if(embeddedFlowTimeOutSlaFlag.get(embeddedFlow) == null) {
          triggerManager.addTrigger(FlowRunner.this.flow.getExecutionId(), SlaOption.getJobLevelSLAOptions(FlowRunner.this.flow), embeddedFlow);
          embeddedFlowTimeOutSlaFlag.put(embeddedFlow, true);
        }
      }
    }
  }

  /**
   * subflow sla alarm
   * @param mailAlerter
   * @param slaJobOptionsList
   */
  private void embeddedFlowAlter(Alerter mailAlerter, ExecutableNode node, List<SlaOption> slaJobOptionsList){

    // 过滤出所有子flow类型的告警
    List<SlaOption> embeddedFlowSlaOptions = slaJobOptionsList.stream()
            .filter(x -> x.getInfo().getOrDefault(SlaOption.INFO_EMBEDDED_ID, null) != null)
            .collect(Collectors.toList());

    String parentFlow = node.getParentFlow().getNestedId();
    try {
      for(SlaOption sla: embeddedFlowSlaOptions){
        // embeddedFlowSla : parentFlow : subflow:subflow2 flow status: RUNNINGnode status : SUCCEEDED parent : 62 sla jobname: subflow:subflow2
        String slaJobName = sla.getInfo().get(SlaOption.INFO_JOB_NAME).toString();
        logger.info("embeddedFlowSla , parentFlow : " + parentFlow + " flow status: "
                + node.getParentFlow().getStatus() + " node status : " + node.getStatus()
                + " parent : " + node.getParentFlow().getFlowId()
                + " sla jobname: " + slaJobName
                + " this node name : " + node.getId()
                + " emb " + sla.getInfo().get(SlaOption.INFO_EMBEDDED_ID)
                + " nestedId : " + node.getNestedId());
        // 只要上一级flow是成功或者失败即可发成功，失败，完成告警
        if(node.getId().equals(sla.getInfo().get(SlaOption.INFO_EMBEDDED_ID))
                && Status.isSucceeded(node.getStatus())
                && parentFlow.equals(slaJobName)
                && (SlaOption.TYPE_JOB_SUCCESS_EMAILS.equals(sla.getType()) || SlaOption.TYPE_JOB_FINISH_EMAILS.equals(sla.getType()))){
          logger.info("embeddedFlowSla , " +  1);
          if(embeddedFlowSlaFlag.getOrDefault(slaJobName + "-" + sla.getType(), null) == null){
            logger.info("embeddedFlowSla , " +  2);
            // FIXME Job flow event alerts, relying on third-party services.
            mailAlerter.alertOnFinishSla(sla, this.flow);
            // 标记已发送过告警
            embeddedFlowSlaFlag.put(slaJobName + "-" + sla.getType(), true);
          }
        }
        // 子flow中的某个job失败 需要立刻发出其对应的子flow和上上级子flow的失败或者完成告警
        if(node.getStatus() == Status.FAILED || node.getStatus() == Status.FAILED_WAITING
                && (SlaOption.TYPE_JOB_FAILURE_EMAILS.equals(sla.getType()) || SlaOption.TYPE_JOB_FINISH_EMAILS.equals(sla.getType()))){
          logger.info("embeddedFlowSla , " +  3);
          // a:b:c 包含 a:b ，规避abc:b:c 包含 ab，
          if(parentFlow.equals(slaJobName) || parentFlow.startsWith(slaJobName + ":")){
            logger.info("embeddedFlowSla , " +  4);
            if(embeddedFlowSlaFlag.getOrDefault(slaJobName + "-" + sla.getType(), null) == null) {
              logger.info("embeddedFlowSla , " +  5);
              // FIXME Job flow event alerts, relying on third-party services.
              mailAlerter.alertOnFinishSla(sla, this.flow);
              embeddedFlowSlaFlag.put(slaJobName + "-" + sla.getType(), true);
            }
          }
        }
      }
    } catch (Exception e){
      logger.error("发送子flow告警失败: ", e);
    }
  }

  /**
   *  Job execution completion alarm.
   * @param runner
   */
  private void handelJobFinishAlter(JobRunner runner){
    final ExecutableNode node = runner.getNode();
    logger.info("SLA 定时任务告警处理开始,当前节点状态为 {} "+node.getStatus());


    Alerter mailAlerter = ServiceProvider.SERVICE_PROVIDER.getInstance(AlerterHolder.class).get("email") == null?
        ServiceProvider.SERVICE_PROVIDER.getInstance(AlerterHolder.class).get("default"):
        ServiceProvider.SERVICE_PROVIDER.getInstance(AlerterHolder.class).get("email");
    if(mailAlerter == null){
      logger.warn("找不到告警插件.");
      return;
    }

    ExecutableFlow exflow = runner.getNode().getExecutableFlow();
    List<SlaOption> slaOptionList = exflow.getSlaOptions();
    // 所有job类型告警列表
    List<String> jobAlterTypes = Arrays.asList(SlaOption.TYPE_JOB_SUCCESS_EMAILS,
            SlaOption.TYPE_JOB_FAILURE_EMAILS, SlaOption.TYPE_JOB_FINISH_EMAILS);
    // 过滤出所有job类型的告警
    List<SlaOption> slaJobOptionsList = slaOptionList.stream()
            .filter(x -> jobAlterTypes.contains(x.getType())).collect(Collectors.toList());

    // 子flow告警处理
    embeddedFlowAlter(mailAlerter, node, slaJobOptionsList);

    // 从job告警列表中获取所有告警的job的名字去重
    Set<String> slaJobAlterName = slaJobOptionsList.stream()
            .map(x -> (String)x.getInfo().get(SlaOption.INFO_JOB_NAME)).collect(Collectors.toSet());
    // 判断当前job是否需要告警
    if(!slaJobAlterName.contains(node.getNestedId())){
      return;
    }
    if (null != slaJobOptionsList) {
      try {
        for (SlaOption slaOption : slaJobOptionsList) {
          // 判断当前告警信息的job名是否与等于该job名字
          if(!((String)slaOption.getInfo().get(SlaOption.INFO_JOB_NAME)).equals(node.getNestedId())){
            continue;
          }
          logger.info("1.任务Job: " + node.getNestedId() + " 开始发送 告警" + " job status is " + node.getStatus());
          if (SlaOption.TYPE_JOB_FAILURE_EMAILS.equals(slaOption.getType())
                  && (node.getStatus().equals(Status.FAILED) || node.getStatus().equals(Status.FAILED_WAITING))) {
            logger.info("任务Job 执行失败 开始发送 告警");
            // FIXME Job flow event alerts, relying on third-party services.
            mailAlerter.alertOnFinishSla(slaOption, flow);
          } else if (SlaOption.TYPE_JOB_SUCCESS_EMAILS.equals(slaOption.getType()) && Status.isSucceeded(node.getStatus())) {
            logger.info("任务Job 执行成功 开始发送 告警");
            // FIXME Job flow event alerts, relying on third-party services.
            mailAlerter.alertOnFinishSla(slaOption, flow);
          } else if (SlaOption.TYPE_JOB_FINISH_EMAILS.equals(slaOption.getType())) {
            logger.info("任务Job 执行完成 开始发送 告警");
            // FIXME Job flow event alerts, relying on third-party services.
            mailAlerter.alertOnFinishSla(slaOption, flow);
          }
        }
      } catch (Exception e) {
        logger.error("发送job告警失败", e);
      }
    }
  }

  /**
   * 按照优先级执行Job
   * @param node
   * @return
   * @throws IOException
   */
  private boolean runReadyJobByPriority(final ExecutableNode node,final String preNodeId) throws IOException {
    if (Status.isStatusFinished(node.getStatus())
            || Status.isStatusRunning(node.getStatus())) {
      return false;
    }

    final Status nextNodeStatus = getImpliedStatus(node, this.flow.getOtherOption());
    if (nextNodeStatus == null) {
      return false;
    }

    if (nextNodeStatus == Status.CANCELLED) {
      this.logger.info("Cancelling '" + node.getNestedId()
              + "' due to prior errors.");
      node.cancelNode(System.currentTimeMillis());
      finishExecutableNode(node);
    } else if (nextNodeStatus == Status.SKIPPED) {
      this.logger.info("Skipping disabled job '" + node.getId() + "'.");
      node.skipNode(System.currentTimeMillis());
      finishExecutableNode(node);
    } else if (nextNodeStatus == Status.READY) {
      if (node instanceof ExecutableFlowBase) {
        final ExecutableFlowBase flow = ((ExecutableFlowBase) node);
        this.logger.info("Running flow '" + flow.getNestedId() + "'.");
        flow.setStatus(Status.RUNNING);
        flow.setStartTime(System.currentTimeMillis());
        prepareJobProperties(flow);

        for (final String startNodeId : ((ExecutableFlowBase) node).getStartNodes()) {
          final ExecutableNode startNode = flow.getExecutableNode(startNodeId);
          runReadyJob(startNode);
        }
      } else {
        logger.info("等待上一个优先级job " + preNodeId + " 执行完毕");
        logger.info("当前等待执行job " + node.getId() + "");
        runExecutableNodeByPriority(node);
      }
    }
    return true;
  }

  @SuppressWarnings("FutureReturnValueIgnored")
  private void runExecutableNodeByPriority(final ExecutableNode node) throws IOException {
    // Collect output props from the job's dependencies.
    prepareJobProperties(node);

    node.setStatus(Status.QUEUED);
    final JobRunner runner = createJobRunner(node);
    this.logger.info("Submitting job '" + node.getNestedId() + "' to run.");
    try {

      this.executorPriorityService.submit(runner);

      this.activeJobRunners.add(runner);

    } catch (final RejectedExecutionException e) {
      this.logger.error("", e);
    }
  }

  private boolean isSkippedDay(ExecutableNode node){
    Map<String, String> jobCronExpression = ((Map<String, String>) this.flow.getOtherOption().get("job.cron.expression"));
    if(!(node instanceof ExecutableFlowBase) && jobCronExpression != null) {
      String cronExpresion = jobCronExpression.get(node.getNestedId());
      if(StringUtils.isNotBlank(cronExpresion)){
        if(this.flow.getSubmitTime() != -1 && Utils.checkDateTime(this.flow.getSubmitTime(),1, cronExpresion)){
          logger.info("job: " + node.getNestedId() + ", cron:" + cronExpresion + ", skiped.");
          return true;
        }
      }
    }
    return false;
  }


  /**
   * Overload the original function of judging the execution status of the parent node.
   * You can control whether to skip the wrong execution node through parameters.
   *
   *
   */
  public Status getImpliedStatus(final ExecutableNode node, final Map<String, Object> faultOption) {
    // If it's running or finished with 'SUCCEEDED', than don't even
    // bother starting this job.
    if (Status.isStatusRunning(node.getStatus())
            || Status.isSucceeded(node.getStatus())) {
      return null;
    }

    // Go through the node's dependencies. If all of the previous job's
    // statuses is finished and not FAILED or KILLED, than we can safely
    // run this job.
    Status status = Status.READY;

    // Go through the node's dependencies. If all of the previous job's
    // statuses is finished and not FAILED or KILLED, than we can safely
    // run this job.
    final ExecutableFlowBase flow = node.getParentFlow();
    boolean shouldKill = false;
    boolean jobSkipFailedOptions = false;
    // FIXME When the execution of a dependent job fails and the current job to be executed is disabled, it will cause the job stream to fail and retry. The task cannot be killed, and the task is always in the killing state. The current solution is that if the execution of the dependent job fails, the entire execution link All jobs are canceled, and the status of the disabled job is skipped after execution.
    if(node.getCondition() == null && node.getConditionOnJobStatus().equals(ConditionOnJobStatus.ALL_SUCCESS)) {
      for (final String dependency : node.getInNodes()) {
        final ExecutableNode dependencyNode = flow.getExecutableNode(dependency);
        final Status depStatus = dependencyNode.getStatus();
        logger.info("depNode:" + dependencyNode.getNestedId() + " status: " + depStatus.toString() + " isDependentlinkFailed: " + dependencyNode.isDependentlinkFailed());
        if (!Status.isStatusFinished(depStatus)) {
          return null;
        } else if (depStatus == Status.CANCELLED
                || depStatus == Status.KILLED) {
          // We propagate failures as KILLED states.
          shouldKill = true;
          setDependentlinkFailed(node, true);
        } else if (depStatus == Status.FAILED) {
          setDependentlinkFailed(node, true);
          // 设置执行跳过某个job
          List<String> skipFaultJobList = (ArrayList) faultOption.get("jobSkipFailedOptions");
          if (null != skipFaultJobList && (skipFaultJobList.contains(dependencyNode.getNestedId()) || skipFaultJobList.contains(dependencyNode.getId()))) {
            logger.info("用户已设置错误跳过策略，跳过错误状态 Job:" + dependencyNode.getNestedId() + " 继续执行。");
            jobSkipFailedOptions = true;
          } else {
            shouldKill = true;
          }
        }
        if(dependencyNode.isDependentlinkFailed()){
          shouldKill = true;
          setDependentlinkFailed(node, true);
        }
      }
    }

    if(!jobSkipFailedOptions) {
      // Check if condition on job status is satisfied 条件执行宏判断
      switch (checkConditionOnJobStatus(node)) {
        case FAILED:
          this.logger.info("Condition on job status: " + node.getConditionOnJobStatus() + " is "
                  + "evaluated to false for " + node.getId());
          status = Status.CANCELLED;
          break;
        // Condition not satisfied yet, need to wait
        case PENDING:
          return null;
        default:
          break;
      }

      // 条件执行表达式计算
      if (status != Status.CANCELLED && !isConditionOnRuntimeVariableMet(node)) {
        status = Status.CANCELLED;
      }
    }

    // If it's disabled but ready to run, we want to make sure it continues
    // being disabled.
    //FIXME isSkippedDay means skipping tasks by time. If the current execution time is equal to the time set by the job to skip execution, the task skips execution.
    if (node.getStatus() == Status.DISABLED
        || node.getStatus() == Status.SKIPPED || isSkippedDay(node)) {
      logger.info("set job: " + node.getNestedId() + " skipped");
      return Status.SKIPPED;
    }

    // If the flow has failed, and we want to finish only the currently running
    // jobs, we just
    // kill everything else. We also kill, if the flow has been cancelled.
    if (this.flowFailed
            && this.failureAction == FailureAction.FINISH_CURRENTLY_RUNNING) {
      return Status.CANCELLED;
    } else if (shouldKill || isKilled()) {
      logger.info("this flow has been killed or dependent link has been failed, this job:" + node.getNestedId() + " will be canceled");
      return Status.CANCELLED;
    }


    // 所有检查通过，设置准备状态
    return status;
  }
  //检查当前node是否设置了错误跳过
  private boolean nodeSkipFailedCheck(final ExecutableNode node) {
    if (node instanceof ExecutableFlowBase) {
      return false;
    }
    //获取用户设置的错误跳过job列表
    List<String> skipFaultJobList = (ArrayList)this.flow.getOtherOption().get("jobSkipFailedOptions");
    if(null != skipFaultJobList && (skipFaultJobList.contains(node.getNestedId()) || skipFaultJobList.contains(node.getId()))){
      return false;
    }else{
      return true;
    }

  }

  final private static Pattern pattern = Pattern.compile("[1-5]*");
  private boolean verifyPriority(final String priorityLev){
    //校验通过
    if(pattern.matcher(priorityLev).matches()){
      return false;
    }
    return true;
  }



}
