import request from '@/utils/request'

export const startAIRisk = (data) => request.post('/ai-risk/start', data)
export const stopAIRisk = (sessionId) => request.post(`/ai-risk/stop/${sessionId}`)
export const detectAudioRisk = (data) => request.post('/ai-risk/audio', data)
export const detectVideoRisk = (data) => request.post('/ai-risk/video', data)
export const detectBehaviorRisk = (data) => request.post('/ai-risk/behavior', data)
export const detectInfraRisk = (data) => request.post('/ai-risk/infra', data)
export const getRiskState = (sessionId) => request.get(`/ai-risk/state/${sessionId}`)
export const getRiskTypes = () => request.get('/ai-risk/risk-types')
