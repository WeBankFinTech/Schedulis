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

package com.webank.wedatasphere.schedulis.jobtype.util;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.management.ManagementFactory;
import java.util.Properties;
import org.apache.commons.lang3.StringUtils;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.FileSystem;
import org.apache.hadoop.fs.Path;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Export {

  public final static String DATABASENAME = "ss.databaseName";
  public final static String TABELNAME = "ss.tableName";
  public final static String PARNAME = "ss.partitionName";
  public final static String URLNAME = "ss.urlName";
  public final static String CLUSNAME = "ss.clusterName";
  public final static String TYPE = "ss.type";

  private Properties p;

  private static final Logger logger = LoggerFactory.getLogger(Export.class);

  public Export(String jobName, Properties p) {
    this.p = p;
  }

  public void run() {

    if (p == null) {
      throw new RuntimeException("Properties is null. Can't continue");
    }
    if (checkParamMap(p, DATABASENAME)) {
      throw new RuntimeException("parameter " + DATABASENAME + " can not be empty.");
    }
    if(p.getProperty(DATABASENAME).substring(p.getProperty(DATABASENAME).length()-4,p.getProperty(DATABASENAME).length()).equals("work")){
      logger.info("DATABASENAME: " + "work");
    }else if(p.getProperty(DATABASENAME).substring(p.getProperty(DATABASENAME).length()-3,p.getProperty(DATABASENAME).length()).equals("int")){
      logger.info("DATABASENAME: " + "int");
    }else{
      throw new RuntimeException("DATABASENAME: " + DATABASENAME + "is not pointed Hive database");
    }
    if (checkParamMap(p, TABELNAME)) {
      throw new RuntimeException("parameter " + TABELNAME + " can not be empty.");
    }
    if (checkParamMap(p, PARNAME)) {
      throw new RuntimeException("parameter " + PARNAME + " can not be empty.");
    }
    if (checkParamMap(p, URLNAME)) {
      throw new RuntimeException("parameter " + URLNAME + " can not be empty.");
    }
    if (checkParamMap(p, CLUSNAME)) {
      throw new RuntimeException("parameter " + CLUSNAME + " can not be empty.");
    }
    if (checkParamMap(p, TYPE)) {
      throw new RuntimeException("parameter " + TYPE + " can not be empty.");
    }
    logger.info("输入参数检测通过！");

    String HDFSUrl = p.getProperty(DATABASENAME)+p.getProperty(TABELNAME);

    getFile(HDFSUrl,p.getProperty(URLNAME));



  }

  public void cancel() throws InterruptedException {

    throw new RuntimeException("Kill this Export.");

  }

  private String getPid() {
    // get name representing the running Java virtual machine.
    String name = ManagementFactory.getRuntimeMXBean().getName();
    System.out.println(name);
    // get pid
    String pid = name.split("@")[0];
    logger.info("Export Pid is:" + pid);
    return pid;
  }

  private boolean checkParamMap(Properties p, String key) {
    boolean checkFlag = false;
    if (!p.containsKey(key)) {//判断参数是否存在
      throw new RuntimeException("parameter " + key + " is empty.");
    }
    if (p.containsKey(key)) {//判断参数是否为空字符串
      if (StringUtils.isEmpty(p.getProperty(key))) {
        checkFlag = true;
      }
    }
//    if (!MESSAGE.equals(key) && StringUtils.contains(p.getProperty(key), " ")) {
//      throw new RuntimeException("参数 " + key + " 不能包含空格 !");
//    }
//    if (!checkNoStandardStr(p.getProperty(key))) {
//      throw new RuntimeException("参数 " + key + " 不能包含字母数字_@-以外的字符 !");
//    }
//    if (p.getProperty(key).length() > 200) {
//      throw new RuntimeException("参数 " + key + " 长度不能超过 200 !");
//    }
    return checkFlag;
  }


  /**
   * 从 HDFS 下载文件
   *
   * @param srcFilePath
   * @param destPath
   */
  public static void getFile(String srcFilePath,String destPath) {

    Configuration conf = new Configuration();

    // 源文件路径
    Path srcPath = new Path(srcFilePath);

    Path dstPath = new Path(destPath);

    try {
      // 获取FileSystem对象
      FileSystem fs = FileSystem.get(conf);
      // 下载hdfs上的文件
      fs.copyToLocalFile(srcPath, dstPath);
      // 释放资源
      fs.close();
    } catch (IOException e) {
      logger.error("", e);
    }
  }
}
