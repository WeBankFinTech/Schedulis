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

package com.webank.wedatasphere.schedulis.common.utils;

import java.util.List;
import java.util.StringJoiner;

/**
 * sql语句封装工具类,用于封装SQL拼接
 */
public class SqlWrapperUtils {

    public static final String DESC_INSERT = "INSERT INTO ";
    public static final String DESC_VALUES = " VALUES ";
    public static final String DESC_INSERT_OR_UPDATE = " ON duplicate key UPDATE ";


    public String insertOrUpdate(String tableName, List<String> tableColumns, List<Object> valueColumns,
                                 List<Object> destKeys, List<Object> destvalues) {
        StringBuilder builder = new StringBuilder();
        builder.append(DESC_INSERT).append(tableName);

        // 拼接字段
        StringJoiner paramsStr = new StringJoiner("`,`", "(`", "`)");
        tableColumns.forEach(paramsStr::add);
        builder.append(paramsStr.toString()).append(DESC_VALUES);

        StringJoiner valuesStr = new StringJoiner(",", "(", ")");
        for (Object valueColumn : valueColumns) {
            StringBuilder valueParamBuilder = new StringBuilder();
            // 获取属性类型
            String filedType = valueColumn.getClass().getSimpleName();

            // 属性判断
            if (filedType.equals("Integer") || filedType.equals("Long")) {
                valueParamBuilder.append(valueColumn);
            } else if (filedType.equals("String") || filedType.equals("Boolean")) {
                valueParamBuilder.append("'").append(valueColumn).append("'");
            }
            valuesStr.add(valueParamBuilder);
        }
        // 拼接字段值
        builder.append(valuesStr.toString()).append(DESC_INSERT_OR_UPDATE);

        StringJoiner kvStr = new StringJoiner(",", "", "");
        for (int i = 0; i < destKeys.size(); i++) {
            StringBuilder kvBuilder = new StringBuilder();
            kvBuilder.append(destKeys.get(i)).append("=");
            // 属性判断
            Object value = destvalues.get(i);
            String filedValueType = value.getClass().getSimpleName();
            // 属性判断
            if (filedValueType.equals("Integer") || filedValueType.equals("Long")) {
                kvBuilder.append(value);
            } else if (filedValueType.equals("String") || filedValueType.equals("Boolean")) {
                kvBuilder.append("'").append(value).append("'");
            }
            kvStr.add(kvBuilder);
        }
        // 拼接需要插入或者修改的值
        builder.append(kvStr.toString()).append(";");

        return builder.toString();
    }
}
