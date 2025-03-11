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

var jobHistoryView;

var jobDataModel;
azkaban.JobDataModel = Backbone.Model.extend({});

var jobTimeGraphView;


azkaban.JobHistoryView = Backbone.View.extend({
    events: {
        "click #pageTable .projectPageSelection  li": "handleChangePageSelection",
        "change #pageTable .pageSizeSelect": "handlePageSizeSelection",
        "click #pageTable .pageNumJump": "handlePageNumJump",
    },

    initialize: function(settings) {
        this.model.bind('render', this.render, this);

        this.model.set('elDomId','pageTable');
        this.model.bind('change:view', this.handleChangeView, this);
        this.model.bind('change:page', this.handlePageChange, this);
        this.model.set({ page: 1, pageSize: 20 });
        this.createResize();
    },
    //组装数据表格
    render: function(evt) {
        console.log("render");
        // Render page selections

        // var tbody = $("#jobHistoryTableBody");
        // tbody.empty();
        var oldJobHistoryTableBody = document.getElementById('jobHistoryTableBody');
        var childrenNum = oldJobHistoryTableBody.children.length;
        var newJobHistoryTableBody = document.createElement('tbody');
        newJobHistoryTableBody.setAttribute('id', 'jobHistoryTableBody');
        var jobPageList = this.model.get("jobPageList");
        for (var i = 0; i < jobPageList.length; ++i) {
            var row = document.createElement("tr");

            //组装执行ID
            var tdId = document.createElement("td");
            var execA = document.createElement("a");
            $(execA).attr("href", filterXSS("/executor?execid=" + jobPageList[i].execId + "&jobId=" + jobPageList[i].jobId));
            $(execA).text(jobPageList[i].execId);
            tdId.appendChild(execA);
            row.appendChild(tdId);

            //组装任务名
            var tdUser = document.createElement("td");
            var jobA = document.createElement("a");
            var flowArr = jobPageList[i].flowId.split(",");
            var fullFlow = flowArr[flowArr.length - 1];
            var flowName = fullFlow.substring(fullFlow.indexOf(':') +1);

            // $(jobA).attr("href", filterXSS("/manager?project=" + projectName + "&flow=" +
            //     jobPageList[i].flowId.split(":").slice(-1)[0] + "&job=" + jobPageList[i].jobId));
            $(jobA).attr("href", filterXSS("/manager?project=" + projectName + "&flow=" +
                flowName + "&job=" + jobPageList[i].jobId));
            $(jobA).text(jobPageList[i].jobId);
            $(jobA).attr("style", "width: 350px; word-break:break-all;");
            tdUser.appendChild(jobA);
            row.appendChild(tdUser);

            //组装工作流
            var tdFlow = document.createElement("td");
            var flowA = document.createElement("a");
            $(flowA).attr("href", filterXSS("/manager?project=" + projectName + "&flow=" + flowName));
            $(flowA).text(jobPageList[i].flowId);
            $(flowA).attr("style", "width: 350px; word-break:break-all;");
            tdFlow.appendChild(flowA);
            row.appendChild(tdFlow);

            //组装开始时间
            var startTime = "-";
            if (jobPageList[i].startTime != -1) {
                var startDateTime = new Date(jobPageList[i].startTime);
                startTime = getProjectModifyDateFormat(startDateTime);
            }
            var tdStartTime = document.createElement("td");
            $(tdStartTime).text(startTime);
            row.appendChild(tdStartTime);

            //组装结束时间
            var endTime = "-";
            var lastTime = jobPageList[i].endTime;
            if (jobPageList[i].endTime != -1 && jobPageList[i].endTime != 0) {
                var endDateTime = new Date(jobPageList[i].endTime);
                endTime = getProjectModifyDateFormat(endDateTime);
            } else {
                lastTime = (new Date()).getTime();
            }
            var tdEndTime = document.createElement("td");
            $(tdEndTime).text(endTime);
            row.appendChild(tdEndTime);

            //组装跑批日期
            var runDate = jobPageList[i].runDate;
            if (runDate > 0) {
                runDate = getRecoverRunDateFormat(new Date(jobPageList[i].runDate));
            } else {
                runDate = "-";
            }
            var tdRunDate = document.createElement("td");
            $(tdRunDate).text(runDate);
            row.appendChild(tdRunDate);

            //组装执行时长
            var tdElapsed = document.createElement("td");
            $(tdElapsed).text(getDuration(jobPageList[i].startTime, lastTime));
            row.appendChild(tdElapsed);

            //组装执行状态
            var tdStatus = document.createElement("td");
            var status = document.createElement("div");
            $(status).addClass("status");
            $(status).addClass(jobPageList[i].status);
            $(status).text(statusStringMap[jobPageList[i].status]);
            tdStatus.appendChild(status);
            row.appendChild(tdStatus);

            //日志行
            var tdAction = document.createElement("td");
            $(tdAction).addClass("logLink");
            var logA = document.createElement("a");
            var jobPath = jobPageList[i].flowId.split(",").slice(1).map(function(a) { return a.split(":")[0]; });
            jobPath.push(jobPageList[i].jobId);
            jobPath = jobPath.join(":");
            $(logA).attr("href", filterXSS("/executor?execid=" +
                jobPageList[i].execId + "&job=" + jobPath + "&attempt=" + jobPageList[i].attempt));
            $(logA).text(wtssI18n.view.log);
            tdAction.appendChild(logA);
            row.appendChild(tdAction);

            //组装执行类型
            var tdFlowType = document.createElement("td");
            $(tdFlowType).text(jobPageList[i].flowType);
            row.appendChild(tdFlowType);

            newJobHistoryTableBody.appendChild(row);
        }
        oldJobHistoryTableBody.parentNode.replaceChild(newJobHistoryTableBody, oldJobHistoryTableBody);
        this.renderPagination(evt);
    },
    ...commonPaginationFun(),
    handlePageChange: function(evt) {
        var page = this.model.get("page");
        var pageSize = this.model.get("pageSize");
        requestURL = "/manager";

        var model = this.model;
        var requestData = {
            "project": projectName,
            "jobId": jobName,
            "ajax": "fetchJobHistoryPage",
            "page": page,
            "size": pageSize,
        };
        var search = model.get('search') || true;
        var advfilter = model.get('advfilter') || false;
        var preciseSearch = model.get('preciseSearch') || false;
        var filterParam = model.get("filterParam");
        if (preciseSearch) {
            requestData.preciseSearch = true;
            Object.assign(requestData, filterParam);
            delete requestData.search;
            delete requestData.advfilter;
        }  else if (advfilter) {
            requestData.advfilter = true;
            Object.assign(requestData, filterParam);
            delete requestData.search;
            delete requestData.preciseSearch;
        } else if (search) {
            requestData.search = true;
            requestData.searchTerm = model.get('searchTerm') || '';
        }
        var successHandler = function(data) {
            model.set({
                "jobPageList": data.jobPageList,
                "total": data.total,
            });
            model.trigger("render");
        };
        $.get(requestURL, requestData, successHandler, "json");
    },


});

var advTaskFilterView;
azkaban.AdvTaskFilterView = Backbone.View.extend({
    events: {
        "click #task-filter-btn": "handleAdvFilter",//模糊查询
        // "click #task-precise-filter-btn": "preciseFilter"//精准查询
    },

    initialize: function (settings) {
        $('#startDatetimeBegin').datetimepicker();
        $('#startDatetimeEnd').datetimepicker();
        $('#endDatetimeBegin').datetimepicker();
        $('#endDatetimeEnd').datetimepicker();
        $('#runDate').datetimepicker({
            format: 'YYYYMMDD'
        });
        $('#startDatetimeBegin').on('change.dp', function (e) {
            $('#startDatetimeEnd').data('DateTimePicker').setStartDate(e.date);
        });
        $('#startDatetimeEnd').on('change.dp', function (e) {
            $('#startDatetimeBegin').data('DateTimePicker').setEndDate(e.date);
        });
        $('#endDatetimeBegin').on('change.dp', function (e) {
            $('#endDatetimeEnd').data('DateTimePicker').setStartDate(e.date);
        });
        $('#endDatetimeEnd').on('change.dp', function (e) {
            $('#endDatetimeBegin').data('DateTimePicker').setEndDate(e.date);
        });
        $('#runDate').on('change.dp', function (e) {
            $('#runDate').data('DateTimePicker').setRunDate(e.date);
        })
        $('#adv-filter-error-msg').hide();
        $('.selected').children("a").css("background-color", "#c0c1c2");
        $('#status').select2();
    },
    render: function () {
    },
    handleAdvFilter () {
        this.submitAdvFilter('filter')
    },
    preciseFilter () {
        this.submitAdvFilter('precise')
    },
    submitAdvFilter: function (filterType) {
        console.log("handleAdv");
        var execIdcontain = $('#execIdcontain').val();
        var usercontain = $('#usercontain').val();
        var status = $('#status').val();
        var startBeginTime = $('#startDatetimeBegin').val();
        var startEndTime = $('#startDatetimeEnd').val();
        var finishBeginTime = $('#endDatetimeBegin').val();
        var finishEndTime = $('#endDatetimeEnd').val();
        var flowType = $('#flowType').val();
        var runDate = $('#runDate').val();


        if (checkExecId(execIdcontain)) {
            return;
        };
        if (checkEnglish(usercontain)) {
            return;
        };

        // 将所有状态设置为默认
        if (!status|| (status.length === 1 && status[0] === "0")) {
            status = "0";
            $('#status').val([0]).trigger("change");
        } else {
            for (var i = status.length - 1; i >= 0; i--) {
                if (status[i] === "0") {
                    status.splice(i, 1)
                }
            }
            status = status.toString()
        };
        if (filterType === 'filter') {
            jobHistoryView.model.set({
                preciseSearch: false,
                search: false,
                advfilter: true,
                searchTerm: '',
                page: 1
            })
        } else {
            jobHistoryView.model.set({
                preciseSearch: true,
                search: false,
                advfilter: false,
                searchTerm: '',
                page: 1
            })
        }
        console.log("filtering history");
        jobHistoryView.init = false;
        $('#searchtextbox').val('');
        jobHistoryView.model.set('filterParam', {
            execIdcontain: execIdcontain,
            usercontain: usercontain,
            status: status,
            startBeginTime: startBeginTime,
            startEndTime: startEndTime,
            finishBeginTime: finishBeginTime,
            finishEndTime: finishEndTime,
            flowType: flowType,
            runDate: runDate
        })
        //请求接口
        jobHistoryView.model.trigger("change:view");
        $("#adv-task-filter").modal('hide');
    },
    initFilterForm () {
        $('#execIdcontain').val('');
        $('#usercontain').val('');
        $('#status').val([0]).trigger("change");
        $('#startDatetimeBegin').val('');
        $('#startDatetimeEnd').val('');
        $('#endDatetimeBegin').val('');
        $('#endDatetimeEnd').val('');
        $('#flowType').val('');
        $('#runDate').val('');
    },

});

$(function() {

    jobDataModel = new azkaban.JobDataModel();
    jobHistoryView = new azkaban.JobHistoryView({
        el: $('#jobHistoryView'),
        model: jobDataModel
    });

    //var selected;
    // var series = dataSeries;
    //
    // jobDataModel.set({
    //   "data": series
    // });
    //jobDataModel.trigger('render');

    jobTimeGraphView = new azkaban.TimeGraphView({
        el: $('#timeGraph'),
        model: jobDataModel,
        modelField: "jobPageList"
    });

    // var hash = window.location.hash;
    // if ("#page" == hash.substring(0, "#page".length)) {
    //     var arr = hash.split("#");
    //     var page = arr[1].substring("#page".length - 1, arr[1].length);
    //     var pageSize = arr[2].substring("#pageSize".length - 1, arr[2].length);

    //     $("#pageSizeSelect").val(pageSize);

    //     console.log("page " + page);
    //     jobDataModel.set({
    //         "page": parseInt(page),
    //         "pageSize": parseInt(pageSize),
    //     });
    //     jobDataModel.trigger("change:view");
    // } else {
    //     var pageNum = $("#pageSizeSelect").val();
    //     jobDataModel.set({ "page": 1, pageSize: pageNum });
    //     jobDataModel.trigger("change:view");
    // }
    function searchTableList() {
        var searchterm = $('#searchtextbox').val()
        jobDataModel.set({
            preciseSearch: false,
            search: true,
            advfilter: false,
            searchTerm: searchterm,
            page: 1
        });
        jobHistoryView.model.trigger("change:view");
    }
    $("#task-quick-serach-btn").click(function () {
        searchTableList();
    });

    advTaskFilterView = new azkaban.AdvTaskFilterView({
        el: $('#adv-task-filter'),
        model: jobDataModel
    });

    $('#adv-task-filter-btn').click(function () {
        $('#adv-task-filter').modal();
        advTaskFilterView.initFilterForm()
    });

    $('#searchtextbox').keyup(function (e) {
        if (e.keyCode === 13) {
            searchTableList();
        }
    });
});