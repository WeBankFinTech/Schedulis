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

var flowEventScheduleDialogView;
var executingSvgGraphView;
azkaban.FlowEventScheduleDialogView = Backbone.View.extend({
    events: {
        "click .closeExecPanel": "hideScheduleOptionPanel",
        "click #event-schedule-flow-button": "scheduleFlow"
    },

    initialize: function(settings) {
        this.model.bind('change:flowinfo', this.changeFlowInfo, this);
        $("#event-schedule-override-success-emails").click(function(evt) {
            if ($(this).is(':checked')) {
                $('#event-schedule-success-emails').attr('disabled', null);
            } else {
                $('#event-schedule-success-emails').attr('disabled', "disabled");
            }
        });

        $("#event-schedule-override-failure-emails").click(function(evt) {
            if ($(this).is(':checked')) {
                $('#event-schedule-failure-emails').attr('disabled', null);
            } else {
                $('#event-schedule-failure-emails').attr('disabled', "disabled");
            }
        });

    },

    render: function() {},

    getExecutionOptionData: function() {
        var failureAction = $('#event-schedule-failure-action').val();
        var failureEmails = $('#event-schedule-failure-emails').val();
        var successEmails = $('#event-schedule-success-emails').val();
        var notifyFailureFirst = $('#event-schedule-notify-failure-first').parent().attr('class').search('active') > -1 ? true : false;
        var notifyFailureLast = !notifyFailureFirst;
        var failureEmailsOverride = $("#event-schedule-override-failure-emails").is(':checked');
        var successEmailsOverride = $("#event-schedule-override-success-emails").is(':checked');
        //告警级别选择
        var failureAlertLevel = $('#event-schedule-override-failure-alert-level').val();
        var successAlertLevel = $('#event-schedule-override-success-alert-level').val();
        var token = $('#event-work-flow-token_input').val().trim();
        var topic = $('#event-schedule-topic_input').val();
        var msgName = $('#event-schedule-msgname_input').val();
        var saveKey = $('#event-schedule-savekey_input').val();
        var flowOverride = {};
        var editRows = $("#event-schedule-editTable .editRow");
        const enabledCacheProjectFiles = $("#eventEnabledCacheProjectFiles :checked").val() || false;
        for (var i = 0; i < editRows.length; ++i) {
            var row = editRows[i];
            var td = $(row).find('span');
            var key = $(td[0]).text();
            var val = $(td[1]).text();

            if (key && key.length > 0) {
                if (flowOverride[key] !== undefined) {
                    alert(wtssI18n.view.workflowParameterPro);
                    return
                }
                flowOverride[key] = val;
            }
        }
        var data = this.model.get("data");
        var disabledList = eventcheduleGatherDisabledNodes(data);

        var jobFailedRetryOptions = {};
        var tdFailedRetrys = document.getElementById("jobEventScheduleFailedRetryTable").tBodies[0];
        for (var row = 0; row < tdFailedRetrys.rows.length - 1; row++) {
            var tdFailedRetry = tdFailedRetrys.rows[row];
            var job = tdFailedRetry.cells[0].firstChild.value;
            var interval = tdFailedRetry.cells[1].firstChild.value;
            if (0 == interval || interval.indexOf("-") != -1) {
                alert(wtssI18n.view.rerunIntervalPro);
                return;
            }
            var count = tdFailedRetry.cells[2].firstChild.value;
            jobFailedRetryOptions[row] = job + "," + interval + "," + count;
        }

        var jobSkipFailedOptions = {};
        var jobSkipActionOptions = [];
        var tdSkipFaileds = document.getElementById("eventScheduleJobSkipFailedTable").tBodies[0];
        for (var row = 0; row < tdSkipFaileds.rows.length - 1; row++) {
            var tdSkipFailed = tdSkipFaileds.rows[row];
            var job = tdSkipFailed.cells[1].firstChild.value;
            jobSkipFailedOptions[row] = job;
            if (tdSkipFailed.cells[0].firstChild.checked) {
                jobSkipActionOptions.push(job);
            }
        }

        var executingData = this.model.get('executingData');

        var executingData = {
            projectId: executingData.projectId,
            project: executingData.projectName,
            ajax: "executeFlow",
            flow: executingData.flow,
            disabled: JSON.stringify(disabledList),
            failureEmailsOverride: failureEmailsOverride,
            successEmailsOverride: successEmailsOverride,
            failureAction: failureAction,
            failureEmails: failureEmails,
            successEmails: successEmails,
            notifyFailureFirst: notifyFailureFirst,
            notifyFailureLast: notifyFailureLast,
            flowOverride: flowOverride,
            jobFailedRetryOptions: jobFailedRetryOptions,
            failureAlertLevel: failureAlertLevel,
            successAlertLevel: successAlertLevel,
            jobSkipFailedOptions: jobSkipFailedOptions,
            jobSkipActionOptions: JSON.stringify(jobSkipActionOptions),
            token: token,
            topic: topic,
            msgName: msgName,
            saveKey: saveKey,
            enabledCacheProjectFiles: enabledCacheProjectFiles === "true",
        };

        var rerunAction = $('input[name=rerunActionEvent]:checked').val();
        executingData.rerunAction = rerunAction;

        //检查是否有重复的规则
        if (checkFiledRetryRule(jobFailedRetryOptions)) {
            alert(wtssI18n.view.errorRerunRulePro);
            return;
        }

        return executingData;
    },

    changeFlowInfo: function() {
        var successEmails = this.model.get("successEmails").length == 0 ? [loginUser] : this.model.get("successEmails");
        var failureEmails = this.model.get("failureEmails").length == 0 ? [loginUser] : this.model.get("failureEmails");
        var failureActions = this.model.get("failureAction") || 'finishPossible';
        var notifyFailure = this.model.get("notifyFailure");
        var flowParams = this.model.get("flowParams");
        var isRunning = this.model.get("isRunning");
        var concurrentOption = this.model.get("concurrentOption");
        var pipelineLevel = this.model.get("pipelineLevel");
        var pipelineExecutionId = this.model.get("pipelineExecution");
        var queueLevel = this.model.get("schedule-queueLevel");
        var nodeStatus = this.model.get("nodeStatus");
        var overrideSuccessEmails = this.model.get("failureEmailsOverride");
        var overrideFailureEmails = this.model.get("successEmailsOverride");
        var enableHistoryRecover = this.model.get("enableHistoryRecover");
        var rerunAction = this.model.get("rerunAction") || 'rerun';

        if (overrideSuccessEmails) {
            $('#event-schedule-override-success-emails').prop('checked', true);
        } else {
            $('#event-schedule-override-success-emails').prop('checked', false);
            $('#event-schedule-success-emails').attr('disabled', 'disabled');
        }
        if (overrideFailureEmails) {
            $('#event-schedule-override-failure-emails').prop('checked', true);
        } else {
            $('#event-schedule-override-failure-emails').prop('checked', false);
            $('#event-schedule-failure-emails').attr('disabled', 'disabled');
        }

        if (successEmails) {
            $('#event-schedule-success-emails').val(successEmails.join());
        }
        if (failureEmails) {
            $('#event-schedule-failure-emails').val(failureEmails.join());
        }
        if (failureActions) {
            $('#event-schedule-failure-action').val(failureActions);
        }

        if (notifyFailure.first || (!notifyFailure.first && !notifyFailure.last)) {
            $('#event-schedule-notify-failure-first').prop('checked', true).parent('.btn').addClass('active');
            $('#event-schedule-notify-failure-last').prop('checked', false).parent('.btn').removeClass('active');
        }
        if (notifyFailure.last) {
            $('#event-schedule-notify-failure-last').prop('checked', true).parent('.btn').addClass('active');
            $('#event-schedule-notify-failure-first').prop('checked', false).parent('.btn').removeClass('active');
        }
        // 初始化成功失败 告警级别
        $('#event-schedule-override-failure-alert-level').val('INFO');
        $('#event-schedule-override-success-alert-level').val('INFO');
        if (rerunAction) {
            $('input[value=' + rerunAction + '][name="rerunActionEvent"]').prop("checked", true);
        }

        const enabledCacheProjectFiles = this.model.get("enabledCacheProjectFiles") || false;
        $('#eventEnabledCacheProjectFiles input[value="' + enabledCacheProjectFiles + '"][name="eventCacheProject"]').prop('checked', true);

        // 初始化 失败重跑设置 失败跳过设置 工作流参数设置
        eventJobScheduleFailedRetryView.clearTable();
        eventScheduleJobSkipFailedView.clearJobSkipFailedTable();
        eventSchedulEditTableView.clearTable();
        if (flowParams) {
            for (var key in flowParams) {
                eventSchedulEditTableView.handleAddRow({
                    paramkey: key,
                    paramvalue: flowParams[key]
                });
            }
        }

    },

    show: function(data) {

        // 兼容不同地方变量名不同
        if (data.project) {
            data.projectName = data.project
        }
        if (data.projectName) {
            data.project = data.projectName
        }
        this.model.set({ "executingData": data })



        var projectName = data.project;
        var flowId = data.flow;
        var jobId = data.job;
        var scheduleFlowTitle = data.scheduleFlowTitle;

        // ExecId is optional
        var execId = data.execid;
        var exgraph = data.exgraph;

        this.projectName = projectName;
        this.flowId = flowId;

        var self = this;
        var loadCallback = function() {
            if (jobId) {
                self.showScheduleJob(scheduleFlowTitle, projectName, flowId, jobId, data.withDep);
            } else {
                self.showScheduleFlow(scheduleFlowTitle, projectName, flowId);
            }
        }

        // 清空缓存数据
        $('#event-work-flow-token_input').val("");
        $('#event-schedule-topic_input').html("");
        $('#event-schedule-msgname_input').html("");
        $('#event-schedule-savekey_input').val("");
        $('#event-schedule-comment').val("");
        // 检查是否有定时调度
        var scheduleId = data.scheduleId;
        if (scheduleId) {
            $('#event-work-flow-token_input').val(data.token);
            var topicOption = new Option(data.topic, data.topic)
            $('#event-schedule-topic_input').append(topicOption).trigger('change');
            var msgOption = new Option(data.msgName, data.msgName)
            $('#event-schedule-msgname_input').append(msgOption).trigger('change');
            $('#event-schedule-savekey_input').val(data.saveKey);
            $('#event-schedule-comment').val(data.comment);
        }

        var loadedId = eventSchedulableGraphModel.get("flowId");
        this.loadGraph(projectName, flowId, exgraph, loadCallback);
        this.loadFlowInfo(projectName, flowId, execId);
        this.loadSchedule();
        // this.loadEventAuthData(data.topic, data.msgName);

        $("#event-schedule-flow-div").show();
        $("#shedule-all-flow-div").hide();
        $('#event-schedule-flow-option').show();
        $('#event-schedule-failure-li').show();
        $('#job-cron-li').show();

        $('#eventEnabledCacheProjectFiles input[value="false"][name="eventCacheProject"]').prop('checked', true);
    },

    loadEventAuthData: function(topic, msgName) {
        var requestURL = "event/auth";
        var requestData = {
            "ajax": "loadEventAuthList",
        };
        var successHandler = function(data) {
            if (data.error) {
                console.log(data.error);
            } else {
                var eventAuthList = data.eventAuthList;
                $("#event-schedule-topic_input").find("option:selected").text("");
                $("#event-schedule-topic_input").empty();
                $("#event-schedule-msgname_input").find("option:selected").text("");
                $("#event-schedule-msgname_input").empty();
                var optionHtmlTopic = ""
                var optionHtmlMsgName = ""
                var eventDistinctTopicList = [];
                var eventDistinctMsgNameList = [];
                if (eventAuthList) {
                    for (var i = 0; i < eventAuthList.length; i++) {
                        var eventAuth = eventAuthList[i];
                        var eventTopic = eventAuth.topic;
                        var eventMsgName = eventAuth.msgName;
                        if (!eventDistinctTopicList.includes(eventTopic)) {
                            eventDistinctTopicList.push(eventTopic);
                        }
                        if (!eventDistinctMsgNameList.includes(eventMsgName)) {
                            eventDistinctMsgNameList.push(eventMsgName);
                        }
                    }
                }

                eventDistinctTopicList.sort();
                eventDistinctMsgNameList.sort();

                for (var i = 0; i < eventDistinctTopicList.length; i++) {
                    optionHtmlTopic += "<option>" + eventDistinctTopicList[i] + "</option>";
                }
                for (var i = 0; i < eventDistinctMsgNameList.length; i++) {
                    optionHtmlMsgName += "<option>" + eventDistinctMsgNameList[i] + "</option>";
                }
                optionHtmlTopic = filterXSS(optionHtmlTopic, { 'whiteList': { 'option': ['value'] } })
                optionHtmlMsgName = filterXSS(optionHtmlMsgName, { 'whiteList': { 'option': ['value'] } })
                $('#event-schedule-topic_input').append(optionHtmlTopic);
                $('#event-schedule-msgname_input').append(optionHtmlMsgName);
            }

            $('#event-schedule-topic_input').selectpicker('refresh');
            $('#event-schedule-msgname_input').selectpicker('refresh');
            $('#event-schedule-topic_input').selectpicker('render');
            $('#event-schedule-msgname_input').selectpicker('render');
            $('#event-schedule-topic_input').val(topic);
            $('#event-schedule-msgname_input').val(msgName);
            $('#event-work-flow-token_input').val(data.token);
            $('#event-schedule-comment').val(data.comment);
            $('#event-schedule-topic_input').selectpicker('refresh');
            $('#event-schedule-msgname_input').selectpicker('refresh');
        };

        $.ajax({
            url: requestURL,
            type: "get",
            async: false,
            data: requestData,
            dataType: "json",
            error: function(data) {
                console.log(data);
            },
            success: successHandler
        });
    },

    showScheduleFlow: function(scheduleFlowTitle, projectName, flowId) {
        $("#event-schedule-flow-panel-title").text(scheduleFlowTitle + flowId);
        this.showScheduleOptionPanel(flowId, projectName);

        // Triggers a render
        this.model.trigger("change:graph");
    },
    
    showScheduleJob: function(scheduleFlowTitle, projectName, flowId, jobId, withDep) {
        eventScheduleSideMenuDialogView.menuSelect($("#event-schedule-flow-option"));
        $("#event-schedule-flow-panel-title").text(scheduleFlowTitle + flowId);

        var data = this.model.get("data");
        var disabled = this.model.get("disabled");

        // Disable all, then re-enable those you want.
        eventScheduleDisableAll();

        var jobNode = data.nodeMap[jobId];
        eventScheduleTouchNode(jobNode, false);

        if (withDep) {
            eventScheduleRecurseAllAncestors(jobNode, false);
        }

        this.showScheduleOptionPanel(flowId, projectName);
        this.model.trigger("change:graph");
    },

    showScheduleOptionPanel: function(flowId, projectName) {
        this.getFlowRealJobList(flowId, projectName)
        eventScheduleSideMenuDialogView.menuSelect($("#event-schedule-flow-option"));
        $('#event-schedule-flow-panel').modal();
    },
    getFlowRealJobList: function(flowId, projectName) {
            var requestURL = "/manager?project=" + projectName;
    
            var model = this.model;
    
            var requestData = {
                "ajax": "fetchFlowRealJobLists",
                "flow": flowId,
            };
    
            var successHandler = function(data) {
                if (data.error) {
                    console.log(data.error.message);
                } else {
                    // var depList = data.webankDepartmentList;
                    // for(var i=0; i<depList.length; i++){
                    //   var department = depList[i];
                    //   $('#update-wtss-department-select').append("<option value='" + department.dpId + "'>" + department.dpName + "</option>");
                    // }
                    //this.jobList = data.jobList;
                    eventJobScheduleFailedRetryView.model.set({ "jobList": data.jobList });
                    eventScheduleJobSkipFailedView.model.set({ "jobList": data.jobList });
                }
            }
    
            $.ajax({
                url: requestURL,
                type: "get",
                async: true,
                data: requestData,
                dataType: "json",
                error: function(data) {
                    console.log(data);
                },
                success: successHandler
            });
    },
    hideScheduleOptionPanel: function() {
        $('#event-schedule-flow-panel').modal("hide");
    },

    loadFlowInfo: function(projectName, flowId, execId) {
        console.log("Loading flow " + flowId);
        fetchFlowInfo(this.model, projectName, flowId, execId);
    },

    loadGraph: function(projectName, flowId, exgraph, callback) {
        console.log("Loading flow " + flowId);
        var requestURL = "/executor";

        var graphModel = eventSchedulableGraphModel;
        // fetchFlow(this.model, projectName, flowId, true);
        var requestData = {
            "project": projectName,
            "ajax": "fetcheventscheduledflowgraph",
            "flow": flowId
        };
        var self = this;
        window.currentEventGraphFlowId = flowId
        var successHandler = function(data) {

            if (data.error) {
                window.currentEventGraphFlowId = ''
                messageBox.show(data.error, 'danger');
            } else {
                console.log("data fetched");
                // Auto disable jobs that are finished.
                eventScheduleDisableFinishedJobs(data);
                graphModel.addFlow(data);

                if (exgraph) {
                    self.assignInitialStatus(data, exgraph);
                }

                executingSvgGraphView = new azkaban.SvgGraphView({
                    el: $('#event-schedule-flow-executing-graph'),
                    model: graphModel,
                    render: false,
                    rightClick: {
                        "node": eventSchedulExpanelNodeClickCallback,
                        "edge": eventSchedulExpanelEdgeClickCallback,
                        "graph": eventSchedulExpanelGraphClickCallback
                    },
                    tooltipcontainer: "#event-schedule-svg-div-custom"
                });

                if (callback) {
                    callback.call(this);
                }
            }
        };
        $.get(requestURL, requestData, successHandler, "json");
    },

    assignInitialStatus: function(data, statusData) {
        // Copies statuses over from the previous execution if it exists.
        var statusNodeMap = statusData.nodeMap;
        var nodes = data.nodes;
        for (var i = 0; i < nodes.length; ++i) {
            var node = nodes[i];
            var statusNode = statusNodeMap[node.id];
            if (statusNode) {
                node.status = statusNode.status;
                if (node.type == "flow" && statusNode.type == "flow") {
                    this.assignInitialStatus(node, statusNode);
                }
            } else {
                // job wasn't present in this flow during the original execution
                node.noInitialStatus = true;
            }
        }
    },

    reloadWindow: function() {
        window.location.reload();
    },

    loadSchedule: function() {
        console.log("Loading Schedule Options ");
        showEventSchedulePanel();
    },

    scheduleFlow: function() {
        var scheduleURL = "/eventschedule"
        var scheduleData = this.getExecutionOptionData();

        console.log("Click eventScheduleFlow");

        var currentMomentTime = moment();
        var scheduleTime = currentMomentTime.utc().format('h,mm,A,') + "UTC";
        var scheduleDate = currentMomentTime.format('MM/DD/YYYY');

        scheduleData.scheduleId = $("#event-schedule-flow-id").text();
        scheduleData.ajax = "eventScheduleFlow";
        var executingData = this.model.get('executingData');
        scheduleData.projectName = executingData.projectName;
        scheduleData.token = $('#event-work-flow-token_input').val().trim();
        scheduleData.topic = $('#event-schedule-topic_input').val();
        scheduleData.msgName = $('#event-schedule-msgname_input').val();
        scheduleData.saveKey = $('#event-schedule-savekey_input').val();
        scheduleData.comment = $('#event-schedule-comment').val();
        if (!scheduleData.topic) {
            alert(wtssI18n.view.messageTopicReq);
            return;
        }
        if (!scheduleData.msgName) {
            alert(wtssI18n.view.messageNameReq);
            return;
        }
        var patt=/^\w*$/;
        if(scheduleData.saveKey && !patt.test(scheduleData.saveKey)){
          alert(wtssI18n.view.messageSavekeyReq);
          return;
        }

        // Currently, All cron expression will be based on server timezone.
        // Later we might implement a feature support cron under various timezones, depending on the future use cases.
        // scheduleData.cronTimezone = timezone;
        $("#event-schedule-flow-button").attr("disabled", true).addClass("button-disable");
        console.log("current Time = " + scheduleDate + "  " + scheduleTime);


        var successHandler = function(data) {
            $("#event-schedule-flow-button").attr("disabled", false).removeClass("button-disable");
            if (data.error) {
                alert(data.error);
                // flowEventScheduleDialogView.hideScheduleOptionPanel();
                // messageDialogView.show(wtssI18n.view.timingSchedulingFailed, data.error);
            } else {
                flowEventScheduleDialogView.hideScheduleOptionPanel();
                messageDialogView.show(wtssI18n.view.eventScheduling, data.message, function() {
                    if (window.location.pathname === "/schedule") {
                        eventScheduleView.handlePageChange()
                    } else {
                        window.location.href = '/schedule#event-schedule';
                    }
                });
            }
        };

        $.post(scheduleURL, scheduleData, successHandler, "json");
    },

    loadScheduleRunningInfo: function(data) {

        console.log("loadEventScheduleRunningInfo");

        var scheduleURL = "/eventschedule"

        var scheduleData = {
            ajax: "getScheduleByScheduleId",
            scheduleId: data.scheduleId
        }
        $("#event-schedule-flow-id").text(data.scheduleId);

        var successHandler = function(data) {
            if (data.error) {
                console.log("error, get event schedule info failed .")
            } else {
                var scheduleId = data.schedule.scheduleId;
                var projectName = data.schedule.projectName;
                var flowName = data.schedule.flowName;

                var successEmails = data.schedule.executionOptions.successEmails;
                var failureEmails = data.schedule.executionOptions.failureEmails;
                var failureActions = data.schedule.executionOptions.failureAction;
                var notifyOnFirstFailure = data.schedule.executionOptions.notifyOnFirstFailure;
                var notifyOnLastFailure = data.schedule.executionOptions.notifyOnLastFailure;
                var flowParams = data.schedule.executionOptions.flowParameters;
                var isRunning = data.schedule.executionOptions.isRunning;
                var concurrentOption = data.schedule.executionOptions.concurrentOption;
                var pipelineLevel = data.schedule.executionOptions.pipelineLevel;
                var queueLevel = data.schedule.executionOptions.pipelineLevel;
                var overrideSuccessEmails = data.schedule.executionOptions.successEmailsOverridden;
                var overrideFailureEmails = data.schedule.executionOptions.failureEmailsOverridden;
                var jobFailedRetryOptions = data.schedule.otherOptions.jobFailedRetryOptions;
                var failureAlertLevel = data.schedule.otherOptions.failureAlertLevel;
                var successAlertLevel = data.schedule.otherOptions.successAlertLevel;
                var jobSkipFailedOptions = data.schedule.otherOptions.jobSkipFailedOptions;
                var jobSkipActionOptions = data.schedule.otherOptions.jobSkipActionOptions;
                var rerunAction = data.schedule.executionOptions.rerunAction;

                if (overrideSuccessEmails && !$('#event-schedule-override-success-emails').is(':checked')) {
                    $('#event-schedule-override-success-emails').click();
                } else if (!$('#event-schedule-override-success-emails').is(':checked')) {
                    $('#event-schedule-success-emails').attr('disabled', 'disabled');
                }

                if (overrideFailureEmails && !$('#event-schedule-override-failure-emails').is(':checked')) {
                    $('#event-schedule-override-failure-emails').click();
                } else if (!$('#event-schedule-override-failure-emails').is(':checked')) {
                    $('#event-schedule-failure-emails').attr('disabled', 'disabled');
                }

                if (successEmails && successEmails.length > 0) {
                    $('#event-schedule-success-emails').val(successEmails.join());
                } else {
                    $('#event-schedule-success-emails').val(loginUser);
                }

                if (failureEmails && failureEmails.length > 0) {
                    $('#event-schedule-failure-emails').val(failureEmails.join());
                } else {
                    $('#event-schedule-failure-emails').val(loginUser);
                }

                if (failureActions) {
                    $('#event-schedule-failure-action').val(failureActions);
                }

                if (failureAlertLevel) {
                    $('#event-schedule-override-failure-alert-level').val(failureAlertLevel);
                }
                if (successAlertLevel) {
                    $('#event-schedule-override-success-alert-level').val(successAlertLevel);
                }

                if (notifyOnFirstFailure || (!notifyOnFirstFailure && !notifyOnLastFailure)) {
                    $('#event-schedule-notify-failure-first').prop('checked', true).parent('.btn').addClass('active');
                    $('#event-schedule-notify-failure-last').prop('checked', false).parent('.btn').removeClass('active');
                }
                if (notifyOnLastFailure) {
                    $('#event-schedule-notify-failure-last').prop('checked', true).parent('.btn').addClass('active');
                    $('#event-schedule-notify-failure-first').prop('checked', false).parent('.btn').removeClass('active');
                }

                if (rerunAction) {
                    $('input[value=' + rerunAction + '][name="rerunActionEvent"]').prop("checked", true);
                }

                /*if (concurrentOption) {
                  $('input[value=' + concurrentOption + '][name="concurrent"]').attr(
                    'checked', true);
                }
                if (pipelineLevel) {
                  $('#schedule-pipeline-level').val(pipelineLevel);
                }
                if (queueLevel) {
                  $('#schedule-queueLevel').val(queueLevel);
                }*/

                eventSchedulEditTableView.clearTable();
                if (flowParams && $(".editRow").length == 0) {
                    for (var key in flowParams) {
                        eventSchedulEditTableView.handleAddRow({
                            paramkey: key,
                            paramvalue: flowParams[key]
                        });
                    }
                }

                eventJobScheduleFailedRetryView.clearTable();

                //错误重试设置数据回填
                if (jobFailedRetryOptions) {
                    // 由于失败跳过下来数据拿的model里面的数据，当数据量大的时候数据没有更新拿的是上一个model值，故做延时处理
                    var failSetIntervalCount = 0;
                    var renderJobFailRow = setInterval(function() {
                        failSetIntervalCount++;
                        if (failSetIntervalCount >= 20) {
                            clearInterval(renderJobFailRow)
                            return;
                        }
                        var failList = eventJobScheduleFailedRetryView && eventJobScheduleFailedRetryView.model.get('jobList');
                        if (failList && eventJobScheduleFailedRetryView.flowId === window.currentEventGraphFlowId) {
                            for (var i = 0; i < jobFailedRetryOptions.length; i++) {
                                var retryOption = jobFailedRetryOptions[i];
                                eventJobScheduleFailedRetryView.handleAddRetryRow({
                                    job: retryOption["jobName"],
                                    interval: retryOption["interval"],
                                    count: retryOption["count"],
                                }, jobFailedRetryOptions);
                            }
                            clearInterval(renderJobFailRow)
                            console.log('clearInterval')
                        }
                    }, 200)
                }

                //清理旧数据
                eventScheduleJobSkipFailedView.clearJobSkipFailedTable();
                //错误跳过设置数据回填
                if (jobSkipFailedOptions) {
                    //循环填充选项
                    // 由于失败跳过下来数据拿的model里面的数据，当数据量大的时候数据没有更新拿的是上一个model值，故做延时处理
                    var skipSetIntervalCount = 0;
                    var renderSkipRow = setInterval(function() {
                        skipSetIntervalCount++;
                        if (skipSetIntervalCount >= 20) {
                            clearInterval(renderSkipRow)
                            return;
                        }
                        var skipList = executingSvgGraphView && executingSvgGraphView.model.get('data')
                        if (skipList && skipList.flowId === window.currentEventGraphFlowId) {
                            for (var i = 0; i < jobSkipFailedOptions.length; i++) {
                                var retryOption = jobSkipFailedOptions[i];
                                eventScheduleJobSkipFailedView.handleAddSkipRow({
                                    job: retryOption,
                                    jobSkipActionOptions: jobSkipActionOptions
                                }, jobSkipFailedOptions);
                            }
                            clearInterval(renderSkipRow)
                            console.log('clearInterval')
                        }
                    }, 200)
                }

                $('#schedule_id').val(scheduleId);
                $('#schedule_projectName').val(projectName);
                $('#schedule_flowName').val(flowName);
                $("#event-schedule-topic_input").val();
                $("#event-schedule-msgname_input").val();
                $("#event-schedule-savekey_input").val();
                $('#event-schedule-comment').val(data.schedule.comment);
                $('#event-work-flow-token_input').val(data.schedule.token);
                // updateScheduleExpression();

            }
        };

        $.post(scheduleURL, scheduleData, successHandler, "json");

    }
});

var eventSchedulEditTableView;
azkaban.EventScheduleEditTableView = Backbone.View.extend({
    events: {
        "click table #event-add-btn": "handleAddRow",
        "click table .editable": "handleEditColumn",
        "click table .remove-btn": "handleRemoveColumn"
    },

    initialize: function(setting) {},

    // 每次回填数据时清除旧数据
    clearTable: function() {
        $(".editable").remove();
        $(".editRow").remove();
    },

    handleAddRow: function(data) {
        var name = "";
        if (data.paramkey) {
            name = data.paramkey;
        }

        var value = "";
        if (data.paramvalue) {
            value = data.paramvalue;
        }

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
        $(nameData).text(name);
        var valueData = document.createElement("span");
        $(valueData).addClass("spanValue");
        $(valueData).text(value);

        $(tdName).append(nameData);
        $(tdName).addClass("editable");

        $(tdValue).append(valueData);
        $(tdValue).append(remove);
        $(tdValue).addClass("editable").addClass('value');

        $(tr).addClass("editRow");
        $(tr).append(tdName);
        $(tr).append(tdValue);

        $(tr).insertBefore(".addRow");
        return tr;
    },

    handleEditColumn: function(evt) {
        if (evt.target.tagName == "INPUT")
            return;
        var curTarget = evt.currentTarget;

        var text = $(curTarget).children(".spanValue").text();
        $(curTarget).empty();

        var input = document.createElement("input");
        $(input).attr("type", "text");
        $(input).addClass('form-control').addClass('input-sm');
        $(input).css("width", "100%");
        $(input).val(text);
        $(curTarget).addClass("editing");
        $(curTarget).append(input);
        $(input).focus();

        var obj = this;
        $(input).focusout(function(evt) {
            obj.closeEditingTarget(evt);
        });

        $(input).keypress(function(evt) {
            if (evt.which == 13) {
                obj.closeEditingTarget(evt);
            }
        });
    },

    handleRemoveColumn: function(evt) {
        var curTarget = evt.currentTarget;
        // Should be the table
        var row = curTarget.parentElement.parentElement;
        $(row).remove();
    },

    closeEditingTarget: function(evt) {
        var input = evt.currentTarget;
        var text = $(input).val();
        var parent = $(input).parent();
        $(parent).empty();

        var valueData = document.createElement("span");
        $(valueData).addClass("spanValue");
        $(valueData).text(text);

        if ($(parent).hasClass("value")) {
            var remove = document.createElement("div");
            $(remove).addClass("pull-right").addClass('remove-btn');
            var removeBtn = document.createElement("button");
            $(removeBtn).attr('type', 'button');
            $(removeBtn).addClass('btn').addClass('btn-xs').addClass('btn-danger');
            $(removeBtn).text('Delete');
            $(remove).append(removeBtn);
            $(parent).append(remove);
        }

        $(parent).removeClass("editing");
        $(parent).append(valueData);
    }
});

var eventScheduleSideMenuDialogView;
azkaban.EventScheduleSideMenuDialogView = Backbone.View.extend({
    events: {
        "click .menu-header": "menuClick"
    },

    initialize: function(settings) {
        var children = $(this.el).children();
        for (var i = 0; i < children.length; ++i) {
            var child = children[i];
            $(child).addClass("menu-header");
            var caption = $(child).find(".menu-caption");
            $(caption).hide();
        }
        this.menuSelect($("#flow-option"));
    },

    menuClick: function(evt) {
        this.menuSelect(evt.currentTarget);
    },

    menuSelect: function(target) {
        if ($(target).hasClass("active")) {
            return;
        }

        $(".side-panel").each(function() {
            $(this).hide();
        });


        // 当点击定时调度工作流时，显示隐藏流程图切换按钮
        if ((target[0] && target[0].id === "event-schedule-flow-option") || target.id === "event-schedule-flow-option") {
            $("#switching-event-schedule-flow-btn").show()
            $("#event-workflow-zoom-in").show()
        } else {
            $("#switching-event-schedule-flow-btn").hide()
            $("#event-workflow-zoom-in").hide()
            $("#event-workflow-zoom-out").hide()
        }


        $(".menu-header").each(function() {
            $(this).find(".menu-caption").slideUp("fast");
            $(this).removeClass("active");
        });

        $(target).addClass("active");
        $(target).find(".menu-caption").slideDown("fast");
        var panelName = $(target).attr("viewpanel");
        $("#" + panelName).show();
    }
});

var eventSchedulableGraphModel;

/**
 * Disable jobs that need to be disabled
 */
var eventScheduleDisableFinishedJobs = function(data) {
    for (var i = 0; i < data.nodes.length; ++i) {
        var node = data.nodes[i];

        if (node.status == "DISABLED" || node.status == "SKIPPED") {
            node.status = "READY";
            node.disabled = true;
            if (node.type == "flow") {
                setSubflowNodeDisabled(node);
            }
        } else if (node.status == "SUCCEEDED" || node.noInitialStatus || node.autoDisabled) {
            node.disabled = true;
            if (node.type == "flow") {
                setSubflowNodeDisabled(node);
            }
        } else {
            node.disabled = false;
            if (node.type == "flow") {
                eventScheduleDisableFinishedJobs(node);
            }
        }
    }
}

/**
 * Enable all jobs. Recurse
 */
var eventSchedulEnableAll = function() {
    eventScheduleRecurseTree(eventSchedulableGraphModel.get("data"), false, true);
    eventSchedulableGraphModel.trigger("change:disabled");
}

var eventScheduleDisableAll = function() {
    eventScheduleRecurseTree(eventSchedulableGraphModel.get("data"), true, true);
    eventSchedulableGraphModel.trigger("change:disabled");
}

var eventScheduleRecurseTree = function(data, disabled, recurse) {
    for (var i = 0; i < data.nodes.length; ++i) {
        var node = data.nodes[i];
        // autoDisabled 为true节点默认关闭
        if (!node.autoDisabled) {
            node.disabled = disabled;
        }

        if (node.type == "flow" && recurse) {
            eventScheduleRecurseTree(node, disabled, recurse);
        }
    }
}

// 打开关闭节点，如果节点类型为flow则需要打开关闭subfolw流程所有节点
var eventScheduleDisableSubflow = function(single, node, disable) {
    if (!node) return;
    if (single) {
        if (!node.autoDisabled) {
            node.disabled = disable;
        }
        eventCheckJobType(node, disable);
        if (!disable && !node.autoDisabled) {
            eventSchedulableEnableSubflow(node);
        }
    } else {
        var count = 0;
        for (var key in node) {
            if (count === 0 && !disable && !node[key].autoDisabled) {
                eventSchedulableEnableSubflow(node[key]);
            }
            if (!node[key].autoDisabled) {
                node[key].disabled = disable;
            }
            eventCheckJobType(node[key], disable);
            count++;
        }
    }
}

function eventCheckJobType(node, disable) {
    if (node.type == "flow") {
        eventScheduleRecurseTree(node, disable, true);
    }
}

var eventScheduleTouchNode = function(node, disable) {
    eventScheduleDisableSubflow(true, node, disable)
    eventSchedulableGraphModel.trigger("change:disabled");
}

var eventScheduleTouchParents = function(node, disable) {
    var inNodes = node.inNodes;

    eventScheduleDisableSubflow(false, inNodes, disable);

    eventSchedulableGraphModel.trigger("change:disabled");
}

// 启用工作流如果父流程节点为disable要先把父节点disable改成true
var eventSchedulableEnableSubflow = function(node) {
    var scheduleData = eventSchedulableGraphModel.get("data");
    var parantArr = [];
    var findNode = { isFind: false };
    eventEnableSubflowNodeTree(scheduleData, parantArr, node, findNode);
}


var eventEnableSubflowNodeTree = function(scheduleData, parantArr, node, findNode) {
    for (var i = 0; i < scheduleData.nodes.length; ++i) {
        if (findNode.isFind) {
            return
        }
        var item = scheduleData.nodes[i];
        if (item.nestedId === node.nestedId) {
            for (var j = 0; j < parantArr.length; j++) {
                if (!parantArr[j].autoDisabled) {
                    parantArr[j].disabled = false
                }
            }
            findNode.isFind = true
            return
        }
        if (item.type == "flow") {
            parantArr.push(item)
            eventEnableSubflowNodeTree(item, parantArr, node, findNode)
            parantArr.splice(parantArr.length - 1, 1)
        }
    }
}

var eventScheduleTouchChildren = function(node, disable) {
    var outNodes = node.outNodes;

    eventScheduleDisableSubflow(false, outNodes, disable)

    eventSchedulableGraphModel.trigger("change:disabled");
}

var eventScheduleTouchAncestors = function(node, disable) {
    var inNodes = node.inNodes;
    if (inNodes && !disable) {
        var key = Object.keys(inNodes)[0]
        eventSchedulableEnableSubflow(inNodes[key])
    }
    eventScheduleRecurseAllAncestors(node, disable);

    eventSchedulableGraphModel.trigger("change:disabled");
}

var eventScheduleTouchDescendents = function(node, disable) {
    var outNodes = node.outNodes;
    if (outNodes && !disable) {
        var key = Object.keys(outNodes)[0]
        eventSchedulableEnableSubflow(outNodes[key])
    }
    eventScheduleRecurseAllDescendents(node, disable);

    eventSchedulableGraphModel.trigger("change:disabled");
}

var eventcheduleGatherDisabledNodes = function(data) {
    var nodes = data.nodes;
    var disabled = [];

    for (var i = 0; i < nodes.length; ++i) {
        var node = nodes[i];
        if (node.disabled) {
            disabled.push(node.id);
        } else {
            if (node.type == "flow") {
                var array = eventcheduleGatherDisabledNodes(node);
                if (array && array.length > 0) {
                    disabled.push({ id: node.id, children: array });
                }
            }
        }
    }

    return disabled;
}

function eventScheduleRecurseAllAncestors(node, disable) {
    var inNodes = node.inNodes;
    if (inNodes) {
        for (var key in inNodes) {
            if (!inNodes[key].autoDisabled) {
                inNodes[key].disabled = disable;
            }
            eventCheckJobType(inNodes[key], disable);
            eventScheduleRecurseAllAncestors(inNodes[key], disable);
        }
    }
}

function eventScheduleRecurseAllDescendents(node, disable) {
    var outNodes = node.outNodes;
    if (outNodes) {
        for (var key in outNodes) {
            if (!outNodes[key].autoDisabled) {
                outNodes[key].disabled = disable;
            }
            eventCheckJobType(outNodes[key], disable);
            eventScheduleRecurseAllDescendents(outNodes[key], disable);
        }
    }
}
// type 为执行类型datachecker--所有datacheck  eventchecker--所有eventchecker/rmbsender(所有信号)  outer--所有外部信息  disabled--true关闭  false开启
var eventscheduleTouchTypecheck = function(type, disabled, labelNum) {
    // var flowName = flowEventScheduleDialogView.model.get('executingData');
    eventScheduleConditionRecurseTree(eventSchedulableGraphModel.get("data"), disabled, true, type, labelNum);
    eventSchedulableGraphModel.trigger("change:disabled");
    sessionStorage.clear('disableEventchecker')
}

var eventScheduleConditionRecurseTree = function(data, disable, recurse, type, labelNum) {
    // 关闭、打开父节点
    function eventScheduleRecurseTreeParent(currentNode, disable) {
        if (currentNode.parent && !disable && !currentNode.parent.autoDisabled) {
            currentNode.parent.disabled = disable
            eventScheduleRecurseTreeParent(currentNode.parent, disable)
        }
    }
    for (var i = 0; i < data.nodes.length; ++i) {
        var node = data.nodes[i];
        switch (type) {
            case 'datachecker':
                if (node.type === 'datachecker' && !node.autoDisabled) {
                    node.disabled = disable;
                    eventScheduleRecurseTreeParent(node, disable);
                }
                break;
            case 'eventchecker':
                // 关闭打开发送、接收信号； rmbsend只有发送信号
                var eventcheckerType = sessionStorage.getItem('disableEventchecker')
                var isDisabled = (eventcheckerType === 'SEND' && ((node.type === 'eventchecker' && node.eventCheckerType === eventcheckerType) || node.type === 'rmbsender')) ||
                    (eventcheckerType === 'RECEIVE' && node.type === 'eventchecker' && node.eventCheckerType === eventcheckerType)
                if (isDisabled && !node.autoDisabled) {
                    node.disabled = disable;
                    eventScheduleRecurseTreeParent(node, disable);
                }
                break;
            case 'outer':
                if ((node.outer === true || node.outer === 'true')  && !node.autoDisabled) {
                    node.disabled = disable;
                    eventScheduleRecurseTreeParent(node, disable);
                }
                break;
            case 'label':
                if (node.tag && node.tag.indexOf(labelNum) > -1  && !node.autoDisabled) {
                    node.disabled = disable;
                    eventScheduleRecurseTreeParent(node, disable);
                }
                break;
            default:
                break;
        }
        if (node.type === "flow" && recurse) {
            eventScheduleConditionRecurseTree(node, disable, recurse, type, labelNum);
        }
    }
}

var eventSchedulExpanelNodeClickCallback = function(event, model, node) {
    console.log("Node clicked callback");
    var jobId = node.id;
    var flowId = eventSchedulableGraphModel.get("flowId");
    var type = node.type;
    var executingData = flowEventScheduleDialogView.model.get('executingData')
    var menu;
    if (type == "flow") {
        var flowRequestURL = filterXSS("/manager?project=" + executingData.projectName +
            "&flow=" + node.flowId);
        if (node.expanded) {
            menu = [{
                    title: wtssI18n.common.collapseFlow,
                    callback: function() {
                        model.trigger("collapseFlow", node);
                    }
                },
                {
                    title: wtssI18n.common.openNewWindow,
                    callback: function() {
                        window.open(flowRequestURL);
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
                    title: wtssI18n.common.openNewWindow,
                    callback: function() {
                        window.open(flowRequestURL);
                    }
                }
            ];
        }
    } else {
        var executingData = flowEventScheduleDialogView.model.get('executingData')
        var requestURL = filterXSS("/manager?project=" + executingData.projectName + "&flow=" +
            flowId + "&job=" + jobId);
        menu = [{
            title: wtssI18n.common.openNewJob,
            callback: function() {
                window.open(requestURL);
            }
        }, ];
    }
    let openCloseMenu = [];
    if (!node.autoDisabled) {
        openCloseMenu = [
            {
                key: "openNode",
                title: wtssI18n.view.open,
                callback: function() {
                    eventScheduleTouchNode(node, false);
                },
                submenu: [{
                        title: wtssI18n.view.openParentNode,
                        callback: function() {
                            eventScheduleTouchParents(node, false);
                        }
                    },
                    {
                        title: wtssI18n.view.openPreviousNode,
                        callback: function() {
                            eventScheduleTouchAncestors(node, false);
                        }
                    },
                    {
                        title: wtssI18n.view.openChildNode,
                        callback: function() {
                            eventScheduleTouchChildren(node, false);
                        }
                    },
                    {
                        title: wtssI18n.view.openDescendantNodes,
                        callback: function() {
                            eventScheduleTouchDescendents(node, false);
                        }
                    },
                    {
                        title: wtssI18n.view.openDatacheck,
                        callback: function() {
                            eventscheduleTouchTypecheck('datachecker', false);
                        }
                    },
                    {
                        title: wtssI18n.view.openSendingSignal,
                        callback: function() {
                            //关闭发送信息
                            sessionStorage.setItem('disableEventchecker', 'SEND')
                            eventscheduleTouchTypecheck('eventchecker', false);
                        }
                    },
                    {
                        title: wtssI18n.view.openReceivingSignal,
                        callback: function() {
                            // 关闭接口信号
                            sessionStorage.setItem('disableEventchecker', 'RECEIVE')
                            eventscheduleTouchTypecheck('eventchecker', false);
                        }
                    },
                    {
                        title: wtssI18n.view.openOuterSignal,
                        callback: function() {
                            eventscheduleTouchTypecheck('outer', false);
                        }
                    },
                    {
                        title: wtssI18n.view.openAll,
                        callback: function() {
                            eventSchedulEnableAll();
                        }
                    }, {
                        key: "openNode",
                        title: wtssI18n.view.openLabel,
                        callback: function() {},
                        submenu: [{
                                title: wtssI18n.view.openLabel + '1',
                                callback: function() {
                                    eventscheduleTouchTypecheck('label', false, 1);
                                }
                            },
                            {
                                title: wtssI18n.view.openLabel + '2',
                                callback: function() {
                                    eventscheduleTouchTypecheck('label', false, 2);
                                }
                            },
                            {
                                title: wtssI18n.view.openLabel + '3',
                                callback: function() {
                                    eventscheduleTouchTypecheck('label', false, 3);
                                }
                            }
                        ]
                    }
                ]
            },
            {
                key: "closeNode",
                title: wtssI18n.view.close,
                callback: function() {
                    eventScheduleTouchNode(node, true)
                },
                submenu: [{
                        title: wtssI18n.view.closeParentNode,
                        callback: function() {
                            eventScheduleTouchParents(node, true);
                        }
                    },
                    {
                        title: wtssI18n.view.closePreviousNode,
                        callback: function() {
                            eventScheduleTouchAncestors(node, true);
                        }
                    },
                    {
                        title: wtssI18n.view.closeChildNode,
                        callback: function() {
                            eventScheduleTouchChildren(node, true);
                        }
                    },
                    {
                        title: wtssI18n.view.closeDescendantNodes,
                        callback: function() {
                            eventScheduleTouchDescendents(node, true);
                        }
                    },
                    {
                        title: wtssI18n.view.closeDatacheck,
                        callback: function() {
                            eventscheduleTouchTypecheck('datachecker', true);
                        }
                    },
                    {
                        title: wtssI18n.view.closeSendingSignal,
                        callback: function() {
                            //关闭发送信息
                            sessionStorage.setItem('disableEventchecker', 'SEND')
                            eventscheduleTouchTypecheck('eventchecker', true);
                        }
                    },
                    {
                        title: wtssI18n.view.closeReceivingSignal,
                        callback: function() {
                            // 关闭接口信号
                            sessionStorage.setItem('disableEventchecker', 'RECEIVE')
                            eventscheduleTouchTypecheck('eventchecker', true);
                        }
                    },
                    {
                        title: wtssI18n.view.closeOuterSignal,
                        callback: function() {
                            eventscheduleTouchTypecheck('outer', true);
                        }
                    },
                    {
                        title: wtssI18n.view.closeAll,
                        callback: function() {
                            eventScheduleDisableAll();
                        }
                    },
                    {
                        key: "closeNode",
                        title: wtssI18n.view.closeLabel,
                        callback: function() {},
                        submenu: [{
                                title: wtssI18n.view.closeLabel + '1',
                                callback: function() {
                                    eventscheduleTouchTypecheck('label', true, 1);
                                }
                            },
                            {
                                title: wtssI18n.view.closeLabel + '2',
                                callback: function() {
                                    eventscheduleTouchTypecheck('label', true, 2);
                                }
                            },
                            {
                                title: wtssI18n.view.closeLabel + '3',
                                callback: function() {
                                    eventscheduleTouchTypecheck('label', true, 3);
                                }
                            }
                        ]
                    }
                ]
            },
        ];
    }
    $.merge(menu, [
        { break: 1 },
        ...openCloseMenu,
        {
            title: wtssI18n.common.centerJob,
            callback: function() {
                model.trigger("centerNode", node);
            }
        },
        {
            title: wtssI18n.view.copyJobName,
            callback: function() {
                copyNodeName(node);
            }
        }
    ]);

    eventScheduleContextMenuView.show(event, menu);
}

var eventSchedulExpanelEdgeClickCallback = function(event) {
    console.log("Edge clicked callback");
}

var eventSchedulExpanelGraphClickCallback = function(event) {
    console.log("Graph clicked callback");
    var flowId = eventSchedulableGraphModel.get("flowId");
    var executingData = flowEventScheduleDialogView.model.get('executingData')
    var requestURL = filterXSS("/manager?project=" + executingData.projectName + "&flow=" +
        flowId);

    var menu = [{
            title: wtssI18n.common.openNewWindow,
            callback: function() {
                window.open(requestURL);
            }
        },
        { break: 1 },
        {
            title: wtssI18n.view.openAll,
            callback: function() {
                eventSchedulEnableAll();
            }
        },
        {
            title: wtssI18n.view.closeAll,
            callback: function() {
                eventScheduleDisableAll();
            }
        },
        { break: 1 },
        {
            title: wtssI18n.common.centerGraph,
            callback: function() {
                eventSchedulableGraphModel.trigger("resetPanZoom");
            }
        }
    ];

    eventScheduleContextMenuView.show(event, menu);
}

var eventScheduleContextMenuView;
$(function() {
    eventSchedulableGraphModel = new azkaban.GraphModel();
    flowEventScheduleDialogView = new azkaban.FlowEventScheduleDialogView({
        el: $('#event-schedule-flow-panel'),
        model: eventSchedulableGraphModel
    });

    eventScheduleSideMenuDialogView = new azkaban.EventScheduleSideMenuDialogView({
        el: $('#event-schedule-graph-options')
    });
    eventSchedulEditTableView = new azkaban.EventScheduleEditTableView({
        el: $('#event-schedule-editTable')
    });

    eventScheduleContextMenuView = new azkaban.ScheduleContextMenuView({
        el: $('#schedule-contextMenu')
    });

    $(document).keyup(function(e) {
        // escape key maps to keycode `27`
        if (e.keyCode == 27) {
            flowEventScheduleDialogView.hideScheduleOptionPanel();
            // flowEventScheduleDialogView.remove();
        }
    });

    jobEventScheduleRetryModel = new azkaban.JobEventScheduleRetryModel();

    eventJobScheduleFailedRetryView = new azkaban.EventJobScheduleFailedRetryView({
        el: $('#job-event-schedule-failed-retry-view'),
        model: jobEventScheduleRetryModel,
    });

    eventScheduleJobSkipFailedModel = new azkaban.EventScheduleJobSkipFailedModel();

    eventScheduleJobSkipFailedView = new azkaban.EventScheduleJobSkipFailedView({
        el: $('#event-schedule-job-skip-failed-view'),
        model: eventScheduleJobSkipFailedModel,
    });

    eventScheduleJobCronView = new azkaban.EventScheduleJobCronView({
        el: $('#job-cron-panel'),
        model: eventScheduleJobSkipFailedModel,
    });

    $("#switching-event-schedule-flow-btn").on('click', function() {
        var trimFlowName = !JSON.parse(sessionStorage.getItem('trimFlowName')); //标识是否剪切节点名称
        sessionStorage.setItem('trimFlowName', trimFlowName)
        seventScheduleReRenderWorflow(true) // 参数是否切换工作流
    })
    $("#event-workflow-zoom-in").on('click', function() {
        $(this).hide()
        $("#event-workflow-zoom-out").show()
        $('#event-schedule-flow-panel .modal-header').hide()
        $('#event-schedule-flow-panel .modal-footer').hide()
        $('#event-schedule-graph-options-box').hide()
        $('#event-schedule-graph-panel-box').removeClass('col-xs-8').addClass('col-xs-12')
        $('#event-schedule-flow-panel .modal-dialog')[0].style.width = "98%"
        $('#event-schedule-flow-executing-graph')[0].style.height = window.innerHeight * 0.88
        eventScheduleZoomInWorflow() // 参数是否切换工作流
    })
    $("#event-workflow-zoom-out").on('click', function() {
        $(this).hide()
        $("#event-workflow-zoom-in").show()
        $('#event-schedule-flow-panel .modal-header').show()
        $('#event-schedule-flow-panel .modal-footer').show()
        $('#event-schedule-graph-options-box').show()
        $('#event-schedule-graph-panel-box').removeClass('col-xs-12').addClass('col-xs-8')
        $('#event-schedule-flow-panel .modal-dialog')[0].style.width = "80%"
        $('#event-schedule-flow-executing-graph')[0].style.height = '500px'
        eventScheduleZoomInWorflow() // 参数是否切换工作流
    })

});

// 放大缩小重新收拢工作流，并居中
function eventScheduleZoomInWorflow() {
    executingSvgGraphView.collapseAllFlows()
    executingSvgGraphView.resetPanZoom()
}

function seventScheduleReRenderWorflow(switchingFlow) {
    var data = executingSvgGraphView.model.get('data') //获取流程图数据
    if (switchingFlow) {
        data.switchingFlow = true
    }
    $(executingSvgGraphView.mainG).empty() //清空流程图
    executingSvgGraphView.renderGraph(data, executingSvgGraphView.mainG)
}

function showEventSchedulePanel() {
    var timeZone = $('#scheduleTimeZoneID');
    timeZone.html(timezone);

    // updateScheduleOutput();

    //清空所有选项的值
    $("#event-schedule-clearCron").click(function() {
        resetEventLabelColor();
        $("#event-work-flow-token_input").val("");
        $("#event-schedule-topic_input").html("");
        $("#event-schedule-msgname_input").html("");
        $("#event-schedule-savekey_input").val("");
        $('#event-schedule-comment').val("");
    });
        // token长度应不少于8位，必须包含数字、大写字母、小写字母和特殊字符中的三种
     $("#event-work-flow-token_input").blur(function(e) {
        var val = e.target.value.trim();
        if (!val) {
            return;
        }
        var reg = /^(?![a-zA-Z]+$)(?![A-Z0-9]+$)(?![A-Z\W_!@#$%^&*`~()-+=]+$)(?![a-z0-9]+$)(?![a-z\W_!@#$%^&*`~()-+=]+$)(?![0-9\W_!@#$%^&*`~()-+=]+$)[a-zA-Z0-9\W_!@#$%^&*`~()-+=]/;
        if (!reg.test(val) || val.length < 8) {
            messageBox.show(wtssI18n.view.tokenPro, 'warning');
            e.target.value = '';
        }
    });
    $("#event-schedule-topic_input").click(function() {
        resetEventLabelColor();
        $("#event-schedule-topic_label").css("color", "red");
    });

    $("#event-schedule-topic_input").select2({
        placeholder: wtssI18n.view.selectTopic,
        ajax: {
            url: "event/auth",
            type: "get",
            dataType: "json",
            delay: 250,
            data: function(params) {
                var query = {
                    ajax: "loadEventAuthSearchList",
                    searchKey: "topic",
                    searchTerm: params.term,
                    page: params.page || 1,
                    size: 10,
                }
                return query;
            },
            processResults: function(data, params) {
                params.page = params.page || 1;
                var eventAuthList = data.eventAuthList;
                var eventDistinctTopicList = [];
                var eventTopicArr = []
                if (eventAuthList) {
                    for (var i = 0; i < eventAuthList.length; i++) {
                        var eventAuth = eventAuthList[i];
                        var eventTopic = eventAuth.topic;
                        if (!eventTopicArr.includes(eventTopic)) {
                            eventDistinctTopicList.push({ id: eventTopic, text: eventTopic });
                            eventTopicArr.push(eventTopic)
                        }
                    }
                }
                eventDistinctTopicList.sort();
                return {
                    results: eventDistinctTopicList,
                    pagination: {
                        more: (params.page * 10) < data.total
                    }    
                };
            }
        },
        minimumInputLength: 3
    });

    $("#event-schedule-msgname_input").select2({
        placeholder: wtssI18n.view.selectMsgName,
        ajax: {
            url: "event/auth",
            type: "get",
            dataType: "json",
            delay: 250,
            data: function(params) {
                var query = {
                    ajax: "loadEventAuthSearchList",
                    searchKey: "msg_name",
                    searchTerm: params.term,
                    page: params.page || 1,
                    size: 10,
                }
                return query;
            },
            processResults: function(data, params) {
                params.page = params.page || 1;
                var eventAuthList = data.eventAuthList;
                var eventDistinctMsgNameList = [];
                var eventMsgNameArr = []
                if (eventAuthList) {
                    for (var i = 0; i < eventAuthList.length; i++) {
                        var eventAuth = eventAuthList[i];
                        var eventMsgName = eventAuth.msgName;
                        if (!eventMsgNameArr.includes(eventMsgName)) {
                            eventDistinctMsgNameList.push({ id: eventMsgName, text: eventMsgName });
                            eventMsgNameArr.push(eventMsgName)
                        }
                    }
                }

                eventDistinctMsgNameList.sort();
                return {
                    results: eventDistinctMsgNameList,
                    pagination: {
                        more: (params.page * 10) < data.total
                    }          
                };
            }
        },
        minimumInputLength: 3
    });

    $("#event-schedule-msgname_input").click(function() {
        resetEventLabelColor();
        $("#event-schedule-msgname_label").css("color", "red");
    });

    $("#event-schedule-savekey_input").click(function() {
        resetEventLabelColor();
        $("#event-schedule-savekey_label").css("color", "red");
    });


}

function resetEventLabelColor() {
    $("#event-schedule-topic_label").css("color", "black");
    $("#event-schedule-msgname_label").css("color", "black");
    $("#event-schedule-savekey_label").css("color", "black");
    $("#schedule-mon_label").css("color", "black");
    $("#schedule-dow_label").css("color", "black");
    $("#schedule-year_label").css("color", "black");
}

//用于保存浏览数据，切换页面也能返回之前的浏览进度。
var jobEventScheduleRetryModel;
azkaban.JobEventScheduleRetryModel = Backbone.Model.extend({});

var eventJobScheduleFailedRetryView;
azkaban.EventJobScheduleFailedRetryView = Backbone.View.extend({
    events: {
        "click table #add-event-schedule-failed-retry-btn": "handleAddRetryRow",
        //"click table .editable": "handleEditColumn",
        "click table .remove-btn": "handleRemoveColumn"
    },

    initialize: function(setting) {},

    // 每次回填数据时清除旧数据
    clearTable: function() {
        $("#jobEventScheduleFailedRetryTable .jobRetryTr").remove();
    },

    //flow 执行成功错误告警设置
    handleAddRetryRow: function(data) {
        var job = "";
        if (data.job) {
            job = data.job;
        }
        var jobList = this.model.get("jobList");

        var retryTr = $("#jobEventScheduleFailedRetryTable tr").length - 1;
        if (jobList && retryTr == jobList.length) {
            $('#add-event-schedule-failed-retry-btn').attr('disabled', 'disabled');
        }


        var failedRetryTable = document.getElementById("jobEventScheduleFailedRetryTable").tBodies[0];
        var trRetry = failedRetryTable.insertRow(failedRetryTable.rows.length - 1);

        $(trRetry).addClass('jobRetryTr');
        //设置失败重跑 job 名称
        var cJob = trRetry.insertCell(-1);
        $(cJob).attr("style", "width: 65%");
        //var tr = $("<tr></tr>");
        //var td = $("<td></td>");

        var jobSelectId = "event-schedule-job-select" + failedRetryTable.rows.length;

        var idSelect = $("<select></select>");
        idSelect.attr("class", "form-control");
        idSelect.attr("id", jobSelectId);
        idSelect.attr("style", "width: 100%");
        // for (var i=0; i < jobList.length; i++) {
        //   idSelect.append("<option value='" + jobList[i] + "'>" + jobList[i] + "</option>");
        // }
        $(cJob).append(idSelect);

        //设置失败重跑时间间隔
        var cInterval = trRetry.insertCell(-1);
        $(cInterval).attr("style", "width: 10%");
        var retryInterval = $("<input></input>");
        retryInterval.attr("class", "form-control");
        retryInterval.attr("id", "interval-input");
        retryInterval.attr("type", "number");
        retryInterval.attr("value", data.interval);
        retryInterval.attr("step", "1");
        retryInterval.attr("min", "1");
        retryInterval.attr("max", "86400");
        $(cInterval).append(retryInterval);

        //设置失败重跑次数
        var cCount = trRetry.insertCell(-1);
        $(cCount).attr("style", "width: 15%");
        var idSelect = $("<select></select>");
        idSelect.attr("class", "form-control");
        idSelect.attr("id", "count-select");
        for (var i = 1; i < 4; i++) {
            idSelect.append("<option value='" + i + "'>" + i + ' ' + wtssI18n.view.times + "</option>");
        }
        $(cCount).append(idSelect).val(data.count);

        //删除按钮
        var cDelete = trRetry.insertCell(-1);
        var remove = document.createElement("div");
        $(remove).addClass("center-block");
        var removeBtn = document.createElement("button");
        $(removeBtn).attr('type', 'button');
        $(removeBtn).addClass('btn btn-sm remove-btn btn-danger');
        $(removeBtn).text('Delete');
        $(remove).append(removeBtn);
        cDelete.appendChild(remove);

        this.loadFlowJobListData(jobSelectId, jobEventScheduleRetryModel.get("flowId"));
        //回显新增的数据,如果是新增一行,就没有回显
        if (job) {
            $("#" + jobSelectId).val(job).select2();
        }
        return trRetry;
    },

    handleRemoveColumn: function(evt) {
        var curTarget = evt.currentTarget;
        // Should be the table
        var row = curTarget.parentElement.parentElement.parentElement;
        $(row).remove();

        var jobList = this.model.get("jobList");

        var retryTr = $("#jobEventScheduleFailedRetryTable tr").length - 2;
        if (retryTr < jobList.length) {
            $('#add-event-schedule-failed-retry-btn').removeAttr('disabled');
        }

    },
    setFlowID: function(flowId, projectName) {
        this.flowId = flowId;
        jobEventScheduleRetryModel.set("flowId", flowId);
        eventJobScheduleFailedRetryView.model.set('jobList', null)
    },

    loadFlowJobListData: function(selectId, flowId) {
        var executingData = flowEventScheduleDialogView.model.get('executingData');
        $("#" + selectId + "").select2({
            placeholder: wtssI18n.view.selectTaskPro, //默认文字提示
            multiple: false,
            width: 'resolve',
            //tags: true,//允许手动添加
            //allowClear: true,//允许清空
            escapeMarkup: function(markup) {
                return markup;
            }, //自定义格式化防止XSS注入
            minimumInputLengt: 1, //最少输入多少字符后开始查询
            formatResult: function formatRepo(repo) {
                return repo.text;
            }, //函数用来渲染结果
            formatSelection: function formatRepoSelection(repo) {
                return repo.text;
            }, //函数用于呈现当前的选择
            data: this.model.get("jobList"),
            language: 'zh-CN',

        });
    },

});

var eventScheduleJobCronView;
azkaban.EventScheduleJobCronView = Backbone.View.extend({
    events: {
        "click #event-add-job-cron-btn": "handleAddRow",
        //"click table .editable": "handleEditColumn",
        "click #event-job-cron-table .remove-btn": "handleRemoveColumn",
        "blur #event-job-cron-table .cron": "showTop"
    },

    initialize: function(setting) {},
    showTop: function(evt) {
        console.log(evt);
        updateJobCronExpression(evt);
    },
    handleAddRow: function(evt) {
        var jobList = this.model.get("jobList");
        var retryTr = $("#event-job-cron-table tr").length - 1;
        if (jobList && retryTr == jobList.length) {
            $('#event-add-job-cron-btn').attr('disabled', 'disabled');
        }
        var skipFailedTable = document.getElementById("event-job-cron-table").tBodies[0];
        var trSkip = skipFailedTable.insertRow(skipFailedTable.rows.length - 1);
        $(trSkip).addClass('scheduleJobSkipTr');
        //设置失败重跑 job 名称
        var cJob = trSkip.insertCell(-1);
        var jobSelectId = "job-cron-select" + skipFailedTable.rows.length;
        var idSelect = $("<select></select>");
        idSelect.attr("class", "form-control");
        idSelect.attr("id", jobSelectId);
        idSelect.attr("style", "width: 100%");
        $(cJob).append(idSelect);

        //cron input
        /**
         <div class="col-sm-offset-1 col-sm-4">
           <div class="input-box">
            <input type="text" class="form-control">
            <span class="unit">0</span>
           </div>
         </div>
         * @type {HTMLElement}
         */
        var cronExpress = trSkip.insertCell(-1);
        var div1 = $('<div class="col-sm-offset-1 col-sm-4"/>');
        var div2 = $('<div class="input-box"/>');
        var input = $('<input type="text" class="form-control cron" style="padding-left: 48px;"/>');
        var span = $('<span class="unit">0 0 0</span>');
        $(div2).append($(input));
        $(div2).append($(span));
        $(div1).append($(div2));
        $(cronExpress).append(div1);

        //删除按钮
        var cDelete = trSkip.insertCell(-1);
        var remove = document.createElement("div");
        $(remove).addClass("center-block");
        var removeBtn = document.createElement("button");
        $(removeBtn).attr('type', 'button');
        $(removeBtn).addClass('btn btn-sm remove-btn btn-danger');
        $(removeBtn).text('Delete');
        $(remove).append(removeBtn);
        cDelete.appendChild(remove);
        this.loadFlowJobListData(jobSelectId, this.model.get("flowId"));
    },
    handleRemoveColumn: function(evt) {
        var curTarget = evt.currentTarget;
        // Should be the table
        var row = curTarget.parentElement.parentElement.parentElement;
        $(row).remove();
    },
    loadFlowJobListData: function(selectId, flowId) {
        var executingData = flowEventScheduleDialogView.model.get('executingData');
        $("#" + selectId + "").select2({
            placeholder: wtssI18n.view.jobPro, //默认文字提示
            multiple: false,
            width: 'resolve',
            //tags: true,//允许手动添加
            //allowClear: true,//允许清空
            escapeMarkup: function(markup) {
                return markup;
            }, //自定义格式化防止XSS注入
            minimumInputLengt: 1, //最少输入多少字符后开始查询
            formatResult: function formatRepo(repo) {
                return repo.text;
            }, //函数用来渲染结果
            formatSelection: function formatRepoSelection(repo) {
                return repo.text;
            }, //函数用于呈现当前的选择
            ajax: {
                type: 'GET',
                url: "/manager?project=" + executingData.projectName,
                dataType: 'json',
                delay: 250,
                data: function(params) {
                    var query = {
                        ajax: "fetchJobNestedIdList",
                        flow: flowId,
                        serach: params.term,
                        // page: params.page || 1,
                        // pageSize: 20,
                    }
                    return query;
                },
                processResults: function(data, params) {
                    params.page = params.page || 1;
                    return {
                        results: data.jobList,
                        // pagination: {
                        //   more: (params.page * 20) < data.webankUserTotalCount
                        // }
                    }
                },
                cache: true
            },
            language: 'zh-CN',

        });
    },
});

//用于保存浏览数据，切换页面也能返回之前的浏览进度。
var eventScheduleJobSkipFailedModel;
azkaban.EventScheduleJobSkipFailedModel = Backbone.Model.extend({});

var eventScheduleJobSkipFailedView;
azkaban.EventScheduleJobSkipFailedView = Backbone.View.extend({
    events: {
        "click table #add-event-schedule-skip-failed-btn": "handleAddSkipRow",
        //"click table .editable": "handleEditColumn",
        "click table .remove-btn": "handleRemoveColumn"
    },

    initialize: function(setting) {},

    //每次回填数据时清除旧数据
    clearJobSkipFailedTable: function() {
        $("#event-schedule-job-skip-failed-view .scheduleJobSkipTr").remove();
    },

    //flow 执行成功错误告警设置
    handleAddSkipRow: function(data) {
        //选中的任务名称
        var jobName = data.job ? data.job : "";
        var jobList = this.model.get("jobList");

        var retryTr = $("#eventScheduleJobSkipFailedTable tr").length - 1;
        if (jobList && retryTr == jobList.length) {
            $('#add-event-schedule-skip-failed-btn').attr('disabled', 'disabled');
        }


        var skipFailedTable = document.getElementById("eventScheduleJobSkipFailedTable").tBodies[0];
        var trSkip = skipFailedTable.insertRow(skipFailedTable.rows.length - 1);

        $(trSkip).addClass('scheduleJobSkipTr');

        var jobSkipActionId = "job-skip-action" + skipFailedTable.rows.length;
        var cSkipCheck = trSkip.insertCell(-1);
        var skipCheck = document.createElement("input");
        skipCheck.type = "checkbox";
        $(skipCheck).attr("style", "width: 70px");
        $(skipCheck).attr("id", jobSkipActionId);
        cSkipCheck.appendChild(skipCheck);

        //设置失败重跑 job 名称
        var cJob = trSkip.insertCell(-1);
        $(cJob).attr("style", "width: 80%");

        //var tr = $("<tr></tr>");
        //var td = $("<td></td>");

        var jobSelectId = "event-schedule-job-skip-failed-select" + skipFailedTable.rows.length;

        var idSelect = $("<select></select>");
        idSelect.attr("class", "form-control");
        idSelect.attr("id", jobSelectId);
        idSelect.attr("style", "width: 100%");
        // for (var i=0; i < jobList.length; i++) {
        //   idSelect.append("<option value='" + jobList[i] + "'>" + jobList[i] + "</option>");
        // }
        $(cJob).append(idSelect);

        //删除按钮
        var cDelete = trSkip.insertCell(-1);
        var remove = document.createElement("div");
        $(remove).addClass("center-block");
        var removeBtn = document.createElement("button");
        $(removeBtn).attr('type', 'button');
        $(removeBtn).addClass('btn btn-sm remove-btn btn-danger');
        $(removeBtn).text('Delete');
        $(remove).append(removeBtn);
        cDelete.appendChild(remove);

        this.loadFlowJobListData(jobSelectId, eventScheduleJobSkipFailedModel.get("flowId"));
        if (jobName) {
            $("#" + jobSelectId).val(jobName).select2();
            $("#" + jobSkipActionId).get(0).checked = data.jobSkipActionOptions ? data.jobSkipActionOptions.includes(jobName) : false;
        }
        return trSkip;
    },

    handleRemoveColumn: function(evt) {
        var curTarget = evt.currentTarget;
        // Should be the table
        var row = curTarget.parentElement.parentElement.parentElement;
        $(row).remove();

        var jobList = this.model.get("jobList");

        var retryTr = $("#eventScheduleJobSkipFailedTable tr").length - 2;
        if (retryTr < jobList.length) {
            $('#add-event-schedule-skip-failed-btn').removeAttr('disabled');
        }

    },

    setFlowID: function(flowId, projectName) {
        this.flowId = flowId;
        eventScheduleJobSkipFailedModel.set("flowId", flowId);
    },
    getskipFailedJobList() {
        var data = executingSvgGraphView.model.get('data')
        var nodes = data.nodes
        var alljobs = 'all_jobs ' + data.flowId
        var subflowArr = [{
            id: alljobs,
            text: alljobs
        }]
        var jobArr = []
        recursionNode(nodes)

        function recursionNode(nodes) {
            for (var i = 0; i < nodes.length; i++) {
                var node = nodes[i]
                if (node.type === 'flow') {
                    subflowArr.push({
                        id: 'subflow:' + node.flowId,
                        text: 'subflow:' + node.flowId
                    })
                    recursionNode(node.nodes)
                } else {
                    jobArr.push({
                        id: node.id,
                        text: node.id
                    })
                }
            }
        }
        subflowArr.push.apply(subflowArr, jobArr)
        return subflowArr
    },
    loadFlowJobListData: function(selectId, flowId, projectName) {
        var data = this.getskipFailedJobList()
            // var executingData = flowEventScheduleDialogView.model.get('executingData');
        $("#" + selectId + "").select2({
            placeholder: wtssI18n.view.selectTaskPro, //默认文字提示
            multiple: false,
            width: 'resolve',
            //tags: true,//允许手动添加
            //allowClear: true,//允许清空
            escapeMarkup: function(markup) {
                return markup;
            }, //自定义格式化防止XSS注入
            minimumInputLengt: 1, //最少输入多少字符后开始查询
            formatResult: function formatRepo(repo) {
                return repo.text;
            }, //函数用来渲染结果
            formatSelection: function formatRepoSelection(repo) {
                return repo.text;
            }, //函数用于呈现当前的选择
            data: data,
            language: 'zh-CN',

        });
    },


});