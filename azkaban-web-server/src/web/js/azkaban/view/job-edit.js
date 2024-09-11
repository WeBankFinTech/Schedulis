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

var jobEditView;
azkaban.JobEditView = Backbone.View.extend({
  events: {
    "click": "closeEditingTarget",
    "click #set-btn": "handleSet",
    "click #cancel-btn": "handleCancel",
    "click #close-btn": "handleCancel",
    "click #add-btn": "handleAddRow",
    "click table .editable": "handleEditColumn",
    "click table .remove-btn": "handleRemoveColumn"
  },

  initialize: function (setting) {
    this.projectURL = contextURL + "manager"
    this.generalParams = {}
    this.overrideParams = {}

    if (window.location.hash) {
      var hash = window.location.hash;
      if (hash == "#jobExecutions") {
        jobTabView.handleJobExecutionsClick();
      } else {
        if ("#page" == hash.substring(0, "#page".length)) {
          var page = hash.substring("#page".length, hash.length);
          console.log("page " + page);
          jobTabView.handleJobExecutionsClick();
          executionModel.set({"page": parseInt(page)});
        }
        else {
          selected = "jobParam";
        }
      }
      if (hash == "#jobParam") {
        // Redundant, but we may want to change the default.
        selected = "jobParam";
      }

    }
  },

  handleCancel: function (evt) {
    $('#job-edit-pane').hide();
    var tbl = document.getElementById("generalProps").tBodies[0];
    var rows = tbl.rows;
    var len = rows.length;
    for (var i = 0; i < len - 1; i++) {
      tbl.deleteRow(0);
    }
  },

  show: function (projectName, flowName, jobName) {
    this.projectName = projectName;
    this.flowName = flowName;
    this.jobName = jobName;

    var projectURL = this.projectURL

    $('#job-edit-pane').modal();

    var handleAddRow = this.handleAddRow;

    /*var overrideParams;
    var generalParams;
    this.overrideParams = overrideParams;
    this.generalParams = generalParams;*/
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
      document.getElementById('jobName').innerHTML = data.jobName;
      document.getElementById('jobType').innerHTML = data.jobType;
      var generalParams = data.generalParams;
      var overrideParams = data.overrideParams;

      /*for (var key in generalParams) {
        var row = handleAddRow();
        var td = $(row).find('span');
        $(td[1]).text(key);
        $(td[2]).text(generalParams[key]);
      }*/

      mythis.overrideParams = overrideParams;
      mythis.generalParams = generalParams;

      if (overrideParams && $(".editRow").length == 0)  {
        for (var okey in overrideParams) {
          if (okey != 'type' && okey != 'dependencies') {
            var row = handleAddRow();
            var td = $(row).find('span');
            $(td[0]).text(okey);
            $(td[1]).text(overrideParams[okey]);
          }
        }
      }
    };

    $.get(projectURL, fetchJobInfo, fetchJobSuccessHandler, "json");
  },

  handleSet: function (evt) {
    this.closeEditingTarget(evt);
    var jobOverride = {};
    var editRows = $(".editRow");
    for (var i = 0; i < editRows.length; ++i) {
      var row = editRows[i];
      var td = $(row).find('span');
      var key = $(td[0]).text();
      var val = $(td[1]).text();

      if (key && key.length > 0) {
        jobOverride[key] = val;
      }
    }

    var overrideParams = this.overrideParams
    var generalParams = this.generalParams

    jobOverride['type'] = overrideParams['type']
    if ('dependencies' in overrideParams) {
      jobOverride['dependencies'] = overrideParams['dependencies']
    }

    var project = this.projectName
    var flowName = this.flowName
    var jobName = this.jobName

    var jobOverrideData = {
      project: project,
      flowName: flowName,
      jobName: jobName,
      ajax: "setJobOverrideProperty",
      jobOverride: jobOverride
    };

    var projectURL = this.projectURL
    var redirectURL = projectURL + '?project=' + project + '&flow=' + flowName
        + '&job=' + jobName;
    var jobOverrideSuccessHandler = function (data) {
      if (data.error) {
        alert(data.error);
      }
      else {
        window.location = redirectURL;
      }
    };

    $.get(projectURL, jobOverrideData, jobOverrideSuccessHandler, "json");
  },

  handleAddRow: function (evt) {
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
    var valueData = document.createElement("span");
    $(valueData).addClass("spanValue");

    $(tdName).append(nameData);
    $(tdName).addClass("editable");
    nameData.myparent = tdName;

    $(tdValue).append(valueData);
    $(tdValue).append(remove);
    $(tdValue).addClass("editable");
    $(tdValue).addClass("value");
    valueData.myparent = tdValue;

    $(tr).addClass("editRow");
    $(tr).append(tdName);
    $(tr).append(tdValue);

    $(tr).insertBefore("#addRow");
    return tr;
  },

  handleEditColumn: function (evt) {
    var curTarget = evt.currentTarget;
    if (this.editingTarget != curTarget) {
      this.closeEditingTarget(evt);

      var text = $(curTarget).children(".spanValue").text();
      $(curTarget).empty();

      var input = document.createElement("input");
      $(input).attr("type", "text");
      $(input).addClass("form-control").addClass("input-sm");
      $(input).val(text);

      $(curTarget).addClass("editing");
      $(curTarget).append(input);
      $(input).focus();
      var obj = this;
      $(input).keypress(function (evt) {
        if (evt.which == 13) {
          obj.closeEditingTarget(evt);
        }
      });
      this.editingTarget = curTarget;
    }

    evt.preventDefault();
    evt.stopPropagation();
  },

  handleRemoveColumn: function (evt) {
    var curTarget = evt.currentTarget;
    // Should be the table
    var row = curTarget.parentElement.parentElement;
    $(row).remove();
  },

  closeEditingTarget: function (evt) {
    if (this.editingTarget == null ||
        this.editingTarget == evt.target ||
        this.editingTarget == evt.target.myparent) {
      return;
    }
    var input = $(this.editingTarget).children("input")[0];
    var text = $(input).val();
    $(input).remove();

    var valueData = document.createElement("span");
    $(valueData).addClass("spanValue");
    $(valueData).text(text);

    if ($(this.editingTarget).hasClass("value")) {
      var remove = document.createElement("div");
      $(remove).addClass("pull-right").addClass('remove-btn');
      var removeBtn = document.createElement("button");
      $(removeBtn).attr('type', 'button');
      $(removeBtn).addClass('btn').addClass('btn-xs').addClass('btn-danger');
      $(removeBtn).text('Delete');
      $(remove).append(removeBtn);
      $(this.editingTarget).append(remove);
    }

    $(this.editingTarget).removeClass("editing");
    $(this.editingTarget).append(valueData);
    valueData.myparent = this.editingTarget;
    this.editingTarget = null;
  }
});

var executionModel;
azkaban.ExecutionModel = Backbone.Model.extend({});

// var executionsTimeGraphView;

$(function() {

  // 在切换选项卡之前创建模型
  executionModel = new azkaban.ExecutionModel();
  jobExecutionsView = new azkaban.JobExecutionsView({
    el: $('#jobExecutionsView'),
    model: executionModel
  });

  var selected = "";
  jobTabView = new azkaban.jobTabView({
    el: $('#headertabs'),
    selectedView: selected
  });

  jobEditView = new azkaban.JobEditView({
    el: $('#job-edit-pane')
  });

  executionsTimeGraphView = new azkaban.TimeGraphView({
    el: $('#timeGraph'),
    model: executionModel,
    modelField: 'jobExecutions'
  });

  jobParamView = new azkaban.JobParamView({
    el: $('#job-param-table'),
    model: executionModel
  });

});

//Tab页处理 切换Tab效果和初始化
var jobTabView;
azkaban.jobTabView = Backbone.View.extend({
  events: {
    "click #jobParam": "handleJobParamClick",
    "click #jobExecutionsHistory": "handleJobExecutionsClick"
  },

  initialize: function(settings) {
    var selectedView = settings.selectedView;
    if (selectedView == "jobExecutions") {
      this.handleJobExecutionsClick();
    }
    else {
      this.handleJobParamClick();
    }
  },

  render: function() {
    console.log("render graph");
  },

  handleJobParamClick: function(){
    $("#jobParam").addClass("active");
    $('#jobExecutionsHistory').removeClass('active');

    $("#jobParamView").show();
    $('#jobExecutionsView').hide();
  },

  handleJobExecutionsClick: function() {
    $("#jobParam").removeClass("active");
    $("#jobExecutionsHistory").addClass("active");

    $("#jobParamView").hide();
    $("#jobExecutionsView").show();
    executionModel.trigger("change:view");
  },

});

//Job历史执行页面处理方法 组装表格和翻页处理
var jobExecutionsView;
azkaban.JobExecutionsView = Backbone.View.extend({
  events: {
    "click #pageSelection li": "handleChangePageSelection"
  },

  initialize: function(settings) {
    this.model.bind('change:view', this.handleChangeView, this);
    this.model.bind('render', this.render, this);
    this.model.set({page: 1, pageSize: 16});
    this.model.bind('change:page', this.handlePageChange, this);
  },

  render: function(evt) {
    console.log("render");
    // Render page selections
    var tbody = $("#execTableBody");
    tbody.empty();

    var executions = this.model.get("jobExecutions");
    for (var i = 0; i < executions.length; ++i) {
      var row = document.createElement("tr");

      //组装执行ID 数据行
      var tdId = document.createElement("td");
      var execA = document.createElement("a");
      $(execA).attr("href", contextURL + "/executor?execid=" + executions[i].execId);
      $(execA).text(executions[i].execId);
      tdId.appendChild(execA);
      row.appendChild(tdId);

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
      var flowName = executions[i].flowId.split(":").slice(-1);
      $(flowA).attr("href", contextURL + "/manager?project=" + projectName + "&flow=" + flowName);
      $(flowA).text(executions[i].flowId);
      $(flowA).css({"max-width": "500px",
        "display": "inline-block",
        "word-break": "break-all"})
      tdFlow.appendChild(flowA);
      row.appendChild(tdFlow);

      //组装开始时间行
      var startTime = "-";
      if (executions[i].startTime != -1) {
        var startDateTime = new Date(executions[i].startTime);
        startTime = getDateFormat(startDateTime);
      }

      var tdStartTime = document.createElement("td");
      $(tdStartTime).text(startTime);
      row.appendChild(tdStartTime);

      //组装结束时间行
      var endTime = "-";
      var lastTime = executions[i].endTime;
      if (executions[i].endTime != -1) {
        var endDateTime = new Date(executions[i].endTime);
        endTime = getDateFormat(endDateTime);
      }
      else {
        lastTime = (new Date()).getTime();
      }

      var tdEndTime = document.createElement("td");
      $(tdEndTime).text(endTime);
      row.appendChild(tdEndTime);

      //组装执行日期
      var runDate = "-";
      if (executions[i].runDate != -1) {
        var execTime = new Date(executions[i].runDate);
        runDate = getRecoverRunDateFormat(execTime);
      }

      var tdRunDate = document.createElement("td");
      $(tdRunDate).text(runDate);
      row.appendChild(tdRunDate);

      //执行时长
      var tdElapsed = document.createElement("td");
      $(tdElapsed).text( getDuration(executions[i].startTime, lastTime));
      row.appendChild(tdElapsed);



      //组装执行状态行
      var tdStatus = document.createElement("td");
      var status = document.createElement("div");
      $(status).addClass("status");
      $(status).addClass(executions[i].status);
      $(status).text(statusStringMap[executions[i].status]);
      tdStatus.appendChild(status);
      row.appendChild(tdStatus);

      //组装日志行
      var tdLog = document.createElement("td");
      var logA = document.createElement("a");
      //http://webip:port/executor?execid=216859&job=subflow:sub1&attempt=0
      var jobPath = executions[i].flowId.split(",").slice(1).map(function(a){return a.split(":")[0];});
      jobPath.push(executions[i].jobId);
      jobPath = jobPath.join(":");
      $(logA).attr("href", contextURL + "/executor?execid=" + executions[i].execId + "&job=" + jobPath + "&attempt=0");
      $(logA).text(wtssI18n.view.log);
      tdLog.appendChild(logA);
      row.appendChild(tdLog);

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
      $("#previous").removeClass("disabled");
      $("#previous")[0].page = page - 1;
      $("#previous a").attr("href", "#page" + (page - 1));
    }
    else {
      $("#previous").addClass("disabled");
    }

    if (page < numPages) {
      $("#next")[0].page = page + 1;
      $("#next").removeClass("disabled");
      $("#next a").attr("href", "#page" + (page + 1));
    }
    else {
      $("#next")[0].page = page + 1;
      $("#next").addClass("disabled");
    }

    // Selection is always in middle unless at barrier.
    var startPage = 0;
    var selectionPosition = 0;
    if (page < 3) {
      selectionPosition = page;
      startPage = 1;
    }
    else if (page == numPages) {
      selectionPosition = 5;
      startPage = numPages - 4;
    }
    else if (page == numPages - 1) {
      selectionPosition = 4;
      startPage = numPages - 4;
    }
    else {
      selectionPosition = 3;
      startPage = page - 2;
    }

    $("#page"+selectionPosition).addClass("active");
    $("#page"+selectionPosition)[0].page = page;
    var selecta = $("#page" + selectionPosition + " a");
    selecta.text(page);
    selecta.attr("href", "#page" + page);

    for (var j = 0; j < 5; ++j) {
      var realPage = startPage + j;
      var elementId = "#page" + (j+1);

      $(elementId)[0].page = realPage;
      var a = $(elementId + " a");
      a.text(realPage);
      a.attr("href", "#page" + realPage);
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
    var page = this.model.get("page") - 1;
    var pageSize = this.model.get("pageSize");
    var requestURL = contextURL + "/manager";

    var model = this.model;
    var requestData = {
      "project": projectName,
      "flow": flowId,
      "job": jobName,
      "ajax": "fetchJobExecutionsHistory",
      "start": page * pageSize,
      "length": pageSize
    };
    var successHandler = function(data) {
      model.set({
        "jobExecutions": data.jobExecutions,
        "total": data.total
      });
      model.trigger("render");
    };
    $.get(requestURL, requestData, successHandler, "json");
  }
});


//Job文件参数页面处理方法 组装表格和翻页处理
var jobParamView;
azkaban.JobParamView = Backbone.View.extend({
  events: {
  },

  initialize: function(settings) {
    this.showJobParam();
    //this.assemblyJobParamTable()
  },

  showJobParam: function () {

    // this.projectName = projectName;
    // this.flowName = flowId;
    // this.jobName = jobName;

    var projectURL = this.projectURL
    var model = this.model;
    var getJobParamDataQuery = {
      "ajax": "getJobParamData",
      "project": projectName,
      "flowName": flowId,
      "jobName": jobName
    };
    var mythis = this;
    var getJobParamDataSuccessHandler = function (data) {
      if (data.error) {
        alert(data.error);
        return;
      }
      var jobParamData = data.jobParamData;
      // model.set({
      //   "jobParamData": data.jobParamData,
      // });
      mythis.assemblyJobParamTable(jobParamData)
    };

    $.get(projectURL, getJobParamDataQuery, getJobParamDataSuccessHandler, "json");

  },

  assemblyJobParamTable: function(jobParamData) {
    console.log("assemblyJobParamTable");
    // Render page selections
    var tbody = $("#job-param-tbody");
    tbody.empty();
    //var jobParamData = jobParamData;
    //var jobParamData = this.model.get("jobParamData");

    for (var i = 0; i < jobParamData.length; ++i) {
      var row = document.createElement("tr");

      //组装执行参数名行
      var tdJobName = document.createElement("td");
      $(tdJobName).text(jobParamData[i].paramName);
      row.appendChild(tdJobName);

      //组装参数值行
      var tdJobValue = document.createElement("td");
      $(tdJobValue).text(jobParamData[i].paramValue);
      row.appendChild(tdJobValue);

      if(jobParamData[i].paramName == "type" && jobParamData[i].paramValue == "datachecker"){
        $('#job-param-notice').append(jobParamData[i].paramNotice);
      }

      tbody.append(row);
    }

  },


});