package azkaban.log;

import static azkaban.Constants.ConfigurationKeys.WTSS_JOB_LOG_AUTO_DIAGNOSIS_THREAD_INTERVAL;
import static azkaban.Constants.ConfigurationKeys.WTSS_JOB_LOG_AUTO_DIAGNOSIS_TIME_INTERVAL;
import static azkaban.Constants.ConfigurationKeys.WTSS_JOB_LOG_DIAGNOSIS_SCRIPT_PATH;
import static azkaban.ServiceProvider.SERVICE_PROVIDER;

import azkaban.Constants.ConfigurationKeys;
import azkaban.executor.ExecutableJobInfo;
import azkaban.executor.ExecutorManagerAdapter;
import azkaban.executor.ExecutorManagerException;
import azkaban.log.diagnosis.entity.JobLogDiagnosis;
import azkaban.log.diagnosis.service.JobLogDiagnosisService;
import azkaban.log.diagnosis.service.JobLogDiagnosisServiceImpl;
import azkaban.spi.AzkabanException;
import azkaban.utils.Props;
import java.io.IOException;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import javax.inject.Inject;
import javax.inject.Singleton;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 用于查询服务自动对失败任务进行日志诊断，内含定时线程，可配置间隔时间、扫描时长
 *
 * @author lebronwang
 * @date 2025/01/27
 **/
@Singleton
public class JobLogDiagnosisManager {

  private static final Logger LOG = LoggerFactory.getLogger(JobLogDiagnosisManager.class);

  private ScheduledExecutorService jobLogDiagnosisManagerExecutor;
  private final boolean jobLogDiagnosisManagerSwitch;

  private final int threadPoolSize;

  private final int diagnosisInterval;

  private ExecutorManagerAdapter executorManager;

  private long indexTime;

  private JobLogDiagnosisService jobLogDiagnosisService;

  private String diagnosisScriptPath;

  @Inject
  public JobLogDiagnosisManager(final Props azkProps, ExecutorManagerAdapter executorManager) {

    this.threadPoolSize = azkProps.getInt(
        ConfigurationKeys.WTSS_JOB_LOG_AUTO_DIAGNOSIS_THREADS_SIZE,
        5);
    this.jobLogDiagnosisManagerSwitch = azkProps.getBoolean(
        ConfigurationKeys.WTSS_JOB_LOG_AUTO_DIAGNOSIS_SWITCH,
        true);

    if (this.jobLogDiagnosisManagerSwitch && this.threadPoolSize <= 0) {
      String errorMsg =
          "JobLogDiagnosisManager is enabled but thread pool size is <= 0: " + this.threadPoolSize;
      LOG.error(errorMsg);
      throw new AzkabanException(errorMsg);
    }

    this.diagnosisInterval = azkProps.getInt(WTSS_JOB_LOG_AUTO_DIAGNOSIS_THREAD_INTERVAL, 600);
    this.executorManager = executorManager;

    this.indexTime = azkProps.getInt(WTSS_JOB_LOG_AUTO_DIAGNOSIS_TIME_INTERVAL, 20 * 60 * 1000);
    this.jobLogDiagnosisService = SERVICE_PROVIDER.getInstance(
        JobLogDiagnosisServiceImpl.class);
    diagnosisScriptPath = azkProps.getString(WTSS_JOB_LOG_DIAGNOSIS_SCRIPT_PATH,
        "/appcom/Install/AzkabanInstall/wtss-query/bin/wtss-analyze.sh");
  }

  public void start() {
    if (this.jobLogDiagnosisManagerSwitch) {
      LOG.info("JobLogDiagnosisManager is ready to take tasks. ");
      this.jobLogDiagnosisManagerExecutor = Executors.newScheduledThreadPool(this.threadPoolSize);
      Runnable task = new JobLogDiagnosisTask(executorManager, indexTime, jobLogDiagnosisService,
          diagnosisScriptPath);
      this.jobLogDiagnosisManagerExecutor.scheduleAtFixedRate(task, 0, this.diagnosisInterval,
          TimeUnit.SECONDS);
    } else {
      LOG.info("JobLogDiagnosisManager is disabled.");
      this.jobLogDiagnosisManagerExecutor = null;
    }
  }

  public boolean stop() throws InterruptedException {
    if (this.jobLogDiagnosisManagerExecutor != null) {
      this.jobLogDiagnosisManagerExecutor.shutdown();
      if (!this.jobLogDiagnosisManagerExecutor.awaitTermination(30, TimeUnit.SECONDS)) {
        this.jobLogDiagnosisManagerExecutor.shutdownNow();
      }
    }
    return true;
  }

  public static class JobLogDiagnosisTask implements Runnable {

    private static final Logger LOG = LoggerFactory.getLogger(JobLogDiagnosisTask.class);

    private ExecutorManagerAdapter executorManager;

    private long indexTime;

    private JobLogDiagnosisService jobLogDiagnosisService;

    private String scriptPath;

    public JobLogDiagnosisTask(ExecutorManagerAdapter executorManager, long indexTime,
        JobLogDiagnosisService jobLogDiagnosisService, String scriptPath) {
      this.executorManager = executorManager;
      this.indexTime = indexTime;
      this.jobLogDiagnosisService = jobLogDiagnosisService;
      this.scriptPath = scriptPath;
    }

    @Override
    public void run() {
      try {

        LOG.info("Start to diagnose failed jobs automatically, index time: {} ms ", this.indexTime);
        // 查询任务表，找到失败任务
        List<ExecutableJobInfo> diagnosisJobs = this.executorManager.getDiagnosisJobs(
            System.currentTimeMillis() - this.indexTime);
        if (diagnosisJobs.isEmpty()) {
          return;
        }

        for (ExecutableJobInfo diagnosisJob : diagnosisJobs) {
          int execId = diagnosisJob.getExecId();
          String jobId = diagnosisJob.getJobId();
          int attempt = diagnosisJob.getAttempt();
          // 查询DB
          JobLogDiagnosis jobLogDiagnosis = this.jobLogDiagnosisService.getJobLogDiagnosis(
              execId, jobId, attempt);
          if (jobLogDiagnosis != null && StringUtils.isNotEmpty(jobLogDiagnosis.getLog())) {
            continue;
          }
          // 调用智能诊断工具
          Map<String, String> diagnosisInfo = this.jobLogDiagnosisService.generateDiagnosisInfo(
              this.scriptPath, execId, jobId, attempt);

          if (diagnosisInfo.containsKey("error")) {
            LOG.warn("error when diagnosing job[execId: {}, jobId: {}, attempt: {}], {}", execId,
                jobId, attempt, diagnosisInfo.get("error"));
            continue;
          }

          String diagnosisLog = diagnosisInfo.get("data");
          // 将生成的诊断日志保存到数据库
          int updateResult = 0;
          if (!diagnosisLog.isEmpty()) {
            updateResult = this.jobLogDiagnosisService.updateJobLogDiagnosis(
                execId, jobId, attempt, diagnosisLog);
          }

          if (updateResult < 1) {
            LOG.warn("error when updating result for job[execId: {}, jobId: {}, attempt: {}]",
                execId, jobId, attempt);
          }
        }

      } catch (ExecutorManagerException | IOException | SQLException | InterruptedException e) {
        LOG.warn("Error in executing task ", e);
      }
    }
  }
}
