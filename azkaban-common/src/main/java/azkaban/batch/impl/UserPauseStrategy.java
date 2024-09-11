package azkaban.batch.impl;

import azkaban.batch.AbstractPauseBatchStrategy;
import azkaban.batch.HoldBatchAlert;
import azkaban.scheduler.Schedule;
import azkaban.scheduler.ScheduleManager;

import java.util.List;
import java.util.stream.Collectors;

public class UserPauseStrategy extends AbstractPauseBatchStrategy {

  @Override
  public boolean isExistBatch(AbstractPauseBatchStrategy pauseBatchStrategy) {
    return pauseBatchStrategy.getUserList().stream().anyMatch(d -> this.dataList.contains(d));
  }

  @Override
  public void fillList(ScheduleManager scheduleManager) throws Exception {
    this.userList = dataList;
    this.tenantList = this.holdBatchDao.queryTenantListByUser(this.userList.stream().collect(
        Collectors.joining(",")));
    List<Schedule> scheduleList = scheduleManager.getSchedules();
    this.customizeList.addAll(
        scheduleList.stream().filter(s -> this.userList.contains(s.getSubmitUser()))
            .map(s -> s.getProjectName() + "-" + s.getFlowName()).collect(Collectors.toList()));
  }

  @Override
  public boolean isInBatch(String project, String flow, String submitUser) {
    if (this.isInWhiteList(project, flow)) {
      return false;
    }
    return this.dataList.contains(submitUser);
  }

  @Override
  public boolean isNotResume(HoldBatchAlert holdBatchAlert) {
    return !this.dataList.contains(holdBatchAlert.getCreateUser());
  }
}
