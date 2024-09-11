package azkaban.system;

import azkaban.Constants;
import azkaban.db.DatabaseOperator;
import azkaban.executor.DepartmentGroup;
import azkaban.system.entity.*;
import azkaban.utils.MD5Utils;
import azkaban.utils.Props;
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.dbutils.ResultSetHandler;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Created by zhu on 7/6/18.
 */
@Singleton
public class JdbcSystemUserImpl implements SystemUserLoader {

    private static final Logger logger = LoggerFactory.getLogger(JdbcSystemUserImpl.class);

    private final DatabaseOperator dbOperator;

    // 表 wtss_user 字段
    private static final String SQL_PARAMS_TABLE_WTSS_USER = "user_id, username, password, full_name, department_id, department_name, "
        + "email, proxy_users, role_id, user_type, create_time, update_time, modify_info, modify_type, user_category ";
    // 表 cfg_webank_all_users 字段
    private static final String SQL_PARAMS_TABLE_CFG_WEBANK_ALL_USERS = "app_id, user_id, urn, full_name, display_name, title, employee_number,"
        + "manager_urn, org_id, default_group_name, email, department_id, department_name,"
        + "start_date, mobile_phone, is_active, person_group, created_time, modified_time ";
    // 表 wtss_role 字段
    public static final String SQL_PARAMS_TABLE_WTSS_ROLE = "role_id,role_name, permissions_ids, description, create_time, update_time";


    // 表 wtss_permissions 字段
    public static final String SQL_PARAMS_TABLE_WTSS_PERMISSIONS = "permissions_id, permissions_name, permissions_value, permissions_type, description, create_time, update_time";

    // 表 cfg_webank_organization 字段
    public static final String SQL_PARAMS_TABLE_CFG_WEBANK_ORGANIZATION = "dp_id, pid, dp_name, dp_ch_name, org_id, org_name, division, group_id, upload_flag";

    // 表 department_maintainer 字段
    public static final String SQL_PARAMS_TABLE_DEPARTMENT_MAINTAINER = "department_id, department_name, ops_user";

    @Inject
    public JdbcSystemUserImpl(final Props props, final DatabaseOperator databaseOperator) {

        this.dbOperator = databaseOperator;

    }

    /**
     * 获取系统用户数据总条数
     *
     * @return
     * @throws SystemUserManagerException
     */
    @Override
    public int getWebankUserTotal() throws SystemUserManagerException {
        final IntHandler intHandler = new IntHandler();
        try {
            return this.dbOperator.query(IntHandler.GET_WEBANK_USER_TOTAL, intHandler);
        } catch (final SQLException e) {
            throw new SystemUserManagerException("Failed to find the total of Webank User.", e);
        }
    }

    @Override
    public List<WebankUser> findAllWebankUserList(String searchName) throws SystemUserManagerException {
        List<WebankUser> webankUserList = null;

        String querySQL = findAllWebankUserHandler.BASE_SQL_FIND_ALL_WEBANK_USER;

        final List<Object> params = new ArrayList<>();
        boolean first = true;

        try {

            if (StringUtils.isNotBlank(searchName)) {
                if (first) {
                    querySQL += " WHERE ";
                    first = false;
                } else {
                    querySQL += " AND ";
                }
                querySQL += " full_name LIKE ?";
                params.add('%' + searchName + '%');
                first = false;
            }

            webankUserList = this.dbOperator.query(querySQL, new findAllWebankUserHandler(), params.toArray());

        } catch (final SQLException e) {
            throw new SystemUserManagerException("Failed to find Webank User by userName.", e);
        }
        return webankUserList;
    }

    @Override
    public List<WebankUser> findAllWebankUserPageList(String searchName, int pageNum, int pageSize)
        throws SystemUserManagerException {
        List<WebankUser> webankUserList = null;

        String querySQL = findAllWebankUserHandler.BASE_SQL_FIND_ALL_WEBANK_USER;

        final List<Object> params = new ArrayList<>();
        boolean first = true;

        try {

            if (StringUtils.isNotBlank(searchName)) {
                if (first) {
                    querySQL += " WHERE ";
                    first = false;
                } else {
                    querySQL += " AND ";
                }
                querySQL += " full_name LIKE ?";
                params.add('%' + searchName + '%');
                first = false;
            }

            if (pageNum > -1 && pageSize > 0) {
                querySQL += " Limit ?, ?";
                params.add(pageNum);
                params.add(pageSize);
            }

            webankUserList = this.dbOperator.query(querySQL, new findAllWebankUserHandler(), params.toArray());

        } catch (final SQLException e) {
            throw new SystemUserManagerException("Failed to page find Webank User", e);
        }
        return webankUserList;
    }


    @Override
    public WebankUser getWebankUserByUserId(final String userId) throws SystemUserManagerException {
        List<WebankUser> webankUserList = null;
        try {

            webankUserList = this.dbOperator.query(findAllWebankUserHandler.FIND_WEBANK_USER_BY_ID, new findAllWebankUserHandler(), userId);

        } catch (final SQLException e) {
            throw new SystemUserManagerException("Failed to find Webank User by userId", e);
        }

        if (CollectionUtils.isNotEmpty(webankUserList)) {
            return webankUserList.get(0);
        } else {
            return null;
        }

    }

    private static class findAllWebankUserHandler implements ResultSetHandler<List<WebankUser>> {

        private static final String BASE_SQL_FIND_ALL_WEBANK_USER = "SELECT " + SQL_PARAMS_TABLE_CFG_WEBANK_ALL_USERS + " FROM cfg_webank_all_users";

        private static final String FIND_WEBANK_USER_BY_ID = BASE_SQL_FIND_ALL_WEBANK_USER + " WHERE user_id=? ";

        private static final String FIND_WEBANK_USER_BY_NAME = BASE_SQL_FIND_ALL_WEBANK_USER + " WHERE urn=? ";

        private static final String FIND_WEBANK_USER_BY_USERNAME = BASE_SQL_FIND_ALL_WEBANK_USER + " WHERE urn=? ";

        @Override
        public List<WebankUser> handle(final ResultSet rs) throws SQLException {
            if (!rs.next()) {
                return Collections.emptyList();
            }

            final List<WebankUser> webankUserList = new ArrayList<>();
            do {
                final int appId = rs.getInt(1);
                final String userId = rs.getString(2);
                final String urn = rs.getString(3);
                final String fullName = rs.getString(4);
                final String displayName = rs.getString(5);
                final String title = rs.getString(6);
                final long employeeNumber = rs.getLong(7);
                final String mangerUrn = rs.getString(8);
                final long orgId = rs.getLong(9);
                final String defaultGroupName = rs.getString(10);
                final String email = rs.getString(11);
                final long departmentId = rs.getLong(12);
                final String departmentName = rs.getString(13);
                final String startDate = rs.getString(14);
                final String mobilePhone = rs.getString(15);
                final String isActive = rs.getString(16);
                final int personGroup = rs.getInt(17);
                final long createdTime = rs.getLong(18);
                final long modifiedTime = rs.getLong(19);

                final WebankUser info = new WebankUser(appId, userId, urn, fullName, displayName, title
                    , employeeNumber, mangerUrn, orgId, defaultGroupName, email, departmentId
                    , departmentName, startDate, mobilePhone, isActive, personGroup, createdTime, modifiedTime);
                webankUserList.add(info);
            } while (rs.next());

            return webankUserList;
        }
    }

    @Override
    public List<WtssUser> findSystemUserPage(final String userName, final String fullName, final String departmentName, int start, int pageSize)
        throws SystemUserManagerException {
        List<WtssUser> wtssUserList = null;

        String querySQL = SystemUserHandler.BASE_SQL_FIND_WTSS_USER;

        final List<Object> params = new ArrayList<>();
        boolean first = true;

        try {

            if (StringUtils.isNotBlank(userName)) {
                if (first) {
                    querySQL += " WHERE ";
                    first = false;
                } else {
                    querySQL += " AND ";
                }
                querySQL += " username LIKE ?";
                params.add('%' + userName + '%');
                first = false;
            }

            if (StringUtils.isNotBlank(fullName)) {
                if (first) {
                    querySQL += " WHERE ";
                    first = false;
                } else {
                    querySQL += " AND ";
                }
                querySQL += " full_name LIKE ?";
                params.add('%' + fullName + '%');
                first = false;
            }

            if (StringUtils.isNotBlank(departmentName)) {
                if (first) {
                    querySQL += " WHERE ";
                    first = false;
                } else {
                    querySQL += " AND ";
                }
                querySQL += " department_name LIKE ?";
                params.add('%' + departmentName + '%');
                first = false;
            }

            if (start > -1 && pageSize > 0) {
                querySQL += " Limit ?, ?";
                params.add(start);
                params.add(pageSize);
            }

            wtssUserList = this.dbOperator.query(querySQL, new SystemUserHandler(), params.toArray());

        } catch (final SQLException e) {
            throw new SystemUserManagerException("Failed to page find WTSS User", e);
        }
        return wtssUserList;
    }

    private static class DepartmentMaintainerHandler implements ResultSetHandler<List<DepartmentMaintainer>> {

        private static final String BASE_SQL_FIND_DEPARTMENT_MAINTAINER = "SELECT " + SQL_PARAMS_TABLE_DEPARTMENT_MAINTAINER + " FROM department_maintainer ";

        private static final String FIND_DEPARTMENT_ID_BY_ID = BASE_SQL_FIND_DEPARTMENT_MAINTAINER + " WHERE department_id=? ";

        @Override
        public List<DepartmentMaintainer> handle(final ResultSet resultSet) throws SQLException {
            if (!resultSet.next()) {
                return Collections.emptyList();
            }

            final List<DepartmentMaintainer> departmentMaintainerList = new ArrayList<>();
            do {
                final int departmentId = resultSet.getInt(1);
                final String departmentName = resultSet.getString(2);
                final String opsUser = resultSet.getString(3);
                final DepartmentMaintainer departmentMaintainer = new DepartmentMaintainer();
                departmentMaintainer.setDepartmentId(departmentId);
                departmentMaintainer.setDepartmentName(departmentName);
                departmentMaintainer.setOpsUser(opsUser);

                departmentMaintainerList.add(departmentMaintainer);

            } while (resultSet.next());
            return departmentMaintainerList;
        }
    }

    private static class SystemUserHandler implements ResultSetHandler<List<WtssUser>> {

        private static final String BASE_SQL_FIND_WTSS_USER = "SELECT " + SQL_PARAMS_TABLE_WTSS_USER + " FROM wtss_user ";

        private static final String FIND_ALL_MODIFY_WTSS_USER = BASE_SQL_FIND_WTSS_USER + " WHERE modify_type !='0'";

        private static final String FIND_WTSS_USER_BY_MODIFY_TYPE = BASE_SQL_FIND_WTSS_USER + " WHERE modify_type=? ";

        private static final String FIND_WTSS_USER_BY_DEPARTMENT_ID = BASE_SQL_FIND_WTSS_USER + " WHERE department_id=? ";

        private static final String FIND_WTSS_USER_BY_ID = BASE_SQL_FIND_WTSS_USER + " WHERE user_id=? ";

        private static final String FIND_WTSS_USER_BY_NAME = BASE_SQL_FIND_WTSS_USER + " WHERE username=? ";

        private static final String FIND_WTSS_USER_BY_USERNAME_AND_PASSWORD = BASE_SQL_FIND_WTSS_USER + " WHERE username=? AND password=? ";

        private static final String FIND_WTSS_USER_BY_USERNAME = BASE_SQL_FIND_WTSS_USER + " WHERE username=? ";

        @Override
        public List<WtssUser> handle(final ResultSet rs) throws SQLException {
            if (!rs.next()) {
                return Collections.emptyList();
            }

            final List<WtssUser> wtssUserList = new ArrayList<>();
            do {

                final String userId = rs.getString(1);
                final String username = rs.getString(2);
                final String password = rs.getString(3);
                final String fullName = rs.getString(4);
                final long departmentId = rs.getLong(5);
                final String departmentName = rs.getString(6);
                final String email = rs.getString(7);
                final String proxyUsers = rs.getString(8);
                final int roleId = rs.getInt(9);
                final int userType = rs.getInt(10);
                final long createdTime = rs.getLong(11);
                final long updateTime = rs.getLong(12);
                final String modifyInfo = rs.getString(13);
                final String modifyType = rs.getString(14);
                final String userCategory = rs.getString(15);

                final WtssUser wtssUser = new WtssUser();
                wtssUser.setUserId(userId);
                wtssUser.setUsername(username);
                wtssUser.setPassword(password);
                wtssUser.setFullName(fullName);
                wtssUser.setDepartmentId(departmentId);
                wtssUser.setEmail(email);
                wtssUser.setDepartmentName(departmentName);
                wtssUser.setProxyUsers(proxyUsers);
                wtssUser.setRoleId(roleId);
                wtssUser.setUserType(userType);
                wtssUser.setCreateTime(createdTime);
                wtssUser.setUpdateTime(updateTime);
                wtssUser.setModifyInfo(modifyInfo);
                wtssUser.setModifyType(modifyType);
                wtssUser.setUserCategory(userCategory);

                wtssUserList.add(wtssUser);
            } while (rs.next());

            return wtssUserList;
        }
    }

    @Override
    public int addWtssUser(final WtssUser wtssUser) throws SystemUserManagerException {

        final String INSERT_WTSS_USER = "INSERT INTO wtss_user (" + SQL_PARAMS_TABLE_WTSS_USER + ") values (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)";
        try {
            return this.dbOperator.update(INSERT_WTSS_USER,
                wtssUser.getUserId(),
                wtssUser.getUsername(),
                wtssUser.getPassword(),
                wtssUser.getFullName(),
                wtssUser.getDepartmentId(),
                wtssUser.getDepartmentName(),
                wtssUser.getEmail(),
                wtssUser.getProxyUsers(),
                wtssUser.getRoleId(),
                wtssUser.getUserType(),
                wtssUser.getCreateTime(),
                wtssUser.getUpdateTime(),
                wtssUser.getModifyInfo(),
                wtssUser.getModifyType(),
                wtssUser.getUserCategory()
            );
        } catch (final SQLException e) {
            throw new SystemUserManagerException(String.format("Add User %s Failed", wtssUser.toString()), e);
        }
    }


    /**
     * 获取系统用户数据总条数
     *
     * @return
     * @throws SystemUserManagerException
     */
    @Override
    public int getWtssUserTotal() throws SystemUserManagerException {
        final IntHandler intHandler = new IntHandler();
        try {
            return this.dbOperator.query(IntHandler.GET_WTSS_USER_TOTAL, intHandler);
        } catch (final SQLException e) {
            throw new SystemUserManagerException("Failed to find the total of WTSS User.", e);
        }
    }

    /**
     * 获取系统用户数据总条数
     *
     * @return
     * @throws SystemUserManagerException
     */
    @Override
    public int getWtssUserTotal(String username) throws SystemUserManagerException {
        final IntHandler intHandler = new IntHandler();

        String querySQL = IntHandler.GET_WTSS_USER_TOTAL;

        final List<Object> params = new ArrayList<>();
        boolean first = true;

        try {

            if (StringUtils.isNotBlank(username)) {
                if (first) {
                    querySQL += " WHERE ";
                    first = false;
                } else {
                    querySQL += " AND ";
                }
                querySQL += " full_name LIKE ?";
                params.add('%' + username + '%');
                first = false;
            }


            return this.dbOperator.query(querySQL, intHandler, params.toArray());
        } catch (final SQLException e) {
            throw new SystemUserManagerException("Failed to find the total of WTSS User", e);
        }
    }

    public static class IntHandler implements ResultSetHandler<Integer> {

        public static String GET_WEBANK_USER_TOTAL = "SELECT count(*) FROM cfg_webank_all_users";

        public static String GET_WTSS_USER_TOTAL = "SELECT count(*) FROM wtss_user";

        public static String GET_WEBANK_DEPARTMENT_TOTAL = "SELECT count(*) FROM cfg_webank_organization ";

        public static String GET_DEPARTMENT_MAINTAINER_TOTAL = "SELECT count(*) FROM department_maintainer ";

        @Override
        public Integer handle(final ResultSet rs) throws SQLException {
            if (!rs.next()) {
                return 0;
            }
            return rs.getInt(1);
        }
    }

    @Override
    public int updateWtssUser(WtssUser wtssUser) throws SystemUserManagerException {
        final String INSERT_WTSS_USER = "UPDATE wtss_user SET "
            + "password=?, department_id=?, department_name=?, "
            + "proxy_users=?, role_id=?, user_type=?, update_time=?, user_category=? "
            + "WHERE user_id=? ";
        try {
            return this.dbOperator.update(INSERT_WTSS_USER,
                wtssUser.getPassword(),
                wtssUser.getDepartmentId(),
                wtssUser.getDepartmentName(),
                wtssUser.getProxyUsers(),
                wtssUser.getRoleId(),
                wtssUser.getUserType(),
                wtssUser.getUpdateTime(),
                wtssUser.getUserCategory(),
                wtssUser.getUserId());
        } catch (final SQLException e) {
            throw new SystemUserManagerException(String.format("Error update by wtssUser, %s ", wtssUser.toString()), e);
        }
    }

    @Override
    public int updateWtssUser(WtssUser wtssUser, boolean synEsb) throws SystemUserManagerException {
        final String INSERT_WTSS_USER = "UPDATE wtss_user SET user_id=?, department_id=?, department_name=?, update_time=?, modify_type=? ,modify_info=? "
            + "WHERE user_id=? ";
        try {
            return this.dbOperator.update(INSERT_WTSS_USER,
                wtssUser.getUserId(),
                wtssUser.getDepartmentId(),
                wtssUser.getDepartmentName(),
                wtssUser.getUpdateTime(),
                wtssUser.getModifyType(),
                wtssUser.getModifyInfo(),
                wtssUser.getUserId());
        } catch (final SQLException e) {
            throw new SystemUserManagerException(String.format("Error update by esb, %s ", wtssUser.toString()), e);
        }
    }

    @Override
    public int updateWtssUserByName(WtssUser wtssUser, boolean synEsb) throws SystemUserManagerException {
        final String INSERT_WTSS_USER = "UPDATE wtss_user SET user_id=?, department_id=?, department_name=?, update_time=?, modify_type=? ,modify_info=? "
            + "WHERE username=? ";
        try {
            return this.dbOperator.update(INSERT_WTSS_USER,
                wtssUser.getUserId(),
                wtssUser.getDepartmentId(),
                wtssUser.getDepartmentName(),
                wtssUser.getUpdateTime(),
                wtssUser.getModifyType(),
                wtssUser.getModifyInfo(),
                wtssUser.getUsername());
        } catch (final SQLException e) {
            throw new SystemUserManagerException(String.format("Error update by name, %s ", wtssUser.toString()), e);
        }
    }


    @Override
    public WtssUser getWtssUserByUserId(final String userId) throws SystemUserManagerException {
        List<WtssUser> wtssUserList = null;
        try {

            wtssUserList = this.dbOperator.query(SystemUserHandler.FIND_WTSS_USER_BY_ID, new SystemUserHandler(), userId);

        } catch (final SQLException e) {
            throw new SystemUserManagerException("Failed to find WTSS User by userId", e);
        }

        if (CollectionUtils.isNotEmpty(wtssUserList)) {
            return wtssUserList.get(0);
        } else {
            return null;
        }

    }

    @Override
    public WtssUser getWtssUserByUsernameAndPassword(String username, String password) throws SystemUserManagerException {
        List<WtssUser> wtssUserList = null;
        try {

            wtssUserList = this.dbOperator.query(SystemUserHandler.FIND_WTSS_USER_BY_USERNAME_AND_PASSWORD,
                new SystemUserHandler(), username, password);

        } catch (final SQLException e) {
            throw new SystemUserManagerException("Failed to find WTSS User by userName and password.", e);
        }

        if (CollectionUtils.isNotEmpty(wtssUserList)) {
            return wtssUserList.get(0);
        } else {
            return null;
        }
    }

    @Override
    public WtssUser getWtssUserByUsernameAndPassword(WtssUser wtssUser) throws SystemUserManagerException {
        List<WtssUser> wtssUserList = null;
        try {

            String username = wtssUser.getUsername();
            String encodePwd = MD5Utils.md5(MD5Utils.md5(wtssUser.getPassword()) + wtssUser.getUserId());

            wtssUserList = this.dbOperator.query(SystemUserHandler.FIND_WTSS_USER_BY_USERNAME_AND_PASSWORD,
                new SystemUserHandler(), username, encodePwd);

        } catch (final SQLException e) {
            throw new SystemUserManagerException("Failed to find WTSS User by userName and password.", e);
        }

        if (CollectionUtils.isNotEmpty(wtssUserList)) {
            return wtssUserList.get(0);
        } else {
            return null;
        }
    }

    @Override
    public WtssRole getWtssRoleById(int roleId) throws SystemUserManagerException {
        List<WtssRole> wtssRoleList = null;
        try {

            wtssRoleList = this.dbOperator.query(WtssRoleHandler.FIND_WTSS_ROLE_BY_ID, new WtssRoleHandler(), roleId);

        } catch (final SQLException e) {
            throw new SystemUserManagerException("Failed to find WTSS User by roleId", e);
        }

        if (CollectionUtils.isNotEmpty(wtssRoleList)) {
            return wtssRoleList.get(0);
        } else {
            return null;
        }
    }

    private static class WtssRoleHandler implements ResultSetHandler<List<WtssRole>> {

        private static final String BASE_SQL_FIND_WTSS_ROLE = "SELECT " + SQL_PARAMS_TABLE_WTSS_ROLE + " FROM wtss_role ";

        private static final String FIND_WTSS_ROLE_BY_ID = BASE_SQL_FIND_WTSS_ROLE + " WHERE role_id=? ";

        @Override
        public List<WtssRole> handle(final ResultSet rs) throws SQLException {
            if (!rs.next()) {
                return Collections.emptyList();
            }

            final List<WtssRole> wtssRoleList = new ArrayList<>();
            do {

                final int roleId = rs.getInt(1);
                final String roleName = rs.getString(2);
                final String permissions_ids = rs.getString(3);
                final String description = rs.getString(4);
                final long createdTime = rs.getLong(5);
                final long updateTime = rs.getLong(6);

                final WtssRole info = new WtssRole(roleId, roleName, permissions_ids, description, createdTime, updateTime);
                wtssRoleList.add(info);
            } while (rs.next());

            return wtssRoleList;
        }
    }

    @Override
    public WtssPermissions getWtssPermissionsById(int permissionsId)
        throws SystemUserManagerException {
        List<WtssPermissions> wtssPermissionsList = null;
        try {

            wtssPermissionsList = this.dbOperator.query(WtssPermissionsHandler.FIND_WTSS_PERM_BY_ID,
                new WtssPermissionsHandler(), permissionsId);

        } catch (final SQLException e) {
            throw new SystemUserManagerException("Failed to find WTSS Permission by permissionsId", e);
        }

        if (CollectionUtils.isNotEmpty(wtssPermissionsList)) {
            return wtssPermissionsList.get(0);
        } else {
            return null;
        }
    }

    @Override
    public List<WtssPermissions> getWtssPermissionsListByIds(String permissionsIds) throws SystemUserManagerException {
        List<WtssPermissions> wtssPermissionsList = null;

        String sql = WtssPermissionsHandler.FIND_WTSS_PERM_BY_IDS;

        sql += "(" + permissionsIds + ")";


        try {

            wtssPermissionsList = this.dbOperator.query(sql, new WtssPermissionsHandler());

        } catch (final SQLException e) {
            throw new SystemUserManagerException("Failed to find WTSS Permission by permissionsIds", e);
        }

        return wtssPermissionsList;
    }

    private static class WtssPermissionsHandler implements ResultSetHandler<List<WtssPermissions>> {

        private static final String BASE_SQL_FIND_WTSS_PERM = "SELECT " + SQL_PARAMS_TABLE_WTSS_PERMISSIONS + " FROM wtss_permissions ";

        private static final String FIND_WTSS_PERM_BY_ID = BASE_SQL_FIND_WTSS_PERM + " WHERE permissions_id=? ";

        private static final String FIND_WTSS_PERM_BY_IDS = BASE_SQL_FIND_WTSS_PERM + " WHERE permissions_id in ";

        @Override
        public List<WtssPermissions> handle(final ResultSet rs) throws SQLException {
            if (!rs.next()) {
                return Collections.emptyList();
            }

            final List<WtssPermissions> wtssPermissionsList = new ArrayList<>();
            do {

                final int permissionsId = rs.getInt(1);
                final String rolepermissionsName = rs.getString(2);
                final int permissions_value = rs.getInt(3);
                final int permissions_type = rs.getInt(4);
                final String description = rs.getString(5);
                final long createdTime = rs.getLong(6);
                final long updateTime = rs.getLong(7);

                final WtssPermissions info = new WtssPermissions(permissionsId, rolepermissionsName, permissions_value,
                    permissions_type, description, createdTime, updateTime);
                wtssPermissionsList.add(info);
            } while (rs.next());

            return wtssPermissionsList;
        }
    }

    @Override
    public WtssUser getWtssUserByUsername(String username) throws SystemUserManagerException {
        List<WtssUser> wtssUserList = null;
        try {

            wtssUserList = this.dbOperator.query(SystemUserHandler.FIND_WTSS_USER_BY_USERNAME,
                new SystemUserHandler(), username);

        } catch (final SQLException e) {
            throw new SystemUserManagerException("Failed to find WTSS User by userName", e);
        }

        if (CollectionUtils.isNotEmpty(wtssUserList)) {
            return wtssUserList.get(0);
        } else {
            return null;
        }
    }

    @Override
    public int getWebankDepartmentTotal() throws SystemUserManagerException {
        final IntHandler intHandler = new IntHandler();
        try {
            return this.dbOperator.query(IntHandler.GET_WEBANK_DEPARTMENT_TOTAL, intHandler);
        } catch (final SQLException e) {
            throw new SystemUserManagerException("Failed to find the total of Webank Department", e);
        }
    }

    @Override
    public int getWebankDepartmentTotal(final String searchterm) throws SystemUserManagerException {
        final IntHandler intHandler = new IntHandler();

        String querySQL = IntHandler.GET_WEBANK_DEPARTMENT_TOTAL;

        final List<Object> params = new ArrayList<>();

        try {
            querySQL += "WHERE pid<>100000 ";
            if (StringUtils.isNotBlank(searchterm)) {
                querySQL += " AND dp_ch_name LIKE ?";
                params.add('%' + searchterm + '%');
            }

            return this.dbOperator.query(querySQL, intHandler, params.toArray());
        } catch (final SQLException e) {
            throw new SystemUserManagerException("Failed to find the total of Webank Department",
                e);
        }
    }

    @Override
    public List<WebankDepartment> findAllWebankDepartmentList()
        throws SystemUserManagerException {
        String querySQL = WebankDepartmentHandler.FIND_WEBANK_DEPARTMENT_NO_DEP;

        try {
            return this.dbOperator.query(querySQL, new WebankDepartmentHandler());
        } catch (final SQLException e) {
            throw new SystemUserManagerException("Failed to find all Webank Department", e);
        }
    }

    @Override
    public List<WebankDepartment> findAllWebankDepartmentPageList(String searchName, int pageNum, int pageSize) throws SystemUserManagerException {
        List<WebankDepartment> webankDepartmentList = null;

        String querySQL = WebankDepartmentHandler.FIND_WEBANK_DEPARTMENT_NO_DEP;

        final List<Object> params = new ArrayList<>();
        boolean first = false;

        try {

            if (StringUtils.isNotBlank(searchName)) {
                querySQL += " AND ";
                querySQL += " dp_ch_name LIKE ?";
                params.add('%' + searchName + '%');
                first = false;
            }

            if (pageNum > -1 && pageSize > 0) {
                querySQL += " Limit ?, ?";
                params.add(pageNum);
                params.add(pageSize);
            }

            webankDepartmentList = this.dbOperator.query(querySQL, new WebankDepartmentHandler(), params.toArray());

        } catch (final SQLException e) {
            throw new SystemUserManagerException("Failed to page find Webank Department", e);
        }
        return webankDepartmentList;
    }

    /**
     *
     */
    @Override
    public WebankDepartment getWebankDepartmentByDpId(final int dpId) throws SystemUserManagerException {
        List<WebankDepartment> webankDepartmentList = null;
        try {

            webankDepartmentList = this.dbOperator.query(WebankDepartmentHandler.FIND_WEBANK_DEPARTMENT_BY_DPID,
                new WebankDepartmentHandler(), dpId);

        } catch (final SQLException e) {
            throw new SystemUserManagerException("Failed to find Webank Department by dpId", e);
        }

        if (CollectionUtils.isNotEmpty(webankDepartmentList)) {
            return webankDepartmentList.get(0);
        } else {
            return null;
        }
    }

    /**
     *
     */
    @Override
    public WebankDepartment getParentDepartmentByPId(final int pId) throws SystemUserManagerException {
        List<WebankDepartment> webankDepartmentList = null;
        try {

            webankDepartmentList = this.dbOperator.query(WebankDepartmentHandler.FIND_WEBANK_DEPARTMENT_BY_PID,
                new WebankDepartmentHandler(), pId);

        } catch (final SQLException e) {
            throw new SystemUserManagerException("Failed to find Webank Department by dpId", e);
        }

        if (CollectionUtils.isNotEmpty(webankDepartmentList)) {
            return webankDepartmentList.get(0);
        } else {
            return null;
        }
    }

    @Override
    public List<WebankDepartment> findAllWebankDepartmentPageOrSearch(String searchName, int pageNum, int pageSize) throws SystemUserManagerException {
        List<WebankDepartment> webankDepartmentList = null;

        String querySQL = FetchWebankDepartmentHandler.FETCH_ALL_WEBANK_DEPARTMENT;

        final List<Object> params = new ArrayList<>();
        boolean first = true;

        try {

            if (StringUtils.isNotBlank(searchName)) {
                if (first) {
                    querySQL += " WHERE ";
                    first = false;
                } else {
                    querySQL += " AND ";
                }
                querySQL += "c.dp_ch_name LIKE ? ";
                params.add('%' + searchName + '%');
                first = false;
            }

            if (pageNum > -1 && pageSize > 0) {
                querySQL += " Limit ?, ?";
                params.add(pageNum);
                params.add(pageSize);
            }

            webankDepartmentList = this.dbOperator.query(querySQL, new FetchWebankDepartmentHandler(), params.toArray());

        } catch (final SQLException e) {
            throw new SystemUserManagerException("Failed to page find Webank Department", e);
        }
        return webankDepartmentList;
    }

    private static class WebankDepartmentHandler implements ResultSetHandler<List<WebankDepartment>> {

        private static final String BASE_SQL_FIND_ALL_WEBANK_DEPARTMENT = "SELECT " + SQL_PARAMS_TABLE_CFG_WEBANK_ORGANIZATION + " FROM cfg_webank_organization ";

        private static final String FIND_WEBANK_DEPARTMENT_NO_DEP = BASE_SQL_FIND_ALL_WEBANK_DEPARTMENT + " WHERE pid<>" + Constants.WEBANK_DEPARTMENT_ID_NO;

        private static final String FIND_WEBANK_DEPARTMENT_BY_DPID = BASE_SQL_FIND_ALL_WEBANK_DEPARTMENT + " WHERE dp_id=? ";

        private static final String FIND_WEBANK_DEPARTMENT_BY_PID = BASE_SQL_FIND_ALL_WEBANK_DEPARTMENT + " WHERE pid=? ";


        @Override
        public List<WebankDepartment> handle(final ResultSet rs) throws SQLException {
            if (!rs.next()) {
                return Collections.emptyList();
            }

            final List<WebankDepartment> webankDepartmentList = new ArrayList<>();
            do {
                final long dpId = rs.getLong(1);
                final long pid = rs.getLong(2);
                final String dpName = rs.getString(3);
                final String dpChName = rs.getString(4);
                final long orgId = rs.getLong(5);
                final String orgName = rs.getString(6);
                final String division = rs.getString(7);
                final int groupId = rs.getInt(8);
                final int uploadFlag = rs.getInt(9);

                final WebankDepartment info = new WebankDepartment(dpId, dpName, dpChName, orgId, orgName, division, pid, groupId, uploadFlag);
                webankDepartmentList.add(info);
            } while (rs.next());

            return webankDepartmentList;
        }
    }

    private static class FetchWebankDepartmentHandler implements ResultSetHandler<List<WebankDepartment>> {

        private static final String FETCH_ALL_WEBANK_DEPARTMENT = "SELECT dp_id, pid, dp_name, dp_ch_name, org_id, org_name, division, g.`name` , c.`upload_flag` " +
            " FROM cfg_webank_organization c LEFT JOIN department_group g ON g.`id` = c.`group_id` ";


        @Override
        public List<WebankDepartment> handle(final ResultSet rs) throws SQLException {
            if (!rs.next()) {
                return Collections.emptyList();
            }

            final List<WebankDepartment> webankDepartmentList = new ArrayList<>();
            do {
                final long dpId = rs.getLong(1);
                final long pid = rs.getLong(2);
                final String dpName = rs.getString(3);
                final String dpChName = rs.getString(4);
                final long orgId = rs.getLong(5);
                final String orgName = rs.getString(6);
                final String division = rs.getString(7);
                final String groupName = rs.getString(8);
                final int uploadFlag = rs.getInt(9);
                DepartmentGroup departmentGroup = new DepartmentGroup();
                departmentGroup.setName(groupName);

                final WebankDepartment info = new WebankDepartment(dpId, dpName, dpChName, orgId, orgName, division, pid,uploadFlag);
                info.setDepartmentGroup(departmentGroup);
                webankDepartmentList.add(info);
            } while (rs.next());

            return webankDepartmentList;
        }
    }

    @Override
    public int deleteWtssUser(String userId) throws SystemUserManagerException {
        final String DELETE_WTSS_USER_BY_ID = "DELETE FROM wtss_user WHERE user_id=? ";
        try {
            return this.dbOperator.update(DELETE_WTSS_USER_BY_ID, userId);
        } catch (final SQLException e) {
            logger.error("delet wtss user failed.", e);
            throw new SystemUserManagerException("Error deleting wtss user " + userId);
        }
    }

    /**
     *
     */
    @Override
    public WebankUser getWebankUserByUsername(final String username) throws SystemUserManagerException {
        List<WebankUser> webankUserList = null;
        try {

            webankUserList = this.dbOperator.query(findAllWebankUserHandler.FIND_WEBANK_USER_BY_USERNAME, new findAllWebankUserHandler(), username);

        } catch (final SQLException e) {
            throw new SystemUserManagerException("Failed to find Webank Department by userName", e);
        }

        if (CollectionUtils.isNotEmpty(webankUserList)) {
            return webankUserList.get(0);
        } else {
            return null;
        }
    }

    @Override
    public int addDeparment(WebankDepartment webankDepartment) throws SystemUserManagerException {
        return addDeparmentHandel(webankDepartment);
    }

    /**
     * 新增系统用户
     *
     * @param webankDepartment
     * @throws SystemUserManagerException
     */
    private int addDeparmentHandel(final WebankDepartment webankDepartment) throws SystemUserManagerException {
        final String INSERT_DEPARMENT = "INSERT INTO cfg_webank_organization (" + SQL_PARAMS_TABLE_CFG_WEBANK_ORGANIZATION + ") values (?,?,?,?,?,?,?,?,?)";
        try {
            return this.dbOperator.update(INSERT_DEPARMENT,
                webankDepartment.getDpId(),
                webankDepartment.getPid(),
                webankDepartment.getDpName(),
                webankDepartment.getDpChName(),
                webankDepartment.getOrgId(),
                webankDepartment.getOrgName(),
                webankDepartment.getDivision(),
                webankDepartment.getGroupId(),
                webankDepartment.getUploadFlag()
            );
        } catch (final SQLException e) {
            throw new SystemUserManagerException(String.format("Add User %s Failed", webankDepartment.toString()), e);
        }
    }

    @Override
    public int updateDeparment(WebankDepartment webankDepartment) throws SystemUserManagerException {
        final String UPDATE_DEPARMENT_BY_ID = "UPDATE cfg_webank_organization SET "
            + "pid=?, dp_name=?, dp_ch_name=?, org_id=?, org_name=?, division=? , group_id=? ,upload_flag=? "
            + " WHERE dp_id=? ";
        try {
            int updateResult = this.dbOperator.update(UPDATE_DEPARMENT_BY_ID,
                webankDepartment.getPid(),
                webankDepartment.getDpName(),
                webankDepartment.getDpChName(),
                webankDepartment.getOrgId(),
                webankDepartment.getOrgName(),
                webankDepartment.getDivision(),
                webankDepartment.getGroupId(),
                webankDepartment.getUploadFlag(),
                webankDepartment.getDpId());
            return updateResult;

        } catch (final SQLException e) {
            throw new SystemUserManagerException(String.format("Error updating %s ", webankDepartment.toString()), e);
        }
    }

    @Override
    public int deleteDeparment(int dpId) throws SystemUserManagerException {
        final String DELETE_DEPARMENT_BY_ID = "DELETE FROM cfg_webank_organization WHERE dp_id=? ";
        try {
            return this.dbOperator.update(DELETE_DEPARMENT_BY_ID, dpId);
        } catch (final SQLException e) {
            logger.error("delet wtss user failed.", e);
            throw new SystemUserManagerException("Error deleting deparment " + dpId);
        }
    }

    @Override
    public List<WtssUser> getSystemUserByDepartmentId(int dpId) throws SystemUserManagerException {

        List<WtssUser> wtssUserList = null;
        try {
            wtssUserList = this.dbOperator.query(SystemUserHandler.FIND_WTSS_USER_BY_DEPARTMENT_ID, new SystemUserHandler(), dpId);
        } catch (final SQLException e) {
            throw new SystemUserManagerException("get SystemUser By DepartmentId failed, caused by:{}", e);
        }
        return wtssUserList;
    }


    @Override
    public List<WtssUser> getModifySystemUser(String modifyType) throws SystemUserManagerException {

        List<WtssUser> wtssUserList = null;
        try {

            if (StringUtils.isBlank(modifyType)) {
                wtssUserList = this.dbOperator.query(SystemUserHandler.FIND_ALL_MODIFY_WTSS_USER, new SystemUserHandler());
            } else {
                StringBuilder builder = new StringBuilder();
                builder.append(SystemUserHandler.BASE_SQL_FIND_WTSS_USER).append(" WHERE modify_type=? ").append(" ORDER BY update_time DESC");

                wtssUserList = this.dbOperator.query(builder.toString(), new SystemUserHandler(), modifyType);
            }
        } catch (final SQLException e) {
            throw new SystemUserManagerException("get Modify SystemUser By modifyType failed, caused by:{}", e);
        }
        return wtssUserList;
    }

    @Override
    public List<WtssUser> getModifySystemUser(int start, int pageSize) throws SystemUserManagerException {

        List<WtssUser> wtssUserList = null;
        try {
            final List<Object> params = new ArrayList<>();
            if (start > -1 && pageSize > 0) {
                params.add(start);
                params.add(pageSize);
            }
            wtssUserList = this.dbOperator.query(SystemUserHandler.FIND_ALL_MODIFY_WTSS_USER + " Limit ?, ?", new SystemUserHandler(), params.toArray());
        } catch (final SQLException e) {
            throw new SystemUserManagerException("get Modify SystemUser By modifyType failed, caused by:{}", e);
        }
        return wtssUserList;
    }

    @Override
    public List<WtssUser> getModifySystemUser(String searchterm, int start, int pageSize)
        throws SystemUserManagerException {

        List<WtssUser> wtssUserList = null;
        try {
            String querySQL = SystemUserHandler.FIND_ALL_MODIFY_WTSS_USER;
            final List<Object> params = new ArrayList<>();
            if (StringUtils.isNotBlank(searchterm)) {
                querySQL += " AND full_name LIKE ?";
                params.add('%' + searchterm + '%');
            }

            if (start > -1 && pageSize > 0) {
                querySQL += " Limit ?, ?";
                params.add(start);
                params.add(pageSize);
            }

            wtssUserList = this.dbOperator.query(querySQL, new SystemUserHandler(), params.toArray());
        } catch (SQLException e) {
            throw new SystemUserManagerException("Failed to page find WTSS User", e);
        }
        return wtssUserList;
    }

    @Override
    public String getModifyInfoSystemUserById(String userId) throws SystemUserManagerException {
        // 调用本地方法查询用户
        WtssUser wtssUserByUserId = this.getWtssUserByUserId(userId);
        if (null == wtssUserByUserId) {
            throw new SystemUserManagerException("Failed to find WTSS User by userId");
        }
        return wtssUserByUserId.getModifyInfo();
    }

    @Override
    public WtssUser getSystemUserByUserName(final String userName) throws SystemUserManagerException {
        List<WtssUser> wtssUserList = null;
        try {

            wtssUserList = this.dbOperator.query(SystemUserHandler.FIND_WTSS_USER_BY_NAME, new SystemUserHandler(), userName);

        } catch (final SQLException e) {
            throw new SystemUserManagerException("Failed to find WTSS User by userName", e);
        }

        if (CollectionUtils.isNotEmpty(wtssUserList)) {
            return wtssUserList.get(0);
        } else {
            return null;
        }

    }

    @Override
    public List<DepartmentMaintainer> getDepartmentMaintainerList(String searchterm, int start, int pageSize) throws SystemUserManagerException {
        List<DepartmentMaintainer> departmentMaintainerList = null;
        try {
            String querySQL = DepartmentMaintainerHandler.BASE_SQL_FIND_DEPARTMENT_MAINTAINER;
            final List<Object> params = new ArrayList<>();
            if (StringUtils.isNotBlank(searchterm)) {
                querySQL += " WHERE department_id LIKE ? or department_name LIKE ? or ops_user LIKE ?";
                params.add('%' + searchterm + '%');
                params.add('%' + searchterm + '%');
                params.add('%' + searchterm + '%');
            }

            if (start > -1 && pageSize > 0) {
                querySQL += " Limit ?, ?";
                params.add(start);
                params.add(pageSize);
            }

            departmentMaintainerList = this.dbOperator.query(querySQL, new DepartmentMaintainerHandler(), params.toArray());
        } catch (SQLException e) {
            throw new SystemUserManagerException("Failed to page find department maintainer, caused by:", e);
        }
        return departmentMaintainerList;
    }

    @Override
    public List<DepartmentMaintainer> getDepartmentMaintainerList(int start, int pageSize) throws SystemUserManagerException {
        List<DepartmentMaintainer> departmentMaintainerList = null;
        try {
            String querySQL = DepartmentMaintainerHandler.BASE_SQL_FIND_DEPARTMENT_MAINTAINER;
            final List<Object> params = new ArrayList<>();

            if (start > -1 && pageSize > 0) {
                querySQL += " Limit ?, ?";
                params.add(start);
                params.add(pageSize);
            }

            departmentMaintainerList = this.dbOperator.query(querySQL, new DepartmentMaintainerHandler(), params.toArray());
        } catch (SQLException e) {
            throw new SystemUserManagerException("Failed to page find department maintainer, caused by:", e);
        }
        return departmentMaintainerList;
    }

    @Override
    public DepartmentMaintainer getDepMaintainerByDepId(long departmentId) throws SystemUserManagerException {
        List<DepartmentMaintainer> departmentMaintainerList = null;
        try {
            String querySQL = DepartmentMaintainerHandler.FIND_DEPARTMENT_ID_BY_ID;

            departmentMaintainerList = this.dbOperator.query(querySQL, new DepartmentMaintainerHandler(), departmentId);

        } catch (SQLException e) {
            throw new SystemUserManagerException("Failed to page find department maintainer, caused by:", e);
        }
        if (CollectionUtils.isNotEmpty(departmentMaintainerList)) {
            return departmentMaintainerList.get(0);
        } else {
            return null;
        }
    }

    /**
     * 获取部门运维人员数据总条数
     *
     * @return
     * @throws SystemUserManagerException
     */
    @Override
    public int getDepartmentMaintainerTotal() throws SystemUserManagerException {
        final IntHandler intHandler = new IntHandler();
        try {
            return this.dbOperator.query(IntHandler.GET_DEPARTMENT_MAINTAINER_TOTAL, intHandler);
        } catch (final SQLException e) {
            throw new SystemUserManagerException("Failed to find the total of Department Maintainer.", e);
        }
    }

    @Override
    public int updateDepartmentMaintainer(long departmentId, String departmentName, String depMaintainer) throws SystemUserManagerException {
        final String UPDATE_DEPARTMENT_MAINTAINER = "UPDATE department_maintainer SET "
            + "department_id=?, department_name=?, ops_user=? "
            + "WHERE department_id=? ";
        try {
            return this.dbOperator.update(UPDATE_DEPARTMENT_MAINTAINER, departmentId, departmentName, depMaintainer, departmentId);
        } catch (final SQLException e) {
            Integer destDepartmentId = Integer.valueOf(departmentId + "");
            String objectStr = new DepartmentMaintainer(destDepartmentId, departmentName, depMaintainer).toString();
            throw new SystemUserManagerException(String.format("Error adding %s ", objectStr), e);
        }
    }

    @Override
    public int deleteDepartmentMaintainer(Integer departmentId) throws SystemUserManagerException {
        final String DELETE_DEPARTMENT_MAINTAINER_BY_ID = "DELETE FROM department_maintainer WHERE department_id=? ";
        try {
            return this.dbOperator.update(DELETE_DEPARTMENT_MAINTAINER_BY_ID, departmentId);
        } catch (final SQLException e) {
            throw new SystemUserManagerException("Error delete Department Maintainer, id= " + departmentId);
        }
    }

    @Override
    public int addDepartmentMaintainer(long departmentId, String departmentName, String userName) throws SystemUserManagerException {
        final String INSERT_DEPARTMENT_MAINTAINER = "INSERT INTO department_maintainer (" + SQL_PARAMS_TABLE_DEPARTMENT_MAINTAINER + ") values (?,?,?)";
        try {
            return this.dbOperator.update(INSERT_DEPARTMENT_MAINTAINER, departmentId, departmentName, userName);
        } catch (final SQLException e) {
            Integer destDepartmentId = Integer.valueOf(departmentId + "");
            String objectStr = new DepartmentMaintainer(destDepartmentId, departmentName, userName).toString();
            throw new SystemUserManagerException(String.format("Add department maintainer %s Failed", objectStr), e);
        }
    }

    @Override
    public  List<Integer> getDepartmentMaintainerDepListByUserName(String loginUserName) throws SystemUserManagerException {
        List<DepartmentMaintainer> departmentMaintainerList = null;
        try {

            String querySql = DepartmentMaintainerHandler.BASE_SQL_FIND_DEPARTMENT_MAINTAINER;
            final List<Object> params = new ArrayList<>();
            if (StringUtils.isNotBlank(loginUserName)) {
                querySql += " WHERE ops_user LIKE ?";
                params.add('%' + loginUserName + '%');
            }
            departmentMaintainerList = this.dbOperator.query(querySql, new DepartmentMaintainerHandler(), params.toArray());

            if (CollectionUtils.isNotEmpty(departmentMaintainerList)) {
                // 过滤数据, 过滤掉因为前后缀匹配出来的垃圾数据
                List<DepartmentMaintainer> realDataList = new ArrayList<>();
                for (DepartmentMaintainer departmentMaintainer : departmentMaintainerList) {
                    String opsUserStr = departmentMaintainer.getOpsUser().trim();

                    // 过滤垃圾数据,获取有效的运维人员信息
                    List<String> opsUserList = filterInvalidData(opsUserStr);
                    if (CollectionUtils.isNotEmpty(opsUserList)) {
                        if (opsUserList.contains(loginUserName)) {
                            realDataList.add(departmentMaintainer);
                        }
                    }
                }
                return realDataList.stream().map(DepartmentMaintainer::getDepartmentId).collect(Collectors.toList());
            } else {
                return null;
            }
        } catch (SQLException e) {
            throw new SystemUserManagerException("Failed to page find department maintainer depId, caused by:", e);
        }

    }

    @Override
    public List<Integer> getMaintainedProjects(String username, int active) throws SystemUserManagerException {
        String prefix = "'" + username + ",%'";
        String middle = "'%," + username + ",%'";
        String suffix = "'%," + username + "'";
        String querySql = "SELECT pr.id FROM projects pr "
            + "JOIN wtss_user w on w.username=pr.create_user "
            + "JOIN department_maintainer d on d.department_id=w.department_id "
            + "WHERE pr.active=? and (d.ops_user like " + prefix + " or d.ops_user like " + middle
            + " or d.ops_user like " + suffix + ")";
        try {
            return this.dbOperator.query(querySql, this::getProjectIds, active);
        } catch (SQLException e) {
            throw new SystemUserManagerException(
                "get maintained projects of " + username + " failed", e);
        }
    }

    @Override
    public Set<String> getMaintainedDeptUser(String username) throws SystemUserManagerException {
        String prefix = "'" + username + ",%'";
        String middle = "'%," + username + ",%'";
        String suffix = "'%," + username + "'";
        String querySql = "SELECT w.username FROM wtss_user w "
            + "JOIN department_maintainer d on d.department_id=w.department_id "
            + "WHERE d.ops_user like " + prefix + " or d.ops_user like " + middle
            + " or d.ops_user like " + suffix;
        try {
            return this.dbOperator.query(querySql, this::getStringList);
        } catch (SQLException e) {
            throw new SystemUserManagerException("get maintained users of " + username + " failed",
                e);
        }
    }

    @Override
    public List<Integer> getMaintainedProjectsByDepList(List<Integer> departmentList, int active) throws SystemUserManagerException {
        String departIds = departmentList.stream().map(String::valueOf).collect(Collectors.joining(",", "(", ")"));
        String querySql = "SELECT pr.id FROM projects pr "
                + "JOIN wtss_user w on w.username=pr.create_user "
                + "where pr.active=? and w.department_id in " + departIds;
        try {
            return this.dbOperator.query(querySql, this::getProjectIds, active);
        } catch (SQLException e) {
            throw new SystemUserManagerException(
                    "get maintained projects failed", e);
        }
    }

    /**
     * 过滤垃圾数据, 例如:
     * 将 ",,, ,,ab,,bf,er,,,ghc,d,,der,,," 变成  "ab,bf,er,ghc,d,der"
     * @param originStr
     * @return
     */
    private List<String> filterInvalidData(String originStr) {

        String[] strings = originStr.split(",");
        List<String> list = new ArrayList<>();
        for (String string : strings) {
            if (StringUtils.isNotBlank(string)) {
                list.add(string);
            }
        }

        return list;
    }

    private List<Integer> getProjectIds(ResultSet rs) throws SQLException {
        List<Integer> projectIds = new ArrayList<>();
        while (rs.next()) {
            projectIds.add(rs.getInt(1));
        }
        return projectIds;
    }

    private Set<String> getStringList(ResultSet rs) throws SQLException {
        Set<String> values = new HashSet<>();
        while (rs.next()) {
            values.add(rs.getString(1));
        }
        return values;
    }
}
