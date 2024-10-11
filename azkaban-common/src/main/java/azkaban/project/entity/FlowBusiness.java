package azkaban.project.entity;

/**
 * 工作流应用信息
 */
public class FlowBusiness {

  /**
   * 项目id
   */
  private Integer projectId;
  /**
   * 工作流名称
   */
  private String flowId;
  /**
   * job名称
   */
  private String jobId;
  /**
   * 业务/产品一级分类
   */
  private String busTypeFirst;
  /**
   * 业务/产品二级分类
   */
  private String busTypeSecond;
  /**
   * 业务描述
   */
  private String busDesc;
  /**
   * 子系统
   */
  private String subsystem;
  /**
   * 业务恢复级别
   */
  private String busResLvl;
  /**
   * 关键路径
   */
  private String busPath;
  /**
   * 批量关键时间段
   */
  private String batchTimeQuat;
  /**
   * 业务故障影响
   */
  private String busErrInf;
  /**
   * 开发科室
   */
  private String devDept;
  /**
   * 运维科室
   */
  private String opsDept;
  /**
   * 上游依赖方
   */
  private String upperDep;
  /**
   * 下游依赖方
   */
  private String lowerDep;
  /**
   * 创建人
   */
  private String createUser;
  /**
   * 创建时间
   */
  private long createTime;
  /**
   * 修改人
   */
  private String updateUser;
  /**
   * 修改时间
   */
  private long updateTime;
  /**
   * 数据级别 1-项目 2-工作流
   */
  private String dataLevel;

  /**
   * 关键批量分组
   */
  private String batchGroup;

  /**
   * 业务域
   */
  private String busDomain;

  /**
   * 项目名
   */
  private String projectName;

  /**
   * 最早开始时间
   */
  private String earliestStartTime;

  /**
   * 最晚结束时间
   */
  private String latestEndTime;

  /**
   * 关联一级产品/二级产品（非必选）
   */
  private String relatedProduct;

  /**
   * 计划开始时间
   */
  private String planStartTime;

  /**
   * 计划结束时间
   */
  private String planFinishTime;

  /**
   * 最迟开始时间
   */
  private String lastStartTime;

  /**
   * 最迟结束时间
   */
  private String lastFinishTime;

  /**
   * 告警级别
   */
  private String alertLevel;

  /**
   * DCN编号
   */
  private String dcnNumber;

  /**
   * 注册人信息
   */
  private String imsUpdater;

  /**
   * 告警备注信息
   */
  private String imsRemark;

  private String batchGroupDesc;

  private String busPathDesc;

  private String busTypeFirstDesc;

  private String busTypeSecondDesc;

  private String subsystemDesc;

  public Integer getProjectId() {
    return projectId;
  }

  public void setProjectId(Integer projectId) {
    this.projectId = projectId;
  }

  public String getFlowId() {
    return flowId;
  }

  public void setFlowId(String flowId) {
    this.flowId = flowId;
  }

  public String getJobId() {
    return jobId;
  }

  public void setJobId(String jobId) {
    this.jobId = jobId;
  }

  public String getBusTypeFirst() {
    return busTypeFirst;
  }

  public void setBusTypeFirst(String busTypeFirst) {
    this.busTypeFirst = busTypeFirst;
  }

  public String getBusTypeSecond() {
    return busTypeSecond;
  }

  public void setBusTypeSecond(String busTypeSecond) {
    this.busTypeSecond = busTypeSecond;
  }

  public String getBusDesc() {
    return busDesc;
  }

  public void setBusDesc(String busDesc) {
    this.busDesc = busDesc;
  }

  public String getSubsystem() {
    return subsystem;
  }

  public void setSubsystem(String subsystem) {
    this.subsystem = subsystem;
  }

  public String getBusResLvl() {
    return busResLvl;
  }

  public void setBusResLvl(String busResLvl) {
    this.busResLvl = busResLvl;
  }

  public String getBusPath() {
    return busPath;
  }

  public void setBusPath(String busPath) {
    this.busPath = busPath;
  }

  public String getBatchTimeQuat() {
    return batchTimeQuat;
  }

  public void setBatchTimeQuat(String batchTimeQuat) {
    this.batchTimeQuat = batchTimeQuat;
  }

  public String getBusErrInf() {
    return busErrInf;
  }

  public void setBusErrInf(String busErrInf) {
    this.busErrInf = busErrInf;
  }

  public String getDevDept() {
    return devDept;
  }

  public void setDevDept(String devDept) {
    this.devDept = devDept;
  }

  public String getOpsDept() {
    return opsDept;
  }

  public void setOpsDept(String opsDept) {
    this.opsDept = opsDept;
  }

  public String getUpperDep() {
    return upperDep;
  }

  public void setUpperDep(String upperDep) {
    this.upperDep = upperDep;
  }

  public String getLowerDep() {
    return lowerDep;
  }

  public void setLowerDep(String lowerDep) {
    this.lowerDep = lowerDep;
  }

  public String getCreateUser() {
    return createUser;
  }

  public void setCreateUser(String createUser) {
    this.createUser = createUser;
  }

  public long getCreateTime() {
    return createTime;
  }

  public void setCreateTime(long createTime) {
    this.createTime = createTime;
  }

  public String getUpdateUser() {
    return updateUser;
  }

  public void setUpdateUser(String updateUser) {
    this.updateUser = updateUser;
  }

  public long getUpdateTime() {
    return updateTime;
  }

  public void setUpdateTime(long updateTime) {
    this.updateTime = updateTime;
  }

  public String getDataLevel() {
    return dataLevel;
  }

  public void setDataLevel(String dataLevel) {
    this.dataLevel = dataLevel;
  }

  public String getBatchGroup() {
    return batchGroup;
  }

  public void setBatchGroup(String batchGroup) {
    this.batchGroup = batchGroup;
  }

  public String getBusDomain() {
    return busDomain;
  }

  public void setBusDomain(String busDomain) {
    this.busDomain = busDomain;
  }

  public String getProjectName() {
    return projectName;
  }

  public void setProjectName(String projectName) {
    this.projectName = projectName;
  }

  public String getEarliestStartTime() {
    return earliestStartTime;
  }

  public void setEarliestStartTime(String earliestStartTime) {
    this.earliestStartTime = earliestStartTime;
  }

  public String getLatestEndTime() {
    return latestEndTime;
  }

  public void setLatestEndTime(String latestEndTime) {
    this.latestEndTime = latestEndTime;
  }

  public String getRelatedProduct() {
    return relatedProduct;
  }

  public void setRelatedProduct(String relatedProduct) {
    this.relatedProduct = relatedProduct;
  }

  public String getPlanStartTime() {
    return planStartTime;
  }

  public void setPlanStartTime(String planStartTime) {
    this.planStartTime = planStartTime;
  }

  public String getPlanFinishTime() {
    return planFinishTime;
  }

  public void setPlanFinishTime(String planFinishTime) {
    this.planFinishTime = planFinishTime;
  }

  public String getLastStartTime() {
    return lastStartTime;
  }

  public void setLastStartTime(String lastStartTime) {
    this.lastStartTime = lastStartTime;
  }

  public String getLastFinishTime() {
    return lastFinishTime;
  }

  public void setLastFinishTime(String lastFinishTime) {
    this.lastFinishTime = lastFinishTime;
  }

  public String getAlertLevel() {
    return alertLevel;
  }

  public void setAlertLevel(String alertLevel) {
    this.alertLevel = alertLevel;
  }

  public String getDcnNumber() {
    return dcnNumber;
  }

  public void setDcnNumber(String dcnNumber) {
    this.dcnNumber = dcnNumber;
  }

  public String getImsUpdater() {
    return imsUpdater;
  }

  public void setImsUpdater(String imsUpdater) {
    this.imsUpdater = imsUpdater;
  }

  public String getImsRemark() {
    return imsRemark;
  }

  public void setImsRemark(String imsRemark) {
    this.imsRemark = imsRemark;
  }

  public String getBatchGroupDesc() {
    return batchGroupDesc;
  }

  public void setBatchGroupDesc(String batchGroupDesc) {
    this.batchGroupDesc = batchGroupDesc;
  }

  public String getBusPathDesc() {
    return busPathDesc;
  }

  public void setBusPathDesc(String busPathDesc) {
    this.busPathDesc = busPathDesc;
  }

  public String getBusTypeFirstDesc() {
    return busTypeFirstDesc;
  }

  public void setBusTypeFirstDesc(String busTypeFirstDesc) {
    this.busTypeFirstDesc = busTypeFirstDesc;
  }

  public String getBusTypeSecondDesc() {
    return busTypeSecondDesc;
  }

  public void setBusTypeSecondDesc(String busTypeSecondDesc) {
    this.busTypeSecondDesc = busTypeSecondDesc;
  }

  public String getSubsystemDesc() {
    return subsystemDesc;
  }

  public void setSubsystemDesc(String subsystemDesc) {
    this.subsystemDesc = subsystemDesc;
  }
}
