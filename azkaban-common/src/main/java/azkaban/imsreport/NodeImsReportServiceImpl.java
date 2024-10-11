package azkaban.imsreport;

import azkaban.Constants;
import azkaban.ServiceProvider;
import azkaban.executor.DmsBusPath;
import azkaban.executor.ExecutableNode;
import azkaban.executor.ExecutorLoader;
import azkaban.executor.Status;
import azkaban.flow.FlowExecuteType;
import azkaban.project.entity.FlowBusiness;
import azkaban.utils.HttpUtils;
import azkaban.utils.Props;
import org.apache.commons.collections.CollectionUtils;
import org.slf4j.Logger;

import java.util.Date;

public class NodeImsReportServiceImpl extends ImsReportService {

  public NodeImsReportServiceImpl(Props flowProps, Props pluginProps, Props serverProps,
      Logger logger, ExecutableNode node, FlowBusiness flowBusiness) {
    super(flowProps, pluginProps, serverProps, logger, flowBusiness);
    this.node = node;
    this.flow = node.getExecutableFlow();
  }

  @Override
  void setStatus() {
    if (!("eventchecker".equals(node.getType()) && "RECEIVE"
        .equals(node.getInputProps().get("msg.type")))) {
      Status execStatus = node.getStatus();
      logger.info("job status is " + execStatus);
      switch (execStatus) {
        case READY:
        case QUEUED:
          imsReport.setStatus("1");
          break;
        case KILLED:
        case FAILED:
          imsReport.setStatus("3");
          break;
        case SUCCEEDED:
        case RETRIED_SUCCEEDED:
          imsReport.setStatus("2");
          break;
        default:
          imsReport.setStatus("4");
      }

    } else {
      imsReport.setStatus("4");
    }
  }

  @Override
  void setDataTime() {
    // 非信号任务
    if (!("eventchecker".equals(node.getType()) && "RECEIVE"
        .equals(node.getInputProps().get("msg.type")))) {
      if ("1".equals(imsReport.getStatus())) {
        imsReport.setStartTime(sdf.format(new Date(node.getStartTime())));
      } else {
        imsReport.setEndTime(sdf.format(new Date(node.getEndTime())));
      }
    } else {
      imsReport.setEndTime(sdf.format(new Date(node.getEndTime())));
    }
  }

  @Override
  void setExecuteType() {
    String executeType = "";
    if (flow.getFlowType() == FlowExecuteType.TIMED_SCHEDULING.getNumVal()
        || flow.getFlowType() == FlowExecuteType.EVENT_SCHEDULE.getNumVal()) {
      executeType = "调度执行";
    } else {
      executeType = flow.getExecutionOptions().getExecuteType();
    }
    imsReport.setExecuteType(executeType);
  }

  @Override
  boolean checkJobCode() {

    // 非信号接收任务
    if (!("eventchecker".equals(node.getType()) && "RECEIVE"
        .equals(node.getInputProps().get("msg.type")))) {
      imsReport.setJobCode(DmsBusPath
          .createJobCode(serverProps.get(Constants.JobProperties.JOB_BUS_PATH_CODE_PREFIX), flow.getProjectName(),
              node.getParentFlow().getFlowId(), node.getId()));
      imsReport.setDmsPath(CollectionUtils.isNotEmpty(node.getJobCodeList()));
    } else {
      // 信号接收任务，原有逻辑
      if (!Status.isSucceeded(node.getStatus())) {
        return false;
      }
      ExecutorLoader executorLoader = ServiceProvider.SERVICE_PROVIDER
          .getInstance(ExecutorLoader.class);
      String topic = node.getInputProps().getString("msg.topic", "");
      String msgName = node.getInputProps().getString("msg.name", "");
      String receiver = node.getInputProps().getString("msg.receiver", "");
      String eventType = executorLoader.getEventType(topic, msgName);
      if ("eventchecker".equals(eventType)) {
        imsReport.setJobCode(
            "EVENTCHECKER/" + serverProps.get(Constants.JobProperties.JOB_BUS_PATH_CODE_PREFIX).split("/")[1] + "/" + topic
                + "/" + msgName + "/" + receiver);
      } else if ("rmb".equals(eventType)) {
        imsReport.setJobCode(
            "RMBSERVICE/" + pluginProps.get("rmb.service.id") + "/" + topic + "/" + msgName + "/"
                + receiver);
      } else {
        return false;
      }
      if (CollectionUtils.isEmpty(HttpUtils
              .getBusPathFromDBOrDms(serverProps, imsReport.getJobCode(), 1, flow.getExecutionId(), null, logger))) {
        return false;
      } else {
        imsReport.setDmsPath(true);
      }
    }
    return true;
  }
}
