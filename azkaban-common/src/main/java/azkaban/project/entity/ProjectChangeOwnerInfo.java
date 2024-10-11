package azkaban.project.entity;

/**
 * 项目交接相关信息
 *
 * @author lebronwang
 * @date 2022/10/12
 **/
public class ProjectChangeOwnerInfo {

  /**
   * ITSM 服务请求单号
   */
  private long itsmNo;

  /**
   * 项目交接状态： 1 - 已提交审批 2 - 交接失败 3 - 已交接
   */
  private int status;

  /**
   * 项目交接人
   */
  private String newOwner;

  /**
   * 提交人
   */
  private String submitUser;

  /**
   * 提交时间
   */
  private long submitTime;

  public ProjectChangeOwnerInfo() {
  }

  public long getItsmNo() {
    return itsmNo;
  }

  public void setItsmNo(long itsmNo) {
    this.itsmNo = itsmNo;
  }

  public int getStatus() {
    return status;
  }

  public void setStatus(int status) {
    this.status = status;
  }

  public String getNewOwner() {
    return newOwner;
  }

  public void setNewOwner(String newOwner) {
    this.newOwner = newOwner;
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
