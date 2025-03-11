package azkaban.execapp.listener;

import azkaban.ServiceProvider;
import azkaban.event.Event;
import azkaban.event.EventListener;
import azkaban.execapp.FlowRunner;
import azkaban.executor.ExecutableFlow;
import azkaban.spi.EventType;
import azkaban.utils.Props;
import org.slf4j.Logger;

import java.util.HashMap;
import java.util.Map;

import static azkaban.Constants.ConfigurationKeys.AZKABAN_SERVER_HOST_NAME;

public class FlowRunnerEventListener implements EventListener {

    public FlowRunnerEventListener() {
    }

    /**
     * 方法会并发访问，如果有全局数据修改请加同步
     * @param flowRunner
     * @return
     */
    private Map<String, String> getFlowMetadata(final FlowRunner flowRunner) {
        final ExecutableFlow flow = flowRunner.getExecutableFlow();
        final Props props = ServiceProvider.SERVICE_PROVIDER.getInstance(Props.class);
        final Map<String, String> metaData = new HashMap<>();
        metaData.put("flowName", flow.getId());
        metaData.put("azkabanHost", props.getString(AZKABAN_SERVER_HOST_NAME, "unknown"));
        metaData.put("projectName", flow.getProjectName());
        metaData.put("submitUser", flow.getSubmitUser());
        metaData.put("executionId", String.valueOf(flow.getExecutionId()));
        metaData.put("startTime", String.valueOf(flow.getStartTime()));
        metaData.put("submitTime", String.valueOf(flow.getSubmitTime()));
        return metaData;
    }

    /**
     * 方法会并发访问，如果有全局数据修改请加同步
     * @param event
     * @return
     */
    @Override
    public void handleEvent(final Event event) {
        if (event.getType() == EventType.FLOW_STARTED) {
            final FlowRunner flowRunner = (FlowRunner) event.getRunner();
            final ExecutableFlow flow = flowRunner.getExecutableFlow();
            Logger flowRunnerLogger = flowRunner.getFlowRunnerLogger();
            if (null != flowRunnerLogger) {
                flowRunnerLogger.info("Flow started: " + flow.getId());
            }
            //FlowRunner.this.azkabanEventReporter.report(event.getType(), getFlowMetadata(flowRunner));
        } else if (event.getType() == EventType.FLOW_FINISHED) {
            final FlowRunner flowRunner = (FlowRunner) event.getRunner();
            final ExecutableFlow flow = flowRunner.getExecutableFlow();
            Logger flowRunnerLogger = flowRunner.getFlowRunnerLogger();
            if (null != flowRunnerLogger) {
                flowRunnerLogger.info("Flow ended: " + flow.getId());
            }
       /* final Map<String, String> flowMetadata = getFlowMetadata(flowRunner);
        flowMetadata.put("endTime", String.valueOf(flow.getEndTime()));
        flowMetadata.put("flowStatus", flow.getStatus().name());
        FlowRunner.this.azkabanEventReporter.report(event.getType(), flowMetadata);*/
        }
    }
}
