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

package com.webank.wedatasphere.schedulis.jobtype.connectors.druid;

import java.util.Base64;
import java.util.Properties;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;

import com.alibaba.druid.pool.DruidDataSource;

public class WBDruidFactory {
	private static DruidDataSource bdpInstance;
	private static DruidDataSource jobInstance;
	private static DruidDataSource msgInstance;
	
	public static DruidDataSource getBDPInstance(Properties props, Logger log) {
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
	}
	
	public static DruidDataSource getJobInstance(Properties props, Logger log) {
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
	}
	
	public static DruidDataSource getMsgInstance(Properties props, Logger log) {
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
	}
	
	private static DruidDataSource createDataSource(Properties props, Logger log, String type) {
		String name = null;
		String url = null;
		String username = null;
		String password = null;
		
		if (type.equals("BDP")) {
			name = props.getProperty("bdp.datachecker.jdo.option.name");
			url = props.getProperty("bdp.datachecker.jdo.option.url");
			username = props.getProperty("bdp.datachecker.jdo.option.username");
			password = props.getProperty("bdp.datachecker.jdo.option.password");
			try {
				password = new String(Base64.getDecoder().decode(props.getProperty("bdp.datachecker.jdo.option.password").getBytes()),"UTF-8");
			} catch (Exception e){
				log.error("password decore failed" + e);
			}
		} else if (type.equals("Job")) {
			name = props.getProperty("job.datachecker.jdo.option.name");
			url = props.getProperty("job.datachecker.jdo.option.url");
			username = props.getProperty("job.datachecker.jdo.option.username");
			password = props.getProperty("job.datachecker.jdo.option.password");
			try {
				password = new String(Base64.getDecoder().decode(props.getProperty("job.datachecker.jdo.option.password").getBytes()),"UTF-8");
			} catch (Exception e){
				log.error("password decore failed" + e);
			}
		}else if(type.equals("Msg")){
			name = props.getProperty("msg.eventchecker.jdo.option.name");
			url = props.getProperty("msg.eventchecker.jdo.option.url");
			username = props.getProperty("msg.eventchecker.jdo.option.username");
			try {
				password = new String(Base64.getDecoder().decode(props.getProperty("msg.eventchecker.jdo.option.password").getBytes()),"UTF-8");
			} catch (Exception e){
				log.error("password decore failed" + e);
			}
		}
		
		int initialSize = Integer.valueOf(props.getProperty("datachecker.jdo.option.initial.size", "1"));
		int maxActive = Integer.valueOf(props.getProperty("datachecker.jdo.option.max.active", "100"));
		int minIdle = Integer.valueOf(props.getProperty("datachecker.jdo.option.min.idle", "1"));
		long maxWait = Long.valueOf(props.getProperty("datachecker.jdo.option.max.wait", "60000"));
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
}
