/*
 * Copyright 2012 LinkedIn Corp.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */

$.namespace('azkaban');

var permissionTableView;
var groupPermissionTableView;

azkaban.PermissionTableView = Backbone.View.extend({
  events: {
    "click button": "handleChangePermission"
  },

  initialize: function (settings) {
    this.group = settings.group;
    this.proxy = settings.proxy;
  },

  render: function () {
  },

  // 项目权限, 修改项目用户
  handleChangePermission: function (evt) {
    var currentTarget = evt.currentTarget;
    var currentTargetId = currentTarget.id;
    if (currentTargetId.indexOf("admin-remove-") != -1) {
      var userid = currentTargetId.substring(13, currentTargetId.length);

      var requestURL = contextURL + "/manager?ajax=checkRemoveProjectManagePermission&project=" + projectName;
      $.ajax({
        url: requestURL,
        type: "get",
        async: false,
        dataType: "json",
        success: function(data){
          if(data["removeManageFlag"] == 1){
            console.log("have permission, click remove manage");
            removeProjectAdminView.show(userid);
          } else {
            $('#remove-project-manage-permission-panel').modal();
          }
        }
      });


    } else if (currentTargetId.indexOf("user-update-") != -1) {

      var requestURL = contextURL + "/manager?ajax=checkUpdateProjectUserPermission&project=" + projectName;
      $.ajax({
        url: requestURL,
        type: "get",
        async: false,
        dataType: "json",
        success: function(data){
          if(data["updateProUserFlag"] == 1){
            console.log("have permission, click modify project user");
            var userid = currentTargetId.substring(12, currentTargetId.length)
            changeProjectUserView.display(userid, false);
          } else {
            $('#update-project-user-permission-panel').modal();
          }
        }
      });


    }
    // changePermissionView.display(currentTarget.id, false, this.group,
    //     this.proxy);

  }
});

var proxyTableView;
azkaban.ProxyTableView = Backbone.View.extend({
  events: {
    "click button": "handleRemoveProxy"
  },

  initialize: function (settings) {
  },

  render: function () {
  },

  handleRemoveProxy: function (evt) {
    removeProxyView.display($(evt.currentTarget).attr("name"));
  }
});

var removeProxyView;
azkaban.RemoveProxyView = Backbone.View.extend({
  events: {
    "click #remove-proxy-btn": "handleRemoveProxy"
  },

  initialize: function (settings) {
    $('#remove-proxy-error-msg').hide();
  },

  display: function (proxyName) {
    this.el.proxyName = proxyName;
    $("#remove-proxy-msg").text("Removing proxy user '" + proxyName + "'");
    $(this.el).modal().on('hide.bs.modal', function (e) {
      $('#remove-proxy-error-msg').hide();
    });
  },

  handleRemoveProxy: function () {
    var requestURL = contextURL + "/manager";
    var proxyName = this.el.proxyName;
    var requestData = {
      "project": projectName,
      "name": proxyName,
      "ajax": "removeProxyUser"
    };
    var successHandler = function (data) {
      console.log("Output");
      if (data.error) {
        $("#remove-proxy-error-msg").text(data.error);
        $("#remove-proxy-error-msg").slideDown();
        return;
      }
      var replaceURL = requestURL + "?project=" + projectName + "&permissions";
      window.location.replace(replaceURL);
    };

    $.get(requestURL, requestData, successHandler, "json");
  }
});

var addProxyView;
azkaban.AddProxyView = Backbone.View.extend({
  events: {
    "click #add-proxy-btn": "handleAddProxy"
  },

  initialize: function (settings) {
    $('#add-proxy-error-msg').hide();
  },

  display: function () {
    $(this.el).modal().on('hide.bs.modal', function (e) {
      $('#add-proxy-error-msg').hide();
    });
  },

  handleAddProxy: function () {
    var requestURL = contextURL + "/manager";
    var name = $('#proxy-user-box').val().trim();
    var requestData = {
      "project": projectName,
      "name": name,
      "ajax": "addProxyUser"
    };

    var successHandler = function (data) {
      console.log("Output");
      if (data.error) {
        $("#add-proxy-error-msg").text(data.error);
        $("#add-proxy-error-msg").slideDown();
        return;
      }

      var replaceURL = requestURL + "?project=" + projectName + "&permissions";
      window.location.replace(replaceURL);
    };
    $.get(requestURL, requestData, successHandler, "json");
  }
});

var changePermissionView;
azkaban.ChangePermissionView = Backbone.View.extend({
  events: {
    "click input[type=checkbox]": "handleCheckboxClick",
    "click #change-btn": "handleChangePermissions"
  },

  initialize: function (settings) {
    $('#change-permission-error-msg').hide();
  },

  display: function (userid, newPerm, group, proxy) {
    // 6 is the length of the prefix "group-"
    this.userid = group ? userid.substring(6, userid.length) : userid;
    if (group == true) {
      this.userid = userid.substring(6, userid.length)
    } else if (proxy == true) {
      this.userid = userid.substring(6, userid.length)
    } else {
      this.userid = userid
    }

    this.permission = {};
    $('#user-box').val(this.userid);
    this.newPerm = newPerm;
    this.group = group;

    var prefix = userid;
    var adminInput = $("#" + prefix + "-admin-checkbox");
    var readInput = $("#" + prefix + "-read-checkbox");
    var writeInput = $("#" + prefix + "-write-checkbox");
    var executeInput = $("#" + prefix + "-execute-checkbox");
    var scheduleInput = $("#" + prefix + "-schedule-checkbox");

    if (newPerm) {
      if (group) {
        $('#change-title').text(wtssI18n.view.addPermissions);
      }
      else if (proxy) {
        $('#change-title').text(wtssI18n.view.addProxyPermissions);
      }
      else {
        $('#change-title').text(wtssI18n.view.addSystemRight);
        this.loadSystemUserData();
      }
      $('#user-box').attr("disabled", null);

      // default
      this.permission.admin = false;
      this.permission.read = true;
      this.permission.write = false;
      this.permission.execute = false;
      this.permission.schedule = false;
    }
    else {
      if (group) {
        $('#change-title').text(wtssI18n.view.modifyPermissions);
      }
      else {
        $('#change-title').text(wtssI18n.view.mjodifySystemRights);
      }

      $('#user-box').attr("disabled", "disabled");

      this.permission.admin = $(adminInput).is(":checked");
      this.permission.read = $(readInput).is(":checked");
      this.permission.write = $(writeInput).is(":checked");
      this.permission.execute = $(executeInput).is(":checked");
      this.permission.schedule = $(scheduleInput).is(":checked");
    }

    this.changeCheckbox();

    changePermissionView.render();
    $('#change-permission').modal().on('hide.bs.modal', function (e) {
      $('#change-permission-error-msg').hide();
    });
  },

  render: function () {
  },

  handleCheckboxClick: function (evt) {
    console.log("click");
    var targetName = evt.currentTarget.name;
    if (targetName == "proxy") {
      this.doProxy = evt.currentTarget.checked;
    }
    else {
      this.permission[targetName] = evt.currentTarget.checked;
    }
    this.changeCheckbox(evt);
  },

  changeCheckbox: function (evt) {
    var perm = this.permission;

    if (perm.admin) {
      $("#admin-change").attr("checked", true);
      $("#read-change").attr("checked", true);
      $("#read-change").attr("disabled", "disabled");

      $("#write-change").attr("checked", true);
      $("#write-change").attr("disabled", "disabled");

      $("#execute-change").attr("checked", true);
      $("#execute-change").attr("disabled", "disabled");

      $("#schedule-change").attr("checked", true);
      $("#schedule-change").attr("disabled", "disabled");
    }
    else {
      $("#admin-change").attr("checked", false);

      $("#read-change").attr("checked", perm.read);
      $("#read-change").attr("disabled", null);

      $("#write-change").attr("checked", perm.write);
      $("#write-change").attr("disabled", null);

      $("#execute-change").attr("checked", perm.execute);
      $("#execute-change").attr("disabled", null);

      $("#schedule-change").attr("checked", perm.schedule);
      $("#schedule-change").attr("disabled", null);
    }

    $("#change-btn").removeClass("btn-disabled");
    $("#change-btn").attr("disabled", null);

    if (perm.admin || perm.read || perm.write || perm.execute
        || perm.schedule) {
      $("#change-btn").text("Commit");
    }
    else {
      if (this.newPerm) {
        $("#change-btn").disabled = true;
        $("#change-btn").addClass("btn-disabled");
      }
      else {
        $("#change-btn").text("Remove");
      }
    }
  },

  handleChangePermissions: function (evt) {
    var requestURL = contextURL + "/manager";
    var name = $('#user-box').val().trim();
    var command = this.newPerm ? "addPermission" : "changePermission";
    var group = this.group;

    var permission = {};
    permission.admin = $("#admin-change").is(":checked");
    permission.read = $("#read-change").is(":checked");
    permission.write = $("#write-change").is(":checked");
    permission.execute = $("#execute-change").is(":checked");
    permission.schedule = $("#schedule-change").is(":checked");

    var requestData = {
      "project": projectName,
      "name": name,
      "ajax": command,
      "permissions": this.permission,
      "group": group
    };
    var successHandler = function (data) {
      console.log("Output");
      if (data.error) {
        $("#change-permission-error-msg").text(data.error);
        $("#change-permission-error-msg").slideDown();
        return;
      }

      var replaceURL = requestURL + "?project=" + projectName + "&permissions";
      window.location.replace(replaceURL);
    };

    $.get(requestURL, requestData, successHandler, "json");
  },

  loadSystemUserData: function () {

    $("#system-user-select").select2({
      placeholder:wtssI18n.system.userPro,//默认文字提示
      multiple : false,
      width: 'resolve',
      //language: "zh-CN",
      //tags: true,//允许手动添加
      allowClear: true,//允许清空
      escapeMarkup: function (markup) { return markup; }, //自定义格式化防止XSS注入
      minimumInputLengt: 1,//最少输入多少字符后开始查询
      formatResult: function formatRepo(repo){return repo.text;},//函数用来渲染结果
      formatSelection: function formatRepoSelection(repo){return repo.text;},//函数用于呈现当前的选择
      ajax: {
        type: 'GET',
        url: contextURL + "/system",
        dataType: 'json',
        delay: 250,
        data: function (params) {
          var query = {
            ajax: "loadSystemUserSelectData",
            serach: params.term,
            page: params.page || 1,
            pageSize: 20,
          }
          return query;
        },
        processResults: function (data, params) {
          params.page = params.page || 1;
          return {
            results: data.systemUserList,
            pagination: {
              more: (params.page * 20) < data.systemUserTotalCount
            }
          }
        },
        cache:true
      },

    });

  },

});

$(function () {
  // permissionTableView = new azkaban.PermissionTableView({
  //   el: $('#permissions-table'),
  //   group: false,
  //   proxy: false
  // });
  // groupPermissionTableView = new azkaban.PermissionTableView({
  //   el: $('#group-permissions-table'),
  //   group: true,
  //   proxy: false
  // });
  adminPermissionTableView = new azkaban.PermissionTableView({
    el: $('#project-admin-permissions-table'),
  });
  userPermissionTableView = new azkaban.PermissionTableView({
    el: $('#project-user-permissions-table'),
  });
  proxyTableView = new azkaban.ProxyTableView({
    el: $('#proxy-user-table'),
    group: false,
    proxy: true
  });
  changePermissionView = new azkaban.ChangePermissionView({
    el: $('#change-permission')
  });
  addProxyView = new azkaban.AddProxyView({
    el: $('#add-proxy')
  });
  removeProxyView = new azkaban.RemoveProxyView({
    el: $('#remove-proxy')
  });
  changeProjectAdminView = new azkaban.ChangeProjectAdminView({
    el: $('#change-project-admin-permission')
  });
  addProjectUserView = new azkaban.AddProjectUserView({
    el: $('#add-project-user-permission')
  });
  changeProjectUserView = new azkaban.ChangeProjectUserView({
    el: $('#change-project-user-permission')
  });

  $('#addUser').bind('click', function () {
    changePermissionView.display("", true, false, false);
  });

  $('#addGroup').bind('click', function () {
    changePermissionView.display("", true, true, false);
  });

  $('#addProxyUser').bind('click', function () {
    addProxyView.display();
  });


  // 项目权限, 点击添加项目管理员
  $('#addProjectAdmin').bind('click', function () {

    var requestURL = contextURL + "/manager?ajax=checkAddProjectManagePermission&project=" + projectName;
    $.ajax({
      url: requestURL,
      type: "get",
      async: false,
      dataType: "json",
      success: function(data){
        if(data["addManageFlag"] == 1){
          console.log("have permission, click add manage");
          changeProjectAdminView.display("", true);
        } else {
          $('#add-project-manage-permission-panel').modal();
        }
      }
    });

  });

  // 项目权限, 点击添加项目用户
  $('#addProjectUser').bind('click', function () {
    var requestURL = contextURL + "/manager?ajax=checkAddProjectUserPermission&project=" + projectName;
    $.ajax({
      url: requestURL,
      type: "get",
      async: false,
      dataType: "json",
      success: function(data){
        if(data["addProjectUserFlag"] == 1){
          console.log("have permission, click add manage");
          addProjectUserView.display("", true);
        } else {
          $('#add-project-user-permission-panel').modal();
        }
      }
    });
  });

  removeProjectAdminView = new azkaban.RemoveProjectAdminView({
    el: $('#project-admin-remove')
  });


});

var adminPermissionTableView;
var userPermissionTableView;

var changeProjectAdminView;
azkaban.ChangeProjectAdminView = Backbone.View.extend({
  events: {
    "click input[type=checkbox]": "handleCheckboxClick",
    "click #project-admin-change-btn": "handleChangePermissions"
  },

  initialize: function (settings) {
    $('#change-project-admin-error-msg').hide();
  },

  display: function (userid, newPerm) {

    this.userid = userid

    this.permission = {};
    $('#user-box').val(this.userid);
    this.newPerm = newPerm;

    var prefix = userid;
    var adminInput = $("#" + prefix + "-admin-checkbox");
    var readInput = $("#" + prefix + "-read-checkbox");
    var writeInput = $("#" + prefix + "-write-checkbox");
    var executeInput = $("#" + prefix + "-execute-checkbox");
    var scheduleInput = $("#" + prefix + "-schedule-checkbox");

    if (newPerm) {

      $('#change-title').text(wtssI18n.view.addSystemRight);
      this.loadSystemUserData();

      $('#user-box').attr("disabled", null);

      // default
      this.permission.admin = true;
      this.permission.read = true;
      this.permission.write = true;
      this.permission.execute = true;
      this.permission.schedule = true;
    }

    this.changeCheckbox();

    changeProjectAdminView.render();
    $('#change-project-admin-permission').modal().on('hide.bs.modal', function (e) {
      $('#change-project-admin-error-msg').hide();
    });
  },

  render: function () {
  },

  handleCheckboxClick: function (evt) {
    console.log("click");
    var targetName = evt.currentTarget.name;

    this.permission[targetName] = evt.currentTarget.checked;

    this.changeCheckbox(evt);
  },

  changeCheckbox: function (evt) {
    var perm = this.permission;

    if (perm.admin) {
      $("#admin-change").attr("checked", true);
      $("#read-change").attr("checked", true);
      $("#read-change").attr("disabled", "disabled");

      $("#write-change").attr("checked", true);
      $("#write-change").attr("disabled", "disabled");

      $("#execute-change").attr("checked", true);
      $("#execute-change").attr("disabled", "disabled");

      $("#schedule-change").attr("checked", true);
      $("#schedule-change").attr("disabled", "disabled");
    }

    $("#change-btn").removeClass("btn-disabled");
    $("#change-btn").attr("disabled", null);

    if (perm.admin || perm.read || perm.write || perm.execute
        || perm.schedule) {
      $("#change-btn").text(wtssI18n.view.submit);
    }
    else {
      if (this.newPerm) {
        $("#change-btn").disabled = true;
        $("#change-btn").addClass("btn-disabled");
      }
      else {
        $("#change-btn").text(wtssI18n.view.remove);
      }
    }
  },

  handleChangePermissions: function (evt) {
    var requestURL = contextURL + "/manager";
    var userId = $('#project-admin-system-user-select').val().trim();
    var command = this.newPerm ? "ajaxAddProjectAdmin" : "changePermission";
    var group = this.group;

    var permission = {};
    permission.admin = $("#admin-change").is(":checked");
    permission.read = $("#read-change").is(":checked");
    permission.write = $("#write-change").is(":checked");
    permission.execute = $("#execute-change").is(":checked");
    permission.schedule = $("#schedule-change").is(":checked");

    var requestData = {
      "project": projectName,
      "userId": userId,
      "ajax": command,
      "permissions": this.permission,
      "group": group
    };
    var successHandler = function (data) {
      console.log("Output");
      if (data.error) {
        $("#change-project-admin-error-msg").text(data.error);
        $("#change-project-admin-error-msg").slideDown();
        return;
      }

      var replaceURL = requestURL + "?project=" + projectName + "&permissions";
      window.location.replace(replaceURL);
    };

    $.get(requestURL, requestData, successHandler, "json");
  },

  loadSystemUserData: function () {

    $("#project-admin-system-user-select").select2({
      placeholder:wtssI18n.system.userPro,//默认文字提示
      multiple : false,
      width: 'resolve',
      //language: "zh-CN",
      //tags: true,//允许手动添加
      allowClear: true,//允许清空
      escapeMarkup: function (markup) { return markup; }, //自定义格式化防止XSS注入
      minimumInputLengt: 1,//最少输入多少字符后开始查询
      formatResult: function formatRepo(repo){return repo.text;},//函数用来渲染结果
      formatSelection: function formatRepoSelection(repo){return repo.text;},//函数用于呈现当前的选择
      ajax: {
        type: 'GET',
        url: contextURL + "/system",
        dataType: 'json',
        delay: 250,
        data: function (params) {
          var query = {
            ajax: "loadSystemUserSelectData",
            serach: params.term,
            page: params.page || 1,
            pageSize: 20,
          }
          return query;
        },
        processResults: function (data, params) {
          params.page = params.page || 1;
          return {
            results: data.systemUserList,
            pagination: {
              more: (params.page * 20) < data.systemUserTotalCount
            }
          }
        },
        cache:true
      },

    });
  },

});

var addProjectUserView;
azkaban.AddProjectUserView = Backbone.View.extend({
  events: {
    "click input[type=checkbox]": "handleCheckboxClick",
    "click #project-user-add-btn": "handleAddProjectUser"
  },

  initialize: function (settings) {
    $('#add-project-user-error-msg').hide();
  },

  display: function (userid, newPerm) {

    this.userid = userid

    this.permission = {};
    $('#user-box').val(this.userid);
    this.newPerm = newPerm;

    var prefix = userid;
    //var adminInput = $("#" + prefix + "-admin-checkbox");
    var readInput = $("#" + prefix + "-read-checkbox");
    var writeInput = $("#" + prefix + "-write-checkbox");
    var executeInput = $("#" + prefix + "-execute-checkbox");
    var scheduleInput = $("#" + prefix + "-schedule-checkbox");


    $("#user-admin-change").prop("checked", false);
    $("#user-read-change").prop("checked", false);
    $("#user-write-change").prop("checked", false);
    $("#user-execute-change").prop("checked", false);
    $("#user-schedule-change").prop("checked", false);

    if (newPerm) {

      // $('#project-user-change-title').text("新增项目用户");
      //
      // $('#project-user-system-user-select').attr("style", "width:100%");
      // $('.select2').attr("style", "width:100%");
      // $('#project-user-div').text("");

      this.loadSystemUserData();

      // default
      //this.permission.admin = false;
      //this.permission.read = true;
      //this.permission.write = false;
      //this.permission.execute = false;
      //this.permission.schedule = false;
    }
    // else {
    //
    //   $('#project-user-change-title').text("修改项目用户权限");
    //
    //   $('.select2').attr("style", "display:none;");
    //
    //   $('#project-user-div').text(userid);
    //
    //   this.loadThisUserProjectPerm(userid);

      //this.permission.admin = $(adminInput).is(":checked");
      //this.permission.read = $(readInput).is(":checked");
      //this.permission.write = $(writeInput).is(":checked");
      //this.permission.execute = $(executeInput).is(":checked");
      //this.permission.schedule = $(scheduleInput).is(":checked");
    //}

    //this.changeCheckbox();

    changeProjectUserView.render();
    $('#add-project-user-permission').modal().on('hide.bs.modal', function (e) {
      $('#add-project-user-error-msg').hide();
    });
  },

  render: function () {
  },

  handleCheckboxClick: function (evt) {
    console.log("click");
    var targetName = evt.currentTarget.name;

    this.permission[targetName] = evt.currentTarget.checked;

    this.changeCheckbox(evt);
  },

  changeCheckbox: function (evt) {
    var perm = this.permission;

    //$("#admin-change").attr("checked", false);

    $("#add-read-change").attr("checked", perm.read);
    $("#add-read-change").attr("disabled", null);

    $("#add-write-change").attr("checked", perm.write);
    $("#add-write-change").attr("disabled", null);

    $("#add-execute-change").attr("checked", perm.execute);
    $("#add-execute-change").attr("disabled", null);

    $("#add-schedule-change").attr("checked", perm.schedule);
    $("#add-schedule-change").attr("disabled", null);


    $("#add-change-btn").removeClass("btn-disabled");
    $("#add-change-btn").attr("disabled", null);

    if (perm.admin || perm.read || perm.write || perm.execute
        || perm.schedule) {
      $("#change-btn").text(wtssI18n.view.submit);
    }
    else {
      if (this.newPerm) {
        $("#change-btn").disabled = true;
        $("#change-btn").addClass("btn-disabled");
      }
      else {
        $("#change-btn").text(wtssI18n.view.remove);
      }
    }
  },

  handleAddProjectUser: function (evt) {
    var requestURL = contextURL + "/manager";
    var userId = $('#project-user-system-user-select').val().trim();

    var permissionMap = {};
    permissionMap.admin = false;
    permissionMap.read = $("#add-user-read-change").is(":checked");
    permissionMap.write = $("#add-user-write-change").is(":checked");
    permissionMap.execute = $("#add-user-execute-change").is(":checked");
    permissionMap.schedule = $("#add-user-schedule-change").is(":checked");

    var requestData = {
      "project": projectName,
      "name": userId,
      "userId": userId,
      "ajax": "ajaxAddProjectUserPermission",
      "permissions": permissionMap,
    };
    var successHandler = function (data) {
      console.log("Output");
      if (data.error) {
        $("#add-project-user-error-msg").text(data.error);
        $("#add-project-user-error-msg").slideDown();
        return;
      }

      var replaceURL = requestURL + "?project=" + projectName + "&permissions";
      window.location.replace(replaceURL);
    };

    $.get(requestURL, requestData, successHandler, "json");
  },

  loadSystemUserData: function () {

    $("#project-user-system-user-select").select2({
      placeholder:wtssI18n.system.userPro,//默认文字提示
      multiple : false,
      width: 'resolve',
      //language: "zh-CN",
      //tags: true,//允许手动添加
      allowClear: true,//允许清空
      escapeMarkup: function (markup) { return markup; }, //自定义格式化防止XSS注入
      minimumInputLengt: 1,//最少输入多少字符后开始查询
      formatResult: function formatRepo(repo){return repo.text;},//函数用来渲染结果
      formatSelection: function formatRepoSelection(repo){return repo.text;},//函数用于呈现当前的选择
      ajax: {
        type: 'GET',
        url: contextURL + "/system",
        dataType: 'json',
        delay: 250,
        data: function (params) {
          var query = {
            ajax: "loadSystemUserSelectData",
            serach: params.term,
            page: params.page || 1,
            pageSize: 20,
          }
          return query;
        },
        processResults: function (data, params) {
          params.page = params.page || 1;
          return {
            results: data.systemUserList,
            pagination: {
              more: (params.page * 20) < data.systemUserTotalCount
            }
          }
        },
        cache:true
      },

    });

  },

});

var changeProjectUserView;
azkaban.ChangeProjectUserView = Backbone.View.extend({
  events: {
    "click input[type=checkbox]": "handleCheckboxClick",
    "click #project-user-change-btn": "handleChangeProjectUser",
    "click #project-user-remove-btn": "handleRemoveProjectUser"
  },

  initialize: function (settings) {
    $('#change-project-user-error-msg').hide();
  },

  display: function (userid, newPerm) {

    this.userid = userid

    this.permission = {};
    $('#user-box').val(this.userid);
    this.newPerm = newPerm;

    var prefix = userid;

    $("#change-user-admin-change").prop("checked", false);
    $("#change-user-read-change").prop("checked", false);
    $("#change-user-write-change").prop("checked", false);
    $("#change-user-execute-change").prop("checked", false);
    $("#change-user-schedule-change").prop("checked", false);


    $('#project-user-div').text(userid);
    this.loadThisUserProjectPerm(userid);

    changeProjectUserView.render();
    $('#change-project-user-permission').modal().on('hide.bs.modal', function (e) {
      $('#change-project-user-error-msg').hide();
    });
  },

  render: function () {
  },

  handleCheckboxClick: function (evt) {
    console.log("click");
    var targetName = evt.currentTarget.name;

    this.permission[targetName] = evt.currentTarget.checked;

    this.changeCheckbox(evt);
  },

  changeCheckbox: function (evt) {
    var perm = this.permission;

    //$("#admin-change").attr("checked", false);

    $("#change-read-change").attr("checked", perm.read);
    $("#change-read-change").attr("disabled", null);

    $("#change-write-change").attr("checked", perm.write);
    $("#change-write-change").attr("disabled", null);

    $("#change-execute-change").attr("checked", perm.execute);
    $("#change-execute-change").attr("disabled", null);

    $("#change-schedule-change").attr("checked", perm.schedule);
    $("#change-schedule-change").attr("disabled", null);


    $("#change-change-btn").removeClass("btn-disabled");
    $("#change-change-btn").attr("disabled", null);

    if (perm.admin || perm.read || perm.write || perm.execute
        || perm.schedule) {
      $("#change-btn").text(wtssI18n.view.submit);
    }
    else {
      if (this.newPerm) {
        $("#change-btn").disabled = true;
        $("#change-btn").addClass("btn-disabled");
      }
      else {
        $("#change-btn").text(wtssI18n.view.remove);
      }
    }
  },

  handleChangeProjectUser: function (evt) {
    var requestURL = contextURL + "/manager";
    var userId = $('#project-user-div').text();
    var permissionMap = {};
    permissionMap.admin = $("#change-user-admin-change").is(":checked");
    permissionMap.read = $("#change-user-read-change").is(":checked");
    permissionMap.write = $("#change-user-write-change").is(":checked");
    permissionMap.execute = $("#change-user-execute-change").is(":checked");
    permissionMap.schedule = $("#change-user-schedule-change").is(":checked");

    var requestData = {
      "project": projectName,
      "name": userId,
      "userId": userId,
      "ajax": "changePermission",
      "permissions": permissionMap,
    };
    var successHandler = function (data) {
      console.log("Output");
      if (data.error) {
        $("#change-project-user-error-msg").text(data.error);
        $("#change-project-user-error-msg").slideDown();
        return;
      }

      var replaceURL = requestURL + "?project=" + projectName + "&permissions";
      window.location.replace(replaceURL);
    };

    $.get(requestURL, requestData, successHandler, "json");
  },

  loadThisUserProjectPerm: function (userId) {
    var requestURL = contextURL + "/manager";
    var requestData = {
      "project": projectName,
      "userId": userId,
      "ajax": "ajaxGetUserProjectPerm",
    };
    var successHandler = function (data) {
      console.log("Output");
      if (data.error) {
        $("#change-permission-error-msg").text(data.error);
        $("#change-permission-error-msg").slideDown();
        return;
      }
      $("#change-user-read-change").prop('checked', data.readPerm);
      $("#change-user-write-change").prop('checked', data.writePerm);
      $("#change-user-execute-change").prop('checked', data.executePerm);
      $("#change-user-schedule-change").prop('checked', data.schedulePerm);
    };

    $.get(requestURL, requestData, successHandler, "json");
  },

  handleRemoveProjectUser: function () {
    var requestURL = contextURL + "/manager";
    var userId = $('#project-user-div').text();
    var requestData = {
      "project": projectName,
      "userId": userId,
      "ajax": "ajaxRemoveProjectAdmin"
    };

    var successHandler = function (data) {
      console.log("Output");
      if (data.error) {
        $("#change-project-user-error-msg").text(data.error);
        $("#change-project-user-error-msg").slideDown();
        return;
      }

      var replaceURL = requestURL + "?project=" + projectName + "&permissions";
      window.location.replace(replaceURL);
    };
    $.get(requestURL, requestData, successHandler, "json");
  }

});

var removeProjectAdminView;
azkaban.RemoveProjectAdminView = Backbone.View.extend({
  events: {
    "click #remove-project-admin-btn": "handleRemoveProjectAdmin"
  },

  initialize: function (settings) {
    $('#remove-project-admind-error-msg').hide();
  },

  show: function (userId) {
    this.el.userId = userId;
    $(this.el).modal().on('hide.bs.modal', function (e) {
      $('#remove-project-admind-error-msg').hide();
    });
  },

  handleRemoveProjectAdmin: function () {
    var requestURL = contextURL + "/manager";
    var userId = this.el.userId;
    var requestData = {
      "project": projectName,
      "userId": userId,
      "ajax": "ajaxRemoveProjectAdmin"
    };

    var successHandler = function (data) {
      console.log("Output");
      if (data.error) {
        $("#remove-project-admind-error-msg").text(data.error);
        $("#remove-project-admind-error-msg").slideDown();
        return;
      }

      var replaceURL = requestURL + "?project=" + projectName + "&permissions";
      window.location.replace(replaceURL);
    };
    $.get(requestURL, requestData, successHandler, "json");
  }
});




