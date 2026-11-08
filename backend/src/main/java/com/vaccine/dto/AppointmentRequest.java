package com.vaccine.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 预约请求（用户选择疫苗 + 班次；库存按 FEFO 自动锁定，剂次由系统推算）
 */
@Data
public class AppointmentRequest {

    @NotNull(message = "疫苗不能为空")
    private Long vaccineId;

    @NotNull(message = "班次不能为空")
    private Long shiftId;
}
