package azkaban.imsreport;

import azkaban.executor.ExecutableFlowBase;
import azkaban.executor.ExecutableNode;
import azkaban.project.entity.FlowBusiness;
import azkaban.utils.HttpUtils;
import azkaban.utils.Props;
import org.apache.commons.lang.StringUtils;
import org.slf4j.Logger;

import java.net.InetAddress;
import java.text.SimpleDateFormat;

public abstract class ImsReportService {

  protected ImsReport imsReport = new ImsReport();
  protected Props flowProps;
  protected Props pluginProps;
  protected Props serverProps;
  protected Logger logger;
  protected ExecutableNode node;
  protected FlowBusiness flowBusiness;
  protected ExecutableFlowBase flow;
  protected SimpleDateFormat sdf= new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
  protected SimpleDateFormat sdf1= new SimpleDateFormat("yyyyMMdd");

  public ImsReportService(Props flowProps, Props pluginProps, Props serverProps,
      Logger logger, FlowBusiness flowBusiness) {
    this.flowProps = flowProps;
    this.pluginProps = pluginProps;
    this.serverProps = serverProps;
    this.logger = logger;
    this.flowBusiness = flowBusiness;
  }

  abstract void setStatus();

  abstract void setDataTime();

  abstract void setExecuteType();

  abstract boolean checkJobCode();

  public ImsReport getImsReport() {
    try {
      if (!preCheck()) {
        return null;
      }
      setParam();
      return imsReport;
    } catch (Exception e) {
      logger.error("ims build param error", e);
    }
    return null;
  }

  private boolean preCheck() {

    if (!checkJobCode()) {
      return false;
    }

    if (!imsReport.isDmsPath()) {
      String imsSwitch = (String) flow.getOtherOption()
          .get(flow.getFlowType() == 3 ? "scheduleImsSwitch" : "eventScheduleImsSwitch");
      if ((flowProps == null || HttpUtils.getValue(flowProps, "reportIMS") == null || !"true"
          .equalsIgnoreCase(HttpUtils.getValue(flowProps, "reportIMS").trim())) && !"1"
          .equals(imsSwitch)) {
        logger.info("IMS close.");
        return false;
      }
    }
    return true;
  }

  private void setParam() {
    imsReport.setJobDate(flow.getExecutionOptions().isCrossDay()?sdf1.format(flow.getStartTime()):flow.getRunDate());
    imsReport.setActionUrl(pluginProps.get("ims.job.report.url"));
    imsReport.setDataVersion(imsReport.isDmsPath() ? "2" : "1");

    if (flowProps != null) {
      imsReport.setDcnNumbers(StringUtils.isEmpty(HttpUtils.getValue(flowProps, "dcnNumber")) ? ""
          : HttpUtils.getValue(flowProps, "dcnNumber"));
      imsReport.setAlertLevel(
          StringUtils.isEmpty(HttpUtils.getValue(flowProps, "alertLevel")) ? "2"
              : HttpUtils.getValue(flowProps, "alertLevel"));
    }

    if (flowBusiness == null) {
      if (flowProps != null) {
        logger.info("get ims from properties");
        imsReport.setSubSystemId(
            StringUtils.isEmpty(HttpUtils.getValue(flowProps, "subSystemId")) ? pluginProps
                .get("ims.job.report.subSystemId") : HttpUtils.getValue(flowProps, "subSystemId"));
      }
    } else {
      logger.info("get ims from page");
      imsReport.setSubSystemId(StringUtils.isEmpty(flowBusiness.getSubsystem()) ? pluginProps
          .get("ims.job.report.subSystemId") : flowBusiness.getSubsystem());
    }
    try {
      imsReport.setLocalHost(InetAddress.getLocalHost().getHostAddress());
    } catch (Exception e) {
      logger.error("cant not get localhost, + " + e);
    }
    setStatus();
    setDataTime();
    setExecuteType();
  }
}
