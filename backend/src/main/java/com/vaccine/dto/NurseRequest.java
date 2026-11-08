package com.vaccine.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 护士创建/修改请求（institution_id 由系统取管理员所属机构自动写入）
 */
@Data
public class NurseRequest {

    @NotBlank(message = "护士姓名不能为空")
    private String name;

    @NotBlank(message = "工号不能为空")
    private String employeeNo;

    private String title;

    private String phone;
}
