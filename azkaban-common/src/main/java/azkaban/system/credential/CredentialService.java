package azkaban.system.credential;

import azkaban.system.dto.CredentialDto;
import java.sql.SQLException;

/**
 * @author lebronwang
 */
public interface CredentialService {

  /**
   * 根据 appId + appSecret 获取鉴权信息
   *
   * @param appId
   * @param appSecret
   * @return
   */
  CredentialDto getCredentialByAppIdAndAppSecret(String appId, String appSecret)
      throws SQLException;

  /**
   * 根据 appId 获取鉴权信息
   *
   * @param appId
   * @return
   * @throws SQLException
   */
  CredentialDto getCredentialByAppId(String appId)
      throws SQLException;

}
