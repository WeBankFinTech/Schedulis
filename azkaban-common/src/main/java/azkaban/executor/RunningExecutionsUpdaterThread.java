/*
 * Copyright 2018 LinkedIn Corp.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */

package azkaban.executor;

import org.slf4j.LoggerFactory;
import org.slf4j.Logger;

import javax.inject.Inject;

/**
 * Updates running executions periodically.
 */
public class RunningExecutionsUpdaterThread extends Thread {

  private static final Logger logger = LoggerFactory.getLogger(RunningExecutionsUpdaterThread.class);

  volatile int waitTimeIdleMs = 2000;
  volatile int waitTimeMs = 500;

  private final RunningExecutionsUpdater updater;
  private final RunningExecutions runningExecutions;
  private long lastThreadCheckTime = -1;
  private boolean shutdown = false;

  @Inject
  public RunningExecutionsUpdaterThread(final RunningExecutionsUpdater updater,
      final RunningExecutions runningExecutions) {
    this.updater = updater;
    this.runningExecutions = runningExecutions;
    this.setName("ExecutorManagerUpdaterThread");
  }

  /**
   * Start the thread: updates running executions periodically.
   */
  @Override
  @SuppressWarnings("unchecked")
  public void run() {
    while (!this.shutdown) {
      try {
        this.lastThreadCheckTime = System.currentTimeMillis();
        this.updater.updateExecutions();
        // TODO not sure why it would be important to check the status immediately in case of _new_
        // executions. This can only optimize finalizing executions that finish super-quickly after
        // being started.
        waitForNewExecutions();
      } catch (final Exception e) {
        logger.error("Unexpected exception in updating executions", e);
      }
    }
  }

  private void waitForNewExecutions() {
    synchronized (this.runningExecutions.get()) {
      try {
        final int waitTimeMillis =
            this.runningExecutions.get().size() > 0 ? this.waitTimeMs : this.waitTimeIdleMs;
        if(this.runningExecutions.get().size() == 0){
          this.updater.getAlerterHolder().getFlowAlerterFlag().clear();
        }
        if (waitTimeMillis > 0) {
          this.runningExecutions.get().wait(waitTimeMillis);
        }
      } catch (final InterruptedException e) {
        logger.error("InterruptedException in wait for new executions", e);
      }
    }
  }

  // FIXME change this method access as public type in order to outside package object can call this method.
  public void shutdown() {
    this.shutdown = true;
  }

  public long getLastThreadCheckTime() {
    return this.lastThreadCheckTime;
  }

}
