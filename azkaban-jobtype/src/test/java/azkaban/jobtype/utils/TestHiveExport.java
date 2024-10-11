package azkaban.jobtype.utils;

import org.junit.Test;

import java.util.Properties;

public class TestHiveExport {

    @Test
    public void testRun() throws Exception{
        Properties p = new Properties();
        p.put("database.name", "abcwork");
        p.put("table.name", "abc");
        p.put("partition.name", "");
        p.put("partition.value", "");
        p.put("export.file.path", "abc");
        p.put("export.file.name", "abc");
        p.put("type", "abc");
        p.put("driver.memory", "30");
        p.put("executor.memory", "30");
        p.put("executor.cores", "30");
//        HiveExport hiveExport = new HiveExport("run spark-submit", p);
//        HiveExport.exeCmd("echo hello");

    }


}
