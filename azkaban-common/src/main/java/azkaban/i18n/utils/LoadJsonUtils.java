package azkaban.i18n.utils;

import azkaban.utils.GsonUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.HashMap;
import java.util.Map;

public class LoadJsonUtils {

    private static final Logger logger = LoggerFactory.getLogger(LoadJsonUtils.class);
    public static volatile String languageType = "zh_CN";

    public static String getLanguageType() {
        return languageType;
    }

    public static void setLanguageType(String languageType) {
        LoadJsonUtils.languageType = languageType;
    }

    /**
     * 读取json文件及对应的节点
     * @param fileName
     * @param dataNode
     * @return
     */
    public static Map<String,String> transJson(String fileName, String dataNode) {
        Map<String, String> resultMap = new HashMap<>();
        try {
            String jsonStr = readFromTextFile(fileName);
            resultMap = (Map<String, String>)GsonUtils.json2Map(jsonStr).get(dataNode);
        } catch (Exception e) {
            logger.error("Json File trans Failed, caused by:{}", e);
        }
        return resultMap;
    }



    public static String readFromTextFile(String fileName) throws IOException {
        InputStream resourceAsStream = LoadJsonUtils.class.getResourceAsStream(fileName);
        InputStreamReader reader = new InputStreamReader(resourceAsStream);
        BufferedReader br = new BufferedReader(reader);
        StringBuilder builder = new StringBuilder();
        String line;
        line = br.readLine();
        while( line != null) {
            builder.append(line);
            line = br.readLine();
        }
        return builder.toString();
    }

}
