package azkaban.system.credential;

import azkaban.db.DatabaseOperator;
import azkaban.system.dto.CredentialDto;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import javax.inject.Inject;
import javax.inject.Singleton;
import org.apache.commons.dbutils.ResultSetHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author lebronwang
 * @date 2024/10/23
 **/
@Singleton
public class CredentialDaoImpl implements CredentialDao {

  public static final Logger logger = LoggerFactory.getLogger(CredentialDaoImpl.class);

  public static final String BASE_SQL_TABLE_CREDENTIAL = "SELECT subsystem_id, app_id, app_secret, "
      + "ip_whitelist FROM wtss_app_credentials ";

  private final DatabaseOperator dbOperator;

  @Inject
  public CredentialDaoImpl(DatabaseOperator dbOperator) {
    this.dbOperator = dbOperator;
  }

  @Override
  public List<CredentialDto> getCredentialByAppIdAndAppSecret(String appId, String appSecret)
      throws SQLException {

    String querySql = BASE_SQL_TABLE_CREDENTIAL + "WHERE app_id = ? AND app_secret = ? ";

    return this.dbOperator.query(querySql, new CredentialHandler(), appId,
        appSecret);
  }

  @Override
  public List<CredentialDto> getCredentialByAppId(String appId) throws SQLException {
    String querySql = BASE_SQL_TABLE_CREDENTIAL + "WHERE app_id = ? ";

    return this.dbOperator.query(querySql, new CredentialHandler(), appId);
  }

  private static class CredentialHandler implements ResultSetHandler<List<CredentialDto>> {

    @Override
    public List<CredentialDto> handle(ResultSet resultSet) throws SQLException {
      if (!resultSet.next()) {
        return Collections.emptyList();
      }

      List<CredentialDto> credentialDtoList = new ArrayList<>();

      do {
        String subsystemId = resultSet.getString(1);
        String appId = resultSet.getString(2);
        String appSecret = resultSet.getString(3);
        String ipWhiteList = resultSet.getString(4);

        CredentialDto credentialDto = new CredentialDto();
        credentialDto.setSubsystemId(subsystemId);
        credentialDto.setAppId(appId);
        credentialDto.setAppSecret(appSecret);
        credentialDto.setIpWhitelist(ipWhiteList);

        credentialDtoList.add(credentialDto);
      } while (resultSet.next());
      return credentialDtoList;
    }
  }

}
