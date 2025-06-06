/*
 * Copyright 2019 WeBank
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

package com.webank.wedatasphere.dss.plugins.azkaban.linkis.jobtype;


import azkaban.jobExecutor.AbstractJob;
import azkaban.utils.JobUtils;
import azkaban.utils.Props;
import com.webank.wedatasphere.dss.linkis.node.execution.conf.LinkisJobExecutionConfiguration;
import com.webank.wedatasphere.dss.linkis.node.execution.execution.LinkisNodeExecution;
import com.webank.wedatasphere.dss.linkis.node.execution.execution.impl.LinkisNodeExecutionImpl;
import com.webank.wedatasphere.dss.linkis.node.execution.job.Job;
import com.webank.wedatasphere.dss.linkis.node.execution.job.JobTypeEnum;
import com.webank.wedatasphere.dss.linkis.node.execution.job.LinkisJob;
import com.webank.wedatasphere.dss.linkis.node.execution.listener.LinkisExecutionListener;
import com.webank.wedatasphere.dss.plugins.azkaban.linkis.jobtype.conf.LinkisJobTypeConf;
import com.webank.wedatasphere.dss.plugins.azkaban.linkis.jobtype.job.AzkanbanBuilder;
import com.webank.wedatasphere.dss.plugins.azkaban.linkis.jobtype.job.JobBuilder;
import com.webank.wedatasphere.dss.plugins.azkaban.linkis.jobtype.log.AzkabanJobLog;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import org.apache.commons.lang.StringUtils;
import org.apache.linkis.protocol.utils.TaskUtils;
import org.slf4j.Logger;


public class AzkabanDssJobType extends AbstractJob {



    private static final String SENSITIVE_JOB_PROP_NAME_SUFFIX = "_X";
    private static final String SENSITIVE_JOB_PROP_VALUE_PLACEHOLDER = "[MASKED]";
    private static final String JOB_DUMP_PROPERTIES_IN_LOG = "job.dump.properties";



    private final Logger log;

    protected volatile Props jobProps;

    protected volatile Props sysProps;

    protected volatile Map<String, String> jobPropsMap;

    private final String type;

    private Job job;

    private boolean isCanceled = false;




    public AzkabanDssJobType(String jobId, Props sysProps, Props jobProps, Logger log) {


        super(jobId, log);

        this.jobProps = jobProps;

        this.sysProps = sysProps;

        this.jobPropsMap = this.jobProps.getMapByPrefix("");

        this.log = log;
        this.type = jobProps.getString(JOB_TYPE, LinkisJobExecutionConfiguration.JOB_DEFAULT_TYPE.getValue(this.jobPropsMap));
        if(!LinkisJobExecutionConfiguration.JOB_DEFAULT_TYPE.getValue(this.jobPropsMap).equalsIgnoreCase(this.type) ){
            throw new RuntimeException("This job(" + this.type + " )is not linkis type");
        }
    }

    private LinkisNodeExecution getLinkisNodeExecution() {
        LinkisNodeExecution execution = null;
        if (isInlineCode(job.getJobProps())) {
            execution = Execution.getLinkisNodeExecution();
        } else {
            execution = LinkisNodeExecutionImpl.getLinkisNodeExecution();
        }
        return execution;
    }


    @Override
    public void run() throws Exception {

        info("Start to execute job");
        logJobProperties();
        String runDate = getRunDate();
        if (StringUtils.isNotBlank(runDate)) {
            this.jobPropsMap.put("run_date", runDate);
        }
        String runTodayH = getRunTodayh(false);
        if (StringUtils.isNotBlank(runTodayH)) {
            this.jobPropsMap.put("run_today_h", runTodayH);
            this.jobPropsMap.put("run_today_hour", runTodayH);
        }
        this.job = JobBuilder.getAzkanbanBuilder().setJobProps(this.jobPropsMap).build();
        this.job.setLogObj(new AzkabanJobLog(this));
        if(JobTypeEnum.EmptyJob == ((LinkisJob)this.job).getJobType()){
            warn("This node is empty type");
            return;
        }
        String jobSourceTags = JobUtils.buildJobSourceTags(this.jobProps, log);
        job.getRuntimeParams().put(LinkisJobTypeConf.JOB_SOURCE_TAGS_KEYS, jobSourceTags);
        Map<String, Object> starMap = new HashMap<>();
        starMap.put(LinkisJobTypeConf.JOB_SOURCE_TAGS_KEYS, jobSourceTags);
        TaskUtils.addStartupMap(job.getParams(), starMap);
        info("runtimeMap is " + job.getRuntimeParams());

        LinkisNodeExecution execution = getLinkisNodeExecution();
        execution.runJob(this.job);

        try {
            execution.waitForComplete(this.job);
        } catch (Exception e) {
            warn("Failed to execute job", e);
            throw e;
        }
        try {
            String endLog = execution.getLog(this.job);
            if (endLog != null) {
                info(endLog);
            }
        } catch (Throwable e){
            info("Failed to get log", e);
        }

        LinkisExecutionListener listener = (LinkisExecutionListener) execution;
        listener.onStatusChanged(null, execution.getState(this.job), this.job);
        int resultSize =  0;
        try{
            resultSize = execution.getResultSize(this.job);
            for (int i = 0; i < resultSize; i++) {
                String result = execution.getResult(this.job, i, LinkisJobExecutionConfiguration.RESULT_PRINT_SIZE.getValue(this.jobPropsMap));
                if (result.length() > LinkisJobTypeConf.LOG_MAX_RESULTSIZE.getValue()) {
                    result = result.substring(0, LinkisJobTypeConf.LOG_MAX_RESULTSIZE.getValue());
                }
                info("The content of the " + (i + 1) + "th resultset is :" + result);
            }
        }catch(final Throwable t){
            error("failed to get result，maybe resource is empty");
        }

        info("Finished to execute job");
    }

    @Override
    public void cancel() throws Exception {
        //super.cancel();
        getLinkisNodeExecution().cancel(this.job);
        isCanceled = true;
        warn("This job has been canceled");
    }

    @Override
    public boolean isCanceled() {
        return isCanceled;
    }

    @Override
    public double getProgress() throws Exception {
        return getLinkisNodeExecution().getProgress(this.job);
    }

    /**
     * prints the current Job props to the Job log.
     */
    private void logJobProperties() {
        if (this.jobProps != null &&
                this.jobProps.getBoolean(JOB_DUMP_PROPERTIES_IN_LOG, true)) {
            try {
                this.info("******   Job properties   ******");
                this.info(String.format("- Note : value is masked if property name ends with '%s'.",
                        SENSITIVE_JOB_PROP_NAME_SUFFIX));
                for (final Map.Entry<String, String> entry : this.jobPropsMap.entrySet()) {
                    final String key = entry.getKey();
                    final String value = key.endsWith(SENSITIVE_JOB_PROP_NAME_SUFFIX) ?
                            SENSITIVE_JOB_PROP_VALUE_PLACEHOLDER :
                            entry.getValue();
                    this.info(String.format("%s=%s", key, value));
                }
                this.info("****** End Job properties  ******");
            } catch (final Exception ex) {
                this.log.error("failed to log job properties ", ex);
            }
        }
    }

    private String getRunDate(){
        this.info("begin to get run date");
        if (this.jobProps != null &&
                this.jobProps.getBoolean(JOB_DUMP_PROPERTIES_IN_LOG, true)) {
            try {
                for (final Map.Entry<String, String> entry : this.jobPropsMap.entrySet()) {
                    final String key = entry.getKey();
                    final String value = key.endsWith(SENSITIVE_JOB_PROP_NAME_SUFFIX) ?
                            SENSITIVE_JOB_PROP_VALUE_PLACEHOLDER :
                            entry.getValue();
                    if ("azkaban.flow.start.timestamp".equals(key)){
                        this.info("run time is " + value);
                        String runDateNow = value.substring(0, 10).replaceAll("-", "");
                        this.info("run date now is " + runDateNow);
                        SimpleDateFormat simpleDateFormat = new SimpleDateFormat("yyyyMMdd");
                        try {
                            Date date = simpleDateFormat.parse(runDateNow);
                            //因为date已经当天的00:00:00 减掉12小时 就是昨天的时间
                            String runDate = simpleDateFormat.format(new Date(date.getTime() - 24 * 60 * 60 * 1000));
                            this.info("runDate is " + runDate);
                            return runDate;
                        } catch (ParseException e) {
                            this.log.error("failed to parse run date " + runDateNow, e);
                        }
                    }
                }
            } catch (final Exception ex) {
                this.log.error("failed to get run date ", ex);
            }
        }
        return null;
    }

    private String getRunTodayh(boolean stdFormat) {
        this.info("begin to get run_today_h");
        if (this.jobProps != null &&
                this.jobProps.getBoolean(JOB_DUMP_PROPERTIES_IN_LOG, true)) {
            try {
                for (final Map.Entry<String, String> entry : this.jobPropsMap.entrySet()) {
                    final String key = entry.getKey();
                    final String value = key.endsWith(SENSITIVE_JOB_PROP_NAME_SUFFIX) ?
                            SENSITIVE_JOB_PROP_VALUE_PLACEHOLDER :
                            entry.getValue();
                    if ("azkaban.flow.start.timestamp".equals(key)) {
                        this.info("run time is " + value);
                        String runTodayh = value.substring(0, 13).replaceAll("-", "").replaceAll("T", "");
                        this.info("run today h is " + runTodayh);
                        //for std
//                        SimpleDateFormat simpleDateFormat = new SimpleDateFormat("yyyy-MM-dd HH");
                        if(!stdFormat){
                            return runTodayh;
                        }
                    }
                }
            } catch (final Exception ex) {
                this.log.error("failed to get run_today_h ", ex);
            }
        }
        return null;
    }

    private boolean isInlineCode(Map<String, String> jobProps) {
        return Boolean.parseBoolean(jobProps.getOrDefault(AzkanbanBuilder.INLINE_CODE, "false"));
    }

}
