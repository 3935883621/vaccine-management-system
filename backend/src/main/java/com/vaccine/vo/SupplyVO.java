package com.vaccine.vo;

import lombok.Data;

import java.time.LocalDate;

/**
 * 供货记录视图（含机构名、疫苗名、批次信息）
 */
@Data
public class SupplyVO {

    private Long supplyId;

    private Long batchId;

    private Long institutionId;

    private String institutionName;

    private Long vaccineId;

    private String vaccineName;

    private Long manufacturerId;

    private String manufacturerName;

    private String batchNo;

    /** 配送数量 */
    private Integer quantity;

    /** 已入库数量 */
    private Integer receivedQuantity;

    /** 配送日期 */
    private LocalDate supplyDate;

    /** 批次生产日期 */
    private LocalDate productionDate;

    /** 批次有效期 */
    private LocalDate expiryDate;

    /** 状态：1=待入库 2=已入库 */
    private Integer status;
}
