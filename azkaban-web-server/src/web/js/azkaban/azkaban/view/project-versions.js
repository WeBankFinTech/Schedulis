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

var versionModel;
azkaban.VersionModel = Backbone.Model.extend({});

var projectVersionView;
azkaban.ProjectVersionView = Backbone.View.extend({
  events: {
    'click .diff-btn': 'clcikShowDiffBtn'
  },

  initialize: function (settings) {
    var search = window.location.search.substring(1);
    var paramArr = search.split('&');
    var param = {}
    for (var i = 0; i < paramArr.length; i++) {
      var tem = paramArr[i].split('=');
      param[tem[0]] = tem[1] || '';
    }
    this.model.set({ projectId: param.projectId || '' });
    if (param.diff === 'true') {
      this.showDiffData(Number(param.leftVersion), Number(param.rightVersion))
    } else {
      this.handleChangeView();
    }
  },

  // 转换函数：将自定义格式转为Git Diff格式
  convertToGitDiff: function (fileDiff) {
    if ((fileDiff.changeType === 'ADDED' || fileDiff.changeType === 'DELETED') &&
        !fileDiff?.diffContent) {
        return '';
    }

    let gitDiff = `--- a/${fileDiff.filePath}\n+++ b/${fileDiff.filePath}\n`;

    // 按原始顺序处理所有变更
    const changes = fileDiff?.diffContent?.filter(c => c.type !== 'EQUAL') || [];

    // 智能分组算法
    const changeBlocks = [];
    let currentBlock = {
        deletes: [],
        inserts: []
    };

    changes.forEach(change => {
        const isDelete = change.type === 'DELETE';
        
        // 判断是否应该开始新块
        const shouldStartNewBlock = 
            // 当前块已有删除操作，而这是不相邻的插入
            (currentBlock.deletes.length > 0 && !isDelete && 
             !currentBlock.deletes.some(d => d.leftLineNum === change.rightLineNum)) ||
            // 或者已有插入操作，而这是不相邻的删除
            (currentBlock.inserts.length > 0 && isDelete && 
             !currentBlock.inserts.some(i => i.rightLineNum === change.leftLineNum));

        if (shouldStartNewBlock && 
            (currentBlock.deletes.length > 0 || currentBlock.inserts.length > 0)) {
            changeBlocks.push(currentBlock);
            currentBlock = {
                deletes: [],
                inserts: []
            };
        }

        // 添加到当前块
        if (isDelete) {
            currentBlock.deletes.push(change);
        } else {
            currentBlock.inserts.push(change);
        }
    });

    // 添加最后一个块
    if (currentBlock.deletes.length > 0 || currentBlock.inserts.length > 0) {
        changeBlocks.push(currentBlock);
    }

    // 生成每个变更块的差异
    changeBlocks.forEach(block => {
        // 计算行号范围
        const leftLines = block.deletes.map(d => d.leftLineNum);
        const rightLines = block.inserts.map(i => i.rightLineNum);

        const leftStart = leftLines.length > 0 ? Math.min(...leftLines) : 
                         rightLines.length > 0 ? rightLines[0] : 0;
        const leftEnd = leftLines.length > 0 ? Math.max(...leftLines) : leftStart;
        const leftSize = leftEnd - leftStart + 1;

        const rightStart = rightLines.length > 0 ? Math.min(...rightLines) : 
                          leftLines.length > 0 ? leftLines[0] : 0;
        const rightEnd = rightLines.length > 0 ? Math.max(...rightLines) : rightStart;
        const rightSize = rightEnd - rightStart + 1;

        // 修正纯插入或纯删除块的行数计算
        const deleteCount = block.deletes.length;
        const insertCount = block.inserts.length;

        gitDiff += `@@ -${leftStart},${deleteCount > 0 ? leftSize : 0} +${rightStart},${insertCount > 0 ? rightSize : 0} @@\n`;
        
        // 合并操作并按行号排序
        const allOps = [...block.deletes, ...block.inserts].sort((a, b) => {
            const aLine = a.type === 'DELETE' ? a.leftLineNum : a.rightLineNum;
            const bLine = b.type === 'DELETE' ? b.leftLineNum : b.rightLineNum;
            return aLine - bLine;
        });

        // 输出变更内容
        allOps.forEach(op => {
            if (op.type === 'DELETE') {
                gitDiff += `-${op.line}\n`;
            } else {
                gitDiff += `+${op.line}\n`;
            }
        });
    });

    return gitDiff;
},


  // 渲染单个文件差异
  renderFileDiff: function (fileDiff) {
    // 根据变更类型设置样式
    let statusClass = '';
    let statusText = '';
    let message = '';

    switch (fileDiff.changeType) {
      case 'ADDED':
        statusClass = 'status-added';
        statusText = 'ADDED';
        message = '这是一个新增的文件';
        break;
      case 'MODIFIED':
        statusClass = 'status-modified';
        statusText = 'CHANGED';
        if (!fileDiff?.diffContent) {
          message = `${fileDiff.filePath}已变更（大小：${fileDiff.leftFileSize} bytes -> ${fileDiff.rightFileSize} bytes; MD5值: ${fileDiff.leftFileMd5} -> ${fileDiff.rightFileMd5}）`;
        }
        break;
      case 'DELETED':
        statusClass = 'status-removed';
        statusText = 'DELETED';
        message = '这个文件已被删除';
        break;
    }

    // 对于ADDED/DELETED且diffContent为null的情况
    if ((fileDiff.changeType === 'ADDED' || fileDiff.changeType === 'DELETED' || fileDiff.changeType === 'MODIFIED') &&
      !fileDiff?.diffContent) {
      return $(`
              <div class="file-diff">
                <div class="file-header">
                  <span class="file-title">${fileDiff.filePath}</span>
                  <span class="file-status ${statusClass}">${statusText}</span>
                </div>
                <div class="file-message">${message}</div>
              </div>
            `);
    }

    // 正常渲染有差异内容的文件
    const gitDiffText = this.convertToGitDiff(fileDiff);
    console.log('gitDiffText', gitDiffText)
    const diffHtml = Diff2Html.html(gitDiffText, {
      outputFormat: 'side-by-side',
      matching: 'lines',
      highlight: true,
      drawFileList: false
    });
    return $(`
            <div class="file-diff">
              <div class="file-header">
                <span class="file-title">${fileDiff.filePath}</span>
                <span class="file-status ${statusClass}">${statusText}</span>
              </div>
              ${diffHtml}
            </div>
          `);
  },

  fetchDiffData: function (left, right) {
    const requestURL = "/manager";
    const requestData = {
      "project": projectName,
      "ajax": "fetchProjectDiff",
      leftVersion: left,
      rightVersion: right
    };
    return $.get(requestURL, requestData);

  },

  // 渲染所有文件差异
  renderAllDiffs: function (diffData) {
    const diffEntries = diffData?.diffEntries || []
    const container = $(`#projectVersionView`)
    container.empty()
    container.append($(`<h3>版本比对：</h3>`))

    if (diffData.error) {
      container.append($(`
        <div style='  color: #dc3545; /* 红色系 */
          background-color: #f8d7da;
          border: 1px solid #f5c6cb;
          border-radius: 4px;
          padding: 10px 15px;
          margin: 10px 0;
          font-size: 14px;'>
         ${diffData.error}
         <div>
      `));
      return;
    }

    const diffHeader = $(`       <div class="diff-header">
            <div class="project-title">${diffData?.project}</div>
            <div class="version-compare">
                <div class="version-box version-old">v${diffData?.leftVersion}</div>
                <div class="version-arrow">→</div>
                <div class="version-box version-new">v${diffData?.rightVersion}</div>
            </div>
            <div class="summary-stats">
                <div class="stat-item">
                    <div class="stat-badge stat-added">${diffData?.summary.added}</div>
                    <span>新增文件</span>
                </div>
                <div class="stat-item">
                    <div class="stat-badge stat-deleted">${diffData?.summary.deleted}</div>
                    <span>删除文件</span>
                </div>
                <div class="stat-item">
                    <div class="stat-badge stat-modified">${diffData?.summary.modified}</div>
                    <span>修改文件</span>
                </div>
            </div>
        </div>`)
    container.append(diffHeader)

    if (!diffEntries || diffEntries.length === 0) {
      container.append('<div class="loading">没有差异内容</div>');
      return;
    }

    diffEntries.forEach(fileDiff => {
      const diff = {
        ...fileDiff,
        diffContent: fileDiff?.diffContent?.map(item => ({
          ...item,
          lineNo: item.leftLineNum || item.rightLineNum
        }))
      }
      container.append(this.renderFileDiff(diff));
    });
  },

  clcikShowDiffBtn: function (e) {
    const leftVersion = $(e.currentTarget).data('leftversion')
    const rightVersion = $(e.currentTarget).data('rightversion')

    const currentURL = window.location.href;
    window.open(`${currentURL}&diff=true&leftVersion=${leftVersion}&rightVersion=${rightVersion}`, '_blank');
  },

  handleChangeView: function (evt) {
    var requestURL = "/manager";
    var model = this.model;
    var requestData = {
      "project": projectName,
      "ajax": "fetchProjectVersions",
      "size": 10,
      "skip": 0
    };
    if (model.get('projectId')) {
      requestData.projectId = model.get('projectId')
    }
    var successHandler = function (data) {
      console.log("fetchVersions");
      if (data.error) {
        showDialog("Error", data.error);
        return;
      }
      // Get the columns to map to the values.
      var columns = data.columns;
      var columnMap = {};
      for (var i = 0; i < columns.length; ++i) {
        columnMap[columns[i]] = i;
      }
      var versionSection = $("#versionTable").find("tbody")[0];
      $(versionSection).empty();
      var versionData = data.versionData;
      let currentVersion = -1
      for (var i = 0; i < versionData.length; ++i) {
        var data = versionData[i];
        var projectId = data[columnMap['projectId']];
        var versionNum = data[columnMap['version']];
        if (i === 0) {
          currentVersion = versionNum
        }
        var uploadTime = data[columnMap['uploadTime']];

        var container = document.createElement("tr");
        $(container).addClass("projectVersion");

        //版本号
        var containerVersion = document.createElement("td");
        $(containerVersion).text(versionNum);

        //上传时间
        var containerUploadTime = document.createElement("td");
        $(containerUploadTime).text(getProjectModifyDateFormat(new Date(uploadTime)));

        var containerDownloadBtn = document.createElement("td");
        //组装 比对按钮
        var diffBtn = document.createElement("button");
        $(diffBtn).attr("class", "btn btn-sm btn-info diff-btn").attr("type", "button").attr("data-leftversion", versionNum).attr("data-rightversion", currentVersion).attr('style', 'margin-right: 8px');
        $(diffBtn).text(wtssI18n.common.diff || '比对');
        containerDownloadBtn.appendChild(diffBtn);
        //组装 下载按钮
        var downloadBtn = document.createElement("button");
        $(downloadBtn).attr("class", "btn btn-sm btn-info").attr("type", "button").attr("onclick", "checkHrefUrlXss('/manager?project=" + projectName + "&version=" + versionNum + "&download=true')");
        $(downloadBtn).text(downloadBtnText);
        containerDownloadBtn.appendChild(downloadBtn);

        $(container).append(containerVersion);
        $(container).append(containerUploadTime);
        $(container).append(containerDownloadBtn);

        $(versionSection).append(container);
      }

    };
    $.get(requestURL, requestData, successHandler);
  },

  showDiffData: function (left, right) {
    if ($('#dynamic-styles').length === 0) {
      $('<style id="dynamic-styles">').appendTo('head');
    }
    $('#dynamic-styles').text('.loading-container{display:flex;justify-content:center;align-items:center;height:100vh;font-family:-apple-system,BlinkMacSystemFont,"Segoe UI",Helvetica,Arial,sans-serif}.spinner{border:4px solid #f3f3f3;border-top:4px solid #0366d6;border-radius:50%;width:40px;height:40px;animation:spin 1s linear infinite;margin-right:15px}@keyframes spin{0%{transform:rotate(0deg)}100%{transform:rotate(360deg)}}.diff-header{background-color:#f8f9fa;border:1px solid #e1e4e8;border-radius:6px;padding:20px;margin-bottom:20px}.project-title{font-size:24px;font-weight:600;margin-bottom:10px;color:#24292e}.version-compare{display:flex;align-items:center;margin-bottom:15px}.version-box{padding:8px 15px;border-radius:4px;font-weight:600}.version-old{background-color:#ffeef0;color:#cb2431}.version-new{background-color:#e6ffec;color:#22863a}.version-arrow{margin:0 15px;font-size:20px;color:#586069}.summary-stats{display:flex;gap:15px}.stat-item{display:flex;align-items:center}.stat-badge{width:24px;height:24px;border-radius:50%;display:flex;align-items:center;justify-content:center;margin-right:8px;font-weight:600;font-size:12px}.stat-added{background-color:#dcffe4;color:#22863a}.stat-deleted{background-color:#ffeef0;color:#cb2431}.stat-modified{background-color:#fff5b1;color:#735c0f}.file-diff{margin-bottom:30px;border:1px solid #e1e4e8;border-radius:6px;overflow:hidden}.file-header{background-color:#f6f8fa;padding:10px 15px;border-bottom:1px solid #e1e4e8;display:flex;align-items:center}.file-title{font-weight:600;font-family:SFMono-Regular,Consolas,"Liberation Mono",Menlo,monospace;margin-right:10px}.file-status{padding:2px 5px;font-size:12px;border-radius:3px;margin-right:10px}.status-added{background-color:#dcffe4;color:#22863a}.status-modified{background-color:#fff5b1;color:#735c0f}.status-removed{background-color:#ffeef0;color:#cb2431}.loading{padding:50px;text-align:center;color:#586069}.controls{margin-bottom:20px;display:flex;gap:10px}.file-message{padding:15px;text-align:center;color:#586069;font-style:italic}');
    const loading = $(`<div
          class="loading-container"
          id="loading-container"
      >
          <div class="spinner"></div>
          <div>正在加载差异数据...</div>
      </div>`)
    const container = $(`#projectVersionView`)
    container[0].style.border = 'none';
    container.empty()
    container.append(loading)
    this.fetchDiffData(left, right).then((diffData) => {
      this.renderAllDiffs(diffData)
    }).catch((error) => {
      this.renderAllDiffs(error)
      console.log(error)
    })
  }
});

var showDialog = function (title, message) {
  $('#messageTitle').text(title);

  $('#messageBox').text(message);

  $('#messageDialog').modal({
    closeHTML: "<a href='#' title='Close' class='modal-close'>x</a>",
    position: ["20%",],
    containerId: 'confirm-container',
    containerCss: {
      'height': '220px',
      'width': '565px'
    },
    onShow: function (dialog) {
    }
  });
}

$(function () {
  versionModel = new azkaban.VersionModel();
  projectVersionView = new azkaban.ProjectVersionView(
    { el: $('#projectVersionView'), model: versionModel });
});
