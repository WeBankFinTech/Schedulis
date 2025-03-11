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

package azkaban.executor;

import static java.util.stream.Collectors.joining;

import static azkaban.executor.Status.TERMINAL_STATUSES;
import azkaban.db.DatabaseOperator;
import azkaban.db.EncodingType;
import azkaban.db.SQLTransaction;
import azkaban.utils.GZIPUtils;
import azkaban.utils.JSONUtils;
import azkaban.utils.Pair;
import java.io.IOException;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import javax.inject.Inject;
import javax.inject.Singleton;
import org.apache.commons.dbutils.ResultSetHandler;
import org.apache.commons.lang.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Singleton
public class ExecutionFlowDao {

  private static final Logger logger = LoggerFactory.getLogger(ExecutionFlowDao.class);

  private final DatabaseOperator dbOperator;

  @Inject
  public ExecutionFlowDao(final DatabaseOperator dbOperator) {
    this.dbOperator = dbOperator;
  }

  public synchronized void uploadExecutableFlow(final ExecutableFlow flow)
          throws ExecutorManagerException {

    final String useExecutorParam =
            flow.getExecutionOptions().getFlowParameters().get(ExecutionOptions.USE_EXECUTOR);
    final String executorId = StringUtils.isNotEmpty(useExecutorParam) ? useExecutorParam : null;

    final String INSERT_EXECUTABLE_FLOW = "INSERT INTO execution_flows "
            + "(project_id, flow_id, version, status, submit_time, submit_user, update_time, flow_type, "
            + "use_executor, repeat_id, run_date, subsystem, bus_path, submit_department) "
            + "values (?,?,?,?,?,?,?,?,?, ?, ?, ?, ?, ?)";
    final long submitTime = System.currentTimeMillis();

    flow.setSubmitTime(submitTime);
    flow.setStatus(Status.PREPARING);

    /**
     * Why we need a transaction to get last insert ID?
     * Because "SELECT LAST_INSERT_ID()" needs to have the same connection
     * as inserting the new entry.
     * See https://dev.mysql.com/doc/refman/5.7/en/information-functions.html#function_last-insert-id
     */
    final SQLTransaction<Long> insertAndGetLastID = transOperator -> {
      transOperator.update(INSERT_EXECUTABLE_FLOW,
              flow.getProjectId(),
              flow.getFlowId(),
              flow.getVersion(),
              Status.PREPARING.getNumVal(),
              submitTime,
              flow.getSubmitUser(),
              submitTime,
              flow.getFlowType(),
              executorId, flow.getRepeatId(), flow.getRunDate(), flow.getSubsystem(), flow.getBusPath(),
              flow.getSubmitDepartmentId());
      transOperator.getConnection().commit();
      return transOperator.getLastInsertId();
    };

    try {
      final long id = this.dbOperator.transaction(insertAndGetLastID);
      logger.info("Flow given " + flow.getFlowId() + " given id " + id);
      flow.setExecutionId((int) id);
      updateExecutableFlow(flow);
    } catch (final SQLException e) {
      throw new ExecutorManagerException("Error creating execution.", e);
    }
  }

  List<ExecutableFlow> fetchFlowHistory(final int skip, final int num)
          throws ExecutorManagerException {
    try {
      return this.dbOperator.query(FetchExecutableFlows.FETCH_ALL_EXECUTABLE_FLOW_HISTORY,
              new FetchExecutableFlows(), skip, num);
    } catch (final SQLException e) {
      throw new ExecutorManagerException("Error fetching flow History", e);
    }
  }

  List<ExecutableFlow> fetchMaintainedFlowHistory(String userType, String username, List<Integer> projectIds, int skip, int num)
          throws ExecutorManagerException {
    try {
      String querySQL;
      if (userType.equals("maintainer")) {
        String projectIdsStr = projectIds.stream()
                .map(Objects::toString)
                .collect(joining(",", "(", ")"));
        querySQL = "SELECT ef.exec_id, ef.enc_type, ef.flow_data, ef.run_date FROM execution_flows ef " +
                "WHERE ef.project_id IN " + projectIdsStr + " " +
                "ORDER BY exec_id DESC LIMIT ?, ?";
        return this.dbOperator.query(querySQL, new FetchExecutableFlows(), skip, num);
      } else if (userType.equals("admin")){
        querySQL = FetchExecutableFlows.FETCH_ALL_EXECUTABLE_FLOW_HISTORY;
        return this.dbOperator.query(querySQL, new FetchExecutableFlows(), skip, num);
      } else {
        querySQL =
                "SELECT f.exec_id, f.enc_type, f.flow_data, f.run_date FROM execution_flows f INNER JOIN " +
                        " ( SELECT ef.exec_id FROM execution_flows ef " +
                        "   INNER JOIN project_permissions pp ON ef.project_id = pp.project_id " +
                        "   WHERE pp.name= ? " +
                        "   ORDER BY ef.exec_id DESC LIMIT ?, ? " +
                        " ) t " +
                        "ON f.`exec_id` = t.exec_id; ";
        return this.dbOperator.query(querySQL, new FetchExecutableFlows(), username, skip, num);
      }

    } catch (final SQLException e) {
      throw new ExecutorManagerException("Error fetching flow History", e);
    }
  }

  List<ExecutableFlow> fetchFlowHistory(final int projectId, final String flowId,
                                        final int skip, final int num)
          throws ExecutorManagerException {
    try {
      return this.dbOperator.query(FetchExecutableFlows.FETCH_EXECUTABLE_FLOW_HISTORY,
              new FetchExecutableFlows(), projectId, flowId, skip, num);
    } catch (final SQLException e) {
      throw new ExecutorManagerException("Error fetching flow history", e);
    }
  }

  List<ExecutableFlow> fetchFlowHistory(final int projectId, final String flowId)
          throws ExecutorManagerException {
    try {
      return this.dbOperator.query(FetchExecutableFlows.FETCH_EXECUTABLE_FLOW_ALL_HISTORY,
              new FetchExecutableFlows(), projectId, flowId);
    } catch (final SQLException e) {
      throw new ExecutorManagerException("Error fetching flow history", e);
    }
  }

  public List<Pair<ExecutionReference, ExecutableFlow>> fetchQueuedFlows()
          throws ExecutorManagerException {
    try {
      return this.dbOperator.query(FetchQueuedExecutableFlows.FETCH_QUEUED_EXECUTABLE_FLOW,
              new FetchQueuedExecutableFlows());
    } catch (final SQLException e) {
      throw new ExecutorManagerException("Error fetching active flows", e);
    }
  }

  public List<Pair<ExecutionReference, ExecutableFlow>> fetchFlowByStatus(Status status)
          throws ExecutorManagerException {
    try {
      return this.dbOperator.query(FetchQueuedExecutableFlows.FETCH_FLOW_BY_STATUS,
              new FetchQueuedExecutableFlows(), status.getNumVal());
    } catch (final SQLException e) {
      throw new ExecutorManagerException("Error fetching flows by status", e);
    }
  }

  public List<ExecutableFlow> quickSearchFlowExecutions(final int projectId, final String flowId,
                                                        final int skip, final int num, final String searchTerm)
          throws ExecutorManagerException{
    try {
      final List<Object> params = new ArrayList<>();
      params.add(projectId);
      params.add(flowId);
      params.add("%" + searchTerm + "%");
      params.add("%" + searchTerm + "%");
      params.add(skip);
      params.add(num);
      return this.dbOperator.query(FetchExecutableFlows.QUICK_SEARCH_EXECUTABLE_FLOW,
              new FetchExecutableFlows(), params.toArray());
    } catch (final SQLException e) {
      throw new ExecutorManagerException("Error quickSearchFlowExecutions", e);
    }
  }

  public List<ExecutableFlow> userQuickSearchFlowExecutions(final int projectId, final String flowId,
                                                            final int skip, final int num, final String searchTerm,final String userId)
          throws ExecutorManagerException {
    try {
      final List<Object> params = new ArrayList<>();
      params.add(projectId);
      params.add(flowId);
      params.add(userId);
      params.add("%" + searchTerm + "%");
      params.add(skip);
      params.add(num);
      return this.dbOperator.query(FetchExecutableFlows.FETCH_USER_QUICKSEARCH_EXECUTABLE_FLOW_HISTORY_BY_PROJECT_AND_FLOW,
              new FetchExecutableFlows(), params.toArray());
    } catch (final SQLException e) {
      throw new ExecutorManagerException("Error fetching flow history", e);
    }
  }

  /**
   * fetch flow execution history with specified {@code projectId}, {@code flowId} and flow start
   * time >= {@code startTime}
   *
   * @return the list of flows meeting the specified criteria
   */
  public List<ExecutableFlow> fetchFlowHistory(final int projectId, final String flowId, final
  long startTime) throws ExecutorManagerException {
    try {
      return this.dbOperator.query(FetchExecutableFlows.FETCH_EXECUTABLE_FLOW_BY_START_TIME,
              new FetchExecutableFlows(), projectId, flowId, startTime);
    } catch (final SQLException e) {
      throw new ExecutorManagerException("Error fetching historic flows", e);
    }
  }

  List<ExecutableFlow> fetchFlowHistory(final int projectId, final String flowId,
                                        final int skip, final int num, final Status status)
          throws ExecutorManagerException {
    try {
      return this.dbOperator.query(FetchExecutableFlows.FETCH_EXECUTABLE_FLOW_BY_STATUS,
              new FetchExecutableFlows(), projectId, flowId, status.getNumVal(), skip, num);
    } catch (final SQLException e) {
      throw new ExecutorManagerException("Error fetching active flows", e);
    }
  }

  List<ExecutableFlow> fetchRecentlyFinishedFlows(final Duration maxAge)
          throws ExecutorManagerException {
    try {
      return this.dbOperator.query(FetchRecentlyFinishedFlows.FETCH_RECENTLY_FINISHED_FLOW,
              new FetchRecentlyFinishedFlows(), System.currentTimeMillis() - maxAge.toMillis(),
              Status.SUCCEEDED.getNumVal(), Status.KILLED.getNumVal(),
              Status.FAILED.getNumVal());
    } catch (final SQLException e) {
      throw new ExecutorManagerException("Error fetching recently finished flows", e);
    }
  }

  List<ExecutableFlow> fetchFlowHistory(final String projContain, final String flowContains,
                                        final String execIdContain,
                                        final String userNameContains, final String status,
                                        final long startTime, final long endTime, String runDate,
                                        final int skip, final int num, final int flowType)
          throws ExecutorManagerException {

    StringBuilder querySql = new StringBuilder(
            "SELECT exec_id, ef.enc_type, flow_data, run_date FROM execution_flows ef JOIN projects p ON ef.project_id = p.id");
    final List<Object> params = new ArrayList<>();

    boolean first = true;
    if (StringUtils.isNotBlank(projContain)) {
      first = wrapperSqlParam(first, projContain, querySql, "name", "LIKE", params);
    }

    // todo kunkun-tang: we don't need the below complicated logics. We should just use a simple way.
    if (StringUtils.isNotBlank(flowContains)) {
      first = wrapperSqlParam(first, flowContains, querySql, "flow_id", "LIKE", params);
    }

    if (StringUtils.isNotBlank(execIdContain)) {
      first = wrapperSqlParam(first, execIdContain, querySql, "exec_id", "LIKE", params);
    }

    if (StringUtils.isNotBlank(userNameContains)) {
      first =  wrapperSqlParam(first, userNameContains, querySql, "submit_user", "LIKE", params);
    }

    String[] statusArray = status.split(",");
    if (!("0".equals(statusArray[0]))) {
      first = wrapperMultipleStatusSql(first, statusArray, querySql, "status", "in");
    }

    if (startTime > 0) {
      first = wrapperSqlParam(first, "" + startTime, querySql, "start_time", ">", params);
    }

    if (endTime > 0) {
      first = wrapperSqlParam(first, "" + endTime, querySql, "end_time", "<", params);
    }

    if (StringUtils.isNotBlank(runDate)) {
      first = wrapperSqlParam(first, runDate, querySql, "run_date", "=", params);
    }

    if (flowType != -1) {
      wrapperSqlParam(first, "" + flowType, querySql, "flow_type", "=", params);
    }

    if (skip > -1 && num > 0) {
      querySql.append(" ORDER BY exec_id DESC LIMIT ?, ?");
      params.add(skip);
      params.add(num);
    }

    try {
      return this.dbOperator.query(querySql.toString(), new FetchExecutableFlows(), params.toArray());
    } catch (final SQLException e) {
      throw new ExecutorManagerException("Error fetching active flows", e);
    }
  }

  List<ExecutableFlow> fetchFlowHistory(HistoryQueryParam param, final int skip, final int num)
          throws ExecutorManagerException {

    StringBuilder querySql = new StringBuilder(
            "SELECT exec_id, ef.enc_type, flow_data, run_date FROM execution_flows ef JOIN projects p ON ef.project_id = p.id ");
    final List<Object> params = new ArrayList<>();

    String multiConditionSql = wrapMultiConditionSql(true, querySql, param, skip, num, params).toString();

    try {
      return this.dbOperator.query(multiConditionSql, new FetchExecutableFlows(), params.toArray());
    } catch (final SQLException e) {
      throw new ExecutorManagerException("Error fetching active flows", e);
    }
  }

  private StringBuilder wrapMultiConditionSql(boolean first, StringBuilder querySql, HistoryQueryParam param, final int skip, final int num, List<Object> params) {

    if (param == null) {
      return querySql;
    }

    String action = "LIKE";
    if ("preciseSearch".equals(param.getSearchType())) {
      action = "=";
    }
    if (StringUtils.isNotBlank(param.getComment())) {
      first = wrapperSqlParam(first, param.getComment(), querySql, "ef.flow_comment", action, params);
    }

    if (StringUtils.isNotBlank(param.getProjContain())) {
      first = wrapperSqlParam(first, param.getProjContain(), querySql, "p.name", action, params);
    }

    if (StringUtils.isNotBlank(param.getProjectName())) {
      first = wrapperSqlParam(first, param.getProjectName(), querySql, "p.name", "=", params);
    }

    if (StringUtils.isNotBlank(param.getFlowId())) {
      first = wrapperSqlParam(first, param.getFlowId(), querySql, "ef.flow_id", "=", params);
    }

    // todo kunkun-tang: we don't need the below complicated logics. We should just use a simple way.
    if (StringUtils.isNotBlank(param.getFlowContain())) {
      first = wrapperSqlParam(first, param.getFlowContain(), querySql, "ef.flow_id", action,
              params);
    }

    if (StringUtils.isNotBlank(param.getExecIdContain())) {
      first = wrapperSqlParam(first, param.getExecIdContain(), querySql, "exec_id", action, params);
    }

    if (StringUtils.isNotBlank(param.getUserContain())) {
      first = wrapperSqlParam(first, param.getUserContain(), querySql, "submit_user", action,
              params);
    }

    String[] statusArray = param.getStatus().split(",");
    if (!("0".equals(statusArray[0]))) {
      first = wrapperMultipleStatusSql(first, statusArray, querySql, "status", "in");
    }

    if (param.getStartBeginTime() > 0) {
      first = wrapperSqlParam(first, "" + param.getStartBeginTime(), querySql, "start_time", ">",
              params);
    }
    if (param.getStartEndTime() > 0) {
      first = wrapperSqlParam(first, "" + param.getStartEndTime(), querySql, "start_time", "<",
              params);
    }
    if (param.getFinishBeginTime() > 0) {
      first = wrapperSqlParam(first, "" + param.getFinishBeginTime(), querySql, "end_time", ">",
              params);
    }
    if (param.getFinishEndTime() > 0) {
      first = wrapperSqlParam(first, "" + -1, querySql, "end_time", "!=", params);
      first = wrapperSqlParam(first, "" + param.getFinishEndTime(), querySql, "end_time", "<",
              params);
    }

    if (param.getBeginTime() > 0) {
      first = wrapperSqlParam(first, "" + param.getBeginTime(), querySql, "start_time", ">",
              params);
    }
    if (param.getEndTime() > 0) {
      if (first) {
        querySql.append(" WHERE ");
        first = false;
      } else {
        querySql.append(" AND ");
      }
      querySql.append(" ((end_time=-1 and start_time<?) or (end_time!=-1 and end_time<?)) ");
      for (int i = 0; i < 2; i++) {
        params.add(param.getEndTime());
      }
    }

    if (StringUtils.isNotBlank(param.getSubsystem())) {
      first = wrapperSqlParam(first, param.getSubsystem(), querySql, "subsystem", "=", params);
    }
    if (StringUtils.isNotBlank(param.getBusPath())) {
      first = wrapperSqlParam(first, param.getBusPath(), querySql, "bus_path", "=", params);
    }
    if (StringUtils.isNotBlank(param.getDepartmentId())) {
      first = wrapperSqlParam(first, param.getDepartmentId(), querySql, "submit_department", "=",
              params);
    }
    if (StringUtils.isNotBlank(param.getRunDateReq())) {
      first = wrapperSqlParam(first, param.getRunDateReq(), querySql, "run_date", "=", params);
    }
    if (param.getFlowType() != -1) {
      first = wrapperSqlParam(first, "" + param.getFlowType(), querySql, "flow_type", "=", params);
    }

    if (StringUtils.isNotBlank(param.getFromHomePage())) {
      Calendar calendar = Calendar.getInstance();
      //获取当天凌晨毫秒数
      calendar.set(Calendar.HOUR_OF_DAY, 0);
      calendar.set(Calendar.MINUTE, 0);
      calendar.set(Calendar.SECOND, 0);
      calendar.set(Calendar.MILLISECOND, 1);
      first = wrapperSqlParam(first, calendar.getTimeInMillis() + "", querySql, "ef.submit_time", ">=",
              params);
      //获取当天24点毫秒数
      calendar.set(Calendar.HOUR_OF_DAY, 23);
      calendar.set(Calendar.MINUTE, 59);
      calendar.set(Calendar.SECOND, 59);
      wrapperSqlParam(first, calendar.getTimeInMillis() + "", querySql, "ef.submit_time", "<=",
              params);
    }

    if (skip > -1 && num > -1) {
      querySql.append(" ORDER BY exec_id DESC LIMIT ?, ?");
      params.add(skip);
      params.add(num);
    }
    return querySql;
  }

  public boolean wrapperMultipleStatusSql(boolean firstParam, String[] statusArray, StringBuilder querySql,
                                          String dbColumnName, String action) {

    if (firstParam) {
      querySql.append(" WHERE ");
      firstParam = false;
    } else {
      querySql.append(" AND ");
    }

    StringBuilder statusBuilder = new StringBuilder();
    String joinStatus = String.join(",", statusArray);
    statusBuilder.append(dbColumnName).append(" ").append(action).append("(").append(joinStatus).append(") ");
    querySql.append(statusBuilder);
    return firstParam;

  }

  List<ExecutableFlow> fetchMaintainedFlowHistory(final String projContain,
                                                  final String flowContains, final String execIdContain,
                                                  final String userNameContains, final String status,
                                                  final long startTime, final long endTime, String runDate,
                                                  final int skip, final int num, final int flowType, String username, List<Integer> projectIds)
          throws ExecutorManagerException {
    String projectIdsStr = projectIds.stream()
            .map(Object::toString)
            .collect(joining(",", "(", ")"));
    StringBuilder querySql = new StringBuilder(
            "SELECT ef.exec_id, ef.enc_type, ef.flow_data, ef.run_date FROM execution_flows ef, projects p "
                    + "WHERE ef.project_id = p.id "
                    + "AND ef.project_id IN " + projectIdsStr + " ");
    final List<Object> params = new ArrayList<>();
    boolean first = false;
    if (StringUtils.isNotBlank(projContain)) {
      first = wrapperSqlParam(first, projContain, querySql, "p.name", "LIKE", params);
    }

    // todo kunkun-tang: we don't need the below complicated logics. We should just use a simple way.
    if (StringUtils.isNotBlank(flowContains)) {
      first = wrapperSqlParam(first, flowContains, querySql, "flow_id", "LIKE", params);
    }

    if (StringUtils.isNotBlank(execIdContain)) {
      first = wrapperSqlParam(first, execIdContain, querySql, "exec_id", "LIKE", params);
    }

    if (StringUtils.isNotBlank(userNameContains)) {
      first =  wrapperSqlParam(first, userNameContains, querySql, "submit_user", "LIKE", params);
    }

    String[] statusArray = status.split(",");
    if (!("0".equals(statusArray[0]))) {
      first = wrapperMultipleStatusSql(first, statusArray, querySql, "status", "in");
    }

    if (startTime > 0) {
      first = wrapperSqlParam(first, "" + startTime, querySql, "start_time", ">", params);
    }

    if (endTime > 0) {
      first = wrapperSqlParam(first, "" + endTime, querySql, "end_time", "<", params);
    }

    if (StringUtils.isNotBlank(runDate)) {
      first = wrapperSqlParam(first, runDate, querySql, "run_date", "=", params);
    }

    if (flowType != -1) {
      wrapperSqlParam(first, "" + flowType, querySql, "flow_type", "=", params);
    }

    if (skip > -1 && num > 0) {
      querySql.append(" ORDER BY exec_id DESC LIMIT ?, ?");
      params.add(skip);
      params.add(num);
    }

    try {
      return this.dbOperator.query(querySql.toString(), new FetchExecutableFlows(), params.toArray());
    } catch (final SQLException e) {
      throw new ExecutorManagerException("Error fetching active flows", e);
    }
  }

  List<ExecutableFlow> fetchMaintainedFlowHistory(HistoryQueryParam param, int skip, int num, List<Integer> projectIds)
          throws ExecutorManagerException {
    String projectIdsStr = projectIds.stream()
            .map(Object::toString)
            .collect(joining(",", "(", ")"));
    StringBuilder querySql = new StringBuilder(
            "SELECT ef.exec_id, ef.enc_type, ef.flow_data, ef.run_date FROM execution_flows ef JOIN projects p on ef.project_id = p.id " +
                    "WHERE ef.project_id IN " + projectIdsStr + " ");
    final List<Object> params = new ArrayList<>();

    String multiConditionSql = wrapMultiConditionSql(false, querySql, param, skip, num, params)
            .toString();

    try {
      return this.dbOperator.query(multiConditionSql, new FetchExecutableFlows(), params.toArray());
    } catch (final SQLException e) {
      throw new ExecutorManagerException("Error fetching active flows", e);
    }
  }
  /**
   * 组装参数拼接
   * @param param
   * @param querySql
   * @param dbColumnName
   * @param action
   * @param firstParam
   * @param params
   * @return
   */
  private boolean wrapperSqlParam(boolean firstParam, String param, StringBuilder querySql,
                                  String dbColumnName, String action, List<Object> params) {
    if (firstParam) {
      querySql.append(" WHERE ");
      firstParam = false;
    } else {
      querySql.append(" AND ");
    }
    querySql.append(" ").append(dbColumnName).append(" ").append(action).append(" ?");
    if ("like".equalsIgnoreCase(action)) {
      params.add('%' + param + '%');
    } else {
      params.add(param);
    }
    return firstParam;
  }

  List<ExecutableFlow> fetchFlowHistoryQuickSearch(final String flowContains, final String userNameContains,
                                                   final int skip, final int num)
          throws ExecutorManagerException {

    String query;
    final List<Object> params = new ArrayList<>();

    if(null != userNameContains){
      query =
              "SELECT ef.exec_id, ef.enc_type, ef.flow_data, ef.run_date FROM execution_flows ef, projects p, project_permissions pp "
                      + "WHERE ef.project_id = p.id AND ef.project_id = pp.project_id "
                      + "AND pp.name=? ";
      params.add(userNameContains);
    }else{
      query = "SELECT exec_id, ef.enc_type, flow_data, run_date FROM execution_flows ef, projects p where ef.project_id = p.id";
    }

    // todo kunkun-tang: we don't need the below complicated logics. We should just use a simple way.
    if (flowContains != null && !flowContains.isEmpty()) {
      query += " AND ";
      query += " (ef.exec_id LIKE ? OR ef.flow_id LIKE ? OR p.name LIKE ? OR ef.submit_user LIKE ? ) ";
      params.add('%' + flowContains + '%');
      params.add('%' + flowContains + '%');
      params.add('%' + flowContains + '%');
      params.add('%' + flowContains + '%');
    }

    if (skip > -1 && num > 0) {
      query += " ORDER BY exec_id DESC LIMIT ?, ? ";
      params.add(skip);
      params.add(num);
    }

    try {
      return this.dbOperator.query(query, new FetchExecutableFlows(), params.toArray());
    } catch (final SQLException e) {
      throw new ExecutorManagerException("Error fetching active flows", e);
    }
  }

  List<ExecutableFlow> fetchFlowHistoryQuickSearch(final String flowContains, final String username,
                                                   final int skip, final int num, List<Integer> projectIds)
          throws ExecutorManagerException {

    String query;
    final List<Object> params = new ArrayList<>();
    String projectIdsStr = projectIds.stream()
            .map(Objects::toString)
            .collect(joining(",", "(", ")"));
    query =
            "SELECT ef.exec_id, ef.enc_type, ef.flow_data, ef.run_date, ef.project_id as project_id FROM execution_flows ef, projects p "
                    + "WHERE ef.project_id = p.id "
                    + "AND ef.project_id IN " + projectIdsStr + " ";
    // todo kunkun-tang: we don't need the below complicated logics. We should just use a simple way.
    if (flowContains != null && !flowContains.isEmpty()) {
      query += " AND ";
      query += " (ef.exec_id LIKE ? OR ef.flow_id LIKE ? OR p.name LIKE ? OR ef.submit_user LIKE ? ) ";
      params.add('%' + flowContains + '%');
      params.add('%' + flowContains + '%');
      params.add('%' + flowContains + '%');
      params.add('%' + flowContains + '%');
    }

    if (skip > -1 && num > -1) {
      query += " ORDER BY exec_id DESC LIMIT ?, ? ";
      params.add(skip);
      params.add(num);
    }

    try {
      return this.dbOperator.query(query, new FetchExecutableFlows(), params.toArray());
    } catch (final SQLException e) {
      throw new ExecutorManagerException("Error fetching active flows", e);
    }
  }

  List<ExecutableFlow> fetchFlowAllHistory(final int projectId, final String flowId, final String user)
          throws ExecutorManagerException {

    String querySql = "SELECT exec_id, enc_type, flow_data, run_date FROM execution_flows "
            + "WHERE project_id=? AND flow_id=? ";

    final List<Object> params = new ArrayList<>();

    try {

      params.add(projectId);

      params.add(flowId);

      if (user != null && !user.isEmpty()) {

        querySql += "AND ";
        querySql += "submit_user = ? ";
        params.add(user);
      }

      querySql += "ORDER BY exec_id ";

      return this.dbOperator.query(querySql, new FetchExecutableFlows(), params.toArray());
    } catch (final SQLException e) {
      throw new ExecutorManagerException("Error fetching flow history", e);
    }
  }

  public List<ExecutableFlow> fetchAllExecutableFlow() throws SQLException {
    String querySql = "SELECT exec_id, enc_type, flow_data, run_date FROM execution_flows ";
    return this.dbOperator.query(querySql, new FetchExecutableFlows());
  }

  public List<ExecutableFlow> fetchExecutableFlows(final long submitTime) throws SQLException {
    String querySql = "SELECT ef.exec_id, ef.flow_id, ef.status, ef.flow_type, " +
            " ef.submit_department AS submit_department_id" +
            " FROM execution_flows ef " +
            " WHERE ef.submit_time > ?";
    return this.dbOperator.query(querySql, new FetchDetailExecutableFlows(), submitTime);
  }

  void updateExecutableFlow(final ExecutableFlow flow) throws ExecutorManagerException {
    logger.info("current flow status is {},execId is {},updateTime is {} .", flow.getStatus(), flow.getExecutionId(), flow.getUpdateTime());
    updateExecutableFlow(flow, EncodingType.GZIP);
  }

  int updateExecutableFlowRunDate(ExecutableFlow flow) throws SQLException {
    String UPDATE_EXECUTABLE_FLOW_RUN_DATE =
            "UPDATE execution_flows "
                    + "SET run_date=? WHERE exec_id=?";

    return this.dbOperator.update(UPDATE_EXECUTABLE_FLOW_RUN_DATE,
            flow.getRunDate(), flow.getExecutionId());
  }

  private void updateExecutableFlow(final ExecutableFlow flow, final EncodingType encType)
          throws ExecutorManagerException {
    final String UPDATE_EXECUTABLE_FLOW_DATA =
            "UPDATE execution_flows "
                    + "SET status=?,update_time=?,start_time=?,end_time=?,enc_type=?,flow_data=?,flow_type=?,run_date=?,flow_comment=? "
                    + "WHERE exec_id=?";

    final String json = JSONUtils.toJSON(flow.toObject());
    byte[] data = null;
    try {
      final byte[] stringData = json.getBytes("UTF-8");
      data = stringData;
      // Todo kunkun-tang: use a common method to transform stringData to data.
      if (encType == EncodingType.GZIP) {
        data = GZIPUtils.gzipBytes(stringData);
      }
    } catch (final IOException e) {
      throw new ExecutorManagerException("Error encoding the execution flow.",e);
    }

    try {
      this.dbOperator.update(UPDATE_EXECUTABLE_FLOW_DATA,
              flow.getStatus()
                      .getNumVal(),
              flow.getUpdateTime(),
              flow.getStartTime(),
              flow.getEndTime(),
              encType.getNumVal(),
              data,
              flow.getFlowType(),
              flow.getRunDate(),
              flow.getComment(),
              flow.getExecutionId());
    } catch (final SQLException e) {
      throw new ExecutorManagerException("Error updating flow.", e);
    }
  }

  public ExecutableFlow fetchExecutableFlow(final int execId) throws ExecutorManagerException {
    final FetchExecutableFlowsContainId flowHandler = new FetchExecutableFlowsContainId();
    try {
      final List<ExecutableFlow> properties = this.dbOperator
              .query(FetchExecutableFlowsContainId.FETCH_EXECUTABLE_FLOW, flowHandler, execId);
      if (properties.isEmpty()) {
        return null;
      } else {
        return properties.get(0);
      }
    } catch (final SQLException e) {
      throw new ExecutorManagerException("Error fetching flow id " + execId, e);
    }
  }

  public List<ExecutableFlow> fetchExecutableFlowByRepeatId(final int repeatId) throws ExecutorManagerException {
    final FetchExecutableFlows flowHandler = new FetchExecutableFlows();
    try {
      final List<ExecutableFlow> executableFlows = this.dbOperator
              .query(FetchExecutableFlows.FETCH_EXECUTABLE_FLOW_BY_REPEAT_ID, flowHandler, repeatId);
      return executableFlows;
    } catch (final SQLException e) {
      throw new ExecutorManagerException("Error fetching flow by repeatId: " + repeatId, e);
    }
  }

  /**
   * set executor id to null for the execution id
   */
  public void unsetExecutorIdForExecution(final int executionId) throws ExecutorManagerException {
    final String UNSET_EXECUTOR = "UPDATE execution_flows SET executor_id = null where exec_id = ?";

    final SQLTransaction<Integer> unsetExecutor =
            transOperator -> transOperator.update(UNSET_EXECUTOR, executionId);

    try {
      this.dbOperator.transaction(unsetExecutor);
    } catch (final SQLException e) {
      throw new ExecutorManagerException("Error unsetting executor id for execution " + executionId,
              e);
    }
  }

  public int selectAndUpdateExecution(final int executorId, final boolean isActive)
          throws ExecutorManagerException {
    final String UPDATE_EXECUTION = "UPDATE execution_flows SET executor_id = ? where exec_id = ?";
    final String selectExecutionForUpdate = isActive ?
            SelectFromExecutionFlows.SELECT_EXECUTION_FOR_UPDATE_ACTIVE :
            SelectFromExecutionFlows.SELECT_EXECUTION_FOR_UPDATE_INACTIVE;

    final SQLTransaction<Integer> selectAndUpdateExecution = transOperator -> {
      final List<Integer> execIds = transOperator.query(selectExecutionForUpdate,
              new SelectFromExecutionFlows(), executorId);

      int execId = -1;
      if (!execIds.isEmpty()) {
        execId = execIds.get(0);
        transOperator.update(UPDATE_EXECUTION, executorId, execId);
      }
      transOperator.getConnection().commit();
      return execId;
    };

    try {
      return this.dbOperator.transaction(selectAndUpdateExecution);
    } catch (final SQLException e) {
      throw new ExecutorManagerException("Error selecting and updating execution with executor "
              + executorId, e);
    }
  }

  public static class SelectFromExecutionFlows implements
          ResultSetHandler<List<Integer>> {

    static final String SELECT_EXECUTION_BASE_QUERY = "SELECT exec_id from execution_flows ";

    private static final String FOR_QUEUED_EXECUTABLE_FLOW =
            "WHERE executor_id is NULL AND status = ?";
    private static final String SELECT_QUEUED_EXECUTABLE_FLOW =
            SELECT_EXECUTION_BASE_QUERY + FOR_QUEUED_EXECUTABLE_FLOW;
    private static final String SELECT_EXECUTION_FOR_UPDATE_FORMAT =
            "SELECT exec_id from execution_flows WHERE status = " + Status.PREPARING.getNumVal()
                    + " and executor_id is NULL and flow_data is NOT NULL and %s"
                    + " ORDER BY submit_time ASC LIMIT 1 FOR UPDATE";

    public static final String SELECT_EXECUTION_FOR_UPDATE_ACTIVE =
            String.format(SELECT_EXECUTION_FOR_UPDATE_FORMAT,
                    "(use_executor is NULL or use_executor = ?)");

    public static final String SELECT_EXECUTION_FOR_UPDATE_INACTIVE =
            String.format(SELECT_EXECUTION_FOR_UPDATE_FORMAT, "use_executor = ?");

    @Override
    public List<Integer> handle(final ResultSet rs) throws SQLException {
      if (!rs.next()) {
        return Collections.emptyList();
      }
      final List<Integer> execIds = new ArrayList<>();
      do {
        final int execId = rs.getInt(1);
        execIds.add(execId);
      } while (rs.next());

      return execIds;
    }
  }

  public static class FetchFlowRunTimes implements
          ResultSetHandler<Integer> {
    @Override
    public Integer handle(ResultSet rs) throws SQLException {
      int times = 0;
      while (rs.next()){
        times = rs.getInt(1);
      }
      return times;
    }
  }

  public static class FetchExecutableFlowsContainId implements ResultSetHandler<List<ExecutableFlow>>{
    static String FETCH_EXECUTABLE_FLOW =
            "SELECT exec_id, enc_type, flow_data, run_date, flow_id FROM execution_flows "
                    + "WHERE exec_id=?";
    @Override
    public List<ExecutableFlow> handle(final ResultSet rs) throws SQLException {
      if (!rs.next()) {
        return Collections.emptyList();
      }

      final List<ExecutableFlow> execFlows = new ArrayList<>();
      do {
        final int id = rs.getInt(1);
        final int encodingType = rs.getInt(2);
        final byte[] data = rs.getBytes(3);
        final String runDate = rs.getString(4);
        final String flowId = rs.getString(5);

        if (data != null) {
          final EncodingType encType = EncodingType.fromInteger(encodingType);
          try {
            final ExecutableFlow exFlow =
                    ExecutableFlow.createExecutableFlowFromObject(
                            GZIPUtils.transformBytesToObject(data, encType));
            exFlow.setRunDate(runDate);
            exFlow.setFlowId(flowId);
            execFlows.add(exFlow);
          } catch (final IOException e) {
            throw new SQLException("Error retrieving flow data " + id, e);
          }
        }
      } while (rs.next());

      return execFlows;
    }
  }

  public static class FetchExecutableFlows implements
          ResultSetHandler<List<ExecutableFlow>> {

    static String FETCH_EXECUTABLE_FLOW_BY_REPEAT_ID =
            "SELECT ef.exec_id, ef.enc_type, ef.flow_data, ef.run_date FROM execution_flows ef " +
                    " WHERE ef.`repeat_id` = ? AND ef.status IN (20, 30, 80);";

    static String FETCH_EXECUTABLE_FLOW_BY_START_TIME =
            "SELECT ef.exec_id, ef.enc_type, ef.flow_data, ef.run_date FROM execution_flows ef WHERE project_id=? "
                    + "AND flow_id=? AND start_time >= ? ORDER BY start_time DESC";
    static String FETCH_BASE_EXECUTABLE_FLOW_QUERY =
            "SELECT ef.exec_id, ef.enc_type, ef.flow_data, ef.run_date FROM execution_flows ef";
    static String FETCH_EXECUTABLE_FLOW =
            "SELECT exec_id, enc_type, flow_data, run_date FROM execution_flows "
                    + "WHERE exec_id=?";
    static String FETCH_ALL_EXECUTABLE_FLOW_HISTORY =
            "SELECT exec_id, enc_type, flow_data, run_date FROM execution_flows "
                    + "ORDER BY exec_id DESC LIMIT ?, ?";
    static String FETCH_EXECUTABLE_FLOW_HISTORY =
            "SELECT t.exec_id, t.enc_type, t.flow_data, t.run_date ,t.flow_type FROM execution_flows t "
                    + " INNER JOIN "
                    + " (SELECT e.`exec_id` FROM execution_flows e WHERE e.project_id=? AND e.flow_id=? ORDER BY e.`exec_id` DESC LIMIT ?, ? ) b "
                    + " ON b.exec_id = t.`exec_id`;";
    static String QUICK_SEARCH_EXECUTABLE_FLOW = "SELECT t.exec_id, t.enc_type, t.flow_data, t.run_date ,t.flow_type FROM execution_flows t "
            + " INNER JOIN "
            + " (SELECT e.`exec_id` FROM execution_flows e WHERE e.project_id=? AND e.flow_id=? "
            + " AND (e.`exec_id` LIKE ? OR e.`submit_user` LIKE ?)"
            + " ORDER BY e.`exec_id` DESC LIMIT ?, ? ) b "
            + " ON b.exec_id = t.`exec_id`;";
    static String FETCH_EXECUTABLE_FLOW_ALL_HISTORY =
            "SELECT t.exec_id, t.enc_type, t.flow_data, t.run_date FROM execution_flows t "
                    + " INNER JOIN "
                    + " (SELECT e.`exec_id` FROM execution_flows e WHERE e.project_id=? AND e.flow_id=? ORDER BY e.`exec_id` DESC ) b "
                    + " ON b.exec_id = t.`exec_id` "
                    + "ORDER BY t.exec_id desc ;";
    static String FETCH_EXECUTABLE_FLOW_BY_STATUS =
            "SELECT exec_id, enc_type, flow_data, run_date FROM execution_flows "
                    + "WHERE project_id=? AND flow_id=? AND status=? "
                    + "ORDER BY exec_id DESC LIMIT ?, ?";
    static String FETCH_USER_EXECUTABLE_FLOW_HISTORY =
            "SELECT exec_id, enc_type, flow_data, run_date FROM execution_flows "
                    + "WHERE submit_user=? "
                    + "ORDER BY exec_id DESC LIMIT ?, ?";
    static String FETCH_USER_EXECUTABLE_FLOW_HISTORY_BY_PROJECT_AND_FLOW =
            "SELECT exec_id, enc_type, flow_data, run_date,flow_type FROM execution_flows "
                    + "WHERE project_id=? AND flow_id=? AND submit_user=? "
                    + "ORDER BY exec_id DESC LIMIT ?, ?";
    static String FETCH_USER_QUICKSEARCH_EXECUTABLE_FLOW_HISTORY_BY_PROJECT_AND_FLOW =
            "SELECT exec_id, enc_type, flow_data, run_date,flow_type FROM execution_flows "
                    + "WHERE project_id=? AND flow_id=? AND submit_user=? AND " +
                    " exec_id LIKE ? "
                    + "ORDER BY exec_id DESC LIMIT ?, ?";

    /*static String FETCH_EXECUTABLE_FLOW_ALL_HISTORY =
        "SELECT exec_id, enc_type, flow_data FROM execution_flows "
            + "WHERE project_id=? AND flow_id=? "
            + "ORDER BY exec_id ";*/

    @Override
    public List<ExecutableFlow> handle(final ResultSet rs) throws SQLException {
      if (!rs.next()) {
        return Collections.emptyList();
      }

      final List<ExecutableFlow> execFlows = new ArrayList<>();
      do {
        final int id = rs.getInt(1);
        final int encodingType = rs.getInt(2);
        final byte[] data = rs.getBytes(3);
        final String runDate = rs.getString(4);

        if (data != null) {
          final EncodingType encType = EncodingType.fromInteger(encodingType);
          try {
            final ExecutableFlow exFlow =
                    ExecutableFlow.createExecutableFlowFromObject(
                            GZIPUtils.transformBytesToObject(data, encType));
            exFlow.setRunDate(runDate);
            execFlows.add(exFlow);
          } catch (final IOException e) {
            throw new SQLException("Error retrieving flow data " + id, e);
          }
        }
      } while (rs.next());

      return execFlows;
    }
  }

  public static class FetchDetailExecutableFlows implements
          ResultSetHandler<List<ExecutableFlow>> {

    @Override
    public List<ExecutableFlow> handle(final ResultSet rs) throws SQLException {
      if (!rs.next()) {
        return Collections.emptyList();
      }
      final List<ExecutableFlow> execFlows = new ArrayList<>();
      do {
        final ExecutableFlow exFlow = new ExecutableFlow();
        exFlow.setExecutionId(rs.getInt(1));
        exFlow.setFlowId(rs.getString(2));
        exFlow.setStatus(Status.fromInteger(rs.getInt(3)));
        exFlow.setFlowType(rs.getInt(4));
        exFlow.setSubmitDepartmentId(rs.getString(5));
        execFlows.add(exFlow);
      } while (rs.next());

      return execFlows;
    }
  }

  /**
   * JDBC ResultSetHandler to fetch queued executions
   */
  private static class FetchQueuedExecutableFlows implements
          ResultSetHandler<List<Pair<ExecutionReference, ExecutableFlow>>> {

    // Select queued unassigned flows
    private static final String FETCH_QUEUED_EXECUTABLE_FLOW =
            "SELECT exec_id, enc_type, flow_data FROM execution_flows"
                    + " Where executor_id is NULL AND status = "
                    + Status.PREPARING.getNumVal();

    static String FETCH_FLOW_BY_STATUS =
            "SELECT exec_id, enc_type, flow_data, run_date FROM execution_flows where status=? and executor_id is null order by exec_id limit 20";

    @Override
    public List<Pair<ExecutionReference, ExecutableFlow>> handle(final ResultSet rs)
            throws SQLException {
      if (!rs.next()) {
        return Collections.emptyList();
      }

      final List<Pair<ExecutionReference, ExecutableFlow>> execFlows =
              new ArrayList<>();
      do {
        final int id = rs.getInt(1);
        final int encodingType = rs.getInt(2);
        final byte[] data = rs.getBytes(3);

        if (data == null) {
          ExecutionFlowDao.logger.error("Found a flow with empty data blob exec_id: " + id);
        } else {
          final EncodingType encType = EncodingType.fromInteger(encodingType);
          try {
            final ExecutableFlow exFlow =
                    ExecutableFlow.createExecutableFlowFromObject(
                            GZIPUtils.transformBytesToObject(data, encType));
            final ExecutionReference ref = new ExecutionReference(id);
            execFlows.add(new Pair<>(ref, exFlow));
          } catch (final IOException e) {
            throw new SQLException("Error retrieving flow data " + id, e);
          }
        }
      } while (rs.next());

      return execFlows;
    }
  }

  private static class FetchRecentlyFinishedFlows implements
          ResultSetHandler<List<ExecutableFlow>> {

    // Execution_flows table is already indexed by end_time
    private static final String FETCH_RECENTLY_FINISHED_FLOW =
            "SELECT exec_id, enc_type, flow_data FROM execution_flows "
                    + "WHERE end_time > ? AND status IN (?, ?, ?)";

    @Override
    public List<ExecutableFlow> handle(
            final ResultSet rs) throws SQLException {
      if (!rs.next()) {
        return Collections.emptyList();
      }

      final List<ExecutableFlow> execFlows = new ArrayList<>();
      do {
        final int id = rs.getInt(1);
        final int encodingType = rs.getInt(2);
        final byte[] data = rs.getBytes(3);

        if (data != null) {
          final EncodingType encType = EncodingType.fromInteger(encodingType);
          try {
            final ExecutableFlow exFlow =
                    ExecutableFlow.createExecutableFlowFromObject(
                            GZIPUtils.transformBytesToObject(data, encType));
            execFlows.add(exFlow);
          } catch (final IOException e) {
            throw new SQLException("Error retrieving flow data " + id, e);
          }
        }
      } while (rs.next());
      return execFlows;
    }
  }

  List<ExecutableFlow> fetchUserFlowHistory(final int skip, final int num, String user)
          throws ExecutorManagerException {
//alter table project_permissions add KEY permission_nameindex(name,project_id)

    String querySQL =
            "SELECT f.exec_id, f.enc_type, f.flow_data, f.run_date FROM execution_flows f INNER JOIN " +
                    " ( SELECT ef.exec_id FROM execution_flows ef " +
                    "   INNER JOIN project_permissions pp ON ef.project_id = pp.project_id " +
                    "   WHERE pp.name= ? " +
                    "   ORDER BY ef.exec_id DESC LIMIT ?, ? " +
                    " ) t " +
                    "ON f.`exec_id` = t.exec_id; ";

//    String querySQL = "SELECT ef.exec_id, ef.enc_type, ef.flow_data FROM execution_flows ef "
//        + "left join project_permissions pp on ef.project_id = pp.project_id "
//        + "WHERE pp.name=? "
//        + "ORDER BY exec_id DESC LIMIT ?, ? ";

    try {
      return this.dbOperator.query(querySQL, new FetchExecutableFlows(), user, skip, num);
    } catch (final SQLException e) {
      logger.error("执行查找用户:" + user + " Flow 历史失败 fetchUserFlowHistory Dao");
      throw new ExecutorManagerException("Error fetching User: " + user + "  flow History", e);
    }
  }


  List<ExecutableFlow> fetchUserFlowHistoryByAdvanceFilter(final String projContain,
                                                           final String flowContains,
                                                           final String execIdContain, final String userNameContains,
                                                           final String status, final long startTime, final long endTime, String runDate,
                                                           final int skip, final int num, int flowType)
          throws ExecutorManagerException {

    StringBuilder baseQuerySql = new StringBuilder(
            "SELECT exec_id, ef.enc_type, flow_data, run_date FROM execution_flows ef JOIN projects p ON ef.project_id = p.id");

    final List<Object> params = new ArrayList<>();

    boolean first = true;
    if (StringUtils.isNotBlank(projContain)) {
      first = wrapperSqlParam(first, projContain, baseQuerySql, "name", "LIKE", params);
    }

    // todo kunkun-tang: we don't need the below complicated logics. We should just use a simple way.
    if (StringUtils.isNotBlank(flowContains)) {
      first = wrapperSqlParam(first, flowContains, baseQuerySql, "flow_id", "LIKE", params);
    }

    if (StringUtils.isNotBlank(execIdContain)) {
      first = wrapperSqlParam(first, execIdContain, baseQuerySql, "exec_id", "LIKE", params);
    }

    String[] statusArray = status.split(",");
    if (!("0".equals(statusArray[0]))) {
      first = wrapperMultipleStatusSql(first, statusArray, baseQuerySql, "status", "in");
    }

    if (startTime > 0) {
      first = wrapperSqlParam(first, "" + startTime, baseQuerySql, "start_time", ">", params);
    }

    if (endTime > 0) {
      first = wrapperSqlParam(first, "" + endTime, baseQuerySql, "end_time", "<", params);
    }

    if (StringUtils.isNotBlank(runDate)) {
      first = wrapperSqlParam(first, runDate, baseQuerySql, "run_date", "=", params);
    }

    first = wrapperSqlParam(first, userNameContains, baseQuerySql, "submit_user", "=", params);

    if (flowType != -1) {
      wrapperSqlParam(first, "" + flowType, baseQuerySql, "flow_type", "=", params);
    }

    if (skip > -1 && num > 0) {
      baseQuerySql.append("  ORDER BY exec_id DESC LIMIT ?, ?");
      params.add(skip);
      params.add(num);
    }

    try {
      return this.dbOperator.query(baseQuerySql.toString(), new FetchExecutableFlows(), params.toArray());
    } catch (final SQLException e) {
      logger.error("执行根据条件查找用户:" + userNameContains + " Flow 历史失败 fetchUserFlowHistory Dao");
      throw new ExecutorManagerException("Error fetching active flows", e);
    }
  }

  List<ExecutableFlow> fetchUserFlowHistoryByAdvanceFilter(final String projContain, final String flowContains, final String execIdContain,
                                                           final String userNameContains, final String status, final long startTime, final long endTime,
                                                           String subsystem, String busPath, String department, String runDate,
                                                           final int skip, final int num, int flowType)
          throws ExecutorManagerException {

    StringBuilder baseQuerySql = new StringBuilder(
            "SELECT exec_id, ef.enc_type, flow_data, run_date FROM execution_flows ef JOIN projects p ON ef.project_id = p.id" +
                    "JOIN flow_business fb on ef.project_id = fb.project_id JOIN wtss_user wu on ef.submit_user = wu.username");

    final List<Object> params = new ArrayList<>();

    boolean first = true;
    if (StringUtils.isNotBlank(projContain)) {
      first = wrapperSqlParam(first, projContain, baseQuerySql, "name", "=", params);
    }

    // todo kunkun-tang: we don't need the below complicated logics. We should just use a simple way.
    if (StringUtils.isNotBlank(flowContains)) {
      first = wrapperSqlParam(first, flowContains, baseQuerySql, "ef.flow_id", "=", params);
    }

    if (StringUtils.isNotBlank(execIdContain)) {
      first = wrapperSqlParam(first, execIdContain, baseQuerySql, "exec_id", "=", params);
    }

    String[] statusArray = status.split(",");
    if (!("0".equals(statusArray[0]))) {
      first = wrapperMultipleStatusSql(first, statusArray, baseQuerySql, "status", "in");
    }

    if (startTime > 0) {
      first = wrapperSqlParam(first, "" + startTime, baseQuerySql, "start_time", ">", params);
    }

    if (endTime > 0) {
      first = wrapperSqlParam(first, "" + endTime, baseQuerySql, "end_time", "<", params);
    }

    if (StringUtils.isNotBlank(subsystem)) {
      first = wrapperSqlParam(first, subsystem, baseQuerySql, "subsystem", "=", params);
    }

    if (StringUtils.isNotBlank(busPath)) {
      first = wrapperSqlParam(first, busPath, baseQuerySql, "bus_path", "=", params);
    }

    if (StringUtils.isNotBlank(department)) {
      first = wrapperSqlParam(first, department, baseQuerySql, "department_name", "=", params);
    }

    if (StringUtils.isNotBlank(runDate)) {
      first = wrapperSqlParam(first, runDate, baseQuerySql, "run_date", "=", params);
    }

    first = wrapperSqlParam(first, userNameContains, baseQuerySql, "submit_user", "=", params);

    if (flowType != -1) {
      wrapperSqlParam(first, "" + flowType, baseQuerySql, "flow_type", "=", params);
    }

    if (skip > -1 && num > 0) {
      baseQuerySql.append("  ORDER BY exec_id DESC LIMIT ?, ?");
      params.add(skip);
      params.add(num);
    }

    try {
      return this.dbOperator.query(baseQuerySql.toString(), new FetchExecutableFlows(), params.toArray());
    } catch (final SQLException e) {
      logger.error("执行根据条件查找用户:" + userNameContains + " Flow 历史失败 fetchUserFlowHistory Dao");
      throw new ExecutorManagerException("Error fetching active flows", e);
    }
  }

  List<ExecutableFlow> fetchHistoryRecoverFlows(final String userNameContains)
          throws ExecutorManagerException {

    String query =
            "SELECT exec_id, enc_type, flow_data, run_date FROM execution_flows a,"
                    + "(select max(exec_id) as bexec_id from execution_flows "
                    + "WHERE flow_type = 2 or flow_type = 3 or flow_type = 4  or flow_type = 5 Group by repeat_id) b "
                    + "where a.exec_id = b.bexec_id";

    final List<Object> params = new ArrayList<>();

    boolean first = true;

    if(userNameContains != null && !userNameContains.isEmpty()){
      query += " AND ";
      query += " submit_user = ?";
      params.add(userNameContains);
    }

    //设置数据补采条件
//    if (first) {
//      query += " WHERE ";
//      first = false;
//    } else {
//      query += " AND ";
//    }
//    query += " flow_type = 2";
    query += " ORDER BY start_time DESC";

    try {
      return this.dbOperator.query(query, new FetchExecutableFlows(), params.toArray());
    } catch (final SQLException e) {
      throw new ExecutorManagerException("Error fetching history recover flows", e);
    }
  }

  List<ExecutableFlow> fetchHistoryRecoverFlowByRepeatId(final String repeatId)
          throws ExecutorManagerException {

    String query =
            "select exec_id, enc_type, flow_data, run_date "
                    + "from execution_flows a,"
                    + "(SELECT max(exec_id) as bexec_id FROM execution_flows WHERE repeat_id = ?)b "
                    + "where a.exec_id = b.bexec_id ";

    final List<Object> params = new ArrayList<>();

    params.add(repeatId);

    try {
      return this.dbOperator.query(query, new FetchExecutableFlows(), params.toArray());
    } catch (final SQLException e) {
      throw new ExecutorManagerException("Error fetching history recover flow by repeatId", e);
    }
  }

  List<ExecutableFlow> fetchHistoryRecoverFlowByFlowId(final String flowId, final String projectId)
          throws ExecutorManagerException {

    String query =
            "select exec_id, enc_type, flow_data, run_date "
                    + "from execution_flows "
                    + "where flow_id = ? and project_id = ? and repeat_id != '' "
                    + "order by start_time DESC limit 1 ";

    final List<Object> params = new ArrayList<>();

    params.add(flowId);

    params.add(projectId);

    try {
      return this.dbOperator.query(query, new FetchExecutableFlows(), params.toArray());
    } catch (final SQLException e) {
      throw new ExecutorManagerException("Error fetching history recover flow by flowId", e);
    }
  }


  List<ExecutableFlow> fetchHistoryRecoverFlows(final Map paramMap, final int skip, final int num)
          throws ExecutorManagerException {

    String query =
            "SELECT exec_id, enc_type, flow_data, run_date FROM execution_flows a,"
                    + "(select max(exec_id) as bexec_id from execution_flows "
                    + "WHERE flow_type = 2 or flow_type = 3 or flow_type = 4  or flow_type = 5 Group by repeat_id) b "
                    + "where a.exec_id = b.bexec_id";

    final List<Object> params = new ArrayList<>();

    boolean first = true;

    if(!paramMap.isEmpty()){

      String userName = String.valueOf(paramMap.get("userName"));

      if(userName != null && !userName.isEmpty()){

        query += " AND submit_user = ?";

        params.add(userName);
      }

    }

    if (skip > -1 && num > 0) {
      query += "  ORDER BY start_time DESC LIMIT ?, ?";
      params.add(skip);
      params.add(num);
    }

    try {
      return this.dbOperator.query(query, new FetchExecutableFlows(), params.toArray());
    } catch (final SQLException e) {
      throw new ExecutorManagerException("Error fetching history recover flows", e);
    }
  }

  List<ExecutableFlow> getProjectLastExecutableFlow(final int projectId, final String flowId)
          throws ExecutorManagerException {
    try {

      String query =
              "select exec_id, enc_type, flow_data, run_date "
                      + "from execution_flows "
                      + "where project_id = ? and flow_id = ? "
                      + "order by start_time DESC limit 1 ";

      return this.dbOperator.query(query, new FetchExecutableFlows(), projectId, flowId);
    } catch (final SQLException e) {
      logger.error("执行查找项目:" + projectId + " 最后一次执行工作流记录失败 getProjectLastExecutableFlow Dao");
      throw new ExecutorManagerException("Error fetching project: " + projectId + " last flow History", e);
    }
  }

  List<ExecutableFlow> fetchUserFlowHistoryByProjectIdAndFlowId(final int projectId, final String flowId,
                                                                final int skip, final int num, final String userName)
          throws ExecutorManagerException {
    try {
      return this.dbOperator.query(FetchExecutableFlows.FETCH_USER_EXECUTABLE_FLOW_HISTORY_BY_PROJECT_AND_FLOW,
              new FetchExecutableFlows(), projectId, flowId, userName, skip, num);
    } catch (final SQLException e) {
      throw new ExecutorManagerException("Error fetching flow history", e);
    }
  }

  List<ExecutableFlow> fetchUserFlowHistory(final String loginUser, final String projContain,
                                            final String flowContains, final String execIdContain,
                                            final String userNameContains, final String status,
                                            final long startTime, final long endTime, String runDate,
                                            final int skip, final int num, int flowType)
          throws ExecutorManagerException {

    StringBuilder querySql = new StringBuilder("SELECT ef.exec_id FROM execution_flows ef, projects p, project_permissions pp "
            + "WHERE ef.project_id = p.id AND ef.project_id = pp.project_id "
            + "AND pp.name=? ");
    final List<Object> params = new ArrayList<>();

    params.add(loginUser);

    boolean first = false;
    if (projContain != null && !projContain.isEmpty()) {
      first = wrapperSqlParam(first, projContain, querySql, "p.name", "like", params);
    }

    // todo kunkun-tang: we don't need the below complicated logics. We should just use a simple way.
    if (flowContains != null && !flowContains.isEmpty()) {
      first = wrapperSqlParam(first, flowContains, querySql, "flow_id", "like", params);
    }

    if (execIdContain != null && !execIdContain.isEmpty()) {
      first = wrapperSqlParam(first, execIdContain, querySql, "exec_id", "like", params);
    }

    String[] statusArray = status.split(",");
    if (!("0".equals(statusArray[0]))) {
      first = wrapperMultipleStatusSql(first, statusArray, querySql, "status", "in");
    }

    if (startTime > 0) {
      first = wrapperSqlParam(first, "" + startTime, querySql, "start_time", ">", params);
    }

    if (endTime > 0) {
      first = wrapperSqlParam(first, "" + endTime, querySql, "end_time", "<", params);
    }

    if (StringUtils.isNotBlank(runDate)) {
      first = wrapperSqlParam(first, runDate, querySql, "run_date", "=", params);
    }

    if (userNameContains != null && !userNameContains.isEmpty()) {
      first = wrapperSqlParam(first, userNameContains, querySql, "submit_user", "like", params);
    }

    if (flowType != -1) {
      first = wrapperSqlParam(first, "" + flowType, querySql, "flow_type", "=", params);
    }

    if (skip > -1 && num > 0) {
      querySql.append("  ORDER BY exec_id DESC LIMIT ?, ?");
      params.add(skip);
      params.add(num);
    }

    querySql.insert(0,
                    "SELECT ef1.exec_id, ef1.enc_type, ef1.flow_data, ef1.run_date FROM execution_flows ef1 inner join (")
            .append(") a on ef1.exec_id=a.exec_id");

    try {
      return this.dbOperator.query(querySql.toString(), new FetchExecutableFlows(), params.toArray());
    } catch (final SQLException e) {
      logger.error("执行根据条件查找用户:" + userNameContains + " Flow 历史失败 fetchUserFlowHistory Dao");
      throw new ExecutorManagerException("Error fetching active flows", e);
    }
  }

  List<ExecutableFlow> fetchUserFlowHistory(String loginUser, HistoryQueryParam param,
                                            int skip, int size) throws ExecutorManagerException {

    StringBuilder querySql = new StringBuilder("SELECT ef.exec_id FROM execution_flows ef "
            + "JOIN projects p on ef.project_id = p.id "
            + "JOIN project_permissions pp on ef.project_id = pp.project_id "
            + "WHERE pp.name=? ");
    final List<Object> params = new ArrayList<>();

    params.add(loginUser);

    wrapMultiConditionSql(false, querySql, param, skip, size, params);

    querySql.insert(0,
                    "SELECT ef1.exec_id, ef1.enc_type, ef1.flow_data, ef1.run_date FROM execution_flows ef1 inner join (")
            .append(") a on ef1.exec_id=a.exec_id");

    try {
      return this.dbOperator.query(querySql.toString(), new FetchExecutableFlows(), params.toArray());
    } catch (final SQLException e) {
      logger.error("执行根据条件查找用户:{} Flow 历史失败 fetchUserFlowHistory Dao", loginUser);
      throw new ExecutorManagerException("Error fetching active flows", e);
    }
  }

  List<ExecutableFlow> getTodayExecutableFlowData(final String userName)
          throws ExecutorManagerException {

    String query = "";

    final List<Object> params = new ArrayList<>();

    Calendar calendar = Calendar.getInstance();
    //获取当天凌晨毫秒数
    calendar.set(Calendar.HOUR_OF_DAY, 0);
    calendar.set(Calendar.MINUTE, 0);
    calendar.set(Calendar.SECOND, 0);
    calendar.set(Calendar.MILLISECOND, 1);
    params.add(calendar.getTimeInMillis());
    //获取当天24点毫秒数
    calendar.set(Calendar.HOUR_OF_DAY, 23);
    calendar.set(Calendar.MINUTE, 59);
    calendar.set(Calendar.SECOND, 59);
    params.add(calendar.getTimeInMillis());
    if(null != userName){
      query = "SELECT ef.exec_id, ef.enc_type, ef.flow_data, ef.run_date FROM execution_flows ef " +
              "WHERE (ef.submit_time >= ? AND ef.submit_time <= ?) AND ef.flow_type=3 AND " +
              "ef.`project_id` " +
              "IN (SELECT pp.`project_id` FROM project_permissions pp WHERE pp.`name` = ?) ;";
      params.add(userName);
    }else{
      query = "SELECT ef.exec_id, ef.enc_type, ef.flow_data, ef.run_date FROM execution_flows ef WHERE (ef.submit_time >= ? AND ef.submit_time <= ?) AND ef.flow_type=3 ";
    }

    try {
      return this.dbOperator.query(query, new FetchExecutableFlows(), params.toArray());
    } catch (final SQLException e) {
      logger.error("执行根据条件查找用户:" + userName + " 当天Flow执行记录失败 getTodayExecutableFlowData Dao");
      throw new ExecutorManagerException("Error fetching active flows", e);
    }
  }

  /**
   * 根据flowid查询其今天运行次数
   * @param flowId
   * @param usename
   * @return
   * @throws ExecutorManagerException
   */
  public Integer getTodayFlowRunTimesByFlowId(final String projectId, final String flowId, final String usename) throws ExecutorManagerException {
//    String COUNT = "SELECT count(ef.exec_id) FROM execution_flows ef, project_permissions pp " +
//             " WHERE ef.project_id = pp.project_id "
//            + " AND ef.flow_id = ? AND submit_time >= ? AND submit_time <= ? AND ef.flow_type = 3 ";
    String COUNT;
    if(usename == null) {
      // 管理员
      COUNT = "SELECT count(exec_id) FROM execution_flows  WHERE project_id = ? AND flow_id = ? AND submit_time >= ? AND submit_time <= ? AND flow_type = 3";
    } else {
      // 非管理员
      COUNT = "SELECT count(ef.exec_id) FROM execution_flows ef, project_permissions pp " +
              " WHERE ef.project_id = pp.project_id AND ef.project_id = ? AND ef.flow_id = ? AND ef.submit_time >= ? AND ef.submit_time <= ? AND ef.flow_type = 3";
    }
    List<Object> params = new ArrayList();
    params.add(projectId);
    params.add(flowId);
    Calendar calendar = Calendar.getInstance();
    //获取当天凌晨毫秒数
    calendar.set(Calendar.HOUR_OF_DAY, 0);
    calendar.set(Calendar.MINUTE, 0);
    calendar.set(Calendar.SECOND, 0);
    calendar.set(Calendar.MILLISECOND, 1);

    params.add(calendar.getTimeInMillis());
    //获取当天24点毫秒数
    calendar.set(Calendar.HOUR_OF_DAY, 23);
    calendar.set(Calendar.MINUTE, 59);
    calendar.set(Calendar.SECOND, 59);
    params.add(calendar.getTimeInMillis());

    if(usename != null){
      logger.info("非admin用户");
      COUNT += " AND pp.name = ?";
      params.add(usename);
    }
    try {
      return this.dbOperator.query(COUNT, new FetchFlowRunTimes(), params.toArray());
    } catch (final SQLException e) {
      logger.error("获取定时调度任务今天运行次数失败");
      throw new ExecutorManagerException("Error fetching active flows", e);
    }
  }

  List<ExecutableFlow> getTodayExecutableFlowDataNew(final String userName)
          throws ExecutorManagerException {

    String query = "";

    final List<Object> params = new ArrayList<>();
    Calendar calendar = Calendar.getInstance();
    //获取当天凌晨毫秒数
    calendar.set(Calendar.HOUR_OF_DAY, 0);
    calendar.set(Calendar.MINUTE, 0);
    calendar.set(Calendar.SECOND, 0);
    calendar.set(Calendar.MILLISECOND, 1);
    params.add(calendar.getTimeInMillis());
    //获取当天24点毫秒数
    calendar.set(Calendar.HOUR_OF_DAY, 23);
    calendar.set(Calendar.MINUTE, 59);
    calendar.set(Calendar.SECOND, 59);
    params.add(calendar.getTimeInMillis());

    if(null != userName){
      // 非管理员
      query = "SELECT ef.exec_id, ef.enc_type, ef.flow_data, ef.run_date FROM execution_flows ef " +
              "WHERE " +
              "ef.exec_id IN  (SELECT MAX(exec_id) FROM execution_flows WHERE  submit_time >= ? AND submit_time <= ? AND flow_type=3  GROUP BY project_id, flow_id) "
              +
              "AND " +
              "ef.`project_id` IN (SELECT pp.`project_id` FROM project_permissions pp WHERE pp.`name` = ?);";
      params.add(userName);
    }else{
      // 管理员
      query =
              "SELECT ef.exec_id, ef.enc_type, ef.flow_data, ef.run_date FROM execution_flows ef WHERE ef.flow_type=3 AND ef.exec_id IN "
                      +
                      "(SELECT MAX(exec_id) FROM execution_flows WHERE  submit_time >= ? AND submit_time <= ? AND flow_type=3  GROUP BY project_id, flow_id);";
    }

    try {
      return this.dbOperator.query(query, new FetchExecutableFlows(), params.toArray());
    } catch (final SQLException e) {
      logger.error("执行根据条件查找用户:" + userName + " 当天Flow执行记录失败 getTodayExecutableFlowData Dao");
      throw new ExecutorManagerException("Error fetching active flows", e);
    }
  }

  List<ExecutableFlow> getRealTimeExecFlowDataDao(final String userName)
          throws ExecutorManagerException {

    String query = "";

    final List<Object> params = new ArrayList<>();

    if(null != userName){
//      query = "SELECT ef.exec_id, ef.enc_type, ef.flow_data FROM execution_flows ef, projects p, project_permissions pp "
//          + "WHERE ef.project_id = p.id AND ef.project_id = pp.project_id "
//          + "AND pp.name=? ";
      query = "select " +
              " exec_id,enc_type,flow_data, run_date " +
              " from execution_flows ef where ef.status in(60,70,80) and exec_id in ( " +
              " select exec_id from ( " +
              " SELECT " +
              " ef.exec_id " +
              " FROM " +
              " execution_flows ef, " +
              " project_permissions pp " +
              "  WHERE " +
              " ef.project_id = pp.project_id " +
              " AND pp. NAME = ? " +
              " ORDER BY " +
              " ef.start_time DESC " +
              " LIMIT 10 " +
              " ) a " +
              " )";
      params.add(userName);
    }else{
      query = "SELECT ef.exec_id, ef.enc_type, ef.flow_data, ef.run_date FROM execution_flows ef "
              + " LEFT JOIN projects p ON ef.project_id = p.id "
              + " WHERE ef.status in(60,70,80) "
              + " ORDER BY ef.start_time DESC LIMIT 10 ;";
    }

    try {
      return this.dbOperator.query(query, new FetchExecutableFlows(), params.toArray());
    } catch (final SQLException e) {
      logger.error("执行根据条件查找用户:" + userName + " Flow 历史失败 fetchUserFlowHistory Dao");
      throw new ExecutorManagerException("Error fetching active flows", e);
    }
  }

  public List<Integer> getRunningExecByLock(Integer projectId, String flowId) {
    try {
      return dbOperator.query(FetchLockHandler.FETCH_LOCK_RESOURCE,
              new FetchLockHandler(), "%:" + projectId + ":" + flowId, System.currentTimeMillis());
    } catch (Exception e) {
      logger.error("Failed to getRunningExecByLock", e);
    }
    return new ArrayList<>();
  }

  public static class FetchLockHandler implements
          ResultSetHandler<List<Integer>> {
    static String FETCH_LOCK_RESOURCE =
            "select lock_resource from distribute_lock dl WHERE dl.lock_resource like ? and dl.timeout >= ?";

    @Override
    public List<Integer> handle(final ResultSet rs) throws SQLException {
      if (!rs.next()) {
        logger.info("there is no exist lock");
        return new ArrayList<>();
      }

      final List<Integer> execIdList =
              new ArrayList<>();
      do {
        try {
          final String lockResource = rs.getString(1);
          if (StringUtils.isNotEmpty(lockResource)) {
            String[] arr = lockResource.split(":");
            execIdList.add(Integer.parseInt(arr[0]));
          }
        } catch (Exception e) {
          logger.error("Failed to handle data", e);
        }
      } while (rs.next());

      return execIdList;

    }
  }

  public Set<DmsBusPath> getDmsBusPathFromDb(String jobCode) {
    try {
      return dbOperator
              .query(DmsBusPathHandler.BUS_PATH_RESOURCE, new DmsBusPathHandler(), jobCode);
    } catch (Exception e) {
      logger.warn("Failed to getDmsBusPathFromDb, jobCode {}", jobCode, e);
    }
    return null;
  }

  public Set<DmsBusPath> getDmsBusPathFromDb(String jobCode, String updateTime) {
    try {
      return dbOperator
              .query(DmsBusPathHandler.BUS_PATH_SELECT_BY_UPDATE_TIME, new DmsBusPathHandler(), jobCode, updateTime);
    } catch (Exception e) {
      logger.warn("Failed to getDmsBusPathFromDb, jobCode {}, updateTime {}", jobCode, updateTime, e);
    }
    return null;
  }

  public void insertOrUpdate(DmsBusPath dmsBusPath) {
    try {
      dbOperator
              .update(DmsBusPathHandler.INSERT_OR_UPDATE_BUS_PATH,dmsBusPath.getBusPathName(), dmsBusPath.getBusPathId(),
                      dmsBusPath.getJobCode(), dmsBusPath.getStatus(), dmsBusPath.getModifiedTime());
    } catch (Exception e) {
      logger.warn("Failed to insert dms bus path, jobCode {}", dmsBusPath.getJobCode(), e);
    }
  }

  public static class DmsBusPathHandler implements
          ResultSetHandler<Set<DmsBusPath>> {


    static String INSERT_OR_UPDATE_BUS_PATH =
            "INSERT INTO bus_path_nodes(bus_path_name, bus_path_id, job_code, status, modified_time) values (?,?,?,?,?)"
                    + "ON DUPLICATE KEY UPDATE modified_time = VALUES(modified_time)";

    static String BUS_PATH_RESOURCE =
            "select bus_path_id,bus_path_name,job_code,status from bus_path_nodes WHERE job_code = ?";

    static String BUS_PATH_SELECT_BY_UPDATE_TIME =
            "select bus_path_id,bus_path_name,job_code,status from bus_path_nodes WHERE job_code = ? and modified_time > ?";

    @Override
    public Set<DmsBusPath> handle(final ResultSet rs) throws SQLException {
      final Set<DmsBusPath> jobCodeSet = new HashSet<>();
      if (!rs.next()) {
        return jobCodeSet;
      }
      do {
        DmsBusPath dmsBusPath = new DmsBusPath();
        dmsBusPath.setBusPathId(rs.getString(1));
        dmsBusPath.setBusPathName(rs.getString(2));
        dmsBusPath.setJobCode(rs.getString(3));
        dmsBusPath.setStatus(rs.getString(4));
        jobCodeSet.add(dmsBusPath);
      } while (rs.next());
      return jobCodeSet;
    }
  }

  public long getFinalScheduleTime(long triggerInitTime) {
    try {
      return dbOperator
              .query(LongHandler.GET_FINAL_SCHEDULE_TIME, new LongHandler(), triggerInitTime);
    } catch (Exception e) {
      logger.error("get final schedule time error", e);
    }
    return 0L;
  }

  public static class LongHandler implements ResultSetHandler<Long> {

    public static String GET_FINAL_SCHEDULE_TIME = "select max(submit_time) from execution_flows where submit_time<? and flow_type=3";

    @Override
    public Long handle(final ResultSet rs) throws SQLException {
      if (!rs.next()) {
        return 0L;
      }

      return rs.getLong(1);
    }
  }

  /**
   * Select flows that are not in finished status, including both dispatched and non-dispatched
   * flows.
   *
   * @return unfinished flow exec_ids
   * @throws ExecutorManagerException the executor manager exception
   */
  List<Integer> selectUnfinishedFlows() throws ExecutorManagerException {
    try {
      return this.dbOperator.query(SelectFromExecutionFlows.SELECT_EXECUTION_BASE_QUERY
                      + "WHERE status NOT IN ("
                      + getTerminalStatusesString() + ")",
              new SelectFromExecutionFlows());
    } catch (final SQLException e) {
      throw new ExecutorManagerException("Error fetching unfinished flows", e);
    }
  }

  List<Integer> selectUnfinishedFlows(final int projectId, final String flowId) throws ExecutorManagerException {
    try {
      return this.dbOperator.query(SelectFromExecutionFlows.SELECT_EXECUTION_BASE_QUERY
                      + "WHERE project_id=? AND flow_id=? AND "
                      + "status NOT IN ("
                      + getTerminalStatusesString() + ")",
              new SelectFromExecutionFlows(), projectId, flowId);
    } catch (final SQLException e) {
      throw new ExecutorManagerException("Error fetching unfinished flows", e);
    }
  }

  public List<Integer> selectQueuedFlows(final Status status)
          throws ExecutorManagerException {
    try {
      return this.dbOperator.query(SelectFromExecutionFlows.SELECT_QUEUED_EXECUTABLE_FLOW,
              new SelectFromExecutionFlows(), status.getNumVal());
    } catch (final SQLException e) {
      throw new ExecutorManagerException("Error fetching active flows", e);
    }
  }

  /**
   * Generates a string representing terminating flow status num values: "50, 60, 65, 70"
   * @return
   */
  static String getTerminalStatusesString() {
    final List<Integer> list = TERMINAL_STATUSES.stream().map(Status::getNumVal).collect(
            Collectors.toList());
    return StringUtils.join(list, ", ");
  }
}
