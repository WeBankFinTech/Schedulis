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

package com.webank.wedatasphere.schedulis.system.entity;

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
