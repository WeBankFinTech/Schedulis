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

package com.webank.wedatasphere.schedulis.jobtype.hiveutils.azkaban.hive.actions;

import com.webank.wedatasphere.schedulis.jobtype.hiveutils.HiveQueryExecutionException;
import com.webank.wedatasphere.schedulis.jobtype.hiveutils.HiveQueryExecutor;
import com.webank.wedatasphere.schedulis.jobtype.hiveutils.azkaban.HiveAction;
import com.webank.wedatasphere.schedulis.jobtype.hiveutils.azkaban.HiveViaAzkabanException;
import com.webank.wedatasphere.schedulis.jobtype.hiveutils.util.AzkHiveAction;
import com.webank.wedatasphere.schedulis.jobtype.hiveutils.util.AzkabanJobPropertyDescription;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.FileSystem;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Properties;

/**
 * Alter the specified table's location to the 'latest' directory found in
 * specified base directory, where latest is defined as greate lexically.</p>
 *
 * For example, if we have a base dir foo with directories:</p>
 *   /foo/2012-01-01</p>
 *       /2012-01-02</p>
 *       /2012-01-03</p>
 * and we specify table as 'bar', this action will execute
 * ALTER TABLE bar SET LOCATION '/foo/2012-01-03';</p>
 * </p>
 */
@AzkHiveAction(Constants.UPDATE_TABLE_LOCATION_TO_LATEST)
public class UpdateTableLocationToLatest implements HiveAction {
  private final static Logger logger = LoggerFactory.getLogger(UpdateTableLocationToLatest.class);

  public static final String UPDATE_TABLE_LOCATION_TO_LATEST =
      Constants.UPDATE_TABLE_LOCATION_TO_LATEST;

  @AzkabanJobPropertyDescription("Comma-separated list of tables to update.  All tables must be within the same database")
  public static final String HIVE_TABLES = "hive.tables";
  @AzkabanJobPropertyDescription("Comma-separated list of new tables locations base paths. These dirs will be searched for latest directory.  Must correspond to hive.tables.")
  public static final String HIVE_TABLES_LOCATIONS = "hive.tables.locations";
  @AzkabanJobPropertyDescription("Database within tables to update are located")
  public static final String HIVE_DATABASE = "hive.database";

  private final String database;
  private final String[] tables;
  private final String[] tablesLocations;
  private final HiveQueryExecutor hqe;

  public UpdateTableLocationToLatest(Properties p, HiveQueryExecutor hqe)
      throws HiveViaAzkabanException {
    this.database = com.webank.wedatasphere.schedulis.jobtype.hiveutils.azkaban.Utils.verifyProperty(p, HIVE_DATABASE);
    this.tables = com.webank.wedatasphere.schedulis.jobtype.hiveutils.azkaban.Utils.verifyProperty(p, HIVE_TABLES).split(",");
    this.tablesLocations = com.webank.wedatasphere.schedulis.jobtype.hiveutils.azkaban.Utils.verifyProperty(p, HIVE_TABLES_LOCATIONS).split(",");

    if (tables.length != tablesLocations.length) {
      throw new HiveViaAzkabanException(HIVE_TABLES + " and "
          + HIVE_TABLES_LOCATIONS + " don't have same number of elements");
    }

    this.hqe = hqe;
  }

  @Override
  public void execute() throws HiveViaAzkabanException {
    ArrayList<HQL> hql = new ArrayList<HQL>();
    hql.add(new UseDatabaseHQL(database));

    Configuration conf = new Configuration();
    try {
      FileSystem fs = FileSystem.get(conf);

      for (int i = 0; i < tables.length; i++) {
        logger.info("Determining HQL commands for table " + tables[i]);
        hql.add(latestURI(fs, tablesLocations[i], tables[i]));
      }
      fs.close();
    } catch (IOException e) {
      throw new HiveViaAzkabanException(
          "Exception fetching the directories from HDFS", e);
    }
    StringBuffer query = new StringBuffer();
    for (HQL q : hql) {
      query.append(q.toHQL()).append("\n");
    }

    System.out.println("Query to execute:\n" + query.toString());
    try {
      hqe.executeQuery(query.toString());
    } catch (HiveQueryExecutionException e) {
      throw new HiveViaAzkabanException("Problem executing query ["
          + query.toString() + "] on Hive", e);
    }

  }

  private HQL latestURI(FileSystem fs, String basePath, String table)
      throws HiveViaAzkabanException, IOException {
    ArrayList<String> directories = null;

    // Alter Table Set Location requires full URI...
    // https://issues.apache.org/jira/browse/HIVE-3860
    directories = Utils.fetchDirectories(fs, basePath, true);

    if (directories.size() == 0) {
      throw new HiveViaAzkabanException(
          "No directories to set as new location in " + basePath);
    }

    Collections.sort(directories);

    String toAdd = directories.remove(directories.size() - 1);

    return new AlterTableLocationQL(table, toAdd);
  }
}
