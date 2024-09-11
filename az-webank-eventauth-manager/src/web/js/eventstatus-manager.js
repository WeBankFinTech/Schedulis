$.namespace('azkaban');

$(function () {
  var eventStatusModel = new azkaban.EventStatusModel();
  new azkaban.EventStatusListView({
    el: $("#eventStatusList"),
    model: eventStatusModel
  });

  $("#quickSearchEventStatus").click(function () {
    $("#quickSearchForm").submit();
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
    var searchText = filterXSS($("#searchTextbox").val());
    this.model.set({ search: searchText });
    this.model.set({ page: 1, pageSize: 20 });
    this.model.set('elDomId','eventStatusPageSelection'); 
    this.createResize();
    // var hash = window.location.hash;
    // if ("#page" === hash.substring(0, "#page".length)) {
    //   var arr = hash.split("#");
    //   var page;
    //   if ("" !== this.model.get("search").trim() && 1 === this.model.get("pageNum")) {
    //     page = 1;
    //   } else {
    //     page = arr[1].substring("#page".length - 1, arr[1].length);
    //   }
    //   this.model.set({ pageNum: parseInt(page) });
    // } else {
    //   this.model.set({ pageNum: 1 });
    // }
    // this.model.trigger("change:view");
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
    var pageNum = this.model.get("page");
    var pageSize = this.model.get("pageSize");
    var searchKey = this.model.get("search");
    var requestURL = "/event/status";
    var model = this.model;
    var requestData = {
      "ajax": "loadEventStatusData",
      "pageNum": pageNum,
      "pageSize": pageSize,
      "search": searchKey,
      "topic": topic,
      "msgName": msgName
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

