package azkaban.utils;

import org.forgerock.opendj.ldap.Connection;
import org.forgerock.opendj.ldap.LDAPConnectionFactory;
import org.forgerock.opendj.ldap.LdapException;
import org.forgerock.opendj.ldap.requests.BindRequest;
import org.forgerock.opendj.ldap.requests.Requests;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.Charset;
import java.util.HashMap;
import java.util.Map;

/**
 * Created by kirkzhou on 7/1/17.
 */
public class LdapCheckCenter {

  private static final Logger logger = LoggerFactory.getLogger(LdapCheckCenter.class.getName());
  private volatile static LDAPConnectionFactory lcf;

  public static Map<String, Object> checkLogin(Props props, String userName, String password) {
    Map<String, Object> resultMap = new HashMap<>(1);
    String ip = props.getString("ladp.ip");
    int port;
    String propStr = null;
    try {
      propStr = props.getString("ladp.port");
      port = Integer.valueOf(propStr);
    } catch (Exception e) {
      logger.error(
          "LdapCheckCenter LDAP-->connecting failed. current config is: ip=" + ip + " port="
              + propStr, e);
      resultMap.put("error", e.getMessage());
      return resultMap;
    }
    setupLCF(ip, port);
    Connection conn = null;
    try {
      conn = lcf.getConnection();
    } catch (LdapException e) {
      logger.error(
          "LdapCheckCenter LDAP-->connecting failed. please check ip :" + ip + " port: " + port, e);
      resultMap.put("error", e.getMessage());
      return resultMap;
    }
    logger.info("LdapCheckCenter LDAP-->Connect to host: " + ip + " success");

    BindRequest request3 = Requests.newSimpleBindRequest(userName , password.getBytes(Charset.defaultCharset()));
    try {
      conn.bind(request3);
      logger.info("LdapCheckCenter LDAP-->auth " + userName + " success. ");
      resultMap.put("success", "success");
      return resultMap;
    } catch (LdapException e) {
      logger.warn("LdapCheckCenter LDAP-->Bind " + userName + " failed.", e);
      resultMap.put("error", e.getMessage());
      return resultMap;
    } finally {
      conn.close();
    }
  }

  private static LDAPConnectionFactory setupLCF(String ip, int port) {
    if (lcf == null) {
      synchronized (LDAPConnectionFactory.class) {
        if (lcf == null) {
          lcf = new LDAPConnectionFactory(ip, port);
        }
      }
    }
    return lcf;
  }
}
