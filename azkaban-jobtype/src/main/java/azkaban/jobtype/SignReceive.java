package azkaban.jobtype;

import azkaban.jobExecutor.AbstractJob;
import azkaban.jobtype.util.EncryptUtil;
import azkaban.utils.Props;
import cn.hutool.core.lang.TypeReference;
import cn.hutool.core.util.RandomUtil;
import cn.hutool.crypto.digest.DigestUtil;
import cn.hutool.http.HttpRequest;
import cn.hutool.http.HttpResponse;
import cn.hutool.json.JSONUtil;
import org.slf4j.Logger;
import org.springframework.util.StringUtils;
import org.springframework.web.util.UriComponentsBuilder;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.TemporalAdjusters;
import java.util.*;

public class SignReceive extends AbstractJob {

    private static final int NONCE_LENGTH = 5;

    private final Logger log;

    protected volatile Props jobProps;
    protected volatile Props sysProps;
    protected volatile Map<String, String> jobPropsMap;

    private boolean isCanceled = false;

    private Thread runThread;

    public SignReceive(String jobId, Props sysProps, Props jobProps, Logger log) {

        super(jobId, log);

        this.jobProps = jobProps;

        this.sysProps = sysProps;

        this.jobPropsMap = this.jobProps.getMapByPrefix("");

        this.log = log;
    }

    @Override
    public void run() throws Exception {
        runThread = Thread.currentThread();
        String batchDate = jobProps.getString("batch_date");
        String signRunDay = jobProps.getString("sign_run_day", "");
        String signCancelDay = jobProps.getString("sign_cancel_day", "");
        if(!needExec(batchDate, signRunDay, signCancelDay)) {
            info("当前日期不需要执行信号相关操作, batchDate:" + batchDate);
        } else {
            String receiveName = jobProps.getString("receive_name");
            String overwriteNames = jobProps.getString("overwrite_names");
            long waitTime = jobProps.getLong("wait_time");
            int times = jobProps.getInt("times");
            String msgTraceTime = jobProps.getString("msg_trace_time");
            String execId = jobProps.getString("exec_id");
            String appId = jobProps.getString("app_id", "");
            String appToken = jobProps.getString("app_token", "");
            appToken = EncryptUtil.decryptPassword(appToken);
            String appHostPort = jobProps.getString("app_host_port");
            String receiveUrl = jobProps.getString("receive_url");
            info("收到查询信号请求,batchDate:" + batchDate + ", receiveName:" + receiveName + ", overwriteNames:" + overwriteNames
                    + ", waitTime:" + waitTime + ", times:" + times + ", msgTraceTime:" + msgTraceTime + ", execId:" + execId
                    + ", signRunDay:" + signRunDay + ", signCancelDay:" + signCancelDay);
            if(waitTime < 300) {
                throw new RuntimeException("wait_time不能为空且值不能小于300");
            }
            List<String> receiveSignList = parseReceiveSign(overwriteNames, receiveName);
            if(receiveSignList.isEmpty()) {
                throw new RuntimeException("查询信号解析后得到的需要查询信号名称为空");
            }

            LocalDateTime receiveTime = null;
            if("TODAY".equalsIgnoreCase(msgTraceTime)) {
                receiveTime = LocalDateTime.of(LocalDate.now(), LocalTime.MIN);
            } else {
                receiveTime = LocalDateTime.now().minusSeconds(Long.parseLong(msgTraceTime));
            }

            info("需要查询的信号:" + JSONUtil.toJsonStr(receiveSignList));
            int count = 0;
            while (!isCanceled) {
                int current = count + 1;
                info("开始第:" + current + "次查询所有信号信息");
                boolean flag = true;
                try {
                    for (String receiveSign : receiveSignList) {
                        Map<String, Object> params = new HashMap<>();
                        params.put("signName", receiveSign);
                        params.put("batchDate", batchDate);
                        String requestUrl = "";
                        if(StringUtils.isEmpty(appId)) {
                            requestUrl = appHostPort + receiveUrl;
                        } else {
                            requestUrl = appendUri(appHostPort + receiveUrl, appId, appToken);
                        }
                        String response = null;
                        HttpResponse httpResponse = null;
                        try {
                            httpResponse = HttpRequest.post(requestUrl).timeout(1000).body(JSONUtil.toJsonStr(params)).execute();
                            response = httpResponse.body();
                        } finally {
                            if(null != httpResponse) {
                                httpResponse.close();
                            }
                        }
                        info("查询单个信号, url:" + requestUrl + " params:" + JSONUtil.toJsonStr(params) + ", response:" + response);
                        if(StringUtils.isEmpty(response)) {
                            throw new RuntimeException("查询信号返回为空");
                        }
                        Map<String, Object> res = JSONUtil.toBean(response, new TypeReference<Map<String, Object>>() {
                        }, false);
                        int code = (int) res.get("code");
                        if(code != 200) {
                            throw new RuntimeException("请求保存信号返回异常，code:" + code);
                        }
                        Object data = res.get("result");
                        if(null == data || StringUtils.isEmpty(((Map<String, String>) data).get("id"))) {
                            info("查询信号名称：" + receiveSign + "未查询到发送信号数据");
                            flag = false;
                            continue;
                        }
                        Map<String, String> signInfo = (Map<String, String>) data;
                        DateTimeFormatter dateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
                        LocalDateTime sendTime = LocalDateTime.parse(signInfo.get("sendTime"), dateTimeFormatter);
                        info("查询信号名称：" + receiveSign + ",跑批日期：" + batchDate + ",查询信号允许发送最早时间：" + receiveTime.format(dateTimeFormatter) + "查询到的最新信号发送时间：" + signInfo.get("sendTime"));
                        if(!sendTime.isAfter(receiveTime)) {
                            flag = false;
                        }
                    }
                } catch (Exception e) {
                    error("查询信号异常", e);
                    flag = false;
                }

                if(flag) {
                    info("需要查询的依赖信号：" + JSONUtil.toJsonStr(receiveSignList) + "都判断通过");
                    break;
                } else {
                    info("需要查询的依赖信号：" + JSONUtil.toJsonStr(receiveSignList) + "未完全判断通过");
                }

                if(current >= times) {
                    throw new RuntimeException("查询信号，receiveName：" + receiveName + ", overwriteNames: " + overwriteNames + "查询次数已用完，查询信号未完全判断通过");
                }
                count++;
                Thread.sleep(waitTime * 1000);
            }
        }
    }

    @Override
    public void cancel() throws Exception {
        isCanceled = true;
        if(null != runThread) {
            runThread.interrupt();
        }
        warn("This job has been canceled");
    }

    @Override
    public boolean isCanceled() {
        return isCanceled;
    }

    private boolean needExec(String batchDate, String signRunDay, String signCancelDay) {
        DateTimeFormatter dtf = DateTimeFormatter.ofPattern("yyyy-MM-dd");
        LocalDate dateTime = LocalDate.parse(batchDate, dtf);
        Set<Integer> needExecDay = new HashSet<>();
        LocalDate lastDayTime = dateTime.with(TemporalAdjusters.lastDayOfMonth());
        int lastDay = lastDayTime.getDayOfMonth();
        if(StringUtils.isEmpty(signRunDay)) {
            for (int i = 1; i <= lastDay; i++) {
                needExecDay.add(i);
            }
        } else {
            String[] runDays = signRunDay.split(",");
            for (String day : runDays) {
                if(!day.toUpperCase().contains("LASTDAY")) {
                    needExecDay.add(Integer.parseInt(day));
                } else {
                    needExecDay.add(calculateLastDay(lastDay, day));
                }
            }
        }
        if(!StringUtils.isEmpty(signCancelDay)) {
            String[] cancelDays = signCancelDay.split(",");
            for (String day : cancelDays) {
                if(!day.toUpperCase().contains("LASTDAY")) {
                    needExecDay.remove(Integer.parseInt(day));
                } else {
                    needExecDay.remove(calculateLastDay(lastDay, day));
                }
            }
        }
        return needExecDay.contains(dateTime.getDayOfMonth());
    }

    private Integer calculateLastDay(int lastDay, String day) {
        String[] strings = day.trim().split("-");
        if(strings.length == 1) {
            return lastDay;
        }
        return lastDay - Integer.parseInt(strings[1]);
    }

    private List<String> parseReceiveSign(String overwriteNames, String receiveName) {
        List<String> receiveSign = new ArrayList<>();
        if(StringUtils.isEmpty(receiveName)) {
            throw new RuntimeException("接收信号分组名称为空");
        }
        String[] signNames = receiveName.split(",");
        if("DEFAULT".equalsIgnoreCase(overwriteNames)) {
            receiveSign.addAll(Arrays.asList(signNames));
        } else {
            List<String> reRuns = Arrays.asList(overwriteNames.split(","));
            for (String signName : signNames) {
                if(reRuns.contains(signName)) {
                    receiveSign.add(signName);
                }
            }
            if(receiveSign.isEmpty()) {
                throw new RuntimeException("接收信号配置的重跑信号与原有配置信号配置交集为空");
            }
        }
        return receiveSign;
    }

    private String appendUri(String uri, String appId, String appToken) {
        String timeStampStr = generateNewTimeStamp();
        String nonce = generateNewNonce();
        String signature = DigestUtil.md5Hex(DigestUtil.md5Hex(appId + nonce + appId + timeStampStr).toLowerCase() + appToken);

        String newUri = UriComponentsBuilder.fromHttpUrl(uri).queryParam("app_id", appId)
                .queryParam("nonce", nonce)
                .queryParam("timestamp", timeStampStr)
                .queryParam("signature", signature)
                .queryParam("loginUser", appId)
                .build().toUriString();
        return newUri;
    }

    private String generateNewTimeStamp() {
        return (System.currentTimeMillis() / 1000) + "";
    }

    private String generateNewNonce() {
        return RandomUtil.randomNumbers(NONCE_LENGTH);
    }

}
