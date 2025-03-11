package azkaban.system.credential;

import azkaban.system.dto.CredentialDto;
import java.sql.SQLException;
import java.util.List;
import javax.inject.Inject;
import javax.inject.Singleton;
import org.apache.commons.collections.CollectionUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author lebronwang
 * @date 2024/10/23
 **/
@Singleton
public class CredentialServiceImpl implements CredentialService {

  public static final Logger logger = LoggerFactory.getLogger(CredentialServiceImpl.class);
  private final CredentialDao credentialDao;

  @Inject
  public CredentialServiceImpl(CredentialDao credentialDao) {
    this.credentialDao = credentialDao;
  }

  @Override
  public CredentialDto getCredentialByAppIdAndAppSecret(String appId, String appSecret)
      throws SQLException {

    List<CredentialDto> credentialDtoList = this.credentialDao.getCredentialByAppIdAndAppSecret(
        appId, appSecret);

    if (CollectionUtils.isNotEmpty(credentialDtoList)) {
      return credentialDtoList.get(0);
    } else {
      return null;
    }
  }

  @Override
  public CredentialDto getCredentialByAppId(String appId) throws SQLException {
    List<CredentialDto> credentialDtoList = this.credentialDao.getCredentialByAppId(
        appId);

    if (CollectionUtils.isNotEmpty(credentialDtoList)) {
      return credentialDtoList.get(0);
    } else {
      return null;
    }
  }
}
