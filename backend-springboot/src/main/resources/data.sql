-- ================================================
-- 双录系统初始数据
-- ================================================

-- 1. 话术模板:R3 银行理财-平衡型客户
INSERT INTO dr_script_template
  (template_id, template_name, product_id, product_type, risk_level, version, status, effective_time, created_by)
VALUES
  ('TPL_FIN_R3_BAL', 'R3银行理财-平衡型客户销售话术', 'PROD_FIN_R3_001', 'BANK_FINANCE', 'R3', 'V1.0', 'PUBLISHED', CURRENT_TIMESTAMP, 'admin');

INSERT INTO dr_script_node
  (template_id, version, node_seq, node_type, node_title, speaker, script_content, required_duration_sec, trigger_action, next_node_rule)
VALUES
  ('TPL_FIN_R3_BAL', 'V1.0', 1, 'OPENING', '开场话术', 'AGENT',
   '您好,我是${agent_name},工号${agent_no}。根据监管要求,本次销售过程将全程录音录像,录像内容将作为日后争议处理依据。请问您是否同意?', 20, 'WAIT_CUSTOMER_AGREE', '客户明确同意后进入N02'),
  ('TPL_FIN_R3_BAL', 'V1.0', 2, 'IDENTITY', '身份核验', 'AGENT',
   '请出示您的身份证,并正对摄像头进行人脸识别,我们将进行身份核验。', 15, 'WAIT_IDENTITY_VERIFY', '核验通过后进入N03'),
  ('TPL_FIN_R3_BAL', 'V1.0', 3, 'RISK_CONFIRM', '风险评估确认', 'AGENT',
   '根据您的风险评估问卷结果,您的风险承受等级为${risk_level}型,可购买${risk_level}及以下风险等级产品。本次产品风险等级为R3,与您的评估结果匹配。', 25, 'AGENT_READ', '客户确认后进入N04'),
  ('TPL_FIN_R3_BAL', 'V1.0', 4, 'PRODUCT_INTRO', '产品介绍', 'AGENT',
   '本次购买的${product_name}为非保本浮动收益型理财,投资范围为国债、金融债、同业存单、货币市场工具等。业绩比较基准为${benchmark}。', 60, 'AGENT_READ', '朗读完成后进入N05'),
  ('TPL_FIN_R3_BAL', 'V1.0', 5, 'RETURN_DISCLOSURE', '收益说明', 'AGENT',
   '请注意,业绩比较基准不代表实际收益,过去业绩不预示未来表现。本产品为非保本浮动收益,您可能无法收回全部本金。', 30, 'AGENT_READ', '客户确认后进入N06'),
  ('TPL_FIN_R3_BAL', 'V1.0', 6, 'RISK_DISCLOSURE', '风险揭示', 'AGENT',
   '请您特别注意,本产品存在本金损失风险。最不利情况下,您可能损失全部本金。产品投资过程中可能面临市场风险、流动性风险、信用风险等。', 45, 'AGENT_MUST_READ_FULL', '完整朗读+客户确认后进入N07'),
  ('TPL_FIN_R3_BAL', 'V1.0', 7, 'FEE_DISCLOSURE', '费用说明', 'AGENT',
   '本产品费率说明:管理费1.5%/年,托管费0.2%/年,申购费1%,赎回费0.5%(持有期<30天)。', 30, 'AGENT_READ', '客户确认后进入N08'),
  ('TPL_FIN_R3_BAL', 'V1.0', 8, 'LIQUIDITY_DISCLOSURE', '流动性说明', 'AGENT',
   '本产品封闭期为12个月,封闭期内不可赎回,只能到期赎回或者转让。', 25, 'AGENT_READ', '客户确认后进入N09'),
  ('TPL_FIN_R3_BAL', 'V1.0', 9, 'CUSTOMER_CONFIRM', '客户确认', 'AGENT',
   '请问您是否清楚上述所有信息?是否自愿购买本产品?请您明确回答:是或者清楚。', 15, 'WAIT_CUSTOMER_AGREE', '客户明确同意后进入N10'),
  ('TPL_FIN_R3_BAL', 'V1.0', 10, 'SIGN_PROMPT', '签字提示', 'AGENT',
   '请您在下方电子屏签名,签名确认后将无法撤销本次交易。', 20, 'WAIT_SIGN', '签字完成后进入N11'),
  ('TPL_FIN_R3_BAL', 'V1.0', 11, 'CLOSING', '结束语', 'AGENT',
   '本次销售过程已结束,录音录像文件将按规定保存。', 10, 'AGENT_READ', '流程结束');

-- 话术节点-合规关键词
INSERT INTO dr_script_keyword (template_id, version, node_seq, keyword, priority, match_type) VALUES
  ('TPL_FIN_R3_BAL', 'V1.0', 1, '录音录像', 'P0', 'EXACT'),
  ('TPL_FIN_R3_BAL', 'V1.0', 1, '依据', 'P0', 'EXACT'),
  ('TPL_FIN_R3_BAL', 'V1.0', 4, '非保本', 'P0', 'EXACT'),
  ('TPL_FIN_R3_BAL', 'V1.0', 4, '浮动收益', 'P0', 'EXACT'),
  ('TPL_FIN_R3_BAL', 'V1.0', 4, '业绩比较基准', 'P0', 'EXACT'),
  ('TPL_FIN_R3_BAL', 'V1.0', 5, '不代表', 'P0', 'EXACT'),
  ('TPL_FIN_R3_BAL', 'V1.0', 5, '不预示', 'P0', 'EXACT'),
  ('TPL_FIN_R3_BAL', 'V1.0', 6, '本金损失', 'P0', 'EXACT'),
  ('TPL_FIN_R3_BAL', 'V1.0', 6, '最不利', 'P0', 'EXACT'),
  ('TPL_FIN_R3_BAL', 'V1.0', 6, '全部本金', 'P0', 'EXACT'),
  ('TPL_FIN_R3_BAL', 'V1.0', 7, '管理费', 'P0', 'EXACT'),
  ('TPL_FIN_R3_BAL', 'V1.0', 7, '托管费', 'P0', 'EXACT'),
  ('TPL_FIN_R3_BAL', 'V1.0', 8, '封闭期', 'P0', 'EXACT'),
  ('TPL_FIN_R3_BAL', 'V1.0', 8, '不可赎回', 'P0', 'EXACT'),
  ('TPL_FIN_R3_BAL', 'V1.0', 10, '签名', 'P0', 'EXACT'),
  ('TPL_FIN_R3_BAL', 'V1.0', 10, '无法撤销', 'P0', 'EXACT');

-- 2. 话术模板:R1 货币基金-谨慎型客户
INSERT INTO dr_script_template
  (template_id, template_name, product_id, product_type, risk_level, version, status, effective_time, created_by)
VALUES
  ('TPL_FUND_R1_CONS', 'R1货币基金-谨慎型客户销售话术', 'PROD_FUND_R1_001', 'FUND', 'R1', 'V1.0', 'PUBLISHED', CURRENT_TIMESTAMP, 'admin');

INSERT INTO dr_script_node (template_id, version, node_seq, node_type, node_title, speaker, script_content, required_duration_sec, trigger_action, next_node_rule) VALUES
  ('TPL_FUND_R1_CONS', 'V1.0', 1, 'OPENING', '开场话术', 'AGENT', '您好,我是${agent_name},工号${agent_no}。本次销售过程将全程录音录像。', 15, 'WAIT_CUSTOMER_AGREE', '客户同意后进入N02'),
  ('TPL_FUND_R1_CONS', 'V1.0', 2, 'IDENTITY', '身份核验', 'AGENT', '请出示身份证并正对摄像头做人脸识别。', 15, 'WAIT_IDENTITY_VERIFY', '核验通过后进入N03'),
  ('TPL_FUND_R1_CONS', 'V1.0', 3, 'RISK_CONFIRM', '风险评估确认', 'AGENT', '您的风险评估结果为谨慎型,可购买R1低风险产品。本次产品为R1级货币基金。', 20, 'AGENT_READ', '客户确认后进入N04'),
  ('TPL_FUND_R1_CONS', 'V1.0', 4, 'PRODUCT_INTRO', '产品介绍', 'AGENT', '本基金为货币市场基金,主要投资于短期货币工具。', 30, 'AGENT_READ', '朗读完成后进入N05'),
  ('TPL_FUND_R1_CONS', 'V1.0', 5, 'RISK_DISCLOSURE', '风险揭示', 'AGENT', '货币基金存在本金损失风险,极端情况下可能损失全部本金。', 30, 'AGENT_MUST_READ_FULL', '完整朗读后进入N06'),
  ('TPL_FUND_R1_CONS', 'V1.0', 6, 'CUSTOMER_CONFIRM', '客户确认', 'AGENT', '请问您是否清楚上述信息?是否自愿购买?', 15, 'WAIT_CUSTOMER_AGREE', '客户同意后进入N07'),
  ('TPL_FUND_R1_CONS', 'V1.0', 7, 'SIGN_PROMPT', '签字提示', 'AGENT', '请签名,签名后无法撤销。', 15, 'WAIT_SIGN', '签字完成后流程结束'),
  ('TPL_FUND_R1_CONS', 'V1.0', 8, 'CLOSING', '结束语', 'AGENT', '本次销售过程已结束。', 5, 'AGENT_READ', '结束');

INSERT INTO dr_script_keyword (template_id, version, node_seq, keyword, priority, match_type) VALUES
  ('TPL_FUND_R1_CONS', 'V1.0', 1, '录音录像', 'P0', 'EXACT'),
  ('TPL_FUND_R1_CONS', 'V1.0', 5, '本金损失', 'P0', 'EXACT'),
  ('TPL_FUND_R1_CONS', 'V1.0', 5, '全部本金', 'P0', 'EXACT'),
  ('TPL_FUND_R1_CONS', 'V1.0', 7, '签名', 'P0', 'EXACT'),
  ('TPL_FUND_R1_CONS', 'V1.0', 7, '无法撤销', 'P0', 'EXACT');

-- 3. 质检规则
INSERT INTO dr_quality_rule (rule_code, rule_name, rule_type, description, severity, enabled, rule_config) VALUES
  ('R001', '风险揭示必含本金损失', 'KEYWORD_REQUIRED', '风险揭示节点必须出现"本金损失"和"最不利"关键词', 'P0', TRUE, '{"keywords":["本金损失","最不利"]}'),
  ('R002', '客户明确同意', 'CUSTOMER_RESPONSE', '客户必须明确回应"是"或"清楚"或"明白"', 'P0', TRUE, '{"keywords":["是","清楚","明白","同意"]}'),
  ('R003', '签字前必读提示', 'KEYWORD_REQUIRED', '签字前必须朗读"签名后无法撤销"', 'P0', TRUE, '{"keywords":["签名","无法撤销"]}'),
  ('R101', '禁止保本承诺', 'KEYWORD_FORBIDDEN', '禁止出现"保本""保证收益""稳赚不赔"', 'P0', TRUE, '{"forbidden":["保本","保证收益","稳赚不赔"]}'),
  ('R102', '禁止无风险', 'KEYWORD_FORBIDDEN', '禁止出现"无风险""零风险"', 'P0', TRUE, '{"forbidden":["无风险","零风险"]}'),
  ('R201', '风险揭示时长', 'DURATION_CHECK', '风险揭示节点朗读时长≥30秒', 'P0', TRUE, '{"minDuration":30}'),
  ('R301', '风险评估匹配', 'STATE_CHECK', '风险评估未通过不能进入销售环节', 'P0', TRUE, '{}'),
  ('R302', '签字提示完整', 'STATE_CHECK', '电子签名前必须播放签字提示话术', 'P0', TRUE, '{}');

-- 4. 风评问卷样例数据
INSERT INTO dr_risk_questionnaire (customer_id, score, risk_level, answers) VALUES
  ('CUST_2026_0001', 75, 'R3', '{"q1":"A","q2":"B","q3":"C","q4":"B","q5":"A"}'),
  ('CUST_2026_0002', 35, 'R1', '{"q1":"A","q2":"A","q3":"A","q4":"A","q5":"A"}'),
  ('CUST_2026_0003', 90, 'R5', '{"q1":"E","q2":"E","q3":"E","q4":"E","q5":"E"}');
