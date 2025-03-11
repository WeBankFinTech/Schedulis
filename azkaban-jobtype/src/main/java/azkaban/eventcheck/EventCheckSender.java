package azkaban.eventcheck;

import org.apache.commons.io.IOUtils;
import org.apache.commons.lang.time.DateFormatUtils;
import org.apache.log4j.helpers.DateTimeDateFormat;
import org.slf4j.Logger;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Date;
import java.util.Properties;

/**
 * @author georgeqiao
 * @Title: EventCheckSender
 * @ProjectName WTSS
 * @date 2019/9/1822:27
 * @Description: TODO
 */
public class EventCheckSender extends AbstractEventCheck{

    public EventCheckSender(Properties props) {
        initECParams(props);
    }

    @Override
    public boolean sendMsg(int jobId, Properties props, Logger log) {
            boolean result = false;
            PreparedStatement pstmt = null;
            Connection msgConn = null;
            try {
                msgConn = getEventCheckerConnection(props,log);
                if(msgConn==null) {
                  return false;
                }
                checkEventUnauth(pstmt, msgConn, log);
                result = send(pstmt, msgConn, result, log);
            } catch (SQLException e) {
                throw new RuntimeException("Send EventChecker msg failed!", e);
            } finally {
                closeQueryStmt(pstmt, log);
                closeConnection(msgConn, log);
            }
            return result;
    }

    private void checkEventUnauth(PreparedStatement pstmt, Connection msgConn, Logger log) {
        String checkSql = "select count(1) from wtss_event_unauth where sender = ? and topic = ? and msg_name = ?";
        String insertSql = "insert into wtss_event_unauth (sender,topic,msg_name,record_time) values (? ,? ,? ,?)";
        String time = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
        ResultSet resultSet = null;
        int count = 0;
        try {
            pstmt = msgConn.prepareCall(checkSql);
            pstmt.setString(1, sender);
            pstmt.setString(2, topic);
            pstmt.setString(3, msgName);
            resultSet = pstmt.executeQuery();
            if (resultSet.last()) {
                count = resultSet.getInt(1);
            }
        } catch (Exception e) {
            log.error("select event unauth failed", e);
        } finally {
            close(pstmt, log, resultSet);
        }
        try {
            if (count == 0) {
                pstmt = msgConn.prepareCall(insertSql);
                pstmt.setString(1, sender);
                pstmt.setString(2, topic);
                pstmt.setString(3, msgName);
                pstmt.setString(4, time);
                int row = pstmt.executeUpdate();
                if (row == 1) {
                    log.info("insert event unauth succeed");
                }
            } else {
                log.info("event unauth already exists");
            }
        } catch (Exception e) {
            log.error("insert event unauth failed", e);
        } finally {
            close(pstmt, log, resultSet);
        }
    }

    private void close(PreparedStatement pstmt, Logger log, ResultSet resultSet) {
        try {
            if (resultSet != null) {
                resultSet.close();
            }
            if (pstmt != null) {
                pstmt.close();
            }
        } catch (SQLException e) {
            log.error("SQLException in execute query, caused by:", e);
        }
    }

    private boolean send(PreparedStatement pstmt, Connection msgConn,boolean result, Logger log) throws SQLException {
        String sendTime = DateFormatUtils.format(new Date(), "yyyy-MM-dd HH:mm:ss");
        String vIP = getLinuxLocalIp(log);
        String sqlForSendMsg = "INSERT INTO event_queue (sender,send_time,topic,msg_name,msg,send_ip) VALUES(?,?,?,?,?,?)";
                pstmt = msgConn.prepareCall(sqlForSendMsg);
                pstmt.setString(1, sender);
                pstmt.setString(2, sendTime);
                pstmt.setString(3, topic);
                pstmt.setString(4, msgName);
                pstmt.setString(5, msg);
                pstmt.setString(6, vIP);
                int rs = pstmt.executeUpdate();
                if (rs == 1) {
                    result = true;
                    log.info("Send msg success!");
                } else {
                    log.error("Send msg failed for update database!");
                }
            return result;
    }
}
