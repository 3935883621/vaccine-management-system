package com.vaccine.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Data;

import java.time.LocalDate;

/**
 * 用户表（居民账号与信息）
 */
@Data
@TableName("`user`")
public class User {

    @TableId(type = IdType.AUTO)
    private Long userId;

    private String username;

    /** BCrypt 密码密文，不参与 JSON 序列化 */
    @JsonIgnore
    private String password;

    private String realName;

    private String idCard;

    private String phone;

    /** 性别：1=男 2=女 */
    private Integer gender;

    private LocalDate birthDate;
}
