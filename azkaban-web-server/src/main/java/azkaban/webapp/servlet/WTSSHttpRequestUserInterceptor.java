package azkaban.webapp.servlet;

import azkaban.server.session.Session;
import azkaban.server.session.SessionCache;
import azkaban.user.User;
import azkaban.user.UserManagerException;
import azkaban.utils.StringUtils;
import azkaban.utils.WebUtils;
import azkaban.webapp.AzkabanWebServer;
import com.webank.wedatasphere.dss.standard.app.sso.plugin.filter.HttpRequestUserInterceptor;
import com.webank.wedatasphere.schedulis.common.user.SystemUserManager;
import org.apache.commons.lang.ArrayUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletRequestWrapper;
import java.util.HashMap;
import java.util.UUID;

public class WTSSHttpRequestUserInterceptor implements HttpRequestUserInterceptor {

    private static final Logger logger = LoggerFactory.getLogger(WTSSHttpRequestUserInterceptor.class.getName());

    private AzkabanWebServer application;

    public WTSSHttpRequestUserInterceptor(AzkabanWebServer application) {
        this.application = application;
    }

    @Override
    public HttpServletRequest addUserToRequest(String s, HttpServletRequest httpServletRequest) {
        httpServletRequest.getSession().setAttribute("username", s);
        return createDssSession(s, httpServletRequest);
    }

    private HttpServletRequest createDssSession(final String username, final HttpServletRequest req) {

        logger.info(username + "enters the createDssSession method");

        final HashMap<String, String> headers = new HashMap<>();
        headers.put(WebUtils.X_FORWARDED_FOR_HEADER, req.getHeader(WebUtils.X_FORWARDED_FOR_HEADER.toLowerCase()));
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
                user = systemUserManager.getUser(username);
            } catch (UserManagerException e) {
                logger.error("get user error, caused by ", e);
            }
            SessionCache sessionCache = this.application.getSessionCache();
            Session sessionByUsername = sessionCache.getSessionByUsername(username);
            if (sessionByUsername == null) {
                final String randomUID = UUID.randomUUID().toString();
                session = new Session(randomUID, user, ip);
                sessionCache.addSession(session);
            } else {
                session = sessionByUsername;
            }
        }
        logger.info("dss exists session id {} for user {}.", session.getSessionId(), username);
        final Cookie cookie = new Cookie(LoginAbstractAzkabanServlet.SESSION_ID_NAME, session.getSessionId());
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

    @Override
    public boolean isUserExistInSession(HttpServletRequest httpServletRequest) {
        String sessionId = "";
        Cookie[] cookies = httpServletRequest.getCookies();
        if (cookies != null) {
            for (Cookie cookie : cookies) {
                if (cookie.getName().equals(LoginAbstractAzkabanServlet.SESSION_ID_NAME)) {
                    sessionId = cookie.getValue();
                }
            }
        }
        logger.info("dss sessionId {} ", sessionId);
        if (sessionId != null && !sessionId.isEmpty()) {
            return true;
        }
        return false;
    }

    @Override
    public String getUser(HttpServletRequest httpServletRequest) {
        return httpServletRequest.getSession().getAttribute("username").toString();
    }
}
