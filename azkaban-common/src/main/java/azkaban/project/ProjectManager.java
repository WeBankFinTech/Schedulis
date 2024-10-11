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

import static java.util.Objects.requireNonNull;

import azkaban.Constants;
import azkaban.Constants.ConfigurationKeys;
import azkaban.executor.ExecutorManagerException;
import azkaban.flow.Flow;
import azkaban.project.ProjectLogEvent.EventType;
import azkaban.project.validator.ValidationReport;
import azkaban.project.validator.ValidatorConfigs;
import azkaban.project.validator.XmlValidatorManager;
import azkaban.storage.StorageManager;
import azkaban.user.Permission;
import azkaban.user.Permission.Type;
import azkaban.user.User;
import azkaban.utils.CaseInsensitiveConcurrentHashMap;
import azkaban.utils.Props;
import azkaban.utils.PropsUtils;
import com.google.common.io.Files;
import com.webank.wedatasphere.schedulis.common.i18nutils.LoadJsonUtils;
import com.webank.wedatasphere.schedulis.common.project.entity.ProjectPermission;
import com.webank.wedatasphere.schedulis.common.system.SystemManager;
import com.webank.wedatasphere.schedulis.common.utils.HttpUtils;
import com.webank.wedatasphere.schedulis.common.utils.PagingListStreamUtil;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Predicate;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;
import java.util.stream.Collectors;
import javax.inject.Inject;
import javax.inject.Singleton;
import org.apache.commons.collections4.CollectionUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


@Singleton
public class ProjectManager {

  private static final Logger logger = LoggerFactory.getLogger(ProjectManager.class);
  private final AzkabanProjectLoader azkabanProjectLoader;
  private final ProjectLoader projectLoader;
  private final Props props;
  private final boolean creatorDefaultPermissions;
  // Both projectsById and projectsByName cache need to be thread safe since they are accessed
  // from multiple threads concurrently without external synchronization for performance.
  private final ConcurrentHashMap<Integer, Project> projectsById =
      new ConcurrentHashMap<>();
  private final CaseInsensitiveConcurrentHashMap<Project> projectsByName =
      new CaseInsensitiveConcurrentHashMap<>();
 
  private SystemManager systemManager;

  private boolean isHaModel;

  @Inject
  public ProjectManager(final AzkabanProjectLoader azkabanProjectLoader,
      final ProjectLoader loader,
      final StorageManager storageManager,
      final Props props,
      final SystemManager systemManager) {
    this.projectLoader = requireNonNull(loader);
    this.props = requireNonNull(props);
    this.azkabanProjectLoader = requireNonNull(azkabanProjectLoader);
    this.systemManager = requireNonNull(systemManager);
    this.creatorDefaultPermissions =
        props.getBoolean("creator.default.proxy", true);
    this.isHaModel = props.getBoolean(ConfigurationKeys.WEBSERVER_HA_MODEL, false);

    // The prop passed to XmlValidatorManager is used to initialize all the
    // validators
    // Each validator will take certain key/value pairs from the prop to
    // initialize itself.
    final Props prop = new Props(props);
    prop.put(ValidatorConfigs.PROJECT_ARCHIVE_FILE_PATH, "initialize");
    // By instantiating an object of XmlValidatorManager, this will verify the
    // config files for the validators.
    new XmlValidatorManager(prop);
    loadAllProjects();
    loadProjectWhiteList();
  }

  public boolean hasFlowTrigger(final Project project, final Flow flow)
      throws IOException, ProjectManagerException {
    final String flowFileName = flow.getId() + ".flow";
    final int latestFlowVersion = this.projectLoader.getLatestFlowVersion(project.getId(), flow
        .getVersion(), flowFileName);
    if (latestFlowVersion > 0) {
      final File tempDir = Files.createTempDir();
      final File flowFile;
      try {
        flowFile = this.projectLoader
            .getUploadedFlowFile(project.getId(), project.getVersion(),
                flowFileName, latestFlowVersion, tempDir);

        final FlowTrigger flowTrigger = FlowLoaderUtils.getFlowTriggerFromYamlFile(flowFile);
        return flowTrigger != null;
      } catch (final Exception ex) {
        logger.error("error in getting flow file", ex);
        throw ex;
      } finally {
        FlowLoaderUtils.cleanUpDir(tempDir);
      }
    } else {
      return false;
    }
  }

  public void loadAllProjects() {
    final List<Project> projects;
    try {
      projects = this.projectLoader.fetchAllActiveProjects();
    } catch (final ProjectManagerException e) {
      throw new RuntimeException("Could not load projects from store.", e);
    }
    for (final Project proj : projects) {
      this.projectsByName.put(proj.getName(), proj);
      this.projectsById.put(proj.getId(), proj);
    }

    for (final Project proj : projects) {
      loadAllProjectFlows(proj);
    }
  }

  private void loadAllProjectFlows(final Project project) {
    try {
      final List<Flow> flows = this.projectLoader.fetchAllProjectFlows(project);
      final Map<String, Flow> flowMap = new HashMap<>();
      for (final Flow flow : flows) {
        flowMap.put(flow.getId(), flow);
      }

      project.setFlows(flowMap);
    } catch (final ProjectManagerException e) {
      throw new RuntimeException("Could not load projects flows from store.", e);
    }
  }

  public Props getProps() {
    return this.props;
  }

  public List<Project> getUserProjects(final User user) {
    final ArrayList<Project> array = new ArrayList<>();
    for (final Project project : this.projectsById.values()) {
      final Permission perm = project.getUserPermission(user);

      if (perm != null && project.getCreateUser().equals(user.getUserId())) {
        array.add(project);
      }

    }

    // FIXME Sort by project name.
    List<Project> newArray = array.stream().sorted(
        Comparator.comparing(Project::getName, String.CASE_INSENSITIVE_ORDER))
        .collect(Collectors.toList());

    return newArray;
  }

  public List<Project> getGroupProjects(final User user) {
    final List<Project> array = new ArrayList<>();
    for (final Project project : this.projectsById.values()) {
      if (project.hasGroupPermission(user, Type.READ)) {
        array.add(project);
      }
    }
    //FIXME Sort by project name.
    List<Project> newArray = array.stream().sorted(
        Comparator.comparing(Project::getName, String.CASE_INSENSITIVE_ORDER)).collect(Collectors.toList());

    return newArray;
  }

  public List<Project> getUserProjectsByRegex(final User user, final String regexPattern) {
    final List<Project> array = new ArrayList<>();
    final Pattern pattern;
    try {
      pattern = Pattern.compile(regexPattern, Pattern.CASE_INSENSITIVE);
    } catch (final PatternSyntaxException e) {
      logger.error("Bad regex pattern " + regexPattern);
      return array;
    }

    for (final Project project : this.projectsById.values()) {
      final Permission perm = project.getUserPermission(user);

      if (perm != null
          && (perm.isPermissionSet(Type.ADMIN) || perm
          .isPermissionSet(Type.READ))) {
        if (pattern.matcher(project.getName()).find() || pattern.matcher(project.getDescription()).find() || pattern.matcher(project.getCreateUser()).find()) {
          array.add(project);
        }
      }
    }
    // FIXME Sort by project name.
    List<Project> newArray = array.stream().sorted(
        Comparator.comparing(Project::getName, String.CASE_INSENSITIVE_ORDER)).collect(Collectors.toList());

    return newArray;
  }

  public List<Project> getProjects() {
    List<Project> projectList = new ArrayList<>(this.projectsById.values());
    // FIXME Sort by project name.
    List<Project> newArray = projectList.stream().sorted(
        Comparator.comparing(Project::getName, String.CASE_INSENSITIVE_ORDER)).collect(Collectors.toList());

    return newArray;
  }

  public List<Project> getProjectsByRegex(final String regexPattern) {
    final List<Project> allProjects = new ArrayList<>();
    final Pattern pattern;
    try {
      pattern = Pattern.compile(regexPattern, Pattern.CASE_INSENSITIVE);
    } catch (final PatternSyntaxException e) {
      logger.error("Bad regex pattern " + regexPattern);
      return allProjects;
    }
    for (final Project project : getProjects()) {
      if (pattern.matcher(project.getName()).find()) {
        allProjects.add(project);
      }
    }
    // FIXME Sort by project name.
    List<Project> newArray = allProjects.stream().sorted(
        Comparator.comparing(Project::getName, String.CASE_INSENSITIVE_ORDER)).collect(Collectors.toList());

    return newArray;
  }

  /**
   * Checks if a project is active using project_id
   */
  public Boolean isActiveProject(final int id) {
    return this.projectsById.containsKey(id);
  }

  /**
   * fetch active project from cache and inactive projects from db by project_name
   */
  public Project getProject(final String name) {
    Project fetchedProject = this.projectsByName.get(name);
    if (fetchedProject == null) {
      try {
        logger.info("Project " + name + " doesn't exist in cache, fetching from DB now.");
        fetchedProject = this.projectLoader.fetchProjectByName(name);
        if (fetchedProject.isActive()) {
          loadAllProjectFlows(fetchedProject);
          this.projectsByName.put(fetchedProject.getName(), fetchedProject);
        }
      } catch (final ProjectManagerException e) {
        logger.error("Could not load project from store.", e);
      }
    }
    return fetchedProject;
  }

  /**
   * fetch active project from cache and inactive projects from db by project_id
   */
  public Project getProject(final int id) {
    Project fetchedProject = this.projectsById.get(id);
    if (fetchedProject == null) {
      try {
        fetchedProject = this.projectLoader.fetchProjectById(id);
        if (fetchedProject.isActive()) {
          loadAllProjectFlows(fetchedProject);
          this.projectsById.put(fetchedProject.getId(), fetchedProject);
        }
      } catch (final ProjectManagerException e) {
        logger.error("Could not load project from store.", e);
      }
    }
    return fetchedProject;
  }

  /**
   * fetch active project from cache and inactive projects from db by project_id
   */
  public List<Flow> getRunningFlow(final Project project) {
    return this.projectLoader.getRunningFlow(project);
  }

  public Project createProject(final String projectName, final String description,
      final User creator) throws ProjectManagerException {
    if (projectName == null || projectName.trim().isEmpty()) {
      throw new ProjectManagerException("项目名称不能为空.");
    } else if (description == null || description.trim().isEmpty()) {
      throw new ProjectManagerException("项目描述不能为空.");
    } else if (creator == null) {
      throw new ProjectManagerException("必须使用有效用户创建项目.");
    } else if (!projectName.matches("[a-zA-Z][a-zA-Z_0-9|-]*")) {
      throw new ProjectManagerException(
          "项目名称必须以字母开头，后面跟着任意数量的字母，数字, '-' 或者 '_'.");
    }

    final Project newProject;
    synchronized (this) {
      if (this.projectsByName.containsKey(projectName)) {
        throw new ProjectManagerException("项目已经存在.");
      }

      logger.info("Trying to create " + projectName + " by user "
          + creator.getUserId());
      newProject = this.projectLoader.createNewProject(projectName, description, creator);
      this.projectsByName.put(newProject.getName(), newProject);
      this.projectsById.put(newProject.getId(), newProject);
    }

    if (this.creatorDefaultPermissions) {
      // Add permission to project
      this.projectLoader.updatePermission(newProject, creator.getUserId(),
          new Permission(Type.ADMIN), true);

      // 需求 不把当前主用户添加到代理中
      //newProject.addProxyUser(creator.getUserId());
      // 先清空原有的代理用户
      newProject.removeAllProxyUsers();
      // FIXME Add user-configured proxy users to perform tasks.
      for (String pUser : creator.getProxyUsers()) {
        newProject.addProxyUser(pUser);
      }

      try {
        updateProjectSetting(newProject);
      } catch (final ProjectManagerException e) {
        e.printStackTrace();
        throw e;
      }
    }

    this.projectLoader.postEvent(newProject, EventType.CREATED, creator.getUserId(),
        null);
    if (this.isHaModel) {
      HttpUtils.reloadWebData(this.getProps().getStringList("azkaban.all.web.url"), "reloadProject",
          newProject.getName());
    }

    return newProject;
  }

  /**
   * Permanently delete all project files and properties data for all versions of a project and log
   * event in project_events table
   */
  public synchronized Project purgeProject(final Project project, final User deleter)
      throws ProjectManagerException {
    this.projectLoader.cleanOlderProjectVersion(project.getId(),
        project.getVersion() + 1, Collections.emptyList());
    this.projectLoader
        .postEvent(project, EventType.PURGE, deleter.getUserId(), String
            .format("Purged versions before %d", project.getVersion() + 1));
    return project;
  }

  public synchronized Project removeProject(final Project project, final User deleter)
      throws ProjectManagerException {
    this.projectLoader.removeProject(project, deleter.getUserId());
    this.projectLoader.postEvent(project, EventType.DELETED, deleter.getUserId(),
        null);

    this.projectsByName.remove(project.getName());
    this.projectsById.remove(project.getId());

    if (this.isHaModel) {
      HttpUtils.reloadWebData(this.getProps().getStringList("azkaban.all.web.url"), "deleteProject",
          project.getId() + "");
    }

    return project;
  }

  public void updateProjectDescription(final Project project, final String description,
      final User modifier) throws ProjectManagerException {
    this.projectLoader.updateDescription(project, description, modifier.getUserId());
    this.projectsByName.put(project.getName(), project);
    this.projectsById.put(project.getId(), project);
    this.projectLoader.postEvent(project, EventType.DESCRIPTION,
        modifier.getUserId(), "Description changed to " + description);
    if (this.isHaModel) {
      HttpUtils.reloadWebData(this.getProps().getStringList("azkaban.all.web.url"), "reloadProject",
          project.getName());
    }
  }

  public List<ProjectLogEvent> getProjectEventLogs(final Project project,
      final int results, final int skip) throws ProjectManagerException {
    return this.projectLoader.getProjectEvents(project, results, skip);
  }

  public Props getPropertiesFromFlowFile(final Flow flow, final String jobName, final String
      flowFileName, final int flowVersion) throws ProjectManagerException {
    File tempDir = null;
    Props props = null;
    try {
      tempDir = Files.createTempDir();
      final File flowFile = this.projectLoader.getUploadedFlowFile(flow.getProjectId(), flow
          .getVersion(), flowFileName, flowVersion, tempDir);
      final String path =
          jobName == null ? flow.getId() : flow.getId() + Constants.PATH_DELIMITER + jobName;
      props = FlowLoaderUtils.getPropsFromYamlFile(path, flowFile);
    } catch (final Exception e) {
      logger.error("Failed to get props from flow file. " + e);
    } finally {
      FlowLoaderUtils.cleanUpDir(tempDir);
    }
    return props;
  }

  public Props getProperties(final Project project, final Flow flow, final String jobName,
      final String source) throws ProjectManagerException {
    if (FlowLoaderUtils.isAzkabanFlowVersion20(flow.getAzkabanFlowVersion())) {
      // Return the properties from the original uploaded flow file.
      return getPropertiesFromFlowFile(flow, jobName, source, 1);
    } else {
      return this.projectLoader.fetchProjectProperty(project, source);
    }
  }

  public Props getJobOverrideProperty(final Project project, final Flow flow, final String jobName,
      final String source) throws ProjectManagerException {
    if (FlowLoaderUtils.isAzkabanFlowVersion20(flow.getAzkabanFlowVersion())) {
      final int flowVersion = this.projectLoader
          .getLatestFlowVersion(flow.getProjectId(), flow.getVersion(), source);
      return getPropertiesFromFlowFile(flow, jobName, source, flowVersion);
    } else {
      return this.projectLoader
          .fetchProjectProperty(project, jobName + Constants.JOB_OVERRIDE_SUFFIX);
    }
  }

  public void setJobOverrideProperty(final Project project, final Flow flow, final Props prop,
      final String jobName, final String source, final User modifier)
      throws ProjectManagerException {
    File tempDir = null;
    Props oldProps = null;
    if (FlowLoaderUtils.isAzkabanFlowVersion20(flow.getAzkabanFlowVersion())) {
      try {
        tempDir = Files.createTempDir();
        final int flowVersion = this.projectLoader.getLatestFlowVersion(flow.getProjectId(), flow
            .getVersion(), source);
        final File flowFile = this.projectLoader.getUploadedFlowFile(flow.getProjectId(), flow
            .getVersion(), source, flowVersion, tempDir);
        final String path = flow.getId() + Constants.PATH_DELIMITER + jobName;
        oldProps = FlowLoaderUtils.getPropsFromYamlFile(path, flowFile);

        FlowLoaderUtils.setPropsInYamlFile(path, flowFile, prop);
        this.projectLoader
            .uploadFlowFile(flow.getProjectId(), flow.getVersion(), flowFile, flowVersion + 1);
      } catch (final Exception e) {
        logger.error("Failed to set job override property. " + e);
      } finally {
        FlowLoaderUtils.cleanUpDir(tempDir);
      }
    } else {
      prop.setSource(jobName + Constants.JOB_OVERRIDE_SUFFIX);
      oldProps = this.projectLoader.fetchProjectProperty(project, prop.getSource());

      if (oldProps == null) {
        this.projectLoader.uploadProjectProperty(project, prop);
      } else {
        this.projectLoader.updateProjectProperty(project, prop);
      }
    }

    final String diffMessage = PropsUtils.getPropertyDiff(oldProps, prop);

    this.projectLoader.postEvent(project, EventType.PROPERTY_OVERRIDE,
        modifier.getUserId(), diffMessage);
    return;
  }

  public void updateProjectSetting(final Project project)
      throws ProjectManagerException {
    this.projectLoader.updateProjectSettings(project);
    this.projectsByName.put(project.getName(), project);
    this.projectsById.put(project.getId(), project);
    if (this.isHaModel) {
      HttpUtils.reloadWebData(this.getProps().getStringList("azkaban.all.web.url"), "reloadProject",
          project.getName());
    }
  }

  public void addProjectProxyUser(final Project project, final String proxyName,
      final User modifier) throws ProjectManagerException {
    logger.info("User " + modifier.getUserId() + " adding proxy user "
        + proxyName + " to project " + project.getName());
    project.addProxyUser(proxyName);

    this.projectLoader.postEvent(project, EventType.PROXY_USER,
        modifier.getUserId(), "Proxy user " + proxyName
            + " is added to project.");
    updateProjectSetting(project);
  }

  public void removeProjectProxyUser(final Project project, final String proxyName,
      final User modifier) throws ProjectManagerException {
    logger.info("User " + modifier.getUserId() + " removing proxy user "
        + proxyName + " from project " + project.getName());
    project.removeProxyUser(proxyName);

    this.projectLoader.postEvent(project, EventType.PROXY_USER,
        modifier.getUserId(), "Proxy user " + proxyName
            + " has been removed form the project.");
    updateProjectSetting(project);
  }

  public void updateProjectPermission(final Project project, final String name,
      final Permission perm, final boolean group, final User modifier)
      throws ProjectManagerException {
    logger.info("User " + modifier.getUserId()
        + " updating permissions for project " + project.getName() + " for "
        + name + " " + perm.toString());
    this.projectLoader.updatePermission(project, name, perm, group);
    if (group) {
      this.projectLoader.postEvent(project, EventType.GROUP_PERMISSION,
          modifier.getUserId(), "Permission for group " + name + " set to "
              + perm.toString());
    } else {
      this.projectLoader.postEvent(project, EventType.USER_PERMISSION,
          modifier.getUserId(), "Permission for user " + name + " set to "
              + perm.toString());
    }
    if (this.isHaModel) {
      HttpUtils.reloadWebData(this.getProps().getStringList("azkaban.all.web.url"),
          "refreshProjectPermission", project.getName());
    }
  }

  public void removeProjectPermission(final Project project, final String name,
      final boolean group, final User modifier) throws ProjectManagerException {
    logger.info("User " + modifier.getUserId()
        + " removing permissions for project " + project.getName() + " for "
        + name);
    this.projectLoader.removePermission(project, name, group);
    if (group) {
      this.projectLoader.postEvent(project, EventType.GROUP_PERMISSION,
          modifier.getUserId(), "Permission for group " + name + " removed.");
    } else {
      this.projectLoader.postEvent(project, EventType.USER_PERMISSION,
          modifier.getUserId(), "Permission for user " + name + " removed.");
    }
    if (this.isHaModel) {
      HttpUtils.reloadWebData(this.getProps().getStringList("azkaban.all.web.url"),
          "refreshProjectPermission", project.getName());
    }
  }

  /**
   * This method retrieves the uploaded project zip file from DB. A temporary file is created to
   * hold the content of the uploaded zip file. This temporary file is provided in the
   * ProjectFileHandler instance and the caller of this method should call method
   * {@ProjectFileHandler.deleteLocalFile} to delete the temporary file.
   *
   * @param version - latest version is used if value is -1
   * @return ProjectFileHandler - null if can't find project zip file based on project name and
   * version
   */
  public ProjectFileHandler getProjectFileHandler(final Project project, final int version)
      throws ProjectManagerException {
    return this.azkabanProjectLoader.getProjectFile(project, version);
  }

  public Map<String, ValidationReport> uploadProject(final Project project,
      final File archive, final String fileType, final User uploader, final Props additionalProps)
      throws ProjectManagerException, ExecutorManagerException {
    Map<String, ValidationReport> retMap = this.azkabanProjectLoader.uploadProject(project, archive,
        fileType, uploader, additionalProps);
    if (this.isHaModel) {
      HttpUtils.reloadWebData(this.getProps().getStringList("azkaban.all.web.url"), "reloadProject",
          project.getName());
    }
    return retMap;
  }

  public Boolean checkFlowName(final Project project,final File archive,
      final String fileType,  final Props additionalProps) {
    return this.azkabanProjectLoader.checkFlowName(project, archive, fileType,  additionalProps);
  }

  public void updateFlow(final Project project, final Flow flow)
      throws ProjectManagerException {
    this.projectLoader.updateFlow(project, flow.getVersion(), flow);
    this.projectsByName.put(project.getName(), project);
    this.projectsById.put(project.getId(), project);
    if (this.isHaModel) {
      HttpUtils.reloadWebData(this.getProps().getStringList("azkaban.all.web.url"), "reloadProject",
          project.getName());
    }
  }


  public void postProjectEvent(final Project project, final EventType type, final String user,
      final String message) {
    this.projectLoader.postEvent(project, type, user, message);
  }

  public boolean loadProjectWhiteList() {
    if (this.props.containsKey(ProjectWhitelist.XML_FILE_PARAM)) {
      ProjectWhitelist.load(this.props);
      return true;
    }
    return false;
  }

//  public List<String> getProjectNames() {
//    return new ArrayList<>(this.projectsByName.keySet());
//  }

  /**
   * 每次登录的时候刷新该用户的项目信息
   *
   * @param user
   */
  public void loadUserProjects(final User user) {
    final List<Project> projects;
    try {
      projects = this.getUserProjects(user);
    } catch (final ProjectManagerException e) {
      throw new RuntimeException("Could not load projects from store.", e);
    }
    for (final Project proj : projects) {
      this.projectsByName.put(proj.getName(), proj);
      this.projectsById.put(proj.getId(), proj);
    }

    for (final Project proj : projects) {
      loadAllProjectFlows(proj);
    }
  }

  public Project createProject(final String projectName, final String description, final String group,
      final User creator) throws ProjectManagerException {

    String languageType = LoadJsonUtils.getLanguageType();
    Map<String, String> dataMap;
    if (languageType.equalsIgnoreCase("zh_CN")) {
      dataMap = LoadJsonUtils.transJson("/com.webank.wedatasphere.schedulis.i18n.conf/azkaban-common-zh_CN.json",
          "azkaban.project.ProjectManager");
    } else {
      dataMap = LoadJsonUtils.transJson("/com.webank.wedatasphere.schedulis.i18n.conf/azkaban-common-en_US.json",
          "azkaban.project.ProjectManager");
    }

    if (projectName == null || projectName.trim().isEmpty()) {
      throw new ProjectManagerException(dataMap.get("noBlankProgramName"));
    } else if (description == null || description.trim().isEmpty()) {
      throw new ProjectManagerException(dataMap.get("noBlankProgramDesc"));
    } else if (creator == null) {
      throw new ProjectManagerException(dataMap.get("noInvalidUser"));
    } else if (!projectName.matches("[a-zA-Z][a-zA-Z_0-9|-]*")) {
      throw new ProjectManagerException(dataMap.get("checkProgramName"));
    }

    final Project newProject;
    synchronized (this) {
      if (this.projectsByName.containsKey(projectName)) {
        throw new ProjectManagerException(dataMap.get("hasExistProgram"));
      }

      logger.info("Trying to create " + projectName + " by user "
          + creator.getUserId());
      newProject = this.projectLoader.createNewProject(projectName, description, creator);
      this.projectsByName.put(newProject.getName(), newProject);
      this.projectsById.put(newProject.getId(), newProject);
    }

    if (this.creatorDefaultPermissions) {
      // Add permission to project
      this.projectLoader.updatePermission(newProject, creator.getUserId(),
          new Permission(Type.ADMIN), "".equals(group) ? false : true,
          group);

      // 需求 不把当前主用户添加到代理中
      //newProject.addProxyUser(creator.getUserId());
      // 先清空原有的代理用户
      newProject.removeAllProxyUsers();
      // 添加用户配置的代理用户
      for (String pUser : creator.getProxyUsers()) {
        newProject.addProxyUser(pUser);
      }

      try {
        updateProjectSetting(newProject);
      } catch (final ProjectManagerException e) {
        e.printStackTrace();
        throw e;
      }
    }

    this.projectLoader.postEvent(newProject, EventType.CREATED, creator.getUserId(),
        null);
    if (this.isHaModel) {
      HttpUtils.reloadWebData(this.getProps().getStringList("azkaban.all.web.url"), "reloadProject",
          newProject.getName());
    }

    return newProject;
  }

  public PagingListStreamUtil<Project> getUserProjectsPage(int skip, int size, List<Project> userProjectList) {

    PagingListStreamUtil<Project> paging = new PagingListStreamUtil<>(userProjectList, size);
    paging.setCurPageNo(skip);

    return paging;
  }

  public PagingListStreamUtil<Project> getGroupProjectsPage(int skip, int size, List<Project> groupProjectList) {

    PagingListStreamUtil<Project> paging = new PagingListStreamUtil<>(groupProjectList, size);
    paging.setCurPageNo(skip);

    return paging;
  }

  public PagingListStreamUtil<Project> getAllProjectsPage(int skip, int size, List<Project> allProjectList) {

    PagingListStreamUtil<Project> paging = new PagingListStreamUtil<>(allProjectList, size);
    paging.setCurPageNo(skip);

    return paging;
  }

  /**
   * 根据搜索条件查询组项目
   *
   * @param user
   * @param regexPattern
   * @return
   */
  public List<Project> getGroupProjectsByRegex(final User user, final String regexPattern) {
    final List<Project> array = new ArrayList<>();
    final Pattern pattern;
    try {
      pattern = Pattern.compile(regexPattern, Pattern.CASE_INSENSITIVE);
    } catch (final PatternSyntaxException e) {
      logger.error("Bad regex pattern " + regexPattern);
      return array;
    }
    for (final Project project : this.projectsById.values()) {
      if (project.hasGroupPermission(user, Type.READ)) {
        if (pattern.matcher(project.getName()).find()) {
          array.add(project);
        }
      }

    }
    //按照项目名称排序
    List<Project> newArray = array.stream().sorted(
        Comparator.comparing(Project::getName, String.CASE_INSENSITIVE_ORDER)).collect(Collectors.toList());

    return newArray;
  }

  public void removeProjectPermission(final Project project, final String userId, final User modifier) throws ProjectManagerException {
    logger.info("User " + modifier.getUserId()
        + " removing permissions for project " + project.getName() + " for "
        + userId);
    this.projectLoader.removeProjectPermission(project, userId);

    this.projectLoader.postEvent(project, EventType.USER_PERMISSION,
        modifier.getUserId(), "Permission for user " + userId + " removed.");
    if (this.isHaModel) {
      HttpUtils.reloadWebData(this.getProps().getStringList("azkaban.all.web.url"),
          "refreshProjectPermission", project.getName());
    }
  }

  /**
   * 查看自己创建的项目和别人创建的但是拥有权限的项目
   * @param user
   * @return
   */
  public List<Project> getUserAllProjects(final User user, final String orderOption) {
    final ArrayList<Project> array = new ArrayList<>();
    List<Integer> projectIds = systemManager.getMaintainedProjects(user);
    boolean isDepartmentMaintainer = systemManager.isDepartmentMaintainer(user);
    for (final Project project : this.projectsById.values()) {
      final Permission permission = project.getUserPermission(user);
      Predicate<Permission> hasPermission = perm -> perm != null && (perm.isPermissionSet(Type.ADMIN) || perm.isPermissionSet(Type.READ));
      Predicate<User> isMaintained = u -> isDepartmentMaintainer && projectIds.contains(project.getId());
      if (isMaintained.test(user) || hasPermission.test(permission)) {
        array.add(project);
      }

    }

//    //按照项目名称排序
//    List<Project> newArray = array.stream().sorted(
//        Comparator.comparing(Project::getName, String.CASE_INSENSITIVE_ORDER))
//        .collect(Collectors.toList());

    //按照项目名称排序
    List<Project> newArray = new ArrayList<>();

    if("orderUpdateTimeSort".equals(orderOption)){
      //修改时间排序 从大到小
      newArray = array.stream().sorted(
          Comparator.comparing(Project::getLastModifiedTimestamp).reversed())
          .collect(Collectors.toList());
    } else {
      //按照项目名称排序
      newArray = array.stream().sorted(
          Comparator.comparing(Project::getName, String.CASE_INSENSITIVE_ORDER))
          .collect(Collectors.toList());
    }

    return newArray;
  }

  public List<Project> getMaintainedProjects(final User user, final List<Integer> projectIds, final String orderOption) {
    final ArrayList<Project> array = new ArrayList<>();
    for (final Project project : this.projectsById.values()) {
      final Permission permission = project.getUserPermission(user);
      Predicate<Permission> hasPermission = perm -> perm != null && (perm.isPermissionSet(Type.ADMIN) || perm.isPermissionSet(Type.READ));
      Predicate<Project> isMaintained = proj -> projectIds.contains(proj.getId());
      if (isMaintained.test(project) || hasPermission.test(permission)) {
        array.add(project);
      }
    }

//    //按照项目名称排序
//    List<Project> newArray = array.stream().sorted(
//        Comparator.comparing(Project::getName, String.CASE_INSENSITIVE_ORDER))
//        .collect(Collectors.toList());

    //按照项目名称排序
    List<Project> newArray = new ArrayList<>();

    if("orderUpdateTimeSort".equals(orderOption)){
      //修改时间排序 从大到小
      newArray = array.stream().sorted(
              Comparator.comparing(Project::getLastModifiedTimestamp).reversed())
              .collect(Collectors.toList());
    } else {
      //按照项目名称排序
      newArray = array.stream().sorted(
              Comparator.comparing(Project::getName, String.CASE_INSENSITIVE_ORDER))
              .collect(Collectors.toList());
    }

    return newArray;
  }

  public List<Project> getUserPersonProjectsByRegex(final User user, final String regexPattern,
      final String orderOption) {
    final List<Project> array = new ArrayList<>();
    final Pattern pattern;
    try {
      pattern = Pattern.compile(regexPattern, Pattern.CASE_INSENSITIVE);
    } catch (final PatternSyntaxException e) {
      logger.error("Bad regex pattern " + regexPattern);
      return array;
    }
    for (final Project project : this.projectsById.values()) {
      final Permission perm = project.getUserPermission(user);

      if (perm != null
          && project.getCreateUser().equals(user.getUserId())) {
        if (pattern.matcher(project.getName()).find()
            || pattern.matcher(project.getDescription()).find()
            || pattern.matcher(project.getCreateUser()).find()) {
          array.add(project);
        }
      }
    }
//    //按照项目名称排序
//    List<Project> newArray = array.stream().sorted(
//        Comparator.comparing(Project::getName, String.CASE_INSENSITIVE_ORDER)).collect(Collectors.toList());

    //按照项目名称排序
    List<Project> newArray = new ArrayList<>();

    if("orderUpdateTimeSort".equals(orderOption)){
      //修改时间排序 从大到小
      newArray = array.stream().sorted(
          Comparator.comparing(Project::getLastModifiedTimestamp).reversed())
          .collect(Collectors.toList());
    } else {
      //按照项目名称排序
      newArray = array.stream().sorted(
          Comparator.comparing(Project::getName, String.CASE_INSENSITIVE_ORDER))
          .collect(Collectors.toList());
    }


    return newArray;
  }


  public List<Project> getUserProjects(final User user, final String orderOption) {
    final ArrayList<Project> array = new ArrayList<>();
    for (final Project project : this.projectsById.values()) {

      final Permission perm = project.getUserPermission(user);

      //有项目权限 并且是项目创建人
      if (perm != null && project.getCreateUser().equals(user.getUserId())) {
        array.add(project);
      }

    }

    //按照项目名称排序
    List<Project> newArray = new ArrayList<>();

    if ("orderUpdateTimeSort".equals(orderOption)) {
      //修改时间排序 从大到小
      newArray = array.stream().sorted(
          Comparator.comparing(Project::getLastModifiedTimestamp).reversed())
          .collect(Collectors.toList());
    } else {
      //按照项目名称排序
      newArray = array.stream().sorted(
          Comparator.comparing(Project::getName, String.CASE_INSENSITIVE_ORDER))
          .collect(Collectors.toList());
    }


    return newArray;
  }

  public List<Project> getProjects(final String orderOption) {
    List<Project> projectList = new ArrayList<>(this.projectsById.values());
//    //按照项目名称排序
//    List<Project> newArray = projectList.stream().sorted(
//        Comparator.comparing(Project::getName, String.CASE_INSENSITIVE_ORDER)).collect(Collectors.toList());

    //按照项目名称排序
    List<Project> newArray = new ArrayList<>();

    if ("orderUpdateTimeSort".equals(orderOption)) {
      //修改时间排序 从大到小
      newArray = projectList.stream().sorted(
          Comparator.comparing(Project::getLastModifiedTimestamp).reversed())
          .collect(Collectors.toList());
    } else {
      //按照项目名称排序
      newArray = projectList.stream().sorted(
          Comparator.comparing(Project::getName, String.CASE_INSENSITIVE_ORDER))
          .collect(Collectors.toList());
    }


    return newArray;
  }

  public List<Project> getProjectsByRegex(final String regexPattern, final String orderOption) {
    final List<Project> allProjects = new ArrayList<>();
    final Pattern pattern;
    try {
      pattern = Pattern.compile(regexPattern, Pattern.CASE_INSENSITIVE);
    } catch (final PatternSyntaxException e) {
      logger.error("Bad regex pattern " + regexPattern);
      return allProjects;
    }
    for (final Project project : getProjects()) {
      if (pattern.matcher(project.getName()).find()
          || pattern.matcher(project.getDescription()).find()
          || pattern.matcher(project.getCreateUser()).find()) {
        allProjects.add(project);
      }
    }
//    //按照项目名称排序
//    List<Project> newArray = allProjects.stream().sorted(
//        Comparator.comparing(Project::getName, String.CASE_INSENSITIVE_ORDER)).collect(Collectors.toList());

    //按照项目名称排序
    List<Project> newArray = new ArrayList<>();

    if("orderUpdateTimeSort".equals(orderOption)){
      //修改时间排序 从大到小
      newArray = allProjects.stream().sorted(
          Comparator.comparing(Project::getLastModifiedTimestamp).reversed())
          .collect(Collectors.toList());
    } else {
      //按照项目名称排序
      newArray = allProjects.stream().sorted(
          Comparator.comparing(Project::getName, String.CASE_INSENSITIVE_ORDER))
          .collect(Collectors.toList());
    }

    return newArray;
  }

  public List<Project> getUserProjectsByRegex(final User user, final String regexPattern,
      final String orderOption) {
    final List<Project> array = new ArrayList<>();
    final Pattern pattern;
    try {
      pattern = Pattern.compile(regexPattern, Pattern.CASE_INSENSITIVE);
    } catch (final PatternSyntaxException e) {
      logger.error("Bad regex pattern " + regexPattern);
      return array;
    }
    for (final Project project : this.projectsById.values()) {
      final Permission perm = project.getUserPermission(user);

      if (perm != null
          && (perm.isPermissionSet(Type.ADMIN) || perm
          .isPermissionSet(Type.READ))) {
        if (pattern.matcher(project.getName()).find()
            || pattern.matcher(project.getDescription()).find()
            || pattern.matcher(project.getCreateUser()).find()) {
          array.add(project);
        }
      }
    }
//    //按照项目名称排序
//    List<Project> newArray = array.stream().sorted(
//        Comparator.comparing(Project::getName, String.CASE_INSENSITIVE_ORDER)).collect(Collectors.toList());

    //按照项目名称排序
    List<Project> newArray = new ArrayList<>();

    if("orderUpdateTimeSort".equals(orderOption)){
      //修改时间排序 从大到小
      newArray = array.stream().sorted(
          Comparator.comparing(Project::getLastModifiedTimestamp).reversed())
          .collect(Collectors.toList());
    } else {
      //按照项目名称排序
      newArray = array.stream().sorted(
          Comparator.comparing(Project::getName, String.CASE_INSENSITIVE_ORDER))
          .collect(Collectors.toList());
    }

    return newArray;
  }

  public List<Project> getMaintainedProjectsByRegex(User user, List<Integer> projectIds, String regexPattern, String orderOption) {
    final List<Project> array = new ArrayList<>();
    final Pattern pattern;
    try {
      pattern = Pattern.compile(regexPattern, Pattern.CASE_INSENSITIVE);
    } catch (final PatternSyntaxException e) {
      logger.error("Bad regex pattern " + regexPattern);
      return array;
    }
    for (final Project project : this.projectsById.values()) {
      final Permission permission = project.getUserPermission(user);
      Predicate<Permission> hasPermission = perm -> perm != null && (perm.isPermissionSet(Type.ADMIN) || perm.isPermissionSet(Type.READ));
      Predicate<Project> isMaintained = proj -> projectIds.contains(proj.getId());
      if (isMaintained.test(project) || hasPermission.test(permission)) {
        if (pattern.matcher(project.getName()).find()
                || pattern.matcher(project.getDescription()).find()
                || pattern.matcher(project.getCreateUser()).find()) {
          array.add(project);
        }
      }
    }
//    //按照项目名称排序
//    List<Project> newArray = array.stream().sorted(
//        Comparator.comparing(Project::getName, String.CASE_INSENSITIVE_ORDER)).collect(Collectors.toList());

    //按照项目名称排序
    List<Project> newArray = new ArrayList<>();

    if("orderUpdateTimeSort".equals(orderOption)){
      //修改时间排序 从大到小
      newArray = array.stream().sorted(
              Comparator.comparing(Project::getLastModifiedTimestamp).reversed())
              .collect(Collectors.toList());
    } else {
      //按照项目名称排序
      newArray = array.stream().sorted(
              Comparator.comparing(Project::getName, String.CASE_INSENSITIVE_ORDER))
              .collect(Collectors.toList());
    }

    return newArray;
  }

  /**
   * 获取当天创建的项目 但是未启动执行的工作流集合
   * @return
   * @throws ProjectManagerException
   */
  public List<Flow> getTodayCreateProjectNoRunNum(final String username) throws ProjectManagerException{
    List<Flow> todayCreateFlowNoRunList = new ArrayList<>();

    List<Project> todayCreateProjectList = this.projectLoader.getTodayCreateProjects(username);

    for(Project project : todayCreateProjectList){
      List<Flow> flows = this.getProject(project.getId()).getFlows();
      for(Flow flow : flows){
        int todayRunCount = this.projectLoader.getTodayRunFlow(project.getId(), flow.getId());
        if(todayRunCount == 0){
          todayCreateFlowNoRunList.add(flow);
        }
      }
    }

    return todayCreateFlowNoRunList;
  }

  /**
   * 获取当天创建的项目 但是未启动执行的工作流集合
   * @return
   * @throws ProjectManagerException
   */
  public List<Map> getTodayCreateProjectNoRunFlowInfo(final String username) throws ProjectManagerException{
    List<Map> todayCreateFlowNoRunFlowList = new ArrayList<>();

    List<Project> todayCreateProjectList = this.projectLoader.getTodayCreateProjects(username);

    for(Project project : todayCreateProjectList){
      List<Flow> flows = this.getProject(project.getId()).getFlows();
      for(Flow flow : flows){
        int todayRunCount = this.projectLoader.getTodayRunFlow(project.getId(), flow.getId());
        if(todayRunCount == 0){
          Map<String, String> flowInfo = new HashMap<>();
          flowInfo.put("ProjectName", project.getName());
          flowInfo.put("flowName", flow.getId());
          flowInfo.put("projectUser", project.getLastModifiedUser());
          todayCreateFlowNoRunFlowList.add(flowInfo);
        }
      }
    }

    return todayCreateFlowNoRunFlowList;
  }

  public void refreshProjectPermission(String projectName) {
    final Project project = this.getProject(projectName);
    if (project != null) {
      List<ProjectPermission> projectPermissionList = this.projectLoader.fetchAllPermissionsForProject(project);
      if (CollectionUtils.isNotEmpty(projectPermissionList)) {
        project.clearUserPermission();
        project.clearGroupPermission();
        for (ProjectPermission projectPermission : projectPermissionList) {
          if (projectPermission.getIsGroup()) {
            project.setGroupPermission(projectPermission.getProjectGroup(), projectPermission.getPermission());
          }
          project.setUserPermission(projectPermission.getUsername(), projectPermission.getPermission());
        }
      }
    }
  }

  public void reloadProject(String projectName) {
    Project project = this.projectLoader.fetchProjectByName(projectName);
    Project pro = this.projectsByName.get(project.getName());
    if (pro != null) {
      if (pro.getVersion() != project.getVersion()) {
        loadAllProjectFlows(project);
      } else {
        project.setFlows(pro.getFlowMap());
      }
    } else {
      loadAllProjectFlows(project);
    }
    this.projectsByName.remove(project.getName());
    this.projectsById.remove(project.getId());
    if (project.isActive()) {
      this.projectsByName.put(project.getName(), project);
      this.projectsById.put(project.getId(), project);
    }
  }

  public void deleteProjectByWeb(int projectId) {
    this.projectsByName.remove(this.projectsById.get(projectId).getName());
    this.projectsById.remove(projectId);
  }

  public List<ProjectVersion> getProjectVersions(final Project project,final int num,final int skip) throws ProjectManagerException {
    return this.projectLoader.getProjectVersions(project, num, skip);
  }
}
