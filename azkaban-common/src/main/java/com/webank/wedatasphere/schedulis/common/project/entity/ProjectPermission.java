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

package com.webank.wedatasphere.schedulis.common.project.entity;

import azkaban.user.Permission;

public class ProjectPermission {

  private int projectId;

  private String username;

  private Permission permission;

  private boolean isGroup;

  private String projectGroup;

  public int getProjectId() {
    return projectId;
  }

  public void setProjectId(int projectId) {
    this.projectId = projectId;
  }

  public String getUsername() {
    return username;
  }

  public void setUsername(String username) {
    this.username = username;
  }

  public Permission getPermission() {
    return permission;
  }

  public void setPermission(Permission permission) {
    this.permission = permission;
  }

  public boolean getIsGroup() {
    return isGroup;
  }

  public void setIsGroup(boolean group) {
    isGroup = group;
  }

  public String getProjectGroup() {
    return projectGroup;
  }

  public void setProjectGroup(String projectGroup) {
    this.projectGroup = projectGroup;
  }

}
