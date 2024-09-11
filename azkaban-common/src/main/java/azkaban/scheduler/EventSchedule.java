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

package azkaban.scheduler;

import azkaban.executor.ExecutionOptions;
import azkaban.sla.SlaOption;
import azkaban.utils.JSONUtils;
import azkaban.utils.Pair;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Singleton;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;


@Singleton
public class EventSchedule {

  private static final Logger logger = LoggerFactory.getLogger(EventSchedule.class);
  private final long submitTime;
  private String submitUser;
  private String source;

  private int scheduleId;
  private long lastModifyTime;
  private String status;
  //private Map<String, Object> info = new HashMap<>();
  //private Map<String, Object> context = new HashMap<>();
  //private boolean resetOnTrigger = true;
  //private boolean resetOnExpire = true;

  private String sender;
  private String topic;
  private String msgName;
  private String saveKey;
  private final int projectId;
  private final String projectName;
  private final String flowName;
  private ExecutionOptions executionOptions;
  private List<SlaOption> slaOptions;
  private String comment;
  private String token;
  private Map<String, Object> otherOption = new HashMap<>();


  private EventSchedule() throws ScheduleManagerException {
    throw new ScheduleManagerException("Schedules should always be specified");
  }

  public EventSchedule(final int scheduleId,
      final int projectId,
      final String projectName,
      final String flowName,
      final String status,
      final long lastModifyTime,
      final long submitTime,
      final String submitUser,
      final String sender,
      final String topic,
      final String msgName,
      final String saveKey,
      final ExecutionOptions executionOptions,
      final List<SlaOption> slaOptions
      ) {
    this.scheduleId = scheduleId;
    this.projectId = projectId;
    this.projectName = projectName;
    this.flowName = flowName;
    this.status = status;
    this.lastModifyTime = lastModifyTime;
    this.submitTime = submitTime;
    this.submitUser = submitUser;
    this.sender = sender;
    this.topic = topic;
    this.msgName = msgName;
    this.saveKey = saveKey;
    this.executionOptions = executionOptions;
    this.slaOptions = slaOptions;

  }

  public EventSchedule(final int scheduleId,
      final int projectId,
      final String projectName,
      final String flowName,
      final String status,
      final long lastModifyTime,
      final long submitTime,
      final String submitUser,
      final String sender,
      final String topic,
      final String msgName,
      final String saveKey,
      final ExecutionOptions executionOptions,
      final List<SlaOption> slaOptions,
      final Map<String, Object> otherOption
  ) {
    this.scheduleId = scheduleId;
    this.projectId = projectId;
    this.projectName = projectName;
    this.flowName = flowName;
    this.status = status;
    this.lastModifyTime = lastModifyTime;
    this.submitTime = submitTime;
    this.submitUser = submitUser;
    this.sender = sender;
    this.topic = topic;
    this.msgName = msgName;
    this.saveKey = saveKey;
    this.executionOptions = executionOptions;
    this.slaOptions = slaOptions;
    this.otherOption = otherOption;
  }

  public EventSchedule(final int scheduleId,
                       final int projectId,
                       final String projectName,
                       final String flowName,
                       final String status,
                       final long lastModifyTime,
                       final long submitTime,
                       final String submitUser,
                       final String sender,
                       final String topic,
                       final String msgName,
                       final String saveKey,
                       final ExecutionOptions executionOptions,
                       final List<SlaOption> slaOptions,
                       final Map<String, Object> otherOption,
                       final String comment,
                       final String token
  ) {
    this.scheduleId = scheduleId;
    this.projectId = projectId;
    this.projectName = projectName;
    this.flowName = flowName;
    this.status = status;
    this.lastModifyTime = lastModifyTime;
    this.submitTime = submitTime;
    this.submitUser = submitUser;
    this.sender = sender;
    this.topic = topic;
    this.msgName = msgName;
    this.saveKey = saveKey;
    this.executionOptions = executionOptions;
    this.slaOptions = slaOptions;
    this.otherOption = otherOption;
    this.comment = comment;
    this.token = token;
  }

  public int getProjectId() {
    return projectId;
  }

  public String getProjectName() {
    return projectName;
  }

  public String getFlowName() {
    return flowName;
  }

  public ExecutionOptions getExecutionOptions(){
    return this.executionOptions;
  }

  public List<SlaOption> getSlaOptions() {
    return this.slaOptions;
  }

  public void setSlaOptions(final List<SlaOption> slaOptions) {
    this.slaOptions = slaOptions;
  }

  public void setFlowOptions(final ExecutionOptions executionOptions) {
    this.executionOptions = executionOptions;
  }

  public String getEventScheduleName() {
    return this.projectName + "." + this.flowName + " (" + this.projectId + ")";
  }

  public Map<String, Object> getOtherOption(){
    return otherOption;
  }

  public void setOtherOption(Map<String, Object> otherOption) {
    this.otherOption = otherOption;
  }

  public long getSubmitTime() {
    return this.submitTime;
  }

  public String getSubmitUser() {
    return this.submitUser;
  }

  public void setSubmitUser(String submitUser) {
    this.submitUser = submitUser;
  }

  public String getStatus() {
    return this.status;
  }

  public void setStatus(final String status) {
    this.status = status;
  }

  public long getLastModifyTime() {
    return this.lastModifyTime;
  }

  public void setLastModifyTime(final long lastModifyTime) {
    this.lastModifyTime = lastModifyTime;
  }

  public int getScheduleId() {
    return this.scheduleId;
  }

  public void setScheduleId(final int id) {
    this.scheduleId = id;
  }

  public String getSender() {
    return sender;
  }

  public void setSender(String sender) {
    this.sender = sender;
  }

  public String getTopic() {
    return topic;
  }

  public void setTopic(String topic) {
    this.topic = topic;
  }

  public String getMsgName() {
    return msgName;
  }

  public void setMsgName(String msgName) {
    this.msgName = msgName;
  }

  public void setSource(String source) {
    this.source = source;
  }

  public String getSaveKey() {
    return saveKey;
  }

  public void setSaveKey(String saveKey) {
    this.saveKey = saveKey;
  }

  public String getComment() {
    return comment;
  }

  public void setComment(String comment) {
    this.comment = comment;
  }

  public String getToken() { return token; }

  public void setToken(String token) { this.token = token; }

  public void setExecutionOptions(ExecutionOptions executionOptions) {
    this.executionOptions = executionOptions;
  }

  public String getSource() {
    return this.source;
  }

  public String getDescription() {

    return "Event schedule from with sender of " + this.sender + " , topic of "
        + this.topic + " and with msgName of " + this.msgName;
  }

  public Pair<Integer, String> getScheduleIdentityPair() {
    return new Pair<>(getProjectId(), getFlowName());
  }

  public static EventSchedule fromJson(final Object obj) throws Exception {

    final Map<String, Object> jsonObj = (HashMap<String, Object>) obj;

    EventSchedule schedule = null;
    try {
      logger.debug("Decoding for " + JSONUtils.toJSON(obj));

      final String submitUser = (String) jsonObj.get("submitUser");
      //final String source = (String) jsonObj.get("source");
      final long submitTime = Long.valueOf((String) jsonObj.get("submitTime"));
      final long lastModifyTime = Long.valueOf((String) jsonObj.get("lastModifyTime"));
      final int scheduleId = Integer.valueOf((String) jsonObj.get("scheduleId"));
      final String status = (String) jsonObj.get("status");

      final String sender = (String) jsonObj.get("sender");
      final String topic = (String) jsonObj.get("topic");
      final String msgName = (String) jsonObj.get("msgName");
      final String saveKey = (String) jsonObj.get("saveKey");
      final int projectId = Integer.valueOf((String) jsonObj.get("projectId"));
      final String projectName = (String) jsonObj.get("projectName");
      final String flowName = (String) jsonObj.get("flowName");
      final String comment = (String) jsonObj.get("comment");
      final String token = (String) jsonObj.get("token");
      /*final ExecutionOptions executionOptions = (ExecutionOptions) jsonObj.get("executionOptions");
      final List<SlaOption> slaOptions = (List<SlaOption>) jsonObj.get("slaOptions");
      final Map<String, Object> otherOption = (Map<String, Object>) jsonObj.get("otherOption");*/
      ExecutionOptions executionOptions = null;
      if (jsonObj.containsKey("executionOptions")) {
        executionOptions = ExecutionOptions.createFromObject(jsonObj.get("executionOptions"));
      }
      List<SlaOption> slaOptions = null;
      if (jsonObj.containsKey("slaOptions")) {
        slaOptions = new ArrayList<>();
        final List<Object> slaOptionsObj = (List<Object>) jsonObj.get("slaOptions");
        for (final Object slaObj : slaOptionsObj) {
          slaOptions.add(SlaOption.fromObject(slaObj));
        }
      }
      //设置其他数据参数
      Map<String, Object> otherOptionsMap = new HashMap<>();
      if(jsonObj.containsKey("otherOptions")){
        otherOptionsMap = (Map<String, Object>) jsonObj.get("otherOptions");
      }

      schedule = new EventSchedule(scheduleId, projectId, projectName, flowName, status,
          lastModifyTime, submitTime, submitUser, sender, topic, msgName,
          saveKey, executionOptions, slaOptions, otherOptionsMap, comment, token);
    } catch (final Exception e) {
      e.printStackTrace();
      logger.error("Failed to decode the event schedule.", e);
      throw new Exception("Failed to decode the event schedule.", e);
    }

    return schedule;
  }

  public Map<String, Object> toJson() {
    final Map<String, Object> jsonObj = new HashMap<>();

    jsonObj.put("submitUser", this.submitUser);
    jsonObj.put("submitTime", String.valueOf(this.submitTime));
    jsonObj.put("lastModifyTime", String.valueOf(this.lastModifyTime));
    jsonObj.put("scheduleId", String.valueOf(this.scheduleId));
    jsonObj.put("status", this.status);
    jsonObj.put("sender", this.sender);
    jsonObj.put("topic", this.topic);
    jsonObj.put("msgName", this.msgName);
    jsonObj.put("saveKey", this.saveKey);
    jsonObj.put("projectId", String.valueOf(this.projectId));
    jsonObj.put("projectName", this.projectName);
    jsonObj.put("flowName", this.flowName);
    jsonObj.put("comment", this.comment);
    jsonObj.put("token", this.token);

    /*jsonObj.put("executionOptions", this.executionOptions.toString());
    jsonObj.put("slaOptions", this.slaOptions.toString());
    jsonObj.put("otherOption", this.otherOption);*/
    if (this.executionOptions != null) {
      jsonObj.put("executionOptions", this.executionOptions.toObject());
    }
    if (this.slaOptions != null) {
      final List<Object> slaOptionsObj = new ArrayList<>();
      for (final SlaOption sla : this.slaOptions) {
        slaOptionsObj.add(sla.toObject());
      }
      jsonObj.put("slaOptions", slaOptionsObj);
    }

    if(this.otherOption != null){
      jsonObj.put("otherOptions", this.otherOption);
    }

    return jsonObj;
  }


  @Override
  public String toString() {
    return this.projectName + "." + this.flowName + "(" + this.projectId + ") to be run with event"
        + "schedule, Schedule Description: " + getDescription();
  }

}
