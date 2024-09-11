package azkaban.webapp.servlet;

import azkaban.executor.*;
import azkaban.project.Project;
import azkaban.project.ProjectManager;
import azkaban.server.session.Session;
import azkaban.system.SystemManager;
import azkaban.system.common.TransitionService;
import azkaban.user.User;
import azkaban.webapp.AzkabanWebServer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.CompletableFuture;

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

    @Override
    public void init(ServletConfig config) throws ServletException {
        super.init(config);
        final AzkabanWebServer server = (AzkabanWebServer) getApplication();
        this.executorManager = server.getExecutorManager();
        this.projectManager = server.getProjectManager();
        this.transitionService = server.getTransitionService();
        this.systemManager = transitionService.getSystemManager();
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
    protected void handlePost(HttpServletRequest req, HttpServletResponse resp, Session session) throws ServletException {

    }

    private void handleAJAXAction(HttpServletRequest req, HttpServletResponse resp, Session session)
            throws ServletException, IOException {
        String ajaxName = getParam(req, "ajax");
        if ("fetchCycleFlows".equals(ajaxName)) {
            ajaxFetchExecutionCycle(req, resp, session);
        } else if ("stopAllCycleFlows".equals(ajaxName)) {
            ajaxStopAllCycleFlows(resp, session);
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

    private void ajaxExecutionCyclePage(HttpServletRequest req, HttpServletResponse resp, Session session) {

    }

}
