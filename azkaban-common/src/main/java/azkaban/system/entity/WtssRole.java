package azkaban.system.entity;

/**
 * Created by zhu on 7/11/18.
 */
public class WtssRole {

  /**
   * 角色ID
   */
  private int roleId;

  /**
   * 角色名称
   */
  private String roleName;

  /**
   * 角色权限
   */
  private String permissionsIds;

  /**
   * 角色说明
   */
  private String description;

  /**
   * 创建时间
   */
  private long createTime;

  /**
   * 更新时间
   */
  private long updateTime;

  public int getRoleId() {
    return roleId;
  }

  public void setRoleId(int roleId) {
    this.roleId = roleId;
  }

  public String getRoleName() {
    return roleName;
  }

  public void setRoleName(String roleName) {
    this.roleName = roleName;
  }

  public String getPermissionsIds() {
    return permissionsIds;
  }

  public void setPermissionsIds(String permissionsIds) {
    this.permissionsIds = permissionsIds;
  }

  public String getDescription() {
    return description;
  }

  public void setDescription(String description) {
    this.description = description;
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

  public WtssRole(){};

  public WtssRole(int roleId, String roleName, String permissionsIds, String description,
      long createTime, long updateTime) {
    this.roleId = roleId;
    this.roleName = roleName;
    this.permissionsIds = permissionsIds;
    this.description = description;
    this.createTime = createTime;
    this.updateTime = updateTime;
  }

  @Override
  public String toString() {
    return "WtssRole{" +
        "roleId=" + roleId +
        ", roleName='" + roleName + '\'' +
        ", permissionsIds='" + permissionsIds + '\'' +
        ", description='" + description + '\'' +
        ", createTime='" + createTime + '\'' +
        ", updateTime='" + updateTime + '\'' +
        '}';
  }
}
