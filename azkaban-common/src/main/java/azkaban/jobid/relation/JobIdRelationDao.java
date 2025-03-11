package azkaban.jobid.relation;

import java.sql.SQLException;
import java.util.List;
import java.util.Map;

public interface JobIdRelationDao {

  List<JobIdRelation> getJobIdRelation(Integer execId, String jobNamePath) throws Exception;

  JobIdRelation getJobIdRelation(Integer execId, String jobNamePath, Integer attempt) throws Exception;

  int addJobIdRelation(JobIdRelation jobIdRelation) throws Exception;

  int updateJobIdRelation(JobIdRelation jobIdRelation) throws Exception;

  int addJobIdRelation(Map<String,String> jobIdRelation) throws Exception;

  int updateJobIdRelation(Map<String,String> jobIdRelation, String... params) throws SQLException;

  int updateProxyUrl(JobIdRelation jobIdRelation) throws SQLException;
}
