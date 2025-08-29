package azkaban.hookExecutor;


import org.apache.commons.lang3.StringUtils;

public enum HookCheckType {

    ALL("all", "都执行"),
    SUCCESS("success", "成功状态下执行"),
    FAIL("fail", "失败状态下执行"),
    ;
    private String type;
    private String desc;


    HookCheckType(String type, String desc) {
        this.type = type;
        this.desc = desc;
    }

    public String getType() {
        return type;
    }

    public String getDesc() {
        return desc;
    }

    public static boolean isAll(String type) {
        return ALL.getType().equalsIgnoreCase(type);
    }

    public static boolean isFail(String type) {
        return FAIL.getType().equalsIgnoreCase(type);
    }

    public static boolean isSuccess(String type) {
        return SUCCESS.getType().equalsIgnoreCase(type);
    }

    public static boolean isNotSuccess(String type){
        return StringUtils.equalsAnyIgnoreCase(type,ALL.getType(),FAIL.getType());
    }

    public static boolean isAny(String type){
        return StringUtils.equalsAnyIgnoreCase(type,SUCCESS.getType(),FAIL.getType());
    }
}
