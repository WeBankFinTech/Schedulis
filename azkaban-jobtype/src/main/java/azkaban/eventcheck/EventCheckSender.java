package azkaban.eventcheck;

import org.apache.commons.lang.time.DateFormatUtils;
import org.slf4j.Logger;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
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
            String sendTime = DateFormatUtils.format(new Date(), "yyyy-MM-dd HH:mm:ss");
            String sqlForSendMsg = "INSERT INTO event_queue (sender,send_time,topic,msg_name,msg,send_ip) VALUES(?,?,?,?,?,?)";
            try {
                String vIP = getLinuxLocalIp(log);
                msgConn = getEventCheckerConnection(props,log);
                if(msgConn==null) {
                  return false;
                }
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
            } catch (SQLException e) {
                throw new RuntimeException("Send EventChecker msg failed!", e);
            } finally {
                closeQueryStmt(pstmt, log);
                closeConnection(msgConn, log);
            }
            return result;
    }
}
