/**
 * Created by zhu on 7/5/18.
 */

 $.namespace('azkaban');
var showDialog = function (title, message) {
  $('#messageTitle').text(title);
  $('#messageBox').text(message);
  $('#messageDialog').modal();
}

var batchTabView;
azkaban.BatchTabView = Backbone.View.extend({
  events: {
    'click #batch-hold-view-link': 'handleBatchHoldViewLinkClick'
  },

  initialize: function (settings) {
    loadBusPathData();
    loadSubSystemData();
    // this.handleBatchHoldViewLinkClick();
  },

  render: function () {
  },

  //系统用户页面
  handleBatchHoldViewLinkClick: function () {
    $('#batch-hold-view-link').addClass('active');
    $('#batch-hold-view').show();
    // 快速查询，高寄过滤参数设置为空
    batchHoldModel.set("advQueryParam", null);
    batchHoldModel.trigger("change:view");
  },

//   loadBusPathData: function () {
//     var requestURL = "/manager";
//     var requestData = {
//       "ajax": "getCmdbData",
//       "type": "wb_batch_critical_path",
//       "id": "id",
//       "name": "name",
//       start: 0,
//       size: 200000
//     };
//     var successHandler = function (data) {
//       if (data.error) {
//         console.log(data.error);
//       }else {
//         if (data.dataList) {
//           for (var i = 0; i < data.dataList.length; i++) {
//             busPathMap[data.dataList[i]['id']]=data.dataList[i]['name'];
//           }
//         }
//       }
//     }

//     $.ajax({
//       url: requestURL,
//       type: "get",
//       async: true,
//       data: requestData,
//       dataType: "json",
//       error: function (data) {
//         console.log(data);
//       },
//       success: successHandler
//     });
//   },

//   loadSubSystemData: function () {
//     var requestURL = "/manager";
//     var requestData = {
//       "ajax": "getCmdbData",
//       "type": "wb_subsystem",
//       "id": "subsystem_id",
//       "name": "subsystem_name"
//     };
//     var successHandler = function (data) {
//       if (data.error) {
//         console.log(data.error);
//       }else {
//         if (data.dataList) {
//           for (var i = 0; i < data.dataList.length; i++) {
//             subsystemMap[data.dataList[i]['subsystem_id']]=data.dataList[i]['subsystem_name'];
//           }
//         }
//       }
//     }

//     $.ajax({
//       url: requestURL,
//       type: "get",
//       async: false,
//       data: requestData,
//       dataType: "json",
//       error: function (data) {
//         console.log(data);
//       },
//       success: successHandler
//     });
//   }

});


$(function () {
  // 在切换选项卡之前创建模型
  batchHoldModel = new azkaban.BatchHoldModel();

  // 用户管理页面视图===start===
  batchTabView = new azkaban.BatchTabView({ el: $('#batch-header-tabs') });

  holdOptionsView = new azkaban.HoldOptionsView({
    el: $('#hold-options'),
    model: batchHoldModel
  });

  advFilterView = new azkaban.AdvFilterView({
    el: $('#adv-filter'),
    model: batchHoldModel
  });

  holdBatchPanel = new azkaban.HoldBatchPanel({
    el: $('#hold-batch-panel'),
    model: batchHoldModel
  });

  resumeBatchPanel = new azkaban.ResumeBatchPanel({
    el: $('#resume-batch-panel'),
    model: batchHoldModel
  });

  batchHoldView = new azkaban.BatchHoldView({
    el: $('#batch-hold-view'),
    model: batchHoldModel
  });
  // 用户管理页面视图===end===

  if (window.location.hash) {//浏览器输入对于的链接时跳转到对应的Tab页
    var hash = window.location.hash;
    if (hash.indexOf('#batch-hold') != -1) {
      //if ("#page" == hash.substring(0, "#page".length)) {
      if (hash.indexOf("#page") != -1) {
        var page = hash.substring("#batch-hold#page".length, hash.length);
        console.log("page " + page);
        batchHoldModel.set({ "page": parseInt(page) });
        batchTabView.handleBatchHoldViewLinkClick();
      } else {
        batchTabView.handleBatchHoldViewLinkClick();
      }
    }
  } else {
    window.location.href = "/batch#batch-hold";
    batchTabView.handleBatchHoldViewLinkClick();
  }

});

// 以下用于保存浏览数据，切换页面也能返回之前的浏览进度。
var batchHoldModel;
azkaban.BatchHoldModel = Backbone.Model.extend({});

var holdLevelMap = {
  '0': wtssI18n.common.globalHOLDBatch,
  '1': wtssI18n.common.tenantLevel,
  '2': wtssI18n.common.userLevel,
  '3': wtssI18n.common.custom,
};

var resumeStatusMap = {
  '0': wtssI18n.common.unrecovered,
  '1': wtssI18n.common.restored,
  '2': wtssI18n.common.terminated,
};

var alertStatusMap = {
  '0': wtssI18n.common.unsent,
  '1': wtssI18n.common.success,
  '2': wtssI18n.common.fail,
};

var yesOrNoMap = {
  '0': wtssI18n.common.no,
  '1': wtssI18n.common.yes
};

