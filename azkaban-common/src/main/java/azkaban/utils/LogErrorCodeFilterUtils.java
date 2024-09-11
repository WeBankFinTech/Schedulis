package azkaban.utils;

import azkaban.log.LogCodeType;
import azkaban.log.LogFilterEntity;
import azkaban.log.OperateType;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Created by zhu on 5/3/18.
 */
public class LogErrorCodeFilterUtils {

  private static final Pattern APPLICATION_ID_PATTERN = Pattern.compile("(application_\\d+_\\d+).*");
  private static final Pattern JOB_ID_PATTERN = Pattern.compile("(job_\\d+_\\d+).*");

//  /**
//   * 日志按级别分割处理方法
//   * @param logData
//   * @param logType
//   * @return
//   */
//  public static String handleLogData(String logData, String logType){
//    String logResult = "";
//
//    String nowCutType = "";
//
//    StringBuilder sb = new StringBuilder();
//
//    String[] handleData = logData.split("\n");
//
//    if("error".equals(logType)){
//      String errorBegin = "";
//      for(String lineLog : handleData){
//        //ERROR日志切割
//        if(lineLog.contains(" ERROR - ")){
//          errorBegin = "START";
//          nowCutType = "ERROR";
//          sb.append(lineLog).append("\n");
//        }else if((lineLog.contains(" ERROR - ") || lineLog.contains(" INFO - ")) && "START".equals(errorBegin) && "ERROR".equals(nowCutType)){
//          errorBegin = "END";
//        }else if("START".equals(errorBegin) && "ERROR".equals(nowCutType)){
//          sb.append(lineLog).append("\n");
//        }
//        //INFO 日志里的异常情况
//        if(lineLog.contains(" INFO - ") && (lineLog.contains("Exception") || lineLog.contains("Error"))){
//          errorBegin = "START";
//          nowCutType = "INFO";
//          sb.append(lineLog).append("\n");
//        }else if(lineLog.contains(" INFO - ") && !lineLog.contains("Exception") && !lineLog.contains(" INFO - \t") && "INFO".equals(nowCutType)){
//          errorBegin = "END";
//        }else if("START".equals(errorBegin) && "INFO".equals(nowCutType)){
//          sb.append(lineLog).append("\n");
//        }
//      }
//    }else if("info".equals(logType)){
//      for(String lineLog : handleData){
//        if(lineLog.contains(" INFO - ") && !lineLog.contains("Exception") && !lineLog.contains(" INFO - \t") ){
//          sb.append(lineLog).append("\n");
//        }
//      }
//    }
//    logResult = sb.toString();
//
//    return logResult;
//  }

  /**
   * 日志按级别分割处理方法
   * @param logData
   * @param logType
   * @return
   */
  public static String handleLogDataFilter(String logData, String logType, List<LogFilterEntity> logFilterList){
    String logResult = "";

    if("error".equals(logType)){
      logResult = handleErrorLog(logData, logFilterList);
    }else if("info".equals(logType)){
      logResult = handleInfoLog(logData, logFilterList);
    }

    return logResult;
  }

  /**
   * 处理Info级别日志过滤
   * @param logData
   * @return
   */
  private static String handleInfoLog(String logData, List<LogFilterEntity> logFilterList){
    String infoLog = "";

    StringBuilder sb = new StringBuilder();

    String[] handleData = logData.split("\n");

    String infoBegin = "";
    for(String lineLog : handleData){
      if(lineLog.contains("Error in query")){
        infoBegin = "ErrorQuery";
      }
      if(lineLog.contains(" INFO - ") && !lineLog.contains("Exception") && !lineLog.contains(" INFO - \t")
          && !lineLog.contains("Error")){
        // 按行匹配日志
        if("ErrorQuery".equals(infoBegin)){
          infoBegin = "End";
        }else{
          lineLog = handleAllLogFilter(lineLog, logFilterList);
          // 如果这行日志被删除 就不用换行了
          if(!"".equals(lineLog)){
            sb.append(lineLog).append("\n");
          }
        }
      }
    }
    infoLog = sb.toString();

    return infoLog;
  }

  /**
   * 处理Error级别日志过滤
   * @param logData
   * @param logFilterList
   * @return
   */
  private static String handleErrorLog(String logData, List<LogFilterEntity> logFilterList){
    String errorLog = "";

    String nowCutType = "";

    StringBuilder sb = new StringBuilder();

    String[] handleData = logData.split("\n");

    String errorBegin = "";
    for(String lineLog : handleData){
      //ERROR日志切割
      if(lineLog.contains(" ERROR - ")){
        errorBegin = "START";
        nowCutType = "ERROR";
        sb.append(handleExecptionLogFilter(lineLog, logFilterList)).append("\n");
        handleExecptionLogFilterAndErrorCode(sb, lineLog, logFilterList);
      }else if((lineLog.contains(" ERROR - ") || lineLog.contains(" INFO - ")) && "START".equals(errorBegin) && "ERROR".equals(nowCutType)){
        errorBegin = "END";
      }else if("START".equals(errorBegin) && "ERROR".equals(nowCutType)){
        sb.append(handleExecptionLogFilter(lineLog, logFilterList)).append("\n");
        handleExecptionLogFilterAndErrorCode(sb, lineLog, logFilterList);
      }
      //INFO 日志里的异常情况
      if(lineLog.contains(" INFO - ") && (lineLog.contains("Exception") || lineLog.contains("Error"))){
        errorBegin = "START";
        nowCutType = "INFO";
        if(lineLog.contains("Error in query")){
          errorBegin = "ErrorQuery";
        }
        sb.append(handleExecptionLogFilter(lineLog, logFilterList)).append("\n");
        handleExecptionLogFilterAndErrorCode(sb, lineLog, logFilterList);
      }else if(lineLog.contains(" INFO - ") && !lineLog.contains("Exception") && !lineLog.contains(" INFO - \t")
          && "INFO".equals(nowCutType) && !"ErrorQuery".equals(errorBegin)){
        errorBegin = "END";
      }else if(("START".equals(errorBegin) || "ErrorQuery".equals(errorBegin)) && "INFO".equals(nowCutType)){
        sb.append(handleExecptionLogFilter(lineLog, logFilterList)).append("\n");
        handleExecptionLogFilterAndErrorCode(sb, lineLog, logFilterList);
        if( "ErrorQuery".equals(errorBegin)){
          errorBegin = "END";
        }
      }
    }

    errorLog = sb.toString();

    return  errorLog;
  }

  /**
   * 处理异常日志的错误码添加
   * @param sb
   * @param logLine
   * @param logFilterList
   */
  private static void handleExecptionLogFilterAndErrorCode(StringBuilder sb, String logLine, List<LogFilterEntity> logFilterList){
    for(LogFilterEntity logFilter : logFilterList){
      if(LogCodeType.ERROR == logFilter.getCodeType()){
        //正则匹配对于的错误关键日志
        List<String> result = regexMathc(logLine, logFilter.getCompareText());
        if(result.size() > 0){
          if(OperateType.ADD == logFilter.getOperateType()){
            sb.append("<font color='red'>");
            sb.append("错误码: " + logFilter.getLogCode() + " 错误说明: "
                + logKeyWordReplace(logFilter.getLogNotice(), result) + "\n");
            sb.append("</font>");
          }
        }
      }
    }
  }

  /**
   * 处理异常日志的过滤器
   * @param logLine
   * @param logFilterList
   */
  private static String handleExecptionLogFilter(String logLine, List<LogFilterEntity> logFilterList){
    String filterLog = logLine;
    for(LogFilterEntity logFilter : logFilterList){
      if(LogCodeType.ERROR == logFilter.getCodeType()){
        if(logLine.contains(logFilter.getCompareText())){
          if(OperateType.REMOVE == logFilter.getOperateType()){
            // 获取需要切割日志的末尾
            int azkabanLog = logLine.indexOf(logFilter.getCompareText()) + logFilter.getCompareText().length();
            // 切割原始日志
            filterLog = logLine.substring(azkabanLog, logLine.length());
          }
        }
      }
    }
    return filterLog;
  }

  /**
   * 处理所有日志的过滤器
   * @param logLine
   * @param logFilterList
   */
  private static String handleAllLogFilter(String logLine, List<LogFilterEntity> logFilterList){
    String filterLog = logLine;
    for(LogFilterEntity logFilter : logFilterList){
      if(LogCodeType.INFO == logFilter.getCodeType()){
        if(filterLog.contains(logFilter.getCompareText())){
          if(OperateType.REMOVE == logFilter.getOperateType()){
            // 获取需要切割日志的末尾
            int azkabanLog = filterLog.indexOf(logFilter.getCompareText()) + logFilter.getCompareText().length();
            // 切割原始日志
            filterLog = filterLog.substring(azkabanLog, filterLog.length());

          }
          if(OperateType.REMOVE_ALL == logFilter.getOperateType()){
            //删除整行日志
            filterLog = "";
          }
        }
      }
    }
    return filterLog;
  }

  //获取匹配到的字符串集合
  private static List<String> regexMathc(String linglog, String regex){
    List<String> result = new ArrayList<>();
    Pattern datePattern = Pattern.compile(regex);
    Matcher dateMatcher = datePattern.matcher(linglog);
    while(dateMatcher.find()) {
      result.add(dateMatcher.group().trim());
    }
    return result;
  }

  //匹配到提示字符串中
  private static String logKeyWordReplace(String logNotic, List<String> keyWord){
    for(int i=0; i<keyWord.size(); i++){
      logNotic = logNotic.replace("#" + i +"#", keyWord.get(i));
    }
    return logNotic;
  }

  /**
   * 给错误日志都加上红色
   * @param logData
   * @return
   */
  public static String handleErrorLogMarkedRed(String logData, boolean infoLogRedSwitch){
    String logResult = "";

    logResult = errorLogRedFont(logData, infoLogRedSwitch);

    return logResult;
  }

  /**
   * 处理Error级别日志过滤
   * @param logData
   * @return
   */
  private static String errorLogRedFont(String logData, boolean infoLogRedSwitch){
    String errorLog = "";

    String nowCutType = "";

    StringBuilder sb = new StringBuilder();

    String[] handleData = logData.split("\n");

    String errorBegin = "";
    for(String lineLog : handleData){
      //ERROR日志切割
      if(lineLog.contains(" ERROR - ")){
        errorBegin = "START";
        nowCutType = "ERROR";
        handleExecptionLogRedFont(sb, lineLog);
      }else if((lineLog.contains(" ERROR - ") || lineLog.contains(" INFO - ")) && "START".equals(errorBegin) && "ERROR".equals(nowCutType)){
        errorBegin = "END";
        sb.append(lineLog).append("\n");
      }else if("START".equals(errorBegin) && "ERROR".equals(nowCutType)){
        handleExecptionLogRedFont(sb, lineLog);
      } //INFO 日志里的异常情况
      else if( infoLogRedSwitch && (lineLog.contains(" INFO - ") && (lineLog.contains("Exception") || lineLog.contains("Error") || lineLog.contains("Execution")))){
        errorBegin = "START";
        nowCutType = "INFO";
        if(lineLog.contains("Error in query")){
          errorBegin = "ErrorQuery";
        }
        handleExecptionLogRedFont(sb, lineLog);
      }else if( infoLogRedSwitch && (lineLog.contains(" INFO - ") && !lineLog.contains("Exception") && !lineLog.contains(" INFO - \t")
          && "INFO".equals(nowCutType) && !"ErrorQuery".equals(errorBegin))){
        errorBegin = "END";
        sb.append(lineLog).append("\n");
      }else if( infoLogRedSwitch && (("START".equals(errorBegin) || "ErrorQuery".equals(errorBegin)) && "INFO".equals(nowCutType))){
        handleExecptionLogRedFont(sb, lineLog);
        if( "ErrorQuery".equals(errorBegin)){
          errorBegin = "END";
        }
      }
      else {
        sb.append(lineLog).append("\n");
      }
    }

    errorLog = sb.toString();

    return  errorLog;
  }


  /**
   * 处理异常日志的过滤器
   * @param sb
   * @param lineLog
   */
  private static void handleExecptionLogRedFont(StringBuilder sb, String lineLog){
    sb.append("<font color='red'>");
    sb.append(lineLog);
    sb.append("</font>").append("\n");
  }


  /**
   * 获取Yarn日志中的 application 号
   * @param logData
   * @return
   */
  public static String handleYarnLogDataFilter(String logData){


    String logResult = "";

    StringBuilder sb = new StringBuilder();

    Set<String> yarnSet = new HashSet<>();
    Set<String> applicationSet = new HashSet<>();

    String[] handleData = logData.split("\n");

    //application开头的号
    for(String lineLog : handleData){
      // 按行匹配日志
      Matcher m = APPLICATION_ID_PATTERN.matcher(lineLog);
      if (m.find()) {
        String appId = m.group(1);
        //sb.append(appId).append("\n");
        yarnSet.add(appId);
        String appStr = appId.replace("application", "job");
        applicationSet.add(appStr);
      }
    }

    //job开头的号
    for(String lineLog : handleData){
      // 按行匹配日志
      Matcher m = JOB_ID_PATTERN.matcher(lineLog);
      if (m.find()) {
        String appId = m.group(1);
        //sb.append(appId).append("\n");
        if (!applicationSet.contains(appId)) {
          yarnSet.add(appId);
        }
      }
    }

    for(String appId : yarnSet){
      sb.append(appId).append("\n");
    }

    logResult = sb.toString();

    return logResult;
  }



}
