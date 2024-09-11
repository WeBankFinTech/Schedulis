package azkaban.dao.impl;

import azkaban.dao.BatchLoader;
import azkaban.db.DatabaseOperator;
import azkaban.exception.BatchManagerException;
import org.apache.commons.dbutils.ResultSetHandler;
import org.apache.commons.lang.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.*;

/**
 * Created by zhu on 7/6/18.
 */
@Singleton
public class JdbcBatchImpl implements BatchLoader {

  private static final Logger logger = LoggerFactory.getLogger(JdbcBatchImpl.class);

  private final DatabaseOperator dbOperator;

  @Inject
  public JdbcBatchImpl(DatabaseOperator dbOperator) {
    this.dbOperator = dbOperator;
  }

  @Override
  public List<Map<String, Object>> queryHoldBatchList(String projectName, String flowName,
      String busPath, String batchId, String subSystem, String devDept, String submitUser,
      String execId, int start, int pageSize) throws BatchManagerException {
    try {
      StringBuilder querySql = new StringBuilder(FetchBatchListHandler.FETCH_BATCH_LIST);
      final List<Object> params = new ArrayList<>();

      boolean first = true;
      if (StringUtils.isNotBlank(projectName)) {
        first = wrapperSqlParam(first, projectName, querySql, "hba.project_name", "LIKE", params);
      }

      if (StringUtils.isNotBlank(flowName)) {
        first = wrapperSqlParam(first, flowName, querySql, "hba.flow_name", "LIKE", params);
      }

      if (StringUtils.isNotBlank(busPath)) {
        first = wrapperSqlParam(first, busPath, querySql, "fb.bus_path", "=", params);
      }

      if (StringUtils.isNotBlank(batchId)) {
        first = wrapperSqlParam(first, batchId, querySql, "hba.batch_id", "LIKE", params);
      }

      if (StringUtils.isNotBlank(subSystem)) {
        first = wrapperSqlParam(first, subSystem, querySql, "fb.subsystem", "=", params);
      }

      if (StringUtils.isNotBlank(devDept)) {
        first = wrapperSqlParam(first, devDept, querySql, "fb.dev_dept", "=", params);
      }

      if (StringUtils.isNotBlank(submitUser)) {
        first = wrapperSqlParam(first, submitUser, querySql, "hba.create_user", "LIKE", params);
      }

      if (StringUtils.isNotBlank(execId)) {
        first = wrapperSqlParam(first, execId, querySql, "hba.exec_id", "LIKE", params);
      }

      querySql.append(" ORDER BY hba.create_time DESC,hba.id LIMIT ?, ?");
      params.add(start * pageSize);
      params.add(pageSize);

      return this.dbOperator
          .query(querySql.toString(), new FetchBatchListHandler(), params.toArray());
    } catch (final SQLException e) {
      throw new BatchManagerException("Error fetching batch list", e);
    }
  }

  @Override
  public long getHoldBatchTotal(String projectName, String flowName,
      String busPath, String batchId, String subSystem, String devDept, String submitUser,
      String execId) throws BatchManagerException {
    try {
      StringBuilder querySql = new StringBuilder(LongHandler.GET_BATCH_TOTAL);
      final List<Object> params = new ArrayList<>();

      boolean first = true;
      if (StringUtils.isNotBlank(projectName)) {
        first = wrapperSqlParam(first, projectName, querySql, "hba.project_name", "LIKE", params);
      }

      if (StringUtils.isNotBlank(flowName)) {
        first = wrapperSqlParam(first, flowName, querySql, "hba.flow_name", "LIKE", params);
      }

      if (StringUtils.isNotBlank(busPath)) {
        first = wrapperSqlParam(first, busPath, querySql, "fb.bus_path", "=", params);
      }

      if (StringUtils.isNotBlank(batchId)) {
        first = wrapperSqlParam(first, batchId, querySql, "hba.batch_id", "LIKE", params);
      }

      if (StringUtils.isNotBlank(subSystem)) {
        first = wrapperSqlParam(first, subSystem, querySql, "fb.subsystem", "=", params);
      }

      if (StringUtils.isNotBlank(devDept)) {
        first = wrapperSqlParam(first, devDept, querySql, "fb.dev_dept", "=", params);
      }

      if (StringUtils.isNotBlank(submitUser)) {
        first = wrapperSqlParam(first, submitUser, querySql, "hba.create_user", "LIKE", params);
      }

      if (StringUtils.isNotBlank(execId)) {
        first = wrapperSqlParam(first, execId, querySql, "hba.exec_id", "LIKE", params);
      }

      return this.dbOperator
          .query(querySql.toString(), new LongHandler(), params.toArray());
    } catch (final SQLException e) {
      throw new BatchManagerException("Error fetching batch total", e);
    }
  }

  @Override
  public long getHoldBatchTotal(String searchterm, int start, int pageSize)
      throws BatchManagerException {
    try {
      StringBuilder querySql = new StringBuilder(LongHandler.GET_BATCH_TOTAL);
      final List<Object> params = new ArrayList<>();

      if (StringUtils.isNotEmpty(searchterm)) {
        querySql
            .append(
                " where (hba.project_name like ? or hba.flow_name like ?)");
        for (int i = 0; i < 2; i++) {
          params.add("%" + searchterm + "%");
        }
      }

      return this.dbOperator
          .query(querySql.toString(), new LongHandler(), params.toArray());
    } catch (final SQLException e) {
      throw new BatchManagerException("Error fetching batch total", e);
    }
  }

  @Override
  public List<Map<String, Object>> queryHoldBatchList(String searchterm, int start,
      int pageSize) throws BatchManagerException {
    try {
      StringBuilder querySql = new StringBuilder(FetchBatchListHandler.FETCH_BATCH_LIST);
      final List<Object> params = new ArrayList<>();

      if (StringUtils.isNotEmpty(searchterm)) {
        querySql
            .append(
                " where (hba.project_name like ? or hba.flow_name like ?)");
        for (int i = 0; i < 2; i++) {
          params.add("%" + searchterm + "%");
        }
      }

      querySql.append(" ORDER BY hba.create_time DESC,hba.id LIMIT ?, ?");
      params.add(start * pageSize);
      params.add(pageSize);

      return this.dbOperator
          .query(querySql.toString(), new FetchBatchListHandler(), params.toArray());
    } catch (final SQLException e) {
      throw new BatchManagerException("Error fetching batch list", e);
    }
  }

  @Override
  public List<String> getBatchIdListByLevel(String batchLevel) throws BatchManagerException {
    try {
      return this.dbOperator
          .query(FetchBatchIdListHandler.FETCH_BATCH_ID_LIST, new FetchBatchIdListHandler(),
              batchLevel);
    } catch (final SQLException e) {
      throw new BatchManagerException("Error fetching batch list", e);
    }
  }


  public static class FetchBatchIdListHandler implements
      ResultSetHandler<List<String>> {

    static String FETCH_BATCH_ID_LIST = "select id from hold_batch_opr where opr_level=? and status=0";


    @Override
    public List<String> handle(final ResultSet rs) throws SQLException {
      if (!rs.next()) {
        return Collections.emptyList();
      }

      final List<String> batchList = new ArrayList<>();
      do {
        batchList.add(rs.getString(1));
      } while (rs.next());

      return batchList;
    }
  }

  public static class LongHandler implements ResultSetHandler<Long> {

    public static String GET_BATCH_TOTAL = "SELECT count(*) "
        + "FROM hold_batch_alert hba left join hold_batch_opr hbo on hba.batch_id=hbo.id left join projects p on hba.project_name=p.name left join flow_business fb on p.id=fb.project_id and hba.flow_name=fb.flow_id and fb.job_id ='' ";

    public static String GET_BATCH_FLOW_TOTAL = "SELECT COUNT(distinct project_name,flow_name) FROM HOLD_BATCH_ALERT WHERE IS_RESUME='0' ";

    @Override
    public Long handle(final ResultSet rs) throws SQLException {
      if (!rs.next()) {
        return 0L;
      }
      return rs.getLong(1);
    }
  }

  public static class FetchBatchListHandler implements
      ResultSetHandler<List<Map<String, Object>>> {

    static String FETCH_BATCH_LIST =
        "SELECT hba.id,hba.batch_id, hba.project_name, hba.flow_name, hba.exec_id, hba.create_user, hba.create_time, hba.update_time, hba.send_status, hba.send_time, hba.is_resume, hba.resume_time, hba.is_black, hbo.opr_type, hbo.opr_level, hbo.create_user, hbo.create_time, fb.bus_path, fb.subsystem, fb.last_start_time, fb.last_finish_time, cwo.dp_ch_name "
            + "FROM hold_batch_alert hba left join hold_batch_opr hbo on hba.batch_id=hbo.id left join projects p on hba.project_name=p.name left join flow_business fb on p.id=fb.project_id and hba.flow_name=fb.flow_id and fb.job_id ='' left join cfg_webank_organization cwo on fb.dev_dept=cwo.dp_id ";


    @Override
    public List<Map<String, Object>> handle(final ResultSet rs) throws SQLException {
      if (!rs.next()) {
        return Collections.emptyList();
      }

      final List<Map<String, Object>> batchList = new ArrayList<>();
      do {
        Map<String, Object> result = new HashMap<>();
        result.put("id", rs.getLong(1));
        result.put("batchId", rs.getString(2));
        result.put("projectName", rs.getString(3));
        result.put("flowName", rs.getString(4));
        result.put("execId", rs.getLong(5));
        result.put("submitUser", rs.getString(6));
        result.put("createTime", rs.getLong(7));
        result.put("updateTime", rs.getLong(8));
        result.put("sendStatus", rs.getInt(9));
        result.put("sendTime", rs.getLong(10));
        result.put("isResume", rs.getInt(11));
        result.put("resumeTime", rs.getLong(12));
        result.put("isBlack", rs.getInt(13));
        result.put("oprType", rs.getInt(14));
        result.put("oprLevel", rs.getInt(15));
        result.put("holdUser", rs.getString(16));
        result.put("holdTime", rs.getLong(17));
        result.put("busPath", rs.getString(18));
        result.put("subsystem", rs.getString(19));
        result.put("lastStartTime", rs.getString(20));
        result.put("lastFinishTime", rs.getString(21));
        result.put("devDept", rs.getString(22));
        batchList.add(result);
      } while (rs.next());

      return batchList;
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

  @Override
  public List<String> queryBatchFlowList(String search, int start, int pageSize)
      throws BatchManagerException {
    try {
      StringBuilder querySql = new StringBuilder(FetchBatchFlowHandler.FETCH_BATCH_FLOW);
      final List<Object> params = new ArrayList<>();

      if (StringUtils.isNotEmpty(search)) {
        querySql
            .append(
                " and (project_name like ? or flow_name like ?) ");
        for (int i = 0; i < 2; i++) {
          params.add("%" + search + "%");
        }
      }

      querySql.append(" LIMIT ?, ?");
      params.add(start);
      params.add(pageSize);

      return this.dbOperator
          .query(querySql.toString(), new FetchBatchFlowHandler(), params.toArray());
    } catch (final SQLException e) {
      throw new BatchManagerException("Error fetching batch flow list", e);
    }
  }

  public static class FetchBatchFlowHandler implements
      ResultSetHandler<List<String>> {

    static String FETCH_BATCH_FLOW =
        "SELECT distinct project_name, flow_name FROM hold_batch_alert where is_resume='0' ";


    @Override
    public List<String> handle(final ResultSet rs) throws SQLException {
      if (!rs.next()) {
        return Collections.emptyList();
      }

      final List<String> flowList = new ArrayList<>();
      do {
        flowList.add(rs.getString(1) + "-" + rs.getString(2));
      } while (rs.next());

      return flowList;
    }
  }

  @Override
  public long getHoldBatchFlowTotal(String search) throws BatchManagerException {
    try {
      StringBuilder querySql = new StringBuilder(LongHandler.GET_BATCH_FLOW_TOTAL);
      final List<Object> params = new ArrayList<>();
      if (StringUtils.isNotEmpty(search)) {
        querySql
            .append(
                " and (project_name like ? or flow_name like ?) ");
        for (int i = 0; i < 2; i++) {
          params.add("%" + search + "%");
        }
      }
      return this.dbOperator.query(querySql.toString(), new LongHandler(), params.toArray());
    } catch (final SQLException e) {
      throw new BatchManagerException("Error fetching batch flow total", e);
    }
  }
}
