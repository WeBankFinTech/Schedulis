package azkaban.distributelock;

/**
 * @author georgeqiao
 * @Title: DistributeLockAdapter
 * @ProjectName WTSS
 * @date 2019/11/1220:02
 * @Description: TODO
 */
public interface DistributeLockAdapter {

    /**
     * 获取锁
     *
     * @param lock_key        锁key
     * @param locktimeout(毫秒) 持有锁的有效时间，防止死锁
     * @param gettimeout(毫秒)  获取锁的超时时间，这个时间内获取不到将重试
     * @return
     */
    boolean lock(String lock_key, long locktimeout, long gettimeout);

    /**
     * 释放锁
     *
     * @param lock_key
     */
    void unlock(String lock_key);

    /**
     * 重置锁
     *
     * @param distributeLock
     * @return
     */
    int resetLock(DistributeLock distributeLock);

    /**
     * 更新lockModel信息，内部采用乐观锁来更新
     *
     * @param distributeLock
     * @return
     */
    int updateLock(DistributeLock distributeLock);
}
