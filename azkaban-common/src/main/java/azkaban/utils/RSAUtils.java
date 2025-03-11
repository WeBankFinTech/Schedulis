package azkaban.utils;



import java.io.ByteArrayOutputStream;

import java.security.*;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;

import javax.crypto.Cipher;


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
    private static byte[] codes = new byte[256];

    static {
        int i;
        for(i = 0; i < 256; ++i) {
            codes[i] = -1;
        }

        for(i = 65; i <= 90; ++i) {
            codes[i] = (byte)(i - 65);
        }

        for(i = 97; i <= 122; ++i) {
            codes[i] = (byte)(26 + i - 97);
        }

        for(i = 48; i <= 57; ++i) {
            codes[i] = (byte)(52 + i - 48);
        }

        codes[43] = 62;
        codes[47] = 63;
    }
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

    public static String decryptBsp( String appPrivKey, String passwd) throws Exception {
        String privKeyBase64 = appPrivKey;
        String passwdContent = passwd;
        if (passwdContent.startsWith("ffffff02")) {
            passwdContent = passwdContent.substring("ffffff02".length());
        }

        byte[] encBin = hexStringToBytes(passwdContent);
        byte[] b = decryptPKS(encBin,  decode(privKeyBase64.toCharArray()));
        return new String(b);
    }

    public static byte[] decode(char[] data) {
        int tempLen = data.length;

        int len;
        for(len = 0; len < data.length; ++len) {
            if (data[len] > 255 || codes[data[len]] < 0) {
                --tempLen;
            }
        }

        len = tempLen / 4 * 3;
        if (tempLen % 4 == 3) {
            len += 2;
        }

        if (tempLen % 4 == 2) {
            ++len;
        }

        byte[] out = new byte[len];
        int shift = 0;
        int accum = 0;
        int index = 0;

        for(int ix = 0; ix < data.length; ++ix) {
            int value = data[ix] > 255 ? -1 : codes[data[ix]];
            if (value >= 0) {
                accum <<= 6;
                shift += 6;
                accum |= value;
                if (shift >= 8) {
                    shift -= 8;
                    out[index++] = (byte)(accum >> shift & 255);
                }
            }
        }

        if (index != out.length) {
            throw new Error("Miscalculated data length (wrote " + index + " instead of " + out.length + ")");
        } else {
            return out;
        }
    }
    private static byte[] hexStringToBytes(String hexString) {
        if (hexString != null && !hexString.equals("")) {
            hexString = hexString.toUpperCase();
            int length = hexString.length() / 2;
            char[] hexChars = hexString.toCharArray();
            byte[] d = new byte[length];

            for(int i = 0; i < length; ++i) {
                int pos = i * 2;
                d[i] = (byte)(charToByte(hexChars[pos]) << 4 | charToByte(hexChars[pos + 1]));
            }

            return d;
        } else {
            return null;
        }
    }
    private static byte charToByte(char c) {
        return (byte)"0123456789ABCDEF".indexOf(c);
    }
    public static byte[] decryptPKS(byte[] encryptedBytes, byte[] keyBytes) throws Exception {
        int keyByteSize = 256;
        int decryptBlockSize = keyByteSize - 11;
        int nBlock = encryptedBytes.length / keyByteSize;
        ByteArrayOutputStream outbuf = null;

        try {
            PKCS8EncodedKeySpec pkcs8KeySpec = new PKCS8EncodedKeySpec(keyBytes);
            KeyFactory keyFactory = KeyFactory.getInstance("RSA");
            Key privateK = keyFactory.generatePrivate(pkcs8KeySpec);
            Cipher cipher = Cipher.getInstance("RSA/ECB/PKCS1Padding");
            cipher.init(2, privateK);
            outbuf = new ByteArrayOutputStream(nBlock * decryptBlockSize);

            for(int offset = 0; offset < encryptedBytes.length; offset += keyByteSize) {
                int inputLen = encryptedBytes.length - offset;
                if (inputLen > keyByteSize) {
                    inputLen = keyByteSize;
                }

                byte[] decryptedBlock = cipher.doFinal(encryptedBytes, offset, inputLen);
                outbuf.write(decryptedBlock);
            }

            outbuf.flush();
            byte[] var22 = outbuf.toByteArray();
            return var22;
        } catch (Exception var20) {
            throw new Exception("DEENCRYPT ERROR:", var20);
        } finally {
            try {
                if (outbuf != null) {
                    outbuf.close();
                }
            } catch (Exception var19) {
                outbuf = null;
                throw new Exception("CLOSE ByteArrayOutputStream ERROR:", var19);
            }

        }
    }

}