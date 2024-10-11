package azkaban.batch;

import azkaban.executor.ExecutableFlow;

public class HoldBatchAlert {

  private long id;
  private String batchId;
  private String projectName;
  private String flowName;
  private int execId = -1;
  private String createUser;
  private long createTime;
  private int sendStatus;
  private long sendTime;
  private int isResume;
  private long resumeTime;
  private int isBlack;
  private String flowData;
  private ExecutableFlow executableFlow;

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

  public int getExecId() {
    return execId;
  }

  public void setExecId(int execId) {
    this.execId = execId;
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

  public int isResume() {
    return isResume;
  }

  public void setResume(int resume) {
    isResume = resume;
  }

  public long getResumeTime() {
    return resumeTime;
  }

  public void setResumeTime(long resumeTime) {
    this.resumeTime = resumeTime;
  }

  public int isBlack() {
    return isBlack;
  }

  public void setBlack(int black) {
    isBlack = black;
  }

  public String getFlowData() {
    return flowData;
  }

  public void setFlowData(String flowData) {
    this.flowData = flowData;
  }

  public ExecutableFlow getExecutableFlow() {
    return executableFlow;
  }

  public void setExecutableFlow(ExecutableFlow executableFlow) {
    this.executableFlow = executableFlow;
  }

}
