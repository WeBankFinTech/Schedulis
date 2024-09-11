package azkaban.user;

import azkaban.ServiceProvider;
import azkaban.system.JdbcSystemUserImpl;
import azkaban.system.SystemUserLoader;
import azkaban.system.SystemUserManagerException;
import azkaban.system.entity.DepartmentMaintainer;
import azkaban.system.entity.WtssPermissions;
import azkaban.system.entity.WtssRole;
import azkaban.system.entity.WtssUser;
import azkaban.utils.LdapCheckCenter;
import azkaban.utils.Props;
import bsp.encrypt.EncryptUtil;
import org.apache.commons.lang.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import java.io.IOException;
import java.util.*;

/**
 * Created by kirkzhou on 7/11/18.
 */
public class SystemUserManager implements UserManager  {

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

    return validateUserPassword(username, password, UserType.PERSONAL, true);
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

  @Override
  public User validateNonRealNameUser(String username, String password, String normalUserName, String normalPassword, UserType type) throws UserManagerException, IOException {
    //运维用户名密码判空
    if (username == null || username.trim().isEmpty()) {
      throw new UserManagerException("Empty " + type + " User Name.");
    }

    User user = validateUserPassword(username, password, type, true);

    //普通用户名密码判空
    if (normalUserName == null || normalUserName.trim().isEmpty()) {
      throw new UserManagerException("Empty Normal User Name.");
    }
    //普通用户密码解码并判空
    try {
      String passwordPrivateKey = props.getString("password.private.key");
      normalPassword = EncryptUtil.decrypt(passwordPrivateKey, normalPassword);
    } catch (Exception e) {
      logger.error("decrypt normal user password failed.", e);
      throw new UserManagerException("decrypt normal user password failed.");
    }
    if (normalPassword == null || normalPassword.trim().isEmpty()) {
      throw new UserManagerException("Empty Normal User Password.");
    }

    //校验普通用户密码是否正确
    validateUserPassword(normalUserName, normalPassword, UserType.PERSONAL, false);
    //校验实名用户是否为运维人员，并且与运维用户同部门
      if (UserType.OPS == type) {
      validDepartmentOpsUser(username, normalUserName);
    }

    //校验通过，设置普通用户
    user.setNormalUser(normalUserName);
    return user;
  }

  @Override
  public void validDepartmentOpsUser(String username, String normalUserName) throws UserManagerException {
    if (props.getBoolean("wtss.opsuser.department.check", true)) {
      try {
        WtssUser opsUser = systemUserLoader.getWtssUserByUsername(username);
        if (opsUser == null) {
          throw new UserManagerException("Unknown Ops User.");
        }
        //查询运维用户所属部门运维人员
        DepartmentMaintainer maintainer = systemUserLoader.getDepMaintainerByDepId(opsUser.getDepartmentId());
        if (maintainer == null || StringUtils.isEmpty(maintainer.getOpsUser())) {
          throw new UserManagerException("opsuser's department has not maintainer.");
        }
        //校验该部门运维人员是否包含实名用户
        String[] opsUserArr = maintainer.getOpsUser().split(",|，");
        boolean isMaintainer = false;
        for (int i = 0; i < opsUserArr.length; i++) {
          if (normalUserName.equals(opsUserArr[i])) {
            isMaintainer = true;
            break;
          }
        }
        if (!isMaintainer) {
          throw new UserManagerException("the normal user is not maintainer of opsuser's department.");
        }

      } catch (Exception e) {
        if (e instanceof UserManagerException) {
          throw new UserManagerException(e.getMessage(), e);
        } else {
          throw new UserManagerException("Check Normal User Department Error.");
        }
      }
    }
  }

  /**
   * 校验用户名密码
   * @param UserName 用户名
   * @param Password 密码
   * @param prefix 提示前缀
   * @param isGetAuth 是否需要赋权限
   * @return
   * @throws UserManagerException
   */
  private User validateUserPassword(String UserName, String Password, UserType prefix, boolean isGetAuth) throws UserManagerException {
    synchronized (this) {
      try {
        //校验用户是否存在
        WtssUser wtssUser = systemUserLoader.getWtssUserByUsername(UserName);
        if (null != wtssUser) {
          wtssUser.setPassword(Password);
        } else {
          throw new UserManagerException("Unknown " + prefix + " User.");
        }

        if (UserType.SYSTEM == prefix && !UserName.startsWith("hduser") && !UserName.startsWith("shduser")) {
          throw new UserManagerException("sys user name must start with hduser or shduser");
        }

        //校验用户密码
        Map<String, Object> ldapCheckLoginMap = LdapCheckCenter.checkLogin(props, UserName,
            Password);
        if (UserType.OPS != prefix && ldapCheckLoginMap.containsKey("error")
            && systemUserLoader.getWtssUserByUsernameAndPassword(wtssUser) == null) {
          throw new UserManagerException(
              ldapCheckLoginMap.isEmpty() ? "Error" + prefix + "User Name Or Password." :
                  "LDAP error: " + "(" + prefix + " user)" + ldapCheckLoginMap.get("error"));
        }

        //赋权限
        User user = null;
        if (isGetAuth) {
          user = new User(wtssUser.getUsername());
          initUserAuthority(wtssUser, user);
        }
        return user;
      } catch (Exception e) {
        logger.error("Login error！ caused by {}：", e);
        if (e instanceof UserManagerException) {
          throw new UserManagerException(e.getMessage(), e);
        } else {
          throw new UserManagerException("Error" + prefix + "User Name Or Password.");
        }
      }
    }
  }

}
