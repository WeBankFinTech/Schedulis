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

package com.webank.wedatasphere.schedulis.common.system.common;

import com.webank.wedatasphere.schedulis.common.system.SystemUserManagerException;
import com.webank.wedatasphere.schedulis.common.system.SystemManager;
import com.webank.wedatasphere.schedulis.common.system.entity.DepartmentMaintainer;
import com.webank.wedatasphere.schedulis.common.system.entity.WebankDepartment;
import com.webank.wedatasphere.schedulis.common.system.entity.WebankUser;
import com.webank.wedatasphere.schedulis.common.system.entity.WtssUser;
import azkaban.user.User;
import azkaban.user.UserManager;
import azkaban.utils.Props;
import java.util.List;
import javax.inject.Inject;

/**
 * 过渡接口服务工具类
 */

public class TransitionService {

    private UserManager userManager;
    private SystemManager systemManager;
    private Props props;

    @Inject
    public TransitionService(UserManager userManager, SystemManager systemManager, Props props) {
        this.userManager = userManager;
        this.systemManager = systemManager;
        this.props = props;
    }

    public SystemManager getSystemManager() {
        return systemManager;
    }

    public UserManager getUserManager() {
        return userManager;
    }

    /**
     * 根据用户名查找所在部门
     * @param userName
     * @return
     * @throws SystemUserManagerException
     */
    public String getUserDepartmentByUsername(String userName) throws SystemUserManagerException {
        return this.systemManager.getUserDepartmentByUsername(userName);
    }

    /**
     * 根据用户名查找WTSS用户
     * @param userName
     * @return
     * @throws SystemUserManagerException
     */
    public WtssUser getSystemUserByUserName(String userName) throws SystemUserManagerException {
        return this.systemManager.getSystemUserByUserName(userName);
    }

    /**
     * 根据用户ID查找WTSS用户
     * @param userId
     * @return
     * @throws SystemUserManagerException
     */
    public WtssUser getSystemUserById(String userId) throws SystemUserManagerException {
        return this.systemManager.getSystemUserById(userId);
    }

    /**
     * 根据用户名查找webank用户
     * @param userName
     * @return
     */
    public WebankUser getWebankUserByUserName(String userName) {
        return this.systemManager.getWebankUserByUserName(userName);
    }

    /**
     * 根据部门编号查找所在部门
     * @param departmentId
     * @return
     * @throws SystemUserManagerException
     */
    public WebankDepartment getWebankDepartmentByDpId(Integer departmentId) throws SystemUserManagerException {
        return this.systemManager.getWebankDepartmentByDpId(departmentId);
    }

    /**
     * 根据用户名查找所有需要运维的部门编号
     * @param userName
     * @return
     * @throws SystemUserManagerException
     */
    public List<Integer> getDepartmentMaintainerDepListByUserName(String userName) throws SystemUserManagerException {
        return this.systemManager.getDepartmentMaintainerDepListByUserName(userName);
    }

    /**
     * 根据用户名查找所有需要运维的部门编号
     * @param departmentId
     * @return
     * @throws SystemUserManagerException
     */
    public String getDepartmentMaintainerByDepId(long departmentId) throws SystemUserManagerException {
        DepartmentMaintainer departmentMaintainer = this.systemManager.getDepMaintainerByDepId(departmentId);
        if (departmentMaintainer != null) {
            return departmentMaintainer.getOpsUser();
        } else {
            return null;
        }
    }

    /**
     * 根据部门查找用户
     * @param depId
     * @return
     */
    public List<WtssUser> getSystemUserByDepId(Integer depId) {
        return this.systemManager.getSystemUserByDepartmentId(depId);
    }


    public Boolean validateProxyUser(String proxyUserName, User user) {
        return this.userManager.validateProxyUser(proxyUserName, user);
    }

    public Boolean validateGroup(String group) {
        return this.userManager.validateGroup(group);
    }

    public Boolean validateUser(String userName) {
        return this.userManager.validateUser(userName);
    }

}
