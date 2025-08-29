/*
 * Copyright 2017 LinkedIn Corp.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */
package azkaban.db;

import azkaban.utils.Props;
import org.apache.commons.dbcp2.BasicDataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.crypto.Cipher;
import javax.inject.Inject;
import javax.inject.Singleton;
import java.io.ByteArrayOutputStream;
import java.security.Key;
import java.security.KeyFactory;
import java.security.spec.PKCS8EncodedKeySpec;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

@Singleton
public class MySQLDataSource extends AbstractAzkabanDataSource {

  private static final Logger logger = LoggerFactory.getLogger(MySQLDataSource.class);
  private final DBMetrics dbMetrics;
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

  @Inject
  public MySQLDataSource(final Props props, final DBMetrics dbMetrics) {
    super();
    this.dbMetrics = dbMetrics;

    final int port = props.getInt("mysql.port");
    final String host = props.getString("mysql.host");
    final String dbName = props.getString("mysql.database");
    final String user = props.getString("mysql.user");
    final String driver = props.getString("mysql.driver","com.mysql.jdbc.Driver");
	  // FIXME The database password needs RSA decoding.
    String pwd = null;
    try {
      String privateKey = props.getString("password.private.key");
      String ciphertext = props.getString("mysql.password");
      pwd = decrypt(privateKey,ciphertext);
    } catch (Exception e){
      logger.error("password decore failed", e);
    }
    final String password = pwd;
    final int numConnections = props.getInt("mysql.numconnections");

    final String url = "jdbc:mysql://" + (host + ":" + port + "/" + dbName);
    addConnectionProperty("useUnicode", "yes");
    addConnectionProperty("characterEncoding", "UTF-8");
    addConnectionProperty("autoReconnect", "true");
    setDriverClassName(driver);
    setUsername(user);
    setPassword(password);
    setUrl(url);
    setMaxTotal(numConnections);
    setValidationQuery("/* ping */ select 1");
    setTestOnBorrow(true);
  }
  /**
   * This method overrides {@link BasicDataSource#getConnection()}, in order to have retry logics.
   * We don't make the call synchronized in order to guarantee normal cases performance.
   */
  @Override
  public Connection getConnection() throws SQLException {

    this.dbMetrics.markDBConnection();
    final long startMs = System.currentTimeMillis();
    Connection connection = null;
    int retryAttempt = 1;
    while (retryAttempt < AzDBUtil.MAX_DB_RETRY_COUNT) {
      try {
        /**
         * when DB connection could not be fetched (e.g., network issue), or connection can not be validated,
         * {@link BasicDataSource} throws a SQL Exception. {@link BasicDataSource#dataSource} will be reset to null.
         * createDataSource() will create a new dataSource.
         * Every Attempt generates a thread-hanging-time, about 75 seconds, which is hard coded, and can not be changed.
         */
        connection = createDataSource().getConnection();

        /**
         * If connection is null or connection is read only, retry to find available connection.
         * When DB fails over from master to slave, master is set to read-only mode. We must keep
         * finding correct data source and sql connection.
         */
        if (connection == null || isReadOnly(connection)) {
          throw new SQLException("Failed to find DB connection Or connection is read only. ");
        } else {

          // Evalaute how long it takes to get DB Connection.
          this.dbMetrics.setDBConnectionTime(System.currentTimeMillis() - startMs);
          return connection;
        }
      } catch (final SQLException ex) {

        /**
         * invalidate connection and reconstruct it later. if remote IP address is not reachable,
         * it will get hang for a while and throw exception.
         */
        this.dbMetrics.markDBFailConnection();
        try {
          invalidateConnection(connection);
        } catch (final Exception e) {
          logger.error( "can not invalidate connection.", e);
        }
        logger.error( "Failed to find write-enabled DB connection. Wait 15 seconds and retry."
            + " No.Attempt = " + retryAttempt, ex);
        /**
         * When database is completed down, DB connection fails to be fetched immediately. So we need
         * to sleep 15 seconds for retry.
         */
        sleep(1000L * 15);
        retryAttempt++;
      }
    }
    return connection;
  }

  private boolean isReadOnly(final Connection conn) throws SQLException {
    PreparedStatement ps = null;
    ResultSet rs = null;
    try {
      ps = conn.prepareStatement("SELECT @@global.read_only");
      rs = ps.executeQuery();
      if (rs.next()) {
        final int value = rs.getInt(1);
        return value != 0;
      }
    } catch (SQLException e) {
      throw new SQLException("can not fetch read only value from DB");
    } finally {
      try {
        if (rs != null) {
          rs.close();
        }
      } catch (SQLException e) {
        logger.error("SQLException in execute query, caused by:", e);
      }

      try {
        if (ps != null) {
          ps.close();
        }
      } catch (SQLException e) {
        logger.error("SQLException in execute query, caused by:", e);
      }
    }
    return false;
  }

  private void sleep(final long milliseconds) {
    try {
      Thread.sleep(milliseconds);
    } catch (final InterruptedException e) {
      logger.error("Sleep interrupted", e);
    }
  }

  @Override
  public String getDBType() {
    return "mysql";
  }

  @Override
  public boolean allowsOnDuplicateKey() {
    return true;
  }



  public static String decrypt(String appPrivKey, String encStr) throws Exception {
    if (encStr.startsWith("ffffff02")) {
      encStr = encStr.substring("ffffff02".length());
    }

    byte[] encBin = hexStringToBytes(encStr);
    byte[] b = decrypt(encBin, decode(appPrivKey.toCharArray()));
    return new String(b);
  }



  public static byte[] decrypt(byte[] encryptedBytes, byte[] keyBytes) throws Exception {
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
