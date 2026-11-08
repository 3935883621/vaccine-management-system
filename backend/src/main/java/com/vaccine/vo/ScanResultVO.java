package com.vaccine.vo;

import lombok.Data;

import java.time.LocalDate;

/**
 * 库存预警扫描结果摘要（定时任务/手动触发返回）。
 */
@Data
public class ScanResultVO {

    /** 扫描日期 */
    private LocalDate scanDate;

    /** 本次新标记临期的批次数（status 1→2） */
    private int nearExpiryMarked;

    /** 本次新封锁的过期批次数（status 1/2→3） */
    private int expiredBlocked;

    /** 因批次过期自动取消的待接种预约数 */
    private int cancelledAppointments;
}
