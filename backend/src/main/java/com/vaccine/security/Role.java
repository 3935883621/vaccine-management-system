package com.vaccine.security;

/**
 * 系统角色（4 类账号）
 */
public enum Role {

    USER("居民"),
    INSTITUTION_ADMIN("机构管理员"),
    MANUFACTURER("生产商"),
    SUPER_ADMIN("超级管理员");

    private final String label;

    Role(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
