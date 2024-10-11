/**
 * Created by zhangxi on 8/7/19.
 */

// 部门运维人员清单主页面视图
var departmentMaintainerListView;
azkaban.DepartmentMaintainerListView = Backbone.View.extend({
  events: {
    "click #department-maintainer-pageSelection li": "handleChangePageSelection",
    "change #department-maintainer-pageSelection .pageSizeSelect": "handlePageSizeSelection",
    "click #department-maintainer-pageSelection .pageNumJump": "handlePageNumJump",
    "click .btn-info": "handleUpdateDepartmentMaintainerBtn",
  },

  initialize: function (settings) {
    this.model.bind('change:view', this.handleChangeView, this);
    this.model.bind('render', this.render, this);
    this.model.set({ page: 1, pageSize: 20 });
    this.model.bind('change:page', this.handlePageChange, this);
    this.model.set('elDomId','department-maintainer-pageSelection'); 
    this.createResize();
  },

  render: function (evt) {
    console.log("render");
    // Render page selections
    var tbody = $("#maintainerTableBody");
    tbody.empty();

    var departmentList = this.model.get("departmentMaintainerPageList");
    var modifyI18n = this.model.get("modify");
    if (!departmentList || departmentList.length == 0) {
      $("#department-maintainer-pageSelection").hide();
    } else {
      $("#department-maintainer-pageSelection").show();
    }

    // 组装数据内容
    for (var i = 0; i < departmentList.length; ++i) {
      var row = document.createElement("tr");

      //组装数字行
      var tdNum = document.createElement("td");
      $(tdNum).text(i + 1);
      $(tdNum).attr("class", "tb-name");
      row.appendChild(tdNum);

      //组装部门ID行
      var tdDepartmentId = document.createElement("td");
      $(tdDepartmentId).text(departmentList[i].departmentId);
      row.appendChild(tdDepartmentId);

      //组装部门名称行
      var tdDepartmentName = document.createElement("td");
      $(tdDepartmentName).text(departmentList[i].departmentName);
      $(tdDepartmentName).attr("style", "word-break:break-all;");
      row.appendChild(tdDepartmentName);

      //组装运维人员行
      var tdMaintainerList = document.createElement("td");
      $(tdMaintainerList).text(departmentList[i].maintainerList);
      $(tdMaintainerList).attr("style", "word-break:break-all;width:300px");
      row.appendChild(tdMaintainerList);

      //组装操作行
      var tdAction = document.createElement("td");
      var updateBtn = document.createElement("button");
      $(updateBtn).attr("id", departmentList[i].departmentId + "updateBtn");
      $(updateBtn).attr("name", departmentList[i].departmentId);
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
      "ajax": "findDepartmentMaintainerList",
      "start": start,
      "pageSize": pageSize,
      "searchterm": searchterm,
    };
    var successHandler = function (data) {
      model.set({
        "departmentMaintainerPageList": data.departmentMaintainerPageList,
        "modify": data.modify,
        "total": data.total
      });
      model.trigger("render");
    };
    $.get(requestURL, requestData, successHandler, "json");
  },

  handleUpdateDepartmentMaintainerBtn: function (evt) {
    console.log("click update department maintainer");

    var departmentId = evt.currentTarget.name;
    departmentMaintainerListModel.set({ "updateDepartmentId": departmentId });
    $('#update-department-maintainer-panel').modal();
    updateDepMaintainerView.render();
    updateDepMaintainerView.loadDepMaintainerData();
  },

});

var depMaintainerOptionsView;
azkaban.DepMaintainerOptionsView = Backbone.View.extend({
  events: {
    "click #addDepMaintainer": "handleAddDepMaintainer",
    "click #search-department-maintainer-btn": "handleSearchDepMaintainer"
  },

  initialize: function (settings) {
  },

  handleAddDepMaintainer: function (evt) {
    console.log("click upload project");
    $("#dep-for-select-add-maintainer").val("");
    $("#dep-maintainer-select").empty();
    $('#add-department-maintainer-panel').modal();
    addDepMaintainerView.render();
  },

  handleSearchDepMaintainer: function () {
    var searchterm = $('#serarch-department-maintainer').val();
    departmentMaintainerListModel.set({ "searchterm": searchterm });
    systemTabView.handleSystemDepartmentMaintainerViewLinkClick();
  },

  render: function () {
  }
});

// 部门运维人员新增页面
var addDepMaintainerView;
azkaban.AddDepMaintainerView = Backbone.View.extend({
  events: {
    "click #dep-maintainer-create-btn": "handleAddDepartmentMaintainer"
  },

  initialize: function (settings) {
    console.log("Hide modal error msg");
    $("#add-user-modal-error-msg").hide();
    this.loadWebankUserData();
  },

  handleAddDepartmentMaintainer: function (evt) {
    console.log("Add department ops-user button.");
    var departmentId = $("#dep-for-select-add-maintainer").val();
    var userId = $("#dep-maintainer-select").val();
    var requestURL = "/system";

    if (null == userId) {
      alert(wtssI18n.system.userPro);
      return;
    }

    if ("0" == departmentId) {
      alert(wtssI18n.system.departmentPro);
      return;
    }

    var requestData = {
      "ajax": "addDepartmentMaintainer",
      "departmentId": departmentId,
      "userId": userId
    };
    var model = this.model;
    var successHandler = function (data) {
      if (data.error) {
        $("#add-department-maintainer-error-msg").show();
        $("#add-department-maintainer-error-msg").text(data.error.message);
        return false;
      } else {
        window.location.href = "/system#department-maintainer-list";
        window.location.reload();
      }
      model.trigger("render");
    };
    $.get(requestURL, requestData, successHandler, "json");
  },

  loadWebankUserData: function () {

    $("#dep-maintainer-select").select2({
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
    
    $("#add-department-maintainer-error-msg").hide();
  },
});

// 部门运维人员更新页面
var updateDepMaintainerView;
azkaban.UpdateDepMaintainerView = Backbone.View.extend({
  events: {
    "click #department-maintainer-update-btn": "handleUpdateDepMaintainer",
    "click #department-maintainer-delete-btn": "handleDeleteDepMaintainer"
  },

  initialize: function (settings) {
    console.log("Hide modal error msg");
    $("#update-department-maintainer-error-msg").hide();
    this.model.bind('change:view', this.handleChangeView, this);
    this.model.bind('render', this.render, this);
    this.model.set({ page: 1, pageSize: 20 });
    this.model.bind('change:page', this.handlePageChange, this);
  },

  handleUpdateDepMaintainer: function (evt) {
    console.log("Update Department Maintainer button.");
    var departmentId = $("#update-depId-maintainer").val();
    var departmentName = $("#update-depName-maintainer").val();
    var depMaintainer = $("#update-department-maintainer").val();
    var requestURL = "/system";

    if ("" == depMaintainer) {
      alert(wtssI18n.system.maintainerPro);
      return;
    }

    var model = this.model;
    var requestData = {
      "ajax": "updateDepartmentMaintainer",
      "departmentId": departmentId,
      "departmentName": departmentName,
      "depMaintainer": depMaintainer
    };
    var successHandler = function (data) {
      if (data.error) {
        $("#update-department-maintainer-error-msg").show();
        $("#update-department-maintainer-error-msg").text(data.error.message);
        return false;
      } else {
        window.location.href = "/system#department-maintainer-list";
        window.location.reload();
      }
      model.trigger("render");
    };
    $.get(requestURL, requestData, successHandler, "json");
  },

  handleDeleteDepMaintainer: function (evt) {
    deleteDialogView.show(wtssI18n.deletePro.deleteMaintainer, wtssI18n.deletePro.whetherDeleteMaintainer, wtssI18n.common.cancel, wtssI18n.common.delete, '', function() {
        console.log("Delete System User button.");
        var departmentId = $("#update-depId-maintainer").val();
        var requestURL = "/system";

        // var model = this.model;
        var requestData = {
        "ajax": "deleteDepartmentMaintainer",
        "departmentId": departmentId
        };
        var successHandler = function (data) {
        if (data.error) {
            $("#update-department-maintainer-error-msg").show();
            $("#update-department-maintainer-error-msg").text(data.error.message);
            return false;
        } else {
            window.location.href = "/system#department-maintainer-list";
            window.location.reload();
        }
        // model.trigger("render");
        };
        $.get(requestURL, requestData, successHandler, "json");
    });
  },

  loadDepMaintainerData: function () {

    var departmentId = this.model.get("updateDepartmentId");
    var requestURL = "/system";

    var requestData = {
      "ajax": "getDepMaintainerByDepId",
      "departmentId": departmentId
    };
    var successHandler = function (data) {
      if (data.error) {
        $("#update-department-maintainer-error-msg").show();
        $("#update-department-maintainer-error-msg").text(data.error.message);
        return false;
      } else {
        $("#update-depId-maintainer").val(data.maintainer.departmentId);
        $("#update-depName-maintainer").val(data.maintainer.departmentName);
        $("#update-department-maintainer").val(data.maintainer.opsUser);
      }
    };
    $.get(requestURL, requestData, successHandler, "json");

  },
  render: function () {
    $("#update-department-maintainer-error-msg").hide();
  },
});