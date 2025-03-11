package azkaban.system.dto;

import com.alibaba.excel.annotation.ExcelProperty;
import com.alibaba.excel.metadata.BaseRowModel;

/**
 * 变更的系统用户数据传输对象
 * Created by zhangxi on 8/8/19
 */
public class ModifyWtssUserDto extends BaseRowModel {

    /**
     * 用户临时id, 用于下载产生的编号标记
     */
    @ExcelProperty(value = "编号" ,index = 0)
    private Integer excelId;

    /**
     * 用户ID
     */
    @ExcelProperty(value = "用户ID" ,index = 1)
    private String userId;

    /**
     * 用户全名
     */
    @ExcelProperty(value = "用户全名" ,index = 2)
    private String fullName;

    /**
     * 用户部门名称
     */
    @ExcelProperty(value = "用户部门" ,index = 3)
    private String departmentName;

    /**
     * 代理用户
     */
    @ExcelProperty(value = "代理用户" ,index = 4)
    private String proxyUsers;

    /**
     * 用户角色名称
     */
    @ExcelProperty(value = "用户角色" ,index = 5)
    private String roleName;

    /**
     * 用户权限
     */
    @ExcelProperty(value = "用户权限" ,index = 6)
    private String rightAccess;

    /**
     * 电子邮箱
     */
    @ExcelProperty(value = "用户邮箱" ,index = 7)
    private String email;


    /**
     * 用户变更类型
     */
    @ExcelProperty(value = "变更类型" ,index = 8)
    private String modifyType;

    public ModifyWtssUserDto(){}

    public Integer getExcelId() {
        return excelId;
    }

    public void setExcelId(Integer excelId) {
        this.excelId = excelId;
    }

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public String getFullName() {
        return fullName;
    }

    public void setFullName(String fullName) {
        this.fullName = fullName;
    }

    public String getDepartmentName() {
        return departmentName;
    }

    public void setDepartmentName(String departmentName) {
        this.departmentName = departmentName;
    }

    public String getProxyUsers() {
        return proxyUsers;
    }

    public void setProxyUsers(String proxyUsers) {
        this.proxyUsers = proxyUsers;
    }

    public String getRoleName() {
        return roleName;
    }

    public void setRoleName(String roleName) {
        this.roleName = roleName;
    }

    public String getRightAccess() {
        return rightAccess;
    }

    public void setRightAccess(String rightAccess) {
        this.rightAccess = rightAccess;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getModifyType() {
        return modifyType;
    }

    public void setModifyType(String modifyType) {
        this.modifyType = modifyType;
    }
}
