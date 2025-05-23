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
import azkaban.utils.DateUtils;
import azkaban.utils.Pair;
import azkaban.utils.Utils;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.datatype.joda.deser.DateTimeZoneDeserializer;
import com.fasterxml.jackson.datatype.joda.ser.DateTimeZoneSerializer;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.joda.time.Days;
import org.joda.time.DurationFieldType;
import org.joda.time.Hours;
import org.joda.time.Minutes;
import org.joda.time.Months;
import org.joda.time.ReadablePeriod;
import org.joda.time.Seconds;
import org.joda.time.Weeks;
import org.quartz.CronExpression;

public class Schedule {

  private final int projectId;
  private final String projectName;
  private final String flowName;
  private final long firstSchedTime;
  private final long endSchedTime;
  @JsonDeserialize(using = DateTimeZoneDeserializer.class)
  @JsonSerialize(using = DateTimeZoneSerializer.class)
  private final DateTimeZone timezone;
  private final long lastModifyTime;
  private final long lastModifyConfiguration;
  private final ReadablePeriod period;
  private final String submitUser;
  private final String status;
  private final long submitTime;
  private final String cronExpression;
  private final boolean skipPastOccurrences = true;
  private int scheduleId;
  private long nextExecTime;
  private ExecutionOptions executionOptions;
  private List<SlaOption> slaOptions;
  private String comment;
  private long lastExecTime;

  private boolean autoSubmit;

  // FIXME Other setting parameters are convenient for future expansion.
  private Map<String, Object> otherOption = new HashMap<>();

  public Schedule(final int scheduleId,
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
                  final ExecutionOptions executionOptions,
                  final List<SlaOption> slaOptions,
                  final String cronExpression, long lastModifyConfiguration) {
    this.scheduleId = scheduleId;
    this.projectId = projectId;
    this.projectName = projectName;
    this.flowName = flowName;
    this.firstSchedTime = firstSchedTime;
    this.endSchedTime = endSchedTime;
    this.timezone = timezone;
    this.lastModifyTime = lastModifyTime;
    this.period = period;
    this.nextExecTime = nextExecTime;
    this.submitUser = submitUser;
    this.status = status;
    this.submitTime = submitTime;
    this.executionOptions = executionOptions;
    this.slaOptions = slaOptions;
    this.cronExpression = cronExpression;
    this.lastModifyConfiguration = lastModifyConfiguration;
  }

  // FIXME Added construction method.
  public Schedule(final int scheduleId,
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
                  final ExecutionOptions executionOptions,
                  final List<SlaOption> slaOptions,
                  final String cronExpression,
                  final Map<String, Object> otherOption, long lastModifyConfiguration) {
    this.scheduleId = scheduleId;
    this.projectId = projectId;
    this.projectName = projectName;
    this.flowName = flowName;
    this.firstSchedTime = firstSchedTime;
    this.endSchedTime = endSchedTime;
    this.timezone = timezone;
    this.lastModifyTime = lastModifyTime;
    this.period = period;
    this.nextExecTime = nextExecTime;
    this.submitUser = submitUser;
    this.status = status;
    this.submitTime = submitTime;
    this.executionOptions = executionOptions;
    this.slaOptions = slaOptions;
    this.cronExpression = cronExpression;
    this.otherOption = otherOption;
    this.lastModifyConfiguration = lastModifyConfiguration;
  }

  public Schedule(final int scheduleId,
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
                  final ExecutionOptions executionOptions,
                  final List<SlaOption> slaOptions,
                  final String cronExpression,
                  final Map<String, Object> otherOption,
                  final String comment, boolean autoSubmit, long lastModifyConfiguration) {
    this.scheduleId = scheduleId;
    this.projectId = projectId;
    this.projectName = projectName;
    this.flowName = flowName;
    this.firstSchedTime = firstSchedTime;
    this.endSchedTime = endSchedTime;
    this.timezone = timezone;
    this.lastModifyTime = lastModifyTime;
    this.period = period;
    this.nextExecTime = nextExecTime;
    this.submitUser = submitUser;
    this.status = status;
    this.submitTime = submitTime;
    this.executionOptions = executionOptions;
    this.slaOptions = slaOptions;
    this.cronExpression = cronExpression;
    this.otherOption = otherOption;
    this.comment = comment;
    this.autoSubmit = autoSubmit;
    this.lastModifyConfiguration = lastModifyConfiguration;
  }

  public static ReadablePeriod parsePeriodString(final String periodStr) {
    final ReadablePeriod period;
    final char periodUnit = periodStr.charAt(periodStr.length() - 1);
    if (periodUnit == 'n') {
      return null;
    }

    final int periodInt =
            Integer.parseInt(periodStr.substring(0, periodStr.length() - 1));
    switch (periodUnit) {
      case 'M':
        period = Months.months(periodInt);
        break;
      case 'w':
        period = Weeks.weeks(periodInt);
        break;
      case 'd':
        period = Days.days(periodInt);
        break;
      case 'h':
        period = Hours.hours(periodInt);
        break;
      case 'm':
        period = Minutes.minutes(periodInt);
        break;
      case 's':
        period = Seconds.seconds(periodInt);
        break;
      default:
        throw new IllegalArgumentException("Invalid schedule period unit '"
                + periodUnit);
    }

    return period;
  }

  public static String createPeriodString(final ReadablePeriod period) {
    String periodStr = "n";

    if (period == null) {
      return "n";
    }

    if (period.get(DurationFieldType.months()) > 0) {
      final int months = period.get(DurationFieldType.months());
      periodStr = months + "M";
    } else if (period.get(DurationFieldType.weeks()) > 0) {
      final int weeks = period.get(DurationFieldType.weeks());
      periodStr = weeks + "w";
    } else if (period.get(DurationFieldType.days()) > 0) {
      final int days = period.get(DurationFieldType.days());
      periodStr = days + "d";
    } else if (period.get(DurationFieldType.hours()) > 0) {
      final int hours = period.get(DurationFieldType.hours());
      periodStr = hours + "h";
    } else if (period.get(DurationFieldType.minutes()) > 0) {
      final int minutes = period.get(DurationFieldType.minutes());
      periodStr = minutes + "m";
    } else if (period.get(DurationFieldType.seconds()) > 0) {
      final int seconds = period.get(DurationFieldType.seconds());
      periodStr = seconds + "s";
    }

    return periodStr;
  }

  public ExecutionOptions getExecutionOptions() {
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

  public String getScheduleName() {
    return this.projectName + "." + this.flowName + " (" + this.projectId + ")";
  }

  public Map<String, Object> getOtherOption() {
    return otherOption;
  }

  public void setOtherOption(Map<String, Object> otherOption) {
    this.otherOption = otherOption;
  }

  @Override
  public String toString() {

    final String underlying =
            this.projectName + "." + this.flowName + " (" + this.projectId + ")"
                    + " to be run at (starting) " + new DateTime(
                    this.firstSchedTime).toDateTimeISO();
    if (this.period == null && this.cronExpression == null) {
      return underlying + " non-recurring";
    } else if (this.cronExpression != null) {
      return underlying + " with CronExpression {" + this.cronExpression + "}";
    } else {
      return underlying + " with precurring period of " + createPeriodString(this.period);
    }
  }

  public Pair<Integer, String> getScheduleIdentityPair() {
    return new Pair<>(getProjectId(), getFlowName());
  }

  public int getScheduleId() {
    return this.scheduleId;
  }

  public void setScheduleId(final int scheduleId) {
    this.scheduleId = scheduleId;
  }

  public int getProjectId() {
    return this.projectId;
  }

  public String getProjectName() {
    return this.projectName;
  }

  public String getFlowName() {
    return this.flowName;
  }

  public long getFirstSchedTime() {
    return this.firstSchedTime;
  }

  public DateTimeZone getTimezone() {
    return this.timezone;
  }

  public long getLastModifyTime() {
    return this.lastModifyTime;
  }

  public ReadablePeriod getPeriod() {
    return this.period;
  }

  public long getNextExecTime() {
    return this.nextExecTime;
  }

  public void setNextExecTime(final long nextExecTime) {
    this.nextExecTime = nextExecTime;
  }

  public String getSubmitUser() {
    return this.submitUser;
  }

  public String getStatus() {
    return this.status;
  }

  public long getSubmitTime() {
    return this.submitTime;
  }

  public String getCronExpression() {
    return this.cronExpression;
  }

  public boolean updateTime() {
    if (new DateTime(this.nextExecTime).isAfterNow()) {
      return true;
    }

    if (this.cronExpression == null && this.period == null) {
      return false;
    }

    DateTime scheduleStartDate = DateUtils.parseDate(otherOption.get("scheduleStartDate"));
    DateTime scheduleEndDate = DateUtils.parseDate(otherOption.get("scheduleEndDate"));

    if (scheduleStartDate != null && scheduleStartDate.isAfter(this.nextExecTime)) {
      this.nextExecTime = scheduleStartDate.getMillis();
    }

    if (this.cronExpression != null) {
      getNextCronRuntime(this.nextExecTime, this.timezone,
              Utils.parseCronExpression(this.cronExpression, this.timezone));
    }

    if (this.period != null) {
      getNextRuntime(this.nextExecTime, this.timezone, this.period);
    }

    if (scheduleEndDate != null && scheduleEndDate.isBefore(this.nextExecTime)) {
      this.nextExecTime = -1;
    }

    return true;
  }

  private void getNextRuntime(final long scheduleTime, final DateTimeZone timezone,
                              final ReadablePeriod period) {
    final DateTime now = new DateTime();
    DateTime date = new DateTime(scheduleTime).withZone(timezone);
    int count = 0;
    while (!now.isBefore(date)) {
      if (count > 100000) {
        throw new IllegalStateException(
                "100000 increments of period did not get to present time.");
      }

      if (period == null) {
        break;
      } else {
        date = date.plus(period);
      }

      count += 1;
    }

    this.nextExecTime = date.getMillis();
  }

  /**
   * @param scheduleTime represents the time when Schedule Servlet receives the Cron Schedule API
   * call.
   * @param timezone is always UTC (after 3.1.0)
   * @return the First Scheduled DateTime to run this flow.
   */
  private void getNextCronRuntime(final long scheduleTime, final DateTimeZone timezone,
                                  final CronExpression ce) {

    Date date = new DateTime(scheduleTime).withZone(timezone).toDate();
    if (ce != null) {
      date = ce.getNextValidTimeAfter(date);
    }
    if (null != date) {
      this.nextExecTime = date.getTime();
    }
  }

  public Map<String, Object> optionsToObject() {
    if (this.executionOptions != null) {
      final HashMap<String, Object> schedObj = new HashMap<>();

      if (this.executionOptions != null) {
        schedObj.put("executionOptions", this.executionOptions.toObject());
      }

      if (this.slaOptions != null) {
        final List<Object> slaOptionsObject = new ArrayList<>();
        for (final SlaOption sla : this.slaOptions) {
          slaOptionsObject.add(sla.toObject());
        }
        schedObj.put("slaOptions", slaOptionsObject);
      }

      return schedObj;
    }
    return null;
  }

  public void createAndSetScheduleOptions(final Object obj) {
    final HashMap<String, Object> schedObj = (HashMap<String, Object>) obj;
    if (schedObj.containsKey("executionOptions")) {
      final ExecutionOptions execOptions =
              ExecutionOptions.createFromObject(schedObj.get("executionOptions"));
      this.executionOptions = execOptions;
    } else if (schedObj.containsKey("flowOptions")) {
      final ExecutionOptions execOptions =
              ExecutionOptions.createFromObject(schedObj.get("flowOptions"));
      this.executionOptions = execOptions;
      execOptions.setConcurrentOption(ExecutionOptions.CONCURRENT_OPTION_SKIP);
    } else {
      this.executionOptions = new ExecutionOptions();
      this.executionOptions
              .setConcurrentOption(ExecutionOptions.CONCURRENT_OPTION_SKIP);
    }

    if (schedObj.containsKey("slaOptions")) {
      final List<Object> slaOptionsObject = (List<Object>) schedObj.get("slaOptions");
      final List<SlaOption> slaOptions = new ArrayList<>();
      for (final Object slaObj : slaOptionsObject) {
        slaOptions.add(SlaOption.fromObject(slaObj));
      }
      this.slaOptions = slaOptions;
    }

  }

  public boolean isRecurring() {
    return this.period != null || this.cronExpression != null;
  }

  public boolean skipPastOccurrences() {
    return this.skipPastOccurrences;
  }

  public long getEndSchedTime() {
    return this.endSchedTime;
  }

  public String getComment() {
    return comment;
  }

  public void setComment(String comment) {
    this.comment = comment;
  }

  public long getLastExecTime() {
    return lastExecTime;
  }

  public void setLastExecTime(long lastExecTime) {
    this.lastExecTime = lastExecTime;
  }

  public boolean isAutoSubmit() {
    return autoSubmit;
  }

  public void setAutoSubmit(boolean autoSubmit) {
    this.autoSubmit = autoSubmit;
  }

  public long getLastModifyConfiguration() {
    return lastModifyConfiguration;
  }
}
