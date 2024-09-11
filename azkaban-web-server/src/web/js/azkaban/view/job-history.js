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

var jobHistoryView;

var jobDataModel;
azkaban.JobDataModel = Backbone.Model.extend({});

var jobTimeGraphView;


$(function () {

  jobDataModel = new azkaban.JobDataModel();
  jobHistoryView = new azkaban.JobHistoryView({
    el: $('#jobHistoryView'),
    model: jobDataModel
  });

  //var selected;
  // var series = dataSeries;
  //
  // jobDataModel.set({
  //   "data": series
  // });
  //jobDataModel.trigger('render');

  jobTimeGraphView = new azkaban.TimeGraphView({
    el: $('#timeGraph'),
    model: jobDataModel,
    modelField: "jobPageList"
  });


  var hash = window.location.hash;
  if ("#page" == hash.substring(0, "#page".length)) {
    var arr = hash.split("#");
    var page = arr[1].substring("#page".length-1, arr[1].length);
    var pageSize = arr[2].substring("#pageSize".length-1, arr[2].length);

    $("#pageSizeSelect").val(pageSize);

    console.log("page " + page);
    jobDataModel.set({
      "page": parseInt(page),
      "pageSize": parseInt(pageSize),
    });
    jobDataModel.trigger("change:view");
  }else{
    var pageNum = $("#pageSizeSelect").val();
    jobDataModel.set({"page": 1, pageSize: pageNum});
    jobDataModel.trigger("change:view");
  }


});

azkaban.JobHistoryView = Backbone.View.extend({
  events: {
    "click #pageSelection li": "handleChangePageSelection",
    "change #pageSizeSelect": "handleJobPageSizeSelection",
    "click #pageNumJump": "handleJobPageNumJump",
  },

  initialize: function (settings) {
    this.model.bind('change:view', this.handleChangeView, this);
    this.model.bind('render', this.render, this);
    var pageNum = $("#pageSizeSelect").val();
    this.model.set({page: 1, pageSize: pageNum});
    this.model.bind('change:page', this.handlePageChange, this);
  },
  //组装数据表格
  render: function (evt) {
    console.log("render");
    // Render page selections

    var tbody = $("#jobHistoryTableBody");
    tbody.empty();

    var jobPageList = this.model.get("jobPageList");
    for (var i = 0; i < jobPageList.length; ++i) {
      var row = document.createElement("tr");

      //组装执行ID
      var tdId = document.createElement("td");
      var execA = document.createElement("a");
      $(execA).attr("href", contextURL + "/executor?execid=" + jobPageList[i].execId);
      $(execA).text(jobPageList[i].execId);
      tdId.appendChild(execA);
      row.appendChild(tdId);

      //组装任务名
      var tdUser = document.createElement("td");
      var jobA = document.createElement("a");
      $(jobA).attr("href", contextURL + "/manager?project=" + projectName + "&flow="
          + jobPageList[i].flowId.split(":").slice(-1)[0] + "&job=" + jobPageList[i].jobId);
      $(jobA).text(jobPageList[i].jobId);
      $(jobA).attr("style", "width: 350px; word-break:break-all;");
      tdUser.appendChild(jobA);
      row.appendChild(tdUser);

      //组装工作流
      var tdFlow = document.createElement("td");
      var flowA = document.createElement("a");
      var flowName = jobPageList[i].flowId.split(":").slice(-1);
      $(flowA).attr("href", contextURL + "/manager?project=" + projectName + "&flow=" + flowName);
      $(flowA).text(jobPageList[i].flowId);
      $(flowA).attr("style", "width: 350px; word-break:break-all;");
      tdFlow.appendChild(flowA);
      row.appendChild(tdFlow);

      //组装开始时间
      var startTime = "-";
      if (jobPageList[i].startTime != -1) {
        var startDateTime = new Date(jobPageList[i].startTime);
        startTime = getDateFormat(startDateTime);
      }
      var tdStartTime = document.createElement("td");
      $(tdStartTime).text(startTime);
      row.appendChild(tdStartTime);

      //组装结束时间
      var endTime = "-";
      var lastTime = jobPageList[i].endTime;
      if (jobPageList[i].endTime != -1 && jobPageList[i].endTime != 0) {
        var endDateTime = new Date(jobPageList[i].endTime);
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
      $(tdRunDate).text(jobPageList[i].runDate);
      row.appendChild(tdRunDate);

      //组装执行时长
      var tdElapsed = document.createElement("td");
      $(tdElapsed).text(getDuration(jobPageList[i].startTime, lastTime));
      row.appendChild(tdElapsed);

      //组装执行状态
      var tdStatus = document.createElement("td");
      var status = document.createElement("div");
      $(status).addClass("status");
      $(status).addClass(jobPageList[i].status);
      $(status).text(statusStringMap[jobPageList[i].status]);
      tdStatus.appendChild(status);
      row.appendChild(tdStatus);

      //日志行
      var tdAction = document.createElement("td");
      $(tdAction).addClass("logLink");
      var logA = document.createElement("a");
      var jobPath = jobPageList[i].flowId.split(",").slice(1).map(function(a){return a.split(":")[0];});
      jobPath.push(jobPageList[i].jobId);
      jobPath = jobPath.join(":");
      $(logA).attr("href", contextURL + "/executor?execid="
          + jobPageList[i].execId +"&job=" + jobPath + "&attempt=" + jobPageList[i].attempt);
      $(logA).text(wtssI18n.view.log);
      tdAction.appendChild(logA);
      row.appendChild(tdAction);

      tbody.append(row);
    }

    this.renderPagination(evt);
  },

  renderPagination: function (evt) {
    var total = this.model.get("total");
    total = total ? total : 1;
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

    $("#page" + selectionPosition).addClass("active");
    $("#page" + selectionPosition)[0].page = page;
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

  handleChangePageSelection: function (evt) {
    if ($(evt.currentTarget).hasClass("disabled")) {
      return;
    }
    var page = evt.currentTarget.page;
    this.model.set({"page": page});
    var pageSize = $("#pageSizeSelect").val();
    this.model.set({"pageSize": pageSize});
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
    var page = this.model.get("page");
    var pageSize = this.model.get("pageSize");
    requestURL = contextURL + "/manager";

    var model = this.model;
    var requestData = {
      "project": projectName,
      "jobId": jobName,
      "ajax": "fetchJobHistoryPage",
      "page": page,
      "size": pageSize,
    };
    var successHandler = function (data) {
      model.set({
        "jobPageList": data.jobPageList,
        "total": data.total,
      });
      model.trigger("render");
    };
    $.get(requestURL, requestData, successHandler, "json");
  },

  handleJobPageSizeSelection: function(evt) {
    var pageSize = evt.currentTarget.value;
    this.model.set({"pageSize": pageSize});
    this.model.set({"page": 1});

    this.init = false;

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
    jobDataModel.trigger("change:view");
  },

  handleJobPageNumJump: function (evt) {

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
    jobDataModel.trigger("change:view");
  },


});

