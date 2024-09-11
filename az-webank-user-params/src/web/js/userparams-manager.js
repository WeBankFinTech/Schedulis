
$.namespace('azkaban');

var showDialog = function (title, message) {
  $('#messageTitle').text(title);
  $('#messageBox').text(message);
  $('#messageDialog').modal();
}

$(function() {
    systemDepartmentGroupModel = new azkaban.SystemDepartmentGroupModel();
  // 分组
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
    //分组
});

//用于保存浏览数据，切换页面也能返回之前的浏览进度。
var systemDepartmentGroupModel;
azkaban.SystemDepartmentGroupModel = Backbone.Model.extend({});


// ----------------------------


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
        console.log("init");
        this.model.bind('change:view', this.handleChangeView, this);
        this.model.bind('render', this.render, this);
        this.model.trigger("change:view");
        // this.model.set({page: 1, pageSize: 20});
        // this.model.bind('change:page', this.handlePageChange, this);
    },

    render: function(evt) {
        console.log("render");
        // Render page selections
        var tbody = $("#department-group-table-body");
        tbody.empty();

        var userparams = this.model.get("userparams");
        var modifyI18n = this.model.get("modify");
        if(!userparams || userparams.length == 0){
            $("#department-group-pageSelection").hide();
        }else{
            $("#department-group-pageSelection").show();
        }


        for (var i = 0; i < userparams.length; ++i) {
            var row = document.createElement("tr");

            //组装数字行
            var tdNum = document.createElement("td");
            $(tdNum).text(i + 1);
            $(tdNum).attr("class","tb-name");
            row.appendChild(tdNum);

            //组装keyname
            var tdName = document.createElement("td");
            $(tdName).text(userparams[i].key);
            $(tdName).attr("style","word-break:break-all;width:250px");
            row.appendChild(tdName);

            //描述
            var tdDescription = document.createElement("td");
            $(tdDescription).text(userparams[i].description);
            $(tdDescription).attr("style","word-break:break-all;width:350px");
            row.appendChild(tdDescription);
            //值
            var tdValue = document.createElement("td");
            $(tdValue).text(userparams[i].value);
            $(tdValue).attr("style","word-break:break-all;width:350px");
            row.appendChild(tdValue);

            //owner
            var tdOwner = document.createElement("td");
            $(tdOwner).text(userparams[i].owner);
            $(tdOwner).attr("style","word-break:break-all;width:100px");
            row.appendChild(tdOwner);

            //只读用户
            var tdUser = document.createElement("td");
            $(tdUser).text(userparams[i].users.map(function(e){return e.userId;}).join(","));
            $(tdUser).attr("style","word-break:break-all;width:200px");
            row.appendChild(tdUser);

            //创建时间
            var tdCreateTime = document.createElement("td");
            $(tdCreateTime).text(dateFormat(userparams[i].createTime));
            row.appendChild(tdCreateTime);

            //更新时间
            var tdUpdateTime = document.createElement("td");
            $(tdUpdateTime).text(dateFormat(userparams[i].updateTime));
            row.appendChild(tdUpdateTime);

            //组装操作行
            var tdAction = document.createElement("td");
            var updateBtn = document.createElement("button");
            $(updateBtn).attr("id", userparams[i].id + "updateBtn");
            $(updateBtn).attr("name", userparams[i].id);
            $(updateBtn).attr("class","btn btn-sm btn-info");
            $(updateBtn).text(modifyI18n);
            if(wtssUser != userparams[i].owner){
                $(updateBtn).attr('disabled',true);
            }
            tdAction.appendChild(updateBtn);
            row.appendChild(tdAction);

            tbody.append(row);
        }

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
        var requestURL = contextURL + "/userparams?ajax=fetchAllUserVariable";
        var model = this.model;
        var successHandler = function(data) {
            model.set({
                "userparams": data.userparams,
                "modify": data.modify
            });
            model.trigger("render");
        };
        $.ajax({
            url: requestURL,
            type: "post",
            data: JSON.stringify({"owner": wtssUser}),
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
        $("#old-id").val("");
        $("#old-keyNname").val("");
        $("#old-description").val("");
        $("#old-value").val("");
        $('#update-department-group-modal-error-msg').hide();
        $('#old-executorTable tbody .jobSkipTr').remove();
        //初始化
        var id = evt.currentTarget.name;
        systemDepartmentGroupModel.set({"id": id});
        $('#update-department-group-panel').modal();
        updateDepartmentGroupView.render();
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
        $('#groupName').val("");
        $('#description').val("");
        $('#executorTable tbody .jobSkipTr').remove();
        $('#add-department-group-modal-error-msg').hide();
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
        var executorTable = document.getElementById("executorTable").tBodies[0];
        var trExecutor = executorTable.insertRow(executorTable.rows.length-1);

        $(trExecutor).addClass('jobSkipTr');
        //设置失败重跑 job 名称
        var cExecutor = trExecutor.insertCell(-1);

        var idSelect = $("<select></select>");
        idSelect.attr("class", "form-control add-webank-user-select");
        idSelect.attr("style", "width: 100%");
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
        this.loadWebankUserData();
    },

    handleAddDepartment: function (evt) {
        console.log("Add user param group button.");
        var keyName = $('#keyName').val();
        var description = $('#description').val();
        var value = $('#value').val();
        var users = [];
        $('#executor-view .add-webank-user-select').each(function(i,e){ users.push($(e).val());});
        var tmp = Array.from(new Set(users));

        if(null == keyName || "" == keyName){
            alert(wtssI18n.param.parameterNameReq);
            return;
        }
        if(checkEnglish(keyName)){
            return;
        }

        if(null == description || "" == description){
            alert(wtssI18n.param.descriptionReq);
            return;
        }
        if(null == value || "" == value){
            alert(wtssI18n.param.parameterValueReq);
            return;
        }
        if(users.length != 0){
            if(tmp.length != users.length){
                alert(wtssI18n.param.sameUserPro);
                return;
            }
            if(tmp.indexOf(wtssUser) != -1){
                alert(wtssI18n.param.notChoosePro + wtssUser);
                return;
            }
            users = users.map(function(i){ return {'userId': i};});
        }
        var requestData = {
            "key": keyName,
            "description": description,
            "value": value,
            "users" : users
        };
        var requestUrl = contextURL + "/userparams?ajax=addUserVariable";
        var model = this.model;
        var successHandler = function (data) {
            if (data.error) {
                $("#add-department-group-modal-error-msg").show();
                $("#add-department-group-modal-error-msg").text(data.error);
                return false;
            } else {
                window.location.href = contextURL + "/userparams";
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
    loadWebankUserData: function () {

        $(".add-webank-user-select").select2({
            placeholder: wtssI18n.system.userPro,//默认文字提示
            multiple: false,
            width: 'resolve',
            //language: "zh-CN",
            tags: true,//允许手动添加
            //allowClear: true,//允许清空
            escapeMarkup: function (markup) {
                return markup;
            }, //自定义格式化防止XSS注入
            minimumInputLengt: 1,//最少输入多少字符后开始查询
            formatResult: function formatRepo(repo) {
                return repo.text;
            },//函数用来渲染结果
            formatSelection: function formatRepoSelection(repo) {
                return repo.text;
            },//函数用于呈现当前的选择
            ajax: {
                type: 'GET',
                url: contextURL + "/userparams",
                dataType: 'json',
                delay: 250,
                data: function (params) {
                    var query = {
                        ajax: "loadWtssUser",
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
    }

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
        console.log(wtssUser);
    },
    handleDeleteExecutor: function(evt){
        var curTarget = evt.currentTarget;
        var row = curTarget.parentElement.parentElement.parentElement;
        $(row).remove();
        $('#old-add-executor-btn').attr('disabled', false);
    },

    handleAddExecutor: function (evt) {
        var executorTable = document.getElementById("old-executorTable").tBodies[0];
        var trExecutor = executorTable.insertRow(executorTable.rows.length-1);

        $(trExecutor).addClass('jobSkipTr');
        //设置失败重跑 job 名称
        var cExecutor = trExecutor.insertCell(-1);

        var idSelect = $("<select></select>");
        idSelect.attr("class", "form-control webank-user-select");
        idSelect.attr("style", "width: 100%");
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
        this.loadWebankUserData();
    },

    handleUpdateSystemDepartment: function (evt) {
        console.log("Update user param Department button.");
        var id = $('#old-id').val();
        var requestUrl = contextURL + "/userparams?ajax=updateUpdateUserVariable";
        var keyNname = $('#old-keyNname').val();
        var description = $('#old-description').val();
        var value = $('#old-value').val();
        var users = [];
        $('#old-executor-view .webank-user-select').each(function(i,e){return users.push($(e).val());});
        var tmp = Array.from(new Set(users));

        if(null == keyNname || "" == keyNname){
            alert(wtssI18n.param.parameterNameReq);
            return;
        }
        if(checkEnglish(keyNname)){
            return;
        }
        if(null == description || "" == description){
            alert(wtssI18n.param.descriptionReq);
            return;
        }
        if(null == value || "" == value){
            alert(wtssI18n.param.parameterValueReq);
            return;
        }
        if(users.length != 0){
            if(tmp.length != users.length){
                alert(wtssI18n.param.sameUserPro);
                return;
            }
            if(tmp.indexOf(wtssUser) != -1){
                alert(wtssI18n.param.notChoosePro + wtssUser);
                return;
            }
            users = users.map(function(i){ return {'userId': i};});
        }
        var requestData = {
            "id": id,
            "key": keyNname,
            "description": description,
            "value": value,
            "users": users
        };
        var model = this.model;
        var successHandler = function (data) {
            if (data.error) {
                $("#update-department-group-modal-error-msg").show();
                $("#update-department-group-modal-error-msg").text(data.error);
                return false;
            } else {
                window.location.href = contextURL + "/userparams";
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
        console.log("Delete user param Department button.");
        var id = $('#old-id').val();
        var requestUrl = contextURL + "/userparams?ajax=deleteUserVariable";
        var model = this.model;
        var successHandler = function (data) {
            if (data.error) {
                $("#update-deparment-modal-error-msg").show();
                $("#update-deparment-modal-error-msg").text(data.error);
                return false;
            } else {
                window.location.href = contextURL + "/userparams";
                window.location.reload();
            }
            model.trigger("render");
        };
        $.ajax({
            url: requestUrl,
            type: "post",
            contentType: "application/json; charset=utf-8",
            data: JSON.stringify({'id': id}),
            dataType: "json",
            error: function(data) {
                console.log(data);
            },
            success: successHandler
        });
    },

    loadDepartmentData: function () {

        var id = this.model.get("id");
        var requestURL = contextURL + "/userparams?ajax=fetchUserVariableById";

        var requestData = {
            "id": id,
        };
        var model = this.model;
        var successHandler = function (data) {
            if (data.status && data.status == 'error') {
                return false
            }
            if (data.error) {
                $("#update-department-group-modal-error-msg").show();
                $("#update-department-group-modal-error-msg").text(data.error);
                return false;
            } else {
                $("#old-id").val(data.userparam.id);
                $("#old-keyNname").val(data.userparam.key);
                $("#old-description").val(data.userparam.description);
                $("#old-value").val(data.userparam.value);
                var users = data.userparam.users;
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

    loadWebankUserData: function () {

        $(".webank-user-select").select2({
            placeholder: wtssI18n.system.userPro,//默认文字提示
            multiple: false,
            width: 'resolve',
            //language: "zh-CN",
            tags: true,//允许手动添加
            //allowClear: true,//允许清空
            escapeMarkup: function (markup) {
                return markup;
            }, //自定义格式化防止XSS注入
            minimumInputLengt: 1,//最少输入多少字符后开始查询
            formatResult: function formatRepo(repo) {
                return repo.text;
            },//函数用来渲染结果
            formatSelection: function formatRepoSelection(repo) {
                return repo.text;
            },//函数用于呈现当前的选择
            ajax: {
                type: 'GET',
                url: contextURL + "/userparams",
                dataType: 'json',
                delay: 250,
                data: function (params) {
                    var query = {
                        ajax: "loadWtssUser",
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
    }
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

