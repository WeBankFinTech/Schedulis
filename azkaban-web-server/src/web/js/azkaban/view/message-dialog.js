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

var messageDialogView;
azkaban.MessageDialogView = Backbone.View.extend({
  events: {},

  initialize: function (settings) {
  },

  show: function (title, message, callback) {
    $("#azkaban-message-dialog-title").text(title);
    $("#azkaban-message-dialog-text").html(message);
    this.callback = callback;
    $(this.el).on('hidden.bs.modal', function () {
      if (callback) {
        callback.call();
      }
    });
    $(this.el).modal();
  }
});

var warnDialogView;
azkaban.WarnDialogView = Backbone.View.extend({
  events: {},

  initialize: function (settings) {
  },

  show: function (title, message, buttonText1, buttonText2, callback1, callback2) {
    $("#azkaban-warn-dialog-title").text(title);
    $("#azkaban-warn-dialog-text").html(message);
    $("#azkaban-warn-dialog-button1").text(buttonText1);
    $("#azkaban-warn-dialog-button2").text(buttonText2);
    this.callback1 = callback1;
    this.callback2 = callback2;
    var modalView=$(this.el);
    $("#azkaban-warn-dialog-button1").unbind('click').bind('click',function(){
      if(callback1){
        callback1();
      }
      modalView.modal('hide');
    });
    $("#azkaban-warn-dialog-button2").unbind('click').bind('click',function(){
      if(callback2){
        callback2();
      }
      modalView.modal('hide');
    });
    modalView.modal();
  }
});

var deleteDialogView;
azkaban.DeleteDialogView = Backbone.View.extend({
  events: {},

  initialize: function (settings) {
  },

  show: function (title, message, buttonText1, buttonText2, callback1, callback2) {
    $("#azkaban-delete-dialog-title").text(title);
    $("#azkaban-delete-dialog-text").html(message);
    $("#azkaban-delete-dialog-button1").text(buttonText1);
    $("#azkaban-delete-dialog-button2").text(buttonText2);
    this.callback1 = callback1;
    this.callback2 = callback2;
    var modalView=$(this.el);
    $("#azkaban-delete-dialog-button1").unbind('click').bind('click',function(){
      if(callback1){
        callback1();
      }
      modalView.modal('hide');
    });
    $("#azkaban-delete-dialog-button2").unbind('click').bind('click',function(){
      if(callback2){
        callback2();
      }
      modalView.modal('hide');
    });
    modalView.modal();
  }
});

$(function () {
  messageDialogView = new azkaban.MessageDialogView({
    el: $('#azkaban-message-dialog')
  });

  warnDialogView = new azkaban.WarnDialogView({
    el: $('#azkaban-warn-dialog')
  });

  deleteDialogView = new azkaban.DeleteDialogView({
    el: $('#azkaban-delete-dialog')
  });
});
