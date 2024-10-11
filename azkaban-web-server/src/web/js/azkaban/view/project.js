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

var flowTableView;
azkaban.FlowTableView = Backbone.View.extend({
  events: {
    "click .flow-expander": "expandFlowProject",
    "mouseover .expanded-flow-job-list li": "highlight",
    "mouseout .expanded-flow-job-list li": "unhighlight",
    "click .runJob": "runJob",
    "click .runWithDep": "runWithDep",
    "click .execute-flow": "executeFlow",
    "click .viewFlow": "viewFlow",
    "click .viewJob": "viewJob",
    "click .replenish-collection": "replenishCollection",
    "click .expandarrow": "handleToggleMenuExpand",
    "click .schedule-flow": "scheduleFlow",
    "click .schedule-job": "scheduleJob",
  },

  initialize: function (settings) {
  },
  //项目页面 工作流展示 按钮事件方法
  expandFlowProject: function (evt) {
    if (evt.target.tagName == "A" || evt.target.tagName == "BUTTON") {
      return;
    }

    var target = evt.currentTarget;
    var targetId = target.id;
    var requestURL = contextURL + "/manager";
    //解决jquery不能获取特殊符号ID的问题
    var expanded = document.getElementById(targetId + '-child');
    var tBody = document.getElementById(targetId + '-tbody');
    var targetExpanded = $(expanded);
    var targetTBody = $(tBody);

    var createJobListFunction = this.createJobListTable;
    if (target.loading) {//正在加载
      console.log("Still loading.");
    }
    else if (target.loaded) {//已经加载过了
      $(targetExpanded).collapse('toggle');
      var expander = $(target).children('.flow-expander-icon')[0];
      if ($(expander).hasClass('glyphicon-chevron-down')) {
        $(expander).removeClass('glyphicon-chevron-down');
        $(expander).addClass('glyphicon-chevron-up');
      }
      else {
        $(expander).removeClass('glyphicon-chevron-up');
        $(expander).addClass('glyphicon-chevron-down');
      }
    }
    else {//第一次加载
      // projectName is available
      target.loading = true;
      var requestData = {
        "project": projectName,
        "ajax": "fetchflowjobs",
        "flow": targetId
      };
      var successHandler = function (data) {
        console.log("Success");
        target.loaded = true;
        target.loading = false;
        createJobListFunction(data, targetTBody);
        $(targetExpanded).collapse('show');
        var expander = $(target).children('.flow-expander-icon')[0];
        $(expander).removeClass('glyphicon-chevron-down');
        $(expander).addClass('glyphicon-chevron-up');
      };
      $.get(requestURL, requestData, successHandler, "json");
    }
  },
  //构建项目Job列表的方法
  createJobListTable: function (data, innerTable) {
    var nodes = data.nodes;
    var flowId = data.flowId;
    var project = data.project;
    var requestURL = contextURL + "/manager?project=" + project + "&flow="
        + flowId + "&job=";
    if(nodes){
      renderTree($(innerTable), data);
    }

  },

  handleToggleMenuExpand: function(evt) {
    var expandarrow = evt.currentTarget;
    var li = $(evt.currentTarget).closest("li.listElement");
    var submenu = $(li).find("> ul");

    if ($(submenu).is(":visible")) {
      this.handleMenuCollapse(li);
    }
    else {
      this.handleMenuExpand(li);
    }

    evt.stopImmediatePropagation();
  },

  handleMenuCollapse: function(li) {
    var expandArrow = $(li).find("> td > .expandarrow").context;
    var submenu = $(li).find("> ul");

    $(expandArrow).removeClass("expandarrow glyphicon glyphicon-chevron-up");
    $(expandArrow).addClass("expandarrow glyphicon glyphicon-chevron-down");
    $(submenu).slideUp();
  },

  handleMenuExpand: function(li) {
    var expandArrow = $(li).find("> td > .expandarrow").context;
    var submenu = $(li).find("> ul");

    $(expandArrow).removeClass("expandarrow glyphicon glyphicon-chevron-down");
    $(expandArrow).addClass("expandarrow glyphicon glyphicon-chevron-up");
    $(submenu).slideDown();
  },

  unhighlight: function (evt) {
    var currentTarget = evt.currentTarget;
    $(".dependent").removeClass("dependent");
    $(".dependency").removeClass("dependency");
  },

  highlight: function (evt) {
    var currentTarget = evt.currentTarget;
    $(".dependent").removeClass("dependent");
    $(".dependency").removeClass("dependency");
    this.highlightJob(currentTarget);
  },

  highlightJob: function (currentTarget) {
    var dependents = currentTarget.dependents;
    var dependencies = currentTarget.dependencies;
    var flowid = currentTarget.flowId;

    if (dependents) {
      for (var i = 0; i < dependents.length; ++i) {
        var depId = flowid + "-" + dependents[i];
        $("#" + depId).toggleClass("dependent");
      }
    }

    if (dependencies) {
      for (var i = 0; i < dependencies.length; ++i) {
        var depId = flowid + "-" + dependencies[i];
        $("#" + depId).toggleClass("dependency");
      }
    }
  },

  viewFlow: function (evt) {
    console.log("View Flow");
    var flowId = evt.currentTarget.flowId;
    location.href = contextURL + "/manager?project=" + projectName + "&flow="
        + flowId;
  },

  viewJob: function (evt) {
    console.log("View Job");
    var flowId = evt.currentTarget.flowId;
    var jobId = evt.currentTarget.jobId;
    location.href = contextURL + "/manager?project=" + projectName + "&flow="
        + flowId + "&job=" + jobId;
  },


  // 进入单个项目, 展开子工作流, 运行任务
  runJob: function (evt) {

    // 需要校验是否具有执行工作流权限 1:允许, 2:不允许
    var requestURL = contextURL + "/manager?ajax=checkUserExecuteFlowPermission&project=" + projectName;
    $.ajax({
      url: requestURL,
      type: "get",
      async: false,
      dataType: "json",
      success: function(data){
        if(data["executeFlowFlag"] == 1){
          console.log("Run Job");
          var jobId = evt.currentTarget.jobName;
          var flowId = evt.currentTarget.flowId;

          var executingData = {
            project: projectName,
            ajax: "executeFlow",
            flow: flowId,
            job: jobId,
            executeFlowTitle:data["executeFlowTitle"]
          };

          flowTableView.executeFlowDialog(executingData);
        }else if(data["executeFlowFlag"] == 2){
          $('#user-temp-operator-permit-panel').modal();
          $('#title-user-temp-operator-permit').text(data["executePermission"]);
          $('#body-user-temp-operator-permit').html(data["noexecuteFlowPermission"]);
        }
      }
    });
  },

  // 进入单个项目, 展开子工作流, 依赖运行
  runWithDep: function (evt) {

    // 需要校验是否具有执行工作流权限 1:允许, 2:不允许
    var requestURL = contextURL + "/manager?ajax=checkUserExecuteFlowPermission&project=" + projectName;
    $.ajax({
      url: requestURL,
      type: "get",
      async: false,
      dataType: "json",
      success: function(data){
        if(data["executeFlowFlag"] == 1){
          var jobId = evt.currentTarget.jobName;
          var flowId = evt.currentTarget.flowId;
          console.log("Run With Dep");

          var executingData = {
            project: projectName,
            ajax: "executeFlow",
            flow: flowId,
            job: jobId,
            withDep: true,
            executeFlowTitle:data["executeFlowTitle"]
          };
          flowTableView.executeFlowDialog(executingData);
        }else if(data["executeFlowFlag"] == 2){
          $('#user-temp-operator-permit-panel').modal();
          $('#title-user-temp-operator-permit').text(data["executePermission"]);
          $('#body-user-temp-operator-permit').html(data["noexecuteFlowPermission"]);
        }
      }
    });
  },

  // 进入单个项目, 不展开子工作流, 执行工作流
  executeFlow: function (evt) {
    // 需要校验是否具有执行工作流权限 1:允许, 2:不允许
    var requestURL = contextURL + "/manager?ajax=checkUserExecuteFlowPermission&project=" + projectName;
    $.ajax({
      url: requestURL,
      type: "get",
      async: false,
      dataType: "json",
      success: function(data){
        if(data["executeFlowFlag"] == 1){
          console.log("temp execute flow");
          var flowId = $(evt.currentTarget).attr('flowid');

          var executingData = {
            project: projectName,
            ajax: "executeFlow",
            flow: flowId,
            executeFlowTitle:data["executeFlowTitle"]
          };

          flowTableView.executeFlowDialog(executingData);
        }else if(data["executeFlowFlag"] == 2){
          $('#user-temp-operator-permit-panel').modal();
          $('#title-user-temp-operator-permit').text(data["executePermission"]);
          $('#body-user-temp-operator-permit').html(data["noexecuteFlowPermission"]);
        }
      }
    });
  },

  executeFlowDialog: function (executingData) {
    flowExecuteDialogView.show(executingData);
  },

  render: function() {
  },

  replenishCollection: function(evt) {
    console.log("Repeat Collection");
    var flowId = $(evt.currentTarget).attr('flowid');

    var executingData = {
      project: projectName,
      ajax: "executeFlow",
      flow: flowId
    };

    repeatCollectionDialogView.show(executingData);
  },

  scheduleFlow: function (evt) {
    // 发请求获取scheduleId
    var cronExpression = "";
    var scheduleId = "";
    var tempFlowId = $(evt.currentTarget).attr('flowid');
    var requestURLForFetchScheduleId = contextURL + "/manager?ajax=fetchRunningScheduleId&project=" + projectName + "&flow=" + tempFlowId;
    $.ajax({
      url: requestURLForFetchScheduleId,
      type: "get",
      async: false,
      dataType: "json",
      success: function(data){
        if (data.error) {
          console.log(data.error.message);
        } else {
          cronExpression = data.cronExpression;
          scheduleId = data.scheduleId;
        }
      }
    });

    // 进入项目,不展开flow, 直接点击定时调度
    // 需要校验是否具有执行调度权限 1:允许, 2:不允许
    var requestURL = contextURL + "/manager?ajax=checkUserScheduleFlowPermission&project=" + projectName;
    $.ajax({
      url: requestURL,
      type: "get",
      async: false,
      dataType: "json",
      success: function(data){
        if(data["scheduleFlowFlag"] == 1){
          console.log("temp schedule flow");
          var flowId = $(evt.currentTarget).attr('flowid');
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
        }else if(data["scheduleFlowFlag"] == 2){
          $('#user-temp-operator-permit-panel').modal();
          $('#title-user-temp-operator-permit').text(data["schFlowPermission"]);
          $('#body-user-temp-operator-permit').html(data["noSchPermissionsFlow"]);
        }
      }
    });
  },

  scheduleFlowDialog: function (executingData) {
    flowScheduleDialogView.show(executingData);
  },

  scheduleJob: function (evt) {

    // 发请求获取scheduleId
    var cronExpression = "";
    var scheduleId = "";
    var tempFlowId = evt.currentTarget.flowId;
    var requestURLForFetchScheduleId = contextURL + "/manager?ajax=fetchRunningScheduleId&project=" + projectName + "&flow=" + tempFlowId;
    $.ajax({
      url: requestURLForFetchScheduleId,
      type: "get",
      async: false,
      dataType: "json",
      success: function(data){
        if (data.error) {
          console.log(data.error.message);
        } else {
          cronExpression = data.cronExpression;
          scheduleId = data.scheduleId;
        }
      }
    });

    // 展开flow, 子flow的定时调度
    // 需要校验是否具有执行调度权限 1:允许, 2:不允许
    var requestURL = contextURL + "/manager?ajax=checkUserScheduleFlowPermission&project=" + projectName;
    $.ajax({
      url: requestURL,
      type: "get",
      async: false,
      dataType: "json",
      success: function(data){
        if(data["scheduleFlowFlag"] == 1){
          console.log("Schedule Job");
          var jobId = evt.currentTarget.jobName;
          var flowId = evt.currentTarget.flowId;

          var executingData;
          if (scheduleId) {
            executingData = {
              project: projectName,
              flow: flowId,
              job: jobId,
              scheduleId: scheduleId,
              cronExpression: cronExpression,
              scheduleFlowTitle: data["scheduleFlowTitle"]
            };
          } else {
            executingData = {
              project: projectName,
              flow: flowId,
              job: jobId,
              scheduleFlowTitle: data["scheduleFlowTitle"]
            };
          }

          flowTableView.scheduleFlowDialog(executingData);
        }else if(data["scheduleFlowFlag"] == 2){
          $('#user-temp-operator-permit-panel').modal();
          $('#title-user-temp-operator-permit').text(data["schFlowPermission"]);
          $('#body-user-temp-operator-permit').html(data["noSchPermissionsFlow"]);
        }
      }
    });
  },

});

$(function() {
  flowTableView = new azkaban.FlowTableView({el:$('#flow-tabs')});

  //默认展开第一级目录, 注释该代码,表示初始化的时候默认是折叠状态
  // $(".flow-expander").each(function (i){
  //   $(this).click();
  // });

  $("#closeFlowList").click(function (i){
    $(".flow-expander").each(function (i){
      $(this).click();
    });
  });


});


var listNodes = {};

function renderTree(el, data, prefix) {
  var nodes = data.nodes;


  if (nodes.length == 0) {
    console.log("No results");
    return;
  };
  if (!prefix) {
    prefix = "";
  }

  var nodeArray = nodes.slice(0);
  // nodeArray.sort(function(a, b) {
  //   var diff = a.y - b.y;
  //   if (diff == 0) {
  //     return a.level - b.level;
  //   }
  //   else {
  //     return diff;
  //   }
  //   return a.level - b.level;
  // });

  var flowId = data.flow;

  var ul = document.createElement('ul');
  $(ul).addClass("tree-list");

  for (var i = 0; i < nodeArray.length; ++i) {

    var job = nodeArray[i];

    var project = job.projectName;

    var requestURL = contextURL + "/manager?project=" + project + "&flow=" + flowId + "&job=";

    var name = nodeArray[i].id;

    var li = document.createElement("li");
    $(li).addClass("listElement");
    $(li).addClass("tree-list-item");
    //$(li).css("margin","10px 0px 10px 10px");
    $(li).css("border-bottom","solid 1px #dddddd");
    $(li).css("position","relative");
    $(li).css("padding","10px 15px");

    // This is used for the filter step.
    var listNodeName = prefix + nodeArray[i].id;
    this.listNodes[listNodeName]=li;
    li.node = nodeArray[i];
    li.node.listElement = li;

    var table = $("<table style='width:100%;'></table>");
    $(li).append(table);
    var tr = $("<tr></tr>");
    table.append(tr);


    if (nodeArray[i].type == "flow") {

      var expandTd = $("<td style='width:17px'></td>");
      tr.append(expandTd);
      // Add the up down
      var expandDiv = document.createElement("div");
      $(expandDiv).addClass("expandarrow glyphicon glyphicon-chevron-down");
      $(expandTd).append(expandDiv);
      // Create subtree
      var subul = this.renderTree(li, nodeArray[i], listNodeName + ":");
      $(subul).hide();
    }


    var urlTd = $("<td></td>");
    urlTd.attr("style","width:80%;");
    tr.append(urlTd);

    if(job.type == "flow"){
      var a = document.createElement("a");
      var iconDiv = document.createElement('div');
      $(iconDiv).addClass('icon');
      $(a).attr("href", contextURL + "/manager?project=" + project + "&flow=" + job.flow + "&job=" + name + "&treeFlow=" + flowId);
      $(a).append(iconDiv);
    }else{
      var a = document.createElement("a");
      var iconDiv = document.createElement('div');
      $(iconDiv).addClass('icon');
      $(a).attr("href", requestURL + name);
      $(a).append(iconDiv);
    }


    var span = document.createElement("span");
    $(span).text(nodeArray[i].id);
    $(span).addClass("jobname");
    $(span).attr("style","word-break:break-all;");
    $(a).append(span);
    $(li).append(a);
    $(ul).append(li);

    urlTd.append($(a));


    li.dependents = job.dependents;
    li.dependencies = job.dependencies;
    li.projectName = project;
    li.jobName = name;

    // if (nodeArray[i].type == "flow") {
    //    name=;
    //    flow=;
    // }

    var languageCategory = langType;
    console.log("languageCategory value is=" + languageCategory);
    var schTag;
    var runJobTag;
    var runFlowTag;
    var relyRunTag;
    var execHistoryTag;

    if (languageCategory == "en_US"){
      schTag = "Schedule";
      runJobTag = "Run Job";
      runFlowTag = "Run Flow";
      relyRunTag = "Rely Run";
      execHistoryTag = "Execute History";
    } else {
      schTag = "定时调度";
      runJobTag = "运行任务";
      runFlowTag = "运行工作流";
      relyRunTag = "依赖运行";
      execHistoryTag = "执行历史";
    }

    var hoverMenuDiv = document.createElement('div');
    $(hoverMenuDiv).addClass('pull-right');
    $(hoverMenuDiv).addClass('job-buttons');

    if (scheduleAccess) {



      var buttonTdSchedule = $("<td></td>");
      var divScheduleJob = document.createElement('button');
      $(divScheduleJob).attr('type', 'button');
      $(divScheduleJob).addClass("btn");
      $(divScheduleJob).addClass("btn-success");
      $(divScheduleJob).addClass("btn-xs");
      $(divScheduleJob).addClass("schedule-job");
      $(divScheduleJob).css("float","right");
      $(divScheduleJob).text(schTag);
      if (job.type == "flow") {
        divScheduleJob.flowId = job.flow;
      } else {
        divScheduleJob.jobName = name;
        divScheduleJob.flowId = data.flow;
      }
      buttonTdSchedule.append(divScheduleJob);
      tr.append(buttonTdSchedule);
    }

    if (execAccess) {
      var buttonTd = $("<td></td>");
      var buttonTd2 = $("<td></td>");
      var buttonTd3 = $("<td></td>");

      if(job.type == "flow"){
        var divRunJob = document.createElement('button');
        $(divRunJob).attr('type', 'button');
        $(divRunJob).addClass("btn");
        $(divRunJob).addClass("btn-success");
        $(divRunJob).addClass("btn-xs");
        $(divRunJob).addClass("execute-flow");
        $(divRunJob).text(runFlowTag);
        $(divRunJob).css("margin","0px 0px 0px 10px");
        $(divRunJob).attr("flowid", job.flow);

        buttonTd.append(divRunJob);
      }else{
        var divRunJob = document.createElement('button');
        $(divRunJob).attr('type', 'button');
        $(divRunJob).addClass("btn");
        $(divRunJob).addClass("btn-success");
        $(divRunJob).addClass("btn-xs");
        $(divRunJob).addClass("runJob");
        $(divRunJob).text(runJobTag);
        $(divRunJob).css("margin","0px 0px 0px 10px");
        divRunJob.jobName = name;
        divRunJob.flowId = data.flow;

        buttonTd.append(divRunJob);
      }
      tr.append(buttonTd);


      var divRunWithDep = document.createElement("button");
      $(divRunWithDep).attr('type', 'button');
      $(divRunWithDep).addClass("btn");
      $(divRunWithDep).addClass("btn-success");
      $(divRunWithDep).addClass("btn-xs");
      $(divRunWithDep).addClass("runWithDep");
      $(divRunWithDep).text(relyRunTag);
      $(divRunWithDep).css("margin","0px 10px 0px 10px");
      divRunWithDep.jobName = name;
      divRunWithDep.flowId = data.flow;
      //$(hoverMenuDiv).append(divRunWithDep);

      buttonTd2.append(divRunWithDep);
      tr.append(buttonTd2);

      // var divJobHistory = document.createElement("button");
      // $(divJobHistory).attr('type', 'button');
      // $(divJobHistory).addClass("btn");
      // $(divJobHistory).addClass("btn-success");
      // $(divJobHistory).addClass("btn-xs");
      //$(divJobHistory).addClass("execute-flow");
      // $(divJobHistory).text("执行历史");
      // $(divJobHistory).css("margin","0px 10px 0px 10px");

      var aJobHistory = document.createElement("a");
      $(aJobHistory).addClass("btn btn-info btn-xs");

      if(job.type == "flow"){
        $(aJobHistory).attr("href", contextURL + "/manager?project=" + project + "&flow=" + job.flow + "#executions");
      }else{
        $(aJobHistory).attr("href", contextURL + "/manager?project=" + project + "&job=" + name + "&history");
      }
      $(aJobHistory).text(execHistoryTag);
      //$(divJobHistory).append(aJobHistory);
      //$(hoverMenuDiv).append(divJobHistory);

      buttonTd3.append(aJobHistory);
      tr.append(buttonTd3);

      //$(buttonTd).append(hoverMenuDiv);
    }

  }

  $(el).append(ul);
  return ul;
}


