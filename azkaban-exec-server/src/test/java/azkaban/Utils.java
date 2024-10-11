package azkaban;

import azkaban.db.AzkabanDataSource;
import azkaban.db.DatabaseOperator;
import azkaban.db.DatabaseSetup;
import com.google.inject.AbstractModule;
import com.google.inject.Guice;
import com.google.inject.Injector;
import org.apache.commons.dbutils.QueryRunner;

import java.io.File;

import static azkaban.ServiceProvider.SERVICE_PROVIDER;


public class Utils {

  public static void initServiceProvider() {
    final Injector injector = Guice.createInjector(new AbstractModule() {
      @Override
      protected void configure() {
      }
    });
    // Because SERVICE_PROVIDER is a singleton and it is shared among many tests,
    // need to reset the state to avoid assertion failures.
    SERVICE_PROVIDER.unsetInjector();

    SERVICE_PROVIDER.setInjector(injector);
  }

  public static DatabaseOperator initTestDB() throws Exception {
    final AzkabanDataSource dataSource = new AzDBTestUtility.EmbeddedH2BasicDataSource();

    final String sqlScriptsDir = new File("../azkaban-db/src/main/sql/").getCanonicalPath();
    final DatabaseSetup setup = new DatabaseSetup(dataSource, sqlScriptsDir);
    setup.updateDatabase();

    return new DatabaseOperator(new QueryRunner(dataSource));
  }

}
