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

var TIMESTAMP_LENGTH = 13;

var getDuration = function(startMs, endMs) {
    if (startMs) {
        if (startMs == -1) {
            return "-";
        }
        if (endMs == null || endMs < startMs) {
            return "-";
        }

        var diff = endMs - startMs;
        return formatDuration(diff, false);
    }

    return "-";
}

var formatDuration = function(duration, millisecSig) {
    var diff = duration;
    var seconds = Math.floor(diff / 1000);

    if (seconds < 60) {
        if (millisecSig) {
            return (diff / 1000).toFixed(millisecSig) + " s";
        } else {
            return seconds + " sec";
        }
    }

    var mins = Math.floor(seconds / 60);
    seconds = seconds % 60;
    if (mins < 60) {
        return mins + "m " + seconds + "s";
    }

    var hours = Math.floor(mins / 60);
    mins = mins % 60;
    if (hours < 24) {
        return hours + "h " + mins + "m " + seconds + "s";
    }

    var days = Math.floor(hours / 24);
    hours = hours % 24;

    return days + "d " + hours + "h " + mins + "m";
}

var getDateFormat = function(date) {
    var year = date.getFullYear();
    var month = getTwoDigitStr(date.getMonth() + 1);
    var day = getTwoDigitStr(date.getDate());

    var hours = getTwoDigitStr(date.getHours());
    var minutes = getTwoDigitStr(date.getMinutes());
    var second = getTwoDigitStr(date.getSeconds());

    var datestring = year + "-" + month + "-" + day + "  " + hours + ":" +
        minutes + " " + second + "s";
    return datestring;
}

var getHourMinSec = function(date) {
    var hours = getTwoDigitStr(date.getHours());
    var minutes = getTwoDigitStr(date.getMinutes());
    var second = getTwoDigitStr(date.getSeconds());

    var timestring = hours + ":" + minutes + " " + second + "s";
    return timestring;
}

var getTwoDigitStr = function(value) {
    if (value < 10) {
        return "0" + value;
    }

    return value;
}
 // 设置子节点disabled
function setSubflowNodeDisabled (data) {
    for (let i = 0; i < data.nodes.length; ++i) {
        let node = data.nodes[i];
        node.disabled = true;
        if (node.type=== "flow") {
            setSubflowNodeDisabled(node);
        }
    }
}
// Verify if a cron String meets the requirement of Quartz Cron Expression.
var validateQuartzStr = function(str) {
    var res = str.split(" "),
        len = res.length;

    // A valid Quartz Cron Expression should have 6 or 7 fields.
    if (len < 6 || len >= 8) {
        return "NUM_FIELDS_ERROR";
    }

    // Quartz currently doesn't support specifying both a day-of-week and a day-of-month value
    // (you must currently use the ‘?’ character in one of these fields).
    // http://www.quartz-scheduler.org/documentation/quartz-2.x/tutorials/crontrigger.html#notes
    if (res[3] != '?' && res[5] != '?') {
        return "DOW_DOM_STAR_ERROR";
    }

    //valid string
    return "VALID";
}

// Users enter values 1-7 for day of week, but UnixCronSyntax requires
// day of week to be values 0-6. However, when using "#" syntax, we
// do not want to apply the modulo operation to the number following "#"
var modifyStrToUnixCronSyntax = function(str) {
    var res = str.split("#");
    res[0] = res[0].replace(/[0-7]/g, function upperToHyphenLower(match) {
        return (parseInt(match) + 6) % 7;
    });
    return res.join("#");
}

// Unix Cron use 0-6 as Sun--Sat, but Quartz use 1-7. Due to later.js only supporting Unix Cron, we have to make this transition.
// The detailed Unix Cron Syntax: https://en.wikipedia.org/wiki/Cron
// The input is a 5 field string (without year) or 6 field String (with year).
var transformFromQuartzToUnixCron = function(str) {
        var res = str.split(" ");

        // If the cron doesn't include year field
        if (res.length == 5) {
            res[res.length - 1] = modifyStrToUnixCronSyntax(res[res.length - 1]);
        } // If the cron Str does include year field
        else if (res.length == 6) {
            res[res.length - 2] = modifyStrToUnixCronSyntax(res[res.length - 2]);
        }

        return res.join(" ");
    }
    //历史重跑日期数据格式化
var getRecoverDateFormat = function(date) {
        var year = date.getFullYear();
        var month = getTwoDigitStr(date.getMonth() + 1);
        var day = getTwoDigitStr(date.getDate());

        var hours = getTwoDigitStr(date.getHours());
        var minutes = getTwoDigitStr(date.getMinutes());
        var second = getTwoDigitStr(date.getSeconds());

        var datestring = year + "/" + month + "/" + day;
        return datestring;
    }
    //历史重跑运行日期数据格式化
var getRecoverRunDateFormat = function(date) {
        var year = date.getFullYear();
        var month = getTwoDigitStr(date.getMonth() + 1);
        var day = getTwoDigitStr(date.getDate());

        var hours = getTwoDigitStr(date.getHours());
        var minutes = getTwoDigitStr(date.getMinutes());
        var second = getTwoDigitStr(date.getSeconds());

        var datestring = year + "" + month + "" + day;
        return datestring;
    }
    //项目修改日期数据格式化
var getProjectModifyDateFormat = function(date) {
    var year = date.getFullYear();
    var month = getTwoDigitStr(date.getMonth() + 1);
    var day = getTwoDigitStr(date.getDate());

    var hours = getTwoDigitStr(date.getHours());
    var minutes = getTwoDigitStr(date.getMinutes());
    var second = getTwoDigitStr(date.getSeconds());

    var datestring = year + "-" + month + "-" + day + " " + hours + ":" + minutes + ":" + second;
    return datestring;
}

var getDifftime = function(startTime, endTime) {
    var startDate = new Date(startTime);
    var endDate = new Date(endTime);
    var nowDate = new Date();
    var difftime = endDate - startDate;


    if (startTime == -1) {
        return "-";
    }

    var durationMS;
    if (endTime == -1) {
        durationMS = nowDate - startTime;
    } else {
        durationMS = endTime - startTime;
    }

    var seconds = Math.floor(durationMS / 1000);
    if (seconds < 60) {
        return seconds + " sec";
    }

    var minutes = Math.floor(seconds / 60);
    seconds %= 60;
    if (minutes < 60) {
        return minutes + "m " + seconds + "s";
    }

    var hours = Math.floor(minutes / 60);
    minutes %= 60;
    if (hours < 24) {
        return hours + "h " + minutes + "m " + seconds + "s";
    }

    var days = Math.floor(hours / 24);
    hours %= 24;
    return days + "d " + hours + "h " + minutes + "m";

}

function dateFormat(fmt, date) {
    let ret;
    const opt = {
        "Y+": date.getFullYear().toString(), // 年
        "m+": (date.getMonth() + 1).toString(), // 月
        "d+": date.getDate().toString(), // 日
        "H+": date.getHours().toString(), // 时
        "M+": date.getMinutes().toString(), // 分
        "S+": date.getSeconds().toString() // 秒
            // 有其他格式化字符需求可以继续添加，必须转化成字符串
    };
    for (let k in opt) {
        ret = new RegExp("(" + k + ")").exec(fmt);
        if (ret) {
            fmt = fmt.replace(ret[1], (ret[1].length == 1) ? (opt[k]) : (opt[k].padStart(ret[1].length, "0")))
        };
    };
    return fmt;
}

function checkEnglish(str) {
    if (str.length != 0) {
        var reg = /^[a-zA-Z0-9_]+$/;
        if (!reg.test(str)) {
            alert(wtssI18n.view.alphanumericUnderlining);
            return true;
        }
    }
}

function checkProject(str) {
    if (str.length != 0) {
        var reg = /^[a-zA-Z0-9_-]+$/;
        if (!reg.test(str)) {
            alert(wtssI18n.view.alphanumericUnderscoreBar);
            return true;
        }
    }
}

function checkExecId(str) {
    if (str.length != 0) {
        var reg = /^[0-9]+$/;
        if (!reg.test(str)) {
            alert(wtssI18n.view.alphanumeric);
            return true;
        }
    }
}

var messageBox = {
    meaasgeTime: null,
    messageRemove: function() {
        document.body.removeChild($('.message-box')[0]);
    },
    //  success danger warning  info  primary
    show: function(content, type) {
        if ($('.message-box')[0]) {
            this.messageRemove();
        }
        clearTimeout(this.meaasgeTime);
        type = type ? type : 'success'
        var message = document.createElement("div");
        message.setAttribute('class', 'message-box alert-' + type);
        message.innerText = content;
        document.body.append(message)
        var that = this
        this.meaasgeTime = setTimeout(function() {
            that.messageRemove()
        }, 2500);
    }
}

// 下拉模糊查询部门
function loadDepartmentData(fun) {
    var that = this
    var requestURL = "/system";
    var requestData = {
        "ajax": "loadWebankDepartmentSelectData"
    };
    var successHandler = function(data) {
        if (data.error) {
            console.log(data.error);
        } else {
            if (data.webankDepartmentList) {
                var departPathMap = {}
                var arr = data.webankDepartmentList
                for (var i = 0; i < arr.length; i++) {
                    departPathMap[arr[i]['id']] = arr[i]['dpName'];
                }
                that.model.set('departPathMap', departPathMap)
            }
            if (typeof fun === 'function') {
                fun()
            }
        }
    }

    $.ajax({
        url: requestURL,
        type: "get",
        async: true,
        data: requestData,
        dataType: "json",
        error: function(data) {
            console.log(data);
        },
        success: successHandler
    });
    // $("#departmentSelect").select2({
    //   multiple: false,
    //   width: 'resolve',
    //   //language: "zh-CN",
    //   tags: false,//允许手动添加
    //   allowClear: true,//允许清空
    //   escapeMarkup: function (markup) { return markup; }, //自定义格式化防止XSS注入
    //   minimumInputLengt: 1,//最少输入多少字符后开始查询
    //   formatResult: function formatRepo (repo) { return repo.text; },//函数用来渲染结果
    //   formatSelection: function formatRepoSelection (repo) { return repo.text; },//函数用于呈现当前的选择
    //   ajax: {
    //     type: 'GET',
    //     url: "/batch",
    //     dataType: 'json',
    //     delay: 250,
    //     data: function (params) {
    //       var query = {
    //         ajax: "ajaxQueryDevDeptList",
    //         search: params.term,
    //         page: params.page || 1,
    //         pageSize: 20
    //       }
    //       return query;
    //     },
    //     processResults: function (data, params) {
    //       params.page = params.page || 1;
    //       return {
    //         results: data.devDeptList,
    //         pagination: {
    //           more: (params.page * 20) < data.totalCount
    //         }
    //       }
    //     },
    //     cache: true
    //   },
    //   language: 'zh-CN'
    // });
}
function getCmdSelectPlaceholder (type) {
    var placeholder = '';
    switch (type) {
        case 'wb_batch_group':
            placeholder = '请选择关键批量分组';
            break;
        case 'wb_batch_critical_path':
            placeholder = wtssI18n.view.criticalPathPro;
            break;
        case 'subsystem_app_instance':
            placeholder = '请选择业务域';
            break;
        case 'wb_subsystem':
            placeholder = '请选择子系统';
            break;
        case 'wb_product_category':
            placeholder = '请选择业务/产品一级分类';
            break;
        case 'wb_product':
            placeholder = '请选择业务/产品二级分类';
            break;
    }
    return placeholder;
}

function byIdGetCmdbLabel(selectId, type, id, name, queryId) {

    var requestURL = "/manager";

    var requestData = {
        "ajax": "getCmdbData",
        "type": type,
        "id": id,
        "name": name,
        queryId: queryId,
        start: 0,
        size: 10
    };
    var successHandler = function(data) {
        if (data.errorMsg) {
            messageBox.show(data.errorMsg, 'warning');
            return false;
        } else {
            var optionData = data.dataList ? data.dataList[0] : {};
            var option = new Option(optionData[name], optionData[id])
            $("#" + selectId).append(option).val(queryId).trigger('change');
        }
    };
    $.get(requestURL, requestData, successHandler, "json");
}

function fetchCmdbData (selectId, type, id, name, optionData, multiple) {
    var placeholder = getCmdSelectPlaceholder(type);
    $("#" + selectId).select2({
        placeholder: placeholder,
        multiple: !!multiple,
        width: 'resolve',
        //language: "zh-CN",
        tags: false,//允许手动添加
        allowClear: false,//允许清空
        escapeMarkup: function (markup) { return markup; }, //自定义格式化防止XSS注
        formatResult: function formatRepo (repo) { return repo.text; },//函数用来渲染结果
        formatSelection: function formatRepoSelection (repo) { return repo.text; },//函数用于呈现当前的选择
        ajax: {
          type: 'GET',
          url: "/manager",
          dataType: 'json',
          delay: 250,
          data: function (params) {
            var query = {
              ajax: "getCmdbData",
              type: type,
              id: id,
              name: name,
              query: params.term || '',
              start: params.page || 0,
              size: 20
            }
            return query;
          },
          processResults: function (data, params) {
            params.page = data.start || 0;
            var result = data.dataList ? data.dataList.map(function(item){
              return {
                  text: item[name],
                  id: item[id]
                }
            }) : []
            return {
              results: result,
              pagination: {
                more: ((params.page + 1) * 20) < data.total
              }
            }
          },
          cache: true
        },
        language: 'zh-CN'
    });
    if (optionData) {
        if (optionData.text) {
            // 回写
            var option = new Option(optionData.text, optionData.id)
            $("#" + selectId).append(option).val(optionData.id).trigger('change');
        } else {
            // 通过id获取label在回写
            byIdGetCmdbLabel(selectId, type, id, name, optionData.id);
        }
    } else {
        $("#" + selectId).empty();
    }
}
function loadBusPathData(id, addSelet) {
    if (!id) {
        id = 'busPathQuery';
    }
    fetchCmdbData(id, 'wb_batch_critical_path', 'id', 'name');
}

function loadSubSystemData(id) {
    if (!id) {
        id = 'subSystemQuery';
    }
    fetchCmdbData(id, 'wb_subsystem', 'subsystem_id', 'subsystem_name');
}

function renderBusPathSelect(busPathMap) {
    if (this.advQueryViewFirstShow) {
        $("#busPathQuery").find("option:selected").text("");
        $("#busPathQuery").empty();

        if (busPathMap) {
            var optionHtml = "";
            for (var key in busPathMap) {
                if (key) {
                    optionHtml += "<option value='" + key + "'>" + busPathMap[key] + "</option>";
                }
            }
            optionHtml = filterXSS(optionHtml, { 'whiteList': { 'option': ['value'] } });
            $("#busPathQuery").append(optionHtml);
        } else {
            $("#busPathQuery").val(null);
        }
    }

    $("#busPathQuery").selectpicker('refresh').selectpicker('render');
}

function handleExecutionTime (s) {
    var timeStr = s.replace(/\s/g, '').toLocaleLowerCase();
    var d = 0, h = 0, m = 0, s = 0, dIndex = -1, hIndex = -1, mIndex = -1, sIndex = -1;
    dIndex = timeStr.indexOf('d');
    if (dIndex > -1) {
        var dTim = timeStr.substring(0, dIndex + 1);
        timeStr = timeStr.substring(dIndex + 1);
        d = dTim.substring(0, dTim.length - 1) * 24 * 60 * 60 * 1000;
    }
    hIndex = timeStr.indexOf('h');
    if (hIndex > -1) {
        var hTim = timeStr.substring(0, hIndex + 1);
        timeStr = timeStr.substring(hIndex + 1);
        h = hTim.substring(0, hTim.length - 1) * 60 * 60 * 1000;
    }
    mIndex = timeStr.indexOf('m');
    if (mIndex > -1) {
        var mTim = timeStr.substring(0, mIndex + 1);
        timeStr = timeStr.substring(mIndex + 1);
        m = mTim.substring(0, mTim.length - 1) * 60 * 1000;
    }
    sIndex = timeStr.indexOf('s');
    if (sIndex > -1) {
        var dTem = timeStr.substring(0, sIndex);
        d = dTem * 1000;
    }
    return d + h + m + s;
}

function renderSubSystemSelect(subsystemMap) {
    if (this.advQueryViewFirstShow) {
        $("#subSystemQuery").find("option:selected").text("");
        $("#subSystemQuery").empty();

        if (subsystemMap) {
            var optionHtml = "";
            for (var key in subsystemMap) {
                if (key) {
                    optionHtml += "<option value='" + key + "'>" + subsystemMap[key] + "</option>";
                }
            }
            optionHtml = filterXSS(optionHtml, { 'whiteList': { 'option': ['value'] } })
            $("#subSystemQuery").append(optionHtml);
        } else {
            $("#subSystemQuery").val(null);
        }
    }

    $("#subSystemQuery").selectpicker('refresh').selectpicker('render');
}

function renderDepartmentSelectelect(subsystemMap) {
    if (this.advQueryViewFirstShow) {
        $("#departmentSelect").find("option:selected").text("");
        $("#departmentSelect").empty();

        if (subsystemMap) {
            var optionHtml = "";
            for (var key in subsystemMap) {
                if (key) {
                    optionHtml += "<option value='" + key + "'>" + subsystemMap[key] + "</option>";
                }
            }
            optionHtml = filterXSS(optionHtml, { 'whiteList': { 'option': ['value'] } })
            $("#departmentSelect").append(optionHtml);
        } else {
            $("#departmentSelect").val(null);
        }
    }

    $("#departmentSelect").selectpicker('refresh').selectpicker('render');
}

// 更改分页下拉值
function changePageSizeSelectValue (id, sizeArr) {
    const selectELE = $(`#${id} .pageSizeSelect`);
    const optionEle = selectELE.children();
    for (let i = 0; i< sizeArr.length; i++ ) {
        if (optionEle[i]) {
            optionEle[i].value = sizeArr[i];
            optionEle[i].innerText = `${sizeArr[i]}条/页`
        } else {
            const option = document.createElement('option');
            option.value = sizeArr[i];
            option.innerText = `${sizeArr[i]}条/页`;
            selectELE.append(option);
        }
    }
    selectELE.val(sizeArr[0]);
}

function commonPaginationFun () {
    return {
        createResize: function () {
            if(window.Event && !this.pageResize) {
                this.pageResize = new Event('resize');
            }
        },
        getPageElDomId: function () {
            return this.model.get('elDomId') ? this.model.get('elDomId') : 'pageTable';
        },
        renderPagination: function  () {
            // 渲染分页
            window.dispatchEvent(this.pageResize);
            const prefix = ".";
            var total = this.model.get("total");
            total = total ? total : 1;
            var pageSize = this.model.get("pageSize");
            var numPages = Math.ceil(total / pageSize);
            if (!this.pageParentDom) {
                const elDomId = this.getPageElDomId();
                this.pageParentDom = $(`#${elDomId} ul`);
            }
            this.model.set({ "numPages": numPages });
            let page = this.model.get("page");
            if (+page > numPages) {
                page = numPages;
                this.model.set("page", page);
            }
            //Start it off
            this.pageParentDom.find(".active").removeClass("active");
        
            // Disable if less than 5
            // 页面选择按钮
            console.log("Num pages " + numPages)
            var i = 1;
            for (; i <= numPages && i <= 5; ++i) {
                this.pageParentDom.find(`${prefix}page${i}`).removeClass("disabled");
            }
            for (; i <= 5; ++i) {
                this.pageParentDom.find(`${prefix}page${i}`).addClass("disabled");
            }
             // 第一页
             const firstEle =  this.pageParentDom.find(".firstPage");
             firstEle[0].page = 1;
             // 最后一页
             const lastEle =  this.pageParentDom.find(".lastPage");
             lastEle[0].page = numPages;
        
            // Disable prev/next if necessary.
            // 上一页按钮
            var previousEle = this.pageParentDom.find(`${prefix}previous`);
            if (page > 1) {
                var prevNum = parseInt(page) - parseInt(1);
                previousEle.removeClass("disabled");
                firstEle.removeClass("disabled");
                previousEle[0].page = prevNum;
            } else {
                previousEle.addClass("disabled");
                firstEle.addClass("disabled");
            }
            // 下一页按钮
            var nextEle = this.pageParentDom.find(`${prefix}next`)
            if (page < numPages) {
                var nextNum = parseInt(page) + parseInt(1);
                nextEle[0].page = nextNum;
                nextEle.removeClass("disabled");
                lastEle.removeClass("disabled");
            } else {
                var nextNum = parseInt(page) + parseInt(1);
                nextEle.addClass("disabled");
                lastEle.addClass("disabled");
            }
        
            // Selection is always in middle unless at barrier.
            var startPage = 0;
            var selectionPosition = 0;
            if (page < 3) {
                selectionPosition = page;
                startPage = 1;
            } else if (page == numPages && page != 3 && page != 4) {
                selectionPosition = 5;
                startPage = numPages - 4;
            } else if (page == numPages - 1 && page != 3) {
                selectionPosition = 4;
                startPage = numPages - 4;
            } else if (page == 4) {
                selectionPosition = 4;
                startPage = page - 3;
            } else if (page == 3) {
                selectionPosition = 3;
                startPage = page - 2;
            } else {
                selectionPosition = 3;
                startPage = page - 2;
            }
        
            var pageSelectionPosition = this.pageParentDom.find(`${prefix}page${selectionPosition}`);
            pageSelectionPosition.addClass("active");
            pageSelectionPosition[0].page = page;
            var selecta = pageSelectionPosition.find('a');
            selecta.text(page);
        
            for (var j = 0; j < 5; ++j) {
                var realPage = startPage + j;
                var elementId = this.pageParentDom.find(`${prefix}page${j + 1}`)
                elementId[0].page = realPage;
                var a = elementId.find('a');
                a.text(realPage);
                a.attr("href", "javascript:void(0);");
        
            }
        },
        handleChangePageSelection: function (evt) {
            // 切换page
            if ($(evt.currentTarget).hasClass("disabled")) {
                return;
            }
            var page = evt.currentTarget.page;
            this.model.set({ "page": page });
        },
        handlePageSizeSelection: function (evt) {
            // 切换size
            var pageSize = evt.currentTarget.value;
            this.model.set({  pageSize });
            this.handleChangeView();
        },
        handlePageNumJump: function (evt) {
            // 跳转page
            var elDomId = this.getPageElDomId();
            var pageNum = $("#" + elDomId + " .pageNumInput").val();
            if (pageNum <= 0) {
                //alert("页数必须大于1!!!");
                return;
            }
        
            if (pageNum > this.model.get("numPages")) {
                pageNum = this.model.get("numPages");
            }
        
            this.model.set({ "page": pageNum });
        },
        handleChangeView: function () {
            // 点击查询
            if (this.model.get("page") === 1 || (!this.init && this.model.get("page") === 1)) {
                this.handlePageChange();
                this.init = true;
            } else {
                this.model.set({ "page": 1 });
            }
        },
    }
}

function getUrlQuery(url) {
    // str为？之后的参数部分字符串
    var str = url.substr(url.indexOf('?') + 1)
        // arr每个元素都是完整的参数键值
    var arr = str.split('&')
        // result为存储参数键值的集合
    var result = {}
    for (let i = 0; i < arr.length; i++) {
        // item的两个元素分别为参数名和参数值
        var item = arr[i].split('=')
        result[item[0]] = item[1]
    }
    return result
}