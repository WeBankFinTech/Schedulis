/*
 * Copyright 2020 WeBank
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.webank.wedatasphere.schedulis.common.utils;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

public class MD5Utils {

  private final static String[] hexDigits =
      {"0","1","2","3","4","5","6","7","8","9","A","B","C","D","E","F"};

  /**
   * MD5加密方法
   * @param inputString
   * @return
   */
  public static String md5(String inputString){
    return encodeByMD5(inputString);
  }

  /**
   * 校验密码
   * @param inputPassword
   * @param userId
   * @param dataPassword
   * @return
   */
  public static boolean authenticatePassword(String inputPassword, String userId, String dataPassword){

    return dataPassword.equals(encodeByMD5(encodeByMD5(inputPassword) + userId));
  }

  private static String encodeByMD5(String originString){
    if(null != originString){

      try {
        MessageDigest messageDigest = MessageDigest.getInstance("MD5");

        byte[] results = messageDigest.digest(originString.getBytes("UTF-8"));

        String result = byteArrayToHexString(results);

        return result;

      } catch (Exception e) {
        e.printStackTrace();
      }


    }
    return null;

  }

  private static String byteArrayToHexString(byte[] b){
    StringBuilder resultSb = new StringBuilder();
    for(int i=0; i<b.length; i++){
      resultSb.append(byteToHexString(b[i]));
    }
    return resultSb.toString();
  }

  private static String byteToHexString(byte b){

    int n = b;
    if(n < 0){
      n = 256+n;
    }

    int d1 = n/16;
    int d2 = n%16;

    return hexDigits[d1] + hexDigits[d2];

  }

  public static void main(String[] args) {
    String userId = "wtss_superadmin";
    String inputPwd = "Abcd1234";
    String md5Pwd = md5(md5(inputPwd) + userId);
    System.out.println("加密后的密码: " + md5Pwd);

    boolean pwdAuth = authenticatePassword(inputPwd, userId, md5Pwd);

    System.out.println(pwdAuth);
  }


}
