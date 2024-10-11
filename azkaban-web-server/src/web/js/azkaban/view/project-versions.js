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

var versionModel;
azkaban.VersionModel = Backbone.Model.extend({});

var projectVersionView;
azkaban.ProjectVersionView = Backbone.View.extend({
    events: {},

    initialize: function(settings) {
        this.handleChangeView();
    },

    handleChangeView: function(evt) {
        var requestURL = "/manager";
        var model = this.model;
        var requestData = {
            "project": projectName,
            "ajax": "fetchProjectVersions",
            "size": 10,
            "skip": 0
        };

        var successHandler = function(data) {
            console.log("fetchVersions");
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
            var versionSection = $("#versionTable").find("tbody")[0];
            $(versionSection).empty();
            var versionData = data.versionData;
            for (var i = 0; i < versionData.length; ++i) {
                var data = versionData[i];
                var projectId = data[columnMap['projectId']];
                var versionNum = data[columnMap['version']];
                var uploadTime = data[columnMap['uploadTime']];

                var container = document.createElement("tr");
                $(container).addClass("projectVersion");

                //版本号
                var containerVersion = document.createElement("td");
                $(containerVersion).text(versionNum);

                //上传时间
                var containerUploadTime = document.createElement("td");
                $(containerUploadTime).text(getProjectModifyDateFormat(new Date(uploadTime)));

                //组装 下载按钮
                var containerDownloadBtn = document.createElement("td");
                var downloadBtn = document.createElement("button");
                $(downloadBtn).attr("class", "btn btn-sm btn-info").attr("type", "button").attr("onclick","window.location.href='/manager?project="+projectName+"&version="+versionNum+"&download=true'");
                $(downloadBtn).text(downloadBtnText);
                containerDownloadBtn.appendChild(downloadBtn);

                $(container).append(containerVersion);
                $(container).append(containerUploadTime);
                $(container).append(containerDownloadBtn);

                $(versionSection).append(container);
            }

        };
        $.get(requestURL, requestData, successHandler);
    }
});

var showDialog = function(title, message) {
    $('#messageTitle').text(title);

    $('#messageBox').text(message);

    $('#messageDialog').modal({
        closeHTML: "<a href='#' title='Close' class='modal-close'>x</a>",
        position: ["20%", ],
        containerId: 'confirm-container',
        containerCss: {
            'height': '220px',
            'width': '565px'
        },
        onShow: function(dialog) {}
    });
}

$(function() {
    versionModel = new azkaban.VersionModel();
    projectVersionView = new azkaban.ProjectVersionView({ el: $('#projectVersionView'), model: versionModel });
});