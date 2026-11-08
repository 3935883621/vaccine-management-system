package com.vaccine.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.vaccine.entity.VaccineStock;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

public interface VaccineStockMapper extends BaseMapper<VaccineStock> {

    /**
     * FEFO 选批次并加行锁：该机构该疫苗可出库批次（status ∈ {1 正常, 2 临期} 且 quantity>0
     * 且 expiry_date >= 今天，临期仍可出库、过期实时封锁）中有效期最早的一条。
     * FOR UPDATE 行锁防止并发超卖；并发事务在锁释放后基于当前读重新评估条件，
     * 若该行 quantity 已减为 0 则自动落到下一批次或查不到。
     */
    @Select("SELECT * FROM vaccine_stock " +
            "WHERE institution_id = #{institutionId} AND vaccine_id = #{vaccineId} " +
            "AND status IN (1, 2) AND quantity > 0 AND expiry_date >= CURDATE() " +
            "ORDER BY expiry_date ASC, stock_id ASC LIMIT 1 FOR UPDATE")
    VaccineStock selectFefoForUpdate(@Param("institutionId") Long institutionId,
                                     @Param("vaccineId") Long vaccineId);
}
