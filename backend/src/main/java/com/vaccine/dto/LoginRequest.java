package com.vaccine.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 登录请求（4 角色统一入口）
 */
@Data
public class LoginRequest {

    @NotBlank(message = "用户名不能为空")
    private String username;

    @NotBlank(message = "密码不能为空")
    private String password;

    /** 角色：USER / INSTITUTION_ADMIN / MANUFACTURER / SUPER_ADMIN */
    @NotBlank(message = "角色不能为空")
    private String role;
}
