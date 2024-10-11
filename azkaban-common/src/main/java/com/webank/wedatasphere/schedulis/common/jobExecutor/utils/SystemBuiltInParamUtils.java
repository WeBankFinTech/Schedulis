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

package com.webank.wedatasphere.schedulis.common.jobExecutor.utils;

import azkaban.executor.ExecutableFlow;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.apache.commons.lang.StringUtils;
import org.slf4j.LoggerFactory;
import org.slf4j.Logger;

@Deprecated
public class SystemBuiltInParamUtils {

  private static final Logger logger = LoggerFactory.getLogger(SystemBuiltInParamUtils.class);

  public static final String RUN_DATE = "run_date";
  public static final String RUN_DATE_STD = "run_date_std";
  public static final String RUN_MONTH_BEGIN = "run_month_begin";
  public static final String RUN_MONTH_BEGIN_STD = "run_month_begin_std";
  public static final String RUN_MONTH_END = "run_month_end";
  public static final String RUN_MONTH_END_STD = "run_month_end_std";
  public static final String MINUS = "MINUS";
  public static final String PLUS = "PLUS";

  private Map<String, String> propMap = new HashMap<>();

  //写入内容到文件
  private void FileWrite(String filePath, String fileStr){
    FileWriter fw = null;
    try {
      fw = new FileWriter(filePath);
      //写入到文件
      fw.write(fileStr);
    } catch (Exception e) {
      logger.error("写入脚本文件异常！", e);
      e.printStackTrace();
    }finally {
      if(fw != null){
        try {
          fw.close();
        } catch (IOException e) {
          e.printStackTrace();
        }
      }
    }
  }

  //获取所有用户配置文件路径
  private List<String> loadAllPropertiesFile(String workingDir){
    List<String> propPathList = new ArrayList<>();

    findPropPath(workingDir, propPathList);

    return propPathList;
  }

  //递归查找配置文件路径
  private void findPropPath(String dirPath, List<String> filePathList){
    File f = new File(dirPath);
    if (!f.exists()) {
      System.out.println(dirPath + " not exists");
    }
    File fa[] = f.listFiles();
    for (int i = 0; i < fa.length; i++) {
      File fs = fa[i];
      if (fs.isDirectory()) {
        findPropPath(fs.getPath(), filePathList);
      } else {
        if(fs.getName().endsWith(".properties")){
          filePathList.add(fs.getPath().toString());
        }
      }
    }
  }

  //获取所有脚本文件地址
  private List<String> loadAllScriptFile(String workingDir){
    List<String> scriptPathList = new ArrayList<>();

    findScriptFilePath(workingDir, scriptPathList);

    return scriptPathList;
  }

  //递归脚本目录
  private void findScriptFilePath(String dirPath, List<String> filePathList){
    File f = new File(dirPath);
    if (!f.exists()) {
      //System.out.println(dirPath + " not exists");
      logger.error("文件地址: " + dirPath + "不存在！");
    }
    File fa[] = f.listFiles();
    for (int i = 0; i < fa.length; i++) {
      File fs = fa[i];
      if (fs.isDirectory()) {
        findScriptFilePath(fs.getPath(), filePathList);
      } else {
        if(fs.getName().endsWith(".py") || fs.getName().endsWith(".sh")
            || fs.getName().endsWith(".sql") || fs.getName().endsWith(".hql")){
          filePathList.add(fs.getPath().toString());
        }
      }
    }
  }

  //读取propertie文件内容
  private Map<String, String> readProperties(String propPath){
    Map<String, String> propMap = new HashMap<>();
    Properties prop = new Properties();
    InputStream input = null;
    try {
      input = new FileInputStream(propPath);
      // load a properties file
      prop.load(input);

      if(!prop.isEmpty()){
        for (Map.Entry<Object, Object> entry : prop.entrySet()) {
          String key = String.valueOf(entry.getKey());
          String value = String.valueOf(entry.getValue());
          propMap.put(key, value);
        }
      }
    } catch (Exception ex) {
      logger.error("读取properties配置文件异常！", ex);
      ex.printStackTrace();
    } finally {
      if (input != null) {
        try {
          input.close();
        } catch (IOException e) {
          e.printStackTrace();
        }
      }
    }
    return propMap;
  }

  //读取文件
  private String readFile(String filePath)
  {
    StringBuilder sb = new StringBuilder();
    BufferedReader br = null;
    try {
      File file = new File(filePath);
      br = new BufferedReader(new FileReader(file));
      char[] data = new char[1024];
      int rn = 0;
      String line = "";
      while ((rn = br.read(data)) > 0){
        String st = String.valueOf(data, 0, rn);
        sb.append(st);
      }
      String fileStr = sb.toString();
      //System.out.println(fileStr);
      return fileStr;
    } catch (Exception e) {
      logger.error("读取脚本文件异常！", e);
      e.printStackTrace();
    } finally {
      if(br != null){
        try {
          br.close();
        } catch (IOException e) {
          e.printStackTrace();
        }
      }
    }
    return sb.toString();
  }



  //用脚本内容字符串中解析出需要替换的参数
  private Map<String, String> paramDecompose(String fileStr, ExecutableFlow ef){

    Map<String, String> paramReplaceMap = new HashMap<>();

    Pattern pattern = Pattern.compile("\\$\\{([^\\}]+)\\}");
    Matcher matcher = pattern.matcher(fileStr);
    while(matcher.find()){
      String fullStr = matcher.group();
      String valueStr = matcher.group(1);

      //设置内置参数的时间
      if(0 == ef.getFlowType()){
        //正常执行的情况
        paramReplaceMap.put(fullStr, scriptTimeHandleOther(valueStr, ef.getStartTime()));
      }else{
        //历史补采的情况
        paramReplaceMap.put(fullStr, scriptTimeHandleOther(valueStr, Long.valueOf(ef.getRepeatOption().get("startTimeLong"))));
      }

    }

    return paramReplaceMap;
  }

  private String scriptTimeHandle(String param, long runDate) {

    //时间字符串
    String timeStr = "";

    Date date = new Date(runDate);
    Instant instant = date.toInstant();
    ZoneId zoneId = ZoneId.systemDefault();

    // atZone()方法返回在指定时区从此Instant生成的ZonedDateTime。
    LocalDate localDate = instant.atZone(zoneId).toLocalDate().minusDays(1);

    //LocalDate localDate = LocalDate.now().minusDays(1);

    param = param.replaceAll("\\s*", "");

    if (RUN_DATE.equals(param)) {
      timeStr = localDate.format(DateTimeFormatter.ofPattern("yyyyMMdd"));
    } else if (RUN_DATE_STD.equals(param)) {
      timeStr = localDate.toString();
    } else if (param.contains(RUN_DATE) && !param.contains(RUN_DATE_STD)) {

      String mathStr = StringUtils.substringAfter(param, RUN_DATE);

      String[] sAry = {};

      if (MINUS.equals(paramVerify(mathStr, param))) {
        sAry = mathStr.split("-");
        timeStr = localDate.minusDays(Long.valueOf(sAry[1]))
            .format(DateTimeFormatter.ofPattern("yyyyMMdd"));
      } else if (PLUS.equals(paramVerify(mathStr, param))) {
        sAry = mathStr.split("\\+");
        timeStr = localDate.plusDays(Long.valueOf(sAry[1]))
            .format(DateTimeFormatter.ofPattern("yyyyMMdd"));
      }

    } else if (param.contains(RUN_DATE) && param.contains(RUN_DATE_STD)) {

      String mathStr = StringUtils.substringAfter(param, RUN_DATE_STD);

      String[] sAry = {};

      if (MINUS.equals(paramVerify(mathStr, param))) {
        sAry = mathStr.split("-");
        timeStr = localDate.minusDays(Long.valueOf(sAry[1])).toString();
      } else if (PLUS.equals(paramVerify(mathStr, param))) {
        sAry = mathStr.split("\\+");
        timeStr = localDate.plusDays(Long.valueOf(sAry[1])).toString();
      }
    }

    if (RUN_MONTH_BEGIN.equals(param)) {
      timeStr = localDate.with(TemporalAdjusters.firstDayOfMonth())
          .format(DateTimeFormatter.ofPattern("yyyyMMdd"));

    } else if (RUN_MONTH_BEGIN_STD.equals(param)) {
      timeStr = localDate.with(TemporalAdjusters.firstDayOfMonth()).toString();

    } else if (param.contains(RUN_MONTH_BEGIN) && !param.contains(RUN_MONTH_BEGIN_STD)) {

      String mathStr = StringUtils.substringAfter(param, RUN_MONTH_BEGIN);

      String[] sAry = {};

      if (MINUS.equals(paramVerify(mathStr, param))) {
        sAry = mathStr.split("-");
        timeStr = localDate.with(TemporalAdjusters.firstDayOfMonth())
            .minusMonths(Long.valueOf(sAry[1]))
            .format(DateTimeFormatter.ofPattern("yyyyMMdd"));
      } else if (PLUS.equals(paramVerify(mathStr, param))) {
        sAry = mathStr.split("\\+");
        timeStr = localDate.with(TemporalAdjusters.firstDayOfMonth())
            .plusMonths(Long.valueOf(sAry[1]))
            .format(DateTimeFormatter.ofPattern("yyyyMMdd"));
      }
    } else if (param.contains(RUN_MONTH_BEGIN) && param.contains(RUN_MONTH_BEGIN_STD)) {

      String mathStr = StringUtils.substringAfter(param, RUN_MONTH_BEGIN_STD);

      String[] sAry = {};

      if (MINUS.equals(paramVerify(mathStr, param))) {
        sAry = mathStr.split("-");
        timeStr = localDate.with(TemporalAdjusters.firstDayOfMonth())
            .minusMonths(Long.valueOf(sAry[1])).toString();
      } else if (PLUS.equals(paramVerify(mathStr, param))) {
        sAry = mathStr.split("\\+");
        timeStr = localDate.with(TemporalAdjusters.firstDayOfMonth())
            .plusMonths(Long.valueOf(sAry[1])).toString();
      }
    }

    return timeStr;
  }

  private String scriptTimeHandleOther(String param, long runDate){

    //时间字符串
    String timeStr = "";

    Date date = new Date(runDate);
    Instant instant = date.toInstant();
    ZoneId zoneId = ZoneId.systemDefault();

    // atZone()方法返回在指定时区从此Instant生成的ZonedDateTime。
    LocalDate localDate = instant.atZone(zoneId).toLocalDate().minusDays(1);

    //LocalDate localDate = LocalDate.now().minusDays(1);

    param = param.replaceAll("\\s*", "");

    if(RUN_DATE.equals(param)){
      timeStr = localDate.format(DateTimeFormatter.ofPattern("yyyyMMdd"));
    }else if(RUN_DATE_STD.equals(param)){
      timeStr = localDate.toString();
    }else if(param.contains(RUN_DATE) && !param.contains(RUN_DATE_STD)){

      String mathStr = StringUtils.substringAfter(param, RUN_DATE);

      String[] sAry = {};

      if(MINUS.equals(paramVerify(mathStr, param))){
        sAry = mathStr.split("-");
        timeStr = localDate.minusDays(Long.valueOf(sAry[1]))
            .format(DateTimeFormatter.ofPattern("yyyyMMdd"));
      }else if(PLUS.equals(paramVerify(mathStr, param))){
        sAry = mathStr.split("\\+");
        timeStr = localDate.plusDays(Long.valueOf(sAry[1]))
            .format(DateTimeFormatter.ofPattern("yyyyMMdd"));
      }

    }else if(param.contains(RUN_DATE) && param.contains(RUN_DATE_STD)){

      String mathStr = StringUtils.substringAfter(param, RUN_DATE_STD);

      String[] sAry = {};

      if(MINUS.equals(paramVerify(mathStr, param))){
        sAry = mathStr.split("-");
        timeStr = localDate.minusDays(Long.valueOf(sAry[1])).toString();
      }else if(PLUS.equals(paramVerify(mathStr, param))){
        sAry = mathStr.split("\\+");
        timeStr = localDate.plusDays(Long.valueOf(sAry[1])).toString();
      }
    }

    if(RUN_MONTH_BEGIN.equals(param)){
      timeStr = localDate.with(TemporalAdjusters.firstDayOfMonth())
          .format(DateTimeFormatter.ofPattern("yyyyMMdd"));

    }else if(RUN_MONTH_BEGIN_STD.equals(param)){
      timeStr = localDate.with(TemporalAdjusters.firstDayOfMonth()).toString();

    }else if(param.contains(RUN_MONTH_BEGIN) && !param.contains(RUN_MONTH_BEGIN_STD)){

      String mathStr = StringUtils.substringAfter(param, RUN_MONTH_BEGIN);

      String[] sAry = {};

      if(MINUS.equals(paramVerify(mathStr, param))) {
        sAry = mathStr.split("-");
        timeStr = localDate.with(TemporalAdjusters.firstDayOfMonth())
            .minusMonths(Long.valueOf(sAry[1]))
            .format(DateTimeFormatter.ofPattern("yyyyMMdd"));
      } else if(PLUS.equals(paramVerify(mathStr, param))) {
        sAry = mathStr.split("\\+");
        timeStr = localDate.with(TemporalAdjusters.firstDayOfMonth())
            .plusMonths(Long.valueOf(sAry[1]))
            .format(DateTimeFormatter.ofPattern("yyyyMMdd"));
      }
    }else if(param.contains(RUN_MONTH_BEGIN) && param.contains(RUN_MONTH_BEGIN_STD)){

      String mathStr = StringUtils.substringAfter(param, RUN_MONTH_BEGIN_STD);

      String[] sAry = {};

      if(MINUS.equals(paramVerify(mathStr, param))) {
        sAry = mathStr.split("-");
        timeStr = localDate.with(TemporalAdjusters.firstDayOfMonth())
            .minusMonths(Long.valueOf(sAry[1])).toString();
      } else if(PLUS.equals(paramVerify(mathStr, param))) {
        sAry = mathStr.split("\\+");
        timeStr = localDate.with(TemporalAdjusters.firstDayOfMonth())
            .plusMonths(Long.valueOf(sAry[1])).toString();
      }
    }

    if(RUN_MONTH_END.equals(param)){
      timeStr = localDate.withDayOfMonth(localDate.lengthOfMonth())
          .format(DateTimeFormatter.ofPattern("yyyyMMdd"));
    }else if(RUN_MONTH_END_STD.equals(param)){
      timeStr = localDate.withDayOfMonth(localDate.lengthOfMonth()).toString();

    }else if(param.contains(RUN_MONTH_END) && !param.contains(RUN_MONTH_END_STD)){

      String mathStr = StringUtils.substringAfter(param, RUN_MONTH_END);

      String[] sAry = {};

      if(MINUS.equals(paramVerify(mathStr, param))) {
        sAry = mathStr.split("-");
        timeStr = localDate.withDayOfMonth(localDate.lengthOfMonth())
            .minusMonths(Long.valueOf(sAry[1]))
            .format(DateTimeFormatter.ofPattern("yyyyMMdd"));
      } else if(PLUS.equals(paramVerify(mathStr, param))) {
        sAry = mathStr.split("\\+");
        timeStr = localDate.withDayOfMonth(localDate.lengthOfMonth())
            .plusMonths(Long.valueOf(sAry[1]))
            .format(DateTimeFormatter.ofPattern("yyyyMMdd"));
      }
    }else if(param.contains(RUN_MONTH_END) && param.contains(RUN_MONTH_END_STD)){

      String mathStr = StringUtils.substringAfter(param, RUN_MONTH_END_STD);

      String[] sAry = {};

      if(MINUS.equals(paramVerify(mathStr, param))) {
        sAry = mathStr.split("-");
        timeStr = localDate.withDayOfMonth(localDate.lengthOfMonth())
            .minusMonths(Long.valueOf(sAry[1])).toString();
      } else if(PLUS.equals(paramVerify(mathStr, param))) {
        sAry = mathStr.split("\\+");
        timeStr = localDate.withDayOfMonth(localDate.lengthOfMonth()                                                                                   )
            .plusMonths(Long.valueOf(sAry[1])).toString();
      }
    }

    return timeStr;
  }

  //过滤用户设置的参数 排除用户设置过的参数
  private void filterUserParam(Map<String, String> systemParam){
    Map<String, String> handleMap = new HashMap<>();
    handleMap.putAll(systemParam);
    for(String userKey : getPropMap().keySet()){
      for(String systemKey : handleMap.keySet()){
        if(systemKey.contains(userKey) && !userKey.contains("_std")
            && !systemKey.contains(userKey + "_std")){
          systemParam.remove(systemKey);
        }
        if(systemKey.contains(userKey) && userKey.contains("_std")){
          systemParam.remove(systemKey);
        }
      }
    }

  }

  //内置参数处理主流程
  public void run(String workingDir, ExecutableFlow ef){

    SystemBuiltInParamUtils df = new SystemBuiltInParamUtils();
    //用户propertie文件路径集合
    List<String> propPathList = df.loadAllPropertiesFile(workingDir);

    //获取用户所有配置文件参数
    for(String filePath : propPathList){
      df.getPropMap().putAll(df.readProperties(filePath));
    }

    //所有脚本的文件地址
    List<String> scriptPathList = df.loadAllScriptFile(workingDir);

    //循环脚本文件地址
    for(String filePath : scriptPathList){

      //读取单个脚本文件的内容
      String fileStr = df.readFile(filePath);
      System.out.println("===============Start===============");
      //获取单个脚本中需要替换的参数
      Map<String, String> scriptMap = df.paramDecompose(fileStr, ef);
      //如果用户没有配置run_date或者run_date_std参数 就使用系统内置变量
      //并把用户以外的配置去除
      if(StringUtils.isEmpty(df.getPropMap().get(df.RUN_DATE))
          && StringUtils.isEmpty(df.getPropMap().get(df.RUN_DATE_STD))){

        //过滤用户设置的参数
        df.filterUserParam(scriptMap);

        //循环替换脚本中对应的参数内容
        for(String timeStr : scriptMap.keySet()){
          fileStr = StringUtils.replace(fileStr, timeStr, scriptMap.get(timeStr));
        }
      }

      System.out.println("===============End================");

      //将替换后的内容重新写入到脚本文件中
      df.FileWrite(filePath, fileStr);
    }

  }


  public static void main(String[] args) {

    LocalDate localDate = LocalDate.now().minusDays(1);

    String timeStr = localDate.format(DateTimeFormatter.ofPattern("yyyyMMdd"));

    System.out.println(localDate.toString());

    System.out.println(timeStr);

  }


  public Map<String, String> getPropMap() {
    return propMap;
  }

  public void setPropMap(Map<String, String> propMap) {
    this.propMap = propMap;
  }

  public void addPropMap(String key, String value) {
    this.propMap.put(key, value);
  }


  private String paramVerify(String param, String fullParam){

    int minusSite = param.indexOf("-");
    int plusSite = param.indexOf("+");

    String symbol = "";

    String[] sAry = null;

    if((plusSite > minusSite && minusSite != -1) || plusSite == -1){
      sAry = param.split("-");
      symbol = MINUS;
    } else if ((minusSite > plusSite && plusSite != -1) || minusSite == -1){
      sAry = param.split("\\+");
      symbol = PLUS;
    }

    if(sAry.length > 1 && sAry.length == 2){
      String start = sAry[0];
      if(StringUtils.isNotEmpty(start)){
        logger.error("脚本替换参数适配异常！请检查脚本！");
        throw new RuntimeException("The script parameter ${" + fullParam + "} exception!Please check the script!");
      }
      String str = sAry[1];
      Pattern pattern = Pattern.compile("[0-9]*");
      if(!pattern.matcher(str).matches()){
        logger.error("脚本替换参数适配异常！请检查脚本！");
        throw new RuntimeException("The script parameter ${" + fullParam + "} exception!Please check the script!");
      }
    }else if(sAry.length > 2){//多个运算符号就报异常
      logger.error("脚本替换参数适配异常！请检查脚本！");
      throw new RuntimeException("The script parameter ${" + fullParam + "} exception!Please check the script!");
    }else if(sAry.length <= 1){//多个运算符号就报异常
      logger.error("脚本替换参数适配异常！请检查脚本！");
      throw new RuntimeException("The script parameter ${" + fullParam + "} exception!Please check the script!");
    }


    return symbol;
  }


}
