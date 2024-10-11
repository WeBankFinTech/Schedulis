package azkaban.batch;

public class HoldBatchFrequent {

  private long id;
  private String batchId;
  private String projectName;
  private String flowName;
  private String submitUser;
  private int sendStatus;
  private long sendTime;

  public long getId() {
    return id;
  }

  public void setId(long id) {
    this.id = id;
  }

  public String getBatchId() {
    return batchId;
  }

  public void setBatchId(String batchId) {
    this.batchId = batchId;
  }

  public String getProjectName() {
    return projectName;
  }

  public void setProjectName(String projectName) {
    this.projectName = projectName;
  }

  public String getFlowName() {
    return flowName;
  }

  public void setFlowName(String flowName) {
    this.flowName = flowName;
  }

  public String getSubmitUser() {
    return submitUser;
  }

  public void setSubmitUser(String submitUser) {
    this.submitUser = submitUser;
  }

  public int getSendStatus() {
    return sendStatus;
  }

  public void setSendStatus(int sendStatus) {
    this.sendStatus = sendStatus;
  }

  public long getSendTime() {
    return sendTime;
  }

  public void setSendTime(long sendTime) {
    this.sendTime = sendTime;
  }
}
