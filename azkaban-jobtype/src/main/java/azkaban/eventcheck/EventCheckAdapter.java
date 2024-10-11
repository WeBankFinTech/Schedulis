package azkaban.eventcheck;

import org.slf4j.Logger;

import java.util.Properties;

/**
 * @author georgeqiao
 * @Title: EventCheckReceiver
 * @ProjectName WTSS
 * @date 2019/9/1822:10
 * @Description: TODO
 */
public interface EventCheckAdapter {

    boolean sendMsg(int jobId, Properties props, Logger log);

    boolean reciveMsg(int jobId, Properties props, Logger log);

}
