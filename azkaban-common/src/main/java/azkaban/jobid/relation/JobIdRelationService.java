package azkaban.jobid.relation;

import java.util.List;
import java.util.Map;

public interface JobIdRelationService {

  List<JobIdRelation> getJobIdRelation(Integer execId, String jobNamePath) throws Exception;

  JobIdRelation getJobIdRelation(Integer execId, String jobNamePath, Integer attempt) throws Exception;
  void addJobIdRelation(JobIdRelation jobIdRelation) throws Exception;

  void updateJobIdRelation(JobIdRelation jobIdRelation) throws Exception;

  void addJobIdRelation(Map<String,String> jobIdRelation) throws Exception;

  void updateJobIdRelation(Map<String,String> jobIdRelation, String... params);

  void updateProxyUrl(JobIdRelation jobIdRelation);
}
