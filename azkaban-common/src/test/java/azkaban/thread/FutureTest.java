package azkaban.thread;

import org.junit.Before;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

/**
 * @author georgeqiao
 * @Description:
 */
public class FutureTest {
    Logger logger = LoggerFactory.getLogger(FutureTest.class);
    ExecutorService executorInfoRefresherService = null;

    @Before
    public void before() {
        this.executorInfoRefresherService = createExecutorInfoRefresherService();
    }

    @Test
    public void testGet() throws Exception {
        final List<Future> futures = new ArrayList<>();
        final Future future1 = executorInfoRefresherService.submit(() -> {
            sleep(10000);
            return 1;
        });
        futures.add(future1);

        final Future future2 = executorInfoRefresherService.submit(() -> {
            sleep(1000);
            return 2;
        });
        futures.add(future2);

        final Future future3 = executorInfoRefresherService.submit(() -> {
            sleep(1000);
            return 3;
        });
        futures.add(future3);


        for (Future future : futures) {
            try {
                // max 5 secs
                System.out.println(Thread.currentThread().getName() + "::value1" + future.get(5, TimeUnit.SECONDS));
            } catch (final Exception e) {
                e.printStackTrace();
            }

        }
    }

    private ExecutorService createExecutorInfoRefresherService() {
        return Executors.newFixedThreadPool(1);
    }

    private void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }
}