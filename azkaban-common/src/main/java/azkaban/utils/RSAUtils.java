package azkaban.utils;

import javax.crypto.Cipher;
import java.security.*;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;


/**
 * @author georgeqiao
 */
public class RSAUtils {
    /**
     * 密钥长度 于原文长度对应 以及越长速度越慢
     */
    private final static int KEY_SIZE = 1024;
    /**
     * 公钥与私钥
     */
    private static String publicKeyString = "MIGfMA0GCSqGSIb3DQEBAQUAA4GNADCBiQKBgQCo14viXWDm19hwsCpSGiaehrF+oBbGq7tIsgfepjkI0iC8HrZ/vo/YsHw+G3yGgEC2oBsHnqUDs/f2k+1FK6EIm5nBnosXpSTtCVDfP8wkwBuTWPdmnAxNQyv7aH2e7BLdfEctYLAdw8atahM2WV9rFZBQzCyheeJ3nI4LCBSzMwIDAQAB";
    private static String privateKeyString = "MIICeAIBADANBgkqhkiG9w0BAQEFAASCAmIwggJeAgEAAoGBAKjXi+JdYObX2HCwKlIaJp6GsX6gFsaru0iyB96mOQjSILwetn++j9iwfD4bfIaAQLagGweepQOz9/aT7UUroQibmcGeixelJO0JUN8/zCTAG5NY92acDE1DK/tofZ7sEt18Ry1gsB3Dxq1qEzZZX2sVkFDMLKF54necjgsIFLMzAgMBAAECgYEAiQG/ZQRY6XklDOwmq1DFHcY2qYXGdZhM9QRiFm3TwjCgl4ZkmOxNVYyAhPVQ6uOPn6HzzQ8S4BpdkB0hYAuzMzMHnyhvv/MsH8V3WbJx0kC4If3UsSa0eANizrZaM3CwJ8ehvN2T73Z0B2iTp8Ocl3Wr74ULKC1y5pJnLC6dIMECQQDbhMVqIwPU+VftMEwIik0BDXHSFL4Vjh2rD9AVkvyWKV0qehYHGsXdlG4XlNUc0zAw8oQVFk9t6XGbMEWnk8QJAkEAxObH8gTy7iGYEUprxbEAJq0SJtOk3qfIngICpqdY5qgWqpl8BS7rSZhKswGLsrdY5rfYr/Mngj5cpe72lcbkWwJBAM+RRt4qR8hNEV/9CBgXNeLl5JdB988H93O12wtbVi1i5W5xzHxhS3FOlZ8Eo1LDOtE9r7kExIxobXzRczuWlIkCQQCJIBO1N78bGig2OnbtuYPaa4ONqK1UJtMvP0UrXLYsBHmsm7FkRrWzjizPl077ynZOT1DH0HX+XYHWSaJO0rGrAkBxQjicw5YAxaz0+Vl/HIFSCT4R4BBKDgSmwHf1yOUOIhspA2AOFdCByrmNg2gQ+Pn0ADHN6N3ke42eAt7GiI4T";

    public static void genKeyPair() throws NoSuchAlgorithmException {
        KeyPairGenerator keyPairGen = KeyPairGenerator.getInstance("RSA");
        keyPairGen.initialize(KEY_SIZE, new SecureRandom());
        KeyPair keyPair = keyPairGen.generateKeyPair();
        RSAPrivateKey privateKey = (RSAPrivateKey) keyPair.getPrivate();
        RSAPublicKey publicKey = (RSAPublicKey) keyPair.getPublic();
        publicKeyString = Base64.getEncoder().encodeToString(publicKey.getEncoded());
        privateKeyString = Base64.getEncoder().encodeToString(privateKey.getEncoded());

    }

    public static String encrypt(String str, String publicKey) throws Exception {
        byte[] decoded = Base64.getDecoder().decode(publicKey);
        RSAPublicKey pubKey = (RSAPublicKey) KeyFactory.getInstance("RSA").generatePublic(new X509EncodedKeySpec(decoded));
        Cipher cipher = Cipher.getInstance("RSA");
        cipher.init(Cipher.ENCRYPT_MODE, pubKey);
        String outStr = Base64.getEncoder().encodeToString(cipher.doFinal(str.getBytes("UTF-8")));
        return outStr;
    }

    public static String decrypt(String str, String privateKey) throws Exception {
        byte[] inputByte = Base64.getDecoder().decode(str);
        byte[] decoded = Base64.getDecoder().decode(privateKey);
        RSAPrivateKey priKey = (RSAPrivateKey) KeyFactory.getInstance("RSA").generatePrivate(new PKCS8EncodedKeySpec(decoded));
        Cipher cipher = Cipher.getInstance("RSA");
        cipher.init(Cipher.DECRYPT_MODE, priKey);
        String outStr = new String(cipher.doFinal(inputByte));
        return outStr;
    }
    public static void main(String[] args) throws Exception {
//        genKeyPair();
//        String message = "Bdp_wtss@We_bank@ABCD~!@#$";
//        String message = args[0];
//        System.out.println("原文:" + message)
//        String messageEn = encrypt(message, publicKeyString);
//        System.out.println("密文:" + messageEn);
        String str = "DIjK7zXeIpNEDrt7f8eh9bnaP7KqcYgEEDGmxuXd9YnXPh7rkR9cTPfASyZg75tkvRS1YakFvWB5rC4qqHslUzYcQX/mORcB1UGbqPLcNC7uLCGD5zLOarcjO6 3e94Z/lIsipLRGwior1UiZdDIoFly3SXMINjbHwLWd0IMV64=";

        str = str.replaceAll(" ","+");

        System.out.println("str:" + str);
        String messageDe = decrypt(str, privateKeyString);
        System.out.println("解密:" + messageDe);
    }
}