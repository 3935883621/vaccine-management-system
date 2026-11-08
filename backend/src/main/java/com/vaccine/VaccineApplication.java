package com.vaccine;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * 疫苗管理系统后端入口
 */
@EnableScheduling
@MapperScan("com.vaccine.mapper")
@SpringBootApplication
public class VaccineApplication {

    public static void main(String[] args) {
        SpringApplication.run(VaccineApplication.class, args);
    }

    /**
     * 启动时校验数据源连接（第 0 步验收：后端成功连上 vaccine_db）
     */
    @Bean
    public CommandLineRunner datasourceCheck(JdbcTemplate jdbcTemplate) {
        return args -> {
            String dbName = jdbcTemplate.queryForObject("SELECT DATABASE()", String.class);
            System.out.println("==> 数据源连接成功，当前数据库: " + dbName);
        };
    }
}
