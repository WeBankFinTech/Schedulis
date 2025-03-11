/*
 * Copyright 2017 LinkedIn Corp.
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

package azkaban.execapp;

import azkaban.execapp.action.KillExecutionAction;
import azkaban.execapp.action.KillJobAction;
import azkaban.executor.ExecutableFlow;
import azkaban.executor.ExecutorLoader;
import azkaban.executor.Status;
import azkaban.sla.SlaOption;
import azkaban.trigger.Condition;
import azkaban.trigger.ConditionChecker;
import azkaban.trigger.TriggerAction;
import azkaban.trigger.builtin.SlaAlertAction;
import azkaban.trigger.builtin.SlaChecker;
import azkaban.utils.Utils;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import javax.inject.Inject;
import javax.inject.Singleton;
import org.joda.time.DateTime;
import org.joda.time.DateTimeUtils;
import org.joda.time.ReadablePeriod;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


@Singleton
public class TriggerManager {

  private static final int SCHEDULED_THREAD_POOL_SIZE = 4;
  private static final Logger logger = LoggerFactory.getLogger(TriggerManager.class);
  private final ScheduledExecutorService scheduledService;
  private final ExecutorLoader executorLoader;


  @Inject
  public TriggerManager(final ExecutorLoader executorLoader) {
    this.scheduledService = Executors.newScheduledThreadPool(SCHEDULED_THREAD_POOL_SIZE);
    this.executorLoader = executorLoader;
  }

  private Condition createCondition(final SlaOption sla, final int execId, final String checkerName,
                                    final String checkerMethod, final long duration) {
    final SlaChecker slaFailChecker = new SlaChecker(checkerName, sla, execId, duration);
    final Map<String, ConditionChecker> slaCheckers = new HashMap<>();
    slaCheckers.put(slaFailChecker.getId(), slaFailChecker);
    return new Condition(slaCheckers, slaFailChecker.getId() + "." + checkerMethod);
  }

  private List<TriggerAction> createActions(final SlaOption sla, final int execId, final String alertType) {
    final List<TriggerAction> actions = new ArrayList<>();
    final List<String> slaActions = sla.getActions();
    for (final String act : slaActions) {
      TriggerAction action = null;
      switch (act) {
        case SlaOption.ACTION_ALERT:
          action = new SlaAlertAction(SlaOption.ACTION_ALERT, sla, execId, alertType);
          break;
        case SlaOption.ACTION_CANCEL_FLOW:
          action = new KillExecutionAction(SlaOption.ACTION_CANCEL_FLOW, execId);
          break;
        case SlaOption.ACTION_KILL_JOB:
          final String jobId = (String) sla.getInfo().get(SlaOption.INFO_JOB_NAME);
          action = new KillJobAction(SlaOption.ACTION_KILL_JOB, execId, jobId);
          break;
        default:
          logger.info("Unknown action type " + act);
          break;
      }
      if (action != null) {
        actions.add(action);
      }
    }
    return actions;
  }

  @SuppressWarnings("FutureReturnValueIgnored")
  public void addTrigger(final int execId, final List<SlaOption> slaOptions) {
    for (final SlaOption sla : slaOptions) {
      handleSla(execId, sla);
    }
  }

  private void handleSla(int execId, SlaOption sla) {
    if (sla.getInfo().containsKey(SlaOption.INFO_DURATION)) {
      final List<TriggerAction> actions = createActions(sla, execId, SlaOption.INFO_DURATION);
      final ReadablePeriod duration = Utils
              .parsePeriodString((String) sla.getInfo().get(SlaOption.INFO_DURATION));
      final long durationInMillis = duration.toPeriod().toStandardDuration().getMillis();
      submitTrigger(execId, sla, actions, durationInMillis);
    }
    if (sla.getInfo().containsKey(SlaOption.INFO_ABS_TIME)) {
      final List<TriggerAction> actions = createActions(sla, execId, SlaOption.INFO_ABS_TIME);
      String[] absTime = sla.getInfo().get(SlaOption.INFO_ABS_TIME).toString().split(":");
      DateTime time = new DateTime().withHourOfDay(Integer.parseInt(absTime[0]))
              .withMinuteOfHour(Integer.parseInt(absTime[1]));
      if (time.isBeforeNow()) {
        for (final TriggerAction action : actions) {
          try {
            ExecutableFlow flow = this.executorLoader.fetchExecutableFlow(execId);
            // FIXME Added judgment. If the task has been completed, the task will not be triggered.
            if (!Status.isStatusFinished(flow.getStatus())) {
              //if(Status.RUNNING.equals(flow.getStatus())){
              action.doAction();
            }
          } catch (final Exception e) {
            logger.error("Failed to do action " + action.getDescription()
                    + " for execution " + execId, e);
          }
        }
      } else {
        submitTrigger(execId, sla, actions, time.getMillis() - DateTimeUtils.currentTimeMillis());
      }
    }
  }

  private void submitTrigger(int execId, SlaOption sla, List<TriggerAction> actions,
                             long durationInMillis) {
    final Condition triggerCond = createCondition(sla, execId, "slaFailChecker", "isSlaFailed()", durationInMillis);

    // if whole flow finish before violating sla, just expire the checker 如果flow在违反 SLA 之前完成 则终止这个 checkerl
    final Condition expireCond = createCondition(sla, execId, "slaPassChecker", "isSlaPassed()", durationInMillis);

    final Trigger trigger = new Trigger(execId, triggerCond, expireCond, actions,executorLoader);

    logger.info("Adding sla trigger " + sla.toString() + " to execution " + execId
            + ", scheduled to trigger in " + durationInMillis / 1000 + " seconds");
    this.scheduledService.schedule(trigger, durationInMillis, TimeUnit.MILLISECONDS);
  }

  // FIXME The overloaded addTrigger method is used for job timeout alarms.
  public void addTrigger(final int execId, final List<SlaOption> slaOptions, final String slaJobName) {
    for (final SlaOption sla : slaOptions) {
      if (!sla.getInfo().get("JobName").equals(slaJobName)) {
        continue;
      }
      this.handleSla(execId, sla);
    }
  }

  public void shutdown() {
    this.scheduledService.shutdownNow();
  }
}
