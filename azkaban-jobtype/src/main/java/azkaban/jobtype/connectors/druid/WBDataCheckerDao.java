package azkaban.jobtype.connectors.druid;

import azkaban.Constants;
import azkaban.datacheck.VirtualViewTableException;
import azkaban.flow.CommonJobProperties;
import azkaban.jobtype.commons.MaskCheckNotExistException;
import azkaban.jobtype.util.DataChecker;
import azkaban.jobtype.util.HttpUtils;
import azkaban.utils.QualitisUtil;
import com.alibaba.druid.pool.DruidDataSource;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.math.BigDecimal;
import java.sql.CallableStatement;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.Period;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.temporal.TemporalAdjusters;
import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Properties;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.sql.DataSource;
import okhttp3.FormBody;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.ResponseBody;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang.StringUtils;
import org.apache.commons.lang.time.DateFormatUtils;
import org.slf4j.Logger;

public class WBDataCheckerDao {

	private static DataSource jobDS;
	private static DataSource dopsDS;
	private static DataSource dataCheckerRecordDS;
	private static WBDataCheckerDao instance;

	public static final Pattern DATA_OBJECT_PATTERN = Pattern.compile("\\{([^\\}]+)\\}");
	//获取当前类的实例
	/*public static WBDataCheckerDao getInstance() {
		if (instance == null) {
			synchronized (WBDataCheckerDao.class) {
				if (instance == null) {
					instance = new WBDataCheckerDao();
				}
			}
		}
		return instance;
	}*/

	public static synchronized WBDataCheckerDao getInstance() {
		if (instance == null) {
			instance = new WBDataCheckerDao();
		}
		return instance;
	}

	private void closeConnection(Connection conn, Logger log) {
		if (conn != null) {
			try {
				conn.close();
			} catch (SQLException e) {
				log.error("Error closing connection", e);
			}
		}
	}

	private void closeQueryRef(ResultSet rs, PreparedStatement pstmt, Logger log) {
		if (rs != null) {
			try {
				rs.close();
			} catch (Exception e) {
				log.error("Error closing result set", e);
			}
		}
		if (pstmt != null) {
			try {
				pstmt.close();
			} catch (Exception e) {
				log.error("Error closing prepared statement", e);
			}
		}
	}

	private void closePreparedStatement(PreparedStatement pstmt, Logger log) {
		if (pstmt != null) {
			try {
				pstmt.close();
			} catch (Exception e) {
				log.error("Error closing prepared statement", e);
			}
		}
	}

	/**
	 * 表（不含分区） check
	 */
	private static final String SQL_SOURCE_TYPE_JOB_TABLE = "/*slave*/ SELECT * FROM DBS d JOIN TBLS t ON t.DB_ID = d.DB_ID WHERE d.NAME=? AND t.TBL_NAME=? ;";
	/**
	 * 分区 check
	 */
	private static final String SQL_SOURCE_TYPE_JOB_PARTITION = "/*slave*/ SELECT * FROM DBS d JOIN TBLS t ON t.DB_ID = d.DB_ID JOIN PARTITIONS p ON p.TBL_ID = t.TBL_ID WHERE d.NAME=? AND t.TBL_NAME=? AND p.PART_NAME=? ;";

	private static final String SQL_NON_PARTITION_TABLE_CHECK = "SQL_NON_PARTITION_TABLE_CHECK";
	private static final String SQL_PARTITION_TABLE_CHECK = "SQL_PARTITION_TABLE_CHECK";
	private static final String SQL_PARTITION_CHECK = "SQL_PARTITION_CHECK";
	private static final String SQL_DOPS_COUNT_CHECK = "SQL_DOPS_COUNT_CHECK";
	private static final String SQL_DOPS_NON_PARTITION_TABLE_COUNT = "SQL_DOPS_NON_PARTITION_TABLE_COUNT";

	/**
	 * DataChecker 通过记录
	 */
	private static final String SQL_DATACHECKER_RECORD =
			"INSERT INTO job_data_check_record(exec_id, exec_user, db_name, tb_name, part_name, start_time, arrived_time, project_id, flow_id, job_id) "
					+ "VALUES(?, ?, ?, ?, ?, ?, ?, ?, ?, ?) ";

	// 查看 data object 的表类型
	private static final String SQL_TABLE_TYPE = "/*slave*/ SELECT t.TBL_TYPE FROM DBS d JOIN TBLS t ON t.DB_ID = d.DB_ID WHERE d.NAME=? AND t.TBL_NAME=? ;";

	//数据库校验总方法
	public boolean validateTableStatusFunction(Properties props, Logger log) {
		// todo: 可根据任务配置优化为懒加载
		if (jobDS == null) {
			jobDS = WBDruidFactory.getJobInstance(props, log);
			if (jobDS == null) {
				log.error("Error getting job Druid DataSource instance");
				return false;
			}
		}
		if (dopsDS == null) {
			dopsDS = WBDruidFactory.getDopsInstance(props, log);
			if (dopsDS == null) {
				log.error("Error getting DOPS Druid DataSource instance");
				return false;
			}
		}

		if (dataCheckerRecordDS == null) {
			//通过alibaba的druid数据库连接池获取数据库连接
			dataCheckerRecordDS = WBDruidFactory.getRecordInstance(props, log);
			if (dataCheckerRecordDS == null) {
				log.error("Error getting DataChecker Record Druid DataSource instance");
				return false;
			}
		}
		Connection jobConn = null;
		try {
			for (Map.Entry<Object, Object> entry : props.entrySet()) {
				entry.setValue(entry.getValue().toString().replace(" ", "").trim());
			}
		} catch (Exception e) {
			throw new RuntimeException("remove job space char failed",e);
		}

		String singleDataObject = props.getProperty(DataChecker.DATA_OBJECT);
		if(singleDataObject!=null){
			singleDataObject = singleDataObject.replace(" ", "").trim();
		}

		log.info("(datachecker info) database table partition Info : " + singleDataObject);

		//组装查询的数据资源集合
		boolean enableHourlySecondaryPartition = Boolean.parseBoolean(
				props.getProperty(DataChecker.DC_HOURLY_SECONDARY_PARTITION, String.valueOf(false)));
		log.info(
				"(datachecker info) enable 24-h secondary partition : " + enableHourlySecondaryPartition);
		List<Map<String, String>> dataObjectList = handleSeparationProperties(props);
		// 打印需要检验的所有 data object
		log.info("data objects to check: ");
		for (Map<String, String> dataObjectMap : dataObjectList) {
			for (Entry<String, String> dataObjectEntry : dataObjectMap.entrySet()) {
				log.info(dataObjectEntry.getKey() + "--" + dataObjectEntry.getValue());
			}
		}

		// 每个 data object 的校验结果
		boolean[] flagList = new boolean[dataObjectList.size()];
		Arrays.fill(flagList, false);
		Long startTime = System.currentTimeMillis();
		Long currentTime = startTime;
		Long waitTime = Long.valueOf(props.getProperty(DataChecker.WAIT_TIME, "1")) * 3600 * 1000;
		int queryFrequency = Integer.valueOf(props.getProperty(DataChecker.QUERY_FREQUENCY, "5"));
		String timeScape = props.getProperty(DataChecker.TIME_SCAPE, "NULL");
		boolean recordSwitch = Boolean.parseBoolean(
				props.getProperty("datachecker.record.switch", "false"));
		// DataChecker 禁用视图表功能开关
		boolean virtualViewTableDenySwitch = Boolean.parseBoolean(
				props.getProperty("datachecker.virtual_view.table.deny.switch", "false"));

		String earliestFinishTimeString = props.getProperty(DataChecker.EARLIEST_FINISH_TIME, "");
		String dateFormat = "HH:mm";

		// 校验日期格式
		if (!"".equals(earliestFinishTimeString)) {
			boolean isValid = isValidDateFormat(earliestFinishTimeString, dateFormat);
			log.info("Is valid date format? " + isValid);
			if (!isValid) {
				// 格式错误，任务直接失败
				log.error("Error date format");
				return false;
			}

		}

		boolean earliestFinishTimeCrossDay = Boolean.parseBoolean(
				props.getProperty(DataChecker.EARLIEST_FINISH_TIME_CROSS_DAY,
						String.valueOf(false)));

		String priority = props.getProperty(DataChecker.PRIORITY, "HIGH");

		String dateRangeInterval = props.getProperty(DataChecker.DC_RANGE_INTERVAL, "1-day");

		log.info("(datachecker info) wait time : " + waitTime);
		log.info("(datachecker info) quert frequency : " + queryFrequency);
		log.info("(datachecker info) time scape : " + timeScape);
		log.info("(datachecker info) earliest finish time : " + earliestFinishTimeString);
		log.info("(datachecker info) earliest finish time cross day: " + earliestFinishTimeCrossDay);
		log.info("(datachecker info) priority: " + priority);
		log.info("(datachecker info) deny virtual view table switch: " + virtualViewTableDenySwitch);
		log.info("(datachecker info) date range interval: " + dateRangeInterval);
		log.info(
				"=============================Data Check Start==========================================");
		Long sleepTime = waitTime / queryFrequency;
		// 总校验结果
		boolean result = false;

		LocalDateTime targetDateTime = null;
		// 判断当前时间是否已超过最早结束时间
		if (!"".equals(earliestFinishTimeString)) {
			// 时间处理
			String[] timeSplit = earliestFinishTimeString.split(":");
			String hourString = timeSplit[0].trim();
			String minString = timeSplit[1].trim();

			targetDateTime = LocalDateTime.of(LocalDateTime.now().toLocalDate(),
					LocalTime.of(Integer.parseInt(hourString), Integer.parseInt(minString)));

			if (earliestFinishTimeCrossDay) {
				targetDateTime = targetDateTime.plusDays(1);
			}
			boolean isAfter = isTimeAfter(log, currentTime, targetDateTime);
			if (isAfter) {
				// 已超过最早完成时间，任务直接失败
				log.error("Already exceeded the earliest time! ");
				return false;
			}
		}

		while ((currentTime - startTime) <= waitTime) {
			boolean flag = true;
			try {
				jobConn = WBDruidFactory.getConnection((DruidDataSource) jobDS, props, log);
				for (int i = 0; i < dataObjectList.size(); i++) {
					if (flagList[i]) {
						continue;
					}
					Map<String, String> proObjectMap = dataObjectList.get(i);

					if (proObjectMap.get("maskStatus") != null) {
						if ("success".equals(proObjectMap.get("maskStatus"))) {
							log.info("(datachecker info) get mask result success");
						} else {
							log.info("(datachecker info) get mask result failed");
						}
					}
					int rowCount = 0;
					ResultSet rs = null;
					PreparedStatement pstmt = null;
					try {
						if (proObjectMap.containsKey(DataChecker.SOURCE_TYPE)) {
							rs = handleHaveSourceType(proObjectMap, jobConn, pstmt, log, props,
									virtualViewTableDenySwitch);
						} else {
							rs = handleNotSourceType(proObjectMap, jobConn, pstmt, log, props,
									virtualViewTableDenySwitch);
						}
						if (null != rs && rs.last()) {
							rowCount = rs.getRow();
						}
					} finally {
						closeQueryRef(rs, pstmt, log);
					}

					if (rowCount >= 1 || "success".equals(proObjectMap.get("maskStatus"))) {
						flagList[i] = true;
						// 记录通过信息
						if (recordSwitch) {
							log.info("Recording DataChecker info passed...");
							int recordResult = recordDataChecker(props, proObjectMap, log, startTime,
									System.currentTimeMillis());
							if (recordResult == 1) {
								log.info("Recorded DataChecker info passed...");
							} else {
								log.error("Record DataChecker info failed");
							}
						}
					}
					flag = flag && flagList[i];
				}
			} catch (VirtualViewTableException e) {
				log.error("Virtual view table is not allowed to use DataChecker. ", e);
				throw new RuntimeException("Virtual view table is not allowed to use DataChecker. ", e);
			} catch (SQLException e) {
				log.error("get datachecker result failed", e);
				throw new RuntimeException("get datachecker result failed",e);
			} finally {
				closeConnection(jobConn, log);
				log.info("=============================Data Check End==========================================");
			}
			if(flag) {
				result = flag;
				// 判断当前时间是否已超过最早结束时间
				if (!"".equals(earliestFinishTimeString)) {
					boolean isAfter = isTimeAfter(log, currentTime, targetDateTime);
					if (isAfter) {
						// 已超过最早完成时间，退出循环
						log.info("Already reached the earliest finish time.");
						break;
					}
				} else {
					break;
				}
			}
			try {
				log.info("Waiting " + sleepTime + " ms to next check...");
				Thread.sleep(sleepTime);
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
			currentTime = System.currentTimeMillis();
		}

		if ("LOW".equalsIgnoreCase(priority)) {
			result = true;
		}
		log.info("The priority of this DataChecker is " + priority + ("LOW".equalsIgnoreCase(priority) ?
				", the result can be seemed as true. " : "."));
		return getQualitisData(props, log, dataObjectList, result);

	}

	private boolean isTimeAfter(Logger log, long currentTimeMillis, LocalDateTime targetDateTime) {
		Instant instant = Instant.ofEpochMilli(currentTimeMillis);
		LocalDateTime currentDateTime = instant.atZone(ZoneId.systemDefault()).toLocalDateTime();
		boolean isAfter = currentDateTime.isAfter(targetDateTime);
		log.info("CurrentDateTime: " + currentDateTime);
		log.info("targetDateTime: " + targetDateTime);
		log.info("Is current time after earliest finish time? " + isAfter);
		return isAfter;
	}

	private boolean isValidDateFormat(String dateString, String dateFormat) {
		SimpleDateFormat sdf = new SimpleDateFormat(dateFormat);
		sdf.setLenient(true);

		try {
			sdf.parse(dateString);
			return true;
		} catch (ParseException e) {
			return false;
		}
	}

	private int recordDataChecker(Properties props, Map<String, String> proObjectMap, Logger log,
								  Long startTime, Long arrivedTime) {
		// 参数获取
		// 执行 ID
		String execId = props.getProperty(Constants.FlowProperties.AZKABAN_FLOW_EXEC_ID);
		// 执行用户
		String execUser = props.getProperty("user.to.proxy");
		// 库名
		String dbName = proObjectMap.get(Constants.DB_NAME);
		// 表名
		String tableName = proObjectMap.get(Constants.TABLE_NAME);
		// 分区名
		String partitionName = proObjectMap.get(Constants.PARTITION_NAME);
		// 开始检查时间
		String startTimeFormatted = DateFormatUtils.format(new Date(startTime), "yyyy-MM-dd HH:mm:ss");
		// 数据到达时间
		String arrivedTimeFormatted = DateFormatUtils.format(new Date(arrivedTime),
				"yyyy-MM-dd HH:mm:ss");
		// 项目 ID，azkaban.flow.projectid
		String projectId = props.getProperty(CommonJobProperties.PROJECT_ID);
		// 工作流 ID，azkaban.flow.flowid
		String flowId = props.getProperty(Constants.FlowProperties.AZKABAN_FLOW_FLOW_ID);
		// 任务 ID，azkaban.job.id
		String jobId = props.getProperty(CommonJobProperties.JOB_ID);

		Connection dataCheckerRecordDSConnection = null;
		int affectRows = 0;
		try {
			dataCheckerRecordDSConnection = dataCheckerRecordDS.getConnection();
			affectRows = updateDataCheckerRecord(dataCheckerRecordDSConnection,
					SQL_DATACHECKER_RECORD, log, execId, execUser, dbName, tableName, partitionName,
					startTimeFormatted,
					arrivedTimeFormatted, projectId, flowId, jobId);
		} catch (SQLException e) {
			log.warn("Update DataChecker record failed", e);
		} finally {
			if (dataCheckerRecordDSConnection != null) {
				closeConnection(dataCheckerRecordDSConnection, log);
			}
		}

		return affectRows;
	}

	private int updateDataCheckerRecord(Connection connection, String sql, Logger log,
										Object... params) {
		PreparedStatement statement = null;
		int affectRows = 0;

		try {
			statement = connection.prepareStatement(sql);

			// exec_id
			statement.setInt(1, Integer.parseInt((String) params[0]));
			// exec_user
			statement.setString(2, (String) params[1]);
			// db name
			statement.setString(3, (String) params[2]);
			// table name
			statement.setString(4, (String) params[3]);
			// partition name
			statement.setString(5, (String) params[4]);
			// start time
			statement.setString(6, (String) params[5]);
			// arrived time
			statement.setString(7, (String) params[6]);
			// project_id
			statement.setInt(8, Integer.parseInt((String) params[7]));
			// flow_id
			statement.setString(9, (String) params[8]);
			// job_id
			statement.setString(10, (String) params[9]);

			affectRows = statement.executeUpdate();

		} catch (SQLException e) {
			log.warn("Update DataChecker record failed", e);
		} finally {
			closePreparedStatement(statement, log);
			closeConnection(connection, log);
		}

		return affectRows;
	}

	private boolean getQualitisData(Properties props, Logger log,
									List<Map<String, String>> dataObjectList, boolean result) {
		// 系统级别功能开关，控制是否开启整个后续校验流程
		boolean systemCheck = Boolean.valueOf(props.getProperty("job.datachecker.qualitis.switch"));
		// 用户级别开关，是否默认开启后续校验流程，true 表示默认开启，false 则表示用户需要在任务中配置开启
		boolean userCheck = Boolean.valueOf(props.getProperty(DataChecker.QUALITIS_CHECK,
				props.getProperty("job.datachecker.qualitis.user.switch")));
		// 系统级别，是否启动 Qualitis check，false 直接去 HDFS check
		boolean type = Boolean.valueOf(props.getProperty("job.datachecker.qualitis.type","false"));
		// 用户任务配置，是否忽略 DOPS check，默认 false
		boolean ignoreDopsCheck = Boolean.parseBoolean(
				props.getProperty(DataChecker.DC_IGNORE_DOPS_CHECK,
						String.valueOf(false)));
		if (systemCheck && userCheck && result) {
			log.info(
					"=============================Data Check Qualitis Start==========================================");

			List<Future<Boolean>> futureList = new ArrayList<>();
			QualitisUtil qualitisUtil = new QualitisUtil(props);
			ExecutorService threadPool = null;
			try {
				threadPool = new ThreadPoolExecutor(8, 16, 0L, TimeUnit.MILLISECONDS,
						new LinkedBlockingQueue<>());
				long startTime = System.currentTimeMillis();
				String retry = props.getProperty("qualitis.submit.retry.count");
				for (int i = 0; i < dataObjectList.size(); i++) {
					Map<String, String> proObjectMap = dataObjectList.get(i);
					ExecutorService finalThreadPool = threadPool;
					int index = i;
					Callable<Boolean> callable = new Callable<Boolean>() {
						private AtomicInteger count = new AtomicInteger(1);
						private int finalI = index;
						final String SQL_DB_LOCATION = "select DB_LOCATION_URI from DBS where name = ? ";
						@Override
						public Boolean call() throws Exception {
							if (type) {
								return qualitisCheck();
							} else {
								return hdfsCheck();
							}
						}

						private Boolean hdfsCheck() {
							String dbName = proObjectMap.get(Constants.DB_NAME);
							String tableName = proObjectMap.get(Constants.TABLE_NAME);
							String parName = proObjectMap.get(Constants.PARTITION_NAME);

							Process process = null;
							BufferedReader reader = null;
							Connection jobConn = null;
							CallableStatement statement = null;
							ResultSet resultSet = null;
							String hdfsLocation = "";
							try {
								hdfsLocation = queryLocation(jobConn, dbName, statement, resultSet);
								if (StringUtils.isBlank(hdfsLocation)) {
									return false;
								}
								if (StringUtils.isNotBlank(tableName)) {
									hdfsLocation += "/" + tableName;
								}
								if (StringUtils.isNotBlank(parName)) {
									hdfsLocation += "/" + parName;
								}
								String[] cmd = { "/bin/bash", "-c", "hdfs dfs -count " + hdfsLocation + " |awk '{print $2}'" };
								log.info("exec cmd: {}", Arrays.toString(cmd));
								String output = runCmd(cmd, process, reader);
								// 此处注意 output 可能为 null，即没有标准输出
								if (output == null || output.isEmpty()) {
									return false;
								}

								int count = Integer.parseInt(output);
								log.info("HDFS count for DB {}, Table {}, Partition {}: {}", dbName, tableName,
										parName, count);
								return count > 0;
							} catch (Exception e) {
								log.error("get datachecker result from hdfs failed", e);
								return false;
							} finally {
								if (null != process) {
									process.destroy();
								}
								try {
									if (null != reader) {
										reader.close();
									}
									closeQueryRef(resultSet, statement, log);
									closeConnection(jobConn,log);
								} catch (IOException e) {
									log.error("close BufferedReader failed", e);
								}

							}
						}

						private String queryLocation(Connection jobConn, String dbName, CallableStatement statement, ResultSet resultSet) throws SQLException {
							jobConn = jobDS.getConnection();
							statement = jobConn.prepareCall(SQL_DB_LOCATION);
							statement.setString(1, dbName);
							resultSet = statement.executeQuery();
							if (resultSet.next()) {
								return resultSet.getString(1);
							}
							return "";
						}

						private String runCmd(String[] cmd, Process process, BufferedReader reader) throws Exception {
							int code;
							BufferedReader errorReader = null;
							try {
								process = Runtime.getRuntime().exec(cmd);
								// 读取标准输出
								reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
								String stdLine = reader.readLine();

								log.info("process for cmd {} std output: {}", Arrays.toString(cmd), stdLine);

								// 读取标准错误
								errorReader = new BufferedReader(
										new InputStreamReader(process.getErrorStream()));
								StringBuilder errorOutput = new StringBuilder();
								String errorLine;
								while ((errorLine = errorReader.readLine()) != null) {
									errorOutput.append(errorLine).append("\n");
								}
								log.warn("process for cmd {} error output: {}", Arrays.toString(cmd), errorOutput);
								code = process.waitFor();
								if (0 == code) {
									return stdLine;
								} else {
									throw new Exception(
											"exec cmd " + Arrays.toString(cmd) + "failed, error output: " + errorOutput);
								}
							} catch (Exception e) {
								throw new Exception(e);
							} finally {
								if (errorReader != null) {
									IOUtils.closeQuietly(errorReader);
								}
							}
						}

						private Boolean qualitisCheck() {
							try {
								String applicationId = qualitisUtil
										.createAndSubmitRule(proObjectMap.get(Constants.DB_NAME),
												proObjectMap.get(Constants.TABLE_NAME),
												proObjectMap.get(Constants.PARTITION_NAME), finalI);
								if (StringUtils.isEmpty(applicationId)) {
									return false;
								}
								while (System.currentTimeMillis() - startTime < Integer
										.parseInt(props.getProperty("qualitis.getStatus.all.timeout"))) {
									int status = new BigDecimal(qualitisUtil.getTaskStatus(applicationId)).intValue();
									switch (status) {
										case 1:
										case 3:
										case 10:
										case 12:
											try {
												Thread
														.sleep(Integer.valueOf(props.getProperty("qualitis.getStatus.interval")));
											} catch (InterruptedException e) {
												log.error("get datachecker result from qualitis InterruptedException", e);
											}
											break;
										case 7:
										case 9:
											if (Integer.parseInt(retry) >= count.getAndIncrement()) {
												Thread.sleep(Integer.parseInt(props.getProperty("qualitis.submit.retry.interval")));
												log.info("qualitis rule rerun:{},limit:{}",count.get(),retry);
												return finalThreadPool.submit(this).get();
											} else {
												return false;
											}
										case 4:
											return true;
										default:
											return false;
									}
								}
								return false;
							} catch (Exception e) {
								log.error("get datachecker result from qualitis failed", e);
								return false;
							}
						}
					};
					if (ignoreDopsCheck) {
						log.info("Skip DOPS check");
						futureList.add(threadPool.submit(callable));
					} else {
						log.info("Starting DOPS check ...");
						Integer flag = dopsDataCheck(props, proObjectMap, log);
						switch (flag) {
							case 0:
								return false;
							case 1:
								futureList.add(threadPool.submit(callable));
								break;
							default:
								break;
						}
					}
				}
				for (Future<Boolean> future : futureList) {
					if (!future.get()) {
						return false;
					}
				}
				return true;
			} catch (Exception e) {
				log.error("get datachecker result from qualitis failed", e);
				throw new RuntimeException("get datachecker result from qualitis failed", e);
			} finally {
				if (threadPool != null) {
					threadPool.shutdown();
				}
				log.info(
						"=============================Data Check Qualitis End==========================================");
			}
		} else {
			return result;
		}
	}

	private Integer dopsDataCheck(Properties props, Map<String, String> proObjectMap, Logger log) {
		String dbName = proObjectMap.get(Constants.DB_NAME);
		String tableName = proObjectMap.get(Constants.TABLE_NAME);
		String partitionName = proObjectMap.get(Constants.PARTITION_NAME);
		String clusterName = props.getProperty(Constants.CLUSTER_NAME,"");
		String dopsCountCheck = props.getProperty(SQL_DOPS_COUNT_CHECK).replace("-"," ");
		String dopsNonPartitionTableCount = props.getProperty(SQL_DOPS_NON_PARTITION_TABLE_COUNT).replace("-"," ");
		String partitionTableCheck = props.getProperty(SQL_PARTITION_TABLE_CHECK).replace("-"," ");
		String nonPartitionTableCheck = props.getProperty(SQL_NON_PARTITION_TABLE_CHECK).replace("-"," ");
		String partitionCheck = props.getProperty(SQL_PARTITION_CHECK).replace("-"," ");

		Connection dopsDSConnection = null;
		String status = null;

		try {
			dopsDSConnection = WBDruidFactory.getConnection((DruidDataSource) dopsDS, props, log);
			status = queryDops(dopsDSConnection, dopsCountCheck, log, dbName, tableName,clusterName);
			if (!"0".equals(status)) {
				if (StringUtils.isEmpty(partitionName)) {
					status = queryDops(dopsDSConnection, dopsNonPartitionTableCount, log, dbName, tableName,clusterName);
					if ("0".equals(status)) {
						status = queryDops(dopsDSConnection, partitionTableCheck, log, dbName, tableName,clusterName);
						log.info("partition table check: db:{},table:{},partition:{},dops status :{}", dbName, tableName, partitionName, status);
						return (!"13".equals(status)) ? 0 : 2;
					} else {
						status = queryDops(dopsDSConnection, nonPartitionTableCheck, log, dbName, tableName,clusterName);
						log.info("non-partition table check: db:{},table:{},partition:{},dops status :{}", dbName, tableName, partitionName, status);
						return (("13".equals(status) || "10".equals(status)) ? 2 : 1);
					}
				} else {
					status = queryDops(dopsDSConnection, partitionCheck, log, dbName, tableName, partitionName,clusterName);
					if ("-1".equals(status)) {
						log.info("db:{},table:{},partition:{},Dops not recorded", dbName, tableName, partitionName);
						return 2;
					} else {
						log.info("partition check: db:{},table:{},partition:{},dops status :{}", dbName, tableName, partitionName, status);
						return ("13".equals(status) || "10".equals(status)) ? 2 : 1;
					}
				}
			} else {
				log.info("db:{},table:{},partition:{},Dops not recorded", dbName, tableName, partitionName);
				return 2;
			}
		} catch (SQLException e) {
			log.error("get datachecker result failed", e);
			throw new RuntimeException("get datachecker result failed",e);
		} finally {
			closeConnection(dopsDSConnection,log);
		}

	}

	public String queryDops(Connection dopsDSConnection, String sql, Logger log, Object... params) {
		CallableStatement pstmt = null;
		ResultSet res = null;
		String status = "-1";
		try {
			pstmt = dopsDSConnection.prepareCall(sql);
			for (int i = 0; i < params.length; i++) {
				pstmt.setString(i+1, (String) params[i]);
			}
			res = pstmt.executeQuery();
			while (res.next()) {
				status = res.getString(1);
			}
			return status;
		} catch (SQLException e) {
			log.error("get datachecker result failed", e);
			throw new RuntimeException("get datachecker result failed",e);
		}finally {
			closeQueryRef(res, pstmt, null);
		}

	}

	//Properties 参数分组
	private List<Map<String, String>> handleSeparationProperties(Properties p) {
		boolean enableHourlySecondaryPartition = Boolean.parseBoolean(
				p.getProperty(DataChecker.DC_HOURLY_SECONDARY_PARTITION, String.valueOf(false)));

		//source.type和data.object的分组集合
		List<Map<String, String>> proList = new ArrayList<>();

		for(Object key : p.keySet()){
			Map<String, String> proMap = new HashMap<>();

			String skey = String.valueOf(key);
			if (skey.contains(DataChecker.DATA_OBJECT)) {
				//根据data.object的个数去拼装数据
				String[] keyArr = skey.split("\\.");

				if (keyArr.length == 3) {
					//有后缀
					//获取后缀数字
					String keyNum = keyArr[2];
					//组装成对的Key
					String stKey = DataChecker.SOURCE_TYPE + "." + keyNum;
					String doKey = DataChecker.DATA_OBJECT + "." + keyNum;
					String sourceType = p.getProperty(stKey);
					String dataObject = p.getProperty(doKey);

					if (null != p.get(stKey)) {
						//source.type可能会不存在
						proMap.put(DataChecker.SOURCE_TYPE, sourceType);
					}
					if (checkHaveDateRange(dataObject)) {
						// 解析data.object的日期范围，组装proMap，并加到proList数组中
						addDateToProList(proList, p, doKey);
						continue;
					}

					if (onlyHasPrimaryPartition(dataObject)) {
						// 有且只有一级分区
						if (enableHourlySecondaryPartition) {
							// 校验 24 小时二级分区，将二级分区加入到集合中
							addHourlySecondaryPartition(proList, sourceType, dataObject);
							continue;
						}
					}
					proMap.put(DataChecker.DATA_OBJECT, dataObject);
				} else {
					//没有后缀
					if (checkHaveDateRange(String.valueOf(p.get(DataChecker.DATA_OBJECT)))) {
						// 解析data.object的日期范围，组装proMap，并加到proList数组中
						addDateToProList(proList, p, DataChecker.DATA_OBJECT);
						continue;
					} else {
						// 如果data.object填的没有分区，或者有分区且分区不是日期范围
						String stKey = DataChecker.SOURCE_TYPE;
						String doKey = DataChecker.DATA_OBJECT;
						String sourceType = p.getProperty(stKey);
						String dataObject = p.getProperty(doKey);
						if (onlyHasPrimaryPartition(dataObject)) {
							// 有且只有一级分区
							if (enableHourlySecondaryPartition) {
								// 校验 24 小时二级分区，将二级分区加入到集合中
								addHourlySecondaryPartition(proList, sourceType, dataObject);
								continue;
							}
						}
						if (null != p.get(stKey)) {
							//source.type可能会不存在
							proMap.put(DataChecker.SOURCE_TYPE, String.valueOf(p.get(stKey)));
						}
						proMap.put(DataChecker.DATA_OBJECT, dataObject);
					}
				}
				proList.add(proMap);
			}
		}
		return proList;
	}

	/**
	 * 处理 24 小时二级分区
	 *
	 * @param proList    check object 集合
	 * @param sourceType 类型
	 * @param dataObject check object
	 */
	private void addHourlySecondaryPartition(List<Map<String, String>> proList, String sourceType,
											 String dataObject) {
		boolean blankSourceType = StringUtils.isBlank(sourceType);
		String secondaryPartition = "";
		for (int i = 0; i < 24; i++) {
			Map<String, String> proMap = new HashMap<>();
			if (!blankSourceType) {
				proMap.put(DataChecker.SOURCE_TYPE, sourceType);
			}
			String replacement = i < 10 ? "/ph=0" + i + "}" : "/ph=" + i + "}";
			secondaryPartition = StringUtils.replace(dataObject, "}", replacement);
			proMap.put(DataChecker.DATA_OBJECT, secondaryPartition);

			proList.add(proMap);
		}
	}

	/**
	 * 判断 data object 是否是一级分区
	 *
	 * @param dataObject check object
	 * @return 该 data object 是否为一级分区
	 */
	private boolean onlyHasPrimaryPartition(String dataObject) {
		return dataObject.contains("{") && !dataObject.contains("/");
	}

	private void addDateToProList(List<Map<String, String>> proList, Properties p,String key) {
		String dataObjectStr = String.valueOf(p.get(key));
		String rangeInterval = p.getProperty(DataChecker.DC_RANGE_INTERVAL, "1-day");
		String regex = "([0-9a-zA-Z_]+\\.[0-9a-zA-Z_]+)\\{ds([<>]=?)([^,]+),ds([<>]=?)([^}]+)}";
		Pattern pattern = Pattern.compile(regex);
		Matcher matcher = pattern.matcher(dataObjectStr);
//		DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyyMMdd");
		List<String> patterns = Arrays.asList("yyyyMMdd", "yyyy-MM-dd");
		// 假定前后日期的格式一样
		DateTimeFormatter formatter;
		LocalDate start = null;
		LocalDate end = null;
		if (matcher.find()) {
			// 第一个运算符
			String operator1 = matcher.group(2);
			String time1 = matcher.group(3);
			// 第二个运算符
			String operator2 = matcher.group(4);
			String time2 = matcher.group(5);
			// 判断两个operation不能相同
			if(operator1.contains(operator2) || operator2.contains(operator1)) {
				throw new RuntimeException("Time range pattern error! data.object=" + dataObjectStr);
			}
			String finaltime1 = time1;
			// 解析字符串的formatter
			formatter = patterns.stream()
					.map(DateTimeFormatter::ofPattern)
					.map(f -> {
						try {
							LocalDate parsedDate = LocalDate.parse(finaltime1,f);
							return new AbstractMap.SimpleEntry<>(f, parsedDate);
						} catch (DateTimeParseException e) {
							return new AbstractMap.SimpleEntry<>(f, (LocalDate) null);
						}
					})
					.filter(entry -> entry.getValue() != null)
					.findFirst()
					.map(Map.Entry::getKey)
					.orElseThrow(() -> new DateTimeParseException("Unable to parse date:", finaltime1, 0));
			switch (operator1){
				case ">":
					// 如果第一个运算符是大于号，则开始时间为time1 +1天
					start = LocalDate.parse(time1, formatter).plusDays(1);
					break;
				case ">=":
					// 如果第一个运算符是大于等于号，则开始时间为time1
					start = LocalDate.parse(time1, formatter);
					break;
				case "<":
					// 如果第一个运算符是小于号，则结束时间为time1 -1天
					end = LocalDate.parse(time1, formatter).minusDays(1);
					break;
				case "<=":
					// 如果第一个运算符是小于等于号，则结束时间为time1
					end = LocalDate.parse(time1, formatter);
					break;
				default:
					throw new RuntimeException("Time range pattern error! operation1=" + operator1);
			}

			switch(operator2){
				case ">":
					// 如果第二个运算符是大于号， 则开始时间为time2 +1天
					start = LocalDate.parse(time2, formatter).plusDays(1);
					break;
				case ">=":
					// 如果第二个运算符是大于等于号， 则开始时间为time2
					start = LocalDate.parse(time2, formatter);
					break;
				case "<":
					// 如果第二个运算符是小于号， 则结束时间为time2 -1天
					end = LocalDate.parse(time2, formatter).minusDays(1);
					break;
				case "<=":
					// 如果第二个运算符是小于等于号， 则结束时间为time2
					end = LocalDate.parse(time2, formatter);
					break;
				default:
					throw new RuntimeException("Time range pattern error! operation2=" + operator2);
			}
		} else {
			throw new RuntimeException("Time range pattern error! dataObjectStr=" + dataObjectStr + ", regex=" + regex);
		}
		// 解析起始日期和结束日期并进行循环生成日期
		LocalDate currentDate = start;
		assert start != null;
		assert end != null;
		if (start.isAfter(end)) {
			throw new RuntimeException("startDate can't be later than endDate");
		}

		// 解析 rangeInterval 配置
		Period intervalPeriod = parseRangeInterval(rangeInterval);
		boolean isLastDayOfMonth = false;
		// 判断第一个日期是否为当前月的最后一天，如果为最后一天，则认为间隔为月时后续日期都取月的最后一天
		if (!currentDate.isAfter(end)) {
			// 获取当前月的最后一天
			LocalDate lastDayOfMonth = currentDate.with(TemporalAdjusters.lastDayOfMonth());
			if (currentDate.isEqual(lastDayOfMonth)) {
				isLastDayOfMonth = true;
			}
		}
		// 按照指定的间隔生成日期
		while (!currentDate.isAfter(end)) {
			// 获取当前月的最后一天
			LocalDate lastDayOfMonth = currentDate.with(TemporalAdjusters.lastDayOfMonth());

			// 如果当前月的最后一天在结束日期之前，则使用最后一天
			if (isLastDayOfMonth && !lastDayOfMonth.isAfter(end) && intervalPeriod.getMonths() > 0) {
				addProMapToProList(proList, p, lastDayOfMonth, formatter, matcher.group(1));
			} else {
				addProMapToProList(proList, p, currentDate, formatter, matcher.group(1));
			}
			currentDate = currentDate.plus(intervalPeriod);
		}
	}

	/**
	 * 解析 rangeInterval 配置，支持 n-day/n-week/n-month 形式
	 *
	 * @param rangeInterval 配置字符串，如 "1-day", "2-week", "3-month"
	 * @return Period 对象
	 */
	private Period parseRangeInterval(String rangeInterval) {
		String[] parts = rangeInterval.split("-");
		if (parts.length != 2) {
			throw new IllegalArgumentException("Invalid rangeInterval format: " + rangeInterval);
		}

		int amount = Integer.parseInt(parts[0]);
		String unit = parts[1].toLowerCase();

		switch (unit) {
			case "day":
			case "days":
				return Period.ofDays(amount);
			case "week":
			case "weeks":
				return Period.ofWeeks(amount);
			case "month":
			case "months":
				return Period.ofMonths(amount);
			default:
				throw new IllegalArgumentException("Unsupported interval unit: " + unit);
		}
	}

	private void addProMapToProList(List<Map<String, String>> proList, Properties p, LocalDate currentDate, DateTimeFormatter formatter, String subffix) {
		String newDateObjectStr = String.format("%s{ds=%s}", subffix, currentDate.format(formatter));
		Map<String, String> proMap = new HashMap<>();
		if(null != p.get(DataChecker.SOURCE_TYPE)){//source.type可能会不存在
			proMap.put(DataChecker.SOURCE_TYPE, String.valueOf(p.get(DataChecker.SOURCE_TYPE)));
		}
		proMap.put(DataChecker.DATA_OBJECT, newDateObjectStr);
		proList.add(proMap);
	}

	private boolean checkDateRangeIllegal(Properties p) {
		for(Object key : p.keySet()){
			String skey = String.valueOf(key);
			if (skey.contains(DataChecker.DATA_OBJECT_SUBFFIX)) {
				return true;
			}
		}
		return false;
	}

	private boolean checkHaveDateRange(String dataObject) {
		if (!dataObject.contains("{")) {
			// 没有分区
			return false;
		}
		String partition = StringUtils.substringBetween(dataObject, "{", "}");
		return partition.contains(">") || partition.contains("<");
	}

	/**
	 * 查询 Hive 元数据库判断指定的表是否为视图表
	 *
	 * @param dbName    数据库名
	 * @param tableName 表名
	 * @return 表是否为视图表
	 */
	private boolean checkVirtualViewTable(String dbName, String tableName, Connection jobConnection,
										  Logger log)
			throws SQLException {

		log.info("Checking if table {}.{} is virtual view table...", dbName, tableName);
		boolean checkResult = false;
		PreparedStatement statement = jobConnection.prepareCall(SQL_TABLE_TYPE);
		statement.setString(1, dbName);
		statement.setString(2, tableName);
		ResultSet tableTypeResultSet = statement.executeQuery();

		if (tableTypeResultSet != null && tableTypeResultSet.last()) {
			String tableType = tableTypeResultSet.getString("TBL_TYPE");
			log.info("table: {}.{} -- type: {}", dbName, tableName, tableType);
			checkResult = "VIRTUAL_VIEW".equals(tableType);
		}

		if (tableTypeResultSet != null) {
			closeQueryRef(tableTypeResultSet, null, log);
		}
		closePreparedStatement(statement, log);
		return checkResult;
	}

	private PreparedStatement constructPreparedStatement(Connection jobConn, String dataScape,
			PreparedStatement pstmt, String dbName, String tableName, String partitionName,
			Properties props)
			throws SQLException {

		if ("Partition".equals(dataScape)) {
			pstmt = jobConn.prepareCall(SQL_SOURCE_TYPE_JOB_PARTITION);
			setString(pstmt, 1, dbName, props);
			setString(pstmt, 2, tableName, props);
			pstmt.setString(3, partitionName);
		} else {
			pstmt = jobConn.prepareCall(SQL_SOURCE_TYPE_JOB_TABLE);
			setString(pstmt, 1, dbName, props);
			setString(pstmt, 2, tableName, props);
		}

		return pstmt;
	}

	public ResultSet executeWithRetry(Connection jobConn, Properties props, String dataScape,
			String dbName, String tableName, String partitionName, Logger logger) {

		int maxRetries = Integer.parseInt(
				props.getProperty(DataChecker.DC_SQL_EXCEPTION_MAX_RETRIES, "5"));
		long retryDelay = Long.parseLong(
				props.getProperty(DataChecker.DC_SQL_EXCEPTION_RETRY_DELAY_MS, 10 * 60 * 1000 + ""));
		String sqlExceptionKeywords = props.getProperty(DataChecker.DC_SQL_EXCEPTION_KEYWORDS,
				"proxy-error;can-not-get-sock");
		int attempt = 0;
		ResultSet rs = null;
		while (attempt <= maxRetries) {
			Connection conn = attempt == 0 ? jobConn : null;
			PreparedStatement pstmt = null;
			try {
				// 从 Druid 连接池获取新连接
				if (conn == null) {
					conn = WBDruidFactory.getConnection((DruidDataSource) jobDS, props, logger);
				}
				pstmt = constructPreparedStatement(conn, dataScape, pstmt, dbName, tableName, partitionName,
						props);
				// 成功则返回结果
				if (pstmt != null) {
					rs = pstmt.executeQuery();
				}
				return rs;
			} catch (SQLException e) {
				// 判断是否为可重试的异常（需根据数据库类型细化逻辑）
				if (isTransientError(e, sqlExceptionKeywords) && attempt < maxRetries) {
					attempt++;
					logger.info("Retry attempt " + attempt);
					// 释放旧资源
					closePreparedStatement(pstmt, logger);
					closeConnection(conn, logger);
					try {
						Thread.sleep(retryDelay);
					} catch (InterruptedException ex) {
						Thread.currentThread().interrupt();
					}
				} else {
					throw new RuntimeException("Execution failed", e);
				}
			}
		}
		throw new RuntimeException("Max retries exceeded");
	}

	private boolean isTransientError(SQLException e, String sqlExceptionKeywords) {

		String message = e.getMessage().toLowerCase();
		if (StringUtils.isNotBlank(sqlExceptionKeywords)) {
			String[] keywordsArr = sqlExceptionKeywords.split(";");
			for (String keywords : keywordsArr) {
				String originKeywords = keywords.replace("-", " ");
				if (message.contains(originKeywords)) {
					return true;
				}
			}
		}
		return false;
	}


	//有source.type的处理方法
	private ResultSet handleHaveSourceType(Map<String, String> proObjectMap, Connection jobConn,
										   PreparedStatement pstmt, Logger log, Properties props, boolean virtualViewTableDenySwitch)
			throws SQLException, VirtualViewTableException{
		ResultSet rs = null;
		String sourceType = proObjectMap.get(DataChecker.SOURCE_TYPE);
		if(sourceType!=null){
			sourceType = sourceType.replace(" ", "").trim();
		}
		String dataObject = proObjectMap.get(DataChecker.DATA_OBJECT);
		if(dataObject!=null){
			dataObject = dataObject.replace(" ", "").trim();
		}
		String dataScape = "Table";
		if (dataObject.contains("{")) {
			dataScape = "Partition";
		}
		String dbName = null;
		String tableName = null;
		String partitionName = null;
		int rowCount = 0;
		if("job".equals(sourceType.toLowerCase())) {
			//log.info("-------------------------------------- search hive/spark/mr data ");
			//log.info("-------------------------------------- : " + dataObject);
			dbName = dataObject.split("\\.")[0];
			tableName = dataObject.split("\\.")[1];
			if("Partition".equals(dataScape)) {
				Matcher matcher = DATA_OBJECT_PATTERN.matcher(dataObject);
				if(matcher.find()){
					partitionName = matcher.group(1);
				}
				//容错代码 过滤用户多写的双引号或者单引号
				partitionName = partitionName.replace("\'", "").replace("\"", "");
				tableName = tableName.split("\\{")[0];
				pstmt = constructPreparedStatement(jobConn, dataScape, pstmt, dbName, tableName,
						partitionName, props);
			} else {
				pstmt = constructPreparedStatement(jobConn, dataScape, pstmt, dbName, tableName,
						partitionName, props);
			}
			if (virtualViewTableDenySwitch) {
				// 判断该表是否是视图表
				boolean isVirtualViewTable = checkVirtualViewTable(dbName, tableName, jobConn, log);
				if (isVirtualViewTable) {
					throw new VirtualViewTableException(
							"Virtual view table is not allowed to use DataChecker. ");
				}
			}
			if (null != pstmt) {
				rs = executeWithRetry(jobConn, props, dataScape, dbName, tableName, partitionName, log);
			}
			if (null != rs && rs.last()) {
				rowCount = rs.getRow();
			}
		} else if("bdp".equals(sourceType.toLowerCase())) {
			//log.info("-------------------------------------- search bdp data ");
			//log.info("-------------------------------------- : " + dataObject);
			dbName = dataObject.split("\\.")[0];
			tableName = dataObject.split("\\.")[1];
			partitionName = "";
			if("Partition".equals(dataScape)) {
				Matcher matcher = DATA_OBJECT_PATTERN.matcher(dataObject);
				if(matcher.find()){
					partitionName = matcher.group(1);
				}
				//容错代码 过滤用户多写的双引号或者单引号
				partitionName = partitionName.replace("\'", "").replace("\"", "");
				tableName = tableName.split("\\{")[0];
			}

			fetchMaskCode(proObjectMap, dbName, tableName, partitionName, log, props);
		}
		proObjectMap.put(Constants.DB_NAME, dbName);
		proObjectMap.put(Constants.TABLE_NAME, tableName);
		proObjectMap.put(Constants.PARTITION_NAME, partitionName);

		String logInfo = "database table partition Info : Table(" + dbName + "." + tableName + ") ";
		if (rowCount >= 1 || "success".equals(proObjectMap.get("maskStatus"))) {
			if ("Partition".equals(dataScape)) {
				logInfo = logInfo + "{" + partitionName + "} has arrived";
			} else {
				logInfo = logInfo + "has arrived. ";
			}
		} else {
			if ("Partition".equals(dataScape)) {
				logInfo = logInfo + "{" + partitionName + "} not arrived";
			} else {
				logInfo = logInfo + "not arrived. ";
			}
		}
		log.info(logInfo);
		return rs;
	}
	//没有source.type的处理方法
	private ResultSet handleNotSourceType(Map<String, String> proObjectMap, Connection jobConn,
										  PreparedStatement pstmt, Logger log, Properties props, boolean virtualViewTableDenySwitch)
			throws SQLException, VirtualViewTableException{
		String dataObject = proObjectMap.get(DataChecker.DATA_OBJECT);
		ResultSet rs = null;
		if (dataObject!=null) {
			dataObject = dataObject.replace(" ", "").trim();
		}
		String dataScape = "Table";
		if (dataObject.contains("{")) {
			dataScape = "Partition";
		}
		String dbName = null;
		String tableName = null;
		String partitionName = null;
		dbName = dataObject.split("\\.")[0];
		int rowCount = 0;
		if(!dbName.toLowerCase().contains("_ods")) {
			tableName = dataObject.split("\\.")[1];
			if("Partition".equals(dataScape)) {
				Matcher matcher = DATA_OBJECT_PATTERN.matcher(dataObject);
				if(matcher.find()){
					partitionName = matcher.group(1);
				}
				//容错代码 过滤用户多写的双引号或者单引号
				partitionName = partitionName.replace("\'", "").replace("\"", "");
				tableName = tableName.split("\\{")[0];
				pstmt = constructPreparedStatement(jobConn, dataScape, pstmt, dbName, tableName,
						partitionName, props);
			} else {
				pstmt = constructPreparedStatement(jobConn, dataScape, pstmt, dbName, tableName,
						partitionName, props);
			}

			if (virtualViewTableDenySwitch) {
				// 判断该表是否是视图表
				boolean isVirtualViewTable = checkVirtualViewTable(dbName, tableName, jobConn, log);
				if (isVirtualViewTable) {
					throw new VirtualViewTableException(
							"Virtual view table is not allowed to use DataChecker. ");
				}
			}

			if (null != pstmt) {
				rs = executeWithRetry(jobConn, props, dataScape, dbName, tableName, partitionName, log);
			}
			if (null != rs && rs.last()) {
				rowCount = rs.getRow();
			}
		} else if(dbName.toLowerCase().contains("_ods")) {
			dbName = dataObject.split("\\.")[0];
			tableName = dataObject.split("\\.")[1];
			partitionName = "";
			if("Partition".equals(dataScape)) {
				Matcher matcher = DATA_OBJECT_PATTERN.matcher(dataObject);
				if(matcher.find()){
					partitionName = matcher.group(1);
				}
				//容错代码 过滤用户多写的双引号或者单引号
				partitionName = partitionName.replace("\'", "").replace("\"", "");
				tableName = tableName.split("\\{")[0];
			}

			fetchMaskCode(proObjectMap, dbName, tableName, partitionName, log, props);
		}
		proObjectMap.put(Constants.DB_NAME, dbName);
		proObjectMap.put(Constants.TABLE_NAME, tableName);
		proObjectMap.put(Constants.PARTITION_NAME, partitionName);

		if (rowCount >= 1 || "success".equals(proObjectMap.get("maskStatus"))) {
			if ("Partition".equals(dataScape)) {
				log.info("database table partition Info : Table(" + dbName + "." + tableName +
						"{" + partitionName + "} ) has arrived. ");
			} else {
				log.info("database table partition Info : Table(" + dbName + "." + tableName +
						") has arrived. ");
			}
		} else {
			if ("Partition".equals(dataScape)) {
				log.info("database table partition Info : Table(" + dbName + "." + tableName +
						"{" + partitionName + "} ) not arrived. ");
			} else {
				log.info("database table partition Info : Table(" + dbName + "." + tableName +
						") not arrived. ");
			}
		}
		return rs;
	}

	public static void closeDruidDataSource(){
		DruidDataSource jobDSObject = (DruidDataSource)jobDS;
		if(jobDSObject != null){
			jobDSObject.close();
		}
	}

	private void fetchMaskCode(Map<String, String> proObjectMap, String dbName, String tableName, String partitionName,
							   Logger log, Properties props) {
		log.info("=============================调用BDP MASK接口查询数据状态==========================================");
		Map resultMap = new HashMap();
		String maskUrl = props.getProperty(DataChecker.MASK_URL);
		String cluster = "BDP";
		String nameIgnoreCase = props.getProperty(DataChecker.NAME_IGNOORE_CASE, "true").trim();
		if (!"false".equalsIgnoreCase(nameIgnoreCase)) {
			dbName = dbName.toLowerCase();
			tableName = tableName.toLowerCase();
		}
		Response response = null;
		RequestBody requestBody = new FormBody.Builder()
				.add("targetDb", dbName)
				.add("targetTable", tableName)
				.add("partition", partitionName)
				.build();
		try {
			Map<String, String> dataMap = HttpUtils.initSelectParams(props);
			log.info("request body:dbName--" + dbName + " tableName--" + tableName + " partitionName--" + partitionName);
			response = HttpUtils.httpClientHandleBase(maskUrl, requestBody, dataMap);
			handleResponse(response, proObjectMap, log);
		} catch (IOException e) {
			log.error("访问 BDP-MASK 远程查询BDAP业务表数据失败！",e);
			proObjectMap.put("maskStatus", "noPrepare");
		} catch (MaskCheckNotExistException e) {
			String errorMessage = "访问 BDP-MASK 远程查询BDAP业务表数据失败！" +
					"请检查业务数据库: " + dbName + ",业务数据表: " + tableName + "是否存在";
			log.error(errorMessage,e);
			throw new RuntimeException(errorMessage, e);
		}finally {
			if(response != null){
				response.close();
			}
		}
	}

	private void handleResponse(Response response, Map<String, String> proObjectMap, Logger log)
			throws IOException, MaskCheckNotExistException {
		int responseCode = response.code();
		ResponseBody body = response.body();
		if (responseCode == 200) {
			handleResponseBody(body, proObjectMap, log);
		} else {
			proObjectMap.put("maskStatus", "noPrepare");
		}
	}

	private void handleResponseBody(ResponseBody body, Map<String, String> proObjectMap, Logger log)
			throws IOException, MaskCheckNotExistException {
		String bodyStr = body.string();
		log.info("mask interface response body：" + bodyStr);
		Map entityMap = HttpUtils.getReturnMap(bodyStr);
		String codeValue = (String) entityMap.get("code");
		if ("200".equals(codeValue)) {
			proObjectMap.put("maskStatus", "success");
		} else if ("1011".equals(codeValue)) {
			throw new MaskCheckNotExistException("Mask check failed");
		} else {
			proObjectMap.put("maskStatus", "noPrepare");
		}
	}

	private void setString(PreparedStatement ptsm, int index, String value, Properties props) throws SQLException {
		String  nameIgnoreCase = props.getProperty(DataChecker.NAME_IGNOORE_CASE, "true").trim();
		if (!"false".equalsIgnoreCase(nameIgnoreCase)) {
			ptsm.setString(index, value.toLowerCase());
		} else {
			ptsm.setString(index, value);
		}
	}
}
