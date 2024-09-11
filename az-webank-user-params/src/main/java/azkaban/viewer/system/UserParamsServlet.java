package azkaban.viewer.system;

import azkaban.ServiceProvider;
import azkaban.executor.UserVariable;
import azkaban.i18n.utils.LoadJsonUtils;
import azkaban.module.UserParamsModule;
import azkaban.server.HttpRequestUtils;
import azkaban.server.session.Session;
import azkaban.service.UserParamsService;
import azkaban.system.entity.WtssUser;
import azkaban.utils.GsonUtils;
import azkaban.utils.Props;
import azkaban.webapp.servlet.AbstractLoginAzkabanServlet;
import azkaban.webapp.servlet.Page;
import com.google.gson.JsonObject;
import com.google.inject.Injector;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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

/**
 * Created by zhu on 7/5/18.
 */
public class UserParamsServlet extends AbstractLoginAzkabanServlet {

    private final static Logger logger = LoggerFactory.getLogger(UserParamsServlet.class);

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

        if ("user_role".equals(ajaxName)) {
            ajaxGetUserRole(req, resp, session, ret);
        } else if ("fetchAllUserVariable".equals(ajaxName)){
            ajaxFetchAllUserVariable(req, resp, session, ret);
        } else if ("addUserVariable".equals(ajaxName)){
            ajaxAddUserVariable(req, resp, session, ret);
        } else if ("deleteUserVariable".equals(ajaxName)){
            ajaxDeleteUserVariable(req, resp, session, ret);
        } else if ("updateUpdateUserVariable".equals(ajaxName)){
            ajaxUpdateUpdateUserVariable(req, resp, session, ret);
        } else if ("fetchUserVariableById".equals(ajaxName)){
            ajaxFetchUserVariableById(req, resp, session, ret);
        } else if("loadWtssUser".equals(ajaxName)){
            ajaxLoadWtssUser(req, resp, session, ret);
        }

        if (ret != null) {
            this.writeJSON(resp, ret);
        }
    }

    //返回当前用户的角色列表
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
            newPage(req, resp, session, "azkaban/viewer/system/userparams-manager.vm");
        page.add("wtssUser", session.getUser().getUserId());

        String languageType = LoadJsonUtils.getLanguageType();
        Map<String, String> viewDataMap;
        Map<String, String> subPageMap1;
        if ("zh_CN".equalsIgnoreCase(languageType)) {
            viewDataMap = LoadJsonUtils.transJson("/conf/az-webank-user-params-zh_CN.json",
                "azkaban.viewer.system.userparams-manager.vm");
            subPageMap1 = LoadJsonUtils.transJson("/conf/azkaban-web-server-zh_CN.json",
                "azkaban.webapp.servlet.velocity.nav.vm");
        }else {
            viewDataMap = LoadJsonUtils.transJson("/conf/az-webank-user-params-en_US.json",
                "azkaban.viewer.system.userparams-manager.vm");
            subPageMap1 = LoadJsonUtils.transJson("/conf/azkaban-web-server-en_US.json",
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
        if ("zh_CN".equalsIgnoreCase(languageType)) {
            dataMap = LoadJsonUtils.transJson("/conf/az-webank-user-params-zh_CN.json",
                "azkaban.viewer.system.UserParamsServlet");
        }else {
            dataMap = LoadJsonUtils.transJson("/conf/az-webank-user-params-en_US.json",
                "azkaban.viewer.system.UserParamsServlet");
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
                ret.put("error", "Failed, User [" + user.getUserId() + "] not exist.");
                return;
            }
        }

        userVariable.setOwner(session.getUser().getUserId());
        try {
            this.userParamsService.addUserVariable(userVariable);
            ret.put("success", "Request Success");
        } catch (Exception e){
            logger.error("add user variable failed", e);
            ret.put("error", e.getMessage());
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
        try {
            String oldKeyName = getParam(req, "oldKeyName");
            this.userParamsService.updateUserVariable(userVariable, oldKeyName);
            ret.put("success", "Request Success");
        } catch (Exception e){
            logger.error("update user variable failed", e);
            ret.put("error", e.getMessage());
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
        List<Map<String, Object>> webankUserSelectList = new ArrayList<>();
        int pageNum = 1;
        try {
            int webankUserTotalCount = this.userParamsService.getWtssUserTotal();
            List<WtssUser> webankUserList;
            if(hasParam(req, "serach")) {
                String searchName = getParam(req, "serach");
                pageNum =  getIntParam(req, "page");
                int pageSize =  getIntParam(req, "pageSize");
                webankUserList = this.userParamsService.findAllWtssUserPageList(searchName, (pageNum - 1) * pageSize, pageSize);
            } else {
                webankUserList = this.userParamsService.findAllWtssUserPageList(null, -1, -1);
            }
            for(WtssUser webankUser : webankUserList){
                Map<String, Object> selectItem = new HashMap<>();
                selectItem.put("id", webankUser.getUsername());
                selectItem.put("username", webankUser.getUsername());
                selectItem.put("text", webankUser.getFullName());
                webankUserSelectList.add(selectItem);
            }
            ret.put("webankUserTotalCount", webankUserTotalCount);

        } catch (Exception e) {
            logger.warn("Failed to load wtss users", e);
        }

        ret.put("page", pageNum);
        ret.put("webankUserList", webankUserSelectList);
    }
}
