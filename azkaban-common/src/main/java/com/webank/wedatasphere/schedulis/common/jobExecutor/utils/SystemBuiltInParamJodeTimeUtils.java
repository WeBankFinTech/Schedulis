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

import com.webank.wedatasphere.schedulis.common.utils.DateUtils;
import org.apache.commons.lang.StringUtils;
import org.slf4j.LoggerFactory;
import org.slf4j.Logger;
import org.joda.time.LocalDate;
import org.joda.time.format.DateTimeFormat;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import azkaban.executor.ExecutableFlow;
import org.joda.time.format.DateTimeFormatter;

public class SystemBuiltInParamJodeTimeUtils {

  private static final Logger logger = LoggerFactory.getLogger(SystemBuiltInParamJodeTimeUtils.class);

  public static final String TIME_TEMPLATE = "(\\d{4}\\.\\d{2}\\.\\d{2}|\\d{4}/\\d{2}/\\d{2}|\\d{4}-\\d{2}-\\d{2}|\\d{4}\\d{2}\\d{2})";

  private Map<String, String> propMap = new HashMap<>();

  private Map<String, LocalDate> defaultDate = new HashMap<>();

  private static String re = "[a-z_]+((\\+|-)[1-9][0-9]*){0,1}";

  private void initDate(ExecutableFlow executableFlow) throws RuntimeException{
    LocalDate runDate = null;
    DateTimeFormatter dateTimeFormatter = DateTimeFormat.forPattern("yyyyMMdd");
    //历史重跑
    if(2 == executableFlow.getFlowType()){
      runDate = new LocalDate(Long.valueOf(executableFlow.getRepeatOption().get("startTimeLong"))).minusDays(1);
    } else {
      if (null != executableFlow.getExecutionOptions().getFlowParameters().get(Date.RUN_DATE.getValue())) {
        runDate = LocalDate.parse(executableFlow.getExecutionOptions().getFlowParameters().get(Date.RUN_DATE.getValue()), dateTimeFormatter);
      } else if(this.propMap.get(Date.RUN_DATE.getValue()) != null) {
        String tmp = this.propMap.get(Date.RUN_DATE.getValue()).replaceAll("[\"'./-]","");
        runDate = LocalDate.parse(tmp, dateTimeFormatter);
      } else {
        runDate = new LocalDate(executableFlow.getSubmitTime()).minusDays(1);
      }
    }
    //用于前端显示
    executableFlow.setRunDate(runDate.toString("yyyyMMdd"));

    defaultDate.put(Date.RUN_DATE.getValue(), runDate);
    defaultDate.put(Date.RUN_DATE_STD.getValue(), runDate);
    defaultDate.put(Date.RUN_TODAY.getValue(), runDate.plusDays(1));
    defaultDate.put(Date.RUN_TODAY_STD.getValue(), runDate.plusDays(1));
    defaultDate.put(Date.RUN_MONTH_BEGIN.getValue(), runDate.dayOfMonth().withMinimumValue());
    defaultDate.put(Date.RUN_MONTH_BEGIN_STD.getValue(), runDate.dayOfMonth().withMinimumValue());
    defaultDate.put(Date.RUN_MONTH_END.getValue(), runDate.dayOfMonth().withMaximumValue());
    defaultDate.put(Date.RUN_MONTH_END_STD.getValue(), runDate.dayOfMonth().withMaximumValue());

    defaultDate.put(Date.RUN_QUARTER_BEGIN.getValue(), DateUtils.getQuarterBegin(runDate));
    defaultDate.put(Date.RUN_QUARTER_END.getValue(), DateUtils.getQuarterEnd(runDate));
    defaultDate.put(Date.RUN_HALF_YEAR_BEGIN.getValue(), DateUtils.getHalfYearBegin(runDate));
    defaultDate.put(Date.RUN_HALF_YEAR_END.getValue(), DateUtils.getHalfYearEnd(runDate));
    defaultDate.put(Date.RUN_YEAR_BEGIN.getValue(), DateUtils.getYearBegin(runDate));
    defaultDate.put(Date.RUN_YEAR_END.getValue(), DateUtils.getYearEnd(runDate));
    defaultDate.put(Date.RUN_LAST_MONTH_END.getValue(), DateUtils.getLastMonthEnd(runDate));
    defaultDate.put(Date.RUN_LAST_QUARTER_END.getValue(), DateUtils.getLastQuarterEnd(runDate));
    defaultDate.put(Date.RUN_LAST_YEAR_END.getValue(), DateUtils.getLastYearEnd(runDate));

    defaultDate.put(Date.RUN_QUARTER_BEGIN_STD.getValue(), DateUtils.getQuarterBegin(runDate));
    defaultDate.put(Date.RUN_QUARTER_END_STD.getValue(), DateUtils.getQuarterEnd(runDate));
    defaultDate.put(Date.RUN_HALF_YEAR_BEGIN_STD.getValue(), DateUtils.getHalfYearBegin(runDate));
    defaultDate.put(Date.RUN_HALF_YEAR_END_STD.getValue(), DateUtils.getHalfYearEnd(runDate));
    defaultDate.put(Date.RUN_YEAR_BEGIN_STD.getValue(), DateUtils.getYearBegin(runDate));
    defaultDate.put(Date.RUN_YEAR_END_STD.getValue(), DateUtils.getYearEnd(runDate));
    defaultDate.put(Date.RUN_LAST_MONTH_END_STD.getValue(), DateUtils.getLastMonthEnd(runDate));
    defaultDate.put(Date.RUN_LAST_QUARTER_END_STD.getValue(), DateUtils.getLastQuarterEnd(runDate));
    defaultDate.put(Date.RUN_LAST_YEAR_END_STD.getValue(), DateUtils.getLastYearEnd(runDate));

    if (2 != executableFlow.getFlowType()) {
      Date date[] = {
          Date.RUN_DATE_STD,
          Date.RUN_TODAY,
          Date.RUN_TODAY_STD,
          Date.RUN_MONTH_BEGIN,
          Date.RUN_MONTH_BEGIN_STD,
          Date.RUN_MONTH_END,
          Date.RUN_MONTH_END_STD,
          Date.RUN_QUARTER_BEGIN,
          Date.RUN_QUARTER_BEGIN_STD,
          Date.RUN_QUARTER_END,
          Date.RUN_QUARTER_END_STD,
          Date.RUN_LAST_QUARTER_END,
          Date.RUN_LAST_QUARTER_END_STD,
          Date.RUN_HALF_YEAR_BEGIN,
          Date.RUN_HALF_YEAR_BEGIN_STD,
          Date.RUN_HALF_YEAR_END,
          Date.RUN_HALF_YEAR_END_STD,
          Date.RUN_YEAR_BEGIN,
          Date.RUN_YEAR_BEGIN_STD,
          Date.RUN_YEAR_END,
          Date.RUN_YEAR_END_STD,
          Date.RUN_LAST_MONTH_END,
          Date.RUN_LAST_MONTH_END_STD,
          Date.RUN_LAST_YEAR_END,
          Date.RUN_LAST_YEAR_END_STD
      };
      for (Date item : date) {
        LocalDate newDate;
        newDate = hasNewDate(executableFlow, item);
        if (newDate != null) {
          defaultDate.put(item.getValue(), newDate);
        }
      }
    }

  }

  private LocalDate hasNewDate(ExecutableFlow executableFlow, Date dateType){
    LocalDate newDate = null;
    if (null != executableFlow.getExecutionOptions().getFlowParameters().get(dateType.getValue())) {
      newDate = LocalDate.parse(executableFlow.getExecutionOptions().getFlowParameters().get(dateType.getValue()),
          DateTimeFormat.forPattern(dateType.getFormat()));
    } else if(this.propMap.get(dateType.getValue()) != null) {
      newDate = LocalDate.parse(this.propMap.get(dateType.getValue()), DateTimeFormat.forPattern(dateType.getFormat()));
    }
    return newDate;
  }

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
            || fs.getName().endsWith(".sql") || fs.getName().endsWith(".hql")
            || fs.getName().endsWith(".job") || fs.getName().endsWith(".flow")
            || fs.getName().endsWith(".properties")){
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
//      while ((rn = br.read(data)) > 0){
//        String st = String.valueOf(data, 0, rn);
//        sb.append(st);
//      }
      while ((line = br.readLine()) != null){
        sb.append(line).append("\n");
      }
      String fileStr = sb.toString();

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
      String fullStr = matcher.group();  // fullStr = ${abcd}
      String valueStr = matcher.group(1); // valueStr = abcd
      String timeParam = calculationDate(valueStr);
      if(!"".equals(timeParam)) {
        paramReplaceMap.put(fullStr, timeParam);
      }

    }

    return paramReplaceMap;
  }


  private String calculationDate(String fullVal) {
    fullVal = fullVal.replaceAll("\\s*", "");
    String timeStr = "";
    if (fullVal.matches(re)) {
      String str[] = fullVal.split("\\+|-");
      if (str.length == 1) {
        String key = str[0];
        int num = 0;
        if (defaultDate.containsKey(key) && Date.getDateMap().containsKey(key)) {
          Date date = Date.getDateMap().get(key);
          timeStr = DateUtils.calDate(date, num, defaultDate.get(key));
        }
      } else {
        String key = str[0];
        int num = Integer.parseInt(StringUtils.substringAfter(fullVal, key));
        if (defaultDate.containsKey(key) && Date.getDateMap().containsKey(key)) {
          Date date = Date.getDateMap().get(key);
          timeStr = DateUtils.calDate(date, num, defaultDate.get(key));
        }
      }
    }
    return timeStr;
  }



  //过滤用户设置的参数 排除用户设置过的参数
  private void filterUserParam(Map<String, String> systemParam, ExecutableFlow ef){
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
    for(String userKey : ef.getExecutionOptions().getFlowParameters().keySet()){
      if(null != handleMap.get("${" + userKey + "}")){
        systemParam.remove("${" + userKey + "}");
      }
    }
    // TODO 过滤用户变量
    for(String userKey : ef.getUserProps().keySet()){
      if(null != handleMap.get("${" + userKey + "}")){
        systemParam.remove("${" + userKey + "}");
      }
    }

  }

  //内置参数处理主流程
  public void run(String workingDir, ExecutableFlow ef){
    //用户propertie文件路径集合
    List<String> propPathList = loadAllPropertiesFile(workingDir);
    //获取用户所有配置文件参数
    for(String filePath : propPathList){
      //项目文件中的properties中的配置参数
      getPropMap().putAll(readProperties(filePath));
    }

    try{
      if(ef.getOtherOption().get("run_date") == null){
        if(this.getPropMap().get("run_date") != null && !this.getPropMap().get("run_date").isEmpty()){
          ef.getOtherOption().put("run_date", this.getPropMap().get("run_date"));
        }
      }
    } catch(RuntimeException e){
      logger.error("set rundate failed {}", e);
    }

    //所有脚本的文件地址
    List<String> scriptPathList = loadAllScriptFile(workingDir);
    //初始化默认run_date日期和其他相关日期
    boolean initDateIsSuccess = true;
    try {
      initDate(ef);
    } catch (RuntimeException re){
      initDateIsSuccess = false;
      logger.error("parse run_date failed.", re);
    }
    //循环脚本文件地址
    for(String filePath : scriptPathList){
      //读取单个脚本文件的内容
      String fileStr = readFile(filePath);
      if(initDateIsSuccess) {
        //获取单个脚本中需要替换的参数
        Map<String, String> scriptMap = paramDecompose(fileStr, ef);
        if (scriptMap != null && scriptMap.size() != 0) {
          //循环替换脚本中对应的参数内容
          for (String timeStr : scriptMap.keySet()) {
            fileStr = StringUtils.replace(fileStr, timeStr, scriptMap.get(timeStr));
          }
        }
      }
      //将替换后的内容重新写入到脚本文件中,重写后文件变成unix格式
      FileWrite(filePath, fileStr);
    }
  }

  public Map<String, String> getPropMap() {
    return propMap;
  }


  public static boolean dateFormatCheck(String date){
    Pattern p = Pattern.compile(TIME_TEMPLATE);
    Matcher m = p.matcher(date);
    if(m.matches()) {
      return true;
    } else {
      logger.error(date + "，不是合法的日期格式！");
      return false;
    }
  }


}
