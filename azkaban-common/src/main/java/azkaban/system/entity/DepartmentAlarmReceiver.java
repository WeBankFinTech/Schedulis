package azkaban.system.entity;

/**
 * @author lebronwang
 * @date 2024/06/11
 **/
public class DepartmentAlarmReceiver {

  private int departmentId;
  private String departmentName;

  private String receiver;

  public DepartmentAlarmReceiver() {
  }

  public int getDepartmentId() {
    return departmentId;
  }

  public void setDepartmentId(int departmentId) {
    this.departmentId = departmentId;
  }

  public String getDepartmentName() {
    return departmentName;
  }

  public void setDepartmentName(String departmentName) {
    this.departmentName = departmentName;
  }

  public String getReceiver() {
    return receiver;
  }

  public void setReceiver(String receiver) {
    this.receiver = receiver;
  }
}
