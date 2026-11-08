package com.vaccine.security;

import lombok.AllArgsConstructor;
import lombok.Data;

/**
 * 当前登录人上下文（ThreadLocal，由 AuthInterceptor 写入/清理）
 */
public final class UserContext {

    private static final ThreadLocal<LoginPrincipal> HOLDER = new ThreadLocal<>();

    private UserContext() {
    }

    public static void set(LoginPrincipal principal) {
        HOLDER.set(principal);
    }

    public static LoginPrincipal get() {
        return HOLDER.get();
    }

    public static Long userId() {
        LoginPrincipal p = HOLDER.get();
        return p == null ? null : p.getUserId();
    }

    public static Role role() {
        LoginPrincipal p = HOLDER.get();
        return p == null ? null : p.getRole();
    }

    public static void clear() {
        HOLDER.remove();
    }

    @Data
    @AllArgsConstructor
    public static class LoginPrincipal {
        /** 对应账号表的主键 ID */
        private Long userId;
        private Role role;
    }
}
