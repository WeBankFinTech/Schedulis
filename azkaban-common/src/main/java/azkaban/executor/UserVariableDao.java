package azkaban.executor;

import azkaban.db.DatabaseOperator;
import azkaban.db.SQLTransaction;
import azkaban.system.entity.WtssUser;
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
public class UserVariableDao {

    private static final Logger logger = LoggerFactory.getLogger(UserVariableDao.class);
    private final DatabaseOperator dbOperator;

    private final static String INSERT_USER_VARIABLE = "INSERT INTO user_variable (`key`, `description`, `value`, `owner`, `create_time`, `update_time`) VALUES (?, ?, ?, ?, ?, ?);";
    private final static String INSERT_USER_VARIABLE_USER = "INSERT INTO user_variable_user (`v_id`, `username`) VALUES (?, ?);";

    private final static String DELETE_USER_VARIABLE = "DELETE FROM user_variable WHERE id = ? ;";
    private final static String DELETE_USER_VARIABLE_USER = "DELETE FROM user_variable_user WHERE v_id = ? ;";

    private final static String UPDATE_USER_VARIABLE = "UPDATE user_variable SET `key` = ?, description = ?, `value` = ?, `owner` = ?, update_time = ? WHERE id = ? ;";

    private final static String FETCH_ALL_USER_VARIABLE = "SELECT v.`id`, v.`key`, v.`description`,v.`value`,v.`owner`,v.`create_time`,v.`update_time`,u.`username` FROM user_variable v LEFT JOIN user_variable_user u ON v.id = u.v_id where v.`owner` = ? OR u.`username` = ? ;";

    private final static String FETCH_USER_VARIABLE_BY_ID = "SELECT v.`id`, v.`key`, v.`description`,v.`value`, v.`owner`,v.`create_time`, v.`update_time`, u.`username`, w.`full_name` FROM user_variable v " +
        "LEFT JOIN user_variable_user u ON v.id = u.v_id " +
        "LEFT JOIN wtss_user w ON u.`username` = w.`username` " +
        "WHERE v.id = ? ;";

    private final static String WTSS_USER_IS_EXIST = "select count(1) from wtss_user where username = ?";

    private final static String LOAD_WTSS_USER = "SELECT user_id, username, full_name FROM wtss_user ";

    private final static String COUNT_WTSS_USER = "SELECT count(1) FROM wtss_user";

    private final static String FIND_USER_VARIABLE_BY_KEY = "SELECT v.`id`, v.`key`, v.`description`,v.`value`, v.`owner`,v.`create_time`, v.`update_time` FROM user_variable v WHERE v.`key` = ? ;";

    @Inject
    public UserVariableDao(final DatabaseOperator dbOperator) {
        this.dbOperator = dbOperator;
    }

    public Integer getWtssUserTotal() throws ExecutorManagerException {
        try {
            return this.dbOperator.query(UserVariableDao.COUNT_WTSS_USER, new FindWtssUserHandler());
        } catch (final SQLException e) {
            throw new ExecutorManagerException("Failed to statistics the number of user in wtss_user", e);
        }
    }

    public List<WtssUser> findAllWtssUserPageList(String searchName, int pageNum, int pageSize)
            throws ExecutorManagerException {
        List<WtssUser> wtssUserList = null;
        String querySQL = UserVariableDao.LOAD_WTSS_USER;
        final List<Object> params = new ArrayList<>();
        try {
            if (searchName != null && !searchName.isEmpty()) {
                querySQL += " WHERE username LIKE ? ";
                params.add('%' + searchName + '%');
            }
            if (pageNum > -1 && pageSize > 0 ) {
                querySQL += " Limit ?, ?";
                params.add(pageNum);
                params.add(pageSize);
            }
            wtssUserList = this.dbOperator.query(querySQL, new FetchWtssUserHandler(), params.toArray());
        } catch (final SQLException e) {
            throw new ExecutorManagerException("Failed to page find user in wtss_user.", e);
        }
        return wtssUserList;
    }

    public UserVariable findUserVariableByKey(String key) throws ExecutorManagerException {
        try{
            List<UserVariable> userVariables = this.dbOperator.query(UserVariableDao.FIND_USER_VARIABLE_BY_KEY, new FetchUserVariableByKeyHandler(), key);
            if(!userVariables.isEmpty()) {
              return userVariables.get(0);
            }
        } catch (final SQLException e) {
            throw new ExecutorManagerException("find user variable by key failed.", e);
        }
        return null;
    }

    public static class FetchWtssUserHandler implements
            ResultSetHandler<List<WtssUser>> {

        @Override
        public List<WtssUser> handle(final ResultSet rs) throws SQLException {
            if (!rs.next()) {
                return Collections.emptyList();
            }
            List<WtssUser> wtssUserList = new ArrayList<>();
            do {
                WtssUser wtssUser = new WtssUser();
                String userId = rs.getString(1);
                String username = rs.getString(2);
                String fullName = rs.getString(3);
                wtssUser.setUserId(userId);
                wtssUser.setUsername(username);
                wtssUser.setFullName(fullName);
                wtssUserList.add(wtssUser);
            } while (rs.next());
            return wtssUserList;
        }
    }

    public Integer findWtssUserByName(String name) throws ExecutorManagerException{
        try {
            return this.dbOperator.query(WTSS_USER_IS_EXIST, new FindWtssUserHandler(), name);
        } catch (final Exception e) {
            throw new ExecutorManagerException("findWtssUserByName failed.", e);
        }
    }

    /**
     * JDBC ResultSetHandler to fetch records from executors table
     */
    public static class FindWtssUserHandler implements
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

    public void addUserVariable(UserVariable userVariable) throws ExecutorManagerException{
        try {
            final SQLTransaction<Long> insertAndGetLastID = transOperator -> {
                final Connection conn = transOperator.getConnection();
                long id = 0;
                try {
                    transOperator.update(INSERT_USER_VARIABLE, userVariable.getKey(), userVariable.getDescription(), userVariable.getValue(), userVariable.getOwner(), System.currentTimeMillis(), System.currentTimeMillis());
                    id = transOperator.getLastInsertId();
                    for(WtssUser user: userVariable.getUsers()){
                        transOperator.update(INSERT_USER_VARIABLE_USER, (int)id, user.getUserId());
                    }
                    transOperator.getConnection().commit();
                }catch (SQLException sql){
                    if(conn != null){
                        conn.rollback();
                    }
                    throw sql;
                }
                return id;
            };
            this.dbOperator.transaction(insertAndGetLastID);
        } catch (final Exception e) {
            throw new ExecutorManagerException("addUserVariable failed.", e);
        }
    }

    public int deleteUserVariable(UserVariable variable) throws ExecutorManagerException{
        try {
            final SQLTransaction<Integer> delete = transOperator -> {
                final Connection conn = transOperator.getConnection();
                Integer ret = 0;
                try {
                    ret = transOperator.update(DELETE_USER_VARIABLE, variable.getId());
                    if(ret == 1){
                        transOperator.update(DELETE_USER_VARIABLE_USER, variable.getId());
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
            throw new ExecutorManagerException("deleteUserVariable failed.", e);
        }
    }

    public int updateUserVariable(UserVariable userVariable) throws Exception{
        final SQLTransaction<Integer> update = transOperator -> {
            final Connection conn = transOperator.getConnection();
            Integer ret = 0;
            try {
                ret = transOperator.update(UPDATE_USER_VARIABLE, userVariable.getKey(), userVariable.getDescription(), userVariable.getValue(), userVariable.getOwner(), System.currentTimeMillis(), userVariable.getId());
                if(ret == 1){
                    transOperator.update(DELETE_USER_VARIABLE_USER, userVariable.getId());
                    for(WtssUser user: userVariable.getUsers()) {
                        if (user.getUserId() != null) {
                            transOperator.update(INSERT_USER_VARIABLE_USER, userVariable.getId(),
                                user.getUserId());
                        }
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
    }

    public List<UserVariable> fetchAllUserVariable(UserVariable userVariable) throws ExecutorManagerException{
        List<UserVariable> userVariables = null;
        try {
            userVariables = this.dbOperator.query(UserVariableDao.FETCH_ALL_USER_VARIABLE, new FetchAllUserVariableHandler(), userVariable.getOwner(), userVariable.getOwner());
        } catch (final Exception e) {
            throw new ExecutorManagerException("fetch ALL UserVariable failed", e);
        }
        return userVariables;
    }

    public List<UserVariable> fetchAllUserVariableByOwnerDepartment(Integer departmentId) throws ExecutorManagerException {
        List<UserVariable> userVariableList = null;
        try {
            String querySql = UserVariableHandler.FETCH_USER_VARIABLE_BY_OWNER_DEPARTMENT_ID;
            userVariableList = this.dbOperator.query(querySql, new UserVariableHandler(), departmentId);
        } catch (SQLException e) {
            throw new ExecutorManagerException("Failed to find userVariableList, caused by:", e);
        }

        return userVariableList;

    }

    public UserVariable getUserVariableById(Integer id) throws ExecutorManagerException{
        List<UserVariable> userVariables = null;
        UserVariable userVariable = null;
        try {
            userVariables = this.dbOperator.query(UserVariableDao.FETCH_USER_VARIABLE_BY_ID, new FetchUserVariableByIdHandler(), id);
            if(userVariables.size() != 0){
                userVariable = userVariables.get(0);
            }
        } catch (final Exception e) {
            throw new ExecutorManagerException("fetch ALL UserVariable failed", e);
        }
        return userVariable;
    }

    public static class FetchUserVariableByKeyHandler implements ResultSetHandler<List<UserVariable>> {

        @Override
        public List<UserVariable> handle(final ResultSet rs) throws SQLException {
            if (!rs.next()) {
                return Collections.emptyList();
            }
            List<UserVariable> userVariables = new ArrayList<>();
            do {
                UserVariable userVariable = new UserVariable();
                userVariable.setId(rs.getInt(1));
                userVariable.setKey(rs.getString(2));
                userVariable.setDescription(rs.getString(3));
                userVariable.setValue(rs.getString(4));
                userVariable.setOwner(rs.getString(5));
                userVariable.setCreateTime(rs.getLong(6));
                userVariable.setUpdateTime(rs.getLong(7));
                userVariables.add(userVariable);
            } while (rs.next());
            return userVariables;
        }
    }

    public static class FetchAllUserVariableHandler implements ResultSetHandler<List<UserVariable>> {

        @Override
        public List<UserVariable> handle(final ResultSet rs) throws SQLException {
            if (!rs.next()) {
                return Collections.emptyList();
            }
            List<UserVariable> userVariables = new ArrayList<>();
            Map<Integer, UserVariable> tmp = new HashMap<>();
            do {
                UserVariable userVariable = new UserVariable();
                int id = rs.getInt(1);
                userVariable.setId(id);
                userVariable.setKey(rs.getString(2));
                userVariable.setDescription(rs.getString(3));
                userVariable.setValue(rs.getString(4));
                userVariable.setOwner(rs.getString(5));
                userVariable.setCreateTime(rs.getLong(6));
                userVariable.setUpdateTime(rs.getLong(7));
                String userName = rs.getString(8);
                WtssUser user = new WtssUser();
                user.setUserId(userName);
                userVariable.getUsers().add(user);
                if(tmp.get(id) != null){
                    tmp.get(id).getUsers().add(user);
                } else {
                    tmp.put(id, userVariable);
                }
            } while (rs.next());
            userVariables.addAll(tmp.values());
            return userVariables;
        }
    }

    public static class FetchUserVariableByIdHandler implements ResultSetHandler<List<UserVariable>> {

        @Override
        public List<UserVariable> handle(final ResultSet rs) throws SQLException {
            if (!rs.next()) {
                return Collections.emptyList();
            }
            List<UserVariable> userVariables = new ArrayList<>();
            Map<Integer, UserVariable> tmp = new HashMap<>();
            do {
                UserVariable userVariable = new UserVariable();
                int id = rs.getInt(1);
                userVariable.setId(id);
                userVariable.setKey(rs.getString(2));
                userVariable.setDescription(rs.getString(3));
                userVariable.setValue(rs.getString(4));
                userVariable.setOwner(rs.getString(5));
                userVariable.setCreateTime(rs.getLong(6));
                userVariable.setUpdateTime(rs.getLong(7));
                String userName = rs.getString(8);
                String fullName = rs.getString(9);
                WtssUser user = new WtssUser();
                user.setUserId(userName);
                user.setFullName(fullName);
                userVariable.getUsers().add(user);
                if(tmp.get(id) != null){
                    tmp.get(id).getUsers().add(user);
                } else {
                    tmp.put(id, userVariable);
                }
            } while (rs.next());
            userVariables.addAll(tmp.values());
            return userVariables;
        }
    }


    public static class UserVariableHandler implements ResultSetHandler<List<UserVariable>> {

        private final static String FETCH_USER_VARIABLE_BY_OWNER_DEPARTMENT_ID = "SELECT * FROM user_variable WHERE owner in"
            + "(SELECT urn FROM cfg_webank_all_users where department_id=?)";

        @Override
        public List<UserVariable> handle(final ResultSet rs) throws SQLException {
            if (!rs.next()) {
                return Collections.emptyList();
            }
            List<UserVariable> userVariables = new ArrayList<>();
            do {
                UserVariable userVariable = new UserVariable();

                userVariable.setId(rs.getInt(1));
                userVariable.setKey(rs.getString(2));
                userVariable.setDescription(rs.getString(3));
                userVariable.setValue(rs.getString(4));
                userVariable.setOwner(rs.getString(5));
                userVariable.setCreateTime(rs.getLong(6));
                userVariable.setUpdateTime(rs.getLong(7));

                userVariables.add(userVariable);
            } while (rs.next());

            return userVariables;
        }
    }


    public Map<String, String> getUserVariableByName(String userName) throws ExecutorManagerException{
        List<UserVariable> userVariables = null;
        Map<String, String> variables = new HashMap<>();
        try {
            userVariables = this.dbOperator.query(UserVariableDao.FETCH_ALL_USER_VARIABLE, new FetchUserVariableHandler(), userName, userName);
            if(userVariables.size() != 0){
                for(UserVariable userVariable: userVariables){
                    variables.put(userVariable.getKey(), userVariable.getValue());
                }
            }
        } catch (final Exception e) {
            throw new ExecutorManagerException("fetch ALL UserVariable failed", e);
        }
        return variables;
    }


    /**
     * JDBC ResultSetHandler to fetch records from executors table
     */
    public static class FetchUserVariableHandler implements
            ResultSetHandler<List<UserVariable>> {

        @Override
        public List<UserVariable> handle(final ResultSet rs) throws SQLException {
            if (!rs.next()) {
                return Collections.emptyList();
            }
            List<UserVariable> userVariables = new ArrayList<>();
            do {
                UserVariable userVariable = new UserVariable();
                userVariable.setId(rs.getInt(1));
                userVariable.setKey(rs.getString(2));
                userVariable.setDescription(rs.getString(3));
                userVariable.setValue(rs.getString(4));
                userVariable.setOwner(rs.getString(5));
                userVariable.setCreateTime(rs.getLong(6));
                userVariable.setUpdateTime(rs.getLong(7));
                userVariables.add(userVariable);
            } while (rs.next());
            return userVariables;
        }
    }



}
