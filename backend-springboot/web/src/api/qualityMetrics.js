import request from '@/utils/request'

export const startQuality = (sessionId) => request.post(`/quality-metrics/start/${sessionId}`)
export const getQuality = (sessionId) => request.get(`/quality-metrics/${sessionId}`)
export const updateQuality = (sessionId, sample) => request.post(`/quality-metrics/${sessionId}/sample`, sample)
export const tickQuality = (sessionId, elapsed) => request.post(`/quality-metrics/${sessionId}/simulate-tick?elapsed=${elapsed}`)
export const stopQuality = (sessionId) => request.delete(`/quality-metrics/${sessionId}`)
