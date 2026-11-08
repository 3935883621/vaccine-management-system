package com.vaccine.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 健康检查接口（第 0 步验收：GET /api/ping 返回 200）
 */
@RestController
@RequestMapping("/api")
public class PingController {

    @GetMapping("/ping")
    public Map<String, Object> ping() {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("status", "ok");
        body.put("service", "vaccine-backend");
        body.put("time", LocalDateTime.now().toString());
        return body;
    }
}
