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

$.namespace('azkaban');

var schedulePanelView;
azkaban.SchedulePanelView = Backbone.View.extend({
  events: {
    "click #schedule-button": "scheduleFlow"
  },

  initialize: function (settings) {
  },

  render: function () {
  },

  showSchedulePanel: function () {
    $('#schedule-modal').modal();
  },

  hideSchedulePanel: function () {
    $('#schedule-modal').modal("hide");
  },

  scheduleFlow: function () {
    var scheduleURL = "/schedule"
    var scheduleData = flowExecuteDialogView.getExecutionOptionData();

    console.log("Creating schedule for " + projectName + "." + scheduleData.flow);

    var currentMomentTime = moment();
    var scheduleTime = currentMomentTime.utc().format('h,mm,A,') + "UTC";
    var scheduleDate = currentMomentTime.format('MM/DD/YYYY');

    scheduleData.ajax = "scheduleCronFlow";
    scheduleData.projectName = projectName;
    scheduleData.cronExpression = "0 " + $('#cron-output').val();

    // Currently, All cron expression will be based on server timezone.
    // Later we might implement a feature support cron under various timezones, depending on the future use cases.
    // scheduleData.cronTimezone = timezone;

    console.log("current Time = " + scheduleDate + "  " + scheduleTime);
    console.log("cronExpression = " + scheduleData.cronExpression);
    var retSignal = validateQuartzStr(scheduleData.cronExpression);

    if (retSignal == "NUM_FIELDS_ERROR") {
      var tipMsg1 = ""
      if (langType === "en_US") {
        tipMsg1 = "Cron Syntax Error, A valid Quartz Cron Expression should have 6 or 7 fields.";
      } else {
        tipMsg1 = "Cron 表达式错误, 一个有效的Cron表达式至少有6个或者7个属性."
      }
      alert(tipMsg1);
      return;
    } else if (retSignal == "DOW_DOM_STAR_ERROR") {
      var tipMsg2 = ""
      if (langType === "en_US") {
        tipMsg2 = "Cron Syntax Error, Currently Quartz doesn't support specifying both a day-of-week and a day-of-month value"
          + "(you must use the ‘?’ character in one of these fields). <a href=\"http://www.quartz-scheduler.org/documentation/quartz-2.x/tutorials/crontrigger.html\" target=\"_blank\">Detailed Explanation</a>";
      } else {
        tipMsg2 = "Cron 表达式错误, 月的某日和周的某天不能同时有值" + "(你必须将其中一个的值改为 ‘?’). <a href=\"http://www.quartz-scheduler.org/documentation/quartz-2.x/tutorials/crontrigger.html\" target=\"_blank\">详情请见</a>";
      }
      alert(tipMsg2)
      return;
    }

    var successHandler = function (data) {
      if (data.error) {
        alert(data.error);
        //schedulePanelView.hideSchedulePanel();
        // messageDialogView.show(wtssI18n.view.timingSchedulingFailed, data.error);
      }
      else {
        schedulePanelView.hideSchedulePanel();
        messageDialogView.show(wtssI18n.view.timingScheduling, data.message, function () {
          window.location.href = scheduleURL;
        });
      }
    };

    $.post(scheduleURL, scheduleData, successHandler, "json");
  }
});

$(function () {
  schedulePanelView = new azkaban.SchedulePanelView({
    el: $('#schedule-modal')
  });

  $(document).keyup(function (e) {
    // escape key maps to keycode `27`
    if (e.keyCode == 27) {
      schedulePanelView.hideSchedulePanel();
    }
  });

  // To compute the current timezone's time offset against UTC.
  // Currently not useful.
  // var TimeZoneOffset = new Date().toString().match(/([-\+][0-9]+)\s/)[1];

  $('#timeZoneID').html(timezone);

  updateOutput();
  $("#clearCron").click(function () {
    $('#cron-output').val("* * ? * *");
    resetLabelColor();
    $("#minute_input").val("*");
    $("#hour_input").val("*");
    $("#dom_input").val("?");
    $("#month_input").val("*");
    $("#dow_input").val("*");
    $("#schedule-year_input").val("");
    $(cron_translate_id).text("")
    $(cron_translate_warning_id).text("")
    $('#nextRecurId').html("");

    while ($("#instructions tbody tr:last").index() >= 4) {
      $("#instructions tbody tr:last").remove();
    }

    updateOutput();
  });

  $("#minute_input").click(function () {
    while ($("#instructions tbody tr:last").index() >= 4) {
      $("#instructions tbody tr:last").remove();
    }
    resetLabelColor();
    $("#min_label").css("color", "red");
    $('#instructions tbody').append($("#instructions tbody tr:first").clone());
    $('#instructions tbody tr:last th').html("0-59");
    $('#instructions tbody tr:last td').html(wtssI18n.view.allowedValue);
  });

  $("#hour_input").click(function () {
    while ($("#instructions tbody tr:last").index() >= 4) {
      $("#instructions tbody tr:last").remove();
    }
    resetLabelColor();
    $("#hour_label").css("color", "red");
    $('#instructions tbody').append($("#instructions tbody tr:first").clone());
    $('#instructions tbody tr:last th').html("0-23");
    $('#instructions tbody tr:last td').html(wtssI18n.view.allowedValue);
  });

  $("#dom_input").click(function () {
    while ($("#instructions tbody tr:last").index() >= 4) {
      $("#instructions tbody tr:last").remove();
    }
    resetLabelColor();
    $("#dom_label").css("color", "red");
    $('#instructions tbody').append($("#instructions tbody tr:first").clone());
    $('#instructions tbody tr:last th').html("1-31");
    $('#instructions tbody tr:last td').html(wtssI18n.view.allowedValue);

  });

  $("#month_input").click(function () {
    while ($("#instructions tbody tr:last").index() >= 4) {
      $("#instructions tbody tr:last").remove();
    }
    resetLabelColor();
    $("#mon_label").css("color", "red");
    $('#instructions tbody').append($("#instructions tbody tr:first").clone());
    $('#instructions tbody tr:last th').html("1-12");
    $('#instructions tbody tr:last td').html(wtssI18n.view.allowedValue);
  });

  $("#dow_input").click(function () {
    while ($("#instructions tbody tr:last").index() >= 4) {
      $("#instructions tbody tr:last").remove();
    }
    resetLabelColor();
    $("#dow_label").css("color", "red");

    $('#instructions tbody').append($("#instructions tbody tr:first").clone());
    $('#instructions tbody tr:last th').html("1-7");
    $('#instructions tbody tr:last td').html(wtssI18n.view.mondayToSunday);

  });
  $("#schedule-year_input").click(function () {
    while ($("#instructions tbody tr:last").index() >= 4) {
      $("#instructions tbody tr:last").remove();
    }
    resetLabelColor();
    $("#schedule-year_label").css("color", "red");

    $('#instructions tbody').append($("#instructions tbody tr:first").clone());
    $('#instructions tbody tr:last th').html("");
    $('#instructions tbody tr:last td').html("This field is optional");
  });
});

function resetLabelColor () {
  $("#min_label").css("color", "black");
  $("#hour_label").css("color", "black");
  $("#dom_label").css("color", "black");
  $("#mon_label").css("color", "black");
  $("#dow_label").css("color", "black");
  $("#schedule-year_label").css("color", "black");
}

var cron_minutes_id = "#minute_input";
var cron_hours_id = "#hour_input";
var cron_dom_id = "#dom_input";
var cron_months_id = "#month_input";
var cron_dow_id = "#dow_input";
var cron_year_id = "#schedule-year_input";
var cron_output_id = "#cron-output";
var cron_translate_id = "#cronTranslate";
var cron_translate_warning_id = "#translationWarning";

function updateOutput () {
  $(cron_output_id).val($(cron_minutes_id).val() + " " + $(cron_hours_id).val() + " " +
    $(cron_dom_id).val() + " " + $(cron_months_id).val() + " " +
    $(cron_dow_id).val() + " " + $(cron_year_id).val()
  );
  updateExpression();
}

function updateExpression () {
  $('#nextRecurId').html("");

  // transformFromQuartzToUnixCron is defined in util/date.js
  var unixCronStr = transformFromQuartzToUnixCron($(cron_output_id).val());
  console.log("Parsed Unix cron = " + unixCronStr);
  var laterCron = later.parse.cron(unixCronStr);

  //Get the current time given the server timezone.
  var serverTime = moment().tz(timezone);
  console.log("serverTime = " + serverTime.format());
  var now1Str = serverTime.format();

  //Get the server Timezone offset against UTC (e.g. if timezone is PDT, it should be -07:00)
  // var timeZoneOffset = now1Str.substring(now1Str.length-6, now1Str.length);
  // console.log("offset = " + timeZoneOffset);

  //Transform the moment time to UTC Date time (required by later.js)
  var serverTimeInJsDateFormat = new Date();
  serverTimeInJsDateFormat.setUTCHours(serverTime.get('hour'),
    serverTime.get('minute'), 0, 0);
  serverTimeInJsDateFormat.setUTCMonth(serverTime.get('month'),
    serverTime.get('date'));

  //Calculate the following 10 occurrences based on the current server time.
  for (var i = 9; i >= 0; i--) {
    // The logic is a bit tricky here. since later.js only support UTC Date (javascript raw library).
    // We transform from current browser-timezone-time to Server timezone.
    // Then we let serverTimeInJsDateFormat is equal to the server time.
    var occurrence = later.schedule(laterCron).next(1, serverTimeInJsDateFormat);

    if (occurrence) {
      var strTime = JSON.stringify(occurrence);

      // Get the time. The original occurrence time string is like: "2016-09-09T05:00:00.999",
      // We trim the string to ignore milliseconds.

      var nextTime = filterXSS('<li style="color:DarkGreen">' + strTime.substring(1, strTime.length - 6) + '</li>', { 'whiteList': { 'li': ['style'] } });
      $('#nextRecurId').append(nextTime);

      serverTimeInJsDateFormat = occurrence;

      // Add 10 seconds to exclude startDate from the next occurrences
      serverTimeInJsDateFormat.setSeconds(serverTimeInJsDateFormat.getSeconds() + 10);
    } else {
      console.log("occurrence is null");
      break;
    }
  }

  $('#nextRecurLabel').html("Next " + $('#nextRecurId li').length +
    " scheduled executions for this cron expression only:");
}
