/*
 * Copyright 2020 WeBank
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.webank.wedatasphere.schedulis.common.utils;

import azkaban.executor.ExecutableFlow;
import azkaban.executor.Status;
import azkaban.utils.Props;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import okhttp3.*;
import org.apache.commons.lang.StringUtils;
import org.jetbrains.annotations.NotNull;
import org.joda.time.format.DateTimeFormat;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.TimeUnit;

/**
 *
 */
public class HttpUtils {

  private static final Logger logger = LoggerFactory.getLogger(HttpUtils.class);

  public static Map<String, String> getReturnMap(String dataStr){
    Map<String, String> dataMap = new HashMap<>();
    GsonBuilder gb = new GsonBuilder();
    Gson g = gb.create();
    dataMap = g.fromJson(dataStr, new TypeToken<Map<String, String>>(){}.getType());
    return dataMap;
  }

  /**
   * 工作流执行IMS上报 接口 HTTP同步远程执行方法
   *
   * 参数格式
   * subSystemId=1001&jobCode=111&jobDate=10214&ip=12312&status=3&alertLevel=1
   * 请求地址
   * http://ip:port/ims_config/job_report.do
   *
   * @param actionUrl
   * @param requestBody
   * @return
   */
  public static String httpClientIMSHandle(String actionUrl, RequestBody requestBody, Map<String, String> urlMap) throws Exception{

//    String maskUrl = actionUrl + "subSystemId=" + urlMap.get("subSystemId") + "&jobCode=" + urlMap.get("jobCode")
//        +  "&jobDate=" + urlMap.get("jobDate") + "&ip=" + urlMap.get("ip") + "&status=" + urlMap.get("status")
//        + "&alertLevel=" + urlMap.get("alertLevel");
    String maskUrl = actionUrl;
    //subSystemId=1001&jobCode=111&jobDate=10214&ip=12312&status=3&alertLevel=1

    //设置链接超时 设置写入超时 设置读取超时
    OkHttpClient okHttpClient = new OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .writeTimeout(20, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build();

    Request request = new Request.Builder()
        .url(maskUrl)
        .post(requestBody)
        .build();

    Call call = okHttpClient.newCall(request);
    Response response = call.execute();
    return response.body().string();
  }

  public static String getValue(Props props, String key){
    if(StringUtils.isNotBlank(props.get(key))){
      return props.get(key)==null?props.get(key):props.get(key).trim();
    }
    if (props.getParent() != null && StringUtils.isNotBlank(props.getParent().get(key))){
      return props.getParent().get(key)==null?props.getParent().get(key):props.getParent().get(key).trim();
    }
    return null;
  }

  /**
   *
   curl http://ip:port/ims_config/add_itsm_batch_job.do -d \
   '[{
   "subsystem_id": "1234",
   "planStartTime": "19:46",
   "planFinishTime": "19:46",
   "lastStartTime": "19:46",
   "lastFinishTime": "19:46",
   "groupName": "flowname"
   }]'
   * @param executableFlow
   * @param azkabanProps
   */
  public static void registerToIMS(final ExecutableFlow executableFlow, final Props azkabanProps, final Props flowPros, final Logger logger){
    String request = null;
    String actionUrl = null;
    try {
      if (executableFlow.getFlowType() != 3) {
        logger.info("非定时调度任务, 无需上报IMS");
        return;
      }
      if (flowPros == null || getValue(flowPros, "reportIMS") == null || !getValue(flowPros, "reportIMS").trim().toLowerCase().equals("true")) {
        logger.info("没有设置上报IMS.");
        return;
      }
      Map<String, String> dataMap = new HashMap<>();
      List<Map> dataList = new ArrayList<>();
      String subSystemId = getValue(flowPros, "subSystemId");
      dataMap.put("subsystem_id", subSystemId == null ? azkabanProps.get("ims.job.report.subSystemId") : subSystemId);
      dataMap.put("jobCode", executableFlow.getFlowId());
      dataMap.put("jobZhName", executableFlow.getFlowId());
      dataMap.put("planStartTime", getValue(flowPros, "planStartTime"));
      dataMap.put("planFinishTime", getValue(flowPros, "planFinishTime"));
      dataMap.put("lastStartTime", getValue(flowPros, "lastStartTime"));
      dataMap.put("lastFinishTime", getValue(flowPros, "lastFinishTime"));
      dataMap.put("groupName", executableFlow.getProjectName());
      dataMap.put("number", getValue(flowPros, "dcnNumber"));
      dataList.add(dataMap);
      request = GsonUtils.toJson(dataList);
      actionUrl = azkabanProps.getString("ims.job.register.url", null);
      if(actionUrl == null){
        logger.error("获取注册接口失败");
        return;
      }
      logger.info("url is : " + actionUrl + " requestBody is " + request);
    }catch (Exception e){
      logger.error("获取ims配置参数失败" +e);
      return;
    }


    try {
      MediaType applicationJson = MediaType.parse("application/json;charset=utf-8");
      RequestBody requestBody = RequestBody.create(applicationJson, request);
      logger.info("register to IMS, flowId is " + executableFlow.getFlowId());
      String result = HttpUtils.httpClientIMSHandle(actionUrl, requestBody, null);
      logger.info("register result is : " + result);
    } catch (Exception e){
      logger.error("registerToIMS, failed," + e);
    }
  }


  /**
   * curl --data "subSystemId=1001&jobCode=myCode&jobDate=20160616&ip=ip&status=3&alertLevel=1"
   http://ip:port/ims_config/job_report.do
   * @param executableFlow
   * @param azkabanProps
   */
  public static void uploadFlowStatusToIMS(final ExecutableFlow executableFlow, final Props azkabanProps, final Props flowPros, final Logger logger){
    String dcnNumbers = null;
    String jobCode = null;
    String jobDate = null;
    String alertLevel = null;
    String actionUrl = null;
    String subSystemId = null;
    String localHost = null;
    String status = null;
    try {
      if (executableFlow.getFlowType() != 3) {
        logger.info("非定时调度任务, 无需上报IMS");
        return;
      }
      if (flowPros == null || getValue(flowPros, "reportIMS") == null || !getValue(flowPros, "reportIMS").trim().toLowerCase().equals("true")) {
        return;
      }
      try {
        localHost = InetAddress.getLocalHost().getHostAddress();
      } catch (Exception e) {
        logger.error("cant not get localhost, + " + e);
      }
      logger.info("flow status is " + executableFlow.getStatus());
      String startTime = null;
      String entTime = null;
      switch (executableFlow.getStatus()) {
        case PREPARING:
        case READY:
          status = "1";
          break;
        case KILLED:
        case FAILED:
          status = "3";
          break;
        case SUCCEEDED:
          status = "2";
          break;
        default:
          status = "4";
//        startTime = DateTimeFormat.forPattern("H:m:s").print(executableFlow.getStartTime());
      }
      dcnNumbers = getValue(flowPros, "dcnNumber") == null ? "-1" : getValue(flowPros, "dcnNumber");
      jobCode = executableFlow.getFlowId();
      jobDate = DateTimeFormat.forPattern("yyyyMMdd").print(new Date().getTime());
      alertLevel = getValue(flowPros, "alertLevel") == null ? "2" : getValue(flowPros, "alertLevel");
      actionUrl = azkabanProps.get("ims.job.report.url");
      subSystemId = getValue(flowPros, "subSystemId") == null ? azkabanProps.get("ims.job.report.subSystemId") : getValue(flowPros, "subSystemId");
      if(actionUrl == null){
        logger.error("获取注册接口失败");
        return;
      }
    } catch (Exception e){
      logger.error("获取ims配置参数失败" +e);
      return;
    }
    try {
      for(String dcnNumber: dcnNumbers.split(",")) {
        RequestBody requestBody = new FormBody.Builder()
                .add("subSystemId", subSystemId)
                .add("jobCode", jobCode)
                .add("jobDate", jobDate)
                .add("ip", localHost)
                .add("dcnNumber", dcnNumber)
                .add("status", status) // 1：已开始；2：已结束；3：出错，4：自定义
                .add("alertLevel", alertLevel) //1:critical; 2:major; 3:minor; 4:warn;
                .build();
        logger.info(String.format("url is : " + actionUrl + ", params is : subSystemId=%s&jobCode=%s&jobDate=%s&ip=%s&dcnNumber=%s&status=%s&alertLevel=%s", subSystemId, jobCode, jobDate, localHost, dcnNumber, status, alertLevel));
        logger.info("upload flow status to IMS, flowId is " + executableFlow.getFlowId());
        String result = HttpUtils.httpClientIMSHandle(actionUrl, requestBody, null);
        logger.info("result is : " + result);
      }
    } catch (Exception e){
      logger.error("send request failed," + e);
    }
  }

  /**
   * 工作流跑批情况上报IMS
   */
  public static void sendFlowStatusToIMS(final ExecutableFlow executableFlow, final Props azkabanProps){
    logger.info("===============开始向IMS上报工作流：" + executableFlow.getFlowId());
    try {
      String actionUrl = azkabanProps.get("ims.job.report.url");
      logger.info("===============IMS上报地址：" + actionUrl);
      String subSystemId = azkabanProps.get("ims.job.report.subSystemId");
      logger.info("===============IMS上报子系统Id：" + subSystemId);
      logger.info("===============IMS上报子工作流：" + executableFlow.getFlowId());
      Date date = new Date(executableFlow.getStartTime());
      Instant instant = date.toInstant();
      ZoneId zone = ZoneId.systemDefault();
      LocalDate localDate = instant.atZone(zone).toLocalDate();
      DateTimeFormatter dtf = DateTimeFormatter.ofPattern("yyyyMMdd");

      String jobDate = localDate.format(dtf);
      logger.info("===============IMS上报子日期：" + jobDate);
      String ip = InetAddress.getLocalHost().getHostAddress();
      logger.info("===============IMS上报子IP：" + ip);
      int status = 1;
      if(Status.FAILED == executableFlow.getStatus()){
        status = 3;
      }else if(Status.SUCCEEDED == executableFlow.getStatus()){
        status = 2;
      }
      String alertLevel = azkabanProps.get("ims.job.report.alertLevel");
      String remark = "项目名: " + executableFlow.getProjectName() + ", 工作流名称： " + executableFlow.getFlowId();

      RequestBody requestBody = new FormBody.Builder()
          .add("subSystemId", subSystemId)
          .add("jobCode", executableFlow.getFlowId())
          .add("jobDate", jobDate)
          .add("ip", ip)
          .add("status", status + "")
          .add("alertLevel", alertLevel)
          .add("remark", remark)
          .build();
      Map<String, String> urlMap = new HashMap<>();

      urlMap.put("", executableFlow.getFlowId());

      String result = HttpUtils.httpClientIMSHandle(actionUrl, requestBody, urlMap);
      logger.info("===============IMS上报结果：" + result);
    } catch (Exception e) {
      logger.error("执行工作流告警上报失败, 异常信息：", e);
    } finally {
      logger.info("===============IMS上报结束");
    }

  }

  public static String getLinuxLocalIp(Logger log) {
    String ip = "127.0.0.1";
    try {
      ip = getLinuxLocalIp();
    } catch (SocketException ex) {
      log.warn("get ip failed", ex);

    }
    log.info("current host IP:   " + ip);
    return ip;
  }

  public static String getLinuxLocalIp() throws SocketException {
    for (Enumeration<NetworkInterface> en = NetworkInterface.getNetworkInterfaces(); en.hasMoreElements(); ) {
      NetworkInterface intf = en.nextElement();
      String name = intf.getName();
      if (!name.contains("docker") && !name.contains("lo")) {
        for (Enumeration<InetAddress> enumIpAddr = intf.getInetAddresses(); enumIpAddr.hasMoreElements(); ) {
          InetAddress inetAddress = enumIpAddr.nextElement();
          if (!inetAddress.isLoopbackAddress()) {
            String ipaddress = inetAddress.getHostAddress().toString();
            if (!ipaddress.contains("::") && !ipaddress.contains("0:0:") && !ipaddress.contains("fe80")) {
              logger.debug("local ip: " + ipaddress);
              return ipaddress;
            }
          }
        }
      }
    }
    return null;
  }

  public static void reloadWebData(List<String> urlList, String type, String data) {
    try {
      if (StringUtils.isEmpty(type)) {
        return;
      }
      for (String url : urlList) {
        if (StringUtils.isEmpty(url) || !(url.indexOf(getLinuxLocalIp()) < 0)) {
          continue;
        }
        url = url + "/executor?ajax=reloadWebData&reloadType=" + type;
        if (type.indexOf("Trigger") >= 0) {
          url = url + "&triggerId=" + data;
        } else if (type.indexOf("Project") >= 0) {
          if("deleteProject".equals(type)){
            url = url + "&projectId=" + data;
          }else{
            url = url + "&projectName=" + data;
          }

        } else if (type.contains("EventSchedule")) {
          url += "&scheduleId=" + data;
        }
        OkHttpClient okHttpClient = new OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .writeTimeout(20, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .build();

        Request request = new Request.Builder()
            .url(url).get()
            .build();

        Call call = okHttpClient.newCall(request);
        call.enqueue(new Callback() {
          @Override
          public void onFailure(@NotNull Call call, @NotNull IOException e) {

          }

          @Override
          public void onResponse(@NotNull Call call, @NotNull Response response)
              throws IOException {

          }
        });
      }
    } catch (Exception e) {
      logger.error("reload web data error", e);
    }

  }

  public static void main(String[] args) {

  }







}

