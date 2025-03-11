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

var projectName;
var projectId;
var fromShowDataProjectName;
var fromShowDataProjectId;
var flowExecuteDialogView;
var executingSvgGraphView;
var executeJobListView;
azkaban.FlowExecuteDialogView = Backbone.View.extend({
    events: {
        "click .closeExecPanel": "hideExecutionOptionPanel",
        "click #schedule-btn": "scheduleClick",
        "click #execute-btn": "handleExecuteFlow",
        "click #history-recover-btn": "handleHistoryRecover",
    },

    initialize: function(settings) {
        this.model.bind('change:flowinfo', this.changeFlowInfo, this);
        $("#override-success-emails").click(function(evt) {
            if ($(this).is(':checked')) {
                $('#success-emails').attr('disabled', null);
                $('#custom-success-alert').attr('disabled', null);
            } else {
                $('#success-emails').attr('disabled', "disabled");
                $('#custom-success-alert').attr('disabled', "disabled");
            }
        });

        $("#flowRetryAlertChecked").click(function(evt) {
            if ($(this).is(':checked')) {
                $('#alertMsg').attr('disabled', null);
            } else {
                $('#alertMsg').attr('disabled', "disabled");
            }
        });

        $("#override-failure-emails").click(function(evt) {
            if ($(this).is(':checked')) {
                $('#failure-emails').attr('disabled', null);
                $('#custom-failure-alert').attr('disabled', null);
            } else {
                $('#failure-emails').attr('disabled', "disabled");
                $('#custom-failure-alert').attr('disabled', "disabled");
            }
        });

        $('#datetimebegin').datetimepicker({
            format: 'YYYY/MM/DD',
            // cancel max current date limit
            maxDate: new Date()
        });
        $('#datetimeend').datetimepicker({
            format: 'YYYY/MM/DD',
            // cancel max current date limit
            maxDate: new Date()
        });
        $('#datetimebegin').on('change.dp', function(e) {
            $('#datetimeend').data('DateTimePicker').setStartDate(e.date);
        });
        $('#datetimeend').on('change.dp', function(e) {
            $('#datetimebegin').data('DateTimePicker').setEndDate(e.date);
        });

        $('#datetimebegin').val("");
        $('#datetimeend').val("");

        $('#executeTimeBegin').datetimepicker({
            format: 'HH:mm'
        });
        $('#executeTimeEnd').datetimepicker({
            format: 'HH:mm'
        });
        $('#timeout-second').datetimepicker({
            format: 'HH:mm'
        });

        // 自然日重置
        $("#resetRunDateTime").click(function() {
                $('#runDateTime').val('');
                updateRecoverTimeTopTen();
            })
         // 跳过自然日重置
         $("#skipResetRunDateTime").click(function() {
            $('#skipRunDateTime').val('');
            updateRecoverTimeTopTen();
        })
            //历史重跑开关点击事件添加
        $("#enable-history-recover").click(function(evt) {
            if ($(this).is(':checked')) {
                $('#datetimebegin').attr('disabled', null);
                $('#datetimeend').attr('disabled', null);
                $('#executeTimeBegin').attr('disabled', null);
                $('#executeTimeEnd').attr('disabled', null);
                $('#repeat-num').attr('disabled', null);
                $('#reRunTimeInterval').attr('disabled', null);
                $('#recover-interval').attr('disabled', null);
                $('#recover-error-option').attr('disabled', null);
                $('#runDateTime').attr('disabled', null);
                $('#skipRunDateTime').attr('disabled', null);
            } else {
                $('#datetimebegin').attr('disabled', 'disabled');
                $('#datetimeend').attr('disabled', 'disabled');
                $('#executeTimeBegin').attr('disabled', 'disabled');
                $('#executeTimeEnd').attr('disabled', 'disabled');
                $('#repeat-num').attr('disabled', 'disabled');
                $('#reRunTimeInterval').attr('disabled', 'disabled');
                $('#recover-interval').attr('disabled', 'disabled');
                $('#recover-error-option').attr('disabled', 'disabled');
                $('#runDateTime').attr('disabled', 'disabled');
                $('#skipRunDateTime').attr('disabled', 'disabled');
            }
        });
        // 重跑间隔分钟
        $('#reRunTimeInterval').blur((e) => {
            const val = e.target.value ? parseInt(e.target.value) : 0
            if (val) {
                $("#recover-Concurrent-option").attr('disabled', 'disabled').val('1');
            } else {
                $("#recover-Concurrent-option").attr('disabled', null);
            }
        });
        // 点击历史重跑执行完成告警，告警列表启用
        $("#enable-history-recover-finished-alert").click(function(evt) {
            if ($(this).is(':checked')) {
                $('#flow-history-rerun-finish-emails').attr('disabled', null);
            } else {
                $('#flow-history-rerun-finish-emails').attr('disabled', "disabled");
            }
        })

        //循环执行开关点击事件添加
        $("#enable-cycle-execution").click(function() {
            if ($(this).is(":checked")) {
                $("#cycle-error-option").attr('disabled', null);
            } else {
                $("#cycle-error-option").attr('disabled', 'disabled');
            }
        });

        //前端校验循环执行和历史补采互斥
        $('li[viewpanel=cycle-execution-panel]').click(function() {
            if ($("#enable-history-recover").is(":checked")) {
                $("#enable-cycle-execution").attr("disabled", "disabled");
                $("#enable-cycle-execution").prop("checked", false);
                $("#cycle-error-option").attr('disabled', 'disabled');
            } else {
                $("#enable-cycle-execution").attr("disabled", null);
                if($("#enable-cycle-execution").is(":checked")){
                    $("#cycle-error-option").attr('disabled', null);
                }else{
                    $("#cycle-error-option").attr('disabled', 'disabled');
                }

            }
        })
    },

    render: function() {},
    //校验超时告警设置是否正确
    checkTimeoutAlertSetting: function() {
        if (!$("#flow-timeout-option").is(":checked")) {
            console.log("unset")
            return true;
        }
        if ($("#timeout-email").is(":checked") == false && $("#timeout-killflow").is(":checked") == false) {
            alert(wtssI18n.view.workflowChoosePro);
            return false;
        }
        if ($("#timeout-email").is(":checked")) {
            if ($('#timeout-slaEmails').val() == undefined || $('#timeout-slaEmails').val() == "") {
                alert(wtssI18n.view.alarmAddressPro);
                return false;
            }
        }
        var t = $("#timeout-second").val();
        var re = /^\d{2}:\d{2}$/;
        if (!re.test(t)) {
            alert(wtssI18n.view.timeoutPro);
            return false;
        } else {
            var h = parseInt(t.split(":")[0]);
            var m = parseInt(t.split(":")[1]);
            if (!(h < 24) || !(m < 60)) {
                alert(wtssI18n.view.timeoutPro);
                return false;
            }
        }
        return true;
    },
    getJobOutputParamDate: function() {
        var jobOutputParam = {};
        var editRows = $("#jobOutputParam-editTable .editRow");
        for (var i = 0; i < editRows.length; ++i) {
            var row = editRows[i];
            var td = $(row).find('span');
            var key = $(td[0]).text();
            var val = $(td[1]).text();

            if (key && key.length > 0) {
                jobOutputParam[key] = val;
            }
        }
        return jobOutputParam;
    },

    getExecutionOptionData: function() {
        var failureAction = $('#failure-action').val();
        var failureEmails = $('#failure-emails').val();
        var failedMessageContent = $('#custom-failure-alert').val();
        var successEmails = $('#success-emails').val();
        var successMessageContent = $('#custom-success-alert').val();
        var notifyFailureFirst = $('#notify-failure-first').parent().attr('class').search('active') != -1 ? true : false;
        var notifyFailureLast = !notifyFailureFirst;
        var failureEmailsOverride = $("#override-failure-emails").is(':checked');
        var successEmailsOverride = $("#override-success-emails").is(':checked');

        // 并发执行顺序
        var taskDistributeMethod = $("#taskDistributeMethod :checked").val() || "uniform";
        //告警级别选择
        var failureAlertLevel = $('#override-failure-alert-level').val();
        var successAlertLevel = $('#override-success-alert-level').val();
        const reRunTimeInterval = $("#reRunTimeInterval").val();
        const enabledCacheProjectFiles = $("#enabledCacheProjectFiles :checked").val() || false;
        var flowOverride = {};
        var editRows = $("#flow-execute-dialog-editTable .editRow");
        for (var i = 0; i < editRows.length; ++i) {
            var row = editRows[i];
            var td = $(row).find('span');
            var key = $(td[0]).text();
            var val = $(td[1]).text();

            if (key && key.length > 0) {
                flowOverride[key] = val;
            }
        }

        var data = this.model.get("data");
        var disabledList = gatherDisabledNodes(data);

        var jobFailedRetryOptions = {};
        // var jobRetryTrList = $(".jobRetryTr");
        // for (var i = 0; i < jobRetryTrList.length; ++i) {
        //   var row = jobRetryTrList[i];
        //   var jobSelect = $(row).find('job-select');


        //   var td = $(row).find('input');
        //   var key = $(td[0]).text();
        //   var val = $(td[1]).text();

        //   if (key && key.length > 0) {
        //     flowOverride[key] = val;
        //   }
        // }
        var tdFailedRetrys = document.getElementById("jobFailedRetryTable").tBodies[0];
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
        var tdSkipFaileds = document.getElementById("jobSkipFailedTable").tBodies[0];
        for (var row = 0; row < tdSkipFaileds.rows.length - 1; row++) {
            var tdSkipFailed = tdSkipFaileds.rows[row];
            var job = tdSkipFailed.cells[1].firstChild.value;
            jobSkipFailedOptions[row] = job;
            if (tdSkipFailed.cells[0].firstChild.checked) {
                jobSkipActionOptions.push(job);
            }
        }

        //超时告警设置
        var useTimeoutSetting = $("#flow-timeout-option").is(":checked");
        var slaEmails = $('#timeout-slaEmails').val();
        var settings = {};
        settings[0] = "," + $("#timeout-status").val() + "," + $("#timeout-second").val() + "," +
            $("#timeout-level").val() + "," + $("#timeout-email").is(":checked") + "," +
            $("#timeout-killflow").is(":checked");

        var flowRetryAlertChecked = $("#flowRetryAlertChecked").is(":checked");
        var flowRetryAlertLevel = $("#flowRetryAlertLevel").val();
        var alertMsg = $("#alertMsg").val();

        var executeType = $('#executeFlowType :checked').val();
        var historyRecover = $("#enable-history-recover").is(':checked');
        var cycleExecution = $("#enable-cycle-execution").is(':checked');
        //todo 历史重跑功能和循环执行功能在前端需要做互斥校验
        //判断是否启用历史重跑功能、循环执行
        if ((historyRecover || cycleExecution) && !executeType) {
            executeType = "异常重跑";
        }
        var executingData = {
            projectId: projectId || fromShowDataProjectId,
            project: this.projectName,
            ajax: "executeFlow",
            flow: this.flowId,
            disabled: JSON.stringify(disabledList),
            failureEmailsOverride: failureEmailsOverride,
            successEmailsOverride: successEmailsOverride,
            failureAction: failureAction,
            failureEmails: failureEmails,
            failedMessageContent: failedMessageContent || '',
            successEmails: successEmails,
            successMessageContent: successMessageContent || '',
            notifyFailureFirst: notifyFailureFirst,
            notifyFailureLast: notifyFailureLast,
            flowOverride: flowOverride,
            jobFailedRetryOptions: jobFailedRetryOptions,
            failureAlertLevel: failureAlertLevel,
            successAlertLevel: successAlertLevel,
            jobSkipFailedOptions: jobSkipFailedOptions,
            jobSkipActionOptions: JSON.stringify(jobSkipActionOptions),
            useTimeoutSetting: useTimeoutSetting,
            slaEmails: slaEmails,
            settings: settings,
            flowRetryAlertChecked: flowRetryAlertChecked,
            flowRetryAlertLevel: flowRetryAlertLevel,
            alertMsg: alertMsg,
            taskDistributeMethod: taskDistributeMethod,
            executeType: executeType,
            reRunTimeInterval: reRunTimeInterval ? parseInt(reRunTimeInterval) : 0,
            enabledCacheProjectFiles: enabledCacheProjectFiles === "true",
        };

        // Set concurrency option, default is skip

        var concurrentOption = $('input[name=concurrent]:checked').val() || 'skip';
        executingData.concurrentOption = concurrentOption;
        if (concurrentOption == "pipeline") {
            var pipelineLevel = $("#pipeline-level").val();
            executingData.pipelineLevel = pipelineLevel;
        } else if (concurrentOption == "queue") {
            executingData.queueLevel = $("#queueLevel").val();
        }

        var rerunAction = $('input[name=rerunAction]:checked').val();
        executingData.rerunAction = rerunAction;

        //检查是否有重复的规则
        if (checkFiledRetryRule(jobFailedRetryOptions)) {
            alert(wtssI18n.view.errorRerunRulePro);
            return;
        }

        return executingData;
    },

    changeFlowInfo: function() {
        var successEmails = Array.isArray(this.model.get("successEmails")) && this.model.get("successEmails").filter(val=>val).length > 0 ?  this.model.get("successEmails").filter(val=>val) : [loginUser];
        var failureEmails = Array.isArray(this.model.get("failureEmails")) && this.model.get("failureEmails").filter(val=>val).length > 0 ?  this.model.get("failureEmails").filter(val=>val) : [loginUser];
        var failedMessageContent = this.model.get("failedMessageContent") || '';
        var successMessageContent = this.model.get("successMessageContent") || '';
        var historyEmails = this.model.get("historyEmails") ? this.model.get("historyEmails") : [loginUser];
        var cycleFlowEmails = this.model.get("cycleFlowEmails") ? this.model.get("cycleFlowEmails") : [loginUser];
        var failureActions = this.model.get("failureAction") || 'finishPossible';
        var notifyFailure = this.model.get("notifyFailure");
        var flowParams = this.model.get("flowParams");
        //jobOutputGlobalParam
        var jobOutputGlobalParam = this.model.get("jobOutputGlobalParam");
        var isRunning = this.model.get("isRunning");
        var concurrentOption = this.model.get("concurrentOption") || 'skip';
        var rerunAction = this.model.get("rerunAction") || 'rerun';
        var pipelineLevel = this.model.get("pipelineLevel") || '1';
        var pipelineExecutionId = this.model.get("pipelineExecution");
        var queueLevel = this.model.get("queueLevel");
        var nodeStatus = this.model.get("nodeStatus");
        var overrideSuccessEmails = this.model.get("successEmailsOverride");
        var overrideFailureEmails = this.model.get("failureEmailsOverride") || true;
        var enableHistoryRecover = this.model.get("enableHistoryRecover");
        var flowRetryAlertOption = this.model.get("flowRetryAlertOption") ? this.model.get("flowRetryAlertOption") : {};
        var flowRetryAlertChecked = flowRetryAlertOption.flowRetryAlertChecked ? flowRetryAlertOption.flowRetryAlertChecked : false;
        var flowRetryAlertLevel = flowRetryAlertOption.flowRetryAlertLevel || 'INFO';
        var alertMsg = flowRetryAlertOption.alertMsg ? flowRetryAlertOption.alertMsg : wtssI18n.view.alertMsg;
        if (flowRetryAlertChecked) {
            $('#flowRetryAlertChecked').prop('checked', true);
            $('#flowRetryAlertLevel').val(flowRetryAlertLevel);
            $('#alertMsg').val(alertMsg);
        } else {
            $('#flowRetryAlertChecked').prop('checked', false);
            $('#alertMsg').attr('disabled', 'disabled');
            $('#alertMsg').val(alertMsg);
        }

        if (overrideSuccessEmails) {
            $('#override-success-emails').prop('checked', true);
        } else {
            $('#override-success-emails').prop('checked', false);
            $('#success-emails').attr('disabled', 'disabled');
            $('#custom-success-alert').attr('disabled', 'disabled');
        }
        if (overrideFailureEmails) {
            $('#override-failure-emails').prop('checked', true);
        } else {
            $('#override-failure-emails').prop('checked', false);
            $('#failure-emails').attr('disabled', 'disabled');
            $('#custom-failure-alert').attr('disabled', 'disabled');
        }

        if (successEmails) {
            $('#success-emails').val(successEmails.join());
        }
        if (successMessageContent){
            $('#custom-success-alert').val(successMessageContent);
        }
        if (failureEmails) {
            $('#failure-emails').val(failureEmails.join());
            console.log(successEmails, failureEmails)
        }
        if (failedMessageContent) {
            $('#custom-failure-alert').val(failedMessageContent);
        }
        if (failureActions) {
            $('#failure-action').val(failureActions);
        }

        if (notifyFailure.first || (!notifyFailure.first && !notifyFailure.last)) {
            $('#notify-failure-first').prop('checked', true).parent('.btn').addClass('active');
            $('#notify-failure-last').prop('checked', false).parent('.btn').removeClass('active');
        } else {
            $('#notify-failure-last').prop('checked', true).parent('.btn').addClass('active');
            $('#notify-failure-first').prop('checked', false).parent('.btn').removeClass('active');
        }

        $('#concurrent-panel input[value="' + concurrentOption + '"][name="concurrent"]').prop('checked', true);


        $('#pipeline-level').val(pipelineLevel);

        if (queueLevel) {
            $('#queueLevel').val(queueLevel);
        }


        $('input[value=' + rerunAction + '][name="rerunAction"]').prop("checked", true);

        // 初始化或赋值工作流参数设置
        $("#flow-execute-dialog-editTable .editRow").remove();
        if (flowParams) {
            for (var key in flowParams) {
                editTableView.handleAddRow({
                    paramkey: key,
                    paramvalue: flowParams[key]
                });
            }
        }
        // 初始化或赋值任务输出参数
        $("#jobOutputParam-editTable .editRow").remove();
        if (jobOutputGlobalParam) {
            for (var key in jobOutputGlobalParam) {
                jobOutputParamEditTableView.handleAddRow({
                    paramkey: key,
                    paramvalue: jobOutputGlobalParam[key]
                });
            }
        }
        //历史重跑界面初始化方法
        if (enableHistoryRecover) {
            $('#datetimebegin').attr('disabled', null);
            $('#datetimeend').attr('disabled', null);
            $('#executeTimeBegin').attr('disabled', null);
            $('#executeTimeEnd').attr('disabled', null);
            $('#repeat-num').attr('disabled', null);
            $('#recover-interval').attr('disabled', null);
            $('#reRunTimeInterval').attr('disabled', null);
            $('#recover-error-option').attr('disabled', null);
            $('#runDateTime').attr('disabled', null);
            $('#skipRunDateTime').attr('disabled', null);
            $('#flow-history-rerun-finish-emails').attr('disabled', null);
        } else {
            $("#enable-history-recover").prop('checked', false);
            $("#enable-reverse-execute-history-recover").prop('checked', false);
            $("#enable-history-recover-finished-alert").prop('checked', false);
            $("#enable-recover-current-version").prop('checked', false);
            var currentTime = new Date()
            var year = currentTime.getFullYear()
            var month = currentTime.getMonth() + 1
            month = month > 9 ? month : '0' + month
            var day = currentTime.getDate()
            day = day > 9 ? day : '0' + day
            var timeStr = year + '/' + month + '/' + day
            $('#datetimebegin').attr('disabled', 'disabled').val(timeStr);
            $('#datetimeend').attr('disabled', 'disabled').val(timeStr);
            $('#executeTimeBegin').attr('disabled', 'disabled');
            $('#executeTimeEnd').attr('disabled', 'disabled');
            $('#repeat-num').attr('disabled', 'disabled').val('1');
            $('#recover-interval').attr('disabled', 'disabled').val('day');
            $('#reRunTimeInterval').attr('disabled', 'disabled').val('0');
            $("#recover-Concurrent-option").attr('disabled', null);
            $('#runDateTime').attr('disabled', 'disabled').val('');
            $('#skipRunDateTime').attr('disabled', 'disabled').val('');
            $('#recover-error-option').attr('disabled', 'disabled').val('errorStop');
            $('#recover-Concurrent-option').val('1');
            $('#flow-history-rerun-finish-alert-level').val('INFO');
            $('#flow-history-rerun-finish-emails').attr('disabled', 'disabled');
            $('#id-show-start-five-date').click();
        }
        $('#flow-history-rerun-finish-emails').val(historyEmails.join());
        $('#cycleFlow-interrupt-emails').val(cycleFlowEmails.join());

        const enabledCacheProjectFiles = this.model.get("enabledCacheProjectFiles") || false;
        $('#enabledCacheProjectFiles input[value="' + enabledCacheProjectFiles + '"][name="cacheProject"]').prop('checked', true);


        const taskDistributeMethod = this.model.get("taskDistributeMethod") || "uniform";
        $('#taskDistributeMethod input[value="' + taskDistributeMethod + '"][name="taskMethod"]').prop('checked', true);
        //初始化循环执行的页面
        $("#enable-cycle-execution").prop("checked", false);
        $("#cycleFlow-interrupt-alert-level").val("INFO");
        $("#cycle-error-option").val("errorStop").attr('disabled', 'disabled');

    },

    show: function(data) {
        console.log('show flow execute dialog', data)
        fromShowDataProjectName = data.project;
        fromShowDataProjectId = data.projectId;
        var projectName = data.project;
        var flowId = data.flow;
        var jobId = data.job;
        var executeFlowTitle = data.executeFlowTitle;

        // ExecId is optional
        var execId = data.execid;
        var exgraph = data.exgraph;

        this.projectName = projectName;
        this.flowId = flowId;

        var self = this;
        var loadCallback = function() {
            if (jobId) {
                self.showExecuteJob(executeFlowTitle, projectName, flowId, jobId, data.withDep);
            } else {
                self.showExecuteFlow(executeFlowTitle, projectName, flowId);
            }
            if (data.isEditCycleScheduleClick === true) {
                sideMenuDialogView.menuSelect($("#cycle-execution-li"));
                $("#enable-cycle-execution").prop("checked", true);
                $("#cycle-error-option").attr('disabled', null);
            }
        };
        if (execId) {
            flowExecuteDialogView.model.set('isLastExecutGraph', false);
            flowExecuteDialogView.model.set('execId', execId);
            $("#workflow-execute-job-switch").show().attr('title', 'Last execut job');

            this.loadGraph(projectName, flowId, exgraph, loadCallback);
            $("#enable-recover-current-version").attr('disabled', 'disabled');
        } else {
            this.loadGraphNew(projectName, flowId, exgraph, loadCallback);
        }
         // 显示工作流执行类型
         $('#executeFlowType').show();

        this.loadFlowInfo(projectName, flowId, execId);
        this.initTimeoutPanel(flowId);

        //nsWtss为true才显示
        if (execId && this.model.get("nsWtss")) {
            $("#job-output-parameters-li").show();
        }

        $("#execute-div").show();
        $("#start-all-div").hide();
        $('#flow-option').show();
        $('#flow-execution-option').show();

        $("#show-start-five-date").prop("checked", true);
        $('#history-recover-li').show();
        $('#cycle-execution-li').show();

        // 判断是否设置了邮件,没有默认使用当前用户,有则保持数据不变
        var successEmails = Array.isArray(this.model.get("successEmails")) && this.model.get("successEmails").filter(val=>val).length > 0 ?  this.model.get("successEmails").filter(val=>val) : [loginUser];
        var failureEmails = Array.isArray(this.model.get("failureEmails")) && this.model.get("failureEmails").filter(val=>val).length > 0 ?  this.model.get("failureEmails").filter(val=>val) : [loginUser];
        console.log(successEmails, failureEmails)
        var failedMessageContent = this.model.get("failedMessageContent");
        var successMessageContent = this.model.get("successMessageContent");


        if (successEmails.length != 0) {
            $('#success-emails').val(successEmails.join());
        }
        if (successMessageContent) {
            $('#custom-success-alert').val(successMessageContent);
        }
        if (failureEmails.length != 0) {
            $('#failure-emails').val(failureEmails.join());
        }
        if (failedMessageContent) {
            $('#custom-failure-alert').val(failedMessageContent);
        }
        var overrideSuccessEmails = this.model.get("successEmailsOverride");
        var overrideFailureEmails = this.model.get("failureEmailsOverride");

        if (overrideSuccessEmails) {
            $('#override-success-emails').prop('checked', true);
            $('#success-emails').attr('disabled', false);
            $('#custom-success-alert').attr('disabled', false);
        } else {
            $('#override-success-emails').prop('checked', false);
            $('#success-emails').attr('disabled', 'disabled');
            $('#custom-success-alert').attr('disabled', 'disabled');
        }
        if (overrideFailureEmails) {
            $('#override-failure-emails').prop('checked', true);
            $('#failure-emails').attr('disabled', false);
            $('#custom-failure-alert').attr('disabled', false);
        } else {
            $('#override-failure-emails').prop('checked', false);
            $('#failure-emails').attr('disabled', 'disabled');
            $('#custom-failure-alert').attr('disabled', 'disabled');
        }

        var failureAlertLevel = this.model.get("failureAlertLevel") || "INFO";
        var successAlertLevel = this.model.get("successAlertLevel") || "INFO";

        var useTimeoutSetting = this.model.get("useTimeoutSetting");
        var ruleType = this.model.get("ruleType");
        var duration = this.model.get("duration");
        var slaAlertLevel = this.model.get("slaAlertLevel") || "INFO";
        var emailAction = this.model.get("emailAction");
        var killAction = this.model.get("killAction");

        $("#override-failure-alert-level").val(failureAlertLevel);
        $("#override-success-alert-level").val(successAlertLevel);

        if (useTimeoutSetting) {
            $('#flow-timeout-option').prop('checked', true);
            $("#flow-timeout-model").show();
            var slaEmails = this.model.get("slaEmails");
            $('#timeout-slaEmails').val(slaEmails);
        } else {
            $("#flow-timeout-model").hide();
            $('#timeout-slaEmails').val(loginUser);
        }

        if (ruleType == "FlowSucceed") {
            $("#timeout-status").val("SUCCESS");
        } else {
            $("#timeout-status").val("FINISH");
        }
        $("#timeout-second").val(duration);

        $("#timeout-level").val(slaAlertLevel);

        if (emailAction == "true") {
            $('#timeout-email').prop('checked', true);
        } else {
            $('#timeout-email').prop('checked', false);
        }

        if (killAction == "true") {
            $('#timeout-killflow').prop('checked', true);
        } else {
            $('#timeout-killflow').prop('checked', false);
        }
        // 执行调度且excuteType为
        if (this.model.get("flowType") === 3 ) {
            document.getElementById('funcVlid').checked = false;
            document.getElementById('failedRunning').checked = true;
        } else {
            document.getElementById('funcVlid').checked = true;
            document.getElementById('failedRunning').checked = false;
        }
        $('#taskDistributeMethod input[value="uniform"][name="taskMethod"]').prop('checked', true);

        $('#enabledCacheProjectFiles input[value="false"][name="cacheProject"]').prop('checked', true);
    },

    showExecuteFlow: function(executeFlowTitle, projectName, flowId) {
        $("#execute-flow-panel-title").text(executeFlowTitle + flowId);
        this.showExecutionOptionPanel();

        // Triggers a render
        this.model.trigger("change:graph");
    },

    showExecuteJob: function(executeFlowTitle, projectName, flowId, jobId, withDep) {
        sideMenuDialogView.menuSelect($("#flow-option"));
        $("#execute-flow-panel-title").text(executeFlowTitle + flowId);

        var data = this.model.get("data");
        var disabled = this.model.get("disabled");

        // Disable all, then re-enable those you want.
        disableAll();

        var jobNode = data.nodeMap[jobId];
        if (!jobNode.autoDisabled) {
            touchNode(jobNode, false);
        }
        if (withDep) {
            recurseAllAncestors(jobNode, false);
        }



        this.showExecutionOptionPanel();
        this.model.trigger("change:graph");
    },

    showExecutionOptionPanel: function() {
        sideMenuDialogView.menuSelect($("#flow-option"));
        $('#execute-flow-panel').modal();
        jobFailedRetryView.setFlowID(this.flowId);
        jobSkipFailedView.setFlowID(this.flowId);
    },

    hideExecutionOptionPanel: function() {
        $('#execute-flow-panel').modal("hide");
    },

    scheduleClick: function() {
        console.log("click schedule button.");
        this.hideExecutionOptionPanel();
        schedulePanelView.showSchedulePanel();
    },

    loadFlowInfo: function(projectName, flowId, execId) {
        console.log("Loading flow " + flowId);
        fetchFlowInfo(this.model, projectName, flowId, execId);
    },

    initTimeoutPanel: function(flowId) {
        console.log("init timeout-panel");
        this.model.set({ "flowName": flowId });
        var useTimeoutSetting = this.model.get("useTimeoutSetting");
        if (useTimeoutSetting) {
            $('#flow-timeout-option').prop('checked', true);
        } else {
            $('#flow-timeout-option').prop('checked', false);
        }
        $("#timeout-slaEmails").val("");
        $("#timeout-status").val("SUCCESS");
        $("#timeout-second").val("");
        $("#timeout-level").val("INFO");
        $("#timeout-email").prop("checked", false);
        $("#timeout-killflow").prop("checked", false);
        // $('.durationpick').datetimepicker({
        //     format: 'HH:mm'
        // });
        $("#flow-timeout-model").hide();
    },
    renderExecutGraph: function(data, exgraph) {
        var graphModel = executableGraphModel;
        graphModel.addFlow(data);

        if (exgraph) {
            this.assignInitialStatus(data, exgraph);
        }

        // Auto disable jobs that are finished.
        if (flowExecuteDialogView.model.get('isLastExecutGraph')) {
            var jobDisabled = flowExecuteDialogView.model.get('jobDisabled');
            disableLastExecutJobs(data, jobDisabled);
        } else {
            disableFinishedJobs(data);
        }
        executingSvgGraphView = new azkaban.SvgGraphView({
            el: $('#flow-executing-graph'),
            model: graphModel,
            render: false,
            rightClick: {
                "node": expanelNodeClickCallbackExecute,
                "edge": expanelEdgeClickCallbackExecute,
                "graph": expanelGraphClickCallbackExecute
            },
            tooltipcontainer: "#svg-div-custom"
        });
        if (!executeJobListView) {
            executeJobListView = new azkaban.ExecuteJobListView({
                el: $('#execute-joblist-panel'),
                model: graphModel,
                contextMenuCallback: jobClickCallback,
                openBtnName: 'open-execute-joblist-btn',
            });
        }
    },
    loadGraph: function(projectName, flowId, exgraph, callback) {
        console.log("Loading flow " + flowId);
        var requestURL = "/executor";


        // fetchFlow(this.model, projectName, flowId, true);
        var requestData = {
            "project": projectName,
            "ajax": "fetchexecutionflowgraph",
            "flow": flowId,
            "lastExecId": execId
        };
        var self = this;
        window.currentGraphFlowId = flowId
        var successHandler = function(data) {
            if (data.error) {
                messageBox.show(data.error, 'danger');
                return;
            }
            console.log("data fetched");
            if (execId && execId !== -1) {
                data.execid = execId;
            }
            flowExecuteDialogView.model.set('executFlowData', _.cloneDeep(data));
            flowExecuteDialogView.model.set('executGraphData', _.cloneDeep(exgraph));
            self.renderExecutGraph(data, exgraph);
            if (callback) {
                callback.call(this);
            }
        };
        $.get(requestURL, requestData, successHandler, "json");
    },

    loadGraphNew: function(projectName, flowId, exgraph, callback) {
        console.log("Loading flow " + flowId);
        var requestURL = "/executor";

        var graphModel = executableGraphModel;
        // fetchFlow(this.model, projectName, flowId, true);
        var requestData = {
            "project": projectName,
            "ajax": "fetchexecutionflowgraphNew",
            "flow": flowId
        };
        var self = this;
        var successHandler = function(data) {
            if (data.error) {
                messageBox.show(data.error, 'danger');
                return;
            }
            console.log("data fetched");
            // Auto disable jobs that are finished.
            disableFinishedJobs(data);
            graphModel.addFlow(data);

            if (exgraph) {
                self.assignInitialStatus(data, exgraph);
            }

            executingSvgGraphView = new azkaban.SvgGraphView({
                el: $('#flow-executing-graph'),
                model: graphModel,
                render: false,
                rightClick: {
                    "node": expanelNodeClickCallbackExecute,
                    "edge": expanelEdgeClickCallbackExecute,
                    "graph": expanelGraphClickCallbackExecute
                },
                tooltipcontainer: "#svg-div-custom"
            });
            if (!executeJobListView) {
                executeJobListView = new azkaban.ExecuteJobListView({
                    el: $('#execute-joblist-panel'),
                    model: graphModel,
                    contextMenuCallback: jobClickCallback,
                    openBtnName: 'open-execute-joblist-btn',
                });
            }
            if (callback) {
                callback.call(this);
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

    handleExecuteFlow: function(evt) {
        console.log("click schedule button.");
        if (!this.checkTimeoutAlertSetting()) {
            return;
        }
        if (!this.executeFlowTypePro) {
            this.executeFlowTypePro = true;
            $('#azkaban-warn-dialog-button1').removeClass('btn-primary').addClass('btn-default');
            warnDialogView.show(wtssI18n.view.flowTypeSetting, wtssI18n.view.executionTypePro,wtssI18n.view.reset, wtssI18n.view.modifySchedule,function () {
                //  取消
                $('#azkaban-message-dialog').modal('hide');
                // 跳转到通用告警设置
                sideMenuDialogView.menuSelect(document.getElementById('flow-notification'));
            },function() {
                // 确认
                $('#azkaban-message-dialog').modal('hide');
                flowExecuteDialogView.handleExecuteFlow();
            })
            return;
        }
        $("#execute-btn").attr("disabled", true).addClass("button-disable")
        var executeURL = "/executor";
        var executingData = this.getExecutionOptionData();
        //todo 历史重跑功能和循环执行功能在前端需要做互斥校验
        //判断是否启用历史重跑功能
        if ($("#enable-history-recover").is(':checked')) {
            this.handleHistoryRecover();
        // 循环执行
        } else if ($("#enable-cycle-execution").is(':checked')) {
            this.handleCycleExecution();
        } else {
            if (!executingData.executeType) {
                $("#execute-btn").attr("disabled", false).removeClass("button-disable")
                messageBox.show(wtssI18n.view.executeFlowTypeReq, 'warning');
                return
            }
            if (typeof execId != "undefined" && execId) {
                console.log("lastExecId is " + execId);
                executingData.lastExecId = execId;
                executingData.jobOutputParam = this.getJobOutputParamDate();
                executingData.lastNsWtss = this.model.get("nsWtss");
            }
            executeFlow(executingData);
        }
    },

    handleHistoryRecover: function(evt) {
        console.log("click History Recover button.");
        if (!this.checkTimeoutAlertSetting()) {
            $("#execute-btn").attr("disabled", false).removeClass("button-disable")
            return;
        }

        var executingData = this.getExecutionOptionData();
        if (typeof execId != "undefined" && execId) {
            console.log("history lastExecId is " + execId);
            executingData.lastExecId = execId;
        } else {
            executingData.lastExecId = -1;
        }
        //单独的JS方法处理历史补采
        HistoryRecoverExecute(executingData);
    },

    userHistoryRecover: function(evt) {
        console.log("userHistoryRecover.");
        var beginTime = $('#datetimebegin').val();
        var endTime = $('#datetimeend').val();
        var executeTimeBegin = $('#executeTimeBegin').val();
        var executeTimeEnd = $('#executeTimeEnd').val();
        var monthNum = $('#repeat-month').val();
        var dayNum = $('#repeat-day').val();
        if (beginTime || endTime || executeTimeBegin || executeTimeEnd || 0 != monthNum || 0 != dayNum) {
            return true;
        } else {
            return false;
        }
    },

    reloadWindow: function() {
        window.location.reload();
    },

    handleCycleExecution: function() {
        console.log("click Cycle Execution button.");
        if (!this.checkTimeoutAlertSetting()) {
            $("#execute-btn").attr("disabled", false).removeClass("button-disable")
            return;
        }
        var executingData = this.getExecutionOptionData();
        if (typeof execId != "undefined" && execId) {
            console.log("executeCycleFlow lastExecId is " + execId);
            executingData.lastExecId = execId;
        } else {
            executingData.lastExecId = -1;
        }
        executeCycleFlow(executingData);
    }

});

var editTableView;
azkaban.EditTableView = Backbone.View.extend({
    events: {
        "click #add-btn-in-flowexecutionpanel": "handleAddRow",
        "click table .editable": "handleEditColumn",
        "click table .remove-btn": "handleRemoveColumn"
    },

    initialize: function(setting) {},

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

        $(tr).insertBefore("#flow-execute-dialog-editTable .addRow");
        return tr;
    },

    handleEditColumn: function(evt) {
        if (evt.target.tagName == "INPUT") {
            return;
        }
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

var jobOutputParamEditTableView;
azkaban.JobOutputParamEditTableView = Backbone.View.extend({
    events: {
        "click #jobOutputParam-add-btn": "handleAddRow",
        "click table .editable": "handleEditColumn",
        "click table .remove-btn": "handleRemoveColumn"
    },

    initialize: function(setting) {},

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

        $(tr).insertBefore("#jobOutputParam-editTable .addRow");
        return tr;
    },

    handleEditColumn: function(evt) {
        if (evt.target.tagName == "INPUT") {
            return;
        }
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

var sideMenuDialogView;
azkaban.SideMenuDialogView = Backbone.View.extend({
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
        if ((target[0] && target[0].id === "flow-option") || target.id === "flow-option") {
            $('#open-execute-joblist-btn').show()
            $("#switching-execute-flow-btn").show()
            $("#workflow-execute-zoom-in").show()
            if ( flowExecuteDialogView.model.get('execId')) {
                $('#workflow-execute-job-switch').show();
            }
        } else {
            $('#open-execute-joblist-btn').hide()
            $('#execute-joblist-panel').hide()
            $("#switching-execute-flow-btn").hide()
            $("#workflow-execute-zoom-in").hide()
            $("#workflow-execute-zoom-out").hide();
            $('#workflow-execute-job-switch').hide();
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

var handleJobMenuClick = function(action, el, pos) {
    var jobid = el[0].jobid;

    var requestURL = filterXSS("/manager?project=" + (projectName || fromShowDataProjectName) + "&flow=" +
        flowName + "&job=" + jobid);
    if (action == "open") {
        window.location.href = requestURL;
    } else if (action == "openwindow") {
        window.open(requestURL);
    }
}

var executableGraphModel;
/**
 * Disable jobs that need to be disabled
 */
 var disableLastExecutJobs = function(data, jobDisabled)  {
    for (var i = 0; i < data.nodes.length; ++i) {
        var node = data.nodes[i];
        if (jobDisabled.includes(node.id) || node.autoDisabled) {
            node.disabled = true;
        }
        if (node.type == "flow") {
            var subFlowDisabled = [];
            for (var j =0; j < jobDisabled.length; ++j) {
                if (typeof(jobDisabled[j]) && jobDisabled[j].id === node.id) {
                    subFlowDisabled = jobDisabled[j].children;
                    break;
                }
            }
            disableLastExecutJobs(node, subFlowDisabled);
        }
    }
}

/**
 * Disable jobs that need to be disabled
 */
var disableFinishedJobs = function(data) {
    for (var i = 0; i < data.nodes.length; ++i) {
        var node = data.nodes[i];
        if (node.status == "DISABLED" || node.status == "SKIPPED") {
            node.status = "READY";
            node.disabled = true;
            if (node.type == "flow") {
                setSubflowNodeDisabled(node);
            }
        } else if (node.autoDisabled || node.status == "SUCCEEDED" || node.status == "RETRIED_SUCCEEDED" || node.noInitialStatus || node.status == "FAILED_SKIPPED" || node.status == "FAILED_SKIPPED_DISABLED") {
            node.disabled = true;
            if (node.type == "flow") {
                setSubflowNodeDisabled(node);
            }
        } else {
            node.disabled = false;
            if (node.type == "flow") {
                disableFinishedJobs(node);
            }
        }
    }
}

/**
 * Enable all jobs. Recurse
 */
var enableAll = function() {
    recurseTree(executableGraphModel.get("data"), false, true);
    executableGraphModel.trigger("change:disabled");
}

var disableAll = function() {
    recurseTree(executableGraphModel.get("data"), true, true);
    executableGraphModel.trigger("change:disabled");
}


var recurseTree = function(data, disabled, recurse) {
        for (var i = 0; i < data.nodes.length; ++i) {
            var node = data.nodes[i];
            if (!node.autoDisabled) {
                node.disabled = disabled;
            }
            if (node.type == "flow" && recurse) {
                recurseTree(node, disabled, recurse);
            }
        }
    }
    // type 为执行类型datachecker--所有datacheck  eventchecker--所有eventchecker/rmbsender(所有信号)  outer--所有外部信息  disabled--true关闭  false开启
var touchTypecheck = function(type, disabled, labelNum) {
    conditionRecurseTree(executableGraphModel.get("data"), disabled, true, type, labelNum);
    executableGraphModel.trigger("change:disabled");
    sessionStorage.clear('disableEventchecker')
}

var conditionRecurseTree = function(data, disable, recurse, type, labelNum) {
    // 关闭、打开父节点
    function scheduleRecurseTreeParent(currentNode, disable) {
        if (currentNode.parent && !disable && !currentNode.parent.autoDisabled) {
            currentNode.parent.disabled = disable
            scheduleRecurseTreeParent(currentNode.parent, disable)
        }
    }
    for (var i = 0; i < data.nodes.length; ++i) {
        var node = data.nodes[i];
        switch (type) {
            case 'datachecker':
                if (node.type === 'datachecker' && !node.autoDisabled) {
                    node.disabled = disable;
                    scheduleRecurseTreeParent(node, disable);
                }
                break;
            case 'eventchecker':
                // 关闭打开发送、接收信号； rmbsend只有发送信号
                var eventcheckerType = sessionStorage.getItem('disableEventchecker')
                var isDisabled = (eventcheckerType === 'SEND' && ((node.type === 'eventchecker' && node.eventCheckerType === eventcheckerType) || node.type === 'rmbsender')) ||
                    (eventcheckerType === 'RECEIVE' && node.type === 'eventchecker' && node.eventCheckerType === eventcheckerType)
                if (isDisabled && !node.autoDisabled) {
                    node.disabled = disable;
                    scheduleRecurseTreeParent(node, disable);
                }
                break;
            case 'outer':
                if ((node.outer === true || node.outer === 'true') && !node.autoDisabled) {
                    node.disabled = disable;
                    scheduleRecurseTreeParent(node, disable);
                }
                break;
            case 'label':
                if (node.tag && node.tag.indexOf(labelNum) > -1 && !node.autoDisabled) {
                    node.disabled = disable;
                    scheduleRecurseTreeParent(node, disable);
                }
                break;
            default:
                break;
        }
        if (node.type == "flow" && recurse) {
            conditionRecurseTree(node, disable, recurse, type, labelNum);
        }
    }
}


// 打开关闭节点，如果节点类型为flow则需要打开关闭subfolw流程所有节点
var executDisableSubflow = function(single, node, disable) {
    if (!node) return;
    if (single) {
        if (!node.autoDisabled) {
            node.disabled = disable;
        }
        checkJobType(node, disable);
        if (!disable && !node.autoDisabled) {
            executEnableSubflow(node);
        }
    } else {
        var count = 0;
        for (var key in node) {
            if (count === 0 && !disable && !node[key].autoDisabled) {
                executEnableSubflow(node[key]);
            }
            if (!node[key].autoDisabled) {
                node[key].disabled = disable;
            }
            checkJobType(node[key], disable);
            count++;
        }
    }
}

function checkJobType(node, disable) {
    if (node.type == "flow") {
        recurseTree(node, disable, true);
    }
}
// 启用工作流如果父流程节点为disable要先把父节点disable改成true
var executEnableSubflow = function(node) {
    var executData = executableGraphModel.get("data")
    var parantArr = []
    var findNode = { isFind: false }
    executEnableSubflowTree(executData, parantArr, node, findNode)
}

var executEnableSubflowTree = function(executData, parantArr, node, findNode) {
    for (var i = 0; i < executData.nodes.length; ++i) {
        if (findNode.isFind) {
            return
        }
        var item = executData.nodes[i];
        if (item.nestedId === node.nestedId) {
            for (var j = 0; j < parantArr.length; j++) {
                if ( !parantArr[j].autoDisabled) {
                    parantArr[j].disabled = false
                }
            }
            findNode.isFind = true
            return
        }
        if (item.type == "flow") {
            parantArr.push(item)
            executEnableSubflowTree(item, parantArr, node, findNode)
            parantArr.splice(parantArr.length - 1, 1)
        }
    }
}


var touchNode = function(node, disable) {
    node.disabled = disable;
    executDisableSubflow(true, node, disable);
    executableGraphModel.trigger("change:disabled");
}

var touchParents = function(node, disable) {
    var inNodes = node.inNodes;

    executDisableSubflow(false, inNodes, disable);

    executableGraphModel.trigger("change:disabled");
}

var touchChildren = function(node, disable) {
    var outNodes = node.outNodes;

    executDisableSubflow(false, outNodes, disable);

    executableGraphModel.trigger("change:disabled");
}

var touchAncestors = function(node, disable) {
    var inNodes = node.inNodes;
    if (inNodes && !disable) {
        var key = Object.keys(inNodes)[0]
        executEnableSubflow(inNodes[key])
    }
    recurseAllAncestors(node, disable);

    executableGraphModel.trigger("change:disabled");
}

var touchDescendents = function(node, disable) {
    var outNodes = node.outNodes;
    if (outNodes && !disable) {
        var key = Object.keys(outNodes)[0]
        executEnableSubflow(outNodes[key])
    }
    recurseAllDescendents(node, disable);

    executableGraphModel.trigger("change:disabled");
}

var gatherDisabledNodes = function(data) {
    var nodes = data.nodes;
    var disabled = [];

    for (var i = 0; i < nodes.length; ++i) {
        var node = nodes[i];
        if (node.disabled) {
            disabled.push(node.id);
        } else {
            if (node.type == "flow") {
                var array = gatherDisabledNodes(node);
                if (array && array.length > 0) {
                    disabled.push({ id: node.id, children: array });
                }
            }
        }
    }

    return disabled;
}

function recurseAllAncestors(node, disable) {
    var inNodes = node.inNodes;
    if (inNodes) {
        for (var key in inNodes) {
            if (!inNodes[key].autoDisabled) {
                inNodes[key].disabled = disable;
                checkJobType(inNodes[key], disable);
            }
            recurseAllAncestors(inNodes[key], disable);
        }
    }
}

function recurseAllDescendents(node, disable) {
    var outNodes = node.outNodes;
    if (outNodes) {
        for (var key in outNodes) {
            if (!outNodes[key].autoDisabled) {
                outNodes[key].disabled = disable;
            }
            checkJobType(outNodes[key], disable);
            recurseAllDescendents(outNodes[key], disable);
        }
    }
}

var expanelNodeClickCallbackExecute = function(event, model, node) {
    console.log("Node clicked callback");
    var jobId = node.id;
    var flowId = executableGraphModel.get("flowId");
    var type = node.type;

    var menu;
    if (type == "flow") {
        var flowRequestURL = filterXSS("/manager?project=" + (projectName || fromShowDataProjectName) +
            "&flow=" + node.flowId);
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
                    title: wtssI18n.common.expandAllFlow,
                    callback: function() {
                        model.trigger("expandAllFlows", node);
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
        var requestURL = filterXSS("/manager?project=" + (projectName || fromShowDataProjectName) + "&flow=" +
            flowId + "&job=" + jobId);
        menu = [{
            title: wtssI18n.common.openNewJob,
            callback: function() {
                window.open(requestURL);
            }
        }, ];
    }
    // autoDisabled 为true节点默认关闭
    let openCloseMenu = [];
    if (!node.autoDisabled) {
        openCloseMenu = [{
            key: "openNode",
            title: wtssI18n.view.open,
            callback: function() {
                touchNode(node, false);
            },
            submenu: [{
                    title: wtssI18n.view.openParentNode,
                    callback: function() {
                        touchParents(node, false);
                    }
                },
                {
                    title: wtssI18n.view.openPreviousNode,
                    callback: function() {
                        touchAncestors(node, false);
                    }
                },
                {
                    title: wtssI18n.view.openChildNode,
                    callback: function() {
                        touchChildren(node, false);
                    }
                },
                {
                    title: wtssI18n.view.openDescendantNodes,
                    callback: function() {
                        touchDescendents(node, false);
                    }
                },
                {
                    title: wtssI18n.view.openDatacheck,
                    callback: function() {
                        touchTypecheck('datachecker', false);
                    }
                },
                {
                    title: wtssI18n.view.openSendingSignal,
                    callback: function() {
                        //关闭发送信息
                        sessionStorage.setItem('disableEventchecker', 'SEND')
                        touchTypecheck('eventchecker', false);
                    }
                },
                {
                    title: wtssI18n.view.openReceivingSignal,
                    callback: function() {
                        // 关闭接口信号
                        sessionStorage.setItem('disableEventchecker', 'RECEIVE')
                        touchTypecheck('eventchecker', false);
                    }
                },
                {
                    title: wtssI18n.view.openOuterSignal,
                    callback: function() {
                        touchTypecheck('outer', false);
                    }
                },
                {
                    title: wtssI18n.view.openAll,
                    callback: function() {
                        enableAll();
                    }
                },
                {
                    key: "openNode",
                    title: wtssI18n.view.openLabel,
                    callback: function() {

                    },
                    submenu: [{
                            title: wtssI18n.view.openLabel + '1',
                            callback: function() {
                                touchTypecheck('label', false, 1);
                            }
                        },
                        {
                            title: wtssI18n.view.openLabel + '2',
                            callback: function() {
                                touchTypecheck('label', false, 2);
                            }
                        },
                        {
                            title: wtssI18n.view.openLabel + '3',
                            callback: function() {
                                touchTypecheck('label', false, 3);
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
                touchNode(node, true)
            },
            submenu: [{
                    title: wtssI18n.view.closeParentNode,
                    callback: function() {
                        touchParents(node, true);
                    }
                },
                {
                    title: wtssI18n.view.closePreviousNode,
                    callback: function() {
                        touchAncestors(node, true);
                    }
                },
                {
                    title: wtssI18n.view.closeChildNode,
                    callback: function() {
                        touchChildren(node, true);
                    }
                },
                {
                    title: wtssI18n.view.closeDescendantNodes,
                    callback: function() {
                        touchDescendents(node, true);
                    }
                },
                {
                    title: wtssI18n.view.closeDatacheck,
                    callback: function() {
                        touchTypecheck('datachecker', true);
                    }
                },
                {
                    title: wtssI18n.view.closeSendingSignal,
                    callback: function() {
                        //关闭发送信息
                        sessionStorage.setItem('disableEventchecker', 'SEND')
                        touchTypecheck('eventchecker', true);
                    }
                },
                {
                    title: wtssI18n.view.closeReceivingSignal,
                    callback: function() {
                        // 关闭接口信号
                        sessionStorage.setItem('disableEventchecker', 'RECEIVE')
                        touchTypecheck('eventchecker', true);
                    }
                },
                {
                    title: wtssI18n.view.closeOuterSignal,
                    callback: function() {
                        touchTypecheck('outer', true);
                    }
                },
                {
                    title: wtssI18n.view.closeAll,
                    callback: function() {
                        disableAll();
                    }
                },
                {
                    key: "closeNode",
                    title: wtssI18n.view.closeLabel,
                    callback: function() {

                    },
                    submenu: [{
                            title: wtssI18n.view.closeLabel + '1',
                            callback: function() {
                                touchTypecheck('label', true, 1);
                            }
                        },
                        {
                            title: wtssI18n.view.closeLabel + '2',
                            callback: function() {
                                touchTypecheck('label', true, 2);
                            }
                        },
                        {
                            title: wtssI18n.view.closeLabel + '3',
                            callback: function() {
                                touchTypecheck('label', true, 3);
                            }
                        }
                    ]
                }
            ]
        },]
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

    contextMenuView.show(event, menu);
}

var expanelEdgeClickCallbackExecute = function(event) {
    console.log("Edge clicked callback");
}

var expanelGraphClickCallbackExecute = function(event) {
    console.log("Graph clicked callback");
    var flowId = executableGraphModel.get("flowId");
    var requestURL = filterXSS("/manager?project=" + (projectName || fromShowDataProjectName) + "&flow=" +
        flowId);

    var menu = [{
            title: wtssI18n.common.expandAllWorkflow,
            callback: function() {
                executableGraphModel.trigger("expandAllFlows");
                executableGraphModel.trigger("resetPanZoom");
            }
        },
        {
            title: wtssI18n.common.collapseAllWorkflow,
            callback: function() {
                executableGraphModel.trigger("collapseAllFlows");
                executableGraphModel.trigger("resetPanZoom");
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
            title: wtssI18n.view.openAll,
            callback: function() {
                enableAll();
            }
        },
        {
            title: wtssI18n.view.closeAll,
            callback: function() {
                disableAll();
            }
        },
        { break: 1 },
        {
            title: wtssI18n.common.centerGraph,
            callback: function() {
                executableGraphModel.trigger("resetPanZoom");
            }
        }
    ];

    contextMenuView.show(event, menu);
}

var contextMenuView;
$(function() {
    executableGraphModel = new azkaban.GraphModel();
    flowExecuteDialogView = new azkaban.FlowExecuteDialogView({
        el: $('#execute-flow-panel'),
        model: executableGraphModel
    });

    sideMenuDialogView = new azkaban.SideMenuDialogView({
        el: $('#graph-options')
    });
    editTableView = new azkaban.EditTableView({
        el: $('#flow-execute-dialog-editTable')
    });

    //  jobOutputParamEditTableView
    jobOutputParamEditTableView = new azkaban.JobOutputParamEditTableView({
        el: $('#jobOutputParam-editTable')
    });

    contextMenuView = new azkaban.ContextMenuView({
        el: $('#contextMenu')
    });

    $(document).keyup(function(e) {
        // escape key maps to keycode `27`
        if (e.keyCode == 27) {
            flowExecuteDialogView.hideExecutionOptionPanel();
            //flowExecuteDialogView.remove();
        }
    });

    jobRetryModel = new azkaban.JobRetryModel();

    jobFailedRetryView = new azkaban.JobFailedRetryView({
        el: $('#job-failed-retry-view'),
        model: jobRetryModel,
    });

    jobSkipFailedModel = new azkaban.JobSkipFailedModel();

    jobSkipFailedView = new azkaban.JobSkipFailedView({
        el: $('#job-skip-failed-view'),
        model: jobSkipFailedModel,
    });

    timeoutAlertView = new azkaban.TimeoutAlertView({
        el: $('#execution-graph-options-panel'),
        model: executableGraphModel
    });

    $("#switching-execute-flow-btn").on('click', function() {
        var trimFlowName = !JSON.parse(sessionStorage.getItem('trimFlowName')); //标识是否剪切节点名称
        sessionStorage.setItem('trimFlowName', trimFlowName)
        executingReRenderWorflow(true)
    })
    $("#workflow-execute-zoom-in").on('click', function() {
        $(this).hide()
        $("#workflow-execute-zoom-out").show()
        $('#execute-flow-panel .modal-header').hide()
        $('#execute-flow-panel .modal-footer').hide()
        $('#execution-graph-options-box').hide()
        $('#execution-graph-panel-box').removeClass('col-xs-10').addClass('col-xs-12')
        $('#execute-flow-panel .modal-dialog')[0].style.width = "98%"
        $('#flow-executing-graph')[0].style.height = window.innerHeight * 0.88
        executingZoomInWorflow() // 参数是否切换工作流
    })
    $("#workflow-execute-zoom-out").on('click', function() {
        $(this).hide()
        $("#workflow-execute-zoom-in").show()
        $('#execute-flow-panel .modal-header').show()
        $('#execute-flow-panel .modal-footer').show()
        $('#execution-graph-options-box').show()
        $('#execution-graph-panel-box').removeClass('col-xs-12').addClass('col-xs-10')
        $('#execute-flow-panel .modal-dialog')[0].style.width = "80%"
        $('#flow-executing-graph')[0].style.height = '500px'
        executingZoomInWorflow() // 参数是否切换工作流
    })
    $("#workflow-execute-job-switch").on('click', function() {
        var isLastExecutGraph = !flowExecuteDialogView.model.get('isLastExecutGraph');
        var title = isLastExecutGraph ? 'Will be execut job' :  'Last execut job';
        $("#workflow-execute-job-switch").attr('title', title);
        flowExecuteDialogView.model.set('isLastExecutGraph' ,isLastExecutGraph);
        var executFlowData =_.cloneDeep(flowExecuteDialogView.model.get('executFlowData'));;
        var executGraphData = _.cloneDeep(flowExecuteDialogView.model.get('executGraphData'));
        flowExecuteDialogView.renderExecutGraph(executFlowData,executGraphData);
        flowExecuteDialogView.model.trigger("change:graph");
    })
    window.kalenDaeRunDateTop = 0;
    window.skipKalenDaeRunDateTop = 0;
    // 自然日父盒子
    function setKalendaeTop(dom, kalenTopKey) {
        var panelScrollTop = document.getElementById('execute-flow-panel').scrollTop;
        dom.style.top = window[kalenTopKey] - panelScrollTop + 'px';
    }
    function setTimeKalendaeTop (index, kalenTopKey) {
        if (!$("#enable-history-recover").is(":checked")) { return }
        var kalendaeSelectDom = document.getElementsByClassName('kalendae k-floating')[index];
        if (!kalendaeSelectDom.style.top) {
            setTimeout(() => {
                if (!window[kalenTopKey]) {
                    window[kalenTopKey] = parseInt(kalendaeSelectDom.style.top);
                }
                setKalendaeTop(kalendaeSelectDom, index);
            }, 600);
        } else {
            if (!window[kalenTopKey]) {
                window[kalenTopKey] = parseInt(kalendaeSelectDom.style.top);
            }
            setKalendaeTop(kalendaeSelectDom, kalenTopKey);
        }
    }
    $("#runDateTimeBox").click(function() {
        setTimeKalendaeTop(1, 'kalenDaeRunDateTop');
    });
    $("#skipRunDateTimeBox").click(function() {
        setTimeKalendaeTop(0, 'skipKalenDaeRunDateTop');
    });
        //
    $("#execute-flow-panel").on('scroll', function(e) {
        var kalendaeSelectArr = document.getElementsByClassName('kalendae k-floating');
        for (var i = 0;i < kalendaeSelectArr.length; i++) {
            var kalendaeSelectDom = kalendaeSelectArr[i];
            if (!kalendaeSelectDom) { return }
            var display = kalendaeSelectDom.style.display
            if (display !== 'none' && kalendaeSelectDom) {
                var kalenTopKey = i == 0 ? 'skipKalenDaeRunDateTop' : 'kalenDaeRunDateTop';
                kalendaeSelectDom.style.top = window[kalenTopKey] - e.target.scrollTop + 'px'
            }
        }

    })
    // 关闭弹窗 关闭接口搜索
    $("#execute-flow-panel").on('hide.bs.modal',function(){
        executeJobListView && executeJobListView.handleClose();
    });
});

// 放大缩小重新收拢工作流，并居中
function executingZoomInWorflow() {
    executingSvgGraphView.collapseAllFlows()
    executingSvgGraphView.resetPanZoom()
}

function executingReRenderWorflow(switchingFlow) {
    var data = executingSvgGraphView.model.get('data') //获取流程图数据
    if (switchingFlow) {
        data.switchingFlow = true
    }
    $(executingSvgGraphView.mainG).empty() //清空流程图
    executingSvgGraphView.renderGraph(data, executingSvgGraphView.mainG)
}

//用于保存浏览数据，切换页面也能返回之前的浏览进度。
var jobRetryModel;
azkaban.JobRetryModel = Backbone.Model.extend({});

var jobFailedRetryView;
azkaban.JobFailedRetryView = Backbone.View.extend({
    events: {
        "click table #add-failed-retry-btn": "handleAddRetryRow",
        //"click table .editable": "handleEditColumn",
        "click table .remove-btn": "handleRemoveColumn"
    },

    initialize: function(setting) {},

    //每次回填数据时清除旧数据
    clearTable: function() {
        $("#jobFailedRetryTable .jobRetryTr").remove();
    },

    //flow 执行成功错误告警设置
    handleAddRetryRowCallShow: function(data, jobFailedRetryOptions, jobList) {

        var job = "";
        if (data.job) {
            job = data.job;
        }
        var interval = "";
        if (data.interval) {
            interval = data.interval;
        }
        var count = "";
        if (data.count) {
            count = data.count;
        }

        var retryTr = $("#jobFailedRetryTable tr").length - 1;
        // if(null != jobFailedRetryOptions && retryTr >= jobFailedRetryOptions.length){
        //   $('#add-schedule-failed-retry-btn').attr('disabled','disabled');
        // }
        if (null != jobList && retryTr > jobList.length) {
            $('#add-failed-retry-btn').attr('disabled', 'disabled');
            alert(wtssI18n.view.failedErrorFormat);
            return;
        }


        var failedRetryTable = document.getElementById("jobFailedRetryTable").tBodies[0];
        var trRetry = failedRetryTable.insertRow(failedRetryTable.rows.length - 1);

        $(trRetry).addClass('jobRetryTr');
        //设置失败重跑 job 名称
        var cJob = trRetry.insertCell(-1);

        var jobSelectId = "job-select" + failedRetryTable.rows.length;

        var idSelect = $("<select></select>");
        idSelect.attr("class", "form-control");
        idSelect.attr("id", jobSelectId);
        idSelect.attr("style", "width: 100%");
        $(cJob).append(idSelect);
        var optionHtml = ''
        for (var i = 0; i < jobList.length; i++) {
            optionHtml += "<option value='" + jobList[i].id + "'>" + jobList[i].id + "</option>"
        }
        optionHtml = filterXSS(optionHtml, { 'whiteList': { 'option': ['value'] } })
        idSelect.append(optionHtml);
        this.loadFlowJobListData(jobSelectId, jobRetryModel.get("flowId"), jobRetryModel.get("projectName"));
        //回显新增的数据,如果是新增一行,就没有回显
        if (job) {
            $("#" + jobSelectId).val(job).select2();
        }

        //设置失败重跑时间间隔
        var cInterval = trRetry.insertCell(-1);
        var retryInterval = $("<input></input>");
        retryInterval.attr("class", "form-control");
        retryInterval.attr("id", "interval-input");
        retryInterval.attr("type", "number");
        retryInterval.attr("value", "1");
        retryInterval.attr("step", "1");
        retryInterval.attr("min", "1");
        retryInterval.attr("max", "86400");
        $(retryInterval).val(interval);
        $(cInterval).append(retryInterval);

        //设置失败重跑次数
        var cCount = trRetry.insertCell(-1);
        var idSelect = $("<select></select>");
        idSelect.attr("class", "form-control");
        idSelect.attr("id", "count-select");
        var selectHtml = ''
        for (var i = 1; i < 4; i++) {
            selectHtml += "<option value='" + i + "'>" + i + " " + wtssI18n.view.times + "</option>";
        }
        selectHtml = filterXSS(selectHtml, { 'whiteList': { 'option': ['value'] } });
        idSelect.append(selectHtml);
        if ("" != count) {
            idSelect.val(count);
        }
        $(cCount).append(idSelect);

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

        if (null != jobList && retryTr >= jobList.length) {
            $('#add-failed-retry-btn').attr('disabled', 'disabled');
        }

        return trRetry;
    },

    //flow 执行成功错误告警设置
    handleAddRetryRow: function(evt) {

        var jobList = this.model.get("jobList");

        var retryTr = $("#jobFailedRetryTable tr").length - 1;
        if (retryTr == jobList.length) {
            $('#add-failed-retry-btn').attr('disabled', 'disabled');
        }


        var failedRetryTable = document.getElementById("jobFailedRetryTable").tBodies[0];
        var trRetry = failedRetryTable.insertRow(failedRetryTable.rows.length - 1);

        $(trRetry).addClass('jobRetryTr');
        //设置失败重跑 job 名称
        var cJob = trRetry.insertCell(-1);
        $(cJob).attr("style", "width: 65%");
        //var tr = $("<tr></tr>");
        //var td = $("<td></td>");


        var jobSelectId = "job-select" + failedRetryTable.rows.length;

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
        retryInterval.attr("value", "1");
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
        var idSelectHtml = ''
        for (var i = 1; i < 4; i++) {
            idSelectHtml += "<option value='" + i + "'>" + i + " " + wtssI18n.view.times + "</option>"
        }
        idSelectHtml = filterXSS(idSelectHtml, { 'whiteList': { 'option': ['value'] } })
        idSelect.append(idSelectHtml);
        $(cCount).append(idSelect);

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

        this.loadFlowJobListData(jobSelectId, jobRetryModel.get("flowId"));

        return trRetry;
    },

    handleRemoveColumn: function(evt) {
        var curTarget = evt.currentTarget;
        // Should be the table
        var row = curTarget.parentElement.parentElement.parentElement;
        $(row).remove();

        var jobList = this.model.get("jobList");

        var retryTr = $("#jobFailedRetryTable tr").length - 2;
        if (retryTr < jobList.length) {
            $('#add-failed-retry-btn').removeAttr('disabled');
        }

    },

    getFlowRealJobList: function(flowId, jobList) {

        var requestURL = "/manager?project=" + (projectName || fromShowDataProjectName);

        var model = this.model;
        var lastExecId = -1;
        if (typeof execId != "undefined" && execId) {
            lastExecId = execId;
        }
        var requestData = {
            "ajax": "fetchFlowRealJobLists",
            "flow": flowId,
            lastExecId: lastExecId,
        };
        // var successHandler = function(data) {
        //     return data.jobList;
        // };
        // $.get(requestURL, requestData, successHandler, "json");

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
                model.set({ "jobList": data.jobList });

                var jobFailedRetryOptions = flowExecuteDialogView.model.get("jobFailedRetryOptions");
                jobFailedRetryView.clearTable();
                //错误重试设置数据回填
                if (jobFailedRetryOptions) {
                    for (var i = 0; i < jobFailedRetryOptions.length; i++) {
                        var retryOption = jobFailedRetryOptions[i];
                        jobFailedRetryView.handleAddRetryRowCallShow({
                            job: retryOption["jobName"],
                            interval: retryOption["interval"],
                            count: retryOption["count"],
                        }, jobFailedRetryOptions, data.jobList);
                    }

                }

                var jobSkipFailedOptions = flowExecuteDialogView.model.get("jobSkipFailedOptions");
                var jobSkipActionOptions = flowExecuteDialogView.model.get("jobSkipActionOptions");
                jobSkipFailedView.clearJobSkipFailedTable();
                if (jobSkipFailedOptions) {
                    //循环填充选项
                    // 由于失败跳过下来数据拿的model里面的数据，当数据量大的时候数据没有更新拿的是上一个model值，故做延时处理
                    var skipSetIntervalCount = 0;
                    var renderSkipRow = setInterval(function() {
                        skipSetIntervalCount++;
                        if (skipSetIntervalCount >= 1000) {
                            clearInterval(renderSkipRow)
                            return;
                        }
                        var skipList = executingSvgGraphView && executingSvgGraphView.model.get('data')
                        if (skipList && skipList.flowId === flowId) {
                            for (var i = 0; i < jobSkipFailedOptions.length; i++) {
                                var retryOption = jobSkipFailedOptions[i];
                                jobSkipFailedView.handleAddSkipRow({
                                    job: retryOption,
                                    jobSkipActionOptions: jobSkipActionOptions
                                }, jobSkipFailedOptions);
                            }
                            clearInterval(renderSkipRow)
                            console.log('clearInterval')
                        }
                    }, 300)
                }

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

    setFlowID: function(flowId) {
        this.getFlowRealJobList(flowId, this.jobList);
        this.flowId = flowId;
        jobRetryModel.set("flowId", flowId);
        $('#add-failed-retry-btn').removeAttr('disabled');
    },

    loadFlowJobListData: function(selectId, flowId) {
        var lastExecId = -1;
        if (typeof execId != "undefined" && execId) {
            lastExecId = execId;
        }
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
            ajax: {
                type: 'GET',
                url: "/manager?project=" + (projectName || fromShowDataProjectName),
                dataType: 'json',
                delay: 250,
                data: function(params) {
                    var query = {
                        ajax: "fetchFlowRealJobLists",
                        flow: flowId,
                        action: "retryFailedJob",
                        lastExecId: lastExecId,
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


function checkFiledRetryRule(data) {
    var new_arr = [];
    var oldlength = 0;
    for (var i in data) {
        oldlength++;
        var items = data[i].substring(0, find(data[i], ",", 1));;
        //判断元素是否存在于new_arr中，如果不存在则插入到new_arr的最后
        if ($.inArray(items, new_arr) == -1) {
            new_arr.push(items);
        }
    }
    if (new_arr.length < oldlength) {
        return true;
    }
}

function find(str, cha, num) {
    var x = str.indexOf(cha);
    for (var i = 0; i < num; i++) {
        x = str.indexOf(cha, x + 1);
    }
    return x;
}


//用于保存浏览数据，切换页面也能返回之前的浏览进度。
var jobSkipFailedModel;
azkaban.JobSkipFailedModel = Backbone.Model.extend({});

var jobSkipFailedView;
azkaban.JobSkipFailedView = Backbone.View.extend({
    events: {
        "click table #add-skip-failed-btn": "handleAddSkipRow",
        //"click table .editable": "handleEditColumn",
        "click table .remove-btn": "handleRemoveColumn"
    },

    initialize: function(setting) {},

    //每次回填数据时清除旧数据
    clearJobSkipFailedTable: function() {
        $("#set-job-skip-failed-tbody").children(".jobSkipTr").remove();
    },

    //flow 失败跳过设置回显
    handleAddSkipRowCallShow: function(data, jobskipFailedOptions, jobList) {

        var jobName = data.job;

        var skipTr = $("#jobSkipFailedTable tr").length - 1;
        if (null != jobList && skipTr >= jobList.length) {
            $('#add-skip-failed-btn').attr('disabled', 'disabled');
        }

        var skipFailedTable = document.getElementById("jobSkipFailedTable").tBodies[0];
        var trSkip = skipFailedTable.insertRow(skipFailedTable.rows.length - 1);

        $(trSkip).addClass('jobSkipTr');

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
        var jobSelectId = "job-skip-failed-select" + skipFailedTable.rows.length;

        var idSelect = $("<select></select>");
        idSelect.attr("class", "form-control");
        idSelect.attr("id", jobSelectId);
        idSelect.attr("style", "width: 100%");
        var optionHtml = ''
        for (var i = 0; i < jobList.length; i++) {
            optionHtml += "<option value='" + jobList[i].id + "'>" + jobList[i].id + "</option>"
        }
        optionHtml = filterXSS(optionHtml, { 'whiteList': { 'option': ['value'] } })
        idSelect.append(optionHtml);

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

        if (jobName) {
            $("#" + jobSelectId).val(jobName).select2();
            $("#" + jobSkipActionId).get(0).checked = data.jobSkipActionOptions ? data.jobSkipActionOptions.includes(jobName) : false;
        }

        this.loadFlowJobListData(jobSelectId, jobSkipFailedModel.get("flowId"), jobSkipFailedModel.get("projectName"));
        return trSkip;
    },

    //flow 执行成功错误告警设置
    handleAddSkipRow: function(data) {
        //选中的任务名称
        var jobName = data.job ? data.job : "";
        var jobList = jobFailedRetryView.model.get('jobList');

        var retryTr = $("#jobSkipFailedTable tr").length - 1;
        if (jobList && retryTr == jobList.length) {
            $('#add-skip-failed-btn').attr('disabled', 'disabled');
        }


        var skipFailedTable = document.getElementById("jobSkipFailedTable").tBodies[0];
        var trSkip = skipFailedTable.insertRow(skipFailedTable.rows.length - 1);

        $(trSkip).addClass('jobSkipTr');

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

        var jobSelectId = "job-skip-failed-select" + skipFailedTable.rows.length;

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

        this.loadFlowJobListData(jobSelectId, jobSkipFailedModel.get("flowId"));
        //回显新增的数据,如果是新增一行,没有回显
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

        var jobList = jobFailedRetryView.model.get('jobList');

        var retryTr = $("#jobSkipFailedTable tr").length - 2;
        if (retryTr < jobList.length) {
            $('#add-skip-failed-btn').removeAttr('disabled');
        }

    },

    getFlowRealJobList: function(flowId, jobList) {

        var requestURL = "/manager?project=" + (projectName || fromShowDataProjectName);

        var model = this.model;

        var requestData = {
            "ajax": "fetchFlowRealJobLists",
            "flow": flowId,
        };
        var successHandler = function(data) {
            return data.jobList;
        };
        $.get(requestURL, requestData, successHandler, "json");

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
                model.set({ "jobList": data.jobList });
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

    setFlowID: function(flowId) {
        // this.getFlowRealJobList(flowId, this.jobList);
        this.flowId = flowId;
        jobSkipFailedModel.set("flowId", flowId);
        $('#add-skip-failed-btn').removeAttr('disabled');
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

var timeoutAlertView;
azkaban.TimeoutAlertView = Backbone.View.extend({
    events: {
        "change #flow-timeout-option": "flowTimeoutOption"
    },

    flowTimeoutOption: function(evt) {
        console.log("timeout option changed.");
        if (evt.currentTarget.checked) {
            $("#flow-timeout-model").show();
        } else {
            $("#flow-timeout-model").hide();
        }

    }
});