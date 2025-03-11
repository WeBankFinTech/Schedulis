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

package azkaban.webapp.servlet;

import static azkaban.Constants.ConfigurationKeys.ENABLE_APPID_LOGIN;
import static azkaban.Constants.WTSS_PUBLIC_KEY;
import static azkaban.ServiceProvider.SERVICE_PROVIDER;

import azkaban.ServiceProvider;
import azkaban.i18n.utils.LoadJsonUtils;
import azkaban.project.Project;
import azkaban.scheduler.ScheduleManagerException;
import azkaban.server.AbstractAzkabanServer;
import azkaban.server.session.Session;
import azkaban.server.session.SessionCache;
import azkaban.system.JdbcSystemUserImpl;
import azkaban.system.SystemManager;
import azkaban.system.SystemUserLoader;
import azkaban.system.SystemUserManagerException;
import azkaban.system.credential.CredentialServiceImpl;
import azkaban.system.dto.CredentialDto;
import azkaban.system.entity.WtssUser;
import azkaban.trigger.TriggerManagerException;
import azkaban.user.Permission;
import azkaban.user.Role;
import azkaban.user.SystemUserManager;
import azkaban.user.User;
import azkaban.user.UserManager;
import azkaban.user.UserManagerException;
import azkaban.user.UserType;
import azkaban.utils.Props;
import azkaban.utils.RSAUtils;
import azkaban.utils.StringUtils;
import azkaban.utils.WebUtils;
import azkaban.utils.XSSFilterUtils;
import azkaban.webapp.AzkabanWebServer;
import azkaban.webapp.WebMetrics;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.Writer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.fileupload.servlet.ServletFileUpload;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang.ObjectUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Abstract Servlet that handles auto login when the session hasn't been verified.
 */
public abstract class AbstractLoginAzkabanServlet extends AbstractAzkabanServlet {

  private static final long serialVersionUID = 1L;

  private static final Logger logger = LoggerFactory.getLogger("LoginAccessLogger");
  public static final String SESSION_ID_NAME = "azkaban.browser.session.id";

  public static final String REQUEST_WITHOUTSESSION = "azkaban.request.withoutsession";

  private static final int DEFAULT_UPLOAD_DISK_SPOOL_SIZE = 20 * 1024 * 1024;

  private static final HashMap<String, String> CONTEXT_TYPE = new HashMap<>();

  private AbstractAzkabanServer application;

  /*private static final List<String> NOT_CHECK_SIZE_AJAX_LIST = Arrays
      .asList("fetchProjectLogs", "fetchExecJobLogs", "fetchExecFlowLogs", "");*/
  private List<String> notCheckSizeAjaxList = new ArrayList<>();

  private List<String> requestWithoutSessionList = new ArrayList<>();

  private int ajaxMaxSize = 500;

  static {
    CONTEXT_TYPE.put(".js", "application/javascript");
    CONTEXT_TYPE.put(".css", "text/css");
    CONTEXT_TYPE.put(".png", "image/png");
    CONTEXT_TYPE.put(".jpeg", "image/jpeg");
    CONTEXT_TYPE.put(".gif", "image/gif");
    CONTEXT_TYPE.put(".jpg", "image/jpeg");
    CONTEXT_TYPE.put(".eot", "application/vnd.ms-fontobject");
    CONTEXT_TYPE.put(".svg", "image/svg+xml");
    CONTEXT_TYPE.put(".ttf", "application/octet-stream");
    CONTEXT_TYPE.put(".woff", "application/x-font-woff");
  }

  private final WebMetrics webMetrics = SERVICE_PROVIDER.getInstance(WebMetrics.class);
  private File webResourceDirectory = null;
  private MultipartParser multipartParser;
  private boolean shouldLogRawUserAgent = false;

  /**
   * 使用 Guava Cache 缓存登录鉴权信息
   */
  private Cache<String, CredentialDto> credentialCache;

  @Override
  public void init(final ServletConfig config) throws ServletException {
    super.init(config);

    this.multipartParser = new MultipartParser(DEFAULT_UPLOAD_DISK_SPOOL_SIZE);

    this.shouldLogRawUserAgent = getApplication().getServerProps()
            .getBoolean("accesslog.raw.useragent", false);
    //获取Web Server实体对象
    this.application = SERVICE_PROVIDER.getInstance(AzkabanWebServer.class);
    this.ajaxMaxSize = this.application.getServerProps().getInt("ajax.query.max.size", 500);

    this.notCheckSizeAjaxList = this.application.getServerProps()
            .getStringList("request.size.limit.whitelist",
                    new ArrayList<>());

    this.requestWithoutSessionList = this.application.getServerProps().getStringList(REQUEST_WITHOUTSESSION,
            Arrays.asList("executeFlowCycleFromExecutor", "reloadWebData", "alertMissedSchedules", "reloadExecutors","recordRunningFlow"));

    this.credentialCache = CacheBuilder.newBuilder()
        .expireAfterWrite(10, TimeUnit.MINUTES)
        .build();
  }

  public void setResourceDirectory(final File file) {
    this.webResourceDirectory = file;
  }

  @Override
  protected void doGet(final HttpServletRequest req, final HttpServletResponse resp)
          throws ServletException, IOException {
    final Props props = this.application.getServerProps();
    String nginxSSL = props.getString("nginx.ssl.module", "");
    if("open".equals(nginxSSL)) {
      String referer = req.getHeader("Referer");
      String refererUrl = props.getString("azkaban.header.referer.url", "");
      // 判断 Referer 是否以 bank.example 开头
      if ((null != referer) && !(referer.trim().startsWith(refererUrl))) {
        resp.sendRedirect("/error");
      }
    }
    this.webMetrics.markWebGetCall();
    // Set session id
    final Session session = getSessionFromRequest(req);
    logRequest(req, session);
    if (this.validCsrf(req, resp, session, true, null)) {
      return;
    }
    if (hasParam(req, "logout")) {
      resp.sendRedirect(req.getContextPath());
      if (session != null) {
        getApplication().getSessionCache().removeSession(session.getSessionId());
      }
      return;
    }

    //session不为空，或者请求不需要检查session
    if (session != null || isRequestWithoutSession(req)) {
      if("open".equals(nginxSSL)) {
        //XSS参数过滤
        String reqString = req.getQueryString();
        if (XSSFilterUtils.invalidStringFilter(reqString)) {
          resp.sendRedirect("/error");
          return;
        }
        if (XSSFilterUtils.invalidCookieFilter(req)) {
          resp.sendRedirect("/error");
          return;
        }
      }
      if (session != null) {
        if (logger.isDebugEnabled()) {
          logger.debug("Found session {}", session.getUser());
        }
        Object csrfToken = session.getSessionData("csrfToken");
        if (csrfToken != null) {
          resp.setHeader("csrfToken", csrfToken.toString());
        }
      }
      if (handleFileGet(req, resp)) {
        return;
      }
      if ("/error".equals(req.getRequestURI())) {
        handleErrorRequest(req, resp, "Illegal Request.");
        return;
      }
      //检查查询数量
      if (this.notCheckSizeAjaxList != null && !this.notCheckSizeAjaxList.contains(
              getParam(req, "ajax", ""))) {
        int querySize = getIntParam(req, "size", 0);
        int queryLength = getIntParam(req, "length", 0);
        int queryPageSize = getIntParam(req, "pageSize", 0);
        if (querySize > this.ajaxMaxSize || queryPageSize > this.ajaxMaxSize
                || queryLength > this.ajaxMaxSize) {
          logger.error("request size more than {}, reject!", this.ajaxMaxSize);
          Map<String, Object> ret = new HashMap<>();
          ret.put("code", 400);
          ret.put("error", "Request size exceeds " + this.ajaxMaxSize + ", reject");
          this.writeJSON(resp, ret);
          return;
        }
      }
      handleGet(req, resp, session);
    } else {
      if (hasParam(req, "ajax")) {
        final HashMap<String, String> retVal = new HashMap<>();
        retVal.put("error", "session");
        //处理ajax请求， session超时
        resp.setHeader("session-status", "timeout");
        this.writeJSON(resp, retVal);
      } else if ("/toL".equals(req.getRequestURI())){
        handleLogin(req, resp);
      } else if ("/error".equals(req.getRequestURI())) {
        handleErrorRequest(req, resp, "Illegal Request.");
      } else {
        handleLogin(req, resp);
      }
    }
  }

  private boolean validCsrf(HttpServletRequest req, HttpServletResponse resp, Session session,
                            boolean isGet, Map<String, Object> params)
          throws IOException, ServletException {
    if (!getApplication().getServerProps().getBoolean("azkaban.csrf.check", true) || session == null
            || "/error".equals(req.getRequestURI()) || !StringUtils
            .isFromBrowser(req.getHeader("User-Agent"))) {
      return false;
    }
    if (isGet) {
      if (!hasParam(req, "ajax") && !hasParam(req, "action") && !hasParam(req, "delete")
              && !hasParam(req, "purge") && !hasParam(req, "download") && !hasParam(req, "logout")) {
        return false;
      }
      String referer = req.getHeader("Referer");
      if (referer == null || !referer.contains(req.getServerName())) {
        handleLogin(req, resp);
        return true;
      }
    } else {
      Object csrfToken = session.getSessionData("csrfToken");
      if (csrfToken != null && !csrfToken.equals(req.getHeader("csrfToken")) && (params == null
              ? true : !csrfToken.equals(params.get("csrfToken") + ""))) {
        resp.sendRedirect("/error");
        return true;
      }
    }
    return false;
  }

  private boolean isRequestWithoutSession(HttpServletRequest req) {
    String ajaxName = getParam(req, "ajax", "");
    return this.requestWithoutSessionList.contains(ajaxName);
  }

  /**
   * Log out request - the format should be close to Apache access log format
   */
  private void logRequest(final HttpServletRequest req, final Session session) {
    final StringBuilder buf = new StringBuilder();
    buf.append(getRealClientIpAddr(req)).append(" ");
    if (session != null && session.getUser() != null) {
      buf.append(session.getUser().getUserId()).append(" ");
      if (org.apache.commons.lang.StringUtils.isNotBlank(session.getUser().getNormalUser())) {
        buf.append("NormalUser:(");
        buf.append(session.getUser().getNormalUser()).append(") ");
      }
    } else {
      buf.append(" - ").append(" ");
    }

    buf.append("\"");
    buf.append(req.getMethod()).append(" ");
    buf.append(req.getRequestURI()).append(" ");
    if (req.getQueryString() != null && !isIllegalPostRequest(req)) {
      buf.append(req.getQueryString()).append(" ");
    } else {
      buf.append("-").append(" ");
    }
    buf.append(req.getProtocol()).append("\" ");

    final String userAgent = req.getHeader("User-Agent");
    if (this.shouldLogRawUserAgent) {
      buf.append(userAgent);
    } else {
      // simply log a short string to indicate browser or not
      if (StringUtils.isFromBrowser(userAgent)) {
        buf.append("browser");
      } else {
        buf.append("not-browser");
      }
    }

    logger.info(buf.toString());
  }

  private boolean handleFileGet(final HttpServletRequest req, final HttpServletResponse resp)
          throws IOException {
    if (this.webResourceDirectory == null) {
      return false;
    }

    // Check if it's a resource
    final String prefix = req.getContextPath() + req.getServletPath();
    String path = req.getRequestURI().substring(prefix.length());

    // 路径操作漏洞,将非法请求路径中的目录转换为不可到达
    path = path.replace("../", "");

    final int index = path.lastIndexOf('.');
    if (index == -1) {
      return false;
    }

    final String extension = path.substring(index);
    if (CONTEXT_TYPE.containsKey(extension)) {
      final File file = new File(this.webResourceDirectory, path);
      if (!file.exists() || !file.isFile()) {
        return false;
      }

      resp.setContentType(CONTEXT_TYPE.get(extension));

      final OutputStream output = resp.getOutputStream();
      BufferedInputStream input = null;
      try {
        input = new BufferedInputStream(new FileInputStream(file));
        IOUtils.copy(input, output);
      } finally {
        if (input != null) {
          input.close();
        }
      }
      output.flush();
      return true;
    }

    return false;
  }

  private String getRealClientIpAddr(final HttpServletRequest req) {

    // If some upstream device added an X-Forwarded-For header
    // use it for the client ip
    // This will support scenarios where load balancers or gateways
    // front the Azkaban web server and a changing Ip address invalidates
    // the session
    final HashMap<String, String> headers = new HashMap<>();
    headers.put(WebUtils.X_FORWARDED_FOR_HEADER, req.getHeader(WebUtils.X_FORWARDED_FOR_HEADER.toLowerCase()));

    final WebUtils utils = new WebUtils();

    return utils.getRealClientIpAddr(headers, req.getRemoteAddr());
  }

  private Session getSessionFromRequest(final HttpServletRequest req)
          throws ServletException {
    final Cookie cookie = getCookieByName(req, SESSION_ID_NAME);
    String sessionId = null;
    String referer = req.getHeader("Referer");

    final Props props = this.application.getServerProps();
    String refererUrl = props.getString("azkaban.header.referer.url", "");

    if (cookie != null) {
      sessionId = cookie.getValue();
    }

    if (sessionId == null && hasParam(req, "session.id")) {
      sessionId = getParam(req, "session.id");
    }

    return getSessionFromSessionId(sessionId);
  }

  private Session getSessionFromSessionId(final String sessionId) {
    if (sessionId == null) {
      return null;
    }

    return getApplication().getSessionCache().getSession(sessionId);
  }

  private void handleLogin(final HttpServletRequest req, final HttpServletResponse resp) throws ServletException, IOException {
    handleLogin(req, resp, null);
  }

  private void handleLogin(final HttpServletRequest req, final HttpServletResponse resp,
                           final String errorMsg) throws ServletException, IOException {
    String setCookie = resp.getHeader("Set-Cookie");
    if(null != setCookie){
      resp.setHeader("Set-Cookie", setCookie + ";Secure");
      resp.setHeader("Set-Cookie", setCookie + ";HttpOnly");
    }
    final Page page = newPage(req, resp, "azkaban/webapp/servlet/velocity/login.vm");

    String languageType = LoadJsonUtils.getLanguageType();
    Map<String, String> loginMap;
    Map<String, String> subPageMap1;
    if ("zh_CN".equalsIgnoreCase(languageType)) {
      // 添加国际化标签
      loginMap = LoadJsonUtils.transJson("/conf/azkaban-web-server-zh_CN.json",
              "azkaban.webapp.servlet.velocity.login.vm");
      subPageMap1 = LoadJsonUtils.transJson("/conf/azkaban-web-server-zh_CN.json",
              "azkaban.webapp.servlet.velocity.nav.vm");
      this.passwordPlaceholder = "密码";
    }else {
      loginMap = LoadJsonUtils.transJson("/conf/azkaban-web-server-en_US.json",
              "azkaban.webapp.servlet.velocity.login.vm");
      subPageMap1 = LoadJsonUtils.transJson("/conf/azkaban-web-server-en_US.json",
              "azkaban.webapp.servlet.velocity.nav.vm");
      this.passwordPlaceholder = "Password";
    }
    loginMap.forEach(page::add);
    subPageMap1.forEach(page::add);

    page.add("passwordPlaceholder", this.passwordPlaceholder);
    page.add("publicKey", getApplication().getServerProps().get(WTSS_PUBLIC_KEY));
    page.add("opsLoginCheck", getApplication().getServerProps().getBoolean("wtss.opsuser.login.switch", false));
    if (errorMsg != null) {
      page.add("errorMsg", errorMsg);
    }
    page.add("currentlangType", languageType);
    page.render();
  }

  @Override
  protected void doPost(final HttpServletRequest req, final HttpServletResponse resp)
          throws ServletException, IOException {
    Session session = getSessionFromRequest(req);
    this.webMetrics.markWebPostCall();
    logRequest(req, session);
    Map<String, Object> params = null;
    if (ServletFileUpload.isMultipartContent(req)) {
      params = this.multipartParser.parseMultipart(req);
    }
    if (this.validCsrf(req, resp, session, false, params)) {
      return;
    }
    if (session != null) {
      resp.setHeader("csrfToken", session.getSessionData("csrfToken") + "");
    }
    if (isIllegalPostRequest(req)) {
      writeResponse(resp, "Login error. Must pass username and password in request body");
      return;
    }
    final Props props = this.application.getServerProps();
    String nginxSSL = props.getString("nginx.ssl.module", "");
    if("open".equals(nginxSSL)) {
      String referer = req.getHeader("Referer");
      String refererUrl = props.getString("azkaban.header.referer.url", "");
      // 判断 Referer 是否以 bank.example 开头
      if ((referer != null) && !(referer.trim().startsWith(refererUrl))) {
        resp.sendRedirect("/error");
      }

      Map<String, String[]> param = req.getParameterMap();
      for(Map.Entry<String, String[]> entry : param.entrySet()){
        //XSS参数过滤
        String reqString = ObjectUtils.toString(entry.getValue()[0]);
        if(XSSFilterUtils.invalidStringFilter(reqString)){
          //handleErrorRequest(req, resp, "请不要输入非法字符串！");
          resp.sendRedirect("/error");
          return;
        }
      }
      if(XSSFilterUtils.invalidCookieFilter(req)){
        resp.sendRedirect("/error");
        return;
      }
    }
    // Handle Multipart differently from other post messages
    if (ServletFileUpload.isMultipartContent(req)) {
      if (session == null) {
        // See if the session id is properly set.
        if (params.containsKey("session.id")) {
          final String sessionId = (String) params.get("session.id");

          session = getSessionFromSessionId(sessionId);
          if (session != null) {
            handleMultiformPost(req, resp, params, session);
            return;
          }
        }

        // if there's no valid session, see if it's a one time session.
        if (!params.containsKey("username") || !params.containsKey("userpwd")) {
          writeResponse(resp, "Login error. Need username and password");
          return;
        }

        final String username = (String) params.get("username");
        final String password = (String) params.get("userpwd");
        final String ip = getRealClientIpAddr(req);
        try {
          session = createSession(username, password, ip);
          resp.setHeader("csrfToken", session.getSessionData("csrfToken") + "");
        } catch (final UserManagerException e) {
          writeResponse(resp, "Login error: " + e.getMessage());
          return;
        }
      }
      handleMultiformPost(req, resp, params, session);
    } else if ("/checkin".equals(req.getRequestURI()) && hasParam(req, "action")
            && "login".equals(getParam(req, "action"))) {
      final HashMap<String, Object> obj = new HashMap<>();
      handleAjaxLoginAction(req, resp, obj);
      this.writeJSON(resp, obj);
    } else if (session == null) {
      if ("/eventschedule".equals(req.getRequestURI())) { // If calling the authAndMatchFlow or executeFlowFromEvent API, the authentication will be bypassed.
        try {
          handlePost(req, resp, session);
        } catch (TriggerManagerException | ScheduleManagerException e) {
          logger.warn("Handle /eventschedule interface exception, e:  {}", e.toString());
          writeResponse(resp, "Handle /eventschedule interface exception, e: " + e.toString());
        }
      } else if (hasParam(req, "username") && hasParam(req, "userpwd")) {
        // If it's a post command with curl, we create a temporary session
        try {
          session = createSession(req);
          resp.setHeader("csrfToken", session.getSessionData("csrfToken") + "");
        } catch (final UserManagerException e) {
          writeResponse(resp, "Login error: " + e.getMessage());
        }
        try {
          handlePost(req, resp, session);
        } catch (TriggerManagerException e) {
          logger.warn("Failed to trigger.", e);
        } catch (ScheduleManagerException e) {
          logger.warn("Failed to schedule.", e);
        }
      } else {
        // There are no valid sessions and temporary logins, no we either pass
        // back a message or redirect.
        if (isAjaxCall(req)) {
          final String response =
                  createJsonResponse("error", "Invalid Session. Please login in again.",
                          "login", null);
          resp.setCharacterEncoding("utf-8");
          //处理ajax请求， session超时
          resp.setHeader("session-status", "timeout");
          writeResponse(resp, response);
        } else {
          handleLogin(req, resp, "Enter username and password");
        }
      }
    } else {
      try {
        handlePost(req, resp, session);
      } catch (TriggerManagerException e) {
        logger.warn("Failed to trigger.", e);
      } catch (ScheduleManagerException e) {
        logger.warn("Failed to schedule.", e);
      }
    }
  }

  /**
   * Disallows users from logging in by passing their username and password via the request header
   * where it'd be logged.
   *
   * Example of illegal post request: curl -X POST http://localhost:8081/?action=login\&username=azkaban\&password=azkaban
   *
   * req.getParameterMap() or req.getParameterNames() cannot be used because they draw no
   * distinction between the illegal request above and the following valid request: curl -X POST -d
   * "action=login&username=azkaban&password=azkaban" http://localhost:8081/
   *
   * "password=" is searched for because it leverages the query syntax to determine that the user is
   * passing the password as a parameter name. There is no other ajax call that has a parameter that
   * includes the string "password" at the end which could throw false positives.
   */
  private boolean isIllegalPostRequest(final HttpServletRequest req) {
    return (req.getQueryString() != null && req.getQueryString().contains("password="));
  }

  private Session createDssSession(final String username, final HttpServletRequest req)
          throws UserManagerException, ServletException, IOException {
    final String ip = getRealClientIpAddr(req);
    try {
      if (!StringUtils.isFromBrowser(req.getHeader("User-Agent"))) {
        logger.info("not browser.");
        Session cacheSession = this.application.getSessionCache().getSessionByUsername(username);
        if (cacheSession != null) {
          logger.info("session not found.");
          return cacheSession;
        }
      }
    } catch (final Exception e) {
      logger.error("no super user", e);
    }
    Session newSession = createSession(username, "dssToken", ip, req);
    return newSession;
  }

  private Session createSession(final HttpServletRequest req)
          throws UserManagerException, ServletException, IOException {
    final String username = getParam(req, "username");
    String password = getParam(req, "userpwd");
    String frompage = "";
    if (hasParam(req, "frompage")) {
      frompage = getParam(req, "frompage");
    }

    final Props props = this.application.getServerProps();

    if (hasParam(req, "encryption") && "true".equals(getParam(req, "encryption"))){
      String wtss_private_key = props.getString("wtss.private.key", "");
      logger.debug("encryption is enable , decode password {}" , password);
      try {
        if(password!=null){
          password = password.replaceAll(" ","+");
        }
        password = RSAUtils.decrypt(password,wtss_private_key);
      } catch (Exception e) {
        throw new RuntimeException("parse encryption secret info failed , caused by {} " + e.getMessage());
      }
    }

    final String ip = getRealClientIpAddr(req);

    if (props.getBoolean(ENABLE_APPID_LOGIN, true)) {
      // appId + appSecret 校验
      if (hasParam(req, "appId") && hasParam(req, "appSecret")) {
        Session sessionByAppIdAndAppToken = this.serverValid(req, props, username, password, ip,
            getParam(req, "appId"), getParam(req, "appSecret"));

        if (sessionByAppIdAndAppToken != null) {
          return sessionByAppIdAndAppToken;
        } else {
          throw new UserManagerException("failed to get session by appId and appToken ");
        }
      }
    }

    Session session = this
            .validSecret(req, props, username, password, ip, "common.secret", "common_secret");
    if (session != null) {
      return session;
    }
    session = this
            .validSecret(req, props, username, password, ip, "hpms.secret", "hpms_secret");
    if (session != null) {
      return session;
    }
    if("true".equals(frompage)){
      try {
        String passwordPrivateKey = props.getString("password.private.key");
        password = RSAUtils.decrypt(passwordPrivateKey, password);
      } catch (Exception e){
        logger.error("decrypt password failed.", e);
        throw new UserManagerException("decrypt password failed.");
      }
      checkUserCategory(req, username);
    }
    return createSession(username, password, ip, req);
  }

  private Session serverValid(HttpServletRequest req, Props props, String username, String password,
      String ip, String appId, String appSecret) throws UserManagerException {

    CredentialDto credentialDto;
    CredentialServiceImpl credentialService = SERVICE_PROVIDER.getInstance(
        CredentialServiceImpl.class);
    try {
      credentialDto = credentialCache.get(appId, () -> credentialService.getCredentialByAppId(
          appId));
    } catch (ExecutionException e) {
      logger.error("Error when get credential info for appId {}", appId);
      throw new RuntimeException("Error when get credential info, cause by", e);
    }

    if (credentialDto == null) {
      logger.warn("handle appId login failed, no credential info");
      return null;
    }

    if ((!credentialDto.getAppId().equals(appId) && credentialDto.getAppSecret()
        .equals(appSecret))) {
      logger.warn("handle appId login failed, no match credential info");
      return null;
    }

    // 判断 IP 是否在 IP 白名单中
    String[] ipArray = credentialDto.getIpWhitelist().split(",");
    List<String> ipList = Arrays.asList(ipArray);
    if (!ipList.contains(ip)) {
      logger.warn("IP {} is not in whitelist for subsystem {}", ip,
          credentialDto.getSubsystemId());
      throw new UserManagerException(
          "IP " + ip + " is not in whitelist for subsystem " + credentialDto.getSubsystemId());
    }

    String normalUserName = getParam(req, "normalUserName", "");
    if (org.apache.commons.lang.StringUtils.isNotEmpty(normalUserName)) {
      final UserManager manager = getApplication().getTransitionService().getUserManager();
      manager.validDepartmentOpsUser(username, normalUserName);
    }

    logger.info("handle appId login , appId pass check, subsystem {}",
        credentialDto.getSubsystemId());
    //如果超级用户用户名和密码都是对的，那么我们直接放行
    if (!StringUtils.isFromBrowser(req.getHeader("User-Agent"))) {
      logger.info("not browser.");
      Session cacheSession = null;
      try {
        cacheSession = this.application.getSessionCache().getSessionByUsername(username);
      } catch (Exception e) {
        logger.warn("get session by username error, caused by: " + e);
      }
      if (cacheSession != null) {
        logger.info("session for user {} was found in cache.", username);
        return cacheSession;
      }
    }

    SessionCache sessionCache = getApplication().getSessionCache();
    Session sessionByUsername = sessionCache.getSessionByUsername(username);
    if (sessionByUsername == null) {
      logger.info("session for user {} was not found in cache, trying to create new session",
          username);
      Session newSession = createSession(username, password, ip, appSecret);
      sessionCache.addSession(newSession);
      return newSession;
    } else {
      logger.info("session for user {} was found in cache.", username);
      return sessionByUsername;
    }

  }

  /**
   * @param req       HTTP 请求
   * @param props     服务配置
   * @param username  用户名
   * @param password  用户密码
   * @param ip        调用 IP
   * @param secretKey 涉及密钥的服务配置参数名
   * @param reqSecret 涉及密钥的请求参数名
   * @return
   * @throws ServletException
   * @throws UserManagerException
   */
  private Session validSecret(HttpServletRequest req, Props props, String username, String password,
                              String ip, String secretKey, String reqSecret) throws ServletException, UserManagerException {
    if (!hasParam(req, reqSecret)) {
      return null;
    }

    String wtss_secret_de = props.getString(secretKey, "");
    if ("".equals(wtss_secret_de)) {
      return null;
    }
    String wtss_private_key = props.getString("wtss.private.key", "");
    String from_dss_secret_de = "";
    // 从请求中获取密文
    String from_dss_secret_en = getParam(req, reqSecret);
    logger.debug("handle secret login , secret > {}", from_dss_secret_en);
    try {
      if (from_dss_secret_en != null) {
        from_dss_secret_en = from_dss_secret_en.replaceAll(" ", "+");
      }
      from_dss_secret_de = RSAUtils.decrypt(from_dss_secret_en, wtss_private_key);
    } catch (Exception e) {
      throw new RuntimeException("parse secret failed , caused by ", e);
    }

    // 请求中的 secret 与配置中的 secret 比对
    if (wtss_secret_de.equals(from_dss_secret_de)) {
      String normalUserName = getParam(req, "normalUserName", "");
      if (org.apache.commons.lang.StringUtils.isNotEmpty(normalUserName)) {
        final UserManager manager = getApplication().getTransitionService().getUserManager();
        manager.validDepartmentOpsUser(username, normalUserName);
      }

      logger.debug("handle secret login , secret pass check");
      //如果超级用户用户名和密码都是对的，那么我们直接放行
      if (!StringUtils.isFromBrowser(req.getHeader("User-Agent"))) {
        logger.info("not browser.");
        Session cacheSession = null;
        try {
          cacheSession = this.application.getSessionCache().getSessionByUsername(username);
        } catch (Exception e) {
          logger.info("get session by username error, caused by: " + e);
        }
        if (cacheSession != null) {
          logger.info("session not found.");
          return cacheSession;
        }
      }

      SessionCache sessionCache = getApplication().getSessionCache();
      Session sessionByUsername = sessionCache.getSessionByUsername(username);
      if (sessionByUsername == null) {
        Session newSession = createSession(username, password, ip, wtss_secret_de);
        sessionCache.addSession(newSession);
        return newSession;
      } else {
        return sessionByUsername;
      }
    }
    return null;
  }

  /**
   * 检查用户类型（ops-运维用户）
   * @param req
   * @param username
   * @throws ServletException
   * @throws UserManagerException
   */
  private void checkUserCategory(HttpServletRequest req, String username) throws ServletException, UserManagerException {
    //开关打开才校验用户类型
    if (!getApplication().getServerProps().getBoolean("wtss.opsuser.login.switch", false)) {
      return;
    }

    if (username == null || username.trim().isEmpty()) {
      return;
    }

    String userCategory = getUserCategory(username);
    Boolean isOps = getBooleanParam(req, "isOps", false);
    Boolean isSys = getBooleanParam(req, "isSys", false);
    if ((isOps||isSys) && !"".equals(userCategory)) {
      //ops-运维用户
      if (isOps && !UserType.OPS.getType().equals(userCategory)) {
        throw new UserManagerException("not ops user.");
      }
      if (isSys && !UserType.SYSTEM.getType().equals(userCategory)) {
        throw new UserManagerException("not system user.");
      }
      //校验实名用户
      String normalUser = getParam(req, "normalUserName");
      if (normalUser == null || normalUser.trim().isEmpty()) {
        return;
      }
      if (!UserType.PERSONAL.getType().equals(getUserCategory(normalUser))) {
        throw new UserManagerException("the normal user is not real-name user.");
      }
    } else {
      //普通用户登录
      if (UserType.OPS.getType().equals(userCategory)) {
        throw new UserManagerException("the username is ops user.");
      }
      if (UserType.SYSTEM.getType().equals(userCategory)) {
        throw new UserManagerException("the username is system user.");
      }
    }
  }

  /**
   * 连接数据库查询用户类型
   * @param username
   * @return
   */
  private String getUserCategory(String username) {
    SystemUserLoader systemUserLoader = ServiceProvider.SERVICE_PROVIDER.getInstance(JdbcSystemUserImpl.class);
    try {
      WtssUser wtssUser = systemUserLoader.getWtssUserByUsername(username);
      if (wtssUser != null) {
        return wtssUser.getUserCategory() == null ? "" : wtssUser.getUserCategory().trim();
      }
    } catch (SystemUserManagerException e) {
      logger.error("query user by name exception", e);
    }
    return "";
  }

  private Session createSession(final String username, final String password, final String ip)
          throws UserManagerException, ServletException {
    final UserManager manager = getApplication().getTransitionService().getUserManager();
    final User user = manager.getUser(username, password);

    final String randomUID = UUID.randomUUID().toString();
    final Session session = new Session(randomUID, user, ip);

    return session;
  }

  /**
   * @param username 用户名
   * @param password 用户密码
   * @param ip 调用 IP
   * @param request HTTP 请求
   * @return
   * @throws UserManagerException
   * @throws ServletException
   * @throws IOException
   */
  private Session createSession(final String username, final String password, final String ip, HttpServletRequest request)
          throws UserManagerException, ServletException, IOException {
    final UserManager manager = getApplication().getTransitionService().getUserManager();
    User user = null;
    if (hasParam(request, "isOps") && "true".equals(getParam(request, "isOps"))) {
      String normalUserName = getParam(request, "normalUserName");
      String normalPassword = getParam(request, "normalPassword");
      user = manager.validateNonRealNameUser(username, password, normalUserName, normalPassword, UserType.OPS);
    } else if (hasParam(request, "isSys") && "true".equals(getParam(request, "isSys"))) {
      String normalUserName = getParam(request, "normalUserName");
      String normalPassword = getParam(request, "normalPassword");
      user = manager.validateNonRealNameUser(username, password, normalUserName, normalPassword, UserType.SYSTEM);
    } else {
      user = manager.getUser(username, password);
    }
    if (!StringUtils.isFromBrowser(request.getHeader("User-Agent"))) {
      logger.info("not browser.");
      Session cacheSession = this.application.getSessionCache().getSessionByUser(user);
      if (cacheSession != null) {
        logger.info("session not found.");
        return cacheSession;
      }
    }

    SessionCache sessionCache = getApplication().getSessionCache();
    Session sessionByUser = sessionCache.getSessionByUser(user);
    if (sessionByUser == null) {
      final String randomUID = UUID.randomUUID().toString();
      final Session session = new Session(randomUID, user, ip);
      sessionCache.addSession(session);
      return session;
    } else {
      return sessionByUser;
    }
  }

  private Session createSession(final String username, final String password, final String ip,
                                final String superUser) throws UserManagerException{

    UserManager manager = getApplication().getTransitionService().getUserManager();
    if (manager instanceof SystemUserManager){
      //不改接口，直接改SystemUserManager，这样做到少侵入
      SystemUserManager userManager = (SystemUserManager)manager;
      final User user = userManager.getUser(username, password, superUser);
      logger.info("User is {}", user.toString());
      final String uuid = UUID.randomUUID().toString();
      return new Session(uuid, user, ip);
    }else{
      logger.warn("user manager 不是 WebankXmlUserManager 实例，不能进行创建session");
      return null;
    }
  }



  protected boolean hasPermission(final Project project, final User user, final Permission.Type type) {
    // 1、判断用户是否有调度权限
    if (project.hasPermission(user, type)) {
      return true;
    }

    // 2、判断用户是否管理员
    for (final String roleName : user.getRoles()) {
      final Role role = user.getRoleMap().get(roleName);
      if (role != null && role.getPermission().isPermissionSet(Permission.Type.ADMIN)) {
        return true;
      }
    }

    // 3、判断用户是否是这个项目创建人所在部门的运维用户
    String createUser = project.getCreateUser();
    SystemManager systemManager = this.application.getTransitionService().getSystemManager();
    try {
      WtssUser wtssUser = systemManager.getSystemUserByUserName(createUser);
      List<Integer> departmentIds = systemManager.getDepartmentMaintainerDepListByUserName(user.getUserId());
      if (CollectionUtils.isNotEmpty(departmentIds) && departmentIds.contains((int) wtssUser.getDepartmentId())) {
        return true;
      }
    } catch (SystemUserManagerException e){
      logger.error("Failed to get information.", e);
    }

    return false;
  }

  protected void handleAjaxLoginAction(final HttpServletRequest req,
                                       final HttpServletResponse resp, final Map<String, Object> ret)
          throws ServletException {

    final Props props = this.application.getServerProps();
    String nginxSSL = props.getString("nginx.ssl.module", "");
    if("open".equals(nginxSSL)){
      //SSL模式下 cookie安全 为了通过 appscan 测试
//      List<String> cookieList = (ArrayList<String>) resp.getHeaders("Set-Cookie");
//      for(String setC : cookieList){
//        //setC = resp.getHeader("Set-Cookie");
//        resp.setHeader("Set-Cookie", setC + ";Secure");
//      }
      String setCookie = resp.getHeader("Set-Cookie");
      resp.setHeader("Set-Cookie", setCookie + ";Secure");
      resp.setHeader("Set-Cookie", setCookie + ";HttpOnly");
    }

    if (hasParam(req, "username") && hasParam(req, "userpwd")) {
      Session session = null;
      try {
        session = createSession(req);
        resp.setHeader("csrfToken", session.getSessionData("csrfToken") + "");
      } catch (final UserManagerException | IOException e) {
        ret.put("error", "Login in error. " + e.getMessage());
        return;
      }
      if (null == session){
        logger.error("session is null");
        ret.put("error","Login in error, session is null.");
        return;
      }
      final Cookie cookie = new Cookie(SESSION_ID_NAME, session.getSessionId());
      cookie.setPath("/");
      if("open".equals(nginxSSL)) {
        cookie.setSecure(true);
        //限制web页面程序的browser端script程序读取cookie 开启可能会影响测试工具工作
        cookie.setHttpOnly(true);
      }
      cookie.setHttpOnly(true);
      resp.addCookie(cookie);

      ret.put("status", "success");
      ret.put("session.id", session.getSessionId());

    } else {
      ret.put("error", "Login in error.");
    }
  }

  protected void handleDssLoginAction(String username,final HttpServletRequest req,final HttpServletResponse resp)
          throws ServletException {
    final Props props = this.application.getServerProps();
    if (username!=null) {
      Session session = null;
      try {
        session = createDssSession(username,req);
      } catch (final UserManagerException | IOException e) {
        logger.error("Dss Login in error.", e);
        return;
      }
      if (null == session){
        logger.error("DSS Login in error, session is null.");
        return;
      }
      final Cookie cookie = new Cookie(SESSION_ID_NAME, session.getSessionId());
      cookie.setPath("/");
      resp.addCookie(cookie);
      logger.info("session.id {} ", session.getSessionId());
    } else {
      logger.error("Login in error,invalid username {}", username);
    }
  }

  protected void writeResponse(final HttpServletResponse resp, final String response) throws IOException {
    final Writer writer = resp.getWriter();
    writer.append(response);
    writer.flush();
  }

  protected boolean isAjaxCall(final HttpServletRequest req) throws ServletException {
    final String value = req.getHeader("X-Requested-With");
    if (value != null) {
      logger.info("has X-Requested-With {}", value);
      return "XMLHttpRequest".equals(value);
    }
    final String ajaxString = req.getParameter("ajax");
    if(null != ajaxString && ajaxString.contains("ext")){
      return true;
    }

    return false;
  }

  /**
   * The get request is handed off to the implementor after the user is logged in.
   */
  protected abstract void handleGet(HttpServletRequest req,
                                    HttpServletResponse resp, Session session) throws ServletException,
          IOException;

  /**
   * The post request is handed off to the implementor after the user is logged in.
   */
  protected abstract void handlePost(HttpServletRequest req,
                                     HttpServletResponse resp, Session session)
          throws ServletException,
          IOException, TriggerManagerException, ScheduleManagerException;

  /**
   * The post request is handed off to the implementor after the user is logged in.
   */
  protected void handleMultiformPost(final HttpServletRequest req,
                                     final HttpServletResponse resp, final Map<String, Object> multipart, final Session session)
          throws ServletException, IOException {
  }

  /**
   *
   * @param req
   * @param resp
   * @param errorMsg
   * @throws ServletException
   * @throws IOException
   */
  private void handleErrorRequest(final HttpServletRequest req, final HttpServletResponse resp,
                                  final String errorMsg) throws ServletException, IOException {
    final Page page = newPage(req, resp, "azkaban/webapp/servlet/velocity/single-error-page.vm");
    page.add("errorMsg", errorMsg);
    String languageType = LoadJsonUtils.getLanguageType();
    page.add("currentlangType", languageType);
    page.render();
    resp.sendError(230);
  }
  public  boolean getBooleanParam(final HttpServletRequest request,
                                        final String name, final boolean defaultVal) {
    if (hasParam(request, name)) {
      try {
        return getBooleanParam(request, name,defaultVal);
      } catch (final Exception e) {
        return defaultVal;
      }
    }

    return defaultVal;
  }

}
