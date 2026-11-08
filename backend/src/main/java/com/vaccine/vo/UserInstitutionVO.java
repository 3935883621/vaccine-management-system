package com.vaccine.vo;

import lombok.Data;

import java.util.List;

/**
 * 用户端机构查询视图：机构 + 该机构可预约疫苗（无正常批次库存的机构+疫苗组合隐藏）
 */
@Data
public class UserInstitutionVO {

    private Long institutionId;

    private String name;

    private String address;

    private String region;

    private String contactPhone;

    /** 该机构有正常批次库存（status=1 且 quantity>0）的疫苗简报 */
    private List<VaccineBrief> availableVaccines;

    @Data
    public static class VaccineBrief {

        private Long vaccineId;

        private String name;

        private String type;

        private String disease;

        /** 全程剂次 */
        private Integer dosesRequired;

        /** 该机构该疫苗正常批次可用数量合计 */
        private Integer totalQuantity;
    }
}
