package azkaban.db;

import org.apache.commons.dbutils.QueryRunner;
import org.apache.commons.dbutils.ResultSetHandler;
import org.junit.Before;
import org.junit.Test;

import java.sql.Connection;
import java.util.ArrayList;
import java.util.List;

import com.webank.wedatasphere.schedulis.common.distributelock.DBTableDistributeLock;

import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

/**
 * @author georgeqiao
 * @Title: DistributeLockTest
 * @date 2019/11/1520:12
 * @Description: TODO
 */
public class DistributeLockTest {
    private static final List<Integer> list = new ArrayList<>();
    private static final int index_2 = 15;
    private static int index_1 = 3;
    private final AzkabanDataSource datasource = new AzDBTestUtility.EmbeddedMysqlDataSource();
    private final ResultSetHandler<Integer> handler = rs -> {
        if (!rs.next()) {
            return 0;
        }
        return rs.getInt(1);
    };
    private DatabaseOperator dbOperator;
    private QueryRunner queryRunner;
    private Connection conn;


    @Before
    public void setUp() throws Exception {
//        this.queryRunner = mock(QueryRunner.class);
        this.queryRunner = new QueryRunner(this.datasource);

        this.conn = this.datasource.getConnection();
//        final DataSource mockDataSource = mock(this.datasource.getClass());

//        when(this.queryRunner.getDataSource()).thenReturn(mockDataSource);
//        when(mockDataSource.getConnection()).thenReturn(this.conn);

        this.dbOperator = new DatabaseOperator(this.queryRunner);

        list.add(index_1);
        list.add(index_2);

    }

//    @Test
    public void testValidQuery() throws Exception {
        int ss = dbOperator.query("select * from distribute_lock where lock_resource = ?", this.handler,1);
        System.out.println("~~~~~~~~" + ss);
    }

    //测试重复获取和重复释放
//    @Test
    public void test1() {
        DBTableDistributeLock dd = new DBTableDistributeLock(dbOperator);
        String requestid = dd.getRequestId();
        String lock_key = "myflowname";
        for (int i = 0; i < 2; i++) {
            dd.lock(lock_key, 10000L, 1000);
        }
        for (int i = 0; i < 2; i++) {
            dd.unlock(lock_key);
        }
    }

    //获取之后不释放，超时之后被thread1获取
    @Test
    public void test2() throws Exception {
        DBTableDistributeLock dd = new DBTableDistributeLock(dbOperator);
        String lock_key = "queue_lock_key";
        dd.lock(lock_key, 5000L, 1000);
        Thread thread1 = new Thread(() -> {
            try {
                try {
                    dd.lock(lock_key, 5000L, 7000);
                } finally {
                    dd.unlock(lock_key);
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        });
        thread1.setName("thread1");
        thread1.start();
        thread1.join();
    }

//    @Test
    public void testtime() throws Exception {
        long time1 = System.currentTimeMillis();
        Thread.sleep(5000);
        long time2 = System.currentTimeMillis();
        System.out.println(time1 + ":" + time2);
        System.out.println(time1 - time2);
        System.out.println((time2-time1)/1000 * 60 * 60);

    }

//    @Test
    public void testThread() throws Exception {
        DBTableDistributeLock dd = new DBTableDistributeLock(dbOperator);
        for (int i = 0; i < 10; i++) {
            ThreadA thread1 = new ThreadA(dd);
            thread1.setName("thread-" + i);
            thread1.start();
        }
    }

    public class ThreadA extends Thread {
        private DBTableDistributeLock dd;

        public ThreadA(DBTableDistributeLock dd) {
            this.dd = dd;
        }

        @Override
        public void run() {
            String lock_key = "myflowname";
            dd.lock(lock_key, 5000L, 10000L);
        }
    }


}
