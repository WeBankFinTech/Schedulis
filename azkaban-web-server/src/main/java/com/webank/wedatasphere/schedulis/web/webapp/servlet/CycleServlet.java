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

package com.webank.wedatasphere.schedulis.web.webapp.servlet;

import static java.util.stream.Collectors.joining;
import static java.util.stream.Collectors.toList;

import azkaban.executor.AlerterHolder;
import azkaban.executor.ExecutableFlow;
import azkaban.executor.ExecutionControllerUtils;
import azkaban.executor.ExecutorManagerAdapter;
import azkaban.executor.ExecutorManagerException;
import azkaban.executor.Status;
import azkaban.project.Project;
import azkaban.project.ProjectManager;
import azkaban.server.session.Session;
import azkaban.user.User;
import azkaban.webapp.AzkabanWebServer;
import azkaban.webapp.servlet.LoginAbstractAzkabanServlet;
import com.webank.wedatasphere.schedulis.common.executor.ExecutionCycle;
import com.webank.wedatasphere.schedulis.common.system.SystemManager;
import com.webank.wedatasphere.schedulis.common.system.common.TransitionService;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class CycleServlet extends LoginAbstractAzkabanServlet {

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
        if (ajaxName.equals("fetchCycleFlows")) {
            ajaxFetchExecutionCycle(req, resp, session);
        } else if (ajaxName.equals("stopAllCycleFlows")) {
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
                List<Integer> maintainedProjectIds = systemManager.getMaintainedProjects(user);
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
