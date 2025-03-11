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

function killFlow (execId, flowName, projectName) {
    if (!execId) {
        var rowData = executingModel.get("currentRowData");
        execId = rowData.execId;
        projectName = rowData.projectName;
    }
  // 需要校验是否具有KILL工作流权限 1:允许, 2:不允许
  var requestURL = "/manager?ajax=checkRunningPageKillFlowPermission&project=" + projectName;
  $.ajax({
    url: requestURL,
    type: "get",
    async: false,
    dataType: "json",
    success: function (data) {
      if (data["runningPageKillFlowFlag"] == 1) {
        var requestURL = document.location.href.replace("#currently-running", "");
        var requestData = { "execid": execId, "ajax": "cancelFlow" };
        var successHandler = function (cancelFlowData) {
          console.log("cancel clicked:", JSON.stringify(cancelFlowData));
          if (cancelFlowData.error) {
            executionShowDialog(wtssI18n.view.error, cancelFlowData.error, cancelFlowData.supportForceCancel, execId);
          } else {
            executionShowDialog(wtssI18n.view.cancel, wtssI18n.view.workflowCanceled);
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

var executionShowDialog = function (title, message, canForceKill = false, execId) {
  console.log('canForceKill', canForceKill)
  $('#executionspage-message-dialog-superkillbtn').hide()
  $('#messageTitle').text(title);
  $('#messageBox').text(message);
  if(canForceKill) {
    $('#executionspage-message-dialog-superkillbtn').show()
    $('#executionspage-message-dialog-superkillbtn').click(function() {
      var requestURL = document.location.href.replace("#currently-running", "");
      var requestData = { "execid": execId, "ajax": "cancelFlow", forceCancel: true  };
      var successHandler = function (data) {
        console.log("cancel clicked");
        if (data.error) {
          $('#messageBox').text('强制停止失败：\n'+data.error);
        }
        else {
          executionShowDialog(wtssI18n.view.cancel, wtssI18n.view.workflowCanceled);
        }
      };
      ajaxCall(requestURL, requestData, successHandler);
    })
  }
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
    executingView.model.set({
        pageSize: 100,
        searchterm: '',
        preciseSearch: true,
        search: false,
        advfilter: false,
        searchTerm: '',
    });
    executingView.model.trigger("change:page");
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
    recoverHistoryView.model.set({
        pageSize: 20,
    });
    recoverHistoryView.model.trigger("change:page");
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
    cycleExecutionView.model.set({
        pageSize: 20,
    });
    cycleExecutionView.model.trigger("change:page");
  }
});

//用于保存浏览数据，切换页面也能返回之前的浏览进度。
var recoverHistoryModel;
azkaban.RecoverHistoryModel = Backbone.Model.extend({});
var recoverHistoryView;
//历史重跑页面处理方法 组装表格和翻页处理
azkaban.RecoverHistoryView = Backbone.View.extend({
    events: {
        "click #recoverHistoryPageTable .projectPageSelection  li": "handleChangePageSelection",
        "change #recoverHistoryPageTable .pageSizeSelect": "handlePageSizeSelection",
        "click #recoverHistoryPageTable .pageNumJump": "handlePageNumJump",
    },
    initialize: function (settings) {
      this.model.bind('render', this.render, this);

      this.model.set('elDomId','recoverHistoryPageTable');
      this.model.bind('change:view', this.handleChangeView, this);
      this.model.bind('change:page', this.handlePageChange, this);
      this.model.set({ page: 1, pageSize: 20 });
      this.createResize();
    },

    render: function (evt) {
      console.log("render");
      // Render page selections
    //   var tbody = $("#execTableBody");
    //   tbody.empty();
      var oldHistoryTableBody = document.getElementById('execTableBody');
      var childrenNum = oldHistoryTableBody.children.length;
      var newHistoryTableBody = document.createElement('tbody');
      newHistoryTableBody.setAttribute('id', 'execTableBody');
      var executions = this.model.get("recoverHistoryList");
      if (!executions) {
        executions = [];
      }
      for (var i = 0; i < executions.length; i++) {
        var row = document.createElement("tr");

        //组装数字行
        var tdNum = document.createElement("td");
        $(tdNum).text(i + 1);
        $(tdNum).attr("class", "tb-name");
        row.appendChild(tdNum);

        //组装Job行
        // var tdJob = document.createElement("td");
        // var jobIdA = document.createElement("a");
        // $(jobIdA).attr("href",  "/manager?project=" + projectName + "&flow=" + executions[i].flowId + "&job=" + executions[i].jobId);
        // $(jobIdA).text(executions[i].jobId);
        // tdJob.appendChild(jobIdA);
        // row.appendChild(tdJob);

        //组装Flow行
        var tdFlow = document.createElement("td");
        var flowA = document.createElement("a");
        $(flowA).attr("href", filterXSS("/manager?project=" + executions[i].projectName + "&flow=" + executions[i].flowId));
        $(flowA).text(executions[i].flowId);
        $(flowA).attr("style", "word-break:break-all;");
        tdFlow.appendChild(flowA);
        row.appendChild(tdFlow);

        //组装Project行
        var tdProject = document.createElement("td");
        var projectA = document.createElement("a");
        $(projectA).attr("href", filterXSS("/manager?project=" + executions[i].projectName));
        $(projectA).text(executions[i].projectName);
        $(projectA).attr("style", "word-break:break-all;");
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
        if (executions[i].nowExectionId == "-1") {
          $(nowExecA).text(wtssI18n.view.waitingExecution);
        } else {
          $(nowExecA).attr("href", filterXSS("/executor?execid=" + executions[i].nowExectionId));
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
        var actionButton = document.createElement("button");
        //$(actionButton).attr("href",  "/executor?execid=" + executions[i].execId + "&job=" + executions[i].jobId + "&attempt=0");
        $(actionButton).text("Kill").attr("rowIndex", i).attr("class", "btn btn-danger btn-sm").click(this.killRepeatConfirm);
        tdAction.appendChild(actionButton);

        row.appendChild(tdAction);

        newHistoryTableBody.appendChild(row);
      }
      oldHistoryTableBody.parentNode.replaceChild(newHistoryTableBody, oldHistoryTableBody);
      this.renderPagination(evt);
    },
    ...commonPaginationFun(),
    killRepeatConfirm: function(e) {
        var rowIndex = e.target.getAttribute("rowIndex");
        var rowData = recoverHistoryModel.get("recoverHistoryList")[rowIndex] || {};
        recoverHistoryModel.set("currentRowData", rowData);
        var prompt = window.langType === 'zh_CN' ? '是否Kill' + rowData.flowId + '工作流历史重跑' : 'Whether to kill '+rowData. flowId +' workflow history rerun';
        deleteDialogView.show(wtssI18n.deletePro.killHitoryReRun, prompt, wtssI18n.common.cancel, 'Kill', '', killRepeat);
    },
    handlePageChange: function (evt) {
      var start = this.model.get("page") - 1;
      var pageSize = this.model.get("pageSize");
      var requestURL = "/recover";

      var model = this.model;
      var requestData = {
        "ajax": "fetchRecoverHistory",
        "start": start,
        "length": pageSize
      };
      var successHandler = function (data) {
        if (data.total > 0) {
          $("#recoverHistoryPageTable").show();

        } else {
          $("#executingPagerecoverHistoryPageTableTable").hide();
        }
        model.set({
            "recoverHistoryList": data.recoverHistoryList,
            "total": data.total
          });
        model.trigger("render");
      };
      $.get(requestURL, requestData, successHandler, "json");
    },
})

var tableSorterView;

//用于保存浏览数据，切换页面也能返回之前的浏览进度。
var executingModel;
azkaban.ExecutingModel = Backbone.Model.extend({});

//当前运行页面处理方法 组装表格和翻页处理
var executingView;
azkaban.ExecutingView = Backbone.View.extend({
    events: {
        "click #executingPageTable .projectPageSelection  li": "handleChangePageSelection",
        "change #executingPageTable .pageSizeSelect": "handlePageSizeSelection",
        "click #executingPageTable .pageNumJump": "handlePageNumJump",
    },
    initialize: function (settings) {

        this.model.bind('render', this.render, this);
        changePageSizeSelectValue("executingPageTable", [100, 500, 1000]);
        this.model.set('elDomId','executingPageTable');
        this.model.bind('change:view', this.handleChangeView, this);
        this.model.bind('change:page', this.handlePageChange, this);
        this.model.set({
            page: 1,
            pageSize: 100,
            searchterm: '',
            preciseSearch: true,
            search: false,
            advfilter: false,
            searchTerm: '',
        });
        this.createResize();
        $("#executingJobs").tablesorter();
    },

    render: function () {
      console.log("render");
      // Render page selections
    //   var tbody = $("#executing-tbody");
    //   tbody.empty();
      var oldExecutingTbody = document.getElementById('executing-tbody');
      var childrenNum = oldExecutingTbody.children.length;
      var newExecutingTbody = document.createElement('tbody');
      newExecutingTbody.setAttribute('id', 'executing-tbody');
      var executions = this.model.get("executingFlowData");
      for (var i = 0; i < executions.length; ++i) {
        var row = document.createElement("tr");

        //组装数字行
        var tdNum = document.createElement("td");
        $(tdNum).text(i + 1);
        $(tdNum).attr("class", "tb-name");
        row.appendChild(tdNum);

        //组装执行Id行
        var tdExecId = document.createElement("td");
        var execIdA = document.createElement("a");
        $(execIdA).attr("href", filterXSS("/executor?execid=" + executions[i].execId));
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
        $(flowA).attr("href", filterXSS("/manager?project=" + executions[i].projectName + "&flow=" + executions[i].flowName));
        $(flowA).text(executions[i].flowName);
        $(flowA).attr("style", "word-break:break-all;");
        tdFlow.appendChild(flowA);
        row.appendChild(tdFlow);

        //组装Project行
        var tdProject = document.createElement("td");
        var projectA = document.createElement("a");
        $(projectA).attr("href", filterXSS("/manager?project=" + executions[i].projectName));
        $(projectA).text(executions[i].projectName);
        $(projectA).attr("style", "word-break:break-all;");
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

        //组装 工作流执行类型
        var tdFlowType = document.createElement("td");
        if (executions[i].flowType == "0") {
          $(tdFlowType).text(wtssI18n.view.singleExecution);
        } else if (executions[i].flowType == "2") {
          $(tdFlowType).text(wtssI18n.view.historicalRerun);
        } else if (executions[i].flowType == "3") {
          $(tdFlowType).text(wtssI18n.view.timedScheduling);
        } else if (executions[i].flowType == "4") {
          $(tdFlowType).text(wtssI18n.view.cycleExecution);
        } else if (executions[i].flowType == "6") {
          $(tdFlowType).text(wtssI18n.view.eventSchedule);
        }
        row.appendChild(tdFlowType);

        //组装操作行
        var tdAction = document.createElement("td");
        var actionButton = document.createElement("button");
        $(actionButton).text(wtssI18n.view.end).attr("rowIndex",i).attr("class", "btn btn-danger btn-sm").click(this.killFlowConfirm);
        tdAction.appendChild(actionButton);
        row.appendChild(tdAction);

        newExecutingTbody.appendChild(row);

      }
      oldExecutingTbody.parentNode.replaceChild(newExecutingTbody, oldExecutingTbody);
      // 页面错误
      // tableSorterView = new azkaban.TableSorter({el: $('#executingJobs')});

      if (executions && executions.length) {
        $("#executingJobs").trigger("update");
        $("#executingJobs").trigger("sorton", "");
      }

      this.renderPagination();
    },
    killFlowConfirm: function(e){
        var rowIndex = e.target.getAttribute("rowIndex");
        var rowData = executingModel.get("executingFlowData")[rowIndex] || {};
        executingModel.set("currentRowData", rowData);
        // rowData.execId, rowData.flowName, rowData.rowData\
        var prompt = window.langType === 'zh_CN' ? '是否结束运行' + rowData.flowName + '工作流' : 'Whether to finish running ' + rowData.flowName + ' workflow';
        deleteDialogView.show(wtssI18n.deletePro.endRun, prompt, wtssI18n.common.cancel, wtssI18n.view.end, '', killFlow);
    },
    ...commonPaginationFun(),
    handlePageChange: function () {
      var requestURL = "/executor";

      var model = this.model;
      var requestData = {
        "ajax": "getExecutingFlowData",
        page: model.get('page'),
        size: model.get('pageSize'),
      };
      var search =  model.get('search') || true;
      requestData.fuzzySearch =  model.get('advfilter') || false;
      requestData.preciseSearch =  model.get('preciseSearch') || false;
      if (requestData.preciseSearch || requestData.fuzzySearch) {
          var filterParam = model.get("filterParam");
          Object.assign(requestData, filterParam);
      } else if (search) {
          requestData.search = model.get('searchTerm') || '';
      }
      var successHandler = function (data) {

        if (data.total > 0) {
            $("#executingPageTable").show();
        } else {
            $("#executingPageTable").hide();
        }
        model.set({
            "executingFlowData": data.executingFlowData,
            "total": data.total
        });
        model.trigger("render");
      };
      $.get(requestURL, requestData, successHandler, "json");
    },

  })

var advRunningFilterView;
azkaban.AdvRunningFilterView = Backbone.View.extend({
    events: {
        "click #running-filter-btn": "handleAdvFilter",//模糊查询
        "click #running-precise-filter-btn": "preciseFilter"//精准查询
    },

    initialize: function (settings) {
        if (!document.getElementById('startDatetimeBegin') || !$('#startDatetimeBegin').datetimepicker) {
            return;
        }
        $('#startDatetimeBegin').datetimepicker();
        $('#startDatetimeEnd').datetimepicker();
        $('#startDatetimeBegin').on('change.dp', function (e) {
            $('#startDatetimeEnd').data('DateTimePicker').setStartDate(e.date);
        });
        $('#startDatetimeEnd').on('change.dp', function (e) {
            $('#startDatetimeBegin').data('DateTimePicker').setEndDate(e.date);
        });
        $('.selected').children("a").css("background-color", "#c0c1c2");
        $('#status').select2();
    },
    render: function () {
    },
    handleAdvFilter () {
        this.submitAdvFilter('filter')
    },
    preciseFilter () {
        this.submitAdvFilter('precise')
    },
    submitAdvFilter: function (filterType) {
        console.log("handleAdv");
        var projcontain = $('#projcontain').val();
        var flowcontain = $('#flowcontain').val();
        var usercontain = $('#usercontain').val();
        var startBeginTime = $('#startDatetimeBegin').val();
        var startEndTime = $('#startDatetimeEnd').val();
        var flowType = $('#flowType').val();


        if (checkEnglish(usercontain)) {
            return;
        };


        if (filterType === 'filter') {
            executingView.model.set({
                preciseSearch: false,
                search: false,
                advfilter: true,
                searchTerm: '',
            })
        } else {
            executingView.model.set({
                preciseSearch: true,
                search: false,
                advfilter: false,
                searchTerm: '',
            })
        }
        console.log("filtering history");
        executingView.init = false;
        $('#searchtextbox').val('');
        executingView.model.set('filterParam', {
            projcontain: projcontain,
            flowcontain: flowcontain,
            usercontain: usercontain,
            startBeginTime: startBeginTime,
            startEndTime: startEndTime,
            flowType: flowType === "-1" ? '' : flowType
        })
        //请求接口
        executingView.model.trigger("change:view");
        $("#adv-running-filter").modal('hide');
    },
    initFilterForm () {
        $('#projcontain').val('');
        $('#flowcontain').val('');
        $('#usercontain').val('');
        $('#status').val([0]).trigger("change");
        $('#startDatetimeBegin').val('');
        $('#startDatetimeEnd').val('');
        $('#flowType').val('');
        $('#flowRemarks').val('');
    },

});

//用于保存浏览数据，切换页面也能返回之前的浏览进度。
var cycleExecutionModel;
azkaban.CycleExecutionModel = Backbone.Model.extend({});

//循环执行页面处理方法 组装表格和翻页处理
var cycleExecutionView;
azkaban.CycleExecutionView = Backbone.View.extend({
    events: {
        "click #cycleExecutionPageTable .projectPageSelection  li": "handleChangePageSelection",
        "change #cycleExecutionPageTable .pageSizeSelect": "handlePageSizeSelection",
        "click #cycleExecutionPageTable .pageNumJump": "handlePageNumJump",
    },
    initialize: function () {
      this.model.bind('render', this.render, this);

      this.model.set('elDomId','cycleExecutionPageTable');
      this.model.bind('change:view', this.handleChangeView, this);
      this.model.bind('change:page', this.handlePageChange, this);
      this.model.set({ page: 1, pageSize: 20 });
      this.createResize();

    },
    render: function (evt) {
      console.log("render");
    //   var tbody = $("#cycleExecutionTableBody");
    //   tbody.empty();
      var oldCycleExecutionTableBody = document.getElementById('cycleExecutionTableBody');
      var childrenNum = oldCycleExecutionTableBody.children.length;
      var newCycleExecutionTableBod = document.createElement('tbody');
      newCycleExecutionTableBod.setAttribute('id', 'cycleExecutionTableBody');
      var executions = this.model.get("executionCycleList");
      if (!executions) {
        executions = [];
      }
      for (var i = 0; i < executions.length; i++) {
        var row = document.createElement("tr");

        //组装数字行
        var tdNum = document.createElement("td");
        $(tdNum).text(i + 1);
        $(tdNum).attr("class", "tb-name");
        row.appendChild(tdNum);

        //组装Flow行
        var tdFlow = document.createElement("td");
        var flowA = document.createElement("a");
        $(flowA).attr("href", filterXSS("/manager?project=" + executions[i].projectName + "&flow=" + executions[i].flowId));
        $(flowA).text(executions[i].flowId);
        $(flowA).attr("style", "word-break:break-all;");
        tdFlow.appendChild(flowA);
        row.appendChild(tdFlow);

        //组装Project行
        var tdProject = document.createElement("td");
        var projectA = document.createElement("a");
        $(projectA).attr("href", filterXSS("/manager?project=" + executions[i].projectName));
        $(projectA).text(executions[i].projectName);
        $(projectA).attr("style", "word-break:break-all;");
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
        if (executions[i].currentExecId === -1) {
          $(nowExecA).text(wtssI18n.view.waitingExecution);
        } else {
          $(nowExecA).attr("href", filterXSS("/executor?execid=" + executions[i].currentExecId));
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
        if (executions[i].status === "RUNNING") {
          var actionButton = document.createElement("button");
          $(actionButton).text(wtssI18n.view.killCycleExecution).attr("rowIndex", i).attr("class", "btn btn-danger btn-sm").click(this.killCycleConfirm);
          tdAction.appendChild(actionButton);
        }
        row.appendChild(tdAction);
        newCycleExecutionTableBod.appendChild(row);
      }
      oldCycleExecutionTableBody.parentElement.replaceChild(newCycleExecutionTableBod, oldCycleExecutionTableBody);
      this.renderPagination(evt);
    },
    killCycleConfirm: function (e) {
        var rowIndex = e.target.getAttribute("rowIndex");
        var rowData = cycleExecutionModel.get("executionCycleList")[rowIndex] || {};
        cycleExecutionModel.set("currentRowData", rowData);
        var prompt = window.langType === 'zh_CN' ? '是否结束' + rowData.flowId + '工作流循环执行': 'Whether to finish loop executing ' + rowData.flowId + ' workflow';
        deleteDialogView.show(wtssI18n.deletePro.endLoopExecution, prompt, wtssI18n.common.cancel, wtssI18n.view.killCycleExecution, '', killCycle);
    },
    ...commonPaginationFun(),
    handlePageChange: function () {
      var page = this.model.get("page");
      var pageSize = this.model.get("pageSize");
      var requestURL = "/cycle";
      var model = this.model;
      var requestData = {
        "ajax": "fetchCycleFlows",
        "page": page,
        "pageSize": pageSize
      };
      var successHandler = function (data) {
        if (data.total > 0) {
          $("#cycleExecutionPageTable").show();

          model.trigger("render");
        } else {
          $("#cycleExecutionPageTable").hide();
        }
        model.set({
            "executionCycleList": data.executionCycleList,
            "total": data.total
          });
        model.trigger("render");
      };
      $.get(requestURL, requestData, successHandler, "json");
    },
  });

function killCycle (id) {
    if (!id) {
        var rowData =  cycleExecutionModel.get("currentRowData");
        id = rowData.id;
    }
  var requestURL = document.location.href.replace("recover", "executor");
  var requestData = { "id": id, "ajax": "stopCycleFlow" };
  var successHandler = function (data) {
    if (data.error) {
      executionShowDialog("Error", data.error);
    }
    else {
      executionShowDialog("Cancelled", "Cycle Flow has been cancelled.");
    }
  };
  ajaxCall(requestURL, requestData, successHandler);
}

$(function () {
    // 正在运行
    executingModel = new azkaban.ExecutingModel({
        tbTodyId: "executing-tbody",
        elDomId: "executingPageTable"
    });
    executingView = new azkaban.ExecutingView({
        el: $('#executing-flows-view'),
        model: executingModel
    });

  // 历史重跑 在切换选项卡之前创建模型
  recoverHistoryModel = new azkaban.RecoverHistoryModel({
    tbTodyId: "execTableBody",
    elDomId: "recoverHistoryPageTable"
  });
  recoverHistoryView = new azkaban.RecoverHistoryView({
    el: $('#recover-history-view'),
    model: recoverHistoryModel
  });

  // 循环执行
  cycleExecutionModel = new azkaban.CycleExecutionModel({
    tbTodyId: "cycleExecutionTableBody",
    elDomId: "cycleExecutionPageTable"
  });
  cycleExecutionView = new azkaban.CycleExecutionView({
    el: $('#cycle-execution-view'),
    model: cycleExecutionModel
  });

  function searchTableList() {
    var searchterm = $('#searchtextbox').val()
    executingModel.set({
        preciseSearch: false,
        search: true,
        advfilter: false,
        searchTerm: searchterm,
    });
    executingView.init = false;
    executingView.model.trigger("change:page");
  }

  $("#quick-running-serach-btn").click(function () {
    searchTableList();
  });

advRunningFilterView = new azkaban.AdvRunningFilterView({
    el: $('#adv-running-filter'),
    model: executingModel
});

$('#adv-running-filter-btn').click(function () {
    $('#adv-running-filter').modal();
    advRunningFilterView.initFilterForm()
});

$('#searchtextbox').keyup(function (e) {
    if (e.keyCode === 13) {
        searchTableList();
    }
});

    $("#executionTimeHeader").click(function(e) {
        e.stopPropagation();
        var trList = Array.from($("#executing-tbody").children()) ;
        executingView.isAsc = executingView.isAsc === undefined ? false : !executingView.isAsc;
        if (!trList.length) {
            return;
        }
        trList.sort((a, b) => executingView.isAsc ? handleExecutionTime(b.children[8].innerText) - handleExecutionTime(a.children[8].innerText): handleExecutionTime(a.children[8].innerText)- handleExecutionTime(b.children[8].innerText));
        var oldExecuteTbody = document.getElementById('executing-tbody');
        var newExecuteTbody = document.createElement('tbody');
        newExecuteTbody.setAttribute('id', 'executing-tbody');
        for (var i = 0; i < trList.length; ++i) {
            newExecuteTbody.appendChild(trList[i]);
        }
        oldExecuteTbody.parentNode.replaceChild(newExecuteTbody, oldExecuteTbody);
    });

  executionsTabView = new azkaban.ExecutionsTabView({ el: $('#header-tabs') });
  if (window.location.hash) {//浏览器输入对于的链接时跳转到对应的Tab页
    var hash = window.location.hash;
    if (hash == '#recently-finished') {
      executionsTabView.handleRecentlyFinishedViewLinkClick();
    } else if (hash == '#recover-history') {
      executionsTabView.handleRecoverHistoryViewLinkClick();
    } else if (hash == "#cycle-execution") {
      executionsTabView.handleCycleExecutionViewLinkClick();
    } else {
      executionsTabView.handleCurrentlyRunningViewLinkClick();
    }
  } else {
    executionsTabView.handleCurrentlyRunningViewLinkClick();
  }

  $("#exec-page-refresh-btn").click(function () {
    window.location.reload();
  });
});
