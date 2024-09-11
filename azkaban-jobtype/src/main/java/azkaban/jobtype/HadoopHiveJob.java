/*
 * Copyright 2014 LinkedIn Corp.
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
import org.slf4j.Logger;

import java.io.File;
import java.io.IOException;
import java.util.*;

import static org.apache.hadoop.security.UserGroupInformation.HADOOP_TOKEN_FILE_LOCATION;

public class HadoopHiveJob extends JavaProcessJob {

  public static final String HIVE_SCRIPT = "hive.script";
  private static final String HIVECONF_PARAM_PREFIX = "hiveconf.";
  private static final String HIVEVAR_PARAM_PREFIX = "hivevar.";
  public static final String HADOOP_SECURE_HIVE_WRAPPER =
      "azkaban.jobtype.HadoopSecureHiveWrapper";

  private String userToProxy = null;
  private boolean shouldProxy = false;
  private boolean obtainTokens = false;
  private File tokenFile = null;

  private AbstractHadoopSecurityManager hadoopSecurityManager;

  private boolean debug = false;

  public HadoopHiveJob(String jobid, Props sysProps, Props jobProps, Logger log)
      throws IOException {
    super(jobid, sysProps, jobProps, log);

    getJobProps().put(CommonJobProperties.JOB_ID, jobid);

    shouldProxy = getSysProps().getBoolean(AbstractHadoopSecurityManager.ENABLE_PROXYING, false);
    getJobProps().put(AbstractHadoopSecurityManager.ENABLE_PROXYING, Boolean.toString(shouldProxy));
    obtainTokens = getSysProps().getBoolean(AbstractHadoopSecurityManager.OBTAIN_BINARY_TOKEN, false);

    debug = getJobProps().getBoolean("debug", false);

    if (shouldProxy) {
      getLog().info("Initiating hadoop security manager.");
      try {
        hadoopSecurityManager = HadoopJobUtils.loadHadoopSecurityManager(getSysProps(), log);
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
      userToProxy = getJobProps().getString("user.to.proxy");
      getLog().info("Need to proxy. Getting tokens.");
      // get tokens in to a file, and put the location in props
      Props props = new Props();
      props.putAll(getJobProps());
      props.putAll(getSysProps());
      tokenFile = HadoopJobUtils.getHadoopTokens(hadoopSecurityManager, props, getLog());
      getJobProps().put("env." + HADOOP_TOKEN_FILE_LOCATION,
          tokenFile.getAbsolutePath());
    }

    try {
      super.run();  
    } catch (Throwable t) {
      t.printStackTrace();
      getLog().error("caught error running the job");
      throw new Exception(t);
    } finally {
      if (tokenFile != null) {
        HadoopJobUtils.cancelHadoopTokens(hadoopSecurityManager, userToProxy, tokenFile, getLog());
        if (tokenFile.exists()) {
          tokenFile.delete();
        }
      }
    }
  }

  @Override
  protected String getJavaClass() {
    return HADOOP_SECURE_HIVE_WRAPPER;
  }

  @Override
  protected String getJVMArguments() {
    String args = super.getJVMArguments();

    String typeUserGlobalJVMArgs =
        getJobProps().getString("jobtype.global.jvm.args", null);
    if (typeUserGlobalJVMArgs != null) {
      args += " " + typeUserGlobalJVMArgs;
    }
    String typeSysGlobalJVMArgs =
        getSysProps().getString("jobtype.global.jvm.args", null);
    if (typeSysGlobalJVMArgs != null) {
      args += " " + typeSysGlobalJVMArgs;
    }
    String typeUserJVMArgs = getJobProps().getString("jobtype.jvm.args", null);
    if (typeUserJVMArgs != null) {
      args += " " + typeUserJVMArgs;
    }
    String typeSysJVMArgs = getSysProps().getString("jobtype.jvm.args", null);
    if (typeSysJVMArgs != null) {
      args += " " + typeSysJVMArgs;
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
    ArrayList<String> list = new ArrayList<String>();

    // for hiveconf
    Map<String, String> map = getHiveConf();
    if(!map.containsKey(AbstractHadoopSecurityManager.HIVE_HADOOP_CLASSPATH)){
      info("hive.hadoop.classpath not found, add it now");
      map.put(AbstractHadoopSecurityManager.HIVE_HADOOP_CLASSPATH,"/appcom/Install/hive/lib/*");
    }
    if (map != null) {
      for (Map.Entry<String, String> entry : map.entrySet()) {
        list.add("-hiveconf");
        list.add(entry.getKey() + "=" + entry.getValue());
      }
    }

    if (debug) {
      list.add("-hiveconf");
      list.add("hive.root.logger=INFO,console");
    }

    // for hivevar
    Map<String, String> hiveVarMap = getHiveVar();
    if(!hiveVarMap.containsKey(AbstractHadoopSecurityManager.HIVE_HADOOP_CLASSPATH)){
      info("hive.hadoop.classpath not found, add it now");
      hiveVarMap.put(AbstractHadoopSecurityManager.HIVE_HADOOP_CLASSPATH,"/appcom/Install/hive/lib/*");
    }
    if (hiveVarMap != null) {
      for (Map.Entry<String, String> entry : hiveVarMap.entrySet()) {
        list.add("-hivevar");
        list.add(entry.getKey() + "=" + entry.getValue());
      }
    }

    list.add("-f");
    list.add(getScript());

    return StringUtils.join((Collection<String>) list, " ");
  }

  @Override
  protected List<String> getClassPaths() {

    List<String> classPath = super.getClassPaths();

    classPath.add(getSourcePathFromClass(Props.class));
    classPath.add(getSourcePathFromClass(HadoopSecureHiveWrapper.class));
    classPath.add(getSourcePathFromClass(AbstractHadoopSecurityManager.class));

    classPath.add(HadoopConfigurationInjector.getPath(getJobProps(),
        getWorkingDirectory()));
    List<String> typeClassPath =
        getSysProps().getStringList("jobtype.classpath", null, ",");
    if (typeClassPath != null) {
      // fill in this when load this jobtype
      String pluginDir = getSysProps().get("plugin.dir");
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

    List<String> typeGlobalClassPath =
        getSysProps().getStringList("jobtype.global.classpath", null, ",");
    if (typeGlobalClassPath != null) {
      for (String jar : typeGlobalClassPath) {
        if (!classPath.contains(jar)) {
          classPath.add(jar);
        }
      }
    }

    return classPath;
  }

  protected String getScript() {
    return getJobProps().getString(HIVE_SCRIPT);
  }

  protected Map<String, String> getHiveConf() {
    return getJobProps().getMapByPrefix(HIVECONF_PARAM_PREFIX);
  }

  protected Map<String, String> getHiveVar() {
    return getJobProps().getMapByPrefix(HIVEVAR_PARAM_PREFIX);
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
   * This cancel method, in addition to the default canceling behavior, also kills the MR jobs launched by Hive
   * on Hadoop
   */
  @Override
  public void cancel() throws InterruptedException {
    super.cancel();

    info("Cancel called.  Killing the Hive launched MR jobs on the cluster");

    String azExecId = jobProps.getString(CommonJobProperties.EXEC_ID);
    final String logFilePath =
        String.format("%s/_job.%s.%s.log", getWorkingDirectory(), azExecId,
            getId());
    info("log file path is: " + logFilePath);

    HadoopJobUtils.proxyUserKillAllSpawnedHadoopJobs(logFilePath, jobProps, tokenFile, getLog());
  }
}
