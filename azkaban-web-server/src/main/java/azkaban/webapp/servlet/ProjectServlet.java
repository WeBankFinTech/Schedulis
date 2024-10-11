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

package azkaban.webapp.servlet;

import azkaban.project.Project;
import azkaban.project.ProjectManager;
import azkaban.server.session.Session;
import azkaban.user.Permission;
import azkaban.user.Permission.Type;
import azkaban.user.User;
import azkaban.user.UserUtils;
import azkaban.utils.Pair;
import azkaban.utils.WebUtils;
import azkaban.webapp.AzkabanWebServer;
import com.webank.wedatasphere.schedulis.common.i18nutils.LoadJsonUtils;
import com.webank.wedatasphere.schedulis.common.system.SystemManager;
import com.webank.wedatasphere.schedulis.common.system.common.TransitionService;
import com.webank.wedatasphere.schedulis.common.utils.PagingListStreamUtil;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.slf4j.LoggerFactory;
import org.slf4j.Logger;

/**
 * The main page
 */
public class ProjectServlet extends LoginAbstractAzkabanServlet {

  private static final Logger logger = LoggerFactory.getLogger(ProjectServlet.class.getName());
  private static final String LOCKDOWN_CREATE_PROJECTS_KEY = "lockdown.create.projects";
  private static final long serialVersionUID = -1;
  private TransitionService transitionService;
  private SystemManager systemManager;

  private boolean lockdownCreateProjects = false;

  @Override
  public void init(final ServletConfig config) throws ServletException {
    super.init(config);
    final AzkabanWebServer server = (AzkabanWebServer) getApplication();

    this.lockdownCreateProjects =
        server.getServerProps().getBoolean(LOCKDOWN_CREATE_PROJECTS_KEY, false);
    if (this.lockdownCreateProjects) {
      logger.info("Creation of projects is locked down");
    }
    this.transitionService = server.getTransitionService();
    this.systemManager = transitionService.getSystemManager();
  }

  @Override
  protected void handleGet(final HttpServletRequest req, final HttpServletResponse resp,
      final Session session) throws ServletException, IOException {

    final ProjectManager manager =
        ((AzkabanWebServer) getApplication()).getProjectManager();

    if (hasParam(req, "ajax")) {
      handleAjaxAction(req, resp, session, manager);
    } else if (hasParam(req, "doaction")) {
      handleDoAction(req, resp, session);
    } else {
      handlePageRender(req, resp, session, manager);
    }
  }

  /**
   * ProjectServlet class now handles ajax requests. It returns a
   *
   * @SimplifiedProject object: information regarding projects, and information regarding user and
   * project association
   */
  private void handleAjaxAction(final HttpServletRequest req,
      final HttpServletResponse resp, final Session session, final ProjectManager manager)
      throws ServletException, IOException {

    final String ajaxName = getParam(req, "ajax");
    final HashMap<String, Object> ret = new HashMap<>();

    if (ajaxName.equals("fetchallprojects")) {
      final List<Project> projects = manager.getProjects();
      final List<SimplifiedProject> simplifiedProjects =
          toSimplifiedProjects(projects);
      ret.put("projects", simplifiedProjects);
    } else if (ajaxName.equals("fetchuserprojects")) {
      handleFetchUserProjects(req, session, manager, ret);
    } else if (ajaxName.equals("fetchProjectPage")) {
      handleProjectPage(req, resp, session, manager, ret);
    } else if (ajaxName.equals("getProjectPageLanguageType")) {
      ajaxGetProjectPageLanguageType(req, resp, session, ret);
    }

    this.writeJSON(resp, ret);
  }

  /**
   * We know the intention of API call is to return project ownership based on given user. <br> If
   * user provides an user name, the method honors it <br> If user provides an empty user name, the
   * user defaults to the session user<br> If user does not provide the user param, the user also
   * defaults to the session user<br>
   */
  private void handleFetchUserProjects(final HttpServletRequest req, final Session session,
      final ProjectManager manager, final HashMap<String, Object> ret)
      throws ServletException {
    User user = null;

    // if key "user" is specified, follow this logic
    if (hasParam(req, "user")) {
      final String userParam = getParam(req, "user");
      if (userParam.isEmpty()) {
        user = session.getUser();
      } else {
        user = new User(userParam);
      }
    } else {
      // if key "user" is not specified, default to the session user
      user = session.getUser();
    }

    final List<Project> projects = manager.getUserProjects(user);
    final List<SimplifiedProject> simplifiedProjects = toSimplifiedProjects(projects);
    ret.put("projects", simplifiedProjects);
  }

  /**
   * A simple helper method that converts a List<Project> to List<SimplifiedProject>
   */
  private List<SimplifiedProject> toSimplifiedProjects(final List<Project> projects) {
    final List<SimplifiedProject> simplifiedProjects = new ArrayList<>();
    for (final Project p : projects) {
      final SimplifiedProject sp =
          new SimplifiedProject(p.getId(), p.getName(),
              p.getLastModifiedUser(), p.getCreateTimestamp(),
              p.getUserPermissions(), p.getGroupPermissions());
      simplifiedProjects.add(sp);
    }
    return simplifiedProjects;
  }

  /**
   * 读取 index.vm 及其子页面的国际化资源数据
   * @return
   */
  private Map<String, Map<String, String>> loadIndexpageI18nData() {

    Map<String, Map<String, String>> dataMap = new HashMap<>();
    String languageType = LoadJsonUtils.getLanguageType();
    Map<String, String> indexMap;
    Map<String, String> subPageMap1;
    if (languageType.equalsIgnoreCase("zh_CN")) {
      // 添加国际化标签
      indexMap = LoadJsonUtils.transJson("/com.webank.wedatasphere.schedulis.i18n.conf/azkaban-web-server-zh_CN.json",
          "azkaban.webapp.servlet.velocity.index.vm");
      subPageMap1 = LoadJsonUtils.transJson("/com.webank.wedatasphere.schedulis.i18n.conf/azkaban-web-server-zh_CN.json",
          "azkaban.webapp.servlet.velocity.nav.vm");
    }else {
      indexMap = LoadJsonUtils.transJson("/com.webank.wedatasphere.schedulis.i18n.conf/azkaban-web-server-en_US.json",
          "azkaban.webapp.servlet.velocity.index.vm");
      subPageMap1 = LoadJsonUtils.transJson("/com.webank.wedatasphere.schedulis.i18n.conf/azkaban-web-server-en_US.json",
          "azkaban.webapp.servlet.velocity.nav.vm");
    }

    dataMap.put("index.vm", indexMap);
    dataMap.put("nav.vm", subPageMap1);
    return dataMap;
  }

  /**
   * Renders the user homepage that users see when they log in
   */
  private void handlePageRender(final HttpServletRequest req,
      final HttpServletResponse resp, final Session session, final ProjectManager manager) {
    final User user = session.getUser();

    final Page page = newPage(req, resp, session, "azkaban/webapp/servlet/velocity/index.vm");

    // FIXME Load international resources.
    Map<String, Map<String, String>> vmDataMap = loadIndexpageI18nData();
    vmDataMap.forEach((vm, data) -> data.forEach(page::add));

    page.add("userGroups", user.getGroups());

    if (this.lockdownCreateProjects && !UserUtils.hasPermissionforAction(user, Type.CREATEPROJECTS)) {
      page.add("hideCreateProject", true);
    }

    if (hasParam(req, "all")) {
      final List<Project> projects;
      // FIXME Add permission judgment, admin user can view all projects, user user can only view their own projects.
      if(user.getRoles().contains("admin")){
        projects = manager.getProjects();
      }else{//user用户只能查看自己的Project
        projects = manager.getUserProjects(user);
      }
      page.add("viewProjects", "all");
      page.add("projects", projects);
    } else if (hasParam(req, "group")) {
      final List<Project> projects = manager.getGroupProjects(user);
      page.add("viewProjects", "group");
      page.add("projects", projects);
    } else {
      final List<Project> projects = manager.getUserProjects(user);
      page.add("viewProjects", "personal");
      page.add("projects", projects);
    }
    String languageType = LoadJsonUtils.getLanguageType();

    page.add("currentlangType", languageType);
    page.render();
  }

  private void handleDoAction(final HttpServletRequest req, final HttpServletResponse resp,
      final Session session) throws ServletException {
    if (getParam(req, "doaction").equals("search")) {
      //去除搜索字符串的空格
      final String searchTerm = getParam(req, "searchterm").trim();
      if (!searchTerm.equals("") && !searchTerm.equals(".*")) {
        handleFilter(req, resp, session, searchTerm);
        return;
      }else{
        final ProjectManager manager =
            ((AzkabanWebServer) getApplication()).getProjectManager();
        final User user = session.getUser();
        final Page page = newPage(req, resp, session, "azkaban/webapp/servlet/velocity/index.vm");

        // 加载国际化资源
        Map<String, Map<String, String>> vmDataMap = loadIndexpageI18nData();
        vmDataMap.forEach((vm, data) -> data.forEach(page::add));

        page.add("userGroups", user.getGroups());

        //final List<Project> projects = manager.getUserProjects(user);
		    // FIXME Add permission judgment, admin user can view all projects, user user can only view their own projects.
        if (hasParam(req, "all")) {
          final List<Project> projects;
          //添加权限判断 admin 用户能查看所有Project
          if(user.getRoles().contains("admin")){
            projects = manager.getProjects();
          }else{//user用户只能查看自己的Project
            projects = manager.getUserProjects(user);
          }
          page.add("viewProjects", "all");
          page.add("projects", projects);
        }else {
          final List<Project> projects = manager.getUserProjects(user);
          page.add("viewProjects", "personal");
          page.add("projects", projects);
        }
        String languageType = LoadJsonUtils.getLanguageType();

        page.add("currentlangType", languageType);

        page.render();
      }
    }
  }

  private void handleFilter(final HttpServletRequest req, final HttpServletResponse resp,
      final Session session, final String searchTerm) {
    final User user = session.getUser();
    final ProjectManager manager =
        ((AzkabanWebServer) getApplication()).getProjectManager();
    final Page page =
        newPage(req, resp, session, "azkaban/webapp/servlet/velocity/index.vm");

    // 加载国际化资源
    Map<String, Map<String, String>> vmDataMap = loadIndexpageI18nData();
    vmDataMap.forEach((vm, data) -> data.forEach(page::add));

    page.add("userGroups", user.getGroups());

    if (hasParam(req, "all")) {
      // do nothing special if one asks for 'ALL' projects
      final List<Project> projects = manager.getProjectsByRegex(searchTerm);
      page.add("allProjects", "");
      page.add("projects", projects);
      page.add("search_term", searchTerm);
      page.add("viewProjects", "all");
    } else if (hasParam(req, "group")) {
      page.add("search_term", searchTerm);
      page.add("viewProjects", "group");
    } else {
      final List<Project> projects = manager.getUserProjectsByRegex(user, searchTerm);
      page.add("projects", projects);
      page.add("search_term", searchTerm);
      page.add("viewProjects", "personal");
    }
    String languageType = LoadJsonUtils.getLanguageType();

    page.add("currentlangType", languageType);
    page.render();
  }

  @Override
  protected void handlePost(final HttpServletRequest req, final HttpServletResponse resp,
      final Session session) throws ServletException, IOException {
    // TODO Auto-generated method stub
  }

  /**
   * This class is used to represent a simplified project, which can be returned to end users via
   * REST API. This is done in consideration that the API caller only wants certain project level
   * information regarding a project, but does not want every flow and every job inside that
   * project.
   *
   * @author jyu
   */
  private static class SimplifiedProject {

    private int projectId;
    private String projectName;
    private String createdBy;
    private long createdTime;
    private List<Pair<String, Permission>> userPermissions;
    private List<Pair<String, Permission>> groupPermissions;

    public SimplifiedProject(final int projectId, final String projectName,
        final String createdBy, final long createdTime,
        final List<Pair<String, Permission>> userPermissions,
        final List<Pair<String, Permission>> groupPermissions) {
      this.projectId = projectId;
      this.projectName = projectName;
      this.createdBy = createdBy;
      this.createdTime = createdTime;
      this.userPermissions = userPermissions;
      this.groupPermissions = groupPermissions;
    }

    public int getProjectId() {
      return this.projectId;
    }

    public void setProjectId(final int projectId) {
      this.projectId = projectId;
    }

    public String getProjectName() {
      return this.projectName;
    }

    public void setProjectName(final String projectName) {
      this.projectName = projectName;
    }

    public String getCreatedBy() {
      return this.createdBy;
    }

    public void setCreatedBy(final String createdBy) {
      this.createdBy = createdBy;
    }

    public long getCreatedTime() {
      return this.createdTime;
    }

    public void setCreatedTime(final long createdTime) {
      this.createdTime = createdTime;
    }

    public List<Pair<String, Permission>> getUserPermissions() {
      return this.userPermissions;
    }

    public void setUserPermissions(
        final List<Pair<String, Permission>> userPermissions) {
      this.userPermissions = userPermissions;
    }

    public List<Pair<String, Permission>> getGroupPermissions() {
      return this.groupPermissions;
    }

    public void setGroupPermissions(
        final List<Pair<String, Permission>> groupPermissions) {
      this.groupPermissions = groupPermissions;
    }

  }

  /**
   * 获取当前语言
   * @param req
   * @param resp
   * @param session
   * @param ret
   */
  private void ajaxGetProjectPageLanguageType(final HttpServletRequest req, final HttpServletResponse resp,
      final Session session, final HashMap<String, Object> ret) {

    try {
      String languageType = LoadJsonUtils.getLanguageType();
      ret.put("langType", languageType);
    } catch (Exception e) {
      ret.put("error", e.getMessage());
      logger.error("languageType load error: caused by:", e);
    }
  }

  /**
   * 项目页面 项目分页处理方法
   */
  private void handleProjectPage(final HttpServletRequest req,
      final HttpServletResponse resp, final Session session, final ProjectManager manager,
      final HashMap<String, Object> ret) {
    final User user = session.getUser();

    try {
      final int start = getIntParam(req, "start");
      final int pageSize = getIntParam(req, "length");
      final String projectsType = getParam(req, "projectsType");
      final int pageNum = getIntParam(req, "pageNum");
      final String orderOption = getParam(req, "order");

      //如果输入了快捷搜索
      if ("doaction".equals(projectsType) && hasParam(req, "doaction")) {
        //去除搜索字符串的空格
        final String searchTerm = getParam(req, "searchterm").trim();
        if ("true".equals(getParam(req, "all"))) {
          //添加权限判断 admin 用户能查看所有Project
          if(user.getRoles().contains("admin")){
            final List<Project> searchProjects = manager.getProjectsByRegex(searchTerm, orderOption);
            final PagingListStreamUtil<Project> pageProjectsList = manager.getUserProjectsPage(pageNum, pageSize, searchProjects);
            assemblerProjectData(pageProjectsList.currentPageData(), searchProjects.size(), start, pageSize, ret, user);
            // 运维管理员可以查看该运维管理员运维部门下的所有工程
          } else if (systemManager.isDepartmentMaintainer(user)) {
            final List<Integer> maintainedProjectIds = systemManager.getMaintainedProjects(user);
            final List<Project> searchProjects = manager.getMaintainedProjectsByRegex(user, maintainedProjectIds,searchTerm, orderOption);
            final PagingListStreamUtil<Project> pageProjectsList = manager.getUserProjectsPage(pageNum, pageSize, searchProjects);
            assemblerProjectData(pageProjectsList.currentPageData(), searchProjects.size(), start, pageSize, ret, user);
          } else {//user用户只能查看自己的Project
            final List<Project> searchUserProject =
                manager.getUserProjectsByRegex(user, searchTerm, orderOption);

            if(searchUserProject.size() > 0) {

              final PagingListStreamUtil<Project> pageProjectsList = manager.getUserProjectsPage(pageNum, pageSize, searchUserProject);

              assemblerProjectData(pageProjectsList.currentPageData(), searchUserProject.size(), start, pageSize, ret, user);
            } else {
              ret.put("total", 0);
            }
          }

        } else {//只查获自己创建的项目
          final List<Project> searchUserProject =
              manager.getUserPersonProjectsByRegex(user, searchTerm, orderOption);
          final PagingListStreamUtil<Project> pageProjectsList
              = manager.getUserProjectsPage(pageNum, pageSize, searchUserProject);

          assemblerProjectData(pageProjectsList.currentPageData(),
              searchUserProject.size(), start, pageSize, ret, user);
        }
      }else{
        if ("all".equals(projectsType)) {
          final List<Project> projects;
          //添加权限判断 admin 用户能查看所有Project
          if(user.getRoles().contains("admin")){

            projects = manager.getProjects(orderOption);

            final PagingListStreamUtil<Project> pageProjectsList
                = manager.getAllProjectsPage(pageNum, pageSize, projects);

            assemblerProjectData(pageProjectsList.currentPageData(),
                projects.size(), start, pageSize, ret, user);

          } else if (systemManager.isDepartmentMaintainer(user)) {
              List<Integer> maintainedProjectIds = systemManager.getMaintainedProjects(user);
            projects = manager.getMaintainedProjects(user, maintainedProjectIds, orderOption);
            final PagingListStreamUtil<Project> pageProjectsList
                    = manager.getAllProjectsPage(pageNum, pageSize, projects);

            assemblerProjectData(pageProjectsList.currentPageData(),
                    projects.size(), start, pageSize, ret, user);
          } else{//user用户只能查看自己有权限的项目
            projects = manager.getUserAllProjects(user, orderOption);

            if(projects.size() > 0) {
              final PagingListStreamUtil<Project> pageProjectsList
                  = manager.getUserProjectsPage(pageNum, pageSize, projects);

              assemblerProjectData(pageProjectsList.currentPageData(),
                  projects.size(), start, pageSize, ret, user);
            }else {
              ret.put("total", 0);
            }
          }
        } else {//只查获自己创建的项目

          final List<Project> projects = manager.getUserProjects(user, orderOption);

          if(projects.size() > 0){
            final PagingListStreamUtil<Project> pageProjectsList
                = manager.getUserProjectsPage(pageNum, pageSize, projects);

            assemblerProjectData(pageProjectsList.currentPageData(),
                projects.size(), start, pageSize, ret, user);


          }else {
            ret.put("total", 0);
          }
        }
      }
    } catch (ServletException e) {
      e.printStackTrace();
    }
  }

  private void assemblerProjectData(List<Project> projectList,
      int total, int start, int pageSize, HashMap<String, Object> ret, User user){
    List<Map<String, String>> projectMapList = new ArrayList<>();
    WebUtils webUtils = new WebUtils();
    boolean isAdmin = user.getRoles().contains("admin");
    for(Project project : projectList){
      Map<String, String> pmap = new HashMap<>();
      pmap.put("name",project.getName());
      pmap.put("description",project.getDescription());
      pmap.put("lastModifiedUser",project.getLastModifiedUser());
      pmap.put("lastModifiedTimestamp", webUtils.formatDateTime(project.getLastModifiedTimestamp()));
      // 页面删除按钮显示判断
      if(isAdmin || hasPermission(project, user, Type.ADMIN)
          || hasPermission(project, user, Type.DEPMAINTAINER)){
        pmap.put("showDeleteBtn", "true");
      }
      projectMapList.add(pmap);
    }

    ret.put("total", total);
    ret.put("from", start);
    ret.put("length", pageSize);
    ret.put("projectList", projectMapList);

  }

}
