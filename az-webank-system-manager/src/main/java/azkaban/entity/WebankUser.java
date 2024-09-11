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

package azkaban.entity;

public class WebankUser {


    public int appId;   // 表的默认

    public String userId;

    public String urn;

    public String fullName;

    public String displayName;

    public String title;

    public Long  employeeNumber;

    public String managerUrn;

    public String managerUserId;     //待确认项

    public Long  managerEmployeeNumber;  //待确认项

    public long orgId;

    public String  defaultGroupName;

    public String email;

    public long  departmentId;

    public String departmentName;

    public String startDate;   //员工入职日期

    //public String startDate;   //员工入职日期


    public String mobilePhone;

    //public char isActive;   // 是否有效,是否离职
    public String isActive;   // 是否有效,是否离职
//
//    public String orgHierarchy;
//
//    public int  orgHierarchyDepth;
//
    public int personGroup;

    public Long createdTime;

    public Long modifiedTime;

//    public String  whEtlExecId;




//    manager_user_id	varchar(50)	Lv0Leader - StaffID
//    manager_employee_number	int(10)	Lv0Leader - ID


    public WebankUser() {
    }
    // 部分带参构造方法
    public WebankUser(int appId, String userId, String urn, String fullName, String displayName, String title
        , long employeeNumber, String mangerUrn, /*String managerUserId, long managerEmployeeNumber,*/
        long orgId,String defaultGroupName, String email, long departmentId, String departmentName,
        String startDate, String mobilePhone, String isActive, int personGroup, Long createdTime, long modifiedTime) {
        this.appId = appId;
        this.userId = userId;
        this.urn = urn;
        this.fullName = fullName;
        this.displayName = displayName;
        this.title = title;
        this.employeeNumber = employeeNumber;
        this.managerUrn = mangerUrn;
//        this.managerUserId = managerUserId;
//        this.managerEmployeeNumber = managerEmployeeNumber;
        this.orgId = orgId;
        this.defaultGroupName = defaultGroupName;
        this.email = email;
        this.departmentId = departmentId;
        this.departmentName = departmentName;
        this.startDate = startDate;
        this.mobilePhone = mobilePhone;
        this.isActive = isActive;
        this.personGroup = personGroup;
        this.createdTime = createdTime;
        this.modifiedTime = modifiedTime;
    }

    @Override
    public String toString() {
        return "ExternalUser{" +
            "appId=" + appId +
            ", userId='" + userId + '\'' +
            ", urn='" + urn + '\'' +
            ", fullName='" + fullName + '\'' +
            ", displayName='" + displayName + '\'' +
            ", title='" + title + '\'' +
            ", employeeNumber=" + employeeNumber +
            ", managerUrn='" + managerUrn + '\'' +
            ", managerUserId='" + managerUserId + '\'' +
            ", managerEmployeeNumber=" + managerEmployeeNumber +
            ", orgId=" + orgId +
            ", defaultGroupName='" + defaultGroupName + '\'' +
            ", email='" + email + '\'' +
            ", departmentId=" + departmentId +
            ", departmentName='" + departmentName + '\'' +
            ", startDate=" + startDate +
            ", mobilePhone='" + mobilePhone + '\'' +
            ", isActive='" + isActive + '\'' +
            ", personGroup=" + personGroup +
            ", createdTime=" + createdTime +
            ", modifiedTime=" + modifiedTime +
            '}';
    }
}
