package azkaban.viewer.homepage;

import azkaban.common.utils.TimeUtils;
import azkaban.executor.*;
import azkaban.flow.Flow;
import azkaban.flow.Node;
import azkaban.i18n.utils.LoadJsonUtils;
import azkaban.project.Project;
import azkaban.project.ProjectManager;
import azkaban.scheduler.Schedule;
import azkaban.scheduler.ScheduleManager;
import azkaban.server.session.Session;
import azkaban.user.User;
import azkaban.utils.Props;
import azkaban.utils.Utils;
import azkaban.utils.WebUtils;
import azkaban.webapp.AzkabanWebServer;
import azkaban.webapp.servlet.AbstractLoginAzkabanServlet;
import azkaban.webapp.servlet.Page;
import azkaban.webapp.servlet.RecoverServlet;
import org.apache.commons.lang.StringUtils;
import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.File;
import java.io.IOException;
import java.util.*;

/**
 * Created by zhu on 9/11/18.
 */
public class HomePageServlet extends AbstractLoginAzkabanServlet {

  private static final Logger logger = LoggerFactory.getLogger(HomePageServlet.class.getName());

  private static final long serialVersionUID = 1L;
  private ExecutorManagerAdapter executorManager;
  private ProjectManager projectManager;
  private RecoverServlet.ExecutorVMHelper vmHelper;
  private ScheduleManager scheduleManager;
  private final Props props;
  private final File webResourcesPath;


  private final String viewerName;
  private final String viewerPath;

  public HomePageServlet(final Props props){

    this.props = props;
    this.viewerName = props.getString("viewer.name");
    this.viewerPath = props.getString("viewer.path");

    this.webResourcesPath = new File
        (new File(props.getSource()).getParentFile().getParentFile(),"web");
    this.webResourcesPath.mkdirs();

    setResourceDirectory(this.webResourcesPath);

  }

  @Override
  public void init(final ServletConfig config) throws ServletException {
    super.init(config);
    final AzkabanWebServer server = (AzkabanWebServer) getApplication();
    this.executorManager = server.getExecutorManager();
    this.projectManager = server.getProjectManager();
    this.scheduleManager = server.getScheduleManager();
  }

  @Override
  protected void handleGet(final HttpServletRequest req, final HttpServletResponse resp,
      final Session session) throws ServletException, IOException {

    if (hasParam(req, "ajax")) {
      handleAJAXAction(req, resp, session);
    } else {
      handleHomePage(req, resp, session);
    }
  }

  private void handleAJAXAction(final HttpServletRequest req,
      final HttpServletResponse resp, final Session session) throws ServletException,
      IOException {
    final HashMap<String, Object> ret = new HashMap<>();
    final String ajaxName = getParam(req, "ajax");

    if ("fetch".equals(ajaxName)) {

    } else if ("getTodayFlowExecuteStatus".equals(ajaxName)) {
      ajaxGetTodayFlowExecuteStatus(req, resp, session, ret);
    } else if ("getRealTimeFlowInfoData".equals(ajaxName)) {
      ajaxGetRealTimeFlowInfoData(req, resp, session, ret);
    } else if ("getTodayAllFlowInfo".equals(ajaxName)) {
      ajaxGetTodayAllFlowInfo(req, resp, session, ret);
    } else if ("getHomePageLanguageType".equals(ajaxName)) {
      ajaxGetHomePageLanguageType(req, resp, session, ret);
    }

    if (ret != null) {
      this.writeJSON(resp, ret);
    }
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

  /**
   * 数据展示首页页面
   * @param req
   * @param resp
   * @param session
   * @throws ServletException
   */
  private void handleHomePage(final HttpServletRequest req, final HttpServletResponse resp, final Session session)
      throws ServletException {
    final Page page = newPage(req, resp, session, "azkaban/viewer/home-page.vm");

    String languageType = LoadJsonUtils.getLanguageType();
    Map<String, String> singleDataMap;
    Map<String, String> subPageMap1;
    if ("zh_CN".equalsIgnoreCase(languageType)) {// azkaban.webapp.servlet.velocity.nav.vm
      singleDataMap = LoadJsonUtils.transJson("/conf/az-webank-homepage-zh_CN.json",
              "azkaban.viewer.home-page.vm");
        subPageMap1 = LoadJsonUtils.transJson("/conf/azkaban-web-server-zh_CN.json",
                "azkaban.webapp.servlet.velocity.nav.vm");
    }else {
      singleDataMap = LoadJsonUtils.transJson("/conf/az-webank-homepage-en_US.json",
              "azkaban.viewer.home-page.vm");
        subPageMap1 = LoadJsonUtils.transJson("/conf/azkaban-web-server-en_US.json",
                "azkaban.webapp.servlet.velocity.nav.vm");
    }
    singleDataMap.forEach(page::add);
    subPageMap1.forEach(page::add);
    page.add("currentlangType", languageType);

    page.render();

  }

  //获取当天工作流执行状态
  private void ajaxGetTodayFlowExecuteStatus(final HttpServletRequest req, final HttpServletResponse resp,
                                             final Session session, final HashMap<String, Object> ret) {

    User user = session.getUser();

    List<Map<String, String>> todayFlowExecuteData = new ArrayList<>();
    Map<String, String> successMap = new HashMap<>();
    Map<String, String> runningMap = new HashMap<>();
    Map<String, String> failedMap = new HashMap<>();
    Map<String, String> killMap = new HashMap<>();
    Map<String, String> queueMap = new HashMap<>();
    int successNum = 0;
    int runningNum = 0;
    int failedNum = 0;
    int killNum = 0;
    int queueNum = 0;
    int finishTotal = 0;
    int jobTotal = 0;
    String moyneTime = "";
    Map<String, Integer> exFlowMap = new HashMap<>();
    try {
      List<ExecutableFlow> execFlowList;
      List<Schedule> schedules = new ArrayList<>();
      if(user.getRoles().contains("admin")){
        execFlowList = this.executorManager.getTodayExecutableFlowData(null);
        //newFlowNoRunNum = this.projectManager.getTodayCreateProjectNoRunNum(null);
        schedules = this.scheduleManager.getSchedules();

      }else {
        execFlowList = this.executorManager.getTodayExecutableFlowData(user.getUserId());
        //newFlowNoRunNum = this.projectManager.getTodayCreateProjectNoRunNum(user.getUserId());
        //schedules = this.scheduleManager.getSchedulesByUser(session.getUser());

        List<Project> userProjectList = this.projectManager.getUserAllProjects(session.getUser(), null, true);
        //schedules = this.scheduleManager.getSchedulesByUser(session.getUser());

        for(Schedule schedule : this.scheduleManager.getSchedules()){
          for(Project project : userProjectList){
            if(project.getId() == schedule.getProjectId()){
              schedules.add(schedule);
            }
          }
        }

      }
      //queueNum += newFlowNoRunNum.size();
      //flowTotal += newFlowNoRunNum.size();
      //flowTotal = execFlowList.size();
      long duartion = 0;
      for(ExecutableFlow exFlow : execFlowList){
        if(Status.SUCCEEDED.equals(exFlow.getStatus())){
          successNum += 1;
          finishTotal += 1;
        }
        if(Status.RUNNING.equals(exFlow.getStatus())){
          runningNum += 1;
        }
        if(Status.FAILED.equals(exFlow.getStatus())){
          failedNum += 1;
          finishTotal += 1;
        }
        if(Status.KILLED.equals(exFlow.getStatus())){
          killNum += 1;
          finishTotal += 1;
        }
        if(Status.QUEUED.equals(exFlow.getStatus())){
          queueNum += 1;
        }
        if(Status.PREPARING.equals(exFlow.getStatus())){
          queueNum += 1;
        }
        if(-1 != exFlow.getEndTime() && -1 != exFlow.getStartTime() && !Status.RUNNING.equals(exFlow.getStatus())) {
          duartion += exFlow.getEndTime() - exFlow.getStartTime();
        }
        //获取工作流里面的任务数量
        //flowTotal += exFlow.getExecutableNodes().size();

        exFlowMap.put(exFlow.getProjectId() + exFlow.getFlowId(), exFlow.getExecutableNodes().size());

      }

      for(Schedule schedule : schedules) {
        if (!(boolean) schedule.getOtherOption().getOrDefault("activeFlag", false)) {
          logger.debug("not active schedule, id:{}", schedule.getScheduleId());
          continue;
        }
        queueNum += getScheduleTodayRunCount(schedule);
        if (0 != queueNum && null == exFlowMap
            .get(schedule.getProjectId() + schedule.getFlowName())) {
          Project project = this.projectManager.getProject(schedule.getProjectId());
          if (project == null) {
            logger.warn("project: {} is not exist.", schedule.getProjectId());
            continue;
          }
          Flow flow = project.getFlow(schedule.getFlowName());
          if (flow == null) {
            logger.warn("flow: {} is not exist in project:{}.", schedule.getFlowName(),
                project.getName());
            continue;
          }
          jobTotal += flow.getNodes().size();
        }

      }

      if(finishTotal != 0){
        duartion = duartion/finishTotal;
      }

      moyneTime = TimeUtils.getTimeStrBySecond(duartion);

    } catch (Exception e) {
      logger.error("Get Today Flow Execute Status Error, caused by:", e);
    }

    String languageType = LoadJsonUtils.getLanguageType();
    Map<String, String> dataMap;
    if ("zh_CN".equalsIgnoreCase(languageType)) {
      dataMap = LoadJsonUtils.transJson("/conf/az-webank-homepage-zh_CN.json",
              "azkaban.viewer.homepage.HomePageServlet");
    }else {
      dataMap = LoadJsonUtils.transJson("/conf/az-webank-homepage-en_US.json",
              "azkaban.viewer.homepage.HomePageServlet");
    }

    successMap.put("name", dataMap.get("successFlow"));
    successMap.put("value", successNum + "");

    runningMap.put("name", dataMap.get("runningFlow"));
    runningMap.put("value", runningNum + "");

    failedMap.put("name", dataMap.get("failedFlow"));
    failedMap.put("value", failedNum + "");

    killMap.put("name", dataMap.get("killedFlow"));
    killMap.put("value", killNum + "");

    queueMap.put("name", dataMap.get("preparingFlow"));
    queueMap.put("value", queueNum + "");

    todayFlowExecuteData.add(successMap);
    todayFlowExecuteData.add(runningMap);
    todayFlowExecuteData.add(failedMap);
    todayFlowExecuteData.add(killMap);
    todayFlowExecuteData.add(queueMap);

    Map<String, String> otherFlowExecDataMap = new HashMap<>();
    otherFlowExecDataMap.put("moyenTime", moyneTime);
    for(String exFlowId : exFlowMap.keySet()){
      jobTotal += exFlowMap.get(exFlowId);
    }
    otherFlowExecDataMap.put("jobTotal", jobTotal+"");

    ret.put("todayFlowExecuteData", todayFlowExecuteData);
    ret.put("otherFlowExecData", otherFlowExecDataMap);
    ret.put("langType", languageType);
  }

  /**
   * 获取当前语言
   * @param req
   * @param resp
   * @param session
   * @param ret
   */
  private void ajaxGetHomePageLanguageType(final HttpServletRequest req, final HttpServletResponse resp,
                                       final Session session, final HashMap<String, Object> ret) {
    
    try {
      String languageType = LoadJsonUtils.getLanguageType();
      ret.put("langType", languageType);
    } catch (Exception e) {
      ret.put("error", e.getMessage());
      logger.error("languageType load error: caused by:", e);
    }
  }
  

  //获取工作流实时信息
  private void ajaxGetRealTimeFlowInfoData(final HttpServletRequest req, final HttpServletResponse resp,
                                           final Session session, final HashMap<String, Object> ret) {

    User user = session.getUser();

    List<Map<String, String>> realTimeFlowExecuteData = new ArrayList<>();

    String languageType = LoadJsonUtils.getLanguageType();
    try {

      //  KILLED(60), FAILED(70), FAILED_FINISHING(80)
      List<ExecutableFlow> errorExecFlowList = new ArrayList<>();
      if(user.getRoles().contains("admin")) {
        errorExecFlowList = this.executorManager.getRealTimeExecFlowData(null);
      }else {
        errorExecFlowList = this.executorManager.getRealTimeExecFlowData(user.getUserId());
      }

      for(ExecutableFlow execFlow : errorExecFlowList){
        Status status = execFlow.getStatus();
        Map<String, String> realTimeData = new HashMap<>();
        realTimeData.put("endTime", TimeUtils.formatEndDateTime(execFlow.getEndTime()));
        realTimeData.put("flowName", execFlow.getFlowId());
        realTimeData.put("execId", execFlow.getExecutionId() + "");
        realTimeData.put("execStatus", status + "");
        realTimeFlowExecuteData.add(realTimeData);
      }
    } catch (ExecutorManagerException e) {
      logger.error("get real time flow info data failed, caused by:", e);
    }

    ret.put("realTimeData", realTimeFlowExecuteData);
    ret.put("langType", languageType);

  }


  //获取当天工作流执行状态
  private void ajaxGetTodayAllFlowInfo(final HttpServletRequest req, final HttpServletResponse resp,
                                       final Session session, final HashMap<String, Object> ret) {
    User user = session.getUser();
    int page = getIntParam(req, "page",1);
    int size = getIntParam(req, "size",10);
    if (page < 1) {
      page = 1;
    }
    if (size < 0) {
      size = 10;
    }
    //List<Map<String, String>> todayAllFlowExecInfo = new ArrayList<>();

    Map<String, Map<String,String>> todayAllFlowExecInfo = new HashMap<>();

    try {
      List<ExecutableFlow> execFlowList;
      List<Map> newFlowNoRunList;
      List<Schedule> schedules = new ArrayList<>();
      if(user.getRoles().contains("admin")){
        execFlowList = this.executorManager.getTodayExecutableFlowDataNew(null);
        //newFlowNoRunList = this.projectManager.getTodayCreateProjectNoRunFlowInfo(null);
        schedules = this.scheduleManager.getSchedules();
      }else{
        execFlowList = this.executorManager.getTodayExecutableFlowDataNew(user.getUserId());
        //newFlowNoRunList = this.projectManager.getTodayCreateProjectNoRunFlowInfo(user.getUserId());
        //schedules = this.scheduleManager.getSchedulesByUser(session.getUser());

        List<Project> userProjectList = this.projectManager.getUserAllProjects(session.getUser(), null, true);
        //schedules = this.scheduleManager.getSchedulesByUser(session.getUser());

        for(Schedule schedule : this.scheduleManager.getSchedules()){
          if(!(boolean)schedule.getOtherOption().getOrDefault("activeFlag", false)){
            logger.debug("not active schedule, id:{}", schedule.getScheduleId());
            continue;
          }
          for(Project project : userProjectList){
            if(project.getId() == schedule.getProjectId()){
              schedules.add(schedule);
            }
          }
        }
      }
      int totalFlowNum = 0;
      for(ExecutableFlow exFlow : execFlowList){
        //用与标识唯一Flow 避免不同项目工作流重名
        String mapKey = exFlow.getProjectName() + ":" + exFlow.getFlowId();
        Map<String, String> flowInfo;
        if(null != todayAllFlowExecInfo.get(mapKey)){
          flowInfo = todayAllFlowExecInfo.get(mapKey);
        } else {
          flowInfo = new HashMap<>();
          flowInfo.put("totalFlowNum", "0");
          flowInfo.put("flowSuccessNum", "0");
          flowInfo.put("flowRunningNum", "0");
          flowInfo.put("flowFailedNum", "0");
          flowInfo.put("flowKilledNum", "0");
          flowInfo.put("flowNoExecNum", "0");

          flowInfo.put("jobSuccessNum", "0");
          flowInfo.put("jobRunningNum", "0");
          flowInfo.put("jobFailedNum", "0");
          flowInfo.put("jobNoExecNum", "0");
          flowInfo.put("jobCancelNum", "0");
        }
        //项目名称
        flowInfo.put("projectName", exFlow.getProjectName());
        //工作流名称
        flowInfo.put("flowName", exFlow.getFlowId());
        //提交人
        flowInfo.put("submitUser", exFlow.getSubmitUser());
        //统计当天工作流运行情况
        if(Status.SUCCEEDED.equals(exFlow.getStatus())){
          flowInfo.put("flowSuccessNum", Integer.valueOf(flowInfo.get("flowSuccessNum")) + 1 + "");
          flowInfo.put("totalFlowNum", Integer.valueOf(flowInfo.get("totalFlowNum")) + 1 + "");
        }
        if(Status.RUNNING.equals(exFlow.getStatus())){
          flowInfo.put("flowRunningNum", (Integer.valueOf(flowInfo.get("flowRunningNum")) + 1) + "");
          flowInfo.put("totalFlowNum", Integer.valueOf(flowInfo.get("totalFlowNum")) + 1 + "");
        }
        if(Status.FAILED.equals(exFlow.getStatus())){
          flowInfo.put("flowFailedNum", (Integer.valueOf(flowInfo.get("flowFailedNum")) + 1) + "");
          flowInfo.put("totalFlowNum", Integer.valueOf(flowInfo.get("totalFlowNum")) + 1 + "");
        }
        if(Status.KILLED.equals(exFlow.getStatus())){
          flowInfo.put("flowKilledNum", (Integer.valueOf(flowInfo.get("flowKilledNum")) + 1) + "");
          flowInfo.put("totalFlowNum", Integer.valueOf(flowInfo.get("totalFlowNum")) + 1 + "");
        }
        if(Status.QUEUED.equals(exFlow.getStatus())){
          flowInfo.put("flowNoExecNum", (Integer.valueOf(flowInfo.get("flowNoExecNum")) + 1) + "");
          //flowInfo.put("totalFlowNum", Integer.valueOf(flowInfo.get("totalFlowNum")) + 1 + "");
        }
        if(Status.PREPARING.equals(exFlow.getStatus())){
          flowInfo.put("flowNoExecNum", (Integer.valueOf(flowInfo.get("flowNoExecNum")) + 1) + "");
          //flowInfo.put("totalFlowNum", Integer.valueOf(flowInfo.get("totalFlowNum")) + 1 + "");
        }
        setJobStatus(exFlow.getExecutableNodes(), flowInfo);
        flowInfo.put("execId", String.valueOf(exFlow.getExecutionId()));
        flowInfo.put("totalJobNum", exFlow.getExecutableNodes().size() + "");

        int count = 0;
        if(exFlow.getExecutableNodes().size() > 1){
          for(ExecutableNode exNode :exFlow.getExecutableNodes()){
              if(exNode instanceof ExecutableFlowBase ){
                  count += this.getExecuNodeCount(exNode);
              }
              else if(exNode instanceof ExecutableNode ){
                count++;
            }
          }
          flowInfo.put("totalJobNum", count + "");

        }else {
          flowInfo.put("totalJobNum", exFlow.getExecutableNodes().size() + "");
        }

        if(user.getRoles().contains("admin")) {
          flowInfo.put("todayFlowRuntimes", String.valueOf(this.executorManager.getTodayFlowRunTimesByFlowId(
                  String.valueOf(exFlow.getProjectId()), exFlow.getFlowId(), null)));
        } else {
          flowInfo.put("todayFlowRuntimes", String.valueOf(this.executorManager.getTodayFlowRunTimesByFlowId(
                  String.valueOf(exFlow.getProjectId()), exFlow.getFlowId(), user.getUserId())));
        }
        todayAllFlowExecInfo.put(mapKey, flowInfo);
      }

      for(Schedule schedule : schedules){
        //用与标识唯一Flow 避免不同项目工作流重名
        String mapKey = schedule.getProjectName() + ":" + schedule.getFlowName();
        Map<String, String> flowInfo;
        boolean flag = false;
        if(null != todayAllFlowExecInfo.get(mapKey)){
          flowInfo = todayAllFlowExecInfo.get(mapKey);
        } else {
          flag = true;
          flowInfo = new HashMap<>();
          flowInfo.put("totalFlowNum", "0");
          flowInfo.put("flowSuccessNum", "0");
          flowInfo.put("flowRunningNum", "0");
          flowInfo.put("flowFailedNum", "0");
          flowInfo.put("flowKilledNum", "0");
          flowInfo.put("flowNoExecNum", "0");

          flowInfo.put("jobSuccessNum", "0");
          flowInfo.put("jobRunningNum", "0");
          flowInfo.put("jobFailedNum", "0");

          flowInfo.put("jobCancelNum", "0");
          flowInfo.put("todayFlowRuntimes", "0");
        }
        //项目名称
        flowInfo.put("projectName", schedule.getProjectName());
        //工作流名称
        flowInfo.put("flowName", schedule.getFlowName());
        //项目提交人
        flowInfo.put("submitUser", schedule.getSubmitUser());
        Project project = this.projectManager.getProject(schedule.getProjectId());
        Flow flow = project.getFlow(schedule.getFlowName());
        if(flow == null) {
          logger.warn("flow: {} is not exist in project:{}.", schedule.getFlowName(), project.getName());
          continue;
        }

        if( flow.getNodes().size() >1){
          int count = 0;
          for(Node node:flow.getNodes()){
            if(StringUtils.isBlank(node.getEmbeddedFlowId())){
              count++;
            }else {
              count += this.getNodeCount(project,node.getEmbeddedFlowId());
            }
          }
          flowInfo.put("totalJobNum", count + "");
        }else{
          flowInfo.put("totalJobNum", flow.getNodes().size() + "");
        }
        // 当前没有运行过
        if(flag) {
          flowInfo.put("jobNoExecNum", String.valueOf(flow.getNodes().size()));
        }
        int scheduleCount = getScheduleTodayRunCount(schedule);
        if(!(Boolean) schedule.getOtherOption().get("activeFlag") || 0 == scheduleCount){
          continue;
        }else {
          flowInfo.put("flowNoExecNum", scheduleCount + "");
        }
        //flowInfo.put("totalflowNum", Integer.valueOf(flowInfo.get("totalFlowNum")) + 1 + "");
        todayAllFlowExecInfo.put(mapKey, flowInfo);
        //queueNum += getScheduleTodayRunCount(schedule);

      }
    } catch (Exception e) {
      logger.error("Get Today All Flow Info Error, caused by:", e);
    }

    List<Map> todayFlowExecuteInfoList = new ArrayList<>();
    for(String key : todayAllFlowExecInfo.keySet()){
      todayFlowExecuteInfoList.add(todayAllFlowExecInfo.get(key));
    }
    if ((page - 1) * size > todayFlowExecuteInfoList.size()) {
      ret.put("todayFlowExecuteInfo", new ArrayList<>());
    } else {
      if (page * size > todayFlowExecuteInfoList.size()) {
        ret.put("todayFlowExecuteInfo", todayFlowExecuteInfoList.subList((page - 1) * size, todayFlowExecuteInfoList.size()));
      } else {
        ret.put("todayFlowExecuteInfo", todayFlowExecuteInfoList.subList((page - 1) * size, page * size));
      }
    }
    ret.put("page", page);
    ret.put("size", size);
    ret.put("total", todayFlowExecuteInfoList.size());
  }

  private int getNodeCount(Project project,String nodeId){
    int count = 0;
    Flow flow = project.getFlow(nodeId);
    for(Node node:flow.getNodes()){
      if(node.getEmbeddedFlowId() !=null){
        count +=  this.getNodeCount(project,node.getEmbeddedFlowId());
      }else{
        count++;
      }
    }
    return count;
  }

  private int getExecuNodeCount(Object obj){
    ExecutableFlowBase exNode = (ExecutableFlowBase) obj;
    int count = 0;
    List<ExecutableNode> nodeList = exNode.getExecutableNodes();
    for(ExecutableNode node:nodeList){
      if(node instanceof ExecutableFlowBase){
        count += this.getExecuNodeCount(node);
      }else if(node instanceof  ExecutableNode){
        count++;
      }
    }
    return count;
  }


  private void setJobStatus(List<ExecutableNode> executableNodes, Map<String, String> flowInfo){
    for (ExecutableNode executableNode : executableNodes){
      switch (executableNode.getStatus()){
        case SUCCEEDED:
        case RETRIED_SUCCEEDED:
          flowInfo.put("jobSuccessNum", Integer.valueOf(flowInfo.getOrDefault("jobSuccessNum", "0")) + 1 + "");
          continue;
        case RUNNING:
          flowInfo.put("jobRunningNum", (Integer.valueOf(flowInfo.getOrDefault("jobRunningNum", "0")) + 1) + "");
          continue;
        case FAILED:
        case FAILED_FINISHING:
        case KILLED:
          flowInfo.put("jobFailedNum", (Integer.valueOf(flowInfo.getOrDefault("jobFailedNum", "0")) + 1) + "");
          continue;
//        case QUEUED:
//        case READY:
//        case PREPARING:
//          flowInfo.put("jobNoExecNum", (Integer.valueOf(flowInfo.getOrDefault("jobNoExecNum", "0")) + 1) + "");
//          continue;
        case CANCELLED:
          flowInfo.put("jobCancelNum", (Integer.valueOf(flowInfo.getOrDefault("jobCancelNum", "0")) + 1) + "");
      }
    }

    int jobNoExecNum = executableNodes.size() - Integer.valueOf(flowInfo.get("jobCancelNum"))
                                              - Integer.valueOf(flowInfo.get("jobFailedNum"))
                                              - Integer.valueOf(flowInfo.get("jobRunningNum"))
                                              - Integer.valueOf(flowInfo.get("jobSuccessNum"));

    flowInfo.put("jobNoExecNum", String.valueOf(jobNoExecNum));
  }

  /**
   * 获取定时调度当天需要执行的次数
   * @param schedule
   * @return
   */
  private int getScheduleTodayRunCount(final Schedule schedule){

    int runCount = 0;


    final DateTimeZone timezone = DateTimeZone.getDefault();
    final DateTime nowSchedTime = new DateTime(timezone);

    final long todayLong = nowSchedTime.getMillis();

    String cronExpression = schedule.getCronExpression();

    Calendar calendar = Calendar.getInstance();
    //获取当天凌晨毫秒数
//    calendar.set(Calendar.HOUR_OF_DAY, 0);
//    calendar.set(Calendar.MINUTE, 0);
//    calendar.set(Calendar.SECOND, 0);
//    calendar.set(Calendar.MILLISECOND, 1);

    //获取当天24点毫秒数
    calendar.set(Calendar.HOUR_OF_DAY, 23);
    calendar.set(Calendar.MINUTE, 59);
    calendar.set(Calendar.SECOND, 59);

    long todayLast = calendar.getTimeInMillis();


    //runCount = countTodaySchedule(cronExpression, todayLast, runCount, nowSchedTime.getMillis(), timezone);

    final DateTime nextTime = WebUtils.getNextCronRuntime(todayLong
        , timezone, Utils.parseCronExpression(cronExpression, timezone));
    long nextExecTime = nextTime.getMillis();

    while (nextExecTime != 0 && nextExecTime < todayLast) {
      runCount += 1;
      nextExecTime = WebUtils.getNextCronRuntime(nextExecTime, timezone
          , Utils.parseCronExpression(cronExpression, timezone)).getMillis();
    }

    return runCount;

  }

//  private int countTodaySchedule(final String cron, final long todayLong, int runCount,
//      final long nowSchedTime, final DateTimeZone timezone){
//
//    final DateTime nextTime = WebUtils.getNextCronRuntime(
//        nowSchedTime, timezone, Utils.parseCronExpression(cron, timezone));
//    long nextExecTime = nextTime.getMillis();
//
//    if(nextExecTime < todayLong){
//      runCount += 1;
//      runCount = countTodaySchedule(cron, todayLong, runCount, nowSchedTime, timezone);
//    }
//
//    return  runCount;
//  }



}
