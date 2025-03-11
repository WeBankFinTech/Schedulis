package azkaban.system.entity;

import java.util.ArrayList;
import java.util.List;

/**
 * 部门运维人员录入表实体
 */
public class DepartmentMaintainer {

    private Integer departmentId;

    private String departmentName;

    private String opsUser;

    private List<String> opsUsers = new ArrayList<>();

    public DepartmentMaintainer() {
    }

    public DepartmentMaintainer(Integer departmentId, String departmentName, String opsUser) {
        this.departmentId = departmentId;
        this.departmentName = departmentName;
        this.opsUser = opsUser;
    }

    public Integer getDepartmentId() {
        return departmentId;
    }

    public void setDepartmentId(Integer departmentId) {
        this.departmentId = departmentId;
    }

    public String getDepartmentName() {
        return departmentName;
    }

    public void setDepartmentName(String departmentName) {
        this.departmentName = departmentName;
    }

    public String getOpsUser() {
        return opsUser;
    }

    public void setOpsUser(String opsUser) {
        this.opsUser = opsUser;
    }

    public List<String> getOpsUsers() {
        return opsUsers;
    }

    public void setOpsUsers(List<String> opsUsers) {
        this.opsUsers = opsUsers;
    }

    @Override
    public String toString() {
        return "DepartmentMaintainer{" +
                "departmentId=" + departmentId +
                ", departmentName='" + departmentName + '\'' +
                ", opsUser='" + opsUser + '\'' +
                '}';
    }
}
