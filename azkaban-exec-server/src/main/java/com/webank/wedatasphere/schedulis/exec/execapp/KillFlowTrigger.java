/*
 * Copyright 2020 WeBank
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.webank.wedatasphere.schedulis.exec.execapp;

import azkaban.execapp.FlowRunner;
import azkaban.execapp.JobRunner;
import azkaban.executor.Status;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Set;

import static azkaban.Constants.FAILED_PAUSED_CHECK_TIME_MS;
import static azkaban.Constants.FAILED_PAUSED_MAX_WAIT_MS;

public class KillFlowTrigger extends Thread {

    private static final Logger LOGGER = LoggerFactory.getLogger(KillFlowTrigger.class);

    private final Logger logger;
    private FlowRunner flowRunner;
    private long timeOut;
    private final Object monitor = new Object();
    private long waitTime;
    private long interval;

    public KillFlowTrigger(FlowRunner flowRunner, Logger logger) {
        this.flowRunner = flowRunner;
        this.interval = flowRunner.getAzkabanProps().getLong(FAILED_PAUSED_MAX_WAIT_MS, 1 * 60 * 60 * 1000);
        this.waitTime = flowRunner.getAzkabanProps().getLong(FAILED_PAUSED_CHECK_TIME_MS, 1 * 60 * 1000);
        this.timeOut = System.currentTimeMillis() + this.interval;
        this.logger = logger;
    }

    private void updateTime(){
        this.timeOut = System.currentTimeMillis() + interval;
    }

    @Override
    public void run() {
        logger.warn("作业流已暂停， 当作业流没有可运行任务时，在" + this.interval / 1000 + "秒后该作业将会被终止运行.");
        try {
            while (true) {
                synchronized (monitor) {
                    if (hasActiveJob()) {
                        LOGGER.debug("execId：{}, has active job , update time." , flowRunner.getExecutionId());
                        updateTime();
                    }
                    if (System.currentTimeMillis() >= this.timeOut) {
                        if (flowRunner != null && !flowRunner.isKilled() && !flowRunner.isFlowFinished()) {
                            logger.warn("workflow timeout termination, execId:" + this.flowRunner.getExecutionId());
                            flowRunner.kill();
                        }
                        break;
                    }
                    monitor.wait(waitTime);
                }
            }
        } catch (InterruptedException ie) {
            logger.info("cancel kill workflow, execId: " + this.flowRunner.getExecutionId());
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
