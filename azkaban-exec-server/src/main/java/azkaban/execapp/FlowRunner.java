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

import static azkaban.Constants.ConfigurationKeys.WTSS_SUBFLOW_JOB_CONSUMER_THREAD_SLEEP_TIME;
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
import azkaban.batch.HoldBatchContext;
import azkaban.event.Event;
import azkaban.event.EventData;
import azkaban.event.EventHandler;
import azkaban.event.EventListener;
import azkaban.execapp.event.AbstractFlowWatcher;
import azkaban.execapp.event.JobCallbackManager;
import azkaban.execapp.jmx.JmxJobMBeanManager;
import azkaban.execapp.listener.FlowRunnerEventListener;
import azkaban.execapp.listener.JobRunnerEventListener;
import azkaban.execapp.metric.NumFailedJobMetric;
import azkaban.execapp.metric.NumRunningJobMetric;
import azkaban.executor.AlerterHolder;
import azkaban.executor.ConnectorParams;
import azkaban.executor.DmsBusPath;
import azkaban.executor.ExecutableFlow;
import azkaban.executor.ExecutableFlowBase;
import azkaban.executor.ExecutableNode;
import azkaban.executor.ExecutionControllerUtils;
import azkaban.executor.ExecutionCycle;
import azkaban.executor.ExecutionOptions;
import azkaban.executor.ExecutionOptions.FailureAction;
import azkaban.executor.ExecutorLoader;
import azkaban.executor.ExecutorManagerException;
import azkaban.executor.Status;
import azkaban.flow.ConditionOnJobStatus;
import azkaban.flow.FlowProps;
import azkaban.flow.FlowUtils;
import azkaban.jobExecutor.ProcessJob;
import azkaban.jobExecutor.utils.SystemBuiltInParamReplacer;
import azkaban.jobhook.JobHookManager;
import azkaban.jobtype.JobTypeManager;
import azkaban.metric.MetricReportManager;
import azkaban.project.FlowLoaderUtils;
import azkaban.project.Project;
import azkaban.project.ProjectLoader;
import azkaban.project.ProjectManagerException;
import azkaban.project.entity.FlowBusiness;
import azkaban.sla.SlaOption;
import azkaban.spi.AzkabanEventReporter;
import azkaban.spi.EventType;
import azkaban.utils.FileIOUtils;
import azkaban.utils.HttpUtils;
import azkaban.utils.LogUtils;
import azkaban.utils.Props;
import azkaban.utils.PropsUtils;
import azkaban.utils.SwapQueue;
import azkaban.utils.Utils;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.io.Files;
import com.google.common.util.concurrent.ThreadFactoryBuilder;
import com.google.gson.JsonObject;
import java.io.File;
import java.io.IOException;
import java.security.AccessControlContext;
import java.security.AccessController;
import java.security.PrivilegedExceptionAction;
import java.security.ProtectionDomain;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.ConcurrentModificationException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import javax.script.ScriptEngine;
import javax.script.ScriptEngineManager;
import javax.script.ScriptException;
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.collections.MapUtils;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang.StringUtils;
import org.jetbrains.annotations.NotNull;
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
  private final JobHookManager jobhookManager;
  private final ExecutorLoader executorLoader;
  private final ProjectLoader projectLoader;
  private final int execId;
  private final File execDir;
  private final ExecutionOptions.FailureAction failureAction;
  // Properties map
  private final Props azkabanProps;
  private final Map<String, Props> sharedProps = new HashMap<>();
  private final JobRunnerEventListener listener = new JobRunnerEventListener(this);
  private final FlowRunnerEventListener flowListener = new FlowRunnerEventListener();
  private final Set<JobRunner> activeJobRunners = Collections
          .newSetFromMap(new ConcurrentHashMap<JobRunner, Boolean>());
  // Thread safe swap queue for finishedExecutions.
  private final SwapQueue<ExecutableNode> finishedNodes;
  private final AzkabanEventReporter azkabanEventReporter;
  private final AlerterHolder alerterHolder;
  private org.slf4j.Logger logger;
  private File logFile;

  private Thread flowRunnerThread;
  private int numJobThreads = 10;

  private int signJobThreads = 5;
  // Used for pipelining
  private Integer pipelineLevel = null;
  private Integer pipelineExecId = null;
  // Watches external flows for execution.
  private AbstractFlowWatcher watcher = null;
  private Set<String> proxyUsers = null;
  private boolean validateUserProxy;
  private String jobLogFileSize = "5MB";
  private int jobLogNumFiles = 8;
  private volatile boolean flowPaused = false;
  private volatile boolean flowFailed = false;
  private volatile boolean flowFinished = false;
  private volatile boolean flowKilled = false;


  private final ConcurrentHashMap<String, ExecutableNode> failedNodes;

  /**
   * 默认任务线程池
   */
  private Lock commonCMDLock = new ReentrantLock();
  private ExecutorService executorService;

  /**
   * check 任务线程池
   */
  private Lock checkLock = new ReentrantLock();
  private ThreadPoolExecutor executorServiceForCheckers;

  /**
   * 信号任务线程池
   */
  private Lock signReceiveLock = new ReentrantLock();
  private volatile ThreadPoolExecutor executorServiceForSign;
  /**
   * Linkis 任务执行线程池
   */
  private Lock linkisLock = new ReentrantLock();
  private ThreadPoolExecutor executorServiceForLinkis;

  /**
   * 优先级任务线程池，单线程
   */
  private ExecutorService executorPriorityService;
  // Used to control task timeout termination.


  /**
   * async kill pool
   */
  private Lock asyncPoolLocker = new ReentrantLock();
  private ThreadPoolExecutor asyncKillPool;

  private volatile boolean isTriggerStarted = false;

  private KillFlowTrigger killFlowTrigger;

  private volatile ConcurrentHashMap embeddedFlowSlaFlag = new ConcurrentHashMap<String, Boolean>();

  private volatile ConcurrentHashMap embeddedFlowTimeOutSlaFlag = new ConcurrentHashMap<String, Boolean>();

  // The following is state that will trigger a retry of all failed jobs
  private volatile boolean isRetryFailedJobs = false;


  private EventListener cycleFlowRunnerEventListener;

  private long pausedStartTime;

  private long maxPausedTime;

  private volatile boolean isFailedPaused = false;

  private String loggerName;
  private String logFileName;

  private final Set<String> failWaitingSet = Collections
          .newSetFromMap(new ConcurrentHashMap<String, Boolean>());

  private FlowBusiness flowBusiness;

  public static ConcurrentHashMap<Integer, ConcurrentHashMap<Integer, AtomicInteger>> runningNoCheckerSumMap = new ConcurrentHashMap<>();

  private boolean holdBatchSwitch;

  /**
   * put all retry jobs with dependence
   */
  private ConcurrentHashMap<String, Set<String>> retryJobs = new ConcurrentHashMap<>();

  private HoldBatchContext holdBatchContext;

  private String lastBatchId;

  SystemBuiltInParamReplacer sbipu;

  private boolean asyncKillJobEnabled;

  /**
   * 子工作流任务并发限制
   */
  private HashMap<String, Integer> subflowNumJobLimit;

  /**
   * 子工作流任务计数
   */
  private ConcurrentHashMap<String, AtomicInteger> subflowJobCount;

  private ConcurrentHashMap<String, BlockingQueue<JobRunner>> subflowJobRunnerQueue;

  private Thread subflowJobConsumerThread;

  private volatile boolean isSubflowJobConsumerThreadAlive = true;

  private long subflowJobConsumerThreadSleepTimeInMs = 0;


  /**
   * Constructor. This will create its own ExecutorService for thread pools
   */
  public FlowRunner(final ExecutableFlow flow, final ExecutorLoader executorLoader,
                    final ProjectLoader projectLoader, final JobTypeManager jobtypeManager,
                    final JobHookManager jobhookManager, final Props azkabanProps,
                    final AzkabanEventReporter azkabanEventReporter, final AlerterHolder alerterHolder)
          throws ExecutorManagerException {
    this(flow, executorLoader, projectLoader, jobtypeManager, jobhookManager, null, azkabanProps,
            azkabanEventReporter, alerterHolder);
  }

  /**
   * Constructor. If executorService is null, then it will create it's own for thread pools.
   */
  public FlowRunner(final ExecutableFlow flow, final ExecutorLoader executorLoader,
                    final ProjectLoader projectLoader, final JobTypeManager jobtypeManager,
                    final JobHookManager jobhookManager, final ExecutorService executorService, final Props azkabanProps,
                    final AzkabanEventReporter azkabanEventReporter, final AlerterHolder alerterHolder)
          throws ExecutorManagerException {
    this.execId = flow.getExecutionId();
    this.flow = flow;
    this.executorLoader = executorLoader;
    this.projectLoader = projectLoader;
    this.execDir = new File(flow.getExecutionPath());
    this.jobtypeManager = jobtypeManager;
    this.jobhookManager = jobhookManager;
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
    createLogger(this.flow.getFlowId(), this.flow.getProjectName());
    this.azkabanEventReporter = azkabanEventReporter;

    if (FlowRunner.runningNoCheckerSumMap.get(this.flow.getProjectId()) == null) {
      ConcurrentHashMap<Integer, AtomicInteger> execRunningMap = new ConcurrentHashMap<>();
      execRunningMap.putIfAbsent(this.execId, new AtomicInteger(0));
      FlowRunner.runningNoCheckerSumMap.put(this.flow.getProjectId(), execRunningMap);
    } else {
      FlowRunner.runningNoCheckerSumMap.get(this.flow.getProjectId())
              .put(this.execId, new AtomicInteger(0));
    }
    this.holdBatchContext = ServiceProvider.SERVICE_PROVIDER.getInstance(HoldBatchContext.class);
    this.holdBatchSwitch = this.azkabanProps.getBoolean("azkaban.holdbatch.switch", false);
    this.asyncKillJobEnabled = this.azkabanProps.getBoolean(JobProperties.FLOW_ASYNC_KILL_ENABLE, true);
    this.lastBatchId = (String) this.flow.getOtherOption().get("lastBatchId");
    this.sbipu = new SystemBuiltInParamReplacer(this.projectLoader);

    this.subflowNumJobLimit = new HashMap<>();
    this.subflowJobCount = new ConcurrentHashMap<>();
    this.subflowJobRunnerQueue = new ConcurrentHashMap<>();

    this.subflowJobConsumerThreadSleepTimeInMs = this.azkabanProps.getLong(
            WTSS_SUBFLOW_JOB_CONSUMER_THREAD_SLEEP_TIME, 60000);
  }

  public EventListener getCycleFlowRunnerEventListener() {
    return this.cycleFlowRunnerEventListener;
  }

  public void setCycleFlowRunnerEventListener(EventListener cycleFlowRunnerEventListener) {
    this.cycleFlowRunnerEventListener = cycleFlowRunnerEventListener;
  }

  public FlowRunner setFlowWatcher(final AbstractFlowWatcher watcher) {
    this.watcher = watcher;
    return this;
  }

  public FlowRunner setNumJobThreads(final int jobs) {
    this.numJobThreads = jobs;
    return this;
  }

  public FlowRunner setSignJobThreads(final int signJobThreads) {
    this.signJobThreads = signJobThreads;
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

  public String getLastBatchId() {
    return this.lastBatchId;
  }

  public File getExecutionDir() {
    return this.execDir;
  }

  public org.slf4j.Logger getFlowRunnerLogger() {
    return this.logger;
  }

  public void printThreadInfo() {
    if (null == this.logger) {
      return;
    }
    try {
      if (this.executorServiceForCheckers != null) {
        this.logger.info("current active Checker threads : {}"
                , this.executorServiceForCheckers.getActiveCount());
      }
    } catch (Exception e) {
      this.logger.error("获取当前活跃的 Checker 线程数失败:", e);
    }

    try {
      if (this.executorServiceForLinkis != null) {
        this.logger.info("current active Linkis threads : {}"
                , this.executorServiceForLinkis.getActiveCount());
      }
    } catch (Exception e) {
      this.logger.error("获取当前活跃的 Linkis 线程数失败:", e);
    }

    try {
      if (this.executorServiceForSign != null) {
        this.logger.info("current active Sign threads :  {}"
                , this.executorServiceForSign.getActiveCount());
      }
    } catch (Exception e) {
      this.logger.error("获取当前活跃的 Sign 线程数失败:", e);
    }
  }

  private void assembleSubflowJobNumLimit() {
    ExecutionOptions options = flow.getExecutionOptions();
    Map<String, String> flowParams = options.getFlowParameters();
    String start = "flow.";
    String end = ".num.job.threads";
    for (String key : flowParams.keySet()) {
      if ("flow.num.job.threads".equals(key)) {
        continue;
      }
      if (key.contains(start) && key.contains(end)) {
        //     存在子工作流并发控制
        int startIndex = key.indexOf(start);
        startIndex += start.length();
        int endIndex = key.indexOf(end, startIndex);
        String subflowId = key.substring(startIndex, endIndex);
        int subflowJobNumLimit = Integer.parseInt(flowParams.get(key));
        if (subflowJobNumLimit > 0 && subflowJobNumLimit < this.numJobThreads) {
          subflowNumJobLimit.put(subflowId, subflowJobNumLimit);
          subflowJobCount.put(subflowId, new AtomicInteger(0));
          subflowJobRunnerQueue.put(subflowId, new LinkedBlockingDeque<>());
        }
      }
    }
    if (!subflowNumJobLimit.isEmpty()) {
      subflowNumJobLimit.forEach(
              (key, value) -> this.logger.info("subflow {} job limit {}", key, value));

      this.subflowJobConsumerThread = new Thread(new SubflowJobConsumerThread(),
              "FlowRunner-SubflowJobConsumerThread-" + this.execId);

      this.subflowJobConsumerThread.start();
    }
  }

  /**
   * 定义一个线程，进行 queue 的交互，如果队列里面有任务则判断是否满足限制条件，如果满足则取出执行
   */
  class SubflowJobConsumerThread implements Runnable {

    @Override
    public void run() {

      while (isSubflowJobConsumerThreadAlive) {

        try {
          subflowJobRunnerQueue.forEach((subflowId, jobRunnerQueue) -> {

            if (!jobRunnerQueue.isEmpty()) {
              int currentCount = subflowJobCount.get(subflowId).get();
              Integer limit = subflowNumJobLimit.get(subflowId);
              logger.info(
                      "subflow {} has unfinished JobRunner, size {}, current count {}, "
                              + "limit {} ", subflowId, jobRunnerQueue.size(), currentCount,
                      limit);
              if (currentCount < limit) {
                int availableSize = limit - currentCount;
                for (int count = 0; count < availableSize; count++) {
                  JobRunner runner = jobRunnerQueue.poll();
                  if (runner != null) {
                    logger.info("JobRunner for {} is executing ... ",
                            runner.getJobId());
                    logger.info("active Common thread size: {}/{}",
                            ((ThreadPoolExecutor) executorService).getActiveCount(),
                            ((ThreadPoolExecutor) executorService).getMaximumPoolSize());
                    executorService.execute(runner);
                    activeJobRunners.add(runner);
                  }
                }
              }
            }
          });

          Thread.sleep(subflowJobConsumerThreadSleepTimeInMs);
        } catch (Exception e) {
          logger.warn("Exception in SubflowJobConsumerThread for flow {}, execId {}: ",
                  flow.getFlowId(), flow.getExecutionId(), e);
        }
      }
      subflowJobRunnerQueue.clear();
      subflowJobCount.clear();
      subflowNumJobLimit.clear();
    }
  }

  @VisibleForTesting
  AlerterHolder getAlerterHolder() {
    return this.alerterHolder;
  }


  private void createThreadPool() {
    if (this.executorPriorityService == null) {
      logger.info("create executorPriorityService. execId:" + execId);
      ThreadFactory threadFactory3 = new ThreadFactoryBuilder().setDaemon(true).build();
      this.executorPriorityService = Executors.newSingleThreadExecutor(threadFactory3);
    }
  }

  public void shutdownThreadPoolNow() {
    if (this.executorService != null) {
      logger.info("shutdown executorService");
      try {
        this.executorService.shutdownNow();
      } catch (Exception e) {
        logger.warn("Failed to shutdown executorService.", e);
      }
    }
    if (this.executorServiceForCheckers != null) {
      try {
        logger.info("shutdown executorServiceForCheckers. current active threads: {}", this.executorServiceForCheckers.getActiveCount());
        this.executorServiceForCheckers.shutdownNow();
      } catch (Exception e) {
        logger.warn("Failed to shutdown executorServiceForCheckers.", e);
      }
    }
    if (this.executorPriorityService != null) {
      logger.info("shutdown executorPriorityService.");
      try {
        this.executorPriorityService.shutdownNow();
      } catch (Exception e) {
        logger.warn("Failed to shutdown executorPriorityService.", e);
      }
    }

    if (this.executorServiceForLinkis != null) {
      try {
        logger.info("shutdown executorServiceForLinkis. current active threads: {}", this.executorServiceForLinkis.getActiveCount());
        this.executorServiceForLinkis.shutdownNow();
      } catch (Exception e) {
        logger.warn("Failed to shutdown executorServiceForLinkis.", e);
      }
    }

    if (this.executorServiceForSign != null) {
      try {
        logger.info("shutdown executorServiceForSign. current active threads: {}", this.executorServiceForSign.getActiveCount());
        this.executorServiceForSign.shutdownNow();
      } catch (Exception e) {
        logger.warn("Failed to shutdown executorServiceForSign.", e);
      }
    }
  }

  private void initNSWTSSValue() {
    Props flowProp = this.sharedProps.get(this.flow.getExecutableNode(((ExecutableFlowBase) flow).getStartNodes().get(0)).getPropsSource());
    String flowParamNsWtss = this.flow.getExecutionOptions().getFlowParameters().getOrDefault("ns_wtss", null);
    String flowPropNswtss = flowProp == null ? null : flowProp.getString("ns_wtss", null);
    if (flowParamNsWtss != null && "false".equals(flowParamNsWtss)) {
      this.flow.setNsWtss(false);
    } else if (flowPropNswtss != null && "false".equals(flowPropNswtss)) {
      this.flow.setNsWtss(false);
    }
    logger.info("nsWtss: " + this.flow.getNsWtss() + ", flowParamNsWtss: " + flowParamNsWtss + ", flowPropNswtss:" + flowPropNswtss);
  }

  /**
   * rundate替换
   */
  private void runDateReplace() throws ExecutorManagerException {
    //获取执行Flow节点
    ExecutableFlow ef = this.flow;
    if (null == ef.getParentFlow()) {
      sbipu.run(this.execDir.getPath(), ef);
    }
  }

  @Override
  public void run() {
    try {
      Thread.currentThread().setName("FlowRunner-exec-" + this.flow.getExecutionId());
      if (this.holdBatchSwitch) {
        synchronized (this.mainSyncObj) {
          if (Status.SYSTEM_PAUSED.equals(this.flow.getStatus())) {
            this.flow.getOtherOption().put("isHoldingSubmit", true);
            logger.info("flow {} status: {} -> {}", this.flow.getPrintableId(":"),
                    this.flow.getStatus(), Status.PREPARING);
            this.flow.setStatus(Status.PREPARING);
            updateFlow();
          }
        }
      }

      // FIXME Create a thread pool and add a thread pool (executorServiceForCheckers) for running checker tasks.
      Object normalUser = this.flow.getOtherOption().get("normalSubmitUser");
      this.logger.info("submit flow by " + this.flow.getSubmitUser() + (normalUser == null
              || normalUser.toString() == "" ? ""
              : ("(" + normalUser + ")")));
      createThreadPool();
      runDateReplace();
      this.logger.info("Fetching job and shared properties.");
      if (!FlowLoaderUtils.isAzkabanFlowVersion20(this.flow.getAzkabanFlowVersion())) {
        loadAllProperties();
        initNSWTSSValue();
      }

      if (this.holdBatchSwitch) {
        String batchId = this.holdBatchContext
                .isInBatch(this.flow.getProjectName(), this.flow.getId(), this.flow.getSubmitUser());
        if (StringUtils.isNotEmpty(batchId) && !batchId.equals(this.lastBatchId)) {
          String key = (batchId + "-" + this.execId).intern();
          synchronized (key) {
            try {
              this.lastBatchId = batchId;
              this.logger.info("flow holding on preparing...");
              long holdStart = System.currentTimeMillis();
              logger.info("flow {} status: {} -> {}", this.flow.getPrintableId(":"),
                      this.flow.getStatus(), Status.SYSTEM_PAUSED);
              this.flow.setStatus(Status.SYSTEM_PAUSED);
              updateFlow();
              this.executorLoader.addHoldBatchAlert(batchId, this.flow, 0);
              key.wait();
              logger.info("flow {} status: {} -> {}", this.flow.getPrintableId(":"),
                      this.flow.getStatus(), Status.PREPARING);
              this.flow.setStatus(Status.PREPARING);
              this.flow.getOtherOption()
                      .put("holdInterval", System.currentTimeMillis() - holdStart);
              this.flow.getOtherOption()
                      .put("lastBatchId", this.lastBatchId);
              updateFlow();
            } catch (InterruptedException e) {

            }

          }
        }

      }

      this.flow.setStartTime(System.currentTimeMillis());
      flowStart2Ims();
      // FIXME Global variable settings for job runs.
      setSubmitUserProps(this.flow.getSubmitUser());
      // 设置系统内置变量
      setupFlowExecution();

      this.logger.info("Updating initial flow directory.");
      updateFlow();

      this.fireEventListeners(
              Event.create(this, EventType.FLOW_STARTED, new EventData(this.getExecutableFlow())));

      // FIXME When the task is submitted to the executor queue and the task status is preparing, the execution is terminated by the user, which causes the job stream to be in the killing state and cannot be turned to the killed state.
      if (this.flowKilled) {
        logger.info(this.flow.getExecutionId() + " was killed. ");
        logger.info("flow {} status: {} -> {}", this.flow.getPrintableId(":"),
                this.flow.getStatus(), Status.KILLED);
        this.flow.setStatus(Status.KILLED);
      } else {
        assembleSubflowJobNumLimit();
        runFlow();
      }
    } catch (final Throwable t) {
      FlowRunner.runningNoCheckerSumMap.get(this.flow.getProjectId()).get(this.execId).decrementAndGet();
      if (this.logger != null) {
        this.logger
                .error(
                        "An error has occurred during the running of the flow. Quiting.",
                        t);
      }
      logger.info("flow {} status: {} -> {}", this.flow.getPrintableId(":"), this.flow.getStatus(),
              Status.FAILED);
      this.flow.setStatus(Status.FAILED);
    } finally {
      try {
        this.flow.setEndTime(System.currentTimeMillis());
        this.logger.info("Setting end time for flow " + this.execId + " to "
                + System.currentTimeMillis());

        try {
          // 上报作业流开始
          Thread.interrupted();
          this.alerterHolder.get("email")
                  .alertOnIMSUploadForFlow(this.flow, this.sharedProps, this.logger, flowBusiness, null, this.azkabanProps);
        } catch (Exception e) {
          logger.error("The flow report IMS faild in the end", e);
        }
        if (this.watcher != null) {
          this.logger.info("Watcher is attached. Stopping watcher.");
          this.watcher.stopWatcher();
          this.logger
                  .info("Watcher cancelled status is " + this.watcher.isWatchCancelled());
        }
        closeLogger();
        updateFlow();
        logger.info("flow info: execId:{},status:{},updateTime:{}", flow.getExecutionId(), flow.getStatus(), flow.getUpdateTime());
        //更新FAIL_WAITING的job最终状态
        for (ExecutableNode node : this.flow.getExecutableNodes()) {
          updateFailWaitingJob(node);
        }
      } finally {
        FlowRunner.runningNoCheckerSumMap.get(this.flow.getProjectId()).remove(this.execId);
        this.fireEventListeners(
                Event.create(this, EventType.FLOW_FINISHED, new EventData(this.flow)));
        // In polling model, executor will be responsible for sending alerting emails when a flow
        // finishes.
        // Todo jamiesjc: switch to event driven model and alert on FLOW_FINISHED event.
        if (this.azkabanProps.getBoolean(ConfigurationKeys.AZKABAN_POLL_MODEL, false)) {
          // 通用告警和sla告警
          ExecutionControllerUtils.alertUserOnFlowFinished(this.flow, this.alerterHolder,
                  ExecutionControllerUtils.getFinalizeFlowReasons("Flow finished", null), this.executorLoader);
        }
      }
    }
  }

  private void flowStart2Ims() {
    try {

      //先从数据库查询，返回为空再查接口
      String jobCode = DmsBusPath
              .createJobCode(azkabanProps.get(JobProperties.JOB_BUS_PATH_CODE_PREFIX), this.flow.getProjectName(),
                      this.flow.getFlowId());
      this.flow.setJobCodeList(HttpUtils.getBusPathFromDBOrDms(azkabanProps, jobCode, 1, this.flow.getExecutionId(), this.executorLoader, logger));
      //关键路径不再注册直接上报
      if (CollectionUtils.isEmpty(this.flow.getJobCodeList())) {
        flowBusiness = this.projectLoader
                .getFlowBusiness(this.flow.getProjectId(), this.flow.getFlowId(), "");
        this.alerterHolder.get("email")
                .alertOnIMSRegistFlowStart(this.flow, this.sharedProps, logger, flowBusiness, this.azkabanProps);
      } else {
        if ((flowBusiness = this.projectLoader.getFlowBusiness(this.flow.getProjectId(), "", ""))
                == null) {
          flowBusiness = this.projectLoader
                  .getFlowBusiness(this.flow.getProjectId(), this.flow.getFlowId(), "");
        }
        this.alerterHolder.get("email")
                .alertOnIMSUploadForFlow(this.flow, this.sharedProps, this.logger, flowBusiness, null, this.azkabanProps);
      }
    } catch (Exception e) {
      logger.error("The flow report IMS faild in the start", e);
    }
  }

  private void setSubmitUserProps(String userName) {
    try {
      if (this.flow.getLastExecId() != -1) {
        return;
      }
      //对于循环执行特殊处理
      if (this.flow.getFlowType() == 4) {
        ExecutionCycle cycleFlow = this.executorLoader.getExecutionCycleFlow(String.valueOf(flow.getProjectId()), flow.getFlowId());
        String submitUser = cycleFlow.getSubmitUser();
        this.flow.setUserProps(this.executorLoader.getUserVariableByName(submitUser));
      } else {
        this.flow.setUserProps(this.executorLoader.getUserVariableByName(userName));
      }
    } catch (ExecutorManagerException em) {
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
    if (!repeatMap.isEmpty() && "RepeatFlow".equals(repeatMap.get("RepeatType"))) {
      long repeatTime = Long.valueOf(repeatMap.get("startTimeLong"));
      commonFlowProps = FlowUtils.addRepeatCommonFlowProperties(null, repeatTime, this.flow);
      this.flow.setLastParameterTime(repeatTime);
    } else {
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
  }

  private void updateFlow() {
    updateFlow(System.currentTimeMillis());
  }

  public synchronized void updateFlow(final long time) {
    try {
      this.flow.setUpdateTime(time);
      this.executorLoader.updateExecutableFlow(this.flow);
    } catch (final Exception e) {
      this.logger.error("Error updating flow.", e);
    }
  }

  private void updateJob(ExecutableNode node) {
    updateJob(node, System.currentTimeMillis());
  }

  private void updateJob(ExecutableNode node, long time) {
    try {
      node.setUpdateTime(time);
      this.executorLoader.updateExecutableNode(node);
    } catch (final Exception e) {
      this.logger.error("Error updating node.", e);
    }
  }

  /**
   * setup logger and execution dir for the flowId
   */
  private void createLogger(final String flowId, String projectName) {
    this.loggerName = UUID.randomUUID().toString() + "." + this.execId + "." + projectName + "." + flowId;
    this.logFileName = "_flow." + loggerName + ".log";
    this.logFile = new File(this.execDir, logFileName);
    LogUtils.createFlowLog(this.execDir.getAbsolutePath(), logFileName, loggerName);
    this.logger = LoggerFactory.getLogger(loggerName);
  }

  /**
   * 创建日志上传至 HDFS 目录，格式：/apps-data/hadoop/wtss/日期/项目名/工作流名
   *
   * @return
   */
  private String buildHdfsPath() {
    StringBuilder logPathBuilder = new StringBuilder(
            this.azkabanProps.getString(ConfigurationKeys.HDFS_LOG_PATH, "/apps-data/hadoop/wtss/"));

    // 添加日期
    LocalDate date = LocalDate.now();
    DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyyMMdd");
    String dateString = date.format(formatter);

    return logPathBuilder.append(dateString).append("/")
            .append(this.flow.getProjectName()).append("/")
            .append(this.flow.getFlowId()).toString();
  }


  public void closeLogger() {
    if (this.logger != null) {
      LogUtils.stopLog(loggerName);
      try {
        // 日志处理
        if (this.azkabanProps.getBoolean(ConfigurationKeys.HDFS_LOG_SWITCH, false)) {
          logger.info("Start to upload flow log for {} to HDFS. Exec id: {}", this.flow.getFlowId(),
                  this.execId);
          String hdfsLogDir = buildHdfsPath();
          LogUtils.uploadLog2Hdfs(hdfsLogDir, this.execDir.getAbsolutePath(), this.logFileName);
          this.executorLoader.uploadLogPath(this.execId, "", 0,
                  hdfsLogDir + "/" + this.logFileName);
        } else {
          this.executorLoader.uploadLogFile(this.execId, "", 0, this.logFile);
        }
      } catch (final ExecutorManagerException e) {
        logger.error("Failed to close logger", e);
      } catch (IOException e) {
        logger.error("Failed to initial HDFS FileSystem", e);
      }
    }
  }

  private void loadAllProperties() throws IOException {
    // First load all the properties
    for (final FlowProps fprops : this.flow.getFlowProps()) {
      final String source = fprops.getSource();
      Props props;
      if (this.flow.getLastExecId() != -1) {
        props = this.projectLoader.fetchProjectProperty(this.flow.getProjectId(),
                this.flow.getLastVersion(), source);
        if (props != null) {
          for (String key : props.getKeySet()) {
            String value = props.getString(key);
            Map<String, String> scriptMap = sbipu.paramDecompose(value, this.flow);
            if (MapUtils.isNotEmpty(scriptMap)) {
              //循环替换脚本中对应的参数内容
              for (String timeStr : scriptMap.keySet()) {
                props.put(key, StringUtils.replace(value, timeStr, scriptMap.get(timeStr)));
              }
            }
          }
        }
      } else {
        props = new Props(null, new File(this.execDir, source));
      }

      this.sharedProps.put(source, props);
      if (source.contains("priority.properties")) {
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

      if (this.holdBatchSwitch) {
        String batchId = this.holdBatchContext
                .isInBatch(this.flow.getProjectName(), this.flow.getId(), this.flow.getSubmitUser());
        if (StringUtils.isNotEmpty(batchId) && !batchId.equals(this.lastBatchId)) {
          String key = (batchId + "-" + this.execId).intern();
          synchronized (key) {
            this.logger.info("flow holding on running...");
            this.lastBatchId = batchId;
            long holdStart = System.currentTimeMillis();
            Status lastStatus = this.flow.getStatus();
            logger.info("flow {} status: {} -> {}", this.flow.getPrintableId(":"),
                    this.flow.getStatus(), Status.SYSTEM_PAUSED);
            this.flow.setStatus(Status.SYSTEM_PAUSED);
            updateFlow();
            this.executorLoader.addHoldBatchAlert(batchId, this.flow, 0);
            holdWait(key);
            if (!this.flowKilled && !this.flowFinished) {
              this.flow.getOtherOption().put("holdInterval", System.currentTimeMillis() - holdStart);
              this.flow.getOtherOption().put("lastBatchId", this.lastBatchId);
              logger.info("flow {} status: {} -> {}", this.flow.getPrintableId(":"),
                      this.flow.getStatus(), lastStatus);
              this.flow.setStatus(lastStatus);
              updateFlow();
            }
          }
        }

      }

      synchronized (this.mainSyncObj) {
        Thread.interrupted();

        if (this.flowPaused) {
          try {
            this.mainSyncObj.wait(CHECK_WAIT_MS);
          } catch (final InterruptedException e) {
          }
          if ((System.currentTimeMillis() - this.pausedStartTime) > maxPausedTime) {
            this.logger.warn("The pause timed out and the job flow was re executed.");
            reStart();
            updateFlow();
          }
          continue;
        } else {
          if (this.isRetryFailedJobs) {
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
    shutDownSubFlowJobConsumerThread();
    shutdownThreadPoolNow();

    updateFlow();
    this.logger.info("Finished Flow");
  }

  /**
   * 打断线程
   */
  private void shutDownSubFlowJobConsumerThread() {

    this.isSubflowJobConsumerThreadAlive = false;
  }

  private void holdWait(String batchId) {
    try {
      batchId.wait();
    } catch (InterruptedException e) {
      if (!this.flowFinished && !this.flowKilled) {
        holdWait(batchId);
      }
    }
  }

  public long getMaxPausedTime() {
    return maxPausedTime;
  }

  public FlowRunner setMaxPausedTime(long maxPausedTime) {
    this.maxPausedTime = maxPausedTime;
    return this;
  }

  private void retryAllFailures() throws Exception {
    this.logger.info("Restarting all failed jobs");

    this.isRetryFailedJobs = false;
    this.flowKilled = false;
    this.flowFailed = false;
    logger.info("RetryAllFailures step: flow {} status: {} -> {}", this.flow.getPrintableId(":"), this.flow.getStatus(),
            Status.RUNNING);
    this.flow.setStatus(Status.RUNNING);
    final ArrayList<ExecutableNode> retryJobs = new ArrayList<>();
    resetFailedState(this.flow, retryJobs);
    for (final ExecutableNode node : retryJobs) {
      if (this.retryJobs.isEmpty()) {
        this.logger.info("retryJob: " + node.getId() + "," + node.getStatus() + ", baseflow: "
                + (node instanceof ExecutableFlowBase));
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
      } else {
        if (node.getStatus() == Status.READY) {
          this.putRetryJobs(new ArrayList<>(this.retryJobs.keySet()), node.getNestedId(), node);
        }

        if (node.getStatus() == Status.DISABLED && !this.retryJobs
                .containsKey(node.getNestedId())) {
          node.skipNode(System.currentTimeMillis());
          finishExecutableNode(node);
        }

        if (Status.isSucceeded(node.getStatus()) && !this.retryJobs
                .containsKey(node.getNestedId())) {
          finishExecutableNode(node);
        }
      }

    }
    for (Entry<String, Set<String>> retryJob : this.retryJobs.entrySet()) {
      ExecutableNode node = this.flow.getExecutableNodePath(retryJob.getKey());
      if (!Status.isStatusRunning(node.getStatus())) {
        resetFlowStatus(node.getParentFlow(), node);
        node.resetForRetry();
      }
      if (retryJob.getValue().isEmpty()) {
        runReadyJob(node);
      }
    }

    updateFlow();
  }

  /**
   * skipped all Failed_waiting job.
   */
  public String skippedAllFailures(String user) {
    this.logger.info("skipped all failures jobs by " + user);
    if (this.flowKilled) {
      logger.warn("flow has been killed, can not skipped the failed jobs");
      return "作业流已被killed，不能执行跳过任务。";
    }
    synchronized (this.mainSyncObj) {
      if (this.flowKilled) {
        logger.warn("flow has been killed, can not skipped the failed jobs");
        return "作业流已被killed，不能执行跳过任务。";
      }
      stopKillFlowTrigger();
      if (this.failureAction == FailureAction.FAILED_PAUSE) {
        List<String> nodes = new ArrayList<>();
        for (ExecutableNode node : this.failedNodes.values()) {
          if (node.getStatus().equals(Status.FAILED_WAITING)) {
            //还原flow状态为Running
            resetFlowStatus(node.getParentFlow(), node);
            logger.info("skippedAllFailures: node[{}] status: {} -> {}", node.getId(), node.getStatus(),
                    Status.FAILED_SKIPPED);
            node.setStatus(Status.FAILED_SKIPPED);
            node.setUpdateTime(System.currentTimeMillis());
            updateJob(node);
            this.finishedNodes.add(node);
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
    logger.info("retry job list: " + retryFailedJobs.toString());
    if (this.flowKilled) {
      logger.warn("flow has been killed, can not skipped the failed jobs");
      return "作业流已被killed，不能重试任务。";
    }
    synchronized (this.mainSyncObj) {
      if (this.flowKilled) {
        logger.warn("flow has been killed, can not skipped the failed jobs");
        return "作业流已被killed，不能重试任务。";
      }
      ExecutableNode targetNode = this.flow.getExecutableNodePath(retryFailedJobs.get(0));
      if (targetNode != null && !(targetNode.getStatus().equals(Status.FAILED_WAITING))) {
        logger.warn(
                "job: " + targetNode.getNestedId() + " 不是 FAILED_WAITING，不能重试。");
        return "job: " + targetNode.getNestedId() + " 不是 FAILED_WAITING，不能重试。";
      }
      stopKillFlowTrigger();
      this.flowKilled = false;

      if (retryFailedJobs.size() > 1) {
        retryJobs(retryFailedJobs);
        logger.info("all retry jobs:" + this.retryJobs.toString());
      } else {
        resetFlowStatus(targetNode.getParentFlow(), targetNode);
        this.logger.info("Restarting failed job: " + targetNode.getId());
        targetNode.resetForRetry();
        if ((targetNode.getStatus() == Status.READY
                || targetNode.getStatus() == Status.DISABLED)) {
          runReadyJob(targetNode);
        }
      }
      updateFlow();
      return null;
    }
  }

  public String retryHangJobs(List<String> retryJobs, String user) throws Exception {
    this.logger.info("retry hang jobs: " + retryJobs.toString());
    if (this.flowKilled) {
      logger.warn("flow has been killed, can not rerun the jobs");
      return "The flow has been killed，can not rerun the jobs.";
    }

    synchronized (this.mainSyncObj) {
      if (this.flowKilled) {
        logger.warn("flow has been killed, can not rerun the jobs");
        return "The flow has been killed，can not rerun the jobs.";
      }
      ExecutableNode targetNode = this.flow.getExecutableNodePath(retryJobs.get(0));
      if (targetNode != null && !targetNode.getStatus().equals(Status.RUNNING)) {
        logger.warn("job:" + targetNode.getNestedId() + " is not RUNNING，can not rerun");
        return "job:" + targetNode.getNestedId() + "is not RUNNING，can not rerun";
      }
//      stopKillFlowTrigger();
//      this.flowKilled = false;

      if (targetNode != null && Status.isStatusRunning(targetNode.getStatus())) {
        for (JobRunner jobRunner : this.activeJobRunners) {
          if (jobRunner.getNode().getNestedId().equals(targetNode.getNestedId())) {
            logger.info("retrying job:" + targetNode.getNestedId());
            jobRunner.rerunJob(user);
          }
        }
      }

      updateFlow();
      return null;
    }
  }

  private void retryJobs(List<String> jobList) {
    for (String job : jobList) {
      ExecutableNode node = this.flow.getExecutableNodePath(job);
      Set<String> jobSet = putRetryJobs(jobList, job, node);
      logger.info("depend job list: " + jobSet);
      if (!Status.isStatusRunning(node.getStatus())) {
        resetFlowStatus(node.getParentFlow(), node);
        node.resetForRetry();
      }
      if (jobSet.isEmpty()) {
        new Thread(() -> {
          try {
            runReadyJob(node);
          } catch (Exception e) {
            throw new RuntimeException(e);
          }
        }).start();
      }
    }
  }

  @NotNull
  private Set<String> putRetryJobs(List<String> jobList, String job, ExecutableNode node) {
    Set<String> jobSet = new HashSet<>();
    getDepend4Job(node, jobList, jobSet);
    if (this.retryJobs.get(job) != null) {
      this.retryJobs.get(job).addAll(jobSet);
    } else {
      this.retryJobs.put(job, jobSet);
    }
    return jobSet;
  }


  public void setFlowFailed(final boolean flowFailed) {
    logger.info("setting flow: " + this.flow.getExecutionId() + " " + flowFailed);
    this.flowFailed = flowFailed;
  }

  private String jobFailedHandle(ExecutableNode node, String user) {
    String msg = "";
    if (node instanceof ExecutableFlowBase) {
      // 子工作流
      if (node.getStatus().equals(Status.READY)) {
        msg = setJobFailed(node, user);
      } else {
        for (ExecutableNode chiledNode : ((ExecutableFlowBase) node).getExecutableNodes()) {
          jobFailedHandle(chiledNode, user);
        }
      }
    } else {
      // 任务节点
      msg = setJobFailed(node, user);
    }
    return msg;
  }

  private String setJobFailed(ExecutableNode node, String user) {
    String msg = "";
    if (node.getStatus().equals(Status.READY)) {
      // 判断工作流执行失败策略
      if (this.failureAction == FailureAction.CANCEL_ALL
              || this.failureAction == FailureAction.FINISH_CURRENTLY_RUNNING) {
        // 完成当前正在运行的任务/结束所有正在执行的任务，工作流不继续往下执行，因此设置为预失败无意义
        msg = "工作流设置的失败策略为 " + this.failureAction + "，不能对未执行节点设置为 PRE_FAILED";
        logger.warn("工作流设置的失败策略为 {}，不能对未执行节点设置为 PRE_FAILED",
                this.failureAction);
        return msg;
      }
      logger.info(
              user + " is setting job:" + node.getNestedId() + " status " + node.getStatus().toString()
                      + " to status PRE_FAILED.");
      msg =
              user + " is setting job:" + node.getNestedId() + " status " + node.getStatus().toString()
                      + " to status PRE_FAILED.";
      logger.info("node[{}] status: {} -> {}", node.getId(), node.getStatus(), Status.PRE_FAILED);
      node.setStatus(Status.PRE_FAILED);
      node.setUpdateTime(System.currentTimeMillis());
    } else if (Status.isStatusRunning(node.getStatus())) {
      for (JobRunner jobRunner : this.activeJobRunners) {
        if (jobRunner.getNode().getNestedId().equals(node.getNestedId())) {
          logger.info(user + " is setting job:" + node.getNestedId() + " status " + node.getStatus()
                  .toString()
                  + " to FAILED.");
          msg = user + " is setting job:" + node.getNestedId() + " status " + node.getStatus()
                  .toString() + " to FAILED.";
          jobRunner.setJobFailed(user);
        }
      }
    }

    return msg;
  }

  private void jobSkippedHandle(ExecutableNode node, String user) {
    if (node instanceof ExecutableFlowBase) {
      if (node.getStatus().equals(Status.READY)) {
        setJobSkipped(node, user);
      } else {
        for (ExecutableNode chiledNode : ((ExecutableFlowBase) node).getExecutableNodes()) {
          jobSkippedHandle(chiledNode, user);
        }
      }
    } else {
      setJobSkipped(node, user);
    }
  }

  private Boolean jobOpenHandle(ExecutableNode node, String user, ExecutableNode targetNode) {
    ExecutableNode parentFlow = node.getParentFlow();

    if (parentFlow.getStatus().equals(Status.READY)) {
      if (jobOpenHandle(parentFlow, user, targetNode)) {
        if (parentFlow.getStatus().equals(Status.DISABLED)) {
          logger.info("jobOpenHandle: parent flow {} status: {} -> {}", parentFlow.getPrintableId(":"),
                  parentFlow.getStatus(), Status.READY);
          parentFlow.setStatus(Status.READY);
          parentFlow.setUpdateTime(System.currentTimeMillis());
          logger.info("User: " + user + "setting job:" + parentFlow.getNestedId() + " status " + parentFlow.getStatus().toString() + " to status ready.");
          ExecutableFlowBase flow = (ExecutableFlowBase) parentFlow;
          for (ExecutableNode executableNode : flow.getExecutableNodes()) {
            if (executableNode.getStatus().equals(Status.READY)) {
              logger.info("jobOpenHandle: node[{}] status: {} -> {}", executableNode.getId(),
                      executableNode.getStatus(), Status.DISABLED);
              executableNode.setStatus(Status.DISABLED);
              executableNode.setUpdateTime(System.currentTimeMillis());
              logger.info("User: " + user + "setting job:" + executableNode.getNestedId() + " status " + executableNode.getStatus().toString() + " to status disabled.");
            }
          }
        }
        if (node.getNestedId().equals(targetNode.getNestedId())) {
          logger.info("jobOpenHandle: node[{}] status: {} -> {}", node.getId(), node.getStatus(), Status.READY);
          node.setStatus(Status.READY);
          node.setUpdateTime(System.currentTimeMillis());
          logger.info("User: " + user + "setting job:" + node.getNestedId() + " status " + node.getStatus().toString() + " to status ready.");
        }
        return true;
      }
    } else if (parentFlow.getStatus().equals(Status.DISABLED)) {
      if (jobOpenHandle(parentFlow, user, targetNode)) {
        logger.info("parent flow {} status: {} -> {}", parentFlow.getPrintableId(":"),
                parentFlow.getStatus(), Status.READY);
        parentFlow.setStatus(Status.READY);
        parentFlow.setUpdateTime(System.currentTimeMillis());
        logger.info("User: " + user + "setting job:" + parentFlow.getNestedId() + " status " + parentFlow.getStatus().toString() + " to status ready.");
        ExecutableFlowBase flow = (ExecutableFlowBase) parentFlow;
        for (ExecutableNode executableNode : flow.getExecutableNodes()) {
          if (executableNode.getStatus().equals(Status.READY)) {
            logger.info("node[{}] status: {} -> {}", executableNode.getId(),
                    executableNode.getStatus(), Status.DISABLED);
            executableNode.setStatus(Status.DISABLED);
            executableNode.setUpdateTime(System.currentTimeMillis());
            logger.info("User: " + user + "setting job:" + executableNode.getNestedId() + " status " + executableNode.getStatus().toString() + " to status disabled.");
          }
        }
        if (node.getNestedId().equals(targetNode.getNestedId())) {
          logger.info("node[{}] status: {} -> {}", node.getId(), node.getStatus(), Status.READY);
          node.setStatus(Status.READY);
          node.setUpdateTime(System.currentTimeMillis());
          logger.info("User: " + user + "setting job:" + node.getNestedId() + " status " + node.getStatus().toString() + " to status ready.");
        }
        return true;
      }
    } else if (parentFlow.getStatus().equals(Status.RUNNING) || parentFlow.getStatus().equals(Status.FAILED_FINISHING) || parentFlow.getStatus().equals(Status.PAUSED)) {
      if (node.getNestedId().equals(targetNode.getNestedId())) {
        logger.info("node[{}] status: {} -> {}", node.getId(), node.getStatus(), Status.READY);
        node.setStatus(Status.READY);
        node.setUpdateTime(System.currentTimeMillis());
        logger.info("User: " + user + "setting job:" + node.getNestedId() + " status " + node.getStatus().toString() + " to status ready.");
      }
      return true;
    }

    return false;
  }

  private void setJobSkipped(ExecutableNode node, String user) {
    if (node.getStatus().equals(Status.READY)) {
      logger.info("setJobSkipped node[{}] status: {} -> {}", node.getId(), node.getStatus(), Status.DISABLED);
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

  private void decrementRunning(ExecutableNode node) {
    AtomicInteger num = FlowRunner.runningNoCheckerSumMap.get(this.flow.getProjectId())
            .get(this.execId);
    String nodeType = node.getType();
    if ("linkis".equals(nodeType)) {
      String linkisType = node.getLinkisType();
      if (!linkisType.contains("datachecker") && !linkisType.contains("eventchecker") && num != null
              && num.get() > 0) {
        num.decrementAndGet();
      }
    } else if (!"datachecker".equals(nodeType) && !"eventchecker".equals(nodeType) && num != null
            && num.get() > 0) {
      num.decrementAndGet();
    }
  }

  /**
   * 1.Change the job status of ready to disabled.
   * 2.Change the running (running, queue, failed_retrying) job to the failed_skipped state.
   *
   * @param nodePath
   * @param respMap
   * @throws ExecutorManagerException
   */
  public void setJobDisabled(String nodePath, Map<String, Object> respMap, String user) throws ExecutorManagerException {
    logger.info("disable job: " + this.flow.getExecutionId() + ", " + nodePath);
    if (this.flowKilled || this.flowFinished) {
      logger.warn("flow has been killed or finished, can not skipped the failed jobs");
      respMap.put(ConnectorParams.RESPONSE_ERROR, "设置失败，作业流已经killed或者finished。");
      return;
    }
    synchronized (this.mainSyncObj) {
      ExecutableNode node = this.flow.getExecutableNodePath(nodePath);
      if (this.flowKilled || this.flowFinished) {
        logger.warn("flow has been killed or finished, can not skipped the failed jobs");
        respMap.put(ConnectorParams.RESPONSE_ERROR, "设置失败，作业流已经killed或者finished。");
      } else if (node == null) {
        logger.warn("job: " + this.flow.getExecutionId() + ", " + nodePath + " is not exists.");
        respMap.put(ConnectorParams.RESPONSE_ERROR, "job: " + this.flow.getExecutionId() + ", " + nodePath + " is not exists.");
      } else if (Status.isStatusFinished(node.getStatus())) {
        logger.warn("job: " + nodePath + " is not running.");
        respMap.put(ConnectorParams.RESPONSE_ERROR, "设置失败, 任务可能已经执行完成。");
      } else {
        jobSkippedHandle(node, user);
        updateFlow();
      }
    }
  }

  public void setJobFailed(String nodePath, Map<String, Object> respMap, String user) {
    logger.info(user + " is setting job: " + nodePath + " to FAILED/PRE_FAILED. ");
    if (this.flowKilled || this.flowFinished) {
      logger.warn("flow, with status: {}, has been killed or finished, can not set jobs",
              this.flow.getStatus());
      respMap.put(ConnectorParams.RESPONSE_ERROR, "操作失败，作业流已经killed或者finished。");
      return;
    }
    synchronized (this.mainSyncObj) {
      ExecutableNode node = this.flow.getExecutableNodePath(nodePath);
      if (this.flowKilled || this.flowFinished) {
        logger.warn("flow, with status: {}, has been killed or finished, can not set jobs",
                this.flow.getStatus());
        respMap.put(ConnectorParams.RESPONSE_ERROR, "操作失败，作业流已经killed或者finished。");
      } else if (node == null) {
        logger.warn("job: {} in execId: {} does not exist", nodePath, this.flow.getExecutionId());
        respMap.put(ConnectorParams.RESPONSE_ERROR,
                "job: " + nodePath + " in execId: " + this.flow.getExecutionId() + " does not exist");
      } else if (Status.isStatusFinished(node.getStatus())) {
        // 节点状态判断
        logger.warn("job: " + nodePath + " is already finished.");
        respMap.put(ConnectorParams.RESPONSE_ERROR, "操作失败, 任务已经执行完成。");
      } else {
        String msg = jobFailedHandle(node, user);
        respMap.put(ConnectorParams.RESPONSE_ERROR, msg);
        updateFlow();
      }
    }
  }

  public void setJobReady(String nodePath, Map<String, Object> respMap, String user) throws ExecutorManagerException {
    logger.info("open job: " + this.flow.getExecutionId() + ", " + nodePath);

    synchronized (this.mainSyncObj) {
      ExecutableNode node = this.flow.getExecutableNodePath(nodePath);
      if (this.flowKilled || this.flowFinished) {
        logger.warn("flow is not running, can not open the disabled job");
        respMap.put(ConnectorParams.RESPONSE_ERROR, "设置失败，作业流可能已经结束。");
      } else if (node == null) {
        logger.warn("job: " + this.flow.getExecutionId() + ", " + nodePath + " is not exists.");
        respMap.put(ConnectorParams.RESPONSE_ERROR, "job: " + this.flow.getExecutionId() + ", " + nodePath + " is not exists.");
      } else if (!node.getStatus().equals(Status.READY) && !node.getStatus().equals(Status.DISABLED)) {
        logger.warn("job: " + nodePath + " is not running.");
        respMap.put(ConnectorParams.RESPONSE_ERROR, "设置失败, 任务可能已经执行完成。");
      } else {
        if (jobOpenHandle(node, user, node)) {
          respMap.put("openJob", nodePath);
          respMap.put("status", node.getStatus());
          updateFlow();
        } else {
          respMap.put(ConnectorParams.RESPONSE_ERROR, "打开工作流节点失败");
        }
      }
    }
  }

  public boolean setFlowFailed(final JsonObject json) {
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


  private boolean progressGraph() throws Exception {
    this.finishedNodes.swap();
    // The following nodes are finished, so we'll collect a list of outnodes
    // that are candidates for running next.
    final HashSet<ExecutableNode> nodesToCheck = new HashSet<>();

    for (Entry<String, Set<String>> retryJob : this.retryJobs.entrySet()) {
      if (retryJob.getValue().stream().allMatch(
              job -> Status.isStatusSucceeded(this.flow.getExecutableNodePath(job).getStatus()))) {
        nodesToCheck.add(this.flow.getExecutableNodePath(retryJob.getKey()));
      }
    }

    for (final ExecutableNode node : this.finishedNodes) {
      Set<String> outNodeIds = new HashSet<>();
      // FIXME If the node status is not FAILED_WAITING, you need to get its external nodes.
      if (!node.getStatus().equals(Status.FAILED_WAITING)) {
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

      if ((outNodeIds.isEmpty() && isFlowReadytoFinalize(parentFlow)) || isFlowReadytoFinalizeByAll(parentFlow)) {
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
        try {
          final ExecutableNode outNode = parentFlow.getExecutableNode(nodeId);
          nodesToCheck.add(outNode);
        } catch (ConcurrentModificationException | NullPointerException e) {
          this.logger.info("cannot find outnode {} in nodesToChecks {}", nodeId, e);
          continue;
        }
      }
    }
    // FIXME New function, run according to the priority set by the node.
    boolean openPriority = false;
    List<ExecutableNode> priList = new ArrayList<>();
    for (final ExecutableNode node : nodesToCheck) {
      priList.add(node);
      if (node.getInputProps() == null) {
        logger.info("Start to prepare job properties for node:{}", node.getId());
        prepareJobProperties(node);
      }
      if (node.getInputProps() != null) {
        String priLevel = node.getInputProps().get("priority");
        if (null != priLevel && verifyPriority(priLevel)) {
          logger.warn("任务" + node.getId() + "优先级采参数设置错误！请设置priority的值为1,2,3,4,5这5个数字中的一个！");
          openPriority = false;
          node.getInputProps().put("priority", "0");
          continue;
        }
        if (null != priLevel) {
          openPriority = true;
        }
      } else {
        Props initProps = new Props();
        initProps.put("priority", 0);
        node.setInputProps(initProps);
      }
    }
    //启动优先级机制执行同一个父节点下面的Job
    if (openPriority) {
      //按优先级排序
      Collections.sort(priList, new Comparator<ExecutableNode>() {
        @Override
        public int compare(ExecutableNode o1, ExecutableNode o2) {
          String priFString = o1.getInputProps().get("priority");
          String priSString = o2.getInputProps().get("priority");
          Integer priF = priFString == null ? 0 : Integer.valueOf(priFString);
          Integer priS = priSString == null ? 0 : Integer.valueOf(priSString);
          if (priF > priS) {
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
    // ALL_SUCCESS, we should set the flow to failed. Otherwise, it could still statisfy theh
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
      if (!node.getStatus().equals(Status.FAILED_WAITING)) {
        this.flowFailed = true;
      }
    }
  }

  private boolean notReadyToRun(final Status status) {
    return Status.isStatusFinished(status)
            || Status.isStatusRunning(status)
            || Status.KILLING == status;
  }

  private boolean runReadyJob(final ExecutableNode node) throws Exception {
    if (Status.isStatusFinished(node.getStatus())
            || Status.isStatusRunning(node.getStatus()) || Status.FAILED_WAITING.equals(node.getStatus())) {
      return false;
    }

    final Status nextNodeStatus = getImpliedStatus(node, this.flow.getOtherOption());
    if (nextNodeStatus == null) {
      return false;
    }

    if (nextNodeStatus == Status.CANCELLED) {
      // if node is root flow
      if (node instanceof ExecutableFlow && node.getParentFlow() == null) {
        this.logger.info(String.format("Flow '%s' was cancelled before execution had started.",
                node.getId()));
        finalizeFlow((ExecutableFlow) node);
      } else {
        this.logger.info(String.format("Cancelling '%s' due to prior errors.", node.getNestedId()));
        node.cancelNode(System.currentTimeMillis());
        finishExecutableNode(node);
      }
    } else if (nextNodeStatus == Status.SKIPPED) {
      this.logger.info("Skipping disabled job '" + node.getId() + "'.");
      node.skipNode(System.currentTimeMillis());
      finishExecutableNode(node);
    } else if (nextNodeStatus == Status.FAILED_SKIPPED) {
      this.logger.info("Failed skipped job '" + node.getId() + "'.");
      node.faliedSkipedNode(System.currentTimeMillis());
      finishExecutableNode(node);
    } else if (nextNodeStatus == Status.READY) {
      if (node instanceof ExecutableFlowBase) { // 子工作流
        final ExecutableFlowBase flow = ((ExecutableFlowBase) node);
        if (flow.getStatus().equals(Status.PRE_FAILED)) {
          // 预失败处理
          this.logger.info("PRE_FAILED flow '{}' to FAILED. ", flow.getFlowId());
          logger.info("node[{}] status: {} -> {}", flow.getFlowId(), flow.getStatus(),
                  Status.FAILED);
          flow.setStatus(Status.FAILED);
          flow.setStartTime(System.currentTimeMillis());
          flow.setEndTime(System.currentTimeMillis());
          flow.setUpdateTime(System.currentTimeMillis());
          finishExecutableNode(flow);
        } else {
          this.logger.info(
                  "baseFlow :" + flow.getFlowId() + " , " + flow.getId() + " Running flow '"
                          + flow.getNestedId() + "'.");
          flow.setStartTime(System.currentTimeMillis());
          if (!(flow instanceof ExecutableFlow)) {
            subFlowReport2Ims(flow);
          }
          logger.info("runReadyJob: flow {} status: {} -> {}", flow.getPrintableId(":"), flow.getStatus(),
                  Status.RUNNING);
          flow.setStatus(Status.RUNNING);
          prepareJobProperties(flow);
          genElasticJob(flow);
          for (final String startNodeId : ((ExecutableFlowBase) node).getStartNodes()) {
            final ExecutableNode startNode = flow.getExecutableNode(startNodeId);
            runReadyJob(startNode);
          }
        }
      } else {  // job
        // 预失败处理
        if (node.getStatus().equals(Status.PRE_FAILED)) {
          this.logger.info("PRE_FAILED job '{}' to FAILED. ", node.getId());
          logger.info("node[{}] status: {} -> {}", node.getId(), node.getStatus(), Status.FAILED);
          node.setStatus(Status.FAILED);
          node.setStartTime(System.currentTimeMillis());
          node.setEndTime(System.currentTimeMillis());
          node.setUpdateTime(System.currentTimeMillis());
          finishExecutableNode(node);
        } else {
          runExecutableNode(node);
        }
      }
    }
    return true;
  }

  private void subFlowReport2Ims(ExecutableFlowBase flowBase) {
    synchronized (this.mainSyncObj) {
      try {
        Thread.interrupted();
        FlowBusiness jobFlowBusiness = FlowRunner.this.projectLoader
                .getFlowBusiness(FlowRunner.this.flow.getProjectId(), flowBase.getFlowId(), "");
        FlowRunner.this.alerterHolder.get("email")
                .alertOnIMSUploadForFlow(flowBase, FlowRunner.this.sharedProps, FlowRunner.this.logger,
                        jobFlowBusiness == null ? flowBusiness : jobFlowBusiness, null,
                        FlowRunner.this.azkabanProps);
      } catch (Exception e) {
        logger.error("The job report IMS faild in the end", e);
      }
    }
  }

  /**
   * generate elasticJob by elastic.params
   *
   * @param flowBase
   * @throws Exception
   */
  public void genElasticJob(ExecutableFlowBase flowBase) throws Exception {
    if (!flowBase.isElasticNode() || flowBase.isSplit()) {
      logger.debug("not elastic flow, or already split.");
      return;
    }
    String elasticParamKey = FlowUtils.getElasticParamKey(flowBase.getExecutableNodes(), execDir);
    if (elasticParamKey == null) {
      logger.warn("can not found elastic.params key in job file.");
      return;
    }
    List<String> elasticParams;
    // 根据elasticParamKey从flowbase中获取参数值
    logger.debug("elastic param key:{}", elasticParamKey);
    Props flowBaseProps = Props.clone(flowBase.getInputProps());
    flowBaseProps = PropsUtils.resolveProps(flowBaseProps);
    elasticParams = flowBaseProps.getStringList(elasticParamKey, new ArrayList<>());
    if (elasticParams.size() == 0) {
      List<String> userElasticParams = azkaban.utils.StringUtils.getUserPropsElasticParamValue(elasticParamKey, this.flow.getUserProps());
      elasticParams = userElasticParams;
    }
    flowBase.addElasticParams(elasticParamKey, elasticParams);
    if (elasticParams == null || elasticParams.size() <= 1) {
      logger.warn("elastic.params size <= 1");
      return;
    }
    ExecutableNode endNode = flowBase.getExecutableNode(flowBase.getEndNodes().get(0));
    Project project = this.projectLoader.fetchProjectByName(this.flow.getProjectName());
    FlowUtils.loadAllProjectFlows(project, projectLoader);
    Map<Integer, ExecutableFlowBase> executableFlowMap = new TreeMap<>();
    for (int i = 1; i < elasticParams.size(); i++) {
      ExecutableFlowBase newFlowBase = (ExecutableFlowBase) new ExecutableFlow(project, project.getFlow(this.flow.getFlowId())).getExecutableNodePath(flowBase.getNestedId());
      FlowUtils.copyExecutableNodesProperties(flowBase, newFlowBase, i, this.flow, elasticParams.get(i), endNode);
      executableFlowMap.put(i, newFlowBase);
      FlowUtils.updateExecutableFlow(newFlowBase);
      newFlowBase.getStartNodes();
      newFlowBase.getEndNodes();
    }
    // 把复制的节点添加到flowbase中
    for (ExecutableFlowBase executableFlowBase : executableFlowMap.values()) {
      ExecutableNode end = executableFlowBase.getExecutableNode(executableFlowBase.getEndNodes().get(0));
      executableFlowBase.getStartNodes().stream().forEach(x -> flowBase.addStartNode(executableFlowBase.getExecutableNode(x)));
      for (ExecutableNode node : executableFlowBase.getExecutableNodes()) {
        node.setParentFlow(flowBase);
        if (end != node) {
          flowBase.addExecutableNode(node);
        }
      }
      endNode.addAllInNode(end.getInNodes());
    }
    // 给每个elasticNode 添加对应的动态参数，如 elastic.param.xxx=a
    for (ExecutableNode node : flowBase.getExecutableNodes()) {
      if (node.isElasticNode()) {
        Props p = new Props();
        p.put(elasticParamKey, elasticParams.get(node.getElasticParamIndex()));
        node.setJobSourcePros(p);
      }
    }
    flowBase.setSplit(true);
    updateFlow();
  }

  private boolean retryJobIfPossible(final ExecutableNode node) {
    if (node instanceof ExecutableFlowBase) {
      return false;
    }

    // PRE_FAILED 节点转为 FAILED 状态，没有赋给 properties，因此需要处理此种场景
    if (node.getInputProps() == null) {
      this.logger.info("Job {} has no input properties. ", node.getId());
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
   * @param base   the base flow
   * @param status the status to be propagated
   */
  private void propagateStatusAndAlert(final ExecutableFlowBase base, final Status status) {
    if (!Status.isStatusFinished(base.getStatus()) && base.getStatus() != Status.KILLING) {
      this.logger.info("Setting " + base.getNestedId() + " to " + status);
      boolean shouldAlert = false;
      if (base.getStatus() != status) {
        logger.info("propagateStatusAndAlert: node[{}] status: {} -> {}", base.getId(), base.getStatus(), status);
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

  private boolean isFlowReadytoFinalizeByAll(final ExecutableFlowBase flow) {
    // Only when all the end nodes are finished, the flow is ready to finalize.
    for (final ExecutableNode node : flow.getExecutableNodes()) {
      if (node instanceof ExecutableFlowBase) {
        if (!isFlowReadytoFinalizeByAll((ExecutableFlowBase) node)) {
          return false;
        }
      }
      if (!Status.isStatusFinished(node.getStatus())) {
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


    flow.setOutputProps(previousOutput);
    if (!succeeded && (flow.getStatus() == Status.RUNNING)) {
      logger.info("finalizeFlow: flow {} status: {} -> {}", flow.getPrintableId(":"), flow.getStatus(),
              Status.KILLED);
      flow.setStatus(Status.KILLED);
    }

    flow.setEndTime(System.currentTimeMillis());
    flow.setUpdateTime(System.currentTimeMillis());
    final long durationSec = (flow.getEndTime() - flow.getStartTime()) / 1000;
    switch (flow.getStatus()) {
      case FAILED_FINISHING:
        this.logger.info("finalizeFlow: flow {} status: {} -> {} in {} seconds", flow.getPrintableId(":"),
                flow.getStatus(), Status.FAILED,
                durationSec);
        flow.setStatus(Status.FAILED);
        break;
      case KILLING:
        this.logger
                .info("finalizeFlow killing: flow {} status: {} -> {} in {} seconds", flow.getPrintableId(":"),
                        flow.getStatus(), Status.KILLED,
                        durationSec);
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
        this.logger.info("finalizeFlow default: flow {} status: {} -> {} in {} seconds", flow.getPrintableId(":"),
                flow.getStatus(), Status.SUCCEEDED,
                durationSec);
        flow.setStatus(Status.SUCCEEDED);
    }

    // If the finalized flow is actually the top level flow, than we finish
    // the main loop.
    if (flow instanceof ExecutableFlow) {
      this.flowFinished = true;
    } else {
      subFlowReport2Ims(flow);
    }
  }

  /**
   * 解析job文件，获取里面的配置   .job > output变量 > 父作业流配置(包含azkaban内置变量，执行参数) > properties > userProperties
   *
   * @param node
   * @throws IOException
   */
  private void prepareJobProperties(final ExecutableNode node) throws IOException {
    if (node instanceof ExecutableFlow) {
      return;
    }
    logger.info("start to initialize properties for node {} ", node.getId());
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
    if (props != null) {
      props.putAll(this.flow.getExecutionOptions().getFlowParameters());
      props.putAll(this.flow.getJobOutputGlobalParam());
    }

    if (this.flow.getOtherOption().containsKey("event_schedule_save_key")) {
      Map<String, String> map = (Map<String, String>) this.flow.getOtherOption()
              .get("event_schedule_save_key");
      if (MapUtils.isNotEmpty(map)) {
        for (Entry<String, String> entry : map.entrySet()) {
          props.put(entry.getKey(), entry.getValue());
        }
      }
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
    if ((!(node instanceof ExecutableFlowBase)) && this.flow.getUserProps() != null && this.flow.getUserProps().size() != 0) {
      Props userProps = new Props();
      userProps.putAll(this.flow.getUserProps());
      //如果有properties配置，设置为prop的祖父级配置
      if (props != null) {
        props.setEarliestAncestor(userProps);
      }
    }
    // 全局变量开关
    String allFlag = props.getString("ns_wtss", "true");
    if (tmpOutputProps != null) {
      if (!"false".equals(allFlag)) {
        setAllVar(tmpOutputProps);
      }
    }

    // FIXME Add some execution parameters to props, such as failed retry tasks, failed skip tasks, etc.
    setExecutionProps(node, props);

    // FIXME Add workflow execution root directory to props.
    props.put("flow.dir", this.execDir.getAbsolutePath());

    //set holding interval
    if ("eventchecker".equals(node.getType())) {
      if (this.flow.getOtherOption().containsKey("isHoldingSubmit")) {
        props.put("hold.time.interval", this.flow.getStartTime() - this.flow.getSubmitTime());
      } else if (this.flow.getOtherOption().containsKey("holdInterval")) {
        props.put("hold.time.interval", (long) this.flow.getOtherOption().get("holdInterval"));
      }
    }
    node.setInputProps(props);
  }

  private void setExecutionProps(ExecutableNode node, Props props) {
    // 1、是否设置了跳过所有job
    // 2、job设置了失败跳过， 通过配置专递给job运行
    List<String> skipFaultJobList = (ArrayList) this.flow.getOtherOption()
            .get("jobSkipFailedOptions");
    List<String> jobSkipActionOptions = (ArrayList) this.flow.getOtherOption()
            .get("jobSkipActionOptions");
    if (this.flow.getFailedSkipedAllJobs() || (skipFaultJobList != null && (skipFaultJobList
            .contains(node.getId()) || validJobInSkipFLow(node, skipFaultJobList)))) {
      logger
              .info("execId: " + this.flow.getExecutionId() + ", node: " + node.getId() + ", 设置了失败跳过.");
      props.put("azkaban.jobSkipFailed", node.getId());
      if (CollectionUtils.isNotEmpty(jobSkipActionOptions) && (
              jobSkipActionOptions.contains(node.getId()) || jobSkipActionOptions.stream()
                      .anyMatch(skipAction -> skipAction.startsWith("all_jobs ")) || validJobInSkipFLow(
                      node, jobSkipActionOptions))) {
        props.put("job.skip.action", node.getId());
      }
    }

    //设置了失败暂停 当job失败时状态改为FAILED_WAITING
    if (FlowRunner.this.failureAction == FailureAction.FAILED_PAUSE) {
      logger.debug("execId: " + this.flow.getExecutionId() + "， 设置了失败暂停。");
      props.put("azkaban.failureAction", FailureAction.FAILED_PAUSE.toString());
    }

    //获取失败重跑配置并添加到 Job 的配置内容中
    if (null != this.flow.getOtherOption().get("jobFailedRetryOptions")) {
      List<Map<String, String>> jobFailedRetryOptions = (List<Map<String, String>>) this.flow
              .getOtherOption().get("jobFailedRetryOptions");
      for (Map<String, String> map : jobFailedRetryOptions) {
        if (node.getId().equals(map.get("jobName"))) {
          props.put("job.failed.retry.interval", map.get("interval"));
          props.put("job.failed.retry.count", map.get("count"));
        }
      }
    }
    // 设置里flow失败重跑，所有job都要继承该配置
    Map<String, String> flowFailedRetryOption = this.flow.getFlowFailedRetry();
    if (flowFailedRetryOption != null && flowFailedRetryOption.size() != 0) {
      Props flowFailedRetryProps = new Props(null);
      flowFailedRetryProps.putAll(flowFailedRetryOption);
      props.setEarliestAncestor(flowFailedRetryProps);
    }

    props.put("rerun.action", this.flow.getExecutionOptions().getRerunAction());

  }

  private void setAllVar(Props outputProps) {
//    this.flow.getExecutionOptions().getFlowParameters().putAll(outputProps.getFlattened());
    this.flow.addJobOutputGlobalParam(outputProps.getFlattened());
    this.flow.getInputProps().putAll(outputProps);
  }

  /**
   * @param props This method is to put in any job properties customization before feeding to the
   *              job.
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
      while (retrylimit < 3) {
        props = loadPropsFromYamlFile(jobPath);
        retrylimit++;
        this.logger.info("Job path loaded from yaml file " + jobPath);
        if (props != null) break;
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
    // 动态job参数 elastic.param.xxx
    if (node.isElasticNode() && !(node instanceof ExecutableFlowBase)) {
      props.putAll(node.getJobSourcePros());
    }

    customizeJobProperties(props);

    return props;
  }

  private Props loadPropsFromYamlFile(final String path) {
    File tempDir = null;
    Props props = null;
    File flowFile = null;
    try {
      tempDir = Files.createTempDir();
      if (this.flow.getLastExecId() != -1 && path.split(Constants.PATH_DELIMITER).length == 1) {
        final int latestFlowVersion = this.projectLoader.getLatestFlowVersion(this.flow.getProjectId(), this.flow.getLastVersion(), path + Constants.FLOW_FILE_SUFFIX);
        if (latestFlowVersion > 0) {
          flowFile = this.projectLoader
                  .getUploadedFlowFile(this.flow.getProjectId(), this.flow.getLastVersion(),
                          path + Constants.FLOW_FILE_SUFFIX, latestFlowVersion, tempDir);
        }
      } else {
        flowFile = getFlowFile(tempDir);
      }
      props = FlowLoaderUtils.getPropsFromYamlFile(path, flowFile);
    } catch (final Exception e) {
      this.logger.error("Failed to get props from flow file {}. ", flowFile, e);
    } finally {
      if (tempDir != null && tempDir.exists()) {
        try {
          FileUtils.deleteDirectory(tempDir);
        } catch (final IOException e) {
          this.logger.error("Failed to delete temp directory.", e);
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
    if (files.size() != 0) {
      FileUtils.copyFile(files.get(0), flowFile);
    } else {
      logger.error("can not found " + source + " file at " + this.execDir.getAbsoluteFile());
      throw new Exception("can not found " + source + " file at " + this.execDir.getAbsoluteFile());
    }

    return flowFile;
  }

  @SuppressWarnings("FutureReturnValueIgnored")
  private void runExecutableNode(final ExecutableNode node)
          throws IOException, InterruptedException {
    // Collect output props from the job's dependencies.
    prepareJobProperties(node);
    logger.info("node[{}] status: {} -> {}", node.getId(), node.getStatus(), Status.QUEUED);
    node.setStatus(Status.QUEUED);

    final JobRunner runner = createJobRunner(node);
    String parentFlowId = null;
    if (node.getParentFlow() != null) {
      // 子工作流并发限制
      parentFlowId = node.getParentFlow().getFlowId();
      if (subflowNumJobLimit.containsKey(parentFlowId)
              && subflowNumJobLimit.get(parentFlowId) > 0
              && subflowNumJobLimit.get(parentFlowId) < this.numJobThreads) {
        logger.info("Starting to check the num of job for subflow {}, current {} max {}",
                parentFlowId, subflowJobCount.get(parentFlowId),
                subflowNumJobLimit.get(parentFlowId));

        if (subflowJobCount.get(parentFlowId).get() >= subflowNumJobLimit.get(
                parentFlowId)) {
          // 不能执行，放入队列中
          logger.info("The number of job {} reach max {} for flow {}, waiting ...",
                  subflowJobCount.get(parentFlowId), subflowNumJobLimit.get(parentFlowId),
                  parentFlowId);
          this.subflowJobRunnerQueue.get(parentFlowId).offer(runner);
          return;
        }

      }
    }

    this.logger.info("Submitting job '" + node.getNestedId() + "' to run. READY -> QUEUED");
    try {
      // FIXME Submit datachecker and eventchecker tasks to the executorServiceForCheckers thread pool to run.
      if ("datachecker".equals(node.getType()) || "eventchecker".equals(node.getType())) {
        initCheckExecutors();
        this.logger.info("active Checker thread size : {}/{}",
                executorServiceForCheckers.getActiveCount(),
                executorServiceForCheckers.getMaximumPoolSize());
        this.executorServiceForCheckers.execute(runner);
      } else if ("linkis".equals(node.getType())) {
        initLinkisExecutors();
        this.logger.info("active Linkis thread size : {}/{}",
                executorServiceForLinkis.getActiveCount(), executorServiceForLinkis.getMaximumPoolSize());
        String linkisType = node.getInputProps().getString("linkistype");
        node.setLinkisType(linkisType);
        this.executorServiceForLinkis.execute(runner);
      } else if ("signReceive".equals(node.getType())) {
        initReceiveSignExecutors();
        this.logger.info("active sign thread size : {}/{}",
                executorServiceForSign.getActiveCount(), executorServiceForSign.getMaximumPoolSize());
        this.executorServiceForSign.execute(runner);
      } else {
        initCommonExecutors();
        this.logger.info("active Common thread size: {}/{}",
                ((ThreadPoolExecutor) executorService).getActiveCount(),
                ((ThreadPoolExecutor) executorService).getMaximumPoolSize());
        this.executorService.execute(runner);
      }
      this.activeJobRunners.add(runner);

    } catch (Exception e) {
      this.logger.error("Can not submit job to run! ", e);
    }
  }


  private void initCommonExecutors() {
    if (this.executorService == null) {
      commonCMDLock.lock();
      try {
        if (this.executorService == null) {
          logger.info("create executorService. execId:" + execId);
          ThreadFactory threadFactory1 = new ThreadFactoryBuilder().setDaemon(true).build();
          this.executorService = new ThreadPoolExecutor(this.numJobThreads, this.numJobThreads,
                  0L, TimeUnit.MILLISECONDS,
                  new LinkedBlockingQueue<Runnable>(1024 * 100), threadFactory1);
        }
      } finally {
        commonCMDLock.unlock();
      }
    }
  }

  private void initCheckExecutors() {
    if (this.executorServiceForCheckers == null) {
      checkLock.lock();
      try {
        if (this.executorServiceForCheckers == null) {
          logger.info("create executorServiceForCheckers. execId:" + execId);
          ThreadFactory threadFactory2 = new ThreadFactoryBuilder().setDaemon(true).build();
          int size = azkabanProps.getInt("checkers.num.threads", 10);
          this.executorServiceForCheckers = new ThreadPoolExecutor(size, size,
                  0L, TimeUnit.MILLISECONDS,
                  new LinkedBlockingQueue<Runnable>(1024 * 100), threadFactory2);
        }
      } finally {
        checkLock.unlock();
      }
    }
  }

  private void initLinkisExecutors() {
    if (this.executorServiceForLinkis == null) {
      linkisLock.lock();
      try {
        if (this.executorServiceForLinkis == null) {
          logger.info("create executorServiceForLinkis. execId:" + execId);
          ThreadFactory threadFactory4 = new ThreadFactoryBuilder().setDaemon(true).build();
          int linkisThreadsNum = azkabanProps.getInt("linkis.num.threads", 40);
          this.executorServiceForLinkis = new ThreadPoolExecutor(linkisThreadsNum, linkisThreadsNum,
                  0L, TimeUnit.MILLISECONDS,
                  new LinkedBlockingQueue<>(1024 * 100), threadFactory4);
        }
      } finally {
        linkisLock.unlock();
      }
    }
  }

  private void initAsyncExecutors() {
    if (this.asyncKillPool == null) {
      asyncPoolLocker.lock();
      try {
        if (this.asyncKillPool == null) {
          logger.info("create executorServiceForAsync. execId:" + execId);
          ThreadFactory threadFactory4 = new ThreadFactoryBuilder().setDaemon(true).build();
          int asyncThreadsNum = azkabanProps.getInt("async.num.threads", 5);
          this.asyncKillPool = new ThreadPoolExecutor(asyncThreadsNum, asyncThreadsNum,
                  0L, TimeUnit.MILLISECONDS,
                  new LinkedBlockingQueue<>(1000), threadFactory4);
        }
      } finally {
        asyncPoolLocker.unlock();
      }
    }
  }

  private void initReceiveSignExecutors() {
    if (this.executorServiceForSign == null) {
      signReceiveLock.lock();
      try {
        if (this.executorServiceForSign == null) {
          logger.info("create executorServiceForSign. execId:" + execId);
          ThreadFactory threadFactory5 = new ThreadFactoryBuilder().setDaemon(true).build();
          this.executorServiceForSign = new ThreadPoolExecutor(signJobThreads, signJobThreads,
                  0L, TimeUnit.MILLISECONDS,
                  new LinkedBlockingQueue<Runnable>(1024 * 100), threadFactory5);
        }
      } finally {
        signReceiveLock.unlock();
      }
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
      this.logger.info("Condition not met, will to mark node {} to Cancelled", node.getId());
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
            && this.failureAction == ExecutionOptions.FailureAction.FINISH_CURRENTLY_RUNNING) {
      this.logger.info("This Flow Failed, failedAction is finishCurrent, will to mark node {} to Cancelled", node.getId());
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
      int lastExecId = (int) this.flow.getOtherOption().getOrDefault("lastExecId", -1);
      try {
        if (output == null && lastExecId != -1 && !this.flow.getLastNsWtss()) {
          String jobId = executableNode.getId();
          if (executableNode instanceof ExecutableFlowBase) {
            ExecutableFlowBase baseFlow = (ExecutableFlowBase) executableNode;
            jobId = baseFlow.getFlowId();
          }
          output = this.executorLoader.fetchExecutionJobOutputProps(lastExecId, jobId);
          logger.debug(jobId + ", output: " + output);
        }
      } catch (ExecutorManagerException e) {
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
                    this.jobtypeManager, this.jobhookManager, this.azkabanProps, this.alerterHolder,
                    this.projectLoader);
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

    jobRunner.setSubflowJobCount(this.subflowJobCount);

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

  public void pause(final String user, long timeoutMs) {
    synchronized (this.mainSyncObj) {
      if (!this.flowFinished) {
        this.maxPausedTime = timeoutMs;
        this.logger.info("Flow paused by " + user + ". If the pause is not cancelled after " + (double) this.maxPausedTime / 1000 / 60 + " minutes, the flow will automatically resume running.");
        this.flowPaused = true;
        logger.info("flow {} status: {} -> {}", this.flow.getPrintableId(":"),
                this.flow.getStatus(), Status.PAUSED);
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
   *
   * @param base
   * @param executableNode
   */
  private void resetFlowStatus(final ExecutableFlowBase base, final ExecutableNode executableNode) {
    int failedNodes = 0;
    for (ExecutableNode node : base.getExecutableNodes()) {
      if (node.getStatus().equals(Status.FAILED_WAITING) || node.getStatus().equals(Status.FAILED_FINISHING)) {
        if (!executableNode.getNestedId().equals(node.getNestedId())) {
          failedNodes++;
        }
      }
    }
    if (failedNodes == 0) {
      //将flow状态改为Running, 子flow有job没跑完，父级flow肯定还是running
      if (!base.getStatus().equals(Status.PAUSED)) {
        logger.info("resetFlowStatus: flow {} status: {} -> {}", base.getPrintableId(":"), base.getStatus(),
                Status.RUNNING);
        base.setStatus(Status.RUNNING);
      }
      if (base instanceof ExecutableFlow) {
        this.isFailedPaused = false;
      }
    }
    if (base.getParentFlow() != null) {
      resetFlowStatus(base.getParentFlow(), executableNode);
    }
  }

  /**
   * Skip the failed_waiting status job.
   *  TODO: 考虑将 failed_finishing 跳过失败任务合并
   *
   * @param skipFailedJobs
   */
  public Map<String, String> setSkipFailedJob(List<String> skipFailedJobs) {
    Map<String, String> retMap = new HashMap<>();
    if (this.flowKilled || this.flowFinished) {
      logger.warn("flow, with status: {}, has been killed or finished, can not set jobs",
              this.flow.getStatus());
      retMap.put("error", "作业流已被 kill 或已经结束，不能执行跳过任务。");
      return retMap;
    }
    synchronized (this.mainSyncObj) {
      String message = null;
      if (this.flowKilled || this.flowFinished) {
        logger.warn("flow, with status: {}, has been killed or finished, can not set jobs",
                this.flow.getStatus());
        retMap.put("error", "作业流已被 kill 或已经结束，不能执行跳过任务。");
        return retMap;
      }

      stopKillFlowTrigger();

      if (this.failureAction == FailureAction.FAILED_PAUSE
              || this.failureAction == FailureAction.FINISH_ALL_POSSIBLE) {
        List<String> nodes = new ArrayList<>();
        ConcurrentHashMap<String, ExecutableNode> skipJobsMap = new ConcurrentHashMap<>();
        for (String skipJobId : skipFailedJobs) {
          ExecutableNode skipNode = this.flow.getExecutableNodePath(skipJobId);
          skipJobsMap.put(skipNode.getNestedId(), skipNode);
        }

        for (ExecutableNode node : skipJobsMap.values()) {
          if (node.getStatus().equals(Status.FAILED_WAITING) || node.getStatus()
                  .equals(Status.FAILED)) {
            if (skipFailedJobs.size() == 1
                    && this.failureAction == FailureAction.FINISH_ALL_POSSIBLE) {
              // 针对于单个任务失败跳过

              stopKillFlowTrigger();

              //还原flow状态为Running
              resetFlowStatus(node.getParentFlow(), node);
              logger.info("setSkipFailedJob: node[{}] status: {} -> {}",
                      node.getPrintableId(":"),
                      node.getStatus(), Status.FAILED_SKIPPED);
              node.setStatus(Status.FAILED_SKIPPED);
              node.setUpdateTime(System.currentTimeMillis());
              resetReadyStatus(node);
              updateFlow();
              FlowRunner.this.finishedNodes.add(node);
              nodes.add(node.getNestedId());
            } else if (this.failureAction == FailureAction.FAILED_PAUSE) {
              // 针对于多个任务在失败暂停策略下的跳过

              stopKillFlowTrigger();
              //还原flow状态为Running
              resetFlowStatus(node.getParentFlow(), node);
              logger.info("skippedFailures: node[{}] status: {} -> {}",
                      node.getPrintableId(":"), node.getStatus(),
                      Status.FAILED_SKIPPED);
              node.setStatus(Status.FAILED_SKIPPED);
              node.setUpdateTime(System.currentTimeMillis());
              updateJob(node);
              updateFlow();
              this.finishedNodes.add(node);
              nodes.add(node.getNestedId());
              logger.info(String.format("job: %s, old status: %s -> new status %s.",
                      node.getPrintableId(":"), Status.FAILED_WAITING,
                      node.getStatus()));
            }
          } else {
            message =
                    "job: " + skipFailedJobs.get(0) + ", 状态为 " + node.getStatus()
                            + ", "
                            + "不是 FAILED_WAITING/FAILED，不能跳过执行。";
            logger.warn(message);
            retMap.put("error", message);
            return retMap;
          }
        }
        updateFlow();
        for (String nodeName : nodes) {
          this.failedNodes.remove(nodeName);
        }
        interrupt();
      } else {
        message =
                "工作流设置的失败策略为" + FlowRunner.this.failureAction + "，不能进行失败跳过";
        logger.warn(message);
        retMap.put("error", message);
        return retMap;
      }
      retMap.put("info", "已执行跳过所有已选择的 job");
      return retMap;
    }
  }

  private void resetReadyStatus(ExecutableNode node) {
    logger.info("reset node {} status to Ready.", node.getId());
    ExecutableFlowBase parentFlow = node.getParentFlow();
    if (node.getStatus().equals(Status.CANCELLED)) {
      node.setStatus(Status.READY);
      node.setDependentlinkFailed(false);
    }
    Set<String> outNodes = node.getOutNodes();
    if (!outNodes.isEmpty()) {
      for (String outNode : outNodes) {
        resetReadyStatus(parentFlow.getExecutableNode(outNode));
      }
    } else {
      if (!(parentFlow instanceof ExecutableFlow)) {
        outNodes = parentFlow.getOutNodes();
        parentFlow = parentFlow.getParentFlow();
        for (String outNode : outNodes) {
          resetReadyStatus(parentFlow.getExecutableNode(outNode));
        }
      }
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

  private void reStart() {
    this.flowPaused = false;
    if (this.flowFailed || this.isFailedPaused) {
      logger.info("reStart: flow {} status: {} -> {}", this.flow.getPrintableId(":"), this.flow.getStatus(),
              Status.FAILED_FINISHING);
      this.flow.setStatus(Status.FAILED_FINISHING);
    } else if (this.flowKilled) {
      logger.info("reStart: flow {} status: {} -> {}", this.flow.getPrintableId(":"), this.flow.getStatus(),
              Status.KILLING);
      this.flow.setStatus(Status.KILLING);
    } else {
      logger.info("reStart: flow {} status: {} -> {}", this.flow.getPrintableId(":"), this.flow.getStatus(),
              Status.RUNNING);
      this.flow.setStatus(Status.RUNNING);
    }
  }

  /**
   * This method can only be used to kill the workflow
   * when the workflow has been in the killing state and cannot be terminated.
   *
   * @param user
   */
  public void superKill(String user) {
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
      logger.info("superKill: flow {} status: {} -> {}", this.flow.getPrintableId(":"), this.flow.getStatus(),
              Status.KILLED);
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
            logger.info("superKill: node[{}] status: {} -> {}", node.getId(), node.getStatus(),
                    Status.CANCELLED);
            node.setStatus(Status.CANCELLED);
            break;
          default:
            node.setEndTime(time);
            node.setUpdateTime(time);
            logger.info("superKill: node[{}] status: {} -> {}", node.getId(), node.getStatus(), Status.KILLED);
            node.setStatus(Status.KILLED);
            break;
        }
      }
      this.flowFinished = true;
      FlowRunner.runningNoCheckerSumMap.get(this.flow.getProjectId()).remove(this.execId);
      this.executorLoader.updateHoldBatchNotResumeByExecId(this.execId);
      interrupt();
    }
  }

  public void kill(final String user) {
    this.logger.info("Flow killed by " + user);
    kill();
  }


  private void killAllJobsAsync(Set<JobRunner> jobRunners) {
    initAsyncExecutors();
    List<CompletableFuture<Void>> futures = jobRunners.stream()
            .map(runner -> CompletableFuture.runAsync(() -> {
              this.logger.info("async killing job:{}", runner.getNode().getNestedId());
              runner.kill();
              ExecutableNode node = runner.getNode();
              if (node.getStatus().equals(Status.FAILED_WAITING)) {
                this.logger.info("node[{}] status: {} -> {}", node.getId(), node.getStatus(), Status.KILLED);
                node.setStatus(Status.KILLED);
                node.setUpdateTime(System.currentTimeMillis());
              }
            }, this.asyncKillPool)).collect(Collectors.toList());
    CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
    this.logger.info("All jobs have been killed.");
  }

  public void kill() {
    synchronized (this.mainSyncObj) {
      if (this.flowKilled) {
        return;
      }
      Status lastStatus = this.flow.getStatus();
      this.logger.info("Kill has been called on flow " + this.execId);
      logger.info("kill: flow {} status: {} -> {}", this.flow.getPrintableId(":"), this.flow.getStatus(),
              Status.KILLING);
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
      if (this.asyncKillJobEnabled) {
        killAllJobsAsync(this.activeJobRunners);
      } else {
        for (final JobRunner runner : this.activeJobRunners) {
          this.logger.info("killing job:" + runner.getNode().getNestedId());
          runner.kill();
          ExecutableNode node = runner.getNode();
          // FIXME New function. When the job stream is terminated, the FAILED_WAITING state job needs to be set to the killed state.
          if (node.getStatus().equals(Status.FAILED_WAITING)) {
            this.logger.info("node[{}] status: {} -> {}", node.getId(), node.getStatus(),
                    Status.KILLED);
            node.setStatus(Status.KILLED);
            node.setUpdateTime(System.currentTimeMillis());
          }
        }
      }
      /**
       * 因为设置了失败暂停的时候没有将执行失败的节点添加到finishedNodes里，导致前台点击kill的时候失败了
       * 现在kill的时候将失败节点重新添加到finishedNodes里
       */
      if (this.failureAction == FailureAction.FAILED_PAUSE) {
        for (ExecutableNode failedNode : this.failedNodes.values()) {
          this.logger.info(String.format("change job: %s , old status %s", failedNode.getNestedId(), failedNode.getStatus()));
          if (failedNode.getStatus().equals(Status.FAILED_WAITING)) {
            logger.info("kill: node[{}] status: {} -> {}", failedNode.getId(), failedNode.getStatus(),
                    Status.KILLED);
            failedNode.setStatus(Status.KILLED);
            failedNode.setUpdateTime(System.currentTimeMillis());
          }
          this.finishedNodes.add(failedNode);
        }
      }
      if (Status.FAILED.equals(lastStatus)) {
        logger.info("kill: flow {} status: {} -> {}", this.flow.getPrintableId(":"),
                this.flow.getStatus(), Status.KILLED);
        this.flow.setStatus(Status.KILLED);
      }
      updateFlow();
      FlowRunner.runningNoCheckerSumMap.get(this.flow.getProjectId()).remove(this.execId);
      this.executorLoader.updateHoldBatchNotResumeByExecId(this.execId);
    }
    interrupt();
  }

  public void retryFailures(final String user, List<String> retryFailedJobs) {
    synchronized (this.mainSyncObj) {
      this.logger.info("Retrying failures invoked by " + user);
      // FIXME Add judgment. If the job stream has been completed, you cannot retry execution.
      if (this.flowFinished) {
        this.logger.info("this flow was finished.");
        return;
      }
      // FIXME If you click to retry the task, you need to cancel the thread that timed out the job stream.
      stopKillFlowTrigger();

      if (retryFailedJobs != null) {
        logger.info("all retry job list: " + retryFailedJobs);
        for (String job : retryFailedJobs) {
          ExecutableNode node = this.flow.getExecutableNodePath(job);
          this.putRetryJobs(retryFailedJobs, job, node);
        }
        logger.info("waiting retry jobs: " + this.retryJobs.toString());
      }

      this.isRetryFailedJobs = true;
      interrupt();
    }
  }

  private void stopKillFlowTrigger() {
    if (killFlowTrigger != null && killFlowTrigger.isAlive()) {
      killFlowTrigger.stopKillFLowTrigger();
      this.isTriggerStarted = false;
      logger.info("stop killing workflow.");
    }
  }

  private void setDependentlinkFailed(ExecutableNode node, boolean flag) {
    if (FlowLoaderUtils.isAzkabanFlowVersion20(this.flow.getAzkabanFlowVersion())) {
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
      logger.info("node {} isDependentLinkFailed: {} -> {} because of resetting failed state",
              node.getPrintableId(":"), node.isDependentlinkFailed(), false);
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
      } else if (node.getStatus() == Status.FAILED_RETRYING) {
        continue;
      } else if (node.getStatus() == Status.SKIPPED) {
        logger.info("resetFailedState: node[{}] status: {} -> {}", node.getId(), node.getStatus(), Status.DISABLED);
        node.setStatus(Status.DISABLED);
        node.setEndTime(-1);
        node.setStartTime(-1);
        node.setUpdateTime(currentTime);
      } else if (node instanceof ExecutableFlowBase) {
        final ExecutableFlowBase base = (ExecutableFlowBase) node;
        switch (base.getStatus()) {
          case CANCELLED:
            logger.info("resetFailedState: node[{}] status: {} -> {}", node.getId(), node.getStatus(), Status.READY);
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
        logger.info("resetFailedState: node[{}] status: {} -> {}", node.getId(), node.getStatus(), Status.READY);
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
      logger.info("resetFailedState: flow {} status: {} -> {}", flow.getPrintableId(":"), flow.getStatus(),
              Status.READY);
      flow.setStatus(Status.READY);
    } else {
      logger.info("resetFailedState: flow {} status: {} -> {}", flow.getPrintableId(":"), flow.getStatus(),
              Status.RUNNING);
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
    if (this.flowRunnerThread != null) {
      this.logger.info("FlowRunner thread-{} is interrupting", this.flowRunnerThread.getName());
      this.flowRunnerThread.interrupt();
    }

    if (this.subflowJobConsumerThread != null) {
      this.logger.info("SubflowJobConsumer thread-{} is interrupting",
              this.subflowJobConsumerThread.getName());
      this.subflowJobConsumerThread.interrupt();
    }
  }

  public boolean isKilled() {
    return this.flowKilled;
  }

  public boolean isFlowFinished() {
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


  public void dealFinishedEvent(Event event) {
    final JobRunner jobRunner = (JobRunner) event.getRunner();
    final ExecutableNode node = jobRunner.getNode();
    final EventData eventData = event.getData();
    if (Status.FAILED_WAITING.equals(node.getStatus())) {
      failWaitingSet.add(node.getId());
    }

    final long seconds = (node.getEndTime() - node.getStartTime()) / 1000;
    synchronized (this.mainSyncObj) {
      this.logger.info("Job " + eventData.getNestedId() + " finished with status "
              + eventData.getStatus() + " in " + seconds + " seconds");
      this.decrementRunning(node);
      // Cancellation is handled in the main thread, but if the flow is
      // paused, the main thread is paused too.
      // This unpauses the flow for cancellation.
      if (this.flowPaused && eventData.getStatus() == Status.FAILED
              && this.failureAction == FailureAction.CANCEL_ALL) {
        this.flowPaused = false;
      }
      this.finishedNodes.add(node);
      this.retryJobs.remove(node.getNestedId());
      this.activeJobRunners.remove(jobRunner);
      // FIXME Added task processing for FAILED_WAITING status.
      failedWaitingJobHandle(node);
      setUserDefined(node);
      node.getParentFlow().setUpdateTime(System.currentTimeMillis());
      interrupt();
      fireEventListeners(event);
    }
    // FIXME Added sla alarms for job and sub-job streams.
    handelJobFinishAlter(jobRunner);
  }

  private void setUserDefined(ExecutableNode node) {
    if (node.getOutputProps() != null && node.getOutputProps().containsKey(USER_DEFINED_PARAM)) {
      FlowRunner.this.flow.setComment(node.getOutputProps().getString(USER_DEFINED_PARAM, ""));
    }
  }

  private void failedWaitingJobHandle(ExecutableNode node) {
    try {
      if (node.getStatus().equals(Status.FAILED_WAITING) && FlowRunner.this.failureAction == FailureAction.FAILED_PAUSE) {
        FlowRunner.this.isFailedPaused = true;
        FlowRunner.this.failedNodes.put(node.getNestedId(), node);
        if (!FlowRunner.this.isTriggerStarted) {
          killFlowTrigger = new KillFlowTrigger(FlowRunner.this, FlowRunner.this.logger);
          killFlowTrigger.start();
          FlowRunner.this.isTriggerStarted = true;
        }
        ExecutionControllerUtils.handleFlowPausedAlert(FlowRunner.this.flow, alerterHolder, node.getNestedId());
      } else if (Status.isStatusSucceeded(node.getStatus()) && FlowRunner.this.failureAction == FailureAction.FAILED_PAUSE) {
        FlowRunner.this.failedNodes.remove(node.getNestedId());
      }
    } catch (RuntimeException re) {
      FlowRunner.this.logger.error("add failed node failed", re);
    }
  }

  /**
   * job or subflow execute timeout alarm.
   *
   * @param jobRunner
   * @param eventData
   */
  public void handleJobAndEmbeddedFlowExecTimeoutAlter(final JobRunner jobRunner, final EventData eventData) {
    // add job level checker
    final TriggerManager triggerManager = ServiceProvider.SERVICE_PROVIDER
            .getInstance(TriggerManager.class);

    // 获取非子工作流的job超时告警job名
    List<String> jobAlerts = SlaOption.getJobLevelSLAOptions(FlowRunner.this.flow).stream()
            .map(x -> (String) x.getInfo().get("JobName")).collect(Collectors.toList());
    String parentFlow = jobRunner.getNode().getParentFlow().getNestedId();
    FlowRunner.this.logger.info("alert jobs is " + jobAlerts.toString() + " job name: " + eventData.getNestedId() + " parent flow " + parentFlow);
    // job超时告警
    if (jobAlerts.contains(eventData.getNestedId())) {
      triggerManager.addTrigger(FlowRunner.this.flow.getExecutionId(), SlaOption.getJobLevelSLAOptions(FlowRunner.this.flow), eventData.getNestedId());
    }
    // 获取子flow
    List<String> embeddedFlowAlerts = SlaOption.getJobLevelSLAOptions(FlowRunner.this.flow).stream()
            .filter(x -> x.getInfo().getOrDefault(SlaOption.INFO_EMBEDDED_ID, null) != null)
            .map(x -> (String) x.getInfo().get("JobName")).collect(Collectors.toList());
    // 子flow超时告警
    for (String embeddedFlow : embeddedFlowAlerts) {
      if (parentFlow.equals(embeddedFlow) || parentFlow.startsWith(embeddedFlow + ":")) {
        if (embeddedFlowTimeOutSlaFlag.get(embeddedFlow) == null) {
          triggerManager.addTrigger(FlowRunner.this.flow.getExecutionId(), SlaOption.getJobLevelSLAOptions(FlowRunner.this.flow), embeddedFlow);
          embeddedFlowTimeOutSlaFlag.put(embeddedFlow, true);
        }
      }
    }
  }

  /**
   * subflow sla alarm
   *
   * @param mailAlerter
   * @param slaJobOptionsList
   */
  private void embeddedFlowAlter(Alerter mailAlerter, ExecutableNode node, List<SlaOption> slaJobOptionsList) {

    // 过滤出所有子flow类型的告警
    List<SlaOption> embeddedFlowSlaOptions = slaJobOptionsList.stream()
            .filter(x -> x.getInfo().getOrDefault(SlaOption.INFO_EMBEDDED_ID, null) != null)
            .collect(Collectors.toList());

    String parentFlow = node.getParentFlow().getNestedId();
    try {
      for (SlaOption sla : embeddedFlowSlaOptions) {
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
        if (node.getId().equals(sla.getInfo().get(SlaOption.INFO_EMBEDDED_ID))
                && Status.isSucceeded(node.getStatus())
                && parentFlow.equals(slaJobName)
                && (SlaOption.TYPE_JOB_SUCCESS_EMAILS.equals(sla.getType()) || SlaOption.TYPE_JOB_FINISH_EMAILS.equals(sla.getType()))) {
          logger.info("embeddedFlowSla , " + 1);
          if (embeddedFlowSlaFlag.getOrDefault(slaJobName + "-" + sla.getType(), null) == null) {
            logger.info("embeddedFlowSla , " + 2);
            mailAlerter.alertOnFinishSla(sla, this.flow);
            // 标记已发送过告警
            embeddedFlowSlaFlag.put(slaJobName + "-" + sla.getType(), true);
          }
        }
        // 子flow中的某个job失败 需要立刻发出其对应的子flow和上上级子flow的失败或者完成告警
        if (node.getStatus() == Status.FAILED || node.getStatus() == Status.FAILED_WAITING
                && (SlaOption.TYPE_JOB_FAILURE_EMAILS.equals(sla.getType()) || SlaOption.TYPE_JOB_FINISH_EMAILS.equals(sla.getType()))) {
          logger.info("embeddedFlowSla , " + 3);
          // a:b:c 包含 a:b ，规避abc:b:c 包含 ab，
          if (parentFlow.equals(slaJobName) || parentFlow.startsWith(slaJobName + ":")) {
            logger.info("embeddedFlowSla , " + 4);
            if (embeddedFlowSlaFlag.getOrDefault(slaJobName + "-" + sla.getType(), null) == null) {
              logger.info("embeddedFlowSla , " + 5);
              mailAlerter.alertOnFinishSla(sla, this.flow);
              embeddedFlowSlaFlag.put(slaJobName + "-" + sla.getType(), true);
            }
          }
        }
      }
    } catch (Exception e) {
      logger.error("发送子flow告警失败: ", e);
    }
  }

  /**
   * Job execution completion alarm.
   *
   * @param runner
   */
  private void handelJobFinishAlter(JobRunner runner) {
    final ExecutableNode node = runner.getNode();
    logger.info("SLA 定时任务告警处理开始,当前节点状态为 {} ", node.getStatus());

    Alerter mailAlerter = ServiceProvider.SERVICE_PROVIDER.getInstance(AlerterHolder.class).get("email");
    if (mailAlerter == null) {
      logger.warn("找不到告警插件.");
      return;
    }

    ExecutableFlow exflow = runner.getNode().getExecutableFlow();
    List<SlaOption> slaOptionList = exflow.getSlaOptions();

    // 过滤出所有job类型的告警
    List<SlaOption> slaJobOptionsList = SlaOption.getAllJobSlaAlertOptions(slaOptionList);

    // 子flow告警处理
    embeddedFlowAlter(mailAlerter, node, slaJobOptionsList);

    // 从job告警列表中获取所有告警的job的名字去重
    Set<String> slaJobAlterName = SlaOption.getAllJobSlaAlertJobName(slaJobOptionsList);
    // 判断当前job是否需要告警
    if (!slaJobAlterName.contains(node.getNestedId())) {
      return;
    }
    if (null != slaJobOptionsList) {
      try {
        for (SlaOption slaOption : slaJobOptionsList) {
          // 判断当前告警信息的job名是否与等于该job名字
          if (!((String) slaOption.getInfo().get(SlaOption.INFO_JOB_NAME)).equals(node.getNestedId())) {
            continue;
          }
          logger.info("1.任务Job: " + node.getNestedId() + " 开始发送 告警" + " job status is " + node.getStatus());
          if (SlaOption.TYPE_JOB_FAILURE_EMAILS.equals(slaOption.getType())
                  && (node.getStatus().equals(Status.FAILED) || node.getStatus().equals(Status.FAILED_WAITING))) {
            logger.info("任务Job 执行失败 开始发送 告警");
            mailAlerter.alertOnFinishSla(slaOption, flow);
          } else if (SlaOption.TYPE_JOB_SUCCESS_EMAILS.equals(slaOption.getType()) && Status.isSucceeded(node.getStatus())) {
            logger.info("任务Job 执行成功 开始发送 告警");
            mailAlerter.alertOnFinishSla(slaOption, flow);
          } else if (SlaOption.TYPE_JOB_FINISH_EMAILS.equals(slaOption.getType())) {
            logger.info("任务Job 执行完成 开始发送 告警");
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
   *
   * @param node
   * @return
   * @throws IOException
   */
  private boolean runReadyJobByPriority(final ExecutableNode node, final String preNodeId) throws Exception {
    if (Status.isStatusFinished(node.getStatus())
            || Status.isStatusRunning(node.getStatus()) || Status.FAILED_WAITING.equals(node.getStatus())) {
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
        logger.info("flow {} status: {} -> {}", flow.getPrintableId(":"), flow.getStatus(),
                Status.RUNNING);
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

    logger.info("runExecutableNodeByPriority: node[{}] status: {} -> {}", node.getId(), node.getStatus(), Status.QUEUED);
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

  private boolean isSkippedDay(ExecutableNode node) {
    Map<String, String> jobCronExpression = ((Map<String, String>) this.flow.getOtherOption().get("job.cron.expression"));
    if (!(node instanceof ExecutableFlowBase) && jobCronExpression != null) {
      String cronExpresion = getJobCron(node, jobCronExpression);
      if (StringUtils.isNotBlank(cronExpresion)) {
        if (flow.getFlowType() != 2 && this.flow.getSubmitTime() != -1 && Utils.checkDateTime(this.flow.getSubmitTime(), 1, cronExpresion)) {
          logger.info("job: " + node.getNestedId() + ", cron:" + cronExpresion + ", skiped.");
          return true;
        }
        if (flow.getFlowType() == 2 && Utils.checkDateTime(Long.valueOf(this.flow.getRepeatOption().get("startTimeLong")), 1, cronExpresion)) {
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
    // 每次进入该方法时需要初始化 isDependentlinkFailed 属性
    setDependentlinkFailed(node, false);
    logger.info(
            "node {} isDependentLinkFailed: {} -> {} because it starts to getImpliedStatus. ",
            node.getPrintableId(":"), node.isDependentlinkFailed(), false);
    // FIXME When the execution of a dependent job fails and the current job to be executed is disabled, it will cause the job stream to fail and retry. The task cannot be killed, and the task is always in the killing state. The current solution is that if the execution of the dependent job fails, the entire execution link All jobs are canceled, and the status of the disabled job is skipped after execution.
    if (node.getCondition() == null && node.getConditionOnJobStatus().equals(ConditionOnJobStatus.ALL_SUCCESS)) {
      for (final String dependency : node.getInNodes()) {
        final ExecutableNode dependencyNode = flow.getExecutableNode(dependency);
        final Status depStatus = dependencyNode.getStatus();
        logger.info(
                "depNode:" + dependencyNode.getNestedId() + " status: " + depStatus.toString()
                        + " isDependentLinkFailed: " + dependencyNode.isDependentlinkFailed());
        if (!(dependencyNode instanceof ExecutableFlowBase || dependencyNode.isFinished())
                || !Status.isStatusFinished(depStatus)) {
          logger.info("depNode: {} not finish", dependencyNode.getNestedId());
          return null;
        } else if (depStatus == Status.CANCELLED
                || depStatus == Status.KILLED) {
          // We propagate failures as KILLED states.
          shouldKill = true;
          setDependentlinkFailed(node, true);
          logger.info("node {} isDependentLinkFailed: {} -> {} because dependency node{} "
                          + "is CANCELLED/KILLED. ", node.getPrintableId(":"),
                  node.isDependentlinkFailed(), true, dependencyNode.getPrintableId(":"));
        } else if (depStatus == Status.FAILED) {
          // 设置执行跳过某个job
          List<String> skipFaultJobList = (ArrayList) faultOption.get("jobSkipFailedOptions");
          if (this.flow.getFailedSkipedAllJobs() || (null != skipFaultJobList && (
                  skipFaultJobList.contains(dependencyNode.getNestedId()) || skipFaultJobList
                          .contains(dependencyNode.getId()))) || validJobInSkipFLow(node,
                  skipFaultJobList)) {
            logger.info("用户已设置错误跳过策略，跳过错误状态 Job:" + dependencyNode.getNestedId() + " 继续执行。");
            jobSkipFailedOptions = true;
          } else {
            setDependentlinkFailed(node, true);
            logger.info(
                    "node {} isDependentLinkFailed: {} -> {} because dependency node{} "
                            + "is FAILED. ", node.getPrintableId(":"),
                    node.isDependentlinkFailed(), true, dependencyNode.getPrintableId(":"));
            shouldKill = true;
          }
        }
        if (dependencyNode.isDependentlinkFailed()) {
          shouldKill = true;
          setDependentlinkFailed(node, true);
          logger.info("node {} isDependentLinkFailed: {} -> {} because dependency node{} "
                          + "isDependentLinkFailed: true. ", node.getPrintableId(":"),
                  node.isDependentlinkFailed(), true, dependencyNode.getPrintableId(":"));
        }
      }
    }

    if (!jobSkipFailedOptions) {
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
            || node.getStatus() == Status.SKIPPED || isSkippedDay(node) || node.isAutoDisabled()) {
      logger.info("set job: " + node.getNestedId() + " skipped");
      return Status.SKIPPED;
    }

    // If the flow has failed, and we want to finish only the currently running
    // jobs, we just
    // kill everything else. We also kill, if the flow has been cancelled.
    if (this.flowFailed
            && this.failureAction == ExecutionOptions.FailureAction.FINISH_CURRENTLY_RUNNING) {
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

    //获取用户设置的错误跳过job列表
    List<String> skipFaultJobList = (ArrayList) this.flow.getOtherOption().get("jobSkipFailedOptions");
    if (this.flow.getFailedSkipedAllJobs() || (null != skipFaultJobList && (
            skipFaultJobList.contains(node.getNestedId()) || skipFaultJobList.contains(node.getId())))
            || validJobInSkipFLow(node, skipFaultJobList)) {
      return false;
    } else {
      return true;
    }

  }

  final private static Pattern PRIORITY_LEV_PATTERN = Pattern.compile("[1-5]*");

  private boolean verifyPriority(final String priorityLev) {
    //校验通过
    if (PRIORITY_LEV_PATTERN.matcher(priorityLev).matches()) {
      return false;
    }
    return true;
  }

  private void updateFailWaitingJob(ExecutableNode node) {
    if (node instanceof ExecutableFlowBase) {
      for (ExecutableNode chiledNode : ((ExecutableFlowBase) node).getExecutableNodes()) {
        updateFailWaitingJob(chiledNode);
      }
    } else {
      try {
        if (failWaitingSet.contains(node.getId())) {
          executorLoader.updateExecutableNodeStatus(node);
        }
      } catch (ExecutorManagerException e) {
        logger.error("update job status error", e);
      }
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

  private String getJobCron(ExecutableNode node, Map<String, String> cronMap) {
    if (MapUtils.isEmpty(cronMap) || node == null) {
      return null;
    }
    String cron;
    if (node instanceof ExecutableFlowBase) {
      cron = cronMap.get("subflow-" + ((ExecutableFlowBase) node).getFlowId());
    } else {
      cron = cronMap.get(node.getNestedId());
    }
    if (StringUtils.isNotEmpty(cron)) {
      return cron;
    } else {
      return getJobCron(node.getParentFlow(), cronMap);
    }
  }

  private void getDepend4Job(ExecutableNode node, List<String> retryList, Set<String> dependList) {
    if (node instanceof ExecutableFlowBase) {
      for (String endNode : ((ExecutableFlowBase) node).getEndNodes()) {
        getDepend4Job(((ExecutableFlowBase) node).getExecutableNode(endNode), retryList,
                dependList);
      }
    } else {
      if (node.getInNodes() == null || node.getInNodes().isEmpty()) {
        if (node.getParentFlow().getInNodes() != null) {
          for (String inNode : node.getParentFlow().getInNodes()) {
            ExecutableNode inNodeOut = node.getParentFlow().getParentFlow()
                    .getExecutableNode(inNode);
            if (retryList.contains(inNodeOut.getNestedId())) {
              dependList.add(inNodeOut.getNestedId());
            }
            getDepend4Job(inNodeOut, retryList, dependList);
          }
        }
      } else {
        for (String inNode : node.getInNodes()) {
          ExecutableNode inNodeIn = node.getParentFlow().getExecutableNode(inNode);
          if (retryList.contains(inNodeIn.getNestedId())) {
            dependList.add(inNodeIn.getNestedId());
          }
          getDepend4Job(inNodeIn, retryList, dependList);
        }
      }

    }
  }

  public void changePaused2SystemPaused() {
    synchronized (this.mainSyncObj) {
      this.mainSyncObj.notifyAll();
    }
  }

  public void changePreparing2SystemPaused() {
    synchronized (this.mainSyncObj) {
      if (Status.PREPARING.equals(this.flow.getStatus())) {
        logger.info("changePreparing2SystemPaused: flow {} status: {} -> {}", this.flow.getPrintableId(":"),
                this.flow.getStatus(), Status.SYSTEM_PAUSED);
        this.flow.setStatus(Status.SYSTEM_PAUSED);
        updateFlow();
      }
    }
  }

  public void resumeBatchFlow() {
    String key = (this.lastBatchId + "-" + this.flow.getExecutionId()).intern();
    synchronized (key) {
      key.notifyAll();
    }
  }

}
