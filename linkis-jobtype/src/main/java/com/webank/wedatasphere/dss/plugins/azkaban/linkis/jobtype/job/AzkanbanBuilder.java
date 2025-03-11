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

package com.webank.wedatasphere.dss.plugins.azkaban.linkis.jobtype.job;

import com.webank.wedatasphere.dss.linkis.node.execution.conf.LinkisJobExecutionConfiguration;
import com.webank.wedatasphere.dss.linkis.node.execution.entity.BMLResource;
import com.webank.wedatasphere.dss.linkis.node.execution.exception.LinkisJobExecutionErrorException;
import com.webank.wedatasphere.dss.linkis.node.execution.job.*;
import com.webank.wedatasphere.dss.linkis.node.execution.utils.LinkisJobExecutionUtils;
import com.webank.wedatasphere.dss.plugins.azkaban.linkis.jobtype.conf.LinkisJobTypeConf;
import org.apache.commons.lang.StringUtils;
import org.apache.linkis.common.utils.JsonUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;


public class AzkanbanBuilder extends Builder {

    private static final Logger LOGGER = LoggerFactory.getLogger(AzkanbanBuilder.class);

    private static final String RUN_DATE_KEY = "run_date";
    private static final String RUN_DATE_H_KEY = "run_today_h";
    private static final String RUN_DATE_HOUR_KEY = "run_today_hour";
    public static final String FLOW_DIR = "flow.dir";
    public static final String INLINE_CODE = "inline_code";
    public static final String RELATIVE_FILE = "relative_file";
    private Map<String, String> jobProps;

    public AzkanbanBuilder setJobProps(Map<String, String> jobProps) {
        this.jobProps = jobProps;
        return this;
    }

    @Override
    protected String getJobType() {
        return jobProps.getOrDefault(LinkisJobExecutionConfiguration.LINKIS_TYPE,
                LinkisJobExecutionConfiguration.LINKIS_DEFAULT_TYPE.getValue(jobProps));
    }

    @Override
    protected LinkisJob creatLinkisJob(boolean isLinkisType) {
        if (isLinkisType) {
            AzkabanCommonLinkisJob linkisJob = new AzkabanCommonLinkisJob();
            linkisJob.setJobProps(this.jobProps);
            return linkisJob;
        } else {
            AzkabanAppConnLinkisJob linkisJob = new AzkabanAppConnLinkisJob();
            linkisJob.setJobProps(this.jobProps);
            return linkisJob;
        }
    }

    @Override
    protected void fillJobInfo(Job job) {
        if (isInlineCode()) {
            job.setCode(this.fillInlineCode());
        } else {
        job.setCode(jobProps.get(LinkisJobTypeConf.COMMAND));
        }

        Map<String, Object> params = new HashMap<>();
        if (jobProps.containsKey("run_date")) {
            params.put("run_date", jobProps.get("run_date"));
        }
        job.setParams(params);

        Map<String, Object> runtimeMap = new HashMap<>();
        if (null != job.getRuntimeParams()) {
            runtimeMap = job.getRuntimeParams();
        }

        runtimeMap.put("nodeName", jobProps.get(LinkisJobTypeConf.JOB_ID));

        runtimeMap.put(LinkisJobTypeConf.DSS_LABELS_KEY, jobProps.get(LinkisJobTypeConf.DSS_LABELS_KEY));
        job.setRuntimeParams(runtimeMap);
    }

    private boolean isInlineCode() {
        return Boolean.parseBoolean(jobProps.getOrDefault(INLINE_CODE, "false"));
    }

    @Override
    protected String getContextID(Job job) {
        String contextID = jobProps.get(LinkisJobExecutionConfiguration.FLOW_CONTEXTID);
        //将部分老的工作流的BDAP_DEV标签替换为BDAP_PROD
        if (null != contextID) {
            contextID = contextID.replace(LinkisJobTypeConf.CONTEXT_ENV_DEV.getValue(), LinkisJobTypeConf.CONTEXT_ENV_PROD.getValue());
        }
        return contextID;
    }

    @Override
    protected void fillLinkisJobInfo(LinkisJob linkisJob) {
        linkisJob.setConfiguration(findConfiguration(LinkisJobExecutionConfiguration.NODE_CONF_PREFIX));
        Map<String, Object> variables = findVariables(LinkisJobExecutionConfiguration.FLOW_VARIABLE_PREFIX);
        // 只有工作流参数中没有设置,我们才会去进行替换
        // 改为不管工作流是否设置，在 Schedulis 这边都需要统一使用 Schedulis 设置的 run_date和un_date_h,防止出现批量调度的误导作用
        setNewRunDateVariable(variables, RUN_DATE_KEY);
        setNewRunDateVariable(variables, RUN_DATE_H_KEY);
        setNewRunDateVariable(variables, RUN_DATE_HOUR_KEY);
        linkisJob.setVariables(variables);
        linkisJob.setSource(getSource());
    }

    private String fillInlineCode() {
        if (!jobProps.containsKey(FLOW_DIR)) {
            return null;
        }

        String path = jobProps.get(FLOW_DIR);
        String fileName = jobProps.get(RELATIVE_FILE);
        Path file = Paths.get(path, fileName);
        try {
            List<String> lines = Files.readAllLines(file);
            StringBuilder sbuilder = new StringBuilder();
            for (String line : lines) {
                sbuilder.append(line);
                sbuilder.append(System.lineSeparator());
            }

            String code = sbuilder.toString();
            if (StringUtils.isBlank(code)) {
                throw new IllegalArgumentException("job code empty");
            }
            return code;
        } catch (IOException e) {
            LOGGER.error("read file error " + file, e);
            throw new RuntimeException("read script file error");
        }
    }

    private void setNewRunDateVariable(Map<String, Object> variables, String replaceVar) {
        if (jobProps.containsKey(replaceVar)) {
            variables.put(replaceVar, jobProps.get(replaceVar));
            LOGGER.info("Put {} to variables,value: {}", replaceVar, jobProps.get(replaceVar));
        }
    }

    @Override
    protected void fillCommonLinkisJobInfo(CommonLinkisJob linkisAppConnJob) {
        linkisAppConnJob.setJobResourceList(LinkisJobExecutionUtils.getResourceListByJson(jobProps.get("resources")));

        String projectResourceName = LinkisJobExecutionConfiguration.PROJECT_PREFIX + "."
                + jobProps.get(LinkisJobTypeConf.PROJECT_NAME) + LinkisJobExecutionConfiguration.RESOURCES_NAME;
        linkisAppConnJob.setProjectResourceList(LinkisJobExecutionUtils.getResourceListByJson(jobProps.get(projectResourceName)));

        linkisAppConnJob.setFlowNameAndResources(findFLowNameAndResources());
    }


    private Map<String, String> getSource() {
        Map<String, String> source = new HashMap<>();
        source.put("projectName", jobProps.get(LinkisJobTypeConf.PROJECT_NAME));
        source.put("flowName", jobProps.get(LinkisJobTypeConf.FLOW_NAME));
        source.put("nodeName", jobProps.get(LinkisJobTypeConf.JOB_ID));
        return source;
    }

    /**
     * Looking for custom variables through the corresponding prefix
     * For example, flow.variable.a=test returns map{a->test}
     *
     * @param prefix
     * @return
     */
    private Map<String, Object> findVariables(String prefix) {
        Map<String, Object> map = new HashMap<>();
        Iterator<String> keyIterator = jobProps.keySet().iterator();
        while (keyIterator.hasNext()) {
            String next = keyIterator.next();
            if (next.startsWith(prefix)) {
                map.put(next.substring(prefix.length()), jobProps.get(next));
            }
        }
        return map;
    }

    /**
     * Looking for configuration through the corresponding prefix includes startup,runtime,special etc
     *
     * @param prefix
     * @return
     */
    private Map<String, Object> findConfiguration(String prefix) {
        Map<String, Object> configuration = new HashMap<>();
        Iterator<String> keyIterator = jobProps.keySet().iterator();
        while (keyIterator.hasNext()) {
            String next = keyIterator.next();
            if (next.startsWith(prefix)) {
                String confTypeAndName = next.substring(prefix.length());
                if (confTypeAndName.startsWith(LinkisJobExecutionConfiguration.CONF_STARTUP)) {
                    putConf(configuration, LinkisJobExecutionConfiguration.CONF_STARTUP, jobProps.get(next), confTypeAndName);
                } else if (confTypeAndName.startsWith(LinkisJobExecutionConfiguration.CONF_RUNTIME)) {
                    putConf(configuration, LinkisJobExecutionConfiguration.CONF_RUNTIME, jobProps.get(next), confTypeAndName);
                } else if (confTypeAndName.startsWith(LinkisJobExecutionConfiguration.CONF_SPECIAL)) {
                    putConf(configuration, LinkisJobExecutionConfiguration.CONF_SPECIAL, jobProps.get(next), confTypeAndName);
                }
            }
        }
        return configuration;
    }

    private void putConf(Map<String, Object> configuration, String key, String value, String confTypeAndName) {
        if (configuration.get(key) == null) {
            Map<String, String> startup = new HashMap<>();
            startup.put(confTypeAndName.substring(key.length() + 1), value);
            configuration.put(key, startup);
        } else {
            Map<String, String> startup = (Map<String, String>) configuration.get(key);
            startup.put(confTypeAndName.substring(key.length() + 1), value);
        }
    }


    private Map<String, List<BMLResource>> findFLowNameAndResources() {
        Map<String, List<BMLResource>> flowNameAndResources = new HashMap<>();
        Iterator<String> iterator = jobProps.keySet().iterator();
        while (iterator.hasNext()) {
            String next = iterator.next();
            if (next.endsWith("resources") && next.startsWith("flow")) {
                flowNameAndResources.put(next, LinkisJobExecutionUtils.getResourceListByJson(jobProps.get(next)));
            }
        }
        return flowNameAndResources;
    }

    @Override
    public Job build() throws Exception {
        if (!isInlineCode()) {
            return super.build();
        }

        // 下面的代码与super.build对比只是少了ContextID的处理.linkis并不需要这个参数.
        LinkisJob job = null;
        String jobType = getJobType();
        String[] jobTypeSplit = jobType.split("\\.");
        if (jobTypeSplit.length < 3) {
            throw new LinkisJobExecutionErrorException(90100, "This is not Linkis job type, this jobtype is " + jobType);
        }
        String engineType = jobTypeSplit[1];
        String runType = StringUtils.substringAfterLast(jobType, jobTypeSplit[0] + "." + jobTypeSplit[1] + ".");

        if (LinkisJobExecutionConfiguration.LINKIS_CONTROL_EMPTY_NODE.equalsIgnoreCase(jobType)) {
            job = new AbstractCommonLinkisJob() {
                @Override
                public String getSubmitUser() {
                    return null;
                }

                @Override
                public String getUser() {
                    return null;
                }

                @Override
                public String getJobName() {
                    return null;
                }
            };

            job.setJobType(JobTypeEnum.EmptyJob);
            return job;
        }
        if (LinkisJobExecutionUtils.isCommonAppConnJob(engineType)) {
            job = creatLinkisJob(false);
            job.setJobType(JobTypeEnum.CommonJob);
        } else {
            job = creatLinkisJob(true);
            job.setJobType(JobTypeEnum.CommonJob);
            fillCommonLinkisJobInfo((CommonLinkisJob) job);
        }

        job.setEngineType(engineType);
        job.setRunType(runType);
        fillJobInfo(job);
        fillLinkisJobInfo(job);

        return job;
    }

}
