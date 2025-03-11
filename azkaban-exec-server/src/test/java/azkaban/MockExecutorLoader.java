/*
 * Copyright 2014 LinkedIn Corp.
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

package azkaban;

import azkaban.batch.HoldBatchAlert;
import azkaban.batch.HoldBatchOperate;
import azkaban.executor.*;
import azkaban.executor.ExecutorLogEvent.EventType;
import azkaban.executor.entity.JobPredictionExecutionInfo;
import azkaban.flow.Flow;
import azkaban.history.ExecutionRecover;
import azkaban.history.RecoverTrigger;
import azkaban.jobhook.JobHook;
import azkaban.log.LogFilterEntity;
import azkaban.project.Project;
import azkaban.sla.AlertMessageTime;
import azkaban.system.entity.WtssUser;
import azkaban.utils.FileIOUtils.LogData;
import azkaban.utils.Pair;
import azkaban.utils.Props;
import java.io.File;
import java.io.IOException;
import java.sql.SQLException;
import java.time.Duration;
import java.util.*;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import org.apache.commons.io.FileUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Used in unit tests to mock the "DB layer" (the real implementation is JdbcExecutorLoader).
 * Captures status updates of jobs and flows (in memory) so that they can be checked in tests.
 */
public class MockExecutorLoader implements ExecutorLoader {

  private static final Logger logger = LoggerFactory.getLogger(MockExecutorLoader.class);

  Map<Integer, Integer> executionExecutorMapping = new ConcurrentHashMap<>();
  Map<Integer, ExecutableFlow> flows = new ConcurrentHashMap<>();
  Map<String, ExecutableNode> nodes = new ConcurrentHashMap<>();
  Map<Integer, ExecutionReference> refs = new ConcurrentHashMap<>();
  int flowUpdateCount = 0;
  Map<String, Integer> jobUpdateCount = new ConcurrentHashMap<>();
  Map<Integer, Pair<ExecutionReference, ExecutableFlow>> activeFlows = new ConcurrentHashMap<>();
  List<Executor> executors = new ArrayList<>();
  int executorIdCounter = 0;
  Map<Integer, ArrayList<ExecutorLogEvent>> executorEvents = new ConcurrentHashMap<>();

  @Override
  public void uploadExecutableFlow(final ExecutableFlow flow)
          throws ExecutorManagerException {
    // Clone the flow node to mimick how it would be saved in DB.
    // If we would keep a handle to the original flow node, we would also see any changes made after
    // this method was called. We must only store a snapshot of the current state.
    // Also to avoid modifying statuses of the original job nodes in this.updateExecutableFlow()
    final ExecutableFlow exFlow = ExecutableFlow.createExecutableFlowFromObject(flow.toObject());
    this.flows.put(flow.getExecutionId(), exFlow);
    this.flowUpdateCount++;
  }

  @Override
  public ExecutableFlow fetchExecutableFlow(final int execId)
          throws ExecutorManagerException {
    final ExecutableFlow flow = this.flows.get(execId);
    return ExecutableFlow.createExecutableFlowFromObject(flow.toObject());
  }

  @Override
  public Map<Integer, Pair<ExecutionReference, ExecutableFlow>> fetchActiveFlows()
          throws ExecutorManagerException {
    return this.activeFlows;
  }

  @Override
  public Map<Integer, Pair<ExecutionReference, ExecutableFlow>> fetchUnfinishedFlows()
          throws ExecutorManagerException {
    return this.activeFlows;
  }

  @Override
  public Map<Integer, Pair<ExecutionReference, ExecutableFlow>> fetchUnfinishedFlowsMetadata()
          throws ExecutorManagerException {
    return this.activeFlows.entrySet().stream()
            .collect(Collectors.toMap(Entry::getKey, e -> {
              final ExecutableFlow metadata = getExecutableFlowMetadata(e.getValue().getSecond());
              return new Pair<>(e.getValue().getFirst(), metadata);
            }));
  }

  private ExecutableFlow getExecutableFlowMetadata(
          final ExecutableFlow fullExFlow) {
    final Flow flow = new Flow(fullExFlow.getId());
    final Project project = new Project(fullExFlow.getProjectId(), null);
    project.setVersion(fullExFlow.getVersion());
    flow.setVersion(fullExFlow.getVersion());
    final ExecutableFlow metadata = new ExecutableFlow(project, flow);
    metadata.setExecutionId(fullExFlow.getExecutionId());
    metadata.setStatus(fullExFlow.getStatus());
    metadata.setSubmitTime(fullExFlow.getSubmitTime());
    metadata.setStartTime(fullExFlow.getStartTime());
    metadata.setEndTime(fullExFlow.getEndTime());
    metadata.setSubmitUser(fullExFlow.getSubmitUser());
    return metadata;
  }



  @Override
  public Pair<ExecutionReference, ExecutableFlow> fetchActiveFlowByExecId(final int execId) {
    return new Pair<>(null, null);
  }

  @Override
  public List<ExecutableFlow> fetchFlowHistory(final int projectId, final String flowId,
                                               final int skip, final int num) throws ExecutorManagerException {
    return null;
  }

  @Override
  public List<ExecutableFlow> fetchFlowHistory(int projectId, String flowId)
          throws ExecutorManagerException {
    return null;
  }

  @Override
  public void addActiveExecutableReference(final ExecutionReference ref)
          throws ExecutorManagerException {
    this.refs.put(ref.getExecId(), ref);
  }

  @Override
  public void removeActiveExecutableReference(final int execId)
          throws ExecutorManagerException {
    this.refs.remove(execId);
  }

  @Override
  public void uploadLogFile(final int execId, final String name, final int attempt,
                            final File... files)
          throws ExecutorManagerException {
    for (final File file : files) {
      try {
        final String logs = FileUtils.readFileToString(file, "UTF-8");
        logger.info("Uploaded log for [" + name + "]:[" + execId + "]:\n" + logs);
      } catch (final IOException e) {
        throw new RuntimeException(e);
      }
    }
  }

  @Override
  public void uploadLogPath(int execId, String name, int attempt, String hdfsPath)
          throws ExecutorManagerException {

  }

  @Override
  public void updateExecutableFlow(final ExecutableFlow flow)
          throws ExecutorManagerException {
    final ExecutableFlow toUpdate = this.flows.get(flow.getExecutionId());

    toUpdate.applyUpdateObject(flow.toUpdateObject(0));
    this.flowUpdateCount++;
  }

  @Override
  public int updateExecutableFlowRunDate(ExecutableFlow flow) throws SQLException {
    return 0;
  }

  @Override
  public void uploadExecutableNode(final ExecutableNode node, final Props inputParams)
          throws ExecutorManagerException {
    // Clone the job node to mimick how it would be saved in DB.
    // If we would keep a handle to the original job node, we would also see any changes made after
    // this method was called. We must only store a snapshot of the current state.
    // Also to avoid modifying statuses of the original job nodes in this.updateExecutableNode()
    final ExecutableNode exNode = new ExecutableNode();
    exNode.fillExecutableFromMapObject(node.toObject());

    this.nodes.put(node.getId(), exNode);
    this.jobUpdateCount.put(node.getId(), 1);
  }

  @Override
  public void updateExecutableNode(final ExecutableNode node)
          throws ExecutorManagerException {
    final ExecutableNode foundNode = this.nodes.get(node.getId());
    foundNode.setEndTime(node.getEndTime());
    foundNode.setStartTime(node.getStartTime());
    foundNode.setStatus(node.getStatus());
    foundNode.setUpdateTime(node.getUpdateTime());

    Integer value = this.jobUpdateCount.get(node.getId());
    if (value == null) {
      throw new ExecutorManagerException("The node has not been uploaded");
    } else {
      this.jobUpdateCount.put(node.getId(), ++value);
    }

    this.flowUpdateCount++;
  }

  @Override
  public void updateExecutableNodeStatus(final ExecutableNode node)
          throws ExecutorManagerException {
    final ExecutableNode foundNode = this.nodes.get(node.getId());
    foundNode.setStatus(node.getStatus());

    Integer value = this.jobUpdateCount.get(node.getId());
    if (value == null) {
      throw new ExecutorManagerException("The node has not been uploaded");
    } else {
      this.jobUpdateCount.put(node.getId(), ++value);
    }

    this.flowUpdateCount++;
  }

  @Override
  public int fetchNumExecutableFlows(final int projectId, final String flowId)
          throws ExecutorManagerException {
    return 0;
  }

  @Override
  public int fetchNumExecutableFlows() throws ExecutorManagerException {
    // TODO Auto-generated method stub
    return 0;
  }

  public Integer getNodeUpdateCount(final String jobId) {
    return this.jobUpdateCount.get(jobId);
  }

  @Override
  public ExecutableJobInfo fetchJobInfo(final int execId, final String jobId, final int attempt)
          throws ExecutorManagerException {
    // TODO Auto-generated method stub
    return null;
  }

  @Override
  public boolean updateExecutableReference(final int execId, final long updateTime)
          throws ExecutorManagerException {
    // TODO Auto-generated method stub
    return true;
  }

  @Override
  public LogData fetchLogs(final int execId, final String name, final int attempt,
                           final int startByte,
                           final int endByte) throws ExecutorManagerException {
    // TODO Auto-generated method stub
    return null;
  }

  @Override
  public List<ExecutableFlow> fetchFlowHistory(final int skip, final int num)
          throws ExecutorManagerException {
    // TODO Auto-generated method stub
    return null;
  }


  @Override
  public List<ExecutableJobInfo> fetchJobHistory(final int projectId, final String jobId,
                                                 final int skip, final int size) throws ExecutorManagerException {
    // TODO Auto-generated method stub
    return null;
  }

  @Override
  public List<ExecutableJobInfo> fetchDiagnosisJob(long startTime) throws ExecutorManagerException {
    return null;
  }

  @Override
  public List<ExecutableJobInfo> fetchQuickSearchJobExecutions(int projectId, String jobId, String searchTerm, int skip, int size) throws ExecutorManagerException {
    return null;
  }

  @Override
  public List<ExecutableJobInfo> searchJobExecutions(HistoryQueryParam historyQueryParam, int skip, int size) throws ExecutorManagerException {
    return null;
  }

  @Override
  public int fetchNumExecutableNodes(final int projectId, final String jobId)
          throws ExecutorManagerException {
    // TODO Auto-generated method stub
    return 0;
  }

  @Override
  public int quickSearchNumberOfJobExecutions(int projectId, String jobId, String searchTerm) throws ExecutorManagerException {
    return 0;
  }

  @Override
  public int searchNumberOfJobExecutions(HistoryQueryParam historyQueryParam) throws ExecutorManagerException {
    return 0;
  }

  @Override
  public Props fetchExecutionJobInputProps(final int execId, final String jobId)
          throws ExecutorManagerException {
    // TODO Auto-generated method stub
    return null;
  }

  @Override
  public Props fetchExecutionJobOutputProps(final int execId, final String jobId)
          throws ExecutorManagerException {
    // TODO Auto-generated method stub
    return null;
  }

  @Override
  public Pair<Props, Props> fetchExecutionJobProps(final int execId, final String jobId)
          throws ExecutorManagerException {
    // TODO Auto-generated method stub
    return null;
  }

  @Override
  public List<ExecutableJobInfo> fetchJobInfoAttempts(final int execId, final String jobId)
          throws ExecutorManagerException {
    // TODO Auto-generated method stub
    return null;
  }

  @Override
  public int removeExecutionLogsByTime(final long millis)
          throws ExecutorManagerException {
    // TODO Auto-generated method stub
    return 0;
  }

  @Override
  public List<ExecutableFlow> fetchFlowHistory(final int projectId, final String flowId,
                                               final int skip, final int num, final Status status) throws ExecutorManagerException {
    // TODO Auto-generated method stub
    return null;
  }

  @Override
  public List<Object> fetchAttachments(final int execId, final String name, final int attempt)
          throws ExecutorManagerException {
    // TODO Auto-generated method stub
    return null;
  }

  @Override
  public void uploadAttachmentFile(final ExecutableNode node, final File file)
          throws ExecutorManagerException {
    // TODO Auto-generated method stub

  }

  @Override
  public List<Executor> fetchActiveExecutors() throws ExecutorManagerException {
    final List<Executor> activeExecutors = new ArrayList<>();
    for (final Executor executor : this.executors) {
      if (executor.isActive()) {
        activeExecutors.add(executor);
      }
    }
    return activeExecutors;
  }

  @Override
  public Executor fetchExecutor(final String host, final int port)
          throws ExecutorManagerException {
    for (final Executor executor : this.executors) {
      if (executor.getHost().equals(host) && executor.getPort() == port) {
        return executor;
      }
    }
    return null;
  }

  @Override
  public Executor fetchExecutor(final int executorId) throws ExecutorManagerException {
    for (final Executor executor : this.executors) {
      if (executor.getId() == executorId) {
        return executor;
      }
    }
    return null;
  }

  @Override
  public Executor addExecutor(final String host, final int port)
          throws ExecutorManagerException {
    Executor executor = null;
    if (fetchExecutor(host, port) == null) {
      this.executorIdCounter++;
      executor = new Executor(this.executorIdCounter, host, port, true);
      this.executors.add(executor);
    }
    return executor;
  }

  @Override
  public void removeExecutor(final String host, final int port) throws ExecutorManagerException {
    final Executor executor = fetchExecutor(host, port);
    if (executor != null) {
      this.executorIdCounter--;
      this.executors.remove(executor);
    }
  }

  @Override
  public void postExecutorEvent(final Executor executor, final EventType type, final String user,
                                final String message) throws ExecutorManagerException {
    final ExecutorLogEvent event =
            new ExecutorLogEvent(executor.getId(), user, new Date(), type, message);

    if (!this.executorEvents.containsKey(executor.getId())) {
      this.executorEvents.put(executor.getId(), new ArrayList<>());
    }

    this.executorEvents.get(executor.getId()).add(event);
  }

  @Override
  public List<ExecutorLogEvent> getExecutorEvents(final Executor executor, final int num,
                                                  final int skip) throws ExecutorManagerException {
    if (!this.executorEvents.containsKey(executor.getId())) {
      final List<ExecutorLogEvent> events = this.executorEvents.get(executor.getId());
      return events.subList(skip, Math.min(num + skip - 1, events.size() - 1));
    }
    return null;
  }

  @Override
  public void updateExecutor(final Executor executor) throws ExecutorManagerException {
    final Executor oldExecutor = fetchExecutor(executor.getId());
    this.executors.remove(oldExecutor);
    this.executors.add(executor);
  }

  @Override
  public List<Executor> fetchAllExecutors() throws ExecutorManagerException {
    return this.executors;
  }

  @Override
  public void assignExecutor(final int executorId, final int execId)
          throws ExecutorManagerException {
    final ExecutionReference ref = this.refs.get(execId);
    ref.setExecutor(fetchExecutor(executorId));
    this.executionExecutorMapping.put(execId, executorId);
  }

  @Override
  public Executor fetchExecutorByExecutionId(final int execId) throws ExecutorManagerException {
    if (this.executionExecutorMapping.containsKey(execId)) {
      return fetchExecutor(this.executionExecutorMapping.get(execId));
    } else {
      throw new ExecutorManagerException(
              "Failed to find executor with execution : " + execId);
    }
  }

  @Override
  public List<Pair<ExecutionReference, ExecutableFlow>> fetchQueuedFlows()
          throws ExecutorManagerException {
    final List<Pair<ExecutionReference, ExecutableFlow>> queuedFlows =
            new ArrayList<>();
    for (final int execId : this.refs.keySet()) {
      if (!this.executionExecutorMapping.containsKey(execId)) {
        queuedFlows.add(new Pair<>(this.refs
                .get(execId), this.flows.get(execId)));
      }
    }
    return queuedFlows;
  }

  @Override
  public void unassignExecutor(final int executionId) throws ExecutorManagerException {
    this.executionExecutorMapping.remove(executionId);
  }

  @Override
  public List<ExecutableFlow> fetchRecentlyFinishedFlows(final Duration maxAge)
          throws ExecutorManagerException {
    return new ArrayList<>();
  }

  @Override
  public int selectAndUpdateExecution(final int executorId, final boolean isActive)
          throws ExecutorManagerException {
    return 1;
  }

  @Override
  public void unsetExecutorIdForExecution(final int executionId) {
  }

  @Override
  public List<ExecutableFlow> fetchAllUnfinishedFlows() throws ExecutorManagerException {
    return null;
  }

  @Override
  public List<ExecutableFlow> fetchMaintainedFlowHistory(String userType, String username, List<Integer> projectIds, int skip, int size) throws ExecutorManagerException {
    return null;
  }

  @Override
  public List<ExecutableFlow> fetchFlowHistory(String projContain, String flowContains,
                                               String execIdContain, String userNameContains, String status, long startData, long endData,
                                               String runDate, int skip, int num, int flowType) throws ExecutorManagerException {
    return null;
  }

  @Override
  public List<ExecutableFlow> fetchFlowHistory(HistoryQueryParam param, int skip, int num) throws ExecutorManagerException {
    return null;
  }

  @Override
  public List<ExecutableFlow> fetchMaintainedFlowHistory(String projContain, String flowContains,
                                                         String execIdContain, String userNameContains, String status, long startData, long endData,
                                                         String runDate, int skip, int num, int flowType, String username, List<Integer> projectIds)
          throws ExecutorManagerException {
    return null;
  }

  @Override
  public List<ExecutableFlow> fetchMaintainedFlowHistory(HistoryQueryParam param, int skip, int size, List<Integer> projectIds)
          throws ExecutorManagerException {
    return null;
  }

  @Override
  public List<ExecutableFlow> fetchFlowHistoryQuickSearch(String searchContains, String userNameContains, int skip, int num) throws ExecutorManagerException {
    return null;
  }

  @Override
  public List<ExecutableFlow> fetchFlowHistoryQuickSearch(String searchContains, String username, int skip, int num, List<Integer> projectIds) throws ExecutorManagerException {
    return null;
  }

  @Override
  public List<ExecutableFlow> fetchFlowAllHistory(int projectId, String flowId, String user) throws ExecutorManagerException {
    return null;
  }

  @Override
  public List<ExecutableFlow> fetchAllExecutableFlow() throws SQLException {
    return null;
  }

  @Override
  public List<ExecutableFlow> fetchExecutableFlows(long startTime) throws SQLException {
    return null;
  }

  @Override
  public Long getJobLogOffset(int execId, String jobName, int attempt, Long length) throws ExecutorManagerException {
    return null;
  }

  @Override
  public String getHdfsLogPath(int execId, String name, int attempt) throws ExecutorManagerException {
    return null;
  }

  @Override
  public int getLogEncType(int execId, String name, int attempt) throws ExecutorManagerException {
    return 0;
  }

  @Override
  public List<ExecutableJobInfo> fetchJobAllHistory(int projectId, String jobId) throws ExecutorManagerException {
    return null;
  }

  @Override
  public List<ExecutableJobInfo> fetchExecutableJobInfo(long startTime) throws ExecutorManagerException {
    return null;
  }

  @Override
  public ExecutableFlow getProjectLastExecutableFlow(int projectId, String flowId) throws ExecutorManagerException {
    return null;
  }

  @Override
  public LogData fetchAllLogs(int execId, String name, int attempt) throws ExecutorManagerException {
    return null;
  }

  @Override
  public List<ExecutableFlow> fetchUserFlowHistory(int skip, int num, String user) throws ExecutorManagerException {
    return null;
  }

  @Override
  public List<ExecutableFlow> fetchUserFlowHistoryByAdvanceFilter(String projContain,
                                                                  String flowContains, String execIdContain, String userNameContains, String status,
                                                                  long startData, long endData, String runDate, int skip, int num, int flowType)
          throws ExecutorManagerException {
    return null;
  }

  @Override
  public List<ExecutableFlow> fetchUserFlowHistoryByAdvanceFilter(String projContain, String flowContains, String execIdContain, String userNameContains, String status, long startData, long endData, String subsystem, String busPath, String department, String runDate, int skip, int num, int flowType) throws ExecutorManagerException {
    return null;
  }

  @Override
  public List<ExecutableFlow> fetchHistoryRecoverFlows(String userNameContains) throws ExecutorManagerException {
    return null;
  }

  @Override
  public List<ExecutableFlow> fetchHistoryRecoverFlowByRepeatId(String repeatId) throws ExecutorManagerException {
    return null;
  }

  @Override
  public List<ExecutableFlow> fetchHistoryRecoverFlowByFlowId(String flowId, String projectId) throws ExecutorManagerException {
    return null;
  }

  @Override
  public List<ExecutionRecover> listHistoryRecoverFlows(Map paramMap, int skip, int num) throws ExecutorManagerException {
    return null;
  }

  @Override
  public List<ExecutionRecover> listMaintainedHistoryRecoverFlows(String username, List<Integer> projectIds, int skip, int num) throws ExecutorManagerException {
    return null;
  }

  @Override
  public Integer saveHistoryRecoverFlow(ExecutionRecover executionRecover) throws ExecutorManagerException {
    return null;
  }

  @Override
  public List<ExecutionRecover> fetchHistoryRecoverFlows() throws ExecutorManagerException {
    return null;
  }

  @Override
  public void updateHistoryRecover(ExecutionRecover executionRecover) throws ExecutorManagerException {

  }

  @Override
  public ExecutionRecover getHistoryRecoverFlow(Integer recoverId) throws ExecutorManagerException {
    return null;
  }

  @Override
  public ExecutionRecover getHistoryRecoverFlowByPidAndFid(String projectId, String flowId) throws ExecutorManagerException {
    return null;
  }

  @Override
  public List<ExecutionRecover> listHistoryRecoverRunnning(Integer loadSize) throws ExecutorManagerException {
    return null;
  }

  @Override
  public int getHistoryRecoverTotal() throws ExecutorManagerException {
    return 0;
  }

  @Override
  public int getUserRecoverHistoryTotal(String userName) throws ExecutorManagerException {
    return 0;
  }

  @Override
  public int getMaintainedHistoryRecoverTotal(String username, List<Integer> maintainedProjectIds) throws ExecutorManagerException {
    return 0;
  }

  @Override
  public Executor addExecutorFixed(int id, String host, int port) throws ExecutorManagerException {
    return null;
  }

  @Override
  public List<LogFilterEntity> listAllLogFilter() throws ExecutorManagerException {
    return null;
  }

  @Override
  public int getExecHistoryTotal(HistoryQueryParam param) throws ExecutorManagerException {
    return 0;
  }

  @Override
  public int getExecHistoryTotal(HistoryQueryParam param, List<Integer> projectIds) throws ExecutorManagerException {
    return 0;
  }

  @Override
  public int getMaintainedExecHistoryTotal(String username, List<Integer> projectIds) throws ExecutorManagerException {
    return 0;
  }

  @Override
  public int getExecHistoryQuickSerachTotal(Map<String, String> filterMap) throws ExecutorManagerException {
    return 0;
  }

  @Override
  public int getMaintainedFlowsQuickSearchTotal(String username, Map<String, String> filterMap, List<Integer> projectIds) throws ExecutorManagerException {
    return 0;
  }

  @Override
  public List<ExecutableFlow> fetchUserFlowHistoryByProjectIdAndFlowId(int projectId, String flowId, int skip, int num, String userName) throws ExecutorManagerException {
    return null;
  }

  @Override
  public int fetchNumUserExecutableFlowsByProjectIdAndFlowId(int projectId, String flowId, String userName) throws ExecutorManagerException {
    return 0;
  }

  @Override
  public int getUserExecHistoryTotal(HistoryQueryParam param, String loginUser) throws ExecutorManagerException {
    return 0;
  }

  @Override
  public int getUserExecHistoryQuickSerachTotal(Map<String, String> filterMap) throws ExecutorManagerException {
    return 0;
  }

  @Override
  public List<ExecutableFlow> fetchUserFlowHistory(String loginUser, String projContain,
                                                   String flowContains, String execIdContain, String userNameContains, String status,
                                                   long startData, long endData, String runDate, int skip, int num, int flowType)
          throws ExecutorManagerException {
    return null;
  }

  @Override
  public List<ExecutableFlow> fetchUserFlowHistory(String loginUser, HistoryQueryParam param,
                                                   int skip, int size) throws ExecutorManagerException {
    return null;
  }

  @Override
  public List<ExecutableFlow> getTodayExecutableFlowData(String userName) throws ExecutorManagerException {
    return null;
  }

  @Override
  public List<ExecutableFlow> getTodayExecutableFlowDataNew(String userName) throws ExecutorManagerException {
    return null;
  }

  @Override
  public Integer getTodayFlowRunTimesByFlowId(String projectId, String flowId, String usename) throws ExecutorManagerException {
    return null;
  }

  @Override
  public List<ExecutableFlow> getRealTimeExecFlowData(String userName) throws ExecutorManagerException {
    return null;
  }

  @Override
  public List<Integer> getExecutorIdsBySubmitUser(String submitUser) throws ExecutorManagerException {
    return null;
  }

  @Override
  public List<DepartmentGroup> fetchAllDepartmentGroup() throws ExecutorManagerException {
    return null;
  }

  @Override
  public void addDepartmentGroup(DepartmentGroup departmentGroup) throws ExecutorManagerException {

  }

  @Override
  public boolean checkGroupNameIsExist(DepartmentGroup departmentGroup) throws ExecutorManagerException {
    return false;
  }

  @Override
  public boolean checkExecutorIsUsed(int executorId) throws ExecutorManagerException {
    return false;
  }

  @Override
  public int deleteDepartmentGroup(DepartmentGroup departmentGroup) throws ExecutorManagerException {
    return 0;
  }

  @Override
  public int updateDepartmentGroup(DepartmentGroup departmentGroup) throws ExecutorManagerException {
    return 0;
  }

  @Override
  public DepartmentGroup fetchDepartmentGroupById(Integer id) throws ExecutorManagerException {
    return null;
  }

  @Override
  public int groupIdIsExist(DepartmentGroup departmentGroup) throws ExecutorManagerException {
    return 0;
  }

  @Override
  public void addUserVariable(UserVariable userVariable) throws ExecutorManagerException {

  }

  @Override
  public int deleteUserVariable(UserVariable variable) throws ExecutorManagerException {
    return 0;
  }

  @Override
  public int updateUserVariable(UserVariable userVariable) throws ExecutorManagerException {
    return 0;
  }

  @Override
  public List<UserVariable> fetchAllUserVariable(UserVariable userVariable) throws ExecutorManagerException {
    return null;
  }

  @Override
  public UserVariable getUserVariableById(Integer id) throws ExecutorManagerException {
    return null;
  }

  @Override
  public Map<String, String> getUserVariableByName(String userName) throws ExecutorManagerException {
    return null;
  }

  @Override
  public Integer findWtssUserByName(String name) throws ExecutorManagerException {
    return null;
  }

  @Override
  public Integer getWtssUserTotal() throws ExecutorManagerException {
    return null;
  }

  @Override
  public List<WtssUser> findAllWtssUserPageList(String searchName, int pageNum, int pageSize) throws ExecutorManagerException {
    return null;
  }

  @Override
  public int getExecutionCycleTotal(Optional<String> usernameOp) throws ExecutorManagerException {
    return 0;
  }

  @Override
  public int getExecutionCycleAllTotal(String userName, String searchTerm, HashMap<String, String> queryMap) throws ExecutorManagerException {
    return 0;
  }

  @Override
  public List<ExecutionCycle> getExecutionCycleAllPages(String userName, String searchTerm, int offset, int length,HashMap<String, String> queryMap) throws ExecutorManagerException {
    return null;
  }

  @Override
  public int getExecutionCycleTotal(String username, List<Integer> projectIds) throws ExecutorManagerException {
    return 0;
  }

  @Override
  public List<ExecutionCycle> listExecutionCycleFlows(Optional<String> username, int offset, int length) throws ExecutorManagerException {
    return null;
  }

  @Override
  public List<ExecutionCycle> listExecutionCycleFlows(String username, List<Integer> projectIds, int offset, int length) throws ExecutorManagerException {
    return null;
  }

  @Override
  public int saveExecutionCycleFlow(ExecutionCycle cycleFlow) throws ExecutorManagerException {
    return 0;
  }

  @Override
  public ExecutionCycle getExecutionCycleFlow(String projectId, String flowId) throws ExecutorManagerException {
    return null;
  }

  @Override
  public ExecutionCycle getExecutionCycleFlowDescId(String projectId, String flowId) throws ExecutorManagerException {
    return null;
  }

  @Override
  public ExecutionCycle getExecutionCycleFlow(int id) throws ExecutorManagerException {
    return null;
  }

  @Override
  public int updateExecutionFlow(ExecutionCycle cycleFlow) throws ExecutorManagerException {
    return 0;
  }

  @Override
  public int stopAllCycleFlows() throws ExecutorManagerException {
    return 0;
  }

  @Override
  public List<ExecutionCycle> getAllRunningCycleFlows() throws ExecutorManagerException {
    return null;
  }

  @Override
  public List<UserVariable> fetchAllUserVariableByOwnerDepartment(Integer departmentId) throws ExecutorManagerException {
    return null;
  }

  @Override
  public List<ExecutableFlow> fetchExecutableFlowByRepeatId(int repeatId) throws ExecutorManagerException {
    return null;
  }

  @Override
  public List<RecoverTrigger> fetchHistoryRecoverTriggers() {
    return null;
  }

  @Override
  public UserVariable findUserVariableByKey(String key) throws ExecutorManagerException {
    return null;
  }

  @Override
  public boolean fetchGroupScheduleSwitch(String submitUser) {
    return true;
  }

  @Override
  public List<Integer> getRunningExecByLock(Integer projectName, String flowId) throws ExecutorManagerException {
    return null;
  }

  @Override
  public Set<DmsBusPath> getDmsBusPathFromDb(String jobCode) {
    return null;
  }

  @Override
  public Set<DmsBusPath> getDmsBusPathFromDb(String jobCode, String updateTime) {
    return null;
  }

  @Override
  public void insertOrUpdate(DmsBusPath dmsBusPath) {

  }

  @Override
  public String getEventType(String topic, String msgName) {
    return null;
  }

  @Override
  public void addHoldBatchOpr(String id, int oprType, int oprLevel, String user, long createTime, String oprData)
          throws ExecutorManagerException {

  }

  @Override
  public void addHoldBatchAlert(String batchId, ExecutableFlow executableFlow, int resumeStatus) throws ExecutorManagerException {

  }

  @Override
  public List<HoldBatchOperate> getLocalHoldBatchOpr() throws ExecutorManagerException {
    return null;
  }

  @Override
  public List<HoldBatchAlert> queryAlertByBatch(String batchId) throws ExecutorManagerException {
    return null;
  }

  @Override
  public void updateHoldBatchAlertStatus(HoldBatchAlert holdBatchAlert) throws ExecutorManagerException {

  }

  @Override
  public List<Pair<ExecutionReference, ExecutableFlow>> fetchFlowByStatus(Status status) throws ExecutorManagerException {
    return null;
  }

  @Override
  public void linkJobHook(String jobCode, String prefixRules, String suffixRules, String username)
          throws SQLException {

  }

  @Override
  public JobHook getJobHook(String jobCode) {
    return null;
  }

  @Override
  public HoldBatchAlert queryBatchExecutableFlows(long id)
          throws ExecutorManagerException {
    return null;
  }

  @Override
  public HoldBatchAlert querySubmittedExecutableFlows(long id)
          throws ExecutorManagerException {
    return null;
  }

  @Override
  public void updateHoldBatchResumeStatus(String projectName, String flowName)
          throws ExecutorManagerException {

  }

  @Override
  public void addHoldBatchResume(String batchId, String oprData, String user)
          throws ExecutorManagerException {

  }

  @Override
  public void updateHoldBatchStatus(String batchId, int status) throws ExecutorManagerException {

  }

  @Override
  public String getLocalHoldBatchResume(String batchId) throws ExecutorManagerException {
    return null;
  }

  @Override
  public void addHoldBatchFrequent(String batchId, ExecutableFlow executableFlow)
          throws ExecutorManagerException {

  }

  @Override
  public List<HoldBatchAlert> queryExecByBatch(String batchId) throws ExecutorManagerException {
    return null;
  }

  @Override
  public List<HoldBatchAlert> queryFrequentByBatch(String batchId)
          throws ExecutorManagerException {
    return null;
  }

  @Override
  public void updateHoldBatchFrequentStatus(HoldBatchAlert holdBatchAlert)
          throws ExecutorManagerException {

  }

  @Override
  public List<HoldBatchAlert> queryExecingByBatch(String batchId) throws ExecutorManagerException {
    return null;
  }

  @Override
  public void updateHoldBatchResumeStatus(HoldBatchAlert holdBatchAlert)
          throws ExecutorManagerException {

  }

  @Override
  public void updateHoldBatchExpired(String batchId) throws ExecutorManagerException {

  }

  @Override
  public void updateHoldBatchId(String batchId) throws ExecutorManagerException {

  }

  @Override
  public List<HoldBatchOperate> getMissResumeBatch() throws ExecutorManagerException {
    return null;
  }

  @Override
  public List<Integer> queryWaitingFlow(String project, String flow) {
    return null;
  }

  @Override
  public HoldBatchAlert getHoldBatchAlert(long id) {
    return null;
  }

  @Override
  public List<HoldBatchAlert> queryWaitingAlert() {
    return null;
  }

  @Override
  public List<CfgWebankOrganization> fetchAllDepartment() {
    return null;
  }

  @Override
  public void updateHoldBatchNotResumeByExecId(int execId) {

  }

  @Override
  public List<ExecutionRecover> getUserHistoryRerunConfiguration(int projectId, String flowName, String userId, int start, int size) throws ExecutorManagerException {
    return null;
  }

  @Override
  public List<ExecutionRecover> getMaintainedHistoryRerunConfiguration(int id, String flow, String userId, int start, int size) {
    return null;
  }

  @Override
  public List<ExecutionRecover> getAllHistoryRerunConfiguration(int id, String flow, int start, int size) {
    return null;
  }

  @Override
  public int getAllExecutionRecoverTotal(int projectId, String flowName) {
    return 0;
  }

  @Override
  public int getMaintainedExecutionRecoverTotal(int projectId, String flowName, String userId) {
    return 0;
  }

  @Override
  public int getUserExecutionRecoverTotal(int projectId, String flowName, String userId) {
    return 0;
  }

  @Override
  public long getFinalScheduleTime(long triggerInitTime) {
    return 0;
  }

  @Override
  public List<ExecutableFlow> fetchUnfinishedFlows(ExecutingQueryParam executingQueryParam) {
    return null;
  }

  @Override
  public List<Integer> selectUnfinishedFlows(int projectId, String flowId) throws ExecutorManagerException {
    return null;
  }

  @Override
  public List<Integer> selectUnfinishedFlows() throws ExecutorManagerException {
    return null;
  }

  @Override
  public long getAllUnfinishedFlows() {
    return 0;
  }

  @Override
  public long getUnfinishedFlowsTotal(ExecutingQueryParam executingQueryParam) {
    return 0;
  }

  @Override
  public List<ExecutableFlow> quickSearchFlowExecutions(int projectId, String flowId, int from, int length, String searchTerm) {
    return null;
  }

  @Override
  public int fetchQuickSearchNumExecutableFlows(int projectId, String flowId, String searchTerm) {
    return 0;
  }

  @Override
  public List<ExecutableFlow> userQuickSearchFlowExecutions(int projectId, String flowId, int from, int length, String searchTerm, String userId) {
    return null;
  }

  @Override
  public int fetchUserQuickSearchNumExecutableFlows(int projectId, String flowId, String searchTerm, String userId) {
    return 0;
  }

  @Override
  public List<Integer> selectQueuedFlows(Status status) throws ExecutorManagerException {
    return null;
  }

  @Override
  public Hosts getHostConfigByHostname(String hostname) throws ExecutorManagerException{
    return null;
  }

  @Override
  public int insertHostsConfig(Hosts hosts) throws ExecutorManagerException {
    return 0;
  }

  @Override
  public int executorOffline(int executorid) throws ExecutorManagerException {
    return 0;
  }

  @Override
  public int executorOnline(int executorid) throws ExecutorManagerException {
    return 0;
  }

  @Override
  public boolean checkIsOnline(int executorid) throws ExecutorManagerException {
    return false;
  }

  @Override
  public List<ExecutableFlow> getFlowTodayHistory(int projectId, String flowId) {
    return null;
  }


  @Override
  public JobPredictionExecutionInfo fetchJobPredictionExecutionInfo(int projectId, String flowId, String jobId) throws ExecutorManagerException {
    return null;
  }

  @Override
  public List<JobPredictionExecutionInfo> fetchJobPredictionExecutionInfoList(int projectId, String flowId) throws ExecutorManagerException {
    return null;
  }

  @Override
  public void deleteExecutionCycle(int projectId, String flowId) {

  }
  @Override
  public List<ExecutionCycle> getRunningCycleFlows(Integer projectId, String flowId) {
    return null;
  }
}
