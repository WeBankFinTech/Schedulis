package azkaban.imsreport;

import azkaban.Constants;
import azkaban.executor.*;
import azkaban.flow.FlowExecuteType;
import azkaban.project.entity.FlowBusiness;
import azkaban.utils.HttpUtils;
import azkaban.utils.Props;
import org.apache.commons.collections.CollectionUtils;
import org.slf4j.Logger;

import java.util.Date;

public class FlowImsReportServiceImpl extends ImsReportService {

  public FlowImsReportServiceImpl(Props flowProps, Props pluginProps, Props serverProps,
      Logger logger, ExecutableNode node,
      FlowBusiness flowBusiness) {
    super(flowProps, pluginProps, serverProps, logger, flowBusiness);
    this.flow = (ExecutableFlowBase) node;
  }

  @Override
  void setStatus() {
    Status execStatus = flow.getStatus();
    logger.info("flow status is " + execStatus);
    switch (execStatus) {
      case PREPARING:
      case READY:
        imsReport.setStatus("1");
        break;
      case KILLED:
      case FAILED:
        imsReport.setStatus("3");
        break;
      case SUCCEEDED:
        imsReport.setStatus("2");
        break;
      default:
        imsReport.setStatus("4");
    }
  }

  @Override
  void setDataTime() {
    if ("1".equals(imsReport.getStatus())) {
      imsReport.setStartTime(sdf.format(new Date(flow.getStartTime())));
    } else {
      imsReport.setEndTime(sdf.format(new Date(flow.getEndTime())));
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
    imsReport.setJobCode(DmsBusPath
        .createJobCode(serverProps.get(Constants.JobProperties.JOB_BUS_PATH_CODE_PREFIX), flow.getProjectName(),
            flow.getFlowId()));
    if (flow instanceof ExecutableFlow) {
      imsReport.setDmsPath(CollectionUtils.isNotEmpty(((ExecutableFlow) flow).getJobCodeList()));
    } else {
      if (CollectionUtils.isEmpty(HttpUtils
          .getBusPathFromDBOrDms(serverProps, imsReport.getJobCode(), 1, flow.getExecutionId(),
              null, logger))) {
        return false;
      } else {
        imsReport.setDmsPath(true);
      }
    }
    return true;
  }
}
