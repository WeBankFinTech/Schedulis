package azkaban.jobtype;

import com.webank.wedatasphere.schedulis.jobtype.HadoopJobUtils;
import com.webank.wedatasphere.schedulis.jobtype.SparkJobArg;
import java.io.File;
import java.io.IOException;

import org.apache.commons.io.FileUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.junit.Before;
import org.junit.Test;

import azkaban.utils.Props;

public class TestHadoopJobUtilsResolveJarSpec {
  Props jobProps = null;

  private static final Logger logger = LoggerFactory.getLogger(TestHadoopJobUtilsResolveJarSpec.class);

  String workingDirString = "/tmp/TestHadoopSpark";

  File workingDirFile = new File(workingDirString);

  File libFolderFile = new File(workingDirFile, "lib");

  String executionJarName = "hadoop-spark-job-test-execution-x.y.z-a.b.c.jar";

  File executionJarFile = new File(libFolderFile, "hadoop-spark-job-test-execution-x.y.z-a.b.c.jar");

  File libraryJarFile = new File(libFolderFile, "library.jar");

  String delim = SparkJobArg.delimiter;

  @Before
  public void beforeMethod() throws IOException {
    if (workingDirFile.exists())
      FileUtils.deleteDirectory(workingDirFile);
    workingDirFile.mkdirs();
    libFolderFile.mkdirs();
    executionJarFile.createNewFile();
    libraryJarFile.createNewFile();

  }

  // nothing should happen
  @Test(expected = IllegalStateException.class)
  public void testJarDoesNotExist() throws IOException {
    HadoopJobUtils.resolveExecutionJarName(workingDirString, "./lib/abc.jar", logger);
  }

  @Test(expected = IllegalStateException.class)
  public void testNoLibFolder() throws IOException {
    FileUtils.deleteDirectory(libFolderFile);
    HadoopJobUtils.resolveExecutionJarName(workingDirString, "./lib/abc.jar", logger);
  }
}
