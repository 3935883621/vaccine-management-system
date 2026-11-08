package com.vaccine.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDate;

/**
 * 生产批次表（批次生产记录，归属确定的生产商+疫苗组合）
 */
@Data
@TableName("batch")
public class Batch {

    @TableId(type = IdType.AUTO)
    private Long batchId;

    private Long manufacturerId;

    private Long vaccineId;

    private String batchNo;

    private LocalDate productionDate;

    /** 有效期至 */
    private LocalDate expiryDate;

    private Integer productionQuantity;
}
