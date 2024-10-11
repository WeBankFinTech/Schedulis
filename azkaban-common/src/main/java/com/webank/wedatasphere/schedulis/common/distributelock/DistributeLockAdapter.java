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

package com.webank.wedatasphere.schedulis.common.distributelock;

/**
 * @author georgeqiao
 * @Title: DistributeLockAdapter
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
