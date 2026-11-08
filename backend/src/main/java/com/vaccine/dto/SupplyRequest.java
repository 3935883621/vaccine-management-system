package com.vaccine.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.LocalDate;

/**
 * 创建供货记录请求（生产商选择本厂批次、目标机构）
 */
@Data
public class SupplyRequest {

    @NotNull(message = "批次不能为空")
    private Long batchId;

    @NotNull(message = "目标机构不能为空")
    private Long institutionId;

    @NotNull(message = "配送数量不能为空")
    @Min(value = 1, message = "配送数量必须大于 0")
    private Integer quantity;

    /** 配送日期（不传默认当天） */
    private LocalDate supplyDate;
}
