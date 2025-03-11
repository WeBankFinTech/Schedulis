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

package azkaban.trigger;

import azkaban.ServiceProvider;
import azkaban.batch.HoldBatchContext;
import azkaban.event.EventHandler;
import azkaban.executor.ExecutorLoader;
import azkaban.executor.ExecutorManagerAdapter;
import azkaban.executor.ExecutorManagerException;
import azkaban.flow.NoSuchResourceException;
import azkaban.project.Project;
import azkaban.scheduler.MissedScheduleManager;
import azkaban.trigger.builtin.BasicTimeChecker;
import azkaban.trigger.builtin.ExecuteFlowAction;
import azkaban.utils.CommonLock;
import azkaban.utils.Props;
import org.apache.commons.collections4.MapUtils;
import org.apache.commons.lang.StringUtils;
import org.quartz.CronExpression;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.time.LocalTime;
import java.util.*;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.PriorityBlockingQueue;
import java.util.stream.Collectors;

import static azkaban.Constants.ConfigurationKeys.SYSTEM_SCHEDULE_SWITCH_ACTIVE_KEY;
import static azkaban.Constants.ConfigurationKeys.WTSS_QUERY_SERVER_TRIGGER_CACHE_REFRESH_PERIOD;
import static java.util.Objects.requireNonNull;

@Singleton
public class TriggerManager extends EventHandler implements TriggerManagerAdapter {

  public static final long DEFAULT_SCANNER_INTERVAL_MS = 60000;
  private static final Logger logger = LoggerFactory.getLogger(TriggerManager.class);
  protected static final Map<Integer, Trigger> TRIGGER_ID_MAP = new ConcurrentHashMap<>();
  private static final Set<Integer> removedTriggerIds = new HashSet<>();
  private final TriggerScannerThread runnerThread;
  private final Object syncObj = new Object();
  private final CheckerTypeLoader checkerTypeLoader;
  private final ActionTypeLoader actionTypeLoader;
  private final TriggerLoader triggerLoader;
  @Inject
  private ExecutorLoader executorLoader;
  private final LocalTriggerJMX jmxStats = new LocalTriggerJMX();
  private long lastRunnerThreadCheckTime = -1;
  private long runnerThreadIdleTime = -1;
  private String scannerStage = "";

  // 定时调度生效系统级别开关
  private static boolean system_schedule_switch_active;

  protected HoldBatchContext holdBatchContext;

  protected boolean holdBatchSwitch;

  protected long triggerInitTime = -1;

  private int trigerMapRefreshTime = 15;


  @Inject
  public TriggerManager(final Props props, final TriggerLoader triggerLoader,
      final ExecutorManagerAdapter executorManagerAdapter,
      final MissedScheduleManager missedScheduleManager) throws TriggerManagerException {

    requireNonNull(props);
    requireNonNull(executorManagerAdapter);
    this.triggerLoader = requireNonNull(triggerLoader);

    final long scannerInterval = props.getLong("trigger.scan.interval", DEFAULT_SCANNER_INTERVAL_MS);
    this.runnerThread = new TriggerScannerThread(scannerInterval);

    this.checkerTypeLoader = new CheckerTypeLoader();
    this.actionTypeLoader = new ActionTypeLoader();

    try {
      this.checkerTypeLoader.init(props);
      this.actionTypeLoader.init(props);
    } catch (final Exception e) {
      throw new TriggerManagerException(e);
    }

    Condition.setCheckerLoader(this.checkerTypeLoader);
    Trigger.setActionTypeLoader(this.actionTypeLoader);
    Trigger.setMissedScheduleManager(missedScheduleManager);
    system_schedule_switch_active = props.getBoolean(SYSTEM_SCHEDULE_SWITCH_ACTIVE_KEY, true);
    this.holdBatchContext = ServiceProvider.SERVICE_PROVIDER.getInstance(HoldBatchContext.class);
    this.holdBatchSwitch = props.getBoolean("azkaban.holdbatch.switch", false);
    this.trigerMapRefreshTime = props.getInt(WTSS_QUERY_SERVER_TRIGGER_CACHE_REFRESH_PERIOD, 15);
    logger.info("TriggerManager loaded.");
  }

  @Override
  public void start() throws TriggerManagerException {

    try {
      // expect loader to return valid triggers
      final List<Trigger> triggers = this.triggerLoader.loadTriggers();
      for (final Trigger t : triggers) {
        if (system_schedule_switch_active) {
          this.runnerThread.addTrigger(t);
        }
        TRIGGER_ID_MAP.put(t.getTriggerId(), t);
      }
    } catch (final Exception e) {
      logger.error("Failed to add trigger", e);
      throw new TriggerManagerException(e);
    }

    if (! system_schedule_switch_active) {
      logger.warn("trigger is offline mode");
      refreshTriggerMap();
      return;
    }

    synchronized (CommonLock.SCHEDULE_INIT_LOCK){
      this.triggerInitTime = System.currentTimeMillis();
      CommonLock.SCHEDULE_INIT_LOCK.notifyAll();
    }
    this.runnerThread.start();
  }

  private void refreshTriggerMap() {
    Timer timer = new Timer();

    Long lastTime = System.currentTimeMillis();
    TimerTask task = new TimerTask() {
      @Override
      public void run() {
        logger.info("Start to refresh TRIGGER_ID_MAP");
        try {
          LocalTime time = LocalTime.now();
          int hour = time.getHour();
          if (hour == 10 || hour == 22) {
            final List<Trigger> triggers = triggerLoader.loadTriggers();
            for (final Trigger t : triggers) {
              TRIGGER_ID_MAP.put(t.getTriggerId(), t);
            }
            Set<Integer> newTriggerIds = triggers.stream().map(trigger -> trigger.getTriggerId()).collect(Collectors.toSet());
            TRIGGER_ID_MAP.keySet().removeIf(key -> !newTriggerIds.contains(key));
          } else {
            final List<Trigger> triggers = triggerLoader.getUpdatedTriggers(lastTime);
            for (final Trigger t : triggers) {
              TRIGGER_ID_MAP.put(t.getTriggerId(), t);
            }
          }
        } catch (Exception e) {
          logger.error("Failed to refresh TRIGGER_ID_MAP", e);
        }
        logger.info("Finished to refresh TRIGGER_ID_MAP");
      }
    };
    timer.schedule(task, 1000 * 60 * 5, 1000 * 60 * trigerMapRefreshTime);
  }

  protected CheckerTypeLoader getCheckerLoader() {
    return this.checkerTypeLoader;
  }

  protected ActionTypeLoader getActionLoader() {
    return this.actionTypeLoader;
  }

  public void insertTrigger(final Trigger t) throws TriggerManagerException {
    logger.info("Inserting trigger " + t + " in TriggerManager");
    synchronized (this.syncObj) {
      try {
        this.triggerLoader.addTrigger(t);
      } catch (final TriggerLoaderException e) {
        throw new TriggerManagerException(e);
      }
      this.runnerThread.addTrigger(t);
      TRIGGER_ID_MAP.put(t.getTriggerId(), t);
    }
  }

  public void insertTriggerByWeb(final int id) throws TriggerManagerException {
    //HA SUPPORT
  }

  public void removeTrigger(final int id) throws TriggerManagerException {
    logger.info("Removing trigger with id: " + id + " from TriggerManager");
    synchronized (this.syncObj) {
      final Trigger t = TRIGGER_ID_MAP.get(id);
      if (t != null) {
        removeTrigger(TRIGGER_ID_MAP.get(id));
      }
    }
  }

  public void removeTriggerByWeb(final int id) throws TriggerManagerException {
    //HA SUPPORT
  }

  public void updateTrigger(final Trigger t) throws TriggerManagerException {
    logger.info("Updating trigger " + t + " in TriggerManager");
    synchronized (this.syncObj) {
      this.runnerThread.deleteTrigger(TRIGGER_ID_MAP.get(t.getTriggerId()));
      this.runnerThread.addTrigger(t);
      TRIGGER_ID_MAP.put(t.getTriggerId(), t);
      try {
        this.triggerLoader.updateTrigger(t);
      } catch (final TriggerLoaderException e) {
        throw new TriggerManagerException(e);
      }
    }
  }

  public void refreshTriggerConfig(HashMap<String, String> flowNameMap, HashMap<String, String> nodeNameMap, Project project) throws TriggerManagerException {
    int total = project.getFlows().size();
    int count = 0;
    BlockingQueue<Trigger> triggers = this.runnerThread.triggers;
    for (Trigger trigger : triggers) {
      List<TriggerAction> actions = trigger.getActions();
      for (TriggerAction action : actions) {
        if (action instanceof ExecuteFlowAction) {
          ExecuteFlowAction executeFlowAction = (ExecuteFlowAction) action;
          if (executeFlowAction.getProjectId() == project.getId()) {
            count++;
            String newName = flowNameMap.get(executeFlowAction.getFlowName());
            if (null != newName) {
              if (StringUtils.isEmpty(newName)) {
                removeTrigger(trigger);
                logger.info("delete trigger old flow name : {}", executeFlowAction.getFlowName());
              } else {
                executeFlowAction.setFlowName(newName);
                logger.info("update trigger old flow name : {} to new name : {}", executeFlowAction.getFlowName(), newName);
              }
            }
            Set<String> keySet = nodeNameMap.keySet();
            for (String oldName : keySet) {
              Map<String, String> jobCronExpression = (Map<String, String>) executeFlowAction.getOtherOption().get("job.cron.expression");
              Set<String> jobNestedIds = jobCronExpression.keySet();
              Iterator<String> iterator = jobNestedIds.iterator();
              HashMap<String, String> newCronExpression = new HashMap<>();
              while (iterator.hasNext()) {
                String jobNestedId = iterator.next();
                if (jobNestedId.contains("-")) {
                  replaceName(nodeNameMap, oldName, jobCronExpression, iterator, newCronExpression, jobNestedId, "-");
                } else if (jobNestedId.contains(":")) {
                  replaceName(nodeNameMap, oldName, jobCronExpression, iterator, newCronExpression, jobNestedId, ":");
                } else {
                  replaceName(nodeNameMap, oldName, jobCronExpression, iterator, newCronExpression, jobNestedId, "");
                }
              }
              jobCronExpression.putAll(newCronExpression);
            }
            try {
              this.triggerLoader.updateTrigger(trigger);
            } catch (final TriggerLoaderException e) {
              throw new TriggerManagerException(e);
            }
          }
        }
        if (count == total) {
          return;
        }
      }

    }
  }

  private void replaceName(HashMap<String, String> nodeNameMap, String oldName, Map<String, String> jobCronExpression, Iterator<String> iterator, HashMap<String, String> newCronExpression, String jobNestedId ,String delemiter) {
    String newName;
    if (!StringUtils.isEmpty(delemiter)) {
      String[] jobs = jobNestedId.split(delemiter);
      for (int i = 0; i < jobs.length; i++) {
        if (jobs[i].equals(oldName)) {
          newName = nodeNameMap.get(oldName);
          if (!StringUtils.isEmpty(newName)) {
            String cronExpression = jobCronExpression.get(jobNestedId);
            iterator.remove();
            jobs[i] = newName;
            jobNestedId = String.join(delemiter, jobs);
            newCronExpression.put(jobNestedId, cronExpression);
            logger.info("update trigger old flow name : {} to new name : {}", oldName, newName);
          } else {
            iterator.remove();
            logger.info("delete trigger old jobNestedId : {}", oldName);
          }
        }
      }
    } else {
      if (jobNestedId.equals(oldName)) {
        newName = nodeNameMap.get(oldName);
        if (!StringUtils.isEmpty(newName)) {
          String cronExpression = jobCronExpression.get(jobNestedId);
          iterator.remove();
          newCronExpression.put(newName, cronExpression);
          logger.info("update trigger old flow name : {} to new name : {}", oldName, newName);
        } else {
          iterator.remove();
          logger.info("delete trigger old jobNestedId : {}", oldName);
        }
      }
    }
  }

  public void updateTriggerByWeb(final int id) throws TriggerManagerException {
    //HA SUPPORT
  }

  public void removeTrigger(final Trigger t) throws TriggerManagerException {
    logger.info("Removing trigger " + t + " from TriggerManager");
    synchronized (this.syncObj) {
      this.runnerThread.deleteTrigger(t);
      TRIGGER_ID_MAP.remove(t.getTriggerId());
      removedTriggerIds.add(t.getTriggerId());
      try {
        t.stopCheckers();
        this.triggerLoader.removeTrigger(t);
      } catch (final TriggerLoaderException e) {
        throw new TriggerManagerException(e);
      }
    }
  }

  @Override
  public List<Trigger> getTriggers() {
    return new ArrayList<>(TRIGGER_ID_MAP.values());
  }

  /**
   * get a list of removed triggers and clear the list
   */
  @Override
  public List<Integer> getRemovedTriggerIds() {
    List<Integer> removedTriggerIdsCopy = new ArrayList<>(removedTriggerIds);
    removedTriggerIds.clear();
    return removedTriggerIdsCopy;
  }

  public Map<String, Class<? extends ConditionChecker>> getSupportedCheckers() {
    return this.checkerTypeLoader.getSupportedCheckers();
  }

  public Trigger getTrigger(final int triggerId) {
    synchronized (this.syncObj) {
      return TRIGGER_ID_MAP.get(triggerId);
    }
  }

  public void expireTrigger(final int triggerId) {
    final Trigger t = getTrigger(triggerId);
    t.setStatus(TriggerStatus.EXPIRED);
  }

  @Override
  public List<Trigger> getTriggers(final String triggerSource) {
    final List<Trigger> triggers = new ArrayList<>();
    for (final Trigger t : TRIGGER_ID_MAP.values()) {
      if (t.getSource().equals(triggerSource)) {
        triggers.add(t);
      }
    }
    return triggers;
  }

  @Override
  public List<Trigger> getTriggerUpdates(final String triggerSource, final long lastUpdateTime) throws TriggerManagerException {
    final List<Trigger> triggers = new ArrayList<>();
    for (final Trigger t : TRIGGER_ID_MAP.values()) {
      if (t.getSource().equals(triggerSource)
              && t.getLastModifyTime() > lastUpdateTime) {
        triggers.add(t);
      }
    }
    return triggers;
  }

  @Override
  public List<Trigger> getAllTriggerUpdates(final long lastUpdateTime)
          throws TriggerManagerException {
    final List<Trigger> triggers = new ArrayList<>();
    for (final Trigger t : TRIGGER_ID_MAP.values()) {
      if (t.getLastModifyTime() > lastUpdateTime) {
        triggers.add(t);
      }
    }
    return triggers;
  }

  @Override
  public void insertTrigger(final Trigger t, final String user)
          throws TriggerManagerException {
    insertTrigger(t);
  }

  @Override
  public void removeTrigger(final int id, final String user) throws TriggerManagerException {
    removeTrigger(id);
  }

  @Override
  public void updateTrigger(final Trigger t, final String user)
          throws TriggerManagerException {
    updateTrigger(t);
  }

  @Override
  public long getTriggerInitTime() {
    return triggerInitTime;
  }

  @Override
  public void shutdown() {
    this.runnerThread.shutdown();
  }

  @Override
  public TriggerJMX getJMX() {
    return this.jmxStats;
  }

  @Override
  public void registerCheckerType(final String name,
                                  final Class<? extends ConditionChecker> checker) {
    this.checkerTypeLoader.registerCheckerType(name, checker);
  }

  @Override
  public void registerActionType(final String name,
                                 final Class<? extends TriggerAction> action) {
    this.actionTypeLoader.registerActionType(name, action);
  }

  protected void checkFrequentBatch(Trigger trigger, ExecuteFlowAction action) {
    try {
      if (!holdBatchSwitch) {
        return;
      }
      String batchId = holdBatchContext
              .isInBatch(action.getProjectName(), action.getFlowName(), action.getSubmitUser());
      if (StringUtils.isEmpty(batchId)) {
        action.getOtherOption().remove("frequentBatchId");
        return;
      }
      BasicTimeChecker basicTimeChecker = null;
      for (final ConditionChecker checker : trigger.getTriggerCondition().getCheckers().values()) {
        if (checker.getType().equals(BasicTimeChecker.TYPE)) {
          basicTimeChecker = (BasicTimeChecker) checker;
        }
      }
      if (basicTimeChecker != null) {
        CronExpression cronExpression = new CronExpression(basicTimeChecker.getCronExpression());
        Date currentTime = cronExpression.getNextValidTimeAfter(new Date());
        if (currentTime == null) {
          return;
        }
        Date nextTime = cronExpression.getNextValidTimeAfter(currentTime);
        if (nextTime == null) {
          return;
        }
        if (nextTime.getTime() - currentTime.getTime() < 24 * 60 * 60 * 1000) {
          action.getOtherOption().put("frequentBatchId", batchId);
        }
      }
    } catch (Exception e) {
      logger.error("calculate day time error", e);
    }

  }

  private class TriggerScannerThread extends Thread {

    private final long scannerInterval;
    private final BlockingQueue<Trigger> triggers;
    private boolean shutdown = false;

    public TriggerScannerThread(final long scannerInterval) {
      this.triggers = new PriorityBlockingQueue<>(1, new TriggerComparator());
      this.setName("TriggerRunnerManager-Trigger-Scanner-Thread");
      this.scannerInterval = scannerInterval;
    }

    public void shutdown() {
      logger.error("Shutting down trigger manager thread " + this.getName());
      this.shutdown = true;
      this.interrupt();
    }

    public void addTrigger(final Trigger t) {
      synchronized (TriggerManager.this.syncObj) {
        t.updateNextCheckTime();
        this.triggers.add(t);
      }
    }

    public void deleteTrigger(final Trigger t) {
      this.triggers.remove(t);
    }

    @Override
    public void run() {
      while (!this.shutdown) {
        synchronized (TriggerManager.this.syncObj) {
          try {
            TriggerManager.this.lastRunnerThreadCheckTime = System.currentTimeMillis();

            TriggerManager.this.scannerStage =
                    "Ready to start a new scan cycle at "
                            + TriggerManager.this.lastRunnerThreadCheckTime;

            try {
              checkAllTriggers();
            } catch (final Throwable t) {
              logger.error("Failed to checkAllTriggers", t);
            }

            TriggerManager.this.scannerStage = "Done flipping all triggers.";

            TriggerManager.this.runnerThreadIdleTime =
                    this.scannerInterval
                            - (System.currentTimeMillis() - TriggerManager.this.lastRunnerThreadCheckTime);

            if (TriggerManager.this.runnerThreadIdleTime <= 0) {
              logger.error("Trigger manager thread " + this.getName()
                      + " is too busy! Remaining idle time in ms: {}", TriggerManager.this.runnerThreadIdleTime);
            } else {
              TriggerManager.this.syncObj.wait(TriggerManager.this.runnerThreadIdleTime);
              TriggerManager.logger.debug("TriggerManager wait on {}", TriggerManager.this.runnerThreadIdleTime);
            }
          } catch (final InterruptedException e) {
            logger.info("Interrupted. Probably to shut down.");
          }
        }
        logger.info("Checked All Triggers, size: {}, wait time: {}", this.triggers.size(),
                TriggerManager.this.runnerThreadIdleTime);
      }
    }

    /**
     * 请不要在该方法里面加任何耗时操作，该方法时间敏感
     * 1. 禁止进行DB操作
     * 2. 禁止http相关操作
     * @throws TriggerManagerException
     */
    private void checkAllTriggers() throws TriggerManagerException {
      logger.info("Checking All Triggers, size: {}", this.triggers.size());
      // sweep through the rest of them
      for (final Trigger t : this.triggers) {
        try {
          TriggerManager.this.scannerStage = "Checking for trigger " + t.getTriggerId();
          if (t.getStatus().equals(TriggerStatus.INVALID)) {
            removeTrigger(t);
            continue;
          }

          if (t.getStatus().equals(TriggerStatus.READY)) {

            /**
             * Prior to this change, expiration condition should never be called though
             * we have some related code here. ExpireCondition used the same BasicTimeChecker
             * as triggerCondition do. As a consequence, we need to figure out a way to distinguish
             * the previous ExpireCondition and this commit's ExpireCondition.
             */
            if (t.getExpireCondition().getExpression().contains("EndTimeChecker") && t
                    .expireConditionMet()) {
              //停止过期的任务
              onTriggerPause(t);
            } else if (t.triggerConditionMet()) {
              String submitUser = t.getSubmitUser();
              // 该方法已经优化为走了缓存
              boolean scheduleSwitch = executorLoader.fetchGroupScheduleSwitch(submitUser);
              if (logger.isDebugEnabled()) {
                logger.debug("Checking for trigger: " + t.getTriggerId() + ", submitUser: " + submitUser + ", scheduleSwitch: " + scheduleSwitch);
              }
              if (!scheduleSwitch) {
                logger.info("Checking for trigger: {} submitUser: {} are off, will be skip trigger", t.getTriggerId(), submitUser);
                continue;
              }
              //触发定时任务
              onTriggerTrigger(t);
            }
          }
          if (t.getStatus().equals(TriggerStatus.EXPIRED) && "azkaban".equals(t.getSource())
                  || t.getStatus().equals(TriggerStatus.INVALID)) {
            removeTrigger(t);
          } else {
            t.updateNextCheckTime();
          }
        } catch (final Throwable th) {
          //skip this trigger, moving on to the next one
          logger.error("Failed to process trigger with id : " + t, th);
        }
      }
    }

    private void onTriggerTrigger(final Trigger t) throws TriggerManagerException {
      final List<TriggerAction> actions = t.getTriggerActions();
      for (final TriggerAction action : actions) {
        try {
          logger.info("Doing trigger actions " + action.getDescription() + " for " + t);

          // 检查定时调度系统级别的激活开关和页面级别的激活开关, true:激活状态  false:失效状态
          if (action instanceof ExecuteFlowAction) {
            Map<String, Object> otherOption = ((ExecuteFlowAction) action).getOtherOption();
            if (MapUtils.isNotEmpty(otherOption)) {

              if (t.getNextCheckTime() < 0) {
                otherOption.put("activeFlag", false);
                ((ExecuteFlowAction) action).setOtherOption(otherOption);
                continue;
              }

              // 为历史数据初始化
              Boolean activeFlag = (Boolean)otherOption.get("activeFlag");

              logger.info("current schedule active switch, flowLevel=" + activeFlag);
              if (null == activeFlag) {
                activeFlag = true;
              }
              if (activeFlag) {
                checkFrequentBatch(t,(ExecuteFlowAction) action);
                try {
                  action.doAction();
                } catch (NoSuchResourceException e) {
                  logger.warn(
                          "find no matching projects/flows for the trigger " + t.getTriggerId()
                                  + ", mark trigger invalid");
                  t.setStatus(TriggerStatus.INVALID);
                  return;
                }
              }
            }
          } else {
            // 非定时调度执行,直接执行
            action.doAction();
          }

        } catch (final ExecutorManagerException e) {
          if (e.getReason() == ExecutorManagerException.Reason.SkippedExecution) {
            logger.info("Skipped action [" + action.getDescription() + "] for [" + t +
                    "] because: " + e.getMessage());
          } else {
            logger.error("Failed to do action [" + action.getDescription() + "] for [" + t + "]",
                    e);
          }
        } catch (final Throwable th) {
          logger.error("Failed to do action [" + action.getDescription() + "] for [" + t + "]", th);
        }
      }
      if (t.isResetOnTrigger()) {
        t.resetTriggerConditions();
        try {
          t.sendTaskToMissedScheduleManager();
        } catch (NoSuchResourceException e) {
          logger.warn("find no matching projects/flows for the trigger " + t.getTriggerId()
                  + ", mark trigger invalid");
          t.setStatus(TriggerStatus.INVALID);
          return;
        }

        // FIXME If the scheduled schedule is only executed once (such as the schedule specific to the year, month, and day),
        //  the scheduled schedule needs to be terminated; otherwise, the scheduled schedule will be triggered periodically.
        //  As a solution, after the checkertime does not change, set the scheduled scheduling status to EXPIRED.
        if (t.getNextCheckTime() < 0) {
          logger.info("NextCheckTime did not change. Setting status to expired for trigger"
                  + t.getTriggerId());
          t.setStatus(TriggerStatus.EXPIRED);
        }
      } else {
        logger.info("NextCheckTime did not change. Setting status to expired for trigger"
                + t.getTriggerId());
        t.setStatus(TriggerStatus.EXPIRED);
      }
      try {
        TriggerManager.this.triggerLoader.updateTrigger(t);
      } catch (final TriggerLoaderException e) {
        throw new TriggerManagerException(e);
      }
    }

    private void onTriggerPause(final Trigger t) throws TriggerManagerException {
      final List<TriggerAction> expireActions = t.getExpireActions();
      for (final TriggerAction action : expireActions) {
        try {
          logger.info("Doing expire actions for " + action.getDescription() + " for " + t);
          action.doAction();
        } catch (final Exception e) {
          logger.error("Failed to do expire action " + action.getDescription() + " for " + t, e);
        } catch (final Throwable th) {
          logger.error("Failed to do expire action " + action.getDescription() + " for " + t, th);
        }
      }
      logger.info("Pausing Trigger " + t.getDescription());
      t.setStatus(TriggerStatus.PAUSED);
      try {
        TriggerManager.this.triggerLoader.updateTrigger(t);
      } catch (final TriggerLoaderException e) {
        throw new TriggerManagerException(e);
      }
    }

    private class TriggerComparator implements Comparator<Trigger> {

      @Override
      public int compare(final Trigger arg0, final Trigger arg1) {
        final long first = arg1.getNextCheckTime();
        final long second = arg0.getNextCheckTime();

        if (first == second) {
          return 0;
        } else if (first < second) {
          return 1;
        }
        return -1;
      }
    }
  }

  private class LocalTriggerJMX implements TriggerJMX {

    @Override
    public long getLastRunnerThreadCheckTime() {
      return TriggerManager.this.lastRunnerThreadCheckTime;
    }

    @Override
    public boolean isRunnerThreadActive() {
      return TriggerManager.this.runnerThread.isAlive();
    }

    @Override
    public String getPrimaryServerHost() {
      return "local";
    }

    @Override
    public int getNumTriggers() {
      return TRIGGER_ID_MAP.size();
    }

    @Override
    public String getTriggerSources() {
      final Set<String> sources = new HashSet<>();
      for (final Trigger t : TRIGGER_ID_MAP.values()) {
        sources.add(t.getSource());
      }
      return sources.toString();
    }

    @Override
    public String getTriggerIds() {
      return TRIGGER_ID_MAP.keySet().toString();
    }

    @Override
    public long getScannerIdleTime() {
      return TriggerManager.this.runnerThreadIdleTime;
    }

    @Override
    public Map<String, Object> getAllJMXMbeans() {
      return new HashMap<>();
    }

    @Override
    public String getScannerThreadStage() {
      return TriggerManager.this.scannerStage;
    }

  }

}
