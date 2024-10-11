package azkaban.eventcheck;

import azkaban.Constants;
import azkaban.alert.Alerter;
import azkaban.alerter.EventCheckerAlerterHolder;
import azkaban.jobtype.util.EventChecker;
import azkaban.jobtype.util.ShellUtils;
import org.apache.commons.lang.StringUtils;
import org.apache.commons.lang.time.DateFormatUtils;
import org.slf4j.Logger;

import java.sql.SQLException;
import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.*;

/**
 * @author georgeqiao
 * @Title: EventcheckReceiver
 * @ProjectName WTSS
 * @date 2019/9/1822:17
 */
public class DefaultEventcheckReceiver extends AbstractEventCheckReceiver{
    String todayStartTime;
    String todayEndTime;
    String allStartTime;
    String allEndTime;
    String nowStartTime;
    Date expectTime;

    public DefaultEventcheckReceiver(Properties props) {
        initECParams(props);
        initReceiverTimes(props);
    }

    private void initReceiverTimes(Properties props) {
        Date now = new Date();
        if (props.containsKey("hold.time.interval")) {
            long interval = Long.parseLong(props.getProperty("hold.time.interval"));
            expectTime = new Date(now.getTime() - interval);
        } else {
            expectTime = now;
        }
        todayStartTime = DateFormatUtils.format(expectTime, "yyyy-MM-dd 00:00:00");
        todayEndTime = DateFormatUtils.format(now, "yyyy-MM-dd 23:59:59");
        allStartTime = DateFormatUtils.format(now, "10000-01-01  00:00:00");
        allEndTime = DateFormatUtils.format(now, "9999-12-31  23:59:59");
        nowStartTime = DateFormatUtils.format(expectTime, "yyyy-MM-dd HH:mm:ss");
    }

    @Override
    public boolean reciveMsg(int jobId, Properties props, Logger log) {
        Long startTime = System.currentTimeMillis();
        Long currentTime = startTime;
        Double doubleWaitTime = Double.valueOf(waitTime) * 3600 * 1000;
        Long waitTime = Long.valueOf(doubleWaitTime.longValue());
        int queryFrequency = Integer.valueOf(query_frequency);
        if (wait_for_time != null && props.containsKey(EventChecker.WAIT_FOR_TIME)) {
            waitForTime(log, waitTime);
        }
        log.info(
            "-------------------------------------- waiting time(unit：millisecond) : " + waitTime);
        log.info(
            "-------------------------------------- Number of query frequency in waiting time  : "
                + queryFrequency);
        log.info(
            "-------------------------------------- earliest finish time : " + earliestFinishTime);
        log.info("-------------------------------------- earliest finish time cross day : "
            + earliestFinishTimeCrossDay);
        Long sleepTime = waitTime / queryFrequency;
        if (sleepTime < 60000L) {
            log.info(
                "your setting is less than the minimum polling time(60s), the system will automatically set it to 60s");
            sleepTime = 60000L;
        }
        boolean result = false;

        boolean isEarliestFinishTimeCrossDay = Boolean.parseBoolean(earliestFinishTimeCrossDay);
        LocalDateTime targetDateTime = null;

        if (earliestFinishTime != null && props.containsKey(EventChecker.EARLIEST_FINISH_TIME)) {
            // 时间处理
            String[] timeSplit = earliestFinishTime.split(":");
            String hourString = timeSplit[0].trim();
            String minString = timeSplit[1].trim();

            targetDateTime = LocalDateTime.of(LocalDateTime.now().toLocalDate(),
                LocalTime.of(Integer.parseInt(hourString), Integer.parseInt(minString)));

            if (isEarliestFinishTimeCrossDay) {
                targetDateTime = targetDateTime.plusDays(1);
            }
            // 判断当前时间是否已超过最早结束时间
            boolean isAfter = isTimeAfter(log, currentTime, targetDateTime);
            if (isAfter) {
                // 已超过最早完成时间，任务直接失败
                log.error("Already exceeded the earliest time! ");
                return false;
            }
        }
        while ((currentTime - startTime) <= waitTime) {
            boolean flag = false;
            try {
                //step1
                //这里让线程随机休眠0到1秒，防止多个receiver在同一时刻重复更新event_status;
                Thread.sleep(new Random().nextInt(1000));
                String lastMsgId = getOffset(jobId, props, log);
                String[] executeType = createExecuteType(jobId, props, log, lastMsgId);
                if (executeType != null && executeType.length == 3) {
                    //step2
                    ConsumedMsgInfo consumedMsgInfo = getMsg(props, log,executeType);
                    if(consumedMsgInfo!=null) {
                        //step3
                        if (earliestFinishTime != null && props.containsKey(
                            EventChecker.EARLIEST_FINISH_TIME)) {
                            boolean isAfter = isTimeAfter(log, currentTime, targetDateTime);
                            if (!isAfter) {
                                // 未达到最早完成时间
                                log.info("Did not reach the earliest finish time.");
                                flag = false;
                            } else {
                                sendBacklogAlert(jobId, props, log, consumedMsgInfo);
                                flag = updateMsgOffset(jobId, props, log, consumedMsgInfo,
                                    lastMsgId);
                            }
                        } else {
                            sendBacklogAlert(jobId, props, log, consumedMsgInfo);
                            flag = updateMsgOffset(jobId,props,log,consumedMsgInfo,lastMsgId);
                        }

                    }else if (trigger_time != null && !"".equals(trigger_time.trim())){
                        flag = autoTrigger(log,props);
                    }
                }else{
                    log.error("executeType error {} " + Arrays.toString(executeType));
                    return result;
                }
                int i = 1 ;
                String azkabanPid = "";
                while(i < 20){
                    azkabanPid = ShellUtils.getExecutorServerPid();
                    log.info("Executor Server PID: " + azkabanPid);
                    if ("".equals(azkabanPid)) {
                        i++;
                        Thread.sleep(5000);
                    }else{
                        break;
                    }
                }
                if ("".equals(azkabanPid)) {
                    throw new RuntimeException("Azkaban-exec has been terminated！");
                }
            }catch (Exception e){
                log.error("EventChecker failed to receive the message", e);
                return result;
            }
            if (flag) {
                result = flag;
                break;
            }
            try {
                log.info("Waiting " + sleepTime + " ms to next check...");
                Thread.sleep(sleepTime);
            } catch (InterruptedException e) {
                throw new RuntimeException(
                    "An exception occurred in the waiting for rotation training！" + e);
            }
            currentTime = System.currentTimeMillis();
        }
        if (!result) {
            throw new RuntimeException("EventChecker receives the message timeout！");
        }
        return result;
    }

    private void sendBacklogAlert(int jobId, Properties props, Logger log, ConsumedMsgInfo consumedMsgInfo) {
        Map<String, String> consumeInfo = new HashMap<>();
        consumeInfo.put("topic", topic);
        consumeInfo.put("msg_id", consumedMsgInfo.getMsgId());
        consumeInfo.put("msg_name", consumedMsgInfo.getMsgName());
        consumeInfo.put("sender", consumedMsgInfo.getSender());
        consumeInfo.put("msg", consumedMsgInfo.getMsg());
        consumeInfo.put("send_time", consumedMsgInfo.getSendTime());
        if (checkSignalExpired(jobId, props, consumeInfo, log)) {
            // 如果信号过期，则发送积压告警
            log.info("The previous signal transmission has exceed {} hours, sending baclog alert........", props.getProperty(Constants.ConfigurationKeys.EVENTCHECKER_BACKLOG_ALERT_TIME, "24"));
            Alerter mailAlerter = EventCheckerAlerterHolder.getAlerterHolder(props).get("email");
            if (null == mailAlerter)  {
                log.error("cannot find alert plugin.");
            } else {
                mailAlerter.alertOnSingnalBacklog(jobId, consumeInfo);
            }
        }
    }

    /**
     * 判断信号是否过期
     *
     * @param jobId
     * @param props
     * @param consumeInfo
     * @param log
     * @return
     */
    public boolean checkSignalExpired(int jobId, Properties props, Map<String, String> consumeInfo, Logger log) {
        try {
            String sendTime = consumeInfo.get("send_time");
            if (StringUtils.isBlank(sendTime)) {
                // 如果sendtime为空，则event_queue中没有消息，不发送告警
                return false;
            }
            long hours = Integer.parseInt(props.getProperty(Constants.ConfigurationKeys.EVENTCHECKER_BACKLOG_ALERT_TIME, "24"));
            setBacklogUser(consumeInfo, props, log);
            LocalDateTime consumnTime = LocalDateTime.parse(sendTime, DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
            log.info("message consumntime: " + consumnTime);
            return LocalDateTime.now().isAfter(consumnTime.plusHours(hours));
        } catch (SQLException e) {
            log.error("setBacklogUser error. ", e);
        } catch (NullPointerException | DateTimeParseException e) {
            log.error("convert date error.", e);
        }
        // 即使判断信号过期逻辑出现异常，不影响主流程运行
        return false;
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

    private String[] createExecuteType(int jobId, Properties props, Logger log, String lastMsgId) {
        boolean receiveTodayFlag = (null != receiveToday && "true".equals(
            receiveToday.trim().toLowerCase()));
        boolean afterSendFlag = (null != afterSend && "true".equals(
            afterSend.trim().toLowerCase()));
        String[] executeType = null;
        try {
            if (receiveTodayFlag) {
                if (afterSendFlag) {
                    executeType = new String[]{nowStartTime, todayEndTime, lastMsgId};
                } else {
                    executeType = new String[]{todayStartTime,todayEndTime,lastMsgId};
                }
            }else{
                if(afterSendFlag){
                    executeType = new String[]{nowStartTime,allEndTime,lastMsgId};
                }else{
                    executeType = new String[]{allStartTime,allEndTime,lastMsgId};
                }
            }
        } catch(Exception e) {
            log.error("create executeType failed", e);
        }
        return executeType;
    }

    private void waitForTime(Logger log,Long waitTime){
        String waitForTime = wait_for_time;
        String formatWaitForTime = DateFormatUtils.format(this.expectTime,"yyyy-MM-dd " + waitForTime + ":00");
        DateFormat fmt =new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        Date targetWaitTime = null;
        try {
            targetWaitTime = fmt.parse(formatWaitForTime);
        } catch (ParseException e) {
            log.error("parse date failed", e);
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
            log.error("parse date failed", e);
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
