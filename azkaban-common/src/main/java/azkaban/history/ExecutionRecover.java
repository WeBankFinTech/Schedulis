package azkaban.history;

import azkaban.executor.ExecutionOptions;
import azkaban.executor.Status;
import azkaban.sla.SlaOption;
import azkaban.utils.TypedMapWrapper;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * Created by zhu on 12/11/17.
 */
public class ExecutionRecover {


  public static final String RECOVER_ID_PARAM = "recoverId";
  public static final String RECOVER_STATUS_PARAM = "recoverStatus";
  public static final String RECOVER_START_TIME_PARAM = "recoverStartTime";
  public static final String RECOVER_END_TIME_PARAM = "recoverEndTime";
  public static final String EX_INTERVAL_PARAM = "exInterval";
  public static final String NOW_EXECUTION_ID_PARAM = "nowExecutionId";
  public static final String PROJECT_ID_PARAM = "projectId";
  public static final String FLOW_ID_PARAM = "flowId";
  public static final String SUBMIT_USER_PARAM = "submitUser";
  public static final String SUBMIT_TIME_PARAM = "submitTime";
  public static final String UPDATE_TIME_PARAM = "updateTime";
  public static final String START_TIME_PARAM = "startTime";
  public static final String END_TIME_PARAM = "endTime";
  public static final String REPEATOPTIONS_PARAM = "repeatOptions";
  public static final String PROXY_USER_PARAM = "proxyUsers";
  public static final String EXECUTION_OPTIONS_PARAM = "executionOptions";
  public static final String OTHEROPTIONS_PARAM = "otherOptions";
  public static final String SLAOPTIONS_PARAM = "slaOptions";

  public static final String LAST_EXEC_ID = "lastExecId";

  private int recoverId = -1;
  private Status recoverStatus = Status.READY;
  private long recoverStartTime = -1;
  private long recoverEndTime = -1;
  private String exInterval;
  private int nowExecutionId = -1;
  private int projectId;
  private String flowId;
  private String submitUser;
  private long submitTime = -1;
  private long updateTime = -1;
  private long startTime = -1;
  private long endTime = -1;
  private Map<String, Object> repeatOption = new HashMap<>();
  private String proxyUsers;
  private ExecutionOptions executionOptions;

  private String recoverErrorOption;

  private int taskSize = 1;

  //其他设置参数，方便后续扩展使用
  private Map<String, Object> otherOption = new HashMap<>();
  //超时告警
  private List<SlaOption> slaOptions = new ArrayList<>();

  private String projectName;

  private boolean finishedAlert = true;

  // 判断是否是准备执行提交的
  private int lastExecId = -1;

//  public ExecutionRecover(final Project project, final Flow flow) {
//    this.projectId = project.getId();
//    this.projectName = project.getName();
//    this.version = project.getVersion();
//    this.scheduleId = -1;
//    this.lastModifiedTimestamp = project.getLastModifiedTimestamp();
//    this.lastModifiedUser = project.getLastModifiedUser();
//    this.setFlow(project, flow);
//  }

  public ExecutionRecover() {
  }

  public static ExecutionRecover createExecutionRecoverFromObject(final Object obj) {
    final ExecutionRecover executionRecover = new ExecutionRecover();
    final HashMap<String, Object> recoverObj = (HashMap<String, Object>) obj;
    executionRecover.fillExecutableFromMapObject(recoverObj);

    return executionRecover;
  }


  public int getTaskSize() {
    return taskSize;
  }

  public void setTaskSize(int taskSize) {
    this.taskSize = taskSize;
  }

  public List<SlaOption> getSlaOptions() {
    return slaOptions;
  }

  public void setSlaOptions(List<SlaOption> slaOptions) {
    this.slaOptions = slaOptions;
  }

  public int getRecoverId() {
    return recoverId;
  }

  public void setRecoverId(int recoverId) {
    this.recoverId = recoverId;
  }

  public Status getRecoverStatus() {
    return recoverStatus;
  }

  public void setRecoverStatus(Status recoverStatus) {
    this.recoverStatus = recoverStatus;
  }

  public String getProjectName() {
    return projectName;
  }

  public void setProjectName(String projectName) {
    this.projectName = projectName;
  }

  public boolean isFinishedAlert() {
    return finishedAlert;
  }

  public void setFinishedAlert(boolean finishedAlert) {
    this.finishedAlert = finishedAlert;
  }

  public long getRecoverStartTime() {
    return recoverStartTime;
  }

  public void setRecoverStartTime(long recoverStartTime) {
    this.recoverStartTime = recoverStartTime;
  }

  public long getRecoverEndTime() {
    return recoverEndTime;
  }

  public void setRecoverEndTime(long recoverEndTime) {
    this.recoverEndTime = recoverEndTime;
  }

  public String getExInterval() {
    return exInterval;
  }

  public void setExInterval(String exInterval) {
    this.exInterval = exInterval;
  }

  public int getNowExecutionId() {
    return nowExecutionId;
  }

  public void setNowExecutionId(int nowExecutionId) {
    this.nowExecutionId = nowExecutionId;
  }

  public int getProjectId() {
    return projectId;
  }

  public void setProjectId(int projectId) {
    this.projectId = projectId;
  }

  public String getFlowId() {
    return flowId;
  }

  public void setFlowId(String flowId) {
    this.flowId = flowId;
  }

  public String getSubmitUser() {
    return submitUser;
  }

  public void setSubmitUser(String submitUser) {
    this.submitUser = submitUser;
  }

  public long getSubmitTime() {
    return submitTime;
  }

  public void setSubmitTime(long submitTime) {
    this.submitTime = submitTime;
  }

  public long getUpdateTime() {
    return updateTime;
  }

  public void setUpdateTime(long updateTime) {
    this.updateTime = updateTime;
  }

  public long getStartTime() {
    return startTime;
  }

  public void setStartTime(long startTime) {
    this.startTime = startTime;
  }

  public long getEndTime() {
    return endTime;
  }

  public void setEndTime(long endTime) {
    this.endTime = endTime;
  }

  public Map<String, Object> getRepeatOption() {
    return repeatOption;
  }

  public void setRepeatOption(Map<String, Object> repeatOption) {
    this.repeatOption = repeatOption;
  }

  public String getProxyUsers() {
    return proxyUsers;
  }

  public void setProxyUsers(String proxyUsers) {
    this.proxyUsers = proxyUsers;
  }

  public ExecutionOptions getExecutionOptions() {
    return executionOptions;
  }

  public Map<String, Object> getOtherOption() {
    return otherOption;
  }

  public void setOtherOption(Map<String, Object> otherOption) {
    this.otherOption = otherOption;
  }

  public void setExecutionOptions(ExecutionOptions executionOptions) {
    this.executionOptions = executionOptions;
  }

  public Map<String, Object> toObject() {
    final HashMap<String, Object> recoverObj = new HashMap<>();

    recoverObj.put(RECOVER_ID_PARAM, this.recoverId);
    recoverObj.put(RECOVER_STATUS_PARAM, this.recoverStatus.toString());
    recoverObj.put(RECOVER_START_TIME_PARAM, this.recoverStartTime);
    recoverObj.put(RECOVER_END_TIME_PARAM, this.recoverEndTime);
    recoverObj.put(EX_INTERVAL_PARAM, this.exInterval);

    recoverObj.put(NOW_EXECUTION_ID_PARAM, this.nowExecutionId);
    recoverObj.put(PROJECT_ID_PARAM, this.projectId);
    recoverObj.put(FLOW_ID_PARAM, this.flowId);

    recoverObj.put(SUBMIT_USER_PARAM, this.submitUser);
    recoverObj.put(SUBMIT_TIME_PARAM, this.submitTime);
    recoverObj.put(UPDATE_TIME_PARAM, this.updateTime);
    recoverObj.put(START_TIME_PARAM, this.startTime);
    recoverObj.put(END_TIME_PARAM, this.endTime);
    if(!Objects.equals(this.executionOptions, null)){
      recoverObj.put(EXECUTION_OPTIONS_PARAM, this.executionOptions.toObject());
    }

    final Map<String, Object> otherOption = this.getOtherOption();
    recoverObj.put(OTHEROPTIONS_PARAM, otherOption);

    //超时告警
    final List<Map<String, Object>> slaOptions = new ArrayList<>();
    this.getSlaOptions().stream().forEach((slaOption) -> slaOptions.add(slaOption.toObject()));
    recoverObj.put(SLAOPTIONS_PARAM, slaOptions);

    //把数据补采参数放入到 repeatOption Map 中
    final Map<String, Object> repeatOption = this.getRepeatOption();
    recoverObj.put(REPEATOPTIONS_PARAM, repeatOption);

    recoverObj.put(PROXY_USER_PARAM, this.proxyUsers);

    recoverObj.put("recoverErrorOption", this.recoverErrorOption);

    recoverObj.put("taskSize", this.taskSize);
    recoverObj.put("finishedAlert", this.finishedAlert);
    recoverObj.put(LAST_EXEC_ID, this.lastExecId);
    return recoverObj;
  }

  public void fillExecutableFromMapObject(final Map<String, Object> objMap) {
    final TypedMapWrapper<String, Object> wrapper =
        new TypedMapWrapper<>(objMap);
    fillExecutableFromMapObject(wrapper);
  }

  public void fillExecutableFromMapObject(final TypedMapWrapper<String, Object> recoverObj) {

    this.recoverId = recoverObj.getInt(RECOVER_ID_PARAM);
    this.recoverStatus = Status.valueOf(recoverObj.getString(RECOVER_STATUS_PARAM));
    this.recoverStartTime = recoverObj.getLong(RECOVER_START_TIME_PARAM);
    this.recoverEndTime = recoverObj.getLong(RECOVER_END_TIME_PARAM);
    this.exInterval = recoverObj.getString(EX_INTERVAL_PARAM);

    this.nowExecutionId = recoverObj.getInt(NOW_EXECUTION_ID_PARAM);
    this.projectId = recoverObj.getInt(PROJECT_ID_PARAM);
    this.flowId = recoverObj.getString(FLOW_ID_PARAM);

    this.submitUser = recoverObj.getString(SUBMIT_USER_PARAM);
    this.submitTime = recoverObj.getLong(SUBMIT_TIME_PARAM);
    this.updateTime = recoverObj.getLong(UPDATE_TIME_PARAM);
    this.startTime = recoverObj.getLong(START_TIME_PARAM);
    this.endTime = recoverObj.getLong(END_TIME_PARAM);

    //设置其他数据参数
    if(recoverObj.containsKey(OTHEROPTIONS_PARAM)){
      final Map<String, Object> otherOptions = recoverObj.getMap(OTHEROPTIONS_PARAM);
      this.setOtherOption(otherOptions);
    }

    // 设置数据补采参数
    if(recoverObj.containsKey(REPEATOPTIONS_PARAM)){
      final Map<String, Object> repeatOption = recoverObj.getMap(REPEATOPTIONS_PARAM);

      this.setRepeatOption(repeatOption);
    }
    //超时告警
    if (recoverObj.containsKey(SLAOPTIONS_PARAM)) {
      final List<SlaOption> slaOptions =
          recoverObj.getList(SLAOPTIONS_PARAM).stream().map(SlaOption::fromObject)
              .collect(Collectors.toList());
      this.setSlaOptions(slaOptions);
    }

    this.proxyUsers = recoverObj.getString(PROXY_USER_PARAM);

    this.executionOptions =  ExecutionOptions.createFromObject(recoverObj
        .getObject(EXECUTION_OPTIONS_PARAM));

    this.recoverErrorOption = recoverObj.getString("recoverErrorOption");
    this.taskSize = recoverObj.getInt("taskSize", 1);
    this.finishedAlert = recoverObj.getBool("finishedAlert", true);
    this.lastExecId = recoverObj.getInt(LAST_EXEC_ID, -1);
  }

  public int getLastExecId() {
    return lastExecId;
  }

  public void setLastExecId(int lastExecId) {
    this.lastExecId = lastExecId;
  }

  //  public Map<String, Object> toUpdateObject(final long lastUpdateTime) {
//    final Map<String, Object> updateData = toUpdateObject(lastUpdateTime);
//    updateData.put(EXECUTIONID_PARAM, this.executionId);
//    return updateData;
//  }

//  public void resetForRetry() {
//    this.setStatus(Status.RUNNING);
//  }


  public String getRecoverErrorOption() {
    return recoverErrorOption;
  }

  public void setRecoverErrorOption(String recoverErrorOption) {
    this.recoverErrorOption = recoverErrorOption;
  }

}
