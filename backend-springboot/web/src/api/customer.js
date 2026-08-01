import request from '@/utils/request'

// 客户 H5 API
export const joinSession = (data) => request.post('/customer-h5/join', data)
export const deviceDiagnose = (data) => request.post('/customer-h5/diagnose', data)
export const submitSignature = (data) => request.post('/customer-h5/signature', data)
export const updateProgress = (data) => request.post('/customer-h5/progress', data)
export const submitRating = (data) => request.post('/customer-h5/rating', data)
export const leaveSession = (sessionId) => request.post(`/customer-h5/leave/${sessionId}`)
export const getSession = (sessionId) => request.get(`/customer-h5/session/${sessionId}`)
export const getChurn = (sessionId) => request.get(`/customer-h5/churn/${sessionId}`)

// 坐席辅助 API
export const getAgentDashboard = (sessionId, currentNode, elapsed) =>
  request.get(`/customer-h5/agent/dashboard/${sessionId}`, {
    params: { currentNode, elapsed }
  })
export const getAgentScripts = (sessionId, currentNode, mood) =>
  request.get('/customer-h5/agent/scripts', {
    params: { sessionId, currentNode, mood }
  })
export const getUrgeScript = (sessionId, idleSeconds) =>
  request.get('/customer-h5/agent/urge', { params: { sessionId, idleSeconds } })
export const getCalmScript = (sessionId) =>
  request.get(`/customer-h5/agent/calm/${sessionId}`)
export const getRetentionActions = (sessionId) =>
  request.get(`/customer-h5/agent/retention/${sessionId}`)
