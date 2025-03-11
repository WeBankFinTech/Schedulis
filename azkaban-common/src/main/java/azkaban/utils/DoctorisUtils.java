package azkaban.utils;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HashMap;
import java.util.Map;

/**
 * @author lebronwang
 * @date 2025/02/17
 **/
public class DoctorisUtils {

  public static String DOCTOR_NONCE = "12345";

  /**
   * 对字符串加密,默认使用SHA-256
   *
   * @param strSrc  要加密的字符串
   * @param encName 加密类型
   * @return
   * @throws UnsupportedEncodingException
   */
  public static String Encrypt(String strSrc, String encName) throws UnsupportedEncodingException {
    MessageDigest md = null;
    String strDes = null;
    byte[] bt = strSrc.getBytes("utf-8");
    try {
      if (encName == null || encName.equals("")) {
        encName = "SHA-256";
      }
      md = MessageDigest.getInstance(encName);
      md.update(bt);
      strDes = bytes2Hex(md.digest()); // to HexString
    } catch (NoSuchAlgorithmException e) {
      return null;
    }
    return strDes;
  }

  public static String bytes2Hex(byte[] bts) {
    String des = "";
    String tmp = null;
    for (int i = 0; i < bts.length; i++) {
      tmp = (Integer.toHexString(bts[i] & 0xFF));
      if (tmp.length() == 1) {
        des += "0";
      }
      des += tmp;
    }
    return des;
  }

  public static void main(String[] args) throws IOException {
    if (org.apache.commons.lang3.StringUtils.isBlank(args[0])) {
      throw new LinkageError("Invalid applicationId cannot be empty");
    }
    Map<String, String> parms = new HashMap<>();
    String timestampStr = String.valueOf(System.currentTimeMillis());
    parms.put("applicationId", args[0]);
    parms.put("app_id", args[1]);
    parms.put("timestamp", timestampStr);
    parms.put("nonce", DOCTOR_NONCE);
    String token = args[2];
    if (org.apache.commons.lang3.StringUtils.isNotBlank(token)) {
      String signature =
          Encrypt(
              Encrypt(parms.get("app_id") + DOCTOR_NONCE + System.currentTimeMillis(), null)
                  + token,
              null);
      parms.put("signature", signature);
    }
    System.out.println(parms);
  }

}
