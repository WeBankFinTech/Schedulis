package azkaban.log.diagnosis.util;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.HashMap;
import java.util.Map;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang.StringEscapeUtils;

/**
 * @author lebronwang
 * @date 2025/01/26
 **/
public class DiagnosisUtils {


  public static Map<String, String> getDiagnosisResult(String scriptPath, int execId,
      String jobName, int attempt)
      throws IOException, InterruptedException {

    HashMap<String, String> resultMap = new HashMap<>();
    String[] command = {
        "bash", scriptPath,
        String.valueOf(execId),
        jobName,
        String.valueOf(attempt)
    };
    StringBuilder diagnosisLog;

    Process process = null;
    BufferedReader reader = null;
    try {
      // 执行脚本
      process = Runtime.getRuntime().exec(command);
      reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
      diagnosisLog = new StringBuilder();
      String line;
      while ((line = reader.readLine()) != null) {
        diagnosisLog.append(line).append("\n");
      }

      // 等待脚本执行完成
      int exitCode = process.waitFor();
      if (exitCode != 0) {
        resultMap.put("error",
            "Failed to generate diagnosis log. Exit code: " + exitCode + ", errorMsg: "
                + diagnosisLog);
        return resultMap;
      }
    } finally {
      IOUtils.closeQuietly(reader);
      if (process != null) {
        process.destroy();
      }
    }

    // 返回生成的诊断日志
    if (diagnosisLog.length() > 0) {
      resultMap.put("data", StringEscapeUtils.escapeHtml(diagnosisLog.toString()));
    } else {
      resultMap.put("error", "No diagnosis log generated.");
    }

    return resultMap;
  }
}
