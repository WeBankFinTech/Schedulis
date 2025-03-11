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
import azkaban.project.ProjectLoader;
import azkaban.project.ProjectManager;
import azkaban.project.ProjectManagerException;
import azkaban.sla.SlaOption;
import azkaban.utils.Props;
import azkaban.utils.StringUtils;
import com.google.gson.Gson;

import java.io.File;
import java.util.*;
import java.util.stream.Collectors;

import org.joda.time.DateTime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class FlowUtils {

  private static final Logger logger = LoggerFactory.getLogger(FlowUtils.class);

  public static Props addCommonFlowProperties(final Props parentProps,
                                              final ExecutableFlow flow) {
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

    DateTime loadTime;
    if (flow.getLastParameterTime() != -1) {
      loadTime = new DateTime(flow.getLastParameterTime());
    } else {
      loadTime = new DateTime();
    }
    //保存上次执行时间内置参数时间戳
    flow.setLastParameterTime(loadTime.getMillis());
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
        } else {
          for (ExecutableNode executableNode : exflow.getExecutableNodes()) {
            if (executableNode != null && executableNode instanceof ExecutableFlowBase) {
              ArrayList<Object> disabledJob = new ArrayList<>();
              disabledJob.add(disabled);
              applyDisabledJobs(disabledJob, (ExecutableFlowBase) executableNode);
            }
          }
        }
        //记录上次失败跳过，用于前端颜色展示
        if (exflow.getFailSkipList() != null && exflow.getFailSkipList().contains(nodeName)) {
          node.setLastStatus("FAILED_SKIPPED");
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

  public static void applyEnabledJobs(final List<Object> enabledJobs,
                                      final ExecutableFlowBase exflow) {

    List<ExecutableNode> nodeList = exflow.getExecutableNodes();
    for (final Object enabled : enabledJobs) {
      if (enabled instanceof String) {
        final String nodeName = (String) enabled;
        final ExecutableNode node = exflow.getExecutableNode(nodeName);
        for (ExecutableNode exeNode : nodeList) {
          exeNode.setStatus(Status.DISABLED);
        }
        if (node != null) {
          node.setStatus(Status.READY);
          nodeList.remove(node);
        }
        //记录上次失败跳过，用于前端颜色展示
        if (exflow.getFailSkipList() != null && exflow.getFailSkipList().contains(nodeName)) {
          node.setLastStatus("FAILED_SKIPPED");
          nodeList.remove(node);
        }
      } else if (enabled instanceof Map) {
        logger.info("job is not String:{}",enabled);
        final Map<String, Object> nestedEnabled = (Map<String, Object>) enabled;
        final String nodeName = (String) nestedEnabled.get("id");
        final List<Object> subEnabledJobs =
                (List<Object>) nestedEnabled.get("children");

        if (nodeName == null || subEnabledJobs == null) {
          return;
        }
        final ExecutableNode node = exflow.getExecutableNode(nodeName);
        node.setStatus(Status.READY);
        nodeList.remove(node);
        if (node != null && node instanceof ExecutableFlowBase) {
          node.setStatus(Status.READY);
          nodeList.remove(node);//重点，当前节点设置成ready之后，一定要移除该集合，不然很多问题
          applyEnabledJobs(subEnabledJobs, (ExecutableFlowBase) node);
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
      throw new NoSuchResourceException("Error finding the project to execute "
              + projectId);
    }
    return project;
  }

  public static Flow getFlow(final Project project, final String flowName) {
    final Project nonNullProj = requireNonNull(project);
    final Flow flow = nonNullProj.getFlow(flowName);
    if (flow == null) {
      throw new NoSuchResourceException("Error finding the flow to execute " + flowName);
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

  public static void getAllFailedNodeList(ExecutableFlowBase node, List<ExecutableNode> executableNodeList) {
    for (ExecutableNode execNode : node.getExecutableNodes()) {
      if (execNode instanceof ExecutableFlowBase) {
        getAllFailedNodeList((ExecutableFlowBase) execNode, executableNodeList);
      } else if (execNode instanceof ExecutableNode) {
        if (Status.isFailed(execNode.getStatus())) {
          executableNodeList.add(execNode);
        }
      }
    }
    return;
  }

  public static List<String> getAllFailedNodeNestIdSortByEndTime(List<ExecutableNode> nodeList) {
    return nodeList.stream().sorted(new Comparator<ExecutableNode>() {
      @Override
      public int compare(ExecutableNode o1, ExecutableNode o2) {
        if (o1.getEndTime() < o2.getEndTime()) {
          return -1;
        } else if (o1.getEndTime() == o2.getEndTime()) {
          return 0;
        }
        return 1;
      }
    }).map(x -> x.getNestedId()).collect(Collectors.toList());
  }

  public static List<String> getThreeFailedNodeNestId(List<String> list) {
    if (list.size() > 3) {
      return list.subList(0, 3);
    } else {
      return list;
    }
  }

  public static void getAllElasticFlow(ExecutableNode node, List<ExecutableFlowBase> elasticFlowList) {
    if (node instanceof ExecutableFlowBase) {
      if (node.isElasticNode()) {
        elasticFlowList.add((ExecutableFlowBase) node);
      }
      for (ExecutableNode executableNode : ((ExecutableFlowBase) node).getExecutableNodes()) {
        if (executableNode instanceof ExecutableFlowBase) {
          getAllElasticFlow(executableNode, elasticFlowList);
        }
      }
    }
  }

  public static void resetExecutableNode(ExecutableNode node) {
    node.setStatus(Status.READY);
    node.setStartTime(-1);
    node.setEndTime(-1);
    node.setUpdateTime(-1);
    if (node instanceof ExecutableFlowBase) {
      for (ExecutableNode tmpNode : ((ExecutableFlowBase) node).getExecutableNodes()) {
        resetExecutableNode(tmpNode);
      }
    }
  }

  public static void findAndReplace(ExecutableFlowBase lastExecutableFlow, ExecutableFlowBase currentFlow, List<ExecutableFlowBase> elasticFlowList) {
    // 使两个flow处于同一层级
    if (lastExecutableFlow.getFlowId().equals(currentFlow.getFlowId())) {
      lastExecutableFlow.setParentFlow(null);
      for (ExecutableFlowBase flowBase : elasticFlowList) {
        ExecutableNode executableNode = lastExecutableFlow.getExecutableNodePath(flowBase.getNestedId());
        if (executableNode != null) {
          resetExecutableNode(executableNode);
          flowBase.getParentFlow().addExecutableNode(executableNode);
        }
      }
    } else {
      for (ExecutableNode node : lastExecutableFlow.getExecutableNodes()) {
        if (node instanceof ExecutableFlowBase) {
          findAndReplace((ExecutableFlowBase) node, currentFlow, elasticFlowList);
        }
      }
    }
  }

  /**
   * 跟上一次执行的flow对比，把上一次的dag结构复制到当前执行的flow中
   *
   * @param currentFlow
   * @param lastExecutableFlow
   */
  public static void compareAndCopyFlow(ExecutableFlowBase currentFlow, ExecutableFlowBase lastExecutableFlow) {
    if (lastExecutableFlow.getFlowId().equals(currentFlow.getFlowId())) {
      for (ExecutableNode executableNode : lastExecutableFlow.getExecutableNodes()) {
        resetExecutableNode(executableNode);
        currentFlow.addExecutableNode(executableNode);
      }
    } else {
      for (ExecutableNode node : lastExecutableFlow.getExecutableNodes()) {
        if (node instanceof ExecutableFlowBase) {
          compareAndCopyFlow(currentFlow, (ExecutableFlowBase) node);
        }
      }
    }
  }

  /**
   * 跟上一次执行的flow对比，把上一次的dag结构复制到当前执行的flow中
   *
   * @param currentFlow
   * @param lastExecutableFlow
   */
  public static void compareAndCopyFlowWithSkip(ExecutableFlowBase currentFlow, ExecutableFlowBase lastExecutableFlow, boolean useNewProperties) {
    if (lastExecutableFlow.getFlowId().equals(currentFlow.getFlowId())) {
      for (ExecutableNode executableNode : lastExecutableFlow.getExecutableNodes()) {
        //覆盖前保存失败跳过节点，用于前端颜色展示
        if (Status.FAILED_SKIPPED.equals(executableNode.getStatus()) || "FAILED_SKIPPED".equals(executableNode.getLastStatus())) {
          currentFlow.addFailSkipList(executableNode.getId());
        }
        resetExecutableNode(executableNode);
        //不覆盖job目录
        ExecutableNode currentNode = currentFlow.getExecutableNode(executableNode.getId());
        if (currentNode != null && org.apache.commons.lang.StringUtils.isNotEmpty(currentNode.getJobSource())) {
          executableNode.setJobSource(currentNode.getJobSource());
        }
        if (useNewProperties && currentNode != null) {
          executableNode.setPropsSource(currentNode.getPropsSource());
        }
        currentFlow.addExecutableNode(executableNode);
      }
    } else {
      for (ExecutableNode node : lastExecutableFlow.getExecutableNodes()) {
        if (node instanceof ExecutableFlowBase) {
          compareAndCopyFlowWithSkip(currentFlow, (ExecutableFlowBase) node, useNewProperties);
        }
      }
    }
  }


  public static void cloneElasticNodeFailedRetry(String templateNodeId, String elasticNodeId, ExecutableFlow flow) {
    if (null != flow.getOtherOption().get("jobFailedRetryOptions")) {
      List<Map<String, String>> jobFailedRetryOptions = (List<Map<String, String>>) flow.getOtherOption().get("jobFailedRetryOptions");
      for (Map<String, String> map : jobFailedRetryOptions) {
        if (templateNodeId.equals(map.get("jobName"))) {
          Map<String, String> failedRetryInfo = new HashMap<>(3);
          failedRetryInfo.put("jobName", elasticNodeId);
          failedRetryInfo.put("interval", map.get("interval"));
          failedRetryInfo.put("count", map.get("count"));
          jobFailedRetryOptions.add(failedRetryInfo);
          return;
        }
      }
    }
  }

  public static void cloneElasticNodeFailedSkipped(String templateNodeId, String elasticNodeId, ExecutableFlow flow) {
    List<String> failedSkipped = (ArrayList) flow.getOtherOption().get("jobSkipFailedOptions");
    if (failedSkipped != null && failedSkipped.contains(templateNodeId)) {
      failedSkipped.add(elasticNodeId);
    }
  }

  public static void getAllExecutableNodeId(ExecutableFlowBase executableFlowBase,
                                            List<String> nodeIdList, String action) {
    for (ExecutableNode node : executableFlowBase.getExecutableNodes()) {
      if (node instanceof ExecutableFlowBase) {
        if ("skipFailedJob".equals(action)) {
          nodeIdList.add("subflow:" + ((ExecutableFlowBase) node).getFlowId());
        }
        getAllExecutableNodeId((ExecutableFlowBase) node, nodeIdList, action);
      } else {
        nodeIdList.add(node.getId());
      }
    }
  }

  public static void cloneElasticNodeSkippedDay(String templateNestedId, String elasticNestedId, ExecutableFlow flow) {
    Map<String, String> data = (Map<String, String>) flow.getOtherOption().get("job.cron.expression");
    if (data != null && data.containsKey(templateNestedId)) {
      String cron = data.get(templateNestedId);
      if (cron != null) {
        data.put(elasticNestedId, cron);
      }
    }
  }

  public static void loadAllProjectFlows(final Project project, ProjectLoader projectLoader) {
    try {
      final List<Flow> flows = projectLoader.fetchAllProjectFlows(project);
      final Map<String, Flow> flowMap = new HashMap<>();
      for (final Flow flow : flows) {
        flowMap.put(flow.getId(), flow);
      }

      project.setFlows(flowMap);
    } catch (final ProjectManagerException e) {
      logger.error("load project flows failed", e);
    }
  }

  public static void copyProperties(ExecutableNode src, ExecutableNode elasticNode, String param, int index, ExecutableFlow flow, ExecutableNode endNode) {
    String elasticNodeId = String.format("%s_%s_%d", src.getId(), param, index);
    elasticNode.setId(elasticNodeId);
    elasticNode.setOuter(src.getOuter());
    elasticNode.setCondition(src.getCondition());
    elasticNode.setConditionOnJobStatus(src.getConditionOnJobStatus());
    elasticNode.setStatus(src.getStatus());
    elasticNode.setElasticNode(src.isElasticNode());
    if (elasticNode.isElasticNode()) {
      elasticNode.setElasticParamIndex(index);
    }
    Set<String> outNodeIds = elasticNode.getOutNodes();
    // out node 是end 节点不处理
    if (!(outNodeIds != null && outNodeIds.size() == 1 && outNodeIds.contains(endNode.getId()))) {
      Set<String> outNodes = elasticNode.getOutNodes().stream().map(id -> String.format("%s_%s_%d", id, param, index)).collect(Collectors.toCollection(HashSet::new));
      elasticNode.setOutNodes(outNodes);
    }
    Set<String> inNodes = elasticNode.getInNodes().stream().map(id -> String.format("%s_%s_%d", id, param, index)).collect(Collectors.toCollection(HashSet::new));
    elasticNode.setInNodes(inNodes);
    elasticNode.setSourceNodeId(src.getId());
    FlowUtils.cloneElasticNodeFailedRetry(src.getId(), elasticNodeId, flow);
    FlowUtils.cloneElasticNodeFailedSkipped(src.getId(), elasticNodeId, flow);
    FlowUtils.cloneElasticNodeSkippedDay(src.getNestedId(), elasticNode.getNestedId(), flow);
    SlaOption.cloneSlaAlert(flow.getSlaOptions(), src.getNestedId(), elasticNode.getNestedId());
    SlaOption.cloneSlaTimeoutAlert(flow.getSlaOptions(), src.getNestedId(), elasticNode.getNestedId());
  }

  public static void copyExecutableNodesProperties(ExecutableFlowBase srcBaseFlow, ExecutableFlowBase destBaseFlow, int index, ExecutableFlow flow, String param, ExecutableNode endNode) {
    for (int i = 0; i < srcBaseFlow.getExecutableNodes().size(); i++) {
      ExecutableNode src = srcBaseFlow.getExecutableNodes().get(i);
      ExecutableNode dest = destBaseFlow.getExecutableNodes().get(i);
      if (src instanceof ExecutableFlowBase) {
        String flowId = String.format("%s_%s_%d", ((ExecutableFlowBase) src).getFlowId(), param, index);
        ((ExecutableFlowBase) dest).setFlowId(flowId);
        copyProperties(src, dest, param, index, flow, endNode);
        copyExecutableNodesProperties(((ExecutableFlowBase) src), ((ExecutableFlowBase) dest), index, flow, param, endNode);
      } else {
        copyProperties(src, dest, param, index, flow, endNode);
      }
    }
  }


  /**
   * 遍历elasticflow里所有节点，获取elastic.param.xxx, 并且设置该节点是Elastic节点
   *
   * @param nodes
   * @param execDir
   * @return
   */
  public static String getElasticParamKey(List<ExecutableNode> nodes, File execDir) {
    String elasticParamKey = null;
    for (ExecutableNode node : nodes) {
      if (!(node instanceof ExecutableFlowBase)) {
        String tmpKey = StringUtils.getElasticParamKey(new File(execDir, node.getJobSource()));
        if (tmpKey != null) {
          node.setElasticNode(true);
          node.setElasticParamIndex(0);
          elasticParamKey = tmpKey;
        }
      }
    }
    return elasticParamKey;
  }

  /**
   * 更新 baseflow ExecutableNodes 信息
   *
   * @param flow
   */
  public static void updateExecutableFlow(ExecutableFlowBase flow) {
    HashMap<String, ExecutableNode> map = new HashMap<>();
    for (ExecutableNode executableNode : flow.getExecutableNodeMap().values()) {
      map.put(executableNode.getId(), executableNode);
      if (executableNode instanceof ExecutableFlowBase) {
        updateExecutableFlow((ExecutableFlowBase) executableNode);
      }
    }
    flow.setExecutableNodeMap(map);

  }

  public static void main(String[] args) {
    List<String> list = new ArrayList<>();
    list.add("1");
    list.add("2");
    list.add("3");
    list.remove(list.indexOf("2"));
    System.out.println(list);
  }

}
