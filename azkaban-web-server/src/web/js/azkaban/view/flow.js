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
var handleJobMenuClick = function (action, el, pos) {
  var jobid = el[0].jobid;
  var requestURL = contextURL + "/manager?project=" + projectName + "&flow=" +
    flowId + "&job=" + jobid;
  if (action == "open") {
    window.location.href = requestURL;
  }
  else if (action == "openwindow") {
    window.open(requestURL);
  }
}

var flowTabView;
azkaban.FlowTabView = Backbone.View.extend({
  events: {
    "click #graphViewLink": "handleGraphLinkClick",
    "click #executionsViewLink": "handleExecutionLinkClick",
    "click #flowtriggersViewLink": "handleFlowTriggerLinkClick",
    "click #summaryViewLink": "handleSummaryLinkClick",
    "click #flowParamViewLink": "handleFlowParamLinkClick",
  },

  initialize: function (settings) {
    var selectedView = settings.selectedView;
    if (selectedView == "executions") {
      this.handleExecutionLinkClick();
    }
    else {
      this.handleGraphLinkClick();
    }
  },

  render: function () {
    console.log("render graph");
  },

  handleGraphLinkClick: function () {
    $("#executionsViewLink").removeClass("active");
    $("#graphViewLink").addClass("active");
    $("#flowtriggersViewLink").removeClass("active");
    $('#summaryViewLink').removeClass('active');
    $('#flowParamViewLink').removeClass('active');

    $("#graphView").show();
    $("#flowtriggerView").hide();
    $("#executionsView").hide();
    $('#summaryView').hide();
    $('#flowParamView').hide();
  },

  handleExecutionLinkClick: function () {
    $("#graphViewLink").removeClass("active");
    $("#executionsViewLink").addClass("active");
    $("#flowtriggersViewLink").removeClass("active");
    $('#summaryViewLink').removeClass('active');
    $('#flowParamViewLink').removeClass('active');

    $("#graphView").hide();
    $("#flowtriggerView").hide();
    $("#executionsView").show();
    $('#summaryView').hide();
    executionModel.trigger("change:view");
    $('#flowParamView').hide();
  },

  handleFlowTriggerLinkClick: function () {
    $("#graphViewLink").removeClass("active");
    $("#executionsViewLink").removeClass("active");
    $("#flowtriggersViewLink").addClass("active");
    $('#summaryViewLink').removeClass('active');
    $('#flowParamViewLink').removeClass('active');

    $("#graphView").hide();
    $("#flowtriggerView").show();
    $("#executionsView").hide();
    $('#summaryView').hide();
    flowTriggerModel.trigger("change:view");
    $('#flowParamView').hide();
  },

  handleSummaryLinkClick: function () {
    $('#graphViewLink').removeClass('active');
    $('#executionsViewLink').removeClass('active');
    $("#flowtriggersViewLink").removeClass("active");
    $('#summaryViewLink').addClass('active');
    $('#flowParamViewLink').removeClass('active');

    $('#graphView').hide();
    $("#flowtriggerView").hide();
    $('#executionsView').hide();
    $('#summaryView').show();
    $('#flowParamView').hide();
  },

  handleFlowParamLinkClick: function () {
    $('#graphViewLink').removeClass('active');
    $('#executionsViewLink').removeClass('active');
    $("#flowtriggersViewLink").removeClass("active");
    $('#summaryViewLink').removeClass('active');
    $('#flowParamViewLink').addClass('active');

    $('#graphView').hide();
    $("#flowtriggerView").hide();
    $('#executionsView').hide();
    $('#summaryView').hide();
    $('#flowParamView').show();
  },
});

var jobListView;
var svgGraphView;
var executionsView;
var flowParamView;

azkaban.ExecutionsView = Backbone.View.extend({
  events: {
    "click #pageSelection li": "handleChangePageSelection",
    "change #pageSizeSelect": "handleFlowPageSizeSelection",
    "click #pageNumJump": "handleFlowPageNumJump",
  },

  initialize: function (settings) {
    this.model.bind('change:view', this.handleChangeView, this);
    this.model.bind('render', this.render, this);
    var pageNum = $("#pageSizeSelect").val();
    this.model.set({ page: 1, pageSize: pageNum });
    this.model.bind('change:page', this.handlePageChange, this);
  },
  //组装数据表格
  render: function (evt) {
    console.log("render");
    // Render page selections
    var content = this.model.get("content");
    if (content == "flow") {
      var tbody = $("#execTableBody");
    }
    else {
      var tbody = $("#triggerTableBody");
    }
    tbody.empty();

    var executions = this.model.get("executions");
    for (var i = 0; i < executions.length; ++i) {
      var row = document.createElement("tr");
      //组装ID
      var tdId = document.createElement("td");
      var execA = document.createElement("a");
      if (content == "flow") {
        $(execA).attr("href", contextURL + "/executor?execid="
          + executions[i].execId);
        $(execA).text(executions[i].execId);
      }
      else {
        $(execA).attr("href", contextURL + "/executor?triggerinstanceid="
          + executions[i].instanceId);
        $(execA).text(executions[i].instanceId);
      }
      tdId.appendChild(execA);
      row.appendChild(tdId);
      //组装用户名
      var tdUser = document.createElement("td");
      $(tdUser).text(executions[i].submitUser);
      row.appendChild(tdUser);
      //组装开始时间
      var startTime = "-";
      if (executions[i].startTime != -1) {
        var startDateTime = new Date(executions[i].startTime);
        startTime = getDateFormat(startDateTime);
      }

      var tdStartTime = document.createElement("td");
      $(tdStartTime).text(startTime);
      row.appendChild(tdStartTime);
      //组装结束时间
      var endTime = "-";
      var lastTime = executions[i].endTime;
      if (executions[i].endTime != -1 && executions[i].endTime != 0) {
        var endDateTime = new Date(executions[i].endTime);
        endTime = getDateFormat(endDateTime);
      }
      else {
        lastTime = (new Date()).getTime();
      }

      var tdEndTime = document.createElement("td");
      $(tdEndTime).text(endTime);
      row.appendChild(tdEndTime);
      //组装跑批日期
      var tdRunDate = document.createElement("td");
      $(tdRunDate).text(executions[i].runDate);
      row.appendChild(tdRunDate);
      //组装执行时长
      var tdElapsed = document.createElement("td");
      $(tdElapsed).text(getDuration(executions[i].startTime, lastTime));
      row.appendChild(tdElapsed);
      //组装执行状态
      var tdStatus = document.createElement("td");
      var status = document.createElement("div");
      $(status).addClass("status");
      $(status).addClass(executions[i].status);
      if (content == "flow") {
        $(status).text(statusStringMap[executions[i].status]);
      }
      else {
        $(status).text(executions[i].status);
      }
      tdStatus.appendChild(status);
      row.appendChild(tdStatus);
      //操作行
      var tdAction = document.createElement("td");
      row.appendChild(tdAction);

      //备注
      var tdComment = document.createElement("td");
      $(tdComment).attr("style", "overflow: hidden;text-overflow: ellipsis;white-space: nowrap;max-width: 60px;");
      $(tdComment).attr("title", executions[i].comment);
      $(tdComment).text(executions[i].comment);
      row.appendChild(tdComment);

      tbody.append(row);
    }

    this.renderPagination(evt);
  },

  renderPagination: function (evt) {
    var total = this.model.get("total");
    total = total ? total : 1;
    var pageSize = this.model.get("pageSize");
    var numPages = Math.ceil(total / pageSize);

    this.model.set({ "numPages": numPages });
    var page = this.model.get("page");

    if (page > numPages) {
      page = numPages;
    }

    //Start it off
    $("#pageSelection .active").removeClass("active");

    // Disable if less than 5
    console.log("Num pages " + numPages)
    var i = 1;
    for (; i <= numPages && i <= 5; ++i) {
      $("#page" + i).removeClass("disabled");
    }
    for (; i <= 5; ++i) {
      $("#page" + i).addClass("disabled");
    }

    // Disable prev/next if necessary.
    if (page > 1) {
      var prevNum = parseInt(page) - parseInt(1);
      $("#previous").removeClass("disabled");
      $("#previous")[0].page = prevNum;
      $("#previous a").attr("href", "#page" + prevNum + "#pageSize" + pageSize);
    }
    else {
      $("#previous").addClass("disabled");
    }
    // 下一页按钮
    if (page < numPages) {
      var nextNum = parseInt(page) + parseInt(1);
      $("#next")[0].page = nextNum;
      $("#next").removeClass("disabled");
      $("#next a").attr("href", "#page" + nextNum + "#pageSize" + pageSize);
    }
    else {
      var nextNum = parseInt(page) + parseInt(1);
      $("#next").addClass("disabled");
    }

    // Selection is always in middle unless at barrier.
    var startPage = 0;
    var selectionPosition = 0;
    if (page < 3) {
      selectionPosition = page;
      startPage = 1;
    }
    else if (page == numPages && page != 3 && page != 4) {
      selectionPosition = 5;
      startPage = numPages - 4;
    }
    else if (page == numPages - 1 && page != 3) {
      selectionPosition = 4;
      startPage = numPages - 4;
    }
    else if (page == 4) {
      selectionPosition = 4;
      startPage = page - 3;
    }
    else if (page == 3) {
      selectionPosition = 3;
      startPage = page - 2;
    }
    else {
      selectionPosition = 3;
      startPage = page - 2;
    }

    $("#page" + selectionPosition).addClass("active");
    $("#page" + selectionPosition)[0].page = page;
    var selecta = $("#page" + selectionPosition + " a");
    selecta.text(page);
    selecta.attr("href", "#page" + page + "#pageSize" + pageSize);

    for (var j = 0; j < 5; ++j) {
      var realPage = startPage + j;
      var elementId = "#page" + (j + 1);
      if ($(elementId).hasClass("disabled")) {
        $(elementId)[0].page = realPage;
        var a = $(elementId + " a");
        a.text(realPage);
        a.attr("href", "javascript:void(0);");
      } else {
        $(elementId)[0].page = realPage;
        var a = $(elementId + " a");
        a.text(realPage);
        a.attr("href", "#page" + realPage + "#pageSize" + pageSize);
      }
    }
  },

  handleChangePageSelection: function (evt) {
    if ($(evt.currentTarget).hasClass("disabled")) {
      return;
    }
    var page = evt.currentTarget.page;
    this.model.set({ "page": page });
    var pageSize = $("#pageSizeSelect").val();
    this.model.set({ "pageSize": pageSize });
  },

  handleChangeView: function (evt) {
    if (this.init) {
      return;
    }
    console.log("init");
    this.handlePageChange(evt);
    this.init = true;
  },

  handlePageChange: function (evt) {
    var page = this.model.get("page") - 1;
    var pageSize = this.model.get("pageSize");
    var content = this.model.get("content");
    if (content == 'flow') {
      requestURL = contextURL + "/manager";
    }
    else {
      requestURL = contextURL + "/flowtriggerinstance";
    }

    var model = this.model;
    var requestData = {
      "project": projectName,
      "flow": flowId,
      "ajax": content == 'flow' ? "fetchFlowExecutions"
        : "fetchTriggerInstances",
      "start": page * pageSize,
      "length": pageSize,
      "page": page,
    };
    var successHandler = function (data) {
      model.set({
        "content": content,
        "executions": data.executions,
        "total": data.total
      });
      model.trigger("render");
    };
    $.get(requestURL, requestData, successHandler, "json");
  },

  handleFlowPageSizeSelection: function (evt) {
    var pageSize = evt.currentTarget.value;
    this.model.set({ "pageSize": pageSize });
    this.model.set({ "page": 1 });

    this.init = false;
    //historyModel.trigger("change:view");


    var search = window.location.search
    var arr = search.split("#");
    var pageURL = arr[0] + "#page1";
    var pageSizeURL = pageURL + "#pageSzie" + pageSize;

    var historyURL = contextURL + "/manager"

    var pageSizeFirestURL = historyURL + pageSizeURL;
    //防止XSS DOM攻击,类似:javascript:alert(1);//http://www.qq.com,所以对URL进行正则校验
    var reg = /^(http|ftp|https):\/\/[\w\-_]+(\.[\w\-_]+)+([\w\-\.,@?^=%&:/~\+#]*[\w\-\@?^=%&/~\+#])?/;
    if (reg.test(pageSizeFirestURL)) {
      window.location = pageSizeFirestURL;
    }
    executionModel.trigger("change:view");
  },

  handleFlowPageNumJump: function (evt) {

    var pageNum = $("#pageNumInput").val();
    if (pageNum <= 0) {
      //alert("页数必须大于1!!!");
      return;
    }

    if (pageNum > this.model.get("numPages")) {
      pageNum = this.model.get("numPages");
    }

    this.model.set({ "page": pageNum });
    this.init = false;
    executionModel.trigger("change:view");
  },


});

var summaryView;
azkaban.SummaryView = Backbone.View.extend({
  events: {
    'click #analyze-btn': 'fetchLastRun'
  },

  initialize: function (settings) {
    this.model.bind('change:view', this.handleChangeView, this);
    this.model.bind('render', this.render, this);

    this.fetchDetails();
    this.fetchSchedule();
    this.fetchFlowTrigger();
    this.model.trigger('render');
  },

  fetchDetails: function () {
    var requestURL = contextURL + "/manager";
    var requestData = {
      'ajax': 'fetchflowdetails',
      'project': projectName,
      'flow': flowId
    };

    var model = this.model;

    var successHandler = function (data) {
      console.log(data);
      model.set({
        'jobTypes': data.jobTypes,
        condition: data.condition
      });
      model.trigger('render');
    };
    $.get(requestURL, requestData, successHandler, 'json');
  },

  fetchSchedule: function () {
    var requestURL = contextURL + "/schedule"
    var requestData = {
      'ajax': 'fetchSchedule',
      'projectId': projectId,
      'flowId': flowId
    };
    var model = this.model;
    var view = this;
    var successHandler = function (data) {
      model.set({ 'schedule': data.schedule });
      model.trigger('render');
      view.fetchSla();
    };
    $.get(requestURL, requestData, successHandler, 'json');
  },

  fetchSla: function () {
    var schedule = this.model.get('schedule');
    if (schedule == null || schedule.scheduleId == null) {
      return;
    }

    var requestURL = contextURL + "/schedule"
    var requestData = {
      "scheduleId": schedule.scheduleId,
      "ajax": "slaInfo"
    };
    var model = this.model;
    var successHandler = function (data) {
      if (data == null || data.settings == null || data.settings.length == 0) {
        return;
      }
      schedule.slaOptions = true;
      model.set({ 'schedule': schedule });
      model.trigger('render');
    };
    $.get(requestURL, requestData, successHandler, 'json');
  },

  fetchLastRun: function () {
    var requestURL = contextURL + "/manager";
    var requestData = {
      'ajax': 'fetchLastSuccessfulFlowExecution',
      'project': projectName,
      'flow': flowId
    };
    var view = this;
    var successHandler = function (data) {
      if (data.success == "false" || data.execId == null) {
        dust.render("flowstats-no-data", data, function (err, out) {
          $('#flow-stats-container').html(out);
        });
        return;
      }
      flowStatsView.show(data.execId);
    };
    $.get(requestURL, requestData, successHandler, 'json');
  },

  fetchFlowTrigger: function () {
    var requestURL = contextURL + "/flowtrigger"
    var requestData = {
      'ajax': 'fetchTrigger',
      'projectId': projectId,
      'flowId': flowId
    };
    var model = this.model;
    var view = this;
    var successHandler = function (data) {
      model.set({ 'flowtrigger': data.flowTrigger });
      model.trigger('render');
    };
    $.get(requestURL, requestData, successHandler, 'json');
  },

  handleChangeView: function (evt) {
  },

  render: function (evt) {
    var data = {
      projectName: projectName,
      flowName: flowId,
      jobTypes: this.model.get('jobTypes'),
      condition: this.model.get('condition'),
      schedule: this.model.get('schedule'),
      flowtrigger: this.model.get('flowtrigger'),
    };
    dust.render("flowsummary", data, function (err, out) {
      $('#summary-view-content').html(out);
    });
  },
});

var graphModel;
var mainSvgGraphView;

var executionModel;
azkaban.ExecutionModel = Backbone.Model.extend({});

var flowTriggerModel;
azkaban.FlowTriggerModel = Backbone.Model.extend({});

var summaryModel;
azkaban.SummaryModel = Backbone.Model.extend({});

var flowStatsView;
var flowStatsModel;

var executionsTimeGraphView;
var slaView;

$(function () {
  var selected;
  // Execution model has to be created before the window switches the tabs.
  executionModel = new azkaban.ExecutionModel();
  executionModel.set("content", "flow");
  executionsView = new azkaban.ExecutionsView({
    el: $('#executionsView'),
    model: executionModel
  });

  flowTriggerModel = new azkaban.ExecutionModel();
  flowTriggerModel.set("content", "trigger");
  flowTriggerView = new azkaban.ExecutionsView({
    el: $('#flowtriggerView'),
    model: flowTriggerModel
  });

  summaryModel = new azkaban.SummaryModel();
  summaryView = new azkaban.SummaryView({
    el: $('#summaryView'),
    model: summaryModel
  });
  //Flow属性初始化视图
  flowParamModel = new azkaban.FlowParamModel();
  flowParamView = new azkaban.FlowParamView({
    el: $('#flowParamView'),
    model: flowParamModel
  });

  flowStatsModel = new azkaban.FlowStatsModel();
  flowStatsView = new azkaban.FlowStatsView({
    el: $('#flow-stats-container'),
    model: flowStatsModel
  });

  flowTabView = new azkaban.FlowTabView({
    el: $('#headertabs'),
    selectedView: selected
  });

  graphModel = new azkaban.GraphModel();
  mainSvgGraphView = new azkaban.SvgGraphView({
    el: $('#svgDiv'),
    model: graphModel,
    rightClick: {
      "node": nodeClickCallback,
      "edge": edgeClickCallback,
      "graph": graphClickCallback
    },
    dbClick: {
      "nodeDetail": nodeDBClickCallback,
    }
  });

  jobsListView = new azkaban.JobListView({
    el: $('#joblist-panel'),
    model: graphModel,
    contextMenuCallback: jobClickCallback
  });

  executionsTimeGraphView = new azkaban.TimeGraphView({
    el: $('#timeGraph'),
    model: executionModel,
    modelField: 'executions'
  });

  slaView = new azkaban.ChangeSlaView({ el: $('#sla-options') });

  var requestURL = contextURL + "/manager";
  // Set up the Flow options view. Create a new one every time :p

  //
  $('#executebtn').click(function () {

    // 点击flow, 进入页面, 页面右上角--执行工作流
    // 需要校验是否具有执行工作流权限 1:允许, 2:不允许
    var requestURL = contextURL + "/manager?ajax=checkUserExecuteFlowPermission&project=" + projectName;
    $.ajax({
      url: requestURL,
      type: "get",
      async: false,
      dataType: "json",
      success: function (data) {
        if (data["executeFlowFlag"] == 1) {
          console.log("have permission, click execute flow");
          var dataValue = graphModel.get("data");
          var nodes = dataValue.nodes;
          var executingData = {
            project: projectName,
            ajax: "executeFlow",
            flow: flowId,
            executeFlowTitle: data["executeFlowTitle"]
          };

          flowExecuteDialogView.show(executingData);
        } else if (data["executeFlowFlag"] == 2) {
          $('#user-operator-flow-permit-panel').modal();
          $('#title-user-operator-flow-permit').text(data["executePermission"]);
          $('#body-user-operator-flow-permit').html(data["noexecuteFlowPermission"]);
        }
      }
    });

  });

  var requestData = {
    "project": projectName,
    "ajax": "fetchflowgraph",
    "flow": flowId
  };
  var successHandler = function (data) {
    console.log("data fetched");
    graphModel.addFlow(data);
    graphModel.trigger("change:graph");

    // Handle the hash changes here so the graph finishes rendering first.
    if (window.location.hash) {
      var hash = window.location.hash;
      if (hash == "#executions") {
        flowTabView.handleExecutionLinkClick();
      }
      if (hash == "#flowparam") {
        flowTabView.handleFlowParamLinkClick();
      }
      if (hash == "#summary") {
        flowTabView.handleSummaryLinkClick();
      }
      if (hash == "#flowtriggers") {
        flowTabView.handleFlowTriggerLinkClick();
      }

      if (hash == "#graph") {
        // Redundant, but we may want to change the default.
        selected = "graph";
      }
      else {
        if ("#page" == hash.substring(0, "#page".length)) {
          var page = hash.substring("#page".length, hash.length);
          console.log("page " + page);
          flowTabView.handleExecutionLinkClick();
          executionModel.set({ "page": parseInt(page) });
        }
        else {
          selected = "graph";
        }
      }
    }
  };
  $.get(requestURL, requestData, successHandler, "json");

  // 定时调度按钮跟执行按钮分离
  $('#schedule-flow-btn').click(function () {

    // 发请求获取scheduleId
    var cronExpression = "";
    var scheduleId = "";
    var tempFlowId = mainSvgGraphView.model.get('data').flow;
    var requestURLForFetchScheduleId = contextURL + "/manager?ajax=fetchRunningScheduleId&project=" + projectName + "&flow=" + tempFlowId;
    $.ajax({
      url: requestURLForFetchScheduleId,
      type: "get",
      async: false,
      dataType: "json",
      success: function (data) {
        if (data.error) {
          console.log(data.error.message);
        } else {
          cronExpression = data.cronExpression;
          scheduleId = data.scheduleId;
        }
      }
    });

    // 点击flow, 进入页面, 页面右上角--执行调度
    // 需要校验是否具有执行调度权限 1:允许, 2:不允许
    var requestURL = contextURL + "/manager?ajax=checkUserScheduleFlowPermission&project=" + projectName;
    $.ajax({
      url: requestURL,
      type: "get",
      async: false,
      dataType: "json",
      success: function (data) {
        if (data["scheduleFlowFlag"] == 1) {
          console.log("have permission, click schedule flow");

          var flowId = mainSvgGraphView.model.get('data').flow;
          var executingData;
          if (scheduleId) {
            executingData = {
              project: projectName,
              flow: flowId,
              scheduleId: scheduleId,
              cronExpression: cronExpression,
              scheduleFlowTitle: data["scheduleFlowTitle"]
            };
          } else {
            executingData = {
              project: projectName,
              flow: flowId,
              scheduleFlowTitle: data["scheduleFlowTitle"]
            };
          }

          flowScheduleDialogView.show(executingData);
        } else if (data["scheduleFlowFlag"] == 2) {
          $('#user-operator-flow-permit-panel').modal();
          $('#title-user-operator-flow-permit').text(data["schFlowPermission"]);
          $('#body-user-operator-flow-permit').html(data["noSchPermissionsFlow"]);
        }
      }
    });

  });


  var hash = window.location.hash;
  if ("#page" == hash.substring(0, "#page".length)) {
    var arr = hash.split("#");
    var page = arr[1].substring("#page".length - 1, arr[1].length);
    var pageSize = arr[2].substring("#pageSize".length - 1, arr[2].length);

    $("#pageSizeSelect").val(pageSize);

    console.log("page " + page);
    executionModel.set({
      "page": parseInt(page),
      "pageSize": parseInt(pageSize),
    });
  } else {
    executionModel.set({ "page": 1 });
  }
  //切换流程图
  $("#switching-flow-btn").on('click', function () {
    var trimFlowName = !JSON.parse(sessionStorage.getItem('trimFlowName'));//标识是否剪切节点名称
    sessionStorage.setItem('trimFlowName', trimFlowName)
    var data = mainSvgGraphView.model.get('data') //获取流程图数据
    data.switchingFlow = true
    $(mainSvgGraphView.mainG).empty() //清空流程图
    mainSvgGraphView.renderGraph(data, mainSvgGraphView.mainG)
  })
});

var flowParamModel;
azkaban.FlowParamModel = Backbone.Model.extend({});
//Flow属性视图构建方法
azkaban.FlowParamView = Backbone.View.extend({
  events: {
  },

  initialize: function (settings) {
    this.model.bind('render', this.render, this);
    //this.handleFlowParam();
  },

  render: function (evt) {
    console.log("render");
  },

  handleFlowParam: function (flow) {
    this.projectName = projectName;
    this.flowName = flowId;
    this.jobName = jobName;

    var projectURL = this.projectURL

    $('#job-edit-pane').modal();

    var handleAddRow = this.handleAddRow;

    var fetchJobInfo = {
      "project": this.projectName,
      "ajax": "fetchJobInfo",
      "flowName": this.flowName,
      "jobName": this.jobName
    };
    var mythis = this;
    var fetchJobSuccessHandler = function (data) {
      if (data.error) {
        alert(data.error);
        return;
      }

      var tbody = $("#flow-param-tbody");

      for (var okey in overrideParams) {
        if (okey != 'type' && okey != 'dependencies') {
          var row = handleAddRow();
          var td = $(row).find('span');
          $(td[0]).text(okey);
          $(td[1]).text(overrideParams[okey]);
        }
      }
    };

    $.get(projectURL, fetchJobInfo, fetchJobSuccessHandler, "json");
  }
});
