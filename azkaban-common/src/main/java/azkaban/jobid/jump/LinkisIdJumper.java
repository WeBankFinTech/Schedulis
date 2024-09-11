package azkaban.jobid.jump;

import azkaban.utils.HttpUtils;
import azkaban.utils.JSONUtils;
import azkaban.utils.Props;
import okhttp3.*;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static java.util.stream.Collectors.joining;

/**
 * Created by shangda on 2023/1/10.
 */
public class LinkisIdJumper implements JobIdJumper {

    private static final Logger logger = LoggerFactory.getLogger(LinkisIdJumper.class.getName());
    private static final String AZKABAN_WEBSERVER_URL = "azkaban.webserver.url";
    private static final String LINKIS_SERVER_URL = "linkis.server.url";
    private static final String LINKIS_COOKIE_DOMAIN = "linkis.cookie.domain";
    private static final String WTSS_LINKIS_TOKEN = "wtss.linkis.token";
    private static final String LINKIS_LOG_PAGE_URL_PATTERN = "linkis.log.page.url.pattern";

    private final OkHttpClient httpClient = HttpUtils.okHttpClient;

    public LinkisIdJumper() {

    }

    @Override
    public String getRedirectUrl(String targetId, Props azkProps) throws IOException {
        String urlPattern = azkProps.get(LINKIS_LOG_PAGE_URL_PATTERN);
        if (StringUtils.isBlank(urlPattern)) {
            urlPattern = "http://sit.dss.bdp.weoa.com/dss/linkis/?FnoHeader=1&noFooter=1#/console/viewHistory?taskID=%s";
        }
        return String.format(urlPattern, targetId);
    }

    @Override
    public String getRedirectCookieString(Map<String, Object> input, Props azkProps) throws IOException {
        String projectName = (String) input.get("project");
        String flowName = (String) input.get("flowName");
        String jobName = (String) input.get("jobName");
        String sessionId = (String) input.get("session.id");
        String submitUser = (String) input.get("submitUser");
        String wtssWebServerUrl = azkProps.get(AZKABAN_WEBSERVER_URL);
        String linkisServerUrl = azkProps.get(LINKIS_SERVER_URL);
        String linkisCookieDomain = azkProps.get(LINKIS_COOKIE_DOMAIN);
        String wtssLinkisToken = azkProps.get(WTSS_LINKIS_TOKEN);

        checkNotNull("project", projectName);
        checkNotNull("flowName", flowName);
        checkNotNull("jobName", jobName);
        checkNotNull("sessionId", sessionId);
        checkNotNull("submitUser", submitUser);
        checkNotNull("wtssWebServerUrl", wtssWebServerUrl);
        checkNotNull("linkisServerUrl", linkisServerUrl);
        checkNotNull("wtssLinkisToken", wtssLinkisToken);
        if(StringUtils.isBlank(linkisCookieDomain)) {
            linkisCookieDomain = ".webank.com";
        }

        Map<String, String> params = new HashMap<>();
        params.put("project", projectName);
        params.put("flowName", flowName);
        params.put("jobName", jobName);
        params.put("session.id", sessionId);

        Response response = makeHttpGet(wtssWebServerUrl, "/manager?ajax=getJobParamData&", params);

        String userToProxy = parseExecUser(response.body().string());
        String execUser = StringUtils.isBlank(userToProxy) ? submitUser : userToProxy;
//        response.close();

        Map<String, String> headers = new HashMap<>();
        headers.put("Token-User", execUser);
        headers.put("Token-Code", wtssLinkisToken);
        headers.put("Content-Type", "application/json");

        response = makeHttpPost(linkisServerUrl, "/api/rest_j/v1/user/token-login", new HashMap<>(), headers, "{}");
        String cookieStrOri = response.header("Set-Cookie");

        if (StringUtils.isBlank(cookieStrOri)) {
            throw new IOException("server returns empty cookie string. url: " + linkisServerUrl + "path: /api/rest_j/v1/user/token-login response: " + response.toString());
        }

        String cookieStr = String.format("%s; Domain=%s", cookieStrOri, linkisCookieDomain);
        IOUtils.closeQuietly(response);
        return cookieStr;
    }

    @Override
    public Map<String, String> getRedirectHeader(Props azkProps) throws IOException {
        return new HashMap<>();
    }

    private void checkNotNull(String name, Object obj) throws IOException {
        if (obj == null) {
            throw new IOException(name + " cannot be null");
        }
    }

    private String parseExecUser(String json) throws IOException {
        Map<String, Object> resultMap;
        try {
            resultMap = JSONUtils.parseObject(json, Map.class);
        } catch (Exception e) {
            throw new IOException("Failed to parse execUser from json: " + json, e);
        }
        if (resultMap == null) {
            throw new IOException("Failed to parse execUser from json: " + json);
        }
        if (!resultMap.containsKey("jobParamData")) {
            throw new IOException("result does not contains \"jobParamData\"");
        }
        List<Object> jobParamData = (List<Object>) resultMap.get("jobParamData");
        for (Object elem : jobParamData) {
            if (elem instanceof Map) {
                if (((Map) elem).containsKey("paramName") &&
                        StringUtils.equals((String) ((Map) elem).get("paramName"), "user.to.proxy")) {
                    return (String) ((Map) elem).get("paramValue");
                }
            }
        }
        return "";
    }

    private Response makeHttpPost(String webServerUrl, String path, Map<String, String> params, Map<String, String> headers, String bodyJson) throws IOException {
        HttpUrl httpUrl = formatUrl(webServerUrl, path, params);

        Headers.Builder hb = new Headers.Builder();
        for (Map.Entry<String, String> entry : headers.entrySet()) {
            hb.add(entry.getKey(), entry.getValue());
        }

        Request request = new Request.Builder()
                .url(httpUrl)
                .headers(hb.build())
                .post(RequestBody.create(MediaType.parse("application/json"), bodyJson))
                .build();

        Call call = httpClient.newCall(request);

        Response response;
        try {
            response = call.execute();
        } catch (Exception e) {
            throw new IOException("failed to make http call. url: " + webServerUrl + " path: " + path, e);
        } finally {
//            if (response != null) {
//                response.close();
//            }
        }

        if (response == null) {
            throw new IOException("server returned null as response. url: " + webServerUrl + " path: " + path);
        } else if (!response.isSuccessful()) {
            throw new IOException("server response code suggest failure. url: " + webServerUrl + " path: " + path + " response: " + response.toString());
        }

        return response;
    }


    private Response makeHttpGet(String webServerUrl, String path, Map<String, String> params) throws IOException {
        HttpUrl httpUrl = formatUrl(webServerUrl, path, params);

        Request request = new Request.Builder()
                .url(httpUrl)
                .get()
                .build();

        Call call = httpClient.newCall(request);
        Response response = null;

        try {
            response = call.execute();
        } catch (Exception e) {
            throw new IOException("failed to make http call to server. url: " + webServerUrl + " path: " + path, e);
        } finally {
//            if (response != null) {
//                response.close();
//            }
        }
        if (response == null) {
            throw new IOException("server returned null as response. url: " + webServerUrl + " path: " + path);
        } else if (!response.isSuccessful()) {
            throw new IOException("server response code suggest failure. url: " + webServerUrl + " path: " + path + " response: " + response.toString());
        }

        return response;
    }

    private HttpUrl formatUrl(String webServerUrl, String path, Map<String, String> params) {
        String urlSuffix = params == null ? "" : params.keySet().stream()
                .map(key -> key + "=" + params.get(key))
                .collect(joining("&"));
        String url = webServerUrl + path + urlSuffix;
        logger.info("url: " + url);
        return HttpUrl.parse(url);
    }
}
