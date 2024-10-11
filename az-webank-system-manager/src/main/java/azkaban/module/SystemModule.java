package azkaban.module;

import azkaban.dao.SystemUserLoader;
import azkaban.dao.impl.JdbcSystemUserImpl;
import azkaban.service.UserManager;
import azkaban.service.impl.SystemManager;
import azkaban.service.impl.SystemUserManager;
import com.google.inject.AbstractModule;

/**
 * 系统管理注入信息module
 */
public class SystemModule extends AbstractModule {

    @Override
    protected void configure() {
        bind(UserManager.class).to(SystemUserManager.class);
        bind(SystemUserLoader.class).to(JdbcSystemUserImpl.class);
        bind(SystemManager.class);
    }
}
