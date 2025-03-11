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
    var requestURL = filterXSS("/manager?project=" + projectName + "&flow=" +
        flowName + "&job=" + jobid);
    if (action == "open") {
        window.location.href = requestURL;
    } else if (action == "openwindow") {
        window.open(requestURL);
    }
}

var showDialog = function(title, message, canForceKill = false) {
    $('#message-dialog-superkillbtn').hide()
    $('#messageTitle').text(title);
    $('#messageBox').text(message);
    if(canForceKill) {
        $('#message-dialog-superkillbtn').show()
        $('#message-dialog-superkillbtn').click(function() {
            var requestURL = "/executor";
            var requestData = { "execid": execId, "ajax": "cancelFlow", forceCancel: true };
            console.log("Click message-dialog-superkillbtn ");
            var successHandler = function(data) {
                console.log("cancel clicked");
                if (data.error) {
                    $('#messageBox').text('强制停止失败：\n'+data.error);
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
        })

    }
    $('#messageDialog').modal();
}

var failedNodeList = []

var getLeafNodes = function(nodes){
    if(!Array.isArray(nodes) || 0 == nodes.length){
        return [];
    }
    var leafNodes = [];
    for(var i = 0; i < nodes.length; i++){
        var node = nodes[i];
        if(!node.nodes || 0 == node.nodes.length){
            leafNodes.push(node);
        }else{
            leafNodes = leafNodes.concat(getLeafNodes(node.nodes));
        }
    }
    return leafNodes;
}

// 减枝，如果某个节点已经失败，但他的所有子节点都还没有失败，那么这个节点的所有子节点全部抛弃，将改节点变为了叶子节点
var pruneTree = function(root){
    if(!root){
        return null;
    }
    if(Array.isArray(root.nodes) && root.nodes.length!==0){
        root.nodes = root.nodes.map(pruneTree).filter(node => node !== null);
    }
    if(( root.status === "FAILED" || root.status === "FAILED_WAITING") && Array.isArray(root.nodes) && root.nodes.length !== 0 && root.nodes.every(node => node.status !== "FAILED" && node.status !== "FAILED_WAITING")){
       root.nodes= null;
    }
    console.log(root)
    return root
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
            $("#startTime").text(getProjectModifyDateFormat(date));

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
            $("#endTime").text(getProjectModifyDateFormat(date));
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
        var requestURL = "/executor";
        var timeoutHour = $('#pauseInput').val();
        if(!/^(0|[1-9]\d*)(\.\d{1})?$/.test(timeoutHour)){
            alert('请输入正确的时长，大于零的整数或小数，小数精确至一位');
            return;
        }
        var requestData = { "execid": execId, "ajax": "pauseFlow", "timeoutHour": timeoutHour };
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
        "click #superkillbtn": "handleSuperKillClick",
        "click #shutdown-selected-flow-btn": "handleShutdownSelectedFlowClick",
        "click #executebtn": "handleRestartClick",
        "click #pausebtn": "handlePauseClick",
        "click #resumebtn": "handleResumeClick",
        "click #retrybtn": "handleRetryClick",
        "click #skipAllFailedJobBtn": "handleSkipAllFailedJobClick",
        "click #backup-rerun-btn": "handleBackupReRunClick"
    },

    initialize: function(settings) {
        $("#cancelbtn").hide();
        $("#superkillbtn").hide();
        $("#executebtn").hide();
        $("#pausebtn").hide();
        $("#resumebtn").hide();
        $("#retrybtn").hide();
        $("#skipAllFailedJobBtn").hide();

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
        $("#statusFilterList").hide()
        $("#statusFilterList").next('span').hide()
        $("#jobsFilterList").hide()
        $("#jobsFilterList").next('span').hide()
        $("#excuteTimeFilterList").hide()
        $("#excuteTimeFilterList").next('span').hide()
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
        $("#statusFilterList").show()
        $("#statusFilterList").next('span').show()
        $("#jobsFilterList").show()
        $("#jobsFilterList").next('span').show()
        $("#excuteTimeFilterList").show()
        $("#excuteTimeFilterList").next('span').show()
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
        $("#statusFilterList").hide()
        $("#statusFilterList").next('span').hide()
        $("#jobsFilterList").hide()
        $("#jobsFilterList").next('span').hide()
        $("#excuteTimeFilterList").hide()
        $("#excuteTimeFilterList").next('span').hide()
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
        $("#statusFilterList").hide()
        $("#statusFilterList").next('span').hide()
        $("#jobsFilterList").hide()
        $("#jobsFilterList").next('span').hide()
        $("#excuteTimeFilterList").hide()
        $("#excuteTimeFilterList").next('span').hide()
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
        $("#statusFilterList").hide()
        $("#statusFilterList").next('span').hide()
        $("#jobsFilterList").hide()
        $("#jobsFilterList").next('span').hide()
        $("#excuteTimeFilterList").hide()
        $("#excuteTimeFilterList").next('span').hide()

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
        $("#superkillbtn").hide();
        $("#executebtn").hide();
        $("#pausebtn").hide();
        $("#resumebtn").hide();
        $("#retrybtn").hide();
        $("#skipAllFailedJobBtn").hide();

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


    handleCancelClick: function(evt) {

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
                            var requestURL = "/executor";
                            var requestData = { "execid": execId, "ajax": "cancelFlow" };
                            console.log("Click shutdownSelectedFlowClick ");
                            var successHandler = function(data) {
                                console.log("cancel clicked");
                                if (data.error) {
                                    showDialog("Error", data.error, data.supportForceCancel);
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
                    $('#body-user-retry-execute-flow-permit').html(filterXSS(data["killExecutePermissionsDesc"]));
                }
            }
        });
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

    handleShutdownSelectedFlowClick: function(evt, forceKill = false) {
        var requestURL = "/executor";
        var requestData = { "execid": execId, "ajax": "cancelFlow" };
        if(forceKill){
            requestData.forceCancel = true
        }
        console.log("Click shutdownSelectedFlowClick ");
        var successHandler = function(data) {
            console.log("cancel clicked");
            if (data.error) {
                showDialog("Error", data.error, data.supportForceCancel);
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
                // var checkedCheckboxes = document.querySelectorAll('input[name="retry-failed-flow"]:checked');
                // var retryFailedJobs = Array.from(checkedCheckboxes).map(checkbox => checkbox.value);
                var graphData = graphModel.get("data");
                var rerunJobs = []
                var operateStatus = getFailedFinishStatus()
                recursiveRerunNode(graphData.nodes, rerunJobs, operateStatus)
                var requestURL = "/executor?execid=" + execId + "&ajax=retryFailedJobs";
                var requestData = { "retryFailedJobs": rerunJobs };
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
                $.ajax({
                    url: requestURL,
                    type: "POST",
                    contentType: "application/json; charset=utf-8",
                    data: JSON.stringify(requestData),
                    dataType: "json",
                    error: function(data) {
                        console.log(data);
                    },
                    success: successHandler
                });
            });
        });
    },

    //一键跳过所有FAILED_WAITING 状态job
    handleSkipAllFailedJobClick: function(evt) {
        // 弹出对话框
        $('#onekey-skip-failed-job-modal').modal();
        // 定义包含复选框选项的列表
        var options = failedNodeList.map(item=>item.nestedId)

        // 获取容器元素
        var container = document.getElementById('skip-failed-flow-checkbox-container');
        container.innerHTML=''

        // 动态生成复选框列表
        options.forEach(option => {
            // 创建一个标签元素
            var label = document.createElement('label');

            // 创建一个复选框元素
            var checkbox = document.createElement('input');
            checkbox.type = 'checkbox';
            checkbox.name = 'skip-failed-flow';
            checkbox.value = option;
            checkbox.checked = true

            // 将复选框添加到标签中
            label.appendChild(checkbox);

            // 添加选项文本
            label.appendChild(document.createTextNode(option));

            // 将标签添加到容器中
            container.appendChild(label);

            // 添加一个换行符
            container.appendChild(document.createElement('br'));
        });
        $(document).ready(function() {
            $("#onekey-skip-failed-job-btn").click(function() {
                var checkedCheckboxes = document.querySelectorAll('input[name="skip-failed-flow"]:checked');
                var skipFailedJobs = Array.from(checkedCheckboxes).map(checkbox => checkbox.value);
                console.log('skipFailedJobs',skipFailedJobs);
                var graphData = graphModel.get("data");
                var requestURL = "/executor?execid=" + execId + "&ajax=ajaxSkipFailedJobs";
                var requestData = { skipFailedJobs  };
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
                $.ajax({
                    url: requestURL,
                    type: "POST",
                    contentType: "application/json; charset=utf-8",
                    data: JSON.stringify(requestData),
                    dataType: "json",
                    error: function(data) {
                        console.log(data);
                    },
                    success: successHandler
                });
            });
        });

    },

    handleBackupReRunClick: function(evt) {
        console.log("backup rerun")
        // 需要校验是否具有执行工作流权限 1:允许, 2:不允许
        var requestURL = "/manager?ajax=checkUserExecuteFlowPermission&project=" + projectName;
        $.ajax({
            url: requestURL,
            type: "get",
            async: false,
            dataType: "json",
            success: function(data) {
                if (data["executeFlowFlag"] == 1) {
                    console.log("handleRestartClick");
                    localStorage.setItem('isBackupRerun', "true"); // 标记容灾重跑
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
                    $('#body-user-retry-execute-flow-permit').html(filterXSS(data["noexecuteFlowPermission"]));
                }
            }
        });
    },


    // 准备执行
    handleRestartClick: function(evt) {

        // 需要校验是否具有执行工作流权限 1:允许, 2:不允许
        var requestURL = "/manager?ajax=checkUserExecuteFlowPermission&project=" + projectName;
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
                    localStorage.setItem('isBackupRerun', "false"); // 清除容灾重跑
                    flowExecuteDialogView.show(executingData);
                } else if (data["executeFlowFlag"] == 2) {
                    $('#user-retry-execute-flow-permit-panel').modal();
                    $('#title-user-retry-execute-flow-permit').text(data["executePermission"]);
                    $('#body-user-retry-execute-flow-permit').html(filterXSS(data["noexecuteFlowPermission"]));
                }
            }
        });
    },

    handlePauseClick: function(evt) {
        $('#paused-flow-modal').modal();
    },

    handleResumeClick: function(evt) {
        var requestURL = "/executor";
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
// 失败暂停这五个状态可以打开关闭
function getFailedFinishStatus() {
    return ['RETRIED_SUCCEEDED', 'SUCCEEDED', 'SKIPPED', 'FAILED_SKIPPED', 'FAILED_SKIPPED_DISABLED']
}
// 任务重跑、重试失败 打开节点处理
function recursiveRerunNode(nodeData, rerunJobs, operateStatus) {
    for (let i = 0; i < nodeData.length; i++) {
        var temNode = nodeData[i]
        if (temNode.type === 'flow') { // type flow节点不传到后端
            recursiveRerunNode(temNode.nodes, rerunJobs, operateStatus)
        } else {
            if (operateStatus.indexOf(temNode.originStatus) > -1 && temNode.status === 'READY') {
                rerunJobs.push(temNode.nestedId)
            }
        }
    }
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
        var requestURL = "/executor";
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
                    var log = document.getElementById('logSection').innerHTML;
                    var logArr = data.data.split('\n')
                    for (let i = 0; i < logArr.length; i++) {
                        log += '<code>' + logArr[i] + '</code>'
                    }


                    var newOffset = data.offset + data.length;

                    document.getElementById('logSection').innerHTML = log;
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
            var requestURL = "/executor";
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
                        var log = document.getElementById('logSection').innerHTML;
                        var logArr = data.data.split('\n')
                        for (let i = 0; i < logArr.length; i++) {
                            log += '<code>' + logArr[i] + '</code>'
                        }

                        var newOffset = data.offset + data.length;

                        document.getElementById('logSection').innerHTML = log;
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
        var requestURL = "/executor";
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
                    if (jobOutputGlobalParams.userDefined) {
                        var flowDescript = document.getElementById('flowDescript')
                        flowDescript.innerHTML = jobOutputGlobalParams.userDefined
                        flowDescript.setAttribute('class', 'flowDescript')
                    }
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
    var requestURL = "/executor";
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
            var nodes = data.nodes
                // 判断节点是否存在 RETRIED_SUCCESS FAILED_SKIPPED状态切父节点状态是SUCCEEDED，将子节点状态同步到父节点
            if (nodes instanceof Array) {
                for (var j = 0; j < nodes.length; j++) {
                    var flag = { isChange: false, changeStatus: '' }
                    if (nodes[j].type === 'flow') {
                        changeParentStatus(nodes[j].nodes, nodes[j], flag)
                        if (flag.isChange && nodes[j].status === 'SUCCEEDED') {
                            nodes[j].status = flag.changeStatus
                        }
                    }
                }
            }
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
    var requestURL = filterXSS("/manager?project=" + projectName + "&flow=" +
        flowId + "&job=" + jobId);
    var visualizerURL = filterXSS("/pigvisualizer?execid=" + execId + "&jobid=" +
        jobId);

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
    var requestURL = filterXSS("/manager?project=" + projectName + "&flow=" +
        flowId + "&job=" + jobId);
    var visualizerURL = filterXSS("/pigvisualizer?execid=" + execId + "&jobid=" +
        jobId);

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
    var requestURL = filterXSS("/manager?project=" + projectName + "&flow=" +
        flowId);

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
        requestURL = "/executor";
        requestData = { "execid": execId, "ajax": "fetchexecflow", "nodeNestedId": nodeNestedId };
    } else {
        requestURL = "/manager";
        requestData = {
            "project": projectName,
            "ajax": "fetchflowgraph",
            "flow": flowId
        };
    }

    var successHandler = function(data) {
        console.log("data fetched");
        var nodes = data.nodes
        var cloneNodes = JSON.parse(JSON.stringify(nodes));
        pruneTree({status:"FAILED", nodes:cloneNodes})
        failedNodeList =getLeafNodes(cloneNodes || []).filter(function(node) {
            return node.status === "FAILED" || node.status === "FAILED_WAITING";
        })
        console.log('failedNodeList',failedNodeList)
            // 判断节点是否存在 RETRIED_SUCCESS FAILED_SKIPPED状态切父节点状态是SUCCEEDED，将子节点状态同步到父节点
        for (var j = 0; j < nodes.length; j++) {
            var flag = { isChange: false, changeStatus: '' }
            if (nodes[j].type === 'flow') {
                changeParentStatus(nodes[j].nodes, flag)
                if (flag.isChange && nodes[j].status === 'SUCCEEDED') {
                    nodes[j].status = flag.changeStatus
                }
            }
        }

        if (data.executionStrategy === "FAILED_PAUSE" && data.status === "FAILED_FINISHING") {
            setstatus(nodes)

            function setstatus(nodes) {
                for (let i = 0; i < nodes.length; i++) {
                    let node = nodes[i]
                    node.originStatus = node.status
                    if (node.type === 'flow') {
                        setstatus(node.nodes)
                    }
                }
            }
        }
        graphModel.addFlow(data);
        graphModel.trigger("change:graph");
        if ($('#jobsFilterList')[0]) {
            var optionHtml = '<option value="filter_job_name">' + wtssI18n.view.filterJobName + '</option>'
            recursiveNode(data.nodes)

            function recursiveNode(nodes) {
                for (var i = 0; i < nodes.length; i++) {
                    optionHtml += '<option value=' + nodes[i].id + '>' + nodes[i].id + '</option>'
                    // if (nodes[i].status !== 'READY') {
                    //     optionHtml += '<option value=' + nodes[i].id + '>' + nodes[i].id + '</option>'
                    // }
                    if (nodes[i].nodes) {
                        recursiveNode(nodes[i].nodes)
                    }
                }
            }
            $('#jobsFilterList').html(optionHtml)
        }
        updateTime = Math.max(updateTime, data.submitTime);
        updateTime = Math.max(updateTime, data.startTime);
        updateTime = Math.max(updateTime, data.endTime);

        if (window.location.hash) {
            var hash = window.location.hash;
            if (hash == "#jobslist") {
                $("#statusFilterList").show()
                $("#statusFilterList").next('span').show()
                $("#jobsFilterList").show()
                $("#jobsFilterList").next('span').show()
                $("#excuteTimeFilterList").show()
                $("#excuteTimeFilterList").next('span').show()
            } else {
                $("#statusFilterList").hide()
                $("#statusFilterList").next('span').hide()
                $("#jobsFilterList").hide()
                $("#jobsFilterList").next('span').hide()
                $("#excuteTimeFilterList").hide()
                $("#excuteTimeFilterList").next('span').hide()
            }
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

        // $("#executeJobTable").tablesorter();
    };
    ajaxCall(requestURL, requestData, successHandler);

    requestURL = "/flowtriggerinstance";
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

    function getfiterExceteTime(excuteTime, node) {
        var excuteTime = $("#excuteTimeFilterList").val()
        var timeFilter = false
        var endTime = node.endTime == -1 ? (new Date()).getTime() :
            node.endTime;
        var runTime = endTime - node.startTime
        switch (excuteTime) {
            case '1h':
                if (runTime <= 60 * 60 * 1000) {
                    timeFilter = true
                }
                break;
            case '2h':
                if (node.startTime > 0 && runTime > 60 * 60 * 1000 && runTime <= 2 * 60 * 60 * 1000) {
                    timeFilter = true
                }
                break;
            case '3h':
                if (node.startTime > 0 && runTime > 2 * 60 * 60 * 1000 && runTime <= 3 * 60 * 60 * 1000) {
                    timeFilter = true
                }
                break;
            case '>3h':
                if (node.startTime > 0 && runTime > 3 * 60 * 60 * 1000) {
                    timeFilter = true
                }
                break;
        }
        return timeFilter
    }
    function initHeaderAsc () {
        jobsListView.executionTimeAsc = undefined;
        jobsListView.startTimeAsc = undefined;
        jobsListView.endTimeAsc = undefined;
        jobsListView.runDateAsc = undefined;
    }
    function handleFilterJobList() {
        var status = $("#statusFilterList").val()
        var job = $("#jobsFilterList").val()
        var time = $("#excuteTimeFilterList").val()
        var data = [];
        var jobLostData = executionListView.model.get('data');
        var executingBody = $("#executableBody");
        var nodes = _.cloneDeep(jobLostData.nodes);
        JobListNodesSort(nodes)
        executingBody.html('');
        initHeaderAsc();
        if (status === 'filter_job_status' && job === 'filter_job_name' && time === '0') {
            recursionNodeDeleteRow(nodes)

            function recursionNodeDeleteRow(nodes) { // 递归删除joblistrow
                for (var j = nodes.length - 1; j >= 0; j--) {
                    delete nodes[j].joblistrow
                    if (nodes[j].type === 'flow') {
                        recursionNodeDeleteRow(nodes[j].nodes)
                    }
                }
            }
            data = nodes
        } else {
            // 标记最后一个包含过滤状态的对象
            var currentFilter = null
                // 要删除不包含过滤状态的数据，避免删除后数据错位从后面开始遍历
            for (var i = nodes.length - 1; i >= 0; i--) {
                // 删除该对象子工作流，不包含过滤状态数据
                if (currentFilter && currentFilter.nodes) {
                    delete currentFilter.nodes
                }
                // 下一个节点时先初始化
                currentFilter = null
                    //删除存的joblistrow
                delete nodes[i].joblistrow
                recursionNode(nodes[i], nodes, i)
            }
        }

        function recursionNode(subNode, nodeList, index) {
            var statusFilter = false
            var jobFilter = false
            var timeFilter = false
                //删除存的joblistrow
            delete subNode.joblistrow
            if (status === 'filter_job_status' || subNode.status === status) {
                statusFilter = true
            }
            if (job === 'filter_job_name' || job === subNode.id) {
                jobFilter = true
            }
            if (time === '0') {
                timeFilter = true
            } else {
                timeFilter = getfiterExceteTime(time, subNode)
            }
            if (statusFilter && jobFilter && timeFilter) {
                // 如果currentFilter为空证明该节点或该节点的父节点没有push到data
                if (!currentFilter) {
                    data.unshift(nodes[i])
                }
                currentFilter = subNode
            } else {
                if (!subNode.nodes || (Array.isArray(subNode.nodes) && subNode.nodes.length < 1)) {
                    nodeList.splice(index, 1)
                }
            }
            if (subNode.nodes) {
                for (var j = subNode.nodes.length - 1; j >= 0; j--) {
                    recursionNode(subNode.nodes[j], subNode.nodes, j)
                }
            }
        }
        if (data.length > 0) {
            executionListView.updateJobRow(data, executingBody, {}, true); // true 过滤默认展开
            executionListView.expandFailedOrKilledJobs(data);
            var flowLastTime = jobLostData.endTime == -1 ? (new Date()).getTime() :
                jobLostData.endTime;
            var flowStartTime = jobLostData.startTime;
            executionListView.updateProgressBar(jobLostData, flowStartTime, flowLastTime, data);
            $("#executeJobTable").trigger("update");
            $("#executeJobTable").trigger("sorton", "");
        }
    }
    // $('#jobsFilterList').attr('placeholder', wtssI18n.view.filterJobPro)
    // 通过状态过滤表格数据
    $('#statusFilterList').select2({
        width: '250',
        placeholder: wtssI18n.view.filterStatusPro,
        matcher: function(term, option) {
            var search = term.term ? term.term.toLocaleUpperCase() : ''
            return !search ? option : (option.id.indexOf(search) > -1 ? option : false)
        },
        data: [{
            id: 'filter_job_status',
            text: wtssI18n.view.filterJobStatus,
        }, {
            id: 'READY',
            text: 'Ready'
        }, {
            id: 'PREPARING',
            text: 'Preparing'
        }, {
            id: 'RUNNING',
            text: 'Running'
        }, {
            id: 'PAUSED',
            text: 'Paused'
        }, {
            id: 'SUCCEEDED',
            text: 'Success'
        }, {
            id: 'KILLING',
            text: 'Killing'
        }, {
            id: 'KILLED',
            text: 'Killed'
        }, {
            id: 'FAILED',
            text: 'Failed'
        }, {
            id: 'SKIPPED',
            text: 'Skipped'
        }, {
            id: 'DISABLED',
            text: 'Disabled'
        }, {
            id: 'CANCELLED',
            text: 'Cancelled'
        }, {
            id: 'QUEUED',
            text: 'Queued'
        }, {
            id: 'FAILED_SKIPPED',
            text: 'Failed skipped'
        }, {
            id: 'FAILED_WAITING',
            text: 'Failed waiting'
        }, {
            id: 'RETRIED_SUCCEEDED',
            text: 'Retried succeeded'
        }, {
            id: 'FAILED_FINISHING',
            text: 'Running w/Failure'
        }, {
            id: 'FAILED_RETRYING',
            text: 'Failed retrying'
        }, {
            id: 'FAILED_SUCCEEDED',
            text: 'Failed, treated as success'
        }]
    })
    $("#statusFilterList").on('change', function() {
            handleFilterJobList()
        })
        // 通过任务过滤表格数据
    $("#jobsFilterList").on('change', function() {
            handleFilterJobList()
        })
        // 过滤下拉
    $('#jobsFilterList').select2({
        width: '250',
        placeholder: wtssI18n.view.filterJobPro,
        matcher: function(term, option) {
            var search = term.term ? term.term.toLowerCase() : ''
            return !search ? option : (option.id.indexOf(search) > -1 ? option : false)
        }
    })
    $('#excuteTimeFilterList').select2({
        width: '250',
        data: [{
            id: '0',
            text: wtssI18n.view.filterExecutionDuration
        }, {
            id: '1h',
            text: '<= 1h'
        }, {
            id: '2h',
            text: '> 1h && <= 2h'
        }, {
            id: '3h',
            text: '> 2h && <= 3h'
        }, {
            id: '>3h',
            text: '> 3h'
        }, ]
    })
    $("#excuteTimeFilterList").on('change', function() {
        handleFilterJobList()
    })
    // 获取第一列数据
    var colIndexObject = {
        executionTime: 6,
        startTime: 3,
        endTime: 4,
        runDate: 5
    }
    function sortJonTableTr (trList, oldJobTbody, isLevel1 , type) {
        if (!trList.length) {
            return;
        }
        var ascName = type + 'Asc';
        var flowTrList = []
        var subflowTrList = [];
        trList.forEach(tr => {
            tr.getAttribute('class')? flowTrList.push(tr) : subflowTrList.push(tr);
        });
        var sublowTrLen = subflowTrList.length;
        if (sublowTrLen) {
            subflowTrList.forEach(function(subTr){
                var oldJobTbody = subTr.children[0].children[0]; // .children[0]
                var subTrList = Array.from(oldJobTbody.children);
                sortJonTableTr(subTrList, oldJobTbody, false, type);
            });
        }
        var colIndex = colIndexObject[type];
        flowTrList.sort(function(a, b){
            var aVal, bVal, aText = a.children[colIndex].innerText, BText = b.children[colIndex].innerText;
            if (type === 'executionTime') {
                aVal =  aText !== "-" && aText ? handleExecutionTime(aText): 0;
                bVal = BText !== "-" && BText ? handleExecutionTime(BText) : 0;
            }else if (type === 'runDate') {
                aVal = aText !== "-" && aText ? parseInt(aText) : 0;
                bVal = BText !== "-" && BText ? parseInt(BText) : 0;
            } else{
                aVal = aText !== "-" && aText ? new Date(aText).getTime() : 0;
                bVal = BText !== "-" && BText ? new Date(BText).getTime() : 0;
            }
            return  jobsListView[ascName] ? bVal - aVal : aVal -bVal;
        })

        var newJobTbody = document.createElement('tbody');
        if (isLevel1) {
            newJobTbody.setAttribute('id', 'executableBody');
        }

        for (var i = 0; i < flowTrList.length; ++i) {
            newJobTbody.appendChild(flowTrList[i]);
            var flowName = flowTrList[i].getAttribute('name');
            if (sublowTrLen && flowName) {
                var subflow = subflowTrList.find(function(ele) {
                    return ele.getAttribute('name') === flowName;
                });
                subflow && newJobTbody.appendChild(subflow);
            }
        }
        oldJobTbody.parentNode.replaceChild(newJobTbody, oldJobTbody);
    }
    function getExecutableBodyTr (type) {
        var oldJobTbody = document.getElementById('executableBody');
        var trList = Array.from(oldJobTbody.children) ;
        var ascName = type + 'Asc';
        jobsListView[ascName] = jobsListView[ascName] === undefined ? false : !jobsListView[ascName];
        if (!trList.length) {
            return;
        }
        sortJonTableTr(trList, oldJobTbody, true, type);
    }
    $("#executionTimeHeader").click(function(e) {
        e.stopPropagation();
        getExecutableBodyTr('executionTime');
    });

    $("#startTimeHeader").click(function(e) {
        e.stopPropagation();
        getExecutableBodyTr('startTime');
    });
    $("#endTimeHeader").click(function(e) {
        e.stopPropagation();
        getExecutableBodyTr('endTime');
    });
    $("#runBatchDateHeader").click(function(e) {
        e.stopPropagation();
        getExecutableBodyTr('runDate');
    });

});

// 任务列表节点显示项目初始化显示顺序一致
function JobListNodesSort(nodes) {
    for (var i = 0; i < nodes.length; i++) {
        var node = nodes[i]
        if (node.type == 'flow') {
            node.nodes.sort(idSort);
            JobListNodesSort(node.nodes)
        }
    }
}

//判断节点是否存在 RETRIED_SUCCESS FAILED_SKIPPED状态切父节点状态是SUCCEEDED，将子节点状态同步到父节点 parentArr父节点数组
function changeParentStatus(nodes, flag) {
    for (let i = 0; i < nodes.length; i++) {
        var subFlag = { isChange: false, changeStatus: '' }
        if (nodes[i].type === 'flow') {
            changeParentStatus(nodes[i].nodes, subFlag)
            if (subFlag.isChange && nodes[i].status === 'SUCCEEDED') {
                nodes[i].status = subFlag.changeStatus
            }
        }
        if ((nodes[i].status === 'RETRIED_SUCCESS' || nodes[i].status === 'FAILED_SKIPPED')) {
            if (!flag.isChange) {
                flag.isChange = true
                flag.changeStatus = nodes[i].status
            }
        }
    }
}