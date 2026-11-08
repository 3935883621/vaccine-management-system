package com.vaccine.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

/**
 * 疫苗-生产商关联表（M:N 分解，联合主键）
 */
@Data
@TableName("vaccine_manufacturer")
public class VaccineManufacturer {

    private Long vaccineId;

    private Long manufacturerId;
}
