/*
 * Copyright 2012 LinkedIn Corp.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */

package azkaban.jobtype;

import azkaban.flow.CommonJobProperties;
import azkaban.jobExecutor.JavaProcessJob;
import azkaban.security.commons.AbstractHadoopSecurityManager;
import azkaban.utils.Props;
import azkaban.utils.StringUtils;
import org.apache.commons.lang.math.NumberUtils;
import org.slf4j.Logger;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map.Entry;
import java.util.StringTokenizer;

import static azkaban.security.commons.AbstractHadoopSecurityManager.*;
import static org.apache.hadoop.security.UserGroupInformation.HADOOP_TOKEN_FILE_LOCATION;

/**
 * <pre>
 * The Azkaban adaptor for running a Spark Submit job.
 * Use this in conjunction with  {@link HadoopSecureSparkWrapper}
 *
 * This class is used by azkaban executor to build the classpath, main args, env and jvm props
 * for HadoopSecureSparkWrapper. Executor will then launch the job process and run
 * HadoopSecureSparkWrapper. HadoopSecureSparkWrapper will be the Spark client wrapper
 * that uses the main args to launch spark-submit.
 *
 * Expect the following jobtype property:
 *
 * spark.home (client default SPARK_HOME if user doesn't give a spark.version)
 *             Conf will be either SPARK_CONF_DIR(we do not override it) or {spark.home}/conf
 *
 * spark.1.6.0.home (spark.{version}.home is REQUIRED for the {version} that we want to support.
 *                  e.g. user can use spark 1.6.0 by setting spark.home=1.6.0 in their job property.
 *                  This class will then look for plugin property spark.1.6.0.home to get the proper spark
 *                  bin/conf to launch the client)
 *
 * spark.1.6.0.conf (OPTIONAL. spark.{version}.conf is the conf used for the {version}.
 *                  If not specified, the conf of this {version} will be spark.{version}.home/conf
 *
 * spark.dynamic.res.alloc.enforced (set to true if we want to enforce dynamic resource allocation policy.
 *                  Enabling dynamic allocation policy for spark job type is different from enabling dynamic
 *                  allocation feature for Spark. This config inside Spark job type is to enforce dynamic
 *                  allocation feature for all Spark applications submitted via Azkaban Spark job type.
 *                  If set to true, our client wrapper will ignore user specified num-executor,
 *                  also make sure user does not overrides dynamic allocation related conf.
 *                  If it is enabled, we suggest the spark cluster should set up dynamic allocation
 *                  properly and set related conf in spark-default.conf)
 *
 * spark.node.labeling.enforced (set to true if we want to enforce node labeling policy.
  *                 Enabling node labeling policy for spark job type is different from enabling node
 *                  labeling feature in YARN. This config inside Spark job type is to enforce node
 *                  labeling is used for all Spark applications submitted via Azkaban Spark job type.
 *                  If set to true, our client wrapper will ignore user specified queue. If this
 *                  is enabled, we suggest to enable node labeling in yarn cluster, and also set
 *                  queue param in spark-default.conf)
 *
 *
 * </pre>
 *
 * @see HadoopSecureSparkWrapper
 */
public class HadoopSparkJob extends JavaProcessJob {

  // Azkaban/Java params
  private static final String HADOOP_SECURE_SPARK_WRAPPER =
      HadoopSecureSparkWrapper.class.getName();

  // SPARK_HOME ENV VAR for HadoopSecureSparkWrapper(Spark Client)
  public static final String SPARK_HOME_ENV_VAR = "SPARK_HOME";
  // SPARK_CONF_DIR ENV VAR for HadoopSecureSparkWrapper(Spark Client)
  public static final String SPARK_CONF_DIR_ENV_VAR = "SPARK_CONF_DIR";
  // SPARK JOBTYPE PROPERTY spark.dynamic.res.alloc.enforced
  public static final String SPARK_DYNAMIC_RES_JOBTYPE_PROPERTY = "spark.dynamic.res.alloc.enforced";
  // HadoopSecureSparkWrapper ENV VAR if spark.dynamic.res.alloc.enforced is set to true
  public static final String SPARK_DYNAMIC_RES_ENV_VAR = "SPARK_DYNAMIC_RES_ENFORCED";
  // SPARK JOBTYPE PROPERTY spark.node.labeling.enforced
  public static final String SPARK_NODE_LABELING_JOBTYPE_PROPERTY = "spark.node.labeling.enforced";
  // HadoopSecureSparkWrapper ENV VAR if spark.node.labeling.enforced is set to true
  public static final String SPARK_NODE_LABELING_ENV_VAR = "SPARK_NODE_LABELING_ENFORCED";
  // Jobtype property for whether to enable auto node labeling for Spark applications
  // submitted via the Spark jobtype.
  public static final String SPARK_AUTO_NODE_LABELING_JOBTYPE_PROPERTY = "spark.auto.node.labeling.enabled";
  // Env var to be passed to {@HadoopSecureSparkWrapper} for whether auto node labeling
  // is enabled
  public static final String SPARK_AUTO_NODE_LABELING_ENV_VAR = "SPARK_AUTO_NODE_LABELING_ENABLED";
  // Jobtype property to configure the desired node label expression when auto node
  // labeling is enabled and min mem/vcore ratio is met.
  public static final String SPARK_DESIRED_NODE_LABEL_JOBTYPE_PROPERTY = "spark.desired.node.label";
  // Env var to be passed to {@HadoopSecureSparkWrapper} for the desired node label expression
  public static final String SPARK_DESIRED_NODE_LABEL_ENV_VAR = "SPARK_DESIRED_NODE_LABEL";
  // Jobtype property to configure the minimum mem/vcore ratio for a Spark application's
  // executor to be submitted with the desired node label expression.
  public static final String SPARK_MIN_MEM_VCORE_RATIO_JOBTYPE_PROPERTY = "spark.min.mem.vore.ratio";
  // Env var to be passed to {@HadoopSecureSparkWrapper} for the value of minimum
  // mem/vcore ratio
  public static final String SPARK_MIN_MEM_VCORE_RATIO_ENV_VAR = "SPARK_MIN_MEM_VCORE_RATIO";

  // security variables
  private String userToProxy = null;

  private boolean shouldProxy = false;

  private boolean obtainTokens = false;

  private File tokenFile = null;

  private AbstractHadoopSecurityManager hadoopSecurityManager;

  public HadoopSparkJob(String jobid, Props sysProps, Props jobProps, Logger log) {
    super(jobid, sysProps, jobProps, log);

    getJobProps().put(CommonJobProperties.JOB_ID, jobid);

    shouldProxy = getSysProps().getBoolean(ENABLE_PROXYING, false);
    getJobProps().put(ENABLE_PROXYING, Boolean.toString(shouldProxy));
    obtainTokens = getSysProps().getBoolean(OBTAIN_BINARY_TOKEN, false);

    if (shouldProxy) {
      getLog().info("Initiating hadoop security manager.");
      try {
        hadoopSecurityManager =
            HadoopJobUtils.loadHadoopSecurityManager(getSysProps(), log);
      } catch (RuntimeException e) {
        throw new RuntimeException("Failed to get hadoop security manager!", e);
      }
    }
  }

  @Override
  public void run() throws Exception {
    HadoopConfigurationInjector.prepareResourcesToInject(getJobProps(),
        getWorkingDirectory());

    if (shouldProxy && obtainTokens) {
      userToProxy = getJobProps().getString(USER_TO_PROXY);
      getLog().info("Need to proxy. Getting tokens.");
      // get tokens in to a file, and put the location in props
      Props props = new Props();
      props.putAll(getJobProps());
      props.putAll(getSysProps());
      tokenFile =
          HadoopJobUtils
              .getHadoopTokens(hadoopSecurityManager, props, getLog());
      getJobProps().put("env." + HADOOP_TOKEN_FILE_LOCATION,
          tokenFile.getAbsolutePath());
    }

    // If we enable dynamic resource allocation or node labeling in jobtype property,
    // then set proper env var for client wrapper(HadoopSecureSparkWrapper) to modify spark job conf
    // before calling spark-submit to enforce every spark job uses dynamic allocation or node labeling
    if (getSysProps().getBoolean(SPARK_DYNAMIC_RES_JOBTYPE_PROPERTY, Boolean.FALSE)) {
      getJobProps().put("env." + SPARK_DYNAMIC_RES_ENV_VAR, Boolean.TRUE.toString());
    }

    if (getSysProps().getBoolean(SPARK_NODE_LABELING_JOBTYPE_PROPERTY, Boolean.FALSE)) {
      getJobProps().put("env." + SPARK_NODE_LABELING_ENV_VAR, Boolean.TRUE.toString());
    }

    if (getSysProps().getBoolean(SPARK_AUTO_NODE_LABELING_JOBTYPE_PROPERTY, Boolean.FALSE)) {
      String desiredNodeLabel = getSysProps().get(SPARK_DESIRED_NODE_LABEL_JOBTYPE_PROPERTY);
      String minMemVcoreRatio = getSysProps().get(SPARK_MIN_MEM_VCORE_RATIO_JOBTYPE_PROPERTY);
      if (desiredNodeLabel == null || minMemVcoreRatio == null) {
        throw new RuntimeException(SPARK_DESIRED_NODE_LABEL_JOBTYPE_PROPERTY + " and " +
            SPARK_MIN_MEM_VCORE_RATIO_JOBTYPE_PROPERTY + " must be configured when " +
            SPARK_AUTO_NODE_LABELING_JOBTYPE_PROPERTY + " is set to true.");
      }
      if (!NumberUtils.isNumber(minMemVcoreRatio)) {
        throw new RuntimeException(SPARK_MIN_MEM_VCORE_RATIO_JOBTYPE_PROPERTY + " is configured as " +
            minMemVcoreRatio + ", but it must be a number.");
      }
      getJobProps().put("env." + SPARK_AUTO_NODE_LABELING_ENV_VAR, Boolean.TRUE.toString());
      getJobProps().put("env." + SPARK_DESIRED_NODE_LABEL_ENV_VAR, desiredNodeLabel);
      getJobProps().put("env." + SPARK_MIN_MEM_VCORE_RATIO_ENV_VAR, minMemVcoreRatio);
    }
    try {
      super.run();
    } catch (Throwable t) {
      t.printStackTrace();
      getLog().error("caught error running the job");
      throw new Exception(t);
    } finally {
      if (tokenFile != null) {
        HadoopJobUtils.cancelHadoopTokens(hadoopSecurityManager, userToProxy,
            tokenFile, getLog());
        if (tokenFile.exists()) {
          tokenFile.delete();
        }
      }
    }
  }

  @Override
  protected String getJavaClass() {
    return HADOOP_SECURE_SPARK_WRAPPER;
  }

  @Override
  protected String getJVMArguments() {
    String args = super.getJVMArguments();

    String typeUserGlobalJVMArgs =
        getJobProps().getString(HadoopJobUtils.JOBTYPE_GLOBAL_JVM_ARGS, null);
    if (typeUserGlobalJVMArgs != null) {
      args += " " + typeUserGlobalJVMArgs;
    }
    String typeSysGlobalJVMArgs =
        getSysProps().getString(HadoopJobUtils.JOBTYPE_GLOBAL_JVM_ARGS, null);
    if (typeSysGlobalJVMArgs != null) {
      args += " " + typeSysGlobalJVMArgs;
    }
    String typeUserJVMArgs =
        getJobProps().getString(HadoopJobUtils.JOBTYPE_JVM_ARGS, null);
    if (typeUserJVMArgs != null) {
      args += " " + typeUserJVMArgs;
    }
    String typeSysJVMArgs =
        getSysProps().getString(HadoopJobUtils.JOBTYPE_JVM_ARGS, null);
    if (typeSysJVMArgs != null) {
      args += " " + typeSysJVMArgs;
    }

    String typeUserJVMArgs2 =
        getJobProps().getString(HadoopJobUtils.JVM_ARGS, null);
    if (typeUserJVMArgs != null) {
      args += " " + typeUserJVMArgs2;
    }
    String typeSysJVMArgs2 =
        getSysProps().getString(HadoopJobUtils.JVM_ARGS, null);
    if (typeSysJVMArgs != null) {
      args += " " + typeSysJVMArgs2;
    }

    if (shouldProxy) {
      info("Setting up secure proxy info for child process");
      String secure;
      secure =
          " -D" + AbstractHadoopSecurityManager.USER_TO_PROXY + "="
              + getJobProps().getString(AbstractHadoopSecurityManager.USER_TO_PROXY);
      String extraToken =
          getSysProps().getString(AbstractHadoopSecurityManager.OBTAIN_BINARY_TOKEN,
              "false");
      if (extraToken != null) {
        secure +=
            " -D" + AbstractHadoopSecurityManager.OBTAIN_BINARY_TOKEN + "="
                + extraToken;
      }
      info("Secure settings = " + secure);
      args += secure;
    } else {
      info("Not setting up secure proxy info for child process");
    }
    return args;
  }

  @Override
  protected String getMainArguments() {
    // Build the main() arguments for HadoopSecureSparkWrapper, which are then
    // passed to spark-submit
    return testableGetMainArguments(jobProps, getWorkingDirectory(), getLog());
  }

  static String testableGetMainArguments(Props jobProps, String workingDir,
      Logger log) {

    // if we ever need to recreate a failure scenario in the test case
    log.debug(jobProps.toString());
    log.debug(workingDir);

    List<String> argList = new ArrayList<String>();

    // special case handling for DRIVER_JAVA_OPTIONS
    argList.add(SparkJobArg.DRIVER_JAVA_OPTIONS.sparkParamName);
    StringBuilder driverJavaOptions = new StringBuilder();
    // note the default java opts are communicated through the hadoop conf and
    // added in the
    // HadoopSecureSparkWrapper
    if (jobProps.containsKey(SparkJobArg.DRIVER_JAVA_OPTIONS.azPropName)) {
      driverJavaOptions.append(" "
          + jobProps.getString(SparkJobArg.DRIVER_JAVA_OPTIONS.azPropName));
    }
    argList.add(driverJavaOptions.toString());

    // Note that execution_jar and params must appear in order, and as the last
    // 2 params
    // Because of the position they are specified in the SparkJobArg class, this
    // should not be an
    // issue
    for (SparkJobArg sparkJobArg : SparkJobArg.values()) {
      if (!sparkJobArg.needSpecialTreatment) {
        handleStandardArgument(jobProps, argList, sparkJobArg);
      } else if (sparkJobArg.equals(SparkJobArg.SPARK_JARS)) {
        sparkJarsHelper(jobProps, workingDir, log, argList);
      } else if (sparkJobArg.equals(SparkJobArg.SPARK_CONF_PREFIX)) {
        sparkConfPrefixHelper(jobProps, argList);
      } else if (sparkJobArg.equals(SparkJobArg.DRIVER_JAVA_OPTIONS)) {
        // do nothing because already handled above
      } else if (sparkJobArg.equals(SparkJobArg.SPARK_FLAG_PREFIX)) {
        sparkFlagPrefixHelper(jobProps, argList);
      } else if (sparkJobArg.equals(SparkJobArg.EXECUTION_JAR)) {
        executionJarHelper(jobProps, workingDir, log, argList);
      } else if (sparkJobArg.equals(SparkJobArg.PARAMS)) {
        paramsHelper(jobProps, argList);
      } else if (sparkJobArg.equals(SparkJobArg.SPARK_VERSION)) {
        // do nothing since this arg is not a spark-submit argument
        // it is only used in getClassPaths() below
      }
    }
    return StringUtils
        .join((Collection<String>) argList, SparkJobArg.DELIMITER);
  }

  private static void paramsHelper(Props jobProps, List<String> argList) {
    if (jobProps.containsKey(SparkJobArg.PARAMS.azPropName)) {
      String params = jobProps.getString(SparkJobArg.PARAMS.azPropName);
      String[] paramsList = params.split(" ");
      for (String s : paramsList) {
        argList.add(s);
      }
    }
  }

  private static void executionJarHelper(Props jobProps, String workingDir,
      Logger log, List<String> argList) {
    if (jobProps.containsKey(SparkJobArg.EXECUTION_JAR.azPropName)) {
      String executionJarName =
          HadoopJobUtils.resolveExecutionJarName(workingDir,
              jobProps.getString(SparkJobArg.EXECUTION_JAR.azPropName), log);
      argList.add(executionJarName);
    }
  }

  private static void sparkFlagPrefixHelper(Props jobProps, List<String> argList) {
    for (Entry<String, String> entry : jobProps.getMapByPrefix(
        SparkJobArg.SPARK_FLAG_PREFIX.azPropName).entrySet()) {
      if ("true".equalsIgnoreCase(entry.getValue())) {
        argList.add(SparkJobArg.SPARK_FLAG_PREFIX.sparkParamName
            + entry.getKey());
      }
    }
  }

  private static void sparkJarsHelper(Props jobProps, String workingDir,
      Logger log, List<String> argList) {
    String propSparkJars =
        jobProps.getString(SparkJobArg.SPARK_JARS.azPropName, "");
    String jarList =
        HadoopJobUtils
            .resolveWildCardForJarSpec(workingDir, propSparkJars, log);
    if (jarList.length() > 0) {
      argList.add(SparkJobArg.SPARK_JARS.sparkParamName);
      argList.add(jarList);
    }
  }

  private static void sparkConfPrefixHelper(Props jobProps, List<String> argList) {
    for (Entry<String, String> entry : jobProps.getMapByPrefix(
        SparkJobArg.SPARK_CONF_PREFIX.azPropName).entrySet()) {
      argList.add(SparkJobArg.SPARK_CONF_PREFIX.sparkParamName);
      String sparkConfKeyVal =
          String.format("%s=%s", entry.getKey(), entry.getValue());
      argList.add(sparkConfKeyVal);
    }
  }

  private static void handleStandardArgument(Props jobProps,
      List<String> argList, SparkJobArg sparkJobArg) {
    if (jobProps.containsKey(sparkJobArg.azPropName)) {
      argList.add(sparkJobArg.sparkParamName);
      argList.add(jobProps.getString(sparkJobArg.azPropName));
    }
  }

  @Override
  protected List<String> getClassPaths() {
    // The classpath for the process that runs HadoopSecureSparkWrapper
    String pluginDir = getSysProps().get("plugin.dir");
    List<String> classPath = super.getClassPaths();

    classPath.add(getSourcePathFromClass(Props.class));
    classPath.add(getSourcePathFromClass(HadoopSecureHiveWrapper.class));
    classPath.add(getSourcePathFromClass(AbstractHadoopSecurityManager.class));

    classPath.add(HadoopConfigurationInjector.getPath(getJobProps(),
        getWorkingDirectory()));

    List<String> typeClassPath =
        getSysProps().getStringList("jobtype.classpath", null, ",");
    info("Adding jobtype.classpath: " + typeClassPath);
    if (typeClassPath != null) {
      // fill in this when load this jobtype
      for (String jar : typeClassPath) {
        File jarFile = new File(jar);
        if (!jarFile.isAbsolute()) {
          jarFile = new File(pluginDir + File.separatorChar + jar);
        }
        File tempFile = jarFile.getAbsoluteFile();
        if (!classPath.contains(tempFile.getPath())) {
          classPath.add(tempFile.getPath());
        }
      }
    }

    // Decide spark home/conf and append Spark classpath for the client.
    String[] sparkHomeConf = getSparkHomeConf();

    classPath.add(sparkHomeConf[0] + "/jars/*");
    classPath.add(sparkHomeConf[1]);

    List<String> typeGlobalClassPath =
        getSysProps().getStringList("jobtype.global.classpath", null, ",");
    info("Adding jobtype.global.classpath: " + typeGlobalClassPath);
    if (typeGlobalClassPath != null) {
      for (String jar : typeGlobalClassPath) {
        if (!classPath.contains(jar)) {
          classPath.add(jar);
        }
      }
    }

    info("Final classpath: " + classPath);
    return classPath;
  }

  private String[] getSparkHomeConf() {
    String sparkHome = null;
    String sparkConf = null;
    // If user has specified version in job property. e.g. spark.version=1.6.0
    String jobSparkVer = getJobProps().get(SparkJobArg.SPARK_VERSION.azPropName);
    if (jobSparkVer != null) {
      info("This job sets spark version: " + jobSparkVer);
      // Spark jobtype supports this version through plugin's jobtype config
      // e.g. spark.1.6.0.home=/path_to_spark/ in commonprivate.properties
      sparkHome = getSysProps().get("spark." + jobSparkVer + ".home");
      if (sparkHome != null) {
        sparkConf = getSysProps().get("spark." + jobSparkVer + ".conf");
        if (sparkConf == null) {
          sparkConf = sparkHome + "/conf";
        }
        info("Using job specific spark: " + sparkHome + " and conf: " + sparkConf);
        // Override the SPARK_HOME SPARK_CONF_DIR env for HadoopSecureSparkWrapper process(spark client)
        getJobProps().put("env." + SPARK_HOME_ENV_VAR, sparkHome);
        getJobProps().put("env." + SPARK_CONF_DIR_ENV_VAR, sparkConf);
      } else {
        info("The spark version " + jobSparkVer +" is not supported. Using system default.");
      }
    }

    // User job doesn't give spark.version
    if (sparkHome == null) {
      // Use default spark.home. Configured in the jobtype plugin's config
      sparkHome = getSysProps().get("spark.home");
      if (sparkHome == null) {
        // Use system default SPARK_HOME env
        sparkHome = System.getenv(SPARK_HOME_ENV_VAR);
      }
      sparkConf = (System.getenv(SPARK_CONF_DIR_ENV_VAR) != null) ?
        System.getenv(SPARK_CONF_DIR_ENV_VAR) : (sparkHome + "/conf");
      info("Using system default spark: " + sparkHome + " and conf: " + sparkConf);
    }

    if (sparkHome == null) {
      throw new RuntimeException("SPARK is not available on the azkaban machine.");
    } else {
      File homeDir = new File(sparkHome);
      if (!homeDir.exists()) {
        throw new RuntimeException("SPARK home dir does not exist.");
      }
      File confDir = new File(sparkConf);
      if (!confDir.exists()) {
        error("SPARK conf dir does not exist. Will use SPARK_HOME/conf as default.");
        sparkConf = sparkHome + "/conf";
      }
      File defaultSparkConf = new File(sparkConf + "/spark-defaults.conf");
      if (!defaultSparkConf.exists()) {
        throw new RuntimeException("Default Spark config file spark-defaults.conf cannot"
            + " be found at " + defaultSparkConf);
      }
    }

    return new String[]{sparkHome, sparkConf};
  }

  private static String getSourcePathFromClass(Class<?> containedClass) {
    File file =
        new File(containedClass.getProtectionDomain().getCodeSource()
            .getLocation().getPath());

    if (!file.isDirectory() && file.getName().endsWith(".class")) {
      String name = containedClass.getName();
      StringTokenizer tokenizer = new StringTokenizer(name, ".");
      while (tokenizer.hasMoreTokens()) {
        tokenizer.nextElement();
        file = file.getParentFile();
      }

      return file.getPath();
    } else {
      return containedClass.getProtectionDomain().getCodeSource().getLocation()
          .getPath();
    }
  }

  /**
   * This cancel method, in addition to the default canceling behavior, also
   * kills the Spark job on Hadoop
   */
  @Override
  public void cancel() throws InterruptedException {
    super.cancel();

    info("Cancel called.  Killing the Spark job on the cluster");

    String azExecId = jobProps.getString(CommonJobProperties.EXEC_ID);
    final String logFilePath =
        String.format("%s/_job.%s.%s.log", getWorkingDirectory(), azExecId,
            getId());
    info("log file path is: " + logFilePath);

    HadoopJobUtils.proxyUserKillAllSpawnedHadoopJobs(logFilePath, jobProps,
        tokenFile, getLog());
  }
}
