/*
 * Copyright 2020 WeBank
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.webank.wedatasphere.schedulis.common.system.entity;

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
