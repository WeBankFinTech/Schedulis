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

package com.webank.wedatasphere.schedulis.system.module;

import com.webank.wedatasphere.schedulis.system.service.UserManager;
import com.webank.wedatasphere.schedulis.system.service.impl.SystemManager;
import com.webank.wedatasphere.schedulis.system.service.impl.SystemUserManager;
import com.webank.wedatasphere.schedulis.system.dao.SystemUserLoader;
import com.webank.wedatasphere.schedulis.system.dao.impl.JdbcSystemUserImpl;
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
