package azkaban.jobExecutor.utils;

import azkaban.executor.ExecutableFlow;
import azkaban.executor.ExecutorManagerException;
import azkaban.flow.CommonJobProperties;
import azkaban.flow.FlowProps;
import azkaban.project.ProjectLoader;
import azkaban.utils.DateUtils;
import azkaban.utils.Props;
import azkaban.utils.PropsUtils;
import org.apache.commons.collections.MapUtils;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang.StringUtils;
import org.joda.time.LocalDate;
import org.joda.time.LocalDateTime;
import org.joda.time.format.DateTimeFormat;
import org.joda.time.format.DateTimeFormatter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.util.*;
import java.util.Map.Entry;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Created by zhu on 11/28/17.
 */
public class SystemBuiltInParamReplacer {

  private static final Logger logger = LoggerFactory.getLogger(SystemBuiltInParamReplacer.class);

  public static final String TIME_TEMPLATE = "(\\d{4}\\.\\d{2}\\.\\d{2}|\\d{4}/\\d{2}/\\d{2}|\\d{4}-\\d{2}-\\d{2}|\\d{4}\\d{2}\\d{2})";

  private Map<String, String> propMap = new HashMap<>();

  private Map<String, LocalDateTime> defaultDate = new HashMap<>();

  private static String re = "[a-z_]+((\\+|-)[1-9][0-9]*){0,1}";

  private ProjectLoader projectLoader;

  public static final Pattern DECOMPOSE_PATTERN = Pattern.compile("\\$\\{([^\\}]+)\\}");

  public SystemBuiltInParamReplacer() {
    super();
  }

  public SystemBuiltInParamReplacer(ProjectLoader projectLoader) {
    this.projectLoader = projectLoader;
  }

  private void initDate(ExecutableFlow executableFlow) throws RuntimeException {
    LocalDateTime runDate = null;
    LocalDateTime runDateHour = null;
    DateTimeFormatter dateTimeFormatter = DateTimeFormat.forPattern("yyyyMMdd");
    //历史重跑
    if (2 == executableFlow.getFlowType()) {
      runDate = new LocalDateTime(
          Long.valueOf(executableFlow.getRepeatOption().get("startTimeLong"))).minusDays(1);
    } else {
      if (null != executableFlow.getExecutionOptions().getFlowParameters()
          .get(Date.RUN_DATE.getValue())) {
        runDate = LocalDateTime.parse(
            executableFlow.getExecutionOptions().getFlowParameters().get(Date.RUN_DATE.getValue()),
            dateTimeFormatter);
      } else if (this.propMap.get(Date.RUN_DATE.getValue()) != null) {
        String tmp = this.propMap.get(Date.RUN_DATE.getValue()).replaceAll("[\"'./-]", "");
        runDate = LocalDateTime.parse(tmp, dateTimeFormatter);
      } else if (executableFlow.getLastParameterTime() != -1) {
        runDate = new LocalDateTime(executableFlow.getLastParameterTime()).minusDays(1);
      } else {
        runDate = new LocalDateTime(executableFlow.getSubmitTime()).minusDays(1);
      }

      if (null != executableFlow.getExecutionOptions().getFlowParameters()
          .get(Date.RUN_DATE_HOUR.getValue())) {
        runDateHour = LocalDateTime.parse(executableFlow.getExecutionOptions().getFlowParameters()
                .get(Date.RUN_DATE_HOUR.getValue()),
            DateTimeFormat.forPattern(Date.RUN_DATE_HOUR.getFormat()));
      } else if (this.propMap.get(Date.RUN_DATE_HOUR.getValue()) != null) {
        String tmp = this.propMap.get(Date.RUN_DATE_HOUR.getValue()).replaceAll("[\"'./-]", "");
        runDateHour = LocalDateTime.parse(tmp,
            DateTimeFormat.forPattern(Date.RUN_DATE_HOUR.getFormat()));
      }

      if (executableFlow.getOtherOption().containsKey("event_schedule_save_key")) {
        Map<String, String> map = (Map<String, String>) executableFlow.getOtherOption()
            .get("event_schedule_save_key");
        if (MapUtils.isNotEmpty(map)) {
          for (Entry<String, String> entry : map.entrySet()) {
            if (Date.RUN_DATE.getValue().equals(entry.getKey())) {
              runDate = LocalDateTime
                  .parse(entry.getValue(), DateTimeFormat.forPattern(Date.RUN_DATE.getFormat()));
            }
            if (Date.RUN_DATE_HOUR.getValue().equals(entry.getKey())) {
              runDateHour = LocalDateTime.parse(entry.getValue(),
                  DateTimeFormat.forPattern(Date.RUN_DATE_HOUR.getFormat()));
            }
          }
        }
      }
    }

    //用于前端显示
    executableFlow.setRunDate(runDate.toString("yyyyMMdd"));

    defaultDate.put(Date.RUN_DATE.getValue(), runDate);
    defaultDate.put(Date.RUN_DATE_STD.getValue(), runDate);
    LocalDateTime runToday = runDate.plusDays(1);
    LocalDateTime day4LastMonth = runToday.minusMonths(1);
    defaultDate.put(Date.RUN_TODAY.getValue(), runToday);
    defaultDate.put(Date.RUN_TODAY_STD.getValue(), runToday);

    if (runDateHour == null) {
      runDateHour = new LocalDateTime()
          .withDate(runToday.getYear(), runToday.getMonthOfYear(), runToday.getDayOfMonth());
    }
    defaultDate.put(Date.RUN_DATE_HOUR.getValue(), runDateHour);
    defaultDate.put(Date.RUN_DATE_HOUR_STD.getValue(), runDateHour);

    defaultDate.put(Date.RUN_MON.getValue(), runToday);
    defaultDate.put(Date.RUN_MON_STD.getValue(), runToday);

    defaultDate.put(Date.RUN_MONTH_BEGIN.getValue(), runDate.dayOfMonth().withMinimumValue());
    defaultDate.put(Date.RUN_MONTH_BEGIN_STD.getValue(), runDate.dayOfMonth().withMinimumValue());
    //添加参数 RUN_MONTH_NOW_BEGIN RUN_MONTH_NOW_BEGIN_STD 根据 day4LastMonth 计算
    defaultDate.put(Date.RUN_MONTH_NOW_BEGIN.getValue(),
        day4LastMonth.dayOfMonth().withMinimumValue());
    defaultDate.put(Date.RUN_MONTH_NOW_BEGIN_STD.getValue(),
        day4LastMonth.dayOfMonth().withMinimumValue());

    defaultDate.put(Date.RUN_MONTH_END.getValue(), runDate.dayOfMonth().withMaximumValue());
    defaultDate.put(Date.RUN_MONTH_END_STD.getValue(), runDate.dayOfMonth().withMaximumValue());
    //添加参数 RUN_MONTH_NOW_END RUN_MONTH_NOW_END_STD 根据 day4LastMonth 计算
    defaultDate.put(Date.RUN_MONTH_NOW_END.getValue(),
        day4LastMonth.dayOfMonth().withMaximumValue());
    defaultDate.put(Date.RUN_MONTH_NOW_END_STD.getValue(),
        day4LastMonth.dayOfMonth().withMaximumValue());

    defaultDate.put(Date.RUN_QUARTER_BEGIN.getValue(), DateUtils.getQuarterBegin(runDate));
    defaultDate.put(Date.RUN_QUARTER_END.getValue(), DateUtils.getQuarterEnd(runDate));
    //添加参数 RUN_QUARTER_NOW_BEGIN RUN_QUARTER_NOW_END 根据 day4LastMonth 计算
    defaultDate.put(Date.RUN_QUARTER_NOW_BEGIN.getValue(),
        DateUtils.getQuarterBegin(day4LastMonth));
    defaultDate.put(Date.RUN_QUARTER_NOW_END.getValue(), DateUtils.getQuarterEnd(day4LastMonth));

    defaultDate.put(Date.RUN_HALF_YEAR_BEGIN.getValue(), DateUtils.getHalfYearBegin(runDate));
    defaultDate.put(Date.RUN_HALF_YEAR_END.getValue(), DateUtils.getHalfYearEnd(runDate));
    //添加参数 RUN_HALF_YEAR_NOW_BEGIN RUN_HALF_YEAR_NOW_END 根据 day4LastMonth 计算
    defaultDate.put(Date.RUN_HALF_YEAR_NOW_BEGIN.getValue(),
        DateUtils.getHalfYearBegin(day4LastMonth));
    defaultDate.put(Date.RUN_HALF_YEAR_NOW_END.getValue(), DateUtils.getHalfYearEnd(day4LastMonth));

    defaultDate.put(Date.RUN_YEAR_BEGIN.getValue(), DateUtils.getYearBegin(runDate));
    defaultDate.put(Date.RUN_YEAR_END.getValue(), DateUtils.getYearEnd(runDate));
    //添加参数 RUN_YEAR_NOW_BEGIN RUN_YEAR_NOW_END 根据 day4LastMonth 计算
    defaultDate.put(Date.RUN_YEAR_NOW_BEGIN.getValue(), DateUtils.getYearBegin(day4LastMonth));
    defaultDate.put(Date.RUN_YEAR_NOW_END.getValue(), DateUtils.getYearEnd(day4LastMonth));

    defaultDate.put(Date.RUN_LAST_MONTH_END.getValue(), DateUtils.getLastMonthEnd(runDate));
    defaultDate.put(Date.RUN_LAST_QUARTER_END.getValue(), DateUtils.getLastQuarterEnd(runDate));
    defaultDate.put(Date.RUN_LAST_YEAR_END.getValue(), DateUtils.getLastYearEnd(runDate));
    //添加参数 RUN_LAST_MONTH_NOW_END RUN_LAST_QUARTER_NOW_END RUN_LAST_YEAR_NOW_END 根据 day4LastMonth 计算
    defaultDate.put(Date.RUN_LAST_MONTH_NOW_END.getValue(),
        DateUtils.getLastMonthEnd(day4LastMonth));
    defaultDate.put(Date.RUN_LAST_QUARTER_NOW_END.getValue(),
        DateUtils.getLastQuarterEnd(day4LastMonth));
    defaultDate.put(Date.RUN_LAST_YEAR_NOW_END.getValue(), DateUtils.getLastYearEnd(day4LastMonth));

    defaultDate.put(Date.RUN_QUARTER_BEGIN_STD.getValue(), DateUtils.getQuarterBegin(runDate));
    defaultDate.put(Date.RUN_QUARTER_END_STD.getValue(), DateUtils.getQuarterEnd(runDate));
    //添加参数 RUN_QUARTER_NOW_BEGIN_STD RUN_QUARTER_NOW_END_STD 根据 day4LastMonth 计算
    defaultDate.put(Date.RUN_QUARTER_NOW_BEGIN_STD.getValue(),
        DateUtils.getQuarterBegin(day4LastMonth));
    defaultDate.put(Date.RUN_QUARTER_NOW_END_STD.getValue(),
        DateUtils.getQuarterEnd(day4LastMonth));

    defaultDate.put(Date.RUN_HALF_YEAR_BEGIN_STD.getValue(), DateUtils.getHalfYearBegin(runDate));
    defaultDate.put(Date.RUN_HALF_YEAR_END_STD.getValue(), DateUtils.getHalfYearEnd(runDate));
    //添加参数 RUN_HALF_YEAR_NOW_BEGIN_STD RUN_HALF_YEAR_NOW_END_STD 根据 day4LastMonth 计算
    defaultDate.put(Date.RUN_HALF_YEAR_NOW_BEGIN_STD.getValue(),
        DateUtils.getHalfYearBegin(day4LastMonth));
    defaultDate.put(Date.RUN_HALF_YEAR_NOW_END_STD.getValue(),
        DateUtils.getHalfYearEnd(day4LastMonth));

    defaultDate.put(Date.RUN_YEAR_BEGIN_STD.getValue(), DateUtils.getYearBegin(runDate));
    defaultDate.put(Date.RUN_YEAR_END_STD.getValue(), DateUtils.getYearEnd(runDate));
    //添加参数 RUN_YEAR_NOW_BEGIN_STD RUN_YEAR_NOW_END_STD 根据 day4LastMonth 计算
    defaultDate.put(Date.RUN_YEAR_NOW_BEGIN_STD.getValue(), DateUtils.getYearBegin(day4LastMonth));
    defaultDate.put(Date.RUN_YEAR_NOW_END_STD.getValue(), DateUtils.getYearEnd(day4LastMonth));

    defaultDate.put(Date.RUN_LAST_MONTH_END_STD.getValue(), DateUtils.getLastMonthEnd(runDate));
    defaultDate.put(Date.RUN_LAST_QUARTER_END_STD.getValue(), DateUtils.getLastQuarterEnd(runDate));
    defaultDate.put(Date.RUN_LAST_YEAR_END_STD.getValue(), DateUtils.getLastYearEnd(runDate));
    //添加参数 RUN_LAST_MONTH_NOW_END_STD RUN_LAST_QUARTER_NOW_END_STD RUN_LAST_YEAR_NOW_END_STD 根据 day4LastMonth 计算
    defaultDate.put(Date.RUN_LAST_MONTH_NOW_END_STD.getValue(),
        DateUtils.getLastMonthEnd(day4LastMonth));
    defaultDate.put(Date.RUN_LAST_QUARTER_NOW_END_STD.getValue(),
        DateUtils.getLastQuarterEnd(day4LastMonth));
    defaultDate.put(Date.RUN_LAST_YEAR_NOW_END_STD.getValue(),
        DateUtils.getLastYearEnd(day4LastMonth));

    if (2 != executableFlow.getFlowType()) {
      Date date[] = {
          Date.RUN_DATE_STD,
          Date.RUN_DATE_HOUR_STD,
          Date.RUN_TODAY,
          Date.RUN_TODAY_STD,
          Date.RUN_MON,
          Date.RUN_MON_STD,
          Date.RUN_MONTH_BEGIN,
          Date.RUN_MONTH_BEGIN_STD,
          Date.RUN_MONTH_NOW_BEGIN,
          Date.RUN_MONTH_NOW_BEGIN_STD,
          Date.RUN_MONTH_END,
          Date.RUN_MONTH_END_STD,
          Date.RUN_MONTH_NOW_END,
          Date.RUN_MONTH_NOW_END_STD,
          Date.RUN_QUARTER_BEGIN,
          Date.RUN_QUARTER_BEGIN_STD,
          Date.RUN_QUARTER_NOW_BEGIN,
          Date.RUN_QUARTER_NOW_BEGIN_STD,
          Date.RUN_QUARTER_END,
          Date.RUN_QUARTER_END_STD,
          Date.RUN_QUARTER_NOW_END,
          Date.RUN_QUARTER_NOW_END_STD,
          Date.RUN_LAST_QUARTER_END,
          Date.RUN_LAST_QUARTER_END_STD,
          Date.RUN_LAST_QUARTER_NOW_END,
          Date.RUN_LAST_QUARTER_NOW_END_STD,
          Date.RUN_HALF_YEAR_BEGIN,
          Date.RUN_HALF_YEAR_BEGIN_STD,
          Date.RUN_HALF_YEAR_NOW_BEGIN,
          Date.RUN_HALF_YEAR_NOW_BEGIN_STD,
          Date.RUN_HALF_YEAR_END,
          Date.RUN_HALF_YEAR_END_STD,
          Date.RUN_HALF_YEAR_NOW_END,
          Date.RUN_HALF_YEAR_NOW_END_STD,
          Date.RUN_YEAR_BEGIN,
          Date.RUN_YEAR_BEGIN_STD,
          Date.RUN_YEAR_NOW_BEGIN,
          Date.RUN_YEAR_NOW_BEGIN_STD,
          Date.RUN_YEAR_END,
          Date.RUN_YEAR_END_STD,
          Date.RUN_YEAR_NOW_END,
          Date.RUN_YEAR_NOW_END_STD,
          Date.RUN_LAST_MONTH_END,
          Date.RUN_LAST_MONTH_END_STD,
          Date.RUN_LAST_MONTH_NOW_END,
          Date.RUN_LAST_MONTH_NOW_END_STD,
          Date.RUN_LAST_YEAR_END,
          Date.RUN_LAST_YEAR_END_STD,
          Date.RUN_LAST_YEAR_NOW_END,
          Date.RUN_LAST_YEAR_NOW_END_STD
      };
      for (Date item : date) {
        LocalDateTime newDate;
        newDate = hasNewDate(executableFlow, item);
        if (newDate != null) {
          defaultDate.put(item.getValue(), newDate);
        }
      }
    }

  }

  private LocalDateTime hasNewDate(ExecutableFlow executableFlow, Date dateType) {
    LocalDateTime newDate = null;
    if (executableFlow.getOtherOption().containsKey("event_schedule_save_key")) {
      Map<String, String> map = (Map<String, String>) executableFlow.getOtherOption()
          .get("event_schedule_save_key");
      if (MapUtils.isNotEmpty(map)) {
        for (Entry<String, String> entry : map.entrySet()) {
          if (dateType.getValue().equals(entry.getKey())) {
            return LocalDateTime.parse(entry.getValue(),
                DateTimeFormat.forPattern(dateType.getFormat()));
          }
        }
      }
    }
    if (null != executableFlow.getExecutionOptions().getFlowParameters()
        .get(dateType.getValue())) {
      newDate = LocalDateTime.parse(
          executableFlow.getExecutionOptions().getFlowParameters().get(dateType.getValue()),
          DateTimeFormat.forPattern(dateType.getFormat()));
    } else if (this.propMap.get(dateType.getValue()) != null) {
      newDate = LocalDateTime.parse(this.propMap.get(dateType.getValue()),
          DateTimeFormat.forPattern(dateType.getFormat()));
    }

    return newDate;
  }

  /**
   * 重写文件内容,先删后写
   * @param filePath
   * @param fileStr
   * @throws ExecutorManagerException
   */
  private void fileWrite(String filePath, String fileStr, int execId) throws ExecutorManagerException {
    FileWriter fw = null;
    //TODO need remove
    logger.info("Flow {} to replace file {}", execId, filePath);
    File file = new File(filePath);
    if (file.isFile()) {
      if (!file.delete()) {
        throw new ExecutorManagerException("Failed to delete filePath: " + filePath);
      }
    }
    try {
      fw = new FileWriter(filePath);
      //写入到文件
      fw.write(fileStr);
    } catch (Exception e) {
      logger.error("写入脚本文件异常！", e);
    } finally {
      if (fw != null) {
        IOUtils.closeQuietly(fw);
      }
    }
  }

  private void fileWrite(String filePath, String fileStr) throws ExecutorManagerException {
    FileWriter fw = null;
    try {
      fw = new FileWriter(filePath);
      //写入到文件
      fw.write(fileStr);
    } catch (Exception e) {
      logger.error("写入脚本文件异常！", e);
    } finally {
      if(fw != null){
        IOUtils.closeQuietly(fw);
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
      logger.info("{} not exists", dirPath);
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
      logger.error("文件地址: " + dirPath + "不存在！");
    }
    File[] fa = f.listFiles();
    for (File fs : fa) {
      if (fs.isDirectory()) {
        findScriptFilePath(fs.getPath(), filePathList);
      } else {
        if (fs.getName().endsWith(".py") || fs.getName().endsWith(".sh")
            || fs.getName().endsWith(".sql") || fs.getName().endsWith(".hql")
            || fs.getName().endsWith(".job") || fs.getName().endsWith(".flow")
            || fs.getName().endsWith(".properties")) {
          filePathList.add(fs.getPath());
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
        for (Entry<Object, Object> entry : prop.entrySet()) {
          String key = String.valueOf(entry.getKey());
          String value = String.valueOf(entry.getValue());
          propMap.put(key, value);
        }
      }
    } catch (Exception ex) {
      logger.error("读取properties配置文件异常！", ex);
    } finally {
      if (input != null) {
        IOUtils.closeQuietly(input);
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
      while ((line = br.readLine()) != null) {
        sb.append(line).append("\n");
      }

      return sb.toString();
    } catch (Exception e) {
      logger.error("读取脚本文件异常！", e);
    } finally {
      if(br != null){
        IOUtils.closeQuietly(br);
      }
    }
    return sb.toString();
  }



  //用脚本内容字符串中解析出需要替换的参数
  public Map<String, String> paramDecompose(String fileStr, ExecutableFlow executableFlow) {

    Map<String, String> paramReplaceMap = new HashMap<>();

    if (StringUtils.isEmpty(fileStr)) {
      return paramReplaceMap;
    }

    Matcher matcher = DECOMPOSE_PATTERN.matcher(fileStr);
    while(matcher.find()){
      // fullStr = ${abcd}
      String fullStr = matcher.group();
      // valueStr = abcd
      String valueStr = matcher.group(1);
      String timeParam;
      if (CommonJobProperties.FLOW_SUBMIT_USER.equals(valueStr)) {
        String submitUser = executableFlow.getSubmitUser();
        if (!"".equals(submitUser)) {
          paramReplaceMap.put(fullStr, submitUser);
        }
      } else {
        timeParam = calculationDate(valueStr);
        if (!"".equals(timeParam)) {
          paramReplaceMap.put(fullStr, timeParam);
        }
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
  private void filterUserParam(Map<String, String> systemParam, ExecutableFlow ef) {
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
    for (String userKey : ef.getExecutionOptions().getFlowParameters().keySet()) {
      if (null != handleMap.get("${" + userKey + "}")) {
        systemParam.remove("${" + userKey + "}");
      }
    }
    // TODO 过滤用户变量
    for (String userKey : ef.getUserProps().keySet()) {
      if (null != handleMap.get("${" + userKey + "}")) {
        systemParam.remove("${" + userKey + "}");
      }
    }

  }

  //内置参数处理主流程
  public void run(String workingDir, ExecutableFlow ef) throws ExecutorManagerException {
    if (ef.getLastExecId() != -1 && this.projectLoader != null) {
      for (final FlowProps fprops : ef.getFlowProps()) {
        Props props = this.projectLoader.fetchProjectProperty(ef.getProjectId(),
            ef.getLastVersion(), fprops.getSource());
        if (props != null) {
          getPropMap().putAll(PropsUtils.toStringMap(props, true));
        }
      }
    } else {
      //用户 properties 文件路径集合
      List<String> propPathList = loadAllPropertiesFile(workingDir);
      //获取用户所有配置文件参数
      for (String filePath : propPathList) {
        //项目文件中的properties中的配置参数
        getPropMap().putAll(readProperties(filePath));
      }
    }

    try {
      if (ef.getOtherOption().get("run_date") == null) {
        if (this.getPropMap().get("run_date") != null && !this.getPropMap().get("run_date")
            .isEmpty()) {
          ef.getOtherOption().put("run_date", this.getPropMap().get("run_date"));
        }
      }
    } catch (RuntimeException e) {
      logger.error("set rundate failed {}", e);
    }

    //所有脚本的文件地址
    List<String> scriptPathList = loadAllScriptFile(workingDir);
    //初始化默认run_date日期和其他相关日期
    boolean initDateIsSuccess = true;
    try {
      initDate(ef);
    } catch (RuntimeException re) {
      initDateIsSuccess = false;
      // 解决表达式显示问题
      ef.setRunDate(new LocalDate(ef.getSubmitTime()).minusDays(1).toString("yyyyMMdd"));
      logger.error("parse run_date failed.", re);
    }
    //循环脚本文件地址
    for(String filePath : scriptPathList) {
      //读取单个脚本文件的内容
      String fileStr = readFile(filePath);
      boolean fileUpdateFlag = false;
      if (initDateIsSuccess) {
        //获取单个脚本中需要替换的参数
        Map<String, String> scriptMap = paramDecompose(fileStr, ef);
        if (scriptMap != null && !scriptMap.isEmpty()) {
          fileUpdateFlag = true;
          //循环替换脚本中对应的参数内容
          for (String varString : scriptMap.keySet()) {
            fileStr = StringUtils.replace(fileStr, varString, scriptMap.get(varString));
          }
        }
      }
      // 只有文件有修改才需要重新写文件
      // 将替换后的内容重新写入到脚本文件中,重写后文件变成unix格式
      //对于文件缓存开启的，应该只对修改的问题就进行重写，其他文件不做操作
      //对于文件缓存没有开启的，因为存在存量项目有windows格式的问题就，所以全部需要重写
      if (ef.getExecutionOptions() != null && ef.getExecutionOptions().isEnabledCacheProjectFiles()) {
        if (fileUpdateFlag) {
          fileWrite(filePath, fileStr, ef.getExecutionId());
        }
      } else {
        fileWrite(filePath, fileStr);
      }

    }
  }

  public Map<String, String> getPropMap() {
    return propMap;
  }


  public static boolean dateFormatCheck(String date) {
    Pattern p = Pattern.compile(TIME_TEMPLATE);
    Matcher m = p.matcher(date);
    if (m.matches()) {
      return true;
    } else {
      logger.error(date + "，不是合法的日期格式！");
      return false;
    }
  }


}
