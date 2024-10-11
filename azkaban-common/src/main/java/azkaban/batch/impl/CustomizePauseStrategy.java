package azkaban.batch.impl;

import azkaban.batch.AbstractPauseBatchStrategy;
import azkaban.batch.HoldBatchAlert;
import azkaban.scheduler.Schedule;
import azkaban.scheduler.ScheduleManager;

import java.util.List;
import java.util.stream.Collectors;

public class CustomizePauseStrategy extends AbstractPauseBatchStrategy {

  @Override
  public boolean isExistBatch(AbstractPauseBatchStrategy pauseBatchStrategy) {
    return pauseBatchStrategy.getCustomizeList().stream().anyMatch(d -> this.dataList.contains(d));
  }

  @Override
  public void fillList(ScheduleManager scheduleManager) throws Exception {
    this.customizeList = this.dataList;
    List<Schedule> scheduleList = scheduleManager.getSchedules();
    this.userList.addAll(scheduleList.stream()
        .filter(s -> this.customizeList.contains(s.getProjectName() + "-" + s.getFlowName()))
        .map(s -> s.getSubmitUser()).collect(Collectors.toList()));
    this.tenantList = this.holdBatchDao.queryTenantListByUser(this.userList.stream().collect(
        Collectors.joining(",")));
  }

  @Override
  public boolean isInBatch(String project, String flow, String submitUser) {
    return this.dataList.contains(project + "-" + flow);
  }

  @Override
  public boolean isNotResume(HoldBatchAlert holdBatchAlert) {
    return !this.dataList
        .contains(holdBatchAlert.getProjectName() + "-" + holdBatchAlert.getFlowName());
  }
}
