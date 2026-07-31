import request from '@/utils/request'

// ============== 会话相关 ==============

// 创建会话
export const createSession = (data) => request.post('/api/session/create', data)

// 获取会话详情
export const getSession = (sessionId) => request.get(`/api/session/${sessionId}`)

// 获取当前话术节点
export const getCurrentNode = (sessionId) => request.get(`/api/session/${sessionId}/current-node`)

// 提交节点 + 质检
export const submitNode = (sessionId, data) => request.post(`/api/session/${sessionId}/submit-node`, data)

// 风险评估
export const evaluateRisk = (sessionId, score) => request.post(`/api/session/${sessionId}/risk-evaluate`, { score })

// 启动视频
export const startVideo = (sessionId) => request.post(`/api/session/${sessionId}/video/start`, {})

// 完成视频(Saga)
export const completeVideo = (sessionId, duration) => request.post(`/api/session/${sessionId}/video/complete`, { duration })

// 签字
export const sign = (sessionId) => request.post(`/api/session/${sessionId}/sign`, {})

// 暂停
export const pauseSession = (sessionId) => request.post(`/api/session/${sessionId}/pause`, {})

// 断点续录
export const resumeSession = (sessionId) => request.get(`/api/session/${sessionId}/resume`)

// 节点明细
export const getSessionNodes = (sessionId) => request.get(`/api/session/${sessionId}/nodes`)

// ============== 事件 ==============

// 事件流
export const getEvents = (sessionId) => request.get(`/api/event/${sessionId}`)

// ============== 质检 ==============

// 规则列表
export const getQualityRules = () => request.get('/api/quality/rules')

// 报告
export const getQualityReport = (sessionId) => request.get(`/api/quality/report/${sessionId}`)

// 手动触发生成报告
export const generateReport = (sessionId) => request.post(`/api/quality/report/${sessionId}/generate`, {})

// ============== 话术 ==============

// 模板列表
export const getScriptTemplates = (params) => request.get('/api/script/templates', { params })

// 加载话术
export const loadScript = (params) => request.get('/api/script/load', { params })

// 模板详情
export const getTemplate = (templateId, version) => request.get(`/api/script/template/${templateId}`, { params: { version } })

// ============== 风评 ==============

// 客户风险评估
export const getRiskAssessment = (customerId) => request.get(`/api/risk/${customerId}`)

// 提交风险评估
export const submitRisk = (data) => request.post('/api/risk/submit', data)

// 产品匹配
export const matchProduct = (customerLevel, productLevel) => request.get('/api/risk/match', {
  params: { customerLevel, productLevel }
})
