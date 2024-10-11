package azkaban.executor;

import azkaban.db.DatabaseOperator;
import azkaban.db.EncodingType;
import azkaban.db.SQLTransaction;
import azkaban.utils.GZIPUtils;
import azkaban.utils.JSONUtils;
import org.apache.commons.dbutils.ResultSetHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

import static java.util.stream.Collectors.joining;

@Singleton
public class ExecutionCycleDao {

    private static final Logger logger = LoggerFactory.getLogger(ExecutionCycleDao.class);

    private final DatabaseOperator dbOperator;

    private static final String GET_CYCLE_FLOWS_TOTAL_SQL = "SELECT count(DISTINCT e.id) FROM execution_cycle_flows e " +
        " LEFT JOIN project_permissions p ON e.`project_id` = p.`project_id` WHERE e.status = 30 ";

    private static final String LIST_CYCLE_FLOWS_SQL =
            "SELECT DISTINCT e.id, e.status, e.now_exec_id, e.project_id, e.flow_id, e.submit_user, e.submit_time, e.update_time, e.start_time, e.end_time, e.enc_type, e.data " +
                " FROM execution_cycle_flows e  " +
                " LEFT JOIN project_permissions p ON e.`project_id` = p.`project_id` WHERE e.status = 30 ";

    private static final String GET_CYCLE_FLOWS_BY_MAINTAINER_TOTAL_SQL = "SELECT count(*) FROM execution_cycle_flows WHERE status = 30";

    private static final String LIST_CYCLE_FLOWS_BY_MAINTAINER_SQL =
        "SELECT id, status, now_exec_id, project_id, flow_id, submit_user, submit_time, update_time, start_time, end_time, enc_type, data " +
            "FROM execution_cycle_flows WHERE status = 30";

    private static final String UPLOAD_CYCLE_FLOW_SQL =
            "INSERT INTO execution_cycle_flows (status, now_exec_id, project_id, flow_id, submit_user, submit_time, update_time, " +
                    "start_time, end_time) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)";

    private static final String GET_CYCLE_FLOW_SQL =
            "SELECT id, status, now_exec_id, project_id, flow_id, submit_user, submit_time, update_time, start_time, end_time, enc_type, data " +
            "FROM execution_cycle_flows WHERE project_id = ? AND flow_id = ? ORDER BY start_time DESC limit 1";

    private static final String GET_CYCLE_FLOW_BY_ID_SQL =
            "SELECT id, status, now_exec_id, project_id, flow_id, submit_user, submit_time, update_time, start_time, end_time, enc_type, data " +
            "FROM execution_cycle_flows WHERE id = ? ORDER BY start_time DESC limit 1";

    private static final String UPDATE_CYCLE_FLOW_BY_EXECID_SQL =
            "UPDATE execution_cycle_flows SET now_exec_id = ?, update_time = ? WHERE now_exec_id = ?";

    private static final String UPDATE_CYCLE_FLOW_BY_ID_SQL = "UPDATE execution_cycle_flows SET status = ?, now_exec_id = ?, " +
            "update_time = ?, start_time = ?, end_time = ?, enc_type = ?, data = ? WHERE id = ? ";

    private static final String STOP_ALL_RUNNING_CYCLE_FLOWS = "UPDATE execution_cycle_flows SET status = 60, end_time = ? WHERE status = 30";

    private static final String GET_ALL_RUNNING_CYCLE_FLOWS =
            "SELECT id, status, now_exec_id, project_id, flow_id, submit_user, submit_time, update_time, start_time, end_time, enc_type, data " +
                    "FROM execution_cycle_flows WHERE status = 30";

    @Inject
    public ExecutionCycleDao(final DatabaseOperator dbOperator) {
        this.dbOperator = dbOperator;
    }

    public synchronized int uploadCycleFlow(ExecutionCycle cycleFlow) throws ExecutorManagerException {
        long now = System.currentTimeMillis();
        Object[] params = new Object[] {
                cycleFlow.getStatus().getNumVal(),
                cycleFlow.getCurrentExecId(),
                cycleFlow.getProjectId(),
                cycleFlow.getFlowId(),
                cycleFlow.getSubmitUser(),
                now,
                now,
                cycleFlow.getStartTime(),
                cycleFlow.getEndTime()
        };
        SQLTransaction<Integer> insertAndGetLastId = transOperator -> {
            transOperator.update(UPLOAD_CYCLE_FLOW_SQL, params);
            transOperator.getConnection().commit();
            return (int)transOperator.getLastInsertId();
        };
        try {
            int id = dbOperator.transaction(insertAndGetLastId);
            cycleFlow.setId(id);
            updateCycleFlow(cycleFlow);
            return id;
        } catch (SQLException e) {
            logger.error("upload cycle flow failed, flowID: " + cycleFlow.getFlowId(), e);
            throw new ExecutorManagerException("upload cycle flow failed, flowID: " + cycleFlow.getFlowId(), e);
        }
    }

    public synchronized int updateCycleFlow(int execId, int execIdNew) throws ExecutorManagerException {
        long now = System.currentTimeMillis();
        try {
            return dbOperator.update(UPDATE_CYCLE_FLOW_BY_EXECID_SQL, execIdNew, now, execId);
        } catch (SQLException e) {
            logger.error("update cycle flow failed, execId: " + execId, e);
            throw new ExecutorManagerException("update cycle flow failed, execId: " + execId, e);
        }
    }

    public int updateCycleFlow(ExecutionCycle cycleFlow) throws ExecutorManagerException {
        try {
            String json = JSONUtils.toJSON(cycleFlow);
            byte[] data =  GZIPUtils.gzipBytes(json.getBytes(StandardCharsets.UTF_8));
            Object[] params = new Object[] {
                    cycleFlow.getStatus().getNumVal(),
                    cycleFlow.getCurrentExecId(),
                    System.currentTimeMillis(),
                    cycleFlow.getStartTime(),
                    cycleFlow.getEndTime(),
                    EncodingType.GZIP.getNumVal(),
                    data,
                    cycleFlow.getId()
            };
            return dbOperator.update(UPDATE_CYCLE_FLOW_BY_ID_SQL, params);
        } catch (SQLException | IOException e) {
            logger.error("update cycle flow failed, id: " + cycleFlow.getId(), e);
            throw new ExecutorManagerException("update cycle flow failed, execId: " + cycleFlow.getId(), e);
        }
    }

    public int stopAllCycleFlows() throws ExecutorManagerException {
        try {
            return dbOperator.update(STOP_ALL_RUNNING_CYCLE_FLOWS, System.currentTimeMillis());
        } catch (SQLException e) {
            logger.error("stop all cycle flows failed", e);
            throw new ExecutorManagerException("stop all cycle flows failed", e);
        }
    }

    public List<ExecutionCycle> getAllRunningCycleFlows() throws ExecutorManagerException {
        try {
            return dbOperator.query(GET_ALL_RUNNING_CYCLE_FLOWS, this::resultSet2CycleFlows);
        } catch (SQLException e) {
            logger.error("get all running cycle flows failed");
            throw new ExecutorManagerException("get all running cycle flows failed", e);
        }
    }

    public ExecutionCycle getExecutionCycleFlow(String projectId, String flowId) throws ExecutorManagerException {
        try {
            List<ExecutionCycle> cycleFlows = dbOperator.query(GET_CYCLE_FLOW_SQL, this::resultSet2CycleFlows, projectId, flowId);
            return cycleFlows.isEmpty() ? null : cycleFlows.get(0);
        } catch (SQLException e) {
            logger.error(String.format("get cycle flow failed, projectId: %s, flowId: %s", projectId, flowId), e);
            throw new ExecutorManagerException(String.format("get cycle flow failed, projectId: %s, flowId: %s", projectId, flowId), e);
        }
    }


    public ExecutionCycle getExecutionCycleFlow(int id) throws ExecutorManagerException {
        try {
            List<ExecutionCycle> cycleFlows = dbOperator.query(GET_CYCLE_FLOW_BY_ID_SQL, this::resultSet2CycleFlows, id);
            return cycleFlows.isEmpty() ? null : cycleFlows.get(0);
        } catch (SQLException e) {
            logger.error(String.format("get cycle flow failed, id: %d", id), e);
            throw new ExecutorManagerException(String.format("get cycle flow failed, id: %d", id), e);
        }
    }

    public int getCycleFlowsTotal(Optional<String> usernameOp) throws ExecutorManagerException {
        ResultSetHandler<Integer> handler = rs -> rs.next() ? rs.getInt(1) : 0;
        try {
            if (usernameOp.isPresent()) {
                String querySQL = GET_CYCLE_FLOWS_TOTAL_SQL + " AND p.`name` = ?";
                return dbOperator.query(querySQL, handler, usernameOp.get());
            } else {
                return dbOperator.query(GET_CYCLE_FLOWS_TOTAL_SQL, handler);
            }
        } catch (SQLException e) {
            logger.error("get cycle flows count failed, username: " + usernameOp.orElse("admin"), e);
            throw new ExecutorManagerException("get cycle flows count failed, username: " + usernameOp.orElse("admin"), e);
        }
    }

    public int getCycleFlowsTotal(String username, List<Integer> maintainedProjectIds) throws ExecutorManagerException {
        ResultSetHandler<Integer> handler = rs -> rs.next() ? rs.getInt(1) : 0;
        try {
            String projectIds = maintainedProjectIds.stream()
                    .map(Objects::toString)
                    .collect(joining(",", "(", ")"));
            final String querySQL = " SELECT COUNT(1) FROM ((SELECT id, `status`, now_exec_id, project_id, flow_id, submit_user, submit_time, update_time, start_time, end_time, enc_type, `data`" +
                "   FROM execution_cycle_flows WHERE STATUS = 30 " +
                "   AND (project_id IN " + projectIds + " OR submit_user = ?)) " +
                " UNION " +
                " (SELECT DISTINCT e.id, e.status, e.now_exec_id, e.project_id, e.flow_id, e.submit_user, e.submit_time, e.update_time, e.start_time, e.end_time, e.enc_type, e.data " +
                "   FROM execution_cycle_flows e  " +
                "   LEFT JOIN project_permissions p ON e.`project_id` = p.`project_id` WHERE e.status = 30 AND p.`name` = ? )) tmp;";
            return dbOperator.query(querySQL, handler, username, username);
        } catch (SQLException e) {
            logger.error("get cycle flows count failed", e);
            throw new ExecutorManagerException("get cycle flows count failed", e);
        }
    }


    public List<ExecutionCycle> listCycleFlows(Optional<String> usernameOp, int offset, int length) throws ExecutorManagerException {
        try {
            if (usernameOp.isPresent()) {
                String querySQL = LIST_CYCLE_FLOWS_SQL + " AND p.`name` = ? LIMIT ?, ?";
                Object[] params = new Object[]{usernameOp.get(), offset, length};
                return dbOperator.query(querySQL, this::resultSet2CycleFlows, params);
            } else {
                String querySQL = LIST_CYCLE_FLOWS_SQL + " LIMIT ?, ?";
                Object[] params = new Object[]{offset, length};
                return dbOperator.query(querySQL, this::resultSet2CycleFlows, params);
            }
        } catch (SQLException e) {
            logger.error("list cycle flows failed, username: " + usernameOp.orElse("admin"), e);
            throw new ExecutorManagerException("list cycle flows failed, username: " + usernameOp.orElse("admin"), e);
        }
    }

    public List<ExecutionCycle> listCycleFlows(String username, List<Integer> maintainedProjectIds, int offset, int length) throws ExecutorManagerException {
        try {
            String projectIds = maintainedProjectIds.stream()
                    .map(Objects::toString)
                    .collect(joining(",", "(", ")"));
            final String querySQL = " (SELECT id, `status`, now_exec_id, project_id, flow_id, submit_user, submit_time, update_time, start_time, end_time, enc_type, `data` " +
                "    FROM execution_cycle_flows WHERE STATUS = 30 " +
                "    AND (project_id IN " + projectIds + " OR submit_user = ? )) " +
                " UNION " +
                " (SELECT DISTINCT e.id, e.status, e.now_exec_id, e.project_id, e.flow_id, e.submit_user, e.submit_time, e.update_time, e.start_time, e.end_time, e.enc_type, e.data " +
                "    FROM execution_cycle_flows e  " +
                "    LEFT JOIN project_permissions p ON e.`project_id` = p.`project_id` WHERE e.status = 30 AND p.`name` = ? ) " +
                " LIMIT ?, ? ";
            Object[] params = new Object[]{username, username, offset, length};
            return dbOperator.query(querySQL, this::resultSet2CycleFlows, params);
        } catch (SQLException e) {
            logger.error("list cycle flows failed", e);
            throw new ExecutorManagerException("list cycle flows failed", e);
        }
    }

    private List<ExecutionCycle> resultSet2CycleFlows(ResultSet rs) throws SQLException {
        List<ExecutionCycle> cycleFlows = new ArrayList<>();
        while (rs.next()) {
            ExecutionCycle cycleFlow = new ExecutionCycle();
            cycleFlow.setId(rs.getInt(1));
            cycleFlow.setStatus(Status.fromInteger(rs.getInt(2)));
            cycleFlow.setCurrentExecId(rs.getInt(3));
            cycleFlow.setProjectId(rs.getInt(4));
            cycleFlow.setFlowId(rs.getString(5));
            cycleFlow.setSubmitUser(rs.getString(6));
            cycleFlow.setSubmitTime(rs.getLong(7));
            cycleFlow.setUpdateTime(rs.getLong(8));
            cycleFlow.setStartTime(rs.getLong(9));
            cycleFlow.setEndTime(rs.getLong(10));
            cycleFlow.setEncType(rs.getInt(11));
            cycleFlow.setData(rs.getBytes(12));
            cycleFlows.add(cycleFlow);
        }
        return cycleFlows;
    }

}
