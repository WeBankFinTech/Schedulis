
$.namespace('azkaban');
//处理方法 组装表格和翻页处理
var batchHoldView;
azkaban.BatchHoldView = Backbone.View.extend({
  events: {
    "click #pageSelection li": "handleChangePageSelection",
    "change #pageSelection .pageSizeSelect": "handlePageSizeSelection",
    "click #pageSelection .pageNumJump": "handlePageNumJump",
    "click .resumeFlow": "handleResumeFlowBtn",
    "click .stopFlow": "handleStopFlowBtn"
  },

  initialize: function (settings) {
    this.model.set('elDomId','pageSelection'); 
    this.model.bind('change:view', this.handleChangeView, this);
    this.model.bind('render', this.render, this);
    this.model.bind('change:page', this.handlePageChange, this);
    this.model.set({ page: 1, pageSize: 20 });
    this.createResize();
  },
  render: function (evt) {
    console.log("render");
    // Render page selections
    var tbody = $("#batchTableBody");
    tbody.empty();

    var batchList = this.model.get("batchPageList");
    if (!batchList || batchList.length == 0) {
      $("#pageSelection").hide();
      return;
    } else {
      $("#pageSelection").show()
    }


    for (var i = 0; i < batchList.length; ++i) {
      var row = document.createElement("tr");

      //组装数字行
      var tdNum = document.createElement("td");
      $(tdNum).text(i + 1);
      $(tdNum).attr("class", "tb-name");
      row.appendChild(tdNum);

      //批次号
      var tdBatchId = document.createElement("td");
      $(tdBatchId).text(batchList[i].batchId);
      row.appendChild(tdBatchId);

      //hold批级别
      var tdHoldLevel = document.createElement("td");
      $(tdHoldLevel).text(holdLevelMap[batchList[i].oprLevel]);
      $(tdHoldLevel).attr("style", "word-break:break-all;");
      row.appendChild(tdHoldLevel);

      //执行id
      var tdExecId = document.createElement("td");
      $(tdExecId).text(batchList[i].execId);
      $(tdExecId).attr("style", "word-break:break-all;");
      row.appendChild(tdExecId);

      //项目名
      var tdProjectName = document.createElement("td");
      $(tdProjectName).text(batchList[i].projectName);
      $(tdProjectName).attr("style", "word-break:break-all;");
      row.appendChild(tdProjectName);

      //工作流
      var tdFlowName = document.createElement("td");
      $(tdFlowName).text(batchList[i].flowName);
      $(tdFlowName).attr("style", "word-break:break-all;");
      row.appendChild(tdFlowName);

      //关键路径
      var tdBusPath = document.createElement("td");
      $(tdBusPath).text(batchList[i].busPath?(busPathMap[batchList[i].busPath]?busPathMap[batchList[i].busPath]:''):'');
      $(tdBusPath).attr("style", "word-break:break-all;");
      row.appendChild(tdBusPath);

      //子系统
      var tdSubSystem = document.createElement("td");
      $(tdSubSystem).text(batchList[i].subsystem?(subsystemMap[batchList[i].subsystem]?subsystemMap[batchList[i].subsystem]:''):'');
      $(tdSubSystem).attr("style", "word-break:break-all;");
      row.appendChild(tdSubSystem);

      //最晚开始时间
      var tdLastStartTime = document.createElement("td");
      $(tdLastStartTime).text(batchList[i].lastStartTime?batchList[i].lastStartTime:'');
      $(tdLastStartTime).attr("style", "word-break:break-all;");
      row.appendChild(tdLastStartTime);

      //最晚结束时间
      var tdLastFinishTime = document.createElement("td");
      $(tdLastFinishTime).text(batchList[i].lastFinishTime?batchList[i].lastFinishTime:'');
      $(tdLastFinishTime).attr("style", "word-break:break-all;");
      row.appendChild(tdLastFinishTime);

      //开发部门
      var tdDevDept = document.createElement("td");
      $(tdDevDept).text(batchList[i].devDept?batchList[i].devDept:'');
      $(tdDevDept).attr("style", "word-break:break-all;");
      row.appendChild(tdDevDept);

      //提交用户
      var tdSubmitUser = document.createElement("td");
      $(tdSubmitUser).text(batchList[i].submitUser);
      $(tdSubmitUser).attr("style", "word-break:break-all;");
      row.appendChild(tdSubmitUser);

      //Hold批时间
      var tdHoldStartTime = document.createElement("td");
      $(tdHoldStartTime).text(batchList[i].holdTime>0?getProjectModifyDateFormat(new Date(batchList[i].holdTime)):'');
      $(tdHoldStartTime).attr("style", "word-break:break-all;");
      row.appendChild(tdHoldStartTime);

      //恢复状态
      var tdResumeStatus = document.createElement("td");
      $(tdResumeStatus).text(resumeStatusMap[batchList[i].isResume]);
      $(tdResumeStatus).attr("style", "word-break:break-all;");
      row.appendChild(tdResumeStatus);

      //恢复时间
      var tdResumeTime = document.createElement("td");
      $(tdResumeTime).text(batchList[i].resumeTime>0?getProjectModifyDateFormat(new Date(batchList[i].resumeTime)):'');
      $(tdResumeTime).attr("style", "word-break:break-all;");
      row.appendChild(tdResumeTime);

      //告警状态
      var tdAlertStatus = document.createElement("td");
      $(tdAlertStatus).text(batchList[i].sendStatus?alertStatusMap[batchList[i].sendStatus]:'');
      $(tdAlertStatus).attr("style", "word-break:break-all;");
      row.appendChild(tdAlertStatus);

      //告警时间
      var tdAlertTime = document.createElement("td");
      $(tdAlertTime).text(batchList[i].sendTime>0?getProjectModifyDateFormat(new Date(batchList[i].sendTime)):'');
      $(tdAlertTime).attr("style", "word-break:break-all;");
      row.appendChild(tdAlertTime);

      //黑名单标签
      var tdIsBlack = document.createElement("td");
      $(tdIsBlack).text(batchList[i].isBlack?yesOrNoMap[batchList[i].isBlack]:'');
      $(tdIsBlack).attr("style", "word-break:break-all;");
      row.appendChild(tdIsBlack);

      //组装操作行
      var tdAction = document.createElement("td");
      if(!batchList[i].isResume){
        $(tdAction).attr("style", "width:110px;vertical-align:middle;");
        var resumeBtn = document.createElement("button");
        $(resumeBtn).attr("id", "resumeBtn" + i);
        $(resumeBtn).attr("name", batchList[i].id);
        $(resumeBtn).attr("class", "btn btn-sm btn-info resumeFlow");
        $(resumeBtn).text(wtssI18n.view.resumeFlowBtn);
        tdAction.appendChild(resumeBtn);

        var stopBtn = document.createElement("button");
        $(stopBtn).attr("id", "stopBtn" + i);
        $(stopBtn).attr("name", batchList[i].id);
        $(stopBtn).attr("class", "btn btn-sm btn-danger stopFlow");
        $(stopBtn).attr("style", "margin-left:5px;");
        $(stopBtn).text(wtssI18n.view.stopFlowBtn);
        tdAction.appendChild(stopBtn);
      }
      row.appendChild(tdAction);


      tbody.append(row);
    }

    this.renderPagination(evt);
  },
  ...commonPaginationFun(),

  handlePageChange: function (evt) {
    const model = this.model;
    const start = model.get("page") - 1;
    const pageSize = model.get("pageSize");
    const requestURL = "/batch";
    const searchterm = model.get("searchterm");
    const advQueryParam = model.get("advQueryParam");
    const requestData = {
        "ajax": "ajaxQueryHoldBatchList",
        "start": start,
        "pageSize": pageSize,
    };
    if (advQueryParam) {
        Object.assign(requestData, advQueryParam);
    } else {
        requestData.searchterm = searchterm;
    }
    var successHandler = function (data) {
        // 高级过滤隐藏弹窗
        if (advQueryParam) {
            $("#adv-filter").modal("hide");
        }
      model.set({
        "batchPageList": data.batchPageList,
        "total": data.total
      });
      model.trigger("render");
    };
    $.get(requestURL, requestData, successHandler, "json");
  },

  handleResumeFlowBtn: function (evt) {
    var alertId = evt.currentTarget.name;
    $("[name='"+alertId+"']").attr('disabled','disabled');
    var prompt = window.langType === 'zh_CN' ? '是否恢复' + alertId + '一键HOLD批?' : 'Whether to resume ' + alertId + ' hold batch';
    deleteDialogView.show(wtssI18n.deletePro.resumeHoldBatch, prompt, wtssI18n.common.cancel, wtssI18n.view.resumeFlowBtn, function () {
        $("[name='"+alertId+"']").attr('disabled',false);
    }, function() {
        var requestURL = "/batch";
        var requestData = {
        "ajax": "ajaxResumeFlow",
        "alertId": alertId
        };
        var successHandler = function (data) {
        $("[name='"+alertId+"']").attr('disabled',false);
        if (data.error) {
            alert(data.error);
        } else {
            alert("resume flow succeeded");
        }
        batchTabView.handleBatchHoldViewLinkClick();
        };
        $.get(requestURL, requestData, successHandler, "json");
    })
  },

  handleStopFlowBtn: function (evt) {
    var alertId = evt.currentTarget.name;
    $("[name='"+alertId+"']").attr('disabled','disabled');
    var prompt = window.langType === 'zh_CN' ? '是否终止' + alertId + '一键HOLD批?' : 'Whether to terminate ' + alertId + ' hold batch';
    deleteDialogView.show(wtssI18n.deletePro.terminateHoldBatch, prompt, wtssI18n.common.cancel, wtssI18n.view.stopFlowBtn, function () {
        $("[name='"+alertId+"']").attr('disabled',false);
    }, function() {
        var requestURL = "/batch";
        var requestData = {
        "ajax": "ajaxStopFlow",
        "alertId": alertId
        };
        var successHandler = function (data) {
        $("[name='"+alertId+"']").attr('disabled',false);
        if (data.error) {
            alert(data.error);
        } else {
            alert("stop flow succeeded");
        }
        batchTabView.handleBatchHoldViewLinkClick();
        };
        $.get(requestURL, requestData, successHandler, "json");
    });
  }

});

var holdOptionsView;
azkaban.HoldOptionsView = Backbone.View.extend({
  events: {
    "click #holdBatchBtn": "handleHoldBatch",
    "click #resumeBatchBtn": "handleResumeBatch",
    "click #search-batch-btn": "handleSearchBatch",
    "click #adv-filter-btn": "handleAdvFilterSearch"
  },

  initialize: function (settings) {
    fetchCmdbData("bus-path-select", 'wb_batch_critical_path', 'id', 'name', "", true);
  },

  handleHoldBatch: function (evt) {
    $('#hold-batch-panel').modal();
    // holdBatchPanel.loadBusPathSelect();
    holdBatchPanel.render();
  },

  handleResumeBatch: function (evt) {
    $('#resume-batch-panel').modal();
    resumeBatchPanel.render();
  },

  handleSearchBatch: function () {
    var searchterm = filterXSS($('#serarch-batch').val());
    this.model.set({ "searchterm": searchterm });
    batchTabView.handleBatchHoldViewLinkClick();
  },

  handleAdvFilterSearch: function () {
    $('#adv-filter').modal();
    advFilterView.render();
  },

  render: function () {
  }
});

var advFilterView;
azkaban.AdvFilterView = Backbone.View.extend({
  events: {
    "click #filter-btn": "handleAdvFilter"
  },

  initialize: function (settings) {
  },

  handleAdvFilter: function (evt) {
    var model = this.model;
    var projcontain = $('#projcontain').val();
    var flowcontain = $('#flowcontain').val();
    var busPathQuery = $('#busPathQuery').val();
    var batchidcontain = $('#batchidcontain').val();
    var subSystemQuery = $('#subSystemQuery').val();
    var devDeptQuery = $('#devDeptQuery').val();
    var usercontain = $('#usercontain').val();
    var execIdcontain = $('#execIdcontain').val();
    model.set("advQueryParam", {
        "projectName": projcontain,
        "flowName": flowcontain,
        "busPath": busPathQuery,
        "batchId": batchidcontain,
        "subSystem": subSystemQuery,
        "devDept": devDeptQuery,
        "submitUser": usercontain,
        "execId": execIdcontain,
        "isAdvQuery": "true",
    });
    batchHoldModel.trigger("change:view");
    // var pageSize = this.model.get("pageSize");
    // var requestURL = "/batch";
    // var requestData = {
    //   "ajax": "ajaxQueryHoldBatchList",
    //   "start": 0,
    //   "pageSize": pageSize,
    //   "projectName": projcontain,
    //   "flowName": flowcontain,
    //   "busPath": busPathQuery,
    //   "batchId": batchidcontain,
    //   "subSystem": subSystemQuery,
    //   "devDept": devDeptQuery,
    //   "submitUser": usercontain,
    //   "execId": execIdcontain,
    //   "isAdvQuery": "true"
    // };
    // var successHandler = function (data) {
    //   if (data.error) {
    //     alert(data.error);
    //   }else {
    //     $("#adv-filter").modal("hide");
    //     model.set({
    //       "batchPageList": data.batchPageList,
    //       "total": data.total
    //     });
    //     batchHoldView.render();
    //   }
    // }

    // $.ajax({
    //   url: requestURL,
    //   type: "get",
    //   async: false,
    //   data: requestData,
    //   dataType: "json",
    //   error: function (data) {
    //     console.log(data);
    //   },
    //   success: successHandler
    // });

  },

  loadBusPathSelect: function () {
    if(advQueryViewFirstShow){
      $("#busPathQuery").find("option:selected").text("");
      $("#busPathQuery").empty();
      if (busPathMap) {
        var optionHtml = "";
        for (var key in busPathMap) {
          if(key){
            optionHtml += "<option value='" + key + "'>" + busPathMap[key] + "</option>";
          }
        }
        optionHtml = filterXSS(optionHtml, { 'whiteList': { 'option': ['value'] } });
        $("#busPathQuery").append(optionHtml);
      }
    }else{
      $("#busPathQuery").val(null);
    }

    $("#busPathQuery").selectpicker('refresh');
    $("#busPathQuery").selectpicker('render');
  },

  loadSubSystemSelect: function () {
    if(advQueryViewFirstShow){
      $("#subSystemQuery").find("option:selected").text("");
      $("#subSystemQuery").empty();
      if (subsystemMap) {
        var optionHtml = "";
        for (var key in subsystemMap) {
          if(key){
            optionHtml += "<option value='" + key + "'>" + subsystemMap[key] + "</option>";
          }
        }
        optionHtml = filterXSS(optionHtml, { 'whiteList': { 'option': ['value'] } })
        $("#subSystemQuery").append(optionHtml);
      }
    }else{
      $("#subSystemQuery").val(null);
    }

    $("#subSystemQuery").selectpicker('refresh');
    $("#subSystemQuery").selectpicker('render');
  },

  loadDevDeptData: function () {
    $("#devDeptQuery").select2({
      multiple: false,
      width: 'resolve',
      //language: "zh-CN",
      tags: false,//允许手动添加
      allowClear: true,//允许清空
      escapeMarkup: function (markup) { return markup; }, //自定义格式化防止XSS注入
      minimumInputLengt: 1,//最少输入多少字符后开始查询
      formatResult: function formatRepo (repo) { return repo.text; },//函数用来渲染结果
      formatSelection: function formatRepoSelection (repo) { return repo.text; },//函数用于呈现当前的选择
      ajax: {
        type: 'GET',
        url: "/batch",
        dataType: 'json',
        delay: 250,
        data: function (params) {
          var query = {
            ajax: "ajaxQueryDevDeptList",
            search: params.term,
            page: params.page || 1,
            pageSize: 20
          }
          return query;
        },
        processResults: function (data, params) {
          params.page = params.page || 1;
          return {
            results: data.devDeptList,
            pagination: {
              more: (params.page * 20) < data.totalCount
            }
          }
        },
        cache: true
      },
      language: 'zh-CN'
    });
  },

  render: function () {
    this.loadBusPathSelect();
    this.loadSubSystemSelect();
    this.loadDevDeptData();
    $('#projcontain').val(null);
    $('#flowcontain').val(null);
    $('#batchidcontain').val(null);
    $('#devDeptQuery').val(null).trigger('change');
    $('#usercontain').val(null);
    $('#execIdcontain').val(null);
    advQueryViewFirstShow=false;
  }
});


var holdBatchPanel;
azkaban.HoldBatchPanel = Backbone.View.extend({
  events: {
    "click #hold-batch-btn": "handleHoldBatch"
  },

  initialize: function (settings) {
  },

  handleHoldBatch: function (evt) {
    var holdType = $("#hold-type-select").val();
    var holdLevel = $("#hold-level-select").val();
    var dataList = $("#data-list-select").val()?$("#data-list-select").val().join(';'):"";
    var busPathList = $("#bus-path-select").val()?$("#bus-path-select").val().join(';'):"";
    var whiteFlowList = $("#white-list-select").val()?$("#white-list-select").val().join(';'):"";
    var requestURL = "/batch";

    if(!holdType){
      alert(wtssI18n.view.holdTypeSelectTips);
      return;
    }

    if(!holdLevel){
      alert(wtssI18n.view.holdLevelSelectTips);
      return;
    }

    if(holdLevel>0&&!dataList){
      alert(wtssI18n.view.holdDataSelectTips);
      return;
    }

    $("#hold-batch-panel").modal('hide');
    $("#hold-batch-running-modal").modal();

    var model = this.model;
    var requestData = {
      "ajax": "ajaxHoldBatch",
      "holdType": holdType,
      "holdLevel": holdLevel,
      "dataList": dataList,
      "busPathList": busPathList,
      "flowList": whiteFlowList
    };
    var successHandler = function (data) {
      $("#hold-batch-running-modal").modal("hide");
      if (data.error) {
        setTimeout(function () {
          alert(data.error);
        }, 50);
        return false;
      } else {
        batchTabView.handleBatchHoldViewLinkClick();
      }
    };
    $.post(requestURL, requestData, successHandler, "json");
  },

  loadWhiteFlowData: function () {

    $("#white-list-select").select2({
      placeholder: wtssI18n.view.whiteFlowSelectTips,//默认文字提示
      multiple: true,
      width: 'resolve',
      //language: "zh-CN",
      tags: false,//允许手动添加
      //allowClear: true,//允许清空
      escapeMarkup: function (markup) { return markup; }, //自定义格式化防止XSS注入
      minimumInputLengt: 1,//最少输入多少字符后开始查询
      formatResult: function formatRepo (repo) { return repo.text; },//函数用来渲染结果
      formatSelection: function formatRepoSelection (repo) { return repo.text; },//函数用于呈现当前的选择
      ajax: {
        type: 'GET',
        url: "/batch",
        dataType: 'json',
        delay: 250,
        data: function (params) {
          var query = {
            ajax: "ajaxQueryProjectFlowList",
            search: params.term,
            page: params.page || 1,
            pageSize: 20
          }
          return query;
        },
        processResults: function (data, params) {
          params.page = params.page || 1;
          return {
            results: data.flowList,
            pagination: {
              more: (params.page * 20) < data.totalCount
            }
          }
        },
        cache: true
      },
      language: 'zh-CN'
    });
  },

  loadBusPathSelect: function () {
    $("#bus-path-select").find("option:selected").text("");
    $("#bus-path-select").empty();
    if (busPathMap) {
      var optionHtml = "";
      for (var key in busPathMap) {
        if(key){
          optionHtml += "<option value='" + key + "'>" + busPathMap[key] + "</option>";
        }
      }
      optionHtml = filterXSS(optionHtml, { 'whiteList': { 'option': ['value'] } });
      $("#bus-path-select").append(optionHtml);
    }

    $("#bus-path-select").selectpicker('refresh');
    $("#bus-path-select").selectpicker('render');
  },

  render: function () {
    $('#hold-type-select').val('0');
    $('#hold-level-select').val('0');
    $("#hold-level-select").trigger('change');
    $('#data-list-select').val(null).trigger('change');
    $('#bus-path-select').val(null);
    $('#white-list-select').val(null);
    this.loadWhiteFlowData();
  }
});

var resumeBatchPanel;
azkaban.ResumeBatchPanel = Backbone.View.extend({
  events: {
    "click #resume-batch-btn": "handleResumeBatch"
  },

  initialize: function (settings) {

  },

  handleResumeBatch: function (evt) {
    var resumeLevel = $("#resume-level-select").val();
    var batchId = $("#resume-batch-select").val();
    var dataList = $("#resume-data-list-select").val()?$("#resume-data-list-select").val().join(';'):"";
    var blackList = $("#resume-black-list-select").val()?$("#resume-black-list-select").val().join(';'):"";

    if(!resumeLevel){
      alert(wtssI18n.view.holdLevelSelectTips);
      return;
    }

    if(!batchId){
      alert(wtssI18n.view.holdBatchIdSelectTips);
      return;
    }

    if(resumeLevel>0 && !dataList){
      $('#resume-batch-panel').modal('hide');
      warnDialogView.show(wtssI18n.view.warning, wtssI18n.view.emptyDataListTips, wtssI18n.common.no, wtssI18n.common.yes,function(){
        $('#resume-batch-panel').modal();
      },this.postResume);
    }else{
      this.postResume();
    }

  },

  postResume:function(){
    var resumeLevel = $("#resume-level-select").val();
    var batchId = $("#resume-batch-select").val();
    var dataList = $("#resume-data-list-select").val()?$("#resume-data-list-select").val().join(';'):"";
    var blackList = $("#resume-black-list-select").val()?$("#resume-black-list-select").val().join(';'):"";
    var requestURL = "/batch";

    $("#resume-batch-panel").modal('hide');
    $("#hold-batch-running-modal").modal();

    var model = this.model;
    var requestData = {
      "ajax": "ajaxHoldBatch",
      "holdType": '0',
      "holdLevel": resumeLevel,
      "dataList": dataList,
      "flowList": blackList,
      "batchId": batchId
    };
    var successHandler = function (data) {
      $("#hold-batch-running-modal").modal("hide");
      if (data.error) {
        setTimeout(function () {
          alert(data.error);
        }, 50);
        return false;
      } else {
        batchTabView.handleBatchHoldViewLinkClick();
      }
    };
    $.post(requestURL, requestData, successHandler, "json");
  },

  loadBlackFlowData: function () {

    $("#resume-black-list-select").select2({
      placeholder: wtssI18n.view.blackFlowSelectTips,//默认文字提示
      multiple: true,
      width: 'resolve',
      //language: "zh-CN",
      tags: false,//允许手动添加
      //allowClear: true,//允许清空
      escapeMarkup: function (markup) { return markup; }, //自定义格式化防止XSS注入
      minimumInputLengt: 1,//最少输入多少字符后开始查询
      formatResult: function formatRepo (repo) { return repo.text; },//函数用来渲染结果
      formatSelection: function formatRepoSelection (repo) { return repo.text; },//函数用于呈现当前的选择
      ajax: {
        type: 'GET',
        url: "/batch",
        dataType: 'json',
        delay: 250,
        data: function (params) {
          var query = {
            ajax: "ajaxQueryBatchFlowList",
            search: params.term,
            page: params.page || 1,
            pageSize: 20
          }
          return query;
        },
        processResults: function (data, params) {
          params.page = params.page || 1;
          return {
            results: data.flowList,
            pagination: {
              more: (params.page * 20) < data.totalCount
            }
          }
        },
        cache: true
      },
      language: 'zh-CN'
    });
  },

  render: function () {
    $('#resume-level-select').val('0');
    $("#resume-level-select").trigger('change');
    $('#resume-batch-select').val(null);
    $("#resume-batch-select").trigger('change');
    $('#resume-data-list-select').val(null).trigger('change');
    $('#resume-black-list-select').val(null);
    this.loadBlackFlowData();
  }
});

$(function () {
  $("#hold-level-select").on('change', function (e) {
    var holdLevel = e.target.value;
    if(holdLevel>0){
      $('#dataListDiv').show();
    } else{
      $('#dataListDiv').hide();
    }
    if(holdLevel==3){
      $('#busPathListDiv').hide();
      $('#whiteListDiv').hide();
    } else{
      $('#busPathListDiv').show();
      $('#whiteListDiv').show();
    }
    $("#data-list-select").select2({
      placeholder: wtssI18n.view.rangeSelectTips,
      multiple: true,
      width: 'resolve',
      //language: "zh-CN",
      tags: false,//允许手动添加
      //allowClear: true,//允许清空
      escapeMarkup: function (markup) { return markup; }, //自定义格式化防止XSS注入
      minimumInputLengt: 1,//最少输入多少字符后开始查询
      formatResult: function formatRepo (repo) { return repo.text; },//函数用来渲染结果
      formatSelection: function formatRepoSelection (repo) { return repo.text; },//函数用于呈现当前的选择
      ajax: {
        type: 'GET',
        url: "/batch",
        dataType: 'json',
        delay: 250,
        data: function (params) {
          var query = {
            ajax: "ajaxGetHoldDataListByLevel",
            holdLevel: holdLevel,
            search: params.term,
            page: params.page || 1,
            pageSize: 20
          }
          return query;
        },
        processResults: function (data, params) {
          params.page = params.page || 1;
          return {
            results: data.dataList,
            pagination: {
              more: (params.page * 20) < data.totalCount
            }
          }
        },
        cache: true
      },
      language: 'zh-CN'
    });

  });

  $("#resume-level-select").on('change', function (e) {
    var resumeLevel = e.target.value;
    if(resumeLevel>0){
      $('#resumeDataListDiv').show();
    }else{
      $('#resumeDataListDiv').hide();
    }
    if(resumeLevel==3){
      $('#resumeBlackListDiv').hide();
    }else{
      $('#resumeBlackListDiv').show();
    }
    var requestURL = "/batch";
    var requestData = {
      "ajax": "ajaxGetBatchIdList",
      "holdLevel": resumeLevel?resumeLevel:'0'
    };
    var successHandler = function (data) {
      if (data.error) {
        alert(data.error);
      }else {
        $("#resume-batch-select").find("option:selected").text("");
        $("#resume-batch-select").empty();
        if (data.dataList) {
          var optionHtml = "";
          for (var i = 0; i < data.dataList.length; i++) {
            optionHtml += "<option value='" + data.dataList[i] + "'>" + data.dataList[i] + "</option>"
          }
          optionHtml = filterXSS(optionHtml, { 'whiteList': { 'option': ['value'] } })
          $("#resume-batch-select").append(optionHtml);
        }
        $("#resume-batch-select").selectpicker('refresh');
        $("#resume-batch-select").selectpicker('render');
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

  });

  $("#resume-batch-select").on('change', function (e) {
    var batchId = e.target.value;
    $("#resume-data-list-select").select2({
      placeholder: wtssI18n.view.rangeSelectTips,
      multiple: true,
      width: 'resolve',
      //language: "zh-CN",
      tags: false,//允许手动添加
      //allowClear: true,//允许清空
      escapeMarkup: function (markup) { return markup; }, //自定义格式化防止XSS注入
      minimumInputLengt: 1,//最少输入多少字符后开始查询
      formatResult: function formatRepo (repo) { return repo.text; },//函数用来渲染结果
      formatSelection: function formatRepoSelection (repo) { return repo.text; },//函数用于呈现当前的选择
      ajax: {
        type: 'GET',
        url: "/batch",
        dataType: 'json',
        delay: 250,
        data: function (params) {
          var query = {
            ajax: "ajaxGetHoldDataListByBatchId",
            batchId: batchId,
            search: params.term,
            page: params.page || 1,
            pageSize: 20
          }
          return query;
        },
        processResults: function (data, params) {
          params.page = params.page || 1;
          return {
            results: data.dataList,
            pagination: {
              more: (params.page * 20) < data.totalCount
            }
          }
        },
        cache: true
      },
      language: 'zh-CN'
    });

  });
});
