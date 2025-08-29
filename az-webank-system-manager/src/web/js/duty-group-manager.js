function convertTimestampToDateString(timestamp) {
  if (!timestamp) return ''
  const date = new Date(timestamp);
  const year = date.getFullYear();
  const month = String(date.getMonth() + 1).padStart(2, '0');
  const day = String(date.getDate()).padStart(2, '0');
  return `${year}-${month}-${day}`;
}

var addDutyGroupView;
azkaban.AddDutyGroupView = Backbone.View.extend({
  events: {
    'submit #dutyForm': 'onSubmit',
    'click #addMember': 'onAddMember',
    'click .remove-member': 'onRemoveMember'
  },

  initialize: function (settings) {
    console.log("Hide modal error msg");
    $("#add-duty-group-modal-error-msg").hide();
    this.initInput();
  },

  initSelect2: function (element) {
    if (element) {
      element.select2({
        placeholder: wtssI18n.system.userPro,//默认文字提示
        multiple: false,
        width: '100%',
        //language: "zh-CN",
        //allowClear: true,//允许清空
        escapeMarkup: function (markup) { return markup; }, //自定义格式化防止XSS注入
        minimumInputLengt: 1,//最少输入多少字符后开始查询
        formatResult: function formatRepo(repo) { return repo.text; },//函数用来渲染结果
        formatSelection: function formatRepoSelection(repo) { return repo.text; },//函数用于呈现当前的选择
        ajax: {
          type: 'GET',
          url: "/system",
          dataType: 'json',
          delay: 250,
          data: function (params) {
            var query = {
              ajax: "loadWebankUserSelectData",
              serach: params.term,
              page: params.page || 1,
              pageSize: 20,
            }
            return query;
          },
          processResults: function (data, params) {
            params.page = params.page || 1;
            return {
              results: data.webankUserList.map(item => ({
                ...item,
                id: item.text.replace(/\([^)]*\)/, ''),
                text: item.text.replace(/\([^)]*\)/, '')
              })),
              pagination: {
                more: (params.page * 20) < data.webankUserTotalCount
              }
            }
          },
          cache: true
        },
        language: 'zh-CN',
      });
    }
  },

  initInput: function () {
    $('#dutyForm')[0].reset()
    $('#dutyGroupName').data('id', '');
    $('.member-row:not(:first)').remove()
    $('.datepicker').not('.duty-date').datepicker({
      format: 'yyyy-mm-dd',
      autoclose: true,
      todayHighlight: true
    });
    $('.datepicker.duty-date').datepicker({
      format: 'yyyy-mm-dd',
      multidate: true,
      multidateSeparator: ',',
      todayHighlight: true
    });
    this.initSelect2($(".member-name"))
    // 清空输入值
    $('#dutyForm').find('input').val('');
    $('#dutyForm').find('select').val('').empty()
  },

  loadGroupData: function (groupData) {
    console.log('loaGroupData', groupData)
    // 重置表单
    $('#dutyForm')[0].reset();
    $('.member-row:not(:first)').remove();

    // 设置值班组名称
    $('#dutyGroupName').val(groupData.groupName);
    // 保存值班组ID用于后续更新
    $('#dutyGroupName').data('id', groupData.id);

    const _this = this
    // 处理成员数据
    groupData.dutyPerson.forEach(function (member, index) {
      // 获取要操作的行
      var row = index === 0 ?
        $('.member-row:first') :
        $('.member-row:first').clone();

      // 如果是新行，添加到容器中
      if (index > 0) {
        // 对于新行，需要重新初始化 select2
        // 替换原始select
        var originSelect = row.find('.member-name')
        var newSelect = $(' <select class="form-control member-name"></select>')
        originSelect.replaceWith(newSelect)

        // 移除旧select2
        row.find('.select2-container').remove()
        $('#membersContainer').append(row);
        _this.initSelect2(row.find('.member-name'));
      }

      const select = row.find('.member-name')
      const newOption = new Option(member.userName, member.userName, true, true);
      select.empty().append(newOption).trigger('change');

      // 设置日期值
      row.find('.datepicker').not('.duty-date').datepicker({
        format: 'yyyy-mm-dd',
        autoclose: true,
        todayHighlight: true
      });
      row.find('.datepicker.duty-date').datepicker({
        format: 'yyyy-mm-dd',
        multidate: true,
        multidateSeparator: ',',
        todayHighlight: true
      });
      row.find('.start-date').datepicker('update', member.beginTime);
      row.find('.end-date').datepicker('update', member.endTime);
      const dutyDates = member.dutyTime.split(',')
      const dutyDatePicker = row.find('.duty-date')
      dutyDatePicker.datepicker('clearDates')
      dutyDatePicker.datepicker('setDates', dutyDates)
    });
  },

  validateDates: function (beginTime, endTime) {
    if (!beginTime || !endTime) return true;
    return new Date(beginTime) <= new Date(endTime);
  },

  onSubmit: function (e) {
    e.preventDefault();

 
    var isValid = true;
    $('.member-name').each(function () {
      if (!$(this).val()) {
        isValid = false;
      } else {
        isValid = true
      }
    });
    if (!isValid) {
      alert(wtssI18n.system.mustSelectMember);
      return;
    }
    // 验证日期
    const _this = this
    $('.member-row').each(function () {
      var beginTime = $(this).find('.start-date').val();
      var endTime = $(this).find('.end-date').val();

      if (!_this.validateDates(beginTime, endTime)) {
        isValid = false;
        return false;
      }
    })

    if (!isValid) {
      alert(wtssI18n.system.startDateMustBeforeEndDate);
      return;
    }

    // 收集表单数据
    var formData = {
      groupName: $('#dutyGroupName').val(),
      dutyPerson: []
    };

    $('.member-row').each(function () {
      var member = {
        userName: $(this).find('.member-name').val(),
        beginTime: $(this).find('.start-date').val(),
        endTime: $(this).find('.end-date').val(),
        dutyTime: $(this).find('.duty-date').val()
      };
      formData.dutyPerson.push(member);
    });

    console.log(formData)

    var id = $('#dutyGroupName').data("id")
    const requestData = {
      groupName: formData.groupName,
      dutyPerson: formData.dutyPerson
    }
    const successHandler = function (data) {
      if (data.error) {
        $("#add-duty-group-modal-error-msg").show();
        $("#add-duty-group-modal-error-msg").text(data.error);
        return false;
      } else {
        window.location.href = "/system#duty-group";
        window.location.reload();
      }
    };
    if (id) {
      // 编辑 
      requestData.groupId = parseInt(id)
      $.ajax({
        url: '/system?ajax=updateDuty',
        type: 'POST',
        contentType: "application/json",
        data: JSON.stringify(requestData),
        dataType: 'json',
        success: successHandler,
        error: function (xhr, status, error) {
          console.log(xhr, status, error);
        }
      });
    } else {
      // 新增
      $.ajax({
        url: '/system?ajax=addGroup',
        type: 'POST',
        contentType: "application/json",
        data: JSON.stringify(requestData),
        dataType: 'json',
        success: successHandler,
        error: function (xhr, status, error) {
          console.log(xhr, status, error);
        }
      });
    }
  },


  onAddMember: function () {
    var newRow = $('.member-row').first().clone();

    // 替换原始select
    var originSelect = newRow.find('.member-name')
    var newSelect = $(' <select class="form-control member-name"></select>')
    originSelect.replaceWith(newSelect)

    // 移除旧select2
    newRow.find('.select2-container').remove()

    // 清空输入值
    newRow.find('input').val('');
    newRow.find('select').val('').empty()

    $('#membersContainer').append(newRow);
    newRow.find('.datepicker').not('.duty-date').datepicker({
      format: 'yyyy-mm-dd',
      autoclose: true,
      todayHighlight: true
    });
    newRow.find('.datepicker.duty-date').datepicker({
      format: 'yyyy-mm-dd',
      multidate: true,
      multidateSeparator: ',',
      todayHighlight: true
    });
    this.initSelect2(newRow.find('.member-name'))
  },

  onRemoveMember: function (e) {
    if (this.$('.member-row').length > 1) {
      $(e.currentTarget).closest('.member-row').remove();
    } else {
      alert(wtssI18n.system.keepAtLeastOneMember);
    }
  },

  render: function () {
    $("#add-duty-group-modal-error-msg").hide();
  },
});

//处理方法 组装表格和翻页处理
var dutyGroupView;
azkaban.DutyGroupView = Backbone.View.extend({
  events: {
    "click #duty-group-pageSelection li": "handleChangePageSelection",
    "change #duty-group-pageSelection .pageSizeSelect": "handlePageSizeSelection",
    "click #duty-group-pageSelection .pageNumJump": "handlePageNumJump",
    "click #duty-group-table-body .btn-danger": "handleDelete",
    "click #duty-group-table-body .btn-primary": "handleEdit",
    "click #add-duty-group-btn": "handleAddDutyGroup",
  },

  initialize: function (settings) {
    this.model.bind('change:view', this.handleChangeView, this);
    this.model.bind('render', this.render, this);
    this.model.set({ page: 1, pageSize: 20 });
    this.model.bind('change:page', this.handlePageChange, this);
    this.model.set('elDomId', 'duty-group-pageSelection');
    this.createResize();
  },

  handleEdit: function (evt) {
    $('#add-duty-group-panel-title').hide()
    $('#edit-duty-group-panel-title').show()
    var groupId = $(evt.currentTarget).attr("groupId")
    var groupName = $(evt.currentTarget).attr("groupName")
    var requestURL = '/system'
    var requestData = {
      "ajax": "getPersonList",
      "groupId": groupId
    };
    var successHandler = function (data) {
      if (data.error) {
        alert(data.error)
        return
      }
      var personList = data.personList.map(item => ({
        ...item,
        beginTime: convertTimestampToDateString(item.beginTime),
        endTime: convertTimestampToDateString(item.endTime),
      }))
      var dutyGroup = {
        id: groupId,
        groupName: groupName,
        dutyPerson: personList
      }
      addDutyGroupView.loadGroupData(dutyGroup)
      $('#add-duty-group-panel').modal();
      $("#add-duty-group-modal-error-msg").hide();
    };
    $.get(requestURL, requestData, successHandler, "json");
  },

  handleDelete: function (evt) {
    console.log("handleDelete");
    var groupId = $(evt.currentTarget).attr("name");
    var requestURL = "/system";

    if (null == groupId) {
      alert(wtssI18n.system.userPro);
      return;
    }
    deleteDialogView.show(wtssI18n.deletePro.deleteDutyGroup, wtssI18n.deletePro.whetherDeleteDutyGroup, wtssI18n.common.cancel, wtssI18n.common.delete, '', function () {
      // var model = this.model;
      var requestData = {
        "ajax": "deleteDutyGroup",
        "groupId": groupId,
      };
      var successHandler = function (data) {
        if (data.error) {
          $("#add-duty-group-modal-error-msg").show();
          $("#add-duty-group-modal-error-msg").text(data.error);
          alert(data.error)
          return false;
        } else {
          window.location.href = "/system#duty-group";
          window.location.reload();
        }
        // model.trigger("render");
      };
      $.post(requestURL, requestData, successHandler, "json");
    });
  },

  handleAddDutyGroup: function (evt) {
    $('#add-duty-group-panel-title').show()
    $('#edit-duty-group-panel-title').hide()
    console.log("showAddPanel");
    addDutyGroupView.initInput()
    $('#add-duty-group-panel').modal();
    $("#add-duty-group-modal-error-msg").hide();
  },

  render: function (evt) {
    console.log("render");
    // Render page selections
    var tbody = $("#duty-group-table-body");
    tbody.empty();

    var dutyGroups = this.model.get("dutyGroups") || [];
    if (!dutyGroups || dutyGroups.length == 0) {
      $("#duty-group-pageSelection").hide()
    }


    for (var i = 0; i < dutyGroups.length; ++i) {
      var row = document.createElement("tr");

      //组装数字行
      var tdNum = document.createElement("td");
      $(tdNum).text(i + 1);
      $(tdNum).attr("class", "tb-name");
      row.appendChild(tdNum);

      //组装值班组ID
      var tdGroupId = document.createElement("td");
      $(tdGroupId).text(dutyGroups[i].id);
      row.appendChild(tdGroupId);

      //组装值班组名
      var tdGroupName = document.createElement("td");
      $(tdGroupName).text(dutyGroups[i].groupName);
      $(tdGroupName).attr("style", "word-break:break-all;");
      row.appendChild(tdGroupName);

      //组装成员
      var tdGroupMembers = document.createElement("td");
      $(tdGroupMembers).text(dutyGroups[i].persons);
      row.appendChild(tdGroupMembers);

      //组装操作行
      var tdAction = document.createElement("td");
      var editBtn = document.createElement("button");
      var deleteBtn = document.createElement("button");
      $(editBtn).attr("groupId", dutyGroups[i].id);
      $(editBtn).attr("groupName", dutyGroups[i].groupName);
      $(editBtn).attr("class", "btn btn-sm btn-primary");
      $(editBtn).attr("style", "margin-right:5px;");
      $(editBtn).text(wtssI18n.system.viewEdit);
      $(deleteBtn).attr("name", dutyGroups[i].id);
      $(deleteBtn).attr("class", "btn btn-sm btn-danger");
      $(deleteBtn).text(wtssI18n.common.delete1);
      tdAction.appendChild(editBtn);
      tdAction.appendChild(deleteBtn);
      row.appendChild(tdAction);

      tbody.append(row);
    }

    this.renderPagination(evt);
  },

  ...commonPaginationFun(),

  handlePageChange: function (evt) {
    var pageNum = this.model.get("page");
    var pageSize = this.model.get("pageSize");
    var requestURL = "/system";
    var searchName = this.model.get("searchName");
    if (!searchName) {
      searchName = "";
    }

    var model = this.model;
    var requestData = {
      "ajax": "getDutyPageList",
      "page": pageNum,
      "size": pageSize,
      "groupName": searchName,
    };
    var successHandler = function (data) {
      model.set({
        "dutyGroups": data.dutyGroupList,
        "total": data.total
      });
      model.trigger("render");
    };
    $.get(requestURL, requestData, successHandler, "json");
  },


});





