package com.vaccine.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalTime;

/**
 * 班次表（护士排班；institution_id 冗余，由系统取护士所属机构自动写入）
 */
@Data
@TableName("shift")
public class Shift {

    @TableId(type = IdType.AUTO)
    private Long shiftId;

    private Long nurseId;

    /** 机构ID（冗余字段，系统自动写入） */
    private Long institutionId;

    private LocalDate shiftDate;

    private LocalTime startTime;

    private LocalTime endTime;

    /** 最大预约名额（>0） */
    private Integer maxAppointments;

    /** 当前已预约人数 */
    private Integer currentCount;

    /** 状态：1=正常 3=已停诊 */
    private Integer status;
}
