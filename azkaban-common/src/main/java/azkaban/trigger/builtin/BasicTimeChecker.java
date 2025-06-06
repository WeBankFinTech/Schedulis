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

package azkaban.trigger.builtin;

import azkaban.trigger.ConditionChecker;
import azkaban.utils.DateUtils;
import azkaban.utils.Utils;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.joda.time.DateTime;
import org.joda.time.DateTimeUtils;
import org.joda.time.DateTimeZone;
import org.joda.time.ReadablePeriod;
import org.joda.time.format.DateTimeFormat;
import org.quartz.CronExpression;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class BasicTimeChecker implements ConditionChecker {

  public static final Logger LOG = LoggerFactory.getLogger(BasicTimeChecker.class);

  public static final String TYPE = "BasicTimeChecker";
  private final String id;
  private final long firstCheckTime;
  private final DateTimeZone timezone;
  private final ReadablePeriod period;
  private final String cronExpression;
  private final CronExpression cronExecutionTime;
  /**
   * 下次检查时间
   */
  private long nextCheckTime;
  private boolean isRecurring = true;
  private boolean skipPastChecks = true;
  private DateTime scheduleStartDate;
  private DateTime scheduleEndDate;
  private long lastExecTime = -1;

  /**
   * 错过的调度执行检查时间
   */
  private List<Long> missedCheckTimeBeforeNow = new ArrayList<>();

  /**
   * This constructor used when first create trigger condition from schedule, the nextCheckTime is
   * calculated based on cronExpression.
   */
  public BasicTimeChecker(final String id, final long firstCheckTime,
      final DateTimeZone timezone, final boolean isRecurring, final boolean skipPastChecks,
      final ReadablePeriod period, final String cronExpression, final Map<String, Object> otherOption) {
    this.id = id;
    this.firstCheckTime = firstCheckTime;
    this.timezone = timezone;
    this.isRecurring = isRecurring;
    this.skipPastChecks = skipPastChecks;
    this.period = period;
    this.nextCheckTime = firstCheckTime;
    this.cronExpression = cronExpression;
    this.cronExecutionTime = Utils.parseCronExpression(cronExpression, timezone);
    this.scheduleStartDate = DateUtils.parseDate(otherOption.get("scheduleStartDate"));
    this.scheduleEndDate = DateUtils.parseDate(otherOption.get("scheduleEndDate"));
    this.nextCheckTime = calculateNextCheckTime().nextValidCheckTimeFromNow;
  }

  /**
   * This constructor used when recover from DB or end checkers, thus nextCheckTime is specified
   * instead of calculated.
   */
  public BasicTimeChecker(final String id, final long firstCheckTime,
      final DateTimeZone timezone, final long nextCheckTime, final boolean isRecurring,
      final boolean skipPastChecks, final ReadablePeriod period, final String cronExpression) {
    this.id = id;
    this.firstCheckTime = firstCheckTime;
    this.timezone = timezone;
    this.nextCheckTime = nextCheckTime;
    this.isRecurring = isRecurring;
    this.skipPastChecks = skipPastChecks;
    this.period = period;
    this.cronExpression = cronExpression;
    this.cronExecutionTime = Utils.parseCronExpression(cronExpression, timezone);
  }

  public static BasicTimeChecker createFromJson(final Object obj) throws Exception {
    return createFromJson((HashMap<String, Object>) obj);
  }

  public static BasicTimeChecker createFromJson(final HashMap<String, Object> obj)
      throws Exception {
    final Map<String, Object> jsonObj = (HashMap<String, Object>) obj;
    if (!jsonObj.get("type").equals(TYPE)) {
      throw new Exception("Cannot create checker of " + TYPE + " from "
          + jsonObj.get("type"));
    }
    final Long firstCheckTime = Long.valueOf((String) jsonObj.get("firstCheckTime"));
    final String timezoneId = (String) jsonObj.get("timezone");
    final long nextCheckTime = Long.valueOf((String) jsonObj.get("nextCheckTime"));
    final DateTimeZone timezone = DateTimeZone.forID(timezoneId);
    final boolean isRecurring = Boolean.valueOf((String) jsonObj.get("isRecurring"));
    final boolean skipPastChecks =
        Boolean.valueOf((String) jsonObj.get("skipPastChecks"));
    final ReadablePeriod period =
        Utils.parsePeriodString((String) jsonObj.get("period"));
    final String id = (String) jsonObj.get("id");
    final String cronExpression = (String) jsonObj.get("cronExpression");

    final BasicTimeChecker checker =
        new BasicTimeChecker(id, firstCheckTime, timezone, nextCheckTime,
            isRecurring, skipPastChecks, period, cronExpression);
    checker.setScheduleStartDate(DateUtils.parseDate(jsonObj.get("scheduleStartDate")));
    checker.setScheduleEndDate(DateUtils.parseDate(jsonObj.get("scheduleEndDate")));
    checker.setLastExecTime(nextCheckTime);
    // todo: remove skipPastChecks, this is not in use
    if (skipPastChecks) {
      checker.updateNextCheckTime();
    }
    return checker;
  }

  public long getFirstCheckTime() {
    return this.firstCheckTime;
  }

  public DateTimeZone getTimeZone() {
    return this.timezone;
  }

  public boolean isRecurring() {
    return this.isRecurring;
  }

  public boolean isSkipPastChecks() {
    return this.skipPastChecks;
  }

  public ReadablePeriod getPeriod() {
    return this.period;
  }

  @Override
  public long getNextCheckTime() {
    return this.nextCheckTime;
  }

  public String getCronExpression() {
    return this.cronExpression;
  }

  public List<Long> getMissedCheckTimeBeforeNow() {
    return missedCheckTimeBeforeNow;
  }

  @Override
  public Boolean eval() {
    return this.nextCheckTime < DateTimeUtils.currentTimeMillis();
  }

  /**
   * A metadata class records next check time from cronExpression.
   */
  private static class NextCheckTime {

    /**
     * nextValidTime from cronExpression, we filtered to get closest next valid time after now
     */
    long nextValidCheckTimeFromNow;

    /**
     * missed valid checkTime between last checkTime and now.
     */
    List<Long> missedCheckTimeBeforeNow = new ArrayList<>();
  }

  /**
   * Reset trigger when trigger fired associated action by updating next check time after now.
   */
  @Override
  public void reset() {
    NextCheckTime nextCheckTimeObj = calculateNextCheckTime();
    this.nextCheckTime = nextCheckTimeObj.nextValidCheckTimeFromNow;
    if (nextCheckTimeObj.missedCheckTimeBeforeNow.size() > 0) {
      nextCheckTimeObj.missedCheckTimeBeforeNow.remove(
              nextCheckTimeObj.missedCheckTimeBeforeNow.size() - 1);
    }
    this.missedCheckTimeBeforeNow = nextCheckTimeObj.missedCheckTimeBeforeNow;
  }

  @Override
  public String getId() {
    return this.id;
  }

  @Override
  public String getType() {
    return TYPE;
  }

  public void setScheduleStartDate(DateTime scheduleStartDate) {
    this.scheduleStartDate = scheduleStartDate;
  }

  public void setScheduleEndDate(DateTime scheduleEndDate) {
    this.scheduleEndDate = scheduleEndDate;
  }

  public long getLastExecTime() {
    return lastExecTime;
  }

  public void setLastExecTime(long lastExecTime) {
    this.lastExecTime = lastExecTime;
  }

  @Override
  public BasicTimeChecker fromJson(final Object obj) throws Exception {
    return createFromJson(obj);
  }

  /**
   * Only used on reload trigger from DB to recover Trigger object. missedCheckTimes would count all
   * previous missed checks including the first one. For example: nextCheckTime from last update is
   * 20:00, the schedule is triggered every minute. Now is system time is 20:00:30, restart process
   * reloads last check point from DB. It calculates next check time, the previous nextCheckTime
   * would be overwritten by the next valid check time after Now which is 20:01, 20:00 would be
   * marked as a missed schedule.
   */
  private void updateNextCheckTime() {
    NextCheckTime nextCheckTimeObj = calculateNextCheckTime();
    this.nextCheckTime = nextCheckTimeObj.nextValidCheckTimeFromNow;
    this.missedCheckTimeBeforeNow = nextCheckTimeObj.missedCheckTimeBeforeNow;
  }

  /**
   *
   */
  private NextCheckTime calculateNextCheckTime() {
    DateTime date = new DateTime(this.nextCheckTime).withZone(this.timezone);
    LOG.debug("calculate next check time, current nextCheckTime is {} by cron {}", date,
        this.cronExpression);
    NextCheckTime nextCheckTimeObj = new NextCheckTime();
    int count = 0;
    while (!date.isAfterNow()) {
      nextCheckTimeObj.missedCheckTimeBeforeNow.add(date.getMillis());
      LOG.debug(
          "nextCheckTime {} by {} is before Now, it will be overrides, adding into missedCheckTimeBeforeNow List, "
              + "now the list: {}", date, this.cronExpression,
          nextCheckTimeObj.missedCheckTimeBeforeNow);
      if (count > 100000) {
        throw new IllegalStateException(
            "100000 increments of period did not get to present time.");
      }
      // todo: 关于调度开始时间处理位置
      if (this.scheduleStartDate != null && this.scheduleStartDate.isAfter(date)) {
        date = this.scheduleStartDate;
      }
      if (this.period == null && this.cronExpression == null) {
        break;
      } else if (this.cronExecutionTime != null) {
        final Date nextDate = this.cronExecutionTime.getNextValidTimeAfter(date.toDate());
        // Some Cron Expressions possibly do not have follow-up occurrences
        if (nextDate != null) {
          date = new DateTime(nextDate);
        } else {
          nextCheckTimeObj.nextValidCheckTimeFromNow = -1;
          nextCheckTime = nextCheckTimeObj.nextValidCheckTimeFromNow;
          return nextCheckTimeObj;
        }
      } else {
        date = date.plus(this.period);
      }
      if (this.scheduleEndDate != null && this.scheduleEndDate.isBefore(date)) {
        // todo: 关于调度结束时间的处理
        nextCheckTimeObj.nextValidCheckTimeFromNow = -1;
        nextCheckTime = nextCheckTimeObj.nextValidCheckTimeFromNow;
        return nextCheckTimeObj;
      }
      count += 1;
    }

    // It's possible the above while loop exits on break condition instead of that "date refreshed after Now"
    // this happens when trigger expires before NOW, in such case we remove all missed checkTime as they're meaningless
    if (!date.isAfterNow()) {
      nextCheckTimeObj.missedCheckTimeBeforeNow.clear();
    }
    nextCheckTimeObj.nextValidCheckTimeFromNow = date.getMillis();
    LOG.debug("Done calculation, current nextCheckTime {} by cron {} and missScheduleList {}", date,
        this.cronExpression, nextCheckTimeObj.missedCheckTimeBeforeNow);
    return nextCheckTimeObj;
  }

  @Override
  public Object getNum() {
    return null;
  }

  @Override
  public Object toJson() {
    final Map<String, Object> jsonObj = new HashMap<>();
    jsonObj.put("type", TYPE);
    jsonObj.put("firstCheckTime", String.valueOf(this.firstCheckTime));
    jsonObj.put("timezone", this.timezone.getID());
    jsonObj.put("nextCheckTime", String.valueOf(this.nextCheckTime));
    jsonObj.put("isRecurring", String.valueOf(this.isRecurring));
    jsonObj.put("skipPastChecks", String.valueOf(this.skipPastChecks));
    jsonObj.put("period", Utils.createPeriodString(this.period));
    jsonObj.put("id", this.id);
    jsonObj.put("cronExpression", this.cronExpression);
    if (this.scheduleStartDate != null) {
      jsonObj.put("scheduleStartDate",
          this.scheduleStartDate.toString(DateTimeFormat.forPattern("yyyy-MM-dd HH:mm")));
    }
    if (this.scheduleEndDate != null) {
      jsonObj.put("scheduleEndDate",
          this.scheduleEndDate.toString(DateTimeFormat.forPattern("yyyy-MM-dd HH:mm")));
    }

    return jsonObj;
  }

  @Override
  public void stopChecker() {
    return;
  }

  @Override
  public void setContext(final Map<String, Object> context) {
  }

}
