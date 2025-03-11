package azkaban.executor;

import azkaban.db.DatabaseOperator;
import azkaban.db.EncodingType;
import azkaban.db.SQLTransaction;
import azkaban.utils.GZIPUtils;
import azkaban.utils.JSONUtils;
import org.apache.commons.dbutils.ResultSetHandler;
import org.apache.commons.dbutils.handlers.BeanHandler;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.*;

import static java.util.stream.Collectors.joining;

@Singleton
public class ExecutionCycleDao {

    private static final Logger logger = LoggerFactory.getLogger(ExecutionCycleDao.class);

    private final DatabaseOperator dbOperator;

    private static final String GET_CYCLE_FLOWS_TOTAL_SQL = "SELECT count(DISTINCT e.id) FROM execution_cycle_flows e " +
            " LEFT JOIN project_permissions p ON e.`project_id` = p.`project_id` WHERE e.status = 30 ";


    private static final String GET_CYCLE_FLOWS_ALL_TOTAL_SQL = "/*slave*/ SELECT count(1) from (SELECT ecf.*,p.userName from\n" +
            "            (SELECT b.name,a.* from execution_cycle_flows a left join\n" +
            "                           projects b on a.project_id  = b.id  whereCondition group by project_id,flow_id )ecf\n" +
            "    left join (SELECT project_id ,name userName from  project_permissions group by project_id ,name ) p on ecf.project_id = p.project_id where  ecf.name is not null  group by ecf.id order by ecf.id desc)A where 1=1 ";
    private static final String GET_CYCLE_FLOWS_ALL_PAGES = "/*slave*/ SELECT * from (SELECT ecf.*,p.userName from\n" +
            "            (SELECT b.name,a.* from execution_cycle_flows a left join\n" +
            "                           projects b on a.project_id  = b.id  whereCondition group by project_id,flow_id )ecf\n" +
            "    left join (SELECT project_id,name userName from  project_permissions group by project_id ,name ) p on ecf.project_id = p.project_id where  ecf.name is not null  group by ecf.id order by ecf.id desc)A where 1=1 ";


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
            "/*slave*/ SELECT id, status, now_exec_id, project_id, flow_id, submit_user, submit_time, update_time, start_time, end_time, enc_type, data " +
                    "FROM execution_cycle_flows WHERE project_id = ? AND flow_id = ? ORDER BY start_time DESC limit 1";
    private static final String GET_CYCLE_FLOW_SQL_DESC_ID =
            "/*slave*/ SELECT a.* " +
                    "FROM execution_cycle_flows a  WHERE project_id = ? AND flow_id = ? ORDER BY id DESC limit 1";

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

    private static final String GET_RUNNING_CYCLE_FLOWS =
            "/*slave*/ SELECT id, status, now_exec_id, project_id, flow_id, submit_user, submit_time, update_time, start_time, end_time, enc_type, data " +
                    "FROM execution_cycle_flows WHERE status = 30 and project_id = ? and flow_id = ? ";

    private static final String GET_ALL_CYCLE_FLOWS_TOTAL =
            "SELECT count(1) " +
                    "FROM execution_cycle_flows WHERE 1 = 1";

    private static final String GET_ALL_CYCLE_FLOWS =
            "SELECT id, status, now_exec_id, project_id, flow_id, submit_user, submit_time, update_time, start_time, end_time, enc_type, data " +
                    "FROM execution_cycle_flows where 1=1";

    @Inject
    public ExecutionCycleDao(final DatabaseOperator dbOperator) {
        this.dbOperator = dbOperator;
    }

    public synchronized int uploadCycleFlow(ExecutionCycle cycleFlow) throws ExecutorManagerException {
        long now = System.currentTimeMillis();
        Object[] params = new Object[]{
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
            return (int) transOperator.getLastInsertId();
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
            byte[] data = GZIPUtils.gzipBytes(json.getBytes(StandardCharsets.UTF_8));
            Object[] params = new Object[]{
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

    public ExecutionCycle getExecutionCycleFlowDescId(String projectId, String flowId) throws ExecutorManagerException {
        try {
            List<ExecutionCycle> cycleFlows = dbOperator.query(GET_CYCLE_FLOW_SQL_DESC_ID, this::resultSet2CycleFlowsPages, projectId, flowId);

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


    public int getExecutionCycleAllTotal(String userName, String searchTerm, HashMap<String, String> queryMap) throws ExecutorManagerException {
        String sql = GET_CYCLE_FLOWS_ALL_TOTAL_SQL;

        ResultSetHandler<Integer> handler = rs -> rs.next() ? rs.getInt(1) : 0;
        try {
            String supperSearchSql = "";
            List<Object> conditions = new ArrayList<>();
            if (queryMap != null && !queryMap.isEmpty()) {
                for (String column : queryMap.keySet()) {
                    String condition = queryMap.get(column);
                    if (StringUtils.isNotEmpty(condition)) {
                        String likeParam = "%" + condition + "%";
                        supperSearchSql = supperSearchSql + " and " + column + " like ? ";
                        conditions.add(likeParam);
                    }
                }
            }

            String param = "%" + searchTerm + "%";

            if (StringUtils.isNotEmpty(searchTerm)) {

                sql = sql.replace("whereCondition", " where b.name LIKE ? or a.flow_id LIKE ? or a.submit_user LIKE ? ");

            } else {
                sql = sql.replace("whereCondition", "");

            }

            if (StringUtils.isNotEmpty(userName)) {

                if (StringUtils.isNotEmpty(searchTerm)) {
                    sql = sql + " and A.userName = ?";
                    return dbOperator.query(sql, handler, param, param, param, userName);
                } else {
                    sql = sql + supperSearchSql;
                    sql = sql + " and A.userName = ?";
                    conditions.add(userName);
                    return dbOperator.query(sql, handler, conditions.stream().toArray());
                }


            } else {
                if (StringUtils.isNotEmpty(searchTerm)) {
                    return dbOperator.query(sql, handler, param, param, param);
                } else {
                    sql = sql + supperSearchSql;
                    return dbOperator.query(sql, handler, conditions.stream().toArray());
                }

            }

        } catch (Exception e) {

            logger.error("获取循环执行报错" + e);

        }


        return 0;
    }

    List<ExecutionCycle> getExecutionCycleAllPages(String userName, String searchTerm, int offset, int length, HashMap<String, String> queryMap) throws ExecutorManagerException {

        String sql = GET_CYCLE_FLOWS_ALL_PAGES;

        try {
            String supperSearchSql = "";
            List<Object> conditions = new ArrayList<>();
            if (queryMap != null && !queryMap.isEmpty()) {

                for (String column : queryMap.keySet()) {
                    String condition = queryMap.get(column);
                    if (StringUtils.isNotEmpty(condition)) {
                        String likeParam = "%" + condition + "%";
                        supperSearchSql = supperSearchSql + " and " + column + " like ? ";
                        conditions.add(likeParam);
                    }
                }
            }
            String param = "%" + searchTerm + "%";
            if (StringUtils.isNotEmpty(searchTerm)) {

                sql = sql.replace("whereCondition", " where b.name LIKE ? or a.flow_id LIKE ? or a.submit_user LIKE ? ");

            } else {
                sql = sql.replace("whereCondition", "");

            }

            if (StringUtils.isNotEmpty(userName)) {

                if (StringUtils.isNotEmpty(searchTerm)) {
                    sql = sql + " and A.userName = ? ";
                    sql = sql + " limit ?,?";
                    return dbOperator.query(sql, this::resultSet2CycleFlowsPages, param, param, param, userName, offset, length);
                } else {

                    sql = sql + supperSearchSql;
                    sql = sql + " and A.userName = ? ";
                    sql = sql + " limit ?,?";
                    conditions.add(userName);
                    conditions.add(offset);
                    conditions.add(length);
                    return dbOperator.query(sql, this::resultSet2CycleFlowsPages, conditions.stream().toArray());
                }


            } else {


                if (StringUtils.isNotEmpty(searchTerm)) {
                    sql = sql + " limit ?,?";
                    return dbOperator.query(sql, this::resultSet2CycleFlowsPages, param, param, param, offset, length);
                } else {
                    sql = sql + supperSearchSql;
                    sql = sql + " limit ?,?";
                    conditions.add(offset);
                    conditions.add(length);
                    return dbOperator.query(sql, this::resultSet2CycleFlowsPages, conditions.stream().toArray());
                }

            }

        } catch (Exception e) {
            logger.error("获取循环执行报错" + e);

        }

        return null;
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

    private List<ExecutionCycle> resultSet2CycleFlowsPages(ResultSet rs) throws SQLException {
        List<ExecutionCycle> cycleFlows = new ArrayList<>();
        while (rs.next()) {
            ExecutionCycle cycleFlow = new ExecutionCycle();
            // cycleFlow.setProjectName(rs.getString("name"));
            cycleFlow.setId(rs.getInt("id"));
            cycleFlow.setStatus(Status.fromInteger(rs.getInt("status")));
            cycleFlow.setCurrentExecId(rs.getInt("now_exec_id"));
            cycleFlow.setProjectId(rs.getInt("project_id"));
            cycleFlow.setFlowId(rs.getString("flow_id"));
            cycleFlow.setSubmitUser(rs.getString("submit_user"));
            cycleFlow.setSubmitTime(rs.getLong("submit_time"));
            cycleFlow.setUpdateTime(rs.getLong("update_time"));
            cycleFlow.setStartTime(rs.getLong("start_time"));
            cycleFlow.setEndTime(rs.getLong("end_time"));
            cycleFlow.setEncType(rs.getInt("enc_type"));
            cycleFlow.setData(rs.getBytes("data"));
            Map<String, Object> mp = parseData(cycleFlow.getCurrentExecId());

            if (!mp.isEmpty()) {
                cycleFlow.setCycleOption((Map<String, Object>) mp.get("cycleOptions"));
                cycleFlow.setOtherOption((Map<String, Object>) mp.get("otherOptions"));
                cycleFlow.setExecutionOptions(ExecutionOptions.createFromObject(mp.get("executionOptions")));
            }

            cycleFlows.add(cycleFlow);
        }
        return cycleFlows;
    }

    private Map<String, Object> parseData(Integer currentExecId) {

        try {
            ExecutableFlow executableFlow = this.dbOperator.query("/*slave*/ select flow_data data from execution_flows where exec_id =? limit 1", new BeanHandler<>(ExecutableFlow.class), currentExecId);
            final String jsonString = GZIPUtils.unGzipString(executableFlow.getData(), "UTF-8");
            Object o = JSONUtils.parseJSONFromString(jsonString);
            Map<String, Object> flowObject = (Map<String, Object>) o;
            return flowObject;
        } catch (Exception e) {

        }
        return new HashMap<>();
    }


    public void deleteExecutionCycle(int projectId, String flowId) {
        try {
            String sql = "DELETE from execution_cycle_flows where project_id =? and flow_id =?";
            this.dbOperator.update(sql, projectId, flowId);
        } catch (Exception e) {

            logger.error("删除失败：projectId {},flowId {},Exception {}",projectId,flowId,  e);
        }

    }

    public List<ExecutionCycle> getRunningCycleFlows(Integer projectId, String flowId) {

        try {
            return dbOperator.query(GET_RUNNING_CYCLE_FLOWS, this::resultSet2CycleFlows,projectId,flowId);
        } catch (SQLException e) {
            logger.error("get all running cycle flows failed,projectId {},flowId {},Exception",projectId,flowId,e);
        }
        return new ArrayList<>();
    }
}
