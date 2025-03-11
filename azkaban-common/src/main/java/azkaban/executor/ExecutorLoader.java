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

import azkaban.batch.HoldBatchAlert;
import azkaban.batch.HoldBatchOperate;
import azkaban.executor.ExecutorLogEvent.EventType;
import azkaban.executor.entity.JobPredictionExecutionInfo;
import azkaban.history.ExecutionRecover;
import azkaban.history.RecoverTrigger;
import azkaban.jobhook.JobHook;
import azkaban.log.LogFilterEntity;
import azkaban.system.entity.WtssUser;
import azkaban.utils.FileIOUtils.LogData;
import azkaban.utils.Pair;
import azkaban.utils.Props;
import java.io.File;
import java.sql.SQLException;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

public interface ExecutorLoader {

  void uploadExecutableFlow(ExecutableFlow flow)
          throws ExecutorManagerException;

  ExecutableFlow fetchExecutableFlow(int execId)
          throws ExecutorManagerException;

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

  List<ExecutableFlow> fetchMaintainedFlowHistory(String userType, String username, List<Integer> projectIds, int skip, int size)
          throws ExecutorManagerException;

  List<ExecutableFlow> fetchFlowHistory(int projectId, String flowId,
                                        int skip, int num) throws ExecutorManagerException;

  List<ExecutableFlow> fetchFlowHistory(int projectId, String flowId)
          throws ExecutorManagerException;

  List<ExecutableFlow> fetchFlowHistory(int projectId, String flowId,
                                        int skip, int num, Status status) throws ExecutorManagerException;

  List<ExecutableFlow> fetchFlowHistory(String projContain, String flowContains,
                                        String execIdContain, String userNameContains, String status, long startData,
                                        long endData, String runDate, int skip, int num, int flowType)
          throws ExecutorManagerException;

  List<ExecutableFlow> fetchFlowHistory(HistoryQueryParam param, int skip, int num)
          throws ExecutorManagerException;

  List<ExecutableFlow> fetchMaintainedFlowHistory(String projContain, String flowContains,
                                                  String execIdContain, String userNameContains, String status, long startData,
                                                  long endData, String runDate, int skip, int num, int flowType, String username,
                                                  List<Integer> projectIds) throws ExecutorManagerException;

  List<ExecutableFlow> fetchMaintainedFlowHistory(HistoryQueryParam param, int skip, int size, List<Integer> projectIds) throws ExecutorManagerException;


  List<ExecutableFlow> fetchFlowHistoryQuickSearch(String searchContains, String userNameContains,
                                                   int skip, int num)
          throws ExecutorManagerException;

  List<ExecutableFlow> fetchFlowHistoryQuickSearch(String searchContains, String username, int skip,
                                                   int num,
                                                   List<Integer> projectIds) throws ExecutorManagerException;

  List<ExecutableFlow> fetchFlowAllHistory(int projectId, String flowId, String user)
          throws ExecutorManagerException;

  List<ExecutableFlow> fetchAllExecutableFlow() throws SQLException;

  List<ExecutableFlow> fetchExecutableFlows(final long startTime) throws SQLException;

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

  /**
   * 获取日志存放的 HDFS 路径
   *
   * @param execId
   * @param name
   * @param attempt
   * @return
   */
  String getHdfsLogPath(int execId, String name, int attempt) throws ExecutorManagerException;

  int getLogEncType(int execId, String name, int attempt) throws ExecutorManagerException;

  List<Object> fetchAttachments(int execId, String name, int attempt)
          throws ExecutorManagerException;

  void uploadLogFile(int execId, String name, int attempt, File... files)
          throws ExecutorManagerException;

  void uploadLogPath(int execId, String name, int attempt, String hdfsPath)
          throws ExecutorManagerException;

  void uploadAttachmentFile(ExecutableNode node, File file)
          throws ExecutorManagerException;

  void updateExecutableFlow(ExecutableFlow flow)
          throws ExecutorManagerException;

  int updateExecutableFlowRunDate(ExecutableFlow flow) throws SQLException;

  void uploadExecutableNode(ExecutableNode node, Props inputParams)
          throws ExecutorManagerException;

  List<ExecutableJobInfo> fetchJobInfoAttempts(int execId, String jobId)
          throws ExecutorManagerException;

  ExecutableJobInfo fetchJobInfo(int execId, String jobId, int attempt)
          throws ExecutorManagerException;

  List<ExecutableJobInfo> fetchJobHistory(int projectId, String jobId,
                                          int skip, int size) throws ExecutorManagerException;

  List<ExecutableJobInfo> fetchDiagnosisJob(long endTime) throws ExecutorManagerException;

  List<ExecutableJobInfo> fetchQuickSearchJobExecutions(int projectId, String jobId,
                                                        String searchTerm, int skip, int size) throws ExecutorManagerException;

  List<ExecutableJobInfo> searchJobExecutions(HistoryQueryParam historyQueryParam, int skip, int size)
          throws ExecutorManagerException;

  List<ExecutableJobInfo> fetchJobAllHistory(int projectId, String jobId)
          throws ExecutorManagerException;

  List<ExecutableJobInfo> fetchExecutableJobInfo(final long startTime)
          throws ExecutorManagerException;

  void updateExecutableNode(ExecutableNode node)
          throws ExecutorManagerException;

  void updateExecutableNodeStatus(ExecutableNode node)
          throws ExecutorManagerException;

  int fetchNumExecutableFlows(int projectId, String flowId)
          throws ExecutorManagerException;

  int fetchNumExecutableFlows() throws ExecutorManagerException;

  int fetchNumExecutableNodes(int projectId, String jobId)
          throws ExecutorManagerException;

  int quickSearchNumberOfJobExecutions(int projectId, String jobId, String searchTerm)
          throws ExecutorManagerException;

  int searchNumberOfJobExecutions(HistoryQueryParam historyQueryParam)
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
                                                           String flowContains, String execIdContain, String userNameContains, String status,
                                                           long startData,
                                                           long endData, String runDate, int skip, int num, int flowType)
          throws ExecutorManagerException;

  List<ExecutableFlow> fetchUserFlowHistoryByAdvanceFilter(String projContain,
                                                           String flowContains, String execIdContain, String userNameContains, String status,
                                                           long startData, long endData, String subsystem, String busPath, String department,
                                                           String runDate, int skip, int num, int flowType)
          throws ExecutorManagerException;

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
  int getExecHistoryTotal(final HistoryQueryParam param) throws ExecutorManagerException;

  int getExecHistoryTotal(HistoryQueryParam param, List<Integer> projectIds) throws ExecutorManagerException;

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
  int getUserExecHistoryTotal(HistoryQueryParam param, String loginUser) throws ExecutorManagerException;

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
                                            String flowContains, String execIdContain, String userNameContains, String status,
                                            long startData,
                                            long endData, String runDate, int skip, int num, int flowType)
          throws ExecutorManagerException;

  List<ExecutableFlow> fetchUserFlowHistory(String loginUser, HistoryQueryParam param, int skip,
                                            int size) throws ExecutorManagerException;

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

  public int updateUserVariable(UserVariable userVariable) throws Exception;

  public List<UserVariable> fetchAllUserVariable(UserVariable userVariable) throws ExecutorManagerException;

  public UserVariable getUserVariableById(Integer id) throws ExecutorManagerException;
  public Map<String, String> getUserVariableByName(String userName) throws ExecutorManagerException;

  public Integer findWtssUserByName(String name) throws ExecutorManagerException;

  public Integer getWtssUserTotal() throws ExecutorManagerException;
  public List<WtssUser> findAllWtssUserPageList(String searchName, int pageNum, int pageSize) throws ExecutorManagerException;

  int getExecutionCycleTotal(Optional<String> usernameOp) throws ExecutorManagerException;

  int getExecutionCycleAllTotal(String userName, String searchTerm, HashMap<String, String> queryMap) throws ExecutorManagerException;

  List<ExecutionCycle> getExecutionCycleAllPages(String userName,String searchTerm,int offset, int length,HashMap<String, String> queryMap)throws ExecutorManagerException;

  int getExecutionCycleTotal(String username, List<Integer> projectIds) throws ExecutorManagerException;

  List<ExecutionCycle> listExecutionCycleFlows(Optional<String> username, int offset, int length) throws ExecutorManagerException;

  List<ExecutionCycle> listExecutionCycleFlows(String username, List<Integer> projectIds, int offset, int length) throws ExecutorManagerException;

  int saveExecutionCycleFlow(ExecutionCycle cycleFlow) throws ExecutorManagerException;

  ExecutionCycle getExecutionCycleFlow(String projectId, String flowId) throws ExecutorManagerException;

  ExecutionCycle getExecutionCycleFlowDescId(String projectId, String flowId) throws ExecutorManagerException;

  ExecutionCycle getExecutionCycleFlow(int id) throws ExecutorManagerException;

  int updateExecutionFlow(ExecutionCycle cycleFlow) throws ExecutorManagerException;

  int stopAllCycleFlows() throws ExecutorManagerException;

  List<ExecutionCycle> getAllRunningCycleFlows() throws ExecutorManagerException;

  List<UserVariable> fetchAllUserVariableByOwnerDepartment(Integer departmentId) throws ExecutorManagerException;

  UserVariable findUserVariableByKey(String key) throws ExecutorManagerException;

  boolean fetchGroupScheduleSwitch(String submitUser) throws ExecutorManagerException;

  List<Integer> getRunningExecByLock(Integer projectName, String flowId) throws ExecutorManagerException;

  Set<DmsBusPath> getDmsBusPathFromDb(String jobCode);


  /**
   * 获取更新时间大于updateTime的关键路径信息
   * @param jobCode
   * @param updateTime
   * @return
   */
  Set<DmsBusPath> getDmsBusPathFromDb(String jobCode, String updateTime);

  void insertOrUpdate(DmsBusPath dmsBusPath);

  String getEventType(String topic, String msgName);

  void addHoldBatchOpr(String id, int oprType, int oprLevel, String user, long createTime, String oprData)
          throws ExecutorManagerException;

  void addHoldBatchAlert(String batchId, ExecutableFlow executableFlow, int resumeStatus) throws ExecutorManagerException;

  List<HoldBatchOperate> getLocalHoldBatchOpr() throws ExecutorManagerException;

  List<HoldBatchAlert> queryAlertByBatch(String batchId) throws ExecutorManagerException;

  void updateHoldBatchAlertStatus(HoldBatchAlert holdBatchAlert) throws ExecutorManagerException;

  List<Pair<ExecutionReference, ExecutableFlow>> fetchFlowByStatus(Status status)
          throws ExecutorManagerException;

  void linkJobHook(String jobCode, String prefixRules, String suffixRules, String username)
          throws SQLException;

  JobHook getJobHook(String jobCode);

  HoldBatchAlert queryBatchExecutableFlows(long id)
          throws ExecutorManagerException;

  HoldBatchAlert querySubmittedExecutableFlows(long id)
          throws ExecutorManagerException;

  void updateHoldBatchResumeStatus(String projectName, String flowName)
          throws ExecutorManagerException;

  void addHoldBatchResume(String batchId, String oprData, String user)
          throws ExecutorManagerException;

  void updateHoldBatchStatus(String batchId, int status)
          throws ExecutorManagerException;

  String getLocalHoldBatchResume(String batchId) throws ExecutorManagerException;

  void addHoldBatchFrequent(String batchId, ExecutableFlow executableFlow) throws ExecutorManagerException;

  List<HoldBatchAlert> queryExecByBatch(String batchId)
          throws ExecutorManagerException;

  List<HoldBatchAlert> queryFrequentByBatch(String batchId)
          throws ExecutorManagerException;

  void updateHoldBatchFrequentStatus(HoldBatchAlert holdBatchAlert)
          throws ExecutorManagerException;

  List<HoldBatchAlert> queryExecingByBatch(String batchId)
          throws ExecutorManagerException;

  void updateHoldBatchResumeStatus(HoldBatchAlert holdBatchAlert)
          throws ExecutorManagerException;

  void updateHoldBatchExpired(String batchId) throws ExecutorManagerException;

  void updateHoldBatchId(String batchId) throws ExecutorManagerException;

  List<HoldBatchOperate> getMissResumeBatch() throws ExecutorManagerException;

  List<Integer> queryWaitingFlow(String project, String flow);

  HoldBatchAlert getHoldBatchAlert(long id);

  List<HoldBatchAlert> queryWaitingAlert();

  List<CfgWebankOrganization> fetchAllDepartment() throws ExecutorManagerException;

  void updateHoldBatchNotResumeByExecId(int execId);

  List<ExecutionRecover> getUserHistoryRerunConfiguration(int projectId, String flowName, String userId, int start, int size) throws ExecutorManagerException;

  List<ExecutionRecover> getMaintainedHistoryRerunConfiguration(int id, String flow, String userId, int start, int size) throws ExecutorManagerException;

  List<ExecutionRecover> getAllHistoryRerunConfiguration(int id, String flow, int start, int size) throws ExecutorManagerException;

  int getAllExecutionRecoverTotal(int projectId, String flowName) throws ExecutorManagerException;

  int getMaintainedExecutionRecoverTotal(int projectId, String flowName, String userId) throws ExecutorManagerException;

  int getUserExecutionRecoverTotal(int projectId, String flowName, String userId) throws ExecutorManagerException;

  long getFinalScheduleTime(long triggerInitTime);

  List<ExecutableFlow> fetchUnfinishedFlows(ExecutingQueryParam executingQueryParam) throws ExecutorManagerException;

  List<Integer> selectUnfinishedFlows(final int projectId, final String flowId) throws ExecutorManagerException;

  List<Integer> selectUnfinishedFlows() throws ExecutorManagerException;

  long getAllUnfinishedFlows() throws ExecutorManagerException;

  long getUnfinishedFlowsTotal(ExecutingQueryParam executingQueryParam) throws ExecutorManagerException;

  /**
   * 快速搜索FLowExecutions
   * @param projectId
   * @param flowId
   * @param from
   * @param length
   * @param searchTerm
   * @return
   * @throws ExecutorManagerException
   */
  List<ExecutableFlow> quickSearchFlowExecutions(int projectId, String flowId, int from, int length, String searchTerm) throws ExecutorManagerException;

  /**
   * 快速搜索FlowExecutions总数
   * @param projectId
   * @param flowId
   * @param searchTerm
   * @return
   * @throws ExecutorManagerException
   */
  int fetchQuickSearchNumExecutableFlows(int projectId, String flowId, String searchTerm) throws ExecutorManagerException;

  /**
   * 根据用户搜索FlowExecution
   * @param projectId
   * @param flowId
   * @param from
   * @param length
   * @param searchTerm
   * @param userId
   * @return
   * @throws ExecutorManagerException
   */
  List<ExecutableFlow> userQuickSearchFlowExecutions(int projectId, String flowId, int from, int length, String searchTerm, String userId) throws ExecutorManagerException;

  /**
   * 根据用户搜索FlowExecutions总数
   * @param projectId
   * @param flowId
   * @param searchTerm
   * @param userId
   * @return
   * @throws ExecutorManagerException
   */
  int fetchUserQuickSearchNumExecutableFlows(int projectId, String flowId, String searchTerm, String userId) throws ExecutorManagerException;


  /**
   * This method is used to get flow ids fetched in Queue. Flows can be in queue in ready, dispatching
   * or preparing state while in queue. That is why it is expecting status in parameter.
   *
   * @param status
   * @return
   * @throws ExecutorManagerException
   */
  List<Integer> selectQueuedFlows(Status status)
          throws ExecutorManagerException;

  /**
   * 根据主机名查询主机信息
   * @param hostname 主机名
   * @return
   * @throws ExecutorManagerException
   */
  Hosts getHostConfigByHostname(String hostname) throws ExecutorManagerException;

  /**
   * 插入host配置，返回执行器id
   * @param hosts
   * @return
   * @throws ExecutorManagerException
   */
  int insertHostsConfig(Hosts hosts) throws ExecutorManagerException;

  int executorOffline(int executorid) throws ExecutorManagerException;

  int executorOnline(int executorid) throws ExecutorManagerException;

  boolean checkIsOnline(int executorid) throws ExecutorManagerException;

  List<ExecutableFlow> getFlowTodayHistory(int projectId, String flowId) throws ExecutorManagerException;

  JobPredictionExecutionInfo fetchJobPredictionExecutionInfo(final int projectId, final String flowId, final String jobId)
          throws ExecutorManagerException;

  List<JobPredictionExecutionInfo> fetchJobPredictionExecutionInfoList(final int projectId, final String flowId)
          throws ExecutorManagerException;

  void deleteExecutionCycle(int projectId, String flowId);

  List<ExecutionCycle> getRunningCycleFlows(Integer projectId, String flowId);
}
