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

var advFilterView;
azkaban.AdvFilterView = Backbone.View.extend({
  events: {
    "click #filter-btn": "handleAdvFilter"
  },

  initialize: function (settings) {
    $('#datetimebegin').datetimepicker();
    $('#datetimeend').datetimepicker();
    $('#datetimebegin').on('change.dp', function (e) {
      $('#datetimeend').data('DateTimePicker').setStartDate(e.date);
    });
    $('#datetimeend').on('change.dp', function (e) {
      $('#datetimebegin').data('DateTimePicker').setEndDate(e.date);
    });
    $('#adv-filter-error-msg').hide();
    $('.selected').children("a").css("background-color","#c0c1c2");
    $('#status').select2();
  },

  handleAdvFilter: function (evt) {
    console.log("handleAdv");
    var projcontain = $('#projcontain').val();
    var flowcontain = $('#flowcontain').val();
    var execIdcontain = $('#execIdcontain').val();
    var usercontain = $('#usercontain').val();
    var status = $('#status').val();
    var begin = $('#datetimebegin').val();
    var end = $('#datetimeend').val();
    var flowType = $('#flowType').val();

    if(checkProject(projcontain)){
      return;
    };
    if(checkProject(flowcontain)){
      return;
    };
    if(checkExecId(execIdcontain)){
      return;
    };
    if(checkEnglish(usercontain)){
      return;
    };

    // 将所有状态设置为默认
    if(!status){
      status = "0";
      $('#status').val('0');
    };

    console.log("filtering history");

    var historyURL = contextURL + "/history"

    var requestURL = historyURL + "?advfilter=true"
        + "&projcontain=" + projcontain
        + "&flowcontain=" + flowcontain
        + "&execIdcontain=" + execIdcontain
        + "&usercontain=" + usercontain
        + "&status=" + status
        + "&begin=" + begin
        + "&end=" + end
        + "&flowType=" + flowType;
    window.location = requestURL;

  },

  render: function () {
  }
});

// function downloadLog(execId) {
//   executeURL = contextURL + "/executor?execid=" + execId + "&ajax=downloadLog";
//
//   $.ajax({
//     type: "GET",
//     contentType: "application/json",
//     url: executeURL,
//     data: recoverData,
//     dataType: 'json',
//     //success: successHandler,
//     error: function (XMLHttpRequest, textStatus, errorThrown) {
//       //alert('请求后台异常！' + errorThrown);
//     }
//   });
// }

$(function () {

  // 在切换选项卡之前创建模型
  historyModel = new azkaban.HistoryModel();

  var urlSearch = window.location.search;
  if(urlSearch.indexOf("search") != -1){
    historyModel.set({"search": true});
  }else if(urlSearch.indexOf("advfilter") != -1){
    historyModel.set({"advfilter": true});
    var arr = urlSearch.split("&");
    // for(var i=0; i< arr.length; i++){
    //   var filterValue = arr[i].split("=");
    //   var key = filterValue[0];
    //   var value = filterValue[1];
    //   historyModel.set({key: value});
    // }
    var projcontain = arr[1].split("=");
    historyModel.set({projcontain: projcontain[1]});
    var flowcontain = arr[2].split("=");
    historyModel.set({flowcontain: flowcontain[1]});
    var execIdcontain = arr[3].split("=");
    historyModel.set({execIdcontain: execIdcontain[1]});
    var usercontain = arr[4].split("=");
    historyModel.set({usercontain: usercontain[1]});
    var status = arr[5].split("=");
    historyModel.set({status: status[1]});
    var begin = arr[6].split("=");
    historyModel.set({begin: decodeURI(begin[1])});
    var end = arr[7].split("=");
    historyModel.set({end: decodeURI(end[1])});
    var flowType = arr[8].split("=");
    historyModel.set({flowType: flowType[1]});
  }

  historyListView = new azkaban.HistoryListView({
    el: $('#history-view-div'),
    model: historyModel
  });

  $("#quick-serach-btn").click(function(){
    historyModel.set({"searchType": "first"});
    $("#search-form").submit();
  });

  var hash = window.location.hash;
  if ("#page" == hash.substring(0, "#page".length)) {
    var arr = hash.split("#");
    var page;
    //var pageSize;
    if(true == historyModel.get("search") && 1 == historyModel.get("page")){
      page = 1;
    }else{
      page = arr[1].substring("#page".length-1, arr[1].length);
      //pageSize = arr[2].substring("#pageSize".length-1, arr[2].length);
    }
    // var page = arr[1].substring("#page".length-1, arr[1].length);
    var pageSize = arr[2].substring("#pageSize".length-1, arr[2].length);

    $("#pageSizeSelect").val(pageSize);

    console.log("page " + page);
    historyModel.set({
      "page": parseInt(page),
      "pageSize": parseInt(pageSize),
    });
  }else{
    historyModel.set({"page": 1});
  }

  historyModel.trigger("change:view");

  console.log("get User Role");

  var roles;
  $.ajax({
    url: "history?ajax=user_role",
    dataType: "json",
    type: "GET",
    //data: {},
    success: function(data) {
      roles=data.userRoles;
    }
  });

  filterView = new azkaban.AdvFilterView({
    el: $('#adv-filter'),
    model: historyModel
  });

  $('#adv-filter-btn').click(function () {
    $('#adv-filter').modal();
    //用户只有user权限没有admin权限时 隐藏用户查找输入框
    // if($.inArray("user", roles) != -1 && $.inArray("admin", roles) == -1){
    //   $('#usercontain-div').hide();
    // }
  });

});

var tableSorterView;

//用于保存浏览数据，切换页面也能返回之前的浏览进度。
var historyModel;
azkaban.HistoryModel = Backbone.Model.extend({});

//项目列表页面
var historyListView;
azkaban.HistoryListView = Backbone.View.extend({
  events: {
    "click #projectPageSelection li": "handleHistoryChangePageSelection",
    "change #pageSizeSelect": "handleHistoryPageSizeSelection",
    "click #pageNumJump": "handleHistoryPageNumJump",
  },

  initialize: function(settings) {
    this.model.bind('change:view', this.handleChangeView, this);
    this.model.bind('render', this.render, this);
    var pageNum = $("#pageSizeSelect").val();
    this.model.set({page: 1, pageSize: pageNum});
    var searchText = $("#searchtextbox").val();
    if(this.model.get("search")){
      this.model.set({searchterm: searchText});
    }
    if(this.model.get("?advfilter")){
    }
    this.model.bind('change:page', this.handlePageChange, this);
  },

  render: function(evt) {
    console.log("render");
    // Render page selections
    var historyTbody = $("#historyTbody");
    historyTbody.empty();

    var historyList = this.model.get("historyList");
    for (var i = 0; i < historyList.length; ++i) {
      var row = document.createElement("tr");

      //组装数字行
      var tdNum = document.createElement("td");
      $(tdNum).text(i + 1);
      $(tdNum).attr("class","tb-name");
      row.appendChild(tdNum);

      //组装行
      var tdJob = document.createElement("td");
      var jobIdA = document.createElement("a");
      $(jobIdA).attr("href", contextURL + "executor?execid=" + historyList[i].executionId);
      $(jobIdA).text(historyList[i].executionId);
      tdJob.appendChild(jobIdA);
      row.appendChild(tdJob);

      //组装Flow行
      var tdFlow = document.createElement("td");
      var flowA = document.createElement("a");
      $(flowA).attr("href", contextURL + "/manager?project=" + historyList[i].projectName + "&flow=" + historyList[i].flowId);
      $(flowA).text(historyList[i].flowId);
      $(flowA).attr("style","width: 350px; word-break:break-all;");
      tdFlow.appendChild(flowA);
      row.appendChild(tdFlow);

      //组装Project行
      var tdProject = document.createElement("td");
      var projectA = document.createElement("a");
      $(projectA).attr("href", contextURL + "/manager?project=" + historyList[i].projectName);
      $(projectA).text(historyList[i].projectName);
      $(projectA).attr("style","width: 350px; word-break:break-all;");
      tdProject.appendChild(projectA);
      row.appendChild(tdProject);

      //组装用户行
      var tdUser = document.createElement("td");
      $(tdUser).text(historyList[i].submitUser);
      row.appendChild(tdUser);

      //组装开始时间行
      var tdStartTime = document.createElement("td");
      $(tdStartTime).text(historyList[i].startTime);
      row.appendChild(tdStartTime);

      //组装结束时间行
      var tdEndTime = document.createElement("td");
      $(tdEndTime).text(historyList[i].endTime);
      row.appendChild(tdEndTime);

      //组装跑批时间行
      var tdRunDate = document.createElement("td");
      $(tdRunDate).text(historyList[i].runDate);
      row.appendChild(tdRunDate);

      //组装执行时长行
      var tdDifftime = document.createElement("td");
      $(tdDifftime).text(historyList[i].difftime);
      row.appendChild(tdDifftime);

      //组装执行状态行
      var tdStatus = document.createElement("td");
      var status = document.createElement("div");
      $(status).addClass("status");
      $(status).addClass(historyList[i].status);
      //执行状态栏超时变色
      // if(historyList[i].status == "RUNNING" && parseInt(historyList[i].execTime) > parseInt(historyList[i].moyenne)){
      //   $(status).addClass("TIMEOUT");
      // }else{
      //   $(status).addClass(historyList[i].status);
      // }
      $(status).text(statusStringMap[historyList[i].status]);
      tdStatus.appendChild(status);
      row.appendChild(tdStatus);

      //组装操作行
      var tdFlowType = document.createElement("td");
      if(historyList[i].flowType == "0"){
        $(tdFlowType).text(wtssI18n.view.singleExecution);
      } else if(historyList[i].flowType == "2"){
        $(tdFlowType).text(wtssI18n.view.historicalRerun);
      } else if(historyList[i].flowType == "3"){
        $(tdFlowType).text(wtssI18n.view.timedScheduling);
      } else if (historyList[i].flowType == "4") {
        $(tdFlowType).text(wtssI18n.view.cycleExecution);
      }
      row.appendChild(tdFlowType);

      //备注
      var tdComment = document.createElement("td");
      $(tdComment).attr("style", "overflow: hidden;text-overflow: ellipsis;white-space: nowrap;max-width: 60px;");
      $(tdComment).attr("title", historyList[i].comment);
      $(tdComment).text(historyList[i].comment);
      row.appendChild(tdComment);

      historyTbody.append(row);

      this.renderPagination(evt);

      $("#executingJobs").trigger("update");
      $("#executingJobs").trigger("sorton", "");
    }
  },
  //组装分页组件
  renderPagination: function(evt) {
    var total = this.model.get("total");
    total = total? total : 1;
    var pageSize = this.model.get("pageSize");
    var numPages = Math.ceil(total / pageSize);

    this.model.set({"numPages": numPages});
    var page = this.model.get("page");

    //Start it off
    $("#projectPageSelection .active").removeClass("active");

    // Disable if less than 5
    // 页面选择按钮
    console.log("Num pages " + numPages)
    var i = 1;
    for (; i <= numPages && i <= 5; ++i) {
      $("#page" + i).removeClass("disabled");
    }
    for (; i <= 5; ++i) {
      $("#page" + i).addClass("disabled");
    }

    // Disable prev/next if necessary.
    // 上一页按钮
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

  handleHistoryChangePageSelection: function(evt) {
    if ($(evt.currentTarget).hasClass("disabled")) {
      return;
    }
    var page = evt.currentTarget.page;
    this.model.set({"page": page});
    var pageSize = $("#pageSizeSelect").val();
    this.model.set({"pageSize": pageSize});
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
    var requestURL = contextURL + "/history";
    var searchText = this.model.get("searchterm");
    var advfilter = this.model.get("advfilter");

    var model = this.model;
    var requestData;
    if(searchText){
      requestData = {
        "ajax": "feachAllHistoryPage",
        "page": start,
        "size": pageSize,
        "pageNum": this.model.get("page"),
        "searchterm": searchText,
        "search": "true",
      };
    }else if(advfilter){

      var projectNameParm = this.model.get("projcontain");
      var flowNameParm = this.model.get("flowcontain");
      var execIdParm = this.model.get("execIdcontain");
      var userNameParm = this.model.get("usercontain");

      requestData = {
        "ajax": "feachAllHistoryPage",
        "page": start,
        "size": pageSize,
        "pageNum": this.model.get("page"),
        "advfilter": true,
        "projcontain": projectNameParm,
        "flowcontain": flowNameParm,
        "execIdcontain": execIdParm,
        "usercontain": userNameParm,
        "begin": this.model.get("begin"),
        "end": this.model.get("end"),
        "status": this.model.get("status"),
        "flowType": this.model.get("flowType"),
      };
    }else{
      requestData = {
        "ajax": "feachAllHistoryPage",
        "page": start,
        "size": pageSize,
        "pageNum": this.model.get("page"),
        "searchterm": searchText,
      };
    }


    var successHandler = function(data) {
      model.set({
        "historyList": data.historyList,
        "total": data.total
      });
      model.trigger("render");
    };
    $.get(requestURL, requestData, successHandler, "json");
  },

  handleHistoryPageSizeSelection: function(evt) {
    var pageSize = evt.currentTarget.value;
    this.model.set({"pageSize": pageSize});
    this.model.set({"page": 1});

    this.init = false;
    //historyModel.trigger("change:view");


    var search = window.location.search
    var arr = search.split("#");
    var pageURL = arr[0] + "#page1";
    var pageSizeURL = pageURL + "#pageSzie" + pageSize;

    var historyURL = contextURL + "/history"

    var pageSizeFirestURL = historyURL + pageSizeURL;
    //防止XSS DOM攻击,类似:javascript:alert(1);//http://www.qq.com,所以对URL进行正则校验
    var reg = /^(http|ftp|https):\/\/[\w\-_]+(\.[\w\-_]+)+([\w\-\.,@?^=%&:/~\+#]*[\w\-\@?^=%&/~\+#])?/;
    if (reg.test(pageSizeFirestURL)) {
        window.location = pageSizeFirestURL;
    }
    historyModel.trigger("change:view");
  },

  handleHistoryPageNumJump: function (evt) {

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
    historyModel.trigger("change:view");
  },

});

function checkEnglish(str){
  if(str.length != 0){
    var reg = /^[a-zA-Z0-9_]+$/;
    if(!reg.test(str)){
      alert(wtssI18n.view.alphanumericUnderlining);
      return true;
    }
  }
}

function checkProject(str){
  if(str.length != 0){
    var reg = /^[a-zA-Z0-9_-]+$/;
    if(!reg.test(str)){
      alert(wtssI18n.view.alphanumericUnderscoreBar);
      return true;
    }
  }
}

function checkExecId(str){
  if(str.length != 0){
    var reg = /^[0-9]+$/;
    if(!reg.test(str)){
      alert(wtssI18n.view.alphanumeric);
      return true;
    }
  }
}