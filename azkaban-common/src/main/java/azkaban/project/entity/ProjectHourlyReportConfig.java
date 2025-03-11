package azkaban.project.entity;

/**
 * @author lebronwang
 * @date 2024/06/02
 **/
public class ProjectHourlyReportConfig {

  private String projectName;
  private String reportWay;
  private String reportReceiver;
  private String submitUser;
  private long submitTime;

  private String overTime;

  public String getOverTime() {
    return overTime;
  }

  public void setOverTime(String overTime) {
    this.overTime = overTime;
  }

  public ProjectHourlyReportConfig() {
  }

  public String getProjectName() {
    return projectName;
  }

  public void setProjectName(String projectName) {
    this.projectName = projectName;
  }

  public String getReportWay() {
    return reportWay;
  }

  public void setReportWay(String reportWay) {
    this.reportWay = reportWay;
  }

  public String getReportReceiver() {
    return reportReceiver;
  }

  public void setReportReceiver(String reportReceiver) {
    this.reportReceiver = reportReceiver;
  }

  public String getSubmitUser() {
    return submitUser;
  }

  public void setSubmitUser(String submitUser) {
    this.submitUser = submitUser;
  }

  public long getSubmitTime() {
    return submitTime;
  }

  public void setSubmitTime(long submitTime) {
    this.submitTime = submitTime;
  }
}
