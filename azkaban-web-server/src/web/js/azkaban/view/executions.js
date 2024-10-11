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
console.log('executions.js')
$.namespace('azkaban');

function killFlow(execId, flowName, projectName) {

  // 需要校验是否具有KILL工作流权限 1:允许, 2:不允许
  var requestURL = contextURL + "/manager?ajax=checkRunningPageKillFlowPermission&project=" + projectName;
  $.ajax({
      url: requestURL,
      type: "get",
      async: false,
      dataType: "json",
      success: function(data){
          if(data["runningPageKillFlowFlag"] == 1){
                var requestURL = document.location.href.replace("#currently-running", "");
                var requestData = {"execid": execId, "ajax": "cancelFlow"};
                var successHandler = function (data) {
                  console.log("cancel clicked");
                  if (data.error) {
                    showDialog(wtssI18n.view.error, data.error);
                  }
                  else {
                    showDialog(wtssI18n.view.cancel, wtssI18n.view.workflowCanceled);

                  }
                };
                ajaxCall(requestURL, requestData, successHandler);

          } else {
              $('#kill-current-running-flow-panel').modal();
              $('#title-kill-current-running-flow').text(wtssI18n.view.killExecutePermissions);
              $('#body-kill-current-running-flow').html(wtssI18n.view.killExecutePermissionsDesc);
          }
      }
  });
}

var showDialog = function (title, message) {
  $('#messageTitle').text(title);
  $('#messageBox').text(message);
  $('#messageDialog').modal();
}
var executionsTabView;
azkaban.ExecutionsTabView = Backbone.View.extend({
  events: {
    'click #currently-running-view-link': 'handleCurrentlyRunningViewLinkClick',
    'click #recently-finished-view-link': 'handleRecentlyFinishedViewLinkClick',
    'click #recover-history-view-link': 'handleRecoverHistoryViewLinkClick',
    'click #cycle-execution-view-link': 'handleCycleExecutionViewLinkClick'
  },

  initialize: function (settings) {
    var selectedView = settings.selectedView;
    if (selectedView == 'recently-finished') {
      this.handleRecentlyFinishedViewLinkClick();
    }
    else {
      this.handleCurrentlyRunningViewLinkClick();
    }
  },

  render: function () {
  },
  //当前运行页面
  handleCurrentlyRunningViewLinkClick: function () {
    $('#recently-finished-view-link').removeClass('active');
    $('#recently-finished-view').hide();
    $('#recover-history-view-link').removeClass('active');
    $('#recover-history-view').hide();
    $('#cycle-execution-view-link').removeClass('active');
    $('#cycle-execution-view').hide();
    $('#currently-running-view-link').addClass('active');
    $('#currently-running-view').show();
  },
  //最近完成页面
  handleRecentlyFinishedViewLinkClick: function () {
    $('#currently-running-view-link').removeClass('active');
    $('#currently-running-view').hide();
    $('#recover-history-view-link').removeClass('active');
    $('#recover-history-view').hide();
    $('#cycle-execution-view-link').removeClass('active');
    $('#cycle-execution-view').hide();
    $('#recently-finished-view-link').addClass('active');
    $('#recently-finished-view').show();
  },
  //历史重跑页面
  handleRecoverHistoryViewLinkClick: function () {
    $('#recently-finished-view-link').removeClass('active');
    $('#recently-finished-view').hide();
    $('#currently-running-view-link').removeClass('active');
    $('#currently-running-view').hide();
    $('#cycle-execution-view-link').removeClass('active');
    $('#cycle-execution-view').hide();
    $('#recover-history-view-link').addClass('active');
    $('#recover-history-view').show();
    executionModel.trigger("change:view");
  },
  //循环执行页面
  handleCycleExecutionViewLinkClick: function () {
    $('#recently-finished-view-link').removeClass('active');
    $('#recently-finished-view').hide();
    $('#currently-running-view-link').removeClass('active');
    $('#currently-running-view').hide();
    $('#recover-history-view-link').removeClass('active');
    $('#recover-history-view').hide();
    $('#cycle-execution-view-link').addClass('active');
    $('#cycle-execution-view').show();
    cycleExecutionModel.trigger("change:view");
  }
});

$(function() {
  // 在切换选项卡之前创建模型
  executionModel = new azkaban.ExecutionModel();
  recoverHistoryView = new azkaban.RecoverHistoryView({
    el: $('#recover-history-view'),
    model: executionModel
  });

  executingModel = new azkaban.ExecutingModel();

  executingView = new azkaban.ExecutingView({
    el: $('executing-flows-view'),
    model: executingModel
  });

  cycleExecutionModel = new azkaban.CycleExecutionModel();
  cycleExecutionView = new azkaban.CycleExecutionView({
    el: $('#cycle-execution-view'),
    model: cycleExecutionModel
  });
  executionsTabView = new azkaban.ExecutionsTabView({el: $('#header-tabs')});
  if (window.location.hash) {//浏览器输入对于的链接时跳转到对应的Tab页
    var hash = window.location.hash;
    if (hash == '#recently-finished') {
      executionsTabView.handleRecentlyFinishedViewLinkClick();
    } else if(hash == '#recover-history'){
      executionsTabView.handleRecoverHistoryViewLinkClick();
    } else if (hash == "#cycle-execution") {
      executionsTabView.handleCycleExecutionViewLinkClick();
    } else if ("#page" == hash.substring(0, "#page".length)) {
      var page = hash.substring("#page".length, hash.length);
      console.log("page " + page);
      executionsTabView.handleRecoverHistoryViewLinkClick();
      executionModel.set({"page": parseInt(page)});
    }else {
      executionsTabView.handleCurrentlyRunningViewLinkClick();
    }
  }

  $("#exec-page-refresh-btn").click(function(){
    window.location.reload();
  });
});

//用于保存浏览数据，切换页面也能返回之前的浏览进度。
var executionModel;
azkaban.ExecutionModel = Backbone.Model.extend({});

//历史重跑页面处理方法 组装表格和翻页处理
var recoverHistoryView;
azkaban.RecoverHistoryView = Backbone.View.extend({
  events: {
    "click #pageSelection li": "handleChangePageSelection",
    "change #pageSizeSelect": "handleRecoverPageSizeSelection",
    "click #pageNumJump": "handleRecoverPageNumJump",
  },

  initialize: function(settings) {
    this.model.bind('change:view', this.handleChangeView, this);
    this.model.bind('render', this.render, this);
    var pageNum = $("#pageSizeSelect").val();
    this.model.set({page: 1, pageSize: pageNum});
    this.model.bind('change:page', this.handlePageChange, this);
  },

  render: function(evt) {
    console.log("render");
    // Render page selections
    var tbody = $("#execTableBody");
    tbody.empty();

    var executions = this.model.get("recoverHistoryList");
    if(!executions){
      executions = [];
    }
    for (var i = 0; i < executions.length; i++) {
      var row = document.createElement("tr");

      //组装数字行
      var tdNum = document.createElement("td");
      $(tdNum).text(i + 1);
      $(tdNum).attr("class","tb-name");
      row.appendChild(tdNum);

      //组装Job行
      // var tdJob = document.createElement("td");
      // var jobIdA = document.createElement("a");
      // $(jobIdA).attr("href", contextURL + "/manager?project=" + projectName + "&flow=" + executions[i].flowId + "&job=" + executions[i].jobId);
      // $(jobIdA).text(executions[i].jobId);
      // tdJob.appendChild(jobIdA);
      // row.appendChild(tdJob);

      //组装Flow行
      var tdFlow = document.createElement("td");
      var flowA = document.createElement("a");
      $(flowA).attr("href", contextURL + "/manager?project=" + executions[i].projectName + "&flow=" + executions[i].flowId);
      $(flowA).text(executions[i].flowId);
      $(flowA).attr("style","word-break:break-all;");
      tdFlow.appendChild(flowA);
      row.appendChild(tdFlow);

      //组装Project行
      var tdProject = document.createElement("td");
      var projectA = document.createElement("a");
      $(projectA).attr("href", contextURL + "/manager?project=" + executions[i].projectName);
      $(projectA).text(executions[i].projectName);
      $(projectA).attr("style","word-break:break-all;");
      tdProject.appendChild(projectA);
      row.appendChild(tdProject);

      //组装用户行
      var tdUser = document.createElement("td");
      $(tdUser).text(executions[i].submitUser);
      row.appendChild(tdUser);

      //组装代理用户行
      // var tdProxyUser = document.createElement("td");
      // $(tdProxyUser).text(executions[i].proxyUsers);
      // row.appendChild(tdProxyUser);

      //组装开始时间行
      var startTime = "-";
      if (executions[i].recoverStartTime != -1) {
        var startDateTime = new Date(executions[i].recoverStartTime);
        startTime = getRecoverDateFormat(startDateTime);
      }

      var tdStartTime = document.createElement("td");
      $(tdStartTime).text(startTime);
      row.appendChild(tdStartTime);

      //组装结束时间行
      var endTime = "-";
      var lastTime = executions[i].recoverEndTime;
      if (executions[i].recoverEndTime != -1) {
        var endDateTime = new Date(executions[i].recoverEndTime);
        endTime = getRecoverDateFormat(endDateTime);
      }

      var tdEndTime = document.createElement("td");
      $(tdEndTime).text(endTime);
      row.appendChild(tdEndTime);


      //组装执行间隔行
      var tdExInt = document.createElement("td");
      $(tdExInt).text(executions[i].exInterval);
      row.appendChild(tdExInt);

      //组装正在执行的补采时间
      var nowExectionTime = "-";
      if (executions[i].nowExectionTime != -1) {
        var execTime = new Date(executions[i].nowExectionTime);
        nowExectionTime = getRecoverRunDateFormat(execTime);
      }

      var tdNowExectionTime = document.createElement("td");
      $(tdNowExectionTime).text(nowExectionTime);
      row.appendChild(tdNowExectionTime);

      //组装 正在执行的工作流 ID 数据行
      var tdExec = document.createElement("td");
      var nowExecA = document.createElement("a");
      if(executions[i].nowExectionId == "-1"){
        $(nowExecA).text(wtssI18n.view.waitingExecution);
      }else{
        $(nowExecA).attr("href", contextURL + "/executor?execid=" + executions[i].nowExectionId);
        $(nowExecA).text(executions[i].nowExectionId);
      }
      tdExec.appendChild(nowExecA);
      row.appendChild(tdExec);

      //组装执行状态行
      var tdStatus = document.createElement("td");
      var status = document.createElement("div");
      $(status).addClass("status");
      $(status).addClass(executions[i].recoverStatus);
      $(status).text(statusStringMap[executions[i].recoverStatus]);
      tdStatus.appendChild(status);
      row.appendChild(tdStatus);

      //组装操作行
      var tdAction = document.createElement("td");
      if(executions[i].recoverStatus == "RUNNING"){
        var actionButton = document.createElement("button");
        //$(actionButton).attr("href", contextURL + "/executor?execid=" + executions[i].execId + "&job=" + executions[i].jobId + "&attempt=0");
        $(actionButton).text("Kill");
        $(actionButton).attr("type","button");
        $(actionButton).attr("id","cancelbtn");
        $(actionButton).attr("class","btn btn-danger btn-sm");
        $(actionButton).attr("onclick","killRepeat(" + executions[i].recoverId + ")");
        tdAction.appendChild(actionButton);
      }
      row.appendChild(tdAction);

      tbody.append(row);
    }

    this.renderPagination(evt);

  },

  renderPagination: function(evt) {
    var total = this.model.get("total");
    total = total? total : 1;
    var pageSize = this.model.get("pageSize");
    var numPages = Math.ceil(total / pageSize);

    this.model.set({"numPages": numPages});
    var page = this.model.get("page");

    if(page > numPages){
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

    $("#page"+selectionPosition).addClass("active");
    $("#page"+selectionPosition)[0].page = page;
    var selecta = $("#page" + selectionPosition + " a");
    selecta.text(page);
    selecta.attr("href", "#page" + page + "#pageSize" + pageSize);

    for (var j = 0; j < 5; ++j) {
      var realPage = startPage + j;
      var elementId = "#page" + (j+1);
      if($(elementId).hasClass("disabled")){
        $(elementId)[0].page = realPage;
        var a = $(elementId + " a");
        a.text(realPage);
        a.attr("href", "javascript:void(0);");
      }else{
        $(elementId)[0].page = realPage;
        var a = $(elementId + " a");
        a.text(realPage);
        a.attr("href", "#page" + realPage + "#pageSize" + pageSize);
      }
    }
  },

  handleChangePageSelection: function(evt) {
    if ($(evt.currentTarget).hasClass("disabled")) {
      return;
    }
    var page = evt.currentTarget.page;
    this.model.set({"page": page});
  },

  handleChangeView: function(evt) {
    if (this.init) {
      return;
    }
    console.log("init");
    this.handlePageChange(evt);
    this.init = true;
  },

  handlePageChange: function(evt) {
    var start = this.model.get("page") - 1;
    var pageSize = this.model.get("pageSize");
    var requestURL = contextURL + "/recover";

    var model = this.model;
    var requestData = {
      "ajax": "fetchRecoverHistory",
      "start": start,
      "length": pageSize
    };
    var successHandler = function(data) {
      if(data.total > 0){
        $("#pageTable").show();
        model.set({
          "recoverHistoryList": data.recoverHistoryList,
          "total": data.total
        });
        model.trigger("render");
      }else{
        $("#pageTable").hide();
      }
      model.trigger("render");
    };
    $.get(requestURL, requestData, successHandler, "json");
  },

  handleRecoverPageSizeSelection: function(evt) {
    var pageSize = evt.currentTarget.value;
    this.model.set({"pageSize": pageSize});
    this.model.set({"page": 1});

    this.init = false;
    //historyModel.trigger("change:view");


    var search = window.location.search
    var arr = search.split("#");
    var pageURL = arr[0] + "#page1";
    var pageSizeURL = pageURL + "#pageSzie" + pageSize;

    var historyURL = contextURL + "/executor"

    var pageSizeFirestURL = historyURL + pageSizeURL;

    //防止XSS DOM攻击,类似:javascript:alert(1);//http://www.qq.com,所以对URL进行正则校验
    var reg = /^(http|ftp|https):\/\/[\w\-_]+(\.[\w\-_]+)+([\w\-\.,@?^=%&:/~\+#]*[\w\-\@?^=%&/~\+#])?/;
    if (reg.test(pageSizeFirestURL)) {
        window.location = pageSizeFirestURL;
    }
    executionModel.trigger("change:view");
  },

  handleRecoverPageNumJump: function (evt) {

    var pageNum = $("#pageNumInput").val();
    if(pageNum <= 0){
        //alert("页数必须大于1!!!");
        return;
    }
    if(pageNum > this.model.get("numPages")){
      pageNum = this.model.get("numPages");
    }

    this.model.set({"page": pageNum});
    this.init = false;
    executionModel.trigger("change:view");
  },

});

var tableSorterView;

//用于保存浏览数据，切换页面也能返回之前的浏览进度。
var executingModel;
azkaban.ExecutingModel = Backbone.Model.extend({});

//当前运行页面处理方法 组装表格和翻页处理
var executingView;
azkaban.ExecutingView = Backbone.View.extend({
  events: {
    // "click #pageSelection li": "handleChangePageSelection",
    // "change #pageSizeSelect": "handleRecoverPageSizeSelection",
    // "click #pageNumJump": "handleRecoverPageNumJump",
  },

  initialize: function(settings) {
    // this.model.bind('change:view', this.handleChangeView, this);
    this.model.bind('render', this.render, this);
    // var pageNum = $("#pageSizeSelect").val();
    // this.model.set({page: 1, pageSize: pageNum});
    // this.model.bind('change:page', this.handlePageChange, this);

    this.handlePageChange();
  },

  render: function() {
    console.log("render");
    // Render page selections
    var tbody = $("#executing-tbody");
    tbody.empty();

    var executions = this.model.get("executingFlowData");
    for (var i = 0; i < executions.length; ++i) {
      var row = document.createElement("tr");

      //组装数字行
      var tdNum = document.createElement("td");
      $(tdNum).text(i + 1);
      $(tdNum).attr("class","tb-name");
      row.appendChild(tdNum);

      //组装执行Id行
      var tdExecId = document.createElement("td");
      var execIdA = document.createElement("a");
      $(execIdA).attr("href", contextURL + "/executor?execid="+ executions[i].execId);
      $(execIdA).text(executions[i].execId);
      tdExecId.appendChild(execIdA);
      row.appendChild(tdExecId);

      //组装执行节点Id行
      var tdExectorId = document.createElement("td");
      $(tdExectorId).text(executions[i].exectorId);
      row.appendChild(tdExectorId);

      //组装Flow行
      var tdFlow = document.createElement("td");
      var flowA = document.createElement("a");
      $(flowA).attr("href", contextURL + "/manager?project=" + executions[i].projectName + "&flow=" + executions[i].flowName);
      $(flowA).text(executions[i].flowName);
      $(flowA).attr("style","word-break:break-all;");
      tdFlow.appendChild(flowA);
      row.appendChild(tdFlow);

      //组装Project行
      var tdProject = document.createElement("td");
      var projectA = document.createElement("a");
      $(projectA).attr("href", contextURL + "/manager?project=" + executions[i].projectName);
      $(projectA).text(executions[i].projectName);
      $(projectA).attr("style","word-break:break-all;");
      tdProject.appendChild(projectA);
      row.appendChild(tdProject);

      //组装用户行
      var tdUser = document.createElement("td");
      $(tdUser).text(executions[i].submitUser);
      row.appendChild(tdUser);

      //组装代理用户行
      // var tdProxyUser = document.createElement("td");
      // $(tdProxyUser).text(executions[i].proxyUsers);
      // row.appendChild(tdProxyUser);

      //组装开始时间行
      var tdStartTime = document.createElement("td");
      $(tdStartTime).text(executions[i].startTime);
      row.appendChild(tdStartTime);

      //组装结束时间行
      // var tdEndTime = document.createElement("td");
      // $(tdEndTime).text(executions[i].startTime);
      // row.appendChild(tdEndTime);


      //组装跑批时间行
      var tdRunDate = document.createElement("td");
      $(tdRunDate).text(executions[i].runDate);
      row.appendChild(tdRunDate);

      //组装执行时长行
      var tdDifftime = document.createElement("td");
      $(tdDifftime).text(executions[i].duration);
      row.appendChild(tdDifftime);

      //组装执行状态行
      var tdStatus = document.createElement("td");
      var status = document.createElement("div");
      $(status).addClass("status");
      // if(parseInt(executions[i].execTime) > parseInt(executions[i].moyenne)){
      //   $(status).addClass("TIMEOUT");
      // }else{
        $(status).addClass(executions[i].status);
      // }
      $(status).text(statusStringMap[executions[i].status]);
      tdStatus.appendChild(status);
      row.appendChild(tdStatus);

      //组装操作行
      var tdAction = document.createElement("td");
      var actionButton = document.createElement("button");
      $(actionButton).text(wtssI18n.view.end);
      $(actionButton).attr("type","button");
      $(actionButton).attr("id","cancelbtn");
      $(actionButton).attr("class","btn btn-danger btn-sm");
      $(actionButton).attr("onclick","killFlow(" + executions[i].execId + ",'"+ executions[i].flowName + "','" + executions[i].projectName + "')");
      tdAction.appendChild(actionButton);
      row.appendChild(tdAction);

      tbody.append(row);

    }
    // 页面错误
    // tableSorterView = new azkaban.TableSorter({el: $('#executingJobs')});

    $(document).ready(function () {
      var jobTable = $("#executingJobs");
      jobTable.tablesorter();
    });



  },

  handleChangeView: function(evt) {
    if (this.init) {
      return;
    }
    console.log("init");
    this.handlePageChange(evt);
    this.init = true;
  },

  handlePageChange: function() {
    var requestURL = contextURL + "/executor";

    var model = this.model;
    var requestData = {
      "ajax": "getExecutingFlowData",
    };
    var successHandler = function(data) {
      model.set({
        "executingFlowData": data.executingFlowData,
      });
      model.trigger("render");
    };
    $.get(requestURL, requestData, successHandler, "json");
  },

});

//用于保存浏览数据，切换页面也能返回之前的浏览进度。
var executionModel;
azkaban.ExecutionModel = Backbone.Model.extend({});

//用于保存浏览数据，切换页面也能返回之前的浏览进度。
var cycleExecutionModel;
azkaban.CycleExecutionModel = Backbone.Model.extend({});

//循环执行页面处理方法 组装表格和翻页处理
var cycleExecutionView;
azkaban.CycleExecutionView = Backbone.View.extend({
  events: {
    "click #cycleExecutionPageSelection li": "handleChangePageSelection",
    "change #cycleExecutionPageSizeSelect": "handleCyclePageSizeSelection",
    "click #cycleExecutionPageNumJump": "handleCyclePageNumJump"
  },
  initialize: function() {
    this.model.bind('change:view', this.handleChangeView, this);
    this.model.bind('render', this.render, this);
    var pageNum = $("#pageSizeSelect").val();
    this.model.set({page: 1, pageSize: pageNum});
    this.model.bind('change:page', this.handlePageChange, this);
  },
  render: function(evt) {
    console.log("render");
    var tbody = $("#cycleExecutionTableBody");
    tbody.empty();
    var executions = this.model.get("executionCycleList");
    if(!executions){
      executions = [];
    }
    for (var i = 0; i < executions.length; i++) {
      var row = document.createElement("tr");

      //组装数字行
      var tdNum = document.createElement("td");
      $(tdNum).text(i + 1);
      $(tdNum).attr("class","tb-name");
      row.appendChild(tdNum);

      //组装Flow行
      var tdFlow = document.createElement("td");
      var flowA = document.createElement("a");
      $(flowA).attr("href", contextURL + "/manager?project=" + executions[i].projectName + "&flow=" + executions[i].flowId);
      $(flowA).text(executions[i].flowId);
      $(flowA).attr("style","word-break:break-all;");
      tdFlow.appendChild(flowA);
      row.appendChild(tdFlow);

      //组装Project行
      var tdProject = document.createElement("td");
      var projectA = document.createElement("a");
      $(projectA).attr("href", contextURL + "/manager?project=" + executions[i].projectName);
      $(projectA).text(executions[i].projectName);
      $(projectA).attr("style","word-break:break-all;");
      tdProject.appendChild(projectA);
      row.appendChild(tdProject);

      //组装用户行
      var tdUser = document.createElement("td");
      $(tdUser).text(executions[i].submitUser);
      row.appendChild(tdUser);

      //组装代理用户行
      // var tdProxyUser = document.createElement("td");
      // $(tdProxyUser).text(executions[i].proxyUsers);
      // row.appendChild(tdProxyUser);

      //组装 正在执行的工作流 ID 数据行
      var tdExec = document.createElement("td");
      var nowExecA = document.createElement("a");
      if(executions[i].currentExecId === -1){
        $(nowExecA).text(wtssI18n.view.waitingExecution);
      }else{
        $(nowExecA).attr("href", contextURL + "/executor?execid=" + executions[i].currentExecId);
        $(nowExecA).text(executions[i].currentExecId);
      }
      tdExec.appendChild(nowExecA);
      row.appendChild(tdExec);

      //组装执行状态行
      var tdStatus = document.createElement("td");
      var status = document.createElement("div");
      $(status).addClass("status");
      $(status).addClass(executions[i].status);
      $(status).text(statusStringMap[executions[i].status]);
      tdStatus.appendChild(status);
      row.appendChild(tdStatus);

      //组装操作行
      var tdAction = document.createElement("td");
      if(executions[i].status === "RUNNING"){
        var actionButton = document.createElement("button");
        $(actionButton).text(wtssI18n.view.killCycleExecution);
        $(actionButton).attr("type","button");
        $(actionButton).attr("id","cancelbtn");
        $(actionButton).attr("class","btn btn-danger btn-sm");
        $(actionButton).attr("onclick","killCycle(" + executions[i].id + ")");
        tdAction.appendChild(actionButton);
      }
      row.appendChild(tdAction);
      tbody.append(row);
    }
    this.renderPagination(evt);
  },
  renderPagination: function() {
    var total = this.model.get("total");
    total = total? total : 1;
    var pageSize = this.model.get("pageSize");
    var numPages = Math.ceil(total / pageSize);

    this.model.set({"numPages": numPages});
    var page = this.model.get("page");

    if(page > numPages){
      page = numPages;
    }

    //Start it off
    $("#cycleExecutionPageSelection .active").removeClass("active");

    // Disable if less than 5
    console.log("Num pages " + numPages)
    var i = 1;
    for (; i <= numPages && i <= 5; ++i) {
      $("#cycleExecutionPageSelection #page" + i).removeClass("disabled");
    }
    for (; i <= 5; ++i) {
      $("#cycleExecutionPageSelection #page" + i).addClass("disabled");
    }

    // Disable prev/next if necessary.
    if (page > 1) {
      var prevNum = parseInt(page) - parseInt(1);
      $("#cycleExecutionPageSelection #previous").removeClass("disabled");
      $("#cycleExecutionPageSelection #previous")[0].page = prevNum;
      $("#cycleExecutionPageSelection #previous a").attr("href", "#page" + prevNum + "#pageSize" + pageSize);
    }
    else {
      $("#cycleExecutionPageSelection #previous").addClass("disabled");
    }
    // 下一页按钮
    if (page < numPages) {
      var nextNum = parseInt(page) + parseInt(1);
      $("#cycleExecutionPageSelection #next")[0].page = nextNum;
      $("#cycleExecutionPageSelection #next").removeClass("disabled");
      $("#cycleExecutionPageSelection #next a").attr("href", "#page" + nextNum + "#pageSize" + pageSize);
    }
    else {
      var nextNum = parseInt(page) + parseInt(1);
      $("#cycleExecutionPageSelection #next").addClass("disabled");
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

    $("#cycleExecutionPageSelection #page"+selectionPosition).addClass("active");
    $("#cycleExecutionPageSelection #page"+selectionPosition)[0].page = page;
    var selecta = $("#cycleExecutionPageSelection #page" + selectionPosition + " a");
    selecta.text(page);
    selecta.attr("href", "#page" + page + "#pageSize" + pageSize);

    for (var j = 0; j < 5; ++j) {
      var realPage = startPage + j;
      var elementId = "#cycleExecutionPageSelection #page" + (j+1);
      if($(elementId).hasClass("disabled")){
        $(elementId)[0].page = realPage;
        var a = $(elementId + " a");
        a.text(realPage);
        a.attr("href", "javascript:void(0);");
      }else{
        $(elementId)[0].page = realPage;
        var a = $(elementId + " a");
        a.text(realPage);
        a.attr("href", "#page" + realPage + "#pageSize" + pageSize);
      }
    }
  },
  handleChangePageSelection: function(evt) {
    if ($(evt.currentTarget).hasClass("disabled")) {
      return;
    }
    var page = evt.currentTarget.page;
    this.model.set({"page": page});
  },
  handleChangeView: function(evt) {
    if (this.init) {
      return;
    }
    console.log("init");
    this.handlePageChange(evt);
    this.init = true;
  },
  handlePageChange: function() {
    var page = this.model.get("page");
    var pageSize = this.model.get("pageSize");
    var requestURL = contextURL + "/cycle";
    var model = this.model;
    var requestData = {
      "ajax": "fetchCycleFlows",
      "page": page,
      "pageSize": pageSize
    };
    var successHandler = function(data) {
      if(data.total > 0){
        $("#cycleExecutionPageTable").show();
        model.set({
          "executionCycleList": data.executionCycleList,
          "total": data.total
        });
        model.trigger("render");
      }else{
        $("#cycleExecutionPageTable").hide();
      }
      model.trigger("render");
    };
    $.get(requestURL, requestData, successHandler, "json");
  },
  handleCyclePageSizeSelection: function(evt) {
    var pageSize = evt.currentTarget.value;
    this.model.set({"pageSize": pageSize});
    this.model.set({"page": 1});
    this.init = false;
    var search = window.location.search
    var arr = search.split("#");
    var pageURL = arr[0] + "#page1";
    var pageSizeURL = pageURL + "#pageSzie" + pageSize;
    var cycleURL = contextURL + "/executor"
    var pageSizeFirestURL = cycleURL + pageSizeURL;
    //防止XSS DOM攻击,类似:javascript:alert(1);//http://www.qq.com,所以对URL进行正则校验
    var reg = /^(http|ftp|https):\/\/[\w\-_]+(\.[\w\-_]+)+([\w\-\.,@?^=%&:/~\+#]*[\w\-\@?^=%&/~\+#])?/;
    if (reg.test(pageSizeFirestURL)) {
      window.location = pageSizeFirestURL;
    }
    cycleExecutionModel.trigger("change:view");
  },
  handleCyclePageNumJump: function () {
    var pageNum = $("#cycleExecutionPageNumInput").val();
    if(pageNum <= 0){
      return;
    }
    if(pageNum > this.model.get("numPages")){
      pageNum = this.model.get("numPages");
    }
    this.model.set({"page": pageNum});
    this.init = false;
    cycleExecutionModel.trigger("change:view");
  }
});

function killCycle(id) {
  var requestURL = document.location.href.replace("recover","executor");
  var requestData = {"id": id, "ajax": "stopCycleFlow"};
  var successHandler = function(data) {
    if (data.error) {
      showDialog("Error", data.error);
    }
    else {
      showDialog("Cancelled", "Cycle Flow has been cancelled.");
    }
  };
  ajaxCall(requestURL, requestData, successHandler);
}

