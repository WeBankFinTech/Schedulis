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
import azkaban.batch.HoldBatchDao;
import azkaban.batch.HoldBatchOperate;
import azkaban.executor.ExecutorLogEvent.EventType;
import azkaban.executor.dao.ExecutionJobDao;
import azkaban.executor.entity.JobPredictionExecutionInfo;
import azkaban.history.ExecutionRecover;
import azkaban.history.ExecutionRecoverDao;
import azkaban.history.RecoverTrigger;
import azkaban.jobhook.JobHook;
import azkaban.log.LogFilterDao;
import azkaban.log.LogFilterEntity;
import azkaban.system.entity.WtssUser;
import azkaban.utils.FileIOUtils.LogData;
import azkaban.utils.Pair;
import azkaban.utils.Props;
import java.io.File;
import java.sql.SQLException;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import javax.inject.Inject;
import javax.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Singleton
public class JdbcExecutorLoader implements ExecutorLoader {

  private static Logger logger = LoggerFactory.getLogger(JdbcExecutorLoader.class);

  private final ExecutionFlowDao executionFlowDao;
  private final ExecutorDao executorDao;
  private final ExecutionJobDao executionJobDao;
  private final ExecutionLogsAdapter executionLogsDao;
  private final ExecutorEventsDao executorEventsDao;
  private final ActiveExecutingFlowsDao activeExecutingFlowsDao;
  private final FetchActiveFlowDao fetchActiveFlowDao;
  private final AssignExecutorDao assignExecutorDao;
  private final NumExecutionsDao numExecutionsDao;
  private final ExecutionRecoverDao executionRecoverDao;
  private final LogFilterDao logFilterDao;
  private final DepartmentGroupDao departmentGroupDao;
  private final UserVariableDao userVariableDao;
  private final ExecutionCycleDao executionCycleDao;
  private final HoldBatchDao holdBatchDao;

  @Inject
  public JdbcExecutorLoader(final ExecutionFlowDao executionFlowDao,
                            final ExecutorDao executorDao,
                            final ExecutionJobDao executionJobDao,
                            final ExecutionLogsAdapter executionLogsDao,
                            final ExecutorEventsDao executorEventsDao,
                            final ActiveExecutingFlowsDao activeExecutingFlowsDao,
                            final FetchActiveFlowDao fetchActiveFlowDao,
                            final AssignExecutorDao assignExecutorDao,
                            final NumExecutionsDao numExecutionsDao,
                            final ExecutionRecoverDao executionRecoverDao,
                            final LogFilterDao logFilterDao,
                            final DepartmentGroupDao departmentGroupDao,
                            final UserVariableDao userVariableDao,
                            final ExecutionCycleDao executionCycleDao, HoldBatchDao holdBatchDao) {
    this.executionFlowDao = executionFlowDao;
    this.executorDao = executorDao;
    this.executionJobDao = executionJobDao;
    this.executionLogsDao = executionLogsDao;
    this.executorEventsDao = executorEventsDao;
    this.activeExecutingFlowsDao = activeExecutingFlowsDao;
    this.fetchActiveFlowDao = fetchActiveFlowDao;
    this.numExecutionsDao = numExecutionsDao;
    this.assignExecutorDao = assignExecutorDao;
    this.executionRecoverDao = executionRecoverDao;
    this.logFilterDao = logFilterDao;
    this.departmentGroupDao = departmentGroupDao;
    this.userVariableDao = userVariableDao;
    this.executionCycleDao = executionCycleDao;
    this.holdBatchDao = holdBatchDao;
  }

  @Override
  public synchronized void uploadExecutableFlow(final ExecutableFlow flow)
          throws ExecutorManagerException {
    this.executionFlowDao.uploadExecutableFlow(flow);
  }

  @Override
  public void updateExecutableFlow(final ExecutableFlow flow)
          throws ExecutorManagerException {
    this.executionFlowDao.updateExecutableFlow(flow);
  }

  @Override
  public int updateExecutableFlowRunDate(ExecutableFlow flow) throws SQLException {
    return this.executionFlowDao.updateExecutableFlowRunDate(flow);
  }

  @Override
  public ExecutableFlow fetchExecutableFlow(final int id)
          throws ExecutorManagerException {
    return this.executionFlowDao.fetchExecutableFlow(id);
  }

  @Override
  public List<ExecutableFlow> fetchExecutableFlowByRepeatId(int repeatId)
          throws ExecutorManagerException {
    return this.executionFlowDao.fetchExecutableFlowByRepeatId(repeatId);
  }

  @Override
  public List<Pair<ExecutionReference, ExecutableFlow>> fetchQueuedFlows()
          throws ExecutorManagerException {
    return this.executionFlowDao.fetchQueuedFlows();
  }

  /**
   * maxAge indicates how long finished flows are shown in Recently Finished flow page.
   */
  @Override
  public List<ExecutableFlow> fetchRecentlyFinishedFlows(final Duration maxAge)
          throws ExecutorManagerException {
    return this.executionFlowDao.fetchRecentlyFinishedFlows(maxAge);
  }

  @Override
  public Map<Integer, Pair<ExecutionReference, ExecutableFlow>> fetchActiveFlows()
          throws ExecutorManagerException {
    return this.fetchActiveFlowDao.fetchActiveFlows();
  }

  @Override
  public Map<Integer, Pair<ExecutionReference, ExecutableFlow>> fetchUnfinishedFlows()
          throws ExecutorManagerException {
    return this.fetchActiveFlowDao.fetchUnfinishedFlows();
  }

  @Override
  public List<ExecutableFlow> fetchAllUnfinishedFlows() throws ExecutorManagerException {
    return this.fetchActiveFlowDao.fetchAllUnFinishFlows();
  }

  @Override
  public List<ExecutableFlow> fetchUnfinishedFlows(ExecutingQueryParam executingQueryParam) throws ExecutorManagerException {
    return this.fetchActiveFlowDao.fetchUnfinishedFlows(executingQueryParam);
  }

  @Override
  public List<Integer> selectUnfinishedFlows(int projectId, String flowId) throws ExecutorManagerException {
    return this.executionFlowDao.selectUnfinishedFlows(projectId, flowId);
  }

  @Override
  public List<Integer> selectUnfinishedFlows() throws ExecutorManagerException {
    return this.executionFlowDao.selectUnfinishedFlows();
  }

  @Override
  public long getAllUnfinishedFlows() throws ExecutorManagerException {
    return this.fetchActiveFlowDao.getAllUnFinishFlowsTotal();
  }

  @Override
  public long getUnfinishedFlowsTotal(ExecutingQueryParam executingQueryParam) throws ExecutorManagerException {
    return this.fetchActiveFlowDao.getUnFinishFlowsTotal(executingQueryParam);
  }

  @Override
  public Map<Integer, Pair<ExecutionReference, ExecutableFlow>> fetchUnfinishedFlowsMetadata()
          throws ExecutorManagerException {
    return this.fetchActiveFlowDao.fetchUnfinishedFlowsMetadata();
  }

  @Override
  public Pair<ExecutionReference, ExecutableFlow> fetchActiveFlowByExecId(final int execId)
          throws ExecutorManagerException {
    return this.fetchActiveFlowDao.fetchActiveFlowByExecId(execId);
  }

  @Override
  public int fetchNumExecutableFlows() throws ExecutorManagerException {
    return this.numExecutionsDao.fetchNumExecutableFlows();
  }

  @Override
  public int fetchNumExecutableFlows(final int projectId, final String flowId)
          throws ExecutorManagerException {
    return this.numExecutionsDao.fetchNumExecutableFlows(projectId, flowId);
  }

  @Override
  public int fetchNumExecutableNodes(final int projectId, final String jobId)
          throws ExecutorManagerException {
    return this.numExecutionsDao.fetchNumExecutableNodes(projectId, jobId);
  }

  @Override
  public int quickSearchNumberOfJobExecutions(final int projectId, final String jobId, String searchTerm)
          throws ExecutorManagerException {
    return this.numExecutionsDao.fetchQuickSearchNumJobExecutions(projectId, jobId, searchTerm);
  }

  @Override
  public int searchNumberOfJobExecutions(HistoryQueryParam historyQueryParam)
          throws ExecutorManagerException {
    return this.numExecutionsDao.searchNumberOfJobExecutions(historyQueryParam);
  }

  @Override
  public List<ExecutableFlow> fetchFlowHistory(final int projectId, final String flowId,
                                               final int skip, final int num) throws ExecutorManagerException {
    return this.executionFlowDao.fetchFlowHistory(projectId, flowId, skip, num);
  }

  @Override
  public List<ExecutableFlow> fetchFlowHistory(final int projectId, final String flowId) throws ExecutorManagerException {
    return this.executionFlowDao.fetchFlowHistory(projectId, flowId);
  }

  public List<ExecutableFlow> fetchFlowHistory(final int projectId, final String flowId,
                                               final long startTime) throws ExecutorManagerException {
    return this.executionFlowDao.fetchFlowHistory(projectId, flowId, startTime);
  }

  @Override
  public List<ExecutableFlow> quickSearchFlowExecutions(final int projectId, final String flowId,
                                                        final int skip, final int num, final String searchTerm)
          throws ExecutorManagerException {
    return this.executionFlowDao.quickSearchFlowExecutions(projectId, flowId, skip, num, searchTerm);
  }

  @Override
  public int fetchQuickSearchNumExecutableFlows(final int projectId, final String flowId, final String searchTerm)
          throws ExecutorManagerException{
    return this.numExecutionsDao.fetchQuickSearchNumExecutableFlows(projectId, flowId, searchTerm);
  }

  @Override
  public List<ExecutableFlow> userQuickSearchFlowExecutions(final int projectId, final String flowId,
                                                            final int skip, final int num, final String searchTerm, final String userId)
          throws ExecutorManagerException{
    return this.executionFlowDao.userQuickSearchFlowExecutions(projectId, flowId, skip, num, searchTerm, userId);
  }

  @Override
  public int fetchUserQuickSearchNumExecutableFlows(final int projectId,final String flowId, final String searchTerm, final String userId)
          throws ExecutorManagerException{
    return this.numExecutionsDao.fetchUserQuickSearchNumExecutableFlows(projectId, flowId, searchTerm, userId);
  }

  @Override
  public List<Integer> selectQueuedFlows(Status status) throws ExecutorManagerException {
    return this.executionFlowDao.selectQueuedFlows(status);
  }

  @Override
  public List<ExecutableFlow> fetchFlowHistory(final int projectId, final String flowId,
                                               final int skip, final int num, final Status status) throws ExecutorManagerException {
    return this.executionFlowDao.fetchFlowHistory(projectId, flowId, skip, num, status);
  }

  @Override
  public List<ExecutableFlow> fetchFlowHistory(final int skip, final int num)
          throws ExecutorManagerException {
    return this.executionFlowDao.fetchFlowHistory(skip, num);
  }

  @Override
  public List<ExecutableFlow> fetchMaintainedFlowHistory(String userType, String username, List<Integer> projectIds, int skip, int size)
          throws ExecutorManagerException {
    return this.executionFlowDao.fetchMaintainedFlowHistory(userType, username, projectIds, skip, size);
  }

  @Override
  public List<ExecutableFlow> fetchFlowHistory(final String projContain,
                                               final String flowContains,
                                               final String execIdContain,
                                               final String userNameContains, final String status,
                                               final long startTime,
                                               final long endTime, String runDate, final int skip,
                                               final int num, final int flowType)
          throws ExecutorManagerException {
    return this.executionFlowDao.fetchFlowHistory(projContain, flowContains, execIdContain,
            userNameContains, status, startTime, endTime, runDate, skip, num, flowType);
  }

  @Override
  public List<ExecutableFlow> fetchFlowHistory(HistoryQueryParam param, int skip, int num) throws ExecutorManagerException {
    return this.executionFlowDao.fetchFlowHistory(param, skip, num);
  }

  @Override
  public List<ExecutableFlow> fetchMaintainedFlowHistory(String projContain, String flowContains,
                                                         String execIdContain, String userNameContains, String status, long startTime,
                                                         long endTime, String runDate, int skip, int num, int flowType, String username,
                                                         List<Integer> projectIds)
          throws ExecutorManagerException {
    return this.executionFlowDao.fetchMaintainedFlowHistory(projContain, flowContains,
            execIdContain,
            userNameContains, status, startTime, endTime, runDate, skip, num, flowType, username,
            projectIds);
  }

  @Override
  public List<ExecutableFlow> fetchMaintainedFlowHistory(HistoryQueryParam param, int skip, int size, List<Integer> projectIds)
          throws ExecutorManagerException {
    return this.executionFlowDao.fetchMaintainedFlowHistory(param, skip, size, projectIds);
  }
  @Override
  public List<ExecutableFlow> fetchFlowHistoryQuickSearch(final String searchContains,
                                                          final String userNameContains, final int skip, final int num) throws ExecutorManagerException {
    return this.executionFlowDao.fetchFlowHistoryQuickSearch(searchContains, userNameContains, skip, num);
  }

  @Override
  public List<ExecutableFlow> fetchFlowHistoryQuickSearch(final String searchContains,
                                                          final String username,
                                                          final int skip, final int num,
                                                          List<Integer> projectIds) throws ExecutorManagerException {
    return this.executionFlowDao.fetchFlowHistoryQuickSearch(searchContains, username, skip, num,
            projectIds);
  }

  @Override
  public List<ExecutableFlow> fetchFlowAllHistory(int projectId, String flowId, String user)
          throws ExecutorManagerException {
    return this.executionFlowDao.fetchFlowAllHistory(projectId, flowId, user);
  }

  @Override
  public List<ExecutableFlow> fetchAllExecutableFlow() throws SQLException {
    return this.executionFlowDao.fetchAllExecutableFlow();
  }

  public List<ExecutableFlow> fetchExecutableFlows(final long startTime) throws SQLException {
    return this.executionFlowDao.fetchExecutableFlows(startTime);
  }

  @Override
  public void addActiveExecutableReference(final ExecutionReference reference)
          throws ExecutorManagerException {

    this.activeExecutingFlowsDao.addActiveExecutableReference(reference);
  }

  @Override
  public void removeActiveExecutableReference(final int execid)
          throws ExecutorManagerException {

    this.activeExecutingFlowsDao.removeActiveExecutableReference(execid);
  }

  @Override
  public boolean updateExecutableReference(final int execId, final long updateTime)
          throws ExecutorManagerException {

    // Should be 1.
    return this.activeExecutingFlowsDao.updateExecutableReference(execId, updateTime);
  }

  @Override
  public void uploadExecutableNode(final ExecutableNode node, final Props inputProps)
          throws ExecutorManagerException {

    this.executionJobDao.uploadExecutableNode(node, inputProps);
  }

  @Override
  public void updateExecutableNode(final ExecutableNode node)
          throws ExecutorManagerException {

    this.executionJobDao.updateExecutableNode(node);
  }

  @Override
  public void updateExecutableNodeStatus(final ExecutableNode node)
          throws ExecutorManagerException {

    this.executionJobDao.updateExecutableNodeStatus(node);
  }

  @Override
  public List<ExecutableJobInfo> fetchJobInfoAttempts(final int execId, final String jobId)
          throws ExecutorManagerException {

    return this.executionJobDao.fetchJobInfoAttempts(execId, jobId);
  }

  @Override
  public ExecutableJobInfo fetchJobInfo(final int execId, final String jobId, final int attempts)
          throws ExecutorManagerException {

    return this.executionJobDao.fetchJobInfo(execId, jobId, attempts);
  }

  @Override
  public Props fetchExecutionJobInputProps(final int execId, final String jobId)
          throws ExecutorManagerException {
    return this.executionJobDao.fetchExecutionJobInputProps(execId, jobId);
  }

  @Override
  public Props fetchExecutionJobOutputProps(final int execId, final String jobId)
          throws ExecutorManagerException {
    return this.executionJobDao.fetchExecutionJobOutputProps(execId, jobId);
  }

  @Override
  public Pair<Props, Props> fetchExecutionJobProps(final int execId, final String jobId)
          throws ExecutorManagerException {
    return this.executionJobDao.fetchExecutionJobProps(execId, jobId);
  }

  @Override
  public List<ExecutableJobInfo> fetchJobHistory(final int projectId, final String jobId,
                                                 final int skip, final int size)
          throws ExecutorManagerException {

    return this.executionJobDao.fetchJobHistory(projectId, jobId, skip, size);
  }

  @Override
  public List<ExecutableJobInfo> fetchDiagnosisJob(long endTime) throws ExecutorManagerException {
    return this.executionJobDao.fetchDiagnosisJob(endTime);
  }

  @Override
  public List<ExecutableJobInfo> fetchQuickSearchJobExecutions(final int projectId, final String jobId,
                                                               final String searchTerm, final int skip, final int size)
          throws ExecutorManagerException {

    return this.executionJobDao.fetchQuickSearchJobExecutions(projectId, jobId, searchTerm, skip, size);
  }

  @Override
  public List<ExecutableJobInfo> searchJobExecutions(HistoryQueryParam historyQueryParam, final int skip, final int size)
          throws ExecutorManagerException {

    return this.executionJobDao.searchJobExecutions(historyQueryParam, skip, size);
  }

  @Override
  public List<ExecutableJobInfo> fetchJobAllHistory(final int projectId, final String jobId)
          throws ExecutorManagerException {

    return this.executionJobDao.fetchJobAllHistory(projectId, jobId);
  }

  @Override
  public List<ExecutableJobInfo> fetchExecutableJobInfo(final long startTime)
          throws ExecutorManagerException {

    return this.executionJobDao.fetchExecutableJobInfo(startTime);
  }

  @Override
  public Long getJobLogOffset(int execId, String jobName, int attempt, Long length) throws ExecutorManagerException {
    Long maxSize = this.executionLogsDao.getJobLogMaxSize(execId, jobName, attempt);
    return maxSize - length > 0? maxSize - length: 0;
  }

  @Override
  public LogData fetchLogs(final int execId, final String name, final int attempt,
                           final int startByte,
                           final int length) throws ExecutorManagerException {

    return this.executionLogsDao.fetchLogs(execId, name, attempt, startByte, length);
  }

  /**
   * 获取日志存放的 HDFS 路径
   *
   * @param execId
   * @param name
   * @param attempt
   * @return
   */
  @Override
  public String getHdfsLogPath(int execId, String name, int attempt)
          throws ExecutorManagerException {
    return this.executionLogsDao.getHdfsLogPath(execId, name, attempt);
  }

  @Override
  public int getLogEncType(int execId, String name, int attempt) throws ExecutorManagerException {
    return this.executionLogsDao.getLogEncType(execId, name, attempt);
  }

  @Override
  public List<Object> fetchAttachments(final int execId, final String jobId, final int attempt)
          throws ExecutorManagerException {

    return this.executionJobDao.fetchAttachments(execId, jobId, attempt);
  }

  @Override
  public void uploadLogFile(final int execId, final String name, final int attempt,
                            final File... files)
          throws ExecutorManagerException {
    this.executionLogsDao.uploadLogFile(execId, name, attempt, files);
  }

  @Override
  public void uploadLogPath(final int execId, final String name, final int attempt,
                            final String hdfsPath) throws ExecutorManagerException {
    this.executionLogsDao.uploadLogPath(execId, name, attempt, hdfsPath);
  }

  @Override
  public void uploadAttachmentFile(final ExecutableNode node, final File file)
          throws ExecutorManagerException {
    this.executionJobDao.uploadAttachmentFile(node, file);
  }

  @Override
  public List<Executor> fetchAllExecutors() throws ExecutorManagerException {
    return this.executorDao.fetchAllExecutors();
  }

  @Override
  public List<Executor> fetchActiveExecutors() throws ExecutorManagerException {
    return this.executorDao.fetchActiveExecutors();
  }

  @Override
  public Executor fetchExecutor(final String host, final int port)
          throws ExecutorManagerException {
    return this.executorDao.fetchExecutor(host, port);
  }

  @Override
  public Executor fetchExecutor(final int executorId) throws ExecutorManagerException {
    return this.executorDao.fetchExecutor(executorId);
  }

  @Override
  public void updateExecutor(final Executor executor) throws ExecutorManagerException {
    this.executorDao.updateExecutor(executor);
  }

  @Override
  public Executor addExecutor(final String host, final int port)
          throws ExecutorManagerException {
    return this.executorDao.addExecutor(host, port);
  }

  @Override
  public void removeExecutor(final String host, final int port) throws ExecutorManagerException {
    this.executorDao.removeExecutor(host, port);
  }

  @Override
  public void postExecutorEvent(final Executor executor, final EventType type, final String user,
                                final String message) throws ExecutorManagerException {

    this.executorEventsDao.postExecutorEvent(executor, type, user, message);
  }

  @Override
  public List<ExecutorLogEvent> getExecutorEvents(final Executor executor, final int num,
                                                  final int offset) throws ExecutorManagerException {
    return this.executorEventsDao.getExecutorEvents(executor, num, offset);
  }

  @Override
  public void assignExecutor(final int executorId, final int executionId)
          throws ExecutorManagerException {
    this.assignExecutorDao.assignExecutor(executorId, executionId);
  }

  @Override
  public Executor fetchExecutorByExecutionId(final int executionId)
          throws ExecutorManagerException {
    return this.executorDao.fetchExecutorByExecutionId(executionId);
  }

  @Override
  public int removeExecutionLogsByTime(final long millis)
          throws ExecutorManagerException {
    return this.executionLogsDao.removeExecutionLogsByTime(millis);
  }

  @Override
  public void unassignExecutor(final int executionId) throws ExecutorManagerException {
    this.assignExecutorDao.unassignExecutor(executionId);
  }

  @Override
  public int selectAndUpdateExecution(final int executorId, final boolean isActive)
          throws ExecutorManagerException {
    return this.executionFlowDao.selectAndUpdateExecution(executorId, isActive);
  }

  @Override
  public void unsetExecutorIdForExecution(final int executionId) throws ExecutorManagerException {
    this.executionFlowDao.unsetExecutorIdForExecution(executionId);
  }

  @Override
  public List<ExecutableFlow> fetchUserFlowHistory(final int skip, final int num, final String user)
          throws ExecutorManagerException {
    return this.executionFlowDao.fetchUserFlowHistory(skip, num, user);
  }

  @Override
  public List<ExecutableFlow> fetchUserFlowHistoryByAdvanceFilter(final String projContain,
                                                                  final String flowContains, final String execIdContain, final String userNameContains,
                                                                  final String status, final long startTime,
                                                                  final long endTime, String runDate, final int skip, final int num, final int flowType)
          throws ExecutorManagerException {
    return this.executionFlowDao.fetchUserFlowHistoryByAdvanceFilter(projContain, flowContains,
            execIdContain,
            userNameContains, status, startTime, endTime, runDate, skip, num, flowType);
  }

  @Override
  public List<ExecutableFlow> fetchUserFlowHistoryByAdvanceFilter(String projContain, String flowContains, String execIdContain, String userNameContains,
                                                                  String status, long startTime, long endTime, String subsystem, String busPath,
                                                                  String department, String runDate, int skip, int num, int flowType)
          throws ExecutorManagerException {
    return this.executionFlowDao.fetchUserFlowHistoryByAdvanceFilter(projContain, flowContains,
            execIdContain,
            userNameContains, status, startTime, endTime, subsystem, busPath, department, runDate, skip, num, flowType);
  }

  @Override
  public List<ExecutableFlow> fetchHistoryRecoverFlows(final String userNameContains)
          throws ExecutorManagerException {
    return this.executionFlowDao.fetchHistoryRecoverFlows(userNameContains);
  }

  @Override
  public List<ExecutableFlow> fetchHistoryRecoverFlowByRepeatId(final String repeatId)
          throws ExecutorManagerException {
    return this.executionFlowDao.fetchHistoryRecoverFlowByRepeatId(repeatId);
  }

  @Override
  public List<ExecutableFlow> fetchHistoryRecoverFlowByFlowId(final String flowId, final String projectId)
          throws ExecutorManagerException {
    return this.executionFlowDao.fetchHistoryRecoverFlowByFlowId(flowId, projectId);
  }

  @Override
  public List<ExecutionRecover> listHistoryRecoverFlows(final Map paramMap, final int skip, final int num)
          throws ExecutorManagerException{
    return this.executionRecoverDao.listHistoryRecoverFlows(paramMap, skip, num);
  }

  @Override
  public List<ExecutionRecover> listMaintainedHistoryRecoverFlows(String username, List<Integer> projectIds, int skip, int num)
          throws ExecutorManagerException {
    return this.executionRecoverDao.listMaintainedHistoryRecoverFlows(username, projectIds, skip, num);
  }

  @Override
  public Integer saveHistoryRecoverFlow(final ExecutionRecover executionRecover)
          throws ExecutorManagerException{
    return this.executionRecoverDao.uploadExecutableRecoverFlow(executionRecover);
  }

  @Override
  public void updateHistoryRecover(final ExecutionRecover executionRecover) throws ExecutorManagerException{
    this.executionRecoverDao.updateExecutableRecoverFlow(executionRecover);
  }

  @Override
  public ExecutionRecover getHistoryRecoverFlow(final Integer recoverId) throws ExecutorManagerException{

    return this.executionRecoverDao.getHistoryRecoverFlows(recoverId);
  }

  @Override
  public ExecutionRecover getHistoryRecoverFlowByPidAndFid(final String projectId, final String flowId)
          throws ExecutorManagerException{

    return this.executionRecoverDao.getHistoryRecoverFlowByPidAndFid(projectId, flowId);
  }


  @Override
  public List<ExecutionRecover> listHistoryRecoverRunnning(final Integer loadSize)
          throws ExecutorManagerException{

    List<ExecutionRecover> allWaitRunning = new ArrayList<>();

    Map<String, String> paramMap = new HashMap<>();
    paramMap.put("recoverStatus", Status.RUNNING.getNumVal()+"");
    paramMap.put("limitNum", loadSize + "");

    allWaitRunning.addAll(this.executionRecoverDao.listHistoryRecover(paramMap));

    paramMap.put("recoverStatus", Status.PREPARING.getNumVal()+"");

    allWaitRunning.addAll(this.executionRecoverDao.listHistoryRecover(paramMap));

    return allWaitRunning;

  }

  @Override
  public int getExecutionCycleTotal(Optional<String> usernameOp) throws ExecutorManagerException {
    return this.executionCycleDao.getCycleFlowsTotal(usernameOp);
  }

  @Override
  public int getExecutionCycleAllTotal(String userName, String searchTerm,HashMap<String, String> queryMap) throws ExecutorManagerException{
    return this.executionCycleDao.getExecutionCycleAllTotal(userName,searchTerm,queryMap);
  }

  @Override
  public List<ExecutionCycle> getExecutionCycleAllPages(String userName, String searchTerm, int offset, int length,HashMap<String, String> queryMap) throws ExecutorManagerException {
    return executionCycleDao.getExecutionCycleAllPages(userName,searchTerm,offset,length,queryMap);
  }

  @Override
  public int getExecutionCycleTotal(String username, List<Integer> projectIds) throws ExecutorManagerException {
    return this.executionCycleDao.getCycleFlowsTotal(username, projectIds);
  }

  @Override
  public List<ExecutionCycle> listExecutionCycleFlows(Optional<String> username, int offset, int length)
          throws ExecutorManagerException {
    return this.executionCycleDao.listCycleFlows(username, offset, length);
  }

  @Override
  public List<ExecutionCycle> listExecutionCycleFlows(String username, List<Integer> projectIds, int offset, int length)
          throws ExecutorManagerException {
    return this.executionCycleDao.listCycleFlows(username, projectIds, offset, length);
  }

  @Override
  public int saveExecutionCycleFlow(ExecutionCycle cycleFlow) throws ExecutorManagerException {
    return this.executionCycleDao.uploadCycleFlow(cycleFlow);
  }

  @Override
  public ExecutionCycle getExecutionCycleFlow(String projectId, String flowId) throws ExecutorManagerException {
    return this.executionCycleDao.getExecutionCycleFlow(projectId, flowId);
  }

  @Override
  public ExecutionCycle getExecutionCycleFlowDescId(String projectId, String flowId) throws ExecutorManagerException {
    return this.executionCycleDao.getExecutionCycleFlowDescId(projectId, flowId);
  }

  @Override
  public ExecutionCycle getExecutionCycleFlow(int id) throws ExecutorManagerException {
    return this.executionCycleDao.getExecutionCycleFlow(id);
  }

  @Override
  public int updateExecutionFlow(ExecutionCycle cycleFlow) throws ExecutorManagerException {
    return this.executionCycleDao.updateCycleFlow(cycleFlow);
  }

  @Override
  public int stopAllCycleFlows() throws ExecutorManagerException {
    return this.executionCycleDao.stopAllCycleFlows();
  }

  @Override
  public List<ExecutionCycle> getAllRunningCycleFlows() throws ExecutorManagerException {
    return this.executionCycleDao.getAllRunningCycleFlows();
  }

  @Override
  public int getHistoryRecoverTotal() throws ExecutorManagerException{
    return this.executionRecoverDao.getHistoryRecoverTotal();
  }

  @Override
  public ExecutableFlow getProjectLastExecutableFlow(int projectId, String flowId)
          throws ExecutorManagerException{
    List<ExecutableFlow> flows = this.executionFlowDao.getProjectLastExecutableFlow(projectId, flowId);
    if(flows.size() > 0){
      return flows.get(0);
    }else{
      return null;
    }
  }

  @Override
  public int getUserRecoverHistoryTotal(final String userName) throws ExecutorManagerException{

    return this.executionRecoverDao.getUserHistoryRecoverTotal(userName);
  }

  @Override
  public int getMaintainedHistoryRecoverTotal(String username, List<Integer> maintainedProjectIds) throws ExecutorManagerException {
    return this.executionRecoverDao.getMaintainedHistoryRecoverTotal(username, maintainedProjectIds);
  }
  @Override
  public LogData fetchAllLogs(final int execId, final String name, final int attempt) throws ExecutorManagerException {

    return this.executionLogsDao.fetchAllLogs(execId, name, attempt);
  }

  /**
   * 插入Executor节点数据 数据表executors去除自增ID 从配置文件获取ID 保持ID不变
   * @param id
   * @param host
   * @param port
   * @return
   * @throws ExecutorManagerException
   */
  @Override
  public Executor addExecutorFixed(final int id, final String host, final int port)
          throws ExecutorManagerException {
    return this.executorDao.addExecutorFixed(id, host, port);
  }

  /**
   * 获取所有的日志过滤条件
   * @return
   * @throws ExecutorManagerException
   */
  @Override
  public List<LogFilterEntity> listAllLogFilter() throws ExecutorManagerException {

    return this.logFilterDao.listAllLogFilter();

  }

  @Override
  public int getExecHistoryTotal(final HistoryQueryParam param) throws ExecutorManagerException{

    return this.executionRecoverDao.getExecHistoryTotal(param);
  }

  @Override
  public int getExecHistoryTotal(HistoryQueryParam param, List<Integer> projectIds) throws ExecutorManagerException{

    return this.executionRecoverDao.getExecHistoryTotal(param, projectIds);
  }

  @Override
  public int getMaintainedExecHistoryTotal(String username, List<Integer> projectIds) throws ExecutorManagerException {
    return this.executionRecoverDao.getMaintainedExecHistoryTotal(username, projectIds);
  }

  @Override
  public int getExecHistoryQuickSerachTotal(final Map<String, String> filterMap) throws ExecutorManagerException{

    return this.executionRecoverDao.getExecHistoryQuickSerachTotal(filterMap);
  }

  @Override
  public int getMaintainedFlowsQuickSearchTotal(String username, final Map<String, String> filterMap, List<Integer> projectIds)
          throws ExecutorManagerException {
    return this.executionRecoverDao.getMaintainedFlowsQuickSearchTotal(username, filterMap, projectIds);
  }

  @Override
  public List<ExecutableFlow> fetchUserFlowHistoryByProjectIdAndFlowId(final int projectId, final String flowId,
                                                                       final int skip, final int num, final String userName) throws ExecutorManagerException {
    return this.executionFlowDao.fetchUserFlowHistoryByProjectIdAndFlowId(projectId, flowId, skip, num, userName);
  }

  @Override
  public int fetchNumUserExecutableFlowsByProjectIdAndFlowId(final int projectId, final String flowId, final String userName)
          throws ExecutorManagerException {
    return this.numExecutionsDao.fetchNumUserExecutableFlowsByProjectIdAndFlowId(projectId, flowId, userName);
  }

  @Override
  public int getUserExecHistoryTotal(HistoryQueryParam param, String loginUser) throws ExecutorManagerException{

    return this.executionRecoverDao.getUserExecHistoryTotal(param, loginUser);
  }

  @Override
  public int getUserExecHistoryQuickSerachTotal(final Map<String, String> filterMap) throws ExecutorManagerException{

    return this.executionRecoverDao.getUserExecHistoryQuickSerachTotal(filterMap);
  }

  @Override
  public List<ExecutableFlow> fetchUserFlowHistory(final String loginUser, final String projContain,
                                                   final String flowContains, final String execIdContain, final String userNameContains,
                                                   final String status, final long startTime,
                                                   final long endTime, String runDate, final int skip, final int num, final int flowType)
          throws ExecutorManagerException {
    return this.executionFlowDao.fetchUserFlowHistory(loginUser, projContain, flowContains,
            execIdContain,
            userNameContains, status, startTime, endTime, runDate, skip, num, flowType);
  }

  @Override
  public List<ExecutableFlow> fetchUserFlowHistory(String loginUser, HistoryQueryParam param,
                                                   int skip, int size) throws ExecutorManagerException {
    return this.executionFlowDao.fetchUserFlowHistory(loginUser, param, skip, size);
  }

  /**
   *
   * @param userName
   * @return
   * @throws ExecutorManagerException
   */
  @Override
  public List<ExecutableFlow> getTodayExecutableFlowData(final String userName)
          throws ExecutorManagerException{

    return this.executionFlowDao.getTodayExecutableFlowData(userName);
  }



  @Override
  public List<ExecutableFlow> getRealTimeExecFlowData(final String userName)
          throws ExecutorManagerException{

    return this.executionFlowDao.getRealTimeExecFlowDataDao(userName);
  }

  @Override
  public List<ExecutableFlow> getTodayExecutableFlowDataNew(String userName) throws ExecutorManagerException {
    return this.executionFlowDao.getTodayExecutableFlowDataNew(userName);
  }

  @Override
  public Integer getTodayFlowRunTimesByFlowId(final String projectId, final String flowId, final String usename) throws ExecutorManagerException{
    return this.executionFlowDao.getTodayFlowRunTimesByFlowId(projectId, flowId, usename);
  }

  @Override
  public List<ExecutionRecover> fetchHistoryRecoverFlows() throws ExecutorManagerException {
    return this.executionRecoverDao.fetchHistoryRecover();
  }

  @Override
  public List<RecoverTrigger> fetchHistoryRecoverTriggers() {
    List<RecoverTrigger> recoverTriggers = new ArrayList<>();
    try {
      recoverTriggers = this.executionRecoverDao.fetchHistoryRecover().stream().map(x -> new RecoverTrigger(x)).collect(Collectors.toList());
    }catch (ExecutorManagerException eme){
      logger.error("fetch history recover trigger failed.", eme);
    }
    return recoverTriggers;
  }

  @Override
  public List<Integer> getExecutorIdsBySubmitUser(String submitUser) throws ExecutorManagerException {
    return departmentGroupDao.fetchExecutorsIdSBySubmitUser(submitUser);
  }

  @Override
  public List<DepartmentGroup> fetchAllDepartmentGroup() throws ExecutorManagerException {
    return departmentGroupDao.fetchAllDepartmentGroup();
  }

  @Override
  public void addDepartmentGroup(DepartmentGroup departmentGroup) throws ExecutorManagerException {
    departmentGroupDao.addDepartmentGroup(departmentGroup);
  }

  @Override
  public boolean checkGroupNameIsExist(DepartmentGroup departmentGroup) throws ExecutorManagerException {
    return departmentGroupDao.checkGroupNameIsExist(departmentGroup);
  }

  @Override
  public boolean checkExecutorIsUsed(int executorId) throws ExecutorManagerException {
    return departmentGroupDao.checkExecutorIsUsed(executorId);
  }

  @Override
  public int deleteDepartmentGroup(DepartmentGroup departmentGroup) throws ExecutorManagerException {
    return departmentGroupDao.deleteDepartmentGroup(departmentGroup);
  }

  @Override
  public int updateDepartmentGroup(DepartmentGroup departmentGroup) throws ExecutorManagerException {
    return departmentGroupDao.updateDepartmentGroup(departmentGroup);
  }

  @Override
  public DepartmentGroup fetchDepartmentGroupById(Integer id) throws ExecutorManagerException {
    return departmentGroupDao.fetchDepartmentGroupById(id);
  }

  @Override
  public int groupIdIsExist(DepartmentGroup departmentGroup) throws ExecutorManagerException {
    return departmentGroupDao.groupIdIsExist(departmentGroup);
  }

  @Override
  public void addUserVariable(UserVariable userVariable) throws ExecutorManagerException {
    this.userVariableDao.addUserVariable(userVariable);
  }

  @Override
  public int deleteUserVariable(UserVariable variable) throws ExecutorManagerException {
    return this.userVariableDao.deleteUserVariable(variable);
  }

  @Override
  public int updateUserVariable(UserVariable userVariable) throws Exception {
    return this.userVariableDao.updateUserVariable(userVariable);
  }

  @Override
  public List<UserVariable> fetchAllUserVariable(UserVariable userVariable) throws ExecutorManagerException {
    return this.userVariableDao.fetchAllUserVariable(userVariable);
  }

  @Override
  public UserVariable getUserVariableById(Integer id) throws ExecutorManagerException {
    return this.userVariableDao.getUserVariableById(id);
  }

  @Override
  public Map<String, String> getUserVariableByName(String userName) throws ExecutorManagerException {
    return this.userVariableDao.getUserVariableByName(userName);
  }


  @Override
  public Integer findWtssUserByName(String name) throws ExecutorManagerException {
    return this.userVariableDao.findWtssUserByName(name);
  }

  @Override
  public Integer getWtssUserTotal() throws ExecutorManagerException {
    return this.userVariableDao.getWtssUserTotal();
  }

  @Override
  public List<WtssUser> findAllWtssUserPageList(String searchName, int pageNum, int pageSize) throws ExecutorManagerException {
    return this.userVariableDao.findAllWtssUserPageList(searchName, pageNum, pageSize);
  }

  @Override
  public List<UserVariable> fetchAllUserVariableByOwnerDepartment(Integer departmentId) throws ExecutorManagerException {

    return this.userVariableDao.fetchAllUserVariableByOwnerDepartment(departmentId);
  }

  @Override
  public UserVariable findUserVariableByKey(String key) throws ExecutorManagerException {
    return this.userVariableDao.findUserVariableByKey(key);
  }

  @Override
  public boolean fetchGroupScheduleSwitch(String submitUser) throws ExecutorManagerException {
    return this.departmentGroupDao.fetchGroupScheduleSwitch(submitUser);
  }

  @Override
  public List<Integer> getRunningExecByLock(Integer projectName, String flowId) {
    return this.executionFlowDao.getRunningExecByLock(projectName, flowId);
  }

  @Override
  public Set<DmsBusPath> getDmsBusPathFromDb(String jobCode) {
    return this.executionFlowDao.getDmsBusPathFromDb(jobCode);
  }

  @Override
  public Set<DmsBusPath> getDmsBusPathFromDb(String jobCode, String updateTime) {
    return this.executionFlowDao.getDmsBusPathFromDb(jobCode, updateTime);
  }

  @Override
  public void insertOrUpdate(DmsBusPath dmsBusPath) {
    this.executionFlowDao.insertOrUpdate(dmsBusPath);
  }

  @Override
  public String getEventType(String topic, String msgName) {
    return this.executionJobDao.getEventType(topic,msgName);
  }

  @Override
  public void addHoldBatchOpr(String id, int oprType, int oprLevel, String user, long createTime, String oprData)
          throws ExecutorManagerException {
    this.holdBatchDao.addHoldBatchOpr(id, oprType, oprLevel, user, createTime, oprData);
  }

  @Override
  public void addHoldBatchAlert(String batchId, ExecutableFlow executableFlow, int resumeStatus) throws ExecutorManagerException {
    this.holdBatchDao.addHoldBatchAlert(batchId, executableFlow, resumeStatus);
  }

  @Override
  public List<HoldBatchOperate> getLocalHoldBatchOpr() throws ExecutorManagerException {
    return this.holdBatchDao.getLocalHoldBatchOpr();
  }

  @Override
  public List<HoldBatchAlert> queryAlertByBatch(String batchId) throws ExecutorManagerException {
    return this.holdBatchDao.queryAlertByBatch(batchId);
  }

  @Override
  public HoldBatchAlert queryBatchExecutableFlows(long id)
          throws ExecutorManagerException {
    return this.holdBatchDao.queryBatchExecutableFlows(id);
  }

  @Override
  public HoldBatchAlert querySubmittedExecutableFlows(long id)
          throws ExecutorManagerException {
    return this.holdBatchDao.querySubmittedExecutableFlows(id);
  }

  @Override
  public void updateHoldBatchResumeStatus(String projectName, String flowName)
          throws ExecutorManagerException {
    this.updateHoldBatchResumeStatus(projectName, flowName);
  }

  @Override
  public void addHoldBatchResume(String batchId, String oprData, String user)
          throws ExecutorManagerException {
    this.holdBatchDao.addHoldBatchResume(batchId, oprData, user);
  }

  @Override
  public void updateHoldBatchStatus(String batchId, int status) throws ExecutorManagerException {
    this.holdBatchDao.updateHoldBatchStatus(batchId, status);
  }

  @Override
  public String getLocalHoldBatchResume(String batchId) throws ExecutorManagerException {
    return this.holdBatchDao.getLocalHoldBatchResume(batchId);
  }

  @Override
  public void addHoldBatchFrequent(String batchId, ExecutableFlow executableFlow)
          throws ExecutorManagerException {
    this.holdBatchDao.addHoldBatchFrequent(batchId,executableFlow);
  }

  @Override
  public List<HoldBatchAlert> queryExecByBatch(String batchId) throws ExecutorManagerException {
    return this.holdBatchDao.queryExecByBatch(batchId);
  }

  @Override
  public List<HoldBatchAlert> queryFrequentByBatch(String batchId)
          throws ExecutorManagerException {
    return this.holdBatchDao.queryFrequentByBatch(batchId);
  }

  @Override
  public void updateHoldBatchFrequentStatus(HoldBatchAlert holdBatchAlert)
          throws ExecutorManagerException {
    this.holdBatchDao.updateHoldBatchFrequentStatus(holdBatchAlert);
  }

  @Override
  public List<HoldBatchAlert> queryExecingByBatch(String batchId) throws ExecutorManagerException {
    return this.holdBatchDao.queryExecingByBatch(batchId);
  }

  @Override
  public void updateHoldBatchResumeStatus(HoldBatchAlert holdBatchAlert)
          throws ExecutorManagerException {
    this.holdBatchDao.updateHoldBatchResumeStatus(holdBatchAlert);
  }

  @Override
  public void updateHoldBatchExpired(String batchId) throws ExecutorManagerException {
    this.holdBatchDao.updateHoldBatchExpired(batchId);
  }

  @Override
  public void updateHoldBatchId(String batchId) throws ExecutorManagerException {
    this.holdBatchDao.updateHoldBatchId(batchId);
  }

  @Override
  public List<HoldBatchOperate> getMissResumeBatch() throws ExecutorManagerException {
    return this.holdBatchDao.getMissResumeBatch();
  }

  @Override
  public List<Integer> queryWaitingFlow(String project, String flow) {
    return this.holdBatchDao.queryWaitingFlow(project, flow);
  }

  @Override
  public HoldBatchAlert getHoldBatchAlert(long id) {
    return this.holdBatchDao.getHoldBatchAlert(id);
  }

  @Override
  public List<HoldBatchAlert> queryWaitingAlert() {
    return this.holdBatchDao.queryWaitingAlert();
  }

  @Override
  public List<CfgWebankOrganization> fetchAllDepartment() throws ExecutorManagerException {
    return this.departmentGroupDao.fetchAllDepartment();
  }

  @Override
  public void updateHoldBatchNotResumeByExecId(int execId) {
    this.holdBatchDao.updateHoldBatchNotResumeByExecId(execId);
  }

  @Override
  public List<ExecutionRecover> getUserHistoryRerunConfiguration(int projectId, String flowName, String userId, int start, int size) throws ExecutorManagerException {
    return this.executionRecoverDao.getUserHistoryRerunConfiguration(projectId, flowName, userId, start, size);
  }

  @Override
  public List<ExecutionRecover> getMaintainedHistoryRerunConfiguration(int id, String flow, String userId, int start, int size) throws ExecutorManagerException {
    return this.executionRecoverDao.getMaintainedHistoryRerunConfiguration(id, flow, userId, start, size);
  }

  @Override
  public List<ExecutionRecover> getAllHistoryRerunConfiguration(int id, String flow, int start, int size) throws ExecutorManagerException {
    return this.executionRecoverDao.getAllHistoryRerunConfiguration(id, flow, start, size);
  }

  @Override
  public int getAllExecutionRecoverTotal(int projectId, String flowName) throws  ExecutorManagerException {
    return this.executionRecoverDao.getAllExecutionRecoverTotal(projectId, flowName);
  }

  @Override
  public int getMaintainedExecutionRecoverTotal(int projectId, String flowName, String userId) throws ExecutorManagerException {
    return this.executionRecoverDao.getMaintainedExecutionRecoverTotal(projectId, flowName, userId);
  }

  @Override
  public int getUserExecutionRecoverTotal(int projectId, String flowName, String userId) throws ExecutorManagerException {
    return this.executionRecoverDao.getUserExecHistoryTotal(projectId, flowName, userId);
  }

  @Override
  public long getFinalScheduleTime(long triggerInitTime) {
    return this.executionFlowDao.getFinalScheduleTime(triggerInitTime);
  }

  @Override
  public void updateHoldBatchAlertStatus(HoldBatchAlert holdBatchAlert)
          throws ExecutorManagerException {
    this.holdBatchDao.updateHoldBatchAlertStatus(holdBatchAlert);
  }

  @Override
  public List<Pair<ExecutionReference, ExecutableFlow>> fetchFlowByStatus(Status status)
          throws ExecutorManagerException {
    return this.executionFlowDao.fetchFlowByStatus(status);
  }

  @Override
  public void linkJobHook(String jobCode, String prefixRules, String suffixRules, String username)
          throws SQLException {
    this.executionJobDao.linkJobHook(jobCode, prefixRules, suffixRules, username);
  }

  @Override
  public JobHook getJobHook(String jobCode) {
    return this.executionJobDao.getJobHook(jobCode);
  }

  @Override
  public Hosts getHostConfigByHostname(String hostname)
          throws ExecutorManagerException {
    return this.executorDao.getHostConfigByHostname(hostname);
  }

  @Override
  public int insertHostsConfig(Hosts hosts)
          throws ExecutorManagerException {
    return this.executorDao.insertHostsConfig(hosts);
  }

  @Override
  public int executorOffline(int executorid) throws ExecutorManagerException {
    return departmentGroupDao.executorOffline(executorid);
  }

  @Override
  public int executorOnline(int executorid) throws ExecutorManagerException {
    return departmentGroupDao.executorOnline(executorid);
  }

  @Override
  public boolean checkIsOnline(int executorid) throws ExecutorManagerException {
    return departmentGroupDao.checkIsOnline(executorid);
  }

  @Override
  public List<ExecutableFlow> getFlowTodayHistory(int projectId, String flowId) throws ExecutorManagerException {
    ZoneId zoneId = ZoneId.systemDefault();
    long milli = LocalDateTime.now(zoneId).withHour(0).withMinute(0).withSecond(0).atZone(zoneId).toInstant().toEpochMilli();
    return executionFlowDao.fetchFlowHistory(projectId,flowId,milli);
  }

  @Override
  public JobPredictionExecutionInfo fetchJobPredictionExecutionInfo(int projectId, String flowId, String jobId) throws ExecutorManagerException {
    return executionJobDao.fetchJobPredictionExecutionInfo(projectId,flowId,jobId);
  }

  @Override
  public List<JobPredictionExecutionInfo> fetchJobPredictionExecutionInfoList(int projectId, String flowId) throws ExecutorManagerException {
    return executionJobDao.fetchJobPredictionExecutionInfoList(projectId,flowId);
  }

  @Override
  public void deleteExecutionCycle(int projectId, String flowId) {
    executionCycleDao.deleteExecutionCycle(projectId, flowId);
  }





  @Override
  public List<ExecutionCycle> getRunningCycleFlows(Integer projectId, String flowId) {
    return executionCycleDao.getRunningCycleFlows(projectId,flowId);
  }
}
