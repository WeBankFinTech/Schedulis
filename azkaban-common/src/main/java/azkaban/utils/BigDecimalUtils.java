package azkaban.utils;

import java.math.BigDecimal;

public class BigDecimalUtils {


    public static BigDecimal add(String arg1,String arg2){

        return new BigDecimal(arg1).add(new BigDecimal(arg2));
    }

    public static BigDecimal subtract(String arg1,String arg2){

        return new BigDecimal(arg1).subtract(new BigDecimal(arg2));
    }


    public static BigDecimal mul(String arg1,String arg2){

        return new BigDecimal(arg1).multiply(new BigDecimal(arg2));
    }

    public static BigDecimal divHalfUp(String arg1,String arg2,Integer scale){
         if ("0".equals(arg2)){
             return BigDecimal.ZERO;
         }
        return new BigDecimal(arg1).divide(new BigDecimal(arg2),scale,BigDecimal.ROUND_HALF_UP).multiply(new BigDecimal(100));
    }

//    public static BigDecimal divHalfDown(String arg1,String arg2,Integer scale){
//
//        return new BigDecimal(arg1).divide(new BigDecimal(arg2),scale,BigDecimal.ROUND_HALF_DOWN);
//    }
//    public static void main(String[] args) {
//        add("1","1.02");
//        System.out.println( divHalfDown("1","1.02321",4));
//    }

}
