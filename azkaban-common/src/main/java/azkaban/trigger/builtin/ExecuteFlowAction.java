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

package azkaban.trigger.builtin;

import azkaban.Constants;
import azkaban.executor.ExecutableFlow;
import azkaban.executor.ExecutionOptions;
import azkaban.executor.ExecutorManagerAdapter;
import azkaban.flow.Flow;
import azkaban.flow.FlowUtils;
import azkaban.project.Project;
import azkaban.project.ProjectManager;
import azkaban.sla.SlaOption;
import azkaban.system.SystemManager;
import azkaban.system.SystemUserManagerException;
import azkaban.system.entity.WtssUser;
import azkaban.trigger.TriggerAction;
import azkaban.trigger.TriggerManager;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ExecuteFlowAction implements TriggerAction {

  public static final String TYPE = "ExecuteFlowAction";

  public static final String EXEC_ID = "ExecuteFlowAction.execid";

  private static ExecutorManagerAdapter executorManagerAdapter;
  private static TriggerManager triggerManager;
  private static ProjectManager projectManager;
  private static SystemManager systemManager;
  private static Logger logger = LoggerFactory.getLogger(ExecuteFlowAction.class);
  private final String actionId;
  private final String projectName;
  private int projectId;
  private String flowName;
  private String submitUser;
  private ExecutionOptions executionOptions = new ExecutionOptions();
  private List<SlaOption> slaOptions;
  //其他设置参数，方便后续扩展使用
  private Map<String, Object> otherOption = new HashMap<>();

  public ExecuteFlowAction(final String actionId, final int projectId, final String projectName,
      final String flowName, final String submitUser, final ExecutionOptions executionOptions,
      final List<SlaOption> slaOptions) {
    this.actionId = actionId;
    this.projectId = projectId;
    this.projectName = projectName;
    this.flowName = flowName;
    this.submitUser = submitUser;
    this.executionOptions = executionOptions;
    this.slaOptions = slaOptions;
  }

  public ExecuteFlowAction(final String actionId, final int projectId, final String projectName,
      final String flowName, final String submitUser, final ExecutionOptions executionOptions,
      final List<SlaOption> slaOptions, final Map<String, Object> otherOption) {
    this.actionId = actionId;
    this.projectId = projectId;
    this.projectName = projectName;
    this.flowName = flowName;
    this.submitUser = submitUser;
    this.executionOptions = executionOptions;
    this.slaOptions = slaOptions;
    this.otherOption = otherOption;
  }

  public static void setLogger(final Logger logger) {
    ExecuteFlowAction.logger = logger;
  }

  public static ExecutorManagerAdapter getExecutorManager() {
    return executorManagerAdapter;
  }

  public static void setExecutorManager(
      final ExecutorManagerAdapter executorManagerAdapter) {
    ExecuteFlowAction.executorManagerAdapter = executorManagerAdapter;
  }

  public static TriggerManager getTriggerManager() {
    return triggerManager;
  }

  public static void setTriggerManager(final TriggerManager triggerManager) {
    ExecuteFlowAction.triggerManager = triggerManager;
  }

  public static SystemManager getSystemManager() {
    return systemManager;
  }

  public static void setSystemManager(SystemManager systemManager) {
    ExecuteFlowAction.systemManager = systemManager;
  }

  public static ProjectManager getProjectManager() {
    return projectManager;
  }

  public static void setProjectManager(final ProjectManager projectManager) {
    ExecuteFlowAction.projectManager = projectManager;
  }

  public static TriggerAction createFromJson(final HashMap<String, Object> obj) {
    final Map<String, Object> jsonObj = (HashMap<String, Object>) obj;
    final String objType = (String) jsonObj.get("type");
    if (!objType.equals(TYPE)) {
      throw new RuntimeException("Cannot create action of " + TYPE + " from "
          + objType);
    }
    final String actionId = (String) jsonObj.get("actionId");
    final int projectId = Integer.valueOf((String) jsonObj.get("projectId"));
    final String projectName = (String) jsonObj.get("projectName");
    final String flowName = (String) jsonObj.get("flowName");
    final String submitUser = (String) jsonObj.get("submitUser");
    ExecutionOptions executionOptions = null;
    if (jsonObj.containsKey("executionOptions")) {
      executionOptions = ExecutionOptions.createFromObject(jsonObj.get("executionOptions"));
    }
    List<SlaOption> slaOptions = null;
    if (jsonObj.containsKey("slaOptions")) {
      slaOptions = new ArrayList<>();
      final List<Object> slaOptionsObj = (List<Object>) jsonObj.get("slaOptions");
      for (final Object slaObj : slaOptionsObj) {
        slaOptions.add(SlaOption.fromObject(slaObj));
      }
    }
    //设置其他数据参数
    Map<String, Object> otherOptionsMap = new HashMap<>();
    if(jsonObj.containsKey("otherOptions")){
      otherOptionsMap = (Map<String, Object>) jsonObj.get("otherOptions");
    }
    otherOptionsMap
        .put(Constants.EXECUTE_FLOW_TRIGGER_ID, jsonObj.get(Constants.EXECUTE_FLOW_TRIGGER_ID));
    return new ExecuteFlowAction(actionId, projectId, projectName, flowName,
        submitUser, executionOptions, slaOptions, otherOptionsMap);
  }

  public String getProjectName() {
    return this.projectName;
  }

  public int getProjectId() {
    return this.projectId;
  }

  protected void setProjectId(final int projectId) {
    this.projectId = projectId;
  }

  public String getFlowName() {
    return this.flowName;
  }

  public void setFlowName(final String flowName) {
    this.flowName = flowName;
  }

  public String getSubmitUser() {
    return this.submitUser;
  }

  protected void setSubmitUser(final String submitUser) {
    this.submitUser = submitUser;
  }

  public ExecutionOptions getExecutionOptions() {
    return this.executionOptions;
  }

  protected void setExecutionOptions(final ExecutionOptions executionOptions) {
    this.executionOptions = executionOptions;
  }

  public List<SlaOption> getSlaOptions() {
    return this.slaOptions;
  }

  protected void setSlaOptions(final List<SlaOption> slaOptions) {
    this.slaOptions = slaOptions;
  }

  public Map<String, Object> getOtherOption() {
    return otherOption;
  }

  public void setOtherOption(Map<String, Object> otherOption) {
    this.otherOption = otherOption;
  }

  @Override
  public String getType() {
    return TYPE;
  }

  @Override
  public TriggerAction fromJson(final Object obj) {
    return createFromJson((HashMap<String, Object>) obj);
  }

  @Override
  public Object toJson() {
    final Map<String, Object> jsonObj = new HashMap<>();
    jsonObj.put("actionId", this.actionId);
    jsonObj.put("type", TYPE);
    jsonObj.put("projectId", String.valueOf(this.projectId));
    jsonObj.put("projectName", this.projectName);
    jsonObj.put("flowName", this.flowName);
    jsonObj.put("submitUser", this.submitUser);
    if (this.executionOptions != null) {
      jsonObj.put("executionOptions", this.executionOptions.toObject());
    }
    if (this.slaOptions != null) {
      final List<Object> slaOptionsObj = new ArrayList<>();
      for (final SlaOption sla : this.slaOptions) {
        slaOptionsObj.add(sla.toObject());
      }
      jsonObj.put("slaOptions", slaOptionsObj);
    }

    if(this.otherOption != null){
      jsonObj.put("otherOptions", this.otherOption);
    }

    return jsonObj;
  }

  @Override
  public void doAction() throws Exception {
    if (projectManager == null || executorManagerAdapter == null) {
      throw new Exception("ExecuteFlowAction not properly initialized!");
    }

    final Project project = FlowUtils.getProject(projectManager, this.projectId);
    final Flow flow = FlowUtils.getFlow(project, this.flowName);

    final ExecutableFlow exflow = FlowUtils.createExecutableFlow(project, flow);

    exflow.setSubmitUser(this.submitUser);
	
	// FIXME Add proxy users to submit users to solve the problem that proxy users of scheduled tasks do not take effect.
    exflow.addAllProxyUsers(project.getProxyUsers());
    WtssUser wtssUser = null;
    try {
      wtssUser = systemManager.getSystemUserByUserName(this.submitUser);
    } catch (SystemUserManagerException e){
      logger.error("get wtssUser failed, ", e);
    }
    if(wtssUser != null && wtssUser.getProxyUsers() != null) {
      String[] proxySplit = wtssUser.getProxyUsers().split("\\s*,\\s*");
      logger.info("add proxyUser.");
      exflow.addAllProxyUsers(Arrays.asList(proxySplit));
    }

    if (this.executionOptions == null) {
      this.executionOptions = new ExecutionOptions();
    }
    if (!this.executionOptions.isFailureEmailsOverridden()) {
      this.executionOptions.setLastFailureEmails(this.executionOptions.getFailureEmails());
      this.executionOptions.setFailureEmails(flow.getFailureEmails());
    }
    if (!this.executionOptions.isSuccessEmailsOverridden()) {
      this.executionOptions.setLastSuccessEmails(this.executionOptions.getSuccessEmails());
      this.executionOptions.setSuccessEmails(flow.getSuccessEmails());
    }
    exflow.setExecutionOptions(this.executionOptions);

    if (this.slaOptions != null && this.slaOptions.size() > 0) {
      exflow.setSlaOptions(this.slaOptions);
    }
    // FIXME Set the task to a scheduled task type.
    exflow.setFlowType(3);
    // FIXME Added new parameters for job stream, used to rerun job stream failure, and skip all tasks that failed execution.
    if(null != this.otherOption && this.otherOption.size() > 0){
      // 设置了flow的失败重跑
      if(this.otherOption.get("flowFailedRetryOption") != null){
        exflow.setFlowFailedRetry((Map<String, String>)this.otherOption.get("flowFailedRetryOption"));
      }
      //是否跳过所有失败job
      exflow.setFailedSkipedAllJobs((Boolean) this.otherOption.getOrDefault("flowFailedSkiped", false));
      exflow.setOtherOption(this.otherOption);
    }

    logger.info("Invoking flow " + project.getName() + "." + this.flowName);
    Map<String, Object> result = new HashMap<>(4);
    try {
      executorManagerAdapter.submitExecutableFlow(exflow, this.submitUser, result);
      Integer code = (Integer) result.get("code");
      if (code == null || code != 200) {
        this.getOtherOption().put(Constants.SCHEDULE_MISSED_TIME, System.currentTimeMillis());
      }
    } catch (Exception e) {
      this.getOtherOption().put(Constants.SCHEDULE_MISSED_TIME, System.currentTimeMillis());
      throw e;
    }

    logger.info("Invoked flow " + project.getName() + "." + this.flowName);
  }

  @Override
  public String getDescription() {
    return "Execute flow " + getFlowName() + " from project "
        + getProjectName();
  }

  @Override
  public void setContext(final Map<String, Object> context) {
  }

  @Override
  public String getId() {
    return this.actionId;
  }

}
