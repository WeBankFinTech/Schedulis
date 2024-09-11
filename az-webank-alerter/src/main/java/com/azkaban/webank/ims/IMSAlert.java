/*
 * Copyright 2017 LinkedIn Corp.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 *
 */

package com.azkaban.webank.ims;

import com.google.gson.Gson;
import org.apache.commons.codec.binary.Hex;
import org.slf4j.Logger;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.Charset;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HashSet;
import java.util.List;
import java.util.Set;


/**
 * Created by kirkzhou on 7/3/17.
 */
public class IMSAlert {
  public static class Result {
    private int resCode = -2;
    private String resInfo = "Error getting result from server!!!";

    private String resultCode = "";

    private String resultMsg = "";

    private String systemTime = "";

    public Result() {
    }

    /**
     * Only for local test.
     *
     * @param resCode result code
     */
    Result(int resCode) {
      this.resCode = resCode;
    }

    @Override
    public String toString() {
      return "IMS返回代码：" + this.resultCode + ", IMS返回消息：" + this.resultMsg + ", IMS返回时间：" + this.systemTime;
    }

    public int getResCode() {
      return resCode;
    }

    public String getResInfo() {
      return resInfo;
    }

    public String getResultCode() {
      return resultCode;
    }

    public void setResultCode(String resultCode) {
      this.resultCode = resultCode;
    }

    public String getResultMsg() {
      return resultMsg;
    }

    public void setResultMsg(String resultMsg) {
      this.resultMsg = resultMsg;
    }

    public String getSystemTime() {
      return systemTime;
    }

    public void setSystemTime(String systemTime) {
      this.systemTime = systemTime;
    }
  }


  /**
   * the level of alert
   */
  @SuppressWarnings("unused") public enum AlertLevel {
    CLEAR, CRITICAL, MAJOR, MINOR, WARNING, INFO
  }


  @SuppressWarnings("FieldCanBeLocal")
  private final String encode = "UTF-8";
  //    private final String encode = "iso8859-1";
  private String server, port;
  @SuppressWarnings("FieldCanBeLocal")
  private final String path = "/ims_data_access/send_alarm.do";

  private int subSystemID; // 子系统ID
  private String alertTitle; // 告警标题
  /**
   * 告警ID, 用于标识一条告警，相同的alert_id会被归并成一条记录.
   * 当系统收到该alert_id对应的恢复告警时，会将告警清除，
   * 如果不传入该ID，系统会自动生成一个默认的alert_id
   */
  private String alertID;
  /**
   * 告警级别, 0:clear，1:critical，2:major，3:minor，4:warning， 5:info.
   * 如不传入，则使用默认值5:info.
   * 告警级别 critical > major > minor > warning＞info
   */
  private AlertLevel alertLevel = AlertLevel.INFO;
  private String alertObj; // 用于标示告警发生的对象, 该字段实际上是对告警标题的细分
  private String alertInfo; // 告警信息
  @SuppressWarnings("unused") private String alertIP; // 告警IP, 如为空，则取上报Server的IP
  private int toECC; // 默认0，为1时，由ECC关注和处理，否则只用自己关注
  private int canRecover; // 默认0，为1时，需要有对应的恢复告警
  /**
   * 默认0(不发送)，为1时，发送RTX消息.为2时，发送Email，为3时，发送微信，
   * 如需要同时发送RTX和Email，可以传入“1,2”
   */
  private Set<Integer> alertWays;
  /**
   * 必须是RTX名称，输入错误时不会报错，但不能正常收到
   * （单个接收人直接填写，如有多个接收人则通过半角逗号分隔）
   */
  private List<String> alertReceivers;

  private Logger logger;

  public IMSAlert(String server, String port, int subSystemID, String alertTitle, Logger logger) {
    this.server = server;
    this.port = port;
    this.subSystemID = subSystemID;
    this.alertTitle = alertTitle;
    this.logger = logger;
  }

  public Result alert() throws IOException {
    Result result = new Result();

    URL url = new URL("http://" + server + ":" + port + path);
    HttpURLConnection conn = (HttpURLConnection) url.openConnection();
    conn.setConnectTimeout(3000);
    conn.setDoInput(true);
    conn.setDoOutput(true);
    conn.setRequestMethod("POST");
    conn.setUseCaches(false);

    // Request data
    String requestDataStr = getRequesData();

    logger.info("请求的IMS URL地址和参数：" + getIMSUrlParam());

    byte[] requestData = requestDataStr.getBytes(encode);
    OutputStream os = conn.getOutputStream();
    os.write(requestData);

    // Send alert and get result
    int response = conn.getResponseCode();
    if (response == HttpURLConnection.HTTP_OK) {
      InputStream is = conn.getInputStream();
      ByteArrayOutputStream baos = new ByteArrayOutputStream();
      byte[] resultData = new byte[1024];
      int len;
      while ((len = is.read(resultData)) != -1) {
        baos.write(resultData, 0, len);
      }

      // Json to java
      String resultStr = new String(baos.toByteArray(), Charset.defaultCharset());
      logger.info("IMS接口返回JSON数据：" + resultStr);
      result = new Gson().fromJson(resultStr, Result.class);
    } else {
      logger.error("请求IMS接口失败, responseCode=" + response + " !!!");
    }

    return result;
  }

  public String getRequesData() {
    StringBuilder sb = new StringBuilder();
    try {
      sb.append("sub_system_id=").append(subSystemID);
      // sb.append("&alert_title=").append(URLEncoder.encode(alertTitle, encode));
      sb.append("&alert_title=").append(alertTitle);
      if (alertID != null && !alertID.isEmpty()){
        sb.append("&alert_id=").append(alertID);
      }
      sb.append("&alert_level=").append(alertLevel.ordinal());
      if (alertObj != null && !alertObj.isEmpty()){
        // sb.append("&alert_obj=").append(URLEncoder.encode(alertObj, encode));
        sb.append("&alert_obj=").append(alertObj);
      }
      if (alertInfo != null && !alertInfo.isEmpty()){
        // sb.append("&alert_info=").append(URLEncoder.encode(alertInfo, encode));
        sb.append("&alert_info=").append(alertInfo);
      }
      if (alertIP != null && !alertIP.isEmpty()){
        sb.append("&alert_ip=").append(alertIP);
      }
      //sb.append("&to_ecc=").append(toECC);
      //sb.append("&can_recover=").append(canRecover);
      if (alertWays != null && !alertWays.isEmpty()) {
        sb.append("&alert_way=");
        for (Integer way : alertWays) {
          sb.append(way).append(",");
        }
        sb.deleteCharAt(sb.length() - 1);
      }
      if (alertReceivers != null && !alertReceivers.isEmpty()) {
        sb.append("&alert_reciver=");
        for (String receiver : alertReceivers) {
          sb.append(receiver).append(",");
        }
        sb.deleteCharAt(sb.length() - 1);
      }
    } catch (Exception e) {
      logger.error("Failed to generate request data!!!", e);
    }
    return sb.toString();
  }

  public void setAlertID(String alertID) {
    this.alertID = alertID;
  }

  public int getSubSystemID() {
    return subSystemID;
  }

  public String getAlertTitle() {
    return alertTitle;
  }

  /**
   * 告警级别, 0:clear，1:critical，2:major，3:minor，4:warning， 5:info.
   * 如不传入，则使用默认值5:info.
   * 告警级别 critical > major > minor > warning＞info
   */
  public void setAlertLevel(AlertLevel alertLevel) {
    this.alertLevel = alertLevel;
  }

  public void setAlertObj(String alertObj) {
    this.alertObj = alertObj;
  }

  public void setAlertInfo(String alertInfo) {
    this.alertInfo = alertInfo;
  }

  public void setToECC(int toECC) {
    this.toECC = toECC;
  }

  public void setCanRecover(int canRecover) {
    this.canRecover = canRecover;
  }

  /**
   * 默认0(不发送)，为1时，发送RTX消息.为2时，发送Email，为3时，发送微信，
   * 如需要同时发送RTX和Email，可以传入“1,2”
   */
  public void addAlertWay(int way) {
    if (alertWays == null){
      alertWays = new HashSet<>();
    }
    alertWays.add(way);
  }

  public void setAlertReceivers(List<String> alertReceivers) {
    this.alertReceivers = alertReceivers;
  }

  /**
   * Concatenate all parameters to a string, md5 the string as alertID.
   *
   * @param subSystemID IMS subsystem ID
   * @param alertTitle  IMS alert title
   * @param alertLevel  IMS alert level
   * @param jobID       Job ID
   * @return 32-bit IMS alert id
   */
  public static String generateAlertID(int subSystemID, String alertTitle, AlertLevel alertLevel,
      String jobID) {
    MessageDigest md5;
    try {
      md5 = MessageDigest.getInstance("MD5");
    } catch (NoSuchAlgorithmException e) {
      e.printStackTrace();
      return null;
    }
    String source = subSystemID + alertTitle + alertLevel.ordinal() + jobID;
    byte[] srcByteArray = md5.digest(source.getBytes(Charset.defaultCharset()));

    return new String(new Hex().encode(srcByteArray), Charset.defaultCharset());
  }

  /**
   * 获取IMS请求参数 去掉消息 方便问题定位
   * @return
   */
  public String getIMSUrlParam() {
    StringBuilder sb = new StringBuilder();
    try {
      sb.append("sub_system_id=").append(subSystemID);
      sb.append("&alert_title=").append(alertTitle);
      if (alertID != null && !alertID.isEmpty()){
        sb.append("&alert_id=").append(alertID);
      }
      sb.append("&alert_level=").append(alertLevel.ordinal());
      if (alertObj != null && !alertObj.isEmpty()){
        sb.append("&alert_obj=").append(alertObj);
      }
      if (alertInfo != null && !alertInfo.isEmpty()){
        sb.append("&alert_info=").append(alertInfo);
      }
      if (alertIP != null && !alertIP.isEmpty()){
        sb.append("&alert_ip=").append(alertIP);
      }

      if (alertWays != null && !alertWays.isEmpty()) {
        sb.append("&alert_way=");
        for (Integer way : alertWays) {
          sb.append(way).append(",");
        }
        sb.deleteCharAt(sb.length() - 1);
      }
      if (alertReceivers != null && !alertReceivers.isEmpty()) {
        sb.append("&alert_reciver=");
        for (String receiver : alertReceivers) {
          sb.append(receiver).append(",");
        }
        sb.deleteCharAt(sb.length() - 1);
      }
    } catch (Exception e) {
      logger.error("Failed to generate request data!!!", e);
    }
    return sb.toString();
  }

}

