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
// 删除嵌入dss隐藏头部标识
sessionStorage.removeItem('hideHead')


var projectHeaderView;
azkaban.ProjectHeaderView = Backbone.View.extend({
    events: {
        "click #create-project-btn": "handleCreateProjectJob",
        "click #project-handover-btn": "handleCreateHandover",
        // "change #orderSelect": "handleOrderSelection",
        // "click #quickSearchBtn": "handerSearchText",
        // "keyup #search-textbox": "keyupSearch",
        "click #filter-search-btn": "openFilterModal",
        "click #project-alarm-btn": "openAlarmModal",
        "click #batch-upload-bussiness-job-btn": "handleUploadBusinessJob",
    },

    initialize: function(settings) {
        var showAnnouce = localStorage.getItem('showAnnouce')
        if (showAnnouce !== 'false'){
            messageModal.show('公告', '请使用系统用户调度和执行生产系统相关批量（影响业务使用的批量），禁止使用实名用户')
            localStorage.setItem('showAnnouce', 'false')
        }
    },

    handleCreateProjectJob: function(evt) {
        $("#modal-error-msg").text("").hide();
        $("#path").val("");
        $("#description").val("");
        // 需要校验是否具有上传权限 1:允许创建, 2:不允许创建
        var requestURL = "/manager?ajax=checkUserCreateProjectPermission";
        $.ajax({
            url: requestURL,
            type: "get",
            async: false,
            dataType: "json",
            success: function(data) {
                if (data["createProjectFlag"] == 1) {
                    console.log("have permission, click create project");
                    $('#create-project-modal').modal();
                } else if (data["createProjectFlag"] == 2) {
                    $('#user-create-project-permit-panel').modal();
                }
            }
        });
    },

    // 上传应用信息
    handleUploadBusinessJob: function(evt) {
        $('#upload-business-modal').modal();
    },

    openFilterModal() {
        $("#adv-filter").modal()
        advFilterView.render()
    },
    handleCreateHandover() {
        var checkedProjectList = sessionStorage.getItem('checkedProjectList') ? JSON.parse(sessionStorage.getItem('checkedProjectList')) : []
        if (checkedProjectList.length === 0) {
            alert('请先勾选要交接的项目');
            return
        }
        $('#create-handover-modal').modal();
        var tdHtml = ''
        for (var i = 0; i < checkedProjectList.length; i++) {
            tdHtml += "<tr  class='bodyTr" + i + "'><td style='width:450px;line-height:28px;word-break: break-all;'>" + checkedProjectList[i] + "</td>" +
                "<td  style='width: 150px;'><select id='handover-select" + i + "' type='text' class='handover form-control'></select></td>" +
                "<td><button type='button' class='btn btn-sm remove-project-btn btn-danger' index='" + i + "'>Delete</button></td></tr>"

        }
        tdHtml = filterXSS(tdHtml, {
            whiteList: {
                tr: ["class"],
                td: ["style"],
                select: ["id", "type", "class"],
                button: ["type", "class", "index"],
            }
        })
        $("#project-handover-body").html('').html(tdHtml);
        this.deletehandover()
        for (var i = 0; i < checkedProjectList.length; i++) {
            this.loadUserNametData(`handover-select${i}`)
        }
    },
    deletehandover() {
        $("#project-handover-body .remove-project-btn").click(function(e) {
            var checkedProjectList = JSON.parse(sessionStorage.getItem('checkedProjectList'))
            var index = e.target.getAttribute('index')
            $("#project-handover-body .bodyTr" + index).remove()
                //取消选中
            var checkbox = $("#project-list .project-checkbox")
            for (var j = 0; j < checkbox.length; j++) {
                if (checkbox[j].value === checkedProjectList[index]) {
                    checkbox[j].checked = false
                    break
                }
            }
            checkedProjectList.splice(index, 1)
            sessionStorage.setItem('checkedProjectList', JSON.stringify(checkedProjectList))

        })
    },
    loadUserNametData(id, multiple) {
        $(`#${id}`).select2({
            multiple: !!multiple,
            width: 'resolve',
            //language: "zh-CN",
            tags: false, //允许手动添加
            allowClear: false, //允许清空
            escapeMarkup: function(markup) { return markup; }, //自定义格式化防止XSS注入
            minimumInputLengt: 1, //最少输入多少字符后开始查询
            formatResult: function formatRepo(repo) { return repo.id; }, //函数用来渲染结果
            formatSelection: function formatRepoSelection(repo) { return repo.text; }, //函数用于呈现当前的选择
            ajax: {
                type: 'GET',
                url: "/userparams",
                dataType: 'json',
                delay: 50,
                data: function(params) {
                    var query = {
                        ajax: "loadWtssUser",
                        serach: params.term,
                        page: params.page || 1,
                        pageSize: 10
                    }
                    return query;
                },
                processResults: function(data, params) {
                    params.page = params.page || 1;
                    return {
                        results: data.webankUserList,
                        pagination: {
                            more: (params.page * 10) < data.webankUserTotalCount
                        }
                    }
                },
                cache: true
            },
            language: 'zh-CN'
        });
    },
    loadProjectNametData(id) {
        $(`#${id}`).select2({
            multiple: false,
            width: 'resolve',
            //language: "zh-CN",
            tags: false, //允许手动添加
            allowClear: false, //允许清空
            escapeMarkup: function(markup) { return markup; }, //自定义格式化防止XSS注入
            minimumInputLengt: 1, //最少输入多少字符后开始查询
            formatResult: function formatRepo(repo) { return repo.id; }, //函数用来渲染结果
            formatSelection: function formatRepoSelection(repo) { return repo.text; }, //函数用于呈现当前的选择
            ajax: {
                type: 'GET',
                url: "/index",
                dataType: 'json',
                delay: 50,
                data: function(params) {
                    const query = {
                        ajax: "fetchProjectPage",
                        start: params.page ?  params.page - 1 : 0,
                        length: 20,
                        pageNum: params.page || 1,
                        order: "orderProjectName",
                        projectsType: !params.term ? "all" : "doaction",
                    }
                    if (params.term) {
                        query.searchterm = params.term;
                        query.doaction = true;
                        query.group = false;
                        query.all = true;
                    }
                    return query;
                },
                processResults: function(data, params) {
                    params.page = params.from || 1;
                    return {
                        results: data.projectList.map(function (v) {
                            return { id: v.name, text: v.name };
                        }),
                        pagination: {
                            more: (params.page * 20) < data.total
                        }
                    }
                },
                cache: true
            },
            language: 'zh-CN'
        });
    },
    insertAlarmRow: function(index, isDetail) {
        const projectAlarmbody = document.getElementById("project-alarm-body");
        // tr
        const trDom = document.createElement("tr");
        $(trDom).attr("class", `bodyTr${index}`).attr("isDetail", isDetail).attr("index", index);
        //项目名称
        const projectDom = document.createElement("td");
        const selectDom = document.createElement("select");
        $(selectDom).attr("id", `alarm-project${index}`).attr("type", "text").attr("class", "project-name form-control").attr("style", "width: 100%");
        $(projectDom).attr("class", "alarm-project").append(selectDom);
        trDom.appendChild(projectDom);
        //告警类型
        const typeDom = document.createElement("td");
        const typeDiv = document.createElement("div");
        typeDiv.setAttribute("class", "project-alarm-type");
        const itmsDom = document.createElement("input");
        const itmsLabel = document.createElement("label");
        const emailDom = document.createElement("input");
        const emailLabel = document.createElement("label");
        $(itmsDom).attr("type", "checkbox").attr("id", `alarmIMS${index}`).attr("name", `alarmType${index}`).attr("value", "1");
        $(itmsLabel).attr("class", "margin-r15").text(" IMS");
        $(emailDom).attr("type", "checkbox").attr("id", `alarmEmail${index}`).attr("name", `alarmType${index}`).attr("value", "2");
        emailLabel.innerText = " 邮件";
        $(typeDiv).append(itmsDom).append(itmsLabel).append(emailDom).append(emailLabel);
        typeDom.appendChild(typeDiv);
        trDom.appendChild(typeDom);
        //告警用户
        const userDom = document.createElement("td");
        const selectUserDom = document.createElement("select");
        $(selectUserDom).attr("id", `alarmer-select${index}`).attr("type", "text").attr("class", "alarmer form-control").attr("style", "width: 100%");
        $(userDom).attr("class", "alarm-user").append(selectUserDom);
        userDom.appendChild(selectUserDom);
        trDom.appendChild(userDom);
        //删除按钮
        var removeDom = document.createElement("td");
        const removeBtn = document.createElement("button");
        $(removeBtn).attr("type", "button").attr("class",'btn btn-sm remove-alarm-btn btn-danger').attr("index", index).text("Delete");
        removeDom.appendChild(removeBtn);
        trDom.appendChild(removeDom);

        projectAlarmbody.appendChild(trDom);

        this.loadProjectNametData(`alarm-project${index}`);
        this.loadUserNametData(`alarmer-select${index}`, true);
        this.deleteAlarm(removeDom);
    },
    // 下拉回显
    setSelectOption (id, val) {
        const options = val.split(",");
        options.forEach(function (v) {
            $(id).append(new Option(v, v, false, true));
        });
        $(id).trigger("change");
    },
    openAlarmModal() {
        const that = this;
        $.get("/manager?ajax=getProjectHourlyReportConfig", function(data) {
            $("#project-alarm-body").empty();
            if (data.projectHourlyReportConfigList.length) {
                data.projectHourlyReportConfigList.forEach((item, index) => {
                    projectHeaderView.insertAlarmRow(index, true);
                    that.setSelectOption(`#alarm-project${index}`, item.projectName);
                    const reportWay = item.reportWay.split(",");
                    reportWay.forEach(function (v) {
                        const type = v === "1" ? "IMS" : "Email";
                        $(`#alarm${type}${index}`).prop("checked", true);
                    });
                    $(`alarmType${index}`).val(item.reportWay);
                    that.setSelectOption(`#alarmer-select${index}`, item.reportReceiver);
                });
            } else {
                projectHeaderView.insertAlarmRow(0, false);
            }
            $("#add-project-alarm-modal").modal();
        });
    },
    removeAlarmTr(index, projectName) {
        $("#project-alarm-body .bodyTr" + index).remove();
        messageBox.show(`${projectName || ''}删除成功`);
    },
    deleteAlarm(removeDom) {
        const that = this;
        $(removeDom).click(function(e) {
            const index = e.target.getAttribute('index');
            const isDetail =  $("#project-alarm-body .bodyTr" + index).attr('isDetail') === "true";
            const projectName =$(`#alarm-project${index}`).val();
            if (isDetail) {
                $.post('/manager?action=removeProjectHourlyReport', { project: projectName }, function (data) {
                    if (data.error) {
                        messageBox.show(data.error, "danger");
                        return;
                    }
                    that.removeAlarmTr(index, projectName);
                });
            } else {
                that.removeAlarmTr(index, projectName);
            }
        })
    },
    getProjectAlarm(editRows) {
        const paramObj = {};
        for (let i = 0; i < editRows.length; i++) {
            const tr = editRows[i];
            const projectname = $(tr).find(".project-name").val();
            const typeList = $(tr).find(`[type="checkbox"]:checked`);
            const alarmType = [];
            for (let j = 0; j < typeList.length; j++) {
                alarmType.push(typeList[j].value);
            }
            const reportWay = alarmType.toString();
            const reportReceiver = $(tr).find(".alarmer").val() || [];
            if (paramObj[projectname]) {
                messageBox.show("项目名不能重复！", "warning");
                return false;
            }
            if(!projectname) {
                messageBox.show("项目名称不能为空！", "warning");
                return false;
            }
            if(!reportWay) {
                messageBox.show("告警类型不能为空！", "warning");
                return false;
            }
            if(!reportReceiver && !reportReceiver.length) {
                messageBox.show("告警人不能为空！", "warning");
                return false;
            }
            paramObj[projectname] = {
                reportWay,
                reportReceiver: reportReceiver.toString(),
            };
        }
        return paramObj;
    },
    submitProjectAlarm() {
        try {
            const editRows = $("#project-alarm-body tr");
            if (!editRows.length) {
                messageBox.show("请选择项目告警信息", "warning");
                return;
            }
            const params = this.getProjectAlarm(editRows);
            if (!params) { return;}
            $.post('/manager?action=configProjectHourlyReport', { reportMap: JSON.stringify(params) }, function (data) {
                if (data.error) {
                    messageBox.show(data.error, "danger");
                    return;
                }
                messageBox.show("设置成功！");
                $("#add-project-alarm-modal").modal("hide");
            });
        } catch (error) {
            console.log(error);
        }

    },
});


var createProjectView;
azkaban.CreateProjectView = Backbone.View.extend({
    events: {
        "click #create-btn": "handleCreateProject"
    },

    initialize: function(settings) {
        $("#modal-error-msg").hide();
    },

    handleCreateProject: function(evt) {
        // First make sure we can upload
        var projectName = $('#path').val();
        var description = $('#description').val();
        var group = ""; //$('#project-group-select').val();
        console.log("Creating");
        $.ajax({
            async: "false",
            url: "manager",
            dataType: "json",
            type: "POST",
            data: {
                action: "create",
                name: projectName,
                description: description,
                projectGroup: group
            },
            success: function(data) {
                if (data.status == "success") {
                    if (data.action == "redirect") {
                        window.location = data.path;
                    }
                } else {
                    if (data.action == "login") {
                        window.location = "";
                    } else {
                        $("#modal-error-msg").text("ERROR: " + data.message);
                        $("#modal-error-msg").attr("style", "word-break:break-all;");
                        $("#modal-error-msg").slideDown("fast");
                    }
                }
            }
        });
    },

    render: function() {}
});

var tableSorterView;


$(function() {
    projectHeaderView = new azkaban.ProjectHeaderView({
        el: $('#create-project'),
    });

    uploadBusView = new azkaban.UploadBusinessView({
        el: $('#upload-business-modal')
    });


    /*tableSorterView = new azkaban.TableSorter({
     el: $('#all-jobs'),
     initialSort: $('.tb-name')
     });*/

    uploadView = new azkaban.CreateProjectView({
        el: $('#create-project-modal')
    });

    // 在切换选项卡之前创建模型
    projectModel = new azkaban.ProjectModel();

    // var urlSearch = window.location.search;
    // if (urlSearch.indexOf("doaction") != -1) {
    //   projectModel.set({ "doaction": true });
    // }

    var deleteProjectName;

    projectListView = new azkaban.ProjectListView({
        el: $('#project-view'),
        model: projectModel,
        deleteProjectName: deleteProjectName
    });
    advFilterView = new azkaban.AdvFilterView({
        el: $('#adv-filter')
    })

    $('#project-handover').click(function() {
        var handoverArr = $("#project-handover-body .handover")
        for (var i = 0; i < handoverArr.length; i++) {
            if (!handoverArr[i].value.trim()) {
                alert(wtssI18n.view.handoverReq)
                return;
            }
        }
        if (handoverArr.length > projectChangeLimit) {
            var prompt = window.langType === 'zh_CN' ? '交接项目不能超过' + projectChangeLimit + '个' : 'Handover items cannot exceed ' + projectChangeLimit;
            alert(prompt)
            return;
        }
        var projectHandoverDesc = $("#projectHandoverDesc").val();
        var requestURL = "/manager";
        var changeMap = {}
        var checkedProjectList = JSON.parse(sessionStorage.getItem('checkedProjectList'))
        for (var j = 0; j < checkedProjectList.length; j++) {
            var keyName = checkedProjectList[j]
            changeMap[keyName] = handoverArr[j].value.trim()
        }
        requestData = {
            "ajax": "ajaxRequestToItsm4ExchangeProjectOwner",
            "changeMap": JSON.stringify(changeMap),
            "requestDesc": projectHandoverDesc ? projectHandoverDesc : "申请 WTSS 项目交接"
        };

        function successHandler(data) {
            if (data.error) {
                alert(data.error)
                return;
            }
            messageBox.show((data.requestInfo || '')+'\n当项目交接完成后，注意工作流中使用的队列可能存在权限问题\n若从DSS中重新发布该项目的工作流，需要检视代理用户是否准确', 'success', 5000)
            sessionStorage.removeItem('checkedProjectList')
            projectModel.set({ projectsType: 'personal', order: 'orderProjectName' });
            projectListView.model.trigger("change:view");
            $("#create-handover-modal").modal('hide');
            projectListView.initSearchForm();
        }
        $.get(requestURL, requestData, successHandler, "json");
    })

    $("#orderSelect").change(function(evt) {
        var orderOption = evt.currentTarget.value;
        projectModel.set({ "order": orderOption, });
        projectListView.model.trigger("change:view");
    })
    $("#quickSearchBtn").click(function() {
        var searchText = $("#search-textbox").val()
        projectModel.set({ searchterm: searchText, isFilter: false })
        projectListView.model.trigger("change:view");
    })
    $("#search-textbox").keyup(function(evt) {
        if (evt.keyCode == "13") {
            const searchText = $("#search-textbox").val()
            projectModel.set({ searchterm: searchText, isFilter: false })
            projectListView.model.trigger("change:view");
        }
    });

    $("#add-project-alarm").on("click", function () {
        const childr = document.getElementById("project-alarm-body").children;
        // 为空判断，空了，确保inde + 1 = 0
        const index = childr.length > 0 ? parseFloat(childr[childr.length - 1].getAttribute("index")) : -1;
        projectHeaderView.insertAlarmRow(index + 1, false);
    });
    $("#submit-project-alarm").on("click", function () {
        projectHeaderView.submitProjectAlarm();
    });
    //上传应用信息文件绑定事件
    document.getElementById('businessfile').addEventListener('change', function() {
        document.getElementById('businessfilefieldsNameBox').innerHTML = filterXSS(this.files[0].name)
    }, false)
});

//用于保存浏览数据，切换页面也能返回之前的浏览进度。
var projectModel;
azkaban.ProjectModel = Backbone.Model.extend({});

var projectsType = "personal";

var lastTimeUser = "最后一次修改是由用户 ";
var projectSource = "项目来源 ";
var workflowlist = "工作流列表";
var deleteItem = "删除项目";
var unexecuted = "未执行";
var lastExecutionLog = "最后一次执行日志";
var noRecord = "没有执行记录";
var workflowNotExist = "项目工作流不存在";
var warning = "警告";
var projectDeletePro = "项目将被放入最近删除，要想完全删除请在 '项目->最近删除' 栏彻底底删除。";
var scheduleProjectPro = "该项目存在定时调度，删除该项目会一并删除其对应的定时调度，无法恢复。";
var eventScheduleProjectPro = "该项目存在信号调度，删除该项目会一并删除其对应的信号调度，无法恢复。";
var on = "在 ";
var recentlyDeleted = "最近删除";
var deletePermanently = "永久删除";
var restoreItem = "还原项目";
var downloadProject = "下载项目";

//项目列表页面
var projectListView;
azkaban.ProjectListView = Backbone.View.extend({
    events: {
        "click #pageTable .projectPageSelection  li": "handleChangePageSelection",
        "change #pageTable .pageSizeSelect": "handlePageSizeSelection",
        "click #pageTable .pageNumJump": "handlePageNumJump",
        "click #project-personal": "handleProjectPersonalNav",
        "click #project-group": "handleProjectGroupNav",
        "click #project-all": "handleProjectAllNav",
        "click .project-expander": "expandProject",
        "click #delete-btn": "handleDeleteProject",
        // "change #orderSelect": "handleOrderSelection",
        "click #project-delete": "showDeleteProjects"
    },

    initialize: function(settings) {
        $("#projectSource").select2({ placeholder: " " + projectSource });
        // 加载语言环境
        this.loadProjectPageLanguageType();
        changePageSizeSelectValue("pageTable", [10, 20, 50, 100])
        this.model.bind('render', this.render, this);
        this.model.set('elDomId','pageTable');
        this.model.bind('change:view', this.handleChangeView, this);
        this.model.bind('change:page', this.handlePageChange, this);
        this.model.set({ page: 1, pageSize: 10, projectsType: 'personal', order: 'orderProjectName' });
        this.createResize();

        loadSubSystemData();
        loadBusPathData();
        var that = this
        setTimeout(function() {
            //获取批量路径
            // that.loadBusPathData();
            // //获取子系统
            // that.loadSubSystemData();
            that.loadDepartmentData();
        }, 100)
        if(window.Event && !this.pageResize) {
            this.pageResize = new Event('resize');
        }
    },
    loadBusPathData: loadBusPathData,
    loadSubSystemData: loadSubSystemData,
    loadDepartmentData: loadDepartmentData,
    loadProjectPageLanguageType: function() {
        var requestURL = "/index";
        var requestData = {
            "ajax": "getProjectPageLanguageType",
        };

        var successHandler = function(data) {
            if (data.error) {
                console.log(data.error.message);
            } else {
                var langType = data.langType;

                if (langType === "en_US") {
                    lastTimeUser = "Last modified by ";
                    projectSource = "Project source ";
                    workflowlist = "Flow list";
                    deleteItem = "Delete project";
                    unexecuted = "Not Executed";
                    lastExecutionLog = "Last execution log";
                    noRecord = "No execution record";
                    workflowNotExist = "Project flow does not exist";
                    warning = "Warning";
                    projectDeletePro = "The item will be put into the most recently deleted item. To delete it completely, please delete it at the bottom of the item > recently deleted column.";
                    scheduleProjectPro = "This project has a scheduled schedule. Deleting this project will delete its corresponding scheduled schedule and cannot be restored.";
                    eventScheduleProjectPro = "This project has a scheduled event schedule. Deleting this project will delete its corresponding scheduled event schedule and cannot be restored.";
                    on = "On ";
                    recentlyDeleted = "Recently deleted";
                    deletePermanently = "Delete permanently";
                    restoreItem = "Restore item";
                    downloadProject = "Download project";
                }
            }
        };

        $.ajax({
            url: requestURL,
            type: "get",
            async: false,
            data: requestData,
            dataType: "json",
            error: function(data) {
                console.log(data);
            },
            success: successHandler
        });
    },

    render: function(evt) {
        console.log("render");
        // Render page selections
        var oldProjectUl = document.getElementById('project-list');
        var childrenNum = oldProjectUl.children.length;
        // projectUl.empty();
        var newProjectUl = document.createElement("ul");
        newProjectUl.setAttribute('id', 'project-list');
        var projects = this.model.get("projectList");
        console.log(this.model.get("projectsType"));
        var checkedProjectList = sessionStorage.getItem('checkedProjectList') ? JSON.parse(sessionStorage.getItem('checkedProjectList')) : []
        for (var i = 0; i < projects.length; ++i) {
            var row = document.createElement("li");

            //组装项目信息D
            var divProjectInfo = document.createElement("div");
            $(divProjectInfo).attr("class", "project-info");
            row.appendChild(divProjectInfo);

            //组装数字行
            var h4 = document.createElement("h4");
            $(h4).attr("style", "word-break:break-all;");
            var projectId =  projects[i].id ? ('&projectId=' + projects[i].id) : '';
            var url = filterXSS("/manager?project=" + projects[i].name + projectId);
            var aProject = '<a href="' + url + '">' + projects[i].name + '</a>'
                // $(aProject).attr("href", filterXSS("/manager?project=" + projects[i].name));
                // $(aProject).text(projects[i].name);
            var checked = checkedProjectList.indexOf(projects[i].name) > -1 ? 'checked' : ''
            h4.innerHTML = '<input type="checkbox" class="project-checkbox" name="' + projects[i].name + '" value="' + projects[i].name + '"' + checked + '>' + aProject;

            divProjectInfo.appendChild(h4);


            var pDesc = document.createElement("p");
            $(pDesc).attr("class", "project-description");
            $(pDesc).text(projects[i].description);
            divProjectInfo.appendChild(pDesc);

            var pProjectSource = document.createElement("p");
            $(pProjectSource).attr("class", "project-last-modified");
            $(pProjectSource).text(projectSource);

            var StrongP = document.createElement("strong");
            $(StrongP).text(projects[i].fromType);
            $(pProjectSource).text($(pProjectSource).text());
            pProjectSource.appendChild(StrongP);

            var pModified = document.createElement("p");
            $(pModified).attr("class", "project-last-modified");
            $(pModified).text(lastTimeUser);

            var strongP = document.createElement("strong");
            $(strongP).text(projects[i].lastModifiedUser + ".");
            $(pModified).text($(pModified).text());
            pModified.appendChild(strongP);

            divProjectInfo.appendChild(pProjectSource);
            divProjectInfo.appendChild(pModified);

            var strong = document.createElement("strong");
            var modifyIn = on + projects[i].lastModifiedTimestamp;
            $(strong).text(modifyIn);
            divProjectInfo.appendChild(strong);


            //组装项目展开按钮
            var divProjectExpander = document.createElement("div");
            $(divProjectExpander).attr("class", "project-expander");
            $(divProjectExpander).attr("id", projects[i].name);
            row.appendChild(divProjectExpander);

            var spanExpander = document.createElement("span");
            $(spanExpander).attr("class", "glyphicon glyphicon-chevron-down project-expander-icon");
            divProjectExpander.appendChild(spanExpander);



            //组装项目展开按钮
            var divProjectClearfix = document.createElement("div");
            $(divProjectClearfix).attr("class", "clearfix");
            row.appendChild(divProjectClearfix);

            //组装项目工作流展示
            var divProjectFlows = document.createElement("div");
            $(divProjectFlows).attr("class", "project-flows");
            $(divProjectFlows).attr("id", projects[i].name + "-child");
            row.appendChild(divProjectFlows);

            //组装项目工作流展示
            var h5 = document.createElement("h5");
            $(h5).text(workflowlist);
            divProjectFlows.appendChild(h5);

            //组装项目工作流展示
            var divFlows = document.createElement("div");
            $(divFlows).attr("class", "list-group");
            $(divFlows).attr("id", projects[i].name + "-tbody");
            divProjectFlows.appendChild(divFlows);

            //组装项目删除按钮
            if (projects[i].showDeleteBtn) {
                var deleteProject = document.createElement("div");
                $(deleteProject).attr("class", "project-delete");
                divFlows.appendChild(deleteProject);

                var deleteBtn = document.createElement("button");
                $(deleteBtn).attr("class", "btn btn-sm btn-danger");
                $(deleteBtn).bind("click", this.model, this.handleShowDeleteProjectView);
                $(deleteBtn).attr("id", projects[i].name);
                deleteProject.appendChild(deleteBtn);

                var deleteBtnSpan = document.createElement("span");
                $(deleteBtnSpan).attr("class", "glyphicon glyphicon-trash");
                $(deleteBtn).text(deleteItem);
                deleteBtn.appendChild(deleteBtnSpan);
                //组装项目删除按钮
            }

            var tableFlows = document.createElement("table");
            $(tableFlows).attr("id", projects[i].name + "-detail-table");

            if (this.model.get("projectsType") == "projectDelete") {
                $(aProject).attr("href", "javascript:void(0);");
                $(aProject).attr("disabled", "true");
                $(aProject).attr("style", "pointer-events:none;color: #333333;");
                $(divProjectExpander).attr("id", projects[i].id + "_" + projects[i].name);
                $(divProjectExpander).attr("name", projects[i].name);
                $(divProjectFlows).attr("id", projects[i].id + "_" + projects[i].name + "-child");
                $(divFlows).attr("id", projects[i].id + "_" + projects[i].name + "-tbody");
                this.assembleRestoreAndDeleteBtn(divFlows, projects[i]);
                $(tableFlows).attr("id", projects[i].id + "_" + projects[i].name + "-detail-table");
            }
            divFlows.appendChild(tableFlows);

            newProjectUl.appendChild(row);
        }
        oldProjectUl.parentNode.replaceChild(newProjectUl, oldProjectUl);
        this.checkboxAddEvent()
        this.renderPagination(evt);
    },
    // 复选框绑定事件
    checkboxAddEvent() {
        $('.project-checkbox').click(function(e) {
            var projectName = e.target.value
            var checkedProjectList = sessionStorage.getItem('checkedProjectList') ? JSON.parse(sessionStorage.getItem('checkedProjectList')) : []
            if (e.target.checked) {
                checkedProjectList.push(projectName)
            } else {

                checkedProjectList.splice(checkedProjectList.indexOf(projectName), 1)
            }
            sessionStorage.setItem('checkedProjectList', JSON.stringify(checkedProjectList))
        })
    },
    assembleRestoreAndDeleteBtn: function(divFlows, project) {
        var deleteProject = document.createElement("div");
        $(deleteProject).attr("class", "project-restore");
        divFlows.appendChild(deleteProject);

        var deleteBtn = document.createElement("button");
        $(deleteBtn).attr("class", "btn btn-sm btn-danger");
        $(deleteBtn).bind("click", this.model, this.handlePermanentlyDeleteProject);
        $(deleteBtn).attr("name", project.name);
        $(deleteBtn).attr("projectId", project.id);
        deleteProject.appendChild(deleteBtn);

        var deleteBtnSpan = document.createElement("span");
        $(deleteBtnSpan).attr("class", "glyphicon glyphicon-trash");
        $(deleteBtn).text(deletePermanently);
        deleteBtn.appendChild(deleteBtnSpan);

        var restoreProjectDiv = document.createElement("div");
        $(restoreProjectDiv).attr("class", "project-restore");
        divFlows.appendChild(restoreProjectDiv);

        var restoreBtn = document.createElement("button");
        $(restoreBtn).attr("class", "btn btn-sm btn-success");
        $(restoreBtn).bind("click", this.model, this.handleRestoreProject);
        $(restoreBtn).attr("name", project.name);
        $(restoreBtn).attr("projectId", project.id);
        $(restoreBtn).text(restoreItem);
        restoreProjectDiv.appendChild(restoreBtn);

        //<a class="btn btn-sm btn-info" href="javascript:void(0);"
        // onclick="checkHrefUrlXss('/manager?project=alter_test_for120&amp;download=true')">
        //  <span class="glyphicon glyphicon-download"></span> 下载项目
        //</a>

        var downloadDiv = document.createElement("div");
        $(downloadDiv).attr("class", "project-restore");
        divFlows.appendChild(downloadDiv);

        var downloadBtn = document.createElement("a");
        $(downloadBtn).attr("class", "btn btn-sm btn-info");
        $(downloadBtn).attr("href", "/manager?project=" + project.name + "&download=true&projectId=" + project.id);
        $(downloadBtn).text(downloadProject);
        downloadDiv.appendChild(downloadBtn);
    },

    handleRestoreProject: function(evt) {
        console.log($(evt.currentTarget).attr("name"));
        var requestURL = "/manager";
        var requestData = {
            "ajax": "restoreProject",
            "project": $(evt.currentTarget).attr("name"),
            "projectId": $(evt.currentTarget).attr("projectId")
        };

        var successHandler = function(data) {
            if (data.error) {
                alert(data.error);
            } else {
                console.log("restore success.");
                window.location.reload();
            }
        };

        $.get(requestURL, requestData, successHandler, "json");
    },

    handlePermanentlyDeleteProject: function(evt) {
        console.log($(evt.currentTarget).attr("name"));
        var requestURL = "/manager";
        var requestData = {
            "ajax": "deleteProject",
            "project": $(evt.currentTarget).attr("name"),
            "projectId": $(evt.currentTarget).attr("projectId"),
        };

        var successHandler = function(data) {
            if (data.error) {
                alert(data.error);
            } else {
                console.log("deleteProject success.");
                projectListView.handlePageChange();
            }
        };

        $.get(requestURL, requestData, successHandler, "json");
    },

     ...commonPaginationFun(),

    handlePageChange: function(evt) {
        var start = this.model.get("page") - 1;
        var pageSize = this.model.get("pageSize");
        var requestURL = "/index";
        var searchText = this.model.get("searchterm");
        // var group = this.model.get("group");
        var all = this.model.get("all");
        var projectsType = projectModel.get("projectsType")
        var model = this.model;
        var isFilter = model.get('isFilter')
        var baseParam = {
            "ajax": isFilter ? "fetchProjectPreciseSearchPage" : "fetchProjectPage",
            "start": start,
            "length": pageSize,
            "projectsType": searchText && projectsType !== 'projectDelete' ? 'doaction' : projectsType,
            "pageNum": this.model.get("page"),
            "order": this.model.get("order"),
        }
        var requestData = {};
        if (isFilter) {
            requestData = model.get('filterParam')
        } else {
            if (searchText) {
                if (projectsType == "projectDelete") {
                    requestData = {
                        "searchterm": searchText
                    };
                } else {
                    requestData = {
                        "projectsType": "doaction",
                        "searchterm": searchText,
                        "doaction": true,
                        "group": false,
                        "all": all ? all : false
                    };
                }
            }
        }
        Object.assign(requestData, baseParam)
        var successHandler = function(data) {
            if (data.total > 0) {
                $("#pageTable").show();
            } else {
                $("#pageTable").hide();
            }
            model.set({
                "projectList": data.projectList ? data.projectList : [],
                "total": data.total
            });
            model.trigger("render");
        };
        $.get(requestURL, requestData, successHandler, "json");
    },

    handleProjectPersonalNav: function(e) {
        projectModel.set({ "isFilter": false ,"all": false, });
        $('#pageNumInput').val('');
        this.hangleChangeType(e, 'personal');
    },

    handleProjectGroupNav: function(e) {
        projectModel.set({ "isFilter": false })
        this.hangleChangeType(e, 'group');
    },

    handleProjectAllNav: function(e) {
        projectModel.set({ "all": true, isFilter: false});
        $('#pageNumInput').val('');
        this.hangleChangeType(e, 'all');
    },
    showDeleteProjects: function(e) {
        projectModel.set({ isFilter: false});
        $('#pageNumInput').val('');
        this.hangleChangeType(e, 'projectDelete');
    },
    hangleChangeType(evt, type) {
        $("#project-nav .active").removeClass('active')
        $(evt.target.parentElement).addClass('active')
        projectModel.set({ "projectsType": type, searchterm: '', order: 'orderProjectName' });
        this.model.trigger("change:view");
        this.initSearchForm();
    },
    initSearchForm() {
        $("#orderSelect").val("orderProjectName");
        $("#search-textbox").val('')
        this.initFilterForm()
    },
    initFilterForm() {
        $("#projcontain").val('');
        $("#usercontain").val("");
        $("#subSystemQuery").empty();
        $("#busPathQuery").empty();
        $("#departmentSelect").val("").trigger("change");
        $('#descriptionCon').val('');
        $("#jobNameSearch").val('');
        $("#projectSource").val("").trigger("change");
    },
    //进入Project页面开始调用这个方法获取后台Project信息
    expandProject: function(evt) {
        if (evt.target.tagName == "A") {
            return;
        }

        var target = evt.currentTarget;
        var targetId = target.id;
        var requestURL = "/manager";

        var targetExpanded = $('#' + targetId + '-child');
        var targetTBody = $('#' + targetId + '-tbody');
        var flowDetailTable = $('#' + targetId + '-detail-table');
        var createFlowListFunction = this.createFlowListTable;

        if (target.loading) {
            console.log("Still loading.");
        } else if (target.loaded) {
            if ($(targetExpanded).is(':visible')) {
                $(target).addClass('expanded').removeClass('collapsed');
                var expander = $(target).children('.project-expander-icon')[0];
                $(expander).removeClass('glyphicon-chevron-up');
                $(expander).addClass('glyphicon-chevron-down');
                $(targetExpanded).slideUp(300);
            } else {
                $(target).addClass('collapsed').removeClass('expanded');
                var expander = $(target).children('.project-expander-icon')[0];
                $(expander).removeClass('glyphicon-chevron-down');
                $(expander).addClass('glyphicon-chevron-up');
                $(targetExpanded).slideDown(300);
            }
        } else {
            // projectId is available
            $(target).addClass('wait').removeClass('collapsed').removeClass(
                'expanded');
            target.loading = true;
            console.log(projectModel.get("projectsType"));
            var request = {
                "project": targetId,
                "ajax": "fetchprojectflows"
            };
            if (this.model.get("projectsType") == "projectDelete") {
                console.log("project delete model.");
                target.loaded = true;
                target.loading = false;
                var tr = $("<tr></tr>");
                var tdNull = $("<td></td>");
                tdNull.appendTo(tr);
                var idaNull = $("<span></span>");
                $(idaNull).text(workflowNotExist);
                $(idaNull).addClass('list-group-item');
                idaNull.appendTo(tdNull);
                $(flowDetailTable).append(tr);
                $(target).addClass('collapsed').removeClass('wait');
                var expander = $(target).children('.project-expander-icon')[0];
                $(expander).removeClass('glyphicon-chevron-down');
                $(expander).addClass('glyphicon-chevron-up');
                $(targetExpanded).slideDown(300);
                return;
            }
            var successHandler = function(data) {
                console.log("Success");
                target.loaded = true;
                target.loading = false;

                //createFlowListFunction(data, targetTBody);
                createFlowListFunction(data, flowDetailTable);

                $(target).addClass('collapsed').removeClass('wait');
                var expander = $(target).children('.project-expander-icon')[0];
                $(expander).removeClass('glyphicon-chevron-down');
                $(expander).addClass('glyphicon-chevron-up');
                $(targetExpanded).slideDown(300);
            };

            $.get(requestURL, request, successHandler, "json");
        }
    },

    //Project页面点击项目右侧按钮展示Flow时调用的
    createFlowListTable: function(data, innerTable) {
        var flows = data.flows;
        flows.sort(function(a, b) {
            return a.flowId.localeCompare(b.flowId);
        });
        var requestURL = "/manager?project=" + data.project + "&flow=";
        if (flows.length > 0) {
            //Flow 循环展示
            for (var i = 0; i < flows.length; ++i) {
                //一个Flow展示一行
                var tr = $("<tr></tr>");
                //tr.appendTo(innerTable);

                //第一格展示Flow名称 并能跳转到Flow详细页面
                var td1 = $("<td></td>");
                td1.appendTo(tr);

                var id = flows[i].flowId;
                var ida = $("<a></a>");
                ida.project = data.project;
                $(ida).text(id);
                $(ida).attr("href", filterXSS(requestURL + id));
                $(ida).addClass('list-group-item');
                $(ida).attr("style", "word-break:break-all;");
                ida.appendTo(td1);

                //Flow最后一次执行状态展示
                var td2 = $("<td></td>");
                td2.appendTo(tr);
                var ida2 = $("<span></span>");
                var flowStatus = flows[i].flowStatus;
                if (20 == flowStatus) {
                    $(ida2).text("Preparing");
                    //PREPARING
                    $(ida2).attr("style", "background:#009fc9;width:100px;text-align:center");
                } else if (30 == flowStatus) {
                    $(ida2).text("Running");
                    //RUNNING
                    $(ida2).attr("style", "background:#3398cc;width:100px;text-align:center");
                } else if (50 == flowStatus) {
                    $(ida2).text("Success");
                    //SUCCESS
                    $(ida2).attr("style", "background:#5cb85c;width:100px;text-align:center");
                } else if (60 == flowStatus) {
                    $(ida2).text("Failed");
                    //FAILE
                    $(ida2).attr("style", "background:#d9534f;width:100px;text-align:center");
                } else if (70 == flowStatus) {
                    $(ida2).text("Kill");
                    //KILL
                    $(ida2).attr("style", "background:#d9534f;width:100px;text-align:center");
                } else if ("NoHistory" == flowStatus) {
                    $(ida2).text(unexecuted);
                    $(ida2).attr("style", "width:150px;text-align:center");
                }
                $(ida2).addClass('list-group-item');
                ida2.appendTo(td2);

                //Flow最后一次执行日志
                var td3 = $("<td></td>");
                td3.appendTo(tr);
                var flowExecId = flows[i].flowExecId;
                if ("NoHistory" != flowExecId) {
                    var ida3 = $("<a></a>");
                    var flowLogURL = "/executor?execid=";
                    $(ida3).text(lastExecutionLog);
                    $(ida3).attr("href", filterXSS(flowLogURL + flowExecId + "#log"));
                    $(ida3).addClass('list-group-item');
                    ida3.appendTo(td3);
                } else {
                    var ida3 = $("<span></span>");
                    $(ida3).text(noRecord);
                    $(ida3).addClass('list-group-item');
                    ida3.appendTo(td3);
                }


                $(innerTable).append(tr);
            }
        } else {
            //一个Flow展示一行
            var tr = $("<tr></tr>");
            //tr.appendTo(innerTable);

            var tdNull = $("<td></td>");
            tdNull.appendTo(tr);
            var idaNull = $("<span></span>");
            $(idaNull).text(workflowNotExist);
            $(idaNull).addClass('list-group-item');
            idaNull.appendTo(tdNull);

            $(innerTable).append(tr);
        }
    },
    // 项目-下拉展开-删除
    handleShowDeleteProjectView: function(evt) {

        var self = this;
        var projectName = $(self).attr("id")
        console.log(projectName);

        // 需要校验是否具有执行工作流权限 1:允许, 2:不允许
        var requestURL = "/manager?ajax=checkDeleteProjectFlagPermission&project=" + projectName;
        $.ajax({
            url: requestURL,
            type: "get",
            async: false,
            dataType: "json",
            success: function(data) {
                if (data["deleteProjectFlag"] == 1) {
                    console.log("click delete project");
                    evt.data.deleteProjectName = $(self).attr("id");
                    $("#delete-project-modal .modal-body p").empty();
                    $("#delete-project-modal .modal-body p").html("<strong>" + warning + " :</strong> " + projectDeletePro);
                    //判断是否有设置了定时调度
                    //http://127.0.0.1:8290/manager?ajax=ajaxFetchProjectSchedules&project=child-flow-test2
                    var requestURL = "/manager?ajax=ajaxFetchProjectSchedules&project=" + $(self).attr("id");
                    $.ajax({
                        url: requestURL,
                        async: false,
                        type: "GET",
                        dataType: "json",
                        success: function(data) {
                            if (data["hasSchedule"] == "Time Schedule") {
                                $("#delete-project-modal .modal-body p").empty();
                                $("#delete-project-modal .modal-body p").html("<strong>" + warning + " :</strong> " + scheduleProjectPro);
                            } else if (data["hasSchedule"] == "Event Schedule") {
                                $("#delete-project-modal .modal-body p").empty();
                                $("#delete-project-modal .modal-body p").html("<strong>" + warning + " :</strong> " + eventScheduleProjectPro);
                            }
                        }
                    });
                    $('#delete-project-modal').modal();

                } else {
                    $('#delete-expand-project-permit-change-panel').modal();
                }
                $('#open-execute-joblist-btn').hide()
                $('#execute-joblist-panel').hide()
                $("#switching-execute-flow-btn").hide()
                $("#workflow-execute-zoom-in").hide()
            }
        });


    },

    handleDeleteProject: function(evt) {
        console.log("delete project " + this.model.deleteProjectName);
        // window.location.href = filterXSS("/manager?project=" + this.model.deleteProjectName + "&delete=true");
        var requestData = {
            "project": this.model.deleteProjectName,
            "delete": true
        };
        var successHandler = function(data) {
            if(data.status === "error") {
                $('#delete-project-modal').modal('hide');
                messageBox.show(data.message, "danger");
                return;
            }
            messageBox.show('删除成功！');
            $('#delete-project-modal').modal('hide');
            projectListView.handlePageChange();
        };
        $.get('/manager', requestData, successHandler, "json");
    },
});

var advFilterView;
azkaban.AdvFilterView = Backbone.View.extend({
    events: {
        "click #filter-btn": "handleAdvFilter", //模糊查询
    },

    initialize: function(settings) {
        this.advQueryViewFirstShow = true

    },
    render: function() {
        projectListView.initFilterForm()
        this.renderBusPathSelect(projectModel.get('busPathMap'));
        this.renderSubSystemSelect(projectModel.get('subsystemMap'));
        this.renderDepartmentSelectelect(projectModel.get('departPathMap'));
        this.advQueryViewFirstShow = false
    },
    handleAdvFilter: function() {
        console.log("handleAdv");
        var projcontain = $('#projcontain').val();
        var usercontain = $('#usercontain').val();
        var subsystem = $('#subSystemQuery').val();
        var busPath = $('#busPathQuery').val();
        var departmentId = $('#departmentSelect').val();
        var description = $('#descriptionCon').val();
        var jobName = $('#jobNameSearch').val();
        var fromType = $('#projectSource').val().join(',');

        if (checkProject(projcontain)) {
            return;
        };

        if (checkEnglish(usercontain)) {
            return;
        };
        console.log("filtering history");
        $("#search-textbox").val("");
        projectListView.model.set('filterParam', {
            projcontain: projcontain,
            usercontain: usercontain,
            subsystem: subsystem,
            busPath: busPath,
            departmentId: departmentId,
            description: description,
            jobName: jobName,
            fromType: fromType
        })
        projectListView.model.set({'isFilter': true, "searchterm": ""});
            //请求接口
        projectListView.model.trigger("change:view");
        $('#adv-filter').modal('hide')

    },
    renderBusPathSelect: renderBusPathSelect,
    renderSubSystemSelect: renderSubSystemSelect,
    renderDepartmentSelectelect: renderDepartmentSelectelect

});

var uploadBusinessView;
azkaban.UploadBusinessView = Backbone.View.extend({
    events: {
        "click #upload-business-btn": "handleCreateBusiness"
    },

    initialize: function(settings) {
        console.log("Hide upload business modal error msg");
        $("#upload-business-modal-error-msg").hide();
    },

    handleCreateBusiness: function(evt) {
        console.log("Upload business button.");
        $("#upload-business-form").submit();
    },

    render: function() {}
});