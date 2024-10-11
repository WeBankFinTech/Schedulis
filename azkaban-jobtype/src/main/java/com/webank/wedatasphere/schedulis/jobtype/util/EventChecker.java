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

import com.webank.wedatasphere.schedulis.jobtype.connectors.druid.WBEventCheckerDao;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.management.ManagementFactory;
import java.util.Properties;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.webank.wedatasphere.schedulis.eventcheck.AbstractEventCheck;
import azkaban.utils.Props;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class EventChecker {
	public final static String WAIT_TIME = "wait.time";
	public final static String WAIT_FOR_TIME = "wait.for.time";
	public final static String QUERY_FREQUENCY = "query.frequency";
	public final static String MSGTYPE="msg.type";
	public final static String SENDER="msg.sender";
	public final static String RECEIVER="msg.receiver";
	public final static String TOPIC="msg.topic";
	public final static String MSGNAME="msg.name";
	public final static String MSG="msg.body";
	public final static String EXEC_ID = "azkaban.flow.execid";
	public final static String SAVE_KEY="msg.savekey";
	public final static String USER_TIME="msg.init.querytime";
	public final static String TODAY="msg.rece.today";
	public final static String AFTERSEND="msg.after.send";
	public final static String TRIGGER_TIME="auto.trigger.time";
	public final static String TRIGGER_PARAM="auto.trigger.param";

	private Properties p;
	private String jobId;
	private WBEventCheckerDao wbDao=null;

	private static final Logger logger = LoggerFactory.getLogger(EventChecker.class);

	public EventChecker(String jobName, Properties p) {
		this.p = p;
		this.jobId = p.getProperty(EXEC_ID);
	}

	public void run() {
		getPid();
		if(p == null) {
			throw new RuntimeException("Properties is null. Can't continue");
		}
		if (checkParamMap(p, MSGTYPE)) {
			throw new RuntimeException("parameter " + MSGTYPE + " can not be blank.");
		}
		if (checkParamMap(p, TOPIC)) {
			throw new RuntimeException("parameter " + TOPIC + " can not be blank.");
		}
		else{
			String topic= p.getProperty(TOPIC);
			if(!topic.matches("[^_]*_[^_]*_[^_]*")){
				throw new RuntimeException("Error format of topic parameter. Accept: XX_XX_XX.");
			}
		}
		if (checkParamMap(p, MSGNAME)) {
			throw new RuntimeException("parameter " + MSGNAME + " can not be blank.");
		}
		wbDao = WBEventCheckerDao.getInstance();
		int execId=Integer.parseInt(jobId);
		boolean success=false;
		if(p.getProperty(MSGTYPE).equals("RECEIVE")){
			if (checkParamMap(p, RECEIVER)) {
			    throw new RuntimeException("parameter " + RECEIVER + " can not be blank.");
			}else{
				String receiver= p.getProperty(RECEIVER);
				if(!receiver.matches("[^@]*@[^@]*@[^@]*")){
					throw new RuntimeException("Error format of receiver parameter. Accept: XX@XX@XX.");
				}
			}
			checkTimeParam(p,WAIT_FOR_TIME);
			String userTime = checkTimeParamMap(p, USER_TIME);
			if(StringUtils.isNotEmpty(userTime)){
				p.put(USER_TIME, userTime);
			}
			success = wbDao.reciveMsg(execId, p, logger);
			if(!success) {
				throw new RuntimeException("Failed Receive message.");
			}
		}else if(p.getProperty(MSGTYPE).equals("SEND")){
			if (checkParamMap(p, SENDER)) {
				throw new RuntimeException("parameter " + SENDER + " can not be blank.");
			}else{
				String sender= p.getProperty(SENDER);
				if(!sender.matches("[^@]*@[^@]*@[^@]*")){
					throw new RuntimeException("Error format of  sender parameter. Accept: XX@XX@XX.");
				}
			}
//			if (checkParamMap(p, MSG)) {
//				throw new RuntimeException("Must specify a " + MSG
//				          + " key and value.");
//			}
			if(p.containsKey(MSG) && StringUtils.isNotEmpty(p.getProperty(MSG)) && p.getProperty(MSG).length() > 250){
		    	throw new RuntimeException("parameter " + MSG + " length less than 250 !");
		    }
			success = wbDao.sendMsg(execId, p, logger);
			if(!success) {
				throw new RuntimeException("Failed Send message.");
			}
		}else{
			  throw new RuntimeException("Please input correct parameter of msg.type, Select RECEIVE Or SEND.");
		}
	}

	public Props getJobGeneratedProperties(){
		Props props = new Props();
		String msgBody=p.getProperty(MSG, "{}");
		String saveKey=p.getProperty(SAVE_KEY,"msg.body");
	    props.put(saveKey, msgBody);
	    logger.info("Output msg body: "+msgBody);
	    return props;
	}

	public void cancel() throws InterruptedException {
		AbstractEventCheck.closeDruidDataSource();
		throw new RuntimeException("Kill this eventchecker.");
	}

	private boolean checkParamMap(Properties p, String key){
	    boolean checkFlag = false;
	    if(!p.containsKey(key)){//判断参数是否存在
	        throw new RuntimeException("parameter " + key + " is Empty.");
	    }
	    if(p.containsKey(key)){//判断参数是否为空字符串
		    if(StringUtils.isEmpty(p.getProperty(key))){
	        checkFlag = true;
	      }
	    }
	    if(!MSG.equals(key) && StringUtils.contains(p.getProperty(key), " ")){
			throw new RuntimeException("parameter " + key + " can not contains space !");
		}
	    if(!checkNoStandardStr(p.getProperty(key))){
	    	throw new RuntimeException("parameter " + key + " Accept letter and number and _@- only.");
	    }
	    if(p.getProperty(key).length() > 45){
	    	throw new RuntimeException("parameter " + key + " length less than 45 !");
	    }
	    return checkFlag;
    }

	private boolean checkNoStandardStr(String param){
        Pattern pattern = Pattern.compile("[a-zA-Z_0-9@\\-]+");
        Matcher matcher = pattern.matcher(param);
        return matcher.matches();
	}

	private void checkTimeParam(Properties p, String key){
		if(p.containsKey(key)){
			String waitForTime= p.getProperty(key);
			if(!waitForTime.matches("^(0?[0-9]|1[0-9]|2[0-3]):(0?[0-9]|[1-5][0-9])$")){
				throw new RuntimeException("Parameter " + key + " Time format error ! For example: HH:mm");
			}
		}
	}

	private String checkTimeParamMap(Properties p, String key){
	    if(p.containsKey(key)){
	    	String userTime = p.getProperty(key);
	    	Pattern ptime = Pattern.compile("^([1][7-9][0-9][0-9]|[2][0][0-9][0-9])(\\-)([0][1-9]|[1][0-2])(\\-)([0-2][1-9]|[3][0-1])(\\s)([0-1][0-9]|[2][0-3])(:)([0-5][0-9])(:)([0-5][0-9])$");
	        Matcher m = ptime.matcher(userTime);
	        if(!m.matches()){
	        	throw new RuntimeException("Parameter " + key + " Time format error ! For example: yyyy-MM-dd HH:mm:ss");
	        }
		    return userTime;
	    }else{
	    	return null;
	    }
    }

	private String getPid(){
		// get name representing the running Java virtual machine.
		String name = ManagementFactory.getRuntimeMXBean().getName();
		System.out.println(name);
		// get pid
		String pid = name.split("@")[0];
		logger.info("EventCheck Pid is:" + pid);
		return pid;
	}

	public static void main(String[] args) {
		Properties p = new Properties();
		p.put("azkaban.flow.execid","111");

		p.put("msg.type","RECEIVE");
		p.put("msg.receiver","project@job@vv");
		p.put("msg.topic","bdp_new_test");
		p.put("msg.name","TestCheck");
		p.put("msg.savekey","msg.body");
		p.put("query.frequency","60");
		p.put("wait.time","1");
		p.put("msg.rece.today","true");
		p.put("msg.after.send","true");
		p.put("auto.trigger.time","12:50:45");
		p.put("auto.trigger.param","");
//		p.put("wait.for.time","22:00");

//		p.put("msg.type","SEND");
//		p.put("msg.sender","project@job@vv");
//		p.put("msg.topic","bdp_new_test");
//		p.put("msg.name","TestCheck");
//		p.put("msg.body","msg.body");

		p.put("msg.eventchecker.jdo.option.name","msg");
		p.put("msg.eventchecker.jdo.option.url","jdbc:mysql://locahost:port/wtss_qyh_test?useUnicode=true&characterEncoding=UTF-8");
		p.put("msg.eventchecker.jdo.option.username","username");
		p.put("msg.eventchecker.jdo.option.password","password");

		EventChecker ec = new EventChecker("AA",p);
		ec.run();
	}


}
