package azkaban.dto;

import java.util.List;

/**
 * 权限上报DTO
 */
public class PrivilegeReportDto {
    private String userId;
    private List<Roles> roles;

    public static class Roles {
        private String roleCode;
        private String roleName;
        private String roleNameCn;
        private List<Priv> privs;
        public static class Priv {
            private String privCode;
            private String privName;
            private String privNameCn;

            public String getPrivCode() {
                return privCode;
            }

            public void setPrivCode(String privCode) {
                this.privCode = privCode;
            }

            public String getPrivName() {
                return privName;
            }

            public void setPrivName(String privName) {
                this.privName = privName;
            }

            public String getPrivNameCn() {
                return privNameCn;
            }

            public void setPrivNameCn(String privNameCn) {
                this.privNameCn = privNameCn;
            }
        }

        public String getRoleCode() {
            return roleCode;
        }

        public void setRoleCode(String roleCode) {
            this.roleCode = roleCode;
        }

        public String getRoleName() {
            return roleName;
        }

        public void setRoleName(String roleName) {
            this.roleName = roleName;
        }

        public String getRoleNameCn() {
            return roleNameCn;
        }

        public void setRoleNameCn(String roleNameCn) {
            this.roleNameCn = roleNameCn;
        }

        public List<Priv> getPrivs() {
            return privs;
        }

        public void setPrivs(List<Priv> privs) {
            this.privs = privs;
        }
    }

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public List<Roles> getRoles() {
        return roles;
    }

    public void setRoles(List<Roles> roles) {
        this.roles = roles;
    }
}
