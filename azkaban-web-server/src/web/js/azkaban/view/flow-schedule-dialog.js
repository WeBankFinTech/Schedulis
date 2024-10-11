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

var flowScheduleDialogView;
azkaban.FlowScheduleDialogView = Backbone.View.extend({
  events: {
    "click .closeExecPanel": "hideScheduleOptionPanel",
    "click #schedule-flow-button": "scheduleFlow",
    //"click #schedule-edit-flow-button": "scheduleEditFlow",
  },

  initialize: function (settings) {
    this.model.bind('change:flowinfo', this.changeFlowInfo, this);
    $("#schedule-override-success-emails").click(function (evt) {
      if ($(this).is(':checked')) {
        $('#schedule-success-emails').attr('disabled', null);
      }
      else {
        $('#schedule-success-emails').attr('disabled', "disabled");
      }
    });

    $("#schedule-override-failure-emails").click(function (evt) {
      if ($(this).is(':checked')) {
        $('#schedule-failure-emails').attr('disabled', null);
      }
      else {
        $('#schedule-failure-emails').attr('disabled', "disabled");
      }
    });

  },

  render: function () {
  },

  getExecutionOptionData: function () {
    var failureAction = $('#schedule-failure-action').val();
    var failureEmails = $('#schedule-failure-emails').val();
    var successEmails = $('#schedule-success-emails').val();
    var notifyFailureFirst = $('#schedule-notify-failure-first').is(':checked');
    var notifyFailureLast = $('#schedule-notify-failure-last').is(':checked');
    var failureEmailsOverride = $("#schedule-override-failure-emails").is(':checked');
    var successEmailsOverride = $("#schedule-override-success-emails").is(':checked');
    //告警级别选择
    var failureAlertLevel = $('#schedule-override-failure-alert-level').val();
    var successAlertLevel = $('#schedule-override-success-alert-level').val();

    var flowOverride = {};
    var editRows = $(".editRow");
    for (var i = 0; i < editRows.length; ++i) {
      var row = editRows[i];
      var td = $(row).find('span');
      var key = $(td[0]).text();
      var val = $(td[1]).text();

      if (key && key.length > 0) {
        flowOverride[key] = val;
      }
    }

    var data = this.model.get("data");
    var disabledList = scheduleGatherDisabledNodes(data);

    var jobFailedRetryOptions = {};
    var tdFailedRetrys = document.getElementById("jobScheduleFailedRetryTable").tBodies[0];
    for (var row = 0; row < tdFailedRetrys.rows.length - 1; row++) {
      var tdFailedRetry = tdFailedRetrys.rows[row];
      var job = tdFailedRetry.cells[0].firstChild.value;
      var interval = tdFailedRetry.cells[1].firstChild.value;
      if (0 == interval || interval.indexOf("-") != -1) {
        alert(wtssI18n.view.rerunIntervalPro);
        return;
      }
      var count = tdFailedRetry.cells[2].firstChild.value;
      jobFailedRetryOptions[row] = job + "," + interval + "," + count;
    }

    var jobSkipFailedOptions = {};
    var tdSkipFaileds = document.getElementById("scheduleJobSkipFailedTable").tBodies[0];
    for (var row = 0; row < tdSkipFaileds.rows.length - 1; row++) {
      var tdSkipFailed = tdSkipFaileds.rows[row];
      var job = tdSkipFailed.cells[0].firstChild.value;
      jobSkipFailedOptions[row] = job;
    }

    var jobCronExpressOptions = {};
    var tdJobCron = document.getElementById("job-cron-table").tBodies[0];
    for (var row = 0; row < tdJobCron.rows.length - 1; row++) {
      var tr = tdJobCron.rows[row];
      var jobNestId = tr.cells[0].firstChild.value;
      var jobCronExpress = "59 59 23 " + $(tr.cells[1]).find(".cron").val();
      jobCronExpressOptions[row] = jobNestId + "#_#" + jobCronExpress;
    }

    var executingData = {
      projectId: projectId,
      project: this.projectName,
      ajax: "executeFlow",
      flow: this.flowId,
      disabled: JSON.stringify(disabledList),
      failureEmailsOverride: failureEmailsOverride,
      successEmailsOverride: successEmailsOverride,
      failureAction: failureAction,
      failureEmails: failureEmails,
      successEmails: successEmails,
      notifyFailureFirst: notifyFailureFirst,
      notifyFailureLast: notifyFailureLast,
      flowOverride: flowOverride,
      jobFailedRetryOptions: jobFailedRetryOptions,
      failureAlertLevel: failureAlertLevel,
      successAlertLevel: successAlertLevel,
      jobSkipFailedOptions: jobSkipFailedOptions,
      jobCronExpressOptions: jobCronExpressOptions
    };

    // Set concurrency option, default is skip

    var concurrentOption = $('input[name=concurrent]:checked').val();
    executingData.concurrentOption = concurrentOption;
    if (concurrentOption == "pipeline") {
      var pipelineLevel = $("#schedule-pipeline-level").val();
      executingData.pipelineLevel = pipelineLevel;
    }
    else if (concurrentOption == "queue") {
      executingData.queueLevel = $("#schedule-queueLevel").val();
    }

    //检查是否有重复的规则
    if (checkFiledRetryRule(jobFailedRetryOptions)) {
      alert(wtssI18n.view.errorRerunRulePro);
      return;
    }

    return executingData;
  },

  changeFlowInfo: function () {
    var successEmails = this.model.get("successEmails");
    var failureEmails = this.model.get("failureEmails");
    var failureActions = this.model.get("failureAction");
    var notifyFailure = this.model.get("notifyFailure");
    var flowParams = this.model.get("flowParams");
    var isRunning = this.model.get("isRunning");
    var concurrentOption = this.model.get("concurrentOption");
    var pipelineLevel = this.model.get("pipelineLevel");
    var pipelineExecutionId = this.model.get("pipelineExecution");
    var queueLevel = this.model.get("schedule-queueLevel");
    var nodeStatus = this.model.get("nodeStatus");
    var overrideSuccessEmails = this.model.get("failureEmailsOverride");
    var overrideFailureEmails = this.model.get("successEmailsOverride");
    var enableHistoryRecover = this.model.get("enableHistoryRecover");

    if (overrideSuccessEmails) {
      $('#schedule-override-success-emails').attr('checked', true);
    }
    else {
      $('#schedule-override-success-emails').attr('checked', false);
      $('#schedule-success-emails').attr('disabled', 'disabled');
    }
    if (overrideFailureEmails) {
      $('#schedule-override-failure-emails').attr('checked', true);
    }
    else {
      $('#schedule-override-failure-emails').attr('checked', false);
      $('#schedule-failure-emails').attr('disabled', 'disabled');
    }

    if (successEmails) {
      $('#schedule-success-emails').val(successEmails.join());
    }
    if (overrideFailureEmails) {
      $('#schedule-override-failure-emails').attr('checked', true);
    } else {
      $('#schedule-override-failure-emails').attr('checked', false);
      $('#schedule-failure-emails').attr('disabled', 'disabled');
    }
    if (failureEmails) {
      $('#schedule-failure-emails').val(failureEmails.join());
    }
    if (failureActions) {
      $('#schedule-failure-action').val(failureActions);
    }

    if (notifyFailure.first) {
      $('#schedule-notify-failure-first').attr('checked', true);
      $('#schedule-notify-failure-first').parent('.btn').addClass('active');
    }
    if (notifyFailure.last) {
      $('#schedule-notify-failure-last').attr('checked', true);
      $('#schedule-notify-failure-last').parent('.btn').addClass('active');
    }

    if (concurrentOption) {
      $('input[value=' + concurrentOption + '][name="concurrent"]').attr(
        'checked', true);
    }
    if (pipelineLevel) {
      $('#schedule-pipeline-level').val(pipelineLevel);
    }
    if (queueLevel) {
      $('#schedule-queueLevel').val(queueLevel);
    }

    if (flowParams && $(".editRow").length == 0) {
      for (var key in flowParams) {
        schedulEditTableView.handleAddRow({
          paramkey: key,
          paramvalue: flowParams[key]
        });
      }
    }

  },

  show: function (data) {
    var projectName = data.project;
    var flowId = data.flow;
    var jobId = data.job;
    var scheduleFlowTitle = data.scheduleFlowTitle;

    // ExecId is optional
    var execId = data.execid;
    var exgraph = data.exgraph;

    this.projectName = projectName;
    this.flowId = flowId;

    var self = this;
    var loadCallback = function () {
      if (jobId) {
        self.showScheduleJob(scheduleFlowTitle, projectName, flowId, jobId, data.withDep);
      }
      else {
        self.showScheduleFlow(scheduleFlowTitle, projectName, flowId);
      }
    }

    // 清空缓存数据
    $('#schedule-cron-output').val("");
    resetLabelColor();
    $("#schedule-minute_input").val("");
    $("#schedule-hour_input").val("");
    $("#schedule-dom_input").val("");
    $("#schedule-month_input").val("");
    $("#schedule-dow_input").val("");
    $(schedule_cron_translate_id).text("")
    $(schedule_cron_translate_warning_id).text("")
    $('#scheduleNextRecurId').html("");
    $("#schedule-year_input").val("");
    while ($("#schedule-instructions tbody tr:last").index() >= 4) {
      $("#schedule-instructions tbody tr:last").remove();
    }

    // 检查是否有定时调度,如果有缓存数据,则赋值,如果没有,则保留空值状态
    var cronExpression = data.cronExpression;
    var scheduleId = data.scheduleId;
    if (scheduleId) {
      var cron = cronExpression.substr(2);
      $('#schedule-cron-output').val(cron);
      var array = cron.split(/[ ]/);

      $("#schedule-minute_input").val(array[0]);
      $("#schedule-hour_input").val(array[1]);
      $("#schedule-dom_input").val(array[2]);
      $("#schedule-month_input").val(array[3]);
      $("#schedule-dow_input").val(array[4]);
      $("#schedule-year_input").val(array[5] ? array[5] : "");
      updateScheduleExpression();
    }

    var loadedId = schedulableGraphModel.get("flowId");
    this.loadGraph(projectName, flowId, exgraph, loadCallback);
    this.loadFlowInfo(projectName, flowId, execId);
    this.loadSchedule();

    $("#schedule-flow-div").show();
    $("#shedule-all-flow-div").hide();
    $('#schedule-flow-option').show();
    $('#schedule-failure-li').show();
    $('#job-cron-li').show();
  },

  showScheduleFlow: function (scheduleFlowTitle, projectName, flowId) {
    $("#schedule-flow-panel-title").text(scheduleFlowTitle + flowId);
    this.showScheduleOptionPanel();

    // Triggers a render
    this.model.trigger("change:graph");
  },

  showScheduleJob: function (scheduleFlowTitle, projectName, flowId, jobId, withDep) {
    scheduleSideMenuDialogView.menuSelect($("#schedule-flow-option"));
    $("#schedule-flow-panel-title").text(scheduleFlowTitle + flowId);

    var data = this.model.get("data");
    var disabled = this.model.get("disabled");

    // Disable all, then re-enable those you want.
    scheduleDisableAll();

    var jobNode = data.nodeMap[jobId];
    scheduleTouchNode(jobNode, false);

    if (withDep) {
      scheduleRecurseAllAncestors(jobNode, false);
    }

    this.showScheduleOptionPanel();
    this.model.trigger("change:graph");
  },

  showScheduleOptionPanel: function () {
    scheduleSideMenuDialogView.menuSelect($("#schedule-flow-option"));
    $('#schedule-flow-panel').modal();
    jobScheduleFailedRetryView.setFlowID(this.flowId);
    scheduleJobSkipFailedView.setFlowID(this.flowId);
  },

  hideScheduleOptionPanel: function () {
    $('#schedule-flow-panel').modal("hide");
  },

  loadFlowInfo: function (projectName, flowId, execId) {
    console.log("Loading flow " + flowId);
    fetchFlowInfo(this.model, projectName, flowId, execId);
  },

  loadGraph: function (projectName, flowId, exgraph, callback) {
    console.log("Loading flow " + flowId);
    var requestURL = contextURL + "/executor";

    var graphModel = schedulableGraphModel;
    // fetchFlow(this.model, projectName, flowId, true);
    var requestData = {
      "project": projectName,
      "ajax": "fetchscheduledflowgraphNew",
      "flow": flowId
    };
    var self = this;
    var successHandler = function (data) {
      console.log("data fetched");
      graphModel.addFlow(data);

      if (exgraph) {
        self.assignInitialStatus(data, exgraph);
      }

      // Auto disable jobs that are finished.
      disableFinishedJobs(data);
      executingSvgGraphView = new azkaban.SvgGraphView({
        el: $('#schedule-flow-executing-graph'),
        model: graphModel,
        render: false,
        rightClick: {
          "node": schedulExpanelNodeClickCallback,
          "edge": schedulExpanelEdgeClickCallback,
          "graph": schedulExpanelGraphClickCallback
        },
        tooltipcontainer: "#schedule-svg-div-custom"
      });

      if (callback) {
        callback.call(this);
      }
    };
    $.get(requestURL, requestData, successHandler, "json");
  },

  assignInitialStatus: function (data, statusData) {
    // Copies statuses over from the previous execution if it exists.
    var statusNodeMap = statusData.nodeMap;
    var nodes = data.nodes;
    for (var i = 0; i < nodes.length; ++i) {
      var node = nodes[i];
      var statusNode = statusNodeMap[node.id];
      if (statusNode) {
        node.status = statusNode.status;
        if (node.type == "flow" && statusNode.type == "flow") {
          this.assignInitialStatus(node, statusNode);
        }
      } else {
        // job wasn't present in this flow during the original execution
        node.noInitialStatus = true;
      }
    }
  },

  reloadWindow: function () {
    window.location.reload();
  },

  loadSchedule: function () {
    console.log("Loading Schedule Options ");
    showSchedulePanel();
  },

  scheduleFlow: function () {
    var scheduleURL = contextURL + "/schedule"
    var scheduleData = this.getExecutionOptionData();

    console.log("Creating schedule for " + projectName + "."
      + scheduleData.flow);

    var currentMomentTime = moment();
    var scheduleTime = currentMomentTime.utc().format('h,mm,A,') + "UTC";
    var scheduleDate = currentMomentTime.format('MM/DD/YYYY');

    scheduleData.ajax = "scheduleCronFlow";
    scheduleData.projectName = projectName;
    scheduleData.cronExpression = "0 " + $(schedule_cron_output_id).val();

    // Currently, All cron expression will be based on server timezone.
    // Later we might implement a feature support cron under various timezones, depending on the future use cases.
    // scheduleData.cronTimezone = timezone;

    console.log("current Time = " + scheduleDate + "  " + scheduleTime);
    console.log("cronExpression = " + scheduleData.cronExpression);
    var retSignal = validateQuartzStr(scheduleData.cronExpression.trim());

    if (retSignal == "NUM_FIELDS_ERROR") {
      var tipMsg1 = "Cron 表达式错误, 一个有效的Cron表达式至少有6个或者7个属性."
      if (langType === "en_US") {
        tipMsg1 = "Cron Syntax Error, A valid Quartz Cron Expression should have 6 or 7 fields.";
      }
      alert(tipMsg1);
      return;
    } else if (retSignal == "DOW_DOM_STAR_ERROR") {
      var tipMsg2 = "Cron 表达式错误, 月的某日和周的某天不能同时有值" + "(你必须将其中一个的值改为 ‘?’). <a href=\"http://www.quartz-scheduler.org/documentation/quartz-2.x/tutorials/crontrigger.html\" target=\"_blank\">详情请见</a>";
      if (langType === "en_US") {
        tipMsg2 = "Cron Syntax Error, Currently Quartz doesn't support specifying both a day-of-week and a day-of-month value"
          + "(you must use the ‘?’ character in one of these fields). <a href=\"http://www.quartz-scheduler.org/documentation/quartz-2.x/tutorials/crontrigger.html\" target=\"_blank\">Detailed Explanation</a>";
      }
      alert(tipMsg2)
      return;
    }
    //检查cron是否正确

    var successHandler = function (data) {
      if (data.error) {
        alert(data.error);
        // flowScheduleDialogView.hideScheduleOptionPanel();
        // messageDialogView.show(wtssI18n.view.timingSchedulingFailed, data.error);
      }
      else {
        flowScheduleDialogView.hideScheduleOptionPanel();
        messageDialogView.show(wtssI18n.view.timingScheduling, data.message, function () {
          window.location.href = scheduleURL;
        });
      }
    };

    $.post(scheduleURL, scheduleData, successHandler, "json");
  },

  loadScheduleRunningInfo: function (data) {

    console.log("loadScheduleRunningInfo");

    var scheduleURL = contextURL + "/schedule"

    var scheduleData = {
      ajax: "getScheduleByScheduleId",
      scheduleId: data.scheduleId
    }


    var successHandler = function (data) {
      if (data.error) {

      }
      else {
        var scheduleId = data.schedule.scheduleId;
        var projectName = data.schedule.projectName;
        var flowName = data.schedule.flowName;
        var cronExpression = data.schedule.cronExpression;

        var successEmails = data.schedule.executionOptions.successEmails;
        var failureEmails = data.schedule.executionOptions.failureEmails;
        var failureActions = data.schedule.executionOptions.failureAction;
        var notifyOnFirstFailure = data.schedule.executionOptions.notifyOnFirstFailure;
        var notifyOnLastFailure = data.schedule.executionOptions.notifyOnLastFailure;
        var flowParams = data.schedule.executionOptions.flowParams;
        var isRunning = data.schedule.executionOptions.isRunning;
        var concurrentOption = data.schedule.executionOptions.concurrentOption;
        var pipelineLevel = data.schedule.executionOptions.pipelineLevel;
        var queueLevel = data.schedule.executionOptions.pipelineLevel;
        var overrideSuccessEmails = data.schedule.executionOptions.failureEmailsOverride;
        var overrideFailureEmails = data.schedule.executionOptions.successEmailsOverride;

        if (overrideSuccessEmails) {
          $('#schedule-override-success-emails').attr('checked', true);
        }
        else {
          $('#schedule-override-success-emails').attr('checked', false);
          $('#schedule-success-emails').attr('disabled', 'disabled');
        }
        if (overrideFailureEmails) {
          $('#schedule-override-failure-emails').attr('checked', true);
        }
        else {
          $('#schedule-override-failure-emails').attr('checked', false);
          $('#schedule-failure-emails').attr('disabled', 'disabled');
        }

        if (successEmails) {
          $('#schedule-success-emails').val(successEmails.join());
        }
        if (overrideFailureEmails) {
          $('#schedule-override-failure-emails').attr('checked', true);
        } else {
          $('#schedule-override-failure-emails').attr('checked', false);
          $('#schedule-failure-emails').attr('disabled', 'disabled');
        }
        if (failureEmails) {
          $('#schedule-failure-emails').val(failureEmails.join());
        }
        if (failureActions) {
          $('#schedule-failure-action').val(failureActions);
        }

        if (notifyOnFirstFailure) {
          $('#schedule-notify-failure-first').attr('checked', true);
          $('#schedule-notify-failure-first').parent('.btn').addClass('active');
        }
        if (notifyOnLastFailure) {
          $('#schedule-notify-failure-last').attr('checked', true);
          $('#schedule-notify-failure-last').parent('.btn').addClass('active');
        }

        if (concurrentOption) {
          $('input[value=' + concurrentOption + '][name="concurrent"]').attr(
            'checked', true);
        }
        if (pipelineLevel) {
          $('#schedule-pipeline-level').val(pipelineLevel);
        }
        if (queueLevel) {
          $('#schedule-queueLevel').val(queueLevel);
        }

        if (flowParams && $(".editRow").length == 0) {
          for (var key in flowParams) {
            schedulEditTableView.handleAddRow({
              paramkey: key,
              paramvalue: flowParams[key]
            });
          }
        }

        $('#schedule_id').val(scheduleId);
        $('#schedule_projectName').val(projectName);
        $('#schedule_flowName').val(flowName);
        var cron = cronExpression.substr(2);
        $('#schedule-cron-output').val(cron);
        var array = cron.split(/[ ]/);

        $("#schedule-minute_input").val(array[0]);
        $("#schedule-hour_input").val(array[1]);
        $("#schedule-dom_input").val(array[2]);
        $("#schedule-month_input").val(array[3]);
        $("#schedule-dow_input").val(array[4]);
        $("#schedule-year_input").val(array[5] ? array[5] : "");
        updateScheduleExpression();

      }
    };

    $.post(scheduleURL, scheduleData, successHandler, "json");

  },

  scheduleEditFlow: function () {
    var scheduleURL = contextURL + "/schedule"
    var scheduleData = this.getExecutionOptionData();

    console.log("Creating schedule for " + projectName + "."
      + scheduleData.flow);

    var currentMomentTime = moment();
    var scheduleTime = currentMomentTime.utc().format('h,mm,A,') + "UTC";
    var scheduleDate = currentMomentTime.format('MM/DD/YYYY');

    scheduleData.ajax = "scheduleEditFlow";
    scheduleData.projectName = projectName;
    scheduleData.cronExpression = "0 " + $(schedule_cron_output_id).val();

    // Currently, All cron expression will be based on server timezone.
    // Later we might implement a feature support cron under various timezones, depending on the future use cases.
    // scheduleData.cronTimezone = timezone;

    console.log("current Time = " + scheduleDate + "  " + scheduleTime);
    console.log("cronExpression = " + scheduleData.cronExpression);
    var retSignal = validateQuartzStr(scheduleData.cronExpression);

    if (retSignal == "NUM_FIELDS_ERROR") {
      var tipMsg1 = "Cron 表达式错误, 一个有效的Cron表达式至少有6个或者7个属性."
      if (langType === "en_US") {
        tipMsg1 = "Cron Syntax Error, A valid Quartz Cron Expression should have 6 or 7 fields.";
      }
      alert(tipMsg1);
      return;
    } else if (retSignal == "DOW_DOM_STAR_ERROR") {
      var tipMsg2 = "Cron 表达式错误, 月的某日和周的某天不能同时有值" + "(你必须将其中一个的值改为 ‘?’). <a href=\"http://www.quartz-scheduler.org/documentation/quartz-2.x/tutorials/crontrigger.html\" target=\"_blank\">详情请见</a>";
      if (langType === "en_US") {
        tipMsg2 = "Cron Syntax Error, Currently Quartz doesn't support specifying both a day-of-week and a day-of-month value"
          + "(you must use the ‘?’ character in one of these fields). <a href=\"http://www.quartz-scheduler.org/documentation/quartz-2.x/tutorials/crontrigger.html\" target=\"_blank\">Detailed Explanation</a>";
      }
      alert(tipMsg2)
      return;
    }

    var successHandler = function (data) {
      if (data.error) {
        alert(data.error);
        // flowScheduleDialogView.hideScheduleOptionPanel();
        // messageDialogView.show(wtssI18n.view.timingSchedulingFailed, data.error);
      }
      else {
        flowScheduleDialogView.hideScheduleOptionPanel();
        messageDialogView.show(wtssI18n.view.timingScheduling, data.message, function () {
          window.location.href = scheduleURL;
        });
      }
    };

    $.post(scheduleURL, scheduleData, successHandler, "json");
  },


});

var schedulEditTableView;
azkaban.ScheduleEditTableView = Backbone.View.extend({
  events: {
    "click table #add-btn": "handleAddRow",
    "click table .editable": "handleEditColumn",
    "click table .remove-btn": "handleRemoveColumn"
  },

  initialize: function (setting) {
  },

  handleAddRow: function (data) {
    var name = "";
    if (data.paramkey) {
      name = data.paramkey;
    }

    var value = "";
    if (data.paramvalue) {
      value = data.paramvalue;
    }

    var tr = document.createElement("tr");
    var tdName = document.createElement("td");
    $(tdName).addClass('property-key');
    var tdValue = document.createElement("td");

    var remove = document.createElement("div");
    $(remove).addClass("pull-right").addClass('remove-btn');
    var removeBtn = document.createElement("button");
    $(removeBtn).attr('type', 'button');
    $(removeBtn).addClass('btn').addClass('btn-xs').addClass('btn-danger');
    $(removeBtn).text('Delete');
    $(remove).append(removeBtn);

    var nameData = document.createElement("span");
    $(nameData).addClass("spanValue");
    $(nameData).text(name);
    var valueData = document.createElement("span");
    $(valueData).addClass("spanValue");
    $(valueData).text(value);

    $(tdName).append(nameData);
    $(tdName).addClass("editable");

    $(tdValue).append(valueData);
    $(tdValue).append(remove);
    $(tdValue).addClass("editable").addClass('value');

    $(tr).addClass("editRow");
    $(tr).append(tdName);
    $(tr).append(tdValue);

    $(tr).insertBefore(".addRow");
    return tr;
  },

  handleEditColumn: function (evt) {
    if (evt.target.tagName == "INPUT")
      return;
    var curTarget = evt.currentTarget;

    var text = $(curTarget).children(".spanValue").text();
    $(curTarget).empty();

    var input = document.createElement("input");
    $(input).attr("type", "text");
    $(input).addClass('form-control').addClass('input-sm');
    $(input).css("width", "100%");
    $(input).val(text);
    $(curTarget).addClass("editing");
    $(curTarget).append(input);
    $(input).focus();

    var obj = this;
    $(input).focusout(function (evt) {
      obj.closeEditingTarget(evt);
    });

    $(input).keypress(function (evt) {
      if (evt.which == 13) {
        obj.closeEditingTarget(evt);
      }
    });
  },

  handleRemoveColumn: function (evt) {
    var curTarget = evt.currentTarget;
    // Should be the table
    var row = curTarget.parentElement.parentElement;
    $(row).remove();
  },

  closeEditingTarget: function (evt) {
    var input = evt.currentTarget;
    var text = $(input).val();
    var parent = $(input).parent();
    $(parent).empty();

    var valueData = document.createElement("span");
    $(valueData).addClass("spanValue");
    $(valueData).text(text);

    if ($(parent).hasClass("value")) {
      var remove = document.createElement("div");
      $(remove).addClass("pull-right").addClass('remove-btn');
      var removeBtn = document.createElement("button");
      $(removeBtn).attr('type', 'button');
      $(removeBtn).addClass('btn').addClass('btn-xs').addClass('btn-danger');
      $(removeBtn).text('Delete');
      $(remove).append(removeBtn);
      $(parent).append(remove);
    }

    $(parent).removeClass("editing");
    $(parent).append(valueData);
  }
});

var scheduleSideMenuDialogView;
azkaban.ScheduleSideMenuDialogView = Backbone.View.extend({
  events: {
    "click .menu-header": "menuClick"
  },

  initialize: function (settings) {
    var children = $(this.el).children();
    for (var i = 0; i < children.length; ++i) {
      var child = children[i];
      $(child).addClass("menu-header");
      var caption = $(child).find(".menu-caption");
      $(caption).hide();
    }
    this.menuSelect($("#flow-option"));
  },

  menuClick: function (evt) {
    this.menuSelect(evt.currentTarget);
  },

  menuSelect: function (target) {
    if ($(target).hasClass("active")) {
      return;
    }

    $(".side-panel").each(function () {
      $(this).hide();
    });


    // 当点击定时调度工作流时，显示隐藏流程图切换按钮
    if ((target[0] && target[0].id === "schedule-flow-option") || target.id === "schedule-flow-option") {
      $("#switching-schedule-flow-btn").show()
      $("#workflow-zoom-in").show()
    } else {
      $("#switching-schedule-flow-btn").hide()
      $("#workflow-zoom-in").hide()
      $("#workflow-zoom-out").hide()
    }


    $(".menu-header").each(function () {
      $(this).find(".menu-caption").slideUp("fast");
      $(this).removeClass("active");
    });

    $(target).addClass("active");
    $(target).find(".menu-caption").slideDown("fast");
    var panelName = $(target).attr("viewpanel");
    $("#" + panelName).show();
  }
});

var schedulableGraphModel;

/**
 * Disable jobs that need to be disabled
 */
var scheduleDisableFinishedJobs = function (data) {
  for (var i = 0; i < data.nodes.length; ++i) {
    var node = data.nodes[i];

    if (node.status == "DISABLED" || node.status == "SKIPPED") {
      node.status = "READY";
      node.disabled = true;
    }
    else if (node.status == "SUCCEEDED" || node.noInitialStatus) {
      node.disabled = true;
    }
    else {
      node.disabled = false;
    }
    if (node.type == "flow") {
      scheduleDisableFinishedJobs(node);
    }
  }
}

/**
 * Enable all jobs. Recurse
 */
var schedulEnableAll = function () {
  scheduleRecurseTree(schedulableGraphModel.get("data"), false, true);
  schedulableGraphModel.trigger("change:disabled");
}

var scheduleDisableAll = function () {
  scheduleRecurseTree(schedulableGraphModel.get("data"), true, true);
  schedulableGraphModel.trigger("change:disabled");
}

var scheduleRecurseTree = function (data, disabled, recurse) {
  for (var i = 0; i < data.nodes.length; ++i) {
    var node = data.nodes[i];
    node.disabled = disabled;

    if (node.type == "flow" && recurse) {
      scheduleRecurseTree(node, disabled, recurse);
    }
  }
}

// 打开关闭节点，如果节点类型为flow则需要打开关闭subfolw流程所有节点
var scheduleDisableSubflow = function (single, node, disable) {
  if (!node) return;
  if (single) {
    node.disabled = disable;
    checkJobType(node, disable);
    if (!disable) {
      schedulableEnableSubflow(node);
    }
  } else {
    var count = 0;
    for (var key in node) {
      if (count === 0 && !disable) {
        schedulableEnableSubflow(node[key]);
      }
      node[key].disabled = disable;
      checkJobType(node[key], disable);
      count++;
    }
  }
}

function checkJobType (node, disable) {
  if (node.type == "flow") {
    scheduleRecurseTree(node, disable, true);
  }
}

var scheduleTouchNode = function (node, disable) {
  scheduleDisableSubflow(true, node, disable)
  schedulableGraphModel.trigger("change:disabled");
}

var scheduleTouchParents = function (node, disable) {
  var inNodes = node.inNodes;

  scheduleDisableSubflow(false, inNodes, disable);

  schedulableGraphModel.trigger("change:disabled");
}

// 启用工作流如果父流程节点为disable要先把父节点disable改成true
var schedulableEnableSubflow = function (node) {
  var scheduleData = schedulableGraphModel.get("data");
  var parantArr = [];
  var findNode = { isFind: false };
  enableSubflowNodeTree(scheduleData, parantArr, node, findNode);
}


var enableSubflowNodeTree = function (scheduleData, parantArr, node, findNode) {
  for (var i = 0; i < scheduleData.nodes.length; ++i) {
    if (findNode.isFind) {
      return
    }
    var item = scheduleData.nodes[i];
    if (item.nestedId === node.nestedId) {
      for (var j = 0; j < parantArr.length; j++) {
        parantArr[j].disabled = false
      }
      findNode.isFind = true
      return
    }
    if (item.type == "flow") {
      parantArr.push(item)
      enableSubflowNodeTree(item, parantArr, node, findNode)
      parantArr.splice(parantArr.length - 1, 1)
    }
  }
}

var scheduleTouchChildren = function (node, disable) {
  var outNodes = node.outNodes;

  scheduleDisableSubflow(false, outNodes, disable)

  schedulableGraphModel.trigger("change:disabled");
}

var scheduleTouchAncestors = function (node, disable) {
  var inNodes = node.inNodes;
  if (inNodes && !disable) {
    var key = Object.keys(inNodes)[0]
    schedulableEnableSubflow(inNodes[key])
  }
  scheduleRecurseAllAncestors(node, disable);

  schedulableGraphModel.trigger("change:disabled");
}

var scheduleTouchDescendents = function (node, disable) {
  var outNodes = node.outNodes;
  if (outNodes && !disable) {
    var key = Object.keys(outNodes)[0]
    schedulableEnableSubflow(outNodes[key])
  }
  scheduleRecurseAllDescendents(node, disable);

  schedulableGraphModel.trigger("change:disabled");
}

var scheduleGatherDisabledNodes = function (data) {
  var nodes = data.nodes;
  var disabled = [];

  for (var i = 0; i < nodes.length; ++i) {
    var node = nodes[i];
    if (node.disabled) {
      disabled.push(node.id);
    }
    else {
      if (node.type == "flow") {
        var array = scheduleGatherDisabledNodes(node);
        if (array && array.length > 0) {
          disabled.push({ id: node.id, children: array });
        }
      }
    }
  }

  return disabled;
}

function scheduleRecurseAllAncestors (node, disable) {
  var inNodes = node.inNodes;
  if (inNodes) {
    for (var key in inNodes) {
      inNodes[key].disabled = disable;
      checkJobType(inNodes[key], disable);
      scheduleRecurseAllAncestors(inNodes[key], disable);
    }
  }
}

function scheduleRecurseAllDescendents (node, disable) {
  var outNodes = node.outNodes;
  if (outNodes) {
    for (var key in outNodes) {
      outNodes[key].disabled = disable;
      checkJobType(outNodes[key], disable);
      scheduleRecurseAllDescendents(outNodes[key], disable);
    }
  }
}
// type 为执行类型datachecker--所有datacheck  eventchecker--所有eventchecker/rmbsender(所有信号)  outer--所有外部信息  disabled--true关闭  false开启
var scheduleTouchTypecheck = function (type, disabled) {
  scheduleConditionRecurseTree(schedulableGraphModel.get("data"), disabled, true, type);
  schedulableGraphModel.trigger("change:disabled");
}

var scheduleConditionRecurseTree = function (data, disable, recurse, type) {
  function scheduleTypeSubflowDisable (data, disable) {
    if (data.type === "flow" && !disable) {
      data.disabled = disable;
    }
  }
  for (var i = 0; i < data.nodes.length; ++i) {
    var node = data.nodes[i];
    switch (type) {
      case 'datachecker':
        if (node.type === 'datachecker') {
          node.disabled = disable;
          scheduleTypeSubflowDisable(data, disable);
        }
        break;
      case 'eventchecker':
        if (node.type === 'eventchecker' || node.type === 'rmbsender') {
          node.disabled = disable;
          scheduleTypeSubflowDisable(data, disable);
        }
        break;
      case 'outer':
        if (node.outer === true || node.outer === 'true') {
          node.disabled = disable;
          scheduleTypeSubflowDisable(data, disable);
        }
        break;
      default:
        break;
    }
    if (node.type === "flow" && recurse) {
      scheduleConditionRecurseTree(node, disable, recurse, type);
    }
  }
}

var schedulExpanelNodeClickCallback = function (event, model, node) {
  console.log("Node clicked callback");
  var jobId = node.id;
  var flowId = schedulableGraphModel.get("flowId");
  var type = node.type;

  var menu;
  if (type == "flow") {
    var flowRequestURL = contextURL + "/manager?project=" + projectName
      + "&flow=" + node.flowId;
    if (node.expanded) {
      menu = [
        {
          title: wtssI18n.common.collapseFlow, callback: function () {
            model.trigger("collapseFlow", node);
          }
        },
        {
          title: wtssI18n.common.openNewWindow, callback: function () {
            window.open(flowRequestURL);
          }
        }
      ];

    }
    else {
      menu = [
        {
          title: wtssI18n.common.expandFlow, callback: function () {
            model.trigger("expandFlow", node);
          }
        },
        {
          title: wtssI18n.common.openNewWindow, callback: function () {
            window.open(flowRequestURL);
          }
        }
      ];
    }
  }
  else {
    var requestURL = contextURL + "/manager?project=" + projectName + "&flow="
      + flowId + "&job=" + jobId;
    menu = [
      {
        title: wtssI18n.common.openNewJob, callback: function () {
          window.open(requestURL);
        }
      },
    ];
  }

  $.merge(menu, [
    { break: 1 },
    {
      title: wtssI18n.view.open, callback: function () {
        scheduleTouchNode(node, false);
      }, submenu: [
        {
          title: wtssI18n.view.openParentNode, callback: function () {
            scheduleTouchParents(node, false);
          }
        },
        {
          title: wtssI18n.view.openPreviousNode, callback: function () {
            scheduleTouchAncestors(node, false);
          }
        },
        {
          title: wtssI18n.view.openChildNode, callback: function () {
            scheduleTouchChildren(node, false);
          }
        },
        {
          title: wtssI18n.view.openDescendantNodes, callback: function () {
            scheduleTouchDescendents(node, false);
          }
        },
        {
          title: wtssI18n.view.openDatacheck, callback: function () {
            scheduleTouchTypecheck('datachecker', false);
          }
        },
        {
          title: wtssI18n.view.openSignal, callback: function () {
            scheduleTouchTypecheck('eventchecker', false);
          }
        },
        {
          title: wtssI18n.view.openOuterSignal, callback: function () {
            scheduleTouchTypecheck('outer', false);
          }
        },
        {
          title: wtssI18n.view.openAll, callback: function () {
            schedulEnableAll();
          }
        }
      ]
    },
    {
      title: wtssI18n.view.close, callback: function () {
        scheduleTouchNode(node, true)
      }, submenu: [
        {
          title: wtssI18n.view.closeParentNode, callback: function () {
            scheduleTouchParents(node, true);
          }
        },
        {
          title: wtssI18n.view.closePreviousNode, callback: function () {
            scheduleTouchAncestors(node, true);
          }
        },
        {
          title: wtssI18n.view.closeChildNode, callback: function () {
            scheduleTouchChildren(node, true);
          }
        },
        {
          title: wtssI18n.view.closeDescendantNodes, callback: function () {
            scheduleTouchDescendents(node, true);
          }
        },
        {
          title: wtssI18n.view.closeDatacheck, callback: function () {
            scheduleTouchTypecheck('datachecker', true);
          }
        },
        {
          title: wtssI18n.view.closeSignal, callback: function () {
            scheduleTouchTypecheck('eventchecker', true);
          }
        },
        {
          title: wtssI18n.view.closeOuterSignal, callback: function () {
            scheduleTouchTypecheck('outer', true);
          }
        },
        {
          title: wtssI18n.view.closeAll, callback: function () {
            scheduleDisableAll();
          }
        }
      ]
    },
    {
      title: wtssI18n.common.centerJob, callback: function () {
        model.trigger("centerNode", node);
      }
    }
  ]);

  scheduleContextMenuView.show(event, menu);
}

var schedulExpanelEdgeClickCallback = function (event) {
  console.log("Edge clicked callback");
}

var schedulExpanelGraphClickCallback = function (event) {
  console.log("Graph clicked callback");
  var flowId = schedulableGraphModel.get("flowId");
  var requestURL = contextURL + "/manager?project=" + projectName + "&flow="
    + flowId;

  var menu = [
    {
      title: wtssI18n.common.openNewWindow, callback: function () {
        window.open(requestURL);
      }
    },
    { break: 1 },
    {
      title: wtssI18n.view.openAll, callback: function () {
        schedulEnableAll();
      }
    },
    {
      title: wtssI18n.view.closeAll, callback: function () {
        scheduleDisableAll();
      }
    },
    { break: 1 },
    {
      title: wtssI18n.common.centerGraph, callback: function () {
        schedulableGraphModel.trigger("resetPanZoom");
      }
    }
  ];

  scheduleContextMenuView.show(event, menu);
}

var scheduleContextMenuView;
$(function () {
  schedulableGraphModel = new azkaban.GraphModel();
  flowScheduleDialogView = new azkaban.FlowScheduleDialogView({
    el: $('#schedule-flow-panel'),
    model: schedulableGraphModel
  });

  scheduleSideMenuDialogView = new azkaban.ScheduleSideMenuDialogView({
    el: $('#schedule-graph-options')
  });
  schedulEditTableView = new azkaban.ScheduleEditTableView({
    el: $('#schedule-editTable')
  });

  scheduleContextMenuView = new azkaban.ScheduleContextMenuView({
    el: $('#schedule-contextMenu')
  });

  $(document).keyup(function (e) {
    // escape key maps to keycode `27`
    if (e.keyCode == 27) {
      flowScheduleDialogView.hideScheduleOptionPanel();
      // flowScheduleDialogView.remove();
    }
  });

  jobScheduleRetryModel = new azkaban.JobScheduleRetryModel();

  jobScheduleFailedRetryView = new azkaban.JobScheduleFailedRetryView({
    el: $('#job-schedule-failed-retry-view'),
    model: jobScheduleRetryModel,
  });

  scheduleJobSkipFailedModel = new azkaban.ScheduleJobSkipFailedModel();

  scheduleJobSkipFailedView = new azkaban.ScheduleJobSkipFailedView({
    el: $('#schedule-job-skip-failed-view'),
    model: scheduleJobSkipFailedModel,
  });

  scheduleJobCronView = new azkaban.ScheduleJobCronView({
    el: $('#job-cron-panel'),
    model: scheduleJobSkipFailedModel,
  });

  $("#switching-schedule-flow-btn").on('click', function () {
    var trimFlowName = !JSON.parse(sessionStorage.getItem('trimFlowName'));//标识是否剪切节点名称
    sessionStorage.setItem('trimFlowName', trimFlowName)
    scheduleReRenderWorflow(true) // 参数是否切换工作流
  })
  $("#workflow-zoom-in").on('click', function () {
    $(this).hide()
    $("#workflow-zoom-out").show()
    $('#schedule-flow-panel .modal-header').hide()
    $('#schedule-flow-panel .modal-footer').hide()
    $('#schedule-graph-options-box').hide()
    $('#schedule-graph-panel-box').removeClass('col-xs-8').addClass('col-xs-12')
    $('#schedule-flow-panel .modal-dialog')[0].style.width = "98%"
    $('#schedule-flow-executing-graph')[0].style.height = window.innerHeight * 0.88
    scheduleZoomInWorflow() // 参数是否切换工作流
  })
  $("#workflow-zoom-out").on('click', function () {
    $(this).hide()
    $("#workflow-zoom-in").show()
    $('#schedule-flow-panel .modal-header').show()
    $('#schedule-flow-panel .modal-footer').show()
    $('#schedule-graph-options-box').show()
    $('#schedule-graph-panel-box').removeClass('col-xs-12').addClass('col-xs-8')
    $('#schedule-flow-panel .modal-dialog')[0].style.width = "80%"
    $('#schedule-flow-executing-graph')[0].style.height = '500px'
    scheduleZoomInWorflow() // 参数是否切换工作流
  })
});
// 放大缩小重新收拢工作流，并居中
function scheduleZoomInWorflow () {
  executingSvgGraphView.collapseAllFlows()
  executingSvgGraphView.resetPanZoom()
}

function scheduleReRenderWorflow (switchingFlow) {
  var data = executingSvgGraphView.model.get('data') //获取流程图数据
  if (switchingFlow) {
    data.switchingFlow = true
  }
  $(executingSvgGraphView.mainG).empty() //清空流程图
  executingSvgGraphView.renderGraph(data, executingSvgGraphView.mainG)
}

function showSchedulePanel () {
  var timeZone = $('#scheduleTimeZoneID');
  timeZone.html(timezone);

  updateScheduleOutput();

  //清空所有选项的值
  $("#schedule-clearCron").click(function () {
    $('#schedule-cron-output').val("");
    resetLabelColor();
    $("#schedule-minute_input").val("");
    $("#schedule-hour_input").val("");
    $("#schedule-dom_input").val("");
    $("#schedule-month_input").val("");
    $("#schedule-dow_input").val("");
    $(schedule_cron_translate_id).text("")
    $(schedule_cron_translate_warning_id).text("")
    $('#scheduleNextRecurId').html("");
    $("#schedule-year_input").val("");

    while ($("#schedule-instructions tbody tr:last").index() >= 4) {
      $("#schedule-instructions tbody tr:last").remove();
    }
  });

  //每天中午12点执行一次,让用户方便编辑
  $("#schedule-suggestCron").click(function () {
    $('#schedule-cron-output').val("0 12 ? * *");
    resetLabelColor();
    $("#schedule-minute_input").val("0");
    $("#schedule-hour_input").val("12");
    $("#schedule-dom_input").val("?");
    $("#schedule-month_input").val("*");
    $("#schedule-dow_input").val("*");
    $(schedule_cron_translate_id).text("")
    $(schedule_cron_translate_warning_id).text("")
    $('#scheduleNextRecurId').html("");
    $("#schedule-year_input").val("");

    while ($("#schedule-instructions tbody tr:last").index() >= 4) {
      $("#schedule-instructions tbody tr:last").remove();
    }
    //每分钟执行一次,要显示:调度时间预览TOP10:
    updateScheduleOutput();
  });

  $("#schedule-minute_input").click(function () {
    while ($("#schedule-instructions tbody tr:last").index() >= 4) {
      $("#schedule-instructions tbody tr:last").remove();
    }
    resetLabelColor();
    $("#schedule-min_label").css("color", "red");
    $('#schedule-instructions tbody').append($("#schedule-instructions tbody tr:first").clone());
    $('#schedule-instructions tbody tr:last th').html("0-59");
    $('#schedule-instructions tbody tr:last td').html(wtssI18n.view.allowedValue);
  });

  $("#schedule-hour_input").click(function () {
    while ($("#schedule-instructions tbody tr:last").index() >= 4) {
      $("#schedule-instructions tbody tr:last").remove();
    }
    resetLabelColor();
    $("#schedule-hour_label").css("color", "red");
    $('#schedule-instructions tbody').append($("#schedule-instructions tbody tr:first").clone());
    $('#schedule-instructions tbody tr:last th').html("0-23");
    $('#schedule-instructions tbody tr:last td').html(wtssI18n.view.allowedValue);
  });

  $("#schedule-dom_input").click(function () {
    while ($("#schedule-instructions tbody tr:last").index() >= 4) {
      $("#schedule-instructions tbody tr:last").remove();
    }
    resetLabelColor();
    $("#schedule-dom_label").css("color", "red");
    $('#schedule-instructions tbody').append($("#schedule-instructions tbody tr:first").clone());
    $('#schedule-instructions tbody tr:last th').html("1-31");
    $('#schedule-instructions tbody tr:last td').html(wtssI18n.view.allowedValue);

    $('#schedule-instructions tbody').append($("#schedule-instructions tbody tr:first").clone());
    $('#schedule-instructions tbody tr:last').find('td').css({ 'class': 'danger' });
    $('#schedule-instructions tbody tr:last th').html("?");
    $('#schedule-instructions tbody tr:last td').html(wtssI18n.view.Space);
  });

  $("#schedule-month_input").click(function () {
    while ($("#schedule-instructions tbody tr:last").index() >= 4) {
      $("#schedule-instructions tbody tr:last").remove();
    }
    resetLabelColor();
    $("#schedule-mon_label").css("color", "red");
    $('#schedule-instructions tbody').append($("#schedule-instructions tbody tr:first").clone());
    $('#schedule-instructions tbody tr:last th').html("1-12");
    $('#schedule-instructions tbody tr:last td').html(wtssI18n.view.allowedValue);
  });

  $("#schedule-dow_input").click(function () {
    while ($("#schedule-instructions tbody tr:last").index() >= 4) {
      $("#schedule-instructions tbody tr:last").remove();
    }
    resetLabelColor();
    $("#schedule-dow_label").css("color", "red");

    $('#schedule-instructions tbody').append($("#schedule-instructions tbody tr:first").clone());
    $('#schedule-instructions tbody tr:last th').html("1-7");
    $('#schedule-instructions tbody tr:last td').html(wtssI18n.view.mondayToSunday);

    $('#schedule-instructions tbody').append($("#schedule-instructions tbody tr:first").clone());
    $('#schedule-instructions tbody tr:last th').html("?");
    $('#schedule-instructions tbody tr:last td').html(wtssI18n.view.Space);
  });

  $("#schedule-year_input").click(function () {
    while ($("#schedule-instructions tbody tr:last").index() >= 4) {
      $("#schedule-instructions tbody tr:last").remove();
    }
    resetLabelColor();
    $("#schedule-year_label").css("color", "red");

    $('#schedule-instructions tbody').append($("#schedule-instructions tbody tr:first").clone());
    $('#schedule-instructions tbody tr:last th').html("");
    $('#schedule-instructions tbody tr:last td').html("This field is optional");
  });

}

function resetLabelColor () {
  $("#schedule-min_label").css("color", "black");
  $("#schedule-hour_label").css("color", "black");
  $("#schedule-dom_label").css("color", "black");
  $("#schedule-mon_label").css("color", "black");
  $("#schedule-dow_label").css("color", "black");
  $("#schedule-year_label").css("color", "black");
}

var schedule_cron_minutes_id = "#schedule-minute_input";
var schedule_cron_hours_id = "#schedule-hour_input";
var schedule_cron_dom_id = "#schedule-dom_input";
var schedule_cron_months_id = "#schedule-month_input";
var schedule_cron_dow_id = "#schedule-dow_input";
var schedule_cron_output_id = "#schedule-cron-output";
var schedule_cron_translate_id = "#schedule-cronTranslate";
var schedule_cron_translate_warning_id = "#schedule-translationWarning";
var cron_year_id = "#schedule-year_input";

function updateScheduleOutput () {
  $(schedule_cron_output_id).val($(schedule_cron_minutes_id).val() + " " + $(schedule_cron_hours_id).val()
    + " " +
    $(schedule_cron_dom_id).val() + " " + $(schedule_cron_months_id).val() + " " + $(
      schedule_cron_dow_id).val() + " " + $(cron_year_id).val()
  );
  updateScheduleExpression();
}

function updateScheduleExpression () {
  $('#scheduleNextRecurId').html("");
  //如果表达式不符合规范,则不显示:调度时间预览TOP10.传给后台是自动添加了秒数:"0 " +
  if ("VALID" != validateQuartzStr("0 " + $(schedule_cron_output_id).val().trim())) {
    return;
  }
  // transformFromQuartzToUnixCron is defined in util/date.js
  var unixCronStr = transformFromQuartzToUnixCron($(schedule_cron_output_id).val());
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
      var nextTime = '<li style="color:DarkGreen">' + strTime.substring(1, strTime.length - 6) + '</li>';
      $('#scheduleNextRecurId').append(nextTime);

      serverTimeInJsDateFormat = occurrence;

      // Add 10 seconds to exclude startDate from the next occurrences
      serverTimeInJsDateFormat.setSeconds(serverTimeInJsDateFormat.getSeconds() + 10);
    } else {
      console.log("occurrence is null");
      break;
    }
  }
}

function updateJobCronExpression (evt) {
  $('#job-cron-top10').html("");
  //如果表达式不符合规范,则不显示:调度时间预览TOP10.传给后台是自动添加了秒数:"0 " +
  if ("VALID" != validateQuartzStr("59 59 23 " + $(evt.currentTarget).val().trim())) {
    return;
  }
  // transformFromQuartzToUnixCron is defined in util/date.js
  var unixCronStr = transformFromQuartzToUnixCron("59 23 " + $(evt.currentTarget).val().trim());
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
      var nextTime = '<li style="color:DarkGreen">' + strTime.split("T")[0].substr(1) + '</li>';
      $('#job-cron-top10').append(nextTime);

      serverTimeInJsDateFormat = occurrence;

      // Add 10 seconds to exclude startDate from the next occurrences
      serverTimeInJsDateFormat.setSeconds(serverTimeInJsDateFormat.getSeconds() + 10);
    } else {
      console.log("occurrence is null");
      break;
    }
  }
}


//用于保存浏览数据，切换页面也能返回之前的浏览进度。
var jobScheduleRetryModel;
azkaban.JobScheduleRetryModel = Backbone.Model.extend({});

var jobScheduleFailedRetryView;
azkaban.JobScheduleFailedRetryView = Backbone.View.extend({
  events: {
    "click table #add-schedule-failed-retry-btn": "handleAddRetryRow",
    //"click table .editable": "handleEditColumn",
    "click table .remove-btn": "handleRemoveColumn"
  },

  initialize: function (setting) {
  },

  //flow 执行成功错误告警设置
  handleAddRetryRow: function (evt) {

    var jobList = this.model.get("jobList");

    var retryTr = $("#jobScheduleFailedRetryTable tr").length - 1;
    if (retryTr == jobList.length) {
      $('#add-schedule-failed-retry-btn').attr('disabled', 'disabled');
    }


    var failedRetryTable = document.getElementById("jobScheduleFailedRetryTable").tBodies[0];
    var trRetry = failedRetryTable.insertRow(failedRetryTable.rows.length - 1);

    $(trRetry).addClass('jobRetryTr');
    //设置失败重跑 job 名称
    var cJob = trRetry.insertCell(-1);

    //var tr = $("<tr></tr>");
    //var td = $("<td></td>");

    var jobSelectId = "schedule-job-select" + failedRetryTable.rows.length;

    var idSelect = $("<select></select>");
    idSelect.attr("class", "form-control");
    idSelect.attr("id", jobSelectId);
    idSelect.attr("style", "width: 100%");
    // for (var i=0; i < jobList.length; i++) {
    //   idSelect.append("<option value='" + jobList[i] + "'>" + jobList[i] + "</option>");
    // }
    $(cJob).append(idSelect);

    //设置失败重跑时间间隔
    var cInterval = trRetry.insertCell(-1);
    var retryInterval = $("<input></input>");
    retryInterval.attr("class", "form-control");
    retryInterval.attr("id", "interval-input");
    retryInterval.attr("type", "number");
    retryInterval.attr("value", "1");
    retryInterval.attr("step", "1");
    retryInterval.attr("min", "1");
    retryInterval.attr("max", "86400");
    $(cInterval).append(retryInterval);

    //设置失败重跑次数
    var cCount = trRetry.insertCell(-1);
    var idSelect = $("<select></select>");
    idSelect.attr("class", "form-control");
    idSelect.attr("id", "count-select");
    for (var i = 1; i < 4; i++) {
      idSelect.append("<option value='" + i + "'>" + i + ' ' + wtssI18n.view.times + "</option>");
    }
    $(cCount).append(idSelect);

    //删除按钮
    var cDelete = trRetry.insertCell(-1);
    var remove = document.createElement("div");
    $(remove).addClass("center-block").addClass('remove-btn');
    var removeBtn = document.createElement("button");
    $(removeBtn).attr('type', 'button');
    $(removeBtn).addClass('btn').addClass('btn-sm').addClass('btn-danger');
    $(removeBtn).text('Delete');
    $(remove).append(removeBtn);
    cDelete.appendChild(remove);

    this.loadFlowJobListData(jobSelectId, jobScheduleRetryModel.get("flowId"));

    return trRetry;
  },

  handleRemoveColumn: function (evt) {
    var curTarget = evt.currentTarget;
    // Should be the table
    var row = curTarget.parentElement.parentElement;
    $(row).remove();

    var jobList = this.model.get("jobList");

    var retryTr = $("#jobScheduleFailedRetryTable tr").length - 2;
    if (retryTr < jobList.length) {
      $('#add-schedule-failed-retry-btn').removeAttr('disabled');
    }

  },

  getFlowRealJobList: function (flowId, jobList) {

    var requestURL = contextURL + "/manager?project=" + projectName;

    var model = this.model;

    var requestData = {
      "ajax": "fetchFlowRealJobLists",
      "flow": flowId,
    };
    var successHandler = function (data) {
      return data.jobList;
    };
    $.get(requestURL, requestData, successHandler, "json");

    var successHandler = function (data) {
      if (data.error) {
        console.log(data.error.message);
      }
      else {
        // var depList = data.webankDepartmentList;
        // for(var i=0; i<depList.length; i++){
        //   var department = depList[i];
        //   $('#update-wtss-department-select').append("<option value='" + department.dpId + "'>" + department.dpName + "</option>");
        // }
        //this.jobList = data.jobList;
        model.set({ "jobList": data.jobList });
      }
    }

    $.ajax({
      url: requestURL,
      type: "get",
      async: true,
      data: requestData,
      dataType: "json",
      error: function (data) {
        console.log(data);
      },
      success: successHandler
    });

  },

  setFlowID: function (flowId) {
    this.getFlowRealJobList(flowId, this.jobList);
    this.flowId = flowId;
    jobScheduleRetryModel.set("flowId", flowId);
  },

  loadFlowJobListData: function (selectId, flowId) {

    $("#" + selectId + "").select2({
      placeholder: wtssI18n.view.selectTaskPro,//默认文字提示
      multiple: false,
      width: 'resolve',
      //tags: true,//允许手动添加
      //allowClear: true,//允许清空
      escapeMarkup: function (markup) {
        return markup;
      }, //自定义格式化防止XSS注入
      minimumInputLengt: 1,//最少输入多少字符后开始查询
      formatResult: function formatRepo (repo) {
        return repo.text;
      },//函数用来渲染结果
      formatSelection: function formatRepoSelection (repo) {
        return repo.text;
      },//函数用于呈现当前的选择
      ajax: {
        type: 'GET',
        url: contextURL + "/manager?project=" + projectName,
        dataType: 'json',
        delay: 250,
        data: function (params) {
          var query = {
            ajax: "fetchFlowRealJobLists",
            flow: flowId,
            action: "retryFailedJob",
            serach: params.term,
            // page: params.page || 1,
            // pageSize: 20,
          }
          return query;
        },
        processResults: function (data, params) {
          params.page = params.page || 1;
          return {
            results: data.jobList,
            // pagination: {
            //   more: (params.page * 20) < data.webankUserTotalCount
            // }
          }
        },
        cache: true
      },
      language: 'zh-CN',

    });
  },

});

var scheduleJobCronView;
azkaban.ScheduleJobCronView = Backbone.View.extend({
  events: {
    "click #add-job-cron-btn": "handleAddRow",
    //"click table .editable": "handleEditColumn",
    "click #job-cron-table .remove-btn": "handleRemoveColumn",
    "blur #job-cron-table .cron": "showTop"
  },

  initialize: function (setting) {
  },
  showTop: function (evt) {
    console.log(evt);
    updateJobCronExpression(evt);
  },
  handleAddRow: function (evt) {
    var jobList = this.model.get("jobList");
    var retryTr = $("#job-cron-table tr").length - 1;
    if (retryTr == jobList.length) {
      $('#add-job-cron-btn').attr('disabled', 'disabled');
    }
    var skipFailedTable = document.getElementById("job-cron-table").tBodies[0];
    var trSkip = skipFailedTable.insertRow(skipFailedTable.rows.length - 1);
    $(trSkip).addClass('scheduleJobSkipTr');
    //设置失败重跑 job 名称
    var cJob = trSkip.insertCell(-1);
    var jobSelectId = "job-cron-select" + skipFailedTable.rows.length;
    var idSelect = $("<select></select>");
    idSelect.attr("class", "form-control");
    idSelect.attr("id", jobSelectId);
    idSelect.attr("style", "width: 100%");
    $(cJob).append(idSelect);

    //cron input
    /**
     <div class="col-sm-offset-1 col-sm-4">
       <div class="input-box">
        <input type="text" class="form-control">
        <span class="unit">0</span>
       </div>
     </div>
     * @type {HTMLElement}
     */
    var cronExpress = trSkip.insertCell(-1);
    var div1 = $('<div class="col-sm-offset-1 col-sm-4"/>');
    var div2 = $('<div class="input-box"/>');
    var input = $('<input type="text" class="form-control cron" style="padding-left: 48px;"/>');
    var span = $('<span class="unit">0 0 0</span>');
    $(div2).append($(input));
    $(div2).append($(span));
    $(div1).append($(div2));
    $(cronExpress).append(div1);

    //删除按钮
    var cDelete = trSkip.insertCell(-1);
    var remove = document.createElement("div");
    $(remove).addClass("center-block").addClass('remove-btn');
    var removeBtn = document.createElement("button");
    $(removeBtn).attr('type', 'button');
    $(removeBtn).addClass('btn').addClass('btn-sm').addClass('btn-danger');
    $(removeBtn).text('Delete');
    $(remove).append(removeBtn);
    cDelete.appendChild(remove);
    this.loadFlowJobListData(jobSelectId, this.model.get("flowId"));
  },
  handleRemoveColumn: function (evt) {
    var curTarget = evt.currentTarget;
    // Should be the table
    var row = curTarget.parentElement.parentElement;
    $(row).remove();
  },
  loadFlowJobListData: function (selectId, flowId) {

    $("#" + selectId + "").select2({
      placeholder: '请选择任务',//默认文字提示
      multiple: false,
      width: 'resolve',
      //tags: true,//允许手动添加
      //allowClear: true,//允许清空
      escapeMarkup: function (markup) {
        return markup;
      }, //自定义格式化防止XSS注入
      minimumInputLengt: 1,//最少输入多少字符后开始查询
      formatResult: function formatRepo (repo) {
        return repo.text;
      },//函数用来渲染结果
      formatSelection: function formatRepoSelection (repo) {
        return repo.text;
      },//函数用于呈现当前的选择
      ajax: {
        type: 'GET',
        url: contextURL + "/manager?project=" + projectName,
        dataType: 'json',
        delay: 250,
        data: function (params) {
          var query = {
            ajax: "fetchJobNestedIdList",
            flow: flowId,
            serach: params.term,
            // page: params.page || 1,
            // pageSize: 20,
          }
          return query;
        },
        processResults: function (data, params) {
          params.page = params.page || 1;
          return {
            results: data.jobList,
            // pagination: {
            //   more: (params.page * 20) < data.webankUserTotalCount
            // }
          }
        },
        cache: true
      },
      language: 'zh-CN',

    });
  },
});

//用于保存浏览数据，切换页面也能返回之前的浏览进度。
var scheduleJobSkipFailedModel;
azkaban.ScheduleJobSkipFailedModel = Backbone.Model.extend({});

var scheduleJobSkipFailedView;
azkaban.ScheduleJobSkipFailedView = Backbone.View.extend({
  events: {
    "click table #add-schedule-skip-failed-btn": "handleAddSkipRow",
    //"click table .editable": "handleEditColumn",
    "click table .remove-btn": "handleRemoveColumn"
  },

  initialize: function (setting) {
  },

  //flow 执行成功错误告警设置
  handleAddSkipRow: function (evt) {

    var jobList = this.model.get("jobList");

    var retryTr = $("#scheduleJobSkipFailedTable tr").length - 1;
    if (retryTr == jobList.length) {
      $('#add-schedule-skip-failed-btn').attr('disabled', 'disabled');
    }


    var skipFailedTable = document.getElementById("scheduleJobSkipFailedTable").tBodies[0];
    var trSkip = skipFailedTable.insertRow(skipFailedTable.rows.length - 1);

    $(trSkip).addClass('scheduleJobSkipTr');
    //设置失败重跑 job 名称
    var cJob = trSkip.insertCell(-1);

    //var tr = $("<tr></tr>");
    //var td = $("<td></td>");

    var jobSelectId = "schedule-job-skip-failed-select" + skipFailedTable.rows.length;

    var idSelect = $("<select></select>");
    idSelect.attr("class", "form-control");
    idSelect.attr("id", jobSelectId);
    idSelect.attr("style", "width: 100%");
    // for (var i=0; i < jobList.length; i++) {
    //   idSelect.append("<option value='" + jobList[i] + "'>" + jobList[i] + "</option>");
    // }
    $(cJob).append(idSelect);

    //删除按钮
    var cDelete = trSkip.insertCell(-1);
    var remove = document.createElement("div");
    $(remove).addClass("center-block").addClass('remove-btn');
    var removeBtn = document.createElement("button");
    $(removeBtn).attr('type', 'button');
    $(removeBtn).addClass('btn').addClass('btn-sm').addClass('btn-danger');
    $(removeBtn).text('Delete');
    $(remove).append(removeBtn);
    cDelete.appendChild(remove);

    this.loadFlowJobListData(jobSelectId, scheduleJobSkipFailedModel.get("flowId"));

    return trSkip;
  },

  handleRemoveColumn: function (evt) {
    var curTarget = evt.currentTarget;
    // Should be the table
    var row = curTarget.parentElement.parentElement;
    $(row).remove();

    var jobList = this.model.get("jobList");

    var retryTr = $("#scheduleJobSkipFailedTable tr").length - 2;
    if (retryTr < jobList.length) {
      $('#add-schedule-skip-failed-btn').removeAttr('disabled');
    }

  },

  getFlowRealJobList: function (flowId, jobList) {

    var requestURL = contextURL + "/manager?project=" + projectName;

    var model = this.model;

    var requestData = {
      "ajax": "fetchFlowRealJobLists",
      "flow": flowId,
    };
    var successHandler = function (data) {
      return data.jobList;
    };
    $.get(requestURL, requestData, successHandler, "json");

    var successHandler = function (data) {
      if (data.error) {
        console.log(data.error.message);
      }
      else {
        // var depList = data.webankDepartmentList;
        // for(var i=0; i<depList.length; i++){
        //   var department = depList[i];
        //   $('#update-wtss-department-select').append("<option value='" + department.dpId + "'>" + department.dpName + "</option>");
        // }
        //this.jobList = data.jobList;
        model.set({ "jobList": data.jobList });
      }
    }

    $.ajax({
      url: requestURL,
      type: "get",
      async: true,
      data: requestData,
      dataType: "json",
      error: function (data) {
        console.log(data);
      },
      success: successHandler
    });



  },

  setFlowID: function (flowId) {
    this.getFlowRealJobList(flowId, this.jobList);
    this.flowId = flowId;
    scheduleJobSkipFailedModel.set("flowId", flowId);
  },

  loadFlowJobListData: function (selectId, flowId) {

    $("#" + selectId + "").select2({
      placeholder: wtssI18n.view.selectTaskPro,//默认文字提示
      multiple: false,
      width: 'resolve',
      //tags: true,//允许手动添加
      //allowClear: true,//允许清空
      escapeMarkup: function (markup) {
        return markup;
      }, //自定义格式化防止XSS注入
      minimumInputLengt: 1,//最少输入多少字符后开始查询
      formatResult: function formatRepo (repo) {
        return repo.text;
      },//函数用来渲染结果
      formatSelection: function formatRepoSelection (repo) {
        return repo.text;
      },//函数用于呈现当前的选择
      ajax: {
        type: 'GET',
        url: contextURL + "/manager?project=" + projectName,
        dataType: 'json',
        delay: 250,
        data: function (params) {
          var query = {
            ajax: "fetchFlowRealJobLists",
            flow: flowId,
            action: "skipFailedJob",
            serach: params.term,
            // page: params.page || 1,
            // pageSize: 20,
          }
          return query;
        },
        processResults: function (data, params) {
          params.page = params.page || 1;
          return {
            results: data.jobList,
            // pagination: {
            //   more: (params.page * 20) < data.webankUserTotalCount
            // }
          }
        },
        cache: true
      },
      language: 'zh-CN',

    });
  },


});