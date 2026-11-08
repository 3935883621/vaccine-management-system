package com.vaccine.controller;

import com.vaccine.common.Result;
import com.vaccine.dto.AppointmentRequest;
import com.vaccine.entity.Vaccine;
import com.vaccine.security.RequireRole;
import com.vaccine.security.Role;
import com.vaccine.service.UserService;
import com.vaccine.vo.AppointmentVO;
import com.vaccine.vo.BatchStockVO;
import com.vaccine.vo.ShiftVO;
import com.vaccine.vo.UserInstitutionVO;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 居民接口（仅 USER 可访问）：疫苗/机构/班次查询、预约、取消、我的预约。
 */
@RestController
@RequestMapping("/api/user")
@RequiredArgsConstructor
@RequireRole(Role.USER)
public class UserController {

    private final UserService userService;

    // ---------------- 查询 ----------------

    /** 全部疫苗列表 */
    @GetMapping("/vaccines")
    public Result<List<Vaccine>> vaccines() {
        return Result.ok(userService.listVaccines());
    }

    /** 机构查询（无正常批次库存的机构+疫苗组合隐藏） */
    @GetMapping("/institutions")
    public Result<List<UserInstitutionVO>> institutions() {
        return Result.ok(userService.listInstitutions());
    }

    /** 机构某疫苗的正常批次余量明细（FEFO 出库顺序） */
    @GetMapping("/institutions/{institutionId}/vaccines/{vaccineId}/batches")
    public Result<List<BatchStockVO>> batchStocks(@PathVariable Long institutionId,
                                                  @PathVariable Long vaccineId) {
        return Result.ok(userService.listBatchStocks(institutionId, vaccineId));
    }

    /** 班次列表（名额>0；该机构该疫苗无正常库存时返回空） */
    @GetMapping("/shifts")
    public Result<List<ShiftVO>> shifts(@RequestParam Long institutionId,
                                        @RequestParam Long vaccineId) {
        return Result.ok(userService.listShifts(institutionId, vaccineId));
    }

    /** 我的预约列表（含状态与取消原因） */
    @GetMapping("/appointments")
    public Result<List<AppointmentVO>> myAppointments() {
        return Result.ok(userService.listMyAppointments());
    }

    /** 我的接种记录列表（仅已完成：疫苗名、批次号、护士、接种时间、剂次） */
    @GetMapping("/vaccination-records")
    public Result<List<AppointmentVO>> vaccinationRecords() {
        return Result.ok(userService.listVaccinationRecords());
    }

    // ---------------- 预约 ----------------

    /** 提交预约（FEFO 锁定库存，行锁防并发超卖） */
    @PostMapping("/appointments")
    public Result<AppointmentVO> createAppointment(@Validated @RequestBody AppointmentRequest request) {
        return Result.ok(userService.createAppointment(request));
    }

    /** 取消预约（班次开始前 24 小时内不可取消） */
    @DeleteMapping("/appointments/{appointmentId}")
    public Result<Void> cancelAppointment(@PathVariable Long appointmentId) {
        userService.cancelAppointment(appointmentId);
        return Result.ok();
    }
}
