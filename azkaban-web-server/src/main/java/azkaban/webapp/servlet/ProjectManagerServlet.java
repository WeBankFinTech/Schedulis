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

import azkaban.Constants;
import azkaban.Constants.ConfigurationKeys;
import azkaban.ServiceProvider;
import azkaban.alert.Alerter;
import azkaban.executor.*;
import azkaban.flow.*;
import azkaban.flowtrigger.quartz.FlowTriggerScheduler;
import azkaban.history.ExecutionRecover;
import azkaban.i18n.utils.LoadJsonUtils;
import azkaban.jobExecutor.utils.SystemBuiltInParamReplacer;
import azkaban.project.*;
import azkaban.project.entity.*;
import azkaban.project.validator.ValidationReport;
import azkaban.project.validator.ValidatorConfigs;
import azkaban.scheduler.*;
import azkaban.server.HttpRequestUtils;
import azkaban.server.session.Session;
import azkaban.system.SystemManager;
import azkaban.system.SystemUserManagerException;
import azkaban.system.common.TransitionService;
import azkaban.system.entity.WebankDepartment;
import azkaban.system.entity.WtssUser;
import azkaban.trigger.TriggerManager;
import azkaban.trigger.TriggerManagerException;
import azkaban.user.*;
import azkaban.user.Permission.Type;
import azkaban.userparams.UserParamsModule;
import azkaban.userparams.UserParamsService;
import azkaban.utils.*;
import azkaban.webapp.AzkabanWebServer;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.reflect.TypeToken;
import com.google.gson.JsonObject;
import com.google.inject.Injector;
import com.webank.wedatasphere.dss.common.utils.IoUtils;
import org.apache.commons.beanutils.BeanUtils;
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.fileupload.FileItem;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang.ArrayUtils;
import org.apache.commons.lang.StringEscapeUtils;
import org.apache.commons.lang.StringUtils;
import org.apache.commons.lang3.math.NumberUtils;
import org.joda.time.DateTime;
import org.joda.time.LocalDate;
import org.joda.time.LocalDateTime;
import org.joda.time.format.DateTimeFormat;
import org.joda.time.format.DateTimeFormatter;
import org.quartz.SchedulerException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.*;
import java.security.AccessControlException;
import java.sql.SQLException;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.Map.Entry;
import java.util.function.Predicate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

public class ProjectManagerServlet extends AbstractLoginAzkabanServlet {

    private static String RE_SPACE = "(\u0020|\u3000)";
    private static final long serialVersionUID = 1;
    private static final Logger logger = LoggerFactory.getLogger(ProjectManagerServlet.class);

    private static final String FILTER_BY_DATE_PATTERN = "MM/dd/yyyy hh:mm aa";
    private static final String EMPRY_ADVANCED_FILTER = "0-1";
    private static final NodeLevelComparator NODE_LEVEL_COMPARATOR =
            new NodeLevelComparator();
    private static final String LOCKDOWN_CREATE_PROJECTS_KEY = "lockdown.create.projects";
    private static final String LOCKDOWN_UPLOAD_PROJECTS_KEY = "lockdown.upload.projects";
    private static final String WTSS_PROJECT_PRIVILEGE_CHECK_KEY = "wtss.project.privilege.check";
    private static final String WTSS_DEP_UPLOAD_PRIVILEGE_CHECK_KEY = "wtss.department.upload.privilege.check";
    private static final String WTSS_PROJECT_FILE_UPLOAD_LENGTH = "wtss.project.file.upload.length";
    private static final String WTSS_PROJECT_FILE_UPLOAD_COUNT = "wtss.project.file.upload.count";

    private static final String ITSM_SWITCH = "itsm.switch";
    private static final String UPLOAD_DISPLAY_SWITCH = "upload.display.switch";
    private static final String BATCH_VERIFY_SIZE = "batch.verify.size";
    private static final String CLUSTER_ID = "azkaban.cluster.id";
    private static final String SINGLE_EXECUTION = "单次执行";
    private static final String HISTORICAL_RERUN = "历史重跑";
    private static final String TIMED_SCHEDULING = "定时调度";
    private static final String CYCLE_EXECUTION = "循环执行";
    private static final String EVENT_SCHEDULE = "信号调度";
    private static final String PROJECT_CHANGE_LIMIT = "project.change.limit";

    private static final Comparator<Flow> FLOW_ID_COMPARATOR = new Comparator<Flow>() {
        @Override
        public int compare(final Flow f1, final Flow f2) {
            return f1.getId().compareTo(f2.getId());
        }
    };
    private ProjectManager projectManager;
    private ExecutorManagerAdapter executorManagerAdapter;
    private ScheduleManager scheduleManager;
    private EventScheduleServiceImpl eventScheduleService;
    private TransitionService transitionService;
    private SystemManager systemManager;
    private SystemUserManager systemUserManager;
    private FlowTriggerScheduler scheduler;
    private int downloadBufferSize;
    private boolean lockdownCreateProjects = false;
    private boolean lockdownUploadProjects = false;

    // 项目权限管控开关
    private static boolean wtss_project_privilege_check;

    private static int exchangeProjectLimit;

    // 部门上传权限管控开关
    private static boolean wtss_dep_upload_privilege_check;

    // ITSM switch
    private static boolean itsmSwitch;

    private static boolean uploadDisplaySwitch;

    private static int batchVerifySize;

    private boolean enableQuartz = false;

    private static final Pattern USER_ID_PATTERN = Pattern.compile("^[0-9]+$");

    private UserParamsService userParamsService;

    @Override
    public void init(final ServletConfig config) throws ServletException {
        super.init(config);

        final AzkabanWebServer server = (AzkabanWebServer) getApplication();
        this.projectManager = server.getProjectManager();
        this.executorManagerAdapter = server.getExecutorManager();
        this.scheduleManager = server.getScheduleManager();
        this.eventScheduleService = server.getEventScheduleService();
        this.transitionService = server.getTransitionService();
        this.systemManager = transitionService.getSystemManager();
        this.systemUserManager = (SystemUserManager) (transitionService.getUserManager());
        Injector injector = ServiceProvider.SERVICE_PROVIDER.getInjector()
                .createChildInjector(new UserParamsModule());
        this.userParamsService = injector.getInstance(UserParamsService.class);
        this.scheduler = server.getScheduler();
        this.lockdownCreateProjects = server.getServerProps()
                .getBoolean(LOCKDOWN_CREATE_PROJECTS_KEY, false);
        exchangeProjectLimit = server.getServerProps().getInt(PROJECT_CHANGE_LIMIT, 5);

        wtss_project_privilege_check = server.getServerProps()
                .getBoolean(WTSS_PROJECT_PRIVILEGE_CHECK_KEY, false);
        wtss_dep_upload_privilege_check = server.getServerProps()
                .getBoolean(WTSS_DEP_UPLOAD_PRIVILEGE_CHECK_KEY, false);
        this.enableQuartz = server.getServerProps()
                .getBoolean(ConfigurationKeys.ENABLE_QUARTZ, false);
        itsmSwitch = server.getServerProps().getBoolean(ITSM_SWITCH, false);
        uploadDisplaySwitch = server.getServerProps().getBoolean(UPLOAD_DISPLAY_SWITCH, false);
        batchVerifySize = server.getServerProps().getInt(BATCH_VERIFY_SIZE, 100);
        if (this.lockdownCreateProjects) {
            logger.info("Creation of projects is locked down");
        }

        this.lockdownUploadProjects =
                server.getServerProps().getBoolean(LOCKDOWN_UPLOAD_PROJECTS_KEY, false);
        if (this.lockdownUploadProjects) {
            logger.info("Uploading of projects is locked down");
        }

        this.downloadBufferSize =
                server.getServerProps().getInt(Constants.PROJECT_DOWNLOAD_BUFFER_SIZE_IN_BYTES,
                        8192);

        logger.info("downloadBufferSize: " + this.downloadBufferSize);
    }

    @Override
    protected void handleGet(final HttpServletRequest req, final HttpServletResponse resp,
                             final Session session) throws ServletException, IOException {

        if (hasParam(req, "ajax")) {
            String ajaxName = getParam(req, "ajax");
            if (ajaxName.equals("downloadBusinessInfoTemple")) {
                downloadBusinessInfoTemple(req, resp, session);
            }
            if (ajaxName.equals("addNewFlowBusiness")) {
                Map<String, Object> ret = new HashMap<>();
                if (hasParam(req, "itsmNo")) {
                    try {
                        Props serverProps = getApplication().getServerProps();
                        Long itsmNo = Long.valueOf(req.getParameter("itsmNo"));
                        Map<String, Object> itsmMap = new HashMap<>();
                        ItsmUtil.getRequestFormStatus(serverProps, itsmNo, itsmMap);

                        if (!itsmMap.isEmpty()) {
                            int requestStatus = (int) itsmMap.get("requestStatus");
                            if (requestStatus != 1009 && requestStatus != 1013 && requestStatus != 1015) {
                                throw new ServletException("单据未审核");
                            }

                            //获取project和flow
                            String projectName = parseNull(itsmMap.get("projectName") + "");
                            String flowId = parseNull(itsmMap.get("flowId") + "");
                            Long requestRatifyFinishTime = (Long) itsmMap.get("requestRatifyFinishTime");
                            Project project = projectManager.getProject(projectName);
                            //获取flowbusiness
                            FlowBusiness flowBusiness = projectManager.getFlowBusiness(project.getId(), flowId, "");
                            if (flowBusiness != null) {
                                String oldWtssNo = flowBusiness.getItsmNo();
                                //获取单据审批时间
                                if (StringUtils.isNotEmpty(oldWtssNo)) {
                                    Map<String, Object> olditsmMap = new HashMap<>();
                                    ItsmUtil.getRequestFormStatus(serverProps, Long.valueOf(oldWtssNo), olditsmMap);
                                    //获取单据审批时间
                                    Long oldrequestRatifyFinishTime = (Long) olditsmMap.get("requestRatifyFinishTime");
                                    //请求进入的单据审核时间大于原来单据时间时，直接修改数据
                                    if (requestRatifyFinishTime > oldrequestRatifyFinishTime) {
                                        buildFlowBusiness(itsmMap, itsmNo.toString(), project, flowBusiness);
                                    }
                                } else { //没有单据编号，直接修改
                                    buildFlowBusiness(itsmMap, itsmNo.toString(), project, flowBusiness);
                                }

                            } else {
                                //不存在，插入操作
                                buildFlowBusiness(itsmMap, itsmNo.toString(), project, new FlowBusiness());

                            }


                        }
                    } catch (Exception e) {
                        logger.error("获取信息失败:" + e);
                        ret.put("code", 500);
                        ret.put("error", "同步失败" + e);

                    }

                }

                ret.put("code", 200);
                ret.put("message", "同步成功");

            }
        }
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

    private FlowBusiness buildFlowBusiness(Map<String, Object> itsmMap, String itsmNo, Project project, FlowBusiness flowBusiness) {

        flowBusiness.setItsmNo(itsmNo);

        flowBusiness.setFlowId(parseNull(itsmMap.get("flowId") + ""));

        flowBusiness.setProjectId(project.getId());

        flowBusiness.setJobId(parseNull(itsmMap.get("jobId") + ""));

        flowBusiness.setSubsystemDesc(parseNull(itsmMap.get("subsystemDesc") + ""));

        flowBusiness.setPlanStartTime(parseNull(itsmMap.get("planStartTime") + ""));

        flowBusiness.setBusDomain(parseNull(itsmMap.get("busDomain") + ""));

        flowBusiness.setDevDeptDesc(parseNull(itsmMap.get("devDeptDesc") + ""));

        flowBusiness.setBusTypeSecondDesc(parseNull(itsmMap.get("busTypeSecondDesc") + ""));

        flowBusiness.setBatchGroupDesc(parseNull(itsmMap.get("batchGroupDesc") + ""));

        flowBusiness.setLastFinishTime(parseNull(itsmMap.get("lastFinishTime") + ""));

        flowBusiness.setPlanFinishTime(parseNull(itsmMap.get("planFinishTime") + ""));

        flowBusiness.setBusTypeFirstDesc(parseNull(itsmMap.get("busTypeFirstDesc") + ""));

        flowBusiness.setBusPathDesc(parseNull(itsmMap.get("busPathDesc") + ""));

        flowBusiness.setBusResLvl(parseNull(itsmMap.get("busResLvl") + ""));

        flowBusiness.setLastStartTime(parseNull(itsmMap.get("lastStartTime") + ""));

        flowBusiness.setOpsDeptDesc(parseNull(itsmMap.get("opsDeptDesc") + ""));
        flowBusiness.setBusDesc(parseNull(itsmMap.get("busDesc") + ""));
        flowBusiness.setScanPartitionNum(Integer.parseInt(parseNull(itsmMap.get("scanPartitionNum") + "")));

        flowBusiness.setScanDataSize(Integer.parseInt(parseNull(itsmMap.get("scanDataSize") + "")));

        projectManager.mergeFlowBusiness(flowBusiness);

        return flowBusiness;
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
            if ("upload".equals(action)) {
                ajaxHandleUpload(req, resp, ret, params, session);
            }

            this.writeJSON(resp, ret);
        } else if (params.containsKey("action")) {
            final String action = (String) params.get("action");
            if (action.contains("uploadBusinessInfo")) {
                if (hasPermission(session.getUser(), Type.WRITE)) {
                    logger.info("come in uploadBusinessInfo");
                    handleUploadBusinessInfo(req, resp, params, session);
                } else {
                    setErrorMessageInCookie(resp, "Have no permission to upload file");
                    resp.sendRedirect(req.getRequestURL() + "/index");
                }
            }

            if ("upload".equals(action)) {
                handleUpload(req, resp, params, session);
            }
            //TODO 批量上传应用信息
//            if (action.equals("uploadBusiness")) {
//                handleUploadBusiness(req, resp, params, session);
//            }
        }
    }

    @Override
    protected void handlePost(final HttpServletRequest req, final HttpServletResponse resp,
                              final Session session) throws ServletException, IOException {
        if (hasParam(req, "action")) {
            final String action = getParam(req, "action");
            if ("create".equals(action)) {
                handleCreate(req, resp, session);
            } else if ("configProjectHourlyReport".equals(action)) {
                configProjectHourlyReport(req, resp, session);
            } else if ("removeProjectHourlyReport".equals(action)) {
                removeProjectHourlyReport(req, resp, session);
            }
        } else if (hasParam(req, "ajax")) {
            final String ajaxName = getParam(req, "ajax");
            if ("mergeFlowBusiness".equals(ajaxName)) {
                ajaxMergeFlowBusiness(req, resp, session, new HashMap<String, String>(), new HashMap<String, Object>());
            } else if ("aompRegister".equals(ajaxName)) {
                ajaxHandleAompRegister(req, resp, session);
            } else if ("batchVerifyProjectsPermission".equals(ajaxName)) {
                ajaxBatchVerifyProjectsPermission(req, resp, session);
            } else if ("ajaxRefreshProjectFlowConfig".equals(ajaxName)) {
                ajaxRefreshProjectFlowConfig(req, resp, session);
            }/*else if ("linkJobHook".equals(ajaxName)) {
        ajaxHandleLinkJobHook(req, resp, session);
      }*/
        }
    }

    /**
     * 删除项目小时报配置
     *
     * @param req
     * @param resp
     * @param session
     */
    private void removeProjectHourlyReport(HttpServletRequest req, HttpServletResponse resp,
                                           Session session) throws IOException {
        Map<String, Object> ret = new HashMap<>();
        int result = 0;
        try {
            User user = session.getUser();

            String projectName = getParam(req, "project");
            Project project = this.projectManager.getProject(projectName);

            if (project == null) {
                ret.put("error", "Project " + projectName + " does not exist!");
                this.writeJSON(resp, ret);
                return;
            }

            if (!hasPermission(project, user, Type.READ)) {
                ret.put("error", "Login User " + user
                        + " has no READ permission for project " + project.getName());
                this.writeJSON(resp, ret);
                return;
            }

            result = this.projectManager.removeProjectHourlyReportConfig(project);

            if (result == 1) {
                ret.put("success",
                        "Remove project hourly report for " + projectName + " successfully");
                this.writeJSON(resp, ret);
            } else if (result == 0) {
                ret.put("error", "Project hourly report for " + projectName + " does not exist! ");
                this.writeJSON(resp, ret);
            }
        } catch (Exception e) {
            ret.put("error", "Remove hourly project report error: " + e.getMessage());
            this.writeJSON(resp, ret);
        }

    }

    private void ajaxRefreshProjectFlowConfig(HttpServletRequest req, HttpServletResponse resp, Session session) throws IOException, ServletException {
        int projectId = getIntParam(req, "projectId");
        String flowModifyInfoStr = getParam(req, "flowModifyInfos");
        HashMap<String, String> flowNameMap = new HashMap<>();
        HashMap<String, String> nodeNameMap = new HashMap<>();
        HashMap<String, Object> ret = new HashMap<>();
        List<Object> flowModifyInfos = GsonUtils.fromJson(flowModifyInfoStr, Object.class);
        for (Object flowModifyInfo : flowModifyInfos) {
            Map<String, Object> map = (Map<String, Object>) flowModifyInfo;
            String historyName = (String) map.get("historyName");
            String newName = (String) map.get("newName");
            flowNameMap.put(historyName, newName);
            nodeNameMap.put(historyName, newName);
            List<Map<String, Object>> nodeList = (List<Map<String, Object>>) map.get("nodeList");
            for (Map<String, Object> objectMap : nodeList) {
                String nodeHistoryName = (String) objectMap.get("nodeHistoryName");
                if (StringUtils.isEmpty(nodeHistoryName)) {
                    continue;
                }
                String nodeNewName = (String) objectMap.get("nodeNewName");
                nodeNameMap.put(nodeHistoryName, nodeNewName);
            }
        }
        Project project = projectManager.getProject(projectId);
        if (!hasPermission(project, session.getUser(), Type.WRITE)) {
            ret.put("status", "error");
            ret.put("message", "has no permission to write");
            this.writeJSON(resp, ret);
            return;
        }
        try {
            TriggerManager triggerManager = ServiceProvider.SERVICE_PROVIDER.getInstance(TriggerManager.class);
            triggerManager.refreshTriggerConfig(flowNameMap, nodeNameMap, project);
        } catch (TriggerManagerException e) {
            logger.error("refresh trigger config failed : ", e);
            ret.put("status", "error");
            ret.put("message", "refresh trigger config failed");
            this.writeJSON(resp, ret);
            return;
        }

        ret.put("status", "success");
        ret.put("message", "refresh trigger config success");
        this.writeJSON(resp, ret);

    }


    private Project getProjectFromCache(final HttpServletRequest req, String projectName) throws ServletException {
        Project project = null;
        if (hasParam(req, "projectId")) {
            int projectId = getIntParam(req, "projectId");
            project = this.projectManager.getProjectAndFlowBaseInfo(projectId);
        } else if (StringUtils.isNotBlank(projectName)) {
            project = this.projectManager.getProjectAndFlowBaseInfoByName(projectName);
        }
        return project;
    }

    private Project getProjectFromDB(final HttpServletRequest req, String projectName) throws ServletException {
        Project project = null;
        if (hasParam(req, "projectId")) {
            int projectId = getIntParam(req, "projectId");
            project = this.projectManager.getProject(projectId);
        } else if (StringUtils.isNotBlank(projectName)) {
            project = this.projectManager.getProject(projectName);
        }
        return project;
    }

    private void handleAJAXAction(final HttpServletRequest req,
                                  final HttpServletResponse resp, final Session session) throws ServletException,
            IOException {
        final HashMap<String, Object> ret = new HashMap<>();
        final User user = session.getUser();

        if (hasParam(req, "project")) {
            final String projectName = getParam(req, "project");
            ret.put("project", projectName);

            Project project = getProjectFromCache(req, projectName);

            if (project == null) {
                ret.put("error", "Project " + projectName + " doesn't exist.");
            } else {
                ret.put("projectId", project.getId());
                ret.put("projectActive", project.isActive());
                final String ajaxName = getParam(req, "ajax");
                if ("getProjectId".equals(ajaxName)) {
                    // Do nothing, since projectId is added to all AJAX requests.
                } else if ("fetchHistoryRerunConfiguration".equals(ajaxName)) {
                    if (handleAjaxPermission(project, user, Type.READ, ret)) {
                        ajaxFetchHistoryRerunConfiguration(project, req, ret, user);
                    }
                } else if ("fetchProjectLogs".equals(ajaxName)) {
                    if (handleAjaxPermission(project, user, Type.READ, ret)) {
                        ajaxFetchProjectLogEvents(project, req, ret);
                    }
                } else if ("fetchProjectVersions".equals(ajaxName)) {
                    if (handleAjaxPermission(project, user, Type.READ, ret)) {
                        ajaxFetchProjectVersion(project, req, ret);
                    }
                } else if ("ajaxFetchProjectSchedules".equals(ajaxName)) {
                    if (handleAjaxPermission(project, user, Type.READ, ret)) {
                        ajaxFetchProjectSchedules(project, ret);
                    }
                } else if ("fetchRunningFlow".equals(ajaxName)) {
                    if (handleAjaxPermission(project, user, Type.READ, ret)) {
                        project = getProjectFromDB(req, projectName);
                        if (project == null) {
                            ret.put("error", "Project " + projectName + " doesn't exist.");
                        } else {
                            ajaxFetchRunningFlow(project, ret, req);
                        }
                    }
                } else if ("fetchflowjobs".equals(ajaxName)) {
                    if (handleAjaxPermission(project, user, Type.READ, ret)) {
                        project = getProjectFromDB(req, projectName);
                        if (project == null) {
                            ret.put("error", "Project " + projectName + " doesn't exist.");
                        } else {
                            ajaxFetchFlow(project, ret, req);
                        }
                    }
                } else if ("fetchflowdetails".equals(ajaxName)) {
                    if (handleAjaxPermission(project, user, Type.READ, ret)) {
                        project = getProjectFromDB(req, projectName);
                        if (project == null) {
                            ret.put("error", "Project " + projectName + " doesn't exist.");
                        } else {
                            ajaxFetchFlowDetails(project, ret, req);
                        }
                    }
                } else if ("fetchflowgraph".equals(ajaxName)) {
                    if (handleAjaxPermission(project, user, Type.READ, ret)) {
                        project = getProjectFromDB(req, projectName);
                        if (project == null) {
                            ret.put("error", "Project " + projectName + " doesn't exist.");
                        } else {
                            ajaxFetchFlowGraph(project, ret, req);
                        }
                    }
                } else if ("fetchflownodedata".equals(ajaxName)) {
                    if (handleAjaxPermission(project, user, Type.READ, ret)) {
                        project = getProjectFromDB(req, projectName);
                        if (project == null) {
                            ret.put("error", "Project " + projectName + " doesn't exist.");
                        } else {
                            ajaxFetchFlowNodeData(project, ret, req);
                        }
                    }
                } else if ("fetchprojectflows".equals(ajaxName)) {
                    //Project页面获取所有项目信息
                    if (handleAjaxPermission(project, user, Type.READ, ret)) {
                        ajaxFetchProjectFlows(project, ret, req);
                    }
                } else if ("changeDescription".equals(ajaxName)) {
                    if (handleAjaxPermission(project, user, Type.WRITE, ret)) {
                        ajaxChangeDescription(project, ret, req, user);
                    }
                } else if ("changeCreateUser".equals(ajaxName)) {
                    if (handleAjaxPermission(project, user, Type.WRITE, ret)) {
                        ajaxChangeCreateUser(project, ret, req, user);
                    }
                } else if ("changeJobLimit".equals(ajaxName)) {
                    if (handleAjaxPermission(project, user, Type.WRITE, ret)) {
                        ajaxChangeJobLimit(project, ret, req, user);
                    }
                } else if ("getPermissions".equals(ajaxName)) {
                    if (handleAjaxPermission(project, user, Type.READ, ret)) {
                        ajaxGetPermissions(project, ret);
                    }
                } else if ("getGroupPermissions".equals(ajaxName)) {
                    if (handleAjaxPermission(project, user, Type.READ, ret)) {
                        ajaxGetGroupPermissions(project, ret);
                    }
                } else if ("getProxyUsers".equals(ajaxName)) {
                    if (handleAjaxPermission(project, user, Type.READ, ret)) {
                        ajaxGetProxyUsers(project, ret);
                    }
                } else if ("changePermission".equals(ajaxName)) {
                    if (handleAjaxPermission(project, user, Type.ADMIN, ret)) {
                        ajaxChangePermissions(project, ret, req, user);
                    }
                } else if ("addPermission".equals(ajaxName)) {
                    if (handleAjaxPermission(project, user, Type.ADMIN, ret)) {
                        ajaxAddPermission(project, ret, req, user);
                    }
                } else if ("addProxyUser".equals(ajaxName)) {
                    if (handleAjaxPermission(project, user, Type.ADMIN, ret)) {
                        ajaxAddProxyUser(project, ret, req, user);
                    }
                } else if ("removeProxyUser".equals(ajaxName)) {
                    if (handleAjaxPermission(project, user, Type.ADMIN, ret)) {
                        ajaxRemoveProxyUser(project, ret, req, user);
                    }
                } else if ("fetchFlowExecutions".equals(ajaxName)) {
                    if (handleAjaxPermission(project, user, Type.READ, ret)) {
                        ajaxFetchFlowExecutions(project, ret, req, user);
                    }
                } else if ("fetchLastSuccessfulFlowExecution".equals(ajaxName)) {
                    if (handleAjaxPermission(project, user, Type.READ, ret)) {
                        ajaxFetchLastSuccessfulFlowExecution(project, ret, req);
                    }
                } else if ("fetchJobInfo".equals(ajaxName)) {
                    if (handleAjaxPermission(project, user, Type.READ, ret)) {
                        project = getProjectFromDB(req, projectName);
                        if (project == null) {
                            ret.put("error", "Project " + projectName + " doesn't exist.");
                        } else {
                            ajaxFetchJobInfo(project, ret, req);
                        }
                    }
                } else if ("setJobOverrideProperty".equals(ajaxName)) {
                    if (handleAjaxPermission(project, user, Type.WRITE, ret)) {
                        project = getProjectFromDB(req, projectName);
                        if (project == null) {
                            ret.put("error", "Project " + projectName + " doesn't exist.");
                        } else {
                            ajaxSetJobOverrideProperty(project, ret, req, user);
                        }
                    }
                } else if ("checkForWritePermission".equals(ajaxName)) {
                    ajaxCheckForWritePermission(project, user, ret);
                } else if ("fetchJobExecutionsHistory".equals(ajaxName)) {
                    if (handleAjaxPermission(project, user, Type.READ, ret)) {
                        ajaxFetchJobExecutionsHistory(project, ret, req);
                    }
                } else if ("ajaxAddProjectUserPermission".equals(ajaxName)) {
                    if (handleAjaxPermission(project, user, Type.ADMIN, ret)) {
                        ajaxAddProjectUserPermission(project, ret, req, user);
                    }
                } else if ("ajaxGetUserProjectPerm".equals(ajaxName)) {
                    if (handleAjaxPermission(project, user, Type.ADMIN, ret)) {
                        ajaxGetUserProjectPerm(project, ret, req, user);
                    }
                } else if ("ajaxRemoveProjectAdmin".equals(ajaxName)) {
                    if (handleAjaxPermission(project, user, Type.ADMIN, ret)) {
                        ajaxRemoveProjectAdmin(project, ret, req, user);
                    }
                } else if ("ajaxAddProjectAdmin".equals(ajaxName)) {
                    if (handleAjaxPermission(project, user, Type.ADMIN, ret)) {
                        ajaxAddProjectAdmin(project, ret, req, user);
                    }
                } else if ("fetchFlowRealJobLists".equals(ajaxName)) {
                    if (handleAjaxPermission(project, user, Type.READ, ret)) {
                        project = getProjectFromDB(req, projectName);
                        if (project == null) {
                            ret.put("error", "Project " + projectName + " doesn't exist.");
                        } else {
                            ajaxFetchFlowRealJobList(project, ret, req);
                        }
                    }
                } else if ("fetchJobNestedIdList".equals(ajaxName)) {
                    project = getProjectFromDB(req, projectName);
                    if (project == null) {
                        ret.put("error", "Project " + projectName + " doesn't exist.");
                    } else {
                        ajaxFetchJobNestedIdList(project, ret, req);
                    }

//                    if (handleAjaxPermission(project, user, Type.READ, ret)) {
//                        project = getProjectFromDB(req, projectName);
//                        if (project == null) {
//                            ret.put("error", "Project " + projectName + " doesn't exist.");
//                        } else {
//                            ajaxFetchJobNestedIdList(project, ret, req);
//                        }
//                    }
                } else if ("fetchJobHistoryPage".equals(ajaxName)) {

                    project = getProjectFromDB(req, projectName);
                    if (project == null) {
                        ret.put("error", "Project " + projectName + " doesn't exist.");
                    } else {
                        ajaxJobHistoryPage(project, ret, req, user);
                    }
                } else if ("getJobParamData".equals(ajaxName)) {
                    ajaxGetJobParamData(project, ret, req);
                } else if ("fetchRunningScheduleId".equals(ajaxName)) {
                    ajaxFetchRunningScheduleId(project, ret, req);
                } else if ("fetchRunningEventScheduleId".equals(ajaxName)) {
                    ajaxFetchRunningEventScheduleId(project, ret, req);
                } else if ("checkUserUploadPermission".equals(ajaxName)) {
                    // 检查用户上传权限
                    ajaxCheckUserUploadPermission(req, resp, ret, session);
                } else if ("checkDepUploadPermission".equals(ajaxName)) {
                    // 检查部门上传权限
                    ajaxCheckDepUploadPermission(req, resp, ret, session);
                } else if ("checkDeleteProjectFlagPermission".equals(ajaxName)) {
                    // 检查删除项目权限
                    ajaxCheckDeleteProjectFlagPermission(req, resp, ret, session);
                } else if ("checkUserScheduleFlowPermission".equals(ajaxName)) {
                    // 检查用户调度流程权限
                    ajaxCheckUserScheduleFlowPermission(req, resp, ret, session);
                } else if ("checkUserExecuteFlowPermission".equals(ajaxName)) {
                    // 检查用户执行流程权限
                    ajaxCheckUserExecuteFlowPermission(req, resp, ret, session);
                } else if ("checkKillFlowPermission".equals(ajaxName)) {
                    // 检查用户KILL流程权限
                    ajaxCheckKillFlowPermission(req, resp, ret, session);
                } else if ("checkUserUpdateScheduleFlowPermission".equals(ajaxName)) {
                    // 检查用户修改调度配置权限(1.49.0修改成提交时校验)
                    ajaxCheckUserUpdateScheduleFlowPermission(req, resp, ret, session);
                } else if ("checkUserDeleteScheduleFlowPermission".equals(ajaxName)) {
                    // 检查用户删除调度配置权限
                    ajaxCheckUserDeleteScheduleFlowPermission(req, resp, ret, session);
                } else if ("checkUserSetScheduleAlertPermission".equals(ajaxName)) {
                    // 检查用户设置告警配置权限
                    ajaxCheckUserSetScheduleAlertPermission(req, resp, ret, session);
                } else if ("checkAddProjectManagePermission".equals(ajaxName)) {
                    // 检查添加项目管理员权限
                    ajaxCheckAddProjectManagePermission(req, resp, ret, session);
                } else if ("checkAddProjectUserPermission".equals(ajaxName)) {
                    // 检查添加项目用户权限
                    ajaxCheckAddProjectUserPermission(req, resp, ret, session);
                } else if ("checkRemoveProjectManagePermission".equals(ajaxName)) {
                    // 检查移除项目管理员权限
                    ajaxCheckRemoveProjectManagePermission(req, resp, ret, session);
                } else if ("checkUpdateProjectUserPermission".equals(ajaxName)) {
                    // 检查更新项目用户权限
                    ajaxCheckUpdateProjectUserPermission(req, resp, ret, session);
                } else if ("checkRunningPageKillFlowPermission".equals(ajaxName)) {
                    // 检查用户Kill运行中的工作流权限
                    ajaxCheckRunningPageKillFlowPermission(req, resp, ret, session);
                } else if ("checkUserSwitchScheduleFlowPermission".equals(ajaxName)) {
                    // 检查用户开启或关闭定时调度权限
                    ajaxcheckUserSwitchScheduleFlowPermission(req, resp, ret, session);
                } else if ("restoreProject".equals(ajaxName)) {
                    ajaxRestoreProject(project, user, ret, req);
                } else if ("deleteProject".equals(ajaxName)) {
                    ajaxDeleteProject(project, user, ret, req);
                } else if ("changeProjectOwner".equals(ajaxName)) {
                    // change project owner
                    if (handleAjaxPermission(project, user, Type.ADMIN, ret)) {
                        ajaxChangeProjectOwner(project, user, req, ret);
                    }
                } else if ("getProjectCreator".equals(ajaxName)) {
                    if (handleAjaxPermission(project, user, Type.READ, ret)) {
                        ajaxFetchProjectCreator(project, req, ret);
                    }
                } else if ("getLineageBusiness".equals(ajaxName)) {
                    if (handleAjaxPermission(project, user, Type.READ, ret)) {
                        ajaxGetLineageBusiness(project, user, req, ret);
                    }
                } else if ("getProjectChangeOwnerInfo".equals(ajaxName)) {
                    if (handleAjaxPermission(project, user, Type.READ, ret)) {
                        ajaxGetProjectChangeOwnerInfo(project, user, req, ret);
                    }
                } else if ("updateProjectChangeOwnerStatus".equals(ajaxName)) {
                    if (handleAjaxPermission(project, user, Type.ADMIN, ret)) {
                        ajaxUpdateProjectChangeOwnerStatus(project, user, req, ret);
                    }
                } else if ("ajaxHandleProjectHourlyMetrics".equals(ajaxName)) {
                    if (hasPermission(user, Type.ADMIN)) {
                        String hourlyReportMessage = handleProjectHourlyMetrics();
                        ret.put("hourlyReportMessage", hourlyReportMessage);
                    }

                }
                //修改项目负责人
                else if ("changePrincipal".equals(ajaxName)) {
                    if (handleAjaxPermission(project, user, Type.WRITE, ret)) {
                        ajaxChangePrincipal(project, ret, req, user);
                    }
                } else {
                    ret.put("error", "Cannot execute command " + ajaxName);
                }
            }
        } else {

            final String ajaxName = getParam(req, "ajax");

            if ("checkCurrentLanguage".equals(ajaxName)) {
                // 检查当前语言
                ajaxCheckCurrentLanguage(req, resp, ret, session);
            } else if ("checkUserCreateProjectPermission".equals(ajaxName)) {
                // 检查用户创建项目权限
                ajaxCheckUserCreateProjectPermission(req, resp, ret, session);
            } else if ("checkDeleteScheduleInDescriptionFlagPermission".equals(ajaxName)) {
                // 检查用户删除摘要中定时调度权限
                ajaxCheckDeleteScheduleInDescriptionFlagPermission(req, resp, ret, session);
            } else if ("getFlowBusiness".equals(ajaxName)) {
                ajaxGetFlowBusiness(req, resp, session, ret);
            } else if ("getCmdbData".equals(ajaxName)) {
                ajaxGetCmdbData(req, resp, session, ret);
            } else if ("getItsmListData4Aomp".equals(ajaxName)) {
                ajaxGetItsmListData4Aomp(req, ret, session);
            } else if ("ajaxFetchUserPermProjects".equals(ajaxName)) {
                fetchUserPermProjects(req, ret);
            } else if ("ajaxFetchMaintainedDeptUsers".equals(ajaxName)) {
                fetchMaintainedDeptUsers(req, ret);
            } else if ("ajaxRequestToItsm4ExchangeProjectOwner".equals(ajaxName)) {
                requestToItsm4ExchangeProjectOwner(req, ret, session);
            } else if ("batchCheckUserSwitchScheduleFlowPermission".equals(ajaxName)) {
                ajaxcheckUserSwitchScheduleFlowPermission(req, resp, ret, session);
            } else if ("batchCheckUserDeleteScheduleFlowPermission".equals(ajaxName)) {
                ajaxCheckUserDeleteScheduleFlowPermission(req, resp, ret, session);
            } else if ("getProjectHourlyReportConfig".equals(ajaxName)) {
                getProjectHourlyReportConfig(req, ret, session);
            } else if ("fetchProjectHourlyReport".equals(ajaxName)) {
                ajaxFetchProjectHourlyReport(req, ret, session);
            } else if ("ajaxHandleProjectHourlyMetrics".equals(ajaxName)) {
                if (hasPermission(user, Type.ADMIN)) {
                    String hourlyReportMessage = handleProjectHourlyMetrics();
                    ret.put("hourlyReportMessage", hourlyReportMessage);
                }

            }
        }

        this.writeJSON(resp, ret);
    }

    private void ajaxFetchProjectHourlyReport(HttpServletRequest req, HashMap<String, Object> ret, Session session) {
        User user = session.getUser();
        String projectName = req.getParameter("projectName");
        this.projectManager.fetchProjectHourlyReport(projectName, user, ret);
    }

    /**
     * 获取项目小时报配置
     *
     * @param req
     * @param ret
     * @param session
     */
    private void getProjectHourlyReportConfig(HttpServletRequest req, HashMap<String, Object> ret,
                                              Session session) {
        try {
            User user = session.getUser();
            List<ProjectHourlyReportConfig> userProjectHourlyReportConfigList = new ArrayList<>();
            List<ProjectHourlyReportConfig> projectHourlyReportConfigList = this.projectManager.getProjectHourlyReportConfigList(
                    user);

            for (ProjectHourlyReportConfig projectHourlyReportConfig : projectHourlyReportConfigList) {
                String projectName = projectHourlyReportConfig.getProjectName();
                Project project = this.projectManager.getProject(projectName);
                // 用户项目权限校验
                if (hasPermission(project, user, Type.READ)) {
                    userProjectHourlyReportConfigList.add(projectHourlyReportConfig);
                }
            }

            ret.put("projectHourlyReportConfigList", userProjectHourlyReportConfigList);
        } catch (SQLException e) {
            ret.put("error", "Get project hourly report config error: " + e.getMessage());
        }
    }

    /**
     * 配置运营小时报 POST 方法
     *
     * @param req
     * @param resp
     * @param session
     */
    private void configProjectHourlyReport(HttpServletRequest req, HttpServletResponse resp,
                                           Session session) throws IOException {
        Map<String, Object> ret = new HashMap<>();
        int result = 0;
        try {
            User user = session.getUser();

            String reportMapReq = getParam(req, "reportMap");
            Map<String, Map<String, String>> reportMap = GsonUtils.json2Map(reportMapReq);

            for (Entry<String, Map<String, String>> entry : reportMap.entrySet()) {
                String projectName = entry.getKey();
                // 接收方式、接收人
                Map<String, String> projectReportSettings = entry.getValue();
                String reportWay = projectReportSettings.get("reportWay");
                // 可能有多个，逗号隔开
                String reportReceiverString = projectReportSettings.get("reportReceiver");

                Project project = this.projectManager.getProject(projectName);
                // 项目存在性校验
                if (project == null) {
                    ret.put("error", "Project " + projectName + " does not exist!");
                    this.writeJSON(resp, ret);
                    return;
                }

                // 操作用户权限校验
                if (!hasPermission(project, user, Type.READ)) {
                    ret.put("error", "User " + user.getUserId()
                            + " has no READ permission for project " + project.getName());
                    this.writeJSON(resp, ret);
                    return;
                }

                String[] reportReceivers = reportReceiverString.split(",");
                // 接收人权限校验
                for (String reportReceiver : reportReceivers) {
                    User reportReceiverUser = this.systemUserManager.getUser(reportReceiver);
                    if (reportReceiverUser == null) {
                        ret.put("error", "Report Receiver " + reportReceiver + " does not exist!");
                        this.writeJSON(resp, ret);
                        return;
                    }

                    if (!hasPermission(project, reportReceiverUser, Type.READ)) {
                        ret.put("error", "Report Receiver " + reportReceiver
                                + " has no READ permission for project " + project.getName());
                        this.writeJSON(resp, ret);
                        return;
                    }
                }

                // 更新 DB
                result = this.projectManager.updateProjectHourlyReportConfig(project, user,
                        reportWay, reportReceiverString);
            }
        } catch (Exception e) {
            ret.put("error", "Update hourly project report error: " + e.getMessage());
            this.writeJSON(resp, ret);
            return;
        }

        if (result > 0) {
            ret.put("success", "Update project hourly report successfully");
            this.writeJSON(resp, ret);
        }
    }


    private void ajaxBatchVerifyProjectsPermission(HttpServletRequest req, HttpServletResponse resp, Session session) throws IOException {
        JsonObject jsonObject = HttpRequestUtils.parseRequestToJsonObject(req);
        ArrayList<ProjectFlow> projectFlows = GsonUtils.jsonToJavaObject(jsonObject.get("projectFlowInfo"), new TypeToken<ArrayList<ProjectFlow>>() {
        }.getType());

        HashMap<String, Object> ret = new HashMap<>();
        ArrayList<ProjectFlow> successList = new ArrayList<>();
        ArrayList<ProjectFlow> failedList = new ArrayList<>();
        if (batchVerifySize < projectFlows.size()) {
            ret.put("error", "The number of tasks exceeds the limit " + batchVerifySize);
            this.writeJSON(resp, ret);
            return;
        }
        for (ProjectFlow projectFlow : projectFlows) {
            final String projectName = projectFlow.getProjectName();
            final String flowId = projectFlow.getFlowId();
            // 检查项目是否存在，工作流基于project这一层级
            final Project project = getProjectAjaxByPermission(ret, projectName, session.getUser(), Type.EXECUTE);
            if (project == null) {
                failedList.add(projectFlow);
                continue;
            }

            if (project.getProjectLock() == 1) {
                failedList.add(projectFlow);
                continue;
            }
            // 检查工作流是否存在
            final Flow flow = project.getFlow(flowId);
            if (flow == null) {
                failedList.add(projectFlow);
                continue;
            }
            successList.add(projectFlow);
        }
        ret.put("size", batchVerifySize);
        ret.put("checkSuccess", successList);
        ret.put("checkFailed", failedList);
        ret.remove("error");

        this.writeJSON(resp, ret);
    }

    private void ajaxFetchHistoryRerunConfiguration(Project project, HttpServletRequest req, HashMap<String, Object> ret, User user) throws ServletException {
        String flow = getParam(req, "flow");
        int page = getIntParam(req, "page", 0);
        int size = getIntParam(req, "size", 10);

        if (page < 0) {
            page = 0;
        }

        Set<String> userRoleSet = new HashSet<>();
        userRoleSet.addAll(user.getRoles());

        List<ExecutionRecover> executionRecovers = null;
        int total = 0;
        try {
            if (userRoleSet.contains("admin")) {
                executionRecovers = this.executorManagerAdapter.fetchAllHistoryRerunConfiguration(project.getId(), flow, page * size, size);
                total = this.executorManagerAdapter.getAllExecutionRecoverTotal(project.getId(), flow);
            } else {
                if (StringUtils.isNotEmpty(user.getNormalUser())) {
                    executionRecovers = this.executorManagerAdapter.fetchMaintainedHistoryRerunConfiguration(project.getId(), flow, user.getUserId(), page * size, size);
                    total = this.executorManagerAdapter.getMaintainedExecutionRecoverTotal(project.getId(), flow, user.getUserId());
                } else {
                    executionRecovers = this.executorManagerAdapter.fetchUserHistoryRerunConfiguration(project.getId(), flow, user.getUserId(), page * size, size);
                    total = this.executorManagerAdapter.getUserExecutionRecoverTotal(project.getId(), flow, user.getUserId());
                }
            }
        } catch (ExecutorManagerException e) {
            throw new ServletException(e);
        }

        ArrayList<ProjectHistoryRerunConfig> projectHistoryRerunConfigList = new ArrayList<>();
        if (CollectionUtils.isNotEmpty(executionRecovers)) {
            for (ExecutionRecover executionRecover : executionRecovers) {
                ProjectHistoryRerunConfig projectHistoryRerunConfig = new ProjectHistoryRerunConfig();
                projectHistoryRerunConfig.setBegin(executionRecover.getRecoverStartTime());
                projectHistoryRerunConfig.setEnd(executionRecover.getRecoverEndTime());
                Pattern recoverNumRegex = Pattern.compile("\\d+");
                Pattern recoverIntervalRegex = Pattern.compile("[a-z]+");
                String exInterval = executionRecover.getExInterval();
                Matcher recoverNumMatcher = recoverNumRegex.matcher(exInterval);
                Matcher recoverIntervalMatcher = recoverIntervalRegex.matcher(exInterval);
                String recoverNum = "";
                String recoverInterval = "";
                if (recoverNumMatcher.find()) {
                    recoverNum = recoverNumMatcher.group();
                }
                if (recoverIntervalMatcher.find()) {
                    recoverInterval = recoverIntervalMatcher.group();
                }
                projectHistoryRerunConfig.setRecoverInterval(recoverInterval);
                projectHistoryRerunConfig.setRecoverNum(recoverNum);
                projectHistoryRerunConfig.setRunDateTimeList(executionRecover.getRunDateTimeList());
                projectHistoryRerunConfig.setSkipDateTimeList(executionRecover.getSkipDateTimeList());
                projectHistoryRerunConfig.setRecoverId(executionRecover.getRecoverId());
                projectHistoryRerunConfig.setSubmitUser(executionRecover.getSubmitUser());
                projectHistoryRerunConfig.setSubmitTime(executionRecover.getSubmitTime());
                projectHistoryRerunConfig.setStartTime((String) executionRecover.getOtherOption().get("repeatExecuteTimeBegin"));
                projectHistoryRerunConfig.setEndTime((String) executionRecover.getOtherOption().get("repeatExecuteTimeEnd"));
                projectHistoryRerunConfig.setTaskSize(executionRecover.getTaskSize());
                projectHistoryRerunConfig.setTaskDistributeMethod(executionRecover.getTaskDistributeMethod());

                projectHistoryRerunConfigList.add(projectHistoryRerunConfig);
            }
        }
        ret.put("projectHistoryRerunConfigList", projectHistoryRerunConfigList);
        ret.put("total", total);
        ret.put("size", size);
        ret.put("page", page);
    }

    private void ajaxUpdateProjectChangeOwnerStatus(Project project, User user,
                                                    HttpServletRequest req, HashMap<String, Object> ret) {

        try {
            int status = getIntParam(req, "status");
            int i = this.projectManager.updateProjectChangeOwnerStatus(project, status);

            if (i == 1) {
                ret.put("code", 200);
                ret.put("message",
                        "update project{" + project.getName() + "} change owner status successfully");
            } else if (i == 0) {
                ret.put("code", 404);
                ret.put("message", "can not find records for changing owner of project{" +
                        project.getName() + "}");
            }
        } catch (Exception e) {
            logger.error(user.getUserId() + " failed to update project{" + project.getName()
                    + "} change owner status: "
                    + e.getMessage());
            ret.put("code", 400);
            ret.put("error",
                    "failed to update project{" + project.getName() + "} change owner status: "
                            + e.getMessage());
        }
    }

    private String handleProjectHourlyMetrics() {
        String message = this.projectManager.handleHourlyReport();
        return message;
    }


    private void ajaxGetProjectChangeOwnerInfo(Project project, User user, HttpServletRequest req,
                                               HashMap<String, Object> ret) {
        ProjectChangeOwnerInfo projectChangeOwnerInfo;
        try {
            projectChangeOwnerInfo = this.projectManager.getProjectChangeOwnerInfo(project);
            if (projectChangeOwnerInfo != null) {
                ret.put("code", 200);
                ret.put("itsmNo", projectChangeOwnerInfo.getItsmNo());
                ret.put("status", projectChangeOwnerInfo.getStatus());
                ret.put("newOwner", projectChangeOwnerInfo.getNewOwner());
            } else {
                ret.put("code", 404);
                ret.put("itsmNo", null);
                ret.put("status", null);
                ret.put("newOwner", null);
            }

            ret.put("fromType", project.getFromType());
        } catch (SQLException e) {
            logger.error("failed to get ProjectChangeOwnerInfo, project:" + project.getName(), e);
            ret.put("code", 400);
            ret.put("error", "failed to get ProjectChangeOwnerInfo, project: " + project.getName());
        }
    }

    private void requestToItsm4ExchangeProjectOwner(HttpServletRequest req,
                                                    HashMap<String, Object> ret, Session session) {

        try {
            Props prop = getApplication().getServerProps();
            User user = session.getUser();

            String changeMapReq = getParam(req, "changeMap");
            Map<String, String> changeMap = GsonUtils.json2Map(changeMapReq);

            if (changeMap.size() > exchangeProjectLimit) {
                ret.put("error",
                        "the number of exchange project must not exceed " + exchangeProjectLimit);
                return;
            }

            // 判断新 owner 用户是否存在
            List<Project> projectList = new ArrayList<>();
            ProjectChangeOwnerInfo projectChangeOwnerInfo;
            for (Entry<String, String> entry : changeMap.entrySet()) {
                String projectName = entry.getKey();
                String newOwner = entry.getValue();
                Project project = this.projectManager.getProject(projectName);

                // 空项目处理
                if (CollectionUtils.isEmpty(project.getAllRootFlows())) {
                    ret.put("error",
                            "存在空项目（没有工作流的项目） " + projectName + "，请重新选择项目");
                    return;
                }

                // 项目正在交接审批中，不可再次发起交接
                projectChangeOwnerInfo = this.projectManager.getProjectChangeOwnerInfo(project);
                if (projectChangeOwnerInfo != null) {
                    // 从 ITSM 获取审批状态
                    long itsmNo = projectChangeOwnerInfo.getItsmNo();
                    ItsmUtil.getRequestFormStatus(prop, itsmNo, ret);

                    if (ret.containsKey("requestStatus")) {
                        int requestStatus = (int) ret.get("requestStatus");
                        if (requestStatus != 1009 && requestStatus != 1013
                                && requestStatus != 1001) {
                            // 1009 —— 验收中，1013 —— 已完成，驳回可以重新提单 1001 —— 待确认
                            ret.put("error", "项目【" + projectName + "】 正在交接审批中，不可再次发起交接! "
                                    + "ITSM 服务请求单号 " + itsmNo);
                            return;
                        }
                    } else {
                        ret.put("error", "未获取到 ITSM 服务请求单 " + itsmNo + " 的状态，请确认");
                        return;
                    }
                }

                User newOwnerUser = this.systemUserManager.getUser(newOwner);
                if (newOwnerUser == null) {
                    ret.put("error", "New owner " + newOwner + " does not exist!");
                    return;
                }
                projectList.add(project);
            }

            // 判断新 owner 是否属于同一个部门
            Collection<String> newOwners = changeMap.values();
            ArrayList<WtssUser> newWtssOwnerList = new ArrayList<>();
            for (String newOwner : newOwners) {
                WtssUser newWtssOwner = this.transitionService.getSystemUserByUserName(newOwner);
                newWtssOwnerList.add(newWtssOwner);
            }

            long count = newWtssOwnerList.stream().map(WtssUser::getDepartmentId).distinct()
                    .count();

            if (count != 1) {
                ret.put("error", "项目新用户不属于同一个部门");
                return;
            }

            String baseOldOwnerName = projectList.get(0).getCreateUser();
            // 新用户拆分，并行审批
            StringBuilder stringBuilder = new StringBuilder();
            for (int i = 0; i < newWtssOwnerList.size(); i++) {
                stringBuilder.append(newWtssOwnerList.get(i).getUsername());
                if (i < newWtssOwnerList.size() - 1) {
                    stringBuilder.append(",");
                }
            }
            String baseNewOwner = stringBuilder.toString();

            String itsmUrl = prop.getString("itsm.url");
            String itsmInsertRequestUri = prop.getString("itsm.insertRequest.uri");
            String appId = prop.getString("itsm.appId");
            String appKey = prop.getString("itsm.appKey");
            String itsmUserId = prop.getString("itsm.userId");
            String itsmFormId = prop.getString("itsm.project.exchange.form.id");
            String handler = prop.getString("itsm.request.handler");
            String env = prop.getString("itsm.request.env");

            String requestDesc = getParam(req, "requestDesc", "申请 WTSS 项目交接");

            ItsmUtil.sendRequest2Itsm4ChangeProjectOwner(itsmUrl + itsmInsertRequestUri,
                    user.getUserId(), changeMap, ret, appId, appKey, itsmUserId, baseOldOwnerName,
                    baseNewOwner, itsmFormId, handler, env, requestDesc);

            if (ret.containsKey("error")) {
                return;
            }

            long itsmNo = (long) ret.get("itsmNo");
            Map<String, String> dataMap = loadProjectManagerServletI18nData();
            for (Entry<String, String> entry : changeMap.entrySet()) {
                String projectName = entry.getKey();
                String newOwner = entry.getValue();
                Project project = this.projectManager.getProject(projectName);
                this.projectManager.updateProjectChangeOwnerInfo(itsmNo, project, newOwner,
                        user);

                // 如果交接人没有项目管理员权限，则添加交接人为项目管理员
                User newOwnerUser = this.systemUserManager.getUser(newOwner);
                if (!project.checkPermission(newOwnerUser, Type.ADMIN)) {
                    project.removeUserPermission(newOwnerUser.getUserId());
                    WtssUser newWtssOwner = this.transitionService.getSystemUserByUserName(newOwner);
                    executeAddProjectAdmin(newWtssOwner, project, ret, dataMap, user);
                }
            }

        } catch (Exception e) {
            ret.put("error", e.getMessage());
        }
    }

    private void ajaxGetLineageBusiness(Project project, User user, HttpServletRequest req,
                                        HashMap<String, Object> ret) {
        try {
            Props prop = getApplication().getServerProps();
            String jobCodePrefix = prop.getString(Constants.JobProperties.JOB_BUS_PATH_CODE_PREFIX);
            String flowName = getParam(req, "flowName");
            String projectName = project.getName();
            String jobName = getParam(req, "jobName", "");
            String searchType = "FLOW_FIND_DATASET";
            StringBuilder jobCode = new StringBuilder(jobCodePrefix).append('/')
                    .append(projectName.toLowerCase()).append('/')
                    .append(flowName.toLowerCase());
            if (!"".equals(jobName)) {
                jobCode.append('/').append(jobName.toLowerCase());
                searchType = "JOB_FIND_DATASET";
            }
            int pageNum = getIntParam(req, "pageNum", 1);
            int pageSize = getIntParam(req, "pageSize", 10);
            // IN / OUT 输入/输出数据
            String searchDateType = getParam(req, "searchDataType");

            String userId = user.getUserId();
            if (userId.startsWith("WTSS_")) {
                HttpUtils.getLineageBusiness(user.getNormalUser(), prop,
                        jobCode.toString(), searchType, ret, pageNum, pageSize, searchDateType);
            } else {
                HttpUtils.getLineageBusiness(userId, prop,
                        jobCode.toString(), searchType, ret, pageNum, pageSize, searchDateType);
            }

        } catch (Exception e) {
            logger.error("Failed to get lineage business data. " + e.getMessage());
            ret.put("error", e.getMessage());
        }
    }

    private void ajaxFetchProjectCreator(Project project, HttpServletRequest req,
                                         HashMap<String, Object> ret) {
        ret.put("creator", project.getCreateUser());
    }

    private void ajaxChangeProjectOwner(Project project, User user, HttpServletRequest req,
                                        HashMap<String, Object> ret) {

        if (!hasParam(req, "newOwner")) {
            ret.put("error", "missing request parameter 'newOwner'");
            return;
        }

        String newOwnerName = req.getParameter("newOwner");
        if (newOwnerName == null || newOwnerName.trim().isEmpty()) {
            ret.put("error", "Empty new owner name! ");
            return;
        }

        if (project.getProjectLock() == 1) {
            ret.put("error", "Project[" + project.getName() + "] is locked ");
            return;
        }

        try {
            WtssUser newWtssOwner = this.transitionService.getSystemUserByUserName(newOwnerName);
            if (newWtssOwner == null) {
                ret.put("error", "Unknown user ");
                return;
            }
            String oldOwnerName = project.getCreateUser();
            // 老用户可能不存在，即为 null
            WtssUser oldWtssOwner = this.transitionService
                    .getSystemUserByUserName(oldOwnerName);

            project.setProjectLock(1);
            this.projectManager.updateProjectLock(project);
            // change project creator
            if (!newOwnerName.equals(oldOwnerName)) {
                this.projectManager.updateProjectCreateUser(project, newOwnerName, user);
                logger.info("Change creator of WTSS project[" + project.getName() + "] from " +
                        oldOwnerName + " to " + newOwnerName);
                // DSS 项目处理
                if ("DSS".equals(project.getFromType())) {
                    Props prop = getApplication().getServerProps();
                    String linkisServerUrl = prop.getString(ConfigurationKeys.LINKIS_SERVER_URL)
                            + prop.getString(ConfigurationKeys.DSS_TRANSFER_PROJECT_URI,
                            "/api/rest_j/v1/dss/framework/project/transferProject");
                    String linkisToken = prop.getString(ConfigurationKeys.WTSS_LINKIS_TOKEN);
                    Map<String, Object> dssRetMap = DssUtils.transferDssProject(
                            linkisServerUrl, oldOwnerName, linkisToken, newOwnerName,
                            project.getName());
                    int dssRetStatus = (int) dssRetMap.get("dssRetStatus");
                    String dssRetMessage = (String) dssRetMap.get("dssRetMessage");
                    if (dssRetStatus == 0) {
                        logger.info(
                                "Change creator of DSS project[" + project.getName() + "] from " +
                                        oldOwnerName + " to " + newOwnerName + ", DSS return message: "
                                        + dssRetMessage);
                    } else {
                        ret.put("error",
                                "向 DSS 发起项目交接失败，失败原因：" + dssRetMessage);
                        return;
                    }
                }
            }

            if (null != oldWtssOwner) {
                // update proxy users of system user
                String oldProxyUsers = oldWtssOwner.getProxyUsers();
                Set<String> oldProxyUserSet = new HashSet<>(
                        Arrays.asList(oldProxyUsers.split(",")));

                String newProxyUsers = newWtssOwner.getProxyUsers();
                Set<String> newProxyUserSet = new HashSet<>(
                        Arrays.asList(newProxyUsers.split(",")));

                for (String proxyUser : oldProxyUserSet) {
                    if (!newProxyUserSet.contains(proxyUser)) {
                        newProxyUserSet.add(proxyUser);
                    }
                }
                newWtssOwner.setProxyUsers(String.join(",", newProxyUserSet));
                int updateResult = this.systemManager.updateSystemUser(newWtssOwner);
                if (updateResult != 1) {
                    ret.put("error", "Update proxy user failed. ");
                    project.setProjectLock(0);
                    this.projectManager.updateProjectLock(project);
                    return;
                }
                logger.info("Change proxy users of new owner[" + newOwnerName + "]");
            }

            // change proxy users of job
            List<Flow> flowList = project.getFlows();
            for (Flow flow : flowList) {
                Collection<Node> nodes = flow.getNodes();
                for (Node node : nodes) {
                    Props jobPropJobSource = this.projectManager
                            .getProperties(project, flow, node.getId(), node.getJobSource());
                    Props jobPropsJobId = this.projectManager
                            .getProperties(project, flow, node.getId(),
                                    node.getId() + Constants.JOB_OVERRIDE_SUFFIX);
                    if (jobPropJobSource == null) {
                        jobPropJobSource = new Props();
                    }
                    if (jobPropsJobId == null) {
                        jobPropsJobId = new Props();
                    }
                    if (jobPropJobSource.containsKey("user.to.proxy")) {
                        String proxyUserJobSource = jobPropJobSource.getString("user.to.proxy");
                        if (oldOwnerName.equals(proxyUserJobSource)) {
                            jobPropJobSource.put("user.to.proxy", newOwnerName);
                            this.projectManager
                                    .setJobOverrideProperty(project, flow, jobPropJobSource,
                                            node.getId(),
                                            node.getJobSource(), user);
                        }
                    }

                    if (jobPropsJobId.containsKey("user.to.proxy")) {
                        String proxyUserJobId = jobPropsJobId.getString("user.to.proxy");
                        if (oldOwnerName.equals(proxyUserJobId)) {
                            jobPropsJobId.put("user.to.proxy", newOwnerName);
                            this.projectManager
                                    .setJobOverrideProperty(project, flow, jobPropsJobId, node.getId(),
                                            node.getJobSource(), user);
                        }
                    }
                }
            }
            logger.info("Change proxy user of jobs for project[" + project.getName() + "]");

            // change submitter of scheduled flow
            int projectId = project.getId();
            for (Flow flow : flowList) {
                Schedule schedule = this.scheduleManager.getSchedule(projectId, flow.getId());
                EventSchedule eventSchedule = this.eventScheduleService
                        .getEventSchedule(projectId, flow.getId());

                if (schedule != null) {
                    if (oldOwnerName.equals(schedule.getSubmitUser())) {
                        this.scheduleManager
                                .cronScheduleFlow(schedule.getScheduleId(), projectId,
                                        project.getName(),
                                        flow.getId(), schedule.getStatus(), schedule.getFirstSchedTime(),
                                        schedule.getEndSchedTime(),
                                        schedule.getTimezone(), DateTime.now().getMillis(),
                                        schedule.getNextExecTime(),
                                        schedule.getSubmitTime(), newOwnerName,
                                        schedule.getExecutionOptions(),
                                        schedule.getSlaOptions(), schedule.getCronExpression(),
                                        schedule.getOtherOption(), schedule.getLastModifyConfiguration(),
                                        schedule.getComment());
                    }
                }
                if (eventSchedule != null) {
                    if (oldOwnerName.equals(eventSchedule.getSubmitUser())) {
                        eventSchedule.setSubmitUser(newOwnerName);
                        this.eventScheduleService.updateEventSchedule(eventSchedule);
                    }
                }
            }
            logger
                    .info("Change submitter of schedules for project[" + project.getName() + "] from " +
                            oldOwnerName + " to " + newOwnerName);

            // change owner of user variable
            // 获取老用户拥有的用户参数
            UserVariable userVariable = new UserVariable();
            userVariable.setOwner(oldOwnerName);
            List<UserVariable> userVariables = this.userParamsService.fetchAllUserVariable(
                    userVariable);
            // 将老用户的用户参数转交给新用户
            for (UserVariable variable : userVariables) {
                variable.setOwner(newOwnerName);
                this.userParamsService.updateUserVariable(variable, variable.getKey());
            }
            logger.info("Change owner user variable from " + oldOwnerName + " to " + newOwnerName);
            ret.put("message",
                    "Change owner for project[" + project.getName() + "] from " + oldOwnerName +
                            " to " + newOwnerName + " successfully! ");


        } catch (SystemUserManagerException | UserManagerException e) {
            ret.put("error", "Get User failed! Reason: " + e.getMessage());
        } catch (ScheduleManagerException e) {
            ret.put("error", "Update schedule failed! Reason: " + e.getMessage());
        } catch (Exception e) {
            ret.put("error", "Change project creator failed! Reason: " + e.getMessage());
        } finally {
            project.setProjectLock(0);
            this.projectManager.updateProjectLock(project);
        }

    }

    /**
     * Get ITSM list data
     *
     * @param req
     * @param ret
     * @param session
     */
    private void ajaxGetItsmListData4Aomp(HttpServletRequest req, HashMap<String, Object> ret,
                                          Session session) {

        String itsmUrl = getApplication().getServerProps().getString("itsm.url") + "/itsm/api/operateCI/itsmListData4Aomp.any";
        String appId = getApplication().getServerProps().getString("itsm.appId");
        String appKey = getApplication().getServerProps().getString("itsm.appKey");
        String userId = getApplication().getServerProps().getString("itsm.userId");
        User user = session.getUser();
        if (user == null) {
            ret.put("error", "No User!");
            return;
        }
        String userName = user.getUserId();
        String keyword = req.getParameter("keyword");
        int currentPage = Integer.parseInt(req.getParameter("currentPage"));

        try {
            ItsmUtil.getListData4Aomp(itsmUrl, userName, keyword, currentPage, ret, appId, appKey, userId);
        } catch (Exception e) {
            logger.error("Can not get ITSM information, caused by: " + e.getMessage());
        }
    }

    /**
     * 读取flowpage.vm及其子页面的国际化资源数据
     *
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

        if ("zh_CN".equalsIgnoreCase(languageType)) {
            // 添加国际化标签
            flowpageMap = LoadJsonUtils.transJson("/conf/azkaban-web-server-zh_CN.json",
                    "azkaban.webapp.servlet.velocity.flowpage.vm");
            subPageMap1 = LoadJsonUtils.transJson("/conf/azkaban-web-server-zh_CN.json",
                    "azkaban.webapp.servlet.velocity.flow-schedule-ecution-panel.vm");
            subPageMap2 = LoadJsonUtils.transJson("/conf/azkaban-web-server-zh_CN.json",
                    "azkaban.webapp.servlet.velocity.messagedialog.vm");
            subPageMap3 = LoadJsonUtils.transJson("/conf/azkaban-web-server-zh_CN.json",
                    "azkaban.webapp.servlet.velocity.slapanel.vm");
            subPageMap4 = LoadJsonUtils.transJson("/conf/azkaban-web-server-zh_CN.json",
                    "azkaban.webapp.servlet.velocity.nav.vm");
        } else {
            // 添加国际化标签
            flowpageMap = LoadJsonUtils.transJson("/conf/azkaban-web-server-en_US.json",
                    "azkaban.webapp.servlet.velocity.flowpage.vm");
            subPageMap1 = LoadJsonUtils.transJson("/conf/azkaban-web-server-en_US.json",
                    "azkaban.webapp.servlet.velocity.flow-schedule-ecution-panel.vm");
            subPageMap2 = LoadJsonUtils.transJson("/conf/azkaban-web-server-en_US.json",
                    "azkaban.webapp.servlet.velocity.messagedialog.vm");
            subPageMap3 = LoadJsonUtils.transJson("/conf/azkaban-web-server-en_US.json",
                    "azkaban.webapp.servlet.velocity.slapanel.vm");
            subPageMap4 = LoadJsonUtils.transJson("/conf/azkaban-web-server-en_US.json",
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
     *
     * @return
     */
    private Map<String, String> loadProjectManagerServletI18nData() {
        String languageType = LoadJsonUtils.getLanguageType();
        Map<String, String> dataMap;
        if ("zh_CN".equalsIgnoreCase(languageType)) {
            dataMap = LoadJsonUtils.transJson("/conf/azkaban-web-server-zh_CN.json",
                    "azkaban.webapp.servlet.ProjectManagerServlet");
        } else {
            dataMap = LoadJsonUtils.transJson("/conf/azkaban-web-server-en_US.json",
                    "azkaban.webapp.servlet.ProjectManagerServlet");
        }
        return dataMap;
    }


    /**
     * 检查修改项目用户权限
     * 判断是否具有更新权限 1:允许, 2:不允许
     *
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
                    logger.info("current user update project user permission flag is updateProUserFlag=" + updateProUserFlag);
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
     *
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
     *
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
                    if ("zh-CN".equalsIgnoreCase(lang)) {
                        LoadJsonUtils.setLanguageType("zh_CN");
                    }
                    if ("en_US".equalsIgnoreCase(lang)) {
                        LoadJsonUtils.setLanguageType("en_US");
                    }
                } else {
                    if ("zh_CN".equalsIgnoreCase(languageType)) {
                        LoadJsonUtils.setLanguageType("zh_CN");
                    }
                    if ("en_US".equalsIgnoreCase(languageType)) {
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
                    resultMap.put("error", "current user has no permission");
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
     *                  <p>
     *                  此处校验后移至提交调度时
     */
    private void ajaxCheckUserUpdateScheduleFlowPermission(HttpServletRequest req, HttpServletResponse resp,
                                                           HashMap<String, Object> resultMap, Session session) {
        try {
            if (session != null) {
                final User user = session.getUser();
                resultMap.put("updateScheduleFlowFlag", 1);
//                if (wtss_project_privilege_check) {
//                    int updateScheduleFlowFlag = checkUserOperatorFlag(user);
//                    resultMap.put("updateScheduleFlowFlag", updateScheduleFlowFlag);
//                    logger.info("current user update schedule flow permission flag is updateScheduleFlowFlag=" + updateScheduleFlowFlag);
//                } else {
//                    resultMap.put("updateScheduleFlowFlag", 1);
//                }
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
                resultMap.put("eventScheduleFlowTitle", dataMap.get("eventScheduleFlowTitle"));
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
                List<String> projectNameList = new ArrayList<>();
                if (hasParam(req, "project")) {
                    String project = getParam(req, "project");
                    projectNameList.add(project);
                } else if (hasParam(req, "projects")) {
                    String projects = getParam(req, "projects");
                    String[] projectNameArray = projects.split(",");
                    projectNameList = Arrays.asList(projectNameArray);
                }

                final User user = session.getUser();
                Map<String, String> stringStringMap = loadProjectManagerServletI18nData();
                for (String projectName : projectNameList) {
                    final Project project = getProjectAjaxByPermission(resultMap, projectName, user,
                            Type.SCHEDULE);
                    if (project == null) {
                        resultMap.put("error",
                                stringStringMap.get("permissionForAction") + projectName);
                        resultMap.put("switchScheduleFlowFlag", 3);
                        return;
                    }
                }
                if (wtss_project_privilege_check) {
                    int switchScheduleFlowFlag = checkUserOperatorFlag(user);
                    resultMap.put("switchScheduleFlowFlag", switchScheduleFlowFlag);
                    logger.info(
                            "current user active schedule flow permission flag is switchScheduleFlowFlag="
                                    + switchScheduleFlowFlag);
                } else {
                    resultMap.put("switchScheduleFlowFlag", 1);
                }
            }
        } catch (Exception e) {
            logger.error(
                    "Failed to find current user active schedule flow permission flag, caused by:{}",
                    e);
        }
    }

    protected Project getProjectAjaxByPermission(final Map<String, Object> ret, final String projectName,
                                                 final User user, final Permission.Type type) {
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
     *
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
     *
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
     *
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

    /**
     * 查询工作流版本
     *
     * @param project
     * @param req
     * @param ret
     * @throws ServletException
     */
    private void ajaxFetchProjectVersion(final Project project,
                                         final HttpServletRequest req, final HashMap<String, Object> ret) throws ServletException {
        final int num = this.getIntParam(req, "size", 1000);
        final int skip = this.getIntParam(req, "skip", 0);

        final List<ProjectVersion> versionList;
        try {
            versionList = this.projectManager.getProjectVersions(project, num, skip);
        } catch (final ProjectManagerException e) {
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
            flowInfo.put("flowType", flow.getFlowType());
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
        final Project project;
        int projectId = -1;
        if (hasParam(req, "projectId")) {
            projectId = getIntParam(req, "projectId");
            logger.info("inactive project: {}", projectId);
            project = this.projectManager.getInactiveProject(projectId);
        } else {
            project = this.projectManager.getProject(projectName);
        }
        if (project == null) {
            this.setErrorMessageInCookie(resp, "Project " + projectName
                    + " doesn't exist.");
            // resp.sendRedirect(req.getContextPath()+"/index");
            resp.sendRedirect(req.getRequestURI() + "?project=" + projectName);
            return;
        }

        if (!hasPermission(project, user, Type.READ)) {
            this.setErrorMessageInCookie(resp, "No permission to download project " + projectName
                    + ".");
            // resp.sendRedirect(req.getContextPath());
            resp.sendRedirect(req.getRequestURI() + "?project=" + projectName);
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
                this.setErrorMessageInCookie(resp, "workflow doesn't exist");
                resp.sendRedirect(req.getRequestURI() + "?project=" + projectName);
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

            resp.setContentType(Constants.APPLICATION_ZIP_MIME_TYPE);

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

            for (final EventSchedule schedule : this.eventScheduleService.getAllEventSchedules()) {
                if (schedule.getProjectId() == project.getId()) {
                    logger.info("removing event schedule " + schedule.getScheduleId());
                    this.eventScheduleService.removeEventSchedule(schedule);
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
                    ret.put("hasSchedule", "Time Schedule");
                    break;
                }
            }

            for (final EventSchedule schedule : this.eventScheduleService.getAllEventSchedules()) {
                if (schedule.getProjectId() == project.getId()) {
                    ret.put("hasSchedule", "Event Schedule");
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
        HashMap<String, String> ret = new HashMap<>();

        final Project project = this.projectManager.getProject(projectName);
        if (project == null) {
            ret.put("message", "Project " + projectName + " doesn't exist.");
            ret.put("status", "error");
            this.writeJSON(resp, ret);
            return;
        }

        if (!hasPermission(project, user, Type.ADMIN)) {
            ret.put("message", "Cannot delete. User '" + user.getUserId() + "' is not an ADMIN.");
            ret.put("status", "error");
            this.writeJSON(resp, ret);
            return;
        }

        // FIXME Added the judgment that if the job stream is running, the project cannot be deleted.
        List<Flow> runningFlows = this.projectManager.getRunningFlow(project);
        if (runningFlows != null && runningFlows.size() != 0) {
            ret.put("message", "工作流: " + runningFlows.stream()
                    .map(Flow::getId).collect(Collectors.toList()).toString() + " 没有结束, 不能删除该工程.");
            ret.put("status", "error");
            this.writeJSON(resp, ret);
            return;
        }

        removeAssociatedSchedules(project);

        try {
            this.projectManager.removeProject(project, user);
        } catch (final ProjectManagerException e) {
            ret.put("message", e.getMessage());
            ret.put("status", "error");
            this.writeJSON(resp, ret);
            return;
        }

        ret.put("status", "success");
        this.writeJSON(resp, ret);
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


    private void ajaxChangeCreateUser(final Project project,
                                      final HashMap<String, Object> ret, final HttpServletRequest req, final User user)
            throws ServletException {
        try {
            final String newCreateUser = getParam(req, "createUser");
            Objects.requireNonNull(newCreateUser, "User creator cannot be empty.");
            this.projectManager.updateProjectCreateUser(project, newCreateUser, user);
        } catch (final Exception e) {
            ret.put("error", e.getMessage());
        }
    }

    private void ajaxChangePrincipal(final Project project,
                                     final HashMap<String, Object> ret, final HttpServletRequest req, final User user) {
        try {
            final String newPrincipal = getParam(req, "principal");
            if (StringUtils.isNotEmpty(newPrincipal)) {
                if (newPrincipal.length() > Constants.PRINCIPAL_MAX_LENGTH) {
                    ret.put("error", "The maximum length of the input value cannot exceed 64");
                    return;
                }

                String regex = "[\u4e00-\u9fa5]";
                // 使用正则表达式进行匹配
                Pattern pattern = Pattern.compile(regex);
                Matcher matcher = pattern.matcher(newPrincipal);
                // 如果找到匹配的汉字，则返回true
                if (matcher.find()) {
                    ret.put("error", "The input value cannot have Chinese characters");
                    return;
                }

            }
            this.projectManager.updateProjectPrincipal(project, newPrincipal, user);
        } catch (final Exception e) {
            ret.put("error", e.getMessage());
        }
    }

    private void ajaxChangeJobLimit(final Project project,
                                    final HashMap<String, Object> ret, final HttpServletRequest req, final User user)
            throws ServletException {
        try {
            final int jobLimit = getIntParam(req, "jobLimit", 0);
            this.projectManager.updateJobLimit(project, jobLimit, user);
        } catch (final Exception e) {
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

            nodeObj.put("comment", node.getComment());

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

        if ("flow".equals(node.getType())) {
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
                    if (wtss_dep_upload_privilege_check && "personal".equals(wtssUser.getUserCategory()) && (write || execute || schedule)) {
                        throw new SystemUserManagerException(dataMap.get("cannotpermitpersonalwriteexecsch"));
                    }
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
                                    if (USER_ID_PATTERN.matcher(userId).matches()) {
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

        final Page page = newPage(req, resp, session, "azkaban/webapp/servlet/velocity/projectlogpage.vm");

        Map<String, String> projectlogpageMap;
        Map<String, String> subPageMap1;
        Map<String, String> subPageMap2;
        Map<String, String> subPageMap3;
        Map<String, String> subPageMap4;
        Map<String, String> subPageMap5;

        String languageType = LoadJsonUtils.getLanguageType();
        if ("zh_CN".equalsIgnoreCase(languageType)) {

            // 添加国际化标签
            projectlogpageMap = LoadJsonUtils.transJson("/conf/azkaban-web-server-zh_CN.json",
                    "azkaban.webapp.servlet.velocity.projectlogpage.vm");
            subPageMap1 = LoadJsonUtils.transJson("/conf/azkaban-web-server-zh_CN.json",
                    "azkaban.webapp.servlet.velocity.nav.vm");
            subPageMap2 = LoadJsonUtils.transJson("/conf/azkaban-web-server-zh_CN.json",
                    "azkaban.webapp.servlet.velocity.projectpageheader.vm");
            subPageMap3 = LoadJsonUtils.transJson("/conf/azkaban-web-server-zh_CN.json",
                    "azkaban.webapp.servlet.velocity.projectnav.vm");
            subPageMap4 = LoadJsonUtils.transJson("/conf/azkaban-web-server-zh_CN.json",
                    "azkaban.webapp.servlet.velocity.projectsidebar.vm");
            subPageMap5 = LoadJsonUtils.transJson("/conf/azkaban-web-server-zh_CN.json",
                    "azkaban.webapp.servlet.velocity.projectmodals.vm");
        } else {
            // 添加国际化标签
            projectlogpageMap = LoadJsonUtils.transJson("/conf/azkaban-web-server-en_US.json",
                    "azkaban.webapp.servlet.velocity.projectlogpage.vm");
            subPageMap1 = LoadJsonUtils.transJson("/conf/azkaban-web-server-en_US.json",
                    "azkaban.webapp.servlet.velocity.nav.vm");
            subPageMap2 = LoadJsonUtils.transJson("/conf/azkaban-web-server-en_US.json",
                    "azkaban.webapp.servlet.velocity.projectpageheader.vm");
            subPageMap3 = LoadJsonUtils.transJson("/conf/azkaban-web-server-en_US.json",
                    "azkaban.webapp.servlet.velocity.projectnav.vm");
            subPageMap4 = LoadJsonUtils.transJson("/conf/azkaban-web-server-en_US.json",
                    "azkaban.webapp.servlet.velocity.projectsidebar.vm");
            subPageMap5 = LoadJsonUtils.transJson("/conf/azkaban-web-server-en_US.json",
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
        PageUtils.hideUploadButtonWhenNeeded(page, session, this.lockdownUploadProjects,
                uploadDisplaySwitch);
        Project project = null;
        try {
            if (hasParam(req, "projectId")) {
                int projectId = getIntParam(req, "projectId");
                project = this.projectManager.getProject(projectId);
            } else {
                project = this.projectManager.getProject(projectName);
            }
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

    private void handleProjectVersionsPage(final HttpServletRequest req, final HttpServletResponse resp,
                                           final Session session) throws ServletException, IOException {

        final Page page = newPage(req, resp, session, "azkaban/webapp/servlet/velocity/projectversionpage.vm");

        Map<String, String> projectVersionPageMap;
        Map<String, String> subPageMap1;
        Map<String, String> subPageMap2;
        Map<String, String> subPageMap3;
        Map<String, String> subPageMap4;
        Map<String, String> subPageMap5;

        String languageType = LoadJsonUtils.getLanguageType();
        if ("zh_CN".equalsIgnoreCase(languageType)) {

            // 添加国际化标签
            projectVersionPageMap = LoadJsonUtils.transJson("/conf/azkaban-web-server-zh_CN.json",
                    "azkaban.webapp.servlet.velocity.projectversionpage.vm");
            subPageMap1 = LoadJsonUtils.transJson("/conf/azkaban-web-server-zh_CN.json",
                    "azkaban.webapp.servlet.velocity.nav.vm");
            subPageMap2 = LoadJsonUtils.transJson("/conf/azkaban-web-server-zh_CN.json",
                    "azkaban.webapp.servlet.velocity.projectpageheader.vm");
            subPageMap3 = LoadJsonUtils.transJson("/conf/azkaban-web-server-zh_CN.json",
                    "azkaban.webapp.servlet.velocity.projectnav.vm");
            subPageMap4 = LoadJsonUtils.transJson("/conf/azkaban-web-server-zh_CN.json",
                    "azkaban.webapp.servlet.velocity.projectsidebar.vm");
            subPageMap5 = LoadJsonUtils.transJson("/conf/azkaban-web-server-zh_CN.json",
                    "azkaban.webapp.servlet.velocity.projectmodals.vm");
        } else {
            // 添加国际化标签
            projectVersionPageMap = LoadJsonUtils.transJson("/conf/azkaban-web-server-en_US.json",
                    "azkaban.webapp.servlet.velocity.projectversionpage.vm");
            subPageMap1 = LoadJsonUtils.transJson("/conf/azkaban-web-server-en_US.json",
                    "azkaban.webapp.servlet.velocity.nav.vm");
            subPageMap2 = LoadJsonUtils.transJson("/conf/azkaban-web-server-en_US.json",
                    "azkaban.webapp.servlet.velocity.projectpageheader.vm");
            subPageMap3 = LoadJsonUtils.transJson("/conf/azkaban-web-server-en_US.json",
                    "azkaban.webapp.servlet.velocity.projectnav.vm");
            subPageMap4 = LoadJsonUtils.transJson("/conf/azkaban-web-server-en_US.json",
                    "azkaban.webapp.servlet.velocity.projectsidebar.vm");
            subPageMap5 = LoadJsonUtils.transJson("/conf/azkaban-web-server-en_US.json",
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
        PageUtils.hideUploadButtonWhenNeeded(page, session, this.lockdownUploadProjects,
                uploadDisplaySwitch);
        Project project = null;
        try {
            if (hasParam(req, "projectId")) {
                int projectId = getIntParam(req, "projectId");
                project = this.projectManager.getProject(projectId);
            } else {
                project = this.projectManager.getProject(projectName);
            }
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

    private void handleJobHistoryPage(final HttpServletRequest req,
                                      final HttpServletResponse resp, final Session session) throws ServletException,
            IOException {
        final Page page =
                newPage(req, resp, session,
                        "azkaban/webapp/servlet/velocity/jobhistorypage.vm");

        String languageType = LoadJsonUtils.getLanguageType();
        Map<String, String> jobhistorypageMap;
        Map<String, String> subPageMap1;
        if ("zh_CN".equalsIgnoreCase(languageType)) {
            // 添加国际化标签
            jobhistorypageMap = LoadJsonUtils.transJson("/conf/azkaban-web-server-zh_CN.json",
                    "azkaban.webapp.servlet.velocity.jobhistorypage.vm");
            subPageMap1 = LoadJsonUtils.transJson("/conf/azkaban-web-server-zh_CN.json",
                    "azkaban.webapp.servlet.velocity.nav.vm");
        } else {
            // 添加国际化标签
            jobhistorypageMap = LoadJsonUtils.transJson("/conf/azkaban-web-server-en_US.json",
                    "azkaban.webapp.servlet.velocity.jobhistorypage.vm");
            subPageMap1 = LoadJsonUtils.transJson("/conf/azkaban-web-server-en_US.json",
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
                        logger.warn("Failed to exec flow {}", executableJobInfo.getExecId(), e);
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
                new PageSelection(String.valueOf(pageStartValue), pageSize, pageStartValue > maxPage,
                        pageStartValue == pageNum, Math.min(pageStartValue, maxPage)));
        pageStartValue++;

        page.add("page2",
                new PageSelection(String.valueOf(pageStartValue), pageSize, pageStartValue > maxPage,
                        pageStartValue == pageNum, Math.min(pageStartValue, maxPage)));
        pageStartValue++;

        page.add("page3",
                new PageSelection(String.valueOf(pageStartValue), pageSize, pageStartValue > maxPage,
                        pageStartValue == pageNum, Math.min(pageStartValue, maxPage)));
        pageStartValue++;

        page.add("page4",
                new PageSelection(String.valueOf(pageStartValue), pageSize, pageStartValue > maxPage,
                        pageStartValue == pageNum, Math.min(pageStartValue, maxPage)));
        pageStartValue++;

        page.add("page5",
                new PageSelection(String.valueOf(pageStartValue), pageSize, pageStartValue > maxPage,
                        pageStartValue == pageNum, Math.min(pageStartValue, maxPage)));

        page.add("currentlangType", languageType);
        page.render();
    }

    //项目权限页面处理方法
    private void handlePermissionPage(final HttpServletRequest req, final HttpServletResponse resp,
                                      final Session session) throws ServletException {

        final Page page = newPage(req, resp, session, "azkaban/webapp/servlet/velocity/permissionspage.vm");

        String languageType = LoadJsonUtils.getLanguageType();
        Map<String, String> permissionspageMap;
        Map<String, String> subPageMap1;
        Map<String, String> subPageMap2;
        Map<String, String> subPageMap3;
        Map<String, String> subPageMap4;
        Map<String, String> subPageMap5;
        if ("en_US".equalsIgnoreCase(languageType)) {
            // 添加国际化标签
            permissionspageMap = LoadJsonUtils.transJson("/conf/azkaban-web-server-en_US.json",
                    "azkaban.webapp.servlet.velocity.permissionspage.vm");
            subPageMap1 = LoadJsonUtils.transJson("/conf/azkaban-web-server-en_US.json",
                    "azkaban.webapp.servlet.velocity.nav.vm");
            subPageMap2 = LoadJsonUtils.transJson("/conf/azkaban-web-server-en_US.json",
                    "azkaban.webapp.servlet.velocity.projectmodals.vm");
            subPageMap3 = LoadJsonUtils.transJson("/conf/azkaban-web-server-en_US.json",
                    "azkaban.webapp.servlet.velocity.projectsidebar.vm");
            subPageMap4 = LoadJsonUtils.transJson("/conf/azkaban-web-server-en_US.json",
                    "azkaban.webapp.servlet.velocity.projectnav.vm");
            subPageMap5 = LoadJsonUtils.transJson("/conf/azkaban-web-server-en_US.json",
                    "azkaban.webapp.servlet.velocity.projectpageheader.vm");
        } else {
            permissionspageMap = LoadJsonUtils.transJson("/conf/azkaban-web-server-zh_CN.json",
                    "azkaban.webapp.servlet.velocity.permissionspage.vm");
            subPageMap1 = LoadJsonUtils.transJson("/conf/azkaban-web-server-zh_CN.json",
                    "azkaban.webapp.servlet.velocity.nav.vm");
            subPageMap2 = LoadJsonUtils.transJson("/conf/azkaban-web-server-zh_CN.json",
                    "azkaban.webapp.servlet.velocity.projectmodals.vm");
            subPageMap3 = LoadJsonUtils.transJson("/conf/azkaban-web-server-zh_CN.json",
                    "azkaban.webapp.servlet.velocity.projectsidebar.vm");
            subPageMap4 = LoadJsonUtils.transJson("/conf/azkaban-web-server-zh_CN.json",
                    "azkaban.webapp.servlet.velocity.projectnav.vm");
            subPageMap5 = LoadJsonUtils.transJson("/conf/azkaban-web-server-zh_CN.json",
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
        PageUtils.hideUploadButtonWhenNeeded(page, session, this.lockdownUploadProjects,
                uploadDisplaySwitch);
        Project project = null;

        Map<String, String> dataMap = loadProjectManagerServletI18nData();
        try {
            if (hasParam(req, "projectId")) {
                int projectId = getIntParam(req, "projectId");
                project = this.projectManager.getProject(projectId);
            } else {
                project = this.projectManager.getProject(projectName);
            }
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
                    } catch (SystemUserManagerException e) {
                        logger.error("get wtssUser failed, caused by: ", e);
                    }
                    if (wtssUser != null && wtssUser.getProxyUsers() != null) {
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

        final Page page = newPage(req, resp, session, "azkaban/webapp/servlet/velocity/jobpage.vm");

        String languageType = LoadJsonUtils.getLanguageType();
        Map<String, String> jobpageMap;
        Map<String, String> subPageMap1;
        if ("zh_CN".equalsIgnoreCase(languageType)) {
            // 添加国际化标签
            jobpageMap = LoadJsonUtils.transJson("/conf/azkaban-web-server-zh_CN.json",
                    "azkaban.webapp.servlet.velocity.jobpage.vm");
            subPageMap1 = LoadJsonUtils.transJson("/conf/azkaban-web-server-zh_CN.json",
                    "azkaban.webapp.servlet.velocity.nav.vm");
        } else {
            // 添加国际化标签
            jobpageMap = LoadJsonUtils.transJson("/conf/azkaban-web-server-en_US.json",
                    "azkaban.webapp.servlet.velocity.jobpage.vm");
            subPageMap1 = LoadJsonUtils.transJson("/conf/azkaban-web-server-en_US.json",
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

        final Page page = newPage(req, resp, session, "azkaban/webapp/servlet/velocity/propertypage.vm");

        String languageType = LoadJsonUtils.getLanguageType();
        Map<String, String> propertypageMap;
        Map<String, String> subPageMap1;
        if ("zh_CN".equalsIgnoreCase(languageType)) {
            // 添加国际化标签
            propertypageMap = LoadJsonUtils.transJson("/conf/azkaban-web-server-zh_CN.json",
                    "azkaban.webapp.servlet.velocity.propertypage.vm");
            subPageMap1 = LoadJsonUtils.transJson("/conf/azkaban-web-server-zh_CN.json",
                    "azkaban.webapp.servlet.velocity.nav.vm");
        } else {
            // 添加国际化标签
            propertypageMap = LoadJsonUtils.transJson("/conf/azkaban-web-server-en_US.json",
                    "azkaban.webapp.servlet.velocity.propertypage.vm");
            subPageMap1 = LoadJsonUtils.transJson("/conf/azkaban-web-server-en_US.json",
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

        final Page page = newPage(req, resp, session, "azkaban/webapp/servlet/velocity/flowpage.vm");

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
        final Page page = newPage(req, resp, session, "azkaban/webapp/servlet/velocity/projectpage.vm");

        String languageType = LoadJsonUtils.getLanguageType();
        Map<String, String> projectpageMap;
        Map<String, String> subPageMap1;
        Map<String, String> subPageMap2;
        Map<String, String> subPageMap3;
        Map<String, String> subPageMap4;
        Map<String, String> subPageMap5;
        Map<String, String> subPageMap6;
        Map<String, String> subPageMap7;
        Map<String, String> subPageMap8;

        if ("zh_CN".equalsIgnoreCase(languageType)) {
            // 添加国际化标签
            projectpageMap = LoadJsonUtils.transJson("/conf/azkaban-web-server-zh_CN.json",
                    "azkaban.webapp.servlet.velocity.projectpage.vm");
            subPageMap1 = LoadJsonUtils.transJson("/conf/azkaban-web-server-zh_CN.json",
                    "azkaban.webapp.servlet.velocity.projectpageheader.vm");
            subPageMap2 = LoadJsonUtils.transJson("/conf/azkaban-web-server-zh_CN.json",
                    "azkaban.webapp.servlet.velocity.projectnav.vm");
            subPageMap3 = LoadJsonUtils.transJson("/conf/azkaban-web-server-zh_CN.json",
                    "azkaban.webapp.servlet.velocity.projectmodals.vm");
            subPageMap4 = LoadJsonUtils.transJson("/conf/azkaban-web-server-zh_CN.json",
                    "azkaban.webapp.servlet.velocity.flow-schedule-ecution-panel.vm");
            subPageMap5 = LoadJsonUtils.transJson("/conf/azkaban-web-server-zh_CN.json",
                    "azkaban.webapp.servlet.velocity.messagedialog.vm");
            subPageMap6 = LoadJsonUtils.transJson("/conf/azkaban-web-server-zh_CN.json",
                    "azkaban.webapp.servlet.velocity.nav.vm");
            subPageMap7 = LoadJsonUtils.transJson("/conf/azkaban-web-server-zh_CN.json",
                    "azkaban.webapp.servlet.velocity.projectsidebar.vm");
            subPageMap8 = LoadJsonUtils.transJson("/conf/azkaban-web-server-zh_CN.json",
                    "azkaban.webapp.servlet.velocity.flow-event-schedule-execution-panel.vm");
        } else {
            // 添加国际化标签
            projectpageMap = LoadJsonUtils.transJson("/conf/azkaban-web-server-en_US.json",
                    "azkaban.webapp.servlet.velocity.projectpage.vm");
            subPageMap1 = LoadJsonUtils.transJson("/conf/azkaban-web-server-en_US.json",
                    "azkaban.webapp.servlet.velocity.projectpageheader.vm");
            subPageMap2 = LoadJsonUtils.transJson("/conf/azkaban-web-server-en_US.json",
                    "azkaban.webapp.servlet.velocity.projectnav.vm");
            subPageMap3 = LoadJsonUtils.transJson("/conf/azkaban-web-server-en_US.json",
                    "azkaban.webapp.servlet.velocity.projectmodals.vm");
            subPageMap4 = LoadJsonUtils.transJson("/conf/azkaban-web-server-en_US.json",
                    "azkaban.webapp.servlet.velocity.flow-schedule-ecution-panel.vm");
            subPageMap5 = LoadJsonUtils.transJson("/conf/azkaban-web-server-en_US.json",
                    "azkaban.webapp.servlet.velocity.messagedialog.vm");
            subPageMap6 = LoadJsonUtils.transJson("/conf/azkaban-web-server-en_US.json",
                    "azkaban.webapp.servlet.velocity.nav.vm");
            subPageMap7 = LoadJsonUtils.transJson("/conf/azkaban-web-server-en_US.json",
                    "azkaban.webapp.servlet.velocity.projectsidebar.vm");
            subPageMap8 = LoadJsonUtils.transJson("/conf/azkaban-web-server-en_US.json",
                    "azkaban.webapp.servlet.velocity.flow-event-schedule-execution-panel.vm");
        }

        projectpageMap.forEach(page::add);
        subPageMap1.forEach(page::add);
        subPageMap2.forEach(page::add);
        subPageMap3.forEach(page::add);
        subPageMap4.forEach(page::add);
        subPageMap5.forEach(page::add);
        subPageMap6.forEach(page::add);
        subPageMap7.forEach(page::add);
        subPageMap8.forEach(page::add);

        final String projectName = getParam(req, "project");

        final User user = session.getUser();

        page.add("loginUser", user.getUserId());

        page.add("currentlangType", languageType);

        PageUtils.hideUploadButtonWhenNeeded(page, session, this.lockdownUploadProjects,
                uploadDisplaySwitch);

        page.add("itsmSwitch", itsmSwitch);
        Project project = null;

        Map<String, String> dataMap = loadProjectManagerServletI18nData();
        try {
            if (hasParam(req, "projectId")) {
                int projectId = getIntParam(req, "projectId");
                project = this.projectManager.getProject(projectId);
            } else {
                project = this.projectManager.getProject(projectName);
            }
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
     *
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
        } else {
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

        try {
            if (wtss_dep_upload_privilege_check) {
                WtssUser wtssUser = this.transitionService.getSystemUserByUserName(user.getUserId());
                if (wtssUser != null && "personal".equals(wtssUser.getUserCategory())) {
                    message = "User " + user.getUserId() + " doesn't have permission to create projects.";
                    status = "error";
                }
            }
        } catch (SystemUserManagerException e) {
            logger.error("query create user error", e);
            message = e.getMessage();
            status = "error";
        }

        if (!"error".equals(status)) {
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

                    // judge where the request to create an project is from
                    String source = req.getParameterMap().containsKey("dssurl") ? "DSS" : "WTSS";
                    this.projectManager.createProject(projectName, projectDescription, projectGroup, user, source);

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
        }

        final String response = createJsonResponse(status, message, action, params);
        try {
            final Writer write = resp.getWriter();
            write.append(response);
            write.flush();
        } catch (final IOException e) {
            logger.error(e.getMessage(), e);
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

        Props serverProps = getApplication().getServerProps();
        boolean itsmSwitch = serverProps.getBoolean("itsm.switch", false);
        String itsmId = (String) multipart.get("itsmId");
        if (itsmSwitch) {
            if (itsmId == null || "".equals(itsmId.replaceAll(" ", ""))) {
                String itsmMessage =
                        "There is no ITSM form related to upload operation! User: " + user.getUserId()
                                + ", Project: " + projectName;
                logger.info(itsmMessage);
                registerError(ret, itsmMessage, resp, 400);
                return;
            }

            project.setItsmId(Long.parseLong(itsmId));
        } else {
            if (StringUtils.isEmpty(itsmId)) {
                project.setItsmId(0L);
            } else {
                project.setItsmId(Long.parseLong(itsmId));
            }
        }

        logger.info(
                "Upload: reference of project " + projectName + " is " + System.identityHashCode(project));

        final String autoFix = (String) multipart.get("fix");

        final Props props = new Props();
        if (autoFix != null && "off".equals(autoFix)) {
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
            if (name.length() > 128 || name.length() <= 0) {
                registerError(ret, dataMap.get("zipFileCannotlength"), resp, 400);
                return;
            }

            String type = null;

            final String contentType = item.getContentType();
            if (contentType != null
                    && (contentType.startsWith(Constants.APPLICATION_ZIP_MIME_TYPE)
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

                if (checkFile(resp, ret, serverProps, dataMap, archiveFile)) return;

                //unscheduleall/scheduleall should only work with flow which has defined flow trigger
                //unschedule all flows within the old project
                if (this.enableQuartz) {
                    //todo chengren311: should maintain atomicity,
                    // e.g, if uploadProject fails, associated schedule shouldn't be added.
                    this.scheduler.unscheduleAll(project);
                }
                Map<String, Boolean> map = this.projectManager.checkFlowName(project, archiveFile, type, props);
                final StringBuffer errorMsgs = new StringBuffer();
                final StringBuffer warnMsgs = new StringBuffer();
                if (!map.get("jobNumResult")) {
                    //(ret, dataMap.get("jobNum"), resp, 200);
                    warnMsgs.append("<ul>");
                    warnMsgs.append("<li>" + dataMap.get("jobNum") + "</li>");
                    warnMsgs.append("</ul>");
                }
                //判断name长度是否大于128
                if (!map.get("flowIdLengthResult")) {
                    //registerError(ret, dataMap.get("jobNamelength"), resp, 200);
                    warnMsgs.append("<ul>");
                    warnMsgs.append("<li>" + dataMap.get("jobNamelength") + "</li>");
                    warnMsgs.append("</ul>");
                }

                // judge where the project is from
                String fromType = "WTSS";
                if (req.getParameterMap().containsKey("dssurl") || multipart.containsKey(
                        "dssurl")) {
                    fromType = "DSS";
                }
                project.setFromType(fromType);


                boolean virtualView = serverProps.getBoolean("datachecker.upload.project.virtual_view.table.deny.switch", false);
                logger.info("virtualView:{}", virtualView);
                if (virtualView) {
                    //校验dataChecker,视图表阻断
                    this.projectManager.checkUpFileDataObject(archiveFile, project, serverProps);
                }

                final Map<String, ValidationReport> reports =
                        this.projectManager.uploadProject(project, archiveFile, type, user,
                                props);

                if (this.enableQuartz) {
                    //schedule the new project
                    this.scheduler.scheduleAll(project, user.getUserId());
                }
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
                    registerError(ret, errorMsgs.toString(), resp, 500);
                    //使用cookie提示错误页面才能显示错误
                    //setErrorMessageInCookie(resp, errorMsgs.toString());
                }
                if (warnMsgs.length() > 0) {
                    ret.put("warn", warnMsgs.toString());
                    //使用cookie提示错误页面才能显示错误
                    //setWarnMessageInCookie(resp, warnMsgs.toString());
                }
                try {

                    addBusinessInfo(req, resp, session, archiveFile, project);
                } catch (Exception e) {

                    registerError(ret, e.getMessage(), resp, 500);
                }


            } catch (final Exception e) {
                logger.info("Installation Failed.", e);
                String error = e.getMessage();
                if (error != null && "MALFORMED".equals(error)) {
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

    private void addBusinessInfo(HttpServletRequest req, HttpServletResponse resp, Session session, File archiveFile, Project project) throws IOException, ServletException {
        ZipFile zipStoreFile = new ZipFile(archiveFile);
        ZipEntry entry = null;
        Enumeration<? extends ZipEntry> entries = zipStoreFile.entries();
        int i = 0;
        while (entries.hasMoreElements()) {
            ZipEntry zipEntry = entries.nextElement();
            String name = zipEntry.getName().toLowerCase();
            if (name.contains("business.json")) {
                //校验文件是否在第一层目录
                String[] split = name.split("/");
                if (split.length > 2) {
                    throw new ServletException("应用信息文件位置放置错误，请检查");
                }
                entry = zipEntry;
                i++;
            }
        }

        if (entry != null && i == 1) {
            InputStream inputStream = zipStoreFile.getInputStream(entry);
            try {
                // 使用Jackson解析JSON
                ObjectMapper mapper = JSONUtils.JacksonObjectMapperFactory.getInstance();
                JSONArray jsonArray = mapper.readValue(inputStream, JSONArray.class);
                if (CollectionUtils.isNotEmpty(jsonArray)) {
                    for (int j = 0; j < jsonArray.size(); j++) {
                        Map<String, String> fileMap = mapper.readValue(jsonArray.getJSONObject(j).toString(), Map.class);
                        if (!fileMap.get("project").equals(project.getName())) {
                            throw new ServletException("项目上传成功,但是应用信息文件中，项目名不一致，请检查");
                        }
                        checkBusinessInfoBeforeIntoDB(req, resp, new HashMap<>(), 0, fileMap, project, "");

                    }
                    for (int k = 0; k < jsonArray.size(); k++) {

                        Map<String, String> fileMap = mapper.readValue(jsonArray.getJSONObject(k).toString(), Map.class);
                        setBusinessInfo(req, resp, session, new HashMap<>(), 0, fileMap, null, Constants.UPLOAD_CHANNEL_TYPE);
                    }
                }

            } catch (Exception e) {
                logger.error("应用信息解析失败,project:{},ERROR:{}", project.getName(), e);
                throw new ServletException("应用信息解析失败: " + e.getMessage());
            }finally {
                IOUtils.closeQuietly(zipStoreFile);
                IOUtils.closeQuietly(inputStream);

            }
        }

        if (i > 1) {
            throw new ServletException("应用信息文件数量只能为1，请检查");
        }

    }

    private boolean checkFile(HttpServletResponse resp, Map<String, String> ret, Props serverProps, Map<String, String> dataMap, File file) throws IOException {
        //检查文件大小和文件数
        long fileLength = serverProps.getLong(WTSS_PROJECT_FILE_UPLOAD_LENGTH, 500 * 1024 * 1024);
        long fileCount = serverProps.getLong(WTSS_PROJECT_FILE_UPLOAD_COUNT, 5000);
        if (file.length() > fileLength) {
            registerError(ret, dataMap.get("zipFileLengthExceedsLimit") + " " + fileLength, resp, 400);
            return true;
        }
        long count;
        ZipFile zipStoreFile = null;
        try {
            zipStoreFile = new ZipFile(file);
            Enumeration<? extends ZipEntry> entries = zipStoreFile.entries();
            count = 0;
            while (entries.hasMoreElements()) {
                ZipEntry zipEntry = entries.nextElement();
                if (!zipEntry.isDirectory()) {
                    count++;
                }
            }
        } finally {
            IOUtils.closeQuietly(zipStoreFile);
        }
        if (count > fileCount) {
            registerError(ret, dataMap.get("zipFileCountExceedsLimit") + " " + fileCount, resp, 400);
            return true;
        }
        return false;
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
                                           final Permission.Type type) {
        final Permission perm = project.getCollectivePermission(user);
        for (final String roleName : user.getRoles()) {
            if ("admin".equals(roleName) || systemManager.isDepartmentMaintainer(user)) {
                perm.addPermission(Type.ADMIN);
            }
        }

        return perm;
    }

    private void handleReloadProjectWhitelist(final HttpServletRequest req,
                                              final HttpServletResponse resp, final Session session) throws IOException {
        final HashMap<String, Object> ret = new HashMap<>();

        if (hasPermission(session.getUser(), Permission.Type.ADMIN)) {
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

    protected boolean hasPermission(final User user, final Permission.Type type) {
        for (final String roleName : user.getRoles()) {
            //final Role role = this.userManager.getRole(roleName);
            final Role role = user.getRoleMap().get(roleName);
            if (role != null && role.getPermission().isPermissionSet(type)
                    || role.getPermission().isPermissionSet(Permission.Type.ADMIN)) {
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
                            logger.warn("Failed to exec flow {}", executableJobInfo.getExecId(), e);
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
            if (job.getFlowType() == 0) {
                flowInfo.put("flowType", SINGLE_EXECUTION);
            } else if (job.getFlowType() == 2) {
                flowInfo.put("flowType", HISTORICAL_RERUN);
            } else if (job.getFlowType() == 3) {
                flowInfo.put("flowType", TIMED_SCHEDULING);
            } else if (job.getFlowType() == 4) {
                flowInfo.put("flowType", CYCLE_EXECUTION);
            } else {
                flowInfo.put("flowType", EVENT_SCHEDULE);
            }
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
            if ((hasParam(req, "advfilter") && Boolean.parseBoolean(getParam(req, "advfilter")))
                    || (hasParam(req, "preciseSearch") && Boolean.parseBoolean(getParam(req, "preciseSearch")))) {
                // 如果是模糊搜索或者精确搜索
                HistoryQueryParam historyQueryParam = new HistoryQueryParam();
                historyQueryParam.setProjectName(project.getName());
                historyQueryParam.setFlowId(flowId);
                historyQueryParam.setExecIdContain(getParam(req, "execIdcontain").trim());
                historyQueryParam.setUserContain(getParam(req, "usercontain").trim());
                historyQueryParam.setComment(getParam(req, "comment").trim());
                String status = getParam(req, "status");
                String[] statusArray = status.split(",");
                StringBuilder statusNumber = new StringBuilder();
                for (int i = 0; i < statusArray.length; i++) {
                    if (NumberUtils.isParsable(statusArray[i])) {
                        if (i < (statusArray.length - 1)) {
                            statusNumber.append(statusArray[i]).append(",");
                        } else {
                            statusNumber.append(statusArray[i]);
                        }
                    }
                }
                status = statusNumber.toString();
                historyQueryParam.setStatus(status);
                String startBeginTime = StringEscapeUtils.escapeHtml(getParam(req, "startBeginTime", ""));
                String startEndTime = StringEscapeUtils.escapeHtml(getParam(req, "startEndTime", ""));
                String finishBeginTime = StringEscapeUtils.escapeHtml(getParam(req, "finishBeginTime", ""));
                String finishEndTime = StringEscapeUtils.escapeHtml(getParam(req, "finishEndTime", ""));
                DateTimeFormatter dateTimeFormatter = DateTimeFormat.forPattern(FILTER_BY_DATE_PATTERN)
                        .withLocale(Locale.ENGLISH);
                historyQueryParam.setStartBeginTime("".equals(startBeginTime) ? -1
                        : dateTimeFormatter.parseDateTime(startBeginTime).getMillis());
                historyQueryParam.setStartEndTime(
                        "".equals(startEndTime) ? -1 : dateTimeFormatter.parseDateTime(startEndTime).getMillis());
                historyQueryParam.setFinishBeginTime("".equals(finishBeginTime) ? -1
                        : dateTimeFormatter.parseDateTime(finishBeginTime).getMillis());
                historyQueryParam.setFinishEndTime("".equals(finishEndTime) ? -1
                        : dateTimeFormatter.parseDateTime(finishEndTime).getMillis());
                String runDate = getData(req, "runDate");
                historyQueryParam.setRunDateReq(runDate == null ? "" : StringEscapeUtils.escapeHtml(runDate));

                // -1/所有类型
                int flowType = StringUtils.isEmpty(getParam(req, "flowType")) ? -1 : getIntParam(req, "flowType");
                historyQueryParam.setFlowType(flowType);

                if (StringUtils.isNotEmpty(getParam(req, "flowType"))) {
                    historyQueryParam.setFlowType(getIntParam(req, "flowType"));
                }

                historyQueryParam.setSearchType(hasParam(req, "advfilter") ? "advfilter" : "preciseSearch");
                StringBuilder filterBuilder = new StringBuilder();
                filterBuilder
                        .append(historyQueryParam.getExecIdContain())
                        .append(historyQueryParam.getUserContain()).append(statusArray[0]).append(startBeginTime)
                        .append(startEndTime).append(finishBeginTime).append(finishEndTime)
                        .append(historyQueryParam.getRunDateReq())
                        .append(historyQueryParam.getFlowType()).append(historyQueryParam.getComment());
                try {
                    // 高级过滤中如果status含有All Status, flowType为所有类型, 其他为空,过滤拼接为 0-1
                    // 注意:StringBuilder如果直接调用equals比较,结果为false; 如果调用toString之后再调用equals, 结果为true, 所以此处toString不能省略
                    if (filterBuilder.toString().equals(EMPRY_ADVANCED_FILTER)) {
                        if (user.getRoles().contains("admin")) {
                            total =
                                    this.executorManagerAdapter.getExecutableFlows(project.getId()
                                            , flowId, from, length, exFlows);
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
                        }
                    } else {
                        List<ExecutableFlow> tempExecutableFlows;
                        final int pageNum = from / length;
                        final int pageSize = length;
                        if (user.getRoles().contains("admin")) {
                            tempExecutableFlows = this.executorManagerAdapter
                                    .getExecutableFlows(historyQueryParam, pageNum * pageSize, pageSize);
                            total = this.executorManagerAdapter.getExecHistoryTotal(historyQueryParam);

                        } else if (systemManager.isDepartmentMaintainer(user)) {
                            //运维管理员可以其运维部门下所有的工作流
                            List<Integer> projectIds = projectManager.getUserAllProjects(user, null, true).stream()
                                    .map(Project::getId)
                                    .collect(Collectors.toList());
                            tempExecutableFlows =
                                    this.executorManagerAdapter
                                            .getMaintainedExecutableFlows(historyQueryParam, pageNum * pageSize, pageSize,
                                                    projectIds);

                            total = this.executorManagerAdapter.getExecHistoryTotal(historyQueryParam, projectIds);

                        } else {
                            tempExecutableFlows = this.executorManagerAdapter
                                    .getUserExecutableFlows(user.getUserId(), historyQueryParam, pageNum * pageSize,
                                            pageSize);

                            total = this.executorManagerAdapter
                                    .getUserExecHistoryTotal(historyQueryParam, user.getUserId());

                        }
                        if (CollectionUtils.isNotEmpty(tempExecutableFlows)) {
                            calculateRunDate(tempExecutableFlows);
                            exFlows.addAll(tempExecutableFlows);
                        }
                    }
                } catch (final ExecutorManagerException e) {
                    logger.error("fetch flow execution failed.", e);
                    //page.add("error", e.getMessage());
                }
            } else if (hasParam(req, "search") && StringUtils
                    .isNotBlank(getParam(req, "searchTerm").trim())) {
                final String searchTerm = getParam(req, "searchTerm").trim();
                // 如果是快速搜索
                if (user.getRoles().contains("admin")) {
                    total = this.executorManagerAdapter.quickSearchFlowExecutions(project.getId(), flowId, from, length, searchTerm, exFlows);
                } else {
                    if (hasPermission(project, user, Type.READ)) {
                        total = this.executorManagerAdapter.quickSearchFlowExecutions(project.getId(), flowId, from, length, searchTerm, exFlows);
                    } else {
                        total = this.executorManagerAdapter.userQuickSearchFlowExecutions(project.getId(), flowId, from, length, searchTerm, exFlows, user.getUserId());
                    }
                }
            } else {
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
            if (flow.getFlowType() == 0) {
                flowInfo.put("flowType", SINGLE_EXECUTION);
            } else if (flow.getFlowType() == 2) {
                flowInfo.put("flowType", HISTORICAL_RERUN);
            } else if (flow.getFlowType() == 3) {
                flowInfo.put("flowType", TIMED_SCHEDULING);
            } else if (flow.getFlowType() == 4) {
                flowInfo.put("flowType", CYCLE_EXECUTION);
            } else {
                flowInfo.put("flowType", EVENT_SCHEDULE);
            }
            flowInfo.put(ExecutableFlow.COMMENT_PARAM, flow.getComment());
            Map<String, String> repeatMap = flow.getRepeatOption();
            if (flow.getRunDate() != null) {
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

    private void calculateRunDate(List<ExecutableFlow> executableFlowList) {
        //计算RunDate日期
        if (null != executableFlowList && !executableFlowList.isEmpty()) {
            executableFlowList.stream().forEach(executableFlow -> {
                Map<String, String> repeatMap = executableFlow.getRepeatOption();
                if (!repeatMap.isEmpty()) {
                    Long recoverRunDate = Long.valueOf(String.valueOf(repeatMap.get("startTimeLong")));
                    LocalDateTime localDateTime = new LocalDateTime(new Date(recoverRunDate)).minusDays(1);
                    Date date = localDateTime.toDate();
                    executableFlow.setUpdateTime(date.getTime());
                } else {
                    String runDatestr = executableFlow.getExecutionOptions().getFlowParameters()
                            .get("run_date");
                    Object runDateOther = executableFlow.getOtherOption().get("run_date");
                    if (runDatestr != null && !"".equals(runDatestr) && !runDatestr.isEmpty()) {
                        try {
                            executableFlow.setUpdateTime(Long.parseLong(runDatestr));
                        } catch (Exception e) {
                            logger.error("rundate convert failed (String to long) {}", runDatestr, e);
                        } finally {
                            executableFlow.setUpdateTime(0);
                            executableFlow.getOtherOption().put("run_date", runDatestr);
                        }
                    } else if (runDateOther != null && !"".equals(runDateOther.toString()) && !runDateOther
                            .toString().isEmpty()) {
                        String runDateTime = (String) runDateOther;
                        runDateTime = runDateTime.replaceAll("\'", "").replaceAll("\"", "");
                        if (SystemBuiltInParamReplacer.dateFormatCheck(runDateTime)) {
                            executableFlow.setUpdateTime(0);
                            executableFlow.getOtherOption().put("run_date", runDateTime);
                        } else {
                            if (-1 != executableFlow.getStartTime()) {
                                LocalDateTime localDateTime = new LocalDateTime(
                                        new Date(executableFlow.getStartTime())).minusDays(1);
                                Date date = localDateTime.toDate();
                                executableFlow.setUpdateTime(date.getTime());
                            }
                        }
                    } else if (executableFlow.getLastParameterTime() != -1) {
                        executableFlow.setUpdateTime(
                                new LocalDate(executableFlow.getLastParameterTime()).minusDays(1).toDate()
                                        .getTime());
                    } else {
                        Long runDate = executableFlow.getSubmitTime();
                        if (-1 != runDate) {
                            LocalDateTime localDateTime = new LocalDateTime(new Date(runDate)).minusDays(1);
                            Date date = localDateTime.toDate();
                            executableFlow.setUpdateTime(date.getTime());
                        }
                    }
                }

                WebUtils webUtils = new WebUtils();
                executableFlow.setRunDate(
                        executableFlow.getUpdateTime() == 0 ? executableFlow.getOtherOption().get("run_date")
                                .toString().replaceAll("[\"'./-]", "")
                                : webUtils.formatRunDate(executableFlow.getUpdateTime()));
            });
        }
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
                if (wtss_dep_upload_privilege_check && "personal".equals(wtssUser.getUserCategory()) && (write || execute || schedule)) {
                    ret.put("error", dataMap.get("cannotpermitpersonalwriteexecsch"));
                    return;
                }
                if (createUser.startsWith("WTSS")) {
                    WtssUser currentAddUser = this.transitionService.getSystemUserByUserName(wtssUser.getUsername());
                    // 判断用户角色  roleId 1:管理员, 2:普通用户
                    if (currentAddUser.getRoleId() != 1) {
                        if (!userId.startsWith("wtss_WTSS")) {
                            // 只对非管理员进行校验
                            int roleId = wtssUser.getRoleId();
                            if (roleId != 1) {
                                if (USER_ID_PATTERN.matcher(userId).matches()) {
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
     *
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
                                      HashMap<String, Object> ret, User user, boolean read, boolean write, boolean execute, boolean schedule) {

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
                if (wtss_dep_upload_privilege_check && "personal".equals(wtssUser.getUserCategory())) {
                    ret.put("error", dataMap.get("canNotAddPersonalUser"));
                    return;
                }
                if (createUser.startsWith("WTSS")) {
                    WtssUser currentToUser = this.transitionService.getSystemUserByUserName(wtssUser.getUsername());
                    // 判断用户角色  roleId 1:管理员, 2:普通用户
                    if (currentToUser.getRoleId() != 1) {
                        if (!userId.startsWith("wtss_WTSS")) {
                            int roleId = wtssUser.getRoleId();
                            if (roleId != 1) {
                                if (USER_ID_PATTERN.matcher(userId).matches()) {
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
                    executeAddProjectAdmin(wtssUser, project, ret, dataMap, user);
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
     *
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

    private List<String> getAllNodes(ExecutableFlowBase baseNode, List<String> allNodes) {
        for (ExecutableNode node : baseNode.getExecutableNodes()) {
            if (node instanceof ExecutableFlowBase) {
                allNodes.add("subflow-" + ((ExecutableFlowBase) node).getFlowId());
                getAllNodes((ExecutableFlowBase) node, allNodes);
            }
            if (!(node instanceof ExecutableFlowBase)) {
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

        if (hasParam(req, "lastExecId") && getIntParam(req, "lastExecId") != -1) {
            ajaxFetchExecutableFlowRealJobList(project, ret, req);
            return;
        }

        String action = getParam(req, "action", "");

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
        FlowUtils.getAllExecutableNodeId(new ExecutableFlow(project, flow), jobList, action);
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

    private void ajaxFetchExecutableFlowRealJobList(final Project project, final HashMap<String, Object> ret,
                                                    final HttpServletRequest req) throws ServletException {
        final int execId = getIntParam(req, "lastExecId", -1);
        final String flowId = getParam(req, "flow");
        final Flow flow = project.getFlow(flowId);
        final String action = getParam(req, "action", "");
        if (flow == null) {
            logger.error(flowId + " is not exist");
            return;
        }
        final ExecutableFlow currentFlow = new ExecutableFlow(project, flow);
        try {
            ExecutableFlow lastFlow = this.executorManagerAdapter.getExecutableFlow(execId);
            FlowUtils.compareAndCopyFlow(currentFlow, lastFlow);
        } catch (Exception e) {
            logger.error("get last executable flow faield.", e);
        }
        String searchName = req.getParameter("serach");

        List<String> jobList = new ArrayList<>();
        jobList.add("all_jobs" + " " + currentFlow.getFlowId());
        FlowUtils.getAllExecutableNodeId(currentFlow, jobList, action);
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
            if ((hasParam(req, "advfilter") && Boolean.parseBoolean(getParam(req, "advfilter")))
                    || (hasParam(req, "preciseSearch") && Boolean.parseBoolean(getParam(req, "preciseSearch")))) {
                // 如果是模糊搜索或者精确搜索
                HistoryQueryParam historyQueryParam = new HistoryQueryParam();
                historyQueryParam.setProjectId(project.getId());
                historyQueryParam.setJobId(jobId);
                historyQueryParam.setUserContain(getParam(req, "usercontain").trim());
                historyQueryParam.setExecIdContain(getParam(req, "execIdcontain").trim());
                String status = getParam(req, "status");
                String[] statusArray = status.split(",");
                StringBuilder statusNumber = new StringBuilder();
                for (int i = 0; i < statusArray.length; i++) {
                    if (NumberUtils.isParsable(statusArray[i])) {
                        if (i < (statusArray.length - 1)) {
                            statusNumber.append(statusArray[i]).append(",");
                        } else {
                            statusNumber.append(statusArray[i]);
                        }
                    }
                }
                status = statusNumber.toString();
                historyQueryParam.setStatus(status);
                String startBeginTime = StringEscapeUtils.escapeHtml(getParam(req, "startBeginTime", ""));
                String startEndTime = StringEscapeUtils.escapeHtml(getParam(req, "startEndTime", ""));
                String finishBeginTime = StringEscapeUtils.escapeHtml(getParam(req, "finishBeginTime", ""));
                String finishEndTime = StringEscapeUtils.escapeHtml(getParam(req, "finishEndTime", ""));
                DateTimeFormatter dateTimeFormatter = DateTimeFormat.forPattern(FILTER_BY_DATE_PATTERN)
                        .withLocale(Locale.ENGLISH);
                historyQueryParam.setStartBeginTime("".equals(startBeginTime) ? -1
                        : dateTimeFormatter.parseDateTime(startBeginTime).getMillis());
                historyQueryParam.setStartEndTime(
                        "".equals(startEndTime) ? -1 : dateTimeFormatter.parseDateTime(startEndTime).getMillis());
                historyQueryParam.setFinishBeginTime("".equals(finishBeginTime) ? -1
                        : dateTimeFormatter.parseDateTime(finishBeginTime).getMillis());
                historyQueryParam.setFinishEndTime("".equals(finishEndTime) ? -1
                        : dateTimeFormatter.parseDateTime(finishEndTime).getMillis());
                String runDate = getData(req, "runDate");
                historyQueryParam.setRunDateReq(runDate == null ? "" : StringEscapeUtils.escapeHtml(runDate));
                historyQueryParam.setFlowType(getIntParam(req, "flowType"));
                historyQueryParam.setSearchType(hasParam(req, "advfilter") ? "advfilter" : "preciseSearch");
                StringBuilder filterBuilder = new StringBuilder();
                filterBuilder.append(historyQueryParam.getExecIdContain())
                        .append(historyQueryParam.getUserContain()).append(statusArray[0]).append(startBeginTime)
                        .append(startEndTime).append(finishBeginTime).append(finishEndTime).append(historyQueryParam.getRunDateReq())
                        .append(historyQueryParam.getFlowType());
                if (filterBuilder.toString().equals(EMPRY_ADVANCED_FILTER)) {
                    total = this.executorManagerAdapter.getNumberOfJobExecutions(project, jobId);

                    jobInfo = this.executorManagerAdapter.getExecutableJobs(project, jobId, skipPage, pageSize);
                } else {
                    total = this.executorManagerAdapter.searchNumberOfJobExecutions(historyQueryParam);
                    jobInfo = this.executorManagerAdapter.searchJobExecutions(historyQueryParam, skipPage, pageSize);
                }
            } else if (hasParam(req, "search") && StringUtils
                    .isNotBlank(getParam(req, "searchTerm").trim())) {
                final String searchTerm = getParam(req, "searchTerm").trim();
                // 如果是快速搜索
                total = this.executorManagerAdapter.quickSearchNumberOfJobExecutions(project, jobId, searchTerm);
                jobInfo = this.executorManagerAdapter.quickSearchJobExecutions(project, jobId, searchTerm, skipPage, pageSize);
            } else {
                total = this.executorManagerAdapter.getNumberOfJobExecutions(project, jobId);

                jobInfo = this.executorManagerAdapter.getExecutableJobs(project, jobId, skipPage, pageSize);
            }


            if (jobInfo == null || jobInfo.isEmpty()) {
                jobInfo = null;
            }

//      moyenne = this.executorManagerAdapter.getExecutableJobsMoyenneRunTime(project, jobId);

            if (jobInfo != null) {
                jobInfo.stream().forEach(executableJobInfo -> {

                    ExecutableFlow executionFlow = null;
                    try {
                        executionFlow = this.executorManagerAdapter.getExecutableFlow(
                                executableJobInfo.getExecId());
                    } catch (ExecutorManagerException e) {
                        logger.warn("Failed to exec flow {}", executableJobInfo.getExecId(), e);
                    }

                    Map<String, String> repeatMap = executionFlow.getRepeatOption();

                    if (executionFlow.getRunDate() != null) {
                        DateTimeFormatter dateTimeFormatter = DateTimeFormat.forPattern("yyyyMMdd");
                        LocalDate localDate = LocalDate.parse(executionFlow.getRunDate(),
                                dateTimeFormatter);
                        executableJobInfo.setRunDate(localDate.toDate().getTime());
                    } else if (!repeatMap.isEmpty()) {

                        long recoverRunDate = Long.parseLong(
                                String.valueOf(repeatMap.get("startTimeLong")));

                        LocalDateTime localDateTime = new LocalDateTime(new Date(recoverRunDate)).minusDays(1);

                        Date date = localDateTime.toDate();

                        executableJobInfo.setRunDate(date.getTime());
                    } else {
                        long startTime = executionFlow.getStartTime();
                        if (-1 != startTime) {
                            LocalDateTime localDateTime = new LocalDateTime(
                                    new Date(startTime)).minusDays(1);

                            Date date = localDateTime.toDate();

                            executableJobInfo.setRunDate(date.getTime());
                        } else {
                            executableJobInfo.setRunDate(startTime);
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
        if (jobInfo != null) {
            for (final ExecutableJobInfo info : jobInfo) {
                final Map<String, Object> map = info.toObject();
                int flowType = (int) map.get("flowType");
                if (flowType == 0) {
                    map.put("flowType", SINGLE_EXECUTION);
                } else if (flowType == 2) {
                    map.put("flowType", HISTORICAL_RERUN);
                } else if (flowType == 3) {
                    map.put("flowType", TIMED_SCHEDULING);
                } else if (flowType == 4) {
                    map.put("flowType", CYCLE_EXECUTION);
                } else {
                    map.put("flowType", EVENT_SCHEDULE);
                }
//      map.put("moyenne", moyenne);
                jobPageList.add(map);
            }
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
            if ("type".equals(ps) && "datachecker".equals(jobProp.getString(ps))) {
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
                ret.put("scheduleStartDate", schedule.getOtherOption().get("scheduleStartDate"));
                ret.put("scheduleEndDate", schedule.getOtherOption().get("scheduleEndDate"));
                ret.put("isCrossDay", schedule.getExecutionOptions().isCrossDay());
                ret.put("comment", schedule.getComment());
                ret.put("autoSubmit", schedule.isAutoSubmit());
            } else {
                ret.put("cronExpression", "");
                ret.put("scheduleId", "");
                ret.put("scheduleStartDate", "");
                ret.put("scheduleEndDate", "");
                ret.put("isCrossDay", "");
                ret.put("comment", "");
                ret.put("autoSubmit", false);
            }
        } catch (ScheduleManagerException e) {
            logger.error("Fetch running schedule failed, caused by:", e);
            ret.put("error", "Fetch running schedule failed, caused by:" + e.getMessage());
        }

    }

    /**
     * 根据项目名，工作流名，任务名获取正在运行的信号调度
     *
     * @param project
     * @param ret
     * @param req
     * @throws ServletException
     */
    private void ajaxFetchRunningEventScheduleId(final Project project, final HashMap<String, Object> ret,
                                                 final HttpServletRequest req) throws ServletException {
        final String flowId = getParam(req, "flow");
        try {
            EventSchedule schedule = this.eventScheduleService.getEventSchedule(project.getId(), flowId);
            if (schedule != null) {
                int scheduleId = schedule.getScheduleId();
                ret.put("scheduleId", scheduleId);
                ret.put("topic", schedule.getTopic());
                ret.put("msgName", schedule.getMsgName());
                ret.put("saveKey", schedule.getSaveKey());
                ret.put("comment", schedule.getComment());
                ret.put("token", schedule.getToken());
            } else {
                ret.put("scheduleId", "");
                ret.put("topic", "");
                ret.put("msgName", "");
                ret.put("saveKey", "");
                ret.put("comment", "");
                ret.put("token", "");
            }
        } catch (ScheduleManagerException e) {
            logger.error("Fetch running event schedule failed, caused by:", e);
            ret.put("error", "Fetch running event schedule failed, caused by:" + e.getMessage());
        }

    }

    public static boolean getWtssProjectPrivilegeCheck() {
        return wtss_project_privilege_check;
    }

    /**
     * 还原已经删除的项目
     *
     * @param project
     * @param user
     * @param ret
     * @param req
     * @throws ServletException
     */
    private void ajaxRestoreProject(final Project project, final User user, final HashMap<String, Object> ret,
                                    final HttpServletRequest req) throws ServletException {
        try {
            String projectName = getParam(req, "project");
            int projectId = getIntParam(req, "projectId");
            this.projectManager.restoreProject(projectName, projectId, user, ret);
        } catch (ProjectManagerException e) {
            logger.error("restore project failed.", e);
            ret.put("error", e.getMessage());
        }

    }

    /**
     * 永久删除
     *
     * @param project
     * @param user
     * @param ret
     * @param req
     * @throws ServletException
     */
    private void ajaxDeleteProject(final Project project, final User user, final HashMap<String, Object> ret,
                                   final HttpServletRequest req) throws ServletException {
        try {
            int projectId = getIntParam(req, "projectId");
            this.projectManager.deleteInactiveProject(projectId, ret);

            // 删除小时报配置
            try {
                this.projectManager.removeProjectHourlyReportConfig(project);
            } catch (Exception e) {
                ret.put("error", "Remove hourly project report error: " + e.getMessage());
            }

        } catch (ProjectManagerException e) {
            logger.error("restore project failed.", e);
            ret.put("error", e.getMessage());
        }

    }
    //TODO 批量上传应用信息
//    private void handleUploadBusiness(final HttpServletRequest req, final HttpServletResponse resp,
//                              final Map<String, Object> multipart, final Session session) throws ServletException,
//            IOException {
//        final HashMap<String, String> ret = new HashMap<>();
//        final User user = session.getUser();
//        final String projectName = (String) multipart.get("project");
//        final Project project = this.projectManager.getProject(projectName);
//
//        FileItem item = (FileItem) multipart.get("file");
//        try {
//            //总列数
//            int colNum=13;
//            List<ExcelRow> rowList=ExcelUtils.getExcelData(item.getInputStream(),item.getName(),colNum);
//            for(ExcelRow row:rowList){
//                for(int i=0;i<colNum;i++){
//                    ExcelColumn col=row.getColumnMap().get(i);
//                    if(col==null){
//                        //TODO
//                    }
//                    String colValue=col.getColumnValue();
//                    String colName=col.getColumnName();
//                    switch (i){
//                        case 1:
//
//                    }
//                }
//            }
//        } catch (Exception e) {
//            //TODO
//        }
//
//        if (ret.containsKey("error")) {
//            setErrorMessageInCookie(resp, ret.get("error"));
//        }
//
//        if (ret.containsKey("warn")) {
//            setWarnMessageInCookie(resp, ret.get("warn"));
//        }
//
//        logger.info("Upload project, Redirect to ---> " + req.getRequestURI() + "?project=" + projectName);
//        resp.sendRedirect(req.getRequestURI() + "?project=" + projectName);
//    }

    /**
     * 新增/更新项目应用信息
     *
     * @param req
     * @param resp
     * @param session
     * @throws ServletException
     */
    private void ajaxMergeFlowBusiness(final HttpServletRequest req,
                                       final HttpServletResponse resp, final Session session, Map<String, String> map, Map<String, Object> ret) throws ServletException, IOException {
        try {
            final User user = session.getUser();
            int projectId = 0;
            String flowId = "";
            String jobId = "";
            if (map.isEmpty()) {
                projectId = getIntParam(req, "projectId");
                flowId = getParam(req, "flowId", "");
                jobId = getParam(req, "jobId", "");
            }

            //假如是文件传入时
            if (!map.isEmpty()) {
                //校验项目是否存在
                Project project = projectManager.getProject(map.get("project"));
                if (Objects.isNull(project)) {
                    ret.put("error", "The project not exist");
                    return;
                }
                projectId = project.getId();
                flowId = map.get("flow");
                List<Flow> flows = projectManager.getFlowsByProject(project);
                jobId = map.get("job") == null ? "" : map.get("job");
                if (CollectionUtils.isNotEmpty(flows) && StringUtils.isNotEmpty(flowId)) {
                    //校验工作流是否属于该项目
                    String finalFlowId = flowId;
                    List<Flow> flowList = flows.stream().filter(f -> f.getId().equals(finalFlowId)).collect(Collectors.toList());
                    if (CollectionUtils.isEmpty(flowList)) {
                        ret.put("error", "this flow not exist.");
                        return;
                    }
                }

            }

            //获取提示语
            String languageType = LoadJsonUtils.getLanguageType();
            Map<String, String> dataMap;
            if ("zh_CN".equalsIgnoreCase(languageType)) {
                dataMap = LoadJsonUtils.transJson("/conf/azkaban-web-server-zh_CN.json",
                        "azkaban.webapp.servlet.velocity.projectmodals.vm");
            } else {
                dataMap = LoadJsonUtils.transJson("/conf/azkaban-web-server-en_US.json",
                        "azkaban.webapp.servlet.velocity.projectmodals.vm");
            }

            boolean isBusPath = false;

            String projectName = this.projectManager.getProject(projectId).getName();

            //TODO 现只支持工作流级别关键路径判断
            Props props = getApplication().getServerProps();
            if (StringUtils.isEmpty(jobId) && StringUtils.isNotEmpty(flowId)) {
                String jobCode = DmsBusPath
                        .createJobCode(props.getString(Constants.JobProperties.JOB_BUS_PATH_CODE_PREFIX, ""),
                                projectName, flowId);
                isBusPath = CollectionUtils.isNotEmpty(HttpUtils
                        .getBusPathFromDBOrDms(props, jobCode, 1, -1, null, null));
            }


            List<String> fieldList = isBusPath ? props.getStringList("buspath.required.field")
                    : props.getStringList("flowbusiness.required.field");

            Map<String, String> valueMap = new HashMap<>(64);
            if (!map.isEmpty()) {
                //校验非空(文件传入时)
                for (String field : fieldList) {
                    String value = map.get(field);
                    if (StringUtils.isEmpty(value)) {
                        ret.put("errorMsg", dataMap.get(field) + " " + dataMap.get("notNullTips"));
                        return;
                    }
                }
                valueMap = map;
            } else {
                //校验非空(页面表单传入时)
                for (String field : fieldList) {
                    String value = getParam(req, field, "").trim();
                    if (StringUtils.isEmpty(value)) {
                        ret.put("errorMsg", dataMap.get(field) + " " + dataMap.get("notNullTips"));
                        return;
                    }
                }

                List<String> allFieldList = Arrays
                        .asList("busTypeFirst", "busTypeSecond", "busDesc", "subsystem", "busResLvl",
                                "busPath", "devDept", "opsDept", "batchGroup", "busDomain", "planStartTime",
                                "planFinishTime", "lastStartTime", "lastFinishTime", "alertLevel", "dcnNumber",
                                "imsUpdater", "imsRemark", "batchGroupDesc", "busPathDesc",
                                "busTypeFirstDesc", "busTypeSecondDesc", "subsystemDesc"
                                , "devDeptDesc", "opsDeptDesc", "scanPartitionNum", "scanDataSize");

                for (String field : allFieldList) {
                    valueMap.put(field, getParam(req, field, "").trim());
                }
            }


            //备份 用于回滚
            FlowBusiness flowBusinessBak = null;

            //补充数据
            FlowBusiness flowBusiness = new FlowBusiness();
            BeanUtils.populate(flowBusiness, valueMap);
            flowBusiness.setProjectId(projectId);
            flowBusiness.setFlowId(flowId);
            flowBusiness.setJobId(jobId);
            //1-项目 2-工作流 3-job
            if (StringUtils.isNotEmpty(jobId)) {
                flowBusiness.setDataLevel("3");
            } else if (StringUtils.isNotEmpty(flowId)) {
                flowBusiness.setDataLevel("2");
                flowBusinessBak = this.projectManager.getFlowBusiness(projectId, flowId, "");
            } else {
                flowBusiness.setDataLevel("1");
            }

            boolean isAppInfoItsmApprovalEnabled = props.getBoolean(
                    ConfigurationKeys.APPLICATION_INFO_ITSM_APPROVAL_SWITCH, false);

            // 判断当前服务请求单的状态，如果未完成，则不能再次提交登记
            // 注意：首次配置应用信息，历史存量数据是没有 ITSM 服务请求单的，
            // 因此校验 ITSM 单状态的条件为 有应用信息 且 有 ITSM 单号

            // B/C 级别不在 0-7 执行，则无需审批
            String busResLvl = flowBusiness.getBusResLvl();
            String planStartTime = flowBusiness.getPlanStartTime();
            int planStartHour = -1;
            if (StringUtils.isNotBlank(planStartTime)) {
                planStartHour = Integer.parseInt(planStartTime.split(":")[0]);
            }

            boolean noNeedApproval =
                    ("B".equals(busResLvl) || "C".equals(busResLvl)) && planStartHour >= 7;
            if (isAppInfoItsmApprovalEnabled && !noNeedApproval) {
                if (map.isEmpty()) {
                    if (!hasParam(req, "itsmNo")) {
                        ret.put("error", "请输入 ITSM 服务请求单号！");
                        return;
                    }
                    String itsmNoFromRequest = getParam(req, "itsmNo");

                    if (StringUtils.isNotBlank(itsmNoFromRequest)) {
                        FlowBusiness flowBusinessFromDb =
                                this.projectManager.getFlowBusiness(projectId, flowId, "");
                        if (flowBusinessFromDb != null) {
                            // 校验 ITSM 请求单状态
                            ItsmUtil.getRequestFormStatus(props, Long.parseLong(itsmNoFromRequest),
                                    ret);
                        } else {
                            if (this.projectManager.getFlowBusiness(projectId, "", "") != null) {
                                // 存在项目级别应用信息
                                ItsmUtil.getRequestFormStatus(props, Long.parseLong(itsmNoFromRequest),
                                        ret);
                            }
                        }

                        if (ret.containsKey("requestStatus")) {
                            int requestStatus = (int) ret.get("requestStatus");
                            if (requestStatus != 1009 && requestStatus != 1013) {
                                // 1009 —— 验收中，1013 —— 已完成
                                ret.put("error", "ITSM 服务请求单 " + itsmNoFromRequest
                                        + " 未完成审批，暂时无法再次注册应用信息。");
                                return;
                            }
                        } else {
                            return;
                        }
                    }

                } else {
                    FlowBusiness business = this.projectManager.getFlowBusiness(projectId, flowId + "", jobId + "");
                    if (business != null && StringUtils.isNotEmpty(business.getItsmNo())) {
                        ItsmUtil.getRequestFormStatus(props, Long.parseLong(business.getItsmNo()), ret);

                        if (ret.containsKey("requestStatus")) {
                            int requestStatus = (int) ret.get("requestStatus");
                            if (requestStatus != 1009 && requestStatus != 1013) {
                                // 1009 —— 验收中，1013 —— 已完成
                                ret.put("error", "ITSM 服务请求单 " + business.getItsmNo()
                                        + " 未完成审批，暂时无法再次注册应用信息。");
                                return;
                            }
                        } else {
                            return;
                        }
                    }

                }
            }
            flowBusiness.setCreateUser(user.getUserId());
            flowBusiness.setUpdateUser(user.getUserId());

            // 发起 ITSM 审批
            if (isAppInfoItsmApprovalEnabled && !noNeedApproval) {

                // 如果为运维用户或者系统用户，需要使用实名用户
                String username =
                        user.getNormalUser() != null ? user.getNormalUser() : user.getUserId();
                ItsmUtil.sendRequest2Itsm4ApplicationInfo(props, username, projectName,
                        flowId, jobId, valueMap, ret);
                if (ret.containsKey("error")) {
                    return;
                }

                String itsmNo = ret.get("itsmNo") + "";
                flowBusiness.setItsmNo(itsmNo);
            }

            if (this.projectManager.mergeFlowBusiness(flowBusiness) > 0 && "2"
                    .equals(flowBusiness.getDataLevel()) && !isBusPath) {
                try {
                    // 注册并上报作业流开始
                    Alerter mailAlerter = ServiceProvider.SERVICE_PROVIDER
                            .getInstance(AlerterHolder.class).get("email");
                    if (mailAlerter == null) {
                        logger.warn("找不到告警插件.");
                    }
                    String result = mailAlerter
                            .alertOnIMSRegistStart(projectName,
                                    flowId, flowBusiness, props);
                    if (StringUtils.isNotEmpty(result)) {
                        if (flowBusinessBak == null) {
                            this.projectManager.deleteFlowBusiness(projectId, flowId, jobId);
                        } else {
                            this.projectManager.mergeFlowBusiness(flowBusinessBak);
                        }
                        ret.put("errorMsg", "Regist to IMS error: " + result);
                    }
                } catch (Exception e) {
                    if (flowBusinessBak == null) {
                        this.projectManager.deleteFlowBusiness(projectId, flowId, jobId);
                    } else {
                        this.projectManager.mergeFlowBusiness(flowBusinessBak);
                    }

                    throw new RuntimeException(e);
                }
            }

            if (!ret.containsKey("requestInfo")) {
                ret.put("requestInfo", "应用信息设置成功");
            }
        } catch (Exception e) {
            logger.error("merge business error", e);
            ret.put("errorMsg", e.getMessage());

        } finally {
            if (!map.isEmpty()) {
                if (ret.containsKey("error")) {
                    setErrorMessageInCookie(resp, ret.get("error") + "");
                }
            }
            if (hasParam(req, "projectId")) {
                this.writeJSON(resp, ret);
            }

        }

    }

    /**
     * 查询项目应用信息
     *
     * @param req
     * @param resp
     * @param session
     * @param ret
     * @throws ServletException
     */
    private void ajaxGetFlowBusiness(final HttpServletRequest req,
                                     final HttpServletResponse resp, final Session session, final HashMap<String, Object> ret)
            throws ServletException {
        try {
            Props prop = getApplication().getServerProps();
            final int projectId = getIntParam(req, "projectId");
            final String flowId = getParam(req, "flowId", "");
            final String jobId = getParam(req, "jobId", "");
            final boolean isLoaded = Boolean.valueOf(getParam(req, "isLoaded", "false"));
            FlowBusiness flowBusiness = this.projectManager
                    .getFlowBusiness(projectId, flowId, jobId);
            if (flowBusiness != null) {
                ret.put("projectBusiness", flowBusiness);
            }
            //部门字典
            if (!isLoaded) {
                List<WebankDepartment> webankDepartmentList = this.systemManager
                        .findAllWebankDepartmentList();
                ret.put("busDeptSelectList", webankDepartmentList);

                List<WtssUser> userList = this.systemManager.findSystemUserPage("", "", "", -1, -1);
                ret.put("imsUpdaterList", userList);
            }

        } catch (Exception e) {
            logger.error(e.getMessage(), e);
            ret.put("errorMsg", "query business failed");
        }

    }

    /**
     * 从cmdb获取下拉框字典
     *
     * @param req
     * @param resp
     * @param session
     * @param ret
     * @throws ServletException
     */
    private void ajaxGetCmdbData(final HttpServletRequest req,
                                 final HttpServletResponse resp, final Session session, final HashMap<String, Object> ret)
            throws ServletException {
        try {
            Props prop = getApplication().getServerProps();
            final String type = getParam(req, "type");
            final String id = getParam(req, "id");
            final String queryId = getParam(req, "queryId", "");
            final String name = getParam(req, "name");
            final String query = getParam(req, "query", "");
            int start = getIntParam(req, "start", 0);
            int size = getIntParam(req, "size", 10);
            if (start < 0) {
                start = 0;
            }
            if (size < 0) {
                size = 10;
            }
            if ("subsystem_app_instance".equals(type)) {
                HttpUtils.getCmdbData(prop, "wtss.cmdb.getIntegrateTemplateData", type, id, queryId, name, query, start, size, ret, true);
            } else {
                HttpUtils.getCmdbData(prop, "wtss.cmdb.operateCi", type, id, queryId, name, query, start, size, ret, true);
            }
            ret.put("start", start);
            ret.put("size", size);

        } catch (Exception e) {
            logger.error(e.getMessage(), e);
            ret.put("errorMsg", "query cmdb data failed");
        }

    }

    private void ajaxHandleAompRegister(final HttpServletRequest req,
                                        final HttpServletResponse resp, final Session session) throws IOException {

        Props prop = getApplication().getServerProps();
        Map<String, Object> ret = new HashMap<>();
        String projectName = req.getParameter("projectName");
        String subsystemId = req.getParameter("subsystemId");
        try {
            String appDomain = HttpUtils.getCmdbAppDomainBySubsystemId(prop,
                    subsystemId);
            ret.put("project", projectName);
            Project project = this.projectManager.getProject(projectName);
            if (project == null) {
                ret.put("error", "Project " + projectName + " does not exist. ");
            } else {
                int projectId = project.getId();
                mergeProjectInfo(projectId, subsystemId, appDomain, session.getUser());
                ret.put("subsystemId", subsystemId);
                ret.put("appDomain", appDomain);
            }

        } catch (Exception e) {
            ret.put("error", e.getMessage());
        } finally {
            this.writeJSON(resp, ret);
        }
    }

    private void mergeProjectInfo(int projectId, String subsystemId, String appDomain, User user)
            throws SQLException {

        FlowBusiness flowBusiness = new FlowBusiness();
        flowBusiness.setProjectId(projectId);
        flowBusiness.setSubsystem(subsystemId);
        flowBusiness.setBusDomain(appDomain);
        flowBusiness.setUpdateUser(user.getUserId());
        flowBusiness.setFlowId("");
        flowBusiness.setJobId("");

        this.projectManager.mergeProjectInfo(flowBusiness);
    }

    private void fetchUserPermProjects(final HttpServletRequest req, final HashMap<String, Object> ret) {
        try {
            String userName = getParam(req, "userName", "");
            if (StringUtils.isEmpty(userName)) {
                ret.put("error", "empty user name");
                return;
            }
            final User user = this.systemUserManager.getUser(userName);
            if (user == null) {
                ret.put("error", "user not found");
                return;
            }
            final Set<String> projects = new HashSet<>();
            final List<Project> projectList = this.projectManager.getProjects(true);
            //添加权限判断 admin 用户能查看所有Project
            if (user.getRoles().contains("admin")) {
                for (Project project : projectList) {
                    projects.add(project.getName());
                }
            } else if (systemManager.isDepartmentMaintainer(user)) {
                //部门下所有用户的个人项目
                List<Integer> maintainedProjectIds = systemManager.getMaintainedProjects(user, 1);
                //部门下所有用户
                Set<String> users = this.systemManager.getMaintainedDeptUser(userName);
                for (final Project project : projectList) {
                    //实名用户个人项目及有写权限项目
                    final Permission permission = project.getUserPermission(user);
                    Predicate<Permission> hasPermission = perm -> perm != null && (
                            perm.isPermissionSet(Type.ADMIN) || perm.isPermissionSet(Type.WRITE));
                    Predicate<Project> isMaintained = proj -> maintainedProjectIds
                            .contains(proj.getId());
                    if (isMaintained.test(project) || hasPermission.test(permission)) {
                        projects.add(project.getName());
                    }
                    //部门下其他用户有写权限的项目
                    Predicate<Permission> hasPermission1 = perm -> perm != null && (perm
                            .isPermissionSet(Type.WRITE));
                    for (String user1 : users) {
                        final Permission permission1 = project.getUserPermission(user1);
                        if (hasPermission1.test(permission1)) {
                            projects.add(project.getName());
                        }
                    }
                }
            } else {
                for (final Project project : projectList) {
                    final Permission permission = project.getUserPermission(user);
                    Predicate<Permission> hasPermission = perm -> perm != null && (
                            perm.isPermissionSet(Type.ADMIN) || perm.isPermissionSet(Type.WRITE));
                    if (hasPermission.test(permission)) {
                        projects.add(project.getName());
                    }
                }
            }
            ret.put("projects", projects);
        } catch (Exception e) {
            logger.error("fetch project by user error", e);
            ret.put("error", e.getMessage());
        }
    }

    private void fetchMaintainedDeptUsers(final HttpServletRequest req,
                                          final HashMap<String, Object> ret) {
        try {
            String userName = getParam(req, "userName", "");
            if (StringUtils.isEmpty(userName)) {
                ret.put("error", "empty user name");
                return;
            }
            final Set<String> users = this.systemManager.getMaintainedDeptUser(userName);
            users.add(userName);
            ret.put("users", users);
            WtssUser wtssUser = this.systemManager.getSystemUserByUserName(userName);
            if (null != wtssUser && wtssUser.getProxyUsers() != null) {
                ret.put("proxyUser", Arrays.asList(wtssUser.getProxyUsers().split(",")));
            }

        } catch (Exception e) {
            logger.error("fetch maintained dept user error", e);
            ret.put("error", e.getMessage());
        }
    }

    /**
     * 上传应用相关信息
     *
     * @param req
     * @param resp
     * @param multipart
     * @param session
     * @throws ServletException
     * @throws IOException
     */
    private void handleUploadBusinessInfo(final HttpServletRequest req, final HttpServletResponse resp,
                                          final Map<String, Object> multipart, final Session session)
            throws ServletException, IOException {
        final FileItem item = (FileItem) multipart.get("businessfile");
        User user = session.getUser();
        Map<String, Object> ret = new HashMap<>();
        int fileError = 0;
        //获取projectName
        String projectName = multipart.get("project") + "";
        logger.info("项目名" + projectName);
        Project project = projectManager.getProject(projectName);

        //校验文件名
        boolean fileNameBoolean = azkaban.utils.StringUtils.checkFileExtension(item.getName(), Arrays.asList("json"));
        if (!fileNameBoolean) {
            setErrorMessageInCookie(resp, "the file is not json file");
            if (project == null) {
                resp.sendRedirect(req.getRequestURI() + "?project=" + project.getName());
            } else {
                resp.sendRedirect(req.getRequestURI() + "?project=" + project.getName());
            }

        }
        ObjectMapper objectMapper = JSONUtils.JacksonObjectMapperFactory.getInstance();
        JSONArray jsonArray = null;
        InputStream inputStream = item.getInputStream();
        try {
            jsonArray = objectMapper.readValue(inputStream, JSONArray.class);
        } catch (Exception e) {
            setErrorMessageInCookie(resp, "the json format is error");
            if (project == null) {
                resp.sendRedirect(req.getRequestURI() + "?project=" + project.getName());
            } else {
                resp.sendRedirect(req.getRequestURI() + "?project=" + project.getName());
            }
        }finally {
            IOUtils.closeQuietly(inputStream);
        }

        if (jsonArray != null) {
            for (int i = 0; i < jsonArray.size(); i++) {
                //项目内上传时，校验文件内是否为同一个项目
                JSONObject jsonObject = jsonArray.getJSONObject(i);
                if (project != null) {
                    if (!project.getName().equals(jsonObject.getString("project"))) {
                        setErrorMessageInCookie(resp, "the project " + jsonObject.getString("project") + " is not currency project");
                        resp.sendRedirect(req.getRequestURI() + "?project=" + project.getName());
                        return;
                    }
                } else {
                    //批量上传时，校验用户是否有该项目权限
                    Project batchProject = projectManager.getProject(jsonObject.getString("project"));
                    if (batchProject == null) {
                        setErrorMessageInCookie(resp, "the project " + jsonObject.getString("project") + " is empty or not exist");
                        resp.sendRedirect(req.getContextPath() + "/index");
                        return;
                    }
                    if (!user.getRoles().contains("admin")) {
                        ProjectPermission projectPermission = projectManager.getProjectPermission(batchProject, user.getUserId());
                        if (projectPermission == null) {
                            setErrorMessageInCookie(resp, "the project " + jsonObject.getString("project") + " have no permission ");
                            resp.sendRedirect(req.getContextPath() + "/index");
                            return;
                        }
                    }
                }
                Map<String, String> map = objectMapper.readValue(jsonArray.getJSONObject(i).toString(), Map.class);

                checkBusinessInfoBeforeIntoDB(req, resp, ret, fileError, map, projectManager.getProject(jsonObject.getString("project")), "");

            }
            for (int j = 0; j < jsonArray.size(); j++) {

                Map<String, String> map = objectMapper.readValue(jsonArray.getJSONObject(j).toString(), Map.class);
                setBusinessInfo(req, resp, session, ret, fileError, map, null, "");
            }
        }
        setSuccessMessageInCookie(resp, "应用信息设置成功");
        if (project != null) {
            resp.sendRedirect(req.getRequestURI() + "?project=" + project.getName());
        } else {
            resp.sendRedirect(req.getContextPath() + "/index");
        }


    }

    private void setBusinessInfo(HttpServletRequest req, HttpServletResponse resp, Session session, Map<String, Object> ret, int fileError, Map<String, String> map, Project project, String channelType) throws IOException, ServletException {
        if (project == null) {
            project = projectManager.getProject(map.get("project"));
        }

        if (Objects.equals(0, fileError)) {
            //更新或新增应用信息
            ajaxMergeFlowBusiness(req, resp, session, map, ret);
            if (StringUtils.isNotEmpty(parseNull(ret.get("error") + ""))) {

                setErrorMessageInCookie(resp, ret.get("error") + "");
                if (!channelType.equals(Constants.UPLOAD_CHANNEL_TYPE)) {
                    resp.sendRedirect(req.getRequestURI() + "?project=" + project.getName());
                }
            }
            if (StringUtils.isNotEmpty(parseNull(ret.get("errorMsg") + ""))) {
                setErrorMessageInCookie(resp, ret.get("errorMsg") + "");

                if (!channelType.equals(Constants.UPLOAD_CHANNEL_TYPE)) {
                    resp.sendRedirect(req.getRequestURI() + "?project=" + project.getName());
                }
            }

        }

    }

    private int checkBusinessInfoBeforeIntoDB(HttpServletRequest req, HttpServletResponse resp, Map<String, Object> ret, int fileError, Map<String, String> map, Project project, String channelType) throws IOException {
        try {

            String error = checkRequired(map);
            logger.error(error);
            if (project == null) {
                setErrorMessageInCookie(resp, "项目名不能为空");
                resp.sendRedirect(req.getContextPath() + "/index");
            }
            if (StringUtils.isNotEmpty(error)) {
                setErrorMessageInCookie(resp, error);
                if (!channelType.equals(Constants.UPLOAD_CHANNEL_TYPE)) {
                    resp.sendRedirect(req.getRequestURI() + "?project=" + project.getName());
                }

            }
            String planStartTimeError = checkTime(map.get("planStartTime"), "HH:mm", "planStartTime");
            String planFinishTimeError = checkTime(map.get("planFinishTime"), "HH:mm", "planFinishTime");
            String lastStartTimeError = checkTime(map.get("lastStartTime"), "HH:mm", "lastStartTime");
            String lastFinishTimeError = checkTime(map.get("lastFinishTime"), "HH:mm", "lastFinishTime");

            //重要性等级 S,A,B,C
            List<String> busResLvls = Arrays.asList("S", "A", "B", "C");
            String busResLvl = map.get("busResLvl");

            if (!busResLvls.contains(busResLvl)) {
                setErrorMessageInCookie(resp, "the busResLvl can only be one of S, A, B, or C");
                if (!channelType.equals(Constants.UPLOAD_CHANNEL_TYPE)) {
                    resp.sendRedirect(req.getRequestURI() + "?project=" + project.getName());
                }
            }


            if (StringUtils.isNotEmpty(planStartTimeError)) {
                setErrorMessageInCookie(resp, planStartTimeError);
                if (!channelType.equals(Constants.UPLOAD_CHANNEL_TYPE)) {
                    resp.sendRedirect(req.getRequestURI() + "?project=" + project.getName());
                }

            }
            if (StringUtils.isNotEmpty(planFinishTimeError)) {
                setErrorMessageInCookie(resp, planFinishTimeError);
                if (!channelType.equals(Constants.UPLOAD_CHANNEL_TYPE)) {
                    resp.sendRedirect(req.getRequestURI() + "?project=" + project.getName());
                }

            }
            if (StringUtils.isNotEmpty(lastStartTimeError)) {
                setErrorMessageInCookie(resp, lastStartTimeError);
                if (!channelType.equals(Constants.UPLOAD_CHANNEL_TYPE)) {
                    resp.sendRedirect(req.getRequestURI() + "?project=" + project.getName());
                }
            }
            if (StringUtils.isNotEmpty(lastFinishTimeError)) {
                setErrorMessageInCookie(resp, lastFinishTimeError);
                if (!channelType.equals(Constants.UPLOAD_CHANNEL_TYPE)) {
                    resp.sendRedirect(req.getRequestURI() + "?project=" + project.getName());
                }

            }

        } catch (Exception e) {
            fileError = 1;
            ret.put("error", "The JSON file format is wrong,please check your file.");
            if (ret.containsKey("error")) {
                setErrorMessageInCookie(resp, ret.get("error") + "");
                if (!channelType.equals(Constants.UPLOAD_CHANNEL_TYPE)) {
                    resp.sendRedirect(req.getRequestURI() + "?project=" + project.getName());
                }
            }

        }
        return fileError;
    }

    private static String checkRequired(Map<String, String> map) {
        List<String> requiredList = Arrays.asList("project", "busDomain",
                "subsystem", "subsystemDesc", "busResLvl",
                "planStartTime", "planFinishTime",
                "lastStartTime", "lastFinishTime",
                "devDept", "devDeptDesc", "opsDept",
                "opsDeptDesc", "scanPartitionNum", "scanDataSize");

        for (String key : requiredList) {
            if (StringUtils.isEmpty(map.get(key))) {
                return key + "为必填字段";
            }

        }
        return null;
    }


    private void downloadBusinessInfoTemple(final HttpServletRequest req, final HttpServletResponse resp,
                                            final Session session) throws IOException {

        HashMap<String, Object> ret = new HashMap<>();

        List<String> allFieldList = Arrays
                .asList("project,项目名称",
                        "flow,工作流名称",
                        "job,job名称",
                        "batchGroup,关键批量分组id（非必填）",
                        "batchGroupDesc,关键批量分组名称（非必填）",
                        "busPath,关键路径(非必填)",
                        "busPathDesc,关键路径(非必填)",
                        "busDomain,业务域（必填）",
                        "subsystem,子系统（必填）",
                        "subsystemDesc,子系统（必填）",
                        "busResLvl,重要性等级（必填）",
                        "planStartTime,计划开始时间(HH:MM必填)",
                        "planFinishTime,计划完成时间(HH:MM必填)",
                        "lastStartTime,最迟开始时间(HH:MM必填)",
                        "lastFinishTime,最迟完成时间(HH:MM必填)",
                        "busTypeFirst,业务/产品一级分类(非必填)",
                        "busTypeFirstDesc,业务/产品一级分类(非必填)",
                        "busTypeSecond,业务/产品二级分类(非必填)",
                        "busTypeSecondDesc,业务/产品二级分类(非必填)",
                        "busDesc,业务描述(非必填)",
                        "devDept,开发科室(必填)",
                        "devDeptDesc,开发科室(必填)",
                        "opsDept,运维科室(必填)",
                        "opsDeptDesc,运维科室(必填)",
                        "scanPartitionNum,扫描分区数量(必填)",
                        "scanDataSize,扫描数据大小（GB）(必填)");

        final String headerKey = "Content-Disposition";
        final String headerValue =
                String.format("attachment; filename=\"%s\"",
                        "businessInfoTemple.JSON");
        resp.setHeader(headerKey, headerValue);
        OutputStream outStream = resp.getOutputStream();

        try {
            StringBuilder stringBuilder = new StringBuilder("[{");
            for (int i = 0; i < allFieldList.size(); i++) {
                String field = allFieldList.get(i);
                String key = field.split(",")[0];
                String value = field.split(",")[1];
                if (i < allFieldList.size() - 1) {
                    stringBuilder.append("\"" + key + "\":\"" + value + "\",\n");
                } else {
                    stringBuilder.append("\"" + key + "\":\"" + value + "\"}]\n");
                }
            }
            String tempJSON = stringBuilder.toString();
            resp.setContentType(Constants.APPLICATION_ZIP_MIME_TYPE);
            outStream.write(tempJSON.getBytes());

        } catch (Exception e) {
            logger.error(e.getMessage());
            ret.put("error", "download template file wrong");
            this.writeJSON(resp, ret);
        } finally {
            IOUtils.closeQuietly(outStream);
        }

    }


    private static String parseNull(String s) {

        if ("null".equalsIgnoreCase(s)) {
            return "";
        }
        return s;
    }

    private static String checkTime(String time, String format, String fileCode) {
        try {
            String[] split = time.split(":");
            for (String t : split) {
                if (t.length() != 2) {
                    return "the " + fileCode + " format is error";
                }
            }

            SimpleDateFormat simpleDateFormat = new SimpleDateFormat(format);
            simpleDateFormat.parse(time);
        } catch (Exception e) {
            logger.error(fileCode + "格式错误");
            return "the " + fileCode + " format is error";
        }
        return null;
    }

}
