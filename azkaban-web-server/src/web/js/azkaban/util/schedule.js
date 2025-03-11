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
function removeSched (scheduleId) {
  deleteDialogView.show(wtssI18n.deletePro.deleteSchedule, wtssI18n.deletePro.whetherDeleteSchedule, wtssI18n.common.cancel, wtssI18n.view.delete, function () {
    // 需要校验是否具有删除工作流调度权限 1:允许, 2:不允许
    var requestURL = "/manager?ajax=checkDeleteScheduleInDescriptionFlagPermission";
    $.ajax({
        url: requestURL,
        type: "get",
        async: false,
        dataType: "json",
        success: function (data) {
        if (data["deleteDescScheduleFlag"] == 1) {
            var scheduleURL = "/schedule"
            var requestData = {
            "action": "removeSched",
            "scheduleId": scheduleId
            };
            var successHandler = function (data) {
            if (data.error) {
                // $('#errorMsg').text(data.error);
                messageBox.show(data.error,'danger');
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
  });
}

function removeSla (scheduleId) {
  var scheduleURL = "/schedule"
  var requestData = {
    "action": "removeSla",
    "scheduleId": scheduleId
  };
  var successHandler = function (data) {
    if (data.error) {
    //   $('#errorMsg').text(data.error)
    messageBox.show(data.error,'danger');
    }
    else {
      window.location = scheduleURL
    }
  };
  $.post(scheduleURL, requestData, successHandler, "json");
}

// 定时调度页面, 定时调度工作流列表, 对显示的调度任务点击调度配置
function editScheduleClick (scheduleId, projectName, flowName, cronExpression) {

  // 需要校验是否具有修改项目调度权限 1:允许, 2:不允许
  var requestURL = "/manager?ajax=checkUserUpdateScheduleFlowPermission&project=" + projectName;
  $.ajax({
    url: requestURL,
    type: "get",
    async: false,
    dataType: "json",
    success: function (data) {
      if (data["updateScheduleFlowFlag"] == 1) {
        console.log("click edit schedule button.");

        var executingData = {
          scheduleId: scheduleId,
          project: projectName,
          flow: flowName,
          scheduleFlowTitle: data["scheduleFlowTitle"]
        };

        flowScheduleDialogView.show(executingData);
        scheduleJobCronView.setFlowID(flowName, projectName);
        //初始化当前所选定时调度的错误重试参数值
        jobScheduleFailedRetryView.setFlowID(flowName, projectName);
        flowScheduleDialogView.loadScheduleRunningInfo(executingData);
        //初始化当前所选定时调度的错误跳过参数值
        scheduleJobSkipFailedView.setFlowID(flowName, projectName);
      } else if (data["updateScheduleFlowFlag"] == 2) {
        // $('#user-operator-schedule-flow-permit-panel').modal();
        // $('#title-user-operator-schedule-flow-permit').text(wtssI18n.view.scheduleConfigPermission);
        // $('#body-user-operator-schedule-flow-permit').html(wtssI18n.view.noScheConfigPermission);
        // $('#active-schedule-flow-modal').modal("hide");
        messageBox.show(wtssI18n.view.noScheConfigPermission, 'danger');
      }
    }
  });

}
// 信号调度tag, 信号调度工作流列表, 对显示的调度任务点击调度配置
function editEventScheduleClick (scheduleId, projectId, projectName, flowName, scheduleType) {
  var scheduleStartDat = "";
  var topic = "";
  var msgName = "";
  var saveKey = "";
  var token = "";
  var requestURLForFetchScheduleId = "/manager?ajax=fetchRunningEventScheduleId&project=" + projectName + "&flow=" + flowName;
  $.ajax({
    url: requestURLForFetchScheduleId,
    type: "get",
    async: false,
    dataType: "json",
    success: function (data) {
      if (data.error) {
        console.log(data.error.message);
      } else {
        topic = data.topic;
        msgName = data.msgName;
        saveKey = data.saveKey;
        token = data.token;
      }
    }
  });

  // 需要校验是否具有执行调度权限 1:允许, 2:不允许
  var requestURL = "/manager?ajax=checkUserScheduleFlowPermission&project=" + projectName;
  $.ajax({
    url: requestURL,
    type: "get",
    async: false,
    dataType: "json",
    success: function (data) {
      if (data["scheduleFlowFlag"] == 1) {
        console.log("temp event schedule flow");
        var executingData = {
          projectId: projectId,
          project: projectName,
          flow: flowName,
          scheduleId: scheduleId,
          scheduleFlowTitle: data["eventScheduleFlowTitle"],
          topic: topic,
          msgName: msgName,
          saveKey: saveKey,
          scheduleType: scheduleType,
          token: token
        };
        eventScheduleJobSkipFailedView.setFlowID(flowName, projectName);
        eventJobScheduleFailedRetryView.setFlowID(flowName, projectName);
        setTimeout(function () {
          flowEventScheduleDialogView.show(executingData, false);
          flowEventScheduleDialogView.loadScheduleRunningInfo(executingData);
        }, 100);
      } else if (data["scheduleFlowFlag"] == 2) {
        // $('#user-operator-schedule-flow-permit-panel').modal();
        // $('#title-user-operator-schedule-flow-permit').text(wtssI18n.view.scheduleConfigPermission);
        // $('#body-user-operator-schedule-flow-permit').html(wtssI18n.view.noScheConfigPermission);
        messageBox.show(wtssI18n.view.noScheConfigPermission, 'danger');
      }
    }
  });
}

// 循环调度页面，循环调度工作流列表，对显示的循环调度任务点击调度配置
function editCycleScheduleClick(currentExecId, projectName, projectId, flowName, cronExpression){
  // 需要校验是否具有执行工作流权限 1:允许, 2:不允许
  var requestURL = "/manager?ajax=checkUserExecuteFlowPermission&project=" + projectName;
  $.ajax({
      url: requestURL,
      type: "get",
      async: false,
      dataType: "json",
      success: function(data) {
          if (data["executeFlowFlag"] == 1) {

              var executingData = {
                project: projectName,
                projectId: projectId,
                ajax: "executeFlow",
                flow: flowName,
                executeFlowTitle: data["executeFlowTitle"],
                isEditCycleScheduleClick: true,
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
}
// 调度管理打开、关闭、批量操作确认框g
function oprScheduleConfirmPrompt (title, bnText,btnType, bodyHtml) {
    $('#active-schedule-flow-modal').modal();
    $('#active-schedule-title').text(title);
    $('#active-btn').text(bnText).attr('type', btnType);
    $("#active-schedule-flow-modal .modal-body p").html(bodyHtml);
}

// 调度管理报错提示
function oprScheduleErrorPrompt (title, error) {
    // $('#user-operator-schedule-flow-permit-panel').modal();
    // $('#title-user-operator-schedule-flow-permit').text(title);
    // $('#body-user-operator-schedule-flow-permit').html(bodyHtml);
    $('#active-schedule-flow-modal').modal("hide");
    messageBox.show(error, 'danger');
}

    // 声明存储批量数据
    scheduleCheckedProjectList = [];
    scheduleCheckedFlowList = [];
    scheduleCheckedIdtList = [];
    eventScheduleCheckedProjectList = [];
    eventScheduleCheckedFlowList = [];
    eventScheduleCheckedIdtList = []
    // 获取选中数据的项目名
function getSelectProjectList (modelKey) {
    var modelKey = modelKey === 'event' ? 'eventScheduleCheckedProjectList' : 'scheduleCheckedProjectList';
    var checkedProjectList = window[modelKey];
    if ( checkedProjectList.length === 0 ) {
        messageBox.show('请先选择操作数据！', 'warning');
        return checkedProjectList;
    }
    // 项目名去重
    var projectList = [];
    for (let i = 0; i < checkedProjectList.length; i++) {
        if (projectList.indexOf(checkedProjectList[i]) === -1) {
            projectList.push(checkedProjectList[i]);
        }
    }
    return projectList;
}
// 校验
function validScheduleProjectPermission( oprType, activeFlag) {
    var modelKey = location.hash === '#event-schedule' ? 'event' : 'schedule';
    var selectProjectList = getSelectProjectList(modelKey);
    var params = {
        projects: selectProjectList.toString(),
        ajax: oprType === 'batch-delete' ?  'batchCheckUserDeleteScheduleFlowPermission' : 'batchCheckUserSwitchScheduleFlowPermission'
    }
    var successHandler = function(data) {
        if (data.switchScheduleFlowFlag === 1 || data.deleteScheduleFlowFlag === 1) {
            oprType === 'batch-delete' ? ajaxBatchDeleteData(modelKey) : ajaxBatchSwitchStatus(modelKey,activeFlag);
        } else {
            var title = wtssI18n.common.batch;
            title+= oprType === 'batch-delete' ? wtssI18n.common.delete : (activeFlag ? wtssI18n.common.enable :  wtssI18n.common.disable);
            oprScheduleErrorPrompt(title,data.error || `你没有权限执行或者调度这个项目: ${selectProjectList.toString()}`);

        }
    };
    $.get('/manager', params, successHandler, "json");
}
// 批量打开关闭
function ajaxBatchSwitchStatus (modelKey,activeFlag) {
    var url, action,storagProjecteName, storagIdName
    if (modelKey === 'event') { // 信号调度
        url = '/eventschedule';
        action = 'setEventScheduleActiveFlag';
        storagProjecteName = 'eventScheduleCheckedProjectList';
        storagIdName = 'eventScheduleCheckedIdtList'
    } else { // 调度管理
        url = '/schedule';
        action = 'setScheduleActiveFlag';
        storagProjecteName = 'scheduleCheckedProjectList';
        storagIdName = 'scheduleCheckedIdtList';
    }
    var successHandler = function(data) {
        if (data.schedule && data.schedule.length > 0) {
            modelKey === 'event' ? eventScheduleView.handlePageChange() : scheduleListView.handlePageChange();
            messageBox.show('设置成功');
            window[storagProjecteName] = [];
            window[storagIdName] = [];
            $('#active-schedule-flow-modal').modal("hide");
        }
    };
    $.post(url, {
        scheduleId: window[storagIdName].toString(),
        ajax: action,
        activeFlag: activeFlag
    }, successHandler, "json");
}
// 批量下线
function ajaxBatchDeleteData (modelKey) {
    var url, action,storagProjecteName, storagIdName
    if (modelKey === 'event') { // 信号调度
        url = '/eventschedule';
        action = 'removeEventSchedule';
        storagProjecteName = 'eventScheduleCheckedProjectList';
        storagIdName = 'eventScheduleCheckedIdtList'
    } else { // 调度管理
        url = '/schedule';
        action = 'removeSched';
        storagProjecteName = 'scheduleCheckedProjectList';
        storagIdName = 'scheduleCheckedIdtList';
    }
    var successHandler = function(data) {
        if (data.status === 'success') {
            modelKey === 'event' ? eventScheduleView.handlePageChange() : scheduleListView.handlePageChange();
            messageBox.show(data.message);
            window[storagProjecteName] = [];
            window[storagIdName] = [];
            $('#active-schedule-flow-modal').modal("hide");
        } else {
            $('#active-schedule-flow-modal').modal("hide");
            messageBox.show(data.message,'danger');
        }
    };
    $.post(url, {
        scheduleId: window[storagIdName].toString(),
        action: action
    }, successHandler, "json");
}
function batchOprOpenPrompt(modelType, oprType) {// modelType schedule/event oprType 开启、关闭、下线
    var selectProjectList = getSelectProjectList(modelType);
    if (selectProjectList.length === 0) {
        return;
    }
    var label = oprType === 'enable' ? wtssI18n.common.enable : (oprType === 'disable' ? wtssI18n.common.disable :  wtssI18n.common.delete)
    var html = window.langType === 'zh_CN' ? '是否' + label+ ': ' + selectProjectList.toString() + ' 调度' : 'Determine whether to' +  label +  selectProjectList.toString() + 'schedules';
    oprScheduleConfirmPrompt(wtssI18n.common.batch + label + wtssI18n.common.schedule, label + wtssI18n.common.schedule, 'batch-' + oprType, html);
}

// 定时调度页面, 定时调度工作流列表, 对显示的调度任务点击调度开启关闭 type schedule 定时调度 eventSchedule 信号调度 cycleSchedule 循环调度
function switchScheduleClick (index, scheduleId, projectName, flowName, type, currentActiveFlag) {

  // 需要校验是否具有修改项目调度权限 1:允许, 2:不允许
  var requestURL = "/manager?ajax=checkUserSwitchScheduleFlowPermission&project=" + projectName;
  $.ajax({
    url: requestURL,
    type: "get",
    async: false,
    dataType: "json",
    success: function (data) {
      if (data["switchScheduleFlowFlag"] == 1) {
        console.log("click active project");
        $('#active-schedule-flow-modal').modal();
        // var currentActiveFlag = type === 'schedule' ? document.getElementById("schedules-tbody").rows[index].cells[9].innerHTML :
        //   document.getElementById("event-schedules-tbody").rows[index].cells[9].innerHTML;
        var props,title, bnText, bodyHtml;
        if (type === 'cycleSchedule'){
          props = scheduleId + '#false';
          title = wtssI18n.view.killCycleExecution + ': ' + flowName;
          bnText = wtssI18n.view.killCycleExecution;
          bodyHtml = "<strong>" + wtssI18n.view.warning + " :</strong> " + wtssI18n.view.cycleWarnContent;
          oprScheduleConfirmPrompt(title, bnText,type, bodyHtml);
        } else {
          if (currentActiveFlag == "false") {
            props = scheduleId + '#true';
            title = wtssI18n.view.activeSchedule + ': ' + flowName;
            bnText = wtssI18n.view.activeSchedule;
            bodyHtml = "<strong>" + wtssI18n.view.warning + " :</strong> " + wtssI18n.view.activeWarnContent;
            oprScheduleConfirmPrompt(title, bnText,type, bodyHtml);
          } else {
            props = scheduleId + '#false';
            title = wtssI18n.view.inactiveSchedule + ': ' + flowName;
            bnText = wtssI18n.view.inactiveSchedule;
            bodyHtml = "<strong>" + wtssI18n.view.warning + " :</strong> " + wtssI18n.view.inactiveWarnContent;
            oprScheduleConfirmPrompt(title, bnText,type, bodyHtml);
          }
        }
        $('#active-schedule-props').val(props);
      } else if (data["switchScheduleFlowFlag"] == 2) {
        oprScheduleErrorPrompt(wtssI18n.view.scheduleActivePermission, wtssI18n.view.noScheSwitchConfigPermission);
      } else if (data["switchScheduleFlowFlag"] == 3) {
        oprScheduleErrorPrompt(wtssI18n.view.scheduleActivePermission, data.error);
      }
    }
  });

}
// 下载调度所属项目
function batchDownloadProject (modelType) {
     var keyName= '',url = '', ajaxKey = '', fileName = '';
     if ( modelType === 'schedule') {
        keyName = 'scheduleCheckedIdtList' ;
        url = '/schedule';
        ajaxKey = 'downloadProjectBySchedule';
        fileName = 'scheduleproject.zip'
     } else {
        keyName = 'eventScheduleCheckedIdtList' ;
        url = '/eventschedule';
        ajaxKey = 'downloadProjectByEventSchedule';
        fileName = 'eventScheduleProject.zip'
     }
     var selectProjectList = window[keyName];
     if (selectProjectList.length === 0) {
        messageBox.show('请先选择操作数据！', 'warning');
        return;
    }
    var schduleId = selectProjectList.toString();
    var xhr = new XMLHttpRequest();
    xhr.open("get", url + '?scheduleIds=' + schduleId + '&ajax=' + ajaxKey, true);
    xhr.responseType = "blob";
    xhr.send()
    xhr.onload = function(data) {
        if (this.status == 200) {
            var blob = this.response;
                if(blob.type == "application/zip"){
                    if(window.navigator.msSaveOrOpenBlob){            // IE浏览器下
                        navigator.msSaveBlob(blob, fileName);
                    } else {
                        var  link = document.createElement("a");
                        link.href = window.URL.createObjectURL(blob);
                        link.download = fileName;
                        link.click();
                        window.URL.revokeObjectURL(link.href);
                    }
                }
                if(blob.type == "application/json"){
                    var reader=new FileReader();
                    reader.readAsText(blob, 'utf-8');;
                    reader.onload = (event) => {;
                        const result = JSON.parse(event.target.result);
                        result.errorMsg && messageBox.show( result.errorMsg, 'danger')
                        // 此时可以根据后端相应数据进行提示
                    }
                }
            } else {
        }
    }
}
$(function () {

  scheduleView = new azkaban.ScheduleView({
    el: $('#time-schedule-view')
  });
  deleteEventScheduleView = new azkaban.DeleteEventScheduleView({
    el: $('#event-schedule-view')
  });
  deleteCycleScheduleView = new azkaban.DeleteCycleScheduleView({
    el: $('#cycle-schedule-view')
  });

  deleteScheduleflowView = new azkaban.DeleteScheduleflowView({
    el: $('#delete-schedule-flow-modal')
  });

  activeScheduleflowView = new azkaban.ActiveScheduleflowView({
    el: $('#active-schedule-flow-modal')
  });

  editImsView = new azkaban.EditImsView({
    el: $('#edit-ims-panel')
  });

});

var scheduleView;
var deleteEventScheduleView;
var deleteCycleScheduleView
function getScheduleViewDeleteFun () {
    return {
        initialize: function (settings) {
        },

        // 定时调度页面, 定时调度工作流列表, 对显示的调度任务点击删除调度
        handleDeleteProject: function (evt) {

          var info = evt.currentTarget.name;
          var infoArr = info.split('#');
          var projectName = infoArr[1]
          var that = this
          // 需要校验是否具有修改项目调度权限 1:允许, 2:不允许
          var requestURL = "/manager?ajax=checkUserDeleteScheduleFlowPermission&project=" + projectName;
          $.ajax({
            url: requestURL,
            type: "get",
            async: false,
            dataType: "json",
            success: function (data) {
              if (data["deleteScheduleFlowFlag"] == 1) {

                console.log("click delete project");
                $('#delete-schedule-flow-modal').modal();
                var btnName = evt.currentTarget.name;
                var str = btnName.split('#');
                var scheduleId = str[0];
                var currentExecId = str[0]
                // 分隔符由原来的减号(-)变为 # 号分隔
                var scheduleName = str[2];
                var flowName = str[2]
                var projectName = str[1]
                var projectId = str[3]
                var title = wtssI18n.view.deleteScheduled + ':'
                $('#delete-schedule-title').text(title + scheduleName);
                $('#schedule-id').val(scheduleId);
                $('#project-id').val(projectId);
                $('#flow-id').val(flowName);
                $('#exec-id').val(currentExecId);
                var scheduleType = that.el.id === 'event-schedule-view' ?  'eventSchedule' : that.el.id === 'cycle-schedule-view' ? 'cycleSchedule' : 'schedule'
                $('#delete-btn').attr('scheduleType', scheduleType)
              } else if (data["deleteScheduleFlowFlag"] == 2) {
              //   $('#user-operator-schedule-flow-permit-panel').modal();
              //   $('#title-user-operator-schedule-flow-permit').text(wtssI18n.common.deleteSchPermissions);
              //   $('#body-user-operator-schedule-flow-permit').html(wtssI18n.common.noPermissionsDelete);
                  $('#delete-schedule-flow-modal').modal("hide");
                  messageBox.show(wtssI18n.common.noPermissionsDelete, 'danger');
              }
            }
          });
        },

        render: function () {
        }
    }
}
azkaban.ScheduleView = Backbone.View.extend({
  events: {
    "click #schedules-tbody .btn-danger": "handleDeleteProject"
  },
  ...getScheduleViewDeleteFun(),
});

azkaban.DeleteEventScheduleView = Backbone.View.extend({
  events: {
    "click #event-schedules-tbody .btn-danger": "handleDeleteProject"
  },
  ...getScheduleViewDeleteFun(),
});

azkaban.DeleteCycleScheduleView = Backbone.View.extend({
  events: {
    "click #cycle-schedules-tbody .btn-danger": "handleDeleteProject"
  },
  ...getScheduleViewDeleteFun(),
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
    var scheduletype = evt.target.getAttribute('scheduletype')
    if (scheduletype === 'cycleSchedule') {
      var projectId = $('#project-id').val();
      var flowId = $('#flow-id').val();
      removeCycleSchedule(projectId, flowId)
    } else{
      removePageSchedule(scheduleId, scheduletype);
    }
  },

  render: function () {
  }
});

// 删除页面中的定时调度
function removePageSchedule (scheduleId, scheduleType) {

  var scheduleURL = scheduleType === 'schedule' ? "/schedule" : "/eventschedule"
  var requestData = {
    "action": scheduleType === 'schedule' ? "removeSched" : "removeEventSchedule",
    "scheduleId": scheduleId
  };
  var successHandler = function (data) {
    if (data.status === 'error') {
      //   $('#errorMsg').text(data.error);
      $('#delete-schedule-flow-modal').modal('hide');
      messageBox.show(data.message,'danger');
    } else if(data.status === 'success') {
      if (scheduleType === 'schedule') {
        scheduleListView.handlePageChange();
      } else {
        eventScheduleView.handlePageChange()
      }
      messageBox.show(data.message);
      $('#delete-schedule-flow-modal').modal('hide');
    }
  };
  $.post(scheduleURL, requestData, successHandler, "json");

}

// 删除循环调度
function removeCycleSchedule(projectId, flowId){
  console.log('removeCycleSchedule', projectId, flowId);
  var url = '/cycle'
  var ajax = 'deleteCycleFlows'
  var requestData = {
    projectId: projectId,
    flowId: flowId,
    ajax: ajax
  }
  var successHandler = function (data) {
    if (data.status === 'error' || data.error) {
      //   $('#errorMsg').text(data.error);
      $('#delete-schedule-flow-modal').modal('hide');
      messageBox.show(data.message || data.error,'danger');
    } else {
      cycleScheduleView.handlePageChange()
      messageBox.show(wtssI18n.common.optSuccessfully);
      $('#delete-schedule-flow-modal').modal('hide');
    }
  };
  $.get(url, requestData, successHandler, 'json');
}

var activeScheduleflowView;
azkaban.ActiveScheduleflowView = Backbone.View.extend({
  events: {
    "click #active-btn": "handleActiveProject"
  },

  initialize: function (settings) {
  },

  handleActiveProject: function (evt) {
    var props = $('#active-schedule-props').val();
    var oprType = evt.target.getAttribute('type')
    if (['batch-enable', 'batch-disable' ,'batch-delete'].indexOf(oprType) > -1) {
        validScheduleProjectPermission(oprType, oprType === 'batch-enable');
    } else {
        activePageSchedule(props, oprType);
    }

  },

  render: function () {
  }
});

// 关闭、开启定时调度
function activePageSchedule (props, schedulisType) {

  var ajaxs = {
    'schedule': 'setScheduleActiveFlag',
    'eventSchedule': 'setEventScheduleActiveFlag',
    'cycleSchedule': 'stopCycleFlow'
  }

  var urls = {
    'schedule': '/schedule',
    'eventSchedule': '/eventschedule',
    'cycleSchedule': '/executor'
  }

  var str = props.split('#');
  var scheduleId = str[0];
  var destActiveFlag = str[1];

  var scheduleActiveData = {
    scheduleId: scheduleId,
    ajax: ajaxs[schedulisType],
    activeFlag: destActiveFlag
  };

  var scheduleURL = urls[schedulisType];
  var successHandler = function (data) {
    if (data.error) {
      alert(data.error);
    } else {
      $('#active-schedule-flow-modal').modal("hide");
      // 触发变更就行, 不是刷新所有页面
      if (schedulisType === 'schedule') {
        scheduleListView.handlePageChange();
      } else if(schedulisType === 'eventSchedule') {
        eventScheduleView.handlePageChange()
      } else{
        cycleScheduleView.handlePageChange()
      }

    }
  };
  if(schedulisType === 'cycleSchedule'){
    $.get(scheduleURL, {
      id: scheduleId,
      ajax: ajaxs[schedulisType],
      activeFlag: destActiveFlag
    }, successHandler, 'json');
  } else {
    $.post(scheduleURL, scheduleActiveData, successHandler, "json");
  }
}

// 定时调度页面, 定时调度工作流列表, 对显示的调度任务点击调度开启关闭
function editImsClick (index, scheduleId, projectName, scheduleType, imsSwitch) {

  // 需要校验是否具有修改项目调度权限 1:允许, 2:不允许
  var requestURL = "/manager?ajax=checkUserScheduleFlowPermission&project=" + projectName;
  $.ajax({
    url: requestURL,
    type: "get",
    async: false,
    dataType: "json",
    success: function (data) {
      if (data["scheduleFlowFlag"] == 1) {
//        $('#edit-ims-panel').modal();
//        $('#schedule-id').val(scheduleId);
//        $('#ims-set-btn').attr('scheduleType', scheduleType);
//        $('#ims-switch').attr('imsSwitch', imsSwitch);
//        editImsView.render();
//        editImsView.loadImsProperties();
        var requestURL = scheduleType === "schedule" ? "/schedule" : "/eventschedule";
        var requestData = {
          scheduleId: scheduleId,
          "ajax": "setImsProperties",
          "imsSwitch": imsSwitch
        };
        var successHandler = function (data) {
          if (data.errorMsg) {
            // alert(data.errorMsg);
            messageBox.show(data.errorMsg, 'danger');
            return false;
          } else {
            alert(imsSwitch=='1'?'inactive success':'active success');
            if (scheduleType === 'schedule') {
              scheduleListView.handlePageChange();
            } else {
              eventScheduleView.handlePageChange()
            }
          }
        };
        $.post(requestURL, requestData, successHandler, "json");
      } else if (data["scheduleFlowFlag"] == 2) {
        // $('#user-operator-schedule-flow-permit-panel').modal();
        // $('#title-user-operator-schedule-flow-permit').text(wtssI18n.view.scheduleEditImsPermission);
        // $('#body-user-operator-schedule-flow-permit').html(wtssI18n.view.noScheEditImsPermission);

        messageBox.show(wtssI18n.view.noScheEditImsPermission, 'danger');
      }
    }
  });

}

var editImsView;
azkaban.EditImsView = Backbone.View.extend({
  events: {
    "click #ims-set-btn": "handleSetImsProperties"
  },

  initialize: function (settings) {
    $("#edit-ims-error-msg").hide();
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

  handleSetImsProperties: function (evt) {
//    var reportIMS = $("#report-ims").val();
//    var planStartTime = $("#plan-start-time").val();
//    var planFinishTime = $("#plan-finish-time").val();
//    var lastStartTime = $("#last-start-time").val();
//    var lastFinishTime = $("#last-finish-time").val();
//    var alertLevel = $("#alert-level").val();
//    var dcnNumber = $("#dcn-number").val();
//    var subSystemId = $("#sub-system-select").val();
//    var batchGroup = $("#batch-group-select").val();
//    var busDomain = $("#bus-domain-select").val();
//    var busPath = $("#bus-path-select").val();
//    var imsUpdater = $("#ims-updater-select").val() ? $("#ims-updater-select").val().join(';') : "";
    var scheduleType = $('#ims-set-btn').attr('scheduleType')
    var requestURL = scheduleType === "schedule" ? "/schedule" : "/eventschedule";
    var scheduleId = $('#schedule-id').val();
    var requestData = {
      scheduleId: scheduleId,
      "ajax": "setImsProperties",
      "imsSwitch":imsSwitch
//      "reportIMS": reportIMS,
//      "planStartTime": planStartTime,
//      "planFinishTime": planFinishTime,
//      "lastStartTime": lastStartTime,
//      "lastFinishTime": lastFinishTime,
//      "alertLevel": alertLevel,
//      "dcnNumber": dcnNumber,
//      "subSystemId": subSystemId,
//      "batchGroup": batchGroup,
//      "busDomain": busDomain,
//      "busPath": busPath,
//      "imsUpdater": imsUpdater
    };
    var successHandler = function (data) {
      if (data.errorMsg) {
        // $("#edit-ims-error-msg").show();
        // $("#edit-ims-error-msg").text(data.errorMsg);
        messageBox.show(data.errorMsg,'danger');
        return false;
      } else {
        $('#edit-ims-panel').modal("hide");
      }
    //   $("#edit-ims-error-msg").hide();
    };
    $.post(requestURL, requestData, successHandler, "json");
  },

  loadImsProperties: function () {
    var scheduleType = $('#ims-set-btn').attr('scheduleType')
    var requestURL = scheduleType === "schedule" ? "/schedule" : "/eventschedule";
    var scheduleId = $('#schedule-id').val();

    var requestData = {
      "ajax": "getImsProperties",
      scheduleId: scheduleId,
      isLoaded: isCmdbLoaded
    };
    var successHandler = function (data) {
      if (data.errorMsg) {
        // $("#edit-ims-error-msg").show();
        // $("#edit-ims-error-msg").text(data.errorMsg);
        messageBox.show(data.errorMsg,'danger');
        return false;
      } else {
        fetchCmdbData("sub-system-select", 'wb_subsystem', 'subsystem_id', 'subsystem_name', data.imsProperties ? data.imsProperties.subSystemId : "");
        fetchCmdbData("bus-path-select", 'wb_batch_critical_path', 'id', 'name', data.imsProperties ? data.imsProperties.busPath : "");
        fetchCmdbData("batch-group-select", 'wb_batch_group', 'group_id', 'group_name', data.imsProperties ? data.imsProperties.batchGroup : "");
        fetchCmdbData("bus-domain-select", 'subsystem_app_instance', 'appdomain_cnname', 'appdomain_cnname', data.imsProperties ? data.imsProperties.busDomain : "");

        if (data.imsUpdaterList) {
          $("#ims-updater-select").find("option:selected").text("");
          $("#ims-updater-select").empty();
          var optionHtml = "";
          for (var i = 0; i < data.imsUpdaterList.length; i++) {
            optionHtml += "<option value='" + data.imsUpdaterList[i].username + "'>" + data.imsUpdaterList[i].fullName + "</option>"
          }
          optionHtml = filterXSS(optionHtml, { 'whiteList': { 'option': ['value'] } })
          $('#ims-updater-select').append(optionHtml);
          $('#ims-updater-select').selectpicker('refresh');
          $('#ims-updater-select').selectpicker('render');
        }

        isCmdbLoaded = true;

        if (data.imsProperties) {
          $("#report-ims").val(data.imsProperties.reportIMS);
          $("#plan-start-time").val(data.imsProperties.planStartTime);
          $("#plan-finish-time").val(data.imsProperties.planFinishTime);
          $("#last-start-time").val(data.imsProperties.lastStartTime);
          $("#last-finish-time").val(data.imsProperties.lastFinishTime);
          $("#alert-level").val(data.imsProperties.alertLevel);
          $("#dcn-number").val(data.imsProperties.dcnNumber);
          $("#ims-updater-select").val(data.imsProperties.imsUpdater ? data.imsProperties.imsUpdater.split(';') : null);
        } else {
          $("#report-ims").val("");
          $("#plan-start-time").val("");
          $("#plan-finish-time").val("");
          $("#last-start-time").val("");
          $("#last-finish-time").val("");
          $("#alert-level").val("");
          $("#dcn-number").val("");
          $("#ims-updater-select").val(null);
        }
        $('#alert-level').selectpicker('refresh');
        $('#ims-updater-select').selectpicker('refresh');
      }
    };
    $.get(requestURL, requestData, successHandler, "json");

  },

  getCmdbData: function (selectCtrl, type, id, name, value) {
    if (isCmdbLoaded) {
      selectCtrl.val(value);
      selectCtrl.selectpicker('refresh');
      return;
    }

    var requestURL = "/manager";

    var requestData = {
      "ajax": "getCmdbData",
      "type": type,
      "id": id,
      "name": name,
      start: 0,
      size: 200000
    };
    var successHandler = function (data) {
      if (data.errorMsg) {
        // $("#merge-business-error-msg").show();
        // $("#merge-business-error-msg").text(data.errorMsg);
        messageBox.show(data.errorMsg,'danger');
        return false;
      } else {
        selectCtrl.find("option:selected").text("");
        selectCtrl.empty();
        if (data.dataList) {
          var optionHtml = "";
          for (var i = 0; i < data.dataList.length; i++) {
            optionHtml += "<option value='" + data.dataList[i][id] + "'>" + data.dataList[i][name] + "</option>"
          }
          optionHtml = filterXSS(optionHtml, { 'whiteList': { 'option': ['value'] } })
          selectCtrl.append(optionHtml);
        }
        selectCtrl.append("<option value='other'>" + wtssI18n.common.other + "</option>");

        //要以编程方式更新JavaScript的选择，首先操作选择，然后使用refresh方法更新UI以匹配新状态。 在删除或添加选项时，或通过JavaScript禁用/启用选择时，这是必需的。
        selectCtrl.selectpicker('refresh');
        //render方法强制重新渲染引导程序 - 选择ui,如果当您编程时更改任何相关值而影响元素布局，这将非常有用。
        selectCtrl.selectpicker('render');
        //绑定数据
        selectCtrl.val(value);
        selectCtrl.selectpicker('refresh');
      }
    };
    $.get(requestURL, requestData, successHandler, "json");
  },

  render: function () {
    $("#edit-ims-error-msg").hide();
  },
});


