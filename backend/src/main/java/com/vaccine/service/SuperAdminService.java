package com.vaccine.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.vaccine.common.BusinessException;
import com.vaccine.dto.RegisterInstitutionRequest;
import com.vaccine.dto.RegisterManufacturerRequest;
import com.vaccine.dto.VaccineRequest;
import com.vaccine.entity.Batch;
import com.vaccine.entity.Institution;
import com.vaccine.entity.InstitutionAdmin;
import com.vaccine.entity.Manufacturer;
import com.vaccine.entity.Vaccine;
import com.vaccine.entity.VaccineManufacturer;
import com.vaccine.mapper.BatchMapper;
import com.vaccine.mapper.InstitutionAdminMapper;
import com.vaccine.mapper.InstitutionMapper;
import com.vaccine.mapper.ManufacturerMapper;
import com.vaccine.mapper.VaccineManufacturerMapper;
import com.vaccine.mapper.VaccineMapper;
import com.vaccine.vo.VaccineVO;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 超级管理员服务：注册机构/生产商（含账号），以及机构/生产商/疫苗的兜底增改查。
 * 疫苗创建/修改支持指定多个生产商，写入 vaccine_manufacturer。
 */
@Service
@RequiredArgsConstructor
public class SuperAdminService {

    private final InstitutionMapper institutionMapper;
    private final InstitutionAdminMapper institutionAdminMapper;
    private final ManufacturerMapper manufacturerMapper;
    private final VaccineMapper vaccineMapper;
    private final VaccineManufacturerMapper vaccineManufacturerMapper;
    private final BatchMapper batchMapper;
    private final BCryptPasswordEncoder passwordEncoder;

    // ---------------- 注册（第 2 步） ----------------

    /** 注册接种机构：事务内同时生成 institution + institution_admin，关联正确 */
    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> registerInstitution(RegisterInstitutionRequest request) {
        if (institutionAdminMapper.selectCount(new LambdaQueryWrapper<InstitutionAdmin>()
                .eq(InstitutionAdmin::getUsername, request.getAdminUsername())) > 0) {
            throw BusinessException.badRequest("管理员用户名已存在");
        }

        Institution institution = new Institution();
        institution.setName(request.getName());
        institution.setAddress(request.getAddress());
        institution.setContactPhone(request.getContactPhone());
        institution.setRegion(request.getRegion());
        institutionMapper.insert(institution);

        InstitutionAdmin admin = new InstitutionAdmin();
        admin.setUsername(request.getAdminUsername());
        admin.setPassword(passwordEncoder.encode(request.getAdminPassword()));
        admin.setRealName(request.getAdminRealName());
        admin.setInstitutionId(institution.getInstitutionId());
        admin.setPhone(request.getAdminPhone());
        institutionAdminMapper.insert(admin);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("institution", institution);
        result.put("adminId", admin.getAdminId());
        result.put("adminUsername", admin.getUsername());
        return result;
    }

    /** 注册生产商（manufacturer 含登录账号，密码 BCrypt 加密） */
    @Transactional(rollbackFor = Exception.class)
    public Manufacturer registerManufacturer(RegisterManufacturerRequest request) {
        if (manufacturerMapper.selectCount(new LambdaQueryWrapper<Manufacturer>()
                .eq(Manufacturer::getUsername, request.getUsername())) > 0) {
            throw BusinessException.badRequest("登录用户名已存在");
        }
        if (manufacturerMapper.selectCount(new LambdaQueryWrapper<Manufacturer>()
                .eq(Manufacturer::getLicenseNo, request.getLicenseNo())) > 0) {
            throw BusinessException.badRequest("生产许可证号已存在");
        }

        Manufacturer manufacturer = new Manufacturer();
        manufacturer.setName(request.getName());
        manufacturer.setLicenseNo(request.getLicenseNo());
        manufacturer.setContactPhone(request.getContactPhone());
        manufacturer.setAddress(request.getAddress());
        manufacturer.setUsername(request.getUsername());
        manufacturer.setPassword(passwordEncoder.encode(request.getPassword()));
        manufacturerMapper.insert(manufacturer);
        return manufacturer;
    }

    // ---------------- 机构兜底 ----------------

    /** 机构列表 */
    public List<Institution> listInstitutions() {
        return institutionMapper.selectList(new LambdaQueryWrapper<Institution>()
                .orderByAsc(Institution::getInstitutionId));
    }

    /** 修改机构信息（仅基本信息，不影响账号） */
    public Institution updateInstitution(Long institutionId, Institution request) {
        Institution institution = institutionMapper.selectById(institutionId);
        if (institution == null) {
            throw BusinessException.badRequest("机构不存在");
        }
        institution.setName(request.getName());
        institution.setAddress(request.getAddress());
        institution.setContactPhone(request.getContactPhone());
        institution.setRegion(request.getRegion());
        institutionMapper.updateById(institution);
        return institution;
    }

    // ---------------- 生产商兜底 ----------------

    /** 生产商列表（密码不返回） */
    public List<Manufacturer> listManufacturers() {
        return manufacturerMapper.selectList(new LambdaQueryWrapper<Manufacturer>()
                .orderByAsc(Manufacturer::getManufacturerId));
    }

    /** 修改生产商信息（仅基本信息，不影响登录账号与密码） */
    public Manufacturer updateManufacturer(Long manufacturerId, Manufacturer request) {
        Manufacturer manufacturer = manufacturerMapper.selectById(manufacturerId);
        if (manufacturer == null) {
            throw BusinessException.badRequest("生产商不存在");
        }
        Long dup = manufacturerMapper.selectCount(new LambdaQueryWrapper<Manufacturer>()
                .eq(Manufacturer::getLicenseNo, request.getLicenseNo())
                .ne(Manufacturer::getManufacturerId, manufacturerId));
        if (dup != null && dup > 0) {
            throw BusinessException.badRequest("生产许可证号已存在");
        }
        manufacturer.setName(request.getName());
        manufacturer.setLicenseNo(request.getLicenseNo());
        manufacturer.setContactPhone(request.getContactPhone());
        manufacturer.setAddress(request.getAddress());
        manufacturerMapper.updateById(manufacturer);
        return manufacturer;
    }

    // ---------------- 疫苗兜底（支持多厂商） ----------------

    /** 疫苗列表（含关联生产商） */
    public List<VaccineVO> listVaccines() {
        Map<Long, String> mfrNames = manufacturerMapper.selectList(null).stream()
                .collect(Collectors.toMap(Manufacturer::getManufacturerId, Manufacturer::getName));
        List<VaccineManufacturer> links = vaccineManufacturerMapper.selectList(null);
        Map<Long, List<VaccineVO.ManufacturerRef>> refMap = links.stream().collect(Collectors.groupingBy(
                VaccineManufacturer::getVaccineId,
                Collectors.mapping(l -> refOf(l.getManufacturerId(), mfrNames), Collectors.toList())));
        return vaccineMapper.selectList(new LambdaQueryWrapper<Vaccine>()
                        .orderByAsc(Vaccine::getVaccineId)).stream()
                .map(v -> {
                    VaccineVO vo = toVO(v);
                    vo.setManufacturers(refMap.getOrDefault(v.getVaccineId(), new ArrayList<>()));
                    return vo;
                })
                .collect(Collectors.toList());
    }

    /** 创建疫苗：可指定多个生产商，写入 vaccine_manufacturer（验收点） */
    @Transactional(rollbackFor = Exception.class)
    public VaccineVO createVaccine(VaccineRequest request) {
        Vaccine vaccine = new Vaccine();
        applyRequest(vaccine, request);
        vaccineMapper.insert(vaccine);
        // 关联生产商（可为多个）
        Map<Long, String> mfrNames = manufacturerMapper.selectList(null).stream()
                .collect(Collectors.toMap(Manufacturer::getManufacturerId, Manufacturer::getName));
        List<VaccineVO.ManufacturerRef> refs = new ArrayList<>();
        Set<Long> distinctIds = request.getManufacturerIds() == null
                ? new HashSet<>() : new HashSet<>(request.getManufacturerIds());
        for (Long mfrId : distinctIds) {
            if (manufacturerMapper.selectById(mfrId) == null) {
                throw BusinessException.badRequest("生产商不存在：" + mfrId);
            }
            linkManufacturer(vaccine.getVaccineId(), mfrId);
            refs.add(refOf(mfrId, mfrNames));
        }
        VaccineVO vo = toVO(vaccine);
        vo.setManufacturers(refs);
        return vo;
    }

    /** 修改疫苗：基本字段更新；manufacturerIds 非空时全量替换关联（被批次引用的关联不可移除） */
    @Transactional(rollbackFor = Exception.class)
    public VaccineVO updateVaccine(Long vaccineId, VaccineRequest request) {
        Vaccine vaccine = vaccineMapper.selectById(vaccineId);
        if (vaccine == null) {
            throw BusinessException.badRequest("疫苗不存在");
        }
        applyRequest(vaccine, request);
        vaccineMapper.updateById(vaccine);

        Map<Long, String> mfrNames = manufacturerMapper.selectList(null).stream()
                .collect(Collectors.toMap(Manufacturer::getManufacturerId, Manufacturer::getName));
        if (request.getManufacturerIds() != null) {
            Set<Long> newIds = new HashSet<>(request.getManufacturerIds());
            for (Long mfrId : newIds) {
                if (manufacturerMapper.selectById(mfrId) == null) {
                    throw BusinessException.badRequest("生产商不存在：" + mfrId);
                }
            }
            List<VaccineManufacturer> existing = vaccineManufacturerMapper.selectList(
                    new LambdaQueryWrapper<VaccineManufacturer>()
                            .eq(VaccineManufacturer::getVaccineId, vaccineId));
            // 要移除的关联若被批次引用则拒绝
            for (VaccineManufacturer link : existing) {
                if (!newIds.contains(link.getManufacturerId())) {
                    Long batchCount = batchMapper.selectCount(new LambdaQueryWrapper<Batch>()
                            .eq(Batch::getVaccineId, vaccineId)
                            .eq(Batch::getManufacturerId, link.getManufacturerId()));
                    if (batchCount != null && batchCount > 0) {
                        throw BusinessException.badRequest("生产商（" + link.getManufacturerId() + "）存在该疫苗的生产批次，无法移除关联");
                    }
                }
            }
            // 全量替换
            vaccineManufacturerMapper.delete(new LambdaQueryWrapper<VaccineManufacturer>()
                    .eq(VaccineManufacturer::getVaccineId, vaccineId));
            for (Long mfrId : newIds) {
                linkManufacturer(vaccineId, mfrId);
            }
        }
        return vaccineVOWithManufacturers(vaccine, mfrNames);
    }

    // ---------------- 私有工具 ----------------

    private void applyRequest(Vaccine vaccine, VaccineRequest req) {
        vaccine.setName(req.getName());
        vaccine.setType(req.getType());
        vaccine.setDisease(req.getDisease());
        vaccine.setDosesRequired(req.getDosesRequired());
        vaccine.setStorageTempMin(req.getStorageTempMin());
        vaccine.setStorageTempMax(req.getStorageTempMax());
        vaccine.setDescription(req.getDescription());
    }

    private void linkManufacturer(Long vaccineId, Long manufacturerId) {
        VaccineManufacturer link = new VaccineManufacturer();
        link.setVaccineId(vaccineId);
        link.setManufacturerId(manufacturerId);
        vaccineManufacturerMapper.insert(link);
    }

    private VaccineVO vaccineVOWithManufacturers(Vaccine vaccine, Map<Long, String> mfrNames) {
        List<VaccineVO.ManufacturerRef> refs = vaccineManufacturerMapper.selectList(
                        new LambdaQueryWrapper<VaccineManufacturer>()
                                .eq(VaccineManufacturer::getVaccineId, vaccine.getVaccineId())).stream()
                .map(l -> refOf(l.getManufacturerId(), mfrNames))
                .collect(Collectors.toList());
        VaccineVO vo = toVO(vaccine);
        vo.setManufacturers(refs);
        return vo;
    }

    private VaccineVO.ManufacturerRef refOf(Long manufacturerId, Map<Long, String> mfrNames) {
        VaccineVO.ManufacturerRef ref = new VaccineVO.ManufacturerRef();
        ref.setManufacturerId(manufacturerId);
        ref.setName(mfrNames.getOrDefault(manufacturerId, ""));
        return ref;
    }

    private VaccineVO toVO(Vaccine v) {
        VaccineVO vo = new VaccineVO();
        vo.setVaccineId(v.getVaccineId());
        vo.setName(v.getName());
        vo.setType(v.getType());
        vo.setDisease(v.getDisease());
        vo.setDosesRequired(v.getDosesRequired());
        vo.setStorageTempMin(v.getStorageTempMin());
        vo.setStorageTempMax(v.getStorageTempMax());
        vo.setDescription(v.getDescription());
        return vo;
    }
}
