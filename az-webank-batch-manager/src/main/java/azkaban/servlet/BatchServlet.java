package azkaban.servlet;

import azkaban.ServiceProvider;
import azkaban.batch.HoldBatchContext;
import azkaban.batch.HoldBatchOperate;
import azkaban.executor.DepartmentGroup;
import azkaban.executor.ExecutorManagerAdapter;
import azkaban.i18n.utils.LoadJsonUtils;
import azkaban.module.BatchModule;
import azkaban.project.ProjectManager;
import azkaban.scheduler.Schedule;
import azkaban.scheduler.ScheduleManager;
import azkaban.scheduler.ScheduleManagerException;
import azkaban.server.session.Session;
import azkaban.service.BatchManager;
import azkaban.service.impl.BatchManagerImpl;
import azkaban.system.SystemManager;
import azkaban.system.entity.WebankDepartment;
import azkaban.system.entity.WtssUser;
import azkaban.trigger.TriggerManagerException;
import azkaban.user.User;
import azkaban.utils.HttpUtils;
import azkaban.utils.Props;
import azkaban.webapp.AzkabanWebServer;
import azkaban.webapp.servlet.AbstractLoginAzkabanServlet;
import azkaban.webapp.servlet.Page;
import com.google.inject.Injector;
import org.apache.commons.lang.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Created by zhu on 7/5/18.
 */
public class BatchServlet extends AbstractLoginAzkabanServlet {

  private static final Logger logger = LoggerFactory.getLogger(BatchServlet.class.getName());

  private static final long serialVersionUID = 1L;
  private BatchManager batchManager;
  private final Props propsPlugin;
  private Props propsAzkaban;
  private final File webResourcesPath;
  private SystemManager systemManager;
  private ExecutorManagerAdapter executorManagerAdapter;
  private ProjectManager projectManager;
  private ScheduleManager scheduleManager;
  private HoldBatchContext holdBatchContext;


  private List<String> holdBatchWhiteList;

  public BatchServlet(final Props propsPlugin) {

    this.propsPlugin = propsPlugin;

    this.webResourcesPath = new File(
        new File(propsPlugin.getSource()).getParentFile().getParentFile(), "web");
    this.webResourcesPath.mkdirs();

    setResourceDirectory(this.webResourcesPath);

  }

  @Override
  public void init(final ServletConfig config) throws ServletException {
    super.init(config);

    AzkabanWebServer server = (AzkabanWebServer) getApplication();
    Injector injector = ServiceProvider.SERVICE_PROVIDER.getInjector()
        .createChildInjector(new BatchModule());
    batchManager = injector.getInstance(BatchManagerImpl.class);
    propsAzkaban = ServiceProvider.SERVICE_PROVIDER.getInstance(Props.class);
    holdBatchWhiteList = propsAzkaban.getStringList("hold.batch.whitelist");
    systemManager = server.getTransitionService().getSystemManager();
    executorManagerAdapter = server.getExecutorManager();
    projectManager = server.getProjectManager();
    scheduleManager = server.getScheduleManager();
    holdBatchContext = ServiceProvider.SERVICE_PROVIDER.getInstance(HoldBatchContext.class);
  }

  @Override
  protected void handleGet(final HttpServletRequest req, final HttpServletResponse resp,
      final Session session) throws ServletException, IOException {

    if (hasParam(req, "ajax")) {
      handleAJAXAction(req, resp, session);
    } else {
      handleBatchPage(req, resp, session);
    }
  }

  @Override
  protected void handlePost(HttpServletRequest req, HttpServletResponse resp, Session session)
      throws ServletException, IOException, TriggerManagerException, ScheduleManagerException {
    if (hasParam(req, "ajax")) {
      handleAJAXAction(req, resp, session);
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
      ret.put("error", "No Access Permission");
    } else {
      if ("ajaxQueryHoldBatchList".equals(ajaxName)) {
        ajaxQueryHoldBatchList(req, resp, session, ret);
      } else if ("ajaxResumeFlow".equals(ajaxName)) {
        ajaxResumeFlow(req, resp, ret);
      } else if ("ajaxStopFlow".equals(ajaxName)) {
        ajaxStopFlow(req, resp, ret);
      } else if ("ajaxQueryDevDeptList".equals(ajaxName)) {
        ajaxQueryDevDeptList(req, resp, ret);
      } else if ("ajaxHoldBatch".equals(ajaxName)) {
        ajaxHoldBatch(req, resp, ret, user);
      } else if ("ajaxQueryProjectFlowList".equals(ajaxName)) {
        ajaxQueryProjectFlowList(req, resp, ret);
      } else if ("ajaxGetHoldDataListByLevel".equals(ajaxName)) {
        ajaxGetHoldDataListByLevel(req, resp, ret);
      } else if ("ajaxGetHoldDataListByBatchId".equals(ajaxName)) {
        ajaxGetHoldDataListByBatchId(req, resp, ret);
      } else if ("ajaxGetBatchIdList".equals(ajaxName)) {
        ajaxGetBatchIdList(req, resp, ret);
      } else if ("ajaxQueryBatchFlowList".equals(ajaxName)) {
        ajaxQueryBatchFlowList(req, resp, ret);
      }
    }

    if (ret != null) {
      this.writeJSON(resp, ret);
    }
  }

  private void ajaxGetBatchIdList(HttpServletRequest req, HttpServletResponse resp,
      HashMap<String, Object> ret) {
    try {
      String holdLevel = getParam(req, "holdLevel", "");
      if (StringUtils.isEmpty(holdLevel)) {
        ret.put("error", "level empty error");
        return;
      }
      ret.put("dataList", this.batchManager.getBatchIdListByLevel(holdLevel));
    } catch (Exception e) {
      logger.error("get batchId list error", e);
      ret.put("error", "get batchId list error");
    }
  }

  private void ajaxGetHoldDataListByLevel(HttpServletRequest req, HttpServletResponse resp,
      HashMap<String, Object> ret) {
    try {
      String holdLevel = getParam(req, "holdLevel", "");
      String search = getParam(req, "search", "");
      int page = getIntParam(req, "page", 1);
      int pageSize = getIntParam(req, "pageSize", 20);
      if (StringUtils.isEmpty(holdLevel)) {
        ret.put("error", "level empty error");
        return;
      }
      List<Map<String, Object>> dataList = new ArrayList<>();
      long total = 0;
      long skip = (long) (page - 1) * pageSize;
      switch (holdLevel) {
        case "1":
          List<DepartmentGroup> groupList = this.systemManager.fetchAllDepartmentGroup();
          if (StringUtils.isNotEmpty(search)) {
            groupList = groupList.stream().filter(g -> g.getName().contains(search))
                .collect(Collectors.toList());
          }
          total = groupList.size();
          for (DepartmentGroup group : groupList.stream()
              .sorted(Comparator.comparing(DepartmentGroup::getId)).skip(skip)
              .limit(pageSize).collect(Collectors.toList())) {
            Map<String, Object> map = new HashMap<>();
            map.put("id", group.getId());
            map.put("text", group.getName());
            dataList.add(map);
          }
          break;
        case "2":
          List<WtssUser> userList = this.systemManager
              .findSystemUserPage("", search, "", (page - 1) * pageSize, pageSize);
          total = this.systemManager.getSystemUserTotal(search);
          for (WtssUser user : userList) {
            Map<String, Object> map = new HashMap<>();
            map.put("id", user.getUsername());
            map.put("text", user.getUsername());
            dataList.add(map);
          }
          break;
        case "3":
          List<Schedule> scheduleList = this.scheduleManager.getSchedules();
          List<String> flowList;
          if (StringUtils.isNotEmpty(search)) {
            flowList = scheduleList.stream().filter(
                s -> s.getProjectName().contains(search) || s.getFlowName()
                    .contains(search)).map(s -> s.getProjectName() + "-" + s.getFlowName())
                .collect(Collectors.toList());
          } else {
            flowList = scheduleList.stream().map(s -> s.getProjectName() + "-" + s.getFlowName())
                .collect(Collectors.toList());
          }
          total = flowList.size();
          for (String str : flowList.stream().sorted().skip(skip).limit(pageSize)
              .collect(Collectors.toList())) {
            Map<String, Object> map = new HashMap<>();
            map.put("id", str);
            map.put("text", str);
            dataList.add(map);
          }
          break;
        default:
      }
      ret.put("dataList", dataList);
      ret.put("totalCount", total);
    } catch (Exception e) {
      logger.error("get data list error", e);
      ret.put("error", "get data list error");
    }
  }

  private void ajaxGetHoldDataListByBatchId(HttpServletRequest req, HttpServletResponse resp,
      HashMap<String, Object> ret) {
    try {
      String batchId = getParam(req, "batchId", "");
      String search = getParam(req, "search", "");
      int page = getIntParam(req, "page", 1);
      int pageSize = getIntParam(req, "pageSize", 20);
      if (StringUtils.isEmpty(batchId)) {
        ret.put("error", "batchId empty error");
        return;
      }
      List<Map<String, Object>> dataList = new ArrayList<>();
      long total = 0;
      long skip = (long) (page - 1) * pageSize;

      HoldBatchOperate holdBatchOperate = this.holdBatchContext.getBatchMap().get(batchId);
      if (holdBatchOperate != null) {
        List<String> list = holdBatchOperate.getDataList();
        if (holdBatchOperate.getOperateLevel() == 1) {
          List<DepartmentGroup> groupList = this.systemManager.fetchAllDepartmentGroup();
          List<String> finalList = list;
          groupList = groupList.stream().filter(g -> finalList.contains(g.getId() + ""))
              .collect(Collectors.toList());
          if (StringUtils.isNotEmpty(search)) {
            groupList = groupList.stream().filter(g -> g.getName().contains(search))
                .collect(
                    Collectors.toList());
          }
          total = groupList.size();
          for (DepartmentGroup group : groupList.stream()
              .sorted(Comparator.comparing(DepartmentGroup::getId)).skip(skip)
              .limit(pageSize).collect(Collectors.toList())) {
            Map<String, Object> map = new HashMap<>();
            map.put("id", group.getId());
            map.put("text", group.getName());
            dataList.add(map);
          }
        } else {
          if (StringUtils.isNotEmpty(search)) {
            list = list.stream().filter(s -> s.contains(search))
                .collect(Collectors.toList());
          }
          total = list.size();
          for (String str : list.stream().sorted().skip(skip).limit(pageSize)
              .collect(Collectors.toList())) {
            Map<String, Object> map = new HashMap<>();
            map.put("id", str);
            map.put("text", str);
            dataList.add(map);
          }
        }
      }

      ret.put("dataList", dataList);
      ret.put("totalCount", total);
    } catch (Exception e) {
      logger.error("get batchId list error", e);
      ret.put("error", "get batchId list error");
    }
  }

  private void ajaxQueryBatchFlowList(HttpServletRequest req, HttpServletResponse resp,
      HashMap<String, Object> ret) {

    try {
      String search = getParam(req, "search", "");
      int page = getIntParam(req, "page", 1);
      int pageSize = getIntParam(req, "pageSize", 20);
      List<String> flowList = this.batchManager
          .queryBatchFlowList(search, (page - 1) * pageSize, pageSize);
      long total = this.batchManager.getHoldBatchFlowTotal(search);
      List<Map<String, Object>> mapList = new ArrayList<>();
      for (String str : flowList) {
        Map<String, Object> map = new HashMap<>(4);
        map.put("id", str);
        map.put("text", str);
        mapList.add(map);
      }

      ret.put("flowList", mapList);
      ret.put("totalCount", total);
    } catch (Exception e) {
      logger.error("get project-flow list error", e);
      ret.put("error", "get project-flow list error");
    }

  }

  private void ajaxQueryProjectFlowList(HttpServletRequest req, HttpServletResponse resp,
      HashMap<String, Object> ret) {

    try {
      String search = getParam(req, "search", "");
      long page = getLongParam(req, "page", 1);
      long pageSize = getLongParam(req, "pageSize", 20);
      List<Schedule> scheduleList = this.scheduleManager.getSchedules();
      List<String> flowList;
      if (StringUtils.isNotEmpty(search)) {
        flowList = scheduleList.stream()
            .filter(s -> s.getProjectName().contains(search) || s.getFlowName().contains(search))
            .map(s -> s.getProjectName() + "-" + s.getFlowName()).collect(Collectors.toList());
      } else {
        flowList = scheduleList.stream().map(s -> s.getProjectName() + "-" + s.getFlowName())
            .collect(Collectors.toList());
      }
      List<Map<String, Object>> mapList = new ArrayList<>();
      for (String str : flowList.stream().sorted().skip((page - 1) * pageSize).limit(pageSize)
          .collect(Collectors.toList())) {
        Map<String, Object> map = new HashMap<>();
        map.put("id", str);
        map.put("text", str);
        mapList.add(map);
      }

      ret.put("flowList", mapList);
      ret.put("totalCount", flowList.size());
    } catch (Exception e) {
      logger.error("get project-flow list error", e);
      ret.put("error", "get project-flow list error");
    }

  }

  private void ajaxHoldBatch(HttpServletRequest req, HttpServletResponse resp,
      HashMap<String, Object> ret, User user) {
    try {
      final int holdType = getIntParam(req, "holdType");
      final int holdLevel = getIntParam(req, "holdLevel");
      String dataListStr = getParam(req, "dataList", "");
      String flowListStr = getParam(req, "flowList", "");
      String busPathListStr = getParam(req, "busPathList", "");
      List<String> criticalPathList = new ArrayList<>();
      boolean excludeAllCriticalPath = getBooleanParam(req, "excludeAllCriticalPath", false);
      if (excludeAllCriticalPath) {
        Props prop = getApplication().getServerProps();
        HashMap<String, Object> map = new HashMap<>();
        HttpUtils.getCmdbData(prop, "wtss.cmdb.operateCi", "wb_batch_group", "group_id", "", "group_name", "", 0, 10, map, false);
        List<Map<String, Object>> dataList = (List<Map<String, Object>>) map.get("dataList");
        criticalPathList = dataList.stream().map(o ->(String) o.get("group_id")).collect(Collectors.toList());
      }
      String[] arr = {};
      final List<String> dataList = Arrays
          .asList(StringUtils.isEmpty(dataListStr) ? arr : dataListStr.split(";"));
      final List<String> flowList = Arrays
          .asList(StringUtils.isEmpty(flowListStr) ? arr : flowListStr.split(";"));
      final List<String> busPathList = Arrays
          .asList(StringUtils.isEmpty(busPathListStr) ? arr : busPathListStr.split(";"));
      final String batchId = getParam(req, "batchId", "");
      ret.put("error", executorManagerAdapter
              .holdBatch(holdType, holdLevel, dataList, flowList, busPathList, batchId, user, criticalPathList));
    } catch (Exception e) {
      logger.error("hold batch error", e);
      ret.put("error", "hold batch error");
    }

  }

  private void ajaxQueryDevDeptList(HttpServletRequest req, HttpServletResponse resp,
      HashMap<String, Object> ret) {
    try {
      int page = getIntParam(req, "page", 1);
      int pageSize = getIntParam(req, "pageSize", 20);
      String search = getParam(req, "search", "");
      List<WebankDepartment> webankDepartmentList = this.systemManager
          .findAllWebankDepartmentPageList(search, (page - 1) * pageSize, pageSize);
      List<Map<String, Object>> mapList = new ArrayList<>();
      for (WebankDepartment dept : webankDepartmentList) {
        Map<String, Object> map = new HashMap<>();
        map.put("id", dept.getDpId());
        map.put("text", dept.getDpChName());
        mapList.add(map);
      }
      int total = this.systemManager.getWebankDepartmentTotal(search);
      ret.put("devDeptList", mapList);
      ret.put("totalCount", total);
    } catch (Exception e) {
      logger.error("query dev dept error", e);
      ret.put("error", "query dev dept error");
    }

  }

  private void ajaxStopFlow(HttpServletRequest req, HttpServletResponse resp,
      HashMap<String, Object> ret) {
    try {
      long id = getLongParam(req, "alertId", 0);
      this.executorManagerAdapter.stopBatchFlow(id);
    } catch (Exception e) {
      logger.error("stop batch flow error", e);
      ret.put("error", "stop batch flow error");
    }
  }

  private void ajaxResumeFlow(HttpServletRequest req, HttpServletResponse resp,
      HashMap<String, Object> ret) {
    try {
      long id = getLongParam(req, "alertId", 0);
      this.executorManagerAdapter.resumeBatchFlow(id);
    } catch (Exception e) {
      logger.error("resume batch flow error", e);
      ret.put("error", "resume batch flow error");
    }
  }

  private void ajaxQueryHoldBatchList(HttpServletRequest req, HttpServletResponse resp,
      Session session, HashMap<String, Object> ret) {
    try {
      boolean isAdvQuery = Boolean.valueOf(getParam(req, "isAdvQuery", "false"));
      int start = getIntParam(req, "start", 0);
      int pageSize = getIntParam(req, "pageSize", 20);
      List<Map<String, Object>> resultList;
      long total;
      if (isAdvQuery) {
        String projectName = getParam(req, "projectName", "");
        String flowName = getParam(req, "flowName", "");
        String busPath = getParam(req, "busPath", "");
        String batchId = getParam(req, "batchId", "");
        String subSystem = getParam(req, "subSystem", "");
        String devDept = getParam(req, "devDept", "");
        String submitUser = getParam(req, "submitUser", "");
        String execId = getParam(req, "execId", "");
        resultList = this.batchManager
            .queryHoldBatchList(projectName, flowName, busPath, batchId, subSystem, devDept,
                submitUser, execId, start, pageSize);
        total = this.batchManager
            .getHoldBatchTotal(projectName, flowName, busPath, batchId, subSystem, devDept,
                submitUser, execId);
      } else {
        String searchterm = getParam(req, "searchterm", "");
        resultList = this.batchManager.queryHoldBatchList(searchterm, start, pageSize);
        total = this.batchManager.getHoldBatchTotal(searchterm, start, pageSize);
      }
      ret.put("batchPageList", resultList);
      ret.put("total", total);
    } catch (Exception e) {
      logger.error("query batch list error", e);
      ret.put("error", "query batch list error");
    }


  }

  /**
   * 批量管理页面
   *
   * @param req
   * @param resp
   * @param session
   * @throws ServletException
   */
  private void handleBatchPage(final HttpServletRequest req, final HttpServletResponse resp,
      final Session session)
      throws ServletException {
    final Page page =
        newPage(req, resp, session, "azkaban/viewer/batch/batch-manager.vm");

    final User user = session.getUser();

    page.add("adminPerm", user.getRoles().contains("admin"));

    String languageType = LoadJsonUtils.getLanguageType();
    Map<String, String> viewDataMap;
    Map<String, String> subPageMap1;
    Map<String, String> subPageMap2;
    if ("zh_CN".equalsIgnoreCase(languageType)) {
      viewDataMap = LoadJsonUtils.transJson("/conf/az-webank-batch-manager-zh_CN.json",
          "azkaban.viewer.batch.batch-manager.vm");
      subPageMap1 = LoadJsonUtils.transJson("/conf/azkaban-web-server-zh_CN.json",
          "azkaban.webapp.servlet.velocity.nav.vm");
      subPageMap2 = LoadJsonUtils.transJson("/conf/azkaban-web-server-zh_CN.json",
          "azkaban.webapp.servlet.velocity.messagedialog.vm");
    } else {
      viewDataMap = LoadJsonUtils.transJson("/conf/az-webank-batch-manager-en_US.json",
          "azkaban.viewer.batch.batch-manager.vm");
      subPageMap1 = LoadJsonUtils.transJson("/conf/azkaban-web-server-en_US.json",
          "azkaban.webapp.servlet.velocity.nav.vm");
      subPageMap2 = LoadJsonUtils.transJson("/conf/azkaban-web-server-en_US.json",
          "azkaban.webapp.servlet.velocity.messagedialog.vm");
    }
    viewDataMap.forEach(page::add);
    subPageMap1.forEach(page::add);
    subPageMap2.forEach(page::add);

    page.add("currentlangType", languageType);
    page.render();

  }

  /**
   * 加载 BatchServlet 中的异常信息等国际化资源
   *
   * @return
   */
  private Map<String, String> loadBatchServletI18nData() {
    String languageType = LoadJsonUtils.getLanguageType();
    Map<String, String> dataMap;
    if ("zh_CN".equalsIgnoreCase(languageType)) {
      dataMap = LoadJsonUtils.transJson("/conf/az-webank-batch-manager-zh_CN.json",
          "azkaban.servlet.BatchServlet");
    } else {
      dataMap = LoadJsonUtils.transJson("/conf/az-webank-batch-manager-en_US.json",
          "azkaban.servlet.BatchServlet");
    }
    return dataMap;
  }

}
