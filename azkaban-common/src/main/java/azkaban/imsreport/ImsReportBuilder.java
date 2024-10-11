package azkaban.imsreport;

import azkaban.executor.ExecutableFlowBase;
import azkaban.executor.ExecutableNode;
import azkaban.project.entity.FlowBusiness;
import azkaban.utils.HttpUtils;
import azkaban.utils.Props;
import okhttp3.FormBody;
import okhttp3.RequestBody;
import org.slf4j.Logger;

public class ImsReportBuilder {

  public static void report2Ims(Props flowProps, Props pluginProps, Props serverProps,
      Logger logger, ExecutableNode node, FlowBusiness flowBusiness) {
    try {
      ImsReportService imsReportService;
      if (node instanceof ExecutableFlowBase) {
        imsReportService = new FlowImsReportServiceImpl(flowProps, pluginProps, serverProps, logger,
            node, flowBusiness);
      } else {
        imsReportService = new NodeImsReportServiceImpl(flowProps, pluginProps, serverProps, logger,
            node, flowBusiness);
      }

      ImsReport imsReport = imsReportService.getImsReport();
      if (imsReport == null) {
        return;
      }
      send2Ims(imsReport, logger);
    } catch (Exception e) {
      logger.warn("send request failed", e);
    }

  }

  private static void send2Ims(ImsReport imsReport, Logger logger) throws Exception {
    if (imsReport.getActionUrl() == null) {
      logger.error("获取注册接口失败");
      return;
    }
    for (String dcnNumber : imsReport.getDcnNumbers().split(",")) {
      RequestBody requestBody = new FormBody.Builder()
          .add("subSystemId", imsReport.getSubSystemId())
          .add("jobCode", imsReport.getJobCode())
          .add("jobDate", imsReport.getJobDate())
          .add("ip", imsReport.getLocalHost())
          .add("dcnNumber", dcnNumber)
          .add("status", imsReport.getStatus())
          .add("alertLevel", imsReport.getAlertLevel())
          .add("dataVersion", imsReport.getDataVersion())
          .add("startTime", imsReport.getStartTime())
          .add("endTime", imsReport.getEndTime())
          .add("executeType", imsReport.getExecuteType())
          .build();
      logger.info(String.format(
          "url is : %s, params is : subSystemId=%s&jobCode=%s&jobDate=%s&ip=%s&dcnNumber=%s&status"
              + "=%s&alertLevel=%s&dataVersion=%s&startTime=%s&endTime=%s&executeType=%s",
          imsReport.getActionUrl(), imsReport.getSubSystemId(), imsReport.getJobCode(),
          imsReport.getJobDate(), imsReport.getLocalHost(), dcnNumber, imsReport.getStatus(),
          imsReport.getAlertLevel(), imsReport.getDataVersion(), imsReport.getStartTime(),
          imsReport.getEndTime(), imsReport.getExecuteType()));
      String result = HttpUtils.httpClientIMSHandle(imsReport.getActionUrl(), requestBody, null);
      logger.info("result is : " + result);
    }
  }
}
