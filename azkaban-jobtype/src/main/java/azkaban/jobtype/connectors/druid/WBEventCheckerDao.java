package azkaban.jobtype.connectors.druid;

import azkaban.eventcheck.DefaultEventcheckReceiver;
import azkaban.eventcheck.EventCheckSender;
import org.slf4j.Logger;

import java.util.Properties;


public class WBEventCheckerDao {
    private static WBEventCheckerDao instance;

    /*public static WBEventCheckerDao getInstance() {
        if (instance == null) {
            synchronized (WBEventCheckerDao.class) {
                if (instance == null) {
                    instance = new WBEventCheckerDao();
                }
            }
        }
        return instance;
    }*/
    public static synchronized WBEventCheckerDao getInstance() {
        if (instance == null) {
            instance = new WBEventCheckerDao();
        }
        return instance;
    }

    public boolean sendMsg(int jobId, Properties props, Logger log) {
        if(props!=null){
            return new EventCheckSender(props).sendMsg(jobId,props,log);
        }else{
            log.error("create EventCheckSender failed {}");
            return false;
        }
    }

    /**
     * 接收消息 接收消息先查询消费记录，有则从上一次消费后开始消费，没有则从任务启动时间点后开始消费。
     * 接收消息是以主动查询的方式进行的，在没有超出设定目标的时间内，反复查询目标消息。
     * Receiving a message first queries the consumption record,
     * and then starts to consume after the last consumption, and no consumption
     * starts after the job starts. The received message is performed in an active
     * query manner, and the target message is repeatedly queried within a time period
     * when the set target is not exceeded.
     */
    public boolean reciveMsg(int jobId, Properties props, Logger log) {
        if(props!=null){
            return new DefaultEventcheckReceiver(props).reciveMsg(jobId,props,log);
        }else{
            log.error("create EventCheckSender failed {}");
            return false;
        }
    }

}
