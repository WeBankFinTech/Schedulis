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

var logAutoRefreshDialogView;
azkaban.LogAutoRefreshDialogView = Backbone.View.extend({
  events: {
    "click .closeLogAutoPanel": "hideLogAutoRefreshPanel",
    "click #auto-btn": "handleLogAutoRefresh"
  },

  initialize: function(settings) {
    $('#log-error-msg').hide();
  },

  render: function() {
  },

  //数据展示入口
  show: function(data) {
    $('#log-auto-refresh-panel').modal();
  },

  hideLogAutoRefreshPanel: function() {
    $('#log-auto-refresh-panel').modal("hide");
  },

  handleLogAutoRefresh: function(evt) {
    console.log("click auto refresh button." + " mode: " + this.model.get("usedLatestLogMode"));

    var selectFlag = $("#refresh-flowlog-time-select").val();
    var timeValue;

    if ("0" == selectFlag){
      timeValue = 20;
      autoSecRefreshTask(timeValue, this.model);
    }

    if ("1" == selectFlag){
      timeValue = 15;
      autoSecRefreshTask(timeValue, this.model);
    }

    if ("2" == selectFlag){
      timeValue = 10;
      autoSecRefreshTask(timeValue, this.model);
    }

    if ("3" == selectFlag){
      timeValue = 5;
      autoSecRefreshTask(timeValue, this.model);
    }

    if ("4" == selectFlag){
      alert(wtssI18n.view.refreshTime);
      return;
    }

    $('#log-auto-refresh-panel').modal("hide");
  },

});

$(function() {

  logAutoRefreshDialogView = new azkaban.LogAutoRefreshDialogView({
    el: $('#log-auto-refresh-panel'),
    model: jobLogModel
  });

});

var timeTask;

function autoSecRefreshTask(refreshNum, model){
  stopRefreshTask();
  //按秒执行定时任务
  var self = model;
  timeTask = setInterval(function(){
    if(self.get("usedLatestLogMode")){
      jobLogView.refreshLatestLog();
    } else {
      jobLogView.handleRefreshLog();
    }
  }, refreshNum * 1000);
}

// function autoMinRefreshTask(refreshNum){
//   stopRefreshTask();
//   //按分执行定时任务
//   timeTask = setInterval(function(){
//     jobLogView.handleRefreshLog();
//   }, refreshNum * 60 * 1000);
// }

function stopRefreshTask(){
  clearInterval(timeTask);
}