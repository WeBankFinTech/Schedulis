/**
 * Created by zhu on 7/5/18.
 */

//处理方法 组装表格和翻页处理
var systemDeparmentView;
azkaban.SystemDeparmentView = Backbone.View.extend({
  events: {
    "click #dep-pageSelection li": "handleChangePageSelection",
    "change #dep-pageSelection .pageSizeSelect": "handlePageSizeSelection",
    "click #dep-pageSelection .pageNumJump": "handlePageNumJump",
    "click .btn-info": "handleUpdateSystemDeparmentBtn",
  },

  initialize: function(settings) {
    this.model.bind('change:view', this.handleChangeView, this);
    this.model.bind('render', this.render, this);
    this.model.set({page: 1, pageSize: 20});
    this.model.bind('change:page', this.handlePageChange, this);
    this.model.set('elDomId','dep-pageSelection'); 
    this.createResize();
  },

  render: function(evt) {
    console.log("render");
    // Render page selections
    var tbody = $("#deparmentTableBody");
    tbody.empty();

    var deparment = this.model.get("systemDeparmentPageList");
    var modifyI18n = this.model.get("modify");
    if(!deparment || deparment.length == 0){
      $("#dep-pageSelection").hide();
    }else{
      $("#dep-pageSelection").show();
    }


    for (var i = 0; i < deparment.length; ++i) {
      var row = document.createElement("tr");

      //组装数字行
      var tdNum = document.createElement("td");
      $(tdNum).text(i + 1);
      $(tdNum).attr("class","tb-name");
      row.appendChild(tdNum);

      //组装用户ID行
      var tdDeparmentId = document.createElement("td");
      $(tdDeparmentId).text(deparment[i].dpId);
      row.appendChild(tdDeparmentId);

      //组装用户全名行
      var tdFullName = document.createElement("td");
      $(tdFullName).text(deparment[i].dpName);
      $(tdFullName).attr("style","word-break:break-all;width:250px");
      row.appendChild(tdFullName);

      //组装用户部门行
      var tdDep = document.createElement("td");
      $(tdDep).text(deparment[i].dpChName);
      $(tdDep).attr("style","word-break:break-all;width:350px");
      row.appendChild(tdDep);

      //组装代理用户行
      var tdProxyDeparment = document.createElement("td");
      $(tdProxyDeparment).text(deparment[i].orgId);
      //$(tdProxyDeparment).attr("style","word-break:break-all;width:250px");
      row.appendChild(tdProxyDeparment);

      //组装用户角色行
      var tdRole = document.createElement("td");
      $(tdRole).text(deparment[i].orgName);
      row.appendChild(tdRole);

      //组装用户行
      var tdPermission = document.createElement("td");
      $(tdPermission).text(deparment[i].division == "null" ? "" : deparment[i].division);
      //$(tdPermission).attr("style","word-break:break-all;width:350px");
      row.appendChild(tdPermission);

      //组装用户邮箱行
      var tdEmail = document.createElement("td");
      $(tdEmail).text(deparment[i].pid);
      row.appendChild(tdEmail);

      //组装groupId
      var groupName = document.createElement("td");
      $(groupName).text(deparment[i].groupName);
      $(groupName).attr("style","word-break:break-all;width:200px");
      row.appendChild(groupName);

      //组装操作行
      var tdAction = document.createElement("td");
      var updateBtn = document.createElement("button");
      $(updateBtn).attr("id", deparment[i].dpId + "updateBtn");
      $(updateBtn).attr("name", deparment[i].dpId);
      $(updateBtn).attr("class","btn btn-sm btn-info");
      $(updateBtn).text(modifyI18n);
      tdAction.appendChild(updateBtn);
      row.appendChild(tdAction);

      tbody.append(row);
    }

    this.renderPagination(evt);
  },

  ...commonPaginationFun(),
  handlePageChange: function(evt) {
    var start = this.model.get("page") - 1;
    var pageSize = this.model.get("pageSize");
    var requestURL = "/system";
    var searchString = $('#serarch-deparment').val();

    var model = this.model;
    var requestData = {
      "ajax": "findSystemDeparmentPage",
      "start": start,
      "pageSize": pageSize,
      "searchterm": searchString,
    };
    var successHandler = function(data) {
      model.set({
        "systemDeparmentPageList": data.systemDeparmentPageList,
        "modify": data.modify,
        "total": data.total
      });
      model.trigger("render");
    };
    $.get(requestURL, requestData, successHandler, "json");
  },

  handleUpdateSystemDeparmentBtn: function(evt) {
    console.log("click upload project");
    var dpId = evt.currentTarget.name;
    systemDeparmentModel.set({"dpId": dpId});
    $('#update-deparment-panel').modal();

    $("#old-department-group").empty();
    $("#old-department-group").append("<option value='0'>"+wtssI18n.system.groupPro+"</option>");
    var requestUrl = "/system?ajax=fetchAllDepartmentGroup";
    var model = this.model;
    var successHandler = function (data) {
        if (data.error) {
            $("#update-department-group-modal-error-msg").show();
            $("#update-department-group-modal-error-msg").text(data.error);
            return false;
        } else {
            model.set({"departmentGroups": data.departmentGroups});
            var departmentGroups = data.departmentGroups;
            for(var index in departmentGroups){
                $("#old-department-group").append("<option value='" + departmentGroups[index].id + "'>" + departmentGroups[index].name + "</option>");
            }
        }
        model.trigger("render");
    };
    $.ajax({
        url: requestUrl,
        type: "post",
        async: false,
        contentType: "application/json; charset=utf-8",
        dataType: "json",
        error: function(data) {
            console.log(data);
        },
        success: successHandler
    });

    updateDeparmentView.render();
    updateDeparmentView.loadDeparmentData();
  },


});

var deparmentOptionsView;
azkaban.DeparmentOptionsView = Backbone.View.extend({
  events: {
    "click #add-deparment": "handleAddDeparment",
    "click #search-deparment-btn": "handleSearchDeparment",
  },

  initialize: function (settings) {
  },

  handleAddDeparment: function (evt) {
    console.log("click upload project");
    $("#add-deparment-modal-error-msg").hide();
    $('#add-deparment-panel').modal();
    $("#department-group").empty();
    $("#department-group").append("<option value='0'>"+wtssI18n.system.groupPro+"</option>");
    var requestUrl = "/system?ajax=fetchAllDepartmentGroup";
    var model = this.model;
    var successHandler = function (data) {
        if (data.error) {
        messageBox.show(data.error, 'danger');
            return false;
        } else {
            model.set({"departmentGroups": data.departmentGroups});
            var departmentGroups = data.departmentGroups;
        var groupHtml = ""
            for(var index in departmentGroups){
          groupHtml += "<option value='" + departmentGroups[index].id + "'>" + departmentGroups[index].name + "</option>"
            }
        groupHtml = filterXSS(groupHtml, { 'whiteList': { 'option': ['value'] } })
        $("#department-group").append(groupHtml);
        }
        model.trigger("render");
    };
    $.ajax({
        url: requestUrl,
        type: "post",
        contentType: "application/json; charset=utf-8",
        dataType: "json",
        error: function(data) {
            console.log(data);
        },
        success: successHandler
    });

    addDeparmentView.render();
  },

  handleSearchDeparment: function () {
    var searchterm = $('#serarch-deparment').val();
    systemDeparmentModel.set({"searchterm": searchterm});
    systemDeparmentModel.set({"page": 1});
    systemTabView.handleSystemDeparmentViewLinkClick();
  },

  render: function () {
  }
});

var addDeparmentView;
azkaban.AddDeparmentView = Backbone.View.extend({
  events: {
    "click #deparment-create-btn": "handleAddDeparment"
  },

  initialize: function (settings) {
    console.log("Hide modal error msg");
    // this.loadWebankDeparmentData();
    // this.loadWebankDepartmentData();
  },

  handleAddDeparment: function (evt) {
    console.log("Add Deparment button.");
    var dpId = $("#deparment-id").val();
    var pid = $("#parent-id").val();
    var dpName = $("#deparment-name").val();
    var dpChName = $("#deparment-ch-name").val();
    var orgId = $("#org-id").val();
    var orgName = $("#org-name").val();
    var groupId = $("#department-group").val();

    var tempUploadFlag = $("#add-select-permission-for-upload").val();

    //状态值说明: 1 -> 允许, 2 -> 不允许, 默认为1
    var uploadFlag = 1;

    var requestURL = "/system";

    if(null == dpId || "" == dpId){
      alert(wtssI18n.system.departmentIDReq);
      return;
    }

    if(checkNumber(dpId)){
      return;
    }

    if(checkNumber(pid)){
      return;
    }

    if(null == dpName || "" == dpName){
      alert(wtssI18n.system.departmentEnglishReq);
      return;
    }

    if(checkEnglish(dpName)){
      return;
    }

    if(null == dpChName || "" == dpChName){
      alert(wtssI18n.system.departmentChineseReq);
      return;
    }

    if(null == orgId || "" == orgId){
      alert(wtssI18n.system.officeIDReq);
      return;
    }

    if(checkNumber(orgId)){
      return;
    }

    if(null == orgName || "" == orgName){
      alert(wtssI18n.system.officeNameReq);
      return;
    }
    if(0 == groupId){
        alert(wtssI18n.system.groupPro);
        return;
    }

    if ("0" == tempUploadFlag){
      uploadFlag = 1;
    }

    if ("1" == tempUploadFlag){
      uploadFlag = 2;
    }

    var model = this.model;
    var requestData = {
      "ajax": "addDeparment",
      "deparmentId": dpId,
      "pid": pid ? pid : 0,
      "dpName": dpName,
      "dpChName": dpChName,
      "orgId": orgId,
      "orgName": orgName,
      "groupId" : groupId,
      "uploadFlag" : uploadFlag
    };
    var successHandler = function (data) {
      if (data.error) {
        messageBox.show(data.error.message, 'danger');
        return false;
      } else {
        window.location.href = "/system#system-deparment";
        window.location.reload();
      }
      model.trigger("render");
    };
    $.get(requestURL, requestData, successHandler, "json");
  },

  // loadWebankDepartmentData:function () {
  //   var requestURL =  "/system";
  //   var requestData = {
  //     "ajax":"loadWebankDepartmentSelectData",
  //   };
  //   var successHandler = function(data) {
  //     if (data.error) {
  //       console.log(data.error);
  //     }
  //     else {
  //       var depList = data.webankDepartmentList;
  //       for(var i=0; i<depList.length; i++){
  //         var department = depList[i];
  //         $('#webank-department-select2').append("<option value='" + department.dpId + "'>" + department.dpName + "</option>");
  //       }
  //     }
  //   }
  //
  //   $.ajax({
  //     url: requestURL,
  //     type: "get",
  //     async: false,
  //     data: requestData,
  //     dataType: "json",
  //     error: function(data) {
  //       console.log(data);
  //     },
  //     success: successHandler
  //   });
  // },

  render: function () {
    $("#deparment-id").val("");
    $("#parent-id").val("");
    $("#deparment-name").val("");
    $("#deparment-ch-name").val("");
    $("#org-id").val("");
    $("#org-name").val("");
    $("#department-group").val("");
    //this.loadWebankDepartmentData();
  },
});

var updateDeparmentView;
azkaban.UpdateDeparmentView = Backbone.View.extend({
  events: {
    "click #deparment-update-btn": "handleUpdateSystemDeparment",
    "click #deparment-delete-btn": "handleDeleteSystemDeparment"
  },

  initialize: function (settings) {
    console.log("Hide modal error msg");
    $("#update-deparment-modal-error-msg").hide();
    // this.loadDeparmentData();  //解决系统页面初始化时出现的请求500问题
  },

  handleUpdateSystemDeparment: function (evt) {
    console.log("Update System Deparment button.");
    var dpId = $("#update-deparment-id").val();
    var pid = $("#update-parent-id").val();
    var dpName = $("#update-deparment-name").val();
    var dpChName = $("#update-deparment-ch-name").val();
    var orgId = $("#update-org-id").val();
    var orgName = $("#update-org-name").val();
    var groupId = $("#old-department-group").val();
    var tempUploadFlag = $("#update-select-permission-for-upload").val();

    // 上传权限状态值说明: 1 -> 允许, 2 -> 不允许, 默认为1
    var uploadFlag = 1;

    var requestURL = "/system";

    // if(null == dpId){
    //   alert("部门ID不能为空");
    //   return;
    // }

    if(null == dpName || "" == dpName){
      alert(wtssI18n.system.departmentEnglishReq);
      return;
    }

    if(checkEnglish(dpName)){
      return;
    }

    if(null == dpChName || "" == dpChName){
      alert(wtssI18n.system.departmentChineseReq);
      return;
    }

    if(null == orgId || "" == orgId){
      alert(wtssI18n.system.officeIDReq);
      return;
    }

    if(checkNumber(orgId)){
      return;
    }

    if(null == orgName || "" == orgName){
      alert(wtssI18n.system.officeNameReq);
      return;
    }

    if(0 == groupId){
        alert(wtssI18n.system.groupPro);
        return;
    }

    if ("0" == tempUploadFlag){
      uploadFlag = 1;
    }

    if ("1" == tempUploadFlag){
      uploadFlag = 2;
    }

    var model = this.model;
    var requestData = {
      "ajax": "updateDeparment",
      "deparmentId": dpId,
      "pid": pid ? pid : 0,
      "dpName": dpName,
      "dpChName": dpChName,
      "orgId": orgId,
      "orgName": orgName,
      "groupId":groupId,
      "uploadFlag":uploadFlag
    };
    var successHandler = function (data) {
      if (data.error) {
        $("#update-deparment-modal-error-msg").show();
        $("#update-deparment-modal-error-msg").text(data.error);
        return false;
      } else {
        window.location.href = "/system#system-deparment";
        window.location.reload();
      }
      model.trigger("render");
    };
    $.get(requestURL, requestData, successHandler, "json");
  },

  handleDeleteSystemDeparment: function (evt) {
    deleteDialogView.show(wtssI18n.deletePro.deleteDepartment, wtssI18n.deletePro.whetherDeleteDepartment, wtssI18n.common.cancel, wtssI18n.common.delete, '', function() {
    console.log("Delete System Deparment button.");
    var dpId = $("#update-deparment-id").val();
        var requestURL = "/system";

        // var model = this.model;
    var requestData = {
      "ajax": "deleteDeparment",
      "dpId": dpId,
    };
    var successHandler = function (data) {
      if (data.error) {
        $("#update-deparment-modal-error-msg").show();
        $("#update-deparment-modal-error-msg").text(data.error.message);
        return false;
      } else {
            window.location.href = "/system#system-deparment";
        window.location.reload();
      }
        // model.trigger("render");
    };
    $.get(requestURL, requestData, successHandler, "json");
    });
  },

  loadDeparmentData: function () {

    var dpId = this.model.get("dpId");
    var requestURL = "/system";

    var requestData = {
      "ajax": "getDeparmentById",
      "dpId": dpId,
    };
    var successHandler = function (data) {
      if (data.error) {
        $("#update-deparment-modal-error-msg").show();
        $("#update-deparment-modal-error-msg").text(data.error.message);
        return false;
      } else {
        $("#update-deparment-id").val(data.deparment.dpId);
        $("#update-parent-id").val(data.deparment.pid);
        $("#update-deparment-name").val(data.deparment.dpName);
        $("#update-deparment-ch-name").val(data.deparment.dpChName);
        $("#update-org-id").val(data.deparment.orgId);
        $("#update-org-name").val(data.deparment.orgName);
        $("#old-department-group").val(data.deparment.groupId);

        var uploadFlagDescription;
        var queryUploadFlag = data.deparment.uploadFlag;
        if (queryUploadFlag == 1) {
          uploadFlagDescription = 0;
        }
        if (queryUploadFlag == 2) {
          uploadFlagDescription = 1;
        }
        $("#update-select-permission-for-upload").val(uploadFlagDescription);
      }
    };
    $.get(requestURL, requestData, successHandler, "json");

  },

  render: function () {
    $("#update-deparment-modal-error-msg").hide();
  },
});


function checkNumber(str){
  if(null != str && str.length != 0){
    var reg = /^[0-9]+$/;
    if(!reg.test(str)){
      alert(wtssI18n.system.numberPro);
      return true;
    }
  }
}

function checkEnglish(str){
  if(str.length != 0){
    var reg = /^[a-zA-Z0-9]+( *[a-zA-Z0-9]+)*$/;
    if(!reg.test(str)){
      alert(wtssI18n.system.alphanumericPro);
      return true;
    }
  }
}

