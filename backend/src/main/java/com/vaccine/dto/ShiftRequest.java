package com.vaccine.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalTime;

/**
 * 班次创建/修改请求（institution_id 由系统取护士所属机构自动写入，前端不传）
 */
@Data
public class ShiftRequest {

    @NotNull(message = "护士不能为空")
    private Long nurseId;

    @NotNull(message = "班次日期不能为空")
    private LocalDate shiftDate;

    @NotNull(message = "开始时间不能为空")
    private LocalTime startTime;

    @NotNull(message = "结束时间不能为空")
    private LocalTime endTime;

    @NotNull(message = "最大预约名额不能为空")
    @Min(value = 1, message = "最大预约名额必须大于 0")
    private Integer maxAppointments;

    /** 状态：1=正常 3=已停诊（修改班次时使用，创建默认 1） */
    private Integer status;
}
