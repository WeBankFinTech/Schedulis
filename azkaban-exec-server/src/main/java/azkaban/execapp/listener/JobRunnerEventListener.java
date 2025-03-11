package azkaban.execapp.listener;

import azkaban.Constants;
import azkaban.ServiceProvider;
import azkaban.event.Event;
import azkaban.event.EventData;
import azkaban.event.EventListener;
import azkaban.execapp.FlowRunner;
import azkaban.execapp.JobRunner;
import azkaban.executor.ExecutableNode;
import azkaban.spi.EventType;
import azkaban.utils.Props;
import org.slf4j.Logger;

import java.util.HashMap;
import java.util.Map;

import static azkaban.Constants.ConfigurationKeys.AZKABAN_SERVER_HOST_NAME;

public class JobRunnerEventListener implements EventListener {

    private FlowRunner flowRunner;
    public JobRunnerEventListener(FlowRunner flowRunner) {
        this.flowRunner = flowRunner;
    }

    private Map<String, String> getJobMetadata(final JobRunner jobRunner) {
        final ExecutableNode node = jobRunner.getNode();
        final Props props = ServiceProvider.SERVICE_PROVIDER.getInstance(Props.class);
        final Map<String, String> metaData = new HashMap<>();
        metaData.put("jobId", node.getId());
        metaData.put("executionID", String.valueOf(node.getExecutableFlow().getExecutionId()));
        metaData.put("flowName", node.getExecutableFlow().getId());
        metaData.put("startTime", String.valueOf(node.getStartTime()));
        metaData.put("jobType", String.valueOf(node.getType()));
        metaData.put("azkabanHost", props.getString(AZKABAN_SERVER_HOST_NAME, "unknown"));
        metaData.put("jobProxyUser",
                jobRunner.getProps().getString(Constants.JobProperties.USER_TO_PROXY, null));
        return metaData;
    }

    @Override
    public void handleEvent(final Event event) {
        final JobRunner jobRunner = (JobRunner) event.getRunner();
        final ExecutableNode node = jobRunner.getNode();
        Logger flowRunnerLogger = flowRunner.getFlowRunnerLogger();
        if (event.getType() == EventType.JOB_STATUS_CHANGED) {
            flowRunner.updateFlow(System.currentTimeMillis());
        } else if (event.getType() == EventType.JOB_FINISHED) {
            // FIXME The task execution needs to update the database information.
            if (node.getLastStartTime() > 0) {
                node.setStartTime(node.getLastStartTime());
            }
            flowRunner.updateFlow(System.currentTimeMillis());
            flowRunner.printThreadInfo();
            if (null != flowRunnerLogger) {
                flowRunnerLogger.info("finished node: " + node.getNestedId());
            }
            synchronized (this) {
                flowRunner.dealFinishedEvent(event);
            }
        } else if (event.getType() == EventType.JOB_STARTED) {
            final EventData eventData = event.getData();
            if (null != flowRunnerLogger) {
                flowRunnerLogger.info("Job Started: " + eventData.getNestedId());
            }
            flowRunner.handleJobAndEmbeddedFlowExecTimeoutAlter(jobRunner, eventData);
        }
    }
}
