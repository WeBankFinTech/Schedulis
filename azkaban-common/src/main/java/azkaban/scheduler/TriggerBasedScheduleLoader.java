/*
 * Copyright 2014 LinkedIn Corp.
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

import azkaban.Constants;
import azkaban.trigger.*;
import azkaban.trigger.builtin.BasicTimeChecker;
import azkaban.trigger.builtin.ExecuteFlowAction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class TriggerBasedScheduleLoader implements ScheduleLoader {

  private static final Logger logger = LoggerFactory.getLogger(TriggerBasedScheduleLoader.class);

  private final TriggerManagerAdapter triggerManager;

  private final String triggerSource;

  private long lastUpdateTime = -1;

  @Inject
  public TriggerBasedScheduleLoader(final TriggerManager triggerManager) {
    this.triggerManager = triggerManager;
    this.triggerSource = ScheduleManager.SIMPLE_TIME_TRIGGER;
  }

  private Trigger scheduleToTrigger(final Schedule s) {
    final Condition triggerCondition = createTriggerCondition(s);
    final Condition expireCondition = createExpireCondition(s);
    final List<TriggerAction> actions = createActions(s);

    final Trigger t = new Trigger.TriggerBuilder(s.getSubmitUser(),
        this.triggerSource, triggerCondition, expireCondition, actions, s.getComment(),
        s.isAutoSubmit(), (Boolean) s.getOtherOption().getOrDefault("alertOnceOnMiss", true))
        .setSubmitTime(s.getSubmitTime())
        .setLastModifyTime(s.getLastModifyTime())
        .setId(s.getScheduleId())
        .setLastModifyConfiguration(s.getLastModifyConfiguration())
        .build();

    if (s.isRecurring()) {
      t.setResetOnTrigger(true);
    } else {
      t.setResetOnTrigger(false);
    }
    return t;
  }

  private List<TriggerAction> createActions(final Schedule s) {
    final List<TriggerAction> actions = new ArrayList<>();
	// FIXME The ExecuteFlowAction constructor is overloaded, adding some parameters, such as failure retry, failure skip.
    if (s.getOtherOption() != null) {
      s.getOtherOption().put(Constants.EXECUTE_FLOW_TRIGGER_ID, s.getScheduleId());
    }
    final ExecuteFlowAction executeAct =
        new ExecuteFlowAction("executeFlowAction", s.getProjectId(),
            s.getProjectName(), s.getFlowName(), s.getSubmitUser(),
            s.getExecutionOptions(), s.getSlaOptions(), s.getOtherOption());
    actions.add(executeAct);

    return actions;
  }

  private Condition createTriggerCondition(final Schedule s) {
    final Map<String, ConditionChecker> checkers = new HashMap<>();
    final BasicTimeChecker checker =
        new BasicTimeChecker("BasicTimeChecker_1", s.getFirstSchedTime(),
            s.getTimezone(), s.isRecurring(), s.skipPastOccurrences(),
            s.getPeriod(), s.getCronExpression(), s.getOtherOption());
    checker.setLastExecTime(s.getLastExecTime());
    checkers.put(checker.getId(), checker);
    final String expr = checker.getId() + ".eval()";
    final Condition cond = new Condition(checkers, expr);
    return cond;
  }

  private Condition createExpireCondition(final Schedule s) {
    final Map<String, ConditionChecker> checkers = new HashMap<>();
    final ConditionChecker checker = new BasicTimeChecker("EndTimeChecker_1", s.getFirstSchedTime(),
        s.getTimezone(), s.getEndSchedTime(), false, false,
        null, null);
    checkers.put(checker.getId(), checker);
    final String expr = checker.getId() + ".eval()";
    return new Condition(checkers, expr);
  }

  @Override
  public void insertSchedule(final Schedule s) throws ScheduleManagerException {
    final Trigger t = scheduleToTrigger(s);
    try {
      this.triggerManager.insertTrigger(t, t.getSubmitUser());
      s.setScheduleId(t.getTriggerId());
    } catch (final TriggerManagerException e) {
      throw new ScheduleManagerException("Failed to insert new schedule!", e);
    }
  }

  @Override
  public void updateSchedule(final Schedule s) throws ScheduleManagerException {
    final Trigger t = scheduleToTrigger(s);
    try {
      this.triggerManager.updateTrigger(t, t.getSubmitUser());
    } catch (final TriggerManagerException e) {
      throw new ScheduleManagerException("Failed to update schedule!", e);
    }
  }

  private Schedule triggerToSchedule(final Trigger t) throws ScheduleManagerException {

    final BasicTimeChecker triggerTimeChecker = getBasicTimeChecker(t.getTriggerCondition().getCheckers());
    final BasicTimeChecker endTimeChecker = getEndTimeChecker(t);

    final List<TriggerAction> actions = t.getActions();
    ExecuteFlowAction act = null;
    for (final TriggerAction action : actions) {
      if (action.getType().equals(ExecuteFlowAction.TYPE)) {
        act = (ExecuteFlowAction) action;
        break;
      }
    }
    if (triggerTimeChecker != null && act != null) {
      Schedule schedule = new Schedule(t.getTriggerId(),
          act.getProjectId(),
          act.getProjectName(),
          act.getFlowName(),
          t.getStatus().toString(),
          triggerTimeChecker.getFirstCheckTime(),
          endTimeChecker == null ? Constants.DEFAULT_SCHEDULE_END_EPOCH_TIME : endTimeChecker.getNextCheckTime(),
          triggerTimeChecker.getTimeZone(),
          triggerTimeChecker.getPeriod(),
          t.getLastModifyTime(),
          triggerTimeChecker.getNextCheckTime(),
          t.getSubmitTime(),
          t.getSubmitUser(),
          act.getExecutionOptions(),
          act.getSlaOptions(),
          triggerTimeChecker.getCronExpression(),
          act.getOtherOption(),
          t.getComment(),
          t.isBackExecuteOnceOnMiss(),t.getLastModifyConfiguration());
      schedule.setLastExecTime(triggerTimeChecker.getLastExecTime());
      return schedule;
    } else {
      logger.error("Failed to parse schedule from trigger!");
      throw new ScheduleManagerException("Failed to parse schedule from trigger!");
    }
  }

  // expirecheckers or triggerCheckers only have BasicTimeChecker today. This should be refactored in future.
  private BasicTimeChecker getBasicTimeChecker(final Map<String, ConditionChecker> checkers) {
    for (final ConditionChecker checker : checkers.values()) {
      if (checker.getType().equals(BasicTimeChecker.TYPE)) {
        return (BasicTimeChecker) checker;
      }
    }
    return null;
  }

  private BasicTimeChecker getEndTimeChecker(final Trigger t) {
    if (t.getExpireCondition().getExpression().contains("EndTimeChecker")) {
      return getBasicTimeChecker(t.getExpireCondition().getCheckers());
    }
    return null;
  }


  @Override
  public void removeSchedule(final Schedule s) throws ScheduleManagerException {
    try {
      this.triggerManager.removeTrigger(s.getScheduleId(), s.getSubmitUser());
    } catch (final TriggerManagerException e) {
      throw new ScheduleManagerException(e.getMessage(), e);
    }

  }

  @Override
  public void updateNextExecTime(final Schedule s) throws ScheduleManagerException {

  }

  @Override
  public synchronized List<Schedule> loadUpdatedSchedules()      throws ScheduleManagerException {
    final List<Trigger> triggers;
    try {
      triggers = this.triggerManager.getTriggerUpdates(this.triggerSource, this.lastUpdateTime);
    } catch (final TriggerManagerException e) {
      logger.error("load updated schedules failed, caused by:" + e);
      throw new ScheduleManagerException(e);
    }
    final List<Schedule> schedules = new ArrayList<>();
    for (final Trigger t : triggers) {
      this.lastUpdateTime = Math.max(this.lastUpdateTime, t.getLastModifyTime());

      // 避免actions为0,抛异常报错
      if (t.getActions().size() == 0) {
        continue;
      }
      final Schedule s = triggerToSchedule(t);
      schedules.add(s);
      logger.debug(
          "loaded schedule for " + s.getProjectName() + " (project_ID: " + s.getProjectId() + ")");
    }
    return schedules;
  }

  @Override
  public long getTriggerInitTime() {
    return this.triggerManager.getTriggerInitTime();
  }

  /**
   * Loading all triggers from triggerManager and converted into Schedule.
   */
  @Override
  public List<Schedule> loadAllSchedules() throws ScheduleManagerException {
    List<Trigger> triggers = this.triggerManager.getTriggers();
    List<Schedule> schedules = new ArrayList<>();
    for (Trigger trigger : triggers) {
      Schedule schedule = triggerToSchedule(trigger);
      schedules.add(schedule);
      logger.debug("loaded schedule for {} (project_id: {}, flow_id: {})",
          schedule.getScheduleId(), schedule.getProjectName(), schedule.getFlowName());
    }
    return schedules;
  }

  @Override
  public List<Integer> loadRemovedTriggers() {
    return this.triggerManager.getRemovedTriggerIds();
  }


}
