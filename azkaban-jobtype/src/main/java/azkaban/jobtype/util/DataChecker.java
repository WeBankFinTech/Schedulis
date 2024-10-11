package azkaban.jobtype.util;

import azkaban.jobtype.connectors.druid.WBDataCheckerDao;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Properties;

public class DataChecker {
	public final static String SOURCE_TYPE = "source.type";
	public final static String DATA_OBJECT = "data.object";
	public final static String DATA_OBJECT_SUBFFIX = "data.object.";
	public final static String WAIT_TIME = "wait.time";
	public final static String QUERY_FREQUENCY = "query.frequency";
	public final static String TIME_SCAPE = "time.scape";
	public final static String MASK_URL = "bdp.mask.url";
	public final static String MASK_APP_ID = "bdp.mask.app.id";
	public final static String MASK_APP_TOKEN = "bdp.mask.app.token";
	public final static String NAME_IGNOORE_CASE = "name.ignore.case";
	public final static String QUALITIS_CHECK = "qualitis.check";
	public final static String EARLIEST_FINISH_TIME = "earliest.finish.time";
	public final static String EARLIEST_FINISH_TIME_CROSS_DAY = "earliest.finish.time.cross.day";

	public final static String PRIORITY = "checker.priority";

	private Properties p;
	private static final Logger logger = LoggerFactory.getLogger(DataChecker.class);

	public DataChecker(String jobName, Properties p) {
		this.p = p;
	}

	public void run() {
		if(p == null) {
			throw new RuntimeException("Properties is null. Can't continue");
		}
		if (!p.containsKey(SOURCE_TYPE)) {
//		    throw new RuntimeException("Must specify a " + SOURCE_TYPE
//		          + " key and value.");
			logger.info("Properties "  + SOURCE_TYPE + " value is Null !");
		}
		if (!p.containsKey(DATA_OBJECT)) {
//			throw new RuntimeException("Must specify a " + DATA_OBJECT
//			          + " key and value.");
			logger.info("Properties " + DATA_OBJECT + " value is Null !");
		}
		WBDataCheckerDao wbDao = WBDataCheckerDao.getInstance();//微众数据源检查Dao

		boolean success = wbDao.validateTableStatusFunction(p, logger);
		if(!success) {
			throw new RuntimeException("Data not found.");
		}

	}

	public void cancel() throws Exception {
		WBDataCheckerDao.closeDruidDataSource();
		throw new RuntimeException("Kill this datachecker.");
	}

}
