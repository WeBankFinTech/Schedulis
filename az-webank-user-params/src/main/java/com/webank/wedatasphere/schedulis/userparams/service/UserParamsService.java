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

package com.webank.wedatasphere.schedulis.userparams.service;

import static java.util.Objects.requireNonNull;

import azkaban.executor.ExecutorManagerException;
import azkaban.executor.JdbcExecutorLoader;
import azkaban.project.ProjectLoader;
import azkaban.utils.Props;
import com.webank.wedatasphere.schedulis.common.executor.UserVariable;
import com.webank.wedatasphere.schedulis.common.system.SystemUserLoader;
import com.webank.wedatasphere.schedulis.common.system.SystemUserManagerException;
import com.webank.wedatasphere.schedulis.common.system.entity.WtssUser;
import java.util.ArrayList;
import java.util.List;
import javax.inject.Inject;
import javax.inject.Singleton;
import org.apache.commons.collections4.CollectionUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Singleton
public class UserParamsService {

    private static final Logger logger = LoggerFactory.getLogger(UserParamsService.class);
    private final ProjectLoader projectLoader;
    private final JdbcExecutorLoader jdbcExecutorLoader;
    private final SystemUserLoader systemUserLoader;
    private final Props props;

    @Inject
    public UserParamsService(
            final ProjectLoader loader,
            final JdbcExecutorLoader jdbcExecutorLoader,
            final SystemUserLoader systemUserLoader,
            final Props props) {

        this.projectLoader = requireNonNull(loader);
        this.props = requireNonNull(props);
        this.jdbcExecutorLoader = jdbcExecutorLoader;
        this.systemUserLoader = systemUserLoader;
    }

    public Props getProps() {
        return this.props;
    }

    public List<UserVariable> fetchAllUserVariable(UserVariable userVariable){
        List<UserVariable> userVariables = new ArrayList<>();
        try {
            userVariables = jdbcExecutorLoader.fetchAllUserVariable(userVariable);
        }catch (ExecutorManagerException e){
            logger.error("fetch All User Variable failed.");
        }
        return userVariables;
    }

    public boolean addUserVariable(UserVariable userVariable){
        boolean ret = false;
        try {
            jdbcExecutorLoader.addUserVariable(userVariable);
            ret = true;
        }catch (ExecutorManagerException e){
            logger.error("add userVariable failed");
        }
        return ret;
    }

    public boolean deleteUserVariable(UserVariable userVariable){
        int ret = 0;
        try {
            ret = jdbcExecutorLoader.deleteUserVariable(userVariable);
        }catch (ExecutorManagerException e){
            logger.error("delete UserVariable failed");
        }
        if(ret != 0) {
            return true;
        }
        return false;
    }

    public boolean updateUserVariable(UserVariable userVariable){
        int ret = 0;
        try {
            ret = jdbcExecutorLoader.updateUserVariable(userVariable);
        }catch (ExecutorManagerException e){
            logger.error("update UserVariable failed");
        }
        if(ret != 0) {
            return true;
        }
        return false;
    }

    public UserVariable getUserVariableById(Integer id){
        UserVariable userVariable = null;
        try {
            userVariable = jdbcExecutorLoader.getUserVariableById(id);
        }catch (ExecutorManagerException e){
            logger.error("get Department Group by id failed");
        }
        return userVariable;
    }

    public boolean checkWtssUserIsExist(String name){
        int cout = 0;
        try {
            cout = jdbcExecutorLoader.findWtssUserByName(name);
        }catch (ExecutorManagerException e){
            logger.error("can not found wtssuser by" + name + ", ", e);
        }
        if(cout == 0){
            return false;
        }
        return true;
    }

    public Integer getWtssUserTotal() throws ExecutorManagerException {
        return this.jdbcExecutorLoader.getWtssUserTotal();
    }

    public List<WtssUser> findAllWtssUserPageList(String searchName, int pageNum, int pageSize) throws ExecutorManagerException {
        return this.jdbcExecutorLoader.findAllWtssUserPageList(searchName, pageNum, pageSize);
    }

    /**
     * 获取当前用户维护的部门的用户参数
     * @param currentUser
     * @throws SystemUserManagerException
     */
    public List<UserVariable> getDepMaintainDepList(String currentUser) throws SystemUserManagerException, ExecutorManagerException {
        List<UserVariable> depUserVariableList = new ArrayList<>();
        List<Integer> depList = this.systemUserLoader.getDepartmentMaintainerDepListByUserName(currentUser);
        if (CollectionUtils.isNotEmpty(depList)) {
            for (Integer departmentId : depList) {
                depUserVariableList = this.jdbcExecutorLoader.fetchAllUserVariableByOwnerDepartment(departmentId);
            }
        }
        return depUserVariableList;
    }
}