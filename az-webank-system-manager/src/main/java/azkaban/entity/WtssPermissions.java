package azkaban.entity;

/**
 * Created by zhu on 7/11/18.
 */
public class WtssPermissions {

  /**
   * 权限ID
   */
  private int permissionsId;

  /**
   * 权限名称
   */
  private String permissionsName;

  /**
   * 权限值
   */
  private int permissionsValue;

  /**
   * 权限类型
   */
  private int permissionsType;

  /**
   * 权限说明
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

  public int getPermissionsId() {
    return permissionsId;
  }

  public void setPermissionsId(int permissionsId) {
    this.permissionsId = permissionsId;
  }

  public String getPermissionsName() {
    return permissionsName;
  }

  public void setPermissionsName(String permissionsName) {
    this.permissionsName = permissionsName;
  }

  public int getPermissionsValue() {
    return permissionsValue;
  }

  public void setPermissionsValue(int permissionsValue) {
    this.permissionsValue = permissionsValue;
  }

  public int getPermissionsType() {
    return permissionsType;
  }

  public void setPermissionsType(int permissionsType) {
    this.permissionsType = permissionsType;
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

  public WtssPermissions(){};

  public WtssPermissions(int permissionsId, String permissionsName, int permissionsValue,
      int permissionsType, String description, long createTime, long updateTime) {
    this.permissionsId = permissionsId;
    this.permissionsName = permissionsName;
    this.permissionsValue = permissionsValue;
    this.permissionsType = permissionsType;
    this.description = description;
    this.createTime = createTime;
    this.updateTime = updateTime;
  }

  @Override
  public String toString() {
    return "WtssPermissions{" +
        "permissionsId=" + permissionsId +
        ", permissionsName='" + permissionsName + '\'' +
        ", permissionsValue='" + permissionsValue + '\'' +
        ", permissionsType='" + permissionsType + '\'' +
        ", description='" + description + '\'' +
        ", createTime='" + createTime + '\'' +
        ", updateTime='" + updateTime + '\'' +
        '}';
  }
}
