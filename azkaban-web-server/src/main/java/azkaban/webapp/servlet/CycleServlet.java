package azkaban.webapp.servlet;

import azkaban.executor.*;
import azkaban.flow.Flow;
import azkaban.project.Project;
import azkaban.project.ProjectLogEvent;
import azkaban.project.ProjectManager;
import azkaban.scheduler.EventScheduleServiceImpl;
import azkaban.server.session.Session;
import azkaban.system.SystemManager;
import azkaban.system.common.TransitionService;
import azkaban.user.Permission;
import azkaban.user.User;
import azkaban.webapp.AzkabanWebServer;
import org.apache.commons.collections4.CollectionUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

import static java.util.stream.Collectors.joining;
import static java.util.stream.Collectors.toList;

public class CycleServlet extends AbstractLoginAzkabanServlet {

    private static final Logger logger = LoggerFactory.getLogger(CycleServlet.class.getName());
    private static final long serialVersionUID = 1L;
    private ExecutorManagerAdapter executorManager;
    private ProjectManager projectManager;
    private TransitionService transitionService;
    private SystemManager systemManager;
    private AlerterHolder alerterHolder;

    private EventScheduleServiceImpl eventScheduleService;

    @Override
    public void init(ServletConfig config) throws ServletException {
        super.init(config);
        final AzkabanWebServer server = (AzkabanWebServer) getApplication();
        this.executorManager = server.getExecutorManager();
        this.projectManager = server.getProjectManager();
        this.transitionService = server.getTransitionService();
        this.systemManager = transitionService.getSystemManager();
        this.eventScheduleService = server.getEventScheduleService();
        this.alerterHolder = server.getAlerterHolder();
    }

    @Override
    protected void handleGet(HttpServletRequest req, HttpServletResponse resp, Session session)
        throws ServletException, IOException {
        if (hasParam(req, "ajax")) {
            handleAJAXAction(req, resp, session);
        } else {
            ajaxExecutionCyclePage(req, resp, session);
        }
    }

    @Override
    protected void handlePost(HttpServletRequest req, HttpServletResponse resp, Session session) throws ServletException, IOException {
        if (hasParam(req, "ajax")) {
            handleAJAXAction(req, resp, session);
        }
    }

    private void handleAJAXAction(HttpServletRequest req, HttpServletResponse resp, Session session)
            throws ServletException, IOException {
        String ajaxName = getParam(req, "ajax");
        if ("fetchCycleFlows".equals(ajaxName)) {
            ajaxFetchExecutionCycle(req, resp, session);
        } else if ("stopAllCycleFlows".equals(ajaxName)) {
            ajaxStopAllCycleFlows(resp, session);
        } else if ("executionCyclePage".equals(ajaxName)) {
            ajaxExecutionCyclePage(req, resp, session);

        } else if ("deleteCycleFlows".equals(ajaxName)) {
            ajaxDeleteExecutionCycle(req, resp, session);
        }
    }

    private void ajaxFetchExecutionCycle(HttpServletRequest req, HttpServletResponse resp, Session session) throws IOException {
        int pageNum = getIntParam(req, "page", 1);
        int pageSize = getIntParam(req, "pageSize", 20);
        int offset = (pageNum - 1) * pageSize;
        User user = session.getUser();
        Set<String> roles = new HashSet<>(user.getRoles());
        HashMap<String, Object> map = new HashMap<>();
        int cycleFlowsTotal = 0;
        List<ExecutionCycle> cycleFlows = new ArrayList<>();
        try {
            if (roles.contains("admin")) {
                cycleFlowsTotal = executorManager.getExecutionCycleTotal(Optional.empty());
                cycleFlows = executorManager.listExecutionCycleFlows(Optional.empty(), offset, pageSize);

            } else if (systemManager.isDepartmentMaintainer(user)) {
                List<Integer> maintainedProjectIds = systemManager.getMaintainedProjects(user, 1);
                cycleFlowsTotal = executorManager.getExecutionCycleTotal(user.getUserId(), maintainedProjectIds);
                cycleFlows = executorManager.listExecutionCycleFlows(user.getUserId(), maintainedProjectIds, offset, pageSize);
            } else {
                cycleFlowsTotal = executorManager.getExecutionCycleTotal(Optional.of(user.getUserId()));
                cycleFlows = executorManager.listExecutionCycleFlows(Optional.of(user.getUserId()), offset, pageSize);
            }
        } catch (ExecutorManagerException e) {
            map.put("error", "Error fetch execution cycle");
        }
        map.put("total", cycleFlowsTotal);
        map.put("page",pageNum);
        map.put("pageSize", pageSize);
        map.put("executionCycleList", cycleFlows2ListMap(cycleFlows));
        writeJSON(resp, map);
    }

    private void ajaxStopAllCycleFlows(HttpServletResponse resp, Session session) throws ServletException, IOException {
        try {
            Map<String, Object> map = new HashMap<>();
            User user = session.getUser();
            Set<String> roles = new HashSet<>(user.getRoles());
            if (roles.contains("admin")) {
                List<ExecutionCycle> executionCycles = executorManager.getAllRunningCycleFlows();
                logger.info("starting stop all cycle flows");
                executorManager.stopAllCycleFlows();
                logger.info("stopped all cycle flows successful");
                alertOnCycleFlowInterrupt(executionCycles);
                map.put("result", "success");
            } else {
                map.put("result", "failed, has no permission");
            }
            writeJSON(resp, map);
        } catch (ExecutorManagerException e) {
            logger.error("ajax stop all cycle flows failed", e);
            throw new ServletException(e);
        }
    }

    private Object cycleFlows2ListMap(List<ExecutionCycle> cycleFlows) {
        return cycleFlows.stream()
            .map(flow -> {
                Map<String, Object> map = new HashMap<>();
                map.put("id", flow.getId());
                map.put("status", flow.getStatus());
                map.put("currentExecId", flow.getCurrentExecId());
                Project project = projectManager.getProject(flow.getProjectId());
                map.put("projectName", project.getName());
                map.put("flowId", flow.getFlowId());
                map.put("submitUser", flow.getSubmitUser());
                String proxyUsers = project.getProxyUsers().stream()
                    .collect(joining(",", "[", "]"));
                map.put("proxyUsers", proxyUsers);

                return map;
            })
            .collect(toList());
    }

    private Object cycleFlows2ListMapToPages(List<ExecutionCycle> cycleFlows) {
        List<Object> mapList = new ArrayList<>();
        if (CollectionUtils.isNotEmpty(cycleFlows)){
            cycleFlows.forEach(flow -> {
                try {
                    Map<String, Object> map = new HashMap<>();
                    ExecutionCycle execFlow = executorManager.getExecutionCycleFlowDescId(flow.getProjectId() + "", flow.getFlowId());
                    map.put("id", execFlow.getId());
                    map.put("status", execFlow.getStatus());
                    map.put("currentExecId", execFlow.getCurrentExecId());
                    Project project = projectManager.getProject(flow.getProjectId());
                    if (project != null) {
                        map.put("projectName", project.getName());
                    } else {
                        map.put("projectName", "");
                    }
                    map.put("flowId", execFlow.getFlowId());
                    map.put("projectId", execFlow.getProjectId());
                    map.put("submitUser", execFlow.getSubmitUser());
                    map.put("updateTime", execFlow.getUpdateTime());
                    String proxyUsers = project.getProxyUsers().stream()
                            .collect(joining(",", "[", "]"));
                    map.put("proxyUsers", proxyUsers);
                    Map<String, Object> otherOption = execFlow.getOtherOption();

                    // 检查工作流是否有效
                    HashMap<String, Boolean> checkMap = new HashMap<>();
                    if (project != null) {
                        checkValidFlows(project, checkMap, execFlow.getFlowId());
                        otherOption.put("validFlow", checkMap.get("validFlow"));
                    }
                    otherOption.put("activeFlag", true);
                    Map<String, Object> cycleOptions = execFlow.getCycleOption();
                    try {
                        if (Objects.nonNull(cycleOptions) && Status.isStatusFailed(execFlow.getStatus())) {
                            if (cycleOptions.get("cycleErrorOption").equals("errorStop") || Status.KILLED.equals(execFlow.getStatus())) {
                                otherOption.put("activeFlag", false);
                            }

                        }
                    } catch (Exception e) {
                        logger.error("cycleErrorOption Exception" + e);
                        otherOption.put("activeFlag", false);
                    }
                    map.put("otherOptions", otherOption);
                    map.put("cycleOptions", execFlow.getCycleOption());
                    map.put("executionOptions", execFlow.getExecutionOptions());
                    mapList.add(map);
                } catch (Exception e) {
                    logger.error("获取循环调度失败", e);
                }
            });
        }

        return mapList;

    }


    private void checkValidFlows(Project project, Map<String, Boolean> checkMap, String flowName) {

        if (null != project) {
            List<Flow> flows = project.getFlows();
            // 取出当前项目的所有flow名称进行判断
            List<String> flowNameList = flows.stream().map(Flow::getId).collect(Collectors.toList());
            if (flowNameList.contains(flowName)) {
                checkMap.put("validFlow", true);
            } else {
                checkMap.put("validFlow", false);
            }
        }

    }

    private void alertOnCycleFlowInterrupt(List<ExecutionCycle> executionCycles) {
        CompletableFuture.runAsync(() -> {
            for (ExecutionCycle executionCycle: executionCycles) {
                if (executionCycle != null) {
                    try {
                        ExecutableFlow exFlow = this.executorManager.getExecutableFlow(executionCycle.getCurrentExecId());
                        executionCycle.setStatus(Status.KILLED);
                        executionCycle.setEndTime(System.currentTimeMillis());
                        ExecutionControllerUtils.alertOnCycleFlowInterrupt(exFlow, executionCycle, alerterHolder);
                    } catch (ExecutorManagerException e) {
                        logger.error(String.format("alter cycle flow interrupt failed: flow: %s", executionCycle.getFlowId()));
                    }
                }
            }
        });
    }

    private void ajaxExecutionCyclePage(HttpServletRequest req, HttpServletResponse resp, Session session) throws IOException {

        int pageNum = getIntParam(req, "page", 1);
        int pageSize = getIntParam(req, "size", 20);
        String searchTerm = getParam(req, "searchterm", "");
        int offset = (pageNum - 1) * pageSize;
        User user = session.getUser();
        Set<String> roles = new HashSet<>(user.getRoles());
        HashMap<String, Object> map = new HashMap<>();
        int cycleFlowsTotal = 0;
        //组装高级查询
        HashMap<String, String> queryMap = buildQueryMap(req);
        List<ExecutionCycle> cycleFlows = new ArrayList<>();
        try {

            if (roles.contains("admin")) {
                cycleFlowsTotal = executorManager.getExecutionCycleAllTotal(null, searchTerm, queryMap);
                cycleFlows = executorManager.getExecutionCycleAllPages(null, searchTerm, offset, pageSize, queryMap);

            } else {
                cycleFlowsTotal = executorManager.getExecutionCycleAllTotal(user.getUserId(), searchTerm, queryMap);
                cycleFlows = executorManager.getExecutionCycleAllPages(user.getUserId(), searchTerm, offset, pageSize, queryMap);
            }

        } catch (ExecutorManagerException e) {
            map.put("error", "Error fetch execution cycle");
        }
        map.put("total", cycleFlowsTotal);
        map.put("page", pageNum);
        map.put("pageSize", pageSize);
        map.put("executionCycleList", cycleFlows2ListMapToPages(cycleFlows));
        this.writeJSON(resp, map);

    }

    private HashMap<String, String> buildQueryMap(HttpServletRequest req) {

        HashMap<String, String> queryMap = new HashMap<>();
        String projcontain = req.getParameter("projcontain");
        String flowcontain = req.getParameter("flowcontain");
        String usercontain = req.getParameter("usercontain");
        String validFlow = req.getParameter("validFlow");
        String activeFlag = req.getParameter("activeFlag");
        queryMap.put("A.name", projcontain);
        queryMap.put("A.flow_id", flowcontain);
        queryMap.put("A.submit_user", usercontain);
        queryMap.put("A.validFlow", validFlow);
        queryMap.put("A.activeFlag", activeFlag);

        if("ALL".equalsIgnoreCase(validFlow)){
            queryMap.put("A.validFlow", "");
        }
        if("ALL".equalsIgnoreCase(activeFlag)){
            queryMap.put("A.activeFlag", "");
        }
        return queryMap;
    }

    private void ajaxDeleteExecutionCycle(HttpServletRequest req, HttpServletResponse resp, Session session) throws IOException {

        Map<String, String> ret = new HashMap<>();
        Project project = null;

        try {

            int projectId = getIntParam(req, "projectId");
            String flowId = getParam(req, "flowId");
            project = projectManager.getProject(projectId);
            boolean hasPermission = hasPermission(project, session.getUser(), Permission.Type.WRITE);
            if (!hasPermission) {
                ret.put("error", "this user has no permission");
                this.writeJSON(resp, ret);
                return;
            }
            List<ExecutionCycle> runningCycleFlows = executorManager.getRunningCycleFlows(projectId,flowId);

                if (CollectionUtils.isNotEmpty(runningCycleFlows)) {
                    ret.put("error", "当前工作流正在运行中，无法删除");
                    this.writeJSON(resp, ret);
                    return;

            }

            executorManager.deleteExecutionCycle(projectId, flowId,session.getUser(),project);

        } catch (Exception e) {
            logger.error("删除失败: project {}, Exception {}" ,project.getName(), e);
            ret.put("error", "删除失败");
        }


        this.writeJSON(resp, ret);
    }

}
