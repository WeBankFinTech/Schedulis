package azkaban.event.viewer;

import azkaban.ServiceProvider;
import azkaban.event.entity.EventAuth;
import azkaban.event.entity.EventQueue;
import azkaban.event.entity.EventStatus;
import azkaban.event.module.EventModule;
import azkaban.event.service.EventAuthManager;
import azkaban.event.service.EventQueueManager;
import azkaban.event.service.EventStatusManager;
import azkaban.function.TriadConsumer;
import azkaban.i18n.utils.LoadJsonUtils;
import azkaban.server.HttpRequestUtils;
import azkaban.server.session.Session;
import azkaban.system.SystemManager;
import azkaban.utils.Props;
import azkaban.utils.WebUtils;
import azkaban.webapp.servlet.AbstractLoginAzkabanServlet;
import azkaban.webapp.servlet.Page;
import com.google.common.collect.Lists;
import com.google.gson.JsonObject;
import com.google.inject.Injector;
import io.jsonwebtoken.lang.Collections;
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.lang.StringEscapeUtils;
import org.apache.commons.lang3.StringUtils;

import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static azkaban.ServiceProvider.SERVICE_PROVIDER;

public class EventServlet extends AbstractLoginAzkabanServlet {

    private static final long serialVersionUID = 1L;
    private final Props props;
    private EventAuthManager eventAuthManager;
    private EventQueueManager eventQueueManager;
    private EventStatusManager eventStatusManager;
    private SystemManager systemManager;

    private boolean checkRealNameSwitch;


    public EventServlet(final Props props) {
        this.props = props;
        File webResourcesPath = new File(new File(props.getSource()).getParentFile().getParentFile(), "web");
        webResourcesPath.mkdirs();
        setResourceDirectory(webResourcesPath);
        this.systemManager = ServiceProvider.SERVICE_PROVIDER.getInstance(SystemManager.class);
        this.checkRealNameSwitch = this.props.getBoolean("realname.check.switch", true);
    }

    @Override
    public void init(ServletConfig config) throws ServletException {
        super.init(config);
        Injector injector = SERVICE_PROVIDER.getInjector().createChildInjector(new EventModule());
        eventAuthManager = injector.getInstance(EventAuthManager.class);
        eventQueueManager = injector.getInstance(EventQueueManager.class);
        eventStatusManager = injector.getInstance(EventStatusManager.class);
    }

    @Override
    protected void handleGet(HttpServletRequest req, HttpServletResponse resp, Session session)
            throws ServletException, IOException {
        switch (req.getRequestURI()) {
            case "/event":
                handleEventAuthPage(req, resp, session);
                break;
            case "/event/auth":
                handleRequest(req, resp, session, this::handleEventAuthPage);
                break;
            case "/event/queue":
                handleRequest(req, resp, session, this::handleEventQueuePage);
                break;
            case "/event/status":
                handleRequest(req, resp, session, this::handleEventStatusPage);
                break;
            case "/event/auth/log":
                handleEventAuthLogPage(req, resp, session, props);
                break;
            default:
                break;
        }
    }

    @Override
    protected void handlePost(HttpServletRequest req, HttpServletResponse resp, Session session)
            throws ServletException, IOException {
        final HashMap<String, Object> ret = new HashMap<>();
        if (hasParam(req, "ajax")) {
            if("setEventAuthBacklogAlarmUser".equals(getParam(req, "ajax"))) {
                ajaxSetEventAuthBacklogAlarmUser(req, ret);
            }
        }
        if (!Collections.isEmpty(ret)) {
            this.writeJSON(resp, ret);
        }
    }

    private void handleRequest(HttpServletRequest req, HttpServletResponse resp, Session session,
                               TriadConsumer<HttpServletRequest, HttpServletResponse, Session> consumer)
            throws ServletException, IOException {
        if (hasParam(req, "ajax")) {
            handleAJAXAction(req, resp);
        } else {
            consumer.accept(req, resp, session);
        }
    }

    private void handleAJAXAction(HttpServletRequest req, HttpServletResponse resp)
            throws ServletException, IOException {
        String ajaxName = getParam(req, "ajax");
        switch (ajaxName) {
            case "loadEventAuthData":
                ajaxLoadEventAuthData(req, resp);
                break;
            case "loadEventQueueData":
                ajaxLoadEventQueueData(req, resp);
                break;
            case "loadEventStatusData":
                ajaxLoadEventStatusData(req, resp);
                break;
            case "loadEventAuthList":
                ajaxLoadEventAuthList(req, resp);
                break;
            case "loadEventAuthSearchList":
                ajaxLoadEventAuthSearchList(req, resp);
                break;
            case "queryMessageSendStatus":
                ajaxQueryMessageSendStatus(req, resp);
                break;
            default:
                break;
        }
    }

    private void ajaxLoadEventAuthSearchList(HttpServletRequest req, HttpServletResponse resp)
        throws IOException, ServletException {

        String searchKey = getParam(req, "searchKey");
        String searchTerm = getParam(req, "searchTerm", "").trim();
        int page = getIntParam(req, "page", 1);
        int size = getIntParam(req, "size", 10);

        List<EventAuth> eventList = eventAuthManager.getEventAuthSearchList(searchKey, searchTerm, page, size);
        int eventAuthSearchTotal = eventAuthManager.getEventAuthSearchTotal(searchKey, searchTerm);
        HashMap<String, Object> ret = new HashMap<>();
        ret.put("eventAuthList", eventList);
        ret.put("total", eventAuthSearchTotal);
        ret.put("page", page);
        ret.put("size", size);
        writeJSON(resp, ret);
    }

    private void ajaxSetEventAuthBacklogAlarmUser(HttpServletRequest req, final HashMap<String, Object> ret)
        throws ServletException {
        JsonObject json = HttpRequestUtils.parseRequestToJsonObject(req);
        final String topic = json.get("topic").getAsString();
        final String sender = json.get("sender").getAsString();
        final String msgName = json.get("msgName").getAsString();
        final String backlogAlarmUser = json.get("backlogAlarmUser").getAsString();
        final String alertLevel = json.get("alertLevel").getAsString();
        final String[] split = StringUtils.isNotBlank(backlogAlarmUser) ? backlogAlarmUser.split("\\s*,\\s*|\\s*;\\s*|\\s+") : new String[0];
        final List<String> emailList = Lists.newArrayList(split);
        if (this.checkRealNameSwitch && WebUtils
                .checkEmailNotRealName(emailList, true, systemManager.findAllWebankUserList(null))) {
            ret.put("error", "Please configure the correct real-name user");
            return;
        }

        List<EventAuth> eventAuthList = eventAuthManager.getEventAuth(topic, sender, msgName);
        if (CollectionUtils.isEmpty(eventAuthList)) {
            ret.put("error",
                    "Error loading event auth. topic=" + topic + ",sender=" + sender + ",msgName=" + msgName
                            + " doesn't exist");
            return;
        }
        EventAuth eventAuth = eventAuthList.get(0);
        if (StringUtils.isNotBlank(backlogAlarmUser)) {
            eventAuth.setBacklogAlarmUser(backlogAlarmUser);
            eventAuth.setAlertLevel(alertLevel);
        } else {
            eventAuth.setBacklogAlarmUser(null);
            eventAuth.setAlertLevel(null);
        }
        eventAuthManager.setBacklogAlarmUser(eventAuth);
        ret.put("status", "success");
    }

    private void ajaxLoadEventAuthList(HttpServletRequest req, HttpServletResponse resp)
        throws IOException {

        List<EventAuth> eventList = eventAuthManager.getEventAuthList();
        HashMap<String, Object> map = new HashMap<>();
        map.put("eventAuthList", eventList);
        writeJSON(resp, map);
    }

    private void handleEventAuthPage(HttpServletRequest req, HttpServletResponse resp, Session session) {
        Page page = newPage(req, resp, session, "azkaban/event/viewer/eventAuth-manager.vm");
        String search = getParam(req,"search", "").trim();
        String languageType = LoadJsonUtils.getLanguageType();
        Map<String, String> eventI18nMap = getAllI18nMap(languageType, "azkaban.viewer.event.eventAuth-manager.vm");
        eventI18nMap.forEach(page::add);
        page.add("search", search);
        page.add("currentlangType", languageType);
        page.render();
    }

    private void handleEventQueuePage(HttpServletRequest req, HttpServletResponse resp, Session session) {
        Page page = getEventPage(req, resp, session,
                "azkaban/event/viewer/eventQueue-manager.vm", "azkaban.viewer.event.eventQueue-manager.vm");
        String languageType = LoadJsonUtils.getLanguageType();

        page.add("currentlangType", languageType);
        page.render();
    }

    private void handleEventStatusPage(HttpServletRequest req, HttpServletResponse resp, Session session) {
        Page page = getEventPage(req, resp, session,
                "azkaban/event/viewer/eventStatus-manager.vm","azkaban.viewer.event.eventStatus-manager.vm" );
        String languageType = LoadJsonUtils.getLanguageType();

        page.add("currentlangType", languageType);
        page.render();
    }

    private Page getEventPage(HttpServletRequest req, HttpServletResponse resp, Session session, String pageFile, String i18nFile) {
        Page page = newPage(req, resp, session, pageFile);
        String search = getParam(req,"search", "").trim();
        String topic = getParam(req,"topic", "").trim();
        String msgName = getParam(req,"msgName", "").trim();
        String languageType = LoadJsonUtils.getLanguageType();
        Map<String, String> eventI18nMap = getAllI18nMap(languageType, i18nFile);
        eventI18nMap.forEach(page::add);
        page.add("search", StringEscapeUtils.escapeHtml(search));
        page.add("topic", StringEscapeUtils.escapeHtml(topic));
        page.add("msgName", StringEscapeUtils.escapeHtml(msgName));
        return page;
    }


    private void handleEventAuthLogPage(HttpServletRequest req, HttpServletResponse resp, Session session, Props props) {
        Page page = newPage(req, resp, session, "azkaban/event/viewer/eventAuth-detail.vm");
        String sender = getParam(req,"sender", "").trim();
        String senderLog = eventAuthManager.fetchSenderLog(sender, props);
        page.add("sender", sender);
        page.add("senderLog", senderLog);
        String languageType = LoadJsonUtils.getLanguageType();
        Map<String, String> commonDataMap = getCommonI18nMap(languageType);
        commonDataMap.forEach(page::add);
        page.add("currentlangType", languageType);
        page.render();
    }

    private void ajaxLoadEventAuthData(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        String search = getParam(req,"search", "").trim();
        int pageNum = getIntParam(req, "pageNum", 1);
        int pageSize = getIntParam(req, "pageSize", 20);
        int eventTotalCount = eventAuthManager.getEventAuthTotal(search);
        List<EventAuth> eventList = eventAuthManager.findEventAuthList(search, pageNum, pageSize);
        HashMap<String, Object> map = new HashMap<>();
        map.put("total", eventTotalCount);
        map.put("pageNum",pageNum);
        map.put("pageSize", pageSize);
        map.put("eventAuthList", eventList);
        writeJSON(resp, map);
    }

    private void ajaxLoadEventQueueData(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        String search = getParam(req,"search", "").trim();
        int pageNum = getIntParam(req, "pageNum", 1);
        int pageSize = getIntParam(req, "pageSize", 20);
        String topic = getParam(req,"topic", "").trim();
        String msgName = getParam(req,"msgName", "").trim();
        int showPageNum = getIntParam(req, "showPageNum", 0);
        List<EventQueue> eventList = eventQueueManager.findEventQueueList(search, pageNum, pageSize, topic, msgName);
        int eventTotalCount;
        if (showPageNum > 0) {
            int index = (pageNum - 1) * pageSize;
            int sum = pageSize * showPageNum;
            eventTotalCount = eventQueueManager.getEventQueueTotal4Page(search, index, sum, topic, msgName) + index;
        } else {
            eventTotalCount = eventQueueManager.getEventQueueTotal(search, topic, msgName);
        }
        HashMap<String, Object> map = new HashMap<>();
        map.put("total", eventTotalCount);
        map.put("pageNum",pageNum);
        map.put("pageSize", pageSize);
        map.put("eventQueueList", eventList);
        writeJSON(resp, map);
    }

    private void ajaxLoadEventStatusData(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        String search = getParam(req,"search", "").trim();
        int pageNum = getIntParam(req, "pageNum", 1);
        int pageSize = getIntParam(req, "pageSize", 20);
        String topic = getParam(req,"topic", "").trim();
        String msgName = getParam(req,"msgName", "").trim();
        int eventTotalCount = eventStatusManager.getEventStatusTotal(search, topic, msgName);
        List<EventStatus> eventList = eventStatusManager.findEventStatusList(search, pageNum, pageSize, topic, msgName);
        HashMap<String, Object> map = new HashMap<>();
        map.put("total", eventTotalCount);
        map.put("pageNum",pageNum);
        map.put("pageSize", pageSize);
        map.put("eventStatusList", eventList);
        writeJSON(resp, map);
    }

    private Map<String, String> getCommonI18nMap(String languageType) {
        if ("zh_CN".equalsIgnoreCase(languageType)) {
            Map<String, String> commonDataMap = LoadJsonUtils.transJson("/conf/az-webank-eventauth-manager-zh_CN.json",
                    "azkaban.viewer.event.common");
            Map<String, String> navPageMap = LoadJsonUtils.transJson("/conf/azkaban-web-server-zh_CN.json",
                    "azkaban.webapp.servlet.velocity.nav.vm");
            commonDataMap.putAll(navPageMap);
            return commonDataMap;
        } else {
            Map<String, String> commonDataMap = LoadJsonUtils.transJson("/conf/az-webank-eventauth-manager-en_US.json",
                    "azkaban.viewer.event.common");
            Map<String, String> navPageMap = LoadJsonUtils.transJson("/conf/azkaban-web-server-en_US.json",
                    "azkaban.webapp.servlet.velocity.nav.vm");
            commonDataMap.putAll(navPageMap);
            return commonDataMap;
        }
    }

    private Map<String, String> getEventI18nMap(String languageType, String dataNode) {
        if ("zh_CN".equalsIgnoreCase(languageType)) {
            return LoadJsonUtils.transJson("/conf/az-webank-eventauth-manager-zh_CN.json", dataNode);
        }else {
            return LoadJsonUtils.transJson("/conf/az-webank-eventauth-manager-en_US.json", dataNode);
        }
    }

    private Map<String, String> getAllI18nMap(String languageType, String dataNode) {
        Map<String, String> commonI18nMap = getCommonI18nMap(languageType);
        Map<String, String> eventI18nMap = getEventI18nMap(languageType, dataNode);
        commonI18nMap.putAll(eventI18nMap);
        return commonI18nMap;
    }

    private void ajaxQueryMessageSendStatus(HttpServletRequest req, HttpServletResponse resp) throws IOException, ServletException {
        HashMap<String, Object> map = new HashMap<>();
        String sender = getParam(req,"sender");
        String topic = getParam(req,"topic");
        String msgName = getParam(req,"msgName");
        String msgBody = getParam(req,"msgBody");
        if (StringUtils.isBlank(sender) && StringUtils.isBlank(topic) && StringUtils.isBlank(msgName) && StringUtils.isBlank(msgBody)) {
            map.put("status", "error");
            map.put("errorMsg", "query param is empty.");
            writeJSON(resp, map);
            return;
        }
        int eventQueueNum = eventQueueManager.queryMessageNum(sender, topic, msgName, msgBody);
        if (eventQueueNum == -1) {
            map.put("status", "error");
            map.put("errorMsg", "Error in query database! Please contact the administrator");
            writeJSON(resp, map);
            return;
        }
        map.put("status", "success");
        map.put("senderStatus", eventQueueNum > 0 ? "S" : "W");
        writeJSON(resp, map);
    }

}
