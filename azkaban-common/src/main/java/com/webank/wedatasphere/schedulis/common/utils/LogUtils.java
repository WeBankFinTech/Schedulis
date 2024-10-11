package com.webank.wedatasphere.schedulis.common.utils;

import com.webank.wedatasphere.schedulis.common.log.LogFilterEntity;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.core.Appender;
import org.apache.logging.log4j.core.Filter;
import org.apache.logging.log4j.core.LoggerContext;
import org.apache.logging.log4j.core.appender.FileAppender;
import org.apache.logging.log4j.core.appender.RollingFileAppender;
import org.apache.logging.log4j.core.appender.rolling.*;
import org.apache.logging.log4j.core.config.AppenderRef;
import org.apache.logging.log4j.core.config.Configuration;
import org.apache.logging.log4j.core.config.LoggerConfig;
import org.apache.logging.log4j.core.filter.CompositeFilter;
import org.apache.logging.log4j.core.filter.RegexFilter;
import org.apache.logging.log4j.core.layout.PatternLayout;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.List;

public class LogUtils {

  private static org.slf4j.Logger logger = LoggerFactory.getLogger(LogUtils.class);

  private static final LoggerContext ctx = LoggerContext.getContext(false);
  private static final Configuration config = ctx.getConfiguration();

  public static void createFlowLog(String logDir, String logFileName, String logName){
    if (config.getAppender(logName) != null) {
      return;
    }
    final PatternLayout layout = PatternLayout.newBuilder()
        .withCharset(Charset.forName("UTF-8"))
        .withConfiguration(config)
        .withPattern("%d{dd-MM-yyyy HH:mm:ss z} %c{1} %p - %m\n")
        .build();

    final Appender appender = FileAppender.newBuilder()
        .withName(logName)
        .withImmediateFlush(true)
        .withFileName(String.format(logDir + File.separator + "%s", logFileName))
        .withLayout(layout)
        .build();

    appender.start();
    config.addAppender(appender);

    AppenderRef[] refs = new AppenderRef[]{AppenderRef.createAppenderRef(logName, Level.ALL, null)};
    LoggerConfig loggerConfig = LoggerConfig.createLogger(false, Level.ALL, logName, "true", refs, null, config, null);
    loggerConfig.addAppender(appender, Level.ALL, null);
    config.addLogger(logName, loggerConfig);
    ctx.updateLoggers(config);
  }

  public static void createJobLog(String logDir, String logFileName, String logName, String logFileSize, int logFileNum, List<LogFilterEntity> logFilterEntityList){

    if (config.getAppender(logName) != null) {
      return;
    }

    final PatternLayout layout = PatternLayout.newBuilder()
        .withCharset(Charset.forName("UTF-8"))
        .withConfiguration(config)
        .withPattern("%d{dd-MM-yyyy HH:mm:ss z} %c{1} %p - %m\n")
        .build();

    final TriggeringPolicy tp = SizeBasedTriggeringPolicy.createPolicy(logFileSize);

    final CompositeTriggeringPolicy policyComposite = CompositeTriggeringPolicy.createPolicy(tp);

    final DefaultRolloverStrategy defaultRolloverStrategy = DefaultRolloverStrategy.newBuilder()
        .withMax(String.valueOf(logFileNum))
        .build();

    CompositeFilter compositeFilter = null;
    if(CollectionUtils.isNotEmpty(logFilterEntityList)) {
      List<Filter> filterList = new ArrayList<>();
      for (int i = 0; i < logFilterEntityList.size(); i++) {
        try {
          LogFilterEntity logFilterEntity = logFilterEntityList.get(i);
          Filter filter;
          if (i < logFilterEntityList.size() - 1) {
            filter = RegexFilter.createFilter(logFilterEntity.getCompareText(), null, false, Filter.Result.DENY, Filter.Result.NEUTRAL);
          } else {
            filter = RegexFilter.createFilter(logFilterEntity.getCompareText(), null, false, Filter.Result.DENY, Filter.Result.ACCEPT);
          }
          filterList.add(filter);
        } catch (Exception e) {
          logger.warn("create log filter failed.", e);
        }
      }
      compositeFilter = CompositeFilter.createFilters(filterList.toArray(new Filter[filterList.size()]));
    }

    final Appender appender = RollingFileAppender.newBuilder()
        .withName(logName)
        .withImmediateFlush(true)
        .withFileName(String.format(logDir + File.separator + "%s", logFileName))
        .withFilePattern(logDir + File.separator + logFileName + ".%i")
        .withLayout(layout)
        .withPolicy(policyComposite)
        .withStrategy(defaultRolloverStrategy)
        .build();

    appender.start();
    config.addAppender(appender);

    AppenderRef[] refs = new AppenderRef[]{AppenderRef.createAppenderRef(logName, Level.ALL, null)};
    LoggerConfig loggerConfig = LoggerConfig.createLogger(false, Level.ALL, logName, "true", refs, null, config, null);
    loggerConfig.addAppender(appender, Level.ALL, compositeFilter);
    config.addLogger(logName, loggerConfig);
    ctx.updateLoggers(config);
  }

  public static void stopLog(String logName){
    if (config.getAppender(logName) == null) {
      return;
    }
    config.getAppender(logName).stop();
    config.getLoggerConfig(logName).removeAppender(logName);
    config.removeLogger(logName);
    ctx.updateLoggers();
  }

}
