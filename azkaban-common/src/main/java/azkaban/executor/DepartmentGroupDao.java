package azkaban.executor;

import azkaban.db.DatabaseOperator;
import azkaban.db.SQLTransaction;
import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import org.apache.commons.dbutils.ResultSetHandler;
import org.apache.commons.dbutils.handlers.ScalarHandler;
import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.*;
import java.util.concurrent.TimeUnit;

/**
 *
 */
@Singleton
public class DepartmentGroupDao {

    private static final Logger logger = LoggerFactory.getLogger(DepartmentGroupDao.class);
    private final DatabaseOperator dbOperator;
    // todo:wtss_user还是cfg_webank_all_users
    private static final String FETCH_EXECUTORS_IDS_BY_SUBMIT_USER = "" +
            "SELECT e.executor_id FROM  department_group_executors e " +
            "LEFT JOIN department_group g ON e.`group_id` = g.`id` " +
            "LEFT JOIN cfg_webank_organization o ON g.`id` = o.`group_id` " +
            "LEFT JOIN wtss_user u ON u.`department_id` = o.`dp_id` " +
            "WHERE u.`username` = ? ;";

    private static final String INSERT_DEPARTMENT_GROUP = "INSERT INTO department_group (`id`, `name`, `description`, `create_time`, `update_time`, `schedule_switch`) VALUES (?, ?, ?, ?, ?, ?);";

    private static final String QUERY_GROUPNAME = "SELECT * FROM department_group WHERE `name` = ? ;";

    private static final String QUERY_EXECUTOR = "SELECT * FROM department_group_executors WHERE executor_id = ? ;";

    private static final String INSERT_DEPARTMENT_GROUP_EXECUTORS = "INSERT INTO department_group_executors (`group_id`, `executor_id`) VALUES (?, ?);";

    private static final String UPDATE_DEPARTMENT_GROUP = "UPDATE department_group SET `id` = ?, `name` = ?, description = ?, update_time = ?, schedule_switch = ? WHERE id = ? ;";

    private static final String DELETE_DEPARTMENT_GROUP_EXECUTORS = "DELETE FROM department_group_executors WHERE group_id = ? ;";
    private static final String DELETE_DEPARTMENT_GROUP_EXECUTOR = "DELETE FROM department_group_executors WHERE executor_id = ? ;";
    private static final String INSERT_DEPARTMENT_GROUP_EXECUTOR = "insert into department_group_executors (`group_id`, `executor_id`) values ( ?, ? );";
    private static final String FETCH_DEPARTMENT_GROUP_BY_EXECUTOR = "select group_id FROM department_group_executors WHERE executor_id = ? ;";
    private static final String FETCH_EXECUTOR_GROUP = "select last_department_group FROM executors WHERE id = ? ;";
    private static final String UPDATE_EXECUTOR_GROUP = "update executors set last_department_group = ? WHERE id = ? ;";

    private static final String DELETE_DEPARTMENT_GROUP = "DELETE FROM department_group WHERE id = ? ;";

    private static final String FETCH_ALL_DEPARTMENT_GROUP = "" +
            "SELECT g.`id`, g.`name`, g.`description`, g.`create_time`, g.`update_time`, e.`executor_id` ,exc.`host`, g.schedule_switch FROM department_group g " +
            "LEFT JOIN department_group_executors e ON g.id = e.`group_id` " +
            "LEFT JOIN executors exc ON exc.`id` = e.`executor_id`;";

    private static final String FETCH_DEPARTMENT_GROUP_BY_ID = "" +
            "SELECT g.`id`, g.`name`, g.`description`, g.`create_time`, g.`update_time`,e.`executor_id` ,exc.`host`, g.schedule_switch FROM department_group g " +
            "LEFT JOIN department_group_executors e ON g.id = e.`group_id` " +
            "LEFT JOIN executors exc ON exc.`id` = e.`executor_id` " +
            "WHERE g.`id` = ? ;";

    private static final String GROUP_ID_IS_EXIST = "SELECT count(1) FROM cfg_webank_organization WHERE group_id = ? ;";

    private static final String FETCH_GROUP_SCHEDULE_SWITCH =
            "SELECT d.schedule_switch FROM wtss_user u JOIN cfg_webank_organization c ON u.department_id = c.dp_id JOIN department_group d ON c.group_id = d.id WHERE u.username = ?";

    private static final String FETCH_DEPARTMENT_NAME = "select dp_id, pid, dp_name, dp_ch_name, org_id, org_name, division, group_id, upload_flag from cfg_webank_organization";

    /**
     * 缓存用户对应的部门开关，用于关闭整个部门用户的所有调度
     */
    private LoadingCache<String, Boolean> userSwitchCache = CacheBuilder
            .newBuilder().maximumSize(1000)
            .expireAfterWrite(10, TimeUnit.MINUTES)
            .build(new CacheLoader<String, Boolean>() {
                @Override
                public Boolean load(@NotNull String submitUser) throws Exception {
                    try {
                        int scheduleSwitch = dbOperator.query(DepartmentGroupDao.FETCH_GROUP_SCHEDULE_SWITCH,
                                rs -> !rs.next()? 1: rs.getInt(1), submitUser);
                        return scheduleSwitch != 0;
                    } catch (final Exception e) {
                        logger.warn("get group schedule switch status failed for submit user: " + submitUser , e);
                        return true;
                    }
                }
            });
    @Inject
    public DepartmentGroupDao(final DatabaseOperator dbOperator) {
        this.dbOperator = dbOperator;
    }



    public int updateDepartmentGroup(DepartmentGroup departmentGroup) throws ExecutorManagerException {
        try {
            final SQLTransaction<Integer> update = transOperator -> {
                final Connection conn = transOperator.getConnection();
                Integer ret = 0;
                try {
                    int scheduleSwitch = departmentGroup.getScheduleSwitch()? 1: 0;
                    ret = transOperator.update(UPDATE_DEPARTMENT_GROUP,departmentGroup.getId(), departmentGroup.getName(), departmentGroup.getDescription(), System.currentTimeMillis(), scheduleSwitch, departmentGroup.getOldId());
                    if(ret == 1){
                        transOperator.update(DELETE_DEPARTMENT_GROUP_EXECUTORS, departmentGroup.getOldId());
                        for(int executorId: departmentGroup.getExecutorIds()){
                            transOperator.update(INSERT_DEPARTMENT_GROUP_EXECUTORS, departmentGroup.getId(), executorId);
                        }
                    }
                    transOperator.getConnection().commit();
                }catch (SQLException sql){
                    if(conn != null){
                        conn.rollback();
                    }
                    throw sql;
                }
                return ret;
            };
            return this.dbOperator.transaction(update);
        } catch (final Exception e) {
            throw new ExecutorManagerException("update Department Group failed.", e);
        }
    }

    public List<CfgWebankOrganization> fetchAllDepartment() throws ExecutorManagerException {
        try {
            return this.dbOperator.query(DepartmentGroupDao.FETCH_DEPARTMENT_NAME, new FetchDepartmentName());
        } catch (final Exception e) {
            throw new ExecutorManagerException("fetch ALL Department Name failed", e);
        }
    }

    public int groupIdIsExist(DepartmentGroup departmentGroup) throws ExecutorManagerException {
        try {
            return this.dbOperator.query(DepartmentGroupDao.GROUP_ID_IS_EXIST, new CountGroupIdHandle(), departmentGroup.getId());
        } catch (final Exception e) {
            throw new ExecutorManagerException("can not found group id in cfg_webank_organization, failed.", e);
        }
    }

    public boolean fetchGroupScheduleSwitch(String submitUser) throws ExecutorManagerException {
        try {
            Boolean scheduleSwitch = userSwitchCache.get(submitUser);
            return scheduleSwitch;
        } catch (Exception e) {
            logger.warn("Failed to get submitUser {} schedule switch default return true", e);
            return true;
        }
    }

    public int executorOffline(int executorid) throws ExecutorManagerException {
        final SQLTransaction<Integer> sqlTransaction = transOperator -> {
            final Connection conn = transOperator.getConnection();
            Integer ret = 0;
            String groupIds = "";
            try {
                String lastGroupIds = transOperator.query(FETCH_EXECUTOR_GROUP, new ScalarHandler<>(), executorid);
                groupIds = transOperator.query(FETCH_DEPARTMENT_GROUP_BY_EXECUTOR, new FetchGroupIdHadler(), executorid);
                transOperator.update(UPDATE_EXECUTOR_GROUP, lastGroupIds + groupIds, executorid);
                ret = transOperator.update(DELETE_DEPARTMENT_GROUP_EXECUTOR, executorid);
                transOperator.getConnection().commit();
            }catch (SQLException sql){
                if(conn != null){
                    conn.rollback();
                }
                throw sql;
            }
            return ret;
        };
        try {
            return dbOperator.transaction(sqlTransaction);
        } catch (SQLException e) {
            throw new ExecutorManagerException("query groupIds failed", e);
        }
    }

    public int executorOnline(int executorid) throws ExecutorManagerException {
        final SQLTransaction<Integer> sqlTransaction = transOperator -> {
            final Connection conn = transOperator.getConnection();
            Integer ret = 0;
            String groupIdStr = "";
            try {
                groupIdStr = transOperator.query(FETCH_EXECUTOR_GROUP, new ScalarHandler<>(), executorid);
                transOperator.update(UPDATE_EXECUTOR_GROUP, "", executorid);
                String[] groupIds = groupIdStr.split(",");
                for (String groupId : groupIds) {
                    ret = transOperator.update(INSERT_DEPARTMENT_GROUP_EXECUTOR, groupId, executorid);
                }
                transOperator.getConnection().commit();
            }catch (SQLException sql){
                if(conn != null){
                    conn.rollback();
                }
                throw sql;
            }
            return ret;
        };
        try {
            return dbOperator.transaction(sqlTransaction);
        } catch (SQLException e) {
            throw new ExecutorManagerException("update groupIds failed", e);
        }
    }

    public boolean checkIsOnline(int executorid) throws ExecutorManagerException {
        boolean ret = false;
        String groupIds = "";
        try {
            groupIds = dbOperator.query(FETCH_DEPARTMENT_GROUP_BY_EXECUTOR, new FetchGroupIdHadler(), executorid);
            if (!StringUtils.isEmpty(groupIds)) {
                dbOperator.update(UPDATE_EXECUTOR_GROUP, "", executorid);
                ret = true;
            }
        } catch (SQLException e) {
            throw new ExecutorManagerException("check executor Is Online", e);
        }
        return ret;
    }

    public static class FetchGroupIdHadler implements ResultSetHandler<String> {
        @Override
        public String handle(ResultSet resultSet) throws SQLException {
            StringBuilder groupIds = new StringBuilder();
            while (resultSet.next()) {
                groupIds.append(resultSet.getString(1));
                groupIds.append(",");
            }
            return groupIds.toString();
        }
    }


    /**
     * JDBC ResultSetHandler to fetch records from executors table
     */
    public static class CountGroupIdHandle implements
            ResultSetHandler<Integer> {

        @Override
        public Integer handle(final ResultSet rs) throws SQLException {
            int count = 0;
            if (!rs.next()) {
                return count;
            }
            do {
                count = rs.getInt(1);
            } while (rs.next());
            return count;
        }
    }

    public int deleteDepartmentGroup(DepartmentGroup departmentGroup) throws ExecutorManagerException {
        try {
            final SQLTransaction<Integer> delete = transOperator -> {
                final Connection conn = transOperator.getConnection();
                Integer ret = 0;
                try {
                    ret = transOperator.update(DELETE_DEPARTMENT_GROUP, departmentGroup.getId());
                    if(ret == 1){
                        transOperator.update(DELETE_DEPARTMENT_GROUP_EXECUTORS, departmentGroup.getId());
                    }
                    transOperator.getConnection().commit();
                }catch (SQLException sql){
                    if(conn != null){
                        conn.rollback();
                    }
                    throw sql;
                }
                return ret;
            };
            return this.dbOperator.transaction(delete);
        } catch (final Exception e) {
            throw new ExecutorManagerException("add Department Group failed.", e);
        }
    }

    public void addDepartmentGroup(DepartmentGroup departmentGroup) throws ExecutorManagerException {
        try {
            final SQLTransaction<Integer> insert = transOperator -> {
                final Connection conn = transOperator.getConnection();
                try {
                    int scheduleSwitch = departmentGroup.getScheduleSwitch()? 1: 0;
                    transOperator.update(DepartmentGroupDao.INSERT_DEPARTMENT_GROUP, departmentGroup.getId(),
                            departmentGroup.getName(), departmentGroup.getDescription(), System.currentTimeMillis(), System.currentTimeMillis(), scheduleSwitch);
                    for(int executorId: departmentGroup.getExecutorIds()){
                        transOperator.update(INSERT_DEPARTMENT_GROUP_EXECUTORS, departmentGroup.getId(), executorId);
                    }
                    transOperator.getConnection().commit();
                }catch (SQLException sql){
                    if(conn != null){
                        conn.rollback();
                    }
                    throw sql;
                }
                return 1;
            };
            this.dbOperator.transaction(insert);
        } catch (final Exception e) {
            throw new ExecutorManagerException("add Department Group failed.", e);
        }
    }

    public boolean checkGroupNameIsExist(DepartmentGroup departmentGroup) throws ExecutorManagerException {
        boolean flag = false;
        try {
            flag = this.dbOperator.query(DepartmentGroupDao.QUERY_GROUPNAME, new CheckExistHandler(), departmentGroup.getName());
        } catch (final Exception e) {
            throw new ExecutorManagerException("checkGroupNameIsExist failed: ", e);
        }
        return flag;
    }

    public static class CheckExistHandler implements
            ResultSetHandler<Boolean> {
        @Override
        public Boolean handle(final ResultSet rs) throws SQLException {
            if (!rs.next()) {
                return false;
            }
            return true;
        }
    }

    public boolean checkExecutorIsUsed(int executorId) throws ExecutorManagerException {
        boolean flag = false;
        try {
            flag = this.dbOperator.query(DepartmentGroupDao.QUERY_EXECUTOR, new CheckExistHandler(), executorId);
        } catch (final Exception e) {
            throw new ExecutorManagerException("checkExecutorIsUsed failed: ", e);
        }
        return flag;
    }

    public List<DepartmentGroup> fetchAllDepartmentGroup() throws ExecutorManagerException {
        try {
            return this.dbOperator.query(DepartmentGroupDao.FETCH_ALL_DEPARTMENT_GROUP, new FetchDepartmentGroupHandler());
        } catch (final Exception e) {
            throw new ExecutorManagerException("fetch ALL Department Group failed", e);
        }
    }

    public DepartmentGroup fetchDepartmentGroupById(Integer id) throws ExecutorManagerException {
        DepartmentGroup departmentGroup = null;
        try {
            List<DepartmentGroup> departmentGroups = this.dbOperator.query(DepartmentGroupDao.FETCH_DEPARTMENT_GROUP_BY_ID, new FetchDepartmentGroupHandler(), id);
            if(departmentGroups.size() != 0){
                departmentGroup = departmentGroups.get(0);
            }
        } catch (final Exception e) {
            throw new ExecutorManagerException("fetch ALL Department Group failed", e);
        }
        return departmentGroup;
    }

    /**
     * JDBC ResultSetHandler to fetch records from executors table
     */
    public static class FetchDepartmentGroupHandler implements
            ResultSetHandler<List<DepartmentGroup>> {

        @Override
        public List<DepartmentGroup> handle(final ResultSet rs) throws SQLException {
            if (!rs.next()) {
                return Collections.emptyList();
            }
            Map<Integer, DepartmentGroup> departmentGroupMap = new HashMap<>();
            do {
                final int id = rs.getInt(1);
                final int executorId = rs.getInt(6);
                final String executorHost = rs.getString(7);
                if(departmentGroupMap.get(id) != null){
                    departmentGroupMap.get(id).getExecutorIds().add(executorId);
                    Executor executor = new Executor(id, executorHost, 12321,true);
                    departmentGroupMap.get(id).getExecutors().add(executor);
                } else {
                    DepartmentGroup dp = new DepartmentGroup();
                    final String name = rs.getString(2);
                    final String description = rs.getString(3);
                    final Long createTime = rs.getLong(4);
                    final Long updateTime = rs.getLong(5);
                    final boolean scheduleSwitch = rs.getInt(8) != 0;
                    dp.setId(id);
                    dp.setName(name);
                    dp.setDescription(description);
                    dp.setCreateTime(createTime);
                    dp.setUpdateTime(updateTime);
                    dp.setScheduleSwitch(scheduleSwitch);
                    dp.getExecutorIds().add(executorId);
                    Executor executor = new Executor(id, executorHost, 12321,true);
                    dp.getExecutors().add(executor);
                    departmentGroupMap.put(id, dp);
                }
            } while (rs.next());
            final List<DepartmentGroup> departmentGroups = new ArrayList<>(departmentGroupMap.values());
            return departmentGroups;
        }
    }

    public static class FetchDepartmentName implements
            ResultSetHandler<List<CfgWebankOrganization>> {

        @Override
        public List<CfgWebankOrganization> handle(final ResultSet rs) throws SQLException {
            if (!rs.next()) {
                return Collections.emptyList();
            }
            List<CfgWebankOrganization> departments = new ArrayList<>();
            do {
                CfgWebankOrganization department = new CfgWebankOrganization();
                final Integer dpId = rs.getInt(1);
                final Integer pid = rs.getInt(2);
                final String dpName = rs.getString(3);
                final String dpChName = rs.getString(4);
                final Integer orgId = rs.getInt(5);
                final String orgName = rs.getString(6);
                final String division = rs.getString(7);
                final Integer groupId = rs.getInt(8);
                final Integer uploadFlag = rs.getInt(9);
                department.setDpId(dpId);
                department.setPid(pid);
                department.setDpName(dpName);
                department.setDpChName(dpChName);
                department.setOrgId(orgId);
                department.setOrgName(orgName);
                department.setDivision(division);
                department.setGroupId(groupId);
                department.setUploadFlag(uploadFlag);
                departments.add(department);
            } while (rs.next());
            return departments;
        }
    }

    public List<Integer> fetchExecutorsIdSBySubmitUser(String submitUser) throws ExecutorManagerException {
        try {
            return this.dbOperator.query(DepartmentGroupDao.FETCH_EXECUTORS_IDS_BY_SUBMIT_USER, new FetchExecutorIdsHandler(), submitUser);
        } catch (final Exception e) {
            throw new ExecutorManagerException("fetch ExecutorsIdS BySubmitUser failed", e);
        }
    }

    /**
     * JDBC ResultSetHandler to fetch records from executors table
     */
    public static class FetchExecutorIdsHandler implements
            ResultSetHandler<List<Integer>> {

        @Override
        public List<Integer> handle(final ResultSet rs) throws SQLException {
            if (!rs.next()) {
                return Collections.emptyList();
            }

            final List<Integer> executorIds = new ArrayList<>();
            do {
                final int executorId = rs.getInt(1);
                executorIds.add(executorId);
            } while (rs.next());

            return executorIds;
        }
    }



}
