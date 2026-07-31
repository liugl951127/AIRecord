import type { NodeCode, NodeDefinition } from '@/types'

// 8 节点元数据（前端硬编码，与后端 RecordingNode 枚举保持一致）
export const NODE_DEFINITIONS: NodeDefinition[] = [
  {
    order: 1,
    code: 'NODE_01_IDENTITY',
    displayName: '出示身份',
    description: '坐席出示工牌，客户出示身份证',
    critical: false,
    durationSec: 30,
    mandatoryPhrases: ['您好，我是理财经理 XXX', '请出示您的身份证件'],
    requiredQuestions: []
  },
  {
    order: 2,
    code: 'NODE_02_DISCLOSURE',
    displayName: '风险揭示',
    description: '明确告知产品风险 · 必播项最多',
    critical: true,
    durationSec: 90,
    mandatoryPhrases: [
      '本产品为非保本浮动收益型理财，不保证本金和收益',
      '业绩比较基准不代表实际收益，过往业绩不代表未来表现'
    ],
    requiredQuestions: ['您是否已了解产品为非保本浮动收益？']
  },
  {
    order: 3,
    code: 'NODE_03_PRODUCT',
    displayName: '产品展示',
    description: '条款 / 收益 / 风险',
    critical: false,
    durationSec: 60,
    mandatoryPhrases: ['本产品风险等级', '投资方向', '期限'],
    requiredQuestions: []
  },
  {
    order: 4,
    code: 'NODE_04_RIGHTS',
    displayName: '权利义务',
    description: '犹豫期 / 退保 / 冷静期',
    critical: false,
    durationSec: 45,
    mandatoryPhrases: ['犹豫期为本合同生效之日起 15 天内', '本金及收益风险由投资者自行承担'],
    requiredQuestions: []
  },
  {
    order: 5,
    code: 'NODE_05_TRUTH_TELL',
    displayName: '如实告知',
    description: '健康 / 财务 / 既往持仓',
    critical: false,
    durationSec: 60,
    mandatoryPhrases: [],
    requiredQuestions: ['您是否已阅读相关法律文件？', '本次投资资金是否为借贷资金？']
  },
  {
    order: 6,
    code: 'NODE_06_CONFIRM',
    displayName: '明确肯定 ★',
    description: '★ 关键节点 · ASR 肯定词 + 坐席双签',
    critical: true,
    durationSec: 30,
    mandatoryPhrases: ['您是否已了解本产品的风险特征？', '您是否已阅读产品说明书？'],
    requiredQuestions: []
  },
  {
    order: 7,
    code: 'NODE_07_SIGN',
    displayName: '签署文件',
    description: '电子签 / CA 认证',
    critical: false,
    durationSec: 45,
    mandatoryPhrases: ['请在平板上签字确认'],
    requiredQuestions: []
  },
  {
    order: 8,
    code: 'NODE_08_FOLLOWUP',
    displayName: '补充询问',
    description: '客户提问环节',
    critical: false,
    durationSec: 20,
    mandatoryPhrases: ['请问您还有其他问题吗？'],
    requiredQuestions: []
  }
]

export function getNodeDefinition(code: NodeCode): NodeDefinition {
  return NODE_DEFINITIONS.find(n => n.code === code)!
}

export function getNodeCodeByIndex(idx: number): NodeCode {
  return NODE_DEFINITIONS[idx].code
}

export const STATE_LABELS: Record<string, string> = {
  INIT: '初始化',
  IDENTITY_VERIFIED: '身份已核验',
  RISK_ASSESSED: '风险已评估',
  SCRIPT_LOADED: '话术已加载',
  RECORDING: '录制中',
  RECORDED: '录制完成',
  AI_QA: 'AI 质检中',
  AI_QA_PASSED: 'AI 质检通过',
  AI_QA_FLAGGED: 'AI 标红待人工',
  HUMAN_REVIEW: '人工复核中',
  HUMAN_REVIEWED: '人工复核完成',
  SIGNED: '已签字',
  ARCHIVED: '已归档',
  FAILED: '失败',
  ROLLED_BACK: '已回滚'
}

export const CHANNEL_LABELS: Record<string, string> = {
  OFFLINE: '线下面对面',
  REMOTE_VIDEO: '远程视频',
  SELF_AI: 'AI 数字人',
  INTERNET_TEXT: '互联网文本'
}

export const STATE_COLORS: Record<string, string> = {
  INIT: '#94a3b8',
  RECORDING: '#3b6b8c',
  AI_QA: '#6b4a8a',
  AI_QA_PASSED: '#2f6f5e',
  AI_QA_FLAGGED: '#c1453a',
  SIGNED: '#2f6f5e',
  ARCHIVED: '#1e2a47',
  FAILED: '#c1453a',
  ROLLED_BACK: '#94a3b8'
}
