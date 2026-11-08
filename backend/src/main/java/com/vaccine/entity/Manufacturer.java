package com.vaccine.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Data;

/**
 * 生产商表（信息与登录账号）
 */
@Data
@TableName("manufacturer")
public class Manufacturer {

    @TableId(type = IdType.AUTO)
    private Long manufacturerId;

    private String name;

    private String licenseNo;

    private String contactPhone;

    private String address;

    private String username;

    /** BCrypt 密码密文，不参与 JSON 序列化 */
    @JsonIgnore
    private String password;
}
