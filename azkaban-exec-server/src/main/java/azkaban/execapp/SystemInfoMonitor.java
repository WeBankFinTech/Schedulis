package azkaban.execapp;

import azkaban.executor.ExecutorLoader;
import azkaban.executor.Hosts;
import azkaban.system.SystemManager;
import azkaban.utils.Props;
import com.google.inject.Inject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import oshi.SystemInfo;
import oshi.software.os.FileSystem;
import oshi.software.os.OSFileStore;

import javax.inject.Singleton;
import java.net.InetAddress;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

@Singleton
public class SystemInfoMonitor {
    private static final Logger logger = LoggerFactory.getLogger(SystemInfoMonitor.class);
    private ScheduledExecutorService scheduledExecutorService;
    private SystemManager systemManager;
    private Props props;
    private final String WTSS_SYSTEM_INFO_CHECK_INTERVAL = "wtss.system.info.check.interval";
    private final String WTSS_SYSTEM_INFO_FILE_MOUNTED_PATH = "wtss.system.info.file.mounted.path";
    private final String WTSS_SYSTEM_INFO_DISK_USAGE_LIMIT = "wtss.system.info.disk.usage.limit";
    private final String WTSS_SYSTEM_INFO_INODE_USAGE_LIMIT = "wtss.system.info.inode.usage.limit";
    private final String WTSS_SYSTEM_INFO_CHECK_SEITCH = "wtss.system.info.check.switch";
    private static String fileMountedPath;
    private static String diskUsageLimit;
    private static String inodeUsageLimit;
    private static boolean checkSwitch;
    private static Hosts hosts;
    private static boolean offlineFlag = false;

    @Inject
    public SystemInfoMonitor(Props props, ExecutorLoader executionLoader, SystemManager systemManager) {
        logger.info("start init SystemInfoMonitor");
        this.props = props;
        fileMountedPath = props.getString(WTSS_SYSTEM_INFO_FILE_MOUNTED_PATH, "/appcom");
        diskUsageLimit = props.getString(WTSS_SYSTEM_INFO_DISK_USAGE_LIMIT, "0.9");
        inodeUsageLimit = props.getString(WTSS_SYSTEM_INFO_INODE_USAGE_LIMIT, "0.9");
        checkSwitch = props.getBoolean(WTSS_SYSTEM_INFO_CHECK_SEITCH, true);
        this.systemManager = systemManager;
        try {
            String hostname = InetAddress.getLocalHost().getHostName();
            hosts = executionLoader.getHostConfigByHostname(hostname);
        } catch (Exception e) {
            logger.error("Cannot get Localhost", e);
        }
        if (checkSwitch) {
            scheduledExecutorService = Executors.newScheduledThreadPool(1);
            scheduledExecutorService.scheduleAtFixedRate(new CpuAndInodeScanner(), 0, props.getLong(WTSS_SYSTEM_INFO_CHECK_INTERVAL, 1 * 60 * 60L), TimeUnit.SECONDS);
        }
    }

    public void shutDown() {
        if (checkSwitch) {
            if (!systemManager.checkIsOnline(hosts.getExecutorid())) {
                if (systemManager.executorOnline(hosts.getExecutorid())) {
                    logger.info("executorOnline update succeed: {}", hosts.getHostname());
                } else {
                    logger.warn("executorOnline update failed: {}", hosts.getHostname());
                }
            }
            scheduledExecutorService.shutdown();
        }
    }


    class CpuAndInodeScanner implements Runnable {
        @Override
        public void run() {
            try {
                logger.info("start to check SystemInfo");
                String[] filePaths = SystemInfoMonitor.fileMountedPath.split(",");
                SystemInfo systemInfo = new SystemInfo();
                FileSystem fileSystem = systemInfo.getOperatingSystem().getFileSystem();
                List<OSFileStore> fileStores = fileSystem.getFileStores();

                for (OSFileStore fileStore : fileStores) {
                    String mount = fileStore.getMount();
                    if (Arrays.asList(filePaths).contains(mount)) {
                        long totalSpace = fileStore.getTotalSpace();
                        long usableSpace = fileStore.getUsableSpace();
                        long freeInodes = fileStore.getFreeInodes();
                        long totalInodes = fileStore.getTotalInodes();
                        double diskUsage = Math.round((double) (totalSpace - usableSpace) / totalSpace * 100) / 100.0;
                        double inodeUsage = Math.round((double) (totalInodes - freeInodes) / totalInodes * 100) / 100.0;
                        logger.info("SystemInfo: diskUsage = {} , inodeUsage = {} , filePath = {} .", diskUsage, inodeUsage, mount);
                        if ((diskUsage >= Double.parseDouble(diskUsageLimit) || inodeUsage >= Double.parseDouble(inodeUsageLimit) && !offlineFlag)) {
                            if (systemManager.checkIsOnline(hosts.getExecutorid())) {
                                if (systemManager.executorOffline(hosts.getExecutorid())) {
                                    offlineFlag = true;
                                    logger.info("executorOffline : {}", hosts.getHostname());
                                }
                            }else {
                                offlineFlag = true;
                                logger.info("executor is Offline : {}", hosts.getHostname());
                            }
                        } else if ((diskUsage < Double.parseDouble(diskUsageLimit) || inodeUsage < Double.parseDouble(inodeUsageLimit) && offlineFlag)) {
                            if (systemManager.checkIsOnline(hosts.getExecutorid())) {
                                offlineFlag = false;
                                logger.info("executor is Online : {}", hosts.getHostname());
                            }else {
                                if (systemManager.executorOnline(hosts.getExecutorid())) {
                                    offlineFlag = false;
                                    logger.info("executorOnline : {}", hosts.getHostname());
                                }
                            }
                        }
                    }
                }
            } catch (Exception e) {
                logger.error("System check failed.", e);
            }
        }
    }

}
