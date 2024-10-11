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

package com.webank.wedatasphere.schedulis.system.dao;

import com.webank.wedatasphere.schedulis.system.entity.WebankDepartment;
import com.webank.wedatasphere.schedulis.system.entity.WebankUser;
import com.webank.wedatasphere.schedulis.system.entity.WtssPermissions;
import com.webank.wedatasphere.schedulis.system.entity.WtssRole;
import com.webank.wedatasphere.schedulis.system.entity.WtssUser;
import com.webank.wedatasphere.schedulis.system.exception.SystemUserManagerException;
import java.util.List;

public interface SystemUserLoader {


    int getWebankUserTotal() throws SystemUserManagerException;

    /**
     *
     */
    List<WebankUser> findAllWebankUserList(String searchName)
        throws SystemUserManagerException;

    /**
     *
     */
    List<WebankUser> findAllWebankUserPageList(String searchName, int pageNum
        , int pageSize) throws SystemUserManagerException;


    /**
     *
     */
    List<WtssUser> findSystemUserPage(final String userName, final String fullName
        , final String departmentName, int start, int pageSize) throws SystemUserManagerException;

    /**
     * @return
     * @throws SystemUserManagerException
     */
    int getWtssUserTotal() throws SystemUserManagerException;

    /**
     * @return
     * @throws SystemUserManagerException
     */
    int getWtssUserTotal(String username) throws SystemUserManagerException;

    /**
     * 新增系统用户
     *
     * @param wtssUser
     * @throws SystemUserManagerException
     */
    int addWtssUser(final WtssUser wtssUser) throws SystemUserManagerException;

    /**
     *
     */
    WebankUser getWebankUserByUserId(final String userId) throws SystemUserManagerException;

    /**
     * @param wtssUser
     * @return
     * @throws SystemUserManagerException
     */
    int updateWtssUser(final WtssUser wtssUser) throws SystemUserManagerException;

    /**
     * 根据id进行更新
     *
     * @param wtssUser
     * @param synEsb   是否是同步ESB的方式更新
     * @return
     * @throws SystemUserManagerException
     */
    int updateWtssUser(final WtssUser wtssUser, boolean synEsb) throws SystemUserManagerException;

    /**
     * 根据name进行更新
     *
     * @param wtssUser
     * @param synEsb   是否是同步ESB的方式更新
     * @return
     * @throws SystemUserManagerException
     */
    int updateWtssUserByName(final WtssUser wtssUser, boolean synEsb) throws SystemUserManagerException;

    /**
     *
     */
    WtssUser getWtssUserByUserId(final String userId) throws SystemUserManagerException;

    /**
     * @param username
     * @param password
     * @return
     * @throws SystemUserManagerException
     */
    WtssUser getWtssUserByUsernameAndPassword(final String username, final String password) throws SystemUserManagerException;

    /**
     * @param wtssUser
     * @return
     * @throws SystemUserManagerException
     */
    WtssUser getWtssUserByUsernameAndPassword(final WtssUser wtssUser) throws SystemUserManagerException;


    /**
     * @param roleId
     * @return
     * @throws SystemUserManagerException
     */
    WtssRole getWtssRoleById(final int roleId) throws SystemUserManagerException;

    /**
     * @param permissionsId
     * @return
     * @throws SystemUserManagerException
     */
    WtssPermissions getWtssPermissionsById(final int permissionsId) throws SystemUserManagerException;

    /**
     * @param permissionsIds
     * @return
     * @throws SystemUserManagerException
     */
    List<WtssPermissions> getWtssPermissionsListByIds(final String permissionsIds) throws SystemUserManagerException;

    /**
     * @param username
     * @return
     * @throws SystemUserManagerException
     */
    WtssUser getWtssUserByUsername(final String username) throws SystemUserManagerException;


    int getWebankDepartmentTotal() throws SystemUserManagerException;

    int getWebankDepartmentTotal(final String searchterm) throws SystemUserManagerException;

    /**
     *
     */
    List<WebankDepartment> findAllWebankDepartmentList(String searchName)
        throws SystemUserManagerException;

    /**
     *
     */
    List<WebankDepartment> findAllWebankDepartmentPageList(String searchName, int pageNum
        , int pageSize) throws SystemUserManagerException;

    /**
     *
     */
    WebankDepartment getWebankDepartmentByDpId(int dpId)
        throws SystemUserManagerException;

    /**
     *
     */
    WebankDepartment getParentDepartmentByPId(int pId)
        throws SystemUserManagerException;

    /**
     *
     */
    List<WebankDepartment> findAllWebankDepartmentPageOrSearch(String searchName, int pageNum
        , int pageSize) throws SystemUserManagerException;

    /**
     * @param userId
     * @return
     * @throws SystemUserManagerException
     */
    int deleteWtssUser(final String userId) throws SystemUserManagerException;

    /**
     *
     */
    WebankUser getWebankUserByUsername(final String username) throws SystemUserManagerException;

    int addDeparment(final WebankDepartment webankDepartment) throws SystemUserManagerException;

    int updateDeparment(final WebankDepartment webankDepartment) throws SystemUserManagerException;

    int deleteDeparment(int dpId) throws SystemUserManagerException;

    /**
     * 根据部门编号查询wtssUser
     *
     * @param dpId
     * @return
     */
    List<WtssUser> getSystemUserByDepartmentId(int dpId) throws SystemUserManagerException;


    /**
     * 根据用户名查找用户
     *
     * @param userName
     * @return
     * @throws SystemUserManagerException
     */
    WtssUser getSystemUserByUserName(String userName) throws SystemUserManagerException;

    WebankUser getWebankUserByUserName(String userName) throws SystemUserManagerException;

}
