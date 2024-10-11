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

var handleJobMenuClick = function(action, el, pos) {
    var jobid = el[0].jobid;
    var requestURL = contextURL + "/manager?project=" + projectName + "&flow=" +
        flowName + "&job=" + jobid;
    if (action == "open") {
        window.location.href = requestURL;
    } else if (action == "openwindow") {
        window.open(requestURL);
    }
}

var statusView;
azkaban.StatusView = Backbone.View.extend({
    initialize: function(settings) {
        this.model.bind('change:graph', this.render, this);
        this.model.bind('change:update', this.statusUpdate, this);
    },
    render: function(evt) {
        var data = this.model.get("data");

        var user = data.submitUser;
        $("#submitUser").text(user);

        this.statusUpdate(evt);
    },

    statusUpdate: function(evt) {
        var data = this.model.get("data");

        statusItem = $("#flowStatus");
        for (var j = 0; j < statusList.length; ++j) {
            var status = statusList[j];
            statusItem.removeClass(status);
        }
        $("#flowStatus").addClass(data.status);
        $("#flowStatus").text(data.status);

        var startTime = data.startTime;
        var endTime = data.endTime;

        if (!startTime || startTime == -1) {
            $("#startTime").text("-");
        } else {
            var date = new Date(startTime);
            $("#startTime").text(getDateFormat(date));

            var lastTime = endTime;
            if (endTime == -1) {
                var currentDate = new Date();
                lastTime = currentDate.getTime();
            }

            var durationString = getDuration(startTime, lastTime);
            $("#duration").text(durationString);
        }

        if (!endTime || endTime == -1) {
            $("#endTime").text("-");
        } else {
            var date = new Date(endTime);
            $("#endTime").text(getDateFormat(date));
        }
    }
});

var pausedTipsView;
azkaban.PausedTipsView = Backbone.View.extend({

    initialize: function(settings) {

    },

    render: function(evt) {

    },

    events: {
        "click #paused-flow-btn": "handlePause",
    },

    handlePause: function() {
        var requestURL = contextURL + "/executor";
        var requestData = { "execid": execId, "ajax": "pauseFlow" };
        var successHandler = function(data) {
            console.log("pause clicked");
            if (data.error) {
                showDialog("Error", data.error);
            } else {
                showDialog(wtssI18n.view.timeOut, wtssI18n.view.workflowSuspended);
                setTimeout(function() {
                    updateStatus();
                }, 1100);
            }
        };
        ajaxCall(requestURL, requestData, successHandler);
    }

});

var flowTabView;
azkaban.FlowTabView = Backbone.View.extend({
    events: {
        "click #graphViewLink": "handleGraphLinkClick",
        "click #flowTriggerlistViewLink": "handleFlowTriggerLinkClick",
        "click #jobslistViewLink": "handleJobslistLinkClick",
        "click #flowLogViewLink": "handleLogLinkClick",
        "click #operationParameterLink": "handleOperationParameterLinkClick",
        "click #statsViewLink": "handleStatsLinkClick",
        "click #cancelbtn": "handleCancelClick",
        "click #shutdown-selected-flow-btn": "handleShutdownSelectedFlowClick",
        "click #executebtn": "handleRestartClick",
        "click #pausebtn": "handlePauseClick",
        "click #resumebtn": "handleResumeClick",
        "click #retrybtn": "handleRetryClick",
        "click #skipAllFailedJobBtn": "handleSkipAllFailedJobClick",
        "click #superkillbtn": "handleSuperKillClick",
    },

    initialize: function(settings) {
        $("#cancelbtn").hide();
        $("#executebtn").hide();
        $("#pausebtn").hide();
        $("#resumebtn").hide();
        $("#retrybtn").hide();
        $("#skipAllFailedJobBtn").hide();
        $("#superkillbtn").hide();
        this.model.bind('change:graph', this.handleFlowStatusChange, this);
        this.model.bind('change:update', this.handleFlowStatusChange, this);

        var selectedView = settings.selectedView;
        if (selectedView == "jobslist") {
            this.handleJobslistLinkClick();
        } else {
            this.handleGraphLinkClick();
        }
    },

    render: function() {
        console.log("render graph");
    },

    handleGraphLinkClick: function() {
        $("#jobslistViewLink").removeClass("active");
        $("#graphViewLink").addClass("active");
        $("#flowLogViewLink").removeClass("active");
        $("#flowTriggerlistViewLink").removeClass("active");
        $("#statsViewLink").removeClass("active");
        $("#operationParameterLink").removeClass("active");

        $("#jobListView").hide();
        $("#flowTriggerListView").hide();
        $("#graphView").show();
        $("#flowLogView").hide();
        $("#statsView").hide();
        $("#operationParameterView").hide();
        if (flowLogView) {
            flowLogView.clearRefresh();
        }
    },

    handleFlowTriggerLinkClick: function() {
        $("#jobslistViewLink").removeClass("active");
        $("#graphViewLink").removeClass("active");
        $("#flowLogViewLink").removeClass("active");
        $("#flowTriggerlistViewLink").addClass("active");
        $("#statsViewLink").removeClass("active");
        $("#operationParameterLink").removeClass("active");
        $("#operationParameterView").hide();
        $("#jobListView").hide();
        $("#flowTriggerListView").show();
        $("#graphView").hide();
        $("#flowLogView").hide();
        $("#statsView").hide();
    },

    handleJobslistLinkClick: function() {
        $("#graphViewLink").removeClass("active");
        $("#jobslistViewLink").addClass("active");
        $("#flowLogViewLink").removeClass("active");
        $("#flowTriggerlistViewLink").removeClass("active");
        $("#statsViewLink").removeClass("active");
        $("#operationParameterLink").removeClass("active");
        $("#operationParameterView").hide();
        $("#graphView").hide();
        $("#flowTriggerListView").hide();
        $("#jobListView").show();
        $("#flowLogView").hide();
        $("#statsView").hide();
        if (flowLogView) {
            flowLogView.clearRefresh();
        }
    },


    handleLogLinkClick: function() {
        $("#graphViewLink").removeClass("active");
        $("#flowTriggerlistViewLink").removeClass("active");
        $("#jobslistViewLink").removeClass("active");
        $("#flowLogViewLink").addClass("active");
        $("#statsViewLink").removeClass("active");
        $("#operationParameterLink").removeClass("active");
        $("#operationParameterView").hide();
        $("#graphView").hide();
        $("#flowTriggerListView").hide();
        $("#jobListView").hide();
        $("#flowLogView").show();
        $("#statsView").hide();
        if (flowLogView) {
            flowLogView.autoRefresh();
        }
    },

    handleOperationParameterLinkClick: function() {
        $("#graphViewLink").removeClass("active");
        $("#flowTriggerlistViewLink").removeClass("active");
        $("#jobslistViewLink").removeClass("active");
        $("#flowLogViewLink").removeClass("active");
        $("#statsViewLink").removeClass("active");
        $("#operationParameterLink").addClass("active");
        $("#graphView").hide();
        $("#flowTriggerListView").hide();
        $("#jobListView").hide();
        $("#flowLogView").hide();
        $("#statsView").hide();
        $("#operationParameterView").show();
        if (operationParameterView) {
            operationParameterView.getOperationParameter();
        }
    },

    handleStatsLinkClick: function() {
        $("#graphViewLink").removeClass("active");
        $("#flowTriggerlistViewLink").removeClass("active");
        $("#jobslistViewLink").removeClass("active");
        $("#flowLogViewLink").removeClass("active");
        $("#statsViewLink").addClass("active");

        $("#graphView").hide();
        $("#flowTriggerListView").hide();
        $("#jobListView").hide();
        $("#flowLogView").hide();
        statsView.show();
        $("#statsView").show();
    },

    handleFlowStatusChange: function() {
        var data = this.model.get("data");
        var getHideHead = sessionStorage.getItem('hideHead');
        $("#cancelbtn").hide();
        $("#executebtn").hide();
        $("#pausebtn").hide();
        $("#resumebtn").hide();
        $("#retrybtn").hide();
        $("#skipAllFailedJobBtn").hide();
        $("#superkillbtn").hide();
        if (data.status == "SUCCEEDED" && getHideHead !== 'true') {
            $("#executebtn").show();
        } else if (data.status == "PREPARING") {
            $("#cancelbtn").show();
        } else if (data.status == "FAILED" && getHideHead !== 'true') {
            $("#executebtn").show();
        } else if (data.status == "FAILED_FINISHING" && getHideHead !== 'true') {
            $("#cancelbtn").show();
            $("#executebtn").hide();
            $("#retrybtn").show();
            $("#pausebtn").show();
            if (data.executionStrategy == "FAILED_PAUSE") {
                $("#skipAllFailedJobBtn").show();
            }
        } else if (data.status == "RUNNING") {
            $("#cancelbtn").show();
            $("#pausebtn").show();
        } else if (data.status == "PAUSED") {
            $("#cancelbtn").show();
            $("#resumebtn").show();
        } else if (data.status == "WAITING") {
            $("#cancelbtn").show();
        } else if (data.status == "KILLED" && getHideHead !== 'true') {
            $("#executebtn").show();
        } else if (data.status == "KILLING") {
            $("#superkillbtn").show();
        }
    },

    handleSuperKillClick: function(evt) {

        // 需要校验是否具有修改项目调度权限 1:允许, 2:不允许
        var requestURL = "/manager?ajax=checkKillFlowPermission&project=" + projectName;
        $.ajax({
            url: requestURL,
            type: "get",
            async: false,
            dataType: "json",
            success: function(data) {
                if (data["killFlowFlag"] == 1) {

                    // 弹出对话框
                    $('#superkill-flow-modal').modal();

                    $(document).ready(function() {
                        $("#superkill-selected-flow-btn").click(function() {
                            var requestURL = "/executor";
                            var requestData = { "execid": execId, "ajax": "superKillFlow" };
                            var successHandler = function(data) {
                                if (data.error) {
                                    showDialog("Error", data.error);
                                } else {
                                    $('#superkill-flow-modal').modal().hide();
                                    setTimeout(function() {
                                        updateStatus();
                                    }, 1100);
                                    setTimeout(function() {
                                        window.location.reload();
                                    }, 1000);
                                }
                            };
                            ajaxCall(requestURL, requestData, successHandler);
                        });
                    });

                } else if (data["killFlowFlag"] == 2) {
                    $('#user-retry-execute-flow-permit-panel').modal();
                    $('#title-user-retry-execute-flow-permit').text(data["killExecutePermissions"]);
                    $('#body-user-retry-execute-flow-permit').html(filterXSS(data["killExecutePermissionsDesc"]));
                }
            }
        });
    },
    handleCancelClick: function(evt) {

        // 需要校验是否具有修改项目调度权限 1:允许, 2:不允许
        var requestURL = contextURL + "/manager?ajax=checkKillFlowPermission&project=" + projectName;
        $.ajax({
            url: requestURL,
            type: "get",
            async: false,
            dataType: "json",
            success: function(data) {
                if (data["killFlowFlag"] == 1) {

                    // 弹出对话框
                    $('#shutdown-flow-modal').modal();
                    console.log("flowId=" + flowId);
                    // 截取过长的字符串
                    var trimFlowId = flowId;
                    if (flowId.length > 20) {
                        trimFlowId = flowId.substring(0, 20) + "...";
                    }
                    $('#shutdown-flow-title').text(data["endExecutenProcess"] + trimFlowId);

                    $(document).ready(function() {
                        $("#shutdown-selected-flow-btn").click(function() {
                            var requestURL = contextURL + "/executor";
                            var requestData = { "execid": execId, "ajax": "cancelFlow" };
                            console.log("Click shutdownSelectedFlowClick ");
                            var successHandler = function(data) {
                                console.log("cancel clicked");
                                if (data.error) {
                                    showDialog("Error", data.error);
                                } else {
                                    $('#shutdown-flow-modal').modal().hide();
                                    setTimeout(function() {
                                        updateStatus();
                                    }, 1100);
                                    setTimeout(function() {
                                        window.location.reload();
                                    }, 1000);
                                }
                            };
                            ajaxCall(requestURL, requestData, successHandler);
                        });
                    });

                } else if (data["killFlowFlag"] == 2) {
                    $('#user-retry-execute-flow-permit-panel').modal();
                    $('#title-user-retry-execute-flow-permit').text(data["killExecutePermissions"]);
                    $('#body-user-retry-execute-flow-permit').html(data["killExecutePermissionsDesc"]);
                }
            }
        });
    },

    handleShutdownSelectedFlowClick: function(evt) {
        var requestURL = contextURL + "/executor";
        var requestData = { "execid": execId, "ajax": "cancelFlow" };
        console.log("Click shutdownSelectedFlowClick ");
        var successHandler = function(data) {
            console.log("cancel clicked");
            if (data.error) {
                showDialog("Error", data.error);
            } else {
                $('#shutdown-flow-modal').modal().hide();
                showDialog(wtssI18n.view.cancel, wtssI18n.view.workflowCanceled);
                setTimeout(function() {
                    updateStatus();
                }, 1100);
            }
        };
        ajaxCall(requestURL, requestData, successHandler);
    },

    handleRetryClick: function(evt) {

        // 弹出对话框
        $('#onekey-retry-failed-flow-modal').modal();
        $(document).ready(function() {
            $("#onekey-retry-failed-flow-btn").click(function() {
                var graphData = graphModel.get("data");
                var requestURL = contextURL + "/executor";
                var requestData = { "execid": execId, "ajax": "retryFailedJobs" };
                var successHandler = function(data) {
                    console.log("cancel clicked");
                    if (data.error) {
                        showDialog("Error", data.error);
                    } else {
                        $('#onekey-retry-failed-flow-modal').modal().hide();
                        showDialog(wtssI18n.view.retry, wtssI18n.view.workflowRetried);
                        setTimeout(function() {
                            updateStatus();
                        }, 1100);
                        setTimeout(function() {
                            window.location.reload();
                        }, 1000);
                    }
                };
                ajaxCall(requestURL, requestData, successHandler);
            });
        });
    },

    //一键跳过所有FAILED_WAITING 状态job
    handleSkipAllFailedJobClick: function(evt) {
        // 弹出对话框
        $('#onekey-skip-failed-job-modal').modal();
        $(document).ready(function() {
            $("#onekey-skip-failed-job-btn").click(function() {
                var graphData = graphModel.get("data");
                var requestURL = contextURL + "/executor";
                var requestData = { "execid": execId, "ajax": "skipAllFailedJobs" };
                var successHandler = function(data) {
                    console.log("skip all FAILED_WAITING job.");
                    if (data.error) {
                        showDialog("Error", data.error);
                    } else {
                        $('#onekey-skip-failed-job-modal').modal().hide();
                        showDialog(wtssI18n.view.skipAllFailedJobs, wtssI18n.view.skipAllFailedJobsMsg);
                        setTimeout(function() {
                            updateStatus();
                        }, 1100);
                        setTimeout(function() {
                            window.location.reload();
                        }, 1000);
                    }
                };
                ajaxCall(requestURL, requestData, successHandler);
            });
        });

    },

    // 准备执行
    handleRestartClick: function(evt) {

        // 需要校验是否具有执行工作流权限 1:允许, 2:不允许
        var requestURL = contextURL + "/manager?ajax=checkUserExecuteFlowPermission&project=" + projectName;
        $.ajax({
            url: requestURL,
            type: "get",
            async: false,
            dataType: "json",
            success: function(data) {
                if (data["executeFlowFlag"] == 1) {
                    console.log("handleRestartClick");
                    var dataValue = graphModel.get("data");

                    var executingData = {
                        project: projectName,
                        ajax: "executeFlow",
                        flow: flowId,
                        execid: execId,
                        exgraph: dataValue,
                        executeFlowTitle: data["executeFlowTitle"]
                    };
                    flowExecuteDialogView.show(executingData);
                } else if (data["executeFlowFlag"] == 2) {
                    $('#user-retry-execute-flow-permit-panel').modal();
                    $('#title-user-retry-execute-flow-permit').text(data["executePermission"]);
                    $('#body-user-retry-execute-flow-permit').html(data["noexecuteFlowPermission"]);
                }
            }
        });
    },

    handlePauseClick: function(evt) {
        $('#paused-flow-modal').modal();
    },

    handleResumeClick: function(evt) {
        var requestURL = contextURL + "/executor";
        var requestData = { "execid": execId, "ajax": "resumeFlow" };
        var successHandler = function(data) {
            console.log("pause clicked");
            if (data.error) {
                showDialog("Error", data.error);
            } else {
                showDialog(wtssI18n.view.restore, wtssI18n.view.workflowRestored);
                setTimeout(function() {
                    updateStatus();
                }, 1100);
            }
        };
        ajaxCall(requestURL, requestData, successHandler);
    }
});

var showDialog = function(title, message) {
    $('#messageTitle').text(title);
    $('#messageBox').text(message);
    $('#messageDialog').modal();
}

var jobListView;
var mainSvgGraphView;
var ref;
var flowLogView;
azkaban.FlowLogView = Backbone.View.extend({
    events: {
        "click #updateLogBtn": "handleUpdate"
    },
    initialize: function(settings) {
        this.model.set({ "offset": 0 });
        var m = this.model;
        this.handleUpdate();
        // this.autoRefresh(m);
    },
    handleUpdate: function(evt) {
        var offset = this.model.get("offset");
        var requestURL = contextURL + "/executor";
        var model = this.model;
        console.log("fetchLogs offset is " + offset)

        $.ajax({
            async: false,
            url: requestURL,
            data: {
                "execid": execId,
                "ajax": "fetchExecFlowLogs",
                "offset": offset,
                "length": 50000
            },
            success: function(data) {
                console.log("fetchLogs");
                if (data.error) {
                    console.log(data.error);
                } else {
                    var log = $("#logSection").text();
                    if (!log) {
                        log = data.data;
                    } else {
                        log += data.data;
                    }

                    var newOffset = data.offset + data.length;

                    $("#logSection").text(log);
                    model.set({ "offset": newOffset, "log": log });
                    $(".logViewer").scrollTop(newOffset);
                }
            }
        });
    },
    clearRefresh: function() {
        if (this.time) {
            console.log("clear refresh.");
            clearInterval(this.time);
            this.time = undefined;
        }
    },
    autoRefresh: function(data) {
        if (this.time) {
            console.log("auto refresh process already exist.");
            return;
        }
        console.log("start auto refresh.");
        var data = this.model;
        var self = this;
        this.time = setInterval(function() {
            var offset = data.get("offset");
            var requestURL = contextURL + "/executor";
            var model = data;
            console.log("fetchLogs offset is " + offset)

            $.ajax({
                async: false,
                url: requestURL,
                data: {
                    "execid": execId,
                    "ajax": "fetchExecFlowLogs",
                    "offset": offset,
                    "length": 50000
                },
                success: function(data) {
                    console.log("fetchLogs");
                    if (data.error) {
                        console.log(data.error);
                    } else {
                        var log = $("#logSection").text();
                        if (!log) {
                            log = data.data;
                        } else {
                            log += data.data;
                        }

                        var newOffset = data.offset + data.length;

                        $("#logSection").text(log);
                        model.set({ "offset": newOffset, "log": log });
                        $(".logViewer").scrollTop(newOffset);
                        if ("Finish" == data.status) {
                            console.log("clear interval.");
                            clearInterval(self.time);
                            self.time = undefined;
                        }
                    }
                }
            });
        }, 5000);
    }
});

var operationParameterView;
azkaban.OperationParameterView = Backbone.View.extend({
    events: {},
    initialize: function(settings) {
        this.getOperationParameter();
    },
    getOperationParameter: function() {
        console.log("operation parameter view.");
        var requestURL = contextURL + "/executor";
        $.ajax({
            async: false,
            url: requestURL,
            data: {
                "execid": execId,
                "ajax": "getOperationParameters",
            },
            success: function(data) {
                console.log("getOperationParameters success.");
                if (data.error) {
                    console.log(data.error);
                } else {
                    var tbody = $("#param-tbody");
                    tbody.empty();
                    var flowParams = data.flowParams;
                    for (var i in flowParams) {
                        var row = document.createElement("tr");
                        //组装执行参数名行
                        var tdName = document.createElement("td");
                        $(tdName).text(i);
                        row.appendChild(tdName);
                        //组装参数值行
                        var tdValue = document.createElement("td");
                        $(tdValue).text(flowParams[i]);
                        row.appendChild(tdValue);
                        tbody.append(row);
                    }
                    var outputParamTbody = $("#job-output-param-tbody");
                    outputParamTbody.empty();
                    var jobOutputGlobalParams = data.jobOutputGlobalParams;
                    for (var i in jobOutputGlobalParams) {
                        var row = document.createElement("tr");
                        //组装执行参数名行
                        var tdName = document.createElement("td");
                        $(tdName).text(i);
                        row.appendChild(tdName);
                        //组装参数值行
                        var tdValue = document.createElement("td");
                        $(tdValue).text(jobOutputGlobalParams[i]);
                        row.appendChild(tdValue);
                        outputParamTbody.append(row);
                    }
                }
            }
        });
    },
});

var statsView;
azkaban.StatsView = Backbone.View.extend({
    events: {},

    initialize: function(settings) {
        this.model.bind('change:graph', this.statusUpdate, this);
        this.model.bind('change:update', this.statusUpdate, this);
        this.model.bind('render', this.render, this);
        this.status = null;
        this.rendered = false;
    },

    statusUpdate: function(evt) {
        var data = this.model.get('data');
        this.status = data.status;
    },

    show: function() {
        this.model.trigger("render");
    },

    render: function(evt) {
        if (this.rendered == true) {
            return;
        }
        if (this.status != 'SUCCEEDED') {
            return;
        }
        flowStatsView.show(execId);
        this.rendered = true;
    }
});

var graphModel;

var logModel;
var flowTriggerModel;
azkaban.LogModel = Backbone.Model.extend({});


var matchData = function(id, newData, ret) {
    if (newData.flow && id == newData.id) {
        ret['0'] = newData;
        return true;
    }
    if (newData.nodes) {
        for (var i = 0; i < newData.nodes.length; i++) {
            if (matchData(id, newData.nodes[i], ret)) {
                break;
            }
        }
    }
    return false;
}

var updateStatus = function(updateTime) {
    var requestURL = contextURL + "/executor";
    var oldData = graphModel.get("data");
    var nodeMap = graphModel.get("nodeMap");

    if (!updateTime) {
        updateTime = oldData.updateTime ? oldData.updateTime : 0;
    }

    var requestData = {
        "execid": execId,
        "ajax": "fetchexecflowupdate",
        "lastUpdateTime": updateTime
    };

    var successHandler = function(data) {
        console.log("data updated");
        if (data.updateTime) {
            var ret = {};
            matchData(oldData.id, data, ret);
            if (ret['0']) {
                data = ret['0'];
            }
            updateGraph(oldData, data);

            graphModel.set({ "update": data });
            graphModel.trigger("change:update");
        }
    };
    ajaxCall(requestURL, requestData, successHandler);
}

function updatePastAttempts(data, update) {
    if (!update.pastAttempts) {
        return;
    }

    if (data.pastAttempts) {
        for (var i = 0; i < update.pastAttempts.length; ++i) {
            var updatedAttempt = update.pastAttempts[i];
            var found = false;
            for (var j = 0; j < data.pastAttempts.length; ++j) {
                var attempt = data.pastAttempts[j];
                if (attempt.attempt == updatedAttempt.attempt) {
                    attempt.startTime = updatedAttempt.startTime;
                    attempt.endTime = updatedAttempt.endTime;
                    attempt.status = updatedAttempt.status;
                    found = true;
                    break;
                }
            }

            if (!found) {
                data.pastAttempts.push(updatedAttempt);
            }
        }
    } else {
        data.pastAttempts = update.pastAttempts;
    }
}

var updateGraph = function(data, update) {
    var nodeMap = data.nodeMap;
    data.startTime = update.startTime;
    data.endTime = update.endTime;
    data.updateTime = update.updateTime;
    data.status = update.status;

    updatePastAttempts(data, update);

    update.changedNode = data;

    if (update.nodes) {
        for (var i = 0; i < update.nodes.length; ++i) {
            var newNode = update.nodes[i];
            var oldNode = nodeMap[newNode.id];
            if (oldNode) {
                updateGraph(oldNode, newNode);
            }
        }
    }
}

var updateTime = -1;
var updaterFunction = function() {
    var oldData = graphModel.get("data");
    var keepRunning =
        oldData.status != "SUCCEEDED" &&
        oldData.status != "FAILED" &&
        oldData.status != "KILLED";

    if (keepRunning) {
        updateStatus();

        var data = graphModel.get("data");
        if (data.status == "UNKNOWN" ||
            data.status == "WAITING" ||
            data.status == "PREPARING") {
            // 2 min updates
            setTimeout(function() {
                updaterFunction();
            }, 2 * 60 * 1000);
        } else if (data.status == "KILLING") {
            // 30 s updates - should finish soon now
            setTimeout(function() {
                updaterFunction();
            }, 30 * 1000);
        } else if (data.status != "SUCCEEDED" && data.status != "FAILED") {
            // 2 min updates
            setTimeout(function() {
                updaterFunction();
            }, 2 * 60 * 1000);
        } else {
            console.log("Flow finished, so no more updates");
            setTimeout(function() {
                updateStatus(0);
            }, 500);
        }
    } else {
        console.log("Flow finished, so no more updates");
    }
}

var logUpdaterFunction = function() {
    var oldData = graphModel.get("data");
    var keepRunning =
        oldData.status != "SUCCEEDED" &&
        oldData.status != "FAILED" &&
        oldData.status != "KILLED";
    if (keepRunning) {
        // update every 2 min for the logs until finished
        flowLogView.handleUpdate();
        setTimeout(function() {
            logUpdaterFunction();
        }, 2 * 60 * 1000);
    } else {
        flowLogView.handleUpdate();
    }
}

var exNodeClickCallback = function(event) {
    console.log("Node clicked callback");
    var jobId = event.currentTarget.jobid;
    var requestURL = contextURL + "/manager?project=" + projectName + "&flow=" +
        flowId + "&job=" + jobId;
    var visualizerURL = contextURL + "/pigvisualizer?execid=" + execId + "&jobid=" +
        jobId;

    var menu = [{
            title: "Open Job...",
            callback: function() {
                window.location.href = requestURL;
            }
        },
        {
            title: "Open Job in New Window...",
            callback: function() {
                window.open(requestURL);
            }
        },
        {
            title: "Visualize Job...",
            callback: function() {
                window.location.href = visualizerURL;
            }
        }
    ];

    contextMenuView.show(event, menu);
}

var exJobClickCallback = function(event) {
    console.log("Node clicked callback");
    var jobId = event.currentTarget.jobid;
    var requestURL = contextURL + "/manager?project=" + projectName + "&flow=" +
        flowId + "&job=" + jobId;
    var visualizerURL = contextURL + "/pigvisualizer?execid=" + execId + "&jobid=" +
        jobId;

    var menu = [{
            title: "Open Job...",
            callback: function() {
                window.location.href = requestURL;
            }
        },
        {
            title: "Open Job in New Window...",
            callback: function() {
                window.open(requestURL);
            }
        },
        {
            title: "Visualize Job...",
            callback: function() {
                window.location.href = visualizerURL;
            }
        }
    ];

    contextMenuView.show(event, menu);
}

var exEdgeClickCallback = function(event) {
    console.log("Edge clicked callback");
}

var exGraphClickCallback = function(event) {
    console.log("Graph clicked callback");
    var requestURL = contextURL + "/manager?project=" + projectName + "&flow=" +
        flowId;

    var menu = [{
            title: wtssI18n.common.openFlow,
            callback: function() {
                window.location.href = requestURL;
            }
        },
        {
            title: wtssI18n.common.openNewWindow,
            callback: function() {
                window.open(requestURL);
            }
        },
        { break: 1 },
        {
            title: wtssI18n.common.centerGraph,
            callback: function() {
                graphModel.trigger("resetPanZoom");
            }
        }
    ];

    contextMenuView.show(event, menu);
}

var flowStatsView;
var flowStatsModel;

$(function() {
    var selected;

    graphModel = new azkaban.GraphModel();
    flowTriggerModel = new azkaban.FlowTriggerModel();
    logModel = new azkaban.LogModel();

    flowTabView = new azkaban.FlowTabView({
        el: $('#headertabs'),
        model: graphModel
    });

    pausedTipsView = new azkaban.PausedTipsView({
        el: $('#paused-flow-modal'),
        model: graphModel
    });

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

    flowLogView = new azkaban.FlowLogView({
        el: $('#flowLogView'),
        model: logModel
    });

    operationParameterView = new azkaban.OperationParameterView({
        el: $('#operationParameterView'),
    });

    statusView = new azkaban.StatusView({
        el: $('#flow-status'),
        model: graphModel
    });

    flowStatsModel = new azkaban.FlowStatsModel();
    flowStatsView = new azkaban.FlowStatsView({
        el: $('#flow-stats-container'),
        model: flowStatsModel,
        histogram: false
    });

    statsView = new azkaban.StatsView({
        el: $('#statsView'),
        model: graphModel
    });

    executionListView = new azkaban.ExecutionListView({
        el: $('#jobListView'),
        model: graphModel
    });

    flowTriggerInstanceListView = new azkaban.FlowTriggerInstanceListView({
        el: $('#flowTriggerListView'),
        model: flowTriggerModel
    });

    var requestURL;
    var requestData;
    if (execId != "-1" && execId != "-2") {
        requestURL = contextURL + "/executor";
        requestData = { "execid": execId, "ajax": "fetchexecflow", "nodeNestedId": nodeNestedId };
    } else {
        requestURL = contextURL + "/manager";
        requestData = {
            "project": projectName,
            "ajax": "fetchflowgraph",
            "flow": flowId
        };
    }

    var successHandler = function(data) {
        console.log("data fetched");
        graphModel.addFlow(data);
        graphModel.trigger("change:graph");

        updateTime = Math.max(updateTime, data.submitTime);
        updateTime = Math.max(updateTime, data.startTime);
        updateTime = Math.max(updateTime, data.endTime);

        if (window.location.hash) {
            var hash = window.location.hash;
            if (hash == "#jobslist") {
                flowTabView.handleJobslistLinkClick();
            } else if (hash == "#log") {
                flowTabView.handleLogLinkClick();
            } else if (hash == "#stats") {
                flowTabView.handleStatsLinkClick();
            } else if (hash == "#triggerslist") {
                flowTabView.handleFlowTriggerLinkClick();
            }
        } else {
            flowTabView.handleGraphLinkClick();
        }
        updaterFunction();
        logUpdaterFunction();
    };
    ajaxCall(requestURL, requestData, successHandler);

    requestURL = contextURL + "/flowtriggerinstance";
    if (execId != "-1" && execId != "-2") {
        requestData = { "execid": execId, "ajax": "fetchTriggerStatus" };
    } else if (triggerInstanceId != "-1") {
        requestData = {
            "triggerinstid": triggerInstanceId,
            "ajax": "fetchTriggerStatus"
        };
    }

    successHandler = function(data) {
        flowTriggerModel.addTrigger(data)
        flowTriggerModel.trigger("change:trigger");
    };
    ajaxCall(requestURL, requestData, successHandler);
    //切换流程图
    $("#switching-flow-btn").on('click', function() {
        var trimFlowName = !JSON.parse(sessionStorage.getItem('trimFlowName')); //标识是否剪切节点名称
        sessionStorage.setItem('trimFlowName', trimFlowName)
        var data = mainSvgGraphView.model.get('data') //获取流程图数据
        data.switchingFlow = true
        $(mainSvgGraphView.mainG).empty() //清空流程图
        mainSvgGraphView.renderGraph(data, mainSvgGraphView.mainG)
    })
});