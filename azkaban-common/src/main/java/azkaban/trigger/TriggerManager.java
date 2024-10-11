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

import static java.util.Objects.requireNonNull;

import azkaban.event.EventHandler;
import azkaban.executor.ExecutorLoader;
import azkaban.executor.ExecutorManagerAdapter;
import azkaban.executor.ExecutorManagerException;
import azkaban.trigger.builtin.ExecuteFlowAction;
import azkaban.utils.Props;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.PriorityBlockingQueue;
import javax.inject.Inject;
import javax.inject.Singleton;
import org.apache.commons.collections4.MapUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Singleton
public class TriggerManager extends EventHandler implements TriggerManagerAdapter {

  public static final long DEFAULT_SCANNER_INTERVAL_MS = 60000;
  private static final Logger logger = LoggerFactory.getLogger(TriggerManager.class);
  private static final Map<Integer, Trigger> TRIGGER_ID_MAP = new ConcurrentHashMap<>();

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

  public static final String SYSTEM_SCHEDULE_SWITCH_ACTIVE_CONF = "system.schedule.switch.active";

  // 定时调度生效系统级别开关
  private static boolean system_schedule_switch_active;


  @Inject
  public TriggerManager(final Props props, final TriggerLoader triggerLoader,
      final ExecutorManagerAdapter executorManagerAdapter) throws TriggerManagerException {

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
    system_schedule_switch_active = props.getBoolean(SYSTEM_SCHEDULE_SWITCH_ACTIVE_CONF, true);

    logger.info("TriggerManager loaded.");
  }

  @Override
  public void start() throws TriggerManagerException {

    try {
      // expect loader to return valid triggers
      final List<Trigger> triggers = this.triggerLoader.loadTriggers();
      for (final Trigger t : triggers) {
        this.runnerThread.addTrigger(t);
        TRIGGER_ID_MAP.put(t.getTriggerId(), t);
      }
    } catch (final Exception e) {
      logger.error("", e);
      throw new TriggerManagerException(e);
    }
    if (system_schedule_switch_active) {
      this.runnerThread.start();
    }
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

  public void updateTriggerByWeb(final int id) throws TriggerManagerException {
    //HA SUPPORT
  }

  public void removeTrigger(final Trigger t) throws TriggerManagerException {
    logger.info("Removing trigger " + t + " from TriggerManager");
    synchronized (this.syncObj) {
      this.runnerThread.deleteTrigger(t);
      TRIGGER_ID_MAP.remove(t.getTriggerId());
      try {
        t.stopCheckers();
        this.triggerLoader.removeTrigger(t);
      } catch (final TriggerLoaderException e) {
        throw new TriggerManagerException(e);
      }
    }
  }

  public List<Trigger> getTriggers() {
    return new ArrayList<>(TRIGGER_ID_MAP.values());
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
            } catch (final Exception e) {
              logger.error(e.getMessage());
            } catch (final Throwable t) {
              logger.error(t.getMessage());
            }

            TriggerManager.this.scannerStage = "Done flipping all triggers.";

            TriggerManager.this.runnerThreadIdleTime =
                this.scannerInterval
                    - (System.currentTimeMillis() - TriggerManager.this.lastRunnerThreadCheckTime);

            if (TriggerManager.this.runnerThreadIdleTime < 0) {
              logger.error("Trigger manager thread " + this.getName()
                  + " is too busy!");
            } else {
              TriggerManager.this.syncObj.wait(TriggerManager.this.runnerThreadIdleTime);
            }
          } catch (final InterruptedException e) {
            logger.info("Interrupted. Probably to shut down.");
          }
        }
      }
    }

    private void checkAllTriggers() throws TriggerManagerException {
      // sweep through the rest of them
      for (final Trigger t : this.triggers) {
        try {
          TriggerManager.this.scannerStage = "Checking for trigger " + t.getTriggerId();
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
              //触发定时任务
              onTriggerTrigger(t);
            }
          }
          if (t.getStatus().equals(TriggerStatus.EXPIRED) && "azkaban".equals(t.getSource())) {
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

              // 为历史数据初始化
              Boolean activeFlag = (Boolean)otherOption.get("activeFlag");

              logger.info("current schedule active switch, flowLevel=" + activeFlag);
              if (null == activeFlag) {
                activeFlag = true;
              }
              if (activeFlag) {
                action.doAction();
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
      final long oldNextCheckTime = t.getNextCheckTime();
      if (t.isResetOnTrigger()) {
        t.resetTriggerConditions();

        // FIXME If the scheduled schedule is only executed once (such as the schedule specific to the year, month, and day),
        //  the scheduled schedule needs to be terminated; otherwise, the scheduled schedule will be triggered periodically.
        //  As a solution, after the checkertime does not change, set the scheduled scheduling status to EXPIRED.
        if(oldNextCheckTime == t.getNextCheckTime()){
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
