package com.vaccine.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 超级管理员注册生产商请求（含登录账号）
 */
@Data
public class RegisterManufacturerRequest {

    @NotBlank(message = "生产商名称不能为空")
    private String name;

    @NotBlank(message = "生产许可证号不能为空")
    private String licenseNo;

    private String contactPhone;

    private String address;

    @NotBlank(message = "登录用户名不能为空")
    private String username;

    @NotBlank(message = "登录密码不能为空")
    private String password;
}
