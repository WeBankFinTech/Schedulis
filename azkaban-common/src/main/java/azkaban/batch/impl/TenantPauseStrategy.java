package azkaban.batch.impl;

import azkaban.batch.AbstractPauseBatchStrategy;
import azkaban.batch.HoldBatchAlert;
import azkaban.executor.ExecutorManagerException;
import azkaban.scheduler.Schedule;
import azkaban.scheduler.ScheduleManager;
import org.apache.commons.collections.CollectionUtils;

import java.util.List;
import java.util.stream.Collectors;

public class TenantPauseStrategy extends AbstractPauseBatchStrategy {

  @Override
  public boolean isExistBatch(AbstractPauseBatchStrategy pauseBatchStrategy) {
    return pauseBatchStrategy.getTenantList().stream().anyMatch(d -> this.dataList.contains(d));
  }

  @Override
  public void fillList(ScheduleManager scheduleManager) throws Exception {
    this.tenantList = dataList;
    this.userList = this.holdBatchDao.queryUserListByTenant(this.tenantList.stream().collect(
        Collectors.joining(",")));
    List<Schedule> scheduleList = scheduleManager.getSchedules();
    this.customizeList.addAll(
        scheduleList.stream().filter(s -> this.userList.contains(s.getSubmitUser()))
            .map(s -> s.getProjectName() + "-" + s.getFlowName()).collect(Collectors.toList()));
  }

  @Override
  public boolean isInBatch(String project, String flow, String submitUser) {
    try {
      if (this.isInWhiteList(project, flow)) {
        return false;
      }
      List<String> groupList = this.holdBatchDao.queryTenantListByUser(submitUser);
      if (CollectionUtils.isEmpty(groupList)) {
        return false;
      }
      return this.dataList.contains(groupList.get(0));
    } catch (ExecutorManagerException e) {
      return false;
    }
  }

  @Override
  public boolean isNotResume(HoldBatchAlert holdBatchAlert) {
    String group = "";
    try {
      group = this.holdBatchDao.queryTenantListByUser(holdBatchAlert.getCreateUser()).get(0);
    } catch (ExecutorManagerException e) {
      return false;
    }
    return !this.dataList.contains(group);
  }
}
