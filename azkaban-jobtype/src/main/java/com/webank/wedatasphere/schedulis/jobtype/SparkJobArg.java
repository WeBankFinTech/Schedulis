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

package com.webank.wedatasphere.schedulis.jobtype;

public enum SparkJobArg {

  // standard spark submit arguments, ordered in the spark-submit --help order
  MASTER("master", false), // just to trick the eclipse formatter
  DEPLOY_MODE("deploy-mode", false), //
  CLASS("class", false), //
  NAME("name", false), //
  SPARK_JARS("jars", true), //
  PACKAGES("packages", false), //
  REPOSITORIES("repositories", false), //
  PY_FILES("py-files", false), //
  FILES("files", false), //
  SPARK_CONF_PREFIX("conf.", "--conf", true), //
  PROPERTIES_FILE("properties-file", false), //
  DRIVER_MEMORY("driver-memory", false), //
  DRIVER_JAVA_OPTIONS("driver-java-options", true), //
  DRIVER_LIBRARY_PATH("driver-library-path", false), //
  DRIVER_CLASS_PATH("driver-class-path", false), //
  EXECUTOR_MEMORY("executor-memory", false), //
  PROXY_USER("proxy-user", false), //
  SPARK_FLAG_PREFIX("flag.", "--", true), // --help, --verbose, --supervise, --version

  // Yarn only Arguments
  EXECUTOR_CORES("executor-cores", false), //
  DRIVER_CORES("driver-cores", false), //
  QUEUE("queue", false), //
  NUM_EXECUTORS("num-executors", false), //
  ARCHIVES("archives", false), //
  PRINCIPAL("principal", false), //
  KEYTAB("keytab", false), //

  // Not SparkSubmit arguments: only exists in azkaban
  EXECUTION_JAR("execution-jar", null, true), //
  PARAMS("params", null, true), //
  SPARK_VERSION("spark-version", null, true),
  ;

  public static final String delimiter = "\u001A";

  SparkJobArg(String propName, boolean specialTreatment) {
    this(propName, "--" + propName, specialTreatment);
  }

  SparkJobArg(String azPropName, String sparkParamName, boolean specialTreatment) {
    this.azPropName = azPropName;
    this.sparkParamName = sparkParamName;    
    this.needSpecialTreatment = specialTreatment;
  }

  final String azPropName;

  final String sparkParamName;  
  
  final boolean needSpecialTreatment;

}