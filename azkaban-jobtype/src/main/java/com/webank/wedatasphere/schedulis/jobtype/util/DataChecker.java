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

import com.webank.wedatasphere.schedulis.jobtype.connectors.druid.WBDataCheckerDao;
import java.util.Properties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DataChecker {
	public final static String SOURCE_TYPE = "source.type";
	public final static String DATA_OBJECT = "data.object";
	public final static String WAIT_TIME = "wait.time";
	public final static String QUERY_FREQUENCY = "query.frequency";
	public final static String TIME_SCAPE = "time.scape";
	public final static String MASK_URL = "bdp.mask.url";
	public final static String MASK_APP_ID = "bdp.mask.app.id";
	public final static String MASK_APP_TOKEN = "bdp.mask.app.token";

	private Properties p;
	private static final Logger logger = LoggerFactory.getLogger(DataChecker.class);

	public DataChecker(String jobName, Properties p) {
		this.p = p;
	}
	
	public void run() {
		if(p == null) {
			throw new RuntimeException("Properties is null. Can't continue");
		}
		if (!p.containsKey(SOURCE_TYPE)) {
//		    throw new RuntimeException("Must specify a " + SOURCE_TYPE
//		          + " key and value.");
			logger.info("Properties "  + SOURCE_TYPE + " value is Null !");
		}
		if (!p.containsKey(DATA_OBJECT)) {
//			throw new RuntimeException("Must specify a " + DATA_OBJECT
//			          + " key and value.");
			logger.info("Properties " + DATA_OBJECT + " value is Null !");
		}
		WBDataCheckerDao wbDao = WBDataCheckerDao.getInstance();
		
		boolean success = wbDao.validateTableStatusFunction(p, logger);
		if(!success) {
			throw new RuntimeException("Data not found.");
		}
		
	}
	
	public void cancel() throws Exception {
		WBDataCheckerDao.closeDruidDataSource();
		throw new RuntimeException("Kill this datachecker.");
	}
	
}
