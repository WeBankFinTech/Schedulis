package azkaban.jobid.relation;


import azkaban.db.DatabaseOperator;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import javax.inject.Inject;
import javax.inject.Singleton;

import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.dbutils.ResultSetHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Singleton
public class JobIdRelationDaoImpl implements JobIdRelationDao {

  private static final Logger logger = LoggerFactory.getLogger(JobIdRelationDaoImpl.class);


  private static final String FETCH_JOB_ID_RELATION =
          "SELECT w.id, w.`exec_id`, w.`attempt`, w.`job_id`, w.`job_server_job_id`, w.`application_id`, w.`proxy_url`, w.`linkis_id` FROM wtss_job_id_relation w " +
                  " WHERE w.`exec_id` = ? AND w.`job_id` = ?;";

  private static final String FETCH_JOB_ID_RELATION_ATTEMPT =
          "SELECT w.id, w.`exec_id`, w.`attempt`, w.`job_id`, w.`job_server_job_id`, w.`application_id`, w.`proxy_url`, w.`linkis_id` FROM wtss_job_id_relation w " +
                  " WHERE w.`exec_id` = ? AND w.`job_id` = ? AND w.`attempt` = ? limit 1;";

  private static final String ADD_JOB_ID_RELATION =
          "INSERT INTO wtss_job_id_relation  (`exec_id`, `attempt`, `job_id`, `job_server_job_id`, `application_id`, `linkis_id`, `proxy_url`) " +
                  " VALUES (?, ?, ?, ?, ?, ?, ?);";

  private static final String UPDATE_JOB_ID_RELATION =
          "UPDATE  wtss_job_id_relation set `proxy_url` = ?, `%s` = ? where `exec_id` = ? and `attempt` = ? and `job_id` = ?  and `%s` = ? ";

  private static final String UPDATE_PROXY_URL = "UPDATE wtss_job_id_relation set `proxy_url` = ? WHERE `exec_id` = ? and `attempt` = ? and `job_id` = ? ";

  private static final String UPDATE_JOB_INFO = "UPDATE wtss_job_id_relation SET `application_id` = ?, `job_server_job_id` = ? ,`proxy_url` = ? WHERE `id` = ?";

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
  public JobIdRelation getJobIdRelation(Integer execId, String jobNamePath, Integer attempt) throws Exception {
    List<JobIdRelation> query = dbOperator.query(FETCH_JOB_ID_RELATION_ATTEMPT, new FetchJobIdRelation(), execId, jobNamePath, attempt);
    if (CollectionUtils.isNotEmpty(query)) {
      return query.get(0);
    } else {
      return null;
    }
  }

  @Override
  public int addJobIdRelation(JobIdRelation jobIdRelation) throws Exception {
    return dbOperator.update(ADD_JOB_ID_RELATION, jobIdRelation.getExecId(), jobIdRelation.getAttempt(), jobIdRelation.getJobNamePath(), jobIdRelation.getJobServerJobId(), jobIdRelation.getApplicationId(), jobIdRelation.getLinkisId(), jobIdRelation.getProxyUrl());
  }


  @Override
  public int updateJobIdRelation(JobIdRelation jobIdRelation) throws Exception {
    return dbOperator.update(UPDATE_JOB_INFO, jobIdRelation.getApplicationId(), jobIdRelation.getJobServerJobId(), jobIdRelation.getProxyUrl(), jobIdRelation.getId());

  }

  @Override
  public int addJobIdRelation(Map<String,String>  jobIdRelation) throws Exception {
    return dbOperator.update(ADD_JOB_ID_RELATION, jobIdRelation.get(JobIdRelation.EXEC_ID), jobIdRelation.get(JobIdRelation.ATTEMPT), jobIdRelation.get(JobIdRelation.JOB_NAME_PATH), jobIdRelation.get(JobIdRelation.JOBSERVER_JOB_ID), jobIdRelation.get(JobIdRelation.APPLICATION_ID), jobIdRelation.get(JobIdRelation.LINKIS_ID), jobIdRelation.get(JobIdRelation.PROXY_URL));
  }

  @Override
  public int updateJobIdRelation(Map<String,String> jobIdRelation, String... params ) throws SQLException {
    String updateSql = String.format(UPDATE_JOB_ID_RELATION, params[0], params[1]);
    return dbOperator.update(updateSql,jobIdRelation.get(JobIdRelation.PROXY_URL), jobIdRelation.get(params[0]),jobIdRelation.get(JobIdRelation.EXEC_ID),jobIdRelation.get(JobIdRelation.ATTEMPT),jobIdRelation.get(JobIdRelation.JOB_NAME_PATH), jobIdRelation.get(params[1]));
  }

  @Override
  public int updateProxyUrl(JobIdRelation jobIdRelation) throws SQLException {
    return dbOperator.update(UPDATE_PROXY_URL, jobIdRelation.getProxyUrl(), jobIdRelation.getExecId(), jobIdRelation.getAttempt(), jobIdRelation.getJobNamePath());
  }

  static class FetchJobIdRelation implements ResultSetHandler<List<JobIdRelation>> {

    @Override
    public List<JobIdRelation> handle(final ResultSet rs) throws SQLException {
      if (!rs.next()) {
        return new ArrayList<>();
      }
      List<JobIdRelation> list = new ArrayList<>();
      do{
        JobIdRelation jobIdRelation = new JobIdRelation(
                rs.getInt(1),
                rs.getInt(2),
                rs.getInt(3),
                rs.getString(4),
                rs.getString(5),
                rs.getString(6),
                rs.getString(8),
                rs.getString(7)
        );
        list.add(jobIdRelation);
      }while (rs.next());
      return list;
    }
  }


}
