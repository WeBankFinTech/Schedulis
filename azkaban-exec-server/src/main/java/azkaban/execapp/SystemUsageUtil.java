package azkaban.execapp;

import cn.hutool.core.util.NumberUtil;
import org.apache.log4j.Logger;
import oshi.json.SystemInfo;
import oshi.json.hardware.CentralProcessor;
import oshi.json.hardware.GlobalMemory;
import oshi.json.hardware.HardwareAbstractionLayer;

public class SystemUsageUtil {
    private static final Logger log = Logger.getLogger(JobRunner.class);

    private static SystemInfo systemInfo = new SystemInfo();

    /**
     * 获取内存的使用率
     *
     * @return 内存使用率 0.36
     */
    public static double getMemoryUsage() {
        HardwareAbstractionLayer hal = systemInfo.getHardware();
        GlobalMemory memory = hal.getMemory();
        long available = memory.getAvailable();
        long total = memory.getTotal();
        log.info(String.format("getMemoryUsage available=%s,total=%s", available, total));
        double useRate = NumberUtil.div(available, total, 2);
        return useRate;
    }

    public static long getMemoryMB() {
        HardwareAbstractionLayer hal = systemInfo.getHardware();
        GlobalMemory memory = hal.getMemory();
        long total = memory.getTotal();
        return total/1024;
    }

    /**
     * 获取CPU的使用率
     *
     * @return CPU使用率 0.36
     */
    public static double getCpuUsage() {
        HardwareAbstractionLayer hal = systemInfo.getHardware();
        CentralProcessor processor = hal.getProcessor();
        double useRate = processor.getSystemCpuLoadBetweenTicks();
        log.info(String.format("getCpuUsage useRate=%s", useRate));
        return NumberUtil.div(useRate, 1, 2);
    }
}