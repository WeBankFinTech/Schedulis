/*
 * Copyright 2014 LinkedIn Corp.
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

package azkaban.executor;

import static azkaban.Constants.ConfigurationKeys.SYSTEM_SCHEDULE_SWITCH_ACTIVE_KEY;
import static azkaban.Constants.ConfigurationKeys.WTSS_QUERY_SERVER_ENABLE;
import static java.util.Objects.requireNonNull;

import azkaban.Constants;
import azkaban.Constants.ConfigurationKeys;
import azkaban.ServiceProvider;
import azkaban.alert.Alerter;
import azkaban.batch.HoldBatchAlert;
import azkaban.batch.HoldBatchContext;
import azkaban.batch.HoldBatchOperate;
import azkaban.db.EncodingType;
import azkaban.event.EventHandler;
import azkaban.executor.selector.ExecutorComparator;
import azkaban.executor.selector.ExecutorFilter;
import azkaban.executor.selector.ExecutorSelector;
import azkaban.flow.Flow;
import azkaban.flow.FlowUtils;
import azkaban.history.ExecutionRecover;
import azkaban.history.GroupTask;
import azkaban.history.RecoverTrigger;
import azkaban.jobExecutor.utils.SystemBuiltInParamReplacer;
import azkaban.log.LogFilterEntity;
import azkaban.metrics.CommonMetrics;
import azkaban.project.Project;
import azkaban.project.ProjectLoader;
import azkaban.project.ProjectLogEvent;
import azkaban.project.ProjectManagerException;
import azkaban.project.ProjectWhitelist;
import azkaban.project.entity.FlowBusiness;
import azkaban.scheduler.EventScheduleServiceImpl;
import azkaban.scheduler.Schedule;
import azkaban.scheduler.ScheduleManager;
import azkaban.scheduler.ScheduleManagerException;
import azkaban.system.SystemUserLoader;
import azkaban.system.SystemUserManagerException;
import azkaban.system.entity.WtssUser;
import azkaban.trigger.ProjectDeleteTrigger;
import azkaban.user.User;
import azkaban.utils.AuthenticationUtils;
import azkaban.utils.FileIOUtils.JobMetaData;
import azkaban.utils.FileIOUtils.LogData;
import azkaban.utils.JSONUtils;
import azkaban.utils.JwtTokenUtils;
import azkaban.utils.LogUtils;
import azkaban.utils.Pair;
import azkaban.utils.Props;
import azkaban.utils.Utils;
import azkaban.utils.WebUtils;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.util.concurrent.ThreadFactoryBuilder;
import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.lang.Thread.State;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.sql.SQLException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import javax.inject.Inject;
import javax.inject.Singleton;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang.StringUtils;
import org.joda.time.DateTime;
import org.joda.time.LocalDate;
import org.joda.time.LocalDateTime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Executor manager used to manage the client side job.
 */
@Singleton
public class ExecutorManager extends EventHandler implements
        ExecutorManagerAdapter {

  //  Time interval of historical rerun training.
  public static final String HISTORY_RECOVER_INTERVAL_MS = "history.recover.interval.ms";

  private static final String SPARK_JOB_TYPE = "spark";
  private static final String APPLICATION_ID = "${application.id}";
  // The regex to look for while fetching application ID from the Hadoop/Spark job log
  private static final Pattern APPLICATION_ID_PATTERN = Pattern
          .compile("application_\\d+_\\d+");
  private static final Pattern IS_NUMBER = Pattern.compile("^[0-9]+$");
  // The regex to look for while validating the content from RM job link
  private static final Pattern FAILED_TO_READ_APPLICATION_PATTERN = Pattern
          .compile("Failed to read the application");
  private static final Pattern INVALID_APPLICATION_ID_PATTERN = Pattern
          .compile("Invalid Application ID");
  private static final int DEFAULT_MAX_ONCURRENT_RUNS_ONEFLOW = 30;
  // 12 weeks
  private static final long DEFAULT_EXECUTION_LOGS_RETENTION_MS = 3 * 4 * 7 * 24 * 60 * 60 * 1000L;
  private static final Duration RECENTLY_FINISHED_LIFETIME = Duration.ofMinutes(10);
  private static final Logger logger = LoggerFactory.getLogger(ExecutorManager.class);
  private final RunningExecutions runningExecutions;
  private final Props azkProps;
  private final CommonMetrics commonMetrics;
  private final ExecutorLoader executorLoader;
  private ProjectLoader projectLoader;
  private final CleanerThread cleanerThread;
  private final HoldBatchThread holdBatchThread;
  // 历史重跑触发线程
  private final RecoverThread recoverThread;
  private final ConcurrentHashMap<Integer, Pair<ExecutionReference, ExecutableFlow>> runningFlows =
          new ConcurrentHashMap<>();

  private final RunningExecutionsUpdaterThread updaterThread;
  private final ExecutorApiGateway apiGateway;
  private final int maxConcurrentRunsOneFlow;
  private final ExecutorManagerUpdaterStage updaterStage;
  private final ExecutionFinalizer executionFinalizer;
  private final ActiveExecutors activeExecutors;
  private final ExecutorService executorInfoRefresherService;
  QueuedExecutions queuedFlows;
  File cacheDir;
  private QueueProcessorThread queueProcessor;

  private List<String> filterList;
  private Map<String, Integer> comparatorWeightsMap;
  private long lastSuccessfulExecutorInfoRefresh;
  private Duration sleepAfterDispatchFailure = Duration.ofSeconds(1L);
  private boolean initialized = false;
  private ExecutorService executorInforRefresherService;
  private final AlerterHolder alerterHolder;

  private final ProjectDeleteTrigger projectDeleteTrigger;

  private ScheduleManager scheduleManager;
  private EventScheduleServiceImpl eventScheduleService;
  private HoldBatchContext holdBatchContext;
  private boolean holdBatchSwitch;

  private SystemUserLoader systemUserLoader;

  // 是否关闭调度，开启查询服务
  private boolean enableScheduleActive;
  private boolean hdfsLogSwitch;

  private final boolean queryServerSwitch;

  private final long executorRefreshWaitTime;

  @Inject
  public ExecutorManager(final Props azkProps, final ExecutorLoader executorLoader,
                         final CommonMetrics commonMetrics,
                         final ExecutorApiGateway apiGateway,
                         final RunningExecutions runningExecutions,
                         final ActiveExecutors activeExecutors,
                         final ExecutorManagerUpdaterStage updaterStage,
                         final ExecutionFinalizer executionFinalizer,
                         final RunningExecutionsUpdaterThread updaterThread,
                         final ProjectLoader projectLoader,
                         final AlerterHolder alerterHolder,
                         final ScheduleManager scheduleManager,
                         final EventScheduleServiceImpl eventScheduleService,
                         final ProjectDeleteTrigger projectDeleteTrigger,
                         final HoldBatchContext holdBatchContext,
                         SystemUserLoader systemUserLoader) throws ExecutorManagerException {
    this.azkProps = azkProps;
    this.commonMetrics = commonMetrics;
    this.executorLoader = executorLoader;
    this.projectLoader = projectLoader;
    this.apiGateway = apiGateway;
    this.runningExecutions = runningExecutions;
    this.activeExecutors = activeExecutors;
    this.updaterStage = updaterStage;
    this.executionFinalizer = executionFinalizer;
    this.updaterThread = updaterThread;
    this.maxConcurrentRunsOneFlow = getMaxConcurrentRunsOneFlow(azkProps);
    this.cleanerThread = createCleanerThread();
    this.holdBatchThread = createHoldBatchThread();
    this.recoverThread = createRecoverThread();
    this.executorInfoRefresherService = createExecutorInfoRefresherService();
    this.alerterHolder = alerterHolder;
    this.scheduleManager = scheduleManager;
    this.eventScheduleService = eventScheduleService;
    this.projectDeleteTrigger = projectDeleteTrigger;
    this.holdBatchContext = holdBatchContext;
    this.holdBatchSwitch = this.azkProps.getBoolean("azkaban.holdbatch.switch", false);
    this.enableScheduleActive = azkProps.getBoolean(SYSTEM_SCHEDULE_SWITCH_ACTIVE_KEY, true);
    this.systemUserLoader = systemUserLoader;
    this.hdfsLogSwitch = this.azkProps.getBoolean(ConfigurationKeys.HDFS_LOG_SWITCH, false);
    queryServerSwitch = this.azkProps.getBoolean(WTSS_QUERY_SERVER_ENABLE, false);
    this.executorRefreshWaitTime = this.azkProps.getLong(ConfigurationKeys.EXECUTOR_REFRESH_WAIT_TIME, 6 * 1000);
  }

  private int getMaxConcurrentRunsOneFlow(final Props azkProps) {
    // The default threshold is set to 30 for now, in case some users are affected. We may
    // decrease this number in future, to better prevent DDos attacks.
    return azkProps.getInt(ConfigurationKeys.MAX_CONCURRENT_RUNS_ONEFLOW,
            DEFAULT_MAX_ONCURRENT_RUNS_ONEFLOW);
  }

  private CleanerThread createCleanerThread() {
    final long executionLogsRetentionMs = this.azkProps.getLong("execution.logs.retention.ms",
            DEFAULT_EXECUTION_LOGS_RETENTION_MS);
    return new CleanerThread(executionLogsRetentionMs);
  }

  private HoldBatchThread createHoldBatchThread() {
    final long holdBatchThreadMs = this.azkProps.getLong("azkaban.holdbatch.thread.ms", 10000);
    return new HoldBatchThread(holdBatchThreadMs);
  }

  @Override
  public Props getAzkabanProps() {
    return this.azkProps;
  }

  private RecoverThread createRecoverThread() {
    // 默认10s
    final long waitTime = this.azkProps.getLong(HISTORY_RECOVER_INTERVAL_MS, 10000);
    return new RecoverThread(waitTime);
  }

  void initialize() throws ExecutorManagerException {
    if (this.initialized) {
      return;
    }
    this.initialized = true;
    this.setupExecutors();
    this.loadRunningExecutions();
    this.queuedFlows = new QueuedExecutions(
            this.azkProps.getLong(ConfigurationKeys.WEBSERVER_QUEUE_SIZE, 100000));
    this.loadQueuedFlows();
    this.cacheDir = new File(this.azkProps.getString("cache.directory", "cache"));
    // TODO extract QueueProcessor as a separate class, move all of this into it
    setupExecutotrComparatorWeightsMap();
    setupExecutorFilterList();
    this.queueProcessor = setupQueueProcessor();
    this.loadHoldingBatch();
  }

  @Override
  public void start() throws ExecutorManagerException {
    initialize();
    if (this.enableScheduleActive) {
      this.updaterThread.start();
      this.cleanerThread.start();
      this.queueProcessor.start();
      this.recoverThread.start();
      this.projectDeleteTrigger.start();
      if (this.holdBatchSwitch) {
        this.holdBatchThread.start();
      }
    }

  }

  private String findApplicationIdFromLog(final String logData) {
    final Matcher matcher = APPLICATION_ID_PATTERN.matcher(logData);
    String appId = null;
    if (matcher.find()) {
      appId = matcher.group().substring(12);
    }
    ExecutorManager.logger.info("Application ID is " + appId);
    return appId;
  }

  private QueueProcessorThread setupQueueProcessor() {
    return new QueueProcessorThread(
            this.azkProps.getBoolean(Constants.ConfigurationKeys.QUEUEPROCESSING_ENABLED, true),
            this.azkProps.getLong(Constants.ConfigurationKeys.ACTIVE_EXECUTOR_REFRESH_IN_MS, 50000),
            this.azkProps.getInt(
                    Constants.ConfigurationKeys.ACTIVE_EXECUTOR_REFRESH_IN_NUM_FLOW, 5),
            this.azkProps.getInt(
                    Constants.ConfigurationKeys.MAX_DISPATCHING_ERRORS_PERMITTED,
                    this.activeExecutors.getAll().size()),
            this.sleepAfterDispatchFailure,
            this.azkProps);
  }

  private void setupExecutotrComparatorWeightsMap() {
    // initialize comparator feature weights for executor selector from azkaban.properties
    final Map<String, String> compListStrings = this.azkProps
            .getMapByPrefix(ConfigurationKeys.EXECUTOR_SELECTOR_COMPARATOR_PREFIX);
    if (compListStrings != null) {
      this.comparatorWeightsMap = new TreeMap<>();
      for (final Map.Entry<String, String> entry : compListStrings.entrySet()) {
        this.comparatorWeightsMap.put(entry.getKey(), Integer.valueOf(entry.getValue()));
      }
    }
  }

  private void setupExecutorFilterList() {
    // initialize hard filters for executor selector from azkaban.properties
    final String filters = this.azkProps
            .getString(ConfigurationKeys.EXECUTOR_SELECTOR_FILTERS, "");
    if (filters != null) {
      this.filterList = Arrays.asList(StringUtils.split(filters, ","));
    }
  }

  private ExecutorService createExecutorInfoRefresherService() {
    return Executors.newFixedThreadPool(this.azkProps.getInt(
            ConfigurationKeys.EXECUTORINFO_REFRESH_MAX_THREADS, 5));
  }

  /**
   * {@inheritDoc}
   *
   * @see azkaban.executor.ExecutorManagerAdapter#setupExecutors()
   */
  @Override
  public void setupExecutors() throws ExecutorManagerException {
    checkMultiExecutorMode();
    this.activeExecutors.setupExecutors();
  }

  // TODO Enforced for now to ensure that users migrate to multi-executor mode acknowledgingly.
  // TODO Remove this once confident enough that all active users have already updated to some
  // version new enough to have this change - for example after 1 year has passed.
  // TODO Then also delete ConfigurationKeys.USE_MULTIPLE_EXECUTORS.
  @Deprecated
  private void checkMultiExecutorMode() {
    if (!this.azkProps.getBoolean(Constants.ConfigurationKeys.USE_MULTIPLE_EXECUTORS, false)) {
      throw new IllegalArgumentException(
              Constants.ConfigurationKeys.USE_MULTIPLE_EXECUTORS +
                      " must be true. Single executor mode is not supported any more.");
    }
  }

  /**
   * Refresh Executor stats for all the actie executors in this executorManager
   */
  private void refreshExecutors() {

    final List<Pair<Executor, Future<ExecutorInfo>>> futures =
            new ArrayList<>();
    for (final Executor executor : this.activeExecutors.getAll()) {
      // execute each executorInfo refresh task to fetch
      final Future<ExecutorInfo> fetchExecutionInfo =
              this.executorInfoRefresherService.submit(
                      () -> {
                        try {
                          ExecutorInfo executorInfo = this.apiGateway.callForJsonType(executor.getHost(),
                                  executor.getPort(), "/serverStatistics", null, ExecutorInfo.class);
                          executor.setExecutorInfo(executorInfo);
                          return executorInfo;
                        } catch (Exception e) {
                          logger.info("Failed to refresh executor {}", executor.getHost(), e);
                        }
                        return null;
                      });
      futures.add(new Pair<>(executor,
              fetchExecutionInfo));
    }

    boolean wasSuccess = true;
    long startTime = System.currentTimeMillis();
    for (final Pair<Executor, Future<ExecutorInfo>> refreshPair : futures) {
      final Executor executor = refreshPair.getFirst();
      try {
        // max 5 secs
        final ExecutorInfo executorInfo = refreshPair.getSecond().get(5, TimeUnit.SECONDS);
        logger.info("Successfully refreshed executor: {} with executor info : {}", executor, executorInfo);
      } catch (final TimeoutException e) {
        wasSuccess = false;
        logger.warn("Timed out while waiting for ExecutorInfo {} refresh ", executor, e);
      } catch (final Exception e) {
        wasSuccess = false;
        logger.warn("Failed to update ExecutorInfo for executor {} ", executor, e);
      }
      if (System.currentTimeMillis() - startTime > this.executorRefreshWaitTime) {
        logger.error("Executor refresh took longer than expected {} ms", System.currentTimeMillis() - startTime);
        wasSuccess = false;
        break;
      }
    }
    for (final Pair<Executor, Future<ExecutorInfo>> refreshPair : futures) {
      Future<ExecutorInfo> future = refreshPair.getSecond();
      if (!future.isDone()) {
        try {
          future.cancel(true);
          logger.info("refresh executor {} time out", refreshPair.getFirst());
        } catch (Exception e) {
          logger.info("Failed to cancel future for executor {} ", refreshPair.getFirst(), e);
        }
      }
    }
    // update is successful for all executors
    if (wasSuccess) {
      this.lastSuccessfulExecutorInfoRefresh = System.currentTimeMillis();
    }
  }

  /**
   * @see azkaban.executor.ExecutorManagerAdapter#disableQueueProcessorThread()
   */
  @Override
  public void disableQueueProcessorThread() {
    this.queueProcessor.setActive(false);
  }

  /**
   * @see azkaban.executor.ExecutorManagerAdapter#enableQueueProcessorThread()
   */
  @Override
  public void enableQueueProcessorThread() {
    this.queueProcessor.setActive(true);
  }

  public State getQueueProcessorThreadState() {
    return this.queueProcessor.getState();
  }

  /**
   * Returns state of QueueProcessor False, no flow is being dispatched True , flows are being
   * dispatched as expected
   */
  public boolean isQueueProcessorThreadActive() {
    return this.queueProcessor.isActive();
  }

  /**
   * Return last Successful ExecutorInfo Refresh for all active executors
   */
  public long getLastSuccessfulExecutorInfoRefresh() {
    return this.lastSuccessfulExecutorInfoRefresh;
  }

  /**
   * Get currently supported Comparators available to use via azkaban.properties
   */
  public Set<String> getAvailableExecutorComparatorNames() {
    return ExecutorComparator.getAvailableComparatorNames();

  }

  /**
   * Get currently supported filters available to use via azkaban.properties
   */
  public Set<String> getAvailableExecutorFilterNames() {
    return ExecutorFilter.getAvailableFilterNames();
  }

  @Override
  public State getExecutorManagerThreadState() {
    return this.updaterThread.getState();
  }

  public String getExecutorThreadStage() {
    return this.updaterStage.get();
  }

  @Override
  public boolean isExecutorManagerThreadActive() {
    return this.updaterThread.isAlive();
  }

  @Override
  public long getLastExecutorManagerThreadCheckTime() {
    return this.updaterThread.getLastThreadCheckTime();
  }

  @Override
  public Collection<Executor> getAllActiveExecutors() {
    return Collections.unmodifiableCollection(this.activeExecutors.getAll());
  }

  /**
   * {@inheritDoc}
   *
   * @see azkaban.executor.ExecutorManagerAdapter#fetchExecutor(int)
   */
  @Override
  public Executor fetchExecutor(final int executorId) throws ExecutorManagerException {
    for (final Executor executor : this.activeExecutors.getAll()) {
      if (executor.getId() == executorId) {
        return executor;
      }
    }
    return this.executorLoader.fetchExecutor(executorId);
  }

  @Override
  public Set<String> getPrimaryServerHosts() {
    // Only one for now. More probably later.
    final HashSet<String> ports = new HashSet<>();
    for (final Executor executor : this.activeExecutors.getAll()) {
      ports.add(executor.getHost() + ":" + executor.getPort());
    }
    return ports;
  }

  @Override
  public Set<String> getAllActiveExecutorServerHosts() {
    // Includes non primary server/hosts
    final HashSet<String> ports = new HashSet<>();
    for (final Executor executor : this.activeExecutors.getAll()) {
      ports.add(executor.getHost() + ":" + executor.getPort());
    }
    // include executor which were initially active and still has flows running
    for (final Pair<ExecutionReference, ExecutableFlow> running : this.runningExecutions.get()
            .values()) {
      final ExecutionReference ref = running.getFirst();
      if (ref.getExecutor().isPresent()) {
        final Executor executor = ref.getExecutor().get();
        ports.add(executor.getHost() + ":" + executor.getPort());
      }
    }
    return ports;
  }

  private void loadRunningExecutions() throws ExecutorManagerException {
    logger.info("Loading running flows from database..");
    final Map<Integer, Pair<ExecutionReference, ExecutableFlow>> activeFlows = this.executorLoader
            .fetchActiveFlows();
    logger.info("Loaded " + activeFlows.size() + " running flows");
    this.runningExecutions.get().putAll(activeFlows);
  }

  /*
   * load queued flows i.e with active_execution_reference and not assigned to
   * any executor
   */
  private void loadQueuedFlows() throws ExecutorManagerException {
    final List<Pair<ExecutionReference, ExecutableFlow>> retrievedExecutions =
            this.executorLoader.fetchQueuedFlows();
    if (retrievedExecutions != null) {
      for (final Pair<ExecutionReference, ExecutableFlow> pair : retrievedExecutions) {
        this.queuedFlows.enqueue(pair.getSecond(), pair.getFirst());
      }
    }
  }

  /**
   * Gets a list of all the active (running flows and non-dispatched flows) executions for a given
   * project and flow {@inheritDoc}. Results should be sorted as we assume this while setting up
   * pipelined execution Id.
   *
   * @see azkaban.executor.ExecutorManagerAdapter#getRunningFlows(int, java.lang.String)
   */
  @Override
  public List<Integer> getRunningFlows(final int projectId, final String flowId) {
    try {
      return this.executorLoader.selectUnfinishedFlows(projectId, flowId);
    } catch (ExecutorManagerException e) {
      logger.error("Failed to get running flows for project {}, flow {}", projectId, flowId, e);
    }
    return new ArrayList<>();
  }

  /* Helper method for getRunningFlows */
  private List<Integer> getRunningFlowsHelper(final int projectId, final String flowId,
                                              final Collection<Pair<ExecutionReference, ExecutableFlow>> collection,
                                              boolean isRunningExecution) {
    final List<Integer> executionIds = new ArrayList<>();
    for (final Pair<ExecutionReference, ExecutableFlow> ref : collection) {
      if (ref.getSecond().getFlowId().equals(flowId)
              && ref.getSecond().getProjectId() == projectId) {
        if (isRunningExecution) {
          try {
            ExecutableFlow runningFlow = this.executorLoader
                    .fetchExecutableFlow(ref.getSecond().getExecutionId());
            if (runningFlow != null && (Status.isStatusRunning(runningFlow.getStatus())
                    || Status.SYSTEM_PAUSED.equals(runningFlow.getStatus()))) {
              executionIds.add(ref.getFirst().getExecId());
            }
          } catch (ExecutorManagerException e) {
            executionIds.add(ref.getFirst().getExecId());
          }
        } else {
          executionIds.add(ref.getFirst().getExecId());
        }
      }
    }
    return executionIds;
  }

  /**
   * {@inheritDoc}
   *
   * @see azkaban.executor.ExecutorManagerAdapter#getActiveFlowsWithExecutor()
   */
  @Override
  public List<Pair<ExecutableFlow, Optional<Executor>>> getActiveFlowsWithExecutor()
          throws IOException {
    final List<Pair<ExecutableFlow, Optional<Executor>>> flows =
            new ArrayList<>();
    getActiveFlowsWithExecutorHelper(flows, this.queuedFlows.getAllEntries());
    getActiveFlowsWithExecutorHelper(flows, this.runningExecutions.get().values());
    // FIXME Add run_date date for page display.
    if (null != flows && !flows.isEmpty()) {
      flows.stream().forEach(pair -> {
        ExecutableFlow executableFlow = pair.getFirst();
        Map<String, String> repeatMap = executableFlow.getRepeatOption();
        if (!repeatMap.isEmpty()) {

          Long recoverRunDate = Long.valueOf(String.valueOf(repeatMap.get("startTimeLong")));

          LocalDateTime localDateTime = new LocalDateTime(new Date(recoverRunDate)).minusDays(1);

          Date date = localDateTime.toDate();

          executableFlow.setUpdateTime(date.getTime());
        } else {
          Long runDate = executableFlow.getStartTime();
          if (-1 != runDate) {
            LocalDateTime localDateTime = new LocalDateTime(new Date(runDate)).minusDays(1);

            Date date = localDateTime.toDate();

            executableFlow.setUpdateTime(date.getTime());
          } else {
            executableFlow.setUpdateTime(runDate);
          }
        }
      });
    }

    return flows;
  }

  /* Helper method for getActiveFlowsWithExecutor */
  private void getActiveFlowsWithExecutorHelper(
          final List<Pair<ExecutableFlow, Optional<Executor>>> flows,
          final Collection<Pair<ExecutionReference, ExecutableFlow>> collection) {
    for (final Pair<ExecutionReference, ExecutableFlow> ref : collection) {
      flows.add(new Pair<>(ref.getSecond(), ref
              .getFirst().getExecutor()));
    }
  }

  /**
   * Checks whether the given flow has an active (running, non-dispatched) executions {@inheritDoc}
   *
   * @see azkaban.executor.ExecutorManagerAdapter#isFlowRunning(int, java.lang.String)
   */
  @Override
  public boolean isFlowRunning(final int projectId, final String flowId) {
    boolean isRunning = false;
    isRunning =
            isRunning
                    || isFlowRunningHelper(projectId, flowId, this.queuedFlows.getAllEntries());
    isRunning =
            isRunning
                    || isFlowRunningHelper(projectId, flowId, this.runningExecutions.get().values());
    return isRunning;
  }

  /* Search a running flow in a collection */
  private boolean isFlowRunningHelper(final int projectId, final String flowId,
                                      final Collection<Pair<ExecutionReference, ExecutableFlow>> collection) {
    for (final Pair<ExecutionReference, ExecutableFlow> ref : collection) {
      if (ref.getSecond().getProjectId() == projectId
              && ref.getSecond().getFlowId().equals(flowId)) {
        return true;
      }
    }
    return false;
  }

  /**
   * Fetch ExecutableFlow from database {@inheritDoc}
   *
   * @see azkaban.executor.ExecutorManagerAdapter#getExecutableFlow(int)
   */
  @Override
  public ExecutableFlow getExecutableFlow(final int execId)
          throws ExecutorManagerException {
    return this.executorLoader.fetchExecutableFlow(execId);
  }

  @Override
  public List<ExecutableFlow> getExecutableFlowByRepeatId(int repeatId) throws ExecutorManagerException {
    return this.executorLoader.fetchExecutableFlowByRepeatId(repeatId);
  }

  /**
   * Get all active (running, non-dispatched) flows
   * <p>
   * {@inheritDoc}
   *
   * @see azkaban.executor.ExecutorManagerAdapter#getRunningFlows()
   */
  @Override
    public List<Integer> getRunningFlows() {
    try {
            return this.executorLoader.selectUnfinishedFlows();
        } catch (final ExecutorManagerException e) {
      logger.error("Failed to get running flows.", e);
    }
    return new ArrayList<>();
  }

  /*
   * Helper method to get all running flows from a Pair<ExecutionReference,
   * ExecutableFlow collection
   */
  private void getActiveFlowHelper(final ArrayList<ExecutableFlow> flows,
                                   final Collection<Pair<ExecutionReference, ExecutableFlow>> collection) {
    for (final Pair<ExecutionReference, ExecutableFlow> ref : collection) {
      flows.add(ref.getSecond());
    }
  }

  /**
   * Get execution Ids of all running (unfinished) flows
   */
  public String getRunningFlowIds() {
    final List<Integer> allIds = new ArrayList<>();
    getRunningFlowsIdsHelper(allIds, this.queuedFlows.getAllEntries());
    getRunningFlowsIdsHelper(allIds, this.runningExecutions.get().values());
    Collections.sort(allIds);
    return allIds.toString();
  }

  /**
   * Get execution Ids of all non-dispatched flows
   */
  public String getQueuedFlowIds() {
    final List<Integer> allIds = new ArrayList<>();
    getRunningFlowsIdsHelper(allIds, this.queuedFlows.getAllEntries());
    Collections.sort(allIds);
    return allIds.toString();
  }

  /**
   * Get the number of non-dispatched flows. {@inheritDoc}
   */
  @Override
  public long getQueuedFlowSize() {
    return this.queuedFlows.size();
  }

  /* Helper method to flow ids of all running flows */
  private void getRunningFlowsIdsHelper(final List<Integer> allIds,
                                        final Collection<Pair<ExecutionReference, ExecutableFlow>> collection) {
    for (final Pair<ExecutionReference, ExecutableFlow> ref : collection) {
      allIds.add(ref.getSecond().getExecutionId());
    }
  }

  @Override
  public List<ExecutableFlow> getRecentlyFinishedFlows() {
    List<ExecutableFlow> flows = new ArrayList<>();
    try {
      flows = this.executorLoader.fetchRecentlyFinishedFlows(
              RECENTLY_FINISHED_LIFETIME);
    } catch (final ExecutorManagerException e) {
      //Todo jamiesjc: fix error handling.
      logger.error("Failed to fetch recently finished flows.", e);
    }
    // FIXME Add run_date date for page display.
    if (null != flows && !flows.isEmpty()) {
      flows.stream().forEach(executableFlow -> {
        Map<String, String> repeatMap = executableFlow.getRepeatOption();
        if (!repeatMap.isEmpty()) {

          Long recoverRunDate = Long.valueOf(String.valueOf(repeatMap.get("startTimeLong")));

          LocalDateTime localDateTime = new LocalDateTime(new Date(recoverRunDate)).minusDays(1);

          Date date = localDateTime.toDate();

          executableFlow.setUpdateTime(date.getTime());
        } else {
          Long runDate = executableFlow.getStartTime();
          if (-1 != runDate) {
            LocalDateTime localDateTime = new LocalDateTime(new Date(runDate)).minusDays(1);

            Date date = localDateTime.toDate();

            executableFlow.setUpdateTime(date.getTime());
          } else {
            executableFlow.setUpdateTime(runDate);
          }
        }
      });
    }

    return flows;
  }

  @Override
  public List<ExecutableFlow> getExecutableFlows(final Project project,
                                                 final String flowId, final int skip, final int size) throws ExecutorManagerException {
    final List<ExecutableFlow> flows =
            this.executorLoader.fetchFlowHistory(project.getId(), flowId, skip, size);
    return flows;
  }

  @Override
  public List<ExecutableFlow> getExecutableFlows(final int skip, final int size)
          throws ExecutorManagerException {
    final List<ExecutableFlow> flows = this.executorLoader.fetchFlowHistory(skip, size);
    return flows;
  }

  @Override
  public List<ExecutableFlow> getMaintainedExecutableFlows(String userType, String username, List<Integer> projectIds, int skip, int size)
          throws ExecutorManagerException {
    return this.executorLoader.fetchMaintainedFlowHistory(userType, username, projectIds, skip, size);
  }

  @Override
  public List<ExecutableFlow> getExecutableFlowsQuickSearch(final String flowIdContains,
                                                            final int skip, final int size) throws ExecutorManagerException {
    final List<ExecutableFlow> flows =
            this.executorLoader.fetchFlowHistoryQuickSearch(
                    '%' + flowIdContains + '%', null, skip, size);

    return flows;
  }

  @Override
  public List<ExecutableFlow> getMaintainedFlowsQuickSearch(String flowIdContains, int skip, int size,
                                                            String username, List<Integer> projectIds) throws ExecutorManagerException {
    return this.executorLoader.fetchFlowHistoryQuickSearch('%' + flowIdContains + '%',
            username, skip, size, projectIds);
  }


  @Override
  public List<ExecutableFlow> getExecutableFlows(final String projContain, final String flowContain,
                                                 final String execIdContain, final String userContain,
                                                 final String status, final long begin, final long end,
                                                 String runDate,
                                                 final int skip, final int size, int flowType) throws ExecutorManagerException {
    final List<ExecutableFlow> flows =
            this.executorLoader.fetchFlowHistory(projContain, flowContain, execIdContain, userContain,
                    status, begin, end, runDate, skip, size, flowType);

    return flows;
  }

  @Override
  public List<CfgWebankOrganization> getAllDepartment() throws ExecutorManagerException {
    final List<CfgWebankOrganization> allDepartment = this.executorLoader.fetchAllDepartment();
    return allDepartment;
  }

  @Override
  public List<ExecutableFlow> getExecutableFlows(HistoryQueryParam param, int skip, int size) throws ExecutorManagerException {
    final List<ExecutableFlow> flows =
            this.executorLoader.fetchFlowHistory(param, skip, size);

    return flows;
  }

  @Override
  public List<ExecutableFlow> getMaintainedExecutableFlows(String projContain, String flowContain,
                                                           String execIdContain,
                                                           String userContain, String status, long begin, long end, String runDate,
                                                           int skip, int size, int flowType, String username, List<Integer> projectIds)
          throws ExecutorManagerException {
    return this.executorLoader.fetchMaintainedFlowHistory(projContain, flowContain, execIdContain,
            userContain,
            status, begin, end, runDate, skip, size, flowType, username, projectIds);
  }

  @Override
  public List<ExecutableFlow> getMaintainedExecutableFlows(HistoryQueryParam param, int skip, int size, List<Integer> projectIds)
          throws ExecutorManagerException {
    return this.executorLoader.fetchMaintainedFlowHistory(param, skip, size, projectIds);
  }

  @Override
  public List<ExecutableJobInfo> getExecutableJobs(final Project project,
                                                   final String jobId, final int skip, final int size) throws ExecutorManagerException {
    final List<ExecutableJobInfo> nodes =
            this.executorLoader.fetchJobHistory(project.getId(), jobId, skip, size);
    return nodes;
  }

  @Override
  public List<ExecutableJobInfo> getDiagnosisJobs(long endTime)
          throws ExecutorManagerException {
    return this.executorLoader.fetchDiagnosisJob(endTime);
  }

  @Override
  public List<ExecutableJobInfo> fetchExecutableJobInfo(final long startTime) throws ExecutorManagerException {
    return this.executorLoader.fetchExecutableJobInfo(startTime);
  }

  @Override
  public List<ExecutableJobInfo> quickSearchJobExecutions(final Project project,
                                                          final String jobId, final String searchTerm, final int skip, final int size) throws ExecutorManagerException {
    final List<ExecutableJobInfo> nodes =
            this.executorLoader.fetchQuickSearchJobExecutions(project.getId(), jobId, searchTerm, skip, size);
    return nodes;
  }

  @Override
  public List<ExecutableJobInfo> searchJobExecutions(HistoryQueryParam historyQueryParam, final int skip, final int size) throws ExecutorManagerException {
    final List<ExecutableJobInfo> nodes =
            this.executorLoader.searchJobExecutions(historyQueryParam, skip, size);
    return nodes;
  }

  @Override
  public ExecutableJobInfo getLastFailedJob(final Project project, final String jobId)
          throws ExecutorManagerException {
    String[] jobIdSplit = jobId.split(":");
    List<ExecutableJobInfo> jobs = this.executorLoader
            .fetchJobAllHistory(project.getId(), jobIdSplit[jobIdSplit.length - 1]);
    Collections.reverse(jobs);
    for (ExecutableJobInfo job : jobs) {
      String jobPath = azkaban.utils.StringUtils.fetchJobPath(job.getFlowId(), job.getJobId());
      if (job.getStatus().equals(Status.FAILED) && jobPath.equals(jobId)) {
        return job;
      }
    }
    return null;
  }

  @Override
  public long getExecutableJobsMoyenneRunTime(final Project project,
                                              final String jobId) throws ExecutorManagerException {
    final List<ExecutableJobInfo> jobInfos =
            this.executorLoader.fetchJobAllHistory(project.getId(), jobId);

    long moyenne = 0;
    long allRunTime = 0;
    int successFlowNum = 0;
    if (jobInfos != null) {
      for (final ExecutableJobInfo info : jobInfos) {
        if (Status.SUCCEEDED.equals(info.getStatus())) {
          successFlowNum += 1;
          allRunTime += (info.getEndTime() - info.getStartTime());
        }
      }
      if (allRunTime != 0 && successFlowNum != 0) {
        moyenne = allRunTime / successFlowNum;
      }
    }

    return moyenne;
  }

  @Override
  public int getNumberOfJobExecutions(final Project project, final String jobId)
          throws ExecutorManagerException {
    return this.executorLoader.fetchNumExecutableNodes(project.getId(), jobId);
  }

  @Override
  public int quickSearchNumberOfJobExecutions(final Project project, final String jobId, final String searchTerm)
          throws ExecutorManagerException {
    return this.executorLoader.quickSearchNumberOfJobExecutions(project.getId(), jobId, searchTerm);
  }

  @Override
  public int searchNumberOfJobExecutions(final HistoryQueryParam historyQueryParam)
          throws ExecutorManagerException {
    return this.executorLoader.searchNumberOfJobExecutions(historyQueryParam);
  }

  @Override
  public int getNumberOfExecutions(final Project project, final String flowId)
          throws ExecutorManagerException {
    return this.executorLoader.fetchNumExecutableFlows(project.getId(), flowId);
  }

  @Override
  public LogData getExecutableFlowLog(final ExecutableFlow exFlow, final int offset,
                                      final int length) throws ExecutorManagerException, IOException {
    Pair<ExecutionReference, ExecutableFlow> pair =
            this.runningExecutions.get().get(exFlow.getExecutionId());
    if (queryServerSwitch && !exFlow.isFinished()) {
      for (Executor executor : this.activeExecutors.getAll()) {
        if (exFlow.getExecutorIds().contains(executor.getId())) {
          ExecutionReference executionReference = new ExecutionReference(exFlow.getExecutionId(), executor);
          pair = new Pair<>(executionReference, exFlow);
          logger.info("Query Server get this flow {} exec node is {}", exFlow.getExecutionId(), executor.getHost());
          break;
        }
      }
    }
    if (pair != null) {
      // 正在执行工作流，从 Executor 本地读取
      final Pair<String, String> typeParam = new Pair<>("type", "flow");
      final Pair<String, String> offsetParam =
              new Pair<>("offset", String.valueOf(offset));
      final Pair<String, String> lengthParam =
              new Pair<>("length", String.valueOf(length));

      @SuppressWarnings("unchecked") final Map<String, Object> result =
              this.apiGateway.callWithReference(pair.getFirst(), ConnectorParams.LOG_ACTION,
                      typeParam, offsetParam, lengthParam);
      return LogData.createLogDataFromObject(result);
    } else {
      // 已结束工作流，从 DB/HDFS 读取
      LogData value;
      int logEncType = this.executorLoader.getLogEncType(exFlow.getExecutionId(), "", 0);
      if (this.hdfsLogSwitch && EncodingType.HDFS.getNumVal() == logEncType) {
        // 从 HDFS 获取日志
        logger.info("get flow log for {} from HDFS", exFlow.getExecutionId());
        String hdfsLogPath = this.executorLoader.getHdfsLogPath(exFlow.getExecutionId(), "", 0);
        logger.info("HDFS log path: {} for flow {}", hdfsLogPath, exFlow.getExecutionId());
        String logContent = LogUtils.loadLogFromHdfs(hdfsLogPath, offset, length);
        value = new LogData(offset, length, logContent, hdfsLogPath);
      } else {
        // 从 DB 获取日志
        logger.info("get flow log for {} from DB", exFlow.getExecutionId());
        value = this.executorLoader.fetchLogs(exFlow.getExecutionId(), "", 0, offset, length);
      }
      return value;
    }
  }

  // FIXME Get the latest number of bytes in the log.
  @Override
  public Long getLatestLogOffset(ExecutableFlow exFlow, String jobId, Long length, int attempt,
                                 User user)
          throws ExecutorManagerException, IOException {
    final Pair<ExecutionReference, ExecutableFlow> pair =
            this.runningExecutions.get().get(exFlow.getExecutionId());
    if (pair != null) {
      logger.info("get offset from local file.");
      Pair<String, String> jobIdParam = new Pair<>("jobId", jobId);
      Pair<String, String> lengthParam = new Pair<>("len", String.valueOf(length));
      Pair<String, String> attemptParam = new Pair<>("attempt", String.valueOf(attempt));

      Map<String, Object> ret = this.apiGateway.callWithReferenceByUser(pair.getFirst(), ConnectorParams.OFFSET_ACTION,
              user.getUserId(), jobIdParam, lengthParam, attemptParam);
      return Long.valueOf(ret.get("offset").toString());
    } else {
      int logEncType = this.executorLoader.getLogEncType(exFlow.getExecutionId(), jobId, attempt);
      if (this.azkProps.getBoolean(ConfigurationKeys.HDFS_LOG_SWITCH, false)
              && EncodingType.HDFS.getNumVal() == logEncType) {
        // 从 HDFS 获取日志
        logger.info("get job offset for [execId: {}, flowId: {}, jobId: {}] from HDFS",
                exFlow.getExecutionId(), exFlow.getId(), jobId);
        String hdfsLogPath = this.executorLoader.getHdfsLogPath(exFlow.getExecutionId(), jobId,
                attempt);
        logger.info("HDFS log path: {} for [execId: {}, flowId: {}, jobId: {}]",
                hdfsLogPath, exFlow.getExecutionId(), exFlow.getId(), jobId);
        String logContent = LogUtils.loadAllLogFromHdfs(hdfsLogPath);
        int maxSize = logContent.length();
        return maxSize - length > 0 ? maxSize - length : 0;
      } else {
        logger.info("get offset from db.");
        return this.executorLoader.getJobLogOffset(exFlow.getExecutionId(), jobId, attempt, length);
      }
    }
  }

  @Override
  public LogData getExecutionJobLog(final ExecutableFlow exFlow, final String jobId,
                                    final int offset, final int length, final int attempt)
          throws ExecutorManagerException, IOException {
    final Pair<ExecutionReference, ExecutableFlow> pair =
            this.runningExecutions.get().get(exFlow.getExecutionId());
    if (pair != null) {
      // 正在执行任务，从 Executor 本地读取
      final Pair<String, String> typeParam = new Pair<>("type", "job");
      final Pair<String, String> jobIdParam =
              new Pair<>("jobId", jobId);
      final Pair<String, String> offsetParam =
              new Pair<>("offset", String.valueOf(offset));
      final Pair<String, String> lengthParam =
              new Pair<>("length", String.valueOf(length));
      final Pair<String, String> attemptParam =
              new Pair<>("attempt", String.valueOf(attempt));

      @SuppressWarnings("unchecked") final Map<String, Object> result =
              this.apiGateway.callWithReference(pair.getFirst(), ConnectorParams.LOG_ACTION,
                      typeParam, jobIdParam, offsetParam, lengthParam, attemptParam);
      return LogData.createLogDataFromObject(result);
    } else {
      // 已结束任务，从 DB/HDFS 读取
      final LogData value;
      int logEncType = this.executorLoader.getLogEncType(exFlow.getExecutionId(), jobId, attempt);
      if (this.azkProps.getBoolean(ConfigurationKeys.HDFS_LOG_SWITCH, false)
              && EncodingType.HDFS.getNumVal() == logEncType) {
        // 从 HDFS 获取日志
        logger.info("get job log for [execId: {}, flowId: {}, jobId: {}] from HDFS",
                exFlow.getExecutionId(), exFlow.getId(), jobId);
        String hdfsLogPath = this.executorLoader.getHdfsLogPath(exFlow.getExecutionId(), jobId,
                attempt);
        logger.info("HDFS log path: {} for [execId: {}, flowId: {}, jobId: {}]",
                hdfsLogPath, exFlow.getExecutionId(), exFlow.getId(), jobId);
        String logContent = LogUtils.loadLogFromHdfs(hdfsLogPath, offset, length);
        value = new LogData(offset, length, logContent, hdfsLogPath);
      } else {
        // 从 DB 获取日志
        logger.info("get job log for [execId: {}, flowId: {}, jobId: {}] from DB",
                exFlow.getExecutionId(), exFlow.getId(), jobId);
        value = this.executorLoader.fetchLogs(exFlow.getExecutionId(), jobId, attempt,
                offset, length);
      }
      return value;
    }
  }

  @Override
  public List<Object> getExecutionJobStats(final ExecutableFlow exFlow, final String jobId,
                                           final int attempt) throws ExecutorManagerException {
    final Pair<ExecutionReference, ExecutableFlow> pair =
            this.runningExecutions.get().get(exFlow.getExecutionId());
    if (pair == null) {
      return this.executorLoader.fetchAttachments(exFlow.getExecutionId(), jobId,
              attempt);
    }

    final Pair<String, String> jobIdParam = new Pair<>("jobId", jobId);
    final Pair<String, String> attemptParam =
            new Pair<>("attempt", String.valueOf(attempt));

    @SuppressWarnings("unchecked") final Map<String, Object> result =
            this.apiGateway.callWithReference(pair.getFirst(), ConnectorParams.ATTACHMENTS_ACTION,
                    jobIdParam, attemptParam);

    @SuppressWarnings("unchecked") final List<Object> jobStats = (List<Object>) result
            .get("attachments");

    return jobStats;
  }

  @Override
  public String getJobLinkUrl(final ExecutableFlow exFlow, final String jobId, final int attempt) {
    if (!this.azkProps.containsKey(ConfigurationKeys.RESOURCE_MANAGER_JOB_URL) || !this.azkProps
            .containsKey(ConfigurationKeys.HISTORY_SERVER_JOB_URL) || !this.azkProps
            .containsKey(ConfigurationKeys.SPARK_HISTORY_SERVER_JOB_URL)) {
      return null;
    }

    final String applicationId = getApplicationId(exFlow, jobId, attempt);
    if (applicationId == null) {
      return null;
    }

    final URL url;
    final String jobLinkUrl;
    boolean isRMJobLinkValid = true;

    try {
      url = new URL(this.azkProps.getString(ConfigurationKeys.RESOURCE_MANAGER_JOB_URL)
              .replace(APPLICATION_ID, applicationId));
      final String keytabPrincipal = requireNonNull(
              this.azkProps.getString(ConfigurationKeys.AZKABAN_KERBEROS_PRINCIPAL));
      final String keytabPath = requireNonNull(this.azkProps.getString(ConfigurationKeys
              .AZKABAN_KEYTAB_PATH));
      final HttpURLConnection connection = AuthenticationUtils.loginAuthenticatedURL(url,
              keytabPrincipal, keytabPath);

      try (final BufferedReader in = new BufferedReader(
              new InputStreamReader(connection.getInputStream(), StandardCharsets.UTF_8))) {
        String inputLine;
        while ((inputLine = in.readLine()) != null) {
          if (FAILED_TO_READ_APPLICATION_PATTERN.matcher(inputLine).find()
                  || INVALID_APPLICATION_ID_PATTERN.matcher(inputLine).find()) {
            logger.info(
                    "RM job link is invalid or has expired for application_" + applicationId);
            isRMJobLinkValid = false;
            break;
          }
        }
      }
    } catch (final Exception e) {
      logger.error("Failed to get job link for application_" + applicationId, e);
      return null;
    }

    if (isRMJobLinkValid) {
      jobLinkUrl = url.toString();
    } else {
      // If RM job link is invalid or has expired, fetch the job link from JHS or SHS.
      if (exFlow.getExecutableNode(jobId).getType().equals(SPARK_JOB_TYPE)) {
        jobLinkUrl =
                this.azkProps.get(ConfigurationKeys.SPARK_HISTORY_SERVER_JOB_URL).replace
                        (APPLICATION_ID, applicationId);
      } else {
        jobLinkUrl =
                this.azkProps.get(ConfigurationKeys.HISTORY_SERVER_JOB_URL).replace(APPLICATION_ID,
                        applicationId);
      }
    }

    logger.info(
            "Job link url is " + jobLinkUrl + " for execution " + exFlow.getExecutionId() + ", job "
                    + jobId);
    return jobLinkUrl;
  }

  private String getApplicationId(final ExecutableFlow exFlow, final String jobId,
                                  final int attempt) {
    String applicationId;
    boolean finished = false;
    int offset = 0;
    try {
      while (!finished) {
        final LogData data = getExecutionJobLog(exFlow, jobId, offset, 50000, attempt);
        if (data != null) {
          applicationId = findApplicationIdFromLog(data.getData());
          if (applicationId != null) {
            return applicationId;
          }
          offset = data.getOffset() + data.getLength();
          logger.info("Get application ID for execution " + exFlow.getExecutionId() + ", job"
                  + " " + jobId + ", attempt " + attempt + ", data offset " + offset);
        } else {
          finished = true;
        }
      }
    } catch (final ExecutorManagerException | IOException e) {
      logger.error("Failed to get application ID for execution " + exFlow.getExecutionId() +
              ", job " + jobId + ", attempt " + attempt + ", data offset " + offset, e);
    }
    return null;
  }

  @Override
  public JobMetaData getExecutionJobMetaData(final ExecutableFlow exFlow,
                                             final String jobId, final int offset, final int length, final int attempt)
          throws ExecutorManagerException {
    final Pair<ExecutionReference, ExecutableFlow> pair =
            this.runningFlows.get(exFlow.getExecutionId());
    if (pair != null) {

      final Pair<String, String> typeParam = new Pair<>("type", "job");
      final Pair<String, String> jobIdParam =
              new Pair<>("jobId", jobId);
      final Pair<String, String> offsetParam =
              new Pair<>("offset", String.valueOf(offset));
      final Pair<String, String> lengthParam =
              new Pair<>("length", String.valueOf(length));
      final Pair<String, String> attemptParam =
              new Pair<>("attempt", String.valueOf(attempt));

      @SuppressWarnings("unchecked") final Map<String, Object> result =
              this.apiGateway.callWithReference(pair.getFirst(), ConnectorParams.METADATA_ACTION,
                      typeParam, jobIdParam, offsetParam, lengthParam, attemptParam);
      return JobMetaData.createJobMetaDataFromObject(result);
    } else {
      return null;
    }
  }

  /**
   * if flows was dispatched to an executor, cancel by calling Executor else if flow is still in
   * queue, remove from queue and finalize {@inheritDoc}
   *
   * @see azkaban.executor.ExecutorManagerAdapter#cancelFlow(azkaban.executor.ExecutableFlow,
   * java.lang.String)
   */
  @Override
  public void cancelFlow(final ExecutableFlow exFlow, final String userId)
          throws ExecutorManagerException {
    logger.info("Cancelling flow {}, by user {}", exFlow.getExecutionId(), userId);
    cancelFlow(exFlow, userId, ConnectorParams.CANCEL_ACTION);
  }

  @Override
  public void forceCancelFlow(ExecutableFlow exFlow, String userId) throws ExecutorManagerException {
    synchronized ((exFlow.getExecutionId() + "").intern()) {
      this.executionFinalizer
              .finalizeFlow(exFlow, "Force cancel flow", null);  // 通用告警
      this.executorLoader.updateHoldBatchNotResumeByExecId(exFlow.getExecutionId());
    }
  }

  @Override
  public void superKillFlow(final ExecutableFlow exFlow, final String userId)
          throws ExecutorManagerException {
    Executor executor = executorLoader.fetchExecutorByExecutionId(exFlow.getExecutionId());
    if (executor == null) {
      throw new ExecutorManagerException("Find Executor Error!");
    }
    this.apiGateway.callWithExecutionId(executor.getHost(), executor.getPort(),
            ConnectorParams.SUPER_KILL_ACTION, exFlow.getExecutionId(), userId);
    this.executorLoader.updateHoldBatchNotResumeByExecId(exFlow.getExecutionId());
  }

  private void cancelFlow(final ExecutableFlow exFlow, final String userId, final String actionType)
          throws ExecutorManagerException {
    synchronized ((exFlow.getExecutionId() + "").intern()) {
      if (this.runningExecutions.get().containsKey(exFlow.getExecutionId())) {
        final Pair<ExecutionReference, ExecutableFlow> pair =
                this.runningExecutions.get().get(exFlow.getExecutionId());
        this.apiGateway.callWithReferenceByUser(pair.getFirst(), actionType,
                userId);
        logger.info("kill qunning flow {}, by user {}", exFlow.getExecutionId(), userId);
      } else if (this.queuedFlows.hasExecution(exFlow.getExecutionId())) {//如果是正在排队的Flow 就直接在这边处理
        logger.info("kill queued flow {}, by user {}", exFlow.getExecutionId(), userId);
        this.queuedFlows.dequeue(exFlow.getExecutionId());
        this.executionFinalizer
                .finalizeFlow(exFlow, "Cancelled before dispatching to executor", null);  // 通用告警
      } else {
        logger.info("kill finished flow {}, by user {}", exFlow.getExecutionId(), userId);
      }
      this.executorLoader.updateHoldBatchNotResumeByExecId(exFlow.getExecutionId());
    }
  }

  @Override
  public void resumeFlow(final ExecutableFlow exFlow, final String userId)
          throws ExecutorManagerException {
    synchronized ((exFlow.getExecutionId() + "").intern()) {
      final Pair<ExecutionReference, ExecutableFlow> pair =
              this.runningExecutions.get().get(exFlow.getExecutionId());
      if (pair == null) {
        throw new ExecutorManagerException("Execution "
                + exFlow.getExecutionId() + " of flow " + exFlow.getFlowId()
                + " isn't running.");
      }
      this.apiGateway
              .callWithReferenceByUser(pair.getFirst(), ConnectorParams.RESUME_ACTION, userId);
    }
  }

  // FIXME You can set the job stream to a failed state.
  @Override
  public void setFlowFailed(ExecutableFlow exFlow, String userId, List<Pair<String, String>> param) throws Exception {
    synchronized ((exFlow.getExecutionId() + "").intern()) {
      final Pair<ExecutionReference, ExecutableFlow> pair =
              this.runningExecutions.get().get(exFlow.getExecutionId());
      if (pair == null) {
        throw new Exception("Execution "
                + exFlow.getExecutionId() + " of flow " + exFlow.getFlowId()
                + " isn't running.");
      }
      this.apiGateway.callForJsonObjectMap(pair.getFirst().getExecutor().get().getHost(),
              pair.getFirst().getExecutor().get().getPort(), "/executor", param);
    }
  }

  private String getToken() {
    String token = "";
    try {
      String dss_secret = azkProps.getString("dss.secret", "***REMOVED***");
      token = JwtTokenUtils.getToken(null, false, dss_secret, 300);
    } catch (RuntimeException e) {
      logger.error("getToken failed when execute httppost ,caused by {}", e);
    }
    return token;
  }


  private String getNewToken() {
    String token = "";
    try {
      String dss_secret = azkProps.getString(ConnectorParams.TOKEN_PARAM_NEW_KEY, "zzee|getsdghthb&dss@2021");
      token = JwtTokenUtils.getToken(null, false, dss_secret, 300);
    } catch (RuntimeException e) {
      logger.error("getToken failed when execute httppost ,caused by {}", e);
    }
    return "&" + ConnectorParams.TOKEN_PARAM_NEW + "=" + token;
  }

  @Override
  public String setJobFailed(ExecutableFlow exFlow, String userId,
                             List<Pair<String, String>> paramList)
          throws Exception {
    synchronized ((exFlow.getExecutionId() + "").intern()) {
      Pair<ExecutionReference, ExecutableFlow> pair = this.runningExecutions.get()
              .get(exFlow.getExecutionId());
      if (pair == null) {
        throw new Exception("Execution "
                + exFlow.getExecutionId() + " of flow " + exFlow.getFlowId()
                + " isn't running.");
      }

      Map<String, Object> jsonObjectMap = this.apiGateway.callForJsonObjectMap(
              pair.getFirst().getExecutor().get().getHost(),
              pair.getFirst().getExecutor().get().getPort(), "/executor", paramList);

      return (String) jsonObjectMap.get(ConnectorParams.STATUS_PARAM);
    }

  }

  // FIXME You can close running tasks for execution.
  @Override
  public String setJobDisabled(ExecutableFlow exFlow, String userId, String request) throws Exception {
    synchronized ((exFlow.getExecutionId() + "").intern()) {
      final Pair<ExecutionReference, ExecutableFlow> pair =
              this.runningExecutions.get().get(exFlow.getExecutionId());
      if (pair == null) {
        throw new Exception("Execution "
                + exFlow.getExecutionId() + " of flow " + exFlow.getFlowId()
                + " isn't running.");
      }

      String url = "http://" + pair.getFirst().getExecutor().get().getHost() + ":" + pair.getFirst().getExecutor().get().getPort() + "/executor?"
              + "action=" + ConnectorParams.DISABLE_JOB_ACTION + "&execid=" + exFlow.getExecutionId() + "&user=" + userId + "&token=" + getToken() + getNewToken();

      return this.apiGateway.httpPost(url, request);
    }
  }

  @Override
  public String setJobOpen(ExecutableFlow exFlow, String userId, String request) throws Exception {
    synchronized ((exFlow.getExecutionId() + "").intern()) {
      final Pair<ExecutionReference, ExecutableFlow> pair =
              this.runningExecutions.get().get(exFlow.getExecutionId());
      if (pair == null) {
        throw new Exception("Execution "
                + exFlow.getExecutionId() + " of flow " + exFlow.getFlowId()
                + " isn't running.");
      }

      String url = "http://" + pair.getFirst().getExecutor().get().getHost() + ":" + pair.getFirst().getExecutor().get().getPort() + "/executor?"
              + "action=" + ConnectorParams.OPEN_JOB_ACTION + "&execid=" + exFlow.getExecutionId() + "&user=" + userId + "&token=" + getToken() + getNewToken();

      return this.apiGateway.httpPost(url, request);
    }
  }

  // FIXME The FAILED_WAITING status task can be rerun.
  @Override
  public String retryFailedJobs(ExecutableFlow exFlow, String userId, String request) throws Exception {
    synchronized ((exFlow.getExecutionId() + "").intern()) {
      final Pair<ExecutionReference, ExecutableFlow> pair =
              this.runningExecutions.get().get(exFlow.getExecutionId());
      if (pair == null) {
        throw new Exception("Execution "
                + exFlow.getExecutionId() + " of flow " + exFlow.getFlowId()
                + " isn't running.");
      }

      String url = "http://" + pair.getFirst().getExecutor().get().getHost() + ":" + pair.getFirst().getExecutor().get().getPort() + "/executor?"
              + "action=" + ConnectorParams.RETRY_FAILED_JOBS_ACTION + "&execid=" + exFlow.getExecutionId() + "&user=" + userId + "&token=" + getToken() + getNewToken();

      return this.apiGateway.httpPost(url, request);
    }
  }

  // FIXME You can skip tasks in the FAILED_WAITING state.
  @Override
  public String skipFailedJobs(ExecutableFlow exFlow, String userId, String request) throws Exception {
    synchronized ((exFlow.getExecutionId() + "").intern()) {
      final Pair<ExecutionReference, ExecutableFlow> pair =
              this.runningExecutions.get().get(exFlow.getExecutionId());
      if (pair == null) {
        throw new Exception("Execution "
                + exFlow.getExecutionId() + " of flow " + exFlow.getFlowId()
                + " isn't running.");
      }
      String url = "http://" + pair.getFirst().getExecutor().get().getHost() + ":" + pair.getFirst().getExecutor().get().getPort() + "/executor?"
              + "action=" + ConnectorParams.SKIP_FAILED_JOBS_ACTION + "&execid=" + exFlow.getExecutionId() + "&user=" + userId + "&token=" + getToken() + getNewToken();

      return this.apiGateway.httpPost(url, request);
    }
  }


  @Override
  public void pauseFlow(final ExecutableFlow exFlow, final String userId, long timeoutMs)
          throws ExecutorManagerException {
    synchronized ((exFlow.getExecutionId() + "").intern()) {
      final Pair<ExecutionReference, ExecutableFlow> pair =
              this.runningExecutions.get().get(exFlow.getExecutionId());
      if (pair == null) {
        throw new ExecutorManagerException("Execution "
                + exFlow.getExecutionId() + " of flow " + exFlow.getFlowId()
                + " isn't running.");
      }

      Pair<String, String> pauseTimeoutMs = new Pair<>("pauseTimeoutMs", timeoutMs + "");
      this.apiGateway
              .callWithReferenceByUser(pair.getFirst(), ConnectorParams.PAUSE_ACTION, userId,
                      pauseTimeoutMs);
    }
  }

  @Override
  public void pauseExecutingJobs(final ExecutableFlow exFlow, final String userId,
                                 final String... jobIds) throws ExecutorManagerException {
    modifyExecutingJobs(exFlow, ConnectorParams.MODIFY_PAUSE_JOBS, userId,
            jobIds);
  }

  @Override
  public void resumeExecutingJobs(final ExecutableFlow exFlow, final String userId,
                                  final String... jobIds) throws ExecutorManagerException {
    modifyExecutingJobs(exFlow, ConnectorParams.MODIFY_RESUME_JOBS, userId,
            jobIds);
  }

  @Override
  public void retryFailures(final ExecutableFlow exFlow, final String userId, final String retryJson)
          throws ExecutorManagerException {
    modifyExecutingJobs(exFlow, ConnectorParams.MODIFY_RETRY_FAILURES, userId, retryJson, null);
  }

  // FIXME You can skip all tasks in the FAILED_WAITING state.
  @Override
  public void skipAllFailures(ExecutableFlow exFlow, String userId) throws ExecutorManagerException {
    synchronized ((exFlow.getExecutionId() + "").intern()) {
      final Pair<ExecutionReference, ExecutableFlow> pair =
              this.runningExecutions.get().get(exFlow.getExecutionId());
      if (pair == null) {
        throw new ExecutorManagerException("Execution "
                + exFlow.getExecutionId() + " of flow " + exFlow.getFlowId()
                + " isn't running.");
      }
      this.apiGateway.callWithReferenceByUser(pair.getFirst(),
              ConnectorParams.SKIPPED_ALL_FAILED_JOBS_ACTION, userId);
    }
  }

  @Override
  public String retryExecutingJobs(final ExecutableFlow exFlow, final String userId,
                                   final String... jobIds) throws ExecutorManagerException {
    Map<String, Object> ret = modifyExecutingJobs(exFlow,
            ConnectorParams.MODIFY_RETRY_JOBS, userId,
            jobIds);
    return ret.toString();
  }

  @Override
  public void disableExecutingJobs(final ExecutableFlow exFlow, final String userId,
                                   final String... jobIds) throws ExecutorManagerException {
    modifyExecutingJobs(exFlow, ConnectorParams.MODIFY_DISABLE_JOBS, userId,
            jobIds);
  }

  @Override
  public void enableExecutingJobs(final ExecutableFlow exFlow, final String userId,
                                  final String... jobIds) throws ExecutorManagerException {
    modifyExecutingJobs(exFlow, ConnectorParams.MODIFY_ENABLE_JOBS, userId,
            jobIds);
  }

  @Override
  public void cancelExecutingJobs(final ExecutableFlow exFlow, final String userId,
                                  final String... jobIds) throws ExecutorManagerException {
    modifyExecutingJobs(exFlow, ConnectorParams.MODIFY_CANCEL_JOBS, userId,
            jobIds);
  }

  private Map<String, Object> modifyExecutingJobs(final ExecutableFlow exFlow,
                                                  final String command, final String userId, final String... jobIds)
          throws ExecutorManagerException {
    return modifyExecutingJobs(exFlow, command, userId, null, jobIds);
  }

  @SuppressWarnings("unchecked")
  private Map<String, Object> modifyExecutingJobs(final ExecutableFlow exFlow,
                                                  final String command, final String userId, final String retryJson, final String... jobIds)
          throws ExecutorManagerException {
    synchronized ((exFlow.getExecutionId() + "").intern()) {
      final Pair<ExecutionReference, ExecutableFlow> pair =
              this.runningExecutions.get().get(exFlow.getExecutionId());
      if (pair == null) {
        throw new ExecutorManagerException("Execution "
                + exFlow.getExecutionId() + " of flow " + exFlow.getFlowId()
                + " isn't running.");
      }

      final Map<String, Object> response;
      if (jobIds != null && jobIds.length > 0) {
        for (final String jobId : jobIds) {
          if (!jobId.isEmpty()) {
            final ExecutableNode node = exFlow.getExecutableNodePath(jobId);
            if (node == null) {
              throw new ExecutorManagerException("Job " + jobId
                      + " doesn't exist in execution " + exFlow.getExecutionId()
                      + ".");
            }
          }
        }
        final String ids = StringUtils.join(jobIds, ',');
        response =
                this.apiGateway.callWithReferenceByUser(pair.getFirst(),
                        ConnectorParams.MODIFY_EXECUTION_ACTION, userId,
                        new Pair<>(
                                ConnectorParams.MODIFY_EXECUTION_ACTION_TYPE, command),
                        new Pair<>(ConnectorParams.MODIFY_JOBS_LIST, ids));
      } else {
        response =
                this.apiGateway.callWithReferenceByUser(pair.getFirst(),
                        ConnectorParams.MODIFY_EXECUTION_ACTION, userId,
                        new Pair<>(
                                ConnectorParams.MODIFY_EXECUTION_ACTION_TYPE, command),
                        new Pair<>(
                                "retryFailedJobs", retryJson));
      }

      return response;
    }
  }

  @Override
  public String submitExecutableFlow(final ExecutableFlow exflow, final String userId)
          throws ExecutorManagerException, SystemUserManagerException {
    Map<String, Object> result = new HashMap<>();
    this.submitExecutableFlow(exflow, userId, result);
    return (String) result.get("message");
  }

  @Override
  public void submitExecutableFlow(final ExecutableFlow exflow, final String userId, Map<String, Object> result)
          throws ExecutorManagerException, SystemUserManagerException {

    if (!this.enableScheduleActive) {
      logger.warn("Schedule disable do not support to exec flow {}, user {}", exflow.getId(), userId);
      return;
    }

    final String exFlowKey = exflow.getProjectName() + "." + exflow.getId() + ".submitFlow";
    // using project and flow name to prevent race condition when same flow is submitted by API and schedule at the same time
    // causing two same flow submission entering this piece.
    synchronized (exFlowKey.intern()) {
      String frequentBatchId = (String) exflow.getOtherOption().getOrDefault("frequentBatchId", "");
      if (StringUtils.isNotEmpty(frequentBatchId)) {
        this.executorLoader.addHoldBatchFrequent(frequentBatchId, exflow);
        result.put("message", "");
        return;
      }
      final String flowId = exflow.getFlowId();

      logger.info("Submitting execution flow " + flowId + " by " + userId);

      String message = "";
      if (this.queuedFlows.isFull()) {
        message = String.format("Failed to submit %s for project %s. Azkaban has overrun its webserver queue capacity", flowId, exflow.getProjectName());
        logger.error(message);
        this.commonMetrics.markSubmitFlowFail();
      } else {
        final int projectId = exflow.getProjectId();
        exflow.setSubmitUser(userId);
        // FIXME The multi-tenant function allocates the corresponding executor to the current job stream according to the submitting user.
        List<Integer> executorIds = null;
        try {
          executorIds = this.executorLoader.getExecutorIdsBySubmitUser(exflow.getSubmitUser());
        } catch (ExecutorManagerException em) {
          logger.error("get executorId by " + exflow.getSubmitUser() + ", failed", em);
          throw new ExecutorManagerException("get executorId by " + exflow.getSubmitUser() + ", failed", em);
        }
        if (executorIds == null || executorIds.size() == 0) {
          logger.error("can not found executorId by " + exflow.getSubmitUser());
          throw new ExecutorManagerException("用户:" + exflow.getSubmitUser() + "，没有分配executor");
        }

        exflow.setExecutorIds(executorIds);
        exflow.setSubmitTime(System.currentTimeMillis());

        ExecutionOptions options = exflow.getExecutionOptions();
        if (options == null) {
          options = new ExecutionOptions();
        }

        if (options.getDisabledJobs() != null && !options.getDisabledJobs().isEmpty()) {
          FlowUtils.applyDisabledJobs(options.getDisabledJobs(), exflow);
        } else if (options.getEnabledJobs() != null && !options.getEnabledJobs().isEmpty()) {
          List<Object> enabledJobs = options.getEnabledJobs();
          for (ExecutableNode exeNode : exflow.getExecutableNodes()) {
            exeNode.setStatus(Status.DISABLED);
          }
          for (int i = 0; i < enabledJobs.size(); i++) {
            Object enabled = enabledJobs.get(i);
            if (enabled instanceof String) {
              final String nodeName = (String) enabled;
              final ExecutableNode node = exflow.getExecutableNode(nodeName);
              if (node != null) {
                node.setStatus(Status.READY);
                enabledJobs.remove(i);
                i--;
              }

            }
          }
          if (org.apache.commons.collections.CollectionUtils.isNotEmpty(enabledJobs)) {
            FlowUtils.applyEnabledJobs(enabledJobs, exflow);
          }
        }

        String batchId = this.holdBatchContext.isInBatch(exflow.getProjectName(), exflow.getId(), exflow.getSubmitUser());
        boolean isInBatch = this.holdBatchSwitch && StringUtils.isNotEmpty(batchId);

        if (isInBatch && exflow.getFlowType() == 4) {
          result.put("message", Constants.HOLD_BATCH_REJECT);
          return;
        }

        // Get collection of running flows given a project and a specific flow name
        final List<Integer> running = getRunningFlows(projectId, flowId);

        int resumeStatus = 0;

        if (isInBatch) {
          exflow.getOtherOption().put("isHoldingSubmit", true);
          List<Integer> alertIdList = this.executorLoader
                  .queryWaitingFlow(exflow.getProjectName(), exflow.getId());
          try {
            message = checkFLowSkip(exflow, flowId, message, options, running, alertIdList);
          } catch (ExecutorManagerException e) {
            resumeStatus = 2;
          }
        } else {
          message = checkFLowSkip(exflow, flowId, message, options, running, new ArrayList<>());
        }

        final boolean memoryCheck = !ProjectWhitelist.isProjectWhitelisted(exflow.getProjectId(),
                ProjectWhitelist.WhitelistType.MemoryCheck);
        options.setMemoryCheck(memoryCheck);
        // first insert Data in DB
        // The exflow id is set by the loader. So it's unavailable until after
        // this call. 写数据库表execution_flows，状态为preparing。
        calculateRunDate(exflow);
        if (isInBatch) {
          this.executorLoader.addHoldBatchAlert(batchId, exflow, resumeStatus);
          message += "Execution save to hold batch with key: " + exFlowKey;
          logger.info(message);
          result.put("message", message);
          result.put("code", 200);
          return;
        }

        // 查询工作流应用信息，并将子系统、关键路径属性赋予工作流
        FlowBusiness flowBusiness = this.projectLoader.getFlowBusiness(projectId, flowId, "");
        if (flowBusiness != null) {
          exflow.setSubsystem(flowBusiness.getSubsystem());
          exflow.setBusPath(flowBusiness.getBusPath());
        }
        // 查询工作流提交用户的部门，将部门信息赋予工作流
        WtssUser wtssUser = this.systemUserLoader.getWtssUserByUsername(userId);
        exflow.setSubmitDepartmentId(wtssUser.getDepartmentId() + "");
        this.executorLoader.uploadExecutableFlow(exflow);

        // We create an active flow reference in the datastore. If the upload
        // fails, we remove the reference.
        final ExecutionReference reference = new ExecutionReference(exflow.getExecutionId());

        this.executorLoader.addActiveExecutableReference(reference);
        logger.info("Added executable reference. Starting to enqueue");
        this.queuedFlows.enqueue(exflow, reference);
        message += "Execution queued successfully with exec id " + exflow.getExecutionId();
        logger.info(message);
        this.commonMetrics.markSubmitFlowSuccess();
      }
      result.put("code", 200);
      result.put("message", message);
    }
  }

  private void refreshFlowExecutorIDs(ExecutableFlow exflow) {
    List<Integer> executorIds = null;
    try {
      executorIds = this.executorLoader.getExecutorIdsBySubmitUser(exflow.getSubmitUser());
    } catch (Exception em) {
      logger.info("get executorId by " + exflow.getSubmitUser() + ", failed", em);
    }
    if (executorIds == null || executorIds.size() == 0) {
      logger.info("can not found executorId by " + exflow.getSubmitUser());
    } else {
      exflow.setExecutorIds(executorIds);
    }
  }

  private String checkFLowSkip(ExecutableFlow exflow, String flowId, String message,
                               ExecutionOptions options, List<Integer> running, List<Integer> waiting) throws ExecutorManagerException {
    int sum = running.size() + waiting.size();
    if (sum > 0) {
      if (sum > this.maxConcurrentRunsOneFlow) {
        this.commonMetrics.markSubmitFlowSkip();
        throw new ExecutorManagerException("Flow " + flowId
                + " has more than " + this.maxConcurrentRunsOneFlow + " concurrent runs. Skipping",
                ExecutorManagerException.Reason.SkippedExecution);
      } else if (options.getConcurrentOption().equals(
              ExecutionOptions.CONCURRENT_OPTION_PIPELINE)) {
        if (running.size() > 0) {
          Collections.sort(running);
          final Integer runningExecId = running.get(running.size() - 1);
          options.setPipelineExecutionId(runningExecId);
          message =
                  "Flow " + flowId + " is already running with exec id "
                          + runningExecId + ". Pipelining level "
                          + options.getPipelineLevel() + ". \n";
        }
      } else if (options.getConcurrentOption().equals(
              ExecutionOptions.CONCURRENT_OPTION_SKIP)) {
        this.commonMetrics.markSubmitFlowSkip();
        throw new ExecutorManagerException("工作流 " + flowId
                + " 正在运行. 跳过执行.",
                ExecutorManagerException.Reason.SkippedExecution);
      } else if (exflow.getFlowType() == 6 && running.size() > 2) {
        this.commonMetrics.markSubmitFlowSkip();
        throw new ExecutorManagerException("Flow " + flowId
                + " is already running 3 task. Skipping execution.",
                ExecutorManagerException.Reason.SkippedExecution);
      } else {
        // The settings is to run anyways.
        message =
                "Flow " + flowId + " is already running with exec id "
                        + StringUtils.join(running, ",")
                        + ". Will execute concurrently. \n";
      }
    }
    return message;
  }

  private void calculateRunDate(ExecutableFlow executableFlow) {
    //计算RunDate日期
    Map<String, String> repeatMap = executableFlow.getRepeatOption();

    if (!repeatMap.isEmpty()) {
      Long recoverRunDate = Long.valueOf(String.valueOf(repeatMap.get("startTimeLong")));
      LocalDateTime localDateTime = new LocalDateTime(new Date(recoverRunDate)).minusDays(1);
      Date date = localDateTime.toDate();
      executableFlow.setUpdateTime(date.getTime());
    } else {
      String runDatestr = executableFlow.getExecutionOptions().getFlowParameters()
              .get("run_date");
      Object runDateOther = executableFlow.getOtherOption().get("run_date");
      if (runDatestr != null && !"".equals(runDatestr) && !runDatestr.isEmpty()) {
        try {
          executableFlow.setUpdateTime(Long.parseLong(runDatestr));
        } catch (Exception e) {
          logger.error("rundate convert failed (String to long) {}", runDatestr, e);
        } finally {
          executableFlow.setUpdateTime(0);
          executableFlow.getOtherOption().put("run_date", runDatestr);
        }
      } else if (runDateOther != null && !"".equals(runDateOther.toString()) && !runDateOther
              .toString().isEmpty()) {
        String runDateTime = (String) runDateOther;
        runDateTime = runDateTime.replaceAll("\'", "").replaceAll("\"", "");
        if (SystemBuiltInParamReplacer.dateFormatCheck(runDateTime)) {
          executableFlow.setUpdateTime(0);
          executableFlow.getOtherOption().put("run_date", runDateTime);
        } else {
          if (-1 != executableFlow.getStartTime()) {
            LocalDateTime localDateTime = new LocalDateTime(
                    new Date(executableFlow.getStartTime())).minusDays(1);
            Date date = localDateTime.toDate();
            executableFlow.setUpdateTime(date.getTime());
          }
        }
      } else if (executableFlow.getLastParameterTime() != -1) {
        executableFlow.setUpdateTime(
                new LocalDate(executableFlow.getLastParameterTime()).minusDays(1).toDate()
                        .getTime());
      } else {
        Long runDate = executableFlow.getSubmitTime();
        if (-1 != runDate) {
          LocalDateTime localDateTime = new LocalDateTime(new Date(runDate)).minusDays(1);
          Date date = localDateTime.toDate();
          executableFlow.setUpdateTime(date.getTime());
        }
      }
    }

    WebUtils webUtils = new WebUtils();
    executableFlow.setRunDate(
            executableFlow.getUpdateTime() == 0 ? executableFlow.getOtherOption().get("run_date")
                    .toString().replaceAll("[\"'./-]", "")
                    : webUtils.formatRunDate(executableFlow.getUpdateTime()));

  }

  private void cleanOldExecutionLogs(final long millis) {
    final long beforeDeleteLogsTimestamp = System.currentTimeMillis();
    try {
      final int count = this.executorLoader.removeExecutionLogsByTime(millis);
      logger.info("Cleaned up " + count + " log entries.");
    } catch (final ExecutorManagerException e) {
      logger.error("log clean up failed. ", e);
    }
    logger.info(
            "log clean up time: " + (System.currentTimeMillis() - beforeDeleteLogsTimestamp) / 1000
                    + " seconds.");
  }

  /**
   * Manage servlet call for stats servlet in Azkaban execution server {@inheritDoc}
   *
   * @see azkaban.executor.ExecutorManagerAdapter#callExecutorStats(int, java.lang.String,
   * azkaban.utils.Pair[])
   */
  @Override
  public Map<String, Object> callExecutorStats(final int executorId, final String action,
                                               final Pair<String, String>... params) throws IOException, ExecutorManagerException {
    final Executor executor = fetchExecutor(executorId);

    final List<Pair<String, String>> paramList =
            new ArrayList<>();

    // if params = null
    if (params != null) {
      paramList.addAll(Arrays.asList(params));
    }

    paramList
            .add(new Pair<>(ConnectorParams.ACTION_PARAM, action));

    return this.apiGateway.callForJsonObjectMap(executor.getHost(), executor.getPort(),
            "/stats", paramList);
  }


  @Override
  public Map<String, Object> callExecutorJMX(final String hostPort, final String action,
                                             final String mBean) throws IOException {
    final List<Pair<String, String>> paramList =
            new ArrayList<>();

    paramList.add(new Pair<>(action, ""));
    if (mBean != null) {
      paramList.add(new Pair<>(ConnectorParams.JMX_MBEAN, mBean));
    }

    final String[] hostPortSplit = hostPort.split(":");
    return this.apiGateway.callForJsonObjectMap(hostPortSplit[0],
            Integer.valueOf(hostPortSplit[1]), "/jmx", paramList);
  }

  @Override
  public void shutdown() {
    this.queueProcessor.shutdown();
    this.cleanerThread.shutdown();
    this.updaterThread.shutdown();
    this.recoverThread.shutdown();
    this.projectDeleteTrigger.shutdown();
    if (this.holdBatchSwitch) {
      this.holdBatchThread.shutdown();
    }
  }


  private void failEverything(final ExecutableFlow exFlow) {
    final long time = System.currentTimeMillis();
    for (final ExecutableNode node : exFlow.getExecutableNodes()) {
      switch (node.getStatus()) {
        case SUCCEEDED:
        case FAILED:
        case KILLED:
        case SKIPPED:
        case DISABLED:
        case FAILED_SKIPPED:
          continue;
          // case UNKNOWN:
        case READY:
          node.setStatus(Status.KILLING);
          break;
        default:
          node.setStatus(Status.FAILED);
          break;
      }

      if (node.getStartTime() == -1) {
        node.setStartTime(time);
      }
      if (node.getEndTime() == -1) {
        node.setEndTime(time);
      }
    }

    if (exFlow.getEndTime() == -1) {
      exFlow.setEndTime(time);
    }

    exFlow.setStatus(Status.FAILED);
  }

  public boolean isFinished(final ExecutableFlow flow) {
    switch (flow.getStatus()) {
      case SUCCEEDED:
      case FAILED:
      case KILLED:
        return true;
      default:
        return false;
    }
  }

  public boolean isFailedFinishing(final ExecutableFlow flow) {
    switch (flow.getStatus()) {
      case FAILED_FINISHING:
        return true;
      default:
        return false;
    }
  }

  private void fillUpdateTimeAndExecId(final List<ExecutableFlow> flows,
                                       final List<Integer> executionIds, final List<Long> updateTimes) {
    for (final ExecutableFlow flow : flows) {
      executionIds.add(flow.getExecutionId());
      updateTimes.add(flow.getUpdateTime());
    }
  }


  @Override
  public int getExecutableFlows(final int projectId, final String flowId, final int from,
                                final int length, final List<ExecutableFlow> outputList)
          throws ExecutorManagerException {
    final List<ExecutableFlow> flows =
            this.executorLoader.fetchFlowHistory(projectId, flowId, from, length);
    outputList.addAll(flows);
    return this.executorLoader.fetchNumExecutableFlows(projectId, flowId);
  }

  @Override
  public int quickSearchFlowExecutions(final int projectId, final String flowId, final int from,
                                       final int length, final String searchTerm, final List<ExecutableFlow> outputList)
          throws ExecutorManagerException {
    final List<ExecutableFlow> flows =
            this.executorLoader.quickSearchFlowExecutions(projectId, flowId, from, length, searchTerm);
    outputList.addAll(flows);
    return this.executorLoader.fetchQuickSearchNumExecutableFlows(projectId, flowId, searchTerm);
  }

  @Override
  public int userQuickSearchFlowExecutions(final int projectId, final String flowId, final int from,
                                           final int length, final String searchTerm, final List<ExecutableFlow> outputList, final String userId)
          throws ExecutorManagerException {
    final List<ExecutableFlow> flows =
            this.executorLoader.userQuickSearchFlowExecutions(projectId, flowId, from, length, searchTerm, userId);
    outputList.addAll(flows);
    return this.executorLoader.fetchUserQuickSearchNumExecutableFlows(projectId, flowId, searchTerm, userId);
  }

  @Override
  public List<ExecutableFlow> getExecutableFlows(final int projectId, final String flowId)
          throws ExecutorManagerException {
    return this.executorLoader.fetchFlowHistory(projectId, flowId);
  }

  @Override
  public List<ExecutableFlow> getExecutableFlows(final int projectId, final String flowId,
                                                 final int from, final int length, final Status status) throws ExecutorManagerException {
    return this.executorLoader.fetchFlowHistory(projectId, flowId, from, length,
            status);
  }

  /**
   * Calls executor to dispatch the flow, update db to assign the executor and in-memory state of
   * executableFlow
   */
  private void dispatch(final ExecutionReference reference, final ExecutableFlow exflow,
                        final Executor choosenExecutor) throws ExecutorManagerException {
    exflow.setUpdateTime(System.currentTimeMillis());

    this.executorLoader.assignExecutor(choosenExecutor.getId(),
            exflow.getExecutionId());
    try {
      long currentTime = System.currentTimeMillis();
      this.apiGateway.callWithExecutable(exflow, choosenExecutor,
              ConnectorParams.EXECUTE_ACTION);
      logger.info("exec id:{} call callWithExecutable() takes {} ms", exflow.getExecutionId(),
              System.currentTimeMillis() - currentTime);
    } catch (final ExecutorManagerException ex) {
      logger.error("Rolling back executor assignment for execution id:"
              + exflow.getExecutionId(), ex);
      this.executorLoader.unassignExecutor(exflow.getExecutionId());
      throw new ExecutorManagerException(ex);
    }
    reference.setExecutor(choosenExecutor);

    // move from flow to running flows
    this.runningExecutions.get().put(exflow.getExecutionId(), new Pair<>(reference, exflow));
    synchronized (this.runningExecutions.get()) {
      // Wake up RunningExecutionsUpdaterThread from wait() so that it will immediately check status
      // from executor(s). Normally flows will run at least some time and can't be cleaned up
      // immediately, so there will be another wait round (or many, actually), but for unit tests
      // this is significant to let them run quickly.
      this.runningExecutions.get().notifyAll();
    }
    synchronized (this) {
      // wake up all internal waiting threads, too
      this.notifyAll();
    }

    logger.info(String.format(
            "Successfully dispatched exec %d with error count %d",
            exflow.getExecutionId(), reference.getNumErrors()));
  }

  @VisibleForTesting
  void setSleepAfterDispatchFailure(final Duration sleepAfterDispatchFailure) {
    this.sleepAfterDispatchFailure = sleepAfterDispatchFailure;
  }

  // FIXME RecoverThread is a historical rerun thread and is responsible for regularly submitting historical rerun tasks.
  private class RecoverThread extends Thread {

    private boolean shutdown = false;
    private long waitTime;

    public RecoverThread(long waitTime) {
      this.waitTime = waitTime;
      this.setName("AzkabanWebServer-Recover-Thread");
    }

    @SuppressWarnings("unused")
    public void shutdown() {
      this.shutdown = true;
      this.interrupt();
    }

    @Override
    public void run() {
      while (!this.shutdown) {
        synchronized (Constants.HISTORY_RERUN_LOCK) {
          try {
            historyRecoverHandle();
            Constants.HISTORY_RERUN_LOCK.wait(this.waitTime);
          } catch (final Exception e) {
            ExecutorManager.logger.info("Recover-Thread interrupted. Probably to shut down.", e);
            if (!this.shutdown) {
              try {
                Constants.HISTORY_RERUN_LOCK.wait(this.waitTime);
              } catch (InterruptedException ex) {
                ExecutorManager.logger.info("Recover-Thread can not wait. ", e);
              }
            }
          }
        }
      }
    }

    private void historyRecoverHandle() {

      for (RecoverTrigger trigger : executorLoader.fetchHistoryRecoverTriggers()) {

        try {
          LocalDateTime now = new LocalDateTime();
          LocalDateTime startTime;
          LocalDateTime endTime;
          try {
            String[] executeTimeBegin =
                    trigger.getExecutionRecover().getOtherOption().get("repeatExecuteTimeBegin")
                            .toString().split(":");
            String[] executeTimeEnd =
                    trigger.getExecutionRecover().getOtherOption().get("repeatExecuteTimeEnd")
                            .toString()
                            .split(":");

            startTime = now.withHourOfDay(Integer.parseInt(executeTimeBegin[0]))
                    .withMinuteOfHour(Integer.parseInt(executeTimeBegin[1])).withSecondOfMinute(0);
            endTime = now.withHourOfDay(Integer.parseInt(executeTimeEnd[0]))
                    .withMinuteOfHour(Integer.parseInt(executeTimeEnd[1])).withSecondOfMinute(0);

          } catch (Exception e) {
            logger.error("check execute time error", e);
            startTime = now.withHourOfDay(0).withMinuteOfHour(0).withSecondOfMinute(0);
            endTime = now.withHourOfDay(0).withMinuteOfHour(0).withSecondOfMinute(0);
          }

          if ((startTime.isBefore(endTime) && (now.isBefore(startTime) || now.isAfter(endTime)))
                  || (startTime.isAfter(endTime) && now.isAfter(endTime) && now.isBefore(
                  startTime))) {
            if (Status.RUNNING.equals(trigger.getExecutionRecover().getRecoverStatus())) {
              trigger.getExecutionRecover().setRecoverStatus(Status.PAUSED);
              updateHistoryRecover(trigger.getExecutionRecover());
            }
            continue;
          }

          ExecutorManager.logger.info("trigger info : " + trigger.toString());
          trigger.setExecutionRecoverStartTime();
          trigger.updateTaskStatus();
          Project project = ExecutorManager.this.projectLoader.fetchProjectById(
                  trigger.getProjectId());
          if (trigger.getExecutionRecover().getProjectVersion() > 0) {
            project.setVersion(trigger.getExecutionRecover().getProjectVersion());
          }
          trigger.setProject(project);
          if (!trigger.expireConditionMet()) {
            loadAllProjectFlows(project);
            Flow flow = project.getFlow(trigger.getFlowId());
            if (flow == null) {
              logger.error("flow " + trigger.getFlowId() + " in project " + project.getName()
                      + " not found,skip recover");
              continue;
            }
            String batchId = ExecutorManager.this.holdBatchContext
                    .isInBatch(project.getName(), flow.getId(),
                            trigger.getExecutionRecover().getSubmitUser());
            if (ExecutorManager.this.holdBatchSwitch && StringUtils.isNotEmpty(batchId)) {
              continue;
            }

            int taskIndex = trigger.getExecutionRecover().getTaskIndex();
            List<GroupTask> groupTasks = trigger.getGroup();
            //任务提交不支持并发
            for (int i = 0; i < groupTasks.size(); i++) {
              if (trigger.getExecutionRecover().getLastSubmitTime() + trigger.getExecutionRecover().getReRunTimeInterval() * 60 * 1000 > System.currentTimeMillis()) {
                logger.info("trigger skipped : {}", trigger.getTriggerId());
                continue;
              }
              if (taskIndex == trigger.getRepeatList().size()) {
                break;
              }
              GroupTask groupTask = groupTasks.get(i);
              if (groupTask.checkIsRunning()) {
                logger.info("group {} is running ,skipped", i);
                continue;
              }
              trigger.updateGroupTask(taskIndex % trigger.getTaskSize(), i);
              Map<String, String> task = groupTask.nextTask();
              if (null != task) {
                trigger.getExecutionRecover().setTaskIndex(++taskIndex);
                trigger.getExecutionRecover().setGroup(groupTasks);
                logger.info("submit task : {} ", task);
                ExecutableFlow exflow = new ExecutableFlow(project, flow);
                if (trigger.getExecutionRecover().getLastExecId() != -1) {
                  try {
                    logger.info("get last executable flow, execId: {}",
                            trigger.getExecutionRecover().getLastExecId());
                    ExecutableFlow lastFlow = ExecutorManager.this.getExecutableFlow(
                            trigger.getExecutionRecover().getLastExecId());
                    FlowUtils.compareAndCopyFlow(exflow, lastFlow);
                  } catch (Exception e) {
                    logger.error("get executable flow failed", e);
                  }
                }
                submitRecoverFlow(exflow, project, trigger.getExecutionRecover(), task);
                trigger.getExecutionRecover().setLastSubmitTime(System.currentTimeMillis());
                updateRecoverFlow(exflow, trigger.getExecutionRecover(), task);
              }
            }
          }
          updateHistoryRecover(trigger.getExecutionRecover());
        } catch (Exception e) {
          logger.error("Errors occurred when handling history recover task, trigger info: "
                  + trigger.toString()
                  + ", skip this task.", e);
          continue;
        }
      }
    }


    private String submitRecoverFlow(ExecutableFlow exflow, Project project, ExecutionRecover recover, Map<String, String> item) {
      exflow.setSubmitUser(recover.getSubmitUser());
      //获取项目默认代理用户
      Set<String> proxyUserSet = project.getProxyUsers();
      //设置用户代理用户
      proxyUserSet.add(recover.getSubmitUser());
      if (recover.getProxyUsers() != null && !"[]".equals(recover.getProxyUsers())) {
        List<String> proxyUsers = Arrays.asList(recover.getProxyUsers().replaceAll("\\s*", "").replace("[", "").replace("]", "").split(","));
        proxyUserSet.addAll(proxyUsers);
      } else {
        ExecutorManager.logger.error("recover proxyUsers is null");
      }
      //设置当前登录的用户的代理用户
      exflow.addAllProxyUsers(proxyUserSet);
      exflow.setExecutionOptions(recover.getExecutionOptions());
      exflow.setOtherOption(recover.getOtherOption());
      if (recover.getOtherOption().get("flowFailedRetryOption") != null) {
        exflow.setFlowFailedRetry((Map<String, String>) recover.getOtherOption().get("flowFailedRetryOption"));
      }
      // 设置失败跳过所有job
      exflow.setFailedSkipedAllJobs((Boolean) recover.getOtherOption().getOrDefault("flowFailedSkiped", false));
      //超时告警设置
      if (recover.getSlaOptions() != null) {
        exflow.setSlaOptions(recover.getSlaOptions());
      }

      //设置数据补采参数
      exflow.setRepeatOption(item);
      //设置Flow类型为数据补采
      exflow.setFlowType(2);
      String message = "";
      exflow.setRepeatId(recover.getRecoverId());
      try {
        message = ExecutorManager.this.submitExecutableFlow(exflow, recover.getSubmitUser());
      } catch (Exception ex) {
        ExecutorManager.logger.error("submit recover flow failed. ", ex);
      }
      return message;
    }

    private void updateRecoverFlow(ExecutableFlow exflow, ExecutionRecover recover, Map<String, String> item) {
      //提交成功
      if (exflow.getExecutionId() != -1) {
        recover.setNowExecutionId(exflow.getExecutionId());
        item.put("isSubmit", "true");
        item.put("exeId", String.valueOf(exflow.getExecutionId()));
      } else {
        ExecutorManager.logger.error("submit recover flow failed. ");
        item.put("exeId", "-1");
      }
    }

    private void updateHistoryRecover(ExecutionRecover recover) {
      try {
        ExecutorManager.this.updateHistoryRecover(recover);
      } catch (ExecutorManagerException executorManager) {
        ExecutorManager.logger.error("更新历史重跑任务信息失败, " + executorManager);
      }
    }


    private void loadAllProjectFlows(final Project project) {
      try {
        final List<Flow> flows = ExecutorManager.this.projectLoader.fetchAllProjectFlows(project);
        final Map<String, Flow> flowMap = new HashMap<>();
        for (final Flow flow : flows) {
          flowMap.put(flow.getId(), flow);
        }

        project.setFlows(flowMap);
      } catch (final ProjectManagerException e) {
        throw new RuntimeException("Could not load projects flows from store.", e);
      }
    }
  }

  /*
   * cleaner thread to clean up execution_logs, etc in DB. Runs every hour.
   */
  private class CleanerThread extends Thread {
    // log file retention is 1 month.

    // check every hour
    private static final long CLEANER_THREAD_WAIT_INTERVAL_MS = 60 * 60 * 1000L;

    private final long executionLogsRetentionMs;

    private boolean shutdown = false;
    private long lastLogCleanTime = -1;

    public CleanerThread(final long executionLogsRetentionMs) {
      this.executionLogsRetentionMs = executionLogsRetentionMs;
      this.setName("AzkabanWebServer-Cleaner-Thread");
    }

    @SuppressWarnings("unused")
    public void shutdown() {
      this.shutdown = true;
      this.interrupt();
    }

    @Override
    public void run() {
      while (!this.shutdown) {
        synchronized (this) {
          try {
            // Cleanup old stuff.
            final long currentTime = System.currentTimeMillis();
            if (currentTime - CLEANER_THREAD_WAIT_INTERVAL_MS > this.lastLogCleanTime) {
              cleanExecutionLogs();
              this.lastLogCleanTime = currentTime;
            }

            wait(CLEANER_THREAD_WAIT_INTERVAL_MS);
          } catch (final InterruptedException e) {
            ExecutorManager.logger.info("Interrupted. Probably to shut down.");
          }
        }
      }
    }

    private void cleanExecutionLogs() {
      ExecutorManager.logger.info("Cleaning old logs from execution_logs");
      final long cutoff = System.currentTimeMillis() - this.executionLogsRetentionMs;
      ExecutorManager.logger.info("Cleaning old log files before "
              + new DateTime(cutoff).toString());
      cleanOldExecutionLogs(System.currentTimeMillis()
              - this.executionLogsRetentionMs);
    }
  }

  /*
   * read hold batch context
   */
  private class HoldBatchThread extends Thread {

    private final long holdBatchThreadMs;

    private boolean shutdown = false;

    public HoldBatchThread(final long holdBatchThreadMs) {
      this.holdBatchThreadMs = holdBatchThreadMs;
      this.setName("AzkabanWebServer-HoldBatch-Thread");
    }

    public void shutdown() {
      this.shutdown = true;
      this.interrupt();
    }

    @Override
    public void run() {
      while (!this.shutdown) {
        try {
          long begin = System.currentTimeMillis();

          loadHoldingBatch();

          for (HoldBatchOperate operate : ExecutorManager.this.holdBatchContext.getBatchMap()
                  .values()) {
            String resumeJson = ExecutorManager.this.executorLoader
                    .getLocalHoldBatchResume(operate.getBatchId());
            if (StringUtils.isNotEmpty(resumeJson)) {
              ExecutorManager.this.holdBatchContext.getBatchMap().remove(operate.getBatchId());
              ExecutorManager.this.executorLoader.updateHoldBatchStatus(operate.getBatchId(), 1);
              operate.setStrategy(resumeJson);
              this.resume(operate);
            }
          }

          ExecutorManager.this.executorLoader.getMissResumeBatch().stream()
                  .forEach(operate -> this.resume(operate));

          this.alertSkipFlow();

          long duration = System.currentTimeMillis() - begin;
          if (this.holdBatchThreadMs > duration) {
            Thread.sleep(this.holdBatchThreadMs - duration);
          }

        } catch (final InterruptedException e) {
          ExecutorManager.logger.info("Interrupted. Probably to shut down.");
        } catch (ExecutorManagerException e) {
          ExecutorManager.logger.error("check hold batch context error");
        }
      }
    }

    private void resume(HoldBatchOperate holdBatchOperate) {
      new Thread(() -> {
        String key = Constants.HOLD_BATCH_RESUME_LOCK_KEY + holdBatchOperate.getBatchId();
        synchronized (key.intern()) {
          try {
            Alerter mailAlerter = ServiceProvider.SERVICE_PROVIDER.getInstance(AlerterHolder.class)
                    .get("email");
            List<HoldBatchAlert> execList = ExecutorManager.this.executorLoader
                    .queryExecByBatch(holdBatchOperate.getBatchId());
            for (HoldBatchAlert exec : execList) {
              boolean alertFlag = true;
              if (holdBatchOperate.isBlack(exec)) {
                exec.setBlack(1);
                exec.setResume(2);
              } else if (holdBatchOperate.isNotResume(exec) || Status
                      .isStatusFinished(exec.getExecutableFlow().getStatus())) {
                exec.setBlack(0);
                exec.setResume(2);
              } else {
                exec.getExecutableFlow().setStatus(Status.PREPARING);
                exec.getExecutableFlow().getOtherOption().put("isHoldingSubmit", true);
                ExecutorManager.this.executorLoader.updateExecutableFlow(exec.getExecutableFlow());
                ExecutorManager.this.queuedFlows
                        .enqueue(exec.getExecutableFlow(),
                                new ExecutionReference(exec.getExecutableFlow().getExecutionId()));
                alertFlag = false;
                exec.setBlack(0);
                exec.setResume(1);
                exec.setResumeTime(System.currentTimeMillis());
              }
              ExecutorManager.this.executorLoader.updateHoldBatchResumeStatus(exec);
              if (alertFlag) {
                exec.getExecutableFlow().setStatus(Status.FAILED);
                ExecutorManager.this.executorLoader.updateExecutableFlow(exec.getExecutableFlow());
                if (mailAlerter != null) {
                  mailAlerter.alertOnHoldBatch(exec, ExecutorManager.this.executorLoader, false);
                }
                ExecutorManager.this.updateScheduleMissedTime(exec);
              }
            }

            List<HoldBatchAlert> alertList = ExecutorManager.this.executorLoader
                    .queryAlertByBatch(holdBatchOperate.getBatchId());
            for (HoldBatchAlert holdBatchAlert : alertList) {
              boolean alertFlag = true;
              if (holdBatchOperate.isBlack(holdBatchAlert)) {
                holdBatchAlert.setBlack(1);
                holdBatchAlert.setResume(2);
              } else if (holdBatchOperate.isNotResume(holdBatchAlert)) {
                holdBatchAlert.setBlack(0);
                holdBatchAlert.setResume(2);
              } else {
                holdBatchAlert.getExecutableFlow().getOtherOption().put("isHoldingSubmit", true);
                ExecutorManager.this.executorLoader
                        .uploadExecutableFlow(holdBatchAlert.getExecutableFlow());
                holdBatchAlert.setExecId(holdBatchAlert.getExecutableFlow().getExecutionId());
                ExecutorManager.this.queuedFlows
                        .enqueue(holdBatchAlert.getExecutableFlow(),
                                new ExecutionReference(
                                        holdBatchAlert.getExecutableFlow().getExecutionId()));
                alertFlag = false;
                holdBatchAlert.setBlack(0);
                holdBatchAlert.setResume(1);
                holdBatchAlert.setResumeTime(System.currentTimeMillis());
              }
              ExecutorManager.this.executorLoader.updateHoldBatchResumeStatus(holdBatchAlert);
              if (alertFlag) {
                ExecutorManager.this.updateScheduleMissedTime(holdBatchAlert);
                if (mailAlerter != null) {
                  mailAlerter.alertOnHoldBatch(holdBatchAlert, ExecutorManager.this.executorLoader, false);
                }
              }
            }

            List<HoldBatchAlert> frequentListList = ExecutorManager.this.executorLoader
                    .queryFrequentByBatch(holdBatchOperate.getBatchId());
            for (HoldBatchAlert frequent : frequentListList) {
              if (mailAlerter != null) {
                mailAlerter.alertOnHoldBatch(frequent, ExecutorManager.this.executorLoader, true);
              }
            }

          } catch (Exception e) {
            logger.error("batch resume error: " + holdBatchOperate.getBatchId(), e);
          }
        }
      }).start();
    }

    private void alertSkipFlow() {
      new Thread(() -> {
        List<HoldBatchAlert> list = ExecutorManager.this.executorLoader.queryWaitingAlert();
        Alerter mailAlerter = ServiceProvider.SERVICE_PROVIDER.getInstance(AlerterHolder.class)
                .get("email");
        if (mailAlerter != null) {
          list.stream().forEach(alert -> mailAlerter
                  .alertOnHoldBatch(alert, ExecutorManager.this.executorLoader, false));
        }
      }).start();
    }

  }

  private void loadHoldingBatch() throws ExecutorManagerException {
    ExecutorManager.this.executorLoader.getLocalHoldBatchOpr().stream()
            .forEach(operate -> ExecutorManager.this.holdBatchContext.getBatchMap()
                    .putIfAbsent(operate.getBatchId(), operate));

    List<HoldBatchOperate> operateList = ExecutorManager.this.holdBatchContext.getBatchMap()
            .values().stream().filter(operate -> operate.getOperateLevel() == 0)
            .collect(Collectors.toList());
    if (CollectionUtils.isNotEmpty(operateList)) {
      ExecutorManager.this.holdBatchContext.getBatchMap().clear();
      ExecutorManager.this.holdBatchContext.getBatchMap()
              .put(operateList.get(0).getBatchId(), operateList.get(0));
      ExecutorManager.this.executorLoader
              .updateHoldBatchExpired(operateList.get(0).getBatchId());
      ExecutorManager.this.executorLoader.updateHoldBatchId(operateList.get(0).getBatchId());
    }
  }

  private void updateScheduleMissedTime(HoldBatchAlert exec) throws ScheduleManagerException {
    int scheduleId = (int) exec
            .getExecutableFlow().getOtherOption().getOrDefault(Constants.EXECUTE_FLOW_TRIGGER_ID, -1);
    Schedule schedule = ExecutorManager.this.scheduleManager.getSchedule(scheduleId);
    schedule.getOtherOption().put(Constants.SCHEDULE_MISSED_TIME, exec.getExecutableFlow().getSubmitTime());
    if (null != schedule.getExecutionOptions()) {
      logger.info("update schedule successEmails : {} ", schedule.getExecutionOptions().getSuccessEmails());
      logger.info("update schedule failureEmails : {} ", schedule.getExecutionOptions().getFailureEmails());
    }
    ExecutorManager.this.scheduleManager.insertSchedule(schedule);
  }

  /*
   * This thread is responsible for processing queued flows using dispatcher and
   * making rest api calls to executor server
   */
  private class QueueProcessorThread extends Thread {

    private static final long QUEUE_PROCESSOR_WAIT_IN_MS = 1000;
    private final int maxDispatchingErrors;
    private final long activeExecutorRefreshWindowInMillisec;
    private final int activeExecutorRefreshWindowInFlows;
    private final Duration sleepAfterDispatchFailure;

    private volatile boolean shutdown = false;
    private volatile boolean isActive = true;


    private final boolean processorAsyncEnabled;

    private ThreadPoolExecutor processorAsyncPool;


    public QueueProcessorThread(final boolean isActive,
                                final long activeExecutorRefreshWindowInTime,
                                final int activeExecutorRefreshWindowInFlows,
                                final int maxDispatchingErrors,
                                final Duration sleepAfterDispatchFailure,
                                final Props azkProps) {
      setActive(isActive);
      this.maxDispatchingErrors = maxDispatchingErrors;
      this.activeExecutorRefreshWindowInFlows =
              activeExecutorRefreshWindowInFlows;
      this.activeExecutorRefreshWindowInMillisec =
              activeExecutorRefreshWindowInTime;
      this.sleepAfterDispatchFailure = sleepAfterDispatchFailure;
      this.setName("AzkabanWebServer-QueueProcessor-Thread");
      this.processorAsyncEnabled = azkProps.getBoolean(ConfigurationKeys.QUEUEPROCESSING_ASYNC_ENABLED, false);
      int processorAsyncSize = azkProps.getInt(ConfigurationKeys.QUEUEPROCESSING_ASYNC_POOL_SIZE, 5);
      if (this.processorAsyncEnabled) {
        logger.info("Async task scheduling to Executor has been started. processorAsyncSize {}", processorAsyncSize);
        ThreadFactory threadFactory = new ThreadFactoryBuilder().setDaemon(true).build();
        this.processorAsyncPool = new ThreadPoolExecutor(processorAsyncSize, processorAsyncSize,
                0L, TimeUnit.MILLISECONDS,
                new LinkedBlockingQueue<Runnable>(1000), threadFactory);
      }
    }

    public boolean isActive() {
      return this.isActive;
    }

    public void setActive(final boolean isActive) {
      this.isActive = isActive;
      ExecutorManager.logger.info("QueueProcessorThread active turned " + this.isActive);
    }

    public void shutdown() {
      this.shutdown = true;
      this.interrupt();
    }

    @Override
    public void run() {
      // Loops till QueueProcessorThread is shutdown
      while (!this.shutdown) {
        synchronized (this) {
          try {
            // start processing queue if active, other wait for sometime
            if (this.isActive) {
              processQueuedFlows(this.activeExecutorRefreshWindowInMillisec,
                      this.activeExecutorRefreshWindowInFlows);
            }
            wait(QUEUE_PROCESSOR_WAIT_IN_MS);
          } catch (final Exception e) {
            ExecutorManager.logger.error(
                    "QueueProcessorThread Interrupted. Probably to shut down.", e);
          }
        }
      }
    }

    /**
     * @param activeExecutorsRefreshWindow
     * @param maxContinuousFlowProcessed
     * @throws InterruptedException
     * @throws ExecutorManagerException
     */
    /* Method responsible for processing the non-dispatched flows */
    private void processQueuedFlows(final long activeExecutorsRefreshWindow,
                                    final int maxContinuousFlowProcessed) throws InterruptedException,
            ExecutorManagerException {
      long lastExecutorRefreshTime = 0;
      int currentContinuousFlowProcessed = 0;
      Pair<ExecutionReference, ExecutableFlow> runningCandidate = null;
      while (isActive() && (runningCandidate = ExecutorManager.this.queuedFlows
              .fetchHead()) != null) {

        final ExecutableFlow exflow = runningCandidate.getSecond();
        String batchId = ExecutorManager.this.holdBatchContext
                .isInBatch(exflow.getProjectName(), exflow.getId(), exflow.getSubmitUser());
        String lastBatchId = (String) exflow.getOtherOption().get("lastBatchId");
        if (ExecutorManager.this.holdBatchSwitch && StringUtils.isNotEmpty(batchId) && !batchId
                .equals(lastBatchId)) {
          exflow.setStatus(Status.SYSTEM_PAUSED);
          ExecutorManager.this.executorLoader.updateExecutableFlow(exflow);
          ExecutorManager.this.executorLoader.addHoldBatchAlert(batchId, exflow, 0);
          continue;
        }

        final ExecutionReference reference = runningCandidate.getFirst();

        final long currentTime = System.currentTimeMillis();

        // if we have dispatched more than maxContinuousFlowProcessed or
        // It has been more then activeExecutorsRefreshWindow millisec since we
        // refreshed

        if (currentTime - lastExecutorRefreshTime > activeExecutorsRefreshWindow
                || currentContinuousFlowProcessed >= maxContinuousFlowProcessed) {
          // Refresh executorInfo for all activeExecutors
          refreshExecutors();
          lastExecutorRefreshTime = currentTime;
          currentContinuousFlowProcessed = 0;
        }

        /**
         * <pre>
         *  TODO: Work around till we improve Filters to have a notion of GlobalSystemState.
         *        Currently we try each queued flow once to infer a global busy state
         * Possible improvements:-
         *   1. Move system level filters in refreshExecutors and sleep if we have all executors busy after refresh
         *   2. Implement GlobalSystemState in selector or in a third place to manage system filters. Basically
         *      taking out all the filters which do not depend on the flow but are still being part of Selector.
         * Assumptions:-
         *   1. no one else except QueueProcessor is updating ExecutableFlow update time
         *   2. re-attempting a flow (which has been tried before) is considered as all executors are busy
         * </pre>
         */
        if (exflow.getUpdateTime() > lastExecutorRefreshTime) {
          // put back in the queue
          logger.info("exec id: {} update time{} > lastExecutorRefreshTime{}",
                  exflow.getExecutionId(),
                  exflow.getUpdateTime(), lastExecutorRefreshTime);
          ExecutorManager.this.queuedFlows.enqueue(exflow, reference);
          final long sleepInterval =
                  activeExecutorsRefreshWindow
                          - (currentTime - lastExecutorRefreshTime);
          // wait till next executor refresh
          Thread.sleep(sleepInterval);
        } else {
          exflow.setUpdateTime(currentTime);
          if (this.processorAsyncEnabled) {
            this.processorAsyncPool.submit(() -> {
              try {
                Thread.currentThread().setName("Processor-async-pool" + exflow.getFlowId());
                selectExecutorAndDispatchFlow(reference, exflow);
              } catch (Exception e) {
                logger.error(
                        "Flow {} Failed to select executor dispatch.", exflow.getFlowId(), e);
              }
            });
          } else {
            // process flow with current snapshot of activeExecutors
            selectExecutorAndDispatchFlow(reference, exflow);
          }
        }

        // do not count failed flow processsing (flows still in queue)
        if (ExecutorManager.this.queuedFlows.getFlow(exflow.getExecutionId()) == null) {
          currentContinuousFlowProcessed++;
        }
      }
    }

    /* process flow with a snapshot of available Executors */
    private void selectExecutorAndDispatchFlow(final ExecutionReference reference,
                                               final ExecutableFlow exflow)
            throws ExecutorManagerException {
      synchronized ((exflow.getExecutionId() + "").intern()) {
        final Set<Executor> flowExecutors = ExecutorManager.this.activeExecutors.getAll().stream()
                .filter(executor -> exflow.getExecutorIds().contains(executor.getId())).collect(Collectors.toSet());
        final Set<Executor> remainingExecutors = new HashSet<>(flowExecutors);
        ExecutorManager.logger.info("execId: {} executorIds is {} can select  executors {}", exflow.getExecutionId(), exflow.getExecutorIds(), remainingExecutors);
        Throwable lastError;
        do {
          final Executor selectedExecutor = selectExecutor(exflow, remainingExecutors);
          if (selectedExecutor == null) {
            ExecutorManager.this.commonMetrics.markDispatchFail();
            handleNoExecutorSelectedCase(reference, exflow);
            // RE-QUEUED - exit
            return;
          } else {
            try {
              dispatch(reference, exflow, selectedExecutor);
              // set select executor id
              List<Integer> dispatchedExecutorList = new ArrayList<>();
              dispatchedExecutorList.add(selectedExecutor.getId());
              exflow.setExecutorIds(dispatchedExecutorList);
              ExecutorManager.this.commonMetrics.markDispatchSuccess();
              // SUCCESS - exit
              return;
            } catch (final ExecutorManagerException e) {
              lastError = e;
              logFailedDispatchAttempt(reference, exflow, selectedExecutor, e);
              ExecutorManager.this.commonMetrics.markDispatchFail();
              reference.setNumErrors(reference.getNumErrors() + 1);
              // FAILED ATTEMPT - try other executors except selectedExecutor
              updateRemainingExecutorsAndSleep(remainingExecutors, selectedExecutor, flowExecutors);
            }
          }
        } while (reference.getNumErrors() < this.maxDispatchingErrors);
        // GAVE UP DISPATCHING
        final String message = "Failed to dispatch queued execution " + exflow.getId() + " because "
                + "reached " + ConfigurationKeys.MAX_DISPATCHING_ERRORS_PERMITTED
                + " (tried " + reference.getNumErrors() + " executors)";
        ExecutorManager.logger.error(message);
        ExecutorManager.this.executionFinalizer.finalizeFlow(exflow, message, lastError);  // 通用告警
      }
    }

    private void updateRemainingExecutorsAndSleep(final Set<Executor> remainingExecutors,
                                                  final Executor selectedExecutor, final Set<Executor> flowExecutors) {
      remainingExecutors.remove(selectedExecutor);
      if (remainingExecutors.isEmpty()) {
        remainingExecutors.addAll(flowExecutors);
        sleepAfterDispatchFailure();
      }
    }

    private void sleepAfterDispatchFailure() {
      try {
        Thread.sleep(this.sleepAfterDispatchFailure.toMillis());
      } catch (final InterruptedException e1) {
        ExecutorManager.logger.warn("Sleep after dispatch failure was interrupted - ignoring");
      }
    }

    private void logFailedDispatchAttempt(final ExecutionReference reference,
                                          final ExecutableFlow exflow,
                                          final Executor selectedExecutor, final ExecutorManagerException e) {
      ExecutorManager.logger.warn(String.format(
              "Executor %s responded with exception for exec: %d",
              selectedExecutor, exflow.getExecutionId()), e);
      ExecutorManager.logger.info(String.format(
              "Failed dispatch attempt for exec %d with error count %d",
              exflow.getExecutionId(), reference.getNumErrors()));
    }

    /* Helper method to fetch  overriding Executor, if a valid user has specifed otherwise return null */
    private Executor getUserSpecifiedExecutor(final ExecutionOptions options,
                                              final int executionId) {
      Executor executor = null;
      if (options != null
              && options.getFlowParameters() != null
              && options.getFlowParameters().containsKey(ExecutionOptions.USE_EXECUTOR)
              && IS_NUMBER.matcher(options.getFlowParameters().get(ExecutionOptions.USE_EXECUTOR)).matches()) {
        try {
          final int executorId =
                  Integer.valueOf(options.getFlowParameters().get(
                          ExecutionOptions.USE_EXECUTOR));
          executor = fetchExecutor(executorId);

          if (executor == null) {
            ExecutorManager.logger
                    .warn(String
                            .format(
                                    "User specified executor id: %d for execution id: %d is not active, Looking up db.",
                                    executorId, executionId));
            executor = ExecutorManager.this.executorLoader.fetchExecutor(executorId);
            if (executor == null) {
              ExecutorManager.logger
                      .warn(String
                              .format(
                                      "User specified executor id: %d for execution id: %d is missing from db. Defaulting to availableExecutors",
                                      executorId, executionId));
            }
          }
        } catch (final ExecutorManagerException ex) {
          ExecutorManager.logger.error("Failed to fetch user specified executor for exec_id = "
                  + executionId, ex);
        }
      }
      return executor;
    }

    /* Choose Executor for exflow among the available executors */
    private Executor selectExecutor(final ExecutableFlow exflow,
                                    final Set<Executor> availableExecutors) {
      Executor choosenExecutor =
              getUserSpecifiedExecutor(exflow.getExecutionOptions(),
                      exflow.getExecutionId());

      // If no executor was specified by admin
      if (choosenExecutor == null) {
        final ExecutorSelector selector = new ExecutorSelector(ExecutorManager.this.filterList,
                ExecutorManager.this.comparatorWeightsMap);
        choosenExecutor = selector.getBest(availableExecutors, exflow);
        ExecutorManager.logger.info("Using dispatcher for execution id :{} use executor: {}", exflow.getExecutionId(), choosenExecutor);
      } else {
        ExecutorManager.logger.info("executionId {} submit to user choose executor {}", exflow.getExecutionId(), choosenExecutor);
      }
      return choosenExecutor;
    }


    private void handleNoExecutorSelectedCase(final ExecutionReference reference,
                                              final ExecutableFlow exflow) throws ExecutorManagerException {
      ExecutorManager.logger
              .info("Reached handleNoExecutorSelectedCase stage for exec {} with error count {}",
                      exflow.getExecutionId(), reference.getNumErrors());
      // TODO: handle scenario where a high priority flow failing to get
      // schedule can starve all others
      // refresh executor info
      ExecutorManager.this.refreshFlowExecutorIDs(exflow);
      ExecutorManager.this.queuedFlows.enqueue(exflow, reference);
    }
  }


  /**
   * @param skip
   * @param size
   * @param user
   * @return
   * @throws ExecutorManagerException
   */
  @Override
  public List<ExecutableFlow> getUserExecutableFlows(int skip, int size, String user)
          throws ExecutorManagerException {
    List<ExecutableFlow> flows = executorLoader.fetchUserFlowHistory(skip, size, user);
    return flows;
  }

  /**
   * 根据条件查找用户的 flow 历史信息
   *
   * @param projContain
   * @param flowContain
   * @param userContain
   * @param status
   * @param begin
   * @param end
   * @param skip
   * @param size
   * @return
   * @throws ExecutorManagerException
   */
  @Override
  public List<ExecutableFlow> getUserExecutableFlowsByAdvanceFilter(String projContain,
                                                                    String flowContain, String execIdContain, String userContain, String status, long begin,
                                                                    long end, String runDate,
                                                                    int skip, int size, int flowType) throws ExecutorManagerException {
    List<ExecutableFlow> flows =
            executorLoader.fetchUserFlowHistoryByAdvanceFilter(projContain, flowContain, execIdContain,
                    userContain, status, begin, end, runDate, skip, size, flowType);

    return flows;
  }

  @Override
  public List<ExecutableFlow> getUserExecutableFlowsByAdvanceFilter(String projContain, String flowContain, String execIdContain, String userContain,
                                                                    String status, long begin, long end, String subsystem, String busPath, String department,
                                                                    String runDate, int skip, int size, int flowType) throws ExecutorManagerException {
    List<ExecutableFlow> flows =
            executorLoader.fetchUserFlowHistoryByAdvanceFilter(projContain, flowContain, execIdContain,
                    userContain, status, begin, end, subsystem, busPath, department, runDate, skip, size, flowType);

    return flows;
  }

  /**
   * 根据 flow Id 查找用户的 flow 历史信息
   *
   * @param flowIdContains
   * @param user
   * @param skip
   * @param size
   * @return
   * @throws ExecutorManagerException
   */
  @Override
  public List<ExecutableFlow> getUserExecutableFlowsQuickSearch(String flowIdContains, String user,
                                                                int skip, int size) throws ExecutorManagerException {
    List<ExecutableFlow> flows =
            executorLoader.fetchFlowHistoryQuickSearch('%' + flowIdContains + '%', user, skip, size);

    return flows;
  }

  @Override
  public List<ExecutableFlow> getHistoryRecoverExecutableFlows(final String userNameContains)
          throws ExecutorManagerException {
    List<ExecutableFlow> flows =
            executorLoader.fetchHistoryRecoverFlows(userNameContains);
    return flows;
  }

  @Override
  public ExecutableFlow getHistoryRecoverExecutableFlowsByRepeatId(final String repeatId)
          throws ExecutorManagerException {
    ExecutableFlow ef = new ExecutableFlow();
    List<ExecutableFlow> flows =
            executorLoader.fetchHistoryRecoverFlowByRepeatId(repeatId);
    if (!flows.isEmpty()) {
      ef = flows.get(0);
    } else {
      throw new ExecutorManagerException("Failed to search current job flow by RepeatId[" + repeatId + "]");
    }
    return ef;
  }

  @Override
  public void stopHistoryRecoverExecutableFlowByRepeatId(final String repeatId)
          throws ExecutorManagerException {
    //如果数据不存在则查找当前最新的flow修改状态
    ExecutableFlow exFlow = null;
    try {
      exFlow = this.getHistoryRecoverExecutableFlowsByRepeatId(repeatId);
      if (2 == exFlow.getFlowType() && !Status.FAILED.equals(exFlow.getStatus())) {
        exFlow.setFlowType(3);//数据补采状态设置为这终止
      } else if (2 == exFlow.getFlowType() && Status.FAILED.equals(exFlow.getStatus())) {
        exFlow.setFlowType(5);//数据补采状态设置为这失败终止
      }
      this.executorLoader.updateExecutableFlow(exFlow);
    } catch (ExecutorManagerException e) {
      logger.warn("Failed to stopHistory repeatId {}.", repeatId, e);
    }
  }

  @Override
  public ExecutableFlow getHistoryRecoverExecutableFlowsByFlowId(final String flowId, final String projectId)
          throws ExecutorManagerException {
    List<ExecutableFlow> flows = executorLoader.fetchHistoryRecoverFlowByFlowId(flowId, projectId);
    if (flows.isEmpty()) {
      return null;
    } else {
      return flows.get(0);
    }
  }

  @Override
  public List<ExecutionRecover> listHistoryRecoverFlows(final Map paramMap,
                                                        int skip, int size) throws ExecutorManagerException {
    List<ExecutionRecover> flows =
            executorLoader.listHistoryRecoverFlows(paramMap, skip, size);
    return flows;
  }

  @Override
  public List<ExecutionRecover> listMaintainedHistoryRecoverFlows(String username, List<Integer> projectIds, int skip, int size)
          throws ExecutorManagerException {
    return executorLoader.listMaintainedHistoryRecoverFlows(username, projectIds, skip, size);
  }

  @Override
  public Integer saveHistoryRecoverFlow(final ExecutionRecover executionRecover)
          throws ExecutorManagerException {
    return executorLoader.saveHistoryRecoverFlow(executionRecover);
  }

  @Override
  public void updateHistoryRecover(final ExecutionRecover executionRecover)
          throws ExecutorManagerException {

    try {
      executionRecover.setUpdateTime(System.currentTimeMillis());
      executorLoader.updateHistoryRecover(executionRecover);
    } catch (ExecutorManagerException e) {
      logger.warn("Failed to updateHistoryRecover {}.", executionRecover, e);
    }
  }

  @Override
  public ExecutionRecover getHistoryRecoverFlow(final Integer recoverId)
          throws ExecutorManagerException {

    ExecutionRecover executionRecover = executorLoader.getHistoryRecoverFlow(recoverId);

    return executionRecover;
  }

  /**
   * 根据项目ID和工作流ID 查找正在运行的历史补采
   *
   * @param projectId
   * @param flowId
   * @return
   * @throws ExecutorManagerException
   */
  @Override
  public ExecutionRecover getHistoryRecoverFlowByPidAndFid(final String projectId, final String flowId)
          throws ExecutorManagerException {
    ExecutionRecover executionRecover = executorLoader.getHistoryRecoverFlowByPidAndFid(projectId, flowId);

    return executionRecover;
  }


  @Override
  public List<ExecutionRecover> listHistoryRecoverRunnning(final Integer loadSize)
          throws ExecutorManagerException {
    List<ExecutionRecover> flows = executorLoader.listHistoryRecoverRunnning(loadSize);
    return flows;
  }

  @Override
  public int getHistoryRecoverTotal() throws ExecutorManagerException {

    return executorLoader.getHistoryRecoverTotal();
  }

  @Override
  public ExecutableFlow getProjectLastExecutableFlow(int projectId, String flowId) throws ExecutorManagerException {
    ExecutableFlow flow = executorLoader.getProjectLastExecutableFlow(projectId, flowId);
    return flow;
  }

  @Override
  public int getUserHistoryRecoverTotal(String userName) throws ExecutorManagerException {

    return executorLoader.getUserRecoverHistoryTotal(userName);
  }

  @Override
  public int getMaintainedHistoryRecoverTotal(String username, List<Integer> maintainedProjectIds) throws ExecutorManagerException {
    return executorLoader.getMaintainedHistoryRecoverTotal(username, maintainedProjectIds);
  }

  @Override
  public int getExecutionCycleTotal(Optional<String> usernameOp) throws ExecutorManagerException {
    return executorLoader.getExecutionCycleTotal(usernameOp);
  }

  @Override
  public int getExecutionCycleAllTotal(String userName, String searchTerm, HashMap<String, String> queryMap) throws ExecutorManagerException {
    return executorLoader.getExecutionCycleAllTotal(userName, searchTerm, queryMap);
  }

  @Override
  public List<ExecutionCycle> getExecutionCycleAllPages(String userName, String searchTerm, int offset, int length, HashMap<String, String> queryMap) throws ExecutorManagerException {
    return executorLoader.getExecutionCycleAllPages(userName, searchTerm, offset, length, queryMap);
  }

  @Override
  public void deleteExecutionCycle(int projectId, String flowId, User user, Project project) throws ExecutorManagerException {
    executorLoader.deleteExecutionCycle(projectId, flowId);

    this.projectLoader.postEvent(project, ProjectLogEvent.EventType.DELETE_CYCLE_FLOW,
            user.getUserId() + (org.apache.commons.lang.StringUtils.isEmpty(user.getNormalUser()) ? "" : ("(" + user.getNormalUser() + ")")), " Delete CycleFlow the product: " + project.getName() + " flowId: " + flowId);
  }

  @Override
  public int getExecutionCycleTotal(String username, List<Integer> projectIds) throws ExecutorManagerException {
    return executorLoader.getExecutionCycleTotal(username, projectIds);
  }

  @Override
  public List<ExecutionCycle> listExecutionCycleFlows(Optional<String> username, int offset, int length)
          throws ExecutorManagerException {
    return executorLoader.listExecutionCycleFlows(username, offset, length);
  }

  @Override
  public List<ExecutionCycle> listExecutionCycleFlows(String username, List<Integer> projectIds, int offset, int length)
          throws ExecutorManagerException {
    return executorLoader.listExecutionCycleFlows(username, projectIds, offset, length);
  }

  @Override
  public int saveExecutionCycleFlow(ExecutionCycle cycleFlow) throws ExecutorManagerException {
    return executorLoader.saveExecutionCycleFlow(cycleFlow);
  }

  @Override
  public ExecutionCycle getExecutionCycleFlow(String projectId, String flowId) throws ExecutorManagerException {
    return executorLoader.getExecutionCycleFlow(projectId, flowId);
  }

  @Override
  public ExecutionCycle getExecutionCycleFlowDescId(String projectId, String flowId) throws ExecutorManagerException {
    return executorLoader.getExecutionCycleFlowDescId(projectId, flowId);
  }

  @Override
  public ExecutionCycle getExecutionCycleFlow(int id) throws ExecutorManagerException {
    return executorLoader.getExecutionCycleFlow(id);
  }

  @Override
  public int updateExecutionFlow(ExecutionCycle executionCycle) throws ExecutorManagerException {
    return executorLoader.updateExecutionFlow(executionCycle);
  }

  @Override
  public int stopAllCycleFlows() throws ExecutorManagerException {
    return executorLoader.stopAllCycleFlows();
  }

  @Override
  public List<ExecutionCycle> getAllRunningCycleFlows() throws ExecutorManagerException {
    return executorLoader.getAllRunningCycleFlows();
  }

  @Override
  public List<ExecutionCycle> getRunningCycleFlows(Integer projectId, String flowId) throws ExecutorManagerException {
    return executorLoader.getRunningCycleFlows(projectId, flowId);
  }

  @Override
  public void reloadWebData() {
    //for ha
  }

  @Override
  public String holdBatch(int oprType, int oprLevel, List<String> dataList, List<String> flowList,
                          List<String> busPathList, String batchId, User user, List<String> criticalPathList) {
    if (!this.holdBatchSwitch) {
      return "hold batch closed";
    }
    synchronized (ExecutorManager.class) {
      try {
        long start = System.currentTimeMillis();

        Map<String, List<String>> jsonMap = new HashMap<>(8);
        if (oprType > 0) {
          jsonMap.put("dataList", dataList);
          jsonMap.put("flowWhiteList", flowList);
          jsonMap.put("pathWhiteList", busPathList);
          jsonMap.put("criticalPathList", criticalPathList);
          HoldBatchOperate operate = new HoldBatchOperate(oprLevel, jsonMap);
          operate.fillList(this.scheduleManager);
          for (HoldBatchOperate holdBatchOperate : holdBatchContext.getBatchMap().values()) {
            if (holdBatchOperate.isExistBatch(operate)) {
              return "batch existed";
            }
          }
          this.executorLoader
                  .addHoldBatchOpr(UUID.randomUUID().toString(), oprType, oprLevel, user.getUserId(),
                          System.currentTimeMillis(), JSONUtils.toJSON(jsonMap));
        } else {
          boolean isExist = holdBatchContext.getBatchMap().values().stream().anyMatch(o -> o.getBatchId().equals(batchId));
          if (!isExist) {
            return "batchId is not exist";
          }
          jsonMap.put("dataList", dataList);
          jsonMap.put("flowBlackList", flowList);
          this.executorLoader
                  .addHoldBatchResume(batchId, JSONUtils.toJSON(jsonMap), user.getUserId());
        }

        long sleepTime = this.azkProps.getLong("azkaban.holdbatch.operate.ms", 30000L) - (
                System.currentTimeMillis() - start);
        if (sleepTime > 0) {
          Thread.sleep(sleepTime);
        }
      } catch (Exception e) {
        logger.error("hold batch error", e);
        return e.getMessage();
      }
      return "";
    }
  }

  @Override
  public List<ExecutableFlow> getAllFlows() throws IOException {
    List<ExecutableFlow> flowList = new ArrayList<>();
    try {
      flowList = this.executorLoader.fetchAllUnfinishedFlows();
    } catch (ExecutorManagerException e) {
      logger.error("Failed to get active flows with executor.", e);
    }
    return flowList;
  }

  /**
   * 获取日志压缩包路径
   * 根据 执行ID 查找到所有日志分段，并组合起来。打包成Zip包。返回路径
   *
   * @param executableFlow
   * @return
   * @throws ExecutorManagerException
   */
  @Override
  public String getDownLoadAllExecutionLog(ExecutableFlow executableFlow) throws ExecutorManagerException {

    String logZipFilePath = "";

    //循环获取数据库分段日志数据
    try {
      List<String> nameList = new ArrayList<>();
      //获取所有执行节点
      getExecutableNodeInfo(executableFlow, nameList);
      //获取日志文件夹
      File flowLogDir = new File("temp" + File.separator + executableFlow.getId() + System.currentTimeMillis());
      //如果目录存在
      if (flowLogDir.exists()) {
        //先删除目录
        flowLogDir.delete();
        //再创建文件夹
        flowLogDir.mkdir();
      } else {
        //不存在就直接创建文件夹
        flowLogDir.mkdir();
      }

      for (String jobName : nameList) {
        //获取日志文本
        LogData value = executorLoader.fetchAllLogs(executableFlow.getExecutionId(), jobName, 0);
        if (null != value) {
          //创建日志文件
          final File file = new File(flowLogDir + File.separator + jobName + ".log");
          //把日志文本写入到日志文件中
          if (file.getName().contains(jobName)) {
            fileWrite(file.getPath(), file.getName(), value.getData());
          }
        }
      }

      //把日志文件夹到包成Zip包
      logZipFilePath =
              fileToZip(flowLogDir.getPath(), new File("temp").getPath(), flowLogDir.getName());

    } catch (Exception e) {
      logger.error("下载所有日志数据失败, 原因为:", e);
    }

    return logZipFilePath;

  }

  public static String fileToZip(String sourceFilePath, String zipFilePath, String fileName) {
    boolean flag = false;
    File sourceFile = new File(sourceFilePath);
    FileInputStream fis = null;
    BufferedInputStream bis = null;
    FileOutputStream fos = null;
    ZipOutputStream zos = null;

    File zipFile = new File(zipFilePath + "/" + fileName + ".zip");
    if (sourceFile.exists() == false) {
      logger.info("待压缩的文件目录：" + sourceFilePath + "不存在.");
    } else {
      try {
        //zipFile = File.createTempFile(fileName, ".zip", new File("temp"));
        if (zipFile.exists()) {
          logger.info(zipFilePath + "目录下存在名字为:" + fileName + ".zip" + "打包文件.");
        } else {
          File[] sourceFiles = sourceFile.listFiles();
          if (null == sourceFiles || sourceFiles.length < 1) {
            logger.info("待压缩的文件目录：" + sourceFilePath + "里面不存在文件，无需压缩.");
          } else {
            fos = new FileOutputStream(zipFile);
            zos = new ZipOutputStream(new BufferedOutputStream(fos));
            byte[] bufs = new byte[1024 * 10];
            for (int i = 0; i < sourceFiles.length; i++) {
              //创建ZIP实体，并添加进压缩包
              ZipEntry zipEntry = new ZipEntry(sourceFiles[i].getName());
              zos.putNextEntry(zipEntry);
              //读取待压缩的文件并写进压缩包里
              fis = new FileInputStream(sourceFiles[i]);
              bis = new BufferedInputStream(fis, 1024 * 10);
              int read = 0;
              while ((read = bis.read(bufs, 0, 1024 * 10)) != -1) {
                zos.write(bufs, 0, read);
              }
            }
            flag = true;
          }
        }
      } catch (FileNotFoundException e) {
        logger.error("FileNotFoundException , caused by:" + e);
        throw new RuntimeException(e);
      } catch (IOException e) {
        logger.error("IOException , caused by:" + e);
        throw new RuntimeException(e);
      } finally {
        //关闭流
        IOUtils.closeQuietly(bis);
        IOUtils.closeQuietly(zos);
        IOUtils.closeQuietly(fis);
        IOUtils.closeQuietly(fos);
      }
    }
    return zipFile.getPath();
  }

  private Map<String, Object> getExecutableNodeInfo(final ExecutableNode node, final List<String> nameList) {
    final HashMap<String, Object> nodeObj = new HashMap<>();
    nodeObj.put("id", node.getId());

    if (null != node.getParentFlow()) {
      nameList.add(node.getNestedId());
    }

    if (node instanceof ExecutableFlowBase) {
      final ExecutableFlowBase base = (ExecutableFlowBase) node;
      final ArrayList<Map<String, Object>> nodeList = new ArrayList<>();
      for (final ExecutableNode subNode : base.getExecutableNodes()) {
        final Map<String, Object> subNodeObj = getExecutableNodeInfo(subNode, nameList);
        if (!subNodeObj.isEmpty()) {
          nodeList.add(subNodeObj);
        }
      }
      nodeObj.put("flow", base.getFlowId());
      nodeObj.put("nodes", nodeList);
      nodeObj.put("flowId", base.getFlowId());
    }

    return nodeObj;
  }

  public static void fileWrite(String allFilePath, String fileName, String fileStr) {

    FileWriter fw = null;
    try {
      fw = new FileWriter(allFilePath);
      //写入到文件
      if (StringUtils.isNotBlank(fileStr)) {
        fw.write(fileStr);
      }
    } catch (IOException e) {
      logger.warn("Failed to write fileStr file path{}", allFilePath, e);
    } finally {
      IOUtils.closeQuietly(fw);
    }
  }

  @Override
  public String getJobLogByJobId(int execId, String jobName) throws ExecutorManagerException {
    final ExecutableFlow exFlow = getExecutableFlow(execId);
    final ExecutableNode node = exFlow.getExecutableNodePath(jobName);
    final Pair<ExecutionReference, ExecutableFlow> pair =
            this.runningExecutions.get().get(exFlow.getExecutionId());
    if (node == null) {
      return "";
    }
    try {

      //获取日志文件夹
      File flowLogDir = new File("temp" + File.separator + jobName + System.currentTimeMillis());
      //如果目录存在
      if (flowLogDir.exists()) {
        //先删除目录
        flowLogDir.delete();
        //再创建文件夹
        flowLogDir.mkdir();
      } else {
        //不存在就直接创建文件夹
        flowLogDir.mkdir();
      }
      LogData value;
      if (Status.isStatusFinished(node.getStatus()) || pair == null) {
        // 已完成节点日志，从 DB/HDFS 获取
        int logEncType = this.executorLoader.getLogEncType(exFlow.getExecutionId(), jobName, 0);
        //获取Job日志文本
        if (this.azkProps.getBoolean(ConfigurationKeys.HDFS_LOG_SWITCH, false)
                && EncodingType.HDFS.getNumVal() == logEncType) {
          // 从 HDFS 获取日志
          logger.info("get job log for [execId: {}, flowId: {}, jobId: {}] from HDFS",
                  exFlow.getExecutionId(), exFlow.getId(), jobName);
          String hdfsLogPath = this.executorLoader.getHdfsLogPath(exFlow.getExecutionId(), jobName,
                  0);
          logger.info("HDFS log path: {} for [execId: {}, flowId: {}, jobId: {}]",
                  hdfsLogPath, exFlow.getExecutionId(), exFlow.getId(), jobName);
          String logContent = LogUtils.loadAllLogFromHdfs(hdfsLogPath);
          value = new LogData(0, 0, logContent, hdfsLogPath);
        } else {
          // DB 读取日志
          logger.info("get job log for [execId: {}, flowId: {}, jobId: {}] from DB",
                  exFlow.getExecutionId(), exFlow.getId(), jobName);
          value = executorLoader.fetchAllLogs(execId, jobName, 0);
        }

      } else {
        final Pair<String, String> typeParam = new Pair<>("type", "job");
        final Pair<String, String> jobIdParam =
                new Pair<>("jobId", jobName);
        final Pair<String, String> offsetParam =
                new Pair<>("offset", "0");
        final Pair<String, String> lengthParam =
                new Pair<>("length", "-1");
        final Pair<String, String> attemptParam =
                new Pair<>("attempt", node.getAttempt() + "");

        @SuppressWarnings("unchecked") final Map<String, Object> result =
                this.apiGateway.callWithReference(pair.getFirst(), ConnectorParams.LOG_ACTION,
                        typeParam, jobIdParam, offsetParam, lengthParam, attemptParam);
        value = LogData.createLogDataFromObject(result);
      }
      if (null != value) {
        //创建日志文件
        final File file = new File(flowLogDir + File.separator + jobName + ".txt");
        //把日志文本写入到日志文件中
        if (file.getName().contains(node.getId())) {
          fileWrite(file.getPath(), file.getName(), value.getData());
        }

        return file.getPath();
      }

    } catch (Exception e) {
      logger.error("getJobLogByJobId execute failed, caused by:", e);
    }


    return "";
  }

  @Override
  public LogData getJobLogDataByJobId(int execId, String name, int attempt)
          throws ExecutorManagerException {

    return executorLoader.fetchAllLogs(execId, name, attempt);
  }


  /**
   * @param exFlow
   * @param jobId   Job Id
   * @param attempt
   * @return
   * @throws ExecutorManagerException
   */
  @Override
  public String getAllExecutionJobLog(ExecutableFlow exFlow, String jobId, int attempt)
          throws ExecutorManagerException, IOException {
    Pair<ExecutionReference, ExecutableFlow> pair =
            runningFlows.get(exFlow.getExecutionId());

    StringBuilder allLogData = new StringBuilder();

    int offset = 0;
    int length = 50000;
    //
    while (true) {
      LogData value;
      int logEncType = this.executorLoader.getLogEncType(exFlow.getExecutionId(), jobId, attempt);
      if (this.azkProps.getBoolean(ConfigurationKeys.HDFS_LOG_SWITCH, false)
              && EncodingType.HDFS.getNumVal() == logEncType) {
        // 从 HDFS 获取日志
        logger.info("get job log for [execId: {}, flowId: {}, jobId: {}] from HDFS",
                exFlow.getExecutionId(), exFlow.getId(), jobId);
        String hdfsLogPath = this.executorLoader.getHdfsLogPath(exFlow.getExecutionId(), jobId,
                attempt);
        logger.info("HDFS log path: {} for [execId: {}, flowId: {}, jobId: {}]",
                hdfsLogPath, exFlow.getExecutionId(), exFlow.getId(), jobId);
        return LogUtils.loadAllLogFromHdfs(hdfsLogPath);
      } else {
        value = executorLoader.fetchLogs(exFlow.getExecutionId(), jobId, attempt, offset, length);
      }
      if (null == value) {
        break;
      }
      //
      allLogData.append(value.getData());
      offset += length;
    }
    return allLogData.toString();
  }


  /**
   * 获取所有的日志过滤规则
   *
   * @return
   * @throws ExecutorManagerException
   */
  @Override
  public List<LogFilterEntity> listAllLogFilter() throws ExecutorManagerException {

    return executorLoader.listAllLogFilter();

  }

  @Override
  public int getExecHistoryTotal(final HistoryQueryParam param) throws ExecutorManagerException {

    return executorLoader.getExecHistoryTotal(param);
  }

  @Override
  public int getExecHistoryTotal(HistoryQueryParam param, List<Integer> projectIds)
          throws ExecutorManagerException {
    return executorLoader.getExecHistoryTotal(param, projectIds);
  }

  @Override
  public int getMaintainedExecHistoryTotal(String username, List<Integer> projectIds)
          throws ExecutorManagerException {
    return executorLoader.getMaintainedExecHistoryTotal(username, projectIds);
  }

  @Override
  public int getExecHistoryQuickSerachTotal(final Map<String, String> filterMap) throws ExecutorManagerException {

    return executorLoader.getExecHistoryQuickSerachTotal(filterMap);
  }

  @Override
  public int getMaintainedFlowsQuickSearchTotal(String username, final Map<String, String> filterMap, List<Integer> projectIds)
          throws ExecutorManagerException {
    return executorLoader.getMaintainedFlowsQuickSearchTotal(username, filterMap, projectIds);
  }

  @Override
  public int getUserExecutableFlowsTotalByProjectIdAndFlowId(final int projectId, final String flowId, final int from,
                                                             final int length, final List<ExecutableFlow> outputList, final String userName)
          throws ExecutorManagerException {
    final List<ExecutableFlow> flows =
            this.executorLoader.fetchUserFlowHistoryByProjectIdAndFlowId(projectId, flowId, from, length, userName);
    outputList.addAll(flows);
    return this.executorLoader.fetchNumUserExecutableFlowsByProjectIdAndFlowId(projectId, flowId, userName);
  }

  @Override
  public long getExecutableFlowsMoyenneRunTime(int projectId, String flowId, String user)
          throws ExecutorManagerException {

    final List<ExecutableFlow> exFlows =
            this.executorLoader.fetchFlowAllHistory(projectId, flowId, user);

    long moyenne = 0;
    long allRunTime = 0;
    int successFlowNum = 0;
    for (final ExecutableFlow flow : exFlows) {
      if (Status.SUCCEEDED.equals(flow.getStatus())) {
        successFlowNum += 1;
        allRunTime += (flow.getEndTime() - flow.getStartTime());
      }
    }
    if (allRunTime != 0 && successFlowNum != 0) {
      moyenne = allRunTime / successFlowNum;
    }

    return moyenne;
  }

  @Override
  public int getUserExecHistoryTotal(HistoryQueryParam param, String loginUser) throws ExecutorManagerException {

    return executorLoader.getUserExecHistoryTotal(param, loginUser);
  }

  @Override
  public int getUserExecHistoryQuickSerachTotal(final Map<String, String> filterMap) throws ExecutorManagerException {

    return executorLoader.getUserExecHistoryQuickSerachTotal(filterMap);
  }

  /**
   * 根据条件查找用户的 flow 历史信息
   *
   * @param projContain
   * @param flowContain
   * @param userContain
   * @param status
   * @param begin
   * @param end
   * @param skip
   * @param size
   * @return
   * @throws ExecutorManagerException
   */
  @Override
  public List<ExecutableFlow> getUserExecutableFlows(String loginUser, String projContain,
                                                     String flowContain, String execIdContain, String userContain, String status, long begin,
                                                     long end, String runDate,
                                                     int skip, int size, int flowType) throws ExecutorManagerException {
    List<ExecutableFlow> flows =
            executorLoader.fetchUserFlowHistory(loginUser, projContain, flowContain, execIdContain,
                    userContain,
                    status, begin, end, runDate, skip, size, flowType);

    return flows;
  }

  @Override
  public List<ExecutableFlow> getUserExecutableFlows(String loginUser, HistoryQueryParam param,
                                                     int skip, int size) throws ExecutorManagerException {
    List<ExecutableFlow> flows =
            executorLoader.fetchUserFlowHistory(loginUser, param, skip, size);

    return flows;
  }

  @Override
  public List<ExecutableFlow> getTodayExecutableFlowData(final String userName) throws ExecutorManagerException {
    List<ExecutableFlow> flows =
            executorLoader.getTodayExecutableFlowData(userName);

    return flows;
  }

  @Override
  public List<ExecutableFlow> getTodayExecutableFlowDataNew(final String userName) throws ExecutorManagerException {

    return executorLoader.getTodayExecutableFlowDataNew(userName);
  }

  @Override
  public Integer getTodayFlowRunTimesByFlowId(final String projectId, String flowId, final String usename) throws ExecutorManagerException {
    return executorLoader.getTodayFlowRunTimesByFlowId(projectId, flowId, usename);
  }

  @Override
  public List<ExecutableFlow> getRealTimeExecFlowData(final String userName) throws ExecutorManagerException {
    List<ExecutableFlow> flows =
            executorLoader.getRealTimeExecFlowData(userName);

    return flows;
  }

  @Override
  public ExecutableFlow getRecentExecutableFlow(int projectId, String flowId)
          throws ExecutorManagerException {
    ExecutableFlow flow = executorLoader.getProjectLastExecutableFlow(projectId, flowId);
    return flow;
  }

  @Override
  public List<ExecutableFlow> fetchAllExecutableFlow() throws SQLException {
    return executorLoader.fetchAllExecutableFlow();
  }

  @Override
  public List<ExecutableFlow> fetchExecutableFlows(final long startTime) throws SQLException {
    return executorLoader.fetchExecutableFlows(startTime);
  }

  @Override
  public int updateExecutableFlow(ExecutableFlow flow) throws SQLException {
    return executorLoader.updateExecutableFlowRunDate(flow);
  }

  @Override
  public List<Map<String, String>> getExectingFlowsData(ExecutingQueryParam executingQueryParam) throws IOException {

    List<ExecutableFlow> flows = new ArrayList<>();
    try {
      if (null == executingQueryParam) {
        flows = this.executorLoader.fetchAllUnfinishedFlows();
      } else {
        flows = this.executorLoader.fetchUnfinishedFlows(executingQueryParam);
      }
    } catch (final ExecutorManagerException e) {
      logger.error("Failed to get active flows with executor.", e);
    }
    final List<Map<String, String>> exectingFlowList = new ArrayList<>();

    WebUtils webUtils = new WebUtils();
    if (null != flows && !flows.isEmpty()) {
      for (ExecutableFlow executableFlow : flows) {
        Map<String, String> repeatMap = executableFlow.getRepeatOption();
        if (!repeatMap.isEmpty()) {
          Long recoverRunDate = Long.valueOf(String.valueOf(repeatMap.get("startTimeLong")));
          LocalDateTime localDateTime = new LocalDateTime(new Date(recoverRunDate)).minusDays(1);
          Date date = localDateTime.toDate();
          executableFlow.setUpdateTime(date.getTime());
        } else {
          String runDatestr = executableFlow.getExecutionOptions().getFlowParameters().get("run_date");
          Object runDateOther = executableFlow.getOtherOption().get("run_date");
          if (runDatestr != null && !"".equals(runDatestr) && !runDatestr.isEmpty()) {
            try {
              executableFlow.setUpdateTime(Long.parseLong(runDatestr));
            } catch (Exception e) {
              logger.error("rundate convert failed (String to long), {}", runDatestr, e);
            } finally {
              executableFlow.setUpdateTime(0);
              executableFlow.getOtherOption().put("run_date", runDatestr);
            }
          } else if (runDateOther != null && !"".equals(runDateOther.toString()) && !runDateOther.toString().isEmpty()) {
            String runDateTime = (String) runDateOther;
            runDateTime = runDateTime.replaceAll("\'", "").replaceAll("\"", "");
            if (SystemBuiltInParamReplacer.dateFormatCheck(runDateTime)) {
              executableFlow.setUpdateTime(0);
              runDateTime = runDateTime.replaceAll("[./-]", "");
              executableFlow.getOtherOption().put("run_date", runDateTime);
            } else {
              if (-1 != executableFlow.getStartTime()) {
                LocalDateTime localDateTime = new LocalDateTime(new Date(executableFlow.getStartTime())).minusDays(1);
                Date date = localDateTime.toDate();
                executableFlow.setUpdateTime(date.getTime());
              }
            }
          } else {
            Long runDate = executableFlow.getStartTime();
            if (-1 != executableFlow.getStartTime()) {
              LocalDateTime localDateTime = new LocalDateTime(new Date(executableFlow.getStartTime())).minusDays(1);
              Date date = localDateTime.toDate();
              executableFlow.setUpdateTime(date.getTime());
            }
          }
        }
        Map<String, String> exectingMap = new HashMap<>();

        try {
//          long moyenne = this.getExecutableFlowsMoyenneRunTime(
//              executableFlow.getProjectId(), executableFlow.getFlowId(), null);

          exectingMap.put("execId", executableFlow.getExecutionId() + "");
          String executorId = (String) executableFlow.getOtherOption().get("currentExecutorId") != null ? (String) executableFlow.getOtherOption().get("currentExecutorId") : "";
          exectingMap.put("exectorId", executorId);
          exectingMap.put("flowName", executableFlow.getFlowId());
          exectingMap.put("projectName", executableFlow.getProjectName());
          exectingMap.put("submitUser", executableFlow.getSubmitUser());
          exectingMap.put("proxyUsers", executableFlow.getProxyUsers().toString());
          exectingMap.put("startTime", webUtils.formatHistoryDateTime(executableFlow.getStartTime()));
          if (StringUtils.isNotBlank(executableFlow.getRunDate())) {
            exectingMap.put("runDate", executableFlow.getRunDate());
          } else if (executableFlow.getOtherOption().get("run_date") != null) {
            exectingMap.put("runDate", executableFlow.getUpdateTime() == 0 ? executableFlow.getOtherOption().get("run_date").toString() : webUtils.formatRunDate(executableFlow.getUpdateTime()));
          } else {
            exectingMap.put("runDate", webUtils.formatRunDate(executableFlow.getUpdateTime()));
          }
          exectingMap.put("duration", Utils
                  .formatDuration(executableFlow.getStartTime(), executableFlow.getEndTime()));
//          exectingMap.put("execTime", webUtils.formatDurationTime(executableFlow.getStartTime(), executableFlow.getEndTime()) + "");
//          exectingMap.put("moyenne", moyenne + "");
          exectingMap.put("status", executableFlow.getStatus().toString());
          exectingMap.put("flowType", String.valueOf(executableFlow.getFlowType()));
          exectingMap.put("projectId", String.valueOf(executableFlow.getProjectId()));

          exectingFlowList.add(exectingMap);
        } catch (Exception e) {
          throw new RuntimeException("generate executingMap failed", e);
        }
      }
    }

    return exectingFlowList;
  }

  @Override
  public long getExectingFlowsTotal(ExecutingQueryParam executingQueryParam) throws ExecutorManagerException {
    long total = 0;
    if (null == executingQueryParam) {
      total = this.executorLoader.getAllUnfinishedFlows();
    } else {
      total = this.executorLoader.getUnfinishedFlowsTotal(executingQueryParam);
    }
    return total;
  }

  @Override
  public String getExecutorIdByHostname(String hostname) throws ExecutorManagerException {
    Hosts hosts = this.executorLoader.getHostConfigByHostname(hostname);
    return String.valueOf(hosts.getExecutorid());
  }

  @Override
  public boolean checkExecutorStatus(int id) {
    refreshExecutors();
    for (Executor executor : activeExecutors.getAll()) {
      if (executor.getId() == id && executor.getExecutorInfo() != null) {
        return true;
      }
    }
    return false;
  }

  @Override
  public List<Integer> fetchPermissionsProjectId(String user) {
    return projectLoader.fetchPermissionsProjectId(user);
  }

  @Override
  public void linkJobHook(String jobCode, String prefixRules, String suffixRules, User user)
          throws SQLException {
    this.executorLoader.linkJobHook(jobCode, prefixRules, suffixRules, user.getUserId());
  }

  @Override
  public void resumeBatchFlow(long id) {
    synchronized (("resumeBatch-" + id).intern()) {
      try {
        HoldBatchAlert holdBatchAlert = this.executorLoader.queryBatchExecutableFlows(id);
        if (holdBatchAlert == null) {
          throw new RuntimeException("flow not in batch");
        }
        if (holdBatchAlert.getExecId() > 0) {
          holdBatchAlert = this.executorLoader.querySubmittedExecutableFlows(id);
          Pair<ExecutionReference, ExecutableFlow> pair =
                  this.runningExecutions.get()
                          .get(holdBatchAlert.getExecutableFlow().getExecutionId());
          if (pair != null) {
            Map<String, Object> result = this.apiGateway
                    .callWithReference(pair.getFirst(), ConnectorParams.RESUME_BATCH_ACTION);
            if (result.containsKey(ConnectorParams.RESPONSE_ERROR)) {
              throw new RuntimeException(result.get(ConnectorParams.RESPONSE_ERROR) + "");
            }
          } else {
            holdBatchAlert.getExecutableFlow().setStatus(Status.PREPARING);
            holdBatchAlert.getExecutableFlow().getOtherOption().put("isHoldingSubmit", true);
            holdBatchAlert.getExecutableFlow().getOtherOption()
                    .put("lastBatchId", holdBatchAlert.getBatchId());
            this.executorLoader.updateExecutableFlow(holdBatchAlert.getExecutableFlow());
            this.queuedFlows.enqueue(holdBatchAlert.getExecutableFlow(),
                    new ExecutionReference(holdBatchAlert.getExecutableFlow().getExecutionId()));
          }
        } else {
          holdBatchAlert.getExecutableFlow().getOtherOption()
                  .put("lastBatchId", holdBatchAlert.getBatchId());
          this.executorLoader.uploadExecutableFlow(holdBatchAlert.getExecutableFlow());
          holdBatchAlert.setExecId(holdBatchAlert.getExecutableFlow().getExecutionId());
          final ExecutionReference reference = new ExecutionReference(
                  holdBatchAlert.getExecutableFlow().getExecutionId());
          this.queuedFlows.enqueue(holdBatchAlert.getExecutableFlow(), reference);
        }
        holdBatchAlert.setResume(1);
        holdBatchAlert.setResumeTime(System.currentTimeMillis());
        this.executorLoader.updateHoldBatchResumeStatus(holdBatchAlert);

      } catch (Exception e) {
        logger.error("resume batch flow error", e);
        throw new RuntimeException("resume batch flow error", e);
      }
    }
  }

  @Override
  public void stopBatchFlow(long id) {
    synchronized (("stopBatch-" + id).intern()) {
      try {
        HoldBatchAlert holdBatchAlert1 = this.executorLoader.queryBatchExecutableFlows(id);
        if (holdBatchAlert1 == null) {
          throw new RuntimeException("flow not in batch");
        }
        HoldBatchAlert holdBatchAlert = this.executorLoader.querySubmittedExecutableFlows(id);
        if (holdBatchAlert != null) {
          Pair<ExecutionReference, ExecutableFlow> pair =
                  this.runningExecutions.get().get(holdBatchAlert.getExecutableFlow().getExecutionId());
          if (pair != null) {
            Map<String, Object> result = this.apiGateway
                    .callWithReference(pair.getFirst(), ConnectorParams.CANCEL_ACTION);
            if (result.containsKey(ConnectorParams.RESPONSE_ERROR)) {
              throw new RuntimeException(result.get(ConnectorParams.RESPONSE_ERROR) + "");
            }
          } else {
            if (!Status.isStatusFinished(holdBatchAlert.getExecutableFlow().getStatus())) {
              holdBatchAlert.getExecutableFlow().setStatus(Status.FAILED);
              this.executorLoader.updateExecutableFlow(holdBatchAlert.getExecutableFlow());
            }
          }
        } else {
          holdBatchAlert = holdBatchAlert1;
        }
        holdBatchAlert.setResume(2);
        this.executorLoader.updateHoldBatchResumeStatus(holdBatchAlert);

        if (holdBatchAlert.getExecutableFlow() != null) {
          this.updateScheduleMissedTime(holdBatchAlert);
        }

      } catch (Exception e) {
        logger.error("stop batch flow error", e);
        throw new RuntimeException("stop batch flow error", e);
      }
    }
  }

  @Override
  public List<ExecutionRecover> fetchUserHistoryRerunConfiguration(int projectId, String flowName, String userId, int start, int size) throws ExecutorManagerException {
    return this.executorLoader.getUserHistoryRerunConfiguration(projectId, flowName, userId, start, size);
  }

  @Override
  public List<ExecutionRecover> fetchMaintainedHistoryRerunConfiguration(int projectId, String flowName, String userId, int start, int size) throws ExecutorManagerException {
    return this.executorLoader.getMaintainedHistoryRerunConfiguration(projectId, flowName, userId, start, size);
  }

  @Override
  public List<ExecutionRecover> fetchAllHistoryRerunConfiguration(int projectId, String flowName, int start, int size) throws ExecutorManagerException {
    return this.executorLoader.getAllHistoryRerunConfiguration(projectId, flowName, start, size);
  }

  @Override
  public int getAllExecutionRecoverTotal(int projectId, String flowName) throws ExecutorManagerException {
    return this.executorLoader.getAllExecutionRecoverTotal(projectId, flowName);
  }

  @Override
  public int getMaintainedExecutionRecoverTotal(int projectId, String flowName, String userId) throws ExecutorManagerException {
    return this.executorLoader.getMaintainedExecutionRecoverTotal(projectId, flowName, userId);
  }

  @Override
  public int getUserExecutionRecoverTotal(int projectId, String flowName, String userId) throws ExecutorManagerException {
    return this.executorLoader.getUserExecutionRecoverTotal(projectId, flowName, userId);
  }

}
