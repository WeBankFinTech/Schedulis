package azkaban.log.diagnosis.dao;

import azkaban.db.DatabaseOperator;
import azkaban.log.diagnosis.entity.JobLogDiagnosis;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import javax.inject.Inject;
import javax.inject.Singleton;
import org.apache.commons.dbutils.ResultSetHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author lebronwang
 * @date 2025/01/26
 **/
@Singleton
public class JobLogDiagnosisDaoImpl implements JobLogDiagnosisDao {

  public static final Logger logger = LoggerFactory.getLogger(JobLogDiagnosisDaoImpl.class);

  public static final String BASE_QUERY_SQL = "SELECT exec_id, name, attempt, log FROM wtss_job_log_diagnosis ";

  private final DatabaseOperator dbOperator;

  @Inject
  public JobLogDiagnosisDaoImpl(DatabaseOperator dbOperator) {
    this.dbOperator = dbOperator;
  }

  @Override
  public List<JobLogDiagnosis> getJobLogDiagnosis(int execId, String jobName, int attempt)
      throws SQLException {
    String querySql = BASE_QUERY_SQL + "WHERE exec_id = ? AND name = ? AND attempt = ? ";

    return this.dbOperator.query(querySql, new JobLogDiagnosisHandler(), execId,
        jobName, attempt);
  }

  @Override
  public int updateJobLogDiagnosis(int execId, String jobName, int attempt, String log)
      throws SQLException {

    String sql = "INSERT INTO wtss_job_log_diagnosis(exec_id, name, attempt, log, upload_time) "
        + "VALUES(?, ?, ?, ?, ?) ";

    return this.dbOperator.update(sql, execId, jobName, attempt, log, System.currentTimeMillis());
  }

  private static class JobLogDiagnosisHandler implements ResultSetHandler<List<JobLogDiagnosis>> {

    @Override
    public List<JobLogDiagnosis> handle(ResultSet resultSet) throws SQLException {
      if (!resultSet.next()) {
        return Collections.emptyList();
      }

      List<JobLogDiagnosis> jobLogDiagnosisList = new ArrayList<>();

      do {
        int execId = resultSet.getInt("exec_id");
        String jobName = resultSet.getString("name");
        int attempt = resultSet.getInt("attempt");
        String log = resultSet.getString("log");

        JobLogDiagnosis jobLogDiagnosis = new JobLogDiagnosis();
        jobLogDiagnosis.setExecId(execId);
        jobLogDiagnosis.setJobName(jobName);
        jobLogDiagnosis.setAttempt(attempt);
        jobLogDiagnosis.setLog(log);

        jobLogDiagnosisList.add(jobLogDiagnosis);
      } while (resultSet.next());
      return jobLogDiagnosisList;
    }
  }
}
