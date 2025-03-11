$.namespace('azkaban');

$(function () {
  var eventStatusModel = new azkaban.EventStatusModel();
  new azkaban.EventStatusListView({
    el: $("#eventStatusList"),
    model: eventStatusModel
  });

    $("#quickSearchEventStatus").on("click", function() {
        eventStatusModel.trigger("change:page");
    });
    $("#searchTextbox").on("keyup", function() {
        if (e.keyCode === 13) {
            eventStatusModel.trigger("change:page");
        }
  });
});

azkaban.EventStatusModel = Backbone.Model.extend({});

azkaban.EventStatusListView = Backbone.View.extend({
  events: {
    "click #eventStatusPageSelection li": "handleChangePageSelection",
    "change #eventStatusPageSelection .pageSizeSelect": "handlePageSizeSelection",
    "click #eventStatusPageSelection .pageNumJump": "handlePageNumJump",
  },
  initialize: function () {
    this.model.bind('change:view', this.handleChangeView, this);
    this.model.bind('render', this.render, this);
    this.model.bind('change:page', this.handlePageChange, this);
    this.setMessageParams();
    this.model.set({ page: 1, pageSize: 20 });
    this.model.set('elDomId','eventStatusPageSelection'); 
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
  render: function () {
    var tbody = $("#eventStatusTbody");
    tbody.empty();
    var eventStatusList = this.model.get("eventStatusList");
    if (!eventStatusList || eventStatusList.length == 0) {
      $("#eventStatusPageSelection").hide();
    } else {
      $("#eventStatusPageSelection").show();
    }
    eventStatusList.forEach(function (es, i) {
      var row = document.createElement("tr");
      var tdNum = document.createElement("td");
      $(tdNum).text(i + 1);
      $(tdNum).attr("class", "tb-name");
      row.appendChild(tdNum);

      var tdReceiver = document.createElement("td");
      $(tdReceiver).text(es.receiver);
      row.appendChild(tdReceiver);

      var tdTopic = document.createElement("td");
      $(tdTopic).text(es.topic);
      $(tdTopic).attr("style", "word-break:break-all;width:250px");
      row.appendChild(tdTopic);

      var tdMsgName = document.createElement("td");
      $(tdMsgName).text(es.msgName);
      $(tdMsgName).attr("style", "word-break:break-all;width:250px");
      row.appendChild(tdMsgName);

      var tdReceiveTime = document.createElement("td");
      $(tdReceiveTime).text(es.receiveTime);
      $(tdReceiveTime).attr("style", "word-break:break-all;width:250px");
      row.appendChild(tdReceiveTime);

      var tdConsumeID = document.createElement("td");
      $(tdConsumeID).text(es.msgId);
      $(tdConsumeID).attr("style", "word-break:break-all;width:250px");
      row.appendChild(tdConsumeID);

      tbody.append(row);
    });
    this.renderPagination();
    $("#eventStatusTable").trigger("update")
  },
  ...commonPaginationFun(),
  handlePageChange: function () {
    const model = this.model;
    const pageNum = model.get("page");
    const pageSize = model.get("pageSize");
    const searchKey = $("#searchTextbox").val();
    const requestURL = "/event/status";
    
    var requestData = {
      "ajax": "loadEventStatusData",
      "pageNum": pageNum,
      "pageSize": pageSize,
      "search": searchKey,
        "topic": model.get("topic"),
        "msgName": model.get("msgName"),
        authType: model.get("type") === "auth",
    };
    var successHandler = function (data) {
      model.set({
        "eventStatusList": data.eventStatusList,
        "total": data.total
      });
      model.trigger("render");
    };
    $.get(requestURL, requestData, successHandler, "json");
  }
});

