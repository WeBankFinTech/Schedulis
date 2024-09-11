package azkaban.execapp;

import azkaban.executor.Status;
import azkaban.jobid.relation.JobIdRelation;
import azkaban.jobid.relation.JobIdRelationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Singleton;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import static azkaban.ServiceProvider.SERVICE_PROVIDER;

@Singleton
public class LinkisJobUpdater {

    private static final Logger logger = LoggerFactory.getLogger(LinkisJobUpdater.class);

    public static LinkedBlockingQueue<JobRunner> linkisJobQueue = new LinkedBlockingQueue<>();

    private ExecutorService executorService;

    public LinkisJobUpdater() {
        executorService  = createThreadPool();
        updateLinkisId(executorService);
    }

    private void updateLinkisId(ExecutorService executorService) {
        logger.info("linkis job updater running");
        for (int i = 0; i < 10; i++) {
            executorService.execute(() -> {
                while (true) {
                    JobRunner jobRunner = null;
                    try {
                        jobRunner = linkisJobQueue.take();
                        Thread.currentThread().setName(
                                "LinkisJobUpdater-" + jobRunner.getExecutionId() + "-" + jobRunner.getJobId());
                        String linkisId = ((azkaban.jobExecutor.AbstractJob) jobRunner.getJob()).getLinkisId();
                        if (org.apache.commons.lang3.StringUtils.isNotEmpty(linkisId)) {
                            logger.info("task id : " + linkisId);
                            uploadJobIdRelation(jobRunner);
                        }else if (Status.RUNNING == jobRunner.getNode().getStatus() && org.apache.commons.lang3.StringUtils.isEmpty(linkisId)) {
                            linkisJobQueue.put(jobRunner);
                        }
                    } catch (Exception e) {
                        logger.error("update linkis Id failed", e);
                    }
                }
            });
        }
    }

    private ExecutorService createThreadPool() {
        ExecutorService executorService = new ThreadPoolExecutor(10, 10,
                0L, TimeUnit.MILLISECONDS,
                new LinkedBlockingQueue<>(10 * 30));
        return executorService;
    }


    private static void uploadJobIdRelation(JobRunner jobRunner){
        JobIdRelationService jobIdRelationService = SERVICE_PROVIDER.getInstance(JobIdRelationService.class);
        try {
            JobIdRelation jobIdRelation = new JobIdRelation();
            jobIdRelation.setExecId(jobRunner.getNode().getParentFlow().getExecutionId());
            jobIdRelation.setAttempt(jobRunner.getNode().getAttempt());
            jobIdRelation.setJobNamePath(jobRunner.getNode().getNestedId());
            jobIdRelation.setLinkisId(((azkaban.jobExecutor.AbstractJob)jobRunner.getJob()).getLinkisId());
            jobIdRelation.setJobServerJobId("");
            jobIdRelation.setApplicationId("");
            jobIdRelation.setProxyUrl("");
            jobIdRelationService.addJobIdRelation(jobIdRelation);
        } catch (Exception e) {
            logger.error("update linkisId failed.", e);
        }
    }

    public void shutDown() {
        this.executorService.shutdown();
    }

}

