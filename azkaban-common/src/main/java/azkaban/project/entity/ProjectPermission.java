package azkaban.project.entity;

import azkaban.user.Permission;

/**
 * Created by zhu on 6/14/18.
 */
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
