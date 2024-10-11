package com.webank.wedatashpere.schedulis.jobhook.demo;

import azkaban.executor.ExecutableNode;
import azkaban.executor.ExecutorLoader;
import azkaban.hookExecutor.ExecuteWithJobHook;
import azkaban.hookExecutor.HookContext;
import azkaban.utils.Props;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;

public class DemoHook implements ExecuteWithJobHook {

    private final Logger logger;
    protected volatile ExecutableNode executableNode;
    private final HookContext.HookType hooktype;
    private final Props pluginJobProps;
    private ExecutorLoader executorLoader;
    private Props serverProps;

    public DemoHook(HookContext hookConext, Props pluginJobProps) {
        this.executableNode = hookConext.getNode();
        this.logger = hookConext.getLogger();
        this.hooktype = hookConext.getHookType();
        this.pluginJobProps = pluginJobProps;
        this.executorLoader = hookConext.getExecutorLoader();
        this.serverProps = hookConext.getProps();
    }

    @Override
    public void run() throws Exception {
        try {
            /**
             * 非阻断hook，请把逻辑写在try-catch块中
             */
            boolean hookSwitch = serverProps.getBoolean("job.hook.switch", false);
            if (!hookSwitch) {
                logger.info("Skipped hook: {}, hook switch is off", this.getClass().getCanonicalName());
                return;
            }

            Props jobParams = this.executableNode.getInputProps();
            String hookName = jobParams.getString("job.sys.hook.name", "");
            if (!StringUtils.equalsIgnoreCase(hookName, this.getClass().getCanonicalName())) {
                /**
                 * 注意，一定要增加此逻辑，否则该hook会对所有任务生效，造成意料之外的影响
                 */
                logger.info("Skipped hook: {}, hookName not matched", this.getClass().getCanonicalName());
                return;
            }

            logger.info("Running hook");

            String filPropKey1 = serverProps.getString("demo.key1");
            String filPropKey2 = serverProps.getString("demo.key2");
            String filPropKey3 = serverProps.getString("demo.key3");
            String filPropKey4 = serverProps.getString("demo.key4");

            logger.info("props from file: {}={}, {}={}, {}={}, {}={}",
                    "demo.key1", filPropKey1,
                    "demo.key2", filPropKey2,
                    "demo.key3", filPropKey3,
                    "demo.key4", filPropKey4
            );

            String jobPropKey1 = serverProps.getString("demo.job.key1");
            String jobPropKey2 = serverProps.getString("demo.job.key2");
            String jobPropKey3 = serverProps.getString("demo.job.key3");
            String jobPropKey4 = serverProps.getString("demo.job.key4");

            logger.info("props from job: {}={}, {}={}, {}={}, {}={}",
                    "demo.job.key1", jobPropKey1,
                    "demo.job.key2", jobPropKey2,
                    "demo.job.key3", jobPropKey3,
                    "demo.job.key4", jobPropKey4
            );

            logger.info("Finished hook");
        } catch (Exception e) {
            logger.error("Failed to run hook: {}", this.getClass().getCanonicalName(), e);
        } finally {
            // do something
        }
    }
}
