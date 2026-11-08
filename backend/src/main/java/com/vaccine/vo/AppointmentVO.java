package com.vaccine.vo;

import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

/**
 * 预约视图（我的预约列表项；含疫苗/机构/班次/护士/批次关联信息）
 */
@Data
public class AppointmentVO {

    private Long appointmentId;

    /** 预约居民姓名（机构端待接种列表展示） */
    private String userName;

    private Long vaccineId;

    private String vaccineName;

    /** 全程剂次（疫苗） */
    private Integer dosesRequired;

    private Long institutionId;

    private String institutionName;

    private Long shiftId;

    private LocalDate shiftDate;

    private LocalTime startTime;

    private LocalTime endTime;

    /** 接种护士名（来自班次） */
    private String nurseName;

    private Long stockId;

    /** 锁定批次号 */
    private String batchNo;

    /** 剂次序号（第几剂） */
    private Integer doseNumber;

    /** 预约日期（=班次日期） */
    private LocalDate appointmentDate;

    /** 接种时间（已完成时有值） */
    private LocalDateTime vaccinationTime;

    /** 取消原因（用户取消/班次停诊/疫苗批次过期） */
    private String cancelReason;

    /** 状态：1=待接种 2=已完成 3=已取消 */
    private Integer status;
}
