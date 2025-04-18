package azkaban.utils;

import azkaban.flow.CommonJobProperties;
import java.io.IOException;
import java.net.URISyntaxException;
import java.security.NoSuchAlgorithmException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.TimeUnit;
import okhttp3.Call;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang.RandomStringUtils;
import org.apache.commons.lang.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Tools for qualitis
 *
 * @author lebronwang
 * @date 2022/08/04
 **/
public class QualitisUtil {

  private static Logger logger = LoggerFactory.getLogger(QualitisUtil.class);

  String SUBMIT_TASK_PATH = "";
  String GET_TASK_STATUS_PATH = "";
  String createSubmitRulePath = "";
  String baseUrl = "";
  String appId = "";
  String appToken = "";
  private Props props;
  private Properties properties;

  private OkHttpClient okHttpClient;

  public QualitisUtil(Props props) {
    this.props = props;

    this.baseUrl = this.props.getString("qualitis.baseUrl");
    this.appId = this.props.getString("qualitis.appId");
    this.appToken = this.props.getString("qualitis.appToken");
    this.SUBMIT_TASK_PATH = this.props.getString("qualitis.submitTask.path");
    this.GET_TASK_STATUS_PATH = this.props.getString("qualitis.get.taskStatus.path");
    this.okHttpClient = new OkHttpClient.Builder()
            .callTimeout(this.props.getInt("qualitis.submitTask.timeout", 60000), TimeUnit.MILLISECONDS)
            .connectTimeout(10, TimeUnit.SECONDS)
            .writeTimeout(20, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .build();
  }

  public QualitisUtil(Properties properties) {
    this.properties = properties;
    this.baseUrl = this.properties.getProperty("qualitis.baseUrl");
    this.appId = this.properties.getProperty("qualitis.appId");
    this.appToken = this.properties.getProperty("qualitis.appToken");
    this.createSubmitRulePath = this.properties.getProperty("qualitis.createSubmitRule.path");
    this.GET_TASK_STATUS_PATH = this.properties.getProperty("qualitis.getTaskStatus.path");

    this.okHttpClient = new OkHttpClient.Builder()
            .callTimeout(Integer.valueOf(this.properties.getProperty("qualitis.submitTask.timeout", "60000")), TimeUnit.MILLISECONDS)
            .connectTimeout(10, TimeUnit.SECONDS)
            .writeTimeout(20, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .build();
  }

  /**
   * 提交 Qualitis 任务
   *
   * @param groupId       规则组 ID
   * @param createUser
   * @param executionUser
   * @return
   * @throws IOException
   */
  public String submitTask(long groupId, String createUser, String executionUser, String runDate)
      throws Exception {

    logger.info("Submitting Qualitis task(groupId: " + groupId + ") ... ");
    String applicationId = "";
    String url = "";
    try {
      url = buildUrI(baseUrl, SUBMIT_TASK_PATH, appId, appToken,
          RandomStringUtils.randomNumeric(5), String.valueOf(System.currentTimeMillis()));
    } catch (NoSuchAlgorithmException | URISyntaxException e) {
      logger.error("Build Qualitis URL error: " + e.getMessage());
    }

    Map<String, Object> param = new HashMap<>();
    param.put("group_id", groupId);
    param.put("create_user", createUser);
    param.put("execution_user", executionUser);
    param.put("execution_param", "run_date:" + runDate);

    String json = GsonUtils.toJson(param);
    logger.info("Request Json: " + json);
    MediaType applicationJson = MediaType.parse("application/json;charset=utf-8");
    RequestBody requestBody = RequestBody.create(json, applicationJson);

    Request request = new Request.Builder()
        .url(url)
        .post(requestBody)
        .build();

    Call call = okHttpClient.newCall(request);
    Response response = call.execute();
    String resultJson = response.body().string();
    IOUtils.closeQuietly(response);
    logger.info("Response Json: " + resultJson);
    if (StringUtils.isNotEmpty(resultJson)) {
      Map<String, Object> resultMap = GsonUtils.json2Map(resultJson);
      logger.info(String.valueOf(resultMap));
      String code = (String) resultMap.get("code");
      if ("200".equals(code)) {
        applicationId = (String) ((Map<String, Object>) resultMap.get("data")).get(
            "application_id");
        logger.info(resultMap.get("message").toString());
      } else {
        throw new RuntimeException(resultMap.get("message").toString());
      }
    }
    return applicationId;
  }

  public String createAndSubmitRule(String dBName, String tableName,
      String partitionName, int i) throws IOException {
    logger.info("");
    String applicationId = "";
    String url = "";
    try {
      url = buildUrI(baseUrl, createSubmitRulePath, appId, appToken,
          RandomStringUtils.randomNumeric(5), String.valueOf(System.currentTimeMillis()));
    } catch (NoSuchAlgorithmException | URISyntaxException e) {
      logger.error("Build Qualitis URL error: " + e.getMessage());
    }

    // JSON 请求参数
    Map<String, Object> param = new HashMap<>();
    param.put("create_user", properties.getProperty("user.to.proxy"));
    param.put("execution_user", properties.getProperty("user.to.proxy"));
    // expectFileAmountCount(\"HDP-GZPC-BDAP-UAT.dqm_test.test_dqm_left:ds=${run_date}\", null, false).addRuleMetricWithCheck(\"BDP-DQM_custom-metric_filetest_Year\", false, false, false).fixValueNotEqual(0)
    // 集群名
    String clusterName = properties.getProperty("cluster.name");
    // 子系统名 WTSS-BDPWFM/WTSS-BDAPWFM
    StringBuilder sb = new StringBuilder("expectFileAmountCount(\"").append(clusterName)
        .append(".").append(dBName)
        .append(".").append(tableName)
        .append(StringUtils.isNotEmpty(partitionName) ? ":" + partitionName : "")
        .append("\", null, false).addRuleMetricWithCheck(\"")
        .append(String.format(properties.getProperty("qualitis.rule.metric"),
            properties.getProperty(CommonJobProperties.JOB_ID).replace("_", "-")))
        .append("\", false, false, false).fixValueNotEqual(0)");
   // System.out.println(sb);
    logger.info(sb+"");
    param.put("template_function", sb.toString());
    param.put("project_name", properties.getProperty("azkaban.flow.projectname"));
    param.put("rule_name", properties.getProperty("azkaban.flow.projectname") + properties
        .getProperty(CommonJobProperties.JOB_ID) + i);

    String json = GsonUtils.toJson(param);
    logger.info("Request Json: " + json);
    MediaType applicationJson = MediaType.parse("application/json;charset=utf-8");
    RequestBody requestBody = RequestBody.create(json, applicationJson);



    Request request = new Request.Builder()
        .url(url)
        .post(requestBody)
        .build();

    Call call = okHttpClient.newCall(request);
    Response response = call.execute();
    String resultJson = response.body().string();
    IOUtils.closeQuietly(response);
    logger.info("Response Json: " + resultJson);
    if (StringUtils.isNotEmpty(resultJson)) {
      Map<String, Object> resultMap = GsonUtils.json2Map(resultJson);
      String code = (String) resultMap.get("code");
      if ("200".equals(code)) {
        Map<String, Object> applicationDetail = (Map<String, Object>) ((Map<String, Object>) (resultMap.get(
            "data"))).get("application_detail");
        Map<String, Object> project_application = ((List<Map<String, Object>>) (applicationDetail.get(
            "project_applications"))).get(0);
        applicationId = (String) project_application.get("application_id");
        logger.info(resultMap.get("message").toString());
      } else {
        throw new RuntimeException(resultMap.get("message").toString());
      }
    }
    return applicationId;
  }

  /**
   * 获取 Qualitis 任务状态
   *
   * @param applicationId
   * @return
   */
  public String getTaskStatus(String applicationId) throws Exception {

    logger.info("Getting Qualitis task status(ApplicationId: " + applicationId + ") ... ");
    if (StringUtils.isEmpty(applicationId)) {
      logger.error("Empty Qualitis application ID");
    }
    String url = "";
    try {
      url = buildUrI(baseUrl, GET_TASK_STATUS_PATH.replace("{applicationId}", applicationId)
          , appId, appToken, RandomStringUtils.randomNumeric(5),
          String.valueOf(System.currentTimeMillis()));
    } catch (NoSuchAlgorithmException | URISyntaxException e) {
      logger.error("Build Qualitis URL error: " + e.getMessage());
    }


    Request request = new Request.Builder()
        .url(url)
        .get()
        .build();

    Call call = okHttpClient.newCall(request);
    Response response = null;
    String resultJson = "";
    String status = "";
    try {
      response = call.execute();
      resultJson = response.body().string();
      IOUtils.closeQuietly(response);
      logger.info("Response Json: " + resultJson);
      if (StringUtils.isNotEmpty(resultJson)) {
        Map<String, Object> resultMap = GsonUtils.json2Map(resultJson);
        logger.info(String.valueOf(resultMap));
        String code = (String) resultMap.get("code");
        if ("200".equals(code)) {
          status = ((Map<String, Object>) (resultMap.get("data"))).get(
              "application_status") + "";
        }
      }
    } catch (IOException e) {
      logger.error("Request to Qualitis failed: " + e.getMessage());
    }

    return status;
  }

  public static String buildUrI(String baseUrl, String path, String appId, String appToken,
      String nonce, String timestamp) throws NoSuchAlgorithmException, URISyntaxException {
    String signature = getSignature(appId, appToken, nonce, timestamp);
    StringBuffer uriBuffer = new StringBuffer(baseUrl);
    uriBuffer.append(path).append("?")
        .append("app_id=").append(appId).append("&")
        .append("nonce=").append(nonce).append("&")
        .append("timestamp=").append(timestamp).append("&")
        .append("signature=").append(signature);

    return uriBuffer.toString();
  }

  public static String getSignature(String appId, String appToken, String nonce, String timestamp)
      throws NoSuchAlgorithmException {
    return Sha256Utils.getSHA256L32(Sha256Utils.getSHA256L32(appId + nonce + timestamp) + appToken);
  }


  public static void main(String[] args) throws Exception {

    /*Map<Long, Integer> ruleGroup = new HashMap<>();
    ruleGroup.put(39211L, 0);
    ruleGroup.put(37456L, 0);


    String applicationId = submitTask(ruleList, "allenzhou", "allenzhou");
    System.out.println(applicationId);
    System.out.println(
        "---------------------------------------------------------------------------");
    String taskStatus = getTaskStatus(applicationId);
    System.out.println(taskStatus);*/

    String appId = "linkis_id";
    String appToken = "***REMOVED***";
    String nonce = RandomStringUtils.randomNumeric(5);
    String timestamp = String.valueOf(System.currentTimeMillis());
    String signature = getSignature(appId, appToken, nonce, timestamp);

    System.out.println("nonce = " + nonce);
    System.out.println("timeStamp = " + timestamp);
    System.out.println("signature = " + signature);

    Properties properties = new Properties();
    properties.setProperty("qualitis.baseUrl", "http://***REMOVED***:7080/");
    properties.setProperty("qualitis.appId", appId);
    properties.setProperty("qualitis.appToken", appToken);
    properties.setProperty("qualitis.createSubmitRule.path",
        "qualitis/outer/api/v1/bdp_client/create_and_submit");
    properties.setProperty("qualitis.getTaskStatus.path",
        "qualitis/outer/api/v1/application/{applicationId}/status/");

    properties.setProperty("user.to.proxy", "hadoop");
    properties.setProperty("user.to.proxy", "hadoop");
    properties.setProperty("cluster.name", "HDP-GZPC-BDAP-UAT");
    properties.setProperty("qualitis.submitTask.timeout","180000");
    properties.setProperty("subsystem.name", "WTSS-BDPWFM");
    properties.setProperty("azkaban.flow.flowid", "test");
    properties.setProperty("azkaban.flow.projectname", "test");
    properties.setProperty(CommonJobProperties.JOB_ID,"4134314");
    properties.setProperty("qualitis.rule.metric","WTSS-BDPWFM_general-metric_%s_Daily");

    String dBName = "dqm_test";
    String tableName = "test_dqm_left";
    String partitionName = "ds=2023-05-08";

    QualitisUtil qualitisUtil = new QualitisUtil(properties);
    String applicationId = qualitisUtil.createAndSubmitRule(dBName, tableName, partitionName, 0);
    String applicationId1 = qualitisUtil.createAndSubmitRule(dBName, tableName, partitionName, 1);
    System.out.println(applicationId);
    System.out.println(applicationId1);

    String taskStatus = qualitisUtil.getTaskStatus(applicationId);
    System.out.println(taskStatus);
  }
}
