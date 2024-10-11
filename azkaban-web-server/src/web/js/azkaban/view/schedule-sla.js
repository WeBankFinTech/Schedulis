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

azkaban.ChangeSlaView = Backbone.View.extend({
  events: {
    "click": "closeEditingTarget",
    "click #set-sla-btn": "handleSetSla",
    "click #remove-sla-btn": "handleRemoveSla",
    "click #add-btn": "handleAddRow",
    "click #finish-add-btn": "handleFinishAddRow",
    "click table .remove-btn": "handleRemoveColumn",
    "click table .remove-timeout-btn": "handleRemoveColumn",
  },

  initialize: function (setting) {
    $('#sla-options').on('hidden.bs.modal', function () {
      slaView.handleSlaCancel();
    });
  },
  //关闭SLA配置页面时的操作
  handleSlaCancel: function () {
    console.log("Clicked cancel button");
    var scheduleURL = contextURL + "/schedule";
    //清空SLA定时告警配置选项
    var tFlowRules = document.getElementById("flowRulesTbl").tBodies[0];
    var rows = tFlowRules.rows;
    var rowLength = rows.length
    for (var i = 0; i < rowLength - 1; i++) {
      tFlowRules.deleteRow(0);
    }
    //清空成功失败告警配置选项
    var tFinishRules = document.getElementById("FinishRulesTbl").tBodies[0];
    var fRows = tFinishRules.rows;
    var fRowLength = fRows.length
    for (var i = 0; i < fRowLength - 1; i++) {
      tFinishRules.deleteRow(0);
    }
  },

  // 定时调度页面, 定时调度工作流列表, 对显示的调度任务点击设置告警
  initFromSched: function (scheduleId, projectName, flowName) {

    var self = this;

    var requestURL = contextURL + "/manager?ajax=checkUserSetScheduleAlertPermission&project=" + projectName;
    $.ajax({
      url: requestURL,
      type: "get",
      async: false,
      dataType: "json",
      success: function (data) {
        if (data["setAlertFlag"] == 1) {
          console.log("have permission, click set alert config.");
          self.scheduleId = scheduleId;
          var scheduleURL = contextURL + "/schedule"
          self.scheduleURL = scheduleURL;

          var indexToName = {};
          var nameToIndex = {};
          var indexToText = {};
          self.indexToName = indexToName;
          self.nameToIndex = nameToIndex;
          self.indexToText = indexToText;

          var ruleBoxOptions = ["SUCCESS", "FINISH"];
          self.ruleBoxOptions = ruleBoxOptions;

          var finshRuleBoxOptions = ["FAILURE EMAILS", "SUCCESS EMAILS", "FINISH EMAILS"];
          self.finshRuleBoxOptions = finshRuleBoxOptions;

          var fetchScheduleData = {
            "scheduleId": self.scheduleId,
            "ajax": "slaInfo"
          };

          var successHandler = function (data) {
            if (data.error) {
              $('#sla-options').modal("hide");
              alert(data.error);
              return;
            }

            // 先清空页面缓存
            $('#slaEmails').val("");
            if (data.slaEmails) {
              $('#slaEmails').val(data.slaEmails.join());
            }

            $('#sla-via-department').prop('checked', false);
            if (data.departmentSlaInform == "true"){
              $('#sla-via-department').prop('checked', true);
            }

            var allJobNames = data.allJobNames;

            indexToName[0] = "";
            nameToIndex[flowName] = 0;
            indexToText[0] = "flow " + flowName;
            for (var i = 1; i <= allJobNames.length; i++) {
              indexToName[i] = allJobNames[i - 1];
              nameToIndex[allJobNames[i - 1]] = i;
              indexToText[i] = "job " + allJobNames[i - 1];
            }

            // populate with existing settings 填充现有的设置
            if (data.settings) {
              var tFlowRules = document.getElementById("flowRulesTbl").tBodies[0];
              for (var setting in data.settings) {
                var rFlowRule = tFlowRules.insertRow(0);

                var cId = rFlowRule.insertCell(-1);
                var idSelect = document.createElement("select");
                idSelect.setAttribute("class", "schedule-select2-search");
                for (var i in indexToName) {
                  idSelect.options[i] = new Option(indexToText[i], indexToName[i]);
                  if (data.settings[setting].id == indexToName[i]) {
                    idSelect.options[i].selected = true;
                  }
                }
                cId.appendChild(idSelect);
                $('.schedule-select2-search').select2();
                var cRule = rFlowRule.insertCell(-1);
                var ruleSelect = document.createElement("select");
                ruleSelect.setAttribute("class", "form-control");
                for (var i in ruleBoxOptions) {
                  ruleSelect.options[i] = new Option(ruleBoxOptions[i],
                    ruleBoxOptions[i]);
                  if (data.settings[setting].rule == ruleBoxOptions[i]) {
                    ruleSelect.options[i].selected = true;
                  }
                }
                cRule.appendChild(ruleSelect);

                var cDuration = rFlowRule.insertCell(-1);
                var duration = document.createElement("input");
                duration.type = "text";
                duration.setAttribute("class", "form-control durationpick");
                duration.setAttribute("onkeyup", "this.value=this.value.replace(/[^\:\d]/g,'')");//只能输入数字和冒号
                var rawMinutes = data.settings[setting].duration;
                var intMinutes = rawMinutes.substring(0, rawMinutes.length - 1);
                var minutes = parseInt(intMinutes);
                var hours = Math.floor(minutes / 60);
                minutes = minutes % 60;
                duration.value = hours + ":" + minutes;
                cDuration.appendChild(duration);

                var cLevel = rFlowRule.insertCell(-1);
                var levelSelect = document.createElement("select");
                levelSelect.setAttribute("class", "form-control");
                $(levelSelect).append("<option value='INFO'>INFO</option>");
                $(levelSelect).append("<option value='WARNING'>WARNING</option>");
                $(levelSelect).append("<option value='MINOR'>MINOR</option>");
                $(levelSelect).append("<option value='MAJOR'>MAJOR</option>");
                $(levelSelect).append("<option value='CRITICAL'>CRITICAL</option>");
                $(levelSelect).append("<option value='CLEAR'>CLEAR</option>");
                $(levelSelect).val(data.settings[setting].level);
                cLevel.appendChild(levelSelect);

                var cEmail = rFlowRule.insertCell(-1);
                var emailCheck = document.createElement("input");
                emailCheck.type = "checkbox";
                for (var act in data.settings[setting].actions) {
                  if (data.settings[setting].actions[act] == "EMAIL") {
                    emailCheck.checked = true;
                  }
                }
                cEmail.appendChild(emailCheck);

                var cKill = rFlowRule.insertCell(-1);
                var killCheck = document.createElement("input");
                killCheck.type = "checkbox";
                for (var act in data.settings[setting].actions) {
                  if (data.settings[setting].actions[act] == "KILL") {
                    killCheck.checked = true;
                  }
                }
                cKill.appendChild(killCheck);
                //删除按钮
                var cDelete = rFlowRule.insertCell(-1);
                var remove = document.createElement("div");
                $(remove).addClass("center-block").addClass('remove-timeout-btn');
                var removeBtn = document.createElement("button");
                $(removeBtn).attr('type', 'button');
                $(removeBtn).addClass('btn').addClass('btn-sm').addClass('btn-danger');
                $(removeBtn).text('Delete');
                $(remove).append(removeBtn);
                cDelete.appendChild(remove);
                $('.durationpick').datetimepicker({
                  format: 'HH:mm'
                });
              }
            }
            //加载已有的成功失败告警设置
            if (data.finishSettings) {
              var finishData = data.finishSettings
              var tFlowRules = document.getElementById("FinishRulesTbl").tBodies[0];
              for (var setting in finishData) {
                var rFlowRule = tFlowRules.insertRow(0);

                var cId = rFlowRule.insertCell(-1);
                // var idSelect = document.createElement("select");
                // idSelect.setAttribute("class", "form-control");
                var idSelect = "<select class='schedule-select2-search' style='width:200px;'>"
                for (var i in indexToName) {
                  // idSelect.options[i] = new Option(indexToText[i], indexToName[i]);
                  var selected = ''
                  if (finishData[setting].id == indexToName[i]) {
                    selected = 'selected';
                  }
                  if (i === '0') {
                    idSelect += "<option value=\"" + indexToName[0] + "\" title=\"" + indexToText[0] + "\">" + indexToText[0] + "</option>"
                  } else {
                    var name = indexToName[i].substring(indexToName[i].lastIndexOf(":") + 1);
                    idSelect += "<option value=\"" + indexToName[i] + "\" title=\"" + indexToText[i] + "\"" + selected + ">job " + name + "</option>"
                  }
                }
                idSelect += "</select>"
                cId.innerHTML = idSelect
                $('.schedule-select2-search').select2();
                var cRule = rFlowRule.insertCell(-1);
                var ruleSelect = document.createElement("select");
                ruleSelect.setAttribute("class", "form-control");
                for (var i in finshRuleBoxOptions) {
                  ruleSelect.options[i] = new Option(finshRuleBoxOptions[i], finshRuleBoxOptions[i]);
                  if (finishData[setting].rule == finshRuleBoxOptions[i]) {
                    ruleSelect.options[i].selected = true;
                  }
                }
                cRule.appendChild(ruleSelect);

                var cLevel = rFlowRule.insertCell(-1);
                var levelSelect = document.createElement("select");
                levelSelect.setAttribute("class", "form-control");
                $(levelSelect).append("<option value='INFO'>INFO</option>");
                $(levelSelect).append("<option value='WARNING'>WARNING</option>");
                $(levelSelect).append("<option value='MINOR'>MINOR</option>");
                $(levelSelect).append("<option value='MAJOR'>MAJOR</option>");
                $(levelSelect).append("<option value='CRITICAL'>CRITICAL</option>");
                $(levelSelect).append("<option value='CLEAR'>CLEAR</option>");
                $(levelSelect).val(data.finishSettings[setting].level);
                cLevel.appendChild(levelSelect);

                //删除按钮
                var cDelete = rFlowRule.insertCell(-1);
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

            $('.durationpick').datetimepicker({
              format: 'HH:mm'
            });
          };

          $.get(self.scheduleURL, fetchScheduleData, successHandler, "json");

          $('#sla-options').modal();

          //this.schedFlowOptions = sched.flowOptions
          console.log("Loaded schedule info. Ready to set SLA.");

        } else {
          $('#user-operator-schedule-flow-permit-panel').modal();
          $('#title-user-operator-schedule-flow-permit').text(wtssI18n.view.scheduleAlertConfigPermission);
          $('#body-user-operator-schedule-flow-permit').html(wtssI18n.view.noScheAlertConfigPermission);
        }
      }
    });

  },

  handleRemoveSla: function (evt) {
    console.log("Clicked remove sla button");
    var scheduleURL = this.scheduleURL;
    var redirectURL = this.scheduleURL;
    var requestData = {
      "action": "removeSla",
      "scheduleId": this.scheduleId
    };
    var successHandler = function (data) {
      if (data.error) {
        $('#errorMsg').text(data.error)
      }
      else {
        window.location = redirectURL
      }
    };
    $.post(scheduleURL, requestData, successHanlder, "json");
  },

  handleSetSla: function (evt) {

    var departmentSlaInform;
    if($("#sla-via-department").is(":checked")){
      console.log("sla-via-department set")
      departmentSlaInform="true";
    }else{
      console.log("sla-via-department unset")
      departmentSlaInform="false";
    }
    var slaEmails = $('#slaEmails').val();
    //SLA告警设置
    var settings = {};
    var tFlowRules = document.getElementById("flowRulesTbl").tBodies[0];
    for (var row = 0; row < tFlowRules.rows.length - 1; row++) {
      var rFlowRule = tFlowRules.rows[row];
      var id = rFlowRule.cells[0].firstChild.value;
      var rule = rFlowRule.cells[1].firstChild.value;
      var duration = rFlowRule.cells[2].firstChild.value;
      var level = rFlowRule.cells[3].firstChild.value;
      var email = rFlowRule.cells[4].firstChild.checked;
      var kill = rFlowRule.cells[5].firstChild.checked;
      settings[row] = id + "," + rule + "," + duration + "," + level + "," + email + "," + kill;
    }
    //失败成功告警设置
    var finishSettings = {};
    var tFinishRules = document.getElementById("FinishRulesTbl").tBodies[0];
    for (var row = 0; row < tFinishRules.rows.length - 1; row++) {
      var tFinishRule = tFinishRules.rows[row];
      var id = tFinishRule.cells[0].firstChild.value;
      var rule = tFinishRule.cells[1].firstChild.value;
      var level = tFinishRule.cells[2].firstChild.value;
      finishSettings[row] = id + "," + rule + "," + level;
    }

    //检查是否有重复的规则
    if (this.checkSlaRepeatRule(settings)) {
      alert(wtssI18n.view.timeoutAlarmFormat);
      return;
    }

    //检查是否有重复的规则
    if (this.checkFinishRepeatRule(finishSettings)) {
      alert(wtssI18n.view.eventAlarmFormat);
      return;
    }

    var slaData = {
      scheduleId: this.scheduleId,
      ajax: "setSla",
      slaEmails: slaEmails,
      departmentSlaInform:departmentSlaInform,
      settings: settings,
      finishSettings: finishSettings,
    };

    var scheduleURL = this.scheduleURL;
    var successHandler = function (data) {
      if (data.error) {
        alert(data.error);
      }
      else {
        tFlowRules.length = 0;
        // 隐藏告警设置对话框, 触发变更
        $('#sla-options').modal("hide");
        scheduleListView.handlePageChange();
      }
    };
    $.post(scheduleURL, slaData, successHandler, "json");
  },

  handleAddRow: function (evt) {
    var indexToName = this.indexToName;
    var nameToIndex = this.nameToIndex;
    var indexToText = this.indexToText;
    var ruleBoxOptions = this.ruleBoxOptions;

    var tFlowRules = document.getElementById("flowRulesTbl").tBodies[0];
    var rFlowRule = tFlowRules.insertRow(tFlowRules.rows.length - 1);

    var retryTr = rFlowRule.rowIndex;
    if (retryTr == Object.keys(indexToName).length) {
      $('#add-btn').attr('disabled', 'disabled');
    }
    //设置工作流/任务
    var cId = rFlowRule.insertCell(-1);
    var idSelect = "<select class='schedule-select2-search'>"
    for (var i in indexToName) {
      if (i === '0') {
        idSelect += "<option value=\"" + indexToName[0] + "\" title=\"" + indexToText[0] + "\">" + indexToText[0] + "</option>"
      } else {
        var name = indexToName[i].substring(indexToName[i].lastIndexOf(":") + 1);
        idSelect += "<option value=\"" + indexToName[i] + "\" title=\"" + indexToText[i] + "\">job " + name + "</option>"
      }
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
    $(removeBtn).addClass('btn').addClass('btn-sm').addClass('btn-danger');
    $(removeBtn).text('Delete');
    $(remove).append(removeBtn);
    cDelete.appendChild(remove);

    return rFlowRule;
  },
  //flow 执行成功错误告警设置
  handleFinishAddRow: function (evt) {
    var indexToName = this.indexToName;
    var indexToText = this.indexToText;
    var finshRuleBoxOptions = this.finshRuleBoxOptions;

    var ruleTr = $("#FinishRulesTbl tr").length - 1;

    var jslength = 0;

    for (var js2 in indexToName) {
      jslength++;
    }

    if (jslength * finshRuleBoxOptions.length < ruleTr) {
      alert(wtssI18n.view.alarmRulesFormat);
      return;
    }

    var tFlowRules = document.getElementById("FinishRulesTbl").tBodies[0];
    var rFlowRule = tFlowRules.insertRow(tFlowRules.rows.length - 1);

    //alert($("#FinishRulesTbl tr").length);

    //设置 flow 或者 job 名称
    var cId = rFlowRule.insertCell(-1);
    var idSelect = document.createElement("select");
    idSelect.setAttribute("class", "schedule-select2-search");
    idSelect.style.width = "200px"
    for (var i in indexToName) {
      idSelect.options[i] = new Option(indexToText[i], indexToName[i]);
    }
    cId.appendChild(idSelect);
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
    $(removeBtn).addClass('btn').addClass('btn-sm').addClass('btn-danger');
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

  handleRemoveColumn: function (evt) {
    var curTarget = evt.currentTarget;
    // Should be the table
    var row = curTarget.parentElement.parentElement;
    $(row).remove();
  },

  closeEditingTarget: function (evt) {
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
  }
});

function find (str, cha, num) {
  var x = str.indexOf(cha);
  for (var i = 0; i < num; i++) {
    x = str.indexOf(cha, x + 1);
  }
  return x;
}
