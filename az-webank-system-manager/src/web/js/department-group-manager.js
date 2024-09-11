//项目修改日期数据格式化
var dateFormat = function(time) {
    var date = new Date();
    date.setTime(time);
    var year = date.getFullYear();
    var month = getTwoDigitStr(date.getMonth() + 1);
    var day = getTwoDigitStr(date.getDate());

    var hours = getTwoDigitStr(date.getHours());
    var minutes = getTwoDigitStr(date.getMinutes());
    var second = getTwoDigitStr(date.getSeconds());

    var datestring = year + "-" + month + "-" + day + " " + hours + ":" + minutes + ":" + second;
    return datestring;
}

//处理方法 组装表格和翻页处理
var systemDepartmentGroupView;
azkaban.SystemDepartmentGroupView = Backbone.View.extend({
  events: {
    "click #department-group-pageSelection li": "handleChangePageSelection",
    "click .btn-info": "handleUpdateSystemDepartmentBtn",
  },

  initialize: function(settings) {
    this.model.bind('change:view', this.handleChangeView, this);
    this.model.bind('render', this.render, this);
    this.model.set({page: 1, pageSize: 20});
    this.model.bind('change:page', this.handlePageChange, this);
  },

  render: function(evt) {
    console.log("render");
    // Render page selections
    var tbody = $("#department-group-table-body");
    tbody.empty();

    var department = this.model.get("systemDepartmentGroupPageList");
    var modifyI18n = this.model.get("modify");
    if(!department || department.length == 0){
      $("#department-group-pageSelection").hide();
    }else{
      $("#department-group-pageSelection").show();
    }


    for (var i = 0; i < department.length; ++i) {
      var row = document.createElement("tr");

      //组装数字行
      var tdNum = document.createElement("td");
      $(tdNum).text(i + 1);
      $(tdNum).attr("class","tb-name");
      row.appendChild(tdNum);

        //组装分组id
        var tdId = document.createElement("td");
        $(tdId).text(department[i].id);
        row.appendChild(tdId);

      //组装分组名称
      var tdName = document.createElement("td");
      $(tdName).text(department[i].name);
      $(tdName).attr("style","word-break:break-all;width:250px");
      row.appendChild(tdName);

      //分组描述
      var tdDescription = document.createElement("td");
      $(tdDescription).text(department[i].description);
      $(tdDescription).attr("style","word-break:break-all;width:350px");
      row.appendChild(tdDescription);

      //组装executorHost
      var tdExecutors = document.createElement("td");
      $(tdExecutors).text(department[i].executors.map(function(e){return e.host;}).join(","));
      $(tdExecutors).attr("style","word-break:break-all;width:200px");
      row.appendChild(tdExecutors);

      //创建时间
      var tdCreateTime = document.createElement("td");
      $(tdCreateTime).text(dateFormat(department[i].createTime));
      row.appendChild(tdCreateTime);

      //更新时间
      var tdUpdateTime = document.createElement("td");
      $(tdUpdateTime).text(dateFormat(department[i].updateTime));
      row.appendChild(tdUpdateTime);

      //组装操作行
      var tdAction = document.createElement("td");
      var updateBtn = document.createElement("button");
      $(updateBtn).attr("id", department[i].id + "updateBtn");
      $(updateBtn).attr("name", department[i].id);
      $(updateBtn).attr("class","btn btn-sm btn-info");
      $(updateBtn).text(modifyI18n);
      tdAction.appendChild(updateBtn);
      row.appendChild(tdAction);

      tbody.append(row);
    }

    // this.renderPagination(evt);
  },

  renderPagination: function(evt) {
    var total = this.model.get("total");
    total = total? total : 1;
    var pageSize = this.model.get("pageSize");
    var numPages = Math.ceil(total / pageSize);

    this.model.set({"numPages": numPages});
    var page = this.model.get("page");

    //Start it off
    $("#department-group-pageSelection .active").removeClass("active");

    // Disable if less than 5
    console.log("Num pages " + numPages)
    var i = 1;
    for (; i <= numPages && i <= 5; ++i) {
      $("#department-group-page" + i).removeClass("disabled");
    }
    for (; i <= 5; ++i) {
      $("#department-group-page" + i).addClass("disabled");
    }

    // Disable prev/next if necessary.
    if (page > 1) {
      var prevNum = parseInt(page) - parseInt(1);
      $("#department-group-previous").removeClass("disabled");
      $("#department-group-previous")[0].page = prevNum;
      $("#department-group-previous a").attr("href", "#department-group#page" + prevNum);
    }
    else {
      $("#department-group-previous").addClass("disabled");
    }

    if (page < numPages) {
      var nextNum = parseInt(page) + parseInt(1);
      $("#department-group-next")[0].page = nextNum;
      $("#department-group-next").removeClass("disabled");
      $("#department-group-next a").attr("href", "#department-group#page" + nextNum);
    }
    else {
      var nextNum = parseInt(page) + parseInt(1);
      $("#department-group-next").addClass("disabled");
    }

    // Selection is always in middle unless at barrier.
    var startPage = 0;
    var selectionPosition = 0;
    if (page < 3) {
      selectionPosition = page;
      startPage = 1;
    }
    else if (page == numPages && page != 3 && page != 4) {
      selectionPosition = 5;
      startPage = numPages - 4;
    }
    else if (page == numPages - 1 && page != 3) {
      selectionPosition = 4;
      startPage = numPages - 4;
    }
    else if (page == 4) {
      selectionPosition = 4;
      startPage = page - 3;
    }
    else if (page == 3) {
      selectionPosition = 3;
      startPage = page - 2;
    }
    else {
      selectionPosition = 3;
      startPage = page - 2;
    }

    $("#department-group-page"+selectionPosition).addClass("active");
    $("#department-group-page"+selectionPosition)[0].page = page;
    var selecta = $("#department-group-page" + selectionPosition + " a");
    selecta.text(page);
    selecta.attr("href", "#department-group#page" + page);

    for (var j = 0; j < 5; ++j) {
      var realPage = startPage + j;
      var elementId = "#department-group-page" + (j+1);

      $(elementId)[0].page = realPage;
      var a = $(elementId + " a");
      a.text(realPage);
      a.attr("href", "#department-group#page" + realPage);
    }
  },

  handleChangePageSelection: function(evt) {
    if ($(evt.currentTarget).hasClass("disabled")) {
      return;
    }
    var page = evt.currentTarget.page;
    this.model.set({"page": page});
  },

  handleChangeView: function(evt) {
    // if (this.init) {
    //   return;
    // }
    console.log("init");
    this.handlePageChange(evt);
    this.init = true;
  },

  handlePageChange: function(evt) {
    var start = this.model.get("page") - 1;
    var pageSize = this.model.get("pageSize");
    var requestURL = contextURL + "/system?ajax=fetchAllDepartmentGroup";
    var searchString = $('#serarch-department-group').val();
    if(!searchString){
      searchString="";
    }else{
      start = 0;
    }

    var model = this.model;
    var requestData = {
      "ajax": "fetchAllDepartmentGroup",
      "start": start,
      "pageSize": pageSize,
      "searchterm": searchString,
    };
    var successHandler = function(data) {
      model.set({
        "systemDepartmentGroupPageList": data.departmentGroups,
        "modify": data.modify,
        "total": data.total
      });
      model.trigger("render");
    };
    $.ajax({
        url: requestURL,
        type: "post",
        contentType: "application/json; charset=utf-8",
        dataType: "json",
        error: function(data) {
            console.log(data.error);
        },
        success: successHandler
    });
  },

  handleUpdateSystemDepartmentBtn: function(evt) {
    console.log("click upload project");
      //初始化
    $("#old-groupId").val("");
    $("#new-groupId").val("");
    $("#old-groupName").val("");
    $("#old-description").val("");
    $('#update-department-group-modal-error-msg').hide();
    $('#old-executorTable tbody .jobSkipTr').remove();
    $('#old-add-executor-btn').attr('disabled', false);
      //初始化
    var groupId = evt.currentTarget.name;
    systemDepartmentGroupModel.set({"groupId": groupId});
    $('#update-department-group-panel').modal();
    updateDepartmentGroupView.render();
    var requestURL = contextURL + "/system?ajax=fetchExecutors";
    var model = this.model;
    var successHandler = function (data) {
        if (data.error) {
            $("#add-department-group-modal-error-msg").show();
            $("#add-department-group-modal-error-msg").text(data.error);
            return false;
        } else {
            // executors
          model.set({"executors": data.executors});
        }
    };
    $.ajax({
      url: requestURL,
      type: "get",
      async: false,
      contentType: "application/json; charset=utf-8",
      dataType: "json",
      error: function(data) {
          console.log(data);
      },
      success: successHandler
    });
    updateDepartmentGroupView.loadDepartmentData();
  },


});

var departmentGroupOptionsView;
azkaban.DepartmentGroupOptionsView = Backbone.View.extend({
  events: {
    "click #add-department-group": "handleAddDepartment",
    "click #search-department-group-btn": "handleSearchDepartment",
  },

  initialize: function (settings) {
  },

  handleAddDepartment: function (evt) {
    console.log("click upload project");
    $('#add-department-group-panel').modal();
    //初始化
    $('#id').val("");
    $('#groupName').val("");
    $('#description').val("");
    $('#executorTable tbody .jobSkipTr').remove();
    $('#add-department-group-modal-error-msg').hide();
    //初始化
    var requestURL = contextURL + "/system?ajax=fetchExecutors";
    var model = this.model;
    var successHandler = function (data) {
        if (data.error) {
            $("#add-department-group-modal-error-msg").show();
            $("#add-department-group-modal-error-msg").text(data.error);
            return false;
        } else {
            // executors
            model.set({"executors": data.executors});
        }
    };
    $.ajax({
        url: requestURL,
        type: "get",
        contentType: "application/json; charset=utf-8",
        dataType: "json",
        error: function(data) {
            console.log(data);
        },
        success: successHandler
    });
  },

  handleSearchDepartment: function () {
    var searchterm = $('#serarch-department').val();
    systemDepartmentGroupModel.set({"searchterm": searchterm});

    // systemTabView.handleSystemDepartmentViewLinkClick();
  },

  render: function () {
  }
});

var addDepartmentGroupView;
azkaban.AddDepartmentGroupView = Backbone.View.extend({
  events: {
    "click #department-group-create-btn": "handleAddDepartment",
    "click #add-executor-btn": "handleAddExecutor",
    "click #executorTable .btn-danger": "handleDeleteExecutor"
  },

  initialize: function (settings) {
    console.log("Hide modal error msg");
    $("#add-department-group-modal-error-msg").hide();
  },

  handleDeleteExecutor: function(evt){
      var curTarget = evt.currentTarget;
      var row = curTarget.parentElement.parentElement.parentElement;
      $(row).remove();
      $('#add-executor-btn').attr('disabled', false);
  },

  handleAddExecutor: function (evt) {
      var executors = this.model.get("executors");
      var executorTr = $("#executorTable tr").length - 1;
      if(executorTr == executors.length){
          $('#add-executor-btn').attr('disabled','disabled');
      }
      var executorTable = document.getElementById("executorTable").tBodies[0];
      var trExecutor = executorTable.insertRow(executorTable.rows.length-1);

      $(trExecutor).addClass('jobSkipTr');
      //设置失败重跑 job 名称
      var cExecutor = trExecutor.insertCell(-1);

      var idSelect = $("<select></select>");
      idSelect.attr("class", "form-control");
      idSelect.attr("style", "width: 100%");
      for(var i=0; i < executors.length; i++) {
        idSelect.append("<option value='" + executors[i].id + "'>" + executors[i].host + "</option>");
      }
      $(cExecutor).append(idSelect);

      //删除按钮
      var cDelete = trExecutor.insertCell(-1);
      var remove = document.createElement("div");
      $(remove).addClass("center-block").addClass('remove-btn');
      var removeBtn = document.createElement("button");
      $(removeBtn).attr('type', 'button');
      $(removeBtn).addClass('btn').addClass('btn-sm').addClass('btn-danger');
      $(removeBtn).text('Delete');
      $(remove).append(removeBtn);
      cDelete.appendChild(remove);
  },

  handleAddDepartment: function (evt) {
    console.log("Add Department group button.");
    var groupId = $("#id").val();
    var groupName = $('#groupName').val();
    var description = $('#description').val();
    var executorIds = [];
    $('#executorTable select').each(function(i, e){return executorIds.push(parseInt($(e).val()));});
    var tmp = Array.from(new Set(executorIds))

    if(!(isNumber(groupId) && parseInt(groupId) <= 2147483647)){
        alert(wtssI18n.system.groupIDFormat);
        return;
    }

    if(null == groupName || "" == groupName){
        alert(wtssI18n.system.groupNameReq);
        return;
    }
    if(checkEnglish(groupName)){
       return;
    }
    if(groupName.length > 128){
        alert(wtssI18n.system.groupNameLength);
        return;
    }
    if(null == description || "" == description){
        alert(wtssI18n.system.groupDescriptionReq);
        return;
    }
    if(description.length > 256){
        alert(wtssI18n.system.descriptionFormat);
        return;
    }
    if(null == executorIds || executorIds.length == 0){
        alert(wtssI18n.system.actuatorPro);
        return;
    }
    if(tmp.length != executorIds.length){
        alert(wtssI18n.system.sameActuatorPro);
        return;
    }
    var requestData = {
      "id": groupId,
      "name": groupName,
      "description": description,
      "executorIds": executorIds
    };
    var requestUrl = contextURL + "/system?ajax=addDepartmentGroup";
    var model = this.model;
    var successHandler = function (data) {
        if (data.error) {
            $("#add-department-group-modal-error-msg").show();
            $("#add-department-group-modal-error-msg").text(data.error);
            return false;
        } else {
            window.location.href = contextURL + "/system#department-group";
            window.location.reload();
        }
        model.trigger("render");
    };
    $.ajax({
        url: requestUrl,
        type: "post",
        contentType: "application/json; charset=utf-8",
        data: JSON.stringify(requestData),
        dataType: "json",
        error: function(data) {
            console.log(data);
        },
        success: successHandler
    });
  },

  render: function () {
    $("#add-department-group-modal-error-msg").hide();
  },
});

var updateDepartmentGroupView;
azkaban.UpdateDepartmentGroupView = Backbone.View.extend({
  events: {
    "click #department-group-update-btn": "handleUpdateSystemDepartment",
    "click #department-group-delete-btn": "handleDeleteSystemDepartment",
    "click #old-add-executor-btn": "handleAddExecutor",
    "click #old-executorTable .btn-danger": "handleDeleteExecutor"
  },

  initialize: function (settings) {
    console.log("Hide modal error msg");
    $("#update-department-group-modal-error-msg").hide();
    // this.loadDepartmentData();  //解决系统页面初始化时出现的请求500问题
  },
  handleDeleteExecutor: function(evt){
      var curTarget = evt.currentTarget;
      var row = curTarget.parentElement.parentElement.parentElement;
      $(row).remove();
      $('#old-add-executor-btn').attr('disabled', false);
  },

  handleAddExecutor: function (evt) {
      var executors = this.model.get("executors");
      var executorTr = $("#old-executorTable tr").length - 1;
      if(executorTr == executors.length){
          $('#old-add-executor-btn').attr('disabled','disabled');
      }
      var executorTable = document.getElementById("old-executorTable").tBodies[0];
      var trExecutor = executorTable.insertRow(executorTable.rows.length-1);

      $(trExecutor).addClass('jobSkipTr');
      //设置失败重跑 job 名称
      var cExecutor = trExecutor.insertCell(-1);

      var idSelect = $("<select></select>");
      idSelect.attr("class", "form-control");
      idSelect.attr("style", "width: 100%");
      for(var i=0; i < executors.length; i++) {
          idSelect.append("<option value='" + executors[i].id + "'>" + executors[i].host + "</option>");
      }
      $(cExecutor).append(idSelect);

      //删除按钮
      var cDelete = trExecutor.insertCell(-1);
      var remove = document.createElement("div");
      $(remove).addClass("center-block").addClass('remove-btn');
      var removeBtn = document.createElement("button");
      $(removeBtn).attr('type', 'button');
      $(removeBtn).addClass('btn').addClass('btn-sm').addClass('btn-danger');
      $(removeBtn).text('Delete');
      $(remove).append(removeBtn);
      cDelete.appendChild(remove);
  },

  handleUpdateSystemDepartment: function (evt) {
    console.log("Update System Department button.");
    var oldGroupId = $('#old-groupId').val();
    var newGroupId = $('#new-groupId').val();
    var requestUrl = contextURL + "/system?ajax=updateDepartmentGroup";
    var groupName = $('#old-groupName').val();
    var description = $('#old-description').val();
    var executorIds = [];
    $('#old-executorTable select').each(function(i, e){return executorIds.push(parseInt($(e).val()));});
    var tmp = Array.from(new Set(executorIds));
    if(!(isNumber(newGroupId) && parseInt(newGroupId) <= 2147483647)){
        alert(wtssI18n.system.groupIDFormat);
        return;
    }

    if(null == groupName || "" == groupName){
        alert(wtssI18n.system.groupNameReq);
        return;
    }
    if(checkEnglish(groupName)){
        return;
    }
    if(groupName.length > 128){
        alert(wtssI18n.system.groupNameLength);
        return;
    }
    if(null == description || "" == description){
        alert(wtssI18n.system.groupDescriptionReq);
        return;
    }
    if(description.length > 256){
        alert(wtssI18n.system.descriptionFormat);
        return;
    }
    if(null == executorIds || executorIds.length == 0){
        alert(wtssI18n.system.actuatorPro);
        return;
    }
    if(tmp.length != executorIds.length){
        alert(wtssI18n.system.sameActuatorPro);
        return;
    }
    var requestData = {
        "oldId": oldGroupId,
        "id": newGroupId,
        "name": groupName,
        "description": description,
        "executorIds": executorIds
    };
    var model = this.model;
    var successHandler = function (data) {
        if (data.error) {
            $("#update-department-group-modal-error-msg").show();
            $("#update-department-group-modal-error-msg").text(data.error);
            return false;
        } else {
            window.location.href = contextURL + "/system#department-group";
            window.location.reload();
        }
        model.trigger("render");
    };
    $.ajax({
        url: requestUrl,
        type: "post",
        contentType: "application/json; charset=utf-8",
        data: JSON.stringify(requestData),
        dataType: "json",
        error: function(data) {
            console.log(data);
        },
        success: successHandler
    });
  },

  handleDeleteSystemDepartment: function (evt) {
    console.log("Delete System Department button.");
    var groupId = $('#old-groupId').val();
    var requestUrl = contextURL + "/system?ajax=deleteDepartmentGroup";
    var model = this.model;
    var successHandler = function (data) {
        if (data.error) {
            $("#update-department-group-modal-error-msg").show();
            $("#update-department-group-modal-error-msg").text(data.error);
            return false;
        } else {
            window.location.href = contextURL + "/system#department-group";
            window.location.reload();
        }
        model.trigger("render");
    };
    $.ajax({
        url: requestUrl,
        type: "post",
        contentType: "application/json; charset=utf-8",
        data: JSON.stringify({id: groupId}),
        dataType: "json",
        error: function(data) {
            console.log(data);
        },
        success: successHandler
    });
  },

  loadDepartmentData: function () {

    var groupId = this.model.get("groupId");
    var requestURL = contextURL + "/system?ajax=fetchDepartmentGroupById";

    var requestData = {
      "id": groupId,
    };
    var model = this.model;
    var successHandler = function (data) {
      if (data.error) {
        $("#update-department-group-modal-error-msg").show();
        $("#update-department-group-modal-error-msg").text(data.error);
        return false;
      } else {
        $("#old-groupId").val(data.departmentGroup.id);
        $("#new-groupId").val(data.departmentGroup.id);
        $("#old-groupName").val(data.departmentGroup.name);
        $("#old-description").val(data.departmentGroup.description);
        var ids = data.departmentGroup.executorIds;
        for(var index in ids){
            var executors = model.get("executors");
            var executorTr = $("#old-executorTable tr").length - 1;
            if(executorTr == executors.length){
                $('#add-executor-btn').attr('disabled','disabled');
            }
            var executorTable = document.getElementById("old-executorTable").tBodies[0];
            var trExecutor = executorTable.insertRow(executorTable.rows.length-1);

            $(trExecutor).addClass('jobSkipTr');
            //设置失败重跑 job 名称
            var cExecutor = trExecutor.insertCell(-1);

            var idSelect = $("<select></select>");
            idSelect.attr("class", "form-control");
            idSelect.attr("style", "width: 100%");
            for(var i=0; i < executors.length; i++) {
                idSelect.append("<option value='" + executors[i].id + "'>" + executors[i].host + "</option>");
            }
            $(cExecutor).append(idSelect);
            idSelect.val(ids[index]);

            //删除按钮
            var cDelete = trExecutor.insertCell(-1);
            var remove = document.createElement("div");
            $(remove).addClass("center-block").addClass('remove-btn');
            var removeBtn = document.createElement("button");
            $(removeBtn).attr('type', 'button');
            $(removeBtn).addClass('btn').addClass('btn-sm').addClass('btn-danger');
            $(removeBtn).text('Delete');
            $(remove).append(removeBtn);
            cDelete.appendChild(remove);
        }
      }
    };
    $.ajax({
        url: requestURL,
        type: "post",
        contentType: "application/json; charset=utf-8",
        data: JSON.stringify(requestData),
        dataType: "json",
        error: function(data) {
            console.log(data);
        },
        success: successHandler
    });

  },

  render: function () {
    $("#update-department-group-modal-error-msg").hide();
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
        var reg = /^[a-zA-Z][a-zA-z0-9_ ]*$/;
        if(!reg.test(str)){
            alert(wtssI18n.system.parameterNameFormat);
            return true;
        }
    }
}


function isNumber(str){
    if(str.length != 0){
        // min 1 max 2147483647
        var reg = /^[1-9][0-9]{0,9}$/;
        return reg.test(str);
    }
    return false;
}
