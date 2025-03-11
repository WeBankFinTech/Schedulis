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
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import javax.inject.Inject;
import javax.inject.Singleton;

import azkaban.history.ExecutionRecoverDao;
import org.apache.commons.dbutils.ResultSetHandler;
import org.apache.commons.lang.StringUtils;
import org.slf4j.LoggerFactory;
import org.slf4j.Logger;

@Singleton
public class NumExecutionsDao {

  private static final Logger logger = LoggerFactory.getLogger(NumExecutionsDao.class);
  private final DatabaseOperator dbOperator;

  @Inject
  public NumExecutionsDao(final DatabaseOperator dbOperator) {
    this.dbOperator = dbOperator;
  }

  public int fetchNumExecutableFlows() throws ExecutorManagerException {
    try {
      return this.dbOperator.query(IntHandler.NUM_EXECUTIONS, new IntHandler());
    } catch (final SQLException e) {
      throw new ExecutorManagerException("Error fetching num executions", e);
    }
  }

  public int fetchQuickSearchNumExecutableFlows(final int projectId,final String flowId, final String searchTerm)
          throws ExecutorManagerException {
    final IntHandler intHandler = new IntHandler();
    try {
      final List<Object> params = new ArrayList<>();
      params.add(projectId);
      params.add(flowId);
      params.add("%" + searchTerm + "%");
      params.add("%" + searchTerm + "%");
      return this.dbOperator.query(IntHandler.NUM_QUICKSEARCH_FLOW_EXECUTIONS, intHandler, params.toArray());
    } catch (final SQLException e) {
      throw new ExecutorManagerException("Error fetchQuickSearchNumExecutableFlows", e);
    }
  }

  public int fetchUserQuickSearchNumExecutableFlows(final int projectId, final String flowId, final String searchTerm, final String userId) throws ExecutorManagerException {
    final IntHandler intHandler = new IntHandler();
    try {
      return this.dbOperator.query(IntHandler.NUM_USER_QUICK_SEARCH_FLOW_EXECUTIONS_BY_PROJECT_AND_FLOW
              , intHandler, projectId, flowId, userId, "%" + searchTerm + "%");
    } catch (final SQLException e) {
      throw new ExecutorManagerException("Error fetchUserQuickSearchNumExecutableFlows", e);
    }
  }

  public int fetchNumExecutableFlows(final int projectId, final String flowId)
      throws ExecutorManagerException {
    final IntHandler intHandler = new IntHandler();
    try {
      return this.dbOperator.query(IntHandler.NUM_FLOW_EXECUTIONS, intHandler, projectId, flowId);
    } catch (final SQLException e) {
      throw new ExecutorManagerException("Error fetching num executions", e);
    }
  }

  public int fetchNumExecutableNodes(final int projectId, final String jobId)
      throws ExecutorManagerException {
    final IntHandler intHandler = new IntHandler();
    try {
      return this.dbOperator.query(IntHandler.NUM_JOB_EXECUTIONS, intHandler, projectId, jobId);
    } catch (final SQLException e) {
      throw new ExecutorManagerException("Error fetching num executions", e);
    }
  }

  public int fetchQuickSearchNumJobExecutions(final int projectId, final String jobId, String searchTerm)
          throws ExecutorManagerException {
    final IntHandler intHandler = new IntHandler();
    try {
      return this.dbOperator.query(IntHandler.NUM_QUICK_SEARCH_JOB_EXECUTIONS, intHandler, projectId, jobId, "%" + searchTerm + "%");
    } catch (final SQLException e) {
      throw new ExecutorManagerException("Error fetching num executions", e);
    }
  }

  public int searchNumberOfJobExecutions(HistoryQueryParam historyQueryParam)
          throws ExecutorManagerException {
    final IntHandler intHandler = new IntHandler();

    final List<Object> params = new ArrayList<>();

    StringBuilder jobExecutionsTotalSql = new StringBuilder(
            "SELECT COUNT(1) FROM execution_jobs ej INNER JOIN execution_flows ef on ej.exec_id = ef.exec_id ");

    boolean first = true;
    //按照过滤条件搜索任务数据条数
    wrapMultiConditionSql(first, historyQueryParam, jobExecutionsTotalSql, params);

    try {
      return this.dbOperator.query(jobExecutionsTotalSql.toString(), intHandler, params.toArray());
    } catch (final SQLException e) {
      throw new ExecutorManagerException("Error search number of job executions", e);
    }
  }

  private static class IntHandler implements ResultSetHandler<Integer> {

    private static final String NUM_EXECUTIONS =
        "SELECT COUNT(1) FROM execution_flows";
    private static final String NUM_FLOW_EXECUTIONS =
        "SELECT COUNT(1) FROM execution_flows WHERE project_id=? AND flow_id=?";
    private static final String NUM_JOB_EXECUTIONS =
        "SELECT COUNT(1) FROM execution_jobs WHERE project_id=? AND job_id=?";
    private static final String NUM_QUICK_SEARCH_JOB_EXECUTIONS =
        "SELECT COUNT(1) FROM execution_jobs WHERE project_id=? AND job_id=? AND exec_id LIKE ?";
    private static final String NUM__USER_FLOW_EXECUTIONS_BY_PROJECT_AND_FLOW =
        "SELECT COUNT(1) FROM execution_flows WHERE project_id=? AND flow_id=? AND submit_user=?";
    private static final String NUM_QUICKSEARCH_FLOW_EXECUTIONS =
        "SELECT COUNT(1) FROM execution_flows WHERE project_id=? AND flow_id=? AND (exec_id LIKE ? OR submit_user LIKE ?)";
    private static final String NUM_USER_QUICK_SEARCH_FLOW_EXECUTIONS_BY_PROJECT_AND_FLOW =
        "SELECT COUNT(1) FROM execution_flows WHERE project_id=? AND flow_id=? AND submit_user=? AND exec_id LIKE ?";

    @Override
    public Integer handle(final ResultSet rs) throws SQLException {
      if (!rs.next()) {
        return 0;
      }
      return rs.getInt(1);
    }
  }


  public int fetchNumUserExecutableFlowsByProjectIdAndFlowId(final int projectId
      , final String flowId, final String userName)
      throws ExecutorManagerException {
    final IntHandler intHandler = new IntHandler();
    try {
      return this.dbOperator.query(IntHandler.NUM__USER_FLOW_EXECUTIONS_BY_PROJECT_AND_FLOW
          , intHandler, projectId, flowId, userName);
    } catch (final SQLException e) {
      throw new ExecutorManagerException("Error fetching num executions", e);
    }
  }

  private void wrapMultiConditionSql(boolean first, HistoryQueryParam param,
    StringBuilder jobExecutionsTotalSql, List<Object> params) {
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
