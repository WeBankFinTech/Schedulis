package azkaban.webapp.servlet;

import azkaban.server.AbstractAzkabanServer;
import azkaban.server.session.Session;
import azkaban.server.session.SessionCache;
import azkaban.user.SystemUserManager;
import azkaban.user.User;
import azkaban.user.UserManagerException;
import azkaban.utils.StringUtils;
import azkaban.utils.WebUtils;


import java.util.HashMap;
import java.util.UUID;
import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletRequestWrapper;
import org.apache.commons.lang.ArrayUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author lebronwang
 * @version 1.0
 * @date 2021/9/9
 */
public class WTSSHttpRequestUserInterceptor implements HttpRequestProxyUserInterceptor
{

    private static final Logger logger = LoggerFactory
            .getLogger(WTSSHttpRequestUserInterceptor.class.getName());

    private AbstractAzkabanServer application;

    public WTSSHttpRequestUserInterceptor(AbstractAzkabanServer application) {
        this.application = application;
    }

    @Override
    public boolean isUserExistInSession(HttpServletRequest httpServletRequest) {
        String sessionId = "";
        Cookie[] cookies = httpServletRequest.getCookies();
        if (cookies != null) {
            for (Cookie cookie : cookies) {
                if (cookie.getName().equals(AbstractLoginAzkabanServlet.SESSION_ID_NAME)) {
                    sessionId = cookie.getValue();
                }
            }
        }
        logger.info("dss sessionId {} ", sessionId);

        SessionCache sessionCache = this.application.getSessionCache();
        return sessionId != null && !sessionId.isEmpty() && sessionCache.getSession(sessionId) != null;
    }

    @Override
    public String getUser(HttpServletRequest httpServletRequest) {
        return httpServletRequest.getSession().getAttribute("username").toString();
    }

    @Override
    public HttpServletRequest addUserToRequest(String user, String opsUser,
                                               HttpServletRequest httpServletRequest) {
        if (user.equals(opsUser)) {
            httpServletRequest.getSession().setAttribute("username", user);
        } else {
            httpServletRequest.getSession()
                    .setAttribute("username", ProxyUserSSOUtils.setUserAndProxyUser(user, opsUser));
        }
        return createDssSession(user, opsUser, httpServletRequest);
    }

    private HttpServletRequest createDssSession(final String username, String opsUser,
                                                final HttpServletRequest req) {

        logger.info(username + "enters the createDssSession method");

        logger.info("The request(" + req + ") is api/v1/redirect or dssurl");

        final HashMap<String, String> headers = new HashMap<>();
        headers.put(WebUtils.X_FORWARDED_FOR_HEADER,
                req.getHeader(WebUtils.X_FORWARDED_FOR_HEADER.toLowerCase()));
        final WebUtils utils = new WebUtils();
        final String ip = utils.getRealClientIpAddr(headers, req.getRemoteAddr());
        Session session = null;
        try {
            if (!StringUtils.isFromBrowser(req.getHeader("User-Agent"))) {
                logger.info("not browser.");
                session = this.application.getSessionCache().getSessionByUsername(username);
            }
        } catch (final Exception e) {
            logger.error("no dss super user", e);
        }
        if (session == null) {
            SystemUserManager systemUserManager = new SystemUserManager();
            User user = null;
            try {
                user = systemUserManager.getUser(opsUser);
            } catch (UserManagerException e) {
                logger.error("get user error, caused by ", e);
            }
            if (user != null) {
                user.setNormalUser(username);
            }
            SessionCache sessionCache = this.application.getSessionCache();
            Session sessionByUsername = sessionCache.getSessionByUsername(opsUser);
            if (sessionByUsername == null) {
                final String randomUID = UUID.randomUUID().toString();
                session = new Session(randomUID, user, ip);
                sessionCache.addSession(session);
            } else {
                session = sessionByUsername;
            }
        }
        logger.info("dss exists session.id {} for normalUser {} with opsUser {}.",
                session.getSessionId(), username, opsUser);
        final Cookie cookie = new Cookie(AbstractLoginAzkabanServlet.SESSION_ID_NAME,
                session.getSessionId());
        cookie.setPath("/");
        HttpServletRequestWrapper httpServletRequestWrapper = new HttpServletRequestWrapper(req) {
            @Override
            public Cookie[] getCookies() {
                final Cookie[] cookies = (Cookie[]) ArrayUtils.add(super.getCookies(), cookie);
                return cookies;
            }
        };
        logger.info("dss new session.id {} for user {}.", session.getSessionId(), username);
        return httpServletRequestWrapper;
    }
}
