package azkaban.execapp;

import azkaban.executor.Status;
import org.slf4j.LoggerFactory;

import java.util.Set;

import static azkaban.Constants.FAILED_PAUSED_CHECK_TIME_MS;
import static azkaban.Constants.FAILED_PAUSED_MAX_WAIT_MS;

/**
 * Created by v_wbkefan on 2019/7/24.
 */
public class KillFlowTrigger extends Thread {

    private static final org.slf4j.Logger LOGGER = LoggerFactory.getLogger(KillFlowTrigger.class);

    private final org.slf4j.Logger flowLogger;
    private FlowRunner flowRunner;
    private long timeOut;
    private final Object monitor = new Object();
    private long waitTime;
    private long interval;

    public KillFlowTrigger(FlowRunner flowRunner, org.slf4j.Logger flowLogger) {
        this.flowRunner = flowRunner;
        this.interval = flowRunner.getAzkabanProps().getLong(FAILED_PAUSED_MAX_WAIT_MS, 1 * 60 * 60 * 1000);
        this.waitTime = flowRunner.getAzkabanProps().getLong(FAILED_PAUSED_CHECK_TIME_MS, 1 * 60 * 1000);
        this.timeOut = System.currentTimeMillis() + this.interval;
        this.flowLogger = flowLogger;
    }

    private void updateTime(){
        this.timeOut = System.currentTimeMillis() + interval;
    }

    @Override
    public void run() {
        flowLogger.warn("作业流已暂停， 当作业流没有可运行任务时，在" + this.interval / 1000 + "秒后该作业将会被终止运行.");
        try {
            while (true) {
                synchronized (monitor) {
                    if (hasActiveJob()) {
                        LOGGER.debug("execId：{}, has active job , update time." , flowRunner.getExecutionId());
                        updateTime();
                    }
                    if (System.currentTimeMillis() >= this.timeOut) {
                        if (flowRunner != null && !flowRunner.isKilled() && !flowRunner.isFlowFinished()) {
                            flowLogger.warn("workflow timeout termination, execId:" + this.flowRunner.getExecutionId());
                            flowRunner.kill();
                        }
                        break;
                    }
                    monitor.wait(waitTime);
                }
            }
        } catch (InterruptedException ie) {
            flowLogger.info("cancel kill workflow, execId: " + this.flowRunner.getExecutionId());
        }
    }

    public void stopKillFLowTrigger(){
        this.interrupt();
    }

    private boolean hasActiveJob(){
        Set<JobRunner> activeJobs = flowRunner.getActiveJobRunners();
        if(activeJobs.isEmpty()){
            return false;
        }
        for(JobRunner jobRunner: activeJobs){
            if(!jobRunner.getStatus().equals(Status.FAILED_WAITING)){
                return true;
            }
        }
        return false;
    }

}
