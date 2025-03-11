package azkaban.utils;

import azkaban.Constants;
import azkaban.executor.ExecutionOptions;
import azkaban.flow.CommonJobProperties;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;

import java.util.Map;

public class JobUtils {

    public static String buildJobSourceTags(Props jobProps, Logger log) {
        StringBuilder jobTagContains = new StringBuilder();
        String jobId = jobProps.get(CommonJobProperties.JOB_ID);
        String execId = jobProps.get(Constants.FlowProperties.AZKABAN_FLOW_EXEC_ID);
        String busPathId = jobProps.get(Constants.JobProperties.JOB_BUS_PATH_KEY);
        String jobLevel = jobProps.get(Constants.JobProperties.JOB_IMPORTANCE_LEVEL_KEY);
        addTags(jobTagContains, jobId, "jobId", log);
        addTags(jobTagContains, execId, "execId", log);
        addTags(jobTagContains, busPathId, "busId", log);
        addTags(jobTagContains, jobLevel, "level", log);
        String jobTags = jobTagContains.toString();
        if (jobTags.endsWith(",")) {
            jobTags = jobTags.substring(0, jobTags.length()-1);
        }

        if (jobProps.containsKey(ExecutionOptions.JOB_ALTER_TIMEOUT) && jobProps.containsKey(ExecutionOptions.JOB_ALTER_RECEIVER)) {
            String timeout = jobProps.get(ExecutionOptions.JOB_ALTER_TIMEOUT);
            if (timeout.contains(",") || timeout.contains(" ")) {
                log.warn("timeout only support - split {}", timeout);
            }
            String receiver = jobProps.get(ExecutionOptions.JOB_ALTER_RECEIVER);
            if (receiver.contains(",") || receiver.contains(" ")) {
                log.warn("receiver only support - split {}", receiver);
            }
            int level = jobProps.getInt(ExecutionOptions.JOB_ALTER_LEVEL, 1);
            String timeoutTags = ",[" + ExecutionOptions.JOB_ALTER_TIMEOUT + "=" + timeout + ";"
                    + ExecutionOptions.JOB_ALTER_LEVEL + "=" + level + ";"
                    + ExecutionOptions.JOB_ALTER_RECEIVER + "=" + receiver + "]";
            if (timeoutTags.length() < 90) {
                jobTags += timeoutTags;
            } else {
                log.warn("timeoutTags length is too long {}", timeoutTags);
            }
        }
        return jobTags;
    }

    private static void addTags(StringBuilder jobTagContains, String tag, String keyPrefix, Logger log) {
        if (StringUtils.isNotBlank(tag) && StringUtils.isAsciiPrintable(tag) && !tag.contains(",")) {
            if (tag.length() < 80) {
                jobTagContains.append(keyPrefix).append("-").append(tag).append(",");
            } else {
                log.info("Tag len {} >  80", tag);
            }
        }
    }

    private static final String HIVE_OPTS = "HIVE_OPTS";

    private static final String SPARK_OPTS = "SPARK_SUBMIT_OPTS";
    public static void setHiveOpts(Map<String, String> envVars, Props jobProps, Logger log) {
        String jobSourceTags = buildJobSourceTags(jobProps, log);
        if (StringUtils.isNotBlank(jobSourceTags)) {
            String hiveOpts = System.getenv("HIVE_OPTS");
            if (StringUtils.isBlank(hiveOpts)) {
                hiveOpts = "";
            }
            envVars.put(HIVE_OPTS, hiveOpts + " --hiveconf mapreduce.job.tags=" + jobSourceTags);
        }
    }

    public static void setSparkOpts(Map<String, String> envVars, Props jobProps, Logger log) {
        String jobSourceTags = buildJobSourceTags(jobProps, log);
        if (StringUtils.isNotBlank(jobSourceTags)){
            String sparkSubmitOpts = System.getenv("SPARK_SUBMIT_OPTS");
            if (StringUtils.isBlank(sparkSubmitOpts)) {
                sparkSubmitOpts = "";
            }
            envVars.put(SPARK_OPTS, sparkSubmitOpts + " -Dspark.yarn.tags=" + jobSourceTags);
        }
    }

    public static boolean isBusPath(String busResLvl) {
        return "S".equalsIgnoreCase(busResLvl) || "A".equalsIgnoreCase(busResLvl);
    }


}
