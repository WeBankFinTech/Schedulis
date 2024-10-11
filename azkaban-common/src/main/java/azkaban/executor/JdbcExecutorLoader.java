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
import azkaban.history.ExecutionRecoverDao;
import azkaban.history.RecoverTrigger;
import com.webank.wedatasphere.schedulis.common.log.LogFilterDao;
import com.webank.wedatasphere.schedulis.common.log.LogFilterEntity;
import com.webank.wedatasphere.schedulis.common.system.entity.WtssUser;
import azkaban.utils.FileIOUtils.LogData;
import azkaban.utils.Pair;
import azkaban.utils.Props;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.webank.wedatasphere.schedulis.common.executor.DepartmentGroup;
import com.webank.wedatasphere.schedulis.common.executor.DepartmentGroupDao;
import com.webank.wedatasphere.schedulis.common.executor.ExecutionCycle;
import com.webank.wedatasphere.schedulis.common.executor.ExecutionCycleDao;
import com.webank.wedatasphere.schedulis.common.executor.ExecutionLogsAdapter;
import com.webank.wedatasphere.schedulis.common.executor.UserVariable;
import com.webank.wedatasphere.schedulis.common.executor.UserVariableDao;
import java.util.*;
import javax.inject.Inject;
import javax.inject.Singleton;
import java.io.File;
import java.time.Duration;
import java.util.stream.Collectors;

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
      final ExecutionCycleDao executionCycleDao) {
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
  public ExecutableFlow fetchExecutableFlow(final int id)
      throws ExecutorManagerException {
    return this.executionFlowDao.fetchExecutableFlow(id);
  }

  @Override
  public List<ExecutableFlow> fetchExecutableFlowByRepeatId(int repeatId) throws ExecutorManagerException {
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
  public List<ExecutableFlow> fetchFlowHistory(final int projectId, final String flowId,
      final int skip, final int num) throws ExecutorManagerException {
    return this.executionFlowDao.fetchFlowHistory(projectId, flowId, skip, num);
  }

  public List<ExecutableFlow> fetchFlowHistory(final int projectId, final String flowId,
      final long startTime) throws ExecutorManagerException {
    return this.executionFlowDao.fetchFlowHistory(projectId, flowId, startTime);
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
  public List<ExecutableFlow> fetchMaintainedFlowHistory(String username, List<Integer> projectIds, int skip, int size)
          throws ExecutorManagerException {
    return this.executionFlowDao.fetchMaintainedFlowHistory(username, projectIds, skip, size);
  }

  @Override
  public List<ExecutableFlow> fetchFlowHistory(final String projContain,
                                               final String flowContains,
                                               final String execIdContain,
                                               final String userNameContains, final String status,
                                               final long startTime,
                                               final long endTime, final int skip, final int num, final int flowType)
                                               throws ExecutorManagerException {
    return this.executionFlowDao.fetchFlowHistory(projContain, flowContains,execIdContain,
        userNameContains, status, startTime, endTime, skip, num, flowType);
  }

  @Override
  public List<ExecutableFlow> fetchMaintainedFlowHistory(String projContain, String flowContains,
                                                  String execIdContain, String userNameContains, String status, long startTime,
                                                  long endTime, int skip, int num, int flowType, String username, List<Integer> projectIds)
          throws ExecutorManagerException {
    return this.executionFlowDao.fetchMaintainedFlowHistory(projContain, flowContains,execIdContain,
            userNameContains, status, startTime, endTime, skip, num, flowType, username, projectIds);
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
    return this.executionFlowDao.fetchFlowHistoryQuickSearch(searchContains, username, skip, num, projectIds);
  }

  @Override
  public List<ExecutableFlow> fetchFlowAllHistory(int projectId, String flowId, String user)
      throws ExecutorManagerException{
    return this.executionFlowDao.fetchFlowAllHistory(projectId, flowId, user);
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
  public List<ExecutableJobInfo> fetchJobAllHistory(final int projectId, final String jobId)
      throws ExecutorManagerException {

    return this.executionJobDao.fetchJobAllHistory(projectId, jobId);
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
      final String flowContains, final String execIdContain, final String userNameContains, final String status, final long startTime,
      final long endTime, final int skip, final int num, final int flowType) throws ExecutorManagerException {
    return this.executionFlowDao.fetchUserFlowHistoryByAdvanceFilter(projContain, flowContains,execIdContain,
        userNameContains, status, startTime, endTime, skip, num, flowType);
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
  public int getExecHistoryTotal(final Map<String, String> filterMap) throws ExecutorManagerException{

    return this.executionRecoverDao.getExecHistoryTotal(filterMap);
  }

  @Override
  public int getExecHistoryTotal(String username, final Map<String, String> filterMap, List<Integer> projectIds) throws ExecutorManagerException{

    return this.executionRecoverDao.getExecHistoryTotal(username, filterMap, projectIds);
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
  public int getUserExecHistoryTotal(final Map<String, String> filterMap) throws ExecutorManagerException{

    return this.executionRecoverDao.getUserExecHistoryTotal(filterMap);
  }

  @Override
  public int getUserExecHistoryQuickSerachTotal(final Map<String, String> filterMap) throws ExecutorManagerException{

    return this.executionRecoverDao.getUserExecHistoryQuickSerachTotal(filterMap);
  }

  @Override
  public List<ExecutableFlow> fetchUserFlowHistory(final String loginUser, final String projContain,
      final String flowContains,final String execIdContain, final String userNameContains, final String status, final long startTime,
      final long endTime, final int skip, final int num, final int flowType) throws ExecutorManagerException {
    return this.executionFlowDao.fetchUserFlowHistory(loginUser, projContain, flowContains, execIdContain,
        userNameContains, status, startTime, endTime, skip, num, flowType);
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
  public int updateUserVariable(UserVariable userVariable) throws ExecutorManagerException{
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
  public List<Integer> getRunningExecByLock(Integer projectName, String flowId) {
    return this.executionFlowDao.getRunningExecByLock(projectName, flowId);
  }

}
