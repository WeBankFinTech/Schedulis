package azkaban.user;

import azkaban.system.entity.WtssUser;
import org.apache.commons.collections4.CollectionUtils;

public final class UserUtils {

  private UserUtils() {

  }

  /**
   * @return - Returns true if the given user is an ADMIN, or if user has the required permission
   * for the action requested.
   */
  public static boolean hasPermissionforAction(final User user, final Permission.Type type) {
    for (final String roleName : user.getRoles()) {
      final Role role = user.getRoleMap().get(roleName);
      final Permission perm = role.getPermission();
      if (perm.isPermissionSet(Permission.Type.ADMIN) || perm.isPermissionSet(type)) {
        return true;
      }
    }

    return false;
  }

  public static boolean checkPermission(final User user, Permission.Type targetType) throws RuntimeException{
    if(CollectionUtils.isEmpty(user.getRoles())){
      return false;
    }
    for (final String roleName : user.getRoles()) {
      final Role role = user.getRoleMap().get(roleName);
      final Permission perm = role.getPermission();
      if (perm.isPermissionSet(targetType)) {
        return true;
      }
    }
    return false;
  }

  /**
   * 1.运维用户只能转交给运维用户和系统管理员
   * 2.非运维用户只能转交给非运维用户和系统管理员
   * @param oldUser
   * @param newUser
   */
  public static void checkChangeCreateUserPermission(WtssUser oldUser, WtssUser newUser){
    String oldUserCategory = oldUser.getUserCategory();
    String newUserCategory = newUser.getUserCategory();
    int roleId = newUser.getRoleId();
    if(newUserCategory == null){
      throw new RuntimeException(String.format("%s, unknown user type.", newUser.getUsername()));
    }
    if(oldUserCategory == null ) {
      throw new RuntimeException(String.format("%s, unknown user type.", oldUser.getUsername()));
    }
    if("ops".equalsIgnoreCase(oldUserCategory)){
      //运维用户只能转交给运维用户和系统管理员
      if(!("ops".equalsIgnoreCase(newUserCategory) || roleId == 1)){
        throw new RuntimeException("The project of operation and maintenance user can only be transferred to operation and maintenance user and system administrator.");
      }
    } else {
      // 非运维用户只能转交给非运维用户和系统管理员
      if(!("personal".equalsIgnoreCase(newUserCategory) || "system"
          .equalsIgnoreCase(newUserCategory) || roleId == 1)){
        throw new RuntimeException("Projects of non operation and maintenance users can only be transferred to non operation and maintenance users and system administrators.");
      }
    }
  }
}
