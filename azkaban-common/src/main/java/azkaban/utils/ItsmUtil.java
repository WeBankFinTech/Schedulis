package azkaban.utils;

import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import org.apache.http.HttpEntity;
import org.apache.http.HttpStatus;
import org.apache.http.StatusLine;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;

import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.text.SimpleDateFormat;
import java.util.*;

/**
 * Tools for ITSM
 */
public class ItsmUtil {

  private static String MD5(String sourceStr) throws NoSuchAlgorithmException {
    String result = "";
    MessageDigest md = MessageDigest.getInstance("MD5");
    md.update(sourceStr.getBytes());
    byte b[] = md.digest();
    int i;
    StringBuffer buf = new StringBuffer();
    for (int offset = 0; offset < b.length; offset++) {
      i = b[offset];
      if (i < 0) {
        i += 256;
      }
      if (i < 16) {
        buf.append("0");
      }
      buf.append(Integer.toHexString(i));
    }
    result = buf.toString();

    return result;
  }

  public static Map<String, String> genHeaders(String appId, String appKey, String userId)
      throws NoSuchAlgorithmException {
    String sign;

    int max = 9999;
    int min = 1000;
    SecureRandom random = SecureRandom.getInstance("SHA1PRNG");
    int randomInt = random.nextInt(max) % (max - min + 1) + min;

    Date date = new Date();
    String timestamp = Long.toString(date.getTime());
    //生成签名
    sign = MD5(MD5(appKey + randomInt + timestamp) + userId);
    //请求头
    Map<String, String> headers = new HashMap<>();
    headers.put("appId", appId);
    headers.put("appKey", appKey);
    headers.put("random", String.valueOf(randomInt));
    headers.put("timestamp", timestamp);
    headers.put("userId", userId);
    headers.put("sign", sign);

    return headers;
  }

  public static String doPost(String url, JSONObject data, String appId, String appKey, String userId)
      throws Exception {
    String result = null;
    Map<String, String> headers = genHeaders(appId, appKey, userId);
    try (CloseableHttpClient httpClient = HttpClients.createDefault()) {
      HttpPost httpPost = new HttpPost(url);
      httpPost.setHeader("Content-Type", "application/json");
      //迭代器遍历获取headers信息
      if (headers != null && !headers.keySet().isEmpty()) {
        Set<String> keySet = headers.keySet();
        Iterator<String> iterator = keySet.iterator();
        while (iterator.hasNext()) {
          String key = iterator.next();
          String value = headers.get(key);
          httpPost.setHeader(key, value);
        }
      }
      //设置和格式化参数
      String charSet = "UTF-8";
      StringEntity entity = new StringEntity(data.toJSONString(), charSet);
      httpPost.setEntity(entity);
      CloseableHttpResponse response = httpClient.execute(httpPost);
      StatusLine statusLine = response.getStatusLine();
      if (statusLine.getStatusCode() == HttpStatus.SC_OK) {
        HttpEntity responseEntity = response.getEntity();
        result = EntityUtils.toString(responseEntity);
      } else {
        result = "请求返回失败，状态码：" + statusLine.getStatusCode() + "请求URL：" + url;
      }
    } catch (IOException e) {
      throw new Exception(e);
    }
    return result;
  }

  public static void getListData4Aomp(String itsmUrl, String userName, String keyword,
      int currentPage, HashMap<String, Object> ret, String appId, String appKey, String userId)
      throws Exception {

    JSONObject reqBody = new JSONObject();
    reqBody.put("userId", userName);
    reqBody.put("keyword", keyword);
    reqBody.put("currentPage", currentPage);
    String result = doPost(itsmUrl, reqBody, appId, appKey, userId);
    JSONObject jsonResult = JSONObject.parseObject(result);
    JSONArray formList = jsonResult.getJSONObject("data").getJSONArray("data");

    ret.put("formList", formList);
    //ret.put("pageSize", jsonResult.getJSONObject("data").getString("pageSize"));
    //ret.put("totalPage", jsonResult.getJSONObject("data").getString("totalPage"));
    //ret.put("currentPage", jsonResult.getJSONObject("data").getString("currentPage"));
  }

  public static void sendRequest2Itsm4ChangeProjectOwner(String itsmUrl, String username,
      Map<String, String> changeMap, Map<String, Object> ret, String appId, String appKey,
      String userId, String baseOldOwner, String baseNewOwner, String itsmFormId,
      String requestHandler, String env, String requestDesc) throws Exception {
    JSONObject reqBody = new JSONObject();

    // 请求标题
    reqBody.put("requestTitle", username + " 申请 WTSS 项目交接");
    // 紧急状态（2000不紧急，2001普通，2002紧急）传数字
    reqBody.put("requestUrgency", "2001");
    // 需求希望完成时间
    Calendar calendar = Calendar.getInstance();
    // 当前日期的 10 天后
    calendar.add(Calendar.DAY_OF_WEEK, 10);
    SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd");
    String requestEndDateStr = sdf.format(calendar.getTime());
    reqBody.put("requestEndDate", requestEndDateStr);
    // 需求描述
    reqBody.put("requestDesc", requestDesc);
    // 关注人 (可以传多个用“,”分隔)
    reqBody.put("notifyUserIds", username);
    // 上报人
    reqBody.put("requestOwner", username);
    // 表单内容 (数组)
    List<JSONObject> formsList = new ArrayList<>();
    JSONObject formJson = new JSONObject();
    List<JSONObject> dataList = new ArrayList<>();

    changeMap.forEach((project, newOwner) -> {
      JSONObject formData = new JSONObject();
      formData.put("env_type", env);
      formData.put("new_user", newOwner);
      formData.put("project_name", project);

      dataList.add(formData);
    });

    JSONObject formDataValueJson = new JSONObject();
    formDataValueJson.put("dataList", dataList);
    formJson.put("formDataValue", formDataValueJson);
    formJson.put("formId", itsmFormId);
    formsList.add(formJson);
    reqBody.put("forms", formsList);

    // 审批链
    List<JSONObject> requestRatifyChainsList = new ArrayList<>();

    String eoaChainStepNameNewOwner = "交接人";
    String eoaChainStepNameOldLeader = "被交接人室经理";
    String eoaChainStepNameNewLeader = "交接人室经理";

    handleRequestRatifyChains(eoaChainStepNameNewOwner, baseNewOwner, requestRatifyChainsList, 0);
    handleRequestRatifyChains(eoaChainStepNameOldLeader, baseOldOwner, requestRatifyChainsList, 1);
    handleRequestRatifyChains(eoaChainStepNameNewLeader, baseNewOwner, requestRatifyChainsList, 1);

    reqBody.put("requestRatifyChains", requestRatifyChainsList);

    // 指定处理人
    reqBody.put("requesthandler", requestHandler);

    String result = doPost(itsmUrl, reqBody, appId, appKey, userId);
    JSONObject jsonResult = JSONObject.parseObject(result);

    int retCode = jsonResult.getIntValue("retCode");
    if (retCode == 0) {
      ret.put("requestInfo", "向 ITSM 提服务请求单成功，服务请求单号：" + jsonResult.getLong("data"));
      ret.put("itsmNo", jsonResult.getLong("data"));
    } else {
      ret.put("error", "向 ITSM 提服务请求单失败，失败原因：" + jsonResult.getString("retDetail"));
    }
  }

  private static void handleRequestRatifyChains(String eoaChainStepName, String baseUser,
      List<JSONObject> requestRatifyChainsList, int userType) {
    JSONObject requestRatifyChain = new JSONObject();
    requestRatifyChain.put("eoaChainStepType", 0);
    requestRatifyChain.put("eoaChainChainTips", "审批重点说明");
    requestRatifyChain.put("eoaChainStepName", eoaChainStepName);
    List<JSONObject> eoaChainUserList = new ArrayList<>();
    JSONObject eoaChainUser = new JSONObject();
    eoaChainUser.put("baseUserId", baseUser);
    eoaChainUser.put("userType", userType);
    eoaChainUserList.add(eoaChainUser);
    requestRatifyChain.put("eoaChainUserList", eoaChainUserList);
    requestRatifyChainsList.add(requestRatifyChain);
  }

  /*public static void main(String[] args) throws Exception {

    String appId = "302";
    String appKey = "099ab2c5-789c-48be-bedb-ed3cbca16183";
    String itsmUrl = "http://172.21.0.65:8080/proxy/toOA" + "/itsm/request/insertRequestAuth.any";
    String userId = "lebronwang";
    String itsmFormId = "10006455";
    *//*String appId = "124";
    String appKey = "d5d5cde0-308c-4e1f-8953-f233e7a7005e";
    String userId = "lebronwang";
    String itsmUrl = "http://itsm.weoa.com" + "/itsm/request/insertRequestAuth.any";
    String itsmFormId = "10002486";*//*

    Map<String, String> formMap = new HashMap<>();

    formMap.put("lebronwang", "lebronwang");
    formMap.put("lebronwang1", "lebronwang1");
    Map<String, Object> ret = new HashMap<>();
    Map<String, String> headers = genHeaders(appId, appKey, userId);
    System.out.println(headers);

    String baseOldOwnerName = "lebronwang";
    String baseNewOwnerName = "lebronwang";
    String requestHandler = "lebronwang";

    sendRequest2Itsm4ChangeProjectOwner(itsmUrl, "cainezhang", formMap, ret, appId, appKey,
        userId, baseOldOwnerName, baseNewOwnerName, itsmFormId, requestHandler);

    System.out.println(ret);
  }*/

}
