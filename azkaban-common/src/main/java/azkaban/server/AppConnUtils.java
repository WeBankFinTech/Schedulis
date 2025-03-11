package azkaban.server;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.UUID;

/**
 * @author lebronwang
 * @date 2024/10/17
 **/
public class AppConnUtils {


  private final static String[] CHARS = new String[]{"a", "b", "c", "d", "e", "f",
      "g", "h", "i", "j", "k", "l", "m", "n", "o", "p", "q", "r", "s",
      "t", "u", "v", "w", "x", "y", "z", "0", "1", "2", "3", "4", "5",
      "6", "7", "8", "9", "A", "B", "C", "D", "E", "F", "G", "H", "I",
      "J", "K", "L", "M", "N", "O", "P", "Q", "R", "S", "T", "U", "V",
      "W", "X", "Y", "Z"};

  public static int genAppId() {
    return 0;
  }

  public static String genAppToken() {
    StringBuilder shortBuffer = new StringBuilder();
    String uuid = UUID.randomUUID().toString().replace("-", "");
    for (int i = 0; i < 8; i++) {
      String str = uuid.substring(i * 4, i * 4 + 4);
      int x = Integer.parseInt(str, 16);
      shortBuffer.append(CHARS[x % 0x3E]);
    }
    return shortBuffer.toString();

  }

  public static String genAppSecret(String appId, String appToken) {
    try {
      String[] array = new String[]{appId, appToken};
      StringBuilder sb = new StringBuilder();
      // 字符串排序
      Arrays.sort(array);
      for (String s : array) {
        sb.append(s);
      }
      String str = sb.toString();
      MessageDigest md = MessageDigest.getInstance("SHA-1");
      md.update(str.getBytes());
      byte[] digest = md.digest();

      StringBuilder hexstr = new StringBuilder();
      String shaHex = "";
      for (byte b : digest) {
        shaHex = Integer.toHexString(b & 0xFF);
        if (shaHex.length() < 2) {
          hexstr.append(0);
        }
        hexstr.append(shaHex);
      }
      return hexstr.toString();
    } catch (NoSuchAlgorithmException e) {
      e.printStackTrace();
      throw new RuntimeException();
    }
  }

  public static void main(String[] args) {

    String appId = args[0];
    String appToken = genAppToken();
    String appSecret = genAppSecret(appId, appToken);

    System.out.println("appId: " + appId);
    System.out.println("appSecret: " + appSecret);
  }
}
