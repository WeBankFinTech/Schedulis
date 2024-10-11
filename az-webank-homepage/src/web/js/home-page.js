$(function () {
  // 删除嵌入dss隐藏头部标识
  sessionStorage.removeItem('hideHead')
  //根据屏幕宽度设置图标宽度
  var homePagePie = document.getElementById('home-page-pie');
  var innerWidth = window.innerWidth;
  var pieWidth = "600px"
  if (innerWidth < 1350) {
    pieWidth = "350px"
  } else if (innerWidth < 1600) {
    pieWidth = "420px"
  }

  var successWorkflowFlag = "成功工作流";
  var runningWorkflow1Flag = "正在运行工作流";
  var failedWorkflowFlag = "失败工作流";
  var killWorkflowFlag = "Kill工作流";
  var waitingWorkflowFlag = "等待工作流";

  var requestURL = contextURL + "/homepage";
  var requestData = {
    "ajax": "getHomePageLanguageType",
  };
  var successHandler = function (data) {
    if (data.error) {
      console.log(data.error.message);
    } else {
      var langTypeFlag = data.langType;
      if (langTypeFlag === "en_US") {

        successWorkflowFlag = "Successful";
        runningWorkflow1Flag = "Running";
        failedWorkflowFlag = "Failed";
        killWorkflowFlag = "Killed";
        waitingWorkflowFlag = "Waiting";
      }
    }
  }
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


  homePagePie.style.width = pieWidth
  var myChart = echarts.init(homePagePie);
  var option = {
    tooltip: {
      trigger: 'item',
      formatter: "{a} <br/>{b}: {c} ({d}%)"
    },
    legend: {
      orient: 'vertical',
      x: 'left',
      data: [successWorkflowFlag, runningWorkflow1Flag, failedWorkflowFlag, killWorkflowFlag, waitingWorkflowFlag],
      textStyle: {
        color: '#000000',
      }
    },
    series: [
      {
        name: wtssI18n.home.workflowStatus,
        type: 'pie',
        color: ['#4cae4c', '#39b3d7', '#d2322d', '#660066', '#CCC'],
        radius: ["50%", "70%"],
        label: {
          normal: {
            show: false,
            position: "chenter"
          },
          emphasis: {
            show: true,
            textStyle: {
              fontSize: "15",
              fontWeight: "bold",
              color: '#000000',
            }
          }
        },
        labelLine: {
          normal: {
            show: false
          }
        },

      }
    ]
  }

  if (option && typeof option == "object") {
    myChart.setOption(option, true);
  }

  getTodayExecFlowData(myChart);

  getRealTimeFlowInfo();

  //用于保存浏览数据，切换页面也能返回之前的浏览进度。
  todayFlowInfoModel = new azkaban.TodayFlowInfoModel();

  todayFlowInfoView = new azkaban.TodayFlowInfoView({
    el: $('#today-flow-info-view'),
    model: todayFlowInfoModel,
  });

});

var todayFormat = function () {
  var date = new Date();
  var year = date.getFullYear();
  var month = getTwoDigitStr(date.getMonth() + 1);
  var day = getTwoDigitStr(date.getDate());
  return month + "/" + day + "/" + year;
}

function getTodayExecFlowData (myChart) {
  var requestURL = contextURL + "/homepage";

  var requestData = {
    "ajax": "getTodayFlowExecuteStatus",
  };
  var successHandler = function (data) {
    if (data.error) {
      console.log(data.error.message);
    }
    else {
      myChart.setOption({
        series: [{
          data: data.todayFlowExecuteData
        }]
      });
      var langType = data.langType;
      var workflowCompleted = "已完成工作流";
      var runningWorkflow2 = "运行中工作流";
      var errorWorkflow = "错误工作流";
      var killWorkflow = "Kill工作流";
      var uninitiatedWorkflow = "未开始工作流";
      var averageTime = "平均执行时间";
      var totalTasks = "总任务数";
      if (langType === "en_US") {
        workflowCompleted = "Completed";
        runningWorkflow2 = "Running";
        errorWorkflow = "Error";
        killWorkflow = "Killed";
        uninitiatedWorkflow = "Not Started";
        averageTime = "Average Time";
        totalTasks = "Total Jobs";
      }
      var begin = todayFormat() + " 12:00 AM";
      var end = todayFormat() + " 11:59 PM";
      $("#success-td").html(
        "<div><span class='glyphicon glyphicon-record' style='color:#4cae4c'/><font style='font-size:16px;'>&nbsp;&nbsp;" + workflowCompleted + ": " + data.todayFlowExecuteData[0].value + "</font></div>"
        +
        "<div><span class='glyphicon glyphicon-record' style='color:#39b3d7'/><font style='font-size:16px;'>&nbsp;&nbsp;" + runningWorkflow2 + ": " + data.todayFlowExecuteData[1].value + "</font></div>"
        +
        "<div><span class='glyphicon glyphicon-record' style='color:#d2322d'/> <a href='/history?advfilter=true&projcontain=&flowcontain=&execIdcontain=&usercontain=&status=70&begin=" + begin + "&end=" + end + "&flowType=3'><font style='font-size:16px;'>&nbsp;&nbsp;" + errorWorkflow + ": " + data.todayFlowExecuteData[2].value + "</font></a></div>"
        +
        "<div><span class='glyphicon glyphicon-record' style='color:#660066'/><font style='font-size:16px;'>&nbsp;&nbsp;" + killWorkflow + ": " + data.todayFlowExecuteData[3].value + "</font></div>"
        +
        "<div><span class='glyphicon glyphicon-record' style='color:#CCC'/><font style='font-size:16px;'>&nbsp;&nbsp;" + uninitiatedWorkflow + ": " + data.todayFlowExecuteData[4].value + "</font></div>"
        +
        "<div><span class='glyphicon glyphicon-record' style='color:#AAA'/><font style='font-size:16px;'>&nbsp;&nbsp;" + averageTime + ": " + data.otherFlowExecData.moyenTime + "</font></div>"
        +
        "<div><span class='glyphicon glyphicon-record' style='color:#AAA'/><font style='font-size:16px;'>&nbsp;&nbsp;" + totalTasks + ": " + data.otherFlowExecData.jobTotal + "</font></div>"
        + "");
    }
  }

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
}

function getRealTimeFlowInfo () {
  var requestURL = contextURL + "/homepage";
  var execURL = contextURL + "/executor?execid=";

  var requestData = {
    "ajax": "getRealTimeFlowInfoData",
  };
  var successHandler = function (data) {
    if (data.error) {
      console.log(data.error.message);
    }
    else {
      var realTimeData = data.realTimeData;
      var langType = data.langType;
      // var currentLanguage=window.sessionStorage.getItem('languageType');
      for (var i = 0; i < realTimeData.length; i++) {
        var html = "<li>" + realTimeData[i].endTime + "<h4>工作流 " + realTimeData[i].flowName
          + " 执行状态 " + realTimeData[i].execStatus + "！ 执行ID：<a href=" + execURL + realTimeData[i].execId + ">" + realTimeData[i].execId + "</a></h4></li>"
        if (langType === "en_US") {
          // html="<li>" + realTimeData[i].endTime + "<h4>Workflow "+realTimeData[i].flowName+" execution state "+realTimeData[i].execStatus+" execution ID: <a href=" + execURL + realTimeData[i].execId +">" + realTimeData[i].execId + "</a></h4></li>"
          html = "<li>" + realTimeData[i].endTime + "<h4>The status of the " + realTimeData[i].flowName + "(<a href=" + execURL + realTimeData[i].execId + ">" + realTimeData[i].execId + "</a>)" + " is " + realTimeData[i].execStatus
        }
        $("#realTimeUl").append(html);
      }
    }
  }

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

}

//用于保存浏览数据，切换页面也能返回之前的浏览进度。
var todayFlowInfoModel;
azkaban.TodayFlowInfoModel = Backbone.Model.extend({});

//项目列表页面
var todayFlowInfoView;
azkaban.TodayFlowInfoView = Backbone.View.extend({
  events: {

  },

  initialize: function (settings) {
    this.model.bind('render', this.render, this);
    this.loadTodayFlowExecInfo();
  },

  render: function (evt) {
    console.log("render");
    // Render page selections
    var todayFlowTbody = $("#today-flow-info-tbody");
    todayFlowTbody.empty();

    var todayFlowInfo = this.model.get("todayAllFlowInfo");
    for (var i = 0; i < todayFlowInfo.length; ++i) {
      var row = document.createElement("tr");

      //组装 Flow 名称行
      var tdFlow = document.createElement("td");
      var flowA = document.createElement("a");
      $(flowA).attr("href", contextURL + "/manager?project=" + todayFlowInfo[i].projectName + "&flow=" + todayFlowInfo[i].flowName);
      $(flowA).text(todayFlowInfo[i].flowName);
      $(flowA).attr("style", "width: 350px; word-break:break-all;");
      tdFlow.appendChild(flowA);
      row.appendChild(tdFlow);

      //组装 Project 行
      var tdProject = document.createElement("td");
      var projectA = document.createElement("a");
      $(projectA).attr("href", contextURL + "/manager?project=" + todayFlowInfo[i].projectName);
      $(projectA).text(todayFlowInfo[i].projectName);
      $(projectA).attr("style", "width: 350px; word-break:break-all;");
      tdProject.appendChild(projectA);
      row.appendChild(tdProject);

      //组装 用户 行
      var tdUser = document.createElement("td");
      $(tdUser).text(todayFlowInfo[i].submitUser);
      row.appendChild(tdUser);

      //组装 执行次数
      var runTimes = document.createElement("td");
      $(runTimes).text(todayFlowInfo[i].todayFlowRuntimes);
      row.appendChild(runTimes);


      //组装 任务数 行
      var tdEndTime = document.createElement("td");
      $(tdEndTime).text(todayFlowInfo[i].totalJobNum);
      row.appendChild(tdEndTime);

      //组装 未开始 行
      var tdRunDate = document.createElement("td");
      $(tdRunDate).text(todayFlowInfo[i].jobNoExecNum);
      row.appendChild(tdRunDate);

      //组装 运行中 行
      var tdDifftime = document.createElement("td");
      $(tdDifftime).text(todayFlowInfo[i].jobRunningNum);
      row.appendChild(tdDifftime);

      //组装 运行成功 行
      var tdDifftime = document.createElement("td");
      $(tdDifftime).text(todayFlowInfo[i].jobSuccessNum);
      row.appendChild(tdDifftime);

      //组装 运行失败 行
      var tdDifftime = document.createElement("td");
      $(tdDifftime).text(todayFlowInfo[i].jobFailedNum);
      row.appendChild(tdDifftime);

      //组装 已取消 行
      var jobCancelNum = document.createElement("td");
      $(jobCancelNum).text(todayFlowInfo[i].jobCancelNum);
      row.appendChild(jobCancelNum);
      todayFlowTbody.append(row);
    }

    $(document).ready(function () {
      var jobTable = $("#executingJobs");
      jobTable.tablesorter();
    });
  },

  loadTodayFlowExecInfo: function (evt) {
    var requestURL = contextURL + "/homepage";
    var model = this.model;
    var requestData = {
      "ajax": "getTodayAllFlowInfo",
    };

    var successHandler = function (data) {
      model.set({
        "todayAllFlowInfo": data.todayFlowExecuteInfo,
      });
      model.trigger("render");
    };
    $.get(requestURL, requestData, successHandler, "json");
  },
});
