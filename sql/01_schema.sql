-- =============================================================================
-- 疫苗管理系统 数据库建表脚本（13 张表）
-- 依据：《.dev_docs/需求文档.md》v1.1 第 4.1 节数据需求
-- 目标库：vaccine_db（utf8mb4）
-- 脚本幂等：可重复执行（先 DROP 后 CREATE，重复执行不报错）
--
-- 约束落地说明：
-- 1. 复合外键：(batch.vaccine_id, batch.manufacturer_id) → vaccine_manufacturer，
--    (vaccine_stock.vaccine_id, vaccine_stock.manufacturer_id) → vaccine_manufacturer，
--    保证"批次/库存的生产商确为生产该疫苗的生产商"；
-- 2. 唯一约束：user.username/id_card、manufacturer.license_no/username、
--    nurse.employee_no、institution_admin.username、super_admin.username、
--    batch(manufacturer_id, batch_no)、appointment(user_id, shift_id)；
-- 3. CHECK 约束（MySQL 8.0.16+ 强制执行，本机 8.0.11 仅解析不强制，
--    已用 UNSIGNED 类型在数据库层面兜底非负，其余由应用层校验）。
-- =============================================================================

SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

-- 按依赖关系逆序删除，保证脚本幂等
DROP TABLE IF EXISTS appointment;
DROP TABLE IF EXISTS vaccine_stock;
DROP TABLE IF EXISTS supply;
DROP TABLE IF EXISTS shift;
DROP TABLE IF EXISTS batch;
DROP TABLE IF EXISTS vaccine_manufacturer;
DROP TABLE IF EXISTS nurse;
DROP TABLE IF EXISTS institution_admin;
DROP TABLE IF EXISTS institution;
DROP TABLE IF EXISTS vaccine;
DROP TABLE IF EXISTS manufacturer;
DROP TABLE IF EXISTS `user`;
DROP TABLE IF EXISTS super_admin;

SET FOREIGN_KEY_CHECKS = 1;

-- -----------------------------------------------------------------------------
-- 1. user 用户表（居民账号与信息）
-- -----------------------------------------------------------------------------
CREATE TABLE `user` (
  user_id    BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '用户ID',
  username   VARCHAR(50)  NOT NULL                COMMENT '登录用户名',
  password   VARCHAR(100) NOT NULL                COMMENT 'BCrypt 密码密文',
  real_name  VARCHAR(50)  NOT NULL                COMMENT '真实姓名',
  id_card    CHAR(18)     NOT NULL                COMMENT '身份证号',
  phone      VARCHAR(20)           DEFAULT NULL   COMMENT '联系电话',
  gender     TINYINT      NOT NULL                COMMENT '性别：1=男 2=女',
  birth_date DATE                  DEFAULT NULL   COMMENT '出生日期',
  PRIMARY KEY (user_id),
  UNIQUE KEY uk_user_username (username),
  UNIQUE KEY uk_user_id_card (id_card)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='用户表（居民账号与信息）';

-- -----------------------------------------------------------------------------
-- 2. manufacturer 生产商表（生产商信息与登录账号）
-- -----------------------------------------------------------------------------
CREATE TABLE manufacturer (
  manufacturer_id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '生产商ID',
  name            VARCHAR(100) NOT NULL                COMMENT '生产商名称',
  license_no      VARCHAR(50)  NOT NULL                COMMENT '生产许可证号',
  contact_phone   VARCHAR(20)           DEFAULT NULL   COMMENT '联系电话',
  address         VARCHAR(255)          DEFAULT NULL   COMMENT '地址',
  username        VARCHAR(50)  NOT NULL                COMMENT '登录用户名',
  password        VARCHAR(100) NOT NULL                COMMENT 'BCrypt 密码密文',
  PRIMARY KEY (manufacturer_id),
  UNIQUE KEY uk_manufacturer_license_no (license_no),
  UNIQUE KEY uk_manufacturer_username (username)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='生产商表（信息与登录账号）';

-- -----------------------------------------------------------------------------
-- 3. vaccine 疫苗表（疫苗品种信息）
-- -----------------------------------------------------------------------------
CREATE TABLE vaccine (
  vaccine_id       BIGINT UNSIGNED  NOT NULL AUTO_INCREMENT COMMENT '疫苗ID',
  name             VARCHAR(100) NOT NULL                COMMENT '疫苗名称',
  type             VARCHAR(20)  NOT NULL                COMMENT '疫苗类型：灭活/减毒/mRNA/重组蛋白',
  disease          VARCHAR(100)          DEFAULT NULL   COMMENT '适用疾病',
  doses_required   TINYINT UNSIGNED NOT NULL DEFAULT 1  COMMENT '全程剂次（>=1）',
  storage_temp_min DECIMAL(4,1)         DEFAULT NULL    COMMENT '储存温度下限（℃）',
  storage_temp_max DECIMAL(4,1)         DEFAULT NULL    COMMENT '储存温度上限（℃）',
  description      VARCHAR(255)         DEFAULT NULL    COMMENT '疫苗说明',
  PRIMARY KEY (vaccine_id),
  CONSTRAINT chk_vaccine_doses_required CHECK (doses_required >= 1)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='疫苗表（疫苗品种信息）';

-- -----------------------------------------------------------------------------
-- 4. vaccine_manufacturer 疫苗-生产商关联表（M:N 分解，联合主键）
-- -----------------------------------------------------------------------------
CREATE TABLE vaccine_manufacturer (
  vaccine_id      BIGINT UNSIGNED NOT NULL COMMENT '疫苗ID',
  manufacturer_id BIGINT UNSIGNED NOT NULL COMMENT '生产商ID',
  PRIMARY KEY (vaccine_id, manufacturer_id),
  KEY idx_vm_manufacturer (manufacturer_id),
  CONSTRAINT fk_vm_vaccine FOREIGN KEY (vaccine_id) REFERENCES vaccine (vaccine_id),
  CONSTRAINT fk_vm_manufacturer FOREIGN KEY (manufacturer_id) REFERENCES manufacturer (manufacturer_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='疫苗-生产商关联表（疫苗与生产商 M:N）';

-- -----------------------------------------------------------------------------
-- 5. institution 接种机构表
-- -----------------------------------------------------------------------------
CREATE TABLE institution (
  institution_id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '机构ID',
  name           VARCHAR(100) NOT NULL                COMMENT '机构名称',
  address        VARCHAR(255)          DEFAULT NULL   COMMENT '地址',
  contact_phone  VARCHAR(20)           DEFAULT NULL   COMMENT '联系电话',
  region         VARCHAR(50)           DEFAULT NULL   COMMENT '所属地区',
  PRIMARY KEY (institution_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='接种机构表';

-- -----------------------------------------------------------------------------
-- 6. nurse 护士表
-- -----------------------------------------------------------------------------
CREATE TABLE nurse (
  nurse_id       BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '护士ID',
  name           VARCHAR(50)  NOT NULL                COMMENT '护士姓名',
  employee_no    VARCHAR(20)  NOT NULL                COMMENT '工号',
  title          VARCHAR(50)           DEFAULT NULL   COMMENT '职称',
  institution_id BIGINT UNSIGNED NOT NULL             COMMENT '所属机构ID',
  phone          VARCHAR(20)           DEFAULT NULL   COMMENT '联系电话',
  PRIMARY KEY (nurse_id),
  UNIQUE KEY uk_nurse_employee_no (employee_no),
  KEY idx_nurse_institution (institution_id),
  CONSTRAINT fk_nurse_institution FOREIGN KEY (institution_id) REFERENCES institution (institution_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='护士表';

-- -----------------------------------------------------------------------------
-- 7. shift 班次表（护士排班；institution_id 冗余，由系统取护士所属机构自动写入）
-- -----------------------------------------------------------------------------
CREATE TABLE shift (
  shift_id         BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '班次ID',
  nurse_id         BIGINT UNSIGNED NOT NULL                COMMENT '护士ID',
  institution_id   BIGINT UNSIGNED NOT NULL                COMMENT '机构ID（冗余字段，系统自动写入）',
  shift_date       DATE            NOT NULL                COMMENT '班次日期',
  start_time       TIME            NOT NULL                COMMENT '开始时间',
  end_time         TIME            NOT NULL                COMMENT '结束时间',
  max_appointments INT UNSIGNED    NOT NULL                COMMENT '最大预约名额（>0）',
  current_count    INT UNSIGNED    NOT NULL DEFAULT 0      COMMENT '当前已预约人数',
  status           TINYINT         NOT NULL DEFAULT 1      COMMENT '状态：1=正常 3=已停诊',
  PRIMARY KEY (shift_id),
  KEY idx_shift_nurse (nurse_id),
  KEY idx_shift_institution (institution_id),
  CONSTRAINT fk_shift_nurse FOREIGN KEY (nurse_id) REFERENCES nurse (nurse_id),
  CONSTRAINT fk_shift_institution FOREIGN KEY (institution_id) REFERENCES institution (institution_id),
  CONSTRAINT chk_shift_max_appointments CHECK (max_appointments > 0),
  CONSTRAINT chk_shift_current_count CHECK (current_count >= 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='班次表（护士排班）';

-- -----------------------------------------------------------------------------
-- 8. batch 生产批次表（批次归属确定的生产商+疫苗组合）
-- -----------------------------------------------------------------------------
CREATE TABLE batch (
  batch_id            BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '批次ID',
  manufacturer_id     BIGINT UNSIGNED NOT NULL                COMMENT '生产商ID',
  vaccine_id          BIGINT UNSIGNED NOT NULL                COMMENT '疫苗ID',
  batch_no            VARCHAR(50)     NOT NULL                COMMENT '批次号',
  production_date     DATE            NOT NULL                COMMENT '生产日期',
  expiry_date         DATE            NOT NULL                COMMENT '有效期至',
  production_quantity INT UNSIGNED    NOT NULL                COMMENT '生产数量',
  PRIMARY KEY (batch_id),
  UNIQUE KEY uk_batch_manufacturer_batch_no (manufacturer_id, batch_no),
  CONSTRAINT fk_batch_vaccine_manufacturer FOREIGN KEY (vaccine_id, manufacturer_id)
    REFERENCES vaccine_manufacturer (vaccine_id, manufacturer_id),
  CONSTRAINT chk_batch_production_quantity CHECK (production_quantity >= 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='生产批次表（批次生产记录）';

-- -----------------------------------------------------------------------------
-- 9. supply 供货记录表（生产商向机构供货，机构凭此入库）
-- -----------------------------------------------------------------------------
CREATE TABLE supply (
  supply_id         BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '供货记录ID',
  batch_id          BIGINT UNSIGNED NOT NULL                COMMENT '批次ID',
  institution_id    BIGINT UNSIGNED NOT NULL                COMMENT '目标机构ID',
  quantity          INT UNSIGNED    NOT NULL                COMMENT '配送数量',
  received_quantity INT UNSIGNED    NOT NULL DEFAULT 0      COMMENT '已入库数量（<=quantity）',
  supply_date       DATE            NOT NULL                COMMENT '配送日期',
  status            TINYINT         NOT NULL DEFAULT 1      COMMENT '状态：1=待入库 2=已入库',
  PRIMARY KEY (supply_id),
  KEY idx_supply_institution (institution_id),
  CONSTRAINT fk_supply_batch FOREIGN KEY (batch_id) REFERENCES batch (batch_id),
  CONSTRAINT fk_supply_institution FOREIGN KEY (institution_id) REFERENCES institution (institution_id),
  CONSTRAINT chk_supply_quantity CHECK (quantity >= 0),
  CONSTRAINT chk_supply_received_quantity CHECK (received_quantity >= 0),
  CONSTRAINT chk_supply_received_le_quantity CHECK (received_quantity <= quantity)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='供货记录表（供货配送记录）';

-- -----------------------------------------------------------------------------
-- 10. vaccine_stock 库存表（机构疫苗库存，按批次；批次信息由 batch 与 supply 带入）
-- -----------------------------------------------------------------------------
CREATE TABLE vaccine_stock (
  stock_id        BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '库存ID',
  institution_id  BIGINT UNSIGNED NOT NULL                COMMENT '机构ID',
  supply_id       BIGINT UNSIGNED NOT NULL                COMMENT '供货记录ID',
  batch_id        BIGINT UNSIGNED NOT NULL                COMMENT '批次ID',
  vaccine_id      BIGINT UNSIGNED NOT NULL                COMMENT '疫苗ID',
  manufacturer_id BIGINT UNSIGNED NOT NULL                COMMENT '生产商ID',
  batch_no        VARCHAR(50)     NOT NULL                COMMENT '批次号（由 batch 带入）',
  quantity        INT UNSIGNED    NOT NULL                COMMENT '可用数量',
  locked_quantity INT UNSIGNED    NOT NULL DEFAULT 0      COMMENT '已锁定数量',
  production_date DATE            NOT NULL                COMMENT '生产日期（由 batch 带入）',
  expiry_date     DATE            NOT NULL                COMMENT '有效期至（由 batch 带入）',
  status          TINYINT         NOT NULL DEFAULT 1      COMMENT '状态：1=正常 2=临期预警 3=已过期锁定 4=已销毁',
  PRIMARY KEY (stock_id),
  KEY idx_stock_institution_vaccine (institution_id, vaccine_id),
  CONSTRAINT fk_stock_institution FOREIGN KEY (institution_id) REFERENCES institution (institution_id),
  CONSTRAINT fk_stock_supply FOREIGN KEY (supply_id) REFERENCES supply (supply_id),
  CONSTRAINT fk_stock_batch FOREIGN KEY (batch_id) REFERENCES batch (batch_id),
  CONSTRAINT fk_stock_vaccine_manufacturer FOREIGN KEY (vaccine_id, manufacturer_id)
    REFERENCES vaccine_manufacturer (vaccine_id, manufacturer_id),
  CONSTRAINT chk_stock_quantity CHECK (quantity >= 0),
  CONSTRAINT chk_stock_locked_quantity CHECK (locked_quantity >= 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='库存表（机构疫苗库存，按批次）';

-- -----------------------------------------------------------------------------
-- 11. appointment 预约表（核心业务表；唯一索引 (user_id, shift_id) 防同一班次重复预约）
-- -----------------------------------------------------------------------------
CREATE TABLE appointment (
  appointment_id   BIGINT UNSIGNED  NOT NULL AUTO_INCREMENT COMMENT '预约ID',
  user_id          BIGINT UNSIGNED  NOT NULL                COMMENT '用户ID',
  vaccine_id       BIGINT UNSIGNED  NOT NULL                COMMENT '疫苗ID',
  institution_id   BIGINT UNSIGNED  NOT NULL                COMMENT '机构ID',
  shift_id         BIGINT UNSIGNED  NOT NULL                COMMENT '班次ID',
  stock_id         BIGINT UNSIGNED  NOT NULL                COMMENT '锁定的库存记录ID',
  appointment_date DATE             NOT NULL                COMMENT '预约日期（=班次日期，系统自动写入）',
  dose_number      TINYINT UNSIGNED NOT NULL DEFAULT 1      COMMENT '剂次序号（第几剂）',
  vaccination_time DATETIME                  DEFAULT NULL   COMMENT '接种时间（确认接种时写入）',
  cancel_reason    VARCHAR(100)              DEFAULT NULL   COMMENT '取消原因（用户取消/班次停诊/疫苗批次过期）',
  status           TINYINT          NOT NULL DEFAULT 1      COMMENT '状态：1=待接种 2=已完成 3=已取消',
  PRIMARY KEY (appointment_id),
  UNIQUE KEY uk_appointment_user_shift (user_id, shift_id),
  KEY idx_appointment_user (user_id),
  KEY idx_appointment_shift (shift_id),
  CONSTRAINT fk_appointment_user FOREIGN KEY (user_id) REFERENCES `user` (user_id),
  CONSTRAINT fk_appointment_vaccine FOREIGN KEY (vaccine_id) REFERENCES vaccine (vaccine_id),
  CONSTRAINT fk_appointment_institution FOREIGN KEY (institution_id) REFERENCES institution (institution_id),
  CONSTRAINT fk_appointment_shift FOREIGN KEY (shift_id) REFERENCES shift (shift_id),
  CONSTRAINT fk_appointment_stock FOREIGN KEY (stock_id) REFERENCES vaccine_stock (stock_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='预约表（预约记录，核心业务表）';

-- -----------------------------------------------------------------------------
-- 12. institution_admin 机构管理员表（机构管理员账号）
-- -----------------------------------------------------------------------------
CREATE TABLE institution_admin (
  admin_id       BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '机构管理员ID',
  username       VARCHAR(50)  NOT NULL                COMMENT '登录用户名',
  password       VARCHAR(100) NOT NULL                COMMENT 'BCrypt 密码密文',
  real_name      VARCHAR(50)  NOT NULL                COMMENT '真实姓名',
  institution_id BIGINT UNSIGNED NOT NULL             COMMENT '所属机构ID',
  phone          VARCHAR(20)           DEFAULT NULL   COMMENT '联系电话',
  PRIMARY KEY (admin_id),
  UNIQUE KEY uk_institution_admin_username (username),
  KEY idx_institution_admin_institution (institution_id),
  CONSTRAINT fk_institution_admin_institution FOREIGN KEY (institution_id) REFERENCES institution (institution_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='机构管理员表（机构管理员账号）';

-- -----------------------------------------------------------------------------
-- 13. super_admin 超级管理员表（系统后台账号，无业务外键）
-- -----------------------------------------------------------------------------
CREATE TABLE super_admin (
  super_admin_id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '超级管理员ID',
  username       VARCHAR(50)  NOT NULL                COMMENT '登录用户名',
  password       VARCHAR(100) NOT NULL                COMMENT 'BCrypt 密码密文',
  real_name      VARCHAR(50)  NOT NULL                COMMENT '真实姓名',
  PRIMARY KEY (super_admin_id),
  UNIQUE KEY uk_super_admin_username (username)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='超级管理员表（系统后台账号）';
