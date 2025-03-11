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
    'keypress input': 'handleKeyPress',
    'click #login-ques-btn':'popover'
  },

  initialize: function (settings) {
    $('#error-msg').hide();
    this.hideHelpPop()
  },

  popover: function () {
    $('#popover').toggle();
  },
  hideHelpPop () {
    $('#popover').hide();
  },
  checkComRequired: function(username, userpwd){
    if (!username && !userpwd) {
        messageBox.show(wtssI18n.login.normalUsernamePasswordReq,'warning');
        return false;
    }
    if (!username) {
        messageBox.show(wtssI18n.login.normalUsernameReq,'warning');
        return false;
    }
    if (!userpwd) {
        messageBox.show(wtssI18n.login.normalPasswordReq,'warning');
        return false;
    }
    return true;
  },
  storageUserName: function (storageUserName, userConfig) {
    var storageUser = localStorage.getItem('systemUserConfig') ? JSON.parse(localStorage.getItem('systemUserConfig')) : [];
    var isExit = storageUser.find(function (item) {
        return item.label === storageUserName;
    })
    if (!isExit) {
        storageUser.push(userConfig);
        localStorage.setItem('systemUserConfig',  JSON.stringify(storageUser));
    }
  },
  handleLogin: function (evt) {
    console.log("Logging in.");
    //var username = $.base64.decode($("#username").val());
    //var userpwd = $.base64.decode($("#userpwd").val());
    var isOps = false;
    var isSys = false;
    var username,password, userpwd,normalUserName,normalPassword
    if (opsLoginCheck == 'true') {
        username = $("#username").val().trim();
        password = $("#userpwd").val().trim();
        userpwd = this.encrypt(password);
        if (!this.checkComRequired(username, password)) return;
      if (this.tabName === 'opsUserLoginTab') {
        normalUserName = username;
        normalPassword = userpwd;
        username = $("#opsUserName").val().trim();
        if (!username) {
            messageBox.show(wtssI18n.login.operationUsernameReq,'warning');
            return;
        }
        userpwd = this.encrypt($("#opsPassword").val());
        isOps = true;
      } else if (this.tabName === 'systemUserLoginTab') {
        normalUserName = username;
        normalPassword = userpwd;
        username = $("#sysUserName").val().trim();
        var sysPassword = $("#sysPassword").val().trim();
        if (!username && !sysPassword) {
            messageBox.show(wtssI18n.login.systemUsernamePasswordReq,'warning');
            return false;
        }
        // 系统用户必须以hduser开头
        if (username.indexOf('hduser') !== 0) {
            messageBox.show(wtssI18n.login.systemUsersFormmter,'warning');
            $("#sysUserName").val('');
            return;
        }
        if (!username) {
            messageBox.show(wtssI18n.login.systemUserReq,'warning');
            return;
        }

        if (!sysPassword) {
            messageBox.show(wtssI18n.login.systemUserPasswordReq,'warning');
            return;
        }
        userpwd = this.encrypt(sysPassword);

        isSys = true;
      }
    } else {
       username = $("#allusername").val().trim();
       password = $("#alluserpwd").val().trim();
       userpwd = this.encrypt(password);
       if (!this.checkComRequired(username, password)) return;
    }
    var frompage = true;
    var that = this;
    $.ajax({
      async: "false",
      url: "/checkin",
      dataType: "json",
      type: "POST",
      data: {
        action: "login",
        username: username,
        userpwd: userpwd,
        normalUserName: normalUserName,
        normalPassword: normalPassword,
        frompage: frompage,
        isOps: isOps,
        isSys: isSys
      },
      success: function (data, textStatus, jqXHR) {
        if (data.error) {
          $('#error-msg').text(data.error);
          $('#error-msg').slideDown('fast');
        } else {
          localStorage.setItem('csrfToken', jqXHR.getResponseHeader("csrfToken"));
          if(that.tabName === 'systemUserLoginTab') {

            var converted = that.toBinary(sysPassword);
            var encoded = btoa(converted);
            that.storageUserName(username, {label: username, value: encoded});
          }
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
    var raw = atob(base64);
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
  toBinary: function (string) {
    var codeUnits = new Uint16Array(string.length);
    for (var i = 0; i < codeUnits.length; i++) {
      codeUnits[i] = string.charCodeAt(i);
    }
    var charCodes = new Uint8Array(codeUnits.buffer);
    var result = "";
    for (var i = 0; i < charCodes.byteLength; i++) {
      result += String.fromCharCode(charCodes[i]);
    }
    return result;
  },
  fromBinary: function (binary) {
    var bytes = new Uint8Array(binary.length);
    for (var i = 0; i < bytes.length; i++) {
      bytes[i] = binary.charCodeAt(i);
    }
    var charCodes = new Uint16Array(bytes.buffer);
    var result = "";
    for (var i = 0; i < charCodes.length; i++) {
      result += String.fromCharCode(charCodes[i]);
    }
    return result;
  },
  render: function () {
  }
});

$(function () {
  loginView = new azkaban.LoginView({ el: $('#checkin-form') });
//   var opsUserNameDom = $('#opsUserName');
//   var sysUserNameDom = $('#sysUserName');
//   var userNameDom = $('#username');
//   function settingUserName () {
//     var userName = userNameDom.val();
//     if (!userName || loginView.tabName === 'userLoginTab') {
//         return;
//     }
//     var userInfo = localStorage.getItem(userName);
//     if (userInfo) {
//         userInfo = JSON.parse(userInfo);
//         loginView.tabName === 'systemUserLoginTab' ? sysUserNameDom.val(userInfo.sysUserName || '') : opsUserNameDom.val(userInfo.opsUserName || '');
//     }
//   }
// 非谷歌浏览器，系统用户密码改成type改成password
    if (navigator.userAgent.indexOf('Chrome') === -1) {
        document.getElementById('sysPassword').setAttribute('type', 'password');
    }
  // tab时间绑定
  $("#loginTab").on('click',function(e) {
      var parentEle = e.target.parentElement;
      // 点击原tab不做处理
      if (parentEle.getAttribute('class') === 'active') {
        return;
      }
      loginView.tabName  =  e.target.getAttribute('name');
      $("#loginTab").children().removeClass();
      $(parentEle).addClass('active');
      if (loginView.tabName === 'opsUserLoginTab') {
        $("#opsUserLoginTab").addClass('in active');
        $("#systemUserLoginTab").removeClass('in active');
        $("#userpwd").removeClass('bottom-radius');
      } else if (loginView.tabName === 'systemUserLoginTab') {
        $("#systemUserLoginTab").addClass('in active');
        $("#opsUserLoginTab").removeClass('in active');
        $("#userpwd").removeClass('bottom-radius');
      } else {
        $("#userpwd").addClass('bottom-radius');
        $("#systemUserLoginTab").removeClass('in active');
        $("#opsUserLoginTab").removeClass('in active');
      }
      $("#error-msg").hide().html('');
  });

  function renderStorageUser (storageUser) {
    try {
        var oldDom = document.getElementById('userDropdown');
        if (!oldDom) {
            return;
        }
        oldDom.innerHTML = '';
        if (storageUser.length > 0) {
            var fragment= document.createDocumentFragment();
            storageUser.forEach(item => {
                var liDom = document.createElement('li');
                var aDom = document.createElement('a');
                aDom.innerText = item.label;
                aDom.setAttribute('href', "javascript:void(0);")
                aDom.setAttribute('name', item.label);
                liDom.appendChild(aDom);
                fragment.appendChild(liDom);
            });
            oldDom.appendChild(fragment);
        }
    } catch (error) {
        console.log(error);
    }
  }
  function hanleStorageUser () {
    var storageUser = localStorage.getItem('systemUserConfig') ? JSON.parse(localStorage.getItem('systemUserConfig')) : [];
    renderStorageUser(storageUser);
    loginView.storageUserList = storageUser;
    loginView.isExitUser = storageUser.length >0
  }
  hanleStorageUser();
  $("#sysUserName").on('focus', function () {
    $('#userDropdown').show();
  });
  $("#sysUserName").on('blur', function (e) {
    if (e.target.value && e.target.value.indexOf('hduser') !== 0) {
        e.target.value ='';
        renderStorageUser(loginView.storageUserList);
        messageBox.show(wtssI18n.login.systemUsersFormmter,'warning');
        return;
    }
    setTimeout(() => {
        $('#userDropdown').hide();
    }, 250);

  });
  $("#sysUserName").on('keyup', function (e) {
    var val = e.target.value.trim().toLowerCase();
    if (val) {
        var userList = loginView.storageUserList.filter(function (item) {
            return item.label.toLowerCase().indexOf(val) > -1;
        })
        loginView.isExitUser && renderStorageUser(userList);
    } else {
        loginView.isExitUser && renderStorageUser(loginView.storageUserList);
    }
  });
  $("#userDropdown").on('click', function (e) {
    if (e.target.tagName === 'A') {
        var name = e.target.name;
        var user = loginView.storageUserList.find(function (v) {
            return v.label === name;
        });
        var decoded = atob(user.value);
        var original = loginView.fromBinary(decoded);
        $("#sysUserName").val(name);
        $("#sysPassword").val(original);
    }
  });


});