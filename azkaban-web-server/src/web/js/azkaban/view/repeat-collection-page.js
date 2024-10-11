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
var advFilterView;
azkaban.AdvFilterView = Backbone.View.extend({
  events: {
    "click #filter-btn": "handleAdvFilter"
  },

  initialize: function(settings) {
    $('#datetimebegin').datetimepicker();
    $('#datetimeend').datetimepicker();
    $('#datetimebegin').on('change.dp', function(e) {
      $('#datetimeend').data('DateTimePicker').setStartDate(e.date);
    });
    $('#datetimeend').on('change.dp', function(e) {
      $('#datetimebegin').data('DateTimePicker').setEndDate(e.date);
    });
    $('#adv-filter-error-msg').hide();
  },

  handleAdvFilter: function(evt) {
    var projcontain = $('#projcontain').val();
    var flowcontain = $('#flowcontain').val();
    var usercontain = $('#usercontain').val();
    var status = $('#status').val();

    console.log("filtering recover history");

    var historyURL = contextURL + "/recover"

    var requestURL = historyURL + "?advfilter=true" + "&projcontain=" + projcontain + "&flowcontain=" + flowcontain + "&usercontain=" + usercontain + "&status=" + status;
    window.location = requestURL;

  },

  render: function() {
  }
});


function killRepeat(repeatId) {
  //请求 /executor 才能进入到跟创建历史补采一样的线程内
  var requestURL=document.location.href.replace("recover","executor");
  var requestData = {"repeatId": repeatId, "ajax": "stopRepeat"};
  var successHandler = function(data) {
    console.log("repeat kill clicked");
    if (data.error) {
      showDialog("Error", data.error);
    }
    else {
      showDialog("Cancelled", "Repeat Flow has been cancelled.");
    }
  };
  ajaxCall(requestURL, requestData, successHandler);
}

var showDialog = function(title, message) {
  $('#messageTitle').text(title);
  $('#messageBox').text(message);
  $('#messageDialog').modal();
}

$(function() {

  $('.selected').children("a").css("background-color","#c0c1c2");

  function refreshHistory(){
    this.refresh();
  }

  var roles;
  $.ajax({
    url: "history?ajax=user_role",
    dataType: "json",
    type: "GET",
    //data: {},
    success: function(data) {
      roles=data.userRoles;
    }
  });

  filterView = new azkaban.AdvFilterView({el: $('#recover-filter')});
  $('#recover-filter-btn').click( function() {
    $('#recover-filter').modal();
    //用户只有user权限没有admin权限时 隐藏用户查找输入框
    if($.inArray("user", roles) != -1 && $.inArray("admin", roles) == -1){
      $('#usercontain-div').hide();
    }
  });

  var total = 0;
  $.ajax({
    url: "recover?ajax=getRecoverTotal",
    dataType: "json",
    type: "GET",
    //data: {},
    success: function(data) {
      total=data.recovertotal;
      pagePluginHandle(total);
    }
  });

  function pagePluginHandle(total) {
    var pagePlugin = $('#pageSelection');
    //var total = this.model.get("total");
    total = total? total : 1;
    var pageSize = 16;
    var numPages = Math.ceil(total / pageSize);

    //this.model.set({"numPages": numPages});
    var page = executionModel.get("page");

    //Start it off
    $("#pageSelection .active").removeClass("active");

    // Disable if less than 5
    console.log("Num pages " + numPages)
    var i = 1;
    for (; i <= numPages && i <= 5; ++i) {
      $("#page" + i).removeClass("disabled");
    }
    for (; i <= 5; ++i) {
      $("#page" + i).addClass("disabled");
    }

    // Disable prev/next if necessary.
    if (page > 1) {
      $("#previous").removeClass("disabled");
      $("#previous")[0].page = page - 1;
      $("#previous a").attr("href", "#page" + (page - 1));
    }
    else {
      $("#previous").addClass("disabled");
    }

    if (page < numPages) {
      $("#next")[0].page = page + 1;
      $("#next").removeClass("disabled");
      $("#next a").attr("href", "#page" + (page + 1));
    }
    else {
      $("#next")[0].page = page + 1;
      $("#next").addClass("disabled");
    }

    // Selection is always in middle unless at barrier.
    var startPage = 0;
    var selectionPosition = 0;
    if (page < 3) {
      selectionPosition = page;
      startPage = 1;
    }
    else if (page == numPages) {
      selectionPosition = 5;
      startPage = numPages - 4;
    }
    else if (page == numPages - 1) {
      selectionPosition = 4;
      startPage = numPages - 4;
    }
    else {
      selectionPosition = 3;
      startPage = page - 2;
    }

    $("#page"+selectionPosition).addClass("active");
    $("#page"+selectionPosition)[0].page = page;
    var selecta = $("#page" + selectionPosition + " a");
    selecta.text(page);
    selecta.attr("href", "#page" + page);

    for (var j = 0; j < 5; ++j) {
      var realPage = startPage + j;
      var elementId = "#page" + (j+1);

      $(elementId)[0].page = realPage;
      var a = $(elementId + " a");
      a.text(realPage);
      a.attr("href", "#page" + realPage);
    }
  }


  $('#page1').click(function () {
    $('#page1').addClass("selected");
    $('#page2').removeClass("selected");
    $('#page3').removeClass("selected");
    $('#page4').removeClass("selected");
    $('#page5').removeClass("selected");
  });

  $('#page2').click(function () {
    $('#page1').removeClass("selected");
    $('#page2').addClass("selected");
    $('#page3').removeClass("selected");
    $('#page4').removeClass("selected");
    $('#page5').removeClass("selected");
  });

  $('#page3').click(function () {
    $('#page1').removeClass("selected");
    $('#page2').removeClass("selected");
    $('#page3').addClass("selected");
    $('#page4').removeClass("selected");
    $('#page5').removeClass("selected");
  });

  $('#page4').click(function () {
    $('#page1').removeClass("selected");
    $('#page2').removeClass("selected");
    $('#page3').removeClass("selected");
    $('#page4').addClass("selected");
    $('#page5').removeClass("selected");
  });

  $('#page5').click(function () {
    $('#page1').removeClass("selected");
    $('#page2').removeClass("selected");
    $('#page3').removeClass("selected");
    $('#page4').removeClass("selected");
    $('#page5').addClass("selected");
  });

  $('#page5').click(function () {
    $('#page1').removeClass("selected");
    $('#page2').removeClass("selected");
    $('#page3').removeClass("selected");
    $('#page4').removeClass("selected");
    $('#page5').addClass("selected");
  });

});

function previousClick(pageData){
  $('li').each(function () {
    if($(this).text() == pageData){
      $(this).addClass("selected");
    }else{
      $(this).removeClass("selected");
    }
  })

}

function nextClick(pageData){
  $('li').each(function () {
    if($(this).text() == pageData){
      $(this).addClass("selected");
    }else{
      $(this).removeClass("selected");
    }
  })
}