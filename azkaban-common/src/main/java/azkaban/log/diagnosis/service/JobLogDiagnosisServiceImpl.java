package azkaban.log.diagnosis.service;

import azkaban.log.diagnosis.dao.JobLogDiagnosisDao;
import azkaban.log.diagnosis.entity.JobLogDiagnosis;
import azkaban.log.diagnosis.util.DiagnosisUtils;
import org.apache.commons.collections.CollectionUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.io.IOException;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;

/**
 * @author lebronwang
 * @date 2025/01/26
 **/
@Singleton
public class JobLogDiagnosisServiceImpl implements JobLogDiagnosisService {

  public static final Logger logger = LoggerFactory.getLogger(JobLogDiagnosisServiceImpl.class);
  private final JobLogDiagnosisDao jobLogDiagnosisDao;

  @Inject
  public JobLogDiagnosisServiceImpl(JobLogDiagnosisDao jobLogDiagnosisDao) {
    this.jobLogDiagnosisDao = jobLogDiagnosisDao;
  }

  @Override
  public JobLogDiagnosis getJobLogDiagnosis(int execId, String jobName, int attempt)
      throws SQLException {
    List<JobLogDiagnosis> jobLogDiagnosisList = this.jobLogDiagnosisDao.getJobLogDiagnosis(execId,
        jobName, attempt);

    if (CollectionUtils.isNotEmpty(jobLogDiagnosisList)) {
      return jobLogDiagnosisList.get(0);
    } else {
      return null;
    }
  }

  @Override
  public Map<String, String> generateDiagnosisInfo(String scriptPath, int execId, String jobName,
      int attempt) throws IOException, InterruptedException {

    return DiagnosisUtils.getDiagnosisResult(scriptPath, execId, jobName, attempt);
  }

  @Override
  public int updateJobLogDiagnosis(int execId, String jobName, int attempt, String log)
      throws SQLException {
    return this.jobLogDiagnosisDao.updateJobLogDiagnosis(execId, jobName, attempt, log);
  }
}
