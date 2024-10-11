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

package azkaban.webank.entity;

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


    public WebankDepartment() {
    }

    public WebankDepartment(Long dpId, String dpName, String dpChName, Long orgId, String orgName, String division, Long pid) {
        this.dpId = dpId;
        this.dpName = dpName;
        this.dpChName = dpChName;
        this.orgId = orgId;
        this.orgName = orgName;
        this.division = division;
        this.pid = pid;
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
                '}';
    }
}
