-- ================================================
-- 双录系统核心表结构
-- ================================================

-- 双录会话主表
CREATE TABLE IF NOT EXISTS dr_session (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    session_id VARCHAR(32) UNIQUE NOT NULL,
    customer_id VARCHAR(32) NOT NULL,
    customer_name VARCHAR(64),
    product_id VARCHAR(32) NOT NULL,
    product_name VARCHAR(128),
    channel VARCHAR(16) NOT NULL,
    current_state VARCHAR(32) NOT NULL,
    current_node_seq INT DEFAULT 0,
    risk_level VARCHAR(8),
    risk_score INT,
    script_template_id VARCHAR(32),
    script_version VARCHAR(16),
    video_file_id VARCHAR(64),
    video_hash VARCHAR(64),
    sign_image_hash VARCHAR(64),
    chain_tx_hash VARCHAR(128),
    chain_block_height BIGINT,
    chain_cert_no VARCHAR(64),
    order_id VARCHAR(32),
    order_amount DECIMAL(15,2),
    quality_report_id VARCHAR(32),
    final_status VARCHAR(16),
    remark VARCHAR(512),
    version INT NOT NULL DEFAULT 0,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    completed_at TIMESTAMP
);
CREATE INDEX idx_session_customer ON dr_session(customer_id);
CREATE INDEX idx_session_state ON dr_session(current_state);
CREATE INDEX idx_session_created ON dr_session(created_at);

-- 节点执行明细
CREATE TABLE IF NOT EXISTS dr_session_node (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    session_id VARCHAR(32) NOT NULL,
    node_seq INT NOT NULL,
    node_type VARCHAR(32) NOT NULL,
    node_title VARCHAR(64),
    script_content TEXT,
    customer_response TEXT,
    quality_status VARCHAR(16),
    quality_message VARCHAR(512),
    missing_keywords VARCHAR(512),
    retry_count INT DEFAULT 0,
    started_at TIMESTAMP,
    completed_at TIMESTAMP,
    UNIQUE (session_id, node_seq)
);
CREATE INDEX idx_node_session ON dr_session_node(session_id);

-- 话术模板主表
CREATE TABLE IF NOT EXISTS dr_script_template (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    template_id VARCHAR(32) UNIQUE NOT NULL,
    template_name VARCHAR(128) NOT NULL,
    product_id VARCHAR(32) NOT NULL,
    product_type VARCHAR(32) NOT NULL,
    risk_level VARCHAR(8) NOT NULL,
    version VARCHAR(16) NOT NULL,
    status VARCHAR(16) NOT NULL,
    effective_time TIMESTAMP NOT NULL,
    expire_time TIMESTAMP,
    created_by VARCHAR(32) DEFAULT 'admin',
    reviewed_by VARCHAR(32),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE (template_id, version)
);
CREATE INDEX idx_template_product ON dr_script_template(product_id, risk_level);

-- 话术节点
CREATE TABLE IF NOT EXISTS dr_script_node (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    template_id VARCHAR(32) NOT NULL,
    version VARCHAR(16) NOT NULL,
    node_seq INT NOT NULL,
    node_type VARCHAR(32) NOT NULL,
    node_title VARCHAR(64) NOT NULL,
    speaker VARCHAR(16) NOT NULL,
    script_content TEXT NOT NULL,
    required_duration_sec INT DEFAULT 0,
    trigger_action VARCHAR(32),
    next_node_rule VARCHAR(256),
    UNIQUE (template_id, version, node_seq)
);

-- 话术节点-合规关键词关联
CREATE TABLE IF NOT EXISTS dr_script_keyword (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    template_id VARCHAR(32) NOT NULL,
    version VARCHAR(16) NOT NULL,
    node_seq INT NOT NULL,
    keyword VARCHAR(64) NOT NULL,
    priority VARCHAR(8) NOT NULL,
    match_type VARCHAR(16) DEFAULT 'EXACT'
);
CREATE INDEX idx_keyword_node ON dr_script_keyword(template_id, version, node_seq);

-- 质检规则
CREATE TABLE IF NOT EXISTS dr_quality_rule (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    rule_code VARCHAR(32) UNIQUE NOT NULL,
    rule_name VARCHAR(128) NOT NULL,
    rule_type VARCHAR(32) NOT NULL,
    description VARCHAR(512),
    severity VARCHAR(8) NOT NULL,
    enabled BOOLEAN DEFAULT TRUE,
    rule_config TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- 质检报告
CREATE TABLE IF NOT EXISTS dr_quality_report (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    report_id VARCHAR(32) UNIQUE NOT NULL,
    session_id VARCHAR(32) NOT NULL,
    rule_version VARCHAR(16) NOT NULL,
    model_version VARCHAR(16) NOT NULL DEFAULT 'v1.0',
    total_nodes INT NOT NULL DEFAULT 0,
    passed_nodes INT NOT NULL DEFAULT 0,
    failed_nodes INT NOT NULL DEFAULT 0,
    blocked_count INT DEFAULT 0,
    alert_count INT DEFAULT 0,
    p0_missing TEXT,
    p1_missing TEXT,
    final_status VARCHAR(16) NOT NULL,
    detail_json TEXT,
    generated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX idx_report_session ON dr_quality_report(session_id);

-- 事件溯源日志
CREATE TABLE IF NOT EXISTS dr_event_log (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    event_id VARCHAR(48) UNIQUE NOT NULL,
    session_id VARCHAR(32) NOT NULL,
    aggregate_type VARCHAR(32) NOT NULL,
    aggregate_id VARCHAR(64) NOT NULL,
    event_type VARCHAR(48) NOT NULL,
    sequence_no BIGINT NOT NULL,
    payload TEXT NOT NULL,
    occurred_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    processed_at TIMESTAMP
);
CREATE INDEX idx_event_session ON dr_event_log(session_id, sequence_no);
CREATE INDEX idx_event_aggregate ON dr_event_log(aggregate_type, aggregate_id);

-- 风险评估问卷
CREATE TABLE IF NOT EXISTS dr_risk_questionnaire (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    questionnaire_id VARCHAR(32) UNIQUE NOT NULL,
    customer_id VARCHAR(32) NOT NULL,
    score INT NOT NULL,
    risk_level VARCHAR(8) NOT NULL,
    answers TEXT,
    assess_time TIMESTAMP,
    expire_time TIMESTAMP,
    version INT DEFAULT 1,
    evaluated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX idx_risk_customer_time ON dr_risk_questionnaire(customer_id, assess_time DESC);
CREATE INDEX idx_risk_expire ON dr_risk_questionnaire(expire_time);

-- Saga 事务日志
CREATE TABLE IF NOT EXISTS dr_saga_log (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    saga_id VARCHAR(48) UNIQUE NOT NULL,
    session_id VARCHAR(32) NOT NULL,
    saga_type VARCHAR(32) NOT NULL,
    current_step VARCHAR(32) NOT NULL,
    state VARCHAR(16) NOT NULL,
    payload TEXT,
    error_message VARCHAR(1024),
    started_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    completed_at TIMESTAMP
);
CREATE INDEX idx_saga_session ON dr_saga_log(session_id);

-- 视频元数据
CREATE TABLE IF NOT EXISTS dr_video (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    video_id VARCHAR(64) UNIQUE NOT NULL,
    session_id VARCHAR(32) NOT NULL,
    file_path VARCHAR(512),
    file_size BIGINT,
    duration_sec INT,
    sha256 VARCHAR(64),
    encrypted BOOLEAN DEFAULT TRUE,
    segments INT DEFAULT 0,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX idx_video_session ON dr_video(session_id);
