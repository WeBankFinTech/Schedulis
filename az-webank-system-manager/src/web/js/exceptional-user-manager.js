
var addExceptionalUserView;
azkaban.AddExceptionalUserView = Backbone.View.extend({
  events: {
    "click #exceptional-user-create-btn": "addUser"
  },

  initialize: function (settings) {
    console.log("Hide modal error msg");
    $("#add-exceptional-user-modal-error-msg").hide();
    this.loadWebankUserData();
  },

  addUser: function (evt) {
    console.log("Add User button.");
    var userId = $("#exceptional-user-select").val();
    var requestURL = "/system";

    if (null == userId) {
      alert(wtssI18n.system.userPro);
      return;
    }

    var model = this.model;
    var requestData = {
      "ajax": "addExceptionalUser",
      "userId": userId,
    };
    var successHandler = function (data) {
      if (data.error) {
        $("#add-exceptional-user-modal-error-msg").show();
        $("#add-exceptional-user-modal-error-msg").text(data.error);
        return false;
      } else {
        window.location.href = "/system#exceptional-user";
        window.location.reload();
      }
      model.trigger("render");
    };
    $.post(requestURL, requestData, successHandler, "json");
  },

  loadWebankUserData: function () {

    $("#exceptional-user-select").select2({
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
    $("#add-exceptional-user-modal-error-msg").hide();
  },
});

//处理方法 组装表格和翻页处理
var exceptionalUserView;
azkaban.ExceptionalUserView = Backbone.View.extend({
  events: {
    "click #exceptional-user-pageSelection li": "handleChangePageSelection",
    "change #exceptional-user-pageSelection .pageSizeSelect": "handlePageSizeSelection",
    "click #exceptional-user-pageSelection .pageNumJump": "handlePageNumJump",
    "click #exceptional-user-table-body .btn-danger": "handleDelete",
    "click #addExceptionalUser": "showAddPanel",
  },

  initialize: function (settings) {
    this.model.bind('change:view', this.handleChangeView, this);
    this.model.bind('render', this.render, this);
    this.model.set({ page: 1, pageSize: 20 });
    this.model.bind('change:page', this.handlePageChange, this);
    this.model.set('elDomId','exceptional-user-pageSelection'); 
    this.createResize();
  },

  handleDelete: function (evt) {
    console.log("handleDelete");
    var userId = $(evt.currentTarget).attr("name");
    var requestURL = "/system";

    if (null == userId) {
      alert(wtssI18n.system.userPro);
      return;
    }
    deleteDialogView.show(wtssI18n.deletePro.deleteExceptionalPerson, wtssI18n.deletePro.whetherDeleteExceptionalPerson, wtssI18n.common.cancel, wtssI18n.common.delete, '', function() {
        // var model = this.model;
        var requestData = {
        "ajax": "deleteExceptionalUser",
        "userId": userId,
        };
        var successHandler = function (data) {
        if (data.error) {
            $("#add-exceptional-user-modal-error-msg").show();
            $("#add-exceptional-user-modal-error-msg").text(data.error);
            return false;
        } else {
            window.location.href = "/system#exceptional-user";
            window.location.reload();
        }
        // model.trigger("render");
        };
        $.post(requestURL, requestData, successHandler, "json");
    });
  },

  showAddPanel: function (evt) {
    console.log("showAddPanel");
    $("#exceptional-user-select").empty();
    $('#add-exceptional-user-panel').modal();
  },

  render: function (evt) {
    console.log("render");
    // Render page selections
    var tbody = $("#exceptional-user-table-body");
    tbody.empty();

    var users = this.model.get("exceptionalUsers");
    if (!users || users.length == 0) {
      $("#exceptional-user-pageSelection").hide()
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

      //组装用户部门id
      var tdDepId = document.createElement("td");
      $(tdDepId).text(users[i].departmentId);
      row.appendChild(tdDepId);

      //组装用户部门行
      var tdDep = document.createElement("td");
      $(tdDep).text(users[i].departmentName);
      $(tdDep).attr("style", "word-break:break-all;");
      row.appendChild(tdDep);

      //组装用户邮箱行
      var tdEmail = document.createElement("td");
      $(tdEmail).text(users[i].email);
      $(tdEmail).attr("style", "word-break:break-all;max-width:350px");
      row.appendChild(tdEmail);

      //组装操作行
      var tdAction = document.createElement("td");
      var deleteBtn = document.createElement("button");
      $(deleteBtn).attr("name", users[i].userId);
      $(deleteBtn).attr("class", "btn btn-sm btn-danger");
      $(deleteBtn).text(wtssI18n.common.delete1);
      tdAction.appendChild(deleteBtn);
      row.appendChild(tdAction);

      tbody.append(row);
    }

    this.renderPagination(evt);
  },

  ...commonPaginationFun(),

  handlePageChange: function (evt) {
    var pageNum = this.model.get("page") - 1;
    var pageSize = this.model.get("pageSize");
    var requestURL = "/system";
    var searchName = this.model.get("searchName");
    if (!searchName) {
      searchName = "";
    }

    var model = this.model;
    var requestData = {
      "ajax": "fetchAllExceptionUsers",
      "pageNum": pageNum,
      "pageSize": pageSize,
      "searchName": searchName,
    };
    var successHandler = function (data) {
      model.set({
        "exceptionalUsers": data.exceptionalUsers,
        "total": data.total
      });
      model.trigger("render");
    };
    $.get(requestURL, requestData, successHandler, "json");
  },


});





