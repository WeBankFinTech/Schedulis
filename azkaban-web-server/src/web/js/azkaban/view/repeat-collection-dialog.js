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

var repeatCollectionDialogView;
azkaban.RepeatCollectionDialogView = Backbone.View.extend({
  events: {
    "click .closeExecPanel": "hideRepeatOptionPanel",
    "click #filter-btn": "handleExecuteFlow"
  },

  initialize: function(settings) {
    var date = new Date();
    $('#datetimebegin').datetimepicker({
      format: 'YYYY/MM/DD HH:mm',
      maxDate: new Date()
    });
    $('#datetimeend').datetimepicker({
      format: 'YYYY/MM/DD HH:mm',
      maxDate: new Date()
    });
    $('#datetimebegin').on('change.dp', function(e) {
      $('#datetimeend').data('DateTimePicker').setStartDate(e.date);
    });
    $('#datetimeend').on('change.dp', function(e) {
      $('#datetimebegin').data('DateTimePicker').setEndDate(e.date);
    });
    $('#repeat-collection-error-msg').hide();
  },

  render: function() {
  },

  getExecutionOptionData: function() {

    var projectName = $('#projcontain').val();
    var flowId      = $('#flowcontain').val();
    var beginTime   = $('#datetimebegin').val();
    var endTime     = $('#datetimeend').val();
    var monthNum   = $('#repeat-month').val();
    var dayNum     = $('#repeat-day').val();
    var hourNum    = $('#repeat-hour').val();
    var minNum     = $('#repeat-min').val();
    var state      = true;

    if(beginTime == ''){
      alert(wtssI18n.view.startTimeReq);
      state = false;
      return;
    }
    if(endTime == ''){
      alert(wtssI18n.view.endTimeReq);
      state = false;
      return;
    }

    if(beginTime > endTime){
      alert(wtssI18n.view.timeFormat);
      state = false;
      return;
    }

    if(monthNum == 0 && dayNum == 0){
      alert(wtssI18n.view.executionIntervaPro);
      state = false;
      return;
    }

    if(monthNum == "" || dayNum == ""){
      alert(wtssI18n.view.executionIntervalFormat);
      state = false;
      return;
    }

    var start = new Date(Date.parse(beginTime));
    var end = new Date(Date.parse(endTime));

    start.setMonth(start.getMonth() + parseInt(monthNum));
    start.setDate(start.getDate() + parseInt(dayNum));
    start.setHours(start.getHours() + parseInt(hourNum));
    start.setMinutes(start.getMinutes() + parseInt(minNum));


    if(start > end){
      alert(wtssI18n.view.timeIntervalFormat);
      state = false;
      return;
    }


    var executingData = {
      projectId: projectId,
      project: projectName,
      flow: this.flowId,
      job: this.jobId,
      begin: beginTime,
      end: endTime,
      month: monthNum,
      day: dayNum,
      hour: hourNum,
      min: minNum,
      state: state
    };


    return executingData;
  },
  //数据展示入口
  show: function(data) {
    var projectName = data.project;
    var flowId = data.flow;
    var jobId = data.job;

    // ExecId is optional
    var execId = data.execid;
    var exgraph = data.exgraph;

    this.projectName = projectName;
    this.flowId = flowId;
    this.jobId = jobId;


    this.showRepeatOptionPanel(data);

  },

  showRepeatOptionPanel: function(data) {
    $('#repeat-collection-panel').modal();
    $("#projcontain").val(data.project);
    $("#flowcontain").val(data.flow);
//    $("#jobcontain").val(data.job);
  },

  hideRepeatOptionPanel: function() {
    $('#repeat-collection-panel').modal("hide");
  },

  handleExecuteFlow: function(evt) {
    console.log("click schedule button.");
    var executeURL = contextURL + "/executor";
    var executingData = this.getExecutionOptionData();
    if(executingData){
      //this.repeatFlow(executingData);
      this.checkRecoverParam(executingData, this.repeatFlow(executingData));
    }
  },

  repeatFlow: function(executingData) {
    executeURL = contextURL + "/executor?ajax=repeatCollection";
    repeatCollectionDialogView.hideRepeatOptionPanel();

    $.ajax({
      type: "GET",
      contentType: "application/json",
      url: executeURL,
      data: executingData,
      dataType: 'json',
      //success: successHandler,
      error: function (XMLHttpRequest, textStatus, errorThrown) {
        //alert('请求后台异常！' + errorThrown);
      }
    });
  },

  checkRecoverParam: function(executingData, repeatFun) {
    executeURL = contextURL + "/executor?ajax=recoverParamVerify";
    repeatCollectionDialogView.hideRepeatOptionPanel();

    var successHandler = function(data) {
      if (data.error) {
        messageDialogView.show(wtssI18n.view.historyRerunsFail, data.error);
        return false;
      } else {
        messageDialogView.show(wtssI18n.view.historicalRerun, wtssI18n.view.rerunSubmitSuccess,
            function() {
              window.location.href = contextURL + "/recover";
              repeatFun(executingData);
            }
        );

      }
    };

    $.ajax({
      type: "GET",
      contentType: "application/json",
      url: executeURL,
      data: executingData,
      dataType: 'json',
      success: successHandler,
      error: function (XMLHttpRequest, textStatus, errorThrown) {
        //alert('请求后台异常！' + errorThrown);
      }
    });

  }

});

$(function() {

  repeatCollectionDialogView = new azkaban.RepeatCollectionDialogView({
    el: $('#repeat-collection-panel')
  });

});


function repeatFlow(executingData) {
  executeURL = contextURL + "/executor?ajax=repeatCollection";
  repeatCollectionDialogView.hideRepeatOptionPanel();

  $.ajax({
    type: "GET",
    contentType: "application/json",
    url: executeURL,
    data: executingData,
    dataType: 'json',
    //success: successHandler,
    error: function (XMLHttpRequest, textStatus, errorThrown) {
      //alert('请求后台异常！' + errorThrown);
    }
  });
}

function checkRecoverParam(executingData, repeatFun) {
  executeURL = contextURL + "/executor?ajax=recoverParamVerify";
  repeatCollectionDialogView.hideRepeatOptionPanel();

  var successHandler = function(data) {
    if (data.error) {
      messageDialogView.show(wtssI18n.view.historyRerunsFail, data.error);
      return false;
    } else {
      messageDialogView.show(wtssI18n.view.historicalRerun, wtssI18n.view.rerunSubmitSuccess,
          function() {
            window.location.href = contextURL + "/recover";
            repeatFun(executingData);
          }
      );

    }
  };

  $.ajax({
    type: "GET",
    contentType: "application/json",
    url: executeURL,
    data: executingData,
    dataType: 'json',
    success: successHandler,
    error: function (XMLHttpRequest, textStatus, errorThrown) {
      //alert('请求后台异常！' + errorThrown);
    }
  });

}

