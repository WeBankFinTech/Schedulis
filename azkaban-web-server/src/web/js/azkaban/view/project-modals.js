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

var scheduleAllFlowView;
azkaban.ScheduleAllFlowView = Backbone.View.extend({
    events: {
        "click #shedule-all-flow-btn": "handleScheduleAllFlow",
    },

    initialize: function(settings) {},

    getExecutionOptionData: function() {
        // 将 '完成所有可执行的任务' 设置为默认
        var failureAction = 'finishPossible';
        var failureEmails = $('#schedule-failure-emails').val();
        var failedMessageContent = $('#schedule-custom-failure-alert').val()
        var successEmails = $('#schedule-success-emails').val();
        var successMessageContent = $('#schedule-custom-success-alert').val();
        var notifyFailureFirst = $('#schedule-notify-failure-first').parent().attr('class').search('active') > -1 ? true : false;
        var notifyFailureLast = !notifyFailureFirst;
        var failureEmailsOverride = $("#schedule-override-failure-emails").is(':checked');
        var successEmailsOverride = $("#schedule-override-success-emails").is(':checked');
        //告警级别选择
        var failureAlertLevel = $('#schedule-override-failure-alert-level').val();
        var successAlertLevel = $('#schedule-override-success-alert-level').val();
        // 作业流参数
        var flowOverride = {};
        var editRows = $(".editRow");
        for (var i = 0; i < editRows.length; ++i) {
            var row = editRows[i];
            var td = $(row).find('span');
            var key = $(td[0]).text();
            var val = $(td[1]).text();
            if (key && key.length > 0) {
                flowOverride[key] = val;
            }
        }

        var jobFailedRetryOptions = {};
        var jobSkipFailedOptions = {};
        var jobCronExpressOptions = {};

        var executingData = {
            projectId: projectId,
            project: projectName,
            disabled: [],
            failureEmailsOverride: failureEmailsOverride,
            successEmailsOverride: successEmailsOverride,
            failureAction: failureAction,
            failureEmails: failureEmails,
            failedMessageContent: failedMessageContent,
            successEmails: successEmails,
            successMessageContent: successMessageContent,
            notifyFailureFirst: notifyFailureFirst,
            notifyFailureLast: notifyFailureLast,
            flowOverride: flowOverride,
            jobFailedRetryOptions: jobFailedRetryOptions,
            failureAlertLevel: failureAlertLevel,
            successAlertLevel: successAlertLevel,
            jobSkipFailedOptions: jobSkipFailedOptions,
            jobCronExpressOptions: jobCronExpressOptions
        };

        // Set concurrency option, default is skip

        var concurrentOption = $('input[name=concurrent]:checked').val();
        executingData.concurrentOption = concurrentOption;
        if (concurrentOption == "pipeline") {
            var pipelineLevel = $("#schedule-pipeline-level").val();
            executingData.pipelineLevel = pipelineLevel;
        } else if (concurrentOption == "queue") {
            executingData.queueLevel = $("#schedule-queueLevel").val();
        }

        return executingData;
    },
    handleScheduleAllFlow: function(evt) {
        var scheduleURL = "/schedule?ajax=ajaxScheduleCronAllFlow"
        var scheduleData = this.getExecutionOptionData();

        console.log("Creating schedule for " + projectName + "." +
            scheduleData.flow);

        var currentMomentTime = moment();
        var scheduleTime = currentMomentTime.utc().format('h,mm,A,') + "UTC";
        var scheduleDate = currentMomentTime.format('MM/DD/YYYY');

        var itsmNo = $("#schedule-flow-check-itsm-number-input").val().trim();
        var itsmNumber = Number(itsmNo)
        if(Number.isNaN(itsmNumber) || itsmNumber < 0){
            messageBox.show('请输入合法的ITSM单号', 'danger');
            $("#schedule-flow-check-itsm-number-input").val("")
            return
        }

        scheduleData.ajax = "ajaxScheduleCronAllFlow";
        scheduleData.projectName = projectName;
        scheduleData.cronExpression = "0 " + $(schedule_cron_output_id).val();
        scheduleData.scheduleStartDate = $('#schedule-start-date-input').val();
        scheduleData.scheduleEndDate = $('#schedule-end-date-input').val();
        scheduleData.isCrossDay = $('#cross-day-select').val() ? $('#cross-day-select').val() : false;
        scheduleData.autoSubmit == $('#autoEnable :checked').val();
        scheduleData.alertOnceOnMiss = $('#autoEnableAlert :checked').val();
        scheduleData.comment = $("#scheduleComment").val();
        scheduleData.enabledCacheProjectFiles = $("#scheduleEnabledCacheProjectFiles :checked").val() === "true";
        scheduleData.itsmNo = itsmNo.trim() || ''
        console.log("current Time = " + scheduleDate + "  " + scheduleTime);
        console.log("cronExpression = " + scheduleData.cronExpression);
        var retSignal = validateQuartzStr(scheduleData.cronExpression.trim());

        if (retSignal == "NUM_FIELDS_ERROR") {
            var tipMsg1 = ""
            if (langType === "en_US") {
                tipMsg1 = "Cron Syntax Error, A valid Quartz Cron Expression should have 6 or 7 fields.";
            } else {
                tipMsg1 = "Cron 表达式错误, 一个有效的Cron表达式至少有6个或者7个属性."
            }
            alert(tipMsg1);
            return;
        } else if (retSignal == "DOW_DOM_STAR_ERROR") {
            var tipMsg2 = ""
            if (langType === "en_US") {
                tipMsg2 = "Cron Syntax Error, Currently Quartz doesn't support specifying both a day-of-week and a day-of-month value" +
                    "(you must use the ‘?’ character in one of these fields). <a href=\"http://www.quartz-scheduler.org/documentation/quartz-2.x/tutorials/crontrigger.html\" target=\"_blank\">Detailed Explanation</a>";
            } else {
                tipMsg2 = "Cron 表达式错误, 月的某日和周的某天不能同时有值" + "(你必须将其中一个的值改为 ‘?’). <a href=\"http://www.quartz-scheduler.org/documentation/quartz-2.x/tutorials/crontrigger.html\" target=\"_blank\">详情请见</a>";
            }
            alert(tipMsg2)
            return;
        }

        var successHandler = function(data) {
            if (data.error) {
                alert(data.error);
            } else {
                flowScheduleDialogView.hideScheduleOptionPanel();
                messageDialogView.show(wtssI18n.view.schedulingAllFlow, data.message, function() {
                    window.location.href = "/schedule";
                });
            }
        };
        $.ajax({
            url: scheduleURL,
            data: JSON.stringify(scheduleData),
            dataType: "json",
            type: "POST",
            contentType: "application/json; charset=utf-8",
            success: successHandler
        });
    }
});

var executeAllFlowView;
azkaban.ExecuteAllFlowView = Backbone.View.extend({
    events: {
        "click #start-all-btn": "handleExecuteAllFlow",
    },

    initialize: function(settings) {},
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
    handleHistoryRecover: function(executingData) {
        console.log("click History Recover button.");
        //单独的JS方法处理历史补采
        var reverseExecuteFlag = false;
        if ($("#enable-reverse-execute-history-recover").is(':checked')) {
            reverseExecuteFlag = true;
        }
        var recoverData = getHistoryRecoverOptionData(executingData, reverseExecuteFlag);
        if (recoverData) {
            $.ajax({
                url: "/executor?ajax=ajaxExecuteAllHistoryRecoverFlow",
                data: JSON.stringify(recoverData),
                dataType: "json",
                type: "POST",
                contentType: "application/json; charset=utf-8",
                success: function(data) {
                    console.log("execute all history recover flow success");
                    if (data.error) {
                        flowExecuteDialogView.hideExecutionOptionPanel();
                        messageDialogView.show(wtssI18n.view.historicalJobFail, data.error);
                    } else {
                        flowExecuteDialogView.hideExecutionOptionPanel();
                        messageDialogView.show(wtssI18n.view.submittedHistoryFlow, data.message,
                            function() {
                                window.location.href = "/executor";
                            }
                        );
                    }
                },
            });
        } else {
            $("#execute-btn").attr("disabled", false).removeClass("button-disable");
        }
    },

    handleExecuteAllFlow: function(evt) {
        console.log("click execute all flow button.");
        if (!this.checkTimeoutAlertSetting()) {
            return;
        }
        var executingData = this.getExecutionOptionData();
        var executeURL = "/executor?ajax=executeAllFlow";
        $.ajax({
            url: executeURL,
            data: JSON.stringify(executingData),
            dataType: "json",
            type: "POST",
            contentType: "application/json; charset=utf-8",
            success: function(data) {
                console.log("exec success");
                if (data.error) {
                    flowExecuteDialogView.hideExecutionOptionPanel();
                    messageDialogView.show(wtssI18n.common.workflowError, data.error);
                } else {
                    flowExecuteDialogView.hideExecutionOptionPanel();
                    messageDialogView.show(wtssI18n.common.workflowSubmit, data.message,
                        function() {
                            var redirectURL = "/executor";
                            window.location.href = redirectURL;
                        }
                    );
                }
            },
        });
    },

    getExecutionOptionData: function() {
        // 将 '完成所有可执行的任务' 设置为默认
        var failureAction = 'finishPossible';
        var failureEmails = $('#failure-emails').val();
        var failedMessageContent = $('#custom-failure-alert').val();
        var successEmails = $('#success-emails').val();
        var successMessageContent = $('#custom-success-alert').val();
        var notifyFailureFirst = $('#notify-failure-first').is(':checked');
        var notifyFailureLast = $('#notify-failure-last').is(':checked');
        var failureEmailsOverride = $("#override-failure-emails").is(':checked');
        var successEmailsOverride = $("#override-success-emails").is(':checked');
        //告警级别选择
        var failureAlertLevel = $('#override-failure-alert-level').val();
        var successAlertLevel = $('#override-success-alert-level').val();
        const enabledCacheProjectFiles = $("#enabledCacheProjectFiles :checked").val() === "true";
        var flowOverride = {};
        var editRows = $(".editRow");
        for (var i = 0; i < editRows.length; ++i) {
            var row = editRows[i];
            var td = $(row).find('span');
            var key = $(td[0]).text();
            var val = $(td[1]).text();

            if (key && key.length > 0) {
                flowOverride[key] = val;
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

        var executingData = {
            projectId: projectId,
            project: projectName,
            disabled: [],
            failureEmailsOverride: failureEmailsOverride,
            successEmailsOverride: successEmailsOverride,
            failureAction: failureAction,
            failureEmails: failureEmails,
            failedMessageContent: failedMessageContent,
            successEmails: successEmails,
            successMessageContent: successMessageContent,
            notifyFailureFirst: notifyFailureFirst,
            notifyFailureLast: notifyFailureLast,
            flowOverride: flowOverride,
            jobFailedRetryOptions: {},
            failureAlertLevel: failureAlertLevel,
            successAlertLevel: successAlertLevel,
            jobSkipFailedOptions: {},
            useTimeoutSetting: useTimeoutSetting,
            slaEmails: slaEmails,
            settings: settings,
            flowRetryAlertChecked: flowRetryAlertChecked,
            flowRetryAlertLevel: flowRetryAlertLevel,
            alertMsg: alertMsg,
            enabledCacheProjectFiles,
        };
        var concurrentOption = $('input[name=concurrent]:checked').val();
        executingData.concurrentOption = concurrentOption;
        if (concurrentOption == "pipeline") {
            var pipelineLevel = $("#pipeline-level").val();
            executingData.pipelineLevel = pipelineLevel;
        } else if (concurrentOption == "queue") {
            executingData.queueLevel = $("#queueLevel").val();
        }
        return executingData;
    },

});


var projectView;
azkaban.ProjectView = Backbone.View.extend({
    events: {
        "click #project-upload-btn": "handleUploadProjectJob",
        "click #project-upload-business-btn": "handleUploadBusinessJob",
        "click #project-upload-sch-btn": "handleUploadSchJob",
        "click #project-delete-btn": "handleDeleteProject",
        "click #start-all-flow-btn": "handleExecuteFlow",
        "click #start-all-schedule-btn": "handleScheduleFlow",
        "click #project-business-btn": "handleProjectBusiness"
    },

    initialize: function(settings) {

    },

    initTimeoutPanel: function() {
        console.log("init timeout-panel");
        $("#flow-timeout-option").prop("checked", false);
        $("#timeout-slaEmails").val(loginUser);
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
    initSchedulePanel: function() {
        $('#schedule-override-success-emails').prop('checked', false);
        $('#schedule-success-emails').attr('disabled', 'disabled');
        $('#schedule-custom-success-alert').attr('disabled', "disabled");
        $('#schedule-success-emails').val(loginUser);

        // $('#schedule-override-failure-emails').prop('checked', true);
        $('#schedule-failure-emails').attr('disabled', 'disabled');
        $('#schedule-custom-failure-alert').attr('disabled', "disabled");
        $('#schedule-failure-emails').val(loginUser);

        $('#schedule-notify-failure-first').prop('checked', true).parent('.btn').addClass('active');
        $('#schedule-notify-failure-last').prop('checked', false).parent('.btn').removeClass('active');
        $('#autoEnableFalse').prop('checked', true);
        $('#autoEnableFalseAlert').prop('checked', true);
        $('#schedule-graph-options-panel .graph-sidebar-open').hide();
        showSchedulePanel();
    },

    initExecuteFlowPanel: function() {

        var alertMsg = wtssI18n.view.alertMsg;
        $('#flowRetryAlertChecked').prop('checked', false);
        $('#alertMsg').attr('disabled', 'disabled');
        $('#alertMsg').val(alertMsg);

        $('#override-success-emails').prop('checked', false);
        $('#success-emails').attr('disabled', 'disabled');
        $('#custom-success-alert').attr('disabled', "disabled");
        $('#success-emails').val(loginUser);

        // $('#override-failure-emails').prop('checked', true);
        $('#failure-emails').attr('disabled', 'disabled');
        $('#custom-failure-alert').attr('disabled', "disabled");
        $('#failure-emails').val(loginUser);

        $('#flowRetryAlertChecked').prop('checked', false);
        $('#alertMsg').attr('disabled', 'disabled');

        $('#notify-failure-first').prop('checked', true);
        $('#notify-failure-first').parent('.btn').addClass('active');
        $('#concurrent-panel input[value="skip"][name="concurrent"]').prop( 'checked', true);
        //历史重跑界面初始化方法
        $("#enable-history-recover").prop('checked', false);
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
    },

    // 进入单个项目,执行所有调度
    handleScheduleFlow: function(evt) {

        // 需要校验是否具有执行调度权限 1:允许, 2:不允许
        var requestURL = "/manager?ajax=checkUserScheduleFlowPermission&project=" + projectName;
        $.ajax({
            url: requestURL,
            type: "get",
            async: false,
            dataType: "json",
            success: function(data) {
                if (data["scheduleFlowFlag"] == 1) {

                    projectView.initSchedulePanel();
                    $('#schedule-flow-panel-title').html(wtssI18n.view.schedulingAllFlow);
                    $('#project-upload-sch-btn').hide();
                    $("#schedule-flow-div").hide();
                    $("#shedule-all-flow-div").show();
                    $('#schedule-flow-option').hide();
                    $('#schedule-failure-li').hide();
                    $('#job-cron-li').hide();
                    sideMenuDialogView.menuSelect('#schedule-notification-li');
                    $('#schedule-flow-panel').modal();
                    $('#schedule-joblist-panel').hide();
                } else if (data["scheduleFlowFlag"] == 2) {
                    $('#user-onekey-schedule-flow-permit-panel').modal();
                }
                $('#open-execute-joblist-btn').hide()
                $('#execute-joblist-panel').hide()
                $("#switching-execute-flow-btn").hide()
                $("#workflow-execute-zoom-in").hide()
            }
        });
    },

    handleExecuteFlow: function(evt) {

        // 需要校验是否具有执行工作流权限 1:允许, 2:不允许
        var requestURL = "/manager?ajax=checkUserExecuteFlowPermission&project=" + projectName;
        $.ajax({
            url: requestURL,
            type: "get",
            async: false,
            dataType: "json",
            success: function(data) {
                if (data["executeFlowFlag"] == 1) {
                    projectView.initExecuteFlowPanel();
                    $("#execute-div").hide();
                    projectView.initTimeoutPanel();
                    $("#start-all-div").show();
                    $("#execute-flow-panel-title").text(wtssI18n.view.executeAllFlow);
                    $('#flow-option').hide();
                    $('#flow-execution-option').hide();
                    $('#history-recover-li').hide();
                    $('#cycle-execution-li').hide()
                    sideMenuDialogView.menuSelect('#flow-notification');
                    $('#execute-flow-panel').modal();

                } else if (data["executeFlowFlag"] == 2) {
                    $('#user-onekey-execute-flow-permit-panel').modal();
                }
                $('#open-execute-joblist-btn').hide()
                $('#execute-joblist-panel').hide()
                $("#switching-execute-flow-btn").hide()
                $("#workflow-execute-zoom-in").hide()
            }
        });
    },

    // 上传定时调度信息
    handleUploadSchJob: function(evt) {
        console.log("handleUploadSchJob");
        $('#upload-sch-modal').modal();
    },

    // 上传应用信息
    handleUploadBusinessJob: function(evt) {
        $('#upload-business-modal').modal();
    },

    // 上传项目flow
    handleUploadProjectJob: function(evt) {

        var requestURL1 = "/manager?ajax=checkUserUploadPermission&project=" + projectName;
        $.ajax({
            url: requestURL1,
            type: "get",
            async: false,
            dataType: "json",
            success: function(data) {
                if (data["userUploadFlag"] == 1) {
                    var requestURL2 = "/manager?ajax=checkDepUploadPermission&project=" + projectName;
                    $.ajax({
                        url: requestURL2,
                        type: "get",
                        async: false,
                        dataType: "json",
                        success: function(data) {
                            if (data["uploadFlag"] == 1) {
                                console.log("have permission, click upload project");
                                $('#upload-project-modal').modal();

                                $("#itsm-form-info").select2({
                                    placeholder: wtssI18n.view.itsmRelated,
                                    ajax: {
                                        url: "manager",
                                        type: "get",
                                        dataType: "json",
                                        delay: 250,
                                        data: function(params) {
                                            var query = {
                                                ajax: "getItsmListData4Aomp",
                                                currentPage: 1,
                                                keyword: params.term
                                            }
                                            return query;
                                        },
                                        processResults: function(data) {

                                            var formList = data.formList;
                                            var resultList = [];
                                            for (var i = 0; i < formList.length; i++) {
                                                var formItem = formList[i];
                                                var formId = formItem.id;
                                                var formName = formItem.title;
                                                var formType = formItem.typeText;
                                                resultList.push({
                                                    id: formId,
                                                    text: "[" + formType + "] " +
                                                        "[" + formId + "] " + formName
                                                });
                                            }

                                            // resultList.sort();
                                            return {
                                                results: resultList
                                            };
                                        }
                                    },
                                    // minimumInputLength: 3
                                });
                                $('#itsm-form-info-box .dropdown-toggle').hide() //隐藏itsm-form-info 多余的input框
                            } else {
                                $('#department-upload-permit-change-panel').modal();
                                $('#project-upload-btn').hide();
                            }
                        }
                    });
                } else {
                    $('#user-upload-permit-change-panel').modal();
                }
            }
        });


    },

    // 进入项目, 顶上操作项中的删除
    handleDeleteProject: function(evt) {

        // 需要校验是否具有执行工作流权限 1:允许, 2:不允许
        var requestURL = "/manager?ajax=checkDeleteProjectFlagPermission&project=" + projectName;
        $.ajax({
            url: requestURL,
            type: "get",
            async: false,
            dataType: "json",
            success: function(data) {
                if (data["deleteProjectFlag"] == 1) {
                    console.log("click delete project");
                    $("#delete-project-modal .modal-body p").empty();
                    $("#delete-project-modal .modal-body p").html("<strong>" + wtssI18n.view.warning + " :</strong> " + wtssI18n.view.projectDeletePro);
                    //判断是否有设置了定时调度
                    //http://127.0.0.1:8290/manager?ajax=ajaxFetchProjectSchedules&project=child-flow-test2
                    var requestURL = "/manager?ajax=ajaxFetchProjectSchedules&project=" + $("#delete-form [name='project']").val();
                    $.ajax({
                        url: requestURL,
                        async: false,
                        type: "GET",
                        dataType: "json",
                        success: function(data) {
                            if (data["hasSchedule"] == "Time Schedule") {
                                $("#delete-project-modal .modal-body p").empty();
                                $("#delete-project-modal .modal-body p").html("<strong>" + wtssI18n.view.warning + " :</strong> " + wtssI18n.view.scheduleProjectPro);
                            } else if (data["hasSchedule"] == "Event Schedule") {
                                $("#delete-project-modal .modal-body p").empty();
                                $("#delete-project-modal .modal-body p").html("<strong>" + wtssI18n.view.warning + " :</strong> " + wtssI18n.view.eventScheduleProjectPro);
                            }
                        }
                    });
                    $('#delete-project-modal').modal();

                } else {
                    $('#delete-project-permit-change-panel').modal();
                }
                $('#open-execute-joblist-btn').hide()
                $('#execute-joblist-panel').hide()
                $("#switching-execute-flow-btn").hide()
                $("#workflow-execute-zoom-in").hide()
            }
        });

    },
    initProjectBusinessForm: function () {
        $("#bus-type-first-select").empty();
        $("#bus-type-second-select").empty();
        $("#subsystem-select").empty();
        $("#bus-path-select").empty();
        $("#batch-group-select").empty();
        $("#bus-domain-select").empty();
    },
    handleProjectBusiness: function(evt) {
        this.initProjectBusinessForm();
        $('#merge-business-panel').modal();
        $("#merge-business-info-msg").hide();
        $('#flow-business-id').val("");
        $('#job-business-id').val("");
        mergeProjectBusinessView.render();
        mergeProjectBusinessView.loadBusinessData();
    },

    render: function() {}
});

var mergeProjectBusinessView;
azkaban.MergeProjectBusinessView = Backbone.View.extend({
    events: {
        "click #business-merge-btn": "handleMergeProjectBusiness"
    },

    initialize: function(settings) {
        $("#merge-business-error-msg").hide();
        $("#merge-business-info-msg").hide();
        $('#plan-start-time').datetimepicker({
            format: 'HH:mm'
        });
        $('#plan-finish-time').datetimepicker({
            format: 'HH:mm'
        });
        $('#last-start-time').datetimepicker({
            format: 'HH:mm'
        });
        $('#last-finish-time').datetimepicker({
            format: 'HH:mm'
        });
    },

    handleMergeProjectBusiness: function(evt) {
        var busTypeFirst = $("#bus-type-first-select").val();
        var busTypeFirstDesc = $("#bus-type-first-select option[value=" + busTypeFirst + "]").text();
        var busTypeSecond = $("#bus-type-second-select").val();
        var busTypeSecondDesc = $("#bus-type-second-select option[value=" + busTypeSecond + "]").text();
        var busDesc = $("#bus-desc").val();
        var subsystem = $("#subsystem-select").val();
        var subsystemDesc = $("#subsystem-select option[value=" + subsystem + "]").text();
        var busResLvl = $("#bus-res-lvl-select").val();
        var busPath = $("#bus-path-select").val();
        var busPathDesc = $("#bus-path-select option[value=" + busPath + "]").text();
        //    var batchTimeQuat = $("#batch-time-quat").val();
        //    var busErrInf = $("#bus-err-inf").val();
        var devDept = $("#bus-dev-dept-select").val();
        var devDeptDesc = devDept ? $("#bus-dev-dept-select option[value=" + devDept + "]").text() : '';
        var opsDept = $("#bus-ops-dept-select").val();
        var opsDeptDesc = opsDept ?  $("#bus-ops-dept-select option[value=" + opsDept + "]").text() : '';

        var scanPartitionNum = $("#scan-partition-num").val();
        var scanDataSize = $("#scan-data-size").val();
        //    var upperDep = $("#upper-dep").val();
        //    var lowerDep = $("#lower-dep").val();
        var batchGroup = $("#batch-group-select").val();
        var batchGroupDesc = $("#batch-group-select option[value=" + batchGroup + "]").text();
        var busDomain = $("#bus-domain-select").val();
        var earliestStartTime = $("#earliest-start-time").val();
        var latestEndTime = $("#latest-end-time").val();
        //    var relatedProduct = $("#related_product").val();
        var flowId = $("#flow-business-id").val();
        var jobId = $("#job-business-id").val();
        var planStartTime = $("#plan-start-time").val();
        var planFinishTime = $("#plan-finish-time").val();
        var lastStartTime = $("#last-start-time").val();
        var lastFinishTime = $("#last-finish-time").val();
        var itsmNo = $("#itsm-number").val()
        // var alertLevel = $("#alert-level").val();
        // var dcnNumber = $("#dcn-number").val();
        // var imsUpdater = $("#ims-updater-select").val() ? $("#ims-updater-select").val().join(';') : "";
        // var imsRemark = $("#ims-remark").val();
        if (!busDomain || !subsystem || !busResLvl || !planStartTime || !planFinishTime || !lastStartTime || !lastFinishTime || !devDept || !opsDept || !scanPartitionNum || !scanDataSize) {
            messageBox.show('有必填项未填', 'warning');
            return;
        }
        var requestURL = "/manager";

        var requestData = {
            "projectId": projectId,
            "flowId": flowId,
            "jobId": jobId,
            "ajax": "mergeFlowBusiness",
            "busTypeFirst": busTypeFirst,
            "busTypeFirstDesc": busTypeFirstDesc,
            "busTypeSecond": busTypeSecond,
            "busTypeSecondDesc": busTypeSecondDesc,
            "busDesc": busDesc,
            "subsystem": subsystem,
            "subsystemDesc": subsystemDesc,
            "busResLvl": busResLvl,
            "busPath": busPath,
            "busPathDesc": busPathDesc,
            //      "batchTimeQuat": batchTimeQuat,
            //      "busErrInf": busErrInf,
            "devDept": devDept,
            "devDeptDesc": devDeptDesc,
            "opsDept": opsDept,
            "opsDeptDesc": opsDeptDesc,
            "scanPartitionNum":  scanPartitionNum,
            "scanDataSize":  scanDataSize,
            //      "upperDep": upperDep,
            //      "lowerDep": lowerDep,
            "batchGroup": batchGroup,
            "batchGroupDesc": batchGroupDesc,
            "busDomain": busDomain,
            //      "earliestStartTime": earliestStartTime,
            //      "latestEndTime": latestEndTime
            "planStartTime": planStartTime,
            "planFinishTime": planFinishTime,
            "lastStartTime": lastStartTime,
            "lastFinishTime": lastFinishTime,
            "itsmNo": itsmNo,
            //   "alertLevel": alertLevel,
            //   "dcnNumber": dcnNumber,
            //   "imsUpdater": imsUpdater,
            //   "imsRemark": imsRemark
            //      "relatedProduct": relatedProduct
        };
        $('#business-merge-btn').prop('disabled', true)
        var successHandler = function(data) {
            if (data.errorMsg || data.error && !data.itsmNo) {
                $("#merge-business-error-msg").show();
                $("#merge-business-error-msg").text(data.errorMsg || data.error);
                $('#business-merge-btn').prop('disabled', false)
                return false;
            } else {
                console.log(data)
                alert(data.requestInfo);
                $('#merge-business-panel').modal("hide");
            }
            $('#business-merge-btn').prop('disabled', false)
            $("#merge-business-error-msg").hide();
        };
        $.post(requestURL, requestData, successHandler, "json");
    },
    handleOptionBusinessData: function (projectBusiness, key) {
        var label = key === 'busDomain' ? key : key + 'Desc'
        return projectBusiness && projectBusiness[key] ? {text: projectBusiness[label], id: projectBusiness[key]} : '';
    },
    loadBusinessData: function() {
        var that = this;
        var requestURL = "/manager";
        var flowId = $("#flow-business-id").val();
        var jobId = $("#job-business-id").val();

        var requestData = {
            "ajax": "getFlowBusiness",
            "projectId": projectId,
            "flowId": flowId,
            "jobId": jobId,
            "isLoaded": isCmdbLoaded
        };
        var successHandler = function(data) {
            if (data.errorMsg) {
                $("#merge-business-error-msg").show();
                $("#merge-business-error-msg").text(data.errorMsg);
                return false;
            } else {
                fetchCmdbData("bus-type-first-select", 'wb_product_category', 'category_id', 'pro_category', that.handleOptionBusinessData(data.projectBusiness,'busTypeFirst'));
                fetchCmdbData("bus-type-second-select", 'wb_product', 'pro_id', 'pro_name', that.handleOptionBusinessData(data.projectBusiness,'busTypeSecond'));
                fetchCmdbData("subsystem-select", 'wb_subsystem', 'subsystem_id', 'subsystem_name', that.handleOptionBusinessData(data.projectBusiness,'subsystem'));
                fetchCmdbData("bus-path-select", 'wb_batch_critical_path', 'id', 'name', that.handleOptionBusinessData(data.projectBusiness,'busPath'));
                fetchCmdbData("batch-group-select", 'wb_batch_group', 'group_id', 'group_name', that.handleOptionBusinessData(data.projectBusiness,'batchGroup'));
                fetchCmdbData("bus-domain-select", 'subsystem_app_instance', 'appdomain_cnname', 'appdomain_cnname', that.handleOptionBusinessData(data.projectBusiness,'busDomain'));

                //开发部门
                if (data.busDeptSelectList) {
                    //每次新增option,需要清空select,避免造成重复数据
                    $("#bus-dev-dept-select").find("option:selected").text("");
                    $("#bus-dev-dept-select").empty();
                    //运维部门
                    $("#bus-ops-dept-select").find("option:selected").text("");
                    $("#bus-ops-dept-select").empty();
                    var optionHtml = "";
                    for (var i = 0; i < data.busDeptSelectList.length; i++) {
                        optionHtml += "<option value='" + data.busDeptSelectList[i].dpId + "'>" + data.busDeptSelectList[i].dpChName + "</option>"
                    }
                    optionHtml = filterXSS(optionHtml, { 'whiteList': { 'option': ['value'] } })
                    $('#bus-dev-dept-select').append(optionHtml);
                    $('#bus-ops-dept-select').append(optionHtml);

                    //要以编程方式更新JavaScript的选择，首先操作选择，然后使用refresh方法更新UI以匹配新状态。 在删除或添加选项时，或通过JavaScript禁用/启用选择时，这是必需的。
                    $('#bus-dev-dept-select').selectpicker('refresh');
                    $('#bus-ops-dept-select').selectpicker('refresh');
                    //render方法强制重新渲染引导程序 - 选择ui,如果当您编程时更改任何相关值而影响元素布局，这将非常有用。
                    $('#bus-dev-dept-select').selectpicker('render');
                    $('#bus-ops-dept-select').selectpicker('render');
                }

                // if (data.imsUpdaterList) {
                //     $("#ims-updater-select").find("option:selected").text("");
                //     $("#ims-updater-select").empty();
                //     var optionHtml = "";
                //     for (var i = 0; i < data.imsUpdaterList.length; i++) {
                //         optionHtml += "<option value='" + data.imsUpdaterList[i].username + "'>" + data.imsUpdaterList[i].fullName + "</option>"
                //     }
                //     optionHtml = filterXSS(optionHtml, { 'whiteList': { 'option': ['value'] } })
                //     $('#ims-updater-select').append(optionHtml);
                //     $('#ims-updater-select').selectpicker('refresh');
                //     $('#ims-updater-select').selectpicker('render');
                // }

                isCmdbLoaded = true;

                if (data.projectBusiness) {
                    $("#bus-desc").val(data.projectBusiness.busDesc);
                    $("#bus-res-lvl-select").val(data.projectBusiness.busResLvl);
                    //          $("#batch-time-quat").val(data.projectBusiness.batchTimeQuat);
                    //          $("#bus-err-inf").val(data.projectBusiness.busErrInf);
                    $("#bus-dev-dept-select").val(data.projectBusiness.devDept);
                    $("#bus-ops-dept-select").val(data.projectBusiness.opsDept);
                    $("#scan-partition-num").val(data.projectBusiness.scanPartitionNum);
                    $("#scan-data-size").val(data.projectBusiness.scanDataSize);
                    //          $("#upper-dep").val(data.projectBusiness.upperDep);
                    //          $("#lower-dep").val(data.projectBusiness.lowerDep);
                    //          $("#earliest-start-time").val(data.projectBusiness.earliestStartTime);
                    //          $("#latest-end-time").val(data.projectBusiness.latestEndTime);
                    //          $("#related_product").val(data.projectBusiness.relatedProduct);
                    $("#plan-start-time").val(data.projectBusiness.planStartTime);
                    $("#plan-finish-time").val(data.projectBusiness.planFinishTime);
                    $("#last-start-time").val(data.projectBusiness.lastStartTime);
                    $("#last-finish-time").val(data.projectBusiness.lastFinishTime);
                    $("#itsm-number").val(data.projectBusiness.itsmNo);
                    if(data.projectBusiness.itsmNo){
                        $("#merge-business-info-msg").show();
                        $("#merge-business-info-msg").text(`上一次设置的审批单号为：${data.projectBusiness.itsmNo}`)
                    }
                    // $("#alert-level").val(data.projectBusiness.alertLevel);
                    // $("#dcn-number").val(data.projectBusiness.dcnNumber);
                    // $("#ims-updater-select").val(data.projectBusiness.imsUpdater ? data.projectBusiness.imsUpdater.split(';') : null);
                    // $("#ims-remark").val(data.projectBusiness.imsRemark);
                } else {
                    $("#bus-desc").val("");
                    $("#bus-res-lvl-select").val("");
                    //          $("#batch-time-quat").val("");
                    //          $("#bus-err-inf").val("");
                    $("#bus-dev-dept-select").val("");
                    $("#bus-ops-dept-select").val("");
                    //          $("#upper-dep").val("");
                    //          $("#lower-dep").val("");
                    //          $("#earliest-start-time").val("");
                    //          $("#latest-end-time").val("");
                    //          $("#related_product").val("");
                    $("#plan-start-time").val("");
                    $("#plan-finish-time").val("");
                    $("#last-start-time").val("");
                    $("#last-finish-time").val("");
                    $("#scan-partition-num").val("");
                    $("#scan-data-size").val("");
                    $("#itsm-number").val("")
                    // $("#alert-level").val("");
                    // $("#dcn-number").val("");
                    // $("#ims-updater-select").val(null);
                    // $("#ims-remark").val("");
                }

                //要以编程方式更新JavaScript的选择，首先操作选择，然后使用refresh方法更新UI以匹配新状态。 在删除或添加选项时，或通过JavaScript禁用/启用选择时，这是必需的。
                $('#bus-dev-dept-select').selectpicker('refresh');
                $('#bus-ops-dept-select').selectpicker('refresh');
                $('#bus-res-lvl-select').selectpicker('refresh');
                // $('#alert-level').selectpicker('refresh');
                // $('#ims-updater-select').selectpicker('refresh');
            }
        };
        $.get(requestURL, requestData, successHandler, "json");

    },

    render: function() {
        $("#merge-business-error-msg").hide();
    }
});

var uploadProjectView;
azkaban.UploadProjectView = Backbone.View.extend({
    events: {
        "click #upload-project-btn": "handleCreateProject"
    },

    initialize: function(settings) {
        console.log("Hide upload project modal error msg");
        $("#upload-project-modal-error-msg").hide();
    },

    handleCreateProject: function(evt) {
        console.log("Upload project button.");
        $("#upload-project-form").submit();
    },

    render: function() {}
});

var uploadBusinessView;
azkaban.UploadBusinessView = Backbone.View.extend({
    events: {
        "click #upload-business-btn": "handleCreateBusiness"
    },

    initialize: function(settings) {
        console.log("Hide upload business modal error msg");
        $("#upload-business-modal-error-msg").hide();
    },

    handleCreateBusiness: function(evt) {
        console.log("Upload business button.");
        $("#upload-business-form").submit();
    },

    render: function() {}
});

var uploadSchView;
azkaban.UploadSchView = Backbone.View.extend({
    events: {
        "click #upload-sch-btn": "handleCreateSch"
    },

    initialize: function(settings) {
        console.log("Hide upload sch modal error msg");
        $("#upload-sch-modal-error-msg").hide();
    },

    handleCreateSch: function(evt) {
        console.log("Upload sch button.");
        $("#upload-sch-form").submit();
    },

    render: function() {}
});

var deleteProjectView;
azkaban.DeleteProjectView = Backbone.View.extend({
    events: {
        "click #delete-btn": "handleDeleteProject"
    },

    initialize: function(settings) {},

    handleDeleteProject: function(evt) {
        var requestData = {
            "project": projectName,
            "delete": true
        };
        var successHandler = function(data) {
            $('#delete-project-modal').modal('hide');
            if(data.status === "error") {
                messageBox.show(data.message, "danger");
                return;
            }
            messageBox.show(projectName + '删除成功');
            setTimeout(() => {
                window.location.href = "/index";
            }, 2000);
        };
        $.get('/manager', requestData, successHandler, "json");
    },

    render: function() {}
});

var projectDescription;
azkaban.ProjectDescriptionView = Backbone.View.extend({
    events: {
        "click #project-description": "handleDescriptionEdit",
        "click #project-description-btn": "handleDescriptionSave",
        "click #project-create-user-btn": "handleCreateUserSave",
        "click #project-create-user": "handleCreateUserEdit",
        "click #project-principal-btn": "handleOwnerSave",
        "click #project-principal": "handleOwnerEdit",
        "click #project-job-limit-btn": "handleJobLimitSave",
        "click #project-job-limit": "handleJobLimitEdit"
    },

    initialize: function(settings) {
        console.log("project description initialize");
    },

    handleCreateUserEdit: function(evt) {
        console.log("Edit create user");
        $('#project-create-user-edit').val($("#project-create-user").text());
        $('#project-create-user').hide();
        $('#project-create-user-form').show();
    },

    handleOwnerEdit: function(evt) {
        console.log("Edit create user");
        $('#project-principal-edit').val($("#project-principal").text());
        $('#project-principal').hide();
        $('#project-principal-form').show();
    },

    handleDescriptionEdit: function(evt) {
        console.log("Edit description");
        var description = null;
        if ($('#project-description').hasClass('editable-placeholder')) {
            description = '';
            $('#project-description').removeClass('editable-placeholder');
        } else {
            description = $('#project-description').text();
        }
        $('#project-description-edit').attr("value", description);
        $('#project-description').hide();
        $('#project-description-form').show();
    },

    handleDescriptionSave: function(evt) {
        var newText = $('#project-description-edit').val();
        if ($('#project-description-edit').hasClass('has-error')) {
            $('#project-description-edit').removeClass('has-error');
        }
        var requestURL = "/manager";
        var requestData = {
            "project": projectName,
            "ajax": "changeDescription",
            "description": newText
        };
        var successHandler = function(data) {
            if (data.error) {
                $('#project-description-edit').addClass('has-error');
                alert(data.error);
                return;
            }
            $('#project-description-form').hide();
            if (newText != '') {
                $('#project-description').text(newText);
            } else {
                $('#project-description').text('Add project description.');
                $('#project-description').addClass('editable-placeholder');
            }
            $('#project-description').show();
        };
        $.get(requestURL, requestData, successHandler, "json");
    },

    handleCreateUserSave: function(evt) {
        var requestURL = "/manager";
        var requestData = {
            "project": projectName,
            "ajax": "changeCreateUser",
            "createUser": $('#project-create-user-edit').val()
        };
        var successHandler = function(data) {
            if (data.error) {
                alert(data.error);
                return;
            }
            $('#project-create-user-form').hide();
            $('#project-create-user').text($('#project-create-user-edit').val());
            $('#project-create-user').show();
        };
        $.get(requestURL, requestData, successHandler, "json");
    },

    handleOwnerSave: function(evt) {
        var requestURL = "/manager";
        var requestData = {
            "project": projectName,
            "ajax": "changePrincipal",
            "principal": $('#project-principal-edit').val()
        };
        var successHandler = function(data) {
            if (data.error) {
                alert(data.error);
                return;
            }
            $('#project-principal-form').hide();
            $('#project-principal').text($('#project-principal-edit').val());
            $('#project-principal').show();
        };
        $.get(requestURL, requestData, successHandler, "json");
    },

    handleJobLimitEdit: function(evt) {
        $('#project-job-limit-edit').val($("#project-job-limit").text());
        $('#project-job-limit').hide();
        $('#project-job-limit-form').show();
    },

    handleJobLimitSave: function(evt) {
        var jobLimit = $('#project-job-limit-edit').val();
        if (!isNaN()) {
            $('#project-job-limit-edit').val('');
        }
        var requestURL = "/manager";
        var requestData = {
            "project": projectName,
            "ajax": "changeJobLimit",
            "jobLimit": jobLimit
        };
        var successHandler = function(data) {
            if (data.error) {
                alert(data.error);
                return;
            }
            $('#project-job-limit-form').hide();
            $('#project-job-limit').text($('#project-job-limit-edit').val());
            $('#project-job-limit').show();
        };
        $.get(requestURL, requestData, successHandler, "json");
    },

    render: function() {}
});

$(function() {
    projectView = new azkaban.ProjectView({
        el: $('#project-options'),
        model: {}
    });
    uploadProView = new azkaban.UploadProjectView({
        el: $('#upload-project-modal')
    });
    uploadBusView = new azkaban.UploadBusinessView({
        el: $('#upload-business-modal')
    });
    uploadScheduleView = new azkaban.UploadSchView({
        el: $('#upload-sch-modal')
    });
    deleteProjectView = new azkaban.DeleteProjectView({
        el: $('#delete-project-modal')
    });
    projectDescription = new azkaban.ProjectDescriptionView({
        el: $('#project-sidebar')
    });
    executeAllFlowView = new azkaban.ExecuteAllFlowView({
        el: $('#execute-flow-panel')
    });
    scheduleAllFlowView = new azkaban.ScheduleAllFlowView({
        el: $('#schedule-flow-panel')
    });
    mergeProjectBusinessView = new azkaban.MergeProjectBusinessView({
        el: $('#merge-business-panel')
    });
    //上传文件绑定事件
    document.getElementById('file').addEventListener('change', function() {
        document.getElementById('fieldsNameBox').innerHTML = filterXSS(this.files[0].name)
    }, false)
    //上传应用信息文件绑定事件
    document.getElementById('businessfile').addEventListener('change', function() {
        document.getElementById('businessfilefieldsNameBox').innerHTML = filterXSS(this.files[0].name)
    }, false)
    //上传定时调度信息文件绑定事件
    document.getElementById('schfile').addEventListener('change', function() {
        document.getElementById('schfilefieldsNameBox').innerHTML = filterXSS(this.files[0].name)
    }, false)
});