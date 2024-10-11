package azkaban.event.service;

import azkaban.event.dao.EventLoader;
import azkaban.event.entity.EventAuth;
import azkaban.function.CheckedSupplier;
import azkaban.utils.Props;
import com.jcraft.jsch.ChannelExec;
import com.jcraft.jsch.JSch;
import com.jcraft.jsch.JSchException;
import com.jcraft.jsch.Session;
import org.apache.commons.lang.StringEscapeUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.sql.SQLException;
import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import static java.util.Objects.requireNonNull;

@Singleton
public class EventAuthManager {

    private static final Logger logger = LoggerFactory.getLogger(EventAuthManager.class);

    private static final int DEFAULT_EVENT_AUTH_TOTAL = 0;

    private static final List<EventAuth> DEFAULT_EVENT_AUTH_LIST = new ArrayList<>();

    private static final int DEFAULT_RMB_SERVER_PORT = 36000;

    private static final String DEFAULT_RMB_PUBLICKEY_PATH = "/home/hadoop/.ssh/id_rsa";

    private static final String DEFAULT_RMB_SERVER_USERNAME = "hadoop";

    private static final String DEFAULT_RMB_SERVERLOG_PATH = "/appcom/Install/AzkabanRMBInstall/schedule-rmb-service-1.0/logs/server.log";

    private static final int DEFAULT_RMB_LOG_DISPLAYLINES = 50;

    private static final int DEFAULT_RMB_SSH_TIMEOUT = 5000;

    private final EventLoader<EventAuth> eventAuthLoader;

    @Inject
    public EventAuthManager(EventLoader<EventAuth> eventAuthLoader) {
        this.eventAuthLoader = requireNonNull(eventAuthLoader);
    }

    public int getEventAuthTotal(String search) {
        return exceptionHandler(DEFAULT_EVENT_AUTH_TOTAL,
                () -> eventAuthLoader.getEventTotal(search));
    }

    public List<EventAuth> findEventAuthList(String search, int pageNum, int pageSize) {
        int startIndex = (pageNum - 1) * pageSize;
        return exceptionHandler(DEFAULT_EVENT_AUTH_LIST,
                () -> eventAuthLoader.findEventList(search, startIndex, pageSize));
    }

    public List<EventAuth> getEventAuthList() {
        return exceptionHandler(DEFAULT_EVENT_AUTH_LIST, () -> eventAuthLoader.getAllEvent());
    }

    public List<EventAuth> getEventAuthSearchList(String searchKey, String searchTerm, int page, int size) {
        return exceptionHandler(DEFAULT_EVENT_AUTH_LIST, () -> eventAuthLoader.getEventListBySearch(searchKey, searchTerm, page, size));
    }

    public Integer getEventAuthSearchTotal(String searchKey, String searchTerm) {
        return exceptionHandler(0, () -> eventAuthLoader.getEventTotalBySearch(searchKey, searchTerm));
    }

    public String fetchSenderLog(String sender, Props prop) {
        List<String> rmbHostList = prop.getStringList("rmb.server.host", ",");
        int rmbSSHPort = prop.getInt("rmb.server.port", DEFAULT_RMB_SERVER_PORT);
        String pubkeyPath = prop.getString("rmb.pubkey.path", DEFAULT_RMB_PUBLICKEY_PATH);
        String username = prop.getString("rmb.server.username", DEFAULT_RMB_SERVER_USERNAME);
        int timeout = prop.getInt("rmn.ssh.timeout", DEFAULT_RMB_SSH_TIMEOUT);
        String command = generateCommand(sender, prop);
        String senderLog = rmbHostList.parallelStream()
                .map(host -> fetchSenderLog(host, pubkeyPath, username, rmbSSHPort, timeout, command))
                .sorted(Comparator.comparing(this::getLogLastUpdateTime))
                .collect(Collectors.joining("\n"));
        return senderLog;
    }

    private long getLogLastUpdateTime(String log) {
        String[] lineArr = log.split("\n");
        Long lastUpdateTime = Arrays.stream(lineArr)
                .mapToLong(this::getLogUpdateTime)
                .max()
                .orElse(-1);
        return lastUpdateTime;
    }

    private long getLogUpdateTime(String line) {
        String[] fieldArr = line.split(",");
        String updateTimeStr = fieldArr.length != 0 ? fieldArr[0] : "";
        DateFormat df = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        long updateTime = -1;
        try {
            Date date = df.parse(updateTimeStr);
            updateTime = date.getTime();
        } catch (ParseException e) {
            return updateTime;
        }
        return updateTime;
    }

    private String fetchSenderLog(String host, String pubkeyPath, String username, int sshPort, int timeout, String command) {
        JSch jsch = new JSch();
        Session session = null;
        ChannelExec channel = null;
        BufferedReader in = null;
        String senderLog = "";
        try {
            jsch.addIdentity(pubkeyPath);
            session = jsch.getSession(username, host, sshPort);
            session.setConfig("StrictHostKeyChecking", "no");
            session.setTimeout(timeout);
            session.connect();
            channel = (ChannelExec) session.openChannel("exec");
            in = new BufferedReader(new InputStreamReader(channel.getInputStream(), "UTF-8"));
            channel.setCommand(command);
            channel.connect();
            senderLog = in.lines()
                    .map(this::formatErrorLog)
                    .collect(Collectors.joining("\n"));
        } catch (IOException | JSchException e) {
            logger.error("fetch sender log failed", e);
        } finally {

            if (in != null) {
                try {
                    in.close();
                } catch (IOException e) {
                    logger.error("close io stream failed, caused by:" + e);
                }
            }

            if (channel != null) {
                channel.disconnect();
            }
            if (session != null) {
                session.disconnect();
            }
        }
        return senderLog;
    }

    private String formatErrorLog(String log) {
        String htmlLog = StringEscapeUtils.escapeHtml(log);
        Predicate<String> isErrorLog = logLine -> {
            String[] fieldArr = logLine.split(" ");
            String logLevel = fieldArr.length > 2 ? fieldArr[2] : "";
            return "ERROR".equals(logLevel);
        };
        Predicate<String> isExceptionLog = logLine -> logLine.contains("Exception");
        Predicate<String> isStackLog = logLine -> logLine.trim().startsWith("at");
        Predicate<String> needFormatLog = isErrorLog.or(isExceptionLog).or(isStackLog);
        return needFormatLog.test(htmlLog)
                ? "<font color='red'>" + htmlLog + "</font>"
                : htmlLog;
    }

    private String generateCommand(String sender, Props prop) {
        String logFilePath = prop.getString("rmb.serverlog.path", DEFAULT_RMB_SERVERLOG_PATH);
        int logDisplayLines = prop.getInt("rmb.log.displayLines", DEFAULT_RMB_LOG_DISPLAYLINES);
        String senderStr = "'" + sender + "'";
        String command = String.format("grep %s -C %d %s |tail -n %d",
                senderStr, logDisplayLines, logFilePath, 2 * logDisplayLines);
        logger.info("fetch sender log command: " + command);
        return command;
    }

    public List<EventAuth> getEventAuth(String topic, String sender, String msgName) {
        return exceptionHandler(new ArrayList<>(),
                () -> eventAuthLoader.getEventAuth(topic, sender, msgName));
    }

    public Integer setBacklogAlarmUser(EventAuth eventAuth) {
        return exceptionHandler(0,
                () -> eventAuthLoader.setBacklogAlarmUser(eventAuth));
    }

    private <T> T exceptionHandler(T defaultValue, CheckedSupplier<T, SQLException> supplier) {
        try {
            return supplier.get();
        } catch (SQLException e) {
            return defaultValue;
        }
    }

}
