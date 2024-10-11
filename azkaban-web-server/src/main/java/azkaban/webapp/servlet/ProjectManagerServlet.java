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

import azkaban.Constants.ConfigurationKeys;
import azkaban.executor.ExecutableFlow;
import azkaban.executor.ExecutableFlowBase;
import azkaban.executor.ExecutableJobInfo;
import azkaban.executor.ExecutableNode;
import azkaban.executor.ExecutorManagerAdapter;
import azkaban.executor.ExecutorManagerException;
import azkaban.executor.Status;
import azkaban.flow.Edge;
import azkaban.flow.Flow;
import azkaban.flow.FlowProps;
import azkaban.flow.FlowUtils;
import azkaban.flow.Node;
import azkaban.flowtrigger.quartz.FlowTriggerScheduler;
import azkaban.project.Project;
import azkaban.project.ProjectFileHandler;
import azkaban.project.ProjectLogEvent;
import azkaban.project.ProjectManager;
import azkaban.project.ProjectManagerException;
import azkaban.project.ProjectVersion;
import azkaban.project.ProjectWhitelist;
import azkaban.project.validator.ValidationReport;
import azkaban.project.validator.ValidatorConfigs;
import azkaban.scheduler.Schedule;
import azkaban.scheduler.ScheduleManager;
import azkaban.scheduler.ScheduleManagerException;
import azkaban.server.session.Session;
import azkaban.user.Permission;
import azkaban.user.Permission.Type;
import azkaban.user.Role;
import azkaban.user.User;
import azkaban.user.UserUtils;
import azkaban.utils.JSONUtils;
import azkaban.utils.Pair;
import azkaban.utils.Props;
import azkaban.utils.PropsUtils;
import azkaban.utils.Utils;
import azkaban.webapp.AzkabanWebServer;
import com.webank.wedatasphere.schedulis.common.i18nutils.LoadJsonUtils;
import com.webank.wedatasphere.schedulis.common.system.SystemManager;
import com.webank.wedatasphere.schedulis.common.system.SystemUserManagerException;
import com.webank.wedatasphere.schedulis.common.system.common.TransitionService;
import com.webank.wedatasphere.schedulis.common.system.entity.WebankDepartment;
import com.webank.wedatasphere.schedulis.common.system.entity.WtssUser;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.Writer;
import java.security.AccessControlException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.apache.commons.fileupload.FileItem;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang.ArrayUtils;
import org.apache.commons.lang.StringEscapeUtils;
import org.apache.commons.lang.StringUtils;
import org.slf4j.LoggerFactory;
import org.slf4j.Logger;
import org.joda.time.LocalDateTime;
import org.quartz.SchedulerException;

public class ProjectManagerServlet extends LoginAbstractAzkabanServlet {

    private static final String APPLICATION_ZIP_MIME_TYPE = "application/zip";
    private static String RE_SPACE = "(\u0020|\u3000)";
    private static final long serialVersionUID = 1;
    private static final Logger logger = LoggerFactory.getLogger(ProjectManagerServlet.class);
    private static final NodeLevelComparator NODE_LEVEL_COMPARATOR =
        new NodeLevelComparator();
    private static final String LOCKDOWN_CREATE_PROJECTS_KEY = "lockdown.create.projects";
    private static final String LOCKDOWN_UPLOAD_PROJECTS_KEY = "lockdown.upload.projects";
    private static final String WTSS_PROJECT_PRIVILEGE_CHECK = "schedulis.project.privilege.check";
    private static final String WTSS_DEP_UPLOAD_PRIVILEGE_CHECK = "wtss.department.upload.privilege.check";
    private static final String PROJECT_DOWNLOAD_BUFFER_SIZE_IN_BYTES = "project.download.buffer.size";
    private static final Comparator<Flow> FLOW_ID_COMPARATOR = new Comparator<Flow>() {
        @Override
        public int compare(final Flow f1, final Flow f2) {
            return f1.getId().compareTo(f2.getId());
        }
    };
    private ProjectManager projectManager;
    private ExecutorManagerAdapter executorManagerAdapter;
    private ScheduleManager scheduleManager;
    private TransitionService transitionService;
    private SystemManager systemManager;
    private FlowTriggerScheduler scheduler;
    private int downloadBufferSize;
    private boolean lockdownCreateProjects = false;
    private boolean lockdownUploadProjects = false;

    // 项目权限管控开关
    private static boolean wtss_project_privilege_check;

    // 部门上传权限管控开关
    private static boolean wtss_dep_upload_privilege_check;
    private boolean enableQuartz = false;

    @Override
    public void init(final ServletConfig config) throws ServletException {
        super.init(config);

        final AzkabanWebServer server = (AzkabanWebServer) getApplication();
        this.projectManager = server.getProjectManager();
        this.executorManagerAdapter = server.getExecutorManager();
        this.scheduleManager = server.getScheduleManager();
        this.transitionService = server.getTransitionService();
        this.systemManager = transitionService.getSystemManager();
        this.scheduler = server.getScheduler();
        this.lockdownCreateProjects = server.getServerProps().getBoolean(LOCKDOWN_CREATE_PROJECTS_KEY, false);

        wtss_project_privilege_check = server.getServerProps().getBoolean(WTSS_PROJECT_PRIVILEGE_CHECK, false);
        wtss_dep_upload_privilege_check = server.getServerProps().getBoolean(WTSS_DEP_UPLOAD_PRIVILEGE_CHECK, false);
        this.enableQuartz = server.getServerProps().getBoolean(ConfigurationKeys.ENABLE_QUARTZ, false);
        if (this.lockdownCreateProjects) {
            logger.info("Creation of projects is locked down");
        }

        this.lockdownUploadProjects =
            server.getServerProps().getBoolean(LOCKDOWN_UPLOAD_PROJECTS_KEY, false);
        if (this.lockdownUploadProjects) {
            logger.info("Uploading of projects is locked down");
        }

        this.downloadBufferSize =
            server.getServerProps().getInt(PROJECT_DOWNLOAD_BUFFER_SIZE_IN_BYTES,
                8192);

        logger.info("downloadBufferSize: " + this.downloadBufferSize);
    }

    @Override
    protected void handleGet(final HttpServletRequest req, final HttpServletResponse resp,
        final Session session) throws ServletException, IOException {

        if (hasParam(req, "project")) {
            if (hasParam(req, "ajax")) {
                handleAJAXAction(req, resp, session);
            } else if (hasParam(req, "logs")) {
                handleProjectLogsPage(req, resp, session);
            } else if (hasParam(req, "permissions")) {
                handlePermissionPage(req, resp, session);
            } else if (hasParam(req, "versions")) {
                handleProjectVersionsPage(req, resp, session);
            } else if (hasParam(req, "prop")) {
                handlePropertyPage(req, resp, session);
            } else if (hasParam(req, "history")) {
                handleJobHistoryPage(req, resp, session);
            } else if (hasParam(req, "treeFlow")) {
                handleFlowDetailPage(req, resp, session);
            } else if (hasParam(req, "job")) {
                handleJobPage(req, resp, session);
            } else if (hasParam(req, "flow")) {
                handleFlowPage(req, resp, session);
            } else if (hasParam(req, "delete")) {
                handleRemoveProject(req, resp, session);
            } else if (hasParam(req, "purge")) {
                handlePurgeProject(req, resp, session);
            } else if (hasParam(req, "download")) {
                handleDownloadProject(req, resp, session);
            } else {
                handleProjectPage(req, resp, session);
            }
            return;
        } else if (hasParam(req, "reloadProjectWhitelist")) {
            handleReloadProjectWhitelist(req, resp, session);
        } else if ("/manager".equals(req.getRequestURI())) {
            if (hasParam(req, "ajax")) {
                handleAJAXAction(req, resp, session);
            }
            return;
        }

        final Page page =
            newPage(req, resp, session,
                "azkaban/webapp/servlet/velocity/projectpage.vm");
        page.add("errorMsg", "No project set.");
        page.render();
    }

    private void handleProjectVersionsPage(final HttpServletRequest req, final HttpServletResponse resp,
                                           final Session session) throws ServletException, IOException {

        final Page page = newPage(req, resp, session,"azkaban/webapp/servlet/velocity/projectversionpage.vm");

        Map<String, String> projectVersionPageMap;
        Map<String, String> subPageMap1;
        Map<String, String> subPageMap2;
        Map<String, String> subPageMap3;
        Map<String, String> subPageMap4;
        Map<String, String> subPageMap5;

        String languageType = LoadJsonUtils.getLanguageType();
        if ("zh_CN".equalsIgnoreCase(languageType)) {

            // 添加国际化标签
            projectVersionPageMap = LoadJsonUtils.transJson("/com.webank.wedatasphere.schedulis.i18n.conf/azkaban-web-server-zh_CN.json",
                    "azkaban.webapp.servlet.velocity.projectversionpage.vm");
            subPageMap1 = LoadJsonUtils.transJson("/com.webank.wedatasphere.schedulis.i18n.conf/azkaban-web-server-zh_CN.json",
                    "azkaban.webapp.servlet.velocity.nav.vm");
            subPageMap2 = LoadJsonUtils.transJson("/com.webank.wedatasphere.schedulis.i18n.conf/azkaban-web-server-zh_CN.json",
                    "azkaban.webapp.servlet.velocity.projectpageheader.vm");
            subPageMap3 = LoadJsonUtils.transJson("/com.webank.wedatasphere.schedulis.i18n.conf/azkaban-web-server-zh_CN.json",
                    "azkaban.webapp.servlet.velocity.projectnav.vm");
            subPageMap4 = LoadJsonUtils.transJson("/com.webank.wedatasphere.schedulis.i18n.conf/azkaban-web-server-zh_CN.json",
                    "azkaban.webapp.servlet.velocity.projectsidebar.vm");
            subPageMap5 = LoadJsonUtils.transJson("/com.webank.wedatasphere.schedulis.i18n.conf/azkaban-web-server-zh_CN.json",
                    "azkaban.webapp.servlet.velocity.projectmodals.vm");
        } else {
            // 添加国际化标签
            projectVersionPageMap = LoadJsonUtils.transJson("/com.webank.wedatasphere.schedulis.i18n.conf/azkaban-web-server-en_US.json",
                    "azkaban.webapp.servlet.velocity.projectversionpage.vm");
            subPageMap1 = LoadJsonUtils.transJson("/com.webank.wedatasphere.schedulis.i18n.conf/azkaban-web-server-en_US.json",
                    "azkaban.webapp.servlet.velocity.nav.vm");
            subPageMap2 = LoadJsonUtils.transJson("/com.webank.wedatasphere.schedulis.i18n.conf/azkaban-web-server-en_US.json",
                    "azkaban.webapp.servlet.velocity.projectpageheader.vm");
            subPageMap3 = LoadJsonUtils.transJson("/com.webank.wedatasphere.schedulis.i18n.conf/azkaban-web-server-en_US.json",
                    "azkaban.webapp.servlet.velocity.projectnav.vm");
            subPageMap4 = LoadJsonUtils.transJson("/com.webank.wedatasphere.schedulis.i18n.conf/azkaban-web-server-en_US.json",
                    "azkaban.webapp.servlet.velocity.projectsidebar.vm");
            subPageMap5 = LoadJsonUtils.transJson("/com.webank.wedatasphere.schedulis.i18n.conf/azkaban-web-server-en_US.json",
                    "azkaban.webapp.servlet.velocity.projectmodals.vm");
        }

        projectVersionPageMap.forEach(page::add);
        subPageMap1.forEach(page::add);
        subPageMap2.forEach(page::add);
        subPageMap3.forEach(page::add);
        subPageMap4.forEach(page::add);
        subPageMap5.forEach(page::add);
        page.add("currentlangType", languageType);

        final String projectName = getParam(req, "project");

        final User user = session.getUser();
        PageUtils.hideUploadButtonWhenNeeded(page, session, this.lockdownUploadProjects);
        Project project = null;
        try {
            project = this.projectManager.getProject(projectName);
            if (project == null) {
                page.add("errorMsg", "项目 " + projectName + " 不存在.");
            } else {
                if (!hasPermission(project, user, Type.READ)) {
                    throw new AccessControlException("没有权限查看这个项目 " + projectName + ".");
                }

                page.add("project", project);
                page.add("admins", Utils.flattenToString(project.getUsersWithPermission(Type.ADMIN), ","));
                final Permission perm = this.getPermissionObject(project, user, Type.ADMIN);
                page.add("userpermission", perm);

                final boolean adminPerm = perm.isPermissionSet(Type.ADMIN);
                if (adminPerm) {
                    page.add("admin", true);
                }
                // Set this so we can display execute buttons only to those who have
                // access.
                if (perm.isPermissionSet(Type.EXECUTE) || adminPerm) {
                    page.add("exec", true);
                } else {
                    page.add("exec", false);
                }

                if (user.getRoles().contains("admin")) {
                    page.add("isSystemAdmin", true);
                }

                if (hasPermission(project, user, Type.ADMIN)) {
                    page.add("isProjectAdmin", true);
                }

                int uploadFlag;
                // 先判断开关是否打开,如果开关打开,则校验部门上传权限,如果关闭,则不需要校验
                // 判断是否具有上传权限  uploadFlag 1:允许, 2:不允许,其他值:不允许
                if (wtss_dep_upload_privilege_check) {
                    uploadFlag = checkDepartmentUploadFlag(user);
                } else {
                    uploadFlag = 1;
                }

                // 需要首先验证部门上传权限是否被允许, 再判断是否满足原有上传许可的逻辑
                if ((uploadFlag == 1) && (perm.isPermissionSet(Type.WRITE) || adminPerm)) {
                    page.add("isWritePerm", true);
                }

            }
        } catch (final AccessControlException e) {
            page.add("errorMsg", e.getMessage());
        } catch (SystemUserManagerException e) {
            logger.error("部门上传权限标识查询异常.");
        }

        page.render();
    }

    @Override
    protected void handleMultiformPost(final HttpServletRequest req,
        final HttpServletResponse resp, final Map<String, Object> params, final Session session)
        throws ServletException, IOException {
        // Looks like a duplicate, but this is a move away from the regular
        // multiform post + redirect
        // to a more ajax like command.
        if (params.containsKey("ajax")) {
            final String action = (String) params.get("ajax");
            final HashMap<String, String> ret = new HashMap<>();
            if (action.equals("upload")) {
                ajaxHandleUpload(req, resp, ret, params, session);
            }
            this.writeJSON(resp, ret);
        } else if (params.containsKey("action")) {
            final String action = (String) params.get("action");
            if (action.equals("upload")) {
                handleUpload(req, resp, params, session);
            }
        }
    }

    @Override
    protected void handlePost(final HttpServletRequest req, final HttpServletResponse resp,
        final Session session) throws ServletException, IOException {
        if (hasParam(req, "action")) {
            final String action = getParam(req, "action");
            if (action.equals("create")) {
                handleCreate(req, resp, session);
            }
        }
    }

    private void handleAJAXAction(final HttpServletRequest req,
        final HttpServletResponse resp, final Session session) throws ServletException,
        IOException {
        final HashMap<String, Object> ret = new HashMap<>();
        final User user = session.getUser();

        if (hasParam(req, "project")) {
            final String projectName = getParam(req, "project");
            ret.put("project", projectName);

            final Project project = this.projectManager.getProject(projectName);
            if (project == null) {
                ret.put("error", "Project " + projectName + " doesn't exist.");
            } else {
                ret.put("projectId", project.getId());
                final String ajaxName = getParam(req, "ajax");
                if (ajaxName.equals("getProjectId")) {
                    // Do nothing, since projectId is added to all AJAX requests.
                } else if (ajaxName.equals("fetchProjectVersions")) {
                    if (handleAjaxPermission(project, user, Type.READ, ret)) {
                        ajaxFetchProjectVersions(project, req, ret);
                    }
                }else if (ajaxName.equals("fetchProjectLogs")) {
                    if (handleAjaxPermission(project, user, Type.READ, ret)) {
                        ajaxFetchProjectLogEvents(project, req, ret);
                    }
                } else if (ajaxName.equals("ajaxFetchProjectSchedules")) {
                    if (handleAjaxPermission(project, user, Type.READ, ret)) {
                        ajaxFetchProjectSchedules(project, ret);
                    }
                } else if (ajaxName.equals("fetchRunningFlow")) {
                    if (handleAjaxPermission(project, user, Type.READ, ret)) {
                        ajaxFetchRunningFlow(project, ret, req);
                    }
                } else if (ajaxName.equals("fetchflowjobs")) {
                    if (handleAjaxPermission(project, user, Type.READ, ret)) {
                        ajaxFetchFlow(project, ret, req);
                    }
                } else if (ajaxName.equals("fetchflowdetails")) {
                    if (handleAjaxPermission(project, user, Type.READ, ret)) {
                        ajaxFetchFlowDetails(project, ret, req);
                    }
                } else if (ajaxName.equals("fetchflowgraph")) {
                    if (handleAjaxPermission(project, user, Type.READ, ret)) {
                        ajaxFetchFlowGraph(project, ret, req);
                    }
                } else if (ajaxName.equals("fetchflownodedata")) {
                    if (handleAjaxPermission(project, user, Type.READ, ret)) {
                        ajaxFetchFlowNodeData(project, ret, req);
                    }
                } else if (ajaxName.equals("fetchprojectflows")) {
                    //Project页面获取所有项目信息
                    if (handleAjaxPermission(project, user, Type.READ, ret)) {
                        ajaxFetchProjectFlows(project, ret, req);
                    }
                } else if (ajaxName.equals("changeDescription")) {
                    if (handleAjaxPermission(project, user, Type.WRITE, ret)) {
                        ajaxChangeDescription(project, ret, req, user);
                    }
                } else if (ajaxName.equals("getPermissions")) {
                    if (handleAjaxPermission(project, user, Type.READ, ret)) {
                        ajaxGetPermissions(project, ret);
                    }
                } else if (ajaxName.equals("getGroupPermissions")) {
                    if (handleAjaxPermission(project, user, Type.READ, ret)) {
                        ajaxGetGroupPermissions(project, ret);
                    }
                } else if (ajaxName.equals("getProxyUsers")) {
                    if (handleAjaxPermission(project, user, Type.READ, ret)) {
                        ajaxGetProxyUsers(project, ret);
                    }
                } else if (ajaxName.equals("changePermission")) {
                    if (handleAjaxPermission(project, user, Type.ADMIN, ret)) {
                        ajaxChangePermissions(project, ret, req, user);
                    }
                } else if (ajaxName.equals("addPermission")) {
                    if (handleAjaxPermission(project, user, Type.ADMIN, ret)) {
                        ajaxAddPermission(project, ret, req, user);
                    }
                } else if (ajaxName.equals("addProxyUser")) {
                    if (handleAjaxPermission(project, user, Type.ADMIN, ret)) {
                        ajaxAddProxyUser(project, ret, req, user);
                    }
                } else if (ajaxName.equals("removeProxyUser")) {
                    if (handleAjaxPermission(project, user, Type.ADMIN, ret)) {
                        ajaxRemoveProxyUser(project, ret, req, user);
                    }
                } else if (ajaxName.equals("fetchFlowExecutions")) {
                    if (handleAjaxPermission(project, user, Type.READ, ret)) {
                        ajaxFetchFlowExecutions(project, ret, req, user);
                    }
                } else if (ajaxName.equals("fetchLastSuccessfulFlowExecution")) {
                    if (handleAjaxPermission(project, user, Type.READ, ret)) {
                        ajaxFetchLastSuccessfulFlowExecution(project, ret, req);
                    }
                } else if (ajaxName.equals("fetchJobInfo")) {
                    if (handleAjaxPermission(project, user, Type.READ, ret)) {
                        ajaxFetchJobInfo(project, ret, req);
                    }
                } else if (ajaxName.equals("setJobOverrideProperty")) {
                    if (handleAjaxPermission(project, user, Type.WRITE, ret)) {
                        ajaxSetJobOverrideProperty(project, ret, req, user);
                    }
                } else if (ajaxName.equals("checkForWritePermission")) {
                    ajaxCheckForWritePermission(project, user, ret);
                } else if (ajaxName.equals("fetchJobExecutionsHistory")) {
                    if (handleAjaxPermission(project, user, Type.READ, ret)) {
                        ajaxFetchJobExecutionsHistory(project, ret, req);
                    }
                } else if (ajaxName.equals("ajaxAddProjectUserPermission")) {
                    if (handleAjaxPermission(project, user, Type.ADMIN, ret)) {
                        ajaxAddProjectUserPermission(project, ret, req, user);
                    }
                } else if (ajaxName.equals("ajaxGetUserProjectPerm")) {
                    if (handleAjaxPermission(project, user, Type.ADMIN, ret)) {
                        ajaxGetUserProjectPerm(project, ret, req, user);
                    }
                } else if (ajaxName.equals("ajaxRemoveProjectAdmin")) {
                    if (handleAjaxPermission(project, user, Type.ADMIN, ret)) {
                        ajaxRemoveProjectAdmin(project, ret, req, user);
                    }
                } else if (ajaxName.equals("ajaxAddProjectAdmin")) {
                    if (handleAjaxPermission(project, user, Type.ADMIN, ret)) {
                        ajaxAddProjectAdmin(project, ret, req, user);
                    }
                } else if (ajaxName.equals("fetchFlowRealJobLists")) {
                    if (handleAjaxPermission(project, user, Type.READ, ret)) {
                        ajaxFetchFlowRealJobList(project, ret, req);
                    }
                } else if (ajaxName.equals("fetchJobNestedIdList")) {
                    if (handleAjaxPermission(project, user, Type.READ, ret)) {
                        ajaxFetchJobNestedIdList(project, ret, req);
                    }
                } else if (ajaxName.equals("fetchJobHistoryPage")) {
                    ajaxJobHistoryPage(project, ret, req, user);
                } else if (ajaxName.equals("getJobParamData")) {
                    ajaxGetJobParamData(project, ret, req);
                } else if (ajaxName.equals("fetchRunningScheduleId")) {
                    ajaxFetchRunningScheduleId(project, ret, req);
                } else if (ajaxName.equals("checkUserUploadPermission")) {
                    // 检查用户上传权限
                    ajaxCheckUserUploadPermission(req, resp, ret, session);
                }else if (ajaxName.equals("checkDepUploadPermission")) {
                    // 检查部门上传权限
                    ajaxCheckDepUploadPermission(req, resp, ret, session);
                }else if (ajaxName.equals("checkDeleteProjectFlagPermission")) {
                    // 检查删除项目权限
                    ajaxCheckDeleteProjectFlagPermission(req, resp, ret, session);
                } else if (ajaxName.equals("checkUserScheduleFlowPermission")) {
                    // 检查用户调度流程权限
                    ajaxCheckUserScheduleFlowPermission(req, resp, ret, session);
                } else if (ajaxName.equals("checkUserExecuteFlowPermission")) {
                    // 检查用户执行流程权限
                    ajaxCheckUserExecuteFlowPermission(req, resp, ret, session);
                } else if (ajaxName.equals("checkKillFlowPermission")) {
                    // 检查用户KILL流程权限
                    ajaxCheckKillFlowPermission(req, resp, ret, session);
                } else if (ajaxName.equals("checkUserUpdateScheduleFlowPermission")) {
                    // 检查用户修改调度配置权限
                    ajaxCheckUserUpdateScheduleFlowPermission(req, resp, ret, session);
                } else if (ajaxName.equals("checkUserDeleteScheduleFlowPermission")) {
                    // 检查用户删除调度配置权限
                    ajaxCheckUserDeleteScheduleFlowPermission(req, resp, ret, session);
                } else if (ajaxName.equals("checkUserSetScheduleAlertPermission")) {
                    // 检查用户设置告警配置权限
                    ajaxCheckUserSetScheduleAlertPermission(req, resp, ret, session);
                } else if (ajaxName.equals("checkAddProjectManagePermission")) {
                    // 检查添加项目管理员权限
                    ajaxCheckAddProjectManagePermission(req, resp, ret, session);
                } else if (ajaxName.equals("checkAddProjectUserPermission")) {
                    // 检查添加项目用户权限
                    ajaxCheckAddProjectUserPermission(req, resp, ret, session);
                } else if (ajaxName.equals("checkRemoveProjectManagePermission")) {
                    // 检查移除项目管理员权限
                    ajaxCheckRemoveProjectManagePermission(req, resp, ret, session);
                } else if (ajaxName.equals("checkUpdateProjectUserPermission")) {
                    // 检查更新项目用户权限
                    ajaxCheckUpdateProjectUserPermission(req, resp, ret, session);
                }else if (ajaxName.equals("checkRunningPageKillFlowPermission")) {
                    // 检查用户Kill运行中的工作流权限
                    ajaxCheckRunningPageKillFlowPermission(req, resp, ret, session);
                } else if (ajaxName.equals("checkUserSwitchScheduleFlowPermission")) {
                    // 检查用户开启或关闭定时调度权限
                    ajaxcheckUserSwitchScheduleFlowPermission(req, resp, ret, session);
                } else {
                    ret.put("error", "Cannot execute command " + ajaxName);
                }
            }
        } else {

            final String ajaxName = getParam(req, "ajax");

             if (ajaxName.equals("checkCurrentLanguage")) {
                // 检查当前语言
                ajaxCheckCurrentLanguage(req, resp, ret, session);
            } else if (ajaxName.equals("checkUserCreateProjectPermission")) {
                 // 检查用户创建项目权限
                 ajaxCheckUserCreateProjectPermission(req, resp, ret, session);
            } else if (ajaxName.equals("checkDeleteScheduleInDescriptionFlagPermission")) {
                 // 检查用户删除摘要中定时调度权限
                 ajaxCheckDeleteScheduleInDescriptionFlagPermission(req, resp, ret, session);
            }
        }

        this.writeJSON(resp, ret);
    }

    /**
     * 读取flowpage.vm及其子页面的国际化资源数据
     * @return
     */
    private Map<String, Map<String, String>> loadFlowpageI18nData() {

        Map<String, Map<String, String>> dataMap = new HashMap<>();
        String languageType = LoadJsonUtils.getLanguageType();
        Map<String, String> flowpageMap;
        Map<String, String> subPageMap1;
        Map<String, String> subPageMap2;
        Map<String, String> subPageMap3;
        Map<String, String> subPageMap4;

        if (languageType.equalsIgnoreCase("zh_CN")) {
            // 添加国际化标签
            flowpageMap = LoadJsonUtils.transJson("/com.webank.wedatasphere.schedulis.i18n.conf/azkaban-web-server-zh_CN.json",
                "azkaban.webapp.servlet.velocity.flowpage.vm");
            subPageMap1 = LoadJsonUtils.transJson("/com.webank.wedatasphere.schedulis.i18n.conf/azkaban-web-server-zh_CN.json",
                "azkaban.webapp.servlet.velocity.flow-schedule-ecution-panel.vm");
            subPageMap2 = LoadJsonUtils.transJson("/com.webank.wedatasphere.schedulis.i18n.conf/azkaban-web-server-zh_CN.json",
                "azkaban.webapp.servlet.velocity.messagedialog.vm");
            subPageMap3 = LoadJsonUtils.transJson("/com.webank.wedatasphere.schedulis.i18n.conf/azkaban-web-server-zh_CN.json",
                "azkaban.webapp.servlet.velocity.slapanel.vm");
            subPageMap4 = LoadJsonUtils.transJson("/com.webank.wedatasphere.schedulis.i18n.conf/azkaban-web-server-zh_CN.json",
                "azkaban.webapp.servlet.velocity.nav.vm");
        } else {
            // 添加国际化标签
            flowpageMap = LoadJsonUtils.transJson("/com.webank.wedatasphere.schedulis.i18n.conf/azkaban-web-server-en_US.json",
                "azkaban.webapp.servlet.velocity.flowpage.vm");
            subPageMap1 = LoadJsonUtils.transJson("/com.webank.wedatasphere.schedulis.i18n.conf/azkaban-web-server-en_US.json",
                "azkaban.webapp.servlet.velocity.flow-schedule-ecution-panel.vm");
            subPageMap2 = LoadJsonUtils.transJson("/com.webank.wedatasphere.schedulis.i18n.conf/azkaban-web-server-en_US.json",
                "azkaban.webapp.servlet.velocity.messagedialog.vm");
            subPageMap3 = LoadJsonUtils.transJson("/com.webank.wedatasphere.schedulis.i18n.conf/azkaban-web-server-en_US.json",
                "azkaban.webapp.servlet.velocity.slapanel.vm");
            subPageMap4 = LoadJsonUtils.transJson("/com.webank.wedatasphere.schedulis.i18n.conf/azkaban-web-server-en_US.json",
                "azkaban.webapp.servlet.velocity.nav.vm");
        }

        dataMap.put("flowpage.vm", flowpageMap);
        dataMap.put("flow-schedule-ecution-panel.vm", subPageMap1);
        dataMap.put("messagedialog.vm", subPageMap3);
        dataMap.put("slapanel.vm", subPageMap2);
        dataMap.put("nav.vm", subPageMap4);

        return dataMap;
    }

    /**
     * 加载 ProjectManagerServlet 中的异常信息等国际化资源
     * @return
     */
    private Map<String, String> loadProjectManagerServletI18nData() {
        String languageType = LoadJsonUtils.getLanguageType();
        Map<String, String> dataMap;
        if (languageType.equalsIgnoreCase("zh_CN")) {
            dataMap = LoadJsonUtils.transJson("/com.webank.wedatasphere.schedulis.i18n.conf/azkaban-web-server-zh_CN.json",
                "azkaban.webapp.servlet.ProjectManagerServlet");
        } else {
            dataMap = LoadJsonUtils.transJson("/com.webank.wedatasphere.schedulis.i18n.conf/azkaban-web-server-en_US.json",
                "azkaban.webapp.servlet.ProjectManagerServlet");
        }
        return dataMap;
    }


    /**
     * 检查修改项目用户权限
     * 判断是否具有更新权限 1:允许, 2:不允许
     * @param req
     * @param resp
     * @param resultMap
     * @param session   wtss_project_privilege_check
     */
    private void ajaxCheckUpdateProjectUserPermission(HttpServletRequest req, HttpServletResponse resp,
        HashMap<String, Object> resultMap, Session session) {

        try {
            if (session != null) {
                final User user = session.getUser();
                if (wtss_project_privilege_check) {
                    int updateProUserFlag = checkUserOperatorFlag(user);
                    resultMap.put("updateProUserFlag", updateProUserFlag);
                    logger.info("current user update project user permission flag is updateProUserFlag=" +updateProUserFlag);
                } else {
                    resultMap.put("updateProUserFlag", 1);
                }
            }
        } catch (Exception e) {
            logger.error("Failed to find current user update project user permission flag, caused by:{}", e);
        }
    }


    /**
     * 校验用户操作权限
     * 判断是否具有操作权限 operatorFlag 1:允许, 2:不允许
     * @param user 当前用户, 注意: 该User类的userId映射表wtss_user中的username
     * @return
     */
    private int checkUserOperatorFlag(User user) {

        int operatorFlag = 1;
        if (user != null) {
            String userName = user.getUserId();
            try {
                // 如果不是WTSS开头的用户,查询是否属于管理员
                if (!userName.startsWith("WTSS_")) {
                    WtssUser wtssUser = this.transitionService.getSystemUserByUserName(userName);
                    int roleId = wtssUser.getRoleId();
                    // roleId: 1:管理员  2:普通用户
                    if (roleId == 2) {
                        operatorFlag = 2;
                    }
                }
            } catch (SystemUserManagerException e) {
                logger.error("系统用户信息不存在.", e);
            }
        }
        return operatorFlag;
    }

    /**
     * 检查移除项目用户权限
     * 判断是否具有删除权限 1:允许, 2:不允许
     * @param req
     * @param resp
     * @param resultMap
     * @param session   wtss_project_privilege_check
     */
    private void ajaxCheckRemoveProjectManagePermission(HttpServletRequest req, HttpServletResponse resp,
        HashMap<String, Object> resultMap, Session session) {

        try {
            if (session != null) {
                final User user = session.getUser();
                if (wtss_project_privilege_check) {
                    int removeManageFlag = checkUserOperatorFlag(user);
                    resultMap.put("removeManageFlag", removeManageFlag);
                    logger.info("current user remove project manager permission flag is removeManageFlag=" + removeManageFlag);
                } else {
                    resultMap.put("removeManageFlag", 1);
                }
            }
        } catch (Exception e) {
            logger.error("Failed to find current user remove project manager permission flag, caused by:{}", e);
        }
    }

    /**
     * 检查用户添加项目用户权限
     *
     * @param req
     * @param resp
     * @param resultMap
     * @param session   wtss_project_privilege_check
     */
    private void ajaxCheckAddProjectUserPermission(HttpServletRequest req, HttpServletResponse resp,
        HashMap<String, Object> resultMap, Session session) {

        try {
            if (session != null) {
                final User user = session.getUser();
                if (wtss_project_privilege_check) {
                    int addProjectUserFlag = checkUserOperatorFlag(user);
                    resultMap.put("addProjectUserFlag", addProjectUserFlag);
                    logger.info("current user add project user permission flag is addProjectUserFlag=" + addProjectUserFlag);
                } else {
                    resultMap.put("addProjectUserFlag", 1);
                }
            }
        } catch (Exception e) {
            logger.error("Failed to find current user add project user permission flag, caused by:{}", e);
        }
    }

    /**
     * 检查用户添加项目管理员权限
     *
     * @param req
     * @param resp
     * @param resultMap
     * @param session   wtss_project_privilege_check
     */
    private void ajaxCheckAddProjectManagePermission(HttpServletRequest req, HttpServletResponse resp,
        HashMap<String, Object> resultMap, Session session) {

        try {
            if (session != null) {
                final User user = session.getUser();
                if (wtss_project_privilege_check) {
                    int addManageFlag = checkUserOperatorFlag(user);
                    resultMap.put("addManageFlag", addManageFlag);
                    logger.info("current user add project manager permission flag is addManageFlag=" + addManageFlag);
                } else {
                    resultMap.put("addManageFlag", 1);
                }
            }
        } catch (Exception e) {
            logger.error("Failed to find current user add project manager permission flag, caused by:{}", e);
        }
    }


    /**
     * 检查用户删除调度配置权限
     *
     * @param req
     * @param resp
     * @param resultMap
     * @param session
     */
    private void ajaxCheckCurrentLanguage(HttpServletRequest req, HttpServletResponse resp,
        HashMap<String, Object> resultMap, Session session) {

        try {
            if (session != null) {
                String languageType = getParam(req, "languageType");
                if (StringUtils.isBlank(languageType)) {
                    String lang = req.getHeader("Accept-Language");
                    if (lang.equalsIgnoreCase("zh-CN")) {
                        LoadJsonUtils.setLanguageType("zh_CN");
                    }
                    if (lang.equalsIgnoreCase("en_US")) {
                        LoadJsonUtils.setLanguageType("en_US");
                    }
                } else {
                    if (languageType.equalsIgnoreCase("zh_CN")) {
                        LoadJsonUtils.setLanguageType("zh_CN");
                    }
                    if (languageType.equalsIgnoreCase("en_US")) {
                        LoadJsonUtils.setLanguageType("en_US");
                    }
                }
            }
        } catch (Exception e) {
            logger.error("Exchange language type to another failed, caused by:{}", e);
        }
    }

    /**
     * 检查用户删除调度配置权限
     *
     * @param req
     * @param resp
     * @param resultMap
     * @param session   wtss_project_privilege_check
     */
    private void ajaxCheckUserDeleteScheduleFlowPermission(HttpServletRequest req, HttpServletResponse resp,
        HashMap<String, Object> resultMap, Session session) {

        try {
            if (session != null) {
                final User user = session.getUser();
                if (wtss_project_privilege_check) {
                    int deleteScheduleFlowFlag = checkUserOperatorFlag(user);
                    resultMap.put("deleteScheduleFlowFlag", deleteScheduleFlowFlag);
                    logger.info("current user delete schedule flow permission flag is deleteScheduleFlowFlag=" + deleteScheduleFlowFlag);
                } else {
                    resultMap.put("deleteScheduleFlowFlag", 1);
                }
                Map<String, String> dataMap = loadProjectManagerServletI18nData();
                resultMap.put("removeScheduleTitle", dataMap.get("removeScheduleTitle"));
            }
        } catch (Exception e) {
            logger.error("Failed to find current user delete schedule flow permission flag, caused by:{}", e);
        }
    }

    /**
     * 检查用户设置告警配置权限
     *
     * @param req
     * @param resp
     * @param resultMap
     * @param session   wtss_project_privilege_check
     */
    private void ajaxCheckUserSetScheduleAlertPermission(HttpServletRequest req, HttpServletResponse resp,
        HashMap<String, Object> resultMap, Session session) {

        try {
            if (session != null) {
                final User user = session.getUser();
                if (wtss_project_privilege_check) {
                    int setAlertFlag = checkUserOperatorFlag(user);
                    resultMap.put("setAlertFlag", setAlertFlag);
                    logger.info("current user set schedule alert permission flag is setAlertFlag=" + setAlertFlag);
                } else {
                    resultMap.put("setAlertFlag", 1);
                }
            }
        } catch (Exception e) {
            logger.error("Failed to find current user set schedule alert permission flag, caused by:{}", e);
        }
    }


    /**
     * 检查用户修改调度流程权限
     *
     * @param req
     * @param resp
     * @param resultMap
     * @param session   wtss_project_privilege_check
     */
    private void ajaxCheckUserUpdateScheduleFlowPermission(HttpServletRequest req, HttpServletResponse resp,
        HashMap<String, Object> resultMap, Session session) {

        try {
            if (session != null) {
                final User user = session.getUser();
                if (wtss_project_privilege_check) {
                    int updateScheduleFlowFlag = checkUserOperatorFlag(user);
                    resultMap.put("updateScheduleFlowFlag", updateScheduleFlowFlag);
                    logger.info("current user update schedule flow permission flag is updateScheduleFlowFlag=" + updateScheduleFlowFlag);
                } else {
                    resultMap.put("updateScheduleFlowFlag", 1);
                }
                Map<String, String> dataMap = loadProjectManagerServletI18nData();
                resultMap.put("scheduleFlowTitle", dataMap.get("scheduleFlowTitle"));
            }
        } catch (Exception e) {
            logger.error("Failed to find current user update schedule flow permission flag, caused by:{}", e);
        }
    }

    /**
     * 检查用户执行流程权限
     *
     * @param req
     * @param resp
     * @param resultMap
     * @param session   wtss_project_privilege_check
     */
    private void ajaxCheckUserExecuteFlowPermission(HttpServletRequest req, HttpServletResponse resp,
        HashMap<String, Object> resultMap, Session session) {

        try {
            if (session != null) {
                final User user = session.getUser();
                if (wtss_project_privilege_check) {
                    int executeFlowFlag = checkUserOperatorFlag(user);
                    resultMap.put("executeFlowFlag", executeFlowFlag);
                    logger.info("current user execute flow permission flag is executeFlowFlag=" + executeFlowFlag);
                } else {
                    resultMap.put("executeFlowFlag", 1);
                }
                Map<String, String> dataMap = loadProjectManagerServletI18nData();
                resultMap.put("executeFlowTitle", dataMap.get("executeFlowTitle"));
                resultMap.put("executePermission", dataMap.get("executePermission"));
                resultMap.put("noexecuteFlowPermission", dataMap.get("noexecuteFlowPermission"));

            }
        } catch (Exception e) {
            logger.error("Failed to find current user execute flow permission flag, caused by:{}", e);
        }
    }

    /**
     * 检查用户KILL流程权限
     *
     * @param req
     * @param resp
     * @param resultMap
     * @param session   wtss_project_privilege_check
     */
    private void ajaxCheckKillFlowPermission(HttpServletRequest req, HttpServletResponse resp,
        HashMap<String, Object> resultMap, Session session) {

        try {
            if (session != null) {
                final User user = session.getUser();
                if (wtss_project_privilege_check) {
                    int killFlowFlag = checkUserOperatorFlag(user);
                    resultMap.put("killFlowFlag", killFlowFlag);
                    logger.info("current user kill flow permission flag is killFlowFlag=" + killFlowFlag);
                } else {
                    resultMap.put("killFlowFlag", 1);
                }
                Map<String, String> dataMap = loadProjectManagerServletI18nData();
                resultMap.put("endExecutenProcess", dataMap.get("endExecutenProcess"));
                resultMap.put("killExecutePermissions", dataMap.get("killExecutePermissions"));
                resultMap.put("killExecutePermissionsDesc", dataMap.get("killExecutePermissionsDesc"));

            }
        } catch (Exception e) {
            logger.error("Failed to find current user kill flow permission flag, caused by:{}.", e);
        }
    }

    /**
     * 检查用户调度流程权限
     *
     * @param req
     * @param resp
     * @param resultMap
     * @param session   wtss_project_privilege_check
     */
    private void ajaxCheckUserScheduleFlowPermission(HttpServletRequest req, HttpServletResponse resp,
        HashMap<String, Object> resultMap, Session session) {

        try {
            if (session != null) {
                final User user = session.getUser();
                if (wtss_project_privilege_check) {
                    int scheduleFlowFlag = checkUserOperatorFlag(user);
                    resultMap.put("scheduleFlowFlag", scheduleFlowFlag);
                    logger.info("current user schedule flow permission flag is scheduleFlowFlag=" + scheduleFlowFlag);
                } else {
                    resultMap.put("scheduleFlowFlag", 1);
                }
                Map<String, String> dataMap = loadProjectManagerServletI18nData();
                resultMap.put("scheduleFlowTitle", dataMap.get("scheduleFlowTitle"));
                resultMap.put("schFlowPermission", dataMap.get("schFlowPermission"));
                resultMap.put("noSchPermissionsFlow", dataMap.get("noSchPermissionsFlow"));
            }
        } catch (Exception e) {
            logger.error("Failed to find current user schedule flow permission flag, caused by:{}", e);
        }
    }

    /**
     * 检查用户删除摘要中定时调度权限
     *
     * @param req
     * @param resp
     * @param resultMap
     * @param session   wtss_project_privilege_check
     */
    private void ajaxCheckDeleteScheduleInDescriptionFlagPermission(HttpServletRequest req, HttpServletResponse resp,
        HashMap<String, Object> resultMap, Session session) {

        try {
            if (session != null) {
                final User user = session.getUser();
                if (wtss_project_privilege_check) {
                    int deleteDescScheduleFlag = checkUserOperatorFlag(user);
                    resultMap.put("deleteDescScheduleFlag", deleteDescScheduleFlag);
                    logger.info("current user create project permission flag is deleteDescScheduleFlag=" + deleteDescScheduleFlag);
                } else {
                    resultMap.put("deleteDescScheduleFlag", 1);
                }
            }
        } catch (Exception e) {
            logger.error("Failed to find current user delete schedule in description tag permission flag, caused by:{}", e);
        }
    }


    /**
     * 检查用户开启或关闭定时调度权限
     *
     * @param req
     * @param resp
     * @param resultMap
     * @param session   wtss_project_privilege_check
     */
    private void ajaxcheckUserSwitchScheduleFlowPermission(HttpServletRequest req, HttpServletResponse resp,
        HashMap<String, Object> resultMap, Session session) {

        try {
            if (session != null) {
                final String projectName = getParam(req, "project");
                final User user = session.getUser();
                final Project project = getProjectAjaxByPermission(resultMap, projectName, user, Type.SCHEDULE);
                Map<String, String> stringStringMap = loadProjectManagerServletI18nData();
                if (project == null) {
                    resultMap.put("error", stringStringMap.get("permissionForAction") + projectName);
                    resultMap.put("switchScheduleFlowFlag", 3);
                    return;
                }
                if (wtss_project_privilege_check) {
                    int switchScheduleFlowFlag = checkUserOperatorFlag(user);
                    resultMap.put("switchScheduleFlowFlag", switchScheduleFlowFlag);
                    logger.info("current user active schedule flow permission flag is switchScheduleFlowFlag=" + switchScheduleFlowFlag);
                } else {
                    resultMap.put("switchScheduleFlowFlag", 1);
                }
            }
        } catch (Exception e) {
            logger.error("Failed to find current user active schedule flow flow permission flag, caused by:{}", e);
        }
    }

    protected Project getProjectAjaxByPermission(final Map<String, Object> ret, final String projectName,
                                                 final User user, final Type type) {
        final Project project = this.projectManager.getProject(projectName);

        Map<String, String> dataMap = loadProjectManagerServletI18nData();

        if (project == null) {
            ret.put("error", dataMap.get("project") + projectName + dataMap.get("notExist"));
        } else if (!hasPermission(project, user, type)) {
            ret.put("error", "User " + user.getUserId() + " doesn't have " + project.getName() + " of " + type.name()
                    + " permissions, please contact with the project creator.");
        } else {
            return project;
        }

        return null;
    }

    /**
     * 检查用户KILL正在运行页面flow权限
     *
     * @param req
     * @param resp
     * @param resultMap
     * @param session   wtss_project_privilege_check
     */
    private void ajaxCheckRunningPageKillFlowPermission(HttpServletRequest req, HttpServletResponse resp,
        HashMap<String, Object> resultMap, Session session) {

        try {
            if (session != null) {
                final User user = session.getUser();
                if (wtss_project_privilege_check) {
                    int runningPageKillFlowFlag = checkUserOperatorFlag(user);
                    resultMap.put("runningPageKillFlowFlag", runningPageKillFlowFlag);
                    logger.info("current user kill page flow permission flag is runningPageKillFlowFlag=" + runningPageKillFlowFlag);
                } else {
                    resultMap.put("runningPageKillFlowFlag", 1);
                }
            }
        } catch (Exception e) {
            logger.error("Failed to find current user kill page flow permission flag, caused by:{}", e);
        }
    }

    /**
     * 检查用户创建项目权限
     *
     * @param req
     * @param resp
     * @param resultMap
     * @param session   wtss_project_privilege_check
     */
    private void ajaxCheckUserCreateProjectPermission(HttpServletRequest req, HttpServletResponse resp,
        HashMap<String, Object> resultMap, Session session) {

        try {
            if (session != null) {
                final User user = session.getUser();
                if (wtss_project_privilege_check) {
                    int createProjectFlag = checkUserOperatorFlag(user);
                    resultMap.put("createProjectFlag", createProjectFlag);
                    logger.info("current user create project permission flag is createProjectFlag=" + createProjectFlag);
                } else {
                    resultMap.put("createProjectFlag", 1);
                }
            }
        } catch (Exception e) {
            logger.error("Failed to find current user create project permission flag, caused by:{}", e);
        }
    }

    /**
     * 检查用户上传权限
     * 判断是否具有上传权限 1:允许, 2:不允许
     * @param req
     * @param resp
     * @param resultMap
     * @param session
     */
    private void ajaxCheckUserUploadPermission(HttpServletRequest req, HttpServletResponse resp,
        HashMap<String, Object> resultMap, Session session) {

        try {
            if (session != null) {
                final User user = session.getUser();
                int userUploadFlag = checkUserUploadFlag(user);
                logger.info("current user upload permission flag is userUploadFlag=" + userUploadFlag);
                resultMap.put("userUploadFlag", userUploadFlag);
            }
        } catch (Exception e) {
            logger.error("Failed to find user upload permission flag, caused by:{}", e);
            resultMap.put("error", e);
        }
    }

    /**
     * 校验部门上传权限
     * 判断是否具有上传权限 userUploadFlag 1:允许, 2:不允许
     * @param user 当前用户, 注意: 该User类的userId映射表wtss_user中的username
     * @return
     */
    private int checkUserUploadFlag(User user) throws SystemUserManagerException {

        int userUploadFlag = 1;
        try {
            String userName = user.getUserId();
            WtssUser systemUser = this.transitionService.getSystemUserByUserName(userName);
            boolean bool = systemUser.getUsername().startsWith("WTSS");
            if (systemUser.getRoleId() != 1) {
                if (!bool) {
                    // 如果不是管理员用户.判断开关是否打开
                    if (wtss_project_privilege_check) {
                        userUploadFlag = 2;
                    }
                }
            }

        } catch (SystemUserManagerException e) {
            logger.error("查询用户信息失败,失败原因:{}", e);
        }
        return userUploadFlag;
    }


    /**
     * 检查删除项目权限
     *
     * @param req
     * @param resp
     * @param resultMap
     * @param session   wtss_project_privilege_check
     */
    private void ajaxCheckDeleteProjectFlagPermission(HttpServletRequest req, HttpServletResponse resp,
        HashMap<String, Object> resultMap, Session session) {

        try {
            if (session != null) {
                final User user = session.getUser();
                if (wtss_project_privilege_check) {
                    int deleteProjectFlag = checkUserOperatorFlag(user);
                    resultMap.put("deleteProjectFlag", deleteProjectFlag);
                    logger.info("current user delete project permission flag is deleteProjectFlag=" + deleteProjectFlag);
                } else {
                    resultMap.put("deleteProjectFlag", 1);
                }
            }
        } catch (Exception e) {
            logger.error("Failed to find user delete project permission flag, caused by:{}", e);
        }
    }


    /**
     * 检查部门上传权限
     * 判断是否具有上传权限  uploadFlag 1:允许, 2:不允许,其他值:不允许
     * @param req
     * @param resp
     * @param resultMap
     * @param session
     */
    private void ajaxCheckDepUploadPermission(HttpServletRequest req, HttpServletResponse resp,
        HashMap<String, Object> resultMap, Session session) {

        try {
            if (session != null) {
                // 用户部门上传的开关,默认关闭,所有部门都能上传
                if (wtss_dep_upload_privilege_check) {
                    final User user = session.getUser();
                    int uploadFlag = checkDepartmentUploadFlag(user);
                    resultMap.put("uploadFlag", uploadFlag);
                } else {
                    resultMap.put("uploadFlag", "1");
                }
            }
        } catch (Exception e) {
            logger.error("Failed to find current department upload permission flag, caused by:{}", e);
            resultMap.put("error", e);
        }
    }

    private boolean handleAjaxPermission(final Project project, final User user, final Type type,
        final Map<String, Object> ret) {
        if (hasPermission(project, user, type)) {
            return true;
        }

        ret.put("error", "Permission denied. Need " + type.toString() + " access.");
        return false;
    }

    private void ajaxFetchProjectVersions(final Project project,final HttpServletRequest req,final HashMap<String, Object> ret) throws ServletException {
        final int num = this.getIntParam(req, "size", 10);
        final int skip = this.getIntParam(req, "skip", 0);
        List<ProjectVersion> versionList = null;

        try {
            versionList = projectManager.getProjectVersions(project, num, skip);
        } catch (ProjectManagerException e) {
            throw new ServletException(e);
        }

        final String[] columns = new String[]{"projectId", "version", "uploadTime"};
        ret.put("columns", columns);

        final List<Object[]> resultList = new ArrayList<>();
        for (final ProjectVersion data : versionList) {
            final Object[] entry = new Object[3];
            entry[0] = data.getProjectId();
            entry[1] = data.getVersion();
            entry[2] = data.getUploadTime();

            resultList.add(entry);
        }

        ret.put("versionData", resultList);

    }

    private void ajaxFetchProjectLogEvents(final Project project,
        final HttpServletRequest req, final HashMap<String, Object> ret) throws ServletException {
        final int num = this.getIntParam(req, "size", 1000);
        final int skip = this.getIntParam(req, "skip", 0);

        final List<ProjectLogEvent> logEvents;
        try {
            logEvents = this.projectManager.getProjectEventLogs(project, num, skip);
        } catch (final ProjectManagerException e) {
            throw new ServletException(e);
        }

        final String[] columns = new String[]{"user", "time", "type", "message"};
        ret.put("columns", columns);

        final List<Object[]> eventData = new ArrayList<>();
        for (final ProjectLogEvent events : logEvents) {
            final Object[] entry = new Object[4];
            entry[0] = events.getUser();
            entry[1] = events.getTime();
            entry[2] = events.getType();
            entry[3] = events.getMessage();

            eventData.add(entry);
        }

        ret.put("logData", eventData);
    }

    private List<String> getFlowJobTypes(final Flow flow) {
        final Set<String> jobTypeSet = new HashSet<>();
        for (final Node node : flow.getNodes()) {
            jobTypeSet.add(node.getType());
        }
        final List<String> jobTypes = new ArrayList<>();
        jobTypes.addAll(jobTypeSet);
        return jobTypes;
    }

    private void ajaxFetchFlowDetails(final Project project,
        final HashMap<String, Object> ret, final HttpServletRequest req)
        throws ServletException {
        final String flowName = getParam(req, "flow");

        Flow flow = null;
        try {
            flow = project.getFlow(flowName);
            if (flow == null) {
                ret.put("error", "Flow[ " + flowName + " ].");
                return;
            }

            ret.put("jobTypes", getFlowJobTypes(flow));
            if (flow.getCondition() != null) {
                ret.put("condition", flow.getCondition());
            }
        } catch (final AccessControlException e) {
            ret.put("error", e.getMessage());
        }
    }

    /**
     * 通过工程名获取其正在运行的flow
     *
     * @param project
     * @param ret
     * @param req
     * @throws ServletException
     */
    private void ajaxFetchRunningFlow(final Project project,
        final HashMap<String, Object> ret, final HttpServletRequest req)
        throws ServletException {
        final String flowName = getParam(req, "flow");

        Flow flow = null;
        try {
            flow = project.getFlow(flowName);
            if (flow == null) {
                ret.put("error", "Flow[ " + flowName + " ].");
                return;
            }

            ret.put("jobTypes", getFlowJobTypes(flow));
        } catch (final AccessControlException e) {
            ret.put("error", e.getMessage());
        }
    }

    private void ajaxFetchLastSuccessfulFlowExecution(final Project project,
        final HashMap<String, Object> ret, final HttpServletRequest req)
        throws ServletException {
        final String flowId = getParam(req, "flow");
        List<ExecutableFlow> exFlows = null;
        try {
            exFlows =
                this.executorManagerAdapter.getExecutableFlows(project.getId(), flowId, 0, 1,
                    Status.SUCCEEDED);
        } catch (final ExecutorManagerException e) {
            ret.put("error", "Error retrieving executable flows");
            return;
        }

        if (exFlows.size() == 0) {
            ret.put("success", "false");
            ret.put("message", "This flow has no successful run.");
            return;
        }

        ret.put("success", "true");
        ret.put("message", "");
        ret.put("execId", exFlows.get(0).getExecutionId());
    }

    private void ajaxFetchFlowExecutions(final Project project,
        final HashMap<String, Object> ret, final HttpServletRequest req)
        throws ServletException {
        final String flowId = getParam(req, "flow");
        final int from = Integer.valueOf(getParam(req, "start"));
        final int length = Integer.valueOf(getParam(req, "length"));

        final ArrayList<ExecutableFlow> exFlows = new ArrayList<>();
        int total = 0;
        try {
            total =
                this.executorManagerAdapter.getExecutableFlows(project.getId(), flowId, from,
                    length, exFlows);
        } catch (final ExecutorManagerException e) {
            ret.put("error", "Error retrieving executable flows");
        }

        ret.put("flow", flowId);
        ret.put("total", total);
        ret.put("from", from);
        ret.put("length", length);

        final ArrayList<Object> history = new ArrayList<>();
        for (final ExecutableFlow flow : exFlows) {
            final HashMap<String, Object> flowInfo = new HashMap<>();
            flowInfo.put("execId", flow.getExecutionId());
            flowInfo.put("flowId", flow.getFlowId());
            flowInfo.put("projectId", flow.getProjectId());
            flowInfo.put("status", flow.getStatus().toString());
            flowInfo.put("submitTime", flow.getSubmitTime());
            flowInfo.put("startTime", flow.getStartTime());
            flowInfo.put("endTime", flow.getEndTime());
            flowInfo.put("submitUser", flow.getSubmitUser());
			      // FIXME Add the run_date variable.
            Map<String, String> repeatMap = flow.getRepeatOption();
            if (!repeatMap.isEmpty()) {
                Long recoverRunDate = Long.valueOf(String.valueOf(repeatMap.get("startTimeLong")));
                LocalDateTime localDateTime = new LocalDateTime(new Date(recoverRunDate)).minusDays(1);
                flowInfo.put("runDate", localDateTime.toString("yyyyMMdd"));
            } else {
                Long runDate = flow.getStartTime();
                LocalDateTime localDateTime = new LocalDateTime(new Date(runDate)).minusDays(1);
                flowInfo.put("runDate", localDateTime.toString("yyyyMMdd"));
            }
            history.add(flowInfo);
        }

        ret.put("executions", history);
    }

    /**
     * Download project zip file from DB and send it back client.
     * <p>
     * This method requires a project name and an optional project version.
     */
    private void handleDownloadProject(final HttpServletRequest req,
        final HttpServletResponse resp, final Session session) throws ServletException,
        IOException {

        final User user = session.getUser();
        final String projectName = getParam(req, "project");
        logger.info(user.getUserId() + " is downloading project: " + projectName);

        final Project project = this.projectManager.getProject(projectName);
        if (project == null) {
            this.setErrorMessageInCookie(resp, "Project " + projectName
                + " doesn't exist.");
            resp.sendRedirect(req.getContextPath());
            return;
        }

        if (!hasPermission(project, user, Type.READ)) {
            this.setErrorMessageInCookie(resp, "No permission to download project " + projectName
                + ".");
            resp.sendRedirect(req.getContextPath());
            return;
        }

        int version = -1;
        if (hasParam(req, "version")) {
            version = getIntParam(req, "version");
        }

        ProjectFileHandler projectFileHandler = null;
        FileInputStream inStream = null;
        OutputStream outStream = null;
        try {
            projectFileHandler =
                this.projectManager.getProjectFileHandler(project, version);
            if (projectFileHandler == null) {
                this.setErrorMessageInCookie(resp, "Project " + projectName
                    + " with version " + version + " doesn't exist");
                resp.sendRedirect(req.getContextPath());
                return;
            }
            final File projectZipFile = projectFileHandler.getLocalFile();
            final String logStr =
                String.format(
                    "downloading project zip file for project \"%s\" at \"%s\""
                        + " size: %d type: %s  fileName: \"%s\"",
                    projectFileHandler.getFileName(),
                    projectZipFile.getAbsolutePath(), projectZipFile.length(),
                    projectFileHandler.getFileType(),
                    projectFileHandler.getFileName());
            logger.info(logStr);

            // now set up HTTP response for downloading file
            inStream = new FileInputStream(projectZipFile);

            resp.setContentType(APPLICATION_ZIP_MIME_TYPE);

            final String headerKey = "Content-Disposition";
            final String headerValue =
                String.format("attachment; filename=\"%s\"",
                    projectFileHandler.getFileName());
            resp.setHeader(headerKey, headerValue);
            resp.setHeader("version",
                Integer.toString(projectFileHandler.getVersion()));
            resp.setHeader("projectId",
                Integer.toString(projectFileHandler.getProjectId()));

            outStream = resp.getOutputStream();

            final byte[] buffer = new byte[this.downloadBufferSize];
            int bytesRead = -1;

            while ((bytesRead = inStream.read(buffer)) != -1) {
                outStream.write(buffer, 0, bytesRead);
            }

        } catch (final Throwable e) {
            logger.error(
                "Encountered error while downloading project zip file for project: "
                    + projectName + " by user: " + user.getUserId(), e);
            throw new ServletException(e);
        } finally {
            IOUtils.closeQuietly(inStream);
            IOUtils.closeQuietly(outStream);

            if (projectFileHandler != null) {
                projectFileHandler.deleteLocalFile();
            }
        }

    }

    /**
     * validate readiness of a project and user permission and use projectManager to purge the project
     * if things looks good
     **/
    private void handlePurgeProject(final HttpServletRequest req,
        final HttpServletResponse resp, final Session session) throws ServletException,
        IOException {
        final User user = session.getUser();
        final HashMap<String, Object> ret = new HashMap<>();
        boolean isOperationSuccessful = true;

        try {
            Project project = null;
            final String projectParam = getParam(req, "project");

            if (StringUtils.isNumeric(projectParam)) {
                project = this.projectManager.getProject(Integer.parseInt(projectParam)); // get
                // project
                // by
                // Id
            } else {
                project = this.projectManager.getProject(projectParam); // get project by
                // name (name cannot
                // start
                // from ints)
            }

            // invalid project
            if (project == null) {
                ret.put("error", "invalid project");
                isOperationSuccessful = false;
            }

            // project is already deleted
            if (isOperationSuccessful
                && this.projectManager.isActiveProject(project.getId())) {
                ret.put("error", "Project " + project.getName()
                    + " should be deleted before purging");
                isOperationSuccessful = false;
            }

            // only eligible users can purge a project
            if (isOperationSuccessful && !hasPermission(project, user, Type.ADMIN)) {
                ret.put("error", "Cannot purge. User '" + user.getUserId()
                    + "' is not an ADMIN.");
                isOperationSuccessful = false;
            }

            if (isOperationSuccessful) {
                this.projectManager.purgeProject(project, user);
            }
        } catch (final Exception e) {
            ret.put("error", e.getMessage());
            isOperationSuccessful = false;
        }

        ret.put("success", isOperationSuccessful);
        this.writeJSON(resp, ret);
    }

    private void removeAssociatedSchedules(final Project project) throws ServletException {
        // remove regular schedules
        try {
            for (final Schedule schedule : this.scheduleManager.getSchedules()) {
                if (schedule.getProjectId() == project.getId()) {
                    logger.info("removing schedule " + schedule.getScheduleId());
                    this.scheduleManager.removeSchedule(schedule);
                }
            }
        } catch (final ScheduleManagerException e) {
            throw new ServletException(e);
        }

        // remove flow trigger schedules
        try {
            if (this.enableQuartz) {
                this.scheduler.unscheduleAll(project);
            }
        } catch (final SchedulerException e) {
            throw new ServletException(e);
        }
    }

    private void ajaxFetchProjectSchedules(final Project project, final HashMap<String, Object> ret) throws ServletException,
        IOException {
        try {
            for (final Schedule schedule : this.scheduleManager.getSchedules()) {
                if (schedule.getProjectId() == project.getId()) {
                    ret.put("hasSchedule", true);
                    break;
                }
            }
        } catch (Exception e) {
            logger.error("getSchedules failed", e);
        }
    }

    /**
     * 删除工程前先判断是否有flow在运行，是否设置了定时调度
     *
     * @param req
     * @param resp
     * @param session
     * @throws ServletException
     * @throws IOException
     */
    private void handleRemoveProject(final HttpServletRequest req,
        final HttpServletResponse resp, final Session session) throws ServletException,
        IOException {
        final User user = session.getUser();
        final String projectName = getParam(req, "project");

        final Project project = this.projectManager.getProject(projectName);
        if (project == null) {
            this.setErrorMessageInCookie(resp, "Project " + projectName + " doesn't exist.");
            logger.info("Project is null, Redirect to ---> " + req.getContextPath());
            resp.sendRedirect(req.getContextPath());
            return;
        }

        if (!hasPermission(project, user, Type.ADMIN)) {
            this.setErrorMessageInCookie(resp,"Cannot delete. User '" + user.getUserId() + "' is not an ADMIN.");
            logger.info("Have no permission, Redirect to ---> " + req.getRequestURI() + "?project=" + projectName);
            resp.sendRedirect(req.getRequestURI() + "?project=" + projectName);
            return;
        }

        // FIXME Added the judgment that if the job stream is running, the project cannot be deleted.
        List<Flow> runningFlows = this.projectManager.getRunningFlow(project);
        if (runningFlows != null && runningFlows.size() != 0) {
            this.setErrorMessageInCookie(resp,"工作流: " + runningFlows.stream()
                .map(Flow::getId).collect(Collectors.toList()).toString() + " 没有结束, 不能删除该工程.");
            logger.info("Flow is not finished, Redirect to ---> " + req.getRequestURI() + "?project=" + projectName);
            resp.sendRedirect(req.getRequestURI() + "?project=" + projectName);
            return;
        }

        removeAssociatedSchedules(project);

        try {
            this.projectManager.removeProject(project, user);
        } catch (final ProjectManagerException e) {
            this.setErrorMessageInCookie(resp, e.getMessage());
            logger.info("Remove project error, Redirect to ---> " + req.getRequestURI() + "?project=" + projectName);
            resp.sendRedirect(req.getRequestURI() + "?project=" + projectName);
            return;
        }

        this.setSuccessMessageInCookie(resp, "Delete Project[" + projectName + "] Success.");
        //删除成功后控制前端页面跳转位置
        resp.sendRedirect("/index");
    }

    private void ajaxChangeDescription(final Project project,
        final HashMap<String, Object> ret, final HttpServletRequest req, final User user)
        throws ServletException {
        final String description = getParam(req, "description");

        //FIXME HTML escapes to prevent XSS attacks.
        String saftyStr = StringEscapeUtils.escapeHtml(description);

        project.setDescription(saftyStr);

        try {
            this.projectManager.updateProjectDescription(project, description, user);
        } catch (final ProjectManagerException e) {
            ret.put("error", e.getMessage());
        }
    }

    private void ajaxFetchJobInfo(final Project project, final HashMap<String, Object> ret,
        final HttpServletRequest req) throws ServletException {
        final String flowName = getParam(req, "flowName");
        final String jobName = getParam(req, "jobName");

        Map<String, String> dataMap = loadProjectManagerServletI18nData();

        final Flow flow = project.getFlow(flowName);
        if (flow == null) {
            ret.put("error", dataMap.get("project") + project.getName() + dataMap.get("notExistFlow") + flowName);
            return;
        }

        final Node node = flow.getNode(jobName);
        if (node == null) {
            ret.put("error", dataMap.get("flow") + flowName + dataMap.get("notExistJob") + jobName);
            return;
        }

        Props jobProp;
        try {
            jobProp = this.projectManager.getProperties(project, flow, jobName, node.getJobSource());
        } catch (final ProjectManagerException e) {
            ret.put("error", "Failed to retrieve job properties!");
            return;
        }

        if (jobProp == null) {
            jobProp = new Props();
        }

        Props overrideProp;
        try {
            overrideProp = this.projectManager
                .getJobOverrideProperty(project, flow, jobName, node.getJobSource());
        } catch (final ProjectManagerException e) {
            ret.put("error", "Failed to retrieve job override properties!");
            return;
        }

        ret.put("jobName", node.getId());
        ret.put("jobType", jobProp.get("type"));

        if (overrideProp == null) {
            overrideProp = new Props(jobProp);
        }

        final Map<String, String> generalParams = new HashMap<>();
        final Map<String, String> overrideParams = new HashMap<>();
        for (final String ps : jobProp.getKeySet()) {
            generalParams.put(ps, jobProp.getString(ps));
        }
        for (final String ops : overrideProp.getKeySet()) {
            overrideParams.put(ops, overrideProp.getString(ops));
        }
        ret.put("generalParams", generalParams);
        ret.put("overrideParams", overrideParams);
    }

    private void ajaxSetJobOverrideProperty(final Project project,
        final HashMap<String, Object> ret, final HttpServletRequest req, final User user)
        throws ServletException {
        final String flowName = getParam(req, "flowName");
        final String jobName = getParam(req, "jobName");

        final Flow flow = project.getFlow(flowName);
        Map<String, String> dataMap = loadProjectManagerServletI18nData();
        if (flow == null) {
            ret.put("error",
                dataMap.get("project") + project.getName() + dataMap.get("notExistFlow") + flowName);
            return;
        }

        final Node node = flow.getNode(jobName);
        if (node == null) {
            ret.put("error", dataMap.get("flow") + flowName + dataMap.get("notExistJob") + jobName);
            return;
        }

        final Map<String, String> jobParamGroup = this.getParamGroup(req, "jobOverride");
        final Props overrideParams = new Props(null, jobParamGroup);
        try {
            this.projectManager
                .setJobOverrideProperty(project, flow, overrideParams, jobName, node.getJobSource(),
                    user);
        } catch (final ProjectManagerException e) {
            ret.put("error", dataMap.get("uploadJobCoverFieldError"));
        }

    }

    private void ajaxFetchProjectFlows(final Project project, final HashMap<String, Object> ret,
        final HttpServletRequest req) throws ServletException {

        final ArrayList<Map<String, Object>> flowList = new ArrayList<>();
        Map<String, String> dataMap = loadProjectManagerServletI18nData();

        for (final Flow flow : project.getFlows()) {
            if (!flow.isEmbeddedFlow()) {
                final HashMap<String, Object> flowObj = new HashMap<>();
                flowObj.put("flowId", flow.getId());
				        // FIXME Get the last execution information of the project Flow.
                ExecutableFlow exFlow = null;
                try {//获取项目Flow最后一次执行信息
                    exFlow = this.executorManagerAdapter.getProjectLastExecutableFlow(project.getId(), flow.getId());
                    if (null != exFlow) {
                        flowObj.put("flowStatus", exFlow.getStatus().getNumVal());
                        flowObj.put("flowExecId", exFlow.getExecutionId());
                    } else {
                        flowObj.put("flowStatus", "NoHistory");
                        flowObj.put("flowExecId", "NoHistory");
                    }
                } catch (final ExecutorManagerException e) {
                    ret.put("error", dataMap.get("getLastRunFailed"));
                    return;
                }
                flowList.add(flowObj);
            }
        }

        ret.put("flows", flowList);
    }

    private void ajaxFetchFlowGraph(final Project project, final HashMap<String, Object> ret,
        final HttpServletRequest req) throws ServletException {
        final String flowId = getParam(req, "flow");

        fillFlowInfo(project, flowId, ret);
    }

    private void fillFlowInfo(final Project project, final String flowId, final HashMap<String, Object> ret) {
        final Flow flow = project.getFlow(flowId);
        if (flow == null) {
            ret.put("error", "Flow " + flowId + " not found in project " + project.getName());
            return;
        }

        final ArrayList<Map<String, Object>> nodeList = new ArrayList<>();
        for (final Node node : flow.getNodes()) {
            final HashMap<String, Object> nodeObj = new HashMap<>();
            nodeObj.put("id", node.getId());
            nodeObj.put("type", node.getType());
            if (node.getCondition() != null) {
                nodeObj.put("condition", node.getCondition());
            }
            if (node.getEmbeddedFlowId() != null) {
                nodeObj.put("flowId", node.getEmbeddedFlowId());
                fillFlowInfo(project, node.getEmbeddedFlowId(), nodeObj);
            }

            nodeList.add(nodeObj);
            final Set<Edge> inEdges = flow.getInEdges(node.getId());
            if (inEdges != null && !inEdges.isEmpty()) {
                final ArrayList<String> inEdgesList = new ArrayList<>();
                for (final Edge edge : inEdges) {
                    inEdgesList.add(edge.getSourceId());
                }
                Collections.sort(inEdgesList);
                nodeObj.put("in", inEdgesList);
            }
        }

        Collections.sort(nodeList, (o1, o2) -> {
            final String id = (String) o1.get("id");
            return id.compareTo((String) o2.get("id"));
        });

        ret.put("flow", flowId);
        ret.put("nodes", nodeList);
    }

    private void ajaxFetchFlowNodeData(final Project project, final HashMap<String, Object> ret,
        final HttpServletRequest req) throws ServletException {

        final String flowId = getParam(req, "flow");
        final Flow flow = project.getFlow(flowId);

        final String nodeId = getParam(req, "node");
        final Node node = flow.getNode(nodeId);

        if (node == null) {
            ret.put("error", "Job " + nodeId + " doesn't exist.");
            return;
        }

        ret.put("id", nodeId);
        ret.put("flow", flowId);
        ret.put("type", node.getType());

        final Props jobProps;
        try {
            jobProps = this.projectManager.getProperties(project, flow, nodeId, node.getJobSource());
        } catch (final ProjectManagerException e) {
            ret.put("error", "Failed to upload job override property for " + nodeId);
            return;
        }

        if (jobProps == null) {
            ret.put("error", "Properties for " + nodeId + " isn't found.");
            return;
        }

        final Map<String, String> properties = PropsUtils.toStringMap(jobProps, true);
        ret.put("props", properties);

        if (node.getType().equals("flow")) {
            if (node.getEmbeddedFlowId() != null) {
                fillFlowInfo(project, node.getEmbeddedFlowId(), ret);
            }
        }
    }

    private void ajaxFetchFlow(final Project project, final HashMap<String, Object> ret,
        final HttpServletRequest req) throws ServletException {
        final String flowId = getParam(req, "flow");
        getProjectNodeTree(project, flowId, ret);
    }

    private void ajaxAddProxyUser(final Project project, final HashMap<String, Object> ret,
        final HttpServletRequest req, final User user) throws ServletException {
        final String name = getParam(req, "name");

        logger.info("Adding proxy user " + name + " by " + user.getUserId());
        if (this.transitionService.validateProxyUser(name, user)) {
            try {
                this.projectManager.addProjectProxyUser(project, name, user);
            } catch (final ProjectManagerException e) {
                ret.put("error", e.getMessage());
            }
        } else {
            ret.put("error", "User " + user.getUserId()
                + " has no permission to add " + name + " as proxy user.");
            return;
        }
    }

    private void ajaxRemoveProxyUser(final Project project,
        final HashMap<String, Object> ret, final HttpServletRequest req, final User user)
        throws ServletException {
        final String name = getParam(req, "name");

        logger.info("Removing proxy user " + name + " by " + user.getUserId());

        try {
            this.projectManager.removeProjectProxyUser(project, name, user);
        } catch (final ProjectManagerException e) {
            ret.put("error", e.getMessage());
        }
    }

    private void ajaxAddPermission(final Project project, final HashMap<String, Object> ret,
        final HttpServletRequest req, final User user) throws ServletException {
        final String name = getParam(req, "name");
        final boolean group = Boolean.parseBoolean(getParam(req, "group"));

        if (group) {
            if (project.getGroupPermission(name) != null) {
                ret.put("error", "Group permission already exists.");
                return;
            }
            if (!this.transitionService.validateGroup(name)) {
                ret.put("error", "Group is invalid.");
                return;
            }
        } else {
            if (project.getUserPermission(name) != null) {
                ret.put("error", "User permission already exists.");
                return;
            }
            if (!this.transitionService.validateUser(name)) {
                ret.put("error", "User is invalid.");
                return;
            }
        }

        final boolean admin = Boolean.parseBoolean(getParam(req, "permissions[admin]"));
        final boolean read = Boolean.parseBoolean(getParam(req, "permissions[read]"));
        final boolean write = Boolean.parseBoolean(getParam(req, "permissions[write]"));
        final boolean execute =
            Boolean.parseBoolean(getParam(req, "permissions[execute]"));
        final boolean schedule =
            Boolean.parseBoolean(getParam(req, "permissions[schedule]"));

        final Permission perm = new Permission();
        if (admin) {
            perm.setPermission(Type.ADMIN, true);
			// FIXME admin can READ WRITE EXECUTE SCHEDULE permission.
            perm.setPermission(Type.READ, true);
            perm.setPermission(Type.WRITE, true);
            perm.setPermission(Type.EXECUTE, true);
            perm.setPermission(Type.SCHEDULE, true);
        } else {
            perm.setPermission(Type.READ, read);
            perm.setPermission(Type.WRITE, write);
            perm.setPermission(Type.EXECUTE, execute);
            perm.setPermission(Type.SCHEDULE, schedule);
        }

        try {
            this.projectManager.updateProjectPermission(project, name, perm, group, user);
        } catch (final ProjectManagerException e) {
            ret.put("error", e.getMessage());
        }
    }

    private void ajaxChangePermissions(final Project project, final HashMap<String, Object> ret,
        final HttpServletRequest req, final User user)
        throws ServletException {

        Map<String, String> dataMap = loadProjectManagerServletI18nData();
        final boolean admin = false;//Boolean.parseBoolean(getParam(req, "permissions[admin]"));
        final boolean read = Boolean.parseBoolean(getParam(req, "permissions[read]"));
        final boolean write = Boolean.parseBoolean(getParam(req, "permissions[write]"));
        final boolean execute = Boolean.parseBoolean(getParam(req, "permissions[execute]"));
        final boolean schedule = Boolean.parseBoolean(getParam(req, "permissions[schedule]"));

        final boolean group = false;//Boolean.parseBoolean(getParam(req, "group"));

        final String name = getParam(req, "name");
        final Permission perm;
        if (group) {
            perm = project.getGroupPermission(name);
        } else {
            perm = project.getUserPermission(name);
        }

        if (perm == null) {
            ret.put("error", "Permissions for " + name + " cannot be found.");
            return;
        }

        if (admin || read || write || execute || schedule) {
            if (admin) {
                perm.setPermission(Type.ADMIN, true);
                perm.setPermission(Type.READ, false);
                perm.setPermission(Type.WRITE, false);
                perm.setPermission(Type.EXECUTE, false);
                perm.setPermission(Type.SCHEDULE, false);
            } else {
                perm.setPermission(Type.ADMIN, false);
                perm.setPermission(Type.READ, read);
                perm.setPermission(Type.WRITE, write);
                perm.setPermission(Type.EXECUTE, execute);
                perm.setPermission(Type.SCHEDULE, schedule);
            }

            try {
                WtssUser wtssUser = this.transitionService.getSystemUserByUserName(name);
                if (wtssUser != null) {
                    String createUser = project.getCreateUser();
                    WtssUser currentUser = this.transitionService.getSystemUserByUserName(wtssUser.getUsername());
                    // 判断用户角色  roleId 1:管理员, 2:普通用户
                    if (currentUser.getRoleId() != 1) {
                        if (createUser.startsWith("WTSS")) {
                            String userId = wtssUser.getUserId();
                            if (!userId.startsWith("wtss_WTSS")) {
                                int roleId = wtssUser.getRoleId();
                                // 只校验非管理员
                                if (roleId != 1) {
                                    if (Pattern.compile("^[0-9]+$").matcher(userId).matches()) {
                                        if (write || execute || schedule) {
                                            throw new SystemUserManagerException(dataMap.get("cannotpermitrealnamewriteexecsch"));
                                        } else {
                                            // 执行添加
                                            this.projectManager.updateProjectPermission(project, name, perm, group, user);
                                        }
                                    } else if (userId.startsWith("wtss_hduser")) {
                                        if (write || execute || schedule) {
                                            throw new SystemUserManagerException(dataMap.get("cannotpermitsystemUserwriteexecsch"));
                                        } else {
                                            // 执行添加
                                            this.projectManager.updateProjectPermission(project, name, perm, group, user);
                                        }
                                    } else {
                                        throw new SystemUserManagerException(dataMap.get("canNotAddUnknown"));
                                    }
                                } else {
                                    this.projectManager.updateProjectPermission(project, name, perm, group, user);
                                }
                            }
                        } else {
                            this.projectManager.updateProjectPermission(project, name, perm, group, user);
                        }
                    } else {
                        this.projectManager.updateProjectPermission(project, name, perm, group, user);
                    }

                } else {
                    ret.put("error", "User is not exist.");
                }
            } catch (final ProjectManagerException e) {
                ret.put("error", e.getMessage());
            } catch (SystemUserManagerException e) {
                // 发生异常则回显只读属性即可
                perm.setPermission(Type.ADMIN, false);
                perm.setPermission(Type.READ, true);
                perm.setPermission(Type.WRITE, false);
                perm.setPermission(Type.EXECUTE, false);
                perm.setPermission(Type.SCHEDULE, false);
                this.projectManager.updateProjectPermission(project, name, perm, group, user);
                ret.put("error", e.getMessage());
            }
        } else {
            try {
                this.projectManager.removeProjectPermission(project, name, group, user);
            } catch (final ProjectManagerException e) {
                ret.put("error", e.getMessage());
            }
        }
    }

    /**
     * this only returns user permissions, but not group permissions and proxy users
     */
    private void ajaxGetPermissions(final Project project, final HashMap<String, Object> ret) {
        final ArrayList<HashMap<String, Object>> permissions = new ArrayList<>();
        for (final Pair<String, Permission> perm : project.getUserPermissions()) {
            final HashMap<String, Object> permObj = new HashMap<>();
            final String userId = perm.getFirst();
            permObj.put("username", userId);
            permObj.put("permission", perm.getSecond().toStringArray());

            permissions.add(permObj);
        }

        ret.put("permissions", permissions);
    }

    private void ajaxGetGroupPermissions(final Project project,
        final HashMap<String, Object> ret) {
        final ArrayList<HashMap<String, Object>> permissions =
            new ArrayList<>();
        for (final Pair<String, Permission> perm : project.getGroupPermissions()) {
            final HashMap<String, Object> permObj = new HashMap<>();
            final String userId = perm.getFirst();
            permObj.put("username", userId);
            permObj.put("permission", perm.getSecond().toStringArray());

            permissions.add(permObj);
        }

        ret.put("permissions", permissions);
    }

    private void ajaxGetProxyUsers(final Project project, final HashMap<String, Object> ret) {
        final String[] proxyUsers = project.getProxyUsers().toArray(new String[0]);
        ret.put("proxyUsers", proxyUsers);
    }

    private void ajaxCheckForWritePermission(final Project project, final User user,
        final HashMap<String, Object> ret) {
        ret.put("hasWritePermission", hasPermission(project, user, Type.WRITE));
    }

    private void handleProjectLogsPage(final HttpServletRequest req, final HttpServletResponse resp,
        final Session session) throws ServletException, IOException {

        final Page page = newPage(req, resp, session,"azkaban/webapp/servlet/velocity/projectlogpage.vm");

        Map<String, String> projectlogpageMap;
        Map<String, String> subPageMap1;
        Map<String, String> subPageMap2;
        Map<String, String> subPageMap3;
        Map<String, String> subPageMap4;
        Map<String, String> subPageMap5;

        String languageType = LoadJsonUtils.getLanguageType();
        if (languageType.equalsIgnoreCase("zh_CN")) {

            // 添加国际化标签
            projectlogpageMap = LoadJsonUtils.transJson("/com.webank.wedatasphere.schedulis.i18n.conf/azkaban-web-server-zh_CN.json",
                "azkaban.webapp.servlet.velocity.projectlogpage.vm");
            subPageMap1 = LoadJsonUtils.transJson("/com.webank.wedatasphere.schedulis.i18n.conf/azkaban-web-server-zh_CN.json",
                "azkaban.webapp.servlet.velocity.nav.vm");
            subPageMap2 = LoadJsonUtils.transJson("/com.webank.wedatasphere.schedulis.i18n.conf/azkaban-web-server-zh_CN.json",
                "azkaban.webapp.servlet.velocity.projectpageheader.vm");
            subPageMap3 = LoadJsonUtils.transJson("/com.webank.wedatasphere.schedulis.i18n.conf/azkaban-web-server-zh_CN.json",
                "azkaban.webapp.servlet.velocity.projectnav.vm");
            subPageMap4 = LoadJsonUtils.transJson("/com.webank.wedatasphere.schedulis.i18n.conf/azkaban-web-server-zh_CN.json",
                "azkaban.webapp.servlet.velocity.projectsidebar.vm");
            subPageMap5 = LoadJsonUtils.transJson("/com.webank.wedatasphere.schedulis.i18n.conf/azkaban-web-server-zh_CN.json",
                "azkaban.webapp.servlet.velocity.projectmodals.vm");
        } else {
            // 添加国际化标签
            projectlogpageMap = LoadJsonUtils.transJson("/com.webank.wedatasphere.schedulis.i18n.conf/azkaban-web-server-en_US.json",
                "azkaban.webapp.servlet.velocity.projectlogpage.vm");
            subPageMap1 = LoadJsonUtils.transJson("/com.webank.wedatasphere.schedulis.i18n.conf/azkaban-web-server-en_US.json",
                "azkaban.webapp.servlet.velocity.nav.vm");
            subPageMap2 = LoadJsonUtils.transJson("/com.webank.wedatasphere.schedulis.i18n.conf/azkaban-web-server-en_US.json",
                "azkaban.webapp.servlet.velocity.projectpageheader.vm");
            subPageMap3 = LoadJsonUtils.transJson("/com.webank.wedatasphere.schedulis.i18n.conf/azkaban-web-server-en_US.json",
                "azkaban.webapp.servlet.velocity.projectnav.vm");
            subPageMap4 = LoadJsonUtils.transJson("/com.webank.wedatasphere.schedulis.i18n.conf/azkaban-web-server-en_US.json",
                "azkaban.webapp.servlet.velocity.projectsidebar.vm");
            subPageMap5 = LoadJsonUtils.transJson("/com.webank.wedatasphere.schedulis.i18n.conf/azkaban-web-server-en_US.json",
                "azkaban.webapp.servlet.velocity.projectmodals.vm");
        }

        projectlogpageMap.forEach(page::add);
        subPageMap1.forEach(page::add);
        subPageMap2.forEach(page::add);
        subPageMap3.forEach(page::add);
        subPageMap4.forEach(page::add);
        subPageMap5.forEach(page::add);
        page.add("currentlangType", languageType);

        final String projectName = getParam(req, "project");

        final User user = session.getUser();
        PageUtils.hideUploadButtonWhenNeeded(page, session, this.lockdownUploadProjects);
        Project project = null;
        try {
            project = this.projectManager.getProject(projectName);
            if (project == null) {
                page.add("errorMsg", "项目 " + projectName + " 不存在.");
            } else {
                if (!hasPermission(project, user, Type.READ)) {
                    throw new AccessControlException("没有权限查看这个项目 " + projectName + ".");
                }

                page.add("project", project);
                page.add("admins", Utils.flattenToString(project.getUsersWithPermission(Type.ADMIN), ","));
                final Permission perm = this.getPermissionObject(project, user, Type.ADMIN);
                page.add("userpermission", perm);

                final boolean adminPerm = perm.isPermissionSet(Type.ADMIN);
                if (adminPerm) {
                    page.add("admin", true);
                }
                // Set this so we can display execute buttons only to those who have
                // access.
                if (perm.isPermissionSet(Type.EXECUTE) || adminPerm) {
                    page.add("exec", true);
                } else {
                    page.add("exec", false);
                }

                if (user.getRoles().contains("admin")) {
                    page.add("isSystemAdmin", true);
                }

                if (hasPermission(project, user, Type.ADMIN)) {
                    page.add("isProjectAdmin", true);
                }

                int uploadFlag;
                // 先判断开关是否打开,如果开关打开,则校验部门上传权限,如果关闭,则不需要校验
                // 判断是否具有上传权限  uploadFlag 1:允许, 2:不允许,其他值:不允许
                if (wtss_dep_upload_privilege_check) {
                    uploadFlag = checkDepartmentUploadFlag(user);
                } else {
                    uploadFlag = 1;
                }

                // 需要首先验证部门上传权限是否被允许, 再判断是否满足原有上传许可的逻辑
                if ((uploadFlag == 1) && (perm.isPermissionSet(Type.WRITE) || adminPerm)) {
                    page.add("isWritePerm", true);
                }

            }
        } catch (final AccessControlException e) {
            page.add("errorMsg", e.getMessage());
        } catch (SystemUserManagerException e) {
            logger.error("部门上传权限标识查询异常.");
        }

        final int numBytes = 1024;

        // Really sucks if we do a lot of these because it'll eat up memory fast.
        // But it's expected that this won't be a heavily used thing. If it is,
        // then we'll revisit it to make it more stream friendly.
        final StringBuffer buffer = new StringBuffer(numBytes);
        page.add("log", buffer.toString());

        page.render();
    }

    private void handleJobHistoryPage(final HttpServletRequest req,
        final HttpServletResponse resp, final Session session) throws ServletException,
        IOException {
        final Page page =
            newPage(req, resp, session,
                "azkaban/webapp/servlet/velocity/jobhistorypage.vm");

        String languageType = LoadJsonUtils.getLanguageType();
        Map<String, String> jobhistorypageMap;
        Map<String, String> subPageMap1;
        if (languageType.equalsIgnoreCase("zh_CN")) {
            // 添加国际化标签
            jobhistorypageMap = LoadJsonUtils.transJson("/com.webank.wedatasphere.schedulis.i18n.conf/azkaban-web-server-zh_CN.json",
                "azkaban.webapp.servlet.velocity.jobhistorypage.vm");
            subPageMap1 = LoadJsonUtils.transJson("/com.webank.wedatasphere.schedulis.i18n.conf/azkaban-web-server-zh_CN.json",
                "azkaban.webapp.servlet.velocity.nav.vm");
        } else {
            // 添加国际化标签
            jobhistorypageMap = LoadJsonUtils.transJson("/com.webank.wedatasphere.schedulis.i18n.conf/azkaban-web-server-en_US.json",
                "azkaban.webapp.servlet.velocity.jobhistorypage.vm");
            subPageMap1 = LoadJsonUtils.transJson("/com.webank.wedatasphere.schedulis.i18n.conf/azkaban-web-server-en_US.json",
                "azkaban.webapp.servlet.velocity.nav.vm");
        }

        jobhistorypageMap.forEach(page::add);
        subPageMap1.forEach(page::add);

        final String projectName = getParam(req, "project");
        final User user = session.getUser();

        final Project project = this.projectManager.getProject(projectName);

        Map<String, String> dataMap = loadProjectManagerServletI18nData();

        if (project == null) {
            page.add("errorMsg", dataMap.get("project") + projectName + dataMap.get("notExist"));
            page.render();
            return;
        }
        if (!hasPermission(project, user, Type.READ)) {
            page.add("errorMsg", dataMap.get("noPerAccessProject") + projectName + ".");
            page.render();
            return;
        }

        final String jobId = getParam(req, "job");
        final int pageNum = getIntParam(req, "page", 1);
        final int pageSize = getIntParam(req, "size", 25);

        page.add("projectId", project.getId());
        page.add("projectName", project.getName());
        page.add("jobId", jobId);
        page.add("page", pageNum);

        final int skipPage = (pageNum - 1) * pageSize;

        int numResults = 0;
        try {
            numResults = this.executorManagerAdapter.getNumberOfJobExecutions(project, jobId);
            final int maxPage = (numResults / pageSize) + 1;
            List<ExecutableJobInfo> jobInfo =
                this.executorManagerAdapter.getExecutableJobs(project, jobId, skipPage, pageSize);

            if (jobInfo == null || jobInfo.isEmpty()) {
                jobInfo = null;
            }

            if (jobInfo != null) {
                jobInfo.stream().forEach(executableJobInfo -> {

                    ExecutableFlow executionFlow = null;
                    try {
                        executionFlow = this.executorManagerAdapter.getExecutableFlow(executableJobInfo.getExecId());
                    } catch (ExecutorManagerException e) {
                        e.printStackTrace();
                    }

                    Map<String, String> repeatMap = executionFlow.getRepeatOption();
                    if (!repeatMap.isEmpty()) {

                        Long recoverRunDate = Long.valueOf(String.valueOf(repeatMap.get("startTimeLong")));

                        LocalDateTime localDateTime = new LocalDateTime(new Date(recoverRunDate)).minusDays(1);

                        Date date = localDateTime.toDate();

                        executableJobInfo.setRunDate(date.getTime());
                    } else {
                        Long runDate = executionFlow.getStartTime();
                        if (-1 != runDate) {
                            LocalDateTime localDateTime = new LocalDateTime(new Date(runDate)).minusDays(1);

                            Date date = localDateTime.toDate();

                            executableJobInfo.setRunDate(date.getTime());
                        } else {
                            executableJobInfo.setRunDate(runDate);
                        }
                    }
                });
            }

            page.add("history", jobInfo);

            page.add("previous", new PageSelection(dataMap.get("previousPage"), pageSize, true, false,
                Math.max(pageNum - 1, 1)));

            page.add("next", new PageSelection(dataMap.get("nextPage"), pageSize, false, false, Math.min(
                pageNum + 1, maxPage)));

            if (jobInfo != null) {


//        long moyenne = 0;
//        long allRunTime = 0;
//        int successFlowNum = 0;
//        for (final ExecutableJobInfo info : jobInfo) {
//          if(Status.SUCCEEDED.equals(info.getStatus())){
//            successFlowNum += 1;
//            allRunTime += (info.getEndTime() - info.getStartTime());
//          }
//        }
//        if(allRunTime !=0 && successFlowNum !=0){
//          moyenne = allRunTime/successFlowNum;
//        }

                final ArrayList<Object> dataSeries = new ArrayList<>();
                for (final ExecutableJobInfo info : jobInfo) {
                    final Map<String, Object> map = info.toObject();
                    dataSeries.add(map);
                }
                page.add("dataSeries", JSONUtils.toJSON(dataSeries));
            } else {
                page.add("dataSeries", "[]");
            }
        } catch (final ExecutorManagerException e) {
            page.add("errorMsg", e.getMessage());
        }

        // Now for the 5 other values.
        int pageStartValue = 1;
        if (pageNum > 3) {
            pageStartValue = pageNum - 2;
        }
        final int maxPage = (numResults / pageSize) + 1;

        page.add("page1",
            new PageSelection(String.valueOf(pageStartValue), pageSize,pageStartValue > maxPage,
                pageStartValue == pageNum, Math.min(pageStartValue, maxPage)));
        pageStartValue++;

        page.add("page2",
            new PageSelection(String.valueOf(pageStartValue), pageSize,pageStartValue > maxPage,
                pageStartValue == pageNum, Math.min(pageStartValue, maxPage)));
        pageStartValue++;

        page.add("page3",
            new PageSelection(String.valueOf(pageStartValue), pageSize,pageStartValue > maxPage,
                pageStartValue == pageNum, Math.min(pageStartValue, maxPage)));
        pageStartValue++;

        page.add("page4",
            new PageSelection(String.valueOf(pageStartValue), pageSize,pageStartValue > maxPage,
                pageStartValue == pageNum, Math.min(pageStartValue, maxPage)));
        pageStartValue++;

        page.add("page5",
            new PageSelection(String.valueOf(pageStartValue), pageSize,pageStartValue > maxPage,
                pageStartValue == pageNum, Math.min(pageStartValue, maxPage)));

        page.add("currentlangType", languageType);
        page.render();
    }

    //项目权限页面处理方法
    private void handlePermissionPage(final HttpServletRequest req, final HttpServletResponse resp,
        final Session session) throws ServletException {

        final Page page = newPage(req, resp, session,"azkaban/webapp/servlet/velocity/permissionspage.vm");

        String languageType = LoadJsonUtils.getLanguageType();
        Map<String, String> permissionspageMap;
        Map<String, String> subPageMap1;
        Map<String, String> subPageMap2;
        Map<String, String> subPageMap3;
        Map<String, String> subPageMap4;
        Map<String, String> subPageMap5;
        if (languageType.equalsIgnoreCase("en_US")) {
            // 添加国际化标签
            permissionspageMap = LoadJsonUtils.transJson("/com.webank.wedatasphere.schedulis.i18n.conf/azkaban-web-server-en_US.json",
                "azkaban.webapp.servlet.velocity.permissionspage.vm");
            subPageMap1 = LoadJsonUtils.transJson("/com.webank.wedatasphere.schedulis.i18n.conf/azkaban-web-server-en_US.json",
                "azkaban.webapp.servlet.velocity.nav.vm");
            subPageMap2 = LoadJsonUtils.transJson("/com.webank.wedatasphere.schedulis.i18n.conf/azkaban-web-server-en_US.json",
                "azkaban.webapp.servlet.velocity.projectmodals.vm");
            subPageMap3 = LoadJsonUtils.transJson("/com.webank.wedatasphere.schedulis.i18n.conf/azkaban-web-server-en_US.json",
                "azkaban.webapp.servlet.velocity.projectsidebar.vm");
            subPageMap4 = LoadJsonUtils.transJson("/com.webank.wedatasphere.schedulis.i18n.conf/azkaban-web-server-en_US.json",
                "azkaban.webapp.servlet.velocity.projectnav.vm");
            subPageMap5 = LoadJsonUtils.transJson("/com.webank.wedatasphere.schedulis.i18n.conf/azkaban-web-server-en_US.json",
                "azkaban.webapp.servlet.velocity.projectpageheader.vm");
        } else {
            permissionspageMap = LoadJsonUtils.transJson("/com.webank.wedatasphere.schedulis.i18n.conf/azkaban-web-server-zh_CN.json",
                "azkaban.webapp.servlet.velocity.permissionspage.vm");
            subPageMap1 = LoadJsonUtils.transJson("/com.webank.wedatasphere.schedulis.i18n.conf/azkaban-web-server-zh_CN.json",
                "azkaban.webapp.servlet.velocity.nav.vm");
            subPageMap2 = LoadJsonUtils.transJson("/com.webank.wedatasphere.schedulis.i18n.conf/azkaban-web-server-zh_CN.json",
                "azkaban.webapp.servlet.velocity.projectmodals.vm");
            subPageMap3 = LoadJsonUtils.transJson("/com.webank.wedatasphere.schedulis.i18n.conf/azkaban-web-server-zh_CN.json",
                "azkaban.webapp.servlet.velocity.projectsidebar.vm");
            subPageMap4 = LoadJsonUtils.transJson("/com.webank.wedatasphere.schedulis.i18n.conf/azkaban-web-server-zh_CN.json",
                "azkaban.webapp.servlet.velocity.projectnav.vm");
            subPageMap5 = LoadJsonUtils.transJson("/com.webank.wedatasphere.schedulis.i18n.conf/azkaban-web-server-zh_CN.json",
                "azkaban.webapp.servlet.velocity.projectpageheader.vm");
        }

        permissionspageMap.forEach(page::add);
        subPageMap1.forEach(page::add);
        subPageMap2.forEach(page::add);
        subPageMap3.forEach(page::add);
        subPageMap4.forEach(page::add);
        subPageMap5.forEach(page::add);

        final String projectName = getParam(req, "project");
        final User user = session.getUser();
        PageUtils.hideUploadButtonWhenNeeded(page, session, this.lockdownUploadProjects);
        Project project = null;

        Map<String, String> dataMap = loadProjectManagerServletI18nData();
        try {
            project = this.projectManager.getProject(projectName);
            if (project == null) {
                page.add("errorMsg", dataMap.get("project") + projectName + dataMap.get("notExist"));
            } else {
                if (!hasPermission(project, user, Type.READ)) {
                    throw new AccessControlException(dataMap.get("noPerAccessProject") + projectName + ".");
                }

                page.add("project", project);
                page.add("username", user.getUserId());
                page.add("admins", Utils.flattenToString(
                    project.getUsersWithPermission(Type.ADMIN), ","));
                final Permission perm = this.getPermissionObject(project, user, Type.ADMIN);
                page.add("userpermission", perm);

                if (perm.isPermissionSet(Type.ADMIN)) {
                    page.add("admin", true);
                }

                final List<Pair<String, Permission>> userPermission =
                    project.getUserPermissions();
                if (userPermission != null && !userPermission.isEmpty()) {
                    page.add("permissions", userPermission);
                }

                final List<Pair<String, Permission>> groupPermission =
                    project.getGroupPermissions();
                if (groupPermission != null && !groupPermission.isEmpty()) {
                    page.add("groupPermissions", groupPermission);
                }

                final Set<String> proxyUsers = project.getProxyUsers();
                if (proxyUsers != null && !proxyUsers.isEmpty()) {
                    WtssUser wtssUser = null;
                    try {
                        wtssUser = transitionService.getSystemUserByUserName(user.getUserId());
                    } catch (SystemUserManagerException e){
                        logger.error("get wtssUser failed, caused by: ", e);
                    }
                    if(wtssUser != null && wtssUser.getProxyUsers() != null) {
                        String[] proxySplit = wtssUser.getProxyUsers().split("\\s*,\\s*");
                        logger.info("add proxyUsers," + ArrayUtils.toString(proxySplit));
                        page.add("proxyUsers", proxySplit);
                    }
                }

                if (user.getRoles().contains("admin")) {
                    page.add("isSystemAdmin", true);
                }

                if (hasPermission(project, user, Type.ADMIN)) {
                    page.add("isProjectAdmin", true);
                }

                int uploadFlag;
                // 先判断开关是否打开,如果开关打开,则校验部门上传权限,如果关闭,则不需要校验
                // 判断是否具有上传权限  uploadFlag 1:允许, 2:不允许,其他值:不允许
                if (wtss_dep_upload_privilege_check) {
                    uploadFlag = checkDepartmentUploadFlag(user);
                } else {
                    uploadFlag = 1;
                }

                // 需要首先验证部门上传权限是否被允许, 再判断是否满足原有上传许可的逻辑
                if ((uploadFlag == 1) && (perm.isPermissionSet(Type.WRITE) || hasPermission(project, user, Type.ADMIN))) {
                    page.add("isWritePerm", true);
                } else {
                    page.add("isWritePerm", false);
                }

                List<Map<String, Object>> projectAdminList = new ArrayList<>();
                if (userPermission != null && !userPermission.isEmpty()) {
                    for (Pair<String, Permission> pair : userPermission) {
                        if (pair.getSecond().isPermissionNameSet("ADMIN")) {
                            Map<String, Object> adminMap = new HashMap<>();
                            adminMap.put("username", pair.getFirst());
                            adminMap.put("permission", pair.getSecond());
                            projectAdminList.add(adminMap);
                        }
                    }
                    page.add("projectAdminList", projectAdminList);
                }

                List<Map<String, Object>> projectUserList = new ArrayList<>();
                if (userPermission != null && !userPermission.isEmpty()) {
                    for (Pair<String, Permission> pair : userPermission) {
                        if (!pair.getSecond().isPermissionNameSet("ADMIN")) {
                            Map<String, Object> userMap = new HashMap<>();
                            userMap.put("username", pair.getFirst());
                            userMap.put("permission", pair.getSecond());
                            projectUserList.add(userMap);
                        }
                    }
                    page.add("projectUserList", projectUserList);
                }


            }
        } catch (final AccessControlException e) {
            page.add("errorMsg", e.getMessage());
        } catch (SystemUserManagerException e) {
            logger.error("部门上传权限标识查询异常.");
        }
        page.add("currentlangType", languageType);
        page.render();
    }

    private void handleJobPage(final HttpServletRequest req, final HttpServletResponse resp,
        final Session session) throws ServletException {

        final Page page = newPage(req, resp, session,"azkaban/webapp/servlet/velocity/jobpage.vm");

        String languageType = LoadJsonUtils.getLanguageType();
        Map<String, String> jobpageMap;
        Map<String, String> subPageMap1;
        if (languageType.equalsIgnoreCase("zh_CN")) {
            // 添加国际化标签
            jobpageMap = LoadJsonUtils.transJson("/com.webank.wedatasphere.schedulis.i18n.conf/azkaban-web-server-zh_CN.json",
                "azkaban.webapp.servlet.velocity.jobpage.vm");
            subPageMap1 = LoadJsonUtils.transJson("/com.webank.wedatasphere.schedulis.i18n.conf/azkaban-web-server-zh_CN.json",
                "azkaban.webapp.servlet.velocity.nav.vm");
        } else {
            // 添加国际化标签
            jobpageMap = LoadJsonUtils.transJson("/com.webank.wedatasphere.schedulis.i18n.conf/azkaban-web-server-en_US.json",
                "azkaban.webapp.servlet.velocity.jobpage.vm");
            subPageMap1 = LoadJsonUtils.transJson("/com.webank.wedatasphere.schedulis.i18n.conf/azkaban-web-server-en_US.json",
                "azkaban.webapp.servlet.velocity.nav.vm");
        }

        jobpageMap.forEach(page::add);
        subPageMap1.forEach(page::add);

        final String projectName = getParam(req, "project");
        final String flowName = getParam(req, "flow");
        final String jobName = getParam(req, "job");

        final User user = session.getUser();
        Project project = null;
        Flow flow = null;

        Map<String, String> dataMap = loadProjectManagerServletI18nData();

        try {
            project = this.projectManager.getProject(projectName);
            logger.info("JobPage: project " + projectName + " version is " + project.getVersion()
                + ", reference is " + System.identityHashCode(project));
            if (project == null) {
                page.add("errorMsg", dataMap.get("project") + projectName + dataMap.get("notExist"));
                page.render();
                return;
            }
            if (!hasPermission(project, user, Type.READ)) {
                throw new AccessControlException(dataMap.get("noPerAccessProject") + projectName + ".");
            }

            page.add("project", project);
            flow = project.getFlow(flowName);
            if (flow == null) {
                page.add("errorMsg", dataMap.get("flow") + flowName + dataMap.get("notExist"));
                page.render();
                return;
            }

            page.add("flowid", flow.getId());
            page.add("flowId", flow.getId());
            final Node node = flow.getNode(jobName);
            if (node == null) {
                page.add("errorMsg", dataMap.get("job") + jobName + dataMap.get("notExist"));
                page.render();
                return;
            }

            Props jobProp = this.projectManager.getJobOverrideProperty(project, flow, jobName, node.getJobSource());
            if (jobProp == null) {
                jobProp = this.projectManager.getProperties(project, flow, jobName, node.getJobSource());
            }

            page.add("jobid", node.getId());
            page.add("jobtype", node.getType());
            if (node.getCondition() != null) {
                page.add("condition", node.getCondition());
            }

            final ArrayList<String> dependencies = new ArrayList<>();
            final Set<Edge> inEdges = flow.getInEdges(node.getId());
            if (inEdges != null) {
                for (final Edge dependency : inEdges) {
                    dependencies.add(dependency.getSourceId());
                }
            }
            if (!dependencies.isEmpty()) {
                page.add("dependencies", dependencies);
            }

            final ArrayList<String> dependents = new ArrayList<>();
            final Set<Edge> outEdges = flow.getOutEdges(node.getId());
            if (outEdges != null) {
                for (final Edge dependent : outEdges) {
                    dependents.add(dependent.getTargetId());
                }
            }
            if (!dependents.isEmpty()) {
                page.add("dependents", dependents);
            }

            // Resolve property dependencies
            final ArrayList<String> source = new ArrayList<>();
            final String nodeSource = node.getPropsSource();
            if (nodeSource != null) {
                source.add(nodeSource);
                FlowProps parent = flow.getFlowProps(nodeSource);
                while (parent.getInheritedSource() != null) {
                    source.add(parent.getInheritedSource());
                    parent = flow.getFlowProps(parent.getInheritedSource());
                }
            }
            if (!source.isEmpty()) {
                page.add("properties", source);
            }

            final ArrayList<Pair<String, String>> parameters =
                new ArrayList<>();
            // Parameter
            for (final String key : jobProp.getKeySet()) {
                final String value = jobProp.get(key);
                parameters.add(new Pair<>(key, value));
            }
            //TODO 版本稳定后优化 source.type 跟 data.object 的排序
            //前端排序优化 Job Properties 根据属性名排序
            final List<Pair<String, String>> sortedParameters = parameters.stream().sorted(Comparator.comparing(Pair::getFirst)).collect(
                Collectors.toList());

            final List<Pair<String, String>> finalParams = new ArrayList<>();
            //data.object 跟 source.type 组合排序
            sortedParameters.stream().forEach(m -> {
                    String dnum;
                    String dkey = m.getFirst();
                    if (dkey.contains("data.object")) {
                        dnum = StringUtils.substringAfter(dkey, "data.object");
                        finalParams.add(m);
                        String snum;
                        for (Pair<String, String> job : sortedParameters) {
                            String skey = job.getFirst();
                            if (skey.contains("source.type")) {
                                snum = StringUtils.substringAfter(skey, "source.type");
                                if (dnum.equals(snum)) {
                                    finalParams.add(job);
                                }
                            }
                        }
                    } else if (!dkey.contains("data.object") && !dkey.contains("source.type")) {
                        finalParams.add(m);
                    }
                }
            );

            page.add("parameters", finalParams);
        } catch (final AccessControlException e) {
            page.add("errorMsg", e.getMessage());
        } catch (final ProjectManagerException e) {
            page.add("errorMsg", e.getMessage());
        }
        page.add("currentlangType", languageType);
        page.render();
    }

    private void handlePropertyPage(final HttpServletRequest req,
        final HttpServletResponse resp, final Session session) throws ServletException {

        final Page page = newPage(req, resp, session,"azkaban/webapp/servlet/velocity/propertypage.vm");

        String languageType = LoadJsonUtils.getLanguageType();
        Map<String, String> propertypageMap;
        Map<String, String> subPageMap1;
        if (languageType.equalsIgnoreCase("zh_CN")) {
            // 添加国际化标签
            propertypageMap = LoadJsonUtils.transJson("/com.webank.wedatasphere.schedulis.i18n.conf/azkaban-web-server-zh_CN.json",
                "azkaban.webapp.servlet.velocity.propertypage.vm");
            subPageMap1 = LoadJsonUtils.transJson("/com.webank.wedatasphere.schedulis.i18n.conf/azkaban-web-server-zh_CN.json",
                "azkaban.webapp.servlet.velocity.nav.vm");
        } else {
            // 添加国际化标签
            propertypageMap = LoadJsonUtils.transJson("/com.webank.wedatasphere.schedulis.i18n.conf/azkaban-web-server-en_US.json",
                "azkaban.webapp.servlet.velocity.propertypage.vm");
            subPageMap1 = LoadJsonUtils.transJson("/com.webank.wedatasphere.schedulis.i18n.conf/azkaban-web-server-en_US.json",
                "azkaban.webapp.servlet.velocity.nav.vm");
        }

        propertypageMap.forEach(page::add);
        subPageMap1.forEach(page::add);

        final String projectName = getParam(req, "project");
        final String flowName = getParam(req, "flow");
        final String jobName = getParam(req, "job");
        final String propSource = getParam(req, "prop");

        final User user = session.getUser();
        Project project = null;
        Flow flow = null;

        Map<String, String> dataMap = loadProjectManagerServletI18nData();
        try {
            project = this.projectManager.getProject(projectName);
            if (project == null) {
                page.add("errorMsg", dataMap.get("project") + projectName + dataMap.get("notExist"));
                logger.info("Display project property. Project " + projectName + " not found.");
                page.render();
                return;
            }

            if (!hasPermission(project, user, Type.READ)) {
                throw new AccessControlException(dataMap.get("noPerAccessProject") + projectName + ".");
            }
            page.add("project", project);

            flow = project.getFlow(flowName);
            if (flow == null) {
                page.add("errorMsg", dataMap.get("flow") + flowName + dataMap.get("notExist"));
                logger.info("Display project property. Project " + projectName +
                    " Flow " + flowName + " not found.");
                page.render();
                return;
            }

            page.add("flowid", flow.getId());
            final Node node = flow.getNode(jobName);
            if (node == null) {
                page.add("errorMsg", dataMap.get("flow") + jobName + dataMap.get("notExist"));
                logger.info("Display project property. Project " + projectName +
                    " Flow " + flowName + " Job " + jobName + " not found.");
                page.render();
                return;
            }

            final Props prop = this.projectManager.getProperties(project, flow, null, propSource);
            if (prop == null) {
                page.add("errorMsg", dataMap.get("config") + propSource + dataMap.get("notExist"));
                logger.info("Display project property. Project " + projectName +
                    " Flow " + flowName + " Job " + jobName +
                    " Property " + propSource + " not found.");
                page.render();
                return;

            }
            page.add("property", propSource);
            page.add("jobid", node.getId());

            // Resolve property dependencies
            final ArrayList<String> inheritProps = new ArrayList<>();
            FlowProps parent = flow.getFlowProps(propSource);
            while (parent.getInheritedSource() != null) {
                inheritProps.add(parent.getInheritedSource());
                parent = flow.getFlowProps(parent.getInheritedSource());
            }
            if (!inheritProps.isEmpty()) {
                page.add("inheritedproperties", inheritProps);
            }

            final ArrayList<String> dependingProps = new ArrayList<>();
            FlowProps child = flow.getFlowProps(flow.getNode(jobName).getPropsSource());
            while (!child.getSource().equals(propSource)) {
                dependingProps.add(child.getSource());
                child = flow.getFlowProps(child.getInheritedSource());
            }
            if (!dependingProps.isEmpty()) {
                page.add("dependingproperties", dependingProps);
            }

            final ArrayList<Pair<String, String>> parameters = new ArrayList<>();
            // Parameter
            for (final String key : prop.getKeySet()) {
                final String value = prop.get(key);
                parameters.add(new Pair<>(key, value));
            }

            page.add("parameters", parameters);
        } catch (final AccessControlException e) {
            page.add("errorMsg", e.getMessage());
        } catch (final ProjectManagerException e) {
            page.add("errorMsg", e.getMessage());
        }

        page.render();
    }

    private void handleFlowPage(final HttpServletRequest req, final HttpServletResponse resp,
        final Session session) throws ServletException {

        final Page page = newPage(req, resp, session,"azkaban/webapp/servlet/velocity/flowpage.vm");

        // 加载国际化资源
        Map<String, Map<String, String>> vmDataMap = loadFlowpageI18nData();
        vmDataMap.forEach((vm, data) -> data.forEach(page::add));

        final String projectName = getParam(req, "project");
        final String flowName = getParam(req, "flow");

        final User user = session.getUser();

        page.add("loginUser", user.getUserId());

        Project project = null;
        Flow flow = null;

        Map<String, String> dataMap = loadProjectManagerServletI18nData();
        try {
            project = this.projectManager.getProject(projectName);

            if (project == null) {
                page.add("errorMsg", dataMap.get("project") + projectName + dataMap.get("notExist"));
                page.render();
                return;
            }

            if (!hasPermission(project, user, Type.READ)) {
                throw new AccessControlException("No permission Project " + projectName
                    + ".");
            }

            final Permission perm = this.getPermissionObject(project, user, Type.ADMIN);

            final boolean adminPerm = perm.isPermissionSet(Type.ADMIN);

            if (perm.isPermissionSet(Type.EXECUTE) || adminPerm) {
                page.add("execPerm", true);
            } else {
                page.add("execPerm", false);
            }
            if (perm.isPermissionSet(Type.SCHEDULE) || adminPerm) {
                page.add("schedulePerm", true);
            } else {
                page.add("schedulePerm", false);
            }

            page.add("project", project);
            flow = project.getFlow(flowName);
            if (flow == null) {
                page.add("errorMsg", dataMap.get("flow") + flowName + dataMap.get("notExist"));
            } else {
                page.add("flowid", flow.getId());
            }
        } catch (final AccessControlException e) {
            page.add("errorMsg", e.getMessage());
        }

        String languageType = LoadJsonUtils.getLanguageType();

        page.add("currentlangType", languageType);
        page.render();
    }

    //项目详细页面请求处理
    private void handleProjectPage(final HttpServletRequest req,
        final HttpServletResponse resp, final Session session) throws ServletException {
        final Page page = newPage(req, resp, session,"azkaban/webapp/servlet/velocity/projectpage.vm");

        String languageType = LoadJsonUtils.getLanguageType();
        Map<String, String> projectpageMap;
        Map<String, String> subPageMap1;
        Map<String, String> subPageMap2;
        Map<String, String> subPageMap3;
        Map<String, String> subPageMap4;
        Map<String, String> subPageMap5;
        Map<String, String> subPageMap6;
        Map<String, String> subPageMap7;

        if (languageType.equalsIgnoreCase("zh_CN")) {
            // 添加国际化标签
            projectpageMap = LoadJsonUtils.transJson("/com.webank.wedatasphere.schedulis.i18n.conf/azkaban-web-server-zh_CN.json",
                "azkaban.webapp.servlet.velocity.projectpage.vm");
            subPageMap1 = LoadJsonUtils.transJson("/com.webank.wedatasphere.schedulis.i18n.conf/azkaban-web-server-zh_CN.json",
                "azkaban.webapp.servlet.velocity.projectpageheader.vm");
            subPageMap2 = LoadJsonUtils.transJson("/com.webank.wedatasphere.schedulis.i18n.conf/azkaban-web-server-zh_CN.json",
                "azkaban.webapp.servlet.velocity.projectnav.vm");
            subPageMap3 = LoadJsonUtils.transJson("/com.webank.wedatasphere.schedulis.i18n.conf/azkaban-web-server-zh_CN.json",
                "azkaban.webapp.servlet.velocity.projectmodals.vm");
            subPageMap4 = LoadJsonUtils.transJson("/com.webank.wedatasphere.schedulis.i18n.conf/azkaban-web-server-zh_CN.json",
                "azkaban.webapp.servlet.velocity.flow-schedule-ecution-panel.vm");
            subPageMap5 = LoadJsonUtils.transJson("/com.webank.wedatasphere.schedulis.i18n.conf/azkaban-web-server-zh_CN.json",
                "azkaban.webapp.servlet.velocity.messagedialog.vm");
            subPageMap6 = LoadJsonUtils.transJson("/com.webank.wedatasphere.schedulis.i18n.conf/azkaban-web-server-zh_CN.json",
                "azkaban.webapp.servlet.velocity.nav.vm");
            subPageMap7 = LoadJsonUtils.transJson("/com.webank.wedatasphere.schedulis.i18n.conf/azkaban-web-server-zh_CN.json",
                "azkaban.webapp.servlet.velocity.projectsidebar.vm");
        } else {
            // 添加国际化标签
            projectpageMap = LoadJsonUtils.transJson("/com.webank.wedatasphere.schedulis.i18n.conf/azkaban-web-server-en_US.json",
                "azkaban.webapp.servlet.velocity.projectpage.vm");
            subPageMap1 = LoadJsonUtils.transJson("/com.webank.wedatasphere.schedulis.i18n.conf/azkaban-web-server-en_US.json",
                "azkaban.webapp.servlet.velocity.projectpageheader.vm");
            subPageMap2 = LoadJsonUtils.transJson("/com.webank.wedatasphere.schedulis.i18n.conf/azkaban-web-server-en_US.json",
                "azkaban.webapp.servlet.velocity.projectnav.vm");
            subPageMap3 = LoadJsonUtils.transJson("/com.webank.wedatasphere.schedulis.i18n.conf/azkaban-web-server-en_US.json",
                "azkaban.webapp.servlet.velocity.projectmodals.vm");
            subPageMap4 = LoadJsonUtils.transJson("/com.webank.wedatasphere.schedulis.i18n.conf/azkaban-web-server-en_US.json",
                "azkaban.webapp.servlet.velocity.flow-schedule-ecution-panel.vm");
            subPageMap5 = LoadJsonUtils.transJson("/com.webank.wedatasphere.schedulis.i18n.conf/azkaban-web-server-en_US.json",
                "azkaban.webapp.servlet.velocity.messagedialog.vm");
            subPageMap6 = LoadJsonUtils.transJson("/com.webank.wedatasphere.schedulis.i18n.conf/azkaban-web-server-en_US.json",
                "azkaban.webapp.servlet.velocity.nav.vm");
            subPageMap7 = LoadJsonUtils.transJson("/com.webank.wedatasphere.schedulis.i18n.conf/azkaban-web-server-en_US.json",
                "azkaban.webapp.servlet.velocity.projectsidebar.vm");
        }

        projectpageMap.forEach(page::add);
        subPageMap1.forEach(page::add);
        subPageMap2.forEach(page::add);
        subPageMap3.forEach(page::add);
        subPageMap4.forEach(page::add);
        subPageMap5.forEach(page::add);
        subPageMap6.forEach(page::add);
        subPageMap7.forEach(page::add);

        final String projectName = getParam(req, "project");

        final User user = session.getUser();

        page.add("loginUser", user.getUserId());

        page.add("currentlangType", languageType);

        PageUtils.hideUploadButtonWhenNeeded(page, session, this.lockdownUploadProjects);
        Project project = null;

        Map<String, String> dataMap = loadProjectManagerServletI18nData();
        try {
            project = this.projectManager.getProject(projectName);
            if (project == null) {
                page.add("errorMsg", StringEscapeUtils.escapeHtml(dataMap.get("project") + projectName + dataMap.get("notExist")));
            } else {
                if (!hasPermission(project, user, Type.READ)) {
                    throw new AccessControlException(dataMap.get("noPerAccessProject") + projectName + ".");
                }

                page.add("project", project);
                page.add("admins", Utils.flattenToString(
                    project.getUsersWithPermission(Type.ADMIN), ","));
                final Permission perm = this.getPermissionObject(project, user, Type.ADMIN);
                page.add("userpermission", perm);
                page.add(
                    "validatorFixPrompt",
                    this.projectManager.getProps().getBoolean(
                        ValidatorConfigs.VALIDATOR_AUTO_FIX_PROMPT_FLAG_PARAM,
                        ValidatorConfigs.DEFAULT_VALIDATOR_AUTO_FIX_PROMPT_FLAG));
                page.add(
                    "validatorFixLabel",
                    this.projectManager.getProps().get(
                        ValidatorConfigs.VALIDATOR_AUTO_FIX_PROMPT_LABEL_PARAM));
                page.add(
                    "validatorFixLink",
                    this.projectManager.getProps().get(
                        ValidatorConfigs.VALIDATOR_AUTO_FIX_PROMPT_LINK_PARAM));

                final boolean adminPerm = perm.isPermissionSet(Type.ADMIN);
                if (adminPerm) {
                    page.add("admin", true);
                }
                // Set this so we can display execute buttons only to those who have
                // access.
                if (perm.isPermissionSet(Type.EXECUTE) || adminPerm) {
                    page.add("exec", true);
                } else {
                    page.add("exec", false);
                }
                if (perm.isPermissionSet(Type.SCHEDULE) || adminPerm) {
                    page.add("schedulePerm", true);
                } else {
                    page.add("schedulePerm", false);
                }

                if (user.getRoles().contains("admin")) {
                    page.add("isSystemAdmin", true);
                }

                if (hasPermission(project, user, Type.ADMIN)) {
                    page.add("isProjectAdmin", true);
                }

                int uploadFlag;
                // 先判断开关是否打开,如果开关打开,则校验部门上传权限,如果关闭,则不需要校验
                // 判断是否具有上传权限  uploadFlag 1:允许, 2:不允许,其他值:不允许
                if (wtss_dep_upload_privilege_check) {
                    uploadFlag = checkDepartmentUploadFlag(user);
                } else {
                    uploadFlag = 1;
                }

                // 需要首先验证部门上传权限是否被允许, 再判断是否满足原有上传许可的逻辑
                if ((uploadFlag == 1) && (perm.isPermissionSet(Type.WRITE) || adminPerm)) {
                    page.add("isWritePerm", true);
                }

                final List<Flow> flows = project.getFlows().stream().filter(flow -> !flow.isEmbeddedFlow())
                    .collect(Collectors.toList());

                if (!flows.isEmpty()) {
                    //获取过滤出来的Flow子节点列表
                    List<String> flowName = getProjectFlowListFilter(project, flows);
                    //获取已经剔除子节点的Flow列表
                    final List<Flow> rootFlows = flows.stream().filter(flow ->
                        !flowName.contains(flow.getId())
                    ).collect(Collectors.toList());
                    //按照ID排序
                    Collections.sort(rootFlows, FLOW_ID_COMPARATOR);
                    page.add("flows", rootFlows);
                }
            }
        } catch (final AccessControlException e) {
            page.add("errorMsg", e.getMessage());
        } catch (SystemUserManagerException e) {
            logger.error("Error department upload flag.");
        }
        page.render();
    }

    /**
     * 校验部门上传权限
     * 判断是否具有上传权限  uploadFlag 1:允许, 2:不允许,其他值:不允许
     * @param user 当前用户, 注意: 该User类的userId映射表wtss_user中的username
     * @return
     */
    private int checkDepartmentUploadFlag(User user) throws SystemUserManagerException {

        int uploadFlag = 1;
        if (user != null) {
            try {
                String userName = user.getUserId();
                WtssUser systemUser = this.transitionService.getSystemUserByUserName(userName);
                if (null != systemUser) {
                    Integer departmentId = Integer.valueOf(systemUser.getDepartmentId() + "");
                    WebankDepartment department = this.transitionService.getWebankDepartmentByDpId(departmentId);
                    if (null != department) {
                        uploadFlag = department.getUploadFlag();
                    }
                } else {
                    uploadFlag = 2;
                    logger.error("系统用户信息不存在");
                }
            } catch (SystemUserManagerException e) {
                uploadFlag = 2;
                logger.error("查询用户信息失败,失败原因:{}", e);
            }
        }
        // 对uploadFlag进行校验,过滤无效值  uploadFlag 1:允许, 2:不允许,其他值:不允许
        if (uploadFlag == 1 || uploadFlag == 2) {
            return uploadFlag;
        }else {
            throw new SystemUserManagerException("Error department upload flag.");
        }
    }

    private void handleCreate(final HttpServletRequest req, final HttpServletResponse resp,
        final Session session) throws ServletException {
        resp.setCharacterEncoding("utf-8");
        final String projectName = hasParam(req, "name") ? getParam(req, "name") : null;
        final String projectDescription =
            hasParam(req, "description") ? getParam(req, "description") : null;
        logger.info("Create project " + projectName);
        final String projectGroup =
            hasParam(req, "projectGroup") ? getParam(req, "projectGroup") : null;

        final User user = session.getUser();

        String status = null;
        String action = null;
        String message = null;
        HashMap<String, Object> params = null;

        if (this.lockdownCreateProjects && !UserUtils.hasPermissionforAction(user, Type.CREATEPROJECTS)) {
            message =
                "User " + user.getUserId()
                    + " doesn't have permission to create projects.";
            logger.info(message);
            status = "error";
        } else {
            try {
                //this.projectManager.createProject(projectName, projectDescription, user);
                //增加项目组设置
                this.projectManager.createProject(projectName, projectDescription, projectGroup, user);
                status = "success";
                action = "redirect";
                final String redirect = "manager?project=" + projectName;
                params = new HashMap<>();
                params.put("path", redirect);
            } catch (final ProjectManagerException e) {
                message = e.getMessage();
                status = "error";
            }
        }
        final String response = createJsonResponse(status, message, action, params);
        try {
            final Writer write = resp.getWriter();
            write.append(response);
            write.flush();
        } catch (final IOException e) {
            e.printStackTrace();
        }
    }

    private void registerError(final Map<String, String> ret, final String error,
        final HttpServletResponse resp, final int returnCode) {
        ret.put("error", error);
        resp.setStatus(returnCode);
    }

    private void ajaxHandleUpload(final HttpServletRequest req, final HttpServletResponse resp,
        final Map<String, String> ret, final Map<String, Object> multipart, final Session session)
        throws ServletException, IOException {
        final User user = session.getUser();
        final String projectName = (String) multipart.get("project");
        final Project project = this.projectManager.getProject(projectName);
        logger.info(
            "Upload: reference of project " + projectName + " is " + System.identityHashCode(project));

        final String autoFix = (String) multipart.get("fix");

        final Props props = new Props();
        if (autoFix != null && autoFix.equals("off")) {
            props.put(ValidatorConfigs.CUSTOM_AUTO_FIX_FLAG_PARAM, "false");
        } else {
            props.put(ValidatorConfigs.CUSTOM_AUTO_FIX_FLAG_PARAM, "true");
        }

        Map<String, String> dataMap = loadProjectManagerServletI18nData();

        if (this.lockdownUploadProjects && !UserUtils.hasPermissionforAction(user, Type.UPLOADPROJECTS)) {
            final String message =
                "Project uploading is locked out. Only admin users and users with special permissions can upload projects. "
                    + "User " + user.getUserId() + " doesn't have permission to upload project.";
            logger.info(message);
            registerError(ret, message, resp, 403);
        } else if (projectName == null || projectName.isEmpty()) {
            registerError(ret, dataMap.get("noProgramName"), resp, 400);
        } else if (project == null) {
            registerError(ret, dataMap.get("uploadProFailed") + projectName
                + dataMap.get("notExist"), resp, 400);
        } else if (!hasPermission(project, user, Type.WRITE)) {
            registerError(ret, dataMap.get("uploadProFailedUser") + user.getUserId()
                + dataMap.get("noWritePermission"), resp, 400);
        } else if (!project.isActive()) {
            registerError(ret, dataMap.get("deletedPro"), resp, 400);
        } else {
            ret.put("projectId", String.valueOf(project.getId()));

            final FileItem item = (FileItem) multipart.get("file");
            final String name = item.getName();
            // 判断zip包是否包含空格
            if (Pattern.compile(RE_SPACE).matcher(name).find()) {
                item.delete();
                registerError(ret, dataMap.get("zipFileCannotHaveBlank"), resp, 400);
                return;
            }

            //判断name长度是否大于128
            if (name.length() > 128) {
                registerError(ret, dataMap.get("zipFileCannotlength"), resp, 400);
                return;
            }

            String type = null;

            final String contentType = item.getContentType();
            if (contentType != null
                && (contentType.startsWith(APPLICATION_ZIP_MIME_TYPE)
                || contentType.startsWith("application/x-zip-compressed") || contentType
                .startsWith("application/octet-stream"))) {
                type = "zip";
            } else {
                item.delete();
                registerError(ret, "File type " + contentType + " unrecognized.", resp, 400);

                return;
            }

            final File tempDir = Utils.createTempDir();
            OutputStream out = null;
            try {
                logger.info("Uploading file " + name);
                final File archiveFile = new File(tempDir, name);
                out = new BufferedOutputStream(new FileOutputStream(archiveFile));
                IOUtils.copy(item.getInputStream(), out);
                out.close();

                //unscheduleall/scheduleall should only work with flow which has defined flow trigger
                //unschedule all flows within the old project
                if (this.enableQuartz) {
                    //todo chengren311: should maintain atomicity,
                    // e.g, if uploadProject fails, associated schedule shouldn't be added.
                    this.scheduler.unscheduleAll(project);
                }
                boolean flag = this.projectManager.checkFlowName(project, archiveFile, type, props);
                //判断name长度是否大于128
                if (!flag) {
                    registerError(ret, dataMap.get("jobNamelength"), resp, 400);
                    return;
                }

                final Map<String, ValidationReport> reports =
                    this.projectManager.uploadProject(project, archiveFile, type, user,
                        props);

                if (this.enableQuartz) {
                    //schedule the new project
                    this.scheduler.scheduleAll(project, user.getUserId());
                }
                final StringBuffer errorMsgs = new StringBuffer();
                final StringBuffer warnMsgs = new StringBuffer();
                for (final Entry<String, ValidationReport> reportEntry : reports.entrySet()) {
                    final ValidationReport report = reportEntry.getValue();
                    if (!report.getInfoMsgs().isEmpty()) {
                        for (final String msg : report.getInfoMsgs()) {
                            switch (ValidationReport.getInfoMsgLevel(msg)) {
                                case ERROR:
                                    errorMsgs.append(ValidationReport.getInfoMsg(msg) + "<br/>");
                                    break;
                                case WARN:
                                    warnMsgs.append(ValidationReport.getInfoMsg(msg) + "<br/>");
                                    break;
                                default:
                                    break;
                            }
                        }
                    }
                    if (!report.getErrorMsgs().isEmpty()) {
                        errorMsgs.append("Validator " + reportEntry.getKey()
                            + " reports errors:<ul>");
                        for (final String msg : report.getErrorMsgs()) {
                            errorMsgs.append("<li>" + msg + "</li>");
                        }
                        errorMsgs.append("</ul>");
                    }
                    if (!report.getWarningMsgs().isEmpty()) {
                        warnMsgs.append("Validator " + reportEntry.getKey()
                            + " reports warnings:<ul>");
                        for (final String msg : report.getWarningMsgs()) {
                            warnMsgs.append("<li>" + msg + "</li>");
                        }
                        warnMsgs.append("</ul>");
                    }
                }
                if (errorMsgs.length() > 0) {
                    // If putting more than 4000 characters in the cookie, the entire
                    // message
                    // will somehow get discarded.
                    registerError(ret, errorMsgs.length() > 4000 ? errorMsgs.substring(0, 4000)
                        : errorMsgs.toString(), resp, 500);
                    //使用cookie提示错误页面才能显示错误
                    //setErrorMessageInCookie(resp, errorMsgs.toString());
                }
                if (warnMsgs.length() > 0) {
                    ret.put(
                        "warn",
                        warnMsgs.length() > 4000 ? warnMsgs.substring(0, 4000) : warnMsgs
                            .toString());
                    //使用cookie提示错误页面才能显示错误
                    //setWarnMessageInCookie(resp, warnMsgs.toString());
                }
            } catch (final Exception e) {
                logger.info("Installation Failed.", e);
                String error = e.getMessage();
                if(error != null && error.equals("MALFORMED")){
                    error = "Decompressing files failed, please check if there are Chinese characters in the file name.";
                }
                if (error.length() > 512) {
                    error =
                        error.substring(0, 512) + "<br>Too many errors to display.<br>";
                }
                registerError(ret, dataMap.get("uploadFailed") + "<br>" + error, resp, 500);
                //使用cookie提示错误页面才能显示错误
                //setErrorMessageInCookie(resp, error);
            } finally {
                if (out != null) {
                    out.close();
                }
                if (tempDir.exists()) {
                    FileUtils.deleteDirectory(tempDir);
                }
            }

            logger.info("Upload: project " + projectName + " version is " + project.getVersion()
                + ", reference is " + System.identityHashCode(project));
            ret.put("version", String.valueOf(project.getVersion()));
        }
    }

    private void handleUpload(final HttpServletRequest req, final HttpServletResponse resp,
        final Map<String, Object> multipart, final Session session) throws ServletException,
        IOException {
        final HashMap<String, String> ret = new HashMap<>();
        final String projectName = (String) multipart.get("project");
        ajaxHandleUpload(req, resp, ret, multipart, session);

        if (ret.containsKey("error")) {
            setErrorMessageInCookie(resp, ret.get("error"));
        }

        if (ret.containsKey("warn")) {
            setWarnMessageInCookie(resp, ret.get("warn"));
        }

        logger.info("Upload project, Redirect to ---> " + req.getRequestURI() + "?project=" + projectName);
        resp.sendRedirect(req.getRequestURI() + "?project=" + projectName);
    }

    private Permission getPermissionObject(final Project project, final User user,
        final Type type) {
        final Permission perm = project.getCollectivePermission(user);
        for (final String roleName : user.getRoles()) {
            if (roleName.equals("admin") || systemManager.isDepartmentMaintainer(user)) {
                perm.addPermission(Type.ADMIN);
            }
        }

        return perm;
    }

    private void handleReloadProjectWhitelist(final HttpServletRequest req,
        final HttpServletResponse resp, final Session session) throws IOException {
        final HashMap<String, Object> ret = new HashMap<>();

        if (hasPermission(session.getUser(), Type.ADMIN)) {
            try {
                if (this.projectManager.loadProjectWhiteList()) {
                    ret.put("success", "Project whitelist re-loaded!");
                } else {
                    ret.put("error", "azkaban.properties doesn't contain property "
                        + ProjectWhitelist.XML_FILE_PARAM);
                }
            } catch (final Exception e) {
                ret.put("error",
                    "Exception occurred while trying to re-load project whitelist: "
                        + e);
            }
        } else {
            ret.put("error", "Provided session doesn't have admin privilege.");
        }

        this.writeJSON(resp, ret);
    }

    protected boolean hasPermission(final User user, final Type type) {
        for (final String roleName : user.getRoles()) {
            //final Role role = this.userManager.getRole(roleName);
            final Role role = user.getRoleMap().get(roleName);
            if (role != null && role.getPermission().isPermissionSet(type)
                || role.getPermission().isPermissionSet(Type.ADMIN)) {
                return true;
            }
        }

        return false;
    }

    private static class NodeLevelComparator implements Comparator<Node> {

        @Override
        public int compare(final Node node1, final Node node2) {
            return node1.getLevel() - node2.getLevel();
        }
    }

    public static class PageSelection {

        private final String page;
        private final int size;
        private final boolean disabled;
        private final int nextPage;
        private boolean selected;

        public PageSelection(final String pageName, final int size, final boolean disabled,
            final boolean selected, final int nextPage) {
            this.page = pageName;
            this.size = size;
            this.disabled = disabled;
            this.setSelected(selected);
            this.nextPage = nextPage;
        }

        public String getPage() {
            return this.page;
        }

        public int getSize() {
            return this.size;
        }

        public boolean getDisabled() {
            return this.disabled;
        }

        public boolean isSelected() {
            return this.selected;
        }

        public void setSelected(final boolean selected) {
            this.selected = selected;
        }

        public int getNextPage() {
            return this.nextPage;
        }
    }

    //组装出项目中Flow的树形结构数据
    private void getProjectNodeTree(final Project project, final String flowId,
        final HashMap<String, Object> ret) {
        final Flow flow = project.getFlow(flowId);

        final ArrayList<Map<String, Object>> nodeList =
            new ArrayList<>();
        for (final Node node : flow.getNodes()) {
            final HashMap<String, Object> nodeObj = new HashMap<>();
            nodeObj.put("id", node.getId());
            nodeObj.put("type", node.getType());
            nodeObj.put("projectName", project.getName());
            nodeObj.put("level", node.getLevel());
            if (node.getEmbeddedFlowId() != null) {
                nodeObj.put("flowId", node.getEmbeddedFlowId());
                getProjectNodeTree(project, node.getEmbeddedFlowId(), nodeObj);
            }

            final ArrayList<String> dependencies = new ArrayList<>();
            Collection<Edge> collection = flow.getInEdges(node.getId());
            if (collection != null) {
                for (final Edge edge : collection) {
                    dependencies.add(edge.getSourceId());
                }
            }

            final ArrayList<String> dependents = new ArrayList<>();
            collection = flow.getOutEdges(node.getId());
            if (collection != null) {
                for (final Edge edge : collection) {
                    dependents.add(edge.getTargetId());
                }
            }

            nodeObj.put("dependencies", dependencies);
            nodeObj.put("dependents", dependents);

            nodeList.add(nodeObj);

            final Set<Edge> inEdges = flow.getInEdges(node.getId());
            if (inEdges != null && !inEdges.isEmpty()) {
                final ArrayList<String> inEdgesList = new ArrayList<>();
                for (final Edge edge : inEdges) {
                    inEdgesList.add(edge.getSourceId());
                }
                Collections.sort(inEdgesList);
                nodeObj.put("in", inEdgesList);
            }

        }

        Collections.sort(nodeList, new Comparator<Map<String, Object>>() {
            @Override
            public int compare(final Map<String, Object> o1, final Map<String, Object> o2) {
                final String id = (String) o1.get("id");
                return id.compareTo((String) o2.get("id"));
            }
        });

        ret.put("flow", flowId);
        ret.put("nodes", nodeList);
    }

    //获取项目Flow的树型结构 剔除子叶上的Flow 只保留总的Flow根
    private List<String> getProjectFlowListFilter(final Project project, final List<Flow> flows) {
        final List<Map<String, Object>> childNodeList = new ArrayList<>();

        for (Flow flow : flows) {
            getProjectChildNode(project, flow.getId(), childNodeList);
        }

        List<String> flowNameList = new ArrayList<>();
        for (Map<String, Object> nodeMap : childNodeList) {
            flowNameList.add(String.valueOf(nodeMap.get("flowId")));
        }

        return flowNameList;
    }

    private void getProjectChildNode(final Project project, final String flowId,
        final List<Map<String, Object>> childTreeList) {
        final Flow flow = project.getFlow(flowId);

        final ArrayList<Map<String, Object>> nodeList =
            new ArrayList<>();
        for (final Node node : flow.getNodes()) {
            final Map<String, Object> nodeObj = new HashMap<>();

            if (node.getEmbeddedFlowId() != null) {
                nodeObj.put("flowId", node.getEmbeddedFlowId());
                getProjectChildNode(project, node.getEmbeddedFlowId(), childTreeList);
                childTreeList.add(nodeObj);
            }
        }
    }

    private void ajaxFetchJobExecutionsHistory(final Project project,
        final HashMap<String, Object> ret, final HttpServletRequest req)
        throws ServletException {
        final String projectName = getParam(req, "project");
        final String flowId = getParam(req, "flow");
        final String jobId = getParam(req, "job");
        final int from = Integer.valueOf(getParam(req, "start"));
        final int length = Integer.valueOf(getParam(req, "length"));

        final List<ExecutableJobInfo> jobInfoList = new ArrayList<>();
        int total = 0;
        try {
//      total = this.executorManager.getExecutableFlows(project.getId(), flowId, from,
//              length, exFlows);
            total = this.executorManagerAdapter.getNumberOfJobExecutions(project, jobId);
            final List<ExecutableJobInfo> jobExecList = this.executorManagerAdapter.getExecutableJobs(project, jobId, from, length);
            if (null != jobExecList && !jobExecList.isEmpty()) {
                jobInfoList.addAll(jobExecList);

                if (jobInfoList != null) {
                    jobInfoList.stream().forEach(executableJobInfo -> {

                        ExecutableFlow executionFlow = null;
                        try {
                            executionFlow = this.executorManagerAdapter.getExecutableFlow(executableJobInfo.getExecId());
                        } catch (ExecutorManagerException e) {
                            e.printStackTrace();
                        }

                        Map<String, String> repeatMap = executionFlow.getRepeatOption();
                        if (!repeatMap.isEmpty()) {

                            Long recoverRunDate = Long.valueOf(String.valueOf(repeatMap.get("startTimeLong")));

                            LocalDateTime localDateTime = new LocalDateTime(new Date(recoverRunDate)).minusDays(1);

                            Date date = localDateTime.toDate();

                            executableJobInfo.setRunDate(date.getTime());
                        } else {
                            Long runDate = executionFlow.getStartTime();
                            if (-1 != runDate) {
                                LocalDateTime localDateTime = new LocalDateTime(new Date(runDate)).minusDays(1);

                                Date date = localDateTime.toDate();

                                executableJobInfo.setRunDate(date.getTime());
                            } else {
                                executableJobInfo.setRunDate(runDate);
                            }
                        }
                    });
                }
            }
        } catch (final ExecutorManagerException e) {
            ret.put("error", "Error retrieving executable flows");
        }

        ret.put("flow", flowId);
        ret.put("total", total);
        ret.put("from", from);
        ret.put("length", length);

        final ArrayList<Object> history = new ArrayList<>();
        for (final ExecutableJobInfo job : jobInfoList) {
            final HashMap<String, Object> flowInfo = new HashMap<>();
            flowInfo.put("execId", job.getExecId());
            flowInfo.put("jobId", job.getJobId());
            flowInfo.put("flowId", job.getFlowId());
            flowInfo.put("projectId", job.getProjectId());
            flowInfo.put("status", job.getStatus().toString());
            flowInfo.put("startTime", job.getStartTime());
            flowInfo.put("endTime", job.getEndTime());
            flowInfo.put("runDate", job.getRunDate());
            history.add(flowInfo);
        }

        ret.put("jobExecutions", history);

    }

    //项目页面点击Flow类型job节点的详细页面
    private void handleFlowDetailPage(final HttpServletRequest req, final HttpServletResponse resp,
        final Session session) throws ServletException {
        final Page page = newPage(req, resp, session, "azkaban/webapp/servlet/velocity/flowpage.vm");

        // 加载国际化资源
        Map<String, Map<String, String>> vmDataMap = loadFlowpageI18nData();
        vmDataMap.forEach((vm, data) -> data.forEach(page::add));

        String languageType = LoadJsonUtils.getLanguageType();

        page.add("currentlangType", languageType);

        final String projectName = getParam(req, "project");
        final String treeFlow = getParam(req, "treeFlow");
        final String jobName = getParam(req, "job");
        final String realFlow = getParam(req, "flow");

        final User user = session.getUser();
        Project project = null;
        Flow jobFlow = null;
        Flow flow = null;

        Map<String, String> dataMap = loadProjectManagerServletI18nData();
        try {
            //获取真实的Flow的详细信息
            project = this.projectManager.getProject(projectName);
            if (project == null) {
                page.add("errorMsg", "项目 " + projectName + " 不存在.");
                page.render();
                return;
            }

            if (!hasPermission(project, user, Type.READ)) {
                throw new AccessControlException("No permission Project " + projectName
                    + ".");
            }

            final Permission perm = this.getPermissionObject(project, user, Type.ADMIN);

            final boolean adminPerm = perm.isPermissionSet(Type.ADMIN);

            if (perm.isPermissionSet(Type.EXECUTE) || adminPerm) {
                page.add("execPerm", true);
            } else {
                page.add("execPerm", false);
            }
            if (perm.isPermissionSet(Type.SCHEDULE) || adminPerm) {
                page.add("schedulePerm", true);
            } else {
                page.add("schedulePerm", false);
            }

            page.add("project", project);
            flow = project.getFlow(realFlow);
            if (flow == null) {
                page.add("errorMsg", dataMap.get("flow") + realFlow + dataMap.get("notExist"));
            } else {
                page.add("flowid", flow.getId());
            }


            //获取Flow类型job节点的属性信息
            jobFlow = project.getFlow(treeFlow);
            final Node node = jobFlow.getNode(jobName);
            if (node == null) {
                page.add("errorMsg", dataMap.get("job") + jobName + dataMap.get("notExist"));
                page.render();
                return;
            }

            final Props prop = this.projectManager.getProperties(project, jobFlow, jobName, node.getJobSource());
            Props overrideProp =
                this.projectManager.getJobOverrideProperty(project, jobFlow, jobName, jobName);
            if (overrideProp == null) {
                overrideProp = new Props();
            }
            final Props comboProp = new Props(prop);
            for (final String key : overrideProp.getKeySet()) {
                comboProp.put(key, overrideProp.get(key));
            }
            page.add("jobid", node.getId());
            page.add("jobtype", node.getType());

            final ArrayList<Pair<String, String>> parameters =
                new ArrayList<>();
            // Parameter
            for (final String key : comboProp.getKeySet()) {
                final String value = comboProp.get(key);
                parameters.add(new Pair<>(key, value));
            }

            //前端排序优化 Job Properties 根据属性名排序
            final List<Pair<String, String>> sortedParameters = parameters.stream().sorted(Comparator.comparing(Pair::getFirst)).collect(
                Collectors.toList());

            final List<Pair<String, String>> finalParams = new ArrayList<>();
            //data.object 跟 source.type 组合排序
            sortedParameters.stream().forEach(m -> {
                    String dnum;
                    String dkey = m.getFirst();
                    if (dkey.contains("data.object")) {
                        dnum = StringUtils.substringAfter(dkey, "data.object");
                        finalParams.add(m);
                        String snum;
                        for (Pair<String, String> job : sortedParameters) {
                            String skey = job.getFirst();
                            if (skey.contains("source.type")) {
                                snum = StringUtils.substringAfter(skey, "source.type");
                                if (dnum.equals(snum)) {
                                    finalParams.add(job);
                                }
                            }
                        }
                    } else if (!dkey.contains("data.object") && !dkey.contains("source.type")) {
                        finalParams.add(m);
                    }
                }
            );

            page.add("parameters", finalParams);

        } catch (final AccessControlException e) {
            page.add("errorMsg", e.getMessage());
        }


        page.render();
    }

    private void ajaxFetchFlowExecutions(final Project project,
        final HashMap<String, Object> ret, final HttpServletRequest req, final User user)
        throws ServletException {
        final String flowId = getParam(req, "flow");
        final int from = Integer.valueOf(getParam(req, "start"));
        final int length = Integer.valueOf(getParam(req, "length"));

        final ArrayList<ExecutableFlow> exFlows = new ArrayList<>();
        int total = 0;
//    long moyenne = 0;
        try {
            if (user.getRoles().contains("admin")) {
                total =
                    this.executorManagerAdapter.getExecutableFlows(project.getId()
                        , flowId, from, length, exFlows);

//        moyenne = this.executorManagerAdapter.getExecutableFlowsMoyenneRunTime(
//            project.getId(), flowId, null);

            } else {
                if (hasPermission(project, user, Type.READ)) {
                    total =
                        this.executorManagerAdapter.getExecutableFlows(project.getId()
                            , flowId, from, length, exFlows);
                } else {
                    total =
                        this.executorManagerAdapter.getUserExecutableFlowsTotalByProjectIdAndFlowId(project.getId()
                            , flowId, from, length, exFlows, user.getUserId());
                }

//        moyenne = this.executorManagerAdapter.getExecutableFlowsMoyenneRunTime(
//            project.getId(), flowId, null);
            }

        } catch (final ExecutorManagerException e) {
            ret.put("error", "Error retrieving executable flows");
        }

        ret.put("flow", flowId);
        ret.put("total", total);
        ret.put("from", from);
        ret.put("length", length);

        final List<Object> execFlowList = new ArrayList<>();


//    long allRunTime = 0;
//    int successFlowNum = 0;
//    for (final ExecutableFlow flow : exFlows) {
//      if(Status.SUCCEEDED.equals(flow.getStatus())){
//        successFlowNum += 1;
//        allRunTime += (flow.getEndTime() - flow.getStartTime());
//      }
//    }
//    if(allRunTime !=0 && successFlowNum !=0){
//      moyenne = allRunTime/successFlowNum;
//    }


        for (final ExecutableFlow flow : exFlows) {
            final HashMap<String, Object> flowInfo = new HashMap<>();
            flowInfo.put("execId", flow.getExecutionId());
            flowInfo.put("flowId", flow.getFlowId());
            flowInfo.put("projectId", flow.getProjectId());
            flowInfo.put("status", flow.getStatus().toString());
            flowInfo.put("submitTime", flow.getSubmitTime());
            flowInfo.put("startTime", flow.getStartTime());
            flowInfo.put("endTime", flow.getEndTime());
            flowInfo.put("submitUser", flow.getSubmitUser());
            flowInfo.put(ExecutableFlow.COMMENT_PARAM, flow.getComment());
            Map<String, String> repeatMap = flow.getRepeatOption();
            if(flow.getRunDate() != null){
                logger.info("run_date: " + flow.getRunDate());
                flowInfo.put("runDate", flow.getRunDate());
            } else if (!repeatMap.isEmpty()) {
                Long recoverRunDate = Long.valueOf(String.valueOf(repeatMap.get("startTimeLong")));
                LocalDateTime localDateTime = new LocalDateTime(new Date(recoverRunDate)).minusDays(1);
                flowInfo.put("runDate", localDateTime.toString("yyyyMMdd"));
            } else {
                Long runDate = flow.getStartTime();
                LocalDateTime localDateTime = new LocalDateTime(new Date(runDate)).minusDays(1);
                flowInfo.put("runDate", localDateTime.toString("yyyyMMdd"));
            }
//      flowInfo.put("moyenne", 0);
            execFlowList.add(flowInfo);
        }

        ret.put("executions", execFlowList);
    }

    private void ajaxAddProjectUserPermission(final Project project, final HashMap<String, Object> ret,
        final HttpServletRequest req, final User user) throws ServletException {
        final String userId = getParam(req, "userId");

        WtssUser wtssUser = null;
        Map<String, String> dataMap = loadProjectManagerServletI18nData();

        //final boolean admin = Boolean.parseBoolean(getParam(req, "permissions[admin]"));
        final boolean read = Boolean.parseBoolean(getParam(req, "permissions[read]"));
        final boolean write = Boolean.parseBoolean(getParam(req, "permissions[write]"));
        final boolean execute = Boolean.parseBoolean(getParam(req, "permissions[execute]"));
        final boolean schedule = Boolean.parseBoolean(getParam(req, "permissions[schedule]"));

        try {

            wtssUser = this.transitionService.getSystemUserById(userId);

            String createUser = project.getCreateUser();
            // 运维用户创建的项目,不能分配给实名用户和系统用户调度和修改项目的权限
            if (wtssUser != null) {
                if (createUser.startsWith("WTSS")) {
                    WtssUser currentAddUser = this.transitionService.getSystemUserByUserName(wtssUser.getUsername());
                    // 判断用户角色  roleId 1:管理员, 2:普通用户
                    if (currentAddUser.getRoleId() != 1) {
                        if (!userId.startsWith("wtss_WTSS")) {
                            // 只对非管理员进行校验
                            int roleId = wtssUser.getRoleId();
                            if (roleId != 1) {
                                if (Pattern.compile("^[0-9]+$").matcher(userId).matches()) {
                                    if (write || execute || schedule) {
                                        ret.put("error", dataMap.get("cannotpermitrealnamewriteexecsch"));
                                    } else {
                                        // 执行添加
                                        executeAddProjectUser(wtssUser, project, dataMap, ret, user, read, write, execute, schedule);
                                    }
                                } else if (userId.startsWith("wtss_hduser")) {
                                    if (write || execute || schedule) {
                                        ret.put("error", dataMap.get("cannotpermitsystemUserwriteexecsch"));
                                    } else {
                                        // 执行添加
                                        executeAddProjectUser(wtssUser, project, dataMap, ret, user, read, write, execute, schedule);
                                    }
                                } else {
                                    ret.put("error", dataMap.get("canNotAddUnknown"));
                                }
                            } else {
                                // 执行添加
                                executeAddProjectUser(wtssUser, project, dataMap, ret, user, read, write, execute, schedule);
                            }

                        } else {
                            // 执行添加
                            executeAddProjectUser(wtssUser, project, dataMap, ret, user, read, write, execute, schedule);
                        }
                    } else {
                        // 执行添加
                        executeAddProjectUser(wtssUser, project, dataMap, ret, user, read, write, execute, schedule);
                    }


                } else {
                    // 执行添加
                    executeAddProjectUser(wtssUser, project, dataMap, ret, user, read, write, execute, schedule);
                }
            } else {
                ret.put("error", dataMap.get("nullUser"));
            }


        } catch (SystemUserManagerException e) {
            ret.put("error", "Request Failed");
        }
    }

    /**
     * 执行添加项目用户
     * @param wtssUser
     * @param project
     * @param dataMap
     * @param ret
     * @param user
     * @param read
     * @param write
     * @param execute
     * @param schedule
     */
    public void executeAddProjectUser(WtssUser wtssUser, Project project, Map<String, String> dataMap,
        HashMap<String, Object> ret, User user, boolean read, boolean write, boolean execute,boolean schedule) {

        final String name = wtssUser.getUsername();

        if (project.getUserPermission(name) != null) {
            ret.put("error", dataMap.get("userPerExist"));
            return;
        }

        final Permission perm = new Permission();

        perm.setPermission(Type.READ, read);
        perm.setPermission(Type.WRITE, write);
        perm.setPermission(Type.EXECUTE, execute);
        perm.setPermission(Type.SCHEDULE, schedule);

        final boolean group = false;
        try {
            this.projectManager.updateProjectPermission(project, name, perm, group, user);
        } catch (final ProjectManagerException e) {
            ret.put("error", "Request Failed.");
        }
    }

    private void ajaxGetUserProjectPerm(final Project project, final HashMap<String, Object> ret,
        final HttpServletRequest req, final User user) {

        try {
            String projectUser = getParam(req, "userId");

            for (final Pair<String, Permission> perm : project.getUserPermissions()) {

                final String userId = perm.getFirst();
                if (userId.equals(projectUser)) {
                    ret.put("adminPerm", perm.getSecond().isPermissionNameSet("ADMIN"));
                    ret.put("readPerm", perm.getSecond().isPermissionNameSet("READ"));
                    ret.put("writePerm", perm.getSecond().isPermissionNameSet("WRITE"));
                    ret.put("executePerm", perm.getSecond().isPermissionNameSet("EXECUTE"));
                    ret.put("schedulePerm", perm.getSecond().isPermissionNameSet("SCHEDULE"));
                }
            }
        } catch (ServletException e) {
            ret.put("error", "Request Failed.");
        }
    }

    private void ajaxAddProjectAdmin(final Project project, final HashMap<String, Object> ret,
        final HttpServletRequest req, final User user) throws ServletException {
        final String userId = getParam(req, "userId");

        WtssUser wtssUser = null;
        Map<String, String> dataMap = loadProjectManagerServletI18nData();
        try {
            String createUser = project.getCreateUser();
            wtssUser = this.transitionService.getSystemUserById(userId);
            if (wtssUser != null) {
                if (createUser.startsWith("WTSS")) {
                    WtssUser currentToUser = this.transitionService.getSystemUserByUserName(wtssUser.getUsername());
                    // 判断用户角色  roleId 1:管理员, 2:普通用户
                    if (currentToUser.getRoleId() != 1) {
                        if (!userId.startsWith("wtss_WTSS")) {
                            int roleId = wtssUser.getRoleId();
                            if (roleId != 1) {
                                if (Pattern.compile("^[0-9]+$").matcher(userId).matches()) {
                                    ret.put("error", dataMap.get("canNotAddRealNameUser"));
                                } else if (userId.startsWith("wtss_hduser")) {
                                    ret.put("error", dataMap.get("canNotAddHduUser"));
                                } else {
                                    ret.put("error", dataMap.get("canNotAddNotOps"));
                                }
                            } else {
                                executeAddProjectAdmin(wtssUser, project, ret, dataMap, user);
                            }

                        } else {
                            executeAddProjectAdmin(wtssUser, project, ret, dataMap, user);
                        }
                    } else {
                        executeAddProjectAdmin(wtssUser, project, ret, dataMap, user);
                    }
                } else {
                    executeAddProjectAdmin(wtssUser,project,ret,dataMap,user);
                }
            } else {
                ret.put("error", dataMap.get("nullUser"));
            }

        } catch (SystemUserManagerException e) {
            ret.put("error", "Request Failed.");
        }
    }

    /**
     * 执行添加管理员
     * @param wtssUser
     * @param project
     * @param ret
     * @param dataMap
     */
    public void executeAddProjectAdmin(WtssUser wtssUser, Project project, HashMap<String, Object> ret, Map<String, String> dataMap, User user) {
        final String name = wtssUser.getUsername();

        if (project.getUserPermission(name) != null) {
            ret.put("error", dataMap.get("userPerExist"));
            return;
        }

        final Permission perm = new Permission();
        perm.setPermission(Type.ADMIN, true);
        perm.setPermission(Type.READ, true);
        perm.setPermission(Type.WRITE, true);
        perm.setPermission(Type.EXECUTE, true);
        perm.setPermission(Type.SCHEDULE, true);

        final boolean group = false;
        try {
            this.projectManager.updateProjectPermission(project, name, perm, group, user);
        } catch (final ProjectManagerException e) {
            ret.put("error", "Request Failed");
        }
    }

    /**
     * 移除项目管理权限
     *
     * @param project
     * @param ret
     * @param req
     * @param user
     * @throws ServletException
     */
    private void ajaxRemoveProjectAdmin(final Project project,
        final HashMap<String, Object> ret, final HttpServletRequest req, final User user)
        throws ServletException {

        final String userId = getParam(req, "userId");
        final Permission perm;

        perm = project.getUserPermission(userId);

        if (perm == null) {
            ret.put("error", "User[" + userId + " have no permission of this project. ");
            return;
        }

        try {
            this.projectManager.removeProjectPermission(project, userId, user);
        } catch (final ProjectManagerException e) {
            ret.put("error", "Request Failed");
        }

    }

    private List<String> getAllNodes(ExecutableFlowBase baseNode, List<String> allNodes){
        for(ExecutableNode node: baseNode.getExecutableNodes()){
            if(node instanceof ExecutableFlowBase){
                getAllNodes((ExecutableFlowBase)node, allNodes);
            }
            if(!(node instanceof ExecutableFlowBase)){
                allNodes.add(node.getNestedId());
            }
        }
        return allNodes;
    }

    /**
     * return jobNestedId
     *
     * @param project
     * @param ret
     * @param req
     * @throws ServletException
     */
    private void ajaxFetchJobNestedIdList(final Project project, final HashMap<String, Object> ret,
        final HttpServletRequest req) throws ServletException {
        final String flowId = getParam(req, "flow");
        final Flow flow = project.getFlow(flowId);
        if (flow == null) {
            logger.error(flowId + " is not exist");
            return;
        }
        String searchName = null;
        if (hasParam(req, "serach")) {
            searchName = getParam(req, "serach");
        }
        List<String> jobList = new ArrayList<>();
        ExecutableFlow executableFlow = FlowUtils.createExecutableFlow(project, flow);
        getAllNodes(executableFlow, jobList);
        List<String> filterList = jobList;
        if (StringUtils.isNotBlank(searchName)) {
            final String flag = searchName;
            filterList = jobList.stream().filter(x -> x.contains(flag)).collect(Collectors.toList());
        }
        List<Map<String, String>> jobSelectDataList = new ArrayList<>();
        for (String name : filterList) {
            Map<String, String> selectMap = new HashMap<>();
            selectMap.put("id", name);
            selectMap.put("text", name);
            jobSelectDataList.add(selectMap);
        }
        ret.put("jobList", jobSelectDataList);
    }

    private void ajaxFetchFlowRealJobList(final Project project, final HashMap<String, Object> ret,
        final HttpServletRequest req) throws ServletException {
        final String flowId = getParam(req, "flow");
        //final Flow flow = project.getFlow(flowId);
        final Flow flow = project.getFlow(flowId);
        if (flow == null) {
            logger.error(flowId + " is not exist");
            return;
        }
        String searchName = req.getParameter("serach");

        List<String> jobList = new ArrayList<>();
        jobList.add("all_jobs" + " " + flow.getId());
        getFlowRealJobTree(project, flowId, jobList);
        if (null != searchName && !searchName.isEmpty()) {
            List<String> filterList = new ArrayList<>();
            for (String jobName : jobList) {
                if (jobName.contains(searchName)) {
                    filterList.add(jobName);
                }
            }
            jobList.clear();
            jobList.addAll(filterList);
        }

        List<Map<String, String>> jobSelectDataList = new ArrayList<>();
        for (String name : jobList) {
            Map<String, String> selectMap = new HashMap<>();
            selectMap.put("id", name);
            selectMap.put("text", name);
            jobSelectDataList.add(selectMap);
        }

        ret.put("jobList", jobSelectDataList);
    }

    //组装出项目中Flow的树形结构数据
    private void getFlowRealJobTree(final Project project, final String flowId,
        final List<String> jobList) {
        final Flow flow = project.getFlow(flowId);

        final ArrayList<Map<String, Object>> nodeList =
            new ArrayList<>();
        for (final Node node : flow.getNodes()) {
            //只记录非 flow 类型 job
            if (node.getEmbeddedFlowId() == null) {
                jobList.add(node.getId());
            }
            if (node.getEmbeddedFlowId() != null) {
                getFlowRealJobTree(project, node.getEmbeddedFlowId(), jobList);
            }

        }

    }

    private void ajaxJobHistoryPage(final Project project, final HashMap<String, Object> ret,
        final HttpServletRequest req, final User user) throws ServletException, IOException {

        final String jobId = getParam(req, "jobId");
        final int pageNum = getIntParam(req, "page", 1);
        final int pageSize = getIntParam(req, "size", 20);

        final int skipPage = (pageNum - 1) * pageSize;

        int total = 0;

//    long moyenne = 0;

        List<ExecutableJobInfo> jobInfo = new ArrayList<>();

        try {
            total = this.executorManagerAdapter.getNumberOfJobExecutions(project, jobId);

            jobInfo = this.executorManagerAdapter.getExecutableJobs(project, jobId, skipPage, pageSize);

            if (jobInfo == null || jobInfo.isEmpty()) {
                jobInfo = null;
            }

//      moyenne = this.executorManagerAdapter.getExecutableJobsMoyenneRunTime(project, jobId);

            if (jobInfo != null) {
                jobInfo.stream().forEach(executableJobInfo -> {

                    ExecutableFlow executionFlow = null;
                    try {
                        executionFlow = this.executorManagerAdapter.getExecutableFlow(executableJobInfo.getExecId());
                    } catch (ExecutorManagerException e) {
                        e.printStackTrace();
                    }

                    Map<String, String> repeatMap = executionFlow.getRepeatOption();
                    if (!repeatMap.isEmpty()) {

                        Long recoverRunDate = Long.valueOf(String.valueOf(repeatMap.get("startTimeLong")));

                        LocalDateTime localDateTime = new LocalDateTime(new Date(recoverRunDate)).minusDays(1);

                        Date date = localDateTime.toDate();

                        executableJobInfo.setRunDate(date.getTime());
                    } else {
                        Long runDate = executionFlow.getStartTime();
                        if (-1 != runDate) {
                            LocalDateTime localDateTime = new LocalDateTime(new Date(runDate)).minusDays(1);

                            Date date = localDateTime.toDate();

                            executableJobInfo.setRunDate(date.getTime());
                        } else {
                            executableJobInfo.setRunDate(runDate);
                        }
                    }
                });
            }
        } catch (final ExecutorManagerException e) {

        }

//    long allRunTime = 0;
//    int successFlowNum = 0;
//    if (jobInfo != null) {
//      for (final ExecutableJobInfo info : jobInfo) {
//        if(Status.SUCCEEDED.equals(info.getStatus())){
//          successFlowNum += 1;
//          allRunTime += (info.getEndTime() - info.getStartTime());
//        }
//      }
//      if(allRunTime !=0 && successFlowNum !=0){
//        moyenne = allRunTime/successFlowNum;
//      }
//    }

        final List<Object> jobPageList = new ArrayList<>();
        for (final ExecutableJobInfo info : jobInfo) {
            final Map<String, Object> map = info.toObject();
//      map.put("moyenne", moyenne);
            jobPageList.add(map);
        }

        ret.put("jobPageList", jobPageList);
        ret.put("total", total);
        ret.put("from", pageNum);
        ret.put("length", pageSize);

    }

    /**
     * 根据项目名，工作流名，任务名获取任务文件的配置内容
     *
     * @param project
     * @param ret
     * @param req
     * @throws ServletException
     */
    private void ajaxGetJobParamData(final Project project, final HashMap<String, Object> ret,
        final HttpServletRequest req) throws ServletException {
        final String flowName = getParam(req, "flowName");
        final String jobName = getParam(req, "jobName");

        final Flow flow = project.getFlow(flowName);
        Map<String, String> dataMap = loadProjectManagerServletI18nData();

        if (flow == null) {
            ret.put("error", dataMap.get("project") + project.getName() + dataMap.get("notExistFlow") + flowName);
            return;
        }

        final Node node = flow.getNode(jobName);
        if (node == null) {
            ret.put("error", dataMap.get("flow") + flowName + dataMap.get("notExistJob") + jobName);
            return;
        }

        Props jobProp;
        try {
            jobProp = this.projectManager.getProperties(project, flow, jobName, node.getJobSource());
        } catch (final ProjectManagerException e) {
            ret.put("error", "Failed to retrieve job properties!");
            return;
        }

        if (jobProp == null) {
            jobProp = new Props();
        }

        List<Map<String, String>> jobParamDataList = new ArrayList<>();

        for (final String ps : jobProp.getKeySet()) {
            final Map<String, String> generalParams = new HashMap<>();
            generalParams.put("paramName", ps);
            generalParams.put("paramValue", jobProp.getString(ps));
            if (ps.equals("type") && jobProp.getString(ps).equals("datachecker")) {
                generalParams.put("paramNotice", dataMap.get("checkSourceType"));
            }
            jobParamDataList.add(generalParams);
        }

        ret.put("jobParamData", jobParamDataList);
    }

    /**
     * 根据项目名，工作流名，任务名获取正在运行的定时调度
     *
     * @param project
     * @param ret
     * @param req
     * @throws ServletException
     */
    private void ajaxFetchRunningScheduleId(final Project project, final HashMap<String, Object> ret,
        final HttpServletRequest req) throws ServletException {
        final String flowId = getParam(req, "flow");
        try {
            Schedule schedule = this.scheduleManager.getSchedule(project.getId(), flowId);
            if (schedule != null) {
                int scheduleId = schedule.getScheduleId();
                String cronExpression = schedule.getCronExpression();
                ret.put("cronExpression", cronExpression);
                ret.put("scheduleId", scheduleId);
            } else {
                ret.put("cronExpression", "");
                ret.put("scheduleId", "");
            }
        } catch (ScheduleManagerException e) {
            logger.error("Fetch running schedule failed, caused by:", e);
            ret.put("error", "Fetch running schedule failed, caused by:" + e.getMessage());
        }

    }

    public static boolean getWtssProjectPrivilegeCheck() {
        return wtss_project_privilege_check;
    }
}
