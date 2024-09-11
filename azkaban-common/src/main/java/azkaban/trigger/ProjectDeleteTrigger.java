package azkaban.trigger;

import azkaban.project.ProjectManager;
import azkaban.utils.Props;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import javax.inject.Singleton;

@Singleton
public class ProjectDeleteTrigger extends Thread {

  private final Logger logger = LoggerFactory.getLogger(ProjectDeleteTrigger.class);

  private final String DELETE_INACTIVE_PROJECT_INTERVAL_DAY = "delete.inactive.project.interval.day";
  private final String DELETE_INACTIVE_PROJECT_WAIT_TIME = "delete.inactive.project.wait.time";


  //默认保留30天
  private final long INTERVAL_DAY = 30L;

  private final long WAIT_TIME = 24 * 3600 * 1000L;

  private ProjectManager projectManager;

  private Props props;

  private long intervalTime;

  private long waitTime;

  private volatile boolean shutdown = false;

  @Inject
  public ProjectDeleteTrigger(ProjectManager projectManager, Props props) {
    this.projectManager = projectManager;
    this.props = props;
    this.init();
  }

  private void init(){
    long day = INTERVAL_DAY;
    try {
      day = this.props.getLong(DELETE_INACTIVE_PROJECT_INTERVAL_DAY, INTERVAL_DAY);
    } catch (NumberFormatException nfe){
      logger.warn("parse delete.inactive.project.interval.day failed.", nfe);
    }
    this.intervalTime = System.currentTimeMillis() - day * 24 * 3600 * 1000;
    long wt = WAIT_TIME;
    try {
      wt = this.props.getLong(DELETE_INACTIVE_PROJECT_WAIT_TIME, WAIT_TIME);
    } catch (NumberFormatException nfe){
      logger.warn("parse delete.inactive.project.wait.time failed.", nfe);
    }
    this.waitTime = wt;
  }

  public void shutdown() {
    this.shutdown = true;
    this.interrupt();
  }

  @Override
  public void run() {
    while(!this.shutdown) {
      synchronized (this) {
        try {
          logger.info("delete historical project.");
          projectManager.deleteInactiveProjectByTime(this.intervalTime);
          wait(waitTime);
        } catch (Exception e){
          logger.warn("trigger has been interrupted.", e);
          break;
        }
      }
    }
  }
}
