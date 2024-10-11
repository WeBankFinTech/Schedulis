
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
    'click #system-department-group-view-link': 'handleSystemDepartmentGroupViewLinkClick'
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
  handleSystemUserViewLinkClick: function() {
    $('#system-deparment-view-link').removeClass('active');
    $('#system-department-group-view-link').removeClass('active');
    $('#system-deparment-view').hide();
    $('#system-department-group-view').hide();
    $('#system-user-view-link').addClass('active');
    $('#system-user-view').show();
    systemUserModel.trigger("change:view");
  },

  //系统用户页面
  handleSystemDeparmentViewLinkClick: function() {
    $('#system-user-view-link').removeClass('active');
    $('#system-department-group-view-link').removeClass('active');
    $('#system-user-view').hide();
    $('#system-department-group-view').hide();
    $('#system-deparment-view-link').addClass('active');
    $('#system-deparment-view').show();
    systemDeparmentModel.trigger("change:view");
  },

  //系统资源分组管理页面
  handleSystemDepartmentGroupViewLinkClick: function() {
    $('#system-user-view-link').removeClass('active');
    $('#system-deparment-view-link').removeClass('active');
    $('#system-user-view').hide();
    $('#system-deparment-view').hide();
    $('#system-department-group-view-link').addClass('active');
    $('#system-department-group-view').show();
    systemDepartmentGroupModel.trigger("change:view");
  },

});


$(function() {
  // 在切换选项卡之前创建模型
  systemUserModel = new azkaban.SystemUserModel();

  systemDeparmentModel = new azkaban.SystemDeparmentModel();

  systemDepartmentGroupModel = new azkaban.SystemDepartmentGroupModel();

  // 用户管理页面视图===start===
  systemTabView = new azkaban.SystemTabView({el: $('#system-header-tabs')});

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


  if (window.location.hash) {//浏览器输入对于的链接时跳转到对应的Tab页
    var hash = window.location.hash;
    if(hash.indexOf('#system-user') != -1){
      //if ("#page" == hash.substring(0, "#page".length)) {
      if (hash.indexOf("#page") != -1) {
        var page = hash.substring("#system-user#page".length, hash.length);
        console.log("page " + page);
        systemUserModel.set({"page": parseInt(page)});
        systemDeparmentModel.set({"page": parseInt(page)});
        systemTabView.handleSystemUserViewLinkClick();
      }else{
        systemTabView.handleSystemUserViewLinkClick();
      }
    }else if (hash.indexOf('#system-deparment') != -1) {
      //if ("#page" == hash.substring(0, "#system-deparment#page".length)) {
      if (hash.indexOf("#page") != -1) {
        var page = hash.substring("#system-deparment#page".length, hash.length);
        console.log("page " + page);
        systemDeparmentModel.set({"page": parseInt(page)});
        systemTabView.handleSystemDeparmentViewLinkClick();
      }else{
        systemTabView.handleSystemDeparmentViewLinkClick();
      }
    } else if (hash.indexOf('#department-group') != -1) {
        if (hash.indexOf("#page") != -1) {
            var page = hash.substring("#department-group#page".length, hash.length);
            console.log("page " + page);
            systemDepartmentGroupModel.set({"page": parseInt(page)});
            systemTabView.handleSystemDepartmentGroupViewLinkClick();
        }else{
            systemTabView.handleSystemDepartmentGroupViewLinkClick();
        }
    }
  } else {
    window.location.href = contextURL + "/system#system-user";
    systemTabView.handleSystemUserViewLinkClick();
  }

  webankUserSyncView = new azkaban.WebankUserSyncView({
    el: $('#webank-user-sync-panel'),
  });

  $("#webank-user-select").on('select2:select', function(e){
    //非自定义用户 则关联部门展示
    var dpId = e.params.data.dpId;
    var dpName = e.params.data.dpName;
    if(dpId){
      $("#webank-department-select2").val(dpId)
    }
  });

});

// 以下用于保存浏览数据，切换页面也能返回之前的浏览进度。
var systemUserModel;
azkaban.SystemUserModel = Backbone.Model.extend({});

var systemDeparmentModel;
azkaban.SystemDeparmentModel = Backbone.Model.extend({});

var systemDepartmentGroupModel;
azkaban.SystemDepartmentGroupModel = Backbone.Model.extend({});


