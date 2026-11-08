package com.vaccine.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.vaccine.entity.Shift;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

public interface ShiftMapper extends BaseMapper<Shift> {

    /** 按主键查班次并加行锁（预约/取消事务内使用，防止并发超额占名额） */
    @Select("SELECT * FROM shift WHERE shift_id = #{shiftId} FOR UPDATE")
    Shift selectByIdForUpdate(@Param("shiftId") Long shiftId);
}
