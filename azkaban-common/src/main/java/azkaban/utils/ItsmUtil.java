package azkaban.utils;

import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.apache.http.HttpEntity;
import org.apache.http.HttpStatus;
import org.apache.http.StatusLine;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;

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

        handleRequestRatifyChains(eoaChainStepNameNewOwner, baseNewOwner, requestRatifyChainsList, 0,
                2);
        handleRequestRatifyChains(eoaChainStepNameNewLeader, baseNewOwner, requestRatifyChainsList, 1,
                2);

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

    /**
     * 向 ITSM 发送应用信息登记服务请求
     *
     * @param props           WTSS 服务配置
     * @param username        请求用户名
     * @param projectName     项目名
     * @param flowId          工作流名
     * @param jobId           任务名
     * @param applicationInfo 应用信息
     * @param ret             响应
     */
    public static void sendRequest2Itsm4ApplicationInfo(Props props, String username,
                                                        String projectName,
                                                        String flowId, String jobId, Map<String, String> applicationInfo, Map<String, Object> ret)
            throws Exception {

        String itsmUrl = props.getString("itsm.url");
        String itsmInsertRequestUri = props.getString("itsm.insertRequest.uri");
        String appId = props.getString("itsm.appId");
        String appKey = props.getString("itsm.appKey");
        String itsmUserId = props.getString("itsm.userId");
        String itsmFormId = props.getString("itsm.application.info.form.id");
        String handler = props.getString("itsm.request.handler");

        JSONObject reqBody = new JSONObject();

        // 请求标题
        reqBody.put("requestTitle", username + " 申请 WTSS 应用信息登记");
        // 需求描述
        String requestDesc = "请求单描述";
        reqBody.put("requestDesc", requestDesc);
        // 紧急状态（2000不紧急，2001普通，2002紧急）传数字
        reqBody.put("requestUrgency", "2001");
        // 需求希望完成时间
        Calendar calendar = Calendar.getInstance();
        // 当前日期的 10 天后
        calendar.add(Calendar.DAY_OF_WEEK, 10);
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd");
        String requestEndDateStr = sdf.format(calendar.getTime());
        reqBody.put("requestEndDate", requestEndDateStr);
        // 关注人 (可以传多个用“,”分隔)
        reqBody.put("notifyUserIds", username);
        // 上报人
        reqBody.put("requestOwner", username);
        // 表单内容
        List<JSONObject> formsList = new ArrayList<>();
        JSONObject formJson = new JSONObject();
        List<JSONObject> dataList = new ArrayList<>();

        JSONObject formData = new JSONObject();
        // 需要匹配服务请求单的参数，不能多传
        formData.put("projectName", projectName);
        formData.put("flowId", flowId);
        formData.put("jobId", jobId);
        formData.put("batchGroupDesc", applicationInfo.get("batchGroupDesc"));
        formData.put("busPathDesc", applicationInfo.get("busPathDesc"));
        formData.put("busDomain", applicationInfo.get("busDomain"));
        formData.put("subsystemDesc", applicationInfo.get("subsystemDesc"));
        formData.put("busResLvl", applicationInfo.get("busResLvl"));
        formData.put("planStartTime", applicationInfo.get("planStartTime"));
        formData.put("planFinishTime", applicationInfo.get("planFinishTime"));
        formData.put("lastStartTime", applicationInfo.get("lastStartTime"));
        formData.put("lastFinishTime", applicationInfo.get("lastFinishTime"));
        formData.put("busTypeFirstDesc", applicationInfo.get("busTypeFirstDesc"));
        formData.put("busTypeSecondDesc", applicationInfo.get("busTypeSecondDesc"));
        formData.put("devDeptDesc", applicationInfo.get("devDeptDesc"));
        formData.put("opsDeptDesc", applicationInfo.get("opsDeptDesc"));
        formData.put("scanPartitionNum", applicationInfo.get("scanPartitionNum"));
        formData.put("scanDataSize", applicationInfo.get("scanDataSize"));
        dataList.add(formData);

        JSONObject formDataValueJson = new JSONObject();
        formDataValueJson.put("dataList", dataList);
        formJson.put("formDataValue", formDataValueJson);
        // 表单 id
        formJson.put("formId", itsmFormId);
        formsList.add(formJson);
        // 表单内容（数组）
        reqBody.put("forms", formsList);

        // 审批链
        List<JSONObject> requestRatifyChainsList = new ArrayList<>();
        String eoaChainStep1Name = "大数据平台批量组";
        String batchEoaUsers = props.getString("itsm.requst.eoaChainUsers",
                "huyangchen,chaogefeng,haydenhan,georgeqiao,jayceyang");
        String eoaChainStep2Name = "上报人直接上级";
        handleRequestRatifyChains(eoaChainStep1Name, batchEoaUsers, requestRatifyChainsList, 0, 1);
        handleRequestRatifyChains(eoaChainStep2Name, username, requestRatifyChainsList, 1, 0);
        reqBody.put("requestRatifyChains", requestRatifyChainsList);

        // 处理人。与表单配置有关，上报时选处理人为“否”时，不管接口是否传处理人，默认从服务目录处理人中随机取；上报时选处理人为“是”且“不限制处理人”时，当接口传处理人时-取接口传入的处理人，未传处理人时-从服务目录处理人中随机取；上报时选处理人为“是”且“指定处理人范围”时，当接口传处理人时-若所传人不在范围内会报错：“处理人限制为AAA，BBB”（AAA，BBB为指定的处理人），若在范围内会取接口传入的处理人，未传处理人时-从指定范围内随机取；上报时选处理人为“是”且“服务目录处理人”时，当接口传处理人时-若所传人不在服务目录处理人中会报错：“处理人限制为AAA，BBB”（AAA，BBB为服务目录处理人），若在服务目录内，取接口传入的处理人，未传处理人时-从服务目录处理人中随机取
        reqBody.put("requesthandler", handler);

        String result = doPost(itsmUrl + itsmInsertRequestUri, reqBody, appId, appKey, itsmUserId);
        JSONObject jsonResult = JSONObject.parseObject(result);

        int retCode = jsonResult.getIntValue("retCode");
        if (retCode == 0) {
            ret.put("requestInfo", "向 ITSM 提服务请求单成功，服务请求单号：" + jsonResult.getLong("data"));
            ret.put("itsmNo", jsonResult.getLong("data"));
        } else {
            ret.put("error", "向 ITSM 提服务请求单失败，失败原因：" + jsonResult.getString("retDetail"));
        }
    }

    /**
     * ITSM 服务请求审批链生成
     *
     * @param eoaChainStepName        审批名称
     * @param baseUser                审批人基准用户
     * @param requestRatifyChainsList 审批链处理人列表，和eoaChainUsers参数作用一样，可支持获取上级领导作为审批人 （只传英文名）
     * @param userType                审批人相对baseUserId 的类型（ 0是baseUserId;
     *                                1是baseUserId的上级领导;2:baseUserId的部门经理;3:baseUserId是部门长）
     * @param eoaChainStepType        处理类型(0:单人;1:协同;2:并行)
     */
    private static void handleRequestRatifyChains(String eoaChainStepName, String baseUser,
                                                  List<JSONObject> requestRatifyChainsList, int userType, int eoaChainStepType) {
        JSONObject requestRatifyChain = new JSONObject();
        // 审批链处理类型(0:单人审批;1:协同审批;2:并行审批)
        requestRatifyChain.put("eoaChainStepType", eoaChainStepType);
        // 审批重点说明
        requestRatifyChain.put("eoaChainChainTips", "审批重点说明");
        // 审批名称
        requestRatifyChain.put("eoaChainStepName", eoaChainStepName);
        List<JSONObject> eoaChainUserList = new ArrayList<>();
        String[] baseUsernameArray = baseUser.split(",");
        for (String baseUsername : baseUsernameArray) {
            JSONObject eoaChainUser = new JSONObject();
            // 审批人基准用户ID
            eoaChainUser.put("baseUserId", baseUsername);
            // 审批人相对baseUserId 的类型（ 0是baseUserId; 1是baseUserId的上级领导;2:baseUserId的部门经理;3:baseUserId是部门长）
            eoaChainUser.put("userType", userType);
            eoaChainUserList.add(eoaChainUser);
        }
        // 审批链处理人列表，和eoaChainUsers参数作用一样，可支持获取上级领导作为审批人 （只传英文名）
        requestRatifyChain.put("eoaChainUserList", eoaChainUserList);
        requestRatifyChainsList.add(requestRatifyChain);
    }

    /**
     * 根据 ITSM 服务请求单 ID 获取状态
     *
     * @param props     WTSS 服务配置
     * @param requestId 服务请求单 id
     * @param ret       响应
     */
    public static void getRequestFormStatus(Props props, long requestId, Map<String, Object> ret)
            throws Exception {
        // 接口鉴权参数
        String itsmUrl = props.getString("itsm.url");
        String itsmGetRequestUri = props.getString("itsm.getRequest.uri",
            "/itsm/requestApi/getDetailById.any");
        String appId = props.getString("itsm.appId");
        String appKey = props.getString("itsm.appKey");
        String itsmUserId = props.getString("itsm.userId");

        JSONObject reqBody = new JSONObject();
        reqBody.put("id", requestId);

        String result = doPost(itsmUrl + itsmGetRequestUri, reqBody, appId, appKey, itsmUserId);

        JSONObject jsonResult = JSONObject.parseObject(result);

        int retCode = jsonResult.getIntValue("retCode");
        if (retCode == 0) {
            int requestStatus = jsonResult.getJSONObject("data").getIntValue("requestStatus");
            Long requestRatifyFinishTime = jsonResult.getJSONObject("data").getLongValue("requestRatifyFinishTime");
            String requestUser = jsonResult.getJSONObject("data").getString("requestOwner");
            ret.put("requestStatus", requestStatus);
            ret.put("requestRatifyFinishTime",requestRatifyFinishTime);
            ret.put("requestUser",requestUser);
            JSONArray jsonArray = jsonResult.getJSONObject("data").getJSONArray("requestFormCis").getJSONObject(0).getJSONArray("sheets").getJSONArray(0);
            for (int i = 0; i < jsonArray.size(); i++) {
                JSONObject jsonObject = jsonArray.getJSONObject(i);
                String name = jsonObject.getString("name");
                String text = jsonObject.getString("text");
                ret.put(name,text);

            }

        } else {
            ret.put("error",
                    "向 ITSM 获取服务请求单信息失败，失败原因：" + jsonResult.getString("retDetail"));
        }
    }

  /*public static void main(String[] args) throws Exception {

    String appId = "302";
    String appKey = "099ab2c5-789c-48be-bedb-ed3cbca16183";
    String itsmUrl = "http://***REMOVED***:8080/proxy/toOA";
    String requestUri = "/itsm/request/insertRequestAuth.any";
    String getRequestUri = "/itsm/requestApi/getDetailById.any";
    String userId = "lebronwang";
    String itsmFormId = "10007599";
    String requestHandler = "lebronwang";

    Props props = new Props();
    props.put("itsm.url", itsmUrl);
    props.put("itsm.insertRequest.uri", requestUri);
    props.put("itsm.appId", appId);
    props.put("itsm.appKey", appKey);
    props.put("itsm.userId", userId);
    props.put("itsm.application.info.form.id", itsmFormId);
    props.put("itsm.request.handler", requestHandler);
    props.put("itsm.getRequest.uri", getRequestUri);
    props.put("itsm.requst.eoaChainUsers", "huyangchen,chaogefeng,georgeqiao,haydenhan");

    Map<String, String> formMap = new HashMap<>();
    formMap.put("batchGroupDesc", "关键批量分组");
    formMap.put("busPathDesc", "关键路径");
    formMap.put("busDomain", "业务域");
    formMap.put("subsystemDesc", "WTSS-BDPWFM");
    formMap.put("busResLvl", "B");
    formMap.put("planStartTime", "00:00");
    formMap.put("planFinishTime", "00:00");
    formMap.put("lastStartTime", "00:00");
    formMap.put("lastFinishTime", "00:00");
    formMap.put("busTypeFirstDesc", "业务/产品一级分类");
    formMap.put("busTypeSecondDesc", "业务/产品二级分类");
    formMap.put("devDeptDesc", "开发科室");
    formMap.put("opsDeptDesc", "运维科室");
    formMap.put("opsDeptDesc1", "运维科室");

    Map<String, Object> ret = new HashMap<>();
    Map<String, String> headers = genHeaders(appId, appKey, userId);
    System.out.println(headers);

    sendRequest2Itsm4ApplicationInfo(props, userId, "项目名", "工作流名", "任务名", formMap, ret);

    System.out.println(ret);
    if (ret.containsKey("itsmNo")) {
      long itsmNo = (long) ret.get("itsmNo");

      getRequestFormStatus(props, itsmNo, ret);
    }
    System.out.println(ret);
  }*/

    public static void main(String[] args) throws Exception {

        // 接口鉴权参数
        String itsmUrl = "http://***REMOVED***:8080/proxy/toOA";
        String itsmGetRequestUri = "/itsm/requestApi/getDetailById.any";
        String appId = "302";
        String appKey = "099ab2c5-789c-48be-bedb-ed3cbca16183";
        String itsmUserId = "lebronwang";

        JSONObject reqBody = new JSONObject();
        reqBody.put("id", 11688405);

        String result = doPost(itsmUrl + itsmGetRequestUri, reqBody, appId, appKey, itsmUserId);


        JSONObject jsonResult = JSONObject.parseObject(result);

        int retCode = jsonResult.getIntValue("retCode");
        System.out.println(jsonResult);
        if (retCode == 0) {
            int requestStatus = jsonResult.getJSONObject("data").getIntValue("requestStatus");
            JSONArray jsonArray = jsonResult.getJSONObject("data").getJSONArray("requestFormCis").getJSONObject(0).getJSONArray("sheets").getJSONArray(0);
            Long requestRatifyFinishTime = jsonResult.getJSONObject("data").getLongValue("requestRatifyFinishTime");
            System.out.println(requestRatifyFinishTime);
            for (int i = 0; i < jsonArray.size(); i++) {
                JSONObject jsonObject = jsonArray.getJSONObject(i);
                String name = jsonObject.getString("name");
                String text = jsonObject.getString("text");
                if(name.equals("projectName")){
                    System.out.println(text);
                }
                if (name.equals("flowId")){
                    System.out.println(text);
                }
                if (name.equals("jobId")){
                    System.out.println(text);
                }

            }

            System.out.println(requestStatus);
        } else {

        }
    }

}
