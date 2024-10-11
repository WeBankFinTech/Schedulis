package azkaban.jobtype.util;

import azkaban.Constants;
import azkaban.flow.CommonJobProperties;
import azkaban.jobtype.connectors.druid.WBDruidFactory;
import azkaban.jobtype.rmb.sender.RMBMsg;
import azkaban.jobtype.rmb.sender.RMBService;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import org.apache.commons.lang.RandomStringUtils;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.sql.DataSource;
import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.lang.management.ManagementFactory;
import java.sql.CallableStatement;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Properties;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Created by kirkzhou on 7/4/18.
 */
public class RMBSender {

  public final static String TARGETDCN = "rmb.targetDcn";
  public final static String SERTRVICEID = "rmb.serviceId";
  public final static String MESSAGE = "rmb.message";
  public final static String MSGTYPE = "rmb.messageType";
  public final static String FLOW_EXEC_ID = Constants.FlowProperties.AZKABAN_FLOW_EXEC_ID;
  public final static String FLOW_FLOW_ID = "azkaban.flow.flowid";
  public final static String JOB_ID = CommonJobProperties.JOB_ID;
  public final static String JOB_ATTEMPT = CommonJobProperties.JOB_ATTEMPT;
  public final static String PSZ_TENANT_ID = "rmb.pszTenantId";
  public final static String REQUEST_TIMEOUT = "rmb.request.timeout";
  public static String TOPICENV = "rmb.environment";
  public static String DEFAULTENV = "rmb.default.environment";
  public static String RMB_SERIAL_NUMBER ="env.rmbSerialNumber";
  public static String RMB_CONFIG_PROPERTIES_FILE_PATH = "rmb.config.properties.file.path";

  private Properties p;

  private static final Logger logger = LoggerFactory.getLogger(RMBSender.class);

  public static final Pattern NO_STANDARD_STR_PATTERN = Pattern.compile("[a-zA-Z_0-9@\\-]+");

  public RMBSender(String jobName, Properties p) {
    this.p = p;
  }

  public void run() {
    Properties rmbConfigProperties = getProperties(
            p.getProperty(RMB_CONFIG_PROPERTIES_FILE_PATH, "/appcom/Install/AzkabanInstall/wtss-exec/plugins/jobtypes/rmbconfig.properties"));
    if((!p.containsKey(TOPICENV)) || "".equals(p.getProperty(TOPICENV)) || p.getProperty(TOPICENV) == null){
      TOPICENV = rmbConfigProperties.getProperty(DEFAULTENV).toUpperCase();
      logger.info("use default rmb configration");
    }else{
      TOPICENV = p.getProperty(TOPICENV).toUpperCase();
    }

    logger.info(
        "TARGETDCN: " + p.getProperty(TARGETDCN) + ",SERTRVICEID: " + p.getProperty(SERTRVICEID)
            + ",MESSAGE: " + p.getProperty(MESSAGE) + ",MSGTYPE: " + p.getProperty(MSGTYPE)
            + ",ENV: " + TOPICENV);

    getPid();
    if (p == null) {
      throw new RuntimeException("Properties is null. Can't continue");
    }
    if (checkParamMap(p, TARGETDCN)) {
      throw new RuntimeException("parameter " + TARGETDCN + " can not be empty.");
    }
    if(p.getProperty(TARGETDCN).length() != 3 && p.getProperty(TARGETDCN).length() != 9){
      throw new RuntimeException("parameter " + TARGETDCN + " is not correct.");
    }
    if (checkParamMap(p, SERTRVICEID)) {
      throw new RuntimeException("parameter " + SERTRVICEID + " can not be empty.");
    }
    if (checkParamMap(p, MESSAGE)) {
      throw new RuntimeException("parameter " + MESSAGE + " can not be empty.");
    }
    if (!isJson(p.getProperty(MESSAGE))) {
      throw new RuntimeException("Message must be JSON.");
    }
    if (checkParamMap(p, MSGTYPE)) {
      throw new RuntimeException("parameter " + MSGTYPE + " can not be empty.");
    }
    if (checkParamMap(p, FLOW_EXEC_ID)) {
      throw new RuntimeException("parameter " + FLOW_EXEC_ID + " can not be empty.");
    }
    if (checkParamMap(p, JOB_ID)) {
      throw new RuntimeException("parameter " + JOB_ID + " can not be empty.");
    }
    if (checkParamMap(p, FLOW_FLOW_ID)) {
      throw new RuntimeException("parameter " + FLOW_FLOW_ID + " can not be empty.");
    }
    if (checkParamMap(p, JOB_ATTEMPT)) {
      throw new RuntimeException("parameter " + JOB_ATTEMPT + " can not be empty.");
    }

    if ("SYNC".equals(p.getProperty(MSGTYPE).toUpperCase()) || "ASYNC"
        .equals(p.getProperty(MSGTYPE).toUpperCase())) {
      logger.info("MessageType format is right.");
    }else{
      throw new RuntimeException("parameter " + MSGTYPE + " is not correct.");
    }
    logger.info("输入参数检测通过！");

    Properties rmbProperties = getProperties(
        rmbConfigProperties.getProperty("rmb.client.url") + TOPICENV
            + "/rmb-client.properties");

    // 设置使用的weservice.properties文件
    System.setProperty("weservice.conf.dir",rmbConfigProperties.getProperty("rmb.client.url") + TOPICENV + "/");

    String rmbNum = generateRmbSerialNumber(rmbProperties);

    RMBMsg msg = new RMBMsg(p.getProperty(TARGETDCN), p.getProperty(SERTRVICEID),
            p.getProperty(MESSAGE), p.getProperty(MSGTYPE), rmbNum,
        p.containsKey(PSZ_TENANT_ID), p.getProperty(PSZ_TENANT_ID, ""));

    System.setProperty("confPath",
        rmbConfigProperties.getProperty("rmb.client.url") + TOPICENV + "/");

    RMBService rmbclient = new RMBService(msg, rmbProperties);

    String reqTimeout = p.getProperty(REQUEST_TIMEOUT, "6000");

    if ("ASYNC".equals(msg.getMessageType().toUpperCase())) {
      rmbclient.sendAsyncRMBMessage(msg);
    } else {
      rmbclient.sendSyncRMBMessage(msg, reqTimeout);
    }

    saveMsg(p,rmbNum);

  }

  private String generateRmbSerialNumber(Properties rmbProps) {
    String rmbNumber = RandomStringUtils.randomNumeric(18);
    String dcn = (String) rmbProps.get("rmb.client.system.dcn");
    String id = (String) rmbProps.get("rmb.client.system.id");

    String now = LocalDate.now().format(DateTimeFormatter.ofPattern("yyMMdd"));
    String rmbSerialNumber = now + "0" + dcn + id + rmbNumber;

    return rmbSerialNumber;
  }

  private void saveMsg(Properties rmbProperties, String rmbNum) {
    final String UPSERT_WEMQ_BIZNO = "UPDATE execution_jobs "
            + "SET wemq_bizno=? "
            + "WHERE exec_id=? AND flow_id=? AND job_id=? AND attempt=?";
    DataSource dataSource = WBDruidFactory.getMsgInstance(rmbProperties, RMBSender.logger);
    try (Connection connection = dataSource.getConnection();
         CallableStatement pstmt = connection.prepareCall(UPSERT_WEMQ_BIZNO)) {
      pstmt.setString(1, rmbNum);
      pstmt.setString(2, p.getProperty(Constants.FlowProperties.AZKABAN_FLOW_EXEC_ID));
      pstmt.setString(3, p.getProperty("azkaban.flow.flowid"));
      pstmt.setString(4, p.getProperty("azkaban.job.id"));
      pstmt.setString(5, p.getProperty("azkaban.job.attempt"));
      int rs = pstmt.executeUpdate();
      if (rs == 1) {
        logger.info("Write msg to db success!");
      } else {
        logger.error("Write msg to db failed!");
      }
    } catch (SQLException e) {
      logger.error("Write msg to db failed", e);
    }

  }

  public void cancel() throws InterruptedException {

    throw new RuntimeException("Kill this rmbsender.");

  }

  private boolean checkParamMap(Properties p, String key) {
    boolean checkFlag = false;
    if (!p.containsKey(key)) {//判断参数是否存在
      throw new RuntimeException("parameter " + key + " is empty.");
    }
    if (p.containsKey(key)) {//判断参数是否为空字符串
      if (StringUtils.isEmpty(p.getProperty(key))) {
        checkFlag = true;
      }
    }
    if (!MESSAGE.equals(key) && StringUtils.contains(p.getProperty(key), " ")) {
      throw new RuntimeException("parameter " + key + " can not contains space !");
    }
//    if (!checkNoStandardStr(p.getProperty(key))) {
//      throw new RuntimeException("参数 " + key + " 不能包含字母数字_@-以外的字符 !");
//    }
//    if (p.getProperty(key).length() > 200) {
//      throw new RuntimeException("参数 " + key + " 长度不能超过 200 !");
//    }
    return checkFlag;
  }

  private boolean checkNoStandardStr(String param) {
    Matcher matcher = NO_STANDARD_STR_PATTERN.matcher(param);
    return matcher.matches();
  }

  private String getPid() {
    // get name representing the running Java virtual machine.
    String name = ManagementFactory.getRuntimeMXBean().getName();
    System.out.println(name);
    // get pid
    String pid = name.split("@")[0];
    logger.info("RMBSender Pid is:" + pid);
    return pid;
  }

  public boolean isJson(String content) {
    try {
      JsonElement jsonStr = JsonParser.parseString(content);
      logger.info("Message is Json Format ");
      return true;
    } catch (Exception e) {
      logger.error("Message isn't Json Format ");
      return false;
    }
  }

  public static Properties getProperties(String path) {
    Properties props = new Properties();
    try {
      InputStream in = new BufferedInputStream(new FileInputStream(new File(path)));
      props.load(in);
    } catch (Exception ex) {
      logger.error("Read properties file failed ", ex);
    }
    return props;
  }
}
