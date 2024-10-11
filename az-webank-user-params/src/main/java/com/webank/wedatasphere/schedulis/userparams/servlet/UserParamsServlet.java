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

package com.webank.wedatasphere.schedulis.userparams.servlet;

import azkaban.ServiceProvider;
import azkaban.server.HttpRequestUtils;
import azkaban.server.session.Session;
import azkaban.utils.Props;
import azkaban.webapp.servlet.LoginAbstractAzkabanServlet;
import azkaban.webapp.servlet.Page;
import com.google.gson.JsonObject;
import com.google.inject.Injector;
import com.webank.wedatasphere.schedulis.common.executor.UserVariable;
import com.webank.wedatasphere.schedulis.common.i18nutils.LoadJsonUtils;
import com.webank.wedatasphere.schedulis.common.system.entity.WtssUser;
import com.webank.wedatasphere.schedulis.common.utils.GsonUtils;
import com.webank.wedatasphere.schedulis.userparams.module.UserParamsModule;
import com.webank.wedatasphere.schedulis.userparams.service.UserParamsService;
import org.json.JSONObject;

import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;


public class UserParamsServlet extends LoginAbstractAzkabanServlet {

    private static final long serialVersionUID = 1L;
    private UserParamsService userParamsService;
    private final Props props;
    private final File webResourcesPath;

    private final String viewerName;
    private final String viewerPath;

    public UserParamsServlet(final Props props){

        this.props = props;
        this.viewerName = props.getString("viewer.name");
        this.viewerPath = props.getString("viewer.path");
        this.webResourcesPath = new File(new File(props.getSource()).getParentFile().getParentFile(),"web");
        this.webResourcesPath.mkdirs();

        setResourceDirectory(this.webResourcesPath);

    }

    @Override
    public void init(final ServletConfig config) throws ServletException {
        super.init(config);
        Injector injector = ServiceProvider.SERVICE_PROVIDER.getInjector()
            .createChildInjector(new UserParamsModule());
        userParamsService = injector.getInstance(UserParamsService.class);
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

        if (ajaxName.equals("user_role")) {
            ajaxGetUserRole(req, resp, session, ret);
        } else if (ajaxName.equals("fetchAllUserVariable")){
            ajaxFetchAllUserVariable(req, resp, session, ret);
        } else if (ajaxName.equals("addUserVariable")){
            ajaxAddUserVariable(req, resp, session, ret);
        } else if (ajaxName.equals("deleteUserVariable")){
            ajaxDeleteUserVariable(req, resp, session, ret);
        } else if (ajaxName.equals("updateUpdateUserVariable")){
            ajaxUpdateUpdateUserVariable(req, resp, session, ret);
        } else if (ajaxName.equals("fetchUserVariableById")){
            ajaxFetchUserVariableById(req, resp, session, ret);
        } else if(ajaxName.equals("loadWtssUser")){
            ajaxLoadWtssUser(req, resp, session, ret);
        }

        if (ret != null) {
            this.writeJSON(resp, ret);
        }
    }

    /**
     * 返回当前用户的角色列表
     */
    private void ajaxGetUserRole(final HttpServletRequest req,
        final HttpServletResponse resp, final Session session, final HashMap<String, Object> ret) {
        final String[] userRoles = session.getUser().getRoles().toArray(new String[0]);
        ret.put("userRoles", userRoles);
    }

    /**
     * 数据补采历史页面
     * @param req
     * @param resp
     * @param session
     * @throws ServletException
     */
    private void handleSystemPage(final HttpServletRequest req, final HttpServletResponse resp, final Session session)
        throws ServletException {
        final Page page =
            newPage(req, resp, session, "/com.webank.wedatasphere.schedulis.viewer.userparams/userparams-manager.vm");
        page.add("wtssUser", session.getUser().getUserId());

        String languageType = LoadJsonUtils.getLanguageType();
        Map<String, String> viewDataMap;
        Map<String, String> subPageMap1;
        if (languageType.equalsIgnoreCase("zh_CN")) {
            viewDataMap = LoadJsonUtils.transJson("/com.webank.wedatasphere.schedulis.i18n.conf/az-webank-user-params-zh_CN.json",
                "com.webank.wedatasphere.schedulis.viewer.userparams.userparams-manager.vm");
            subPageMap1 = LoadJsonUtils.transJson("/com.webank.wedatasphere.schedulis.i18n.conf/azkaban-web-server-zh_CN.json",
                "azkaban.webapp.servlet.velocity.nav.vm");
        }else {
            viewDataMap = LoadJsonUtils.transJson("/com.webank.wedatasphere.schedulis.i18n.conf/az-webank-user-params-en_US.json",
                "com.webank.wedatasphere.schedulis.viewer.userparams.userparams-manager.vm");
            subPageMap1 = LoadJsonUtils.transJson("/com.webank.wedatasphere.schedulis.i18n.conf/azkaban-web-server-en_US.json",
                "azkaban.webapp.servlet.velocity.nav.vm");
        }

        // 添加国际化标签
        viewDataMap.forEach(page::add);
        subPageMap1.forEach(page::add);
        page.add("currentlangType", languageType);
        page.render();

    }

    /**
     * 加载 UserParamsServlet 中的异常信息等国际化资源
     * @return
     */
    private Map<String, String> loadUserParamsServletI18nData() {
        String languageType = LoadJsonUtils.getLanguageType();
        Map<String, String> dataMap;
        if (languageType.equalsIgnoreCase("zh_CN")) {
            dataMap = LoadJsonUtils.transJson("/com.webank.wedatasphere.schedulis.i18n.conf/az-webank-user-params-zh_CN.json",
                "com.webank.wedatasphere.schedulis.userparams.servlet.UserParamsServlet");
        }else {
            dataMap = LoadJsonUtils.transJson("/com.webank.wedatasphere.schedulis.i18n.conf/az-webank-user-params-en_US.json",
                "com.webank.wedatasphere.schedulis.userparams.servlet.UserParamsServlet");
        }
        return dataMap;
    }

    @Override
    protected void handlePost(final HttpServletRequest req, final HttpServletResponse resp,
        final Session session) throws ServletException, IOException {
        if (hasParam(req, "ajax")) {
            handleAJAXAction(req, resp, session);
        }
    }


    private void ajaxFetchAllUserVariable(final HttpServletRequest req, final HttpServletResponse resp, final Session session, final HashMap<String, Object> ret) throws ServletException {
        JsonObject jsonObject = HttpRequestUtils.parseRequestToJsonObject(req);
        UserVariable userVariable = GsonUtils.jsonToJavaObject(jsonObject, UserVariable.class);
        if(!userVariable.getOwner().equals(session.getUser().getUserId())){
            ret.put("error", "No Access Permission");
            return;
        }
        List<UserVariable> userVariables = this.userParamsService.fetchAllUserVariable(userVariable);
        ret.put("userparams", userVariables);
        Map<String, String> sourceMap = loadUserParamsServletI18nData();
        ret.put("modify", sourceMap.get("modify"));

    }

    private void ajaxAddUserVariable(final HttpServletRequest req, final HttpServletResponse resp, final Session session, final HashMap<String, Object> ret) throws ServletException {
        JsonObject jsonObject = HttpRequestUtils.parseRequestToJsonObject(req);
        UserVariable userVariable = GsonUtils.jsonToJavaObject(jsonObject, UserVariable.class);

        for(WtssUser user: userVariable.getUsers()){
            if(!this.userParamsService.checkWtssUserIsExist(user.getUserId())){
                ret.put("error", "Failed, User[" + user.getUserId() + "] not exist.");
                return;
            }
        }

        userVariable.setOwner(session.getUser().getUserId());
        if(this.userParamsService.addUserVariable(userVariable)){
            ret.put("success", "Request Success");
        } else {
            ret.put("error", "Request Failed");
        }
    }


    private void ajaxDeleteUserVariable(final HttpServletRequest req, final HttpServletResponse resp, final Session session, final HashMap<String, Object> ret) throws ServletException {
        JsonObject jsonObject = HttpRequestUtils.parseRequestToJsonObject(req);
        UserVariable userVariable = GsonUtils.jsonToJavaObject(jsonObject, UserVariable.class);
        UserVariable findById = this.userParamsService
            .getUserVariableById(jsonObject.get("id").getAsInt());
        if (findById == null) {
            ret.put("error", "Not find the UserVariable by ID");
            return;
        }
        userVariable.setOwner(findById.getOwner());
        if(!userVariable.getOwner().equals(session.getUser().getUserId())){
            ret.put("error", "No Access Permission");
            return;
        }
        userVariable.setOwner(session.getUser().getUserId());
        if(this.userParamsService.deleteUserVariable(userVariable)){
            ret.put("success", "Request Success");
        } else {
            ret.put("error", "Request Failed");
        }
    }

    private void ajaxUpdateUpdateUserVariable(final HttpServletRequest req, final HttpServletResponse resp, final Session session, final HashMap<String, Object> ret) throws ServletException {
        JsonObject jsonObject = HttpRequestUtils.parseRequestToJsonObject(req);
        UserVariable userVariable = GsonUtils.jsonToJavaObject(jsonObject, UserVariable.class);
        UserVariable findById = this.userParamsService
            .getUserVariableById(jsonObject.get("id").getAsInt());
        if (findById == null) {
            ret.put("error", "Not find the UserVariable by ID");
            return;
        }
        userVariable.setOwner(findById.getOwner());
        if(!userVariable.getOwner().equals(session.getUser().getUserId())){
            ret.put("error", "No Access Permission");
            return;
        }
        userVariable.setOwner(session.getUser().getUserId());
        for(WtssUser user: userVariable.getUsers()){
            if(!this.userParamsService.checkWtssUserIsExist(user.getUserId())){
                ret.put("error", "Failed, User[" + user.getUserId() + "] not exist.");
                return;
            }
        }
        if(this.userParamsService.updateUserVariable(userVariable)){
            ret.put("success", "Request Success");
        } else {
            ret.put("error", "Request Failed");
        }
    }

    private void ajaxFetchUserVariableById(final HttpServletRequest req, final HttpServletResponse resp, final Session session, final HashMap<String, Object> ret) throws ServletException {
        JsonObject jsonObject = HttpRequestUtils.parseRequestToJsonObject(req);
        UserVariable userVariable = GsonUtils.jsonToJavaObject(jsonObject, UserVariable.class);
        userVariable = this.userParamsService.getUserVariableById(userVariable.getId());
        if(userVariable != null) {
            ret.put("userparam", userVariable);
        } else {
            ret.put("error", "Can not find.");
        }
    }


    private void ajaxLoadWtssUser(final HttpServletRequest req, final HttpServletResponse resp, final Session session, final HashMap<String, Object> ret) throws ServletException {

        String searchName = req.getParameter("serach");
        int pageNum = getIntParam(req, "page");
        int pageSize = getIntParam(req, "pageSize");
        List<Map<String, Object>> webankUserSelectList = new ArrayList<>();
        JSONObject items = new JSONObject();
        try {
            int webankUserTotalCount = this.userParamsService.getWtssUserTotal();
            List<WtssUser> webankUserList = this.userParamsService.findAllWtssUserPageList(searchName, (pageNum-1) * pageSize, pageSize);

            for(WtssUser webankUser : webankUserList){
                Map<String, Object> selectItem = new HashMap<>();
                selectItem.put("id", webankUser.getUsername());
                selectItem.put("username", webankUser.getUsername());
                selectItem.put("text", webankUser.getFullName());
                webankUserSelectList.add(selectItem);
            }
            ret.put("webankUserTotalCount", webankUserTotalCount);

        } catch (Exception e) {
            e.printStackTrace();
        }

        ret.put("page", pageNum);
        ret.put("webankUserList", webankUserSelectList);
    }
}
