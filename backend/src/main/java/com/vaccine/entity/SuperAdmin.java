package com.vaccine.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Data;

/**
 * 超级管理员表（系统后台账号）
 */
@Data
@TableName("super_admin")
public class SuperAdmin {

    @TableId(type = IdType.AUTO)
    private Long superAdminId;

    private String username;

    /** BCrypt 密码密文，不参与 JSON 序列化 */
    @JsonIgnore
    private String password;

    private String realName;
}
