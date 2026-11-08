package com.vaccine.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.vaccine.entity.Appointment;
import com.vaccine.entity.Shift;
import com.vaccine.entity.VaccineStock;
import com.vaccine.mapper.AppointmentMapper;
import com.vaccine.mapper.ShiftMapper;
import com.vaccine.mapper.VaccineStockMapper;
import com.vaccine.vo.ScanResultVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

/**
 * 库存预警定时扫描（需求 S-01 / 5.2）：
 * 每日 02:00 扫描全部机构库存——临期批次置预警（status 1→2）、过期批次封锁（status 1/2→3），
 * 过期批次若存在锁定预约：释放锁定（locked 回加 quantity）、关联待接种预约自动取消
 * （cancel_reason="疫苗批次过期"）、班次名额同步释放。
 * 手动触发接口复用同一扫描逻辑（机构端"立即扫描"）。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class StockAlertService {

    private final VaccineStockMapper vaccineStockMapper;
    private final AppointmentMapper appointmentMapper;
    private final ShiftMapper shiftMapper;

    /** 临期预警天数（配置：vaccine.near-expiry-days） */
    @Value("${vaccine.near-expiry-days}")
    private int nearExpiryDays;

    /**
     * 扫描并处理库存预警（事务）。
     * 处理顺序：先过期封锁（含关联预约取消），后临期标记（两条件按有效期互斥）。
     * 锁顺序与预约/取消事务一致（shift → stock），避免死锁。
     */
    @Scheduled(cron = "${vaccine.alert-scan-cron:0 0 2 * * *}")
    @Transactional
    public ScanResultVO scan() {
        LocalDate today = LocalDate.now();
        LocalDate nearDeadline = today.plusDays(nearExpiryDays);
        ScanResultVO result = new ScanResultVO();
        result.setScanDate(today);

        // 1. 过期封锁：expiry_date < today 且 status ∈ {1,2}
        List<VaccineStock> expiredStocks = vaccineStockMapper.selectList(new LambdaQueryWrapper<VaccineStock>()
                .in(VaccineStock::getStatus, 1, 2)
                .lt(VaccineStock::getExpiryDate, today));
        for (VaccineStock stock : expiredStocks) {
            // 1a. 自动取消关联待接种预约：锁班次行 → 预约置取消 → 班次名额释放
            List<Appointment> pendings = appointmentMapper.selectList(new LambdaQueryWrapper<Appointment>()
                    .eq(Appointment::getStockId, stock.getStockId())
                    .eq(Appointment::getStatus, 1));
            for (Appointment appt : pendings) {
                shiftMapper.selectByIdForUpdate(appt.getShiftId());
                appt.setStatus(3);
                appt.setCancelReason("疫苗批次过期");
                appointmentMapper.updateById(appt);
                shiftMapper.update(null, new LambdaUpdateWrapper<Shift>()
                        .eq(Shift::getShiftId, appt.getShiftId())
                        .gt(Shift::getCurrentCount, 0)
                        .setSql("current_count = current_count - 1"));
                result.setCancelledAppointments(result.getCancelledAppointments() + 1);
            }
            // 1b. 释放锁定并封锁：quantity += locked（MySQL 单表 UPDATE 从左到右求值）、locked=0、status=3
            vaccineStockMapper.update(null, new LambdaUpdateWrapper<VaccineStock>()
                    .eq(VaccineStock::getStockId, stock.getStockId())
                    .setSql("quantity = quantity + locked_quantity")
                    .setSql("locked_quantity = 0")
                    .set(VaccineStock::getStatus, 3));
            result.setExpiredBlocked(result.getExpiredBlocked() + 1);
        }

        // 2. 临期标记：status=1 且 today <= expiry_date <= today+阈值 → status=2（仍可出库）
        int nearRows = vaccineStockMapper.update(null, new LambdaUpdateWrapper<VaccineStock>()
                .eq(VaccineStock::getStatus, 1)
                .ge(VaccineStock::getExpiryDate, today)
                .le(VaccineStock::getExpiryDate, nearDeadline)
                .set(VaccineStock::getStatus, 2));
        result.setNearExpiryMarked(nearRows);

        if (result.getExpiredBlocked() > 0 || result.getNearExpiryMarked() > 0
                || result.getCancelledAppointments() > 0) {
            log.info("[库存扫描] {} 临期标记 {} 批，过期封锁 {} 批，自动取消预约 {} 条",
                    today, result.getNearExpiryMarked(), result.getExpiredBlocked(),
                    result.getCancelledAppointments());
        }
        return result;
    }
}
