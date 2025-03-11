$.namespace('azkaban');

$(function() {
    var eventQueueModel = new azkaban.EventQueueModel();
    new azkaban.EventQueueListView({
        el: $("#eventQueueList"),
        model: eventQueueModel
    });

    $("#quickSearchEventQueue").on("click", function() {
        eventQueueModel.trigger("change:page");
    });
    $("#searchTextbox").on("keyup", function() {
        if (e.keyCode === 13) {
            eventQueueModel.trigger("change:page");
        }
    });
});

azkaban.EventQueueModel = Backbone.Model.extend({});

azkaban.EventQueueListView = Backbone.View.extend({
    events: {
        "click #eventQueuePageSelection li": "handleChangePageSelection",
        "change #eventQueuePageSelection .pageSizeSelect": "handlePageSizeSelection",
        "click #eventQueuePageSelection .pageNumJump": "handlePageNumJump",
    },
    initialize: function() {
        this.model.bind('change:view', this.handleChangeView, this);
        this.model.bind('render', this.render, this);
        this.model.bind('change:page', this.handlePageChange, this);
        this.setMessageParams();
        if (this.model.get("type") !== "auth") {
            $("#rmbNumber").hide();
        }
        this.model.set({ page: 1, pageSize: 20 });
        var hash = window.location.hash;
        this.model.set('elDomId','eventQueuePageSelection'); 
        this.createResize();
    },
    setMessageParams: function() {
        // topic 、 msgName包含特殊字符
        const url = window.location.href;
        const arr = url.split("?topic=");
        const topicArr = arr[1].split("&msgName=");
        const msgNameArr = topicArr[1].split("&type=");
        this.model.set({ topic: topicArr[0], msgName: msgNameArr[0], type: msgNameArr[1] });
    },
    render: function() {
        var tbody = $("#eventQueueTbody");
        tbody.empty();
        var eventQueueList = this.model.get("eventQueueList");
        const isAuth = this.model.get("type") === "auth"
        if (!eventQueueList || eventQueueList.length === 0) {
            $("#eventQueuePageSelection").hide();
        } else {
            $("#eventQueuePageSelection").show();
        }
        eventQueueList.forEach(function(eq, i) {
            var row = document.createElement("tr");
            var tdNum = document.createElement("td");
            $(tdNum).text(i + 1);
            $(tdNum).attr("class", "tb-name");
            row.appendChild(tdNum);

            var tdId = document.createElement("td");
            $(tdId).text(eq.msgId);
            row.appendChild(tdId);

            var tdSender = document.createElement("td");
            $(tdSender).text(eq.sender);
            row.appendChild(tdSender);
            if (isAuth) {
            // rmb
            var tdRmb = document.createElement("td");
            $(tdRmb).text(eq.wemqBizno);
            $(tdTopic).attr("style", "word-break:break-all;width:250px");
            row.appendChild(tdRmb);
            }

            var tdTopic = document.createElement("td");
            $(tdTopic).text(eq.topic);
            $(tdTopic).attr("style", "word-break:break-all;width:250px");
            row.appendChild(tdTopic);

            var tdMsgName = document.createElement("td");
            $(tdMsgName).text(eq.msgName);
            $(tdMsgName).attr("style", "word-break:break-all;width:250px");
            row.appendChild(tdMsgName);

            var tdSendTime = document.createElement("td");
            $(tdSendTime).text(eq.sendTime);
            $(tdSendTime).attr("style", "word-break:break-all;width:250px");
            row.appendChild(tdSendTime);

            var tdMsgBody = document.createElement("td");
            $(tdMsgBody).text(eq.msg);
            $(tdMsgBody).attr("style", "word-break:break-all;width:250px");
            row.appendChild(tdMsgBody);

            tbody.append(row);
        });
        this.renderPagination();
        $("#eventQueueTable").trigger("update")
    },
    ...commonPaginationFun(),
    handlePageChange: function() {
        const model = this.model;
        const pageNum = model.get("page");
        const pageSize = model.get("pageSize");
        const searchKey = $("#searchTextbox").val();;
        const requestURL = "/event/queue";
        var requestData = {
            "ajax": "loadEventQueueData",
            "pageNum": pageNum,
            "pageSize": pageSize,
            "search": searchKey,
            "topic": model.get("topic"),
            "msgName": model.get("msgName"),
            authType: model.get("type") === "auth",
        };
        var successHandler = function(data) {
            model.set({
                "eventQueueList": data.eventQueueList,
                "total": data.total
            });
            model.trigger("render");
        };
        $.get(requestURL, requestData, successHandler, "json");
    }
});