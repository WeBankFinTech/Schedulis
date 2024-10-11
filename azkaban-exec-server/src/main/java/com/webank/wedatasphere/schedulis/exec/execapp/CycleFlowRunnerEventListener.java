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

package com.webank.wedatasphere.schedulis.exec.execapp;

import static azkaban.executor.ExecutionOptions.FailureAction.CANCEL_ALL;
import static azkaban.executor.ExecutionOptions.FailureAction.FAILED_PAUSE;
import static azkaban.executor.ExecutionOptions.FailureAction.FINISH_ALL_POSSIBLE;
import static azkaban.executor.ExecutionOptions.FailureAction.FINISH_CURRENTLY_RUNNING;
import static java.util.stream.Collectors.joining;

import azkaban.event.Event;
import azkaban.event.EventListener;
import azkaban.execapp.FlowRunner;
import azkaban.executor.AlerterHolder;
import azkaban.executor.ExecutableFlow;
import azkaban.executor.ExecutionControllerUtils;
import azkaban.executor.ExecutionOptions;
import azkaban.executor.ExecutorManagerException;
import azkaban.executor.Status;
import azkaban.sla.SlaOption;
import azkaban.spi.EventType;
import azkaban.utils.JSONUtils;
import azkaban.utils.Pair;
import azkaban.utils.Props;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import com.webank.wedatasphere.schedulis.common.executor.ExecutionCycle;
import com.webank.wedatasphere.schedulis.common.executor.ExecutionCycleDao;
import com.webank.wedatasphere.schedulis.common.function.CheckedSupplier;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Stream;
import javax.inject.Inject;
import okhttp3.Call;
import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;
import org.apache.commons.collections.MapUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class CycleFlowRunnerEventListener implements EventListener {

    private static final Logger logger = LoggerFactory.getLogger(CycleFlowRunnerEventListener.class);

    private static final String AZKABAN_WEBSERVER_URL = "azkaban.webserver.url";
    private static final String EXECUTE_CYCLE_INTERVAL = "execute.cycle.interval";
    private static final String EXECUTE_CYCLE_RETRY_TIMES = "execute.cycle.retry.times";

    private ExecutionCycleDao executionCycleDao;
    private Props props;
    private AlerterHolder alerterHolder;

    @Inject
    public CycleFlowRunnerEventListener(ExecutionCycleDao executionCycleDao, Props props, AlerterHolder alerterHolder) {
        this.executionCycleDao = executionCycleDao;
        this.props = props;
        this.alerterHolder = alerterHolder;
    }

    @Override
    public synchronized void handleEvent(Event event) {
        if (event.getType() == EventType.FLOW_FINISHED) {
            FlowRunner flowRunner = (FlowRunner) event.getRunner();
            ExecutableFlow flow = flowRunner.getExecutableFlow();
            int flowType = flow.getFlowType();
            Map<String, String> cycleOption = flow.getCycleOption();
            String cycleErrorOption = cycleOption.get("cycleErrorOption");
            if (flowType == 4 && cycleErrorOption != null) {
                ExecutionCycle cycleFlow = getExecutionCycle(flow);
                if (cycleFlow != null && cycleFlow.getStatus() == Status.RUNNING) {
                    Status status = event.getData().getStatus();
                    if (Status.isStatusSucceeded(status)) {
                        submitExecutableFlow(flow, cycleFlow);
                    } else if (Status.isStatusFailed(status) && cycleErrorOption.equals("errorContinue")) {
                        submitExecutableFlow(flow, cycleFlow);
                    } else if (Status.isStatusFailed(status) && cycleErrorOption.equals("errorStop")) {
                        cycleFlow.setStatus(Status.FAILED);
                        cycleFlow.setEndTime(System.currentTimeMillis());
                        updateCycleFlow(() -> executionCycleDao.updateCycleFlow(cycleFlow));
                        ExecutionControllerUtils.alertOnCycleFlowInterrupt(flow, cycleFlow, alerterHolder);
                    }
                }
            }
        }
    }

    private void submitExecutableFlow(ExecutableFlow flow, ExecutionCycle cycleFlow) {
        try {
            int retryTimes = props.getInt(EXECUTE_CYCLE_RETRY_TIMES, 5);
            Pair<Boolean, Integer> pair = submitExecutableFlow(flow, retryTimes);
            boolean submitFlowResult = pair.getFirst();
            if (submitFlowResult) {
                int executionId = flow.getExecutionId();
                int executionIdNew = pair.getSecond();
                updateCycleFlow(() -> executionCycleDao.updateCycleFlow(executionId, executionIdNew));
            } else {
                logger.error("submit executable flow error");
                cycleFlow.setStatus(Status.FAILED);
                cycleFlow.setEndTime(System.currentTimeMillis());
                updateCycleFlow(() -> executionCycleDao.updateCycleFlow(cycleFlow));
                ExecutionControllerUtils.alertOnCycleFlowInterrupt(flow, cycleFlow, alerterHolder);
            }
        } catch (Exception e) {
            logger.error("submit executable flow error", e);
            cycleFlow.setStatus(Status.FAILED);
            cycleFlow.setEndTime(System.currentTimeMillis());
            updateCycleFlow(() -> executionCycleDao.updateCycleFlow(cycleFlow));
            ExecutionControllerUtils.alertOnCycleFlowInterrupt(flow, cycleFlow, alerterHolder);
        }
    }

    private ExecutionCycle getExecutionCycle(ExecutableFlow flow) {
        try {
            return executionCycleDao.getExecutionCycleFlow(String.valueOf(flow.getProjectId()), flow.getFlowId());
        } catch (ExecutorManagerException e) {
            return null;
        }
    }

    private void updateCycleFlow(CheckedSupplier<Integer, ExecutorManagerException> supplier) {
        try {
            supplier.get();
        } catch (ExecutorManagerException e) {
            logger.error("updateCycleFlow error", e);
        }
    }

    private Pair<Boolean, Integer> submitExecutableFlow(ExecutableFlow flow, int retryTimes) throws IOException {
        Supplier<Pair<Boolean, Integer>> submitExecutableFlow = () -> {
            try {
                int interval = props.getInt(EXECUTE_CYCLE_INTERVAL, 5);
                sleep(interval * 1000);
                logger.info(String.format("submit cycle: %s : %d flow start ...", flow.getFlowId(), flow.getExecutionId()));
                Pair<Boolean, Integer> result = submitExecutableFlow(flow);
                logger.info(String.format("submit cycle: %s : %d flow end", flow.getFlowId(), flow.getExecutionId()));
                return result;
            } catch (Exception e) {
                logger.error("submit cycle flow error", e);
                return new Pair<>(false, null);
            }
        };
        return Stream.generate(submitExecutableFlow)
                .limit(retryTimes)
                .filter(Pair::getFirst)
                .findFirst()
                .orElseThrow(() -> new IOException("submit cycle flow error"));

    }

    private Pair<Boolean, Integer> submitExecutableFlow(ExecutableFlow flow) throws IOException {
        OkHttpClient okHttpClient = new OkHttpClient.Builder()
                .connectTimeout(10, TimeUnit.SECONDS)
                .writeTimeout(20, TimeUnit.SECONDS)
                .readTimeout(20, TimeUnit.SECONDS)
                .build();
        HttpUrl url = flow2HttpUrl(flow);
        Request request = new Request.Builder()
                .url(url)
                .get()
                .build();
        Call call = okHttpClient.newCall(request);
        Response response = call.execute();
        if (response.code() != 200) {
            logger.info("submit cycle flow http response code: " + response.code());
            return new Pair<>(false, null);
        } else {
            Map<String, Object> submitFlowResult = response2Map(response);
            String code = (String) submitFlowResult.get("code");
            String message = (String) submitFlowResult.get("message");
            logger.info("submit cycle flow map identity code: " + code);
            logger.info("submit cycle flow map identity message: " + message);
            return "200".equals(code)
                    ? new Pair<>(true, ((Double) submitFlowResult.get("execid")).intValue())
                    : new Pair<>(false, null) ;
        }
    }

    private HttpUrl flow2HttpUrl(ExecutableFlow flow) {
        Map<String, String> params = new HashMap<>();
        params.put("cycleFlowSubmitUser", flow.getSubmitUser());

        params.put("projectId", String.valueOf(flow.getProjectId()));
        params.put("project", flow.getProjectName());
        params.put("ajax", "executeFlowCycleFromExecutor");
        params.put("flow", flow.getFlowId());

        ExecutionOptions executionOptions = flow.getExecutionOptions();
        String disabledJobs = JSONUtils.toJSON(executionOptions.getDisabledJobs());
        params.put("disabled", disabledJobs);

        String failureEmailsOverride = String.valueOf(executionOptions.isFailureEmailsOverridden());
        params.put("failureEmailsOverride", failureEmailsOverride);
        String successEmailsOverride = String.valueOf(executionOptions.isSuccessEmailsOverridden());
        params.put("successEmailsOverride", successEmailsOverride);

        String failureAction = parseFailureAction(executionOptions.getFailureAction());
        params.put("failureAction", failureAction);

        String failureEmails = String.join(",", executionOptions.getFailureEmails());
        params.put("failureEmails", failureEmails);
        String successEmails = String.join(",", executionOptions.getSuccessEmails());
        params.put("successEmails", successEmails);

        String notifyFailureFirst = String.valueOf(executionOptions.getNotifyOnFirstFailure());
        params.put("notifyFailureFirst", notifyFailureFirst);
        String notifyFailureLast = String.valueOf(executionOptions.getNotifyOnLastFailure());
        params.put("notifyFailureLast", notifyFailureLast);

        Map<String, String> flowParams = parseFlowParams(executionOptions.getFlowParameters());
        params.putAll(flowParams);

        Map<String, Object> otherOption = flow.getOtherOption();
        Map<String, String> jobFailedParams = parseJobFailedParams(otherOption, flow.getFlowId());
        params.putAll(jobFailedParams);

        Map<String, String> jobSkipParams = parseJobSkipParams(otherOption);
        params.putAll(jobSkipParams);

//        Map<String, String> flowRetryAlertParams = parseFlowRetryAlertParams(otherOption);
//        params.putAll(flowRetryAlertParams);

        params.put("failureAlertLevel", (String) otherOption.get("failureAlertLevel"));
        params.put("successAlertLevel", (String) otherOption.get("successAlertLevel"));

        params.put("useTimeoutSetting", String.valueOf(flow.getSlaOptions().size() > 0));

        List<SlaOption> slaOptions = flow.getSlaOptions();
        String slaEmails = parseSlaEmails(slaOptions);
        params.put("slaEmails", slaEmails);

        Map<String, String> settingParams = parseSettingParams(slaOptions);
        params.putAll(settingParams);

        Map<String, String> concurrentParams = parseConcurrentParams(executionOptions);
        params.putAll(concurrentParams);

        params.put("cycleErrorOption", flow.getCycleOption().get("cycleErrorOption"));
        params.put("cycleFlowInterruptAlertLevel", flow.getCycleOption().get("cycleFlowInterruptAlertLevel"));
        params.put("cycleFlowInterruptEmails", flow.getCycleOption().get("cycleFlowInterruptEmails"));

        String urlSuffix = params.keySet().stream()
                .map(key -> key + "=" + params.get(key))
                .collect(joining("&"));
        String webServerUrl = props.getString(AZKABAN_WEBSERVER_URL);
        String url = webServerUrl + "/executor?" + urlSuffix;
        logger.info("cycle flow url: " + url);
        return HttpUrl.parse(url);
    }

    private String parseFailureAction(ExecutionOptions.FailureAction failureAction) {
        String failureActionStr = "finishCurrent";
        if (failureAction == FINISH_CURRENTLY_RUNNING) {
            failureActionStr = "finishCurrent";
        } else if (failureAction == CANCEL_ALL) {
            failureActionStr = "cancelImmediately";
        } else if (failureAction == FINISH_ALL_POSSIBLE) {
            failureActionStr = "finishPossible";
        } else if (failureAction == FAILED_PAUSE) {
            failureActionStr = "failedPause";
        }
        return failureActionStr;
    }

    private Map<String, String> parseFlowParams(Map<String, String> flowParams) {
        if (MapUtils.isEmpty(flowParams)) return new HashMap<>();
        Map<String, String> newFlowParams = new HashMap<>();
        flowParams.forEach((k, v) -> {
            String key = "flowOverride[" + k + "]";
            newFlowParams.put(key, v);
        });
        return newFlowParams;
    }

    private Map<String, String> parseJobFailedParams(Map<String, Object> otherOption, String flowId) {
        Object jobFailedObj = otherOption.get("jobFailedRetryOptions");
        if (jobFailedObj == null) return new HashMap<>();
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> jobFailedList = (List<Map<String, Object>>) jobFailedObj;
        Map<String, String> jobFailedParams = new HashMap<>();
        for (int i = 0; i < jobFailedList.size(); i++) {
            String key = "jobFailedRetryOptions[" + i + "]";
            String value;
            Map<String, Object> jobFailedOption = jobFailedList.get(i);
            if (jobFailedOption.containsKey("job.failed.retry.interval")) {
                String jobName = "all_jobs " + flowId;
                String interval = (String) jobFailedOption.get("job.failed.retry.interval");
                String count = (String) jobFailedOption.get("job.failed.retry.count");
                value = jobName + "," + interval + "," + count;
            } else {
                String jobName = (String) jobFailedOption.get("jobName");
                String interval = (String) jobFailedOption.get("interval");
                String count = (String) jobFailedOption.get("count");
                value = jobName + "," + interval + "," + count;
            }
            jobFailedParams.put(key, value);
        }
        return jobFailedParams;
    }

    private Map<String, String> parseJobSkipParams(Map<String, Object> otherOption) {
        Object jobSkipObj = otherOption.get("jobSkipFailedOptions");
        if (jobSkipObj == null) return new HashMap<>();
        @SuppressWarnings("unchecked")
        List<String> jobSkipList = (List<String>) jobSkipObj;
        Map<String, String> jobSkipParams = new HashMap<>();
        for (int i = 0; i < jobSkipList.size(); i++) {
            String key = "jobSkipFailedOptions[" + i + "]";
            String value = jobSkipList.get(i);
            jobSkipParams.put(key, value);
        }
        return jobSkipParams;
    }

    private Map<String, String> parseFlowRetryAlertParams(Map<String, Object> otherOption) {
        @SuppressWarnings("unchecked")
        Map<String, Object> flowRetryAlertChecked = (Map<String, Object>) otherOption.get("flowRetryAlertOption");
        Function<Map<String, Object>, Map<String, String>> getFlowRetryAlertParams = map -> {
            Map<String, String> flowRetryAlertParams = new HashMap<>();
            map.forEach((k, v) -> flowRetryAlertParams.put(k, v.toString()));
            return flowRetryAlertParams;
        };
        return null == flowRetryAlertChecked
                ? new HashMap<>()
                : getFlowRetryAlertParams.apply(flowRetryAlertChecked);
    }

    private String parseSlaEmails(List<SlaOption> slaOptions) {
        if (slaOptions.isEmpty()) return "";
        @SuppressWarnings("unchecked")
        List<String> slaEmails = (List<String>) slaOptions.get(0).getInfo().get(SlaOption.INFO_EMAIL_LIST);
        return String.join(",", slaEmails);
    }

    private Map<String, String> parseSettingParams(List<SlaOption> slaOptions) {
        if (slaOptions.isEmpty()) return new HashMap<>();
        SlaOption slaOption = slaOptions.get(0);
        String id = (String) slaOption.getInfo().getOrDefault(SlaOption.INFO_JOB_NAME, "");
        String rule = slaOption.getType().equals(SlaOption.TYPE_FLOW_SUCCEED)? "SUCCESS": "FINISH";
        String duration = (String) slaOption.getInfo().get(SlaOption.INFO_TIME_SET);
        String level = slaOption.getLevel();
        String emailAction = (String) slaOption.getInfo().get(SlaOption.INFO_EMAIL_ACTION_SET);
        String killAction = (String) slaOption.getInfo().get(SlaOption.INFO_KILL_FLOW_ACTION_SET);
        String key = "settings[0]";
        String value =  String.join(",", id, rule, duration, level, emailAction, killAction);
        Map<String, String> settingParams = new HashMap<>();
        settingParams.put(key, value);
        return settingParams;
    }

    private Map<String, String> parseConcurrentParams(ExecutionOptions executionOptions) {
        Map<String, String> concurrentParams = new HashMap<>();
        String concurrentOption = executionOptions.getConcurrentOption();
        if ("pipeline".equals(concurrentOption)){
            concurrentParams.put("pipelineLevel", String.valueOf(executionOptions.getPipelineLevel()));
        } else if ("queue".equals(concurrentOption)) {
            concurrentParams.put("queueLevel", String.valueOf(executionOptions.getQueueLevel()));
        }
        concurrentParams.put("concurrentOption", concurrentOption);
        return concurrentParams;
    }

    private Map<String, Object> response2Map(Response response) throws IOException {
        ResponseBody body = response.body();
        if (body == null) return null;
        String bodyString = body.string();
        GsonBuilder gb = new GsonBuilder();
        Gson g = gb.create();
        return g.fromJson(bodyString, new TypeToken<Map<String, Object>>(){}.getType());
    }

    private void sleep(int interval) {
        try {
            Thread.sleep(interval);
        } catch (InterruptedException e) {
            logger.info("cycle flow sleep is interrupted");
        }
    }
}
