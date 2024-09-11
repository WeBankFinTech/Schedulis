package azkaban.batch.impl;

import azkaban.batch.AbstractPauseBatchStrategy;
import azkaban.batch.HoldBatchAlert;
import azkaban.scheduler.ScheduleManager;

public class OverallPauseStrategy extends AbstractPauseBatchStrategy {

  @Override
  public boolean isExistBatch(AbstractPauseBatchStrategy pauseBatchStrategy) {
    return true;
  }

  @Override
  public void fillList(ScheduleManager scheduleManager) throws Exception {

  }

  @Override
  public boolean isInBatch(String project, String flow, String submitUser) {
    return !this.isInWhiteList(project, flow);
  }

  @Override
  public boolean isNotResume(HoldBatchAlert holdBatchAlert) {
    return false;
  }
}
