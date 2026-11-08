package com.vaccine.controller;

import com.vaccine.common.Result;
import com.vaccine.dto.RegisterInstitutionRequest;
import com.vaccine.dto.RegisterManufacturerRequest;
import com.vaccine.dto.VaccineRequest;
import com.vaccine.entity.Institution;
import com.vaccine.entity.Manufacturer;
import com.vaccine.security.RequireRole;
import com.vaccine.security.Role;
import com.vaccine.service.SuperAdminService;
import com.vaccine.vo.VaccineVO;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * 超级管理员接口：注册机构/生产商，以及机构/生产商/疫苗的兜底增改查（仅 SUPER_ADMIN 可访问）
 */
@RestController
@RequestMapping("/api/super-admin")
@RequiredArgsConstructor
@RequireRole(Role.SUPER_ADMIN)
public class SuperAdminController {

    private final SuperAdminService superAdminService;

    /** 注册接种机构（事务内同时创建机构管理员账号） */
    @PostMapping("/institutions")
    public Result<Map<String, Object>> registerInstitution(
            @Validated @RequestBody RegisterInstitutionRequest request) {
        return Result.ok(superAdminService.registerInstitution(request));
    }

    /** 注册生产商（含登录账号） */
    @PostMapping("/manufacturers")
    public Result<Manufacturer> registerManufacturer(
            @Validated @RequestBody RegisterManufacturerRequest request) {
        return Result.ok(superAdminService.registerManufacturer(request));
    }

    // ---------------- 机构兜底 ----------------

    /** 机构列表 */
    @GetMapping("/institutions")
    public Result<List<Institution>> institutions() {
        return Result.ok(superAdminService.listInstitutions());
    }

    /** 修改机构信息 */
    @PutMapping("/institutions/{institutionId}")
    public Result<Institution> updateInstitution(@PathVariable Long institutionId,
                                                 @RequestBody Institution request) {
        return Result.ok(superAdminService.updateInstitution(institutionId, request));
    }

    // ---------------- 生产商兜底 ----------------

    /** 生产商列表 */
    @GetMapping("/manufacturers")
    public Result<List<Manufacturer>> manufacturers() {
        return Result.ok(superAdminService.listManufacturers());
    }

    /** 修改生产商信息（不影响登录账号与密码） */
    @PutMapping("/manufacturers/{manufacturerId}")
    public Result<Manufacturer> updateManufacturer(@PathVariable Long manufacturerId,
                                                   @RequestBody Manufacturer request) {
        return Result.ok(superAdminService.updateManufacturer(manufacturerId, request));
    }

    // ---------------- 疫苗兜底（支持多厂商） ----------------

    /** 疫苗列表（含关联生产商） */
    @GetMapping("/vaccines")
    public Result<List<VaccineVO>> vaccines() {
        return Result.ok(superAdminService.listVaccines());
    }

    /** 创建疫苗：可指定多个生产商，写入 vaccine_manufacturer */
    @PostMapping("/vaccines")
    public Result<VaccineVO> createVaccine(@Validated @RequestBody VaccineRequest request) {
        return Result.ok(superAdminService.createVaccine(request));
    }

    /** 修改疫苗（manufacturerIds 非空时全量替换关联） */
    @PutMapping("/vaccines/{vaccineId}")
    public Result<VaccineVO> updateVaccine(@PathVariable Long vaccineId,
                                           @Validated @RequestBody VaccineRequest request) {
        return Result.ok(superAdminService.updateVaccine(vaccineId, request));
    }
}
