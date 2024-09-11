/*
 * Copyright 2012 LinkedIn Corp.
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

package azkaban.system;

import azkaban.executor.DepartmentGroup;
import azkaban.executor.Executor;
import azkaban.executor.ExecutorManagerException;
import azkaban.executor.JdbcExecutorLoader;
import azkaban.i18n.utils.LoadJsonUtils;
import azkaban.project.Project;
import azkaban.project.ProjectLoader;
import azkaban.storage.StorageManager;
import azkaban.system.entity.*;
import azkaban.user.User;
import azkaban.utils.MD5Utils;
import azkaban.utils.Props;
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static java.util.Objects.requireNonNull;


@Singleton
public class SystemManager {

  private static final Logger logger = LoggerFactory.getLogger(SystemManager.class);

  private final ProjectLoader projectLoader;
  private final SystemUserLoader systemUserLoader;
  private final JdbcExecutorLoader jdbcExecutorLoader;
  private final Props props;
  public static final Pattern PROXY_USER_PATTERN = Pattern.compile("^[A-Za-z0-9_,]+$");
  public static final Pattern USER_ID_PATTERN = Pattern.compile("^[0-9]+$");

  @Inject
  public SystemManager(
      final ProjectLoader loader,
      final StorageManager storageManager,
      final SystemUserLoader systemUserLoader,
      final JdbcExecutorLoader jdbcExecutorLoader,
      final Props props) {

    this.projectLoader = requireNonNull(loader);
    this.props = requireNonNull(props);
    this.systemUserLoader = requireNonNull(systemUserLoader);
    this.jdbcExecutorLoader = jdbcExecutorLoader;


  }

  public Props getProps() {
    return this.props;
  }


  public WebankUser getWebankUserByUserId(String userId) {
    WebankUser webankUser = null;
    try {
      webankUser = this.systemUserLoader.getWebankUserByUserId(userId);
    } catch (SystemUserManagerException e) {
      logger.error("Exception in query webank user by userId, caused by:{}", e);
    }

    return webankUser;
  }

  /**
   * 加载 SystemManager 中的异常信息等国际化资源
   *
   * @return
   */
  private Map<String, String> loadSystemManagerI18nData() {
    String languageType = LoadJsonUtils.getLanguageType();
    Map<String, String> dataMap;
    if ("zh_CN".equalsIgnoreCase(languageType)) {
      dataMap = LoadJsonUtils.transJson("/conf/azkaban-common-zh_CN.json",
          "azkaban.system.SystemManager");
    } else {
      dataMap = LoadJsonUtils.transJson("/conf/azkaban-common-en_US.json",
          "azkaban.system.SystemManager");
    }
    return dataMap;
  }

  public WebankUser getWebankUserByUserName(String userName) {
    WebankUser webankUser = null;
    try {
      webankUser = this.systemUserLoader.getWebankUserByUsername(userName);
    } catch (SystemUserManagerException e) {
      logger.error("Exception in query webank user by user name, caused by:{}", e);
    }

    return webankUser;
  }

  public List<WebankUser> findAllWebankUserList(final String searchName) {

    List<WebankUser> webankUserList = null;

    try {
      webankUserList = this.systemUserLoader.findAllWebankUserList(searchName);

    } catch (SystemUserManagerException e) {
      logger.error("Exception in query all webank user as user list, caused by:{}", e);
    }

    return webankUserList;


  }

  public List<WebankUser> findAllWebankUserPageList(final String searchName, final int pageNum, final int pageSize)
      throws SystemUserManagerException {

    List<WebankUser> webankUserList = null;

    try {
      webankUserList = this.systemUserLoader.findAllWebankUserPageList(searchName, pageNum, pageSize);

    } catch (SystemUserManagerException e) {
      logger.error("Exception in query all webank user as user page list, caused by:{}", e);
    }

    return webankUserList;


  }

  public List<WtssUser> findSystemUserPage(final String userName, final String fullName
      , final String departmentName, int start, int pageSize) throws SystemUserManagerException {
    List<WtssUser> wtssUserList = this.systemUserLoader.findSystemUserPage(userName, fullName, departmentName, start, pageSize);
    return wtssUserList;
  }

  public int getSystemUserTotal() throws SystemUserManagerException {
    return this.systemUserLoader.getWtssUserTotal();
  }

  public int getSystemUserTotal(String username) throws SystemUserManagerException {
    return this.systemUserLoader.getWtssUserTotal(username);
  }


  public int addSystemUser(final String userId, final String password, final int roleId, final String proxyUser,
      final int departmentId) throws SystemUserManagerException {

    Map<String, String> dataMap = loadSystemManagerI18nData();

    WtssUser wtssUser = new WtssUser();

    WebankUser webankUser = this.systemUserLoader.getWebankUserByUserId(userId);

    WebankDepartment webankDepartment = this.systemUserLoader.getWebankDepartmentByDpId(departmentId);

    if (null != webankUser) {
      wtssUser.setUserId(webankUser.userId);
      wtssUser.setUsername(webankUser.urn);
      wtssUser.setFullName(webankUser.fullName);

      if (null != webankDepartment) {
        wtssUser.setDepartmentId(webankDepartment.dpId);
        wtssUser.setDepartmentName(webankDepartment.dpChName);
      } else {
        wtssUser.setDepartmentId(webankUser.departmentId);
        wtssUser.setDepartmentName(webankUser.departmentName);
      }
      wtssUser.setEmail(webankUser.email);
    } else {
      wtssUser.setUserId("wtss_" + userId);
      wtssUser.setUsername(userId);
      wtssUser.setFullName(userId);
      if (null != webankDepartment) {
        wtssUser.setDepartmentId(webankDepartment.dpId);
        wtssUser.setDepartmentName(webankDepartment.dpChName);
      }
      wtssUser.setEmail("");
    }

    String encodePwd = MD5Utils.md5(MD5Utils.md5(password) + wtssUser.getUserId());
    wtssUser.setPassword(encodePwd);

    if (null != proxyUser && !"".equals(proxyUser)) {

      // 代理用户正则表达式增加下划线校验通过
      if (!PROXY_USER_PATTERN.matcher(proxyUser).matches()) {
        throw new SystemUserManagerException(dataMap.get("errorFormatProxy"));
      }
      wtssUser.setProxyUsers(proxyUser);
    } else {
      wtssUser.setProxyUsers(wtssUser.getUsername());
    }

    wtssUser.setRoleId(roleId);
    wtssUser.setUserType(WtssUser.UserType.ACTIVE.getUserTypeNum());
    wtssUser.setCreateTime(System.currentTimeMillis());
    wtssUser.setUpdateTime(System.currentTimeMillis());
    wtssUser.setModifyInfo("Normal");
    wtssUser.setModifyType("0");

    // 用户类型默默认为实名用户
    String userCategory;
    if (userId.startsWith("WTSS_")) {
      userCategory = "ops";
    } else if (userId.startsWith("hduser")) {
      userCategory = "system";
    } else {
      userCategory = "personal";
    }
    wtssUser.setUserCategory(userCategory);

    return this.systemUserLoader.addWtssUser(wtssUser);
  }

  public int getWebankUserTotal() throws SystemUserManagerException {
    return this.systemUserLoader.getWebankUserTotal();
  }

  public WtssUser getSystemUserById(final String userId) throws SystemUserManagerException {
    return this.systemUserLoader.getWtssUserByUserId(userId);
  }

  public int updateSystemUser(final WtssUser wtssUser, boolean synEsb)
      throws SystemUserManagerException {
    return this.systemUserLoader.updateWtssUser(wtssUser, synEsb);
  }

  public int updateSystemUserByName(final WtssUser wtssUser, boolean synEsb)
      throws SystemUserManagerException {
    return this.systemUserLoader.updateWtssUserByName(wtssUser, synEsb);
  }

  public int updateSystemUser(WtssUser wtssUser) throws SystemUserManagerException {
    return this.systemUserLoader.updateWtssUser(wtssUser);
  }

  /**
   * 更新系统用户
   * updated by zhangxi
   *
   * @param userId
   * @param password
   * @param roleId
   * @param proxyUser
   * @param departmentId
   * @return
   * @throws SystemUserManagerException
   */
  public int updateSystemUser(final String userId, final String password, final int roleId, final String proxyUser,
      final int departmentId) throws SystemUserManagerException {


    Map<String, String> dataMap = loadSystemManagerI18nData();
    // wtssUser 数据应该是先从查询原表中查询出
    WtssUser wtssUser = this.systemUserLoader.getWtssUserByUserId(userId);
    if (null != wtssUser) {

      // 判断是用户属于 webankUser 还是测试数据
      WebankUser webankUser = this.systemUserLoader.getWebankUserByUserId(userId);
      // 校验部门
      if (null != webankUser) {
        // 能从总数据表中查询到,说明是真实数据
        wtssUser.setUserId(webankUser.userId);
      } else {
        // 否则就是测试数据,就按照测试数据来设置就行
        wtssUser.setUserId(userId);
      }

      WebankDepartment webankDepartment = this.systemUserLoader.getWebankDepartmentByDpId(departmentId);
      wtssUser.setDepartmentId(departmentId);
      if (webankDepartment != null) {
        // 能从总数据表中查询到,说明是真实数据
        wtssUser.setDepartmentName(webankDepartment.dpChName);
      } else {
        // 否则就是测试数据,就按照测试数据来设置就行
        wtssUser.setDepartmentName(wtssUser.getDepartmentName());
      }

      // 因为前端回显的时候回显的是数据库加密之后的密码,所以修改密码的时候如果不改,
      // 则用数据库中加密的密码和现在的密码进行比较,如果相同,则不做修改,不相同则修改
      String passwordDb = wtssUser.getPassword();
      if (!passwordDb.equals(password)) {
        String encodePwd = MD5Utils.md5(MD5Utils.md5(password) + wtssUser.getUserId());
        wtssUser.setPassword(encodePwd);
      }

      if (null != proxyUser && !"".equals(proxyUser)) {
        if (!PROXY_USER_PATTERN.matcher(proxyUser).matches()) {
          throw new SystemUserManagerException(dataMap.get("errorFormatProxy"));
        }
        wtssUser.setProxyUsers(proxyUser);
      } else {
        wtssUser.setProxyUsers(wtssUser.getUsername());
      }
      wtssUser.setRoleId(roleId);
      wtssUser.setUserType(WtssUser.UserType.ACTIVE.getUserTypeNum());
      wtssUser.setUpdateTime(System.currentTimeMillis());

      String userCategory;
      if (USER_ID_PATTERN.matcher(userId).matches()) {
        userCategory = "personal";
      } else if (userId.startsWith("wtss_hduser")) {
        userCategory = "system";
      } else if (userId.startsWith("wtss_WTSS")) {
        userCategory = "ops";
      } else {
        // 针对测试数据的标识
        userCategory = "test";
      }

      wtssUser.setUserCategory(userCategory);

      return this.systemUserLoader.updateWtssUser(wtssUser);
    } else {
      throw new SystemUserManagerException("Unregistered User, Please register this user.");
    }

  }

  /**
   * 根据roleId查询WtssRole用户
   *
   * @param roleId
   * @return
   */
  public WtssRole getWtssRoleById(int roleId) {
    try {
      return this.systemUserLoader.getWtssRoleById(roleId);
    } catch (SystemUserManagerException e) {
      logger.error("get wtss user role by roleId fail, caused by:{}", e);
      return null;
    }

  }

  public String getUserPermission(int roleId) {

    String userPermString = "";
    try {
      //获取用户对应的角色
      final WtssRole wtssRole = this.systemUserLoader.getWtssRoleById(roleId);
      if (wtssRole != null) {
        List<WtssPermissions> wtssPermissionsList = this.systemUserLoader.getWtssPermissionsListByIds(wtssRole.getPermissionsIds());
        //获取角色对应的权限
        List<String> permissionsNameList = wtssPermissionsList.stream()
            .map(WtssPermissions::getPermissionsName).collect(Collectors.toList());
        userPermString = String.join(",", permissionsNameList);
      }
    } catch (SystemUserManagerException e) {
      logger.error("getUserPermission by roleId failed, caused by:{}", e);
    }
    return userPermString;
  }


  public List<WebankDepartment> findAllWebankDepartmentPageList(final String searchName, final int pageNum, final int pageSize)
      throws SystemUserManagerException {

    List<WebankDepartment> webankDepartmentList = null;

    try {
      webankDepartmentList = this.systemUserLoader.findAllWebankDepartmentPageList(searchName, pageNum, pageSize);

    } catch (SystemUserManagerException e) {
      logger.error("Exception in query webank department as page list, caused by:{}", e);
    }
    return webankDepartmentList;
  }

  public int getWebankDepartmentTotal() throws SystemUserManagerException {
    return this.systemUserLoader.getWebankDepartmentTotal();
  }

  public List<WebankDepartment> findAllWebankDepartmentList()
      throws SystemUserManagerException {
    try {
      return this.systemUserLoader.findAllWebankDepartmentList();
    } catch (SystemUserManagerException e) {
      throw new SystemUserManagerException(e);
    }
  }

  public WebankDepartment getWebankDepartmentByDpId(final int dpId)
      throws SystemUserManagerException {

    return this.systemUserLoader.getWebankDepartmentByDpId(dpId);

  }

  public List<WebankDepartment> findAllWebankDepartmentPageOrSearch(final String searchName, int pageNum
      , int pageSize)
      throws SystemUserManagerException {

    List<WebankDepartment> webankDepartmentList = null;

    try {
      webankDepartmentList = this.systemUserLoader.findAllWebankDepartmentPageOrSearch(searchName, pageNum, pageSize);

    } catch (SystemUserManagerException e) {
      throw new SystemUserManagerException(e);
    }
    return webankDepartmentList;
  }


  public int deleteSystemUser(final String userId) throws SystemUserManagerException {

    return this.systemUserLoader.deleteWtssUser(userId);

  }

  public WebankDepartment getParentDepartmentByPId(final int pid) throws SystemUserManagerException {
    return this.systemUserLoader.getParentDepartmentByPId(pid);
  }

  public WebankDepartment getDeparmentById(final int dpId) throws SystemUserManagerException {
    return this.systemUserLoader.getWebankDepartmentByDpId(dpId);
  }

  public int addDeparment(final int dpId, final int pid, final String dpName, final String dpChName,
      final int orgId, final String orgName, final String division, final Integer groupId,
      final Integer uploadFlag) throws SystemUserManagerException {

    WebankDepartment webankDepartment = new WebankDepartment();

    webankDepartment.setDpId((long) dpId);
    webankDepartment.setPid((long) pid);
    webankDepartment.setDpName(dpName);
    webankDepartment.setDpChName(dpChName);
    webankDepartment.setOrgId((long) orgId);
    webankDepartment.setOrgName(orgName);
    webankDepartment.setDivision(division);
    webankDepartment.setGroupId(groupId);
    webankDepartment.setUploadFlag(uploadFlag);


    return this.systemUserLoader.addDeparment(webankDepartment);
  }

  public int updateDeparment(final int dpId, final int pid, final String dpName, final String dpChName,
      final int orgId, final String orgName, final String division, final Integer groupId,
      final Integer uploadFlag
  ) throws SystemUserManagerException {

    WebankDepartment webankDepartment = new WebankDepartment();

    webankDepartment.setDpId((long) dpId);
    webankDepartment.setPid((long) pid);
    webankDepartment.setDpName(dpName);
    webankDepartment.setDpChName(dpChName);
    webankDepartment.setOrgId((long) orgId);
    webankDepartment.setOrgName(orgName);
    webankDepartment.setDivision(division);
    webankDepartment.setGroupId(groupId);
    // 新增字段 upload_flag, 用于区别部门是否有上传项目权限
    webankDepartment.setUploadFlag(uploadFlag);

    return this.systemUserLoader.updateDeparment(webankDepartment);
  }

  public int deleteDeparment(final int dpId) throws SystemUserManagerException {

    return this.systemUserLoader.deleteDeparment(dpId);
  }

  public int getWebankDepartmentTotal(final String searchterm) throws SystemUserManagerException {
    return this.systemUserLoader.getWebankDepartmentTotal(searchterm);
  }


  public String getUserDepartmentByUsername(final String userName) throws SystemUserManagerException {

    String department = "Submitter";
    WtssUser wtssUser = this.systemUserLoader.getWtssUserByUsername(userName);
    if (null != wtssUser) {
      String userDepartment = wtssUser.getDepartmentName();
      if (null != userDepartment && !userDepartment.isEmpty()) {
        return userDepartment;
      }
    }
    return department;
  }

  public List<DepartmentGroup> fetchAllDepartmentGroup() {
    List<DepartmentGroup> departmentGroups = new ArrayList<>();
    try {
      departmentGroups = jdbcExecutorLoader.fetchAllDepartmentGroup();
    } catch (ExecutorManagerException e) {
      logger.error("fetch All Department Group failed.");
    }
    return departmentGroups;
  }

  public boolean addDepartmentGroup(DepartmentGroup departmentGroup) {
    boolean ret = false;
    try {
      jdbcExecutorLoader.addDepartmentGroup(departmentGroup);
      ret = true;
    } catch (ExecutorManagerException e) {
      logger.error("add Department Group failed");
    }
    return ret;
  }

  public boolean checkGroupNameIsExist(DepartmentGroup departmentGroup) {
    try {
      return jdbcExecutorLoader.checkGroupNameIsExist(departmentGroup);
    } catch (ExecutorManagerException e) {
      logger.error("checkGroupNameIsExist: {}", e);
    }
    return false;
  }

  public boolean checkExecutorIsUsed(int executorId) {
    try {
      return jdbcExecutorLoader.checkExecutorIsUsed(executorId);
    } catch (ExecutorManagerException e) {
      logger.error("checkExecutorIsUsed: {}", e);
    }
    return false;
  }

  public boolean deleteDepartmentGroup(DepartmentGroup departmentGroup) {
    int ret = 0;
    try {
      ret = jdbcExecutorLoader.deleteDepartmentGroup(departmentGroup);
    } catch (ExecutorManagerException e) {
      logger.error("delete Department Group failed");
    }
    if (ret != 0) {
      return true;
    }
    return false;
  }

  public boolean updateDepartmentGroup(DepartmentGroup departmentGroup) {
    int ret = 0;
    try {
      ret = jdbcExecutorLoader.updateDepartmentGroup(departmentGroup);
    } catch (ExecutorManagerException e) {
      logger.error("update Department Group failed");
    }
    if (ret != 0) {
      return true;
    }
    return false;
  }

  public boolean groupIdIsExist(DepartmentGroup departmentGroup) {
    int count = -1;
    boolean ret = false;
    try {
      count = jdbcExecutorLoader.groupIdIsExist(departmentGroup);
    } catch (ExecutorManagerException e) {
      logger.warn("Failed to execute groupIdIsExist", e);
    }
    if (count > 0) {
      ret = true;
    }
    return ret;
  }

  public DepartmentGroup getDepartmentGroupById(Integer id) {
    DepartmentGroup departmentGroup = null;
    try {
      departmentGroup = jdbcExecutorLoader.fetchDepartmentGroupById(id);
    } catch (ExecutorManagerException e) {
      logger.error("get Department Group by id failed");
    }
    return departmentGroup;
  }

  public List<Executor> fetchAllExecutors() {
    List<Executor> executors = null;
    try {
      executors = jdbcExecutorLoader.fetchAllExecutors();
    } catch (ExecutorManagerException e) {
      logger.error("get Executor failed");
    }
    return executors;
  }

  /**
   * 根据部门编号查询wtssUser
   *
   * @param dpId
   * @return
   */
  public List<WtssUser> getSystemUserByDepartmentId(int dpId) {
    List<WtssUser> wtssUserList = null;
    try {
      wtssUserList = this.systemUserLoader.getSystemUserByDepartmentId(dpId);
    } catch (SystemUserManagerException e) {
      logger.error("get SystemUser By DepartmentId failed, caused by:{}", e);
    }
    return wtssUserList;
  }

  /**
   * 不分页查询查询所有变更用户
   *
   * @param modifyType 需要查询的变更类型
   * @return
   */
  public List<WtssUser> getModifySystemUser(String modifyType) {

    List<WtssUser> wtssUserList = null;
    try {
      wtssUserList = this.systemUserLoader.getModifySystemUser(modifyType);
    } catch (SystemUserManagerException e) {
      logger.error("get Modify SystemUser By modifyType failed, caused by:{}", e);
    }
    return wtssUserList;
  }

  /**
   * 分页查询查询所有变更用户
   *
   * @return
   */
  public List<WtssUser> getModifySystemUser(int start, int pageSize) {

    List<WtssUser> wtssUserList = null;
    try {
      wtssUserList = this.systemUserLoader.getModifySystemUser(start, pageSize);
    } catch (SystemUserManagerException e) {
      logger.error("get Modify SystemUser By modifyType failed, caused by:{}", e);
    }
    return wtssUserList;
  }

  /**
   * 根据条件查询变更用户
   *
   * @param searchterm
   * @param start
   * @param pageSize
   * @return
   */
  public List<WtssUser> getModifySystemUser(String searchterm, int start, int pageSize) {

    List<WtssUser> wtssUserList = null;
    try {
      wtssUserList = this.systemUserLoader.getModifySystemUser(searchterm, start, pageSize);
    } catch (SystemUserManagerException e) {
      logger.error("get Modify SystemUser By modifyType failed, caused by:{}", e);
    }
    return wtssUserList;
  }


  /**
   * 查询单个用户变更详情
   *
   * @param userId 用户Id
   * @return
   */
  public String getModifyInfoSystemUserById(String userId) {

    String modifyInfoById = "";
    try {
      modifyInfoById = this.systemUserLoader.getModifyInfoSystemUserById(userId);
    } catch (SystemUserManagerException e) {
      logger.error("get Single Modify SystemUser Info By userId failed, caused by:{}", e);
    }
    return modifyInfoById;
  }

  /**
   * 根据名字查询用户
   *
   * @param userName
   * @return
   */
  public WtssUser getSystemUserByUserName(String userName) throws SystemUserManagerException {
    return this.systemUserLoader.getSystemUserByUserName(userName);
  }

  /**
   * 分页查找部门运维人员
   *
   * @param start
   * @param pageSize
   * @return
   * @throws SystemUserManagerException
   */
  public List<DepartmentMaintainer> getDepartmentMaintainerList(int start, int pageSize) throws SystemUserManagerException {
    return this.systemUserLoader.getDepartmentMaintainerList(start, pageSize);
  }


  public int getDepartmentMaintainerTotal() throws SystemUserManagerException {
    return this.systemUserLoader.getDepartmentMaintainerTotal();
  }

  /**
   * 条件查询所有的部门运维人员
   *
   * @param searchterm
   * @param start
   * @param pageSize
   * @return
   * @throws SystemUserManagerException
   */
  public List<DepartmentMaintainer> getDepartmentMaintainerList(String searchterm, int start, int pageSize) throws SystemUserManagerException {
    return this.systemUserLoader.getDepartmentMaintainerList(searchterm, start, pageSize);
  }

  public int addDepartmentMaintainer(long departmentId, String departmentName, String userName) throws SystemUserManagerException {
    return this.systemUserLoader.addDepartmentMaintainer(departmentId, departmentName, userName);
  }

  /**
   * 查找部门id
   *
   * @param departmentId
   * @return
   * @throws SystemUserManagerException
   */
  public DepartmentMaintainer getDepMaintainerByDepId(long departmentId) throws SystemUserManagerException {
    return this.systemUserLoader.getDepMaintainerByDepId(departmentId);
  }

  public boolean checkDepartmentMaintainer(Long dpId, String username) throws RuntimeException{
    DepartmentMaintainer departmentMaintainer = null;
    try {
      departmentMaintainer = this.systemUserLoader.getDepMaintainerByDepId(dpId);
    } catch (SystemUserManagerException e){
      logger.error("get maintainer by dpId failed.", e);
    }
    if(departmentMaintainer == null){
      logger.warn("{} is not the maintenance personnel.", username);
      return false;
    }
    String opsUser = departmentMaintainer.getOpsUser();
    if(StringUtils.isEmpty(opsUser) || !opsUser.contains(username)){
      logger.warn("{} is not the maintenance personnel.", username);
      return false;
    }
    return true;
  }

  public int updateDepartmentMaintainer(long departmentId, String departmentName, String depMaintainer) throws SystemUserManagerException {
    return this.systemUserLoader.updateDepartmentMaintainer(departmentId, departmentName, depMaintainer);
  }

  public int deleteDepartmentMaintainer(Integer departmentId) throws SystemUserManagerException {
    return this.systemUserLoader.deleteDepartmentMaintainer(departmentId);
  }

  public List<Integer> getDepartmentMaintainerDepListByUserName(String loginUserName) {
    List<Integer> departmentList = new ArrayList<>();
    try {
      departmentList = this.systemUserLoader.getDepartmentMaintainerDepListByUserName(loginUserName);
    } catch (SystemUserManagerException e) {
      logger.error("fetch department_id failed", e);
    }
    return departmentList;
  }

  public boolean isDepartmentMaintainer(User user) {
    return CollectionUtils.isNotEmpty(getDepartmentMaintainerDepListByUserName(user.getUserId()));
  }

  public List<Integer> getMaintainedProjects(User user, int active) {
    try {
      return this.systemUserLoader.getMaintainedProjects(user.getUserId(), active);
    } catch (SystemUserManagerException e ) {
      return Collections.emptyList();
    }
  }

  public List<Integer> getMaintainedProjectsByDepList(List<Integer> departmentList, int active) {
    try {
      if (CollectionUtils.isNotEmpty(departmentList)) {
        return this.systemUserLoader.getMaintainedProjectsByDepList(departmentList, active);
      } else {
        return Collections.emptyList();
      }
    } catch (SystemUserManagerException e ) {
      return Collections.emptyList();
    }
  }

  public boolean isMaintainedProject(Project project, List<Integer> projectIds) {
    return projectIds.contains(project.getId());
  }

  public Set<String> getMaintainedDeptUser(String username) throws SystemUserManagerException {
    return this.systemUserLoader.getMaintainedDeptUser(username);
  }

  public long getFinalScheduleTime(long triggerInitTime) {
    return this.jdbcExecutorLoader.getFinalScheduleTime(triggerInitTime);
  }

  public boolean executorOffline(int executorid) {
    int res = 0;
    try {
      res = jdbcExecutorLoader.executorOffline(executorid);
    } catch (ExecutorManagerException e) {
      logger.error("executor Offline failed", e);
    }
    return res != 0;
  }

  public boolean executorOnline(int executorid) {
    int res = 0;
    try {
      res = jdbcExecutorLoader.executorOnline(executorid);
    } catch (ExecutorManagerException e) {
      logger.error("executor Online failed", e);
    }
    return res != 0;
  }

  public boolean checkIsOnline(int executorid) {
    boolean res = false;
    try {
      res = jdbcExecutorLoader.checkIsOnline(executorid);
    } catch (ExecutorManagerException e) {
      logger.error("executor Online check failed", e);
    }
    return res ;
  }
}
