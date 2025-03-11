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
  var requestURL = filterXSS("/manager?project=" + projectName + "&flow=" +
    flowId + "&job=" + jobid);
  if (action == "open") {
    window.location.href = requestURL;
  }
  else if (action == "openwindow") {
    window.open(requestURL);
  }
}

var uploadSchView;
azkaban.UploadSchView = Backbone.View.extend({
    events: {
        "click #upload-sch-btn": "handleCreateSch"
    },

    initialize: function(settings) {
        console.log("Hide upload sch modal error msg");
        $("#upload-sch-modal-error-msg").hide();
    },

    handleCreateSch: function(evt) {
        console.log("Upload sch button.");
        $("#upload-sch-form").submit();
    },

    render: function() {}
});

var flowTabView;
azkaban.FlowTabView = Backbone.View.extend({
  events: {
    "click #graphViewLink": "handleGraphLinkClick",
    "click #executionsViewLink": "handleExecutionLinkClick",
    "click #flowtriggersViewLink": "handleFlowTriggerLinkClick",
    "click #summaryViewLink": "handleSummaryLinkClick",
    "click #flowParamViewLink": "handleFlowParamLinkClick",
    "click #applyLinkedData": "handleApplyLinkeClick",
    "click #historyRerunTime": "handleHistoryRerunTime",
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
  tabIdArray:['executionsViewLink', 'graphViewLink', 'flowtriggersViewLink', 'summaryViewLink', 'flowParamViewLink', 'applyLinkedData', 'historyRerunTime'],
  viewIdArray:['graphView', 'flowtriggerView', 'executionsView', 'summaryView', 'flowParamView', 'linkedDataView', 'linkedHistoryRerunView'],
  handleGraphLinkClick: function () {
    this.handlePageShow('graphViewLink', 'graphView');
  },

  handleExecutionLinkClick: function () {
    this.handlePageShow('executionsViewLink', 'executionsView');
    executionModel.trigger("change:view");
  },

  handleFlowTriggerLinkClick: function () {
    this.handlePageShow('flowtriggersViewLink', 'flowtriggerView');
    flowTriggerModel.trigger("change:view");
  },

  handleSummaryLinkClick: function () {
    this.handlePageShow('summaryViewLink', 'summaryView');
  },

  handleFlowParamLinkClick: function () {
    this.handlePageShow('flowParamViewLink', 'flowParamView');
  },
  handleApplyLinkeClick () {
    this.handlePageShow('applyLinkedData', 'linkedDataView');
  },
  handleHistoryRerunTime () {
    this.handlePageShow('historyRerunTime', 'linkedHistoryRerunView');
  },
  handlePageShow(currentTabId, currentViewId) {
    $('#' + currentTabId ).addClass('active');
    $('#' + currentViewId).show();
    for (var i = 0 ; i < this.tabIdArray.length; i++){
        if (this.tabIdArray[i] !== currentTabId) {
            $('#' +this.tabIdArray[i] ).removeClass('active');
        }
    }
    for (var j = 0 ; j < this.viewIdArray.length; j++){
        if (this.viewIdArray[j] !== currentViewId) {
            $('#' + this.viewIdArray[j]).hide();
        }
    }
  }
});

var jobListView;
var svgGraphView;
var executionsView;
var flowParamView;

azkaban.ExecutionsView = Backbone.View.extend({
  events: {
    "click #pageTable .projectPageSelection  li": "handleChangePageSelection",
    "change #pageTable .pageSizeSelect": "handlePageSizeSelection",
    "click #pageTable .pageNumJump": "handlePageNumJump",
  },

  initialize: function (settings) {
    this.model.bind('render', this.render, this);
    this.model.set({ page: 1, pageSize: 20 });
    this.previousEle = $("#previous");
    this.previousAEle =  $("#previous a");
    this.nextEle =  $("#next");
    this.nextAEle =  $("#next a");

    this.model.set('elDomId','pageTable');
    this.model.bind('change:view', this.handleChangeView, this);
    this.model.bind('change:page', this.handlePageChange, this);
    this.createResize();
  },
  //组装数据表格
  render: function (evt) {
    console.log("render");
    // Render page selections
    var content = this.model.get("content");
    var oldTbody;
    var newTbody = document.createElement('tbody');
    if (content == "flow") {
       oldTbody = document.getElementById('execTableBody');
       newTbody.setAttribute('id', 'execTableBody');
    } else {
       oldTbody = document.getElementById('triggerTableBody');
       newTbody.setAttribute('id', 'triggerTableBody');
    }
    var childrenNum = oldTbody.children.length;
    // tbody.empty();

    var executions = this.model.get("executions");
    for (var i = 0; i < executions.length; ++i) {
      var row = document.createElement("tr");
      //组装ID
      var tdId = document.createElement("td");
      var execA = document.createElement("a");
      if (content == "flow") {
        $(execA).attr("href", filterXSS("/executor?execid="
          + executions[i].execId));
        $(execA).text(executions[i].execId);
      }
      else {
        $(execA).attr("href", filterXSS("/executor?triggerinstanceid="
          + executions[i].instanceId));
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
        startTime = getProjectModifyDateFormat(startDateTime);
      }

      var tdStartTime = document.createElement("td");
      $(tdStartTime).text(startTime);
      row.appendChild(tdStartTime);
      //组装结束时间
      var endTime = "-";
      var lastTime = executions[i].endTime;
      if (executions[i].endTime != -1 && executions[i].endTime != 0) {
        var endDateTime = new Date(executions[i].endTime);
        endTime = getProjectModifyDateFormat(endDateTime);
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
      //组装执行类型
      var tdFlowType = document.createElement("td");
      $(tdFlowType).text(executions[i].flowType);
      row.appendChild(tdFlowType);

      //备注
      var tdComment = document.createElement("td");
      $(tdComment).attr("style", "overflow: hidden;text-overflow: ellipsis;white-space: nowrap;max-width: 60px;");
      $(tdComment).attr("title", executions[i].comment);
      $(tdComment).text(executions[i].comment);
      row.appendChild(tdComment);

      newTbody.appendChild(row);
    }
    oldTbody.parentNode.replaceChild(newTbody, oldTbody);
    this.renderPagination(evt);
  },

  ...commonPaginationFun(),

  handlePageChange: function (evt) {
    var page = this.model.get("page") - 1;
    var pageSize = this.model.get("pageSize");
    var content = this.model.get("content");

    var model = this.model;
    var requestData = {
      "project": projectName,
      "flow": flowId,
      "ajax": content == 'flow' ? "fetchFlowExecutions" : "fetchTriggerInstances",
      "start": page * pageSize,
      "length": pageSize,
      "page": page,
    };

    if (content == 'flow') {
        requestURL = "/manager";
        var search = model.get('search') || true;
        var advfilter = model.get('advfilter') || false;
        var preciseSearch = model.get('preciseSearch') || false;
        var filterParam = model.get("filterParam");
        if (preciseSearch) {
            requestData.preciseSearch = true;
            Object.assign(requestData, filterParam);
            delete requestData.search;
            delete requestData.advfilter;
        }  else if (advfilter) {
            requestData.advfilter = true;
            Object.assign(requestData, filterParam);
            delete requestData.search;
            delete requestData.preciseSearch;
        } else if (search) {
            requestData.search = true;
            requestData.searchTerm = model.get('searchTerm') || '';
        }
    } else {
        requestURL = "/flowtriggerinstance";
    }


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
    // 从大换到小size为页面数据渲染过多，滚动条在最下面导致页面空白
    if ((pageSize - this.model.get('pageSize')) < -20) {
        document.documentElement.scrollTop = 0
    }
    this.model.set({ "pageSize": pageSize });
    this.model.set({ "page": 1 });

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
    executionModel.trigger("change:view");
  },
  initFilterForm () {
    $('#execIdcontain').val('');
    $('#usercontain').val('');
    $('#status').val([0]).trigger("change");
    $('#startDatetimeBegin').val('');
    $('#startDatetimeEnd').val('');
    $('#endDatetimeBegin').val('');
    $('#endDatetimeEnd').val('');
    $('#flowType').val('');
    $('#flowRemarks').val('');
    $('#runDate').val('');
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
    var requestURL = "/manager";
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
    var requestURL = "/schedule"
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

    var requestURL = "/schedule"
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
    var requestURL = "/manager";
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
    var requestURL = "/flowtrigger"
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

var advFlowFilterView;
azkaban.AdvFlowFilterView = Backbone.View.extend({
    events: {
        "click #flow-filter-btn": "handleAdvFilter",//模糊查询
        "click #flow-precise-filter-btn": "preciseFilter"//精准查询
    },

    initialize: function (settings) {
        $('#startDatetimeBegin').datetimepicker();
        $('#startDatetimeEnd').datetimepicker();
        $('#endDatetimeBegin').datetimepicker();
        $('#endDatetimeEnd').datetimepicker();
        $('#runDate').datetimepicker({
            format: 'YYYYMMDD'
        });
        $('#startDatetimeBegin').on('change.dp', function (e) {
            $('#startDatetimeEnd').data('DateTimePicker').setStartDate(e.date);
        });
        $('#startDatetimeEnd').on('change.dp', function (e) {
            $('#startDatetimeBegin').data('DateTimePicker').setEndDate(e.date);
        });
        $('#endDatetimeBegin').on('change.dp', function (e) {
            $('#endDatetimeEnd').data('DateTimePicker').setStartDate(e.date);
        });
        $('#endDatetimeEnd').on('change.dp', function (e) {
            $('#endDatetimeBegin').data('DateTimePicker').setEndDate(e.date);
        });
        $('#runDate').on('change.dp', function (e) {
            $('#runDate').data('DateTimePicker').setRunDate(e.date);
        })
        $('#adv-filter-error-msg').hide();
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
        var execIdcontain = $('#execIdcontain').val();
        var usercontain = $('#usercontain').val();
        var status = $('#status').val();
        var startBeginTime = $('#startDatetimeBegin').val();
        var startEndTime = $('#startDatetimeEnd').val();
        var finishBeginTime = $('#endDatetimeBegin').val();
        var finishEndTime = $('#endDatetimeEnd').val();
        var flowType = $('#flowType').val();
        var flowRemarks =  $('#flowRemarks').val();
        var runDate = $('#runDate').val();


        if (checkExecId(execIdcontain)) {
            return;
        };
        if (checkEnglish(usercontain)) {
            return;
        };

        // 将所有状态设置为默认
        if (!status|| (status.length === 1 && status[0] === "0")) {
            status = "0";
            $('#status').val([0]).trigger("change");
        } else {
            for (var i = status.length - 1; i >= 0; i--) {
                if (status[i] === "0") {
                    status.splice(i, 1)
                }
            }
            status = status.toString()
        };
        if (filterType === 'filter') {
            executionsView.model.set({
                advfilter: true,
                search: false,
                preciseSearch: false,
                searchTerm: '',
                content: "flow",
            })
        } else {
            executionsView.model.set({
                preciseSearch: true,
                search: false,
                advfilter: false,
                searchTerm: '',
                content: "flow",
            })
        }
        console.log("filtering history");
        executionsView.init = false;
        $('#searchtextbox').val('');
        executionsView.model.set('filterParam', {
            execIdcontain: execIdcontain,
            usercontain: usercontain,
            status: status,
            startBeginTime: startBeginTime,
            startEndTime: startEndTime,
            finishBeginTime: finishBeginTime,
            finishEndTime: finishEndTime,
            flowType: flowType,
            comment: flowRemarks,
            runDate: runDate
        })
        //请求接口
        executionsView.model.trigger("change:view");
        $('#adv-flow-filter').modal('hide');
    },
    initFilterForm () {
        $('#execIdcontain').val('');
        $('#usercontain').val('');
        $('#status').val([0]).trigger("change");
        $('#startDatetimeBegin').val('');
        $('#startDatetimeEnd').val('');
        $('#endDatetimeBegin').val('');
        $('#endDatetimeEnd').val('');
        $('#flowType').val('');
        $('#flowRemarks').val('');
        $('#runDate').val('');
    },

});

//关联数据
function getRenderLinkTableFun (elDomId){
    return {
        initialize: function (settings) {
            changePageSizeSelectValue(elDomId, [10, 20, 50]);
            this.model.bind('change:page', this.handlePageChange, this);
            this.model.set({ page: 1, pageSize: 10 });
            this.createResize();
        },
        render: function () {
            console.log("render");
            // Render page selections
            var tableBody = $("#" + this.model.get('tbTodyId'));
            tableBody.empty();

            var tableList = this.model.get("tableList");
            for (var i = 0; i < tableList.length; ++i) {
            var row = document.createElement("tr");

            //组装数字行
            var tdNum = document.createElement("td");
            $(tdNum).text(i + 1);
            $(tdNum).attr("class", "tb-name");
            row.appendChild(tdNum);

            //组装数据源类型
            var dtdSourceType = document.createElement("td");
            $(dtdSourceType).text(tableList[i].dataSourceType);
            row.appendChild(dtdSourceType);

            //组装集群
            var tdCluster = document.createElement("td");
            $(tdCluster).text(tableList[i].cluster);
            row.appendChild(tdCluster);

            //组装数据库
            var tdDatabase = document.createElement("td");
            $(tdDatabase).text(tableList[i].database);
            row.appendChild(tdDatabase);

            //组装数据表
            var tdTable = document.createElement("td");
            $(tdTable).text(tableList[i].table);
            row.appendChild(tdTable);

            //组装子系统
            var tdSubsystem = document.createElement("td");
            $(tdSubsystem).text(tableList[i].subsystem);
            row.appendChild(tdSubsystem);

            //组装开发部门
            var tdDevelopDepartment = document.createElement("td");
            $(tdDevelopDepartment).text(tableList[i].developDepartment);
            row.appendChild(tdDevelopDepartment);

            //组装开发负责人
            var tdDeveloper = document.createElement("td");
            $(tdDeveloper).text(tableList[i].developer);
            row.appendChild(tdDeveloper);
            tableBody.append(row);

            this.renderPagination();

            // $("#executingJobs").trigger("update");
            // $("#executingJobs").trigger("sorton", "");
            }
        },
        ...commonPaginationFun(),
        handlePageNumJump: function (evt) {
            var start = this.model.get("page") - 1;
            var pageSize = this.model.get("pageSize");
            var requestURL = "/manager";

            var that = this;
            var requestData = {
            "ajax": "getLineageBusiness",
            "page": start,
            "pageSize": pageSize,
            "pageNum": this.model.get("page"),
            searchDataType: this.model.get('tbTodyId') === 'flowLinkInputDataBody' ? 'IN' : 'OUT',
            project: projectName,
            flowName: flowId,
            jobName: ""
            }

            var successHandler = function (data) {
            if (data.code === "200") {
                that.model.set({
                "tableList": data.lineageBusinessList ? data.lineageBusinessList : [],
                "total": data.lineageBusinessListSize
                });
                if (data.lineageBusinessList) {
                that.render();
                } else {
                that.renderPagination();
                }
                $('#link-data-error-msg').hide();
            } else {
                var prompt = window.langType === 'zh_CN'? data.jobCode + '工作流暂无权限，请到DMS增加工作流权限' : 'There is currently no permission for the' + data.jobCode +' flow. Please increase the flow permission in DMS';

                var error = data.code === "401" || data.code === "403" ? prompt : data.error
                $('#link-data-error-msg').html(error).show();
            }

            };
            $.get(requestURL, requestData, successHandler, "json");
        }
    }
}
var linkInputModel;
azkaban.LinkInputModel = Backbone.Model.extend();
var linkOutModel;
azkaban.LinkOutModel = Backbone.Model.extend();
var linkInputDataView
var linkOutDataView
azkaban.LinkedInputDataView = Backbone.View.extend({
    events: {
        "click #inputPageTable .projectPageSelection  li": "handleChangePageSelection",
        "change #inputPageTable .pageSizeSelect": "handlePageSizeSelection",
        "click #inputPageTable .pageNumJump": "handlePageNumJump",
    },
    ...getRenderLinkTableFun("inputPageTable"),
})
azkaban.LinkedOutDataView = Backbone.View.extend({
    events: {
        "click #outPageTable .projectPageSelection  li": "handleChangePageSelection",
        "change #outPageTable .pageSizeSelect": "handlePageSizeSelection",
        "click #outPageTable .pageNumJump": "handlePageNumJump",
    },
    ...getRenderLinkTableFun("outPageTable"),
})
//历史重跑
var historyReturnModel;
azkaban.HistoryReturnModel = Backbone.Model.extend();
var historyReturnDataView;
azkaban.HistoryReturnDataView = Backbone.View.extend({
    events: {
        "click #historyReturnPageTable .projectPageSelection  li": "handleChangePageSelection",
        "change #historyReturnPageTable .pageSizeSelect": "handlePageSizeSelection",
        "click #historyReturnPageTable .pageNumJump": "handlePageNumJump",
    },
    initialize: function (settings) {
      changePageSizeSelectValue("historyReturnPageTable", [10, 20,50]);
      this.model.set('elDomId','historyReturnPageTable');
      this.model.bind('change:page', this.handlePageChange, this);
      this.model.set({ page: 1, pageSize: 10 });
      this.createResize();
    },
    render: function () {
      console.log("render");
      // Render page selections
      var tableBody = $("#" + this.model.get('tbTodyId'));
      tableBody.empty();

      var tableList = this.model.get("tableList");
      for (var i = 0; i < tableList.length; ++i) {
        var row = document.createElement("tr");
          //组装编号
          var tdNum = document.createElement("td");
          $(tdNum).text(i + 1);
          row.appendChild(tdNum);
          //组装Id
          var tdId = document.createElement("td");
          $(tdId).text(tableList[i].recoverId);
          row.appendChild(tdId);
          //组装开始时间
          var tdBegin = document.createElement("td");
          $(tdBegin).text(getRecoverDateFormat(new Date(tableList[i].begin)));
          row.appendChild(tdBegin);

          //组装结束时间
          var tdEnd = document.createElement("td");
          $(tdEnd).text(getRecoverDateFormat(new Date(tableList[i].end)));
          row.appendChild(tdEnd);

          //组装执行开始时间
          var tdStartTime = document.createElement("td");
          $(tdStartTime).text(tableList[i].startTime);
          row.appendChild(tdStartTime);

          //组装执行结束时间
          var tdEndTime = document.createElement("td");
          $(tdEndTime).text(tableList[i].endTime);
          row.appendChild(tdEndTime);

          //组装重跑间隔
          var tdRecoverNum = document.createElement("td");
          $(tdRecoverNum).text(tableList[i].recoverNum);
          row.appendChild(tdRecoverNum);

          //组装间隔单位
          var tdInterval = document.createElement("td");
          $(tdInterval).text(tableList[i].recoverInterval);
          row.appendChild(tdInterval);

          //历史重跑自然日
          var tdRunDate = document.createElement("td");
          var divRunDate = document.createElement("div");
          var runDate = '';
          var runDateTimeList = tableList[i].runDateTimeList;
          if (Array.isArray(runDateTimeList)) {
              for(var h = 0; h < runDateTimeList.length ; h++) {
                  runDate += getRecoverDateFormat(new Date(runDateTimeList[h])) + ',';
              }
              runDate = runDate.substring(0,runDate.length - 1);
          }
          $(divRunDate).text(runDate).css('max-width', '300px').css('word-break', 'break-all');
          tdRunDate.appendChild(divRunDate);
          row.appendChild(tdRunDate);

          //重跑跳过自然日
          var tdSkipDate = document.createElement("td");
          var divSkipDate = document.createElement("div");
          var skipDate = '';
          var skipDateTimeList = tableList[i].skipDateTimeList;
          if (Array.isArray(skipDateTimeList)) {
              for(var f = 0; f < skipDateTimeList.length ; f++) {
                  skipDate += getRecoverDateFormat(new Date(skipDateTimeList[f])) + ',';
              }
              skipDate = skipDate.substring(0,skipDate.length - 1);//
          }
          $(divSkipDate).text(skipDate).css('max-width', '300px').css('word-break', 'break-all');;
          tdSkipDate.appendChild(divSkipDate);
          row.appendChild(tdSkipDate);

          //重跑并发数
          var tdTaskSize = document.createElement("td");
          $(tdTaskSize).text(tableList[i].taskSize);
          row.appendChild(tdTaskSize);

          //组装重跑间隔
          var tdTaskDistributeMethod = document.createElement("td");
          var methodsName = tableList[i].taskDistributeMethod === 'sequential' ? wtssI18n.view.executeChronologicalOrder : wtssI18n.view.uniformDistributionMethod;
          $(tdTaskDistributeMethod).text(methodsName);
          row.appendChild(tdTaskDistributeMethod);


          tableBody.append(row);

          this.renderPagination();
      }
    },
    ...commonPaginationFun(),
    handlePageChange: function (evt) {
      var start = this.model.get("page") - 1;
      var pageSize = this.model.get("pageSize");
      var requestURL = "/manager";

      var that = this;
      var requestData = {
        "ajax": "fetchHistoryRerunConfiguration",
        "page": start,
        "pageSize": pageSize,
        "pageNum": this.model.get("page"),
        project: projectName,
        flow: flowId,
      }

      var successHandler = function (data) {
          if (data.projectHistoryRerunConfigList && data.projectHistoryRerunConfigList.length > 0 ) {
              that.model.set({
              "tableList": data.projectHistoryRerunConfigList,
              "total": data.total
              })
              that.render();
          }
      };
      $.get(requestURL, requestData, successHandler, "json");
    }
})

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
  uploadScheduleView = new azkaban.UploadSchView({
    el: $('#upload-sch-modal')
  });

  //上传定时调度信息文件绑定事件
  document.getElementById('schfile').addEventListener('change', function() {
    document.getElementById('schfilefieldsNameBox').innerHTML = filterXSS(this.files[0].name)
  }, false)

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

  var requestURL = "/manager";
  //关联数据
  //输入数据
  linkInputModel = new azkaban.LinkInputModel(
    {
      tbTodyId: "flowLinkInputDataBody",
      elDomId: "inputPageTable"
    }
  );
  linkInputDataView = new azkaban.LinkedInputDataView({
    el: $('#flowLinkInputBox'),
    model: linkInputModel,
  })
  //输出数据
  linkOutModel = new azkaban.LinkOutModel({
    tbTodyId: "flowLinkoutputDataBody",
    elDomId: "outPageTable"
  });
  linkOutDataView = new azkaban.LinkedOutDataView({
    el: $('#flowLinkOutBox'),
    model: linkOutModel,
  })

  //历史重跑
  historyReturnModel = new azkaban.HistoryReturnModel({
    tbTodyId: "hstoryRerunBody",
    elDomId: "historyReturnPageTable"
  });
  historyReturnDataView = new azkaban.HistoryReturnDataView({
    el: $('#linkedHistoryRerunView'),
    model: historyReturnModel,
  })
    function searchTableList () {
        var searchterm = $('#searchtextbox').val()
            executionsView.init = false;
            executionModel.set({
                search: true,
                preciseSearch: false,
                advfilter: false,
                searchTerm: searchterm,
                content: "flow",
            });
        executionsView.model.trigger("change:view");
    }
    $("#quick-flow-serach-btn").click(function () {
        searchTableList();
    });

    $('#searchtextbox').keyup(function (e) {
        if (e.keyCode === 13) {
            searchTableList();
        }
    });

    advFlowFilterView = new azkaban.AdvFlowFilterView({
        el: $('#adv-flow-filter'),
        model: executionModel
    });

    $('#adv-flow-filter-btn').click(function () {
        $('#adv-flow-filter').modal();
        advFlowFilterView.initFilterForm()
    });
  // Set up the Flow options view. Create a new one every time :p

  //
  $('#executebtn').click(function () {

    // 点击flow, 进入页面, 页面右上角--执行工作流
    // 需要校验是否具有执行工作流权限 1:允许, 2:不允许
    var requestURL = "/manager?ajax=checkUserExecuteFlowPermission&project=" + projectName;
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
          localStorage.setItem('isBackupRerun', "false"); // 清除容灾重跑
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
      if (hash == "#linkFlowData") {
        flowTabView.handleApplyLinkeClick();
      }
      if (hash == "#linkHistoryRerunTime") {
        flowTabView.handleHistoryRerunTime();
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
    var scheduleStartDate = "";
    var scheduleEndDate = "";
    var comment = "";
    var tempFlowId = mainSvgGraphView.model.get('data').flow;
    var requestURLForFetchScheduleId = "/manager?ajax=fetchRunningScheduleId&project=" + projectName + "&flow=" + tempFlowId;
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
          scheduleStartDate = data.scheduleStartDate;
          scheduleEndDate = data.scheduleEndDate;
          comment = data.comment;
        }
      }
    });

    // 点击flow, 进入页面, 页面右上角--执行调度
    // 需要校验是否具有执行调度权限 1:允许, 2:不允许
    var requestURL = "/manager?ajax=checkUserScheduleFlowPermission&project=" + projectName;
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
              scheduleStartDate: scheduleStartDate,
              scheduleEndDate: scheduleEndDate,
              comment:comment,
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
          $('#body-user-operator-flow-permit').html(filterXSS(data["noSchPermissionsFlow"]));
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
