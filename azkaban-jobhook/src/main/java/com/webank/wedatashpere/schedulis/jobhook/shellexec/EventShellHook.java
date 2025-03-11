package com.webank.wedatashpere.schedulis.jobhook.shellexec;

import java.io.File;
import java.util.Arrays;
import java.util.Map;

import azkaban.flow.CommonJobProperties;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;

import azkaban.executor.ExecutableNode;
import azkaban.executor.ExecutorLoader;
import azkaban.hookExecutor.ExecuteWithJobHook;
import azkaban.hookExecutor.HookConstants;
import azkaban.hookExecutor.HookContext;
import azkaban.hookExecutor.HookContext.HookType;
import azkaban.jobExecutor.ProcessJob;
import azkaban.utils.Props;

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
            String nodeTypes = jobParams.getString(HookConstants.WTSS_JOB_HOOK_EVENT_NODE_TYPES, "");
            if (!(Arrays.asList(StringUtils.split(nodeTypes, ",")).contains(node.getType()))) {
                logger.info("{}-Hook setted,but skipped,Only for {} node!,BUT is:{}",
                        this.getClass().getCanonicalName(), node.getType(), nodeTypes);
                return;
            }


            // workspace folder not created
            // -- some params/access not ready,so not now ,post currently
            // before
            String hookCommandPre = jobParams.getString("wtss.job.hook.event.cmd.pre", "");
            if (HookType.PRE_EXEC_SYS_HOOK.equals(this.hooktype) && StringUtils.isNotBlank(hookCommandPre)) {
                exeCmd(jobParams, hookCommandPre);
            }


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

    private String get(String key) {
        if (node.getInputProps() == null) {
            return "";
        }
        return StringUtils.defaultString(node.getInputProps().get(key), "");
    }

    private String getOutput(String key) {
        if (node.getOutputProps() == null) {
            return "";
        }
        return StringUtils.defaultString(node.getOutputProps().get(key), "");
    }

    protected void exeCmd(Props jobParams, String hookCommand) throws Exception {
        long start = System.currentTimeMillis();
        String nodeId = this.node.getId();
        logger.info("{} - starting cmd, current nodeId: {},{},{},with:{}", this.getClass().getCanonicalName(), nodeId,
                node.getType(), hooktype, hookCommand);
        String msgBody = getOutput("msg.body");
        String msgTopic = get("msg.topic");
        String msgSender = get("msg.sender");
        String msgReceiver = get("msg.receiver");
        String dataObject = get("data.object");
        String msg = "--msgBody=" + msgBody + " --msgTopic=" + msgTopic + " --msgSender=" + msgSender
                + " --msgReceiver=" + msgReceiver + " --nodeType=" + node.getType() + " --dataObject=" + dataObject;
        // write back info
        jobParams.put("EventShellHook.msg", msg);

        // init if cleaned
        final String flowName = this.node.getParentFlow().getFlowId();
        final int executionId = this.node.getParentFlow().getExecutionId();

        Props jobProps = Props.clone(jobParams);
        if (!jobProps.containsKey("azkaban.job.id")) {
            jobProps.put("azkaban.job.id", nodeId);
        }
        jobProps.put("azkaban.job.innodes", "");// depend not required
        if (!jobProps.containsKey("azkaban.job.execid")) {
            jobProps.put("azkaban.job.execid", executionId);
        }
        if (!jobProps.containsKey("azkaban.flow.flowid")) {
            jobProps.put("azkaban.flow.flowid", flowName);
        }
        jobProps.put(CommonJobProperties.IGNORE_PARSE_JOBSERVERID, "true");
        EventShellProcessJob processJob =
                new EventShellProcessJob(node.getId() + "-hookCmd", Props.clone(pluginJobProps), jobProps, logger);
        jobProps.put("command", hookCommand);
        processJob.run();
        jobProps.removeLocal(CommonJobProperties.IGNORE_PARSE_JOBSERVERID);
        logger.info("{}-Done with:{}ms", this.getClass().getCanonicalName(), (System.currentTimeMillis() - start));
    }


    /**
     * <pre>
     * 实现新的ProcessJob，重写以下方法：
    initPropsFiles： 返回空集合
    initOverAllPropsFiles 返回空集合
    generateProperties  实现空方法
    getCommandList 返回想要执行的命令
    
    new ProcessJob时，job属性改为深度拷贝的方式，不使用同一个，防止processJob里面的替换导致问题
    
    hook出现异常退出时调用下ProcessJob的cancel方法
     * </pre>
     * 
     * @author yonghxia
     *
     */
    private class EventShellProcessJob extends ProcessJob {

        public EventShellProcessJob(String jobId, Props sysProps, Props jobProps, Logger log) {
            super(jobId, sysProps, jobProps, log);
            Map<String, String> cmds = this.jobProps.getMapByPrefix(COMMAND_PREFIX);
            for (String key : cmds.keySet()) {
                // 不取command开头的其它
                logger.info("remove command:{}", COMMAND_PREFIX + key);
                jobProps.removeLocal(COMMAND_PREFIX + key);
            }
        }

        @Override
        public File[] initPropsFiles() {
            File[] files = super.initPropsFiles();
            this.jobProps.removeLocal(ENV_PREFIX + JOB_PROP_ENV);
            this.jobProps.removeLocal(ENV_PREFIX + JOB_NAME_ENV);
            this.jobProps.removeLocal(ENV_PREFIX + JOB_OUTPUT_PROP_FILE);
            return files;
        }

        @Override
        public File initOverAllPropsFiles() {
            return null;
        }

        @Override
        public void generateProperties(File outputFile) {
            // nothing
    }

        /*- @Override
        protected List<String> getCommandList() {
            final List<String> commands = new ArrayList<>();
            if (this.jobProps.containsKey(COMMAND)) {
                commands.add(this.jobProps.getString(COMMAND));
            }
            return commands;
        }*/

        @Override
        public void cancel() throws InterruptedException {
            super.cancel();
        }
    }
}
