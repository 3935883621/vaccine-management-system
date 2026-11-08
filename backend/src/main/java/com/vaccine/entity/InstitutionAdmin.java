package com.vaccine.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Data;

/**
 * 机构管理员表（机构管理员账号）
 */
@Data
@TableName("institution_admin")
public class InstitutionAdmin {

    @TableId(type = IdType.AUTO)
    private Long adminId;

    private String username;

    /** BCrypt 密码密文，不参与 JSON 序列化 */
    @JsonIgnore
    private String password;

    private String realName;

    private Long institutionId;

    private String phone;
}
