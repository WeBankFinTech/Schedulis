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

// 删除摘要中的定时调度
function removeSched(scheduleId) {


    // 需要校验是否具有删除工作流调度权限 1:允许, 2:不允许
    var requestURL = contextURL + "/manager?ajax=checkDeleteScheduleInDescriptionFlagPermission";
    $.ajax({
        url: requestURL,
        type: "get",
        async: false,
        dataType: "json",
        success: function(data){
            if(data["deleteDescScheduleFlag"] == 1){
                var scheduleURL = contextURL + "/schedule"
                var requestData = {
                    "action": "removeSched",
                    "scheduleId": scheduleId
                };
                var successHandler = function (data) {
                    if (data.error) {
                        $('#errorMsg').text(data.error);
                    }
                    else {
                        window.location = scheduleURL;
                    }
                };
                $.post(scheduleURL, requestData, successHandler, "json");

            } else {
                $('#desc-delete-sch-permit-panel').modal();
            }
        }
    });

}

function removeSla(scheduleId) {
  var scheduleURL = contextURL + "/schedule"
  var requestData = {
    "action": "removeSla",
    "scheduleId": scheduleId
  };
  var successHandler = function (data) {
    if (data.error) {
      $('#errorMsg').text(data.error)
    }
    else {
      window.location = scheduleURL
    }
  };
  $.post(scheduleURL, requestData, successHandler, "json");
}

// 定时调度页面, 定时调度工作流列表, 对显示的调度任务点击调度配置
function editScheduleClick(scheduleId, projectName, flowName, cronExpression) {

    // 需要校验是否具有修改项目调度权限 1:允许, 2:不允许
    var requestURL = contextURL + "/manager?ajax=checkUserUpdateScheduleFlowPermission&project=" + projectName;
    $.ajax({
        url: requestURL,
        type: "get",
        async: false,
        dataType: "json",
        success: function(data){
            if(data["updateScheduleFlowFlag"] == 1){
                console.log("click edit schedule button.");

                var executingData = {
                    scheduleId: scheduleId,
                    project: projectName,
                    flow: flowName,
                    scheduleFlowTitle:data["scheduleFlowTitle"]
                };

              flowScheduleDialogView.show(executingData);
              scheduleJobCronView.setFlowID(flowName, projectName);
              //初始化当前所选定时调度的错误重试参数值
              jobScheduleFailedRetryView.setFlowID(flowName, projectName);
              flowScheduleDialogView.loadScheduleRunningInfo(executingData);
              //初始化当前所选定时调度的错误跳过参数值
              scheduleJobSkipFailedView.setFlowID(flowName, projectName);
            }else if(data["updateScheduleFlowFlag"] == 2){
                $('#user-operator-schedule-flow-permit-panel').modal();
                $('#title-user-operator-schedule-flow-permit').text(wtssI18n.view.scheduleConfigPermission);
                $('#body-user-operator-schedule-flow-permit').html(wtssI18n.view.noScheConfigPermission);
            }
        }
    });

}

// 定时调度页面, 定时调度工作流列表, 对显示的调度任务点击调度开启关闭
function switchScheduleClick (index, scheduleId, projectName, flowName, cronExpression) {

  // 需要校验是否具有修改项目调度权限 1:允许, 2:不允许
  var requestURL = "/manager?ajax=checkUserSwitchScheduleFlowPermission&project=" + projectName;
  $.ajax({
    url: requestURL,
    type: "get",
    async: false,
    dataType: "json",
    success: function (data) {
      if (data["switchScheduleFlowFlag"] == 1) {
        console.log("click switch schedule button.");
        var currentActiveFlag = document.getElementById("schedules-tbody").rows[index].cells[8].innerHTML;
        console.log("currentActiveFlag=" + currentActiveFlag);
        var destActiveFlag = false;
        if (currentActiveFlag == "false") {
          destActiveFlag = true;
        }

        var scheduleActiveData = {
          scheduleId: scheduleId,
          ajax: "setScheduleActiveFlag",
          activeFlag: destActiveFlag
        };

        var scheduleURL = "/schedule"
        var successHandler = function (data) {
          if (data.error) {
            alert(data.error);
          } else {
            // 触发变更就行, 不是刷新所有页面
            scheduleListView.handlePageChange();
          }
        };
        $.post(scheduleURL, scheduleActiveData, successHandler, "json");
      } else if (data["switchScheduleFlowFlag"] == 2) {
          $('#user-operator-schedule-flow-permit-panel').modal();
          $('#title-user-operator-schedule-flow-permit').text(wtssI18n.view.scheduleActivePermission);
          $('#body-user-operator-schedule-flow-permit').html(wtssI18n.view.noScheSwitchConfigPermission);
      } else if(data["switchScheduleFlowFlag"] == 3){
          $('#user-operator-schedule-flow-permit-panel').modal();
          $('#title-user-operator-schedule-flow-permit').text(wtssI18n.view.scheduleActivePermission);
          $('#body-user-operator-schedule-flow-permit').html(data.error);
      }
    }
  });
}

$(function () {

  scheduleView = new azkaban.ScheduleView({
    el: $('#schedule-view')
  });


  deleteScheduleflowView = new azkaban.DeleteScheduleflowView({
    el: $('#delete-schedule-flow-modal')
  });

});

var scheduleView;
azkaban.ScheduleView = Backbone.View.extend({
  events: {
    "click .btn-danger": "handleDeleteProject"
  },

  initialize: function (settings) {
  },

  // 定时调度页面, 定时调度工作流列表, 对显示的调度任务点击删除调度
  handleDeleteProject: function (evt) {

    var info = evt.currentTarget.name;
    var infoArr = info.split('#');
    var projectName = infoArr[1]

    // 需要校验是否具有修改项目调度权限 1:允许, 2:不允许
    var requestURL = contextURL + "/manager?ajax=checkUserDeleteScheduleFlowPermission&project=" + projectName;
    $.ajax({
        url: requestURL,
        type: "get",
        async: false,
        dataType: "json",
        success: function(data){
            if(data["deleteScheduleFlowFlag"] == 1){

                console.log("click delete project");
                $('#delete-schedule-flow-modal').modal();
                var btnName = evt.currentTarget.name;
                var str = btnName.split('#');
                var scheduleId = str[0];
                // 分隔符由原来的减号(-)变为 # 号分隔
                var scheduleName = str[2];
                $('#delete-schedule-title').text(data["removeScheduleTitle"] + scheduleName);
                $('#schedule-id').val(scheduleId);

            }else if(data["deleteScheduleFlowFlag"] == 2){
                $('#user-operator-schedule-flow-permit-panel').modal();
                $('#title-user-operator-schedule-flow-permit').text(wtssI18n.common.deleteSchPermissions);
                $('#body-user-operator-schedule-flow-permit').html(wtssI18n.common.noPermissionsDelete);
            }
        }
    });
},

  render: function () {
  }
});

var deleteScheduleflowView;
azkaban.DeleteScheduleflowView = Backbone.View.extend({
  events: {
    "click #delete-btn": "handleDeleteProject"
  },

  initialize: function (settings) {
  },

  handleDeleteProject: function (evt) {
    var scheduleId = $('#schedule-id').val();
    removePageSchedule(scheduleId);
  },

  render: function () {
  }
});

// 删除页面中的定时调度
function removePageSchedule(scheduleId) {

   var scheduleURL = contextURL + "/schedule"
   var requestData = {
       "action": "removeSched",
       "scheduleId": scheduleId
   };
   var successHandler = function (data) {
       if (data.error) {
           $('#errorMsg').text(data.error);
       }
       else {
           window.location = scheduleURL;
       }
   };
   $.post(scheduleURL, requestData, successHandler, "json");

}

