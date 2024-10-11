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


$(function () {

  // function initializeHistoryRecover(settings) {
  //   var date = new Date();
  //   $('#datetimebegin').datetimepicker({
  //     format: 'YYYY/MM/DD HH:mm',
  //     maxDate: new Date()
  //   });
  //   $('#datetimeend').datetimepicker({
  //     format: 'YYYY/MM/DD HH:mm',
  //     maxDate: new Date()
  //   });
  //   $('#datetimebegin').on('change.dp', function(e) {
  //     $('#datetimeend').data('DateTimePicker').setStartDate(e.date);
  //   });
  //   $('#datetimeend').on('change.dp', function(e) {
  //     $('#datetimebegin').data('DateTimePicker').setEndDate(e.date);
  //   });
  //   $('#repeat-collection-error-msg').hide();
  // }

  var beginTime = $('#datetimebegin');
  beginTime.blur(function () {
    updateRecoverTimeTopTen();
  });
  var endTime = $('#datetimeend');
  endTime.blur(function () {
    updateRecoverTimeTopTen();
  });
  var monthNum = $('#repeat-month');
  monthNum.click(function () {
    updateRecoverTimeTopTen();
  });
  var dayNum = $('#repeat-day');
  dayNum.click(function () {
    updateRecoverTimeTopTen();
  });
  var recoverNum = $('#repeat-num');
  recoverNum.click(function () {
    updateRecoverTimeTopTen();
  });
  recoverNum.keyup(function () {
    updateRecoverTimeTopTen();
  });

  var recoverInterval = $('#recover-interval');
  recoverInterval.click(function () {
    updateRecoverTimeTopTen();
  });

  var rdt = $('#runDateTime');
  rdt.blur(function () {
    updateRecoverTimeTopTen();
  });

  $("#id-show-start-five-date").click(function (evt) {
    $("#show-start-five-date").prop("checked", true);
    $("#show-last-five-date").prop("checked", false);
    updateRecoverTimeTopTen();
  });

  $("#id-show-last-five-date").click(function (evt) {
    $("#show-last-five-date").prop("checked", true);
    $("#show-start-five-date").prop("checked", false);
    updateRecoverTimeTopTen();
  });

  $("#enable-reverse-execute-history-recover").click(function (evt) {
    updateRecoverTimeTopTen();
  });

  beginTime.val(getRecoverDateFormat(new Date()));
  endTime.val(getRecoverDateFormat(new Date()));

});


function getHistoryRecoverOptionData (executingData, reverseExecuteFlag) {

  var beginTime = $('#datetimebegin').val();
  var endTime = $('#datetimeend').val();
  var monthNum = $('#repeat-month').val();
  var dayNum = $('#repeat-day').val();
  var hourNum = $('#repeat-hour').val();
  var minNum = $('#repeat-min').val();
  var state = true;
  var recoverNum = $('#repeat-num').val();
  var recoverInterval = $('#recover-interval').val();
  var recoverErrorOption = $('#recover-error-option').val();
  var runDateTimeList = [];
  if ($("#runDateTime").val()) {
    runDateTimeList = $("#runDateTime").val().split(",").map(function (x) { return Date.parse(x); });
  }

  // 历史重跑告警信息
  var historyRerunAlertLevel = $('#flow-history-rerun-finish-alert-level').val();
  var historyRerunAlertEmails = $('#flow-history-rerun-finish-emails').val();

  if (beginTime == '') {
    alert(wtssI18n.view.startTimeReq);
    state = false;
    return;
  }
  if (endTime == '') {
    alert(wtssI18n.view.endTimeReq);
    state = false;
    return;
  }

  if (beginTime > endTime) {
    alert(wtssI18n.view.timeFormat);
    state = false;
    return;
  }

  if (0 == recoverNum) {
    alert(wtssI18n.view.executionIntervaPro);
    state = false;
    return;
  }

  if ("" == recoverNum) {
    alert(wtssI18n.view.executeIntervalFormat);
    state = false;
    return;
  }

  var start = new Date(Date.parse(beginTime));
  var end = new Date(Date.parse(endTime));

  start.setMonth(start.getMonth() + parseInt(monthNum));
  start.setDate(start.getDate() + parseInt(dayNum));
  start.setHours(start.getHours() + parseInt(hourNum));
  start.setMinutes(start.getMinutes() + parseInt(minNum));


  if (start > end) {
    alert(wtssI18n.view.timeIntervalFormat);
    state = false;
    return;
  }

  var recoverData = executingData;

  recoverData.ajax = "";
  recoverData.begin = beginTime;
  recoverData.end = endTime;
  recoverData.month = monthNum;
  recoverData.day = dayNum;
  recoverData.hour = hourNum;
  recoverData.min = minNum;
  recoverData.state = state;
  recoverData.recoverNum = recoverNum;
  recoverData.recoverInterval = recoverInterval;
  recoverData.recoverErrorOption = recoverErrorOption;
  recoverData.runDateTimeList = runDateTimeList;
  recoverData.reverseExecuteFlag = reverseExecuteFlag;
  recoverData.historyRerunAlertLevel = historyRerunAlertLevel;
  recoverData.historyRerunAlertEmails = historyRerunAlertEmails;
  recoverData.taskSize = $('#recover-Concurrent-option').val();
  recoverData.finishedAlert = $("#enable-history-recover-finished-alert").is(':checked');

  return recoverData;
}

function HistoryRecoverExecute (executingData) {
  var reverseExecuteFlag = false;
  if ($("#enable-reverse-execute-history-recover").is(':checked')) {
    reverseExecuteFlag = true;
  }
  var recoverData = this.getHistoryRecoverOptionData(executingData, reverseExecuteFlag);
  if (recoverData) {
    this.checkRecoverParam(recoverData, repeatFlow);
  }
}

function repeatFlow (recoverData) {
  executeURL = "/executor?ajax=repeatCollection";
  recoverData.disabled = JSON.parse(recoverData.disabled);
  $.ajax({
    type: "POST",
    contentType: "application/json; charset=utf-8",
    url: executeURL,
    data: JSON.stringify(recoverData),
    dataType: 'json',
    //success: successHandler,
    error: function (XMLHttpRequest, textStatus, errorThrown) {
      //alert('请求后台异常！' + errorThrown);
    }
  });
}

function checkRecoverParam (recoverData, repeatFun) {
  executeURL = "/executor?ajax=recoverParamVerify";

  var successHandler = function (data) {
    if (data.error) {
      messageDialogView.show(wtssI18n.view.historyRerunError, data.error);
      return false;
    } else {
      flowExecuteDialogView.hideExecutionOptionPanel();
      messageDialogView.show(wtssI18n.view.historicalRerun, wtssI18n.view.rerunSubmitSuccess,
        function () {
          window.location.href = "/executor#recover-history";
          repeatFun(recoverData);
        }
      );

    }
  };

  $.ajax({
    type: "GET",
    contentType: "application/json",
    url: executeURL,
    data: recoverData,
    dataType: 'json',
    success: successHandler,
    error: function (XMLHttpRequest, textStatus, errorThrown) {
      //alert('请求后台异常！' + errorThrown);
    }
  });

}
//历史重跑Top10时间预览处理方法
function updateRecoverTimeTopTen () {
  $('#nextRecoverId').html("");
  var beginTime = $('#datetimebegin').val();
  var endTime = $('#datetimeend').val();
  var monthNum = $('#repeat-month').val();
  var dayNum = $('#repeat-day').val();

  var recoverNum = $('#repeat-num').val();
  var recoverInterval = $('#recover-interval').val();

  if (beginTime && endTime && 0 != recoverNum) {
    var recoverTimeList = loadRecoverTimeList();
    var flowDateList = loadFlowDateTimeList();

    if ($("#runDateTime").val()) {
      var _flowDateList = $("#runDateTime").val().split(", ").map(function (x) { return x.replace(/\//g, ""); });
      var _recoverTimeList = $("#runDateTime").val().split(", ").map(function (x) {
        var d = new Date(Date.parse(x));
        d.setDate(d.getDate() - 1);
        return getRecoverRunDateFormat(d);
      });
      _flowDateList.forEach(function (x) { flowDateList.push(x); });
      _recoverTimeList.forEach(function (x) { recoverTimeList.push(x); });
      recoverTimeList = Array.from(new Set(recoverTimeList));
      flowDateList = Array.from(new Set(flowDateList));
      recoverTimeList.sort();
      flowDateList.sort();
    }

    var tableRecoverTime = $("<table></table>");

    tableRecoverTime.attr("class", "table table-striped");

    var theadRecoverTime = $("<thead></thead>");
    var trHeadRecoverTime = $("<tr></tr>");
    var flowRecoverTime = $("<th></th>");
    flowRecoverTime.text("Flow Date");
    trHeadRecoverTime.append(flowRecoverTime);
    var runRecoverTime = $("<th></th>");
    runRecoverTime.text("Run Date");

    trHeadRecoverTime.append(runRecoverTime);
    theadRecoverTime.append(trHeadRecoverTime);
    tableRecoverTime.append(theadRecoverTime);

    var tbodyRecoverTime = $("<tbody></tbody>")


    // 如果选择倒序执行, 则调整预览页面数据, 逆序输出时间
    if ($("#enable-reverse-execute-history-recover").is(':checked')) {
      recoverTimeList.reverse();
      flowDateList.reverse();
    }

    var dateLength = recoverTimeList.length
    if ($("#show-start-five-date").is(":checked")) {
      if (dateLength <= 10) {
        for (var i = 0; i < dateLength; i++) {
          var tr = $("<tr></tr>");
          var tdFlow = $("<td></td>");
          tdFlow.text(flowDateList[i]);
          tr.append(tdFlow);

          var tdRun = $("<td></td>");
          tdRun.text(recoverTimeList[i]);
          tr.append(tdRun);
          tbodyRecoverTime.append(tr);
        }
      } else {
        for (var i = 0; i < 10; i++) {
          var tr = $("<tr></tr>");
          var tdFlow = $("<td></td>");
          tdFlow.text(flowDateList[i]);
          tr.append(tdFlow);

          var tdRun = $("<td></td>");
          tdRun.text(recoverTimeList[i]);
          tr.append(tdRun);
          tbodyRecoverTime.append(tr);
        }
      }
    }

    if ($("#show-last-five-date").is(":checked")) {
      if (dateLength <= 10) {
        for (var i = 0; i < dateLength; i++) {
          var tr = $("<tr></tr>");
          var tdFlow = $("<td></td>");
          tdFlow.text(flowDateList[i]);
          tr.append(tdFlow);

          var tdRun = $("<td></td>");
          tdRun.text(recoverTimeList[i]);
          tr.append(tdRun);
          tbodyRecoverTime.append(tr);
        }
      } else {
        for (var i = (dateLength - 10); i < dateLength; i++) {
          var tr = $("<tr></tr>");
          var tdFlow = $("<td></td>");
          tdFlow.text(flowDateList[i]);
          tr.append(tdFlow);

          var tdRun = $("<td></td>");
          tdRun.text(recoverTimeList[i]);
          tr.append(tdRun);
          tbodyRecoverTime.append(tr);
        }
      }
    }

    tableRecoverTime.append(tbodyRecoverTime);

    $('#nextRecoverId').append(tableRecoverTime);

    setHistoryRecoverRunNum();

  }

}
//获取预期Flow执行时间
function loadRecoverTimeList () {

  var beginTime = $('#datetimebegin').val();
  var endTime = $('#datetimeend').val();
  var monthNum = $('#repeat-month').val();
  var dayNum = $('#repeat-day').val();

  var recoverNum = $('#repeat-num').val();
  var recoverInterval = $('#recover-interval').val();

  var start = new Date(Date.parse(beginTime));
  var end = new Date(Date.parse(endTime));


  var recoverTimeList = new Array();



  var i = 0;
  var first = false;
  var firstDate;
  while (start <= end) {
    if (i == 0) {
      firstDate = start.getDate();
      var firstLastDay = getLastDay(start.getFullYear(), start.getMonth() + 1);
      if (firstDate == firstLastDay) {
        first = true;
      }
    }
    //    i++;
    //    if(i>10){
    //      break;
    //    }
    var run_date = new Date(Date.parse(beginTime));
    run_date.setFullYear(start.getFullYear(), start.getMonth(), start.getDate());
    run_date.setMonth(start.getMonth(), start.getDate());
    run_date.setDate(start.getDate() - 1);
    recoverTimeList.push(getRecoverRunDateFormat(run_date));
    if ("month" == recoverInterval) {
      //start.setMonth(start.getMonth() + parseInt(recoverNum), start.getDate());
      var oldDate = start.getDate();
      var oldMonth = start.getMonth();
      var newMonth = oldMonth + parseInt(recoverNum);

      var oldLastDay = getLastDay(start.getFullYear(), oldMonth + 1);

      var newLastDay = getLastDay(start.getFullYear(), newMonth + 1);

      if (oldDate > newLastDay) {
        start.setMonth(newMonth, newLastDay);
      } else if (first && oldDate < newLastDay && oldLastDay < newLastDay) {
        start.setMonth(newMonth, newLastDay);
      } else if (!first && oldDate < firstDate && oldLastDay < newLastDay) {
        start.setMonth(newMonth, firstDate);
      } else {
        start.setMonth(newMonth, oldDate);
      }

    } else if ("week" == recoverInterval) {
      start.setDate(start.getDate() + parseInt(recoverNum) * parseInt(7));
    } else if ("day" == recoverInterval) {
      start.setDate(start.getDate() + parseInt(recoverNum));
    }
  }

  return recoverTimeList;
}
//获取FlowDate时间
function loadFlowDateTimeList () {

  var beginTime = $('#datetimebegin').val();
  var endTime = $('#datetimeend').val();
  var monthNum = $('#repeat-month').val();
  var dayNum = $('#repeat-day').val();

  var recoverNum = $('#repeat-num').val();
  var recoverInterval = $('#recover-interval').val();

  var start = new Date(Date.parse(beginTime));
  var end = new Date(Date.parse(endTime));

  var recoverTimeList = new Array();

  var i = 0;
  var first = false;
  var firstDate;
  while (start <= end) {
    if (i == 0) {
      firstDate = start.getDate();
      var firstLastDay = getLastDay(start.getFullYear(), start.getMonth() + 1);
      if (firstDate == firstLastDay) {
        first = true;
      }
    }
    //    i++;
    //    if(i>10){
    //      break;
    //    }
    recoverTimeList.push(getRecoverRunDateFormat(start));
    if ("month" == recoverInterval) {
      // start.setDate(1);
      // var oldMonth = start.getMonth();
      // start.setMonth(oldMonth + parseInt(recoverNum));
      // start.setDate(31);

      var oldDate = start.getDate();
      var oldMonth = start.getMonth();
      var newMonth = oldMonth + parseInt(recoverNum);

      var oldLastDay = getLastDay(start.getFullYear(), oldMonth + 1);

      var newLastDay = getLastDay(start.getFullYear(), newMonth + 1);

      if (oldDate > newLastDay) {
        start.setMonth(newMonth, newLastDay);
      } else if (first && oldDate < newLastDay && oldLastDay < newLastDay) {
        start.setMonth(newMonth, newLastDay);
      } else if (!first && oldDate < firstDate && oldLastDay < newLastDay) {
        start.setMonth(newMonth, firstDate);
      } else {
        start.setMonth(newMonth, oldDate);
      }

    } else if ("week" == recoverInterval) {
      start.setDate(start.getDate() + parseInt(recoverNum) * parseInt(7));
    } else if ("day" == recoverInterval) {
      start.setDate(start.getDate() + parseInt(recoverNum));
    }
  }

  return recoverTimeList;

}

function getLastDay (year, month) {
  var d = new Date(0);
  if (month == 12) {
    d.setUTCFullYear(year + 1);
    d.setUTCMonth(0);
  } else {
    d.setUTCFullYear(year);
    d.setUTCMonth(month);
  }
  d.setTime(d.getTime() - 1);
  return d.getUTCDate();
}

function setHistoryRecoverRunNum () {

  var beginTime = $('#datetimebegin').val();
  var endTime = $('#datetimeend').val();

  var recoverNum = $('#repeat-num').val();
  var recoverInterval = $('#recover-interval').val();

  var start = new Date(Date.parse(beginTime));
  var end = new Date(Date.parse(endTime));

  var recoverTimeList = new Array();

  var i = 0;
  var first = false;
  var firstDate;
  while (start <= end) {
    if (i == 0) {
      firstDate = start.getDate();
      var firstLastDay = getLastDay(start.getFullYear(), start.getMonth() + 1);
      if (firstDate == firstLastDay) {
        first = true;
      }
    }
    i++;
    recoverTimeList.push(getRecoverRunDateFormat(start));
    if ("month" == recoverInterval) {
      // start.setDate(1);
      // var oldMonth = start.getMonth();
      // start.setMonth(oldMonth + parseInt(recoverNum));
      // start.setDate(31);

      var oldDate = start.getDate();
      var oldMonth = start.getMonth();
      var newMonth = oldMonth + parseInt(recoverNum);

      var oldLastDay = getLastDay(start.getFullYear(), oldMonth + 1);

      var newLastDay = getLastDay(start.getFullYear(), newMonth + 1);

      if (oldDate > newLastDay) {
        start.setMonth(newMonth, newLastDay);
      } else if (first && oldDate < newLastDay && oldLastDay < newLastDay) {
        start.setMonth(newMonth, newLastDay);
      } else if (!first && oldDate < firstDate && oldLastDay < newLastDay) {
        start.setMonth(newMonth, firstDate);
      } else {
        start.setMonth(newMonth, oldDate);
      }

    } else if ("week" == recoverInterval) {
      start.setDate(start.getDate() + parseInt(recoverNum) * parseInt(7));
    } else if ("day" == recoverInterval) {
      start.setDate(start.getDate() + parseInt(recoverNum));
    }
  }

  if ($("#runDateTime").val()) {
    var _recoverTimeList = $("#runDateTime").val().split(", ").map(function (x) {
      var d = new Date(Date.parse(x));
      return getRecoverRunDateFormat(d);
    });
    _recoverTimeList.forEach(function (x) { recoverTimeList.push(x); });
    recoverTimeList = Array.from(new Set(recoverTimeList));
    recoverTimeList.sort();
  }

  $("#history-run-num").text(recoverTimeList.length + wtssI18n.view.times);
}
