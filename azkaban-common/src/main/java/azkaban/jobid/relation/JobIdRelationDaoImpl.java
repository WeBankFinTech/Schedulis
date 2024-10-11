package azkaban.jobid.relation;



import azkaban.db.DatabaseOperator;
import org.apache.commons.dbutils.ResultSetHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Singleton
public class JobIdRelationDaoImpl implements JobIdRelationDao {

  private static final Logger logger = LoggerFactory.getLogger(JobIdRelationDaoImpl.class);


  private static final String FETCH_JOB_ID_RELATION =
      "SELECT w.id, w.`exec_id`, w.`attempt`, w.`job_id`, w.`job_server_job_id`, w.`application_id` FROM wtss_job_id_relation w " +
          " WHERE w.`exec_id` = ? AND w.`job_id` = ?;";

  private static final String ADD_JOB_ID_RELATION =
      "INSERT INTO wtss_job_id_relation  (`exec_id`, `attempt`, `job_id`, `job_server_job_id`, `application_id`) " +
          " VALUES (?, ?, ?, ?, ?);";

  private final DatabaseOperator dbOperator;

  @Inject
  public JobIdRelationDaoImpl(final DatabaseOperator dbOperator) {
    this.dbOperator = dbOperator;
  }

  @Override
  public List<JobIdRelation> getJobIdRelation(Integer execId, String jobNamePath) throws Exception {
    return dbOperator.query(FETCH_JOB_ID_RELATION, new FetchJobIdRelation(), execId, jobNamePath);
  }

  @Override
  public int addJobIdRelation(JobIdRelation jobIdRelation) throws Exception {
    return dbOperator.update(ADD_JOB_ID_RELATION, jobIdRelation.getExecId(), jobIdRelation.getAttempt(), jobIdRelation.getJobNamePath(), jobIdRelation.getJobServerJobId(), jobIdRelation.getApplicationId());
  }

  static class FetchJobIdRelation implements ResultSetHandler<List<JobIdRelation>> {

    @Override
    public List<JobIdRelation> handle(final ResultSet rs) throws SQLException {
      if (!rs.next()) {
        return Collections.emptyList();
      }
      List<JobIdRelation> list = new ArrayList<>();
      do{
        JobIdRelation jobIdRelation = new JobIdRelation(
            rs.getInt(1),
            rs.getInt(2),
            rs.getInt(3),
            rs.getString(4),
            rs.getString(5),
            rs.getNString(6)
        );
        list.add(jobIdRelation);
      }while (rs.next());
      return list;
    }
  }


}
