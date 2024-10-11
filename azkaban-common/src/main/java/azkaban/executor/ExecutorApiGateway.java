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
 */

package azkaban.executor;

import azkaban.Constants;
import azkaban.utils.JSONUtils;
import azkaban.utils.Pair;
import azkaban.utils.Props;
import com.google.inject.Inject;
import java.io.IOException;
import java.net.URI;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import javax.inject.Singleton;

import com.webank.wedatasphere.schedulis.common.utils.JwtTokenUtils;
import okhttp3.*;
import org.codehaus.jackson.map.ObjectMapper;

@Singleton
public class ExecutorApiGateway {

  private final ExecutorApiClient apiClient;
  private final Props azkProps;

  @Inject
  public ExecutorApiGateway(final ExecutorApiClient apiClient,final Props azkProps) {
    this.apiClient = apiClient;
    this.azkProps = azkProps;
  }

  // FIXME change this method access as public type in order to outside package object can call this method.
  public Map<String, Object> callWithExecutable(final ExecutableFlow exflow,
      final Executor executor, final String action) throws ExecutorManagerException {
    return callWithExecutionId(executor.getHost(), executor.getPort(), action,
        exflow.getExecutionId(), null, (Pair<String, String>[]) null);
  }

  // FIXME change this method access as public type in order to outside package object can call this method.
  public Map<String, Object> callWithReference(final ExecutionReference ref, final String action,
      final Pair<String, String>... params) throws ExecutorManagerException {
    final Executor executor = ref.getExecutor().get();
    return callWithExecutionId(executor.getHost(), executor.getPort(), action, ref.getExecId(),
        null, params);
  }

  // FIXME change this method access as public type in order to outside package object can call this method.
  public Map<String, Object> callWithReferenceByUser(final ExecutionReference ref,
      final String action, final String user, final Pair<String, String>... params)
      throws ExecutorManagerException {
    final Executor executor = ref.getExecutor().get();
    return callWithExecutionId(executor.getHost(), executor.getPort(), action,
        ref.getExecId(), user, params);
  }

  // FIXME change this method access as public type in order to outside package object can call this method.
  public Map<String, Object> callWithExecutionId(final String host, final int port,
      final String action, final Integer executionId, final String user,
      final Pair<String, String>... params) throws ExecutorManagerException {
    try {
      final List<Pair<String, String>> paramList = new ArrayList<>();

      if (params != null) {
        paramList.addAll(Arrays.asList(params));
      }

      paramList
          .add(new Pair<>(ConnectorParams.ACTION_PARAM, action));
      paramList.add(new Pair<>(ConnectorParams.EXECID_PARAM, String
          .valueOf(executionId)));
      paramList.add(new Pair<>(ConnectorParams.USER_PARAM, user));

      if(this.azkProps.getBoolean(Constants.ConfigurationKeys.IP_WHITELIST_ENABLED,false)){
        String dss_secret = azkProps.getString("dss.secret", "dws-wtss|WeBankBDPWTSS&DWS@2019");
        String token = JwtTokenUtils.getToken(null,false,dss_secret,300);
        paramList.add(new Pair<>(ConnectorParams.TOKEN_PARAM, token));
      }

      return callForJsonObjectMap(host, port, "/executor", paramList);
    } catch (final IOException e) {
      throw new ExecutorManagerException(e);
    }
  }

  /**
   * Call executor and parse the JSON response as an instance of the class given as an argument.
   * FIXME change this method access as public type in order to outside package object can call this method.
   */
  public <T> T callForJsonType(final String host, final int port, final String path,
      final List<Pair<String, String>> paramList, final Class<T> valueType) throws IOException {
    final String responseString = callForJsonString(host, port, path, paramList);
    if (null == responseString || responseString.length() == 0) {
      return null;
    }
    return new ObjectMapper().readValue(responseString, valueType);
  }

  /**
   * Call executor and return json object map.
   * FIXME change this method access as public type in order to outside package object can call this method.
   */
  public Map<String, Object> callForJsonObjectMap(final String host, final int port,
      final String path, final List<Pair<String, String>> paramList) throws IOException {
    final String responseString =
        callForJsonString(host, port, path, paramList);

    @SuppressWarnings("unchecked") final Map<String, Object> jsonResponse =
        (Map<String, Object>) JSONUtils.parseJSONFromString(responseString);
    final String error = (String) jsonResponse.get(ConnectorParams.RESPONSE_ERROR);
    if (error != null) {
      throw new IOException(error);
    }
    return jsonResponse;
  }

  /*
   * Call executor and return raw json string.
   */
  private String callForJsonString(final String host, final int port, final String path,
      List<Pair<String, String>> paramList) throws IOException {
    if (paramList == null) {
      paramList = new ArrayList<>();
    }

    @SuppressWarnings("unchecked") final URI uri =
        ExecutorApiClient.buildUri(host, port, path, true);

    if(this.azkProps.getBoolean(Constants.ConfigurationKeys.IP_WHITELIST_ENABLED,false)){
      String dss_secret = azkProps.getString("dss.secret", "dws-wtss|WeBankBDPWTSS&DWS@2019");
      String token = JwtTokenUtils.getToken(null,false,dss_secret,300);
      paramList.add(new Pair<>(ConnectorParams.TOKEN_PARAM, token));
    }

    return this.apiClient.httpPost(uri, paramList);
  }

  public Map<String, Object> updateExecutions(final Executor executor,
      final List<ExecutableFlow> executions) throws ExecutorManagerException {
    final List<Long> updateTimesList = new ArrayList<>();
    final List<Integer> executionIdsList = new ArrayList<>();
    // We pack the parameters of the same host together before query
    for (final ExecutableFlow flow : executions) {
      executionIdsList.add(flow.getExecutionId());
      updateTimesList.add(flow.getUpdateTime());
    }
    final Pair<String, String> updateTimes = new Pair<>(
        ConnectorParams.UPDATE_TIME_LIST_PARAM,
        JSONUtils.toJSON(updateTimesList));
    final Pair<String, String> executionIds = new Pair<>(
        ConnectorParams.EXEC_ID_LIST_PARAM,
        JSONUtils.toJSON(executionIdsList));

    return callWithExecutionId(executor.getHost(), executor.getPort(),
        ConnectorParams.UPDATE_ACTION, null, null, executionIds, updateTimes);
  }

  public String httpPost(String actionUrl, String json) throws Exception{
    MediaType applicationJson = MediaType.parse("application/json;charset=utf-8");
    RequestBody requestBody = RequestBody.create(applicationJson, json);
    //设置链接超时 设置写入超时 设置读取超时
    OkHttpClient okHttpClient = new OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .writeTimeout(20, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .build();
    Request request = new Request.Builder()
            .url(actionUrl)
            .post(requestBody)
            .build();
    Call call = okHttpClient.newCall(request);
    Response response = null;
    String ret = null;
    try {
      response = call.execute();
      if(response.isSuccessful()) {
        ret = response.body().string();
      }
    } catch (IOException e){
      throw e;
    } finally {
      if(response != null){
        response.close();
      }
    }
    return ret;
  }

}
