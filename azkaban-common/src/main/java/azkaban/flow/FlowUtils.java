/*
 * Copyright 2017 LinkedIn Corp.
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

package azkaban.flow;

import static java.util.Objects.requireNonNull;

import azkaban.executor.ExecutableFlow;
import azkaban.executor.ExecutableFlowBase;
import azkaban.executor.ExecutableNode;
import azkaban.executor.Status;
import azkaban.project.Project;
import azkaban.project.ProjectManager;
import azkaban.utils.Props;
import com.google.gson.Gson;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import org.slf4j.LoggerFactory;
import org.slf4j.Logger;
import org.joda.time.DateTime;

public class FlowUtils {

  private static final Logger logger = LoggerFactory.getLogger(FlowUtils.class);

  public static Props addCommonFlowProperties(final Props parentProps,
      final ExecutableFlowBase flow) {
    final Props props = new Props(parentProps);

    props.put(CommonJobProperties.FLOW_ID, flow.getFlowId());
    props.put(CommonJobProperties.EXEC_ID, flow.getExecutionId());
    props.put(CommonJobProperties.PROJECT_ID, flow.getProjectId());
    props.put(CommonJobProperties.PROJECT_NAME, flow.getProjectName());
    props.put(CommonJobProperties.PROJECT_VERSION, flow.getVersion());
    props.put(CommonJobProperties.FLOW_UUID, UUID.randomUUID().toString());
    props.put(CommonJobProperties.PROJECT_LAST_CHANGED_BY, flow.getLastModifiedByUser());
    props.put(CommonJobProperties.PROJECT_LAST_CHANGED_DATE, flow.getLastModifiedTimestamp());
    props.put(CommonJobProperties.SUBMIT_USER, flow.getExecutableFlow().getSubmitUser());

    final DateTime loadTime = new DateTime();

    props.put(CommonJobProperties.FLOW_START_TIMESTAMP, loadTime.toString());
    props.put(CommonJobProperties.FLOW_START_YEAR, loadTime.toString("yyyy"));
    props.put(CommonJobProperties.FLOW_START_MONTH, loadTime.toString("MM"));
    props.put(CommonJobProperties.FLOW_START_DAY, loadTime.toString("dd"));
    props.put(CommonJobProperties.FLOW_START_HOUR, loadTime.toString("HH"));
    props.put(CommonJobProperties.FLOW_START_MINUTE, loadTime.toString("mm"));
    props.put(CommonJobProperties.FLOW_START_SECOND, loadTime.toString("ss"));
    props.put(CommonJobProperties.FLOW_START_MILLISSECOND,
        loadTime.toString("SSS"));
    props.put(CommonJobProperties.FLOW_START_TIMEZONE,
        loadTime.toString("ZZZZ"));

    return props;
  }

  /**
   * Change job status to disabled in exflow if the job is in disabledJobs
   */
  public static void applyDisabledJobs(final List<Object> disabledJobs,
      final ExecutableFlowBase exflow) {
    for (final Object disabled : disabledJobs) {
      if (disabled instanceof String) {
        final String nodeName = (String) disabled;
        final ExecutableNode node = exflow.getExecutableNode(nodeName);
        if (node != null) {
          node.setStatus(Status.DISABLED);
        }
      } else if (disabled instanceof Map) {
        final Map<String, Object> nestedDisabled = (Map<String, Object>) disabled;
        final String nodeName = (String) nestedDisabled.get("id");
        final List<Object> subDisabledJobs =
            (List<Object>) nestedDisabled.get("children");

        if (nodeName == null || subDisabledJobs == null) {
          return;
        }

        final ExecutableNode node = exflow.getExecutableNode(nodeName);
        if (node != null && node instanceof ExecutableFlowBase) {
          applyDisabledJobs(subDisabledJobs, (ExecutableFlowBase) node);
        }
      }
    }
  }

  public static void resetJobDisabled(final List<Object> disabledJobs,
                                       final ExecutableFlowBase exflow) {
    for (final Object disabled : disabledJobs) {
      if (disabled instanceof String) {
        final String nodeName = (String) disabled;
        final ExecutableNode node = exflow.getExecutableNode(nodeName);
        if (node != null && node.getStatus().equals(Status.CANCELLED)) {
          logger.info("reset job {} status {} to DISABLED.", node.getNestedId(), node.getStatus());
          node.setStatus(Status.DISABLED);
          node.setEndTime(-1);
          node.setStartTime(-1);
          node.setUpdateTime(System.currentTimeMillis());
        }
      } else if (disabled instanceof Map) {
        final Map<String, Object> nestedDisabled = (Map<String, Object>) disabled;
        final String nodeName = (String) nestedDisabled.get("id");
        final List<Object> subDisabledJobs =
            (List<Object>) nestedDisabled.get("children");

        if (nodeName == null || subDisabledJobs == null) {
          return;
        }

        final ExecutableNode node = exflow.getExecutableNode(nodeName);
        if (node != null && node instanceof ExecutableFlowBase) {
          resetJobDisabled(subDisabledJobs, (ExecutableFlowBase) node);
        }
      }
    }
  }

  public static Project getProject(final ProjectManager projectManager, final int projectId) {
    final Project project = projectManager.getProject(projectId);
    if (project == null) {
      throw new RuntimeException("Error finding the project to execute "
          + projectId);
    }
    return project;
  }

  public static Flow getFlow(final Project project, final String flowName) {
    final Project nonNullProj = requireNonNull(project);
    final Flow flow = nonNullProj.getFlow(flowName);
    if (flow == null) {
      throw new RuntimeException("Error finding the flow to execute " + flowName);
    }
    return flow;
  }

  public static ExecutableFlow createExecutableFlow(final Project project, final Flow flow) {
    final ExecutableFlow exflow = new ExecutableFlow(project, flow);
    exflow.addAllProxyUsers(project.getProxyUsers());
    return exflow;
  }

  public static String toJson(final Project proj) {
    final Gson gson = new Gson();
    final String jsonStr = gson.toJson(proj);
    return jsonStr;
  }

  public static Project toProject(final String json) {
    final Gson gson = new Gson();
    return gson.fromJson(json, Project.class);
  }

  public static Props addRepeatCommonFlowProperties(final Props parentProps,
      final long repeatTime,
      final ExecutableFlowBase flow) {
    final Props props = new Props(parentProps);

    props.put(CommonJobProperties.FLOW_ID, flow.getFlowId());
    props.put(CommonJobProperties.EXEC_ID, flow.getExecutionId());
    props.put(CommonJobProperties.PROJECT_ID, flow.getProjectId());
    props.put(CommonJobProperties.PROJECT_NAME, flow.getProjectName());
    props.put(CommonJobProperties.PROJECT_VERSION, flow.getVersion());
    props.put(CommonJobProperties.FLOW_UUID, UUID.randomUUID().toString());
    props.put(CommonJobProperties.PROJECT_LAST_CHANGED_BY, flow.getLastModifiedByUser());
    props.put(CommonJobProperties.PROJECT_LAST_CHANGED_DATE, flow.getLastModifiedTimestamp());
    props.put(CommonJobProperties.SUBMIT_USER, flow.getExecutableFlow().getSubmitUser());

    final DateTime loadTime = new DateTime(repeatTime);

    props.put(CommonJobProperties.FLOW_START_TIMESTAMP, loadTime.toString());
    props.put(CommonJobProperties.FLOW_START_YEAR, loadTime.toString("yyyy"));
    props.put(CommonJobProperties.FLOW_START_MONTH, loadTime.toString("MM"));
    props.put(CommonJobProperties.FLOW_START_DAY, loadTime.toString("dd"));
    props.put(CommonJobProperties.FLOW_START_HOUR, loadTime.toString("HH"));
    props.put(CommonJobProperties.FLOW_START_MINUTE, loadTime.toString("mm"));
    props.put(CommonJobProperties.FLOW_START_SECOND, loadTime.toString("ss"));
    props.put(CommonJobProperties.FLOW_START_MILLISSECOND,
        loadTime.toString("SSS"));
    props.put(CommonJobProperties.FLOW_START_TIMEZONE,
        loadTime.toString("ZZZZ"));

    return props;
  }

  public static void getAllFailedNodeList(ExecutableFlowBase node, List<ExecutableNode> executableNodeList){
    for(ExecutableNode execNode: node.getExecutableNodes()){
      if(execNode instanceof ExecutableFlowBase){
        getAllFailedNodeList((ExecutableFlowBase) execNode, executableNodeList);
      } else if(execNode instanceof ExecutableNode) {
        if(Status.isFailed(execNode.getStatus())){
          executableNodeList.add(execNode);
        }
      }
    }
    return;
  }

  public static List<String> getAllFailedNodeNestIdSortByEndTime(List<ExecutableNode> nodeList){
    return nodeList.stream().sorted(new Comparator<ExecutableNode>() {
      @Override
      public int compare(ExecutableNode o1, ExecutableNode o2) {
        if(o1.getEndTime() < o2.getEndTime()){
          return -1;
        } else if(o1.getEndTime() == o2.getEndTime()){
          return 0;
        }
        return 1;
      }
    }).map(x -> x.getNestedId()).collect(Collectors.toList());
  }

  public static List<String> getThreeFailedNodeNestId(List<String> list){
    if(list.size() > 3){
      return list.subList(0, 3);
    } else {
      return list;
    }
  }

  /**
   * 跟上一次执行的flow对比，把上一次的dag结构复制到当前执行的flow中
   * @param currentFlow
   * @param lastExecutableFlow
   */
  public static void compareAndCopyFlow(ExecutableFlowBase currentFlow, ExecutableFlowBase lastExecutableFlow){
    if(lastExecutableFlow.getFlowId().equals(currentFlow.getFlowId())) {
      for(ExecutableNode executableNode: lastExecutableFlow.getExecutableNodes()){
        resetExecutableNode(executableNode);
        currentFlow.addExecutableNode(executableNode);
      }
    } else {
      for(ExecutableNode node: lastExecutableFlow.getExecutableNodes()){
        if(node instanceof ExecutableFlowBase){
          compareAndCopyFlow(currentFlow, (ExecutableFlowBase) node);
        }
      }
    }
  }

  public static void resetExecutableNode(ExecutableNode node){
    node.setStatus(Status.READY);
    node.setStartTime(-1);
    node.setEndTime(-1);
    node.setUpdateTime(-1);
    if(node instanceof ExecutableFlowBase){
      for(ExecutableNode tmpNode: ((ExecutableFlowBase) node).getExecutableNodes()){
        resetExecutableNode(tmpNode);
      }
    }
  }

}
