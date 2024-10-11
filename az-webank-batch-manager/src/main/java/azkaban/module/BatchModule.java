package azkaban.module;

import azkaban.dao.BatchLoader;
import azkaban.dao.impl.JdbcBatchImpl;
import azkaban.service.BatchManager;
import azkaban.service.impl.BatchManagerImpl;
import com.google.inject.AbstractModule;

/**
 * 批量管理注入信息module
 */
public class BatchModule extends AbstractModule {

  @Override
  protected void configure() {
    bind(BatchManager.class).to(BatchManagerImpl.class);
    bind(BatchLoader.class).to(JdbcBatchImpl.class);
  }
}
