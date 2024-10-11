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

import static azkaban.ServiceProvider.SERVICE_PROVIDER;

import azkaban.Constants.JobProperties;
import azkaban.event.Event;
import azkaban.event.EventData;
import azkaban.event.EventHandler;
import azkaban.execapp.event.BlockingStatus;
import azkaban.execapp.event.FlowWatcher;
import azkaban.executor.ExecutableFlowBase;
import azkaban.executor.ExecutableNode;
import azkaban.executor.ExecutorLoader;
import azkaban.executor.ExecutorManagerException;
import azkaban.executor.Status;
import azkaban.flow.CommonJobProperties;
import azkaban.jobExecutor.AbstractProcessJob;
import azkaban.jobExecutor.JavaProcessJob;
import azkaban.jobExecutor.Job;
import azkaban.jobid.relation.JobIdRelation;
import azkaban.jobid.relation.JobIdRelationService;
import azkaban.jobtype.JobTypeManager;
import azkaban.jobtype.JobTypeManagerException;
import azkaban.spi.EventType;
import azkaban.utils.ExternalLinkUtils;
import azkaban.utils.Props;
import azkaban.utils.StringUtils;
import com.webank.wedatasphere.schedulis.common.log.LogFilterEntity;
import com.webank.wedatasphere.schedulis.common.log.OperateType;
import com.webank.wedatasphere.schedulis.common.utils.LogUtils;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.FilenameFilter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.slf4j.LoggerFactory;

public class JobRunner extends EventHandler implements Runnable {

  public static final Pattern APPLICATION_JOB_ID_PATTERN = Pattern.compile("(application|job)_\\d+_\\d+");
  public static final String AZKABAN_WEBSERVER_URL = "azkaban.webserver.url";

  public static final String JOB_FAILED_RETRY_COUNT = "job.failed.retry.count";
  public static final String JOB_FAILED_RETRY_INTERVAL = "job.failed.retry.interval";

  private static final org.slf4j.Logger serverLogger = LoggerFactory.getLogger(JobRunner.class);
  private static final Object logCreatorLock = new Object();

  private final Object syncObject = new Object();
  private final JobTypeManager jobtypeManager;
  private final ExecutorLoader loader;
  private final Props props;
  private final Props azkabanProps;
  private final ExecutableNode node;
  private final File workingDir;
  private final String jobId;
  private final Set<String> pipelineJobs = new HashSet<>();
  private org.slf4j.Logger logger = null;
  private org.slf4j.Logger flowLogger = null;
  private File logFile;
  private String attachmentFileName;
  private Job job;
  private int executionId = -1;
  // Used by the job to watch and block against another flow
  private Integer pipelineLevel = null;
  private FlowWatcher watcher = null;
  private Set<String> proxyUsers = null;

  private String jobLogChunkSize;
  private int jobLogBackupIndex;

  private long delayStartMs = 0;
  private volatile boolean killed = false;
  private BlockingStatus currentBlockStatus = null;

  //job运行过程中需要跳过此job
  private volatile boolean skipped = false;
  private boolean restartFailed = false;

  private boolean restartFaildOpen = false;
  private int retryConut = 0;

  private String loggerName;
  private String logFileName;

  public JobRunner(final ExecutableNode node, final File workingDir, final ExecutorLoader loader,
      final JobTypeManager jobtypeManager, final Props azkabanProps) {
    this.props = node.getInputProps();
    this.node = node;
    this.workingDir = workingDir;

    this.executionId = node.getParentFlow().getExecutionId();
    this.jobId = node.getId();
    this.loader = loader;
    this.jobtypeManager = jobtypeManager;
    this.azkabanProps = azkabanProps;
//    final String jobLogLayout = props.getString(
//        JobProperties.JOB_LOG_LAYOUT, DEFAULT_LAYOUT);

  }

  public static String createLogFileName(final ExecutableNode node, final int attempt) {
    final int executionId = node.getExecutableFlow().getExecutionId();
    String jobId = node.getId();
    if (node.getExecutableFlow() != node.getParentFlow()) {
      // Posix safe file delimiter
      jobId = node.getPrintableId("._.");
    }
    return attempt > 0 ? "_job." + executionId + "." + attempt + "." + jobId
        + ".log" : "_job." + executionId + "." + jobId + ".log";
  }

  public static String createLogFileName(final ExecutableNode node) {
    return JobRunner.createLogFileName(node, node.getAttempt());
  }

  public static String createMetaDataFileName(final ExecutableNode node, final int attempt) {
    final int executionId = node.getExecutableFlow().getExecutionId();
    String jobId = node.getId();
    if (node.getExecutableFlow() != node.getParentFlow()) {
      // Posix safe file delimiter
      jobId = node.getPrintableId("._.");
    }

    return attempt > 0 ? "_job." + executionId + "." + attempt + "." + jobId
        + ".meta" : "_job." + executionId + "." + jobId + ".meta";
  }

  public static String createMetaDataFileName(final ExecutableNode node) {
    return JobRunner.createMetaDataFileName(node, node.getAttempt());
  }

  public static String createAttachmentFileName(final ExecutableNode node) {

    return JobRunner.createAttachmentFileName(node, node.getAttempt());
  }

  public static String createAttachmentFileName(final ExecutableNode node, final int attempt) {
    final int executionId = node.getExecutableFlow().getExecutionId();
    String jobId = node.getId();
    if (node.getExecutableFlow() != node.getParentFlow()) {
      // Posix safe file delimiter
      jobId = node.getPrintableId("._.");
    }

    return attempt > 0 ? "_job." + executionId + "." + attempt + "." + jobId
        + ".attach" : "_job." + executionId + "." + jobId + ".attach";
  }

  public void setValidatedProxyUsers(final Set<String> proxyUsers) {
    this.proxyUsers = proxyUsers;
  }

  public void setLogSettings(final org.slf4j.Logger flowLogger, final String logFileChuckSize,
      final int numLogBackup) {
    this.flowLogger = flowLogger;
    this.jobLogChunkSize = logFileChuckSize;
    this.jobLogBackupIndex = numLogBackup;
  }

  public Props getProps() {
    return this.props;
  }

  public void setPipeline(final FlowWatcher watcher, final int pipelineLevel) {
    this.watcher = watcher;
    this.pipelineLevel = pipelineLevel;

    if (this.pipelineLevel == 1) {
      this.pipelineJobs.add(this.node.getNestedId());
    } else if (this.pipelineLevel == 2) {
      this.pipelineJobs.add(this.node.getNestedId());
      final ExecutableFlowBase parentFlow = this.node.getParentFlow();

      if (parentFlow.getEndNodes().contains(this.node.getId())) {
        if (!parentFlow.getOutNodes().isEmpty()) {
          final ExecutableFlowBase grandParentFlow = parentFlow.getParentFlow();
          for (final String outNode : parentFlow.getOutNodes()) {
            final ExecutableNode nextNode =
                grandParentFlow.getExecutableNode(outNode);

            // If the next node is a nested flow, then we add the nested
            // starting nodes
            if (nextNode instanceof ExecutableFlowBase) {
              final ExecutableFlowBase nextFlow = (ExecutableFlowBase) nextNode;
              findAllStartingNodes(nextFlow, this.pipelineJobs);
            } else {
              this.pipelineJobs.add(nextNode.getNestedId());
            }
          }
        }
      } else {
        for (final String outNode : this.node.getOutNodes()) {
          final ExecutableNode nextNode = parentFlow.getExecutableNode(outNode);

          // If the next node is a nested flow, then we add the nested starting
          // nodes
          if (nextNode instanceof ExecutableFlowBase) {
            final ExecutableFlowBase nextFlow = (ExecutableFlowBase) nextNode;
            findAllStartingNodes(nextFlow, this.pipelineJobs);
          } else {
            this.pipelineJobs.add(nextNode.getNestedId());
          }
        }
      }
    }
  }

  private void findAllStartingNodes(final ExecutableFlowBase flow,
      final Set<String> pipelineJobs) {
    for (final String startingNode : flow.getStartNodes()) {
      final ExecutableNode node = flow.getExecutableNode(startingNode);
      if (node instanceof ExecutableFlowBase) {
        findAllStartingNodes((ExecutableFlowBase) node, pipelineJobs);
      } else {
        pipelineJobs.add(node.getNestedId());
      }
    }
  }

  /**
   * Returns a list of jobs that this JobRunner will wait upon to finish before starting. It is only
   * relevant if pipeline is turned on.
   */
  public Set<String> getPipelineWatchedJobs() {
    return this.pipelineJobs;
  }

  public long getDelayStart() {
    return this.delayStartMs;
  }

  public void setDelayStart(final long delayMS) {
    this.delayStartMs = delayMS;
  }

  public ExecutableNode getNode() {
    return this.node;
  }

  public String getJobId() {
    return this.node.getId();
  }

  public String getLogFilePath() {
    return this.logFile == null ? null : this.logFile.getPath();
  }

  private void createLogger() {
    // Create logger
    synchronized (logCreatorLock) {
      try {
        this.logFileName = createLogFileName(this.node);
        this.loggerName = String.format("%s.%s.%s.%s", UUID.randomUUID().toString(), this.executionId, node.getAttempt(), node.getId());
        this.logFile = new File(this.workingDir, logFileName);
        final String absolutePath = this.logFile.getAbsolutePath();
        this.flowLogger.info("Log file path for job: " + this.jobId + " is: " + absolutePath);
        // todo Added log filtering.
        List<LogFilterEntity> logFilterEntities = loader.listAllLogFilter().stream()
          .filter(x -> x.getOperateType() == OperateType.REMOVE_ALL).collect(Collectors.toList());
        LogUtils.createJobLog(this.workingDir.getAbsolutePath(), logFileName, loggerName, jobLogChunkSize, jobLogBackupIndex, logFilterEntities);
        this.logger = LoggerFactory.getLogger(loggerName);
        this.flowLogger.info("Created file appender for job " + this.jobId);
      } catch (final Exception e) {
        fireEvent(Event.create(this, EventType.JOB_FINISHED,
            new EventData(changeStatus(Status.FAILED), this.node.getNestedId())), false);
        this.flowLogger.error("Could not open log file in " + this.workingDir
            + " for job " + this.jobId, e);
        throw new RuntimeException("Could not open log file in " + this.workingDir
            + " for job " + this.jobId, e);
      }
    }

    final String externalViewer = ExternalLinkUtils
        .getExternalLogViewer(this.azkabanProps, this.jobId,
            this.props);
    if (!externalViewer.isEmpty()) {
      this.logger.info("If you want to leverage AZ ELK logging support, you need to follow the "
          + "instructions: http://azkaban.github.io/azkaban/docs/latest/#how-to");
      this.logger.info("If you did the above step, see logs at: " + externalViewer);
    }
  }


  private void createAttachmentFile() {
    final String fileName = createAttachmentFileName(this.node);
    final File file = new File(this.workingDir, fileName);
    this.attachmentFileName = file.getAbsolutePath();
  }

  private void closeLogger() {
    if (this.logger != null) {
      LogUtils.stopLog(loggerName);
    }
  }

  private void writeStatus() {
    try {
      this.node.setUpdateTime(System.currentTimeMillis());
      this.loader.updateExecutableNode(this.node);
    } catch (final ExecutorManagerException e) {
      this.flowLogger.error("Could not update job properties in db for "
          + this.jobId, e);
    }
  }

  /**
   * Used to handle non-ready and special status's (i.e. KILLED). Returns true if they handled
   * anything.
   */
  private boolean handleNonReadyStatus() {
    synchronized (this.syncObject) {
      Status nodeStatus = this.node.getStatus();
      boolean quickFinish = false;
      final long time = System.currentTimeMillis();

      if (Status.isStatusFinished(nodeStatus)) {
        serverLogger.info("execId:" + this.executionId + ",node:" + node.getNestedId() + ", status is " + nodeStatus);
        quickFinish = true;
      } else if (nodeStatus == Status.DISABLED) {
        serverLogger.info("execId:" + this.executionId + ",node:" + node.getNestedId() + ", disabled");
        nodeStatus = changeStatus(Status.SKIPPED, time);
        quickFinish = true;
      } else if (this.isKilled()) {
        serverLogger.info("execId:" + this.executionId + ",node:" + node.getNestedId() + ", was being killed");
        nodeStatus = changeStatus(Status.KILLED, time);
        quickFinish = true;
      // FIXME Added judgment conditions to determine whether the job has been skipped.
      } else if(this.isSkipped()){
        nodeStatus = changeStatus(Status.SKIPPED, time);
        quickFinish = true;
      }

      if (quickFinish) {
        this.node.setStartTime(time);
        fireEvent(
            Event.create(this, EventType.JOB_STARTED,
                new EventData(nodeStatus, this.node.getNestedId())));
        this.node.setEndTime(time);
        fireEvent(
            Event
                .create(this, EventType.JOB_FINISHED,
                    new EventData(nodeStatus, this.node.getNestedId())));
        serverLogger.info("execId:" + this.executionId + ",node:" + node.getNestedId() + ", quick Finish");
        return true;
      }

      return false;
    }
  }

  /**
   * If pipelining is set, will block on another flow's jobs.
   */
  private boolean blockOnPipeLine() {
    // FIXME Added judgment conditions to determine whether the job has been skipped.
    if (this.isKilled() || this.isSkipped()) {
      serverLogger.info("execId:" + this.executionId + ",node:" + node.getNestedId() + ", has been killed.");
      return true;
    }

    // For pipelining of jobs. Will watch other jobs.
    if (!this.pipelineJobs.isEmpty()) {
      String blockedList = "";
      final ArrayList<BlockingStatus> blockingStatus =
          new ArrayList<>();
      for (final String waitingJobId : this.pipelineJobs) {
        final Status status = this.watcher.peekStatus(waitingJobId);
        if (status != null && !Status.isStatusFinished(status)) {
          final BlockingStatus block = this.watcher.getBlockingStatus(waitingJobId);
          blockingStatus.add(block);
          blockedList += waitingJobId + ",";
        }
      }
      if (!blockingStatus.isEmpty()) {
        this.logger.info("Pipeline job " + this.jobId + " waiting on " + blockedList
            + " in execution " + this.watcher.getExecId());
        serverLogger.info("Pipeline job " + this.jobId + " waiting on " + blockedList
                + " in execution " + this.watcher.getExecId());
        for (final BlockingStatus bStatus : blockingStatus) {
          this.logger.info("Waiting on pipelined job " + bStatus.getJobId());
          serverLogger.info("execId:" + this.executionId + ",node:" + node.getNestedId() + ",Waiting on pipelined job " + bStatus.getJobId());
          this.currentBlockStatus = bStatus;
          bStatus.blockOnFinishedStatus();
          // FIXME Added judgment conditions to determine whether the job has been skipped.
          if (this.isKilled() || this.isSkipped()) {
            this.logger.info("Job was killed or skipped while waiting on pipeline. Quiting.");
            return true;
          } else {
            this.logger.info("Pipelined job " + bStatus.getJobId() + " finished.");
          }
        }
      }
    }

    this.currentBlockStatus = null;
    return false;
  }

  private boolean delayExecution() {
    synchronized (this) {
      // FIXME Added judgment conditions to determine whether the job has been skipped.
      if (this.isKilled() || this.isSkipped()) {
        serverLogger.info("execId:" + this.executionId + ",node:" + node.getNestedId() + ", has been killed.");
        return true;
      }

      final long currentTime = System.currentTimeMillis();
      if (this.delayStartMs > 0) {
        this.logger.info("Delaying start of execution for " + this.delayStartMs
            + " milliseconds.");
        try {
          this.wait(this.delayStartMs);
          this.logger.info("Execution has been delayed for " + this.delayStartMs
              + " ms. Continuing with execution.");
        } catch (final InterruptedException e) {
          this.logger.error("Job " + this.jobId + " was to be delayed for "
              + this.delayStartMs + ". Interrupted after "
              + (System.currentTimeMillis() - currentTime));
        }
        // FIXME Added judgment conditions to determine whether the job has been skipped.
        if (this.isKilled() || this.isSkipped()) {
          this.logger.info("Job was killed or skipped while in delay. Quiting.");
          return true;
        }
      }
    }

    return false;
  }

  private void finalizeLogFile(final int attemptNo) {
    closeLogger();
    if (this.logFile == null) {
      this.flowLogger.info("Log file for job " + this.jobId + " is null");
      return;
    }

    try {
      final File[] files = this.logFile.getParentFile().listFiles(new FilenameFilter() {
        @Override
        public boolean accept(final File dir, final String name) {
          return name.startsWith(JobRunner.this.logFile.getName());
        }
      });
      Arrays.sort(files, Collections.reverseOrder());

      this.loader.uploadLogFile(this.executionId, this.node.getNestedId(), attemptNo,
          files);
    } catch (final ExecutorManagerException e) {
      this.flowLogger.error(
          "Error writing out logs for job " + this.node.getNestedId(), e);
    }
  }

  private void finalizeAttachmentFile() {
    if (this.attachmentFileName == null) {
      this.flowLogger.info("Attachment file for job " + this.jobId + " is null");
      return;
    }

    try {
      final File file = new File(this.attachmentFileName);
      if (!file.exists()) {
        this.flowLogger.info("No attachment file for job " + this.jobId
            + " written.");
        return;
      }
      this.loader.uploadAttachmentFile(this.node, file);
    } catch (final ExecutorManagerException e) {
      this.flowLogger.error(
          "Error writing out attachment for job " + this.node.getNestedId(), e);
    }
  }

  private void uploadJobIdRelation(){
    JobIdRelationService jobIdRelationService = SERVICE_PROVIDER.getInstance(JobIdRelationService.class);
    try {
      final File[] files = this.logFile.getParentFile().listFiles(new FilenameFilter() {
        @Override
        public boolean accept(final File dir, final String name) {
          return name.startsWith(JobRunner.this.logFile.getName());
        }
      });
      Arrays.sort(files, Collections.reverseOrder());
      Set<String> appId = new HashSet<>();
      Set<String> bdpId = new HashSet<>();
      for (File file : files) {
        BufferedReader br = null;
        try {
          br = new BufferedReader(new FileReader(file));
          String line;
          while ((line = br.readLine()) != null) {
            Matcher m = APPLICATION_JOB_ID_PATTERN.matcher(line);
            while (m.find()) {
              String match = m.group(0);
              if (match.startsWith("application")) {
                appId.add(match);
              } else if (match.startsWith("job")) {
                bdpId.add(match);
              }
            }
          }
        } catch (IOException e) {
          logger.error("Error while trying to find applicationId for log", e);
        } finally {
          try {
            if (br != null) {
              br.close();
            }
          } catch (IOException e) {
            logger.error("close io failed.", e);
          }
        }
      }
      if(!appId.isEmpty() || !bdpId.isEmpty()) {
        JobIdRelation jobIdRelation = new JobIdRelation();
        jobIdRelation.setExecId(executionId);
        jobIdRelation.setAttempt(node.getAttempt());
        jobIdRelation.setJobNamePath(node.getNestedId());
        jobIdRelation.setApplicationId(org.apache.commons.lang3.StringUtils.join(appId, ","));
        jobIdRelation.setJobServerJobId(org.apache.commons.lang3.StringUtils.join(bdpId, ","));
        jobIdRelationService.addJobIdRelation(jobIdRelation);
      }

    } catch (Exception e){
      logger.error("add job id relation failed.", e);
    }
  }
  /**
   * The main run thread.
   */
  @Override
  public void run() {
    try {
      doRun();
    } catch (final Exception e) {
      serverLogger.error("Unexpected exception", e);
      throw e;
    }
    serverLogger.info("execId:" + this.executionId + ",node:" + node.getNestedId() + ", finished.");
  }

  
  private void doRun() {
    Thread.currentThread().setName(
        "JobRunner-" + this.jobId + "-" + this.executionId);

    // If the job is cancelled, disabled, killed. No log is created in this case
    if (handleNonReadyStatus()) {
      return;
    }
    createAttachmentFile();
    createLogger();
    serverLogger.info("execId:" + this.executionId + ",node:" + node.getNestedId() + ", createLogger.");
    boolean errorFound = false;
    // Delay execution if necessary. Will return a true if something went wrong.
    errorFound |= delayExecution();
    serverLogger.info("execId:" + this.executionId + ",node:" + node.getNestedId() + ", delayExecution.");

    // For pipelining of jobs. Will watch other jobs. Will return true if
    // something went wrong.
    errorFound |= blockOnPipeLine();
    serverLogger.info("execId:" + this.executionId + ",node:" + node.getNestedId() + ", blockOnPipeLine.");

    // Start the node.
    this.node.setStartTime(System.currentTimeMillis());
    Status finalStatus = this.node.getStatus();
    uploadExecutableNode();
    serverLogger.info("execId:" + this.executionId + ",node:" + node.getNestedId() + ", uploadExecutableNode.");


    Props inputProps = this.node.getInputProps();
    if(inputProps.containsKey(JOB_FAILED_RETRY_COUNT)
        && inputProps.containsKey(JOB_FAILED_RETRY_INTERVAL)){
      this.restartFaildOpen = true;
    }

    finalStatus = jobRunHandle(errorFound, finalStatus, this.restartFaildOpen);
    serverLogger.info("execId:" + this.executionId + ",node:" + node.getNestedId() + ", jobRunHandle.");

	// FIXME New function, when task execution fails, it will be re-executed according to the set parameters.
    if(this.restartFaildOpen){
      try {
        restartFailedJobHandle(errorFound, finalStatus, this.restartFaildOpen);
      } catch (Exception e) {
        logger.error("job rerun Exception.", e);
        if(this.props.get("azkaban.failureAction") != null
                && this.props.get("azkaban.failureAction").equals("FAILED_PAUSE")) {
          finalStatus = changeStatus(Status.FAILED_WAITING);
        } else {
          finalStatus = changeStatus(Status.FAILED);
        }
      }
    }
    serverLogger.info("execId:" + this.executionId + ",node:" + node.getNestedId() + ", run job end.");

    // change FAILED_RETRYING status to FAILED
    if(this.node.getStatus().equals(Status.FAILED_RETRYING)){
      logger.info("execId: " + executionId + ", node: " +  this.node.getId() + ", set status FAILED_RETRYING.");
      finalStatus = changeStatus(Status.FAILED);
    }

    // FIXME New feature. If the task is set to fail skip, you need to change the status to FAILED_SKIPPED when the task fails.
    if((this.node.getStatus().equals(Status.FAILED))
            && this.props.get("azkaban.jobSkipFailed") != null
            && this.props.get("azkaban.jobSkipFailed").equals(this.node.getId())){
      serverLogger.info("execId: " + executionId + ", node: " +  this.node.getId() + ", 预设置了失败跳过.");
      logger.info("execId: " + executionId + ", node: " +  this.node.getId() + ", 预设置了失败跳过.");
      finalStatus = changeStatus(Status.FAILED_SKIPPED);
    }
    serverLogger.info("execId:" + this.executionId + ",node:" + node.getNestedId() + ", FAILED_SKIPPED.");

    // FIXME New function, if the job stream is set to fail pause mode, when the task fails, the status needs to be changed to FAILED_WAITING
    if(this.node.getStatus().equals(Status.FAILED) &&
            this.props.get("azkaban.failureAction") != null
            && this.props.get("azkaban.failureAction").equals("FAILED_PAUSE")) {
      logger.info("execId: " + executionId + ", node: " +  this.node.getId() + ", 预设置了失败暂停.");
      finalStatus = changeStatus(Status.FAILED_WAITING);
    }


    this.node.setEndTime(System.currentTimeMillis());
    // FIXME Added judgment conditions to determine whether the job has been skipped.
    if(isSkipped()){
      logger.info("execId: " + executionId + ", node: " +  this.node.getId() + ", set status SKIPPED.");
      finalStatus = changeStatus(Status.SKIPPED);
    }

    if (isKilled()) {
      serverLogger.info("execId:" + this.executionId + ",node:" + node.getNestedId() + ", isKilled().");

      // even if it's killed, there is a chance that the job failed is marked as
      // failure,
      // So we set it to KILLED to make sure we know that we forced kill it
      // rather than
      // it being a legitimate failure.
      logInfo("execId: " + executionId + ", node: " +  this.node.getId() + ", status: " + this.node.getStatus());
      finalStatus = changeStatus(Status.KILLED);
    }

    logInfo(
        "Finishing job " + this.jobId + getNodeRetryLog() + " at " + this.node.getEndTime()
            + " with status " + this.node.getStatus());

    try {
      finalizeLogFile(this.node.getAttempt());
      finalizeAttachmentFile();
      uploadJobIdRelation();
      writeStatus();
    } finally {
      serverLogger.info("execId:" + this.executionId + ",node:" + node.getNestedId() + ", status: " + finalStatus);
      // note that FlowRunner thread does node.attempt++ when it receives the JOB_FINISHED event
      fireEvent(Event.create(this, EventType.JOB_FINISHED,
          new EventData(finalStatus, this.node.getNestedId())), false);
    }
  }

  private String getNodeRetryLog() {
    return this.node.getAttempt() > 0 ? (" retry: " + this.node.getAttempt()) : "";
  }

  private void uploadExecutableNode() {
    try {
      this.loader.uploadExecutableNode(this.node, this.props);
    } catch (final ExecutorManagerException e) {
      this.logger.error("Error writing initial node properties", e);
    }
  }

  private Status prepareJob() throws RuntimeException {
    // Check pre conditions
    // FIXME Added judgment conditions to determine whether the job has been skipped.
    if (this.props == null || this.isKilled() || this.isSkipped()) {
      logError("Failing job. The job properties don't exist");
      return null;
    }

    final Status finalStatus;
    synchronized (this.syncObject) {
      if ((this.node.getStatus() == Status.FAILED && !this.restartFailed) || this.isKilled() || this.isSkipped()) {
        return null;
      }

      logInfo("Starting job " + this.jobId + getNodeRetryLog() + " at " + this.node.getStartTime());

      // If it's an embedded flow, we'll add the nested flow info to the job
      // conf
      if (this.node.getExecutableFlow() != this.node.getParentFlow()) {
        final String subFlow = this.node.getPrintableId(":");
        this.props.put(CommonJobProperties.NESTED_FLOW_PATH, subFlow);
      }

      insertJobMetadata();
      insertJVMAargs();

      this.props.put(CommonJobProperties.JOB_ID, this.jobId);
      this.props.put(CommonJobProperties.JOB_ATTEMPT, this.node.getAttempt());
      this.props.put(CommonJobProperties.JOB_METADATA_FILE,
          createMetaDataFileName(this.node));
      this.props.put(CommonJobProperties.JOB_ATTACHMENT_FILE, this.attachmentFileName);
      this.props.put(CommonJobProperties.JOB_LOG_FILE, this.logFile.getAbsolutePath());
      finalStatus = changeStatus(Status.RUNNING);

      // 如果job使用了flow.dir参数，获取工作流执行根目录
      if (!this.props.containsKey("flow.dir")) {
        this.props.put("flow.dir", this.node.getInputProps().get("flow.dir"));
      }
      // Ability to specify working directory
      if (!this.props.containsKey(AbstractProcessJob.WORKING_DIR)) {
        this.props.put(AbstractProcessJob.WORKING_DIR, this.workingDir.getAbsolutePath());
      }
      //校验Job中配置的代理用户是否符合用户配置
      if (this.props.containsKey(JobProperties.USER_TO_PROXY)) {
        final String jobProxyUser = this.props.getString(JobProperties.USER_TO_PROXY);
        // user.xml 配置的 Proxy 跟 job中的不一致， 并且 Proxy 不为空字符串
        if (this.proxyUsers != null && !this.proxyUsers.contains(jobProxyUser) && !proxyUsers.isEmpty()) {
          final String permissionsPageURL = getProjectPermissionsURL();
          this.logger.error("代理用户 " + jobProxyUser
              + " 沒有权限执行当前任务 " + this.jobId + "!"
              + " 如果想使用代理用户 " + jobProxyUser + " 执行Job "
              + ", 请联系系统管理员为您的用户添加该代理用户。 ");
              //permissionsPageURL);
          return null;
        // FIXME Added judgment that user.xml configures Proxy as an empty string is inconsistent with the job and Proxy is not an empty string
        }else if(proxyUsers.isEmpty() && !jobProxyUser.equals(this.node.getExecutableFlow().getSubmitUser())){
          this.logger.error("代理用戶 " + jobProxyUser + " 沒有权限执行当前任务 " + this.jobId + "!"
              + "请联系系统管理员为您的用户添加该代理用户。");
          return null;
        }
      } else {
        final String submitUser = this.getNode().getExecutableFlow().getSubmitUser();
        this.props.put(JobProperties.USER_TO_PROXY, submitUser);
//        this.logger.info("user.to.proxy property was not set, defaulting to submit user " +
//            submitUser);
        this.logger.info("user.to.proxy 参数未设置, 默认使用项目提交用户执行 " + submitUser);
      }

      try {
        this.job = this.jobtypeManager.buildJobExecutor(this.jobId, this.props, this.logger);
      } catch (final JobTypeManagerException e) {
        this.logger.error("Failed to build job type", e);
        return null;
      }
    }

    return finalStatus;
  }

  /**
   * Get project permissions page URL
   */
  private String getProjectPermissionsURL() {
    String projectPermissionsURL = null;
    final String baseURL = this.azkabanProps.get(AZKABAN_WEBSERVER_URL);
    if (baseURL != null) {
      final String projectName = this.node.getParentFlow().getProjectName();
      projectPermissionsURL = String
          .format("%s/manager?project=%s&permissions", baseURL, projectName);
    }
    return projectPermissionsURL;
  }

  /**
   * Add useful JVM arguments so it is easier to map a running Java process to a flow, execution id
   * and job
   */
  private void insertJVMAargs() {
    final String flowName = this.node.getParentFlow().getFlowId();
    final String jobId = this.node.getId();

    String jobJVMArgs =
        String.format(
            "-Dazkaban.flowid=%s -Dazkaban.execid=%s -Dazkaban.jobid=%s",
            flowName, this.executionId, jobId);

    final String previousJVMArgs = this.props.get(JavaProcessJob.JVM_PARAMS);
    jobJVMArgs += (previousJVMArgs == null) ? "" : " " + previousJVMArgs;

    this.logger.info("job JVM args: " + jobJVMArgs);
    this.props.put(JavaProcessJob.JVM_PARAMS, jobJVMArgs);
  }

  /**
   * Add relevant links to the job properties so that downstream consumers may know what executions
   * initiated their execution.
   */
  private void insertJobMetadata() {
    final String baseURL = this.azkabanProps.get(AZKABAN_WEBSERVER_URL);
    if (baseURL != null) {
      final String flowName = this.node.getParentFlow().getFlowId();
      final String projectName = this.node.getParentFlow().getProjectName();

      this.props.put(CommonJobProperties.AZKABAN_URL, baseURL);
      this.props.put(CommonJobProperties.EXECUTION_LINK,
          String.format("%s/executor?execid=%d", baseURL, this.executionId));
      this.props.put(CommonJobProperties.JOBEXEC_LINK, String.format(
          "%s/executor?execid=%d&job=%s", baseURL, this.executionId, this.jobId));
      this.props.put(CommonJobProperties.ATTEMPT_LINK, String.format(
          "%s/executor?execid=%d&job=%s&attempt=%d", baseURL, this.executionId,
          this.jobId, this.node.getAttempt()));
      this.props.put(CommonJobProperties.WORKFLOW_LINK, String.format(
          "%s/manager?project=%s&flow=%s", baseURL, projectName, flowName));
      this.props.put(CommonJobProperties.JOB_LINK, String.format(
          "%s/manager?project=%s&flow=%s&job=%s", baseURL, projectName,
          flowName, this.jobId));
    } else {
      if (this.logger != null) {
        this.logger.info(AZKABAN_WEBSERVER_URL + " property was not set");
      }
    }
    // out nodes
    this.props.put(CommonJobProperties.OUT_NODES,
        StringUtils.join2(this.node.getOutNodes(), ","));

    // in nodes
    this.props.put(CommonJobProperties.IN_NODES,
        StringUtils.join2(this.node.getInNodes(), ","));
  }

  private Status runJob() {
    Status finalStatus;
    try {
      this.job.run();
      finalStatus = this.node.getStatus();
    } catch (final Throwable e) {
      synchronized (this.syncObject) {
        if (this.props.getBoolean("job.succeed.on.failure", false)) {
          finalStatus = changeStatus(Status.FAILED_SUCCEEDED);
          logError("Job run failed, but will treat it like success.");
          logError(e.getMessage() + " cause: " + e.getCause(), e);
        } else {
          if (isKilled() || this.node.getStatus() == Status.KILLED) {
            finalStatus = Status.KILLED;
            logError("Job run killed!", e);
			    // FIXME Determine if the task has been closed for execution.
          } else if (isSkipped()) {
            finalStatus = Status.SKIPPED;
            logError("Job run SKIPPED!", e);
			    // FIXME Determine if the task is set to retry.
          } else if (this.restartFaildOpen){
            finalStatus = changeStatus(Status.FAILED_RETRYING);
            writeStatus();
            fireEvent(Event.create(this, EventType.JOB_STATUS_CHANGED,
                    new EventData(finalStatus, this.node.getNestedId())));
            logError("Job run failed_retrying!", e);
          } else {
            finalStatus = changeStatus(Status.FAILED);
            logError("Job run failed!", e);
          }
          logError(e.getMessage() + " cause: " + e.getCause());
        }
      }
    }

    if (this.job != null) {
      this.node.setOutputProps(this.job.getJobGeneratedProperties());
    }

    synchronized (this.syncObject) {
      // If the job is still running (but not killed), set the status to Success.
      if (!Status.isStatusFinished(finalStatus) && !isKilled()) {
        if(!finalStatus.equals(Status.FAILED_RETRYING)) {
          logInfo("Job run succeeded/retried_succeeded.");
          // FIXME The task status is RETRIED_SUCCEEDED after successful retry execution.
          if(this.retryConut != 0 || this.node.getAttempt() != 0){
            finalStatus = changeStatus(Status.RETRIED_SUCCEEDED);
          } else {
            finalStatus = changeStatus(Status.SUCCEEDED);
          }
        }
      }
    }
    return finalStatus;
  }

  private Status changeStatus(final Status status) {
    changeStatus(status, System.currentTimeMillis());
    return status;
  }

  private Status changeStatus(final Status status, final long time) {
    this.node.setStatus(status);
    this.node.setUpdateTime(time);
    return status;
  }

  private void fireEvent(final Event event) {
    fireEvent(event, true);
  }

  private void fireEvent(final Event event, final boolean updateTime) {
    if (updateTime) {
      this.node.setUpdateTime(System.currentTimeMillis());
    }
    this.fireEventListeners(event);
  }

  public void killBySLA() {
    synchronized (this.syncObject) {
      kill();
      this.getNode().setKilledBySLA(true);
    }
  }

  public void kill() {
    synchronized (this.syncObject) {
      if (Status.isStatusFinished(this.node.getStatus()) || this.node.getStatus().equals(Status.FAILED_WAITING)) {
        logInfo("this job is finished. " + node.getNestedId());
        serverLogger.info("this job is finished. " + node.getNestedId());
        return;
      }
      logError("Kill has been called.");
      serverLogger.warn("execId : " + executionId + " ,job: " + node.getNestedId() + ", Kill has been called.");
      this.changeStatus(Status.KILLING);
      this.killed = true;
      handleKill();
    }
  }

  /**
   * 跳过执行job
   */
  public void skippedJob(String user){
    synchronized (this.syncObject) {
      logError("User " + user + " used skip execution, job: " + node.getNestedId());
      if (Status.isStatusFinished(this.node.getStatus()) || this.node.getStatus().equals(Status.FAILED_WAITING)) {
        logInfo("this job is finished. " + node.getNestedId());
        serverLogger.info("this job is finished. " + node.getNestedId());
        return;
      }
      serverLogger.warn("User " + user + " used skip execution, job: " + node.getNestedId());
      this.changeStatus(Status.DISABLED);
      this.skipped = true;
      handleKill();
    }
  }

  private void handleKill(){
    final BlockingStatus status = this.currentBlockStatus;
    if (status != null) {
      status.unblock();
    }

    // Cancel code here
    if (this.job == null) {
      serverLogger.warn( node.getNestedId() + ", Job hasn't started yet.");
      logError("Job hasn't started yet.");
      // Just in case we're waiting on the delay
      synchronized (this) {
        this.notify();
      }
      return;
    }

    try {
      this.job.cancel();
    } catch (final Exception e) {
      serverLogger.error( node.getNestedId() + ": " + e.getMessage());
      logError(e.getMessage());
      logError(
          "Failed trying to cancel job. Maybe it hasn't started running yet or just finished.");
      serverLogger.error( node.getNestedId() + ", Failed trying to cancel job. Maybe it hasn't started running yet or just finished.");
    }
  }

  public boolean isKilled() {
    return this.killed;
  }

  public boolean isSkipped() {
    return this.skipped;
  }

  public Status getStatus() {
    return this.node.getStatus();
  }

  private void logError(final String message) {
    if (this.logger != null) {
      this.logger.error(message);
    }
  }

  private void logError(final String message, final Throwable t) {
    if (this.logger != null) {
      this.logger.error(message, t);
    }
  }

  private void logInfo(final String message) {
    if (this.logger != null) {
      this.logger.info(message);
    }
  }

  public File getLogFile() {
    return this.logFile;
  }

  public org.slf4j.Logger getLogger() {
    return this.logger;
  }

  private Status jobRunHandle(boolean errorFound, Status finalStatus, boolean restartFailed) {
    return this.jobRunHandle(errorFound, finalStatus, restartFailed, System.currentTimeMillis());
  }

  /**
   * 执行Job的方法
   * @param errorFound
   * @param finalStatus
   */
  private Status jobRunHandle(boolean errorFound, Status finalStatus, boolean restartFailed, long startTime){
//    // Start the node.
    this.node.setStartTime(startTime);
//    Status finalStatus = this.node.getStatus();
//    //更新数据库中Job的状态信息
//    uploadExecutableNode();
    if (!errorFound && !isKilled() && !isSkipped()) {
      fireEvent(Event.create(this, EventType.JOB_STARTED, new EventData(this.node)));
      // status queue -> running
      final Status prepareStatus = prepareJob();
      if (prepareStatus != null) {
        // Writes status to the db
        writeStatus();
        fireEvent(Event.create(this, EventType.JOB_STATUS_CHANGED,
            new EventData(prepareStatus, this.node.getNestedId())));
        finalStatus = runJob();

      } else {
        if(!restartFailed){
          finalStatus = changeStatus(Status.FAILED);
          logError("Job run failed preparing the job.");
        } else {
          finalStatus = changeStatus(Status.FAILED_RETRYING);
          writeStatus();
          fireEvent(Event.create(this, EventType.JOB_STATUS_CHANGED,
                  new EventData(finalStatus, this.node.getNestedId())));
          logError("Job run failed_retrying preparing the job.");
        }
      }
    }
    return finalStatus;
    //this.node.setEndTime(System.currentTimeMillis());
  }

  private static Pattern NUM_PATTERN = Pattern.compile("^[1-9]\\d*$");
  /**
   * Job错误重试处理逻辑
   * 用户在.job文件中添加了错误重试次数跟错误重试间隔参数时，启动错误重试处理。
   * 错误重试次数参数名：job.failed.restart.count
   * 错误重试间隔参数名：job.failed.restart.interval
   * @param errorFound
   * @param finalStatus
   */
  private void restartFailedJobHandle(boolean errorFound, Status finalStatus, boolean restartFailed)
      throws Exception {
    Props inputProps = this.node.getInputProps();

    if(restartFailed && Status.FAILED.equals(finalStatus) || Status.FAILED_RETRYING.equals(finalStatus)){
      int count = 0;
      int interval = 0;
      try {
        String restartCount = inputProps.getString(JOB_FAILED_RETRY_COUNT).trim();
        String restartInterval = inputProps.getString(JOB_FAILED_RETRY_INTERVAL).trim();

        if(NUM_PATTERN.matcher(restartCount).matches() && NUM_PATTERN.matcher(restartInterval).matches()){
          count = Integer.valueOf(restartCount);
          interval = Integer.valueOf(restartInterval);
        } else {
          // 错误重跑参数校验异常！参数请用正整数。
          throw new ExecutorManagerException("Check parameters of error retry fail, parameters accept positive integer only.");
        }
      } catch (Exception e) {
        logger.error("错误重跑参数设置异常！", e);
        // 错误重跑参数设置异常！错误重跑执行失败
        throw new ExecutorManagerException("Exception in parameter of Error retry, failed to excute Error retry.", e);
      }
      if(count > 3){
        logger.warn("错误重跑最大重试次数为3次，大于3的次数不会执行！");
      }
      count = count > 3 ? 3 : count;
      logger.info("执行错误重跑逻辑！ 重试次数 " + count + "。 重试间隔 " + interval + "秒。");
      this.restartFailed = true;
      for(int i=0; i < count; i++){
        try {
          Thread.sleep(interval * 1000);
          if(isKilled() || isSkipped()){
            logger.info("job has been killed or skipped, no need to retry.");
            return;
          }
          logger.info("重试第" + (i+1) + "次。");
          this.retryConut++;
          if(i == count - 1){
            restartFailed = false;
          }
          finalStatus = jobRunHandle(errorFound, finalStatus, restartFailed, this.node.getStartTime());
          if(Status.isSucceeded(finalStatus) || isKilled() || isSkipped()){
            logger.info("the job stop retry, job status is " + finalStatus);
            break;
          } else if((i + 1) == count){
            logger.info("retry reaches the limit: " + count + ", setting job failed." );
            finalStatus = changeStatus(Status.FAILED);
          }
        } catch (InterruptedException e) {
          logger.error("Job重跑中断！", e);
        }
      }



    }


  }


}
