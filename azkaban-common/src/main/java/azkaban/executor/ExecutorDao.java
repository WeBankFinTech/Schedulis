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
import java.util.Collections;
import java.util.List;
import javax.inject.Inject;
import javax.inject.Singleton;

import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.dbutils.ResultSetHandler;
import org.slf4j.LoggerFactory;
import org.slf4j.Logger;

@Singleton
public class ExecutorDao {

  private static final Logger logger = LoggerFactory.getLogger(ExecutorDao.class);
  private final DatabaseOperator dbOperator;

  @Inject
  public ExecutorDao(final DatabaseOperator dbOperator) {
    this.dbOperator = dbOperator;
  }

  List<Executor> fetchAllExecutors() throws ExecutorManagerException {
    try {
      return this.dbOperator
              .query(FetchExecutorHandler.FETCH_ALL_EXECUTORS, new FetchExecutorHandler());
    } catch (final Exception e) {
      throw new ExecutorManagerException("Error fetching executors", e);
    }
  }

  List<Executor> fetchActiveExecutors() throws ExecutorManagerException {
    try {
      return this.dbOperator
              .query(FetchExecutorHandler.FETCH_ACTIVE_EXECUTORS, new FetchExecutorHandler());
    } catch (final SQLException e) {
      throw new ExecutorManagerException("Error fetching active executors", e);
    }
  }

  public Executor fetchExecutor(final String host, final int port)
          throws ExecutorManagerException {
    try {
      final List<Executor> executors =
              this.dbOperator.query(FetchExecutorHandler.FETCH_EXECUTOR_BY_HOST_PORT,
                      new FetchExecutorHandler(), host, port);
      if (executors.isEmpty()) {
        return null;
      } else {
        return executors.get(0);
      }
    } catch (final SQLException e) {
      throw new ExecutorManagerException(String.format(
              "Error fetching executor %s:%d", host, port), e);
    }
  }

  public Executor fetchExecutor(final int executorId) throws ExecutorManagerException {
    try {
      final List<Executor> executors = this.dbOperator
              .query(FetchExecutorHandler.FETCH_EXECUTOR_BY_ID,
                      new FetchExecutorHandler(), executorId);
      if (executors.isEmpty()) {
        return null;
      } else {
        return executors.get(0);
      }
    } catch (final Exception e) {
      throw new ExecutorManagerException(String.format(
              "Error fetching executor with id: %d", executorId), e);
    }
  }

  Executor fetchExecutorByExecutionId(final int executionId)
          throws ExecutorManagerException {
    final FetchExecutorHandler executorHandler = new FetchExecutorHandler();
    try {
      final List<Executor> executors = this.dbOperator
              .query(FetchExecutorHandler.FETCH_EXECUTION_EXECUTOR,
                      executorHandler, executionId);
      if (executors.size() > 0) {
        return executors.get(0);
      } else {
        return null;
      }
    } catch (final SQLException e) {
      throw new ExecutorManagerException(
              "Error fetching executor for exec_id : " + executionId, e);
    }
  }

  Executor addExecutor(final String host, final int port)
          throws ExecutorManagerException {
    // verify, if executor already exists
    if (fetchExecutor(host, port) != null) {
      throw new ExecutorManagerException(String.format(
              "Executor %s:%d already exist", host, port));
    }
    // add new executor
    addExecutorHelper(host, port);

    // fetch newly added executor
    return fetchExecutor(host, port);
  }

  private void addExecutorHelper(final String host, final int port)
          throws ExecutorManagerException {
    final String INSERT = "INSERT INTO executors (host, port) values (?,?)";
    try {
      this.dbOperator.update(INSERT, host, port);
    } catch (final SQLException e) {
      throw new ExecutorManagerException(String.format("Error adding %s:%d ", host, port), e);
    }
  }

  public void updateExecutor(final Executor executor) throws ExecutorManagerException {
    final String UPDATE =
            "UPDATE executors SET host=?, port=?, active=? where id=?";

    try {
      final int rows = this.dbOperator.update(UPDATE, executor.getHost(), executor.getPort(),
              executor.isActive(), executor.getId());
      if (rows == 0) {
        throw new ExecutorManagerException("No executor with id :" + executor.getId());
      }
    } catch (final SQLException e) {
      throw new ExecutorManagerException("Error inactivating executor "
              + executor.getId(), e);
    }
  }

  void removeExecutor(final String host, final int port) throws ExecutorManagerException {
    final String DELETE = "DELETE FROM executors WHERE host=? AND port=?";
    try {
      final int rows = this.dbOperator.update(DELETE, host, port);
      if (rows == 0) {
        throw new ExecutorManagerException("No executor with host, port :"
                + "(" + host + "," + port + ")");
      }
    } catch (final SQLException e) {
      throw new ExecutorManagerException("Error removing executor with host, port : "
              + "(" + host + "," + port + ")", e);
    }
  }

  /**
   * JDBC ResultSetHandler to fetch records from executors table
   */
  public static class FetchExecutorHandler implements
          ResultSetHandler<List<Executor>> {

    static String FETCH_ALL_EXECUTORS =
            "SELECT id, host, port, active, last_department_group FROM executors";
    static String FETCH_ACTIVE_EXECUTORS =
            "SELECT id, host, port, active, last_department_group FROM executors where active=true";
    static String FETCH_EXECUTOR_BY_ID =
            "SELECT id, host, port, active, last_department_group FROM executors where id=?";
    static String FETCH_EXECUTOR_BY_HOST_PORT =
            "SELECT id, host, port, active, last_department_group FROM executors where host=? AND port=?";
    static String FETCH_EXECUTION_EXECUTOR =
            "SELECT ex.id, ex.host, ex.port, ex.active, ex.last_department_group FROM "
                    + " executors ex INNER JOIN execution_flows ef "
                    + "on ex.id = ef.executor_id  where exec_id=?";

    @Override
    public List<Executor> handle(final ResultSet rs) throws SQLException {
      if (!rs.next()) {
        return Collections.emptyList();
      }

      final List<Executor> executors = new ArrayList<>();
      do {
        final int id = rs.getInt(1);
        final String host = rs.getString(2);
        final int port = rs.getInt(3);
        final boolean active = rs.getBoolean(4);
        final String department = rs.getString(5);
        final Executor executor = new Executor(id, host, port, active);
        executor.setLastDepartment(department);
        executors.add(executor);
      } while (rs.next());

      return executors;
    }
  }

  /**
   * 插入固定ID的executors信息
   * @param id
   * @param host
   * @param port
   * @return
   * @throws ExecutorManagerException
   */
  Executor addExecutorFixed(final int id, final String host, final int port)
          throws ExecutorManagerException {
    // verify, if executor already exists
    if (fetchExecutor(host, port) != null) {
      throw new ExecutorManagerException(String.format(
              "Executor %s:%d already exist", host, port));
    }
    // add new executor
    addExecutorHelper(id, host, port);

    // fetch newly added executor
    return fetchExecutor(host, port);
  }

  private void addExecutorHelper(final int id, final String host, final int port)
          throws ExecutorManagerException {
    final String INSERT = "INSERT INTO executors (id, host, port) values (?, ?, ?)";
    try {
      this.dbOperator.update(INSERT, id, host, port);
    } catch (final SQLException e) {
      throw new ExecutorManagerException(String.format("Error adding %s:%s:%d ", id, host, port), e);
    }
  }

  Hosts getHostConfigByHostname(String hostname)
          throws ExecutorManagerException{
    FetchHostHandler fetchHostHandler = new FetchHostHandler();
    final String SQL = "SELECT executorid,hostname,creator,createtime,updater FROM wtss_hosts WHERE hostname=? LIMIT 1";
    try {
      List<Hosts> query = this.dbOperator.query(SQL, fetchHostHandler, hostname);
      if (CollectionUtils.isEmpty(query)) {
        return null;
      }
      return query.get(0);
    } catch (final SQLException e) {
      throw new ExecutorManagerException("Error in query wtss_hosts", e);
    }
  }

  public static class FetchHostHandler implements
          ResultSetHandler<List<Hosts>> {

    @Override
    public List<Hosts> handle(ResultSet rs) throws SQLException {
      if (!rs.next()) {
        return Collections.emptyList();
      }

      final List<Hosts> hosts = new ArrayList<>();
      do {
        final String hostname = rs.getString(2);
        final int executorid = rs.getInt(1);
        final String creator = rs.getString(3);
        final Long createtime = rs.getLong(4);
        final String updater = rs.getString(5);

        final Hosts host = new Hosts(hostname,executorid,creator,createtime,updater);
        hosts.add(host);
      } while (rs.next());

      return hosts;
    }
  }

  int insertHostsConfig(Hosts hosts) throws ExecutorManagerException {
    FetchHostHandler fetchHostHandler = new FetchHostHandler();
    final String insertsql = "INSERT INTO wtss_hosts (hostname,executorid,creator,createtime) " +
            "SELECT ?, CASE WHEN MAX(executorid) is null then 1 ELSE MAX(executorid)+1 END,?,? FROM wtss_hosts";
    final String getexecuteidsql = "SELECT executorid FROM wtss_hosts WHERE hostname=? LIMIT 1";
    try{
      int res = this.dbOperator.update(insertsql,hosts.getHostname(),hosts.getCreator(),hosts.getCreatetime());
      if (res <= 0) {
        throw new ExecutorManagerException(String.format("Error in insert wtss_hosts, Insert SQL is:%s,hostname=%s,creator=%s,createtime=%s", insertsql, hosts.getHostname(), hosts.getCreator(), hosts.getCreatetime()));
      }
      List<Hosts> list = this.dbOperator.query(getexecuteidsql, fetchHostHandler, hosts.getHostname());
      if (CollectionUtils.isEmpty(list)) {
        throw new ExecutorManagerException(String.format("Error in getexecuteidsql, SQL is: %s, hostname=%s", getexecuteidsql, hosts.getHostname()));
      }
      return list.get(0).getExecutorid();
    }catch (SQLException e) {
      throw new ExecutorManagerException("Error in insertHostsConfig:", e);
    }

  }

}
