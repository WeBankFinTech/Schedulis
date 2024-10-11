package azkaban.executor;

public class HistoryQueryParam {

  private String projContain;
  private String flowContain;
  private String execIdContain;
  private String userContain;
  private String status;
  private long startBeginTime;
  private long startEndTime;
  private long finishBeginTime;
  private long finishEndTime;
  private String subsystem;
  private String busPath;
  private String departmentId;
  private String runDateReq;
  private int flowType;
  private String searchType;
  private String fromHomePage;
  private String comment;
  private String jobId;
  private int projectId;
  private String projectName;
  private String flowId;

  /**
   * 外部调用接口兼容旧参数
   */
  private long beginTime;
  private long endTime;

  public String getComment() {
    return comment;
  }

  public void setComment(String comment) {
    this.comment = comment;
  }

  public String getProjContain() {
    return projContain;
  }

  public void setProjContain(String projContain) {
    this.projContain = projContain;
  }

  public String getFlowContain() {
    return flowContain;
  }

  public void setFlowContain(String flowContain) {
    this.flowContain = flowContain;
  }

  public String getExecIdContain() {
    return execIdContain;
  }

  public void setExecIdContain(String execIdContain) {
    this.execIdContain = execIdContain;
  }

  public String getUserContain() {
    return userContain;
  }

  public void setUserContain(String userContain) {
    this.userContain = userContain;
  }

  public String getStatus() {
    return status;
  }

  public void setStatus(String status) {
    this.status = status;
  }

  public long getStartBeginTime() {
    return startBeginTime;
  }

  public void setStartBeginTime(long startBeginTime) {
    this.startBeginTime = startBeginTime;
  }

  public long getStartEndTime() {
    return startEndTime;
  }

  public void setStartEndTime(long startEndTime) {
    this.startEndTime = startEndTime;
  }

  public long getFinishBeginTime() {
    return finishBeginTime;
  }

  public void setFinishBeginTime(long finishBeginTime) {
    this.finishBeginTime = finishBeginTime;
  }

  public long getFinishEndTime() {
    return finishEndTime;
  }

  public void setFinishEndTime(long finishEndTime) {
    this.finishEndTime = finishEndTime;
  }

  public String getSubsystem() {
    return subsystem;
  }

  public void setSubsystem(String subsystem) {
    this.subsystem = subsystem;
  }

  public String getBusPath() {
    return busPath;
  }

  public void setBusPath(String busPath) {
    this.busPath = busPath;
  }

  public String getDepartmentId() {
    return departmentId;
  }

  public void setDepartmentId(String departmentId) {
    this.departmentId = departmentId;
  }

  public String getRunDateReq() {
    return runDateReq;
  }

  public void setRunDateReq(String runDateReq) {
    this.runDateReq = runDateReq;
  }

  public int getFlowType() {
    return flowType;
  }

  public void setFlowType(int flowType) {
    this.flowType = flowType;
  }

  public String getSearchType() {
    return searchType;
  }

  public void setSearchType(String searchType) {
    this.searchType = searchType;
  }

  public String getFromHomePage() {
    return fromHomePage;
  }

  public void setFromHomePage(String fromHomePage) {
    this.fromHomePage = fromHomePage;
  }

  public long getBeginTime() {
    return beginTime;
  }

  public void setBeginTime(long beginTime) {
    this.beginTime = beginTime;
  }

  public long getEndTime() {
    return endTime;
  }

  public void setEndTime(long endTime) {
    this.endTime = endTime;
  }

  public String getJobId() { return jobId; }

  public void setJobId(String jobId) { this.jobId = jobId; }

  public int getProjectId() { return projectId; }

  public void setProjectId(int projectId) { this.projectId = projectId; }

  public String getProjectName() { return projectName; }

  public void setProjectName(String projectName) { this.projectName = projectName; }

  public String getFlowId() { return flowId; }

  public void setFlowId(String flowId) { this.flowId = flowId; }
}
