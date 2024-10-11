package azkaban.jobid;

import java.util.Objects;

public class BDPClientJobInfo {

    String jobId;

    String proxyUrl;

    public BDPClientJobInfo(String jobId, String proxyUrl){
        this.jobId = jobId;
        this.proxyUrl = proxyUrl;
    }

    public BDPClientJobInfo(){

    }

    public String getJobId() {
        return jobId;
    }

    public void setJobId(String jobId) {
        this.jobId = jobId;
    }

    public String getProxyUrl() {
        return proxyUrl;
    }

    public void setProxyUrl(String proxyUrl) {
        this.proxyUrl = proxyUrl;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof BDPClientJobInfo)) {
            return false;
        }
        BDPClientJobInfo that = (BDPClientJobInfo) o;
        return Objects.equals(getJobId(), that.getJobId()) && Objects.equals(getProxyUrl(), that.getProxyUrl());
    }

    @Override
    public int hashCode() {
        return Objects.hash(getJobId(), getProxyUrl());
    }

    @Override
    public String toString() {
        return "BDPClientJobInfo{" +
                "jobId='" + jobId + '\'' +
                ", proxyUrl='" + proxyUrl + '\'' +
                '}';
    }
}
