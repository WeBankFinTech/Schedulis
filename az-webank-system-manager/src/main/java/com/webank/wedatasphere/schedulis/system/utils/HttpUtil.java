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
package com.webank.wedatasphere.schedulis.system.utils;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Map;
import org.json.JSONObject;
import org.slf4j.Logger;

public class HttpUtil {

  public static StringBuilder get(String baseUrl,Map<String, String> requestProperties){
    HttpURLConnection connection = null;
    try {
      for (Map.Entry<String, String> entry : requestProperties.entrySet()) {
        baseUrl = baseUrl + "&"+entry.getKey()+"="+entry.getValue();
      }

      URL url = new URL(baseUrl);
      connection = (HttpURLConnection) url.openConnection();
      connection.setUseCaches(false);
      int responseCode = connection.getResponseCode();
      if (responseCode == HttpURLConnection.HTTP_OK) {
        InputStream is = connection.getInputStream();

        BufferedReader br = new BufferedReader(new InputStreamReader(is));
        String line = null;
        StringBuilder sb = new StringBuilder();

        while ((line = br.readLine()) != null){
          sb.append(line+"\n");
        }
        br.close();

        return sb;
      } else if (responseCode == HttpURLConnection.HTTP_NO_CONTENT) {
        return null;
      }
    }catch (Exception e){
      System.out.println("error msg:"+e.getMessage());
    }finally {
      if (connection != null) {
        connection.disconnect();
      }
    }

    return null;
  }

  public static StringBuilder get(String baseUrl,Map<String, String> requestProperties, Logger logger)
      throws Exception{
    HttpURLConnection connection = null;
    try {
      for (Map.Entry<String, String> entry : requestProperties.entrySet()) {
        baseUrl = baseUrl + "&"+entry.getKey()+"="+entry.getValue();
      }

      URL url = new URL(baseUrl);
      logger.info("ESB HTTP请求链接：" + url);
      connection = (HttpURLConnection) url.openConnection();
      connection.setUseCaches(false);
      int responseCode = connection.getResponseCode();
      if (responseCode == HttpURLConnection.HTTP_OK) {
        InputStream is = connection.getInputStream();

        BufferedReader br = new BufferedReader(new InputStreamReader(is));
        String line = null;
        StringBuilder sb = new StringBuilder();

        while ((line = br.readLine()) != null){
          sb.append(line+"\n");
        }
        br.close();

        JSONObject root = new JSONObject(sb.toString());

        logger.info("ESB HTTP请求返回信息： Code:" + root.getString("Code")
            + ", Message:" + root.getString("Message"));

        return sb;
      } else if (responseCode == HttpURLConnection.HTTP_NO_CONTENT) {
        return null;
      }
    }catch (Exception e){
      logger.error("获取 ESB 请求接口失败. ", e);
      throw new Exception("Request ESB Api Failed.");
    }finally {
      if (connection != null) {
        connection.disconnect();
      }
    }

    return null;
  }

}
