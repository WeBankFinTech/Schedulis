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

var loginView;
azkaban.LoginView = Backbone.View.extend({
  events: {
    "click #checkin-submit": "handleLogin",
    'keypress input': 'handleKeyPress'
  },

  initialize: function (settings) {
    $('#error-msg').hide();
  },

  handleLogin: function (evt) {
    console.log("Logging in.");
    //var username = $.base64.decode($("#username").val());
    //var userpwd = $.base64.decode($("#userpwd").val());
    var username = $("#username").val();
    var userpwd = this.encrypt($("#userpwd").val());
    var frompage = true;

    $.ajax({
      async: "false",
      url: contextURL + "/checkin",
      dataType: "json",
      type: "POST",
      data: {
        action: "login",
        username: username,
        userpwd: userpwd,
        frompage: frompage
      },
      success: function (data, textStatus, jqXHR) {
        if (data.error) {
          $('#error-msg').text(data.error);
          $('#error-msg').slideDown('fast');
        } else {
          localStorage.setItem('csrfToken', jqXHR.getResponseHeader("csrfToken"));
          document.location.reload();
        }
      }
    });
  },

  encrypt: function (str) {
      var encrypt = new JSEncrypt();
      encrypt.setPublicKey('-----BEGIN PUBLIC KEY-----' + $("#publicKey").val() + '-----END PUBLIC KEY-----');
      return this.base64toHEX(encrypt.encrypt(str));
    },

    base64toHEX: function (base64) {
      var raw = decodeURIComponent(escape(atob(base64)));
      var HEX = '';
      for (var i = 0; i < raw.length; i++) {
        var _hex = raw.charCodeAt(i).toString(16)
        HEX += (_hex.length == 2 ? _hex : '0' + _hex);
      }
      return HEX;
    },

  handleKeyPress: function (evt) {
    if (evt.charCode == 13 || evt.keyCode == 13) {
      this.handleLogin();
    }
  },

  render: function () {
  }
});

$(function () {
  loginView = new azkaban.LoginView({el: $('#checkin-form')});
});
