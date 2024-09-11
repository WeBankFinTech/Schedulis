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

$.namespace('azkaban');

var lastlogType = "refresh";

azkaban.JobLogModel = Backbone.Model.extend({
  initialize: function () {
    this.set("offset", 0);
    this.set("logData", "");
  },

  refresh: function(data) {
    var requestURL = contextURL + "/executor";
    var finished = false;
    var logType = data != undefined ? data : "";
    var ref_flag = 0;
    var requestData = {
      "execid": execId,
      "jobId": jobId,
      "ajax": "fetchExecJobLogs",
      "offset": this.get("offset"),
      "length": 100000,
      "attempt": attempt,
      "logType": logType
    };

    var self = this;

    var successHandler = function (data) {
      console.log("fetchLogs");
      if (data.error) {
        console.log(data.error);
        finished = true;
      }
      else if (data.length == 0) {
        finished = true;
      }
      else {
        if(ref_flag==0 && "refresh" != lastlogType && data.status == "Finish"){//第一次获取日志的时候，清空日志显示区域的数据。避免残留数据。
          self.set("logData", "");
          self.trigger("change:logView", self.get("logData"));
        }
        //Job状态执行完成时，才显示过滤日志按钮。
        if(data.status == "Finish" && !($("#errrorLogBtn").length>0) && !($("#infoLogBtn")>0)){

          // 执行成功之后清除定时任务
          clearInterval(timeTask);
          // 将自动刷新按钮设置为不可用
          document.getElementById('autoRefreshLogBtn').disabled = true;
          $("#job-log-button-div").append(
              '<button type="button" id="errrorLogBtn" class="btn btn-xs btn-info">'+wtssI18n.common.errorLog+'</button> '
              + '<button type="button" id="infoLogBtn" class="btn btn-xs btn-info">'+wtssI18n.common.InfoLog+'</button> '
              + '<button type="button" id="jobLogDownloadBtn" class="btn btn-xs btn-info">'+wtssI18n.common.logDownload+'</button> '
              + '<button type="button" id="yarnLogBtn" class="btn btn-xs btn-info">'+wtssI18n.common.YARNInfo+'</button> '
          )
        }
        setTimeout(function(){},1000);

        var offserLength = data.offset + data.length;
        self.set("offset", offserLength);
        self.set("logData", self.get("logData") + data.data);
        self.trigger("change:logView", self.get("logData"));
        $("#logSection").scrollTop(offserLength);
      }
      ref_flag++;
    }

    $.ajax({
      url: requestURL,
      type: "get",
      async: false,
      data: requestData,
      dataType: "json",
      error: function (data) {
        console.log(data);
        finished = true;
      },
      success: successHandler
    });

  },

  refreshLatestLog: function(data) {
    var requestURL = contextURL + "/executor";
    var finished = false;
    var logType = data != undefined ? data : "";
    var ref_flag = 0;
    var requestData = {
      "execid": execId,
      "jobId": jobId,
      "ajax": "fetchExecJobLogs",
      "offset": this.get("latestLogOffset"),
      "length": 100000,
      "attempt": attempt,
      "logType": logType
    };
    var self = this;
    var successHandler = function (data) {
      console.log("fetch latest Logs");
      if (data.error) {
        console.log(data.error);
        finished = true;
      }
      else if (data.length == 0) {
        finished = true;
      }
      else {
        if(ref_flag==0 && "refresh" != lastlogType && data.status == "Finish"){//第一次获取日志的时候，清空日志显示区域的数据。避免残留数据。
          self.set("latestLogData", "");
          self.trigger("change:logView", self.get("latestLogData"));
        }
        //Job状态执行完成时，才显示过滤日志按钮。
        if(data.status == "Finish" && !($("#errrorLogBtn").length>0) && !($("#infoLogBtn")>0)){

          // 执行成功之后清除定时任务
          clearInterval(timeTask);
          // 将自动刷新按钮设置为不可用
          document.getElementById('autoRefreshLogBtn').disabled = true;

          $("#job-log-button-div").append(
              '<button type="button" id="errrorLogBtn" class="btn btn-xs btn-info">'+wtssI18n.common.errorLog+'</button> '
              + '<button type="button" id="infoLogBtn" class="btn btn-xs btn-info">'+wtssI18n.common.InfoLog+'</button> '
              + '<button type="button" id="jobLogDownloadBtn" class="btn btn-xs btn-info">'+wtssI18n.common.logDownload+'</button> '
              + '<button type="button" id="yarnLogBtn" class="btn btn-xs btn-info">'+wtssI18n.common.YARNInfo+'</button> '
          )
        }
        setTimeout(function(){},1000);

        var offsetLength = data.offset + data.length;

        self.set("latestLogOffset", offsetLength);
        self.set("latestLogData", self.get("latestLogData") + data.data);
        self.trigger("change:logView", self.get("latestLogData"));
        $("#logSection").scrollTop(offsetLength);
      }
      ref_flag++;
    }

    $.ajax({
      url: requestURL,
      type: "get",
      async: false,
      data: requestData,
      dataType: "json",
      error: function (data) {
        console.log(data);
        finished = true;
      },
      success: successHandler
    });

  },

  error_info: function(data) {
    var requestURL = contextURL + "/executor";
    var finished = false;
    var logType = data != undefined ? data : "";
    lastlogType=logType;

    var requestData = {
      "execid": execId,
      "jobId": jobId,
      "ajax":"fetchExecJobLogs",
      "offset": this.get("offset"),
      "length": 100000,
      "attempt": attempt,
      "logType": logType
    };

    var self = this;

    var successHandler = function(data) {
      console.log("fetchLogs");
      if (data.error) {
        console.log(data.error);
      }
      else {
        self.set("offset", data.offset);
        self.set("logData", data.data);
        self.trigger("change:logView", self.get("logData"));
        $("#logSection").scrollTop(offserLength);
      }
    }

    $.ajax({
      url: requestURL,
      type: "get",
      async: false,
      data: requestData,
      dataType: "json",
      error: function(data) {
        console.log(data);
        finished = true;
      },
      success: successHandler
    });

  },

});
