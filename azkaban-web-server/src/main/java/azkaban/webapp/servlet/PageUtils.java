package azkaban.webapp.servlet;

import azkaban.server.session.Session;
import azkaban.user.Permission;
import azkaban.user.User;
import azkaban.user.UserUtils;


public final class PageUtils {

  private PageUtils() {

  }

  /**
   * Method hides the upload button for regular users from relevant pages when the property
   * "lockdown.upload.projects" is set. The button is displayed for admin users and users with
   * upload permissions.
   */
  public static void hideUploadButtonWhenNeeded(final Page page, final Session session,
                                                final Boolean lockdownUploadProjects) {
    final User user = session.getUser();

    if (lockdownUploadProjects && !UserUtils.hasPermissionforAction(user, Permission.Type.UPLOADPROJECTS)) {
      page.add("hideUploadProject", true);
    }
  }
}
