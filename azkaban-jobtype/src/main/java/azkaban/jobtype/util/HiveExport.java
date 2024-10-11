package azkaban.jobtype.util;


import azkaban.jobtype.commons.LogGobbler;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.hadoop.util.Shell;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.InputStreamReader;
import java.lang.management.ManagementFactory;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

/**
 * Created by kirkzhou on 9/10/18.
 */
public class HiveExport {

  private final static String DATABASE_NAME = "database.name";
  private final static String TABEL_NAME = "table.name";
  private final static String PARTITION_NAME = "partition.name";
  private final static String PARTITION_VALUE = "partition.value";
  private final static String EXPORT_FILE_PATH = "export.file.path";
  private final static String EXPORT_FILE_NAME = "export.file.name";

  private final static String DRIVER_MEMORY = "driver.memory";
  private final static String EXECUTOR_MEMORY = "executor.memory";
  private final static String EXECUTOR_CORES = "executor.cores";

  private final static String EXECUTE_JAR = "execute.jar";
  private final static String DEFAULT_JAR = "/appcom/Install/AzkabanInstall/HiveExport-1.0.0-SNAPSHOT.jar";
  private final static String SPARK_HOME = "spark.home";
  private final static String DEFAULT_SPARK_HOME = "/appcom/Install/spark-cmd";
  private final static String MAIN_CLASS = "main.class";
  private final static String DEFAULT_MAIN_CLASS = "com.webank.bdp.hiveexport.HiveExport";
  private final static String MASTER = "master";
  private final static String DEPLOY_MODE = "deploy.mode";
  private final static String DEFAULT_MASTER = "yarn";
  private final static String DEFAULT_DEPLOY_MODE = "client";
  private final static String QUEUE = "queue";


  private Properties p;

  private static final Logger logger = LoggerFactory.getLogger(HiveExport.class);

  public HiveExport(Properties p) {
    this.p = p;
  }

  public HiveExport(String jobName, Properties p) {
    this.p = p;
  }

  public void run() throws Exception{
    if(!checkArgs(p)){
      throw new IllegalArgumentException("job's args is illegal.");
    }
    logger.info("props :" + p.toString());
    getPid();
    try {
      exeCmd(genCommand(p));

    }catch (Exception e){
      logger.error("exec spark-submit command failed, " + e);
      throw e;
    }


  }

  public void cancel() throws InterruptedException {
    logger.info("this flow was killed.");
    throw new RuntimeException("cancel the flow.");
    //_job.119395.child-job02.log
//    String azExecId = p.getProperty(CommonJobProperties.EXEC_ID);
//    String jobId = p.getProperty(CommonJobProperties.JOB_ID);
//    String workDir = p.getProperty("working.dir");
//    final String logFilePath = String.format("%s/_job.%s.%s.log", workDir, azExecId, jobId);
//    logger.info("log file path is: " + logFilePath);
//    HadoopJobUtils.killAllSpawnedHadoopJobs(logFilePath, logger);
//    String killCmd = String.format("kill -9 %s", getPid());
//    logger.info("kill process by " + killCmd);
//    try {
//      Runtime.getRuntime().exec(killCmd);
//    } catch (Exception e){
//      logger.error(e);
//    }

  }

  private String getPid() {
    // get name representing the running Java virtual machine.
    String name = ManagementFactory.getRuntimeMXBean().getName();
    // get pid
    String pid = name.split("@")[0];
    logger.info("HiveExport Pid is:" + pid);
    return pid;
  }

  private static boolean checkParamMap(Properties p, String key) {
    return StringUtils.isEmpty(p.getProperty(key));
  }


  private static boolean checkArgs(Properties p) {
    if (p == null) {
      logger.info("args can't be null");
      return false;
    }
    if (checkParamMap(p, DATABASE_NAME) || !(p.getProperty(DATABASE_NAME).endsWith("work") || p.getProperty(DATABASE_NAME).endsWith("ind"))) {
      logger.info("args: " + DATABASE_NAME + " can't be null ,and must be end with 'work' or 'ind'");
      return false;
    }
    if (checkParamMap(p, TABEL_NAME)) {
      logger.info("args: " + TABEL_NAME + "  Illegal Argument");
      return false;
    }
    if (checkParamMap(p, EXPORT_FILE_PATH)) {
      logger.info("args: " + EXPORT_FILE_PATH + "  Illegal Argument");
      return false;
    }
    if (checkParamMap(p, EXPORT_FILE_NAME)) {
      logger.info("args: " + EXPORT_FILE_PATH + "  Illegal Argument");
      return false;
    }
    if(checkParamMap(p, QUEUE)){
      logger.info("args: " + QUEUE + "  Illegal Argument");
      return false;
    }
    logger.info("args is ok.");
    logger.info("DATABASE_NAME: " + p.getProperty(DATABASE_NAME) + ",TABEL_NAME: " + p.getProperty(TABEL_NAME) + ",PARTITION_NAME: " + p.getProperty(PARTITION_NAME)
            + ",PARTITION_VALUE: " + p.getProperty(PARTITION_VALUE) + ",EXPORT_FILE_PATH: " + p.getProperty(EXPORT_FILE_PATH) + ",EXPORT_FILE_NAME: "
            + p.getProperty(EXPORT_FILE_NAME) + ",QUEUE: " + p.getProperty(QUEUE));
    return true;
  }
  /**
   * spark-submit
   * --class com.webank.bdp.hiveexport.HiveExport
   * /appcom/Install/AzkabanInstall/HiveExport-1.0.0-SNAPSHOT.jar
   * "rxttest01_c_ind" "employee" "null" "null" "file:///appcom/Install/AzkabanInstall/testExport" "test"
   */
  private static String genCommand(Properties p){
    List<String> tmp = new ArrayList<String>();
    tmp.add(getValue(p, SPARK_HOME, DEFAULT_SPARK_HOME) + "/bin/spark-submit");
    tmp.add("--class " + getValue(p, MAIN_CLASS, DEFAULT_MAIN_CLASS));
    tmp.add("--executor-cores " + getValue(p, EXECUTOR_CORES, "2"));
    tmp.add("--executor-memory " + getValue(p, EXECUTOR_MEMORY, "1G"));
    tmp.add("--driver-memory " + getValue(p, DRIVER_MEMORY, "1G"));
    tmp.add("--master " + getValue(p, MASTER, DEFAULT_MASTER));
    tmp.add("--deploy-mode " + getValue(p, DEPLOY_MODE, DEFAULT_DEPLOY_MODE));
    tmp.add("--queue " + p.getProperty("queue"));
    tmp.add(getValue(p, EXECUTE_JAR, DEFAULT_JAR));
    // args
    tmp.add(p.getProperty(DATABASE_NAME));
    tmp.add(p.getProperty(TABEL_NAME));
    tmp.add(StringUtils.isEmpty(p.getProperty(PARTITION_NAME)) ? "null" : p.getProperty(PARTITION_NAME));
    tmp.add(StringUtils.isEmpty(p.getProperty(PARTITION_VALUE)) ? "null" : p.getProperty(PARTITION_VALUE));
    tmp.add(p.getProperty(EXPORT_FILE_PATH));
    tmp.add(p.getProperty(EXPORT_FILE_NAME));
    // args
    String cmd = String.join(" ", tmp);;
    logger.info("execute cmd: " + cmd);
    return String.join(" ", tmp);
  }


  public static String executeHqlCommand(String cmd) throws Exception {
    logger.info("Start to run Hive shell script.");
    String result;
    try {
      result = Shell.execCommand(cmd);
    }catch(Exception e){
      logger.info("", e);
      throw new Exception(e);
    }
    logger.info("Run Hive shell script result" + result);
    return result;
  }

  private static String getValue(Properties p, String key, String defaultValue){
    if(StringUtils.isBlank(p.getProperty(key))){
      return defaultValue;
    }
    return p.getProperty(key);
  }

  public static void exeCmd(String cmd) throws Exception {
    logger.info("submit spark job ...");
    Process process = null;
    try {
      process = Runtime.getRuntime().exec(cmd);
      final LogGobbler outputGobbler =
              new LogGobbler(
                      new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8),
                      logger, "INFO", 30);
      final LogGobbler errorGobbler =
              new LogGobbler(
                      new InputStreamReader(process.getErrorStream(), StandardCharsets.UTF_8),
                      logger, "ERROR", 30);
      outputGobbler.start();
      errorGobbler.start();
      int exitCode = -1;
      try {
        exitCode = process.waitFor();
      } catch (final InterruptedException e) {
        logger.error("Process interrupted. Exit code is " + exitCode);
        throw new InterruptedException();
      }
      // try to wait for everything to get logged out before exiting
      outputGobbler.awaitCompletion(5000);
      errorGobbler.awaitCompletion(5000);

      if (exitCode != 0) {
        logger.error("execute spark job failed, exitcode : " + exitCode);
        final String output =
                new StringBuilder().append("Stdout:\n")
                        .append(outputGobbler.getRecentLog()).append("\n\n")
                        .append("Stderr:\n").append(errorGobbler.getRecentLog())
                        .append("\n").toString();
        throw new RuntimeException("execute spark job failed, exitcode: " + exitCode);
      }
      logger.info("execute spark job success.");
    } finally {
      IOUtils.closeQuietly(process.getInputStream());
      IOUtils.closeQuietly(process.getOutputStream());
      IOUtils.closeQuietly(process.getErrorStream());
    }
  }


}