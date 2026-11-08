package com.vaccine.vo;

import lombok.Data;

import java.time.LocalDate;

/**
 * 用户端批次余量视图（机构某疫苗的正常批次库存明细）
 */
@Data
public class BatchStockVO {

    private Long stockId;

    private String batchNo;

    /** 生产商名 */
    private String manufacturerName;

    /** 生产日期 */
    private LocalDate productionDate;

    /** 有效期至 */
    private LocalDate expiryDate;

    /** 可用数量 */
    private Integer quantity;
}
