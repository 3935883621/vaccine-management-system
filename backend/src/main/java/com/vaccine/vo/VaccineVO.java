package com.vaccine.vo;

import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

/**
 * 疫苗视图（含关联生产商列表；生产商视角含 mine 标记）
 */
@Data
public class VaccineVO {

    private Long vaccineId;

    private String name;

    private String type;

    private String disease;

    private Integer dosesRequired;

    private BigDecimal storageTempMin;

    private BigDecimal storageTempMax;

    private String description;

    /** 关联生产商列表 */
    private List<ManufacturerRef> manufacturers;

    /** 是否本厂关联（生产商视角，其他角色为 null） */
    private Boolean mine;

    @Data
    public static class ManufacturerRef {

        private Long manufacturerId;

        private String name;
    }
}
