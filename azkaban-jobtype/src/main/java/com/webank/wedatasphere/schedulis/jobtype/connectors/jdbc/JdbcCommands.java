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

package com.webank.wedatasphere.schedulis.jobtype.connectors.jdbc;

import com.google.common.base.Optional;

/**
 * Various JDBC commands. Each operation may or may not committed depends on either implementaion class, or the one that
 * calls JdbcCommands.
 *
 */
public interface JdbcCommands {

  /**
   * Drops the table
   * @param table
   * @param database
   */
  public void dropTable(String table, Optional<String> database);

  /**
   * Truncates table.
   * @param table
   * @param database
   * @return
   */
  public Optional<Integer> truncateTable(String table, Optional<String> database);

//  /**
//   * Copies data from one table to another.
//   * @param src
//   * @param dest
//   * @param database Assumes that source and destination table is in same database.
//   * @return
//   */
//  public Optional<Integer> copyData(String src, String dest, Optional<String> database);

  /**
   * Check if table exist or not
   * @param table
   * @return
   */
  public boolean doesExist(String table, Optional<String> database);

}