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

package azkaban.execapp;

import static azkaban.Constants.DEFAULT_FLOW_PAUSED_MAX_TIME;
import static azkaban.Constants.FLOW_PAUSED_MAX_TIME_MS;
import static java.util.Objects.requireNonNull;

import azkaban.Constants;
import azkaban.Constants.ConfigurationKeys;
import azkaban.ServiceProvider;
import azkaban.alert.Alerter;
import azkaban.batch.HoldBatchAlert;
import azkaban.batch.HoldBatchContext;
import azkaban.batch.HoldBatchOperate;
import azkaban.event.Event;
import azkaban.event.EventListener;
import azkaban.execapp.event.AbstractFlowWatcher;
import azkaban.execapp.event.LocalFlowWatcher;
import azkaban.execapp.event.RemoteFlowWatcher;
import azkaban.execapp.metric.NumFailedFlowMetric;
import azkaban.executor.AlerterHolder;
import azkaban.executor.ExecutableFlow;
import azkaban.executor.ExecutionControllerUtils;
import azkaban.executor.ExecutionCycleDao;
import azkaban.executor.ExecutionOptions;
import azkaban.executor.Executor;
import azkaban.executor.ExecutorLoader;
import azkaban.executor.ExecutorManagerException;
import azkaban.executor.Status;
import azkaban.jobhook.JobHookManager;
import azkaban.jobtype.JobTypeManager;
import azkaban.jobtype.JobTypeManagerException;
import azkaban.metric.MetricReportManager;
import azkaban.metrics.CommonMetrics;
import azkaban.project.ProjectLoader;
import azkaban.project.ProjectWhitelist;
import azkaban.project.ProjectWhitelist.WhitelistType;
import azkaban.server.AbstractAzkabanServer;
import azkaban.sla.SlaOption;
import azkaban.spi.AzkabanEventReporter;
import azkaban.spi.EventType;
import azkaban.storage.StorageManager;
import azkaban.utils.FileIOUtils;
import azkaban.utils.FileIOUtils.JobMetaData;
import azkaban.utils.FileIOUtils.LogData;
import azkaban.utils.JSONUtils;
import azkaban.utils.Pair;
import azkaban.utils.Props;
import azkaban.utils.ThreadPoolExecutingListener;
import azkaban.utils.TrackingThreadPool;
import azkaban.utils.UndefinedPropertyException;
import com.codahale.metrics.Timer;
import com.google.common.base.Preconditions;
import com.google.gson.JsonObject;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.lang.Thread.State;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import javax.annotation.Nullable;
import javax.inject.Inject;
import javax.inject.Singleton;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.FilenameUtils;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang.ArrayUtils;
import org.apache.commons.lang.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Execution manager for the server side execution.
 *
 * When a flow is submitted to FlowRunnerManager, it is the {@link Status#PREPARING} status. When a
 * flow is about to be executed by FlowRunner, its status is updated to {@link Status#RUNNING}
 *
 * Two main data structures are used in this class to maintain flows.
 *
 * runningFlows: this is used as a bookkeeping for submitted flows in FlowRunnerManager. It has
 * nothing to do with the executor service that is used to execute the flows. This bookkeeping is
 * used at the time of canceling or killing a flow. The flows in this data structure is removed in
 * the handleEvent method.
 *
 * submittedFlows: this is used to keep track the execution of the flows, so it has the mapping
 * between a Future<?> and an execution id. This would allow us to find out the execution ids of the
 * flows that are in the Status.PREPARING status. The entries in this map is removed once the flow
 * execution is completed.
 */
@Singleton
public class FlowRunnerManager implements EventListener,
        ThreadPoolExecutingListener {

  private static final Logger logger = LoggerFactory.getLogger(FlowRunnerManager.class);

  private static final String EXECUTOR_USE_BOUNDED_THREADPOOL_QUEUE = "executor.use.bounded.threadpool.queue";
  private static final String EXECUTOR_THREADPOOL_WORKQUEUE_SIZE = "executor.threadpool.workqueue.size";
  private static final String EXECUTOR_FLOW_THREADS = "executor.flow.threads";
  private static final String FLOW_NUM_JOB_THREADS = "flow.num.job.threads";

  private static final String SIGN_NUM_JOB_THREADS = "sign.num.job.threads";

  // recently finished secs to clean up. 1 minute
  private static final int RECENTLY_FINISHED_TIME_TO_LIVE = 60 * 1000;

  private static final int DEFAULT_NUM_EXECUTING_FLOWS = 30;
  private static final int DEFAULT_FLOW_NUM_JOB_TREADS = 10;

  private static final int DEFAULT_SIGN_NUM_JOB_TREADS = 5;
  private static final int DEFAULT_POLLING_INTERVAL_MS = 1000;

  // this map is used to store the flows that have been submitted to
  // the executor service. Once a flow has been submitted, it is either
  // in the queue waiting to be executed or in executing state.
  private final Map<Future<?>, Integer> submittedFlows = new ConcurrentHashMap<>();
  private final Map<Integer, FlowRunner> runningFlows = new ConcurrentHashMap<>();
  // keep track of the number of flow being setup({@link createFlowRunner()})
  private final AtomicInteger preparingFlowCount = new AtomicInteger(0);
  private final Map<Integer, ExecutableFlow> recentlyFinishedFlows = new ConcurrentHashMap<>();
  private final TrackingThreadPool executorService;
  private final CleanerThread cleanerThread;
  private final ExecutorLoader executorLoader;
  private final ProjectLoader projectLoader;
  private final JobTypeManager jobtypeManager;
  private final JobHookManager jobhookManager;
  private final FlowPreparer flowPreparer;
  private final TriggerManager triggerManager;
  private final AlerterHolder alerterHolder;
  private final AzkabanEventReporter azkabanEventReporter;
  private final ExecutionCycleDao executionCycleDao;
  private Props azkabanProps;
  private final File executionDirectory;
  private final File projectDirectory;
  private final Object executionDirDeletionSync = new Object();
  private final CommonMetrics commonMetrics;

  private final int numThreads;
  private final int numJobThreadPerFlow;

  private final int signJobThreadsPerFlow;
  // We want to limit the log sizes to about 20 megs
  private final String jobLogChunkSize;
  private final int jobLogNumFiles;
  // If true, jobs will validate proxy user against a list of valid proxy users.
  private final boolean validateProxyUser;
  private PollingService pollingService;
  private int threadPoolQueueSize = -1;
  private Props globalProps;
  private long lastCleanerThreadCheckTime = -1;
  /**
   * execution 目录保留时间 ms，默认 1 day
   */
  private long executionDirRetention;
  // date time of the the last flow submitted.
  private long lastFlowSubmittedDate = 0;
  // Indicate if the executor is set to active.
  private volatile boolean active;
  // sla alerter when executor shutdown.
  private volatile boolean slaAlerter = false;
  private HoldBatchThread holdBatchThread;
  private HoldBatchContext holdBatchContext;
  private boolean executionsLogRetention;
  private String executionsLogRetentionDir;
  private final AtomicInteger failedFlowNum = new AtomicInteger(0);

  @Inject
  public FlowRunnerManager(final Props props,
                           final ExecutorLoader executorLoader,
                           final ProjectLoader projectLoader,
                           final StorageManager storageManager,
                           final TriggerManager triggerManager,
                           final AlerterHolder alerterHolder,
                           final CommonMetrics commonMetrics,
                           final ExecutionCycleDao executionCycleDao,
                           @Nullable final AzkabanEventReporter azkabanEventReporter) throws IOException, ExecutorManagerException {
    this.azkabanProps = props;

    this.executionDirRetention = props.getLong(ConfigurationKeys.EXECUTION_DIR_RETENTION_MS,
            12 * 60 * 60 * 1000);
    this.azkabanEventReporter = azkabanEventReporter;
    this.signJobThreadsPerFlow = props.getInt(SIGN_NUM_JOB_THREADS, DEFAULT_SIGN_NUM_JOB_TREADS);
    logger.info("Execution dir retention set to " + this.executionDirRetention + " ms");

    this.executionDirectory = new File(props.getString("azkaban.execution.dir", "executions"));
    if (!this.executionDirectory.exists()) {
      this.executionDirectory.mkdirs();
      setgidPermissionOnExecutionDirectory();
    }
    this.projectDirectory = new File(props.getString("azkaban.project.dir", "projects"));
    if (!this.projectDirectory.exists()) {
      this.projectDirectory.mkdirs();
    }

    // azkaban.temp.dir
    this.numThreads = props.getInt(EXECUTOR_FLOW_THREADS, DEFAULT_NUM_EXECUTING_FLOWS);
    this.numJobThreadPerFlow = props.getInt(FLOW_NUM_JOB_THREADS, DEFAULT_FLOW_NUM_JOB_TREADS);
    this.executorService = createExecutorService(this.numThreads);

    this.executorLoader = executorLoader;
    this.projectLoader = projectLoader;
    this.triggerManager = triggerManager;
    this.alerterHolder = alerterHolder;
    this.commonMetrics = commonMetrics;
    this.executionCycleDao = executionCycleDao;

    this.jobLogChunkSize = this.azkabanProps.getString("job.log.chunk.size", "5MB");
    this.jobLogNumFiles = this.azkabanProps.getInt("job.log.backup.index", 4);

    this.executionsLogRetention = this.azkabanProps.getBoolean("executions.log.retention", false);
    this.executionsLogRetentionDir = this.azkabanProps.getString("executions.log.retention.dir", "/data/logs/schedulis");

    this.validateProxyUser = this.azkabanProps.getBoolean("proxy.user.lock.down", false);

    final String globalPropsPath = props.getString("executor.global.properties", null);
    if (globalPropsPath != null) {
      this.globalProps = new Props(null, globalPropsPath);
    }

    this.jobtypeManager =
            new JobTypeManager(props.getString(
                    AzkabanExecutorServer.JOBTYPE_PLUGIN_DIR,
                    JobTypeManager.DEFAULT_JOBTYPEPLUGINDIR), this.globalProps,
                    getClass().getClassLoader());

    this.jobhookManager =
            new JobHookManager(props.getString(
                    AzkabanExecutorServer.JOBHOOK_PLUGIN_DIR,
                    JobHookManager.DEFAULT_JOBHOOKPLUGINDIR), this.globalProps,
                    getClass().getClassLoader());

    Long projectDirMaxSize = null;
    ProjectCacheCleaner cleaner = null;
    try {
      projectDirMaxSize = props.getLong(ConfigurationKeys.PROJECT_DIR_MAX_SIZE_IN_MB);
      cleaner = new ProjectCacheCleaner(this.projectDirectory, projectDirMaxSize);
    } catch (final UndefinedPropertyException ex) {
    }

    // Create a flow preparer
    this.flowPreparer = new FlowPreparer(storageManager, this.executionDirectory,
            this.projectDirectory, cleaner);

    this.cleanerThread = new CleanerThread();
    this.cleanerThread.start();

    if (this.azkabanProps.getBoolean(ConfigurationKeys.AZKABAN_POLL_MODEL, false)) {
      logger.info("Starting polling service.");
      this.pollingService = new PollingService(this.azkabanProps.getLong
              (ConfigurationKeys.AZKABAN_POLLING_INTERVAL_MS, DEFAULT_POLLING_INTERVAL_MS));
      this.pollingService.start();
    }
    if (this.azkabanProps.getBoolean("azkaban.holdbatch.switch", false)) {
      this.holdBatchThread = new HoldBatchThread();
      this.holdBatchThread.start();
    }

    this.holdBatchContext = ServiceProvider.SERVICE_PROVIDER.getInstance(HoldBatchContext.class);
  }

  public double getProjectDirCacheHitRatio() {
    return this.flowPreparer.getProjectDirCacheHitRatio();
  }

  /**
   * Setting the gid bit on the execution directory forces all files/directories created within the
   * directory to be a part of the group associated with the azkaban process. Then, when users
   * create their own files, the azkaban cleanup thread can properly remove them.
   *
   * Java does not provide a standard library api for setting the gid bit because the gid bit is
   * system dependent, so the only way to set this bit is to start a new process and run the shell
   * command "chmod g+s " + execution directory name.
   *
   * Note that this should work on most Linux distributions and MacOS, but will not work on
   * Windows.
   */
  private void setgidPermissionOnExecutionDirectory() throws IOException {
    logger.info("Creating subprocess to run shell command: chmod g+s "
            + this.executionDirectory.toString());
    Runtime.getRuntime().exec("chmod g+s " + this.executionDirectory.toString());
  }

  private void setgidPermissionOnExecutionBackUpDirectory(File file) throws IOException {
    logger.info("Creating subprocess to run shell command: chmod g+s " + file.toString());
    Runtime.getRuntime().exec("chmod g+s " + file.toString());
  }

  private TrackingThreadPool createExecutorService(final int nThreads) {
    final boolean useNewThreadPool =
            this.azkabanProps.getBoolean(EXECUTOR_USE_BOUNDED_THREADPOOL_QUEUE, false);
    logger.info("useNewThreadPool: " + useNewThreadPool);

    if (useNewThreadPool) {
      this.threadPoolQueueSize =
              this.azkabanProps.getInt(EXECUTOR_THREADPOOL_WORKQUEUE_SIZE, nThreads);
      logger.info("workQueueSize: " + this.threadPoolQueueSize);

      // using a bounded queue for the work queue. The default rejection policy
      // {@ThreadPoolExecutor.AbortPolicy} is used
      final TrackingThreadPool executor =
              new TrackingThreadPool(nThreads, nThreads, 0L, TimeUnit.MILLISECONDS,
                      new LinkedBlockingQueue<>(this.threadPoolQueueSize), this);

      return executor;
    } else {
      // the old way of using unbounded task queue.
      // if the running tasks are taking a long time or stuck, this queue
      // will be very very long.
      return new TrackingThreadPool(nThreads, nThreads, 0L,
              TimeUnit.MILLISECONDS, new LinkedBlockingQueue<>(), this);
    }
  }

  public void setExecutorActive(final boolean isActive, final String host, final int port)
          throws ExecutorManagerException, InterruptedException {
    final Executor executor = this.executorLoader.fetchExecutor(host, port);
    Preconditions.checkState(executor != null, "Unable to obtain self entry in DB");
    if (executor.isActive() != isActive) {
      executor.setActive(isActive);
      this.executorLoader.updateExecutor(executor);
    } else {
      logger.info(
              "Set active action ignored. Executor is already " + (isActive ? "active" : "inactive"));
    }
    this.active = isActive;
    if (!this.active) {
      // When deactivating this executor, this call will wait to return until every thread in {@link
      // #createFlowRunner} has finished. When deploying new executor, old running executor will be
      // deactivated before new one is activated and only one executor is allowed to
      // delete/hard-linking project dirs to avoid race condition described in {@link
      // FlowPreparer#setup}. So to make deactivation process block until flow preparation work
      // finishes guarantees the old executor won't access {@link FlowPreparer#setup} after
      // deactivation.
      waitUntilFlowPreparationFinish();
    }
  }

  /**
   * Wait until ongoing flow preparation work finishes.
   */
  private void waitUntilFlowPreparationFinish() throws InterruptedException {
    final Duration SLEEP_INTERVAL = Duration.ofSeconds(5);
    while (this.preparingFlowCount.intValue() != 0) {
      logger.info(this.preparingFlowCount + " flow(s) is/are still being setup before complete "
              + "deactivation.");
      Thread.sleep(SLEEP_INTERVAL.toMillis());
    }
  }

  public long getLastFlowSubmittedTime() {
    // Note: this is not thread safe and may result in providing dirty data.
    //       we will provide this data as is for now and will revisit if there
    //       is a string justification for change.
    return this.lastFlowSubmittedDate;
  }

  public Props getGlobalProps() {
    return this.globalProps;
  }

  public void setGlobalProps(final Props globalProps) {
    this.globalProps = globalProps;
  }

  public void submitFlow(final int execId) throws ExecutorManagerException {
    if (isAlreadyRunning(execId)) {
      return;
    }

    final FlowRunner runner = createFlowRunner(execId);

    // Check again.
    if (isAlreadyRunning(execId) || runner == null) {
      return;
    }
    submitFlowRunner(runner);
  }

  private boolean isAlreadyRunning(final int execId) throws ExecutorManagerException {
    if (this.runningFlows.containsKey(execId)) {
      logger.info("Execution " + execId + " is already in running.");
      if (!this.submittedFlows.containsValue(execId)) {
        // Execution had been added to running flows but not submitted - something's wrong.
        // Return a response with error: this is a cue for the dispatcher to retry or finalize the
        // execution as failed.
        throw new ExecutorManagerException("Execution " + execId +
                " is in runningFlows but not in submittedFlows. Most likely submission had failed.");
      }
      // Already running, everything seems fine. Report as a successful submission.
      return true;
    }
    return false;
  }

  /**
   * return whether this execution has useExecutor defined. useExecutor is for running test
   * executions on inactive executor.
   */
  private boolean isExecutorSpecified(final ExecutableFlow flow) {
    return flow.getExecutionOptions().getFlowParameters()
            .containsKey(ExecutionOptions.USE_EXECUTOR);
  }

  private FlowRunner createFlowRunner(final int execId) throws ExecutorManagerException {
    final ExecutableFlow flow;
    flow = this.executorLoader.fetchExecutableFlow(execId);
    if (flow == null) {
      throw new ExecutorManagerException("Error loading flow with exec "
              + execId);
    } else if (Status.isStatusFinished(flow.getStatus())) {
      return null;
    }

    // Sets up the project files and execution directory.
    this.preparingFlowCount.incrementAndGet();
    // Record the time between submission, and when the flow preparation/execution starts.
    // Note that since submit time is recorded on the web server, while flow preparation is on
    // the executor, there could be some inaccuracies due to clock skew.
    commonMetrics.addQueueWait(System.currentTimeMillis() -
            flow.getExecutableFlow().getSubmitTime());

    final Timer.Context flowPrepTimerContext = commonMetrics.getFlowSetupTimerContext();

    try {
      if (this.active || isExecutorSpecified(flow)) {
        this.flowPreparer.setup(flow);
      } else {
        // Unset the executor.
        this.executorLoader.unsetExecutorIdForExecution(execId);
        throw new ExecutorManagerException("executor became inactive before setting up the "
                + "flow " + execId);
      }
    } finally {
      this.preparingFlowCount.decrementAndGet();
      flowPrepTimerContext.stop();
    }

    // Setup flow runner
    AbstractFlowWatcher watcher = null;
    final ExecutionOptions options = flow.getExecutionOptions();
    if (options.getPipelineExecutionId() != null) {
      final Integer pipelineExecId = options.getPipelineExecutionId();
      final FlowRunner runner = this.runningFlows.get(pipelineExecId);

      if (runner != null) {
        watcher = new LocalFlowWatcher(runner);
      } else {
        // also ends up here if execute is called with pipelineExecId that's not running any more
        // (it could have just finished, for example)
        watcher = new RemoteFlowWatcher(pipelineExecId, this.executorLoader);
      }
    }

    int numJobThreads = this.numJobThreadPerFlow;
    if (options.getFlowParameters().containsKey(FLOW_NUM_JOB_THREADS)) {
      try {
        final int numJobs =
                Integer.valueOf(options.getFlowParameters().get(
                        FLOW_NUM_JOB_THREADS));
        if (numJobs > 0 && (numJobs <= numJobThreads || ProjectWhitelist
                .isProjectWhitelisted(flow.getProjectId(),
                        WhitelistType.NumJobPerFlow))) {
          numJobThreads = numJobs;
        }
      } catch (final Exception e) {
        throw new ExecutorManagerException(
                "Failed to set the number of job threads "
                        + options.getFlowParameters().get(FLOW_NUM_JOB_THREADS)
                        + " for flow " + execId, e);
      }
    }

    int signJobThreads = this.signJobThreadsPerFlow;
    if (options.getFlowParameters().containsKey(SIGN_NUM_JOB_THREADS)) {
      try {
        final int numJobs =
                Integer.valueOf(options.getFlowParameters().get(
                        SIGN_NUM_JOB_THREADS));
        signJobThreads = numJobs;
      } catch (final Exception e) {
        throw new ExecutorManagerException(
                "Failed to set the number of sign threads "
                        + options.getFlowParameters().get(SIGN_NUM_JOB_THREADS)
                        + " for flow " + execId, e);
      }
    }

    final FlowRunner runner =
            new FlowRunner(flow, this.executorLoader, this.projectLoader, this.jobtypeManager,this.jobhookManager,
                    this.azkabanProps, this.azkabanEventReporter, this.alerterHolder);
    runner.setFlowWatcher(watcher)
            .setJobLogSettings(this.jobLogChunkSize, this.jobLogNumFiles)
            .setValidateProxyUser(this.validateProxyUser)
            .setNumJobThreads(numJobThreads)
            .setSignJobThreads(signJobThreads)
            .setMaxPausedTime(getMaxPausedTime())
            .addListener(this);

    // FIXME Add a listener for loop execution, and continue to submit new tasks when the job stream execution is complete.
    EventListener cycleFlowRunnerEventListener = new CycleFlowRunnerEventListener(executionCycleDao, azkabanProps, alerterHolder);
    runner.setCycleFlowRunnerEventListener(cycleFlowRunnerEventListener);
    runner.addListener(cycleFlowRunnerEventListener);
    configureFlowLevelMetrics(runner);
    return runner;
  }

  private long getMaxPausedTime(){
    long time;
    try{
      time = this.azkabanProps.getLong(FLOW_PAUSED_MAX_TIME_MS, DEFAULT_FLOW_PAUSED_MAX_TIME);
    } catch (RuntimeException re){
      logger.warn("get the FLOW_PAUSED_MAX_TIME_MS property failed.", re);
      time = DEFAULT_FLOW_PAUSED_MAX_TIME;
    }
    return time;
  }

  private void submitFlowRunner(final FlowRunner runner) throws ExecutorManagerException {
    this.runningFlows.put(runner.getExecutionId(), runner);
    try {
      // The executorService already has a queue.
      // The submit method below actually returns an instance of FutureTask,
      // which implements interface RunnableFuture, which extends both
      // Runnable and Future interfaces
      final Future<?> future = this.executorService.submit(runner);
      // keep track of this future
      this.submittedFlows.put(future, runner.getExecutionId());
      // update the last submitted time.
      this.lastFlowSubmittedDate = System.currentTimeMillis();
    } catch (final RejectedExecutionException re) {
      this.runningFlows.remove(runner.getExecutionId());
      final StringBuffer errorMsg = new StringBuffer(
              "Azkaban executor can't execute any more flows. ");
      if (this.executorService.isShutdown()) {
        errorMsg.append("The executor is being shut down.");
      }
      throw new ExecutorManagerException(errorMsg.toString(), re);
    }
  }

  /**
   * Configure Azkaban metrics tracking for a new flowRunner instance
   */
  private void configureFlowLevelMetrics(final FlowRunner flowRunner) {
    logger.info("Configuring Azkaban metrics tracking for flow runner object");

    if (MetricReportManager.isAvailable()) {
      final MetricReportManager metricManager = MetricReportManager.getInstance();
      // Adding NumFailedFlow Metric listener
      flowRunner.addListener((NumFailedFlowMetric) metricManager
              .getMetricFromName(NumFailedFlowMetric.NUM_FAILED_FLOW_METRIC_NAME));
    }

  }


  public void cancelJobBySLA(final int execId, final String jobId)
          throws ExecutorManagerException {
    final FlowRunner flowRunner = this.runningFlows.get(execId);

    if (flowRunner == null) {
      throw new ExecutorManagerException("Execution " + execId
              + " is not running.");
    }
    // FIXME Added support for killing subflow and subflow jobs.
    for (final JobRunner jobRunner : flowRunner.getActiveJobRunners()) {
      if (jobRunner.getNode().getNestedId().equals(jobId)) {
        logger.info("Killing job or embededflow job: " + jobId + " in execution " + execId + " by SLA");
        jobRunner.killBySLA();
        break;
      } else if(jobRunner.getNode().getNestedId().startsWith(jobId)){
        logger.info("embededflow: " + jobId + " , killing current active job : " + jobRunner.getNode().getNestedId() + " in execution " + execId + " by SLA");
        jobRunner.killBySLA();
      }
    }
  }

  public void cancelFlow(final int execId, final String user)
          throws ExecutorManagerException {
    final FlowRunner runner = this.runningFlows.get(execId);
    if (runner == null) {
      throw new ExecutorManagerException("Execution " + execId
              + " is not running.");
    }
    runner.kill(user);
  }

  public void resumeBatchFlow(final int execId)
          throws ExecutorManagerException {
    final FlowRunner runner = this.runningFlows.get(execId);
    if (runner == null) {
      throw new ExecutorManagerException("Execution " + execId
              + " is not running.");
    }
    runner.resumeBatchFlow();
  }

  public void superKill(final int execId, final String user)
          throws ExecutorManagerException {
    final FlowRunner runner = this.runningFlows.get(execId);
    if (runner == null) {
      throw new ExecutorManagerException("Execution " + execId
              + " is not running.");
    }
    runner.superKill(user);
  }

  public void pauseFlow(final int execId, final String user, long timeoutMs)
          throws ExecutorManagerException {
    final FlowRunner runner = this.runningFlows.get(execId);

    if (runner == null) {
      throw new ExecutorManagerException("Execution " + execId
              + " is not running.");
    }

    runner.pause(user, timeoutMs);
  }

  public void setFlowFailed(final int execId, final JsonObject json) throws ExecutorManagerException {
    final FlowRunner runner = this.runningFlows.get(execId);

    if (runner == null) {
      throw new ExecutorManagerException("Execution " + execId
              + " is not running.");
    }
    if(!runner.setFlowFailed(json)){
      throw new ExecutorManagerException("Execution:" + execId + " is not paused or has finished.");
    }
  }

  public void setJobDisabled(final int execId, String disableJob, Map<String, Object> respMap, String user) throws ExecutorManagerException {
    final FlowRunner runner = this.runningFlows.get(execId);

    if (runner == null) {
      throw new ExecutorManagerException("Execution " + execId
              + " is not running.");
    }
    runner.setJobDisabled(disableJob, respMap, user);
  }

  public void setJobFailed(int execid, String setJob, Map<String, Object> respMap, String user)
          throws ExecutorManagerException {
    final FlowRunner runner = this.runningFlows.get(execid);

    if (runner == null) {
      throw new ExecutorManagerException("Execution " + execid
              + " is not running.");
    }
    runner.setJobFailed(setJob, respMap, user);
  }

  public void setJobOpen(int execid, String openJob, Map<String, Object> respMap, String user) throws ExecutorManagerException {
    final FlowRunner runner = this.runningFlows.get(execid);

    if (runner == null) {
      throw new ExecutorManagerException("Execution " + execid
              + " is not running.");
    }
    runner.setJobReady(openJob, respMap, user);
  }

  /**
   * 设置失败重试job
   * @param execId
   * @param retryFailedJobs
   * @throws ExecutorManagerException
   */
  public void retryJobs(final int execId, List<String> retryFailedJobs) throws ExecutorManagerException {
    final FlowRunner runner = this.runningFlows.get(execId);

    if (runner == null) {
      throw new ExecutorManagerException("Execution " + execId
              + " is not running.");
    }
    String message = null;
    try {
      message = runner.retryFailedJobs(retryFailedJobs);
    } catch (Exception e) {
      throw new ExecutorManagerException(e.getMessage(), e);
    }
    if (message != null) {
      throw new ExecutorManagerException(message);
    }

  }

  public void retryHangJobs(int execId, List<String> jobs, String user)
          throws ExecutorManagerException {
    FlowRunner runner = this.runningFlows.get(execId);

    if (runner == null) {
      throw new ExecutorManagerException("Execution " + execId
              + " is not running.");
    }
    String message = null;
    try {
      message = runner.retryHangJobs(jobs, user);
    } catch (Exception e) {
      throw new ExecutorManagerException("Failed to retryHangJobs", e);
    }
    if (message != null) {
      throw new ExecutorManagerException(message);
    }
  }

  public Map<String, String> skipFailedJobs(final int execId, List<String> skipFailedJobs)
          throws ExecutorManagerException {
    Map<String, String> retMap;
    final FlowRunner runner = this.runningFlows.get(execId);

    if (runner == null) {
      throw new ExecutorManagerException("Execution " + execId
              + " is not running.");
    }
    retMap = runner.setSkipFailedJob(skipFailedJobs);
    if (retMap != null && retMap.containsKey("error")) {
      throw new ExecutorManagerException(retMap.get("error"));
    }

    return retMap;
  }

  /**
   * 设置失败重试job
   * @param execId
   * @param flowFailed
   * @throws ExecutorManagerException
   */
  public void setFlowFailed(final int execId, final boolean flowFailed) throws ExecutorManagerException {
    final FlowRunner runner = this.runningFlows.get(execId);

    if (runner == null) {
      throw new ExecutorManagerException("Execution " + execId
              + " is not running.");
    }

    runner.setFlowFailed(flowFailed);

  }

  public void resumeFlow(final int execId, final String user)
          throws ExecutorManagerException {
    final FlowRunner runner = this.runningFlows.get(execId);

    if (runner == null) {
      throw new ExecutorManagerException("Execution " + execId
              + " is not running.");
    }

    runner.resume(user);
  }

  public void retryFailures(final int execId, final String user, final List<String> retryFailedJobs)
          throws ExecutorManagerException {
    final FlowRunner runner = this.runningFlows.get(execId);

    if (runner == null) {
      throw new ExecutorManagerException("Execution " + execId
              + " is not running.");
    }

    runner.retryFailures(user,retryFailedJobs);
  }

  /**
   * 跳过所有Failed_waiting job
   * @param execId
   * @param user
   * @throws ExecutorManagerException
   */
  public String skippedAllFailures(final int execId, final String user)
          throws ExecutorManagerException {
    final FlowRunner runner = this.runningFlows.get(execId);

    if (runner == null) {
      throw new ExecutorManagerException("Execution " + execId
              + " is not running.");
    }

    return runner.skippedAllFailures(user);
  }

  public ExecutableFlow getExecutableFlow(final int execId) {
    final FlowRunner runner = this.runningFlows.get(execId);
    if (runner == null) {
      return this.recentlyFinishedFlows.get(execId);
    }
    return runner.getExecutableFlow();
  }

  @Override
  public void handleEvent(final Event event) {
    if (event.getType() == EventType.FLOW_FINISHED || event.getType() == EventType.FLOW_STARTED) {
      final FlowRunner flowRunner = (FlowRunner) event.getRunner();
      final ExecutableFlow flow = flowRunner.getExecutableFlow();

      if (event.getType() == EventType.FLOW_FINISHED) {
        this.recentlyFinishedFlows.put(flow.getExecutionId(), flow);
        if (Status.isFailed(flow.getStatus())) {
          failedFlowNum.getAndIncrement();
        }
        logger.info("Flow " + flow.getExecutionId()
                + " is finished. Adding it to recently finished flows list.");
        this.runningFlows.remove(flow.getExecutionId());
        if(slaAlerter) {
          logger.info("executor shutdown sla alerter");
          ExecutionControllerUtils.handleFlowFinishAlert(flow, alerterHolder);
        }
      } else if (event.getType() == EventType.FLOW_STARTED) {
        // add flow level SLA checker flow 超时告警
        this.triggerManager
                .addTrigger(flow.getExecutionId(), SlaOption.getFlowLevelSLAOptions(flow));
      }
    }
  }

  public LogData readFlowLogs(final int execId, final int startByte, final int length)
          throws ExecutorManagerException {
    final FlowRunner runner = this.runningFlows.get(execId);
    if (runner == null) {
      throw new ExecutorManagerException("Running flow " + execId
              + " not found.");
    }

    final File dir = runner.getExecutionDir();
    if (dir != null && dir.exists()) {
      try {
        synchronized (this.executionDirDeletionSync) {
          if (!dir.exists()) {
            throw new ExecutorManagerException(
                    "Execution dir file doesn't exist. Probably has been deleted");
          }

          final File logFile = runner.getFlowLogFile();
          if (logFile != null && logFile.exists()) {
            return FileIOUtils.readUtf8File(logFile, startByte, length);
          } else {
            throw new ExecutorManagerException("Flow log file doesn't exist.");
          }
        }
      } catch (final IOException e) {
        throw new ExecutorManagerException(e);
      }
    }

    throw new ExecutorManagerException(
            "Error reading file. Log directory doesn't exist.");
  }

  public long getJobFileSize(final int execId, final String jobId, final int attempt) throws ExecutorManagerException {
    final FlowRunner runner = this.runningFlows.get(execId);
    if (runner == null) {
      throw new ExecutorManagerException("Running flow " + execId
              + " not found.");
    }
    final File dir = runner.getExecutionDir();
    if (dir != null && dir.exists()) {
      synchronized (this.executionDirDeletionSync) {
        if (!dir.exists()) {
          throw new ExecutorManagerException(
                  "Execution dir file doesn't exist. Probably has beend deleted");
        }
        final File logFile = runner.getJobLogFile(jobId, attempt);
        if (logFile != null && logFile.exists()) {
          return logFile.length();
        } else {
          throw new ExecutorManagerException("Job log file doesn't exist.");
        }
      }
    }
    return 0L;
  }

  public LogData readJobLogs(final int execId, final String jobId, final int attempt,
                             final int startByte, final int length) throws ExecutorManagerException {
    final FlowRunner runner = this.runningFlows.get(execId);
    if (runner == null) {
      throw new ExecutorManagerException("Running flow " + execId
              + " not found.");
    }

    final File dir = runner.getExecutionDir();
    if (dir != null && dir.exists()) {
      try {
        synchronized (this.executionDirDeletionSync) {
          if (!dir.exists()) {
            throw new ExecutorManagerException(
                    "Execution dir file doesn't exist. Probably has beend deleted");
          }
          final File logFile = runner.getJobLogFile(jobId, attempt);
          if (logFile != null && logFile.exists()) {
            return FileIOUtils.readUtf8File(logFile, startByte, length < 0 ? (int) logFile.length() : length);
          } else {
            throw new ExecutorManagerException("Job log file doesn't exist.");
          }
        }
      } catch (final IOException e) {
        throw new ExecutorManagerException(e);
      }
    }

    throw new ExecutorManagerException(
            "Error reading file. Log directory doesn't exist.");
  }

  public List<Object> readJobAttachments(final int execId, final String jobId, final int attempt)
          throws ExecutorManagerException {
    final FlowRunner runner = this.runningFlows.get(execId);
    if (runner == null) {
      throw new ExecutorManagerException("Running flow " + execId
              + " not found.");
    }

    final File dir = runner.getExecutionDir();
    if (dir == null || !dir.exists()) {
      throw new ExecutorManagerException(
              "Error reading file. Log directory doesn't exist.");
    }

    try {
      synchronized (this.executionDirDeletionSync) {
        if (!dir.exists()) {
          throw new ExecutorManagerException(
                  "Execution dir file doesn't exist. Probably has beend deleted");
        }

        final File attachmentFile = runner.getJobAttachmentFile(jobId, attempt);
        if (attachmentFile == null || !attachmentFile.exists()) {
          return null;
        }

        final List<Object> jobAttachments =
                (ArrayList<Object>) JSONUtils.parseJSONFromFile(attachmentFile);

        return jobAttachments;
      }
    } catch (final IOException e) {
      throw new ExecutorManagerException(e);
    }
  }

  public JobMetaData readJobMetaData(final int execId, final String jobId, final int attempt,
                                     final int startByte, final int length) throws ExecutorManagerException {
    final FlowRunner runner = this.runningFlows.get(execId);
    if (runner == null) {
      throw new ExecutorManagerException("Running flow " + execId
              + " not found.");
    }

    final File dir = runner.getExecutionDir();
    if (dir != null && dir.exists()) {
      try {
        synchronized (this.executionDirDeletionSync) {
          if (!dir.exists()) {
            throw new ExecutorManagerException(
                    "Execution dir file doesn't exist. Probably has beend deleted");
          }
          final File metaDataFile = runner.getJobMetaDataFile(jobId, attempt);
          if (metaDataFile != null && metaDataFile.exists()) {
            return FileIOUtils.readUtf8MetaDataFile(metaDataFile, startByte,
                    length);
          } else {
            throw new ExecutorManagerException("Job log file doesn't exist.");
          }
        }
      } catch (final IOException e) {
        throw new ExecutorManagerException(e);
      }
    }

    throw new ExecutorManagerException(
            "Error reading file. Log directory doesn't exist.");
  }

  public long getLastCleanerThreadCheckTime() {
    return this.lastCleanerThreadCheckTime;
  }

  public boolean isCleanerThreadActive() {
    return this.cleanerThread.isAlive();
  }

  public State getCleanerThreadState() {
    return this.cleanerThread.getState();
  }

  public boolean isExecutorThreadPoolShutdown() {
    return this.executorService.isShutdown();
  }

  public int getNumQueuedFlows() {
    return this.executorService.getQueue().size();
  }

  public int getNumRunningFlows() {
    return this.executorService.getActiveCount();
  }

  public String getRunningFlowIds() {
    // The in progress tasks are actually of type FutureTask
    final Set<Runnable> inProgressTasks = this.executorService.getInProgressTasks();

    final List<Integer> runningFlowIds =
            new ArrayList<>(inProgressTasks.size());

    for (final Runnable task : inProgressTasks) {
      // add casting here to ensure it matches the expected type in
      // submittedFlows
      final Integer execId = this.submittedFlows.get((Future<?>) task);
      if (execId != null) {
        runningFlowIds.add(execId);
      } else {
        logger.warn("getRunningFlowIds: got null execId for task: " + task);
      }
    }

    Collections.sort(runningFlowIds);
    return runningFlowIds.toString();
  }

  public String getQueuedFlowIds() {
    final List<Integer> flowIdList =
            new ArrayList<>(this.executorService.getQueue().size());

    for (final Runnable task : this.executorService.getQueue()) {
      final Integer execId = this.submittedFlows.get(task);
      if (execId != null) {
        flowIdList.add(execId);
      } else {
        logger
                .warn("getQueuedFlowIds: got null execId for queuedTask: " + task);
      }
    }
    Collections.sort(flowIdList);
    return flowIdList.toString();
  }

  public int getMaxNumRunningFlows() {
    return this.numThreads;
  }

  public int getTheadPoolQueueSize() {
    return this.threadPoolQueueSize;
  }

  public void reloadJobTypePlugins() throws JobTypeManagerException {
    this.jobtypeManager.loadPlugins();
  }

  public void reloadExecProps() {
    this.azkabanProps = AbstractAzkabanServer
            .loadProps(azkabanProps.getString("schedulis.exec.conf.dir",
                    "bin/internal/../../conf"));
  }

  public int getTotalNumExecutedFlows() {
    return this.executorService.getTotalTasks();
  }

  @Override
  public void beforeExecute(final Runnable r) {
  }

  @Override
  public void afterExecute(final Runnable r) {
    this.submittedFlows.remove(r);
  }

  /**
   * This shuts down the flow runner. The call is blocking and awaits execution of all jobs.
   */
  public void shutdown() {
    logger.warn("Shutting down FlowRunnerManager...");
    if (this.azkabanProps.getBoolean(ConfigurationKeys.AZKABAN_POLL_MODEL, false)) {
      this.pollingService.shutdown();
    }
    this.executorService.shutdown();
    if (this.azkabanProps.getBoolean("azkaban.holdbatch.switch", false)) {
      this.holdBatchThread.shutdown();
    }

    boolean result = false;
    while (!result) {
      logger.info("Awaiting Shutdown. # of executing flows: " + getNumRunningFlows());
      try {
        result = this.executorService.awaitTermination(1, TimeUnit.MINUTES);
      } catch (final InterruptedException e) {
        logger.error("", e);
      }
    }
    logger.warn("Shutdown FlowRunnerManager complete.");
  }

  /**
   * This attempts shuts down the flow runner immediately (unsafe). This doesn't wait for jobs to
   * finish but interrupts all threads.
   *
   */
  public void shutdownNow() {
    logger.warn("Shutting down FlowRunnerManager now...");
    if (this.azkabanProps.getBoolean(ConfigurationKeys.AZKABAN_POLL_MODEL, false)) {
      this.pollingService.shutdown();
    }
    this.slaAlerter = true;
    // FIXME New feature: Before closing the executor thread pool, you need to actively kill all running tasks.
    for (FlowRunner flowRunner : runningFlows.values()) {
      logger.info("killing flow execId:" + flowRunner.getExecutionId());
      try {
        flowRunner.removeListener(flowRunner.getCycleFlowRunnerEventListener());
        flowRunner.kill();
      } catch (Exception e) {
        logger.error("kill flow failed, execId: " + flowRunner.getExecutionId(), e);
      }
    }

    this.executorService.shutdown();
    this.triggerManager.shutdown();

    boolean result = false;
    for(int i = 0; i < 8; i++) {
      logger.info("Awaiting Shutdown. # of executing flows: " + getNumRunningFlows());
      try {
        result = this.executorService.awaitTermination(30, TimeUnit.SECONDS);
        if(result){
          logger.info("all flow is finished.");
          break;
        }
      } catch (final InterruptedException e) {
        logger.error("", e);
      }
    }
    logger.warn("Shutdown FlowRunnerManager complete, now executing flows size: " + getNumRunningFlows());
  }

  /**
   * Deleting old execution directory to free disk space.
   */
  public void deleteExecutionDirectory() {
    logger.warn("Deleting execution dir: " + this.executionDirectory.getAbsolutePath());
    try {

      File execBackupDir;
      // 读取自定义备份数据目录
      String configDirName = azkabanProps.getString("execution.log.backup.dir");
      if (StringUtils.isNotBlank(configDirName)) {
        execBackupDir = new File(azkabanProps.getString("execution.log.backup.dir"));
      } else {
        // 如果没有定义目录,则创建一个默认的目录
        execBackupDir = new File(azkabanProps.getString("azkaban.execution.dir", "executions_backup"));
      }
      execBackupDir.mkdirs();
      setgidPermissionOnExecutionBackUpDirectory(execBackupDir);

      FileIOUtils.copy(this.executionDirectory, execBackupDir);
      FileUtils.deleteDirectory(this.executionDirectory);
    } catch (final IOException e) {
      logger.error("", e);
    }
  }

  private Set<Pair<Integer, Integer>> getActiveProjectVersions() {
    final Set<Pair<Integer, Integer>> activeProjectVersions = new HashSet<>();
    for (final FlowRunner runner : FlowRunnerManager.this.runningFlows.values()) {
      final ExecutableFlow flow = runner.getExecutableFlow();
      activeProjectVersions.add(new Pair<>(flow
              .getProjectId(), flow.getVersion()));
    }
    return activeProjectVersions;
  }

  public int getFailedFlowNum() {
    return failedFlowNum.get();
  }


  private class CleanerThread extends Thread {

    /**
     * execution 目录清理间隔，默认 1 h
     */
    private final long executionDirCleanIntervalMs = FlowRunnerManager.this.azkabanProps.getLong(
            ConfigurationKeys.EXECUTION_DIR_CLEAN_INTERVAL_MS, 60 * 60 * 1000);
    // Every 2 mins clean the recently finished list
    private static final long RECENTLY_FINISHED_INTERVAL_MS = 2 * 60 * 1000;
    // Every 5 mins kill flows running longer than allowed max running time
    private static final long LONG_RUNNING_FLOW_KILLING_INTERVAL_MS = 5 * 60 * 1000;
    private final long flowMaxRunningTimeInMins = FlowRunnerManager.this.azkabanProps.getInt(
            Constants.ConfigurationKeys.AZKABAN_MAX_FLOW_RUNNING_MINS, -1);
    private boolean shutdown = false;
    private long lastExecutionDirCleanTime = -1;
    private long lastRecentlyFinishedCleanTime = -1;
    private long lastLongRunningFlowCleanTime = -1;

    public CleanerThread() {
      this.setName("FlowRunnerManager-Cleaner-Thread");
      setDaemon(true);
    }

    public void shutdown() {
      this.shutdown = true;
      this.interrupt();
    }

    private boolean isFlowRunningLongerThan(final ExecutableFlow flow,
                                            final long flowMaxRunningTimeInMins) {
      final Set<Status> nonFinishingStatusAfterFlowStarts = new HashSet<>(
              Arrays.asList(Status.RUNNING, Status.QUEUED, Status.PAUSED, Status.FAILED_FINISHING));
      return nonFinishingStatusAfterFlowStarts.contains(flow.getStatus()) && flow.getStartTime() > 0
              && TimeUnit.MILLISECONDS.toMinutes(System.currentTimeMillis() - flow.getStartTime())
              >= flowMaxRunningTimeInMins;
    }

    @Override
    public void run() {
      while (!this.shutdown) {
        synchronized (this) {
          try {
            FlowRunnerManager.this.lastCleanerThreadCheckTime = System.currentTimeMillis();
            FlowRunnerManager.logger.info("# of executing flows: " + getNumRunningFlows());

            // Cleanup old stuff.
            final long currentTime = System.currentTimeMillis();
            if (currentTime - RECENTLY_FINISHED_INTERVAL_MS > this.lastRecentlyFinishedCleanTime) {
              FlowRunnerManager.logger.info("Cleaning recently finished");
              cleanRecentlyFinished();
              this.lastRecentlyFinishedCleanTime = currentTime;
            }

            if (currentTime - executionDirCleanIntervalMs > this.lastExecutionDirCleanTime) {
              FlowRunnerManager.logger.info("Cleaning old execution dirs, last clean time: {}, "
                              + "clean interval: {} ms, retention: {} ms ", this.lastExecutionDirCleanTime,
                      executionDirCleanIntervalMs, FlowRunnerManager.this.executionDirRetention);
              cleanOlderExecutionDirs();
              this.lastExecutionDirCleanTime = currentTime;
            }

            if (this.flowMaxRunningTimeInMins > 0
                    && currentTime - LONG_RUNNING_FLOW_KILLING_INTERVAL_MS
                    > this.lastLongRunningFlowCleanTime) {
              FlowRunnerManager.logger
                      .info(String.format("Killing long jobs running longer than %s mins",
                              this.flowMaxRunningTimeInMins));
              for (final FlowRunner flowRunner : FlowRunnerManager.this.runningFlows.values()) {
                if (isFlowRunningLongerThan(flowRunner.getExecutableFlow(),
                        this.flowMaxRunningTimeInMins)) {
                  FlowRunnerManager.logger.info(String
                          .format("Killing job [id: %s, status: %s]. It has been running for %s mins",
                                  flowRunner.getExecutableFlow().getId(),
                                  flowRunner.getExecutableFlow().getStatus(), TimeUnit.MILLISECONDS
                                          .toMinutes(System.currentTimeMillis() - flowRunner.getExecutableFlow()
                                                  .getStartTime())));
                  flowRunner.kill();
                }
              }
              this.lastLongRunningFlowCleanTime = currentTime;
            }

            wait(FlowRunnerManager.RECENTLY_FINISHED_TIME_TO_LIVE);
          } catch (final InterruptedException e) {
            FlowRunnerManager.logger.info("Interrupted. Probably to shut down.");
          } catch (final Throwable t) {
            FlowRunnerManager.logger.warn(
                    "Uncaught throwable, please look into why it is not caught", t);
          }
        }
      }
    }

    private void cleanOlderExecutionDirs() {
      final File dir = FlowRunnerManager.this.executionDirectory;

      final long pastTimeThreshold =
              System.currentTimeMillis() - FlowRunnerManager.this.executionDirRetention;
      final File[] executionDirs = dir
              .listFiles(path -> path.isDirectory() && path.lastModified() < pastTimeThreshold);

      FlowRunnerManager.logger.info("Cleaning execution dirs last modified time < {}",
              pastTimeThreshold);

      for (final File exDir : executionDirs) {
        try {
          final int execId = Integer.valueOf(exDir.getName());
          if (FlowRunnerManager.this.runningFlows.containsKey(execId)
                  || FlowRunnerManager.this.recentlyFinishedFlows.containsKey(execId)) {
            continue;
          }
        } catch (final NumberFormatException e) {
          FlowRunnerManager.logger.error("Can't delete exec dir " + exDir.getName()
                  + " it is not a number");
          continue;
        }

        synchronized (FlowRunnerManager.this.executionDirDeletionSync) {
          try {
            if (FlowRunnerManager.this.executionsLogRetention) {
              File executionsLogRetentionDir = new File(FlowRunnerManager.this.executionsLogRetentionDir);
              zipDirectory(exDir, executionsLogRetentionDir);
            }
            FileUtils.deleteDirectory(exDir);
          } catch (final IOException e) {
            FlowRunnerManager.logger.error("Error cleaning execution dir " + exDir.getPath(), e);
          }
        }
      }
    }

    private void zipDirectory(File exDir, File executionsLogRetentionDir) {
      if (!executionsLogRetentionDir.exists()) {
        executionsLogRetentionDir.mkdirs();
      }
      ZipOutputStream zos = null;
      try {
        String fileName = generateFileName(exDir);
        File zipFile = new File(executionsLogRetentionDir.getCanonicalPath(), fileName + ".zip");
        zos = new ZipOutputStream(new FileOutputStream(zipFile));
        zip(exDir.getName(), zos, exDir);
      } catch (IOException e) {
        FlowRunnerManager.logger.error("Error zip execution dir " + exDir.getPath(), e);
      }finally {
        IOUtils.closeQuietly(zos);
      }
    }

    private String generateFileName(File exDir) {
      StringBuilder fileName = new StringBuilder(exDir.getName());
      File[] files = exDir.listFiles();
      for (File file : files) {
        if (file.isFile() && file.getName().endsWith(".log") && file.getName().startsWith("_flow")) {
          String[] split = file.getName().split("\\.");
          fileName.append("-" + split[split.length - 3]).append("-" + split[split.length - 2]);
          break;
        }
      }
      return fileName.toString();
    }

    private void zip(String path, ZipOutputStream zos, File exDir){
      if (exDir.isFile() && exDir.getName().endsWith(".log")) {
        try (FileInputStream input = new FileInputStream(exDir)) {
          zos.putNextEntry(new ZipEntry(path + File.separator + exDir.getName()));
          IOUtils.copy(input, zos);
          zos.flush();
          zos.closeEntry();
        } catch (IOException e) {
          FlowRunnerManager.logger.error("Error zip execution dir " + exDir.getPath(), e);
        }
      } else {
        File[] files = exDir.listFiles();
        if (ArrayUtils.isNotEmpty(files)) {
          for (File file : files) {
            if (file.isDirectory()) {
              zip(FilenameUtils.normalizeNoEndSeparator(path + File.separator + file.getName()), zos, file);
            } else {
              if (file.getName().endsWith(".log")) {
                try (FileInputStream input = new FileInputStream(file)) {
                  zos.putNextEntry(new ZipEntry(path + File.separator + file.getName()));
                  IOUtils.copy(input, zos);
                  zos.flush();
                  zos.closeEntry();
                } catch (IOException e) {
                  FlowRunnerManager.logger.error("Error zip execution dir " + exDir.getPath(), e);
                }
              }
            }
          }
        } else {
          try {
            zos.putNextEntry(new ZipEntry(path + File.separator));
            zos.closeEntry();
          } catch (IOException e) {
            FlowRunnerManager.logger.error("Error zip execution dir " + exDir.getPath(), e);
          }
        }
      }
    }

    private void cleanRecentlyFinished() {
      final long cleanupThreshold =
              System.currentTimeMillis() - FlowRunnerManager.RECENTLY_FINISHED_TIME_TO_LIVE;
      final ArrayList<Integer> executionToKill = new ArrayList<>();
      for (final ExecutableFlow flow : FlowRunnerManager.this.recentlyFinishedFlows.values()) {
        if (flow.getEndTime() < cleanupThreshold) {
          executionToKill.add(flow.getExecutionId());
        }
      }

      for (final Integer id : executionToKill) {
        FlowRunnerManager.logger.info("Cleaning execution " + id
                + " from recently finished flows list.");
        FlowRunnerManager.this.recentlyFinishedFlows.remove(id);
      }
    }
  }

  /*
   * read hold batch context
   */
  private class HoldBatchThread extends Thread {

    private boolean shutdown = false;

    public HoldBatchThread() {
      this.setName("AzkabanExecutorServer-HoldBatch-Thread");
    }

    public void shutdown() {
      this.shutdown = true;
      this.interrupt();
    }

    @Override
    public void run() {
      while (!this.shutdown) {
        synchronized (this) {
          try {
            FlowRunnerManager.this.executorLoader.getLocalHoldBatchOpr().stream().forEach(
                    operate -> FlowRunnerManager.this.holdBatchContext.getBatchMap()
                            .putIfAbsent(operate.getBatchId(), operate));

            List<HoldBatchOperate> operateList = FlowRunnerManager.this.holdBatchContext
                    .getBatchMap().values().stream().filter(operate -> operate.getOperateLevel() == 0)
                    .collect(Collectors.toList());
            if (CollectionUtils.isNotEmpty(operateList)) {
              FlowRunnerManager.this.holdBatchContext.getBatchMap().clear();
              FlowRunnerManager.this.holdBatchContext.getBatchMap()
                      .put(operateList.get(0).getBatchId(), operateList.get(0));
            }

            for (HoldBatchOperate operate : FlowRunnerManager.this.holdBatchContext.getBatchMap()
                    .values()) {
              String resumeJson = FlowRunnerManager.this.executorLoader
                      .getLocalHoldBatchResume(operate.getBatchId());
              if (StringUtils.isNotEmpty(resumeJson)) {
                FlowRunnerManager.this.holdBatchContext.getBatchMap().remove(operate.getBatchId());
                FlowRunnerManager.this.executorLoader
                        .updateHoldBatchStatus(operate.getBatchId(), 1);
                operate.setStrategy(resumeJson);
                this.resume(operate);
              } else {
                for (FlowRunner flowRunner : FlowRunnerManager.this.runningFlows.values()) {
                  ExecutableFlow executableFlow = flowRunner.getExecutableFlow();
                  String batchId = FlowRunnerManager.this.holdBatchContext
                          .isInBatch(executableFlow.getProjectName(), executableFlow.getId(),
                                  executableFlow.getSubmitUser());
                  if (StringUtils.isNotEmpty(batchId)) {
                    flowRunner.changePreparing2SystemPaused();
                    flowRunner.changePaused2SystemPaused();
                  }
                }
              }
            }

            FlowRunnerManager.this.executorLoader.getMissResumeBatch().stream()
                    .forEach(operate -> this.resume(operate));

            wait(FlowRunnerManager.this.azkabanProps.getLong("azkaban.holdbatch.thread.ms", 10000));
          } catch (final InterruptedException e) {
            FlowRunnerManager.logger.info("Interrupted. Probably to shut down.");
          } catch (Exception e) {
            FlowRunnerManager.logger.error("check hold batch context error", e);
          }
        }
      }
    }

    private void resume(HoldBatchOperate holdBatchOperate) {
      new Thread(() -> {
        String batchKey = Constants.HOLD_BATCH_RESUME_LOCK_KEY + holdBatchOperate.getBatchId();
        synchronized (batchKey.intern()) {
          try {
            Alerter mailAlerter = ServiceProvider.SERVICE_PROVIDER.getInstance(AlerterHolder.class)
                    .get("email");

            List<HoldBatchAlert> execList = FlowRunnerManager.this.executorLoader
                    .queryExecingByBatch(holdBatchOperate.getBatchId());
            for (HoldBatchAlert exec : execList) {
              FlowRunner flowRunner = FlowRunnerManager.this.runningFlows.get(exec.getExecId());
              if (flowRunner == null) {
                continue;
              }
              boolean alertFlag = true;
              if (holdBatchOperate.isBlack(exec)) {
                exec.setBlack(1);
                exec.setResume(2);
              } else if (holdBatchOperate.isNotResume(exec)) {
                exec.setBlack(0);
                exec.setResume(2);
              } else {
                String key = (StringUtils.isEmpty(flowRunner.getLastBatchId()) ? holdBatchOperate
                        .getBatchId() : flowRunner.getLastBatchId() + "-" + exec.getExecId()).intern();
                synchronized (key) {
                  key.notifyAll();
                }
                exec.setBlack(0);
                exec.setResume(1);
                exec.setResumeTime(System.currentTimeMillis());
                alertFlag = false;
              }
              FlowRunnerManager.this.executorLoader.updateHoldBatchResumeStatus(exec);
              if (alertFlag) {
                if (flowRunner != null) {
                  flowRunner.kill();
                }
                if (mailAlerter != null) {
                  mailAlerter.alertOnHoldBatch(exec, FlowRunnerManager.this.executorLoader, false);
                }
              }
            }
            //唤醒循环执行
            String cycleKey = ("cycleFlow-" + holdBatchOperate.getBatchId()).intern();
            synchronized (cycleKey) {
              cycleKey.notifyAll();
            }
          } catch (Exception e) {
            logger.error("batch resume error: " + holdBatchOperate.getBatchId(), e);
          }
        }
      }).start();
    }
  }

  /**
   * Polls new executions from DB periodically and submits the executions to run on the executor.
   */
  @SuppressWarnings("FutureReturnValueIgnored")
  private class PollingService {

    private final long pollingIntervalMs;
    private final ScheduledExecutorService scheduler;
    private int executorId = -1;

    public PollingService(final long pollingIntervalMs) {
      this.pollingIntervalMs = pollingIntervalMs;
      this.scheduler = Executors.newSingleThreadScheduledExecutor();
    }

    public void start() {
      this.scheduler.scheduleAtFixedRate(() -> pollExecution(), 0L, this.pollingIntervalMs,
              TimeUnit.MILLISECONDS);
    }

    private void pollExecution() {
      if (this.executorId == -1) {
        if (AzkabanExecutorServer.getApp() != null) {
          try {
            final Executor executor = requireNonNull(FlowRunnerManager.this.executorLoader
                    .fetchExecutor(AzkabanExecutorServer.getApp().getHost(),
                            AzkabanExecutorServer.getApp().getPort()), "The executor can not be null");
            this.executorId = executor.getId();
          } catch (final Exception e) {
            FlowRunnerManager.logger.error("Failed to fetch executor ", e);
          }
        }
      } else {
        try {
          // Todo jamiesjc: check executor capacity before polling from DB
          final int execId = FlowRunnerManager.this.executorLoader
                  .selectAndUpdateExecution(this.executorId, FlowRunnerManager.this.active);
          if (execId != -1) {
            FlowRunnerManager.logger.info("Submitting flow " + execId);
            submitFlow(execId);
            commonMetrics.markDispatchSuccess();
          }
        } catch (final Exception e) {
          FlowRunnerManager.logger.error("Failed to submit flow ", e);
          commonMetrics.markDispatchFail();
        }
      }
    }

    public void shutdown() {
      this.scheduler.shutdown();
      this.scheduler.shutdownNow();
    }
  }

}
