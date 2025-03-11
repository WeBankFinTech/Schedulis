package azkaban.utils;

import com.alibaba.fastjson.JSONObject;
import java.io.IOException;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import org.apache.http.HttpEntity;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;

/**
 * DSS 工具类
 *
 * @author lebronwang
 **/
public class DssUtils {

  public static Map<String, String> genHeaders(String tokenUser, String tokenCode) {

    //请求头
    Map<String, String> headers = new HashMap<>();
    headers.put("Token-User", tokenUser);
    headers.put("Token-Code", tokenCode);

    return headers;
  }

  public static String doPost(String url, JSONObject data, String tokenUser, String tokenCode)
      throws Exception {
    String result = null;
    Map<String, String> headers = genHeaders(tokenUser, tokenCode);
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
      HttpEntity responseEntity = response.getEntity();
      result = EntityUtils.toString(responseEntity);
    } catch (IOException e) {
      throw new Exception(e);
    }
    return result;
  }

  /**
   * @param url              请求 URL
   * @param tokenUser        项目原创建用户
   * @param tokenCode
   * @param transferUserName 被交接的人，为在职用户，将成为新的项目owner
   * @param projectName      要交接的项目名称
   * @return
   * @throws Exception
   */
  public static Map<String, Object> transferDssProject(String url, String tokenUser,
      String tokenCode,
      String transferUserName, String projectName) throws Exception {
    Map<String, Object> ret = new HashMap<>();
    JSONObject reqBody = new JSONObject();
    reqBody.put("transferUserName", transferUserName);
    reqBody.put("projectName", projectName);
    String result = doPost(url, reqBody, tokenUser, tokenCode);

    JSONObject jsonResult = JSONObject.parseObject(result);
    int retStatus = jsonResult.getIntValue("status");
    ret.put("dssRetStatus", retStatus);
    ret.put("dssRetMessage", jsonResult.getString("message"));
    return ret;
  }
}
