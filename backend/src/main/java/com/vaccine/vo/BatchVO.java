package com.vaccine.vo;

import lombok.Data;

import java.time.LocalDate;

/**
 * 批次视图（含疫苗名称）
 */
@Data
public class BatchVO {

    private Long batchId;

    private Long manufacturerId;

    private Long vaccineId;

    private String vaccineName;

    private String batchNo;

    private LocalDate productionDate;

    private LocalDate expiryDate;

    private Integer productionQuantity;
}
