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

function ajaxCall(requestURL, data, callback) {
  var successHandler = function (data) {
    if (data.error == "session") {
      // We need to relogin.
      var errorDialog = document.getElementById("invalid-session");
      if (errorDialog) {
        $(errorDialog).modal({
          closeHTML: "<a href='#' title='Close' class='modal-close'>x</a>",
          position: ["20%",],
          containerId: 'confirm-container',
          containerCss: {
            'height': '220px',
            'width': '565px'
          },
          onClose: function (dialog) {
            window.location.reload();
          }
        });
      }
    }
    else {
      callback.call(this, data);
    }
  };
  $.get(requestURL, data, successHandler, "json");
}

function executeFlow(executingData) {
    executeURL = "/executor";
    var isBackupRerun = localStorage.getItem("isBackupRerun")
    if (isBackupRerun === 'true') {
        executeURL = "/executor?ajax=disasterToleranceRetry"
    }else {
        executeURL = "/executor";
    }
    var executingData1=executingData;
  var successHandler = function (data) {
    if (data.error) {
        $("#execute-btn").attr("disabled", false).removeClass("button-disable");
      flowExecuteDialogView.hideExecutionOptionPanel();
      messageDialogView.show(wtssI18n.common.workflowError, data.error);
      } else if (data.info){
        $("#execute-btn").attr("disabled", false).removeClass("button-disable");
        messageDialogView.show(wtssI18n.common.workflowError, data.info);
      } else if (data.warn) {
        $("#execute-btn").attr("disabled", false).removeClass("button-disable");
        const warnHtml = `${data.warn}<span style="color: red;">（请注意不管选择哪个选项版本物料和脚本只会使用新版本的进行执行）</span>`
        warnDialogView.show(wtssI18n.common.workflowSubmit, warnHtml, wtssI18n.common.no, wtssI18n.common.yes,
          function () {
            executingData1.propertiesVersion='new';
            executeFlow(executingData1);
            flowExecuteDialogView.hideExecutionOptionPanel();
          },
          function () {
            executingData1.propertiesVersion='old';
            executeFlow(executingData1);
            flowExecuteDialogView.hideExecutionOptionPanel();
          }
        );
      } else {
      flowExecuteDialogView.hideExecutionOptionPanel();
      messageDialogView.show(wtssI18n.common.workflowSubmit, data.message,
        function() {
            var redirectURL = filterXSS("/executor?execid=" + data.execid);
          window.location.href = redirectURL;
        }
      );
    }
  };

  $.get(executeURL, executingData, successHandler, "json");
}

function fetchFlowInfo(model, projectName, flowId, execId) {
  var fetchData = {"project": projectName, "ajax": "flowInfo", "flow": flowId};
  if (execId) {
    fetchData.execid = execId;
  }

    var executeURL = "/executor";
  var successHandler = function (data) {
    if (data.error) {
      alert(data.error);
    }
    else {
      if(!data.otherOption){
        data.otherOption = {};
      }
      model.set({
          "jobDisabled": data.disabled,
        "successEmails": data.successEmails,
        "successEmailsOverride":data.successEmailsOverride,
        "failureEmails": data.failureEmails,
        "failureEmailsOverride":data.failureEmailsOverride,
        "failureAction": data.failureAction,
          "rerunAction": data.rerunAction,
        "notifyFailure": {
          "first": data.notifyFailureFirst,
          "last": data.notifyFailureLast
        },
        "flowParams": data.flowParam,
        "jobOutputGlobalParam": data.jobOutputGlobalParam,
        "nsWtss": data.nsWtss,
        "isRunning": data.running,
        "nodeStatus": data.nodeStatus,
        "concurrentOption": data.concurrentOptions,
        "pipelineLevel": data.pipelineLevel,
        "pipelineExecution": data.pipelineExecution,
        "queueLevel": data.queueLevel,
        "slaEmails":data.slaEmails,
        "failureAlertLevel": data.failureAlertLevel,
        "successAlertLevel":data.successAlertLevel,

        "useTimeoutSetting": data.useTimeoutSetting,

        "ruleType":data.ruleType,
        "duration":data.duration,
        "emailAction":data.emailAction,
        "killAction":data.killAction,
        "slaAlertLevel":data.slaAlertLevel,
        "slaAlertType":data.slaAlertType,

        "jobFailedRetryOptions":data.jobFailedRetryOptions,
          "jobSkipFailedOptions": data.jobSkipFailedOptions,
          "jobSkipActionOptions": data.otherOption.jobSkipActionOptions,
  
          "flowRetryAlertOption": data.otherOption.flowRetryAlertOption,
          "flowType": data.flowType,
          "enabledCacheProjectFiles": data.enabledCacheProjectFiles,
      });
    }
    model.trigger("change:flowinfo");
  };

  $.ajax({
    url: executeURL,
    data: fetchData,
    success: successHandler,
    dataType: "json",
    async: false
  });
}

function fetchFlow(model, projectName, flowId, sync) {
  // Just in case people don't set sync
  sync = sync ? true : false;
    var managerUrl = "/manager";
  var fetchData = {
    "ajax": "fetchflowgraph",
    "project": projectName,
    "flow": flowId
  };
  var successHandler = function (data) {
    if (data.error) {
      alert(data.error);
    }
    else {
      var disabled = data.disabled ? data.disabled : {};
      model.set({
        flowId: data.flowId,
        data: data,
        disabled: disabled
      });

      var nodeMap = {};
      for (var i = 0; i < data.nodes.length; ++i) {
        var node = data.nodes[i];
        nodeMap[node.id] = node;
      }

      for (var i = 0; i < data.edges.length; ++i) {
        var edge = data.edges[i];

        if (!nodeMap[edge.target].in) {
          nodeMap[edge.target].in = {};
        }
        var targetInMap = nodeMap[edge.target].in;
        targetInMap[edge.from] = nodeMap[edge.from];

        if (!nodeMap[edge.from].out) {
          nodeMap[edge.from].out = {};
        }
        var sourceOutMap = nodeMap[edge.from].out;
        sourceOutMap[edge.target] = nodeMap[edge.target];
      }

      model.set({nodeMap: nodeMap});
    }
  };

  $.ajax({
    url: managerUrl,
    data: fetchData,
    success: successHandler,
    dataType: "json",
    async: !sync
  });
}

/**
 * Checks to see if a flow is running.
 *
 */
function flowExecutingStatus(projectName, flowId) {
    var requestURL = "/executor";

  var executionIds;
  var successHandler = function (data) {
    if (data.error == "session") {
      // We need to relogin.
      var errorDialog = document.getElementById("invalid-session");
      if (errorDialog) {
        $(errorDialog).modal({
          closeHTML: "<a href='#' title='Close' class='modal-close'>x</a>",
          position: ["20%",],
          containerId: 'confirm-container',
          containerCss: {
            'height': '220px',
            'width': '565px'
          },
          onClose: function (dialog) {
            window.location.reload();
          }
        });
      }
    }
    else {
      executionIds = data.execIds;
    }
  };
  $.ajax({
    url: requestURL,
    async: false,
    data: {
      "ajax": "getRunning",
      "project": projectName,
      "flow": flowId
    },
    error: function (data) {
    },
    success: successHandler
  });

  return executionIds;
}
