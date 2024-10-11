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

package com.webank.wedatasphere.schedulis.common.executor;

import com.webank.wedatasphere.schedulis.common.system.entity.WtssUser;

import java.util.ArrayList;
import java.util.List;

/**
 *  用户变量
 */
public class UserVariable {

    private Integer id;

    private String key;

    private String description;

    private String value;

    private String owner;

    private Long createTime;

    private Long updateTime;

    private List<WtssUser> users = new ArrayList<>();

    public UserVariable(Integer id, String key, String description, String value, Long createTime, Long updateTime) {
        this.id = id;
        this.key = key;
        this.description = description;
        this.value = value;
        this.createTime = createTime;
        this.updateTime = updateTime;
    }

    public UserVariable() {
    }

    public Integer getId() {
        return id;
    }

    public void setId(Integer id) {
        this.id = id;
    }

    public String getKey() {
        return key;
    }

    public void setKey(String key) {
        this.key = key;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getValue() {
        return value;
    }

    public void setValue(String value) {
        this.value = value;
    }

    public Long getCreateTime() {
        return createTime;
    }

    public void setCreateTime(Long createTime) {
        this.createTime = createTime;
    }

    public Long getUpdateTime() {
        return updateTime;
    }

    public void setUpdateTime(Long updateTime) {
        this.updateTime = updateTime;
    }

    public List<WtssUser> getUsers() {
        return users;
    }

    public void setUsers(List<WtssUser> wtssUsers) {
        this.users = wtssUsers;
    }

    public void setOwner(String owner) {
        this.owner = owner;
    }

    public String getOwner() {
        return owner;
    }

    @Override
    public String toString() {
        return "UserVariable{" +
                "id=" + id +
                ", key='" + key + '\'' +
                ", description='" + description + '\'' +
                ", value='" + value + '\'' +
                ", createTime=" + createTime +
                ", updateTime=" + updateTime +
                '}';
    }
}