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

package azkaban.batch;

import azkaban.db.DatabaseOperator;
import azkaban.db.EncodingType;
import azkaban.executor.ExecutableFlow;
import azkaban.executor.ExecutorManagerException;
import azkaban.utils.GZIPUtils;
import azkaban.utils.JSONUtils;
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.dbutils.ResultSetHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.io.IOException;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Singleton
public class HoldBatchDao {

  private static final Logger logger = LoggerFactory.getLogger(HoldBatchDao.class);
  private final DatabaseOperator dbOperator;

  @Inject
  public HoldBatchDao(final DatabaseOperator dbOperator) {
    this.dbOperator = dbOperator;
  }

  public void addHoldBatchOpr(String id, int oprType, int oprLevel, String user, long createTime,
      String oprData)
      throws ExecutorManagerException {
    final String INSERT = "INSERT INTO hold_batch_opr (id, opr_type, opr_level, create_user, create_time, status, opr_data) values (?,?,?,?,?,'0',?)";
    try {
      this.dbOperator.update(INSERT, id, oprType, oprLevel, user, createTime, oprData);
    } catch (final SQLException e) {
      logger.error("Error Insert Hold Batch Operate", e);
      throw new ExecutorManagerException("Error Insert Hold Batch Operate", e);
    }
  }

  public void addHoldBatchResume(String batchId, String oprData, String user)
      throws ExecutorManagerException {
    final String INSERT = "INSERT INTO hold_batch_resume (batch_id, resume_data, create_user, create_time) values (?,?,?,?)";
    try {
      this.dbOperator.update(INSERT, batchId, oprData, user, System.currentTimeMillis());
    } catch (final SQLException e) {
      logger.error("Error Insert Hold Batch Resume", e);
      throw new ExecutorManagerException("Error Insert Hold Batch Resume", e);
    }
  }

  public void addHoldBatchAlert(String batchId, ExecutableFlow executableFlow, int resumeStatus)
      throws ExecutorManagerException {
    final String INSERT = "INSERT INTO hold_batch_alert (batch_id, project_name, flow_name, exec_id, create_user, create_time, update_time, is_resume, flow_data) values (?,?,?,?,?,?,?,?,?)";
    try {
      final String json = JSONUtils.toJSON(executableFlow.toObject());
      byte[] data = GZIPUtils.gzipBytes(json.getBytes("UTF-8"));
      this.dbOperator
          .update(INSERT, batchId, executableFlow.getProjectName(), executableFlow.getId(),
              executableFlow.getExecutionId(), executableFlow.getSubmitUser(),
              executableFlow.getSubmitTime(), System.currentTimeMillis(), resumeStatus, data);
    } catch (final Exception e) {
      logger.error("Error Insert Hold Batch Alert", e);
      throw new ExecutorManagerException("Error Insert Hold Batch Alert", e);
    }
  }

  public void addHoldBatchFrequent(String batchId, ExecutableFlow executableFlow)
      throws ExecutorManagerException {
    final String INSERT = "INSERT INTO hold_batch_frequent (batch_id, project_name, flow_name, submit_user) values (?,?,?,?) ON DUPLICATE KEY UPDATE submit_user = VALUES(submit_user)";
    try {
      this.dbOperator
          .update(INSERT, batchId, executableFlow.getProjectName(), executableFlow.getId(),
              executableFlow.getSubmitUser());
    } catch (final Exception e) {
      logger.error("Error Insert Hold Batch Frequent", e);
      throw new ExecutorManagerException("Error Insert Hold Batch Frequent", e);
    }
  }

  public void updateHoldBatchExpired(String batchId)
      throws ExecutorManagerException {
    final String INSERT = "update hold_batch_opr set status='2' where id!=?";
    try {
      this.dbOperator.update(INSERT, batchId);
    } catch (final SQLException e) {
      logger.error("Error update Hold Batch Expired", e);
      throw new ExecutorManagerException("Error update Hold Batch Expired", e);
    }
  }

  public void updateHoldBatchId(String batchId)
      throws ExecutorManagerException {
    final String INSERT = "update hold_batch_alert set batch_id=? where is_resume='0'";
    try {
      this.dbOperator.update(INSERT, batchId);
    } catch (final SQLException e) {
      logger.error("Error update Hold Batch Id", e);
      throw new ExecutorManagerException("Error update Hold Batch Id", e);
    }
  }

  public void updateHoldBatchFrequentStatus(HoldBatchAlert holdBatchAlert)
      throws ExecutorManagerException {
    final String INSERT = "update hold_batch_frequent set send_status=?,send_time=? where id=?";
    try {
      this.dbOperator
          .update(INSERT, holdBatchAlert.getSendStatus(), holdBatchAlert.getSendTime(),
              holdBatchAlert.getId());
    } catch (final SQLException e) {
      logger.error("Error update Hold Batch Frequent status", e);
      throw new ExecutorManagerException("Error update Hold Batch Frequent status", e);
    }
  }

  public void updateHoldBatchAlertStatus(HoldBatchAlert holdBatchAlert)
      throws ExecutorManagerException {
    final String INSERT = "update hold_batch_alert set send_status=?,send_time=? where id=?";
    try {
      this.dbOperator
          .update(INSERT, holdBatchAlert.getSendStatus(), holdBatchAlert.getSendTime(),
              holdBatchAlert.getId());
    } catch (final SQLException e) {
      logger.error("Error update Hold Batch Alert status", e);
      throw new ExecutorManagerException("Error update Hold Batch Alert status", e);
    }
  }

  public void updateHoldBatchNotResumeByExecId(int execId) {
    final String INSERT = "update hold_batch_alert set is_resume='2' where exec_id=? and is_resume='0'";
    try {
      this.dbOperator
          .update(INSERT, execId);
    } catch (final SQLException e) {
      logger.error("Error update Hold Batch not resume", e);
    }
  }

  public void updateHoldBatchResumeStatus(HoldBatchAlert holdBatchAlert)
      throws ExecutorManagerException {
    final String INSERT = "update hold_batch_alert set exec_id=?,is_resume=?,resume_time=?,is_black=? where id=?";
    try {
      this.dbOperator
          .update(INSERT, holdBatchAlert.getExecId(), holdBatchAlert.isResume(),
              holdBatchAlert.getResumeTime(), holdBatchAlert.isBlack(), holdBatchAlert.getId());
    } catch (final SQLException e) {
      logger.error("Error update Hold Batch Alert status", e);
      throw new ExecutorManagerException("Error update Hold Batch Alert status", e);
    }
  }

  public void updateHoldBatchStatus(String batchId, int status)
      throws ExecutorManagerException {
    final String INSERT = "update hold_batch_opr set status=? where id=? ";
    try {
      this.dbOperator.update(INSERT, status, batchId);
    } catch (final SQLException e) {
      logger.error("Error update Hold Batch status", e);
      throw new ExecutorManagerException("Error update Hold Batch status", e);
    }
  }

  public List<HoldBatchOperate> getLocalHoldBatchOpr()
      throws ExecutorManagerException {
    try {
      String query = FetchHoldBatchHandler.FETCH_LOCAL_OPERATE;
      return this.dbOperator.query(query, new FetchHoldBatchHandler());
    } catch (final SQLException e) {
      logger.error("Error Query Hold Batch", e);
      throw new ExecutorManagerException("Error Query Hold Batch", e);
    }
  }

  public List<HoldBatchOperate> getMissResumeBatch()
      throws ExecutorManagerException {
    try {
      String query = FetchHoldBatchHandler.FETCH_MISS_RESUME_OPERATE;
      return this.dbOperator.query(query, new FetchHoldBatchHandler());
    } catch (final SQLException e) {
      logger.error("Error Query Miss Resume Batch", e);
      throw new ExecutorManagerException("Error Query Miss Resume Batch", e);
    }
  }


  /**
   * JDBC ResultSetHandler to fetch records from hold_batch_opr table
   */
  public static class FetchHoldBatchHandler implements
      ResultSetHandler<List<HoldBatchOperate>> {

    static String FETCH_LOCAL_OPERATE =
        "SELECT hbo.id, hbo.opr_type, hbo.opr_level, hbo.create_time, hbo.opr_data FROM hold_batch_opr hbo where hbo.status='0'";

    static String FETCH_MISS_RESUME_OPERATE =
        "SELECT distinct hbo.id, hbo.opr_type, hbo.opr_level, hbo.create_time, hbr.resume_data FROM hold_batch_opr hbo,hold_batch_resume hbr,hold_batch_alert hba where hbr.batch_id=hbo.id and hbr.batch_id=hba.batch_id and hba.is_resume='0' and hbo.status>0";

    @Override
    public List<HoldBatchOperate> handle(final ResultSet rs) throws SQLException {
      List<HoldBatchOperate> list = new ArrayList<>();

      while (rs.next()) {
        HoldBatchOperate holdBatchAlert = new HoldBatchOperate(rs.getString(1), rs.getInt(2),
            rs.getInt(3), rs.getLong(4), rs.getString(5));

        list.add(holdBatchAlert);
      }

      return list;
    }
  }

  public List<HoldBatchAlert> queryFrequentByBatch(String batchId)
      throws ExecutorManagerException {
    try {
      return this.dbOperator
          .query(FetchHoldBatchFrequentHandler.FETCH_FREQUENT_BY_BATCH,
              new FetchHoldBatchFrequentHandler(),
              batchId);
    } catch (final SQLException e) {
      logger.error("Error Query Hold Batch Frequent", e);
      throw new ExecutorManagerException("Error Query Hold Batch Frequent", e);
    }
  }


  /**
   * JDBC ResultSetHandler to fetch records from hold_batch_alert table
   */
  public static class FetchHoldBatchFrequentHandler implements
      ResultSetHandler<List<HoldBatchAlert>> {

    static String FETCH_FREQUENT_BY_BATCH =
        "SELECT id,batch_id, project_name, flow_name, submit_user FROM hold_batch_frequent where batch_id = ? and send_status ='0'";

    @Override
    public List<HoldBatchAlert> handle(final ResultSet rs) throws SQLException {
      List<HoldBatchAlert> list = new ArrayList<>();

      while (rs.next()) {
        HoldBatchAlert holdBatchFrequent = new HoldBatchAlert();
        holdBatchFrequent.setId(rs.getLong(1));
        holdBatchFrequent.setBatchId(rs.getString(2));
        holdBatchFrequent.setProjectName(rs.getString(3));
        holdBatchFrequent.setFlowName(rs.getString(4));
        holdBatchFrequent.setCreateUser(rs.getString(5));
        list.add(holdBatchFrequent);
      }

      return list;
    }
  }

  public List<HoldBatchAlert> queryAlertByBatch(String batchId)
      throws ExecutorManagerException {
    try {
      return this.dbOperator
          .query(FetchHoldBatchAlertHandler.FETCH_ALERT_BY_BATCH, new FetchHoldBatchAlertHandler(),
              batchId);
    } catch (final SQLException e) {
      logger.error("Error Query Hold Batch Alert", e);
      throw new ExecutorManagerException("Error Query Hold Batch Alert", e);
    }
  }

  public List<HoldBatchAlert> queryExecByBatch(String batchId)
      throws ExecutorManagerException {
    try {
      return this.dbOperator
          .query(FetchHoldBatchAlertHandler.FETCH_EXEC_BY_BATCH, new FetchHoldBatchAlertHandler(),
              batchId);
    } catch (final SQLException e) {
      logger.error("Error Query Hold Batch Exec", e);
      throw new ExecutorManagerException("Error Query Hold Batch Exec", e);
    }
  }

  public List<HoldBatchAlert> queryExecingByBatch(String batchId)
      throws ExecutorManagerException {
    try {
      return this.dbOperator
          .query(FetchHoldBatchAlertHandler.FETCH_EXECING_BY_BATCH,
              new FetchHoldBatchAlertHandler(), batchId);
    } catch (final SQLException e) {
      logger.error("Error Query Hold Batch Execing", e);
      throw new ExecutorManagerException("Error Query Hold Batch Execing", e);
    }
  }


  /**
   * JDBC ResultSetHandler to fetch records from hold_batch_alert table
   */
  public static class FetchHoldBatchAlertHandler implements
      ResultSetHandler<List<HoldBatchAlert>> {

    static String FETCH_ALERT_BY_BATCH =
        "SELECT id,batch_id, project_name, flow_name, exec_id, create_user, create_time,'2', flow_data FROM hold_batch_alert where batch_id = ? and is_resume = '0' and exec_id =-1";

    static String FETCH_EXEC_BY_BATCH =
        "SELECT hba.id,hba.batch_id, hba.project_name, hba.flow_name, hba.exec_id, hba.create_user, hba.create_time,ef.enc_type, ef.flow_data FROM hold_batch_alert hba,execution_flows ef where hba.exec_id=ef.exec_id and ef.executor_id is null and hba.batch_id = ? and hba.is_resume = '0' and hba.exec_id !=-1";

    static String FETCH_EXECING_BY_BATCH =
        "SELECT hba.id,hba.batch_id, hba.project_name, hba.flow_name, hba.exec_id, hba.create_user, hba.create_time,'1', null FROM hold_batch_alert hba,execution_flows ef where hba.exec_id=ef.exec_id and ef.executor_id is not null and hba.batch_id = ? and hba.is_resume = '0' and hba.exec_id !=-1";

    @Override
    public List<HoldBatchAlert> handle(final ResultSet rs) throws SQLException {
      List<HoldBatchAlert> list = new ArrayList<>();

      while (rs.next()) {
        HoldBatchAlert holdBatchAlert = new HoldBatchAlert();
        holdBatchAlert.setId(rs.getLong(1));
        holdBatchAlert.setBatchId(rs.getString(2));
        holdBatchAlert.setProjectName(rs.getString(3));
        holdBatchAlert.setFlowName(rs.getString(4));
        holdBatchAlert.setExecId(rs.getInt(5));
        holdBatchAlert.setCreateUser(rs.getString(6));
        holdBatchAlert.setCreateTime(rs.getLong(7));

        final int encodingType = rs.getInt(8);
        final byte[] data = rs.getBytes(9);
        if (data != null) {
          final EncodingType encType = EncodingType.fromInteger(encodingType);
          try {
            final ExecutableFlow exFlow =
                ExecutableFlow.createExecutableFlowFromObject(
                    GZIPUtils.transformBytesToObject(data, encType));
            holdBatchAlert.setExecutableFlow(exFlow);
          } catch (final IOException e) {
            throw new SQLException("Error retrieving flow data ", e);
          }
        }
        list.add(holdBatchAlert);
      }

      return list;
    }
  }

  public HoldBatchAlert queryBatchExecutableFlows(long id)
      throws ExecutorManagerException {
    try {
      return this.dbOperator
          .query(FetchBatchExecutableFlows.FETCH_EXECUTABLE_FLOW_BY_BATCH,
              new FetchBatchExecutableFlows(), id);
    } catch (final SQLException e) {
      logger.error("Error Query Batch flow", e);
      throw new ExecutorManagerException("Error Query Batch flow", e);
    }
  }

  public HoldBatchAlert querySubmittedExecutableFlows(long id)
      throws ExecutorManagerException {
    try {
      return this.dbOperator
          .query(FetchBatchExecutableFlows.FETCH_EXECUTABLE_FLOW_SUBMITTED,
              new FetchBatchExecutableFlows(), id);
    } catch (final SQLException e) {
      logger.error("Error Query Batch flow", e);
      throw new ExecutorManagerException("Error Query Batch flow", e);
    }
  }

  public static class FetchBatchExecutableFlows implements
      ResultSetHandler<HoldBatchAlert> {

    static String FETCH_EXECUTABLE_FLOW_BY_BATCH =
        "SELECT id,batch_id, project_name, flow_name, exec_id, create_user, create_time,'2', flow_data FROM hold_batch_alert where id=? and is_resume = '0'";

    static String FETCH_EXECUTABLE_FLOW_SUBMITTED =
        "SELECT hba.id,hba.batch_id, hba.project_name, hba.flow_name, hba.exec_id, hba.create_user, hba.create_time,ef.enc_type, ef.flow_data FROM hold_batch_alert hba,execution_flows ef where hba.exec_id=ef.exec_id and hba.id=? and hba.is_resume = '0'";

    @Override
    public HoldBatchAlert handle(final ResultSet rs) throws SQLException {

      if (rs.next()) {
        HoldBatchAlert holdBatchAlert = new HoldBatchAlert();
        holdBatchAlert.setId(rs.getLong(1));
        holdBatchAlert.setBatchId(rs.getString(2));
        holdBatchAlert.setProjectName(rs.getString(3));
        holdBatchAlert.setFlowName(rs.getString(4));
        holdBatchAlert.setExecId(rs.getInt(5));
        holdBatchAlert.setCreateUser(rs.getString(6));
        holdBatchAlert.setCreateTime(rs.getLong(7));

        final int encodingType = rs.getInt(8);
        final byte[] data = rs.getBytes(9);
        if (data != null) {
          final EncodingType encType = EncodingType.fromInteger(encodingType);
          try {
            final ExecutableFlow exFlow =
                ExecutableFlow.createExecutableFlowFromObject(
                    GZIPUtils.transformBytesToObject(data, encType));
            holdBatchAlert.setExecutableFlow(exFlow);
          } catch (final IOException e) {
            throw new SQLException("Error retrieving flow data ", e);
          }
        }
        return holdBatchAlert;
      }

      return null;
    }
  }

  public void updateHoldBatchResumeStatus(String projectName, String flowName)
      throws ExecutorManagerException {
    final String INSERT = "update hold_batch_alert set is_resume=1 where project_name=? and flow_name=?";
    try {
      this.dbOperator
          .update(INSERT, projectName, flowName);
    } catch (final SQLException e) {
      logger.error("Error update Hold Batch resume status", e);
      throw new ExecutorManagerException("Error update Hold Batch resume status", e);
    }
  }

  public String getLocalHoldBatchResume(String batchId) throws ExecutorManagerException {
    try {
      return this.dbOperator
          .query(FetchResumeBatchHandler.FETCH_LOCAL_RESUME, new FetchResumeBatchHandler(),
              batchId);
    } catch (final SQLException e) {
      logger.error("Error Query Resume Batch", e);
      throw new ExecutorManagerException("Error Query Resume Batch", e);
    }
  }


  /**
   * JDBC ResultSetHandler to fetch records from hold_batch_opr table
   */
  public static class FetchResumeBatchHandler implements
      ResultSetHandler<String> {

    static String FETCH_LOCAL_RESUME =
        "SELECT resume_data FROM hold_batch_resume where batch_id=? limit 1";

    @Override
    public String handle(final ResultSet rs) throws SQLException {

      if (rs.next()) {
        return rs.getString(1);
      } else {
        return "";
      }
    }
  }

  public List<String> queryTenantListByUser(String userNames)
      throws ExecutorManagerException {
    try {
      return this.dbOperator
          .query(FetchListHandler.FETCH_GROUP_LIST, new FetchListHandler(),
              userNames);
    } catch (final SQLException e) {
      logger.error("Error Query Tenant List", e);
      throw new ExecutorManagerException("Error Query Tenant List", e);
    }
  }

  public List<String> queryUserListByTenant(String groupIds)
      throws ExecutorManagerException {
    try {
      return this.dbOperator
          .query(FetchListHandler.FETCH_USER_LIST, new FetchListHandler(),
              groupIds);
    } catch (final SQLException e) {
      logger.error("Error Query Tenant List", e);
      throw new ExecutorManagerException("Error Query Tenant List", e);
    }
  }


  /**
   * JDBC ResultSetHandler to fetch records from hold_batch_alert table
   */
  public static class FetchListHandler implements
      ResultSetHandler<List<String>> {

    static String FETCH_GROUP_LIST =
        "SELECT distinct cwo.group_id  FROM wtss_user wu,cfg_webank_organization cwo,department_group dg where wu.department_id=cwo.dp_id and cwo.group_id=dg.id and wu.username in (?)";

    static String FETCH_USER_LIST =
        "SELECT distinct wu.username  FROM wtss_user wu,cfg_webank_organization cwo,department_group dg where wu.department_id=cwo.dp_id and cwo.group_id=dg.id and dg.id in (?)";

    @Override
    public List<String> handle(final ResultSet rs) throws SQLException {
      List<String> list = new ArrayList<>();

      while (rs.next()) {
        list.add(rs.getString(1));
      }

      return list;
    }
  }

  public String getFlowBusPath(String project, String flow) {
    try {
      return this.dbOperator
          .query(BusPathAndCriticalPathResultHandler.GET_BUS_PATH, new BusPathAndCriticalPathResultHandler(), project, flow);
    } catch (final SQLException e) {
      logger.error("Error Query Flow Path", e);
      return "";
    }
  }

  public String getCriticalPath(String project, String flow) {
    try {
      return this.dbOperator
              .query(BusPathAndCriticalPathResultHandler.GET_CRITICAL_PATH, new BusPathAndCriticalPathResultHandler(), project, flow);
    } catch (final SQLException e) {
      logger.error("Error Query Flow Path", e);
      return "";
    }
  }

  public static class BusPathAndCriticalPathResultHandler implements ResultSetHandler<String> {

    public static String GET_BUS_PATH =
        "SELECT fb.bus_path FROM flow_business fb,projects p " +
            "WHERE fb.project_id=p.id AND p.active='1' AND p.name=? AND flow_id=? AND job_id='' limit 1";
    public static String GET_CRITICAL_PATH =
        "SELECT fb.batch_group FROM flow_business fb,projects p " +
            "WHERE fb.project_id=p.id AND p.active='1' AND p.name=? AND flow_id=? AND job_id='' limit 1";

    @Override
    public String handle(final ResultSet rs) throws SQLException {
      if (rs.next()) {
        return rs.getString(1);
      } else {
        return "";
      }

    }
  }

  public List<Integer> queryWaitingFlow(String project, String flow) {
    try {
      return this.dbOperator
          .query(AlertIdResultHandler.QUERY_ALERT_ID_LIST, new AlertIdResultHandler(), project,
              flow);
    } catch (final SQLException e) {
      logger.error("Error Query Flow Path", e);
      return new ArrayList<>();
    }
  }

  public static class AlertIdResultHandler implements ResultSetHandler<List<Integer>> {

    public static String QUERY_ALERT_ID_LIST =
        "SELECT id FROM hold_batch_alert WHERE project_name=? and flow_name=? and is_resume=0 and exec_id=-1";

    @Override
    public List<Integer> handle(final ResultSet rs) throws SQLException {
      List<Integer> list = new ArrayList<>();

      while (rs.next()) {
        list.add(rs.getInt(1));
      }

      return list.stream().sorted(Integer::compareTo).collect(Collectors.toList());
    }
  }

  public HoldBatchAlert getHoldBatchAlert(long id) {
    try {
      List<HoldBatchAlert> list = this.dbOperator
          .query(HoldBatchAlertHandler.GET_BATCH_ALERT_BY_ID, new HoldBatchAlertHandler(), id);
      return CollectionUtils.isNotEmpty(list) ? list.get(0) : new HoldBatchAlert();
    } catch (final SQLException e) {
      logger.error("Error Query Flow Path", e);
      return new HoldBatchAlert();
    }
  }

  public List<HoldBatchAlert> queryWaitingAlert() {
    try {
      return this.dbOperator
          .query(HoldBatchAlertHandler.QUERY_UN_ALERT_LIST, new HoldBatchAlertHandler());
    } catch (final SQLException e) {
      logger.error("Error Query Flow Path", e);
      return new ArrayList<>();
    }
  }

  public static class HoldBatchAlertHandler implements ResultSetHandler<List<HoldBatchAlert>> {

    public static String GET_BATCH_ALERT_BY_ID =
        "SELECT id,batch_id,project_name,flow_name,exec_id,create_user,create_time,send_status,send_time,resume_time,is_black,is_resume FROM hold_batch_alert WHERE id=?";

    public static String QUERY_UN_ALERT_LIST =
        "SELECT id,batch_id,project_name,flow_name,exec_id,create_user,create_time,send_status,send_time,resume_time,is_black,is_resume FROM hold_batch_alert WHERE is_resume=2 and send_status=0";

    @Override
    public List<HoldBatchAlert> handle(final ResultSet rs) throws SQLException {
      List<HoldBatchAlert> list = new ArrayList<>();
      while (rs.next()) {
        HoldBatchAlert holdBatchAlert = new HoldBatchAlert();
        holdBatchAlert.setId(rs.getInt(1));
        holdBatchAlert.setBatchId(rs.getString(2));
        holdBatchAlert.setProjectName(rs.getString(3));
        holdBatchAlert.setFlowName(rs.getString(4));
        holdBatchAlert.setExecId(rs.getInt(5));
        holdBatchAlert.setCreateUser(rs.getString(6));
        holdBatchAlert.setCreateTime(rs.getLong(7));
        holdBatchAlert.setSendStatus(rs.getInt(8));
        holdBatchAlert.setSendTime(rs.getLong(9));
        holdBatchAlert.setResumeTime(rs.getLong(10));
        holdBatchAlert.setBlack(rs.getInt(11));
        holdBatchAlert.setResume(rs.getInt(12));
        list.add(holdBatchAlert);
      }
      return list;
    }
  }

}
