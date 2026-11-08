package com.vaccine.vo;

import lombok.Data;

import java.time.LocalDate;
import java.util.List;

/**
 * 机构端库存预警看板（需求 5.4）：临期列表（标黄）、过期列表（红色封锁）、低库存列表（合计 ≤ 阈值）。
 */
@Data
public class StockAlertVO {

    private Long institutionId;

    private String institutionName;

    /** 预警生成日期 */
    private LocalDate scanDate;

    /** 临期批次（status=2，或 status=1 且距过期 ≤ 阈值天）——前端标黄 */
    private List<StockItem> nearExpiryStocks;

    /** 过期批次（status=3，或已过有效期）——前端红色封锁 */
    private List<StockItem> expiredStocks;

    /** 低库存疫苗（某疫苗全部可出库批次 quantity 合计 ≤ 阈值）——提示补货 */
    private List<LowStockItem> lowStockVaccines;

    /** 预警批次明细 */
    @Data
    public static class StockItem {

        private Long stockId;

        private Long vaccineId;

        private String vaccineName;

        private String batchNo;

        private String manufacturerName;

        private Integer quantity;

        private Integer lockedQuantity;

        private LocalDate productionDate;

        private LocalDate expiryDate;

        /** 距过期天数（负数=已过期） */
        private Long daysToExpiry;

        /** 库存状态：1=正常 2=临期预警 3=已过期锁定 4=已销毁 */
        private Integer status;
    }

    /** 低库存疫苗汇总 */
    @Data
    public static class LowStockItem {

        private Long vaccineId;

        private String vaccineName;

        /** 可出库批次 quantity 合计 */
        private Integer totalQuantity;

        /** 低库存阈值 */
        private Integer threshold;
    }
}
