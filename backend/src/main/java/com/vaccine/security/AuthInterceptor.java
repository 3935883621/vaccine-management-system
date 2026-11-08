package com.vaccine.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.vaccine.common.Result;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * 认证 + 授权拦截器：
 * 1. 认证：解析 Authorization: Bearer <token>，失败返回 401；
 * 2. 授权：校验方法/类上的 @RequireRole，角色不匹配返回 403。
 */
@Component
@RequiredArgsConstructor
public class AuthInterceptor implements HandlerInterceptor {

    private static final String BEARER_PREFIX = "Bearer ";

    private final JwtUtil jwtUtil;
    private final ObjectMapper objectMapper;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler)
            throws Exception {
        if (HttpMethod.OPTIONS.matches(request.getMethod())) {
            return true;
        }
        if (!(handler instanceof HandlerMethod)) {
            return true;
        }

        // 1. 认证：解析并校验 JWT
        String header = request.getHeader("Authorization");
        if (header == null || !header.startsWith(BEARER_PREFIX)) {
            return reject(response, 401, "未登录或登录已失效");
        }
        Claims claims;
        try {
            claims = jwtUtil.parseToken(header.substring(BEARER_PREFIX.length()));
        } catch (JwtException | IllegalArgumentException e) {
            return reject(response, 401, "未登录或登录已失效");
        }
        Long userId = Long.valueOf(claims.getSubject());
        Role role = Role.valueOf(claims.get("role", String.class));
        UserContext.set(new UserContext.LoginPrincipal(userId, role));

        // 2. 授权：校验 @RequireRole（方法级优先于类级）
        HandlerMethod handlerMethod = (HandlerMethod) handler;
        RequireRole requireRole = handlerMethod.getMethodAnnotation(RequireRole.class);
        if (requireRole == null) {
            requireRole = handlerMethod.getBeanType().getAnnotation(RequireRole.class);
        }
        if (requireRole != null) {
            boolean allowed = false;
            for (Role allowedRole : requireRole.value()) {
                if (allowedRole == role) {
                    allowed = true;
                    break;
                }
            }
            if (!allowed) {
                return reject(response, 403, "无权限访问该接口");
            }
        }
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler,
                                Exception ex) {
        UserContext.clear();
    }

    private boolean reject(HttpServletResponse response, int status, String message) throws Exception {
        response.setStatus(status);
        response.setContentType("application/json;charset=UTF-8");
        response.getWriter().write(objectMapper.writeValueAsString(Result.fail(status, message)));
        return false;
    }
}
