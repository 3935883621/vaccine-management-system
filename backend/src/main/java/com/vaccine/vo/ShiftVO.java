package com.vaccine.vo;

import lombok.Data;

import java.time.LocalDate;
import java.time.LocalTime;

/**
 * 班次视图（含护士姓名）
 */
@Data
public class ShiftVO {

    private Long shiftId;

    private Long nurseId;

    private String nurseName;

    private Long institutionId;

    private LocalDate shiftDate;

    private LocalTime startTime;

    private LocalTime endTime;

    private Integer maxAppointments;

    private Integer currentCount;

    /** 状态：1=正常 3=已停诊 */
    private Integer status;
}
