package azkaban.utils;

import azkaban.Constants;
import azkaban.flow.CommonJobProperties;
import org.apache.commons.lang3.StringUtils;

import java.util.Map;

public class JobUtils {

    public static String buildJobSourceTags(Props jobProps) {
        StringBuilder jobTagContains = new StringBuilder();
        String jobId = jobProps.get(CommonJobProperties.JOB_ID);
        String execId = jobProps.get(Constants.FlowProperties.AZKABAN_FLOW_EXEC_ID);
        String busPathId = jobProps.get(Constants.JobProperties.JOB_BUS_PATH_KEY);
        String jobLevel = jobProps.get(Constants.JobProperties.JOB_IMPORTANCE_LEVEL_KEY);
        addTags(jobTagContains, jobId, "jobId");
        addTags(jobTagContains, execId, "execId");
        addTags(jobTagContains, busPathId, "busId");
        addTags(jobTagContains, jobLevel, "level");
        String jobTags = jobTagContains.toString();
        if (jobTags.endsWith(",")) {
            jobTags = jobTags.substring(0, jobTags.length()-1);
        }
        return jobTags;
    }

    private static void addTags(StringBuilder jobTagContains, String tag, String keyPrefix) {
        if (StringUtils.isNotBlank(tag) && StringUtils.isAsciiPrintable(tag)) {
            if (tag.length() < 80) {
                jobTagContains.append(keyPrefix).append("-").append(tag).append(",");
            }
        }
    }


    private static final String HIVE_OPTS = "HIVE_OPTS";

    private static final String SPARK_OPTS = "SPARK_SUBMIT_OPTS";
    public static void setHiveOpts(Map<String, String> envVars, Props jobProps) {
        String jobSourceTags = buildJobSourceTags(jobProps);
        if (StringUtils.isNotBlank(jobSourceTags)) {
            String hiveOpts = System.getenv("HIVE_OPTS");
            if (StringUtils.isBlank(hiveOpts)) {
                hiveOpts = "";
            }
            envVars.put(HIVE_OPTS, hiveOpts + " --hiveconf mapreduce.job.tags=" + jobSourceTags);
        }
    }

    public static void setSparkOpts(Map<String, String> envVars, Props jobProps) {
        String jobSourceTags = buildJobSourceTags(jobProps);
        if (StringUtils.isNotBlank(jobSourceTags)){
            String sparkSubmitOpts = System.getenv("SPARK_SUBMIT_OPTS");
            if (StringUtils.isBlank(sparkSubmitOpts)) {
                sparkSubmitOpts = "";
            }
            envVars.put(SPARK_OPTS, sparkSubmitOpts + " -Dspark.yarn.tags=" + jobSourceTags);
        }
    }

}
