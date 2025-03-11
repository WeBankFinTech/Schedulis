/**
 * Created by zhu on 7/5/18.
 */
 $.namespace('azkaban');
var showDialog = function (title, message) {
  $('#messageTitle').text(title);
  $('#messageBox').text(message);
  $('#messageDialog').modal();
}

var systemTabView;
azkaban.SystemTabView = Backbone.View.extend({
  events: {
    'click #system-user-view-link': 'handleSystemUserViewLinkClick',
    'click #system-deparment-view-link': 'handleSystemDeparmentViewLinkClick',
    'click #system-executor-view-link': 'handleSystemExecutorViewLinkClick',
    'click #system-department-group-view-link': 'handleSystemDepartmentGroupViewLinkClick',
    'click #system-user-modify-list-view-link': 'handleSystemUserModifyViewLinkClick',
    'click #system-department-maintainer-list-view-link': 'handleSystemDepartmentMaintainerViewLinkClick',
    'click #exceptional-user-view-link': 'handleExceptionalUserViewLinkClick'
  },

  initialize: function (settings) {
    var selectedView = settings.selectedView;
    if (selectedView == 'system-deparment') {
      this.handleSystemDeparmentViewLinkClick();
    }
    else {
      this.handleSystemUserViewLinkClick();
    }
  },

  render: function () {
  },

  //系统用户页面
  handleSystemUserViewLinkClick: function () {
    $('#system-deparment-view-link').removeClass('active');
    $('#system-department-group-view-link').removeClass('active');
    $('#system-user-modify-list-view-link').removeClass('active');
    $('#system-department-maintainer-list-view-link').removeClass('active');
    $('#system-deparment-view').hide();
    $('#system-department-group-view').hide();
    $('#system-user-modify-list-view').hide();
    $('#system-department-maintainer-list-view').hide();
    $('#system-user-view-link').addClass('active');
    $('#system-user-view').show();
    $('#exceptional-user-view-link').removeClass('active');
    $('#exceptional-user-view').hide();
    $('#system-executor-view-link').removeClass('active');
    $('#system-executor-view').hide();
    systemUserModel.trigger("change:view");
  },

  //系统用户页面
  handleSystemDeparmentViewLinkClick: function () {
    $('#system-user-view-link').removeClass('active');
    $('#system-department-group-view-link').removeClass('active');
    $('#system-user-modify-list-view-link').removeClass('active');
    $('#system-department-maintainer-list-view-link').removeClass('active');
    $('#system-user-view').hide();
    $('#system-department-group-view').hide();
    $('#system-user-modify-list-view').hide();
    $('#system-department-maintainer-list-view').hide();
    $('#system-deparment-view-link').addClass('active');
    $('#system-deparment-view').show();
    $('#exceptional-user-view-link').removeClass('active');
    $('#exceptional-user-view').hide();
    $('#system-executor-view-link').removeClass('active');
    $('#system-executor-view').hide();
    systemDeparmentModel.trigger("change:view");
  },

  // 执行器管理页面
  handleSystemExecutorViewLinkClick: function () {
    $('#system-user-view-link').removeClass('active');
    $('#system-department-group-view-link').removeClass('active');
    $('#system-user-modify-list-view-link').removeClass('active');
    $('#system-department-maintainer-list-view-link').removeClass('active');
    $('#system-user-view').hide();
    $('#system-department-group-view').hide();
    $('#system-user-modify-list-view').hide();
    $('#system-department-maintainer-list-view').hide();
    $('#system-deparment-view-link').removeClass('active');
    $('#system-deparment-view').hide();
    $('#system-executor-view-link').addClass('active');
    $('#system-executor-view').show();
    $('#exceptional-user-view-link').removeClass('active');
    $('#exceptional-user-view').hide();
    systemExecutorModel.trigger("change:view");
  },

  //系统资源分组管理页面
  handleSystemDepartmentGroupViewLinkClick: function () {
    $('#system-user-view-link').removeClass('active');
    $('#system-deparment-view-link').removeClass('active');
    $('#system-user-modify-list-view-link').removeClass('active');
    $('#system-department-maintainer-list-view-link').removeClass('active');
    $('#system-user-view').hide();
    $('#system-deparment-view').hide();
    $('#system-user-modify-list-view').hide();
    $('#system-department-maintainer-list-view').hide();
    $('#system-department-group-view-link').addClass('active');
    $('#system-department-group-view').show();
    $('#exceptional-user-view-link').removeClass('active');
    $('#exceptional-user-view').hide();
    $('#system-executor-view-link').removeClass('active');
    $('#system-executor-view').hide();
    systemDepartmentGroupModel.trigger("change:view");
  },

  //人员变动清单页面
  handleSystemUserModifyViewLinkClick: function () {
    $('#system-user-view-link').removeClass('active');
    $('#system-deparment-view-link').removeClass('active');
    $('#system-department-group-view-link').removeClass('active');
    $('#system-department-maintainer-list-view-link').removeClass('active');
    $('#system-user-view').hide();
    $('#system-deparment-view').hide();
    $('#system-department-group-view').hide();
    $('#system-department-maintainer-list-view').hide();
    $('#system-user-modify-list-view-link').addClass('active');
    $('#system-user-modify-list-view').show();
    $('#exceptional-user-view-link').removeClass('active');
    $('#exceptional-user-view').hide();
    $('#system-executor-view-link').removeClass('active');
    $('#system-executor-view').hide();
    systemUserModifyListModel.trigger("change:view");
  },

  //部门运维人员清单页面
  handleSystemDepartmentMaintainerViewLinkClick: function () {
    $('#system-user-view-link').removeClass('active');
    $('#system-deparment-view-link').removeClass('active');
    $('#system-department-group-view-link').removeClass('active');
    $('#system-user-modify-list-view-link').removeClass('active');
    $('#system-user-view').hide();
    $('#system-deparment-view').hide();
    $('#system-department-group-view').hide();
    $('#system-user-modify-list-view').hide();
    $('#system-department-maintainer-list-view-link').addClass('active');
    $('#system-department-maintainer-list-view').show();
    $('#exceptional-user-view-link').removeClass('active');
    $('#exceptional-user-view').hide();
    $('#system-executor-view-link').removeClass('active');
    $('#system-executor-view').hide();
    departmentMaintainerListModel.trigger("change:view");
  },

  //例外人员页面
  handleExceptionalUserViewLinkClick: function () {
    $('#system-user-view-link').removeClass('active');
    $('#system-deparment-view-link').removeClass('active');
    $('#system-department-group-view-link').removeClass('active');
    $('#system-user-modify-list-view-link').removeClass('active');
    $('#system-user-view').hide();
    $('#system-deparment-view').hide();
    $('#system-department-group-view').hide();
    $('#system-user-modify-list-view').hide();
    $('#system-department-maintainer-list-view-link').removeClass('active');
    $('#system-department-maintainer-list-view').hide();

    $('#exceptional-user-view-link').addClass('active');
    $('#exceptional-user-view').show();
    $('#system-executor-view-link').removeClass('active');
    $('#system-executor-view').hide();
    exceptionalUserListModel.trigger("change:view");
  },

});


$(function () {
  // 在切换选项卡之前创建模型
  systemUserModel = new azkaban.SystemUserModel();

  systemDeparmentModel = new azkaban.SystemDeparmentModel();

  systemDepartmentGroupModel = new azkaban.SystemDepartmentGroupModel();

  systemUserModifyListModel = new azkaban.SystemUserModifyListModel();

  departmentMaintainerListModel = new azkaban.DepartmentMaintainerListModel();

  exceptionalUserListModel = new azkaban.ExceptionalUserListModel();

  systemExecutorModel = new azkaban.SystemExecutorModel()

  // 用户管理页面视图===start===
  systemTabView = new azkaban.SystemTabView({ el: $('#system-header-tabs') });

  userOptionsView = new azkaban.UserOptionsView({
    el: $('#user-options')
  });

  addSystemUserView = new azkaban.AddSystemUserView({
    el: $('#add-system-user-panel'),
    model: systemUserModel
  });

  updateSystemUserView = new azkaban.UpdateSystemUserView({
    el: $('#update-system-user-panel'),
    model: systemUserModel
  });

  systemUserView = new azkaban.SystemUserView({
    el: $('#system-user-view'),
    model: systemUserModel
  });
  // 用户管理页面视图===end===

  // 部门管理页面视图===start===
  systemDeparmentView = new azkaban.SystemDeparmentView({
    el: $('#system-deparment-view'),
    model: systemDeparmentModel
  });

  deparmentOptionsView = new azkaban.DeparmentOptionsView({
    el: $('#deparment-options'),
    model: systemDeparmentModel
  });

  addDeparmentView = new azkaban.AddDeparmentView({
    el: $('#add-deparment-panel'),
    model: systemDeparmentModel
  });

  updateDeparmentView = new azkaban.UpdateDeparmentView({
    el: $('#update-deparment-panel'),
    model: systemDeparmentModel
  });
  // 部门管理页面视图===end===

  // 执行器管理页面视图===start===
  executorManageView = new azkaban.ExecutorManageView({
    el: $('#system-executor-view'),
    model: systemExecutorModel
  });

  executorOptionsView = new azkaban.ExecutorOptionsView({
    el: $('#system-executor-options'),
    model: systemExecutorModel
  });
  // 执行器管理页面视图===end===

  // 资源分组管理页面视图===start===
  systemDepartmentGroupView = new azkaban.SystemDepartmentGroupView({
    el: $('#system-department-group-view'),
    model: systemDepartmentGroupModel
  });

  departmentGroupOptionsView = new azkaban.DepartmentGroupOptionsView({
    el: $('#department-group-options'),
    model: systemDepartmentGroupModel
  });

  addDepartmentGroupView = new azkaban.AddDepartmentGroupView({
    el: $('#add-department-group-panel'),
    model: systemDepartmentGroupModel
  });

  updateDepartmentGroupView = new azkaban.UpdateDepartmentGroupView({
    el: $('#update-department-group-panel'),
    model: systemDepartmentGroupModel
  });
  // 资源分组管理页面视图===end===

  // 人员变动清单页面视图===start===
  systemUserModifyListView = new azkaban.SystemUserModifyListView({
    el: $('#system-user-modify-list-view'),
    model: systemUserModifyListModel
  });

  modifyUserOptionsView = new azkaban.ModifyUserOptionsView({
    el: $('#modify-user-options'),
    model: systemUserModifyListModel
  });

  modifyWebankUserSyncView = new azkaban.ModifyWebankUserSyncView({
    el: $('#modify-webank-user-sync-panel'),
    model: systemUserModifyListModel
  });
  // 人员变动清单页面视图===end===

  // 部门运维人员页面视图===start===
  addDepMaintainerView = new azkaban.AddDepMaintainerView({
    el: $('#add-department-maintainer-panel'),
    model: departmentMaintainerListModel
  });

  depMaintainerOptionsView = new azkaban.DepMaintainerOptionsView({
    el: $('#department-maintainer-options'),
    model: departmentMaintainerListModel
  });

  updateDepMaintainerView = new azkaban.UpdateDepMaintainerView({
    el: $('#update-department-maintainer-panel'),
    model: departmentMaintainerListModel
  });

  departmentMaintainerListView = new azkaban.DepartmentMaintainerListView({
    el: $('#system-department-maintainer-list-view'),
    model: departmentMaintainerListModel
  });
  // 部门运维人员页面视图===end===

  // exceptional-user-manager
  addExceptionalUserView = new azkaban.AddExceptionalUserView({
    el: $('#add-exceptional-user-panel'),
    model: exceptionalUserListModel
  });

  exceptionalUserView = new azkaban.ExceptionalUserView({
    el: $('#exceptional-user-view'),
    model: exceptionalUserListModel
  });
  // exceptional-user-manager

  if (window.location.hash) {//浏览器输入对于的链接时跳转到对应的Tab页
    var hash = window.location.hash;
    if (hash.indexOf('#system-user') != -1) {
      //if ("#page" == hash.substring(0, "#page".length)) {
      if (hash.indexOf("#page") != -1) {
        var page = hash.substring("#system-user#page".length, hash.length);
        console.log("page " + page);
        systemUserModel.set({ "page": parseInt(page) });
        systemDeparmentModel.set({ "page": parseInt(page) });
        systemTabView.handleSystemUserViewLinkClick();
      } else {
        systemTabView.handleSystemUserViewLinkClick();
      }
    } else if (hash.indexOf('#system-deparment') != -1) {
      //if ("#page" == hash.substring(0, "#system-deparment#page".length)) {
      if (hash.indexOf("#page") != -1) {
        var page = hash.substring("#system-deparment#page".length, hash.length);
        console.log("page " + page);
        systemDeparmentModel.set({ "page": parseInt(page) });
        systemTabView.handleSystemDeparmentViewLinkClick();
      } else {
        systemTabView.handleSystemDeparmentViewLinkClick();
      }
    } else if (hash.indexOf('#system-executor') != -1) {
      //if ("#page" == hash.substring(0, "#system-deparment#page".length)) {
      if (hash.indexOf("#page") != -1) {
        var page = hash.substring("#system-executor#page".length, hash.length);
        console.log("page " + page);
        systemExecutorModel.set({ "page": parseInt(page) });
        systemTabView.handleSystemExecutorViewLinkClick();
      } else {
        systemTabView.handleSystemExecutorViewLinkClick();
      }
    } else if (hash.indexOf('#department-group') != -1) {
      if (hash.indexOf("#page") != -1) {
        var page = hash.substring("#department-group#page".length, hash.length);
        console.log("page " + page);
        systemDepartmentGroupModel.set({ "page": parseInt(page) });
        systemTabView.handleSystemDepartmentGroupViewLinkClick();
      } else {
        systemTabView.handleSystemDepartmentGroupViewLinkClick();
      }
    } else if (hash.indexOf('#modify-user-list') != -1) {
      if (hash.indexOf("#page") != -1) {
        var page = hash.substring("#modify-user-list#page".length, hash.length);
        console.log("page " + page);
        systemUserModifyListModel.set({ "page": parseInt(page) });
        systemTabView.handleSystemUserModifyViewLinkClick();
      } else {
        systemTabView.handleSystemUserModifyViewLinkClick();
      }
    } else if (hash.indexOf('#department-maintainer-list') != -1) {
      if (hash.indexOf("#page") != -1) {
        var page = hash.substring("#department-maintainer-list#page".length, hash.length);
        console.log("page " + page);
        departmentMaintainerListModel.set({ "page": parseInt(page) });
        systemTabView.handleSystemDepartmentMaintainerViewLinkClick();
      } else {
        systemTabView.handleSystemDepartmentMaintainerViewLinkClick();
      }
    } else if (hash.indexOf('#exceptional-user') != -1) {
      if (hash.indexOf("#page") != -1) {
        var page = hash.substring("#exceptional-user-page#page".length, hash.length);
        console.log("page " + page);
        exceptionalUserListModel.set({ "page": parseInt(page) });
        systemTabView.handleExceptionalUserViewLinkClick();
      } else {
        systemTabView.handleExceptionalUserViewLinkClick();
      }
    }
  } else {
    window.location.href = "/system#system-user";
    systemTabView.handleSystemUserViewLinkClick();
  }

  webankUserSyncView = new azkaban.WebankUserSyncView({
    el: $('#webank-user-sync-panel'),
  });

  $("#webank-user-select").on('select2:select', function (e) {
    //非自定义用户 则关联部门展示
    var dpId = e.params.data.dpId;
    var dpName = e.params.data.dpName;
    var email = e.params.data.email;
    if (dpId) {
      $("#webank-department-select2").val(dpId)
    }
    if (email) {
      $("#add-system-user-panel [name=email]").val(email);
    }
  });

  // 非谷歌浏览器，系统用户密码改成type改成password
  if (navigator.userAgent.indexOf('Chrome') === -1) {
    document.getElementById('password').setAttribute('type', 'password');
    document.getElementById('update-password').setAttribute('type', 'password');
  }
});

// 以下用于保存浏览数据，切换页面也能返回之前的浏览进度。
var systemUserModel;
azkaban.SystemUserModel = Backbone.Model.extend({});

var systemDeparmentModel;
azkaban.SystemDeparmentModel = Backbone.Model.extend({});

var systemExecutorModel;
azkaban.SystemExecutorModel = Backbone.Model.extend({});

var systemDepartmentGroupModel;
azkaban.SystemDepartmentGroupModel = Backbone.Model.extend({});

var systemUserModifyListModel;
azkaban.SystemUserModifyListModel = Backbone.Model.extend({});

var departmentMaintainerListModel;
azkaban.DepartmentMaintainerListModel = Backbone.Model.extend({});

var exceptionalUserListModel;
azkaban.ExceptionalUserListModel = Backbone.Model.extend({});

