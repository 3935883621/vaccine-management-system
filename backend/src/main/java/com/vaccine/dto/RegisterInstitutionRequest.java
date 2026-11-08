package com.vaccine.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 超级管理员注册机构请求（同时创建机构管理员账号）
 */
@Data
public class RegisterInstitutionRequest {

    @NotBlank(message = "机构名称不能为空")
    private String name;

    private String address;

    private String contactPhone;

    private String region;

    @NotBlank(message = "管理员用户名不能为空")
    private String adminUsername;

    @NotBlank(message = "管理员密码不能为空")
    private String adminPassword;

    @NotBlank(message = "管理员姓名不能为空")
    private String adminRealName;

    private String adminPhone;
}
