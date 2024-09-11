package azkaban.system;

import azkaban.system.entity.*;

import java.util.List;
import java.util.Set;

/**
 * Created by kirkzhou on 7/6/18.
 */
public interface SystemUserLoader {


    int getWebankUserTotal() throws SystemUserManagerException;

    /**
     *
     */
    List<WebankUser> findAllWebankUserList(String searchName)
        throws SystemUserManagerException;

    /**
     *
     */
    List<WebankUser> findAllWebankUserPageList(String searchName, int pageNum
        , int pageSize) throws SystemUserManagerException;


    /**
     *
     */
    List<WtssUser> findSystemUserPage(final String userName, final String fullName
        , final String departmentName, int start, int pageSize) throws SystemUserManagerException;

    /**
     * @return
     * @throws SystemUserManagerException
     */
    int getWtssUserTotal() throws SystemUserManagerException;

    /**
     * @return
     * @throws SystemUserManagerException
     */
    int getWtssUserTotal(String username) throws SystemUserManagerException;

    /**
     * 新增系统用户
     *
     * @param wtssUser
     * @throws SystemUserManagerException
     */
    int addWtssUser(final WtssUser wtssUser) throws SystemUserManagerException;

    /**
     *
     */
    WebankUser getWebankUserByUserId(final String userId) throws SystemUserManagerException;

    /**
     * @param wtssUser
     * @return
     * @throws SystemUserManagerException
     */
    int updateWtssUser(final WtssUser wtssUser) throws SystemUserManagerException;

    /**
     * 根据id进行更新
     *
     * @param wtssUser
     * @param synEsb   是否是同步ESB的方式更新
     * @return
     * @throws SystemUserManagerException
     */
    int updateWtssUser(final WtssUser wtssUser, boolean synEsb) throws SystemUserManagerException;

    /**
     * 根据name进行更新
     *
     * @param wtssUser
     * @param synEsb   是否是同步ESB的方式更新
     * @return
     * @throws SystemUserManagerException
     */
    int updateWtssUserByName(final WtssUser wtssUser, boolean synEsb) throws SystemUserManagerException;

    /**
     *
     */
    WtssUser getWtssUserByUserId(final String userId) throws SystemUserManagerException;

    /**
     * @param username
     * @param password
     * @return
     * @throws SystemUserManagerException
     */
    WtssUser getWtssUserByUsernameAndPassword(final String username, final String password) throws SystemUserManagerException;

    /**
     * @param wtssUser
     * @return
     * @throws SystemUserManagerException
     */
    WtssUser getWtssUserByUsernameAndPassword(final WtssUser wtssUser) throws SystemUserManagerException;


    /**
     * @param roleId
     * @return
     * @throws SystemUserManagerException
     */
    WtssRole getWtssRoleById(final int roleId) throws SystemUserManagerException;

    /**
     * @param permissionsId
     * @return
     * @throws SystemUserManagerException
     */
    WtssPermissions getWtssPermissionsById(final int permissionsId) throws SystemUserManagerException;

    /**
     * @param permissionsIds
     * @return
     * @throws SystemUserManagerException
     */
    List<WtssPermissions> getWtssPermissionsListByIds(final String permissionsIds) throws SystemUserManagerException;

    /**
     * @param username
     * @return
     * @throws SystemUserManagerException
     */
    WtssUser getWtssUserByUsername(final String username) throws SystemUserManagerException;


    int getWebankDepartmentTotal() throws SystemUserManagerException;

    int getWebankDepartmentTotal(final String searchterm) throws SystemUserManagerException;

    /**
     *
     */
    List<WebankDepartment> findAllWebankDepartmentList()
        throws SystemUserManagerException;

    /**
     *
     */
    List<WebankDepartment> findAllWebankDepartmentPageList(String searchName, int pageNum
        , int pageSize) throws SystemUserManagerException;

    /**
     *
     */
    WebankDepartment getWebankDepartmentByDpId(int dpId)
        throws SystemUserManagerException;

    /**
     *
     */
    WebankDepartment getParentDepartmentByPId(int pId)
        throws SystemUserManagerException;

    /**
     *
     */
    List<WebankDepartment> findAllWebankDepartmentPageOrSearch(String searchName, int pageNum
        , int pageSize) throws SystemUserManagerException;

    /**
     * @param userId
     * @return
     * @throws SystemUserManagerException
     */
    int deleteWtssUser(final String userId) throws SystemUserManagerException;

    /**
     *
     */
    WebankUser getWebankUserByUsername(final String username) throws SystemUserManagerException;

    int addDeparment(final WebankDepartment webankDepartment) throws SystemUserManagerException;

    int updateDeparment(final WebankDepartment webankDepartment) throws SystemUserManagerException;

    int deleteDeparment(int dpId) throws SystemUserManagerException;

    /**
     * 根据部门编号查询wtssUser
     *
     * @param dpId
     * @return
     */
    List<WtssUser> getSystemUserByDepartmentId(int dpId) throws SystemUserManagerException;

    /**
     * 根据更新类型查询wtssUser
     *
     * @param modifyType 需要查询的状态, 如果不传状态,则默认查询所有变更
     * @return
     * @throws SystemUserManagerException
     */
    List<WtssUser> getModifySystemUser(String modifyType) throws SystemUserManagerException;

    /**
     * 根据更新类型查询wtssUser
     *
     * @param start    当前页面
     * @param pageSize 每页条数
     * @return
     * @throws SystemUserManagerException
     */
    List<WtssUser> getModifySystemUser(int start, int pageSize) throws SystemUserManagerException;

    /**
     * 根据条件查询变更用户
     *
     * @param searchterm
     * @param start
     * @param pageSize
     * @return
     * @throws SystemUserManagerException
     */
    List<WtssUser> getModifySystemUser(String searchterm, int start, int pageSize) throws SystemUserManagerException;

    String getModifyInfoSystemUserById(String userId) throws SystemUserManagerException;

    /**
     * 根据用户名查找用户
     *
     * @param userName
     * @return
     * @throws SystemUserManagerException
     */
    WtssUser getSystemUserByUserName(String userName) throws SystemUserManagerException;

    List<DepartmentMaintainer> getDepartmentMaintainerList(String searchterm, int start, int pageSize) throws SystemUserManagerException;

    List<DepartmentMaintainer> getDepartmentMaintainerList(int start, int pageSize) throws SystemUserManagerException;

    DepartmentMaintainer getDepMaintainerByDepId(long departmentId) throws SystemUserManagerException;

    int updateDepartmentMaintainer(long departmentId, String departmentName, String depMaintainer) throws SystemUserManagerException;

    int deleteDepartmentMaintainer(Integer departmentId) throws SystemUserManagerException;

    int addDepartmentMaintainer(long departmentId, String departmentName, String userName) throws SystemUserManagerException;

    int getDepartmentMaintainerTotal() throws SystemUserManagerException;

    List<Integer> getDepartmentMaintainerDepListByUserName(String loginUserName) throws SystemUserManagerException;

    List<Integer> getMaintainedProjects(String userId, int active) throws SystemUserManagerException;

    Set<String> getMaintainedDeptUser(String username) throws SystemUserManagerException;

    List<Integer> getMaintainedProjectsByDepList(List<Integer> departmentList, int active) throws SystemUserManagerException;
}
