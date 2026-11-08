package com.vaccine.vo;

import lombok.Data;

import java.time.LocalDate;

/**
 * 库存追溯视图：由一条 vaccine_stock 记录追溯 supply（供货）与 batch（生产批次）
 */
@Data
public class StockTraceVO {

    // ---- 库存 ----
    private Long stockId;

    private Long vaccineId;

    private String vaccineName;

    private String batchNo;

    private Integer quantity;

    private Integer lockedQuantity;

    private Integer stockStatus;

    // ---- 供货 ----
    private Long supplyId;

    /** 供货日期 */
    private LocalDate supplyDate;

    /** 配送数量 */
    private Integer supplyQuantity;

    /** 已入库数量 */
    private Integer receivedQuantity;

    private Long supplyInstitutionId;

    private String supplyInstitutionName;

    // ---- 生产批次 ----
    private Long batchId;

    /** 生产商名 */
    private String manufacturerName;

    /** 生产日期 */
    private LocalDate productionDate;

    /** 有效期至 */
    private LocalDate expiryDate;

    /** 生产数量 */
    private Integer productionQuantity;
}
