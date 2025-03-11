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

package azkaban.executor.dao;

import azkaban.db.DatabaseOperator;
import azkaban.executor.ExecutableFlow;
import azkaban.executor.ExecutableJobInfo;
import azkaban.executor.ExecutableNode;
import azkaban.executor.ExecutorDao;
import azkaban.executor.ExecutorManagerException;
import azkaban.executor.HistoryQueryParam;
import azkaban.executor.Status;
import azkaban.executor.entity.JobPredictionExecutionInfo;
import azkaban.jobhook.JdbcJobHookHandlerSet;
import azkaban.jobhook.JdbcJobHookHandlerSet.JobHookResultHandler;
import azkaban.jobhook.JobHook;
import azkaban.project.ProjectManagerException;
import azkaban.utils.GZIPUtils;
import azkaban.utils.JSONUtils;
import azkaban.utils.Pair;
import azkaban.utils.Props;
import azkaban.utils.PropsUtils;
import java.io.File;
import java.io.IOException;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import javax.inject.Inject;
import javax.inject.Singleton;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.dbutils.ResultSetHandler;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Singleton
public class ExecutionJobDao {

  private static final Logger logger = LoggerFactory.getLogger(ExecutorDao.class);
  private final DatabaseOperator dbOperator;

  @Inject
  ExecutionJobDao(final DatabaseOperator databaseOperator) {
    this.dbOperator = databaseOperator;
  }

  public void uploadExecutableNode(final ExecutableNode node, final Props inputProps)
      throws ExecutorManagerException {
    final String INSERT_EXECUTION_NODE = "INSERT INTO execution_jobs "
        + "(exec_id, project_id, version, flow_id, job_id, start_time, "
        + "end_time, status, input_params, attempt) VALUES (?,?,?,?,?,?,?,?,?,?)";

    byte[] inputParam = null;
    if (inputProps != null) {
      try {
        final String jsonString =
            JSONUtils.toJSON(PropsUtils.toHierarchicalMap(inputProps));
        logger.debug("the job's inputParam is : " + jsonString);
        inputParam = GZIPUtils.gzipString(jsonString, "UTF-8");
      } catch (final Throwable e) {
        logger.error("to json failed.", e);
      }
    }

    final ExecutableFlow flow = node.getExecutableFlow();
    final String flowId = node.getParentFlow().getFlowPath();
    logger.info("Uploading flowId " + flowId);
    try {
      this.dbOperator.update(INSERT_EXECUTION_NODE, flow.getExecutionId(),
          flow.getProjectId(), flow.getVersion(), flowId, node.getId(),
          node.getStartTime(), node.getEndTime(), node.getStatus().getNumVal(),
          inputParam, node.getAttempt());
    } catch (final SQLException e) {
      throw new ExecutorManagerException("Error writing job " + node.getId(), e);
    }
  }

  public void updateExecutableNode(final ExecutableNode node) throws ExecutorManagerException {
    final String UPSERT_EXECUTION_NODE = "UPDATE execution_jobs "
        + "SET start_time=?, end_time=?, status=?, output_params=? "
        + "WHERE exec_id=? AND flow_id=? AND job_id=? AND attempt=?";

    byte[] outputParam = null;
    final Props outputProps = node.getOutputProps();
    if (outputProps != null) {
      try {
        final String jsonString =
            JSONUtils.toJSON(PropsUtils.toHierarchicalMap(outputProps));
        logger.debug("the job's outputParam is : " + jsonString);
        outputParam = GZIPUtils.gzipString(jsonString, "UTF-8");
      } catch (final Throwable e) {
        logger.error("to json failed.", e);
      }
    }
    try {
      this.dbOperator.update(UPSERT_EXECUTION_NODE, node.getStartTime(), node
          .getEndTime(), node.getStatus().getNumVal(), outputParam, node
          .getExecutableFlow().getExecutionId(), node.getParentFlow()
          .getFlowPath(), node.getId(), node.getAttempt());
    } catch (final SQLException e) {
      throw new ExecutorManagerException("Error updating job " + node.getId(), e);
    }
  }

  public void updateExecutableNodeStatus(final ExecutableNode node) throws ExecutorManagerException {
    final String UPSERT_EXECUTION_NODE = "UPDATE execution_jobs "
            + "SET status=? "
            + "WHERE exec_id=? AND flow_id=? AND job_id=? AND attempt=?";

    try {
      this.dbOperator.update(UPSERT_EXECUTION_NODE, node.getStatus().getNumVal(), node
              .getExecutableFlow().getExecutionId(), node.getParentFlow()
              .getFlowPath(), node.getId(), node.getAttempt());
    } catch (final SQLException e) {
      throw new ExecutorManagerException("Error updating job " + node.getId(), e);
    }
  }

  public List<ExecutableJobInfo> fetchJobInfoAttempts(final int execId, final String jobId)
      throws ExecutorManagerException {
    try {
      final List<ExecutableJobInfo> info = this.dbOperator.query(
          FetchExecutableJobHandler.FETCH_EXECUTABLE_NODE_ATTEMPTS,
          new FetchExecutableJobHandler(), execId, jobId);
      if (info == null || info.isEmpty()) {
        return null;
      } else {
        return info;
      }
    } catch (final SQLException e) {
      throw new ExecutorManagerException("Error querying job info " + jobId, e);
    }
  }

  public ExecutableJobInfo fetchJobInfo(final int execId, final String jobId, final int attempts)
      throws ExecutorManagerException {
    try {
      final List<ExecutableJobInfo> info =
          this.dbOperator.query(FetchExecutableJobHandler.FETCH_EXECUTABLE_NODE,
              new FetchExecutableJobHandler(), execId, jobId, attempts);
      if (info == null || info.isEmpty()) {
        return null;
      } else {
        return info.get(0);
      }
    } catch (final SQLException e) {
      throw new ExecutorManagerException("Error querying job info " + jobId, e);
    }
  }

  public Props fetchExecutionJobInputProps(final int execId, final String jobId)
      throws ExecutorManagerException {
    try {
      final Pair<Props, Props> props = this.dbOperator.query(
          FetchExecutableJobPropsHandler.FETCH_INPUT_PARAM_EXECUTABLE_NODE,
          new FetchExecutableJobPropsHandler(), execId, jobId);
      return props.getFirst();
    } catch (final SQLException e) {
      throw new ExecutorManagerException("Error querying job params " + execId
          + " " + jobId, e);
    }
  }

  public Props fetchExecutionJobOutputProps(final int execId, final String jobId)
      throws ExecutorManagerException {
    try {
      final Pair<Props, Props> props = this.dbOperator.query(
          FetchExecutableJobPropsHandler.FETCH_OUTPUT_PARAM_EXECUTABLE_NODE,
          new FetchExecutableJobPropsHandler(), execId, jobId);
      return props.getFirst();
    } catch (final SQLException e) {
      throw new ExecutorManagerException("Error querying job params " + execId
          + " " + jobId, e);
    }
  }

  public Pair<Props, Props> fetchExecutionJobProps(final int execId, final String jobId)
      throws ExecutorManagerException {
    try {
      return this.dbOperator.query(
          FetchExecutableJobPropsHandler.FETCH_INPUT_OUTPUT_PARAM_EXECUTABLE_NODE,
          new FetchExecutableJobPropsHandler(), execId, jobId);
    } catch (final SQLException e) {
      throw new ExecutorManagerException("Error querying job params " + execId
          + " " + jobId, e);
    }
  }

  public List<ExecutableJobInfo> fetchJobHistory(final int projectId,
      final String jobId,
      final int skip,
      final int size) throws ExecutorManagerException {
    try {
      final List<ExecutableJobInfo> info =
          this.dbOperator.query(FetchExecutableJobHandler.FETCH_PROJECT_EXECUTABLE_NODE_ALL,
              new FetchExecutableJobHandler(), projectId, jobId, skip, size);
      if (info == null || info.isEmpty()) {
        return null;
      } else {
        return info;
      }
    } catch (final SQLException e) {
      throw new ExecutorManagerException("Error querying job info " + jobId, e);
    }
  }

  public List<ExecutableJobInfo> fetchDiagnosisJob(long endTime) throws ExecutorManagerException {
    try {
      final List<ExecutableJobInfo> info =
          this.dbOperator.query(FetchExecutableJobHandler.FETCH_DIAGNOSIS_NODE,
              new FetchExecutableJobHandler(), endTime);
      if (info == null || info.isEmpty()) {
        return null;
      } else {
        return info;
      }
    } catch (final SQLException e) {
      throw new ExecutorManagerException("Error querying job info ", e);
    }
  }

  public List<ExecutableJobInfo> fetchExecutableJobInfo(final long startTime)
          throws ExecutorManagerException {
    try {
      final List<ExecutableJobInfo> info =
              this.dbOperator.query(FetchExecutableJobInfoHandler.FETCH_EXECUTABLE_NODE_INFO,
                      new FetchExecutableJobInfoHandler(), startTime);
      if (info == null || info.isEmpty()) {
        return null;
      } else {
        return info;
      }
    } catch (final SQLException e) {
      throw new ExecutorManagerException("Error querying job info ", e);
    }
  }

  public List<ExecutableJobInfo> fetchQuickSearchJobExecutions(final int projectId,
     final String jobId,
     final String searchTerm,
     final int skip,
     final int size) throws ExecutorManagerException {
    try {
      final List<ExecutableJobInfo> info =
              this.dbOperator.query(FetchExecutableJobHandler.FETCH_QUICK_SEARCH_JOB_EXECUTIONS,
                      new FetchExecutableJobHandler(), projectId, jobId, "%" + searchTerm + "%" , skip, size);
      if (info == null || info.isEmpty()) {
        return null;
      } else {
        return info;
      }
    } catch (final SQLException e) {
      throw new ExecutorManagerException("Error querying job info " + jobId, e);
    }
  }

  public List<ExecutableJobInfo> searchJobExecutions(HistoryQueryParam historyQueryParam,
     final int skip,
     final int size) throws ExecutorManagerException {

    StringBuilder querySql = new StringBuilder(
            "SELECT ej.exec_id, ej.project_id, ej.version, ej.flow_id, ej.job_id, "
                    + "ej.start_time, ej.end_time, ej.status, ej.attempt, ef.flow_type FROM execution_jobs ej "
                    + "inner join execution_flows ef on ej.exec_id = ef.exec_id ");
    final List<Object> params = new ArrayList<>();

    wrapMultiConditionSql(true, historyQueryParam, querySql, skip, size, params);

    try {
      return this.dbOperator.query(querySql.toString(), new FetchExecutableJobHandler(), params.toArray());
    } catch (final SQLException e) {
      throw new ExecutorManagerException("Error search Job Executions", e);
    }
  }

  public List<ExecutableJobInfo> fetchJobAllHistory(final int projectId, final String jobId)
      throws ExecutorManagerException {
    try {
      final List<ExecutableJobInfo> info =
          this.dbOperator.query(FetchExecutableJobHandler.FETCH_PROJECT_EXECUTABLE_NODE_ALL,
              new FetchExecutableJobHandler(), projectId, jobId);
      if (info == null || info.isEmpty()) {
        return null;
      } else {
        return info;
      }
    } catch (final SQLException e) {
      throw new ExecutorManagerException("Error querying job info " + jobId, e);
    }
  }

  public List<Object> fetchAttachments(final int execId, final String jobId, final int attempt)
      throws ExecutorManagerException {
    try {
      final String attachments = this.dbOperator.query(
          FetchExecutableJobAttachmentsHandler.FETCH_ATTACHMENTS_EXECUTABLE_NODE,
          new FetchExecutableJobAttachmentsHandler(), execId, jobId);
      if (attachments == null) {
        return null;
      } else {
        return (List<Object>) JSONUtils.parseJSONFromString(attachments);
      }
    } catch (final IOException e) {
      throw new ExecutorManagerException(
          "Error converting job attachments to JSON " + jobId, e);
    } catch (final SQLException e) {
      throw new ExecutorManagerException(
          "Error query job attachments " + jobId, e);
    }
  }

  public void uploadAttachmentFile(final ExecutableNode node, final File file)
      throws ExecutorManagerException {
    final String UPDATE_EXECUTION_NODE_ATTACHMENTS =
        "UPDATE execution_jobs " + "SET attachments=? "
            + "WHERE exec_id=? AND flow_id=? AND job_id=? AND attempt=?";
    try {
      final String jsonString = FileUtils.readFileToString(file);
      final byte[] attachments = GZIPUtils.gzipString(jsonString, "UTF-8");
      this.dbOperator.update(UPDATE_EXECUTION_NODE_ATTACHMENTS, attachments,
          node.getExecutableFlow().getExecutionId(), node.getParentFlow()
              .getNestedId(), node.getId(), node.getAttempt());
    } catch (final IOException | SQLException e) {
      throw new ExecutorManagerException("Error uploading attachments.", e);
    }
  }


  public JobPredictionExecutionInfo fetchJobPredictionExecutionInfo(final int projectId, final String flowId, final String jobId)
          throws ExecutorManagerException {
    try {
      final List<JobPredictionExecutionInfo> info =
              this.dbOperator.query(FetchJobPredictionExecutionInfo.FETCH_SINGLE_PREDICTION_EXECUTION_INFO,
                      new FetchJobPredictionExecutionInfo(), projectId, flowId, jobId);
      if (info == null || info.isEmpty()) {
        return null;
      } else {
        return info.get(0);
      }
    } catch (final SQLException e) {
      throw new ExecutorManagerException("Error querying job prediction info " + jobId, e);
    }
  }


  public List<JobPredictionExecutionInfo> fetchJobPredictionExecutionInfoList(final int projectId, final String flowId)
          throws ExecutorManagerException {
    try {
      final List<JobPredictionExecutionInfo> info =
              this.dbOperator.query(FetchJobPredictionExecutionInfo.FETCH_PREDICTION_EXECUTION_INFO_LIST,
                      new FetchJobPredictionExecutionInfo(), projectId, flowId);
      return info;
    } catch (final SQLException e) {
      throw new ExecutorManagerException("Error querying job prediction info project info:" + flowId, e);
    }
  }

    private static class FetchExecutableJobHandler implements
      ResultSetHandler<List<ExecutableJobInfo>> {

      private static final String FETCH_DIAGNOSIS_NODE =
          "SELECT exec_id, project_id, version, flow_id, job_id, "
              + "start_time, end_time, status, attempt, 0 "
              + "FROM execution_jobs WHERE end_time > ? AND status = 70 ";
    private static final String FETCH_EXECUTABLE_NODE =
        "SELECT exec_id, project_id, version, flow_id, job_id, "
            + "start_time, end_time, status, attempt, 0 "
            + "FROM execution_jobs WHERE exec_id=? "
            + "AND job_id=? AND attempt=?";
    private static final String FETCH_EXECUTABLE_NODE_ATTEMPTS =
        "SELECT exec_id, project_id, version, flow_id, job_id, "
            + "start_time, end_time, status, attempt, 0 FROM execution_jobs "
            + "WHERE exec_id=? AND job_id=?";
    private static final String FETCH_PROJECT_EXECUTABLE_NODE =
        "SELECT exec_id, project_id, version, flow_id, job_id, "
             + "start_time, end_time, status, attempt, 0 FROM execution_jobs "
             + "WHERE project_id=? AND job_id=? "
             + "ORDER BY exec_id DESC LIMIT ?, ? ";

    private static final String FETCH_PROJECT_EXECUTABLE_NODE_ALL =
        "SELECT ej.exec_id, ej.project_id, ej.version, ej.flow_id, ej.job_id, "
            + "ej.start_time, ej.end_time, ej.status, ej.attempt, ef.flow_type FROM execution_jobs ej "
            + "inner join execution_flows ef on ej.exec_id = ef.exec_id "
            + "WHERE ej.project_id=? AND ej.job_id=? "
            + "ORDER BY exec_id DESC LIMIT ?, ? ";

    private static final String FETCH_QUICK_SEARCH_JOB_EXECUTIONS =
        "SELECT ej.exec_id, ej.project_id, ej.version, ej.flow_id, ej.job_id, "
                + "ej.start_time, ej.end_time, ej.status, ej.attempt, ef.flow_type FROM execution_jobs ej "
                + "inner join execution_flows ef on ej.exec_id = ef.exec_id "
                + "WHERE ej.project_id=? AND ej.job_id=? AND ej.exec_id LIKE ? "
                + "ORDER BY exec_id DESC LIMIT ?, ? ";

    @Override
    public List<ExecutableJobInfo> handle(final ResultSet rs) throws SQLException {
      if (!rs.next()) {
        return Collections.<ExecutableJobInfo>emptyList();
      }

      final List<ExecutableJobInfo> execNodes = new ArrayList<>();
      do {
        final int execId = rs.getInt(1);
        final int projectId = rs.getInt(2);
        final int version = rs.getInt(3);
        final String flowId = rs.getString(4);
        final String jobId = rs.getString(5);
        final long startTime = rs.getLong(6);
        final long endTime = rs.getLong(7);
        final Status status = Status.fromInteger(rs.getInt(8));
        final int attempt = rs.getInt(9);
        final int flowType = rs.getInt(10);

        final ExecutableJobInfo info =
            new ExecutableJobInfo(execId, projectId, version, flowId, jobId,
                startTime, endTime, status, attempt, flowType);
        execNodes.add(info);
      } while (rs.next());

      return execNodes;
    }
  }

  private static class FetchExecutableJobInfoHandler implements
          ResultSetHandler<List<ExecutableJobInfo>> {

    private static final String FETCH_EXECUTABLE_NODE_INFO =
            "SELECT   ej.exec_id, ej.flow_id, ej.job_id, ej.status, ef.flow_type, " +
                    " ef.submit_department AS submit_department_id " +
                    " FROM  execution_jobs ej " +
                    " INNER JOIN execution_flows ef ON ej.exec_id = ef.exec_id " +
                    " WHERE ej.start_time > ?";

    @Override
    public List<ExecutableJobInfo> handle(final ResultSet rs) throws SQLException {
      if (!rs.next()) {
        return Collections.<ExecutableJobInfo>emptyList();
      }

      final List<ExecutableJobInfo> execNodes = new ArrayList<>();
      do {
        final ExecutableJobInfo info = new ExecutableJobInfo();
        info.setExecId(rs.getInt(1));
        info.setFlowId(rs.getString(2));
        info.setJobId(rs.getString(3));
        info.setStatus(Status.fromInteger(rs.getInt(4)));
        info.setFlowType(rs.getInt(5));
        info.setSubmitDepartmentId(rs.getString(6));
        execNodes.add(info);
      } while (rs.next());

      return execNodes;
    }
  }

  private static class FetchExecutableJobPropsHandler implements
      ResultSetHandler<Pair<Props, Props>> {

    private static final String FETCH_OUTPUT_PARAM_EXECUTABLE_NODE =
        "SELECT output_params FROM execution_jobs WHERE exec_id=? AND job_id=?";
    private static final String FETCH_INPUT_PARAM_EXECUTABLE_NODE =
        "SELECT input_params FROM execution_jobs WHERE exec_id=? AND job_id=?";
    private static final String FETCH_INPUT_OUTPUT_PARAM_EXECUTABLE_NODE =
        "SELECT input_params, output_params "
            + "FROM execution_jobs WHERE exec_id=? AND job_id=?";

    @Override
    public Pair<Props, Props> handle(final ResultSet rs) throws SQLException {
      if (!rs.next()) {
        return new Pair<>(null, null);
      }

      if (rs.getMetaData().getColumnCount() > 1) {
        final byte[] input = rs.getBytes(1);
        final byte[] output = rs.getBytes(2);

        Props inputProps = null;
        Props outputProps = null;
        try {
          if (input != null) {
            final String jsonInputString = GZIPUtils.unGzipString(input, "UTF-8");
            inputProps =
                PropsUtils.fromHierarchicalMap((Map<String, Object>) JSONUtils
                    .parseJSONFromString(jsonInputString));

          }
          if (output != null) {
            final String jsonOutputString = GZIPUtils.unGzipString(output, "UTF-8");
            outputProps =
                PropsUtils.fromHierarchicalMap((Map<String, Object>) JSONUtils
                    .parseJSONFromString(jsonOutputString));
          }
        } catch (final IOException e) {
          throw new SQLException("Error decoding param data", e);
        }

        return new Pair<>(inputProps, outputProps);
      } else {
        final byte[] params = rs.getBytes(1);
        Props props = null;
        try {
          if (params != null) {
            final String jsonProps = GZIPUtils.unGzipString(params, "UTF-8");

            props =
                PropsUtils.fromHierarchicalMap((Map<String, Object>) JSONUtils
                    .parseJSONFromString(jsonProps));
          }
        } catch (final IOException e) {
          throw new SQLException("Error decoding param data", e);
        }

        return new Pair<>(props, null);
      }
    }
  }

  private static class FetchExecutableJobAttachmentsHandler implements
      ResultSetHandler<String> {

    private static final String FETCH_ATTACHMENTS_EXECUTABLE_NODE =
        "SELECT attachments FROM execution_jobs WHERE exec_id=? AND job_id=?";

    @Override
    public String handle(final ResultSet rs) throws SQLException {
      String attachmentsJson = null;
      if (rs.next()) {
        try {
          final byte[] attachments = rs.getBytes(1);
          if (attachments != null) {
            attachmentsJson = GZIPUtils.unGzipString(attachments, "UTF-8");
          }
        } catch (final IOException e) {
          throw new SQLException("Error decoding job attachments", e);
        }
      }
      return attachmentsJson;
    }
  }

  public String getEventType(final String topic, final String msgName) {
    try {
      return this.dbOperator
          .query(FetchEventTypeHandler.FETCH_EVENT_TYPE, new FetchEventTypeHandler(), topic,
              msgName);
    } catch (final Exception e) {
      logger.error("get event type error", e);
      return null;
    }
  }

  private static class FetchEventTypeHandler implements
      ResultSetHandler<String> {

    private static final String FETCH_EVENT_TYPE =
        "SELECT source_type FROM event_queue WHERE topic=? AND msg_name=? order by send_time desc limit 1";

    @Override
    public String handle(final ResultSet rs) throws SQLException {
      if (rs.next()) {
        String sourceType = rs.getString(1);
        if (StringUtils.isEmpty(sourceType)) {
          return "eventchecker";
        } else {
          return "rmb";
        }
      } else {
        return null;
      }
    }
  }

  public void linkJobHook(String jobCode, String prefixRules, String suffixRules, String username)
      throws SQLException {

    this.dbOperator.update(
        JobHookResultHandler.LINK_JOB_HOOKS_SQL,
        jobCode,
        prefixRules,
        suffixRules,
        username,
        System.currentTimeMillis(),
        username,
        System.currentTimeMillis());
  }

  public JobHook getJobHook(String jobCode) {
    final JdbcJobHookHandlerSet.JobHookResultHandler jobHookHandler = new JdbcJobHookHandlerSet.JobHookResultHandler();
    try {
      List<JobHook> resultList = this.dbOperator
          .query(JobHookResultHandler.SELECT_JOB_HOOKS, jobHookHandler,
              jobCode);
      if (CollectionUtils.isNotEmpty(resultList)) {
        return resultList.get(0);
      }
      return null;
    } catch (final SQLException e) {
      logger.error("Error getJobHook, jobCode:" + jobCode, e);
      throw new ProjectManagerException("Error getJobHook, jobCode:" + jobCode, e);
    }
  }

  private void wrapMultiConditionSql(boolean first, HistoryQueryParam param,
                                     StringBuilder jobExecutionsTotalSql, int skip, int num, List<Object> params) {
    if (param == null) {
      return;
    }

    String action = "advfilter".equals(param.getSearchType()) ? "like" : "=";

    if (param.getProjectId() !=0 ) {
      first = wrapperSqlParam(first, String.valueOf(param.getProjectId()), jobExecutionsTotalSql, "ej.project_id", "=",
              params);
    }

    if (StringUtils.isNotEmpty(param.getJobId())) {
      first = wrapperSqlParam(first, param.getJobId(), jobExecutionsTotalSql, "ej.job_id", "=", params);
    }

    if (StringUtils.isNotEmpty(param.getExecIdContain())) {
      first = wrapperSqlParam(first, param.getExecIdContain(), jobExecutionsTotalSql, "ej.exec_id", action,
              params);
    }

    String[] statusArray = param.getStatus().split(",");
    if (!("0".equals(statusArray[0]))) {
      first = wrapperMultipleStatusSql(first, statusArray, jobExecutionsTotalSql, "ej.status", "in");
    }

    if (param.getStartBeginTime() > 0) {
      first = wrapperSqlParam(first, "" + param.getStartBeginTime(), jobExecutionsTotalSql, "ej.start_time",
              ">", params);
    }
    if (param.getStartEndTime() > 0) {
      first = wrapperSqlParam(first, "" + param.getStartEndTime(), jobExecutionsTotalSql, "ej.start_time",
              "<", params);
    }
    if (param.getFinishBeginTime() > 0) {
      first = wrapperSqlParam(first, "" + param.getFinishBeginTime(), jobExecutionsTotalSql, "ej.end_time",
              ">", params);
    }
    if (param.getFinishEndTime() > 0) {
      first = wrapperSqlParam(first, "" + -1, jobExecutionsTotalSql, "ej.end_time",
              "!=", params);
      first = wrapperSqlParam(first, "" + param.getFinishEndTime(), jobExecutionsTotalSql, "ej.end_time",
              "<", params);
    }

    if (StringUtils.isNotEmpty(param.getRunDateReq())) {
      first = wrapperSqlParam(first, param.getRunDateReq(), jobExecutionsTotalSql, "ef.run_date", "=",
              params);
    }

    if (StringUtils.isNotEmpty(param.getUserContain())) {
      first = wrapperSqlParam(first, param.getUserContain(), jobExecutionsTotalSql, "ef.submit_user", action,
              params);
    }

    if (param.getFlowType() != -1) {
      wrapperSqlParam(first, "" + param.getFlowType(), jobExecutionsTotalSql, "ef.flow_type", "=", params);
    }

    if (skip > -1 && num > -1) {
      jobExecutionsTotalSql.append(" ORDER BY exec_id DESC LIMIT ?, ?");
      params.add(skip);
      params.add(num);
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


}
