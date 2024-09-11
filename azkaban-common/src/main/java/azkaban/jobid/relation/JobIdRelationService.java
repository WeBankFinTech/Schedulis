package azkaban.jobid.relation;

import java.util.List;

public interface JobIdRelationService {

  List<JobIdRelation> getJobIdRelation(Integer execId, String jobNamePath) throws Exception;

  void addJobIdRelation(JobIdRelation jobIdRelation) throws Exception;
}
