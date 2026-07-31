import request from '@/utils/request'

// 统计信息
export const getSagaStatistics = () => request.get('/api/saga/statistics')

// 分页列表
export const getSagaList = (params) => request.get('/api/saga/list', { params })

// 所有 Saga 类型
export const getSagaTypes = () => request.get('/api/saga/types')

// 详情
export const getSagaDetail = (sagaId) => request.get(`/api/saga/${sagaId}`)

// 时间线
export const getSagaTimeline = (sagaId) => request.get(`/api/saga/${sagaId}/timeline`)

// 待人工处理
export const getPendingManual = () => request.get('/api/saga/pending-manual')

// 手动重试
export const retrySaga = (sagaId, operator) => request.post(`/api/saga/${sagaId}/retry`, { operator })

// 取消
export const cancelSaga = (sagaId, reason, operator) => request.post(`/api/saga/${sagaId}/cancel`, { reason, operator })

// 强制完成
export const forceCompleteSaga = (sagaId, reason, operator) => request.post(`/api/saga/${sagaId}/force-complete`, { reason, operator })
