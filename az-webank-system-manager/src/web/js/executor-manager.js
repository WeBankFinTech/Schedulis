/**
 * Created by v_boweicai on 9/20/24.
 */

//执行器管理主页面视图
var executorManageView;
azkaban.ExecutorManageView = Backbone.View.extend({
    events: {
        "click .btn-executor-active": "handleActiveExecutorBtn",
    },

    initialize: function (settings) {
        this.model.bind('change:view', this.fetchExecutors, this);
        this.model.bind('render', this.render, this);
    },

    render: function (evt) {
        console.log("render");
        // Render page selections
        var tbody = $("#executorManageTableBody");
        tbody.empty();

        var executors = this.model.get("executorManagePageList");

        // 组装数据内容
        for (var i = 0; i < executors.length; ++i) {
            var row = document.createElement("tr");

            //组装数字行
            var tdNum = document.createElement("td");
            $(tdNum).text(i + 1);
            $(tdNum).attr("class", "tb-name");
            row.appendChild(tdNum);

            //组装执行器ID行
            var tdExecutorId = document.createElement("td");
            $(tdExecutorId).text(executors[i].id);
            row.appendChild(tdExecutorId);

            //组装主机名行
            var tdHostName = document.createElement("td");
            $(tdHostName).text(executors[i].host);
            $(tdHostName).attr("style", "word-break:break-all;");
            row.appendChild(tdHostName);

            //组装端口行
            var tdPort = document.createElement("td");
            $(tdPort).text(executors[i].port);
            $(tdPort).attr("style", "word-break:break-all;");
            row.appendChild(tdPort);

            //组装是否有效行
            var tdIsValid = document.createElement("td");
            $(tdIsValid).text(executors[i].active);
            $(tdIsValid).attr("style", "word-break:break-all;");
            row.appendChild(tdIsValid);

            //组装上次分组信息行
            var tdExecutorInfo = document.createElement("td");
            $(tdExecutorInfo).text(executors[i].executorInfo);
            $(tdExecutorInfo).attr("style", "word-break:break-all;");
            row.appendChild(tdExecutorInfo);

            //组装操作行
            var tdAction = document.createElement("td");
            var updateBtn = document.createElement("button");
            $(updateBtn).attr("id", executors[i].id + "updateBtn");
            $(updateBtn).attr("name", executors[i].id);
            $(updateBtn).attr("active", executors[i].active);
            $(updateBtn).attr("class", "btn btn-sm btn-info btn-executor-active");
            $(updateBtn).text(executors[i].active ? '下线' : '上线');
            tdAction.appendChild(updateBtn);
            row.appendChild(tdAction);

            tbody.append(row);
        }
    },

    fetchExecutors: function (evt) {
        var requestURL = "/system";

        var model = this.model;
        var requestData = {
            "ajax": "fetchExecutors",
        };
        var successHandler = function (data) {
            if (data.error) {
                messageBox.show(data.error, 'danger')
            } else {
                model.set({
                    "executorManagePageList": data.executors,
                    "total": data.total
                });
                model.trigger("render");
            }
        };
        $.get(requestURL, requestData, successHandler, "json");
    },

    handleActiveExecutorBtn: function (evt) {
        console.log('acitve executor', evt);
        var executorId = evt.currentTarget.name;
        console.log(this.model.get("executorManagePageList"), executorId)
        var executorData = this.model.get("executorManagePageList").find(function (executor) {
            return String(executor.id) === String(executorId);
        });

        var requestURL = "/system?ajax=updateExecutor";

        var requestData = {
            "id": executorData.id,
            "host": executorData.host,
            "port": executorData.port,
            "lastStatsUpdatedTime": executorData.lastStatsUpdatedTime,
            "isActive": !executorData.active,
            "executorInfo": executorData.executorInfo,
        };
        var successHandler = function (data) {
            if (data.error) {
                alert(data.error);
            } else {
                this.fetchExecutors()
            }
        };
        $.post({
            url: requestURL,
            data: JSON.stringify(requestData),
            contentType: "application/json",
            success: successHandler.bind(this),
        })
    }

});

var executorOptionsView;
azkaban.ExecutorOptionsView = Backbone.View.extend({
    events: {
        "click #refreshExecutorListBtn": "hanldeRefreshExecutorList",
    },

    initialize: function (settings) {
    },

    hanldeRefreshExecutorList: function (evt) {
        console.log("click refreshExecutorListBtn");
        var requestURL = "/executor";

        var requestData = {
            "ajax": "reloadExecutors",
        };
        var successHandler = function (data) {
            console.log(data)
            if (data.error) {
                alert(data.error)
            } else {
                messageBox.show("强制刷新所有执行器成功");
            }
        };
        $.get(requestURL, requestData, successHandler, "json");
    },

    render: function () {
    }
});