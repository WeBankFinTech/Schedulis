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
import azkaban.security.commons.SecurityUtils;
import azkaban.utils.Props;
import bsp.encrypt.EncryptUtil;
import org.slf4j.Logger;

import java.io.File;
import java.util.List;
import java.util.StringTokenizer;

public class JavaJob extends JavaProcessJob {

  public static final String RUN_METHOD_PARAM = "method.run";
  public static final String CANCEL_METHOD_PARAM = "method.cancel";
  public static final String PROGRESS_METHOD_PARAM = "method.progress";

  public static final String JOB_CLASS = "job.class";
  public static final String DEFAULT_CANCEL_METHOD = "cancel";
  public static final String DEFAULT_RUN_METHOD = "run";
  public static final String DEFAULT_PROGRESS_METHOD = "getProgress";

  private String runMethod;
  private String cancelMethod;
  private String progressMethod;

  private Object javaObject = null;
  private String props;

  public JavaJob(String jobid, Props sysProps, Props jobProps, Logger log) {
    super(jobid, sysProps, new Props(sysProps, jobProps), log);
    
    getJobProps().put(CommonJobProperties.JOB_ID, jobid);
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
    String typeSysJVMArgs = getSysProps().getString("jobtype.jvm.args", null);
    if (typeSysJVMArgs != null) {
      args += " " + typeSysJVMArgs;
    }
    return args;
  }

  @Override
  protected List<String> getClassPaths() {
    List<String> classPath = super.getClassPaths();

    classPath.add(getSourcePathFromClass(JavaJobRunnerMain.class));
    classPath.add(getSourcePathFromClass(Props.class));
    if (jobProps.get("type").equalsIgnoreCase("rmbsender")) {
      classPath.add(getSourcePathFromClass(SecurityUtils.class));
      classPath.add(getSourcePathFromClass(EncryptUtil.class));
    }

    classPath.add(HadoopConfigurationInjector.getPath(getJobProps(),
        getWorkingDirectory()));
    // todo ?
    String loggerPath = getSourcePathFromClass(Logger.class);
    if (!classPath.contains(loggerPath)) {
      classPath.add(loggerPath);
    }

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

  @Override
  protected String getJavaClass() {
    return JavaJobRunnerMain.class.getName();
  }

  @Override
  public String toString() {
    return "JavaJob{" + "_runMethod='" + runMethod + '\''
        + ", _cancelMethod='" + cancelMethod + '\'' + ", _progressMethod='"
        + progressMethod + '\'' + ", _javaObject=" + javaObject + ", props="
        + props + '}';
  }


  @Override
  public void run() throws Exception {
    HadoopConfigurationInjector.prepareResourcesToInject(getJobProps(),
        getWorkingDirectory());
    super.run();
  }
}
