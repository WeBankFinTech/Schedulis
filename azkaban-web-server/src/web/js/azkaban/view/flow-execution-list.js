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

/*
 * List of executing jobs on executing flow page.
 */

var executionListView;
azkaban.ExecutionListView = Backbone.View.extend({
    events: {
        //"contextmenu .flow-progress-bar": "handleProgressBoxClick"
    },

    initialize: function(settings) {
        this.model.bind('change:graph', this.renderJobs, this);
        this.model.bind('change:update', this.updateJobs, this);

        // This is for tabbing. Blah, hacky
        var executingBody = $("#executableBody")[0];
        executingBody.level = 0;
    },

    renderJobs: function(evt) {
        var data = this.model.get("data");
        var executingBody = $("#executableBody");
        JobListNodesSort(data.nodes)
        this.updateJobRow(data.nodes, executingBody);

        var flowLastTime = data.endTime == -1 ? (new Date()).getTime() :
            data.endTime;
        var flowStartTime = data.startTime;
        this.updateProgressBar(data, flowStartTime, flowLastTime);

        this.expandFailedOrKilledJobs(data.nodes);
    },

    //
    //  handleProgressBoxClick: function(evt) {
    //    var target = evt.currentTarget;
    //    var job = target.job;
    //    var attempt = target.attempt;
    //
    //    var data = this.model.get("data");
    //    var node = data.nodes[job];
    //
    //    var jobId = event.currentTarget.jobid;
    //    var requestURL =  "/manager?project=" + projectName + "&execid=" + execId + "&job=" + job + "&attempt=" + attempt;
    //
    //    var menu = [
    //        {title: "Open Job...", callback: function() {window.location.href=requestURL;}},
    //        {title: "Open Job in New Window...", callback: function() {window.open(requestURL);}}
    //    ];
    //
    //    contextMenuView.show(evt, menu);
    //  },
    updateJobs: function(evt) {
        $("#statusFilterList").val('filter_job_status')
        $("#jobsFilterList").val('filter_job_name')
        var originData = this.model.get("data")
        var update = _.cloneDeep(this.model.get("update"));
        var executingBody = $("#executableBody");

        if (update.nodes) {
            JobListNodesSort(update.nodes)
            this.updateJobRow(update.nodes, executingBody, originData);
            this.expandFailedOrKilledJobs(update.nodes);
        }

        var data = this.model.get("data");
        var flowLastTime = data.endTime == -1 ?
            (new Date()).getTime() :
            data.endTime;
        var flowStartTime = data.startTime;
        this.updateProgressBar(data, flowStartTime, flowLastTime);
        // if (historyList && historyList.length) {
        //     $("#execuJobtable").trigger("update");
        //     $("#execuJobtable").trigger("sorton", "");
        // }
    },
    //任务列表构建方法
    updateJobRow: function(nodes, body, originData, expand) { // expand 是否展开
        if (!nodes) {
            return;
        }

        // nodes.sort(function (a, b) {
        //   return a.startTime - b.startTime;
        // });
        for (var i = 0; i < nodes.length; ++i) {
            var node = nodes[i].changedNode ? nodes[i].changedNode : nodes[i];

            // if (node.status == 'READY') {
            //     continue;
            // }

            //var nodeId = node.id.replace(".", "\\\\.");
            var row = node.joblistrow;
            if (!row || (row && expand)) {
                this.addNodeRow(node, body, expand);
            }
            row = node.joblistrow
                // 如果最开始返回节点状态是READY ，将不会渲染没有joblistrow，导致状态更新时候渲染多次
            if (nodes[i].changedNode) {
                for (var j = 0; j < originData.nodes.length; ++j) {
                    var originNode = originData.nodes[j]
                    if (!originNode.joblistrow && originNode.id === node.id) {
                        originNode.joblistrow = row
                    }
                }
            }
            var statusDiv = $(row).find("> td.statustd > .status");
            statusDiv.text(statusStringMap[node.status]);
            $(statusDiv).attr("class", "status " + node.status);

            var startTimeTd = $(row).find("> td.startTime");
            if (node.startTime == -1) {
                $(startTimeTd).text("-");
            } else {
                var startdate = new Date(node.startTime);
                $(startTimeTd).text(getProjectModifyDateFormat(startdate));
            }

            var endTimeTd = $(row).find("> td.endTime");
            if (node.endTime == -1) {
                $(endTimeTd).text("-");
            } else {
                var enddate = new Date(node.endTime);
                $(endTimeTd).text(getProjectModifyDateFormat(enddate));
            }

            var progressBar = $(row).find(
                "> td.timeline > .flow-progress > .main-progress");
            if (!progressBar.hasClass(node.status)) {
                for (var j = 0; j < statusList.length; ++j) {
                    var status = statusList[j];
                    progressBar.removeClass(status);
                }
                progressBar.addClass(node.status);
            }

            // Create past attempts
            if (node.pastAttempts) {
                for (var a = 0; a < node.pastAttempts.length; ++a) {
                    var attempt = node.pastAttempts[a];
                    var attemptBox = attempt.attemptBox;

                    if (!attemptBox) {
                        var attemptBox = document.createElement("div");
                        attempt.attemptBox = attemptBox;

                        $(attemptBox).addClass("flow-progress-bar");
                        $(attemptBox).addClass("attempt");

                        $(attemptBox).css("float", "left");
                        $(attemptBox).bind("contextmenu", attemptRightClick);

                        $(progressBar).before(attemptBox);
                        attemptBox.job = node.nestedId;
                        attemptBox.attempt = a;
                    }
                }
            }

            var elapsedTime = $(row).find("> td.elapsedTime");
            if (node.endTime == -1) {
                $(elapsedTime).text(
                    getDuration(node.startTime, (new Date()).getTime()));
            } else {
                $(elapsedTime).text(getDuration(node.startTime, node.endTime));
            }

            if (node.nodes) {
                $(row).attr('name', node.id);
                $(row.subflowrow).attr('name', node.id);
                var subtableBody = $(row.subflowrow).find("> td > table");
                if (!subtableBody[0]) {
                    continue;
                }
                subtableBody[0].level = $(body)[0].level + 1;
                this.updateJobRow(node.nodes, subtableBody);
                if (expand) {
                    $(row).addClass("expanded");
                    $(row.subflowrow).show()
                }
            }
        }
    },

    updateProgressBar: function(data, flowStartTime, flowLastTime, nodeList) {
        var outerWidth = $(".flow-progress").css("width");
        if (outerWidth) {
            if (outerWidth.substring(outerWidth.length - 2, outerWidth.length) ==
                "px") {
                outerWidth = outerWidth.substring(0, outerWidth.length - 2);
            }
            outerWidth = parseInt(outerWidth);
        }

        var parentLastTime = data.endTime == -1 ? (new Date()).getTime() :
            data.endTime;
        var parentStartTime = data.startTime;

        var factor = outerWidth / (flowLastTime - flowStartTime);
        var outerProgressBarWidth = parseInt(factor * (parentLastTime - parentStartTime));
        var outerLeftMargin = parseInt(factor * (parentStartTime - flowStartTime));

        var nodes = [];
        if (nodeList) {
            nodes = nodeList
        } else {
            nodes = data.nodes
        }
        for (var i = 0; i < nodes.length; ++i) {
            var node = nodes[i];
            // calculate the progress
            var tr = node.joblistrow;
            var outerProgressBar = $(tr).find("> td.timeline > .flow-progress");
            var progressBar = $(tr).find(
                "> td.timeline > .flow-progress > .main-progress");
            var offsetLeft = 0;
            var minOffset = 0;
            progressBar.attempt = 0;

            // Shift the outer progress
            $(outerProgressBar).css("width", outerProgressBarWidth)
            $(outerProgressBar).css("margin-left", outerLeftMargin);
            // $(outerProgressBar).css("margin-left", 0);
            // Add all the attempts
            if (node.pastAttempts) {
                var logURL = "/executor?execid=" + execId + "&job=" +
                    node.nestedId + "&attempt=" + node.pastAttempts.length;
                var anchor = $(tr).find("> td.details > a");
                if (anchor.length != 0) {
                    $(anchor).attr("href", filterXSS(logURL));
                    progressBar.attempt = node.pastAttempts.length;
                }

                // Calculate the node attempt bars
                for (var p = 0; p < node.pastAttempts.length; ++p) {
                    var pastAttempt = node.pastAttempts[p];
                    var pastAttemptBox = pastAttempt.attemptBox;

                    var left = (pastAttempt.startTime - flowStartTime) * factor;
                    var width = parseInt(Math.max((pastAttempt.endTime - pastAttempt.startTime) *
                        factor, 3));

                    var margin = left - offsetLeft;
                    $(pastAttemptBox).css("margin-left", parseInt(left - offsetLeft));
                    $(pastAttemptBox).css("width", width);

                    $(pastAttemptBox).attr("title", "attempt:" + p + "  start:" +
                        getHourMinSec(new Date(pastAttempt.startTime)) + "  end:" +
                        getHourMinSec(new Date(pastAttempt.endTime)));
                    offsetLeft += width + margin;
                }
            }

            var nodeLastTime = node.endTime == -1 ? (new Date()).getTime() :
                node.endTime;
            var nodeStartTime = node.startTime == -1 ? (new Date()).getTime() :
                node.startTime;
            var left = parseInt(Math.max((nodeStartTime - parentStartTime) * factor),
                minOffset);
            var margin = left - offsetLeft;
            var width = Math.max((nodeLastTime - nodeStartTime) * factor, 3);

            width = parseInt(Math.min(width, outerWidth));

            progressBar.css("margin-left", left)
            progressBar.css("width", width);
            progressBar.attr("title", "attempt:" + progressBar.attempt + "  start:" +
                getHourMinSec(new Date(node.startTime)) + "  end:" + getHourMinSec(
                    new Date(node.endTime)));

            if (node.nodes) {
                this.updateProgressBar(node, flowStartTime, flowLastTime);
            }
        }
    },

    /**
     * Expands or collapses a flow node according to the value of the expand
     * parameter.
     *
     * @param flow - node to expand/collapse
     * @param expand - if value true -> expand, false: collapse,
     *                 undefined -> toggles node status
     */
    setFlowExpansion: function(flow, expand) {
        var tr = flow.joblistrow;
        var subFlowRow = tr.subflowrow;
        var expandIcon = $(tr).find("> td > .listExpand");

        var needsExpansion = !tr.expanded && (expand === undefined || expand);
        var needsCollapsing = tr.expanded && (expand === undefined || !expand);

        if (needsExpansion) {
            tr.expanded = true;
            $(expandIcon).addClass("glyphicon-chevron-up");
            $(expandIcon).removeClass("glyphicon-chevron-down");
            $(tr).addClass("expanded");
            $(subFlowRow).show();

        } else if (needsCollapsing) {
            tr.expanded = false;
            $(expandIcon).removeClass("glyphicon-chevron-up");
            $(expandIcon).addClass("glyphicon-chevron-down");
            $(tr).removeClass("expanded");
            $(subFlowRow).hide();
        } // else do nothing
    },

    expandFailedOrKilledJobs: function(nodes) {
        var hasFailedOrKilled = false;
        for (var i = 0; i < nodes.length; ++i) {
            var node = nodes[i].changedNode ? nodes[i].changedNode : nodes[i];

            if (node.type === "flow") {
                if (this.expandFailedOrKilledJobs(node.nodes || [])) {
                    hasFailedOrKilled = true;
                    this.setFlowExpansion(node, true);
                }

            } else if (node.status === "FAILED" || node.status === "KILLED") {
                hasFailedOrKilled = true;
            }
        }
    },

    addNodeRow: function(node, body, expand) {
        var self = this;
        var tr = document.createElement("tr");
        var tdName = document.createElement("td");
        var tdType = document.createElement("td");
        var tdTimeline = document.createElement("td");
        var tdStart = document.createElement("td");
        var tdEnd = document.createElement("td");
        var tdRunDate = document.createElement("td");
        var tdElapse = document.createElement("td");
        var tdStatus = document.createElement("td");
        var tdDetails = document.createElement("td");
        var tdAction = document.createElement("td");
        node.joblistrow = tr;
        tr.node = node;
        var padding = 15 * $(body)[0].level;

        $(tr).append(tdName);
        $(tr).append(tdType);
        $(tr).append(tdTimeline);
        $(tr).append(tdStart);
        $(tr).append(tdEnd);
        $(tr).append(tdRunDate);
        $(tr).append(tdElapse);
        $(tr).append(tdStatus);
        $(tr).append(tdDetails);
        $(tr).append(tdAction);
        $(tr).addClass("jobListRow");

        $(tdName).addClass("jobname");
        $(tdName).attr("style", "width: 500px; word-break:break-all;"); //解决名字过长问题
        $(tdType).addClass("jobtype");
        if (padding) {
            $(tdName).css("padding-left", padding);
        }
        $(tdTimeline).addClass("timeline");
        $(tdStart).addClass("startTime");
        $(tdEnd).addClass("endTime");
        $(tdRunDate).addClass("runDate");
        $(tdElapse).addClass("elapsedTime");
        $(tdStatus).addClass("statustd");
        $(tdDetails).addClass("details").css({ "width": "50px" });
        $(tdAction).addClass("");

        $(tdType).text(node.type);

        var outerProgressBar = document.createElement("div");
        //$(outerProgressBar).attr("id", node.id + "-outerprogressbar");
        $(outerProgressBar).addClass("flow-progress");

        var progressBox = document.createElement("div");
        progressBox.job = node.id;
        //$(progressBox).attr("id", node.id + "-progressbar");
        $(progressBox).addClass("flow-progress-bar");
        $(progressBox).addClass("main-progress");
        $(outerProgressBar).append(progressBox);
        $(tdTimeline).append(outerProgressBar);

        var requestURL = "/manager?project=" + projectName + "&job=" +
            node.id + "&history";
        //Name字段
        var a = document.createElement("a");
        $(a).attr("href", filterXSS(requestURL));
        $(a).text(node.id);
        $(tdName).append(a);
        if (node.type == "flow") {
            var expandIcon = document.createElement("div");
            $(expandIcon).addClass("listExpand");
            $(tdName).append(expandIcon);
            if (expand) {
                $(expandIcon).addClass("expandarrow glyphicon glyphicon-chevron-up");
            } else {
                $(expandIcon).addClass("expandarrow glyphicon glyphicon-chevron-down");
            }

            $(expandIcon).click(function(evt) {
                var parent = $(evt.currentTarget).parents("tr")[0];
                self.setFlowExpansion(parent.node);
            });
        }
        //状态字段
        var status = document.createElement("div");
        $(status).addClass("status");
        //$(status).attr("id", node.id + "-status-div");
        tdStatus.appendChild(status);

        //日志字段
        var logURL = "/executor?execid=" + execId + "&job=" +
            node.nestedId;
        if (node.attempt) {
            logURL += "&attempt=" + node.attempt;
        }

        if (node.type != 'flow' && node.status != 'SKIPPED') {
            var a = document.createElement("a");
            $(a).attr("href", filterXSS(logURL));
            //$(a).attr("id", node.id + "-log-link");
            $(a).text(wtssI18n.view.log);
            $(tdDetails).append(a);

            var a = document.createElement("a");
            $(a).attr("href", filterXSS(logURL + "&downloadLog=true"));
            $(a).addClass("btn btn-sm btn-info");
            var span = $("<span></span>");
            span.addClass("glyphicon glyphicon-download");
            $(a).append(span);
            $(a).text(wtssI18n.common.logDownload);
            $(tdAction).append(a);
        }

        //操作字段
        if (node.type != 'flow' && node.status != 'SKIPPED') {
            //<button type="button" id="job-id-relation-btn" class="btn btn-sm btn-success" >jobId关系</button>
            var idRelationBtn = document.createElement("button");
            $(idRelationBtn).attr("nestedId", node.nestedId);
            $(idRelationBtn).addClass("btn btn-sm btn-success job-id-relation");
            $(idRelationBtn).text(wtssI18n.view.jobIdRelationship);
            $(idRelationBtn).attr("style", "margin-left:5px");
            $(idRelationBtn).bind("click", showRelation);
            $(tdAction).append(idRelationBtn);
        }

        //运行日期
        if (node.runDate == -1) {
            $(tdRunDate).text("-");
        } else {
            var runDate = new Date(node.runDate);
            $(tdRunDate).text(getRecoverRunDateFormat(runDate));
        }

        $(body).append(tr);
        if (node.type == "flow") {
            var subFlowRow = document.createElement("tr");
            var subFlowCell = document.createElement("td");
            $(subFlowCell).addClass("subflowrow");

            var numColumn = $(tr).children("td").length;
            $(subFlowCell).attr("colspan", numColumn);
            tr.subflowrow = subFlowRow;

            $(subFlowRow).append(subFlowCell);
            $(body).append(subFlowRow);
            $(subFlowRow).hide();
            var subtable = document.createElement("table");
            var parentClasses = $(body).closest("table").attr("class");

            $(subtable).attr("class", parentClasses);
            $(subtable).addClass("subtable");
            $(subFlowCell).append(subtable);
        }
    }
});

var attemptRightClick = function(event) {
    var target = event.currentTarget;
    var job = target.job;
    var attempt = target.attempt;

    var jobId = event.currentTarget.jobid;
    var requestURL = filterXSS("/executor?project=" + projectName + "&execid=" +
        execId + "&job=" + job + "&attempt=" + attempt);

    var menu = [{
            title: "Open Attempt Log...",
            callback: function() {
                window.location.href = requestURL;
            }
        },
        {
            title: "Open Attempt Log in New Window...",
            callback: function() {
                window.open(requestURL);
            }
        }
    ];

    contextMenuView.show(event, menu);
    return false;
}

var showRelation = function(evt) {
    console.log("show relation");
    $("#jobid-relation-modal").modal();
    var tbody = $("#jobIdRelationBody");
    tbody.empty();
    $("#jobid-relation-title").text($(evt.currentTarget).attr("nestedid"));
    var requestURL = "/executor";
    var params = {
        "execid": execId,
        "nested_id": $(evt.currentTarget).attr("nestedid"),
        "ajax": "getJobIdRelation"
    }
    var successHandler = function(data) {
        if (data.error) {
            console.log(data.error);
        } else {

            for (var i = 0; i < data.data.length; i++) {
                var row = document.createElement("tr");
                //jobServerId
                var td1 = document.createElement("td");
                $(td1).text(data.data[i].jobServerJobId);
                row.appendChild(td1);

                //linkisId
                var td3 = document.createElement("td");
                var linkisIdHtml = "<span class='linkisId' execId='" + data.data[i].execId + "' jobName='" + data.data[i].jobNamePath + "'>" + data.data[i].linkisId + "</span>";
                $(td3).html(linkisIdHtml);
                row.appendChild(td3);

                //appid
                var td2 = document.createElement("td");
                var applicationId = data.data[i].applicationId.split(',');
                var applicationHtml = '';
                if (applicationId.length) {
                    for (var j=0;j < applicationId.length; j++) {
                        applicationHtml += "<div class='application-id'>" + applicationId[j] + "</div>";
                    }
                }
                $(td2).html(applicationHtml);
                row.appendChild(td2);

                //attempt
                var td4 = document.createElement("td");
                $(td4).text(data.data[i].attempt);
                row.appendChild(td4);

                tbody.append(row);
            }
        }
    };
    $.ajax({
        url: requestURL,
        type: "get",
        data: params,
        dataType: "json",
        error: function(data) {
            console.log(data.error);
        },
        success: successHandler
    });

}

$(function() {
    $('#exec-refresh-btn').click(function() {
        window.location.reload();
    });
    $("#jobIdRelationBody").click(function(e) {
        var target = e.target;
        var className = target.getAttribute('class');
        if (className === "application-id") {
            var applicationId = target.innerText.split('application_')
            $("#yarnId").prop('checked', false).attr("applicationId", applicationId[1])
            $("#historyId").prop('checked', false).attr("applicationId", applicationId[1])
            $('#log-modal').modal();
        } else if (className === "linkisId") {
            var params = {
                ajax: 'jumpToLogWebsite',
                execid: target.getAttribute('execId'),
                jobName: target.getAttribute('jobName'),
                targetName: 'linkis',
                targetId: target.innerText,
            }
            $.ajax({
                url: '/executor',
                type: "get",
                data: params,
                dataType: "json",
                error: function(data) {
                    console.log(data.error);
                },
                success: function(res) {
                    window.open(res.location, '_blank') = res.location
                }
            });
        }
    });
    $("input[name=applicationId]").click(function(e) {
        var applicationId = e.target.getAttribute('applicationId')
        var baseUrl = e.target.id === 'yarnId' ? window.yarnUrl + '/proxy/application_' + applicationId : window.jobHistoryUrl + '/job/job_' + applicationId
        window.open(baseUrl)
        $('#log-modal').modal('hide');
        $("#jobid-relation-modal").modal('hide');
    })
    $('#link-flow-data-btn').click(function(e) {
        var model = mainSvgGraphView.model.get('data')
        window.location.href = '/manager?project=' + model.project + '&flow=' + model.flow + '#linkFlowData'
    })
});