package com.webank.wedatashpere.schedulis.jobhook;

import azkaban.Constants;
import azkaban.executor.ExecutableFlowBase;
import azkaban.executor.ExecutableNode;
import azkaban.executor.ExecutorLoader;
import azkaban.hookExecutor.ExecuteWithJobHook;
import azkaban.hookExecutor.HookContext;
import azkaban.hookExecutor.HookContext.HookType;
import azkaban.jobhook.JobHook;
import azkaban.utils.Props;
import azkaban.utils.QualitisUtil;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;

public class DataQualityInspectionHook implements ExecuteWithJobHook {

    private final Logger logger;
    protected volatile ExecutableNode node;
    private final HookContext.HookType hooktype;
    private final Props pluginJobProps;
    private ExecutorLoader executorLoader;
    private Props serverProps;

    public DataQualityInspectionHook(HookContext hookConext, Props pluginJobProps) {
        this.node = hookConext.getNode();
        this.logger = hookConext.getLogger();
        this.hooktype = hookConext.getHookType();
        this.pluginJobProps = pluginJobProps;
        this.executorLoader = hookConext.getExecutorLoader();
        this.serverProps = hookConext.getProps();
    }

    @Override
    public void run() throws Exception {
        logger.info("DataQualityInspectionHook start execute , hook type {}", hooktype);
        boolean hookSwitch = serverProps.getBooleanDefaultFalse("job.hook.switch", false);
        String jobCodePrefix = serverProps.getString(Constants.JobProperties.JOB_BUS_PATH_CODE_PREFIX);
        QualitisUtil qualitisUtil = new QualitisUtil(this.pluginJobProps);
        String jobCode = createJobCode(jobCodePrefix);

        logger.info("jobCode: " + jobCode);

        JobHook jobHook = this.executorLoader.getJobHook(jobCode);

        long timeout = pluginJobProps.getLong("qualitis.timeout", 14400000);
        long interval = pluginJobProps.getLong("qualitis.interval", 60000);
        long submitTimeout = pluginJobProps.getLong("qualitis.submit.timeout", 180000);
        int threadPoolSize = pluginJobProps.getInt("qualitis.threadPool.size", 10);

        logger.info("Qualitis timeout: " + timeout + " ms");

        if (hookSwitch && HookType.PRE_EXEC_SYS_HOOK.equals(this.hooktype) && jobHook != null) {
            // 前置 hook
            Map<Long, String> prefixRules = jobHook.getPrefixRules();
            logger.info("prefix Qualitis hook , ruleGroupInfoArr info :{}" , prefixRules.toString());
            if (!prefixRules.isEmpty()) {
                for (Long prefixRuleId : prefixRules.keySet()) {
                    String roleGroupInfo = prefixRules.get(prefixRuleId);
                    String[] roleGroupInfoArr = roleGroupInfo.split(",");
                    String interrupt = roleGroupInfoArr[0];
                    String createUser = roleGroupInfoArr[1];
                    String executionUser = roleGroupInfoArr[2];

                    if ("0".equals(interrupt)) {
                        // 非阻断，异步，直接提交
                        try {
                            final ExecutorService exec = Executors.newFixedThreadPool(1);
                            Callable<String> call = () -> {
                                String applicationId = qualitisUtil.submitTask(prefixRuleId,
                                    createUser,
                                    executionUser, node.getRunDate());
                                logger.info("Application Id: " + applicationId);
                                return applicationId;
                            };
                            try {
                                Future<String> future = exec.submit(call);
                                future.get(submitTimeout, TimeUnit.MILLISECONDS);
                            } catch (TimeoutException ex) {
                                logger.error("Submit Qualitis task timeout {}", ex);
                            }
                            exec.shutdown();
                        } catch (Throwable e) {
                            logger.warn("Submit Qualitis task error: " + e.getMessage());
                        }
                    } else {
                        // 阻断，同步
                        String applicationId = "";
                        final ExecutorService exec = Executors.newFixedThreadPool(threadPoolSize);
                        Callable<String> call = () -> {
                            String applicationIdInCall = "";
                            try {
                                applicationIdInCall = qualitisUtil.submitTask(prefixRuleId,
                                    createUser,
                                    executionUser, node.getRunDate());
                                logger.info("Application Id: " + applicationIdInCall);
                            } catch (Throwable e) {
                                throw new QualitisTaskException(
                                    "Qualitis task submit error: " + e.getMessage());
                            }
                            return applicationIdInCall;
                        };
                        try {
                            Future<String> future = exec.submit(call);
                            applicationId = future.get();
                            future.get(submitTimeout, TimeUnit.MILLISECONDS);
                        } catch (TimeoutException ex) {
                            logger.error("Submit Qualitis task timeout {}", ex);
                        }
                        logger.info("Submit Qualitis task success , now to start get status");
                        String finalApplicationId = applicationId;
                        String taskStatus = "";
                        Callable<String> callStatus = () -> {
                            String taskStatusInCall = "";
                            Double statusDouble = 0.0;
                            while (true) {
                                try {
                                    taskStatusInCall = qualitisUtil.getTaskStatus(
                                        finalApplicationId);
                                    statusDouble = Double.parseDouble(taskStatusInCall);
                                } catch (Exception e) {
                                    throw new QualitisTaskException(
                                        "Get Qualitis task error: " + e.getMessage());
                                }
                                if (QualitisTaskStatusEnum.FINISHED.getCode()
                                    .equals(statusDouble)) {
                                    logger.info(QualitisTaskStatusEnum.FINISHED.getMessage());
                                    break;
                                } else if (QualitisTaskStatusEnum.FAILED.getCode()
                                    .equals(statusDouble)) {
                                    throw new QualitisTaskException("Qualitis task error: "
                                        + QualitisTaskStatusEnum.FAILED.getMessage());
                                } else if (QualitisTaskStatusEnum.NOT_PASS.getCode()
                                    .equals(statusDouble)) {
                                    throw new QualitisTaskException("Qualitis task error: "
                                        + QualitisTaskStatusEnum.NOT_PASS.getMessage());
                                } else if (QualitisTaskStatusEnum.TASK_SUBMIT_FAILED.getCode()
                                    .equals(
                                        statusDouble)) {
                                    throw new QualitisTaskException("Qualitis task error: "
                                        + QualitisTaskStatusEnum.TASK_SUBMIT_FAILED.getMessage());
                                } else if (QualitisTaskStatusEnum.ARGUMENT_NOT_CORRECT.getCode()
                                    .equals(
                                        statusDouble)) {
                                    throw new QualitisTaskException("Qualitis task error: "
                                        + QualitisTaskStatusEnum.ARGUMENT_NOT_CORRECT.getMessage());
                                } else if (QualitisTaskStatusEnum.SUBMITTED.getCode()
                                    .equals(statusDouble)) {
                                    logger.info(QualitisTaskStatusEnum.SUBMITTED.getMessage());
                                } else if (QualitisTaskStatusEnum.RUNNING.getCode()
                                    .equals(statusDouble)) {
                                    logger.info(QualitisTaskStatusEnum.RUNNING.getMessage());
                                } else if (QualitisTaskStatusEnum.SUCCESSFUL_CREATE_APPLICATION.getCode()
                                    .equals(statusDouble)) {
                                    logger.info(
                                        QualitisTaskStatusEnum.SUCCESSFUL_CREATE_APPLICATION.getMessage());
                                } else if (QualitisTaskStatusEnum.SUBMIT_PENDING.getCode()
                                    .equals(statusDouble)) {
                                    logger.info(QualitisTaskStatusEnum.SUBMIT_PENDING.getMessage());
                                }
                                logger.info("Qualitis interval: " + interval + " ms");
                                Thread.sleep(interval);
                            }

                            return taskStatusInCall;
                        };

                        try {
                            Future<String> future = exec.submit(callStatus);
                            future.get(timeout, TimeUnit.MILLISECONDS);
                        } catch (TimeoutException ex) {
                            logger.error("Hook timeout {}", ex);
                        } finally {
                            exec.shutdown();
                        }
                    }
                }
            }
        } else if (hookSwitch && HookType.POST_EXEC_SYS_HOOK.equals(this.hooktype)
            && jobHook != null) {
            // 后置 hook
            Map<Long, String> suffixRules = jobHook.getSuffixRules();
            logger.info("suffix Qualitis hook ,  ruleGroupInfoArr info :{}" , suffixRules.toString());
            if (!suffixRules.isEmpty()) {
                for (Long suffixRuleId : suffixRules.keySet()) {
                    String roleGroupInfo = suffixRules.get(suffixRuleId);
                    String[] roleGroupInfoArr = roleGroupInfo.split(",");
                    String interrupt = roleGroupInfoArr[0];
                    String createUser = roleGroupInfoArr[1];
                    String executionUser = roleGroupInfoArr[2];
                    if ("0".equals(interrupt)) {
                        // 非阻断，异步，直接提交
                        try {
                            final ExecutorService exec = Executors.newFixedThreadPool(1);
                            Callable<String> call = () -> {
                                String applicationId = qualitisUtil.submitTask(suffixRuleId,
                                    createUser,
                                    executionUser, node.getRunDate());
                                logger.info("Application Id: " + applicationId);
                                return applicationId;
                            };
                            try {
                                Future<String> future = exec.submit(call);
                                future.get(submitTimeout, TimeUnit.MILLISECONDS);
                            } catch (TimeoutException ex) {
                                logger.error("Submit Qualitis task timeout {}", ex);
                            }
                            exec.shutdown();
                        } catch (Throwable e) {
                            logger.warn("Submit Qualitis task error: " + e.getMessage());
                        }
                    } else {
                        // 阻断，同步
                        String applicationId = "";
                        final ExecutorService exec = Executors.newFixedThreadPool(threadPoolSize);
                        Callable<String> call = () -> {
                            String applicationIdInCall = "";
                            try {
                                applicationIdInCall = qualitisUtil.submitTask(suffixRuleId,
                                    createUser,
                                    executionUser, node.getRunDate());
                                logger.info("Application Id: " + applicationIdInCall);
                            } catch (Throwable e) {
                                throw new QualitisTaskException(
                                    "Qualitis task submit error: " + e.getMessage());
                            }
                            return applicationIdInCall;
                        };
                        try {
                            Future<String> future = exec.submit(call);
                            applicationId = future.get();
                            future.get(submitTimeout, TimeUnit.MILLISECONDS);
                        } catch (TimeoutException ex) {
                            logger.error("Submit Qualitis task timeout {}", ex);
                        }

                        logger.info("Submit Qualitis task success , now to start get status");
                        String finalApplicationId = applicationId;
                        String taskStatus = "";
                        Callable<String> callStatus = () -> {
                            String taskStatusInCall = "";
                            Double statusDouble = 0.0;
                            while (true) {
                                try {
                                    taskStatusInCall = qualitisUtil.getTaskStatus(
                                        finalApplicationId);
                                    statusDouble = Double.parseDouble(taskStatusInCall);
                                } catch (Exception e) {
                                    throw new QualitisTaskException(
                                        "Get Qualitis task error: " + e.getMessage());
                                }
                                if (QualitisTaskStatusEnum.FINISHED.getCode()
                                    .equals(statusDouble)) {
                                    logger.info(QualitisTaskStatusEnum.FINISHED.getMessage());
                                    break;
                                } else if (QualitisTaskStatusEnum.FAILED.getCode()
                                    .equals(statusDouble)) {
                                    throw new QualitisTaskException("Qualitis task error: "
                                        + QualitisTaskStatusEnum.FAILED.getMessage());
                                } else if (QualitisTaskStatusEnum.NOT_PASS.getCode()
                                    .equals(statusDouble)) {
                                    throw new QualitisTaskException("Qualitis task error: "
                                        + QualitisTaskStatusEnum.NOT_PASS.getMessage());
                                } else if (QualitisTaskStatusEnum.TASK_SUBMIT_FAILED.getCode()
                                    .equals(
                                        statusDouble)) {
                                    throw new QualitisTaskException("Qualitis task error: "
                                        + QualitisTaskStatusEnum.TASK_SUBMIT_FAILED.getMessage());
                                } else if (QualitisTaskStatusEnum.ARGUMENT_NOT_CORRECT.getCode()
                                    .equals(
                                        statusDouble)) {
                                    throw new QualitisTaskException("Qualitis task error: "
                                        + QualitisTaskStatusEnum.ARGUMENT_NOT_CORRECT.getMessage());
                                } else if (QualitisTaskStatusEnum.SUBMITTED.getCode()
                                    .equals(statusDouble)) {
                                    logger.info(QualitisTaskStatusEnum.SUBMITTED.getMessage());
                                } else if (QualitisTaskStatusEnum.RUNNING.getCode()
                                    .equals(statusDouble)) {
                                    logger.info(QualitisTaskStatusEnum.RUNNING.getMessage());
                                } else if (QualitisTaskStatusEnum.SUCCESSFUL_CREATE_APPLICATION.getCode()
                                    .equals(statusDouble)) {
                                    logger.info(
                                        QualitisTaskStatusEnum.SUCCESSFUL_CREATE_APPLICATION.getMessage());
                                } else if (QualitisTaskStatusEnum.SUBMIT_PENDING.getCode()
                                    .equals(statusDouble)) {
                                    logger.info(QualitisTaskStatusEnum.SUBMIT_PENDING.getMessage());
                                }
                                logger.info("Qualitis interval: " + interval + " ms");
                                Thread.sleep(interval);
                            }

                            return taskStatusInCall;
                        };

                        try {
                            Future<String> future = exec.submit(callStatus);
                            future.get(timeout, TimeUnit.MILLISECONDS);
                        } catch (TimeoutException ex) {
                            logger.error("Hook timeout {}", ex);
                        } finally {
                            exec.shutdown();
                        }
                    }
                }
            }
        }

        logger.info("DataQualityInspectionHook end execute , hook type {}" , hooktype);
    }

    @NotNull
    private String createJobCode(String jobCodePrefix) {
        String flowId = this.node.getExecutableFlow().getId();
        String projectName = this.node.getExecutableFlow().getProjectName();
        // 获取任务节点直接父级工作流名
        ExecutableFlowBase parentFlow = this.node.getParentFlow();
        String jobCode = "";
        if (parentFlow == null) {
            // 最外层工作流上的节点
            jobCode =
                jobCodePrefix + "/" + projectName.toLowerCase() + "/" + flowId.toLowerCase() + "/"
                    + this.node.getId().toLowerCase();
        } else {
            // 子工作流内的节点使用直接父级工作流名（末端节点名）
            jobCode =
                jobCodePrefix + "/" + projectName.toLowerCase() + "/" + parentFlow.getFlowId()
                    .toLowerCase() + "/" + this.node.getId().toLowerCase();
        }
        return jobCode;
    }
}
