// 业务类型
export type BusinessType = 'INSURANCE' | 'WEALTH' | 'FUND'

// 渠道
export type Channel = 'OFFLINE' | 'REMOTE_VIDEO' | 'SELF_AI' | 'INTERNET_TEXT'

// 销售方类型
export type SellerType = 'HUMAN' | 'AI_DIGITAL_HUMAN'

// 8 个节点
export type NodeCode =
  | 'NODE_01_IDENTITY'
  | 'NODE_02_DISCLOSURE'
  | 'NODE_03_PRODUCT'
  | 'NODE_04_RIGHTS'
  | 'NODE_05_TRUTH_TELL'
  | 'NODE_06_CONFIRM'
  | 'NODE_07_SIGN'
  | 'NODE_08_FOLLOWUP'

// 状态机状态
export type RecordingState =
  | 'INIT' | 'IDENTITY_VERIFIED' | 'RISK_ASSESSED' | 'SCRIPT_LOADED'
  | 'RECORDING' | 'RECORDED' | 'AI_QA' | 'AI_QA_PASSED'
  | 'AI_QA_FLAGGED' | 'HUMAN_REVIEW' | 'HUMAN_REVIEWED'
  | 'SIGNED' | 'ARCHIVED' | 'FAILED' | 'ROLLED_BACK'

export interface Business {
  id: string
  businessId: string
  businessType: BusinessType
  productId: string
  customerIdHash: string
  sellerIdHash?: string
  channel: Channel
  state: RecordingState
  currentNode?: string
  amount?: number
  riskLevel?: string
  productRiskLevel?: string
  createdAt: string
  updatedAt: string
  archivedAt?: string
}

export interface NodeDefinition {
  order: number
  code: NodeCode
  displayName: string
  description: string
  critical: boolean
  durationSec: number
  mandatoryPhrases: string[]
  requiredQuestions: string[]
}

export interface ScriptTemplate {
  productId: string
  productType: string
  riskLevel: string
  version: string
  nodes: Array<{
    node_id: string
    display_name: string
    duration_sec: number
    mandatory_phrases?: string[]
    required_questions?: string[]
    critical?: boolean
    require_asr_affirmative?: boolean
    require_human_dual_sign?: boolean
  }>
  forbidden_phrases: string[]
  required_questions: string[]
  channel_overrides: Record<string, any>
  source?: string
}

export interface ComplianceHit {
  phrase: string
  severity: 'HIGH' | 'MEDIUM' | 'LOW'
  regulationRef: string
}

export interface QaResult {
  qaId: string
  recId: string
  businessId: string
  checkerType: string
  aiModelVersion: string
  aiQaScore: number
  aiQaResult: 'PASS' | 'PASS_WITH_FINDINGS' | 'FAIL'
  issuesJson?: string
  checkTime: string
}

export interface StartBusinessRequest {
  businessType: BusinessType
  productId: string
  customerIdHash: string
  sellerIdHash?: string
  channel: Channel
  sellerType: SellerType
  amount?: number
}

export interface NodeResult {
  nodeCode: string
  passed: boolean
  hits: ComplianceHit[]
  error?: string
}

export interface OverviewData {
  business: Business
  recordings: any[]
  nodes: any[]
  node_count: number
  completed_node_count: number
}
