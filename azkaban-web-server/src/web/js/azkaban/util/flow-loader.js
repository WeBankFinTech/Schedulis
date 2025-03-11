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
var openJobDisplayCallback = function(nodeId, flowId, evt) {
    console.log("Open up data");

    /*
    $("#flowInfoBase").before(cloneStuff);
    var requestURL =  "/manager";

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
// 复制任务名称
function copyNodeName (node) {
    var inputElement = document.querySelector('#copynodeName');
    if (!inputElement) {
        inputElement = document.createElement('input');
		inputElement.setAttribute('id', 'copynodeName');
        inputElement.setAttribute('style', "opacity: 0;");
        document.body.appendChild(inputElement);
    }
    inputElement.value = node.id;
    inputElement.select();
    document.execCommand('copy');
    messageBox.show(wtssI18n.view.copySuccessful);
}

var createNewPanel = function(node, model, evt) {
    var parentPath = node.parentPath;

    var nodeInfoPanelID = parentPath ? parentPath + ":" + node.id + "-info" :
        node.id + "-info";
    var cloneStuff = $("#flowInfoBase").clone();
    cloneStuff.data = node;
    $(cloneStuff).attr("id", nodeInfoPanelID);
    $("#flowInfoBase").before(cloneStuff);

    var backboneView = new azkaban.FlowExtendedViewPanel({ el: cloneStuff, model: model });
    node.panel = backboneView;
    backboneView.showExtendedView(evt);
}

var closeAllSubDisplays = function() {
    $(".flowExtendedView").hide();
}

function closeJobOperation (type, execId, node) {
    console.log("disabled job id: " + node.id);
    var requestURL = "/executor?execid=" + execId;
    var requestData = {};
    if (type === 'continueJob') {
        requestURL += "&ajax=ajaxDisableJob";
        requestData = { "disableJob": node.nestedId };
    } else {
        requestURL += "&ajax=ajaxSetJobFailed&setJob=" +  node.nestedId;
    }
    var successHandler = function(data) {
        if (data.error) {
            showDialog("Error", data.error);
            updateStatus();
        } else {
            var prompt = window.langType === 'zh_CN' ? 'Job：' + node.id + '设置成功！': 'Job：' + node.id + ' setting successful';
            showDialog(type === 'continueJob' ? wtssI18n.common.skipExecution : wtssI18n.view.closedSettingFaild, 'Job：' + node.id + '设置成功！');
            setTimeout(function() {
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
        error: function(data) {
            console.log(data);
        },
        success: successHandler
    });
}

function closeFailedJob (menu, menuIndex, executorId, jobId ) {
    menu.splice(menuIndex, 0, {
        title: '关闭执行',
        callback: function() {
            var requestURL = `/executor?ajax=ajaxSkipFailedJobs&execid=${executorId}`;
            var requestData = {
                skipFailedJobs: [jobId],
            };
            var successHandler = function(data) {
                if (data.error) {
                    showDialog("Error", data.error);
                    updateStatus();
                } else {
                    messageBox.show(`${jobId}关闭执行成功`)
                    setTimeout(function() {
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
                error: function(data) {
                    console.log(data);
                },
                success: successHandler
            });
        }
    });
}

const skipCurrentJob = function(menu, model, node, execId) {
    if ((model.attributes.data.executionStrategy == "FAILED_PAUSE" && node.status == "FAILED_WAITING") ||  (!['SUCCEEDED', 'FAILED', 'KILLED'].includes(model.attributes.data.status) &&  node.status === "FAILED")) {
        menu.push({
            title: wtssI18n.common.skipCurrentJob,
            callback: function() {
                console.log("job id: " + node.id);
                var requestURL = "/executor?execid=" + execId + "&ajax=ajaxSkipFailedJobs";
                var requestData = { "skipFailedJobs": [node.nestedId] };
                var successHandler = function(data) {
                    if (data.error) {
                        showDialog("Error", data.error);
                    } else {
                        var prompt = ""
                        if (langType === "en_US") {
                            prompt = "Set Skip To Execute Job " + node.nestedId + " Successfully."
                        } else {
                            prompt = "设置跳过执行job:" + node.nestedId + ", 成功。"
                        }
                        showDialog(wtssI18n.common.taskFailureSettings, prompt);
                        setTimeout(function() {
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
                    error: function(data) {
                        console.log(data);
                    },
                    success: successHandler
                });
            }
        });
    }
};
var nodeClickCallback = function(event, model, node) {
        console.log("Node clicked callback");

        //获取节点最新信息
        // http://127.0.0.1:8290/executor?execid=238653&ajax=fetchexecflow&nodeNestedId=
        var newAttempt = 0;
        if (model.attributes.data.execid) {
            $.ajax({
                async: false,
                url: "/executor",
                dataType: "json",
                type: "GET",
                data: {
                    execid: model.attributes.data.execid,
                    ajax: "fetchexecflow",
                    nodeNestedId: node.nestedId,
                },
                success: function(data) {
                    newAttempt = data.attempt ? data.attempt : 0;
                }
            });
        }
        const workflowStatus = model.attributes.data.status;
        var target = event.currentTarget;
        var type = node.type;
        var flowId = node.parent.flow;
        var jobId = node.id;
        var executorId = node.parent.execid;
        var tmpId = node.sourceNodeId ? node.sourceNodeId : jobId;
        var requestURL = filterXSS("/manager?project=" + projectName + "&flow=" +
            flowId + "&job=" + tmpId);
        var menu = [];

        var jobHistoryURL = filterXSS("/manager?project=" + projectName + "&job=" + node.id + "&history");

        var jobLogURL = filterXSS("/executor?execid=" + executorId + "&job=" + node.nestedId + "&attempt=" + newAttempt);
        if (type == "flow") {
            //打开工作流,需要显示当前执行结果,需要execid,同时也需要当前节点
            var flowRequestURL = filterXSS("/manager?project=" + projectName +
                "&flow=" + node.flowId);
            //如果是执行记录,则显示执行状态,如果是工作流,则显示工作流
            if (executorId) {
                flowRequestURL = filterXSS("/executor?execid=" + executorId +
                    "&nodeNestedId=" + node.nestedId + "&flow=" + node.flowId);
            }
            if (node.expanded) {
                menu = [{
                        title: wtssI18n.common.collapseFlow,
                        callback: function() {
                            model.trigger("collapseFlow", node);
                        }
                    },
                    {
                        title: wtssI18n.common.collapseAllFlow,
                        callback: function() {
                            model.trigger("collapseAllFlows", node);
                        }
                    }
                ];
            } else {
                menu = [{
                        title: wtssI18n.common.expandFlow,
                        callback: function() {
                            model.trigger("expandFlow", node);
                        }
                    },
                    {
                        title: wtssI18n.common.expandAllFlow,
                        callback: function() {
                            model.trigger("expandAllFlows", node);
                        }
                    }
                ];
            }
            $.merge(menu, [
                //  {title: "View Properties...", callback: function() {openJobDisplayCallback(jobId, flowId, event)}},
                { break: 1 },
                {
                    title: wtssI18n.common.openFlow,
                    callback: function() {
                        window.location.href = flowRequestURL;
                    }
                },
                {
                    title: wtssI18n.common.openNewWindow,
                    callback: function() {
                        window.open(flowRequestURL);
                    }
                },
                { break: 1 },
                {
                    title: wtssI18n.common.workflowProperties,
                    callback: function() {
                        window.location.href = requestURL;
                    }
                },
                {
                    title: wtssI18n.common.openNewProperties,
                    callback: function() {
                        window.open(requestURL);
                    }
                },
                { break: 1 },
                {
                    title: wtssI18n.common.centerFlow,
                    callback: function() {
                        model.trigger("centerNode", node);
                    }
                },
            ]);
            if (executorId && isRunning(node.status) && (node.parent && !node.parent.autoDisabled && !node.parent.disable) && !isFinished(workflowStatus)) {
                menu.splice(-1, 0, {
                    title: wtssI18n.common.skipExecution,
                    callback: function() {
                        closeJobOperation('continueJob', execId, node);
                    }
                });
                menu.splice(-1, 0, {
                    title: wtssI18n.view.closedSettingFaild,
                    callback: function() {
                        closeJobOperation('failedJob', execId, node);
                    }
                });
            }
            // 运行中工作流，失败节点关闭执行
            if (executorId && !isFinished(workflowStatus) && node.status === "FAILED") {
                closeFailedJob(menu, -1, executorId, node.nestedId);
            }
            skipCurrentJob(menu, model, node, executorId);
        } else {
            menu = [
                {
                    title: wtssI18n.common.openJob,
                    callback: function() {
                        window.location.href = requestURL;
                    }
                },
                {
                    title: wtssI18n.common.openNewJob,
                    callback: function() {
                        window.open(requestURL);
                    }
                },
                { break: 1 },
                {
                    title: wtssI18n.common.centerJob,
                    callback: function() {
                        model.trigger("centerNode", node)
                    }
                },
                { break: 1 },
                { title: wtssI18n.common.executiveHistory, callback: function() { window.open(jobHistoryURL); } }
            ];
            if (executorId) {
                menu.push({ title: wtssI18n.common.taskLog, callback: function() { window.open(jobLogURL); } });
            }

            // flow运行是可以disabled为运行的job
            if (executorId && isRunning(node.status) && (node.parent && !node.parent.autoDisabled && !node.parent.disable) && !isFinished(workflowStatus)) {
                menu.splice(4, 0, {
                    title: wtssI18n.common.skipExecution,
                    callback: function() {
                        closeJobOperation('continueJob', execId, node);
                    }
                });
                menu.splice(5, 0, {
                    title: wtssI18n.view.closedSettingFaild,
                    callback: function() {
                        closeJobOperation('failedJob', execId, node);
                    }
                });
            }

            // // 任务running时间长 任务重跑
            if (executorId && node.status === 'RUNNING' && ['RUNNING', 'FAILED_FINISHING'].includes(workflowStatus)) {
                menu.splice(4, 0, {
                    title: wtssI18n.view.jobRerun,
                    callback: function() {
                        console.log("disabled job nestedId: " + node.nestedId);
                        var requestURL = "/executor";
                        var requestData = {
                            "jobId": node.nestedId,
                            "ajax": "rerunJob",
                            execid: executorId
                        };
                        var successHandler = function(data) {
                            if (data.error) {
                                showDialog("Error", data.error);
                            } else if (data.status) {
                                messageBox.show(wtssI18n.view.jobRerunPro)
                            }
                        }
                        $.get(requestURL, requestData, successHandler, "json");
                    }
                });
            }
            // 运行中工作流，失败节点关闭执行
            if (executorId && !isFinished(workflowStatus) && node.status === "FAILED") {
                closeFailedJob(menu, 4, executorId, node.nestedId);
            }

            // 执行策略是失败暂停 job状态是FAILED_WAITING
            skipCurrentJob(menu, model, node, executorId);
            if (model.attributes.data.executionStrategy == "FAILED_PAUSE" && node.status == "FAILED_WAITING") {

                menu.push({
                    title: wtssI18n.common.retryCurrentJob,
                    callback: function() {
                        console.log("job id path: " + node.nestedId);
                        var requestURL = "/executor?execid=" + execId + "&ajax=ajaxRetryFailedJobs";
                        var requestData = { "retryFailedJobs": [node.nestedId] };
                        var successHandler = function(data) {
                            if (data.error) {
                                showDialog("Error", data.error);
                            } else {
                                var prompt = ""
                                if (langType === "en_US") {
                                    prompt = "Retry The Job " + node.nestedId + " Successfully."
                                } else {
                                    prompt = "设置重试job:" + node.nestedId + ", 成功。"
                                }
                                showDialog(wtssI18n.common.taskFailureSettings, prompt);
                                setTimeout(function() {
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
                            error: function(data) {
                                console.log(data);
                            },
                            success: successHandler
                        });
                    }
                });
                var flowNodesData = model.get('data')
                var operateStatus = getFailedFinishStatus()
                menu.push({
                    title: wtssI18n.common.rerunJobs,
                    callback: function() {
                        var rerunJobs = [node.nestedId]
                        recursiveRerunNode(flowNodesData.nodes, rerunJobs, operateStatus)
                        var requestURL = "/executor?execid=" + execId + "&ajax=ajaxRetryFailedJobs";
                        var requestData = { "retryFailedJobs": rerunJobs };
                        var successHandler = function(data) {
                            if (data.error) {
                                showDialog("Error", data.error);
                            } else {
                                var prompt = ""
                                if (langType === "en_US") {
                                    prompt = "Retry The Job " + node.nestedId + " Successfully."
                                } else {
                                    prompt = "设置重试job:" + node.nestedId + ", 成功。"
                                }
                                showDialog(wtssI18n.common.taskFailureSettings, prompt);
                                setTimeout(function() {
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
                            error: function(data) {
                                console.log(data);
                            },
                            success: successHandler
                        });
                    }
                });
            }
        }
        // 工作流执行时打开disabled节点
        if (executorId && ['RUNNING', 'FAILED_FINISHING'].includes(workflowStatus)  && node.status == "DISABLED") {
            menu.push({
                title: wtssI18n.view.enableJob,
                callback: function() {
                    console.log("job id: " + node.id);
                    var requestURL = "/executor?execid=" + execId + "&ajax=ajaxOpenJob";
                    var requestData = { "openJob": [node.nestedId] };
                    var successHandler = function(data) {
                        if (data.error) {
                            showDialog("Error", data.error);
                        } else {
                            var prompt = ""
                            if (langType === "en_US") {
                                prompt = "Set To Execute Job " + node.nestedId + " Successfully."
                            } else {
                                prompt = "设置执行job:" + node.nestedId + ", 成功。"
                            }
                            showDialog('任务设置', prompt);
                        }
                        setTimeout(function() {
                            updateStatus();
                        }, 1100);
                    }
                    $.ajax({
                        url: requestURL,
                        type: "POST",
                        data: JSON.stringify(requestData),
                        dataType: "json",
                        contentType: "application/json; charset=utf-8",
                        error: function(data) {
                            console.log(data);
                        },
                        success: successHandler
                    });
                }
            });
         }
        // 失败暂停 增加打开关闭
        var flowNodesData = model.get('data')
        var operateStatus = getFailedFinishStatus()
        // autoDisabled 为true节点默认关闭,且不能打开
        if (!node.autoDisabled && flowNodesData.executionStrategy === "FAILED_PAUSE" && flowNodesData.status === "FAILED_FINISHING" && (operateStatus.indexOf(node.status) > -1 || operateStatus.indexOf(node.originStatus) > -1)) {
            //打开
            menu.push({
                    key: "openNode",
                    title: wtssI18n.view.open,
                    callback: function() {
                        failedFinishTouchNode(node, false);
                    },
                    submenu: [{
                            title: wtssI18n.view.openParentNode,
                            callback: function() {
                                failedFinishTouchParents(node, false);
                            }
                        },
                        {
                            title: wtssI18n.view.openPreviousNode,
                            callback: function() {
                                failedFinishTouchAncestors(node, false);
                            }
                        },
                        {
                            title: wtssI18n.view.openChildNode,
                            callback: function() {
                                failedFinishTouchChildren(node, false);
                            }
                        },
                        {
                            title: wtssI18n.view.openDescendantNodes,
                            callback: function() {
                                failedFinishTouchDescendents(node, false);
                            }
                        }
                    ]
                })
                // 关闭
            menu.push({
                key: "closeNode",
                title: wtssI18n.view.close,
                callback: function() {
                    failedFinishTouchNode(node, true)
                },
                submenu: [{
                        title: wtssI18n.view.closeParentNode,
                        callback: function() {
                            failedFinishTouchParents(node, true);
                        }
                    },
                    {
                        title: wtssI18n.view.closePreviousNode,
                        callback: function() {
                            failedFinishTouchAncestors(node, true);
                        }
                    },
                    {
                        title: wtssI18n.view.closeChildNode,
                        callback: function() {
                            failedFinishTouchChildren(node, true);
                        }
                    },
                    {
                        title: wtssI18n.view.closeDescendantNodes,
                        callback: function() {
                            failedFinishTouchDescendents(node, true);
                        }
                    }
                ]
            })
        }
        menu.push({ break: 1 });
        menu.push({
            title: wtssI18n.view.copyJobName,
            callback: function() {
                copyNodeName(node);
            }
        })
        contextMenuView.show(event, menu);
    }
    //------------------------------->打开关闭

// 处理失败暂停打开关闭节点状态
function handleFailedFinishStatus(node, disable) {
    // autoDisabled 为true节点默认关闭
    if (node.autoDisabled) {
        return;
    }
    node.status = disable ? node.originStatus : 'READY';
}

function failedFinishCheckJobType(node, disable) {
    if (node.type == "flow") {
        failedFinishRecurseTree(node, disable, true);
    }
}

function failedFinishRecurseTree(data, disable, recurse) {
    var operateStatus = getFailedFinishStatus()
    for (var i = 0; i < data.nodes.length; ++i) {
        var node = data.nodes[i];
        // 除这五个状态其他都不用显示打开关闭,fubflow 正在运行部分子节点success
        if ((operateStatus.indexOf(node.originStatus) < 0 && operateStatus.indexOf(node.status) < 0) && node.type !== 'flow') {
            continue
        }
        handleFailedFinishStatus(node, disable)
        if (node.type == "flow" && recurse) {
            failedFinishRecurseTree(node, disable, recurse);
        }
    }
}

function failedFinishTouchNode(node, disable) {
    failedFinishDisableSubflow(true, node, disable)
    graphModel.trigger("failedFinishStatusChanges");
}

function failedFinishTouchParents(node, disable) {
    var inNodes = node.inNodes;

    failedFinishDisableSubflow(false, inNodes, disable);

    graphModel.trigger("failedFinishStatusChanges");
}



function failedFinishTouchChildren(node, disable) {
    var outNodes = node.outNodes;

    failedFinishDisableSubflow(false, outNodes, disable)

    graphModel.trigger("failedFinishStatusChanges");
}

function failedFinishTouchAncestors(node, disable) {
    var inNodes = node.inNodes;
    if (inNodes && !disable) {
        var key = Object.keys(inNodes)[0]
        failedFinishEnableSubflow(inNodes[key])
    }
    failedFinishRecurseAllAncestors(node, disable);

    graphModel.trigger("failedFinishStatusChanges");
}

function failedFinishTouchDescendents(node, disable) {
    var outNodes = node.outNodes;
    if (outNodes && !disable) {
        var key = Object.keys(outNodes)[0]
        failedFinishEnableSubflow(outNodes[key])
    }
    failedFinishRecurseAllDescendents(node, disable);

    graphModel.trigger("failedFinishStatusChanges");
}

function failedFinishRecurseAllAncestors(node, disable) {
    var inNodes = node.inNodes;
    if (inNodes) {
        for (var key in inNodes) {
            if (!inNodes[key].originStatus) {
                inNodes[key].originStatus = inNodes[key].status
            }
            handleFailedFinishStatus(inNodes[key], disable)
            checkJobType(inNodes[key], disable);
            failedFinishRecurseAllAncestors(inNodes[key], disable);
        }
    }
}

function failedFinishRecurseAllDescendents(node, disable) {
    var outNodes = node.outNodes;
    if (outNodes) {
        var operateStatus = getFailedFinishStatus();
        for (var key in outNodes) {
            // 除这五个状态其他都不用显示打开关闭,fubflow 正在运行部分子节点success
            if ((operateStatus.indexOf(outNodes[key].originStatus) < 0 && operateStatus.indexOf(outNodes[key].status) < 0) && outNodes[key].type !== 'flow') {
                continue
            }
            handleFailedFinishStatus(outNodes[key], disable)
            failedFinishCheckJobType(outNodes[key], disable);
            failedFinishRecurseAllDescendents(outNodes[key], disable);
        }
    }
}

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

// 打开关闭节点，如果节点类型为flow则需要打开关闭subfolw流程所有节点
function failedFinishDisableSubflow(single, node, disable) {
    if (!node) return;
    if (single) {
        handleFailedFinishStatus(node, disable)
        failedFinishCheckJobType(node, disable);
        if (!disable) {
            failedFinishEnableSubflow(node);
        }
    } else {
        var operateStatus = getFailedFinishStatus()
        for (var key in node) {
            if (!disable) {
                failedFinishEnableSubflow(node[key]);
            }
            // 除这五个状态其他都不用显示打开关闭,fubflow 正在运行部分子节点success
            if ((operateStatus.indexOf(node[key].originStatus) < 0 && operateStatus.indexOf(node[key].status) < 0) && node[key].type !== 'flow') {
                continue
            }
            handleFailedFinishStatus(node[key], disable)
            failedFinishCheckJobType(node[key], disable);
        }
    }
}
// 启用工作流如果父流程节点为disable要先把父节点disable改成true
function failedFinishEnableSubflow(node) {
    var parentNode = node.parent
    if (parentNode.type === 'flow') {
        handleFailedFinishStatus(parentNode, false)
        failedFinishEnableSubflow(parentNode)
    }
}
//<-------------------------------打开关闭

var jobClickCallback = function(event, model, node, isSearchJob = false) {
    console.log("Node clicked callback");
    var target = event.currentTarget;
    var type = node.type;
    var flowId = node.parent.flow;
    var jobId = node.id;
    var executorId = node.parent.execid;
    const projectName = window.projectName || model.get('data').project;
    var requestURL = filterXSS("/manager?project=" + projectName + "&flow=" +
        flowId + "&job=" + node.id);
    var flowHistoryURL = filterXSS("/manager?project=" + projectName + "&flow=" + flowId + "#executions");
    var jobHistoryURL = filterXSS("/manager?project=" + projectName + "&job=" + node.id + "&history");
    var jobLogURL = filterXSS("/executor?execid=" + executorId + "&job=" + node.nestedId)
    if (node.attempt) {
        jobLogURL += filterXSS("&attempt=" + node.attempt);
    }
    var menu;
    if (type == "flow") {
        //打开工作流,需要显示当前执行结果,需要execid,同时也需要当前节点
        var flowRequestURL = filterXSS("/manager?project=" + projectName +
            "&flow=" + node.flowId);
        //如果是执行记录,则显示执行状态,如果是工作流,则显示工作流
        if (executorId) {
            flowRequestURL = filterXSS("/executor?execid=" + executorId +
                "&nodeNestedId=" + node.nestedId);
        }
        menu = [
            //  {title: "View Properties...", callback: function() {openJobDisplayCallback(jobId, flowId, event)}},
            //  {break: 1},
            {
                title: wtssI18n.common.openFlow,
                callback: function() {
                    window.location.href = flowRequestURL;
                }
            },
            {
                title: wtssI18n.common.openNewWindow,
                callback: function() {
                    window.open(flowRequestURL);
                }
            },
            { break: 1 },
            {
                title: wtssI18n.common.workflowProperties,
                callback: function() {
                    window.location.href = requestURL;
                }
            },
            {
                title: wtssI18n.common.openNewProperties,
                callback: function() {
                    window.open(requestURL);
                }
            },
            { break: 1 },
            {
                title: wtssI18n.common.centerFlow,
                callback: function() {
                    model.trigger("centerNode", node)
                }
            }
        ];
    } else {
        menu = [
            //  {title: "View Job...", callback: function() {openJobDisplayCallback(jobId, flowId, event)}},
            //  {break: 1},
            {
                title: wtssI18n.common.openJob,
                callback: function() {
                    window.location.href = requestURL;
                }
            },
            {
                title: wtssI18n.common.openNewJob,
                callback: function() {
                    window.open(requestURL);
                }
            },
            { break: 1 },
            {
                title: wtssI18n.common.centerJob,
                callback: function() {
                    // var jobPanel = document.getElementById('joblist-panel') ;
                    // var graphModelKey = 'graphModel';
                    // var viewKey = '';
                    // // execute-joblist-panel 调度搜索弹框
                    // if (document.getElementById('execute-flow-panel').style.display === 'block' && document.getElementById('execute-joblist-panel').style.display === 'block') {
                    //     jobPanel = document.getElementById('execute-joblist-panel');
                    //     viewKey = 'executeJobListView';
                    //     graphModelKey = 'executableGraphModel';
                    // // joblist-panel视图搜索弹窗
                    // } else if (jobPanel && jobPanel.id === 'joblist-panel') {
                    //     viewKey = 'jobsListView';
                    // }
                    // // 视图左上角搜索节点，选择任务展示到中心,先选中该节点，如果是flow并展开子节点
                    // if (jobPanel.style.display === 'block') {
                    //     if (window[viewKey].model.has("selected")) {
                    //         var selected = window[viewKey].model.get("selected");
                    //         if (selected == node) {
                    //             window[viewKey].model.unset("selected");
                    //         } else {
                    //             window[viewKey].model.set({ "selected": node });
                    //         }
                    //     } else {
                    //         window[viewKey].model.set({ "selected": node });
                    //     }
                    //     setTimeout(() => {
                    //         window[graphModelKey].trigger("centerNode", node)
                    //     }, 1000);
                    // } else {
                    //     window[graphModelKey].trigger("centerNode", node)
                    // }
                    if (isSearchJob) {
                        if (model.has("selected")) {
                            var selected = model.get("selected");
                            if (selected == node) {
                                model.unset("selected");
                            } else {
                                model.set({ "selected": node });
                            }
                        } else {
                            model.set({ "selected": node });
                        }
                        setTimeout(() => {
                            model.trigger("centerNode", node)
                        }, 1000);
                    } else {
                        model.trigger("centerNode", node)
                    }
                }
            },
            { break: 1 },
            { title: wtssI18n.common.executiveHistory, callback: function() { window.open(jobHistoryURL); } }
        ];
        if (executorId && executorId !== -1) {
            menu.push({ title: wtssI18n.common.taskLog, callback: function() { window.open(jobLogURL); } });
        }
    }
    contextMenuView.show(event, menu);
}

var edgeClickCallback = function(event, model) {
    console.log("Edge clicked callback");
}

var graphClickCallback = function(event, model) {
    console.log("Graph clicked callback");
    var data = model.get("data");
    var flowId = data.flow;
    var requestURL = filterXSS("/manager?project=" + projectName + "&flow=" +
        flowId);

    var menu = [{
            title: wtssI18n.common.expandAllWorkflow,
            callback: function() {
                model.trigger("expandAllFlows");
                model.trigger("resetPanZoom");
            }
        },
        {
            title: wtssI18n.common.collapseAllWorkflow,
            callback: function() {
                model.trigger("collapseAllFlows");
                model.trigger("resetPanZoom");
            }
        },
        { break: 1 },
        {
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
                model.trigger("resetPanZoom");
            }
        }
    ];

    contextMenuView.show(event, menu);
}


var nodeDBClickCallback = function(event, model, node) {
    console.log("Node dbclicked callback");

    var target = event.currentTarget;
    var type = node.type;
    var flowId = node.parent.flow;
    var jobId = node.id;
    var executorId = node.parent.execid;
    jobId = node.sourceNodeId ? node.sourceNodeId : jobId;
    if (node.flowId) {
        var requestURL = filterXSS("/manager?project=" + projectName + "&flow=" + node.flowId + "&job=" + jobId + "&treeFlow=" + flowId);
        window.open(requestURL);
    } else {
        var requestURL = filterXSS("/manager?project=" + projectName + "&flow=" + flowId + "&job=" + jobId);
        window.open(requestURL);
    }

}