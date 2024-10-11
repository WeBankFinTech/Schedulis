/*
 * Copyright 2020 WeBank
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.webank.wedatasphere.schedulis.common.executor;

import azkaban.db.EncodingType;
import azkaban.db.DatabaseOperator;
import azkaban.db.SQLTransaction;
import azkaban.executor.ExecutorManagerException;
import azkaban.executor.Status;
import azkaban.utils.GZIPUtils;
import azkaban.utils.JSONUtils;
import java.io.IOException;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.*;
import javax.inject.Inject;
import javax.inject.Singleton;
import org.apache.commons.collections.MapUtils;
import org.apache.commons.dbutils.ResultSetHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static java.util.stream.Collectors.joining;

@Singleton
public class ExecutionRecoverDao {

  private static final Logger logger = LoggerFactory.getLogger(ExecutionRecoverDao.class);
  private final DatabaseOperator dbOperator;

  @Inject
  public ExecutionRecoverDao(final DatabaseOperator dbOperator) {
    this.dbOperator = dbOperator;
  }

  /**
   * 创建历史补采数据库记录
   * @param recover
   * @throws ExecutorManagerException
   */
  public synchronized Integer uploadExecutableRecoverFlow(final ExecutionRecover recover)
      throws ExecutorManagerException {
    final String INSERT_EXECUTABLE_RECOVER_FLOW = "INSERT INTO execution_recover_flows "
        + "(recover_status, recover_start_time, recover_end_time, ex_interval, "
        + "now_exec_id, project_id, flow_id, "
        + "submit_user, submit_time, update_time ) "
        + "values (?,?,?,?,?,?,?,?,?,?)";
    final long submitTime = System.currentTimeMillis();

    final SQLTransaction<Long> insertAndGetLastID = transOperator -> {
      transOperator.update(INSERT_EXECUTABLE_RECOVER_FLOW,

          recover.getRecoverStatus().getNumVal(),
          recover.getRecoverStartTime(),
          recover.getRecoverEndTime(),
          recover.getExInterval(),

          recover.getNowExecutionId(),
          recover.getProjectId(),
          recover.getFlowId(),

          recover.getSubmitUser(),
          submitTime,
          submitTime);
      transOperator.getConnection().commit();
      return transOperator.getLastInsertId();
    };
    try {
      final long id = this.dbOperator.transaction(insertAndGetLastID);
      logger.info("History Recover given " + recover.getRecoverId() + " given id " + id);
      recover.setRecoverId((int)id);
      updateExecutableRecoverFlow(recover);
    } catch (final SQLException e) {
      throw new ExecutorManagerException("Error creating History Recover.", e);
    }
    return recover.getRecoverId();
  }

  /**
   * 历史补采数据更新方法
   * @param recover
   * @throws ExecutorManagerException
   */
  public void updateExecutableRecoverFlow(final ExecutionRecover recover) throws ExecutorManagerException {
    updateExecutableRecoverFlow(recover, EncodingType.GZIP);
  }

  /**
   * 历史补采数据更新实现方法
   * @param recover
   * @param encType
   * @throws ExecutorManagerException
   */
  private void updateExecutableRecoverFlow(final ExecutionRecover recover, final EncodingType encType)
      throws ExecutorManagerException {
    final String UPDATE_EXECUTABLE_RECOVER_FLOW_DATA =
        "UPDATE execution_recover_flows "
            + "SET recover_status=?, now_exec_id=?, update_time=?, start_time=?, end_time=?, enc_type=?, recover_data=? "
            + "WHERE recover_id=? ";

    final String json = JSONUtils.toJSON(recover.toObject());
    byte[] data = null;
    try {
      final byte[] stringData = json.getBytes("UTF-8");
      data = stringData;
      // Todo kunkun-tang: use a common method to transform stringData to data.
      if (encType == EncodingType.GZIP) {
        data = GZIPUtils.gzipBytes(stringData);
      }
    } catch (final IOException e) {
      throw new ExecutorManagerException("Error encoding the execution recover flow.");
    }

    try {
      this.dbOperator.update(UPDATE_EXECUTABLE_RECOVER_FLOW_DATA,
          recover.getRecoverStatus().getNumVal(),
          recover.getNowExecutionId(),
          recover.getUpdateTime(),
          recover.getStartTime(),
          recover.getEndTime(),
          encType.getNumVal(),
          data,
          recover.getRecoverId());
    } catch (final SQLException e) {
      throw new ExecutorManagerException("Error updating History Recover.", e);
    }
  }

  /**
   * 历史补采查找结果处理内部类
   */
  public static class FetchExecutionRecoverFlows implements ResultSetHandler<List<ExecutionRecover>> {

    static String LIST_BASE_EXECUTABLE_RECOVER_FLOW_QUERY =
        "SELECT recover_id, recover_status, enc_type, recover_data FROM execution_recover_flows "
            + "WHERE recover_status=30 ";
    static String LIST_EXECUTABLE_RECOVER_FLOW_BY_ID =
        "SELECT recover_id, recover_status, enc_type, recover_data FROM execution_recover_flows "
            + "WHERE recover_id=?";
    static String LIST_ALL_EXECUTABLE_RECOVER_FLOW_HISTORY =
        "SELECT recover_id, recover_status, enc_type, recover_data FROM execution_recover_flows "
            + "ORDER BY exec_id DESC LIMIT ?, ?";
    static String LIST_EXECUTABLE_FLOW_HISTORY =
        "SELECT exec_id, recover_status, enc_type, flow_data FROM execution_recover_flows "
            + "WHERE project_id=? AND flow_id=? "
            + "ORDER BY exec_id DESC LIMIT ?, ?";
    static String LIST_EXECUTABLE_FLOW_BY_STATUS =
        "SELECT exec_id, recover_status, enc_type, flow_data FROM execution_recover_flows "
            + "WHERE project_id=? AND flow_id=? AND status=? "
            + "ORDER BY exec_id DESC LIMIT ?, ?";
    static String LIST_USER_EXECUTABLE_RECOVER_FLOW_HISTORY =
        "SELECT recover_id, recover_status, recover_data FROM execution_recover_flows "
            + "WHERE submit_user=? "
            + "ORDER BY recover_id DESC LIMIT ?, ?";

    private static String IS_NOT_FINISH_RECOVER =
            "SELECT recover_id, recover_status, enc_type, recover_data FROM execution_recover_flows "
                    + "WHERE recover_status in (20,30)";

    @Override
    public List<ExecutionRecover> handle(final ResultSet rs) throws SQLException {
      if (!rs.next()) {
        return Collections.emptyList();
      }

      final List<ExecutionRecover> execFlows = new ArrayList<>();
      do {
        final int id = rs.getInt(1);
        final int status = rs.getInt(2);
        final int encodingType = rs.getInt(3);
        final byte[] data = rs.getBytes(4);

        if (data != null) {
          final EncodingType encType = EncodingType.fromInteger(encodingType);
          final Object flowObj;

          /**
           * The below code is a duplicate against many places, like azkaban.database.EncodingType
           * TODO kunkun-tang: Extract these duplicates to a single static method.
           */
          try {
            // Convoluted way to inflate strings. Should find common package
            // or helper function.
            if (encType == EncodingType.GZIP) {
              // Decompress the sucker.
              final String jsonString = GZIPUtils.unGzipString(data, "UTF-8");
              flowObj = JSONUtils.parseJSONFromString(jsonString);
            } else {
              final String jsonString = new String(data, "UTF-8");
              flowObj = JSONUtils.parseJSONFromString(jsonString);
            }

            final ExecutionRecover exFlow =
                ExecutionRecover.createExecutionRecoverFromObject(flowObj);
            exFlow.setRecoverStatus(Status.fromInteger(status));
            execFlows.add(exFlow);
          } catch (final IOException e) {
            throw new SQLException("Error retrieving History Recover data " + id, e);
          }
        }
      } while (rs.next());

      return execFlows;
    }
  }

  public ExecutionRecover getHistoryRecoverFlows(final Integer recoverId)
      throws ExecutorManagerException {

    String query =
        "SELECT recover_id, recover_status, enc_type, recover_data FROM execution_recover_flows where recover_id = ?";

    final List<Object> params = new ArrayList<>();

    params.add(recoverId);

    try {
      List<ExecutionRecover> recoverList = this.dbOperator.query(query, new FetchExecutionRecoverFlows(), params.toArray());
      if(recoverList.size() > 0){
        return recoverList.get(0);
      }else {
        return null;
      }
    } catch (final SQLException e) {
      throw new ExecutorManagerException("Error fetching history recover flows", e);
    }
  }

  List<ExecutionRecover> listHistoryRecoverFlowByFlowId(final String flowId, final String projectId)
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
      return this.dbOperator.query(query, new FetchExecutionRecoverFlows(), params.toArray());
    } catch (final SQLException e) {
      throw new ExecutorManagerException("Error fetching history recover flow by flowId", e);
    }
  }

  /**
   * 历史重跑 历史数据分页 查找 方法
   * @param paramMap 查找参数合集
   * @param skip 开始条数
   * @param num  结束条数
   * @return
   * @throws ExecutorManagerException
   */
  public List<ExecutionRecover> listHistoryRecoverFlows(final Map paramMap, final int skip, final int num)
      throws ExecutorManagerException {

    StringBuilder querySql = new StringBuilder("SELECT recover_id, recover_status, erf.enc_type, recover_data FROM execution_recover_flows erf "
            + "join projects p on erf.project_id = p.id "
            + "WHERE recover_status=30 ");

    final List<Object> params = new ArrayList<>();

    boolean first = false;

    if(!paramMap.isEmpty()){

      String projContain = MapUtils.getString(paramMap, "projContain");
      if (projContain != null && !projContain.isEmpty()) {
        wrapperSqlParam(first, paramMap, "projContain", null, "name", "like", querySql, params);
      }

      //String flowContains = String.valueOf(paramMap.get("flowContains"));
      String flowContains = MapUtils.getString(paramMap, "flowContains");

      // todo kunkun-tang: we don't need the below complicated logics. We should just use a simple way.
      if (flowContains != null && !flowContains.isEmpty()) {
        wrapperSqlParam(first, paramMap, "flowContains", null, "flow_id", "like", querySql, params);
      }

      Integer recoverStatus = MapUtils.getInteger(paramMap, "recoverStatus");

      if (recoverStatus != null && recoverStatus != 0) {
        wrapperSqlParam(first, paramMap, "recoverStatus", "int", "recover_status", "=", querySql, params);
      }

      String userName = MapUtils.getString(paramMap, "userName");

      if(userName != null && !userName.isEmpty()){
        wrapperSqlParam(first, paramMap, "userName", null, "submit_user", "=", querySql, params);
      }

    }

    if (skip > -1 && num > 0) {
      querySql.append("  ORDER BY recover_id DESC LIMIT ?, ?");
      params.add(skip);
      params.add(num);
    }

    try {
      return this.dbOperator.query(querySql.toString(), new FetchExecutionRecoverFlows(), params.toArray());
    } catch (final SQLException e) {
      throw new ExecutorManagerException("Error fetching history recover flows", e);
    }
  }

  /**
   * 历史重跑 历史数据分页 查找 方法
   * @param maintainedProjectIds 运维管理员运维的所有工程ID
   * @param skip
   * @param num
   * @return
   */
  public List<ExecutionRecover> listMaintainedHistoryRecoverFlows(String username, List<Integer> maintainedProjectIds, int skip, int num)
          throws ExecutorManagerException {
    String projectIds = maintainedProjectIds.stream()
            .map(Objects::toString)
            .collect(joining(",", "(", ")"));
    String querySQL = "SELECT recover_id, recover_status, erf.enc_type, recover_data, erf.project_id AS project_id FROM execution_recover_flows erf "
            + "JOIN projects p ON erf.project_id = p.id WHERE recover_status=30"
            + " AND (project_id IN " + projectIds + " OR submit_user = ?)"
            + " ORDER BY recover_id DESC LIMIT ?, ?";
    try {
      return this.dbOperator.query(querySQL, new FetchExecutionRecoverFlows(), username, skip, num);
    } catch (final SQLException e) {
      throw new ExecutorManagerException("Error fetching history recover flows", e);
    }
  }


  public ExecutionRecover getHistoryRecoverFlowByPidAndFid(final String projectId, final String flowId)
      throws ExecutorManagerException {
    String query = "SELECT recover_id, recover_status, enc_type, recover_data FROM execution_recover_flows "
            + "WHERE project_id = ? AND flow_id = ? "
            + "ORDER BY start_time DESC limit 1";

    final List<Object> params = new ArrayList<>();

    params.add(projectId);

    params.add(flowId);

    try {
      List<ExecutionRecover> recoverList = this.dbOperator.query(query, new FetchExecutionRecoverFlows(), params.toArray());

      if(recoverList.size() > 0){
        return recoverList.get(0);
      }else {
        return null;
      }
    } catch (final SQLException e) {
      throw new ExecutorManagerException("Error fetching history recover flows", e);
    }
  }

  /**
   * 历史重跑 查找 方法
   * @param paramMap 查找参数合集
   * @return
   * @throws ExecutorManagerException
   */
  public List<ExecutionRecover> listHistoryRecover(final Map<String, String> paramMap)
      throws ExecutorManagerException {

    String query = FetchExecutionRecoverFlows.LIST_BASE_EXECUTABLE_RECOVER_FLOW_QUERY;

    final List<Object> params = new ArrayList<>();

    boolean first = false;

    if(!paramMap.isEmpty()){

      String recoverStatus = MapUtils.getString(paramMap, "recoverStatus", "");

      if(recoverStatus != null && !recoverStatus.isEmpty()){
        if (first) {
          query += " WHERE ";
          first = false;
        } else {
          query += " AND ";
        }
        query += "recover_status = ?";
        params.add(recoverStatus);
      }


      Integer limitNum = MapUtils.getInteger(paramMap, "limitNum", 1);

      if(recoverStatus != null && !recoverStatus.isEmpty()){
        query += " ORDER BY recover_id DESC LIMIT ? ";
        params.add(limitNum);
      }

    }

    try {
      return this.dbOperator.query(query, new FetchExecutionRecoverFlows(), params.toArray());
    } catch (final SQLException e) {
      throw new ExecutorManagerException("Error fetching history recover flows", e);
    }
  }

  //获取所有历史重跑总数
  public int getHistoryRecoverTotal()
      throws ExecutorManagerException {
    final IntHandler intHandler = new IntHandler();
    try {
      return this.dbOperator.query(IntHandler.NUM_EXECUTIONS, intHandler);
    } catch (final SQLException e) {
      throw new ExecutorManagerException("Error fetching num executions", e);
    }
  }
  //根据用户名获取用户历史重跑总数
  public int getUserHistoryRecoverTotal(final String userName)
      throws ExecutorManagerException {
    final IntHandler intHandler = new IntHandler();

    final List<Object> params = new ArrayList<>();

    params.add(userName);

    try {
      return this.dbOperator.query(IntHandler.USER_NUM_EXECUTIONS, intHandler, params.toArray());
    } catch (final SQLException e) {
      throw new ExecutorManagerException("Error fetching num executions", e);
    }
  }

  //根据projectID获取历史重跑的总数
  public int getMaintainedHistoryRecoverTotal(String username, List<Integer> maintainedProjectIds) throws ExecutorManagerException {
    String projectIds = maintainedProjectIds.stream()
            .map(Objects::toString)
            .collect(joining(",", "(", ")"));
    String querySQL  = "SELECT COUNT(1) FROM execution_recover_flows WHERE recover_status = 30 " +
            "AND (project_id in " + projectIds + " OR submit_user = ?)";
    try {
      final IntHandler intHandler = new IntHandler();
      return this.dbOperator.query(querySQL, intHandler, username);
    } catch (SQLException e) {
      throw new ExecutorManagerException("Error fetching num executions", e);
    }
  }

  //获取peparing 或者running状态的历史重跑任务
  public List<ExecutionRecover> fetchHistoryRecover()
          throws ExecutorManagerException {
    final FetchExecutionRecoverFlows fetchExecutionRecoverFlows = new  FetchExecutionRecoverFlows();
    try {
      return this.dbOperator.query(FetchExecutionRecoverFlows.IS_NOT_FINISH_RECOVER, fetchExecutionRecoverFlows);
    } catch (final SQLException e) {
      throw new ExecutorManagerException("Error fetching num executions", e);
    }
  }


  private static class IntHandler implements ResultSetHandler<Integer> {

    private static final String NUM_EXECUTIONS =
        "SELECT COUNT(1) FROM execution_recover_flows "
            + "WHERE recover_status=30 ";

    private static final String USER_NUM_EXECUTIONS =
        "SELECT COUNT(1) FROM execution_recover_flows where submit_user = ? AND recover_status=30 ";

    @Override
    public Integer handle(final ResultSet rs) throws SQLException {
      if (!rs.next()) {
        return 0;
      }
      return rs.getInt(1);
    }
  }

  //根据条件获取执行历史的数据条数
  public int getExecHistoryTotal(final Map<String, String> filterMap)
      throws ExecutorManagerException {
    final IntHandler intHandler = new IntHandler();

    final List<Object> params = new ArrayList<>();

    StringBuilder historyTotalSql = new StringBuilder("SELECT COUNT(1) FROM execution_flows ef JOIN projects p ON ef.project_id = p.id");

    boolean first = true;
    //搜索所有的执行历史数据条数
    if (null != filterMap.get("userContains")) {
      first = wrapperSqlParam(first, filterMap, "userContains", null,"submit_user", "like", historyTotalSql, params);
    }
    //搜索flow名字类似的执行历史数据条数
    if (null != filterMap.get("flowContains")) {
      first = wrapperSqlParam(first, filterMap, "flowContains", null,"flow_id", "like", historyTotalSql, params);

      first = wrapperSqlParam(first, filterMap, "userName", null,"submit_user", "=", historyTotalSql, params);

    }
    //按照过滤条件搜索执行历史数据条数
    if (null != filterMap.get("filterContains")) {
      if (filterMap.get("projContain") != null && !filterMap.get("projContain").isEmpty()) {
        first = wrapperSqlParam(first, filterMap, "projContain", null,"name", "like", historyTotalSql, params);
      }

      // todo kunkun-tang: we don't need the below complicated logics. We should just use a simple way.
      if (filterMap.get("flowContain") != null && !filterMap.get("flowContain").isEmpty()) {
        first = wrapperSqlParam(first, filterMap, "flowContain", null,"flow_id", "like", historyTotalSql, params);
      }

      if (Integer.valueOf(filterMap.get("status")) != 0) {
        first = wrapperSqlParam(first, filterMap, "status", "int","status", "=", historyTotalSql, params);
      }

      if (!"-1".equals(filterMap.get("beginTime"))) {
        first = wrapperSqlParam(first, filterMap, "beginTime", "long","start_time", ">", historyTotalSql, params);
      }

      if (!"-1".equals(filterMap.get("endTime"))) {
        first = wrapperSqlParam(first, filterMap, "endTime", "long","end_time", "<", historyTotalSql, params);
      }

      if(null != filterMap.get("userContain") && !filterMap.get("userContain").isEmpty()){
        first = wrapperSqlParam(first, filterMap, "userContain", null,"submit_user", "like", historyTotalSql, params);
      }


      if (!"-1".equals(filterMap.get("flowType"))) {
        first = wrapperSqlParam(first, filterMap, "flowType", "int","flow_type", "like", historyTotalSql, params);
      }
    }

    try {
      return this.dbOperator.query(historyTotalSql.toString(), intHandler, params.toArray());
    } catch (final SQLException e) {
      throw new ExecutorManagerException("Error fetching num executions", e);
    }
  }

  public int getExecHistoryTotal(String username, final Map<String, String> filterMap, List<Integer> projectIds)
          throws ExecutorManagerException {
    final IntHandler intHandler = new IntHandler();

    final List<Object> params = new ArrayList<>();
    String projectIdsStr = projectIds.stream()
            .map(Object::toString)
            .collect(joining(",", "(", ")"));
    StringBuilder historyTotalSql = new StringBuilder("SELECT COUNT(1) FROM execution_flows ef, projects p "
            + "WHERE ef.project_id = p.id "
            + "AND ef.project_id IN " + projectIdsStr);
    boolean first = false;
    //搜索所有的执行历史数据条数
    if (null != filterMap.get("userContains")) {
      first = wrapperSqlParam(first, filterMap, "userContains", null,"submit_user", "like", historyTotalSql, params);
    }
    //搜索flow名字类似的执行历史数据条数
    if (null != filterMap.get("flowContains")) {
      first = wrapperSqlParam(first, filterMap, "flowContains", null,"flow_id", "like", historyTotalSql, params);

      first = wrapperSqlParam(first, filterMap, "userName", null,"submit_user", "=", historyTotalSql, params);

    }
    //按照过滤条件搜索执行历史数据条数
    if (null != filterMap.get("filterContains")) {
      if (filterMap.get("projContain") != null && !filterMap.get("projContain").isEmpty()) {
        first = wrapperSqlParam(first, filterMap, "projContain", null,"p.name", "like", historyTotalSql, params);
      }

      // todo kunkun-tang: we don't need the below complicated logics. We should just use a simple way.
      if (filterMap.get("flowContain") != null && !filterMap.get("flowContain").isEmpty()) {
        first = wrapperSqlParam(first, filterMap, "flowContain", null,"flow_id", "like", historyTotalSql, params);
      }

      if (Integer.valueOf(filterMap.get("status")) != 0) {
        first = wrapperSqlParam(first, filterMap, "status", "int","status", "=", historyTotalSql, params);
      }

      if (!"-1".equals(filterMap.get("beginTime"))) {
        first = wrapperSqlParam(first, filterMap, "beginTime", "long","start_time", ">", historyTotalSql, params);
      }

      if (!"-1".equals(filterMap.get("endTime"))) {
        first = wrapperSqlParam(first, filterMap, "endTime", "long","end_time", "<", historyTotalSql, params);
      }

      if(null != filterMap.get("userContain") && !filterMap.get("userContain").isEmpty()){
        first = wrapperSqlParam(first, filterMap, "userContain", null,"submit_user", "like", historyTotalSql, params);
      }


      if (!"-1".equals(filterMap.get("flowType"))) {
        first = wrapperSqlParam(first, filterMap, "flowType", "int","flow_type", "like", historyTotalSql, params);
      }
    }

    try {
      return this.dbOperator.query(historyTotalSql.toString(), intHandler, params.toArray());
    } catch (final SQLException e) {
      throw new ExecutorManagerException("Error fetching num executions", e);
    }
  }


  public int getMaintainedExecHistoryTotal(String username, List<Integer> projectIds) throws ExecutorManagerException {
    String projectIdsStr = projectIds.stream()
            .map(Object::toString)
            .collect(joining(",", "(", ")"));
    String querySQL = "SELECT COUNT(1) " +
            "FROM execution_flows ef, projects p " +
            "WHERE ef.project_id = p.id " +
            "AND ef.project_id IN " + projectIdsStr;
    try {
      ResultSetHandler<Integer> handler = rs -> rs.next()? rs.getInt(1): 0;
      return this.dbOperator.query(querySQL, handler);
    } catch (final SQLException e) {
      throw new ExecutorManagerException("Error fetching num executions", e);
    }
  }

  //根据条件获取执行历史的数据条数
  public int getExecHistoryQuickSerachTotal(final Map<String, String> filterMap)
      throws ExecutorManagerException {
    final IntHandler intHandler = new IntHandler();

    final List<Object> params = new ArrayList<>();

    String historyTotalSql = "SELECT COUNT(1) FROM execution_flows ef JOIN projects p ON ef.project_id = p.id";

    boolean first = true;
    //搜索flow名字类似的执行历史数据条数
    if (null != filterMap.get("flowContains")) {
      if (first) {
        historyTotalSql += " WHERE ";
        first = false;
      } else {
        historyTotalSql += " AND ";
      }
      historyTotalSql += " (exec_id LIKE ? OR flow_id LIKE ? OR name LIKE ?) ";
      params.add('%' + filterMap.get("flowContains") + '%');
      params.add('%' + filterMap.get("flowContains") + '%');
      params.add('%' + filterMap.get("flowContains") + '%');
      first = false;
      if(null != filterMap.get("flowUser")){
        historyTotalSql += " AND submit_user = ?";
        params.add(filterMap.get("flowUser"));
        first = false;
      }
    }

    try {
      return this.dbOperator.query(historyTotalSql, intHandler, params.toArray());
    } catch (final SQLException e) {
      throw new ExecutorManagerException("Error fetching num executions", e);
    }
  }

  public int getMaintainedFlowsQuickSearchTotal(String username, final Map<String, String> filterMap, List<Integer> projectIds)
          throws ExecutorManagerException {
    final IntHandler intHandler = new IntHandler();

    final List<Object> params = new ArrayList<>();
    String projectIdsStr = projectIds.stream()
            .map(Object::toString)
            .collect(joining(",", "(", ")"));
    String historyTotalSql = "SELECT COUNT(1) FROM execution_flows ef, projects p "
            + "WHERE ef.project_id = p.id "
            + "AND ef.project_id IN " + projectIdsStr + " ";
    boolean first = false;
    //搜索flow名字类似的执行历史数据条数
    if (null != filterMap.get("flowContains")) {
      if (first) {
        historyTotalSql += " WHERE ";
        first = false;
      } else {
        historyTotalSql += " AND ";
      }
      historyTotalSql += " (ef.exec_id LIKE ? OR ef.flow_id LIKE ? OR p.name LIKE ?) ";
      params.add('%' + filterMap.get("flowContains") + '%');
      params.add('%' + filterMap.get("flowContains") + '%');
      params.add('%' + filterMap.get("flowContains") + '%');
      first = false;
      if(null != filterMap.get("flowUser")){
        historyTotalSql += " AND submit_user = ?";
        params.add(filterMap.get("flowUser"));
        first = false;
      }
    }

    try {
      return this.dbOperator.query(historyTotalSql, intHandler, params.toArray());
    } catch (final SQLException e) {
      throw new ExecutorManagerException("Error fetching num executions", e);
    }
  }

  //根据条件获取执行历史的数据条数
  public int getUserExecHistoryTotal(final Map<String, String> filterMap)
      throws ExecutorManagerException {
    final IntHandler intHandler = new IntHandler();

    final List<Object> params = new ArrayList<>();

    StringBuilder historyTotalSql = new StringBuilder( "SELECT COUNT(1) FROM execution_flows ef, projects p, project_permissions pp "
            + "WHERE ef.project_id = p.id AND ef.project_id = pp.project_id "
            + "AND pp.name=? ");

    params.add(filterMap.get("userName"));

    boolean first = false;
    //搜索所有的执行历史数据条数
    if (null != filterMap.get("userContains")) {
      wrapperSqlParam(first,filterMap,"userContains", null,"submit_user", "=",historyTotalSql,params);
    }

    //搜索flow名字类似的执行历史数据条数
    if (null != filterMap.get("flowContains")) {
      wrapperSqlParam(first, filterMap, "flowContains", null,"flow_id", "like", historyTotalSql, params);

      if(null != filterMap.get("userName")){
        wrapperSqlParam(first, filterMap, "userName", null,"submit_user", "=", historyTotalSql, params);
      }
    }

    //搜索execId类似的执行历史数据条数
    if (null != filterMap.get("execIdContain")) {
      wrapperSqlParam(first, filterMap, "execIdContain", null,"exec_id", "like", historyTotalSql, params);
      if(null != filterMap.get("userName")){
        wrapperSqlParam(first, filterMap, "userName", null,"submit_user", "=", historyTotalSql, params);
      }
    }

    //按照过滤条件搜索执行历史数据条数
    if (null != filterMap.get("filterContains")) {
      if (filterMap.get("projContain") != null && !filterMap.get("projContain").isEmpty()) {
        wrapperSqlParam(first, filterMap, "projContain", null,"p.name", "like", historyTotalSql, params);
      }

      // todo kunkun-tang: we don't need the below complicated logics. We should just use a simple way.
      if (filterMap.get("flowContain") != null && !filterMap.get("flowContain").isEmpty()) {
        wrapperSqlParam(first, filterMap, "flowContain", null,"flow_id", "like", historyTotalSql, params);
      }

      if (Integer.valueOf(filterMap.get("status")) != 0) {
        wrapperSqlParam(first, filterMap, "status", "int","status", "=", historyTotalSql, params);
      }

      if (!"-1".equals(filterMap.get("beginTime"))) {
        wrapperSqlParam(first, filterMap, "beginTime", "long", "start_time", ">", historyTotalSql, params);
      }

      if (!"-1".equals(filterMap.get("endTime"))) {
        wrapperSqlParam(first, filterMap, "endTime", "long","end_time", "<", historyTotalSql, params);
      }

      if(null != filterMap.get("userContain") && !filterMap.get("userContain").isEmpty()){
        wrapperSqlParam(first, filterMap, "userContain", null,"submit_user", "like", historyTotalSql, params);
      }


      if (!"-1".equals(filterMap.get("flowType"))) {
        wrapperSqlParam(first, filterMap, "flowType", "int","flow_type", "=", historyTotalSql, params);
      }
    }

    try {
      return this.dbOperator.query(historyTotalSql.toString(), intHandler, params.toArray());
    } catch (final SQLException e) {
      throw new ExecutorManagerException("Error fetching num executions", e);
    }
  }

  /**
   * 组装参数拼接
   * @param firstParam
   * @param filterMap
   * @param param
   * @param parseType 转换类型
   * @param dbColumnName
   * @param action
   * @param querySql
   * @param params
   */
  private boolean wrapperSqlParam(boolean firstParam, Map<String, String> filterMap, String param, String parseType, String dbColumnName,
                               String action, StringBuilder querySql, List<Object> params) {
      if (firstParam) {
        querySql.append(" WHERE ");
        firstParam = false;
      } else {
        querySql.append(" AND ");
      }
      querySql.append(" ").append(dbColumnName).append(" ").append(action).append(" ?");

      if (action.equalsIgnoreCase("like")) {
        params.add('%' + filterMap.get(param) + '%');
      } else {
        // 判断是否需要转换类型
        if (parseType == null) {
          params.add(filterMap.get(param));
          // 需要转换为int类型
        } else if ("int".equalsIgnoreCase(parseType)) {
          params.add(Integer.valueOf(filterMap.get(param)));
        } else if ("long".equalsIgnoreCase(parseType)) {
          params.add(Long.valueOf(filterMap.get(param)));
        }
      }
    return firstParam;
  }

  //根据条件获取执行历史的数据条数
  public int getUserExecHistoryQuickSerachTotal(final Map<String, String> filterMap)
      throws ExecutorManagerException {
    final IntHandler intHandler = new IntHandler();

    final List<Object> params = new ArrayList<>();

    String historyTotalSql = "SELECT COUNT(1) FROM execution_flows ef, projects p, project_permissions pp "
        + "WHERE ef.project_id = p.id AND ef.project_id = pp.project_id "
        + "AND pp.name=? ";

    params.add(filterMap.get("userName"));

    boolean first = false;
    //搜索flow名字类似的执行历史数据条数
    if (null != filterMap.get("flowContains")) {
      if (first) {
        historyTotalSql += " WHERE ";
        first = false;
      } else {
        historyTotalSql += " AND ";
      }
      historyTotalSql += " (ef.exec_id LIKE ? OR ef.flow_id LIKE ? OR p.name LIKE ?) ";
      params.add('%' + filterMap.get("flowContains") + '%');
      params.add('%' + filterMap.get("flowContains") + '%');
      params.add('%' + filterMap.get("flowContains") + '%');
      first = false;
      if(null != filterMap.get("flowUser")){
        historyTotalSql += " AND submit_user = ?";
        params.add(filterMap.get("flowUser"));
        first = false;
      }
    }

    try {
      return this.dbOperator.query(historyTotalSql, intHandler, params.toArray());
    } catch (final SQLException e) {
      throw new ExecutorManagerException("Error fetching num executions", e);
    }
  }

}
