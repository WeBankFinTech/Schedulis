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

import azkaban.Constants;
import azkaban.executor.ExecutionOptions;
import azkaban.server.AbstractAzkabanServer;
import azkaban.sla.OvertimeScheduleScanner;
import azkaban.sla.SlaOption;
import azkaban.trigger.TriggerAgent;
import azkaban.trigger.TriggerStatus;
import azkaban.utils.Pair;
import azkaban.utils.Props;
import org.joda.time.DateTimeZone;
import org.joda.time.ReadablePeriod;
import org.joda.time.format.DateTimeFormat;
import org.joda.time.format.DateTimeFormatter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import java.util.*;
import java.util.stream.Collectors;

import static azkaban.Constants.ConfigurationKeys.*;

/**
 * The ScheduleManager stores and executes the schedule. It uses a single thread instead and waits
 * until correct loading time for the flow. It will not remove the flow from the schedule when it is
 * run, which can potentially allow the flow to and overlap each other.
 * <p>
 * TODO kunkun-tang: When new AZ quartz Scheduler comes, we will remove this class.
 */
public class ScheduleManager implements TriggerAgent {

    public static final String SIMPLE_TIME_TRIGGER = "SimpleTimeTrigger";
    private static final Logger logger = LoggerFactory.getLogger(ScheduleManager.class);
    private final DateTimeFormatter dateFormat = DateTimeFormat.forPattern("MM-dd-yyyy HH:mm:ss:SSS");
    private final ScheduleLoader loader;

    private final Map<Integer, Schedule> scheduleIDMap = new LinkedHashMap<>();
    private final Map<Pair<Integer, String>, Schedule> scheduleIdentityPairMap = new LinkedHashMap<>();

    private int scheduleIDMapRefreshTime = 60;
    // 定时调度生效系统级别开关
    private boolean scheduleCache = true;

    private final boolean queryServerSwitch;

    private final OvertimeScheduleScanner overtimeScheduleScanner;

    /**
     * Give the schedule manager a loader class that will properly load the schedule.
     */
    @Inject
    public ScheduleManager(final ScheduleLoader loader, final OvertimeScheduleScanner overtimeScheduleScanner) {
        this.loader = loader;
        Props azkabanProperties = AbstractAzkabanServer.getAzkabanProperties();
        this.scheduleIDMapRefreshTime = azkabanProperties.getInt(WTSS_QUERY_SERVER_SCHEDULE_CACHE_REFRESH_PERIOD, 60);
        this.scheduleCache = azkabanProperties.getBoolean(WTSS_QUERY_SCHEDULE_CACHE_ENABLE, false);
        if (scheduleCache) {
            logger.warn("schedule cache, start refresh ScheduleIDMap timer");
            refreshScheduleIDMap();
        }
        this.overtimeScheduleScanner = overtimeScheduleScanner;
        queryServerSwitch = azkabanProperties.getBoolean(WTSS_QUERY_SERVER_ENABLE, false);

    }

    @Override
    public void start() {

        try {
            List<Schedule> schedules = this.loader.loadAllSchedules();
            refreshLocal(schedules);

            if (queryServerSwitch) {
                overtimeScheduleScanner.start();
            }
        } catch (ScheduleManagerException e) {
            logger.warn(
                    "failed to init ScheduleManager, it might result in schedule not loaded into "
                            + "memory. ");
        }

    }



    private void refreshScheduleIDMap() {
        Timer timer = new Timer();
        TimerTask task = new TimerTask() {
            @Override
            public void run() {
                logger.info("Start to refresh ScheduleIDMap");
                try {
                    List<Schedule> schedules = loader.loadAllSchedules();
                    Set<Integer> newScheduleId = schedules.stream().map(schedule -> schedule.getScheduleId()).collect(Collectors.toSet());
                    scheduleIDMap.keySet().removeIf(key -> !newScheduleId.contains(key));
                    refreshLocal(schedules);
                } catch (Exception e) {
                    logger.error("Failed to refresh ScheduleIDMap", e);
                }
                logger.info("Finished to refresh ScheduleIDMap");
            }
        };
        timer.schedule(task, 1000L * 60L * 5L, 1000L * 60L * this.scheduleIDMapRefreshTime);
    }



    private void refreshLocal(List<Schedule> schedules) {
        for (Schedule schedule : schedules) {
            if (schedule.getStatus().equals(TriggerStatus.EXPIRED.toString())) {
                onScheduleExpire(schedule);
            } else {
                internalSchedule(schedule);
            }
        }

        // remove any schedules that are marked "removed" in triggerManager
        for (int id : this.loader.loadRemovedTriggers()) {
            Schedule schedule = this.scheduleIDMap.get(id);
            if (schedule != null) {
                Pair<Integer, String> scheduleIdentityPair = schedule.getScheduleIdentityPair();
                if (scheduleIdentityPair != null) {
                    this.scheduleIdentityPairMap.remove(scheduleIdentityPair);
                }
            }
            this.scheduleIDMap.remove(id);
        }
    }

    private synchronized void updateLocal() throws ScheduleManagerException {
        final List<Schedule> updates = this.loader.loadUpdatedSchedules();
        for (final Schedule s : updates) {
            if (s.getStatus().equals(TriggerStatus.EXPIRED.toString())) {
                onScheduleExpire(s);
            } else {
                internalSchedule(s);
            }
        }
    }

    private void onScheduleExpire(final Schedule s) {
        removeSchedule(s);
    }

    /**
     * Shutdowns the scheduler thread. After shutdown, it may not be safe to use it again.
     */
    @Override
    public void shutdown() {
        this.overtimeScheduleScanner.stop();
    }

    /**
     * Retrieves a copy of the list of schedules.
     */
    public synchronized List<Schedule> getSchedules() throws ScheduleManagerException {

        List<Schedule> updates = this.loader.loadUpdatedSchedules();
        refreshLocal(updates);
        return new ArrayList<>(this.scheduleIDMap.values());
    }

    /**
     * Returns the scheduled flow for the flow name
     */
    public Schedule getSchedule(final int projectId, final String flowId) throws ScheduleManagerException {
        updateLocal();
        return this.scheduleIdentityPairMap.get(new Pair<>(projectId, flowId));
    }

    /**
     * Returns the scheduled flow for the scheduleId
     *
     * @param scheduleId Schedule ID
     */
    public Schedule getSchedule(final int scheduleId) throws ScheduleManagerException {
        updateLocal();
        return this.scheduleIDMap.get(scheduleId);
    }

    public List<Schedule> idsToSchedules(List<Integer> scheduleIds, ArrayList<HashMap<String, String>> failedList) throws ScheduleManagerException {
        updateLocal();
        ArrayList<Schedule> schedules= new ArrayList<>();
        for (Integer scheduleId : scheduleIds) {
            if (null != this.scheduleIDMap.get(scheduleId)) {
                schedules.add(this.scheduleIDMap.get(scheduleId));
            } else {
                HashMap<String, String> map = new HashMap<>();
                map.put("scheduleId", String.valueOf(scheduleId));
                map.put("errorInfo", scheduleId + " does not exist");
                failedList.add(map);
            }
        }
        return schedules;
    }


    /**
     * Removes the flow from the schedule if it exists.
     */
    public synchronized void removeSchedule(final Schedule sched) {
        final Pair<Integer, String> identityPairMap = sched.getScheduleIdentityPair();

        final Schedule schedule = this.scheduleIdentityPairMap.get(identityPairMap);
        if (schedule != null) {
            this.scheduleIdentityPairMap.remove(identityPairMap);
        }

        this.scheduleIDMap.remove(sched.getScheduleId());

        try {
            this.loader.removeSchedule(sched);
        } catch (final ScheduleManagerException e) {
            logger.error("", e);
        }
    }

    public synchronized void removeSchedule(int scheduleId) {
        Schedule schedule = this.scheduleIDMap.get(scheduleId);
        if (schedule != null) {
            this.scheduleIdentityPairMap.remove(schedule.getScheduleIdentityPair());
            this.scheduleIDMap.remove(scheduleId);
        }
    }

    public Schedule scheduleFlow(final int scheduleId,
                                 final int projectId,
                                 final String projectName,
                                 final String flowName,
                                 final String status,
                                 final long firstSchedTime,
                                 final long endSchedTime,
                                 final DateTimeZone timezone,
                                 final ReadablePeriod period,
                                 final long lastModifyTime,
                                 final long nextExecTime,
                                 final long submitTime,
                                 final String submitUser,
                                 final ExecutionOptions execOptions,
                                 final List<SlaOption> slaOptions, long lastModifyConfiguration) {
        final Schedule sched = new Schedule(scheduleId, projectId, projectName, flowName, status,
                firstSchedTime, endSchedTime, timezone, period, lastModifyTime, nextExecTime,
                submitTime, submitUser, execOptions, slaOptions, null, lastModifyConfiguration);
        logger.info("Scheduling flow '" + sched.getScheduleName() + "' for "
                + this.dateFormat.print(firstSchedTime) + " with a period of " + (period == null ? "(non-recurring)" : period));

        insertSchedule(sched);
        return sched;
    }

    public Schedule cronScheduleFlow(final int scheduleId,
                                     final int projectId,
                                     final String projectName,
                                     final String flowName,
                                     final String status,
                                     final long firstSchedTime,
                                     final long endSchedTime,
                                     final DateTimeZone timezone,
                                     final long lastModifyTime,
                                     final long nextExecTime,
                                     final long submitTime,
                                     final String submitUser,
                                     final ExecutionOptions execOptions,
                                     final List<SlaOption> slaOptions,
                                     final String cronExpression,long lastModifyConfiguration) {
        final Schedule sched = new Schedule(scheduleId, projectId, projectName, flowName, status,
                firstSchedTime, endSchedTime, timezone, null, lastModifyTime, nextExecTime,
                submitTime, submitUser, execOptions, slaOptions, cronExpression, lastModifyConfiguration);
        logger.info("Scheduling flow '" + sched.getScheduleName() + "' for "
                + this.dateFormat.print(firstSchedTime) + " cron Expression = " + cronExpression);

        insertSchedule(sched);
        return sched;
    }

    public Schedule cronScheduleFlow(final int scheduleId,
                                     final int projectId,
                                     final String projectName,
                                     final String flowName,
                                     final String status,
                                     final long firstSchedTime,
                                     final long endSchedTime,
                                     final DateTimeZone timezone,
                                     final long lastModifyTime,
                                     final long nextExecTime,
                                     final long submitTime,
                                     final String submitUser,
                                     final ExecutionOptions execOptions,
                                     final List<SlaOption> slaOptions,
                                     final String cronExpression,
                                     final Map<String, Object> otherOption, long lastModifyConfiguration,
                                     final String comment) {
        final Schedule sched = new Schedule(scheduleId, projectId, projectName, flowName, status,
                firstSchedTime, endSchedTime, timezone, null, lastModifyTime, nextExecTime,
                submitTime, submitUser, execOptions, slaOptions, cronExpression, otherOption, lastModifyConfiguration);
        sched.setComment(comment);
        logger.info("Scheduling flow '" + sched.getScheduleName() + "' for "
                + this.dateFormat.print(firstSchedTime) + " cron Expression = " + cronExpression);

        insertSchedule(sched);
        return sched;
    }

    public Schedule cronScheduleFlow(final int scheduleId,
                                     final int projectId,
                                     final String projectName,
                                     final String flowName,
                                     final String status,
                                     final long firstSchedTime,
                                     final long endSchedTime,
                                     final DateTimeZone timezone,
                                     final long lastModifyTime,
                                     final long nextExecTime,
                                     final long submitTime,
                                     final String submitUser,
                                     final ExecutionOptions execOptions,
                                     final List<SlaOption> slaOptions,
                                     final String cronExpression,
                                     final Map<String, Object> otherOption,
                                     final String comment, boolean backExecuteOnceOnMiss, long lastModifyConfiguration) {
        final Schedule sched = new Schedule(scheduleId, projectId, projectName, flowName, status,
                firstSchedTime, endSchedTime, timezone, null, lastModifyTime, nextExecTime,
                submitTime, submitUser, execOptions, slaOptions, cronExpression, otherOption, comment,
                backExecuteOnceOnMiss,lastModifyConfiguration);
        logger.info("Scheduling flow '" + sched.getScheduleName() + "' for "
                + this.dateFormat.print(firstSchedTime) + " cron Expression = " + cronExpression
                + " with back execute " + (backExecuteOnceOnMiss ? "enabled" : "disabled"));

        insertSchedule(sched);
        return sched;
    }

    /**
     * Schedules the flow, but doesn't save the schedule afterwards.
     */
    private synchronized void internalSchedule(final Schedule s) {
        this.scheduleIDMap.put(s.getScheduleId(), s);
        this.scheduleIdentityPairMap.put(s.getScheduleIdentityPair(), s);
    }

    /**
     * Adds a flow to the schedule.
     */
    public synchronized void insertSchedule(final Schedule s) {
        final Schedule exist = this.scheduleIdentityPairMap.get(s.getScheduleIdentityPair());
        if (s.updateTime()) {
            try {
                if (exist == null) {//定时任务不存在 则插入数据库
                    this.loader.insertSchedule(s);
                    internalSchedule(s);
                } else {//定时任务已存在 更新数据库
                    s.setScheduleId(exist.getScheduleId());
                    //避免修改时覆盖ims上报设置
                    if (s.getOtherOption().get("scheduleImsSwitch") == null) {
                        s.getOtherOption().put("scheduleImsSwitch", exist.getOtherOption().get("scheduleImsSwitch"));
                    }

                    if (s.getOtherOption().get(Constants.SCHEDULE_MISSED_TIME) == null) {
                        s.getOtherOption().put(Constants.SCHEDULE_MISSED_TIME, exist.getOtherOption().get(Constants.SCHEDULE_MISSED_TIME));
                    }

                    this.loader.updateSchedule(s);
                    internalSchedule(s);//更新缓存
                }
            } catch (final ScheduleManagerException e) {
                logger.error("", e);
            }
        } else {
            logger.error("The provided schedule is non-recurring and the scheduled time already passed. " + s.getScheduleName());
        }
    }

    @Override
    public void loadTriggerFromProps(final Props props) throws ScheduleManagerException {
        throw new ScheduleManagerException("create " + getTriggerSource() + " from json not supported yet");
    }

    @Override
    public String getTriggerSource() {
        return SIMPLE_TIME_TRIGGER;
    }

    public long getTriggerInitTime() {
        return this.loader.getTriggerInitTime();
    }

}
