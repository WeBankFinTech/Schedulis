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
import java.util.*;
import java.util.stream.Collectors;
import javax.inject.Inject;
import javax.inject.Singleton;
import org.apache.commons.dbutils.ResultSetHandler;
import org.apache.commons.lang.StringUtils;
import org.slf4j.LoggerFactory;
import org.slf4j.Logger;

import static java.util.stream.Collectors.joining;

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
        + "use_executor, repeat_id) values (?,?,?,?,?,?,?,?,?, ?)";
    final long submitTime = System.currentTimeMillis();
    flow.setStatus(Status.PREPARING);
    flow.setSubmitTime(submitTime);

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
		  executorId, flow.getRepeatId());
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

  List<ExecutableFlow> fetchMaintainedFlowHistory(String username, List<Integer> projectIds, int skip, int num)
          throws ExecutorManagerException {
    try {
      String projectIdsStr = projectIds.stream()
              .map(Objects::toString)
              .collect(joining(",", "(", ")"));
      String querySQL = "SELECT ef.exec_id, ef.enc_type, ef.flow_data FROM execution_flows ef " +
              "WHERE ef.project_id IN " + projectIdsStr + " " +
              "ORDER BY exec_id DESC LIMIT ?, ?";
      return this.dbOperator.query(querySQL, new FetchExecutableFlows(), skip, num);
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

  public List<Pair<ExecutionReference, ExecutableFlow>> fetchQueuedFlows()
      throws ExecutorManagerException {
    try {
      return this.dbOperator.query(FetchQueuedExecutableFlows.FETCH_QUEUED_EXECUTABLE_FLOW,
          new FetchQueuedExecutableFlows());
    } catch (final SQLException e) {
      throw new ExecutorManagerException("Error fetching active flows", e);
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

  List<ExecutableFlow> fetchFlowHistory(final String projContain, final String flowContains,final String execIdContain,
      final String userNameContains, final String status,
      final long startTime, final long endTime,
      final int skip, final int num, final int flowType)
      throws ExecutorManagerException {

    StringBuilder querySql = new StringBuilder("SELECT exec_id, ef.enc_type, flow_data FROM execution_flows ef JOIN projects p ON ef.project_id = p.id");
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

  List<ExecutableFlow> fetchMaintainedFlowHistory(final String projContain, final String flowContains,final String execIdContain,
                                        final String userNameContains, final String status,
                                        final long startTime, final long endTime,
                                        final int skip, final int num, final int flowType, String username, List<Integer> projectIds)
          throws ExecutorManagerException {
    String projectIdsStr = projectIds.stream()
            .map(Object::toString)
            .collect(joining(",", "(", ")"));
    StringBuilder querySql = new StringBuilder("SELECT ef.exec_id, ef.enc_type, ef.flow_data FROM execution_flows ef, projects p "
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
    if (action.equalsIgnoreCase("like")) {
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
      query = "SELECT ef.exec_id, ef.enc_type, ef.flow_data FROM execution_flows ef, projects p, project_permissions pp "
          + "WHERE ef.project_id = p.id AND ef.project_id = pp.project_id "
          + "AND pp.name=? ";
      params.add(userNameContains);
    }else{
      query = "SELECT exec_id, ef.enc_type, flow_data FROM execution_flows ef JOIN projects p ON ef.project_id = p.id";
    }

    // todo kunkun-tang: we don't need the below complicated logics. We should just use a simple way.
    if (flowContains != null && !flowContains.isEmpty()) {
      query += " AND ";
      query += " (ef.exec_id LIKE ? OR ef.flow_id LIKE ? OR p.name LIKE ?) ";
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
    query = "SELECT ef.exec_id, ef.enc_type, ef.flow_data, ef.project_id as project_id FROM execution_flows ef, projects p "
              + "WHERE ef.project_id = p.id "
              + "AND ef.project_id IN " + projectIdsStr + " ";
    // todo kunkun-tang: we don't need the below complicated logics. We should just use a simple way.
    if (flowContains != null && !flowContains.isEmpty()) {
      query += " AND ";
      query += " (ef.exec_id LIKE ? OR ef.flow_id LIKE ? OR p.name LIKE ?) ";
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

  List<ExecutableFlow> fetchFlowAllHistory(final int projectId, final String flowId, final String user)
      throws ExecutorManagerException {

    String querySql = "SELECT exec_id, enc_type, flow_data FROM execution_flows "
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

  void updateExecutableFlow(final ExecutableFlow flow) throws ExecutorManagerException {
    logger.info("current flow status is {}.", flow.getStatus());
    updateExecutableFlow(flow, EncodingType.GZIP);
  }

  private void updateExecutableFlow(final ExecutableFlow flow, final EncodingType encType)
      throws ExecutorManagerException {
    final String UPDATE_EXECUTABLE_FLOW_DATA =
        "UPDATE execution_flows "
            + "SET status=?,update_time=?,start_time=?,end_time=?,enc_type=?,flow_data=?,flow_type=? "
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
      throw new ExecutorManagerException("Error encoding the execution flow.");
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
          flow.getExecutionId());
    } catch (final SQLException e) {
      throw new ExecutorManagerException("Error updating flow.", e);
    }
  }

  public ExecutableFlow fetchExecutableFlow(final int execId) throws ExecutorManagerException {
    final FetchExecutableFlows flowHandler = new FetchExecutableFlows();
    try {
      final List<ExecutableFlow> properties = this.dbOperator
          .query(FetchExecutableFlows.FETCH_EXECUTABLE_FLOW, flowHandler, execId);
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

  public static class FetchExecutableFlows implements
      ResultSetHandler<List<ExecutableFlow>> {

    static String FETCH_EXECUTABLE_FLOW_BY_REPEAT_ID = "SELECT ef.exec_id, ef.enc_type, ef.flow_data FROM execution_flows ef " +
        " WHERE ef.`repeat_id` = ? AND ef.status IN (20, 30, 80);";

    static String FETCH_EXECUTABLE_FLOW_BY_START_TIME =
        "SELECT ef.exec_id, ef.enc_type, ef.flow_data FROM execution_flows ef WHERE project_id=? "
            + "AND flow_id=? AND start_time >= ? ORDER BY start_time DESC";
    static String FETCH_BASE_EXECUTABLE_FLOW_QUERY =
        "SELECT ef.exec_id, ef.enc_type, ef.flow_data FROM execution_flows ef";
    static String FETCH_EXECUTABLE_FLOW =
        "SELECT exec_id, enc_type, flow_data FROM execution_flows "
            + "WHERE exec_id=?";
    static String FETCH_ALL_EXECUTABLE_FLOW_HISTORY =
        "SELECT exec_id, enc_type, flow_data FROM execution_flows "
            + "ORDER BY exec_id DESC LIMIT ?, ?";
    static String FETCH_EXECUTABLE_FLOW_HISTORY =
        "SELECT exec_id, enc_type, flow_data FROM execution_flows "
            + "WHERE project_id=? AND flow_id=? "
            + "ORDER BY exec_id DESC LIMIT ?, ?";
    static String FETCH_EXECUTABLE_FLOW_BY_STATUS =
        "SELECT exec_id, enc_type, flow_data FROM execution_flows "
            + "WHERE project_id=? AND flow_id=? AND status=? "
            + "ORDER BY exec_id DESC LIMIT ?, ?";
    static String FETCH_USER_EXECUTABLE_FLOW_HISTORY =
        "SELECT exec_id, enc_type, flow_data FROM execution_flows "
            + "WHERE submit_user=? "
            + "ORDER BY exec_id DESC LIMIT ?, ?";
    static String FETCH_USER_EXECUTABLE_FLOW_HISTORY_BY_PROJECT_AND_FLOW =
        "SELECT exec_id, enc_type, flow_data FROM execution_flows "
            + "WHERE project_id=? AND flow_id=? AND submit_user=? "
            + "ORDER BY exec_id DESC LIMIT ?, ?";
    static String FETCH_EXECUTABLE_FLOW_ALL_HISTORY =
        "SELECT exec_id, enc_type, flow_data FROM execution_flows "
            + "WHERE project_id=? AND flow_id=? "
            + "ORDER BY exec_id ";

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

    String querySQL = "select exec_id,enc_type, flow_data from execution_flows where exec_id in " +
            "( " +
            "select exec_id from ( " +
            "SELECT exec_id FROM execution_flows ef  " +
            "left join project_permissions pp on ef.project_id = pp.project_id  " +
            "WHERE pp.name=? " +
            "ORDER BY exec_id DESC LIMIT ?, ? " +
            ") a " +
            ") order by exec_id DESC";

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


  List<ExecutableFlow> fetchUserFlowHistoryByAdvanceFilter(final String projContain, final String flowContains,
                                                           final String execIdContain, final String userNameContains,
                                                           final String status, final long startTime, final long endTime,
                                                           final int skip, final int num, int flowType)
                                                           throws ExecutorManagerException {

    StringBuilder baseQuerySql = new StringBuilder("SELECT exec_id, ef.enc_type, flow_data FROM execution_flows ef JOIN projects p ON ef.project_id = p.id");

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
        "SELECT exec_id, enc_type, flow_data FROM execution_flows a,"
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
        "select exec_id, enc_type, flow_data "
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
        "select exec_id, enc_type, flow_data "
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
        "SELECT exec_id, enc_type, flow_data FROM execution_flows a,"
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
          "select exec_id, enc_type, flow_data "
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
                                            final String flowContains,final String execIdContain,
      final String userNameContains, final String status,
      final long startTime, final long endTime,
      final int skip, final int num, int flowType)
      throws ExecutorManagerException {

    StringBuilder querySql = new StringBuilder("SELECT ef.exec_id, ef.enc_type, ef.flow_data FROM execution_flows ef, projects p, project_permissions pp "
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

    try {
      return this.dbOperator.query(querySql.toString(), new FetchExecutableFlows(), params.toArray());
    } catch (final SQLException e) {
      logger.error("执行根据条件查找用户:" + userNameContains + " Flow 历史失败 fetchUserFlowHistory Dao");
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
      query = "SELECT ef.exec_id, ef.enc_type, ef.flow_data FROM execution_flows ef " +
              "WHERE (ef.submit_time >= ? AND ef.submit_time <= ?) AND ef.flow_type=3 AND " +
              "ef.`project_id` " +
              "IN (SELECT pp.`project_id` FROM project_permissions pp WHERE pp.`name` = ?) ;";
      params.add(userName);
    }else{
      query = "SELECT ef.exec_id, ef.enc_type, ef.flow_data FROM execution_flows ef WHERE (ef.submit_time >= ? AND ef.submit_time <= ?) AND ef.flow_type=3 ";
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
      query = "SELECT ef.exec_id, ef.enc_type, ef.flow_data FROM execution_flows ef " +
              "WHERE " +
              "ef.exec_id IN  (SELECT MAX(exec_id) FROM execution_flows WHERE  submit_time >= ? AND submit_time <= ? AND flow_type=3  GROUP BY project_id, flow_id) " +
              "AND " +
              "ef.`project_id` IN (SELECT pp.`project_id` FROM project_permissions pp WHERE pp.`name` = ?);";
      params.add(userName);
    }else{
      // 管理员
      query = "SELECT ef.exec_id, ef.enc_type, ef.flow_data FROM execution_flows ef WHERE ef.flow_type=3 AND ef.exec_id IN " +
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
              " exec_id,enc_type,flow_data " +
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
      query = "SELECT ef.exec_id, ef.enc_type, ef.flow_data FROM execution_flows ef "
          + " LEFT JOIN projects p ON ef.project_id = p.id "
          +" WHERE ef.status in(60,70,80) "
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
      logger.error(e.getMessage(), e);
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
          logger.error(e.getMessage(), e);
        }
      } while (rs.next());

      return execIdList;

    }
  }

}
