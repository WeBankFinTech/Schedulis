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

// 定时调度分页
$.namespace('azkaban');

$(function() {
    // 在切换选项卡之前创建模型
    scheduleModel = new azkaban.ScheduleModel();

    scheduleListView = new azkaban.ScheduleListView({
        el: $('#time-schedule-view'),
        model: scheduleModel
    });

    eventScheduleModel = new azkaban.EventScheduleModel();
    scheduleTabView = new azkaban.ScheduleTabView({ el: $('#header-tabs') });
    eventScheduleView = new azkaban.EventScheduleView({
        el: $('#event-schedule-view'),
        model: eventScheduleModel
    });
    // 定时调度批量告警页面
    batchSetSlaView = new azkaban.BatchSetSlaView({
        el: $('#time-schedule-view'),
        model: scheduleModel
    });

    // 循环调度tab页
    cycleScheduleModel = new azkaban.CycleScheduleModel();
    cycleScheduleView = new azkaban.CycleScheduleView({
        el: $('#cycle-schedule-view'),
        model: cycleScheduleModel
    });

    advFilterView = new azkaban.AdvFilterView({
        el: $('#adv-filter')
    })

    if (window.location.hash) { //浏览器输入对于的链接时跳转到对应的Tab页
        var hash = window.location.hash;
        if (hash == '#event-schedule') {
            $('#event-schedule-view').show()
            $('#search-form').hide();
            $('#cycle-search-form').hide();
            $('#event-search-form').show();
            $('#event-schedule-view-link').addClass('active');
            scheduleTabView.handleEventScheduleViewLinkClick();
        }  else if(hash === '#cycle-schedule') {
            $('#cycle-schedule-view').show()
            $('#search-form').hide();
            $('#event-search-form').hide();
            $('#cycle-search-form').show();
            $('#cycle-schedule-view-link').addClass('active');
            scheduleTabView.handleCycleScheduleViewLinkClick();
        } else {
            $('#time-schedule-view').show()
            $('#time-schedule-view-link').addClass('active');
            scheduleTabView.handleTimeScheduleViewLinkClick();
        }
    }



    $("#quick-serach-btn").click(function() {
        scheduleModel.set({
            "searchterm": $("#searchtextbox").val() ? filterXSS($("#searchtextbox").val()) : "",
            "isFilter": false
        });
        scheduleListView.model.trigger("change:view");
    });

    $('#searchtextbox').on('keyup', function(e) {
        if (e.keyCode == 13) {
            scheduleModel.set({
                "searchterm": $("#searchtextbox").val() ? filterXSS($("#searchtextbox").val()) : "",
                "isFilter": false
            });
            scheduleListView.model.trigger("change:view");
        }
    });

    $('#filter-search-btn').click(function() {
        $('#adv-filter').modal()
        advFilterView.render()
    })

    $("#event-quick-search-btn").click(function() {
        eventScheduleModel.set({
            "searchterm": $("#event-searchtextbox").val() ? filterXSS($("#event-searchtextbox").val()) : "",
        });
        scheduleModel.set({ "isFilter": false });
        eventScheduleView.model.trigger("change:view");
    });

    $('#event-searchtextbox').on('keyup', function(e) {
        if (e.keyCode == 13) {
            eventScheduleModel.set({
                "searchterm": $("#event-searchtextbox").val() ? filterXSS($("#event-searchtextbox").val()) : "",
            });
            scheduleModel.set({ "isFilter": false });
            eventScheduleView.model.trigger("change:view");
        }
    });
    $('#event-filter-search-btn').click(function() {
        $('#adv-filter').modal()
        advFilterView.render('event-schedule')
    })

    $("#cycle-quick-search-btn").click(function() {
        cycleScheduleModel.set({
            "searchterm": $("#cycle-searchtextbox").val() ? filterXSS($("#cycle-searchtextbox").val()) : "",
        });
        scheduleModel.set({ "isFilter": false });
        cycleScheduleView.model.trigger("change:view");
    });

    $('#cycle-searchtextbox').on('keyup', function(e) {
        if (e.keyCode == 13) {
            cycleScheduleModel.set({
                "searchterm": $("#cycle-searchtextbox").val() ? filterXSS($("#cycle-searchtextbox").val()) : "",
            });
            scheduleModel.set({ "isFilter": false });
            cycleScheduleView.model.trigger("change:view");
        }
    });
    $('#cycle-filter-search-btn').click(function() {
        $('#adv-filter').modal()
        advFilterView.render('cycle-schedule')
    })

     // 批量启用
     $("#batch-enable-btn").click(function() {
        batchOprOpenPrompt('schedule', 'enable');
    });
     // 批量禁用
     $("#batch-disable-btn").click(function() {
        batchOprOpenPrompt('schedule', 'disable');
        // hanleBatchOpr('schedule',,'disable');
    });
     // 批量下线
     $("#batch-delete-btn").click(function() {
        batchOprOpenPrompt('schedule', 'delete');
        // hanleBatchOpr('schedule',,'delete');
    });

    function batchDownloadList (type) {
        let scheduleList = [];
        let keyNameList =  [];
        let specialKey =  [];
        // 列标题，逗号隔开，每一个逗号就是隔开一个单元格
        let str = ''
        if (type ==='schedule') {
            scheduleList = scheduleListView.model.get("scheduleList");
            keyNameList = ['scheduleId', 'flowName', 'projectName', 'submitUser', 'lastModifyConfiguration', 'nextExecTime', 'cronExpression', 'activeFlag', 'validFlow'];
            specialKey = ['lastModifyConfiguration', 'nextExecTime', 'cronExpression', 'activeFlag', 'validFlow'];
            str = `Id,工作流,项目,提交人,调度配置修改时间,下一次执行时间,Cron表达式,调度有效,工作流有效,是否设置告警,备注\n`;
        } else {
            scheduleList = eventScheduleView.model.get("eventScheduleList");
            keyNameList = ['scheduleId', 'flowName', 'projectName', 'submitUser', 'lastModifyTime', 'topic', 'msgName', 'activeFlag', 'validFlow'];
            specialKey = ['lastModifyTime', 'activeFlag', 'validFlow'];
            str = `Id,工作流,项目,提交人,调度配置修改时间,信号主题 (Message Topic),信号名称(Message Name),调度有效,工作流有效,是否设置告警,备注\n`;
        }
        // 增加\t为了不让表格显示科学计数法或者其他格式
        for(let i = 0 ; i < scheduleList.length ; i++ ){
            for(let j = 0 ; j < keyNameList.length ; j++ ){
                const key = keyNameList[j];
                if (!specialKey.includes(key)) {
                    str+=`${scheduleList[i][key] + '\t'},`;
                } else if (['lastModifyTime', 'nextExecTime', 'lastModifyConfiguration'].includes(key)) {
                    str+=`${scheduleList[i][key] > 0 ? getProjectModifyDateFormat(new Date(scheduleList[i][key])) + '\t': '\t'},`;
                } else if (key === 'cronExpression'){
                    str+=`${scheduleList[i].cronExpression ? scheduleList[i].cronExpression + '\t' : wtssI18n.view.notApplicable + '\t'},`;
                }else if (['activeFlag', 'validFlow'].includes(key)){
                    str+=`${!!scheduleList[i][key] + '\t'},`;
                }
            }
            const isSla = scheduleList[i].slaOptions && scheduleList[i].slaOptions.length != 0 ? true : false;
            str+=`${(isSla) + '\t'},`;
            str+=`${(scheduleList[i].comment || "") + '\t'},`;
            str+='\n';
        }
        // encodeURIComponent解决中文乱码
        const uri = 'data:text/csv;charset=utf-8,\ufeff' + encodeURIComponent(str);
        // 通过创建a标签实现
        const link = document.createElement("a");
        link.href = uri;
        // 对下载的文件命名
        link.download =  `${type ==='schedule' ? '定时调度' : '信号调度'}数据.csv`;
        link.click();
    }
      // 批量导出
    $("#batch-schedule-download").click(function() {
        batchDownloadList('schedule');
    });
    // 下载调度所属项目
    $("#batch-schedule-project").click(function() {
        batchDownloadProject('schedule');
    });
    function showSettingAlerter () {
        $('#batch-settinh-alerter').addClass('active');
        $('#batch-settinh-alerter-box').show();

        $('#batch-setting-alert-config').removeClass('active');
        $('#batch-setting-alert-config-box').hide();
    }
    $('#batch-settinh-alerter').click(function() {
        showSettingAlerter();
    })
    function showAlertConfig() {
        $('#batch-settinh-alerter').removeClass('active');
        $('#batch-settinh-alerter-box').hide();
        $('#batch-setting-alert-config').addClass('active');
        $('#batch-setting-alert-config-box').show();
    }
    $('#batch-setting-alert-config').click(function() {
        showAlertConfig();
    })
    function settingBatchAlertTab(type) {
        // 有选中工作流
        var flowList = [];
        var executIdList = [];
        if ( type === 'schedule') {
            flowList = scheduleCheckedFlowList;
            executIdList = scheduleCheckedIdtList
        } else {
            flowList = eventScheduleCheckedFlowList;
            executIdList = eventScheduleCheckedIdtList
        }
        $('#batchUpdateSlaEmails').val('');
        if (flowList.length > 0) {
            showSettingAlerter();
            var alertFlow = '';
            flowList.forEach(function(item, index){
                alertFlow += executIdList[index] + '-' + item + ', ';
            })
            $('#batchUpdateSlaFlows').text(alertFlow);
        } else{
            showAlertConfig();
            $('#batchUpdateSlaFlows').text(wtssI18n.view.selectAlarmerPro);
        }
    }
     // 定时调度设置批量告警对话框
    $("#batch-setSla-btn").click(function() {
        settingBatchAlertTab('schedule');
        var ids = scheduleListView.model.get('scheduleList');
        if (ids) {
            $('#batch-set-sla-btn').attr('scheduleType', 'schedule')
            $('#batch-sla-options').modal();
            $('#batch-aletType input').prop('checked',true);
        } else {
            alert(wtssI18n.view.scheduleIsNotExist);
        }

    });
    // 信号调度批量告警页面
    eventBatchSetSlaView = new azkaban.BatchSetSlaView({
        el: $('#event-schedule-view'),
        model: eventScheduleModel
    });

    // 定时调度设置批量告警对话框
    $("#event-batch-setSla-btn").click(function() {
        // var ids = eventScheduleView.model.get('allEventScheduleIdList');
        settingBatchAlertTab('event');
        var ids = eventScheduleView.model.get('eventScheduleList');
        if (ids) {
            $('#batch-set-sla-btn').attr('scheduleType', 'eventSchedule')
            $('#batch-sla-options').modal();
            $('#batch-aletType input').prop('checked',true);
        } else {
            alert(wtssI18n.view.scheduleIsNotExist);
        }

    });
     // 批量启用
     $("#event-batch-enable-btn").click(function() {
        batchOprOpenPrompt('event', 'enable');
    });
     // 批量禁用
     $("#event-batch-disable-btn").click(function() {
        batchOprOpenPrompt('event', 'disable');
    });
     // 批量下线
     $("#event-batch-delete-btn").click(function() {
        batchOprOpenPrompt('event', 'delete');

    });
    // 批量导出
    $("#event-batch-schedule-download").click(function() {
        batchDownloadList("event");
    });
     // 下载调度所属项目
     $("#event-batch-schedule-project").click(function() {
        batchDownloadProject('event');
    });
    // 设置批量告警
    $("#batch-set-sla-btn").click(function() {
        if ($('#batch-settinh-alerter').attr('class') === 'active') {
            const type = $("#batch-set-sla-btn").attr("scheduletype");
            if ((type === "schedule" && scheduleCheckedFlowList.length === 0) || (type === "eventSchedule" && eventScheduleCheckedFlowList.length === 0)) {
                messageBox.show(wtssI18n.view.selectAlarmerPro, 'warning');
                return;
            }
            if (!$('#batchUpdateSlaEmails').val().trim()) {
                messageBox.show(wtssI18n.view.fillAlarmerPro, 'warning');
                return;
            }
            batchSetSlaView.handleBatchUpdateAlert();
        } else {
            $('#batch-set-sla-valid-modal').show();
        }
    });
    $("#batch-set-sla-valid-btn").click(function() {
        batchSetSlaView.handleBatchSetSla();
    });

    $("#batch-set-sla-cancel-btn").click(function() {
        $('#batch-set-sla-valid-modal').hide();
    });

    $("#batch-set-sla-close-btn").click(function() {
        $('#batch-set-sla-valid-modal').hide();
    });

    // 工作流超时告警规则设置-新增一条
    $("#batch-add-btn").click(function() {
        batchSetSlaView.handleBatchAddRow();
    });

    // 工作流事件告警规则设置-新增一条
    $("#batch-finish-add-btn").click(function() {
        batchSetSlaView.handleBatchFinishAddRow();
    });

    // 工作流超时告警规则设置-删除一条
    $("#batchFlowRulesTbl").on('click', '.btn-danger-type1', function() {
        var row = this.parentElement.parentElement.parentElement;
        $(row).remove();
    })

    // 工作流事件告警规则设置-删除一条
    $("#batchFinishRulesTbl").on('click', '.btn-danger-type2', function() {
        var row = this.parentElement.parentElement.parentElement;
        $(row).remove();
    })


    scheduleShowArgsView = new azkaban.ScheduleShowArgsView({
        el: $('#time-schedule-view'),
        model: scheduleModel
    });

    cycleScheduleShowArgsView = new azkaban.CycleScheduleShowArgsView({
        el: $('#cycle-schedule-view'),
        model: cycleScheduleModel
    });

    var urlSearch = window.location.search;
    if (urlSearch.indexOf("search") != -1) {
        scheduleModel.set({ "search": true });
    }

    // var hash = window.location.hash;
    // if ("#page" == hash.substring(0, "#page".length)) {
    //     var arr = hash.split("#");
    //     var page;
    //     //var pageSize;
    //     if (true == scheduleModel.get("search") && 1 == scheduleModel.get("page")) {
    //         page = 1;
    //     } else {
    //         page = arr[1].substring("#page".length - 1, arr[1].length);
    //     }
    //     var pageSize = arr[2].substring("#pageSize".length - 1, arr[2].length);

    //     $("#pageSizeSelect").val(pageSize);

    //     console.log("page " + page);
    //     scheduleModel.set({
    //         "page": parseInt(page),
    //         "pageSize": parseInt(pageSize),
    //     });
    // } else {
    //     scheduleModel.set({ "page": 1 });
    // }

    // scheduleModel.trigger("change:view");

     // 全选
     $("#selectAllFlowBox").click(function(e){
        e.stopPropagation();
        var tagName = e.target.tagName;
        if (tagName !== 'INPUT') {
            return;
        }
        var checked = e.target.checked;
        var checkboxList = $("#schedules-tbody .schedule-checkbox");
        for(var i in checkboxList){
            var currentDom = checkboxList[i];
            if (!isNaN(+i)) {
                currentDom.checked = checked;
                if (checked) {
                    var scheduleId = currentDom.value;
                    var projectName = currentDom.getAttribute('projectName');
                    var flowName = currentDom.getAttribute('flowName');
                    scheduleCheckedProjectList.push(projectName);
                    scheduleCheckedFlowList.push(flowName);
                    scheduleCheckedIdtList.push(scheduleId);
                }
            }
        }
        if (!checked) {
            scheduleCheckedProjectList = [];
            scheduleCheckedFlowList = [];
            scheduleCheckedIdtList = [];
        }
    });

     // 全选
     $("#EventSelectAllFlowBox").click(function(e){
        e.stopPropagation();
        var tagName = e.target.tagName;
        if (tagName !== 'INPUT') {
            return;
        }
        var checked = e.target.checked;
        var checkboxList = $("#event-schedules-tbody .event-schedule-checkbox");
        for(var i in checkboxList){
            var currentDom = checkboxList[i];
            if (!isNaN(+i)) {
                currentDom.checked = checked;
                if (checked) {
                    var scheduleId = currentDom.value;
                    var projectName = currentDom.getAttribute('projectName');
                    var flowName = currentDom.getAttribute('flowName');
                    eventScheduleCheckedProjectList.push(projectName);
                    eventScheduleCheckedFlowList.push(flowName);
                    eventScheduleCheckedIdtList.push(scheduleId);
                }
            }
        }
        if (!checked) {
            eventScheduleCheckedProjectList = [];
            eventScheduleCheckedFlowList = [];
            eventScheduleCheckedIdtList = [];
        }
    })


});

//显示参数按钮
var scheduleShowArgsView;
azkaban.ScheduleShowArgsView = Backbone.View.extend({
    events: {
        "click .btn-info": "handleShowArgs"
    },

    initialize: function(settings) {},

    handleShowArgs: function(evt) {
        console.log("Show Args");
        $('#executionOptions-pre').text("");
        $('#executionOptions-modal').modal();
        var index = parseInt(evt.currentTarget.name);
        $('#executionOptions-pre').text(JSON.stringify(scheduleModel.get("scheduleList")[index].executionOptions, null, 4));
    },

    render: function() {}
});

//显示参数按钮
var cycleScheduleShowArgsView;
azkaban.CycleScheduleShowArgsView = Backbone.View.extend({
    events: {
        "click .btn-info": "handleShowCycleScheduleArgs"
    },

    initialize: function(settings) {},

    handleShowCycleScheduleArgs: function(evt) {
        var index = parseInt(evt.currentTarget.name);
        $('#executionOptions-pre').text("");
        $('#executionOptions-modal').modal();
        $('#executionOptions-pre').text(JSON.stringify(cycleScheduleModel.get("executionCycleList")[index].executionOptions, null, 4));
    },

    render: function() {}
});

var batchSetSlaView;
azkaban.BatchSetSlaView = Backbone.View.extend({
    events: {},

    //关闭SLA配置页面时的操作
    handleSlaCancel: function() {
        console.log("Clicked cancel button");
        var scheduleURL = "/schedule";
        $('#batchSlaEmails').val('');
        $("#batch-sla-via-department").prop('checked', false)
            //清空SLA定时告警配置选项
        if (document.getElementById("batchFlowRulesTbl")) {
            var tFlowRules = document.getElementById("batchFlowRulesTbl").tBodies[0];
            var rows = tFlowRules.rows;
            var rowLength = rows.length
            for (var i = 0; i < rowLength - 1; i++) {
                tFlowRules.deleteRow(0);
            }
        }

        if (document.getElementById("batchFinishRulesTbl")) {
            //清空成功失败告警配置选项
            var tFinishRules = document.getElementById("batchFinishRulesTbl").tBodies[0];
            var fRows = tFinishRules.rows;
            var fRowLength = fRows.length
            for (var i = 0; i < fRowLength - 1; i++) {
                tFinishRules.deleteRow(0);
            }
        }
        $('#batch-add-btn').attr('disabled', false);
    },

    initialize: function(settings) {
        if (this.$el[0].id === 'time-schedule-view') {
            // this.getCurrentScheduleAllFlowSetSla();
            $('#batch-sla-options').on('hidden.bs.modal', function() {
                batchSetSlaView.handleSlaCancel();
            });
        }
    },

    // getCurrentScheduleAllFlowSetSla: function(isEventSchedule) {
    //     var requestURL = isEventSchedule ? "/eventschedule?ajax=fetchAllScheduleFlowInfo" : "/schedule?ajax=fetchAllScheduleFlowInfo";

    //     var model = this.model;
    //     $.ajax({
    //         url: requestURL,
    //         type: "get",
    //         async: false,
    //         dataType: "json",
    //         success: function(data) {
    //             if (isEventSchedule) {
    //                 model.set({
    //                     "eventCurrentFlowNameList": data.scheduleFlowNameList,
    //                     "eventAllScheduleIdList": data.scheduleIdList
    //                 });
    //             } else {
    //                 model.set({
    //                     "currentFlowNameList": data.scheduleFlowNameList,
    //                     "allScheduleIdList": data.scheduleIdList
    //                 });
    //             }
    //         }
    //     });
    // },
    handleBatchUpdateAlert: function () {
        var scheduleType = $('#batch-set-sla-btn').attr('scheduleType')
        var scheduleURL = scheduleType === 'schedule' ? "/schedule?ajax=batchSetSlaEmail" : "/eventschedule?ajax=batchSetSlaEmail"
        var batchSlaData = {
            scheduleInfos: {
                scheduleIds: scheduleType === 'schedule' ? scheduleCheckedIdtList : eventScheduleCheckedIdtList,
                slaEmail: $('#batchUpdateSlaEmails').val().trim()
            }
        }
        function successHandler(data) {
            if (data.error) {
                messageBox.show(data.error, 'danger');
                return;
            }
            var successedList = data.successedList;
            var failedList = data.failedList;
            var timeout = 0;
            if (failedList && failedList.length) {
                timeout = 2000;
                var message = "";
                for (var i =0;i < failedList.length; i++) {
                    message += failedList[i].scheduleId + ' ： ' + failedList[i].errorInfo + ', ';
                }
                messageBox.show(message, 'danger');
            }
            if (successedList && successedList.length) {
                var successMsg = "";
                const flowIdList = scheduleType === 'schedule' ? scheduleCheckedIdtList : eventScheduleCheckedIdtList
                const flowList = scheduleType === 'schedule' ? scheduleCheckedFlowList : eventScheduleCheckedFlowList;
                if (successedList.length === flowList.length) {
                    successMsg = wtssI18n.view.modifiedSuccessfully;
                } else {
                    var successArr = [];
                    for (var i =0;i < successedList.length; i++) {
                        var index = flowIdList.indexOf(successedList[i] + '');
                        successArr.push(flowList[index])
                    }
                    successMsg = successArr.toString() + wtssI18n.view.modifiedSuccessfully;
                }
                setTimeout(() => {
                    messageBox.show(successMsg);
                    $('#batch-sla-options').modal('hide');
                }, timeout);
                if (scheduleType === 'schedule') {
                    scheduleCheckedProjectList = [];
                    scheduleCheckedFlowList = [];
                    scheduleCheckedIdtList = [];
                    scheduleListView.handlePageChange();
                } else {
                    eventScheduleCheckedProjectList = [];
                    eventScheduleCheckedFlowList = [];
                    eventScheduleCheckedIdtList = [];
                    eventScheduleView.handlePageChange();
                }
            }
        }
        $.ajax({
            url: scheduleURL,
            type: "post",
            async: true,
            data: JSON.stringify(batchSlaData) ,
            dataType: "json",
            headers: {'Content-Type': 'application/json;'},
            error: function (error) {
                var responseText = error.responseText ? JSON.parse(error.responseText) : {};
                messageBox.show(responseText.message, 'danger');
                console.log('error', error)
            },
            success: successHandler
        });
        // $.post(scheduleURL, batchSlaData, successHandler, "json");
    },
    handleBatchSetSla: function(evt) {
        var scheduleType = $('#batch-set-sla-btn').attr('scheduleType')
        var scheduleURL = scheduleType === 'schedule' ? "/schedule" : "/eventschedule";
        // var allScheduleIdList = scheduleType === 'schedule' ? this.model.get("allScheduleIdList").join(',') : eventScheduleView.model.get("allEventScheduleIdList").join(',');
        var departmentSlaInform;
        if ($("#batch-sla-via-department").is(":checked")) {
            console.log("batch-sla-via-department set")
            departmentSlaInform = "true";
        } else {
            console.log("batch-sla-via-department unset")
            departmentSlaInform = "false";
        }

        var batchSlaEmails = $('#batchSlaEmails').val();
        var aletTypeChecked =$('#batch-aletType :checked');
        var aletTypeArr = [];
        for (var i=0; i< aletTypeChecked.length; i++) {
            aletTypeArr.push(aletTypeChecked[i].value)
        }
        var fistScheduleId = scheduleTabView.isScheduleTab ? scheduleListView.model.get('allScheduleIdList') : eventScheduleView.model.get('allScheduleIdList');
        var allScheduleIdList =[].concat(fistScheduleId);
        //工作流超时告警规则设置
        var settings = {};
        var timeoutScheduleIdList = {};
        var eventScheduleIdList = {};
        var tFlowRules = document.getElementById("batchFlowRulesTbl").tBodies[0];
        var tFinishRules = document.getElementById("batchFinishRulesTbl").tBodies[0];
        if (tFlowRules.rows.length === 1 && tFinishRules.rows.length === 1) {
            messageBox.show(wtssI18n.view.setAlarmPro, 'warning');
            return;
        }

        for (var g = 0; g < tFlowRules.rows.length - 1; g++) {
            var rFlowRule = tFlowRules.rows[g];
            var id = rFlowRule.cells[0].firstChild.value;
            if (id && !allScheduleIdList.includes(id) && id !== 'all') {
                allScheduleIdList.push(id);
            }
        }

        for (var h = 0; h < tFinishRules.rows.length - 1; h++) {
            var tFinishRule = tFinishRules.rows[h];
            var id = tFinishRule.cells[0].firstChild.value;
            if (id && !allScheduleIdList.includes(id) && id !== 'all') {
                allScheduleIdList.push(id);
            }
        }

        for (var row = 0; row < tFlowRules.rows.length - 1; row++) {
            var rFlowRule = tFlowRules.rows[row];
            var id = rFlowRule.cells[0].firstChild.value;
            id = id === 'all' ? 0 : allScheduleIdList.indexOf(id) + 1;
            // 没有选择工作流
            if (id === -1 ) {
                messageBox.show(wtssI18n.view.timeoutAlarmPro, 'warning');
                return;
            }
            var rule = rFlowRule.cells[1].firstChild.value;
            var duration = rFlowRule.cells[2].firstChild.firstChild.value;
            var absTime = rFlowRule.cells[3].firstChild.firstChild.value;
            var level = rFlowRule.cells[4].firstChild.value;
            var email = rFlowRule.cells[5].firstChild.checked;
            var kill = rFlowRule.cells[6].firstChild.checked;
            var alarmFrequency = rFlowRule.cells[7].firstChild.value;
            settings[row] = id + "," + rule + "," + duration + "," + absTime + "," + level + "," + email + "," + kill + "," + alarmFrequency;
            // 设置超时告警选中的scheduleId
            var selectionFlow = $(rFlowRule.children[0]).find('.selection .select2-selection__rendered')
            timeoutScheduleIdList[row] = selectionFlow[0].innerText;

            if (!duration && !absTime) {
                alert(timeoutScheduleIdList[row] + ": " + wtssI18n.view.timeoutAlarmTime);
                return false;
            }

            if (email == false && kill == false) {
                alert(timeoutScheduleIdList[row] + ": " + wtssI18n.view.timeoutAlarmRuleLessOne);
                return false;
            }

        }
        //工作流事件告警规则设置
        var finishSettings = {};
        // var tFinishRules = document.getElementById("batchFinishRulesTbl").tBodies[0];
        for (var row = 0; row < tFinishRules.rows.length - 1; row++) {
            var tFinishRule = tFinishRules.rows[row];
            var id = tFinishRule.cells[0].firstChild.value;
            id = id === 'all' ? 0 : allScheduleIdList.indexOf(id) + 1;
            // 没有选择工作流
            if (id === -1 ) {
                messageBox.show(wtssI18n.view.eventAlarmPro, 'warning');
                return;
            }
            var rule = tFinishRule.cells[1].firstChild.value;
            var level = tFinishRule.cells[2].firstChild.value;
            finishSettings[row] = id + "," + rule + "," + level;
            // 设置事件告警选中的scheduleId
            eventScheduleIdList[row] = tFinishRule.innerText.replace(/[\r\n\t]/g, "").replace("Delete", "");
        }
        //检查是否有重复的规则
        if (this.checkSlaRepeatRule(settings)) {
            alert(wtssI18n.view.timeoutAlarmFormat);
            return false;
        }
        //检查是否有重复的规则
        if (this.checkSlaRepeatRule(settings)) {
            alert(wtssI18n.view.timeoutAlarmFormat);
            return false;
        }

        //检查是否有重复的规则
        if (this.checkFinishRepeatRule(finishSettings)) {
            alert(wtssI18n.view.eventAlarmFormat);
            return false;
        }

        var batchSlaData = {
            timeoutScheduleIdList: timeoutScheduleIdList,
            eventScheduleIdList: eventScheduleIdList,
            allScheduleIdList: allScheduleIdList.toString(),
            ajax: "batchSetSla",
            batchSlaEmails: batchSlaEmails,
            alerterWay: aletTypeArr.toString(),
            departmentSlaInform: departmentSlaInform,
            settings: settings,
            finishSettings: finishSettings,
        };

        var successHandler = function(data) {
            if (data.error) {
                alert(data.error);
            } else {
                tFlowRules.length = 0;
                // 隐藏告警设置对话框, 触发变更
                $('#batch-sla-options').modal("hide");
                $('#batch-set-sla-valid-modal').hide();
                if (scheduleType === 'schedule') {
                    scheduleListView.handlePageChange();
                } else {
                    eventScheduleView.handlePageChange();
                }

            }
        };
        $.post(scheduleURL, batchSlaData, successHandler, "json");
    },

    checkSlaRepeatRule: function(data) {
        var new_arr = [];
        var oldlength = 0;
        for (var i in data) {
            oldlength++;
            var items = data[i].substring(0, find(data[i], ",", 1));;
            //判断元素是否存在于new_arr中，如果不存在则插入到new_arr的最后
            if ($.inArray(items, new_arr) == -1) {
                new_arr.push(items);
            }
        }
        if (new_arr.length < oldlength) {
            return true;
        }
    },

    checkFinishRepeatRule: function(data) {
        var new_arr = [];
        var oldlength = 0;
        for (var i in data) {
            oldlength++;
            var items = data[i];
            //判断元素是否存在于new_arr中，如果不存在则插入到new_arr的最后
            if ($.inArray(items, new_arr) == -1) {
                new_arr.push(items);
            }
        }
        if (new_arr.length < oldlength) {
            return true;
        }
    },
    loadAlertFlowSelect:  function(id) {
        var urls = {
            'time-schedule': '/schedule',
            'event-schedule': '/eventschedule',
            'cycle-schedule': '/cycle',
        }
        var ajaxs = {
            'time-schedule': 'ajaxFetchAllSchedules',
            'event-schedule': 'fetchAllEventSchedules',
            'cycle-schedule': 'ajaxFetchAllSchedules',
        }
        $("#" + id).select2({
            placeholder: wtssI18n.view.schduleKeywordsSearch,
            multiple: false,
            width: 'resolve',
            //language: "zh-CN",
            tags: false, //允许手动添加
            allowClear: false, //允许清空
            escapeMarkup: function(markup) { return markup; }, //自定义格式化防止XSS注入
            minimumInputLengt: 1, //最少输入多少字符后开始查询
            formatResult: function formatRepo(repo) { return repo.id; }, //函数用来渲染结果
            formatSelection: function formatRepoSelection(repo) { return repo.text; }, //函数用于呈现当前的选择
            ajax: {
                type: 'GET',
                url: urls[scheduleTabView.activeTab] || '/schedule',
                dataType: 'json',
                delay: 50,
                data: function(params) {
                    var searchterm = params.term ? params.term.trim() : '';
                    var query = {
                        ajax: ajaxs[scheduleTabView.activeTab] || 'ajaxFetchAllSchedules',
                        page: params.page || 1,
                        size: 20,
                        pageNum: 1
                    }
                    if (searchterm) {
                        query.searchterm = searchterm;
                        query.search = true;
                    }
                    return query;
                },
                processResults: function(data, params) {
                    var dataKeys = {
                        'time-schedule': 'schedules',
                        'event-schedule': 'eventSchedules',
                        'cycle-schedule': 'cycle',
                    }
                    params.page = params.page || 1;
                    var options = [];
                    // params.page === 1 ? [{
                    //     id: 'all',
                    //     text: 'all#All_Flow'
                    // }] : [];
                    var result = data[dataKeys[scheduleTabView.activeTab] || 'schedules']
                    result.forEach(function (item) {
                        options.push({
                            id: item.scheduleId,
                            text: item.scheduleId + '#'+ item.flowName
                        })
                    })

                    return {
                        results: options,
                        pagination: {
                            more: (params.page * 20) < data.total
                        }
                    }
                },
                cache: true
            },
            language: 'zh-CN'
        })
    },

    // 工作流超时告警规则设置-新增一行
    handleBatchAddRow: function(evt) {
        var scheduletype = $("#batch-set-sla-btn").attr('scheduletype');
        var flowNameList = scheduletype === 'schedule' ? this.model.get("currentFlowNameList") : this.model.get("eventCurrentFlowNameList");
        var ruleBoxOptions = ["SUCCESS", "FINISH"];

        var tFlowRules = document.getElementById("batchFlowRulesTbl").tBodies[0];
        var rFlowRule = tFlowRules.insertRow(tFlowRules.rows.length - 1);
        var retryTr = rFlowRule.rowIndex;
        // if (retryTr == flowNameList.length) {
        //     $('#batch-add-btn').attr('disabled', 'disabled');
        // }

        //设置工作流
        var cId = rFlowRule.insertCell(-1);
        $(cId).attr("style", "width:40%;");
        // var idSelect = "<select class='schedule-select2-search' id='schedule-outtiome-select" + retryTr + "' style='width:100%'></select>"
        // for (var i = 0; i < flowNameList.length; i++) {
        //     idSelect += "<option value=\"" + i + "\" title=\"" + flowNameList[i] + "\">" + flowNameList[i] + "</option>"
        // }

        // idSelect += "</select>"
        // idSelect = filterXSS(idSelect, { 'whiteList': { 'select': ['class', 'style'], 'option': ['value', 'title'] } })
        var idSelect = document.createElement("select");
        idSelect.setAttribute("class", "schedule-select2-search form-control");
        idSelect.setAttribute("id", "schedule-outtiome-select" + retryTr);
        cId.appendChild(idSelect);
        // cId.innerHTML = idSelect;
        // $('.schedule-select2-search').select2();

        //设置告警规则
        var cRule = rFlowRule.insertCell(-1);
        $(cRule).attr("style", "width:10%; min-width: 135px;");
        var ruleSelect = document.createElement("select");
        ruleSelect.setAttribute("class", "form-control");
        for (var i in ruleBoxOptions) {
            ruleSelect.options[i] = new Option(ruleBoxOptions[i], ruleBoxOptions[i]);
        }
        cRule.appendChild(ruleSelect);
        //设置超时时间
        var cDuration = rFlowRule.insertCell(-1);
        $(cDuration).attr("style", "width:10%; min-width: 85px;");
        var duration = document.createElement("input");
        duration.type = "text";
        duration.setAttribute("class", "durationpick form-control");
        var durationDiv = document.createElement("div");
        durationDiv.setAttribute("class", "position-relative");
        durationDiv.appendChild(duration);
        cDuration.appendChild(durationDiv);

        //设置超时时间点
        var cAbsTime = rFlowRule.insertCell(-1);
        $(cAbsTime).attr("style", "width:10%; min-width: 85px;");
        var absTime = document.createElement("input");
        absTime.type = "text";
        absTime.setAttribute("class", "durationpick form-control");
        var absTimeDiv = document.createElement("div");
        absTimeDiv.setAttribute("class", "position-relative");
        absTimeDiv.appendChild(absTime);
        cAbsTime.appendChild(absTimeDiv);

        //设置告警级别
        var cLevel = rFlowRule.insertCell(-1);
        $(cLevel).attr("style", "width:10%; min-width: 135px;");
        var levelSelect = document.createElement("select");
        levelSelect.setAttribute("class", "form-control");
        $(levelSelect).append("<option value='INFO'>INFO</option>");
        $(levelSelect).append("<option value='WARNING'>WARNING</option>");
        $(levelSelect).append("<option value='MINOR'>MINOR</option>");
        $(levelSelect).append("<option value='MAJOR'>MAJOR</option>");
        $(levelSelect).append("<option value='CRITICAL'>CRITICAL</option>");
        $(levelSelect).append("<option value='CLEAR'>CLEAR</option>");
        cLevel.appendChild(levelSelect);
        //设置发送邮件
        var cEmail = rFlowRule.insertCell(-1);
        var emailCheck = document.createElement("input");
        emailCheck.type = "checkbox";
        cEmail.appendChild(emailCheck);
        //设置终止工作流/任务
        var cKill = rFlowRule.insertCell(-1);
        var killCheck = document.createElement("input");
        killCheck.type = "checkbox";
        cKill.appendChild(killCheck);

        $('.durationpick').datetimepicker({
            format: 'HH:mm'
        });

        // 设置告警频率
        var cAlarmFrequency = rFlowRule.insertCell(-1);
        $(cAlarmFrequency).attr("style", "width:15%; min-width: 165px;");
        var alarmFrequencySelect = document.createElement("select")
        alarmFrequencySelect.setAttribute("class", "form-control")
        $(alarmFrequencySelect).append(`<option value=''>${wtssI18n.view.alarmFrequencySelect}</option>`);
        $(alarmFrequencySelect).append(`<option value='dayOnce'>${wtssI18n.view.dayOnce}</option>`);
        $(alarmFrequencySelect).append(`<option value='thirtyMinuteOnce'>${wtssI18n.view.thirtyMinuteOnce}</option>`);
        $(alarmFrequencySelect).append(`<option value='threeHourOnce'>${wtssI18n.view.threeHourOnce}</option>`);
        cAlarmFrequency.appendChild(alarmFrequencySelect);

        //删除按钮
        var cDelete = rFlowRule.insertCell(-1);
        var remove = document.createElement("div");
        $(remove).addClass("center-block").addClass('remove-timeout-btn');
        var removeBtn = document.createElement("button");
        $(removeBtn).attr('type', 'button');
        $(removeBtn).addClass('btn').addClass('btn-sm').addClass('btn-danger-type1');
        $(removeBtn).text('Delete');
        $(remove).append(removeBtn);
        cDelete.appendChild(remove);
        batchSetSlaView.loadAlertFlowSelect("schedule-outtiome-select" + retryTr);

        return rFlowRule;
    },


    //工作流事件告警规则设置-新增一行
    handleBatchFinishAddRow: function(evt) {
        var scheduletype = $("#batch-set-sla-btn").attr('scheduletype');
        var flowNameList = scheduletype === "schedule" ? this.model.get("currentFlowNameList") : this.model.get("eventCurrentFlowNameList");
        var finshRuleBoxOptions = ["FAILURE EMAILS", "SUCCESS EMAILS", "FINISH EMAILS"];

        var ruleTr = $("#batchFinishRulesTbl tr").length - 1;

        // var jslength = 0;

        // for (var i = 0; i < flowNameList.length; i++) {
        //     jslength++;
        // }

        // if (jslength * finshRuleBoxOptions.length < ruleTr) {
        //     alert(wtssI18n.view.alarmRulesFormat);
        //     return;
        // }

        var tFlowRules = document.getElementById("batchFinishRulesTbl").tBodies[0];
        var rFlowRule = tFlowRules.insertRow(tFlowRules.rows.length - 1);

        //alert($("#FinishRulesTbl tr").length);

        //设置 flow
        var cId = rFlowRule.insertCell(-1);
        cId.style.width = "450px"
        // var idSelect = "<select class='schedule-select2-search' id='schedule-finish-select" + ruleTr + "' style='width:100%'></select>"
        // for (var i = 0; i < flowNameList.length; i++) {
        //     idSelect += "<option value=\"" + i + "\" title=\"" + flowNameList[i] + "\">" + flowNameList[i] + "</option>"
        // }
        // idSelect += "</select>"
        // idSelect = filterXSS(idSelect, { 'whiteList': { 'select': ['class', 'style'], 'option': ['value', 'title'] } })
        // cId.innerHTML = idSelect;
        // $('.schedule-select2-search').select2();
        var idSelect = document.createElement("select");
        idSelect.setAttribute("class", "schedule-select2-search");
        idSelect.setAttribute("style", "width:450px");
        idSelect.setAttribute("id", "schedule-finish-select" + ruleTr);
        cId.appendChild(idSelect);
        //设置规则选项
        var cRule = rFlowRule.insertCell(-1);
        var ruleSelect = document.createElement("select");
        ruleSelect.setAttribute("class", "form-control");
        for (var i in finshRuleBoxOptions) {
            ruleSelect.options[i] = new Option(finshRuleBoxOptions[i], finshRuleBoxOptions[i]);
        }
        cRule.appendChild(ruleSelect);

        //设置告警级别
        var cLevel = rFlowRule.insertCell(-1);
        var levelSelect = document.createElement("select");
        levelSelect.setAttribute("class", "form-control");
        $(levelSelect).append("<option value='INFO'>INFO</option>");
        $(levelSelect).append("<option value='WARNING'>WARNING</option>");
        $(levelSelect).append("<option value='MINOR'>MINOR</option>");
        $(levelSelect).append("<option value='MAJOR'>MAJOR</option>");
        $(levelSelect).append("<option value='CRITICAL'>CRITICAL</option>");
        $(levelSelect).append("<option value='CLEAR'>CLEAR</option>");
        cLevel.appendChild(levelSelect);

        //删除按钮
        var cDelete = rFlowRule.insertCell(-1);
        var remove = document.createElement("div");
        $(remove).addClass("center-block").addClass('remove-btn');
        var removeBtn = document.createElement("button");
        $(removeBtn).attr('type', 'button');
        $(removeBtn).addClass('btn').addClass('btn-sm').addClass('btn-danger-type2');
        $(removeBtn).text('Delete');
        $(remove).append(removeBtn);
        cDelete.appendChild(remove);
        batchSetSlaView.loadAlertFlowSelect("schedule-finish-select" + ruleTr);
        return rFlowRule;
    },

    handleEditColumn: function(evt) {
        var curTarget = evt.currentTarget;
        if (this.editingTarget != curTarget) {
            this.closeEditingTarget();

            var text = $(curTarget).children(".spanValue").text();
            $(curTarget).empty();

            var input = document.createElement("input");
            $(input).attr("type", "text");
            $(input).css("width", "100%");
            $(input).val(text);
            $(curTarget).addClass("editing");
            $(curTarget).append(input);
            $(input).focus();
            this.editingTarget = curTarget;
        }
    },

    closeEditingTarget: function(evt) {},

    render: function() {}
});


function find(str, cha, num) {
    var x = str.indexOf(cha);
    for (var i = 0; i < num; i++) {
        x = str.indexOf(cha, x + 1);
    }
    return x;
}

var tableSorterView;

//用于保存浏览数据，切换页面也能返回之前的浏览进度。
var scheduleModel;
azkaban.ScheduleModel = Backbone.Model.extend({});

// tab 切换定时调度/信号调度
var scheduleTabView;
azkaban.ScheduleTabView = Backbone.View.extend({
    events: {
        'click #time-schedule-view-link': 'handleTimeScheduleViewLinkClick',
        'click #event-schedule-view-link': 'handleEventScheduleViewLinkClick',
        'click #cycle-schedule-view-link': 'handleCycleScheduleViewLinkClick'
    },

    initialize: function(settings) {
        var selectedView = settings.selectedView;
        if (selectedView == 'event-schedule') {
            this.isScheduleTab = false;
            this.activeTab = 'event-schedule'
            this.handleEventScheduleViewLinkClick();
        } else if (selectedView == 'cycle-schedule') {
            this.isScheduleTab = false;
            this.activeTab = 'cycle-schedule'
            this.handleCycleScheduleViewLinkClick();
        } else {
            this.isScheduleTab = true;
            this.activeTab = 'time-schedule'
            this.handleTimeScheduleViewLinkClick();
        }
    },

    render: function() {},
    //定时调度页面
    handleTimeScheduleViewLinkClick: function() {
        this.isScheduleTab = true;
        this.activeTab = 'time-schedule'
        $('#time-schedule-view-link').addClass('active');
        $('#event-schedule-view-link').removeClass('active');
        $('#cycle-schedule-view-link').removeClass('active');
        $('#time-schedule-view').show();
        $('#event-schedule-view').hide();
        $('#cycle-schedule-view').hide();
        $('#search-form').show();
        $('#event-search-form').hide();
        $('#cycle-search-form').hide();
        scheduleModel.set({ isFilter: false })
        if (!scheduleModel.get('scheduleList')) {
            scheduleListView.model.trigger("change:view");
        }
    },
    //信号调度页面
    handleEventScheduleViewLinkClick: function() {
        this.isScheduleTab = false;
        this.activeTab = 'event-schedule'
        $('#time-schedule-view-link').removeClass('active');
        $('#event-schedule-view-link').addClass('active');
        $('#cycle-schedule-view-link').removeClass('active');
        $('#time-schedule-view').hide();
        $('#event-schedule-view').show();
        $('#cycle-schedule-view').hide();
        $('#search-form').hide();
        $('#event-search-form').show();
        $('#cycle-search-form').hide();
        scheduleModel.set({ isFilter: false })

        if (!eventScheduleModel.get('eventScheduleList')) {
            eventScheduleView.model.trigger("change:view");
        }
        // if (!batchSetSlaView.model.get('eventCurrentFlowNameList')) {
        //     batchSetSlaView.getCurrentScheduleAllFlowSetSla(true)
        // }
    },
    //循环调度页面
    handleCycleScheduleViewLinkClick: function() {
        this.isScheduleTab = false;
        this.activeTab = 'event-schedule'
        $('#time-schedule-view-link').removeClass('active');
        $('#event-schedule-view-link').removeClass('active');
        $('#cycle-schedule-view-link').addClass('active');
        $('#time-schedule-view').hide();
        $('#event-schedule-view').hide();
        $('#cycle-schedule-view').show();
        $('#search-form').hide();
        $('#event-search-form').hide();
        $('#cycle-search-form').show();
        scheduleModel.set({ isFilter: false })
        if (!cycleScheduleModel.get('executionCycleList')) {
            cycleScheduleView.model.trigger("change:view");
        }
    }
});

//项目列表页面
// time schedule
var scheduleListView;
azkaban.ScheduleListView = Backbone.View.extend({
    events: {
        "click #pageTable .projectPageSelection  li": "handleChangePageSelection",
        "change #pageTable .pageSizeSelect": "handlePageSizeSelection",
        "click #pageTable .pageNumJump": "handlePageNumJump",

    },

    initialize: function(settings) {
        this.model.bind('render', this.render, this);

        var searchText = filterXSS($("#searchtextbox").val());
        if (this.model.get("search")) {
            this.model.set({ searchterm: searchText });
        }
        this.model.set('elDomId','pageTable');
        this.model.bind('change:view', this.handleChangeView, this);
        this.model.bind('change:page', this.handlePageChange, this);
        this.model.set({ page: 1, pageSize: 20 });
        this.createResize();
    },

    render: function(evt) {
        console.log("render");
        scheduleCheckedProjectList = [];
        scheduleCheckedFlowList = [];
        scheduleCheckedIdtList = [];
        // Render page selections
        var oldScheduleTbody = document.getElementById('schedules-tbody');
        var childrenNum = oldScheduleTbody.children.length;
        // scheduleTbody.empty();
        var newcheduleTbody = document.createElement("tbody");
        newcheduleTbody.setAttribute('id', 'schedules-tbody');
        var scheduleList = this.model.get("scheduleList") || [];
        var schConfig = this.model.get("schConfig");
        var slaSetting = this.model.get("slaSetting");
        var deleteSch = this.model.get("deleteSch");
        var showParam = this.model.get("showParam");
        var imsReport = this.model.get("imsReport");
        for (var i = 0; i < scheduleList.length; ++i) {
            var row = document.createElement("tr");
            //多选框
            var tdCheckbox = document.createElement("td");
            var checked = window.scheduleCheckedIdtList.indexOf(scheduleList[i].scheduleId) > -1 ;
            var checkbox = document.createElement("input");
            $(checkbox).attr("type", "checkbox").attr("class", "schedule-checkbox").attr("projectName", scheduleList[i].projectName).attr("flowName", scheduleList[i].flowName).attr("value", scheduleList[i].scheduleId).prop("checked", false);
            tdCheckbox.appendChild(checkbox);
            row.appendChild(tdCheckbox);

            //组装数字行
            var tdNum = document.createElement("td");
            $(tdNum).text(i + 1);
            $(tdNum).attr("class", "tb-name");
            row.appendChild(tdNum);

            //组装调度id
            var tdScheduleId = document.createElement("td");
            $(tdScheduleId).text(scheduleList[i].scheduleId);
            row.appendChild(tdScheduleId);

            //组装Flow行
            var tdFlow = document.createElement("td");
            var flowA = document.createElement("a");
            $(flowA).attr("href", filterXSS("/manager?project=" + scheduleList[i].projectName + "&flow=" + scheduleList[i].flowName));
            $(flowA).text(scheduleList[i].flowName);
            $(flowA).attr("style", "display: inline-block;max-width: 350px; word-break:break-all;");
            tdFlow.appendChild(flowA);
            row.appendChild(tdFlow);

            //组装Project行
            var tdProject = document.createElement("td");
            var projectA = document.createElement("a");
            $(projectA).attr("href", filterXSS("/manager?project=" + scheduleList[i].projectName));
            $(projectA).text(scheduleList[i].projectName);
            $(projectA).attr("style", "display: inline-block;max-width: 350px; word-break:break-all;");
            tdProject.appendChild(projectA);
            row.appendChild(tdProject);

            //组装用户行
            var tdUser = document.createElement("td");
            $(tdUser).text(scheduleList[i].submitUser);
            row.appendChild(tdUser);

            //组装firstSchedTime
            var tdFirstSchedTime = document.createElement("td");
            $(tdFirstSchedTime).text(getProjectModifyDateFormat(new Date(scheduleList[i].lastModifyConfiguration)));
            row.appendChild(tdFirstSchedTime);

            //组装nextExecTime
            var tdNextExecTime = document.createElement("td");
            $(tdNextExecTime).text(scheduleList[i].nextExecTime > 0 ? getProjectModifyDateFormat(new Date(scheduleList[i].nextExecTime)) : "");
            row.appendChild(tdNextExecTime);

            //组装cronExpression
            var tdCronExpression = document.createElement("td");
            $(tdCronExpression).text(scheduleList[i].cronExpression ? scheduleList[i].cronExpression : wtssI18n.view.notApplicable);
            row.appendChild(tdCronExpression);

            //组装 是否调度有效
            var scheduleActive = document.createElement("td");
            var currentSchActiveFlag = scheduleList[i].otherOption.activeFlag;
            var color = currentSchActiveFlag ? '#4cae4c' : '#d9534f';
            scheduleActive.setAttribute('style', 'color:' + color);
            $(scheduleActive).text(currentSchActiveFlag);
            row.appendChild(scheduleActive);

            //组装 是否是有效工作流
            var validFlow = document.createElement("td");
            var color = scheduleList[i].otherOption.validFlow ? '#4cae4c' : '#d9534f';
            validFlow.setAttribute('style', 'color:' + color);
            $(validFlow).text(scheduleList[i].otherOption.validFlow ? true : false);
            row.appendChild(validFlow);

            //组装 显示参数
            var tdShowArgs = document.createElement("td");
            var showArgsBtn = document.createElement("button");
            $(showArgsBtn).attr("class", "btn btn-sm btn-info");
            $(showArgsBtn).attr("type", "button");
            $(showArgsBtn).attr("data-toggle", "modal");
            $(showArgsBtn).attr("name", i);
            $(showArgsBtn).text(showParam);
            tdShowArgs.appendChild(showArgsBtn);
            row.appendChild(tdShowArgs);

            //组装 是否设置告警
            var tdSlaOptions = document.createElement("td");
            var slaConfFlag = false;
            if ((scheduleList[i].slaOptions) && (scheduleList[i].slaOptions.length != 0)) {
                slaConfFlag = true;
            }
            $(tdSlaOptions).text(slaConfFlag);
            row.appendChild(tdSlaOptions);

            //组装 备注
            const tdRemark = document.createElement("td");
            const spanDom = document.createElement("div");;
            $(spanDom).text(scheduleList[i].comment || "").attr("title", scheduleList[i].comment || "").attr("class","schedulis-comment");
            tdRemark.appendChild(spanDom);
            row.appendChild(tdRemark);

            //组装 删除定时调度按钮
            var tdRemoveSchedBtn = document.createElement("td");
            var removeSchedBtn = document.createElement("button");
            $(removeSchedBtn).attr("class", "btn btn-sm btn-danger").attr("type", "button").attr("name", scheduleList[i].scheduleId + "#" + scheduleList[i].projectName + "#" + scheduleList[i].flowName);
            $(removeSchedBtn).text(deleteSch);
            tdRemoveSchedBtn.appendChild(removeSchedBtn);
            row.appendChild(tdRemoveSchedBtn);

            //组装 设置告警
            var tdAddSlaBtn = document.createElement("td");
            var addSlaBtn = document.createElement("button");
            $(addSlaBtn).attr("class", "btn btn-sm btn-primary").attr("type", "button").attr("onclick", "slaView.initFromSched(" + scheduleList[i].scheduleId + ",'" + scheduleList[i].projectName + "','" + scheduleList[i].flowName + "','" + 'schedule' + "')");
            $(addSlaBtn).text(slaSetting);
            tdAddSlaBtn.appendChild(addSlaBtn);
            row.appendChild(tdAddSlaBtn);

            //组装 调度配置
            var tdEditSchedBtn = document.createElement("td");
            var editSchedBtn = document.createElement("button");
            $(editSchedBtn).attr("class", "btn btn-sm btn-success").attr("type", "button")
                .attr("onclick", "editScheduleClick(" + scheduleList[i].scheduleId + ",'" + scheduleList[i].projectName + "','" + scheduleList[i].flowName + "','" + scheduleList[i].cronExpression + "')");
            $(editSchedBtn).text(schConfig);
            tdEditSchedBtn.appendChild(editSchedBtn);
            row.appendChild(tdEditSchedBtn);

            //组装 开启调度/关闭调度
            var tdSwitchSchedBtn = document.createElement("td");
            var switchSchedBtn = document.createElement("button");
            $(switchSchedBtn).attr("class", "btn btn-sm btn-success").attr("type", "button")
                .attr("onclick", "switchScheduleClick(" + i + "," + scheduleList[i].scheduleId + ",'" + scheduleList[i].projectName + "','" + scheduleList[i].flowName + "','" + 'schedule' +  "','" + currentSchActiveFlag +"')");
            if (currentSchActiveFlag) {
                $(switchSchedBtn).text(wtssI18n.view.inactiveScheduleBtn);
            } else {
                $(switchSchedBtn).text(wtssI18n.view.activeScheduleBtn);
            }
            tdSwitchSchedBtn.appendChild(switchSchedBtn);
            row.appendChild(tdSwitchSchedBtn);

            //组装 ims上报按钮
            var tdImsSchedBtn = document.createElement("td");
            var imsSchedBtn = document.createElement("button");
            var scheduleImsSwitch = scheduleList[i].otherOption.scheduleImsSwitch;
            $(imsSchedBtn).attr("class", "btn btn-sm btn-success").attr("type", "button")
                .attr("onclick", "editImsClick(" + i + "," + scheduleList[i].scheduleId + ",'" + scheduleList[i].projectName + "','schedule'," + scheduleImsSwitch + ")");
            if (scheduleImsSwitch == '1') {
                $(imsSchedBtn).text(wtssI18n.view.inactiveImsBtn);
            } else {
                $(imsSchedBtn).text(wtssI18n.view.activeImsBtn);
            }
            tdImsSchedBtn.appendChild(imsSchedBtn);
            row.appendChild(tdImsSchedBtn);

            newcheduleTbody.appendChild(row);

        }
        oldScheduleTbody.parentNode.replaceChild(newcheduleTbody, oldScheduleTbody);
        if (scheduleList && scheduleList.length) {
            $("#scheduledFlowsTbl").trigger("update");
            $("#scheduledFlowsTbl").trigger("sorton", "");
        }
        this.scheduleCheckboxAddEvent();
        this.renderPagination(evt);

        window.dispatchEvent(this.pageResize);
    },
    // 复选框绑定事件
    scheduleCheckboxAddEvent() {
        $('#schedules-tbody .schedule-checkbox').click(function(e) {
            var scheduleId = e.target.value;
            var projectName = e.target.getAttribute('projectName');
            var flowName =  e.target.getAttribute('flowName');
            if (e.target.checked) {
                scheduleCheckedProjectList.push(projectName);
                scheduleCheckedFlowList.push(flowName);
                scheduleCheckedIdtList.push(scheduleId);
            } else {
                var  index = scheduleCheckedIdtList.indexOf(scheduleId);
                scheduleCheckedProjectList.splice(index, 1);
                scheduleCheckedFlowList.splice(index, 1);
                scheduleCheckedIdtList.splice(index, 1);
            }
        })
    },
    ...commonPaginationFun(),

    handlePageChange: function(evt) {
        var start = this.model.get("page");
        var pageSize = this.model.get("pageSize");
        var requestURL = "/schedule";
        var searchText = this.model.get("searchterm");
        var model = this.model;
        var isFilter = model.get('isFilter')

        var baseParam = {
            "ajax": isFilter ? "preciseSearchFetchAllSchedules" : "ajaxFetchAllSchedules",
            "page": start,
            "size": pageSize,
            "pageNum": this.model.get("page")
        };
        var requestData = {}
        if (isFilter) {
            requestData = model.get('filterParam')
        } else {
            requestData.searchterm = searchText
            if (searchText) {
                requestData.search = "true"
            }
        }
        requestData.time = new Date().getTime()
        Object.assign(requestData, baseParam)
        var successHandler = function(data) {
            if (!model.get('allScheduleIdList') && data.schedules.length > 0) {
                model.set('allScheduleIdList', [data.schedules[0].scheduleId])
            }
            model.set({
                "scheduleList": data.schedules,
                "schConfig": data.schConfig,
                "slaSetting": data.slaSetting,
                "deleteSch": data.deleteSch,
                "showParam": data.showParam,
                "imsReport": data.imsReport,
                "total": data.total
            });
            model.trigger("render");
        };
        $.get(requestURL, requestData, successHandler, "json");
    },
    loadDepartmentData: loadDepartmentData
});
//
var eventScheduleModel
azkaban.EventScheduleModel = Backbone.Model.extend({});

// 信号调度页面处理
var eventScheduleView;
azkaban.EventScheduleView = Backbone.View.extend({
    events: {
        "click #eventSchedulePageTable .projectPageSelection  li": "handleChangePageSelection",
        "change #eventSchedulePageTable .pageSizeSelect": "handlePageSizeSelection",
        "click #eventSchedulePageTable .pageNumJump": "handlePageNumJump",
    },
    initialize: function() {
        this.model.bind('render', this.render, this);

        var searchText = filterXSS($("#searchtextbox").val());
        if (this.model.get("search")) {
            this.model.set({ searchterm: searchText });
        }
        this.model.set('elDomId','eventSchedulePageTable');
        this.model.bind('change:view', this.handleChangeView, this);
        this.model.bind('change:page', this.handlePageChange, this);
        this.model.set({ page: 1, pageSize: 20 });
        this.createResize();
    },
    render: function(evt) {
        console.log("render");
        eventScheduleCheckedProjectList = [];
        eventScheduleCheckedFlowList = [];
        eventScheduleCheckedIdtList = [];
        // var tbody = $("#event-schedules-tbody");
        // tbody.empty();
        var oldEventTbody = document.getElementById('event-schedules-tbody');
        var childrenNum = oldEventTbody.children.length;
        // scheduleTbody.empty();
        var newEventTbody = document.createElement("tbody");
        newEventTbody.setAttribute('id', 'event-schedules-tbody');
        var schedules = this.model.get("eventScheduleList");
        if (!schedules) {
            schedules = [];
        }
        var schConfig = this.model.get("schConfig");
        var slaSetting = this.model.get("slaSetting");
        var deleteSch = this.model.get("deleteSch");
        var schConfig = this.model.get("schConfig");
        var imsReport = this.model.get("imsReport");
        for (var i = 0; i < schedules.length; i++) {
            var row = document.createElement("tr");
             //多选框
             var tdCheckbox = document.createElement("td");
             var checked = window.eventScheduleCheckedIdtList.indexOf(schedules[i].scheduleId) > -1 ;
             var checkbox = document.createElement("input");
             $(checkbox).attr("type", "checkbox").attr("class", "event-schedule-checkbox").attr("projectName", schedules[i].projectName).attr("flowName", schedules[i].flowName).attr("value", schedules[i].scheduleId).prop("checked", false);
             tdCheckbox.appendChild(checkbox);
             row.appendChild(tdCheckbox);

            //组装数字行

            var tdNum = document.createElement("td");
            $(tdNum).text(i + 1);
            $(tdNum).attr("class", "tb-name");
            row.appendChild(tdNum);

            // 组装执行 id
            var tdScheduleId = document.createElement("td");
            $(tdScheduleId).text(schedules[i].scheduleId);
            row.appendChild(tdScheduleId);

            //组装Flow行
            var tdFlow = document.createElement("td");
            var flowA = document.createElement("a");
            $(flowA).attr("href", filterXSS("/manager?project=" + schedules[i].projectName + "&flow=" + schedules[i].flowName));
            $(flowA).text(schedules[i].flowName);
            $(flowA).attr("style", "word-break:break-all;");
            tdFlow.appendChild(flowA);
            row.appendChild(tdFlow);

            //组装Project行
            var tdProject = document.createElement("td");
            var projectA = document.createElement("a");
            $(projectA).attr("href", filterXSS("/manager?project=" + schedules[i].projectName));
            $(projectA).text(schedules[i].projectName);
            $(projectA).attr("style", "word-break:break-all;");
            tdProject.appendChild(projectA);
            row.appendChild(tdProject);

            //组装用户行
            var tdUser = document.createElement("td");
            $(tdUser).text(schedules[i].submitUser);
            row.appendChild(tdUser);

            // 组装 调度配置修改时间
            var tdChangeTime = document.createElement("td");
            $(tdChangeTime).text(getProjectModifyDateFormat(new Date(schedules[i].lastModifyTime)));
            row.appendChild(tdChangeTime);

            // 组装 sender
            /*var tdSender = document.createElement("td");
            $(tdSender).text(schedules[i].sender);
            row.appendChild(tdSender);*/

            // 组装 topic
            var tdTopic = document.createElement("td");
            $(tdTopic).text(schedules[i].topic);
            row.appendChild(tdTopic);

            // 组装 msgName
            var tdMsgName = document.createElement("td");
            $(tdMsgName).text(schedules[i].msgName);
            row.appendChild(tdMsgName);

            //组装 是否调度有效
            var scheduleActive = document.createElement("td");
            var currentSchActiveFlag = schedules[i].otherOption.activeFlag;
            var color = currentSchActiveFlag ? '#4cae4c' : '#d9534f';
            scheduleActive.setAttribute('style', 'color:' + color);
            $(scheduleActive).text(currentSchActiveFlag);
            row.appendChild(scheduleActive);

            //组装 是否是有效工作流
            var validFlow = document.createElement("td");
            var color = schedules[i].otherOption.validFlow ? '#4cae4c' : '#d9534f';
            validFlow.setAttribute('style', 'color:' + color);
            $(validFlow).text(schedules[i].otherOption.validFlow ? true : false);
            row.appendChild(validFlow);

            //组装 是否设置告警
            var tdSlaOptions = document.createElement("td");
            var slaConfFlag = false;
            if ((schedules[i].slaOptions) && (schedules[i].slaOptions.length != 0)) {
                slaConfFlag = true;
            }
            $(tdSlaOptions).text(slaConfFlag);
            row.appendChild(tdSlaOptions);
            //组装 备注
            const tdRemark = document.createElement("td");
            const spanDom = document.createElement("div");;
            $(spanDom).text(schedules[i].comment || "").attr("title", schedules[i].comment || "").attr("class","schedulis-comment");
            tdRemark.appendChild(spanDom);
            row.appendChild(tdRemark);

            //组装 删除调度按钮
            var tdRemoveSchedBtn = document.createElement("td");
            var removeSchedBtn = document.createElement("button");
            $(removeSchedBtn).attr("class", "btn btn-sm btn-danger")
                .attr("type", "button").attr("name", schedules[i].scheduleId + "#" +
                    schedules[i].projectName + "#" + schedules[i].flowName);
            $(removeSchedBtn).text(deleteSch);
            tdRemoveSchedBtn.appendChild(removeSchedBtn);
            row.appendChild(tdRemoveSchedBtn);

            //组装 告警按钮
            var tdAddSlaBtn = document.createElement("td");
            var addSlaBtn = document.createElement("button");
            $(addSlaBtn).attr("class", "btn btn-sm btn-primary").attr("type", "button")
                .attr("onclick", "slaView.initFromSched(" + schedules[i].scheduleId + ",'" +
                    schedules[i].projectName + "','" + schedules[i].flowName + "','" + 'eventSchedule' + "')");
            $(addSlaBtn).text(slaSetting);
            tdAddSlaBtn.appendChild(addSlaBtn);
            row.appendChild(tdAddSlaBtn);

            //组装 调度配置
            var tdEditSchedBtn = document.createElement("td");
            var editSchedBtn = document.createElement("button");
            $(editSchedBtn).attr("class", "btn  btn-sm btn-success").attr("type", "button")
                .attr("onclick", "editEventScheduleClick(" + schedules[i].scheduleId + ",'" + schedules[i].projectId + "','" + schedules[i].projectName + "','" + schedules[i].flowName + "','" + 'eventSchedule' + "')");
            $(editSchedBtn).text(schConfig);
            tdEditSchedBtn.appendChild(editSchedBtn);
            row.appendChild(tdEditSchedBtn);

            //组装 开启调度/关闭调度
            var tdSwitchSchedBtn = document.createElement("td");
            var switchSchedBtn = document.createElement("button");
            $(switchSchedBtn).attr("class", "btn  btn-sm btn-success").attr("type", "button")
                .attr("onclick", "switchScheduleClick(" + i + "," +
                    schedules[i].scheduleId + ",'" + schedules[i].projectName + "','" +
                    schedules[i].flowName + "','" + 'eventSchedule' + "','" + currentSchActiveFlag +"')");

            if (currentSchActiveFlag) {
                $(switchSchedBtn).text(wtssI18n.view.inactiveScheduleBtn);
            } else {
                $(switchSchedBtn).text(wtssI18n.view.activeScheduleBtn);
            }
            tdSwitchSchedBtn.appendChild(switchSchedBtn);
            row.appendChild(tdSwitchSchedBtn);

            //组装 ims上报按钮
            var tdImsSchedBtn = document.createElement("td");
            var imsSchedBtn = document.createElement("button");
            var eventScheduleImsSwitch = schedules[i].otherOption.eventScheduleImsSwitch;
            $(imsSchedBtn).attr("class", "btn  btn-sm btn-success").attr("type", "button")
                .attr("onclick", "editImsClick(" + i + "," + schedules[i].scheduleId + ",'" + schedules[i].projectName + "','eventSchedule'," + eventScheduleImsSwitch + ")");
            if (eventScheduleImsSwitch == '1') {
                $(imsSchedBtn).text(wtssI18n.view.inactiveImsBtn);
            } else {
                $(imsSchedBtn).text(wtssI18n.view.activeImsBtn);
            }
            tdImsSchedBtn.appendChild(imsSchedBtn);
            row.appendChild(tdImsSchedBtn);


            newEventTbody.appendChild(row);

        }
        oldEventTbody.parentNode.replaceChild(newEventTbody, oldEventTbody);
        this.eventScheduleCheckboxAddEvent();
        this.renderPagination(evt);

        if (schedules && schedules.length) {
            $("#eventScheduledFlowsTbl").trigger("update");
            $("#eventScheduledFlowsTbl").trigger("sorton", "");
        }

        window.dispatchEvent(this.pageResize);
    },
    // 复选框绑定事件
    eventScheduleCheckboxAddEvent() {
        $('#event-schedules-tbody .event-schedule-checkbox').click(function(e) {
            var scheduleId = e.target.value;
            var projectName = e.target.getAttribute('projectName');
            var flowName = e.target.getAttribute('flowName');
            if (e.target.checked) {
                eventScheduleCheckedProjectList.push(projectName);
                eventScheduleCheckedFlowList.push(flowName);
                eventScheduleCheckedIdtList.push(scheduleId);
            } else {
                var index = eventScheduleCheckedIdtList.indexOf(scheduleId);
                eventScheduleCheckedProjectList.splice(index, 1);
                eventScheduleCheckedFlowList.splice(index, 1);
                eventScheduleCheckedIdtList.splice(index, 1);
            }
        })
    },
    ...commonPaginationFun(),
    handlePageChange: function() {
        var start = this.model.get("page");
        var pageSize = this.model.get("pageSize");
        var requestURL = "/eventschedule";
        var searchText = this.model.get("searchterm");
        var model = this.model;
        var isFilter = scheduleModel.get('isFilter')


        var baseParam = {
            "ajax": isFilter ? "preciseSearchFetchAllEventSchedules" : "fetchAllEventSchedules",
            "page": start,
            "size": pageSize,
            "pageNum": this.model.get("page")
        };
        var requestData = {}
        if (isFilter) {
            requestData = scheduleModel.get('filterParam')
            delete requestData.startTime
            delete requestData.endTime
        } else {
            requestData.searchterm = searchText
            if (searchText) {
                requestData.search = "true"
            }
        }

        Object.assign(requestData, baseParam)

        var successHandler = function(data) {
            if (data.total > 0) {
                $("#eventSchedulePageTable").show();
                // var allEventScheduleIdList = []
                // for (var i = 0; i < data.allSchedules.length; i++) {
                //     allEventScheduleIdList.push(data.allSchedules[i].scheduleId)
                // }
                if (!model.get('allScheduleIdList') && data.eventSchedules.length > 0) {
                    model.set('allScheduleIdList', [data.eventSchedules[0].scheduleId])
                }
                model.set({
                    "eventScheduleList": data.eventSchedules,
                    // "allEventScheduleIdList": allEventScheduleIdList,
                    "slaSetting": data.slaSetting,
                    "deleteSch": data.deleteSch,
                    "schConfig": data.schConfig,
                    "imsReport": data.imsReport,
                    "close": data.close,
                    "total": data.total
                });
                model.trigger("render");
            } else {
                model.set({
                    "eventScheduleList": [],
                });
                $('#event-schedules-tbody').html('')
                $("#eventSchedulePageTable").hide();
            }
            model.trigger("render");
        };
        $.get(requestURL, requestData, successHandler, "json");
    },
});

var cycleScheduleModel
azkaban.CycleScheduleModel = Backbone.Model.extend({})

// 循环调度页面处理
var cycleScheduleView
azkaban.CycleScheduleView = Backbone.View.extend({
    events: {
        "click #cycleSchedulePageTable .projectPageSelection  li": "handleChangePageSelection",
        "change #cycleSchedulePageTable .pageSizeSelect": "handlePageSizeSelection",
        "click #cycleSchedulePageTable .pageNumJump": "handlePageNumJump",
    },
    initialize: function() {
        this.model.bind('render', this.render, this);

        var searchText = filterXSS($("#searchtextbox").val());
        if (this.model.get("search")) {
            this.model.set({ searchterm: searchText });
        }
        this.model.set('elDomId','cycleSchedulePageTable');
        this.model.bind('change:view', this.handleChangeView, this);
        this.model.bind('change:page', this.handlePageChange, this);
        this.model.set({ page: 1, pageSize: 20 });
        this.createResize();
    },
    render: function(evt) {
        console.log("render");
        scheduleCheckedProjectList = [];
        scheduleCheckedFlowList = [];
        scheduleCheckedIdtList = [];
        // Render page selections
        var oldCycleScheduleTbody = document.getElementById('cycle-schedules-tbody');
        var childrenNum = oldCycleScheduleTbody.children.length;
        // scheduleTbody.empty();
        var newCycleScheduleTbody = document.createElement("tbody");
        newCycleScheduleTbody.setAttribute('id', 'cycle-schedules-tbody');
        var scheduleList = this.model.get("executionCycleList") || [];
        for (var i = 0; i < scheduleList.length; ++i) {
            var row = document.createElement("tr");
            // //多选框
            // var tdCheckbox = document.createElement("td");
            // var checked = window.scheduleCheckedIdtList.indexOf(scheduleList[i].currentExecId) > -1 ;
            // var checkbox = document.createElement("input");
            // $(checkbox).attr("type", "checkbox").attr("class", "schedule-checkbox").attr("projectName", scheduleList[i].projectName).attr("flowName", scheduleList[i].flowId).attr("value", scheduleList[i].currentExecId).prop("checked", false);
            // tdCheckbox.appendChild(checkbox);
            // row.appendChild(tdCheckbox);

            //组装数字行
            var tdNum = document.createElement("td");
            $(tdNum).text(i + 1);
            $(tdNum).attr("class", "tb-name");
            row.appendChild(tdNum);

            //组装调度id
            var tdScheduleId = document.createElement("td");
            $(tdScheduleId).text(scheduleList[i].id);
            row.appendChild(tdScheduleId);

            //组装Flow行
            var tdFlow = document.createElement("td");
            var flowA = document.createElement("a");
            $(flowA).attr("href", filterXSS("/manager?project=" + scheduleList[i].projectName + "&flow=" + scheduleList[i].flowId));
            $(flowA).text(scheduleList[i].flowId);
            $(flowA).attr("style", "display: inline-block;max-width: 350px; word-break:break-all;");
            tdFlow.appendChild(flowA);
            row.appendChild(tdFlow);

            //组装Project行
            var tdProject = document.createElement("td");
            var projectA = document.createElement("a");
            $(projectA).attr("href", filterXSS("/manager?project=" + scheduleList[i].projectName));
            $(projectA).text(scheduleList[i].projectName);
            $(projectA).attr("style", "display: inline-block;max-width: 350px; word-break:break-all;");
            tdProject.appendChild(projectA);
            row.appendChild(tdProject);

            //组装用户行
            var tdUser = document.createElement("td");
            $(tdUser).text(scheduleList[i].submitUser);
            row.appendChild(tdUser);

            //组装firstSchedTime
            var tdFirstSchedTime = document.createElement("td");
            $(tdFirstSchedTime).text(getProjectModifyDateFormat(new Date(scheduleList[i].updateTime)));
            row.appendChild(tdFirstSchedTime);

            //组装 是否调度有效
            var scheduleActive = document.createElement("td");
            var currentSchActiveFlag = scheduleList[i].otherOptions.activeFlag;
            var color = currentSchActiveFlag ? '#4cae4c' : '#d9534f';
            scheduleActive.setAttribute('style', 'color:' + color);
            $(scheduleActive).text(currentSchActiveFlag);
            row.appendChild(scheduleActive);

            //组装 是否是有效工作流
            var validFlow = document.createElement("td");
            var color = scheduleList[i].otherOptions.validFlow ? '#4cae4c' : '#d9534f';
            validFlow.setAttribute('style', 'color:' + color);
            $(validFlow).text(scheduleList[i].otherOptions.validFlow ? true : false);
            row.appendChild(validFlow);

            //组装 显示参数
            var tdShowArgs = document.createElement("td");
            var showArgsBtn = document.createElement("button");
            $(showArgsBtn).attr("class", "btn btn-sm btn-info");
            $(showArgsBtn).attr("type", "button");
            $(showArgsBtn).attr("data-toggle", "modal");
            $(showArgsBtn).attr("name", i);
            $(showArgsBtn).text(wtssI18n.common.param);
            tdShowArgs.appendChild(showArgsBtn);
            row.appendChild(tdShowArgs);

            //组装 删除调度按钮
            var tdRemoveSchedBtn = document.createElement("td");
            var removeSchedBtn = document.createElement("button");
            $(removeSchedBtn).attr("class", "btn btn-sm btn-danger").attr("type", "button").attr("name", scheduleList[i].currentExecId + "#" + scheduleList[i].projectName + "#" + scheduleList[i].flowId + "#" + scheduleList[i].projectId);
            $(removeSchedBtn).text(wtssI18n.common.delete);
            tdRemoveSchedBtn.appendChild(removeSchedBtn);
            row.appendChild(tdRemoveSchedBtn);

            //组装 调度配置
            var tdEditSchedBtn = document.createElement("td");
            var editSchedBtn = document.createElement("button");
            $(editSchedBtn).attr("class", "btn btn-sm btn-success").attr("type", "button")
                .attr("onclick", "editCycleScheduleClick(" + scheduleList[i].currentExecId + ",'" + scheduleList[i].projectName + "','" + scheduleList[i].projectId + "','" + scheduleList[i].flowId + "','" + scheduleList[i].cronExpression + "')");
            $(editSchedBtn).text(wtssI18n.common.schedule);
            tdEditSchedBtn.appendChild(editSchedBtn);
            row.appendChild(tdEditSchedBtn);

            //组装 开启调度/关闭调度
            var tdSwitchSchedBtn = document.createElement("td");
            var switchSchedBtn = document.createElement("button");
            $(switchSchedBtn).attr("class", "btn btn-sm btn-danger").attr("type", "button")
                .attr("onclick", "switchScheduleClick(" + i + "," + scheduleList[i].id + ",'" + scheduleList[i].projectName + "','" + scheduleList[i].flowId + "','" + 'cycleSchedule' +  "','" + currentSchActiveFlag +"')");
            $(switchSchedBtn).text(wtssI18n.view.killCycleExecution);
            tdSwitchSchedBtn.appendChild(switchSchedBtn);
            row.appendChild(tdSwitchSchedBtn);

            newCycleScheduleTbody.appendChild(row);

        }
        oldCycleScheduleTbody.parentNode.replaceChild(newCycleScheduleTbody, oldCycleScheduleTbody);
        // this.scheduleCheckboxAddEvent();
        this.renderPagination(evt);

        if (scheduleList && scheduleList.length) {
            $("#cycleScheduledFlowsTbl").trigger("update");
            $("#cycleScheduledFlowsTbl").trigger("sorton", "");
        }

        window.dispatchEvent(this.pageResize);
    },
    ...commonPaginationFun(),
    handlePageChange: function() {
        var start = this.model.get("page");
        var pageSize = this.model.get("pageSize");
        var requestURL = "/cycle";
        var searchText = this.model.get("searchterm");
        var model = this.model;
        var isFilter = scheduleModel.get('isFilter')


        var baseParam = {
            "ajax": isFilter ? "executionCyclePage" : "executionCyclePage",
            "page": start,
            "size": pageSize,
            "pageNum": this.model.get("page")
        };
        var requestData = {}
        if (isFilter) {
            requestData = scheduleModel.get('filterParam')
            delete requestData.startTime
            delete requestData.endTime
            delete requestData.subsystem
            delete requestData.busPath
            delete requestData.departmentId
            delete requestData.setAlarm
            delete requestData.scheduleInterval
        } else {
            requestData.searchterm = searchText
            if (searchText) {
                requestData.search = "true"
            }
        }

        Object.assign(requestData, baseParam)

        var successHandler = function(data) {
            if (data.executionCycleList.length > 0) {
                $("#cycleSchedulePageTable").show();
                model.set({
                    "executionCycleList": data.executionCycleList,
                    "total": data.total
                });
                model.trigger("render");
            } else {
                model.set({
                    "executionCycleList": [],
                });
                $('#cycle-schedules-tbody').html('')
                $("#cycleSchedulePageTable").hide();
            }
            model.trigger("render");
        };
        $.get(requestURL, requestData, successHandler, "json");
    },
})

var advFilterView;
azkaban.AdvFilterView = Backbone.View.extend({
    events: {
        "click #filter-btn": "handleAdvFilter", //模糊查询
    },

    initialize: function(settings) {
        this.advQueryViewFirstShow = true
        this.getSelectOption = false;
        loadSubSystemData();
        loadBusPathData();
    },
    render: function(type) {
        $('#subsystem-div').show()
        $('#buspath-div').show()
        $('#department-div').show()
        $('#nextExecutionTime').show()
        $('#frequency-div').show()
        $('#is-job-flow-valid').show()
        $('#is-active-sch').show()
        $('#alarm-div').show()
        if (type === 'cycle-schedule') {
            $('#subsystem-div').hide()
            $('#buspath-div').hide()
            $('#department-div').hide()
            $('#nextExecutionTime').hide()
            $('#frequency-div').hide()
            $('#is-job-flow-valid').hide()
            $('#is-active-sch').hide()
            $('#alarm-div').hide()
        } else if (type === 'event-schedule') {
            $('#nextExecutionTime').hide()
        }
        if (!this.getSelectOption) {
            this.getSelectOption = true;
            scheduleListView.loadDepartmentData(function () {
                advFilterView.renderDepartmentSelectelect(scheduleModel.get('departPathMap'));
            })
        }
        this.initFilterForm()

        $('#nextExecutionTime #datetimebegin').datetimepicker();
        $('#nextExecutionTime #datetimeend').datetimepicker();
        $('#nextExecutionTime #datetimebegin').on('change.dp', function(e) {
            $('#nextExecutionTime #datetimeend').data('DateTimePicker').setStartDate(e.date);
        });
        $('#nextExecutionTime #datetimeend').on('change.dp', function(e) {
            $('#nextExecutionTime #datetimebegin').data('DateTimePicker').setEndDate(e.date);
        });
    },
    initFilterForm() {
        $("#projcontain").val('');
        $('#flowcontain').val('');
        $("#usercontain").val("");
        $("#subSystemQuery").empty();
        $("#busPathQuery").empty();
        $("#departmentSelect").val("").trigger("change");
        $('#nextExecutionTime #datetimebegin').val('');
        $('#nextExecutionTime #datetimeend').val('');
        $('#executeFrequency [name="frequency"]').prop('checked', false);
        $('#isVlidFlow [name="vlidFlow"]').prop('checked', false);
        $('#isValid [name="optionsRadiosinline"]').prop('checked', false);
        $('#isAlarm [name="settingAlarm"]').prop('checked', false);
    },
    handleAdvFilter: function() {
        console.log("handleAdv");
        var projcontain = $('#projcontain').val();
        var flowcontain = $('#flowcontain').val();
        var usercontain = $('#usercontain').val();
        var subsystem = $('#subSystemQuery').val();
        var busPath = $('#busPathQuery').val();
        var departmentId = $('#departmentSelect').val();
        var startTime = $('#nextExecutionTime #datetimebegin').val();
        startTime = startTime ? new Date(startTime).getTime() : '';
        var endTime = $('#nextExecutionTime #datetimeend').val();
        endTime = endTime ? new Date(endTime).getTime() : '';
        var validFlow =$('#isVlidFlow :checked').val() || 'All';
        var activeFlag = $('#isValid :checked').val() || 'All';
        var setAlarm = $('#isAlarm :checked').val() || 'All';
        var scheduleInterval = $('#executeFrequency :checked').val() || 'all';
        $('#nextExecutionTime #datetimeend').val();
        if (checkProject(projcontain)) {
            return;
        };
        if (checkProject(flowcontain)) {
            return;
        };
        if (checkEnglish(usercontain)) {
            return;
        };
        console.log("filtering history");
        scheduleModel.set('filterParam', {
            projcontain: projcontain,
            flowcontain: flowcontain,
            usercontain: usercontain,
            subsystem: subsystem,
            busPath: busPath,
            departmentId: departmentId,
            startTime: startTime,
            endTime: endTime,
            validFlow,
            setAlarm,
            activeFlag,
            scheduleInterval,
        })
        scheduleModel.set('isFilter', true);
        var hash = window.location.hash;
        if (hash == '#event-schedule') {
            eventScheduleView.model.trigger("change:view");
        } else if(hash === '#cycle-schedule'){
            cycleScheduleView.model.trigger("change:view");
        } else {
            scheduleListView.model.trigger("change:view");
        }
        $('#adv-filter').modal('hide')

    },
    renderBusPathSelect: renderBusPathSelect,
    renderSubSystemSelect: renderSubSystemSelect,
    renderDepartmentSelectelect: renderDepartmentSelectelect

});