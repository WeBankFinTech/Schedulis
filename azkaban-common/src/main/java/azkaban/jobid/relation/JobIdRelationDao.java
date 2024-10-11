package azkaban.jobid.relation;

import java.util.List;

public interface JobIdRelationDao {

  List<JobIdRelation> getJobIdRelation(Integer execId, String jobNamePath) throws Exception;

  int addJobIdRelation(JobIdRelation jobIdRelation) throws Exception;
}
