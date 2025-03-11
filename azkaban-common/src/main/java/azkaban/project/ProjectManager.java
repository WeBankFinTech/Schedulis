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

package azkaban.project;

import static azkaban.Constants.ConfigurationKeys.WTSS_QUERY_SERVER_ENABLE;
import static azkaban.Constants.ConfigurationKeys.WTSS_QUERY_SERVER_ENABLE_PROJECT_CACHE;
import static azkaban.Constants.ConfigurationKeys.WTSS_QUERY_SERVER_PROJECT_CACHE_REFRESH_PERIOD;
import static java.util.Objects.requireNonNull;

import azkaban.Constants;
import azkaban.Constants.ConfigurationKeys;
import azkaban.alert.Alerter;
import azkaban.executor.AlerterHolder;
import azkaban.executor.ExecutableFlow;
import azkaban.executor.ExecutableNode;
import azkaban.executor.ExecutorLoader;
import azkaban.executor.ExecutorManagerException;
import azkaban.executor.Status;
import azkaban.flow.Flow;
import azkaban.flow.Node;
import azkaban.flow.SpecialJobTypes;
import azkaban.i18n.utils.LoadJsonUtils;
import azkaban.metrics.ProjectHourlyReportMertics;
import azkaban.project.ProjectLogEvent.EventType;
import azkaban.project.entity.FlowBusiness;
import azkaban.project.entity.ProjectChangeOwnerInfo;
import azkaban.project.entity.ProjectHourlyReportConfig;
import azkaban.project.entity.ProjectPermission;
import azkaban.project.entity.ProjectVersion;
import azkaban.project.validator.ValidationReport;
import azkaban.project.validator.ValidatorConfigs;
import azkaban.project.validator.XmlValidatorManager;
import azkaban.scheduler.Schedule;
import azkaban.scheduler.ScheduleLoader;
import azkaban.server.HttpRequestUtils;
import azkaban.sla.SlaOption;
import azkaban.storage.StorageManager;
import azkaban.system.SystemManager;
import azkaban.system.SystemUserLoader;
import azkaban.system.SystemUserManagerException;
import azkaban.system.entity.WtssUser;
import azkaban.user.Permission;
import azkaban.user.Permission.Type;
import azkaban.user.User;
import azkaban.user.UserUtils;
import azkaban.utils.CaseInsensitiveConcurrentHashMap;
import azkaban.utils.HttpUtils;
import azkaban.utils.PagingListStreamUtil;
import azkaban.utils.Props;
import azkaban.utils.PropsUtils;
import azkaban.utils.Utils;
import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.google.common.io.Files;

import java.io.File;
import java.io.IOException;
import java.sql.SQLException;
import java.time.LocalTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Predicate;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import javax.inject.Inject;
import javax.inject.Singleton;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;

import com.google.gson.Gson;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang.StringUtils;
import org.joda.time.DateTime;
import org.joda.time.ReadablePeriod;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


@Singleton
public class ProjectManager {

  private static final Logger logger = LoggerFactory.getLogger(ProjectManager.class);
  private final AzkabanProjectLoader azkabanProjectLoader;
  private final ProjectLoader projectLoader;
  private final ExecutorLoader executorLoader;
  private final SystemUserLoader systemUserLoader;
  private final AlerterHolder alerterHolder;
  private final Props props;
  private final boolean creatorDefaultPermissions;

  // 是否打开Flow信息的缓存
  private final boolean enableFlowCache;

  // 是否关闭调度，开启查询服务
  private boolean enableQueryServer;

  // 开启查询服务后，是否开启project cache
  private boolean enableProjectCache;

  // Both projectsById and projectsByName cache need to be thread safe since they are accessed
  // from multiple threads concurrently without external synchronization for performance.
  private final ConcurrentHashMap<Integer, Project> projectsById =
          new ConcurrentHashMap<>();
  private final CaseInsensitiveConcurrentHashMap<Project> projectsByName =
          new CaseInsensitiveConcurrentHashMap<>();
  private final ConcurrentHashMap<Integer, Project> inactiveProject = new ConcurrentHashMap<>();

  /**
   * key: project Id
   * value: Map<flowName, Flow> flows
   */
  private final LoadingCache<Integer, Map<String, Flow>> flowCache = CacheBuilder
          .newBuilder()
          .maximumSize(500)
          .expireAfterAccess(15, TimeUnit.MINUTES)
          .build(new CacheLoader<Integer, Map<String, Flow>>() {
            @Override
            public Map<String, Flow> load(Integer projectId) throws Exception {
              return getFlowsFromDB(projectId);
            }
          });


  private SystemManager systemManager;

  private boolean isHaModel;

  /**
   * 运营小时报线程
   */
  private ScheduledExecutorService hourlyReportThread;

  private ScheduleLoader scheduleLoader;


  @Inject
  public ProjectManager(final AzkabanProjectLoader azkabanProjectLoader,
                        final ProjectLoader loader,
                        final StorageManager storageManager,
                        ExecutorLoader executorLoader, SystemUserLoader systemUserLoader, AlerterHolder alerterHolder, final Props props,
                        final SystemManager systemManager, final ScheduleLoader scheduleLoader) {
    this.projectLoader = requireNonNull(loader);
    this.executorLoader = executorLoader;
    this.systemUserLoader = systemUserLoader;
    this.alerterHolder = alerterHolder;
    this.props = requireNonNull(props);
    this.azkabanProjectLoader = requireNonNull(azkabanProjectLoader);
    this.systemManager = requireNonNull(systemManager);
    this.creatorDefaultPermissions =
            props.getBoolean("creator.default.proxy", true);
    this.scheduleLoader = scheduleLoader;

    this.enableFlowCache = props.getBoolean(ConfigurationKeys.WTSS_ENABLE_PROJECT_MANAGER_FLOW_CACHE, false);
    this.enableQueryServer = props.getBoolean(WTSS_QUERY_SERVER_ENABLE, false);
    this.enableProjectCache = props.getBoolean(WTSS_QUERY_SERVER_ENABLE_PROJECT_CACHE, false);
    this.isHaModel = props.getBoolean(ConfigurationKeys.WEBSERVER_HA_MODEL, false);

    // The prop passed to XmlValidatorManager is used to initialize all the
    // validators
    // Each validator will take certain key/value pairs from the prop to
    // initialize itself.
    final Props prop = new Props(props);
    prop.put(ValidatorConfigs.PROJECT_ARCHIVE_FILE_PATH, "initialize");
    // By instantiating an object of XmlValidatorManager, this will verify the
    // config files for the validators.
    new XmlValidatorManager(prop);
    if (!enableQueryServer || enableProjectCache) {
      loadAllProjects();
      loadAllInactiveProjects();
      loadProjectWhiteList();
    }

    if (enableQueryServer && enableProjectCache) {
      refreshProjectTimeTask();
    }

    if (enableQueryServer) {
      // 运营小时报线程
      hourlyReportThread = Executors.newSingleThreadScheduledExecutor();

      // 小时报处理任务
      Runnable task = this::handleHourlyReport;

      // 安排首次执行
      scheduleNextExecution(hourlyReportThread, task);
    }
  }

  public String handleHourlyReport() {

    String emailMessage = "";
    try {
      // 获取所有配置了小时报的项目集合
      List<ProjectHourlyReportConfig> projectHourlyReportConfigList = this.projectLoader.getProjectHourlyReportConfig();
      logger.info("starting to handle report data from {} projects",
              projectHourlyReportConfigList.size());
      List<String> projectNames = projectHourlyReportConfigList.stream().map(ProjectHourlyReportConfig::getProjectName).collect(Collectors.toList());

      // 所有超时工作流任务超时明细
      List<ExecutableFlow> allOvertimeFlows = getAllOvertimeRunningFlows(  projectNames);

      // 遍历项目集合
      for (ProjectHourlyReportConfig projectHourlyReportConfig : projectHourlyReportConfigList) {

        String projectName = projectHourlyReportConfig.getProjectName();

        if (StringUtils.isBlank(projectHourlyReportConfig.getReportReceiver())) {
          logger.info("This project {} does not configure alarm_user, skip..", projectName);
          return "";
        }

        ProjectHourlyReportMertics projectHourlyReportMertics = null;
        // 组装所需要的数据
        try {
          projectHourlyReportMertics = fetchProjectHourlyReportMertics(projectName);
          List<ExecutableFlow> overtimeFlows = allOvertimeFlows.stream()
                  .filter(flow -> flow.getProjectName().equals(projectName))
                  .collect(Collectors.toList());
          projectHourlyReportMertics.setOvertimeFlows(overtimeFlows);
          projectHourlyReportMertics.setAllFlows(getAllFlows(projectName));


        } catch (ExecutorManagerException e) {
          logger.error("fetch project hourly report metrics failed", e);
        }
        String[] receivers = projectHourlyReportConfig.getReportReceiver().split("[,;\\s]+");
        StringBuilder alertUsers = new StringBuilder();
        for (String name : receivers) {
          try {
            WtssUser wtssUser = this.systemUserLoader.getWtssUserByUsername(name);
            if (null != wtssUser) {
              String userEmail = wtssUser.getEmail();
              // 用户邮箱信息可能为空
              if (StringUtils.isNotBlank(userEmail)) {
                alertUsers.append(userEmail).append(";");
              } else {
                // 用户邮箱信息为空，根据用户名进行拼接
                alertUsers.append(wtssUser.getUsername()).append("@webank.com")
                        .append(";");
              }
            }
          } catch (SystemUserManagerException e) {
            logger.error("fetch wtss user failed.", e);
          }
        }
        projectHourlyReportConfig.setReportReceiver(alertUsers.toString());
        String message = "";
        // 发送数据
        String[] alertWays = projectHourlyReportConfig.getReportWay().split(",");
        for (String alertWay : alertWays) {
          message = handleReport(projectHourlyReportMertics, projectHourlyReportConfig, Integer.parseInt(alertWay));

        }

        emailMessage = emailMessage +"\n"+message;
      }

    } catch (Exception e) {
      logger.error("handle project hourly report error ", e);
    }
    return emailMessage;
  }

  private String handleReport(ProjectHourlyReportMertics projectHourlyReportMertics,
                              ProjectHourlyReportConfig projectHourlyReportConfig, int alertWay) {
    Alerter alerter = alerterHolder.get("email");
    if (null == alerter) {
      alerter = alerterHolder.get("default");
    }
    String emailMessage = alerter.alertProjectHourlyReportMertics(props, projectHourlyReportMertics, projectHourlyReportConfig, alertWay);
    return emailMessage;
  }

  private ProjectHourlyReportMertics fetchProjectHourlyReportMertics(String projectName)
          throws ExecutorManagerException {
    int total = 0;
    int readyCount = 0;
    int runningCount = 0;
    int failedCount = 0;
    int succeedCount = 0;
    List<Map<String, String>> unfinishedFlows = new ArrayList<>();
    List<Map<String, String>> failedFlows = new ArrayList<>();
    List<String> failedJob = new ArrayList<>();
    //flow名称，成功job数量
    Map<String, Integer> succeedJobs = new HashMap<>();
    //flow名称，失败job数量
    Map<String, Integer> failJobs = new HashMap<>();

    Project project = getProject(projectName);
    List<Flow> flows = project.getFlows();
    ProjectHourlyReportMertics projectHourlyReportMertics = new ProjectHourlyReportMertics();
    for (Flow flow : flows) {

      List<ExecutableFlow> flowInfos = this.executorLoader.getFlowTodayHistory(project.getId(), flow.getId());
      for (ExecutableFlow flowInfo : flowInfos) {
        total++;
        HashMap<String, String> map = new HashMap<>();
        map.put("executionId", String.valueOf(flowInfo.getExecutionId()));
        map.put("flowId", flowInfo.getFlowId());
        map.put("runDate", flowInfo.getRunDate());
        map.put("startTime", String.valueOf(flowInfo.getStartTime()));
        map.put("endTime", String.valueOf(flowInfo.getEndTime()));
        if (Status.isStatusFailed(flowInfo.getStatus())) {
          failedCount++;
          failedFlows.add(map);
          // 统计满足major告警的失败工作流任务
          failedJob = flowInfo.getExecutableNodes().stream().filter(node -> Status.isFailed(node.getStatus()) &&
                          flowInfo.getSlaOptions().stream().anyMatch(slaOption -> {
                            String level = slaOption.getLevel();
                            return StringUtils.isNotBlank(level) && "MAJOR".equalsIgnoreCase(level) &&
                                    node.getId().equals(slaOption.getInfo().get(SlaOption.INFO_JOB_NAME));
                          })).map(ExecutableNode::getId)
                  .collect(Collectors.toList());
          if (!failedJob.isEmpty()) {
            map.put("jobIds", failedJob.stream().collect(Collectors.joining(",")));
            map.put("alertLevel", "MAJOR");
            failedJob.clear();
          }

        } else if (Status.READY.equals(flowInfo.getStatus())) {
          readyCount++;
          unfinishedFlows.add(map);
        } else if (Status.isStatusRunning(flowInfo.getStatus())) {
          runningCount++;
          unfinishedFlows.add(map);
        } else if (Status.isStatusSucceeded(flowInfo.getStatus())) {
          succeedCount++;
        }
      }


    }

    projectHourlyReportMertics.setTotal(total);
    projectHourlyReportMertics.setReadyCount(readyCount);
    projectHourlyReportMertics.setRunningCount(runningCount);
    projectHourlyReportMertics.setFailedCount(failedCount);
    projectHourlyReportMertics.setSucceedCount(succeedCount);
    projectHourlyReportMertics.setUnfinishedFlows(unfinishedFlows);
    projectHourlyReportMertics.setFailedFlows(failedFlows);
    projectHourlyReportMertics.setSucceedJobs(succeedJobs);
    projectHourlyReportMertics.setFailJobs(failJobs);

    return projectHourlyReportMertics;
  }


  private void getAllJob(List<String> allJobs, Collection<Node> nodes, Project project,
                         String flowName) {
    for (final Node n : nodes) {
      if (n.getEmbeddedFlowId() != null) {
        final Flow childFlow = project.getFlow(n.getEmbeddedFlowId());
        getAllJob(allJobs, childFlow.getNodes(), project, flowName + ":" + n.getId());
      }
      allJobs.add(flowName + ":" + n.getId());
    }
  }

  private List<ExecutableFlow> getAllOvertimeRunningFlows( List<String> projectNames) {
    // 获取所有调度
    List<Schedule> allSchedules = new ArrayList<>();
    try {
      allSchedules = scheduleLoader.loadAllSchedules().stream().filter(schedule -> projectNames.contains(schedule.getProjectName())).collect(Collectors.toList());
    } catch (Exception e) {
      logger.warn("Load all schedules failed. ", e);
    }
    List<ExecutableFlow> overtimeFlowList = new ArrayList<>();
    for (Schedule schedule : allSchedules) {
      // 有效调度筛选
      Map<String, Object> otherOptionMap = schedule.getOtherOption();
      boolean isValidFlow = (Boolean) otherOptionMap.getOrDefault("validFlow", true);
      boolean activeFlag = (Boolean) otherOptionMap.getOrDefault("activeFlag", false);
      if (isValidFlow && activeFlag) {
        // 获取配置超时告警时间点的调度，此处的告警配置包含工作流级别以及任务级别
        List<SlaOption> slaOptions = schedule.getSlaOptions();
        if (slaOptions != null && !slaOptions.isEmpty()) {
          // 存在告警配置
          for (SlaOption slaOption : slaOptions) {
            if (slaOption.getInfo().containsKey(SlaOption.INFO_ABS_TIME)
                    || slaOption.getInfo().containsKey(SlaOption.INFO_DURATION)) {
              // 超时告警逻辑
              List<ExecutableFlow> overtimeRunningFlow = getOvertimeRunningFlow(schedule, slaOption);
              if (CollectionUtils.isNotEmpty(overtimeRunningFlow)) {
                overtimeFlowList.addAll(overtimeRunningFlow);
              }
            }
          }
        }
      }
    }
    return overtimeFlowList;
  }

  private List<ExecutableFlow> getAllFlows(String projectName) {
    // 获取所有调度
    Project project = getProject(projectName);
    List<Flow> flows = project.getFlows();
    List<ExecutableFlow> allFlowList = new ArrayList<>();
    if (CollectionUtils.isNotEmpty(flows)) {
      flows.forEach(flow -> {
        try {
          List<ExecutableFlow> flowInfos = this.executorLoader.getFlowTodayHistory(project.getId(), flow.getId());
          List<ExecutableFlow> flowExcutables = flowInfos.stream().filter(info -> info.getStartTime() != -1).collect(Collectors.toList());
          allFlowList.addAll(flowExcutables);
        } catch (ExecutorManagerException e) {
          logger.error("ExecutableFlow:" + e);
        }

      });
    }

    return allFlowList;
  }

  private List<ExecutableFlow> getOvertimeRunningFlow(Schedule schedule, SlaOption slaOption) {

    // 超时时间点
    DateTime time = null;
    if (slaOption.getInfo().containsKey(SlaOption.INFO_ABS_TIME)) {
      String[] absTime = slaOption.getInfo().get(SlaOption.INFO_ABS_TIME).toString()
              .split(":");
      time = new DateTime().withHourOfDay(Integer.parseInt(absTime[0]))
              .withMinuteOfHour(Integer.parseInt(absTime[1]));

    }
    // 超时时长
    long durationInMillis = Long.MAX_VALUE;
    if (slaOption.getInfo().containsKey(SlaOption.INFO_DURATION)) {
      final ReadablePeriod duration = Utils
              .parsePeriodString((String) slaOption.getInfo().get(SlaOption.INFO_DURATION));
      durationInMillis = duration.toPeriod().toStandardDuration().getMillis();
    }

    //获取调度工作流最近一次执行
    List<ExecutableFlow> executableFlows = null;
    List<ExecutableFlow> flowList = new ArrayList<>();
    try {
      executableFlows = executorLoader.fetchFlowHistory(schedule.getProjectId(), schedule.getFlowName());
    } catch (ExecutorManagerException e) {
      logger.warn("Fetch flowHistory failed. ", e);
    }
    if (executableFlows != null && !executableFlows.isEmpty()) {

      for (ExecutableFlow recentExecutableFlow: executableFlows) {
        if (!Status.isStatusFinished(recentExecutableFlow.getStatus())) {
          if (recentExecutableFlow.getStartTime() == -1) {
            return null;
          }
          // 配置的超时时间未超过当前时间，即当前时间已经超过了设置的超时时间
          boolean timeOver = time != null && time.isBeforeNow();

          long runtime = System.currentTimeMillis() - recentExecutableFlow.getStartTime();
          // 运行时长超过设置的超时告警时长
          boolean runTimeOver = runtime > durationInMillis;
          if(timeOver || runTimeOver){
            flowList.add(recentExecutableFlow);
          }

        }
      }

    }

    return flowList;
  }

  /**
   * 计算到下一个整点的时延时间，并安排任务执行
   *
   * @param scheduler
   * @param task
   */
  private void scheduleNextExecution(ScheduledExecutorService scheduler, Runnable task) {
    // 计算到下一个整点的延迟时间
    long delay = computeNextDelay();

    logger.info("next project hourly report task will run in {} ms ", delay);
    // 调度任务并在任务执行后重新安排下次执行
    scheduler.schedule(() -> {
      try {
        task.run();
      } catch (Exception e) {
        throw new RuntimeException(e);
      } finally {
        // 再次调度下次执行
        scheduleNextExecution(scheduler, task);
      }
    }, delay, TimeUnit.MILLISECONDS);
  }

  /**
   * 计算当前时间到下一个整点的毫秒数
   *
   * @return
   */
  private long computeNextDelay() {
    Calendar nextHour = Calendar.getInstance();
    nextHour.set(Calendar.MINUTE, 0);
    nextHour.set(Calendar.SECOND, 0);
    nextHour.set(Calendar.MILLISECOND, 0);
    nextHour.add(Calendar.HOUR_OF_DAY, 1);

    logger.info("next project hourly report task will start at {}", nextHour);

    long currentTimeMillis = System.currentTimeMillis();
    long nextHourTimeInMillis = nextHour.getTimeInMillis();

    return nextHourTimeInMillis - currentTimeMillis;
  }

  public int updateProjectHourlyReportConfig(Project project, User user, String reportWay,
                                             String reportReceiverString) throws SQLException {
    return this.projectLoader.updateProjectHourlyReportConfig(project, user, reportWay,
            reportReceiverString);
  }

  public int removeProjectHourlyReportConfig(Project project) throws SQLException {
    return this.projectLoader.removeProjectHourlyReportConfig(project);
  }

  public void fetchProjectHourlyReport(String projectName, User user, HashMap<String, Object> ret) {
    Project project = projectsByName.get(projectName);
    ProjectHourlyReportMertics projectHourlyReportMertics = null;
    if (project.hasPermission(user, Type.READ)) {
      try {
        projectHourlyReportMertics = fetchProjectHourlyReportMertics(projectName);
      } catch (ExecutorManagerException e) {
        logger.error("fetch project hourly report metrics failed", e);
      }
    } else {
      ret.put("error", "The user has no read permission");
    }
    ret.put("projectHourlyReportMertics", projectHourlyReportMertics);
  }

  public List<ProjectHourlyReportConfig> getProjectHourlyReportConfigList(User user)
          throws SQLException {
    return this.projectLoader.getProjectHourlyReportConfig();
  }


  private void refreshProjectTimeTask() {
    Timer timer = new Timer();
    TimerTask task = new TimerTask() {
      @Override
      public void run() {
        logger.info("Start to refresh project cached information");
        try {
          loadAllProjects();
          loadAllInactiveProjects();
        } catch (Exception e) {
          logger.error("Failed to refresh project", e);
        }
        logger.info("Finished to refresh project cached information");
      }
    };
    // 每5分钟刷新一次
    int period = props.getInt(WTSS_QUERY_SERVER_PROJECT_CACHE_REFRESH_PERIOD, 30);
    timer.schedule(task, 1000 * 60 * 5, 1000 * 60 * period);
  }

  private Map<Integer, Project> getProjectsIDMap() {
    if (!enableQueryServer || enableProjectCache) {
      return this.projectsById;
    } else {
      //1. 直接load all
      return loadPorjectMap(1);
    }
  }

  private Map<Integer, Project> getInactiveProjectIDMap() {
    if (!enableQueryServer || enableProjectCache) {
      return this.inactiveProject;
    } else {
      //1. 直接load all
      return loadPorjectMap(0);
    }
  }

  private Map<Integer, Project> loadPorjectMap(int active) {
    Map<Integer, Project> projectIDMap = new HashMap<>();
    final List<Project> projects;
    try {
      projects = this.projectLoader.fetchAllProjects(active);
    } catch (final ProjectManagerException e) {
      throw new RuntimeException("Could not load projects from store.", e);
    }
    for (final Project proj : projects) {
      projectIDMap.put(proj.getId(), proj);
    }
    return projectIDMap;
  }

  /**
   * 判断对应工程的Flow是否设置调度
   *
   * @param project
   * @param flow
   * @return
   * @throws IOException
   * @throws ProjectManagerException
   */
  public boolean hasFlowTrigger(final Project project, final Flow flow)
          throws IOException, ProjectManagerException {
    final String flowFileName = flow.getId() + ".flow";
    final int latestFlowVersion = this.projectLoader.getLatestFlowVersion(project.getId(), flow
            .getVersion(), flowFileName);
    if (latestFlowVersion > 0) {
      final File tempDir = com.google.common.io.Files.createTempDir();
      final File flowFile;
      try {
        flowFile = this.projectLoader
                .getUploadedFlowFile(project.getId(), project.getVersion(),
                        flowFileName, latestFlowVersion, tempDir);

        final FlowTrigger flowTrigger = FlowLoaderUtils.getFlowTriggerFromYamlFile(flowFile);
        return flowTrigger != null;
      } catch (final Exception ex) {
        logger.error("error in getting flow file", ex);
        throw ex;
      } finally {
        FlowLoaderUtils.cleanUpDir(tempDir);
      }
    } else {
      return false;
    }
  }

  /**
   * 从数据库中加载所有激活的工程信息
   */
  private void loadAllProjects() {
    final List<Project> projects;
    try {
      projects = this.projectLoader.fetchAllProjects(1);
    } catch (final ProjectManagerException e) {
      throw new RuntimeException("Could not load projects from store.", e);
    }
    for (final Project proj : projects) {
      this.projectsByName.put(proj.getName(), proj);
      this.projectsById.put(proj.getId(), proj);
    }

    for (final Project proj : projects) {
      loadAllProjectFlows(proj);
    }

  }

  /**
   * 从数据库中加载所有未激活的工程信息
   */
  private void loadAllInactiveProjects() {
    final List<Project> projects;
    try {
      logger.info("load all inactive project.");
      projects = this.projectLoader.fetchAllProjects(0);
    } catch (final ProjectManagerException e) {
      throw new RuntimeException("Could not load projects from store.", e);
    }
    for (final Project proj : projects) {
      this.inactiveProject.put(proj.getId(), proj);
    }

    for (final Project proj : projects) {
      loadAllProjectFlows(proj);
    }

  }

  /**
   * 加载工程的所有flows信息
   * 开启Flow cache后，只缓存工作流的名字方便如调度管理等只需要获取名字的情况
   *
   * @param project
   */
  public void loadAllProjectFlows(final Project project) {
    try {
      final List<Flow> flows = this.projectLoader.fetchAllProjectFlows(project);
      final Map<String, Flow> flowMap = new HashMap<>();
      for (final Flow flow : flows) {
        if (enableFlowCache) {
          Flow cacheFlow = new Flow(flow.getId());
          cacheFlow.setVersion(flow.getVersion());
          // cacheFlow.setCondition(flow.getCondition());
          flowMap.put(flow.getId(), cacheFlow);
        } else {
          flowMap.put(flow.getId(), flow);
        }
      }

      project.setFlows(flowMap);
    } catch (final ProjectManagerException e) {
      throw new RuntimeException("Could not load projects flows from store.", e);
    }
  }


  public Props getProps() {
    return this.props;
  }

  /**
   * 获取所有用户的Projects
   *
   * @param user
   * @return
   */
  public List<Project> getUserProjects(final User user) {
    final ArrayList<Project> array = new ArrayList<>();
    for (final Project project : getProjectsIDMap().values()) {
      final Permission perm = project.getUserPermission(user);

      if (perm != null && project.getCreateUser().equals(user.getUserId())) {
        array.add(project);
      }

    }

    // FIXME Sort by project name.
    List<Project> newArray = array.stream().sorted(
                    Comparator.comparing(Project::getName, String.CASE_INSENSITIVE_ORDER))
            .collect(Collectors.toList());

    return newArray;
  }

  /**
   * 获取该用户所有协同的工程信息
   *
   * @param user
   * @return
   */
  public List<Project> getGroupProjects(final User user) {
    final List<Project> array = new ArrayList<>();
    for (final Project project : getProjectsIDMap().values()) {
      if (project.hasGroupPermission(user, Type.READ)) {
        array.add(project);
      }
    }
    //FIXME Sort by project name.
    List<Project> newArray = array.stream().sorted(
            Comparator.comparing(Project::getName, String.CASE_INSENSITIVE_ORDER)).collect(Collectors.toList());

    return newArray;
  }

  public List<Project> getUserProjectsByRegex(final User user, final String regexPattern) {
    final List<Project> array = new ArrayList<>();
    final Pattern pattern;
    try {
      pattern = Pattern.compile(regexPattern, Pattern.CASE_INSENSITIVE);
    } catch (final PatternSyntaxException e) {
      logger.error("Bad regex pattern " + regexPattern);
      return array;
    }

    for (final Project project : getProjectsIDMap().values()) {
      final Permission perm = project.getUserPermission(user);

      if (perm != null
              && (perm.isPermissionSet(Type.ADMIN) || perm
              .isPermissionSet(Type.READ))) {
        if (pattern.matcher(project.getName()).find() || pattern.matcher(project.getDescription()).find() || pattern.matcher(project.getCreateUser()).find()) {
          array.add(project);
        }
      }
    }
    // FIXME Sort by project name.
    List<Project> newArray = array.stream().sorted(
            Comparator.comparing(Project::getName, String.CASE_INSENSITIVE_ORDER)).collect(Collectors.toList());

    return newArray;
  }


  public List<Project> getDeleteProjects(final User user) {
    return new ArrayList<>();
  }

  public List<Project> getProjects(boolean active) {
    List<Project> projectList;
    if (active) {
      projectList = new ArrayList<>(getProjectsIDMap().values());
    } else {
      projectList = new ArrayList<>(getInactiveProjectIDMap().values());
    }
    // FIXME Sort by project name.
    List<Project> newArray = projectList.stream().sorted(
            Comparator.comparing(Project::getName, String.CASE_INSENSITIVE_ORDER)).collect(Collectors.toList());

    return newArray;
  }

  public List<Project> getProjectsByRegex(final String regexPattern) {
    final List<Project> allProjects = new ArrayList<>();
    final Pattern pattern;
    try {
      pattern = Pattern.compile(regexPattern, Pattern.CASE_INSENSITIVE);
    } catch (final PatternSyntaxException e) {
      logger.error("Bad regex pattern " + regexPattern);
      return allProjects;
    }
    for (final Project project : getProjects(true)) {
      if (pattern.matcher(project.getName()).find()) {
        allProjects.add(project);
      }
    }
    // FIXME Sort by project name.
    List<Project> newArray = allProjects.stream().sorted(
            Comparator.comparing(Project::getName, String.CASE_INSENSITIVE_ORDER)).collect(Collectors.toList());

    return newArray;
  }

  /**
   * Checks if a project is active using project_id
   */
  public Boolean isActiveProject(final int id) {
    return getProjectsIDMap().containsKey(id);
  }

  /**
   * TODO 如果enableFlowCache开启，返回的project应该深度复制，不然也有轻微泄漏问题
   * fetch active project from cache and inactive projects from db by project_name
   */
  public Project getProject(final String name) {
    Project fetchedProject = this.projectsByName.get(name);
    if (fetchedProject == null) {
      try {
        logger.info("Project {} doesn't exist in cache, fetching from DB now.", name);
        fetchedProject = this.projectLoader.fetchProjectByName(name);
      } catch (final ProjectManagerException e) {
        logger.error("Could not load project {} from store.", name, e);
      }
    }
    if (enableFlowCache && fetchedProject != null) {
      try {
        fetchedProject.setFlows(flowCache.get(fetchedProject.getId()));
      } catch (ExecutionException e) {
        logger.error("Could not load project {} flow info ", name, e);
      }
    }
    return fetchedProject;
  }

  /**
   * fetch active project from cache and inactive projects from db by project_id
   */
  public Project getProject(final int id) {
    Project fetchedProject = getProjectsIDMap().get(id);
    if (fetchedProject == null) {
      try {
        fetchedProject = this.projectLoader.fetchProjectById(id);
      } catch (final ProjectManagerException e) {
        logger.error("Could not load project from store.", e);
      }
    }
    if (enableFlowCache && fetchedProject != null) {
      try {
        fetchedProject.setFlows(flowCache.get(fetchedProject.getId()));
      } catch (ExecutionException e) {
        logger.error("Could not load project {} flow info ", fetchedProject.getId(), e);
      }
    }
    return fetchedProject;
  }


  /**
   * 获取工程信息和工作流的基本信息，该方法为只给到enableFlowCache开启后启动作用
   * 用于获取工作流的名字信息更方便
   * 该方法为直接从缓存获取信息
   */
  public Project getProjectAndFlowBaseInfoByName(final String name) {
    Project fetchedProject = this.projectsByName.get(name);
    if (fetchedProject == null) {
      try {
        logger.info("Project {} doesn't exist in cache, fetching from DB now.", name);
        fetchedProject = this.projectLoader.fetchProjectByName(name);
      } catch (final ProjectManagerException e) {
        logger.error("Could not load project {} from store.", name, e);
      }
    }
    return fetchedProject;
  }

  /**
   * 获取工程信息和工作流的基本信息，该方法为只给到enableFlowCache开启后启动作用
   * 用于获取工作流的名字信息更方便
   * 该方法为直接从缓存获取信息
   */
  public Project getProjectAndFlowBaseInfo(final int id) {
    Project fetchedProject = getProjectsIDMap().get(id);
    if (fetchedProject == null) {
      try {
        fetchedProject = this.projectLoader.fetchProjectById(id);
      } catch (final ProjectManagerException e) {
        logger.error("Could not load project id {} from store.", id, e);
      }
    }
    return fetchedProject;
  }

  public Project getInactiveProject(final int id) {
    Project fetchedProject = getInactiveProjectIDMap().get(id);
    if (fetchedProject == null) {
      try {
        fetchedProject = this.projectLoader.fetchProjectById(id);
      } catch (final ProjectManagerException e) {
        logger.error("Could not load project from store.", e);
      }
    }
    return fetchedProject;
  }

  /**
   * 更新工程的创建用户
   *
   * @param project
   * @param newCreateUser 新用户名
   * @param user
   * @throws Exception
   */
  public void updateProjectCreateUser(final Project project, String newCreateUser, User user) throws Exception {
    final String oldCreateUser = project.getCreateUser();
    WtssUser newWtssUser = this.systemManager.getSystemUserByUserName(newCreateUser);
    // 老用户可能已经在系统中不存在，即为 null
    WtssUser oldWtssUser = this.systemManager.getSystemUserByUserName(project.getCreateUser());
    Objects.requireNonNull(newWtssUser, String.format("%s, user does not exist, user category cannot be confirmed.", newCreateUser));

    if (null != oldWtssUser) {
      // 1、系统管理员 2、是否是项目管理员 3、是否是部門运维
      if (!(UserUtils.checkPermission(user, Type.ADMIN) || project.checkPermission(user,
              Type.ADMIN) || this.systemManager.checkDepartmentMaintainer(
              oldWtssUser.getDepartmentId(),
              user.getUserId()))) {
        throw new RuntimeException("You must be a project administrator, "
                + "department operation and maintenance personnel, and system administrator to change the project creator.");
      }
      // 新旧用户角色权限判断
      UserUtils.checkChangeCreateUserPermission(oldWtssUser, newWtssUser);
    } else {
      // 1、系统管理员 2、是否是项目管理员
      if (!(UserUtils.checkPermission(user, Type.ADMIN) || project.checkPermission(user,
              Type.ADMIN))) {
        throw new RuntimeException("You must be a project administrator, "
                + "department operation and maintenance personnel, and system administrator to change the project creator.");
      }
    }
    this.projectLoader.updateProjectCreateUser(project, newWtssUser, user);
    this.projectLoader.postEvent(project, EventType.CREATE_USER,
            user.getUserId() + (org.apache.commons.lang.StringUtils.isEmpty(user.getNormalUser()) ? "" : ("(" + user.getNormalUser() + ")")), "createUser: " + oldCreateUser + " changed to " + newCreateUser);
    final Permission perm = new Permission();
    perm.setPermission(Type.ADMIN, true);
    perm.setPermission(Type.READ, true);
    perm.setPermission(Type.WRITE, true);
    perm.setPermission(Type.EXECUTE, true);
    perm.setPermission(Type.SCHEDULE, true);
    final boolean group = false;
    this.updateProjectPermission(project, newWtssUser.getUsername(), perm, group, user);
  }

  /**
   * 更新项目负责人
   *
   * @param project
   * @param principal
   * @param user
   * @throws Exception
   */
  public void updateProjectPrincipal(final Project project, String principal, User user) throws Exception {

    String oldPrincipal = project.getPrincipal();
    // 1、系统管理员 2、是否是项目管理员
    if (!(UserUtils.checkPermission(user, Type.ADMIN) || project.checkPermission(user,
            Type.ADMIN))) {
      throw new RuntimeException("You must be a project administrator, "
              + "department operation and maintenance personnel, and system administrator to change the project creator.");
    }

    this.projectLoader.updateProjectPrincipal(project, principal, user);
    this.projectLoader.postEvent(project, EventType.PRINCIPAL,
            user.getUserId() + (org.apache.commons.lang.StringUtils.isEmpty(user.getNormalUser()) ? "" : ("(" + user.getNormalUser() + ")")), "product principal : " + oldPrincipal + " changed to " + principal);
    //重新存入缓存
    project.setPrincipal(principal);
    this.projectsByName.put(project.getName(), project);
    this.projectsById.put(project.getId(), project);
    HttpUtils.reloadWebData(this.getProps().getStringList("azkaban.all.web.url"), "reloadProject",
            project.getName());

  }


  /**
   * fetch active project from cache and inactive projects from db by project_id
   */
  public List<Flow> getRunningFlow(final Project project) {
    return this.projectLoader.getRunningFlow(project);
  }


  /**
   * Permanently delete all project files and properties data for all versions of a project and log
   * event in project_events table
   */
  public synchronized Project purgeProject(final Project project, final User deleter)
          throws ProjectManagerException {
    this.projectLoader.cleanOlderProjectVersion(project.getId(),
            project.getVersion() + 1, Collections.emptyList());
    this.projectLoader
            .postEvent(project, EventType.PURGE, deleter.getUserId() + (org.apache.commons.lang.StringUtils.isEmpty(deleter.getNormalUser()) ? "" : ("(" + deleter.getNormalUser() + ")")), String
                    .format("Purged versions before %d", project.getVersion() + 1));
    return project;
  }

  public synchronized Project removeProject(final Project project, final User deleter)
          throws ProjectManagerException {
    this.projectLoader.removeProject(project, deleter.getUserId());
    this.projectLoader.postEvent(project, EventType.DELETED, deleter.getUserId() + (org.apache.commons.lang.StringUtils.isEmpty(deleter.getNormalUser()) ? "" : ("(" + deleter.getNormalUser() + ")")),
            null);

    this.projectsByName.remove(project.getName());
    this.projectsById.remove(project.getId());
    this.flowCache.invalidate(project.getId());
    this.inactiveProject.put(project.getId(), project);
    if (this.isHaModel) {
      HttpUtils.reloadWebData(this.getProps().getStringList("azkaban.all.web.url"), "reloadProject",
              project.getName());
    }
    return project;
  }


  /**
   * 恢复对应的工程
   *
   * @param projectName
   * @param user
   * @throws ProjectManagerException
   */
  public void restoreProject(String projectName, int projectId, User user, HashMap<String, Object> ret) throws ProjectManagerException {
    if (getProjectsIDMap().containsKey(projectId) || this.projectsByName.containsKey(projectName)) {
      logger.warn("The project:{} may have been restored. ", projectId);
      ret.put("error", "restore failed, the project may have been restored.");
      return;
    }
    int res = this.projectLoader.restoreProject(projectName, projectId, user.getUserId());
    if (res != 1) {
      logger.warn("The project:{} may have been restored", projectId);
      ret.put("error", "restore failed, the project may have been restored.");
      return;
    }
    Project p = this.getInactiveProject(projectId);
    p.setActive(true);
    this.inactiveProject.remove(projectId);
    this.projectsByName.put(p.getName(), p);
    this.projectsById.put(p.getId(), p);
    this.flowCache.invalidate(p.getId());
    this.projectLoader.postEvent(p, EventType.RESTORE_PROJECT, user.getUserId() + (org.apache.commons.lang.StringUtils.isEmpty(user.getNormalUser()) ? "" : ("(" + user.getNormalUser() + ")")), "restore project.");
    if (this.isHaModel) {
      HttpUtils.reloadWebData(this.getProps().getStringList("azkaban.all.web.url"), "reloadProject",
              p.getName());
    }

  }

  public void updateProjectDescription(final Project project, final String description,
                                       final User modifier) throws ProjectManagerException {
    this.projectLoader.updateDescription(project, description, modifier.getUserId());
    this.projectsByName.put(project.getName(), project);
    this.projectsById.put(project.getId(), project);
    this.flowCache.invalidate(project.getId());
    this.projectLoader.postEvent(project, EventType.DESCRIPTION,
            modifier.getUserId() + (org.apache.commons.lang.StringUtils.isEmpty(modifier.getNormalUser()) ? "" : ("(" + modifier.getNormalUser() + ")")), "Description changed to " + description);
    if (this.isHaModel) {
      HttpUtils.reloadWebData(this.getProps().getStringList("azkaban.all.web.url"), "reloadProject",
              project.getName());
    }

  }

  public void updateJobLimit(final Project project, final int jobLimit,
                             final User modifier) throws ProjectManagerException {
    this.projectLoader.updateJobLimit(project, jobLimit, modifier.getUserId());
    this.projectLoader.postEvent(project, EventType.JOB_EXECUTE_LIMIT,
            modifier.getUserId() + (
                    org.apache.commons.lang.StringUtils.isEmpty(modifier.getNormalUser()) ? ""
                            : ("(" + modifier.getNormalUser() + ")")),
            "Job Execute Limit changed to " + jobLimit);
    if (this.isHaModel) {
      HttpUtils.reloadWebData(this.getProps().getStringList("azkaban.all.web.url"), "reloadProject",
              project.getName());
    }

  }

  public void updateProjectLock(Project project) throws ProjectManagerException {
    this.projectLoader.updateProjectLock(project);
    if (this.isHaModel) {
      HttpUtils.reloadWebData(this.getProps().getStringList("azkaban.all.web.url"), "reloadProject",
              project.getName());
    }
  }

  public List<ProjectLogEvent> getProjectEventLogs(final Project project,
                                                   final int results, final int skip) throws ProjectManagerException {
    return this.projectLoader.getProjectEvents(project, results, skip);
  }

  public List<ProjectVersion> getProjectVersions(final Project project,
                                                 final int results, final int skip) throws ProjectManagerException {
    return this.projectLoader.getProjectVersions(project, results, skip);
  }

  public Props getPropertiesFromFlowFile(final Flow flow, final String jobName, final String
          flowFileName, final int flowVersion) throws ProjectManagerException {
    File tempDir = null;
    Props props = null;
    try {
      tempDir = Files.createTempDir();
      final File flowFile = this.projectLoader.getUploadedFlowFile(flow.getProjectId(), flow
              .getVersion(), flowFileName, flowVersion, tempDir);
      final String path =
              jobName == null ? flow.getId() : flow.getId() + Constants.PATH_DELIMITER + jobName;
      props = FlowLoaderUtils.getPropsFromYamlFile(path, flowFile);
    } catch (final Exception e) {
      logger.error("Failed to get props from flow file. ", e);
    } finally {
      FlowLoaderUtils.cleanUpDir(tempDir);
    }
    return props;
  }

  public Props getProperties(final Project project, final Flow flow, final String jobName,
                             final String source) throws ProjectManagerException {
    if (FlowLoaderUtils.isAzkabanFlowVersion20(flow.getAzkabanFlowVersion())) {
      // Return the properties from the original uploaded flow file.
      return getPropertiesFromFlowFile(flow, jobName, source, 1);
    } else {
      return this.projectLoader.fetchProjectProperty(project, source);
    }
  }

  public Props getJobOverrideProperty(final Project project, final Flow flow, final String jobName,
                                      final String source) throws ProjectManagerException {
    if (FlowLoaderUtils.isAzkabanFlowVersion20(flow.getAzkabanFlowVersion())) {
      final int flowVersion = this.projectLoader
              .getLatestFlowVersion(flow.getProjectId(), flow.getVersion(), source);
      return getPropertiesFromFlowFile(flow, jobName, source, flowVersion);
    } else {
      return this.projectLoader
              .fetchProjectProperty(project, jobName + Constants.JOB_OVERRIDE_SUFFIX);
    }
  }

  public void setJobOverrideProperty(final Project project, final Flow flow, final Props prop,
                                     final String jobName, final String source, final User modifier)
          throws ProjectManagerException {
    File tempDir = null;
    Props oldProps = null;
    if (FlowLoaderUtils.isAzkabanFlowVersion20(flow.getAzkabanFlowVersion())) {
      try {
        tempDir = Files.createTempDir();
        final int flowVersion = this.projectLoader.getLatestFlowVersion(flow.getProjectId(), flow
                .getVersion(), source);
        final File flowFile = this.projectLoader.getUploadedFlowFile(flow.getProjectId(), flow
                .getVersion(), source, flowVersion, tempDir);
        final String path = flow.getId() + Constants.PATH_DELIMITER + jobName;
        oldProps = FlowLoaderUtils.getPropsFromYamlFile(path, flowFile);

        FlowLoaderUtils.setPropsInYamlFile(path, flowFile, prop);
        this.projectLoader
                .uploadFlowFile(flow.getProjectId(), flow.getVersion(), flowFile, flowVersion + 1);
      } catch (final Exception e) {
        logger.error("Failed to set job override property. ", e);
      } finally {
        FlowLoaderUtils.cleanUpDir(tempDir);
      }
    } else {
      // prop.setSource(jobName + Constants.JOB_OVERRIDE_SUFFIX);
      oldProps = this.projectLoader.fetchProjectProperty(project, prop.getSource());

      if (oldProps == null) {
        this.projectLoader.uploadProjectProperty(project, prop);
      } else {
        this.projectLoader.updateProjectProperty(project, prop);
      }

      prop.setSource(jobName + Constants.JOB_OVERRIDE_SUFFIX);
      Props oldPropsJor = this.projectLoader.fetchProjectProperty(project, prop.getSource());
      if (oldPropsJor == null) {
        this.projectLoader.uploadProjectProperty(project, prop);
      } else {
        this.projectLoader.updateProjectProperty(project, prop);
      }
    }

    final String diffMessage = PropsUtils.getPropertyDiff(oldProps, prop);

    this.projectLoader.postEvent(project, EventType.PROPERTY_OVERRIDE,
            modifier.getUserId() + (org.apache.commons.lang.StringUtils.isEmpty(modifier.getNormalUser()) ? "" : ("(" + modifier.getNormalUser() + ")")), diffMessage);
    return;
  }

  public void updateProjectSetting(final Project project)
          throws ProjectManagerException {
    this.projectLoader.updateProjectSettings(project);
    this.projectsByName.put(project.getName(), project);
    this.projectsById.put(project.getId(), project);
    this.flowCache.invalidate(project.getId());
    if (this.isHaModel) {
      HttpUtils.reloadWebData(this.getProps().getStringList("azkaban.all.web.url"), "reloadProject",
              project.getName());
    }

  }

  public void addProjectProxyUser(final Project project, final String proxyName,
                                  final User modifier) throws ProjectManagerException {
    logger.info("User " + modifier.getUserId() + " adding proxy user "
            + proxyName + " to project " + project.getName());
    project.addProxyUser(proxyName);

    this.projectLoader.postEvent(project, EventType.PROXY_USER,
            modifier.getUserId() + (org.apache.commons.lang.StringUtils.isEmpty(modifier.getNormalUser()) ? "" : ("(" + modifier.getNormalUser() + ")")), "Proxy user " + proxyName
                    + " is added to project.");
    updateProjectSetting(project);
  }

  public void removeProjectProxyUser(final Project project, final String proxyName,
                                     final User modifier) throws ProjectManagerException {
    logger.info("User " + modifier.getUserId() + " removing proxy user "
            + proxyName + " from project " + project.getName());
    project.removeProxyUser(proxyName);

    this.projectLoader.postEvent(project, EventType.PROXY_USER,
            modifier.getUserId() + (org.apache.commons.lang.StringUtils.isEmpty(modifier.getNormalUser()) ? "" : ("(" + modifier.getNormalUser() + ")")), "Proxy user " + proxyName
                    + " has been removed form the project.");
    updateProjectSetting(project);
  }

  public void updateProjectPermission(final Project project, final String name,
                                      final Permission perm, final boolean group, final User modifier)
          throws ProjectManagerException {
    logger.info("User " + modifier.getUserId()
            + " updating permissions for project " + project.getName() + " for "
            + name + " " + perm.toString());
    this.projectLoader.updatePermission(project, name, perm, group);
    if (group) {
      this.projectLoader.postEvent(project, EventType.GROUP_PERMISSION,
              modifier.getUserId() + (org.apache.commons.lang.StringUtils.isEmpty(modifier.getNormalUser()) ? "" : ("(" + modifier.getNormalUser() + ")")), "Permission for group " + name + " set to "
                      + perm.toString());
    } else {
      this.projectLoader.postEvent(project, EventType.USER_PERMISSION,
              modifier.getUserId() + (org.apache.commons.lang.StringUtils.isEmpty(modifier.getNormalUser()) ? "" : ("(" + modifier.getNormalUser() + ")")), "Permission for user " + name + " set to "
                      + perm.toString());
    }
    if (this.isHaModel) {
      HttpUtils.reloadWebData(this.getProps().getStringList("azkaban.all.web.url"),
              "refreshProjectPermission", project.getName());
    }

  }

  public void removeProjectPermission(final Project project, final String name,
                                      final boolean group, final User modifier) throws ProjectManagerException {
    logger.info("User " + modifier.getUserId()
            + " removing permissions for project " + project.getName() + " for "
            + name);
    this.projectLoader.removePermission(project, name, group);
    if (group) {
      this.projectLoader.postEvent(project, EventType.GROUP_PERMISSION,
              modifier.getUserId() + (org.apache.commons.lang.StringUtils.isEmpty(modifier.getNormalUser()) ? "" : ("(" + modifier.getNormalUser() + ")")), "Permission for group " + name + " removed.");
    } else {
      this.projectLoader.postEvent(project, EventType.USER_PERMISSION,
              modifier.getUserId() + (org.apache.commons.lang.StringUtils.isEmpty(modifier.getNormalUser()) ? "" : ("(" + modifier.getNormalUser() + ")")), "Permission for user " + name + " removed.");
    }

    if (this.isHaModel) {
      HttpUtils.reloadWebData(this.getProps().getStringList("azkaban.all.web.url"),
              "refreshProjectPermission", project.getName());
    }

  }

  /**
   * This method retrieves the uploaded project zip file from DB. A temporary file is created to
   * hold the content of the uploaded zip file. This temporary file is provided in the
   * ProjectFileHandler instance and the caller of this method should call method
   * {@ProjectFileHandler.deleteLocalFile} to delete the temporary file.
   *
   * @param version - latest version is used if value is -1
   * @return ProjectFileHandler - null if can't find project zip file based on project name and
   * version
   */
  public ProjectFileHandler getProjectFileHandler(final Project project, final int version)
          throws ProjectManagerException {
    return this.azkabanProjectLoader.getProjectFile(project, version);
  }

  public File getProjectFiles(List<Project> projectList) throws ProjectManagerException {
    return this.azkabanProjectLoader.getProjectFiles(projectList);
  }

  public Map<String, ValidationReport> uploadProject(final Project project,
                                                     final File archive, final String fileType, final User uploader, final Props additionalProps)
          throws Exception {
    Map<String, ValidationReport> retMap = this.azkabanProjectLoader.uploadProject(project, archive,
            fileType, uploader, additionalProps);
    projectsByName.get(project.getName()).setFromType(project.getFromType());
    getProjectsIDMap().get(project.getId()).setFromType(project.getFromType());
    flowCache.invalidate(project.getId());
    if (this.isHaModel) {
      HttpUtils.reloadWebData(this.getProps().getStringList("azkaban.all.web.url"), "reloadProject",
              project.getName());
    }

    return retMap;
  }

  public void checkUpFileDataObject(File file, Project project, Props prop) throws SQLException {
    logger.info("upload file DataChecker");
    this.azkabanProjectLoader.checkUpFileDataObject(file, project,prop);
  }

  public Map<String, Boolean> checkFlowName(final Project project, final File archive,
                                            final String fileType, final Props additionalProps) throws Exception {
    return this.azkabanProjectLoader.checkFlowName(project, archive, fileType, additionalProps);
  }

  public void updateFlow(final Project project, final Flow flow)
          throws ProjectManagerException {
    this.projectLoader.updateFlow(project, flow.getVersion(), flow);
    this.projectsByName.put(project.getName(), project);
    this.projectsById.put(project.getId(), project);
    this.flowCache.invalidate(project.getId());
    if (this.isHaModel) {
      HttpUtils.reloadWebData(this.getProps().getStringList("azkaban.all.web.url"), "reloadProject",
              project.getName());
    }

  }


  public void postProjectEvent(final Project project, final EventType type, final String user,
                               final String message) {
    this.projectLoader.postEvent(project, type, user, message);
  }

  public boolean loadProjectWhiteList() {
    if (this.props.containsKey(ProjectWhitelist.XML_FILE_PARAM)) {
      ProjectWhitelist.load(this.props);
      return true;
    }
    return false;
  }


  public Project createProject(final String projectName, final String description, final String group,
                               final User creator, final String source) throws ProjectManagerException {

    String languageType = LoadJsonUtils.getLanguageType();
    Map<String, String> dataMap;
    if ("zh_CN".equalsIgnoreCase(languageType)) {
      dataMap = LoadJsonUtils.transJson("/conf/azkaban-common-zh_CN.json",
              "azkaban.project.ProjectManager");
    } else {
      dataMap = LoadJsonUtils.transJson("/conf/azkaban-common-en_US.json",
              "azkaban.project.ProjectManager");
    }

    if (projectName == null || projectName.trim().isEmpty()) {
      throw new ProjectManagerException(dataMap.get("noBlankProgramName"));
    } else if (description == null || description.trim().isEmpty()) {
      throw new ProjectManagerException(dataMap.get("noBlankProgramDesc"));
    } else if (creator == null) {
      throw new ProjectManagerException(dataMap.get("noInvalidUser"));
    } else if (!projectName.matches("[a-zA-Z][a-zA-Z_0-9|-]*")) {
      throw new ProjectManagerException(dataMap.get("checkProgramName"));
    } else if (source == null) {
      throw new ProjectManagerException(dataMap.get("noBlankProgramSource"));
    } else if (projectName.length() > 64) {
      throw new ProjectManagerException(dataMap.get("projectNameLength"));
    } else if (description.length() > 2048) {
      throw new ProjectManagerException(dataMap.get("projectDescLength"));
    }

    final Project newProject;
    synchronized (this) {
      if (this.projectsByName.containsKey(projectName)) {
        throw new ProjectManagerException(dataMap.get("hasExistProgram"));
      }

      logger.info("Trying to create " + projectName + " by user "
              + creator.getUserId());
      newProject = this.projectLoader.createNewProject(projectName, description, creator, source);
      this.projectsByName.put(newProject.getName(), newProject);
      this.projectsById.put(newProject.getId(), newProject);
      this.flowCache.invalidate(newProject.getId());
    }

    if (this.creatorDefaultPermissions) {
      // Add permission to project
      this.projectLoader.updatePermission(newProject, creator.getUserId(),
              new Permission(Permission.Type.ADMIN), "".equals(group) ? false : true,
              group);

      // 需求 不把当前主用户添加到代理中
      //newProject.addProxyUser(creator.getUserId());
      // 先清空原有的代理用户
      newProject.removeAllProxyUsers();
      // 添加用户配置的代理用户
      for (String pUser : creator.getProxyUsers()) {
        newProject.addProxyUser(pUser);
      }

      try {
        updateProjectSetting(newProject);
      } catch (final ProjectManagerException e) {
        logger.error("Failed to updateProjectSetting", e);
        throw e;
      }
    }

    this.projectLoader.postEvent(newProject, EventType.CREATED, creator.getUserId() + (org.apache.commons.lang.StringUtils.isEmpty(creator.getNormalUser()) ? "" : ("(" + creator.getNormalUser() + ")")),
            null);

    if (this.isHaModel) {
      HttpUtils.reloadWebData(this.getProps().getStringList("azkaban.all.web.url"), "reloadProject",
              newProject.getName());
    }

    return newProject;
  }

  public PagingListStreamUtil<Project> getUserProjectsPage(int skip, int size, List<Project> userProjectList) {

    PagingListStreamUtil<Project> paging = new PagingListStreamUtil<>(userProjectList, size);
    paging.setCurPageNo(skip);

    return paging;
  }

  public PagingListStreamUtil<Project> getGroupProjectsPage(int skip, int size, List<Project> groupProjectList) {

    PagingListStreamUtil<Project> paging = new PagingListStreamUtil<>(groupProjectList, size);
    paging.setCurPageNo(skip);

    return paging;
  }

  public PagingListStreamUtil<Project> getAllProjectsPage(int skip, int size, List<Project> allProjectList) {

    PagingListStreamUtil<Project> paging = new PagingListStreamUtil<>(allProjectList, size);
    paging.setCurPageNo(skip);

    return paging;
  }

  /**
   * 根据搜索条件查询组项目
   *
   * @param user
   * @param regexPattern
   * @return
   */
  public List<Project> getGroupProjectsByRegex(final User user, final String regexPattern) {
    final List<Project> array = new ArrayList<>();
    final Pattern pattern;
    try {
      pattern = Pattern.compile(regexPattern, Pattern.CASE_INSENSITIVE);
    } catch (final PatternSyntaxException e) {
      logger.error("Bad regex pattern " + regexPattern);
      return array;
    }
    for (final Project project : getProjectsIDMap().values()) {
      if (project.hasGroupPermission(user, Type.READ)) {
        if (pattern.matcher(project.getName()).find()) {
          array.add(project);
        }
      }

    }
    //按照项目名称排序
    List<Project> newArray = array.stream().sorted(
            Comparator.comparing(Project::getName, String.CASE_INSENSITIVE_ORDER)).collect(Collectors.toList());

    return newArray;
  }

  public void removeProjectPermission(final Project project, final String userId, final User modifier) throws ProjectManagerException {
    logger.info("User " + modifier.getUserId()
            + " removing permissions for project " + project.getName() + " for "
            + userId);
    this.projectLoader.removeProjectPermission(project, userId);

    this.projectLoader.postEvent(project, EventType.USER_PERMISSION,
            modifier.getUserId() + (org.apache.commons.lang.StringUtils.isEmpty(modifier.getNormalUser()) ? "" : ("(" + modifier.getNormalUser() + ")")), "Permission for user " + userId + " removed.");
    if (this.isHaModel) {
      HttpUtils.reloadWebData(this.getProps().getStringList("azkaban.all.web.url"),
              "refreshProjectPermission", project.getName());
    }
  }

  /**
   * 查看自己创建的项目和别人创建的但是拥有权限的项目
   *
   * @param user
   * @return
   */
  public List<Project> getUserAllProjects(final User user, final String orderOption, boolean active) {
    final ArrayList<Project> array = new ArrayList<>();
    List<Integer> departmentList = systemManager.getDepartmentMaintainerDepListByUserName(user.getUserId());
    if (active) {
      List<Integer> projectIds = systemManager.getMaintainedProjectsByDepList(departmentList, 1);
      for (final Project project : getProjectsIDMap().values()) {
        final Permission permission = project.getUserPermission(user);
        Predicate<Permission> hasPermission = perm -> perm != null && (perm.isPermissionSet(Type.ADMIN) || perm.isPermissionSet(Type.READ));
        Predicate<User> isMaintained = u -> projectIds.contains(project.getId());
        if (isMaintained.test(user) || hasPermission.test(permission)) {
          array.add(project);
        }
      }
    } else {
      List<Integer> projectIds = systemManager.getMaintainedProjects(user, 0);
      for (final Project project : getInactiveProjectIDMap().values()) {
        final Permission permission = project.getUserPermission(user);
        Predicate<Permission> hasPermission = perm -> perm != null && perm.isPermissionSet(Type.ADMIN);
        Predicate<User> isMaintained = u -> projectIds.contains(project.getId());
        if (isMaintained.test(user) || hasPermission.test(permission)) {
          array.add(project);
        }
      }
    }

//    //按照项目名称排序
//    List<Project> newArray = array.stream().sorted(
//        Comparator.comparing(Project::getName, String.CASE_INSENSITIVE_ORDER))
//        .collect(Collectors.toList());

    //按照项目名称排序
    List<Project> newArray = new ArrayList<>();

    if ("orderUpdateTimeSort".equals(orderOption)) {
      //修改时间排序 从大到小
      newArray = array.stream().sorted(
                      Comparator.comparing(Project::getLastModifiedTimestamp).reversed())
              .collect(Collectors.toList());
    } else {
      //按照项目名称排序
      newArray = array.stream().sorted(
                      Comparator.comparing(Project::getName, String.CASE_INSENSITIVE_ORDER))
              .collect(Collectors.toList());
    }

    return newArray;
  }

  public List<Project> getMaintainedProjects(final User user, final List<Integer> projectIds, final String orderOption, boolean active) {
    final ArrayList<Project> array = new ArrayList<>();
    List<Project> projects;
    if (active) {
      projects = new ArrayList<>(getProjectsIDMap().values());
    } else {
      projects = new ArrayList<>(getInactiveProjectIDMap().values());
    }
    for (final Project project : projects) {
      final Permission permission = project.getUserPermission(user);
      Predicate<Permission> hasPermission = perm -> perm != null && (perm.isPermissionSet(Type.ADMIN) || perm.isPermissionSet(Type.READ));
      Predicate<Project> isMaintained = proj -> projectIds.contains(proj.getId());
      if (isMaintained.test(project) || hasPermission.test(permission)) {
        array.add(project);
      }
    }

//    //按照项目名称排序
//    List<Project> newArray = array.stream().sorted(
//        Comparator.comparing(Project::getName, String.CASE_INSENSITIVE_ORDER))
//        .collect(Collectors.toList());

    //按照项目名称排序
    List<Project> newArray = new ArrayList<>();

    if ("orderUpdateTimeSort".equals(orderOption)) {
      //修改时间排序 从大到小
      newArray = array.stream().sorted(
                      Comparator.comparing(Project::getLastModifiedTimestamp).reversed())
              .collect(Collectors.toList());
    } else {
      //按照项目名称排序
      newArray = array.stream().sorted(
                      Comparator.comparing(Project::getName, String.CASE_INSENSITIVE_ORDER))
              .collect(Collectors.toList());
    }

    return newArray;
  }

  public List<Project> getUserPersonProjectsByRegex(final User user, final String regexPattern,
                                                    final String orderOption) {
    final List<Project> array = new ArrayList<>();
    final Pattern pattern;
    try {
      pattern = Pattern.compile(regexPattern, Pattern.CASE_INSENSITIVE);
    } catch (final PatternSyntaxException e) {
      logger.error("Bad regex pattern " + regexPattern);
      return array;
    }

    for (final Project project : getProjectsIDMap().values()) {
      final Permission perm = project.getUserPermission(user);

      if (perm != null
              && project.getCreateUser().equals(user.getUserId())) {
        if (pattern.matcher(project.getName()).find()
                || pattern.matcher(project.getDescription()).find()
                || pattern.matcher(project.getCreateUser()).find()) {
          array.add(project);
        }
      }
    }
//    //按照项目名称排序
//    List<Project> newArray = array.stream().sorted(
//        Comparator.comparing(Project::getName, String.CASE_INSENSITIVE_ORDER)).collect(Collectors.toList());

    //按照项目名称排序
    List<Project> newArray = new ArrayList<>();

    if ("orderUpdateTimeSort".equals(orderOption)) {
      //修改时间排序 从大到小
      newArray = array.stream().sorted(
                      Comparator.comparing(Project::getLastModifiedTimestamp).reversed())
              .collect(Collectors.toList());
    } else {
      //按照项目名称排序
      newArray = array.stream().sorted(
                      Comparator.comparing(Project::getName, String.CASE_INSENSITIVE_ORDER))
              .collect(Collectors.toList());
    }


    return newArray;
  }


  public List<Project> getUserProjects(final User user, final String orderOption) {
    final ArrayList<Project> array = new ArrayList<>();
    for (final Project project : getProjectsIDMap().values()) {

      final Permission perm = project.getUserPermission(user);

      //有项目权限 并且是项目创建人
      if (perm != null && project.getCreateUser().equals(user.getUserId())) {
        array.add(project);
      }

    }

    //按照项目名称排序
    List<Project> newArray = new ArrayList<>();

    if ("orderUpdateTimeSort".equals(orderOption)) {
      //修改时间排序 从大到小
      newArray = array.stream().sorted(
                      Comparator.comparing(Project::getLastModifiedTimestamp).reversed())
              .collect(Collectors.toList());
    } else {
      //按照项目名称排序
      newArray = array.stream().sorted(
                      Comparator.comparing(Project::getName, String.CASE_INSENSITIVE_ORDER))
              .collect(Collectors.toList());
    }


    return newArray;
  }

  /**
   * get active or inactive project
   *
   * @param orderOption
   * @param active
   * @return
   */
  public List<Project> getProjects(final String orderOption, boolean active) {

    List<Project> projectList;
    if (active) {
      projectList = new ArrayList<>(getProjectsIDMap().values());
    } else {
      projectList = new ArrayList<>(getInactiveProjectIDMap().values());
    }
//    //按照项目名称排序
//    List<Project> newArray = projectList.stream().sorted(
//        Comparator.comparing(Project::getName, String.CASE_INSENSITIVE_ORDER)).collect(Collectors.toList());

    //按照项目名称排序
    List<Project> newArray = new ArrayList<>();

    if ("orderUpdateTimeSort".equals(orderOption)) {
      //修改时间排序 从大到小
      newArray = projectList.stream().sorted(
                      Comparator.comparing(Project::getLastModifiedTimestamp).reversed())
              .collect(Collectors.toList());
    } else {
      //按照项目名称排序
      newArray = projectList.stream().sorted(
                      Comparator.comparing(Project::getName, String.CASE_INSENSITIVE_ORDER))
              .collect(Collectors.toList());
    }


    return newArray;
  }

  public List<Project> getProjects(String projContain, String flowContain,
                                   String execIdContain, String userContain, String status, long begin, long end,
                                   String subsystem, String busPath, String department, String runDate,
                                   int skip, int size, int flowType, String orderOption, Boolean active) {
    List<Project> projectList;
    if (active) {
      projectList = projectLoader.preciseSearchFetchAllProjects(projContain, flowContain, execIdContain, userContain,
              status, begin, end, subsystem, busPath, department, runDate, skip, size, flowType, 1);
    } else {
      projectList = projectLoader.preciseSearchFetchAllProjects(projContain, flowContain, execIdContain, userContain,
              status, begin, end, subsystem, busPath, department, runDate, skip, size, flowType, 0);
    }
//    //按照项目名称排序
//    List<Project> newArray = projectList.stream().sorted(
//        Comparator.comparing(Project::getName, String.CASE_INSENSITIVE_ORDER)).collect(Collectors.toList());

    //按照项目名称排序
    List<Project> newArray = new ArrayList<>();

    if ("orderUpdateTimeSort".equals(orderOption)) {
      //修改时间排序 从大到小
      newArray = projectList.stream().sorted(
                      Comparator.comparing(Project::getLastModifiedTimestamp).reversed())
              .collect(Collectors.toList());
    } else {
      //按照项目名称排序
      newArray = projectList.stream().sorted(
                      Comparator.comparing(Project::getName, String.CASE_INSENSITIVE_ORDER))
              .collect(Collectors.toList());
    }
    return newArray;
  }

  public List<Project> getProjects(String projContain, String flowContain,
                                   String execIdContain, String userContain, String status, long begin, long end,
                                   String subsystem, String busPath, String department, String runDate,
                                   int skip, int size, int flowType, String orderOption, Boolean active, List<Integer> maintainedProjectIds, User user) {
    List<Project> array = new ArrayList<>();
    List<Project> projectList;
    if (active) {
      projectList = projectLoader.preciseSearchFetchAllProjects(projContain, flowContain, execIdContain, userContain,
              status, begin, end, subsystem, busPath, department, runDate, skip, size, flowType, 1);
    } else {
      projectList = projectLoader.preciseSearchFetchAllProjects(projContain, flowContain, execIdContain, userContain,
              status, begin, end, subsystem, busPath, department, runDate, skip, size, flowType, 0);
    }
    for (final Project project : projectList) {
      final Permission permission = project.getUserPermission(user);
      Predicate<Permission> hasPermission = perm -> perm != null && (perm.isPermissionSet(Type.ADMIN) || perm.isPermissionSet(Type.READ));
      Predicate<Project> isMaintained = proj -> maintainedProjectIds.contains(proj.getId());
      if (isMaintained.test(project) || hasPermission.test(permission)) {
        array.add(project);
      }
    }
//    //按照项目名称排序
//    List<Project> newArray = projectList.stream().sorted(
//        Comparator.comparing(Project::getName, String.CASE_INSENSITIVE_ORDER)).collect(Collectors.toList());

    //按照项目名称排序
    List<Project> newArray = new ArrayList<>();

    if ("orderUpdateTimeSort".equals(orderOption)) {
      //修改时间排序 从大到小
      newArray = array.stream().sorted(
                      Comparator.comparing(Project::getLastModifiedTimestamp).reversed())
              .collect(Collectors.toList());
    } else {
      //按照项目名称排序
      newArray = array.stream().sorted(
                      Comparator.comparing(Project::getName, String.CASE_INSENSITIVE_ORDER))
              .collect(Collectors.toList());
    }
    return newArray;
  }

  public List<Project> getAllUserProjects(String projContain, String flowContain,
                                          String execIdContain, String userContain, String status, long begin, long end,
                                          String subsystem, String busPath, String department, String runDate,
                                          int skip, int size, int flowType, String orderOption, Boolean active, User user) {
    List<Project> projectList;
    List<Project> array = new ArrayList<>();
    boolean isDepartmentMaintainer = systemManager.isDepartmentMaintainer(user);
    if (active) {
      List<Integer> projectIds = systemManager.getMaintainedProjects(user, 1);
      projectList = projectLoader.preciseSearchFetchAllProjects(projContain, flowContain, execIdContain, userContain,
              status, begin, end, subsystem, busPath, department, runDate, skip, size, flowType, 1);
      for (final Project project : projectList) {
        final Permission permission = project.getUserPermission(user);
        Predicate<Permission> hasPermission = perm -> perm != null && (perm.isPermissionSet(Type.ADMIN) || perm.isPermissionSet(Type.READ));
        Predicate<User> isMaintained = u -> isDepartmentMaintainer && projectIds.contains(project.getId());
        if (isMaintained.test(user) || hasPermission.test(permission)) {
          array.add(project);
        }
      }
    } else {
      List<Integer> projectIds = systemManager.getMaintainedProjects(user, 0);
      projectList = projectLoader.preciseSearchFetchAllProjects(projContain, flowContain, execIdContain, userContain,
              status, begin, end, subsystem, busPath, department, runDate, skip, size, flowType, 0);
      for (final Project project : projectList) {
        final Permission permission = project.getUserPermission(user);
        Predicate<Permission> hasPermission = perm -> perm != null && perm.isPermissionSet(Type.ADMIN);
        Predicate<User> isMaintained = u -> isDepartmentMaintainer && projectIds.contains(project.getId());
        if (isMaintained.test(user) || hasPermission.test(permission)) {
          array.add(project);
        }
      }
    }
//    //按照项目名称排序
//    List<Project> newArray = projectList.stream().sorted(
//        Comparator.comparing(Project::getName, String.CASE_INSENSITIVE_ORDER)).collect(Collectors.toList());

    //按照项目名称排序
    List<Project> newArray = new ArrayList<>();

    if ("orderUpdateTimeSort".equals(orderOption)) {
      //修改时间排序 从大到小
      newArray = array.stream().sorted(
                      Comparator.comparing(Project::getLastModifiedTimestamp).reversed())
              .collect(Collectors.toList());
    } else {
      //按照项目名称排序
      newArray = array.stream().sorted(
                      Comparator.comparing(Project::getName, String.CASE_INSENSITIVE_ORDER))
              .collect(Collectors.toList());
    }
    return newArray;
  }

  public List<Project> getUserProjects(String projContain, String flowContain,
                                       String execIdContain, String userContain, String status, long begin, long end,
                                       String subsystem, String busPath, String department, String runDate,
                                       int skip, int size, int flowType, String orderOption, User user) {
    List<Project> projectList;
    List<Project> array = new ArrayList<>();

    projectList = projectLoader.preciseSearchFetchAllProjects(projContain, flowContain, execIdContain, userContain,
            status, begin, end, subsystem, busPath, department, runDate, skip, size, flowType, 1);
    for (final Project project : projectList) {
      final Permission perm = project.getUserPermission(user);
      //有项目权限 并且是项目创建人
      if (perm != null && project.getCreateUser().equals(user.getUserId())) {
        array.add(project);
      }
    }
//    //按照项目名称排序
//    List<Project> newArray = projectList.stream().sorted(
//        Comparator.comparing(Project::getName, String.CASE_INSENSITIVE_ORDER)).collect(Collectors.toList());

    //按照项目名称排序
    List<Project> newArray = new ArrayList<>();

    if ("orderUpdateTimeSort".equals(orderOption)) {
      //修改时间排序 从大到小
      newArray = array.stream().sorted(
                      Comparator.comparing(Project::getLastModifiedTimestamp).reversed())
              .collect(Collectors.toList());
    } else {
      //按照项目名称排序
      newArray = array.stream().sorted(
                      Comparator.comparing(Project::getName, String.CASE_INSENSITIVE_ORDER))
              .collect(Collectors.toList());
    }
    return newArray;
  }

  public List<Project> getProjectsByRegex(final String regexPattern, final String orderOption, boolean active) {
    final List<Project> allProjects = new ArrayList<>();
    final Pattern pattern;
    try {
      pattern = Pattern.compile(regexPattern, Pattern.CASE_INSENSITIVE);
    } catch (final PatternSyntaxException e) {
      logger.error("Bad regex pattern " + regexPattern);
      return allProjects;
    }
    for (final Project project : getProjects(active)) {
      if (pattern.matcher(project.getName()).find()
              || pattern.matcher(project.getDescription()).find()
              || pattern.matcher(project.getCreateUser()).find()) {
        allProjects.add(project);
      }
    }
//    //按照项目名称排序
//    List<Project> newArray = allProjects.stream().sorted(
//        Comparator.comparing(Project::getName, String.CASE_INSENSITIVE_ORDER)).collect(Collectors.toList());

    //按照项目名称排序
    List<Project> newArray = new ArrayList<>();

    if ("orderUpdateTimeSort".equals(orderOption)) {
      //修改时间排序 从大到小
      newArray = allProjects.stream().sorted(
                      Comparator.comparing(Project::getLastModifiedTimestamp).reversed())
              .collect(Collectors.toList());
    } else {
      //按照项目名称排序
      newArray = allProjects.stream().sorted(
                      Comparator.comparing(Project::getName, String.CASE_INSENSITIVE_ORDER))
              .collect(Collectors.toList());
    }

    return newArray;
  }

  public List<Project> getUserProjectsByRegex(final User user, final String regexPattern,
                                              final String orderOption) {
    final List<Project> array = new ArrayList<>();
    final Pattern pattern;
    try {
      pattern = Pattern.compile(regexPattern, Pattern.CASE_INSENSITIVE);
    } catch (final PatternSyntaxException e) {
      logger.error("Bad regex pattern " + regexPattern);
      return array;
    }

    for (final Project project : getProjectsIDMap().values()) {
      final Permission perm = project.getUserPermission(user);

      if (perm != null
              && (perm.isPermissionSet(Type.ADMIN) || perm
              .isPermissionSet(Type.READ))) {
        if (pattern.matcher(project.getName()).find()
                || pattern.matcher(project.getDescription()).find()
                || pattern.matcher(project.getCreateUser()).find()) {
          array.add(project);
        }
      }
    }
//    //按照项目名称排序
//    List<Project> newArray = array.stream().sorted(
//        Comparator.comparing(Project::getName, String.CASE_INSENSITIVE_ORDER)).collect(Collectors.toList());

    //按照项目名称排序
    List<Project> newArray = new ArrayList<>();

    if ("orderUpdateTimeSort".equals(orderOption)) {
      //修改时间排序 从大到小
      newArray = array.stream().sorted(
                      Comparator.comparing(Project::getLastModifiedTimestamp).reversed())
              .collect(Collectors.toList());
    } else {
      //按照项目名称排序
      newArray = array.stream().sorted(
                      Comparator.comparing(Project::getName, String.CASE_INSENSITIVE_ORDER))
              .collect(Collectors.toList());
    }

    return newArray;
  }

  public List<Project> getMaintainedProjectsByRegex(User user, List<Integer> projectIds, String regexPattern, String orderOption, boolean active) {
    final List<Project> array = new ArrayList<>();
    final Pattern pattern;
    try {
      pattern = Pattern.compile(regexPattern, Pattern.CASE_INSENSITIVE);
    } catch (final PatternSyntaxException e) {
      logger.error("Bad regex pattern " + regexPattern);
      return array;
    }

    if (active) {
      for (final Project project : getProjectsIDMap().values()) {
        final Permission permission = project.getUserPermission(user);
        Predicate<Permission> hasPermission = perm -> perm != null && (perm.isPermissionSet(Type.ADMIN) || perm.isPermissionSet(Type.READ));
        Predicate<Project> isMaintained = proj -> projectIds.contains(proj.getId());
        if (isMaintained.test(project) || hasPermission.test(permission)) {
          if (pattern.matcher(project.getName()).find()
                  || pattern.matcher(project.getDescription()).find()
                  || pattern.matcher(project.getCreateUser()).find()) {
            array.add(project);
          }
        }
      }
    } else {
      for (final Project project : getInactiveProjectIDMap().values()) {
        final Permission permission = project.getUserPermission(user);
        Predicate<Permission> hasPermission = perm -> perm != null && perm.isPermissionSet(Type.ADMIN);
        Predicate<Project> isMaintained = proj -> projectIds.contains(proj.getId());
        if (isMaintained.test(project) || hasPermission.test(permission)) {
          if (pattern.matcher(project.getName()).find()
                  || pattern.matcher(project.getDescription()).find()
                  || pattern.matcher(project.getCreateUser()).find()) {
            array.add(project);
          }
        }
      }
    }


//    //按照项目名称排序
//    List<Project> newArray = array.stream().sorted(
//        Comparator.comparing(Project::getName, String.CASE_INSENSITIVE_ORDER)).collect(Collectors.toList());

    //按照项目名称排序
    List<Project> newArray = new ArrayList<>();

    if ("orderUpdateTimeSort".equals(orderOption)) {
      //修改时间排序 从大到小
      newArray = array.stream().sorted(
                      Comparator.comparing(Project::getLastModifiedTimestamp).reversed())
              .collect(Collectors.toList());
    } else {
      //按照项目名称排序
      newArray = array.stream().sorted(
                      Comparator.comparing(Project::getName, String.CASE_INSENSITIVE_ORDER))
              .collect(Collectors.toList());
    }

    return newArray;
  }

  /**
   * 获取当天创建的项目 但是未启动执行的工作流集合
   *
   * @return
   * @throws ProjectManagerException
   */
  public List<Flow> getTodayCreateProjectNoRunNum(final String username) throws ProjectManagerException {
    List<Flow> todayCreateFlowNoRunList = new ArrayList<>();

    List<Project> todayCreateProjectList = this.projectLoader.getTodayCreateProjects(username);

    for (Project project : todayCreateProjectList) {
      List<Flow> flows = this.getProject(project.getId()).getFlows();
      for (Flow flow : flows) {
        int todayRunCount = this.projectLoader.getTodayRunFlow(project.getId(), flow.getId());
        if (todayRunCount == 0) {
          todayCreateFlowNoRunList.add(flow);
        }
      }
    }

    return todayCreateFlowNoRunList;
  }

  /**
   * 获取当天创建的项目 但是未启动执行的工作流集合
   *
   * @return
   * @throws ProjectManagerException
   */
  public List<Map> getTodayCreateProjectNoRunFlowInfo(final String username) throws ProjectManagerException {
    List<Map> todayCreateFlowNoRunFlowList = new ArrayList<>();

    List<Project> todayCreateProjectList = this.projectLoader.getTodayCreateProjects(username);

    for (Project project : todayCreateProjectList) {
      List<Flow> flows = this.getProject(project.getId()).getFlows();
      for (Flow flow : flows) {
        int todayRunCount = this.projectLoader.getTodayRunFlow(project.getId(), flow.getId());
        if (todayRunCount == 0) {
          Map<String, String> flowInfo = new HashMap<>();
          flowInfo.put("ProjectName", project.getName());
          flowInfo.put("flowName", flow.getId());
          flowInfo.put("projectUser", project.getLastModifiedUser());
          todayCreateFlowNoRunFlowList.add(flowInfo);
        }
      }
    }

    return todayCreateFlowNoRunFlowList;
  }

  public List<Project> getProjects(User user, String order) {
    //1、system admin
    List<Project> projects;
    if (user.getRoles().contains("admin")) {
      projects = this.getProjects(order, false);
      //2、maintainer
    } else if (systemManager.isDepartmentMaintainer(user)) {
      List<Integer> maintainedProjectIds = systemManager.getMaintainedProjects(user, 0);
      projects = this.getMaintainedProjects(user, maintainedProjectIds, order, false);
      //3、personal
    } else {//user用户只能查看自己有权限的项目
      projects = this.getUserAllProjects(user, order, false);
    }
    return projects;
  }

  public List<Project> handleProjectDeleteSearch(String searchTerm, String order, User user) {
    List<Project> searchProjects;
    //添加权限判断 admin 用户能查看所有Project
    if (user.getRoles().contains("admin")) {
      searchProjects = this.getProjectsByRegex(searchTerm, order, false);
      // 运维管理员可以查看该运维管理员运维部门下的所有工程
    } else if (systemManager.isDepartmentMaintainer(user)) {
      final List<Integer> maintainedProjectIds = systemManager.getMaintainedProjects(user, 0);
      searchProjects = this.getMaintainedProjectsByRegex(user, maintainedProjectIds, searchTerm, order, false);
    } else {//user用户只能查看自己的Project
      searchProjects = this.getUserProjectsByRegex(user, searchTerm, order);
    }
    return searchProjects;
  }

  public int countDeleteProjects(String username, String search) {
    return this.projectLoader.getInactiveProjectsTotalNumber(username, search);
  }

  public void deleteInactiveProject(int projectId, HashMap<String, Object> ret) {
    this.projectLoader.deleteInactiveProject(projectId);
    this.inactiveProject.remove(projectId);
    this.flowCache.invalidate(projectId);
    if (this.isHaModel) {
      HttpUtils.reloadWebData(this.getProps().getStringList("azkaban.all.web.url"), "deleteProject",
              projectId + "");
    }

  }

  public void deleteInactiveProjectByTime(long interval) {
    try {
      for (Project project : getInactiveProjectIDMap().values()) {
        if (project.getLastModifiedTimestamp() < interval) {
          logger.debug("delete project:{}", project.getId());
          this.inactiveProject.remove(project.getId());
          this.flowCache.invalidate(project.getId());
          if (this.isHaModel) {
            HttpUtils
                    .reloadWebData(this.getProps().getStringList("azkaban.all.web.url"), "deleteProject",
                            project.getId() + "");
          }

        }
      }
      projectLoader.deleteHistoricalProject(interval);
    } catch (Exception e) {
      logger.error("delete project failed.", e);
    }
  }

  /**
   * 新增/更新工作流应用信息
   *
   * @param flowBusiness
   */
  public int mergeFlowBusiness(FlowBusiness flowBusiness) {
    return this.projectLoader.mergeFlowBusiness(flowBusiness);
  }

  public int mergeProjectInfo(FlowBusiness flowBusiness) throws SQLException {
    return this.projectLoader.mergeProjectInfo(flowBusiness);
  }

  /**
   * 查询工作流应用信息
   *
   * @param projectId
   * @return
   */
  public FlowBusiness getFlowBusiness(int projectId, String flowId, String jobId) {
    return this.projectLoader.getFlowBusiness(projectId, flowId, jobId);
  }

  private Map<String, Flow> getFlowsFromDB(Integer projectId) {
    Project fetchedProject = getProjectsIDMap().get(projectId);
    if (null == fetchedProject) {
      fetchedProject = getInactiveProjectIDMap().get(projectId);
    }
    if (fetchedProject == null) {
      try {
        fetchedProject = this.projectLoader.fetchProjectById(projectId);
      } catch (final ProjectManagerException e) {
        logger.error("Could not load project from store.", e);
      }
    }

    if (null == fetchedProject) {
      throw new RuntimeException("Could not load project flows from store, for project not exists: " + projectId);
    }

    try {
      final List<Flow> flows = this.projectLoader.fetchAllProjectFlows(fetchedProject);
      final Map<String, Flow> flowMap = new HashMap<>();
      for (final Flow flow : flows) {
        flowMap.put(flow.getId(), flow);
      }
      return flowMap;
    } catch (final ProjectManagerException e) {
      throw new RuntimeException("Could not load project flows from store. project: " + projectId, e);
    }
  }

  public List<Flow> getFlowsByProject(Project project) {
    List<Flow> retFlow = project.getFlows();
    if (enableFlowCache) {
      Map<String, Flow> flows = null;
      try {
        flows = this.flowCache.get(project.getId());
      } catch (ExecutionException e) {
        logger.error("Could not load project {} flow info ", project.getName(), e);
      }
      if (flows != null) {
        retFlow = new ArrayList<>(flows.values());
      } else {
        retFlow = new ArrayList<>();
      }
    }
    if (null == retFlow) {
      retFlow = new ArrayList<>();
    }
    return retFlow;
  }

  public void deleteFlowBusiness(int projectId, String flowId, String jobId) {
    this.projectLoader.deleteFlowBusiness(projectId, flowId, jobId);
  }

  public void refreshProjectPermission(String projectName) {
    final Project project = this.getProject(projectName);
    if (project != null) {
      List<ProjectPermission> projectPermissionList = this.projectLoader.fetchAllPermissionsForProject(project);
      if (CollectionUtils.isNotEmpty(projectPermissionList)) {
        project.clearUserPermission();
        project.clearGroupPermission();
        for (ProjectPermission projectPermission : projectPermissionList) {
          if (projectPermission.getIsGroup()) {
            project.setGroupPermission(projectPermission.getProjectGroup(), projectPermission.getPermission());
          }
          project.setUserPermission(projectPermission.getUsername(), projectPermission.getPermission());
        }
      }
    }
  }

  public ProjectPermission getProjectPermission(Project project,String userName) {
    if (project != null) {
      ProjectPermission projectPermission = this.projectLoader.getProjectPermission(project.getId() + "", userName);
      return projectPermission;
    }
    return null;
  }

  public void reloadProject(String projectName) {
    Project project = this.projectLoader.fetchProjectByName(projectName);
    Project pro = this.projectsByName.get(project.getName());
    if (pro != null) {
      if (pro.getVersion() != project.getVersion()) {
        loadAllProjectFlows(project);
      } else {
        project.setFlows(pro.getFlowMap());
      }
    } else {
      loadAllProjectFlows(project);
    }
    this.projectsByName.remove(project.getName());
    this.projectsById.remove(project.getId());
    this.flowCache.invalidate(project.getId());
    if (project.isActive()) {
      this.projectsByName.put(project.getName(), project);
      this.projectsById.put(project.getId(), project);
      this.flowCache.invalidate(project.getId());
    } else {
      this.inactiveProject.put(project.getId(), project);
      this.flowCache.invalidate(project.getId());
    }
  }

  public void deleteProjectByWeb(int projectId) {
    this.inactiveProject.remove(projectId);
    this.flowCache.invalidate(projectId);
  }

  public int updateProjectChangeOwnerInfo(long itsmNo, Project project, String newOwner, User user)
          throws SQLException {
    return this.projectLoader.updateProjectChangeOwnerInfo(itsmNo, project, newOwner, user);
  }

  public List<Project> getProjects(String projContain, String flowContain, String description, String userContain, String subsystem, String busPath,
                                   String departmentId, String orderOption, Boolean active, List<Integer> maintainedProjectIds, User user) {
    Set<String> userRoleSet = new HashSet<>();
    userRoleSet.addAll(user.getRoles());
    List<Project> array = new ArrayList<>();
    List<Project> projectList;
    if (active) {
      projectList = projectLoader.preciseSearchFetchProjects(projContain, flowContain, description, userContain, subsystem, busPath, departmentId, 1);
    } else {
      projectList = projectLoader.preciseSearchFetchProjects(projContain, flowContain, description, userContain, subsystem, busPath, departmentId, 0);
    }
    if (CollectionUtils.isNotEmpty(maintainedProjectIds)) {
      for (final Project project : projectList) {
        final Permission permission = project.getUserPermission(user);
        Predicate<Permission> hasPermission = perm -> perm != null && (perm.isPermissionSet(Type.ADMIN) || perm.isPermissionSet(Type.READ));
        Predicate<Project> isMaintained = proj -> maintainedProjectIds.contains(proj.getId());
        if (isMaintained.test(project) || hasPermission.test(permission)) {
          array.add(project);
        }
      }
    } else {
      array.addAll(projectList);
    }
//    //按照项目名称排序
//    List<Project> newArray = projectList.stream().sorted(
//        Comparator.comparing(Project::getName, String.CASE_INSENSITIVE_ORDER)).collect(Collectors.toList());

    //按照项目名称排序
    List<Project> newArray;
    if ("orderUpdateTimeSort".equals(orderOption)) {
      //修改时间排序 从大到小
      newArray = array.stream().sorted(
                      Comparator.comparing(Project::getLastModifiedTimestamp).reversed())
              .collect(Collectors.toList());
    } else {
      //按照项目名称排序
      newArray = array.stream().sorted(
                      Comparator.comparing(Project::getName, String.CASE_INSENSITIVE_ORDER))
              .collect(Collectors.toList());
    }
    return newArray;

  }

  public List<Project> getUserProjects(String projContain, String flowContain, String description, String userContain, String subsystem, String busPath,
                                       String departmentId, String orderOption, User user) {
    List<Project> array = new ArrayList<>();
    List<Project> projectList;
    projectList = projectLoader.preciseSearchFetchProjects(projContain, flowContain, description, userContain, subsystem, busPath, departmentId, 1);
    for (final Project project : projectList) {
      final Permission perm = project.getUserPermission(user);
      //有项目权限 并且是项目创建人
      if (perm != null && project.getCreateUser().equals(user.getUserId())) {
        array.add(project);
      }
    }
//    //按照项目名称排序
//    List<Project> newArray = projectList.stream().sorted(
//        Comparator.comparing(Project::getName, String.CASE_INSENSITIVE_ORDER)).collect(Collectors.toList());

    //按照项目名称排序
    List<Project> newArray;
    if ("orderUpdateTimeSort".equals(orderOption)) {
      //修改时间排序 从大到小
      newArray = array.stream().sorted(
                      Comparator.comparing(Project::getLastModifiedTimestamp).reversed())
              .collect(Collectors.toList());
    } else {
      //按照项目名称排序
      newArray = array.stream().sorted(
                      Comparator.comparing(Project::getName, String.CASE_INSENSITIVE_ORDER))
              .collect(Collectors.toList());
    }

    return newArray;

  }

  public ProjectChangeOwnerInfo getProjectChangeOwnerInfo(Project project) throws SQLException {

    return this.projectLoader.getProjectChangeOwnerInfo(project);
  }

  public int updateProjectChangeOwnerStatus(Project project, int status) throws SQLException {
    return this.projectLoader.updateProjectChangeOwnerStatus(project, status);
  }

  public List<String> getProjectIdsAndFlowIds(String subsystem, String busPath) {
    return this.projectLoader.getProjectIdsAndFlowIds(subsystem, busPath);
  }

  public List<String> getProjectIds(String subsystem, String busPath) {
    return this.projectLoader.getProjectIds(subsystem, busPath);
  }

  public List<Project> getProjectsPreciseSearch(String projContain, String description, String userContain, String subsystem, String busPath, String departmentId,
                                                String orderOption, Boolean active) {
    final List<Project> allProjects = new ArrayList<>();
    List<String> projectIds = this.getProjectIds(subsystem, busPath);
    List<WtssUser> wtssUserList = new ArrayList<>();
    if (StringUtils.isNotEmpty(departmentId)) {
      wtssUserList = this.systemManager.getSystemUserByDepartmentId(Integer.parseInt(departmentId));
    }
    List<String> usernameList = wtssUserList.stream().map(WtssUser::getUsername).collect(Collectors.toList());
    for (final Project project : getProjects(active)) {
      boolean proResult = StringUtils.isEmpty(projContain) || project.getName().equals(projContain);
      boolean flowResult = StringUtils.isEmpty(description) || project.getDescription().equals(description);
      boolean userResult = StringUtils.isEmpty(userContain) || project.getCreateUser().equals(userContain);
      boolean subsystemResult = StringUtils.isEmpty(subsystem) || projectIds.contains(project.getId() + "");
      boolean busPathResult = StringUtils.isEmpty(busPath) || projectIds.contains(project.getId() + "");
      boolean departmentIdResult = StringUtils.isEmpty(departmentId) || usernameList.contains(project.getCreateUser());

      if (proResult && flowResult && userResult && subsystemResult && busPathResult && departmentIdResult) {
        allProjects.add(project);
      }
    }
//    //按照项目名称排序
//    List<Project> newArray = allProjects.stream().sorted(
//        Comparator.comparing(Project::getName, String.CASE_INSENSITIVE_ORDER)).collect(Collectors.toList());

    //按照项目名称排序
    List<Project> newArray = new ArrayList<>();

    if ("orderUpdateTimeSort".equals(orderOption)) {
      //修改时间排序 从大到小
      newArray = allProjects.stream().sorted(
                      Comparator.comparing(Project::getLastModifiedTimestamp).reversed())
              .collect(Collectors.toList());
    } else {
      //按照项目名称排序
      newArray = allProjects.stream().sorted(
                      Comparator.comparing(Project::getName, String.CASE_INSENSITIVE_ORDER))
              .collect(Collectors.toList());
    }

    return newArray;
  }

  public List<Project> getMaintainerProjects(String projContain, String description, String userContain, String subsystem, String busPath, String departmentId,
                                             String orderOption, Boolean active, List<Integer> maintainedProjectIds, User user) {
    final List<Project> array = new ArrayList<>();
    List<String> projectIds = this.getProjectIds(subsystem, busPath);
    List<WtssUser> wtssUserList = new ArrayList<>();
    if (StringUtils.isNotEmpty(departmentId)) {
      wtssUserList = this.systemManager.getSystemUserByDepartmentId(Integer.parseInt(departmentId));
    }
    List<String> usernameList = wtssUserList.stream().map(WtssUser::getUsername).collect(Collectors.toList());

    if (active) {
      for (final Project project : getProjectsIDMap().values()) {
        final Permission permission = project.getUserPermission(user);
        Predicate<Permission> hasPermission = perm -> perm != null && (perm.isPermissionSet(Type.ADMIN) || perm.isPermissionSet(Type.READ));
        Predicate<Project> isMaintained = proj -> maintainedProjectIds.contains(proj.getId());
        if (isMaintained.test(project) || hasPermission.test(permission)) {
          boolean proResult = StringUtils.isEmpty(projContain) || project.getName().equals(projContain);
          boolean flowResult = StringUtils.isEmpty(description) || project.getDescription().equals(description);
          boolean userResult = StringUtils.isEmpty(userContain) || project.getCreateUser().equals(userContain);
          boolean subsystemResult = StringUtils.isEmpty(subsystem) || projectIds.contains(project.getId() + "");
          boolean busPathResult = StringUtils.isEmpty(busPath) || projectIds.contains(project.getId() + "");
          boolean departmentIdResult = StringUtils.isEmpty(departmentId) || usernameList.contains(project.getCreateUser());

          if (proResult && flowResult && userResult && subsystemResult && busPathResult && departmentIdResult) {
            array.add(project);
          }
        }
      }
    } else {
      for (final Project project : getInactiveProjectIDMap().values()) {
        final Permission permission = project.getUserPermission(user);
        Predicate<Permission> hasPermission = perm -> perm != null && perm.isPermissionSet(Type.ADMIN);
        Predicate<Project> isMaintained = proj -> maintainedProjectIds.contains(proj.getId());
        if (isMaintained.test(project) || hasPermission.test(permission)) {
          if (isMaintained.test(project) || hasPermission.test(permission)) {
            boolean proResult = StringUtils.isEmpty(projContain) || project.getName().equals(projContain);
            boolean flowResult = StringUtils.isEmpty(description) || project.getDescription().equals(description);
            boolean userResult = StringUtils.isEmpty(userContain) || project.getCreateUser().equals(userContain);
            boolean subsystemResult = StringUtils.isEmpty(subsystem) || projectIds.contains(project.getId() + "");
            boolean busPathResult = StringUtils.isEmpty(busPath) || projectIds.contains(project.getId() + "");
            boolean departmentIdResult = StringUtils.isEmpty(departmentId) || usernameList.contains(project.getCreateUser());

            if (proResult && flowResult && userResult && subsystemResult && busPathResult && departmentIdResult) {
              array.add(project);
            }
          }
        }
      }
    }


//    //按照项目名称排序
//    List<Project> newArray = array.stream().sorted(
//        Comparator.comparing(Project::getName, String.CASE_INSENSITIVE_ORDER)).collect(Collectors.toList());

    //按照项目名称排序
    List<Project> newArray = new ArrayList<>();

    if ("orderUpdateTimeSort".equals(orderOption)) {
      //修改时间排序 从大到小
      newArray = array.stream().sorted(
                      Comparator.comparing(Project::getLastModifiedTimestamp).reversed())
              .collect(Collectors.toList());
    } else {
      //按照项目名称排序
      newArray = array.stream().sorted(
                      Comparator.comparing(Project::getName, String.CASE_INSENSITIVE_ORDER))
              .collect(Collectors.toList());
    }

    return newArray;
  }

  public List<Project> getUserProjects(HttpServletRequest req, boolean active, User user, boolean isAll)
          throws ServletException {
    List<Project> array = new ArrayList<>();
    final String projContain = HttpRequestUtils.getParam(req, "projcontain").trim();
    final String description = HttpRequestUtils.getParam(req, "description").trim();
    final String userContain = HttpRequestUtils.getParam(req, "usercontain").trim();
    final String subsystem = HttpRequestUtils.getParam(req, "subsystem");
    final String busPath = HttpRequestUtils.getParam(req, "busPath");
    final String departmentId = HttpRequestUtils.getParam(req, "departmentId");
    final String orderOption = HttpRequestUtils.getParam(req, "order");
    final String jobName = HttpRequestUtils.getParam(req, "jobName");
    final String fromType = HttpRequestUtils.getParam(req, "fromType");
    List<String> projectIds = this.getProjectIds(subsystem, busPath);
    List<WtssUser> wtssUserList = new ArrayList<>();
    if (StringUtils.isNotEmpty(departmentId)) {
      wtssUserList = this.systemManager.getSystemUserByDepartmentId(Integer.parseInt(departmentId));
    }
    List<String> usernameList = wtssUserList.stream().map(WtssUser::getUsername).collect(Collectors.toList());

    for (final Project project : active ? getProjectsIDMap().values() : getInactiveProjectIDMap().values()) {
      final Permission perm = project.getUserPermission(user);
      boolean permFlag =
              isAll ? (perm != null && (perm.isPermissionSet(Type.ADMIN) || perm.isPermissionSet(Type.READ)))
                      : project.getCreateUser().equals(user.getUserId());
      if (permFlag) {
        boolean proResult = StringUtils.isEmpty(projContain) || project.getName().equals(projContain);
        boolean flowResult = StringUtils.isEmpty(description) || project.getDescription().equals(description);
        boolean userResult = StringUtils.isEmpty(userContain) || project.getCreateUser().equals(userContain);
        boolean subsystemResult = StringUtils.isEmpty(subsystem) || projectIds.contains(project.getId() + "");
        boolean busPathResult = StringUtils.isEmpty(busPath) || projectIds.contains(project.getId() + "");
        boolean departmentIdResult = StringUtils.isEmpty(departmentId) || usernameList.contains(project.getCreateUser());

        if (proResult && flowResult && userResult && subsystemResult && busPathResult && departmentIdResult) {
          array.add(project);
        }
      }
    }

    if (StringUtils.isNotEmpty(jobName)) {
      array = array.stream().filter(project -> project.getFlows().stream().anyMatch(
              flow -> flow.getNodes().stream().anyMatch(
                      node -> node != null && !node.getType().equals(SpecialJobTypes.EMBEDDED_FLOW_TYPE)
                              && node.getId().contains(jobName)))).collect(Collectors.toList());
    }

    // 项目来源 WTSS/DSS
    if (StringUtils.isNotEmpty(fromType)) {
      List<String> fromTypes = Arrays.asList(fromType.split(","));
      array = array.stream().filter(project -> fromTypes.contains(project.getFromType())).collect(Collectors.toList());
    }

    //按照项目名称排序
    List<Project> newArray = new ArrayList<>();

    if ("orderUpdateTimeSort".equals(orderOption)) {
      //修改时间排序 从大到小
      newArray = array.stream().sorted(
                      Comparator.comparing(Project::getLastModifiedTimestamp).reversed())
              .collect(Collectors.toList());
    } else {
      //按照项目名称排序
      newArray = array.stream().sorted(
                      Comparator.comparing(Project::getName, String.CASE_INSENSITIVE_ORDER))
              .collect(Collectors.toList());
    }
    return newArray;
  }

  public long getProjectFileSize(String projectIds) {
    return this.projectLoader.getProjectFileSize(projectIds);
  }

  public Flow getProjectFlow(int projectId, int version, String flowId) throws ProjectManagerException {
    final Flow flow = this.projectLoader.fetchAllProjectFlows(projectId, version, flowId);
    return flow;
  }

}
