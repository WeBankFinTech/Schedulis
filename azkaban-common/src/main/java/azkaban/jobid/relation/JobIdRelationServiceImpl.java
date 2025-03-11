package azkaban.jobid.relation;

import azkaban.utils.Props;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.List;
import java.util.Map;

@Singleton
public class JobIdRelationServiceImpl implements JobIdRelationService {


  private static final Logger logger = LoggerFactory.getLogger(JobIdRelationServiceImpl.class);

  private JobIdRelationDao jobIdRelationDao;
  private final Props azkProps;

  @Inject
  public JobIdRelationServiceImpl(JobIdRelationDao jobIdRelationDao, Props azkProps) {
    this.jobIdRelationDao = jobIdRelationDao;
    this.azkProps = azkProps;
  }

  @Override
  public List<JobIdRelation> getJobIdRelation(Integer execId, String jobNamePath) throws Exception {
    return jobIdRelationDao.getJobIdRelation(execId, jobNamePath);
  }

  @Override
  public JobIdRelation getJobIdRelation(Integer execId, String jobNamePath, Integer attempt) throws Exception {
    return jobIdRelationDao.getJobIdRelation(execId, jobNamePath, attempt);
  }

  @Override
  public void addJobIdRelation(JobIdRelation jobIdRelation) throws Exception {
    try {
      jobIdRelationDao.addJobIdRelation(jobIdRelation);
    } catch (Exception e){
      logger.error("add jobIdRelation failed." , e);
    }
  }

  @Override
  public void addJobIdRelation(Map<String, String> jobIdRelation) throws Exception {
    try {
      jobIdRelationDao.addJobIdRelation(jobIdRelation);
    } catch (Exception e){
      logger.error("add jobIdRelation failed." , e);
    }
  }

  @Override
  public void updateJobIdRelation(JobIdRelation jobIdRelation) throws Exception {
    jobIdRelationDao.updateJobIdRelation(jobIdRelation);
  }

  @Override
  public void updateJobIdRelation(Map<String,String> jobIdRelation, String... params) {
    try {
      jobIdRelationDao.updateJobIdRelation(jobIdRelation, params);
    } catch (Exception e) {
      logger.error("update jobIdRelation failed." , e);
    }
  }

  @Override
  public void updateProxyUrl(JobIdRelation jobIdRelation) {
    try {
      jobIdRelationDao.updateProxyUrl(jobIdRelation);
    } catch (Exception e){
      logger.error("update proxyUrl failed." , e);
    }
  }
}
