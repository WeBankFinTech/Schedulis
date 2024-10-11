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

var flowExecuteDialogView;
azkaban.FlowExecuteDialogView = Backbone.View.extend({
  events: {
    "click .closeExecPanel": "hideExecutionOptionPanel",
    "click #schedule-btn": "scheduleClick",
    "click #execute-btn": "handleExecuteFlow",
    "click #history-recover-btn": "handleHistoryRecover",
  },

  initialize: function (settings) {
    this.model.bind('change:flowinfo', this.changeFlowInfo, this);
    $("#override-success-emails").click(function (evt) {
      if ($(this).is(':checked')) {
        $('#success-emails').attr('disabled', null);
      }
      else {
        $('#success-emails').attr('disabled', "disabled");
      }
    });


    $("#override-failure-emails").click(function (evt) {
      if ($(this).is(':checked')) {
        $('#failure-emails').attr('disabled', null);
      }
      else {
        $('#failure-emails').attr('disabled', "disabled");
      }
    });

    $('#datetimebegin').datetimepicker({
      format: 'YYYY/MM/DD',
      // cancel max current date limit
      // maxDate: new Date()
    });
    $('#datetimeend').datetimepicker({
      format: 'YYYY/MM/DD',
      // cancel max current date limit
      // maxDate: new Date()
    });
    $('#datetimebegin').on('change.dp', function (e) {
      $('#datetimeend').data('DateTimePicker').setStartDate(e.date);
    });
    $('#datetimeend').on('change.dp', function (e) {
      $('#datetimebegin').data('DateTimePicker').setEndDate(e.date);
    });

    $('#datetimebegin').val("");
    $('#datetimeend').val("");
    // 自然日重置
    $("#resetRunDateTime").click(function () {
      $('#runDateTime').val('')
    })
    //历史重跑开关点击事件添加
    $("#enable-history-recover").click(function (evt) {
      if ($(this).is(':checked')) {
        $('#datetimebegin').attr('disabled', null);
        $('#datetimeend').attr('disabled', null);
        $('#repeat-num').attr('disabled', null);
        $('#recover-interval').attr('disabled', null);
        $('#recover-error-option').attr('disabled', null);
        $('#runDateTime').attr('disabled', null);
      } else {
        $('#datetimebegin').attr('disabled', 'disabled');
        $('#datetimeend').attr('disabled', 'disabled');
        $('#repeat-num').attr('disabled', 'disabled');
        $('#recover-interval').attr('disabled', 'disabled');
        $('#recover-error-option').attr('disabled', 'disabled');
        $('#runDateTime').attr('disabled', 'disabled');
      }
    });

    // 点击历史重跑执行完成告警，告警列表启用
    $("#enable-history-recover-finished-alert").click(function (evt) {
      if ($(this).is(':checked')) {
        $('#flow-history-rerun-finish-emails').attr('disabled', null);
      } else {
        $('#flow-history-rerun-finish-emails').attr('disabled', "disabled");
      }
    })

    //循环执行开关点击事件添加
    $("#enable-cycle-execution").click(function () {
      if ($(this).is(":checked")) {
        $("#cycle-error-option").attr('disabled', null);
      } else {
        $("#cycle-error-option").attr('disabled', 'disabled');
      }
    });

    //前端校验循环执行和历史补采互斥
    $('li[viewpanel=cycle-execution-panel]').click(function () {
      if ($("#enable-history-recover").is(":checked")) {
        $("#enable-cycle-execution").attr("disabled", "disabled");
        $("#enable-cycle-execution").attr("checked", false);
        $("#cycle-error-option").attr('disabled', 'disabled');
      } else {
        $("#enable-cycle-execution").attr("disabled", null);
        $("#cycle-error-option").attr('disabled', null);
      }
    })
  },

  render: function () {
  },
  //校验超时告警设置是否正确
  checkTimeoutAlertSetting: function () {
    if (!$("#flow-timeout-option").is(":checked")) {
      console.log("unset")
      return true;
    }
    if ($("#timeout-email").is(":checked") == false && $("#timeout-killflow").is(":checked") == false) {
      alert(wtssI18n.view.workflowChoosePro);
      return false;
    }
    if ($("#timeout-email").is(":checked")) {
      if ($('#timeout-slaEmails').val() == undefined || $('#timeout-slaEmails').val() == "") {
        alert(wtssI18n.view.alarmAddressPro);
        return false;
      }
    }
    var t = $("#timeout-second").val();
    var re = /^\d{2}:\d{2}$/;
    if (!re.test(t)) {
      alert(wtssI18n.view.timeoutPro);
      return false;
    } else {
      var h = parseInt(t.split(":")[0]);
      var m = parseInt(t.split(":")[1]);
      if (!(h < 24) || !(m < 60)) {
        alert(wtssI18n.view.timeoutPro);
        return false;
      }
    }
    return true;
  },
  getJobOutputParamDate: function () {
    var jobOutputParam = {};
    var editRows = $("#jobOutputParam-editTable .editRow");
    for (var i = 0; i < editRows.length; ++i) {
      var row = editRows[i];
      var td = $(row).find('span');
      var key = $(td[0]).text();
      var val = $(td[1]).text();

      if (key && key.length > 0) {
        jobOutputParam[key] = val;
      }
    }
    return jobOutputParam;
  },

  getExecutionOptionData: function () {
    var failureAction = $('#failure-action').val();
    var failureEmails = $('#failure-emails').val();
    var successEmails = $('#success-emails').val();
    var notifyFailureFirst = $('#notify-failure-first').parent().attr('class').search('active') != -1 ? true : false;
    var notifyFailureLast = !notifyFailureFirst;
    var failureEmailsOverride = $("#override-failure-emails").is(':checked');
    var successEmailsOverride = $("#override-success-emails").is(':checked');
    //告警级别选择
    var failureAlertLevel = $('#override-failure-alert-level').val();
    var successAlertLevel = $('#override-success-alert-level').val();

    var flowOverride = {};
    var editRows = $("#editTable .editRow");
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
    var disabledList = gatherDisabledNodes(data);

    var jobFailedRetryOptions = {};
    // var jobRetryTrList = $(".jobRetryTr");
    // for (var i = 0; i < jobRetryTrList.length; ++i) {
    //   var row = jobRetryTrList[i];
    //   var jobSelect = $(row).find('job-select');


    //   var td = $(row).find('input');
    //   var key = $(td[0]).text();
    //   var val = $(td[1]).text();

    //   if (key && key.length > 0) {
    //     flowOverride[key] = val;
    //   }
    // }
    var tdFailedRetrys = document.getElementById("jobFailedRetryTable").tBodies[0];
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
    var tdSkipFaileds = document.getElementById("jobSkipFailedTable").tBodies[0];
    for (var row = 0; row < tdSkipFaileds.rows.length - 1; row++) {
      var tdSkipFailed = tdSkipFaileds.rows[row];
      var job = tdSkipFailed.cells[0].firstChild.value;
      jobSkipFailedOptions[row] = job;
    }

    //超时告警设置
    var useTimeoutSetting = $("#flow-timeout-option").is(":checked");
    var slaEmails = $('#timeout-slaEmails').val();
    var settings = {};
    settings[0] = "," + $("#timeout-status").val() + "," + $("#timeout-second").val() + ","
      + $("#timeout-level").val() + "," + $("#timeout-email").is(":checked") + ","
      + $("#timeout-killflow").is(":checked");

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
      useTimeoutSetting: useTimeoutSetting,
      slaEmails: slaEmails,
      settings: settings
    };

    // Set concurrency option, default is skip

    var concurrentOption = $('input[name=concurrent]:checked').val();
    executingData.concurrentOption = concurrentOption;
    if (concurrentOption == "pipeline") {
      var pipelineLevel = $("#pipeline-level").val();
      executingData.pipelineLevel = pipelineLevel;
    }
    else if (concurrentOption == "queue") {
      executingData.queueLevel = $("#queueLevel").val();
    }

    //检查是否有重复的规则
    if (checkFiledRetryRule(jobFailedRetryOptions)) {
      alert(wtssI18n.view.errorRerunRulePro);
      return;
    }

    return executingData;
  },

  changeFlowInfo: function () {
    var successEmails = this.model.get("successEmails").length == 0 ? [loginUser] : this.model.get("successEmails");
    var failureEmails = this.model.get("failureEmails").length == 0 ? [loginUser] : this.model.get("failureEmails");
    var historyEmails = this.model.get("historyEmails") ? this.model.get("historyEmails") : [loginUser];
    var cycleFlowEmails = this.model.get("cycleFlowEmails") ? this.model.get("cycleFlowEmails") : [loginUser];
    var failureActions = this.model.get("failureAction");
    var notifyFailure = this.model.get("notifyFailure");
    var flowParams = this.model.get("flowParams");
    //jobOutputGlobalParam
    var jobOutputGlobalParam = this.model.get("jobOutputGlobalParam");
    var isRunning = this.model.get("isRunning");
    var concurrentOption = this.model.get("concurrentOption");
    var pipelineLevel = this.model.get("pipelineLevel");
    var pipelineExecutionId = this.model.get("pipelineExecution");
    var queueLevel = this.model.get("queueLevel");
    var nodeStatus = this.model.get("nodeStatus");
    var overrideSuccessEmails = this.model.get("failureEmailsOverride");
    var overrideFailureEmails = this.model.get("successEmailsOverride");
    var enableHistoryRecover = this.model.get("enableHistoryRecover");

    if (overrideSuccessEmails) {
      $('#override-success-emails').attr('checked', true);
    }
    else {
      $('#override-success-emails').attr('checked', false);
      $('#success-emails').attr('disabled', 'disabled');
    }
    if (overrideFailureEmails) {
      $('#override-failure-emails').attr('checked', true);
    }
    else {
      $('#override-failure-emails').attr('checked', false);
      $('#failure-emails').attr('disabled', 'disabled');
    }

    if (successEmails) {
      $('#success-emails').val(successEmails.join());
    }
    if (overrideFailureEmails) {
      $('#override-failure-emails').attr('checked', true);
    } else {
      $('#override-failure-emails').attr('checked', false);
      $('#failure-emails').attr('disabled', 'disabled');
    }
    if (failureEmails) {
      $('#failure-emails').val(failureEmails.join());
    }
    if (failureActions) {
      $('#failure-action').val(failureActions);
    }

    if (notifyFailure && (notifyFailure.first || !notifyFailure.last)) {
      $('#notify-failure-first').attr('checked', true);
      $('#notify-failure-first').parent('.btn').addClass('active');
    } else {
      $('#notify-failure-last').attr('checked', true);
      $('#notify-failure-last').parent('.btn').addClass('active');
    }

    if (concurrentOption) {
      $('input[value=' + concurrentOption + '][name="concurrent"]').attr(
        'checked', true);
    }
    if (pipelineLevel) {
      $('#pipeline-level').val(pipelineLevel);
    }
    if (queueLevel) {
      $('#queueLevel').val(queueLevel);
    }

    if (flowParams && $("#editTable .editRow").length == 0) {
      for (var key in flowParams) {
        editTableView.handleAddRow({
          paramkey: key,
          paramvalue: flowParams[key]
        });
      }
    }
    if (jobOutputGlobalParam && $("#jobOutputParam-editTable .editRow").length == 0) {
      for (var key in jobOutputGlobalParam) {
        jobOutputParamEditTableView.handleAddRow({
          paramkey: key,
          paramvalue: jobOutputGlobalParam[key]
        });
      }
    }
    //历史重跑界面初始化方法
    if (enableHistoryRecover) {
      $('#datetimebegin').attr('disabled', null);
      $('#datetimeend').attr('disabled', null);
      $('#repeat-num').attr('disabled', null);
      $('#recover-interval').attr('disabled', null);
      $('#recover-error-option').attr('disabled', null);
      $('#runDateTime').attr('disabled', null);
      $('#flow-history-rerun-finish-emails').attr('disabled', null);
    }
    else {
      $("#enable-history-recover").attr('checked', false);
      $("#enable-reverse-execute-history-recover").attr('checked', false);
      $("#enable-history-recover-finished-alert").attr('checked', false);
      var currentTime = new Date()
      var year = currentTime.getFullYear()
      var month = currentTime.getMonth() + 1
      month = month > 9 ? month : '0' + month
      var day = currentTime.getDate()
      day = day > 9 ? day : '0' + day
      var timeStr = year + '/' + month + '/' + day
      $('#datetimebegin').attr('disabled', 'disabled').val(timeStr);
      $('#datetimeend').attr('disabled', 'disabled').val(timeStr);
      $('#repeat-num').attr('disabled', 'disabled').val('1');
      $('#recover-interval').attr('disabled', 'disabled').val('day');
      $('#runDateTime').attr('disabled', 'disabled').val('');
      $('#recover-error-option').attr('disabled', 'disabled').val('errorStop');
      $('#recover-Concurrent-option').val('1');
      $('#flow-history-rerun-finish-alert-level').val('INFO');
      $('#flow-history-rerun-finish-emails').attr('disabled', 'disabled');
      $('#id-show-start-five-date').click();
    }
    $('#flow-history-rerun-finish-emails').val(historyEmails.join());
    $('#cycleFlow-interrupt-emails').val(cycleFlowEmails.join());
    //初始化循环执行的页面
    $("#enable-cycle-execution").attr("checked", false);
    $("#cycle-error-option").attr('disabled', 'disabled');

  },

  show: function (data) {
    var projectName = data.project;
    var flowId = data.flow;
    var jobId = data.job;
    var executeFlowTitle = data.executeFlowTitle;

    // ExecId is optional
    var execId = data.execid;
    var exgraph = data.exgraph;

    this.projectName = projectName;
    this.flowId = flowId;

    var self = this;
    var loadCallback = function () {
      if (jobId) {
        self.showExecuteJob(executeFlowTitle, projectName, flowId, jobId, data.withDep);
      }
      else {
        self.showExecuteFlow(executeFlowTitle, projectName, flowId);
      }
    };
    if (execId) {
      this.loadGraph(projectName, flowId, exgraph, loadCallback);
    } else {
      this.loadGraphNew(projectName, flowId, exgraph, loadCallback);
    }

    this.loadFlowInfo(projectName, flowId, execId);
    this.initTimeoutPanel(flowId);

    //nsWtss为true才显示
    if (execId && this.model.get("nsWtss")) {
      $("#job-output-parameters-li").show();
    }

    $("#execute-div").show();
    $("#start-all-div").hide();
    $('#flow-option').show();
    $('#flow-execution-option').show();

    $("#show-start-five-date").prop("checked", true);
    $('#history-recover-li').show();
    $('#cycle-execution-li').show();

    // 判断是否设置了邮件,没有默认使用当前用户,有则保持数据不变
    var successEmails = this.model.get("successEmails");
    var failureEmails = this.model.get("failureEmails");


    if (successEmails.length != 0) {
      $('#success-emails').val(successEmails);
    }

    if (failureEmails.length != 0) {
      $('#failure-emails').val(failureEmails);
    }

    var overrideSuccessEmails = this.model.get("successEmailsOverride");
    var overrideFailureEmails = this.model.get("failureEmailsOverride");

    if (overrideSuccessEmails) {
      $('#override-success-emails').prop('checked', true);
      $('#success-emails').attr('disabled', false);
    } else {
      $('#override-success-emails').prop('checked', false);
      $('#success-emails').attr('disabled', 'disabled');
    }
    if (overrideFailureEmails) {
      $('#override-failure-emails').prop('checked', true);
      $('#failure-emails').attr('disabled', false);
    } else {
      $('#override-failure-emails').prop('checked', false);
      $('#failure-emails').attr('disabled', 'disabled');
    }

    var failureAlertLevel = this.model.get("failureAlertLevel");
    var successAlertLevel = this.model.get("successAlertLevel");

    var useTimeoutSetting = this.model.get("useTimeoutSetting");
    var ruleType = this.model.get("ruleType");
    var duration = this.model.get("duration");
    var slaAlertLevel = this.model.get("slaAlertLevel");
    var emailAction = this.model.get("emailAction");
    var killAction = this.model.get("killAction");

    $("#override-failure-alert-level").val(failureAlertLevel);
    $("#override-success-alert-level").val(successAlertLevel);

    if (useTimeoutSetting) {
      $('#flow-timeout-option').attr('checked', true);
      $("#flow-timeout-model").show();
      var slaEmails = this.model.get("slaEmails");
      $('#timeout-slaEmails').val(slaEmails);
    } else {
      $("#flow-timeout-model").hide();
      $('#timeout-slaEmails').val(loginUser);
    }

    if (ruleType == "FlowSucceed") {
      $("#timeout-status").val("SUCCESS");
    } else {
      $("#timeout-status").val("FINISH");
    }
    $("#timeout-second").val(duration);

    $("#timeout-level").val(slaAlertLevel);

    if (emailAction == "true") {
      $('#timeout-email').prop('checked', true);
    } else {
      $('#timeout-email').prop('checked', false);
    }

    if (killAction == "true") {
      $('#timeout-killflow').prop('checked', true);
    } else {
      $('#timeout-killflow').prop('checked', false);
    }

  },

  showExecuteFlow: function (executeFlowTitle, projectName, flowId) {
    $("#execute-flow-panel-title").text(executeFlowTitle + flowId);
    this.showExecutionOptionPanel();

    // Triggers a render
    this.model.trigger("change:graph");
  },

  showExecuteJob: function (executeFlowTitle, projectName, flowId, jobId, withDep) {
    sideMenuDialogView.menuSelect($("#flow-option"));
    $("#execute-flow-panel-title").text(executeFlowTitle + flowId);

    var data = this.model.get("data");
    var disabled = this.model.get("disabled");

    // Disable all, then re-enable those you want.
    disableAll();

    var jobNode = data.nodeMap[jobId];
    touchNode(jobNode, false);

    if (withDep) {
      recurseAllAncestors(jobNode, false);
    }

    this.showExecutionOptionPanel();
    this.model.trigger("change:graph");
  },

  showExecutionOptionPanel: function () {
    sideMenuDialogView.menuSelect($("#flow-option"));
    $('#execute-flow-panel').modal();
    jobFailedRetryView.setFlowID(this.flowId);
    jobSkipFailedView.setFlowID(this.flowId);
  },

  hideExecutionOptionPanel: function () {
    $('#execute-flow-panel').modal("hide");
  },

  scheduleClick: function () {
    console.log("click schedule button.");
    this.hideExecutionOptionPanel();
    schedulePanelView.showSchedulePanel();
  },

  loadFlowInfo: function (projectName, flowId, execId) {
    console.log("Loading flow " + flowId);
    fetchFlowInfo(this.model, projectName, flowId, execId);
  },

  initTimeoutPanel: function (flowId) {
    console.log("init timeout-panel");
    this.model.set({ "flowName": flowId });
    var useTimeoutSetting = this.model.get("useTimeoutSetting");
    if (useTimeoutSetting) {
      $('#flow-timeout-option').attr('checked', true);
    } else {
      $('#flow-timeout-option').attr('checked', false);
    }
    $("#timeout-slaEmails").val("");
    $("#timeout-status").val("SUCCESS");
    $("#timeout-second").val("");
    $("#timeout-level").val("INFO");
    $("#timeout-email").attr("checked", false);
    $("#timeout-killflow").attr("checked", false);
    $('.durationpick').datetimepicker({
      format: 'HH:mm'
    });
    $("#flow-timeout-model").hide();
  },

  loadGraph: function (projectName, flowId, exgraph, callback) {
    console.log("Loading flow " + flowId);
    var requestURL = contextURL + "/executor";

    var graphModel = executableGraphModel;
    // fetchFlow(this.model, projectName, flowId, true);
    var requestData = {
      "project": projectName,
      "ajax": "fetchexecutionflowgraph",
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
        el: $('#flow-executing-graph'),
        model: graphModel,
        render: false,
        rightClick: {
          "node": expanelNodeClickCallback,
          "edge": expanelEdgeClickCallback,
          "graph": expanelGraphClickCallback
        },
        tooltipcontainer: "#svg-div-custom"
      });

      if (callback) {
        callback.call(this);
      }
    };
    $.get(requestURL, requestData, successHandler, "json");
  },

  loadGraphNew: function (projectName, flowId, exgraph, callback) {
    console.log("Loading flow " + flowId);
    var requestURL = contextURL + "/executor";

    var graphModel = executableGraphModel;
    // fetchFlow(this.model, projectName, flowId, true);
    var requestData = {
      "project": projectName,
      "ajax": "fetchexecutionflowgraphNew",
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
        el: $('#flow-executing-graph'),
        model: graphModel,
        render: false,
        rightClick: {
          "node": expanelNodeClickCallback,
          "edge": expanelEdgeClickCallback,
          "graph": expanelGraphClickCallback
        },
        tooltipcontainer: "#svg-div-custom"
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

  handleExecuteFlow: function (evt) {
    console.log("click schedule button.");
    if (!this.checkTimeoutAlertSetting()) {
      return;
    }
    var executeURL = contextURL + "/executor";
    var executingData = this.getExecutionOptionData();
    //todo 历史重跑功能和循环执行功能在前端需要做互斥校验
    //判断是否启用历史重跑功能
    if ($("#enable-history-recover").is(':checked')) {
      this.handleHistoryRecover();
    } else if ($("#enable-cycle-execution").is(':checked')) {
      this.handleCycleExecution();
    } else {
      if (typeof execId != "undefined" && execId) {
        console.log("lastExecId is " + execId);
        executingData.lastExecId = execId;
        executingData.jobOutputParam = this.getJobOutputParamDate();
        executingData.lastNsWtss = this.model.get("nsWtss");
      }
      executeFlow(executingData);
    }
  },

  handleHistoryRecover: function (evt) {
    console.log("click History Recover button.");
    if (!this.checkTimeoutAlertSetting()) {
      return;
    }

    var executingData = this.getExecutionOptionData();
    //单独的JS方法处理历史补采
    HistoryRecoverExecute(executingData);
  },

  userHistoryRecover: function (evt) {
    console.log("userHistoryRecover.");
    var beginTime = $('#datetimebegin').val();
    var endTime = $('#datetimeend').val();
    var monthNum = $('#repeat-month').val();
    var dayNum = $('#repeat-day').val();
    if (beginTime || endTime || 0 != monthNum || 0 != dayNum) {
      return true;
    } else {
      return false;
    }
  },

  reloadWindow: function () {
    window.location.reload();
  },

  handleCycleExecution: function () {
    console.log("click Cycle Execution button.");
    if (!this.checkTimeoutAlertSetting()) {
      return;
    }
    var executingData = this.getExecutionOptionData();
    executeCycleFlow(executingData);
  }

});

var editTableView;
azkaban.EditTableView = Backbone.View.extend({
  events: {
    "click #add-btn": "handleAddRow",
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

    $(tr).insertBefore("#editTable .addRow");
    return tr;
  },

  handleEditColumn: function (evt) {
    if (evt.target.tagName == "INPUT") {
      return;
    }
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

var jobOutputParamEditTableView;
azkaban.JobOutputParamEditTableView = Backbone.View.extend({
  events: {
    "click #jobOutputParam-add-btn": "handleAddRow",
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

    $(tr).insertBefore("#jobOutputParam-editTable .addRow");
    return tr;
  },

  handleEditColumn: function (evt) {
    if (evt.target.tagName == "INPUT") {
      return;
    }
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

var sideMenuDialogView;
azkaban.SideMenuDialogView = Backbone.View.extend({
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
    if ((target[0] && target[0].id === "flow-option") || target.id === "flow-option") {
      $("#switching-execute-flow-btn").show()
      $("#workflow-execute-zoom-in").show()
    } else {
      $("#switching-execute-flow-btn").hide()
      $("#workflow-execute-zoom-in").hide()
      $("#workflow-execute-zoom-out").hide()
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

var handleJobMenuClick = function (action, el, pos) {
  var jobid = el[0].jobid;

  var requestURL = contextURL + "/manager?project=" + projectName + "&flow="
    + flowName + "&job=" + jobid;
  if (action == "open") {
    window.location.href = requestURL;
  }
  else if (action == "openwindow") {
    window.open(requestURL);
  }
}

var executableGraphModel;

/**
 * Disable jobs that need to be disabled
 */
var disableFinishedJobs = function (data) {
  for (var i = 0; i < data.nodes.length; ++i) {
    var node = data.nodes[i];
    if (data.type === 'flow' && data.disabled) {
      node.status = "READY";
      node.disabled = true;
    } else if (node.status == "DISABLED" || node.status == "SKIPPED") {
      node.status = "READY";
      node.disabled = true;
    }
    else if (node.status == "SUCCEEDED" || node.status == "RETRIED_SUCCEEDED" || node.noInitialStatus) {
      node.disabled = true;
    }
    else {
      node.disabled = false;
    }
    if (node.type == "flow") {
      disableFinishedJobs(node);
    }
  }
}

/**
 * Enable all jobs. Recurse
 */
var enableAll = function () {
  recurseTree(executableGraphModel.get("data"), false, true);
  executableGraphModel.trigger("change:disabled");
}

var disableAll = function () {
  recurseTree(executableGraphModel.get("data"), true, true);
  executableGraphModel.trigger("change:disabled");
}


var recurseTree = function (data, disabled, recurse) {
  for (var i = 0; i < data.nodes.length; ++i) {
    var node = data.nodes[i];
    node.disabled = disabled;

    if (node.type == "flow" && recurse) {
      recurseTree(node, disabled, recurse);
    }
  }
}
// type 为执行类型datachecker--所有datacheck  eventchecker--所有eventchecker/rmbsender(所有信号)  outer--所有外部信息  disabled--true关闭  false开启
var touchTypecheck = function (type, disabled) {
  conditionRecurseTree(executableGraphModel.get("data"), disabled, true, type);
  executableGraphModel.trigger("change:disabled");
}

var conditionRecurseTree = function (data, disable, recurse, type) {
  function typeSubflowDisable (data, disable) {
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
          typeSubflowDisable(data, disable);
        }
        break;
      case 'eventchecker':
        if (node.type === 'eventchecker' || node.type === 'rmbsender') {
          node.disabled = disable;
          typeSubflowDisable(data, disable);
        }
        break;
      case 'outer':
        if (node.outer === true || node.outer === 'true') {
          node.disabled = disable;
          typeSubflowDisable(data, disable);
        }
        break;
      default:
        break;
    }
    if (node.type == "flow" && recurse) {
      conditionRecurseTree(node, disable, recurse, type);
    }
  }
}


// 打开关闭节点，如果节点类型为flow则需要打开关闭subfolw流程所有节点
var executDisableSubflow = function (single, node, disable) {
  if (!node) return;
  if (single) {
    node.disabled = disable;
    checkJobType(node, disable);
    if (!disable) {
      executEnableSubflow(node);
    }
  } else {
    var count = 0;
    for (var key in node) {
      if (count === 0 && !disable) {
        executEnableSubflow(node[key]);
      }
      node[key].disabled = disable;
      checkJobType(node[key], disable);
      count++;
    }
  }
}

function checkJobType (node, disable) {
  if (node.type == "flow") {
    recurseTree(node, disable, true);
  }
}
// 启用工作流如果父流程节点为disable要先把父节点disable改成true
var executEnableSubflow = function (node) {
  var executData = executableGraphModel.get("data")
  var parantArr = []
  var findNode = { isFind: false }
  executEnableSubflowTree(executData, parantArr, node, findNode)
}

var executEnableSubflowTree = function (executData, parantArr, node, findNode) {
  for (var i = 0; i < executData.nodes.length; ++i) {
    if (findNode.isFind) {
      return
    }
    var item = executData.nodes[i];
    if (item.nestedId === node.nestedId) {
      for (var j = 0; j < parantArr.length; j++) {
        parantArr[j].disabled = false
      }
      findNode.isFind = true
      return
    }
    if (item.type == "flow") {
      parantArr.push(item)
      executEnableSubflowTree(item, parantArr, node, findNode)
      parantArr.splice(parantArr.length - 1, 1)
    }
  }
}


var touchNode = function (node, disable) {
  node.disabled = disable;
  executDisableSubflow(true, node, disable);
  executableGraphModel.trigger("change:disabled");
}

var touchParents = function (node, disable) {
  var inNodes = node.inNodes;

  executDisableSubflow(false, inNodes, disable);

  executableGraphModel.trigger("change:disabled");
}

var touchChildren = function (node, disable) {
  var outNodes = node.outNodes;

  executDisableSubflow(false, outNodes, disable);

  executableGraphModel.trigger("change:disabled");
}

var touchAncestors = function (node, disable) {
  var inNodes = node.inNodes;
  if (inNodes && !disable) {
    var key = Object.keys(inNodes)[0]
    executEnableSubflow(inNodes[key])
  }
  recurseAllAncestors(node, disable);

  executableGraphModel.trigger("change:disabled");
}

var touchDescendents = function (node, disable) {
  var outNodes = node.outNodes;
  if (outNodes && !disable) {
    var key = Object.keys(outNodes)[0]
    executEnableSubflow(outNodes[key])
  }
  recurseAllDescendents(node, disable);

  executableGraphModel.trigger("change:disabled");
}

var gatherDisabledNodes = function (data) {
  var nodes = data.nodes;
  var disabled = [];

  for (var i = 0; i < nodes.length; ++i) {
    var node = nodes[i];
    if (node.disabled) {
      disabled.push(node.id);
    }
    else {
      if (node.type == "flow") {
        var array = gatherDisabledNodes(node);
        if (array && array.length > 0) {
          disabled.push({ id: node.id, children: array });
        }
      }
    }
  }

  return disabled;
}

function recurseAllAncestors (node, disable) {
  var inNodes = node.inNodes;
  if (inNodes) {
    for (var key in inNodes) {
      inNodes[key].disabled = disable;
      checkJobType(inNodes[key], disable);
      recurseAllAncestors(inNodes[key], disable);
    }
  }
}

function recurseAllDescendents (node, disable) {
  var outNodes = node.outNodes;
  if (outNodes) {
    for (var key in outNodes) {
      outNodes[key].disabled = disable;
      checkJobType(outNodes[key], disable);
      recurseAllDescendents(outNodes[key], disable);
    }
  }
}

var expanelNodeClickCallback = function (event, model, node) {
  console.log("Node clicked callback");
  var jobId = node.id;
  var flowId = executableGraphModel.get("flowId");
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
          title: wtssI18n.common.collapseAllFlow, callback: function () {
            model.trigger("collapseAllFlows", node);
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
          title: wtssI18n.common.expandAllFlow, callback: function () {
            model.trigger("expandAllFlows", node);
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
        touchNode(node, false);
      }, submenu: [
        {
          title: wtssI18n.view.openParentNode, callback: function () {
            touchParents(node, false);
          }
        },
        {
          title: wtssI18n.view.openPreviousNode, callback: function () {
            touchAncestors(node, false);
          }
        },
        {
          title: wtssI18n.view.openChildNode, callback: function () {
            touchChildren(node, false);
          }
        },
        {
          title: wtssI18n.view.openDescendantNodes, callback: function () {
            touchDescendents(node, false);
          }
        },
        {
          title: wtssI18n.view.openDatacheck, callback: function () {
            touchTypecheck('datachecker', false);
          }
        },
        {
          title: wtssI18n.view.openSignal, callback: function () {
            touchTypecheck('eventchecker', false);
          }
        },
        {
          title: wtssI18n.view.openOuterSignal, callback: function () {
            touchTypecheck('outer', false);
          }
        },
        {
          title: wtssI18n.view.openAll, callback: function () {
            enableAll();
          }
        }
      ]
    },
    {
      title: wtssI18n.view.close, callback: function () {
        touchNode(node, true)
      }, submenu: [
        {
          title: wtssI18n.view.closeParentNode, callback: function () {
            touchParents(node, true);
          }
        },
        {
          title: wtssI18n.view.closePreviousNode, callback: function () {
            touchAncestors(node, true);
          }
        },
        {
          title: wtssI18n.view.closeChildNode, callback: function () {
            touchChildren(node, true);
          }
        },
        {
          title: wtssI18n.view.closeDescendantNodes, callback: function () {
            touchDescendents(node, true);
          }
        },
        {
          title: wtssI18n.view.closeDatacheck, callback: function () {
            touchTypecheck('datachecker', true);
          }
        },
        {
          title: wtssI18n.view.closeSignal, callback: function () {
            touchTypecheck('eventchecker', true);
          }
        },
        {
          title: wtssI18n.view.closeOuterSignal, callback: function () {
            touchTypecheck('outer', true);
          }
        },
        {
          title: wtssI18n.view.closeAll, callback: function () {
            disableAll();
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

  contextMenuView.show(event, menu);
}

var expanelEdgeClickCallback = function (event) {
  console.log("Edge clicked callback");
}

var expanelGraphClickCallback = function (event) {
  console.log("Graph clicked callback");
  var flowId = executableGraphModel.get("flowId");
  var requestURL = contextURL + "/manager?project=" + projectName + "&flow="
    + flowId;

  var menu = [
    {
      title: wtssI18n.common.expandAllWorkflow, callback: function () {
        executableGraphModel.trigger("expandAllFlows");
        executableGraphModel.trigger("resetPanZoom");
      }
    },
    {
      title: wtssI18n.common.collapseAllWorkflow, callback: function () {
        executableGraphModel.trigger("collapseAllFlows");
        executableGraphModel.trigger("resetPanZoom");
      }
    },
    {
      title: wtssI18n.common.openNewWindow, callback: function () {
        window.open(requestURL);
      }
    },
    { break: 1 },
    {
      title: wtssI18n.view.openAll, callback: function () {
        enableAll();
      }
    },
    {
      title: wtssI18n.view.closeAll, callback: function () {
        disableAll();
      }
    },
    { break: 1 },
    {
      title: wtssI18n.common.centerGraph, callback: function () {
        executableGraphModel.trigger("resetPanZoom");
      }
    }
  ];

  contextMenuView.show(event, menu);
}

var contextMenuView;
$(function () {
  executableGraphModel = new azkaban.GraphModel();
  flowExecuteDialogView = new azkaban.FlowExecuteDialogView({
    el: $('#execute-flow-panel'),
    model: executableGraphModel
  });

  sideMenuDialogView = new azkaban.SideMenuDialogView({
    el: $('#graph-options')
  });
  editTableView = new azkaban.EditTableView({
    el: $('#editTable')
  });

  //  jobOutputParamEditTableView
  jobOutputParamEditTableView = new azkaban.JobOutputParamEditTableView({
    el: $('#jobOutputParam-editTable')
  });

  contextMenuView = new azkaban.ContextMenuView({
    el: $('#contextMenu')
  });

  $(document).keyup(function (e) {
    // escape key maps to keycode `27`
    if (e.keyCode == 27) {
      flowExecuteDialogView.hideExecutionOptionPanel();
      //flowExecuteDialogView.remove();
    }
  });

  jobRetryModel = new azkaban.JobRetryModel();

  jobFailedRetryView = new azkaban.JobFailedRetryView({
    el: $('#job-failed-retry-view'),
    model: jobRetryModel,
  });

  jobSkipFailedModel = new azkaban.JobSkipFailedModel();

  jobSkipFailedView = new azkaban.JobSkipFailedView({
    el: $('#job-skip-failed-view'),
    model: jobSkipFailedModel,
  });

  timeoutAlertView = new azkaban.TimeoutAlertView({
    el: $('#execution-graph-options-panel'),
    model: executableGraphModel
  });

  $("#switching-execute-flow-btn").on('click', function () {
    var trimFlowName = !JSON.parse(sessionStorage.getItem('trimFlowName'));//标识是否剪切节点名称
    sessionStorage.setItem('trimFlowName', trimFlowName)
    executingReRenderWorflow(true)
  })
  $("#workflow-execute-zoom-in").on('click', function () {
    $(this).hide()
    $("#workflow-execute-zoom-out").show()
    $('#execute-flow-panel .modal-header').hide()
    $('#execute-flow-panel .modal-footer').hide()
    $('#execution-graph-options-box').hide()
    $('#execution-graph-panel-box').removeClass('col-xs-8').addClass('col-xs-12')
    $('#execute-flow-panel .modal-dialog')[0].style.width = "98%"
    $('#flow-executing-graph')[0].style.height = window.innerHeight * 0.88
    executingZoomInWorflow() // 参数是否切换工作流
  })
  $("#workflow-execute-zoom-out").on('click', function () {
    $(this).hide()
    $("#workflow-execute-zoom-in").show()
    $('#execute-flow-panel .modal-header').show()
    $('#execute-flow-panel .modal-footer').show()
    $('#execution-graph-options-box').show()
    $('#execution-graph-panel-box').removeClass('col-xs-12').addClass('col-xs-8')
    $('#execute-flow-panel .modal-dialog')[0].style.width = "80%"
    $('#flow-executing-graph')[0].style.height = '500px'
    executingZoomInWorflow() // 参数是否切换工作流
  })
});
// 放大缩小重新收拢工作流，并居中
function executingZoomInWorflow () {
  executingSvgGraphView.collapseAllFlows()
  executingSvgGraphView.resetPanZoom()
}

function executingReRenderWorflow (switchingFlow) {
  var data = executingSvgGraphView.model.get('data') //获取流程图数据
  if (switchingFlow) {
    data.switchingFlow = true
  }
  $(executingSvgGraphView.mainG).empty() //清空流程图
  executingSvgGraphView.renderGraph(data, executingSvgGraphView.mainG)
}


//用于保存浏览数据，切换页面也能返回之前的浏览进度。
var jobRetryModel;
azkaban.JobRetryModel = Backbone.Model.extend({});

var jobFailedRetryView;
azkaban.JobFailedRetryView = Backbone.View.extend({
  events: {
    "click table #add-failed-retry-btn": "handleAddRetryRow",
    //"click table .editable": "handleEditColumn",
    "click table .remove-btn": "handleRemoveColumn"
  },

  initialize: function (setting) {
  },

  //每次回填数据时清除旧数据
  clearTable: function () {
    $("#jobFailedRetryTable .jobRetryTr").remove();
  },

  //flow 执行成功错误告警设置
  handleAddRetryRowCallShow: function (data, jobFailedRetryOptions, jobList) {

    var job = "";
    if (data.job) {
      job = data.job;
    }
    var interval = "";
    if (data.interval) {
      interval = data.interval;
    }
    var count = "";
    if (data.count) {
      count = data.count;
    }

    var retryTr = $("#jobFailedRetryTable tr").length - 1;
    // if(null != jobFailedRetryOptions && retryTr >= jobFailedRetryOptions.length){
    //   $('#add-schedule-failed-retry-btn').attr('disabled','disabled');
    // }
    if (null != jobList && retryTr > jobList.length) {
      $('#add-failed-retry-btn').attr('disabled', 'disabled');
      alert(wtssI18n.view.failedErrorFormat);
      return;
    }


    var failedRetryTable = document.getElementById("jobFailedRetryTable").tBodies[0];
    var trRetry = failedRetryTable.insertRow(failedRetryTable.rows.length - 1);

    $(trRetry).addClass('jobRetryTr');
    //设置失败重跑 job 名称
    var cJob = trRetry.insertCell(-1);

    var jobSelectId = "job-select" + failedRetryTable.rows.length;

    var idSelect = $("<select></select>");
    idSelect.attr("class", "form-control");
    idSelect.attr("id", jobSelectId);
    idSelect.attr("style", "width: 100%");
    $(cJob).append(idSelect);
    for (var i = 0; i < jobList.length; i++) {
      idSelect.append("<option value='" + jobList[i].id + "'>" + jobList[i].id + "</option>");
    }

    this.loadFlowJobListData(jobSelectId, jobRetryModel.get("flowId"), jobRetryModel.get("projectName"));
    //回显新增的数据,如果是新增一行,就没有回显
    if (job) {
      $("#" + jobSelectId).val(job).select2();
    }

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
    $(retryInterval).val(interval);
    $(cInterval).append(retryInterval);

    //设置失败重跑次数
    var cCount = trRetry.insertCell(-1);
    var idSelect = $("<select></select>");
    idSelect.attr("class", "form-control");
    idSelect.attr("id", "count-select");
    for (var i = 1; i < 4; i++) {
      idSelect.append("<option value='" + i + "'>" + i + " " + wtssI18n.view.times + "</option>");
    }
    if ("" != count) {
      idSelect.val(count);
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

    if (null != jobList && retryTr >= jobList.length) {
      $('#add-failed-retry-btn').attr('disabled', 'disabled');
    }

    return trRetry;
  },

  //flow 执行成功错误告警设置
  handleAddRetryRow: function (evt) {

    var jobList = this.model.get("jobList");

    var retryTr = $("#jobFailedRetryTable tr").length - 1;
    if (retryTr == jobList.length) {
      $('#add-failed-retry-btn').attr('disabled', 'disabled');
    }


    var failedRetryTable = document.getElementById("jobFailedRetryTable").tBodies[0];
    var trRetry = failedRetryTable.insertRow(failedRetryTable.rows.length - 1);

    $(trRetry).addClass('jobRetryTr');
    //设置失败重跑 job 名称
    var cJob = trRetry.insertCell(-1);
    $(cJob).attr("style", "width: 65%");
    //var tr = $("<tr></tr>");
    //var td = $("<td></td>");


    var jobSelectId = "job-select" + failedRetryTable.rows.length;

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
    $(cInterval).attr("style", "width: 10%");
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
    $(cCount).attr("style", "width: 15%");
    var idSelect = $("<select></select>");
    idSelect.attr("class", "form-control");
    idSelect.attr("id", "count-select");
    var idSelectHtml = ''
    for (var i = 1; i < 4; i++) {
      idSelectHtml += "<option value='" + i + "'>" + i + " " + wtssI18n.view.times + "</option>"
    }
    idSelect.append(idSelectHtml);
    $(cCount).append(idSelect);

    //删除按钮
    var cDelete = trRetry.insertCell(-1);
    var remove = document.createElement("div");
    $(remove).addClass("center-block");
    var removeBtn = document.createElement("button");
    $(removeBtn).attr('type', 'button');
    $(removeBtn).addClass('btn btn-sm remove-btn btn-danger');
    $(removeBtn).text('Delete');
    $(remove).append(removeBtn);
    cDelete.appendChild(remove);

    this.loadFlowJobListData(jobSelectId, jobRetryModel.get("flowId"));

    return trRetry;
  },

  handleRemoveColumn: function (evt) {
    var curTarget = evt.currentTarget;
    // Should be the table
    var row = curTarget.parentElement.parentElement.parentElement;
    $(row).remove();

    var jobList = this.model.get("jobList");

    var retryTr = $("#jobFailedRetryTable tr").length - 2;
    if (retryTr < jobList.length) {
      $('#add-failed-retry-btn').removeAttr('disabled');
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

        var jobFailedRetryOptions = flowExecuteDialogView.model.get("jobFailedRetryOptions");
        jobFailedRetryView.clearTable();
        //错误重试设置数据回填
        if (jobFailedRetryOptions) {
          for (var i = 0; i < jobFailedRetryOptions.length; i++) {
            var retryOption = jobFailedRetryOptions[i];
            jobFailedRetryView.handleAddRetryRowCallShow({
              job: retryOption["jobName"],
              interval: retryOption["interval"],
              count: retryOption["count"],
            }, jobFailedRetryOptions, data.jobList);
          }
        }

        var jobSkipFailedOptions = flowExecuteDialogView.model.get("jobSkipFailedOptions");
        jobSkipFailedView.clearJobSkipFailedTable();
        if (jobSkipFailedOptions) {
          //循环填充选项
          for (var i = 0; i < jobSkipFailedOptions.length; i++) {
            var retryOption = jobSkipFailedOptions[i];
            jobSkipFailedView.handleAddSkipRowCallShow({
              job: retryOption,
            }, jobSkipFailedOptions, data.jobList);
          }
        }

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
    jobRetryModel.set("flowId", flowId);
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


function checkFiledRetryRule (data) {
  var new_arr = [];
  var oldlength = 0;
  for (var i in data) {
    oldlength++;
    var items = data[i].substring(0, find(data[i], ",", 1));;
    //判断元素是否存在于new_arr中，如果不存在则插入到new_arr的最后
    if ($.inArray(items, new_arr) == -1) {
      new_arr.push(items);
    }
  }
  if (new_arr.length < oldlength) {
    return true;
  }
}

function find (str, cha, num) {
  var x = str.indexOf(cha);
  for (var i = 0; i < num; i++) {
    x = str.indexOf(cha, x + 1);
  }
  return x;
}


//用于保存浏览数据，切换页面也能返回之前的浏览进度。
var jobSkipFailedModel;
azkaban.JobSkipFailedModel = Backbone.Model.extend({});

var jobSkipFailedView;
azkaban.JobSkipFailedView = Backbone.View.extend({
  events: {
    "click table #add-skip-failed-btn": "handleAddSkipRow",
    //"click table .editable": "handleEditColumn",
    "click table .remove-btn": "handleRemoveColumn"
  },

  initialize: function (setting) {
  },

  //每次回填数据时清除旧数据
  clearJobSkipFailedTable: function () {
    $("#set-job-skip-failed-tbody").children(".jobSkipTr").remove();
  },

  //flow 失败跳过设置回显
  handleAddSkipRowCallShow: function (data, jobskipFailedOptions, jobList) {

    var jobName = data.job;

    var skipTr = $("#jobSkipFailedTable tr").length - 1;
    if (null != jobList && skipTr >= jobList.length) {
      $('#add-skip-failed-btn').attr('disabled', 'disabled');
    }

    var skipFailedTable = document.getElementById("jobSkipFailedTable").tBodies[0];
    var trSkip = skipFailedTable.insertRow(skipFailedTable.rows.length - 1);

    $(trSkip).addClass('jobSkipTr');
    //设置失败重跑 job 名称
    var cJob = trSkip.insertCell(-1);
    $(cJob).attr("style", "width: 80%");
    var jobSelectId = "job-skip-failed-select" + skipFailedTable.rows.length;

    var idSelect = $("<select></select>");
    idSelect.attr("class", "form-control");
    idSelect.attr("id", jobSelectId);
    idSelect.attr("style", "width: 100%");
    var optionHtml = ''
    for (var i = 0; i < jobList.length; i++) {
      optionHtml += "<option value='" + jobList[i].id + "'>" + jobList[i].id + "</option>"
    }
    idSelect.append(optionHtml);

    $(cJob).append(idSelect);

    //删除按钮
    var cDelete = trSkip.insertCell(-1);
    var remove = document.createElement("div");
    $(remove).addClass("center-block");
    var removeBtn = document.createElement("button");
    $(removeBtn).attr('type', 'button');
    $(removeBtn).addClass('btn btn-sm remove-btn btn-danger');
    $(removeBtn).text('Delete');
    $(remove).append(removeBtn);
    cDelete.appendChild(remove);

    if (jobName) {
      $("#" + jobSelectId).val(jobName).select2();
    }
    this.loadFlowJobListData(jobSelectId, jobSkipFailedModel.get("flowId"), jobSkipFailedModel.get("projectName"));
    return trSkip;
  },

  //flow 执行成功错误告警设置
  handleAddSkipRow: function (evt) {

    var jobList = this.model.get("jobList");

    var retryTr = $("#jobSkipFailedTable tr").length - 1;
    if (retryTr == jobList.length) {
      $('#add-skip-failed-btn').attr('disabled', 'disabled');
    }


    var skipFailedTable = document.getElementById("jobSkipFailedTable").tBodies[0];
    var trSkip = skipFailedTable.insertRow(skipFailedTable.rows.length - 1);

    $(trSkip).addClass('jobSkipTr');
    //设置失败重跑 job 名称
    var cJob = trSkip.insertCell(-1);
    $(cJob).attr("style", "width: 80%");
    //var tr = $("<tr></tr>");
    //var td = $("<td></td>");

    var jobSelectId = "job-skip-failed-select" + skipFailedTable.rows.length;

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
    $(remove).addClass("center-block");
    var removeBtn = document.createElement("button");
    $(removeBtn).attr('type', 'button');
    $(removeBtn).addClass('btn btn-sm remove-btn btn-danger');
    $(removeBtn).text('Delete');
    $(remove).append(removeBtn);
    cDelete.appendChild(remove);

    this.loadFlowJobListData(jobSelectId, jobSkipFailedModel.get("flowId"));

    return trSkip;
  },

  handleRemoveColumn: function (evt) {
    var curTarget = evt.currentTarget;
    // Should be the table
    var row = curTarget.parentElement.parentElement.parentElement;
    $(row).remove();

    var jobList = this.model.get("jobList");

    var retryTr = $("#jobSkipFailedTable tr").length - 2;
    if (retryTr < jobList.length) {
      $('#add-skip-failed-btn').removeAttr('disabled');
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
    jobSkipFailedModel.set("flowId", flowId);
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

var timeoutAlertView;
azkaban.TimeoutAlertView = Backbone.View.extend({
  events: {
    "change #flow-timeout-option": "flowTimeoutOption"
  },

  flowTimeoutOption: function (evt) {
    console.log("timeout option changed.");
    if (evt.currentTarget.checked) {
      $("#flow-timeout-model").show();
    } else {
      $("#flow-timeout-model").hide();
    }

  }
});
