package azkaban.distributelock;

import java.util.Date;

/**
 * @author georgeqiao
 * @Title: DistributeLock
 * @ProjectName WTSS
 * @date 2019/11/1220:09
 * @Description: TODO
 */
public class DistributeLock {
    private int id;
    private String request_id;
    private String lock_resource;
    private long lock_count;
    private int version;
    private String ip;
    private long timeout;
    private long create_time;
    private long update_time;
    private boolean isValid;

    public DistributeLock(String request_id, String lock_resource, long lock_count, int version, String ip, long timeout, long create_time, long update_time) {
        this.request_id = request_id;
        this.lock_resource = lock_resource;
        this.lock_count = lock_count;
        this.version = version;
        this.ip = ip;
        this.timeout = timeout;
        this.create_time = create_time;
        this.update_time = update_time;
    }

    public DistributeLock(int id, String request_id, String lock_resource, long lock_count, int version, String ip, long timeout, long create_time, long update_time) {
        this.id = id;
        this.request_id = request_id;
        this.lock_resource = lock_resource;
        this.lock_count = lock_count;
        this.version = version;
        this.ip = ip;
        this.timeout = timeout;
        this.create_time = create_time;
        this.update_time = update_time;
    }

    public int getId() {
        return this.id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public String getRequest_id() {
        return this.request_id;
    }

    public void setRequest_id(String request_id) {
        this.request_id = request_id;
    }

    public String getLock_resource() {
        return this.lock_resource;
    }

    public void setLock_resource(String lock_resource) {
        this.lock_resource = lock_resource;
    }

    public long getLock_count() {
        return this.lock_count;
    }

    public void setLock_count(long lock_count) {
        this.lock_count = lock_count;
    }

    public int getVersion() {
        return this.version;
    }

    public void setVersion(int version) {
        this.version = version;
    }

    public String getIp() {
        return this.ip;
    }

    public void setIp(String ip) {
        this.ip = ip;
    }

    public long getTimeout() {
        return this.timeout;
    }

    public void setTimeout(long timeout) {
        this.timeout = timeout;
    }

    public long getCreate_time() {
        return this.create_time;
    }

    public void setCreate_time(long create_time) {
        this.create_time = create_time;
    }

    public long getUpdate_time() {
        return this.update_time;
    }

    public void setUpdate_time(long update_time) {
        this.update_time = update_time;
    }

    @Override
    public String toString() {
        return "DistributeLock{" +
                "id=" + id +
                ", request_id='" + request_id + '\'' +
                ", lock_resource='" + lock_resource + '\'' +
                ", lock_count=" + lock_count +
                ", version=" + version +
                ", ip='" + ip + '\'' +
                ", timeout=" + new Date(timeout) +
                ", create_time=" + new Date(create_time) +
                ", update_time=" + new Date(update_time) +
                '}';
    }
}