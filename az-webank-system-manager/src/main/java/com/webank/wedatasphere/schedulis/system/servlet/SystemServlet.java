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

package com.webank.wedatasphere.schedulis.system.servlet;

import azkaban.ServiceProvider;
import azkaban.executor.Executor;
import azkaban.server.HttpRequestUtils;
import azkaban.server.session.Session;
import azkaban.user.User;
import azkaban.utils.Props;
import azkaban.webapp.servlet.HistoryServlet;
import azkaban.webapp.servlet.LoginAbstractAzkabanServlet;
import azkaban.webapp.servlet.Page;
import com.google.common.base.Joiner;
import com.google.gson.JsonObject;
import com.google.inject.Injector;
import com.webank.wedatasphere.schedulis.common.executor.DepartmentGroup;
import com.webank.wedatasphere.schedulis.common.i18nutils.LoadJsonUtils;
import com.webank.wedatasphere.schedulis.common.utils.GsonUtils;
import com.webank.wedatasphere.schedulis.system.entity.WebankDepartment;
import com.webank.wedatasphere.schedulis.system.entity.WebankUser;
import com.webank.wedatasphere.schedulis.system.entity.WtssUser;
import com.webank.wedatasphere.schedulis.system.exception.SystemUserManagerException;
import com.webank.wedatasphere.schedulis.system.module.SystemModule;
import com.webank.wedatasphere.schedulis.system.service.impl.SystemManager;
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class SystemServlet extends LoginAbstractAzkabanServlet {

    private static final Logger logger = LoggerFactory.getLogger(SystemServlet.class.getName());
    private static final long serialVersionUID = 1L;
    private SystemManager systemManager;
    private Props propsPlugin;
    private Props propsAzkaban;
    private final File webResourcesPath;

    private final String viewerName;
    private final String viewerPath;

    public SystemServlet(final Props propsPlugin) {

        this.propsPlugin = propsPlugin;
        this.viewerName = propsPlugin.getString("viewer.name");
        this.viewerPath = propsPlugin.getString("viewer.path");

        this.webResourcesPath = new File(new File(propsPlugin.getSource()).getParentFile().getParentFile(), "web");
        this.webResourcesPath.mkdirs();

        setResourceDirectory(this.webResourcesPath);

    }

    @Override
    public void init(final ServletConfig config) throws ServletException {
        super.init(config);

        Injector injector = ServiceProvider.SERVICE_PROVIDER.getInjector().createChildInjector(new SystemModule());
        systemManager = injector.getInstance(SystemManager.class);
        propsAzkaban = ServiceProvider.SERVICE_PROVIDER.getInstance(Props.class);
    }

    @Override
    protected void handleGet(final HttpServletRequest req, final HttpServletResponse resp,
                             final Session session) throws ServletException, IOException {

        if (hasParam(req, "ajax")) {
            handleAJAXAction(req, resp, session);
        } else {
            handleSystemPage(req, resp, session);
        }
    }

    private void handleAJAXAction(final HttpServletRequest req,
                                  final HttpServletResponse resp, final Session session) throws ServletException,
            IOException {
        final HashMap<String, Object> ret = new HashMap<>();
        final String ajaxName = getParam(req, "ajax");

        final User user = session.getUser();
        if (!user.getRoles().contains("admin")) {
            if (!"loadSystemUserSelectData".equals(ajaxName)) {
                ret.put("error", "No Access Permission");
                if (ret != null) {
                    this.writeJSON(resp, ret);
                }
                return;
            }
        }

        if (ajaxName.equals("addSystemUserViaFastTrack")) {
            // 通过非登录页面的快速通道新增用户
            ajaxAddSystemUserViaFastTrack(req, resp, session, ret);
        } else if (ajaxName.equals("fetch")) {
            fetchHistoryData(req, resp, ret);
        } else if (ajaxName.equals("user_role")) {
            ajaxGetUserRole(req, resp, session, ret);
        } else if (ajaxName.equals("loadWebankUserSelectData")) {
            ajaxLoadWebankUserSelectData(req, resp, session, ret);
        } else if (ajaxName.equals("findSystemUserPage")) {
            ajaxFindSystemUserPage(req, resp, session, ret);
        } else if (ajaxName.equals("addSystemUser")) {
            ajaxAddSystemUser(req, resp, session, ret);
        } else if (ajaxName.equals("getSystemUserById")) {
            ajaxGetSystemUserById(req, resp, session, ret);
        } else if (ajaxName.equals("updateSystemUser")) {
            ajaxUpdateSystemUser(req, resp, session, ret);
        } else if (ajaxName.equals("loadSystemUserSelectData")) {
            ajaxLoadSystemUserSelectData(req, resp, session, ret);
        } else if (ajaxName.equals("loadWebankDepartmentSelectData")) {
            ajaxLoadWebankDepartmentSelectData(req, resp, session, ret);
        } else if (ajaxName.equals("deleteSystemUser")) {
            ajaxDeleteSystemUser(req, resp, session, ret);
        } else if (ajaxName.equals("syncXmlUsers")) {
            ajaxSyncXmlUsers(req, resp, session, ret);
        } else if (ajaxName.equals("findSystemDeparmentPage")) {
            ajaxFindSystemDeparmentPage(req, resp, session, ret);
        } else if (ajaxName.equals("addDeparment")) {
            ajaxAddDeparment(req, resp, session, ret);
        } else if (ajaxName.equals("updateDeparment")) {
            ajaxUpdateDeparment(req, resp, session, ret);
        } else if (ajaxName.equals("getDeparmentById")) {
            ajaxGetDeparmentById(req, resp, session, ret);
        } else if (ajaxName.equals("deleteDeparment")) {
            ajaxDeleteDeparment(req, resp, session, ret);
        } else if (ajaxName.equals("fetchAllDepartmentGroup")) {
            ajaxFetchAllDepartmentGroup(req, resp, session, ret);
        } else if (ajaxName.equals("addDepartmentGroup")) {
            ajaxAddDepartmentGroup(req, resp, session, ret);
        } else if (ajaxName.equals("deleteDepartmentGroup")) {
            ajaxDeleteDepartmentGroup(req, resp, session, ret);
        } else if (ajaxName.equals("updateDepartmentGroup")) {
            ajaxUpdateDepartmentGroup(req, resp, session, ret);
        } else if (ajaxName.equals("fetchDepartmentGroupById")) {
            ajaxFetchDepartmentGroupById(req, resp, session, ret);
        } else if (ajaxName.equals("fetchExecutors")) {
            ajaxFetchExecutors(req, resp, session, ret);
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
        if (languageType.equalsIgnoreCase("zh_CN")) {
            dataMap = LoadJsonUtils.transJson("/com.webank.wedatasphere.schedulis.i18n.conf/az-webank-system-manager-zh_CN.json",
                    "com.webank.wedatasphere.schedulis.system.servlet.SystemServlet");
        }else {
            dataMap = LoadJsonUtils.transJson("/com.webank.wedatasphere.schedulis.i18n.conf/az-webank-system-manager-en_US.json",
                    "com.webank.wedatasphere.schedulis.system.servlet.SystemServlet");
        }
        return dataMap;
    }


    /**
     * 通过非登录页面的快速通道新增用户
     *
     * @param req
     * @param resp
     * @param ret
     * @throws ServletException
     */
    private void ajaxAddSystemUserViaFastTrack(final HttpServletRequest req, final HttpServletResponse resp,
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

        try {

            if (StringUtils.isBlank(userId)) {
                throw new SystemUserManagerException("未填写用户ID。");
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

                                logger.info("for update value is userId:{}, password:{}, roleId:{}, proxyUser:{}, departmentId:{}", userId, password, roleId, proxyUser, departmentId);
                                addResult = this.systemManager.updateSystemUser(userId, password, roleId, proxyUser, departmentId);
                            }
                        } else {

                            // 单个代理用户只需要判断添加的代理是否和用户名一样
                            if (!proxyUser.equals(wtssUser.getUsername())) {
                                proxyUser = wtssUser.getProxyUsers() + "," + proxyUser;
                                addResult = this.systemManager.updateSystemUser(userId, password, roleId, proxyUser, departmentId);
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
                addResult = this.systemManager.addSystemUser(userId, password, roleId, 3, proxyUser, departmentId);
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


        } catch (SystemUserManagerException e) {
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
                newPage(req, resp, session, "/com.webank.wedatasphere.schedulis.viewer.system/system-manager.vm");
        int pageNum = getIntParam(req, "page", 1);
        final int pageSize = getIntParam(req, "size", 16);

        if (pageNum < 0) {
            pageNum = 1;
        }

        final User user = session.getUser();

        page.add("adminPerm", user.getRoles().contains("admin"));

        page.add("size", pageSize);
        page.add("page", pageNum);

        // keep the search terms so that we can navigate to later pages
        if (hasParam(req, "searchterm") && !getParam(req, "searchterm").equals("")) {
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
        if (languageType.equalsIgnoreCase("zh_CN")) {
             viewDataMap = LoadJsonUtils.transJson("/com.webank.wedatasphere.schedulis.i18n.conf/az-webank-system-manager-zh_CN.json",
                    "com.webank.wedatasphere.schedulis.viewer.system.system-manager.vm");
            subPageMap1 = LoadJsonUtils.transJson("/com.webank.wedatasphere.schedulis.i18n.conf/azkaban-web-server-zh_CN.json",
                    "azkaban.webapp.servlet.velocity.nav.vm");
        }else {
            viewDataMap = LoadJsonUtils.transJson("/com.webank.wedatasphere.schedulis.i18n.conf/az-webank-system-manager-en_US.json",
                    "com.webank.wedatasphere.schedulis.viewer.system.system-manager.vm");
            subPageMap1 = LoadJsonUtils.transJson("/com.webank.wedatasphere.schedulis.i18n.conf/azkaban-web-server-en_US.json",
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
                webankUserSelectList.add(selectItem);
//        child.put("id", webankUser.userId);
//        child.put("text", webankUser.fullName);
//        children.put(child);
            }

//      items.put("items", children);

            ret.put("webankUserTotalCount", webankUserTotalCount);

        } catch (Exception e) {
            e.printStackTrace();
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

        Map<String, String> dataMap = loadSystemServletI18nData();

        int total = 0;
        try {

            //查询数据补采全部记录
            List<WtssUser> wtssUserList;
            if (StringUtils.isNotBlank(searchterm)) {
                // 如果搜索条件非空,则默认从第一页开始查找
                start = 0;
                wtssUserList =
                    this.systemManager.findSystemUserPage(null, searchterm, null,start * pageSize, pageSize);
                total = this.systemManager.getSystemUserTotal(searchterm);
            } else {
                wtssUserList =
                    this.systemManager.findSystemUserPage(null, searchterm, null,start * pageSize, pageSize);
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

        Map<String, String> dataMap = loadSystemServletI18nData();
        try {
            if (0 == roleId) {
                throw new SystemUserManagerException(dataMap.get("plsSelectRole"));
            }
            if (0 == departmentId) {
                throw new SystemUserManagerException(dataMap.get("plsSelectDep"));
            }

            // 校验用户是否存在
            WtssUser tempWtssUser = this.systemManager.getSystemUserById(userId);
            WtssUser featureWtssUser = this.systemManager.getSystemUserById(("wtss_" + userId));
            if ((null != tempWtssUser) || (null != featureWtssUser)) {
                throw new SystemUserManagerException(dataMap.get("userHasExist"));
            }

            int addResult = this.systemManager.addSystemUser(userId, password, roleId, categoryUser, proxyUser, departmentId);
            if (addResult != 1) {
                throw new SystemUserManagerException(dataMap.get("addSystemUserFailed"));
            }
        } catch (SystemUserManagerException e) {
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
                if (Pattern.compile("^[0-9]+$").matcher(userId).matches()) {
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

        Map<String, String> dataMap = loadSystemServletI18nData();
        try {

            if (0 == roleId) {
                throw new SystemUserManagerException(dataMap.get("plsSelectRole"));
            }
            if (0 == departmentId) {
                throw new SystemUserManagerException(dataMap.get("plsSelectDep"));
            }

            int addResult = this.systemManager.updateSystemUser(userId, password, roleId, proxyUser, departmentId);
            if (addResult != 1) {
                throw new SystemUserManagerException(dataMap.get("requestFailed"));
            }
        } catch (Exception e) {
            ret.put("error", e);
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
                    this.systemManager.findSystemUserPage(searchName, null, null,
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
            e.printStackTrace();
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
                Map<String, String> dataMap = loadSystemServletI18nData();

                String dpId = getParam(req, "dpId");
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
            ret.put("error", e);
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

}
