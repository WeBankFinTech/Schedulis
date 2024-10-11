package azkaban.executor;

public class Hosts {
    private String hostname;
    private int executorid;
    private String creator;
    private Long createtime;
    private String updater;

    public Hosts() {}

    public Hosts(String hostname, int executorid, String creator, Long createtime, String updater) {
        this.hostname = hostname;
        this.executorid = executorid;
        this.creator = creator;
        this.createtime = createtime;
        this.updater = updater;
    }

    public String getHostname() { return hostname; }

    public void setHostname(String hostname) { this.hostname = hostname; }

    public int getExecutorid() { return executorid; }

    public void setExecutorid(int executorid) { this.executorid = executorid; }

    public String getCreator() { return creator; }

    public void setCreator(String creator) { this.creator = creator; }

    public Long getCreatetime() { return createtime; }

    public void setCreatetime(Long createtime) { this.createtime = createtime; }

    public String getUpdater() { return updater; }

    public void setUpdater(String updater) { this.updater = updater; }
}
