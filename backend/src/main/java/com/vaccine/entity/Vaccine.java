package com.vaccine.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 疫苗表（疫苗品种信息）
 */
@Data
@TableName("vaccine")
public class Vaccine {

    @TableId(type = IdType.AUTO)
    private Long vaccineId;

    private String name;

    /** 疫苗类型：灭活/减毒/mRNA/重组蛋白 */
    private String type;

    /** 适用疾病 */
    private String disease;

    /** 全程剂次（>=1） */
    private Integer dosesRequired;

    /** 储存温度下限（℃） */
    private BigDecimal storageTempMin;

    /** 储存温度上限（℃） */
    private BigDecimal storageTempMax;

    /** 疫苗说明 */
    private String description;
}
