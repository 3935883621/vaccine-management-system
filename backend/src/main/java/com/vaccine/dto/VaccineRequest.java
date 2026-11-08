package com.vaccine.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

/**
 * 疫苗创建/修改请求（manufacturerIds 由超管指定；生产商端忽略，自动关联本厂）
 */
@Data
public class VaccineRequest {

    @NotBlank(message = "疫苗名称不能为空")
    private String name;

    @NotBlank(message = "疫苗类型不能为空")
    private String type;

    private String disease;

    @NotNull(message = "全程剂次不能为空")
    @Min(value = 1, message = "全程剂次至少为 1")
    private Integer dosesRequired;

    private BigDecimal storageTempMin;

    private BigDecimal storageTempMax;

    private String description;

    /** 关联生产商 ID 列表（仅超管端使用，落库 vaccine_manufacturer） */
    private List<Long> manufacturerIds;
}
