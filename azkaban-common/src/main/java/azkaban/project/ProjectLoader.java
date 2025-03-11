/*
 * Copyright 2012 LinkedIn Corp.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */

package azkaban.project;

import azkaban.flow.Flow;
import azkaban.project.ProjectLogEvent.EventType;
import azkaban.project.entity.FlowBusiness;
import azkaban.project.entity.ProjectChangeOwnerInfo;
import azkaban.project.entity.ProjectHourlyReportConfig;
import azkaban.project.entity.ProjectPermission;
import azkaban.project.entity.ProjectVersion;
import azkaban.system.entity.WtssUser;
import azkaban.user.Permission;
import azkaban.user.User;
import azkaban.utils.Props;
import azkaban.utils.Triple;
import java.io.File;
import java.io.IOException;
import java.sql.SQLException;
import java.util.Collection;
import java.util.List;
import java.util.Map;

public interface ProjectLoader {

  /**
   * Returns all projects which are active
   */
  List<Project> fetchAllProjects(int active) throws ProjectManagerException;

  List<Project> preciseSearchFetchAllProjects(final String projContain, final String flowContains,
                                              final String execIdContain, final String userNameContains, final String status,
                                              final long startTime, final long endTime, String subsystem, String busPath, String department,
                                              String runDate, final int skip, final int num, final int flowType, int active) throws ProjectManagerException;

  /**
   * return all projects which are inactive
   * @return
   * @throws ProjectManagerException
   */
  List<Project> fetchAllInactiveProjects(String username, String search, String order, int start, int offset) throws ProjectManagerException;

  /**
   * return all projects which are inactive
   * @return
   * @throws ProjectManagerException
   */
  int getInactiveProjectsTotalNumber(String username, String search) throws ProjectManagerException;

  /**
   * Loads whole project, including permissions, by the project id.
   */
  Project fetchProjectById(int id) throws ProjectManagerException;

  /**
   * Loads whole project, including permissions, by the project name.
   */
  Project fetchProjectByName(String name) throws ProjectManagerException;

  /**
   * Should create an empty project with the given name and user and adds it to the data store. It
   * will auto assign a unique id for this project if successful.
   *
   * If an active project of the same name exists, it will throw an exception. If the name and
   * description of the project exceeds the store's constraints, it will throw an exception.
   *
   * @throws ProjectManagerException if an active project of the same name exists.
   */
  Project createNewProject(String name, String description, User creator, String source)
          throws ProjectManagerException;

  void updateProjectCreateUser(Project project, WtssUser newCreateUser, User user) throws Exception;
  /**
   * Removes the project by marking it inactive.
   */
  void removeProject(Project project, String user)
          throws ProjectManagerException;

  /**
   * restore the project by marking it active.
   */
  int restoreProject(String projectName, int projectId, String user)
          throws ProjectManagerException;


  /**
   * delete inactive project
   */
  void deleteInactiveProject(int projectId) throws ProjectManagerException;

  /**
   * Delete the project according to the time interval.
   */
  void deleteHistoricalProject(long interval) throws ProjectManagerException;


  /**
   * 获取该工程没有结束的flow
   */
  List<Flow> getRunningFlow(Project project)
          throws ProjectManagerException;

  /**
   * Adds and updates the user permissions. Does not check if the user is valid. If the permission
   * doesn't exist, it adds. If the permission exists, it updates.
   */
  void updatePermission(Project project, String name, Permission perm,
                        boolean isGroup) throws ProjectManagerException;

  void removePermission(Project project, String name, boolean isGroup)
          throws ProjectManagerException;

  /**
   * Modifies and commits the project description.
   */
  void updateDescription(Project project, String description, String user)
          throws ProjectManagerException;

  void updateJobLimit(Project project, int jobLimit, String user)
          throws ProjectManagerException;

  void updateProjectLock(Project project) throws ProjectManagerException;

  /**
   * Stores logs for a particular project. Will soft fail rather than throw exception.
   *
   * @param message return true if the posting was success.
   */
  boolean postEvent(Project project, EventType type, String user,
                    String message);

  /**
   * Returns all the events for a project sorted
   */
  List<ProjectLogEvent> getProjectEvents(Project project, int num,
                                         int skip) throws ProjectManagerException;

  /**
   * Returns 10 version for a project sorted
   */
  List<ProjectVersion> getProjectVersions(Project project, int num,
                                          int skip) throws ProjectManagerException;

  /**
   * Will upload the files and return the version number of the file uploaded.
   */
  void uploadProjectFile(int projectId, int version, File localFile, String user)
          throws ProjectManagerException;

  /**
   * Will upload the files and pass the version number of the file uploaded.
   */
  void uploadProjectFile(final int projectId, final int version, final File localFile,
                         final String uploader, String resourceID);
  /**
   * Add project and version info to the project_versions table. This current maintains the metadata
   * for each uploaded version of the project
   */
  void addProjectVersion(int projectId, int version, File localFile, String uploader, byte[] md5,
                         String resourceId)
          throws ProjectManagerException;

  /**
   * Fetch project metadata from project_versions table
   *
   * @param projectId project ID
   * @param version version
   * @return ProjectFileHandler object containing the metadata
   */
  ProjectFileHandler fetchProjectMetaData(int projectId, int version);

  /**
   * Get file that's uploaded.
   */
  ProjectFileHandler getUploadedFile(int projectId, int version)
          throws ProjectManagerException;

  /**
   * Changes and commits different project version.
   */
  void changeProjectVersion(Project project, int version, String user)
          throws ProjectManagerException;

  void updateFlow(Project project, int version, Flow flow)
          throws ProjectManagerException;

  /**
   * Uploads all computed flows
   */
  void uploadFlows(Project project, int version, Collection<Flow> flows)
          throws ProjectManagerException;

  /**
   * Upload just one flow.
   */
  void uploadFlow(Project project, int version, Flow flow)
          throws ProjectManagerException;

  /**
   * Fetches one particular flow.
   */
  Flow fetchFlow(Project project, String flowId)
          throws ProjectManagerException;

  /**
   * Fetches all flows.
   */
  List<Flow> fetchAllProjectFlows(Project project)
          throws ProjectManagerException;

  Flow fetchAllProjectFlows(int project, int version, String flowId)
          throws ProjectManagerException;

  /**
   * Gets the latest upload version.
   */
  int getLatestProjectVersion(Project project)
          throws ProjectManagerException;

  /**
   * Upload Project properties
   */
  void uploadProjectProperty(Project project, Props props)
          throws ProjectManagerException;

  /**
   * Upload Project properties. Map contains key value of path and properties
   */
  void uploadProjectProperties(Project project, List<Props> properties)
          throws ProjectManagerException;

  /**
   * Fetch project properties
   */
  Props fetchProjectProperty(Project project, String propsName)
          throws ProjectManagerException;

  /**
   * Fetch all project properties
   */
  Map<String, Props> fetchProjectProperties(int projectId, int version)
          throws ProjectManagerException;

  /**
   * Cleans all project versions less than the provided version, except the versions to exclude
   * given as argument
   */
  void cleanOlderProjectVersion(int projectId, int version, final List<Integer> excludedVersions)
          throws ProjectManagerException;

  void updateProjectProperty(Project project, Props props)
          throws ProjectManagerException;

  Props fetchProjectProperty(int projectId, int projectVer, String propsName)
          throws ProjectManagerException;

  List<Triple<String, Boolean, Permission>> getProjectPermissions(Project project)
          throws ProjectManagerException;

  void updateProjectSettings(Project project) throws ProjectManagerException;

  /**
   * Uploads flow file.
   */
  void uploadFlowFile(int projectId, int projectVersion, File flowFile, int flowVersion)
          throws ProjectManagerException;

  /**
   * Gets flow file that's uploaded.
   */
  File getUploadedFlowFile(int projectId, int projectVersion, String flowFileName, int
          flowVersion, final File tempDir)
          throws ProjectManagerException, IOException;

  /**
   * Gets the latest flow version.
   */
  int getLatestFlowVersion(int projectId, int projectVersion, String flowName)
          throws ProjectManagerException;

  /**
   * Check if flow file has been uploaded.
   */
  boolean isFlowFileUploaded(int projectId, int projectVersion)
          throws ProjectManagerException;

  /**
   * 添加项目的组和组权限接口
   */
  void updatePermission(Project project, String name, Permission perm,
                        boolean isGroup, String group) throws ProjectManagerException;

  /**
   *
   * @param project
   * @param userId
   * @throws ProjectManagerException
   */
  void removeProjectPermission(Project project, String userId)
          throws ProjectManagerException;

  /**
   * 获取当天创建的项目列表
   * @param username
   * @return
   * @throws ProjectManagerException
   */
  List<Project> getTodayCreateProjects(String username) throws ProjectManagerException;

  /**
   * 查找当前项目的 flow 是否执行过
   * @param projectId, flowName
   * @return
   * @throws ProjectManagerException
   */
  int getTodayRunFlow(int projectId, String flowName) throws ProjectManagerException;

  /**
   * 获取用户有权限的projectId
   * @param user
   * @return
   * @throws ProjectManagerException
   */
  List<Integer> fetchPermissionsProjectId(String user) throws ProjectManagerException;

  /**
   * 新增/更新工作流应用信息
   *
   * @param flowBusiness
   */
  int mergeFlowBusiness(FlowBusiness flowBusiness);

  int mergeProjectInfo(FlowBusiness flowBusiness) throws SQLException;

  /**
   * 查询工作流应用信息
   *
   * @param projectId
   * @return
   */
  FlowBusiness getFlowBusiness(int projectId, String flowId, String jobId);

  /**
   * 删除工作流应用信息
   *
   * @param projectId
   * @return
   */
  void deleteFlowBusiness(int projectId, String flowId, String jobId);

  List<ProjectPermission> fetchAllPermissionsForProject(Project project)
          throws ProjectManagerException;

  /**
   * 修改项目关联 ITSM 单号
   *
   * @param project
   */
  void changeProjectItsmId(Project project);

  int updateProjectChangeOwnerInfo(long itsmNo, Project project, String newOwner, User user)
          throws SQLException;

  List<Project> preciseSearchFetchProjects(String projContain, String flowContain,
                                           String description, String userContain, String subsystem,
                                           String busPath, String departmentId, int active) throws ProjectManagerException;

  ProjectChangeOwnerInfo getProjectChangeOwnerInfo(Project project) throws SQLException;

  int updateProjectChangeOwnerStatus(Project project, int status) throws SQLException;

  List<String> getProjectIdsAndFlowIds(String subsystem, String busPath);

  List<String> getProjectIds(String subsystem, String busPath);

  long getProjectFileSize(String projectIds);

  File getProjectFiles(List<Project> projectList) throws ProjectManagerException;

  int updateProjectHourlyReportConfig(Project project, User user, String reportWay,
                                      String reportReceiverString)
          throws SQLException;

  int removeProjectHourlyReportConfig(Project project) throws SQLException;

  List<ProjectHourlyReportConfig> getProjectHourlyReportConfig() throws SQLException;

  void updateProjectPrincipal(Project project, String principal, User user) throws Exception;

  ProjectPermission getProjectPermission(String projectId, String userName);
}
