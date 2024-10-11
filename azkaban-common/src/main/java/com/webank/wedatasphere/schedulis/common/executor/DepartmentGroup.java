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

import azkaban.executor.Executor;
import java.util.ArrayList;
import java.util.List;

/**
 *  部门分组
 */
public class DepartmentGroup {

    private Integer id;

    private Integer oldId;

    private String name;

    private String description;

    private Long createTime;

    private Long updateTime;

    private List<Integer> executorIds = new ArrayList<>();

    private List<Executor> executors = new ArrayList<>();

    public DepartmentGroup() {
    }

    public DepartmentGroup(Integer id, String name, String description, Long createTime, Long updateTime, List<Integer> executorIds) {
        this.id = id;
        this.name = name;
        this.description = description;
        this.createTime = createTime;
        this.updateTime = updateTime;
        this.executorIds = executorIds;
    }

    public List<Executor> getExecutors() {
        return executors;
    }

    public void setExecutors(List<Executor> executors) {
        this.executors = executors;
    }

    public Integer getId() {
        return id;
    }

    public void setId(Integer id) {
        this.id = id;
    }

    public Integer getOldId() {
        return oldId;
    }

    public void setOldId(Integer oldId) {
        this.oldId = oldId;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
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

    public List<Integer> getExecutorIds() {
        return executorIds;
    }

    public void setExecutorIds(List<Integer> executorIds) {
        this.executorIds = executorIds;
    }

    @Override
    public String toString() {
        return "DepartmentGroup{" +
                "id=" + id +
                ", oldId=" + oldId +
                ", name='" + name + '\'' +
                ", description='" + description + '\'' +
                ", createTime=" + createTime +
                ", updateTime=" + updateTime +
                ", executorIds=" + executorIds +
                ", executors=" + executors +
                '}';
    }
}