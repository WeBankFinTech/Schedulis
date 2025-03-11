package azkaban.system.credential;

import azkaban.system.dto.CredentialDto;
import java.sql.SQLException;
import java.util.List;

/**
 * @author lebronwang
 */
public interface CredentialDao {

  /**
   * 根据 appId + appSecret 获取鉴权信息
   *
   * @param appId
   * @param appSecret
   * @return
   */
  List<CredentialDto> getCredentialByAppIdAndAppSecret(String appId, String appSecret)
      throws SQLException;

  /**
   * 根据 appId 获取鉴权信息
   *
   * @param appId
   * @return
   * @throws SQLException
   */
  List<CredentialDto> getCredentialByAppId(String appId)
      throws SQLException;

}
