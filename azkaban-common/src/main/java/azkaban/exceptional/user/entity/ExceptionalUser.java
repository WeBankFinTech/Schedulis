package azkaban.exceptional.user.entity;

public class ExceptionalUser {

  /**
   * 用户ID
   */
  private String userId;

  /**
   * 用户登录名
   */
  private String username;


  /**
   * 用户姓名
   */
  private String fullName;

  /**
   * 部门ID
   */
  private long departmentId;

  /**
   * 部门
   */
  private String departmentName;

  /**
   * 电子邮箱
   */
  private String email;

  /**
   * 创建时间
   */
  private long createTime;

  /**
   * 更新时间
   */
  private long updateTime;

  public ExceptionalUser() {
  }

  public ExceptionalUser(String userId, String username, String fullName, long departmentId, String departmentName, String email, long createTime, long updateTime) {
    this.userId = userId;
    this.username = username;
    this.fullName = fullName;
    this.departmentId = departmentId;
    this.departmentName = departmentName;
    this.email = email;
    this.createTime = createTime;
    this.updateTime = updateTime;
  }

  public ExceptionalUser(String userId, String username, String fullName, long departmentId, String departmentName, String email) {
    this.userId = userId;
    this.username = username;
    this.fullName = fullName;
    this.departmentId = departmentId;
    this.departmentName = departmentName;
    this.email = email;
  }

  public String getUserId() {
    return userId;
  }

  public void setUserId(String userId) {
    this.userId = userId;
  }

  public String getUsername() {
    return username;
  }

  public void setUsername(String username) {
    this.username = username;
  }

  public String getFullName() {
    return fullName;
  }

  public void setFullName(String fullName) {
    this.fullName = fullName;
  }

  public long getDepartmentId() {
    return departmentId;
  }

  public void setDepartmentId(long departmentId) {
    this.departmentId = departmentId;
  }

  public String getDepartmentName() {
    return departmentName;
  }

  public void setDepartmentName(String departmentName) {
    this.departmentName = departmentName;
  }

  public String getEmail() {
    return email;
  }

  public void setEmail(String email) {
    this.email = email;
  }

  public long getCreateTime() {
    return createTime;
  }

  public void setCreateTime(long createTime) {
    this.createTime = createTime;
  }

  public long getUpdateTime() {
    return updateTime;
  }

  public void setUpdateTime(long updateTime) {
    this.updateTime = updateTime;
  }

  @Override
  public String toString() {
    return "ExceptionalUser{" +
        "userId='" + userId + '\'' +
        ", username='" + username + '\'' +
        ", fullName='" + fullName + '\'' +
        ", departmentId=" + departmentId +
        ", departmentName='" + departmentName + '\'' +
        ", email='" + email + '\'' +
        ", createTime=" + createTime +
        ", updateTime=" + updateTime +
        '}';
  }
}
