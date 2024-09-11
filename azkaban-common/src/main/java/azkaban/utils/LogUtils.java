package azkaban.utils;

import azkaban.Constants.ConfigurationKeys;
import azkaban.log.LogFilterEntity;
import azkaban.server.AbstractAzkabanServer;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.io.IOUtils;
import org.apache.hadoop.fs.FSDataInputStream;
import org.apache.hadoop.fs.FileSystem;
import org.apache.hadoop.fs.Path;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.core.Appender;
import org.apache.logging.log4j.core.Filter;
import org.apache.logging.log4j.core.LoggerContext;
import org.apache.logging.log4j.core.appender.FileAppender;
import org.apache.logging.log4j.core.appender.RollingFileAppender;
import org.apache.logging.log4j.core.appender.rolling.CompositeTriggeringPolicy;
import org.apache.logging.log4j.core.appender.rolling.DefaultRolloverStrategy;
import org.apache.logging.log4j.core.appender.rolling.SizeBasedTriggeringPolicy;
import org.apache.logging.log4j.core.appender.rolling.TriggeringPolicy;
import org.apache.logging.log4j.core.config.AbstractConfiguration;
import org.apache.logging.log4j.core.config.AppenderRef;
import org.apache.logging.log4j.core.config.Configuration;
import org.apache.logging.log4j.core.config.LoggerConfig;
import org.apache.logging.log4j.core.filter.CompositeFilter;
import org.apache.logging.log4j.core.filter.RegexFilter;
import org.apache.logging.log4j.core.layout.PatternLayout;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.Charset;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

public class LogUtils {

  private static final org.slf4j.Logger logger = LoggerFactory.getLogger(LogUtils.class);

  private static final LoggerContext LOGGER_CONTEXT = LoggerContext.getContext(false);
  private static final Configuration CONFIG = LOGGER_CONTEXT.getConfiguration();

  public static org.apache.hadoop.conf.Configuration conf;


  static {
    String hadoopConfDir = AbstractAzkabanServer.getAzkabanProperties()
        .getString(ConfigurationKeys.HADOOP_CONF_DIR_PATH,
            "/appcom/config/hadoop-config");
    File hadoopConfPath = new File(hadoopConfDir);
    if (!hadoopConfPath.exists() || hadoopConfPath.isFile()) {
      throw new RuntimeException(
          "Create hadoop configuration failed, path " + hadoopConfDir + " not exists.");
    }
    // 创建 Hadoop 配置对象
    conf = new org.apache.hadoop.conf.Configuration();

    conf.addResource(new Path(
        Paths.get(hadoopConfDir, "core-site.xml").toAbsolutePath().toFile().getAbsolutePath()));
    conf.addResource(new Path(
        Paths.get(hadoopConfDir, "hdfs-site.xml").toAbsolutePath().toFile().getAbsolutePath()));
    conf.set("fs.hdfs.impl.disable.cache", "true");
  }


  public static void createFlowLog(String logDir, String logFileName, String logName){
    if (CONFIG.getAppender(logName) != null) {
      return;
    }
    final PatternLayout layout = PatternLayout.newBuilder()
        .withCharset(Charset.forName("UTF-8"))
        .withConfiguration(CONFIG)
        .withPattern("%d{dd-MM-yyyy HH:mm:ss z} %c{1} %p - %m\n")
        .build();

    final Appender appender = FileAppender.newBuilder()
        .withName(logName)
        .withImmediateFlush(true)
        .withFileName(String.format(logDir + File.separator + "%s", logFileName))
        .withLayout(layout)
        .build();

    appender.start();
    CONFIG.addAppender(appender);

    AppenderRef[] refs = new AppenderRef[]{AppenderRef.createAppenderRef(logName, Level.ALL, null)};
    LoggerConfig loggerConfig = LoggerConfig.createLogger(false, Level.ALL, logName, "true", refs, null, CONFIG, null);
    loggerConfig.addAppender(appender, Level.ALL, null);
    CONFIG.addLogger(logName, loggerConfig);
    LOGGER_CONTEXT.updateLoggers(CONFIG);
  }

  public static void createJobLog(String logDir, String logFileName, String logName, String logFileSize, int logFileNum, List<LogFilterEntity> logFilterEntityList){

    if (CONFIG.getAppender(logName) != null) {
      return;
    }

    final PatternLayout layout = PatternLayout.newBuilder()
        .withCharset(Charset.forName("UTF-8"))
        .withConfiguration(CONFIG)
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
    CONFIG.addAppender(appender);

    AppenderRef[] refs = new AppenderRef[]{AppenderRef.createAppenderRef(logName, Level.ALL, null)};
    LoggerConfig loggerConfig = LoggerConfig.createLogger(false, Level.ALL, logName, "true", refs, null, CONFIG, null);
    loggerConfig.addAppender(appender, Level.ALL, compositeFilter);
    CONFIG.addLogger(logName, loggerConfig);
    LOGGER_CONTEXT.updateLoggers(CONFIG);
  }

  public static void stopLog(String logName){
    if (CONFIG.getAppender(logName) == null) {
      return;
    }
    CONFIG.getAppender(logName).stop();
    //  Appender 也需要被移除，不然会导致服务OOM
    if (CONFIG instanceof AbstractConfiguration) {
      ((AbstractConfiguration)CONFIG).removeAppender(logName);
    }
    CONFIG.getLoggerConfig(logName).removeAppender(logName);
    CONFIG.removeLogger(logName);
    LOGGER_CONTEXT.updateLoggers();
  }

  /**
   * 将工作流、任务日志上传到 HDFS 上
   */
  public static void uploadLog2Hdfs(String hdfsPath, String logDir,
      String logFileName)
      throws IOException {

    FileSystem fs = FileSystem.newInstance(conf);

    try {
      // 指定要上传的文件在本地的路径
      Path localFilePath = new Path(logDir + "/" + logFileName);
      logger.info("Local log path: {}", localFilePath);

      // 指定要将文件上传到 HDFS 的目标路径
      Path hdfsLogDir = new Path(hdfsPath);
      Path hdfsFilePath = new Path(hdfsPath + "/" + logFileName);
      logger.info("HDFS log path: {}", hdfsFilePath);

      // 上传文件到 HDFS
      if (!fs.exists(hdfsLogDir)) {
        fs.mkdirs(hdfsLogDir);
      }

      fs.copyFromLocalFile(localFilePath, hdfsFilePath);
    } finally {
      IOUtils.closeQuietly(fs);
    }

  }

  /**
   * 读取存放在 HDFS 上的日志
   *
   */
  public static String loadLogFromHdfs(String hdfsLogPath, int startChars,
      int length)
      throws IOException {

    // 指定日志文件路径
    Path logFilePath = new Path(hdfsLogPath);
    FileSystem fileSystem = FileSystem.newInstance(conf);
    FSDataInputStream fsDataInputStream;
    StringBuilder logContent;
    try {
      // 读取日志文件内容
      fsDataInputStream = fileSystem.open(logFilePath);

      logContent = new StringBuilder();

      try (BufferedReader reader = new BufferedReader(
          new InputStreamReader(fileSystem.open(logFilePath)))) {
        char[] buffer = new char[length];
        long skip = reader.skip(startChars);
        if (skip >= 0) {
          int bytesRead = reader.read(buffer, 0, length);
          if (bytesRead > 0) {
            logContent.append(new String(buffer, 0, bytesRead));
          }
        }
      }
    } finally {
      IOUtils.closeQuietly(fileSystem);
    }

    return logContent.toString();
  }

  /**
   * 从 HDFS 获取所有日志
   *
   * @param hdfsLogPath HDFS 日志路径
   * @return 日志内容
   */
  public static String loadAllLogFromHdfs(String hdfsLogPath)
      throws IOException {

    // 指定日志文件路径
    Path logFilePath = new Path(hdfsLogPath);
    FileSystem fileSystem = FileSystem.newInstance(conf);
    FSDataInputStream fsDataInputStream;
    StringBuilder logContent;
    BufferedReader reader;
    try {
      // 读取日志文件内容
      fsDataInputStream = fileSystem.open(logFilePath);

      logContent = new StringBuilder();

      reader = new BufferedReader(new InputStreamReader(fsDataInputStream));
      String line;
      while ((line = reader.readLine()) != null) {
        logContent.append(line).append("\n");
      }
      IOUtils.closeQuietly(reader);
    } finally {
      IOUtils.closeQuietly(fileSystem);
    }

    return logContent.toString();
  }

}
