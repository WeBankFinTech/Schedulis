$.namespace('azkaban');

function executeCycleFlow(executeData) {
    var cycleOption = getCycleOption(executeData);
    if (cycleOption) {
        checkCycleParam(cycleOption, submitCycleFlow);
    }
}

function getCycleOption(executeData) {
    var cycleOption = executeData;
    cycleOption.cycleErrorOption = $("#cycle-error-option").val();
    cycleOption.cycleFlowInterruptAlertLevel = $("#cycleFlow-interrupt-alert-level").val();
    cycleOption.cycleFlowInterruptEmails = $("#cycleFlow-interrupt-emails").val()
    return cycleOption;
}

function checkCycleParam(cycleOption, submitCycleFlowFunc) {
  var url = "/executor?ajax=cycleParamVerify";
    var successHandler = function(data) {
        if (data.error) {
      $("#execute-btn").attr("disabled", false).removeClass("button-disable");
            messageDialogView.show(wtssI18n.view.cycleExecutionError, data.error);
            return false;
    } else if (data.alert) {
      $("#execute-btn").attr("disabled", false).removeClass("button-disable");
      messageDialogView.show(wtssI18n.view.cycleExecution, data.alert,
          function () {
            window.location.href = "/executor#cycle-execution";
            submitCycleFlowFunc(cycleOption);
          });
        } else {
            flowExecuteDialogView.hideExecutionOptionPanel();
            messageDialogView.show(wtssI18n.view.cycleExecution, wtssI18n.view.cycleExecutionSubmitSuccess,
                function() {
            setTimeout(function() {
                window.location.href = "/executor#cycle-execution";
                    submitCycleFlowFunc(cycleOption);
            }, 1500);
                }
            );
        }
    };
  $.get(url, cycleOption, successHandler, "json");
}


function submitCycleFlow(cycleData) {
  executeURL = "/executor?ajax=submitCycleFlow";
    $.ajax({
        type: "GET",
        contentType: "application/json; charset=utf-8",
        url: executeURL,
        data: cycleData,
        dataType: 'json'
    });
}