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

var flowTableView;
azkaban.FlowTableView = Backbone.View.extend({
  events: {
    "click .flow-expander": "expandFlowProject",
    "mouseover .expanded-flow-job-list li": "highlight",
    "mouseout .expanded-flow-job-list li": "unhighlight",
    "click .runJob": "runJob",
    "click .runWithDep": "runWithDep",
    "click .execute-flow": "executeFlow",
    "click .viewFlow": "viewFlow",
    "click .viewJob": "viewJob",
    "click .replenish-collection": "replenishCollection",
    "click .expandarrow": "handleToggleMenuExpand",
    "click .schedule-flow": "scheduleFlow",
    "click .schedule-job": "scheduleJob",
    "click #flowBusinessBtn": "handleFlowBusiness",
    "click .job-business": "handleJobBusiness"
  },

  initialize: function (settings) {
  },
  //项目页面 工作流展示 按钮事件方法
  expandFlowProject: function (evt) {// 节点过滤，分别调用项目flow拿到所有flow、job，但是不展开
    if (evt.target.tagName == "A" || evt.target.tagName == "BUTTON") {
      return;
    }
    var flowCollapse = sessionStorage.getItem('flowCollapse')
    var target = evt.currentTarget;
    var targetId = target.id;
    var requestURL = "/manager";
    //解决jquery不能获取特殊符号ID的问题
    var expanded = document.getElementById(targetId + '-child');
    var tBody = document.getElementById(targetId + '-tbody');
    var targetExpanded = $(expanded);
    var targetTBody = $(tBody);

    var createJobListFunction = this.createJobListTable;
    if (target.loading) {//正在加载
      console.log("Still loading.");
    }
    else if (flowTableView.model[targetId]) {//已经加载过了
      if (tBody.children.length === 0) {
        createJobListFunction(flowTableView.model[targetId], targetTBody);
      }
      $(targetExpanded).collapse('toggle');
      var expander = $(target).children('.flow-expander-icon')[0];
      if ($(expander).hasClass('glyphicon-chevron-down')) {
        $(expander).removeClass('glyphicon-chevron-down');
        $(expander).addClass('glyphicon-chevron-up');
      }
      else {
        $(expander).removeClass('glyphicon-chevron-up');
        $(expander).addClass('glyphicon-chevron-down');
      }
    }
    else {//第一次加载
      // projectName is available
      target.loading = true;
      var requestData = {
        "project": projectName,
        "ajax": "fetchflowjobs",
        "flow": targetId
      };
      var successHandler = function (data) {
        console.log("Success");
        target.loading = false;
        flowTableView.model[targetId] = data
        createJobListFunction(data, targetTBody);
        // 页面渲染时手动获取子工作流信息，用于select子工作流过滤，不展开工作流
        if (flowCollapse && flowCollapse === 'true') {
          return
        }
        $(targetExpanded).collapse('show');
        var expander = $(target).children('.flow-expander-icon')[0];
        $(expander).removeClass('glyphicon-chevron-down');
        $(expander).addClass('glyphicon-chevron-up');
      };
      $.get(requestURL, requestData, successHandler, "json");
    }
  },
  //构建项目Job列表的方法
  createJobListTable: function (data, innerTable) {
    var nodes = data.nodes;
    var flowId = data.flowId;
    var project = data.project;
    var requestURL = filterXSS("/manager?project=" + project + "&flow="
      + flowId + "&job=");
    if (nodes) {
      renderTree($(innerTable), data);
    }

  },

  handleToggleMenuExpand: function (evt) {
    var expandarrow = evt.currentTarget;
    var li = $(evt.currentTarget).closest("li.listElement");
    var submenu = $(li).find("> ul");

    if ($(submenu).is(":visible")) {
      this.handleMenuCollapse(li);
    }
    else {
      this.handleMenuExpand(li);
    }

    evt.stopImmediatePropagation();
  },

  handleMenuCollapse: function (li) {
    var expandArrow = $(li).find("> td > .expandarrow").context;
    var submenu = $(li).find("> ul");

    $(expandArrow).removeClass("expandarrow glyphicon glyphicon-chevron-up");
    $(expandArrow).addClass("expandarrow glyphicon glyphicon-chevron-down");
    $(submenu).slideUp();
  },

  handleMenuExpand: function (li) {
    var expandArrow = $(li).find("> td > .expandarrow").context;
    var submenu = $(li).find("> ul");

    $(expandArrow).removeClass("expandarrow glyphicon glyphicon-chevron-down");
    $(expandArrow).addClass("expandarrow glyphicon glyphicon-chevron-up");
    $(submenu).slideDown();
  },

  unhighlight: function (evt) {
    var currentTarget = evt.currentTarget;
    $(".dependent").removeClass("dependent");
    $(".dependency").removeClass("dependency");
  },

  highlight: function (evt) {
    var currentTarget = evt.currentTarget;
    $(".dependent").removeClass("dependent");
    $(".dependency").removeClass("dependency");
    this.highlightJob(currentTarget);
  },

  highlightJob: function (currentTarget) {
    var dependents = currentTarget.dependents;
    var dependencies = currentTarget.dependencies;
    var flowid = currentTarget.flowId;

    if (dependents) {
      for (var i = 0; i < dependents.length; ++i) {
        var depId = flowid + "-" + dependents[i];
        $("#" + depId).toggleClass("dependent");
      }
    }

    if (dependencies) {
      for (var i = 0; i < dependencies.length; ++i) {
        var depId = flowid + "-" + dependencies[i];
        $("#" + depId).toggleClass("dependency");
      }
    }
  },

  viewFlow: function (evt) {
    console.log("View Flow");
    var flowId = evt.currentTarget.flowId;
    location.href = filterXSS("/manager?project=" + projectName + "&flow="
      + flowId);
  },

  viewJob: function (evt) {
    console.log("View Job");
    var flowId = evt.currentTarget.flowId;
    var jobId = evt.currentTarget.jobId;
    location.href = filterXSS("/manager?project=" + projectName + "&flow="
      + flowId + "&job=" + jobId);
  },


  // 进入单个项目, 展开子工作流, 运行任务
  runJob: function (evt) {

    // 需要校验是否具有执行工作流权限 1:允许, 2:不允许
    var requestURL = "/manager?ajax=checkUserExecuteFlowPermission&project=" + projectName;
    $.ajax({
      url: requestURL,
      type: "get",
      async: false,
      dataType: "json",
      success: function (data) {
        if (data["executeFlowFlag"] == 1) {
          console.log("Run Job");
          var jobId = evt.currentTarget.jobName;
          var flowId = evt.currentTarget.flowId;

          var executingData = {
            project: projectName,
            ajax: "executeFlow",
            flow: flowId,
            job: jobId,
            executeFlowTitle: data["executeFlowTitle"]
          };

          flowTableView.executeFlowDialog(executingData);
        } else if (data["executeFlowFlag"] == 2) {
          $('#user-temp-operator-permit-panel').modal();
          $('#title-user-temp-operator-permit').text(data["executePermission"]);
          $('#body-user-temp-operator-permit').html(data["noexecuteFlowPermission"]);
        }
      }
    });
  },

  // 进入单个项目, 展开子工作流, 依赖运行
  runWithDep: function (evt) {

    // 需要校验是否具有执行工作流权限 1:允许, 2:不允许
    var requestURL = "/manager?ajax=checkUserExecuteFlowPermission&project=" + projectName;
    $.ajax({
      url: requestURL,
      type: "get",
      async: false,
      dataType: "json",
      success: function (data) {
        if (data["executeFlowFlag"] == 1) {
          var jobId = evt.currentTarget.jobName;
          var flowId = evt.currentTarget.flowId;
          console.log("Run With Dep");

          var executingData = {
            project: projectName,
            ajax: "executeFlow",
            flow: flowId,
            job: jobId,
            withDep: true,
            executeFlowTitle: data["executeFlowTitle"]
          };
          flowTableView.executeFlowDialog(executingData);
        } else if (data["executeFlowFlag"] == 2) {
          $('#user-temp-operator-permit-panel').modal();
          $('#title-user-temp-operator-permit').text(data["executePermission"]);
          $('#body-user-temp-operator-permit').html(data["noexecuteFlowPermission"]);
        }
      }
    });
  },

  // 进入单个项目, 不展开子工作流, 执行工作流
  executeFlow: function (evt) {
    // 需要校验是否具有执行工作流权限 1:允许, 2:不允许
    var requestURL = "/manager?ajax=checkUserExecuteFlowPermission&project=" + projectName;
    $.ajax({
      url: requestURL,
      type: "get",
      async: false,
      dataType: "json",
      success: function (data) {
        if (data["executeFlowFlag"] == 1) {
          console.log("temp execute flow");
          var flowId = $(evt.currentTarget).attr('flowid');

          var executingData = {
            project: projectName,
            ajax: "executeFlow",
            flow: flowId,
            executeFlowTitle: data["executeFlowTitle"]
          };

          flowTableView.executeFlowDialog(executingData);
        } else if (data["executeFlowFlag"] == 2) {
          $('#user-temp-operator-permit-panel').modal();
          $('#title-user-temp-operator-permit').text(data["executePermission"]);
          $('#body-user-temp-operator-permit').html(data["noexecuteFlowPermission"]);
        }
      }
    });
  },

  executeFlowDialog: function (executingData) {
    localStorage.setItem('isBackupRerun', "false"); // 清除容灾重跑
    flowExecuteDialogView.show(executingData);
  },

  render: function () {
  },

  replenishCollection: function (evt) {
    console.log("Repeat Collection");
    var flowId = $(evt.currentTarget).attr('flowid');

    var executingData = {
      project: projectName,
      ajax: "executeFlow",
      flow: flowId
    };

    repeatCollectionDialogView.show(executingData);
  },

  scheduleFlow: function (evt) {
    // 发请求获取scheduleId
    var cronExpression = "";
    var scheduleId = "";
    var scheduleStartDate = "";
    var scheduleEndDate = "";
    var tempFlowId = $(evt.currentTarget).attr('flowid');
    var scheduleType = $(evt.currentTarget).attr('id');
    var topic = "";
    var msgName = "";
    var saveKey = "";
    var token = "";
    if (scheduleType == 'time') {

      var requestURLForFetchScheduleId = "/manager?ajax=fetchRunningScheduleId&project=" + projectName + "&flow=" + tempFlowId;
      $.ajax({
        url: requestURLForFetchScheduleId,
        type: "get",
        async: false,
        dataType: "json",
        success: function (data) {
          if (data.error) {
            console.log(data.error.message);
          } else {
            cronExpression = data.cronExpression;
            scheduleId = data.scheduleId;
            scheduleStartDate = data.scheduleStartDate;
            scheduleEndDate = data.scheduleEndDate;
          }
        }
      });
    } else if (scheduleType == 'event') {
      var requestURLForFetchScheduleId = "/manager?ajax=fetchRunningEventScheduleId&project=" + projectName + "&flow=" + tempFlowId;
      $.ajax({
        url: requestURLForFetchScheduleId,
        type: "get",
        async: false,
        dataType: "json",
        success: function (data) {
          if (data.error) {
            console.log(data.error.message);
          } else {
            scheduleId = data.scheduleId;
            topic = data.topic;
            msgName = data.msgName;
            saveKey = data.saveKey;
            token = data.token;
          }
        }
      });
    }

    // 进入项目,不展开flow, 直接点击定时调度
    // 需要校验是否具有执行调度权限 1:允许, 2:不允许
    var requestURL = "/manager?ajax=checkUserScheduleFlowPermission&project=" + projectName;
    $.ajax({
      url: requestURL,
      type: "get",
      async: false,
      dataType: "json",
      success: function (data) {
        if (data["scheduleFlowFlag"] == 1) {
          console.log("temp schedule flow");
          var flowId = $(evt.currentTarget).attr('flowid');
          var executingData;
          if (scheduleId) {
            if (scheduleType == 'time') {

              executingData = {
                project: projectName,
                flow: flowId,
                scheduleId: scheduleId,
                cronExpression: cronExpression,
                scheduleStartDate: scheduleStartDate,
                scheduleEndDate: scheduleEndDate,
                scheduleFlowTitle: data["scheduleFlowTitle"],
                scheduleType: scheduleType
              };
            } else if (scheduleType == 'event') {
              executingData = {
                project: projectName,
                flow: flowId,
                scheduleId: scheduleId,
                topic: topic,
                msgName: msgName,
                saveKey: saveKey,
                token: token,
                scheduleFlowTitle: data["eventScheduleFlowTitle"],
                scheduleType: scheduleType
              };
            }
          } else {
            executingData = {
              project: projectName,
              flow: flowId,
              scheduleFlowTitle: data["scheduleFlowTitle"],
              scheduleType: scheduleType
            };
            if (scheduleType == 'event') {
              executingData.scheduleFlowTitle = data["eventScheduleFlowTitle"]
            }
          }

          if (scheduleType == 'time') {
            flowScheduleDialogView.show(executingData);
          } else if (scheduleType == 'event') {
            eventScheduleJobSkipFailedView.setFlowID(flowId, projectName);
            eventJobScheduleFailedRetryView.setFlowID(flowId, projectName);
            flowEventScheduleDialogView.show(executingData, true);
          }
        } else if (data["scheduleFlowFlag"] == 2) {
          $('#user-temp-operator-permit-panel').modal();
          $('#title-user-temp-operator-permit').text(data["schFlowPermission"]);
          $('#body-user-temp-operator-permit').html(data["noSchPermissionsFlow"]);
        }
      }
    });
  },

  scheduleFlowDialog: function (executingData) {
    flowScheduleDialogView.show(executingData);
  },

  scheduleJob: function (evt) {

    // 发请求获取scheduleId
    var cronExpression = "";
    var scheduleId = "";
    var scheduleStartDate = "";
    var scheduleEndDate = "";
    var tempFlowId = evt.currentTarget.flowId;
    var requestURLForFetchScheduleId = "/manager?ajax=fetchRunningScheduleId&project=" + projectName + "&flow=" + tempFlowId;
    $.ajax({
      url: requestURLForFetchScheduleId,
      type: "get",
      async: false,
      dataType: "json",
      success: function (data) {
        if (data.error) {
          console.log(data.error.message);
        } else {
          cronExpression = data.cronExpression;
          scheduleId = data.scheduleId;
          scheduleStartDate = data.scheduleStartDate;
          scheduleEndDate = data.scheduleEndDate;
        }
      }

    });
    // 展开flow, 子flow的定时调度
    // 需要校验是否具有执行调度权限 1:允许, 2:不允许
    var requestURL = "/manager?ajax=checkUserScheduleFlowPermission&project=" + projectName;
    $.ajax({
      url: requestURL,
      type: "get",
      async: false,
      dataType: "json",
      success: function (data) {
        if (data["scheduleFlowFlag"] == 1) {
          console.log("Schedule Job");
          var jobId = evt.currentTarget.jobName;
          var flowId = evt.currentTarget.flowId;

          var executingData;
          if (scheduleId) {
            executingData = {
              project: projectName,
              flow: flowId,
              job: jobId,
              scheduleId: scheduleId,
              cronExpression: cronExpression,
              scheduleStartDate: scheduleStartDate,
              scheduleEndDate: scheduleEndDate,
              scheduleFlowTitle: data["scheduleFlowTitle"]
            };
          } else {
            executingData = {
              project: projectName,
              flow: flowId,
              job: jobId,
              scheduleFlowTitle: data["scheduleFlowTitle"]
            };
          }

          flowTableView.scheduleFlowDialog(executingData);
        } else if (data["scheduleFlowFlag"] == 2) {
          $('#user-temp-operator-permit-panel').modal();
          $('#title-user-temp-operator-permit').text(data["schFlowPermission"]);
          $('#body-user-temp-operator-permit').html(data["noSchPermissionsFlow"]);
        }
      }
    });
  },

  handleFlowBusiness: function (evt) {
    projectView.initProjectBusinessForm();
    $('#merge-business-panel').modal();
    $("#merge-business-info-msg").hide();
    $('#flow-business-id').val(evt.currentTarget.value);
    $('#job-business-id').val("");
    mergeProjectBusinessView.render();
    mergeProjectBusinessView.loadBusinessData();
  },
  handleJobBusiness: function (evt) {
    projectView.initProjectBusinessForm();
    $('#merge-business-panel').modal();
    $("#merge-business-info-msg").hide();
    $('#flow-business-id').val($(evt.currentTarget).attr('flowBusinessId'));
    $('#job-business-id').val($(evt.currentTarget).attr('jobBusinessId'));
    mergeProjectBusinessView.render();
    mergeProjectBusinessView.loadBusinessData();
  }

});

$(function () {
  azkaban.SubFlowModel = Backbone.Model.extend({});
  var subFlowModel = new azkaban.SubFlowModel();
  flowTableView = new azkaban.FlowTableView({ el: $('#flow-tabs'), model: subFlowModel });

  $("#quickSearchEventStatus").click(function () {
    $("#quickSearchForm").submit();
  });



  //  折叠/展开工作流列表
  $("#closeFlowList").click(function (i) {
    $(".flow-expander").each(function (i) {
      $(this).click();
    });
  });
  //获取所有flow的子工作流，用于job 拉下
  function getFlowExpanderData () {
    sessionStorage.setItem('flowCollapse', true)
    $(".flow-expander").each(function (i) {
      $(this).click();
    });
    sessionStorage.clear('flowCollapse')
  }
  if ($(".flow-expander")[0]) {
    window.projectFlowData = []
    $(".flow-expander").each(function (i, ele) {
      window.projectFlowData.push({
        id: ele.getAttribute('id')
      })
    })
    getFlowExpanderData()
    renderSelectFun(true) //select
  } else {
    $('#filterJobBox').hide()
    $('#filterFlowBox').hide()
  }


  // 渲染工作流列表
  function renderFlowList (filterFlow) {
    if (window.projectFlowData) {
      var flowHtml = ''
      for (var i = 0; i < window.projectFlowData.length; i++) {
        var flow = window.projectFlowData[i]
        // 一级过滤select 选择flow ，filter_job为所有flow
        if (filterFlow && filterFlow !== 'filter_job' && filterFlow !== flow.id) {
          continue;
        }
        flowHtml += '<div class="panel panel-default" flow="' + flow.id + '" project="' + window.projectName + '">' +
          '<div class="panel-heading flow-expander" id="' + flow.id + '"><div class="pull-right">'
        if (window.scheduleAccess) {
          flowHtml += '<button type="button" class="btn btn-xs btn-success schedule-flow" flowId="' +
            flow.id + '" id="event">'+ wtssI18n.view.signalScheduling+'</button>&nbsp;&nbsp;<button type="button" class="btn btn-xs btn-success schedule-flow"' +
            'flowId="' + flow.id + '" id="time">'+ wtssI18n.view.timedScheduling+'</button>'
        }
        if (window.execAccess) {
          flowHtml += '&nbsp;&nbsp;<button type="button" class="btn btn-xs btn-success execute-flow" flowId="' + flow.id +
            '">'+ wtssI18n.view.executeFlow+'</button>&nbsp;&nbsp;<a href="javascript:void(0);" onclick="checkHrefUrlXss(\'/manager?project=' +
            window.projectName + '&flow=' + flow.id + '#executions\')" class="btn btn-info btn-xs">'+ wtssI18n.view.executionHistory+'</a>'

        }
        flowHtml += '&nbsp;&nbsp;<button type="button" class="btn btn-xs btn-warning" id="flowBusinessBtn"' +
          'value="' + flow.id + '">'+ wtssI18n.view.applicationInformation+' </button></div>' +
          '<span class="glyphicon glyphicon-chevron-down flow-expander-icon"></span>' +
          '<a href="javascript:void(0);" onclick="checkHrefUrlXss(\'/manager?project=' + window.projectName + '&flow=' + flow.id +
          '\')" style="word-break:break-all;">' + flow.id + '</a></div>' +
          '<div id="' + flow.id + '-child" class="panel-collapse panel-list collapse">' +
          '<ul class="list-group list-group-collapse expanded-flow-job-list" id="' + flow.id + '-tbody"></ul></div> </div>'
      }
      document.getElementById('flow-list').innerHTML = flowHtml

    } else {
      document.getElementById('flow-list').innerHTML = '<div class="callout callout-default"><h4>$noFlow</h4><p>$noFlowTips</p></div>'
    }
  }

  // 处理flow 下拉数据
  function handleFlowSelectList () {
    var flow = [{
      id: 'filter_flow',
      text: wtssI18n.view.filterFlowName
    }]
    for (var i = 0; i < window.projectFlowData.length; i++) {
      var item = window.projectFlowData[i]
      flow.push({ id: item.id, text: item.id })
    }
    return flow
  }

  // 处理job 下拉数据
  function hanleJobSelectList () {
    var job = [{
      id: 'filter_job',
      text: wtssI18n.view.filterJobName
    }]
    for (var j = 0; j < window.projectFlowData.length; j++) {
        var flowId = window.projectFlowData[j].id;
        if (flowId && flowId !== 'filter_job') {
            var jobList = flowTableView.model[flowId].nodes
            recursiveRerunNode(jobList)
            function recursiveRerunNode (nodes) {
              for (var i = 0; i < nodes.length; i++) {
                var item = nodes[i]
                job.push({ id: item.id, text: item.id })
                if (item.type === 'flow') {
                  recursiveRerunNode(item.nodes)
                }
              }
            }
        }
    }
    return job
  }

    // 处理job 下拉数据
    function hanleFlowJobSelectList (flowId) {
        var job = [{
          id: 'filter_job',
          text: wtssI18n.view.filterJobName
        }]
        if (flowId && flowId !== 'filter_job') {
            var jobList = flowTableView.model[flowId].nodes
             recursiveRerunNode(jobList)
            function recursiveRerunNode (nodes) {
                for (var i = 0; i < nodes.length; i++) {
                    var item = nodes[i]
                    job.push({ id: item.id, text: item.id })
                    if (item.type === 'flow') {
                      recursiveRerunNode(item.nodes)
                    }
                }
            }
        }
        return job
    }

  function byJobFindFlowId(jobId) {
    var jobFlowId = "";
    for (var j = 0; j < window.projectFlowData.length; j++) {
        if(jobFlowId) {
            break;
        }
        var flowId = window.projectFlowData[j].id;
        var jobList = flowTableView.model[flowId].nodes;
        recursiveRerunNode(jobList);
        function recursiveRerunNode (nodes) {
            for (var i = 0; i < nodes.length; i++) {
                var item = nodes[i];
                if (jobId === item.id) {
                    jobFlowId =  flowId;
                    break;
                }
                if (item.type === 'flow' && !jobFlowId) {
                  recursiveRerunNode(item.nodes);
                }
            }
        }
    }
    return jobFlowId;
  }

  //flow job select赋值
  function renderSelectFun (first) {
    var flowSelect = handleFlowSelectList();
    loadFlowJobListData("flowFilterList", flowSelect, first);//true 首次渲染select
    var defaultSelect = [{
        id: 'filter_job',
        text: wtssI18n.view.filterJobName
    }]
    loadFlowJobListData("subFlowFilterList", defaultSelect, first);
    var setSubflowSelect = setInterval(() => {
        var fetchFlowFinish = true;
        for (var j = 0; j < window.projectFlowData.length; j++) {
            var flowId = window.projectFlowData[j].id;
            if (!flowTableView.model[flowId]) {
                fetchFlowFinish = false;
                break;
            }
        }
        if (fetchFlowFinish) {
            clearInterval(setSubflowSelect);
            var jobSelect = hanleJobSelectList();
            flowTableView.model.allSubflowOption = jobSelect;
            loadFlowJobListData("subFlowFilterList", jobSelect);
        }
    }, 800);

  }


  // 通过工作流过滤表格数据
  $("#flowFilterList").on('change', function (e) {
    var flowId = e.target.value

    if (flowId === 'filter_flow') {
      flowId = ''
      renderFlowList()
      loadFlowJobListData("subFlowFilterList", flowTableView.model.allSubflowOption);
    } else {
      renderFlowList(flowId)
      var jobSelect = hanleFlowJobSelectList(flowId);
      loadFlowJobListData("subFlowFilterList", jobSelect);
    }
    $("#subFlowFilterList").val('filter_job').trigger("change");
  })
  // 通过任务过滤表格数据
  $("#subFlowFilterList").on('change', function (e) {
    var flowId = $("#flowFilterList").val();
    var jobId = $("#subFlowFilterList").val();
    if (flowId === 'filter_flow' && jobId !== "filter_job") {
        flowId = byJobFindFlowId(jobId);
        renderFlowList(flowId);
    } else if (flowId === 'filter_flow' && jobId === "filter_job") {
        // 展示所有工作流
        renderFlowList();
        return;
    }
    var data = handleFilterPrajectJobList(flowId, jobId);
    var expanded = flowId + '-child';
    var flowBodyId = flowId + '-tbody';
    $("#" + flowBodyId + "").html('');
    var subFlowLevel = jobId && jobId !== "filter_job" ? 1 : 0
    renderTree($("#" + flowBodyId + ""), data, "", flowId, subFlowLevel) //过滤job记录 subFlowLevel 层级大于2时展开
    var expander = $("#" + flowId + "").children('.flow-expander-icon')[0];
    if (expander.getAttribute('class').indexOf('glyphicon-chevron-down') > -1) {
      $("#" + expanded + "").collapse('show');
      $(expander).removeClass('glyphicon-chevron-down');
      $(expander).addClass('glyphicon-chevron-up');
    }
  })
  // 过滤子工作流
  function handleFilterPrajectJobList (flowId, jobId) {
    var data = [];
    var flowLostData = flowTableView.model[flowId];
    var nodes = _.cloneDeep(flowLostData.nodes);
    JobListNodesSort(nodes)
    if (jobId === 'filter_job') {
      data = nodes
    } else {
      // 标记最后一个包含过滤状态的对象
      var currentFilter = null
      // 要删除不包含过滤状态的数据，避免删除后数据错位从后面开始遍历
      for (var i = nodes.length - 1; i >= 0; i--) {
        // 删除该对象子工作流，不包含过滤状态数据
        // if (currentFilter && currentFilter.nodes) {
        //   delete currentFilter.nodes
        // }
        // 下一个节点时先初始化
        currentFilter = null
        recursionNode(nodes[i], nodes, i)
      }
    }
    function recursionNode (subNode, nodeList, index) {
      if (jobId === subNode.id) {
        // 如果currentFilter为空证明该节点或该节点的父节点没有push到data
        if (!currentFilter) {
          data.unshift(nodes[i])
        }
        currentFilter = subNode
        // 删除该对象子工作流，不包含过滤状态数据
        if (currentFilter.nodes) {
          delete currentFilter.nodes
          return
        }
      } else {
        if (!subNode.nodes) {
          nodeList.splice(index, 1)
        }
      }
      if (subNode.nodes) {
        for (var j = subNode.nodes.length - 1; j >= 0; j--) {
          recursionNode(subNode.nodes[j], subNode.nodes, j)
        }
        // 当遍历完没有找到过滤对象则删除该节点
        if (!currentFilter) {
          nodeList.splice(index, 1)
        }
      }
    }
    return data
  }
  // 任务列表节点显示项目初始化显示顺序一致
  function JobListNodesSort (nodes) {
    for (var i = 0; i < nodes.length; i++) {
      var node = nodes[i]
      if (node.type == 'flow') {
        node.nodes.sort(idSort);
        JobListNodesSort(node.nodes)
      }
    }
  }
  function loadFlowJobListData (selectId, optionData, initSelect) { // initSelect true 首次渲染select
    if (initSelect) {
      $("#" + selectId + "").select2({
        placeholder: wtssI18n.view.filterCriteriaPro,//默认文字提示
        multiple: false,
        width: 'resolve',
        //tags: true,//允许手动添加
        //allowClear: true,//允许清空
        escapeMarkup: function (markup) {
          return markup;
        }, //自定义格式化防止XSS注入
        minimumInputLengt: 1,//最少输入多少字符后开始查询
        formatResult: function formatRepo (repo) {
          return repo.text;
        },//函数用来渲染结果
        formatSelection: function formatRepoSelection (repo) {
          return repo.text;
        },//函数用于呈现当前的选择
        data: optionData,
        language: 'zh-CN',

      });
    } else {
      if (selectId === 'subFlowFilterList') {
        $("#" + selectId + "").html('')
        $("#select2-" + selectId + "-results").html('')
      }
      $("#" + selectId + "").select2({
        placeholderOption: 'first',
        allowClear: true,
        data: optionData
      })
    }
  }
});


var listNodes = {};

function renderTree (el, data, prefix, filterFlowId, subFlowLevel) {//过滤job记录 subFlowLevel 层级大于2时展开
  var nodes = data.__proto__ === Array.prototype ? data : data.nodes;


  if (!nodes || nodes.length == 0) {
    console.log("No results");
    return;
  };
  if (!prefix) {
    prefix = "";
  }

  var nodeArray = nodes.slice(0);
  // nodeArray.sort(function(a, b) {
  //   var diff = a.y - b.y;
  //   if (diff == 0) {
  //     return a.level - b.level;
  //   }
  //   else {
  //     return diff;
  //   }
  //   return a.level - b.level;
  // });

  var flowId = data.__proto__ === Array.prototype && filterFlowId ? filterFlowId : data.flow;

  var ul = document.createElement('ul');
  $(ul).addClass("tree-list");

  for (var i = 0; i < nodeArray.length; ++i) {

    var job = nodeArray[i];

    var project = job.projectName;

    var requestURL = "/manager?project=" + project + "&flow=" + flowId + "&job=";

    var name = nodeArray[i].id;

    var li = document.createElement("li");
    $(li).addClass("listElement");
    $(li).addClass("tree-list-item");
    //$(li).css("margin","10px 0px 10px 10px");
    $(li).css("border-bottom", "solid 1px #dddddd");
    $(li).css("position", "relative");
    $(li).css("padding", "10px 15px");

    // This is used for the filter step.
    var listNodeName = prefix + nodeArray[i].id;
    this.listNodes[listNodeName] = li;
    li.node = nodeArray[i];
    li.node.listElement = li;

    var table = $("<table style='width:100%;'></table>");
    $(li).append(table);
    var tr = $("<tr></tr>");
    table.append(tr);


    if (nodeArray[i].type == "flow") {

      var expandTd = $("<td style='width:17px'></td>");
      tr.append(expandTd);
      // Add the up down
      var expandDiv = document.createElement("div");
      var chevron = subFlowLevel && subFlowLevel >= 2 ? "glyphicon-chevron-up" : "glyphicon-chevron-down";
      $(expandDiv).addClass("expandarrow glyphicon " + chevron);
      $(expandTd).append(expandDiv);
      // Create subtree
      if (subFlowLevel) {
        ++subFlowLevel
      }
      var subul = this.renderTree(li, nodeArray[i], listNodeName + ":", filterFlowId, subFlowLevel);
      if (!subFlowLevel) {
        $(subul).hide();
      }

    }


    var urlTd = $("<td></td>");
    urlTd.attr("style", "width:80%;");
    tr.append(urlTd);

    if (job.type == "flow") {
      var a = document.createElement("a");
      var iconDiv = document.createElement('div');
      $(iconDiv).addClass('icon');
      $(a).attr("href", filterXSS("/manager?project=" + project + "&flow=" + job.flow + "&job=" + name + "&treeFlow=" + flowId));
      $(a).append(iconDiv);
    } else {
      var a = document.createElement("a");
      var iconDiv = document.createElement('div');
      $(iconDiv).addClass('icon');
      $(a).attr("href", filterXSS(requestURL + name));
      $(a).append(iconDiv);
    }


    var span = document.createElement("span");
    $(span).text(nodeArray[i].id);
    $(span).addClass("jobname");
    $(span).attr("style", "word-break:break-all;");
    $(a).append(span);
    $(li).append(a);
    $(ul).append(li);

    urlTd.append($(a));


    li.dependents = job.dependents;
    li.dependencies = job.dependencies;
    li.projectName = project;
    li.jobName = name;

    // if (nodeArray[i].type == "flow") {
    //    name=;
    //    flow=;
    // }

    var languageCategory = langType;
    console.log("languageCategory value is=" + languageCategory);
    var schTag;
    var runJobTag;
    var runFlowTag;
    var relyRunTag;
    var execHistoryTag;
    var flowBusinessTag;
    var jobBusinessTag;

    if (languageCategory == "en_US") {
      schTag = "Schedule";
      runJobTag = "Run Job";
      runFlowTag = "Run Flow";
      relyRunTag = "Rely Run";
      execHistoryTag = "Execute History";
      flowBusinessTag = "Flow Business";
      jobBusinessTag = "Job Business";
    } else {
      schTag = "定时调度";
      runJobTag = "运行任务";
      runFlowTag = "运行工作流";
      relyRunTag = "依赖运行";
      execHistoryTag = "执行历史";
      flowBusinessTag = "应用信息";
      jobBusinessTag = "应用信息";
    }

    var hoverMenuDiv = document.createElement('div');
    $(hoverMenuDiv).addClass('pull-right');
    $(hoverMenuDiv).addClass('job-buttons');

    if (scheduleAccess) {



      var buttonTdSchedule = $("<td></td>");
      var divScheduleJob = document.createElement('button');
      $(divScheduleJob).attr('type', 'button');
      $(divScheduleJob).addClass("btn");
      $(divScheduleJob).addClass("btn-success");
      $(divScheduleJob).addClass("btn-xs");
      $(divScheduleJob).addClass("schedule-job");
      $(divScheduleJob).css("float", "right");
      $(divScheduleJob).text(schTag);
      if (job.type == "flow") {
        divScheduleJob.flowId = job.flow;
      } else {
        divScheduleJob.jobName = name;
        divScheduleJob.flowId = flowId;
      }
      buttonTdSchedule.append(divScheduleJob);
      tr.append(buttonTdSchedule);
    }

    if (execAccess) {
      var buttonTd = $("<td></td>");
      var buttonTd2 = $("<td></td>");
      var buttonTd3 = $("<td></td>");

      if (job.type == "flow") {
        var divRunJob = document.createElement('button');
        $(divRunJob).attr('type', 'button');
        $(divRunJob).addClass("btn");
        $(divRunJob).addClass("btn-success");
        $(divRunJob).addClass("btn-xs");
        $(divRunJob).addClass("execute-flow");
        $(divRunJob).text(runFlowTag);
        $(divRunJob).css("margin", "0px 0px 0px 10px");
        $(divRunJob).attr("flowid", job.flow);

        buttonTd.append(divRunJob);
      } else {
        var divRunJob = document.createElement('button');
        $(divRunJob).attr('type', 'button');
        $(divRunJob).addClass("btn");
        $(divRunJob).addClass("btn-success");
        $(divRunJob).addClass("btn-xs");
        $(divRunJob).addClass("runJob");
        $(divRunJob).text(runJobTag);
        $(divRunJob).css("margin", "0px 0px 0px 10px");
        divRunJob.jobName = name;
        divRunJob.flowId = flowId;

        buttonTd.append(divRunJob);
      }
      tr.append(buttonTd);


      var divRunWithDep = document.createElement("button");
      $(divRunWithDep).attr('type', 'button');
      $(divRunWithDep).addClass("btn");
      $(divRunWithDep).addClass("btn-success");
      $(divRunWithDep).addClass("btn-xs");
      $(divRunWithDep).addClass("runWithDep");
      $(divRunWithDep).text(relyRunTag);
      $(divRunWithDep).css("margin", "0px 10px 0px 10px");
      divRunWithDep.jobName = name;
      divRunWithDep.flowId = flowId;
      //$(hoverMenuDiv).append(divRunWithDep);

      buttonTd2.append(divRunWithDep);
      tr.append(buttonTd2);

      // var divJobHistory = document.createElement("button");
      // $(divJobHistory).attr('type', 'button');
      // $(divJobHistory).addClass("btn");
      // $(divJobHistory).addClass("btn-success");
      // $(divJobHistory).addClass("btn-xs");
      //$(divJobHistory).addClass("execute-flow");
      // $(divJobHistory).text("执行历史");
      // $(divJobHistory).css("margin","0px 10px 0px 10px");

      var aJobHistory = document.createElement("a");
      $(aJobHistory).addClass("btn btn-info btn-xs");

      if (job.type == "flow") {
        $(aJobHistory).attr("href", filterXSS("/manager?project=" + project + "&flow=" + job.flow + "#executions"));
      } else {
        $(aJobHistory).attr("href", filterXSS("/manager?project=" + project + "&job=" + name + "&history"));
      }
      $(aJobHistory).text(execHistoryTag);
      //$(divJobHistory).append(aJobHistory);
      //$(hoverMenuDiv).append(divJobHistory);

      buttonTd3.append(aJobHistory);
      tr.append(buttonTd3);

      //$(buttonTd).append(hoverMenuDiv);
    }

    var buttonTd4 = $("<td></td>");
    var divJobBusiness = document.createElement('button');
    $(divJobBusiness).attr('type', 'button');
    $(divJobBusiness).addClass("btn");
    $(divJobBusiness).addClass("btn-xs");
    $(divJobBusiness).addClass("btn-warning");
    $(divJobBusiness).addClass("job-business");
    $(divJobBusiness).css("margin", "0px 0px 0px 10px");

    if (job.type == "flow") {
      $(divJobBusiness).text(flowBusinessTag);
      $(divJobBusiness).attr("flowBusinessId", job.flow);
    } else {
      $(divJobBusiness).text(jobBusinessTag);
      $(divJobBusiness).attr("flowBusinessId", flowId);
      $(divJobBusiness).attr("jobBusinessId", name);
    }
    buttonTd4.append(divJobBusiness);
    tr.append(buttonTd4);

  }

  $(el).append(ul);
  return ul;
}


