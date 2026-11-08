package com.vaccine.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.vaccine.entity.Appointment;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

public interface AppointmentMapper extends BaseMapper<Appointment> {

    /** 按主键查预约并加行锁（确认接种事务内使用，保证幂等：并发确认时后者读到已更新状态） */
    @Select("SELECT * FROM appointment WHERE appointment_id = #{appointmentId} FOR UPDATE")
    Appointment selectByIdForUpdate(@Param("appointmentId") Long appointmentId);
}
