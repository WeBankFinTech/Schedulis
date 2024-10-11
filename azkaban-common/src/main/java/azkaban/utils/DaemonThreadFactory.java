package azkaban.utils;

import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * A thread factory that sets the threads to run as daemons. (Otherwise things that embed the thread
 * pool cannot shut themselves down)
 *
 * @author lebronwang
 * @date 2023/12/11
 **/
public class DaemonThreadFactory implements ThreadFactory {

  private final AtomicInteger threadNum;

  private final String namePrefix;


  public DaemonThreadFactory(String threadNamePrefix) {
    this.threadNum = new AtomicInteger(1);
    this.namePrefix = threadNamePrefix;
  }

  @Override
  public Thread newThread(Runnable r) {
    Thread t = new Thread(r, this.namePrefix + "-" + this.threadNum.getAndIncrement());
    t.setDaemon(true);
    return t;
  }
}
