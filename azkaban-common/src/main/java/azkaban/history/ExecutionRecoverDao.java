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

package azkaban.history;

import static java.util.stream.Collectors.joining;

import azkaban.db.DatabaseOperator;
import azkaban.db.EncodingType;
import azkaban.db.SQLTransaction;
import azkaban.executor.ExecutorManagerException;
import azkaban.executor.HistoryQueryParam;
import azkaban.executor.Status;
import azkaban.utils.GZIPUtils;
import azkaban.utils.JSONUtils;
import java.io.IOException;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import javax.inject.Inject;
import javax.inject.Singleton;
import org.apache.commons.collections.MapUtils;
import org.apache.commons.dbutils.ResultSetHandler;
import org.apache.commons.lang.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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
      logger.info("History Recover given recoverid: {}, given id: {}" , recover.getRecoverId(), id);
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
                    + " SET recover_status=?, now_exec_id=?, update_time=?, start_time=?, end_time=?, enc_type=?, recover_data=? "
                    + " WHERE recover_id=? ";

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

  public List<ExecutionRecover> getUserHistoryRerunConfiguration(int projectId, String flowName, String userId, int start, int size) throws ExecutorManagerException {
    try {
      return this.dbOperator.query(FetchExecutionRecoverFlowsWithSubmitInfo.USER_HISTORY_RERUN_CONFIGURATION, new FetchExecutionRecoverFlowsWithSubmitInfo(), userId, projectId, flowName, start, size);
    } catch (SQLException e) {
      throw new ExecutorManagerException("Error fetching user history rerun configuration", e);
    }
  }

  public List<ExecutionRecover> getMaintainedHistoryRerunConfiguration(int projectId, String flowName, String userId, int start, int size) throws ExecutorManagerException {
    try {
      return this.dbOperator.query(FetchExecutionRecoverFlowsWithSubmitInfo.MAINTAINED_HISTORY_RERUN_CONFIGURATION, new FetchExecutionRecoverFlowsWithSubmitInfo(), userId, projectId, flowName, start, size);
    } catch (SQLException e) {
      throw new ExecutorManagerException("Error fetching maintained history rerun configuration", e);
    }
  }

  public List<ExecutionRecover> getAllHistoryRerunConfiguration(int projectId, String flowName, int start, int size) throws ExecutorManagerException {
    try {
      return this.dbOperator.query(FetchExecutionRecoverFlowsWithSubmitInfo.ALL_HISTORY_RERUN_CONFIGURATION, new FetchExecutionRecoverFlowsWithSubmitInfo(), projectId, flowName, start, size);
    } catch (SQLException e) {
      throw new ExecutorManagerException("Error fetching all history rerun configuration", e);
    }
  }

  public int getAllExecutionRecoverTotal(int projectId, String flowName) throws ExecutorManagerException {
    try {
      return this.dbOperator.query(IntHandler.ALL_EXECUTION_RECOVER_TOTAL, new IntHandler(), projectId, flowName);
    } catch (SQLException e) {
      throw new ExecutorManagerException("Error fetching all execution recover total", e);
    }
  }

  public int getMaintainedExecutionRecoverTotal(int projectId, String flowName, String userId) throws ExecutorManagerException {
    try {
      return this.dbOperator.query(IntHandler.MAINTAINED_EXECUTION_RECOVER_TOTAL, new IntHandler(),userId, projectId, flowName);
    } catch (SQLException e) {
      throw new ExecutorManagerException("Error fetching maintained execution recover total", e);
    }
  }

  public int getUserExecHistoryTotal(int projectId, String flowName, String userId) throws ExecutorManagerException {
    try {
      return this.dbOperator.query(IntHandler.USER_EXECUTION_RECOVER_TOTAL, new IntHandler(),userId, projectId, flowName);
    } catch (SQLException e) {
      throw new ExecutorManagerException("Error fetching user execution recover total", e);
    }
  }

  /**
   * 历史补采查找结果处理内部类
   */
  public static class FetchExecutionRecoverFlowsWithSubmitInfo implements ResultSetHandler<List<ExecutionRecover>> {

    private static String IS_NOT_FINISH_RECOVER_WITH_SUBMIT_TIME =
            "SELECT recover_id, recover_status, enc_type, recover_data, submit_time, recover_start_time, recover_end_time "
                    + " FROM execution_recover_flows "
                    + " WHERE recover_status in (20,30,40)";

    private static String RUNNING_PAGE_KILL_RECOVER_DATA =
            "SELECT recover_id, recover_status, enc_type, recover_data, submit_time, recover_start_time, recover_end_time "
                    + " FROM execution_recover_flows where recover_id = ?";

    private static String USER_HISTORY_RERUN_CONFIGURATION = "SELECT recover_id, recover_status, enc_type, recover_data, submit_time, recover_start_time, recover_end_time"
            +" FROM execution_recover_flows WHERE submit_user = ? and project_id = ? and flow_id = ? order by recover_id desc limit ? , ? ";

    private static String MAINTAINED_HISTORY_RERUN_CONFIGURATION = "SELECT recover_id, recover_status, enc_type, recover_data, submit_time, recover_start_time, recover_end_time"
            +" FROM execution_recover_flows WHERE submit_user in ( select username from wtss_user where department_id = ( select department_id from wtss_user where username = ? ) ) and project_id = ? and flow_id = ? order by recover_id desc limit ? , ? ";

    private static String ALL_HISTORY_RERUN_CONFIGURATION = "SELECT recover_id, recover_status, enc_type, recover_data, submit_time, recover_start_time, recover_end_time"
            +" FROM execution_recover_flows WHERE project_id = ? and flow_id = ? order by recover_id desc limit ? , ? ";

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
        final long submitTime = rs.getLong(5);
        final long recoverStartTime = rs.getLong(6);
        final long recoverEndTime = rs.getLong(7);

        if (data != null) {
          final EncodingType encType = EncodingType.fromInteger(encodingType);
          final Object flowObj;

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

            final ExecutionRecover exFlow = ExecutionRecover.createExecutionRecoverFromObject(flowObj);
            exFlow.setRecoverStatus(Status.fromInteger(status));
            exFlow.setRecoverStartTime(recoverStartTime);
            exFlow.setRecoverEndTime(recoverEndTime);
            exFlow.setSubmitTime(submitTime);
            execFlows.add(exFlow);
          } catch (final IOException e) {
            throw new SQLException("Error retrieving History Recover data " + id, e);
          }
        }
      } while (rs.next());

      return execFlows;
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

            final ExecutionRecover exFlow = ExecutionRecover.createExecutionRecoverFromObject(flowObj);
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

  public ExecutionRecover getHistoryRecoverFlows(final Integer recoverId) throws ExecutorManagerException {

    final List<Object> params = new ArrayList<>();

    params.add(recoverId);

    try {
      List<ExecutionRecover> recoverList = this.dbOperator.query(FetchExecutionRecoverFlowsWithSubmitInfo.RUNNING_PAGE_KILL_RECOVER_DATA,
              new FetchExecutionRecoverFlowsWithSubmitInfo(), params.toArray());
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

    StringBuilder querySql = new StringBuilder("SELECT DISTINCT e.recover_id, recover_status, e.enc_type, e.recover_data FROM execution_recover_flows e " +
            " JOIN project_permissions p ON e.project_id = p.`project_id` " +
            " WHERE recover_status in (20,30,40) ");

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
        wrapperSqlParam(first, paramMap, "userName", null, "p.name", "=", querySql, params);
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
    final String querySQL = "(SELECT recover_id, recover_status, erf.enc_type, recover_data FROM execution_recover_flows erf " +
            "  JOIN projects p ON erf.project_id = p.id WHERE recover_status in (20,30,40)" +
            "   AND (project_id IN " + projectIds + " OR submit_user = ?))" +
            "UNION " +
            "(SELECT DISTINCT e.recover_id, recover_status, e.enc_type, e.recover_data FROM execution_recover_flows e " +
            "  JOIN project_permissions p ON e.project_id = p.`project_id` " +
            "  WHERE recover_status in (20,30,40) AND p.`name` = ? ) " +
            "  ORDER BY recover_id DESC LIMIT ?, ? ;";
    try {
      return this.dbOperator.query(querySQL, new FetchExecutionRecoverFlows(), username, username, skip, num);
    } catch (final SQLException e) {
      throw new ExecutorManagerException("Error fetching history recover flows", e);
    }
  }


  public ExecutionRecover getHistoryRecoverFlowByPidAndFid(final String projectId, final String flowId)
          throws ExecutorManagerException {
    String query = "SELECT recover_id, recover_status, enc_type, recover_data FROM execution_recover_flows "
            + "WHERE project_id = ? AND flow_id = ? AND recover_data IS NOT NULL "
            + "ORDER BY submit_time DESC limit 1";

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
    final String querySQL = "SELECT COUNT(1) FROM ((SELECT recover_id, recover_status, erf.enc_type, recover_data FROM execution_recover_flows erf " +
            " JOIN projects p ON erf.project_id = p.id WHERE recover_status in (20,30,40) " +
            " AND (project_id IN " + projectIds + " OR submit_user = ?)) " +
            "UNION " +
            "(SELECT DISTINCT e.recover_id, recover_status, e.enc_type, e.recover_data FROM execution_recover_flows e " +
            " JOIN project_permissions p ON e.project_id = p.`project_id` " +
            " WHERE recover_status in (20,30,40) AND p.`name` = ?)) tmp";
    try {
      final IntHandler intHandler = new IntHandler();
      return this.dbOperator.query(querySQL, intHandler, username, username);
    } catch (SQLException e) {
      throw new ExecutorManagerException("Error fetching num executions", e);
    }
  }

  //获取peparing 或者running状态的历史重跑任务
  public List<ExecutionRecover> fetchHistoryRecover() throws ExecutorManagerException {
    final FetchExecutionRecoverFlowsWithSubmitInfo recoverFlowsWithSubmitInfo = new FetchExecutionRecoverFlowsWithSubmitInfo();
    try {
      return this.dbOperator.query(FetchExecutionRecoverFlowsWithSubmitInfo.IS_NOT_FINISH_RECOVER_WITH_SUBMIT_TIME, recoverFlowsWithSubmitInfo);
    } catch (final SQLException e) {
      throw new ExecutorManagerException("Error fetching num executions", e);
    }
  }


  private static class IntHandler implements ResultSetHandler<Integer> {

    private static final String NUM_EXECUTIONS =
            "SELECT COUNT(1) FROM execution_recover_flows "
                    + "WHERE recover_status in (20,30,40) ";

    private static final String USER_NUM_EXECUTIONS =
            "SELECT COUNT(DISTINCT e.recover_id) FROM execution_recover_flows e " +
                    " LEFT JOIN project_permissions p ON e.project_id = p.`project_id` " +
                    " WHERE recover_status in (20,30,40) AND p.name = ?";

    private static final String ALL_EXECUTION_RECOVER_TOTAL = "SELECT COUNT(1) FROM execution_recover_flows "
            +" WHERE project_id = ? and flow_id = ? ";

    private static final String USER_EXECUTION_RECOVER_TOTAL = "SELECT COUNT(1) FROM execution_recover_flows "
            +" WHERE submit_user = ? and project_id = ? and flow_id = ? order by recover_id desc ";

    private static final String MAINTAINED_EXECUTION_RECOVER_TOTAL = "SELECT COUNT(1) FROM execution_recover_flows "
            +" WHERE submit_user in ( select username from wtss_user where department_id = ( select department_id from wtss_user where username = ? ) ) and project_id = ? and flow_id = ? order by recover_id desc";

    @Override
    public Integer handle(final ResultSet rs) throws SQLException {
      if (!rs.next()) {
        return 0;
      }
      return rs.getInt(1);
    }
  }

  //根据条件获取执行历史的数据条数
  public int getExecHistoryTotal(final HistoryQueryParam param)
          throws ExecutorManagerException {
    final IntHandler intHandler = new IntHandler();

    final List<Object> params = new ArrayList<>();

    StringBuilder historyTotalSql = new StringBuilder(
            "SELECT COUNT(1) FROM execution_flows ef JOIN projects p ON ef.project_id = p.id ");

    boolean first = true;
    /*//搜索所有的执行历史数据条数
    if (null != filterMap.get("userContains")) {
      first = wrapperSqlParam(first, filterMap, "userContains", null, "submit_user", "like",
          historyTotalSql, params);
    }
    //搜索flow名字类似的执行历史数据条数
    if (null != filterMap.get("flowContains")) {
      first = wrapperSqlParam(first, filterMap, "flowContains", null, "flow_id", "like",
          historyTotalSql, params);

      first = wrapperSqlParam(first, filterMap, "userName", null,"submit_user", "=", historyTotalSql, params);

    }*/
    //按照过滤条件搜索执行历史数据条数
    wrapMultiConditionSql(first, param, historyTotalSql, params);

    try {
      return this.dbOperator.query(historyTotalSql.toString(), intHandler, params.toArray());
    } catch (final SQLException e) {
      throw new ExecutorManagerException("Error fetching num executions", e);
    }
  }

  public int getExecHistoryTotal(HistoryQueryParam param, List<Integer> projectIds)
          throws ExecutorManagerException {
    final IntHandler intHandler = new IntHandler();

    final List<Object> params = new ArrayList<>();
    String projectIdsStr = projectIds.stream()
            .map(Object::toString)
            .collect(joining(",", "(", ")"));
    StringBuilder historyTotalSql = new StringBuilder("SELECT COUNT(1) FROM execution_flows ef JOIN projects p ON ef.project_id = p.id " +
            "WHERE ef.project_id IN " + projectIdsStr + " ");
    boolean first = false;
    /*//搜索所有的执行历史数据条数
    if (null != filterMap.get("userContains")) {
      first = wrapperSqlParam(first, filterMap, "userContains", null,"submit_user", "like", historyTotalSql, params);
    }
    //搜索flow名字类似的执行历史数据条数
    if (null != filterMap.get("flowContains")) {
      first = wrapperSqlParam(first, filterMap, "flowContains", null,"flow_id", "like", historyTotalSql, params);

      first = wrapperSqlParam(first, filterMap, "userName", null,"submit_user", "=", historyTotalSql, params);

    }*/
    //按照过滤条件搜索执行历史数据条数
    wrapMultiConditionSql(first, param, historyTotalSql, params);

    try {
      return this.dbOperator.query(historyTotalSql.toString(), intHandler, params.toArray());
    } catch (final SQLException e) {
      throw new ExecutorManagerException("Error fetching num executions", e);
    }
  }

  private void wrapMultiConditionSql(boolean first, HistoryQueryParam param,
                                     StringBuilder historyTotalSql, List<Object> params) {
    if (param == null) {
      return;
    }

    String action = "advfilter".equals(param.getSearchType()) ? "like" : "=";

    if (StringUtils.isNotEmpty(param.getProjContain())) {
      first = wrapperSqlParam(first, param.getProjContain(), historyTotalSql, "p.name", action,
              params);
    }

    if (StringUtils.isNotBlank(param.getProjectName())) {
      first = wrapperSqlParam(first, param.getProjectName(), historyTotalSql, "p.name", "=", params);
    }

    if (StringUtils.isNotBlank(param.getFlowId())) {
      first = wrapperSqlParam(first, param.getFlowId(), historyTotalSql, "ef.flow_id", "=", params);
    }

    if (StringUtils.isNotBlank(param.getComment())) {
      first = wrapperSqlParam(first, param.getComment(), historyTotalSql, "flow_comment", action,
              params);
    }

    // todo kunkun-tang: we don't need the below complicated logics. We should just use a simple way.
    if (StringUtils.isNotEmpty(param.getFlowContain())) {
      first = wrapperSqlParam(first, param.getFlowContain(), historyTotalSql, "flow_id", action,
              params);
    }

    if (StringUtils.isNotEmpty(param.getExecIdContain())) {
      first = wrapperSqlParam(first, param.getExecIdContain(), historyTotalSql, "exec_id", action,
              params);
    }

    String[] statusArray = param.getStatus().split(",");
    if (!("0".equals(statusArray[0]))) {
      first = wrapperMultipleStatusSql(first, statusArray, historyTotalSql, "status", "in");
    }

    if (param.getStartBeginTime() > 0) {
      first = wrapperSqlParam(first, "" + param.getStartBeginTime(), historyTotalSql, "start_time",
              ">", params);
    }
    if (param.getStartEndTime() > 0) {
      first = wrapperSqlParam(first, "" + param.getStartEndTime(), historyTotalSql, "start_time",
              "<", params);
    }
    if (param.getFinishBeginTime() > 0) {
      first = wrapperSqlParam(first, "" + param.getFinishBeginTime(), historyTotalSql, "end_time",
              ">", params);
    }
    if (param.getFinishEndTime() > 0) {
      first = wrapperSqlParam(first, "" + -1, historyTotalSql, "end_time",
              "!=", params);
      first = wrapperSqlParam(first, "" + param.getFinishEndTime(), historyTotalSql, "end_time",
              "<", params);
    }

    if (param.getBeginTime() > 0) {
      first = wrapperSqlParam(first, "" + param.getBeginTime(), historyTotalSql, "start_time",
              ">", params);
    }
    if (param.getEndTime() > 0) {
      if (first) {
        historyTotalSql.append(" WHERE ");
        first = false;
      } else {
        historyTotalSql.append(" AND ");
      }
      historyTotalSql.append(" ((end_time=-1 and start_time<?) or (end_time!=-1 and end_time<?)) ");
      for (int i = 0; i < 2; i++) {
        params.add(param.getEndTime());
      }
    }

    if (StringUtils.isNotEmpty(param.getSubsystem())) {
      first = wrapperSqlParam(first, param.getSubsystem(), historyTotalSql, "subsystem", "=",
              params);
    }

    if (StringUtils.isNotEmpty(param.getBusPath())) {
      first = wrapperSqlParam(first, param.getBusPath(), historyTotalSql, "bus_path", "=", params);
    }

    if (StringUtils.isNotEmpty(param.getDepartmentId())) {
      first = wrapperSqlParam(first, param.getDepartmentId(), historyTotalSql, "submit_department",
              "=", params);
    }

    if (StringUtils.isNotEmpty(param.getRunDateReq())) {
      first = wrapperSqlParam(first, param.getRunDateReq(), historyTotalSql, "run_date", "=",
              params);
    }

    if (StringUtils.isNotEmpty(param.getUserContain())) {
      first = wrapperSqlParam(first, param.getUserContain(), historyTotalSql, "submit_user", action,
              params);
    }

    if (param.getFlowType() != -1) {
      first = wrapperSqlParam(first, "" + param.getFlowType(), historyTotalSql, "flow_type", "=", params);
    }

    if (StringUtils.isNotBlank(param.getFromHomePage())) {
      Calendar calendar = Calendar.getInstance();
      //获取当天凌晨毫秒数
      calendar.set(Calendar.HOUR_OF_DAY, 0);
      calendar.set(Calendar.MINUTE, 0);
      calendar.set(Calendar.SECOND, 0);
      calendar.set(Calendar.MILLISECOND, 1);
      first = wrapperSqlParam(first, calendar.getTimeInMillis() + "", historyTotalSql, "ef.submit_time", ">=",
              params);
      //获取当天24点毫秒数
      calendar.set(Calendar.HOUR_OF_DAY, 23);
      calendar.set(Calendar.MINUTE, 59);
      calendar.set(Calendar.SECOND, 59);
      wrapperSqlParam(first, calendar.getTimeInMillis() + "", historyTotalSql, "ef.submit_time", "<=",
              params);
    }
  }

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
      historyTotalSql += " (exec_id LIKE ? OR flow_id LIKE ? OR name LIKE ?  OR submit_user LIKE ? ) ";
      params.add('%' + filterMap.get("flowContains") + '%');
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
      historyTotalSql += " (ef.exec_id LIKE ? OR ef.flow_id LIKE ? OR p.name LIKE ?  OR ef.submit_user LIKE ? ) ";
      params.add('%' + filterMap.get("flowContains") + '%');
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
  public int getUserExecHistoryTotal(HistoryQueryParam param, String loginUser)
          throws ExecutorManagerException {
    final IntHandler intHandler = new IntHandler();

    final List<Object> params = new ArrayList<>();

    StringBuilder historyTotalSql = new StringBuilder( "SELECT COUNT(1) FROM execution_flows ef JOIN projects p on ef.project_id = p.id " +
            "JOIN project_permissions pp on ef.project_id = pp.project_id " +
            "WHERE pp.name=? ");

    params.add(loginUser);

    boolean first = false;
    //搜索所有的执行历史数据条数
    /*if (null != filterMap.get("userContains")) {
      wrapperSqlParam(false,filterMap,"userContains", null,"submit_user", "=",historyTotalSql,params);
    }

    //搜索flow名字类似的执行历史数据条数
    if (null != filterMap.get("flowContains")) {
      wrapperSqlParam(false, filterMap, "flowContains", null,"ef.flow_id", "like", historyTotalSql, params);

      if(null != filterMap.get("userName")){
        wrapperSqlParam(false, filterMap, "userName", null,"submit_user", "=", historyTotalSql, params);
      }
    }

    //搜索execId类似的执行历史数据条数
    if (null != filterMap.get("execIdContain")) {
      wrapperSqlParam(false, filterMap, "execIdContain", null,"exec_id", "like", historyTotalSql, params);
      if(null != filterMap.get("userName")){
        wrapperSqlParam(false, filterMap, "userName", null,"submit_user", "=", historyTotalSql, params);
      }
    }*/

    //按照过滤条件搜索执行历史数据条数
    wrapMultiConditionSql(first, param, historyTotalSql, params);

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

    if ("like".equalsIgnoreCase(action)) {
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
      historyTotalSql += " (ef.exec_id LIKE ? OR ef.flow_id LIKE ? OR p.name LIKE ? OR ef.submit_user LIKE ? ) ";
      params.add('%' + filterMap.get("flowContains") + '%');
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
