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

package azkaban.executor;

import azkaban.history.ExecutionRecover;
import azkaban.log.LogFilterEntity;
import azkaban.project.Project;
import azkaban.system.SystemUserManagerException;
import azkaban.user.User;
import azkaban.utils.FileIOUtils.JobMetaData;
import azkaban.utils.FileIOUtils.LogData;
import azkaban.utils.Pair;
import azkaban.utils.Props;
import java.io.IOException;
import java.lang.Thread.State;
import java.sql.SQLException;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

public interface ExecutorManagerAdapter {

  public boolean checkExecutorStatus(int id);

  public List<Integer> fetchPermissionsProjectId(String user);

  public Props getAzkabanProps();

  public boolean isFlowRunning(int projectId, String flowId);

  public ExecutableFlow getExecutableFlow(int execId)
          throws ExecutorManagerException;

  public List<ExecutableFlow> getExecutableFlowByRepeatId(int repeatId)
          throws ExecutorManagerException;

  public List<Integer> getRunningFlows(int projectId, String flowId);

  public List<Integer> getRunningFlows();

  public long getQueuedFlowSize();

  /**
   * <pre>
   * Returns All running with executors and queued flows
   * Note, returns empty list if there isn't any running or queued flows
   * </pre>
   */
  public List<Pair<ExecutableFlow, Optional<Executor>>> getActiveFlowsWithExecutor()
          throws IOException;

  public List<ExecutableFlow> getRecentlyFinishedFlows();

  public List<ExecutableFlow> getExecutableFlows(Project project,
                                                 String flowId, int skip, int size) throws ExecutorManagerException;

  public List<ExecutableFlow> getExecutableFlows(int skip, int size)
          throws ExecutorManagerException;

  List<ExecutableFlow> getMaintainedExecutableFlows(String userType, String username, List<Integer> projectIds, int skip, int size)
          throws ExecutorManagerException;

  public List<ExecutableFlow> getExecutableFlowsQuickSearch(String flowIdContains,
                                                            int skip, int size) throws ExecutorManagerException;

  public List<ExecutableFlow> getMaintainedFlowsQuickSearch(String flowIdContains,
                                                            int skip, int size, String username, List<Integer> projectIds) throws ExecutorManagerException;

  public List<ExecutableFlow> getExecutableFlows(String projContain, String flowContain,
                                                 String execIdContain, String userContain, String status, long begin, long end, String runDate,
                                                 int skip, int size, int flowType) throws ExecutorManagerException;

  public List<CfgWebankOrganization> getAllDepartment() throws ExecutorManagerException;

  public List<ExecutableFlow> getExecutableFlows(HistoryQueryParam param, int skip, int size) throws ExecutorManagerException;

  List<ExecutableFlow> getMaintainedExecutableFlows(String projContain, String flowContain,
                                                    String execIdContain, String userContain, String status, long begin, long end, String runDate,
                                                    int skip, int size, int flowType, String username, List<Integer> projectIds) throws ExecutorManagerException;

  List<ExecutableFlow> getMaintainedExecutableFlows(HistoryQueryParam param, int skip, int size, List<Integer> projectIds) throws ExecutorManagerException;

  public int getExecutableFlows(int projectId, String flowId, int from,
                                int length, List<ExecutableFlow> outputList)
          throws ExecutorManagerException;

  /**
   * 快速查询FlowExecutions
   *
   * @param projectId
   * @param flowId
   * @param from
   * @param length
   * @param searchTerm 搜索项
   * @param exFlows
   * @return
   * @throws ExecutorManagerException
   */
  int quickSearchFlowExecutions(int projectId, String flowId, int from, int length,
                                String searchTerm,List<ExecutableFlow> exFlows)
          throws ExecutorManagerException;

  /**
   * 根据用户查询有权限的FlowExecutions
   *
   * @param projectId
   * @param flowId
   * @param from
   * @param length
   * @param searchTerm
   * @param exFlows
   * @param userId
   * @return
   * @throws ExecutorManagerException
   */
  int userQuickSearchFlowExecutions(int projectId, String flowId, int from, int length,
                                    String searchTerm,List<ExecutableFlow> exFlows, String userId)
          throws ExecutorManagerException;

  List<ExecutableFlow> getExecutableFlows(int projectId, String flowId)
          throws ExecutorManagerException;

  public List<ExecutableFlow> getExecutableFlows(int projectId, String flowId,
                                                 int from, int length, Status status) throws ExecutorManagerException;

  public List<ExecutableJobInfo> getExecutableJobs(Project project,
                                                   String jobId, int skip, int size) throws ExecutorManagerException;

  List<ExecutableJobInfo> getDiagnosisJobs(long endTime) throws ExecutorManagerException;

  /**
   * 快速查询JobExecutions
   *
   * @param project
   * @param jobId
   * @param searchTerm
   * @param skip
   * @param size
   * @return
   * @throws ExecutorManagerException
   */
  public List<ExecutableJobInfo> quickSearchJobExecutions(Project project,
                                                          String jobId, String searchTerm, int skip, int size) throws ExecutorManagerException;

  /**
   * 高级查询JobExecutions
   *
   * @param historyQueryParam
   * @param skip
   * @param size
   * @return
   * @throws ExecutorManagerException
   */
  public List<ExecutableJobInfo> searchJobExecutions (HistoryQueryParam historyQueryParam, int skip, int size)
          throws ExecutorManagerException;

  ExecutableJobInfo getLastFailedJob(final Project project, final String jobId)
          throws ExecutorManagerException;

  LogData getJobLogDataByJobId(int execId, String name, int attempt)
          throws ExecutorManagerException;

  public long getExecutableJobsMoyenneRunTime(Project project,
                                              String jobId) throws ExecutorManagerException;

  public int getNumberOfJobExecutions(Project project, String jobId)
          throws ExecutorManagerException;

  /**
   * 快速查询JobExectuion总数
   * @param project
   * @param jobId
   * @param searchTerm
   * @return
   * @throws ExecutorManagerException
   */
  public int quickSearchNumberOfJobExecutions(Project project, String jobId, String searchTerm)
          throws ExecutorManagerException;

  /**
   * 高级搜索JobExecutions总数
   * @param historyQueryParam
   * @return
   * @throws ExecutorManagerException
   */
  public int searchNumberOfJobExecutions(HistoryQueryParam historyQueryParam)
          throws ExecutorManagerException;

  public int getNumberOfExecutions(Project project, String flowId)
          throws ExecutorManagerException;

  public LogData getExecutableFlowLog(ExecutableFlow exFlow, int offset,
                                      int length) throws ExecutorManagerException, IOException;

  public LogData getExecutionJobLog(ExecutableFlow exFlow, String jobId,
                                    int offset, int length, int attempt) throws ExecutorManagerException, IOException;

  public Long getLatestLogOffset(ExecutableFlow exFlow, String jobId,
                                 Long length, int attempt, User user)
          throws ExecutorManagerException, IOException;

  public List<Object> getExecutionJobStats(ExecutableFlow exflow, String jobId,
                                           int attempt) throws ExecutorManagerException;

  public String getJobLinkUrl(ExecutableFlow exFlow, String jobId, int attempt);

  public JobMetaData getExecutionJobMetaData(ExecutableFlow exFlow,
                                             String jobId, int offset, int length, int attempt)
          throws ExecutorManagerException;

  public void cancelFlow(ExecutableFlow exFlow, String userId)
          throws ExecutorManagerException;

  /**
   * 强制在内存中杀死工作流，没有真实操作执行节点
   * @param exFlow
   * @param userId
   * @throws ExecutorManagerException
   */
  public void forceCancelFlow(ExecutableFlow exFlow, String userId)
          throws ExecutorManagerException;

  public void superKillFlow(ExecutableFlow exFlow, String userId)
          throws ExecutorManagerException;

  public void resumeFlow(ExecutableFlow exFlow, String userId)
          throws ExecutorManagerException;

  /**
   *  设置flow失败
   * @param exFlow
   * @param userId
   * @param params
   * @throws Exception
   */
  public void setFlowFailed(ExecutableFlow exFlow, String userId, List<Pair<String, String>> params)
          throws Exception;

  /**
   *  flow失败暂停设置 重试指定的失败job
   * @param exFlow
   * @param userId
   * @param request
   * @throws Exception
   */
  public String retryFailedJobs(ExecutableFlow exFlow, String userId, String request)
          throws Exception;

  /**
   *  设置job状态为disabled
   * @param exFlow
   * @param userId
   * @param request
   * @throws Exception
   */
  public String setJobDisabled(ExecutableFlow exFlow, String userId, String request)
          throws Exception;

  /**
   * 设置 Job 状态为 failed
   *
   * @param exFlow
   * @param userId
   * @return
   */
  String setJobFailed(ExecutableFlow exFlow, String userId, List<Pair<String, String>> paramList)
          throws Exception;

  /**
   *  设置job状态为Open
   * @param exFlow
   * @param userId
   * @param request
   * @throws Exception
   */
  public String setJobOpen(ExecutableFlow exFlow, String userId, String request)
          throws Exception;

  /**
   *  设置跳过执行失败的jobs
   * @param exFlow
   * @param userId
   * @param request
   * @throws Exception
   */
  public String skipFailedJobs(ExecutableFlow exFlow, String userId, String request)
          throws Exception;

  public void pauseFlow(ExecutableFlow exFlow, String userId, long timeoutMs)
          throws ExecutorManagerException;

  public void pauseExecutingJobs(ExecutableFlow exFlow, String userId,
                                 String... jobIds) throws ExecutorManagerException;

  public void resumeExecutingJobs(ExecutableFlow exFlow, String userId,
                                  String... jobIds) throws ExecutorManagerException;

  public void retryFailures(ExecutableFlow exFlow, String userId, String retryJson)
          throws ExecutorManagerException;

  //跳过所有FAILED_WAITING 状态job
  public void skipAllFailures(ExecutableFlow exFlow, String userId)
          throws ExecutorManagerException;

  String retryExecutingJobs(ExecutableFlow exFlow, String userId,
                            String... jobIds) throws ExecutorManagerException;

  public void disableExecutingJobs(ExecutableFlow exFlow, String userId,
                                   String... jobIds) throws ExecutorManagerException;

  public void enableExecutingJobs(ExecutableFlow exFlow, String userId,
                                  String... jobIds) throws ExecutorManagerException;

  public void cancelExecutingJobs(ExecutableFlow exFlow, String userId,
                                  String... jobIds) throws ExecutorManagerException;

  public String submitExecutableFlow(ExecutableFlow exflow, String userId)
          throws ExecutorManagerException, SystemUserManagerException;

  public void submitExecutableFlow(final ExecutableFlow exflow, final String userId, Map<String,Object> result)
          throws ExecutorManagerException, SystemUserManagerException;

  /**
   * Manage servlet call for stats servlet in Azkaban execution server Action can take any of the
   * following values <ul> <li>{@link azkaban.executor.ConnectorParams#STATS_SET_REPORTINGINTERVAL}<li>
   * <li>{@link azkaban.executor.ConnectorParams#STATS_SET_CLEANINGINTERVAL}<li> <li>{@link
   * azkaban.executor.ConnectorParams#STATS_SET_MAXREPORTERPOINTS}<li> <li>{@link
   * azkaban.executor.ConnectorParams#STATS_GET_ALLMETRICSNAME}<li> <li>{@link
   * azkaban.executor.ConnectorParams#STATS_GET_METRICHISTORY}<li> <li>{@link
   * azkaban.executor.ConnectorParams#STATS_SET_ENABLEMETRICS}<li> <li>{@link
   * azkaban.executor.ConnectorParams#STATS_SET_DISABLEMETRICS}<li> </ul>
   */
  public Map<String, Object> callExecutorStats(int executorId, String action,
                                               Pair<String, String>... param) throws IOException, ExecutorManagerException;

  public Map<String, Object> callExecutorJMX(String hostPort, String action,
                                             String mBean) throws IOException;

  public void start() throws ExecutorManagerException;

  public void shutdown();

  public Set<String> getAllActiveExecutorServerHosts();

  public State getExecutorManagerThreadState();

  public boolean isExecutorManagerThreadActive();

  public long getLastExecutorManagerThreadCheckTime();

  public Set<? extends String> getPrimaryServerHosts();

  /**
   * Returns a collection of all the active executors maintained by active executors
   */
  public Collection<Executor> getAllActiveExecutors();

  /**
   * <pre>
   * Fetch executor from executors with a given executorId
   * Note:
   * 1. throws an Exception in case of a SQL issue
   * 2. return null when no executor is found with the given executorId
   * </pre>
   */
  public Executor fetchExecutor(int executorId) throws ExecutorManagerException;

  /**
   * <pre>
   * Setup activeExecutors using azkaban.properties and database executors
   * Note:
   * 1. If azkaban.use.multiple.executors is set true, this method will
   *    load all active executors
   * 2. In local mode, If a local executor is specified and it is missing from db,
   *    this method add local executor as active in DB
   * 3. In local mode, If a local executor is specified and it is marked inactive in db,
   *    this method will convert local executor as active in DB
   * </pre>
   */
  public void setupExecutors() throws ExecutorManagerException;

  /**
   * Enable flow dispatching in QueueProcessor
   */
  public void enableQueueProcessorThread() throws ExecutorManagerException;

  /**
   * Disable flow dispatching in QueueProcessor
   */
  public void disableQueueProcessorThread() throws ExecutorManagerException;

  public String getAllExecutionJobLog(ExecutableFlow exFlow, String jobId, int attempt)
          throws ExecutorManagerException, IOException;

  public List<ExecutableFlow> getUserExecutableFlows(int skip, int size, String user)
          throws ExecutorManagerException;

  public List<ExecutableFlow> getUserExecutableFlowsByAdvanceFilter(String projContain,
                                                                    String flowContain, String execIdContain, String userContain, String status, long begin,
                                                                    long end, String runDate,
                                                                    int skip, int size, int flowType) throws ExecutorManagerException;

  public List<ExecutableFlow> getUserExecutableFlowsByAdvanceFilter(String projContain,
                                                                    String flowContain, String execIdContain, String userContain, String status, long begin,
                                                                    long end, String subsystem, String busPath, String department, String runDate,
                                                                    int skip, int size, int flowType) throws ExecutorManagerException;

  public List<ExecutableFlow> getUserExecutableFlowsQuickSearch(String flowIdContains, String user,
                                                                int skip, int size) throws ExecutorManagerException;

  /**
   * 查找所有的历史补采记录
   * @param userNameContains
   * @return
   * @throws ExecutorManagerException
   */
  public List<ExecutableFlow> getHistoryRecoverExecutableFlows(final String userNameContains) throws ExecutorManagerException;

  /**
   * 根据历史补采ID 查找对应的补采记录
   * @param repeatId
   * @return
   * @throws ExecutorManagerException
   */
  public ExecutableFlow getHistoryRecoverExecutableFlowsByRepeatId(final String repeatId) throws ExecutorManagerException;

  /**
   * 根据历史补采ID 设置该补采流程停止
   * @param repeatId
   * @throws ExecutorManagerException
   */
  public void stopHistoryRecoverExecutableFlowByRepeatId(final String repeatId)
          throws ExecutorManagerException;


  public ExecutableFlow getHistoryRecoverExecutableFlowsByFlowId(final String flowId, final String projectId)
          throws ExecutorManagerException;

  /**
   * 分页查找历史补采记录
   * @param paramMap
   * @param skip
   * @param size
   * @return
   * @throws ExecutorManagerException
   */
  public List<ExecutionRecover> listHistoryRecoverFlows(final Map paramMap, int skip, int size)
          throws ExecutorManagerException;

  /**
   * 分页查询运维管理员运维的所有历史补采记录
   * @param projectIds 运维管理员运维的所有工程的ID
   * @param skip
   * @param size
   * @return
   * @throws ExecutorManagerException
   */
  List<ExecutionRecover> listMaintainedHistoryRecoverFlows(String username, List<Integer> projectIds, int skip, int size)
          throws ExecutorManagerException;
  /**
   * 创建一个新的历史补采记录
   * @param executionRecover
   * @return
   * @throws ExecutorManagerException
   */
  Integer saveHistoryRecoverFlow(final ExecutionRecover executionRecover)
          throws ExecutorManagerException;

  /**
   * 更新历史补采记录
   * @param executionRecover
   * @throws ExecutorManagerException
   */
  void updateHistoryRecover(final ExecutionRecover executionRecover)
          throws ExecutorManagerException;

  /**
   * 根据历史补采ID 获取历史补采信息
   * @param recoverId
   * @return
   * @throws ExecutorManagerException
   */
  ExecutionRecover getHistoryRecoverFlow(final Integer recoverId)
          throws ExecutorManagerException;

  /**
   * 根据项目ID和工作流ID 查找正在运行的历史补采
   * @param projectId
   * @param flowId
   * @return
   * @throws ExecutorManagerException
   */
  ExecutionRecover getHistoryRecoverFlowByPidAndFid(final String projectId, final String flowId)
          throws ExecutorManagerException;

  /**
   * 获取未完成的历史补采
   * @return
   * @throws ExecutorManagerException
   */
  List<ExecutionRecover> listHistoryRecoverRunnning(final Integer loadSize) throws ExecutorManagerException;

  /**
   * 获取历史重跑的总数
   * @return
   * @throws ExecutorManagerException
   */
  int getHistoryRecoverTotal() throws ExecutorManagerException;

  /**
   *
   * @param projectId
   * @param flowId
   * @return
   * @throws ExecutorManagerException
   */
  ExecutableFlow getProjectLastExecutableFlow(int projectId, String flowId) throws ExecutorManagerException;

  /**
   * 获取用户历史重跑的总数
   * @return
   * @throws ExecutorManagerException
   */
  int getUserHistoryRecoverTotal(String userName) throws ExecutorManagerException;

  /**
   * 获取运维管理者历史重跑总数
   * @param maintainedProjectIds 运维管理者运维的所有工程ID
   * @return
   * @throws ExecutorManagerException
   */
  int getMaintainedHistoryRecoverTotal(String username, List<Integer> maintainedProjectIds) throws ExecutorManagerException;

  /**
   * 根据执行ID获取所有执行日志压缩包地址
   * @return
   * @throws ExecutorManagerException
   */
  String getDownLoadAllExecutionLog(ExecutableFlow executableFlow) throws ExecutorManagerException;

  /**
   * 根据 Job ID 获取 Job日志 压缩包地址
   * @return
   * @throws ExecutorManagerException
   */
  String getJobLogByJobId(int execId, String jobName) throws ExecutorManagerException;


  /**
   * 获取所有的日志过滤条件
   * @return
   * @throws ExecutorManagerException
   */
  List<LogFilterEntity> listAllLogFilter() throws ExecutorManagerException;


  /**
   * 根据条件获取历史记录总条数
   * @param param
   * @return
   * @throws ExecutorManagerException
   */
  public int getExecHistoryTotal(final HistoryQueryParam param)
          throws ExecutorManagerException;


  int getExecHistoryTotal(HistoryQueryParam param, List<Integer> projectIds)
          throws ExecutorManagerException;

  /**
   * 根据工程ID查询历史记录总条数
   * @param projectIds
   * @return
   * @throws ExecutorManagerException
   */
  int getMaintainedExecHistoryTotal(String username, List<Integer> projectIds)
          throws ExecutorManagerException;

  /**
   * 根据条件获取历史记录总条数
   * @param filterMap
   * @return
   * @throws ExecutorManagerException
   */
  public int getExecHistoryQuickSerachTotal(final Map<String, String> filterMap)
          throws ExecutorManagerException;

  public int getMaintainedFlowsQuickSearchTotal(String username, final Map<String, String> filterMap, List<Integer> projectIds)
          throws ExecutorManagerException;


  /**
   *
   * @param projectId
   * @param flowId
   * @param from
   * @param length
   * @param outputList
   * @return
   * @throws ExecutorManagerException
   */
  public int getUserExecutableFlowsTotalByProjectIdAndFlowId(int projectId, String flowId, int from,
                                                             int length, List<ExecutableFlow> outputList, final String userName)
          throws ExecutorManagerException;



  public long getExecutableFlowsMoyenneRunTime(int projectId, String flowId, String user)
          throws ExecutorManagerException;

  /**
   * 根据条件获取用户历史记录总条数
   * @param param
   * @return
   * @throws ExecutorManagerException
   */
  public int getUserExecHistoryTotal(HistoryQueryParam param, String loginUser)
          throws ExecutorManagerException;

  /**
   * 根据条件获取历史记录总条数
   * @param filterMap
   * @return
   * @throws ExecutorManagerException
   */
  public int getUserExecHistoryQuickSerachTotal(final Map<String, String> filterMap)
          throws ExecutorManagerException;

  /**
   * 根据登录用户条件获取历史记录总条数
   * @return
   * @throws ExecutorManagerException
   */
  public List<ExecutableFlow> getUserExecutableFlows(String loginUser, String projContain,
                                                     String flowContain, String execIdContain, String userContain, String status, long begin,
                                                     long end, String runDate,
                                                     int skip, int size, int flowType) throws ExecutorManagerException;

  public List<ExecutableFlow> getUserExecutableFlows(String loginUser, HistoryQueryParam param,
                                                     int skip, int size) throws ExecutorManagerException;

  /**
   *
   * @param userName
   * @return
   * @throws ExecutorManagerException
   */
  public List<ExecutableFlow> getTodayExecutableFlowData(final String userName) throws ExecutorManagerException;

  /**
   *
   * @param userName
   * @return
   * @throws ExecutorManagerException
   */
  public List<ExecutableFlow> getTodayExecutableFlowDataNew(final String userName) throws ExecutorManagerException;

  /**
   * 获取定时调度任务今天运行次数
   * @param flowId
   * @return
   * @throws ExecutorManagerException
   */
  public Integer getTodayFlowRunTimesByFlowId(final String projectId, final String flowId, final String usename) throws ExecutorManagerException;


  /**
   *
   * @param userName
   * @return
   * @throws ExecutorManagerException
   */
  public List<ExecutableFlow> getRealTimeExecFlowData(final String userName) throws ExecutorManagerException;

  /**
   *
   * @return
   * @throws ExecutorManagerException
   */
  public ExecutableFlow getRecentExecutableFlow(final int projectId, final String flowId)
          throws ExecutorManagerException;

  /**
   * 获取正在执行的工作流数据集合
   * @param executingQueryParam
   */
  List<Map<String, String>> getExectingFlowsData(ExecutingQueryParam executingQueryParam) throws IOException;

  List<ExecutableFlow> fetchAllExecutableFlow() throws SQLException;

  List<ExecutableFlow> fetchExecutableFlows(final long startTime) throws SQLException;

  List<ExecutableJobInfo> fetchExecutableJobInfo(final long startTime)
          throws ExecutorManagerException;

  int updateExecutableFlow(ExecutableFlow flow) throws SQLException;


  int getExecutionCycleTotal(Optional<String> usernameOp) throws ExecutorManagerException;

  int getExecutionCycleAllTotal(String userName,String searchTerm,HashMap<String, String> queryMap) throws ExecutorManagerException;

  List<ExecutionCycle> getExecutionCycleAllPages(String userName, String searchTerm, int offset, int length, HashMap<String, String> queryMap)throws ExecutorManagerException;

  void deleteExecutionCycle(int projectId, String flowId,User user,Project project)throws ExecutorManagerException;

  int getExecutionCycleTotal(String username, List<Integer> projectIds)
          throws ExecutorManagerException;

  List<ExecutionCycle> listExecutionCycleFlows(Optional<String> usernameOP, int offset, int length)
          throws ExecutorManagerException;

  List<ExecutionCycle> listExecutionCycleFlows(String username, List<Integer> projectIds,
                                               int offset, int length) throws ExecutorManagerException;

  int saveExecutionCycleFlow(ExecutionCycle cycleFlow) throws ExecutorManagerException;

  ExecutionCycle getExecutionCycleFlow(String projectId, String flowId) throws ExecutorManagerException;

  ExecutionCycle getExecutionCycleFlowDescId(String projectId, String flowId) throws ExecutorManagerException;

  ExecutionCycle getExecutionCycleFlow(int id) throws ExecutorManagerException;

  int updateExecutionFlow(ExecutionCycle executionCycle) throws ExecutorManagerException;

  int stopAllCycleFlows() throws ExecutorManagerException;

  List<ExecutionCycle> getAllRunningCycleFlows() throws ExecutorManagerException;

  List<ExecutionCycle> getRunningCycleFlows(Integer projectId,String flowId) throws ExecutorManagerException;

  void reloadWebData();

  String holdBatch(int oprType, int oprLevel, List<String> dataList, List<String> flowList,
                   List<String> busPathList, String batchId, User user, List<String> criticalPathList);

  List<ExecutableFlow> getAllFlows() throws IOException;

  void linkJobHook(String jobCode, String prefixRules, String suffixRules, User user)
          throws SQLException;

  void resumeBatchFlow(long id);

  void stopBatchFlow(long id);

  List<ExecutionRecover> fetchUserHistoryRerunConfiguration(int projectId, String flowName, String userId, int start, int size) throws ExecutorManagerException;

  List<ExecutionRecover> fetchMaintainedHistoryRerunConfiguration(int projectId, String flowName, String userId, int start, int size) throws ExecutorManagerException;

  List<ExecutionRecover> fetchAllHistoryRerunConfiguration(int projectId, String flowName, int start, int size) throws ExecutorManagerException;

  int getAllExecutionRecoverTotal(int projectId, String flowName) throws  ExecutorManagerException;

  int getMaintainedExecutionRecoverTotal(int projectId, String flowName, String userId) throws ExecutorManagerException;

  int getUserExecutionRecoverTotal(int projectId, String flowName, String userId) throws ExecutorManagerException;

  long getExectingFlowsTotal(ExecutingQueryParam executingQueryParam) throws ExecutorManagerException;

  String getExecutorIdByHostname(String hostname) throws ExecutorManagerException;

}
