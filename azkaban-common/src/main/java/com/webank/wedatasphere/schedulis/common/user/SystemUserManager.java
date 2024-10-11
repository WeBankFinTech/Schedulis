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

package com.webank.wedatasphere.schedulis.common.user;

import azkaban.ServiceProvider;
import azkaban.user.*;
import azkaban.utils.Props;
import com.webank.wedatasphere.schedulis.common.system.JdbcSystemUserImpl;
import com.webank.wedatasphere.schedulis.common.system.SystemUserLoader;
import com.webank.wedatasphere.schedulis.common.system.SystemUserManagerException;
import com.webank.wedatasphere.schedulis.common.system.entity.WtssPermissions;
import com.webank.wedatasphere.schedulis.common.system.entity.WtssRole;
import com.webank.wedatasphere.schedulis.common.system.entity.WtssUser;
import com.webank.wedatasphere.schedulis.common.utils.LdapCheckCenter;
import org.apache.commons.lang.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import java.util.*;

public class SystemUserManager implements UserManager {

  private static final Logger logger = LoggerFactory.getLogger(SystemUserManager.class.getName());

  private HashMap<String, User> users;
  private HashMap<String, String> userPassword;
  private HashMap<String, Role> roles;
  private HashMap<String, Set<String>> groupRoles;
  private HashMap<String, Set<String>> proxyUserMap;
  private Props props;

  @Inject
  private SystemUserLoader systemUserLoader;

  @Inject
  public SystemUserManager(final Props props){
    this.props = props;
    systemUserLoader = ServiceProvider.SERVICE_PROVIDER.getInstance(JdbcSystemUserImpl.class);
  }

  public SystemUserManager() {
    systemUserLoader = ServiceProvider.SERVICE_PROVIDER.getInstance(JdbcSystemUserImpl.class);
  }

  @Override
  public User getUser(String username, String password) throws UserManagerException {

    if (username == null || username.trim().isEmpty()) {
      throw new UserManagerException("Empty User Name.");
    } else if (password == null || password.trim().isEmpty()) {
      throw new UserManagerException("Empty Password.");
    }

    synchronized (this) {
      try {

        WtssUser wtssUser = this.systemUserLoader.getWtssUserByUsername(username);

        if (null != wtssUser){
          wtssUser.setPassword(password);
        } else {
          throw new UserManagerException("Unknown User.");
        }

        boolean ldapSwitch = props.getBoolean("ladp.switch", false);
        WtssUser wtssUserByUsernameAndPassword = systemUserLoader
            .getWtssUserByUsernameAndPassword(wtssUser);

        if (!ldapSwitch) {
          if (wtssUserByUsernameAndPassword == null) {
            throw new UserManagerException("Error User Name Or Password.");
          }
        } else {
          if (!LdapCheckCenter.checkLogin(props, username, password) && wtssUserByUsernameAndPassword == null) {
            throw new UserManagerException("Error User Name Or Password.");
          }
        }

        User user = new User(wtssUser.getUsername());
        initUserAuthority(wtssUser, user);

        return user;
      } catch (Exception e) {
        logger.error("Login error！ caused by {}：" , e);
        if (e instanceof UserManagerException) {
          throw new UserManagerException(e.getMessage());
        } else {
          throw new UserManagerException("Error User Name Or Password.");
        }
      }
    }
  }

  /**
   * 重载getUser方法，放行超级用户登录
   * @param username daili
   * @param password
   * @param superUser
   * @return
   * @throws UserManagerException
   */
  public User getUser(String username, String password, String superUser) throws UserManagerException {
    if (StringUtils.isBlank(username)){
      logger.error("Login by  superuser, username is null");
      throw new UserManagerException("superUser proxy login, username is null");
    }
    User user = null;
    synchronized (this) {
      try {

        WtssUser wtssUser = this.systemUserLoader.getWtssUserByUsername(username);

        if (null != wtssUser){
          user = new User(wtssUser.getUsername());
          wtssUser.setPassword(password);
        } else {
          throw new UserManagerException("User does not exists");
        }

        initUserAuthority(wtssUser, user);

      } catch (Exception e) {
        logger.error("Login error！cased by {}：", e);
        throw new UserManagerException("Error User Name Or Password.");
      }
    }
    return user;
  }

  public User getUser(String username) throws UserManagerException {

    if (username == null || username.trim().isEmpty()) {
      throw new UserManagerException("Empty User Name.");
    }

    User user = null;
    synchronized (this) {
      try {
        WtssUser wtssUser = this.systemUserLoader.getWtssUserByUsername(username);

        if (null != wtssUser){
          user = new User(wtssUser.getUsername());
          wtssUser.setPassword("");
        } else {
          throw new UserManagerException("Unknown User.");
        }
        initUserAuthority(wtssUser, user);
      } catch (Exception e) {
        throw new UserManagerException("Error User Name.");
      }
    }

    return user;
  }

  private void initUserAuthority(final WtssUser wtssUser, final User user){

    final HashMap<String, User> users = new HashMap<>();
    final HashMap<String, String> userPassword = new HashMap<>();
    final HashMap<String, Role> roles = new HashMap<>();
    final HashMap<String, Set<String>> groupRoles =
        new HashMap<>();
    final HashMap<String, Set<String>> proxyUserMap =
        new HashMap<>();

    try {

      users.put(wtssUser.getUsername(), user);
      //获取用户对应的角色
      final WtssRole wtssRole = this.systemUserLoader.getWtssRoleById(wtssUser.getRoleId());
      //获取角色对应的权限
      final List<WtssPermissions> wtssPermissionsList = this.systemUserLoader
          .getWtssPermissionsListByIds(wtssRole.getPermissionsIds());

      final List<String> permissionsNameList = new ArrayList<>();
      for(WtssPermissions wtssPermissions : wtssPermissionsList){
        permissionsNameList.add(wtssPermissions.getPermissionsName());
      }
      //WtssPermissions转换成自带的权限对象
      final Permission perm = new Permission();
      for (final String permName : permissionsNameList) {
        try {
          final Permission.Type type = Permission.Type.valueOf(permName);
          perm.addPermission(type);
        } catch (final IllegalArgumentException e) {
          logger.error("添加权限 " + permName + "错误. 权限不存在.", e);
        }
      }
      //组装原系统角色对象
      Role role = new Role(wtssRole.getRoleName(), perm);
      user.addRole(role.getName());
      roles.put(wtssRole.getRoleName(), role);

      String proxyUsers = wtssUser.getProxyUsers();
      //空字符串不做处理
      if(StringUtils.isNotEmpty(proxyUsers)){
        final String[] proxySplit = proxyUsers.split("\\s*,\\s*");
        for (final String proxyUser : proxySplit) {
          Set<String> proxySet = new HashSet<>();
          //把代理用户添加到User对象中
          user.addProxyUser(proxyUser);
          proxySet.add(proxyUser);
          proxyUserMap.put(wtssUser.getUsername(), proxySet);
        }
      }
      //用户权限保存在用户对象中 防止线程问题
      user.setRoleMap(roles);

    } catch (SystemUserManagerException e) {
      logger.error("用户权限组装失败！", e);
    }

    // Synchronize the swap. Similarly, the gets are synchronized to this.
    synchronized (this) {
      this.users = users;
      this.userPassword = userPassword;
      this.roles = roles;
      this.proxyUserMap = proxyUserMap;
      this.groupRoles = groupRoles;
    }
  }

  @Override
  public boolean validateUser(String username) {
    return this.users.containsKey(username);
  }

  @Override
  public boolean validateGroup(String group) {
    return true;
  }

  @Override
  public Role getRole(String roleName) {
    return this.roles.get(roleName);
  }

  @Override
  public boolean validateProxyUser(String proxyUser, User realUser) {
    if (this.proxyUserMap.containsKey(realUser.getUserId())
        && this.proxyUserMap.get(realUser.getUserId()).contains(proxyUser)) {
      return true;
    } else {
      return false;
    }
  }

}
