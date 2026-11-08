package com.vaccine.vo;

import lombok.Data;

import java.time.LocalDate;
import java.util.List;

/**
 * 排班页数据视图：本机构疫苗库存 + 临期/低库存预警标志 + 本机构班次
 */
@Data
public class ScheduleOverviewVO {

    private Long institutionId;

    private String institutionName;

    /** 库存批次明细 */
    private List<StockDetail> stockDetails;

    /** 疫苗维度汇总（含预警标志） */
    private List<VaccineSummary> vaccineSummaries;

    /** 临期预警批次 */
    private List<StockDetail> nearExpiryStocks;

    /** 低库存疫苗 */
    private List<VaccineSummary> lowStockVaccines;

    /** 本机构班次 */
    private List<ShiftVO> shifts;

    @Data
    public static class StockDetail {

        private Long stockId;

        private Long vaccineId;

        private String vaccineName;

        private Long manufacturerId;

        private String manufacturerName;

        private String batchNo;

        private Integer quantity;

        private Integer lockedQuantity;

        private LocalDate expiryDate;

        /** 状态：1=正常 2=临期预警 3=已过期锁定 4=已销毁 */
        private Integer status;
    }

    @Data
    public static class VaccineSummary {

        private Long vaccineId;

        private String vaccineName;

        /** 合计可用数量 */
        private Integer totalQuantity;

        /** 合计锁定数量 */
        private Integer totalLocked;

        /** 存在临期批次 */
        private Boolean nearExpiry;

        /** 合计可用 <= 低库存阈值 */
        private Boolean lowStock;

        /** 存在过期封锁批次 */
        private Boolean expiredBlocked;
    }
}
