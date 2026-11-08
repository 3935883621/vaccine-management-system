package com.vaccine.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.vaccine.common.BusinessException;
import com.vaccine.dto.BatchRequest;
import com.vaccine.dto.SupplyRequest;
import com.vaccine.dto.VaccineRequest;
import com.vaccine.entity.Batch;
import com.vaccine.entity.Institution;
import com.vaccine.entity.Manufacturer;
import com.vaccine.entity.Supply;
import com.vaccine.entity.Vaccine;
import com.vaccine.entity.VaccineManufacturer;
import com.vaccine.mapper.BatchMapper;
import com.vaccine.mapper.InstitutionMapper;
import com.vaccine.mapper.ManufacturerMapper;
import com.vaccine.mapper.SupplyMapper;
import com.vaccine.mapper.VaccineManufacturerMapper;
import com.vaccine.mapper.VaccineMapper;
import com.vaccine.security.UserContext;
import com.vaccine.vo.BatchVO;
import com.vaccine.vo.SupplyVO;
import com.vaccine.vo.VaccineVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 生产商服务：疫苗维护（本厂关联）、批次生产记录维护。
 * 数据级隔离：仅能操作本厂数据，跨厂操作返回 403。
 */
@Service
@RequiredArgsConstructor
public class ManufacturerService {

    private final VaccineMapper vaccineMapper;
    private final VaccineManufacturerMapper vaccineManufacturerMapper;
    private final BatchMapper batchMapper;
    private final ManufacturerMapper manufacturerMapper;
    private final SupplyMapper supplyMapper;
    private final InstitutionMapper institutionMapper;

    // ---------------- 机构（供货下拉） ----------------

    /** 全部接种机构列表（创建供货时选择收货机构） */
    public List<Institution> listInstitutions() {
        return institutionMapper.selectList(new LambdaQueryWrapper<Institution>()
                .orderByAsc(Institution::getInstitutionId));
    }

    // ---------------- 疫苗 ----------------

    /** 全部疫苗列表（含关联厂商与本厂标记 mine） */
    public List<VaccineVO> listVaccines() {
        Long selfId = UserContext.userId();
        List<Vaccine> vaccines = vaccineMapper.selectList(null);
        // 全部关联关系 + 厂商名
        List<VaccineManufacturer> links = vaccineManufacturerMapper.selectList(null);
        Map<Long, String> mfrNames = manufacturerMapper.selectList(null).stream()
                .collect(Collectors.toMap(Manufacturer::getManufacturerId, Manufacturer::getName));
        Map<Long, List<VaccineVO.ManufacturerRef>> refMap = links.stream().collect(Collectors.groupingBy(
                VaccineManufacturer::getVaccineId,
                Collectors.mapping(l -> {
                    VaccineVO.ManufacturerRef ref = new VaccineVO.ManufacturerRef();
                    ref.setManufacturerId(l.getManufacturerId());
                    ref.setName(mfrNames.getOrDefault(l.getManufacturerId(), ""));
                    return ref;
                }, Collectors.toList())));
        return vaccines.stream().map(v -> {
            VaccineVO vo = toVO(v);
            List<VaccineVO.ManufacturerRef> refs = refMap.getOrDefault(v.getVaccineId(), new ArrayList<>());
            vo.setManufacturers(refs);
            vo.setMine(refs.stream().anyMatch(r -> r.getManufacturerId().equals(selfId)));
            return vo;
        }).collect(Collectors.toList());
    }

    /** 创建疫苗（自动关联本厂） */
    @Transactional
    public VaccineVO createVaccine(VaccineRequest req) {
        Vaccine vaccine = new Vaccine();
        applyRequest(vaccine, req);
        vaccineMapper.insert(vaccine);
        linkManufacturer(vaccine.getVaccineId(), UserContext.userId());
        VaccineVO vo = toVO(vaccine);
        vo.setManufacturers(List.of(refOf(UserContext.userId())));
        vo.setMine(true);
        return vo;
    }

    /** 修改疫苗基本信息（仅本厂已关联的疫苗） */
    @Transactional
    public VaccineVO updateVaccine(Long vaccineId, VaccineRequest req) {
        Vaccine vaccine = vaccineMapper.selectById(vaccineId);
        if (vaccine == null) {
            throw BusinessException.badRequest("疫苗不存在");
        }
        checkMine(vaccineId);
        applyRequest(vaccine, req);
        vaccineMapper.updateById(vaccine);
        VaccineVO vo = toVO(vaccine);
        vo.setManufacturers(manufacturersOf(vaccineId));
        vo.setMine(true);
        return vo;
    }

    /** 本厂关联指定疫苗 */
    public void associateVaccine(Long vaccineId) {
        Vaccine vaccine = vaccineMapper.selectById(vaccineId);
        if (vaccine == null) {
            throw BusinessException.badRequest("疫苗不存在");
        }
        if (isLinked(vaccineId, UserContext.userId())) {
            throw BusinessException.badRequest("本厂已关联该疫苗");
        }
        linkManufacturer(vaccineId, UserContext.userId());
    }

    /** 本厂解除疫苗关联（存在本厂生产批次时拒绝） */
    @Transactional
    public void disassociateVaccine(Long vaccineId) {
        Long selfId = UserContext.userId();
        if (!isLinked(vaccineId, selfId)) {
            throw BusinessException.badRequest("本厂未关联该疫苗");
        }
        Long count = batchMapper.selectCount(new LambdaQueryWrapper<Batch>()
                .eq(Batch::getVaccineId, vaccineId)
                .eq(Batch::getManufacturerId, selfId));
        if (count != null && count > 0) {
            throw BusinessException.badRequest("本厂存在该疫苗的生产批次，无法解除关联");
        }
        vaccineManufacturerMapper.delete(new LambdaQueryWrapper<VaccineManufacturer>()
                .eq(VaccineManufacturer::getVaccineId, vaccineId)
                .eq(VaccineManufacturer::getManufacturerId, selfId));
    }

    // ---------------- 批次 ----------------

    /** 本厂批次列表（含疫苗名） */
    public List<BatchVO> listBatches() {
        Long selfId = UserContext.userId();
        List<Batch> batches = batchMapper.selectList(new LambdaQueryWrapper<Batch>()
                .eq(Batch::getManufacturerId, selfId)
                .orderByDesc(Batch::getBatchId));
        Map<Long, String> vaccineNames = vaccineMapper.selectList(null).stream()
                .collect(Collectors.toMap(Vaccine::getVaccineId, Vaccine::getName));
        return batches.stream().map(b -> toBatchVO(b, vaccineNames.getOrDefault(b.getVaccineId(), "")))
                .collect(Collectors.toList());
    }

    /** 创建生产批次（疫苗必须为本厂关联，否则 403） */
    @Transactional
    public BatchVO createBatch(BatchRequest req) {
        Long selfId = UserContext.userId();
        Vaccine vaccine = vaccineMapper.selectById(req.getVaccineId());
        if (vaccine == null) {
            throw BusinessException.badRequest("疫苗不存在");
        }
        if (!isLinked(vaccine.getVaccineId(), selfId)) {
            throw BusinessException.forbidden("无权为本厂未关联的疫苗创建批次");
        }
        if (req.getExpiryDate().isBefore(req.getProductionDate())) {
            throw BusinessException.badRequest("有效期不能早于生产日期");
        }
        checkBatchNoUnique(selfId, req.getBatchNo(), null);
        Batch batch = new Batch();
        batch.setManufacturerId(selfId);
        applyRequest(batch, req);
        batchMapper.insert(batch);
        return toBatchVO(batch, vaccine.getName());
    }

    /** 修改生产批次（仅本厂批次） */
    @Transactional
    public BatchVO updateBatch(Long batchId, BatchRequest req) {
        Batch batch = batchMapper.selectById(batchId);
        if (batch == null) {
            throw BusinessException.badRequest("批次不存在");
        }
        if (!batch.getManufacturerId().equals(UserContext.userId())) {
            throw BusinessException.forbidden("无权操作其他生产商的批次");
        }
        Vaccine vaccine = vaccineMapper.selectById(req.getVaccineId());
        if (vaccine == null) {
            throw BusinessException.badRequest("疫苗不存在");
        }
        if (!isLinked(vaccine.getVaccineId(), UserContext.userId())) {
            throw BusinessException.forbidden("无权为本厂未关联的疫苗维护批次");
        }
        if (req.getExpiryDate().isBefore(req.getProductionDate())) {
            throw BusinessException.badRequest("有效期不能早于生产日期");
        }
        checkBatchNoUnique(UserContext.userId(), req.getBatchNo(), batchId);
        applyRequest(batch, req);
        batchMapper.updateById(batch);
        return toBatchVO(batch, vaccine.getName());
    }

    // ---------------- 供货 --------------

    /** 创建供货记录（批次必须为本厂批次，否则 403） */
    @Transactional
    public SupplyVO createSupply(SupplyRequest req) {
        Long selfId = UserContext.userId();
        Batch batch = batchMapper.selectById(req.getBatchId());
        if (batch == null) {
            throw BusinessException.badRequest("批次不存在");
        }
        if (!batch.getManufacturerId().equals(selfId)) {
            throw BusinessException.forbidden("无权使用其他生产商的批次供货");
        }
        Institution institution = institutionMapper.selectById(req.getInstitutionId());
        if (institution == null) {
            throw BusinessException.badRequest("目标机构不存在");
        }
        Supply supply = new Supply();
        supply.setBatchId(req.getBatchId());
        supply.setInstitutionId(req.getInstitutionId());
        supply.setQuantity(req.getQuantity());
        supply.setReceivedQuantity(0);
        supply.setSupplyDate(req.getSupplyDate() != null ? req.getSupplyDate() : LocalDate.now());
        supply.setStatus(1);
        supplyMapper.insert(supply);
        return toSupplyVO(supply, batch, institution);
    }

    /** 本厂供货记录列表（按供货时间倒序） */
    public List<SupplyVO> listSupplies() {
        Long selfId = UserContext.userId();
        List<Long> selfBatchIds = batchMapper.selectList(new LambdaQueryWrapper<Batch>()
                        .eq(Batch::getManufacturerId, selfId).select(Batch::getBatchId)).stream()
                .map(Batch::getBatchId).collect(Collectors.toList());
        if (selfBatchIds.isEmpty()) {
            return new ArrayList<>();
        }
        List<Supply> supplies = supplyMapper.selectList(new LambdaQueryWrapper<Supply>()
                .in(Supply::getBatchId, selfBatchIds)
                .orderByDesc(Supply::getSupplyId));
        return toSupplyVOs(supplies);
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

    private void applyRequest(Batch batch, BatchRequest req) {
        batch.setVaccineId(req.getVaccineId());
        batch.setBatchNo(req.getBatchNo());
        batch.setProductionDate(req.getProductionDate());
        batch.setExpiryDate(req.getExpiryDate());
        batch.setProductionQuantity(req.getProductionQuantity());
    }

    private void checkBatchNoUnique(Long manufacturerId, String batchNo, Long excludeBatchId) {
        LambdaQueryWrapper<Batch> wrapper = new LambdaQueryWrapper<Batch>()
                .eq(Batch::getManufacturerId, manufacturerId)
                .eq(Batch::getBatchNo, batchNo);
        if (excludeBatchId != null) {
            wrapper.ne(Batch::getBatchId, excludeBatchId);
        }
        Long count = batchMapper.selectCount(wrapper);
        if (count != null && count > 0) {
            throw BusinessException.badRequest("批次号已存在");
        }
    }

    /** 校验疫苗已关联本厂，否则 403（跨厂操作拒绝） */
    private void checkMine(Long vaccineId) {
        if (!isLinked(vaccineId, UserContext.userId())) {
            throw BusinessException.forbidden("无权操作非本厂关联的疫苗");
        }
    }

    private boolean isLinked(Long vaccineId, Long manufacturerId) {
        return vaccineManufacturerMapper.selectCount(new LambdaQueryWrapper<VaccineManufacturer>()
                .eq(VaccineManufacturer::getVaccineId, vaccineId)
                .eq(VaccineManufacturer::getManufacturerId, manufacturerId)) > 0;
    }

    private void linkManufacturer(Long vaccineId, Long manufacturerId) {
        VaccineManufacturer link = new VaccineManufacturer();
        link.setVaccineId(vaccineId);
        link.setManufacturerId(manufacturerId);
        vaccineManufacturerMapper.insert(link);
    }

    private List<VaccineVO.ManufacturerRef> manufacturersOf(Long vaccineId) {
        Map<Long, String> mfrNames = manufacturerMapper.selectList(null).stream()
                .collect(Collectors.toMap(Manufacturer::getManufacturerId, Manufacturer::getName));
        return vaccineManufacturerMapper.selectList(new LambdaQueryWrapper<VaccineManufacturer>()
                        .eq(VaccineManufacturer::getVaccineId, vaccineId)).stream()
                .map(l -> refOf(l.getManufacturerId(), mfrNames))
                .collect(Collectors.toList());
    }

    private VaccineVO.ManufacturerRef refOf(Long manufacturerId) {
        return refOf(manufacturerId, manufacturerMapper.selectById(manufacturerId) == null ? "" : manufacturerMapper.selectById(manufacturerId).getName());
    }

    private VaccineVO.ManufacturerRef refOf(Long manufacturerId, Map<Long, String> mfrNames) {
        return refOf(manufacturerId, mfrNames.getOrDefault(manufacturerId, ""));
    }

    private VaccineVO.ManufacturerRef refOf(Long manufacturerId, String name) {
        VaccineVO.ManufacturerRef ref = new VaccineVO.ManufacturerRef();
        ref.setManufacturerId(manufacturerId);
        ref.setName(name);
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

    private BatchVO toBatchVO(Batch b, String vaccineName) {
        BatchVO vo = new BatchVO();
        vo.setBatchId(b.getBatchId());
        vo.setManufacturerId(b.getManufacturerId());
        vo.setVaccineId(b.getVaccineId());
        vo.setVaccineName(vaccineName);
        vo.setBatchNo(b.getBatchNo());
        vo.setProductionDate(b.getProductionDate());
        vo.setExpiryDate(b.getExpiryDate());
        vo.setProductionQuantity(b.getProductionQuantity());
        return vo;
    }

    // ---------------- 供货视图 --------------

    private List<SupplyVO> toSupplyVOs(List<Supply> supplies) {
        Map<Long, Batch> batchMap = batchMapper.selectList(null).stream()
                .collect(Collectors.toMap(Batch::getBatchId, Function.identity()));
        Map<Long, String> vaccineNames = vaccineMapper.selectList(null).stream()
                .collect(Collectors.toMap(Vaccine::getVaccineId, Vaccine::getName));
        Map<Long, String> mfrNames = manufacturerMapper.selectList(null).stream()
                .collect(Collectors.toMap(Manufacturer::getManufacturerId, Manufacturer::getName));
        Map<Long, String> institutionNames = institutionMapper.selectList(null).stream()
                .collect(Collectors.toMap(Institution::getInstitutionId, Institution::getName));
        return supplies.stream().map(s -> {
            Batch b = batchMap.get(s.getBatchId());
            SupplyVO vo = toSupplyVO(s, b, null);
            if (b != null) {
                vo.setVaccineName(vaccineNames.getOrDefault(b.getVaccineId(), ""));
                vo.setManufacturerName(mfrNames.getOrDefault(b.getManufacturerId(), ""));
            }
            vo.setInstitutionName(institutionNames.getOrDefault(s.getInstitutionId(), ""));
            return vo;
        }).collect(Collectors.toList());
    }

    private SupplyVO toSupplyVO(Supply s, Batch b, Institution institution) {
        SupplyVO vo = new SupplyVO();
        vo.setSupplyId(s.getSupplyId());
        vo.setBatchId(s.getBatchId());
        vo.setInstitutionId(s.getInstitutionId());
        vo.setQuantity(s.getQuantity());
        vo.setReceivedQuantity(s.getReceivedQuantity());
        vo.setSupplyDate(s.getSupplyDate());
        vo.setStatus(s.getStatus());
        if (b != null) {
            vo.setVaccineId(b.getVaccineId());
            vo.setManufacturerId(b.getManufacturerId());
            vo.setBatchNo(b.getBatchNo());
            vo.setProductionDate(b.getProductionDate());
            vo.setExpiryDate(b.getExpiryDate());
        }
        if (institution != null) {
            vo.setInstitutionName(institution.getName());
        }
        return vo;
    }
}
