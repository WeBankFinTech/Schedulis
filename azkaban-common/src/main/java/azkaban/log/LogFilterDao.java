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

package azkaban.log;

import azkaban.db.DatabaseOperator;
import azkaban.db.EncodingType;
import azkaban.db.SQLTransaction;
import azkaban.executor.ExecutorManagerException;
import org.apache.commons.dbutils.ResultSetHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;

@Singleton
public class LogFilterDao {

  private static final Logger logger = LoggerFactory.getLogger(LogFilterDao.class);
  private final DatabaseOperator dbOperator;

  @Inject
  public LogFilterDao(final DatabaseOperator dbOperator) {
    this.dbOperator = dbOperator;
  }

  /**
   * 创建历史补采数据库记录
   * @param filterEntity
   * @throws ExecutorManagerException
   */
  public synchronized Integer uploadLogFilter(final LogFilterEntity filterEntity)
      throws ExecutorManagerException {
    final String INSERT_LOG_FILTER_FLOW = "INSERT INTO log_filter "
        + "(log_code, code_type, compare_text, operate_type, log_notice, submit_time, update_time) "
        + "values (?,?,?,?,?,?,?)";
    final Date submitTime = new Date();

    final SQLTransaction<Long> insertAndGetLastID = transOperator -> {
      transOperator.update(INSERT_LOG_FILTER_FLOW,

          filterEntity.getLogCode(),
          filterEntity.getCodeType(),
          filterEntity.getCompareText(),
          filterEntity.getOperateType(),
          filterEntity.getLogNotice(),

          submitTime,
          submitTime);
      transOperator.getConnection().commit();
      return transOperator.getLastInsertId();
    };
    try {
      final long id = this.dbOperator.transaction(insertAndGetLastID);
      logger.info("History Recover given " + filterEntity.getCodeId() + " given id " + id);
      filterEntity.setCodeId((int)id);
      updateLogFilterFlow(filterEntity);
    } catch (final SQLException e) {
      throw new ExecutorManagerException("Error creating History Recover.", e);
    }
    return filterEntity.getCodeId();
  }

  /**
   * 历史补采数据更新方法
   * @param logFilterEntity
   * @throws ExecutorManagerException
   */
  void updateLogFilterFlow(final LogFilterEntity logFilterEntity) throws ExecutorManagerException {
    updateLogFilterFlow(logFilterEntity, EncodingType.GZIP);
  }

  /**
   * 历史补采数据更新实现方法
   * @param logFilterEntity
   * @param encType
   * @throws ExecutorManagerException
   */
  private void updateLogFilterFlow(final LogFilterEntity logFilterEntity, final EncodingType encType)
      throws ExecutorManagerException {
    final String UPDATE_LOG_FILTER_FLOW_DATA =
        "UPDATE log_filter "
            + "SET log_code=?, code_type=?, compare_text=?, operate_type=?, log_notice=?, update_time=? "
            + "WHERE code_id=? ";
    final Date updateTime = new Date();
    try {
      this.dbOperator.update(UPDATE_LOG_FILTER_FLOW_DATA,
          logFilterEntity.getLogCode(),
          logFilterEntity.getCodeType(),
          logFilterEntity.getCompareText(),
          logFilterEntity.getOperateType(),
          logFilterEntity.getLogNotice(),
          updateTime,
          logFilterEntity.getCodeId());
    } catch (final SQLException e) {
      throw new ExecutorManagerException("Error updating History Recover.", e);
    }
  }

  /**
   * 历史补采查找结果处理内部类
   */
  public static class FetchLogFilterEntity implements ResultSetHandler<List<LogFilterEntity>> {

    static String LIST_BASE_LOG_FILTER_QUERY =
        "SELECT code_id, log_code, code_type, compare_text, operate_type, log_notice, submit_time, update_time"
            + " FROM log_filter ";
    static String LIST_LOG_FILTER_BY_ID =
        "SELECT code_id, log_code, code_type, compare_text, operate_type, log_notice, submit_time, update_time"
            + " FROM log_filter "
            + "WHERE code_id=?";
    static String LIST_ALL_LOG_FILTER_HISTORY =
        "SELECT code_id, log_code, code_type, compare_text, operate_type, log_notice, submit_time, update_time"
            + " FROM log_filter "
            + "ORDER BY code_id DESC LIMIT ?, ?";
    static String LIST_EXECUTABLE_FLOW_HISTORY =
        "SELECT code_id, log_code, code_type, compare_text, operate_type, log_notice, submit_time, update_time"
            + " FROM log_filter "
            + "WHERE project_id=? AND flow_id=? "
            + "ORDER BY exec_id DESC LIMIT ?, ?";
    static String LIST_EXECUTABLE_FLOW_BY_STATUS =
        "SELECT code_id, log_code, code_type, compare_text, operate_type, log_notice, submit_time, update_time"
            + " FROM log_filter "
            + "WHERE project_id=? AND flow_id=? AND status=? "
            + "ORDER BY exec_id DESC LIMIT ?, ?";
    static String LIST_USER_LOG_FILTER_FLOW_HISTORY =
        "SELECT code_id, log_code, code_type, compare_text, operate_type, log_notice, submit_time, update_time"
            + " FROM log_filter "
            + "WHERE submit_user=? "
            + "ORDER BY recover_id DESC LIMIT ?, ?";

    @Override
    public List<LogFilterEntity> handle(final ResultSet rs) throws SQLException {
      if (!rs.next()) {
        return Collections.emptyList();
      }

      final List<LogFilterEntity> logFilterList = new ArrayList<>();
      do {
        try {
          final int codeId = rs.getInt(1);
          final String logCode = rs.getString(2);
          final int codeType = rs.getInt(3);
          final String compareText = rs.getString(4);
          final int operateType = rs.getInt(5);
          final String logNotice = rs.getString(6);
          final Date submitTime = rs.getDate(7);
          final Date updateTime = rs.getDate(8);

          final LogFilterEntity filterObj =
              new LogFilterEntity(codeId, logCode, LogCodeType.fromInteger(codeType), compareText,
                  OperateType.fromInteger(operateType),
                  logNotice, submitTime, updateTime);

          logFilterList.add(filterObj);
        } catch (final Exception e) {
          throw new SQLException("Error retrieving History Recover data ", e);
        }
      } while (rs.next());

      return logFilterList;
    }
  }


  public List<LogFilterEntity> listAllLogFilter() throws ExecutorManagerException {

    String query = FetchLogFilterEntity.LIST_BASE_LOG_FILTER_QUERY;

    final List<Object> params = new ArrayList<>();
    
    try {
      return this.dbOperator.query(query, new FetchLogFilterEntity(), params.toArray());
    } catch (final SQLException e) {
      throw new ExecutorManagerException("Error fetching history recover flows", e);
    }
  }

//
//  LogFilterEntity getHistoryRecoverFlows(final Integer recoverId)
//      throws ExecutorManagerException {
//
//    String query =
//        "SELECT recover_id, enc_type, recover_data FROM log_filter where recover_id = ?";
//
//    final List<Object> params = new ArrayList<>();
//
//    params.add(recoverId);
//
//    try {
//      List<LogFilterEntity> recoverList = this.dbOperator.query(query, new FetchLogFilterEntity(), params.toArray());
//      if(recoverList.size() > 0){
//        return recoverList.get(0);
//      }else {
//        return null;
//      }
//    } catch (final SQLException e) {
//      throw new ExecutorManagerException("Error fetching history recover flows", e);
//    }
//  }
//
//  List<LogFilterEntity> listHistoryRecoverFlowByFlowId(final String flowId, final String projectId)
//      throws ExecutorManagerException {
//
//    String query =
//        "select exec_id, enc_type, flow_data "
//            + "from execution_flows "
//            + "where flow_id = ? and project_id = ? and repeat_id != '' "
//            + "order by start_time DESC limit 1 ";
//
//    final List<Object> params = new ArrayList<>();
//
//    params.add(flowId);
//
//    params.add(projectId);
//
//    try {
//      return this.dbOperator.query(query, new FetchLogFilterEntity(), params.toArray());
//    } catch (final SQLException e) {
//      throw new ExecutorManagerException("Error fetching history recover flow by flowId", e);
//    }
//  }
//
//  /**
//   * 历史重跑 历史数据分页 查找 方法
//   * @param paramMap 查找参数合集
//   * @param skip 开始条数
//   * @param num  结束条数
//   * @return
//   * @throws ExecutorManagerException
//   */
//  List<LogFilterEntity> listHistoryRecoverFlows(final Map paramMap, final int skip, final int num)
//      throws ExecutorManagerException {
//
//    String query = FetchLogFilterEntity.LIST_BASE_LOG_FILTER_FLOW_QUERY;
//
//    query = "SELECT recover_id, erf.enc_type, recover_data FROM log_filter erf "
//        + "join projects p on erf.project_id = p.id "
//        + "WHERE recover_status=30 ";
//
//    final List<Object> params = new ArrayList<>();
//
//    boolean first = false;
//
//    if(!paramMap.isEmpty()){
//
//      //String projContain = String.valueOf(paramMap.get("projContain"));
//      String projContain = MapUtils.getString(paramMap, "projContain");
//
//      if (projContain != null && !projContain.isEmpty()) {
//        if (first) {
//          query += " WHERE ";
//          first = false;
//        } else {
//          query += " AND ";
//        }
//        query += " name LIKE ?";
//        params.add('%' + projContain + '%');
//        first = false;
//      }
//
//      //String flowContains = String.valueOf(paramMap.get("flowContains"));
//      String flowContains = MapUtils.getString(paramMap, "flowContains");
//
//      // todo kunkun-tang: we don't need the below complicated logics. We should just use a simple way.
//      if (flowContains != null && !flowContains.isEmpty()) {
//        if (first) {
//          query += " WHERE ";
//          first = false;
//        } else {
//          query += " AND ";
//        }
//
//        query += " flow_id LIKE ?";
//        params.add('%' + flowContains + '%');
//      }
//
//      Integer recoverStatus = MapUtils.getInteger(paramMap, "recoverStatus");
//
//      if (recoverStatus != null && recoverStatus != 0) {
//        if (first) {
//          query += " WHERE ";
//          first = false;
//        } else {
//          query += " AND ";
//        }
//        query += " recover_status = ?";
//        params.add(recoverStatus);
//      }
//
//      //String userName = String.valueOf(paramMap.get("userName"));
//      String userName = MapUtils.getString(paramMap, "userName");
//
//      if(userName != null && !userName.isEmpty()){
//        if (first) {
//          query += " WHERE ";
//          first = false;
//        } else {
//          query += " AND ";
//        }
//        query += "submit_user = ?";
//        params.add(userName);
//      }
//
//    }
//
//    if (skip > -1 && num > 0) {
//      query += "  ORDER BY recover_id DESC LIMIT ?, ?";
//      params.add(skip);
//      params.add(num);
//    }
//
//    try {
//      return this.dbOperator.query(query, new FetchLogFilterEntity(), params.toArray());
//    } catch (final SQLException e) {
//      throw new ExecutorManagerException("Error fetching history recover flows", e);
//    }
//  }
//
//
//
//  LogFilterEntity getHistoryRecoverFlowByPidAndFid(final String projectId, final String flowId)
//      throws ExecutorManagerException {
//    String query =
//        "SELECT recover_id, enc_type, recover_data FROM log_filter "
//            + "WHERE project_id = ? AND flow_id = ? "
//            + "ORDER BY start_time DESC limit 1";
//
//    final List<Object> params = new ArrayList<>();
//
//    params.add(projectId);
//
//    params.add(flowId);
//
//    try {
//      List<LogFilterEntity> recoverList = this.dbOperator.query(query, new FetchLogFilterEntity(), params.toArray());
//
//      if(recoverList.size() > 0){
//        return recoverList.get(0);
//      }else {
//        return null;
//      }
//    } catch (final SQLException e) {
//      throw new ExecutorManagerException("Error fetching history recover flows", e);
//    }
//  }
//
//  /**
//   * 历史重跑 查找 方法
//   * @param paramMap 查找参数合集
//   * @return
//   * @throws ExecutorManagerException
//   */
//  List<LogFilterEntity> listHistoryRecover(final Map<String, String> paramMap)
//      throws ExecutorManagerException {
//
//    String query = FetchLogFilterEntity.LIST_BASE_LOG_FILTER_FLOW_QUERY;
//
//    final List<Object> params = new ArrayList<>();
//
//    boolean first = false;
//
//    if(!paramMap.isEmpty()){
//
//      String recoverStatus = MapUtils.getString(paramMap, "recoverStatus", "");
//
//      if(recoverStatus != null && !recoverStatus.isEmpty()){
//        if (first) {
//          query += " WHERE ";
//          first = false;
//        } else {
//          query += " AND ";
//        }
//        query += "recover_status = ?";
//        params.add(recoverStatus);
//      }
//
//
//      Integer limitNum = MapUtils.getInteger(paramMap, "limitNum", 1);
//
//      if(recoverStatus != null && !recoverStatus.isEmpty()){
//        query += " ORDER BY recover_id DESC LIMIT ? ";
//        params.add(limitNum);
//      }
//
//    }
//
//    try {
//      return this.dbOperator.query(query, new FetchLogFilterEntity(), params.toArray());
//    } catch (final SQLException e) {
//      throw new ExecutorManagerException("Error fetching history recover flows", e);
//    }
//  }
//
//  //获取所有历史重跑总数
//  int getHistoryRecoverTotal()
//      throws ExecutorManagerException {
//    final IntHandler intHandler = new IntHandler();
//    try {
//      return this.dbOperator.query(IntHandler.NUM_EXECUTIONS, intHandler);
//    } catch (final SQLException e) {
//      throw new ExecutorManagerException("Error fetching num executions", e);
//    }
//  }
//  //根据用户名获取用户历史重跑总数
//  int getUserHistoryRecoverTotal(final String userName)
//      throws ExecutorManagerException {
//    final IntHandler intHandler = new IntHandler();
//
//    final List<Object> params = new ArrayList<>();
//
//    params.add(userName);
//
//    try {
//      return this.dbOperator.query(IntHandler.USER_NUM_EXECUTIONS, intHandler, params.toArray());
//    } catch (final SQLException e) {
//      throw new ExecutorManagerException("Error fetching num executions", e);
//    }
//  }
//
//  private static class IntHandler implements ResultSetHandler<Integer> {
//
//    private static final String NUM_EXECUTIONS =
//        "SELECT COUNT(1) FROM log_filter "
//            + "WHERE recover_status=30 ";
//
//    private static final String USER_NUM_EXECUTIONS =
//        "SELECT COUNT(1) FROM log_filter where submit_user = ? AND recover_status=30 ";
//
//    @Override
//    public Integer handle(final ResultSet rs) throws SQLException {
//      if (!rs.next()) {
//        return 0;
//      }
//      return rs.getInt(1);
//    }
//  }

}
