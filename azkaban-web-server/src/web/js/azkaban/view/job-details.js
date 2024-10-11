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

var jobLogView;
azkaban.JobLogView = Backbone.View.extend({
  events: {
    "click #updateLogBtn" : "handleRefreshLog",
    "click #errrorLogBtn" : "handleErrorLog",
    "click #infoLogBtn" : "handleInfoLog",
    "click #jobLogDownloadBtn" : "handlejobLogDownload",
    "click #autoRefreshLogBtn" : "handleAutoRefreshLog",
    "click #manuallyLogBtn" : "handleManuallyRefreshLog",
    "click #latestLogBtn" : "handleLatestLogRefreshLog",
    "click #yarnLogBtn" : "handleYarnLog",
  },

  initialize: function () {
    this.model.bind("change:logView", this.render, this);
    // 记录是否点击过最新日志按钮，第一次点击即可获取最后100000行的offset，以这个offset开始往后去日志
    this.model.set({"latestLogBtnFirstClick": false});
    // 初始化latestLog offset
    this.model.set({"latestLogOffset": 0});
    // 标记当前使用的是最新日志 还是 原来的手动刷日志
    this.model.set({"usedLatestLogMode": false});
    // 初始化最新日志数据记录
    this.model.set("latestLogData", "");
  },

  // refresh: function() {
  //   this.model.refresh();
  // },

  handleRefreshLog: function(){

    var data = {"logType":"refresh"};

    this.model.refresh(data.logType);
  },

  handleErrorLog: function(){

    var data = {"logType":"error"};

    stopRefreshTask();

    this.model.error_info(data.logType);
  },

  handleInfoLog: function(){

    var data = {"logType":"info"};

    stopRefreshTask();

    this.model.error_info(data.logType);
  },

  render: function (data) {
    var re = /(https?:\/\/(([-\w\.]+)+(:\d+)?(\/([\w/_\.]*(\?\S+)?)?)?))/g;
    var log = data;
    log = log.replace(re, "<a href=\"$1\" title=\"\">$1</a>");
    $("#logSection").html(log);
  },

  handlejobLogDownload: function(){

    //本地窗口打开日志下载
    window.location.href = contextURL + "/executor?execid=" + execId + "&job=" + jobId + "&downloadLog=true";

  },

  handleAutoRefreshLog: function(){

    logAutoRefreshDialogView.show();

  },

  handleManuallyRefreshLog: function(){
    //切换到从头开始刷log模式
    console.log("from the beginning mode");
    this.model.set("usedLatestLogMode", false);
    this.model.trigger("change:logView", this.model.get("logData"));
    var data = {"logType":"refresh"};

    stopRefreshTask();

    this.model.refresh(data.logType);
  },

  handleLatestLogRefreshLog: function(){
    console.log("fetch latest log.");
    // 切换到最新log模式
    this.model.set("usedLatestLogMode", true);
    this.model.trigger("change:logView", this.model.get("latestLogData"));
    var isSuccess = true;
    // 是否是第一次点击
    if(!this.model.get("latestLogBtnFirstClick")){
      var self = this;
      // offset = fileSize - len
      var requestData = {
        "execid": execId,
        "jobId": jobId,
        "ajax": "latestLogOffset",
        "len": 100000,
        "attempt": attempt,
      };
      var requestURL = contextURL + "/executor";
      var successHandler = function(data){
        if(data.error){
          console.log("fetch log offset failed.");
          isSuccess = false;
          return;
        }
        self.model.set({"latestLogOffset": data.offset});
        self.model.set({"latestLogBtnFirstClick": true});
      }
      $.ajax({
        url: requestURL,
        type: "get",
        async: false,
        data: requestData,
        dataType: "json",
        error: function (data) {
          console.log("get offset error." + data);
        },
        success: successHandler
      });
    }
    stopRefreshTask();
    if(isSuccess){
      this.refreshLatestLog();
    }
  },
  refreshLatestLog: function(){
    var data = {"logType":"refresh"};
    this.model.refreshLatestLog(data.logType);
  },

  handleYarnLog: function(){

    var data = {"logType":"yarn"};

    stopRefreshTask();

    this.model.error_info(data.logType);
  },


});

var showDialog = function (title, message) {
  $('#messageTitle').text(title);
  $('#messageBox').text(message);
  $('#messageDialog').modal({
    closeHTML: "<a href='#' title='Close' class='modal-close'>x</a>",
    position: ["20%",],
    containerId: 'confirm-container',
    containerCss: {
      'height': '220px',
      'width': '565px'
    },
    onShow: function (dialog) {
    }
  });
}
var jobLogModel;
$(function () {
  jobLogModel = new azkaban.JobLogModel();
  jobLogView = new azkaban.JobLogView({
    el: $('#jobLogView'),
    model: jobLogModel
  });
  jobLogModel.refresh();
});
