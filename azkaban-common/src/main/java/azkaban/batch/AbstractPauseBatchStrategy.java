package azkaban.batch;

import azkaban.ServiceProvider;
import azkaban.scheduler.ScheduleManager;

import java.util.ArrayList;
import java.util.List;

public abstract class AbstractPauseBatchStrategy {

  protected List<String> dataList = new ArrayList<>();
  protected List<String> pathWhiteList = new ArrayList<>();
  protected List<String> flowWhiteList = new ArrayList<>();
  protected List<String> criticalPathList = new ArrayList<>();
  protected List<String> flowBlackList = new ArrayList<>();
  protected List<String> tenantList = new ArrayList<>();
  protected List<String> userList = new ArrayList<>();
  protected List<String> customizeList = new ArrayList<>();
  protected HoldBatchDao holdBatchDao = ServiceProvider.SERVICE_PROVIDER
      .getInstance(HoldBatchDao.class);


  public abstract boolean isExistBatch(AbstractPauseBatchStrategy pauseBatchStrategy);

  public abstract void fillList(ScheduleManager scheduleManager) throws Exception;

  public abstract boolean isInBatch(String project, String flow, String submitUser);

  public boolean isBlack(HoldBatchAlert holdBatchAlert) {
    return this.flowBlackList
        .contains(holdBatchAlert.getProjectName() + "-" + holdBatchAlert.getFlowName());
  }

  protected boolean isInWhiteList(String project, String flow) {
    return this.flowWhiteList.contains(project + "-" + flow) || this.pathWhiteList
            .contains(this.holdBatchDao.getFlowBusPath(project, flow)) || this.criticalPathList.contains(this.holdBatchDao.getCriticalPath(project, flow));
  }

  public abstract boolean isNotResume(HoldBatchAlert holdBatchAlert);

  public List<String> getTenantList() {
    return tenantList;
  }

  public List<String> getUserList() {
    return userList;
  }

  public List<String> getCustomizeList() {
    return customizeList;
  }

  public List<String> getDataList() {
    return dataList;
  }
}
