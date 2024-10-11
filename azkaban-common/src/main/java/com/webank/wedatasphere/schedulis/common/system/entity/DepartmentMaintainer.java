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

package com.webank.wedatasphere.schedulis.common.system.entity;

import java.util.ArrayList;
import java.util.List;

/**
 * 部门运维人员录入表实体
 */
public class DepartmentMaintainer {

    private Integer departmentId;

    private String departmentName;

    private String opsUser;

    private List<String> opsUsers = new ArrayList<>();

    public DepartmentMaintainer() {
    }

    public DepartmentMaintainer(Integer departmentId, String departmentName, String opsUser) {
        this.departmentId = departmentId;
        this.departmentName = departmentName;
        this.opsUser = opsUser;
    }

    public Integer getDepartmentId() {
        return departmentId;
    }

    public void setDepartmentId(Integer departmentId) {
        this.departmentId = departmentId;
    }

    public String getDepartmentName() {
        return departmentName;
    }

    public void setDepartmentName(String departmentName) {
        this.departmentName = departmentName;
    }

    public String getOpsUser() {
        return opsUser;
    }

    public void setOpsUser(String opsUser) {
        this.opsUser = opsUser;
    }

    public List<String> getOpsUsers() {
        return opsUsers;
    }

    public void setOpsUsers(List<String> opsUsers) {
        this.opsUsers = opsUsers;
    }

    @Override
    public String toString() {
        return "DepartmentMaintainer{" +
                "departmentId=" + departmentId +
                ", departmentName='" + departmentName + '\'' +
                ", opsUser='" + opsUser + '\'' +
                '}';
    }
}
