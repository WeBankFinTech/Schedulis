package azkaban.jobtype.util;

import azkaban.jobtype.connectors.druid.WBDataCheckerDao;
import java.util.Properties;
import java.util.Random;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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

    public final static String DC_WAIT_ENABLED = "wtss.datachecker.random.wiat.enabled";

    public final static String DC_WAIT_BASE_TIME = "wtss.datachecker.random.wait.base.time";

    public static final String DC_SQL_EXCEPTION_MAX_RETRIES = "datachecker.sql.exception.max.retries";
    public static final String DC_SQL_EXCEPTION_RETRY_DELAY_MS = "datachecker.sql.exception.retry.delay.ms";
    public static final String DC_SQL_EXCEPTION_KEYWORDS = "datachecker.sql.exception.keywords";

    public static final String DC_HOURLY_SECONDARY_PARTITION = "hourly.secondary.partition";

    /**
     * 是否跳过 DOPS 检查
     */
    public static final String DC_IGNORE_DOPS_CHECK = "ignore.dops.check";

    public static final String DC_RANGE_INTERVAL = "range.interval";

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
			logger.info("Properties "  + SOURCE_TYPE + " value is Null !");
		}
		if (!p.containsKey(DATA_OBJECT)) {
			logger.info("Properties " + DATA_OBJECT + " value is Null !");
		}
        WBDataCheckerDao wbDao = WBDataCheckerDao.getInstance();

		boolean success = wbDao.validateTableStatusFunction(p, logger);
		if(!success) {
			throw new RuntimeException("Data not found.");
        } else {
            String dcWaitEnabled = p.getProperty(DC_WAIT_ENABLED, "false");
            if ("true".equalsIgnoreCase(dcWaitEnabled)) {
                try {
                    String baseTimeStr = p.getProperty(DC_WAIT_BASE_TIME, "30");
                    // 创建一个随机数生成器
                    Random random = new Random();
                    // 生成30到60之间的随机整数（包括30和60）
                    int sleepTime = random.nextInt(31) + Integer.parseInt(baseTimeStr);
                    TimeUnit.SECONDS.sleep(sleepTime);
                } catch (Exception e) {
                    logger.info("InterruptedException occurred while waiting for 60 seconds", e);
                }
		}
        }
	}

	public void cancel() throws Exception {
		WBDataCheckerDao.closeDruidDataSource();
		throw new RuntimeException("Kill this datachecker.");
	}

}
