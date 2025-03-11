/**
 * Created by zhu on 7/5/18.
 */


//处理方法 组装表格和翻页处理
var systemUserView;
azkaban.SystemUserView = Backbone.View.extend({
  events: {
    "click #pageSelection li": "handleChangePageSelection",
    "change #pageSelection .pageSizeSelect": "handlePageSizeSelection",
    "click #pageSelection .pageNumJump": "handlePageNumJump",
    "click .btn-info": "handleUpdateSystemUserBtn",
  },

  initialize: function (settings) {
    this.model.bind('change:view', this.handleChangeView, this);
    this.model.bind('render', this.render, this);
    this.model.set({ page: 1, pageSize: 20 });
    this.model.bind('change:page', this.handlePageChange, this);
    this.model.set('elDomId','pageSelection');
    this.createResize();
  },

  render: function (evt) {
    console.log("render");
    // Render page selections
    var tbody = $("#userTableBody");
    tbody.empty();

    var users = this.model.get("systemUserPageList");
    var modifyI18n = this.model.get("modify");
    if (!users || users.length == 0) {
      $("#pageSelection").hide()
    } else {
      $("#pageSelection").show()
    }


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

      //组装用户行
      var tdPermission = document.createElement("td");
      $(tdPermission).text(users[i].permission);
      $(tdPermission).attr("style", "word-break:break-all;width:350px");
      row.appendChild(tdPermission);

      //组装用户邮箱行
      var tdEmail = document.createElement("td");
      $(tdEmail).attr("style", "word-break:break-all;max-width:350px");
      $(tdEmail).text(users[i].email);
      row.appendChild(tdEmail);

      //组装操作行
      var tdAction = document.createElement("td");
      var updateBtn = document.createElement("button");
      $(updateBtn).attr("id", users[i].userId + "updateBtn");
      $(updateBtn).attr("name", users[i].userId);
      $(updateBtn).attr("class", "btn btn-sm btn-info");
      $(updateBtn).text(modifyI18n);
      tdAction.appendChild(updateBtn);
      row.appendChild(tdAction);

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
      "ajax": "findSystemUserPage",
      "start": start,
      "pageSize": pageSize,
      "searchterm": searchterm,
    };
    var successHandler = function (data) {
      model.set({
        "systemUserPageList": data.systemUserPageList,
        "modify": data.modify,
        "total": data.total
      });
      model.trigger("render");
    };
    $.get(requestURL, requestData, successHandler, "json");
  },

  handleUpdateSystemUserBtn: function (evt) {
    console.log("click upload project");

    var userId = evt.currentTarget.name;
    systemUserModel.set({ "updateUserId": userId });
    $('#update-system-user-panel').modal();
    updateSystemUserView.render();
    updateSystemUserView.loadWtssUserData();
  },


});

var userOptionsView;
azkaban.UserOptionsView = Backbone.View.extend({
  events: {
    "click #addSystemUser": "handleAddSystemUser",
    "click #syncWebankUserBtn": "handleSyncWebankUser",
    "click #syncXmlUserBtn": "handleXmlUserBtn",
    "click #search-user-btn": "handleSearchUser",
  },

  initialize: function (settings) {
  },
  initCreateForm: function() {
    $("#webank-user-select").empty();
    $("#password").val("");
    $("#user-role-select").val("0");
    $("#user-category-select").val("0");
    $("#proxy-user").val("");
    $("#webank-department-select2").val("0");
    $("#add-system-user-panel [name=email]").val("");
  },
  handleAddSystemUser: function (evt) {
    console.log("click upload project");
    this.initCreateForm();
    $('#add-system-user-panel').modal();
    addSystemUserView.render();
  },

  handleSyncWebankUser: function (evt) {
    console.log("click webank user sync");
    $('#webank-user-sync-panel').modal();
    webankUserSyncView.render();
  },

  handleXmlUserBtn: function (evt) {
    console.log("click webank user sync");
    var requestURL = "/system";

    var requestData = {
      "ajax": "syncXmlUsers",
    };
    var successHandler = function (data) {
      if (data.error) {
        alert(data.error);
        return false;
      } else {
        window.location.href = "/system#system-user";
      }
    };
    $.get(requestURL, requestData, successHandler, "json");
  },

  handleSearchUser: function () {
    var searchterm = filterXSS($('#serarch-user').val());
    systemUserModel.set({ "searchterm": searchterm });

    //systemUserModel.trigger("change:page");

    systemTabView.handleSystemUserViewLinkClick();
  },

  render: function () {
  }
});



var addSystemUserView;
azkaban.AddSystemUserView = Backbone.View.extend({
  events: {
    "click #system-user-create-btn": "handleAddSystemUser"
  },

  initialize: function (settings) {
    console.log("Hide modal error msg");
    $("#add-user-modal-error-msg").hide();
    this.loadWebankUserData();
  },

  handleAddSystemUser: function (evt) {
    console.log("Add System User button.");
    var userId = $("#webank-user-select").val();
    var password = $("#password").val();
    var roleId = $("#user-role-select").val();
    var categoryUser = $("#user-category-select").val();
    var proxyUser = $("#proxy-user").val();
    var departmentId = $("#webank-department-select2").val();
    var email = $("#add-system-user-panel [name=email]").val();
    var requestURL = "/system";

    if (null == userId) {
      alert(wtssI18n.system.userPro);
      return;
    }

    if (!roleId || "0" == roleId) {
      alert(wtssI18n.system.rolePro);
      return;
    }

    if (!departmentId || "0" == departmentId) {
      alert(wtssI18n.system.departmentPro);
      return;
    }
    if (!categoryUser ||"0" == categoryUser) {
      alert(wtssI18n.system.userCatePro);
      return;
    }

    var model = this.model;
    var requestData = {
      "ajax": "addSystemUser",
      "userId": userId,
      "password": password,
      "roleId": roleId,
      "proxyUser": proxyUser,
      "departmentId": departmentId,
      "categoryUser": categoryUser,
      "email": email
    };
    var successHandler = function (data) {
      if (data.error) {
        $("#add-user-modal-error-msg").show();
        $("#add-user-modal-error-msg").text(data.error.message);
        return false;
      } else {
        window.location.href = "/system";
      }
      model.trigger("render");
    };
    $.get(requestURL, requestData, successHandler, "json");
  },

  loadWebankUserData: function () {

    $("#webank-user-select").select2({
      placeholder: wtssI18n.system.userPro,//默认文字提示
      multiple: false,
      width: 'resolve',
      //language: "zh-CN",
      tags: true,//允许手动添加
      //allowClear: true,//允许清空
      escapeMarkup: function (markup) { return markup; }, //自定义格式化防止XSS注入
      minimumInputLengt: 1,//最少输入多少字符后开始查询
      formatResult: function formatRepo (repo) { return repo.text; },//函数用来渲染结果
      formatSelection: function formatRepoSelection (repo) { return repo.text; },//函数用于呈现当前的选择
      ajax: {
        type: 'GET',
        url: "/system",
        dataType: 'json',
        delay: 250,
        data: function (params) {
          var query = {
            ajax: "loadWebankUserSelectData",
            serach: params.term,
            page: params.page || 1,
            pageSize: 20,
          }
          return query;
        },
        processResults: function (data, params) {
          params.page = params.page || 1;
          return {
            results: data.webankUserList,
            pagination: {
              more: (params.page * 20) < data.webankUserTotalCount
            }
          }
        },
        cache: true
      },
      language: 'zh-CN',


    });
  },

  render: function () {
    $("#add-user-modal-error-msg").hide();
  },
});

var updateSystemUserView;
azkaban.UpdateSystemUserView = Backbone.View.extend({
  events: {
    "click #system-user-update-btn": "handleUpdateSystemUser",
    "click #system-user-delete-btn": "handleDeleteSystemUser"
  },

  initialize: function (settings) {
    console.log("Hide modal error msg");
    $("#update-user-modal-error-msg").hide();
    this.loadWebankDepartmentData(this.renderDepartmentOption);
  },

  handleUpdateSystemUser: function (evt) {
    console.log("Update System User button.");
    var userId = $("#wtss-user-id").val();
    var password = $("#update-password").val();
    var categoryUser = $("#wtss-user-category").attr('userType');
    var roleId = $("#update-user-role-select").val();
    var proxyUser = $("#update-proxy-user").val();
    var departmentId = $("#update-wtss-department-select").val();
    var email = $("#update-system-user-panel [name=email]").val();
    var requestURL = "/system";

    if (!roleId || "0" == roleId) {
      alert(wtssI18n.system.rolePro);
      return;
    }

    if (!departmentId || "0" == departmentId) {
      alert(wtssI18n.system.departmentPro);
      return;
    }

    var model = this.model;
    var requestData = {
      "ajax": "updateSystemUser",
      "userId": userId,
      "categoryUser": categoryUser,
      "password": password,
      "roleId": roleId,
      "proxyUser": proxyUser,
      "departmentId": departmentId,
      "email": email
    };
    var successHandler = function (data) {
      if (data.error) {
        $("#update-user-modal-error-msg").show();
        $("#update-user-modal-error-msg").text(data.error);
        return false;
      } else {
        window.location.href = "/system";
      }
      model.trigger("render");
    };
    $.get(requestURL, requestData, successHandler, "json");
  },

  handleDeleteSystemUser: function (evt) {
    deleteDialogView.show(wtssI18n.deletePro.deleteUser, wtssI18n.deletePro.whetherDeleteUser, wtssI18n.common.cancel, wtssI18n.common.delete, '', function() {
        console.log("Delete System User button.");
        var userId = $("#wtss-user-id").val();
        var requestURL = "/system";

        var model = this.model;
        var requestData = {
        "ajax": "deleteSystemUser",
        "userId": userId,
        };
        var successHandler = function (data) {
        if (data.error) {
            $("#update-user-modal-error-msg").show();
            $("#update-user-modal-error-msg").text(data.error.message);
            return false;
        } else {
            window.location.href = "/system";
        }
        model.trigger("render");
        };
        $.get(requestURL, requestData, successHandler, "json");
    });
  },

  loadWtssUserData: function () {

    var userId = this.model.get("updateUserId");
    var requestURL = "/system";

    var requestData = {
      "ajax": "getSystemUserById",
      "userId": userId,
    };
    var successHandler = function (data) {
      if (data.error) {
        $("#update-user-modal-error-msg").show();
        $("#update-user-modal-error-msg").text(data.error.message);
        return false;
      } else {
        var showUserCategory = "";
        if (data.systemUser.userCategory) {
          if ("ops" == data.systemUser.userCategory) {
            if (data.languageType == "zh_CN") {
              showUserCategory = "运维用户";
            } else {
              showUserCategory = "OPS User";
            }
          }
          if ("system" == data.systemUser.userCategory) {
            if (data.languageType == "zh_CN") {
              showUserCategory = "系统用户";
            } else {
              showUserCategory = "System User";
            }
          }
          if ("personal" == data.systemUser.userCategory) {
            if (data.languageType == "zh_CN") {
              showUserCategory = "实名用户";
            } else {
              showUserCategory = "Real-Name User";
            }
          }
          if ("test" == data.systemUser.userCategory) {
            if (data.languageType == "zh_CN") {
              showUserCategory = "临时测试用户";
            } else {
              showUserCategory = "User for Test";
            }
          }
        }
        const departPathMap = addSystemUserView.model.get("departPathMap");
        if (!departPathMap[data.systemUser.departmentId]) {
            messageBox.show(`${data.systemUser.departmentName}不存在，请重新选择用户部门`,"warning");
            data.systemUser.departmentId = "";
        }

        $("#wtss-user-id").val(data.systemUser.userId);
        $("#wtss-full-name").val(data.systemUser.fullName);
        $("#wtss-user-category").val(showUserCategory).attr('userType', data.systemUser.userType);
        $("#update-password").val(data.systemUser.password);
        $("#update-user-role-select").val(data.systemUser.roleId);
        $("#update-proxy-user").val(data.systemUser.proxyUsers);
        $("#update-wtss-department-select").val(data.systemUser.departmentId);
        $("#update-system-user-panel [name=email]").val(data.systemUser.email);
      }
    };
    $.get(requestURL, requestData, successHandler, "json");

  },
  renderDepartmentOption: function (){
    var subsystemMap = addSystemUserView.model.get("departPathMap");
    var optionHtml = ""
    for (var key in subsystemMap) {
        optionHtml += "<option value='" + key + "'>" + subsystemMap[key] + "</option>"
    }
    optionHtml = filterXSS(optionHtml, { 'whiteList': { 'option': ['value'] } })
    $('#webank-department-select2').empty().append(optionHtml);
    $('#update-wtss-department-select').empty().append(optionHtml);
    $('#dep-for-select-add-maintainer').empty().append(optionHtml);
  },
  loadWebankDepartmentData: loadDepartmentData,

  render: function () {
    $("#update-user-modal-error-msg").hide();
  },
});


var webankUserSyncView;
azkaban.WebankUserSyncView = Backbone.View.extend({
  events: {
    "click #webank-user-sync-btn": "handleWebankUserSync",
  },

  initialize: function (settings) {
    $("#webank-user-sync-error-msg").hide();
    $("#webank-user-sync-success-msg").hide();
  },

  handleWebankUserSync: function (evt) {
    //console.log("click webank user sync");
    //$('#webank-user-sync-panel').modal();

    $('#webank-user-sync-progress').prop("class", "flow-progress-bar main-progress RUNNING");

    var requestData = {
      "ajax": "syncWebankUsers",
    };
    var successHandler = function (data) {
      if (data.error) {
        $("#webank-user-sync-error-msg").show();
        $("#webank-user-sync-error-msg").text(data.error);
        $('#webank-user-sync-progress').prop("class", "flow-progress-bar main-progress FAILED");
      } else {
        $("#webank-user-sync-success-msg").show();
        $("#webank-user-sync-success-msg").text(data.message);
        $('#webank-user-sync-progress').prop("class", "flow-progress-bar main-progress SUCCEEDED");
      }

    };
    $.get('', requestData, successHandler, "json");

  },

  render: function () {
    $("#webank-user-sync-error-msg").hide();
    $("#webank-user-sync-success-msg").hide();
    $('#webank-user-sync-progress').prop("class", "");
  },

});

