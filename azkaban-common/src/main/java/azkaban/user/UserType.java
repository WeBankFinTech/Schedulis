package azkaban.user;

public enum UserType {
    OPS("ops"),
    SYSTEM("system"),
    PERSONAL("personal");

    private String type;

    UserType(String type) {
        this.type = type;
    }

    public String getType() {
        return type;
    }
}
