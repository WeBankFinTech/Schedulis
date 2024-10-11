package azkaban.utils;

import azkaban.executor.ExecutorInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;

/**
 * @author lebronwang
 * @date 2023/08/23
 **/
public class OsUtils {

  private static final int CACHE_TIME_IN_MILLISECONDS = 1000;
  private static final Logger logger = LoggerFactory.getLogger(OsUtils.class);
  private static final String NO_CACHE_PARAM_NAME = "nocache";
  private static final boolean EXISTS_BASH = new File("/bin/bash").exists();
  private static final boolean EXISTS_CAT = new File("/bin/cat").exists();
  private static final boolean EXISTS_GREP = new File("/bin/grep").exists();
  private static final boolean EXISTS_MEM_INFO = new File("/proc/meminfo").exists();
  private static final boolean EXISTS_LOAD_AVG = new File("/proc/loadavg").exists();

  protected static long lastRefreshedTime = 0;


  /**
   * <pre>
   * fill the result set with the CPU usage .
   * Note : As the 'Top' bash call doesn't yield accurate result for the system load,
   *        the implementation has been changed to load from the "proc/loadavg" which keeps
   *        the moving average of the system load, we are pulling the average for the recent 1 min.
   * </pre>
   *
   * @param stats reference to the result container which contains all the results, this specific
   *              method will only work on the property "cpuUsage".
   */
  public static void fillCpuUsage(ExecutorInfo stats) throws RuntimeException {
    if (EXISTS_BASH && EXISTS_CAT && EXISTS_LOAD_AVG) {
      try {
        ProcessBuilder processBuilder =
            new ProcessBuilder("/bin/bash", "-c", "/bin/cat /proc/loadavg");
        final Process process = processBuilder.start();
        final ArrayList<String> output = new ArrayList<>();
        process.waitFor();
        final InputStream inputStream = process.getInputStream();
        try {
          BufferedReader reader = new BufferedReader(
              new InputStreamReader(inputStream, StandardCharsets.UTF_8));
          String line = null;
          while ((line = reader.readLine()) != null) {
            output.add(line);
          }
        } finally {
          inputStream.close();
        }

        // process the output from bash call.
        if (output.size() > 0) {
          final String[] splitedresult = output.get(0).split("\\s+");
          double cpuUsage = 0.0;

          try {
            cpuUsage = Double.parseDouble(splitedresult[0]);
          } catch (final NumberFormatException e) {
            logger.error("yielding 0.0 for CPU usage as output is invalid -" + output.get(0));
            throw new RuntimeException(
                "yielding 0.0 for CPU usage as output is invalid -" + output.get(0));
          }
          logger.info("System load : " + cpuUsage);
          stats.setCpuUpsage(cpuUsage);
        }
      } catch (final Exception ex) {
        logger.error("failed fetch system load info "
            + "as exception is captured when fetching result from bash call. Ex -" + ex
            .getMessage());
        throw new RuntimeException("failed fetch system load info "
            + "as exception is captured when fetching result from bash call. Ex -" + ex
            .getMessage());
      }
    } else {
      logger.error(
          "failed fetch system load info, one or more files from the following list are missing -  "
              + "'/bin/bash'," + "'/bin/cat'," + "'/proc/loadavg'");
      throw new RuntimeException(
          "failed fetch system load info, one or more files from the following list are missing -  "
              + "'/bin/bash'," + "'/bin/cat'," + "'/proc/loadavg'");
    }
  }


  /**
   * fill the result set with the percent of the remaining system memory on the server.
   *
   * @param stats reference to the result container which contains all the results, this specific
   *              method will only work work on the property "remainingMemory" and
   *              "remainingMemoryPercent".
   *              <p>
   *              NOTE: a double value will be used to present the remaining memory, a returning
   *              value of '55.6' means 55.6%
   */
  public static void fillRemainingMemoryPercent(ExecutorInfo stats) throws RuntimeException {
    if (EXISTS_BASH && EXISTS_CAT && EXISTS_GREP && EXISTS_MEM_INFO) {
      ProcessBuilder processBuilder =
          new ProcessBuilder("/bin/bash", "-c",
              "/bin/cat /proc/meminfo | grep -E \"^MemTotal:|^MemFree:|^Buffers:|^Cached:|^SwapCached:\"");
      try {
        final ArrayList<String> output = new ArrayList<>();
        final Process process = processBuilder.start();
        process.waitFor();
        final InputStream inputStream = process.getInputStream();
        try {
          final BufferedReader reader = new BufferedReader(
              new InputStreamReader(inputStream, StandardCharsets.UTF_8));
          String line = null;
          while ((line = reader.readLine()) != null) {
            output.add(line);
          }
        } finally {
          inputStream.close();
        }

        long totalMemory = 0;
        long totalFreeMemory = 0;
        Long parsedResult = (long) 0;

        // process the output from bash call.
        // we expect the result from the bash call to be something like following -
        // MemTotal:       65894264 kB
        // MemFree:        57753844 kB
        // Buffers:          305552 kB
        // Cached:          3802432 kB
        // SwapCached:            0 kB
        // Note : total free memory = freeMemory + cached + buffers + swapCached
        // TODO : think about merging the logic in systemMemoryInfo as the logic is similar
        if (output.size() == 5) {
          for (final String result : output) {
            // find the total memory and value the variable.
            parsedResult = extractMemoryInfo("MemTotal", result);
            if (null != parsedResult) {
              totalMemory = parsedResult;
              continue;
            }

            // find the free memory.
            parsedResult = extractMemoryInfo("MemFree", result);
            if (null != parsedResult) {
              totalFreeMemory += parsedResult;
              continue;
            }

            // find the Buffers.
            parsedResult = extractMemoryInfo("Buffers", result);
            if (null != parsedResult) {
              totalFreeMemory += parsedResult;
              continue;
            }

            // find the Cached.
            parsedResult = extractMemoryInfo("SwapCached", result);
            if (null != parsedResult) {
              totalFreeMemory += parsedResult;
              continue;
            }

            // find the Cached.
            parsedResult = extractMemoryInfo("Cached", result);
            if (null != parsedResult) {
              totalFreeMemory += parsedResult;
              continue;
            }
          }
        } else {
          logger.error(
              "failed to get total/free memory info as the bash call returned invalid result."
                  + String.format(" Output from the bash call - %s ", output));
        }

        // the number got from the proc file is in KBs we want to see the number in MBs so we are dividing it by 1024.
        stats.setRemainingMemoryInMB(totalFreeMemory / 1024);
        stats.setRemainingMemoryPercent(
            totalMemory == 0 ? 0 : ((double) totalFreeMemory / (double) totalMemory) * 100);
      } catch (final Exception ex) {
        logger.error("failed fetch system memory info "
            + "as exception is captured when fetching result from bash call. Ex -" + ex
            .getMessage());
        throw new RuntimeException("failed fetch system memory info "
            + "as exception is captured when fetching result from bash call. Ex -" + ex
            .getMessage());
      }
    } else {
      logger.error(
          "failed fetch system memory info, one or more files from the following list are missing -  "
              + "'/bin/bash'," + "'/bin/cat'," + "'/proc/loadavg'");
      throw new RuntimeException(
          "failed fetch system memory info, one or more files from the following list are missing -  "
              + "'/bin/bash'," + "'/bin/cat'," + "'/proc/loadavg'");
    }

  }

  private static Long extractMemoryInfo(final String field, final String result) {
    Long returnResult = null;
    if (null != result && null != field && result.matches(String.format("^%s:.*", field))
        && result.split("\\s+").length > 2) {
      try {
        returnResult = Long.parseLong(result.split("\\s+")[1]);
        logger.debug(field + ":" + returnResult);
      } catch (final NumberFormatException e) {
        returnResult = 0L;
        logger.error(String.format("yielding 0 for %s as output is invalid - %s", field, result));
        throw new RuntimeException(
            String.format("yielding 0 for %s as output is invalid - %s", field, result));
      }
    }
    return returnResult;
  }

  public static boolean isOverload(double maxCpuLoadAvgThreshold, double MaxUsedMemThreshold,
      Logger logger) throws Exception {

    ExecutorInfo executorInfo = new ExecutorInfo();
    fillCpuUsage(executorInfo);
    fillRemainingMemoryPercent(executorInfo);

    double cpuUsage = executorInfo.getCpuUsage();
    double maxUsedMemPercentage = MaxUsedMemThreshold * 100;
    logger.info("Current cpu load average {}, max cpu load average {}", cpuUsage,
        maxCpuLoadAvgThreshold);
    double remainingMemoryPercentage = executorInfo.getRemainingMemoryPercent();
    double usedMemPercentage = 100 - remainingMemoryPercentage;
    logger.info("Current used memory percentage {}, max used memory percentage {}",
        usedMemPercentage, maxUsedMemPercentage);

    if (cpuUsage > maxCpuLoadAvgThreshold) {
      logger.warn("Current cpu load average {} is too high, max cpu load average {}", cpuUsage,
          maxCpuLoadAvgThreshold);
      return true;
    }

    if (usedMemPercentage > maxUsedMemPercentage) {
      logger.warn("Current used memory average {} is too high, max used memory average {}",
          usedMemPercentage,
          maxUsedMemPercentage);
      return true;
    }
    return false;
  }
}
