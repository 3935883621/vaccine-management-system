package com.vaccine.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

/**
 * 护士表
 */
@Data
@TableName("nurse")
public class Nurse {

    @TableId(type = IdType.AUTO)
    private Long nurseId;

    private String name;

    private String employeeNo;

    private String title;

    private Long institutionId;

    private String phone;
}
