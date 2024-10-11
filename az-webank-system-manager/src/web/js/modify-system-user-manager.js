/**
 * Created by zhangxi on 8/7/19.
 */

// 人员变动清单主页面视图
var systemUserModifyListView;
azkaban.SystemUserModifyListView = Backbone.View.extend({
  events: {
    "click #modify-system-user-pageSelection li": "handleChangePageSelection",
    "change #modify-system-user-pageSelection .pageSizeSelect": "handlePageSizeSelection",
    "click #modify-system-user-pageSelection .pageNumJump": "handlePageNumJump",
  },

  initialize: function (settings) {
    this.model.bind('change:view', this.handleChangeView, this);
    this.model.bind('render', this.render, this);
    this.model.set({ page: 1, pageSize: 20 });
    this.model.bind('change:page', this.handlePageChange, this);
    this.model.set('elDomId','modify-system-user-pageSelection'); 
    this.createResize();
  },

  render: function (evt) {
    console.log("render");
    // Render page selections
    var tbody = $("#modifyUserTableBody");
    tbody.empty();

    var users = this.model.get("modifySystemUserPageList");
    if (!users || users.length == 0) {
      $("#modify-system-user-pageSelection").hide();
    } else {
      $("#modify-system-user-pageSelection").show();
    }

    // 组装数据内容
    for (var i = 0; i < users.length; ++i) {
      var row = document.createElement("tr");

      //组装数字行
      var tdNum = document.createElement("td");
      $(tdNum).text(i + 1);
      $(tdNum).attr("class", "tb-name");
      row.appendChild(tdNum);

      //组装用户ID行
      var tdUserId = document.createElement("td");
      $(tdUserId).text(users[i].userId);
      row.appendChild(tdUserId);

      //组装用户全名行
      var tdFullName = document.createElement("td");
      $(tdFullName).text(users[i].fullName);
      $(tdFullName).attr("style", "word-break:break-all;");
      row.appendChild(tdFullName);

      //组装用户部门行
      var tdDep = document.createElement("td");
      $(tdDep).text(users[i].departmentName);
      $(tdDep).attr("style", "word-break:break-all;");
      row.appendChild(tdDep);

      //组装代理用户行
      var tdProxyUser = document.createElement("td");
      $(tdProxyUser).text(users[i].proxyUsers);
      $(tdProxyUser).attr("style", "word-break:break-all;width:250px");
      row.appendChild(tdProxyUser);

      //组装用户角色行
      var tdRole = document.createElement("td");
      $(tdRole).text(users[i].role);
      row.appendChild(tdRole);

      //组装用户权限行
      var tdPermission = document.createElement("td");
      $(tdPermission).text(users[i].permission);
      $(tdPermission).attr("style", "word-break:break-all;width:300px");
      row.appendChild(tdPermission);

      //组装用户邮箱行
      var tdEmail = document.createElement("td");
      $(tdEmail).attr("style", "word-break:break-all;max-width:350px");
      $(tdEmail).text(users[i].email);
      row.appendChild(tdEmail);

      //组装变更类型行
      var tdModifyType = document.createElement("td");
      $(tdModifyType).text(users[i].modifyType);
      $(tdPermission).attr("style", "word-break:break-all;width:330px");
      row.appendChild(tdModifyType);
      tbody.append(row);
    }

    this.renderPagination(evt);
  },

  ...commonPaginationFun(),

  handlePageChange: function (evt) {
    var start = this.model.get("page") - 1;
    var pageSize = this.model.get("pageSize");
    var requestURL = "/system";
    var searchterm = this.model.get("searchterm");
    if (!searchterm) {
      searchterm = "";
    }

    var model = this.model;
    var requestData = {
      "ajax": "findModifySystemUserPage",
      "start": start,
      "pageSize": pageSize,
      "searchterm": searchterm,
    };
    var successHandler = function (data) {
      model.set({
        "modifySystemUserPageList": data.modifySystemUserPageList,
        "total": data.total
      });
      model.trigger("render");
    };
    $.get(requestURL, requestData, successHandler, "json");
  },

});

// 人员变动清单选项相关页面
var modifyUserOptionsView;
azkaban.ModifyUserOptionsView = Backbone.View.extend({
  events: {
    "click #syncModifyWebankUserBtn": "handleSyncModifyWebankUser",
    "click #search-modify-user-btn": "handleSearchModifyUser",
    "click #downloadModifySystemUserBtn": "handleModifyUserInfoDownload",
  },

  initialize: function (settings) {
  },

  handleSyncModifyWebankUser: function (evt) {
    console.log("click modify webank user sync");
    $('#modify-webank-user-sync-panel').modal();
    modifyWebankUserSyncView.render();
  },

  handleSearchModifyUser: function () {
    var searchterm = $('#serarch-modify-user').val();
    systemUserModifyListModel.set({ "searchterm": searchterm });
    systemTabView.handleSystemUserModifyViewLinkClick();
  },

  handleModifyUserInfoDownload: function () {
    window.location.href = "/system/downloadModifyInfo";
  },

  render: function () {
  }
});

// 人员变动清单选项,同步变动用户数据模态框
var modifyWebankUserSyncView;
azkaban.ModifyWebankUserSyncView = Backbone.View.extend({
  events: {
    "click #modify-webank-user-sync-btn": "handleModifyWebankUserSync",
  },

  initialize: function (settings) {
    $("#modify-webank-user-sync-error-msg").hide();
    $("#modify-webank-user-sync-success-msg").hide();
  },

  handleModifyWebankUserSync: function (evt) {

    $('#modify-webank-user-sync-progress').prop("class", "flow-progress-bar main-progress RUNNING");

    var requestData = {
      "ajax": "syncModifyEsbSystemUsers",
    };
    var successHandler = function (data) {
      if (data.error) {
        $("#modify-webank-user-sync-error-msg").show();
        $("#modify-webank-user-sync-error-msg").text(data.error);
        $('#modify-webank-user-sync-progress').prop("class", "flow-progress-bar main-progress FAILED");
        document.getElementById('modify-webank-user-sync-btn').disabled = true;
        setTimeout(function () {
          window.location.reload();
        }, 1000);

      } else {
        $("#modify-webank-user-sync-success-msg").show();
        $("#modify-webank-user-sync-success-msg").text(data.message);
        $('#modify-webank-user-sync-progress').prop("class", "flow-progress-bar main-progress SUCCEEDED");
        document.getElementById('modify-webank-user-sync-btn').disabled = true;
        setTimeout(function () {
          window.location.reload();
        }, 1000);
      }

    };
    $.get('', requestData, successHandler, "json");

  },

  render: function () {
    $("#modify-webank-user-sync-error-msg").hide();
    $("#modify-webank-user-sync-success-msg").hide();
    $('#modify-webank-user-sync-progress').prop("class", "");
  },

});