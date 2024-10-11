package azkaban.executor;

import java.sql.Timestamp;
import java.util.Objects;

public class DmsBusPath {

  private String busPathId;
  private String busPathName;
  private String jobCode;
  private String status;
  private String maintainMethod;
  private String maintainer;
  private Timestamp createdTime;
  private Timestamp modifiedTime;
  private String nodeEntrance;

  public String getBusPathId() {
    return busPathId;
  }

  public void setBusPathId(String busPathId) {
    this.busPathId = busPathId;
  }

  public String getBusPathName() {
    return busPathName;
  }

  public void setBusPathName(String busPathName) {
    this.busPathName = busPathName;
  }

  public String getJobCode() {
    return jobCode;
  }

  public void setJobCode(String jobCode) {
    this.jobCode = jobCode;
  }

  public String getStatus() {
    return status;
  }

  public void setStatus(String status) {
    this.status = status;
  }

  public String getMaintainMethod() {
    return maintainMethod;
  }

  public void setMaintainMethod(String maintainMethod) {
    this.maintainMethod = maintainMethod;
  }

  public String getMaintainer() {
    return maintainer;
  }

  public void setMaintainer(String maintainer) {
    this.maintainer = maintainer;
  }

  public Timestamp getCreatedTime() {
    return createdTime;
  }

  public void setCreatedTime(Timestamp createdTime) {
    this.createdTime = createdTime;
  }

  public Timestamp getModifiedTime() {
    return modifiedTime;
  }

  public void setModifiedTime(Timestamp modifiedTime) {
    this.modifiedTime = modifiedTime;
  }

  public String getNodeEntrance() {
    return nodeEntrance;
  }

  public void setNodeEntrance(String nodeEntrance) {
    this.nodeEntrance = nodeEntrance;
  }

  public static String createJobCode(String jobCodePrefix, String projectName, String flowId) {
    return jobCodePrefix + "/" + projectName.toLowerCase() + "/" + flowId.toLowerCase();
  }

  public static String createJobCode(String jobCodePrefix, String projectName, String flowId,
      String jobId) {
    return jobCodePrefix + "/" + projectName.toLowerCase() + "/" + flowId.toLowerCase() + "/"
        + jobId.toLowerCase();
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (!(o instanceof DmsBusPath)) {
      return false;
    }
    DmsBusPath that = (DmsBusPath) o;
    return Objects.equals(getBusPathName(), that.getBusPathName()) && getJobCode().equals(that.getJobCode());
  }

  @Override
  public int hashCode() {
    return Objects.hash(getBusPathName(), getJobCode());
  }
}
