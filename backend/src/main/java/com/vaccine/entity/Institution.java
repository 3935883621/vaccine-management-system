package com.vaccine.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

/**
 * 接种机构表
 */
@Data
@TableName("institution")
public class Institution {

    @TableId(type = IdType.AUTO)
    private Long institutionId;

    private String name;

    private String address;

    private String contactPhone;

    private String region;
}
