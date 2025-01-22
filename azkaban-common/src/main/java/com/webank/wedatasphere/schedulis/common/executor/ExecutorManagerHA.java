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

package com.webank.wedatasphere.schedulis.common.executor;

import static java.util.Objects.requireNonNull;

import azkaban.Constants;
import azkaban.Constants.ConfigurationKeys;
import azkaban.db.DatabaseOperator;
import azkaban.event.EventHandler;
import azkaban.executor.ActiveExecutors;
import azkaban.executor.ConnectorParams;
import azkaban.executor.ExecutableFlow;
import azkaban.executor.ExecutableFlowBase;
import azkaban.executor.ExecutableJobInfo;
import azkaban.executor.ExecutableNode;
import azkaban.executor.ExecutionFinalizer;
import azkaban.executor.ExecutionOptions;
import azkaban.executor.ExecutionReference;
import azkaban.executor.Executor;
import azkaban.executor.ExecutorApiGateway;
import azkaban.executor.ExecutorInfo;
import azkaban.executor.ExecutorLoader;
import azkaban.executor.ExecutorManagerAdapter;
import azkaban.executor.ExecutorManagerException;
import azkaban.executor.ExecutorManagerUpdaterStage;
import azkaban.executor.QueuedExecutions;
import azkaban.executor.RunningExecutions;
import azkaban.executor.RunningExecutionsUpdaterThread;
import azkaban.executor.Status;
import azkaban.executor.selector.ExecutorComparator;
import azkaban.executor.selector.ExecutorFilter;
import azkaban.executor.selector.ExecutorSelector;
import azkaban.flow.Flow;
import azkaban.flow.FlowUtils;
import azkaban.history.ExecutionRecover;
import azkaban.history.GroupTask;
import azkaban.history.RecoverTrigger;
import azkaban.metrics.CommonMetrics;
import azkaban.project.Project;
import azkaban.project.ProjectLoader;
import azkaban.project.ProjectManagerException;
import azkaban.project.ProjectWhitelist;
import azkaban.scheduler.ScheduleManager;
import azkaban.user.User;
import azkaban.utils.AuthenticationUtils;
import azkaban.utils.FileIOUtils.JobMetaData;
import azkaban.utils.FileIOUtils.LogData;
import azkaban.utils.Pair;
import azkaban.utils.Props;
import azkaban.utils.Utils;
import azkaban.utils.WebUtils;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.Lists;
import com.webank.wedatasphere.schedulis.common.distributelock.DBTableDistributeLock;
import com.webank.wedatasphere.schedulis.common.executor.ExecutorQueueLoader;
import com.webank.wedatasphere.schedulis.common.jobExecutor.utils.SystemBuiltInParamJodeTimeUtils;
import com.webank.wedatasphere.schedulis.common.log.LogFilterEntity;
import com.webank.wedatasphere.schedulis.common.utils.HttpUtils;
import com.webank.wedatasphere.schedulis.common.utils.JwtTokenUtils;
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
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import javax.inject.Inject;
import javax.inject.Singleton;
import org.apache.commons.lang.StringUtils;
import org.joda.time.DateTime;
import org.joda.time.LocalDateTime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Executor manager used to manage the client side job.
 */
@Singleton
public class ExecutorManagerHA extends EventHandler implements
    ExecutorManagerAdapter {

  // 历史重跑轮训时间间隔
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
  private static final Logger logger = LoggerFactory.getLogger(ExecutorManagerHA.class);
  private static final String CLEAN_LOG_LOCK_KEY = "clean_log_lock_key";
  private static final String RECOVER_LOCK_KEY = "recover_lock_key";
  private final RunningExecutions runningExecutions;
  private final Props azkProps;
  private final CommonMetrics commonMetrics;
  private final ExecutorLoader executorLoader;
  private final ExecutorQueueLoader executorQueueLoader;
  private ProjectLoader projectLoader;
  private final CleanerThread cleanerThread;
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
  File cacheDir;
  private QueueProcessorThread queueProcessor;
  private volatile Pair<ExecutionReference, ExecutableFlow> runningCandidate = null;
  private long lastCleanerThreadCheckTime = -1;
  private long lastThreadCheckTime = -1;
  private List<String> filterList;
  private Map<String, Integer> comparatorWeightsMap;
  private long lastSuccessfulExecutorInfoRefresh;
  private Duration sleepAfterDispatchFailure = Duration.ofSeconds(1L);
  private boolean initialized = false;
  private ExecutorService executorInforRefresherService;
  private DatabaseOperator dbOperator;

  @Inject
  public ExecutorManagerHA(final Props azkProps, final ExecutorLoader executorLoader,
      final ExecutorQueueLoader executorQueueLoader,
      final CommonMetrics commonMetrics,
      final ExecutorApiGateway apiGateway,
      final RunningExecutions runningExecutions,
      final ActiveExecutors activeExecutors,
      final ExecutorManagerUpdaterStage updaterStage,
      final ExecutionFinalizer executionFinalizer,
      final RunningExecutionsUpdaterThread updaterThread,
      final ProjectLoader projectLoader, final DatabaseOperator dbOperator,
      final ScheduleManager scheduleManager
  ) throws ExecutorManagerException {
    this.azkProps = azkProps;
    this.commonMetrics = commonMetrics;
    this.executorLoader = executorLoader;
    this.executorQueueLoader = executorQueueLoader;
    this.projectLoader = projectLoader;
    this.apiGateway = apiGateway;
    this.runningExecutions = runningExecutions;
    this.activeExecutors = activeExecutors;
    this.updaterStage = updaterStage;
    this.executionFinalizer = executionFinalizer;
    this.updaterThread = updaterThread;
    this.maxConcurrentRunsOneFlow = getMaxConcurrentRunsOneFlow(azkProps);
    this.cleanerThread = createCleanerThread();
    this.recoverThread = createRecoverThread();
    this.executorInfoRefresherService = createExecutorInfoRefresherService();
    this.dbOperator = dbOperator;
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
//    this.loadQueuedFlows();
    this.cacheDir = new File(this.azkProps.getString("cache.directory", "cache"));
    // TODO extract QueueProcessor as a separate class, move all of this into it
    setupExecutotrComparatorWeightsMap();
    setupExecutorFilterList();
    this.queueProcessor = setupQueueProcessor();
  }

  @Override
  public void start() throws ExecutorManagerException {
    initialize();
    this.updaterThread.start();
    this.cleanerThread.start();
    this.queueProcessor.start();
    this.recoverThread.start();
  }

  private String findApplicationIdFromLog(final String logData) {
    final Matcher matcher = APPLICATION_ID_PATTERN.matcher(logData);
    String appId = null;
    if (matcher.find()) {
      appId = matcher.group().substring(12);
    }
    ExecutorManagerHA.logger.info("Application ID is " + appId);
    return appId;
  }

  private QueueProcessorThread setupQueueProcessor() {
    return new QueueProcessorThread(
        this.azkProps.getBoolean(ConfigurationKeys.QUEUEPROCESSING_ENABLED, true),
        this.azkProps.getLong(ConfigurationKeys.ACTIVE_EXECUTOR_REFRESH_IN_MS, 50000),
        this.azkProps.getInt(
            ConfigurationKeys.ACTIVE_EXECUTOR_REFRESH_IN_NUM_FLOW, 5),
        this.azkProps.getInt(
            ConfigurationKeys.MAX_DISPATCHING_ERRORS_PERMITTED,
            this.activeExecutors.getAll().size()),
        this.sleepAfterDispatchFailure);
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
   * @see ExecutorManagerAdapter#setupExecutors()
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
    if (!this.azkProps.getBoolean(ConfigurationKeys.USE_MULTIPLE_EXECUTORS, false)) {
      throw new IllegalArgumentException(
          ConfigurationKeys.USE_MULTIPLE_EXECUTORS +
              " must be true. Single executor mode is not supported any more.");
    }
  }

  /**
   * Refresh Executor stats for all the actie executors in this ExecutorManagerHA
   */
  private void refreshExecutors() {

    final List<Pair<Executor, Future<ExecutorInfo>>> futures =
        new ArrayList<>();
    for (final Executor executor : this.activeExecutors.getAll()) {
      // execute each executorInfo refresh task to fetch
      final Future<ExecutorInfo> fetchExecutionInfo =
          this.executorInfoRefresherService.submit(
              () -> this.apiGateway.callForJsonType(executor.getHost(),
                  executor.getPort(), "/serverStatistics", null, ExecutorInfo.class));
      futures.add(new Pair<>(executor,
          fetchExecutionInfo));
    }

    boolean wasSuccess = true;
    for (final Pair<Executor, Future<ExecutorInfo>> refreshPair : futures) {
      final Executor executor = refreshPair.getFirst();
      executor.setExecutorInfo(null); // invalidate cached ExecutorInfo
      try {
        // max 5 secs
        final ExecutorInfo executorInfo = refreshPair.getSecond().get(5, TimeUnit.SECONDS);
        // executorInfo is null if the response was empty
        executor.setExecutorInfo(executorInfo);
        logger.info(String.format(
            "Successfully refreshed executor: %s with executor info : %s",
            executor, executorInfo));
      } catch (final TimeoutException e) {
        wasSuccess = false;
        logger.error("Timed out while waiting for ExecutorInfo refresh"
            + executor, e);
      } catch (final Exception e) {
        wasSuccess = false;
        logger.error("Failed to update ExecutorInfo for executor : "
            + executor, e);
      }

      // update is successful for all executors
      if (wasSuccess) {
        this.lastSuccessfulExecutorInfoRefresh = System.currentTimeMillis();
      }
    }
  }

  /**
   * @see ExecutorManagerAdapter#disableQueueProcessorThread()
   */
  @Override
  public void disableQueueProcessorThread() {
    this.queueProcessor.setActive(false);
  }

  /**
   * @see ExecutorManagerAdapter#enableQueueProcessorThread()
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
   * @see ExecutorManagerAdapter#fetchExecutor(int)
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
    this.runningExecutions.get().clear();
    this.runningExecutions.get().putAll(activeFlows);
  }

  /*
   * load queued flows i.e with active_execution_reference and not assigned to
   * any executor
   */
  private QueuedExecutions loadQueuedFlows() throws ExecutorManagerException {
    QueuedExecutions queuedFlows = new QueuedExecutions(
        this.azkProps.getLong(ConfigurationKeys.WEBSERVER_QUEUE_SIZE, 100000));
    final List<Pair<ExecutionReference, ExecutableFlow>> retrievedExecutions =
        this.executorLoader.fetchQueuedFlows();
    if (retrievedExecutions != null) {
      for (final Pair<ExecutionReference, ExecutableFlow> pair : retrievedExecutions) {
        queuedFlows.enqueue(pair.getSecond(), pair.getFirst());
      }
    }
    return queuedFlows;
  }

  /**
   * Gets a list of all the active (running flows and non-dispatched flows) executions for a given
   * project and flow {@inheritDoc}. Results should be sorted as we assume this while setting up
   * pipelined execution Id.
   *
   * @see ExecutorManagerAdapter#getRunningFlows(int, String)
   */
  @Override
  public List<Integer> getRunningFlows(final int projectId, final String flowId) {
    List<Integer> executionIds = new ArrayList<>();
    try {
      QueuedExecutions queuedFlows = loadQueuedFlows();
      executionIds.addAll(getRunningFlowsHelper(projectId, flowId, queuedFlows.getAllEntries(),false));
      executionIds.addAll(this.executorLoader.getRunningExecByLock(projectId, flowId));
      // it's possible an execution is runningCandidate, meaning it's in dispatching state neither in queuedFlows nor runningFlows,
      // so checks the runningCandidate as well.
      if (this.runningCandidate != null) {
        executionIds.addAll(getRunningFlowsHelper(projectId, flowId, Lists.newArrayList(this.runningCandidate),false));
      }
      executionIds.addAll(getRunningFlowsHelper(projectId, flowId, this.runningExecutions.get().values(),true));
      executionIds = executionIds.stream().distinct().collect(Collectors.toList());
      Collections.sort(executionIds);
    } catch (ExecutorManagerException e) {
      logger.error("getRunningFlows loadQueuedFlows failed", e);
    }
    return executionIds;
  }

  /* Helper method for getRunningFlows */
  private List<Integer> getRunningFlowsHelper(final int projectId, final String flowId,
      final Collection<Pair<ExecutionReference, ExecutableFlow>> collection,final boolean isRunningExecution) {
    final List<Integer> executionIds = new ArrayList<>();
    for (final Pair<ExecutionReference, ExecutableFlow> ref : collection) {
      if (ref.getSecond().getFlowId().equals(flowId)
          && ref.getSecond().getProjectId() == projectId) {
        if (isRunningExecution) {
          try {
            ExecutableFlow runningFlow = this.executorLoader
                    .fetchExecutableFlow(ref.getSecond().getExecutionId());
            if (runningFlow != null && Status.isStatusRunning(runningFlow.getStatus())) {
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
   * @see ExecutorManagerAdapter#getActiveFlowsWithExecutor()
   */
  @Override
  public List<Pair<ExecutableFlow, Optional<Executor>>> getActiveFlowsWithExecutor()
      throws IOException {
    final List<Pair<ExecutableFlow, Optional<Executor>>> flows =
        new ArrayList<>();
    QueuedExecutions queuedFlows = null;
    try {
      queuedFlows = loadQueuedFlows();
    } catch (ExecutorManagerException e) {
      logger.error("getActiveFlowsWithExecutor loadQueuedFlows failed",e);
    }
    getActiveFlowsWithExecutorHelper(flows, queuedFlows.getAllEntries());
    getActiveFlowsWithExecutorHelper(flows, this.runningExecutions.get().values());

    if(null != flows && !flows.isEmpty()){
      flows.stream().forEach(pair -> {
        ExecutableFlow executableFlow = pair.getFirst();
        Map<String, String> repeatMap = executableFlow.getRepeatOption();
        if(!repeatMap.isEmpty()){

          Long recoverRunDate = Long.valueOf(String.valueOf(repeatMap.get("startTimeLong")));

          LocalDateTime localDateTime = new LocalDateTime(new Date(recoverRunDate)).minusDays(1);

          Date date = localDateTime.toDate();

          executableFlow.setUpdateTime(date.getTime());
        }else{
          Long runDate = executableFlow.getStartTime();
          if(-1 != runDate){
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
   * @see ExecutorManagerAdapter#isFlowRunning(int, String)
   */
  @Override
  public boolean isFlowRunning(final int projectId, final String flowId) {
    QueuedExecutions queuedFlows = null;
    try {
      queuedFlows = loadQueuedFlows();
    } catch (ExecutorManagerException e) {
      logger.error("getActiveFlowsWithExecutor loadQueuedFlows failed",e);
    }
    boolean isRunning = false;
    isRunning =
        isRunning
            || isFlowRunningHelper(projectId, flowId, queuedFlows.getAllEntries());
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
   * @see ExecutorManagerAdapter#getExecutableFlow(int)
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
   *
   * {@inheritDoc}
   *
   * @see ExecutorManagerAdapter#getRunningFlows()
   */
  @Override
  public List<ExecutableFlow> getRunningFlows() {
    final ArrayList<ExecutableFlow> flows = new ArrayList<>();
    QueuedExecutions queuedFlows = null;
    try {
      queuedFlows = loadQueuedFlows();
    } catch (ExecutorManagerException e) {
      logger.error("getRunningFlows loadQueuedFlows failed",e);
    }
    getActiveFlowHelper(flows, queuedFlows.getAllEntries());
    getActiveFlowHelper(flows, this.runningExecutions.get().values());
    return flows;
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
    QueuedExecutions queuedFlows = null;
    try {
      queuedFlows = loadQueuedFlows();
    } catch (ExecutorManagerException e) {
      logger.error("getActiveFlowsWithExecutor loadQueuedFlows failed",e);
    }
    getRunningFlowsIdsHelper(allIds, queuedFlows.getAllEntries());
    getRunningFlowsIdsHelper(allIds, this.runningExecutions.get().values());
    Collections.sort(allIds);
    return allIds.toString();
  }

  /**
   * Get execution Ids of all non-dispatched flows
   */
  public String getQueuedFlowIds() {
    final List<Integer> allIds = new ArrayList<>();
    QueuedExecutions queuedFlows = null;
    try {
      queuedFlows = loadQueuedFlows();
    } catch (ExecutorManagerException e) {
      logger.error("getQueuedFlowIds loadQueuedFlows failed",e);
    }
    getRunningFlowsIdsHelper(allIds, queuedFlows.getAllEntries());
    Collections.sort(allIds);
    return allIds.toString();
  }

  /**
   * Get the number of non-dispatched flows. {@inheritDoc}
   */
  @Override
  public long getQueuedFlowSize() {
    QueuedExecutions queuedFlows = null;
    try {
      queuedFlows = loadQueuedFlows();
    } catch (ExecutorManagerException e) {
      logger.error("getQueuedFlowSize loadQueuedFlows failed",e);
    }
    return queuedFlows.size();
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
    if(null != flows && !flows.isEmpty()){
      flows.stream().forEach(executableFlow -> {
        Map<String, String> repeatMap = executableFlow.getRepeatOption();
        if(!repeatMap.isEmpty()){

          Long recoverRunDate = Long.valueOf(String.valueOf(repeatMap.get("startTimeLong")));

          LocalDateTime localDateTime = new LocalDateTime(new Date(recoverRunDate)).minusDays(1);

          Date date = localDateTime.toDate();

          executableFlow.setUpdateTime(date.getTime());
        }else{
          Long runDate = executableFlow.getStartTime();
          if(-1 != runDate){
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
  public List<ExecutableFlow> getMaintainedExecutableFlows(String username, List<Integer> projectIds, int skip, int size)
      throws ExecutorManagerException {
    return this.executorLoader.fetchMaintainedFlowHistory(username, projectIds, skip, size);
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
  public List<ExecutableFlow> getExecutableFlows(final String projContain, final String flowContain,
      final String execIdContain, final String userContain,
      final String status, final long begin, final long end,
      final int skip, final int size, int flowType) throws ExecutorManagerException {
    final List<ExecutableFlow> flows =
        this.executorLoader.fetchFlowHistory(projContain, flowContain, execIdContain, userContain,
            status, begin, end, skip, size, flowType);

    return flows;
  }

  @Override
  public List<ExecutableFlow> getMaintainedExecutableFlows(String projContain, String flowContain,
      String execIdContain, String userContain, String status, long begin, long end,
      int skip, int size, int flowType, String username, List<Integer> projectIds) throws ExecutorManagerException {
    return this.executorLoader.fetchMaintainedFlowHistory(projContain, flowContain, execIdContain, userContain,
        status, begin, end, skip, size, flowType, username, projectIds);
  }

  @Override
  public List<ExecutableJobInfo> getExecutableJobs(final Project project,
      final String jobId, final int skip, final int size) throws ExecutorManagerException {
    final List<ExecutableJobInfo> nodes =
        this.executorLoader.fetchJobHistory(project.getId(), jobId, skip, size);
    return nodes;
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
        if(Status.SUCCEEDED.equals(info.getStatus())){
          successFlowNum += 1;
          allRunTime += (info.getEndTime() - info.getStartTime());
        }
      }
      if(allRunTime !=0 && successFlowNum !=0){
        moyenne = allRunTime/successFlowNum;
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
  public int getNumberOfExecutions(final Project project, final String flowId)
      throws ExecutorManagerException {
    return this.executorLoader.fetchNumExecutableFlows(project.getId(), flowId);
  }

  @Override
  public LogData getExecutableFlowLog(final ExecutableFlow exFlow, final int offset,
      final int length) throws ExecutorManagerException {
    final Pair<ExecutionReference, ExecutableFlow> pair =
        this.runningExecutions.get().get(exFlow.getExecutionId());
    if (pair != null) {
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
      final LogData value =
          this.executorLoader.fetchLogs(exFlow.getExecutionId(), "", 0, offset,
              length);
      return value;
    }
  }

  @Override
  public LogData getExecutionJobLog(final ExecutableFlow exFlow, final String jobId,
      final int offset, final int length, final int attempt) throws ExecutorManagerException {
    final Pair<ExecutionReference, ExecutableFlow> pair =
        this.runningExecutions.get().get(exFlow.getExecutionId());
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
          this.apiGateway.callWithReference(pair.getFirst(), ConnectorParams.LOG_ACTION,
              typeParam, jobIdParam, offsetParam, lengthParam, attemptParam);
      return LogData.createLogDataFromObject(result);
    } else {
      final LogData value =
          this.executorLoader.fetchLogs(exFlow.getExecutionId(), jobId, attempt,
              offset, length);
      return value;
    }
  }

  @Override
  public Long getLatestLogOffset(ExecutableFlow exFlow, String jobId, Long length, int attempt, User user) throws ExecutorManagerException {
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
      logger.info("get offset from db.");
      return this.executorLoader.getJobLogOffset(exFlow.getExecutionId(), jobId, attempt, length);
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
    } catch (final ExecutorManagerException e) {
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
   * @see ExecutorManagerAdapter#cancelFlow(ExecutableFlow,
   * String)
   */
  @Override
  public void cancelFlow(final ExecutableFlow exFlow, final String userId)
      throws ExecutorManagerException {
    cancelFlow(exFlow, userId, ConnectorParams.CANCEL_ACTION);
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
  }

  private void cancelFlow(final ExecutableFlow exFlow, final String userId, final String actionType)
      throws ExecutorManagerException {
    QueuedExecutions queuedFlows = null;
    try {
      queuedFlows = loadQueuedFlows();
    } catch (ExecutorManagerException e) {
      logger.error("getActiveFlowsWithExecutor loadQueuedFlows failed", e);
    }
    synchronized (exFlow) {
      if (this.runningExecutions.get().containsKey(exFlow.getExecutionId())) {
        final Pair<ExecutionReference, ExecutableFlow> pair =
            this.runningExecutions.get().get(exFlow.getExecutionId());
        this.apiGateway.callWithReferenceByUser(pair.getFirst(), actionType, userId);
      } else if (queuedFlows.hasExecution(exFlow.getExecutionId())) {//如果是正在排队的Flow 就直接在这边处理
        queuedFlows.dequeue(exFlow.getExecutionId());

        //todo 删除 execution_flows 表中的内容

        this.executionFinalizer
            .finalizeFlow(exFlow, "Cancelled before dispatching to executor", null);  // 通用告警
      } else {
        throw new ExecutorManagerException("Executor Id is["
            + exFlow.getExecutionId() + "] and its Flow[" + exFlow.getFlowId()
            + "] has stop working.");
      }
    }
  }

  @Override
  public void resumeFlow(final ExecutableFlow exFlow, final String userId)
      throws ExecutorManagerException {
    synchronized (exFlow) {
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

  @Override
  public void setFlowFailed(ExecutableFlow exFlow, String userId, List<Pair<String, String>> param) throws Exception {
    synchronized (exFlow) {
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

  private String getToken(){
    String token = "";
    try {
      String dss_secret = azkProps.getString("dss.secret", "dws-wtss|WeBankBDPWTSS&DWS@2019");
      token = JwtTokenUtils.getToken(null,false,dss_secret,300);
    }catch (RuntimeException e){
      logger.error("getToken failed when execute httppost ,caused by {}",e);
    }
    return token;
  }

  @Override
  public String setJobDisabled(ExecutableFlow exFlow, String userId, String request) throws Exception {
    synchronized (exFlow) {
      final Pair<ExecutionReference, ExecutableFlow> pair =
          this.runningExecutions.get().get(exFlow.getExecutionId());
      if (pair == null) {
        throw new Exception("Execution "
            + exFlow.getExecutionId() + " of flow " + exFlow.getFlowId()
            + " isn't running.");
      }
      String url = "http://" + pair.getFirst().getExecutor().get().getHost() + ":" + pair.getFirst().getExecutor().get().getPort() + "/executor?"
          + "action=" + ConnectorParams.DISABLE_JOB_ACTION + "&execid=" + exFlow.getExecutionId() + "&user=" + userId + "&token=" + getToken();

      return this.apiGateway.httpPost(url, request);
    }
  }

  @Override
  public String retryFailedJobs(ExecutableFlow exFlow, String userId, String request) throws Exception {
    synchronized (exFlow) {
      final Pair<ExecutionReference, ExecutableFlow> pair =
          this.runningExecutions.get().get(exFlow.getExecutionId());
      if (pair == null) {
        throw new Exception("Execution "
            + exFlow.getExecutionId() + " of flow " + exFlow.getFlowId()
            + " isn't running.");
      }
      String url = "http://" + pair.getFirst().getExecutor().get().getHost() + ":" + pair.getFirst().getExecutor().get().getPort() + "/executor?"
          + "action=" + ConnectorParams.RETRY_FAILED_JOBS_ACTION + "&execid=" + exFlow.getExecutionId() + "&user=" + userId + "&token=" + getToken();

      return this.apiGateway.httpPost(url, request);
    }
  }

  @Override
  public String skipFailedJobs(ExecutableFlow exFlow, String userId, String request) throws Exception {
    synchronized (exFlow) {
      final Pair<ExecutionReference, ExecutableFlow> pair =
          this.runningExecutions.get().get(exFlow.getExecutionId());
      if (pair == null) {
        throw new Exception("Execution "
            + exFlow.getExecutionId() + " of flow " + exFlow.getFlowId()
            + " isn't running.");
      }
      String url = "http://" + pair.getFirst().getExecutor().get().getHost() + ":" + pair.getFirst().getExecutor().get().getPort() + "/executor?"
          + "action=" + ConnectorParams.SKIP_FAILED_JOBS_ACTION + "&execid=" + exFlow.getExecutionId() + "&user=" + userId + "&token=" + getToken();

      return this.apiGateway.httpPost(url, request);
    }
  }

  @Override
  public void pauseFlow(final ExecutableFlow exFlow, final String userId)
      throws ExecutorManagerException {
    synchronized (exFlow) {
      final Pair<ExecutionReference, ExecutableFlow> pair =
          this.runningExecutions.get().get(exFlow.getExecutionId());
      if (pair == null) {
        throw new ExecutorManagerException("Execution "
            + exFlow.getExecutionId() + " of flow " + exFlow.getFlowId()
            + " isn't running.");
      }
      this.apiGateway
          .callWithReferenceByUser(pair.getFirst(), ConnectorParams.PAUSE_ACTION, userId);
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
  public void retryFailures(final ExecutableFlow exFlow, final String userId)
      throws ExecutorManagerException {
    modifyExecutingJobs(exFlow, ConnectorParams.MODIFY_RETRY_FAILURES, userId, null);
  }

  @Override
  public void skipAllFailures(ExecutableFlow exFlow, String userId) throws ExecutorManagerException {
    synchronized (exFlow) {
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
  public void retryExecutingJobs(final ExecutableFlow exFlow, final String userId,
      final String... jobIds) throws ExecutorManagerException {
    modifyExecutingJobs(exFlow, ConnectorParams.MODIFY_RETRY_JOBS, userId,
        jobIds);
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
      final String command, final String userId,final String retryJson, final String... jobIds)
      throws ExecutorManagerException {
    synchronized (exFlow) {
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
            final ExecutableNode node = exFlow.getExecutableNode(jobId);
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

  /**
   * When a flow is submitted, insert a new execution into the database queue. {@inheritDoc}
   */
  @Override
  public String submitExecutableFlow(final ExecutableFlow exflow, final String userId)
      throws ExecutorManagerException {
    final String exFlowKey = exflow.getProjectName() + "." + exflow.getId() + ".submitFlow";
    // Use project and flow name to prevent race condition when same flow is submitted by API and
    // schedule at the same time
    // causing two same flow submission entering this piece.
    synchronized (exFlowKey.intern()) {
      final String flowId = exflow.getFlowId();
      logger.info("Submitting execution flow " + flowId + " by " + userId);

      String message = "";

      final int projectId = exflow.getProjectId();
      exflow.setSubmitUser(userId);
      List<Integer> executorIds = null;
      try {
        executorIds = this.executorLoader.getExecutorIdsBySubmitUser(exflow.getSubmitUser());
      }catch (ExecutorManagerException em){
        logger.error("get executorId by " + exflow.getSubmitUser() + ", failed", em);
        throw new ExecutorManagerException("get executorId by " + exflow.getSubmitUser() + ", failed", em);
      }
      if(executorIds == null || executorIds.size() == 0){
        logger.error("can not found executorId by " + exflow.getSubmitUser());
        throw new ExecutorManagerException("用户:" + exflow.getSubmitUser() + "，没有分配executor");
      }
      exflow.setExecutorIds(executorIds);
      exflow.setSubmitTime(System.currentTimeMillis());

      ExecutionOptions options = exflow.getExecutionOptions();
      if (options == null) {
        options = new ExecutionOptions();
      }

      if (options.getDisabledJobs() != null) {
        FlowUtils.applyDisabledJobs(options.getDisabledJobs(), exflow);
      }

      final List<Integer> running = getRunningFlows(projectId, flowId);

      if (!running.isEmpty()) {
        if (running.size() > this.maxConcurrentRunsOneFlow) {
          this.commonMetrics.markSubmitFlowSkip();
          throw new ExecutorManagerException("Flow " + flowId
              + " has more than " + this.maxConcurrentRunsOneFlow + " concurrent runs. Skipping",
              ExecutorManagerException.Reason.SkippedExecution);
        } else if (options.getConcurrentOption().equals(
            ExecutionOptions.CONCURRENT_OPTION_PIPELINE)) {
          Collections.sort(running);
          final Integer runningExecId = running.get(running.size() - 1);

          options.setPipelineExecutionId(runningExecId);
          message =
              "Flow " + flowId + " is already running with exec id "
                  + runningExecId + ". Pipelining level "
                  + options.getPipelineLevel() + ". \n";
        } else if (options.getConcurrentOption().equals(
            ExecutionOptions.CONCURRENT_OPTION_SKIP)) {
          this.commonMetrics.markSubmitFlowSkip();
          throw new ExecutorManagerException("Flow " + flowId
              + " is already running. Skipping execution.",
              ExecutorManagerException.Reason.SkippedExecution);
        } else if (exflow.getFlowType() == 6 && running.size() > 2) {
          this.commonMetrics.markSubmitFlowSkip();
          throw new ExecutorManagerException("Flow " + flowId
              + " is already running 3 task. Skipping execution.",
              ExecutorManagerException.Reason.SkippedExecution);
        } else {
          message =
              "Flow " + flowId + " is already running with exec id "
                  + StringUtils.join(running, ",")
                  + ". Will execute concurrently. \n";
        }
      }

      final boolean memoryCheck =
          !ProjectWhitelist.isProjectWhitelisted(exflow.getProjectId(),
              ProjectWhitelist.WhitelistType.MemoryCheck);
      options.setMemoryCheck(memoryCheck);

      // The exflow id is set by the loader. So it's unavailable until after
      // this call. in ha model we put it into execution_flows and return exec_id
      this.executorLoader.uploadExecutableFlow(exflow);

      // We create an active flow reference in the datastore. If the upload
      // fails, we remove the reference.
      final ExecutionReference reference =
          new ExecutionReference(exflow.getExecutionId());
      this.executorLoader.addActiveExecutableReference(reference);

      //if it is ha mode,we insert queuedFlow into db
      this.executorQueueLoader.insertExecutableQueue(exflow);

      this.commonMetrics.markSubmitFlowSuccess();
      message += "Execution DB queued successfully with exec id " + exflow.getExecutionId();
      logger.info(message);
      return message;
    }
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
   * @see ExecutorManagerAdapter#callExecutorStats(int, String,
   * Pair[])
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
      this.apiGateway.callWithExecutable(exflow, choosenExecutor,
          ConnectorParams.EXECUTE_ACTION);
    } catch (final ExecutorManagerException ex) {
      logger.error("Rolling back executor assignment for execution id:"
          + exflow.getExecutionId(), ex);
      this.executorLoader.unassignExecutor(exflow.getExecutionId());
      throw new ExecutorManagerException(ex);
    }
    reference.setExecutor(choosenExecutor);

    // move from flow to running flows
    HttpUtils
        .reloadWebData(this.azkProps.getStringList("azkaban.all.web.url"), "runningExecutions", "");
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

  /*
   * cleaner thread to clean up execution_logs, etc in DB. Runs every hour.
   */
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
            DBTableDistributeLock dd = new DBTableDistributeLock(dbOperator);
            boolean lockFlag = dd.lock(RECOVER_LOCK_KEY, azkProps.getLong(ConfigurationKeys.DISTRIBUTELOCK_LOCK_TIMEOUT, 30000),
                azkProps.getLong(ConfigurationKeys.DISTRIBUTELOCK_GET_TIMEOUT, 60000));
            if (lockFlag) {
              try {
                historyRecoverHandle();
              } catch (Exception e) {
                throw e;
              } finally {
                try {
                  dd.unlock(RECOVER_LOCK_KEY);
                  logger.debug("unlock successfully");
                } catch (RuntimeException e) {
                  logger.info("unlock failed ", e);
                }
              }
            } else {
              logger.info("historyRecoverHandle step is running in another webserver , lock_resource is {} " + RECOVER_LOCK_KEY);
            }
            Constants.HISTORY_RERUN_LOCK.wait(this.waitTime);
          } catch (final Exception e) {
            ExecutorManagerHA.logger.info("Recover-Thread interrupted. Probably to shut down.", e);
          }
        }
      }
    }

    private void historyRecoverHandle() {

      for(RecoverTrigger trigger: executorLoader.fetchHistoryRecoverTriggers()){

        ExecutorManagerHA.logger.info("trigger info : " + trigger.toString());
        trigger.setExecutionRecoverStartTime();
        trigger.updateTaskStatus();
        Project project = ExecutorManagerHA.this.projectLoader.fetchProjectById(trigger.getProjectId());
        trigger.setProject(project);
        if(!trigger.expireConditionMet()) {
          loadAllProjectFlows(project);
          Flow flow = project.getFlow(trigger.getFlowId());
          for (GroupTask groupTask : trigger.getGroup()) {
            Map<String, String> task = groupTask.nextTask();
            if (task != null) {
              ExecutableFlow exflow = new ExecutableFlow(project, flow);
              if(trigger.getExecutionRecover().getLastExecId() != -1) {
                try {
                  logger.info("get last executable flow, execId: {}", trigger.getExecutionRecover().getLastExecId());
                  ExecutableFlow lastFlow = ExecutorManagerHA.this.getExecutableFlow(trigger.getExecutionRecover().getLastExecId());
                  FlowUtils.compareAndCopyFlow(exflow, lastFlow);
                } catch (Exception e){
                  logger.error("get executable flow failed", e);
                }
              }
              submitRecoverFlow(exflow, project, trigger.getExecutionRecover(), task);
              updateRecoverFlow(exflow, trigger.getExecutionRecover(), task);
            }
          }
        }
        updateHistoryRecover(trigger.getExecutionRecover());
      }
    }


    private String submitRecoverFlow(ExecutableFlow exflow, Project project, ExecutionRecover recover, Map<String, String> item){
      exflow.setSubmitUser(recover.getSubmitUser());
      //获取项目默认代理用户
      Set<String> proxyUserSet = project.getProxyUsers();
      //设置用户代理用户
      proxyUserSet.add(recover.getSubmitUser());
      if (recover.getProxyUsers() != null && !"[]".equals(recover.getProxyUsers())) {
        List<String> proxyUsers = Arrays.asList(recover.getProxyUsers().replaceAll("\\s*", "").replace("[", "").replace("]", "").split(","));
        proxyUserSet.addAll(proxyUsers);
      } else {
        ExecutorManagerHA.logger.error("recover proxyUsers is null");
      }
      //设置当前登录的用户的代理用户
      exflow.addAllProxyUsers(proxyUserSet);
      exflow.setExecutionOptions(recover.getExecutionOptions());
      exflow.setOtherOption(recover.getOtherOption());
      if(recover.getOtherOption().get("flowFailedRetryOption") != null){
        exflow.setFlowFailedRetry((Map<String, String>) recover.getOtherOption().get("flowFailedRetryOption"));
      }
      // 设置失败跳过所有job
      exflow.setFailedSkipedAllJobs((Boolean) recover.getOtherOption().getOrDefault("flowFailedSkiped", false));
      //超时告警设置
      if(recover.getSlaOptions() != null) {
        exflow.setSlaOptions(recover.getSlaOptions());
      }

      //设置数据补采参数
      exflow.setRepeatOption(item);
      //设置Flow类型为数据补采
      exflow.setFlowType(2);
      String message = "";
      exflow.setRepeatId(recover.getRecoverId());
      try {
        message = ExecutorManagerHA.this.submitExecutableFlow(exflow, recover.getSubmitUser());
      } catch (ExecutorManagerException ex){
        ExecutorManagerHA.logger.error("submit recover flow failed. ", ex);
      }
      return message;
    }

    private void updateRecoverFlow(ExecutableFlow exflow, ExecutionRecover recover, Map<String, String> item){
      //提交成功
      if(exflow.getExecutionId() != -1){
        recover.setNowExecutionId(exflow.getExecutionId());
        item.put("isSubmit", "true");
        item.put("exeId", String.valueOf(exflow.getExecutionId()));
      }else{
        ExecutorManagerHA.logger.error("submit recover flow failed. ");
        item.put("exeId", "-1");
      }
    }

    private void updateHistoryRecover(ExecutionRecover recover){
      try {
        ExecutorManagerHA.this.updateHistoryRecover(recover);
      }catch (ExecutorManagerException executorManager){
        ExecutorManagerHA.logger.error("更新历史重跑任务信息失败, " + executorManager);
      }
    }

    private void loadAllProjectFlows(final Project project) {
      try {
        final List<Flow> flows = ExecutorManagerHA.this.projectLoader.fetchAllProjectFlows(project);
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
            ExecutorManagerHA.logger.info("Interrupted. Probably to shut down.");
          }
        }
      }
    }

    private void cleanExecutionLogs() {
      DBTableDistributeLock dd = new DBTableDistributeLock(dbOperator);
      boolean lockFlag = dd.lock(CLEAN_LOG_LOCK_KEY, azkProps.getLong(ConfigurationKeys.DISTRIBUTELOCK_LOCK_TIMEOUT, 30000),
          azkProps.getLong(ConfigurationKeys.DISTRIBUTELOCK_GET_TIMEOUT, 60000));
      if (lockFlag) {
        try {
          ExecutorManagerHA.logger.info("Cleaning old logs from execution_logs");
          final long cutoff = System.currentTimeMillis() - this.executionLogsRetentionMs;
          ExecutorManagerHA.logger.info("Cleaning old log files before "
              + new DateTime(cutoff).toString());
          cleanOldExecutionLogs(System.currentTimeMillis()
              - this.executionLogsRetentionMs);
        } catch (Exception e) {
          throw e;
        } finally {
          try {
            dd.unlock(CLEAN_LOG_LOCK_KEY);
            logger.debug("unlock successfully");
          } catch (RuntimeException e) {
            logger.info("unlock failed ", e);
          }
        }

      } else {
        logger.info("cleanExecutionLogs step is running in another webserver , lock_resource is {} " + CLEAN_LOG_LOCK_KEY);
      }

    }
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

    public QueueProcessorThread(final boolean isActive,
        final long activeExecutorRefreshWindowInTime,
        final int activeExecutorRefreshWindowInFlows,
        final int maxDispatchingErrors,
        final Duration sleepAfterDispatchFailure) {
      setActive(isActive);
      this.maxDispatchingErrors = maxDispatchingErrors;
      this.activeExecutorRefreshWindowInFlows =
          activeExecutorRefreshWindowInFlows;
      this.activeExecutorRefreshWindowInMillisec =
          activeExecutorRefreshWindowInTime;
      this.sleepAfterDispatchFailure = sleepAfterDispatchFailure;
      this.setName("AzkabanWebServer-QueueProcessor-Thread-HA");
    }

    public boolean isActive() {
      return this.isActive;
    }

    public void setActive(final boolean isActive) {
      this.isActive = isActive;
      ExecutorManagerHA.logger.info("QueueProcessorThreadHA active turned " + this.isActive);
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
            ExecutorManagerHA.logger.error(
                "QueueProcessorThread Interrupted. Probably to shut down.", e);
          }
        }
      }
    }

    /**
     *
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

//      this.executorQueueLoader.insertExecutableQueue(exflow);

      QueuedExecutions queuedFlows = null;
      try {
        queuedFlows = loadQueuedFlows();
        logger.debug("queuedFlows size is {} " + queuedFlows.size());
      } catch (ExecutorManagerException e) {
        logger.error("getActiveFlowsWithExecutor loadQueuedFlows failed",e);
      }

      if(queuedFlows.size() == 0 ){
        return;
      }
      HttpUtils.reloadWebData(ExecutorManagerHA.this.azkProps.getStringList("azkaban.all.web.url"), "runningExecutions", "");

      while (isActive() && (ExecutorManagerHA.this.runningCandidate = queuedFlows
          .fetchHeadPoll()) != null) {
        final ExecutionReference reference = ExecutorManagerHA.this.runningCandidate.getFirst();
        final ExecutableFlow exflow = ExecutorManagerHA.this.runningCandidate.getSecond();
        String lock_resource = exflow.getExecutionId() + ":" + exflow.getProjectId() + ":" + exflow.getId();
        final long currentTime = System.currentTimeMillis();

        //
        DBTableDistributeLock dd = new DBTableDistributeLock(dbOperator);
        boolean lockFlag = dd.lock(lock_resource, azkProps.getLong(ConfigurationKeys.DISTRIBUTELOCK_LOCK_TIMEOUT, 30000),
            azkProps.getLong(ConfigurationKeys.DISTRIBUTELOCK_GET_TIMEOUT, 60000));
        if (lockFlag) {
          try {
            logger.info("request a new distributeLock successfully , lock_resource is {} " + lock_resource);

            /**
             * if we have dispatched more than maxContinuousFlowProcessed or
             * It has been more then activeExecutorsRefreshWindow millisec since we
             * refreshed
             */
            if (currentTime - lastExecutorRefreshTime > activeExecutorsRefreshWindow
                || currentContinuousFlowProcessed >= maxContinuousFlowProcessed) {
              // Refresh executorInfo for all activeExecutors
              refreshExecutors();
              logger.debug("refreshExecutors success");
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
              //          queuedFlows.enqueue(exflow, reference);
              ExecutorManagerHA.this.runningCandidate = null;
              final long sleepInterval =
                  activeExecutorsRefreshWindow
                      - (currentTime - lastExecutorRefreshTime);
              // wait till next executor refresh
              Thread.sleep(sleepInterval);
              logger.debug("the flow updatetime is greater than lastExecutorRefreshTime");
            } else {
              exflow.setUpdateTime(currentTime);
              // process flow with current snapshot of activeExecutors
              selectExecutorAndDispatchFlow(reference, exflow);
              ExecutorManagerHA.this.runningCandidate = null;
              logger.debug("selectExecutorAndDispatchFlow success");
            }

            // do not count failed flow processsing (flows still in queue)
            if (queuedFlows.getFlow(exflow.getExecutionId()) == null) {
              currentContinuousFlowProcessed++;
            }
          } catch (Exception e) {
            logger.error(e.getMessage(), e);
            throw e;
          } finally {
            //执行完成后解锁
            try {
              dd.unlock(lock_resource);
              logger.debug("unlock successfully");
            } catch (RuntimeException e) {
              logger.info("unlock failed ", e);
            }
          }

        }else{
          logger.info("processQueuedFlows step is running in another webserver , lock_resource is {} " + lock_resource);
        }
      }
    }

    /* process flow with a snapshot of available Executors */
    private void selectExecutorAndDispatchFlow(final ExecutionReference reference,
        final ExecutableFlow exflow)
        throws ExecutorManagerException {
      // 过滤出该flow分配的executor
      final Set<Executor> flowExecutors = ExecutorManagerHA.this.activeExecutors.getAll().stream().filter(executor -> exflow.getExecutorIds().contains(executor.getId())).collect(Collectors.toSet());
      final Set<Executor> remainingExecutors = new HashSet<>(flowExecutors);
      ExecutorManagerHA.logger.info("execId: " + exflow.getExecutionId() + ", executors: " + remainingExecutors.toString());
      Throwable lastError;
      synchronized (exflow) {
        do {
          final Executor selectedExecutor = selectExecutor(exflow, remainingExecutors);
          if (selectedExecutor == null) {
            ExecutorManagerHA.this.commonMetrics.markDispatchFail();
            handleNoExecutorSelectedCase(reference, exflow);
            // RE-QUEUED - exit
            return;
          } else {
            try {
              dispatch(reference, exflow, selectedExecutor);
              ExecutorManagerHA.this.commonMetrics.markDispatchSuccess();
              // SUCCESS - exit
              return;
            } catch (final ExecutorManagerException e) {
              lastError = e;
              logFailedDispatchAttempt(reference, exflow, selectedExecutor, e);
              ExecutorManagerHA.this.commonMetrics.markDispatchFail();
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
        ExecutorManagerHA.logger.error(message);
        ExecutorManagerHA.this.executionFinalizer.finalizeFlow(exflow, message, lastError);  // 通用告警
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
        ExecutorManagerHA.logger.warn("Sleep after dispatch failure was interrupted - ignoring");
      }
    }

    private void logFailedDispatchAttempt(final ExecutionReference reference,
        final ExecutableFlow exflow,
        final Executor selectedExecutor, final ExecutorManagerException e) {
      ExecutorManagerHA.logger.warn(String.format(
          "Executor %s responded with exception for exec: %d",
          selectedExecutor, exflow.getExecutionId()), e);
      ExecutorManagerHA.logger.info(String.format(
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
            ExecutorManagerHA.logger
                .warn(String
                    .format(
                        "User specified executor id: %d for execution id: %d is not active, Looking up db.",
                        executorId, executionId));
            executor = ExecutorManagerHA.this.executorLoader.fetchExecutor(executorId);
            if (executor == null) {
              ExecutorManagerHA.logger
                  .warn(String
                      .format(
                          "User specified executor id: %d for execution id: %d is missing from db. Defaulting to availableExecutors",
                          executorId, executionId));
            }
          }
        } catch (final ExecutorManagerException ex) {
          ExecutorManagerHA.logger.error("Failed to fetch user specified executor for exec_id = "
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
        final ExecutorSelector selector = new ExecutorSelector(ExecutorManagerHA.this.filterList,
            ExecutorManagerHA.this.comparatorWeightsMap);
        choosenExecutor = selector.getBest(availableExecutors, exflow);
        ExecutorManagerHA.logger.info("Using dispatcher for execution id :"
            + exflow.getExecutionId() + ", use executor: " + choosenExecutor);
      }
      return choosenExecutor;
    }


    private void handleNoExecutorSelectedCase(final ExecutionReference reference,
        final ExecutableFlow exflow) throws ExecutorManagerException {
      QueuedExecutions queuedFlows = null;
      try {
        queuedFlows = loadQueuedFlows();
      } catch (ExecutorManagerException e) {
        logger.error("getActiveFlowsWithExecutor loadQueuedFlows failed",e);
      }
      ExecutorManagerHA.logger
          .info(String
              .format(
                  "Reached handleNoExecutorSelectedCase stage for exec %d with error count %d",
                  exflow.getExecutionId(), reference.getNumErrors()));
      // TODO: handle scenario where a high priority flow failing to get
      // schedule can starve all others
      queuedFlows.enqueue(exflow, reference);
    }
  }




  /**
   *
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
  public List<ExecutableFlow> getUserExecutableFlowsByAdvanceFilter(String projContain, String flowContain, String execIdContain,
      String userContain, String status, long begin, long end,
      int skip, int size, int flowType) throws ExecutorManagerException {
    List<ExecutableFlow> flows =
        executorLoader.fetchUserFlowHistoryByAdvanceFilter(projContain, flowContain, execIdContain,
            userContain, status, begin, end, skip, size, flowType);

    return flows;
  }

  /**
   * 根据 flow Id 查找用户的 flow 历史信息
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
    if(!flows.isEmpty()){
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
      if(2 == exFlow.getFlowType() && !Status.FAILED.equals(exFlow.getStatus())){
        exFlow.setFlowType(3);//数据补采状态设置为这终止
      }else if(2 == exFlow.getFlowType() && Status.FAILED.equals(exFlow.getStatus())){
        exFlow.setFlowType(5);//数据补采状态设置为这失败终止
      }
      this.executorLoader.updateExecutableFlow(exFlow);
    } catch (ExecutorManagerException e) {
      e.printStackTrace();
    }
  }

  @Override
  public ExecutableFlow getHistoryRecoverExecutableFlowsByFlowId(final String flowId, final String projectId)
      throws ExecutorManagerException {
    List<ExecutableFlow> flows = executorLoader.fetchHistoryRecoverFlowByFlowId(flowId, projectId);
    if(flows.isEmpty()){
      return null;
    }else {
      return flows.get(0);
    }
  }

  @Override
  public List<ExecutionRecover> listHistoryRecoverFlows(final Map paramMap,
      int skip, int size) throws ExecutorManagerException{
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
      throws ExecutorManagerException{
    return executorLoader.saveHistoryRecoverFlow(executionRecover);
  }

  @Override
  public void updateHistoryRecover(final ExecutionRecover executionRecover)
      throws ExecutorManagerException{

    try {
      executionRecover.setUpdateTime(System.currentTimeMillis());
      executorLoader.updateHistoryRecover(executionRecover);
    } catch (ExecutorManagerException e) {
      e.printStackTrace();
    }
  }

  @Override
  public ExecutionRecover getHistoryRecoverFlow(final Integer recoverId)
      throws ExecutorManagerException{

    ExecutionRecover executionRecover = executorLoader.getHistoryRecoverFlow(recoverId);

    return executionRecover;
  }

  /**
   * 根据项目ID和工作流ID 查找正在运行的历史补采
   * @param projectId
   * @param flowId
   * @return
   * @throws ExecutorManagerException
   */
  @Override
  public ExecutionRecover getHistoryRecoverFlowByPidAndFid(final String projectId, final String flowId)
      throws ExecutorManagerException{
    ExecutionRecover executionRecover = executorLoader.getHistoryRecoverFlowByPidAndFid(projectId, flowId);

    return executionRecover;
  }


  @Override
  public List<ExecutionRecover> listHistoryRecoverRunnning(final Integer loadSize)
      throws ExecutorManagerException{
    List<ExecutionRecover> flows = executorLoader.listHistoryRecoverRunnning(loadSize);
    return flows;
  }

  @Override
  public int getHistoryRecoverTotal() throws ExecutorManagerException{

    return executorLoader.getHistoryRecoverTotal();
  }

  @Override
  public ExecutableFlow getProjectLastExecutableFlow(int projectId, String flowId) throws ExecutorManagerException{
    ExecutableFlow flow = executorLoader.getProjectLastExecutableFlow(projectId, flowId);
    return flow;
  }

  @Override
  public int getMaintainedHistoryRecoverTotal(String username, List<Integer> maintainedProjectIds) throws ExecutorManagerException {
    return executorLoader.getMaintainedHistoryRecoverTotal(username, maintainedProjectIds);
  }

  @Override
  public int getUserHistoryRecoverTotal(String userName) throws ExecutorManagerException{

    return executorLoader.getUserRecoverHistoryTotal(userName);
  }

  @Override
  public int getExecutionCycleTotal(Optional<String> usernameOp) throws ExecutorManagerException {
    return executorLoader.getExecutionCycleTotal(usernameOp);
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
  public ExecutionCycle getExecutionCycleFlow(int id) throws ExecutorManagerException {
    return executorLoader.getExecutionCycleFlow(id);
  }

  @Override
  public int updateExecutionFlow(ExecutionCycle executionCycle) throws ExecutorManagerException {
    return executorLoader.updateExecutionFlow(executionCycle);
  }

  @Override
  public int stopAllCycleFlows() throws ExecutorManagerException {
//    return executorLoader.stopAllCycleFlows();
    return 0;
  }

  @Override
  public List<ExecutionCycle> getAllRunningCycleFlows() throws ExecutorManagerException {
    return executorLoader.getAllRunningCycleFlows();
  }

  @Override
  public void reloadWebData() {
    try {
      this.loadRunningExecutions();
    } catch (Exception e) {
      logger.error("reload web data in ha error", e);
    }

  }

  /**
   * 获取日志压缩包路径
   * 根据 执行ID 查找到所有日志分段，并组合起来。打包成Zip包。返回路径
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
      if(flowLogDir.exists()){
        //先删除目录
        flowLogDir.delete();
        //再创建文件夹
        flowLogDir.mkdir();
      }else{
        //不存在就直接创建文件夹
        flowLogDir.mkdir();
      }

      for(String jobName : nameList){
        //获取日志文本
        LogData value = executorLoader.fetchAllLogs(executableFlow.getExecutionId(), jobName, 0);
        if (null != value){
          //创建日志文件
          final File file = new File(flowLogDir + File.separator + jobName + ".log");
          //把日志文本写入到日志文件中
          FileWrite(file.getPath(), file.getName(), value.getData());
        }
      }

      //把日志文件夹到包成Zip包
      logZipFilePath =
          fileToZip(flowLogDir.getPath(), new File("temp").getPath(), flowLogDir.getName());

    } catch (Exception e) {
      logger.error("下载所有日志数据失败, 原因为:" + e);
    }

    return logZipFilePath;

  }

  public static String fileToZip(String sourceFilePath, String zipFilePath, String fileName){
    boolean flag = false;
    File sourceFile = new File(sourceFilePath);
    FileInputStream fis = null;
    BufferedInputStream bis = null;
    FileOutputStream fos = null;
    ZipOutputStream zos = null;

    File zipFile = new File(zipFilePath + "/" + fileName +".zip");
    if(sourceFile.exists() == false){
      System.out.println("待压缩的文件目录："+sourceFilePath+"不存在.");
    }else{
      try {
        //zipFile = File.createTempFile(fileName, ".zip", new File("temp"));
        if(zipFile.exists()){
          System.out.println(zipFilePath + "目录下存在名字为:" + fileName +".zip" +"打包文件.");
        }else{
          File[] sourceFiles = sourceFile.listFiles();
          if(null == sourceFiles || sourceFiles.length<1){
            System.out.println("待压缩的文件目录：" + sourceFilePath + "里面不存在文件，无需压缩.");
          }else{
            fos = new FileOutputStream(zipFile);
            zos = new ZipOutputStream(new BufferedOutputStream(fos));
            byte[] bufs = new byte[1024*10];
            for(int i=0;i<sourceFiles.length;i++){
              //创建ZIP实体，并添加进压缩包
              ZipEntry zipEntry = new ZipEntry(sourceFiles[i].getName());
              zos.putNextEntry(zipEntry);
              //读取待压缩的文件并写进压缩包里
              fis = new FileInputStream(sourceFiles[i]);
              bis = new BufferedInputStream(fis, 1024*10);
              int read = 0;
              while((read=bis.read(bufs, 0, 1024*10)) != -1){
                zos.write(bufs,0,read);
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
      } finally{
        //关闭流
        try {
          if(null != bis) {
            bis.close();
          }
          if(null != zos) {
            zos.close();
          }
          if (null != fis) {
            fis.close();
          }
          if (null != fos) {
            fos.close();
          }
        } catch (IOException e) {
          logger.error("close io stream failed, caused by:" + e);
        }
      }
    }
    return zipFile.getPath();
  }

  private Map<String, Object> getExecutableNodeInfo(final ExecutableNode node, final List<String> nameList) {
    final HashMap<String, Object> nodeObj = new HashMap<>();
    nodeObj.put("id", node.getId());

    if(null != node.getParentFlow()){
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

  public static void FileWrite(String allFilePath, String fileName, String fileStr) {

    FileWriter fw = null;
    try {
      fw = new FileWriter(allFilePath);
      //写入到文件
      fw.write(fileStr);
    } catch (IOException e) {
      e.printStackTrace();
    } finally {
      if (fw != null) {
        try {
          fw.close();
        } catch (IOException e) {
          logger.error("close io stream failed, caused by:" + e);
        }
      }
    }
  }

  @Override
  public String getJobLogByJobId(int execId, String jobName) throws ExecutorManagerException{
    String logZipFilePath = "";

    try {
      //获取日志文件夹
      File flowLogDir = new File("temp" + File.separator + jobName + System.currentTimeMillis());
      //如果目录存在
      if(flowLogDir.exists()){
        //先删除目录
        flowLogDir.delete();
        //再创建文件夹
        flowLogDir.mkdir();
      }else{
        //不存在就直接创建文件夹
        flowLogDir.mkdir();
      }
      //获取Job日志文本
      LogData value = executorLoader.fetchAllLogs(execId, jobName, 0);
      if (null != value){
        //创建日志文件
        final File file = new File(flowLogDir + File.separator + jobName + ".txt");
        //把日志文本写入到日志文件中
        FileWrite(file.getPath(), file.getName(), value.getData());

        logZipFilePath = file.getPath();
      }

      //把日志文件夹到包成Zip包
//      logZipFilePath =
//          fileToZip(flowLogDir.getPath(), new File("temp").getPath(), flowLogDir.getName());

    } catch (Exception e) {
      logger.error("getJobLogByJobId execute failed, caused by:" + e);
    }

    return logZipFilePath;
  }


  /**
   *
   *
   * @param exFlow
   * @param jobId Job Id
   * @param attempt
   * @return
   * @throws ExecutorManagerException
   */
  @Override
  public String getAllExecutionJobLog(ExecutableFlow exFlow, String jobId, int attempt) throws ExecutorManagerException {
    Pair<ExecutionReference, ExecutableFlow> pair =
        runningFlows.get(exFlow.getExecutionId());

    StringBuilder allLogData = new StringBuilder();

    int offset = 0;
    int length = 50000;
    //
    while (true){
      LogData value =
          executorLoader.fetchLogs(exFlow.getExecutionId(), jobId, attempt, offset, length);
      if(null == value){
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
   * @return
   * @throws ExecutorManagerException
   */
  @Override
  public List<LogFilterEntity> listAllLogFilter() throws ExecutorManagerException {

    return executorLoader.listAllLogFilter();

  }

  @Override
  public int getExecHistoryTotal(final Map<String, String> filterMap) throws ExecutorManagerException{

    return executorLoader.getExecHistoryTotal(filterMap);
  }

  @Override
  public int getExecHistoryTotal(String username, final Map<String, String> filterMap, List<Integer> projectIds)
      throws ExecutorManagerException {
    return executorLoader.getExecHistoryTotal(username, filterMap, projectIds);
  }

  @Override
  public int getMaintainedExecHistoryTotal(String username, List<Integer> projectIds)
      throws ExecutorManagerException {
    return executorLoader.getMaintainedExecHistoryTotal(username, projectIds);
  }

  @Override
  public int getExecHistoryQuickSerachTotal(final Map<String, String> filterMap) throws ExecutorManagerException{

    return executorLoader.getExecHistoryQuickSerachTotal(filterMap);
  }

  @Override
  public int getMaintainedFlowsQuickSearchTotal(String username, final Map<String, String> filterMap, List<Integer> projectIds)
      throws ExecutorManagerException {
    return executorLoader.getMaintainedFlowsQuickSearchTotal(username, filterMap, projectIds);
  }


  @Override
  public List<ExecutableFlow> getMaintainedFlowsQuickSearch(String flowIdContains, int skip, int size, String username, List<Integer> projectIds) throws ExecutorManagerException {
    return this.executorLoader.fetchFlowHistoryQuickSearch('%' + flowIdContains + '%', username, skip, size, projectIds);
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
      throws ExecutorManagerException{

    final List<ExecutableFlow> exFlows =
        this.executorLoader.fetchFlowAllHistory(projectId, flowId, user);

    long moyenne = 0;
    long allRunTime = 0;
    int successFlowNum = 0;
    for (final ExecutableFlow flow : exFlows) {
      if(Status.SUCCEEDED.equals(flow.getStatus())){
        successFlowNum += 1;
        allRunTime += (flow.getEndTime() - flow.getStartTime());
      }
    }
    if(allRunTime !=0 && successFlowNum !=0){
      moyenne = allRunTime/successFlowNum;
    }

    return moyenne;
  }

  @Override
  public int getUserExecHistoryTotal(final Map<String, String> filterMap) throws ExecutorManagerException{

    return executorLoader.getUserExecHistoryTotal(filterMap);
  }

  @Override
  public int getUserExecHistoryQuickSerachTotal(final Map<String, String> filterMap) throws ExecutorManagerException{

    return executorLoader.getUserExecHistoryQuickSerachTotal(filterMap);
  }

  /**
   * 根据条件查找用户的 flow 历史信息
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
      String flowContain, String execIdContain, String userContain, String status, long begin, long end,
      int skip, int size, int flowType) throws ExecutorManagerException {
    List<ExecutableFlow> flows =
        executorLoader.fetchUserFlowHistory(loginUser, projContain, flowContain, execIdContain, userContain,
            status, begin, end, skip, size, flowType);

    return flows;
  }

  @Override
  public List<ExecutableFlow> getTodayExecutableFlowData(final String userName) throws ExecutorManagerException{
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
  public List<ExecutableFlow> getRealTimeExecFlowData(final String userName) throws ExecutorManagerException{
    List<ExecutableFlow> flows =
        executorLoader.getRealTimeExecFlowData(userName);

    return flows;
  }

  @Override
  public ExecutableFlow getRecentExecutableFlow(int projectId, String flowId) throws ExecutorManagerException{
    ExecutableFlow flow = executorLoader.getProjectLastExecutableFlow(projectId, flowId);
    return flow;
  }

  @Override
  public List<Map<String, String>> getExectingFlowsData() throws IOException {

    List<ExecutableFlow> flows = new ArrayList<>();
    try {
      flows = this.executorLoader.fetchAllUnfinishedFlows();
    } catch (final ExecutorManagerException e) {
      logger.error("Failed to get active flows with executor.", e);
    }
    final List<Map<String, String>> exectingFlowList = new ArrayList<>();

    WebUtils webUtils = new WebUtils();
    if(null != flows && !flows.isEmpty()){
      for(ExecutableFlow executableFlow : flows){
        Map<String, String> repeatMap = executableFlow.getRepeatOption();
        if(!repeatMap.isEmpty()){
          Long recoverRunDate = Long.valueOf(String.valueOf(repeatMap.get("startTimeLong")));
          LocalDateTime localDateTime = new LocalDateTime(new Date(recoverRunDate)).minusDays(1);
          Date date = localDateTime.toDate();
          executableFlow.setUpdateTime(date.getTime());
        }else{
          String runDatestr = executableFlow.getExecutionOptions().getFlowParameters().get("run_date");
          Object runDateOther = executableFlow.getOtherOption().get("run_date");
          if(runDatestr!=null&&!"".equals(runDatestr)&&!runDatestr.isEmpty()){
            try {
              executableFlow.setUpdateTime(Long.parseLong(runDatestr));
            } catch (Exception e) {
              logger.error("rundate convert failed (String to long)" + runDatestr + "{}"+e);
            }finally {
              executableFlow.setUpdateTime(0);
              executableFlow.getOtherOption().put("run_date",runDatestr);
            }
          }else if(runDateOther!=null&&!"".equals(runDateOther.toString())&&!runDateOther.toString().isEmpty()){
            String runDateTime = (String) runDateOther;
            runDateTime = runDateTime.replaceAll("\'","").replaceAll("\"","");
            if(SystemBuiltInParamJodeTimeUtils.dateFormatCheck(runDateTime)){
              executableFlow.setUpdateTime(0);
              runDateTime = runDateTime.replaceAll("[./-]", "");
              executableFlow.getOtherOption().put("run_date", runDateTime);
            } else {
              if(-1 != executableFlow.getStartTime()) {
                LocalDateTime localDateTime = new LocalDateTime(new Date(executableFlow.getStartTime())).minusDays(1);
                Date date = localDateTime.toDate();
                executableFlow.setUpdateTime(date.getTime());
              }
            }
          }else{
            Long runDate = executableFlow.getStartTime();
            if(-1 != executableFlow.getStartTime()) {
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
          String executorId = (String) executableFlow.getOtherOption().get("currentExecutorId") != null ? (String)executableFlow.getOtherOption().get("currentExecutorId") : "";
          exectingMap.put("exectorId", executorId);
          exectingMap.put("flowName", executableFlow.getFlowId());
          exectingMap.put("projectName", executableFlow.getProjectName());
          exectingMap.put("submitUser", executableFlow.getSubmitUser());
          exectingMap.put("proxyUsers", executableFlow.getProxyUsers().toString());
          exectingMap.put("startTime", webUtils.formatHistoryDateTime(executableFlow.getStartTime()));
          //exectingMap.put("endTime", executableFlow.getEndTime());
          if(executableFlow.getOtherOption().get("run_date")!=null){
            exectingMap.put("runDate", executableFlow.getUpdateTime()==0?executableFlow.getOtherOption().get("run_date").toString():webUtils.formatRunDate(executableFlow.getUpdateTime()));
          }else{
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
          throw new RuntimeException("generate executingMap failed"+e);
        }
      }
    }

    return exectingFlowList;
  }


  @Override
  public List<Integer> fetchPermissionsProjectId(String user) {
    return this.projectLoader.fetchPermissionsProjectId(user);
  }
}
