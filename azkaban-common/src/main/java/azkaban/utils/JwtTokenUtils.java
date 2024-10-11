package azkaban.utils;

import io.jsonwebtoken.*;

import java.nio.charset.StandardCharsets;
import java.security.Key;
import java.util.Date;
import java.util.Map;
import java.util.UUID;

/**
 * @author georgeqiao
 * @Description:
 */
public class JwtTokenUtils {

    private Key key;
    private SignatureAlgorithm signatureAlgorithm = SignatureAlgorithm.HS256;

//    static {
//        String secret = "bdp";
//        //We will sign our JWT with our ApiKey secret
//        byte[] apiKeySecretBytes = secret.getBytes(Charsets.UTF_8);
//        key = new SecretKeySpec(apiKeySecretBytes, signatureAlgorithm.getJcaName());
//    }


    /**
     * 获取token - json化 map信息
     *
     * @param claimMaps
     * @param encryKey
     * @param secondTimeOut
     * @return
     */
    public static String getTokenByJson(Map<String, Object> claimMaps, String encryKey, int secondTimeOut) {
        return getToken(claimMaps, true, encryKey, secondTimeOut);
    }

    /**
     * 获取token
     *
     * @param claimMaps
     * @param isJsonMpas
     * @param encryKey
     * @param secondTimeOut
     * @return
     */
    public static String getToken(Map<String, Object> claimMaps, boolean isJsonMpas, String encryKey, int secondTimeOut) {

        if (isJsonMpas) {
            claimMaps.forEach((key, val) -> {
                claimMaps.put(key, GsonUtils.toJson(val));
            });
        }
        long currentTime = System.currentTimeMillis();
        byte[] apiKeySecretBytes = encryKey.getBytes(StandardCharsets.UTF_8);
        return Jwts.builder()
                .setId(UUID.randomUUID().toString())
                .setIssuedAt(new Date(currentTime))  //签发时间
                .setExpiration(new Date(currentTime + secondTimeOut * 1000))  //过期时间戳
                .setSubject("webserver_to_executorserver")  //说明
                .setAudience("executorservercontainer")  //接收用户
                .setIssuer("webservercontainer") //签发者信息
                .compressWith(CompressionCodecs.GZIP)  //数据压缩方式
                .signWith(SignatureAlgorithm.HS256, apiKeySecretBytes) //加密方式
                .addClaims(claimMaps) //cla信息
                .compact();
    }

    /**
     * 获取token中的claims信息
     *
     * @param token
     * @param encryKey
     * @return
     */
    private static Jws<Claims> getJws(String token, String encryKey) {
        byte[] apiKeySecretBytes = encryKey.getBytes(StandardCharsets.UTF_8);
        return Jwts.parser()
                .setSigningKey(apiKeySecretBytes)
                .parseClaimsJws(token);
    }

    /**
     * 获取token中签名信息
     *
     * @param token
     * @param encryKey
     * @return
     */
    public static String getSignature(String token, String encryKey) {
        try {
            return getJws(token, encryKey).getSignature();
        } catch (Exception ex) {
            return "";
        }
    }

    /**
     * 获取token中head信息
     *
     * @param token
     * @param encryKey
     * @return
     */
    public static JwsHeader getHeader(String token, String encryKey) {
        try {
            return getJws(token, encryKey).getHeader();
        } catch (Exception ex) {
            return null;
        }
    }

    /**
     * 获取payload body信息
     *
     * @param token
     * @param encryKey
     * @return
     */
    public static Claims getClaimsBody(String token, String encryKey) {
        return getJws(token, encryKey).getBody();
    }

    /**
     * 获取body某个值
     *
     * @param token
     * @param encryKey
     * @param key
     * @return
     */
    public static Object getVal(String token, String encryKey, String key) {
        return getJws(token, encryKey).getBody().get(key);
    }

    /**
     * 获取body某个值，json字符转实体
     *
     * @param token
     * @param encryKey
     * @param key
     * @param tClass
     * @param <T>
     * @return
     */
    public static <T> T getValByT(String token, String encryKey, String key, Class<T> tClass) {
        try {
            String strJson = getVal(token, encryKey, key).toString();
            return GsonUtils.fromJson(strJson, tClass);
        } catch (Exception ex) {
            return null;
        }
    }

    /**
     * 是否过期
     *
     * @param token
     * @param encryKey
     * @return
     */
    public static boolean isExpiration(String token, String encryKey) {
        try {
            return getClaimsBody(token, encryKey)
                    .getExpiration()
                    .before(new Date());
        } catch (ExpiredJwtException ex) {
            return true;
        }
    }

    /**
     * 获取说明信息
     *
     * @param token
     * @param encryKey
     * @return
     */
    public static String getSubject(String token, String encryKey) {
        try {
            return getClaimsBody(token, encryKey).getSubject();
        } catch (Exception ex) {
            return "";
        }
    }

    public static void main(String[] args) {
        String token = getToken(null,false,"dws-wtss|WeBankBDPWTSS&DWS@2019",1 * 7 * 24 * 60 * 60 );
        System.out.println("testEncode: " + token);

        Claims claims = getJws(token,"dws-wtss|WeBankBDPWTSS&DWS@2019").getBody();
        System.out.println("ID: " + claims.getId());
        System.out.println("Subject: " + claims.getSubject());
        System.out.println("Issuer: " + claims.getIssuer());
        System.out.println("Audience: " + claims.getAudience());
        System.out.println("Expiration: " + claims.getExpiration());

    }
}