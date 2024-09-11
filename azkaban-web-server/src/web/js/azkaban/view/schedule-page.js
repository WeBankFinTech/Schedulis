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

// 定时调度分页
$.namespace('azkaban');


$(function () {
  // 在切换选项卡之前创建模型
  scheduleModel = new azkaban.ScheduleModel();

  scheduleListView = new azkaban.ScheduleListView({
    el: $('#schedule-view'),
    model: scheduleModel
  });

  $("#quick-serach-btn").click(function(){
    scheduleModel.set({"searchterm": $("#searchtextbox").val() ? $("#searchtextbox").val() : ""});
    //this.model.set({"page": 1});
    scheduleModel.set({"page": 1});
    scheduleListView.handlePageChange(this);
  });

  $('#searchtextbox').on('keyup', function(e) {
    if(e.keyCode == 13) {
      scheduleModel.set({"searchterm": $("#searchtextbox").val() ? $("#searchtextbox").val() : ""});
      scheduleModel.set({"page": 1});
      scheduleListView.handlePageChange(this);
    }
  });

  // 批量告警页面
  batchSetSlaView = new azkaban.BatchSetSlaView({
    el: $('#schedule-view'),
    model: scheduleModel
  });

  // 设置批量告警对话框
  $("#batch-setSla-btn").click(function () {
    var ids = batchSetSlaView.model.get('allScheduleIdList');
    if (ids) {
      $('#batch-sla-options').modal();
    } else {
      alert(wtssI18n.view.scheduleIsNotExist);
    }

  });

  // 设置批量告警
  $("#batch-set-sla-btn").click(function () {
    $('#batch-set-sla-valid-modal').show();
  });
  $("#batch-set-sla-valid-btn").click(function () {
    var batchSetSla = batchSetSlaView.handleBatchSetSla();
    $('#batch-set-sla-valid-modal').hide();
    if (batchSetSla === undefined) {
      setTimeout(function () {
        scheduleListView.handlePageChange();
      }, 1000);
    }
  });

  $("#batch-set-sla-cancel-btn").click(function () {
    $('#batch-set-sla-valid-modal').hide();
  });

  $("#batch-set-sla-close-btn").click(function () {
    $('#batch-set-sla-valid-modal').hide();
  });

  // 工作流超时告警规则设置-新增一条
  $("#batch-add-btn").click(function () {
    batchSetSlaView.handleBatchAddRow();
  });

  // 工作流事件告警规则设置-新增一条
  $("#batch-finish-add-btn").click(function () {
    batchSetSlaView.handleBatchFinishAddRow();
  });

  // 工作流超时告警规则设置-删除一条
  $("#batchFlowRulesTbl").on('click', '.btn-danger-type1', function () {
    var row = this.parentElement.parentElement.parentElement;
    $(row).remove();
  })

  // 工作流事件告警规则设置-删除一条
  $("#batchFinishRulesTbl").on('click', '.btn-danger-type2', function () {
    var row = this.parentElement.parentElement.parentElement;
    $(row).remove();
  })


  scheduleShowArgsView = new azkaban.ScheduleShowArgsView({
    el: $('#schedule-view'),
    model: scheduleModel
  });

  var urlSearch = window.location.search;
  if(urlSearch.indexOf("search") != -1){
    scheduleModel.set({"search": true});
  }

  var hash = window.location.hash;
  if ("#page" == hash.substring(0, "#page".length)) {
    var arr = hash.split("#");
    var page;
    //var pageSize;
    if(true == scheduleModel.get("search") && 1 == scheduleModel.get("page")){
      page = 1;
    }else{
      page = arr[1].substring("#page".length-1, arr[1].length);
    }
    var pageSize = arr[2].substring("#pageSize".length-1, arr[2].length);

    $("#pageSizeSelect").val(pageSize);

    console.log("page " + page);
    scheduleModel.set({
      "page": parseInt(page),
      "pageSize": parseInt(pageSize),
    });
  }else{
    scheduleModel.set({"page": 1});
  }

  scheduleModel.trigger("change:view");


});

//显示参数按钮
var scheduleShowArgsView;
azkaban.ScheduleShowArgsView = Backbone.View.extend({
  events: {
    "click .btn-info": "handleShowArgs"
  },

  initialize: function (settings) {
  },

  handleShowArgs: function (evt) {
    console.log("Show Args");
    $('#executionOptions-pre').text("");
    $('#executionOptions-modal').modal();
    var index = parseInt(evt.currentTarget.name);
    $('#executionOptions-pre').text(JSON.stringify(scheduleModel.get("scheduleList")[index].executionOptions, null, 4));
  },

  render: function () {
  }
});

var batchSetSlaView;
azkaban.BatchSetSlaView = Backbone.View.extend({
  events: {
  },

  //关闭SLA配置页面时的操作
  handleSlaCancel: function () {
    console.log("Clicked cancel button");
    var scheduleURL = "/schedule";
    //清空SLA定时告警配置选项
    if (document.getElementById("batchFlowRulesTbl")) {
      var tFlowRules = document.getElementById("batchFlowRulesTbl").tBodies[0];
      var rows = tFlowRules.rows;
      var rowLength = rows.length
      for (var i = 0; i < rowLength - 1; i++) {
        tFlowRules.deleteRow(0);
      }
    }

    if (document.getElementById("batchFinishRulesTbl")) {
      //清空成功失败告警配置选项
      var tFinishRules = document.getElementById("batchFinishRulesTbl").tBodies[0];
      var fRows = tFinishRules.rows;
      var fRowLength = fRows.length
      for (var i = 0; i < fRowLength - 1; i++) {
        tFinishRules.deleteRow(0);
      }
    }
    $('#batch-add-btn').attr('disabled', false);
  },

  initialize: function (settings) {
    this.getCurrentScheduleAllFlowSetSla();
    $('#batch-sla-options').on('hidden.bs.modal', function () {
      batchSetSlaView.handleSlaCancel();
    });
  },

  getCurrentScheduleAllFlowSetSla: function (evt) {
    var requestURL = "/schedule?ajax=fetchAllScheduleFlowInfo";

    var model = this.model;
    $.ajax({
      url: requestURL,
      type: "get",
      async: false,
      dataType: "json",
      success: function (data) {
        model.set({
          "currentFlowNameList": data.scheduleFlowNameList,
          "allScheduleIdList": data.scheduleIdList
        });
      }
    });
  },

  handleBatchSetSla: function (evt) {
    var scheduleURL = "/schedule";
    var allScheduleIdList = this.model.get("allScheduleIdList").join(',');
    var departmentSlaInform;
    if ($("#batch-sla-via-department").is(":checked")) {
      console.log("batch-sla-via-department set")
      departmentSlaInform = "true";
    } else {
      console.log("batch-sla-via-department unset")
      departmentSlaInform = "false";
    }

    var batchSlaEmails = $('#batchSlaEmails').val();
    //工作流超时告警规则设置
    var settings = {};
    var timeoutScheduleIdList = {};
    var eventScheduleIdList = {};
    var tFlowRules = document.getElementById("batchFlowRulesTbl").tBodies[0];
    for (var row = 0; row < tFlowRules.rows.length - 1; row++) {
      var rFlowRule = tFlowRules.rows[row];
      var id = rFlowRule.cells[0].firstChild.value;
      var rule = rFlowRule.cells[1].firstChild.value;
      var duration = rFlowRule.cells[2].firstChild.value;
      var level = rFlowRule.cells[3].firstChild.value;
      var email = rFlowRule.cells[4].firstChild.checked;
      var kill = rFlowRule.cells[5].firstChild.checked;
      settings[row] = id + "," + rule + "," + duration + "," + level + "," + email + "," + kill;
      // 设置超时告警选中的scheduleId

      timeoutScheduleIdList[row] = rFlowRule.innerText.replace(/[\r\n\t]/g, "").replace("Delete", "");

      if (duration == "") {
        alert(timeoutScheduleIdList[row] + ": " + wtssI18n.view.timeoutAlarmTime);
        return false;
      }

      if (email == false && kill == false) {
        alert(timeoutScheduleIdList[row] + ": " + wtssI18n.view.timeoutAlarmRuleLessOne);
        return false;
      }

    }
    //工作流事件告警规则设置
    var finishSettings = {};
    var tFinishRules = document.getElementById("batchFinishRulesTbl").tBodies[0];
    for (var row = 0; row < tFinishRules.rows.length - 1; row++) {
      var tFinishRule = tFinishRules.rows[row];
      var id = tFinishRule.cells[0].firstChild.value;
      var rule = tFinishRule.cells[1].firstChild.value;
      var level = tFinishRule.cells[2].firstChild.value;
      finishSettings[row] = id + "," + rule + "," + level;
      // 设置事件告警选中的scheduleId
      eventScheduleIdList[row] = tFinishRule.innerText.replace(/[\r\n\t]/g, "").replace("Delete", "");
    }

    //检查是否有重复的规则
    if (this.checkSlaRepeatRule(settings)) {
      alert(wtssI18n.view.timeoutAlarmFormat);
      return false;
    }

    //检查是否有重复的规则
    if (this.checkFinishRepeatRule(finishSettings)) {
      alert(wtssI18n.view.eventAlarmFormat);
      return false;
    }

    var batchSlaData = {
      timeoutScheduleIdList: timeoutScheduleIdList,
      eventScheduleIdList: eventScheduleIdList,
      allScheduleIdList: allScheduleIdList,
      ajax: "batchSetSla",
      batchSlaEmails: batchSlaEmails,
      departmentSlaInform: departmentSlaInform,
      settings: settings,
      finishSettings: finishSettings,
    };

    var successHandler = function (data) {
      if (data.error) {
        alert(data.error);
      } else {
        tFlowRules.length = 0;
        // 隐藏告警设置对话框, 触发变更
        $('#batch-sla-options').modal("hide");
        scheduleListView.handlePageChange();
      }
    };
    $.post(scheduleURL, batchSlaData, successHandler, "json");
  },

  checkSlaRepeatRule: function (data) {
    var new_arr = [];
    var oldlength = 0;
    for (var i in data) {
      oldlength++;
      var items = data[i].substring(0, find(data[i], ",", 1));;
      //判断元素是否存在于new_arr中，如果不存在则插入到new_arr的最后
      if ($.inArray(items, new_arr) == -1) {
        new_arr.push(items);
      }
    }
    if (new_arr.length < oldlength) {
      return true;
    }
  },

  checkFinishRepeatRule: function (data) {
    var new_arr = [];
    var oldlength = 0;
    for (var i in data) {
      oldlength++;
      var items = data[i];
      //判断元素是否存在于new_arr中，如果不存在则插入到new_arr的最后
      if ($.inArray(items, new_arr) == -1) {
        new_arr.push(items);
      }
    }
    if (new_arr.length < oldlength) {
      return true;
    }
  },


  // 工作流超时告警规则设置-新增一行
  handleBatchAddRow: function (evt) {
    var flowNameList = this.model.get("currentFlowNameList");
    var ruleBoxOptions = ["SUCCESS", "FINISH"];

    var tFlowRules = document.getElementById("batchFlowRulesTbl").tBodies[0];
    var rFlowRule = tFlowRules.insertRow(tFlowRules.rows.length - 1);
    var retryTr = rFlowRule.rowIndex;
    if (retryTr == flowNameList.length) {
      $('#batch-add-btn').attr('disabled', 'disabled');
    }

    //设置工作流
    var cId = rFlowRule.insertCell(-1);
    var idSelect = "<select class='schedule-select2-search'>"
    for (var i = 0; i < flowNameList.length; i++) {
      idSelect += "<option value=\"" + i + "\" title=\"" + flowNameList[i] + "\">" + flowNameList[i] + "</option>"
    }

    idSelect += "</select>"
    cId.innerHTML = idSelect;
    $('.schedule-select2-search').select2();
    //设置告警规则
    var cRule = rFlowRule.insertCell(-1);
    var ruleSelect = document.createElement("select");
    ruleSelect.setAttribute("class", "form-control");
    for (var i in ruleBoxOptions) {
      ruleSelect.options[i] = new Option(ruleBoxOptions[i], ruleBoxOptions[i]);
    }
    cRule.appendChild(ruleSelect);
    //设置超时时间
    var cDuration = rFlowRule.insertCell(-1);
    var duration = document.createElement("input");
    duration.type = "text";
    duration.setAttribute("class", "durationpick form-control");
    cDuration.appendChild(duration);

    //设置告警级别
    var cLevel = rFlowRule.insertCell(-1);
    var levelSelect = document.createElement("select");
    levelSelect.setAttribute("class", "form-control");
    $(levelSelect).append("<option value='INFO'>INFO</option>");
    $(levelSelect).append("<option value='WARNING'>WARNING</option>");
    $(levelSelect).append("<option value='MINOR'>MINOR</option>");
    $(levelSelect).append("<option value='MAJOR'>MAJOR</option>");
    $(levelSelect).append("<option value='CRITICAL'>CRITICAL</option>");
    $(levelSelect).append("<option value='CLEAR'>CLEAR</option>");
    cLevel.appendChild(levelSelect);
    //设置发送邮件
    var cEmail = rFlowRule.insertCell(-1);
    var emailCheck = document.createElement("input");
    emailCheck.type = "checkbox";
    cEmail.appendChild(emailCheck);
    //设置终止工作流/任务
    var cKill = rFlowRule.insertCell(-1);
    var killCheck = document.createElement("input");
    killCheck.type = "checkbox";
    cKill.appendChild(killCheck);

    $('.durationpick').datetimepicker({
      format: 'HH:mm'
    });

    //删除按钮
    var cDelete = rFlowRule.insertCell(-1);
    var remove = document.createElement("div");
    $(remove).addClass("center-block").addClass('remove-timeout-btn');
    var removeBtn = document.createElement("button");
    $(removeBtn).attr('type', 'button');
    $(removeBtn).addClass('btn').addClass('btn-sm').addClass('btn-danger-type1');
    $(removeBtn).text('Delete');
    $(remove).append(removeBtn);
    cDelete.appendChild(remove);

    return rFlowRule;
  },


  //工作流事件告警规则设置-新增一行
  handleBatchFinishAddRow: function (evt) {
    var flowNameList = this.model.get("currentFlowNameList");
    var finshRuleBoxOptions = ["FAILURE EMAILS", "SUCCESS EMAILS", "FINISH EMAILS"];

    var ruleTr = $("#batchFinishRulesTbl tr").length - 1;

    var jslength = 0;

    for (var i = 0; i < flowNameList.length; i++) {
      jslength++;
    }

    if (jslength * finshRuleBoxOptions.length < ruleTr) {
      alert(wtssI18n.view.alarmRulesFormat);
      return;
    }

    var tFlowRules = document.getElementById("batchFinishRulesTbl").tBodies[0];
    var rFlowRule = tFlowRules.insertRow(tFlowRules.rows.length - 1);

    //alert($("#FinishRulesTbl tr").length);

    //设置 flow
    var cId = rFlowRule.insertCell(-1);
    var idSelect = "<select class='schedule-select2-search'>"
    for (var i = 0; i < flowNameList.length; i++) {
      idSelect += "<option value=\"" + i + "\" title=\"" + flowNameList[i] + "\">" + flowNameList[i] + "</option>"
    }
    idSelect += "</select>"

    cId.innerHTML = idSelect;
    $('.schedule-select2-search').select2();
    //设置规则选项
    var cRule = rFlowRule.insertCell(-1);
    var ruleSelect = document.createElement("select");
    ruleSelect.setAttribute("class", "form-control");
    for (var i in finshRuleBoxOptions) {
      ruleSelect.options[i] = new Option(finshRuleBoxOptions[i], finshRuleBoxOptions[i]);
    }
    cRule.appendChild(ruleSelect);

    //设置告警级别
    var cLevel = rFlowRule.insertCell(-1);
    var levelSelect = document.createElement("select");
    levelSelect.setAttribute("class", "form-control");
    $(levelSelect).append("<option value='INFO'>INFO</option>");
    $(levelSelect).append("<option value='WARNING'>WARNING</option>");
    $(levelSelect).append("<option value='MINOR'>MINOR</option>");
    $(levelSelect).append("<option value='MAJOR'>MAJOR</option>");
    $(levelSelect).append("<option value='CRITICAL'>CRITICAL</option>");
    $(levelSelect).append("<option value='CLEAR'>CLEAR</option>");
    cLevel.appendChild(levelSelect);

    //删除按钮
    var cDelete = rFlowRule.insertCell(-1);
    var remove = document.createElement("div");
    $(remove).addClass("center-block").addClass('remove-btn');
    var removeBtn = document.createElement("button");
    $(removeBtn).attr('type', 'button');
    $(removeBtn).addClass('btn').addClass('btn-sm').addClass('btn-danger-type2');
    $(removeBtn).text('Delete');
    $(remove).append(removeBtn);
    cDelete.appendChild(remove);

    return rFlowRule;
  },

  handleEditColumn: function (evt) {
    var curTarget = evt.currentTarget;
    if (this.editingTarget != curTarget) {
      this.closeEditingTarget();

      var text = $(curTarget).children(".spanValue").text();
      $(curTarget).empty();

      var input = document.createElement("input");
      $(input).attr("type", "text");
      $(input).css("width", "100%");
      $(input).val(text);
      $(curTarget).addClass("editing");
      $(curTarget).append(input);
      $(input).focus();
      this.editingTarget = curTarget;
    }
  },

  closeEditingTarget: function (evt) {
  },

  render: function () {
  }
});

function find (str, cha, num) {
  var x = str.indexOf(cha);
  for (var i = 0; i < num; i++) {
    x = str.indexOf(cha, x + 1);
  }
  return x;
}

var tableSorterView;

//用于保存浏览数据，切换页面也能返回之前的浏览进度。
var scheduleModel;
azkaban.ScheduleModel = Backbone.Model.extend({});

//项目列表页面
var scheduleListView;
azkaban.ScheduleListView = Backbone.View.extend({
  events: {
    "click #projectPageSelection li": "handleScheduleChangePageSelection",
    "change #pageSizeSelect": "handleSchedulePageSizeSelection",
    "click #pageNumJump": "handleSchedulePageNumJump",
  },

  initialize: function(settings) {
    this.model.bind('change:view', this.handleChangeView, this);
    this.model.bind('render', this.render, this);
    var pageNum = $("#pageSizeSelect").val();
    this.model.set({page: 1, pageSize: pageNum});
    var searchText = $("#searchtextbox").val();
    if(this.model.get("search")){
      this.model.set({searchterm: searchText});
    }
    this.model.bind('change:page', this.handlePageChange, this);
  },

  render: function(evt) {
    console.log("render");
    // Render page selections
    var scheduleTbody = $("#schedules-tbody");
    scheduleTbody.empty();

    var scheduleList = this.model.get("scheduleList") || [];
    var schConfig = this.model.get("schConfig");
    var slaSetting = this.model.get("slaSetting");
    var deleteSch = this.model.get("deleteSch");
    var showParam = this.model.get("showParam");


    for (var i = 0; i < scheduleList.length; ++i) {
      var row = document.createElement("tr");

      //组装数字行
      var tdNum = document.createElement("td");
      $(tdNum).text(i + 1);
      $(tdNum).attr("class","tb-name");
      row.appendChild(tdNum);

      //组装调度id
      var tdScheduleId = document.createElement("td");
      $(tdScheduleId).text(scheduleList[i].scheduleId);
      row.appendChild(tdScheduleId);

      //组装Flow行
      var tdFlow = document.createElement("td");
      var flowA = document.createElement("a");
      $(flowA).attr("href", contextURL + "/manager?project=" + scheduleList[i].projectName + "&flow=" + scheduleList[i].flowName);
      $(flowA).text(scheduleList[i].flowName);
      $(flowA).attr("style","width: 350px; word-break:break-all;");
      tdFlow.appendChild(flowA);
      row.appendChild(tdFlow);

      //组装Project行
      var tdProject = document.createElement("td");
      var projectA = document.createElement("a");
      $(projectA).attr("href", contextURL + "/manager?project=" + scheduleList[i].projectName);
      $(projectA).text(scheduleList[i].projectName);
      $(projectA).attr("style","width: 350px; word-break:break-all;");
      tdProject.appendChild(projectA);
      row.appendChild(tdProject);

      //组装用户行
      var tdUser = document.createElement("td");
      $(tdUser).text(scheduleList[i].submitUser);
      row.appendChild(tdUser);

      //组装firstSchedTime
      var tdFirstSchedTime = document.createElement("td");
      $(tdFirstSchedTime).text(getProjectModifyDateFormat(new Date(scheduleList[i].firstSchedTime)));
      row.appendChild(tdFirstSchedTime);

      //组装nextExecTime
      var tdNextExecTime = document.createElement("td");
      $(tdNextExecTime).text(getProjectModifyDateFormat(new Date(scheduleList[i].nextExecTime)));
      row.appendChild(tdNextExecTime);

      //组装cronExpression
      var tdCronExpression = document.createElement("td");
      $(tdCronExpression).text(scheduleList[i].cronExpression ? scheduleList[i].cronExpression : wtssI18n.view.notApplicable);
      row.appendChild(tdCronExpression);

      //组装 是否调度有效
      var scheduleActive = document.createElement("td");
      var currentSchActiveFlag = scheduleList[i].otherOption.activeFlag;
      $(scheduleActive).text(currentSchActiveFlag);
      row.appendChild(scheduleActive);

      //组装 是否是有效工作流
      var validFlow = document.createElement("td");
      $(validFlow).text(scheduleList[i].otherOption.validFlow ? true : false);
      row.appendChild(validFlow);

      //组装 显示参数
      var tdShowArgs = document.createElement("td");
      var showArgsBtn = document.createElement("button");
      $(showArgsBtn).attr("class", "btn btn-sm btn-info");
      $(showArgsBtn).attr("type", "button");
      $(showArgsBtn).attr("data-toggle", "modal");
      $(showArgsBtn).attr("name", i);
      $(showArgsBtn).text(showParam);
      tdShowArgs.appendChild(showArgsBtn);
      row.appendChild(tdShowArgs);

      //组装 是否设置告警
      var tdSlaOptions = document.createElement("td");
      var slaConfFlag = false;
      if ((scheduleList[i].slaOptions) && (scheduleList[i].slaOptions.length != 0)) {
        slaConfFlag = true;
      }
      $(tdSlaOptions).text(slaConfFlag);
      row.appendChild(tdSlaOptions);

      //组装 删除定时调度按钮
      var tdRemoveSchedBtn = document.createElement("td");
      var removeSchedBtn = document.createElement("button");
      $(removeSchedBtn).attr("class", "btn btn-sm btn-danger").attr("type", "button").attr("name", scheduleList[i].scheduleId + "#"+ scheduleList[i].projectName +"#" + scheduleList[i].flowName);
      $(removeSchedBtn).text(deleteSch);
      tdRemoveSchedBtn.appendChild(removeSchedBtn);
      row.appendChild(tdRemoveSchedBtn);

      //组装 设置告警
      var tdAddSlaBtn = document.createElement("td");
      var addSlaBtn = document.createElement("button");
      $(addSlaBtn).attr("class", "btn btn-sm btn-primary").attr("type", "button").attr("onclick","slaView.initFromSched(" + scheduleList[i].scheduleId +",'" + scheduleList[i].projectName + "','" + scheduleList[i].flowName + "')");
      $(addSlaBtn).text(slaSetting);
      tdAddSlaBtn.appendChild(addSlaBtn);
      row.appendChild(tdAddSlaBtn);

      //组装 调度配置
      var tdEditSchedBtn = document.createElement("td");
      var editSchedBtn = document.createElement("button");
      $(editSchedBtn).attr("class", "btn btn-success").attr("type", "button")
      .attr("onclick", "editScheduleClick(" + scheduleList[i].scheduleId + ",'" + scheduleList[i].projectName + "','" + scheduleList[i].flowName + "','" + scheduleList[i].cronExpression + "')");
      $(editSchedBtn).text(schConfig);
      tdEditSchedBtn.appendChild(editSchedBtn);
      row.appendChild(tdEditSchedBtn);

      //组装 开启调度/关闭调度
      var tdSwitchSchedBtn = document.createElement("td");
      var switchSchedBtn = document.createElement("button");
      $(switchSchedBtn).attr("class", "btn btn-success").attr("type", "button")
          .attr("onclick", "switchScheduleClick(" + i + "," + scheduleList[i].scheduleId + ",'" + scheduleList[i].projectName + "','" + scheduleList[i].flowName + "','" + scheduleList[i].cronExpression + "')");
      if (currentSchActiveFlag) {
        $(switchSchedBtn).text(wtssI18n.view.inactiveSchedule);
      } else {
        $(switchSchedBtn).text(wtssI18n.view.activeSchedule);
      }
      tdSwitchSchedBtn.appendChild(switchSchedBtn);
      row.appendChild(tdSwitchSchedBtn);

      scheduleTbody.append(row);

      this.renderPagination(evt);

      $("#scheduledFlowsTbl").trigger("update");
      $("#scheduledFlowsTbl").trigger("sorton", "");
    }
  },
  //组装分页组件
  renderPagination: function(evt) {
    var total = this.model.get("total");
    total = total? total : 1;
    var pageSize = this.model.get("pageSize");
    var numPages = Math.ceil(total / pageSize);

    this.model.set({"numPages": numPages});
    var page = this.model.get("page");

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

    $("#page"+selectionPosition).addClass("active");
    $("#page"+selectionPosition)[0].page = page;
    var selecta = $("#page" + selectionPosition + " a");
    selecta.text(page);
    selecta.attr("href", "#page" + page + "#pageSize" + pageSize);

    for (var j = 0; j < 5; ++j) {
      var realPage = startPage + j;
      var elementId = "#page" + (j+1);
      if($(elementId).hasClass("disabled")){
        $(elementId)[0].page = realPage;
        var a = $(elementId + " a");
        a.text(realPage);
        a.attr("href", "javascript:void(0);");
      }else{
        $(elementId)[0].page = realPage;
        var a = $(elementId + " a");
        a.text(realPage);
        a.attr("href", "#page" + realPage + "#pageSize" + pageSize);
      }
    }
  },

  handleScheduleChangePageSelection: function(evt) {
    if ($(evt.currentTarget).hasClass("disabled")) {
      return;
    }
    var page = evt.currentTarget.page;
    this.model.set({"page": page});
    var pageSize = $("#pageSizeSelect").val();
    this.model.set({"pageSize": pageSize});
  },

  handleChangeView: function(evt) {
    if (this.init) {
      return;
    }
    console.log("init");
    this.handlePageChange(evt);
    this.init = true;
  },

  handlePageChange: function(evt) {
    var start = this.model.get("page");
    var pageSize = this.model.get("pageSize");
    var requestURL = contextURL + "/schedule";
    var searchText = this.model.get("searchterm");

    var model = this.model;
    var requestData = {
      "ajax": "ajaxFetchAllSchedules",
      "page": start,
      "size": pageSize,
      "pageNum": this.model.get("page"),
      "searchterm": searchText,
    };
    if (searchText) {
      requestData.search = "true"
    }
    requestData.time = new Date().getTime()

    var successHandler = function(data) {
      model.set({
        "scheduleList": data.schedules,
        "schConfig": data.schConfig,
        "slaSetting": data.slaSetting,
        "deleteSch": data.deleteSch,
        "showParam": data.showParam,

        "total": data.total
      });
      model.trigger("render");
    };
    $.get(requestURL, requestData, successHandler, "json");
  },

  handleSchedulePageSizeSelection: function(evt) {
    var pageSize = evt.currentTarget.value;
    this.model.set({"pageSize": pageSize});
    this.model.set({"page": 1});

    this.init = false;

    var search = window.location.search
    var arr = search.split("#");
    var pageURL = arr[0] + "#page1";
    var pageSizeURL = pageURL + "#pageSzie" + pageSize;

    var scheduleURL = contextURL + "/schedule"

    var pageSizeFirestURL = scheduleURL + pageSizeURL;
    //防止XSS DOM攻击,类似:javascript:alert(1);//http://www.qq.com,所以对URL进行正则校验
    var reg = /^(http|ftp|https):\/\/[\w\-_]+(\.[\w\-_]+)+([\w\-\.,@?^=%&:/~\+#]*[\w\-\@?^=%&/~\+#])?/;
    if (reg.test(pageSizeFirestURL)) {
        window.location = pageSizeFirestURL;
    }
    scheduleModel.trigger("change:view");
  },

  handleSchedulePageNumJump: function (evt) {

    var pageNum = $("#pageNumInput").val();

    if(pageNum <= 0){
      //alert("页数必须大于1!!!");
      return;
    }

    // var total = this.model.get("total");
    //   total = total? total : 1;
    //   var pageSize = this.model.get("pageSize");
    //   var numPages = Math.ceil(total / pageSize);


    if(pageNum > this.model.get("numPages")){
      pageNum = this.model.get("numPages");
    }

    this.model.set({"page": pageNum});
    this.init = false;
    scheduleModel.trigger("change:view");
  },

});
