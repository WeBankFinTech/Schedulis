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

package azkaban.executor;

import azkaban.executor.ExecutorLogEvent.EventType;
import azkaban.history.ExecutionRecover;
import azkaban.history.RecoverTrigger;
import com.webank.wedatasphere.schedulis.common.log.LogFilterEntity;
import com.webank.wedatasphere.schedulis.common.system.entity.WtssUser;
import azkaban.utils.FileIOUtils.LogData;
import azkaban.utils.Pair;
import azkaban.utils.Props;
import com.webank.wedatasphere.schedulis.common.executor.DepartmentGroup;
import com.webank.wedatasphere.schedulis.common.executor.ExecutionCycle;
import com.webank.wedatasphere.schedulis.common.executor.UserVariable;
import java.io.File;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public interface ExecutorLoader {

  void uploadExecutableFlow(ExecutableFlow flow)
      throws ExecutorManagerException;

  ExecutableFlow fetchExecutableFlow(int execId) throws ExecutorManagerException;

  List<ExecutableFlow> fetchExecutableFlowByRepeatId(int repeatId)
      throws ExecutorManagerException;

  List<ExecutableFlow> fetchRecentlyFinishedFlows(Duration maxAge)
      throws ExecutorManagerException;

  Map<Integer, Pair<ExecutionReference, ExecutableFlow>> fetchActiveFlows()
      throws ExecutorManagerException;

  Map<Integer, Pair<ExecutionReference, ExecutableFlow>> fetchUnfinishedFlows()
      throws ExecutorManagerException;

  List<ExecutableFlow> fetchAllUnfinishedFlows() throws ExecutorManagerException;

  Map<Integer, Pair<ExecutionReference, ExecutableFlow>> fetchUnfinishedFlowsMetadata()
      throws ExecutorManagerException;

  Pair<ExecutionReference, ExecutableFlow> fetchActiveFlowByExecId(int execId)
      throws ExecutorManagerException;

  List<ExecutableFlow> fetchFlowHistory(int skip, int num)
      throws ExecutorManagerException;

  List<ExecutableFlow> fetchMaintainedFlowHistory(String username, List<Integer> projectIds, int skip, int size)
          throws ExecutorManagerException;

  List<ExecutableFlow> fetchFlowHistory(int projectId, String flowId,
      int skip, int num) throws ExecutorManagerException;

  List<ExecutableFlow> fetchFlowHistory(int projectId, String flowId,
      int skip, int num, Status status) throws ExecutorManagerException;

  List<ExecutableFlow> fetchFlowHistory(String projContain, String flowContains,
                                        String execIdContain, String userNameContains, String status, long startData,
      long endData, int skip, int num, int flowType) throws ExecutorManagerException;

  List<ExecutableFlow> fetchMaintainedFlowHistory(String projContain, String flowContains,
                                        String execIdContain, String userNameContains, String status, long startData,
      long endData, int skip, int num, int flowType, String username, List<Integer> projectIds) throws ExecutorManagerException;

  List<ExecutableFlow> fetchFlowHistoryQuickSearch(String searchContains, String userNameContains, int skip, int num)
      throws ExecutorManagerException;

  List<ExecutableFlow> fetchFlowHistoryQuickSearch(String searchContains, String username, int skip, int num,
                                                   List<Integer> projectIds) throws ExecutorManagerException;

  List<ExecutableFlow> fetchFlowAllHistory(int projectId, String flowId, String user)
      throws ExecutorManagerException;

  /**
   * <pre>
   * Fetch all executors from executors table
   * Note:-
   * 1 throws an Exception in case of a SQL issue
   * 2 returns an empty list in case of no executor
   * </pre>
   *
   * @return List<Executor>
   */
  List<Executor> fetchAllExecutors() throws ExecutorManagerException;

  /**
   * <pre>
   * Fetch all executors from executors table with active = true
   * Note:-
   * 1 throws an Exception in case of a SQL issue
   * 2 returns an empty list in case of no active executor
   * </pre>
   *
   * @return List<Executor>
   */
  List<Executor> fetchActiveExecutors() throws ExecutorManagerException;

  /**
   * <pre>
   * Fetch executor from executors with a given (host, port)
   * Note:
   * 1. throws an Exception in case of a SQL issue
   * 2. return null when no executor is found
   * with the given (host,port)
   * </pre>
   *
   * @return Executor
   */
  Executor fetchExecutor(String host, int port)
      throws ExecutorManagerException;

  /**
   * <pre>
   * Fetch executor from executors with a given executorId
   * Note:
   * 1. throws an Exception in case of a SQL issue
   * 2. return null when no executor is found with the given executorId
   * </pre>
   *
   * @return Executor
   */
  Executor fetchExecutor(int executorId) throws ExecutorManagerException;

  /**
   * <pre>
   * create an executor and insert in executors table.
   * Note:-
   * 1. throws an Exception in case of a SQL issue
   * 2. throws an Exception if a executor with (host, port) already exist
   * 3. return null when no executor is found with the given executorId
   * </pre>
   *
   * @return Executor
   */
  Executor addExecutor(String host, int port)
      throws ExecutorManagerException;

  /**
   * <pre>
   * create an executor and insert in executors table.
   * Note:-
   * 1. throws an Exception in case of a SQL issue
   * 2. throws an Exception if there is no executor with the given id
   * 3. return null when no executor is found with the given executorId
   * </pre>
   */
  void updateExecutor(Executor executor) throws ExecutorManagerException;

  /**
   * <pre>
   * Remove the executor from executors table.
   * Note:-
   * 1. throws an Exception in case of a SQL issue
   * 2. throws an Exception if there is no executor in the table* </pre>
   * </pre>
   */
  void removeExecutor(String host, int port) throws ExecutorManagerException;

  /**
   * <pre>
   * Log an event in executor_event audit table Note:- throws an Exception in
   * case of a SQL issue
   * Note: throws an Exception in case of a SQL issue
   * </pre>
   *
   * @return isSuccess
   */
  void postExecutorEvent(Executor executor, EventType type, String user,
      String message) throws ExecutorManagerException;

  /**
   * <pre>
   * This method is to fetch events recorded in executor audit table, inserted
   * by postExecutorEvents with a given executor, starting from skip
   * Note:-
   * 1. throws an Exception in case of a SQL issue
   * 2. Returns an empty list in case of no events
   * </pre>
   *
   * @return List<ExecutorLogEvent>
   */
  List<ExecutorLogEvent> getExecutorEvents(Executor executor, int num,
      int offset) throws ExecutorManagerException;

  void addActiveExecutableReference(ExecutionReference ref)
      throws ExecutorManagerException;

  void removeActiveExecutableReference(int execId)
      throws ExecutorManagerException;


  /**
   * <pre>
   * Unset executor Id for an execution
   * Note:-
   * throws an Exception in case of a SQL issue
   * </pre>
   */
  void unassignExecutor(int executionId) throws ExecutorManagerException;

  /**
   * <pre>
   * Set an executor Id to an execution
   * Note:-
   * 1. throws an Exception in case of a SQL issue
   * 2. throws an Exception in case executionId or executorId do not exist
   * </pre>
   */
  void assignExecutor(int executorId, int execId)
      throws ExecutorManagerException;

  /**
   * <pre>
   * Fetches an executor corresponding to a given execution
   * Note:-
   * 1. throws an Exception in case of a SQL issue
   * 2. return null when no executor is found with the given executionId
   * </pre>
   *
   * @return fetched Executor
   */
  Executor fetchExecutorByExecutionId(int executionId)
      throws ExecutorManagerException;

  /**
   * <pre>
   * Fetch queued flows which have not yet dispatched
   * Note:
   * 1. throws an Exception in case of a SQL issue
   * 2. return empty list when no queued execution is found
   * </pre>
   *
   * @return List of queued flows and corresponding execution reference
   */
  List<Pair<ExecutionReference, ExecutableFlow>> fetchQueuedFlows()
      throws ExecutorManagerException;

  boolean updateExecutableReference(int execId, long updateTime)
      throws ExecutorManagerException;

  LogData fetchLogs(int execId, String name, int attempt, int startByte,
      int endByte) throws ExecutorManagerException;

  Long getJobLogOffset(int execId, String jobName, int attempt, Long length) throws ExecutorManagerException;

  List<Object> fetchAttachments(int execId, String name, int attempt)
      throws ExecutorManagerException;

  void uploadLogFile(int execId, String name, int attempt, File... files)
      throws ExecutorManagerException;

  void uploadAttachmentFile(ExecutableNode node, File file)
      throws ExecutorManagerException;

  void updateExecutableFlow(ExecutableFlow flow)
      throws ExecutorManagerException;

  void uploadExecutableNode(ExecutableNode node, Props inputParams)
      throws ExecutorManagerException;

  List<ExecutableJobInfo> fetchJobInfoAttempts(int execId, String jobId)
      throws ExecutorManagerException;

  ExecutableJobInfo fetchJobInfo(int execId, String jobId, int attempt)
      throws ExecutorManagerException;

  List<ExecutableJobInfo> fetchJobHistory(int projectId, String jobId,
      int skip, int size) throws ExecutorManagerException;

  List<ExecutableJobInfo> fetchJobAllHistory(int projectId, String jobId)
      throws ExecutorManagerException;

  void updateExecutableNode(ExecutableNode node)
      throws ExecutorManagerException;

  int fetchNumExecutableFlows(int projectId, String flowId)
      throws ExecutorManagerException;

  int fetchNumExecutableFlows() throws ExecutorManagerException;

  int fetchNumExecutableNodes(int projectId, String jobId)
      throws ExecutorManagerException;

  Props fetchExecutionJobInputProps(int execId, String jobId)
      throws ExecutorManagerException;

  Props fetchExecutionJobOutputProps(int execId, String jobId)
      throws ExecutorManagerException;

  Pair<Props, Props> fetchExecutionJobProps(int execId, String jobId)
      throws ExecutorManagerException;

  int removeExecutionLogsByTime(long millis)
      throws ExecutorManagerException;

  

  
  void unsetExecutorIdForExecution(final int executionId) throws ExecutorManagerException;
 
  int selectAndUpdateExecution(final int executorId, boolean isActive)
      throws ExecutorManagerException;
 
  ExecutableFlow getProjectLastExecutableFlow(int projectId, String flowId) throws ExecutorManagerException;

  LogData fetchAllLogs(int execId, String name, int attempt) throws ExecutorManagerException;
  
  List<ExecutableFlow> fetchUserFlowHistory(int skip, int num, String user)
      throws ExecutorManagerException;

  List<ExecutableFlow> fetchUserFlowHistoryByAdvanceFilter(String projContain,
      String flowContains, String execIdContain, String userNameContains, String status, long startData,
      long endData, int skip, int num, int flowType) throws ExecutorManagerException;

  List<ExecutableFlow> fetchHistoryRecoverFlows(final String userNameContains) throws ExecutorManagerException;

  List<ExecutableFlow> fetchHistoryRecoverFlowByRepeatId(final String repeatId) throws ExecutorManagerException;

  List<ExecutableFlow> fetchHistoryRecoverFlowByFlowId(final String flowId, final String projectId) throws ExecutorManagerException;

  List<ExecutionRecover> listHistoryRecoverFlows(final Map paramMap, int skip, int num) throws ExecutorManagerException;

  List<ExecutionRecover> listMaintainedHistoryRecoverFlows(String username, List<Integer> projectIds, int skip, int num) throws ExecutorManagerException;

  Integer saveHistoryRecoverFlow(final ExecutionRecover executionRecover) throws ExecutorManagerException;

  List<ExecutionRecover> fetchHistoryRecoverFlows() throws ExecutorManagerException;

  List<RecoverTrigger> fetchHistoryRecoverTriggers();

  void updateHistoryRecover(final ExecutionRecover executionRecover) throws ExecutorManagerException;

  ExecutionRecover getHistoryRecoverFlow(final Integer recoverId) throws ExecutorManagerException;

  ExecutionRecover getHistoryRecoverFlowByPidAndFid(final String projectId, final String flowId)
      throws ExecutorManagerException;

  List<ExecutionRecover> listHistoryRecoverRunnning(final Integer loadSize) throws ExecutorManagerException;

  /**
   * 获取历史重跑的总数
   * @return
   * @throws ExecutorManagerException
   */
  int getHistoryRecoverTotal() throws ExecutorManagerException;

  /**
   * 获取用户历史重跑的总数
   * @return
   * @throws ExecutorManagerException
   */
  int getUserRecoverHistoryTotal(final String userName) throws ExecutorManagerException;

  /**
   * 获取运维管理者历史重跑总数
   * @param maintainedProjectIds 运维管理者运维的所有工程ID
   * @return
   * @throws ExecutorManagerException
   */
  int getMaintainedHistoryRecoverTotal(String username, List<Integer> maintainedProjectIds) throws ExecutorManagerException;

  /**
   * 插入Executor节点数据 数据表executors去除自增ID 从配置文件获取ID 保持ID不变
   * @param id
   * @param host
   * @param port
   * @return
   * @throws ExecutorManagerException
   */
  Executor addExecutorFixed(int id, String host, int port)
      throws ExecutorManagerException;

  /**
   * 获取所有的日志过滤条件
   * @return
   * @throws ExecutorManagerException
   */
  List<LogFilterEntity> listAllLogFilter() throws ExecutorManagerException;

  /**
   * 获取用户历史重跑的总数
   * @return
   * @throws ExecutorManagerException
   */
  int getExecHistoryTotal(final Map<String, String> filterMap) throws ExecutorManagerException;

  int getExecHistoryTotal(String username, final Map<String, String> filterMap, List<Integer> projectIds) throws ExecutorManagerException;

  /**
   * 根据工程ID获取用户历史重跑的总数
   * @param projectIds
   * @return
   * @throws ExecutorManagerException
   */
  int getMaintainedExecHistoryTotal(String username, List<Integer> projectIds) throws ExecutorManagerException;

  /**
   * 获取历史执行记录数
   * @return
   * @throws ExecutorManagerException
   */
  int getExecHistoryQuickSerachTotal(final Map<String, String> filterMap) throws ExecutorManagerException;

  int getMaintainedFlowsQuickSearchTotal(String username, final Map<String, String> filterMap, List<Integer> projectIds) throws ExecutorManagerException;

  /**
   *
   * @param projectId
   * @param flowId
   * @param skip
   * @param num
   * @return
   * @throws ExecutorManagerException
   */
  List<ExecutableFlow> fetchUserFlowHistoryByProjectIdAndFlowId(int projectId, String flowId,
      int skip, int num, String userName) throws ExecutorManagerException;


  int fetchNumUserExecutableFlowsByProjectIdAndFlowId(int projectId, String flowId, String userName)
      throws ExecutorManagerException;

  /**
   * 获取用户历史重跑的总数
   * @return
   * @throws ExecutorManagerException
   */
  int getUserExecHistoryTotal(final Map<String, String> filterMap) throws ExecutorManagerException;

  /**
   * 获取用户历史执行记录数
   * @return
   * @throws ExecutorManagerException
   */
  int getUserExecHistoryQuickSerachTotal(final Map<String, String> filterMap) throws ExecutorManagerException;

  /**
   *
   * @param loginUser
   * @param projContain
   * @param flowContains
   * @param userNameContains
   * @param status
   * @param startData
   * @param endData
   * @param skip
   * @param num
   * @param flowType
   * @return
   * @throws ExecutorManagerException
   */
  List<ExecutableFlow> fetchUserFlowHistory(String loginUser, String projContain,
      String flowContains, String execIdContain, String userNameContains, String status, long startData,
      long endData, int skip, int num, int flowType) throws ExecutorManagerException;

  /**
   *
   * @param userName
   * @return
   * @throws ExecutorManagerException
   */
  List<ExecutableFlow> getTodayExecutableFlowData(final String userName)
      throws ExecutorManagerException;

  /**
   *
   * @param userName
   * @return
   * @throws ExecutorManagerException
   */
  List<ExecutableFlow> getTodayExecutableFlowDataNew(final String userName) throws ExecutorManagerException;

  /**
   *
   * @param flowId
   * @return
   * @throws ExecutorManagerException
   */
  Integer getTodayFlowRunTimesByFlowId(final String projectId, final String flowId, final String usename) throws ExecutorManagerException;


  List<ExecutableFlow> getRealTimeExecFlowData(final String userName)
      throws ExecutorManagerException;

  /**
   *
   * @param submitUser
   * @return
   * @throws ExecutorManagerException
   */
  List<Integer> getExecutorIdsBySubmitUser(String submitUser) throws ExecutorManagerException;


  public List<DepartmentGroup> fetchAllDepartmentGroup() throws ExecutorManagerException;

  public void addDepartmentGroup(DepartmentGroup departmentGroup) throws ExecutorManagerException;

  public boolean checkGroupNameIsExist(DepartmentGroup departmentGroup) throws ExecutorManagerException;

  public boolean checkExecutorIsUsed(int executorId) throws ExecutorManagerException;

  public int deleteDepartmentGroup(DepartmentGroup departmentGroup) throws ExecutorManagerException;

  public int updateDepartmentGroup(DepartmentGroup departmentGroup) throws ExecutorManagerException;

  public DepartmentGroup fetchDepartmentGroupById(Integer id) throws ExecutorManagerException;

  public int groupIdIsExist(DepartmentGroup departmentGroup) throws ExecutorManagerException;


  public void addUserVariable(UserVariable userVariable) throws ExecutorManagerException;

  public int deleteUserVariable(UserVariable variable) throws ExecutorManagerException;

  public int updateUserVariable(UserVariable userVariable) throws ExecutorManagerException;

  public List<UserVariable> fetchAllUserVariable(UserVariable userVariable) throws ExecutorManagerException;

  public UserVariable getUserVariableById(Integer id) throws ExecutorManagerException;
  public Map<String, String> getUserVariableByName(String userName) throws ExecutorManagerException;

  public Integer findWtssUserByName(String name) throws ExecutorManagerException;

  public Integer getWtssUserTotal() throws ExecutorManagerException;
  public List<WtssUser> findAllWtssUserPageList(String searchName, int pageNum, int pageSize) throws ExecutorManagerException;

  int getExecutionCycleTotal(Optional<String> usernameOp) throws ExecutorManagerException;

  int getExecutionCycleTotal(String username, List<Integer> projectIds) throws ExecutorManagerException;

  List<ExecutionCycle> listExecutionCycleFlows(Optional<String> username, int offset, int length) throws ExecutorManagerException;

  List<ExecutionCycle> listExecutionCycleFlows(String username, List<Integer> projectIds, int offset, int length) throws ExecutorManagerException;

  int saveExecutionCycleFlow(ExecutionCycle cycleFlow) throws ExecutorManagerException;

  ExecutionCycle getExecutionCycleFlow(String projectId, String flowId) throws ExecutorManagerException;

  ExecutionCycle getExecutionCycleFlow(int id) throws ExecutorManagerException;

  int updateExecutionFlow(ExecutionCycle cycleFlow) throws ExecutorManagerException;

  int stopAllCycleFlows() throws ExecutorManagerException;

  List<ExecutionCycle> getAllRunningCycleFlows() throws ExecutorManagerException;

  List<UserVariable> fetchAllUserVariableByOwnerDepartment(Integer departmentId) throws ExecutorManagerException;

  List<Integer> getRunningExecByLock(Integer projectName, String flowId) throws ExecutorManagerException;

}
