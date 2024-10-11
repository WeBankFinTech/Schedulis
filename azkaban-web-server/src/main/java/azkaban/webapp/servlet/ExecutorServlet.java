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

import static azkaban.ServiceProvider.SERVICE_PROVIDER;

import azkaban.Constants;
import azkaban.alert.Alerter;
import azkaban.executor.AlerterHolder;
import azkaban.executor.ConnectorParams;
import azkaban.executor.ExecutableFlow;
import azkaban.executor.ExecutableFlowBase;
import azkaban.executor.ExecutableNode;
import azkaban.executor.ExecutionControllerUtils;
import azkaban.executor.ExecutionOptions;
import azkaban.executor.ExecutionOptions.FailureAction;
import azkaban.executor.Executor;
import azkaban.executor.ExecutorManagerAdapter;
import azkaban.executor.ExecutorManagerException;
import azkaban.executor.Status;
import azkaban.flow.Flow;
import azkaban.flow.FlowUtils;
import azkaban.flow.Node;
import azkaban.flowtrigger.FlowTriggerService;
import azkaban.flowtrigger.TriggerInstance;
import azkaban.history.ExecutionRecover;
import azkaban.jobid.relation.JobIdRelationService;
import azkaban.project.Project;
import azkaban.project.ProjectManager;
import azkaban.scheduler.Schedule;
import azkaban.scheduler.ScheduleManager;
import azkaban.scheduler.ScheduleManagerException;
import azkaban.server.HttpRequestUtils;
import azkaban.server.session.Session;
import azkaban.sla.SlaOption;
import azkaban.trigger.TriggerManager;
import azkaban.trigger.TriggerManagerException;
import azkaban.user.Permission;
import azkaban.user.Permission.Type;
import azkaban.user.User;
import azkaban.user.UserManagerException;
import azkaban.utils.ExternalLinkUtils;
import azkaban.utils.FileIOUtils;
import azkaban.utils.FileIOUtils.LogData;
import azkaban.utils.Pair;
import azkaban.utils.Props;
import azkaban.webapp.AzkabanWebServer;
import azkaban.webapp.WebMetrics;
import azkaban.webapp.plugin.PluginRegistry;
import azkaban.webapp.plugin.ViewerPlugin;

import com.webank.wedatasphere.schedulis.common.executor.ExecutionCycle;
import com.webank.wedatasphere.schedulis.common.i18nutils.LoadJsonUtils;
import com.webank.wedatasphere.schedulis.common.log.LogFilterEntity;
import com.webank.wedatasphere.schedulis.common.system.SystemManager;
import com.webank.wedatasphere.schedulis.common.system.SystemUserManagerException;
import com.webank.wedatasphere.schedulis.common.system.common.TransitionService;
import com.webank.wedatasphere.schedulis.common.system.entity.WtssUser;
import com.webank.wedatasphere.schedulis.common.user.SystemUserManager;
import com.webank.wedatasphere.schedulis.common.utils.AlertUtil;
import com.webank.wedatasphere.schedulis.common.utils.GsonUtils;
import com.webank.wedatasphere.schedulis.common.utils.LogErrorCodeFilterUtils;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.*;
import java.util.stream.Collectors;
import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.reflect.TypeToken;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.collections4.MapUtils;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang.ArrayUtils;
import org.apache.commons.lang.StringEscapeUtils;
import org.apache.commons.lang.StringUtils;
import org.slf4j.LoggerFactory;
import org.slf4j.Logger;
import org.joda.time.LocalDate;
import org.joda.time.format.DateTimeFormat;
import org.joda.time.format.DateTimeFormatter;


public class ExecutorServlet extends LoginAbstractAzkabanServlet {

    private static final Logger logger = LoggerFactory.getLogger(ExecutorServlet.class.getName());
    private static final long serialVersionUID = 1L;
    private WebMetrics webMetrics;
    private ProjectManager projectManager;
    private FlowTriggerService flowTriggerService;
    private ExecutorManagerAdapter executorManagerAdapter;
    private ScheduleManager scheduleManager;
    private TransitionService transitionService;
    private AlerterHolder alerterHolder;


    //历史补采停止集合
    private Map<String, String> repeatStopMap = new HashMap<>();

    private SystemManager systemManager;

    private JobIdRelationService jobIdRelationService;
    private TriggerManager triggerManager;

    @Override
    public void init(final ServletConfig config) throws ServletException {
        super.init(config);
        final AzkabanWebServer server = (AzkabanWebServer) getApplication();
        this.projectManager = server.getProjectManager();
        this.executorManagerAdapter = server.getExecutorManager();
        this.scheduleManager = server.getScheduleManager();
        this.transitionService = server.getTransitionService();
        this.flowTriggerService = server.getFlowTriggerService();
        // TODO: reallocf fully guicify
        this.webMetrics = SERVICE_PROVIDER.getInstance(WebMetrics.class);
        this.jobIdRelationService = SERVICE_PROVIDER.getInstance(JobIdRelationService.class);
        this.alerterHolder = server.getAlerterHolder();
        Props props = executorManagerAdapter.getAzkabanProps();
        this.systemManager = transitionService.getSystemManager();
        this.triggerManager=server.getTriggerManager();

    }

    @Override
    protected void handleGet(final HttpServletRequest req, final HttpServletResponse resp,
        final Session session) throws ServletException, IOException {
        if ("/executor".equals(req.getRequestURI())) {
            if (hasParam(req, "ajax")) {
                handleAJAXAction(req, resp, session);
            } else if (hasParam(req, "execid")) {
                if (hasParam(req, "job") && !hasParam(req, "downloadLog")) {
                    handleExecutionJobDetailsPage(req, resp, session);
                } else if (hasParam(req, "downloadLog")) {
                    handleDownloadExecLog(req, resp, session);
                } else {
                    handleExecutionFlowPageByExecId(req, resp, session);
                }
            } else if (hasParam(req, "triggerinstanceid")) {
                handleExecutionFlowPageByTriggerInstanceId(req, resp, session);
            } else {
                handleExecutionsPage(req, resp, session);
            }
        } else if ("/recover".equals(req.getRequestURI())) {
            if (hasParam(req, "ajax")) {
                handleAJAXAction(req, resp, session);
            } else {
                ajaxHistoryRecoverPage(req, resp, session);
            }
        }
    }

    // handleAJAXAction 函数对请求参数进行解析
    private void handleAJAXAction(final HttpServletRequest req, final HttpServletResponse resp,
        final Session session) throws ServletException, IOException {
        final HashMap<String, Object> ret = new HashMap<>();
        final String ajaxName = getParam(req, "ajax");

        if (hasParam(req, "execid")) {
            final int execid = getIntParam(req, "execid");
            ExecutableFlow exFlow = null;

            try {
                exFlow = this.executorManagerAdapter.getExecutableFlow(execid);
            } catch (final ExecutorManagerException e) {
                ret.put("error",
                    "Error fetching execution '" + execid + "': " + e.getMessage());
            }

            if (exFlow == null) {
                ret.put("error", "Cannot find execution '" + execid + "'");
            } else {
                if (ajaxName.equals("fetchexecflow")) {
                    ajaxFetchExecutableFlow(req, resp, ret, session.getUser(), exFlow);
                } else if (ajaxName.equals("fetchexecflowupdate")) {
                    ajaxFetchExecutableFlowUpdate(req, resp, ret, session.getUser(),
                        exFlow);
                } else if (ajaxName.equals("cancelFlow")) {
                    ajaxCancelFlow(req, resp, ret, session.getUser(), exFlow);
                } else if (ajaxName.equals("pauseFlow")) {
                    ajaxPauseFlow(req, resp, ret, session.getUser(), exFlow);
                } else if (ajaxName.equals("superKillFlow")) {
                    ajaxSuperKillFlow(req, resp, ret, session.getUser(), exFlow);
                } else if (ajaxName.equals("resumeFlow")) {
                    ajaxResumeFlow(req, resp, ret, session.getUser(), exFlow);
					      // FIXME Added interface to set job stream to failed state.
                } else if (ajaxName.equals("ajaxSetFlowFailed")) {
                    ajaxSetFlowFailed(req, resp, ret, session.getUser(), exFlow);
					      // FIXME Added interface to re-run tasks in FAILED_WAITING state.
                } else if (ajaxName.equals("ajaxRetryFailedJobs")) {
                    ajaxRetryFailedJobs(req, resp, ret, session.getUser(), exFlow);
					      // FIXME Added interface to skip tasks in FAILED_WAITING state.
                } else if (ajaxName.equals("ajaxSkipFailedJobs")) {
                    ajaxSkipFailedJobs(req, resp, ret, session.getUser(), exFlow);
                // FIXME Added interface to close task execution.
                } else if (ajaxName.equals("ajaxDisableJob")) {
                    ajaxDisableJob(req, resp, ret, session.getUser(), exFlow);
                } else if (ajaxName.equals("fetchExecFlowLogs")) {
                    ajaxFetchExecFlowLogs(req, resp, ret, session.getUser(), exFlow);
                } else if (ajaxName.equals("fetchExecJobLogs")) {
                    ajaxFetchJobLogs(req, resp, ret, session.getUser(), exFlow);
					      // FIXME Added interface to get the latest bytes of logs.
                } else if (ajaxName.equals("latestLogOffset")) {
                    ajaxGetJobLatestLogOffset(req, resp, ret, session.getUser(), exFlow);
					      // FIXME Added interface to get job stream running parameters, including global variables for task output.
                } else if (ajaxName.equals("getOperationParameters")) {
                    ajaxGetOperationParameters(req, resp, ret, session.getUser(), exFlow);
                } else if (ajaxName.equals("getJobIdRelation")) {
                    ajaxGetJobIdRelation(req, resp, ret, session.getUser(), exFlow);
                } else if (ajaxName.equals("fetchExecJobStats")) {
                    ajaxFetchJobStats(req, resp, ret, session.getUser(), exFlow);
                } else if (ajaxName.equals("retryFailedJobs")) {
                    ajaxRestartFailed(req, resp, ret, session.getUser(), exFlow);
					      // FIXME Added interface to skip all tasks in FAILED_WAITING state.
                } else if (ajaxName.equals("skipAllFailedJobs")) {
                    ajaxSkipAllFailedJobs(req, resp, ret, session.getUser(), exFlow);
                } else if (ajaxName.equals("flowInfo")) {
                    ajaxFetchExecutableFlowInfo(req, resp, ret, session.getUser(), exFlow);
                }
            }
			  // FIXME Added interface to get scheduled job flow information.
        } else if (ajaxName.equals("fetchscheduledflowgraphNew")) {
            final String projectName = getParam(req, "project");
            final String flowName = getParam(req, "flow");
            ajaxFetchscheduledflowgraphNew(projectName, flowName, ret, session.getUser());
        } else if (ajaxName.equals("fetchscheduledflowgraph")) {
            final String projectName = getParam(req, "project");
            final String flowName = getParam(req, "flow");
            ajaxFetchScheduledFlowGraph(projectName, flowName, ret, session.getUser());
        } else if (ajaxName.equals("reloadExecutors")) {
            ajaxReloadExecutors(req, resp, ret, session.getUser());
        } else if (ajaxName.equals("enableQueueProcessor")) {
            ajaxUpdateQueueProcessor(req, resp, ret, session.getUser(), true);
        } else if (ajaxName.equals("disableQueueProcessor")) {
            ajaxUpdateQueueProcessor(req, resp, ret, session.getUser(), false);
        } else if (ajaxName.equals("getRunning")) {
            final String projectName = getParam(req, "project");
            final String flowName = getParam(req, "flow");
            ajaxGetFlowRunning(req, resp, ret, session.getUser(), projectName,
                flowName);
        } else if (ajaxName.equals("flowInfo")) {
            final String projectName = getParam(req, "project");
            final String flowName = getParam(req, "flow");
            ajaxFetchFlowInfo(req, resp, ret, session.getUser(), projectName,
                flowName);
		    // FIXME Added interface to submit historical rerun tasks.
        } else if (ajaxName.equals("repeatCollection")) {
            ajaxAttRepeatExecuteFlow(req, resp, ret, session.getUser());
			  // FIXME Added interface to stop history and rerun ajax method.
        } else if (ajaxName.equals("stopRepeat")) {
            ajaxStopRepeat(req, resp, ret, session.getUser());
			  // FIXME Added interface to get historical rerun data.
        } else if (ajaxName.equals("historyRecover")) {
            ajaxHistoryRecoverPage(req, resp, session);
			  // FIXME Added interface to verify the existence of historical rerun tasks.
        } else if (ajaxName.equals("recoverParamVerify")) {
            ajaxRecoverParamVerify(req, resp, ret, session.getUser());
			  // FIXME Added interface to get information about tasks performed.
        } else if (ajaxName.equals("fetchexecutionflowgraphNew")) {
            final String projectName = getParam(req, "project");
            final String flowName = getParam(req, "flow");
            ajaxFetchExecutionFlowGraphNew(projectName, flowName, ret, session.getUser());
			  // FIXME Added interface to get information about tasks performed.
        } else if (ajaxName.equals("fetchexecutionflowgraph")) {
            final String projectName = getParam(req, "project");
            final String flowName = getParam(req, "flow");
            ajaxFetchExecutionFlowGraph(projectName, flowName, ret, session.getUser());
			// FIXME Added interface to get all currently running job streams.
        } else if (ajaxName.equals("getExecutingFlowData")) {
            ajaxGetExecutingFlowData(req, resp, ret, session.getUser());
			// FIXME Added interface to execute all job streams under the project.
        } else if(ajaxName.equals("executeAllFlow")){
            executeAllFlow(req, resp, ret, session.getUser());
			// FIXME Added interface to get all historical rerun task information.
        } else if(ajaxName.equals("ajaxExecuteAllHistoryRecoverFlow")){
            ajaxExecuteAllHistoryRecoverFlow(req, resp, ret, session.getUser());
			// FIXME Added interface to submit loop execution workflow.
        } else if (ajaxName.equals("submitCycleFlow")) {
            ajaxSubmitCycleFlow(req, resp, ret, session.getUser());
			// FIXME Added interface to stop cyclic execution of workflow.
        } else if (ajaxName.equals("stopCycleFlow")) {
            ajaxStopCycleFlow(req, resp, ret, session.getUser());
			// FIXME Added an interface to verify that the cyclic execution task already exists.
        } else if (ajaxName.equals("cycleParamVerify")) {
            ajaxCycleParamVerify(req, resp, ret, session.getUser());
			// FIXME Added interface to perform cyclic execution tasks.
        } else if (ajaxName.equals("executeFlowCycleFromExecutor")) {
            final String projectId = getParam(req, "projectId");
            final String flowId = getParam(req, "flow");
            try {
                String cycleFlowSubmitUserName = getParam(req, "cycleFlowSubmitUser");
                SystemUserManager systemUserManager = (SystemUserManager)(transitionService.getUserManager()) ;
                User user = systemUserManager.getUser(cycleFlowSubmitUserName);
                ajaxAttemptExecuteFlow(req, resp, ret, user);
                if (ret.get("error") != null && ret.get("code") == null) {
                    logger.error("submit cycle flow failed: " + ret.get("error"));
                    updateCycleFlowKilled(projectId, flowId);
                }
            } catch (UserManagerException e) {
                ret.put("error", "executeFlowCycleFromExecutor error, fetch execute user failed");
                updateCycleFlowKilled(projectId, flowId);
            }
        }
        // FIXME Added interface to get job logs.
        else if (ajaxName.equals("extGetRecentJobLog")) {
            extGetRecentJobLog(req, resp, ret, session.getUser());
        // FIXME Added interface to get job running status.
        } else if (ajaxName.equals("extGetRecentJobStatus")) {
            extGetRecentJobStatus(req, resp, ret, session.getUser());
        // FIXME Added interface to submit a single execution task.
        } else if (ajaxName.equals("extExecuteFlow")) {
            extExecuteFlow(req, resp, ret, session.getUser());
        // FIXME Added interface to terminate job stream.
        } else if (ajaxName.equals("extCancelFlow")) {
            extCancelFlow(req, resp, ret, session.getUser());
        } else if ("reloadWebData".equals(ajaxName)) {
            try {
                reloadWebData(req);
            } catch (TriggerManagerException | ScheduleManagerException e) {
                logger.error(e.getMessage(),e);
            }
        } else {
            final String projectName = getParam(req, "project");

            ret.put("project", projectName);
            if (ajaxName.equals("executeFlow")) {
                ajaxAttemptExecuteFlow(req, resp, ret, session.getUser());
            }
        }
        if (ret != null) {
            this.writeJSON(resp, ret);
        }
    }

    private void ajaxSuperKillFlow(HttpServletRequest req, HttpServletResponse resp, HashMap<String, Object> ret, User user, ExecutableFlow exFlow) {
        final Project project = getProjectAjaxByPermission(ret, exFlow.getProjectId(), user, Type.EXECUTE);
        if (project == null) {
            return;
        }

        try {
            this.executorManagerAdapter.superKillFlow(exFlow, user.getUserId());
        } catch (final ExecutorManagerException e) {
            ret.put("error", e.getMessage());
        }
    }


    private void updateCycleFlowKilled(String prjectId, String flowId) {
        try {
            ExecutionCycle executionCycle = executorManagerAdapter.getExecutionCycleFlow(prjectId, flowId);
            if (executionCycle != null) {
                ExecutableFlow exFlow = this.executorManagerAdapter.getExecutableFlow(executionCycle.getCurrentExecId());
                executionCycle.setStatus(Status.KILLED);
                executionCycle.setEndTime(System.currentTimeMillis());
                executorManagerAdapter.updateExecutionFlow(executionCycle);
                ExecutionControllerUtils.alertOnCycleFlowInterrupt(exFlow, executionCycle, alerterHolder);
            }
        } catch (ExecutorManagerException e) {
            logger.error(String.format("update cycle flow %s : %s cancel status failed", prjectId, flowId ));
        }
    }

    /**
     * 读取executingflowpage.vm及其子页面的国际化资源数据
     * @return
     */
    private Map<String, Map<String, String>> loadExecutingflowpageI18nData() {
        Map<String, Map<String, String>> dataMap = new HashMap<>();
        String languageType = LoadJsonUtils.getLanguageType();
        Map<String, String> executingflowpageMap;
        Map<String, String> subPageMap1;
        Map<String, String> subPageMap2;
        Map<String, String> subPageMap3;
        Map<String, String> subPageMap4;
        if (languageType.equalsIgnoreCase("zh_CN")) {
            // 添加国际化标签
            executingflowpageMap = LoadJsonUtils.transJson("/com.webank.wedatasphere.schedulis.i18n.conf/azkaban-web-server-zh_CN.json",
                "azkaban.webapp.servlet.velocity.executingflowpage.vm");

            subPageMap1 = LoadJsonUtils.transJson("/com.webank.wedatasphere.schedulis.i18n.conf/azkaban-web-server-zh_CN.json",
                "azkaban.webapp.servlet.velocity.nav.vm");

            subPageMap2 = LoadJsonUtils.transJson("/com.webank.wedatasphere.schedulis.i18n.conf/azkaban-web-server-zh_CN.json",
                "azkaban.webapp.servlet.velocity.flow-schedule-ecution-panel.vm");

            subPageMap3 = LoadJsonUtils.transJson("/com.webank.wedatasphere.schedulis.i18n.conf/azkaban-web-server-zh_CN.json",
                "azkaban.webapp.servlet.velocity.messagedialog.vm");

            subPageMap4 = LoadJsonUtils.transJson("/com.webank.wedatasphere.schedulis.i18n.conf/azkaban-web-server-zh_CN.json",
                "azkaban.webapp.servlet.velocity.flowgraphview.vm");
        }else {
            // 添加国际化标签
            executingflowpageMap = LoadJsonUtils.transJson("/com.webank.wedatasphere.schedulis.i18n.conf/azkaban-web-server-en_US.json",
                "azkaban.webapp.servlet.velocity.executingflowpage.vm");

            subPageMap1 = LoadJsonUtils.transJson("/com.webank.wedatasphere.schedulis.i18n.conf/azkaban-web-server-en_US.json",
                "azkaban.webapp.servlet.velocity.nav.vm");

            subPageMap2 = LoadJsonUtils.transJson("/com.webank.wedatasphere.schedulis.i18n.conf/azkaban-web-server-en_US.json",
                "azkaban.webapp.servlet.velocity.flow-schedule-ecution-panel.vm");

            subPageMap3 = LoadJsonUtils.transJson("/com.webank.wedatasphere.schedulis.i18n.conf/azkaban-web-server-en_US.json",
                "azkaban.webapp.servlet.velocity.messagedialog.vm");

            subPageMap4 = LoadJsonUtils.transJson("/com.webank.wedatasphere.schedulis.i18n.conf/azkaban-web-server-en_US.json",
                "azkaban.webapp.servlet.velocity.flowgraphview.vm");
        }

        dataMap.put("executingflowpage.vm", executingflowpageMap);
        dataMap.put("nav.vm", subPageMap1);
        dataMap.put("flow-schedule-ecution-panel.vm", subPageMap2);
        dataMap.put("messagedialog.vm", subPageMap3);
        dataMap.put("flowgraphview.vm", subPageMap4);

        return dataMap;
    }

    /**
     * 加载ExecutorServlet中的异常信息等国际化资源
     * @return
     */
    private Map<String, String> loadExecutorServletI18nData() {
        String languageType = LoadJsonUtils.getLanguageType();
        Map<String, String> dataMap;
        if (languageType.equalsIgnoreCase("zh_CN")) {
            dataMap = LoadJsonUtils.transJson("/com.webank.wedatasphere.schedulis.i18n.conf/azkaban-web-server-zh_CN.json",
                "azkaban.webapp.servlet.ExecutorServlet");
        }else {
            dataMap = LoadJsonUtils.transJson("/com.webank.wedatasphere.schedulis.i18n.conf/azkaban-web-server-en_US.json",
                "azkaban.webapp.servlet.ExecutorServlet");
        }
        return dataMap;
    }

    /**
     * <pre>
     * Enables queueProcessor if @param status is true
     * disables queueProcessor if @param status is false.
     * </pre>
     */
    private void ajaxUpdateQueueProcessor(final HttpServletRequest req, final HttpServletResponse resp,
        final HashMap<String, Object> returnMap, final User user,
        final boolean enableQueue) {
        boolean wasSuccess = false;
        if (HttpRequestUtils.hasPermission(user, Type.ADMIN)) {
            try {
                if (enableQueue) {
                    this.executorManagerAdapter.enableQueueProcessorThread();
                } else {
                    this.executorManagerAdapter.disableQueueProcessorThread();
                }
                returnMap.put(ConnectorParams.STATUS_PARAM, ConnectorParams.RESPONSE_SUCCESS);
                wasSuccess = true;
            } catch (final ExecutorManagerException e) {
                returnMap.put(ConnectorParams.RESPONSE_ERROR, e.getMessage());
            }
        } else {
            returnMap.put(ConnectorParams.RESPONSE_ERROR, "Only Admins are allowed to update queue processor");
        }
        if (!wasSuccess) {
            returnMap.put(ConnectorParams.STATUS_PARAM, ConnectorParams.RESPONSE_ERROR);
        }
    }

    /**
     * 首次执行或者单次执行的时候调用的接口
     * @param projectName
     * @param flowName
     * @param ret
     * @param user
     * @throws ServletException
     */
    private void ajaxFetchscheduledflowgraphNew(final String projectName, final String flowName,
        final HashMap<String, Object> ret, final User user) throws ServletException {
        final Project project = getProjectAjaxByPermission(ret, projectName, user, Type.EXECUTE);
        Map<String, String> stringStringMap = loadExecutorServletI18nData();
        if (project == null) {
            ret.put("error", stringStringMap.get("permissionForAction") + projectName);
            return;
        }
        try {
            final ExecutionOptions executionOptions =  new ExecutionOptions();
            final Flow flow = project.getFlow(flowName);
            if (flow == null) {
                ret.put("error", "Flow '" + flowName + "' cannot be found in project " + project);
                return;
            }
            final ExecutableFlow exFlow = new ExecutableFlow(project, flow);
            exFlow.setExecutionOptions(executionOptions);
            ret.put("submitTime", exFlow.getSubmitTime());
            ret.put("submitUser", exFlow.getSubmitUser());
            ret.put("execid", exFlow.getExecutionId());
            ret.put("projectId", exFlow.getProjectId());
            ret.put("project", project.getName());
            FlowUtils.applyDisabledJobs(executionOptions.getDisabledJobs(), exFlow);
            final Map<String, Object> flowObj = getExecutableNodeInfo(exFlow, exFlow.getExecutionId());
            ret.putAll(flowObj);
        } catch (final Exception ex) {
            throw new ServletException(ex);
        }
    }

    private void ajaxFetchScheduledFlowGraph(final String projectName, final String flowName,
        final HashMap<String, Object> ret, final User user) throws ServletException {
        final Project project = getProjectAjaxByPermission(ret, projectName, user, Type.EXECUTE);
        Map<String, String> stringStringMap = loadExecutorServletI18nData();
        if (project == null) {
            ret.put("error", stringStringMap.get("permissionForAction") + projectName);
            return;
        }
        try {
            final Schedule schedule = this.scheduleManager.getSchedule(project.getId(), flowName);
            // 要读取是否存在调度在执行,但是读取这个调度会拿到调度设置的一些旧数据,影响临时执行
            final ExecutionOptions executionOptions = schedule != null ? schedule.getExecutionOptions() : new ExecutionOptions();
            final Flow flow = project.getFlow(flowName);
            if (flow == null) {
                ret.put("error", "Flow '" + flowName + "' cannot be found in project " + project);
                return;
            }
            final ExecutableFlow exFlow = new ExecutableFlow(project, flow);
            exFlow.setExecutionOptions(executionOptions);
            ret.put("submitTime", exFlow.getSubmitTime());
            ret.put("submitUser", exFlow.getSubmitUser());
            ret.put("execid", exFlow.getExecutionId());
            ret.put("projectId", exFlow.getProjectId());
            ret.put("project", project.getName());
            FlowUtils.applyDisabledJobs(executionOptions.getDisabledJobs(), exFlow);
            final Map<String, Object> flowObj = getExecutableNodeInfo(exFlow, exFlow.getExecutionId());
            ret.putAll(flowObj);
        } catch (final ScheduleManagerException ex) {
            throw new ServletException(ex);
        }
    }

    /**
     * 通过请求重新加载执行节点
     *
     * @param req
     * @param resp
     * @param returnMap
     * @param user
     */
    /* Reloads executors from DB and azkaban.properties via executorManager */
    private void ajaxReloadExecutors(final HttpServletRequest req, final HttpServletResponse resp,
        final HashMap<String, Object> returnMap, final User user) {
        boolean wasSuccess = false;
        if (HttpRequestUtils.hasPermission(user, Type.ADMIN)) {
            try {
                this.executorManagerAdapter.setupExecutors();
                returnMap.put(ConnectorParams.STATUS_PARAM, ConnectorParams.RESPONSE_SUCCESS);
                wasSuccess = true;
            } catch (final ExecutorManagerException e) {
                returnMap.put(ConnectorParams.RESPONSE_ERROR, "Failed to refresh the executors " + e.getMessage());
            }
        } else {
            returnMap.put(ConnectorParams.RESPONSE_ERROR, "Only Admins are allowed to refresh the executors");
        }
        if (!wasSuccess) {
            returnMap.put(ConnectorParams.STATUS_PARAM, ConnectorParams.RESPONSE_ERROR);
        }
    }

    @Override
    protected void handlePost(final HttpServletRequest req, final HttpServletResponse resp,
        final Session session) throws ServletException, IOException {
        if (hasParam(req, "ajax")) {
            handleAJAXAction(req, resp, session);
        }
    }

    private void handleExecutionJobDetailsPage(final HttpServletRequest req, final HttpServletResponse resp,
        final Session session) throws ServletException, IOException {
		    // FIXME globalization.
        final Page page = newPage(req, resp, session, "azkaban/webapp/servlet/velocity/jobdetailspage.vm");

        String languageType = LoadJsonUtils.getLanguageType();
        Map<String, String> jobdetailspageMap;
        Map<String, String> subPageMap1;
        Map<String, String> subPageMap2;
        Map<String, String> subPageMap3;
        if (languageType.equalsIgnoreCase("zh_CN")) {
            jobdetailspageMap = LoadJsonUtils.transJson("/com.webank.wedatasphere.schedulis.i18n.conf/azkaban-web-server-zh_CN.json",
                "azkaban.webapp.servlet.velocity.jobdetailspage.vm");
            subPageMap1 = LoadJsonUtils.transJson("/com.webank.wedatasphere.schedulis.i18n.conf/azkaban-web-server-zh_CN.json",
                "azkaban.webapp.servlet.velocity.nav.vm");
            subPageMap2 = LoadJsonUtils.transJson("/com.webank.wedatasphere.schedulis.i18n.conf/azkaban-web-server-zh_CN.json",
                "azkaban.webapp.servlet.velocity.jobdetailsheader.vm");
            subPageMap3 = LoadJsonUtils.transJson("/com.webank.wedatasphere.schedulis.i18n.conf/azkaban-web-server-zh_CN.json",
                "azkaban.webapp.servlet.velocity.log-auto-refresh-option.vm");
        }else {
            jobdetailspageMap = LoadJsonUtils.transJson("/com.webank.wedatasphere.schedulis.i18n.conf/azkaban-web-server-en_US.json",
                "azkaban.webapp.servlet.velocity.jobdetailspage.vm");
            subPageMap1 = LoadJsonUtils.transJson("/com.webank.wedatasphere.schedulis.i18n.conf/azkaban-web-server-en_US.json",
                "azkaban.webapp.servlet.velocity.nav.vm");
            subPageMap2 = LoadJsonUtils.transJson("/com.webank.wedatasphere.schedulis.i18n.conf/azkaban-web-server-en_US.json",
                "azkaban.webapp.servlet.velocity.jobdetailsheader.vm");
            subPageMap3 = LoadJsonUtils.transJson("/com.webank.wedatasphere.schedulis.i18n.conf/azkaban-web-server-en_US.json",
                "azkaban.webapp.servlet.velocity.log-auto-refresh-option.vm");
        }

        jobdetailspageMap.forEach(page::add);
        subPageMap1.forEach(page::add);
        subPageMap2.forEach(page::add);
        subPageMap3.forEach(page::add);

        final User user = session.getUser();
        final int execId = getIntParam(req, "execid");
        final String jobId = getParam(req, "job");
        final int attempt = getIntParam(req, "attempt", 0);
        page.add("execid", execId);
        page.add("jobid", jobId);
        page.add("attempt", attempt);

        ExecutableFlow flow = null;
        ExecutableNode node = null;
        final String jobLinkUrl;
        try {
            flow = this.executorManagerAdapter.getExecutableFlow(execId);
            if (flow == null) {
                page.add("errorMsg", "Error loading executing flow " + execId + ": not found.");
                page.render();
                return;
            }

            node = flow.getExecutableNodePath(jobId);
            if (node == null) {
                page.add("errorMsg", "Job " + jobId + " doesn't exist in " + flow.getExecutionId());
                return;
            }

            jobLinkUrl = this.executorManagerAdapter.getJobLinkUrl(flow, jobId, attempt);

            final List<ViewerPlugin> jobViewerPlugins = PluginRegistry.getRegistry().getViewerPluginsForJobType(node.getType());
            page.add("jobViewerPlugins", jobViewerPlugins);
        } catch (final ExecutorManagerException e) {
            page.add("errorMsg", "Error loading executing flow: " + e.getMessage());
            page.render();
            return;
        }

        final int projectId = flow.getProjectId();
        final Project project = getProjectPageByPermission(page, projectId, user, Type.READ);
        if (project == null) {
            page.render();
            return;
        }

        page.add("projectName", project.getName());
        page.add("flowid", flow.getId());
        page.add("parentflowid", node.getParentFlow().getFlowId());
        page.add("jobname", node.getId());
        page.add("jobLinkUrl", jobLinkUrl);
        page.add("jobType", node.getType());

        if (node.getStatus() == Status.FAILED || node.getStatus() == Status.KILLED) {
            page.add("jobFailed", true);
        } else {
            page.add("jobFailed", false);
        }
        page.add("currentlangType", languageType);
        page.render();
    }

    /**
     * 正在运行模块页面
     *
     * @param req
     * @param resp
     * @param session
     * @throws ServletException
     * @throws IOException
     */
    private void handleExecutionsPage(final HttpServletRequest req, final HttpServletResponse resp,
        final Session session) throws ServletException, IOException {
		    // FIXME globalization.
        final Page page = newPage(req, resp, session, "azkaban/webapp/servlet/velocity/executionspage.vm");

        String languageType = LoadJsonUtils.getLanguageType();
        Map<String, String> executionspageMap;
        Map<String, String> subPageMap1;
        if (languageType.equalsIgnoreCase("zh_CN")) {
            executionspageMap = LoadJsonUtils.transJson("/com.webank.wedatasphere.schedulis.i18n.conf/azkaban-web-server-zh_CN.json",
                "azkaban.webapp.servlet.velocity.executionspage.vm");
            subPageMap1 = LoadJsonUtils.transJson("/com.webank.wedatasphere.schedulis.i18n.conf/azkaban-web-server-zh_CN.json",
                "azkaban.webapp.servlet.velocity.nav.vm");
        }else {
            executionspageMap = LoadJsonUtils.transJson("/com.webank.wedatasphere.schedulis.i18n.conf/azkaban-web-server-en_US.json",
                "azkaban.webapp.servlet.velocity.executionspage.vm");
            subPageMap1 = LoadJsonUtils.transJson("/com.webank.wedatasphere.schedulis.i18n.conf/azkaban-web-server-en_US.json",
                "azkaban.webapp.servlet.velocity.nav.vm");
        }

        executionspageMap.forEach(page::add);
        subPageMap1.forEach(page::add);
        page.add("currentlangType", languageType);
        //获取用户权限
        User user = session.getUser();
        Set<String> userRoleSet = new HashSet<>();
        userRoleSet.addAll(user.getRoles());

        final List<Pair<ExecutableFlow, Optional<Executor>>> runningFlows = this.executorManagerAdapter.getActiveFlowsWithExecutor();
        page.add("runningFlows", runningFlows.isEmpty() ? null : runningFlows);

        final List<ExecutableFlow> finishedFlows = this.executorManagerAdapter.getRecentlyFinishedFlows();

        // FIXME Add permission judgment, admin user can view all flow history user user can only view their own flow history.
        if (userRoleSet.contains("admin")) {

            page.add("runningFlows", runningFlows.isEmpty() ? null : runningFlows);

            page.add("recentlyFinished", finishedFlows.isEmpty() ? null : finishedFlows);

        } else if (systemManager.isDepartmentMaintainer(user)) {
          List<Integer> projectIds = systemManager.getMaintainedProjects(user);
          List<Pair<ExecutableFlow, Optional<Executor>>> maintainedRunningFlows = runningFlows.stream()
                  .filter(pair -> projectIds.contains(pair.getFirst().getProjectId())
                          || user.getUserId().equals(pair.getFirst().getSubmitUser()))
                  .collect(Collectors.toList());
          page.add("runningFlows", maintainedRunningFlows.isEmpty() ? null : maintainedRunningFlows);
          List<ExecutableFlow> maintainedFinishedFlows = finishedFlows.stream()
                  .filter(flow -> projectIds.contains(flow.getProjectId())
                          || user.getUserId().equals(flow.getSubmitUser()))
                  .collect(Collectors.toList());
          page.add("recentlyFinished", maintainedFinishedFlows.isEmpty() ? null : maintainedFinishedFlows);
        } else {

            final List<Pair<ExecutableFlow, Optional<Executor>>> userRunningFlows = runningFlows.stream()
                .filter(pair ->
                    user.getUserId().equals(pair.getFirst().getSubmitUser())
                ).collect(Collectors.toList());
            page.add("runningFlows", userRunningFlows.isEmpty() ? null : userRunningFlows);
            List<Integer> projectIds = this.executorManagerAdapter.fetchPermissionsProjectId(user.getUserId());

            final List<ExecutableFlow> userFinishedFlows = finishedFlows.stream()
                .filter(finishFlow ->
                    hasPermission(finishFlow.getProjectId(), projectIds) || user.getUserId().equals(finishFlow.getSubmitUser())
                )
                .collect(Collectors.toList());

            page.add("recentlyFinished", userFinishedFlows.isEmpty() ? null : userFinishedFlows);

        }
        page.add("vmutils", new VelocityUtil(this.projectManager));
        page.render();
    }

    private void handleExecutionFlowPageByTriggerInstanceId(final HttpServletRequest req, final HttpServletResponse resp,
        final Session session) throws ServletException, IOException {
        final Page page = newPage(req, resp, session, "azkaban/webapp/servlet/velocity/executingflowpage.vm");
        final User user = session.getUser();
        final String triggerInstanceId = getParam(req, "triggerinstanceid");

        final TriggerInstance triggerInst = this.flowTriggerService.findTriggerInstanceById(triggerInstanceId);

        if (triggerInst == null) {
            page.add("errorMsg", "Error loading trigger instance " + triggerInstanceId + " not found.");
            page.render();
            return;
        }

        // FIXME Load international resources.
        Map<String, Map<String, String>> dataMap = loadExecutingflowpageI18nData();
        dataMap.forEach((vm, data) -> data.forEach(page::add));

        page.add("triggerInstanceId", triggerInstanceId);
        page.add("execid", triggerInst.getFlowExecId());

        final int projectId = triggerInst.getProject().getId();
        final Project project = getProjectPageByPermission(page, projectId, user, Type.READ);

        if (project == null) {
            page.render();
            return;
        }

        addExternalLinkLabel(req, page);

        page.add("projectId", project.getId());
        page.add("projectName", project.getName());
        page.add("flowid", triggerInst.getFlowId());

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
        String languageType = LoadJsonUtils.getLanguageType();

        page.add("currentlangType", languageType);

        page.render();
    }

    private void addExternalLinkLabel(final HttpServletRequest req, final Page page) {
        final Props props = getApplication().getServerProps();
        final String execExternalLinkURL = ExternalLinkUtils.getExternalAnalyzerOnReq(props, req);

        if (execExternalLinkURL.length() > 0) {
            page.add("executionExternalLinkURL", execExternalLinkURL);
            logger.debug("Added an External analyzer to the page");
            logger.debug("External analyzer url: " + execExternalLinkURL);

            final String execExternalLinkLabel =
                props.getString(Constants.ConfigurationKeys.AZKABAN_SERVER_EXTERNAL_ANALYZER_LABEL, "External Analyzer");
            page.add("executionExternalLinkLabel", execExternalLinkLabel);
            logger.debug("External analyzer label set to : " + execExternalLinkLabel);
        }
    }

    private void handleExecutionFlowPageByExecId(final HttpServletRequest req, final HttpServletResponse resp,
        final Session session) throws ServletException, IOException {
        final Page page = newPage(req, resp, session, "azkaban/webapp/servlet/velocity/executingflowpage.vm");
        final User user = session.getUser();
        final int execId = getIntParam(req, "execid");
        //当前节点的NestedId,如果查看整个工作流,则是空
        final String nodeNestedId = getParam(req, "nodeNestedId", "");
        final String flowId = getParam(req, "flow", null);
        page.add("execid", execId);
        page.add("triggerInstanceId", "-1");
        page.add("loginUser", user.getUserId());
        page.add("nodeNestedId", nodeNestedId);

        // 加载国际化资源
        Map<String, Map<String, String>> dataMap = loadExecutingflowpageI18nData();
        dataMap.forEach((vm, data) -> data.forEach(page::add));

        ExecutableFlow flow = null;
        try {
            flow = this.executorManagerAdapter.getExecutableFlow(execId);
            if (flow == null) {
                page.add("errorMsg", "Error loading executing flow " + execId + " not found.");
                page.render();
                return;
            }
        } catch (final ExecutorManagerException e) {
            page.add("errorMsg", "Error loading executing flow: " + e.getMessage());
            page.render();
            return;
        }

        final int projectId = flow.getProjectId();
        final Project project = getProjectPageByPermission(page, projectId, user, Type.READ);
        if (project == null) {
            page.render();
            return;
        }

        addExternalLinkLabel(req, page);

        page.add("projectId", project.getId());
        page.add("projectName", project.getName());
        page.add("flowid", flowId == null ? flow.getFlowId(): flowId);

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
        String languageType = LoadJsonUtils.getLanguageType();
        page.add("currentlangType", languageType);
        page.render();
    }

    protected Project getProjectPageByPermission(final Page page, final int projectId,
        final User user, final Type type) {
        final Project project = this.projectManager.getProject(projectId);

        Map<String, String> dataMap = loadExecutorServletI18nData();

        if (project == null) {
            page.add("errorMsg", dataMap.get("program") + project + dataMap.get("notExist"));
        } else if (!hasPermission(project, user, type)) {
            page.add("errorMsg", "User " + user.getUserId() + " doesn't have " + type.name()
                + " permissions on " + project.getName());
        } else {
            return project;
        }

        return null;
    }

    protected Project getProjectAjaxByPermission(final Map<String, Object> ret, final String projectName,
        final User user, final Type type) {
        final Project project = this.projectManager.getProject(projectName);

        Map<String, String> dataMap = loadExecutorServletI18nData();

        if (project == null) {
            ret.put("error", dataMap.get("program") + projectName + dataMap.get("notExist"));
        } else if (!hasPermission(project, user, type)) {
            ret.put("error", "User " + user.getUserId() + " doesn't have " + project.getName() + " of " + type.name()
                + " permissions, please contact with the project creator.");
        } else {
            return project;
        }

        return null;
    }

    protected Project getProjectAjaxByPermission(final Map<String, Object> ret, final int projectId,
        final User user, final Type type) {

        final Project project = this.projectManager.getProject(projectId);

        Map<String, String> dataMap = loadExecutorServletI18nData();
        if (project == null) {
            ret.put("error", dataMap.get("program") + projectId + dataMap.get("notExist"));
        } else if (!hasPermission(project, user, type)) {
            ret.put("error", "User '" + user.getUserId() + "' doesn't have " + type.name() + " permissions on " + project.getName());
        } else {
            return project;
        }

        return null;
    }

    private void ajaxRestartFailed(final HttpServletRequest req, final HttpServletResponse resp,
        final HashMap<String, Object> ret, final User user,
        final ExecutableFlow exFlow) throws ServletException {
        final Project project = getProjectAjaxByPermission(ret, exFlow.getProjectId(), user, Type.EXECUTE);
        if (project == null) {
            return;
        }

        if (exFlow.getStatus() == Status.FAILED || exFlow.getStatus() == Status.SUCCEEDED) {
            ret.put("error", "Flow has already finished. Please re-execute.");
            return;
        }

        try {
            this.executorManagerAdapter.retryFailures(exFlow, user.getUserId());
        } catch (final ExecutorManagerException e) {
            ret.put("error", e.getMessage());
        }
    }

    private void ajaxSkipAllFailedJobs(final HttpServletRequest req, final HttpServletResponse resp,
        final HashMap<String, Object> ret, final User user,
        final ExecutableFlow exFlow) throws ServletException {
        final Project project = getProjectAjaxByPermission(ret, exFlow.getProjectId(), user, Type.EXECUTE);
        if (project == null) {
            return;
        }

        if (exFlow.getStatus() == Status.FAILED || exFlow.getStatus() == Status.SUCCEEDED) {
            logger.error("Flow has already finished. Please re-execute.");
            ret.put("error", "Flow has already finished. Please re-execute.");
            return;
        }

        try {
            logger.info("execId: {}, user: {} use skip all failed jobs.", exFlow.getExecutionId(), user.getUserId());
            this.executorManagerAdapter.skipAllFailures(exFlow, user.getUserId());
        } catch (final ExecutorManagerException e) {
            ret.put("error", e.getMessage());
        }
    }

    /**
     * Gets the logs through plain text stream to reduce memory overhead.
     */
    private void ajaxFetchExecFlowLogs(final HttpServletRequest req, final HttpServletResponse resp,
        final HashMap<String, Object> ret, final User user,
        final ExecutableFlow exFlow) throws ServletException {
        final long startMs = System.currentTimeMillis();
        final Project project = getProjectAjaxByPermission(ret, exFlow.getProjectId(), user, Type.READ);
        if (project == null) {
            return;
        }

        final int offset = this.getIntParam(req, "offset");
        final int length = this.getIntParam(req, "length");

        resp.setCharacterEncoding("utf-8");


        //获取当前查看的Job的执行状态
        ret.put("status", Status.isStatusFinished(exFlow.getStatus()) ? "Finish" : "Runing");

        try {
            final LogData data = this.executorManagerAdapter.getExecutableFlowLog(exFlow, offset, length);
            if (data == null) {
                ret.put("length", 0);
                ret.put("offset", offset);
                ret.put("data", "");
            } else {
                ret.put("length", data.getLength());
                ret.put("offset", data.getOffset());
                String htmlStr = StringEscapeUtils.escapeHtml(data.getData());
                htmlStr = StringEscapeUtils.unescapeHtml(htmlStr);
                ret.put("data", htmlStr);
            }
        } catch (final ExecutorManagerException e) {
            throw new ServletException("get executed LogData failed {}" + e);
        }

        /*
         * We originally consider leverage Drop Wizard's Timer API {@link com.codahale.metrics.Timer}
         * to measure the duration time.
         * However, Timer will result in too many accompanying metrics (e.g., min, max, 99th quantile)
         * regarding one metrics. We decided to use gauge to do that and monitor how it behaves.
         */
        this.webMetrics.setFetchLogLatency(System.currentTimeMillis() - startMs);
    }

    /**
     * 获取 offset = fileSize - len
     * @throws ServletException
     */
    private void ajaxGetJobLatestLogOffset(final HttpServletRequest req, final HttpServletResponse resp,
        final HashMap<String, Object> ret, final User user,
        final ExecutableFlow exFlow) throws ServletException {
        final Project project = getProjectAjaxByPermission(ret, exFlow.getProjectId(), user, Type.READ);
        if (project == null) {
            return;
        }
        Long len = this.getLongParam(req, "len");
        String jobId = this.getParam(req, "jobId");
        int attempt = this.getIntParam(req, "attempt");
        try {
            Long latestLogOffset = this.executorManagerAdapter.getLatestLogOffset(exFlow, jobId, len, attempt, user);
            ret.put("offset", latestLogOffset);
        } catch (final ExecutorManagerException e) {
            logger.error("get log offset failed.", e);
            ret.put("error", "get log offset failed, please try again.");
        }
    }

    /**
     * 获取作业流运行参数
     * @throws ServletException
     */
    private void ajaxGetOperationParameters(final HttpServletRequest req, final HttpServletResponse resp,
                                           final HashMap<String, Object> ret, final User user,
                                           final ExecutableFlow exFlow) throws ServletException {
        final Project project = getProjectAjaxByPermission(ret, exFlow.getProjectId(), user, Type.READ);
        if (project == null) {
            return;
        }
        ret.put("flowParams", exFlow.getExecutionOptions().getFlowParameters());
        ret.put("jobOutputGlobalParams", exFlow.getJobOutputGlobalParam());
    }

    /**
     * 获取jobid 关系信息
     * @throws ServletException
     */
    private void ajaxGetJobIdRelation(final HttpServletRequest req, final HttpServletResponse resp,
                                            final HashMap<String, Object> ret, final User user,
                                            final ExecutableFlow exFlow) throws ServletException {
        final Project project = getProjectAjaxByPermission(ret, exFlow.getProjectId(), user, Type.READ);
        if (project == null) {
            return;
        }
        try {
            Integer execId = getIntParam(req, "execid");
            String jobNamePath = getParam(req, "nested_id");
            ret.put("data", jobIdRelationService.getJobIdRelation(execId, jobNamePath));
        }catch (Exception e){
            logger.error("get job id relation failed", e);
            ret.put("error", e.getMessage());
        }

    }


    /**
     * Gets the logs through ajax plain text stream to reduce memory overhead.
     */
    private void ajaxFetchJobLogs(final HttpServletRequest req, final HttpServletResponse resp,
        final HashMap<String, Object> ret, final User user,
        final ExecutableFlow exFlow) throws ServletException {
        final Project project = getProjectAjaxByPermission(ret, exFlow.getProjectId(), user, Type.READ);
        if (project == null) {
            return;
        }

        final int offset = this.getIntParam(req, "offset");
        final int length = this.getIntParam(req, "length");

        final String jobId = this.getParam(req, "jobId");
        resp.setCharacterEncoding("utf-8");

        try {
            final ExecutableNode node = exFlow.getExecutableNodePath(jobId);
            if (node == null) {
                ret.put("error", "Job " + jobId + " doesn't exist in " + exFlow.getExecutionId());
                return;
            }
            //获取当前查看的Job的执行状态
            ret.put("status", Status.isStatusFinished(node.getStatus()) ? "Finish" : "Runing");

            final int attempt = this.getIntParam(req, "attempt", node.getAttempt());
            //获取前端传送过来的日志过滤类型
            final String logType = this.getParam(req, "logType");

            if ("refresh".equals(logType) || "".equals(logType)) {//不过滤日志
                final LogData data = this.executorManagerAdapter.getExecutionJobLog(exFlow, jobId, offset, length,
                    attempt);
                if (data == null) {
                    ret.put("length", 0);
                    ret.put("offset", offset);
                    ret.put("data", "");
                } else {
                    String logData = data.getData();

                    ret.put("length", data.getLength());
                    ret.put("offset", data.getOffset());
                    String htmlStr = StringEscapeUtils.escapeHtml(logData);
                    // FIXME Mark the exception log in red.
                    String formatLog = LogErrorCodeFilterUtils.handleErrorLogMarkedRed(htmlStr);
                    ret.put("data", formatLog);
                }
				    // FIXME Filter out error logs or INFO-level logs.
            } else if ("error".equals(logType) || "info".equals(logType)) {
                //获取Job所有日志
                final String data = this.executorManagerAdapter.getAllExecutionJobLog(exFlow, jobId, attempt);
                final List<LogFilterEntity> logFilterList = this.executorManagerAdapter.listAllLogFilter();

                //按级别过滤出需要的日志
                String logData = LogErrorCodeFilterUtils.handleLogDataFilter(data, logType, logFilterList);

                ret.put("length", 0);
                ret.put("offset", 0);
                String htmlStr = StringEscapeUtils.escapeHtml(logData);
                ret.put("data", htmlStr);
				    // FIXME Filter out the applicationID in the yarn log.
            } else if ("yarn".equals(logType)) {
                //获取Job所有日志
                final String data = this.executorManagerAdapter.getAllExecutionJobLog(exFlow, jobId, attempt);

                //按级别过滤出需要的日志
                String logData = LogErrorCodeFilterUtils.handleYarnLogDataFilter(data);

                ret.put("length", 0);
                ret.put("offset", 0);
                String htmlStr = StringEscapeUtils.escapeHtml(logData);
                ret.put("data", htmlStr);
            }

        } catch (final ExecutorManagerException e) {
            logger.error("fetch log failed.", e);
            ret.put("error", "fetch log failed, please try again.");
        }
    }

    private void ajaxFetchJobStats(final HttpServletRequest req, final HttpServletResponse resp,
        final HashMap<String, Object> ret, final User user,
        final ExecutableFlow exFlow) throws ServletException {
        final Project project = getProjectAjaxByPermission(ret, exFlow.getProjectId(), user, Type.READ);
        if (project == null) {
            return;
        }

        final String jobId = this.getParam(req, "jobid");
        resp.setCharacterEncoding("utf-8");

        try {
            final ExecutableNode node = exFlow.getExecutableNodePath(jobId);
            if (node == null) {
                ret.put("error", "Job " + jobId + " doesn't exist in " + exFlow.getExecutionId());
                return;
            }

            final List<Object> jsonObj = this.executorManagerAdapter.getExecutionJobStats(exFlow, jobId, node.getAttempt());
            ret.put("jobStats", jsonObj);
        } catch (final ExecutorManagerException e) {
            ret.put("error", "Error retrieving stats for job " + jobId);
            return;
        }
    }

    private void ajaxFetchFlowInfo(final HttpServletRequest req, final HttpServletResponse resp,
        final HashMap<String, Object> ret, final User user,
        final String projectName, final String flowId) throws ServletException {
        final Project project = getProjectAjaxByPermission(ret, projectName, user, Type.READ);
        if (project == null) {
            return;
        }

        final Flow flow = project.getFlow(flowId);
        if (flow == null) {
            ret.put("error", "Error loading flow. Flow " + flowId + " doesn't exist in " + projectName);
            return;
        }

        ret.put("successEmails", flow.getSuccessEmails());
        ret.put("failureEmails", flow.getFailureEmails());

        Schedule sflow = null;
        try {
            for (final Schedule sched : this.scheduleManager.getSchedules()) {
                if (sched.getProjectId() == project.getId() && sched.getFlowName().equals(flowId)) {
                    sflow = sched;
                    break;
                }
            }
        } catch (final ScheduleManagerException e) {
            // TODO Auto-generated catch block
            throw new ServletException(e);
        }

        if (sflow != null) {
            ret.put("scheduled", sflow.getNextExecTime());
        }
    }

    private void ajaxFetchExecutableFlowInfo(final HttpServletRequest req, final HttpServletResponse resp,
        final HashMap<String, Object> ret, final User user,
        final ExecutableFlow exflow) throws ServletException {

        final Project project = getProjectAjaxByPermission(ret, exflow.getProjectId(), user, Type.READ);
        if (project == null) {
            return;
        }

        final Flow flow = project.getFlow(exflow.getFlowId());
        if (flow == null) {
            ret.put("error", "Error loading flow. Flow " + exflow.getFlowId() + " doesn't exist in " + exflow.getProjectId());
            return;
        }

        final ExecutionOptions options = exflow.getExecutionOptions();
        ret.put("flowType", exflow.getFlowType());
        ret.put("successEmails", options.getSuccessEmails());
        ret.put("failureEmails", options.getFailureEmails());
        ret.put("flowParam", options.getFlowParameters());
		// FIXME Returns the global variable parameters of the task output.
        ret.put("jobOutputGlobalParam", exflow.getJobOutputGlobalParam());
        ret.put("nsWtss", exflow.getNsWtss());
		// FIXME If it is a historical rerun task, the run_date date is returned and echoed to the job stream parameters.
        if(exflow.getFlowType() == 2 && options.getFlowParameters().get("run_date") == null) {
            setRunDate(ret, exflow);
        }

        final FailureAction action = options.getFailureAction();
        String failureAction = null;
        switch (action) {
            case FINISH_CURRENTLY_RUNNING:
                failureAction = "finishCurrent";
                break;
            case CANCEL_ALL:
                failureAction = "cancelImmediately";
                break;
            case FINISH_ALL_POSSIBLE:
                failureAction = "finishPossible";
                break;
            case FAILED_PAUSE:
                failureAction = "failedPause";
                break;
        }
        ret.put("failureAction", failureAction);

        ret.put("notifyFailureFirst", options.getNotifyOnFirstFailure());
        ret.put("notifyFailureLast", options.getNotifyOnLastFailure());

        ret.put("failureEmailsOverride", options.isFailureEmailsOverridden());
        ret.put("successEmailsOverride", options.isSuccessEmailsOverridden());

        ret.put("concurrentOptions", options.getConcurrentOption());
        ret.put("pipelineLevel", options.getPipelineLevel());
        ret.put("pipelineExecution", options.getPipelineExecutionId());
        ret.put("queueLevel", options.getQueueLevel());

        final HashMap<String, String> nodeStatus = new HashMap<>();
        for (final ExecutableNode node : exflow.getExecutableNodes()) {
            nodeStatus.put(node.getId(), node.getStatus().toString());
        }
        ret.put("nodeStatus", nodeStatus);
        ret.put("disabled", options.getDisabledJobs());

        // FIXME Returns alarm information, which is used to prepare to perform page data echo.
        boolean useTimeoutSetting;
        List<SlaOption> slaOptions = exflow.getSlaOptions();
        // Only the non scheduled scheduling type is displayed.
        if (exflow.getFlowType() != 3 && CollectionUtils.isNotEmpty(slaOptions)) {
            useTimeoutSetting = true;
            List<String> slaEmails = new ArrayList<>();
            String type="FlowSucceed";
            String duration="";
            String emailAction="";
            String killAction="";
            String level="INFO";
            String slaAlertType = "email";
            for (SlaOption slaOption : slaOptions) {
                slaEmails = (List<String>) slaOption.getInfo().get(SlaOption.INFO_EMAIL_LIST);
                type = slaOption.getType();
                level = slaOption.getLevel();
                slaAlertType = (String)slaOption.getInfo().get(SlaOption.ALERT_TYPE);

                duration = (String)slaOption.getInfo().get(SlaOption.INFO_TIME_SET);
                emailAction = (String)slaOption.getInfo().get(SlaOption.INFO_EMAIL_ACTION_SET);
                killAction = (String)slaOption.getInfo().get(SlaOption.INFO_KILL_FLOW_ACTION_SET);

            }
            ret.put("slaEmails", slaEmails);
            ret.put("ruleType", type);
            ret.put("duration", duration);
            ret.put("emailAction", emailAction);
            ret.put("killAction", killAction);
            ret.put("slaAlertLevel", level);
            ret.put("slaAlertType", slaAlertType);
        } else {
            useTimeoutSetting = false;
            ret.put("slaEmails", null);
        }
        ret.put("useTimeoutSetting", useTimeoutSetting);

        // FIXME Returns the execution failure setting, which is used to prepare the page data for execution.
        Map<String, Object> otherOption = exflow.getOtherOption();

        if (MapUtils.isNotEmpty(otherOption)) {

            String failureAlertLevel = (String)otherOption.get("failureAlertLevel");
            String successAlertLevel = (String)otherOption.get("successAlertLevel");
            ret.put("failureAlertLevel", failureAlertLevel);
            ret.put("successAlertLevel", successAlertLevel);

            List<Map<String, String>> jobFailedRetryOptions = (List<Map<String, String>>) otherOption.get("jobFailedRetryOptions");
            ret.put("jobFailedRetryOptions", jobFailedRetryOptions);

            List<String> jobSkipList = (List<String>) otherOption.get("jobSkipFailedOptions");
            ret.put("jobSkipFailedOptions", jobSkipList);

            ret.put("otherOption", otherOption);
        }

    }

    private void ajaxCancelFlow(final HttpServletRequest req, final HttpServletResponse resp,
        final HashMap<String, Object> ret, final User user, final ExecutableFlow exFlow)
        throws ServletException {
        final Project project = getProjectAjaxByPermission(ret, exFlow.getProjectId(), user, Type.EXECUTE);
        if (project == null) {
            return;
        }

        try {
            this.executorManagerAdapter.cancelFlow(exFlow, user.getUserId());
        } catch (final ExecutorManagerException e) {
            ret.put("error", e.getMessage());
        }
    }

    private void ajaxGetFlowRunning(final HttpServletRequest req,
        final HttpServletResponse resp, final HashMap<String, Object> ret, final User user,
        final String projectId, final String flowId) throws ServletException {
        final Project project = getProjectAjaxByPermission(ret, projectId, user, Type.EXECUTE);
        if (project == null) {
            return;
        }

        final List<Integer> refs = this.executorManagerAdapter.getRunningFlows(project.getId(), flowId);
        if (!refs.isEmpty()) {
            ret.put("execIds", refs);
        }
    }

    private void ajaxPauseFlow(final HttpServletRequest req, final HttpServletResponse resp,
        final HashMap<String, Object> ret, final User user, final ExecutableFlow exFlow)
        throws ServletException {
        final Project project = getProjectAjaxByPermission(ret, exFlow.getProjectId(), user, Type.EXECUTE);
        if (project == null) {
            return;
        }

        try {
            this.executorManagerAdapter.pauseFlow(exFlow, user.getUserId());
        } catch (final ExecutorManagerException e) {
            ret.put("error", e.getMessage());
        }
    }

    private void ajaxResumeFlow(final HttpServletRequest req, final HttpServletResponse resp,
        final HashMap<String, Object> ret, final User user, final ExecutableFlow exFlow)
        throws ServletException {
        final Project project = getProjectAjaxByPermission(ret, exFlow.getProjectId(), user, Type.EXECUTE);
        if (project == null) {
            return;
        }

        try {
            this.executorManagerAdapter.resumeFlow(exFlow, user.getUserId());
        } catch (final ExecutorManagerException e) {
            ret.put("resume", e.getMessage());
        }
    }

    private void ajaxSetFlowFailed(final HttpServletRequest req, final HttpServletResponse resp,
        final HashMap<String, Object> ret, final User user, final ExecutableFlow exFlow)
        throws ServletException {
        final Project project = getProjectAjaxByPermission(ret, exFlow.getProjectId(), user, Type.EXECUTE);
        if (project == null) {
            return;
        }
        try {
            List<Pair<String, String>> paramList = new ArrayList<>();
            paramList.add(new Pair<String, String>("flowFailed", getParam(req, "flowFailed")));
            paramList.add(new Pair<>(ConnectorParams.ACTION_PARAM, ConnectorParams.FLOW_FAILED_ACTION));
            paramList.add(new Pair<>(ConnectorParams.EXECID_PARAM, String.valueOf(exFlow.getExecutionId())));
            paramList.add(new Pair<>(ConnectorParams.USER_PARAM, user.getUserId()));
            this.executorManagerAdapter.setFlowFailed(exFlow, user.getUserId(), paramList);
        } catch (final Exception e) {
            ret.put("error", e.getMessage());
        }
    }

    private void ajaxDisableJob(final HttpServletRequest req, final HttpServletResponse resp,
        final HashMap<String, Object> ret, final User user, final ExecutableFlow exFlow)
        throws ServletException {
        final Project project = getProjectAjaxByPermission(ret, exFlow.getProjectId(), user, Type.EXECUTE);
        if (project == null) {
            logger.error("no permission, " + user);
            return;
        }
        String response = null;
        try {
            JsonObject request = HttpRequestUtils.parseRequestToJsonObject(req);
            response = this.executorManagerAdapter.setJobDisabled(exFlow, user.getUserId(), request.toString());
            if(response == null){
                ret.put("error", "Request Failed");
            }
            JsonObject result = new JsonParser().parse(response).getAsJsonObject();
            if (result.has("error")) {
                ret.put("error", result.get("error").getAsString());
            }
        } catch (final Exception e) {
            ret.put("error", e.getMessage());
        }
    }

    private void ajaxRetryFailedJobs(final HttpServletRequest req, final HttpServletResponse resp,
        final HashMap<String, Object> ret, final User user, final ExecutableFlow exFlow)
        throws ServletException {
        final Project project = getProjectAjaxByPermission(ret, exFlow.getProjectId(), user, Type.EXECUTE);
        if (project == null) {
            logger.error("no permission, " + user);
            return;
        }
        String response = null;
        try {
            JsonObject request = HttpRequestUtils.parseRequestToJsonObject(req);
            response = this.executorManagerAdapter.retryFailedJobs(exFlow, user.getUserId(), request.toString());
            if(response == null){
                ret.put("error", "Request Failed");
            }
            JsonObject result = new JsonParser().parse(response).getAsJsonObject();
            if (result.has("error")) {
                ret.put("error", result.get("error").getAsString());
            }
        } catch (final Exception e) {
            ret.put("error", e.getMessage());
        }
    }

    private void ajaxSkipFailedJobs(final HttpServletRequest req, final HttpServletResponse resp,
        final HashMap<String, Object> ret, final User user, final ExecutableFlow exFlow)
        throws ServletException {
        final Project project = getProjectAjaxByPermission(ret, exFlow.getProjectId(), user, Type.EXECUTE);
        if (project == null) {
            logger.error("no permission, " + user);
            return;
        }
        String response = null;
        try {
            JsonObject request = HttpRequestUtils.parseRequestToJsonObject(req);
            response = this.executorManagerAdapter.skipFailedJobs(exFlow, user.getUserId(), request.toString());
            if(response == null){
                ret.put("error", "Request Failed");
            }
            JsonObject result = new JsonParser().parse(response).getAsJsonObject();
            if (result.has("error")) {
                ret.put("error", result.get("error").getAsString());
            }
        } catch (final Exception e) {
            ret.put("error", e.getMessage());
        }
    }

    private Map<String, Object> getExecutableFlowUpdateInfo(final ExecutableNode node,
        final long lastUpdateTime) {
        final HashMap<String, Object> nodeObj = new HashMap<>();
        if (node instanceof ExecutableFlowBase) {
            final ExecutableFlowBase base = (ExecutableFlowBase) node;
            final ArrayList<Map<String, Object>> nodeList = new ArrayList<>();

            for (final ExecutableNode subNode : base.getExecutableNodes()) {
                final Map<String, Object> subNodeObj = getExecutableFlowUpdateInfo(subNode, lastUpdateTime);
                if (!subNodeObj.isEmpty()) {
                    nodeList.add(subNodeObj);
                }
            }

            if (!nodeList.isEmpty()) {
                nodeObj.put("flow", base.getFlowId());
                nodeObj.put("nodes", nodeList);
            }
        }

        if (node.getUpdateTime() > lastUpdateTime || !nodeObj.isEmpty()) {
            nodeObj.put("id", node.getId());
            nodeObj.put("status", node.getStatus());
            nodeObj.put("startTime", node.getStartTime());
            nodeObj.put("endTime", node.getEndTime());
            nodeObj.put("updateTime", node.getUpdateTime());

            nodeObj.put("attempt", node.getAttempt());
            if (node.getAttempt() > 0) {
                nodeObj.put("pastAttempts", node.getAttemptObjects());
            }
        }

        return nodeObj;
    }

    private Map<String, Object> getExecutableNodeInfo(final ExecutableNode node, int executionId) {
        final HashMap<String, Object> nodeObj = new HashMap<>();
        nodeObj.put("id", node.getId());
        nodeObj.put("status", node.getStatus());
        nodeObj.put("startTime", node.getStartTime());
        nodeObj.put("endTime", node.getEndTime());
        nodeObj.put("updateTime", node.getUpdateTime());
        nodeObj.put("type", node.getType());
        nodeObj.put("outer", node.getOuter());
        if (node.getCondition() != null) {
            nodeObj.put("condition", node.getCondition());
        }
        nodeObj.put("nestedId", node.getNestedId());

        nodeObj.put("attempt", node.getAttempt());
        if (node.getAttempt() > 0) {
            nodeObj.put("pastAttempts", node.getAttemptObjects());
        }

        if (node.getInNodes() != null && !node.getInNodes().isEmpty()) {
            nodeObj.put("in", node.getInNodes());
        }

        if (node instanceof ExecutableFlowBase) {
            final ExecutableFlowBase base = (ExecutableFlowBase) node;
            final ArrayList<Map<String, Object>> nodeList = new ArrayList<>();

            for (final ExecutableNode subNode : base.getExecutableNodes()) {
                final Map<String, Object> subNodeObj = getExecutableNodeInfo(subNode, executionId);
                if (!subNodeObj.isEmpty()) {
                    nodeList.add(subNodeObj);
                }
            }
            nodeObj.put("execid", executionId);
            nodeObj.put("flow", base.getFlowId());
            nodeObj.put("nodes", nodeList);
            nodeObj.put("flowId", base.getFlowId());
        }

        return nodeObj;
    }

    private void ajaxFetchExecutableFlowUpdate(final HttpServletRequest req, final HttpServletResponse resp,
        final HashMap<String, Object> ret, final User user,
        final ExecutableFlow exFlow) throws ServletException {

        final Long lastUpdateTime = Long.parseLong(getParam(req, "lastUpdateTime"));
        logger.info("Fetching " + exFlow.getExecutionId());

        final Project project = getProjectAjaxByPermission(ret, exFlow.getProjectId(), user, Type.READ);
        if (project == null) {
            return;
        }

        final Map<String, Object> map = getExecutableFlowUpdateInfo(exFlow, lastUpdateTime);
        map.put("status", exFlow.getStatus());
        map.put("startTime", exFlow.getStartTime());
        map.put("endTime", exFlow.getEndTime());
        map.put("updateTime", exFlow.getUpdateTime());
        ret.putAll(map);
    }

    //获取执行Flow列表
    private void ajaxFetchExecutableFlow(final HttpServletRequest req, final HttpServletResponse resp,
        final HashMap<String, Object> ret, final User user,
        final ExecutableFlow exFlow) throws ServletException {

        logger.info("Fetching " + exFlow.getExecutionId());

        final Project project = getProjectAjaxByPermission(ret, exFlow.getProjectId(), user, Type.READ);
        if (project == null) {
            return;
        }

        ret.put("submitTime", exFlow.getSubmitTime());
        ret.put("submitUser", exFlow.getSubmitUser());
        ret.put("execid", exFlow.getExecutionId());
        ret.put("projectId", exFlow.getProjectId());
        ret.put("project", project.getName());
        //执行策略
        ret.put("executionStrategy", exFlow.getExecutionOptions().getFailureAction().toString());

        Long runDate = 0L;

        Map<String, String> repeatMap = exFlow.getRepeatOption();
        if(exFlow.getRunDate() != null){
            logger.info("run_date: {}", exFlow.getRunDate());
            DateTimeFormatter dateTimeFormatter = DateTimeFormat.forPattern("yyyyMMdd");
            LocalDate localDate = LocalDate.parse(exFlow.getRunDate(), dateTimeFormatter);
            runDate = localDate.toDate().getTime();
        } else if (!repeatMap.isEmpty()) {

            Long recoverRunDate = Long.valueOf(String.valueOf(repeatMap.get("startTimeLong")));

            org.joda.time.LocalDateTime localDateTime = new org.joda.time.LocalDateTime(new Date(recoverRunDate)).minusDays(1);

            Date date = localDateTime.toDate();

            runDate = date.getTime();

        } else {
            Long startTime = exFlow.getStartTime();
            if (-1 != startTime) {
                org.joda.time.LocalDateTime localDateTime = new org.joda.time.LocalDateTime(new Date(startTime)).minusDays(1);

                Date date = localDateTime.toDate();

                runDate = date.getTime();
            } else {
                runDate = startTime;
            }
        }
        //如果当前查看的是子工作流节点,则只查询整个执行工作流的子工作流
        final String nodeNestedId = getParam(req, "nodeNestedId", "");
        if (org.apache.commons.lang.StringUtils.isNotBlank(nodeNestedId)) {
            ExecutableNode node = exFlow.getExecutableNodePath(nodeNestedId);
            if (node == null) {
                return;
            }
            final Map<String, Object> flowObj = getExecutableNodeInfo(node, runDate, exFlow.getExecutionId());
            ret.putAll(flowObj);
            return;
        }
        //查看整个执行工作流节点
        final Map<String, Object> flowObj = getExecutableNodeInfo(exFlow, runDate, exFlow.getExecutionId());
        ret.putAll(flowObj);
    }

    private void setRunDate(Map<String, Object> ret, ExecutableFlow exflow){
        Map<String, String> repeatMap = exflow.getRepeatOption();
        long recoverRunDate = Long.valueOf(repeatMap.get("startTimeLong"));
        org.joda.time.LocalDateTime localDateTime = new org.joda.time.LocalDateTime(new Date(recoverRunDate)).minusDays(1);
        ((Map<String, String>)ret.get("flowParam")).put("run_date", localDateTime.toString("yyyyMMdd"));
    }

    // 提交工作流前的参数检测
    private void ajaxAttemptExecuteFlow(final HttpServletRequest req, final HttpServletResponse resp,
        final HashMap<String, Object> ret, final User user) throws ServletException {
        final String projectName = getParam(req, "project");
        final String flowId = getParam(req, "flow");

        Map<String, String> dataMap = loadExecutorServletI18nData();
        // 检查项目是否存在，工作流基于project这一层级
        final Project project = getProjectAjaxByPermission(ret, projectName, user, Type.EXECUTE);
        if (project == null) {
            ret.put("error", dataMap.get("program") + projectName + dataMap.get("notExist"));
            return;
        }
        // 检查工作流是否存在
        ret.put("flow", flowId);
        final Flow flow = project.getFlow(flowId);
        if (flow == null) {
            ret.put("error", dataMap.get("flow") + flowId + dataMap.get("notExist") + project);
            return;
        }

        ajaxExecuteFlow(req, resp, ret, user);
    }

    /*  //立即执行 Flow 的方法
      private void ajaxExecuteFlow(final HttpServletRequest req,
          final HttpServletResponse resp, final HashMap<String, Object> ret, final User user)
          throws ServletException {
        final String projectName = getParam(req, "project");
        final String flowId = getParam(req, "flow");
        // 检测project是否存在
        final Project project =
            getProjectAjaxByPermission(ret, projectName, user, Type.EXECUTE);
        if (project == null) {
          ret.put("error", "项目 '" + projectName + "' 不存在.");
          return;
        }
        // 检查工作流是否存在
        ret.put("flow", flowId);
        final Flow flow = project.getFlow(flowId);
        if (flow == null) {
          ret.put("error", "工作流 '" + flowId + "' 不存在. "
              + project);
          return;
        }

        final ExecutableFlow exflow = FlowUtils.createExecutableFlow(project, flow);
        exflow.setSubmitUser(user.getUserId());
        //设置代理用户
        exflow.addAllProxyUsers(project.getProxyUsers());
        // 设置执行参数，比如执行成功邮件通知人，执行失败邮件通知人等
        final ExecutionOptions options = HttpRequestUtils.parseFlowOptions(req);
        exflow.setExecutionOptions(options);
        if (!options.isFailureEmailsOverridden()) {
          options.setFailureEmails(flow.getFailureEmails());
        }
        if (!options.isSuccessEmailsOverridden()) {
          options.setSuccessEmails(flow.getSuccessEmails());
        }
        options.setMailCreator(flow.getMailCreator());

        try {
          HttpRequestUtils.filterAdminOnlyFlowParams(this.userManager, options, user);
          final String message =
              this.executorManager.submitExecutableFlow(exflow, user.getUserId());
          ret.put("message", message);
        } catch (final Exception e) {
          e.printStackTrace();
          ret.put("error",
              "工作流 " + exflow.getFlowId() + " 提交失败. " + e.getMessage());
        }

        ret.put("execid", exflow.getExecutionId());
      }
    */
    private void executeAllFlow(final HttpServletRequest req, final HttpServletResponse resp,
        final HashMap<String, Object> ret, final User user) throws ServletException {
        JsonObject request = HttpRequestUtils.parseRequestToJsonObject(req);
        String projectName = request.get("project").getAsString();
        Project project = getProjectAjaxByPermission(ret, projectName, user, Type.EXECUTE);
        Map<String, String> dataMap = loadExecutorServletI18nData();

        if (project == null) {
            ret.put("error", "Project '" + projectName + "' doesn't exist.");
            logger.error("Project '" + projectName + "' doesn't exist.");
            return;
        }
        if (project.getFlows() == null || project.getFlows().size() == 0) {
            ret.put("error", projectName + dataMap.get("haveNoFlows"));
            logger.error(projectName + ", 没有作业流。");
            return;
        }
        List<Flow> rootFlows = project.getAllRootFlows();
        StringBuilder sb = new StringBuilder();
        for(Flow flow: rootFlows){
            try {
                execFlow(project, flow, ret, request, user, sb);
            }catch (ServletException se){
                logger.warn("submit " + flow.getId() + " error." + se);
            }
        }
        ret.put("message", sb.toString());
        ret.put("code", "200");
    }

    private boolean execFlow(Project project, Flow flow, Map<String, Object> ret,
                             JsonObject request, User user, StringBuilder msg) throws ServletException{

        ret.put("flow", flow.getId());

        Map<String, String> dataMap = loadExecutorServletI18nData();

        ExecutableFlow exflow = FlowUtils.createExecutableFlow(project, flow);
        exflow.setSubmitUser(user.getUserId());
        //获取项目默认代理用户
        Set<String> proxyUserSet = project.getProxyUsers();
        //设置用户代理用户
        proxyUserSet.add(user.getUserId());
        proxyUserSet.addAll(user.getProxyUsers());
        //设置代理用户
        exflow.addAllProxyUsers(proxyUserSet);

        final ExecutionOptions options = HttpRequestUtils.parseFlowOptions(request);
        exflow.setExecutionOptions(options);
        if (!options.isFailureEmailsOverridden()) {
            options.setFailureEmails(flow.getFailureEmails());
        }
        if (!options.isSuccessEmailsOverridden()) {
            options.setSuccessEmails(flow.getSuccessEmails());
        }
        options.setMailCreator(flow.getMailCreator());

        //设置其他参数配置
        Map<String, Object> otherOptions = new HashMap<>();

        //设置失败重跑配置
        List<Map<String, String>> jobRetryList = new ArrayList<>();
        otherOptions.put("jobFailedRetryOptions", jobRetryList);
        exflow.setOtherOption(otherOptions);

        //设置失败跳过配置
        List<String> jobSkipList = new ArrayList<>();
        otherOptions.put("jobSkipFailedOptions", jobSkipList);
        exflow.setOtherOption(otherOptions);

        //设置通用告警级别
        if (request.has("failureAlertLevel")) {
            otherOptions.put("failureAlertLevel", request.get("failureAlertLevel").getAsString());
        }
        if (request.has("successAlertLevel")) {
            otherOptions.put("successAlertLevel", request.get("successAlertLevel").getAsString());
        }

        //---超时告警设置---
        boolean useTimeoutSetting = false;
        if(request.has("useTimeoutSetting")) {
            useTimeoutSetting = request.get("useTimeoutSetting").getAsBoolean();
        }
        final List<SlaOption> slaOptions = new ArrayList<>();
        if (useTimeoutSetting) {
            String emailStr = "";
            if(request.has("slaEmails")){
                emailStr = request.get("slaEmails").getAsString();
            }
            final String[] emailSplit = emailStr.split("\\s*,\\s*|\\s*;\\s*|\\s+");
            final List<String> slaEmails = Arrays.asList(emailSplit);
            Map<String, String> settings = new HashMap<>();
            try {
                if(request.has( "settings")){
                    settings = GsonUtils.jsonToJavaObject(request.getAsJsonObject("settings"), new TypeToken<Map<String, String>>() {}.getType());
                }
            } catch (Exception e){
                logger.error("没有找到超时告警信息");
            }
            //设置SLA 超时告警配置项
            for (final String set : settings.keySet()) {
                final SlaOption sla;
                try {
                    sla = AlertUtil.parseSlaSetting(settings.get(set), flow, project);
                } catch (final Exception e) {
                    logger.error("parse sla setting failed.");
                    msg.append(String.format("Error, flow:%s, msg:%s", flow.getId(), dataMap.get("resolveSlaFailed")+ "<br/>"));
                    throw new ServletException(e);
                }
                if (sla != null) {
                    sla.getInfo().put(SlaOption.INFO_FLOW_NAME, flow.getId());
                    sla.getInfo().put(SlaOption.INFO_EMAIL_LIST, slaEmails);
                    sla.getInfo().put(SlaOption.INFO_TIME_SET, sla.getTimeSet());
                    sla.getInfo().put(SlaOption.INFO_EMAIL_ACTION_SET, sla.getEmailAction());
                    sla.getInfo().put(SlaOption.INFO_KILL_FLOW_ACTION_SET, sla.getKillAction());
                    slaOptions.add(sla);
                }
            }
        }
        exflow.setSlaOptions(slaOptions);

        //设置flowType
        //对于单次执行，假如提交的json中含有cycleErrorOption，表示是循环执行，需要设置flowType为4; 设置cycleOption
        if (request.has("cycleErrorOption")) {
            exflow.setFlowType(4);
            HashMap<String, String> cycleOption = new HashMap<>();
            cycleOption.put("cycleErrorOption", request.get("cycleErrorOption").getAsString());
            exflow.setCycleOption(cycleOption);
        }


        try {
            //设置告警用户部门信息
            String userDep = transitionService.getUserDepartmentByUsername(user.getUserId());
            otherOptions.put("alertUserDeparment", userDep);
            HttpRequestUtils.filterAdminOnlyFlowParams(options, user);
            final String message = this.executorManagerAdapter.submitExecutableFlow(exflow, user.getUserId());
            msg.append(String.format("Success, flow:%s, execId:%s .<br/>", flow.getId(), exflow.getExecutionId()));
        } catch (final Exception e) {
            logger.error("submit executableFlow failed, ", e);
            msg.append(String.format("Error, flow:%s, msg:%s .<br/>", flow.getId(), e.getMessage()));
            return false;
        }
        ret.put(flow.getId(), exflow.getExecutionId());
        return true;
    }

    private void ajaxExecuteFlow(final HttpServletRequest req, final HttpServletResponse resp,
        final HashMap<String, Object> ret, final User user) throws ServletException {
        final String projectName = getParam(req, "project");
        final String flowId = getParam(req, "flow");

        final Project project =
            getProjectAjaxByPermission(ret, projectName, user, Type.EXECUTE);
        if (project == null) {
            ret.put("error", "Project '" + projectName + "' doesn't exist.");
            return;
        }

        ret.put("flow", flowId);
        final Flow flow = project.getFlow(flowId);
        if (flow == null) {
            ret.put("error", "Flow '" + flowId + "' cannot be found in project "
                + project);
            return;
        }

        final ExecutableFlow exflow = FlowUtils.createExecutableFlow(project, flow);
        exflow.setSubmitUser(user.getUserId());

        //获取项目默认代理用户
        Set<String> proxyUserSet = project.getProxyUsers();
        //设置用户代理用户
        proxyUserSet.add(user.getUserId());
        //设置提交用户的proxyUser
        WtssUser wtssUser = null;
        try {
            wtssUser = transitionService.getSystemUserByUserName(user.getUserId());
        } catch (SystemUserManagerException e){
            logger.error("get wtssUser failed, caused by: ", e);
        }
        if(wtssUser != null && wtssUser.getProxyUsers() != null) {
            String[] proxySplit = wtssUser.getProxyUsers().split("\\s*,\\s*");
            logger.info("add proxyUsers," + ArrayUtils.toString(proxySplit));
            exflow.addAllProxyUsers(Arrays.asList(proxySplit));
        }

        final ExecutionOptions options = HttpRequestUtils.parseFlowOptions(req);


        if (hasParam(req, "disableOutterFlag")) {
            final String disableOutterFlag = getParam(req, "disableOutterFlag");
            logger.info("current request param disableOutterFlag=" + disableOutterFlag);
            if ("true".equals(disableOutterFlag)) {
                List<Flow> flows = project.getFlows();
                List<ExecutableNode> executableNodes = exflow.getExecutableNodes();
                Map<String, ExecutableNode> executableNodeMap = new HashMap<>();
                // 递归存放subflow中的节点
                recursionFindNode(executableNodes, executableNodeMap);
                logger.info("current executableNodeMap size is:" + executableNodeMap.size());
                for (Flow tempFlow : flows) {
                    for (Node node : tempFlow.getNodes()) {
                        if ("true".equals(node.getOuter())) {
                            if (executableNodeMap.containsKey(node.getId())) {
                                executableNodeMap.get(node.getId()).setStatus(Status.DISABLED);
                            }
                        }
                    }
                }
            }
        }

        exflow.setExecutionOptions(options);
        if (!options.isFailureEmailsOverridden()) {
            options.setFailureEmails(flow.getFailureEmails());
        }
        if (!options.isSuccessEmailsOverridden()) {
            options.setSuccessEmails(flow.getSuccessEmails());
        }
        options.setMailCreator(flow.getMailCreator());

        //设置其他参数配置
        Map<String, Object> otherOptions = new HashMap<>();

        //设置失败重跑配置
        Map<String, String> jobFailedRetrySettings = getParamGroup(req, "jobFailedRetryOptions");
        final List<Map<String, String>> jobRetryList = new ArrayList<>();
        for (final String set : jobFailedRetrySettings.keySet()) {
            String[] setOption = jobFailedRetrySettings.get(set).split(",");
            Map<String, String> jobOption = new HashMap<>();
            String jobName = setOption[0].trim();
            String interval = setOption[1].trim();
            String count = setOption[2].trim();
            if (jobName.split(" ")[0].equals("all_jobs")) {
                Map<String, String> flowFailedRetryOption = new HashMap<>();
                flowFailedRetryOption.put("job.failed.retry.interval", interval);
                flowFailedRetryOption.put("job.failed.retry.count", count);
                exflow.setFlowFailedRetry(flowFailedRetryOption);
            }
            jobOption.put("jobName", jobName);
            jobOption.put("interval", interval);
            jobOption.put("count", count);
            jobRetryList.add(jobOption);


        }

        otherOptions.put("jobFailedRetryOptions", jobRetryList);
        exflow.setOtherOption(otherOptions);

        //设置失败跳过配置
        Map<String, String> jobSkipFailedSettings = getParamGroup(req, "jobSkipFailedOptions");
        final List<String> jobSkipList = new ArrayList<>();
        for (final String set : jobSkipFailedSettings.keySet()) {
            String jobName = jobSkipFailedSettings.get(set).trim();
            if(jobName.split(" ")[0].equals("all_jobs")){
                exflow.setFailedSkipedAllJobs(true);
            }
            jobSkipList.add(jobName);
        }

        otherOptions.put("jobSkipFailedOptions", jobSkipList);
        exflow.setOtherOption(otherOptions);

        //设置通用告警级别
        if (hasParam(req, "failureAlertLevel")) {
            otherOptions.put("failureAlertLevel", getParam(req, "failureAlertLevel"));
        }
        if (hasParam(req, "successAlertLevel")) {
            otherOptions.put("successAlertLevel", getParam(req, "successAlertLevel"));
        }

        //---超时告警设置---
        boolean useTimeoutSetting = false;
        if(hasParam(req, "useTimeoutSetting")) {
            useTimeoutSetting = Boolean.valueOf(getParam(req, "useTimeoutSetting"));
        }
        final List<SlaOption> slaOptions = new ArrayList<>();
        if (useTimeoutSetting) {
            String emailStr = "";
            if(hasParam(req, "slaEmails")){
                emailStr = getParam(req, "slaEmails");
            }
            final String[] emailSplit = emailStr.split("\\s*,\\s*|\\s*;\\s*|\\s+");
            final List<String> slaEmails = Arrays.asList(emailSplit);
            Map<String, String> settings = getParamGroup(req, "settings");
            //设置SLA 超时告警配置项
            for (final String set : settings.keySet()) {
                final SlaOption sla;
                try {
                    sla = AlertUtil.parseSlaSetting(settings.get(set), flow, project);
                } catch (final Exception e) {
                    logger.error("parse sla setting failed.");
                    throw new ServletException(e);
                }
                if (sla != null) {
                    sla.getInfo().put(SlaOption.INFO_FLOW_NAME, flowId);
                    sla.getInfo().put(SlaOption.INFO_EMAIL_LIST, slaEmails);
                    sla.getInfo().put(SlaOption.INFO_TIME_SET, sla.getTimeSet());
                    sla.getInfo().put(SlaOption.INFO_EMAIL_ACTION_SET, sla.getEmailAction());
                    sla.getInfo().put(SlaOption.INFO_KILL_FLOW_ACTION_SET, sla.getKillAction());
                    slaOptions.add(sla);
                }
            }
        }
        exflow.setSlaOptions(slaOptions);
        //---超时告警设置---

        //设置flowType
        //对于单次执行，假如提交的json中含有cycleErrorOption，表示是循环执行，需要设置flowType为4; 设置cycleOption
        if (hasParam(req,"cycleErrorOption")) {
            exflow.setFlowType(4);
            HashMap<String, String> cycleOption = new HashMap<>();
            cycleOption.put("cycleErrorOption", getParam(req,"cycleErrorOption"));
            cycleOption.put("cycleFlowInterruptAlertLevel", getParam(req,"cycleFlowInterruptAlertLevel"));
            cycleOption.put("cycleFlowInterruptEmails", getParam(req,"cycleFlowInterruptEmails"));
            exflow.setCycleOption(cycleOption);
        }

        // lastexecid
        if(hasParam(req,"lastNsWtss")){
            boolean lastNsWtss = Boolean.valueOf(getParam(req, "lastNsWtss"));
            exflow.setLastNsWtss(lastNsWtss);
            if(lastNsWtss){
                exflow.setJobOutputGlobalParam(new ConcurrentHashMap<>(getParamGroup(req, "jobOutputParam")));
            } else {
                int lastExecId = getIntParam(req, "lastExecId");
                otherOptions.put("lastExecId", lastExecId);
            }
        }

        try {
            //设置告警用户部门信息
            String userDep = transitionService.getUserDepartmentByUsername(user.getUserId());
            otherOptions.put("alertUserDeparment", userDep);

            HttpRequestUtils.filterAdminOnlyFlowParams(options, user);
            final String message =
                this.executorManagerAdapter.submitExecutableFlow(exflow, user.getUserId());
            ret.put("message", message);
            String ajaxName = getParam(req, "ajax");
        } catch (final Exception e) {
            logger.error(e.toString());
            ret.put("code", "10006");
            ret.put("error", "Error submitting flow " + exflow.getFlowId() + ". " + e.getMessage());
            ret.put("message", "Execute Flow[" + exflow.getFlowId() + "Failed." + e.getMessage());
            return;
        }
        ret.put("code", "200");
        ret.put("message", "success");
        ret.put("execid", exflow.getExecutionId());
    }


    /**
     * 递归存放subflow中的节点
     * @param executableNodes
     * @param resultMap
     */
    private void recursionFindNode(List<ExecutableNode> executableNodes, Map<String, ExecutableNode> resultMap) {
        for (ExecutableNode executableNode : executableNodes) {
            if (executableNode instanceof ExecutableFlowBase) {
                ExecutableFlowBase subFlow = (ExecutableFlowBase) executableNode;
                List<ExecutableNode> subFlowExecutableNodes = subFlow.getExecutableNodes();
                recursionFindNode(subFlowExecutableNodes, resultMap);
            } else {
                resultMap.put(executableNode.getId(), executableNode);
            }
        }
    }

    private void ajaxExecuteAllHistoryRecoverFlow(final HttpServletRequest req, final HttpServletResponse resp,
        final HashMap<String, Object> ret, final User user) throws ServletException {
        JsonObject request = HttpRequestUtils.parseRequestToJsonObject(req);
        String projectName = request.get("project").getAsString();
        Project project = getProjectAjaxByPermission(ret, projectName, user, Type.EXECUTE);
        if (project == null) {
            ret.put("error", "Project '" + projectName + "' doesn't exist.");
            logger.error("Project '" + projectName + "' doesn't exist.");
            return;
        }
        Map<String, String> dataMap = loadExecutorServletI18nData();

        if (project.getFlows() == null || project.getFlows().size() == 0) {
            ret.put("error", projectName + dataMap.get("haveNoFlows"));
            logger.error(projectName + ", 没有作业流。");
            return;
        }
        List<Flow> rootFlows = project.getAllRootFlows();
        StringBuilder sb = new StringBuilder();
        for(Flow flow: rootFlows){
            try {
                executeHistoryRecoverFlow(project, flow, ret, request, user, sb);
            }catch (ServletException se){
                logger.warn("submit HistoryRecoverFlow failed, " + se);
            }
        }
        ret.put("code", "200");
        ret.put("message", sb.toString());
    }

    private boolean executeHistoryRecoverFlow(Project project, Flow flow, Map<String, Object> ret, JsonObject json, User user, StringBuilder msg) throws ServletException {
        String projectName = project.getName();
        String flowId = flow.getId();
        ret.put("flow", flowId);
        ExecutionRecover nowRuningRecover = null;

        Map<String, String> dataMap = loadExecutorServletI18nData();
        //查询这个Flow的补采记录
        try {
            nowRuningRecover =
                this.executorManagerAdapter.getHistoryRecoverFlowByPidAndFid(String.valueOf(project.getId()), flow.getId());
        } catch (ExecutorManagerException e) {
            logger.error(e.getMessage());
        }
        //校验这个Flow是否已经开始补采
        if (null != nowRuningRecover && (Status.RUNNING.equals(nowRuningRecover.getRecoverStatus())
            || Status.PREPARING.equals(nowRuningRecover.getRecoverStatus()))) {
            logger.error("This flow '" + flow.getId() + "' has running history recover.");
            msg.append(String.format("Error, flow:%s, msg: " + dataMap.get("haveHisReRun")+"<br/>", flow.getId()));
            return false;
        }
        final ExecutionOptions options = HttpRequestUtils.parseFlowOptions(json);
        if (!options.isFailureEmailsOverridden()) {
            options.setFailureEmails(flow.getFailureEmails());
        }
        if (!options.isSuccessEmailsOverridden()) {
            options.setSuccessEmails(flow.getSuccessEmails());
        }
        options.setMailCreator(flow.getMailCreator());

        Map<String, String> reqMap = new HashMap<>();
        reqMap.put("projectName", projectName);
        reqMap.put("projectId", project.getId() + "");
        reqMap.put("flowId", flow.getId());
        //获取前端的历史重跑错误设置
        String recoverErrorOption = json.get("recoverErrorOption").getAsString();
        reqMap.put("recoverErrorOption", recoverErrorOption);
        final Map<String, Object> repeatOptionMap = new HashMap<>();
        try {
            //解析前端的参数 获取数据补采参数集合
            repeatOptionMap.putAll(repeatDateCompute(json));
        } catch (Exception e) {
            logger.error("数据补采输入参数处理异常!", e);
            msg.append(String.format("Error, flow:%s, msg: %s <br/>", flow.getId(), dataMap.get("dataCompensateinputParamError")));
            return false;
        }

        ExecutionRecover executionRecover = new ExecutionRecover();
        //组装历史补采数据
        executionRecover.setSubmitUser(user.getUserId());
        executionRecover.setRecoverStatus(Status.PREPARING);
        executionRecover.setRecoverStartTime(Long.valueOf(String.valueOf(repeatOptionMap.get("recoverStartTime"))));
        executionRecover.setRecoverEndTime(Long.valueOf(String.valueOf(repeatOptionMap.get("recoverEndTime"))));
        executionRecover.setExInterval(String.valueOf(repeatOptionMap.get("exInterval")));

        executionRecover.setProjectId(project.getId());
        executionRecover.setFlowId(flowId);
        executionRecover.setExecutionOptions(options);

        //repeatOptionMap.put("repeatOptionList", repeatOptionMap);
        executionRecover.setRepeatOption(repeatOptionMap);
        //放入用户代理
        executionRecover.setProxyUsers(ArrayUtils.toString(user.getProxyUsers()));
        executionRecover.setRecoverErrorOption(recoverErrorOption);
        int taskSize = 1;
        if(json.has("taskSize")){
            taskSize = json.get("taskSize").getAsInt();
        }
        executionRecover.setTaskSize(taskSize);

        Map<String, Object> otherOptions = new HashMap<>();
        //设置通用告警级别failureAlertLevel
        if (json.has("failureAlertLevel")) {
            otherOptions.put("failureAlertLevel", json.get("failureAlertLevel").getAsString());
        }
        if (json.has("successAlertLevel"))
        {
            otherOptions.put("successAlertLevel", json.get("successAlertLevel").getAsString());
        }
        //设置失败重跑配置
        List<Map<String, String>> jobRetryList = new ArrayList<>();
        otherOptions.put("jobFailedRetryOptions", jobRetryList);

        //设置失败跳过配置
        List<String> jobSkipList = new ArrayList<>();
        otherOptions.put("jobSkipFailedOptions", jobSkipList);

        executionRecover.setOtherOption(otherOptions);
        ret.put("repeatOptionMap", repeatOptionMap);

        //---超时告警设置---
        boolean useTimeoutSetting = false;
        if(json.get("useTimeoutSetting").getAsBoolean()) {
            useTimeoutSetting = json.get("useTimeoutSetting").getAsBoolean();
        }
        final List<SlaOption> slaOptions = new ArrayList<>();
        if (useTimeoutSetting) {
            final String emailStr = json.get("slaEmails").getAsString();
            final String[] emailSplit = emailStr.split("\\s*,\\s*|\\s*;\\s*|\\s+");
            final List<String> slaEmails = Arrays.asList(emailSplit);
            final Map<String, String> settings = GsonUtils.jsonToJavaObject(json.getAsJsonObject("settings"), new TypeToken<Map<String, String>>() {}.getType());;
            //设置SLA 超时告警配置项
            for (final String set : settings.keySet()) {
                SlaOption sla = null;
                try {
                    sla = AlertUtil.parseSlaSetting(settings.get(set), flow, project);
                } catch (final Exception e) {
                    logger.warn("parse sla setting failed." + e);
                }
                if (sla != null) {
                    sla.getInfo().put(SlaOption.INFO_FLOW_NAME, flowId);
                    sla.getInfo().put(SlaOption.INFO_EMAIL_LIST, slaEmails);
                    sla.getInfo().put(SlaOption.INFO_TIME_SET, sla.getTimeSet());
                    sla.getInfo().put(SlaOption.INFO_EMAIL_ACTION_SET, sla.getEmailAction());
                    sla.getInfo().put(SlaOption.INFO_KILL_FLOW_ACTION_SET, sla.getKillAction());
                    slaOptions.add(sla);
                }
            }
        }
        executionRecover.setSlaOptions(slaOptions);
        //---超时告警设置---

        //新增历史补采数据记录
        try {
            this.executorManagerAdapter.saveHistoryRecoverFlow(executionRecover);
            logger.info("提交历史重跑成功" + project.getId() + " - " + flowId);
            msg.append(String.format("Success, flow:%s, msg:"+dataMap.get("submitHisReRunSuccess")+"<br/>", flow.getId()));
        } catch (Exception e) {
            logger.error("新增历史重跑任务失败 ", e);
            msg.append(String.format("Error, flow:%s, msg:"+dataMap.get("submitHisReRunFail")+"。<br/>", flow.getId()));
            return false;
        }
        return true;
    }


    /**
     * 数据补采 前端入口方法
     *
     * @param req
     * @param resp
     * @param ret
     * @param user
     * @throws ServletException
     */
    private void ajaxAttRepeatExecuteFlow(final HttpServletRequest req, final HttpServletResponse resp,
        final HashMap<String, Object> ret, final User user) throws ServletException {
        JsonObject json = HttpRequestUtils.parseRequestToJsonObject(req);
        if (json == null) {
            logger.error("解析请求参数异常.");
            return;
        }
        final String projectName = json.get("project").getAsString();
        final String flowId = json.get("flow").getAsString();

        final Project project = getProjectAjaxByPermission(ret, projectName, user, Type.EXECUTE);
        if (project == null) {
            logger.error("Project '" + projectName + "' doesn't exist.");
            return;
        }

        ret.put("flow", flowId);
        final Flow flow = project.getFlow(flowId);
        if (flow == null) {
            logger.error("Flow '" + flowId + "' cannot be found in project " + project);
            return;
        }

        ExecutionRecover nowRuningRecover = null;

        //查询这个Flow的补采记录
        try {
            nowRuningRecover =
                this.executorManagerAdapter.getHistoryRecoverFlowByPidAndFid(String.valueOf(project.getId()), flow.getId());
        } catch (ExecutorManagerException e) {
            logger.error("get flow history recover failed, caused by:", e);
        }
        //校验这个Flow是否已经开始补采
        if (null != nowRuningRecover && (Status.RUNNING.equals(nowRuningRecover.getRecoverStatus())
            || Status.PREPARING.equals(nowRuningRecover.getRecoverStatus()))) {
            logger.error("This flow '" + flow.getId() + "' has running history recover.");
            return;
        }

        final ExecutionOptions options = HttpRequestUtils.parseFlowOptions(json);
        if (!options.isFailureEmailsOverridden()) {
            options.setFailureEmails(flow.getFailureEmails());
        }
        if (!options.isSuccessEmailsOverridden()) {
            options.setSuccessEmails(flow.getSuccessEmails());
        }
        options.setMailCreator(flow.getMailCreator());
        options.setConcurrentOption(ExecutionOptions.CONCURRENT_OPTION_IGNORE);
        Map<String, String> reqMap = new HashMap<>();
        reqMap.put("projectName", projectName);
        reqMap.put("projectId", project.getId() + "");
        reqMap.put("flowId", flow.getId());
        //获取前端的历史重跑错误设置
        String recoverErrorOption = json.get("recoverErrorOption").getAsString();
        reqMap.put("recoverErrorOption", recoverErrorOption);


        final Map<String, Object> repeatOptionMap = new HashMap<>();
        try {
            //解析前端的参数 获取数据补采参数集合
            repeatOptionMap.putAll(repeatDateCompute(json));
        } catch (Exception e) {
            logger.error("数据补采输入参数处理异常!", e);
            e.printStackTrace();
        }

        //repeatOptionMap.put("flowOption", options);

        ExecutionRecover executionRecover = new ExecutionRecover();
        //组装历史补采数据
        executionRecover.setSubmitUser(user.getUserId());
        executionRecover.setRecoverStatus(Status.PREPARING);
        executionRecover.setRecoverStartTime(Long.valueOf(String.valueOf(repeatOptionMap.get("recoverStartTime"))));
        executionRecover.setRecoverEndTime(Long.valueOf(String.valueOf(repeatOptionMap.get("recoverEndTime"))));
        executionRecover.setExInterval(String.valueOf(repeatOptionMap.get("exInterval")));

        executionRecover.setProjectId(project.getId());
        executionRecover.setFlowId(flowId);
        executionRecover.setExecutionOptions(options);

        //repeatOptionMap.put("repeatOptionList", repeatOptionMap);
        executionRecover.setRepeatOption(repeatOptionMap);
        //放入用户代理
        executionRecover.setProxyUsers(ArrayUtils.toString(user.getProxyUsers()));
        executionRecover.setRecoverErrorOption(recoverErrorOption);
        int taskSize = 1;
        if(json.has("taskSize")){
            taskSize = json.get("taskSize").getAsInt();
        }
        executionRecover.setTaskSize(taskSize);
        boolean finishedAlert = true;
        if(json.has("finishedAlert")){
            finishedAlert = json.get("finishedAlert").getAsBoolean();
        }
        executionRecover.setFinishedAlert(finishedAlert);

        Map<String, Object> otherOptions = new HashMap<>();
        //设置通用告警级别failureAlertLevel
        if (json.has("failureAlertLevel")) {
            otherOptions.put("failureAlertLevel", json.get("failureAlertLevel").getAsString());
        }
        if (json.has("successAlertLevel")) {
            otherOptions.put("successAlertLevel", json.get("successAlertLevel").getAsString());
        }

        //设置失败重跑配置
        Map<String, String> jobFailedRetrySettings = new HashMap<>();
        if(json.has("jobFailedRetryOptions")){
            jobFailedRetrySettings = GsonUtils.jsonToJavaObject(json.get("jobFailedRetryOptions").getAsJsonObject(), new TypeToken<Map<String, String>>() {
            }.getType());
        }
        final List<Map<String, String>> jobRetryList = new ArrayList<>();
        for (final String set : jobFailedRetrySettings.keySet()) {
            String[] setOption = jobFailedRetrySettings.get(set).split(",");
            Map<String, String> jobOption = new HashMap<>();
            String jobName = setOption[0].trim();
            String interval = setOption[1].trim();
            String count = setOption[2].trim();
            if (jobName.split(" ")[0].equals("all_jobs")) {
                Map<String, String> flowFailedRetryOption = new HashMap<>();
                flowFailedRetryOption.put("job.failed.retry.interval", interval);
                flowFailedRetryOption.put("job.failed.retry.count", count);
                otherOptions.put("flowFailedRetryOption", flowFailedRetryOption);
            }
            jobOption.put("jobName", jobName);
            jobOption.put("interval", interval);
            jobOption.put("count", count);
            jobRetryList.add(jobOption);
        }
        otherOptions.put("jobFailedRetryOptions", jobRetryList);

        // 设置历史重跑告警
        otherOptions.put("historyRerunAlertLevel", json.get("historyRerunAlertLevel").getAsString());
        otherOptions.put("historyRerunAlertEmails", json.get("historyRerunAlertEmails").getAsString());


        //设置失败跳过配置
        Map<String, String> jobSkipFailedSettings = new HashMap<>();
        if(json.has("jobSkipFailedOptions")){
            jobSkipFailedSettings = GsonUtils.jsonToJavaObject(json.get("jobSkipFailedOptions").getAsJsonObject(), new TypeToken<Map<String, String>>() {
            }.getType());
        }
        final List<String> jobSkipList = new ArrayList<>();
        for (final String set : jobSkipFailedSettings.keySet()) {
            String jobName = jobSkipFailedSettings.get(set).trim();
            if(jobName.split(" ")[0].equals("all_jobs")){
                otherOptions.put("flowFailedSkiped", true);
            }
            jobSkipList.add(jobName);
        }

        otherOptions.put("jobSkipFailedOptions", jobSkipList);

        executionRecover.setOtherOption(otherOptions);

        ret.put("repeatOptionMap", repeatOptionMap);

        //---超时告警设置---
        boolean useTimeoutSetting = false;
        if(json.has("useTimeoutSetting")){
            useTimeoutSetting = json.get("useTimeoutSetting").getAsBoolean();
        }
        final List<SlaOption> slaOptions = new ArrayList<>();
        if (useTimeoutSetting) {
            final String emailStr = json.get("slaEmails").getAsString();
            final String[] emailSplit = emailStr.split("\\s*,\\s*|\\s*;\\s*|\\s+");
            final List<String> slaEmails = Arrays.asList(emailSplit);
            final Map<String, String> settings = GsonUtils.jsonToJavaObject(json.getAsJsonObject("settings"), new TypeToken<Map<String, String>>() {}.getType());;
            //设置SLA 超时告警配置项
            for (final String set : settings.keySet()) {
                final SlaOption sla;
                try {
                    sla = AlertUtil.parseSlaSetting(settings.get(set), flow, project);
                } catch (final Exception e) {
                    logger.error("parse sla setting failed.");
                    throw new ServletException(e);
                }
                if (sla != null) {
                    sla.getInfo().put(SlaOption.INFO_FLOW_NAME, flowId);
                    sla.getInfo().put(SlaOption.INFO_EMAIL_LIST, slaEmails);
                    sla.getInfo().put(SlaOption.INFO_TIME_SET, sla.getTimeSet());
                    sla.getInfo().put(SlaOption.INFO_EMAIL_ACTION_SET, sla.getEmailAction());
                    sla.getInfo().put(SlaOption.INFO_KILL_FLOW_ACTION_SET, sla.getKillAction());
                    slaOptions.add(sla);
                }
            }
        }
        executionRecover.setSlaOptions(slaOptions);
        //---超时告警设置---

        //新增历史补采数据记录
        try {
            Map<String, Object> recoverHandleMap = new HashMap<>();
            recoverHandleMap.putAll(repeatOptionMap);
            recoverHandleMap.put("project", project);
            recoverHandleMap.put("flow", flow);

            this.executorManagerAdapter.saveHistoryRecoverFlow(executionRecover);
            logger.info("提交历史重跑成功" + project.getId() + " - " + flowId);
        } catch (Exception e) {
            logger.info("历史补采线程终止： ", e);
        }

    }

    private void ajaxCycleParamVerify(HttpServletRequest req, HttpServletResponse resp, HashMap<String, Object> ret, User user)
        throws ServletException {
        JsonObject json = HttpRequestUtils.parseRequestToJsonObject(req);
        if (json == null) {
            json = new JsonObject();
            String projectName = getParam(req, "project");
            String flowId = getParam(req, "flow");
            json.addProperty("project", projectName);
            json.addProperty("flow", flowId);
        }
        Optional<Pair<Project, Flow>> validateCycleFlowRes = validateCycleFlowParams(json, user, ret);
        if (!validateCycleFlowRes.isPresent()) {
            return;
        }
        Project project = validateCycleFlowRes.get().getFirst();
        Flow flow =  validateCycleFlowRes.get().getSecond();
        String flowId = flow.getId();
        boolean isCycleFlowRunning = isCycleFlowRunning(project.getId(), flow.getId());
        if (isCycleFlowRunning) {
            ret.put("error", "This flow '" + flow.getId() + "' has running cycle");
            return;
        }
        //判断flow是否真正运行
        final String concurrentOption = getParam(req, "concurrentOption");
        if (concurrentOption.equals("skip") && executorManagerAdapter.getRunningFlows(project.getId(), flowId).size() != 0) {
            ret.put("error", "flow: " + flowId + "cycle flow is running, can not submit");
        }
    }

    private void ajaxStopCycleFlow(HttpServletRequest req, HttpServletResponse resp, Map<String, Object> ret, User user) throws ServletException {
        int id = getIntParam(req, "id");
        try {
            ExecutionCycle executionCycle = executorManagerAdapter.getExecutionCycleFlow(id);
            if (executionCycle != null) {
                ExecutableFlow exFlow = this.executorManagerAdapter.getExecutableFlow(executionCycle.getCurrentExecId());
                executionCycle.setStatus(Status.KILLED);
                executionCycle.setEndTime(System.currentTimeMillis());
                executionCycle.setExecutionOptions(exFlow.getExecutionOptions());
                executorManagerAdapter.updateExecutionFlow(executionCycle);
                executorManagerAdapter.cancelFlow(exFlow, user.getUserId());
                ExecutionControllerUtils.alertOnCycleFlowInterrupt(exFlow, executionCycle, alerterHolder);
            }
        } catch (ExecutorManagerException e) {
            ret.put("error", e.getMessage());
        }
    }

    private void ajaxSubmitCycleFlow(HttpServletRequest req, HttpServletResponse resp, Map<String, Object> ret, User user) throws ServletException {
        String projectName = getParam(req, "project");
        String flowId = getParam(req, "flow");
        final Project project = getProjectAjaxByPermission(ret, projectName, user, Type.EXECUTE);
        final Flow flow = project.getFlow(flowId);
        ExecutionCycle executionCycle = generateExecutionCycle(req, project, flow, user);
        try {
            executorManagerAdapter.saveExecutionCycleFlow(executionCycle);
            HashMap<String, Object> executeFlowRes = new HashMap<>();
            ajaxExecuteFlow(req, resp, executeFlowRes, user);
            boolean flag = "200".equals(executeFlowRes.get("code"));
            if (flag) {
                int currentExecId = (Integer) executeFlowRes.get("execid");
                executionCycle.setCurrentExecId(currentExecId);
                executionCycle.setStatus(Status.RUNNING);
            } else {
                executionCycle.setStatus(Status.FAILED);
            }
            executorManagerAdapter.updateExecutionFlow(executionCycle);
            logger.info("submit cycle flow success " + project.getId() + " - " + flow.getId());
        } catch (ExecutorManagerException e) {
            logger.info("submit cycle flow failed: " + e);
            ret.put("error", "submit cycle flow failed: " + e);
        }
    }

    private Optional<Pair<Project, Flow>> validateCycleFlowParams(JsonObject json, User user, Map<String, Object> ret) {
        final String projectName = json.get("project").getAsString();
        final String flowId = json.get("flow").getAsString();
        final Project project = getProjectAjaxByPermission(ret, projectName, user, Type.EXECUTE);
        if (project == null) {
            logger.error("Project '" + projectName + "' doesn't exist.");
            ret.put("error", "Project '" + projectName + "' doesn't exist.");
            return Optional.empty();
        }
        final Flow flow = project.getFlow(flowId);
        if (flow == null) {
            logger.error("Flow '" + flowId + "' cannot be found in project " + project);
            ret.put("error", "Flow '" + flowId + "' cannot be found in project " + project);
            return Optional.empty();
        }
        return Optional.of(new Pair<>(project, flow));
    }

    private boolean isCycleFlowRunning(int projectId, String flowId) {
        ExecutionCycle nowExecutionCycle;
        try {
            nowExecutionCycle = executorManagerAdapter.getExecutionCycleFlow(String.valueOf(projectId), flowId);
        } catch (ExecutorManagerException e) {
            nowExecutionCycle = null;
        }
        if (null != nowExecutionCycle && (Status.RUNNING.equals(nowExecutionCycle.getStatus())
            || Status.PREPARING.equals(nowExecutionCycle.getStatus()))) {
            logger.error("This flow '" + flowId + "' has running cycle");
            return true;
        }
        return false;
    }

    private ExecutionCycle generateExecutionCycle(HttpServletRequest req, Project project, Flow flow, User user) throws ServletException {
        ExecutionCycle executionCycle = new ExecutionCycle();
        //todo cycleOption 的处理
        Map<String, Object> cycleOption = new HashMap<>();
        //组装循环执行数据
        executionCycle.setSubmitUser(user.getUserId());
        executionCycle.setStatus(Status.PREPARING);
        executionCycle.setProjectId(project.getId());
        executionCycle.setFlowId(flow.getId());
        executionCycle.setStartTime(System.currentTimeMillis());
        ExecutionOptions executionOptions = generateExecutionOptions(req, flow);
        executionCycle.setExecutionOptions(executionOptions);
        executionCycle.setCycleOption(cycleOption);
        executionCycle.setProxyUsers(ArrayUtils.toString(user.getProxyUsers()));
        executionCycle.setCycleErrorOption(getParam(req, "cycleErrorOption"));
        Map<String, Object> otherOption = generateOtherOptions(req);
        executionCycle.setOtherOption(otherOption);
        List<SlaOption> slaOptions = generateSlaOptions(req, project, flow);
        executionCycle.setSlaOptions(slaOptions);
        return executionCycle;
    }

    private ExecutionOptions generateExecutionOptions(HttpServletRequest req, Flow flow) throws ServletException {
        final ExecutionOptions executionOptions = HttpRequestUtils.parseFlowOptions(req);
        if (!executionOptions.isFailureEmailsOverridden()) {
            executionOptions.setFailureEmails(flow.getFailureEmails());
        }
        if (!executionOptions.isSuccessEmailsOverridden()) {
            executionOptions.setSuccessEmails(flow.getSuccessEmails());
        }
        executionOptions.setMailCreator(flow.getMailCreator());
        return executionOptions;
    }

    private Map<String, Object> generateOtherOptions(HttpServletRequest req) throws ServletException {
        Map<String, Object> otherOptions = new HashMap<>();
        //通用告警设置
        if (hasParam(req, "failureAlertLevel")) {
            otherOptions.put("failureAlertLevel", getParam(req, "failureAlertLevel"));
        }
        if (hasParam(req, "successAlertLevel")) {
            otherOptions.put("successAlertLevel", getParam(req, "successAlertLevel"));
        }
        //设置失败重跑配置
        if (hasParam(req, "jobFailedRetryOptions")) {
            Map<String, String> jobFailedRetrySettings = getParamGroup(req, "jobFailedRetryOptions");
            final List<Map<String, String>> jobRetryList = new ArrayList<>();
            for (final String set : jobFailedRetrySettings.keySet()) {
                String[] setOption = jobFailedRetrySettings.get(set).split(",");
                Map<String, String> jobOption = new HashMap<>();
                String jobName = setOption[0].trim();
                String interval = setOption[1].trim();
                String count = setOption[2].trim();
                if (jobName.split(" ")[0].equals("all_jobs")) {
                    Map<String, String> flowFailedRetryOption = new HashMap<>();
                    flowFailedRetryOption.put("job.failed.retry.interval", interval);
                    flowFailedRetryOption.put("job.failed.retry.count", count);
                    otherOptions.put("flowFailedRetryOption", flowFailedRetryOption);
                }
                jobOption.put("jobName", jobName);
                jobOption.put("interval", interval);
                jobOption.put("count", count);
                jobRetryList.add(jobOption);


            }
            otherOptions.put("jobFailedRetryOptions", jobRetryList);
        }
        //设置失败跳过配置
        if (hasParam(req, "jobSkipFailedOptions")) {
            Map<String, String> jobSkipFailedSettings = getParamGroup(req, "jobSkipFailedOptions");
            final List<String> jobSkipList = new ArrayList<>();
            for (final String set : jobSkipFailedSettings.keySet()) {
                String jobName = jobSkipFailedSettings.get(set).trim();
                if(jobName.split(" ")[0].equals("all_jobs")){
                    otherOptions.put("flowFailedSkiped", true);
                }
                jobSkipList.add(jobName);
            }
            otherOptions.put("jobSkipFailedOptions", jobSkipList);
            return otherOptions;
        }
        return otherOptions;
    }

    private List<SlaOption> generateSlaOptions(HttpServletRequest req, Project project, Flow flow) throws ServletException {
        //---超时告警设置---
        final List<SlaOption> slaOptions = new ArrayList<>();
        if (hasParam(req, "useTimeoutSetting")) {
            final String emailStr = getParam(req, "slaEmails");
            final String[] emailSplit = emailStr.split("\\s*,\\s*|\\s*;\\s*|\\s+");
            final List<String> slaEmails = Arrays.asList(emailSplit);
            final Map<String, String> settings = getParamGroup(req,"settings");
            //设置SLA 超时告警配置项
            for (final String set : settings.keySet()) {
                final SlaOption sla;
                try {
                    sla = AlertUtil.parseSlaSetting(settings.get(set), flow, project);
                } catch (final Exception e) {
                    logger.error("parse sla setting failed.");
                    throw new ServletException(e);
                }
                if (sla != null) {
                    sla.getInfo().put(SlaOption.INFO_FLOW_NAME, flow.getId());
                    sla.getInfo().put(SlaOption.INFO_EMAIL_LIST, slaEmails);
                    sla.getInfo().put(SlaOption.INFO_TIME_SET, sla.getTimeSet());
                    sla.getInfo().put(SlaOption.INFO_EMAIL_ACTION_SET, sla.getEmailAction());
                    sla.getInfo().put(SlaOption.INFO_KILL_FLOW_ACTION_SET, sla.getKillAction());
                    slaOptions.add(sla);
                }
            }
        }
        return slaOptions;
    }

    /**
     * 执行每个数据补采的 Flow 方法
     *
     * @param reqMap
     * @param repeatMap
     * @param options
     * @param user
     * @return
     * @throws Exception
     */
    private ExecutableFlow ajaxRepeatExecuteFlow(final Map<String, String> reqMap, final Map<String, Object> repeatMap,
        final ExecutionOptions options, final User user, final String userId) throws Exception {

        final String flowId = reqMap.get("flowId");

        final Project project = (Project) repeatMap.get("project");

        final Flow flow = project.getFlow(flowId);

        final ExecutableFlow exflow = new ExecutableFlow(project, flow);
        exflow.setSubmitUser(user.getUserId());

        //获取项目默认代理用户
        Set<String> proxyUserSet = project.getProxyUsers();
        //设置用户代理用户
        if (null != user) {
            proxyUserSet.add(user.getUserId());
            proxyUserSet.addAll(user.getProxyUsers());
        }
        //设置当前登录的用户的代理用户
        exflow.addAllProxyUsers(proxyUserSet);
        exflow.setExecutionOptions(options);

        //设置数据补采参数
        exflow.setRepeatOption((Map<String, String>) repeatMap.get("flowRecoverOptionMap"));
        //设置Flow类型为数据补采
        exflow.setFlowType(2);
//    exflow.setRepeatId(repeatId);

//    HttpRequestUtils.filterAdminOnlyFlowParams(this.userManager, options, user);
        //调用 Flow 执行逻辑处理方法
        final String message = this.executorManagerAdapter.submitExecutableFlow(exflow, user.getUserId());
        logger.info("历史补采执行Flow日志: " + message);

        return exflow;
    }

    private static final String FILTER_BY_REPEAT_DATE_PATTERN = "yyyy/MM/dd HH:mm";

    private static final String FILTER_BY_REPEAT_DATE_PATTERN_NEW = "yyyy/MM/dd";

    /**
     * 数据补采 时间计算方法
     *
     * @param jsonObject 开始时间，结束时间，执行间隔
     * @return 按照时间执行顺序返回一个时间参数集合
     */
    private Map<String, Object> repeatDateCompute(final JsonObject jsonObject) {

        Map<String, Object> repeatOptionMap = new HashMap<>();

        List<Map<String, String>> repeatTimeList = new LinkedList<>();
        Set<Long> timeList = new HashSet<>();
        List<Long> runDateTimeList = GsonUtils.jsonToJavaObject(jsonObject.getAsJsonArray("runDateTimeList"), new TypeToken<List<Long>>() {}.getType());
        timeList.addAll(runDateTimeList);
        try {
            String recoverNum = jsonObject.get("recoverNum").getAsString();
            String recoverInterval = jsonObject.get("recoverInterval").getAsString();

            final String begin = jsonObject.get("begin").getAsString();

            final long beginTimeLong =
                DateTimeFormat.forPattern(FILTER_BY_REPEAT_DATE_PATTERN_NEW).parseDateTime(begin).getMillis();

            repeatOptionMap.put("recoverStartTime", beginTimeLong);

            final String end = jsonObject.get("end").getAsString();

            final long endTimeLong =
                DateTimeFormat.forPattern(FILTER_BY_REPEAT_DATE_PATTERN_NEW).parseDateTime(end).getMillis();

            repeatOptionMap.put("recoverEndTime", endTimeLong);

            //拼接前端的执行间隔字符串
            String executeInterval = "";
            if (!"0".equals(recoverNum)) {
                executeInterval = recoverNum + recoverInterval;
            }
            //执行间隔记录
            repeatOptionMap.put("exInterval", executeInterval);

            //设置时区
            ZoneId zoneId = ZoneId.systemDefault();

            Instant startInstant = Instant.ofEpochMilli(beginTimeLong);
            //转换成LocalDateTime对象
            LocalDateTime startTime = LocalDateTime.ofInstant(startInstant, zoneId);
            System.out.println("startTime: " + startTime);

            Instant endInstant = Instant.ofEpochMilli(endTimeLong);

            LocalDateTime endTime = LocalDateTime.ofInstant(endInstant, zoneId);
            System.out.println("startTime: " + endTime);

            Duration duration = Duration.between(startTime, endTime);

            long betweenDay = duration.toDays();
            long betweenHours = duration.toHours();
            long betweenMin = duration.toMinutes();

            //记录第一天数据补采时间
            //if(startTime.isEqual(endTime) || (betweenMin >= 0 && betweenMin < 1440 )){
            if (repeatTimeList.size() == 0) {
                Map<String, String> jobStartTime = new HashMap<>();
                //组装repeatOption的参数
                jobStartTime.put("RepeatType", "RepeatFlow");
                Long tmp = startTime.atZone(zoneId).toInstant().toEpochMilli();
                jobStartTime.put("startTimeLong", tmp + "");
                timeList.add(tmp);
                jobStartTime.put("recoverStatus", Status.PREPARING.getNumVal() + "");
                jobStartTime.put("exeId", "");

                repeatTimeList.add(jobStartTime);
            }
            //判断开始时间是否时一个月的最后一天
            int startDay = startTime.getDayOfMonth();
            boolean isLastDay = false;
            if (startDay == getLastDayOfMonth(startTime)) {
                isLastDay = true;
            }

            //多天数据补采时间
            while (startTime.isBefore(endTime) && !startTime.isEqual(endTime)) {
            //先记录第一次执行时间再开始相加
                //执行间隔计算
                if ("month".equals(recoverInterval)) {

                    int oldDay = startTime.getDayOfMonth();
                    int oldLastDay = getLastDayOfMonth(startTime);

                    startTime = startTime.plus(Long.valueOf(recoverNum), ChronoUnit.MONTHS);

                    int newDay = startTime.getDayOfMonth();
                    int newLastDay = getLastDayOfMonth(startTime);

                    if (isLastDay) {
                        startTime = startTime.withDayOfMonth(newLastDay);
                    } else if (!isLastDay && oldDay < startDay) {
                        startTime = startTime.withDayOfMonth(startDay);
                    }


                } else if ("week".equals(recoverInterval)) {
                    startTime = startTime.plus(Long.valueOf(recoverNum), ChronoUnit.WEEKS);
                } else if ("day".equals(recoverInterval)) {
                    startTime = startTime.plus(Long.valueOf(recoverNum), ChronoUnit.DAYS);
                }

                Map<String, String> jobStartTime = new HashMap<>();
                //组装repeatOption的参数
                jobStartTime.put("RepeatType", "RepeatFlow");
                Long tmp = startTime.atZone(zoneId).toInstant().toEpochMilli();
                jobStartTime.put("startTimeLong", tmp + "");
                jobStartTime.put("recoverStatus", Status.PREPARING.getNumVal() + "");
                jobStartTime.put("exeId", "");
                //如果开始时间大于结束时间 就补记录了
                if (startTime.isBefore(endTime) || startTime.isEqual(endTime)) {
                    repeatTimeList.add(jobStartTime);
                    timeList.add(tmp);
                }

            }

        } catch (Exception e) {
            e.printStackTrace();
        }
        repeatTimeList.clear();
        List<Long> flowDateList = new ArrayList<>(timeList);
        Collections.sort(flowDateList);
        // 检查是否设置逆序执行
        String reverseExecuteFlag = jsonObject.get("reverseExecuteFlag").getAsString();
        if ("true".equals(reverseExecuteFlag)) {
            Collections.reverse(flowDateList);
        }
        for (Long t : flowDateList) {
            Map<String, String> jobStartTime = new HashMap<>();
            //组装repeatOption的参数
            jobStartTime.put("RepeatType", "RepeatFlow");
            jobStartTime.put("startTimeLong", t + "");
            jobStartTime.put("recoverStatus", Status.PREPARING.getNumVal() + "");
            jobStartTime.put("exeId", "");
            repeatTimeList.add(jobStartTime);
        }
        repeatOptionMap.put("repeatTimeList", repeatTimeList);

        return repeatOptionMap;
    }


    /**
     * java8(别的版本获取2月有bug) 获取某月最后一天的23:59:59
     *
     * @return
     */
    public static int getLastDayOfMonth(LocalDateTime startTime) {

        //LocalDateTime localDateTime = LocalDateTime.ofInstant(Instant.ofEpochMilli(date.getTime()), ZoneId.systemDefault());;
        LocalDateTime endOfDay = startTime.with(TemporalAdjusters.lastDayOfMonth()).with(LocalTime.MAX);
        //Date dates = Date.from(endOfDay.atZone(ZoneId.systemDefault()).toInstant());
        return endOfDay.getDayOfMonth();
    }

    /**
     * 数据补采 执行间隔参数校验
     *
     * @param req
     * @return
     * @throws ServletException
     */
    private Boolean repeatOptionCheck(final HttpServletRequest req) throws ServletException {
//    String month = getParam(req, "month");
//    String day = getParam(req, "day");
//    String hour = "0";//getParam(req, "hour");
//    String min = "0";//getParam(req, "min");

        String recoverNum = getParam(req, "recoverNum");
        String recoverInterval = getParam(req, "recoverInterval");

        if ("0".equals(recoverNum)) {
            return false;
        } else if ("".equals(recoverNum) || "".equals(recoverInterval)) {
            return false;
        } else {
            return true;
        }

    }

    /**
     * 处理数据补采终止的方法
     *
     * @param req
     * @param resp
     * @param ret
     * @param user
     * @throws ServletException
     */
    private void ajaxStopRepeat(final HttpServletRequest req, final HttpServletResponse resp,
        final HashMap<String, Object> ret, final User user)
        throws ServletException {

        final String repeatId = getParam(req, "repeatId");

        this.repeatStopMap.put(repeatId, repeatId);
        ExecutionRecover executionRecover = null;
        try {
            Integer recoverId = Integer.valueOf(repeatId);
            synchronized (Constants.HISTORY_RERUN_LOCK) {
                logger.info("set history recover flow status to killed, recover id: {}.", recoverId);
                executionRecover = this.executorManagerAdapter.getHistoryRecoverFlow(recoverId);
                if (null != executionRecover) {
                    executionRecover.setRecoverStatus(Status.KILLED);
                    executionRecover.setEndTime(System.currentTimeMillis());
                    executorManagerAdapter.updateHistoryRecover(executionRecover);
                }
            }
            //终止 Flow 的逻辑方法
            List<ExecutableFlow> executableFlows = this.executorManagerAdapter.getExecutableFlowByRepeatId(recoverId);
            for(ExecutableFlow executableFlow: executableFlows) {
                logger.info("cancel flow: {}", executableFlow.getExecutionId());
                this.executorManagerAdapter.cancelFlow(executableFlow, user.getUserId());
            }
        } catch (final ExecutorManagerException e) {
            ret.put("error", e.getMessage());
        } finally {
            // 状态已经终止, 状态为完成,触发告警
            try {
                if (null != executionRecover && executionRecover.isFinishedAlert()) {
                    Alerter mailAlerter = alerterHolder.get("email");
                    if(null == mailAlerter){
                        mailAlerter = alerterHolder.get("default");
                    }
                    Project project = this.projectManager.getProject(executionRecover.getProjectId());
                    executionRecover.setProjectName(project.getName());
                    mailAlerter.alertOnHistoryRecoverFinish(executionRecover);
                } else {
                    logger.error("正在运行页面KILL历史重跑, executionRecover is null or alarm not set.");
                }
            } catch (Exception e) {
                logger.error("正在运行页面KILL历史重跑, 触发历史重跑告警失败, 失败原因:{}", e);
            }
        }

    }

    /**
     * 数据补采历史页面
     *
     * @param req
     * @param resp
     * @param session
     * @throws ServletException
     */
    private void ajaxHistoryRecoverPage(final HttpServletRequest req, final HttpServletResponse resp, final Session session)
        throws ServletException {
        final Page page =
            newPage(req, resp, session, "azkaban/webapp/servlet/velocity/history-recover-page.vm");

        String languageType = LoadJsonUtils.getLanguageType();
        Map<String, String> historyRecoverPageMap;
        Map<String, String> subPageMap1;
        Map<String, String> subPageMap2;
        if (languageType.equalsIgnoreCase("zh_CN")) {
            // 添加国际化标签
            historyRecoverPageMap = LoadJsonUtils.transJson("/conf/azkaban-web-server-zh_CN.json",
                "azkaban.webapp.servlet.velocity.history-recover-page.vm");
            subPageMap1 = LoadJsonUtils.transJson("/conf/azkaban-web-server-zh_CN.json",
                "azkaban.webapp.servlet.velocity.nav.vm");
            subPageMap2 = LoadJsonUtils.transJson("/conf/azkaban-web-server-zh_CN.json",
                "azkaban.webapp.servlet.velocity.messagedialog.vm");
        }else {
            historyRecoverPageMap = LoadJsonUtils.transJson("/conf/azkaban-web-server-en_US.json",
                "azkaban.webapp.servlet.velocity.history-recover-page.vm");
            subPageMap1 = LoadJsonUtils.transJson("/conf/azkaban-web-server-en_US.json",
                "azkaban.webapp.servlet.velocity.nav.vm");
            subPageMap2 = LoadJsonUtils.transJson("/conf/azkaban-web-server-en_US.json",
                "azkaban.webapp.servlet.velocity.messagedialog.vm");
        }
        historyRecoverPageMap.forEach(page::add);
        subPageMap1.forEach(page::add);
        subPageMap2.forEach(page::add);
        page.add("currentlangType", languageType);
        int pageNum = getIntParam(req, "page", 1);
        final int pageSize = getIntParam(req, "size", 16);

        if (pageNum < 0) {
            pageNum = 1;
        }

        final User user = session.getUser();
        Set<String> userRoleSet = new HashSet<>();
        userRoleSet.addAll(user.getRoles());

        List<ExecutionRecover> historyRecover = new ArrayList<>();

        //添加权限判断 admin 用户能查看所有flow历史 user用户只能查看自己的flow历史
        if (userRoleSet.contains("admin")) {
            //查询数据补采全部记录
            try {
                historyRecover = this.executorManagerAdapter.listHistoryRecoverFlows(new HashMap(), (pageNum - 1) * pageSize, pageSize);
            } catch (ExecutorManagerException e) {
                logger.error("查询数据补采全部记录失败,原因为:" + e);
            }
        } else {
            try {

                Map paramMap = new HashMap();

                paramMap.put("userName", user.getUserId());

                historyRecover =
                    this.executorManagerAdapter.listHistoryRecoverFlows(paramMap, (pageNum - 1) * pageSize, pageSize);
            } catch (ExecutorManagerException e) {
                e.printStackTrace();
            }
        }

        if (null != historyRecover && !historyRecover.isEmpty()) {
            historyRecover.stream().forEach(historyFlow -> {
                Map<String, Object> repeatMap = historyFlow.getRepeatOption();
                if (!repeatMap.isEmpty()) {
                    List<Map<String, String>> list = (List<Map<String, String>>) repeatMap.get("repeatTimeList");
                    for (Map<String, String> repMap : list) {
                        if (!repMap.get("exeId").isEmpty() && historyFlow.getNowExecutionId() == Integer.valueOf(repMap.get("exeId"))) {
                            historyFlow.setUpdateTime(Long.valueOf(String.valueOf(repMap.get("startTimeLong"))));
                        }
                    }
                }
            });
        }

        page.add("historyRecover", historyRecover.isEmpty() ? null : historyRecover);

        page.add("vmutils", new VelocityUtil(this.projectManager));

        page.add("size", pageSize);
        page.add("page", pageNum);

        if (pageNum == 1) {
            page.add("previous", new PageSelection(1, pageSize, true, false));
        } else {
            page.add("previous", new PageSelection(pageNum - 1, pageSize, false,false));
        }
        page.add("next", new PageSelection(pageNum + 1, pageSize, false, false));
        // Now for the 5 other values.
        int pageStartValue = 1;
        if (pageNum > 3) {
            pageStartValue = pageNum - 2;
        }

        page.add("page1", new PageSelection(pageStartValue, pageSize, false,
            pageStartValue == pageNum));
        pageStartValue++;
        page.add("page2", new PageSelection(pageStartValue, pageSize, false,
            pageStartValue == pageNum));
        pageStartValue++;
        page.add("page3", new PageSelection(pageStartValue, pageSize, false,
            pageStartValue == pageNum));
        pageStartValue++;
        page.add("page4", new PageSelection(pageStartValue, pageSize, false,
            pageStartValue == pageNum));
        pageStartValue++;
        page.add("page5", new PageSelection(pageStartValue, pageSize, false,
            pageStartValue == pageNum));
        pageStartValue++;


        page.render();

    }

//  /**
//   * 日志按级别分割处理方法
//   * @param logData
//   * @param logType
//   * @return
//   */
//  private String handleLogData(String logData, String logType){
//    String logResult = "";
//
//    String nowCutType = "";
//
//    StringBuilder sb = new StringBuilder();
//
//    String[] handleData = logData.split("\n");
//
//    if("error".equals(logType)){
//      String errorBegin = "";
//      for(String lineLog : handleData){
//        //ERROR日志切割
//        if(lineLog.contains(" ERROR - ")){
//          errorBegin = "START";
//          nowCutType = "ERROR";
//          sb.append(lineLog).append("\n");
//        }else if((lineLog.contains(" ERROR - ") || lineLog.contains(" INFO - ")) && "START".equals(errorBegin) && "ERROR".equals(nowCutType)){
//          errorBegin = "END";
//        }else if("START".equals(errorBegin) && "ERROR".equals(nowCutType)){
//          sb.append(lineLog).append("\n");
//        }
//        //INFO 日志里的异常情况
//        if(lineLog.contains(" INFO - ") && (lineLog.contains("Exception") || lineLog.contains("Error"))){
//          errorBegin = "START";
//          nowCutType = "INFO";
//          sb.append(lineLog).append("\n");
//        }else if(lineLog.contains(" INFO - ") && !lineLog.contains("Exception") && !lineLog.contains(" INFO - \t") && "INFO".equals(nowCutType)){
//          errorBegin = "END";
//        }else if("START".equals(errorBegin) && "INFO".equals(nowCutType)){
//          sb.append(lineLog).append("\n");
//        }
//      }
//    }else if("info".equals(logType)){
//      for(String lineLog : handleData){
//        if(lineLog.contains(" INFO - ") && !lineLog.contains("Exception") && !lineLog.contains(" INFO - \t") ){
//          sb.append(lineLog).append("\n");
//        }
//      }
//    }
//    logResult = sb.toString();
//
//    return logResult;
//  }

    /**
     * 数据补采 执行数据校验 通过之后才能执行数据补采
     *
     * @param req
     * @param resp
     * @param ret
     * @param user
     * @throws ServletException
     */
    private void ajaxRecoverParamVerify(final HttpServletRequest req, final HttpServletResponse resp,
        final HashMap<String, Object> ret, final User user) throws ServletException {
        final String projectName = getParam(req, "project");
        final String flowId = getParam(req, "flow");
        // 检查项目是否存在，工作流基于project这一层级
        final Project project = getProjectAjaxByPermission(ret, projectName, user, Type.EXECUTE);

        Map<String, String> dataMap = loadExecutorServletI18nData();

        if (project == null) {
            ret.put("error", "Project '" + projectName + "' doesn't exist.");
            return;
        }
        ret.put("flow", flowId);
        // 检查工作流是否存在
        final Flow flow = project.getFlow(flowId);
        if (flow == null) {
            ret.put("error", "Flow '" + flowId + "' cannot be found in project "
                + project);
            return;
        }

        ExecutionRecover nowRuningRecover = null;
        //查询这个Flow的补采记录
        try {
            nowRuningRecover =
                this.executorManagerAdapter.getHistoryRecoverFlowByPidAndFid(String.valueOf(project.getId()), flow.getId());
        } catch (ExecutorManagerException e) {
            logger.error("获取历史重跑任务失败" + e);
        }
        //校验这个Flow是否已经开始补采
        if (null != nowRuningRecover && (Status.RUNNING.equals(nowRuningRecover.getRecoverStatus())
            || Status.PREPARING.equals(nowRuningRecover.getRecoverStatus()))) {
            logger.error("recover id: " + nowRuningRecover.getRecoverId() + ", status : " + nowRuningRecover.getRecoverStatus());
            ret.put("error", dataMap.get("existHisJob"));
            return;
        }

        //concurrentOption,
        final String concurrentOption = getParam(req, "concurrentOption");
        //判断flow是否真正运行
        if (concurrentOption.equals("skip") && this.executorManagerAdapter.getRunningFlows(project.getId(), flowId).size() != 0) {
            ret.put("error", "flow: " + flowId + dataMap.get("runningCanNotCommit"));
            return;
        }

        //参数校验
        if (!repeatOptionCheck(req)) {
            ret.put("error", dataMap.get("dataCompensateinputParamError"));
            return;
        }

    }


    /**
     * Job日志下载处理方法
     * 根据 execId 和 jobId 查找到对应的日志，然后读取到web节点的temp中的
     */
    private void handleDownloadExecLog(final HttpServletRequest req, final HttpServletResponse resp, final Session session)
        throws ServletException, IOException {

        final User user = session.getUser();
        final int execId = getIntParam(req, "execid");
        final String jobName = getParam(req, "job");
        logger.info(user.getUserId() + " is downloading log execId: " + execId);

        FileInputStream inStream = null;
        OutputStream outStream = null;

        String logZipPath = "";

        Map<String, String> dataMap = loadExecutorServletI18nData();

        try {
            logZipPath = this.executorManagerAdapter.getJobLogByJobId(execId, jobName);

            final File logZipFile = new File(logZipPath);

            if (!logZipFile.exists()) {
                this.setErrorMessageInCookie(resp, dataMap.get("job") + jobName + dataMap.get("haveNoLog"));
                resp.sendRedirect(req.getContextPath() + "executor?execid=" + execId + "#jobslist");
                return;
            }

            // now set up HTTP response for downloading file
            inStream = new FileInputStream(logZipFile);

            resp.setContentType("application/zip");

            final String headerKey = "Content-Disposition";
            final String headerValue = String.format("attachment; filename=\"%s\"", logZipFile.getName());
            resp.setHeader(headerKey, headerValue);
            resp.setHeader("execId", execId + "");

            outStream = resp.getOutputStream();

            final byte[] buffer = new byte[8192];
            int bytesRead = -1;

            while ((bytesRead = inStream.read(buffer)) != -1) {
                outStream.write(buffer, 0, bytesRead);
            }

        } catch (final Throwable e) {
            logger.error("", e);
            throw new ServletException(e);
        } finally {
            IOUtils.closeQuietly(inStream);
            IOUtils.closeQuietly(outStream);
            if (!logZipPath.isEmpty()) {
                //先删除压缩包
                FileIOUtils.deleteDir(new File(logZipPath));
                logZipPath = logZipPath.replace(".zip", "");
                //再删除临时文件夹
                FileIOUtils.deleteDir(new File(logZipPath));
            }
        }

    }

    private Map<String, Object> getExecutableNodeInfo(final ExecutableNode node, final Long runDate,
        int executionId) {

        final HashMap<String, Object> nodeObj = new HashMap<>();
        nodeObj.put("id", node.getId());
        nodeObj.put("status", node.getStatus());
        nodeObj.put("startTime", node.getStartTime());
        nodeObj.put("endTime", node.getEndTime());
        nodeObj.put("updateTime", node.getUpdateTime());
        nodeObj.put("type", node.getType());
        nodeObj.put("nestedId", node.getNestedId());
        nodeObj.put("runDate", runDate);
        if (node.getCondition() != null) {
            nodeObj.put("condition", node.getCondition());
        }

        nodeObj.put("attempt", node.getAttempt());
        if (node.getAttempt() > 0) {
            nodeObj.put("pastAttempts", node.getAttemptObjects());
        }

        if (node.getInNodes() != null && !node.getInNodes().isEmpty()) {
            nodeObj.put("in", node.getInNodes());
        }

        if (node instanceof ExecutableFlowBase) {
            final ExecutableFlowBase base = (ExecutableFlowBase) node;
            final ArrayList<Map<String, Object>> nodeList = new ArrayList<>();

            for (final ExecutableNode subNode : base.getExecutableNodes()) {
                final Map<String, Object> subNodeObj = getExecutableNodeInfo(subNode, runDate, executionId);
                if (!subNodeObj.isEmpty()) {
                    nodeList.add(subNodeObj);
                }
            }
            nodeObj.put("execid", executionId);
            nodeObj.put("flow", base.getFlowId());
            nodeObj.put("nodes", nodeList);
            nodeObj.put("flowId", base.getFlowId());
        }

        return nodeObj;
    }

    public static class PageSelection {

        private final int page;
        private final int size;
        private final boolean disabled;
        private boolean selected;

        public PageSelection(final int page, final int size, final boolean disabled,
            final boolean selected) {
            this.page = page;
            this.size = size;
            this.disabled = disabled;
            this.setSelected(selected);
        }

        public int getPage() {
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

    /**
     * 临时执行或者单次执行
     * @param projectName
     * @param flowName
     * @param ret
     * @param user
     * @throws ServletException
     */
    private void ajaxFetchExecutionFlowGraphNew(final String projectName, final String flowName,
        final HashMap<String, Object> ret, final User user) throws ServletException {

        final Project project = getProjectAjaxByPermission(ret, projectName, user, Type.EXECUTE);
        if (project == null) {
            ret.put("error", "Project '" + projectName + "' doesn't exist.");
            return;
        }
        try {
            final ExecutionOptions executionOptions =  new ExecutionOptions();
            final Flow flow = project.getFlow(flowName);
            if (flow == null) {
                ret.put("error", "Flow '" + flowName + "' cannot be found in project " + project);
                return;
            }
            final ExecutableFlow exFlow = new ExecutableFlow(project, flow);
            exFlow.setExecutionOptions(executionOptions);
            ret.put("submitTime", exFlow.getSubmitTime());
            ret.put("submitUser", exFlow.getSubmitUser());
            ret.put("execid", exFlow.getExecutionId());
            ret.put("projectId", exFlow.getProjectId());
            ret.put("project", project.getName());
            FlowUtils.applyDisabledJobs(executionOptions.getDisabledJobs(), exFlow);
            final Map<String, Object> flowObj = getExecutableNodeInfo(exFlow, exFlow.getExecutionId());
            ret.putAll(flowObj);
        } catch (final Exception ex) {
            throw new ServletException(ex);
        }
    }

    private void ajaxFetchExecutionFlowGraph(final String projectName, final String flowName,
        final HashMap<String, Object> ret, final User user) throws ServletException {

        final Project project = getProjectAjaxByPermission(ret, projectName, user, Type.EXECUTE);
        if (project == null) {
            ret.put("error", "Project '" + projectName + "' doesn't exist.");
            return;
        }
        try {
            final Schedule schedule = this.scheduleManager.getSchedule(project.getId(), flowName);
            // 读取是否存在调度在执行,但是读取这个调度会拿到调度设置的一些旧数据,影响临时执行
            final ExecutionOptions executionOptions = schedule != null ? schedule.getExecutionOptions() : new ExecutionOptions();
            // final ExecutionOptions executionOptions =  new ExecutionOptions();
            final Flow flow = project.getFlow(flowName);
            if (flow == null) {
                ret.put("error", "Flow '" + flowName + "' cannot be found in project " + project);
                return;
            }
            final ExecutableFlow exFlow = new ExecutableFlow(project, flow);
            exFlow.setExecutionOptions(executionOptions);
            ret.put("submitTime", exFlow.getSubmitTime());
            ret.put("submitUser", exFlow.getSubmitUser());
            ret.put("execid", exFlow.getExecutionId());
            ret.put("projectId", exFlow.getProjectId());
            ret.put("project", project.getName());
            FlowUtils.applyDisabledJobs(executionOptions.getDisabledJobs(), exFlow);
            final Map<String, Object> flowObj = getExecutableNodeInfo(exFlow, exFlow.getExecutionId());
            ret.putAll(flowObj);
        } catch (final ScheduleManagerException ex) {
            throw new ServletException(ex);
        }
    }

    /**
     * 对外开放的Job日志查询接口
     * 根据项目名称，工作流名称，任务名称获取任务日志
     *
     * @param req
     * @param resp
     * @param ret
     * @param user
     * @throws ServletException
     */
    private void extGetRecentJobLog(final HttpServletRequest req, final HttpServletResponse resp,
        final HashMap<String, Object> ret, final User user) throws ServletException {

        final String projectName = getParam(req, "projectName");
        final String flowName = getParam(req, "flowName");
        final String jobName = getParam(req, "jobName");

        final Project project = getProjectAjaxByPermission(ret, projectName, user, Type.READ);

        Map<String, String> dataMap = loadExecutorServletI18nData();

        if (project == null) {
            respMessageBuild("100001", ret, dataMap.get("project") + projectName + dataMap.get("notExist"), projectName);
            return;
        }

        final Flow flow = project.getFlow(flowName);
        if (flow == null) {
            respMessageBuild("100002", ret, dataMap.get("flow") + flowName + dataMap.get("notExist"), flowName);
            return;
        }

        resp.setCharacterEncoding("utf-8");

        try {
            final ExecutableFlow exFlow = this.executorManagerAdapter.getRecentExecutableFlow(project.getId(), flow.getId());

            final ExecutableNode node = exFlow.getExecutableNodePath(jobName);
            if (node == null) {
                respMessageBuild("100003", ret, dataMap.get("job") + jobName + dataMap.get("notExist"), jobName);
                return;
            }
            final int attempt = this.getIntParam(req, "attempt", node.getAttempt());
            //获取Job所有日志
            final String data = this.executorManagerAdapter.getAllExecutionJobLog(exFlow, jobName, attempt);

            String htmlStr = StringEscapeUtils.escapeHtml(data);
            htmlStr = StringEscapeUtils.unescapeHtml(htmlStr);
            respMessageBuild("200", ret, "success", jobName);
            ret.put("jobLog", htmlStr);

        } catch (final ExecutorManagerException e) {
            respMessageBuild("100004", ret, dataMap.get("getLatestLogFailed"), e.getMessage());
            throw new ServletException(e);
        }
    }

    /**
     * 对外开放的Job状态查询接口
     * 根据项目名称，工作流名称，任务名称获取任务状态
     *
     * @param req
     * @param resp
     * @param ret
     * @param user
     * @throws ServletException
     */
    private void extGetRecentJobStatus(final HttpServletRequest req, final HttpServletResponse resp,
        final HashMap<String, Object> ret, final User user) throws ServletException {

        final String projectName = getParam(req, "projectName");
        final String flowName = getParam(req, "flowName");
        final String jobName = getParam(req, "jobName");

        final Project project = getProjectAjaxByPermission(ret, projectName, user, Type.READ);
        if (project == null) {
            respMessageBuild("100001", ret, ret.get("error").toString(), projectName);
            return;
        }
        Map<String, String> dataMap = loadExecutorServletI18nData();
        final Flow flow = project.getFlow(flowName);
        if (flow == null) {
            respMessageBuild("100002", ret, dataMap.get("flow") + flowName + dataMap.get("notExist"), flowName);
            return;
        }

        resp.setCharacterEncoding("utf-8");

        try {
            final ExecutableFlow exFlow = this.executorManagerAdapter.getRecentExecutableFlow(project.getId(), flow.getId());

            final ExecutableNode node = exFlow.getExecutableNodePath(jobName);
            if (node == null) {
                respMessageBuild("100003", ret, dataMap.get("job") + jobName + dataMap.get("notExist"), flowName);
                return;
            }

            respMessageBuild("200", ret, "success");
            ret.put("jobStatus", node.getStatus());

        } catch (final ExecutorManagerException e) {
            respMessageBuild("100005", ret, dataMap.get("getLatestExecStatusFailed"), e.getMessage());
            throw new ServletException(e);
        }
    }

    /**
     * 对外开放的工作流执行接口
     * 根据项目名称，工作流名称，执行工作流
     *
     * @param req
     * @param resp
     * @param ret
     * @param user
     * @throws ServletException
     */
    private void extExecuteFlow(final HttpServletRequest req, final HttpServletResponse resp,
        final HashMap<String, Object> ret, final User user) throws ServletException {

        final String projectName = getParam(req, "projectName");
        final String flowName = getParam(req, "flowName");

        final Project project = getProjectAjaxByPermission(ret, projectName, user, Type.EXECUTE);
        if (project == null) {
            respMessageBuild("100001", ret, ret.get("error").toString(), projectName);
            return;
        }

        Map<String, String> dataMap = loadExecutorServletI18nData();

        final Flow flow = project.getFlow(flowName);
        if (flow == null) {
            respMessageBuild("100002", ret, dataMap.get("flow") + flowName + dataMap.get("notExist"), flowName);
            return;
        }

        final ExecutableFlow exflow = FlowUtils.createExecutableFlow(project, flow);
        exflow.setSubmitUser(user.getUserId());
        //获取项目默认代理用户
        Set<String> proxyUserSet = project.getProxyUsers();
        //设置用户代理用户
        proxyUserSet.add(user.getUserId());
        proxyUserSet.addAll(user.getProxyUsers());
        //设置代理用户
        exflow.addAllProxyUsers(proxyUserSet);

        final ExecutionOptions options = HttpRequestUtils.parseFlowOptions(req);
        exflow.setExecutionOptions(options);
        if (!options.isFailureEmailsOverridden()) {
            options.setFailureEmails(flow.getFailureEmails());
        }
        if (!options.isSuccessEmailsOverridden()) {
            options.setSuccessEmails(flow.getSuccessEmails());
        }
        options.setMailCreator(flow.getMailCreator());

        //设置其他参数配置
        Map<String, Object> otherOptions = new HashMap<>();

        //设置失败重跑配置
//    final Map<String, String> jobFailedRetrySettings = getParamGroup(req, "jobFailedRetryOptions");
//    final List<Map<String, String>> jobRetryList = new ArrayList<>();
//    for (final String set : jobFailedRetrySettings.keySet()) {
//      String[] setOption = jobFailedRetrySettings.get(set).split(",");
//      Map<String, String> jobOption = new HashMap<>();
//      jobOption.put("jobName", setOption[0].trim());
//      jobOption.put("interval", setOption[1].trim());
//      jobOption.put("count", setOption[2].trim());
//      jobRetryList.add(jobOption);
//    }
//
//    otherOptions.put("jobFailedRetryOptions", jobRetryList);
//    exflow.setOtherOption(otherOptions);

        //设置失败跳过配置
        final Map<String, String> jobSkipFailedSettings = getParamGroup(req, "jobSkipFailedOptions");
        final List<String> jobSkipList = new ArrayList<>();
        for (final String set : jobSkipFailedSettings.keySet()) {
            jobSkipList.add(jobSkipFailedSettings.get(set).trim());
        }

        otherOptions.put("jobSkipFailedOptions", jobSkipList);
        exflow.setOtherOption(otherOptions);

        //设置通用告警级别
        if (hasParam(req, "failureAlertLevel")) {
            otherOptions.put("failureAlertLevel", getParam(req, "failureAlertLevel"));
        }
        if (hasParam(req, "successAlertLevel")) {
            otherOptions.put("successAlertLevel", getParam(req, "successAlertLevel"));
        }
        try {
            //设置告警用户部门信息
            String userDep = transitionService.getUserDepartmentByUsername(user.getUserId());
            otherOptions.put("alertUserDeparment", userDep);
            HttpRequestUtils.filterAdminOnlyFlowParams(options, user);
            final String message = this.executorManagerAdapter.submitExecutableFlow(exflow, user.getUserId());
            ret.put("message", message);
        } catch (final Exception e) {
            logger.error(e.toString());
            respMessageBuild("100006", ret, dataMap.get("execFlow")+ exflow.getFlowId() + dataMap.get("failed"), exflow.getFlowId(), e.getMessage());
            return;
        }
        respMessageBuild("200", ret, "success");
        ret.put("execid", exflow.getExecutionId());
    }

    /**
     * 对外开放的终止工作流接口
     * 根据项目名称，工作流名称，终止工作流
     *
     * @param req
     * @param resp
     * @param ret
     * @param user
     * @throws ServletException
     */
    private void extCancelFlow(final HttpServletRequest req, final HttpServletResponse resp,
        final HashMap<String, Object> ret, final User user)
        throws ServletException {

        final String projectName = getParam(req, "projectName");
        final String flowName = getParam(req, "flowName");

        final Project project = getProjectAjaxByPermission(ret, projectName, user, Type.EXECUTE);
        if (project == null) {
            respMessageBuild("100001", ret, ret.get("error").toString(), projectName);
            return;
        }

        Map<String, String> dataMap = loadExecutorServletI18nData();

        final Flow flow = project.getFlow(flowName);
        if (flow == null) {
            respMessageBuild("100002", ret, dataMap.get("flow") + flowName + dataMap.get("notExist"), flowName);
            return;
        }

        resp.setCharacterEncoding("utf-8");
        int execId = 0;
        try {
            final ExecutableFlow exFlow = this.executorManagerAdapter.getRecentExecutableFlow(project.getId(), flow.getId());
            execId = exFlow.getExecutionId();
            this.executorManagerAdapter.cancelFlow(exFlow, user.getUserId());
        } catch (final ExecutorManagerException e) {
            respMessageBuild("100007", ret, dataMap.get("execId") + execId + dataMap.get("flow") + flowName + dataMap.get("stopFailed"), execId, flowName, e);
            return;
        }
        respMessageBuild("200", ret, "Execute Id: %s Flow %s Shutdown success. ", execId, flowName);
    }

    /**
     * 正在运行模块页面
     */
    private void ajaxGetExecutingFlowData(final HttpServletRequest req, final HttpServletResponse resp,
        final HashMap<String, Object> ret, final User user) throws ServletException, IOException {

        Set<String> userRoleSet = new HashSet<>();
        userRoleSet.addAll(user.getRoles());

        final List<Map<String, String>> runningFlowsList = this.executorManagerAdapter.getExectingFlowsData();

        //添加权限判断 admin 用户能查看所有flow历史 user用户只能查看自己的flow历史
        if (userRoleSet.contains("admin")) {

            ret.put("executingFlowData", runningFlowsList);
            return;
        //运维管理员可以查看自己运维部门下所有人提交且正在运行的工作流
        } else if (systemManager.isDepartmentMaintainer(user)) {
            List<Integer> maintainedProjectIds = systemManager.getMaintainedProjects(user);
            final List<Map<String, String>> userRunningFlowsList = runningFlowsList.stream()
                    .filter(map -> isMaintainedProject(map, maintainedProjectIds) || isOwner(map, user))
                    .collect(Collectors.toList());
            // #164643 查询该用户能查看的项目id
            List<Integer> projectIds = this.executorManagerAdapter.fetchPermissionsProjectId(user.getUserId());
            final Set<Map<String, String>> userRunningFlowsList2 = runningFlowsList.stream()
                .filter(map -> hasPermission(Integer.valueOf(map.get("projectId")), projectIds) || isOwner(map, user))
                .collect(Collectors.toSet());
            userRunningFlowsList2.addAll(userRunningFlowsList);
            ret.put("executingFlowData", userRunningFlowsList2);
        } else {
            // 查询该用户能查看的项目id
            List<Integer> projectIds = this.executorManagerAdapter.fetchPermissionsProjectId(user.getUserId());
            final List<Map<String, String>> userRunningFlowsList = runningFlowsList.stream()
                .filter(map -> hasPermission(Integer.valueOf(map.get("projectId")), projectIds) || isOwner(map, user))
                .collect(Collectors.toList());
            ret.put("executingFlowData", userRunningFlowsList);

        }


    }

    private boolean hasPermission(int projectId, List<Integer> projectList){
        if(projectList.contains(projectId)){
            return true;
        }
        return false;
    }

    private boolean isOwner(Map<String, String> map, User user) {
        try {
            int flowType = Integer.parseInt(map.get("flowType"));
            if (flowType == 4) {
                String projectId = map.get("projectId");
                String flowName = map.get("flowName");
                ExecutionCycle cycleFlow = executorManagerAdapter.getExecutionCycleFlow(projectId, flowName);
                return user.getUserId().equals(cycleFlow.getSubmitUser());
            } else {
                return user.getUserId().equals(map.get("submitUser"));
            }
        } catch (Exception e) {
            return user.getUserId().equals(map.get("submitUser"));
        }
    }

    private boolean isMaintainedProject(Map<String, String> map, List<Integer> projectIds) {
        String projectId = map.get("projectId");
        return projectIds.stream()
                .anyMatch(id -> id.toString().equals(projectId));
    }

    /**
     * reload data for ha
     * @param req
     * @throws ServletException
     */
    private void reloadWebData(final HttpServletRequest req)
        throws ServletException, TriggerManagerException, ScheduleManagerException {

        final String type = getParam(req, "reloadType");
        final int triggerId = getIntParam(req, "triggerId", -1);
        final String projectName = getParam(req, "projectName","");
        switch (type) {
            case "runningExecutions":
                this.executorManagerAdapter.reloadWebData();
                break;
            case "deleteTrigger":
                this.triggerManager.removeTriggerByWeb(triggerId);
                break;
            case "insertTrigger":
                this.triggerManager.insertTriggerByWeb(triggerId);
                break;
            case "updateTrigger":
                this.triggerManager.updateTriggerByWeb(triggerId);
                break;
            case "refreshProjectPermission":
                this.projectManager.refreshProjectPermission(projectName);
                break;
            case "reloadProject":
                this.projectManager.reloadProject(projectName);
                break;
            case "deleteProject":
                this.projectManager.deleteProjectByWeb(getIntParam(req, "projectId", -1));
                break;
            default:
        }

    }

}
