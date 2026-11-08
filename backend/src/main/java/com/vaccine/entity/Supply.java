package com.vaccine.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDate;

/**
 * 供货记录表（生产商向机构供货，机构凭此入库）
 */
@Data
@TableName("supply")
public class Supply {

    @TableId(type = IdType.AUTO)
    private Long supplyId;

    private Long batchId;

    /** 目标机构ID */
    private Long institutionId;

    /** 配送数量 */
    private Integer quantity;

    /** 已入库数量（<=quantity） */
    private Integer receivedQuantity;

    /** 配送日期 */
    private LocalDate supplyDate;

    /** 状态：1=待入库 2=已入库 */
    private Integer status;
}
