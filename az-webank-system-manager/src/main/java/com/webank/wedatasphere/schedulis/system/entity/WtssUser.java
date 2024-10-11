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

import com.google.common.collect.ImmutableMap;
import java.util.Arrays;

public class WtssUser {

  public final static String PERSONAL = "personal";
  /**
   * 用户ID
   */
  private String userId;

  /**
   * 用户登录名
   */
  private String username;

  /**
   * 用户登录密码
   */
  private String password;

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
   * 代理用户
   */
  private String proxyUsers;

  /**
   * 用户角色
   */
  private int roleId;

  /**
   * 用户状态
   */
  private int userType;

  /**
   * 用户变更内容
   */
  private String modifyInfo;

  /**
   * 用户变更类型
   */
  private String modifyType;

  /**
   * 创建时间
   */
  private long createTime;

  /**
   * 更新时间
   */
  private long updateTime;

  /**
   * 用户种类
   */
  private String userCategory;


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

  public String getPassword() {
    return password;
  }

  public void setPassword(String password) {
    this.password = password;
  }

  public String getFullName() {
    return fullName;
  }

  public void setFullName(String fullName) {
    this.fullName = fullName;
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

  public String getProxyUsers() {
    return proxyUsers;
  }

  public void setProxyUsers(String proxyUsers) {
    this.proxyUsers = proxyUsers;
  }

  public int getRoleId() {
    return roleId;
  }

  public void setRoleId(int roleId) {
    this.roleId = roleId;
  }

  public int getUserType() {
    return userType;
  }

  public void setUserType(int userType) {
    this.userType = userType;
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

  public long getDepartmentId() {
    return departmentId;
  }

  public void setDepartmentId(long departmentId) {
    this.departmentId = departmentId;
  }

  public String getModifyInfo() {
    return modifyInfo;
  }

  public void setModifyInfo(String modifyInfo) {
    this.modifyInfo = modifyInfo;
  }

  public String getModifyType() {
    return modifyType;
  }

  public void setModifyType(String modifyType) {
    this.modifyType = modifyType;
  }

  public String getUserCategory() {
    return userCategory;
  }

  public void setUserCategory(String userCategory) {
    this.userCategory = userCategory;
  }

  public WtssUser(){
  }

  @Override
  public String toString() {
    return "WtssUser{" +
        "userId='" + userId + '\'' +
        ", username='" + username + '\'' +
        ", fullName='" + fullName + '\'' +
        ", departmentI='" + departmentId + '\'' +
        ", departmentName='" + departmentName + '\'' +
        ", email='" + email + '\'' +
        ", proxyUsers='" + proxyUsers + '\'' +
        ", roleId=" + roleId +
        ", userType=" + userType +
        ", createTime=" + createTime +
        ", updateTime=" + updateTime +
        ", modifyType=" + modifyType +
        ", modifyInfo=" + modifyInfo +
        ", userCategory=" + userCategory +
        '}';
  }


  public enum UserType {

    ACTIVE(1),
    BLOCK(2),
    DESTROY(3),
    OTHER(9999);

    private final int userTypeNum;

    UserType(final int userTypeNum) {
      this.userTypeNum = userTypeNum;
    }

    public int getUserTypeNum() {
      return this.userTypeNum;
    }

    private static final ImmutableMap<Integer, UserType> userTypeNumMap = Arrays.stream(UserType.values())
        .collect(ImmutableMap.toImmutableMap(userType -> userType.getUserTypeNum(), userType -> userType));

    public static UserType fromInteger(final int x) {
      return userTypeNumMap.getOrDefault(x, OTHER);
    }


  }

  public enum ModifySystemUserType{
    NORMAL(0,"Normal"),
    LEAVE_OFF(1,"Dimission"),
    DEPARTMENT_EXCHANGE(2,"Exchange Department"),
    NO_DEPARTMENT(3,"Non Department"),
    OTHER(4,"Other");

    // 状态码
    private int statusCode;

    // 状态描述
    private String statusDesc;

    public String getStatusDesc() {
      return this.statusDesc;
    }

    public int getStatusCode() {
      return this.statusCode;
    }

    ModifySystemUserType(int statusCode, String statusDesc) {
      this.statusCode = statusCode;
      this.statusDesc = statusDesc;
    }

    public static ModifySystemUserType getEnumByType(int type){
      for(ModifySystemUserType obj:values()){
        if(obj.statusCode == type){
          return obj;
        }
      }
      return null;
    }

  }

}
