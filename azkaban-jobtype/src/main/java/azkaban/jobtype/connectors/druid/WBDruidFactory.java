package azkaban.jobtype.connectors.druid;

import bsp.encrypt.EncryptUtil;
import com.alibaba.druid.pool.DruidDataSource;
import org.apache.commons.lang.StringUtils;
import org.slf4j.Logger;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.Properties;

public class WBDruidFactory {

	private static DruidDataSource bdpInstance;
	private static DruidDataSource jobInstance;
	private static DruidDataSource dopsInstance;
	private static DruidDataSource msgInstance;
	private static DruidDataSource dataCheckerRecordInstance;

	/*public static DruidDataSource getBDPInstance(Properties props, Logger log) {
		if (bdpInstance == null ) {
			synchronized (WBDruidFactory.class) {
				if(bdpInstance == null) {
					try {
						bdpInstance = createDataSource(props, log, "BDP");
				    } catch (Exception e) {
				    	throw new RuntimeException("Error creating Druid DataSource", e);
				    }
				}
			}
		}
		return bdpInstance;
	}*/

	public static synchronized DruidDataSource getBDPInstance(Properties props, Logger log) {

		if (bdpInstance == null) {
			bdpInstance = createDataSource(props, log, "BDP");
		}
		return bdpInstance;
	}

	/*public static DruidDataSource getJobInstance(Properties props, Logger log) {
		if (jobInstance == null ) {
			synchronized (WBDruidFactory.class) {
				if(jobInstance == null) {
					try {
						jobInstance = createDataSource(props, log, "Job");
				    } catch (Exception e) {
				    	throw new RuntimeException("Error creating Druid DataSource", e);
				    }
				}
			}
		}
		return jobInstance;
	}*/
	public static synchronized DruidDataSource getJobInstance(Properties props, Logger log) {
		if (jobInstance == null) {
			jobInstance = createDataSource(props, log, "Job");
		}
		return jobInstance;
	}

	public static DataSource getDopsInstance(Properties props, Logger log) {
		if (dopsInstance == null) {
			dopsInstance = createDataSource(props, log, "Dops");
		}
		return dopsInstance;
	}

	public static DataSource getRecordInstance(Properties props, Logger log) {
		if (dataCheckerRecordInstance == null) {
			dataCheckerRecordInstance = createDataSource(props, log, "DataCheckerRecord");
		}
		return dataCheckerRecordInstance;
	}
	
	/*public static DruidDataSource getMsgInstance(Properties props, Logger log) {
		if (msgInstance == null ) {
			synchronized (WBDruidFactory.class) {
				if(msgInstance == null) {
					try {
						msgInstance = createDataSource(props, log, "Msg");
				    } catch (Exception e) {
				    	throw new RuntimeException("Error creating Druid DataSource", e);
				    }
				}
			}
		}
		return msgInstance;
	}*/

	public static synchronized DruidDataSource getMsgInstance(Properties props, Logger log) {
		if (msgInstance == null) {
			msgInstance = createDataSource(props, log, "Msg");
		}
		return msgInstance;
	}
	
	private static DruidDataSource createDataSource(Properties props, Logger log, String type) {
		String name = null;
		String url = null;
		String username = null;
		String password = null;
		
		if ("BDP".equals(type)) {
			name = props.getProperty("bdp.datachecker.jdo.option.name");
			url = props.getProperty("bdp.datachecker.jdo.option.url");
			username = props.getProperty("bdp.datachecker.jdo.option.username");
			password = props.getProperty("bdp.datachecker.jdo.option.password");
			try {
				String privateKey = props.getProperty("password.private.key");
				password = EncryptUtil.decrypt(privateKey, password);
			} catch (Exception e){
				log.error("password decore failed", e);
			}
		} else if ("Job".equals(type)) {
			name = props.getProperty("job.datachecker.jdo.option.name");
			url = props.getProperty("job.datachecker.jdo.option.url");
			username = props.getProperty("job.datachecker.jdo.option.username");
			password = props.getProperty("job.datachecker.jdo.option.password");
			try {
				String privateKey = props.getProperty("password.private.key");
				password = EncryptUtil.decrypt(privateKey, password);
			} catch (Exception e){
				log.error("password decore failed", e);
			}
		}else if("Msg".equals(type)){
			name = props.getProperty("msg.eventchecker.jdo.option.name");
			url = props.getProperty("msg.eventchecker.jdo.option.url");
			username = props.getProperty("msg.eventchecker.jdo.option.username");
			try {
				String privateKey = props.getProperty("password.private.key");
				String ciphertext = props.getProperty("msg.eventchecker.jdo.option.password");
				password = EncryptUtil.decrypt(privateKey, ciphertext);
			} catch (Exception e){
				log.error("password decore failed", e);
			}
		} else if ("Dops".equals(type)) {
			name = props.getProperty("dops.datachecker.jdo.option.name");
			url = props.getProperty("dops.datachecker.jdo.option.url");
			username = props.getProperty("dops.datachecker.jdo.option.username");
			try {
				String privateKey = props.getProperty("password.private.key");
				String ciphertext = props.getProperty("dops.datachecker.jdo.option.password");
				password = EncryptUtil.decrypt(privateKey, ciphertext);
			} catch (Exception e) {
				log.error("password decore failed", e);
			}
		} else if ("DataCheckerRecord".equals(type)) {
			name = props.getProperty("datachecker.record.jdo.option.name");
			url = props.getProperty("datachecker.record.jdo.option.url");
			username = props.getProperty("datachecker.record.jdo.option.username");
			password = props.getProperty("datachecker.record.jdo.option.password");
			try {
				String privateKey = props.getProperty("password.private.key");
				password = EncryptUtil.decrypt(privateKey, password);
			} catch (Exception e) {
				log.error("password decore failed", e);
			}
		}
		
		int initialSize = Integer.valueOf(props.getProperty("datachecker.jdo.option.initial.size", "1"));
		int maxActive = Integer.valueOf(props.getProperty("datachecker.jdo.option.max.active", "100"));
		int minIdle = Integer.valueOf(props.getProperty("datachecker.jdo.option.min.idle", "1"));
		long maxWait = Long.valueOf(props.getProperty("datachecker.jdo.optzion.max.wait", "60000"));
		String validationQuery = props.getProperty("datachecker.jdo.option.validation.quert", "SELECT 'x'");
		long timeBetweenEvictionRunsMillis = Long.valueOf(props.getProperty("datachecker.jdo.option.time.between.eviction.runs.millis", "6000"));
		long minEvictableIdleTimeMillis = Long.valueOf(props.getProperty("datachecker.jdo.option.evictable.idle,time.millis", "300000"));
		boolean testOnBorrow = Boolean.valueOf(props.getProperty("datachecker.jdo.option.test.on.borrow", "true"));
		int maxOpenPreparedStatements = Integer.valueOf(props.getProperty("datachecker.jdo.option.max.open.prepared.statements", "-1"));
		if (timeBetweenEvictionRunsMillis > minEvictableIdleTimeMillis) {
			timeBetweenEvictionRunsMillis = minEvictableIdleTimeMillis;
		}
		
		DruidDataSource ds = new DruidDataSource();
		
		if (StringUtils.isNotBlank(name)) {
			ds.setName(name);
		}
		
		ds.setUrl(url);
	    ds.setUsername(username);
	    ds.setPassword(password);
	    ds.setInitialSize(initialSize);
	    ds.setMinIdle(minIdle);
	    ds.setMaxActive(maxActive);
	    ds.setMaxWait(maxWait);
	    ds.setTestOnBorrow(testOnBorrow);
	    ds.setValidationQuery(validationQuery);
	    ds.setTimeBetweenEvictionRunsMillis(timeBetweenEvictionRunsMillis);
	    ds.setMinEvictableIdleTimeMillis(minEvictableIdleTimeMillis);
	    ds.setInitExceptionThrow(false);
	    if (maxOpenPreparedStatements > 0) {
	      ds.setPoolPreparedStatements(true);
	      ds.setMaxPoolPreparedStatementPerConnectionSize(
	          maxOpenPreparedStatements);
	    } else {
	      ds.setPoolPreparedStatements(false);
	    }
	    log.info("Druid data source initialed!");
	    return ds;
	}

	public static Connection getConnection(DruidDataSource ds, Properties props, Logger log) throws SQLException{
		int retryTimes = Integer.parseInt(props.getProperty("druid_connection_error_retry_attemps", "3"));
		long timeBetweenConnectErrorMillis = Long.parseLong(props.getProperty("druid_time_between_connection_error_millis", "3000"));
		Connection conn;
		int time = 0;
		while(true) {
			if (time >= retryTimes) {
				throw new SQLException("The connection failed after " + time + " attempts");
			}
			try {
				log.info("Start getting connection for the " + (time+1) + " time");
				conn = ds.getConnection(timeBetweenConnectErrorMillis);
				log.info("get connection success");
				break;
			} catch (SQLException e) {
				++time;
				log.error("Get DruidDataSource error, retry: " + time + "......");
			}
		}
		return conn;
	}
}


