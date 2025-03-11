package azkaban.webapp.servlet;

import javax.servlet.http.HttpServletRequest;

public interface UserInterceptor {
    boolean isUserExistInSession(HttpServletRequest var1);

    String getUser(HttpServletRequest var1);
}
