package com.vaccine.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.time.LocalDate;

/**
 * 居民自助注册请求
 */
@Data
public class RegisterUserRequest {

    @NotBlank(message = "用户名不能为空")
    private String username;

    @NotBlank(message = "密码不能为空")
    private String password;

    @NotBlank(message = "真实姓名不能为空")
    private String realName;

    @NotBlank(message = "身份证号不能为空")
    private String idCard;

    private String phone;

    /** 性别：1=男 2=女 */
    private Integer gender;

    private LocalDate birthDate;
}
