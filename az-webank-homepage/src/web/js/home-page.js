$.namespace('azkaban');
$(function () {
  // 删除嵌入dss隐藏头部标识
  sessionStorage.removeItem('hideHead');
  //用于保存浏览数据，切换页面也能返回之前的浏览进度。
  todayFlowInfoModel = new azkaban.TodayFlowInfoModel();


  function getPieConfig () {
    //根据屏幕宽度设置图标宽度
    var innerWidth = window.innerWidth;
    var radiusX = '50%';
    var radiusY = '70%';
    var pieWidth = "600px"
    var fontSize = '15'
    if (innerWidth < 1350) {
        pieWidth = "400px"
        radiusX = '35%';
        radiusY = '40%';
        fontSize = '12'
    } else if (innerWidth < 1600) {
        pieWidth = "480px"
        radiusX = '45%';
        radiusY = '50%';
        fontSize = '13'
    }
    return {
        radiusX: radiusX,
        radiusY: radiusY,
        pieWidth: pieWidth,
        fontSize: fontSize
    }
  }

  var homePagePie = document.getElementById('home-page-pie');
  var myChart = {};
  var option = {};
  function renderPie () {
    var pieConfig = getPieConfig();
    homePagePie.style.width = pieConfig.pieWidth;
    myChart = echarts.init(homePagePie);
    var successWorkflowFlag = "成功工作流";
    var runningWorkflow1Flag = "正在运行工作流";
    var failedWorkflowFlag = "失败工作流";
    var killWorkflowFlag = "Kill工作流";
    var waitingWorkflowFlag = "等待工作流";

    var requestURL = "/homepage";
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
        option = {
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
            series: [{
                name: wtssI18n.home.workflowStatus,
                type: 'pie',
                color: ['#4cae4c', '#39b3d7', '#d2322d', '#660066', '#CCC'],
                radius: [pieConfig.radiusX, pieConfig.radiusY],
                label: {
                    normal: {
                        show: false,
                        position: "chenter"
                    },
                    emphasis: {
                        show: false,
                        textStyle: {
                        fontSize: pieConfig.fontSize,
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
                data:[
                    {name: successWorkflowFlag, value: '0'},
                    {name: runningWorkflow1Flag, value: '0'},
                    {name: failedWorkflowFlag, value: '0'},
                    {name: killWorkflowFlag, value: '0'},
                    {name: waitingWorkflowFlag, value: '0'}
                ]
            }]
        }
        if (option && typeof option == "object") {
            myChart.setOption(option);
        }
    }
    $.ajax({
        url: requestURL,
        type: "get",
        async: true,
        data: requestData,
        dataType: "json",
        error: function (data) {
        console.log(data);
        },
        success: successHandler
    });

  }

  getRealTimeFlowInfo();

  renderPie();
  getTodayExecFlowData(myChart);
  // 监听屏幕大小
  window.addEventListener('resize', function () {
    var pieConfig = getPieConfig();
    if ( homePagePie.style.width !== pieConfig.pieWidth ) {
        homePagePie.style.width = pieConfig.pieWidth;
        option.series[0].radius = [pieConfig.radiusX, pieConfig.radiusY];
        option.series[0].label.emphasis.fontSize = pieConfig.fontSize;
        if (option && typeof option == "object") {
            myChart.setOption(option);
        }
    }
  })

  todayFlowInfoView = new azkaban.TodayFlowInfoView({
    el: $('#today-flow-info-view'),
    model: todayFlowInfoModel,
  });

  $("#todayFlowInfoHeader").on('click',function () {
      if (!todayFlowInfoView.model.get("todayAllFlowInfo")) {
        todayFlowInfoView.loadTodayFlowExecInfo();
      }
  })
});

var todayFormat = function () {
  var date = new Date();
  var year = date.getFullYear();
  var month = getTwoDigitStr(date.getMonth() + 1);
  var day = getTwoDigitStr(date.getDate());
  return month + "/" + day + "/" + year;
}

function getTodayExecFlowData (myChart) {
  var requestURL = "/homepage";

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
      var workflowCompleted = "";
      var runningWorkflow2 = "";
      var errorWorkflow = "";
      var killWorkflow = "";
      var uninitiatedWorkflow = "";
      var averageTime = "";
      var totalTasks = "";
      if (langType === "en_US") {
        workflowCompleted = "Completed";
        runningWorkflow2 = "Running";
        errorWorkflow = "Error";
        killWorkflow = "Killed";
        uninitiatedWorkflow = "Not Started";
        averageTime = "Average Time";
        totalTasks = "Total Jobs";
      } else {
        workflowCompleted = "已完成工作流";
        runningWorkflow2 = "运行中工作流";
        errorWorkflow = "错误工作流";
        killWorkflow = "Kill工作流";
        uninitiatedWorkflow = "未开始工作流";
        averageTime = "平均执行时间";
        totalTasks = "总任务数";
      }
      var successHtml = "<div><span class='glyphicon glyphicon-record' style='color:#4cae4c'/> <a target='_blank' href='/history?advfilter=true&projcontain=&flowcontain=&execIdcontain=&usercontain=&status=50&fromHomePage=true&flowType=3&runDate='><font style='font-size:16px;'>&nbsp;&nbsp;" + workflowCompleted + ": " + data.todayFlowExecuteData[0].value + "</font></a></div>"
        +
        "<div><span class='glyphicon glyphicon-record' style='color:#39b3d7'/> <a target='_blank' href='/history?advfilter=true&projcontain=&flowcontain=&execIdcontain=&usercontain=&status=30&fromHomePage=true&flowType=3&runDate='><font style='font-size:16px;'>&nbsp;&nbsp;" + runningWorkflow2 + ": " + data.todayFlowExecuteData[1].value + "</font></a></div>"
        +
        "<div><span class='glyphicon glyphicon-record' style='color:#d2322d'/> <a target='_blank' href='/history?advfilter=true&projcontain=&flowcontain=&execIdcontain=&usercontain=&status=70&fromHomePage=true&flowType=3&runDate='><font style='font-size:16px;'>&nbsp;&nbsp;" + errorWorkflow + ": " + data.todayFlowExecuteData[2].value + "</font></a></div>"
        +
        "<div><span class='glyphicon glyphicon-record' style='color:#660066'/> <a target='_blank' href='/history?advfilter=true&projcontain=&flowcontain=&execIdcontain=&usercontain=&status=60&fromHomePage=true&flowType=3&runDate='><font style='font-size:16px;'>&nbsp;&nbsp;" + killWorkflow + ": " + data.todayFlowExecuteData[3].value + "</font></a></div>"
        +
        "<div><span class='glyphicon glyphicon-record' style='color:#CCC'/><font style='font-size:16px;'>&nbsp;&nbsp;" + uninitiatedWorkflow + ": " + data.todayFlowExecuteData[4].value + "</font></div>"
        +
        "<div><span class='glyphicon glyphicon-record' style='color:#AAA'/><font style='font-size:16px;'>&nbsp;&nbsp;" + averageTime + ": " + data.otherFlowExecData.moyenTime + "</font></div>"
        +
        "<div><span class='glyphicon glyphicon-record' style='color:#AAA'/><font style='font-size:16px;'>&nbsp;&nbsp;" + totalTasks + ": " + data.otherFlowExecData.jobTotal + "</font></div>"
        + ""
      successHtml = filterXSS(successHtml, { 'whiteList': { 'div': [], 'span': ['class', 'style'], 'font': ['style'], 'a': ['href', 'target'] } })
      $("#success-td").html(successHtml);
    }
  }

  $.ajax({
    url: requestURL,
    type: "get",
    async: true,
    data: requestData,
    dataType: "json",
    error: function (data) {
      console.log(data);
    },
    success: successHandler
  });
}

function getRealTimeFlowInfo () {
  var requestURL = "/homepage";
  var execURL = "/executor?execid=";

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
      var html = ''
      for (var i = 0; i < realTimeData.length; i++) {
        if (langType === "en_US") {
          // html="<li>" + realTimeData[i].endTime + "<h4>Workflow "+realTimeData[i].flowName+" execution state "+realTimeData[i].execStatus+" execution ID: <a href=" + execURL + realTimeData[i].execId +">" + realTimeData[i].execId + "</a></h4></li>"
          html += "<li>" + realTimeData[i].endTime + "<h4>The status of the " + realTimeData[i].flowName + "(<a target='_blank' href=" + execURL + realTimeData[i].execId + ">" + realTimeData[i].execId + "</a>)" + " is " + realTimeData[i].execStatus
        } else {
          html += "<li>" + realTimeData[i].endTime + "<h4>工作流 " + realTimeData[i].flowName
            + " 执行状态 " + realTimeData[i].execStatus + "！ 执行ID：<a target='_blank' href=" + execURL + realTimeData[i].execId + ">" + realTimeData[i].execId + "</a></h4></li>"
        }
      }
      html = filterXSS(html)
      $("#realTimeUl").append(html);
    }
  }

  $.ajax({
    url: requestURL,
    type: "get",
    async: true,
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
  },

  render: function (evt) {
    console.log("render");
    // Render page selections
    var todayFlowTbody = $("#today-flow-info-tbody");
    if (this.model.get('page') === 1) {
        todayFlowTbody.empty();
    }
    var todayFlowInfo = this.model.get("todayAllFlowInfo");
    for (var i = 0; i < todayFlowInfo.length; ++i) {
      var row = document.createElement("tr");

      //组装 Flow 名称行
      var tdFlow = document.createElement("td");
      var flowA = document.createElement("a");
      var flowAUrl = filterXSS("/manager?project=" + todayFlowInfo[i].projectName + "&flow=" + todayFlowInfo[i].flowName)
      $(flowA).attr("href", flowAUrl);
      $(flowA).attr("target", '_blank');
      $(flowA).text(todayFlowInfo[i].flowName);
      $(flowA).attr("style", "width: 350px; word-break:break-all;");
      tdFlow.appendChild(flowA);
      row.appendChild(tdFlow);

      //组装 Project 行
      var tdProject = document.createElement("td");
      var projectA = document.createElement("a");
      var projectAUrl = filterXSS("/manager?project=" + todayFlowInfo[i].projectName)
      $(projectA).attr("href", projectAUrl);
      $(projectA).attr("target", '_blank');
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

    $("#todayFlowInfoHeader").removeClass();
    $('#today-flow-info-view').show();
    $('#flowInfoTitle').text(window.wtssI18n.home.todayFlowWorkingStatus);
    $("#executingJobs").tablesorter();
  },

  loadTodayFlowExecInfo: function (evt) {
    var requestURL = "/homepage";
    var model = this.model;
    var requestData = {
      "ajax": "getTodayAllFlowInfo",
      page: 1,
      size: 10000
    };

    var successHandler = function (data) {
      model.set({
        "todayAllFlowInfo": data.todayFlowExecuteInfo,
        total: data.total
      });
      model.trigger("render");
    };
    $.get(requestURL, requestData, successHandler, "json");
  },
});
