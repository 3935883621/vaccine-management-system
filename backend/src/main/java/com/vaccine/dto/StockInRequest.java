package com.vaccine.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 确认入库请求（单次入库量，需 <= 剩余供货量）
 */
@Data
public class StockInRequest {

    @NotNull(message = "入库数量不能为空")
    @Min(value = 1, message = "入库数量必须大于 0")
    private Integer quantity;
}
