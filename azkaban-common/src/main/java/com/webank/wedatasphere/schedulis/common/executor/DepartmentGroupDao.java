/*
 * Copyright 2020 WeBank
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.webank.wedatasphere.schedulis.common.executor;

import azkaban.db.DatabaseOperator;
import azkaban.db.SQLTransaction;
import azkaban.executor.Executor;
import azkaban.executor.ExecutorManagerException;
import org.apache.commons.dbutils.ResultSetHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.*;

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

    private static final String INSERT_DEPARTMENT_GROUP = "INSERT INTO department_group (`id`, `name`, `description`, `create_time`, `update_time`) VALUES (?, ?, ?, ?, ?);";

    private static final String QUERY_GROUPNAME = "SELECT * FROM department_group WHERE `name` = ? ;";

    private static final String QUERY_EXECUTOR = "SELECT * FROM department_group_executors WHERE executor_id = ? ;";

    private static final String INSERT_DEPARTMENT_GROUP_EXECUTORS = "INSERT INTO department_group_executors (`group_id`, `executor_id`) VALUES (?, ?);";

    private static final String UPDATE_DEPARTMENT_GROUP = "UPDATE department_group SET `id` = ?, `name` = ?, description = ?, update_time = ? WHERE id = ? ;";

    private static final String DELETE_DEPARTMENT_GROUP_EXECUTORS = "DELETE FROM department_group_executors WHERE group_id = ? ;";

    private static final String DELETE_DEPARTMENT_GROUP = "DELETE FROM department_group WHERE id = ? ;";

    private static final String FETCH_ALL_DEPARTMENT_GROUP = "" +
            "SELECT g.`id`, g.`name`, g.`description`, g.`create_time`, g.`update_time`,e.`executor_id` ,exc.`host` FROM department_group g " +
            "LEFT JOIN department_group_executors e ON g.id = e.`group_id` " +
            "LEFT JOIN executors exc ON exc.`id` = e.`executor_id`;";

    private static final String FETCH_DEPARTMENT_GROUP_BY_ID = "" +
            "SELECT g.`id`, g.`name`, g.`description`, g.`create_time`, g.`update_time`,e.`executor_id` ,exc.`host` FROM department_group g " +
            "LEFT JOIN department_group_executors e ON g.id = e.`group_id` " +
            "LEFT JOIN executors exc ON exc.`id` = e.`executor_id` " +
            "WHERE g.`id` = ? ;";

    private static final String GROUP_ID_IS_EXIST = "SELECT count(1) FROM cfg_webank_organization WHERE group_id = ? ;";

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
                    ret = transOperator.update(UPDATE_DEPARTMENT_GROUP,departmentGroup.getId(), departmentGroup.getName(), departmentGroup.getDescription(), System.currentTimeMillis(), departmentGroup.getOldId());
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

    public int groupIdIsExist(DepartmentGroup departmentGroup) throws ExecutorManagerException {
        try {
            return this.dbOperator.query(DepartmentGroupDao.GROUP_ID_IS_EXIST, new CountGroupIdHandle(), departmentGroup.getId());
        } catch (final Exception e) {
            throw new ExecutorManagerException("can not found group id in cfg_webank_organization, failed.", e);
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
                    transOperator.update(DepartmentGroupDao.INSERT_DEPARTMENT_GROUP, departmentGroup.getId(),
                            departmentGroup.getName(), departmentGroup.getDescription(), System.currentTimeMillis(), System.currentTimeMillis());
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
                    dp.setId(id);
                    dp.setName(name);
                    dp.setDescription(description);
                    dp.setCreateTime(createTime);
                    dp.setUpdateTime(updateTime);
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
