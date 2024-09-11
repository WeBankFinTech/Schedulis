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

import azkaban.utils.Props;
import java.nio.charset.Charset;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.forgerock.opendj.ldap.Connection;
import org.forgerock.opendj.ldap.LDAPConnectionFactory;
import org.forgerock.opendj.ldap.LdapException;
import org.forgerock.opendj.ldap.requests.BindRequest;
import org.forgerock.opendj.ldap.requests.Requests;

public class LdapCheckCenter {
  private static final Logger logger = LoggerFactory.getLogger(LdapCheckCenter.class.getName());
  private volatile static LDAPConnectionFactory lcf;

  public static boolean checkLogin(Props props, String userName, String password) {
    String ip = props.getString("ladp.ip");
    int port = props.getInt("ladp.port");
    setupLCF(ip, port);
    Connection conn = null;
    try {
      conn = lcf.getConnection();
    } catch (LdapException e) {
      logger.error("LdapCheckCenter LDAP-->connecting failed. please check ip :" + ip + " port: " + port, e);
      return false;
    }
    logger.info("LdapCheckCenter LDAP-->Connect to host: " + ip + " success");

    BindRequest request3 = Requests.newSimpleBindRequest(userName , password.getBytes(Charset.defaultCharset()));
    try {
      conn.bind(request3);
      logger.info("LdapCheckCenter LDAP-->auth " + userName + " success. ");
      return true;
    } catch (LdapException e) {
      logger.error("LdapCheckCenter LDAP-->Bind " + userName + " failed.", e);
      return false;
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
