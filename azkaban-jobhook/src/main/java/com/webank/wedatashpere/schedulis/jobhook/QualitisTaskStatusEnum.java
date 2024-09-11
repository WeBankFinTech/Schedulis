package com.webank.wedatashpere.schedulis.jobhook;

public enum QualitisTaskStatusEnum {

  SUBMITTED(1.0, "已提交执行器", "SUBMITTED"),
  RUNNING(3.0, "运行中", "RUNNING"),
  FINISHED(4.0, "通过校验", "FINISHED"),
  FAILED(7.0, "失败", "FAILED"),
  NOT_PASS(8.0, "未通过校验", "NOT_PASS"),
  TASK_SUBMIT_FAILED(9.0, "任务初始化失败", "TASK_SUBMIT_FAILED"),
  SUCCESSFUL_CREATE_APPLICATION(10.0, "任务初始化成功", "SUCCESSFUL_CREATE_APPLICATION"),
  ARGUMENT_NOT_CORRECT(11.0, "参数错误", "ARGUMENT_NOT_CORRECT"),
  SUBMIT_PENDING(12.0, "提交阻塞", "SUBMIT_PENDING"),
  ;

  private Double code;
  private String message;
  private String state;

  QualitisTaskStatusEnum(Double code, String message, String state) {
    this.code = code;
    this.message = message;
    this.state = state;
  }

  public Double getCode() {
    return code;
  }

  public String getMessage() {
    return message;
  }

  public String getState() {
    return state;
  }

}
