package com.vaccine.controller;

import com.vaccine.common.Result;
import com.vaccine.dto.NurseRequest;
import com.vaccine.dto.ShiftRequest;
import com.vaccine.dto.StockInRequest;
import com.vaccine.entity.Institution;
import com.vaccine.entity.Nurse;
import com.vaccine.security.RequireRole;
import com.vaccine.security.Role;
import com.vaccine.service.InstitutionAdminService;
import com.vaccine.service.StockAlertService;
import com.vaccine.vo.AppointmentVO;
import com.vaccine.vo.ScanResultVO;
import com.vaccine.vo.ScheduleOverviewVO;
import com.vaccine.vo.ShiftVO;
import com.vaccine.vo.StockAlertVO;
import com.vaccine.vo.StockTraceVO;
import com.vaccine.vo.SupplyVO;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 机构管理员接口（仅 INSTITUTION_ADMIN 可访问，数据级隔离：仅本机构）
 */
@RestController
@RequestMapping("/api/institution-admin")
@RequiredArgsConstructor
@RequireRole(Role.INSTITUTION_ADMIN)
public class InstitutionAdminController {

    private final InstitutionAdminService institutionAdminService;
    private final StockAlertService stockAlertService;

    /** 本机构信息 */
    @GetMapping("/institution")
    public Result<Institution> myInstitution() {
        return Result.ok(institutionAdminService.myInstitution());
    }

    // ---------------- 护士 ----------------

    /** 护士列表（跨机构访问返回 403） */
    @GetMapping("/nurses")
    public Result<List<Nurse>> nurses(@RequestParam Long institutionId) {
        return Result.ok(institutionAdminService.listNurses(institutionId));
    }

    /** 创建护士（institution_id 系统自动写入本机构） */
    @PostMapping("/nurses")
    public Result<Nurse> createNurse(@Validated @RequestBody NurseRequest request) {
        return Result.ok(institutionAdminService.createNurse(request));
    }

    /** 修改护士（仅本机构护士） */
    @PutMapping("/nurses/{nurseId}")
    public Result<Nurse> updateNurse(@PathVariable Long nurseId,
                                     @Validated @RequestBody NurseRequest request) {
        return Result.ok(institutionAdminService.updateNurse(nurseId, request));
    }

    /** 删除护士（仅本机构护士；存在排班记录时拒绝） */
    @DeleteMapping("/nurses/{nurseId}")
    public Result<Void> deleteNurse(@PathVariable Long nurseId) {
        institutionAdminService.deleteNurse(nurseId);
        return Result.ok();
    }

    // ---------------- 班次 ----------------

    /** 班次列表（含护士姓名；跨机构访问返回 403） */
    @GetMapping("/shifts")
    public Result<List<ShiftVO>> shifts(@RequestParam Long institutionId) {
        return Result.ok(institutionAdminService.listShifts(institutionId));
    }

    /** 创建班次（institution_id 由系统取护士所属机构自动写入） */
    @PostMapping("/shifts")
    public Result<ShiftVO> createShift(@Validated @RequestBody ShiftRequest request) {
        return Result.ok(institutionAdminService.createShift(request));
    }

    /** 修改班次（仅本机构班次） */
    @PutMapping("/shifts/{shiftId}")
    public Result<ShiftVO> updateShift(@PathVariable Long shiftId,
                                       @Validated @RequestBody ShiftRequest request) {
        return Result.ok(institutionAdminService.updateShift(shiftId, request));
    }

    /** 删除班次（仅本机构班次；已有预约记录时拒绝） */
    @DeleteMapping("/shifts/{shiftId}")
    public Result<Void> deleteShift(@PathVariable Long shiftId) {
        institutionAdminService.deleteShift(shiftId);
        return Result.ok();
    }

    // ---------------- 排班页数据 ----------------

    /** 排班页数据：本机构疫苗库存 + 临期/低库存预警标志 + 本机构班次 */
    @GetMapping("/schedule-overview")
    public Result<ScheduleOverviewVO> scheduleOverview() {
        return Result.ok(institutionAdminService.scheduleOverview());
    }

    // ---------------- 供货入库 ----------------

    /** 本机构供货记录列表（含生产商名、疫苗名、批次信息） */
    @GetMapping("/supplies")
    public Result<List<SupplyVO>> supplies() {
        return Result.ok(institutionAdminService.listSupplies());
    }

    /** 确认入库（支持分批，单次入库量不得超过剩余供货量） */
    @PostMapping("/supplies/{supplyId}/stock-in")
    public Result<SupplyVO> stockIn(@PathVariable Long supplyId,
                                    @Validated @RequestBody StockInRequest request) {
        return Result.ok(institutionAdminService.stockIn(supplyId, request));
    }

    /** 追溯查询：库存 → 供货 → 生产批次 完整链路 */
    @GetMapping("/stocks/{stockId}/trace")
    public Result<StockTraceVO> traceStock(@PathVariable Long stockId) {
        return Result.ok(institutionAdminService.traceStock(stockId));
    }

    // ---------------- 接种处理 ----------------

    /** 本机构待接种预约列表（含居民姓名、疫苗、班次、护士、批次信息） */
    @GetMapping("/appointments")
    public Result<List<AppointmentVO>> pendingAppointments() {
        return Result.ok(institutionAdminService.listPendingAppointments());
    }

    /** 确认接种（锁行幂等：locked-1、写入接种时间、置已完成） */
    @PostMapping("/appointments/{appointmentId}/confirm")
    public Result<AppointmentVO> confirmVaccination(@PathVariable Long appointmentId) {
        return Result.ok(institutionAdminService.confirmVaccination(appointmentId));
    }

    // ---------------- 库存预警与销毁 ----------------

    /** 本机构预警看板：临期（标黄）/过期（红色封锁）/低库存（合计≤阈值）三类列表 */
    @GetMapping("/alerts")
    public Result<StockAlertVO> alerts() {
        return Result.ok(institutionAdminService.listAlerts());
    }

    /** 手动触发库存扫描（与每日 02:00 定时任务同一逻辑，全局扫描） */
    @PostMapping("/alerts/scan")
    public Result<ScanResultVO> scanAlerts() {
        return Result.ok(stockAlertService.scan());
    }

    /** 销毁过期批次（仅 status=3 且 locked=0；quantity 置 0、status→4，记录保留） */
    @PostMapping("/stocks/{stockId}/destroy")
    public Result<StockAlertVO.StockItem> destroyStock(@PathVariable Long stockId) {
        return Result.ok(institutionAdminService.destroyStock(stockId));
    }
}
