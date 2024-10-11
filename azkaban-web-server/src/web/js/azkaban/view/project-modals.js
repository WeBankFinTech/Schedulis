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

  initialize: function (settings) {
  },

  getExecutionOptionData: function () {
    // 将 '完成所有可执行的任务' 设置为默认
    var failureAction = 'finishPossible';
    var failureEmails = $('#schedule-failure-emails').val();
    var successEmails = $('#schedule-success-emails').val();
    var notifyFailureFirst = $('#schedule-notify-failure-first').is(':checked');
    var notifyFailureLast = $('#schedule-notify-failure-last').is(':checked');
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
      successEmails: successEmails,
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
    }
    else if (concurrentOption == "queue") {
      executingData.queueLevel = $("#schedule-queueLevel").val();
    }

    return executingData;
  },
  handleScheduleAllFlow: function (evt) {
    var scheduleURL = contextURL + "/schedule?ajax=ajaxScheduleCronAllFlow"
    var scheduleData = this.getExecutionOptionData();

    console.log("Creating schedule for " + projectName + "."
      + scheduleData.flow);

    var currentMomentTime = moment();
    var scheduleTime = currentMomentTime.utc().format('h,mm,A,') + "UTC";
    var scheduleDate = currentMomentTime.format('MM/DD/YYYY');

    scheduleData.ajax = "ajaxScheduleCronAllFlow";
    scheduleData.projectName = projectName;
    scheduleData.cronExpression = "0 " + $(schedule_cron_output_id).val();

    console.log("current Time = " + scheduleDate + "  " + scheduleTime);
    console.log("cronExpression = " + scheduleData.cronExpression);
    var retSignal = validateQuartzStr(scheduleData.cronExpression.trim());

    if (retSignal == "NUM_FIELDS_ERROR") {
      var tipMsg1 = "Cron 表达式错误, 一个有效的Cron表达式至少有6个或者7个属性."
      if (langType === "en_US") {
        tipMsg1 = "Cron Syntax Error, A valid Quartz Cron Expression should have 6 or 7 fields.";
      }
      alert(tipMsg1);
      return;
    } else if (retSignal == "DOW_DOM_STAR_ERROR") {
      var tipMsg2 = "Cron 表达式错误, 月的某日和周的某天不能同时有值" + "(你必须将其中一个的值改为 ‘?’). <a href=\"http://www.quartz-scheduler.org/documentation/quartz-2.x/tutorials/crontrigger.html\" target=\"_blank\">详情请见</a>";
      if (langType === "en_US") {
        tipMsg2 = "Cron Syntax Error, Currently Quartz doesn't support specifying both a day-of-week and a day-of-month value"
          + "(you must use the ‘?’ character in one of these fields). <a href=\"http://www.quartz-scheduler.org/documentation/quartz-2.x/tutorials/crontrigger.html\" target=\"_blank\">Detailed Explanation</a>";
      }
      alert(tipMsg2)
      return;
    }

    var successHandler = function (data) {
      if (data.error) {
        alert(data.error);
      }
      else {
        flowScheduleDialogView.hideScheduleOptionPanel();
        messageDialogView.show(wtssI18n.view.schedulingAllFlow, data.message, function () {
          window.location.href = contextURL + "/schedule";
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

  initialize: function (settings) {
  },
  //校验超时告警设置是否正确
  checkTimeoutAlertSetting: function () {
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

  handleExecuteAllFlow: function (evt) {
    console.log("click execute all flow button.");
    if (!this.checkTimeoutAlertSetting()) {
      return;
    }
    var executingData = this.getExecutionOptionData();
    var executeURL = contextURL + "/executor?ajax=executeAllFlow";
    $.ajax({
      url: executeURL,
      data: JSON.stringify(executingData),
      dataType: "json",
      type: "POST",
      contentType: "application/json; charset=utf-8",
      success: function (data) {
        console.log("exec success");
        if (data.error) {
          flowExecuteDialogView.hideExecutionOptionPanel();
          messageDialogView.show(wtssI18n.common.workflowError, data.error);
        }
        else {
          flowExecuteDialogView.hideExecutionOptionPanel();
          messageDialogView.show(wtssI18n.common.workflowSubmit, data.message,
            function () {
              var redirectURL = contextURL + "/executor";
              window.location.href = redirectURL;
            }
          );
        }
      },
    });
  },

  getExecutionOptionData: function () {
    // 将 '完成所有可执行的任务' 设置为默认
    var failureAction = 'finishPossible';
    var failureEmails = $('#failure-emails').val();
    var successEmails = $('#success-emails').val();
    var notifyFailureFirst = $('#notify-failure-first').is(':checked');
    var notifyFailureLast = $('#notify-failure-last').is(':checked');
    var failureEmailsOverride = $("#override-failure-emails").is(':checked');
    var successEmailsOverride = $("#override-success-emails").is(':checked');
    //告警级别选择
    var failureAlertLevel = $('#override-failure-alert-level').val();
    var successAlertLevel = $('#override-success-alert-level').val();

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
    settings[0] = "," + $("#timeout-status").val() + "," + $("#timeout-second").val() + ","
      + $("#timeout-level").val() + "," + $("#timeout-email").is(":checked") + ","
      + $("#timeout-killflow").is(":checked");


    var executingData = {
      projectId: projectId,
      project: projectName,
      disabled: [],
      failureEmailsOverride: failureEmailsOverride,
      successEmailsOverride: successEmailsOverride,
      failureAction: failureAction,
      failureEmails: failureEmails,
      successEmails: successEmails,
      notifyFailureFirst: notifyFailureFirst,
      notifyFailureLast: notifyFailureLast,
      flowOverride: flowOverride,
      jobFailedRetryOptions: {},
      failureAlertLevel: failureAlertLevel,
      successAlertLevel: successAlertLevel,
      jobSkipFailedOptions: {},
      useTimeoutSetting: useTimeoutSetting,
      slaEmails: slaEmails,
      settings: settings
    };
    var concurrentOption = $('input[name=concurrent]:checked').val();
    executingData.concurrentOption = concurrentOption;
    if (concurrentOption == "pipeline") {
      var pipelineLevel = $("#pipeline-level").val();
      executingData.pipelineLevel = pipelineLevel;
    }
    else if (concurrentOption == "queue") {
      executingData.queueLevel = $("#queueLevel").val();
    }
    return executingData;
  },

});


var projectView;
azkaban.ProjectView = Backbone.View.extend({
  events: {
    "click #project-upload-btn": "handleUploadProjectJob",
    "click #project-delete-btn": "handleDeleteProject",
    "click #start-all-flow-btn": "handleExecuteFlow",
    "click #start-all-schedule-btn": "handleScheduleFlow"
  },

  initialize: function (settings) {
  },

  initTimeoutPanel: function () {
    console.log("init timeout-panel");
    $("#flow-timeout-option").attr("checked", false);
    $("#timeout-slaEmails").val("");
    $("#timeout-status").val("SUCCESS");
    $("#timeout-second").val("");
    $("#timeout-level").val("INFO");
    $("#timeout-email").attr("checked", false);
    $("#timeout-killflow").attr("checked", false);
    $('.durationpick').datetimepicker({
      format: 'HH:mm'
    });
    $("#flow-timeout-model").hide();
  },
  initSchedulePanel: function () {
    $('#schedule-override-success-emails').attr('checked', false);
    $('#schedule-success-emails').attr('disabled', 'disabled');

    $('#schedule-override-failure-emails').attr('checked', false);
    $('#schedule-failure-emails').attr('disabled', 'disabled');

    $('#schedule-override-failure-emails').attr('checked', false);
    $('#schedule-failure-emails').attr('disabled', 'disabled');

    $('#schedule-notify-failure-first').attr('checked', true);
    $('#schedule-notify-failure-first').parent('.btn').addClass('active');
    showSchedulePanel();
  },

  initExecuteFlowPanel: function () {

    $('#override-success-emails').attr('checked', false);
    $('#success-emails').attr('disabled', 'disabled');

    $('#override-failure-emails').attr('checked', false);
    $('#failure-emails').attr('disabled', 'disabled');

    $('#override-failure-emails').attr('checked', false);
    $('#failure-emails').attr('disabled', 'disabled');

    $('#notify-failure-first').attr('checked', true);
    $('#notify-failure-first').parent('.btn').addClass('active');

  },

  // 进入单个项目,执行所有调度
  handleScheduleFlow: function (evt) {

    // 需要校验是否具有执行调度权限 1:允许, 2:不允许
    var requestURL = contextURL + "/manager?ajax=checkUserScheduleFlowPermission&project=" + projectName;
    $.ajax({
      url: requestURL,
      type: "get",
      async: false,
      dataType: "json",
      success: function (data) {
        if (data["scheduleFlowFlag"] == 1) {

          projectView.initSchedulePanel();
          $('#schedule-flow-panel-title').html(wtssI18n.view.schedulingAllFlow);
          $("#schedule-flow-div").hide();
          $("#shedule-all-flow-div").show();
          $('#schedule-flow-option').hide();
          $('#schedule-failure-li').hide();
          $('#job-cron-li').hide();
          sideMenuDialogView.menuSelect('#schedule-notification-li');
          $('#schedule-flow-panel').modal();

        } else if (data["scheduleFlowFlag"] == 2) {
          $('#user-onekey-schedule-flow-permit-panel').modal();
        }
        $("#switching-execute-flow-btn").hide()
        $("#workflow-execute-zoom-in").hide()
      }
    });
  },

  handleExecuteFlow: function (evt) {

    // 需要校验是否具有执行工作流权限 1:允许, 2:不允许
    var requestURL = contextURL + "/manager?ajax=checkUserExecuteFlowPermission&project=" + projectName;
    $.ajax({
      url: requestURL,
      type: "get",
      async: false,
      dataType: "json",
      success: function (data) {
        if (data["executeFlowFlag"] == 1) {
          projectView.initExecuteFlowPanel();
          $("#execute-div").hide();
          projectView.initTimeoutPanel();
          $("#start-all-div").show();
          $("#execute-flow-panel-title").text(wtssI18n.view.executeAllFlow);
          $('#flow-option').hide();
          $('#flow-execution-option').hide();
          $('#cycle-execution-li').hide()
          sideMenuDialogView.menuSelect('#flow-notification');
          $('#execute-flow-panel').modal();

        } else if (data["executeFlowFlag"] == 2) {
          $('#user-onekey-execute-flow-permit-panel').modal();
        }
        $("#switching-execute-flow-btn").hide()
        $("#workflow-execute-zoom-in").hide()
      }
    });
  },

  // 上传项目flow
  handleUploadProjectJob: function (evt) {

    var requestURL1 = "/manager?ajax=checkUserUploadPermission&project=" + projectName;
    $.ajax({
      url: requestURL1,
      type: "get",
      async: false,
      dataType: "json",
      success: function (data) {
        if (data["userUploadFlag"] == 1) {
          var requestURL2 = "/manager?ajax=checkDepUploadPermission&project=" + projectName;
          $.ajax({
            url: requestURL2,
            type: "get",
            async: false,
            dataType: "json",
            success: function (data) {
              if (data["uploadFlag"] == 1) {
                console.log("have permission, click upload project");
                $('#upload-project-modal').modal();
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
  handleDeleteProject: function (evt) {

    // 需要校验是否具有执行工作流权限 1:允许, 2:不允许
    var requestURL = "/manager?ajax=checkDeleteProjectFlagPermission&project=" + projectName;
    $.ajax({
      url: requestURL,
      type: "get",
      async: false,
      dataType: "json",
      success: function (data) {
        if (data["deleteProjectFlag"] == 1) {
          console.log("click delete project");
          $("#delete-project-modal .modal-body p").empty();
          $("#delete-project-modal .modal-body p").html("<strong>" + wtssI18n.view.warning + " :</strong> " + wtssI18n.view.projectDeletePro);
          //判断是否有设置了定时调度
          //http://webip:port/manager?ajax=ajaxFetchProjectSchedules&project=child-flow-test2
          var requestURL = "/manager?ajax=ajaxFetchProjectSchedules&project=" + $("#delete-form [name='project']").val();
          $.ajax({
            url: requestURL,
            async: false,
            type: "GET",
            dataType: "json",
            success: function (data) {
              if (data["hasSchedule"]) {
                $("#delete-project-modal .modal-body p").empty();
                $("#delete-project-modal .modal-body p").html("<strong>" + wtssI18n.view.warning + " :</strong> " + wtssI18n.view.scheduleProjectPro);
              }
            }
          });
          $('#delete-project-modal').modal();

        } else {
          $('#delete-project-permit-change-panel').modal();
        }
        $("#switching-execute-flow-btn").hide()
        $("#workflow-execute-zoom-in").hide()
      }
    });

  },

  render: function () {
  }
});

var uploadProjectView;
azkaban.UploadProjectView = Backbone.View.extend({
  events: {
    "click #upload-project-btn": "handleCreateProject"
  },

  initialize: function (settings) {
    console.log("Hide upload project modal error msg");
    $("#upload-project-modal-error-msg").hide();
  },

  handleCreateProject: function (evt) {
    console.log("Upload project button.");
    $("#upload-project-form").submit();
  },

  render: function () {
  }
});

var deleteProjectView;
azkaban.DeleteProjectView = Backbone.View.extend({
  events: {
    "click #delete-btn": "handleDeleteProject"
  },

  initialize: function (settings) {
  },

  handleDeleteProject: function (evt) {
    $("#delete-form").submit();
  },

  render: function () {
  }
});

var projectDescription;
azkaban.ProjectDescriptionView = Backbone.View.extend({
  events: {
    "click #project-description": "handleDescriptionEdit",
    "click #project-description-btn": "handleDescriptionSave"
  },

  initialize: function (settings) {
    console.log("project description initialize");
  },

  handleDescriptionEdit: function (evt) {
    console.log("Edit description");
    var description = null;
    if ($('#project-description').hasClass('editable-placeholder')) {
      description = '';
      $('#project-description').removeClass('editable-placeholder');
    }
    else {
      description = $('#project-description').text();
    }
    $('#project-description-edit').attr("value", description);
    $('#project-description').hide();
    $('#project-description-form').show();
  },

  handleDescriptionSave: function (evt) {
    var newText = $('#project-description-edit').val();
    if ($('#project-description-edit').hasClass('has-error')) {
      $('#project-description-edit').removeClass('has-error');
    }
    var requestURL = contextURL + "/manager";
    var requestData = {
      "project": projectName,
      "ajax": "changeDescription",
      "description": newText
    };
    var successHandler = function (data) {
      if (data.error) {
        $('#project-description-edit').addClass('has-error');
        alert(data.error);
        return;
      }
      $('#project-description-form').hide();
      if (newText != '') {
        $('#project-description').text(newText);
      }
      else {
        $('#project-description').text('Add project description.');
        $('#project-description').addClass('editable-placeholder');
      }
      $('#project-description').show();
    };
    $.get(requestURL, requestData, successHandler, "json");
  },

  render: function () {
  }
});

$(function () {
  projectView = new azkaban.ProjectView({
    el: $('#project-options'),
    model: {}
  });
  uploadView = new azkaban.UploadProjectView({
    el: $('#upload-project-modal')
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
  //上传文件绑定事件
  document.getElementById('file').addEventListener('change', function () {
    document.getElementById('fieldsNameBox').innerHTML = this.files[0].name
  }, false)
});
