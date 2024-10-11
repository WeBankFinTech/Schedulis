package azkaban.imsreport;

public class ImsReport {

  private String jobDate;
  private String actionUrl;
  private String subSystemId = "";
  private String dataVersion;
  private String localHost;
  private String dcnNumbers = "";
  private String alertLevel = "2";
  private String status;
  private boolean isDmsPath;
  private String jobCode;
  private String startTime = "";
  private String endTime = "";
  private String executeType;

  public String getJobDate() {
    return jobDate;
  }

  public void setJobDate(String jobDate) {
    this.jobDate = jobDate;
  }

  public String getActionUrl() {
    return actionUrl;
  }

  public void setActionUrl(String actionUrl) {
    this.actionUrl = actionUrl;
  }

  public String getSubSystemId() {
    return subSystemId;
  }

  public void setSubSystemId(String subSystemId) {
    this.subSystemId = subSystemId;
  }

  public String getDataVersion() {
    return dataVersion;
  }

  public void setDataVersion(String dataVersion) {
    this.dataVersion = dataVersion;
  }

  public String getLocalHost() {
    return localHost;
  }

  public void setLocalHost(String localHost) {
    this.localHost = localHost;
  }

  public String getDcnNumbers() {
    return dcnNumbers;
  }

  public void setDcnNumbers(String dcnNumbers) {
    this.dcnNumbers = dcnNumbers;
  }

  public String getAlertLevel() {
    return alertLevel;
  }

  public void setAlertLevel(String alertLevel) {
    this.alertLevel = alertLevel;
  }

  public String getStatus() {
    return status;
  }

  public void setStatus(String status) {
    this.status = status;
  }

  public boolean isDmsPath() {
    return isDmsPath;
  }

  public void setDmsPath(boolean dmsPath) {
    isDmsPath = dmsPath;
  }

  public String getJobCode() {
    return jobCode;
  }

  public void setJobCode(String jobCode) {
    this.jobCode = jobCode;
  }

  public String getStartTime() {
    return startTime;
  }

  public void setStartTime(String startTime) {
    this.startTime = startTime;
  }

  public String getEndTime() {
    return endTime;
  }

  public void setEndTime(String endTime) {
    this.endTime = endTime;
  }

  public String getExecuteType() {
    return executeType;
  }

  public void setExecuteType(String executeType) {
    this.executeType = executeType;
  }
}
