package com.vaccine.controller;

import com.vaccine.common.Result;
import com.vaccine.dto.LoginRequest;
import com.vaccine.dto.LoginResponse;
import com.vaccine.dto.RegisterUserRequest;
import com.vaccine.entity.User;
import com.vaccine.security.UserContext;
import com.vaccine.service.AuthService;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 账号接口：登录、居民注册、当前登录人
 */
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    /** 4 角色统一登录入口 */
    @PostMapping("/login")
    public Result<LoginResponse> login(@Validated @RequestBody LoginRequest request) {
        return Result.ok(authService.login(request));
    }

    /** 居民自助注册（密码 BCrypt 加密入库） */
    @PostMapping("/register")
    public Result<User> register(@Validated @RequestBody RegisterUserRequest request) {
        return Result.ok(authService.register(request));
    }

    /** 当前登录人信息（4 角色通用） */
    @GetMapping("/me")
    public Result<Object> me() {
        UserContext.LoginPrincipal principal = UserContext.get();
        return Result.ok(authService.me(principal.getRole(), principal.getUserId()));
    }
}
