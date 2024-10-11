package azkaban.utils;

import azkaban.Constants;
import azkaban.ServiceProvider;
import azkaban.executor.DmsBusPath;
import azkaban.executor.ExecutableFlow;
import azkaban.executor.ExecutorLoader;
import azkaban.project.entity.FlowBusiness;
import azkaban.project.entity.LineageBusiness;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import okhttp3.*;
import okhttp3.HttpUrl.Builder;
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang.StringUtils;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.math.BigDecimal;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.TimeUnit;

/**
 *
 */
public class HttpUtils {

    private static final Logger logger = LoggerFactory.getLogger(HttpUtils.class);

    public static Map<String, String> getReturnMap(String dataStr) {
        Map<String, String> dataMap = new HashMap<>();
        GsonBuilder gb = new GsonBuilder();
        Gson g = gb.create();
        dataMap = g.fromJson(dataStr, new TypeToken<Map<String, String>>() {
        }.getType());
        return dataMap;
    }

    public static OkHttpClient okHttpClient = new OkHttpClient.Builder()
            .connectTimeout(60, TimeUnit.SECONDS)
            .writeTimeout(60, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .build();




    /**
     * 工作流执行IMS上报 接口 HTTP同步远程执行方法
     * <p>
     * 参数格式
     * subSystemId=1001&jobCode=111&jobDate=10214&ip=12312&status=3&alertLevel=1
     * 请求地址
     * http://10.255.4.120:10815/ims_config/job_report.do
     *
     * @param actionUrl
     * @param requestBody
     * @return
     */
    public static String httpClientIMSHandle(String actionUrl, RequestBody requestBody, Map<String, String> urlMap) throws Exception {

//    String maskUrl = actionUrl + "subSystemId=" + urlMap.get("subSystemId") + "&jobCode=" + urlMap.get("jobCode")
//        +  "&jobDate=" + urlMap.get("jobDate") + "&ip=" + urlMap.get("ip") + "&status=" + urlMap.get("status")
//        + "&alertLevel=" + urlMap.get("alertLevel");
        String maskUrl = actionUrl;
        //subSystemId=1001&jobCode=111&jobDate=10214&ip=12312&status=3&alertLevel=1

        Request request = new Request.Builder()
                .url(maskUrl)
                .post(requestBody)
                .build();

        Call call = okHttpClient.newCall(request);
        Response response = call.execute();
        String res = response.body().string();
        IOUtils.closeQuietly(response);
        return res;
    }

    public static String getValue(Props props, String key) {
        if (StringUtils.isNotBlank(props.get(key))) {
            return props.get(key) == null ? props.get(key) : props.get(key).trim();
        }
        if (props.getParent() != null && StringUtils.isNotBlank(props.getParent().get(key))) {
            return props.getParent().get(key) == null ? props.getParent().get(key) : props.getParent().get(key).trim();
        }
        return null;
    }

    /**
     * curl http://10.255.4.120:10815/ims_config/add_itsm_batch_job.do -d \
     * '[{
     * "subsystem_id": "1234",
     * "planStartTime": "19:46",
     * "planFinishTime": "19:46",
     * "lastStartTime": "19:46",
     * "lastFinishTime": "19:46",
     * "groupName": "flowname"
     * }]'
     *
     * @param executableFlow
     * @param azkabanProps
     */
    public static void registerToIMS(final ExecutableFlow executableFlow, final Props azkabanProps,
                                     final Props flowPros, final Logger logger, final FlowBusiness flowBusiness, Props props) {
        try {

            Map<String, String> dataMap = new HashMap<>();

            if (flowBusiness == null || StringUtils.isEmpty(flowBusiness.getPlanStartTime())) {
                if (flowPros == null || getValue(flowPros, "reportIMS") == null || !"true"
                        .equals(getValue(flowPros, "reportIMS").trim().toLowerCase())) {
                    logger.info("not need register to IMS.");
                    return;
                } else {
                    logger.info("get ims from properties");
                    String subSystemId = getValue(flowPros, "subSystemId");
                    dataMap.put("subsystem_id",
                            StringUtils.isEmpty(subSystemId) ? azkabanProps.get("ims.job.report.subSystemId")
                                    : subSystemId);
                    dataMap.put("planStartTime", getValue(flowPros, "planStartTime"));
                    dataMap.put("planFinishTime", getValue(flowPros, "planFinishTime"));
                    dataMap.put("lastStartTime", getValue(flowPros, "lastStartTime"));
                    dataMap.put("lastFinishTime", getValue(flowPros, "lastFinishTime"));
                    dataMap.put("number", getValue(flowPros, "dcnNumber"));
                    dataMap.put("keyPathId", getValue(flowPros, "keyPathId"));
                    dataMap.put("cmdbGroupId", getValue(flowPros, "groupId"));
                    dataMap.put("app_domain", getValue(flowPros, "appDomain"));
                    dataMap.put("updater", getValue(flowPros, "imsUpdater"));
                    dataMap.put("remark", getValue(flowPros, "imsRemark"));
                }
                send2Ims(executableFlow.getProjectName(), executableFlow.getFlowId(), azkabanProps, dataMap,
                        logger, props);
            }
        } catch (Exception e) {
            logger.warn("获取ims配置参数失败", e);
        }

    }

    public static String registerToIMS(String projectName, String flowId, Props azkabanProps,
                                       FlowBusiness flowBusiness, Props props) {
        try {
            Map<String, String> dataMap = new HashMap<>();
            dataMap.put("subsystem_id", flowBusiness.getSubsystem());
            dataMap.put("planStartTime", flowBusiness.getPlanStartTime());
            dataMap.put("planFinishTime", flowBusiness.getPlanFinishTime());
            dataMap.put("lastStartTime", flowBusiness.getLastStartTime());
            dataMap.put("lastFinishTime", flowBusiness.getLastFinishTime());
            dataMap.put("number", flowBusiness.getDcnNumber());
            dataMap.put("keyPathId", flowBusiness.getBusPath());
            dataMap.put("cmdbGroupId", flowBusiness.getBatchGroup());
            dataMap.put("app_domain", flowBusiness.getBusDomain());
            dataMap.put("updater", flowBusiness.getImsUpdater());
            dataMap.put("remark", flowBusiness.getImsRemark());
            return send2Ims(projectName, flowId, azkabanProps, dataMap, null, props);
        } catch (Exception e) {
            logger.error("set ims by flow business error", e);
            return e.getMessage();
        }

    }

    private static String send2Ims(String projectName, String flowId, Props azkabanProps,
                                   Map<String, String> dataMap, final Logger log1, final Props props) {
        Logger log = log1 == null ? logger : log1;

        String request;
        String actionUrl = azkabanProps.getString("ims.job.register.url", null);
        try {
            String jobCode = DmsBusPath
                    .createJobCode(props.get(Constants.JobProperties.JOB_BUS_PATH_CODE_PREFIX), projectName, flowId);
            dataMap.put("jobCode", jobCode);
            dataMap.put("jobZhName", jobCode);
            dataMap.put("isKeyPath", StringUtils.isEmpty(dataMap.get("keyPathId")) ? "0" : "1");
            dataMap.put("source", "WTSS");

            List<Map> dataList = new ArrayList<>();
            dataList.add(dataMap);
            request = GsonUtils.toJson(dataList);

            if (actionUrl == null) {
                log.error("获取注册接口失败");
                return "get ims url error";
            }
            log.info("url is : " + actionUrl + " requestBody is " + request);

        } catch (Exception e) {
            log.warn("获取ims配置参数失败", e);
            return e.getMessage();
        }
        try {
            MediaType applicationJson = MediaType.parse("application/json;charset=utf-8");
            RequestBody requestBody = RequestBody.create(applicationJson, request);
            log.info("register to IMS, flowId is " + flowId);
            String result = HttpUtils.httpClientIMSHandle(actionUrl, requestBody, null);
            log.info("register result is : " + result);
            Map<String, Object> resultMap = GsonUtils.json2Map(result);
            if (!"0.0".equals(resultMap.get("retCode").toString()) && !"0"
                    .equals(resultMap.get("retCode").toString())) {
                return result;
            }
        } catch (Exception e) {
            log.warn("registerToIMS, failed", e);
            return e.getMessage();
        }
        return "";
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

    /**
     * 从cmdb获取字典数据
     *
     * @param prop
     * @param type
     * @param id
     * @param name
     * @param query
     * @param start
     * @param page
     * @param ret
     * @param isPaging
     * @return
     */
    public static List<Map<String, Object>> getCmdbData(Props prop, String urlProp, String type, String id, String queryId, String name, String query, int start, int page, HashMap<String, Object> ret, boolean isPaging) {
        String resultJson = "";
        try {
            String url = prop.getString("wtss.cmdb.url") + prop.getString(urlProp);
            String userName = prop.getString("wtss.cmdb.user");
            Map<String, Object> param = new HashMap<>();
            Map<String, String> filter = new HashMap<>();
            filter.put(name, "%" + query + "%");
            filter.put(id, "%" + queryId + "%");
            param.put("userAuthKey", userName);
            param.put("type", type);
            param.put("action", "select");
            param.put("isPaging", isPaging);
            param.put("filter", filter);
            param.put("startIndex", start * page);
            param.put("pageSize", page);
            param.put("resultColumn", new ArrayList<>(Arrays.asList(id, name)));
            String json = GsonUtils.toJson(param);
            logger.debug("cmdb data json: " + json);

            MediaType applicationJson = MediaType.parse("application/json;charset=utf-8");
            RequestBody requestBody = RequestBody.create(json, applicationJson);

            Request request = new Request.Builder()
                    .url(url)
                    .post(requestBody)
                    .build();

            Call call = okHttpClient.newCall(request);
            Response response = call.execute();
            resultJson = response.body().string();
            IOUtils.closeQuietly(response);
            if (StringUtils.isNotEmpty(resultJson)) {
                Map<String, Object> result = GsonUtils.json2Map(resultJson);
                ret.put("dataList", ((Map<String, Object>) result.get("data")).get("content"));
                ret.put("total", ((Map<String, Object>) result.get("headers")).get("totalRows"));
            }
        } catch (Exception e) {
            logger.info("cmdb result: " + resultJson);
            logger.warn("get data from cmdb error", e);
        }
        return new ArrayList<>();
    }

    public static String getCmdbAppDomainBySubsystemId(Props prop, String subsystemId)
            throws Exception {

        String resultJson = "";
        String url =
                prop.getString("wtss.cmdb.url") + prop.getString("wtss.cmdb.getIntegrateTemplateData");
        String userName = prop.getString("wtss.cmdb.user");
        Map<String, Object> param = new HashMap<>();
        param.put("userAuthKey", userName);
        param.put("type", "subsystem_app_instance");
        param.put("action", "select");
        param.put("isPaging", false);
        param.put("resultColumn", new ArrayList<>(Arrays.asList("appdomain_cnname")));
        Map<String, String> filter = new HashMap<>();
        filter.put("subsystem_id", subsystemId);
        String filterJson = GsonUtils.toJson(filter);
        param.put("filter", filterJson);
        String json = GsonUtils.toJson(param);
        logger.debug("cmdb data json: " + json);

        MediaType applicationJson = MediaType.parse("application/json;charset=utf-8");
        RequestBody requestBody = RequestBody.create(json, applicationJson);

        Request request = new Request.Builder()
                .url(url)
                .post(requestBody)
                .build();

        Call call = okHttpClient.newCall(request);
        Response response = call.execute();
        resultJson = response.body().string();
        IOUtils.closeQuietly(response);
        if (StringUtils.isNotEmpty(resultJson)) {
            Map<String, Object> result = GsonUtils.json2Map(resultJson);
            List<Map<String, Object>> appDomainList = (List<Map<String, Object>>) ((Map<String, Object>) result.get(
                    "data")).get("content");
            String appDomainCnName = (String) appDomainList.get(0).get("appdomain_cnname");
            return appDomainCnName;
        }

        return "";
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
                    if ("deleteProject".equals(type)) {
                        url = url + "&projectId=" + data;
                    } else {
                        url = url + "&projectName=" + data;
                    }

                } else if (type.contains("EventSchedule")) {
                    url += "&scheduleId=" + data;
                }

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
            logger.warn("reload web data error", e);
        }

    }

    /**
     * 从dms获取关键路径数据
     *
     * @param prop
     * @param jobCode
     * @param page
     * @return
     */
    public static Set<DmsBusPath> getBusPathFromDms(Props prop, String jobCode, int page, int execId) {
        String resultJson = "";
        Set<DmsBusPath> jobCodeSet = new HashSet<>();
        try {
            String url = prop.getString("wtss.dms.url") + "/metadata-service/batchApp/busPath/nodes";
            String userName = prop.getString("wtss.dms.user");
            String appId = prop.getString("wtss.dms.appid");
            String token = prop.getString("wtss.dms.token");
            long timestamp = System.currentTimeMillis();
            int nonce = new Random().nextInt(90000) + 10000;
            String signature = Encrypt(Encrypt(appId + nonce + userName + timestamp, null) + token, null);
            int pageSize = prop.getInt("wtss.dms.pagesize", 10);

            HttpUrl httpUrl = HttpUrl.parse(url).newBuilder()
                    .addQueryParameter("timestamp", timestamp + "").addQueryParameter("loginUser", userName)
                    .addQueryParameter("appid", appId).addQueryParameter("nonce", nonce + "")
                    .addQueryParameter("signature", signature).addQueryParameter("jobCode", jobCode)
                    .addQueryParameter("pageNum", page + "").addQueryParameter("pageSize", pageSize + "")
                    .build();
            logger.info("execId:" + execId + ",dms url:" + httpUrl.toString());


            Request request = new Request.Builder().url(httpUrl).get().build();

            Call call = okHttpClient.newCall(request);
            Response response = call.execute();
            resultJson = response.body().string();
            IOUtils.closeQuietly(response);
            if (StringUtils.isNotEmpty(resultJson)) {
                Map<String, Object> result = GsonUtils.json2Map(resultJson);
                Map<String, Object> data = (Map<String, Object>) result.get("data");
                if (null == data) {
                    throw new RuntimeException("DMS return error result: " + resultJson);
                }
                for (Map<String, Object> content : (List<Map<String, Object>>) data.get("content")) {
                    DmsBusPath dmsBusPath = new DmsBusPath();
                    dmsBusPath.setBusPathId(new BigDecimal(content.get("busPathId") + "").intValue() + "");
                    dmsBusPath.setBusPathName(content.get("busPathName") + "");
                    dmsBusPath.setJobCode(content.get("jobCode") + "");
                    dmsBusPath.setStatus(content.get("status") + "");
                    jobCodeSet.add(dmsBusPath);
                }

                if (page * pageSize < new BigDecimal(data.get("totalCount") + "").intValue()) {
                    jobCodeSet.addAll(getBusPathFromDms(prop, jobCode, ++page, execId));
                }
            }
        } catch (Exception e) {
            logger.warn("execId:" + execId + ",get data from dms error", e);
        } finally {
            logger.info("execId:" + execId + ",dms result: " + resultJson);
        }
        return jobCodeSet;
    }

    /**
     * 只支持查询数据库中修改时间为一个月内的数据，如果超过该值需要查询DMS
     * @param prop
     * @param jobCode
     * @param page
     * @param execId
     * @param executorLoader
     * @return
     */
    public static Set<DmsBusPath> getBusPathFromDBOrDms(Props prop, String jobCode, int page, int execId,
                                                        ExecutorLoader executorLoader, Logger joblogger) {
        if (executorLoader == null) {
            executorLoader = ServiceProvider.SERVICE_PROVIDER.getInstance(ExecutorLoader.class);
        }
        if (executorLoader == null) {
            return  null;
        }
        if (null == joblogger) {
            joblogger = logger;
        }
        try {
            LocalDate currentDate = LocalDate.now();
            LocalDate lastMonth = currentDate.minusMonths(1);
            String lastMonthString = lastMonth.format(DateTimeFormatter.ofPattern("yyyy-MM-dd"));
            Set<DmsBusPath> paths = executorLoader.getDmsBusPathFromDb(jobCode, lastMonthString);
            if (CollectionUtils.isEmpty(paths)) {
                paths = getBusPathFromDms(prop, jobCode, page, execId);
                joblogger.info("From DMS to get bus info for jobCode {}, execID {}, get size {}", jobCode, execId, paths.size());
                if (!paths.isEmpty()) {
                    Iterator<DmsBusPath> iterator = paths.iterator();
                    while (iterator.hasNext()) {
                        DmsBusPath dmsBusPath = iterator.next();
                        dmsBusPath.setModifiedTime(new Timestamp(System.currentTimeMillis()));
                        joblogger.info("From DMS to get bus info for jobCode {}, bus name {}", dmsBusPath.getJobCode(), dmsBusPath.getBusPathName());
                        if (org.apache.commons.lang3.StringUtils.isNoneBlank(dmsBusPath.getBusPathName(), dmsBusPath.getJobCode())) {
                            executorLoader.insertOrUpdate(dmsBusPath);
                        }
                    }
                }
            } else {
                joblogger.info("From DB get paths, update time {}", lastMonthString);
            }
            return paths;
        } catch (Exception e) {
            joblogger.warn("Failed to get bus info  for jobCode {}, execId {}", jobCode, execId, e);
        }
        return null;
    }

    public static String Encrypt(String strSrc, String encName) throws UnsupportedEncodingException {
        MessageDigest md;
        String strDes;

        byte[] bt = strSrc.getBytes("utf-8");
        try {
            if (encName == null || encName.equals("")) {
                encName = "SHA-256";
            }
            md = MessageDigest.getInstance(encName);
            md.update(bt);
            strDes = bytes2Hex(md.digest()); // to HexString
        } catch (NoSuchAlgorithmException e) {
            return null;
        }
        return strDes;
    }

    public static String bytes2Hex(byte[] bts) {
        String des = "";
        String tmp;
        for (int i = 0; i < bts.length; i++) {
            tmp = (Integer.toHexString(bts[i] & 0xFF));
            if (tmp.length() == 1) {
                des += "0";
            }
            des += tmp;
        }
        return des;
    }

    public static void getLineageBusiness(String username, Props prop, String jobCode,
                                          String searchType, Map<String, Object> ret, int pageNum, int pageSize,
                                          String searchDateType) {

        String appId = prop.get("wtss.dms.appid");
        String token = prop.get("wtss.dms.token");
        int interval = prop.getInt("wtss.dms.lineage.month.interval", 1);

        ret.put("jobCode", jobCode);

        String url = prop.getString("wtss.dms.url") + prop.getString("wtss.dms.lineage.business.uri");

        String envFlag = prop.getString("wtss.dms.envFlag", "prod");

        String resultJson = "";

        try {
            long timestamp = System.currentTimeMillis();
            int nonce = new Random().nextInt(90000) + 10000;
            String signature = Encrypt(Encrypt(appId + nonce + username + timestamp, null) + token, null);

            Calendar calendar = Calendar.getInstance();
            long endTime = calendar.getTimeInMillis();
            calendar.add(2, -interval);
            long startTime = calendar.getTimeInMillis();

            HttpUrl httpUrl = null;

            Builder httpUrlBuilder = HttpUrl.parse(url).newBuilder()
                    .addQueryParameter("timestamp", timestamp + "")
                    .addQueryParameter("loginUser", username)
                    .addQueryParameter("appid", appId)
                    .addQueryParameter("nonce", nonce + "")
                    .addQueryParameter("signature", signature)
                    .addQueryParameter("startTime", String.valueOf(startTime))
                    .addQueryParameter("endTime", String.valueOf(endTime))
                    .addQueryParameter("direction", searchDateType)
                    .addQueryParameter("searchType", searchType)
                    .addQueryParameter("isolateEnvFlag", envFlag);

            httpUrl = httpUrlBuilder.addQueryParameter("urn", jobCode).build();


            Request request = new Request.Builder()
                    .url(httpUrl)
                    .get()
                    .build();

            Call call = okHttpClient.newCall(request);
            Response response = call.execute();
            resultJson = response.body().string();
            IOUtils.closeQuietly(response);
            if (StringUtils.isNotEmpty(resultJson)) {
                Map<String, Object> result = GsonUtils.json2Map(resultJson);
                String responseCode = (String) result.get("code");
                ret.put("code", responseCode);
                String responseMsg = (String) result.get("msg");

                List<LineageBusiness> lineageBusinessesList = new ArrayList<>();

                if ("200".equals(responseCode)) {
                    Map<String, Object> data = (Map<String, Object>) result.get("data");
                    List<Map<String, Object>> nodeList = (List<Map<String, Object>>) data.get("nodeSet");

                    if (nodeList != null && !nodeList.isEmpty()) {
                        for (Map<String, Object> node : nodeList) {
                            LineageBusiness lineageBusiness = new LineageBusiness();

                            String id = (String) node.get("id");

                            if (id.startsWith("WTSS/")) {
                                continue;
                            }

                            String datasourceType = id.substring(0, id.indexOf(':'));
                            lineageBusiness.setDataSourceType(datasourceType);
                            Map<String, Object> attribute = (Map<String, Object>) node.get("attr");

                            lineageBusiness.setCluster((String) attribute.get("CLUSTER_CODE"));
                            lineageBusiness.setDatabase((String) attribute.get("DATABASE"));
                            lineageBusiness.setTable((String) attribute.get("TABLE"));
                            lineageBusiness.setSubsystem((String) attribute.get("subsystemName"));
                            lineageBusiness.setDevelopDepartment((String) attribute.get("devDept"));
                            lineageBusiness.setDeveloper((String) attribute.get("devOwner"));

                            lineageBusinessesList.add(lineageBusiness);

                        }
                    }
                } else {
                    // 返回接口返回异常信息
                    ret.put("error", "Failed to get lineage business data. " + responseMsg);
                    return;
                }

                ret.put("direction", searchDateType);
                ret.put("lineageBusinessListSize", lineageBusinessesList.size());
                PagingListStreamUtil<LineageBusiness> pagingListStreamUtil = new PagingListStreamUtil<>(
                        lineageBusinessesList, pageSize);
                pagingListStreamUtil.setCurPageNo(pageNum);
                ret.put("lineageBusinessList", pagingListStreamUtil.currentPageData());
                ret.put("pageNum", pageNum);
                ret.put("pageSize", pageSize);
            }
        } catch (Exception e) {
            logger.warn("Failed to get lineage business data. " + e.getMessage());
            ret.put("error", "Failed to get lineage business data. " + e.getMessage());
        }
    }


    public static void alert2Ims(String url, String json, Logger alertLogger) {
        try {
            alertLogger.info("alert ims json: {}", json);

            MediaType applicationJson = MediaType.parse("application/json;charset=utf-8");
            RequestBody requestBody = RequestBody.create(json, applicationJson);

            Request request = new Request.Builder()
                    .url(url)
                    .post(requestBody)
                    .build();

            Call call = okHttpClient.newCall(request);
            Response response = call.execute();
            alertLogger.info("ims batch alert result: {}", response.body().string());
            IOUtils.closeQuietly(response);
        } catch (Exception e) {
            alertLogger.error("ims batch alert error", e);
        }
    }


}

