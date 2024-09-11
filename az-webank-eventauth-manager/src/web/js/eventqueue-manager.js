$.namespace('azkaban');

$(function() {
    var eventQueueModel = new azkaban.EventQueueModel();
    new azkaban.EventQueueListView({
        el: $("#eventQueueList"),
        model: eventQueueModel
    });

    $("#quickSearchEventQueue").click(function() {
        $("#quickSearchForm").submit();
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
        var searchText = filterXSS($("#searchTextbox").val());
        this.model.set({ search: searchText });
        this.model.set({ page: 1, pageSize: 20 });
        var hash = window.location.hash;
        this.model.set('elDomId','eventQueuePageSelection'); 
        this.createResize();
        // if ("#page" === hash.substring(0, "#page".length)) {
        //     var arr = hash.split("#");
        //     var page;
        //     if ("" !== this.model.get("search").trim() && 1 === this.model.get("pageNum")) {
        //         page = 1;
        //     } else {
        //         page = arr[1].substring("#page".length - 1, arr[1].length);
        //     }
        //     this.model.set({ pageNum: parseInt(page) });
        // } else {
        //     this.model.set({ pageNum: 1 });
        // }
        // this.model.trigger("change:view");
    },
    render: function() {
        var tbody = $("#eventQueueTbody");
        tbody.empty();
        var eventQueueList = this.model.get("eventQueueList");
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

            // rmb
            var tdRmb = document.createElement("td");
            $(tdRmb).text(eq.wemqBizno);
            $(tdTopic).attr("style", "word-break:break-all;width:250px");
            row.appendChild(tdRmb);

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
        var pageNum = this.model.get("page");
        var pageSize = this.model.get("pageSize");
        var searchKey = this.model.get("search");
        var requestURL = "/event/queue";
        var model = this.model;
        var requestData = {
            "ajax": "loadEventQueueData",
            "pageNum": pageNum,
            "pageSize": pageSize,
            "search": searchKey,
            "topic": topic,
            "msgName": msgName,
            "showPageNum": 5
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