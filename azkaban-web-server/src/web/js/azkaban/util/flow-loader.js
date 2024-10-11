/*
 * Copyright 2014 LinkedIn Corp.
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

var extendedViewPanels = {};
var extendedDataModels = {};
var openJobDisplayCallback = function (nodeId, flowId, evt) {
  console.log("Open up data");

  /*
  $("#flowInfoBase").before(cloneStuff);
  var requestURL = contextURL + "/manager";

  $.get(
      requestURL,
      {"project": projectName, "ajax":"fetchflownodedata", "flow":flowId, "node": nodeId},
      function(data) {
      var graphModel = new azkaban.GraphModel();
      graphModel.set({id: data.id, flow: data.flowData, type: data.type, props: data.props});

      var flowData = data.flowData;
      if (flowData) {
        createModelFromAjaxCall(flowData, graphModel);
      }

      var backboneView = new azkaban.FlowExtendedViewPanel({el:cloneStuff, model: graphModel});
      extendedViewPanels[nodeInfoPanelID] = backboneView;
      extendedDataModels[nodeInfoPanelID] = graphModel;
      backboneView.showExtendedView(evt);
      },
      "json"
    );
    */
}

var createNewPanel = function (node, model, evt) {
  var parentPath = node.parentPath;

  var nodeInfoPanelID = parentPath ? parentPath + ":" + node.id + "-info"
    : node.id + "-info";
  var cloneStuff = $("#flowInfoBase").clone();
  cloneStuff.data = node;
  $(cloneStuff).attr("id", nodeInfoPanelID);
  $("#flowInfoBase").before(cloneStuff);

  var backboneView = new azkaban.FlowExtendedViewPanel(
    { el: cloneStuff, model: model });
  node.panel = backboneView;
  backboneView.showExtendedView(evt);
}

var closeAllSubDisplays = function () {
  $(".flowExtendedView").hide();
}


var nodeClickCallback = function (event, model, node) {
  console.log("Node clicked callback");

  //获取节点最新信息
  // http://webserverip:port/executor?execid=238653&ajax=fetchexecflow&nodeNestedId=
  var newAttempt = 0;
  if (model.attributes.data.execid) {
    $.ajax({
      async: false,
      url: contextURL + "/executor",
      dataType: "json",
      type: "GET",
      data: {
        execid: model.attributes.data.execid,
        ajax: "fetchexecflow",
        nodeNestedId: node.nestedId,
      },
      success: function (data) {
        newAttempt = data.attempt ? data.attempt : 0;
      }
    });
  }

  var target = event.currentTarget;
  var type = node.type;
  var flowId = node.parent.flow;
  var jobId = node.id;
  var executorId = node.parent.execid;

  var requestURL = contextURL + "/manager?project=" + projectName + "&flow="
    + flowId + "&job=" + jobId;
  var menu = [];

  var jobHistoryURL = contextURL + "/manager?project=" + projectName + "&job=" + node.id + "&history";

  var jobLogURL = contextURL + "/executor?execid=" + executorId + "&job=" + node.nestedId + "&attempt=" + newAttempt;
  if (type == "flow") {
    //打开工作流,需要显示当前执行结果,需要execid,同时也需要当前节点
    var flowRequestURL = contextURL + "/manager?project=" + projectName
      + "&flow=" + node.flowId;
    //如果是执行记录,则显示执行状态,如果是工作流,则显示工作流
    if (executorId) {
      flowRequestURL = contextURL + "/executor?execid=" + executorId
        + "&nodeNestedId=" + node.nestedId;
    }
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
        }
      ];
    }

    $.merge(menu, [
      //  {title: "View Properties...", callback: function() {openJobDisplayCallback(jobId, flowId, event)}},
      { break: 1 },
      {
        title: wtssI18n.common.openFlow, callback: function () {
          window.location.href = flowRequestURL;
        }
      },
      {
        title: wtssI18n.common.openNewWindow, callback: function () {
          window.open(flowRequestURL);
        }
      },
      { break: 1 },
      {
        title: wtssI18n.common.workflowProperties, callback: function () {
          window.location.href = requestURL;
        }
      },
      {
        title: wtssI18n.common.openNewProperties, callback: function () {
          window.open(requestURL);
        }
      },
      { break: 1 },
      {
        title: wtssI18n.common.centerFlow, callback: function () {
          model.trigger("centerNode", node);
        }
      }
    ]);
    if (executorId && isRunning(node.status) && !isFinished(model.attributes.data.status)) {
      menu.splice(-1, 0,
        {
          title: wtssI18n.common.skipExecution, callback: function () {
            console.log("disabled job id: " + node.id);
            var requestURL = contextURL + "/executor?execid=" + execId + "&ajax=ajaxDisableJob";
            var requestData = { "disableJob": node.nestedId };
            var successHandler = function (data) {
              if (data.error) {
                showDialog("Error", data.error);
              } else {
                var prompt = "关闭执行job:" + node.nestedId + ", 成功。"
                if (langType === "en_US") {
                  prompt = "Close The Execution Job " + node.nestedId + " Successfully."
                }
                showDialog(wtssI18n.common.taskSetting, prompt);
                setTimeout(function () {
                  updateStatus();
                }, 1100);
              }
            }
            $.ajax({
              url: requestURL,
              type: "POST",
              data: JSON.stringify(requestData),
              dataType: "json",
              contentType: "application/json; charset=utf-8",
              error: function (data) {
                console.log(data);
              },
              success: successHandler
            });
          }
        });
    }
  }
  else {
    menu = [
      //  {title: "View Properties...", callback: function() {openJobDisplayCallback(jobId, flowId, event)}},
      //  {break: 1},
      {
        title: wtssI18n.common.openJob, callback: function () {
          window.location.href = requestURL;
        }
      },
      {
        title: wtssI18n.common.openNewJob, callback: function () {
          window.open(requestURL);
        }
      },
      { break: 1 },
      {
        title: wtssI18n.common.centerJob, callback: function () {
          model.trigger("centerNode", node)
        }
      },
      { break: 1 },
      { title: wtssI18n.common.executiveHistory, callback: function () { window.open(jobHistoryURL); } }
    ];
    if (executorId) {
      menu.push({ title: wtssI18n.common.taskLog, callback: function () { window.open(jobLogURL); } });
    }
    // flow运行是可以disabled为运行的job
    if (executorId && isRunning(node.status) && !isFinished(model.attributes.data.status)) {
      menu.splice(4, 0, {
        title: wtssI18n.common.skipExecution, callback: function () {
          console.log("disabled job id: " + node.id);
          var requestURL = contextURL + "/executor?execid=" + execId + "&ajax=ajaxDisableJob";
          var requestData = { "disableJob": node.nestedId };
          var successHandler = function (data) {
            if (data.error) {
              showDialog("Error", data.error);
            } else {
              var prompt = "关闭执行job:" + node.nestedId + ", 成功。"
              if (langType === "en_US") {
                prompt = "Close The Execution Job " + node.nestedId + " Successfully."
              }
              showDialog(wtssI18n.common.taskSetting, prompt);
              setTimeout(function () {
                updateStatus();
              }, 1100);
            }
          }
          $.ajax({
            url: requestURL,
            type: "POST",
            data: JSON.stringify(requestData),
            dataType: "json",
            contentType: "application/json; charset=utf-8",
            error: function (data) {
              console.log(data);
            },
            success: successHandler
          });
        }
      });
    }

    // 执行策略是失败暂停 job状态是FAILED_WAITING
    if (model.attributes.data.executionStrategy == "FAILED_PAUSE" && node.status == "FAILED_WAITING") {
      menu.push({
        title: wtssI18n.common.skipCurrentJob, callback: function () {
          console.log("job id: " + node.id);
          var requestURL = contextURL + "/executor?execid=" + execId + "&ajax=ajaxSkipFailedJobs";
          var requestData = { "skipFailedJobs": [node.nestedId] };
          var successHandler = function (data) {
            if (data.error) {
              showDialog("Error", data.error);
            } else {
              var prompt = "设置跳过执行job:" + node.nestedId + ", 成功。"
              if (langType === "en_US") {
                prompt = "Set Skip To Execute Job " + node.nestedId + " Successfully."
              }
              showDialog(wtssI18n.common.taskFailureSettings, prompt);
              setTimeout(function () {
                updateStatus();
              }, 1100);
            }
          }
          $.ajax({
            url: requestURL,
            type: "POST",
            data: JSON.stringify(requestData),
            dataType: "json",
            contentType: "application/json; charset=utf-8",
            error: function (data) {
              console.log(data);
            },
            success: successHandler
          });
        }
      });
      menu.push({
        title: wtssI18n.common.retryCurrentJob, callback: function () {
          console.log("job id path: " + node.nestedId);
          var requestURL = contextURL + "/executor?execid=" + execId + "&ajax=ajaxRetryFailedJobs";
          var requestData = { "retryFailedJobs": [node.nestedId] };
          var successHandler = function (data) {
            if (data.error) {
              showDialog("Error", data.error);
            } else {
              var prompt = "设置重试job:" + node.nestedId + ", 成功。"
              if (langType === "en_US") {
                prompt = "Retry The Job " + node.nestedId + " Successfully."
              }
              showDialog(wtssI18n.common.taskFailureSettings, prompt);
              setTimeout(function () {
                updateStatus();
              }, 1100);
            }
          }
          $.ajax({
            url: requestURL,
            type: "POST",
            contentType: "application/json; charset=utf-8",
            data: JSON.stringify(requestData),
            dataType: "json",
            error: function (data) {
              console.log(data);
            },
            success: successHandler
          });
        }
      });
    }
  }
  contextMenuView.show(event, menu);
}

var jobClickCallback = function (event, model, node) {
  console.log("Node clicked callback");
  var target = event.currentTarget;
  var type = node.type;
  var flowId = node.parent.flow;
  var jobId = node.id;
  var executorId = node.parent.execid;

  var requestURL = contextURL + "/manager?project=" + projectName + "&flow="
    + flowId + "&job=" + node.id;
  var flowHistoryURL = contextURL + "/manager?project=" + projectName + "&flow=" + flowId + "#executions";
  var jobHistoryURL = contextURL + "/manager?project=" + projectName + "&job=" + node.id + "&history";
  var jobLogURL = contextURL + "/executor?execid=" + executorId + "&job=" + node.nestedId;
  if (node.attempt) {
    jobLogURL += "&attempt=" + node.attempt;
  }
  var menu;
  if (type == "flow") {
    //打开工作流,需要显示当前执行结果,需要execid,同时也需要当前节点
    var flowRequestURL = contextURL + "/manager?project=" + projectName
      + "&flow=" + node.flowId;
    //如果是执行记录,则显示执行状态,如果是工作流,则显示工作流
    if (executorId) {
      flowRequestURL = contextURL + "/executor?execid=" + executorId
        + "&nodeNestedId=" + node.nestedId;
    }
    menu = [
      //  {title: "View Properties...", callback: function() {openJobDisplayCallback(jobId, flowId, event)}},
      //  {break: 1},
      {
        title: wtssI18n.common.openFlow, callback: function () {
          window.location.href = flowRequestURL;
        }
      },
      {
        title: wtssI18n.common.openNewWindow, callback: function () {
          window.open(flowRequestURL);
        }
      },
      { break: 1 },
      {
        title: wtssI18n.common.workflowProperties, callback: function () {
          window.location.href = requestURL;
        }
      },
      {
        title: wtssI18n.common.openNewProperties, callback: function () {
          window.open(requestURL);
        }
      },
      { break: 1 },
      {
        title: wtssI18n.common.centerFlow, callback: function () {
          model.trigger("centerNode", node)
        }
      }
    ];
  }
  else {
    menu = [
      //  {title: "View Job...", callback: function() {openJobDisplayCallback(jobId, flowId, event)}},
      //  {break: 1},
      {
        title: wtssI18n.common.openJob, callback: function () {
          window.location.href = requestURL;
        }
      },
      {
        title: wtssI18n.common.openNewJob, callback: function () {
          window.open(requestURL);
        }
      },
      { break: 1 },
      {
        title: wtssI18n.common.centerJob, callback: function () {
          graphModel.trigger("centerNode", node)
        }
      },
      { break: 1 },
      { title: wtssI18n.common.executiveHistory, callback: function () { window.open(jobHistoryURL); } }
    ];
    if (executorId) {
      menu.push({ title: wtssI18n.common.taskLog, callback: function () { window.open(jobLogURL); } });
    }
  }
  contextMenuView.show(event, menu);
}

var edgeClickCallback = function (event, model) {
  console.log("Edge clicked callback");
}

var graphClickCallback = function (event, model) {
  console.log("Graph clicked callback");
  var data = model.get("data");
  var flowId = data.flow;
  var requestURL = contextURL + "/manager?project=" + projectName + "&flow="
    + flowId;

  var menu = [
    {
      title: wtssI18n.common.expandAllWorkflow, callback: function () {
        model.trigger("expandAllFlows");
        model.trigger("resetPanZoom");
      }
    },
    {
      title: wtssI18n.common.collapseAllWorkflow, callback: function () {
        model.trigger("collapseAllFlows");
        model.trigger("resetPanZoom");
      }
    },
    { break: 1 },
    {
      title: wtssI18n.common.openFlow, callback: function () {
        window.location.href = requestURL;
      }
    },
    {
      title: wtssI18n.common.openNewWindow, callback: function () {
        window.open(requestURL);
      }
    },
    { break: 1 },
    {
      title: wtssI18n.common.centerGraph, callback: function () {
        model.trigger("resetPanZoom");
      }
    }
  ];

  contextMenuView.show(event, menu);
}


var nodeDBClickCallback = function (event, model, node) {
  console.log("Node dbclicked callback");

  var target = event.currentTarget;
  var type = node.type;
  var flowId = node.parent.flow;
  var jobId = node.id;
  var executorId = node.parent.execid;

  if (node.flowId) {
    var requestURL = contextURL + "/manager?project=" + projectName + "&flow=" + node.flowId + "&job=" + jobId + "&treeFlow=" + flowId;
    window.open(requestURL);
  } else {
    var requestURL = contextURL + "/manager?project=" + projectName + "&flow=" + flowId + "&job=" + jobId;
    window.open(requestURL);
  }

}
