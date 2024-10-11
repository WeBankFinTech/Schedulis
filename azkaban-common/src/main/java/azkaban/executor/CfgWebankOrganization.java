package azkaban.executor;

public class CfgWebankOrganization {

    private Integer dpId;

    private Integer pid;

    private String dpName;

    private String dpChName;

    private Integer orgId;

    private String orgName;

    private String division;

    private Integer groupId;

    private Integer uploadFlag;

    public CfgWebankOrganization(Integer dpId, Integer pid, String dpName, String dpChName, Integer orgId, String orgName, String division, Integer groupId, Integer uploadFlag) {
        this.dpId = dpId;
        this.pid = pid;
        this.dpName = dpName;
        this.dpChName = dpChName;
        this.orgId = orgId;
        this.orgName = orgName;
        this.division = division;
        this.groupId = groupId;
        this.uploadFlag = uploadFlag;
    }

    public CfgWebankOrganization(){
    }

    public Integer getDpId() {
        return dpId;
    }

    public void setDpId(Integer dpId) {
        this.dpId = dpId;
    }

    public Integer getPid() {
        return pid;
    }

    public void setPid(Integer pid) {
        this.pid = pid;
    }

    public String getDpName() {
        return dpName;
    }

    public void setDpName(String dpName) {
        this.dpName = dpName;
    }

    public String getDpChName() {
        return dpChName;
    }

    public void setDpChName(String dpChName) {
        this.dpChName = dpChName;
    }

    public Integer getOrgId() {
        return orgId;
    }

    public void setOrgId(Integer orgId) {
        this.orgId = orgId;
    }

    public String getOrgName() {
        return orgName;
    }

    public void setOrgName(String orgName) {
        this.orgName = orgName;
    }

    public String getDivision() {
        return division;
    }

    public void setDivision(String division) {
        this.division = division;
    }

    public Integer getGroupId() {
        return groupId;
    }

    public void setGroupId(Integer groupId) {
        this.groupId = groupId;
    }

    public Integer getUploadFlag() {
        return uploadFlag;
    }

    public void setUploadFlag(Integer uploadFlag) {
        this.uploadFlag = uploadFlag;
    }

    @Override
    public String toString() {
        return "Department{" +
                "dpId=" + dpId +
                ", pid=" + pid +
                ", dpName='" + dpName + '\'' +
                ", dpChName='" + dpChName + '\'' +
                ", orgId=" + orgId +
                ", orgName='" + orgName + '\'' +
                ", division='" + division + '\'' +
                ", groupId=" + groupId +
                ", uploadFlag=" + uploadFlag +
                '}';
    }
}
