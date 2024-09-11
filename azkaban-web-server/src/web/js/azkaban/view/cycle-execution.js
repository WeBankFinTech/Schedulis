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
    var url = contextURL + "/executor?ajax=cycleParamVerify";
    var successHandler = function(data) {
        if (data.error) {
            messageDialogView.show(wtssI18n.view.cycleExecutionError, data.error);
            return false;
        } else {
            flowExecuteDialogView.hideExecutionOptionPanel();
            messageDialogView.show(wtssI18n.view.cycleExecution, wtssI18n.view.cycleExecutionSubmitSuccess,
                function() {
                    window.location.href = contextURL + "/executor#cycle-execution";
                    submitCycleFlowFunc(cycleOption);
                }
            );
        }
    };
    $.ajax({
        type: "GET",
        contentType: "application/json",
        url: url,
        data: cycleOption,
        dataType: 'json',
        success: successHandler,
    });
}


function submitCycleFlow(cycleData) {
    executeURL = contextURL + "/executor?ajax=submitCycleFlow";
    $.ajax({
        type: "GET",
        contentType: "application/json; charset=utf-8",
        url: executeURL,
        data: cycleData,
        dataType: 'json'
    });
}