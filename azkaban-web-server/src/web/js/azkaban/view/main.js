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
// var projectTableView;
// azkaban.ProjectTableView = Backbone.View.extend({
//   events: {
//     "click .project-expander": "expandProject"
//   },

//   initialize: function (settings) {
//   },
//   //进入Project页面开始调用这个方法获取后台Project信息
//   expandProject: function(evt) {
//     if (evt.target.tagName == "A") {
//       return;
//     }

//     var target = evt.currentTarget;
//     var targetId = target.id;
//     var requestURL = contextURL + "/manager";

//     var targetExpanded = $('#' + targetId + '-child');
//     var targetTBody = $('#' + targetId + '-tbody');
//     var flowDetailTable = $('#' + targetId + '-detail-table');
//     var createFlowListFunction = this.createFlowListTable;

//     if (target.loading) {
//       console.log("Still loading.");
//     }
//     else if (target.loaded) {
//       if ($(targetExpanded).is(':visible')) {
//         $(target).addClass('expanded').removeClass('collapsed');
//         var expander = $(target).children('.project-expander-icon')[0];
//         $(expander).removeClass('glyphicon-chevron-up');
//         $(expander).addClass('glyphicon-chevron-down');
//         $(targetExpanded).slideUp(300);
//       }
//       else {
//         $(target).addClass('collapsed').removeClass('expanded');
//         var expander = $(target).children('.project-expander-icon')[0];
//         $(expander).removeClass('glyphicon-chevron-down');
//         $(expander).addClass('glyphicon-chevron-up');
//         $(targetExpanded).slideDown(300);
//       }
//     }
//     else {
//       // projectId is available
//       $(target).addClass('wait').removeClass('collapsed').removeClass(
//           'expanded');
//       target.loading = true;

//       var request = {
//         "project": targetId,
//         "ajax": "fetchprojectflows"
//       };

//       var successHandler = function (data) {
//         console.log("Success");
//         target.loaded = true;
//         target.loading = false;

//         //createFlowListFunction(data, targetTBody);
//         createFlowListFunction(data, flowDetailTable);

//         $(target).addClass('collapsed').removeClass('wait');
//         var expander = $(target).children('.project-expander-icon')[0];
//         $(expander).removeClass('glyphicon-chevron-down');
//         $(expander).addClass('glyphicon-chevron-up');
//         $(targetExpanded).slideDown(300);
//       };

//       $.get(requestURL, request, successHandler, "json");
//     }
//   },

//   render: function () {
//   },
//   //Project页面点击项目右侧按钮展示Flow时调用的
//   createFlowListTable: function (data, innerTable) {
//     var flows = data.flows;
//     flows.sort(function (a, b) {
//       return a.flowId.localeCompare(b.flowId);
//     });
//     var requestURL = contextURL + "/manager?project=" + data.project + "&flow=";
//     if(flows.length > 0){
//       //Flow 循环展示
//       for (var i = 0; i < flows.length; ++i) {
//         //一个Flow展示一行
//         var tr=$("<tr></tr>");
//         //tr.appendTo(innerTable);

//         //第一格展示Flow名称 并能跳转到Flow详细页面
//         var td1=$("<td></td>");
//         td1.appendTo(tr);

//         var id = flows[i].flowId;
//         var ida = $("<a></a>");
//         ida.project = data.project;
//         $(ida).text(id);
//         $(ida).attr("href", requestURL + id);
//         $(ida).addClass('list-group-item');
//         $(ida).attr("style","word-break:break-all;");
//         ida.appendTo(td1);

//         //Flow最后一次执行状态展示
//         var td2=$("<td></td>");
//         td2.appendTo(tr);
//         var ida2 = $("<span></span>");
//         var flowStatus = flows[i].flowStatus;
//         if(20 == flowStatus){
//           $(ida2).text("Preparing");
//           //PREPARING
//           $(ida2).attr("style", "background:#009fc9;width:100px;text-align:center");
//         }else if(30 == flowStatus){
//           $(ida2).text("Running");
//           //RUNNING
//           $(ida2).attr("style", "background:#3398cc;width:100px;text-align:center");
//         }else if(50 == flowStatus){
//           $(ida2).text("Success");
//           //SUCCESS
//           $(ida2).attr("style", "background:#5cb85c;width:100px;text-align:center");
//         }else if(60 == flowStatus){
//           $(ida2).text("Faile");
//           //FAILE
//           $(ida2).attr("style", "background:#d9534f;width:100px;text-align:center");
//         }else if(70 == flowStatus){
//           $(ida2).text("Kill");
//           //KILL
//           $(ida2).attr("style", "background:#d9534f;width:100px;text-align:center");
//         }else if("NoHistory" == flowStatus){
//           $(ida2).text("未执行");
//           $(ida2).attr("style", "width:100px;text-align:center");
//         }
//         $(ida2).addClass('list-group-item');
//         ida2.appendTo(td2);

//         //Flow最后一次执行日志
//         var td3=$("<td></td>");
//         td3.appendTo(tr);
//         var flowExecId = flows[i].flowExecId;
//         if("NoHistory" != flowExecId){
//           var ida3 = $("<a></a>");
//           var flowLogURL = contextURL + "/executor?execid=";
//           $(ida3).text("最后一次执行日志");
//           $(ida3).attr("href", flowLogURL + flowExecId + "#log");
//           $(ida3).addClass('list-group-item');
//           ida3.appendTo(td3);
//         }else{
//           var ida3 = $("<span></span>");
//           $(ida3).text("没有执行记录");
//           $(ida3).addClass('list-group-item');
//           ida3.appendTo(td3);
//         }


//         $(innerTable).append(tr);
//       }
//     }else{
//       //一个Flow展示一行
//       var tr=$("<tr></tr>");
//       //tr.appendTo(innerTable);

//       var tdNull=$("<td></td>");
//       tdNull.appendTo(tr);
//       var idaNull = $("<span></span>");
//       $(idaNull).text("项目工作流不存在");
//       $(idaNull).addClass('list-group-item');
//       idaNull.appendTo(tdNull);

//       $(innerTable).append(tr);
//     }
//   }
// });

var projectHeaderView;
azkaban.ProjectHeaderView = Backbone.View.extend({
  events: {
    "click #create-project-btn": "handleCreateProjectJob"
  },

  initialize: function (settings) {
    console.log("project header view initialize.");
    if (settings.errorMsg && settings.errorMsg != "null") {
      $('#messaging').addClass("alert-danger");
      $('#messaging').removeClass("alert-success");
      $('#messaging-message').html(settings.errorMsg);
    }
    else if (settings.successMsg && settings.successMsg != "null") {
      $('#messaging').addClass("alert-success");
      $('#messaging').removeClass("alert-danger");
      $('#messaging-message').html(settings.successMsg);
    }
    else {
      $('#messaging').removeClass("alert-success");
      $('#messaging').removeClass("alert-danger");
    }
  },

  handleCreateProjectJob: function (evt) {

    // 需要校验是否具有上传权限 1:允许创建, 2:不允许创建
    var requestURL = contextURL + "/manager?ajax=checkUserCreateProjectPermission";
    $.ajax({
      url: requestURL,
      type: "get",
      async: false,
      dataType: "json",
      success: function (data) {
        if (data["createProjectFlag"] == 1) {
          console.log("have permission, click create project");
          $('#create-project-modal').modal();
        } else if (data["createProjectFlag"] == 2) {
          $('#user-create-project-permit-panel').modal();
        }
      }
    });
  },

  render: function () {
  }
});

var orderSelectView;
azkaban.OrderSelectView = Backbone.View.extend({
  events: {
    "change #orderSelect": "handleOrderSelection"
  },

  initialize: function (settings) {
    console.log("project header view initialize.");
    var order = $("#orderSelect").val();
    this.model.set({ order: order });
    this.model.bind('change:view', this.handleChangeView, this);
    this.model.bind('change:page', this.handlePageChange, this);
  },
  handleChangeView: function (evt) {
    if (this.init) {
      return;
    }
    console.log("init");
    this.handlePageChange(evt);
    this.init = true;
  },

  handlePageChange: function (evt) {
    var start = this.model.get("page") - 1;
    var pageSize = this.model.get("pageSize");
    var requestURL = contextURL + "/index";
    var searchText = this.model.get("searchterm");
    var group = this.model.get("group");
    var all = this.model.get("all");

    var model = this.model;
    var requestData;
    if (searchText) {
      requestData = {
        "ajax": "fetchProjectPage",
        "start": start,
        "length": pageSize,
        "pageNum": this.model.get("page"),
        "projectsType": "doaction",
        "searchterm": searchText,
        "doaction": true,
        "group": group,
        "all": all,
        "order": this.model.get("order"),
      };
    } else {
      requestData = {
        "ajax": "fetchProjectPage",
        "start": start,
        "length": pageSize,
        "projectsType": this.model.get("projectsType"),
        "pageNum": this.model.get("page"),
        "order": this.model.get("order"),
      };
    }
    var successHandler = function (data) {
      if (data.total > 0) {
        $("#pageTable").show();
        model.set({
          "projectList": data.projectList,
          "total": data.total
        });
        model.trigger("render");
      } else {
        $("#pageTable").hide();
      }
    };
    $.get(requestURL, requestData, successHandler, "json");
  },

  handleOrderSelection: function (evt) {
    var orderOption = evt.currentTarget.value;
    this.model.set({ "order": orderOption });
    // projectModel.set({"order": orderOption});
    this.model.set({ "page": 1 });
    this.init = false;
    //projectModel.trigger("change:view");
    //window.location.href=contextURL + "/index";

    var pageSize = $("#pageSizeSelect").val();

    var search = window.location.search
    var arr = search.split("#");
    var pageURL = arr[0] + "#page1";
    var pageSizeURL = pageURL + "#pageSzie" + pageSize;
    //var orderURL = "#order=" + orderOption;

    //var projectURL = contextURL + "/index"

    //var pageSizeFirestURL = projectURL + pageSizeURL + orderURL;
    //window.location = pageSizeFirestURL;
    this.handleChangeView(evt);
  },

  render: function () {
  }
});

var createProjectView;
azkaban.CreateProjectView = Backbone.View.extend({
  events: {
    "click #create-btn": "handleCreateProject"
  },

  initialize: function (settings) {
    $("#modal-error-msg").hide();
  },

  handleCreateProject: function (evt) {
    // First make sure we can upload
    var projectName = $('#path').val();
    var description = $('#description').val();
    var group = "";//$('#project-group-select').val();
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
      success: function (data) {
        if (data.status == "success") {
          if (data.action == "redirect") {
            window.location = data.path;
          }
        }
        else {
          if (data.action == "login") {
            window.location = "";
          }
          else {
            $("#modal-error-msg").text("ERROR: " + data.message);
            $("#modal-error-msg").attr("style", "word-break:break-all;");
            $("#modal-error-msg").slideDown("fast");
          }
        }
      }
    });
  },

  render: function () {
  }
});

var tableSorterView;
$(function () {
  projectHeaderView = new azkaban.ProjectHeaderView({
    el: $('#create-project'),
    successMsg: successMessage,
    errorMsg: errorMessage
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

  var urlSearch = window.location.search;
  if (urlSearch.indexOf("doaction") != -1) {
    projectModel.set({ "doaction": true });
  }

  var deleteProjectName;

  projectListView = new azkaban.ProjectListView({
    el: $('#project-view'),
    model: projectModel,
    deleteProjectName: deleteProjectName
  });

  orderSelectView = new azkaban.OrderSelectView({
    el: $('#order-select'),
    model: projectModel,
    projectListView: projectListView
  });

  // var hash = window.location.hash;
  // if ("#page" == hash.substring(0, "#page".length)) {
  //   var page = hash.substring("#page".length, hash.length);
  //   console.log("page " + page);

  //   projectModel.set({"page": parseInt(page)});
  // }else{
  //   projectModel.set({"page": 1});
  // }

  var hash = window.location.hash;
  if ("#page" == hash.substring(0, "#page".length)) {
    var arr = hash.split("#");
    var page = arr[1].substring("#page".length - 1, arr[1].length);
    var pageSize = arr[2].substring("#pageSize".length - 1, arr[2].length);
    //var projectOrder = arr[3].substring("#order=".length-1, arr[3].length);

    $("#lpageSizeSelect").val(pageSize);

    console.log("page " + page);
    projectModel.set({
      "page": parseInt(page),
      "pageSize": parseInt(pageSize),
      //"order": projectOrder,
    });
  } else {
    projectModel.set({ "page": 1 });
  }
  //
  var urlSearch = window.location.search;
  if (urlSearch.indexOf("group") != -1) {
    projectModel.set({ "projectsType": "group" });
  } else if (urlSearch.indexOf("all") != -1) {
    projectModel.set({ "projectsType": "all" });
  } else {
    projectModel.set({ "projectsType": "personal" });
  }

  projectModel.trigger("change:view");

  // projectTableView = new azkaban.ProjectTableView({
  //   el: $('#project-list')
  // });

});

//用于保存浏览数据，切换页面也能返回之前的浏览进度。
var projectModel;
azkaban.ProjectModel = Backbone.Model.extend({});

var projectsType = "personal";

var lastTimeUser = "最后一次修改是由用户 ";
var workflowlist = "工作流列表";
var deleteItem = "删除项目";
var unexecuted = "未执行";
var lastExecutionLog = "最后一次执行日志";
var noRecord = "没有执行记录";
var workflowNotExist = "项目工作流不存在";
var warning = "警告";
var projectDeletePro = "该项目将被删除，可能无法恢复。";
var scheduleProjectPro = "该项目存在定时调度，删除该项目会一并删除其对应的定时调度，无法恢复。";
var on = "在 ";

//项目列表页面
var projectListView;
azkaban.ProjectListView = Backbone.View.extend({
  events: {
    "click #projectPageSelection li": "handleProjectChangePageSelection",
    "click #project-personal": "handleProjectPersonalNav",
    "click #project-group": "handleProjectGroupNav",
    "click #project-all": "handleProjectAllNav",
    "click .project-expander": "expandProject",
    "change #pageSizeSelect": "handleProjectPageSizeSelection",
    "click #pageNumJump": "handleProjectPageNumJump",
    "click #delete-btn": "handleDeleteProject"
    // "change #orderSelect": "handleOrderSelection",
  },

  initialize: function (settings) {

    // 加载语言环境
    this.loadProjectPageLanguageType();

    this.model.bind('change:view', this.handleChangeView, this);
    this.model.bind('render', this.render, this);
    var pageSize = $("#pageSizeSelect").val();
    this.model.set({ page: 1, pageSize: pageSize });

    var searchText = $("#search-textbox").val();
    if (this.model.get("doaction")) {
      this.model.set({ searchterm: searchText });
    }
    var searchURL = window.location.search;
    if (searchURL.indexOf("all=true") != -1) {
      this.model.set({ all: true });
    } else {
      this.model.set({ all: false });
    }
    if (searchURL.indexOf("group=true") != -1) {
      this.model.set({ group: true });
    } else {
      this.model.set({ group: false });
    }
    // var order = $("#orderSelect").val();
    // this.model.set({order: order});

    // this.model.bind('change:page', this.handlePageChange, this);
  },

  loadProjectPageLanguageType: function () {
    var requestURL = contextURL + "/index";
    var requestData = {
      "ajax": "getProjectPageLanguageType",
    };

    var successHandler = function (data) {
      if (data.error) {
        console.log(data.error.message);
      } else {
        var langType = data.langType;

        if (langType === "en_US") {
          lastTimeUser = "Last modified by ";
          workflowlist = "Flow list";
          deleteItem = "Delete project";
          unexecuted = "Not Executed";
          lastExecutionLog = "Last execution log";
          noRecord = "No execution record";
          workflowNotExist = "Project flow does not exist";
          warning = "Warning";
          projectDeletePro = "This item will be deleted and may not be recoverable.";
          scheduleProjectPro = "This project has a scheduled schedule. Deleting this project will delete its corresponding scheduled schedule and cannot be restored.";
          on = "On ";
        }
      }
    };

    $.ajax({
      url: requestURL,
      type: "get",
      async: false,
      data: requestData,
      dataType: "json",
      error: function (data) {
        console.log(data);
      },
      success: successHandler
    });
  },

  render: function (evt) {
    console.log("render");
    // Render page selections
    var projectUl = $("#project-list");
    projectUl.empty();

    var projects = this.model.get("projectList");

    for (var i = 0; i < projects.length; ++i) {
      var row = document.createElement("li");

      //组装项目信息D
      var divProjectInfo = document.createElement("div");
      $(divProjectInfo).attr("class", "project-info");
      row.appendChild(divProjectInfo);

      //组装数字行
      var h4 = document.createElement("h4");
      $(h4).attr("style", "word-break:break-all;");

      var aProject = document.createElement("a");
      $(aProject).attr("href", contextURL + "/manager?project=" + projects[i].name);
      $(aProject).text(projects[i].name);
      h4.appendChild(aProject);

      divProjectInfo.appendChild(h4);


      var pDesc = document.createElement("p");
      $(pDesc).attr("class", "project-description");
      $(pDesc).text(projects[i].description);
      divProjectInfo.appendChild(pDesc);

      var pModified = document.createElement("p");
      $(pModified).attr("class", "project-last-modified");
      $(pModified).text(lastTimeUser);

      var strongP = document.createElement("strong");
      $(strongP).text(projects[i].lastModifiedUser + ".");
      $(pModified).text($(pModified).text());
      pModified.appendChild(strongP);

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
      divFlows.appendChild(tableFlows);

      projectUl.append(row);
    }

    this.renderPagination(evt);
  },
  //组装分页组件
  renderPagination: function (evt) {
    var total = this.model.get("total");
    total = total ? total : 1;
    var pageSize = this.model.get("pageSize");
    var numPages = Math.ceil(total / pageSize);

    this.model.set({ "numPages": numPages });
    var page = this.model.get("page");

    if (page > numPages) {
      page = numPages;
    }

    //Start it off
    $("#projectPageSelection .active").removeClass("active");

    // Disable if less than 5
    // 页面选择按钮
    console.log("Num pages " + numPages)
    var i = 1;
    for (; i <= numPages && i <= 5; ++i) {
      $("#page" + i).removeClass("disabled");
    }
    for (; i <= 5; ++i) {
      $("#page" + i).addClass("disabled");
    }

    // Disable prev/next if necessary.
    // 上一页按钮
    if (page > 1) {
      var prevNum = parseInt(page) - parseInt(1);
      $("#previous").removeClass("disabled");
      $("#previous")[0].page = prevNum;
      $("#previous a").attr("href", "#page" + prevNum + "#pageSize" + pageSize);
    }
    else {
      $("#previous").addClass("disabled");
    }
    // 下一页按钮
    if (page < numPages) {
      var nextNum = parseInt(page) + parseInt(1);
      $("#next")[0].page = nextNum;
      $("#next").removeClass("disabled");
      $("#next a").attr("href", "#page" + nextNum + "#pageSize" + pageSize);
    }
    else {
      var nextNum = parseInt(page) + parseInt(1);
      $("#next").addClass("disabled");
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

    $("#page" + selectionPosition).addClass("active");
    $("#page" + selectionPosition)[0].page = page;
    var selecta = $("#page" + selectionPosition + " a");
    selecta.text(page);
    selecta.attr("href", "#page" + page + "#pageSize" + pageSize);

    for (var j = 0; j < 5; ++j) {
      var realPage = startPage + j;
      var elementId = "#page" + (j + 1);
      if ($(elementId).hasClass("disabled")) {
        $(elementId)[0].page = realPage;
        var a = $(elementId + " a");
        a.text(realPage);
        a.attr("href", "javascript:void(0);");
      } else {
        $(elementId)[0].page = realPage;
        var a = $(elementId + " a");
        a.text(realPage);
        a.attr("href", "#page" + realPage + "#pageSize" + pageSize);
      }
    }
  },

  handleProjectChangePageSelection: function (evt) {
    if ($(evt.currentTarget).hasClass("disabled")) {
      return;
    }
    var page = evt.currentTarget.page;
    this.model.set({ "page": page });
    var pageSize = $("#pageSizeSelect").val();
    this.model.set({ "pageSize": pageSize });
  },

  handleChangeView: function (evt) {
    if (this.init) {
      return;
    }
    console.log("init");
    this.handlePageChange(evt);
    this.init = true;
  },

  handlePageChange: function (evt) {
    var start = this.model.get("page") - 1;
    var pageSize = this.model.get("pageSize");
    var requestURL = contextURL + "/index";
    var searchText = this.model.get("searchterm");
    var group = this.model.get("group");
    var all = this.model.get("all");

    var model = this.model;
    var requestData;
    if (searchText) {
      requestData = {
        "ajax": "fetchProjectPage",
        "start": start,
        "length": pageSize,
        "pageNum": this.model.get("page"),
        "projectsType": "doaction",
        "searchterm": searchText,
        "doaction": true,
        "group": group,
        "all": all,
        "order": this.model.get("order"),
      };
    } else {
      requestData = {
        "ajax": "fetchProjectPage",
        "start": start,
        "length": pageSize,
        "projectsType": projectModel.get("projectsType"),
        "pageNum": this.model.get("page"),
        "order": this.model.get("order"),
      };
    }
    var successHandler = function (data) {
      if (data.total > 0) {
        $("#pageTable").show();
        model.set({
          "projectList": data.projectList,
          "total": data.total
        });
        model.trigger("render");
      } else {
        $("#pageTable").hide();
      }
    };
    $.get(requestURL, requestData, successHandler, "json");
  },

  handleProjectPersonalNav: function () {
    projectModel.set({ "projectsType": "personal" });
  },

  handleProjectGroupNav: function () {
    projectModel.set({ "projectsType": "group" });
  },

  handleProjectAllNav: function () {
    projectModel.set({ "projectsType": "all" });
  },

  //进入Project页面开始调用这个方法获取后台Project信息
  expandProject: function (evt) {
    if (evt.target.tagName == "A") {
      return;
    }

    var target = evt.currentTarget;
    var targetId = target.id;
    var requestURL = contextURL + "/manager";

    var targetExpanded = $('#' + targetId + '-child');
    var targetTBody = $('#' + targetId + '-tbody');
    var flowDetailTable = $('#' + targetId + '-detail-table');
    var createFlowListFunction = this.createFlowListTable;

    if (target.loading) {
      console.log("Still loading.");
    }
    else if (target.loaded) {
      if ($(targetExpanded).is(':visible')) {
        $(target).addClass('expanded').removeClass('collapsed');
        var expander = $(target).children('.project-expander-icon')[0];
        $(expander).removeClass('glyphicon-chevron-up');
        $(expander).addClass('glyphicon-chevron-down');
        $(targetExpanded).slideUp(300);
      }
      else {
        $(target).addClass('collapsed').removeClass('expanded');
        var expander = $(target).children('.project-expander-icon')[0];
        $(expander).removeClass('glyphicon-chevron-down');
        $(expander).addClass('glyphicon-chevron-up');
        $(targetExpanded).slideDown(300);
      }
    }
    else {
      // projectId is available
      $(target).addClass('wait').removeClass('collapsed').removeClass(
        'expanded');
      target.loading = true;

      var request = {
        "project": targetId,
        "ajax": "fetchprojectflows"
      };

      var successHandler = function (data) {
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
  createFlowListTable: function (data, innerTable) {
    var flows = data.flows;
    flows.sort(function (a, b) {
      return a.flowId.localeCompare(b.flowId);
    });
    var requestURL = contextURL + "/manager?project=" + data.project + "&flow=";
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
        $(ida).attr("href", requestURL + id);
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
          var flowLogURL = contextURL + "/executor?execid=";
          $(ida3).text(lastExecutionLog);
          $(ida3).attr("href", flowLogURL + flowExecId + "#log");
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


  handleProjectPageSizeSelection: function (evt) {
    var pageSize = evt.currentTarget.value;
    this.model.set({ "pageSize": pageSize });
    projectModel.set({ "pageSize": pageSize });
    this.model.set({ "page": 1 });
    this.init = false;
    //projectModel.trigger("change:view");
    //window.location.href=contextURL + "/index";
    var search = window.location.search
    var arr = search.split("#");
    var pageURL = arr[0] + "#page1";
    var pageSizeURL = pageURL + "#pageSzie" + pageSize;

    var projectURL = contextURL + "/index"

    var pageSizeFirestURL = projectURL + pageSizeURL;
    //防止XSS DOM攻击,类似:javascript:alert(1);//http://www.qq.com,所以对URL进行正则校验
    var reg = /^(http|ftp|https):\/\/[\w\-_]+(\.[\w\-_]+)+([\w\-\.,@?^=%&:/~\+#]*[\w\-\@?^=%&/~\+#])?/;
    if (reg.test(pageSizeFirestURL)) {
      window.location = pageSizeFirestURL;
    }
    projectModel.trigger("change:view");
  },

  handleProjectPageNumJump: function (evt) {

    var pageNum = $("#pageNumInput").val();
    if (pageNum <= 0) {
      //alert("页数必须大于1!!!");
      return;
    }

    this.model.set({ "page": pageNum });
    this.init = false;
    projectModel.trigger("change:view");
  },

  // 项目-下拉展开-删除
  handleShowDeleteProjectView: function (evt) {

    var self = this;
    var projectName = $(self).attr("id")
    console.log(projectName);

    // 需要校验是否具有执行工作流权限 1:允许, 2:不允许
    var requestURL = contextURL + "/manager?ajax=checkDeleteProjectFlagPermission&project=" + projectName;
    $.ajax({
      url: requestURL,
      type: "get",
      async: false,
      dataType: "json",
      success: function (data) {
        if (data["deleteProjectFlag"] == 1) {
          console.log("click delete project");
          evt.data.deleteProjectName = $(self).attr("id");
          $("#delete-project-modal .modal-body p").empty();
          $("#delete-project-modal .modal-body p").html("<strong>" + warning + " :</strong> " + projectDeletePro);
          //判断是否有设置了定时调度
          //http://webip:port/manager?ajax=ajaxFetchProjectSchedules&project=child-flow-test2
          var requestURL = contextURL + "/manager?ajax=ajaxFetchProjectSchedules&project=" + $(self).attr("id");
          $.ajax({
            url: requestURL,
            async: false,
            type: "GET",
            dataType: "json",
            success: function (data) {
              if (data["hasSchedule"]) {
                $("#delete-project-modal .modal-body p").empty();
                $("#delete-project-modal .modal-body p").html("<strong>" + warning + " :</strong> " + scheduleProjectPro);
              }
            }
          });
          $('#delete-project-modal').modal();

        } else {
          $('#delete-expand-project-permit-change-panel').modal();
        }
        $("#switching-execute-flow-btn").hide()
        $("#workflow-execute-zoom-in").hide()
      }
    });


  },

  handleDeleteProject: function (evt) {
    console.log("delete project " + this.model.deleteProjectName);
    window.location.href = contextURL + "/manager?project=" + this.model.deleteProjectName + "&delete=true";
  },
});