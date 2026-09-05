-- =============================================
-- 接口日志监控系统 - 数据库初始化脚本
-- 数据库：MySQL 5.7+ / 8.0+
-- =============================================

CREATE DATABASE IF NOT EXISTS log_monitor
    DEFAULT CHARACTER SET utf8mb4
    DEFAULT COLLATE utf8mb4_unicode_ci;

USE log_monitor;

-- 接口日志表
DROP TABLE IF EXISTS interface_log;
CREATE TABLE interface_log (
    id INT AUTO_INCREMENT PRIMARY KEY COMMENT '主键ID',
    trace_id VARCHAR(64) DEFAULT NULL COMMENT '链路追踪ID',
    interface_name VARCHAR(255) DEFAULT NULL COMMENT '接口名称（类名+方法名）',
    method VARCHAR(100) DEFAULT NULL COMMENT '方法名',
    request_params TEXT COMMENT '请求参数',
    response_data TEXT COMMENT '返回数据',
    cost_time INT DEFAULT NULL COMMENT '耗时（毫秒）',
    status VARCHAR(20) DEFAULT NULL COMMENT '状态：SUCCESS/ERROR/TIMEOUT',
    error_msg VARCHAR(1000) DEFAULT NULL COMMENT '错误信息',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    caller_ip VARCHAR(50) DEFAULT NULL COMMENT '调用方IP',
    env VARCHAR(20) DEFAULT 'dev' COMMENT '环境标识：dev/test/prod',
    INDEX idx_create_time (create_time),
    INDEX idx_status (status),
    INDEX idx_interface_name (interface_name(100))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='接口日志表';

-- 告警记录表（预留，当前用内存存储）
DROP TABLE IF EXISTS alarm_record;
CREATE TABLE alarm_record (
    id INT AUTO_INCREMENT PRIMARY KEY COMMENT '主键ID',
    level VARCHAR(20) NOT NULL COMMENT '告警级别：INFO/WARN/CRITICAL',
    title VARCHAR(200) NOT NULL COMMENT '告警标题',
    message TEXT COMMENT '告警详细信息',
    trigger_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '触发时间',
    INDEX idx_trigger_time (trigger_time),
    INDEX idx_level (level)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='告警记录表';