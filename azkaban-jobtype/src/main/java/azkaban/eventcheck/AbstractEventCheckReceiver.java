package azkaban.eventcheck;

import azkaban.jobtype.util.EventChecker;
import org.apache.commons.lang.StringUtils;
import org.apache.commons.lang.time.DateFormatUtils;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;

import java.sql.*;
import java.time.LocalDate;
import java.util.Date;
import java.util.Map;
import java.util.Properties;

import static azkaban.jobtype.util.EventChecker.MSG_RECEIVE_AFTER_DATE;
/**
 * @author johnnwang
 * @Title: EventcheckReceiver
 * @ProjectName WTSS
 * @date 2019/9/1822:17
 */
public class AbstractEventCheckReceiver extends AbstractEventCheck{
    /**
     * Fill the result into the source
     * @param props
     * @param log
     * @param consumedMsgInfo
     * @return
     */
    String setConsumedMsg(Properties props, Logger log, ConsumedMsgInfo consumedMsgInfo){
        String vNewMsgID = "";
        try {
            if(consumedMsgInfo!=null){
                vNewMsgID = consumedMsgInfo.getMsgId();
                String vMsgName = consumedMsgInfo.getMsgName();
                String vSender = consumedMsgInfo.getSender();
                String vMsg = consumedMsgInfo.getMsg();
                if (null == vMsg) {
                    props.put(EventChecker.MSG, "NULL");
                } else {
                    props.put(EventChecker.MSG, vMsg);
                }
                if (props.containsKey(MSG_RECEIVE_AFTER_DATE)) {
                    log.info(
                        "handle message : messageID: " + vNewMsgID + ", messageName: " + vMsgName
                            + ", sender: " + vSender
                            + ", messageBody: " + vMsg);
                } else {
                    log.info(
                        "Received message : messageID: " + vNewMsgID + ", messageName: " + vMsgName
                            + ", sender: " + vSender
                        + ", messageBody: " + vMsg);
                }
            }
        }catch (Exception e) {
            log.error("Error set consumed message ", e);
            return vNewMsgID;
        }
        return vNewMsgID;
    }

    /**
     * Update consumption status
     *
     * @param jobId
     * @param props
     * @param log
     * @param consumedMsgInfo
     * @return
     */
    boolean updateMsgOffset(int jobId, Properties props, Logger log,
        ConsumedMsgInfo consumedMsgInfo, String lastMsgId, boolean isMsgAfterDate,
        LocalDate receiveAfterDate, LocalDate msgDate) {

        boolean result = false;
        String vNewMsgID = "-1";
        PreparedStatement updatePstmt = null;
        PreparedStatement pstmtForGetID = null;
        Connection msgConn = null;
        vNewMsgID = setConsumedMsg(props,log,consumedMsgInfo);
        ResultSet rs = null;
        try {
            if(StringUtils.isNotEmpty(vNewMsgID) && StringUtils.isNotBlank(vNewMsgID) && !"-1".equals(vNewMsgID)){
                msgConn = getEventCheckerConnection(props,log);
                if(msgConn == null) {
                    return false;
                }
                msgConn.setAutoCommit(false);
                String sqlForReadMsgID = "SELECT msg_id FROM event_status WHERE receiver=? AND topic=? AND msg_name=? for update";
                pstmtForGetID = msgConn.prepareCall(sqlForReadMsgID);
                pstmtForGetID.setString(1, receiver);
                pstmtForGetID.setString(2, topic);
                pstmtForGetID.setString(3, msgName);
                rs = pstmtForGetID.executeQuery();
                String nowLastMsgId = rs.last() ? rs.getString("msg_id") : "0";
                if (isMsgAfterDate) {
                    log.info(
                        "get message successfully, now check to see if the latest offset has changed, nowLastMsgId is {} ",
                        nowLastMsgId);
                } else {
                    log.info(
                        "Received message successfully, now check to see if the latest offset has changed, nowLastMsgId is {} ",
                        nowLastMsgId);
                }
                if (("0".equals(nowLastMsgId) || nowLastMsgId.equals(lastMsgId))
                    || isMsgAfterDate) {
                    int vProcessID = jobId;
                    String vReceiveTime = DateFormatUtils.format(new Date(), "yyyy-MM-dd HH:mm:ss");
                    String sqlForUpdateMsg = getSqlForUpdateMsg(props, lastMsgId);
                    updatePstmt = msgConn.prepareCall(sqlForUpdateMsg);
                    updatePstmt.setString(1, receiver);
                    updatePstmt.setString(2, topic);
                    updatePstmt.setString(3, msgName);
                    updatePstmt.setString(4, vReceiveTime);
                    updatePstmt.setString(5, vNewMsgID);
                    log.info("paramsForUpdateMsg: vReceiveTime: {}; vNewMsgID: {}", vReceiveTime,
                        vNewMsgID);
                    if (StringUtils.isNotBlank(consumedMsgInfo.getSourceType())) {
                        updatePstmt.setString(6, consumedMsgInfo.getSourceType());
                    } else {
                        updatePstmt.setNull(6, Types.VARCHAR);
                    }
                    int updaters = updatePstmt.executeUpdate();
                    log.info("updateMsgOffset successfully, update result is: " + updaters);
                    if(updaters != 0){
                        if (isMsgAfterDate) {

                            if (receiveAfterDate != null && msgDate != null) {
                                if (msgDate.isBefore(receiveAfterDate)) {
                                    log.info(
                                        "move message offset successfully, update message status successfully");
                                } else {
                                    log.info(
                                        "Received message successfully , update message status succeeded, consumed flow execution ID: "
                                            + vProcessID);
                                    //return true after update success
                                    result = true;
                                }
                            } else {
                                log.warn("null value for receiveAfterDate {} or msgDate {} ",
                                    receiveAfterDate, msgDate);
                            }
                        } else {
                            log.info(
                                "Received message successfully , update message status succeeded, consumed flow execution ID: "
                                    + vProcessID);
                        //return true after update success
                        result = true;
                        }
                    }else{
                        log.info("Received message successfully , update message status failed, consumed flow execution ID: " + vProcessID);
                        // result = false;
                    }
                }else{
                    log.info("the latest offset has changed , Keep waiting for the signal");
                    // result = false;
                }
                msgConn.commit();
            }// result = false;

        }catch (SQLException e){
            log.error("Error update Msg Offset", e);
            try {
                msgConn.rollback();
            } catch (SQLException ex) {
                log.error("transaction rollback failed " + e);
            }
            return false;
        }finally {
            closeQueryStmt(pstmtForGetID, log);
            closeQueryStmt(updatePstmt, log);
            closeConnection(msgConn, log);
            closeQueryRef(rs, log);
        }
        return result;
    }

    @NotNull
    private static String getSqlForUpdateMsg(Properties props, String lastMsgId) {
        String sqlForUpdateMsg;
        if (props.containsKey(MSG_RECEIVE_AFTER_DATE)) {
            sqlForUpdateMsg =
                "INSERT INTO event_status(receiver,topic,msg_name,receive_time,msg_id,source_type) VALUES(?,?,?,?,?,?) ON DUPLICATE KEY UPDATE receive_time=VALUES(receive_time),source_type=VALUES(source_type),msg_id=VALUES(msg_id) ";
        } else {
            sqlForUpdateMsg =
                "INSERT INTO event_status(receiver,topic,msg_name,receive_time,msg_id,source_type) VALUES(?,?,?,?,?,?) ON DUPLICATE KEY UPDATE receive_time=VALUES(receive_time),source_type=VALUES(source_type),msg_id= CASE WHEN msg_id= "
                    + lastMsgId + " THEN VALUES(msg_id) ELSE msg_id END";
        }
        return sqlForUpdateMsg;
    }

    /**
     * get consumption progress
     * @param jobId
     * @param props
     * @param log
     * @return
     */
    String getOffset(int jobId, Properties props, Logger log){
        String sqlForReadMsgID = "SELECT msg_id FROM event_status WHERE receiver=? AND topic=? AND msg_name=?";
        PreparedStatement pstmtForGetID = null;
        Connection msgConn = null;
        ResultSet rs = null;
        boolean flag = false;
        String lastMsgId = "0";
        try {
            msgConn = getEventCheckerConnection(props,log);
            pstmtForGetID = msgConn.prepareCall(sqlForReadMsgID);
            pstmtForGetID.setString(1, receiver);
            pstmtForGetID.setString(2, topic);
            pstmtForGetID.setString(3, msgName);
            rs = pstmtForGetID.executeQuery();
            lastMsgId = rs.last() ? rs.getString("msg_id") : "0";
        } catch (SQLException e) {
            throw new RuntimeException("get Offset failed ", e);
        }finally {
            closeQueryStmt(pstmtForGetID,log);
            closeConnection(msgConn,log);
            closeQueryRef(rs,log);
        }
        return lastMsgId;
    }

    /**
     * Consistent entrance to consumer message
     * @param props
     * @param log
     * @param params   params[startQueryTime,endQueryTime,vMsgID]
     * @return
     */
    ConsumedMsgInfo getMsg(Properties props, Logger log,String ... params){
        String sqlForReadTMsg = "SELECT * FROM event_queue WHERE topic=? AND msg_name=? AND send_time >=? AND send_time <=? AND msg_id >? ORDER BY msg_id ASC LIMIT 1";
        PreparedStatement pstmt = null;
        Connection msgConn = null;
        ResultSet rs = null;
//        String[] consumedMsgInfo = null;
        ConsumedMsgInfo consumedMsgInfo = null;
        try {
            msgConn = getEventCheckerConnection(props,log);
            pstmt = msgConn.prepareCall(sqlForReadTMsg);
            pstmt.setString(1, topic);
            pstmt.setString(2, msgName);
            pstmt.setString(3, params[0]);
            pstmt.setString(4, params[1]);
            pstmt.setString(5, params[2]);
            log.info("receiving ... param: StartTime: " + params[0] + ", EndTime: " + params[1]
                    + ", Topic: " + topic + ", MessageName: " + msgName + ", LastMessageID: " + params[2]);
            rs = pstmt.executeQuery();

            if(rs.last()){
                consumedMsgInfo = new ConsumedMsgInfo();
                consumedMsgInfo.setMsgId(rs.getString("msg_id"));
                consumedMsgInfo.setMsgName(rs.getString("msg_name"));
                consumedMsgInfo.setSender(rs.getString("sender"));
                consumedMsgInfo.setMsg(rs.getString("msg"));
                consumedMsgInfo.setSendTime(rs.getString("send_time"));
                consumedMsgInfo.setSourceType(rs.getString("source_type"));
            }
        } catch (SQLException e) {
            throw new RuntimeException("EventChecker failed to receive message", e);
        } finally {
            closeQueryStmt(pstmt, log);
            closeConnection(msgConn, log);
            closeQueryRef(rs, log);
        }
        return consumedMsgInfo;
    }

    void setBacklogUser(Map<String,String> consumeInfo, Properties props, Logger log) throws SQLException {
        String eaSql = "SELECT backlog_alarm_user, alert_level FROM event_auth where topic=? AND sender=? AND msg_name=? LIMIT 1";
        String euaSql = "SELECT backlog_alarm_user, alert_level FROM wtss_event_unauth where topic=? AND sender=? AND msg_name=? LIMIT 1";
        PreparedStatement eaPstmt = null;
        PreparedStatement euaPstmt = null;
        Connection msgConn = null;
        ResultSet ears = null;
        ResultSet euars = null;
        try {
            msgConn = getEventCheckerConnection(props,log);
            euaPstmt = msgConn.prepareCall(euaSql);
            euaPstmt.setString(1, consumeInfo.get("topic"));
            euaPstmt.setString(2, consumeInfo.get("sender"));
            euaPstmt.setString(3, consumeInfo.get("msg_name"));
            euars = euaPstmt.executeQuery();
            if (euars.last()) {
                consumeInfo.put("backlog_alarm_user", euars.getString("backlog_alarm_user"));
                consumeInfo.put("alert_level", euars.getString("alert_level"));
                if (StringUtils.isEmpty(euars.getString("backlog_alarm_user")) ) {
            eaPstmt = msgConn.prepareCall(eaSql);
            eaPstmt.setString(1, consumeInfo.get("topic"));
            eaPstmt.setString(2, consumeInfo.get("sender"));
            eaPstmt.setString(3, consumeInfo.get("msg_name"));
            ears = eaPstmt.executeQuery();
            if (ears.last()) {
                consumeInfo.put("backlog_alarm_user", ears.getString("backlog_alarm_user"));
                consumeInfo.put("alert_level", ears.getString("alert_level"));
                    }
                }
            }
        } catch (SQLException e) {
            throw new SQLException("set backlog user failed ", e);
        } finally {
            closeQueryRef(euars,log);
            closeQueryRef(ears,log);
            closeQueryStmt(euaPstmt,log);
            closeQueryStmt(eaPstmt,log);
            closeConnection(msgConn,log);
        }
    }

    @Override
    public boolean reciveMsg(int jobId, Properties props, Logger log) {
        return super.reciveMsg(jobId, props, log);
    }
}
