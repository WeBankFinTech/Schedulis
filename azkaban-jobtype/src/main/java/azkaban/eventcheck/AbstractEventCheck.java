package azkaban.eventcheck;

import azkaban.jobtype.connectors.druid.WBDruidFactory;
import azkaban.jobtype.util.EventChecker;
import com.alibaba.druid.pool.DruidDataSource;
import org.slf4j.Logger;

import javax.sql.DataSource;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.management.ManagementFactory;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.Properties;

/**
 * @author georgeqiao
 * @Title: AbstractEventCheck
 * @ProjectName WTSS
 * @date 2019/9/1822:21
 */
public abstract class AbstractEventCheck implements EventCheckAdapter{
    static DataSource msgDS;
    String topic;
    String msgName;
    String receiver;
    String sender;
    String receiveToday;
    String userTime;
    String waitTime;
    String query_frequency;
    String wait_for_time;
    String msg;
    String afterSend;
    String trigger_time;
    String trigger_param;
    String earliestFinishTime;
    String earliestFinishTimeCrossDay;

    DataSource getMsgDS(Properties props, Logger log){
        if (msgDS == null) {
            msgDS = WBDruidFactory.getMsgInstance(props, log);
            if (msgDS == null) {
                log.error("Error getting Druid DataSource instance");
            }
        }
        return msgDS;
    }

    void initECParams(Properties props){
        topic = props.getProperty(EventChecker.TOPIC);
        msgName = props.getProperty(EventChecker.MSGNAME);
        receiver = props.getProperty(EventChecker.RECEIVER);
        sender = props.getProperty(EventChecker.SENDER);
        msg = props.getProperty(EventChecker.MSG);
        receiveToday = props.getProperty(EventChecker.TODAY);
        userTime = props.getProperty(EventChecker.USER_TIME);
        waitTime = props.getProperty(EventChecker.WAIT_TIME, "1");
        query_frequency = props.getProperty(EventChecker.QUERY_FREQUENCY, "5");
        wait_for_time = props.getProperty(EventChecker.WAIT_FOR_TIME);
        afterSend = props.getProperty(EventChecker.AFTERSEND);
        trigger_time = props.getProperty(EventChecker.TRIGGER_TIME);
        trigger_param = props.getProperty(EventChecker.TRIGGER_PARAM);
        earliestFinishTime = props.getProperty(EventChecker.EARLIEST_FINISH_TIME);
        earliestFinishTimeCrossDay = props.getProperty(EventChecker.EARLIEST_FINISH_TIME_CROSS_DAY,
            "false");
    }

    Connection getEventCheckerConnection(Properties props, Logger log){
        Connection connection = null;
        try {
            connection = WBDruidFactory.getConnection((DruidDataSource) getMsgDS(props,log), props, log);
        } catch (Throwable e) {
            throw new RuntimeException("Error getting DB Connection instance {} ", e);
        }
        log.info("getEventCheckerConnection successfully !");
        return connection;
    }

    @Override
    public boolean sendMsg(int jobId, Properties props, Logger log) {
        return false;
    }

    @Override
    public boolean reciveMsg(int jobId, Properties props, Logger log) {
        return false;
    }

    void closeConnection(Connection conn, Logger log) {
        if (conn != null) {
            try {
                conn.close();
            } catch (SQLException e) {
                log.error("Error closing connection", e);
            }
        }
    }

    void closeQueryRef(ResultSet rs, Logger log) {
        if (rs != null) {
            try {
                rs.close();
            } catch (SQLException e) {
                log.error("Error closing result set", e);
            }
        }

    }

    void closeQueryStmt(PreparedStatement stmt, Logger log) {
        if (stmt != null) {
            try {
                stmt.close();
            } catch (SQLException e) {
                log.error("Error closing result stmt", e);
            }
        }

    }


    public static void closeDruidDataSource() {
        DruidDataSource msgDSObject = (DruidDataSource) msgDS;

        if (msgDSObject != null) {
            msgDSObject.close();
        }

    }

    String getLinuxLocalIp(Logger log) {
        String ip = "127.0.0.1";
        try {
            for (Enumeration<NetworkInterface> en = NetworkInterface.getNetworkInterfaces(); en.hasMoreElements(); ) {
                NetworkInterface intf = en.nextElement();
                String name = intf.getName();
                if (!name.contains("docker") && !name.contains("lo")) {
                    for (Enumeration<InetAddress> enumIpAddr = intf.getInetAddresses(); enumIpAddr.hasMoreElements(); ) {
                        InetAddress inetAddress = enumIpAddr.nextElement();
                        if (!inetAddress.isLoopbackAddress()) {
                            String ipaddress = inetAddress.getHostAddress().toString();
                            if (!ipaddress.contains("::") && !ipaddress.contains("0:0:") && !ipaddress.contains("fe80")) {
                                ip = ipaddress;
                            }
                        }
                    }
                }
            }
        } catch (SocketException ex) {
            log.warn("get ip failed", ex);

        }
        log.info("Send IP:" + ip);
        return ip;
    }

    static void AzkabanStatusMonitor() {
        String name = ManagementFactory.getRuntimeMXBean().getName();
        System.out.println(name);
        String pid = name.split("@")[0];
        Process process = null;
        List<String> processList = new ArrayList<String>();
        InputStream in = null;
        String[] cmds = {"/bin/sh", "-c", "ps -ef | grep " + pid + " | grep -v 'grep' | awk '{print $3}'"};
        try {
            process = Runtime.getRuntime().exec(cmds);
            process.waitFor();
            in = process.getInputStream();
            BufferedReader reader = new BufferedReader(new InputStreamReader(in));
            String line;
            while ((line = reader.readLine()) != null) {
                processList.add(line);
            }
            reader.close();
        } catch (IOException e) {
            e.printStackTrace();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        for (String ppid : processList) {
            System.out.println(ppid);

        }
    }
}
