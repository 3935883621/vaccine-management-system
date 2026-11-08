package com.vaccine.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.vaccine.common.BusinessException;
import com.vaccine.dto.AppointmentRequest;
import com.vaccine.entity.Appointment;
import com.vaccine.entity.Institution;
import com.vaccine.entity.Nurse;
import com.vaccine.entity.Shift;
import com.vaccine.entity.Vaccine;
import com.vaccine.entity.VaccineStock;
import com.vaccine.mapper.AppointmentMapper;
import com.vaccine.mapper.InstitutionMapper;
import com.vaccine.mapper.ManufacturerMapper;
import com.vaccine.mapper.NurseMapper;
import com.vaccine.mapper.ShiftMapper;
import com.vaccine.mapper.VaccineMapper;
import com.vaccine.mapper.VaccineStockMapper;
import com.vaccine.security.UserContext;
import com.vaccine.vo.AppointmentVO;
import com.vaccine.vo.BatchStockVO;
import com.vaccine.vo.ShiftVO;
import com.vaccine.vo.UserInstitutionVO;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 用户（居民）服务：疫苗/机构/班次查询、预约（FEFO 锁定）、取消预约、我的预约。
 * 预约事务按 shift → stock 顺序加行锁（与取消预约一致），防止并发超卖与死锁。
 */
@Service
@RequiredArgsConstructor
public class UserService {

    private final VaccineMapper vaccineMapper;
    private final InstitutionMapper institutionMapper;
    private final VaccineStockMapper vaccineStockMapper;
    private final ShiftMapper shiftMapper;
    private final AppointmentMapper appointmentMapper;
    private final NurseMapper nurseMapper;
    private final ManufacturerMapper manufacturerMapper;

    /** 取消预约截止小时数（配置：vaccine.cancel-deadline-hours，班次开始前） */
    @Value("${vaccine.cancel-deadline-hours}")
    private int cancelDeadlineHours;

    // ---------------- 查询 ----------------

    /** 全部疫苗列表 */
    public List<Vaccine> listVaccines() {
        return vaccineMapper.selectList(new LambdaQueryWrapper<Vaccine>()
                .orderByAsc(Vaccine::getVaccineId));
    }

    /**
     * 机构查询：返回全部机构 + 各自有可出库批次库存（status ∈ {1,2} 且 quantity>0 且未过期）的疫苗。
     * 无可出库库存的机构+疫苗组合不出现在结果中（组合隐藏）。
     */
    public List<UserInstitutionVO> listInstitutions() {
        List<Institution> institutions = institutionMapper.selectList(
                new LambdaQueryWrapper<Institution>().orderByAsc(Institution::getInstitutionId));
        List<VaccineStock> stocks = vaccineStockMapper.selectList(new LambdaQueryWrapper<VaccineStock>()
                .in(VaccineStock::getStatus, 1, 2)
                .gt(VaccineStock::getQuantity, 0)
                .ge(VaccineStock::getExpiryDate, LocalDate.now()));
        Map<Long, Vaccine> vaccineMap = vaccineMapper.selectList(null).stream()
                .collect(Collectors.toMap(Vaccine::getVaccineId, Function.identity()));
        Map<Long, Map<Long, Integer>> totalsByInst = stocks.stream().collect(Collectors.groupingBy(
                VaccineStock::getInstitutionId,
                Collectors.groupingBy(VaccineStock::getVaccineId,
                        Collectors.summingInt(VaccineStock::getQuantity))));

        return institutions.stream().map(inst -> {
            UserInstitutionVO vo = new UserInstitutionVO();
            vo.setInstitutionId(inst.getInstitutionId());
            vo.setName(inst.getName());
            vo.setAddress(inst.getAddress());
            vo.setRegion(inst.getRegion());
            vo.setContactPhone(inst.getContactPhone());
            List<UserInstitutionVO.VaccineBrief> briefs = new ArrayList<>();
            Map<Long, Integer> totals = totalsByInst.getOrDefault(inst.getInstitutionId(), Map.of());
            totals.entrySet().stream().sorted(Map.Entry.comparingByKey()).forEach(entry -> {
                Vaccine v = vaccineMap.get(entry.getKey());
                if (v == null) {
                    return;
                }
                UserInstitutionVO.VaccineBrief brief = new UserInstitutionVO.VaccineBrief();
                brief.setVaccineId(v.getVaccineId());
                brief.setName(v.getName());
                brief.setType(v.getType());
                brief.setDisease(v.getDisease());
                brief.setDosesRequired(v.getDosesRequired());
                brief.setTotalQuantity(entry.getValue());
                briefs.add(brief);
            });
            vo.setAvailableVaccines(briefs);
            return vo;
        }).collect(Collectors.toList());
    }

    /** 机构某疫苗的可出库批次余量明细（status ∈ {1,2}、quantity>0、未过期，按有效期升序 = FEFO 出库顺序） */
    public List<BatchStockVO> listBatchStocks(Long institutionId, Long vaccineId) {
        List<VaccineStock> stocks = vaccineStockMapper.selectList(new LambdaQueryWrapper<VaccineStock>()
                .eq(VaccineStock::getInstitutionId, institutionId)
                .eq(VaccineStock::getVaccineId, vaccineId)
                .in(VaccineStock::getStatus, 1, 2)
                .gt(VaccineStock::getQuantity, 0)
                .ge(VaccineStock::getExpiryDate, LocalDate.now())
                .orderByAsc(VaccineStock::getExpiryDate)
                .orderByAsc(VaccineStock::getStockId));
        Map<Long, String> mfrNames = manufacturerMapper.selectList(null).stream()
                .collect(Collectors.toMap(m -> m.getManufacturerId(), m -> m.getName()));
        return stocks.stream().map(s -> {
            BatchStockVO vo = new BatchStockVO();
            vo.setStockId(s.getStockId());
            vo.setBatchNo(s.getBatchNo());
            vo.setManufacturerName(mfrNames.getOrDefault(s.getManufacturerId(), ""));
            vo.setProductionDate(s.getProductionDate());
            vo.setExpiryDate(s.getExpiryDate());
            vo.setQuantity(s.getQuantity());
            return vo;
        }).collect(Collectors.toList());
    }

    /**
     * 班次列表（名额>0 且 status=1）；该机构该疫苗无可出库库存时返回空（有苗才有位）。
     */
    public List<ShiftVO> listShifts(Long institutionId, Long vaccineId) {
        Long stockCount = vaccineStockMapper.selectCount(new LambdaQueryWrapper<VaccineStock>()
                .eq(VaccineStock::getInstitutionId, institutionId)
                .eq(VaccineStock::getVaccineId, vaccineId)
                .in(VaccineStock::getStatus, 1, 2)
                .gt(VaccineStock::getQuantity, 0)
                .ge(VaccineStock::getExpiryDate, LocalDate.now()));
        if (stockCount == null || stockCount == 0) {
            return new ArrayList<>();
        }
        List<Shift> shifts = shiftMapper.selectList(new LambdaQueryWrapper<Shift>()
                .eq(Shift::getInstitutionId, institutionId)
                .eq(Shift::getStatus, 1)
                .apply("current_count < max_appointments")
                .orderByAsc(Shift::getShiftDate)
                .orderByAsc(Shift::getStartTime));
        return toShiftVOs(shifts);
    }

    /** 我的预约列表（含疫苗/机构/班次/护士/批次关联信息，倒序） */
    public List<AppointmentVO> listMyAppointments() {
        return listMyAppointmentsByStatus(null);
    }

    /** 我的接种记录列表（仅已完成预约，按接种时间倒序） */
    public List<AppointmentVO> listVaccinationRecords() {
        return listMyAppointmentsByStatus(List.of(2));
    }

    private List<AppointmentVO> listMyAppointmentsByStatus(List<Integer> statuses) {
        Long userId = UserContext.userId();
        LambdaQueryWrapper<Appointment> wrapper = new LambdaQueryWrapper<Appointment>()
                .eq(Appointment::getUserId, userId);
        if (statuses != null) {
            wrapper.in(Appointment::getStatus, statuses);
        }
        wrapper.orderByDesc(Appointment::getAppointmentId);
        List<Appointment> appts = appointmentMapper.selectList(wrapper);
        if (appts.isEmpty()) {
            return new ArrayList<>();
        }
        Map<Long, Shift> shiftMap = shiftMapper.selectBatchIds(appts.stream()
                        .map(Appointment::getShiftId).distinct().collect(Collectors.toList())).stream()
                .collect(Collectors.toMap(Shift::getShiftId, Function.identity()));
        Map<Long, VaccineStock> stockMap = vaccineStockMapper.selectBatchIds(appts.stream()
                        .map(Appointment::getStockId).distinct().collect(Collectors.toList())).stream()
                .collect(Collectors.toMap(VaccineStock::getStockId, Function.identity()));
        Map<Long, String> vaccineNames = vaccineMapper.selectList(null).stream()
                .collect(Collectors.toMap(Vaccine::getVaccineId, Vaccine::getName));
        Map<Long, Vaccine> vaccineMap = vaccineMapper.selectList(null).stream()
                .collect(Collectors.toMap(Vaccine::getVaccineId, Function.identity()));
        Map<Long, String> instNames = institutionMapper.selectList(null).stream()
                .collect(Collectors.toMap(Institution::getInstitutionId, Institution::getName));
        Map<Long, String> nurseNames = nurseMapper.selectList(null).stream()
                .collect(Collectors.toMap(Nurse::getNurseId, Nurse::getName));
        return appts.stream().map(a -> {
            Shift shift = shiftMap.get(a.getShiftId());
            VaccineStock stock = stockMap.get(a.getStockId());
            AppointmentVO vo = new AppointmentVO();
            vo.setAppointmentId(a.getAppointmentId());
            vo.setVaccineId(a.getVaccineId());
            vo.setVaccineName(vaccineNames.getOrDefault(a.getVaccineId(), ""));
            Vaccine vaccine = vaccineMap.get(a.getVaccineId());
            vo.setDosesRequired(vaccine == null ? null : vaccine.getDosesRequired());
            vo.setInstitutionId(a.getInstitutionId());
            vo.setInstitutionName(instNames.getOrDefault(a.getInstitutionId(), ""));
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

    // ---------------- 预约 ----------------

    /**
     * 提交预约（事务）：锁班次 → FEFO 选批次锁行 → 剂次/重复/时段冲突校验 →
     * 库存 quantity-1 / locked+1 → 班次 current_count+1 → 插入预约。
     */
    @Transactional
    public AppointmentVO createAppointment(AppointmentRequest req) {
        Long userId = UserContext.userId();

        // 1. 班次校验 + 行锁（加锁顺序 shift → stock，与取消预约一致，避免死锁）
        Shift shift = shiftMapper.selectByIdForUpdate(req.getShiftId());
        if (shift == null) {
            throw BusinessException.badRequest("班次不存在");
        }
        if (shift.getStatus() == null || shift.getStatus() != 1) {
            throw BusinessException.badRequest("班次不可预约");
        }
        if (shift.getCurrentCount() >= shift.getMaxAppointments()) {
            throw BusinessException.badRequest("班次名额已满");
        }

        // 2. FEFO 选批次并锁行（可出库：status ∈ {1,2}、quantity>0、未过期，有效期最早）
        VaccineStock stock = vaccineStockMapper.selectFefoForUpdate(shift.getInstitutionId(), req.getVaccineId());
        if (stock == null) {
            throw BusinessException.badRequest("该机构暂无此疫苗的可用库存");
        }

        // 3. 疫苗与剂次校验
        Vaccine vaccine = vaccineMapper.selectById(req.getVaccineId());
        if (vaccine == null) {
            throw BusinessException.badRequest("疫苗不存在");
        }
        int dose = resolveDoseNumber(userId, req.getVaccineId(), vaccine.getDosesRequired());

        // 4. 重复预约校验：该用户该疫苗不存在待接种预约
        Long pending = appointmentMapper.selectCount(new LambdaQueryWrapper<Appointment>()
                .eq(Appointment::getUserId, userId)
                .eq(Appointment::getVaccineId, req.getVaccineId())
                .eq(Appointment::getStatus, 1));
        if (pending != null && pending > 0) {
            throw BusinessException.badRequest("您已有该疫苗的待接种预约，请先完成或取消");
        }

        // 5. 时段冲突校验：同一用户同一日期+时段无其他待接种预约
        checkTimeConflict(userId, shift);

        // 6. 库存 quantity-1 / locked+1（行锁已持，条件更新双保险）
        int stockRows = vaccineStockMapper.update(null, new LambdaUpdateWrapper<VaccineStock>()
                .eq(VaccineStock::getStockId, stock.getStockId())
                .gt(VaccineStock::getQuantity, 0)
                .setSql("quantity = quantity - 1")
                .setSql("locked_quantity = locked_quantity + 1"));
        if (stockRows == 0) {
            throw BusinessException.badRequest("库存不足，请稍后重试");
        }

        // 7. 班次名额 +1
        int shiftRows = shiftMapper.update(null, new LambdaUpdateWrapper<Shift>()
                .eq(Shift::getShiftId, shift.getShiftId())
                .apply("current_count < max_appointments")
                .setSql("current_count = current_count + 1"));
        if (shiftRows == 0) {
            throw BusinessException.badRequest("班次名额已满");
        }

        // 8. 插入预约（唯一索引 (user_id, shift_id) 兜底重复预约）
        Appointment appt = new Appointment();
        appt.setUserId(userId);
        appt.setVaccineId(req.getVaccineId());
        appt.setInstitutionId(shift.getInstitutionId());
        appt.setShiftId(shift.getShiftId());
        appt.setStockId(stock.getStockId());
        appt.setAppointmentDate(shift.getShiftDate());
        appt.setDoseNumber(dose);
        appt.setStatus(1);
        try {
            appointmentMapper.insert(appt);
        } catch (DuplicateKeyException e) {
            throw BusinessException.badRequest("您已预约该班次，请勿重复预约");
        }
        return toAppointmentVO(appt, shift, stock, vaccine,
                institutionMapper.selectById(shift.getInstitutionId()));
    }

    /**
     * 取消预约（事务）：仅待接种且距班次开始 >= 24 小时；释放库存锁定与班次名额，写取消原因。
     */
    @Transactional
    public void cancelAppointment(Long appointmentId) {
        Long userId = UserContext.userId();
        Appointment appt = appointmentMapper.selectById(appointmentId);
        if (appt == null) {
            throw BusinessException.badRequest("预约不存在");
        }
        if (!appt.getUserId().equals(userId)) {
            throw BusinessException.forbidden("无权操作他人预约");
        }
        if (appt.getStatus() == null || appt.getStatus() != 1) {
            throw BusinessException.badRequest("仅待接种预约可取消");
        }
        // 锁班次（与预约一致的加锁顺序 shift → stock）
        Shift shift = shiftMapper.selectByIdForUpdate(appt.getShiftId());
        if (shift == null) {
            throw BusinessException.badRequest("关联班次不存在");
        }
        // 时间截止校验：当前时间必须不晚于班次开始前 cancelDeadlineHours 小时
        LocalDateTime shiftStart = LocalDateTime.of(shift.getShiftDate(), shift.getStartTime());
        LocalDateTime deadline = shiftStart.minusHours(cancelDeadlineHours);
        if (LocalDateTime.now().isAfter(deadline)) {
            throw BusinessException.badRequest("距班次开始不足 " + cancelDeadlineHours + " 小时，无法取消");
        }
        // 释放库存锁定：quantity+1、locked-1
        vaccineStockMapper.update(null, new LambdaUpdateWrapper<VaccineStock>()
                .eq(VaccineStock::getStockId, appt.getStockId())
                .gt(VaccineStock::getLockedQuantity, 0)
                .setSql("quantity = quantity + 1")
                .setSql("locked_quantity = locked_quantity - 1"));
        // 释放班次名额：current_count-1
        shiftMapper.update(null, new LambdaUpdateWrapper<Shift>()
                .eq(Shift::getShiftId, appt.getShiftId())
                .gt(Shift::getCurrentCount, 0)
                .setSql("current_count = current_count - 1"));
        // 预约置已取消
        appt.setStatus(3);
        appt.setCancelReason("用户取消");
        appointmentMapper.updateById(appt);
    }

    // ---------------- 私有工具 ----------------

    /**
     * 剂次推算（需求 5.3）：
     * 首剂——无任何待接种/已完成记录；续种——存在前一剂已完成记录且未超全程剂次。
     */
    private int resolveDoseNumber(Long userId, Long vaccineId, int dosesRequired) {
        List<Appointment> history = appointmentMapper.selectList(new LambdaQueryWrapper<Appointment>()
                .eq(Appointment::getUserId, userId)
                .eq(Appointment::getVaccineId, vaccineId));
        boolean hasActive = history.stream()
                .anyMatch(a -> a.getStatus() != null && (a.getStatus() == 1 || a.getStatus() == 2));
        if (!hasActive) {
            return 1;
        }
        int maxCompleted = history.stream()
                .filter(a -> a.getStatus() != null && a.getStatus() == 2)
                .mapToInt(a -> a.getDoseNumber() == null ? 0 : a.getDoseNumber())
                .max().orElse(0);
        if (maxCompleted == 0) {
            throw BusinessException.badRequest("请先完成第 1 剂接种");
        }
        if (maxCompleted >= dosesRequired) {
            throw BusinessException.badRequest("已完成全部剂次，无需再预约");
        }
        return maxCompleted + 1;
    }

    /** 时段冲突校验：同一用户在同一日期+时段存在其他待接种预约则拒绝 */
    private void checkTimeConflict(Long userId, Shift target) {
        List<Appointment> pending = appointmentMapper.selectList(new LambdaQueryWrapper<Appointment>()
                .eq(Appointment::getUserId, userId)
                .eq(Appointment::getStatus, 1));
        if (pending.isEmpty()) {
            return;
        }
        List<Shift> shifts = shiftMapper.selectBatchIds(pending.stream()
                .map(Appointment::getShiftId).distinct().collect(Collectors.toList()));
        for (Shift s : shifts) {
            if (s == null || !s.getShiftDate().equals(target.getShiftDate())) {
                continue;
            }
            // 半开区间 [start, end) 重叠判断
            boolean overlap = !s.getEndTime().isBefore(target.getStartTime())
                    && !target.getEndTime().isBefore(s.getStartTime());
            if (overlap) {
                throw BusinessException.badRequest("您在该时段已有其他待接种预约，请勿重复预约");
            }
        }
    }

    private List<ShiftVO> toShiftVOs(List<Shift> shifts) {
        if (shifts.isEmpty()) {
            return new ArrayList<>();
        }
        Map<Long, String> nurseNames = nurseMapper.selectList(null).stream()
                .collect(Collectors.toMap(Nurse::getNurseId, Nurse::getName));
        return shifts.stream().map(s -> {
            ShiftVO vo = new ShiftVO();
            vo.setShiftId(s.getShiftId());
            vo.setNurseId(s.getNurseId());
            vo.setNurseName(nurseNames.getOrDefault(s.getNurseId(), ""));
            vo.setInstitutionId(s.getInstitutionId());
            vo.setShiftDate(s.getShiftDate());
            vo.setStartTime(s.getStartTime());
            vo.setEndTime(s.getEndTime());
            vo.setMaxAppointments(s.getMaxAppointments());
            vo.setCurrentCount(s.getCurrentCount());
            vo.setStatus(s.getStatus());
            return vo;
        }).collect(Collectors.toList());
    }

    private AppointmentVO toAppointmentVO(Appointment a, Shift shift, VaccineStock stock,
                                          Vaccine vaccine, Institution institution) {
        AppointmentVO vo = new AppointmentVO();
        vo.setAppointmentId(a.getAppointmentId());
        vo.setVaccineId(a.getVaccineId());
        vo.setVaccineName(vaccine == null ? "" : vaccine.getName());
        vo.setDosesRequired(vaccine == null ? null : vaccine.getDosesRequired());
        vo.setInstitutionId(a.getInstitutionId());
        vo.setInstitutionName(institution == null ? "" : institution.getName());
        vo.setShiftId(a.getShiftId());
        if (shift != null) {
            vo.setShiftDate(shift.getShiftDate());
            vo.setStartTime(shift.getStartTime());
            vo.setEndTime(shift.getEndTime());
            Nurse nurse = shift.getNurseId() == null ? null : nurseMapper.selectById(shift.getNurseId());
            vo.setNurseName(nurse == null ? "" : nurse.getName());
        }
        vo.setStockId(a.getStockId());
        vo.setBatchNo(stock == null ? "" : stock.getBatchNo());
        vo.setDoseNumber(a.getDoseNumber());
        vo.setAppointmentDate(a.getAppointmentDate());
        vo.setVaccinationTime(a.getVaccinationTime());
        vo.setCancelReason(a.getCancelReason());
        vo.setStatus(a.getStatus());
        return vo;
    }
}
