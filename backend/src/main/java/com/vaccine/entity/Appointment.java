package com.vaccine.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 预约表（核心业务表；唯一索引 (user_id, shift_id) 防同一班次重复预约）
 */
@Data
@TableName("appointment")
public class Appointment {

    @TableId(type = IdType.AUTO)
    private Long appointmentId;

    private Long userId;

    private Long vaccineId;

    private Long institutionId;

    private Long shiftId;

    /** 锁定的库存记录ID */
    private Long stockId;

    /** 预约日期（=班次日期，系统自动写入） */
    private LocalDate appointmentDate;

    /** 剂次序号（第几剂） */
    private Integer doseNumber;

    /** 接种时间（确认接种时写入） */
    private LocalDateTime vaccinationTime;

    /** 取消原因（用户取消/班次停诊/疫苗批次过期） */
    private String cancelReason;

    /** 状态：1=待接种 2=已完成 3=已取消 */
    private Integer status;
}
