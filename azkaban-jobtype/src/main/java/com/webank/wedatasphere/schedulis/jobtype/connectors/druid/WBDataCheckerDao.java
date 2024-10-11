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

import com.webank.wedatasphere.schedulis.jobtype.commons.MaskCheckNotExistException;
import com.webank.wedatasphere.schedulis.jobtype.util.HttpUtils;
import com.webank.wedatasphere.schedulis.jobtype.util.DataChecker;
import java.io.IOException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.sql.DataSource;

import okhttp3.FormBody;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.ResponseBody;
import org.slf4j.Logger;
import com.alibaba.druid.pool.DruidDataSource;

public class WBDataCheckerDao {
	private static DataSource bdpDS;
	private static DataSource jobDS;
	private static WBDataCheckerDao instance;
	//获取当前类的实例
	public static WBDataCheckerDao getInstance() {
		if (instance == null) {
			synchronized (WBDataCheckerDao.class) {
				if (instance == null) {
					instance = new WBDataCheckerDao();
				}
			}
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
			} catch (SQLException e) {
				log.error("Error closing result set", e);
			}
		}
		if (pstmt != null) {
			try {
				pstmt.close();
			} catch (SQLException e) {
				log.error("Error closing prepared statement", e);
			}
		}
	}

	private static final String SQL_SOURCE_TYPE_JOB_TABLE = "SELECT * FROM DBS d JOIN TBLS t ON t.DB_ID = d.DB_ID WHERE d.NAME=? AND t.TBL_NAME=? ;";
	private static final String SQL_SOURCE_TYPE_JOB_PARTITION = "SELECT * FROM DBS d JOIN TBLS t ON t.DB_ID = d.DB_ID JOIN PARTITIONS p ON p.TBL_ID = t.TBL_ID WHERE d.NAME=? AND t.TBL_NAME=? AND p.PART_NAME=? ;";
	private static final String SQL_SOURCE_TYPE_BDP = "SELECT * FROM desktop_bdapimport WHERE bdap_db_name = ? AND bdap_table_name = ? AND target_partition_name = ? AND status = '1';";
	private static final String SQL_SOURCE_TYPE_BDP_WITH_TIME_CONDITION = "SELECT * FROM desktop_bdapimport WHERE bdap_db_name = ? AND bdap_table_name = ? AND target_partition_name = ? AND (UNIX_TIMESTAMP() - UNIX_TIMESTAMP(STR_TO_DATE(modify_time, '%Y-%m-%d %H:%i:%s'))) <= ? AND status = '1';";

	//数据库校验总方法
	public boolean validateTableStatusFunction(Properties props, Logger log) {
		if (bdpDS == null) {
			//通过alibaba的druid数据库连接池获取BDP数据库连接
			bdpDS = WBDruidFactory.getBDPInstance(props, log);
			if (bdpDS == null) {
				log.error("Error getting Druid DataSource instance");
				return false;
			}
		}
		if (jobDS == null) {
			//通过alibaba的druid数据库连接池获取JOB数据库连接
			jobDS = WBDruidFactory.getJobInstance(props, log);
			if (jobDS == null) {
				log.error("Error getting Druid DataSource instance");
				return false;
			}
		}

		PreparedStatement pstmt = null;
		Connection jobConn = null;
		Connection bdpConn = null;

		try {
			for (Map.Entry<Object, Object> entry : props.entrySet()) {
				entry.setValue(entry.getValue().toString().replace(" ", "").trim());
			}
		}catch (Exception e){
			throw new RuntimeException("remove job space char failed",e);
		}

		String singleDataObject = props.getProperty(DataChecker.DATA_OBJECT);
		if(singleDataObject!=null){
			singleDataObject = singleDataObject.replace(" ", "").trim();
		}
		log.info("=============================Data Check Start==========================================");
		log.info("(datachecker info) database table partition Info : " + singleDataObject);

		//组装查询的数据资源集合
		List<Map<String, String>> dataObjectList = handleSeparationProperties(props);

		boolean[] flagList = new boolean[dataObjectList.size()];
		Arrays.fill(flagList, false);
		Long startTime = System.currentTimeMillis();
		Long currentTime = startTime;
		Long waitTime = Long.valueOf(props.getProperty(DataChecker.WAIT_TIME, "1")) * 3600 * 1000;
		int queryFrequency = Integer.valueOf(props.getProperty(DataChecker.QUERY_FREQUENCY, "5"));
		String timeScape = props.getProperty(DataChecker.TIME_SCAPE, "NULL");

		log.info("(datachecker info) wait time : " + waitTime);
		log.info("(datachecker info) quert frequency : " + queryFrequency);
		log.info("(datachecker info) time scape : " + timeScape);

		Long sleepTime = waitTime / queryFrequency;
		boolean result = false;
		while((currentTime - startTime) <= waitTime) {
			boolean flag = true;
			ResultSet rs = null;
			try {
				jobConn = jobDS.getConnection();
				bdpConn = bdpDS.getConnection();
				for(int i = 0; i < dataObjectList.size(); i++) {
					if(flagList[i]) {
						continue;
					}
					Map<String, String> proObjectMap = dataObjectList.get(i);

					if(proObjectMap.containsKey(DataChecker.SOURCE_TYPE)){
						rs = handleHaveSourceType(proObjectMap, pstmt, jobConn, bdpConn, rs, timeScape, log, props);
					}else{
						rs = handleNotSourceType(proObjectMap, pstmt, jobConn, bdpConn, rs, timeScape, log, props);
					}

					int rowCount = 0;
					if(rs.last()) {
						rowCount = rs.getRow();
					}
					if(rowCount >= 1){
						log.info("(datachecker info) get maskdb result success");
					}else{
						log.info("(datachecker info) get maskdb result failed");
					}
					if(rowCount >= 1 || "success".equals(proObjectMap.get("maskStatus"))) {
						flagList[i] = true;
					}
					flag = flag & flagList[i];
				}
			} catch (SQLException e) {
				throw new RuntimeException("get datachecker result failed",e);
			} finally {
				closeQueryRef(rs, pstmt, log);
				closeConnection(jobConn, log);
				closeConnection(bdpConn, log);
				log.info("=============================Data Check End==========================================");
			}
			if(flag) {
				result = flag;
				break;
			}
			try {
				Thread.sleep(sleepTime);
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
			currentTime = System.currentTimeMillis();
		}
		return result;
	}

	//Properties 参数分组
	private List<Map<String, String>> handleSeparationProperties(Properties p){
		//source.type和data.object的分组集合
		List<Map<String, String>> proList = new ArrayList<>();

		for(Object key : p.keySet()){
			Map<String, String> proMap = new HashMap<>();
			String skey = String.valueOf(key);
			//根据data.object的个数去拼装数据
			if(skey.contains(DataChecker.DATA_OBJECT)){
				String[] keyArr = skey.split("\\.");
				//有后缀
				if(keyArr.length == 3){
					//获取后缀数字
					String keyNum = keyArr[2];
					//组装成对的Key
					String stKey = DataChecker.SOURCE_TYPE + "." + keyNum;
					String doKey = DataChecker.DATA_OBJECT + "." + keyNum;
					//source.type可能会不存在
					if(null != p.get(stKey)){
						proMap.put(DataChecker.SOURCE_TYPE, String.valueOf(p.get(stKey)));
					}
					proMap.put(DataChecker.DATA_OBJECT, String.valueOf(p.get(doKey)));
				}else{//没有后缀
					String stKey = DataChecker.SOURCE_TYPE;
					String doKey = DataChecker.DATA_OBJECT;
					//source.type可能会不存在
					if(null != p.get(stKey)){
						proMap.put(DataChecker.SOURCE_TYPE, String.valueOf(p.get(stKey)));
					}
					proMap.put(DataChecker.DATA_OBJECT, String.valueOf(p.get(doKey)));
				}
				proList.add(proMap);
			}
		}
		return proList;
	}


	//有source.type的处理方法
	private ResultSet handleHaveSourceType(Map<String, String>  proObjectMap, PreparedStatement pstmt,
										   Connection jobConn, Connection bdpConn, ResultSet rs, String timeScape, Logger log, Properties props) throws SQLException{
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
		Pattern pattern = Pattern.compile("\\{([^\\}]+)\\}");
		if(sourceType.toLowerCase().equals("job")) {
			log.info("-------------------------------------- search hive/spark/mr data ");
			log.info("-------------------------------------- : " + dataObject);
			dbName = dataObject.split("\\.")[0];
			tableName = dataObject.split("\\.")[1];
			if(dataScape.equals("Partition")) {
//	    		partitionName = tableName.split("\\{")[1];
//	    		partitionName = partitionName.substring(0, partitionName.length()-1).replace("\'", "").replace("\"", "");
				Matcher matcher = pattern.matcher(dataObject);
				if(matcher.find()){
					partitionName = matcher.group(1);
				}
				//容错代码 过滤用户多写的双引号或者单引号
				partitionName = partitionName.replace("\'", "").replace("\"", "");
				tableName = tableName.split("\\{")[0];
				pstmt = jobConn.prepareCall(SQL_SOURCE_TYPE_JOB_PARTITION);
				pstmt.setString(1, dbName);
				pstmt.setString(2, tableName);
				pstmt.setString(3, partitionName);
			} else {
				pstmt = jobConn.prepareCall(SQL_SOURCE_TYPE_JOB_TABLE);
				pstmt.setString(1, dbName);
				pstmt.setString(2, tableName);
			}
			rs = pstmt.executeQuery();
		} else if(sourceType.toLowerCase().equals("bdp")) {
			log.info("-------------------------------------- search bdp data ");
			log.info("-------------------------------------- : " + dataObject);
			dbName = dataObject.split("\\.")[0];
			tableName = dataObject.split("\\.")[1];
			partitionName = "";
			if(dataScape.equals("Partition")) {
				Matcher matcher = pattern.matcher(dataObject);
				if(matcher.find()){
					partitionName = matcher.group(1);
				}
				//容错代码 过滤用户多写的双引号或者单引号
				partitionName = partitionName.replace("\'", "").replace("\"", "");
				tableName = tableName.split("\\{")[0];
			}

			if (timeScape.equals("NULL")) {
				pstmt = bdpConn.prepareCall(SQL_SOURCE_TYPE_BDP);
			} else {
				pstmt = bdpConn.prepareCall(SQL_SOURCE_TYPE_BDP_WITH_TIME_CONDITION);
				pstmt.setInt(4, Integer.valueOf(timeScape) * 3600);
			}
			pstmt.setString(1, dbName);
			pstmt.setString(2, tableName);
			pstmt.setString(3, partitionName);
			rs = pstmt.executeQuery();
			fetchMaskCode(proObjectMap, dbName, tableName, partitionName, log, props);
		}
		return rs;
	}
	//没有source.type的处理方法
	private ResultSet handleNotSourceType(Map<String, String>  proObjectMap, PreparedStatement pstmt,
										  Connection jobConn, Connection bdpConn, ResultSet rs, String timeScape, Logger log, Properties props) throws SQLException{
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
		Pattern pattern = Pattern.compile("\\{([^\\}]+)\\}");
		dbName = dataObject.split("\\.")[0];
		if(!dbName.contains("_ods")) {
			log.info("-------------------------------------- search hive/spark/mr data ");
			log.info("-------------------------------------- : " + dataObject);
			tableName = dataObject.split("\\.")[1];
			if(dataScape.equals("Partition")) {
				Matcher matcher = pattern.matcher(dataObject);
				if(matcher.find()){
					partitionName = matcher.group(1);
				}
				//容错代码 过滤用户多写的双引号或者单引号
				partitionName = partitionName.replace("\'", "").replace("\"", "");
				tableName = tableName.split("\\{")[0];
				pstmt = jobConn.prepareCall(SQL_SOURCE_TYPE_JOB_PARTITION);
				pstmt.setString(1, dbName);
				pstmt.setString(2, tableName);
				pstmt.setString(3, partitionName);
			} else {
				pstmt = jobConn.prepareCall(SQL_SOURCE_TYPE_JOB_TABLE);
				pstmt.setString(1, dbName);
				pstmt.setString(2, tableName);
			}
			rs = pstmt.executeQuery();
		} else if(dbName.contains("_ods")) {
			log.info("-------------------------------------- search bdp data ");
			log.info("-------------------------------------- : " + dataObject);
			dbName = dataObject.split("\\.")[0];
			tableName = dataObject.split("\\.")[1];
			partitionName = "";
			if(dataScape.equals("Partition")) {
				Matcher matcher = pattern.matcher(dataObject);
				if(matcher.find()){
					partitionName = matcher.group(1);
				}
				//容错代码 过滤用户多写的双引号或者单引号
				partitionName = partitionName.replace("\'", "").replace("\"", "");
				tableName = tableName.split("\\{")[0];
			}
			if (timeScape.equals("NULL")) {
				pstmt = bdpConn.prepareCall(SQL_SOURCE_TYPE_BDP);
			} else {
				pstmt = bdpConn.prepareCall(SQL_SOURCE_TYPE_BDP_WITH_TIME_CONDITION);
				pstmt.setInt(4, Integer.valueOf(timeScape) * 3600);
			}
			pstmt.setString(1, dbName);
			pstmt.setString(2, tableName);
			pstmt.setString(3, partitionName);
			rs = pstmt.executeQuery();
			fetchMaskCode(proObjectMap, dbName, tableName, partitionName, log, props);
		}
		return rs;
	}

	public static void closeDruidDataSource(){
		DruidDataSource bdpDSObject = (DruidDataSource)bdpDS;
		DruidDataSource jobDSObject = (DruidDataSource)jobDS;

		if(bdpDSObject != null){
			bdpDSObject.close();
		}
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
		try {
			RequestBody requestBody = new FormBody.Builder()
					.add("targetDb", dbName)
					.add("targetTable", tableName)
					.add("partition", partitionName)
					.build();
			Map<String, String> dataMap = HttpUtils.initSelectParams(props);
			log.info("request body:dbName--" + dbName + " tableName--" + tableName + " partitionName--" + partitionName);
			Response response = HttpUtils.httpClientHandleBase(maskUrl, requestBody, dataMap);
			handleResponse(response, proObjectMap, log);
		} catch (IOException e) {
			log.error("访问 BDP-MASK 远程查询BDAP业务表数据失败！");
			proObjectMap.put("maskStatus", "noPrepare");
		} catch (MaskCheckNotExistException e) {
			String errorMessage = "访问 BDP-MASK 远程查询BDAP业务表数据失败！" +
					"请检查业务数据库: " + dbName + ",业务数据表: " + tableName + "是否存在";
			log.error(errorMessage);
			throw new RuntimeException(errorMessage, e);
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
}
