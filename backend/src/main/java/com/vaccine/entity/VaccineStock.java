package com.vaccine.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDate;

/**
 * 库存表（机构疫苗库存，按批次）
 */
@Data
@TableName("vaccine_stock")
public class VaccineStock {

    @TableId(type = IdType.AUTO)
    private Long stockId;

    private Long institutionId;

    private Long supplyId;

    private Long batchId;

    private Long vaccineId;

    private Long manufacturerId;

    /** 批次号（由 batch 带入） */
    private String batchNo;

    /** 可用数量 */
    private Integer quantity;

    /** 已锁定数量 */
    private Integer lockedQuantity;

    /** 生产日期（由 batch 带入） */
    private LocalDate productionDate;

    /** 有效期至（由 batch 带入） */
    private LocalDate expiryDate;

    /** 状态：1=正常 2=临期预警 3=已过期锁定 4=已销毁 */
    private Integer status;
}
