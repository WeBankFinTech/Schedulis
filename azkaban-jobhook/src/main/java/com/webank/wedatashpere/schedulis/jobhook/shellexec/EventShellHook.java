package com.webank.wedatashpere.schedulis.jobhook.shellexec;

import azkaban.executor.ExecutableNode;
import azkaban.executor.ExecutorLoader;
import azkaban.hookExecutor.ExecuteWithJobHook;
import azkaban.hookExecutor.HookConstants;
import azkaban.hookExecutor.HookContext;
import azkaban.hookExecutor.HookContext.HookType;
import azkaban.jobExecutor.ProcessJob;
import azkaban.utils.Props;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;

import java.util.Arrays;

/**
 * A hook with pre/post cmd feature for specify nodes
 * 
 * @author yonghxia
 * @since 20230223
 *
 */
public class EventShellHook implements ExecuteWithJobHook {

    private final Logger logger;
    protected volatile ExecutableNode node;
    private final HookContext.HookType hooktype;
    private final Props pluginJobProps;
    @SuppressWarnings("unused")
    private ExecutorLoader executorLoader;
    private Props serverProps;

    public EventShellHook(HookContext hookConext, Props pluginJobProps) {
        this.node = hookConext.getNode();
        this.logger = hookConext.getLogger();
        this.hooktype = hookConext.getHookType();
        this.pluginJobProps = pluginJobProps;
        this.executorLoader = hookConext.getExecutorLoader();
        this.serverProps = hookConext.getProps();
    }

    @Override
    public void run() throws Exception {
        try {
            boolean hookSwitch = serverProps.getBoolean(HookConstants.WTSS_JOB_HOOK_SWITCH, false);
            if (!hookSwitch) {
                logger.info("Skipped hook: {}, hook switch off", this.getClass().getCanonicalName());
                return;
            }
            Props jobParams = this.node.getInputProps();
            boolean hookEventSwitch = jobParams.getBoolean(HookConstants.WTSS_JOB_HOOK_EVENT_SWITCH, false);
            if (!hookEventSwitch) {
                // logger.info("Skipped hook: {}, hook event switch off",
                // this.getClass().getCanonicalName());
                return;
            }
            String hookName = jobParams.getString(HookConstants.WTSS_JOB_HOOK_NAME, "");
            if (!StringUtils.equalsIgnoreCase(hookName, this.getClass().getName())) {
                logger.info("Skipped hook: {}, hookName not matched", this.getClass().getCanonicalName());
                return;
            }


            // default only for event/data checker
            String nodeTypes = jobParams.getString(HookConstants.WTSS_JOB_HOOK_EVENT_NODE_TYPES, "eventchecker,datachecker");
            if (!(Arrays.asList(StringUtils.split(nodeTypes, ",")).contains(node.getType()))) {
                logger.info("{}-Hook setted,but skipped,Only for {} node!,BUT is:{}", node.getType(), nodeTypes,
                        this.getClass().getCanonicalName());
                return;
            }

            /*-
             -- some params/access not ready,so not now ,post currently
             boolean isReceive = "RECEIVE".equals(node.getInputProps().get("msg.type"));
             String hookCommandPre = jobParams.getString("job.hook.event.cmd.pre", "");
            // before sender
            if (!isReceive && HookType.PRE_EXEC_SYS_HOOK.equals(this.hooktype)
                    && StringUtils.isNotBlank(hookCommandPre)) {
                exeCmd(jobParams, hookCommandPre);
            }*/

            // after
            String hookCommandPost = jobParams.getString(HookConstants.WTSS_JOB_HOOK_EVENT_CMD_POST, "");
            if (HookType.POST_EXEC_SYS_HOOK.equals(this.hooktype)
                    && StringUtils.isNotBlank(hookCommandPost)) {
                exeCmd(jobParams, hookCommandPost);
            }

            logger.info("Finished hook:{}", this.getClass().getCanonicalName());
        } catch (Exception e) {
            boolean breakFlag = this.node.getInputProps().getBoolean(HookConstants.WTSS_JOB_HOOK_EVENT_ERROR_BREAK, false);
            if (breakFlag) {
                throw e;
            }
            logger.error("Failed to run hook: {}", this.getClass().getCanonicalName(), e);
        } finally {
            // do something
        }
    }

    protected void exeCmd(Props jobParams, String hookCommand) throws Exception {
        long start = System.currentTimeMillis();
        String nodeId = this.node.getId();
        logger.info("{} - current nodeId: {},{},{},with:{}", this.getClass().getCanonicalName(), nodeId,
                node.getType(), hooktype, hookCommand);
        // jobParams.put("EventShellHook.nodeId", nodeId);
        String msgBody = getOutput("msg.body");
        String msgTopic = get("msg.topic");
        String msgSender = get("msg.sender");
        String msgReceiver = get("msg.receiver");
        String dataObject = get("data.object");
        String msg = "--msgBody=" + msgBody + " --msgTopic=" + msgTopic + " --msgSender=" + msgSender
                + " --msgReceiver=" + msgReceiver + " --nodeType=" + node.getType() + " --dataObject=" + dataObject;
        // write back info
        jobParams.put("EventShellHook.msg", msg);
        String old = jobParams.getString("command", "");
        jobParams.put("command", hookCommand);

        ProcessJob processJob = new ProcessJob(node.getId() + "-hookCmd", pluginJobProps, jobParams, logger);
        processJob.run();

        jobParams.put("command", old);
        logger.info("{}-Done with:{}ms", this.getClass().getCanonicalName(), (System.currentTimeMillis() - start));
    }

    private String get(String key) {
        return StringUtils.defaultString(node.getInputProps().get(key), "");
    }

    private String getOutput(String key) {
        return StringUtils.defaultString(node.getOutputProps().get(key), "");
    }
}
