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

var logModel;
azkaban.LogModel = Backbone.Model.extend({});

// From ProjectLogEvent.java
// ERROR(128), CREATED(1), DELETED(2), USER_PERMISSION(3), GROUP_PERMISSION(4), DESCRIPTION(5);
var typeMapping = {
  "ERROR": "Error",
  "CREATED": "Project Created",
  "DELETED": "Project Deleted",
  "USER_PERMISSION": "User Permission",
  "GROUP_PERMISSION": "Group Permission",
  "DESCRIPTION": "Description Set",
  "SCHEDULE": "Schedule",
  "UPLOADED": "Uploaded",
  "PROPERTY_OVERRIDE": "Property Override",
  "IMS_PROPERTIES": "IMS Report",
  "JOB_EXECUTE_LIMIT": "Job Execute Limit"
};

var projectLogView;
azkaban.ProjectLogView = Backbone.View.extend({
  events: {
    "click #updateLogBtn": "handleUpdate"
  },

  initialize: function (settings) {
    var search = window.location.search.substring(1);
    var paramArr = search.split('&');
    var param = {}
    for (var i = 0; i < paramArr.length; i++) {
        var tem = paramArr[i].split('=');
        param[tem[0]] = tem[1] || '';
    }
    this.model.set({ "current": 0, projectId: param.projectId || '' });
    this.handleUpdate();
  },

  handleUpdate: function (evt) {
    var current = this.model.get("current");
    var requestURL = "/manager";
    var model = this.model;
    var requestData = {
      "project": projectName,
      "ajax": "fetchProjectLogs",
      "size": 1000,
      "skip": 0
    };
    if (model.get('projectId')) {
        requestData.projectId = model.get('projectId')
    }
    var successHandler = function (data) {
      console.log("fetchLogs");
      if (data.error) {
        showDialog("Error", data.error);
        return;
      }
      // Get the columns to map to the values.
      var columns = data.columns;
      var columnMap = {};
      for (var i = 0; i < columns.length; ++i) {
        columnMap[columns[i]] = i;
      }
      var logSection = $("#logTable").find("tbody")[0];
      $(logSection).empty();
      var logData = data.logData;
      for (var i = 0; i < logData.length; ++i) {
        var event = logData[i];
        var user = event[columnMap['user']];
        var time = event[columnMap['time']];
        var type = event[columnMap['type']];
        var message = event[columnMap['message']];

        var containerEvent = document.createElement("tr");
        $(containerEvent).addClass("projectEvent");

        var containerTime = document.createElement("td");
        $(containerTime).addClass("time");
        $(containerTime).text(getProjectModifyDateFormat(new Date(time)));

        var containerUser = document.createElement("td");
        $(containerUser).addClass("user");
        $(containerUser).text(user);

        var containerType = document.createElement("td");
        var containerMessage = document.createElement("td");

        // If the event is a property override change, highlight by red color.
        if (type == "PROPERTY_OVERRIDE") {
          $(containerMessage).css("color", "red");
          $(containerMessage).css("white-space", "pre-wrap")
          $(containerType).css("color", "red");
        }
        $(containerMessage).css("word-break", "break-all");
        $(containerType).addClass("type");
        $(containerType).addClass(type);
        $(containerType).text(typeMapping[type] ? typeMapping[type] : type);

        $(containerMessage).html(message);

        $(containerEvent).append(containerTime);
        $(containerEvent).append(containerUser);
        $(containerEvent).append(containerType);
        $(containerEvent).append(containerMessage);

        $(logSection).append(containerEvent);
      }

      model.set({"log": data});
    };
    $.get(requestURL, requestData, successHandler);
  }
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

$(function () {
  var selected;

  logModel = new azkaban.LogModel();
  projectLogView = new azkaban.ProjectLogView(
      {el: $('#projectLogView'), model: logModel});
});
