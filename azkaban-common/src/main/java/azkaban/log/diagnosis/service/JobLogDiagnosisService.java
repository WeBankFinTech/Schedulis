package azkaban.log.diagnosis.service;

import azkaban.log.diagnosis.entity.JobLogDiagnosis;
import java.io.IOException;
import java.sql.SQLException;
import java.util.Map;

/**
 * @author lebronwang
 */
public interface JobLogDiagnosisService {

  /**
   * 获取任务日志智能诊断信息
   *
   * @param execId
   * @param jobName
   * @param attempt
   * @return
   * @throws SQLException
   */
  JobLogDiagnosis getJobLogDiagnosis(int execId, String jobName, int attempt) throws SQLException;

  /**
   * 生成诊断信息
   *
   * @param scriptPath
   * @param execId
   * @param jobName
   * @param attempt
   * @return
   * @throws IOException
   * @throws InterruptedException
   */
  Map<String, String> generateDiagnosisInfo(String scriptPath, int execId, String jobName,
      int attempt) throws IOException, InterruptedException;

  /**
   * 更新诊断信息
   *
   * @param execId
   * @param jobName
   * @param attempt
   * @param log
   * @return
   * @throws SQLException
   */
  int updateJobLogDiagnosis(int execId, String jobName, int attempt, String log)
      throws SQLException;
}
