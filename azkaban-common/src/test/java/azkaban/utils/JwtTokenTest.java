package azkaban.utils;

import io.jsonwebtoken.*;
import io.jsonwebtoken.impl.crypto.MacProvider;
import org.junit.Before;
import org.junit.Test;

import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.Key;
import java.util.Date;
import java.util.UUID;

/**
 * @author georgeqiao
 * @Description:
 */
public class JwtTokenTest {

    private Key key;
    private SignatureAlgorithm signatureAlgorithm = SignatureAlgorithm.HS256;

    @Before
    public void init() {
        String secret = "bdp";
        //We will sign our JWT with our ApiKey secret
        byte[] apiKeySecretBytes = secret.getBytes(StandardCharsets.UTF_8);
        key = new SecretKeySpec(apiKeySecretBytes, signatureAlgorithm.getJcaName());
    }

    @Test
    public void testEncode() {

        long ttl = 7200 * 1000;
        Date now = new Date();
        Date exp = new Date(now.getTime() + ttl);

        //Let's set the JWT Claims
        JwtBuilder builder = Jwts.builder().setId(UUID.randomUUID().toString())
                .setIssuedAt(now)
                .setExpiration(exp)
                .setSubject("sub") //说明
                .setIssuer("iss") //签发者信息
                .setAudience("aud") //接收用户
                .compressWith(CompressionCodecs.GZIP)
                .setHeaderParam(Header.TYPE, Header.JWT_TYPE)
                .signWith(signatureAlgorithm, key);

        String compactJws = builder.compact();
        System.out.println("testEncode: " + compactJws);
    }

    @Test
    public void testDecode() {
        String compactJws = "eyJ0eXAiOiJKV1QiLCJhbGciOiJIUzI1NiJ9.eyJqdGkiOiJ1Y29kZSIsImlhdCI6MTU5NjA0MzIxMSwiZXhwIjoxNTk2MDUwNDExLCJzdWIiOiJzdWIiLCJpc3MiOiJpc3MiLCJhdWQiOiJhdWQifQ.19lD9f4gul8K4_7wJ-hcizbWDyTK6YcjayhYE_19PD8";

        try {
            Claims claims = Jwts.parser().setSigningKey(key).parseClaimsJws(compactJws).getBody();

            //OK, we can trust this JWT
            System.out.println("ID: " + claims.getId());
            System.out.println("Subject: " + claims.getSubject());
            System.out.println("Issuer: " + claims.getIssuer());
            System.out.println("Audience: " + claims.getAudience());
            System.out.println("Expiration: " + claims.getExpiration());
        } catch (Exception e) {
            //don't trust the JWT!
        }
    }

    @Test
    public void testSimple() {

        Key key = MacProvider.generateKey();

        String compactJws = Jwts.builder()
                .setSubject("Joe")
                .signWith(SignatureAlgorithm.HS512, key)
                .compact();

        System.out.println("testSimple: " + compactJws);
    }
}