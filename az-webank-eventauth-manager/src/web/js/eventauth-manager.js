$.namespace('azkaban');

$(function() {
    var eventAuthModel = new azkaban.EventAuthModel();
    // eventAuthListView 为全局变量
    eventAuthListView = new azkaban.EventAuthListView({
        el: $("#eventAuthList"),
        model: eventAuthModel
    });
    $("#quickSearchForm").on('keydown', function (event){
        if(event.key === 'Enter'){
            event.preventDefault();
            eventAuthModel.trigger("change:view");
        }
    })
    $("#quickSearchEventAuth").click(function() {
        eventAuthModel.trigger("change:view");
        // $("#quickSearchForm").submit();
    });

    $("#setting-backlog-alert").click(function() {
        const emails = $("#schedule-backlog-emails").val().trim();
        const level = $('#alert-level').val();
        if (!level) {
            messageBox.show('请选择告警级别', 'warning');
            return;
        }
        const index = eventAuthListView.model.get("alertIndex");
        const eventAuthList = eventAuthListView.model.get("eventAuthList");
        const info = eventAuthList[index];
        const redirectURL = `/event?ajax=${eventAuthListView.model.get("type") === "auth" ? "setEventAuthBacklogAlarmUser" : "setEventUnauthBacklogAlarmUser"}`;
        const requestData = {
            topic:  info.topic,
            sender: info.sender,
            msgName: info.msgName,
            backlogAlarmUser: emails,
            alertLevel: level,
        };
        var successHandler = function (data) {
            if (data && data.error) {
                messageBox.show(data.error, 'danger');
            }else {
                messageBox.show(wtssI18n.common.optSuccessfully);
                $('#backlog-alert-modal').modal('hide');
                eventAuthListView.handlePageChange();
            }
        };
        $.ajax({
            url: redirectURL,
            type: "post",
            async: true,
            data: JSON.stringify(requestData) ,
            dataType: "json",
            headers: {'Content-Type': 'application/json;'},
            error: function (error) {
                var responseText = error.responseText ? JSON.parse(error.responseText) : {};
                responseText.message && messageBox.show(responseText.message, 'danger');
                console.log('error', error)
            },
            success: successHandler
        });
    });
    
    $("#header-tabs").on("click",function(e) {
        e.preventDefault();
        if (e.target.name ===  eventAuthListView.model.get("tabActive")) {
            return;
        }
        eventAuthListView.model.set("tabActive", e.target.name);
        $(e.target.parentElement).addClass("active").siblings().removeClass("active");
        $("#searchTextbox").val("");
        eventAuthModel.trigger("change:view");
    })
});

azkaban.EventAuthModel = Backbone.Model.extend({});

azkaban.EventAuthListView = Backbone.View.extend({
    events: {
        "click #eventAuthPageSelection li": "handleChangePageSelection",
        "change #eventAuthPageSelection .pageSizeSelect": "handlePageSizeSelection",
        "click #eventAuthPageSelection .pageNumJump": "handlePageNumJump",
    },
    initialize: function() {
        this.model.bind('change:view', this.handleChangeView, this);
        this.model.bind('render', this.render, this);
        this.model.bind('change:page', this.handlePageChange, this);
        
        this.model.set({ page: 1, pageSize: 20, tabActive: "auth" });
        this.model.set('elDomId','eventAuthPageSelection'); 
        this.createResize();
    },
    // 告警绑定事件
    backlogAlertEvent() {
        $('#eventAuthTbody .alert-event-auth').click(function(e) {
            const index = e.target.getAttribute('index');
            const type = e.target.getAttribute('type');
            eventAuthListView.model.set({ alertIndex: index, type: type });
            var eventAuthList = eventAuthListView.model.get("eventAuthList");
            var info = eventAuthList[index];
            var level = info.alertLevel || 'alertLevel';
            $('#alert-level').val(level);
            $("#schedule-backlog-emails").val(info.backlogAlarmUser || '');
            $('#backlog-alert-modal').modal();
        })
    },
    render: function() {
        var tbody = $("#eventAuthTbody");
        tbody.empty();
        var eventAuthList = this.model.get("eventAuthList");
        const tabActive = this.model.get("tabActive") || "auth";
        if (!eventAuthList || eventAuthList.length == 0) {
            $("#eventAuthPageSelection").hide();
        } else {
            $("#eventAuthPageSelection").show();
        }
        eventAuthList.forEach(function(ea, i) {
            var row = document.createElement("tr");
            var tdNum = document.createElement("td");
            $(tdNum).text(i + 1);
            $(tdNum).attr("class", "tb-name");
            row.appendChild(tdNum);

            var tdSender = document.createElement("td");
            $(tdSender).text(ea.sender);
            row.appendChild(tdSender);

            var tdTopic = document.createElement("td");
            $(tdTopic).text(ea.topic);
            $(tdTopic).attr("style", "word-break:break-all;width:250px");
            row.appendChild(tdTopic);

            var tdMsgName = document.createElement("td");
            $(tdMsgName).text(ea.msgName);
            $(tdMsgName).attr("style", "word-break:break-all;width:250px");
            row.appendChild(tdMsgName);

            var tdRecordTime = document.createElement("td");
            $(tdRecordTime).text(ea.recordTime);
            $(tdRecordTime).attr("style", "word-break:break-all;width:180px");
            row.appendChild(tdRecordTime);

            var tdDetail = document.createElement("td");

            if (tabActive === "auth") {
            var aLog = document.createElement("a");
            var logURL = filterXSS("/event/auth/log?sender=" + ea.sender);
            $(aLog).attr("href", logURL);
            $(aLog).css("margin-right", "8px");
            $(aLog).text(wtssI18n.view.log);
            tdDetail.appendChild(aLog);
            }

            var aDetail = document.createElement("a");
            var detailURL = filterXSS("/event/queue?topic=" + ea.topic + "&msgName=" + ea.msgName + "&type=" + tabActive);
            $(aDetail).attr("href", detailURL);
            $(aDetail).css("margin-right", "8px");
            $(aDetail).text(wtssI18n.view.detail);
            tdDetail.appendChild(aDetail);

            var aStatus = document.createElement("a");
            var statusURL = filterXSS("/event/status?topic=" + ea.topic + "&msgName=" + ea.msgName + "&type=" + tabActive);
            $(aStatus).attr("href", statusURL);
            $(aStatus).css("margin-right", "8px");
            $(aStatus).text(wtssI18n.view.status);
            tdDetail.appendChild(aStatus);

            var aAlert = document.createElement("a");
            $(aAlert).attr("href", 'javascript:void(0)').attr("class", 'alert-event-auth').attr("index", i).attr("type", tabActive).css("margin-right", "8px").text(wtssI18n.common.sla);
            tdDetail.appendChild(aAlert);

            row.appendChild(tdDetail);
            tbody.append(row);
        });
        this.renderPagination();
        $("#eventAuthTable").trigger("update")
        this.backlogAlertEvent();
    },
    ...commonPaginationFun(),
    handlePageChange: function() {
        var pageNum = this.model.get("page");
        var pageSize = this.model.get("pageSize");
        var searchKey = filterXSS($("#searchTextbox").val());
        const tabActive = this.model.get("tabActive") || "auth";
        var requestURL = "/event/auth";
        var model = this.model;
        var requestData = {
            "ajax": tabActive === "auth" ? "loadEventAuthData" : "loadEventUnauthData",
            "pageNum": pageNum,
            "pageSize": pageSize,
            "search": searchKey
        };
        var successHandler = function(data) {
            model.set({
                "eventAuthList": data.eventAuthList,
                "total": data.total
            });
            model.trigger("render");
        };
        $.get(requestURL, requestData, successHandler, "json");
    }
});