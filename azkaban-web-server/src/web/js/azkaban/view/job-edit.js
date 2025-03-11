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

var jobEditView;
azkaban.JobEditView = Backbone.View.extend({
    events: {
        "click": "closeEditingTarget",
        "click #set-btn": "handleSet",
        "click #cancel-btn": "handleCancel",
        "click #close-btn": "handleCancel",
        "click #add-btn": "handleAddRow",
        "click table .editable": "handleEditColumn",
        "click table .remove-btn": "handleRemoveColumn"
    },

    initialize: function(setting) {
        this.projectURL = "manager"
        this.generalParams = {}
        this.overrideParams = {}

        if (window.location.hash) {
            var hash = window.location.hash;
            if (hash == "#jobExecutions") {
                jobTabView.handleJobExecutionsClick();
            } else if (hash == "#jobParam") {
                if ("#page" == hash.substring(0, "#page".length)) {
                    var page = hash.substring("#page".length, hash.length);
                    console.log("page " + page);
                    jobTabView.handleJobExecutionsClick();
                    executionModel.set({ "page": parseInt(page) });
                } else {
                    selected = "jobParam";
                }
            }
            if (hash == "#jobParam") {
                // Redundant, but we may want to change the default.
                selected = "jobParam";
            }

        }
    },

    handleCancel: function(evt) {
        $('#job-edit-pane').hide();
        var tbl = document.getElementById("generalProps").tBodies[0];
        var rows = tbl.rows;
        var len = rows.length;
        for (var i = 0; i < len - 1; i++) {
            tbl.deleteRow(0);
        }
    },

    show: function(projectName, flowName, jobName) {
        this.projectName = projectName;
        this.flowName = flowName;
        this.jobName = jobName;

        var projectURL = this.projectURL

        $('#job-edit-pane').modal();

        var handleAddRow = this.handleAddRow;

        /*var overrideParams;
        var generalParams;
        this.overrideParams = overrideParams;
        this.generalParams = generalParams;*/
        var fetchJobInfo = {
            "project": this.projectName,
            "ajax": "fetchJobInfo",
            "flowName": this.flowName,
            "jobName": this.jobName
        };
        var mythis = this;
        var fetchJobSuccessHandler = function(data) {
            if (data.error) {
                alert(data.error);
                return;
            }
            document.getElementById('jobName').innerHTML = filterXSS(data.jobName);
            document.getElementById('jobType').innerHTML = filterXSS(data.jobType);
            var generalParams = data.generalParams;
            var overrideParams = data.overrideParams;

            /*for (var key in generalParams) {
              var row = handleAddRow();
              var td = $(row).find('span');
              $(td[1]).text(key);
              $(td[2]).text(generalParams[key]);
            }*/

            mythis.overrideParams = overrideParams;
            mythis.generalParams = generalParams;

            if (overrideParams && $(".editRow").length == 0) {
                for (var okey in overrideParams) {
                    if (okey != 'type' && okey != 'dependencies') {
                        var row = handleAddRow();
                        var td = $(row).find('span');
                        $(td[0]).text(okey);
                        $(td[1]).text(overrideParams[okey]);
                    }
                }
            }
        };

        $.get(projectURL, fetchJobInfo, fetchJobSuccessHandler, "json");
    },

    handleSet: function(evt) {
        this.closeEditingTarget(evt);
        var jobOverride = {};
        var editRows = $(".editRow");
        for (var i = 0; i < editRows.length; ++i) {
            var row = editRows[i];
            var td = $(row).find('span');
            var key = $(td[0]).text();
            var val = $(td[1]).text();

            if (key && key.length > 0) {
                jobOverride[key] = val;
            }
        }

        var overrideParams = this.overrideParams
        var generalParams = this.generalParams

        jobOverride['type'] = overrideParams['type']
        if ('dependencies' in overrideParams) {
            jobOverride['dependencies'] = overrideParams['dependencies']
        }

        var project = this.projectName
        var flowName = this.flowName
        var jobName = this.jobName

        var jobOverrideData = {
            project: project,
            flowName: flowName,
            jobName: jobName,
            ajax: "setJobOverrideProperty",
            jobOverride: jobOverride
        };

        var projectURL = this.projectURL
        var redirectURL = filterXSS(projectURL + '?project=' + project + '&flow=' + flowName +
            '&job=' + jobName);
        var jobOverrideSuccessHandler = function(data) {
            if (data.error) {
                alert(data.error);
            } else {
                window.location = redirectURL;
            }
        };

        $.get(projectURL, jobOverrideData, jobOverrideSuccessHandler, "json");
    },

    handleAddRow: function(evt) {
        var tr = document.createElement("tr");
        var tdName = document.createElement("td");
        $(tdName).addClass('property-key');
        var tdValue = document.createElement("td");

        var remove = document.createElement("div");
        $(remove).addClass("pull-right").addClass('remove-btn');
        var removeBtn = document.createElement("button");
        $(removeBtn).attr('type', 'button');
        $(removeBtn).addClass('btn').addClass('btn-xs').addClass('btn-danger');
        $(removeBtn).text('Delete');
        $(remove).append(removeBtn);

        var nameData = document.createElement("span");
        $(nameData).addClass("spanValue");
        var valueData = document.createElement("span");
        $(valueData).addClass("spanValue");

        $(tdName).append(nameData);
        $(tdName).addClass("editable");
        nameData.myparent = tdName;

        $(tdValue).append(valueData);
        $(tdValue).append(remove);
        $(tdValue).addClass("editable");
        $(tdValue).addClass("value");
        valueData.myparent = tdValue;

        $(tr).addClass("editRow");
        $(tr).append(tdName);
        $(tr).append(tdValue);

        $(tr).insertBefore("#addRow");
        return tr;
    },

    handleEditColumn: function(evt) {
        var curTarget = evt.currentTarget;
        if (this.editingTarget != curTarget) {
            this.closeEditingTarget(evt);

            var text = $(curTarget).children(".spanValue").text();
            $(curTarget).empty();

            var input = document.createElement("input");
            $(input).attr("type", "text");
            $(input).addClass("form-control").addClass("input-sm");
            $(input).val(text);

            $(curTarget).addClass("editing");
            $(curTarget).append(input);
            $(input).focus();
            var obj = this;
            $(input).keypress(function(evt) {
                if (evt.which == 13) {
                    obj.closeEditingTarget(evt);
                }
            });
            this.editingTarget = curTarget;
        }

        evt.preventDefault();
        evt.stopPropagation();
    },

    handleRemoveColumn: function(evt) {
        var curTarget = evt.currentTarget;
        // Should be the table
        var row = curTarget.parentElement.parentElement;
        $(row).remove();
    },

    closeEditingTarget: function(evt) {
        if (this.editingTarget == null ||
            this.editingTarget == evt.target ||
            this.editingTarget == evt.target.myparent) {
            return;
        }
        var input = $(this.editingTarget).children("input")[0];
        var text = $(input).val();
        $(input).remove();

        var valueData = document.createElement("span");
        $(valueData).addClass("spanValue");
        $(valueData).text(text);

        if ($(this.editingTarget).hasClass("value")) {
            var remove = document.createElement("div");
            $(remove).addClass("pull-right").addClass('remove-btn');
            var removeBtn = document.createElement("button");
            $(removeBtn).attr('type', 'button');
            $(removeBtn).addClass('btn').addClass('btn-xs').addClass('btn-danger');
            $(removeBtn).text('Delete');
            $(remove).append(removeBtn);
            $(this.editingTarget).append(remove);
        }

        $(this.editingTarget).removeClass("editing");
        $(this.editingTarget).append(valueData);
        valueData.myparent = this.editingTarget;
        this.editingTarget = null;
    },
    initProjectBusinessForm: function () {
        $("#bus-type-first-select").empty();
        $("#bus-type-second-select").empty();
        $("#subsystem-select").empty();
        $("#bus-path-select").empty();
        $("#batch-group-select").empty();
        $("#bus-domain-select").empty();
    },
});

function handleJobBusiness() {
    jobEditView.initProjectBusinessForm();
    $('#merge-business-panel').modal();
    $("#merge-business-info-msg").hide();
    mergeJobBusinessView.render();
    mergeJobBusinessView.loadBusinessData();
};

var mergeJobBusinessView;
azkaban.MergeJobBusinessView = Backbone.View.extend({
    events: {
        "click #business-merge-btn": "handleMergeJobBusiness"
    },

    initialize: function(settings) {
        $("#merge-business-error-msg").hide();
        $("#merge-business-info-msg").hide();
        $('#plan-start-time').datetimepicker({
            format: 'HH:mm'
        });
        $('#plan-finish-time').datetimepicker({
            format: 'HH:mm'
        });
        $('#last-start-time').datetimepicker({
            format: 'HH:mm'
        });
        $('#last-finish-time').datetimepicker({
            format: 'HH:mm'
        });
    },

    handleMergeJobBusiness: function(evt) {
        var busTypeFirst = $("#bus-type-first-select").val();
        var busTypeFirstDesc = $("#bus-type-first-select option[value=" + busTypeFirst + "]").text();
        var busTypeSecond = $("#bus-type-second-select").val();
        var busTypeSecondDesc = $("#bus-type-second-select option[value=" + busTypeSecond + "]").text();
        var busDesc = $("#bus-desc").val();
        var subsystem = $("#subsystem-select").val();
        var subsystemDesc = $("#subsystem-select option[value=" + subsystem + "]").text();
        var busResLvl = $("#bus-res-lvl-select").val();
        var busPath = $("#bus-path-select").val();
        var busPathDesc = $("#bus-path-select option[value=" + busPath + "]").text();
        //    var batchTimeQuat = $("#batch-time-quat").val();
        //    var busErrInf = $("#bus-err-inf").val();
        var devDept = $("#bus-dev-dept-select").val();
        var devDeptDesc = devDept ? $("#bus-dev-dept-select option[value=" + devDept + "]").text() : '';
        var opsDept = $("#bus-ops-dept-select").val();
        var opsDeptDesc = opsDept ?  $("#bus-ops-dept-select option[value=" + opsDept + "]").text() : '';

        var scanPartitionNum = $("#scan-partition-num").val();
        var scanDataSize = $("#scan-data-size").val();
        //    var upperDep = $("#upper-dep").val();
        //    var lowerDep = $("#lower-dep").val();
        var batchGroup = $("#batch-group-select").val();
        var batchGroupDesc = $("#batch-group-select option[value=" + batchGroup + "]").text();
        var busDomain = $("#bus-domain-select").val();
        //    var earliestStartTime = $("#earliest-start-time").val();
        //    var latestEndTime = $("#latest-end-time").val();
        //    var relatedProduct = $("#related_product").val();
        var planStartTime = $("#plan-start-time").val();
        var planFinishTime = $("#plan-finish-time").val();
        var lastStartTime = $("#last-start-time").val();
        var lastFinishTime = $("#last-finish-time").val();
        var itsmNo = $("#itsm-number").val()
        // var alertLevel = $("#alert-level").val();
        // var dcnNumber = $("#dcn-number").val();
        // var imsUpdater = $("#ims-updater-select").val() ? $("#ims-updater-select").val().join(';') : "";
        // var imsRemark = $("#ims-remark").val();
        if (!busDomain || !subsystem || !busResLvl || !planStartTime || !planFinishTime || !lastStartTime || !lastStartTime || !lastFinishTime || !scanPartitionNum|| !scanDataSize) {
            messageBox.show('有必填项未填', 'info');
            return;
        }
        var requestURL = "/manager";

        var requestData = {
            "projectId": projectId,
            "flowId": flowId,
            "jobId": jobId,
            "ajax": "mergeFlowBusiness",
            "busTypeFirst": busTypeFirst,
            "busTypeFirstDesc": busTypeFirstDesc,
            "busTypeSecond": busTypeSecond,
            "busTypeSecondDesc": busTypeSecondDesc,
            "busDesc": busDesc,
            "subsystem": subsystem,
            "subsystemDesc": subsystemDesc,
            "busResLvl": busResLvl,
            "busPath": busPath,
            "busPathDesc": busPathDesc,
            //      "batchTimeQuat": batchTimeQuat,
            //      "busErrInf": busErrInf,
            "devDept": devDept,
            "devDeptDesc": devDeptDesc,
            "opsDept": opsDept,
            "opsDeptDesc": opsDeptDesc,
            "scanPartitionNum":  scanPartitionNum,
            "scanDataSize":  scanDataSize,
            //      "upperDep": upperDep,
            //      "lowerDep": lowerDep,
            "batchGroup": batchGroup,
            "batchGroupDesc": batchGroupDesc,
            "busDomain": busDomain,
            //      "earliestStartTime": earliestStartTime,
            //      "latestEndTime": latestEndTime,
            "planStartTime": planStartTime,
            "planFinishTime": planFinishTime,
            "lastStartTime": lastStartTime,
            "lastFinishTime": lastFinishTime,
            "itsmNo": itsmNo,
            // "alertLevel": alertLevel,
            // "dcnNumber": dcnNumber,
            // "imsUpdater": imsUpdater,
            // "imsRemark": imsRemark
            //      "relatedProduct": relatedProduct
        };
        $('#business-merge-btn').prop('disabled', true)
        var successHandler = function(data) {
            if (data.errorMsg || data.error && !data.itsmNo) {
                $("#merge-business-error-msg").show();
                $("#merge-business-error-msg").text(data.errorMsg || data.error);
                $('#business-merge-btn').prop('disabled', false)
                return false;
            } else {
                console.log(data)
                alert(data.requestInfo);
                $('#merge-business-panel').modal("hide");
            }
            $('#business-merge-btn').prop('disabled', false)
            $("#merge-business-error-msg").hide();
        };
        $.post(requestURL, requestData, successHandler, "json");
    },
    handleOptionBusinessData: function (projectBusiness, key) {
        var label = key === 'busDomain' ? key : key + 'Desc'
        return projectBusiness && projectBusiness[key] ? {text: projectBusiness[label], id: projectBusiness[key]} : '';
    },
    loadBusinessData: function() {

        var requestURL = "/manager";
        var that = this;
        var requestData = {
            "ajax": "getFlowBusiness",
            "projectId": projectId,
            "flowId": flowId,
            "jobId": jobId,
            "isLoaded": isCmdbLoaded
        };
        var successHandler = function(data) {
            if (data.errorMsg) {
                $("#merge-business-error-msg").show();
                $("#merge-business-error-msg").text(data.errorMsg);
                return false;
            } else {
                fetchCmdbData("bus-type-first-select", 'wb_product_category', 'category_id', 'pro_category', that.handleOptionBusinessData(data.projectBusiness,'busTypeFirst'));
                fetchCmdbData("bus-type-second-select", 'wb_product', 'pro_id', 'pro_name', that.handleOptionBusinessData(data.projectBusiness,'busTypeSecond'));
                fetchCmdbData("subsystem-select", 'wb_subsystem', 'subsystem_id', 'subsystem_name', that.handleOptionBusinessData(data.projectBusiness,'subsystem'));
                fetchCmdbData("bus-path-select", 'wb_batch_critical_path', 'id', 'name', that.handleOptionBusinessData(data.projectBusiness,'busPath'));
                fetchCmdbData("batch-group-select", 'wb_batch_group', 'group_id', 'group_name', that.handleOptionBusinessData(data.projectBusiness,'batchGroup'));
                fetchCmdbData("bus-domain-select", 'subsystem_app_instance', 'appdomain_cnname', 'appdomain_cnname', that.handleOptionBusinessData(data.projectBusiness,'busDomain'));

                //部门
                if (data.busDeptSelectList) {
                    //每次新增option,需要清空select,避免造成重复数据
                    $("#bus-dev-dept-select").find("option:selected").text("");
                    $("#bus-dev-dept-select").empty();
                    //运维部门
                    $("#bus-ops-dept-select").find("option:selected").text("");
                    $("#bus-ops-dept-select").empty();
                    var optionHtml = "";
                    for (var i = 0; i < data.busDeptSelectList.length; i++) {
                        optionHtml += "<option value='" + data.busDeptSelectList[i].dpId + "'>" + data.busDeptSelectList[i].dpChName + "</option>"
                    }
                    optionHtml = filterXSS(optionHtml, { 'whiteList': { 'option': ['value'] } })
                    $('#bus-dev-dept-select').append(optionHtml);
                    $('#bus-ops-dept-select').append(optionHtml);

                    //要以编程方式更新JavaScript的选择，首先操作选择，然后使用refresh方法更新UI以匹配新状态。 在删除或添加选项时，或通过JavaScript禁用/启用选择时，这是必需的。
                    $('#bus-dev-dept-select').selectpicker('refresh');
                    $('#bus-ops-dept-select').selectpicker('refresh');
                    //render方法强制重新渲染引导程序 - 选择ui,如果当您编程时更改任何相关值而影响元素布局，这将非常有用。
                    $('#bus-dev-dept-select').selectpicker('render');
                    $('#bus-ops-dept-select').selectpicker('render');
                }

                // if (data.imsUpdaterList) {
                //     $("#ims-updater-select").find("option:selected").text("");
                //     $("#ims-updater-select").empty();
                //     var optionHtml = "";
                //     for (var i = 0; i < data.imsUpdaterList.length; i++) {
                //         optionHtml += "<option value='" + data.imsUpdaterList[i].username + "'>" + data.imsUpdaterList[i].fullName + "</option>"
                //     }
                //     optionHtml = filterXSS(optionHtml, { 'whiteList': { 'option': ['value'] } })
                //     $('#ims-updater-select').append(optionHtml);
                //     $('#ims-updater-select').selectpicker('refresh');
                //     $('#ims-updater-select').selectpicker('render');
                // }

                isCmdbLoaded = true;

                if (data.projectBusiness) {
                    $("#bus-desc").val(data.projectBusiness.busDesc);
                    $("#bus-res-lvl-select").val(data.projectBusiness.busResLvl);
                    //          $("#batch-time-quat").val(data.projectBusiness.batchTimeQuat);
                    //          $("#bus-err-inf").val(data.projectBusiness.busErrInf);
                    $("#bus-dev-dept-select").val(data.projectBusiness.devDept);
                    $("#bus-ops-dept-select").val(data.projectBusiness.opsDept);
                    $("#scan-partition-num").val(data.projectBusiness.scanPartitionNum);
                    $("#scan-data-size").val(data.projectBusiness.scanDataSize);
                    //          $("#upper-dep").val(data.projectBusiness.upperDep);
                    //          $("#lower-dep").val(data.projectBusiness.lowerDep);
                    $("#earliest-start-time").val(data.projectBusiness.earliestStartTime);
                    $("#latest-end-time").val(data.projectBusiness.latestEndTime);
                    //          $("#related_product").val(data.projectBusiness.relatedProduct);
                    $("#plan-start-time").val(data.projectBusiness.planStartTime);
                    $("#plan-finish-time").val(data.projectBusiness.planFinishTime);
                    $("#last-start-time").val(data.projectBusiness.lastStartTime);
                    $("#last-finish-time").val(data.projectBusiness.lastFinishTime);
                    $("#itsm-number").val(data.projectBusiness.itsmNo);
                    if(data.projectBusiness.itsmNo){
                        $("#merge-business-info-msg").show();
                        $("#merge-business-info-msg").text(`上一次设置的审批单号为：${data.projectBusiness.itsmNo}`)
                    }
                    // $("#alert-level").val(data.projectBusiness.alertLevel);
                    // $("#dcn-number").val(data.projectBusiness.dcnNumber);
                    // $("#ims-updater-select").val(data.projectBusiness.imsUpdater ? data.projectBusiness.imsUpdater.split(';') : null);
                    // $("#ims-remark").val(data.projectBusiness.imsRemark);
                } else {
                    $("#bus-desc").val("");
                    $("#bus-res-lvl-select").val("");
                    //          $("#batch-time-quat").val("");
                    //          $("#bus-err-inf").val("");
                    $("#bus-dev-dept-select").val("");
                    $("#bus-ops-dept-select").val("");
                    //          $("#upper-dep").val("");
                    //          $("#lower-dep").val("");
                    $("#earliest-start-time").val("");
                    $("#latest-end-time").val("");
                    //          $("#related_product").val("");
                    $("#plan-start-time").val("");
                    $("#plan-finish-time").val("");
                    $("#last-start-time").val("");
                    $("#last-finish-time").val("");
                    $("#scan-partition-num").val("");
                    $("#scan-data-size").val("");
                    $("#itsm-number").val("");
                    // $("#alert-level").val("");
                    // $("#dcn-number").val("");
                    // $("#ims-updater-select").val(null);
                    // $("#ims-remark").val("");
                }

                //要以编程方式更新JavaScript的选择，首先操作选择，然后使用refresh方法更新UI以匹配新状态。 在删除或添加选项时，或通过JavaScript禁用/启用选择时，这是必需的。
                $('#bus-dev-dept-select').selectpicker('refresh');
                $('#bus-ops-dept-select').selectpicker('refresh');
                $('#bus-res-lvl-select').selectpicker('refresh');
                // $('#alert-level').selectpicker('refresh');
                // $('#ims-updater-select').selectpicker('refresh');
            }
        };
        $.get(requestURL, requestData, successHandler, "json");

    },
    render: function() {
        $("#merge-business-error-msg").hide();
    }
});

var executionModel;
azkaban.ExecutionModel = Backbone.Model.extend({});

//关联数据
//关联数据
function getRenderLinkTableFun (elDomId){
    return {
        initialize: function(settings) {
            changePageSizeSelectValue(elDomId, [10, 20, 50]);
            this.model.bind('change:page', this.handlePageChange, this);
            this.model.set({ page: 1, pageSize: 10 });
            this.createResize();
        },
        render: function() {
            console.log("render");
            // Render page selections
            var tableBody = $("#" + this.model.get('tbTodyId'));
            tableBody.empty();

            var tableList = this.model.get("tableList");
            for (var i = 0; i < tableList.length; ++i) {
                var row = document.createElement("tr");

                //组装数字行
                var tdNum = document.createElement("td");
                $(tdNum).text(i + 1);
                $(tdNum).attr("class", "tb-name");
                row.appendChild(tdNum);

                //组装数据源类型
                var dtdSourceType = document.createElement("td");
                $(dtdSourceType).text(tableList[i].dataSourceType);
                row.appendChild(dtdSourceType);

                //组装集群
                var tdCluster = document.createElement("td");
                $(tdCluster).text(tableList[i].cluster);
                row.appendChild(tdCluster);

                //组装数据库
                var tdDatabase = document.createElement("td");
                $(tdDatabase).text(tableList[i].database);
                row.appendChild(tdDatabase);

                //组装数据表
                var tdTable = document.createElement("td");
                $(tdTable).text(tableList[i].table);
                row.appendChild(tdTable);

                //组装子系统
                var tdSubsystem = document.createElement("td");
                $(tdSubsystem).text(tableList[i].subsystem);
                row.appendChild(tdSubsystem);

                //组装开发部门
                var tdDevelopDepartment = document.createElement("td");
                $(tdDevelopDepartment).text(tableList[i].developDepartment);
                row.appendChild(tdDevelopDepartment);

                //组装开发负责人
                var tdDeveloper = document.createElement("td");
                $(tdDeveloper).text(tableList[i].developer);
                row.appendChild(tdDeveloper);
                tableBody.append(row);

                this.renderPagination();

                // $("#executingJobs").trigger("update");
                // $("#executingJobs").trigger("sorton", "");
            }
        },
        ...commonPaginationFun(),
        handlePageChange: function(evt) {
            var start = this.model.get("page") - 1;
            var pageSize = this.model.get("pageSize");
            var requestURL = "/manager";

            var that = this;
            var requestData = {
                "ajax": "getLineageBusiness",
                "page": start,
                "pageSize": pageSize,
                "pageNum": this.model.get("page"),
                searchDataType: this.model.get('tbTodyId') === 'jobLinkInputDataBody' ? 'IN' : 'OUT',
                project: projectName,
                flowName: flowId,
                jobName: jobName
            }

            var successHandler = function(data) {
                if (data.code === "200") {
                    that.model.set({
                        "tableList": data.lineageBusinessList ? data.lineageBusinessList : [],
                        "total": data.lineageBusinessListSize
                    });
                    if (data.lineageBusinessList) {
                        that.render();
                    } else {
                        that.renderPagination();
                    }
                    $('#link-data-error-msg').hide();
                } else {
                    var error = data.code === "401" || data.code === "403" ? data.jobCode + ' 节点暂无权限，请到DMS增加工作流权限' : data.error
                    $('#link-data-error-msg').html(error).show();
                };

            }
            $.get(requestURL, requestData, successHandler, "json");
        }
    }
};

var linkInputModel;
azkaban.LinkInputModel = Backbone.Model.extend();
var linkOutModel;
azkaban.LinkOutModel = Backbone.Model.extend();
var jobLinkInputDataView
var jobLinkOutDataView
azkaban.LinkedInputDataView = Backbone.View.extend({
    events: {
        "click #inputPageTable .projectPageSelection  li": "handleChangePageSelection",
        "change #inputPageTable .pageSizeSelect": "handlePageSizeSelection",
        "click #inputPageTable .pageNumJump": "handlePageNumJump",
    },
    ...getRenderLinkTableFun("inputPageTable"),
})
azkaban.LinkedOutDataView = Backbone.View.extend({
    events: {
        "click #outPageTable .projectPageSelection  li": "handleChangePageSelection",
        "change #outPageTable .pageSizeSelect": "handlePageSizeSelection",
        "click #outPageTable .pageNumJump": "handlePageNumJump",
    },
    ...getRenderLinkTableFun("outPageTable"),
})
// var executionsTimeGraphView;

$(function() {

    // 在切换选项卡之前创建模型
    executionModel = new azkaban.ExecutionModel();
    jobExecutionsView = new azkaban.JobExecutionsView({
        el: $('#jobExecutionsView'),
        model: executionModel
    });

    var selected = "";
    jobTabView = new azkaban.jobTabView({
        el: $('#headertabs'),
        selectedView: selected
    });

    jobEditView = new azkaban.JobEditView({
        el: $('#job-edit-pane')
    });

    mergeJobBusinessView = new azkaban.MergeJobBusinessView({
        el: $('#merge-business-panel')
    });

    executionsTimeGraphView = new azkaban.TimeGraphView({
        el: $('#timeGraph'),
        model: executionModel,
        modelField: 'jobExecutions'
    });

    jobParamView = new azkaban.JobParamView({
        el: $('#job-param-table'),
        model: executionModel
    });

    //关联数据
    //输入数据
    linkInputModel = new azkaban.LinkInputModel({
        tbTodyId: "jobLinkInputDataBody",
        elDomId: "inputPageTable"
    });
    jobLinkInputDataView = new azkaban.LinkedInputDataView({
            el: $('#jobLinkInputBox'),
            model: linkInputModel,
        })
        //输出数据
    linkOutModel = new azkaban.LinkOutModel({
        tbTodyId: "jobLinkoutputDataBody",
        elDomId: "outPageTable"
    });
    jobLinkOutDataView = new azkaban.LinkedOutDataView({
        el: $('#jobLinkOutBox'),
        model: linkOutModel,
    })
});

//Tab页处理 切换Tab效果和初始化
var jobTabView;
azkaban.jobTabView = Backbone.View.extend({
    events: {
        "click #jobParam": "handleJobParamClick",
        "click #jobExecutionsHistory": "handleJobExecutionsClick",
        "click #applyJobLinkedData": "handleApplyJobLinkedClick"
    },

    initialize: function(settings) {
        var selectedView = settings.selectedView;
        if (selectedView == "jobExecutions") {
            this.handleJobExecutionsClick();
        } else if (selectedView == "linkJobData") {
            //关联数据
            this.handleApplyJobLinkedClick();
        } else {
            this.handleJobParamClick();
        }
    },

    render: function() {
        console.log("render graph");
    },

    handleJobParamClick: function() {
        $("#jobParam").addClass("active");
        $('#jobExecutionsHistory').removeClass('active');
        $('#applyJobLinkedData').removeClass('active');

        $("#jobParamView").show();
        $('#applyJobLinkedView').hide();
        $('#jobExecutionsView').hide();
    },

    handleJobExecutionsClick: function() {
        $("#jobParam").removeClass("active");
        $("#jobExecutionsHistory").addClass("active");
        $('#applyJobLinkedData').removeClass('active');

        $("#jobParamView").hide();
        $('#applyJobLinkedView').hide();
        $("#jobExecutionsView").show();
        executionModel.trigger("change:view");
    },
    handleApplyJobLinkedClick() {
        $("#jobParam").removeClass("active");
        $("#jobExecutionsHistory").removeClass("active");
        $('#applyJobLinkedData').addClass('active');

        $("#jobParamView").hide();
        $('#applyJobLinkedView').show();
        $("#jobExecutionsView").hide();
    }
});

//Job历史执行页面处理方法 组装表格和翻页处理
var jobExecutionsView;
azkaban.JobExecutionsView = Backbone.View.extend({
    events: {
        "click #pageSelection .projectPageSelection  li": "handleChangePageSelection",
        "change #pageSelection .pageSizeSelect": "handlePageSizeSelection",
        "click #pageSelection .pageNumJump": "handlePageNumJump",
    },

    initialize: function(settings) {
        this.model.set('elDomId','pageSelection');
        this.model.bind('change:view', this.handleChangeView, this);
        this.model.bind('render', this.render, this);

        this.model.bind('change:page', this.handlePageChange, this);
        this.model.set({ page: 1, pageSize: 20 });
        this.createResize();
    },

    render: function(evt) {
        console.log("render");
        // Render page selections
        var tbody = $("#execTableBody");
        tbody.empty();

        var executions = this.model.get("jobExecutions");
        for (var i = 0; i < executions.length; ++i) {
            var row = document.createElement("tr");

            //组装执行ID 数据行
            var tdId = document.createElement("td");
            var execA = document.createElement("a");
            $(execA).attr("href", filterXSS("/executor?execid=" + executions[i].execId));
            $(execA).text(executions[i].execId);
            tdId.appendChild(execA);
            row.appendChild(tdId);

            //组装Job行
            // var tdJob = document.createElement("td");
            // var jobIdA = document.createElement("a");
            // $(jobIdA).attr("href",  "/manager?project=" + projectName + "&flow=" + executions[i].flowId + "&job=" + executions[i].jobId);
            // $(jobIdA).text(executions[i].jobId);
            // tdJob.appendChild(jobIdA);
            // row.appendChild(tdJob);

            //组装Flow行
            var tdFlow = document.createElement("td");
            var flowA = document.createElement("a");
            var flowName = executions[i].flowId.split(":").slice(-1);
            $(flowA).attr("href", filterXSS("/manager?project=" + projectName + "&flow=" + flowName));
            $(flowA).text(executions[i].flowId);
            $(flowA).css({
                "max-width": "500px",
                "display": "inline-block",
                "word-break": "break-all"
            })
            tdFlow.appendChild(flowA);
            row.appendChild(tdFlow);

            //组装开始时间行
            var startTime = "-";
            if (executions[i].startTime != -1) {
                var startDateTime = new Date(executions[i].startTime);
                startTime = getProjectModifyDateFormat(startDateTime);
            }

            var tdStartTime = document.createElement("td");
            $(tdStartTime).text(startTime);
            row.appendChild(tdStartTime);

            //组装结束时间行
            var endTime = "-";
            var lastTime = executions[i].endTime;
            if (executions[i].endTime != -1) {
                var endDateTime = new Date(executions[i].endTime);
                endTime = getProjectModifyDateFormat(endDateTime);
            } else {
                lastTime = (new Date()).getTime();
            }

            var tdEndTime = document.createElement("td");
            $(tdEndTime).text(endTime);
            row.appendChild(tdEndTime);

            //组装执行日期
            var runDate = "-";
            if (executions[i].runDate != -1) {
                var execTime = new Date(executions[i].runDate);
                runDate = getRecoverRunDateFormat(execTime);
            }

            var tdRunDate = document.createElement("td");
            $(tdRunDate).text(runDate);
            row.appendChild(tdRunDate);

            //执行时长
            var tdElapsed = document.createElement("td");
            $(tdElapsed).text(getDuration(executions[i].startTime, lastTime));
            row.appendChild(tdElapsed);



            //组装执行状态行
            var tdStatus = document.createElement("td");
            var status = document.createElement("div");
            $(status).addClass("status");
            $(status).addClass(executions[i].status);
            $(status).text(statusStringMap[executions[i].status]);
            tdStatus.appendChild(status);
            row.appendChild(tdStatus);

            //组装日志行
            var tdLog = document.createElement("td");
            var logA = document.createElement("a");
            //http://127.0.0.1:8290/executor?execid=216859&job=subflow:sub1&attempt=0
            var jobPath = executions[i].flowId.split(",").slice(1).map(function(a) { return a.split(":")[0]; });
            jobPath.push(executions[i].jobId);
            jobPath = jobPath.join(":");
            $(logA).attr("href", filterXSS("/executor?execid=" + executions[i].execId + "&job=" + jobPath + "&attempt=0"));
            $(logA).text(wtssI18n.view.log);
            tdLog.appendChild(logA);
            row.appendChild(tdLog);

            tbody.append(row);
        }

        this.renderPagination(evt);
    },
    ...commonPaginationFun(),
    handlePageChange: function(evt) {
        var page = this.model.get("page") - 1;
        var pageSize = this.model.get("pageSize");
        var requestURL = "/manager";

        var model = this.model;
        var requestData = {
            "project": projectName,
            "flow": flowId,
            "job": jobName,
            "ajax": "fetchJobExecutionsHistory",
            "start": page * pageSize,
            "length": pageSize
        };
        var successHandler = function(data) {
            model.set({
                "jobExecutions": data.jobExecutions,
                "total": data.total
            });
            model.trigger("render");
        };
        $.get(requestURL, requestData, successHandler, "json");
    }
});


//Job文件参数页面处理方法 组装表格和翻页处理
var jobParamView;
azkaban.JobParamView = Backbone.View.extend({
    events: {},

    initialize: function(settings) {
        this.showJobParam();
        //this.assemblyJobParamTable()
    },

    showJobParam: function() {

        // this.projectName = projectName;
        // this.flowName = flowId;
        // this.jobName = jobName;

        var projectURL = this.projectURL
        var model = this.model;
        var getJobParamDataQuery = {
            "ajax": "getJobParamData",
            "project": projectName,
            "flowName": flowId,
            "jobName": jobName
        };
        var mythis = this;
        var getJobParamDataSuccessHandler = function(data) {
            if (data.error) {
                alert(data.error);
                return;
            }
            var jobParamData = data.jobParamData;
            // model.set({
            //   "jobParamData": data.jobParamData,
            // });
            mythis.assemblyJobParamTable(jobParamData)
        };

        $.get(projectURL, getJobParamDataQuery, getJobParamDataSuccessHandler, "json");

    },

    assemblyJobParamTable: function(jobParamData) {
        console.log("assemblyJobParamTable");
        // Render page selections
        var tbody = $("#job-param-tbody");
        tbody.empty();
        //var jobParamData = jobParamData;
        //var jobParamData = this.model.get("jobParamData");

        for (var i = 0; i < jobParamData.length; ++i) {
            var row = document.createElement("tr");

            //组装执行参数名行
            var tdJobName = document.createElement("td");
            $(tdJobName).text(jobParamData[i].paramName);
            row.appendChild(tdJobName);

            //组装参数值行
            var tdJobValue = document.createElement("td");
            $(tdJobValue).text(jobParamData[i].paramValue);
            row.appendChild(tdJobValue);

            if (jobParamData[i].paramName == "type" && jobParamData[i].paramValue == "datachecker") {
                $('#job-param-notice').append(filterXSS(jobParamData[i].paramNotice));
            }

            tbody.append(row);
        }

    },


});