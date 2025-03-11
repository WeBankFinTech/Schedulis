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
 *
 */
package azkaban;

import azkaban.db.AbstractAzkabanDataSource;
import azkaban.db.H2FileDataSource;
import azkaban.db.MySQLDataSource;
import azkaban.exceptional.user.dao.ExceptionalUserLoader;
import azkaban.exceptional.user.impl.ExceptionalUserLoaderImpl;
import azkaban.executor.ExecutionLogsAdapter;
import azkaban.executor.ExecutionLogsDao;
import azkaban.executor.ExecutorLoader;
import azkaban.executor.ExecutorQueueLoader;
import azkaban.executor.JdbcExecutorLoader;
import azkaban.executor.JdbcExecutorQueueLoader;
import azkaban.jobid.relation.JobIdRelationDao;
import azkaban.jobid.relation.JobIdRelationDaoImpl;
import azkaban.jobid.relation.JobIdRelationService;
import azkaban.jobid.relation.JobIdRelationServiceImpl;
import azkaban.project.JdbcProjectImpl;
import azkaban.project.ProjectLoader;
import azkaban.sla.dao.AlertMessageTimeDao;
import azkaban.sla.dao.impl.AlertMessageTimeDaoImpl;
import azkaban.spi.Storage;
import azkaban.spi.StorageException;
import azkaban.storage.StorageImplementationType;
import azkaban.system.JdbcSystemUserImpl;
import azkaban.system.SystemUserLoader;
import azkaban.system.common.TransitionService;
import azkaban.system.credential.CredentialDao;
import azkaban.system.credential.CredentialDaoImpl;
import azkaban.trigger.JdbcTriggerImpl;
import azkaban.trigger.TriggerLoader;
import azkaban.user.SystemUserManager;
import azkaban.user.UserManager;
import azkaban.utils.Props;
import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import org.apache.commons.dbutils.QueryRunner;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


/**
 * This Guice module is currently a one place container for all bindings in the current module. This
 * is intended to help during the migration process to Guice. Once this class starts growing we can
 * move towards more modular structuring of Guice components.
 */
public class AzkabanCommonModule extends AbstractModule {

  private static final Logger log = LoggerFactory.getLogger(AzkabanCommonModule.class);

  private final Props props;
  private final AzkabanCommonModuleConfig config;

  public AzkabanCommonModule(final Props props) {
    this.props = props;
    this.config = new AzkabanCommonModuleConfig(props);
  }

  /**
   * 请不要在此处随意添加 bind，如需添加必须经过评审！！！
   */
  @Override
  protected void configure() {
    install(new AzkabanCoreModule(this.props));
    bind(Storage.class).to(resolveStorageClassType());
    bind(AbstractAzkabanDataSource.class).to(resolveDataSourceType());
    bind(TriggerLoader.class).to(JdbcTriggerImpl.class);
    bind(ProjectLoader.class).to(JdbcProjectImpl.class);
    bind(ExecutorLoader.class).to(JdbcExecutorLoader.class);
    bind(ExecutorQueueLoader.class).to(JdbcExecutorQueueLoader.class);
    bind(SystemUserLoader.class).to(JdbcSystemUserImpl.class);
    bind(ExecutionLogsAdapter.class).to(resolveExecutionLogsType());
    bind(TransitionService.class);
    bind(UserManager.class).to(SystemUserManager.class);
    bind(JobIdRelationDao.class).to(JobIdRelationDaoImpl.class);
    bind(JobIdRelationService.class).to(JobIdRelationServiceImpl.class);
    bind(ExceptionalUserLoader.class).to(ExceptionalUserLoaderImpl.class);
    bind(CredentialDao.class).to(CredentialDaoImpl.class);
    bind(AlertMessageTimeDao.class).to(AlertMessageTimeDaoImpl.class);
  }

  public Class<? extends Storage> resolveStorageClassType() {
    final StorageImplementationType type = StorageImplementationType
        .from(this.config.getStorageImplementation());
    if (type == StorageImplementationType.HDFS) {
      install(new HadoopModule(this.props));
    }
    if (type != null) {
      return type.getImplementationClass();
    } else {
      return loadCustomStorageClass(this.config.getStorageImplementation());
    }
  }

  private Class<? extends Storage> loadCustomStorageClass(final String storageImplementation) {
    try {
      return (Class<? extends Storage>) Class.forName(storageImplementation);
    } catch (final ClassNotFoundException e) {
      throw new StorageException(e);
    }
  }

  private Class<? extends AbstractAzkabanDataSource> resolveDataSourceType() {

    final String databaseType = this.props.getString("database.type");
    if ("h2".equals(databaseType)) {
      return H2FileDataSource.class;
    } else {
      return MySQLDataSource.class;
    }
  }

  private Class<? extends ExecutionLogsAdapter> resolveExecutionLogsType() {
    return ExecutionLogsDao.class;
  }

  @Provides
  public QueryRunner createQueryRunner(final AbstractAzkabanDataSource dataSource) {
    return new QueryRunner(dataSource);
  }
}
