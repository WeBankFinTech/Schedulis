package azkaban.viewer.system;

import azkaban.ServiceProvider;
import azkaban.batch.HoldBatchContext;
import azkaban.batch.HoldBatchLevel;
import azkaban.common.utils.ExcelUtil;
import azkaban.dto.ModifyWtssUserDto;
import azkaban.entity.*;
import azkaban.exception.SystemUserManagerException;
import azkaban.executor.DepartmentGroup;
import azkaban.executor.Executor;
import azkaban.i18n.utils.LoadJsonUtils;
import azkaban.module.SystemModule;
import azkaban.project.Project;
import azkaban.project.ProjectManager;
import azkaban.project.entity.ProjectChangeOwnerInfo;
import azkaban.scheduler.Schedule;
import azkaban.scheduler.ScheduleManager;
import azkaban.server.HttpRequestUtils;
import azkaban.server.session.Session;
import azkaban.service.impl.SystemManager;
import azkaban.user.SystemUserManager;
import azkaban.user.User;
import azkaban.utils.GsonUtils;
import azkaban.utils.Props;
import azkaban.webank.data.WebankUsersSync;
import azkaban.webapp.AzkabanWebServer;
import azkaban.webapp.servlet.*;
import com.google.common.base.Joiner;
import com.google.gson.JsonObject;
import com.google.inject.Injector;
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.collections4.MapUtils;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;
import org.joda.time.DateTime;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.util.*;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Created by zhu on 7/5/18.
 */
public class SystemServlet extends AbstractLoginAzkabanServlet {

    private static final Logger logger = LoggerFactory.getLogger(SystemServlet.class.getName());

    private static final long serialVersionUID = 1L;
    private RecoverServlet.ExecutorVMHelper vmHelper;
    private SystemManager systemManager;
    private Props propsPlugin;
    private Props propsAzkaban;
    private final File webResourcesPath;

    private final String viewerName;
    private final String viewerPath;

    private static final String DEPARTMENT_MAINTAINER_CHECK_SWITCH_KEY = "department.maintainer.check.switch";

    public static boolean department_maintainer_check_switch;

    public static final Pattern DEPARTMENT_PATTERN = Pattern.compile("^[a-zA-Z]+$");
    public static final Pattern USER_ID_PATTERN = Pattern.compile("^[0-9]+$");
    public static final Pattern USER_NAME_PATTERN = Pattern.compile("^[a-zA-Z_]+$");

    private HoldBatchContext holdBatchContext;

    private ProjectManager projectManager;

    private SystemUserManager systemUserManager;

    private ScheduleManager scheduleManager;

    private ScheduledThreadPoolExecutor scheduledLoadWebankUsers;


    public SystemServlet(final Props propsPlugin) {

        this.propsPlugin = propsPlugin;
        this.viewerName = propsPlugin.getString("viewer.name");
        this.viewerPath = propsPlugin.getString("viewer.path");

        this.webResourcesPath = new File(
            new File(propsPlugin.getSource()).getParentFile().getParentFile(), "web");
        this.webResourcesPath.mkdirs();

        setResourceDirectory(this.webResourcesPath);

    }

    @Override
    public void init(final ServletConfig config) throws ServletException {
        super.init(config);

        Injector injector = ServiceProvider.SERVICE_PROVIDER.getInjector()
            .createChildInjector(new SystemModule());
        systemManager = injector.getInstance(SystemManager.class);
        propsAzkaban = ServiceProvider.SERVICE_PROVIDER.getInstance(Props.class);
        department_maintainer_check_switch = propsAzkaban.getBooleanDefaultFalse(
            DEPARTMENT_MAINTAINER_CHECK_SWITCH_KEY, false);
        this.holdBatchContext = ServiceProvider.SERVICE_PROVIDER.getInstance(
            HoldBatchContext.class);
        this.projectManager = ServiceProvider.SERVICE_PROVIDER.getInstance(ProjectManager.class);
        this.systemUserManager = ServiceProvider.SERVICE_PROVIDER.getInstance(
            SystemUserManager.class);
        this.scheduleManager = ServiceProvider.SERVICE_PROVIDER.getInstance(ScheduleManager.class);
        scheduledLoadWebankUsers = new ScheduledThreadPoolExecutor(1);
        scheduledLoadWebankUsers.scheduleAtFixedRate(new Runnable() {
            @Override
            public void run() {
                try {
                    loadWebankUsers();
                } catch (Exception e) {
                    logger.error("load webank users error.", e);
                }
            }
        }, 0, propsAzkaban.getLong("wtss.webank.user.reload.interval", 86400L), TimeUnit.SECONDS);
        AzkabanWebServer azkabanWebServer = injector.getInstance(AzkabanWebServer.class);
        azkabanWebServer.setWebankUsersLoadThread(scheduledLoadWebankUsers);
    }

    @Override
    protected void handleGet(final HttpServletRequest req, final HttpServletResponse resp,
                             final Session session) throws ServletException, IOException {

        // 下载人员变动信息表
        if ("/system/downloadModifyInfo".equals(req.getRequestURI())) {
            downloadAllModifyDetailInfo(req, resp, session);
        }

        if (hasParam(req, "ajax")) {
            handleAJAXAction(req, resp, session);
        } else {
            handleSystemPage(req, resp, session);
        }
    }

    /**
     * updated by v_wbzxluo
     *
     * @param req
     * @param resp
     * @param session
     * @throws ServletException
     * @throws IOException
     */
    private void handleAJAXAction(final HttpServletRequest req,
                                  final HttpServletResponse resp, final Session session) throws ServletException,
            IOException {
        final HashMap<String, Object> ret = new HashMap<>();
        final String ajaxName = getParam(req, "ajax");

        final User user = session.getUser();
        if (!user.getRoles().contains("admin")) {
            if (!"loadSystemUserSelectData".equals(ajaxName) && !"loadWebankDepartmentSelectData".equals(ajaxName)) {
                ret.put("error", "No Access Permission");
                if (ret != null) {
                    this.writeJSON(resp, ret);
                }
                return;
            }
        }

        if ("addSystemUserViaFastTrack".equals(ajaxName)) {
            // 通过非登录页面的快速通道新增用户
            ajaxAddSystemUserViaFastTrack(req, resp, session, ret);
        } else if ("fetch".equals(ajaxName)) {
            fetchHistoryData(req, resp, ret);
        } else if ("user_role".equals(ajaxName)) {
            ajaxGetUserRole(req, resp, session, ret);
        } else if ("syncWebankUsers".equals(ajaxName)) {
            ajaxSyncWebankUsers(req, resp, session, ret);
        } else if ("loadWebankUserSelectData".equals(ajaxName)) {
            ajaxLoadWebankUserSelectData(req, resp, session, ret);
        } else if ("findSystemUserPage".equals(ajaxName)) {
            ajaxFindSystemUserPage(req, resp, session, ret);
        } else if ("addSystemUser".equals(ajaxName)) {
            ajaxAddSystemUser(req, resp, session, ret);
        } else if ("getSystemUserById".equals(ajaxName)) {
            ajaxGetSystemUserById(req, resp, session, ret);
        } else if ("updateSystemUser".equals(ajaxName)) {
            ajaxUpdateSystemUser(req, resp, session, ret);
        } else if ("loadSystemUserSelectData".equals(ajaxName)) {
            ajaxLoadSystemUserSelectData(req, resp, session, ret);
        } else if ("loadWebankDepartmentSelectData".equals(ajaxName)) {
            ajaxLoadWebankDepartmentSelectData(req, resp, session, ret);
        } else if ("deleteSystemUser".equals(ajaxName)) {
            ajaxDeleteSystemUser(req, resp, session, ret);
        } else if ("syncXmlUsers".equals(ajaxName)) {
            ajaxSyncXmlUsers(req, resp, session, ret);
        } else if ("findSystemDeparmentPage".equals(ajaxName)) {
            ajaxFindSystemDeparmentPage(req, resp, session, ret);
        } else if ("addDeparment".equals(ajaxName)) {
            ajaxAddDeparment(req, resp, session, ret);
        } else if ("updateDeparment".equals(ajaxName)) {
            ajaxUpdateDeparment(req, resp, session, ret);
        } else if ("getDeparmentById".equals(ajaxName)) {
            ajaxGetDeparmentById(req, resp, session, ret);
        } else if ("deleteDeparment".equals(ajaxName)) {
            ajaxDeleteDeparment(req, resp, session, ret);
        } else if ("fetchAllDepartmentGroup".equals(ajaxName)) {
            ajaxFetchAllDepartmentGroup(req, resp, session, ret);
        } else if ("addDepartmentGroup".equals(ajaxName)) {
            ajaxAddDepartmentGroup(req, resp, session, ret);
        } else if ("deleteDepartmentGroup".equals(ajaxName)) {
            ajaxDeleteDepartmentGroup(req, resp, session, ret);
        } else if ("updateDepartmentGroup".equals(ajaxName)) {
            ajaxUpdateDepartmentGroup(req, resp, session, ret);
        } else if ("fetchDepartmentGroupById".equals(ajaxName)) {
            ajaxFetchDepartmentGroupById(req, resp, session, ret);
        } else if ("fetchExecutors".equals(ajaxName)) {
            ajaxFetchExecutors(req, resp, session, ret);
        } else if ("findModifySystemUserPage".equals(ajaxName)) {
            ajaxFindModifySystemUserPage(req, resp, session, ret);
        } else if ("syncModifyEsbSystemUsers".equals(ajaxName)) {
            ajaxSyncModifyEsbSystemUsers(req, resp, session, ret);
        } else if ("getModifyInfoSystemUserById".equals(ajaxName)) {
            ajaxGetModifyInfoSystemUserById(req, resp, session, ret);
        } else if ("findDepartmentMaintainerList".equals(ajaxName)) {
            ajaxFindDepartmentMaintainerList(req, resp, session, ret);
        } else if ("addDepartmentMaintainer".equals(ajaxName)) {
            ajaxAddDepartmentMaintainer(req, resp, session, ret);
        } else if ("updateDepartmentMaintainer".equals(ajaxName)) {
            ajaxUpdateDepartmentMaintainer(req, resp, session, ret);
        } else if ("getDepMaintainerByDepId".equals(ajaxName)) {
            ajaxGetDepMaintainerByDepId(req, resp, session, ret);
        } else if ("deleteDepartmentMaintainer".equals(ajaxName)) {
            ajaxDeleteDepartmentMaintainer(req, resp, session, ret);
        } else if("deleteExceptionalUser".equals(ajaxName)){
            ajaxDeleteExceptionalUser(req, resp, session, ret);
        } else if("addExceptionalUser".equals(ajaxName)){
            ajaxAddExceptionalUser(req, resp, session, ret);
        } else if ("fetchAllExceptionUsers".equals(ajaxName)) {
            ajaxFetchAllExceptionUsers(req, resp, session, ret);
        } else if ("revokeUser".equals(ajaxName)) {
            ajaxRevokeUser(req, resp, session, ret);
        } else if ("privilegeReport".equals(ajaxName)) {
            ajaxPrivilegeReport(req, resp, session, ret);
        }

        if (ret != null) {
            this.writeJSON(resp, ret);
        }
    }

    /**
     * 加载 SystemServlet 中的异常信息等国际化资源
     * @return
     */
    private Map<String, String> loadSystemServletI18nData() {
        String languageType = LoadJsonUtils.getLanguageType();
        Map<String, String> dataMap;
        if ("zh_CN".equalsIgnoreCase(languageType)) {
            dataMap = LoadJsonUtils.transJson("/conf/az-webank-system-manager-zh_CN.json",
                "azkaban.viewer.system.SystemServlet");
        } else {
            dataMap = LoadJsonUtils.transJson("/conf/az-webank-system-manager-en_US.json",
                "azkaban.viewer.system.SystemServlet");
        }
        return dataMap;
    }

    /**
     * 用户权限回收
     *
     * @param req
     * @param resp
     * @param session
     * @param ret
     */
    private void ajaxRevokeUser(HttpServletRequest req, HttpServletResponse resp, Session session,
        Map<String, Object> ret) {

        String username = null;
        try {
            username = getParam(req, "username");
            // 检查调用接口的用户是否为系统管理员
            if (!(session.getUser().hasRole("admin"))) {
                ret.put("code", 1001);
                ret.put("user", username);
                ret.put("message", "当前用户不为系统管理员，无法进行权限回收。");
                return;
            }
            // 检查传入用户在系统中是否存在
            WtssUser wtssUser = this.systemManager.getSystemUserByUserName(username);
            if (wtssUser == null) {
                ret.put("code", 1002);
                ret.put("user", username);
                ret.put("message", "系统中不存在该用户，无法进行权限回收。");
                return;
            }

            // 获取该用户创建的所有项目
            User user = systemUserManager.getUser(username);
            List<Project> userProjects = projectManager.getUserProjects(user);

            ArrayList<String> noExchangeProjectName = new ArrayList<>();
            // 判断该用户拥有的项目是否都已经完成了交接
            for (Project userProject : userProjects) {
                if (userProject.isActive()) {
                    ProjectChangeOwnerInfo projectChangeOwnerInfo = projectManager.getProjectChangeOwnerInfo(
                        userProject);

                    if (projectChangeOwnerInfo != null && 3 != projectChangeOwnerInfo.getStatus()) {
                        // 存在未交接完成的项目，无法进行权限回收
                        noExchangeProjectName.add(userProject.getName());
                        ret.put("code", 1003);
                        ret.put("user", username);
                        ret.put("message", "该用户仍然存在项目尚未交接完成，无法进行权限回收。");
                        return;
                    }
                }
            }

            // 项目都已交接完成，再次检查
            List<Project> userProjectsCheck = projectManager.getUserProjects(user);
            if (userProjectsCheck.size() != 0) {
                ret.put("code", 1003);
                ret.put("user", username);
                ret.put("message", "该用户仍然存在项目尚未交接完成，无法进行权限回收。");
                return;
            }

            // 检查用户是否存在有效调度
            List<Schedule> schedules = scheduleManager.getSchedules();
            for (Schedule schedule : schedules) {
                if (username.equals(schedule.getSubmitUser())) {
                    Map<String, Object> otherOption = schedule.getOtherOption();
                    boolean activeFlag = (boolean) otherOption.get("activeFlag");
                    if (activeFlag) {
                        ret.put("code", 1004);
                        ret.put("user", username);
                        ret.put("message", "该用户仍然存在有效调度，无法进行权限回收。");
                        return;
                    }
                }
            }

            // 删除系统用户
            int addResult = this.systemManager.deleteSystemUser(wtssUser.getUserId());

            if (addResult != 1) {
                ret.put("code", 1005);
                ret.put("user", username);
                ret.put("message", "删除系统中该用户失败。");
                return;
            }

            // 删除部门管理员（部门运维人员）
            long departmentId = wtssUser.getDepartmentId();
            String departmentName = wtssUser.getDepartmentName();
            DepartmentMaintainer maintainer = this.systemManager.getDepMaintainerByDepId(
                departmentId);
            if (maintainer != null) {
                // 存在部门管理员
                String opsUser = maintainer.getOpsUser();
                String[] opsUserArray = opsUser.split(",");
                HashSet<String> opsUserSet = new HashSet<>();
                for (String opsUserName : opsUserArray) {
                    if (!username.equals(opsUserName)) {
                        opsUserSet.add(opsUserName);
                    }
                }

                StringBuilder stringBuilder = new StringBuilder();
                for (String opsUserName : opsUserSet) {
                    stringBuilder.append(opsUserName).append(",");
                }
                stringBuilder.deleteCharAt(stringBuilder.lastIndexOf(","));

                int updateResult = this.systemManager.updateDepartmentMaintainer(departmentId,
                    departmentName,
                    stringBuilder.toString());
                if (updateResult != 1) {
                    ret.put("code", 1006);
                    ret.put("user", username);
                    ret.put("message", "更新部门管理员失败。");
                    return;
                }
            }

            ret.put("code", 1000);
            ret.put("user", username);
            ret.put("message", "用户权限回收成功");

        } catch (Exception e) {
            ret.put("code", 1010);
            ret.put("user", username);
            ret.put("message", "用户权限回收失败，原因：" + e.getMessage());
        }
    }

    /**
     * 通过非登录页面的快速通道新增用户
     *
     * @param req
     * @param resp
     * @param ret
     * @throws ServletException
     */
    private void ajaxAddSystemUserViaFastTrack(final HttpServletRequest req,
        final HttpServletResponse resp,
                                               final Session session, final HashMap<String, Object> ret) throws ServletException {
        String userId;
        if (hasParam(req, "userId")) {
            userId = getParam(req, "userId");
        } else {
            userId = null;
        }
        final String password = getParam(req, "password");
        String tempRoleId = getParam(req, "roleId");
        int roleId;
        if (StringUtils.isNotBlank(tempRoleId)) {
            roleId = Integer.valueOf(tempRoleId);
        } else {
            roleId = 0;
        }
        String proxyUser = getParam(req, "proxyUser");
        String tempDepartmentId = getParam(req, "departmentId");
        int departmentId;
        if (StringUtils.isNotBlank(tempDepartmentId)) {
            departmentId = Integer.valueOf(getParam(req, "departmentId"));
        } else {
            departmentId = -1;
        }
        final String email = getParam(req, "email", "");
        try {

            if (StringUtils.isBlank(userId)) {
                throw new SystemUserManagerException("未填写用户ID。");
            }
            if(email != null && email.length() > 1 && !azkaban.utils.StringUtils.isEmail(email)){
                throw new SystemUserManagerException("email格式不正确");
            }
            if (departmentId == -1) {
                throw new SystemUserManagerException("不能添加无部门的用户。");
            }

            if (roleId != 2) {
                throw new SystemUserManagerException("未页面登录只能新增普通用户(roleId = 2),请正确选择角色roleId的值.");
            }

            if (0 == departmentId) {
                throw new SystemUserManagerException("请选择部门。");
            }

            if(this.systemManager.checkUserIsExceptionalUser(userId)){
                throw new SystemUserManagerException("Failed to create, the new user is an exception.");
            }

            WtssUser wtssUser = null;
            int addResult = 0;

            // 校验用户是否存在
            WtssUser tempWtssUser = this.systemManager.getSystemUserById(userId);

            if (null == tempWtssUser) {

                // 针对id 带前缀'wtss_' 的, 因为第一次添加的时候如果不是WebankUser对象,则会在userId前加上前缀'wtss_'
                // 此处如果不加这个前缀是查不出来的, 代码就会走新增逻辑,然后报错:'主键userId重复定义'
                // 该条查询在 this.systemManager.getSystemUserById(userId) 这条查询无结果之后再执行
                WtssUser featureWtssUser = this.systemManager.getSystemUserById(("wtss_" + userId));
                if (null != featureWtssUser) {
                    wtssUser = featureWtssUser;
                    userId = "wtss_" + userId;
                }
            } else {
                wtssUser = tempWtssUser;
            }

            if (null != wtssUser) {

                // 已经存在用户时, 新增的代理用户不能为空,为空就表示重复添加主用户
                if (StringUtils.isNotBlank(proxyUser)) {

                    // 如果存在用户的话 但是代理用户不一样  直接更新代理用户即可,是将新用户加入进去
                    String dbProxyUsers = wtssUser.getProxyUsers();

                    // 代理用户默认就是用户自己,此处判断防止数据库数据手动修改将代理用户变为空之后发生异常
                    if (StringUtils.isNotBlank(dbProxyUsers)) {

                        // 判断是否有多个代理用户
                        if (dbProxyUsers.contains(",")) {
                            String[] split = dbProxyUsers.split(",");
                            ArrayList<String> arrayList = new ArrayList<>(Arrays.asList(split));

                            // 判断添加的代理用户是否已存在
                            boolean containResult = arrayList.contains(proxyUser);
                            if (containResult || proxyUser.equalsIgnoreCase(wtssUser.getUsername())) {

                                logger.error("wtssUser=" + wtssUser.toString());
                                addResult = 2;

                            } else {
                                // 将添加的代理用户加入代理用户集 ,多个代理用户用逗号分隔
                                proxyUser = dbProxyUsers + "," + proxyUser;

                                logger.info("for update value is userId:{}, password:{}, roleId:{}, proxyUser:{}, departmentId:{}, email:{}", userId, password, roleId, proxyUser, departmentId, email);
                                addResult = this.systemManager.updateSystemUser(userId, password, roleId, proxyUser, departmentId, email);
                            }
                        } else {

                            // 单个代理用户只需要判断添加的代理是否和用户名一样
                            if (!proxyUser.equals(wtssUser.getUsername())) {
                                proxyUser = wtssUser.getProxyUsers() + "," + proxyUser;
                                addResult = this.systemManager.updateSystemUser(userId, password, roleId, proxyUser, departmentId, email);
                            } else {
                                logger.error("wtssUser=" + wtssUser.toString());
                                addResult = 3;
                            }
                        }
                    } else {
                        throw new SystemUserManagerException("代理用户不存在, 请检查数据是否完整.");
                    }
                } else {
                    logger.error("wtssUser=" + wtssUser.toString());
                    addResult = 4;
                }

            } else {
                addResult = this.systemManager.addSystemUser(userId, password, roleId, 3, proxyUser, departmentId, email);
            }

            if (addResult == 1) {
                ret.put("success", "add system user success");
            } else if (addResult == 2) {
                ret.put("success", "当前设置代理用户已存在,代理用户数据未发生变化");
            } else if (addResult == 3) {
                ret.put("success", "当前操作无数据发生变化");
            } else if (addResult == 4) {
                ret.put("success", "当前设置代理用户数据为空, 该操作无数据发生变化");
            } else {
                throw new SystemUserManagerException("新增WTSS用户失败!");
            }


        } catch (Exception e) {
            ret.put("error", e.getMessage());
        }
    }


    private void fetchHistoryData(final HttpServletRequest req,
                                  final HttpServletResponse resp, final HashMap<String, Object> ret)
            throws ServletException {
    }

    //返回当前用户的角色列表
    private void ajaxGetUserRole(final HttpServletRequest req,
                                 final HttpServletResponse resp, final Session session, final HashMap<String, Object> ret) {
        final String[] userRoles = session.getUser().getRoles().toArray(new String[0]);
        ret.put("userRoles", userRoles);
    }

    /**
     * 数据补采历史页面
     *
     * @param req
     * @param resp
     * @param session
     * @throws ServletException
     */
    private void handleSystemPage(final HttpServletRequest req, final HttpServletResponse resp, final Session session)
            throws ServletException {
        final Page page =
                newPage(req, resp, session, "azkaban/viewer/system/system-manager.vm");
        int pageNum = getIntParam(req, "page", 1);
        final int pageSize = getIntParam(req, "size", 16);

        if (pageNum < 0) {
            pageNum = 1;
        }

        final User user = session.getUser();

        page.add("adminPerm", user.getRoles().contains("admin"));

        page.add("vmutils", this.vmHelper);

        page.add("size", pageSize);
        page.add("page", pageNum);

        // keep the search terms so that we can navigate to later pages
        if (hasParam(req, "searchterm") && !"".equals(getParam(req, "searchterm"))) {
            page.add("search", "true");
            page.add("search_term", getParam(req, "searchterm"));
        }

        if (hasParam(req, "advfilter")) {
            page.add("advfilter", "true");
            page.add("projcontain", getParam(req, "projcontain"));
            page.add("flowcontain", getParam(req, "flowcontain"));
            page.add("usercontain", getParam(req, "usercontain"));
            page.add("status", getIntParam(req, "status"));
        }

        if (pageNum == 1) {
            page.add("previous", new HistoryServlet.PageSelection(1, pageSize, true, false));
        } else {
            page.add("previous", new HistoryServlet.PageSelection(pageNum - 1, pageSize, false,
                    false));
        }
        page.add("next", new HistoryServlet.PageSelection(pageNum + 1, pageSize, false, false));
        // Now for the 5 other values.
        int pageStartValue = 1;
        if (pageNum > 3) {
            pageStartValue = pageNum - 2;
        }

        page.add("page1", new HistoryServlet.PageSelection(pageStartValue, pageSize, false,
                pageStartValue == pageNum));
        pageStartValue++;
        page.add("page2", new HistoryServlet.PageSelection(pageStartValue, pageSize, false,
                pageStartValue == pageNum));
        pageStartValue++;
        page.add("page3", new HistoryServlet.PageSelection(pageStartValue, pageSize, false,
                pageStartValue == pageNum));
        pageStartValue++;
        page.add("page4", new HistoryServlet.PageSelection(pageStartValue, pageSize, false,
                pageStartValue == pageNum));
        pageStartValue++;
        page.add("page5", new HistoryServlet.PageSelection(pageStartValue, pageSize, false,
                pageStartValue == pageNum));
        pageStartValue++;

        String languageType = LoadJsonUtils.getLanguageType();
        Map<String, String> viewDataMap;
        Map<String, String> subPageMap1;
        if ("zh_CN".equalsIgnoreCase(languageType)) {
             viewDataMap = LoadJsonUtils.transJson("/conf/az-webank-system-manager-zh_CN.json",
                    "azkaban.viewer.system.system-manager.vm");
            subPageMap1 = LoadJsonUtils.transJson("/conf/azkaban-web-server-zh_CN.json",
                    "azkaban.webapp.servlet.velocity.nav.vm");
        }else {
            viewDataMap = LoadJsonUtils.transJson("/conf/az-webank-system-manager-en_US.json",
                    "azkaban.viewer.system.system-manager.vm");
            subPageMap1 = LoadJsonUtils.transJson("/conf/azkaban-web-server-en_US.json",
                    "azkaban.webapp.servlet.velocity.nav.vm");
        }
        viewDataMap.forEach(page::add);
        subPageMap1.forEach(page::add);

        page.add("webankDepartmentList", getWebankDepartmentAllList(null));
        page.add("currentlangType", languageType);
        page.render();

    }

    @Override
    protected void handlePost(final HttpServletRequest req, final HttpServletResponse resp,
                              final Session session) throws ServletException, IOException {
        if (hasParam(req, "ajax")) {
            handleAJAXAction(req, resp, session);
        }
    }

    public static class PageSelection {

        private final int page;
        private final int size;
        private final boolean disabled;
        private boolean selected;

        public PageSelection(final int page, final int size, final boolean disabled,
                             final boolean selected) {
            this.page = page;
            this.size = size;
            this.disabled = disabled;
            this.setSelected(selected);
        }

        public int getPage() {
            return this.page;
        }

        public int getSize() {
            return this.size;
        }

        public boolean getDisabled() {
            return this.disabled;
        }

        public boolean isSelected() {
            return this.selected;
        }

        public void setSelected(final boolean selected) {
            this.selected = selected;
        }
    }


    private void ajaxSyncWebankUsers(final HttpServletRequest req, final HttpServletResponse resp
            , final Session session, final HashMap<String, Object> ret) {
        Map<String, String> dataMap = loadSystemServletI18nData();
        try {
            loadWebankUsers();
        } catch (SystemUserManagerException e) {
            ret.put("error", e.getMessage());
        }

        ret.put("message", dataMap.get("syncWebanUserInfoComplete"));

    }

    private void loadWebankUsers() throws SystemUserManagerException {
        if (this.holdBatchContext.getBatchMap().values().stream()
                .anyMatch(opr -> HoldBatchLevel.CUSTOMIZE.getNumVal() == opr.getOperateLevel())) {
            throw new SystemUserManagerException("some user is holding, can not update");
        }
        try {
            WebankUsersSync webankUsersSync = new WebankUsersSync(this.propsPlugin);
            webankUsersSync.extract();
            webankUsersSync.transform();
            webankUsersSync.load();
        } catch (Exception e) {
            logger.error("loadWebankUsers Failed.", e);
            throw new SystemUserManagerException("Sync Webank User Information Failed.", e);
        }
    }

    private void ajaxLoadWebankUserSelectData(final HttpServletRequest req, final HttpServletResponse resp
            , final Session session, final HashMap<String, Object> ret)
            throws ServletException {

        String searchName = req.getParameter("serach");
        int pageNum = getIntParam(req, "page");
        int pageSize = getIntParam(req, "pageSize");


        List<Map<String, Object>> webankUserSelectList = new ArrayList<>();

        JSONObject items = new JSONObject();

        try {

            int webankUserTotalCount = this.systemManager.getWebankUserTotal();
            List<WebankUser> webankUserList = this.systemManager.findAllWebankUserPageList(searchName, (pageNum - 1) * pageSize, pageSize);

            //JSONArray children = new JSONArray();

            for (WebankUser webankUser : webankUserList) {
                Map<String, Object> selectItem = new HashMap<>();
//        JSONObject child = new JSONObject();
                selectItem.put("id", Integer.valueOf(webankUser.userId));
                selectItem.put("text", webankUser.fullName);
                selectItem.put("dpId", webankUser.departmentId);
                selectItem.put("dpName", webankUser.departmentName);
                selectItem.put("email", webankUser.email);
                webankUserSelectList.add(selectItem);
//        child.put("id", webankUser.userId);
//        child.put("text", webankUser.fullName);
//        children.put(child);
            }

//      items.put("items", children);

            ret.put("webankUserTotalCount", webankUserTotalCount);

        } catch (Exception e) {
            logger.warn("Failed to load users", e);
        }

        ret.put("page", pageNum);
        ret.put("webankUserList", webankUserSelectList);

    }


    private void ajaxFindSystemUserPage(final HttpServletRequest req, final HttpServletResponse resp,
                                        final Session session, final HashMap<String, Object> ret)
            throws ServletException {
        int start = Integer.valueOf(getParam(req, "start"));
        final int pageSize = Integer.valueOf(getParam(req, "pageSize"));
        final String searchterm = getParam(req, "searchterm").trim();
        String preciseSearch = "false";
        if(hasParam(req, "preciseSearch")){
            preciseSearch = getParam(req, "preciseSearch","false").trim();
        }

        Map<String, String> dataMap = loadSystemServletI18nData();

        int total = 0;
        try {

            //查询数据补采全部记录
            List<WtssUser> wtssUserList;
            if (StringUtils.isNotBlank(searchterm)) {
                // 如果搜索条件非空,则默认从第一页开始查找
                wtssUserList =
                    this.systemManager.findSystemUserPage(preciseSearch,null, searchterm, null,start * pageSize, pageSize);
                total = this.systemManager.getSystemUserTotal(searchterm);
            } else {
                wtssUserList =
                    this.systemManager.findSystemUserPage(preciseSearch,null, searchterm, null,start * pageSize, pageSize);
                total = this.systemManager.getSystemUserTotal();
            }


            //组装前端展示用的集合数据
            final List<Map<String, Object>> wtssUserPageList = new ArrayList<>();
            for (final WtssUser wtssUser : wtssUserList) {
                final HashMap<String, Object> wtssUserMap = new HashMap<>();
                wtssUserMap.put("userId", wtssUser.getUserId());
                wtssUserMap.put("username", wtssUser.getUsername());
                wtssUserMap.put("fullName", wtssUser.getFullName());
                wtssUserMap.put("departmentName", wtssUser.getDepartmentName());
                wtssUserMap.put("email", wtssUser.getEmail());
                wtssUserMap.put("proxyUsers", wtssUser.getProxyUsers());
                wtssUserMap.put("role", wtssUser.getRoleId() == 1 ? dataMap.get("admin") : dataMap.get("ordinaryUsers"));
                wtssUserMap.put("permission", this.systemManager.getUserPermission(wtssUser.getRoleId()));
                wtssUserMap.put("userType", wtssUser.getUserType());

                wtssUserPageList.add(wtssUserMap);
            }
            ret.put("total", total);
            ret.put("modify", dataMap.get("modify"));
            ret.put("start", start);
            ret.put("pageSize", pageSize);
            ret.put("systemUserPageList", wtssUserPageList);

        } catch (final SystemUserManagerException e) {
            ret.put("error", "Error retrieving executable flows");
        }


    }


    private void ajaxAddSystemUser(final HttpServletRequest req,
                                   final HttpServletResponse resp, final Session session, final HashMap<String, Object> ret)
            throws ServletException {

        final String userId = getParam(req, "userId");
        final String password = getParam(req, "password");
        final int roleId = Integer.valueOf(getParam(req, "roleId"));
        final int categoryUser = Integer.valueOf(getParam(req, "categoryUser"));
        final String proxyUser = getParam(req, "proxyUser");
        final int departmentId = Integer.valueOf(getParam(req, "departmentId"));
        final String email = getParam(req, "email", "");

        Map<String, String> dataMap = loadSystemServletI18nData();

        try {

            if (0 == roleId) {
                throw new SystemUserManagerException(dataMap.get("plsSelectRole"));
            }

            if (0 == departmentId) {
                throw new SystemUserManagerException(dataMap.get("plsSelectDep"));
            }

            if(email != null && email.length() > 1 && !azkaban.utils.StringUtils.isEmail(email)){
                throw new SystemUserManagerException("The mailbox format is incorrect.");
            }

            if(this.systemManager.checkUserIsExceptionalUser(userId)){
                throw new SystemUserManagerException("Failed to create, the new user is an exception.");
            }

            // 校验用户权限开关是否开启,开启的时候需要判断添加的用户是否符合规则,关闭的时候不需要校验
            boolean wtssProjectPrivilegeCheck = ProjectManagerServlet.getWtssProjectPrivilegeCheck();

            // 用户名是否需要分类, true:需要, false:不需要
            if (department_maintainer_check_switch) {
                // 判断添加的用户是不是运维用户,如果不是,则只能新增的用户只能设置他自己作为代理用户
                if (userId.startsWith("WTSS_")) {
                    if (categoryUser != 1) {
                        throw new SystemUserManagerException(dataMap.get("errusercate"));
                    }
                    String[] userIdParts = StringUtils.split(userId, "_");
                    if (userIdParts.length == 3) {
                        String simpleDepartmentCode = userIdParts[1];
                        if (DEPARTMENT_PATTERN.matcher(simpleDepartmentCode).matches()) {
                            throw new SystemUserManagerException(dataMap.get("invalidUserNamePrefix") + userId + dataMap.get("invalidDepCode"));
                        }
                    }else {
                        throw new SystemUserManagerException(dataMap.get("invalidUserNamePrefix") + userId + dataMap.get("invalidLength"));
                    }

                } else if (userId.startsWith("hduser")) {
                    if (categoryUser != 2) {
                        throw new SystemUserManagerException(dataMap.get("errusercate"));
                    }
                    if (StringUtils.isNotBlank(proxyUser)) {
                        // 判断开关是否打开,打开,则需要校验添加的代理用户是不是本人,关闭,不需要校验
                        if (wtssProjectPrivilegeCheck) {
                            if (roleId != 1) {
                                if (!proxyUser.equals(userId)) {
                                    throw new SystemUserManagerException(dataMap.get("invalidAddSystemUserProxy") + userId);
                                }
                            }
                        }
                    }

                } else if (USER_ID_PATTERN.matcher(userId).matches()) {
                    if (categoryUser != 3) {
                        throw new SystemUserManagerException(dataMap.get("errusercate"));
                    }
                    // 判断增加的用户是不是管理员
                    if (roleId != 1) {
                        // userId为纯数字的实名用户
                        if (StringUtils.isNotBlank(proxyUser)) {
                            WebankUser webankUser = this.systemManager.getWebankUserByUserId(userId);
                            if (wtssProjectPrivilegeCheck) {
                                if (webankUser != null) {
                                    if (!proxyUser.equals(webankUser.urn)) {
                                        throw new SystemUserManagerException(dataMap.get("invalidAddRealNameUserProxy") + webankUser.urn);
                                    }
                                }
                            }
                        }
                    }


                } else {
                    throw new SystemUserManagerException(dataMap.get("invalidUserNamePrefix") + userId + dataMap.get("invalidName"));
                }
            }

            WtssUser wtssUser = this.systemManager.getSystemUserById(userId);
            if (null != wtssUser) {
                throw new SystemUserManagerException(dataMap.get("userHasExist"));
            }

            // 校验用户是否存在
            WtssUser tempWtssUser = this.systemManager.getSystemUserById(userId);

            // 针对id 带前缀'wtss_' 的, 因为第一次添加的时候如果不是WebankUser对象,则会在userId前加上前缀'wtss_'
            // 此处如果不加这个前缀是查不出来的, 代码就会走新增逻辑,然后报错:'主键userId重复定义'
            // 该条查询在 this.systemManager.getSystemUserById(userId) 这条查询无结果之后再执行
            WtssUser featureWtssUser = this.systemManager.getSystemUserById(("wtss_" + userId));

            if ((null != tempWtssUser) || (null != featureWtssUser)) {
                throw new SystemUserManagerException(dataMap.get("userHasExist"));
            }

            int addResult = this.systemManager.addSystemUser(userId, password, roleId, categoryUser, proxyUser, departmentId, email);
            if (addResult != 1) {
                throw new SystemUserManagerException(dataMap.get("addSystemUserFailed"));
            }
        } catch (Exception e) {
            ret.put("error", e);
        }
    }

    private void ajaxGetSystemUserById(final HttpServletRequest req,
                                       final HttpServletResponse resp, final Session session, final HashMap<String, Object> ret)
            throws ServletException {

        final String userId = getParam(req, "userId");

        try {
            WtssUser wtssUser = this.systemManager.getSystemUserById(userId);
            String dbUserCategory = wtssUser.getUserCategory();
            String userCategory;
            // 回显数据
            if (dbUserCategory == null) {
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
            }
            String languageType = LoadJsonUtils.getLanguageType();
            ret.put("languageType", languageType);
            ret.put("systemUser", wtssUser);
        } catch (SystemUserManagerException e) {
            ret.put("error", "Get System User failed.");
        }
    }

    private void ajaxUpdateSystemUser(final HttpServletRequest req,
                                      final HttpServletResponse resp, final Session session, final HashMap<String, Object> ret)
            throws ServletException {

        final String userId = getParam(req, "userId");
        final String password = getParam(req, "password");
        final int roleId = Integer.valueOf(getParam(req, "roleId"));
        final String proxyUser = getParam(req, "proxyUser");
        final int departmentId = Integer.valueOf(getParam(req, "departmentId"));
        final String email = getParam(req, "email", "");

        Map<String, String> dataMap = loadSystemServletI18nData();
        try {
            WtssUser wtssUser = this.systemManager.getSystemUserById(userId);

            if (this.holdBatchContext.getBatchMap().values().stream()
                .filter(opr -> HoldBatchLevel.USER.getNumVal() == opr.getOperateLevel()).anyMatch(
                    opr -> opr.getDataList().contains(wtssUser.getUsername()))) {
                ret.put("error", "user[" + wtssUser.getUsername() + "] is holding, can not update");
                return;
            }

            if (0 == roleId) {
                throw new SystemUserManagerException(dataMap.get("plsSelectRole"));
            }
            if(email != null && email.length() > 1 && !azkaban.utils.StringUtils.isEmail(email)){
                throw new SystemUserManagerException("email格式不正确");
            }
            if (0 == departmentId) {
                throw new SystemUserManagerException(dataMap.get("plsSelectDep"));
            }

            // 校验用户权限开关是否开启,开启的时候需要判断添加的用户是否符合规则,关闭的时候不需要校验
            boolean wtssProjectPrivilegeCheck = ProjectManagerServlet.getWtssProjectPrivilegeCheck();


                // 实名用户的普通用户只能设置自己为代理
                if (USER_ID_PATTERN.matcher(userId).matches()) {
                    if (roleId != 1) {
                        if (StringUtils.isNotBlank(proxyUser)) {
                            // 校验开关是否打开,打开,只能设置自己为代理用户
                            if (wtssProjectPrivilegeCheck) {
                                if (!wtssUser.getUsername().equals(proxyUser)) {
                                    throw new SystemUserManagerException(dataMap.get("invalidUpdateRealNameUserProxy") + wtssUser.getUsername());
                                }
                            }
                        }
                    }
                } else if (wtssUser.getUsername().startsWith("hduser")) {
                    if (StringUtils.isNotBlank(proxyUser)) {
                        if (wtssProjectPrivilegeCheck) {
                            if (roleId != 1) {
                                if (!proxyUser.equals(wtssUser.getUsername())) {
                                    throw new SystemUserManagerException(dataMap.get("invalidUpdateSystemUserProxy") + wtssUser.getUsername());
                                }
                            }
                        }
                    }
                }

            int addResult = this.systemManager.updateSystemUser(userId, password, roleId, proxyUser, departmentId, email);
            if (addResult != 1) {
                throw new SystemUserManagerException(dataMap.get("requestFailed"));
            }
        } catch (Exception e) {
            ret.put("error", e.getMessage());
        }
    }


    private void ajaxLoadSystemUserSelectData(final HttpServletRequest req, final HttpServletResponse resp
            , final Session session, final HashMap<String, Object> ret)
            throws ServletException {

        String searchName = req.getParameter("serach");

        int pageNum = getIntParam(req, "page");
        int pageSize = getIntParam(req, "pageSize");

        Map<String, String> dataMap = loadSystemServletI18nData();

        try {

            //查询数据补采全部记录
            final List<WtssUser> wtssUserList =
                    this.systemManager.findSystemUserPage("false", searchName, null, null,
                            (pageNum - 1) * pageSize, pageSize);

            int total = this.systemManager.getSystemUserTotal();

            //组装前端展示用的集合数据
            final List<Map<String, Object>> wtssUserPageList = new ArrayList<>();
            for (final WtssUser wtssUser : wtssUserList) {
                final HashMap<String, Object> wtssUserMap = new HashMap<>();
                wtssUserMap.put("id", wtssUser.getUserId());
                wtssUserMap.put("text", wtssUser.getFullName());
                wtssUserMap.put("username", wtssUser.getUsername());
                wtssUserPageList.add(wtssUserMap);
            }

            ret.put("systemUserTotalCount", total);

            ret.put("page", pageNum);
            ret.put("systemUserList", wtssUserPageList);


        } catch (final SystemUserManagerException e) {
            ret.put("error", dataMap.get("requestFailed"));
        }

    }

    private void ajaxLoadWebankDepartmentSelectData(final HttpServletRequest req, final HttpServletResponse resp
            , final Session session, final HashMap<String, Object> ret)
            throws ServletException {

        String searchName = req.getParameter("serach");
        //int pageNum = getIntParam(req, "page");
        //int pageSize = getIntParam(req, "pageSize");

        ret.put("webankDepartmentList", getWebankDepartmentAllList(searchName));

    }

    private List<Map<String, Object>> getWebankDepartmentAllList(String searchName) {
        List<Map<String, Object>> webankUserSelectList = new ArrayList<>();

        try {

            List<WebankDepartment> webankDepartmentList = this.systemManager.findAllWebankDepartmentList(searchName);

            for (WebankDepartment webankDepartment : webankDepartmentList) {
                Map<String, Object> selectItem = new HashMap<>();
                selectItem.put("id", Long.valueOf(webankDepartment.dpId));
                selectItem.put("text", webankDepartment.dpChName);
                selectItem.put("dpId", Long.valueOf(webankDepartment.dpId));
                selectItem.put("dpName", webankDepartment.dpChName);
                webankUserSelectList.add(selectItem);
            }

        } catch (Exception e) {
            logger.warn("Failed to get department list", e);
        }

        return webankUserSelectList;
    }


    private void ajaxDeleteSystemUser(final HttpServletRequest req, final HttpServletResponse resp,
                                      final Session session, final HashMap<String, Object> ret)
            throws ServletException {

        final String userId = getParam(req, "userId");
        Map<String, String> dataMap = loadSystemServletI18nData();
        try {

            int addResult = this.systemManager.deleteSystemUser(userId);
            if (addResult != 1) {
                throw new SystemUserManagerException(dataMap.get("requestFailed"));
            }
        } catch (Exception e) {
            ret.put("error", e);
        }
    }

    private void ajaxSyncXmlUsers(final HttpServletRequest req, final HttpServletResponse resp
            , final Session session, final HashMap<String, Object> ret)
            throws ServletException {
        Map<String, String> dataMap = loadSystemServletI18nData();
        try {
            //获取xml用户数据
            //XmlUsersSync xmlUsersSync = new XmlUsersSync(this.propsPlugin);

            this.systemManager.addXmlUserToDB();

        } catch (Exception e) {
            ret.put("error", dataMap.get("requestFailed"));
        }

        ret.put("message", dataMap.get("requestSuccess"));

    }


    private void ajaxFindSystemDeparmentPage(final HttpServletRequest req,
                                             final HttpServletResponse resp, final Session session, final HashMap<String, Object> ret)
            throws ServletException {
        final int start = Integer.valueOf(getParam(req, "start"));
        final int pageSize = Integer.valueOf(getParam(req, "pageSize"));
        final String searchterm = getParam(req, "searchterm").trim();

        int total = 0;

        //组装前端展示用的集合数据
        final List<Map<String, Object>> wtssDepPageList = new ArrayList<>();
        try {

            //查询数据补采全部记录
            final List<WebankDepartment> wtssDepList = this.systemManager.
                    findAllWebankDepartmentPageOrSearch(searchterm,
                            start * pageSize, pageSize);

            if (StringUtils.isNotBlank(searchterm)) {
                total = this.systemManager.getWebankDepartmentTotal(searchterm);
            } else {
                total = this.systemManager.getWebankDepartmentTotal();
            }

            for (final WebankDepartment dep : wtssDepList) {
                final HashMap<String, Object> depMap = new HashMap<>();
                depMap.put("dpId", dep.getDpId());
                depMap.put("dpName", dep.getDpName());
                depMap.put("dpChName", dep.getDpChName());
                depMap.put("orgId", dep.getOrgId());
                depMap.put("orgName", dep.getOrgName());
                depMap.put("division", dep.getDivision());
                depMap.put("pid", dep.getPid());
                depMap.put("groupId", dep.getGroupId());
                depMap.put("groupName", dep.getDepartmentGroup().getName());

                wtssDepPageList.add(depMap);
            }

            Map<String, String> dataMap = loadSystemServletI18nData();
            ret.put("modify", dataMap.get("modify"));

            ret.put("total", total);
            ret.put("start", start);
            ret.put("pageSize", pageSize);
            ret.put("systemDeparmentPageList", wtssDepPageList);

        } catch (final SystemUserManagerException e) {
            ret.put("error", "Error retrieving executable flows");
        }


    }


    private void ajaxAddDeparment(final HttpServletRequest req,
                                  final HttpServletResponse resp, final Session session, final HashMap<String, Object> ret)
            throws ServletException {

        try {
            List<String> list = Arrays.asList("deparmentId", "pid", "dpName", "dpChName", "orgId", "orgName", "groupId", "uploadFlag");
            for (String key : list) {
                String value = getParam(req, key, "");
                if (StringUtils.isEmpty(value)) {
                    ret.put("error", key + " can't be empty");
                    return;
                }
            }
            Map<String, String> dataMap = loadSystemServletI18nData();

                String dpId = getParam(req, "deparmentId");
                // 部门编号最长8位
                if (dpId.length() > 8) {
                    throw new SystemUserManagerException(dataMap.get("tooLongDepId"));
                }

                final int departmentId = Integer.valueOf(dpId);
                final int pid = Integer.valueOf(getParam(req, "pid"));
                final String dpName = getParam(req, "dpName");
                final String dpChName = getParam(req, "dpChName");
                final int orgId = Integer.valueOf(getParam(req, "orgId"));
                final String orgName = getParam(req, "orgName");
                //final String division = getParam(req, "division");
                Integer groupId = Integer.valueOf(getParam(req, "groupId"));
                Integer uploadFlag = Integer.valueOf(getParam(req, "uploadFlag"));
                if (groupId == 0) {
                    groupId = 1;
                }

                WebankDepartment exisDep = this.systemManager.getDeparmentById(departmentId);
                if (null != exisDep) {
                    throw new SystemUserManagerException(dataMap.get("existDep"));
                }

                if (0 != pid) {
                    WebankDepartment parentDep = this.systemManager.getParentDepartmentByPId(pid);
                    if (null == parentDep) {
                        throw new SystemUserManagerException(dataMap.get("noParentDep"));
                    }
                }


                int addResult = this.systemManager.addDeparment(departmentId, pid, dpName, dpChName,
                        orgId, orgName, "", groupId, uploadFlag);
                if (addResult != 1) {
                    throw new SystemUserManagerException(dataMap.get("requestFailed"));
                }
        } catch (SystemUserManagerException e) {
            ret.put("error", e);
        }
    }

    private void ajaxUpdateDeparment(final HttpServletRequest req,
                                     final HttpServletResponse resp, final Session session, final HashMap<String, Object> ret)
            throws ServletException {

        final int depmentId = Integer.valueOf(getParam(req, "deparmentId"));
        final int pid = Integer.valueOf(getParam(req, "pid"));
        final String dpName = getParam(req, "dpName");
        final String dpChName = getParam(req, "dpChName");
        final int orgId = Integer.valueOf(getParam(req, "orgId"));
        final String orgName = getParam(req, "orgName");
        //final String division = getParam(req, "division");
        Integer groupId = Integer.valueOf(getParam(req, "groupId"));
        Integer uploadFlag = Integer.valueOf(getParam(req, "uploadFlag"));
        if (groupId == 0) {
            groupId = 1;
        }

        Map<String, String> dataMap = loadSystemServletI18nData();

        try {
            List<WtssUser> userList = this.systemManager.getSystemUserByDepartmentId(depmentId);
            if (CollectionUtils.isNotEmpty(userList) && this.holdBatchContext.getBatchMap().values()
                .stream().filter(opr -> HoldBatchLevel.USER.getNumVal() == opr.getOperateLevel())
                .anyMatch(opr -> userList.stream()
                    .anyMatch(user -> opr.getDataList().contains(user.getUsername())))) {
                ret.put("error", "department user is holding, can not update");
                return;
            }

            if (0 == depmentId) {
                throw new SystemUserManagerException(dataMap.get("plsInputDepId"));
            }

            if ("".equals(dpName) ) {
                throw new SystemUserManagerException(dataMap.get("plsInputEnDepName"));
            }

            if ("".equals(dpChName)) {
                throw new SystemUserManagerException(dataMap.get("plsInputCnDepName"));
            }

            if (0 == orgId) {
                throw new SystemUserManagerException(dataMap.get("plsInputOfficeId"));
            }

            int addResult = this.systemManager.updateDeparment(depmentId, pid, dpName, dpChName,
                    orgId, orgName, "", groupId, uploadFlag);
            if (addResult != 1) {
                throw new SystemUserManagerException(dataMap.get("requestFailed"));
            }
        } catch (Exception e) {
            ret.put("error", e.getMessage());
        }
    }

    private void ajaxGetDeparmentById(final HttpServletRequest req,
                                      final HttpServletResponse resp, final Session session, final HashMap<String, Object> ret)
            throws ServletException {

        final int dpId = Integer.valueOf(getParam(req, "dpId"));
        Map<String, String> dataMap = loadSystemServletI18nData();
        try {
            WebankDepartment webankDepartment = this.systemManager.getDeparmentById(dpId);
            ret.put("deparment", webankDepartment);
        } catch (SystemUserManagerException e) {
            ret.put("error", dataMap.get("requestFailed"));
        }
    }

    private void ajaxDeleteDeparment(final HttpServletRequest req,
                                     final HttpServletResponse resp, final Session session, final HashMap<String, Object> ret)
            throws ServletException {

        final int dpId = Integer.valueOf(getParam(req, "dpId"));
        int deleteResult = 0;

        Map<String, String> dataMap = loadSystemServletI18nData();

        try {

            // 先判断部门下有没有用户,如果有则不能删除部门信息
            List<WtssUser> wtssUserList = this.systemManager.getSystemUserByDepartmentId(dpId);
            if (CollectionUtils.isEmpty(wtssUserList)) {
                deleteResult = this.systemManager.deleteDeparment(dpId);
            } else {
                List<String> userNameList = wtssUserList.stream().map(WtssUser::getUsername).collect(Collectors.toList());

                // 取前五条展示
                List<String> showUserNameList = new ArrayList<>();
                StringBuilder s = new StringBuilder();
                if (userNameList.size() >= 5) {
                    for (int i = 0; i < 5; i++) {
                        showUserNameList.add(userNameList.get(i));
                    }
                    s.append(dataMap.get("canNotDeleteDepPrefix")).append(userNameList.size()).append(dataMap.get("canNotDeleteDepSuffix_1"))
                            .append("[ ").append(Joiner.on("  ").join(showUserNameList)).append(" ]");
                } else {
                    s.append(dataMap.get("canNotDeleteDepPrefix")).append(userNameList.size()).append(dataMap.get("canNotDeleteDepSuffix_2"))
                            .append("[ ").append(Joiner.on("  ").join(userNameList)).append(" ]");
                }
                throw new SystemUserManagerException(s.toString());
            }

            if (deleteResult != 1) {
                throw new SystemUserManagerException(dataMap.get("requestFailed"));
            }
        } catch (Exception e) {
            ret.put("error", e);
        }
    }

    private void ajaxGetDepMaintainerByDepId(final HttpServletRequest req, final HttpServletResponse resp,
                                             final Session session, final HashMap<String, Object> ret)
            throws ServletException {

        final String departmentId = getParam(req, "departmentId");

        try {
            DepartmentMaintainer maintainer = this.systemManager.getDepMaintainerByDepId(Long.valueOf(departmentId));
            ret.put("maintainer", maintainer);
        } catch (SystemUserManagerException e) {
            logger.error("Get Department Maintainer by departmentId({}) failed", departmentId, e);
            ret.put("error", "Get Department Maintainer failed.");
        }
    }

    private void ajaxAddDepartmentMaintainer(final HttpServletRequest req, final HttpServletResponse resp,
                                             final Session session, final HashMap<String, Object> ret)
            throws ServletException {

        final long departmentId = Long.valueOf(getParam(req, "departmentId"));
        final String userId = getParam(req, "userId");

        Map<String, String> dataMap = loadSystemServletI18nData();

        try {

            if (0 == departmentId) {
                throw new SystemUserManagerException(dataMap.get("plsSelectDep"));
            }

            if(this.systemManager.checkUserIsExceptionalUser(userId)){
                throw new SystemUserManagerException("Failed to create, the new user is an exception.");
            }

            int addResult;
            WebankDepartment webankDepartment = this.systemManager.getWebankDepartmentByDpId(Integer.valueOf(departmentId + ""));
            // 开关打开, 则校验用户信息
            WebankUser webankUser = this.systemManager.getWebankUserByUserId(userId);
            // 查询是否存在部门
            DepartmentMaintainer depMaintainerByDepId = this.systemManager.getDepMaintainerByDepId(departmentId);
            if (department_maintainer_check_switch) {
                if (webankUser != null) {
                    if (null != depMaintainerByDepId) {
                        String opsUser = depMaintainerByDepId.getOpsUser();
                        if (opsUser.contains(webankUser.urn)) {
                            throw new SystemUserManagerException(dataMap.get("existedDepMaintainer"));
                        }
                        // 部门已经配置了,再执行添加就相当于更新
                        opsUser = opsUser + "," + webankUser.urn;
                        addResult = this.systemManager.updateDepartmentMaintainer(departmentId, webankDepartment.dpChName, opsUser);
                    } else {
                        addResult = this.systemManager.addDepartmentMaintainer(departmentId, webankDepartment.dpChName, webankUser.urn);
                    }
                } else {
                    logger.error("switch turn on, Invalid user, userId:" + userId);
                    throw new SystemUserManagerException(dataMap.get("invalidUserName") + userId);
                }
            } else {
                if (null != depMaintainerByDepId) {
                    if (webankUser != null) {
                        // 如果属于webankUser, 则显示对应的英文名
                        String opsUser = depMaintainerByDepId.getOpsUser();
                        opsUser = opsUser + "," + webankUser.urn;
                        addResult = this.systemManager.updateDepartmentMaintainer(departmentId, webankDepartment.dpChName, opsUser);
                    } else {
                        // 不属于webankUser, 则直接显示填入用户名
                        String opsUser = depMaintainerByDepId.getOpsUser();
                        opsUser = opsUser + "," + userId;
                        logger.info("switch turn off, department info existed, current add user info is: departmentId:{}, " +
                                "departmentName:{}, depMaintainer:{}.", departmentId, webankDepartment.dpChName, opsUser);
                        addResult = this.systemManager.updateDepartmentMaintainer(departmentId, webankDepartment.dpChName, opsUser);
                    }
                } else {
                    // 如果是webankUser, 则显示英文名,不是webankUser, 则填入什么就是什么
                    if (webankUser != null) {
                        addResult = this.systemManager.addDepartmentMaintainer(departmentId, webankDepartment.dpChName, webankUser.urn);
                    } else {
                        logger.info("switch turn off, current add user info is: departmentId:{}, departmentName:{}, " +
                                "depMaintainer:{}.", departmentId, webankDepartment.dpChName, userId);
                        addResult = this.systemManager.addDepartmentMaintainer(departmentId, webankDepartment.dpChName, userId);
                    }
                }

            }

            if (addResult != 1) {
                logger.error("current add user info is: departmentId:{}, depMaintainer:{}.", departmentId, userId);
                throw new SystemUserManagerException(dataMap.get("addDepMaintainerUserFailed"));
            }

        } catch (Exception e) {
            logger.error("failed, current add user info is: departmentId:{}, depMaintainer:{}", departmentId, userId, e);
            ret.put("error", e);
        }
    }


    private void ajaxUpdateDepartmentMaintainer(final HttpServletRequest req, final HttpServletResponse resp,
                                                final Session session, final HashMap<String, Object> ret)
            throws ServletException {

        final long departmentId = Long.valueOf(getParam(req, "departmentId"));
        final String departmentName = getParam(req, "departmentName");
        final String depMaintainer = getParam(req, "depMaintainer");

        Map<String, String> dataMap = loadSystemServletI18nData();
        try {

            // 判断开关是否打开
            int updateResult;
            if (department_maintainer_check_switch) {
                // 判断用户是否符合要求, 部门不匹配的不能更新
                if (depMaintainer.contains(",")) {
                    if (depMaintainer.endsWith(",")) {
                        throw new SystemUserManagerException(dataMap.get("invalidUserNameOrNull"));
                    }
                    String[] maintainerArray = depMaintainer.split(",");

                    // 判断是否更新时加上了重复的人员
                    List<String> repeatUserList = mapSolution(maintainerArray);
                    if (repeatUserList.size() > 0) {
                        StringJoiner joiner = new StringJoiner(" ", "[", "]");
                        repeatUserList.forEach(joiner::add);
                        logger.info("switch turn on, current depMaintainer info is: " + joiner.toString());
                        throw new SystemUserManagerException(dataMap.get("updateRepeatedUser") + joiner.toString());
                    }

                    for (String singleMaintainer : maintainerArray) {
                        // 校验用户名
                        checkNotMatchedDepMaintainer(singleMaintainer, dataMap, departmentId);
                    }
                } else {
                    // 校验用户名
                    checkNotMatchedDepMaintainer(depMaintainer, dataMap, departmentId);
                }
                logger.info("switch turn on, current update department info is: departmentId:{}, departmentName:{}, " +
                        "depMaintainer:{}.", departmentId, departmentName, depMaintainer);
                updateResult = this.systemManager.updateDepartmentMaintainer(departmentId, departmentName, depMaintainer);
            } else {

                logger.info("switch turn off, current update department info is: departmentId:{}, departmentName:{}, " +
                        "depMaintainer:{}.", departmentId, departmentName, depMaintainer);
                // 开关关闭,则直接操作更新,不需要多余的判断
                updateResult = this.systemManager.updateDepartmentMaintainer(departmentId, departmentName, depMaintainer);
            }

            if (updateResult != 1) {
                logger.info("failed, current update department info is: departmentId:{}, departmentName:{}, " +
                        "depMaintainer:{}.", departmentId, departmentName, depMaintainer);
                throw new SystemUserManagerException(dataMap.get("requestFailed"));
            }
        } catch (Exception e) {
            logger.error("failed, current update department info is: departmentId:{}, departmentName:{}, " +
                    "depMaintainer:{}", departmentId, departmentName, depMaintainer, e);
            ret.put("error", e);
        }
    }

    /**
     * 找出存在重复的人员
     * 利用 hashmap 实现
     * @param nums
     * @return
     */
    public List<String> mapSolution(String[] nums) {
        List<String> list = new ArrayList<>();
        Map<String, String> map = new HashMap<>();
        for (int i = 0; i < nums.length; i++) {
            String str = nums[i];
            if (!map.containsKey(str)) {
                map.put(str, str);
            } else {
                list.add(str);
            }
        }
        return list;
    }

    /**
     * 校验并排除非法用户名和部门不匹配的用户
     * @param userName
     * @param dataMap
     * @param departmentId
     * @throws SystemUserManagerException
     */
    private void checkNotMatchedDepMaintainer(String userName, Map<String, String> dataMap, long departmentId) throws SystemUserManagerException {
        if (StringUtils.isNotBlank(userName)) {
            // 校验用户名
            if (!USER_NAME_PATTERN.matcher(userName).matches()) {
                throw new SystemUserManagerException(dataMap.get("invalidUserName") + userName);
            }
            WebankUser webankUser = this.systemManager.getWebankUserByUserName(userName);
            if (webankUser == null) {
                throw new SystemUserManagerException(dataMap.get("invalidUserName") + userName);
            }
        } else {
            throw new SystemUserManagerException(dataMap.get("invalidUserNameOrNull"));
        }

    }

    private void ajaxDeleteDepartmentMaintainer(final HttpServletRequest req, final HttpServletResponse resp,
                                                final Session session, final HashMap<String, Object> ret)
            throws ServletException {

        final Integer departmentId = Integer.valueOf(getParam(req, "departmentId"));
        Map<String, String> dataMap = loadSystemServletI18nData();
        try {
            logger.info("current delete department info is: departmentId:{}.", departmentId);

            int deleteResult = this.systemManager.deleteDepartmentMaintainer(departmentId);

            if (deleteResult != 1) {
                logger.info("current delete department info is: departmentId:{}.", departmentId);
                throw new SystemUserManagerException(dataMap.get("requestFailed"));
            }
        } catch (Exception e) {
            logger.error("failed, current delete department info is: departmentId:{}", departmentId, e);
            ret.put("error", e);
        }
    }


    /**
     * 查看部门运维人员名单
     *
     * @param req
     * @param resp
     * @param session
     * @param ret
     * @throws ServletException
     */
    private void ajaxFindDepartmentMaintainerList(final HttpServletRequest req, final HttpServletResponse resp,
                                                  final Session session, final HashMap<String, Object> ret) throws ServletException {

        try {
            // 数据展示
            int start = Integer.valueOf(getParam(req, "start"));
            int pageSize = Integer.valueOf(getParam(req, "pageSize"));
            final String searchterm = getParam(req, "searchterm").trim();
            List<DepartmentMaintainer> departmentMaintainerList;

            // 查询总数量
            int total = 0;

            // 按条件查找变更用户
            if (StringUtils.isNotBlank(searchterm)) {
                departmentMaintainerList = this.systemManager.getDepartmentMaintainerList(searchterm, 0, pageSize);
                total = departmentMaintainerList.size();
            } else {
                // 查找所有用户
                departmentMaintainerList = this.systemManager.getDepartmentMaintainerList(start * pageSize, pageSize);
                total = this.systemManager.getDepartmentMaintainerTotal();
            }
            logger.info("Find Department Maintainer List is " + departmentMaintainerList.toString());


            // 查询所有部门设置的运维人员
            List<Map<String, Object>> departmentMaintainerPageList = new ArrayList<>();
            for (DepartmentMaintainer maintainer : departmentMaintainerList) {
                Map<String, Object> maintainerMap = new HashMap<>();
                maintainerMap.put("departmentId", maintainer.getDepartmentId());
                maintainerMap.put("departmentName", maintainer.getDepartmentName());
                maintainerMap.put("maintainerList", maintainer.getOpsUser());

                departmentMaintainerPageList.add(maintainerMap);
            }
            Map<String, String> dataMap = loadSystemServletI18nData();
            ret.put("modify", dataMap.get("modify"));

            ret.put("total", total);
            ret.put("start", start);
            ret.put("pageSize", pageSize);
            ret.put("departmentMaintainerPageList", departmentMaintainerPageList);
        } catch (Exception e) {
            logger.error("Failed, Error find department maintainer caused by:", e);
            ret.put("error", "Error find Department Maintainer.");
        }

    }

    /**
     * 查看人员变动变更详情
     *
     * @param req
     * @param resp
     * @param session
     * @param ret
     * @throws ServletException
     */
    private void ajaxGetModifyInfoSystemUserById(final HttpServletRequest req, final HttpServletResponse resp,
                                                 final Session session, final HashMap<String, Object> ret) throws ServletException {

        try {
            String userId = getParam(req, "userId");
            // 查询变更的用户详情信息
            String modifyInfo = this.systemManager.getModifyInfoSystemUserById(userId);
            ret.put("modifyInfo", modifyInfo);
        } catch (Exception e) {
            ret.put("error", "Error find modify system user.");
        }

    }

    /**
     * 同步变更用户数据到正常状态
     *
     * @param req
     * @param resp
     * @param session
     * @param ret
     * @throws ServletException
     */
    private void ajaxSyncModifyEsbSystemUsers(final HttpServletRequest req, final HttpServletResponse resp
            , final Session session, final HashMap<String, Object> ret)
            throws ServletException {
        Map<String, String> dataMap = loadSystemServletI18nData();
        List<String> errorIdList = new ArrayList<>();
        try {
            WebankUsersSync webankUsersSync = new WebankUsersSync(this.propsPlugin);
            Map<String, List<String>> originEsbData = webankUsersSync.getOriginEsbData();
            // 更新发生部门变更的人员
            List<String> exchangeDepIdIdWtssUserIdList = originEsbData.get("exchangeDepIdIdWtssUserId");
            if (CollectionUtils.isNotEmpty(exchangeDepIdIdWtssUserIdList)) {
                for (String userId : exchangeDepIdIdWtssUserIdList) {
                    WtssUser exchangeDepIdIdWtssUser = this.systemManager.getSystemUserById(userId);
                    try {
                        if (null != exchangeDepIdIdWtssUser) {
                            WebankUser webankUser = this.systemManager.getWebankUserByUserId(userId);
                            exchangeDepIdIdWtssUser.setDepartmentId(webankUser.departmentId);
                            exchangeDepIdIdWtssUser.setDepartmentName(webankUser.departmentName);
                            exchangeDepIdIdWtssUser.setModifyType("0");
                            exchangeDepIdIdWtssUser.setModifyInfo("Normal");
                            this.systemManager.updateSystemUser(exchangeDepIdIdWtssUser, true);
                        }
                    } catch (SystemUserManagerException e) {
                        errorIdList.add(userId);
                    }
                }
            }

            // 更新无部门的人员
            List<String> noDepIdIdWtssUserIdList = originEsbData.get("noDepIdIdWtssUserId");
            if (CollectionUtils.isNotEmpty(noDepIdIdWtssUserIdList)) {
                for (String userId : noDepIdIdWtssUserIdList) {
                    WtssUser noDepIdIdWtssUserIdWtssUser = this.systemManager.getSystemUserById(userId);
                    try {
                        if (null != noDepIdIdWtssUserIdWtssUser) {
                            WebankUser webankUser = this.systemManager.getWebankUserByUserId(userId);
                            noDepIdIdWtssUserIdWtssUser.setDepartmentId(webankUser.departmentId);
                            noDepIdIdWtssUserIdWtssUser.setDepartmentName(webankUser.departmentName);
                            noDepIdIdWtssUserIdWtssUser.setModifyType("0");
                            noDepIdIdWtssUserIdWtssUser.setModifyInfo("Normal");
                            this.systemManager.updateSystemUser(noDepIdIdWtssUserIdWtssUser, true);
                        }
                    } catch (SystemUserManagerException e) {
                        errorIdList.add(userId);
                    }
                }
            }

            // 更新其他变化的人员, 例如id变化, 此处不能根据wtss_user的id来进行更新了,因为根据新id,找不出要更新的数据
            List<String> otherChangeWtssUserIdList = originEsbData.get("otherChangeWtssUserId");
            if (CollectionUtils.isNotEmpty(otherChangeWtssUserIdList)) {
                for (String userName : otherChangeWtssUserIdList) {
                    WtssUser otherChangeWtssUser = this.systemManager.getSystemUserByUserName(userName);
                    try {
                        if (null != otherChangeWtssUser) {
                            WebankUser webankUser = this.systemManager.getWebankUserByUserName(userName);
                            otherChangeWtssUser.setUserId(webankUser.userId);
                            otherChangeWtssUser.setDepartmentId(webankUser.departmentId);
                            otherChangeWtssUser.setDepartmentName(webankUser.departmentName);

                            // 判断是否离职
                            if ("N".equalsIgnoreCase(webankUser.isActive)) {
                                otherChangeWtssUser.setModifyType("1");
                                otherChangeWtssUser.setModifyInfo("Dimission");
                            } else {
                                otherChangeWtssUser.setModifyType("0");
                                otherChangeWtssUser.setModifyInfo("Normal");
                            }

                            // 注意: 此处不能根据wtss_user的id来进行更新了,因为根据新id,找不出要更新的数据,所以需要根据name来进行更新
                            this.systemManager.updateSystemUserByName(otherChangeWtssUser, true);
                        }
                    } catch (SystemUserManagerException e) {
                        errorIdList.add(otherChangeWtssUser.getUserId());
                    }
                }
            }

            // 关闭连接
            webankUsersSync.closeConn();

        } catch (Exception e) {
            ret.put("error", dataMap.get("syncEsbUserDataFailed") + errorIdList.toString());
        }

        ret.put("message", dataMap.get("syncEsbUserDataComplete"));
    }

    /**
     * 查询所有变更人员
     *
     * @param req
     * @param resp
     * @param session
     * @param ret
     * @throws ServletException
     */
    private void ajaxFindModifySystemUserPage(final HttpServletRequest req, final HttpServletResponse resp,
                                              final Session session, final HashMap<String, Object> ret) throws ServletException {
        Map<String, String> dataMap = loadSystemServletI18nData();
        try {

            WebankUsersSync webankUsersSync = new WebankUsersSync(this.propsPlugin);
            // 获取用户数据
            Map<String, List<String>> originEsbData = webankUsersSync.getOriginEsbData();

            if (MapUtils.isNotEmpty(originEsbData)) {
                // 部门变更
                List<String> exchangeDepIdIdWtssUserIdList = originEsbData.get("exchangeDepIdIdWtssUserId");
                if (CollectionUtils.isNotEmpty(exchangeDepIdIdWtssUserIdList)) {
                    for (String userId : exchangeDepIdIdWtssUserIdList) {
                        WtssUser exchangeDepIdIdWtssUser = this.systemManager.getSystemUserById(userId);
                        if (null != exchangeDepIdIdWtssUser) {
                            exchangeDepIdIdWtssUser.setModifyType("2");
                            exchangeDepIdIdWtssUser.setModifyInfo("Exchange Department");
                            this.systemManager.updateSystemUser(exchangeDepIdIdWtssUser, true);
                        }
                    }
                }

                // 无部门
                List<String> noOrgIdWtssUserIdList = originEsbData.get("noDepIdIdWtssUserId");
                if (CollectionUtils.isNotEmpty(noOrgIdWtssUserIdList)) {
                    for (String userId : noOrgIdWtssUserIdList) {
                        WtssUser noOrgIdWtssUser = this.systemManager.getSystemUserById(userId);
                        if (null != noOrgIdWtssUser) {
                            noOrgIdWtssUser.setModifyType("3");
                            noOrgIdWtssUser.setModifyInfo("Non Department");
                            this.systemManager.updateSystemUser(noOrgIdWtssUser, true);
                        }
                    }
                }

                // 找出变化的用户数据,key为变化的类型,value为对应的人员id
                List<String> leaveOffWtssUserIdList = originEsbData.get("leaveOffWtssUserId");
                // 获取当前执行更新的时间
                if (CollectionUtils.isNotEmpty(leaveOffWtssUserIdList)) {
                    for (String userId : leaveOffWtssUserIdList) {
                        WtssUser leaveOffWtssUser = this.systemManager.getSystemUserById(userId);
                        if (null != leaveOffWtssUser) {
                            leaveOffWtssUser.setModifyType("1");
                            // 离职(Dimission)
                            leaveOffWtssUser.setModifyInfo("Dimission");
                            this.systemManager.updateSystemUser(leaveOffWtssUser, true);
                        }
                    }
                }

                // 找出变化的用户数据,key为变化的类型,value为对应的人员id
                List<String> otherChangeWtssUserIdList = originEsbData.get("otherChangeWtssUserId");
                // 获取当前执行更新的时间
                if (CollectionUtils.isNotEmpty(otherChangeWtssUserIdList)) {
                    for (String userName : otherChangeWtssUserIdList) {
                        WtssUser otherChangeWtssUser = this.systemManager.getSystemUserByUserName(userName);
                        if (null != otherChangeWtssUser) {
                            otherChangeWtssUser.setModifyType("4");

                            // 其他变更
                            otherChangeWtssUser.setModifyInfo("Other");
                            this.systemManager.updateSystemUser(otherChangeWtssUser, true);
                        }
                    }
                }
            }
            // 关闭连接
            webankUsersSync.closeConn();

            // 数据展示
            int start = Integer.valueOf(getParam(req, "start"));
            int pageSize = Integer.valueOf(getParam(req, "pageSize"));
            final String searchterm = getParam(req, "searchterm").trim();
            List<WtssUser> modifyUserPageList;

            // 查询总数量
            int total = 0;

            // 按条件查找变更用户
            if (StringUtils.isNotBlank(searchterm)) {
                start = 0;
                modifyUserPageList = this.systemManager.getModifySystemUser(searchterm, start * pageSize, pageSize);
                total = modifyUserPageList.size();
            } else {
                // 查找所有用户
                modifyUserPageList = this.systemManager.getModifySystemUser(start * pageSize, pageSize);

                // 查询所有变更
                List<WtssUser> allModifyUserList = this.systemManager.getModifySystemUser(null);
                total = allModifyUserList.size();
            }

            List<Long> dpIds = modifyUserPageList.stream().map(x -> x.getDepartmentId()).collect(Collectors.toList());
            Map<Long, List<Integer>> departmentExecutorMap = this.systemManager.getDepartmentExecutor(dpIds);

            // 查询所有变更的用户
            List<Map<String, Object>> wtssUserPageList = new ArrayList<>();
            for (WtssUser wtssUser : modifyUserPageList) {
                Map<String, Object> wtssUserMap = new HashMap<>();
                wtssUserMap.put("userId", wtssUser.getUserId());
                wtssUserMap.put("username", wtssUser.getUsername());
                wtssUserMap.put("fullName", wtssUser.getFullName());
                wtssUserMap.put("departmentName", wtssUser.getDepartmentName());
                wtssUserMap.put("email", wtssUser.getEmail());
                wtssUserMap.put("proxyUsers", wtssUser.getProxyUsers());
                wtssUserMap.put("role", wtssUser.getRoleId() == 1 ? dataMap.get("admin") : dataMap.get("ordinaryUsers"));
                wtssUserMap.put("permission", this.systemManager.getUserPermission(wtssUser.getRoleId()));
                wtssUserMap.put("userType", wtssUser.getUserType());
                String modifyType = wtssUser.getModifyType();
                Integer tempType = Integer.valueOf(modifyType);
                WtssUser.ModifySystemUserType enumByType = WtssUser.ModifySystemUserType.getEnumByType(tempType);
                List<Integer> executors = departmentExecutorMap.get(wtssUser.getDepartmentId());
                String statusDesc = enumByType.getStatusDesc();
                if(CollectionUtils.isEmpty(executors)){
                    statusDesc += "(no executor)";
                }
                wtssUserMap.put("modifyType", statusDesc);
                wtssUserMap.put("modifyInfo", wtssUser.getModifyInfo());

                wtssUserPageList.add(wtssUserMap);
            }
            ret.put("total", total);
            ret.put("start", start);
            ret.put("pageSize", pageSize);
            ret.put("modifySystemUserPageList", wtssUserPageList);
        } catch (Exception e) {
            ret.put("error", "Error find modify system user.");
        }

    }

    private void downloadAllModifyDetailInfo(final HttpServletRequest req, final HttpServletResponse resp,
                                             final Session session) throws ServletException {
        OutputStream os = null;
        Map<String, String> dataMap = loadSystemServletI18nData();
        try {
            DateTime dateTime = new DateTime(new Date());
            String downloadDate = dateTime.toString("yyyy-MM-dd");

            // 查询所有变更的用户
            List<WtssUser> modifyUserList = this.systemManager.getModifySystemUser(null);

            List<ModifyWtssUserDto> modifyWtssUserDtoList = new ArrayList<>();
            List<Long> dpIds = modifyUserList.stream().map(x -> x.getDepartmentId()).collect(Collectors.toList());
            Map<Long, List<Integer>> departmentExecutorMap = this.systemManager.getDepartmentExecutor(dpIds);

            for (int i = 0; i < modifyUserList.size(); i++) {
                WtssUser wtssUser = modifyUserList.get(i);
                ModifyWtssUserDto modifyWtssUserDto = new ModifyWtssUserDto();
                modifyWtssUserDto.setExcelId(i + 1);
                modifyWtssUserDto.setUserId(wtssUser.getUserId());
                modifyWtssUserDto.setFullName(wtssUser.getFullName());
                modifyWtssUserDto.setDepartmentName(wtssUser.getDepartmentName());
                modifyWtssUserDto.setProxyUsers(wtssUser.getProxyUsers());

                // 用户角色
                int roleId = wtssUser.getRoleId();
                WtssRole wtssRole = this.systemManager.getWtssRoleById(roleId);
                if (null != wtssRole) {
                    if (roleId == 1) {
                        modifyWtssUserDto.setRoleName(dataMap.get("admin"));
                    } else {
                        modifyWtssUserDto.setRoleName(dataMap.get("ordinaryUsers"));
                    }
                }

                // 用户权限
                String userPermission = this.systemManager.getUserPermission(roleId);
                modifyWtssUserDto.setRightAccess(userPermission);

                // 用户邮箱
                modifyWtssUserDto.setEmail(wtssUser.getEmail());

                // 用户变更类型
                String modifyType = wtssUser.getModifyType();
                Integer tempType = Integer.valueOf(modifyType);
                WtssUser.ModifySystemUserType enumByType = WtssUser.ModifySystemUserType.getEnumByType(tempType);
                String statusDesc = enumByType.getStatusDesc();
                List<Integer> executors = departmentExecutorMap.get(wtssUser.getDepartmentId());
                if(CollectionUtils.isEmpty(executors)){
                    statusDesc += "(no executor)";
                }
                modifyWtssUserDto.setModifyType(statusDesc);
                modifyWtssUserDtoList.add(modifyWtssUserDto);
            }
            // 执行导出
            ExcelUtil.writeExcel(resp, modifyWtssUserDtoList, dataMap.get("userDataModifyTable")+ "_" + downloadDate, dataMap.get("userDataModifyTable"), new ModifyWtssUserDto());

        } catch (final Throwable e) {
            logger.error("download modify system user info failed, caused by:{}", e);
            throw new ServletException(e);
        } finally {
            // 关闭流
            IOUtils.closeQuietly(os);
        }

    }


    private void ajaxFetchAllDepartmentGroup(final HttpServletRequest req, final HttpServletResponse resp, final Session session, final HashMap<String, Object> ret) throws ServletException {
        List<DepartmentGroup> departmentGroups = this.systemManager.fetchAllDepartmentGroup();
        Map<String, String> dataMap = loadSystemServletI18nData();
        ret.put("modify", dataMap.get("modify"));
        ret.put("departmentGroups", departmentGroups);
    }

    private void ajaxAddDepartmentGroup(final HttpServletRequest req, final HttpServletResponse resp, final Session session, final HashMap<String, Object> ret) throws ServletException {

        JsonObject jsonObject = HttpRequestUtils.parseRequestToJsonObject(req);
        DepartmentGroup departmentGroup = GsonUtils.jsonToJavaObject(jsonObject, DepartmentGroup.class);
        Map<String, String> dataMap = loadSystemServletI18nData();
        //校验名字是否已存在
        if (this.systemManager.checkGroupNameIsExist(departmentGroup)) {
            ret.put("error", "Existed Group Name");
            return;
        }
        //校验机器是否已被使用
        for (int execuotrId : departmentGroup.getExecutorIds()) {
            if (this.systemManager.checkExecutorIsUsed(execuotrId)) {
                ret.put("error", String.format("executor:[%d] was Used", execuotrId));
                return;
            }
        }

        if (this.systemManager.addDepartmentGroup(departmentGroup)) {
            ret.put("success", dataMap.get("requestSuccess"));
        } else {
            ret.put("error", dataMap.get("checkGroup_1"));
        }
    }


    private void ajaxDeleteDepartmentGroup(final HttpServletRequest req, final HttpServletResponse resp, final Session session, final HashMap<String, Object> ret) throws ServletException {
        JsonObject jsonObject = HttpRequestUtils.parseRequestToJsonObject(req);
        DepartmentGroup departmentGroup = GsonUtils.jsonToJavaObject(jsonObject, DepartmentGroup.class);
        Map<String, String> dataMap = loadSystemServletI18nData();
        if (this.systemManager.groupIdIsExist(departmentGroup)) {
            ret.put("error", dataMap.get("checkGroup_2"));
            return;
        }
        if (this.systemManager.deleteDepartmentGroup(departmentGroup)) {
            ret.put("success", dataMap.get("requestSuccess"));
        } else {
            ret.put("error", dataMap.get("requestFailed"));
        }
    }

    private void ajaxUpdateDepartmentGroup(final HttpServletRequest req, final HttpServletResponse resp, final Session session, final HashMap<String, Object> ret) throws ServletException {

        JsonObject jsonObject = HttpRequestUtils.parseRequestToJsonObject(req);
        DepartmentGroup departmentGroup = GsonUtils.jsonToJavaObject(jsonObject, DepartmentGroup.class);

        if (this.holdBatchContext.getBatchMap().values().stream()
            .filter(opr -> HoldBatchLevel.TENANT.getNumVal() == opr.getOperateLevel()).anyMatch(
                opr -> opr.getDataList().contains(departmentGroup.getOldId()+""))) {
            ret.put("error", "group[" + departmentGroup.getOldId() + "] is holding, can not update");
            return;
        }

        Map<String, String> dataMap = loadSystemServletI18nData();
        if (this.systemManager.updateDepartmentGroup(departmentGroup)) {
            ret.put("success", dataMap.get("requestSuccess"));
        } else {
            ret.put("error", dataMap.get("checkGroup_1"));
        }
    }

    private void ajaxFetchDepartmentGroupById(final HttpServletRequest req, final HttpServletResponse resp, final Session session, final HashMap<String, Object> ret) throws ServletException {

        JsonObject jsonObject = HttpRequestUtils.parseRequestToJsonObject(req);
        DepartmentGroup departmentGroup = GsonUtils.jsonToJavaObject(jsonObject, DepartmentGroup.class);
        DepartmentGroup dg = this.systemManager.getDepartmentGroupById(departmentGroup.getId());
        Map<String, String> dataMap = loadSystemServletI18nData();
        if (dg != null) {
            ret.put("departmentGroup", dg);
        } else {
            ret.put("error", dataMap.get("requestFailed"));
        }
    }

    private void ajaxFetchExecutors(final HttpServletRequest req, final HttpServletResponse resp, final Session session,
                                    final HashMap<String, Object> ret) throws ServletException {
        List<Executor> executors = this.systemManager.fetchAllExecutors();
        ret.put("executors", executors);
    }

    private void ajaxFetchAllExceptionUsers(final HttpServletRequest req, final HttpServletResponse resp, final Session session,
                                            final HashMap<String, Object> ret) throws ServletException {
        try {
            String searchName = getParam(req, "searchName");
            int pageNum = getIntParam(req, "pageNum");
            int pageSize = getIntParam(req, "pageSize");
            ret.put("exceptionalUsers", this.systemManager.fetchAllExceptionalUsers(searchName, pageNum * pageSize, pageSize));
            ret.put("total", this.systemManager.getTotalExceptionalUser(searchName));
        }catch (Exception e){
            ret.put("error", e.getMessage());
        }
    }
    private void ajaxAddExceptionalUser(final HttpServletRequest req, final HttpServletResponse resp, final Session session,
                                            final HashMap<String, Object> ret) throws ServletException {
        try {
            String userId = getParam(req, "userId");
            this.systemManager.addExceptionalUser(userId);
        }catch (Exception e){
            ret.put("error", e.getMessage());
        }
    }
    private void ajaxDeleteExceptionalUser(final HttpServletRequest req, final HttpServletResponse resp, final Session session,
                                            final HashMap<String, Object> ret) throws ServletException {
        try {
            String userId = getParam(req, "userId");
            this.systemManager.deleteExceptionalUser(userId);
        }catch (Exception e){
            ret.put("error", e.getMessage());
        }
    }

    private void ajaxPrivilegeReport(final HttpServletRequest req, final HttpServletResponse resp, final Session session,
        final HashMap<String, Object> ret) throws ServletException {
        try {
            // 检查调用接口的用户是否为系统管理员
            if (!(session.getUser().hasRole("admin"))) {
                ret.put("code", 403);
                ret.put("error", "当前用户不为系统管理员，无法进行权限上报。");
                return;
            }
            ret.put("code", 200);
            ret.put("data", this.systemManager.getPrivilegeReport());
        } catch (Exception e) {
            ret.put("code", 500);
            ret.put("error", e.getMessage());
        }
    }
}
