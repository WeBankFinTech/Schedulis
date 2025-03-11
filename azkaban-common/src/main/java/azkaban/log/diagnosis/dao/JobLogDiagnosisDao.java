package azkaban.log.diagnosis.dao;

import azkaban.log.diagnosis.entity.JobLogDiagnosis;
import java.sql.SQLException;
import java.util.List;

/**
 * @author lebronwang
 */
public interface JobLogDiagnosisDao {

  /**
   * 获取任务日志智能诊断
   *
   * @param execId  执行 id
   * @param jobName 任务名
   * @param attempt 执行次数
   * @return
   * @throws SQLException
   */
  List<JobLogDiagnosis> getJobLogDiagnosis(int execId, String jobName, int attempt)
      throws SQLException;


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
