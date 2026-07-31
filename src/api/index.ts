import axios, { type AxiosResponse } from 'axios'
import type {
  Business, StartBusinessRequest, NodeResult, QaResult,
  ScriptTemplate, ComplianceHit, OverviewData, NodeCode
} from '@/types'

const http = axios.create({
  baseURL: '/api/v1',
  timeout: 30000,
  headers: { 'Content-Type': 'application/json' }
})

// 响应拦截器：统一处理错误
http.interceptors.response.use(
  (r) => r,
  (err) => {
    const msg = err.response?.data?.message || err.message || '请求失败'
    console.error('[API Error]', msg, err.response?.data)
    return Promise.reject(new Error(msg))
  }
)

// ===== 录制主流程 =====
export const recordingApi = {
  startBusiness: (req: StartBusinessRequest) =>
    http.post<Business>('/recording/start', req).then(r => r.data),

  loadScript: (businessId: string, productId: string) =>
    http.post<ScriptTemplate>('/recording/script/load',
      null, { params: { businessId, productId } }).then(r => r.data),

  assessRisk: (businessId: string, customerIdHash: string) =>
    http.post<any>('/recording/risk/assess', null,
      { params: { businessId, customerIdHash } }).then(r => r.data),

  startRecording: (businessId: string) =>
    http.post<void>('/recording/begin', null, { params: { businessId } }),

  completeNode: (req: { businessId: string; recId: string; node: NodeCode; asrText: string }) =>
    http.post<NodeResult>('/recording/node/complete', req).then(r => r.data),

  finalize: (businessId: string, recId: string, fullAsrText: string) =>
    http.post<QaResult>('/recording/finalize', null,
      { params: { businessId, recId, fullAsrText } }).then(r => r.data),

  signAndArchive: (businessId: string) =>
    http.post<void>('/recording/sign', null, { params: { businessId } }),

  overview: (businessId: string) =>
    http.get<OverviewData>(`/recording/overview/${businessId}`).then(r => r.data)
}

// ===== 话术 =====
export const scriptApi = {
  getScript: (productId: string) =>
    http.get<ScriptTemplate>(`/script/${productId}`).then(r => r.data),

  allScripts: () =>
    http.get<Record<string, ScriptTemplate>>('/script/all').then(r => r.data),

  consistency: () =>
    http.get<string[]>('/script/consistency').then(r => r.data),

  isFrozen: (productId: string) =>
    http.get<boolean>(`/script/frozen/${productId}`).then(r => r.data)
}

// ===== 通用话术配置 =====
export const scriptConfigApi = {
  global: () =>
    http.get<any>('/script-config/global').then(r => r.data),

  product: (productId: string, productType?: string) =>
    http.get<ScriptTemplate>('/script-config/product/' + productId,
      { params: { productType } }).then(r => r.data),

  productSource: (productId: string, productType?: string) =>
    http.get<any>('/script-config/product/' + productId + '/source',
      { params: { productType } }).then(r => r.data),

  all: () =>
    http.get<Record<string, ScriptTemplate>>('/script-config/all').then(r => r.data),

  addForbiddenPhrase: (phrase: string) =>
    http.post<any>('/script-config/forbidden-phrase', null, { params: { phrase } }).then(r => r.data),

  forbiddenPhrases: () =>
    http.get<string[]>('/script-config/forbidden-phrases').then(r => r.data)
}

// ===== 合规 =====
export const complianceApi = {
  scan: (text: string) =>
    http.post<ComplianceHit[]>('/compliance/scan', null, { params: { text } }).then(r => r.data)
}

// ===== 风险评估 =====
export const riskApi = {
  latest: (customerIdHash: string) =>
    http.get<any>(`/risk/latest/${customerIdHash}`).then(r => r.data),

  submit: (customerIdHash: string, answers: Record<string, any>) =>
    http.post<any>('/risk/submit', answers, { params: { customerIdHash } }).then(r => r.data),

  match: (customerLevel: string, productLevel: string) =>
    http.post<any>('/risk/match', null, { params: { customerLevel, productLevel } }).then(r => r.data)
}

// ===== 状态机 =====
export const stateMachineApi = {
  transitions: () =>
    http.get<Record<string, string[]>>('/statemachine/transitions').then(r => r.data),

  canTransition: (from: string, to: string) =>
    http.get<boolean>('/statemachine/can-transition', { params: { from, to } }).then(r => r.data),

  allNodes: () =>
    http.get<any[]>('/recording/nodes').then(r => r.data)
}

// ===== 健康 =====
export const healthApi = {
  check: () => http.get<any>('/health').then(r => r.data)
}

export default http
