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

var advFilterView;
azkaban.AdvFilterView = Backbone.View.extend({
    events: {
        "click #filter-btn": "handleAdvFilter",//模糊查询
        "click #precise-filter-btn": "preciseFilter"//精准查询
    },

    initialize: function (settings) {
        this.advQueryViewFirstShow = true
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
        this.renderBusPathSelect(historyListView.model.get('busPathMap'));
        this.renderSubSystemSelect(historyListView.model.get('subsystemMap'));
        this.renderDepartmentSelectelect(historyListView.model.get('departPathMap'));

        this.advQueryViewFirstShow = false
    },
    handleAdvFilter () {
        this.submitAdvFilter('filter')
    },
    preciseFilter () {
        this.submitAdvFilter('precise')
    },
    submitAdvFilter: function (filterType) {
        console.log("handleAdv");
        var projcontain = $('#projcontain').val();
        var flowcontain = $('#flowcontain').val();
        var execIdcontain = $('#execIdcontain').val();
        var usercontain = $('#usercontain').val();
        var status = $('#status').val();
        var startBeginTime = $('#startDatetimeBegin').val();
        var startEndTime = $('#startDatetimeEnd').val();
        var finishBeginTime = $('#endDatetimeBegin').val();
        var finishEndTime = $('#endDatetimeEnd').val();
        var flowType = $('#flowType').val() || "-1";
        var flowRemarks =  $('#flowRemarks').val();
        var runDate = $('#runDate').val();
        var subsystem = $('#subSystemQuery').val();
        var busPath = $('#busPathQuery').val();
        var departmentId = $('#departmentSelect').val();

        if (checkProject(projcontain)) {
            return;
        };
        if (checkProject(flowcontain)) {
            return;
        };
        if (checkExecId(execIdcontain)) {
            return;
        };
        if (checkEnglish(usercontain)) {
            return;
        };
        if (flowRemarks && (!projcontain || !flowcontain)) {
            messageBox.show(wtssI18n.view.flowRemarksRueryPro,'warning');
            return;
        }
        if (filterType === 'filter') {
            historyListView.model.set({
                preciseSearch: false,
                advfilter: true,
            })
        } else {
            historyListView.model.set({
                preciseSearch: true,
                advfilter: false,
            })
        }
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

        console.log("filtering history");

        historyListView.model.set('filterParam', {
            projcontain: projcontain,
            flowcontain: flowcontain,
            execIdcontain: execIdcontain,
            usercontain: usercontain,
            status: status,
            startBeginTime: startBeginTime,
            startEndTime: startEndTime,
            finishBeginTime: finishBeginTime,
            finishEndTime: finishEndTime,
            flowType: flowType,
            comment: flowRemarks,
            runDate: runDate,
            subsystem: subsystem,
            busPath: busPath,
            departmentId: departmentId
        })
        //请求接口
        historyListView.model.trigger("change:view");

    },
    initFilterForm () {
        $('#projcontain').val('');
        $('#flowcontain').val('');
        $('#execIdcontain').val('');
        $('#usercontain').val('');
        $('#status').val([0]).trigger("change");
        $('#startDatetimeBegin').val('');
        $('#startDatetimeEnd').val('');
        $('#endDatetimeBegin').val('');
        $('#endDatetimeEnd').val('');
        $('#flowType').val('');
        $('#flowRemarks').val('');
        $('#runDate').val('');
        $("#subSystemQuery").empty();
        $("#busPathQuery").empty();
        $("#departmentSelect").val("").trigger("change");
    },
    renderBusPathSelect: renderBusPathSelect,
    renderSubSystemSelect: renderSubSystemSelect,
    renderDepartmentSelectelect: renderDepartmentSelectelect

});


$(function () {

    // 在切换选项卡之前创建模型
    historyModel = new azkaban.HistoryModel();
    var urlSearch = window.location.search;
    if (urlSearch.indexOf("advfilter") != -1) {
        var arr = urlSearch.split("&");
        var projcontain = arr[1].split("=");
        var flowcontain = arr[2].split("=");
        var execIdcontain = arr[3].split("=");
        var usercontain = arr[4].split("=");
        var status = arr[5].split("=");
        var fromHomePage = arr[6].split("=");
        var flowType = arr[7].split("=");
        var runDate = arr[8].split("=");
        historyModel.set({
            "advfilter": true,
            preciseSearch: false,
            filterParam: {
                projcontain: projcontain[1],
                flowcontain: flowcontain[1],
                execIdcontain: execIdcontain[1],
                usercontain: usercontain[1],
                status: status[1],
                fromHomePage: fromHomePage[1],
                flowType: flowType[1],
                runDate: decodeURI(runDate[1])
            }
        });
    }

    historyListView = new azkaban.HistoryListView({
        el: $('#history-view-div'),
        model: historyModel
    });
    function quickSerach () {
        const searchterm = $('#searchtextbox').val();
        historyModel.set({ "searchterm": searchterm, advfilter: false, preciseSearch: false });
        historyListView.model.trigger("change:view");
        filterView.initFilterForm()
    }
    $("#quick-serach-btn").click(function () {
        quickSerach();
    });
    $('#searchtextbox').on('keyup', function (e) {
        if (e.keyCode === 13) {
            quickSerach();
        }
    });

    console.log("get User Role");

    var roles;
    $.ajax({
        url: "history?ajax=user_role",
        dataType: "json",
        type: "GET",
        //data: {},
        success: function (data) {
            roles = data.userRoles;
        }
    });

    filterView = new azkaban.AdvFilterView({
        el: $('#adv-filter'),
        model: historyModel
    });

    $('#adv-filter-btn').click(function () {
        $('#adv-filter').modal();
        filterView.render()
        filterView.initFilterForm()
        //用户只有user权限没有admin权限时 隐藏用户查找输入框
        // if($.inArray("user", roles) != -1 && $.inArray("admin", roles) == -1){
        //   $('#usercontain-div').hide();
        // }
    });
    function ajaxReSchedulisFlow (params) {
        function successHandler (data) {
            if (data.error || data.info) {
                messageBox.show(data.error || data.info, 'danger');
                return;
            }
            historyModel.set({ "searchterm": '', advfilter: false, preciseSearch: false });
            historyListView.model.trigger("change:view");
        }
        $.get("/executor", params, successHandler, "json");
    }
    // 重跑工作流（模拟准备执行），如果flow节点disabled，只需要传flow节点
    function gatherDisabledNodes(data) {
        var nodes = data.nodes;
        var disabled = [];

        for (var i = 0; i < nodes.length; ++i) {
            var node = nodes[i];
            if (node.disabled) {
                disabled.push(node.id);
            } else {
                if (node.type == "flow") {
                    var array = gatherDisabledNodes(node);
                    if (array && array.length > 0) {
                        disabled.push({ id: node.id, children: array });
                    }
                }
            }
        }
        return disabled;
    }
    /**
     * Disable jobs that need to be disabled
     */
    function disableFinishedJobs(data) {
        for (var i = 0; i < data.nodes.length; ++i) {
            var node = data.nodes[i];
            // 运行成功跳过执行 disabled节点执行后statis 是DISABLED
            if (["DISABLED", "SKIPPED", "SUCCEEDED", "RETRIED_SUCCEEDED", "FAILED_SKIPPED", "FAILED_SKIPPED_DISABLED"].includes(node.status)) {
                node.disabled = true;
            } else {
                node.disabled = false;
            }
            if (node.type == "flow") {
                disableFinishedJobs(node);
            }
        }
    }
    // 处理调度参数res调度信息
    function hanleFlowSchdulisInfo(res , flowInfo, flowData) {
        var settings = {};
        var loginUser = $(".nav.navbar-nav.navbar-right li a")[1].text;
        // 如果超时规则值有返回则拼接、没有则赋值默认值
        if (res.ruleType) {
            var ruleType = res.ruleType === "FlowSucceed" ? "SUCCESS" : "FINISH";
            settings[0] = "," + ruleType + "," + res.duration + "," +
            res.slaAlertLevel + "," + res.emailAction + "," +
            res.killAction;
        } else {
            settings[0] = ",FINISH,,INFO,false,false";
        }

        var jobFailedRetryObj = {};
        for (let index = 0; index < res.jobFailedRetryOptions.length; index++) {
            jobFailedRetryObj[index] = res.jobFailedRetryOptions[index].jobName + "," + res.jobFailedRetryOptions[index].interval + "," + res.jobFailedRetryOptions[index].count;
        }
        var jobSkipFailedOptions = {};
        for (let i = 0; i < res.jobSkipFailedOptions.length; i++) {
            jobSkipFailedOptions[i] = res.jobSkipFailedOptions[i];
        }
        // 节点添加disabled
        disableFinishedJobs(flowData);
        // 获取节点disabled ，由于nodeStatus节点节点状态没有返回
        var disabledList = gatherDisabledNodes(flowData);
        var executingData = {
            projectId: flowInfo.projectId,
            project: flowInfo.projectName,
            ajax: "executeFlow",
            flow: flowInfo.flowId,
            disabled: JSON.stringify(disabledList),
            failureEmailsOverride: res.failureEmailsOverride,
            successEmailsOverride: res.successEmailsOverride,
            failureAction: res.failureAction,
            failureEmails: res.failureEmails.toString() || loginUser,
            successEmails: res.successEmails.toString() || loginUser,
            notifyFailureFirst: res.notifyFailureFirst,
            notifyFailureLast: res.notifyFailureLast,
            flowOverride: res.flowParam,
            jobFailedRetryOptions: jobFailedRetryObj,
            failureAlertLevel: res.failureAlertLevel || "INFO",
            successAlertLevel: res.successAlertLevel || "INFO",
            jobSkipFailedOptions: jobSkipFailedOptions,
            jobSkipActionOptions: JSON.stringify(res.otherOption.jobSkipActionOptions),
            useTimeoutSetting: res.useTimeoutSetting,
            slaEmails: res.slaEmails ? res.slaEmails.toString() : loginUser,
            settings:  settings,
            taskDistributeMethod: res.taskDistributeMethod || 'uniform',
            concurrentOption: res.concurrentOptions,
            rerunAction: res.rerunAction,
            lastExecId: flowInfo.executionId,
            jobOutputParam: res.jobOutputGlobalParam,
            lastNsWtss: res.nsWtss,
            executeType: '异常重跑'
        }
        var flowRetryAlertOption = res.otherOption.flowRetryAlertOption;
        if ( flowRetryAlertOption ) {
            executingData.flowRetryAlertChecked = flowRetryAlertOption.flowRetryAlertChecked;
            executingData.flowRetryAlertLevel = flowRetryAlertOption.flowRetryAlertLevel;
            executingData.alertMsg = flowRetryAlertOption.alertMsg;
        } else {
            executingData.flowRetryAlertChecked = false;
            executingData.flowRetryAlertLevel = "INFO";
            executingData.alertMsg = wtssI18n.view.alertMsg;
        }
        if (res.concurrentOption == "pipeline") {
            executingData.pipelineLevel = res.pipelineLevel;
        } else if (res.concurrentOption == "queue") {
            executingData.queueLevel = res.queueLevel;
        }
        return executingData;
    }
    // 获取单个工作流信息
    function getFlowSchdulisInfo (param, flowData) {
        $.ajax({
            url: "/executor",
            dataType: "json",
            contentType: "application/json; charset=utf-8",
            type: "GET",
            data: {
                project: param.projectName,
                ajax: 'flowInfo',
                flow: param.flowId,
                execid: param.executionId
            },
            success: function (data) {
                if (data.error) {
                    messageBox.show(data.error, 'danger');
                    return;
                }
               var schdulisParam =  hanleFlowSchdulisInfo(data, param, flowData);
               ajaxReSchedulisFlow(schdulisParam);
            }
        });
    }
    // 获取工作流节点状态
    function getFlowData (param) {
        $.ajax({
            url: "/executor",
            dataType: "json",
            contentType: "application/json; charset=utf-8",
            type: "GET",
            data: {
                ajax: 'fetchexecflow',
                execid: param.executionId,
                nodeNestedId: ''
            },
            success: function (data) {
                getFlowSchdulisInfo(param, data);
            }
        })
    }
    //校验工作流权限
    function validFlowPermission () {
        var params = window.reScheduleCheckedtList.map(function (item) {
            return {
                projectName: item.projectName,
                flowId: item.flowId
            }
        })
        $.ajax({
            url: "/manager?ajax=batchVerifyProjectsPermission",
            dataType: "json",
            contentType: "application/json; charset=utf-8",
            type: "POST",
            data: JSON.stringify({
                projectFlowInfo: params
            }),
            success: function (data) {
                if (data.error) {
                    messageBox.show(data.error, 'danger');
                    return;
                }
                var noPermission = [];
                var count =  1;
                for (var i = 0; i <  data.checkFailed.length; i++) {
                    noPermission.push(data.checkFailed[i].projectName + '-' + data.checkFailed[i].flowId);
                };
                for (var k = window.reScheduleCheckedtList.length - 1; k >= 0; k--) {
                    if (count >  data.size) {
                        var prompt = window.langType === 'zh_CN' ? '批量重跑最多必能' + data.size + '个' : 'Batch reruns cannot exceed ' + data.size;
                        messageBox.show(prompt,'warning');
                        break;
                    }
                    if (!noPermission.includes(window.reScheduleCheckedtList[k].projectName + '-' + window.reScheduleCheckedtList[k].flowId)) {
                        getFlowData(window.reScheduleCheckedtList[k]);
                    }

                    count ++;
                }
                noPermission.length > 0 && messageBox.show(noPermission.toString() + wtssI18n.view.noReturnPermission,'warning');
                window.reScheduleCheckedtList = [];
                document.getElementById('selectAllFlowcheckbox').checked = false;
            }
        });
    }
    // 获取正在运行
    function getRunningList() {
        $.ajax({
            url: "/executor",
            dataType: "json",
            contentType: "application/json; charset=utf-8",
            type: "GET",
            data: {
                ajax: 'getExecutingFlowData'
            },
            success: function (data) {
                if (data.error) {
                    messageBox.show(data.error,'warning');
                    return;
                }
                var runningFlow = [];
                for (var i = 0; i <  data.executingFlowData.length; i++) {
                    for (var k = window.reScheduleCheckedtList.length - 1; k >= 0; k--) {
                        if (data.executingFlowData[i].projectName === window.reScheduleCheckedtList[k].projectName && data.executingFlowData[i].flowId === window.reScheduleCheckedtList[k].flowId) {
                            runningFlow.push(data.executingFlowData[i].projectName + '-' + data.executingFlowData[i].flowId);
                            window.reScheduleCheckedtList.splice(k , 1);
                        }
                    }
                };
                runningFlow.length > 0 && messageBox.show(runningFlow.toString() + wtssI18n.view.flowRunning,'warning');
                validFlowPermission();
            }
        });
    }
    // 批量重跑
    $('#batch-reRun-btn').click(function () {
       var checkboxList = $("#executingJobs .reSchedule-checkbox");
       // 获取参数，并通过project、flowid去重
       for(var i in checkboxList){
           var currentDom = checkboxList[i];
           if ( !isNaN(+i) && currentDom.checked) {
            var projectName = currentDom.getAttribute('projectName');
            var projectId = currentDom.getAttribute('projectId');
            var flowId = currentDom.getAttribute('flowId');
            var executionId = currentDom.value;
               var index =  window.reScheduleCheckedtList.findIndex(function(e){return e.projectName === projectName && e.flowId === flowId});
                if (index === -1) { // 新增
                    window.reScheduleCheckedtList.push({projectName, projectId, flowId, executionId});
                } else { // 覆盖
                    if (Number(window.reScheduleCheckedtList[index].executionId) < Number(executionId)) {
                        window.reScheduleCheckedtList[index] = {projectName, projectId, flowId, executionId};
                    }
                }
           }
       }
       if (window.reScheduleCheckedtList.length === 0) {
            messageBox.show(wtssI18n.view.returnFlowPro,'warning');
            return;
       }
       // 调用正在运行接口，校验工作流是否正在运行
       getRunningList();
    });
    // 批量导出
    $("#batch-download-btn").on("click", function() {
        const keyNameList = ['executionId', 'flowId', 'projectName', 'submitUser', 'startTime', 'endTime', 'runDate', 'difftime', 'status', 'flowType', 'comment'];
        const specialKey = ['status', 'flowType'];
        const historyList = historyListView.model.get("historyList");
        // 列标题，逗号隔开，每一个逗号就是隔开一个单元格
        let str = `执行 Id,工作流,项目,用户,开始时间,结束时间,跑批日期,执行时长,执行状态,工作流执行类型,备注\n`;
        // 增加\t为了不让表格显示科学计数法或者其他格式
        for(let i = 0 ; i < historyList.length ; i++ ){
            for(let j = 0 ; j < keyNameList.length ; j++ ){
                const key = keyNameList[j];
                if (!specialKey.includes(key)) {
                    str+=`${historyList[i][key] + '\t'},`;
                } else if (key === 'status') {
                    str+=`${statusStringMap[historyList[i][key]] + '\t'},`;
                } else {
                    str+=`${historyListView.getFlowTypeLabel(historyList[i][key]) + '\t'},`;
                }
            }
            str+='\n';
        }
        // encodeURIComponent解决中文乱码
        const uri = 'data:text/csv;charset=utf-8,\ufeff' + encodeURIComponent(str);
        // 通过创建a标签实现
        const link = document.createElement("a");
        link.href = uri;
        // 对下载的文件命名
        link.download =  "执行历史数据.csv";
        link.click();
    })
    // 全选
    $("#selectAllFlowBox").click(function(e){
        e.stopPropagation();
        var tagName = e.target.tagName;
        if (tagName !== 'INPUT') {
            return;
        }
        var checked = e.target.checked;
        var checkboxList = $("#executingJobs .reSchedule-checkbox");
        for(var i in checkboxList){
            var currentDom = checkboxList[i];
            if (!isNaN(+i) && (!checked ||(checked && currentDom.getAttribute('disabled') !== 'disabled'))) {
                currentDom.checked = checked;
            }
        }
        if (!checked) {
            window.reScheduleCheckedtList = [];
        }
    });
    $("#executionTimeHeader").click(function(e) {
        e.stopPropagation();
        var trList = Array.from($("#historyTbody").children()) ;
        historyListView.isAsc = historyListView.isAsc === undefined ? false : !historyListView.isAsc;
        if (!trList.length) {
            return;
        }
        trList.sort((a, b) => historyListView.isAsc ? handleExecutionTime(b.children[9].innerText) - handleExecutionTime(a.children[9].innerText): handleExecutionTime(a.children[9].innerText)- handleExecutionTime(b.children[9].innerText));
        var oldHistoryTbody = document.getElementById('historyTbody');
        var newHistoryTbody = document.createElement('tbody');
        newHistoryTbody.setAttribute('id', 'historyTbody');
        for (var i = 0; i < trList.length; ++i) {
            newHistoryTbody.appendChild(trList[i]);
        }
        oldHistoryTbody.parentNode.replaceChild(newHistoryTbody, oldHistoryTbody);
    });
});

var tableSorterView;

//用于保存浏览数据，切换页面也能返回之前的浏览进度。
var historyModel;
azkaban.HistoryModel = Backbone.Model.extend({

});
window.reScheduleCheckedtList = [];
//项目列表页面
var historyListView;
azkaban.HistoryListView = Backbone.View.extend({
    events: {
        "click #pageTable .projectPageSelection li": "handleChangePageSelection",
        "change #pageTable .pageSizeSelect": "handlePageSizeSelection",
        "click #pageTable .pageNumJump": "handlePageNumJump",
    },
    initialize: function (settings) {
        var advfilter = this.model.get('advfilter') ? this.model.get('advfilter') : false
        this.model.set("searchterm", $('#searchtextbox').val());
        this.model.bind('render', this.render, this);

        this.model.set('elDomId','pageTable');
        this.model.bind('change:view', this.handleChangeView, this);
        this.model.bind('change:page', this.handlePageChange, this);
        this.model.set({ page: 1, pageSize: 20, searchterm: '', advfilter: advfilter, preciseSearch: false });
        this.createResize();

        loadSubSystemData();
        loadBusPathData();
        var that = this
        setTimeout(function () {
            //获取批量路径
            // that.loadBusPathData();
            //获取子系统
            // loadSubSystemData();
            //获取部门
            that.loadDepartmentData()
        }, 300)
    },

    render: function () {
        console.log("render");
        window.reScheduleCheckedtList = [];
        // Render page selections
        // var historyTbody = $("#historyTbody");
        // historyTbody.empty();
        var oldHistoryTbody = document.getElementById('historyTbody');
        var childrenNum = oldHistoryTbody.children.length;
        var newHistoryTbody = document.createElement('tbody');
        newHistoryTbody.setAttribute('id', 'historyTbody');
        var historyList = this.model.get("historyList");
        for (var i = 0; i < historyList.length; ++i) {
            var row = document.createElement("tr");

            //多选框
            var tdCheckbox = document.createElement("td");
            var checkbox = document.createElement("input");
            $(checkbox).attr("type", "checkbox").attr("class", "reSchedule-checkbox").attr("projectName",  historyList[i].projectName).attr("projectId", historyList[i].projectId).attr("flowId", historyList[i].flowId).attr("disabled", ['FAILED','KILLED'].indexOf(historyList[i].status) === -1).attr("value", historyList[i].executionId);;
            tdCheckbox.appendChild(checkbox);
            row.appendChild(tdCheckbox);

            //组装数字行
            var tdNum = document.createElement("td");
            $(tdNum).text(i + 1);
            $(tdNum).attr("class", "tb-name");
            row.appendChild(tdNum);

            //组装行
            var tdJob = document.createElement("td");
            var jobIdA = document.createElement("a");
            $(jobIdA).attr("href", filterXSS("executor?execid=" + historyList[i].executionId)).attr('target', '_blank');
            $(jobIdA).text(historyList[i].executionId);
            tdJob.appendChild(jobIdA);
            row.appendChild(tdJob);

            //组装Flow行
            var tdFlow = document.createElement("td");
            var flowA = document.createElement("a");
            $(flowA).attr("href", filterXSS("/manager?project=" + historyList[i].projectName + "&flow=" + historyList[i].flowId));
            $(flowA).text(historyList[i].flowId);
            $(flowA).attr("style", "width: 350px; word-break:break-all;");
            tdFlow.appendChild(flowA);
            row.appendChild(tdFlow);

            //组装Project行
            var tdProject = document.createElement("td");
            var projectA = document.createElement("a");
            $(projectA).attr("href", filterXSS("/manager?project=" + historyList[i].projectName));
            $(projectA).text(historyList[i].projectName);
            $(projectA).attr("style", "width: 350px; word-break:break-all;");
            tdProject.appendChild(projectA);
            row.appendChild(tdProject);

            //组装用户行
            var tdUser = document.createElement("td");
            $(tdUser).text(historyList[i].submitUser);
            row.appendChild(tdUser);

            //组装开始时间行
            var tdStartTime = document.createElement("td");
            $(tdStartTime).text(historyList[i].startTime);
            row.appendChild(tdStartTime);

            //组装结束时间行
            var tdEndTime = document.createElement("td");
            $(tdEndTime).text(historyList[i].endTime);
            row.appendChild(tdEndTime);

            //组装跑批时间行
            var tdRunDate = document.createElement("td");
            $(tdRunDate).text(historyList[i].runDate);
            row.appendChild(tdRunDate);

            //组装执行时长行
            var tdDifftime = document.createElement("td");
            $(tdDifftime).text(historyList[i].difftime);
            row.appendChild(tdDifftime);

            //组装执行状态行
            var tdStatus = document.createElement("td");
            var status = document.createElement("div");
            $(status).addClass("status");
            $(status).addClass(historyList[i].status);
            //执行状态栏超时变色
            // if(historyList[i].status == "RUNNING" && parseInt(historyList[i].execTime) > parseInt(historyList[i].moyenne)){
            //   $(status).addClass("TIMEOUT");
            // }else{
            //   $(status).addClass(historyList[i].status);
            // }
            $(status).text(statusStringMap[historyList[i].status]);
            tdStatus.appendChild(status);
            row.appendChild(tdStatus);

            //组装 工作流执行类型
            var tdFlowType = document.createElement("td");
            var flowTypeLabel = this.getFlowTypeLabel(historyList[i].flowType);
            $(tdFlowType).text(flowTypeLabel);
            row.appendChild(tdFlowType);

            //备注
            var tdComment = document.createElement("td");
            $(tdComment).attr("style", "overflow: hidden;text-overflow: ellipsis;white-space: nowrap;max-width: 60px;");
            $(tdComment).attr("title", historyList[i].comment);
            $(tdComment).text(historyList[i].comment);
            row.appendChild(tdComment);

            newHistoryTbody.appendChild(row);
        }
        oldHistoryTbody.parentNode.replaceChild(newHistoryTbody, oldHistoryTbody);
        this.renderPagination();
        if (historyList && historyList.length) {
            $("#executingJobs").trigger("update");
            $("#executingJobs").trigger("sorton", "");
        }
    },
    getFlowTypeLabel: function(flowType) {
        switch (flowType) {
            case 0:
                return wtssI18n.view.singleExecution;
            case 2:
                return wtssI18n.view.historicalRerun;
            case 3:
                return wtssI18n.view.timedScheduling;
            case 4:
                return wtssI18n.view.cycleExecution;
            default:
                return wtssI18n.view.eventSchedule;
        }
    },
    ...commonPaginationFun(),
    handlePageChange: function (evt) {
        var start = this.model.get("page") - 1;
        var pageSize = this.model.get("pageSize");
        var requestURL = "/history";
        var searchText = this.model.get("searchterm");
        var advfilter = this.model.get("advfilter");
        var preciseSearch = this.model.get("preciseSearch");

        var that = this;
        var basicData = {
            "ajax": "feachAllHistoryPage",
            "page": start,
            "size": pageSize,
            "pageNum": this.model.get("page")
        }
        var requestData;
        if (advfilter) {
            requestData = this.model.get("filterParam");
            requestData.advfilter = advfilter
        } else if (preciseSearch) {
            requestData = this.model.get("filterParam");
            requestData.preciseSearch = preciseSearch
        } else {
            requestData = {
                "searchterm": searchText,
                "search": searchText ? "true" : "",
            };
        }

        Object.assign(requestData, basicData)
        var successHandler = function (data) {
            that.model.set({
                "historyList": data.historyList,
                "total": data.total
            });
            that.render();
            if (advfilter || preciseSearch) {
                $('#adv-filter').modal('hide');
            }
        };
        $.get(requestURL, requestData, successHandler, "json");
    },
    loadBusPathData: loadBusPathData,
    loadDepartmentData: loadDepartmentData
})

function checkEnglish (str) {
    if (str.length != 0) {
        var reg = /^[a-zA-Z0-9_]+$/;
        if (!reg.test(str)) {
            alert(wtssI18n.view.alphanumericUnderlining);
            return true;
        }
    }
}

function checkProject (str) {
    if (str.length != 0) {
        var reg = /^[a-zA-Z0-9_-]+$/;
        if (!reg.test(str)) {
            alert(wtssI18n.view.alphanumericUnderscoreBar);
            return true;
        }
    }
}

function checkExecId (str) {
    if (str.length != 0) {
        var reg = /^[0-9]+$/;
        if (!reg.test(str)) {
            alert(wtssI18n.view.alphanumeric);
            return true;
        }
    }
}