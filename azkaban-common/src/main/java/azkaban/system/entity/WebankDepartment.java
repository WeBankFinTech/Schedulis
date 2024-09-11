/**
 * Copyright 2015 LinkedIn Corp. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 */

package azkaban.system.entity;

import azkaban.executor.DepartmentGroup;

/**
 * cfg_webank_department维护微众银行各业务部门列表JavaBean
 *
 * Created by v_wbwpyin on 2017/5/22.
 */
public class WebankDepartment {

    public Long dpId ;    //NOT NULL   部门ID
    public String dpName;  //NOT NULL  英文部门名称
    public String dpChName; //NOT NULL 中文部门名称
    public Long  orgId ;    //NOT NULL   室ID
    public String  orgName ;    //NOT NULL   室名称
    public String division;  //NOT NULL 部门所属事业条线
    public Long pid;
    private Integer groupId;
    private Integer uploadFlag;
    private DepartmentGroup departmentGroup;


    public WebankDepartment() {
    }

    public WebankDepartment(Long dpId, String dpName, String dpChName, Long orgId, String orgName, String division, Long pid, Integer uploadFlag) {
        this.dpId = dpId;
        this.dpName = dpName;
        this.dpChName = dpChName;
        this.orgId = orgId;
        this.orgName = orgName;
        this.division = division;
        this.pid = pid;
        this.uploadFlag = uploadFlag;
    }

    public WebankDepartment(Long dpId, String dpName, String dpChName, Long orgId, String orgName, String division, Long pid, Integer groupId, Integer uploadFlag) {
        this.dpId = dpId;
        this.dpName = dpName;
        this.dpChName = dpChName;
        this.orgId = orgId;
        this.orgName = orgName;
        this.division = division;
        this.pid = pid;
        this.groupId = groupId;
        this.uploadFlag = uploadFlag;
    }

    public Integer getGroupId() {
        return groupId;
    }

    public void setGroupId(Integer groupId) {
        this.groupId = groupId;
    }

    public Long getDpId() {
        return dpId;
    }

    public void setDpId(Long dpId) {
        this.dpId = dpId;
    }

    public String getDpName() {
        return dpName;
    }

    public void setDpName(String dpName) {
        this.dpName = dpName;
    }

    public String getDpChName() {
        return dpChName;
    }

    public void setDpChName(String dpChName) {
        this.dpChName = dpChName;
    }

    public Long getOrgId() {
        return orgId;
    }

    public void setOrgId(Long orgId) {
        this.orgId = orgId;
    }

    public String getOrgName() {
        return orgName;
    }

    public void setOrgName(String orgName) {
        this.orgName = orgName;
    }

    public String getDivision() {
        return division;
    }

    public void setDivision(String division) {
        this.division = division;
    }

    public Long getPid() {
        return pid;
    }

    public void setPid(Long pid) {
        this.pid = pid;
    }

    public DepartmentGroup getDepartmentGroup() {
        return departmentGroup;
    }

    public void setDepartmentGroup(DepartmentGroup departmentGroup) {
        this.departmentGroup = departmentGroup;
    }

    public Integer getUploadFlag() {
        return uploadFlag;
    }

    public void setUploadFlag(Integer uploadFlag) {
        this.uploadFlag = uploadFlag;
    }

    @Override
    public String toString() {
        return "WebankDepartment{" +
                "dpId=" + dpId +
                ", dpName='" + dpName + '\'' +
                ", dpChName='" + dpChName + '\'' +
                ", orgId=" + orgId +
                ", orgName='" + orgName + '\'' +
                ", division='" + division + '\'' +
                ", uploadFlag='" + uploadFlag + '\'' +
                '}';
    }
}
