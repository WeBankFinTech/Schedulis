/*
 * Copyright 2017 LinkedIn Corp.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */

package azkaban.executor;

import azkaban.db.DatabaseOperator;
import azkaban.db.DatabaseTransOperator;
import azkaban.db.EncodingType;
import azkaban.db.SQLTransaction;
import azkaban.utils.FileIOUtils;
import azkaban.utils.FileIOUtils.LogData;
import azkaban.utils.GZIPUtils;
import azkaban.utils.Pair;
import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.sql.Blob;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Arrays;
import javax.inject.Inject;
import javax.inject.Singleton;
import org.apache.commons.dbutils.ResultSetHandler;
import org.apache.commons.io.IOUtils;
import org.joda.time.DateTime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


@Singleton
public class ExecutionLogsDao implements ExecutionLogsAdapter {

  private static final Logger logger = LoggerFactory.getLogger(ExecutionLogsDao.class);
  private final DatabaseOperator dbOperator;
  private final EncodingType defaultEncodingType = EncodingType.GZIP;

  @Inject
  ExecutionLogsDao(final DatabaseOperator dbOperator) {
    this.dbOperator = dbOperator;
  }

  // TODO kunkun-tang: the interface's parameter is called endByte, but actually is length.
  @Override
  public LogData fetchLogs(final int execId, final String name, final int attempt,
                           final int startByte,
                           final int length) throws ExecutorManagerException {
    final FetchLogsHandler handler = new FetchLogsHandler(startByte, length + startByte);
    try {
      return this.dbOperator.query(FetchLogsHandler.FETCH_LOGS, handler,
              execId, name, attempt, startByte, startByte + length);
    } catch (final SQLException e) {
      throw new ExecutorManagerException("Error fetching logs " + execId
              + " : " + name, e);
    }
  }

  @Override
  public String getHdfsLogPath(int execId, String name, int attempt)
          throws ExecutorManagerException {
    FetchHdfsLogPathHandler handler = new FetchHdfsLogPathHandler();
    try {
      return this.dbOperator.query(FetchHdfsLogPathHandler.FETCH_LOGS, handler,
              execId, name, attempt);
    } catch (final SQLException e) {
      throw new ExecutorManagerException(
              String.format("Error fetching HDFS path, execId: %d, job: %s, attempt: %d .", execId,
                      name, attempt), e);
    }
  }

  @Override
  public int getLogEncType(int execId, String name, int attempt) throws ExecutorManagerException {
    FetchLogEncTypeHandler handler = new FetchLogEncTypeHandler();
    try {
      return this.dbOperator.query(FetchLogEncTypeHandler.FETCH_LOG_ENCTYPE, handler,
              execId, name, attempt);
    } catch (SQLException e) {
      throw new ExecutorManagerException(
              String.format("Error fetching log encType, execId: %d, job: %s, attempt: %d .", execId,
                      name, attempt), e);
    }
  }

  @Override
  public Long getJobLogMaxSize(int execId, String jobName, int attempt) throws ExecutorManagerException {
    final JobLogsOffsetHandler handler = new JobLogsOffsetHandler();
    try {
      return this.dbOperator.query(JobLogsOffsetHandler.FETCH_LOGS_MAX_SIZE, handler,
              execId, jobName, attempt);
    } catch (final SQLException e) {
      throw new ExecutorManagerException(String.format("Error fetching job log max size, execId: %d, job: %s, attempt: %d .", execId, jobName, attempt), e);
    }
  }

  @Override
  public void uploadLogFile(final int execId, final String name, final int attempt,
                            final File... files) throws ExecutorManagerException {
    final SQLTransaction<Integer> transaction = transOperator -> {
      uploadLogFile(transOperator, execId, name, attempt, files, this.defaultEncodingType);
      transOperator.getConnection().commit();
      return 1;
    };
    try {
      this.dbOperator.transaction(transaction);
    } catch (final SQLException e) {
      logger.error("uploadLogFile failed.", e);
      throw new ExecutorManagerException("uploadLogFile failed.", e);
    }
  }

  @Override
  public void uploadLogPath(final int execId, final String name, final int attempt,
                            final String hdfsPath) throws ExecutorManagerException {
    final SQLTransaction<Integer> transaction = transOperator -> {
      uploadLogPath(transOperator, execId, name, attempt, hdfsPath, EncodingType.HDFS);
      transOperator.getConnection().commit();
      return 1;
    };
    try {
      this.dbOperator.transaction(transaction);
    } catch (SQLException e) {
      logger.error("uploadLogFile failed.", e);
      throw new ExecutorManagerException("uploadLogFile failed.", e);
    }
  }

  private void uploadLogPath(final DatabaseTransOperator transOperator, final int execId,
                             final String name, final int attempt, final String hdfsPath, final EncodingType encType)
          throws SQLException {
    int startByte = 0;
    final String INSERT_EXECUTION_LOGS = "INSERT INTO execution_logs "
            + "(exec_id, name, attempt, enc_type, start_byte, log, upload_time) VALUES (?,?,?,?,?,?,?) ";

    try {
      transOperator.update(INSERT_EXECUTION_LOGS, execId, name, attempt,
              encType.getNumVal(), startByte, hdfsPath, DateTime.now().getMillis());
    } catch (SQLException e) {
      logger.error("Error writing DataBase.", e);
      throw new SQLException("Error writing DataBase.", e);
    }
  }

  private void uploadLogFile(final DatabaseTransOperator transOperator, final int execId,
                             final String name,
                             final int attempt, final File[] files, final EncodingType encType)
          throws SQLException {
    // 50K buffer... if logs are greater than this, we chunk.
    // However, we better prevent large log files from being uploaded somehow
    final byte[] buffer = new byte[50 * 1024];
    int pos = 0;
    int length = buffer.length;
    int startByte = 0;
    try {
      for (int i = 0; i < files.length; ++i) {
        final File file = files[i];

        final BufferedInputStream bufferedStream =
                new BufferedInputStream(new FileInputStream(file));
        try {
          int size = bufferedStream.read(buffer, pos, length);
          while (size >= 0) {
            if (pos + size == buffer.length) {
              // Flush here.
              uploadLogPart(transOperator, execId, name, attempt, startByte,
                      startByte + buffer.length, encType, buffer, buffer.length);

              pos = 0;
              length = buffer.length;
              startByte += buffer.length;
            } else {
              // Usually end of file.
              pos += size;
              length = buffer.length - pos;
            }
            size = bufferedStream.read(buffer, pos, length);
          }
        } finally {
          IOUtils.closeQuietly(bufferedStream);
        }
      }

      // Final commit of buffer.
      if (pos > 0) {
        uploadLogPart(transOperator, execId, name, attempt, startByte, startByte
                + pos, encType, buffer, pos);
      }
    } catch (final SQLException e) {
      logger.error("Error writing log part.", e);
      throw new SQLException("Error writing log part", e);
    } catch (final IOException e) {
      logger.error("Error chunking.", e);
      throw new SQLException("Error chunking", e);
    }
  }

  @Override
  public int removeExecutionLogsByTime(final long millis)
          throws ExecutorManagerException {
    final String DELETE_BY_TIME =
            "DELETE FROM execution_logs WHERE upload_time < ?";
    try {
      return this.dbOperator.update(DELETE_BY_TIME, millis);
    } catch (final SQLException e) {
      logger.error("delete execution logs failed", e);
      throw new ExecutorManagerException(
              "Error deleting old execution_logs before " + millis, e);
    }
  }

  private void uploadLogPart(final DatabaseTransOperator transOperator, final int execId,
                             final String name,
                             final int attempt, final int startByte, final int endByte,
                             final EncodingType encType,
                             final byte[] buffer, final int length)
          throws SQLException, IOException {
    final String INSERT_EXECUTION_LOGS = "INSERT INTO execution_logs "
            + "(exec_id, name, attempt, enc_type, start_byte, end_byte, "
            + "log, upload_time) VALUES (?,?,?,?,?,?,?,?)";

    byte[] buf = buffer;
    if (encType == EncodingType.GZIP) {
      buf = GZIPUtils.gzipBytes(buf, 0, length);
    } else if (length < buf.length) {
      buf = Arrays.copyOf(buffer, length);
    }

    transOperator.update(INSERT_EXECUTION_LOGS, execId, name, attempt,
            encType.getNumVal(), startByte, startByte + length, buf, DateTime.now()
                    .getMillis());
  }

  private static class FetchLogsHandler implements ResultSetHandler<LogData> {

    private static final String FETCH_LOGS =
            "SELECT exec_id, name, attempt, enc_type, start_byte, end_byte, log "
                    + "FROM execution_logs "
                    + "WHERE exec_id=? AND name=? AND attempt=? AND end_byte > ? "
                    + "AND start_byte <= ? ORDER BY start_byte";

    private final int startByte;
    private final int endByte;

    FetchLogsHandler(final int startByte, final int endByte) {
      this.startByte = startByte;
      this.endByte = endByte;
    }

    @Override
    public LogData handle(final ResultSet rs) throws SQLException {
      if (!rs.next()) {
        return null;
      }

      final ByteArrayOutputStream byteStream = new ByteArrayOutputStream();

      do {
        // int execId = rs.getInt(1);
        // String name = rs.getString(2);
        final int attempt = rs.getInt(3);
        final EncodingType encType = EncodingType.fromInteger(rs.getInt(4));
        final int startByte = rs.getInt(5);
        final int endByte = rs.getInt(6);

        final byte[] data = rs.getBytes(7);

        final int offset =
                this.startByte > startByte ? this.startByte - startByte : 0;
        final int length =
                this.endByte < endByte ? this.endByte - startByte - offset
                        : endByte - startByte - offset;
        try {
          byte[] buffer = data;
          if (encType == EncodingType.GZIP) {
            buffer = GZIPUtils.unGzipBytes(data);
          }

          byteStream.write(buffer, offset, length);
        } catch (final IOException e) {
          throw new SQLException(e);
        }
      } while (rs.next());

      final byte[] buffer = byteStream.toByteArray();
      final Pair<Integer, Integer> result =
              FileIOUtils.getUtf8Range(buffer, 0, buffer.length);

      return new LogData(this.startByte + result.getFirst(), result.getSecond(),
              new String(buffer, result.getFirst(), result.getSecond(), StandardCharsets.UTF_8));
    }
  }

  private static class JobLogsOffsetHandler implements ResultSetHandler<Long> {

    private static final String FETCH_LOGS_MAX_SIZE ="SELECT MAX(e.`end_byte`) FROM execution_logs e WHERE e.exec_id=?  AND e.`name`=? AND e.attempt=? ;";

    @Override
    public Long handle(final ResultSet rs) throws SQLException {
      Long maxSize = 0L;
      if (!rs.next()) {
        return maxSize;
      }
      do {
        maxSize = rs.getLong(1);
      } while (rs.next());
      return maxSize;
    }
  }

  @Override
  public LogData fetchAllLogs(final int execId, final String name, final int attempt) throws ExecutorManagerException {

    final FetchAllLogsHandler handler = new FetchAllLogsHandler();
    try {
      final LogData result =
              this.dbOperator.query(FetchAllLogsHandler.FETCH_LOGS, handler, execId, name, attempt);
      return result;
    } catch (final SQLException e) {
      throw new ExecutorManagerException("Error fetching logs " + execId
              + " : " + name, e);
    }
  }

  private static class FetchHdfsLogPathHandler implements ResultSetHandler<String> {

    private static final String FETCH_LOGS =
            "SELECT log FROM execution_logs "
                    + "WHERE exec_id=? AND name=? AND attempt=? ";

    @Override
    public String handle(ResultSet rs) throws SQLException {
      if (!rs.next()) {
        return null;
      }

      // 获取 HDFS 路径
      Blob blob = rs.getBlob(1);
      byte[] bytes = blob.getBytes(1, (int) blob.length());

      return new String(bytes, StandardCharsets.UTF_8);
    }
  }

  private static class FetchLogEncTypeHandler implements ResultSetHandler<Integer> {

    private static final String FETCH_LOG_ENCTYPE =
            "SELECT enc_type FROM execution_logs "
                    + "WHERE exec_id=? AND name=? AND attempt=? ";

    @Override
    public Integer handle(ResultSet rs) throws SQLException {
      if (!rs.next()) {
        return null;
      }

      return rs.getInt(1);
    }
  }


  private static class FetchAllLogsHandler implements ResultSetHandler<LogData> {

    private static final String FETCH_LOGS =
            "SELECT exec_id, name, attempt, enc_type, start_byte, end_byte, log "
                    + "FROM execution_logs "
                    + "WHERE exec_id=? AND name=? AND attempt=? "
                    + "ORDER BY start_byte";

//    private final int startByte;
//    private final int endByte;
//
//    FetchAllLogsHandler(final int startByte, final int endByte) {
//      this.startByte = startByte;
//      this.endByte = endByte;
//    }

    @Override
    public LogData handle(final ResultSet rs) throws SQLException {
      if (!rs.next()) {
        return null;
      }

      final ByteArrayOutputStream byteStream = new ByteArrayOutputStream();

      do {
        // int execId = rs.getInt(1);
        // String name = rs.getString(2);
        // final int attempt = rs.getInt(3);
        final EncodingType encType = EncodingType.fromInteger(rs.getInt(4));
        final int startByte = rs.getInt(5);
        final int endByte = rs.getInt(6);

        final byte[] data = rs.getBytes(7);

        final int offset = 0;
        final int length = endByte - startByte;
        try {
          byte[] buffer = data;
          if (encType == EncodingType.GZIP) {
            buffer = GZIPUtils.unGzipBytes(data);
          }
          byteStream.write(buffer, offset, length);
        } catch (final IOException e) {
          throw new SQLException(e);
        }
      } while (rs.next());

      final byte[] buffer = byteStream.toByteArray();
      final Pair<Integer, Integer> result =
              FileIOUtils.getUtf8Range(buffer, 0, buffer.length);

      return new LogData(result.getFirst(), result.getSecond(),
              new String(buffer, result.getFirst(), result.getSecond(), StandardCharsets.UTF_8));
    }
  }

}
