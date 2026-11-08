package com.vaccine.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.LocalDate;

/**
 * 生产批次创建/修改请求
 */
@Data
public class BatchRequest {

    @NotNull(message = "疫苗不能为空")
    private Long vaccineId;

    @NotBlank(message = "批次号不能为空")
    private String batchNo;

    @NotNull(message = "生产日期不能为空")
    private LocalDate productionDate;

    @NotNull(message = "有效期不能为空")
    private LocalDate expiryDate;

    @NotNull(message = "生产数量不能为空")
    @Min(value = 0, message = "生产数量不能为负")
    private Integer productionQuantity;
}
