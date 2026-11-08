package com.vaccine.controller;

import com.vaccine.common.Result;
import com.vaccine.dto.BatchRequest;
import com.vaccine.dto.SupplyRequest;
import com.vaccine.dto.VaccineRequest;
import com.vaccine.entity.Institution;
import com.vaccine.security.RequireRole;
import com.vaccine.security.Role;
import com.vaccine.service.ManufacturerService;
import com.vaccine.vo.BatchVO;
import com.vaccine.vo.SupplyVO;
import com.vaccine.vo.VaccineVO;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 生产商接口（仅 MANUFACTURER 可访问）：疫苗维护（本厂关联）、批次生产记录维护。
 * 数据级隔离：仅能操作本厂数据，跨厂操作返回 403。
 */
@RestController
@RequestMapping("/api/manufacturer")
@RequiredArgsConstructor
@RequireRole(Role.MANUFACTURER)
public class ManufacturerController {

    private final ManufacturerService manufacturerService;

    // ---------------- 疫苗 ----------------

    /** 全部疫苗列表（含关联厂商与本厂标记） */
    @GetMapping("/vaccines")
    public Result<List<VaccineVO>> vaccines() {
        return Result.ok(manufacturerService.listVaccines());
    }

    /** 创建疫苗（自动关联本厂） */
    @PostMapping("/vaccines")
    public Result<VaccineVO> createVaccine(@Validated @RequestBody VaccineRequest request) {
        return Result.ok(manufacturerService.createVaccine(request));
    }

    /** 修改疫苗基本信息（仅本厂已关联的疫苗） */
    @PutMapping("/vaccines/{vaccineId}")
    public Result<VaccineVO> updateVaccine(@PathVariable Long vaccineId,
                                           @Validated @RequestBody VaccineRequest request) {
        return Result.ok(manufacturerService.updateVaccine(vaccineId, request));
    }

    /** 本厂关联指定疫苗 */
    @PostMapping("/vaccines/{vaccineId}/associate")
    public Result<Void> associateVaccine(@PathVariable Long vaccineId) {
        manufacturerService.associateVaccine(vaccineId);
        return Result.ok();
    }

    /** 本厂解除疫苗关联（存在本厂生产批次时拒绝） */
    @DeleteMapping("/vaccines/{vaccineId}/associate")
    public Result<Void> disassociateVaccine(@PathVariable Long vaccineId) {
        manufacturerService.disassociateVaccine(vaccineId);
        return Result.ok();
    }

    // ---------------- 批次 ----------------

    /** 本厂批次列表（含疫苗名） */
    @GetMapping("/batches")
    public Result<List<BatchVO>> batches() {
        return Result.ok(manufacturerService.listBatches());
    }

    /** 创建生产批次（疫苗必须为本厂关联） */
    @PostMapping("/batches")
    public Result<BatchVO> createBatch(@Validated @RequestBody BatchRequest request) {
        return Result.ok(manufacturerService.createBatch(request));
    }

    /** 修改生产批次（仅本厂批次） */
    @PutMapping("/batches/{batchId}")
    public Result<BatchVO> updateBatch(@PathVariable Long batchId,
                                       @Validated @RequestBody BatchRequest request) {
        return Result.ok(manufacturerService.updateBatch(batchId, request));
    }

    // ---------------- 供货 ----------------

    /** 全部接种机构列表（创建供货下拉选择） */
    @GetMapping("/institutions")
    public Result<List<Institution>> institutions() {
        return Result.ok(manufacturerService.listInstitutions());
    }

    /** 创建供货记录（批次必须为本厂批次，目标机构为收货方） */
    @PostMapping("/supplies")
    public Result<SupplyVO> createSupply(@Validated @RequestBody SupplyRequest request) {
        return Result.ok(manufacturerService.createSupply(request));
    }

    /** 本厂供货记录列表（按供货时间倒序） */
    @GetMapping("/supplies")
    public Result<List<SupplyVO>> supplies() {
        return Result.ok(manufacturerService.listSupplies());
    }
}
