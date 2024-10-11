package azkaban.db;

import org.apache.commons.jexl2.Expression;
import org.apache.commons.jexl2.JexlContext;
import org.apache.commons.jexl2.JexlEngine;
import org.apache.commons.jexl2.MapContext;
import org.joda.time.DateTimeUtils;
import org.junit.Test;

import java.util.HashMap;
import java.util.Map;

/**
 * @author georgeqiao
 * @Title: ExpressionTest
 * @date 2019/11/1520:12
 * @Description: TODO
 */
public class ExpressionTest {

    String str = null;
    String str1 = null;

//    @Test
    public void testExpress() throws Exception {
        try {
            long nowtime = DateTimeUtils.currentTimeMillis();
            Map<String, Object> map = new HashMap<String, Object>(16);
            System.out.println(nowtime);
            map.put("nextCheckTime", "1576490100000");
            String expression = "nextCheckTime < " + nowtime;
            Object code = convertToCode(expression, map);
            System.out.println((Boolean) code);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static Object convertToCode(String jexlExp, Map<String, Object> map) {
        JexlEngine jexl = new JexlEngine();
        Expression e = jexl.createExpression(jexlExp);
        JexlContext jc = new MapContext();
        for (String key : map.keySet()) {
            jc.set(key, map.get(key));
        }
        if (null == e.evaluate(jc)) {
            return "";
        }
        return e.evaluate(jc);
    }


    @Test
    public void testprocess() throws Exception {
        TT tt = new TT();
        new Thread(tt).start();

        while(true && (str = str1) != null){
            System.out.println(str);
        }

        System.out.println("complete");
    }

    public class TT implements Runnable{

        @Override
        public void run() {
            for (int i = 0;i <=10 ;i++){
                try {
                    Thread.sleep(30000);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
                str1 = "";
            }
        }
    }


}
