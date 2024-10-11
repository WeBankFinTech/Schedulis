package azkaban.jobtype;

import azkaban.jobExecutor.AbstractJob;
import azkaban.jobtype.util.EncryptUtil;
import azkaban.utils.Props;
import cn.hutool.core.util.RandomUtil;
import cn.hutool.crypto.digest.DigestUtil;
import cn.hutool.http.HttpRequest;
import cn.hutool.http.HttpResponse;
import cn.hutool.json.JSONUtil;
import org.slf4j.Logger;
import org.springframework.util.StringUtils;
import org.springframework.web.util.UriComponentsBuilder;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.temporal.TemporalAdjusters;
import java.util.*;

public class SignSend extends AbstractJob {

    private final int NONCE_LENGTH = 5;

    private final int MAX_RETRY = 5;

    private final long RETRY_INTERVAL = 60 * 1000L;
    private final Logger log;

    protected volatile Props jobProps;
    protected volatile Props sysProps;
    protected volatile Map<String, String> jobPropsMap;

    private boolean isCanceled = false;

    private Thread runThread;

    public SignSend(String jobId, Props sysProps, Props jobProps, Logger log) {
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
            String signName = jobProps.getString("sign_name");
            String customArgs = jobProps.getString("custom_args", "");
            String execId = jobProps.getString("exec_id");
            String sendUrl = jobProps.getString("send_url");
            String appId = jobProps.getString("app_id", "");
            String appToken = jobProps.getString("app_token", "");
            appToken = EncryptUtil.decryptPassword(appToken);
            String appHostPort = jobProps.getString("app_host_port");
            info("收到发送信号请求,batchDate:" + batchDate + ", signName:" + signName + ", customArgs:" + customArgs + ", execId:" + execId + ", sendUrl:" + sendUrl);
            Map<String, Object> params = new HashMap<>();
            params.put("id", UUID.randomUUID().toString().replaceAll("-", ""));
            params.put("signName", signName);
            params.put("execId", execId);
            params.put("customArgs", customArgs);
            LocalDateTime ldt = LocalDateTime.ofInstant(Instant.now(), ZoneId.systemDefault());
            DateTimeFormatter dtf = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
            params.put("sendTime", dtf.format(ldt));
            params.put("createTime", dtf.format(ldt));
            params.put("batchDate", batchDate);
            String requestUrl = "";
            if(StringUtils.isEmpty(appId)) {
                requestUrl = appHostPort + sendUrl;
            } else {
                requestUrl = appendUri(appHostPort + sendUrl, appId, appToken);
            }
            String response = retryPost(requestUrl, JSONUtil.toJsonStr(params));
            info("请求保存信号，url:" + requestUrl + ", params:" + JSONUtil.toJsonStr(params) + ", response:" + response);
            if(StringUtils.isEmpty(response)) {
                throw new RuntimeException("请求保存信号返回为空");
            }
            Map<String, Object> result = JSONUtil.toBean(response, Map.class);
            int code = (int) result.get("code");
            if(code != 200) {
                throw new RuntimeException("请求保存信号返回异常，code:" + code);
            }
        }
    }

    private String retryPost(String url, String jsonBody) throws InterruptedException {
        for (int i = 1; i <= MAX_RETRY; i ++) {
            if(isCanceled) {
                throw new RuntimeException("This job has been canceled");
            }
            HttpResponse httpResponse = null;
            try {
                try {
                    httpResponse = HttpRequest.post(url).body(jsonBody).execute();
                } catch (Exception e) {
                    warn("第" + i + "次请求保存信号失败," + e.getMessage());
                }
                if(null != httpResponse) {
                    String response = httpResponse.body();
                    info("第" + i + "次请求保存信号响应：" + response);
                    if(!StringUtils.isEmpty(response)) {
                        return response;
                    }
                }
            }finally {
                if(null != httpResponse) {
                    httpResponse.close();
                }
            }

            Thread.sleep(RETRY_INTERVAL);
        }
        return null;
    }

    @Override
    public void cancel() throws InterruptedException {
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
