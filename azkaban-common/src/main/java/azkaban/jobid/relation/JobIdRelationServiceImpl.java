package azkaban.jobid.relation;

import azkaban.utils.Props;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.List;

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
  public void addJobIdRelation(JobIdRelation jobIdRelation) throws Exception {
    try {
      jobIdRelationDao.addJobIdRelation(jobIdRelation);
    } catch (Exception e){
      logger.error("add jobIdRelation failed." , e);
    }
  }
}
