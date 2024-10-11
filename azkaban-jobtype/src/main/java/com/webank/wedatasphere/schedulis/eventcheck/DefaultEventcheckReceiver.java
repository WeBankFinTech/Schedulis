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

package com.webank.wedatasphere.schedulis.eventcheck;

import org.apache.commons.lang.time.DateFormatUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Date;
import java.util.Properties;
import java.util.Random;

import com.webank.wedatasphere.schedulis.jobtype.util.EventChecker;
import com.webank.wedatasphere.schedulis.jobtype.util.ShellUtils;

/**
 * @author peacewong
 * @Title: EventcheckReceiver
 * @date 2019/9/1822:17
 */
public class DefaultEventcheckReceiver extends AbstractEventCheckReceiver{
    String todayStartTime;
    String todayEndTime;
    String allStartTime;
    String allEndTime;
    String nowStartTime;

    public DefaultEventcheckReceiver(Properties props) {
        initECParams(props);
        initReceiverTimes();
    }

    private void initReceiverTimes(){
        todayStartTime = DateFormatUtils.format(new Date(), "yyyy-MM-dd 00:00:00");
        todayEndTime = DateFormatUtils.format(new Date(), "yyyy-MM-dd 23:59:59");
        allStartTime = DateFormatUtils.format(new Date(), "10000-01-01  00:00:00");
        allEndTime = DateFormatUtils.format(new Date(), "9999-12-31  23:59:59");
        nowStartTime = DateFormatUtils.format(new Date(), "yyyy-MM-dd HH:mm:ss");
    }

    @Override
    public boolean reciveMsg(int jobId, Properties props, Logger log) {
        Long startTime = System.currentTimeMillis();
        Long currentTime = startTime;
        Double doubleWaitTime = Double.valueOf(waitTime) * 3600 * 1000;
        Long waitTime = Long.valueOf(doubleWaitTime.longValue());
        int queryFrequency = Integer.valueOf(query_frequency);
        if(wait_for_time!=null && props.containsKey(EventChecker.WAIT_FOR_TIME)){
            waitForTime(log,waitTime);
        }
        log.info("-------------------------------------- waiting time(unit：millisecond) : " + waitTime);
        log.info("-------------------------------------- Number of query frequency in waiting time  : " + queryFrequency);
        Long sleepTime = waitTime / queryFrequency;
        if(sleepTime < 60000L){
            log.info("your setting is less than the minimum polling time(60s), the system will automatically set it to 60s");
            sleepTime = 60000L;
        }
        boolean result = false;
        while ((currentTime - startTime) <= waitTime) {
            boolean flag = false;
            try{
                //step1
                //这里让线程随机休眠0到1秒，防止多个receiver在同一时刻重复更新event_status;
                Thread.sleep(new Random().nextInt(1000));
                String lastMsgId = getOffset(jobId,props,log);
                String[] executeType = createExecuteType(jobId,props,log,lastMsgId);
                if(executeType!=null && executeType.length ==3){
                    //step2
                    String[] consumedMsgInfo = getMsg(props, log,executeType);
                    if(consumedMsgInfo!=null && consumedMsgInfo.length == 4){
                        //step3
                        flag = updateMsgOffset(jobId,props,log,consumedMsgInfo,lastMsgId);
                    }else if (trigger_time != null && !"".equals(trigger_time.trim())){
                        flag = autoTrigger(log,props);
                    }
                }else{
                    log.error("executeType error {} " + Arrays.toString(executeType));
                    return result;
                }
                int i = 1 ;
                String azkabanPid = "0";
                while(i < 5){
                    azkabanPid = ShellUtils.getPPid(ShellUtils.getPid());
                    if("1".equals(azkabanPid)){
                        i++;
                        Thread.sleep(new Random().nextInt(1000));
                    }else{
                        break;
                    }
                }
                if ("1".equals(azkabanPid)) {
                    throw new RuntimeException("Azkaban-exec has been terminated！");
                }
            }catch (Exception e){
                log.error("EventChecker failed to receive the message {}" + e);
                return result;
            }
            if (flag) {
                result = flag;
                break;
            }
            try {
                Thread.sleep(sleepTime);
            } catch (InterruptedException e) {
                throw new RuntimeException("An exception occurred in the waiting for rotation training！" + e);
            }
            currentTime = System.currentTimeMillis();
        }
        if (!result) {
            throw new RuntimeException("EventChecker receives the message timeout！");
        }
        return result;
    }

    private String[] createExecuteType(int jobId, Properties props, Logger log,String lastMsgId){
        boolean receiveTodayFlag = (null != receiveToday && "true".equals(receiveToday.trim().toLowerCase()));
        boolean afterSendFlag = (null != afterSend && "true".equals(afterSend.trim().toLowerCase()));
        String[] executeType = null;
        try {
            if ("0".equals(lastMsgId)){
                if(receiveTodayFlag){
                    if(afterSendFlag){
                        executeType = new String[]{nowStartTime,todayEndTime,"0"};
                    }else{
                        executeType = new String[]{todayStartTime,todayEndTime,"0"};
                    }
                }else{
                    if(afterSendFlag){
                        executeType = new String[]{nowStartTime,allEndTime,"0"};
                    }else{
                        executeType = new String[]{allStartTime,allEndTime,"0"};
                    }
                }
            }else{
                if(receiveTodayFlag){
                    if(afterSendFlag){
                        executeType = new String[]{nowStartTime,todayEndTime,lastMsgId};
                    }else{
                        executeType = new String[]{todayStartTime,todayEndTime,lastMsgId};
                    }
                }else{
                    if(afterSendFlag){
                        executeType = new String[]{nowStartTime,allEndTime,lastMsgId};
                    }else{
                        executeType = new String[]{allStartTime,allEndTime,lastMsgId};
                    }
                }
            }
        }catch(Exception e){
            log.error("create executeType failed {}" + e);
        }
        return executeType;
    }

    private void waitForTime(Logger log,Long waitTime){
        String waitForTime = wait_for_time;
        String formatWaitForTime = DateFormatUtils.format(new Date(),"yyyy-MM-dd " + waitForTime + ":00");
        DateFormat fmt =new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        Date targetWaitTime = null;
        try {
            targetWaitTime = fmt.parse(formatWaitForTime);
        } catch (ParseException e) {
            log.error("parse date failed {}" + e);
        }

        log.info("It will success at a specified time: " + targetWaitTime);
        long wt = targetWaitTime.getTime() - System.currentTimeMillis();
        if(wt > 0){
            //wt must less than wait.time
            if(wt <= waitTime){
                log.info("EventChecker will wait "+ wt + " milliseconds before starting execution");
                try {
                    Thread.sleep(wt);
                } catch (InterruptedException e) {
                    throw new RuntimeException("EventChecker throws an exception during the waiting time {}"+e);
                }
            }else{
                throw new RuntimeException("The waiting time from Job starttime to wait.for.time"+ wt +"(ms) greater than wait.time , unreasonable setting！");
            }
        }else{
            log.info("EventChecker has reached the specified time");
        }
    }

    private boolean autoTrigger(Logger log,Properties props){
        String formatTriggerTime = DateFormatUtils.format(new Date(),"yyyy-MM-dd " + trigger_time + ":00");
        DateFormat fmt =new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        Date targetTriggerTime = null;
        try {
            targetTriggerTime = fmt.parse(formatTriggerTime);
        } catch (ParseException e) {
            log.error("parse date failed {}" + e);
        }
        long wt = targetTriggerTime.getTime() - System.currentTimeMillis();
        if(wt < 0){
            log.info("EventChecker has reached the automatic trigger time point:" + targetTriggerTime);
            if (null == trigger_param) {
                props.put(EventChecker.MSG, "NULL");
            } else {
                props.put(EventChecker.MSG, trigger_param);
            }
            return true;
        }
        return false;
    }
}
