package com.vaccine.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.vaccine.common.BusinessException;
import com.vaccine.dto.NurseRequest;
import com.vaccine.dto.ShiftRequest;
import com.vaccine.dto.StockInRequest;
import com.vaccine.entity.Appointment;
import com.vaccine.entity.Batch;
import com.vaccine.entity.Institution;
import com.vaccine.entity.InstitutionAdmin;
import com.vaccine.entity.Manufacturer;
import com.vaccine.entity.Nurse;
import com.vaccine.entity.Shift;
import com.vaccine.entity.Supply;
import com.vaccine.entity.User;
import com.vaccine.entity.Vaccine;
import com.vaccine.entity.VaccineStock;
import com.vaccine.mapper.AppointmentMapper;
import com.vaccine.mapper.BatchMapper;
import com.vaccine.mapper.InstitutionAdminMapper;
import com.vaccine.mapper.InstitutionMapper;
import com.vaccine.mapper.ManufacturerMapper;
import com.vaccine.mapper.NurseMapper;
import com.vaccine.mapper.ShiftMapper;
import com.vaccine.mapper.SupplyMapper;
import com.vaccine.mapper.UserMapper;
import com.vaccine.mapper.VaccineMapper;
import com.vaccine.mapper.VaccineStockMapper;
import com.vaccine.security.UserContext;
import com.vaccine.vo.AppointmentVO;
import com.vaccine.vo.ScheduleOverviewVO;
import com.vaccine.vo.ShiftVO;
import com.vaccine.vo.StockAlertVO;
import com.vaccine.vo.StockTraceVO;
import com.vaccine.vo.SupplyVO;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 机构管理员服务：护士/班次维护、排班页数据。
 * 数据级隔离：仅可操作本机构数据，跨机构访问返回 403。
 */
@Service
@RequiredArgsConstructor
public class InstitutionAdminService {

    private final InstitutionAdminMapper institutionAdminMapper;
    private final InstitutionMapper institutionMapper;
    private final NurseMapper nurseMapper;
    private final ShiftMapper shiftMapper;
    private final VaccineStockMapper vaccineStockMapper;
    private final AppointmentMapper appointmentMapper;
    private final VaccineMapper vaccineMapper;
    private final ManufacturerMapper manufacturerMapper;
    private final SupplyMapper supplyMapper;
    private final BatchMapper batchMapper;
    private final UserMapper userMapper;

    /** 临期预警天数（配置：vaccine.near-expiry-days） */
    @Value("${vaccine.near-expiry-days}")
    private int nearExpiryDays;

    /** 低库存阈值（配置：vaccine.low-stock-threshold） */
    @Value("${vaccine.low-stock-threshold}")
    private int lowStockThreshold;

    // ---------------- 机构信息 ----------------

    /** 查询当前管理员所属机构信息 */
    public Institution myInstitution() {
        return institutionMapper.selectById(currentAdmin().getInstitutionId());
    }

    // ---------------- 护士 ----------------

    /** 查询护士列表；校验数据归属，他机构数据返回 403 */
    public List<Nurse> listNurses(Long institutionId) {
        checkOwnInstitution(institutionId);
        return nurseMapper.selectList(new LambdaQueryWrapper<Nurse>()
                .eq(Nurse::getInstitutionId, institutionId)
                .orderByAsc(Nurse::getNurseId));
    }

    /** 创建护士（institution_id 由系统取管理员所属机构自动写入） */
    public Nurse createNurse(NurseRequest req) {
        checkEmployeeNoUnique(req.getEmployeeNo(), null);
        Nurse nurse = new Nurse();
        applyRequest(nurse, req);
        nurse.setInstitutionId(currentAdmin().getInstitutionId());
        nurseMapper.insert(nurse);
        return nurse;
    }

    /** 修改护士（仅本机构护士） */
    public Nurse updateNurse(Long nurseId, NurseRequest req) {
        Nurse nurse = nurseMapper.selectById(nurseId);
        if (nurse == null) {
            throw BusinessException.badRequest("护士不存在");
        }
        checkOwnInstitution(nurse.getInstitutionId());
        checkEmployeeNoUnique(req.getEmployeeNo(), nurseId);
        applyRequest(nurse, req);
        nurseMapper.updateById(nurse);
        return nurse;
    }

    /** 删除护士（仅本机构护士；存在排班记录时拒绝） */
    public void deleteNurse(Long nurseId) {
        Nurse nurse = nurseMapper.selectById(nurseId);
        if (nurse == null) {
            throw BusinessException.badRequest("护士不存在");
        }
        checkOwnInstitution(nurse.getInstitutionId());
        Long shiftCount = shiftMapper.selectCount(new LambdaQueryWrapper<Shift>()
                .eq(Shift::getNurseId, nurseId));
        if (shiftCount != null && shiftCount > 0) {
            throw BusinessException.badRequest("该护士存在排班记录，无法删除");
        }
        nurseMapper.deleteById(nurseId);
    }

    // ---------------- 班次 ----------------

    /** 查询班次列表（含护士姓名）；校验数据归属，他机构数据返回 403 */
    public List<ShiftVO> listShifts(Long institutionId) {
        checkOwnInstitution(institutionId);
        return toShiftVOs(shiftMapper.selectList(new LambdaQueryWrapper<Shift>()
                .eq(Shift::getInstitutionId, institutionId)
                .orderByAsc(Shift::getShiftDate)
                .orderByAsc(Shift::getStartTime)));
    }

    /** 创建班次：institution_id 由系统取护士所属机构自动写入（无法出现跨机构班次） */
    public ShiftVO createShift(ShiftRequest req) {
        Nurse nurse = requireOwnNurse(req.getNurseId());
        if (req.getEndTime().isBefore(req.getStartTime()) || req.getEndTime().equals(req.getStartTime())) {
            throw BusinessException.badRequest("结束时间必须晚于开始时间");
        }
        Shift shift = new Shift();
        applyRequest(shift, req);
        shift.setInstitutionId(nurse.getInstitutionId());
        shift.setCurrentCount(0);
        shift.setStatus(1);
        shiftMapper.insert(shift);
        return toShiftVO(shift, nurse.getName());
    }

    /**
     * 修改班次（仅本机构班次；换护士同样校验护士属于本机构）。
     * 停诊（status → 3）时在同一事务内自动取消该班次全部待接种预约并释放库存锁定与名额（需求 A-07 / 5.3）。
     */
    @Transactional
    public ShiftVO updateShift(Long shiftId, ShiftRequest req) {
        Shift shift = shiftMapper.selectById(shiftId);
        if (shift == null) {
            throw BusinessException.badRequest("班次不存在");
        }
        checkOwnInstitution(shift.getInstitutionId());
        Nurse nurse = requireOwnNurse(req.getNurseId());
        if (req.getEndTime().isBefore(req.getStartTime()) || req.getEndTime().equals(req.getStartTime())) {
            throw BusinessException.badRequest("结束时间必须晚于开始时间");
        }
        applyRequest(shift, req);
        shift.setInstitutionId(nurse.getInstitutionId());
        shiftMapper.updateById(shift);
        if (req.getStatus() != null && req.getStatus() == 3) {
            cancelPendingAppointments(shiftId);
        }
        return toShiftVO(shift, nurse.getName());
    }

    /**
     * 停诊自动取消：锁班次行 → 逐条取消待接种预约（回加库存锁定）→ 名额清空。
     * 锁顺序 shift → stock 与预约事务一致，避免死锁；重复停诊无待接种预约时为无操作（幂等）。
     */
    private void cancelPendingAppointments(Long shiftId) {
        shiftMapper.selectByIdForUpdate(shiftId);
        List<Appointment> pendings = appointmentMapper.selectList(new LambdaQueryWrapper<Appointment>()
                .eq(Appointment::getShiftId, shiftId)
                .eq(Appointment::getStatus, 1));
        for (Appointment appt : pendings) {
            vaccineStockMapper.update(null, new LambdaUpdateWrapper<VaccineStock>()
                    .eq(VaccineStock::getStockId, appt.getStockId())
                    .gt(VaccineStock::getLockedQuantity, 0)
                    .setSql("quantity = quantity + 1")
                    .setSql("locked_quantity = locked_quantity - 1"));
            appt.setStatus(3);
            appt.setCancelReason("班次停诊");
            appointmentMapper.updateById(appt);
        }
        if (!pendings.isEmpty()) {
            shiftMapper.update(null, new LambdaUpdateWrapper<Shift>()
                    .eq(Shift::getShiftId, shiftId)
                    .gt(Shift::getCurrentCount, 0)
                    .setSql("current_count = current_count - " + pendings.size()));
        }
    }

    /** 删除班次（仅本机构班次；已有预约记录时拒绝） */
    public void deleteShift(Long shiftId) {
        Shift shift = shiftMapper.selectById(shiftId);
        if (shift == null) {
            throw BusinessException.badRequest("班次不存在");
        }
        checkOwnInstitution(shift.getInstitutionId());
        Long apptCount = appointmentMapper.selectCount(new LambdaQueryWrapper<Appointment>()
                .eq(Appointment::getShiftId, shiftId));
        if (apptCount != null && apptCount > 0) {
            throw BusinessException.badRequest("该班次已有预约记录，无法删除");
        }
        shiftMapper.deleteById(shiftId);
    }

    // ---------------- 排班页数据 ----------------

    /**
     * 排班页数据：本机构疫苗库存（批次明细 + 疫苗汇总）+ 临期/低库存预警标志 + 本机构班次
     */
    public ScheduleOverviewVO scheduleOverview() {
        Long institutionId = currentAdmin().getInstitutionId();
        ScheduleOverviewVO vo = new ScheduleOverviewVO();
        vo.setInstitutionId(institutionId);
        Institution institution = institutionMapper.selectById(institutionId);
        vo.setInstitutionName(institution == null ? "" : institution.getName());

        List<VaccineStock> stocks = vaccineStockMapper.selectList(new LambdaQueryWrapper<VaccineStock>()
                .eq(VaccineStock::getInstitutionId, institutionId));
        Map<Long, String> vaccineNames = vaccineMapper.selectList(null).stream()
                .collect(Collectors.toMap(Vaccine::getVaccineId, Vaccine::getName));
        Map<Long, String> mfrNames = manufacturerMapper.selectList(null).stream()
                .collect(Collectors.toMap(Manufacturer::getManufacturerId, Manufacturer::getName));

        LocalDate today = LocalDate.now();
        LocalDate nearDeadline = today.plusDays(nearExpiryDays);

        // 批次明细
        List<ScheduleOverviewVO.StockDetail> details = new ArrayList<>();
        List<ScheduleOverviewVO.StockDetail> nearExpiryStocks = new ArrayList<>();
        // 疫苗汇总
        Map<Long, ScheduleOverviewVO.VaccineSummary> summaryMap = new java.util.LinkedHashMap<>();

        for (VaccineStock stock : stocks) {
            ScheduleOverviewVO.StockDetail detail = toStockDetail(stock, vaccineNames, mfrNames);
            details.add(detail);

            ScheduleOverviewVO.VaccineSummary summary = summaryMap.computeIfAbsent(stock.getVaccineId(), id -> {
                ScheduleOverviewVO.VaccineSummary s = new ScheduleOverviewVO.VaccineSummary();
                s.setVaccineId(id);
                s.setVaccineName(vaccineNames.getOrDefault(id, ""));
                s.setTotalQuantity(0);
                s.setTotalLocked(0);
                s.setNearExpiry(false);
                s.setLowStock(false);
                s.setExpiredBlocked(false);
                return s;
            });
            summary.setTotalQuantity(summary.getTotalQuantity() + stock.getQuantity());
            summary.setTotalLocked(summary.getTotalLocked() + stock.getLockedQuantity());

            // 临期：状态为临期预警，或（未过期且距过期 <= 阈值天 的正常批次）
            boolean nearExpiry = stock.getStatus() != null && stock.getStatus() == 2
                    || (stock.getExpiryDate() != null
                    && !stock.getExpiryDate().isBefore(today)
                    && !stock.getExpiryDate().isAfter(nearDeadline)
                    && (stock.getStatus() == null || stock.getStatus() == 1));
            if (nearExpiry) {
                summary.setNearExpiry(true);
                nearExpiryStocks.add(detail);
            }
            // 过期封锁：状态 3 或已过有效期
            boolean expired = stock.getStatus() != null && stock.getStatus() == 3
                    || (stock.getExpiryDate() != null && stock.getExpiryDate().isBefore(today));
            if (expired) {
                summary.setExpiredBlocked(true);
            }
        }
        // 低库存：合计可用 <= 阈值
        List<ScheduleOverviewVO.VaccineSummary> lowStockVaccines = new ArrayList<>();
        for (ScheduleOverviewVO.VaccineSummary summary : summaryMap.values()) {
            if (summary.getTotalQuantity() <= lowStockThreshold) {
                summary.setLowStock(true);
                lowStockVaccines.add(summary);
            }
        }

        vo.setStockDetails(details);
        vo.setVaccineSummaries(new ArrayList<>(summaryMap.values()));
        vo.setNearExpiryStocks(nearExpiryStocks);
        vo.setLowStockVaccines(lowStockVaccines);
        vo.setShifts(toShiftVOs(shiftMapper.selectList(new LambdaQueryWrapper<Shift>()
                .eq(Shift::getInstitutionId, institutionId)
                .orderByAsc(Shift::getShiftDate)
                .orderByAsc(Shift::getStartTime))));
        return vo;
    }

    // ---------------- 供货入库 ----------------

    /** 本机构供货记录列表（含生产商名、疫苗名、批次信息） */
    public List<SupplyVO> listSupplies() {
        Long institutionId = currentAdmin().getInstitutionId();
        List<Supply> supplies = supplyMapper.selectList(new LambdaQueryWrapper<Supply>()
                .eq(Supply::getInstitutionId, institutionId)
                .orderByDesc(Supply::getSupplyId));
        return toSupplyVOs(supplies);
    }

    /**
     * 确认入库（支持分批）：单次入库量不得超过剩余供货量；
     * 条件更新保证并发安全；received_quantity 累加满额后状态自动置“已入库”。
     */
    @Transactional
    public SupplyVO stockIn(Long supplyId, StockInRequest req) {
        Supply supply = supplyMapper.selectById(supplyId);
        if (supply == null) {
            throw BusinessException.badRequest("供货记录不存在");
        }
        checkOwnInstitution(supply.getInstitutionId());
        if (supply.getStatus() != null && supply.getStatus() != 1) {
            throw BusinessException.badRequest("该供货记录已完成入库，不可重复入库");
        }
        int qty = req.getQuantity();
        // 条件更新（InnoDB 行锁 + 当前读，并发入库安全）：剩余量校验与累加、满额置状态在同一条 SQL 完成
        int rows = supplyMapper.update(null, new LambdaUpdateWrapper<Supply>()
                .eq(Supply::getSupplyId, supplyId)
                .eq(Supply::getStatus, 1)
                .apply("received_quantity + {0} <= quantity", qty)
                .setSql("received_quantity = received_quantity + " + qty)
                .setSql("status = IF(received_quantity >= quantity, 2, status)"));
        if (rows == 0) {
            throw BusinessException.badRequest("入库数量超出剩余供货量");
        }
        // 新增库存记录（批次号、生产商、生产/有效期从 batch 带入）
        Batch batch = batchMapper.selectById(supply.getBatchId());
        VaccineStock stock = new VaccineStock();
        stock.setInstitutionId(supply.getInstitutionId());
        stock.setSupplyId(supplyId);
        stock.setBatchId(batch.getBatchId());
        stock.setVaccineId(batch.getVaccineId());
        stock.setManufacturerId(batch.getManufacturerId());
        stock.setBatchNo(batch.getBatchNo());
        stock.setQuantity(qty);
        stock.setLockedQuantity(0);
        stock.setProductionDate(batch.getProductionDate());
        stock.setExpiryDate(batch.getExpiryDate());
        stock.setStatus(1);
        vaccineStockMapper.insert(stock);

        SupplyVO vo = toSupplyVO(supply, batch, institutionMapper.selectById(supply.getInstitutionId()));
        int received = supply.getReceivedQuantity() + qty;
        vo.setReceivedQuantity(received);
        vo.setStatus(received >= supply.getQuantity() ? 2 : 1);
        return vo;
    }

    /** 追溯查询：由一条库存记录查到供货（日期/数量）与生产批次（生产商/生产日期/有效期） */
    public StockTraceVO traceStock(Long stockId) {
        VaccineStock stock = vaccineStockMapper.selectById(stockId);
        if (stock == null) {
            throw BusinessException.badRequest("库存记录不存在");
        }
        checkOwnInstitution(stock.getInstitutionId());
        Supply supply = supplyMapper.selectById(stock.getSupplyId());
        if (supply == null) {
            throw BusinessException.badRequest("库存记录缺失关联供货记录");
        }
        Batch batch = batchMapper.selectById(stock.getBatchId());
        if (batch == null) {
            throw BusinessException.badRequest("库存记录缺失关联生产批次");
        }
        Vaccine vaccine = vaccineMapper.selectById(stock.getVaccineId());
        Manufacturer manufacturer = manufacturerMapper.selectById(stock.getManufacturerId());
        Institution institution = institutionMapper.selectById(supply.getInstitutionId());

        StockTraceVO vo = new StockTraceVO();
        // 库存
        vo.setStockId(stock.getStockId());
        vo.setVaccineId(stock.getVaccineId());
        vo.setVaccineName(vaccine == null ? "" : vaccine.getName());
        vo.setBatchNo(stock.getBatchNo());
        vo.setQuantity(stock.getQuantity());
        vo.setLockedQuantity(stock.getLockedQuantity());
        vo.setStockStatus(stock.getStatus());
        // 供货
        vo.setSupplyId(supply.getSupplyId());
        vo.setSupplyDate(supply.getSupplyDate());
        vo.setSupplyQuantity(supply.getQuantity());
        vo.setReceivedQuantity(supply.getReceivedQuantity());
        vo.setSupplyInstitutionId(supply.getInstitutionId());
        vo.setSupplyInstitutionName(institution == null ? "" : institution.getName());
        // 生产批次
        vo.setBatchId(batch.getBatchId());
        vo.setManufacturerName(manufacturer == null ? "" : manufacturer.getName());
        vo.setProductionDate(batch.getProductionDate());
        vo.setExpiryDate(batch.getExpiryDate());
        vo.setProductionQuantity(batch.getProductionQuantity());
        return vo;
    }

    // ---------------- 接种处理 ----------------

    /** 本机构待接种预约列表（含居民姓名、疫苗、班次、护士、批次信息） */
    public List<AppointmentVO> listPendingAppointments() {
        Long institutionId = currentAdmin().getInstitutionId();
        List<Appointment> appts = appointmentMapper.selectList(new LambdaQueryWrapper<Appointment>()
                .eq(Appointment::getInstitutionId, institutionId)
                .eq(Appointment::getStatus, 1)
                .orderByAsc(Appointment::getAppointmentDate)
                .orderByAsc(Appointment::getAppointmentId));
        return toAppointmentVOs(appts);
    }

    /**
     * 确认接种（事务）：锁预约行保证幂等（并发确认时后者读到 status=2 后拒绝）→
     * 库存 locked_quantity − 1（quantity 不变）→ vaccination_time=当前时间 → status=2。
     */
    @Transactional
    public AppointmentVO confirmVaccination(Long appointmentId) {
        Appointment appt = appointmentMapper.selectByIdForUpdate(appointmentId);
        if (appt == null) {
            throw BusinessException.badRequest("预约不存在");
        }
        checkOwnInstitution(appt.getInstitutionId());
        if (appt.getStatus() == null || appt.getStatus() != 1) {
            throw BusinessException.badRequest("该预约已完成或已取消，不可重复确认");
        }
        // 扣减锁定库存（quantity 不变，locked_quantity − 1）
        int rows = vaccineStockMapper.update(null, new LambdaUpdateWrapper<VaccineStock>()
                .eq(VaccineStock::getStockId, appt.getStockId())
                .gt(VaccineStock::getLockedQuantity, 0)
                .setSql("locked_quantity = locked_quantity - 1"));
        if (rows == 0) {
            throw BusinessException.badRequest("库存锁定记录异常，无法确认接种");
        }
        // 释放班次名额：current_count − 1（与预约 +1、取消 −1 对齐，避免已接种预约永久占座）
        shiftMapper.update(null, new LambdaUpdateWrapper<Shift>()
                .eq(Shift::getShiftId, appt.getShiftId())
                .setSql("current_count = current_count - 1"));
        appt.setVaccinationTime(LocalDateTime.now());
        appt.setStatus(2);
        appointmentMapper.updateById(appt);
        return toAppointmentVOs(List.of(appt)).get(0);
    }

    // ---------------- 库存预警与销毁 ----------------

    /**
     * 本机构预警看板（需求 5.4）：临期列表（标黄）、过期列表（红色封锁）、
     * 低库存列表（某疫苗全部可出库批次 quantity 合计 ≤ 阈值）。
     */
    public StockAlertVO listAlerts() {
        Long institutionId = currentAdmin().getInstitutionId();
        Institution institution = institutionMapper.selectById(institutionId);
        LocalDate today = LocalDate.now();
        LocalDate nearDeadline = today.plusDays(nearExpiryDays);

        // 本机构全部未销毁批次（按有效期升序）
        List<VaccineStock> stocks = vaccineStockMapper.selectList(new LambdaQueryWrapper<VaccineStock>()
                .eq(VaccineStock::getInstitutionId, institutionId)
                .ne(VaccineStock::getStatus, 4)
                .orderByAsc(VaccineStock::getExpiryDate)
                .orderByAsc(VaccineStock::getStockId));
        Map<Long, String> vaccineNames = vaccineMapper.selectList(null).stream()
                .collect(Collectors.toMap(Vaccine::getVaccineId, Vaccine::getName));
        Map<Long, String> mfrNames = manufacturerMapper.selectList(null).stream()
                .collect(Collectors.toMap(Manufacturer::getManufacturerId, Manufacturer::getName));

        List<StockAlertVO.StockItem> nearExpiryStocks = new ArrayList<>();
        List<StockAlertVO.StockItem> expiredStocks = new ArrayList<>();
        // 低库存：本机构出现过的疫苗默认可出库 0，再累加可出库批次（status ∈ {1,2} 且未过期）
        Map<Long, Integer> availableByVaccine = new LinkedHashMap<>();
        for (VaccineStock stock : stocks) {
            availableByVaccine.putIfAbsent(stock.getVaccineId(), 0);
            boolean expired = (stock.getStatus() != null && stock.getStatus() == 3)
                    || (stock.getExpiryDate() != null && stock.getExpiryDate().isBefore(today));
            if (expired) {
                expiredStocks.add(toStockItem(stock, vaccineNames, mfrNames, today));
                continue;
            }
            boolean nearExpiry = (stock.getStatus() != null && stock.getStatus() == 2)
                    || (stock.getExpiryDate() != null && !stock.getExpiryDate().isAfter(nearDeadline));
            if (nearExpiry) {
                nearExpiryStocks.add(toStockItem(stock, vaccineNames, mfrNames, today));
            }
            if (stock.getStatus() != null && (stock.getStatus() == 1 || stock.getStatus() == 2)) {
                availableByVaccine.merge(stock.getVaccineId(), stock.getQuantity(), Integer::sum);
            }
        }
        List<StockAlertVO.LowStockItem> lowStockVaccines = new ArrayList<>();
        for (Map.Entry<Long, Integer> entry : availableByVaccine.entrySet()) {
            if (entry.getValue() <= lowStockThreshold) {
                StockAlertVO.LowStockItem item = new StockAlertVO.LowStockItem();
                item.setVaccineId(entry.getKey());
                item.setVaccineName(vaccineNames.getOrDefault(entry.getKey(), ""));
                item.setTotalQuantity(entry.getValue());
                item.setThreshold(lowStockThreshold);
                lowStockVaccines.add(item);
            }
        }

        StockAlertVO vo = new StockAlertVO();
        vo.setInstitutionId(institutionId);
        vo.setInstitutionName(institution == null ? "" : institution.getName());
        vo.setScanDate(today);
        vo.setNearExpiryStocks(nearExpiryStocks);
        vo.setExpiredStocks(expiredStocks);
        vo.setLowStockVaccines(lowStockVaccines);
        return vo;
    }

    /**
     * 销毁过期批次（需求 5.2 / A-06）：仅 status=3 可销毁；校验 locked_quantity=0
     * （否则先取消关联预约释放锁定）；quantity 置 0、status → 4，记录保留不物理删除。
     */
    @Transactional
    public StockAlertVO.StockItem destroyStock(Long stockId) {
        VaccineStock stock = vaccineStockMapper.selectById(stockId);
        if (stock == null) {
            throw BusinessException.badRequest("库存记录不存在");
        }
        checkOwnInstitution(stock.getInstitutionId());
        if (stock.getStatus() == null || stock.getStatus() != 3) {
            throw BusinessException.badRequest("仅已过期锁定的批次可销毁");
        }
        if (stock.getLockedQuantity() != null && stock.getLockedQuantity() > 0) {
            throw BusinessException.badRequest("该批次存在锁定预约，请先取消关联预约后再销毁");
        }
        stock.setQuantity(0);
        stock.setStatus(4);
        vaccineStockMapper.updateById(stock);
        Map<Long, String> vaccineNames = vaccineMapper.selectList(null).stream()
                .collect(Collectors.toMap(Vaccine::getVaccineId, Vaccine::getName));
        Map<Long, String> mfrNames = manufacturerMapper.selectList(null).stream()
                .collect(Collectors.toMap(Manufacturer::getManufacturerId, Manufacturer::getName));
        return toStockItem(stock, vaccineNames, mfrNames, LocalDate.now());
    }

    // ---------------- 私有工具 ----------------

    /** 校验数据归属：仅允许本机构，否则 403 */
    private void checkOwnInstitution(Long institutionId) {
        if (!currentAdmin().getInstitutionId().equals(institutionId)) {
            throw BusinessException.forbidden("无权访问其他机构数据");
        }
    }

    /** 取护士并校验属于本机构，否则 403（杜绝跨机构班次） */
    private Nurse requireOwnNurse(Long nurseId) {
        Nurse nurse = nurseMapper.selectById(nurseId);
        if (nurse == null) {
            throw BusinessException.badRequest("护士不存在");
        }
        checkOwnInstitution(nurse.getInstitutionId());
        return nurse;
    }

    private void checkEmployeeNoUnique(String employeeNo, Long excludeNurseId) {
        LambdaQueryWrapper<Nurse> wrapper = new LambdaQueryWrapper<Nurse>()
                .eq(Nurse::getEmployeeNo, employeeNo);
        if (excludeNurseId != null) {
            wrapper.ne(Nurse::getNurseId, excludeNurseId);
        }
        Long count = nurseMapper.selectCount(wrapper);
        if (count != null && count > 0) {
            throw BusinessException.badRequest("工号已存在");
        }
    }

    private void applyRequest(Nurse nurse, NurseRequest req) {
        nurse.setName(req.getName());
        nurse.setEmployeeNo(req.getEmployeeNo());
        nurse.setTitle(req.getTitle());
        nurse.setPhone(req.getPhone());
    }

    private void applyRequest(Shift shift, ShiftRequest req) {
        shift.setNurseId(req.getNurseId());
        shift.setShiftDate(req.getShiftDate());
        shift.setStartTime(req.getStartTime());
        shift.setEndTime(req.getEndTime());
        shift.setMaxAppointments(req.getMaxAppointments());
        if (req.getStatus() != null) {
            shift.setStatus(req.getStatus());
        }
    }

    private InstitutionAdmin currentAdmin() {
        InstitutionAdmin admin = institutionAdminMapper.selectById(UserContext.userId());
        if (admin == null) {
            throw BusinessException.unauthorized("登录已失效");
        }
        return admin;
    }

    private StockAlertVO.StockItem toStockItem(VaccineStock stock, Map<Long, String> vaccineNames,
                                               Map<Long, String> mfrNames, LocalDate today) {
        StockAlertVO.StockItem item = new StockAlertVO.StockItem();
        item.setStockId(stock.getStockId());
        item.setVaccineId(stock.getVaccineId());
        item.setVaccineName(vaccineNames.getOrDefault(stock.getVaccineId(), ""));
        item.setBatchNo(stock.getBatchNo());
        item.setManufacturerName(mfrNames.getOrDefault(stock.getManufacturerId(), ""));
        item.setQuantity(stock.getQuantity());
        item.setLockedQuantity(stock.getLockedQuantity());
        item.setProductionDate(stock.getProductionDate());
        item.setExpiryDate(stock.getExpiryDate());
        item.setDaysToExpiry(stock.getExpiryDate() == null ? null
                : ChronoUnit.DAYS.between(today, stock.getExpiryDate()));
        item.setStatus(stock.getStatus());
        return item;
    }

    // ---------------- 预约视图 ----------------
    
    private List<AppointmentVO> toAppointmentVOs(List<Appointment> appts) {
        if (appts.isEmpty()) {
            return new ArrayList<>();
        }
        Map<Long, Shift> shiftMap = shiftMapper.selectBatchIds(appts.stream()
                        .map(Appointment::getShiftId).distinct().collect(Collectors.toList())).stream()
                .collect(Collectors.toMap(Shift::getShiftId, Function.identity()));
        Map<Long, VaccineStock> stockMap = vaccineStockMapper.selectBatchIds(appts.stream()
                        .map(Appointment::getStockId).distinct().collect(Collectors.toList())).stream()
                .collect(Collectors.toMap(VaccineStock::getStockId, Function.identity()));
        Map<Long, Vaccine> vaccineMap = vaccineMapper.selectList(null).stream()
                .collect(Collectors.toMap(Vaccine::getVaccineId, Function.identity()));
        Map<Long, String> userNames = userMapper.selectList(null).stream()
                .collect(Collectors.toMap(User::getUserId, User::getRealName));
        Map<Long, String> nurseNames = nurseMapper.selectList(null).stream()
                .collect(Collectors.toMap(Nurse::getNurseId, Nurse::getName));
        return appts.stream().map(a -> {
            Shift shift = shiftMap.get(a.getShiftId());
            VaccineStock stock = stockMap.get(a.getStockId());
            Vaccine vaccine = vaccineMap.get(a.getVaccineId());
            AppointmentVO vo = new AppointmentVO();
            vo.setAppointmentId(a.getAppointmentId());
            vo.setUserName(userNames.getOrDefault(a.getUserId(), ""));
            vo.setVaccineId(a.getVaccineId());
            vo.setVaccineName(vaccine == null ? "" : vaccine.getName());
            vo.setDosesRequired(vaccine == null ? null : vaccine.getDosesRequired());
            vo.setInstitutionId(a.getInstitutionId());
            vo.setShiftId(a.getShiftId());
            if (shift != null) {
                vo.setShiftDate(shift.getShiftDate());
                vo.setStartTime(shift.getStartTime());
                vo.setEndTime(shift.getEndTime());
                vo.setNurseName(nurseNames.getOrDefault(shift.getNurseId(), ""));
            }
            vo.setStockId(a.getStockId());
            vo.setBatchNo(stock == null ? "" : stock.getBatchNo());
            vo.setDoseNumber(a.getDoseNumber());
            vo.setAppointmentDate(a.getAppointmentDate());
            vo.setVaccinationTime(a.getVaccinationTime());
            vo.setCancelReason(a.getCancelReason());
            vo.setStatus(a.getStatus());
            return vo;
        }).collect(Collectors.toList());
    }
    
    // ---------------- 供货视图 ----------------

    private List<SupplyVO> toSupplyVOs(List<Supply> supplies) {
        if (supplies.isEmpty()) {
            return new ArrayList<>();
        }
        Map<Long, Batch> batchMap = batchMapper.selectList(null).stream()
                .collect(Collectors.toMap(Batch::getBatchId, b -> b));
        Map<Long, String> vaccineNames = vaccineMapper.selectList(null).stream()
                .collect(Collectors.toMap(Vaccine::getVaccineId, Vaccine::getName));
        Map<Long, String> mfrNames = manufacturerMapper.selectList(null).stream()
                .collect(Collectors.toMap(Manufacturer::getManufacturerId, Manufacturer::getName));
        Institution institution = institutionMapper.selectById(currentAdmin().getInstitutionId());
        return supplies.stream().map(s -> {
            Batch b = batchMap.get(s.getBatchId());
            SupplyVO vo = toSupplyVO(s, b, institution);
            if (b != null) {
                vo.setVaccineName(vaccineNames.getOrDefault(b.getVaccineId(), ""));
                vo.setManufacturerName(mfrNames.getOrDefault(b.getManufacturerId(), ""));
            }
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

    private List<ShiftVO> toShiftVOs(List<Shift> shifts) {
        if (shifts.isEmpty()) {
            return new ArrayList<>();
        }
        Map<Long, String> nurseNames = nurseMapper.selectList(null).stream()
                .collect(Collectors.toMap(Nurse::getNurseId, Nurse::getName));
        return shifts.stream().map(s -> toShiftVO(s, nurseNames.getOrDefault(s.getNurseId(), "")))
                .collect(Collectors.toList());
    }

    private ShiftVO toShiftVO(Shift s, String nurseName) {
        ShiftVO vo = new ShiftVO();
        vo.setShiftId(s.getShiftId());
        vo.setNurseId(s.getNurseId());
        vo.setNurseName(nurseName);
        vo.setInstitutionId(s.getInstitutionId());
        vo.setShiftDate(s.getShiftDate());
        vo.setStartTime(s.getStartTime());
        vo.setEndTime(s.getEndTime());
        vo.setMaxAppointments(s.getMaxAppointments());
        vo.setCurrentCount(s.getCurrentCount());
        vo.setStatus(s.getStatus());
        return vo;
    }

    private ScheduleOverviewVO.StockDetail toStockDetail(VaccineStock stock,
                                                         Map<Long, String> vaccineNames,
                                                         Map<Long, String> mfrNames) {
        ScheduleOverviewVO.StockDetail detail = new ScheduleOverviewVO.StockDetail();
        detail.setStockId(stock.getStockId());
        detail.setVaccineId(stock.getVaccineId());
        detail.setVaccineName(vaccineNames.getOrDefault(stock.getVaccineId(), ""));
        detail.setManufacturerId(stock.getManufacturerId());
        detail.setManufacturerName(mfrNames.getOrDefault(stock.getManufacturerId(), ""));
        detail.setBatchNo(stock.getBatchNo());
        detail.setQuantity(stock.getQuantity());
        detail.setLockedQuantity(stock.getLockedQuantity());
        detail.setExpiryDate(stock.getExpiryDate());
        detail.setStatus(stock.getStatus());
        return detail;
    }
}
