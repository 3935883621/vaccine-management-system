package com.vaccine.dto;

import lombok.Builder;
import lombok.Data;

/**
 * 登录成功响应
 */
@Data
@Builder
public class LoginResponse {

    private String token;

    /** 角色：USER / INSTITUTION_ADMIN / MANUFACTURER / SUPER_ADMIN */
    private String role;

    /** 对应账号表的主键 ID */
    private Long principalId;

    private String username;

    private String realName;

    /** 机构管理员所属机构 ID（其他角色为 null） */
    private Long institutionId;
}
