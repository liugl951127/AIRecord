import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import { recordingApi, scriptConfigApi, riskApi } from '@/api'
import { NODE_DEFINITIONS, getNodeCodeByIndex } from '@/utils/nodes'
import type {
  Business, ScriptTemplate, NodeCode, Channel, BusinessType, SellerType
} from '@/types'

export const useRecordingStore = defineStore('recording', () => {
  // ===== 状态 =====
  const business = ref<Business | null>(null)
  const script = ref<ScriptTemplate | null>(null)
  const currentNodeIdx = ref(0)        // 当前 8 节点中的下标 (0-7)
  const completedNodes = ref<Set<NodeCode>>(new Set())
  const asrInput = ref('')             // 当前节点的 ASR 输入
  const alerts = ref<Array<{ time: string; type: string; message: string }>>([])
  const isProcessing = ref(false)
  const qaResult = ref<any>(null)
  const riskMatch = ref<any>(null)
  const fullAsrText = ref('')          // 累积所有节点的 ASR 转写
  const lastError = ref<string>('')

  // ===== 计算属性 =====
  const currentNode = computed(() => NODE_DEFINITIONS[currentNodeIdx.value])
  const progress = computed(() => Math.round((completedNodes.value.size / 8) * 100))
  const isFinished = computed(() => completedNodes.value.size === 8)
  const stateLabel = computed(() => {
    if (!business.value) return '未开始'
    return business.value.state
  })

  // ===== Actions =====

  function logAlert(type: string, message: string) {
    alerts.value.unshift({
      time: new Date().toLocaleTimeString('zh-CN'),
      type,
      message
    })
    if (alerts.value.length > 50) alerts.value = alerts.value.slice(0, 50)
  }

  function reset() {
    business.value = null
    script.value = null
    currentNodeIdx.value = 0
    completedNodes.value = new Set()
    asrInput.value = ''
    alerts.value = []
    qaResult.value = null
    riskMatch.value = null
    fullAsrText.value = ''
    lastError.value = ''
  }

  async function startBusiness(params: {
    businessType: BusinessType
    productId: string
    customerIdHash: string
    sellerIdHash?: string
    channel: Channel
    sellerType: SellerType
    amount?: number
  }) {
    isProcessing.value = true
    lastError.value = ''
    try {
      logAlert('info', `开始创建业务: ${params.productId}`)
      const b = await recordingApi.startBusiness(params)
      business.value = b
      logAlert('success', `✓ 业务已创建: ${b.businessId}`)
      return b
    } catch (e: any) {
      lastError.value = e.message
      logAlert('error', `✗ 创建失败: ${e.message}`)
      throw e
    } finally {
      isProcessing.value = false
    }
  }

  async function loadScript(productId: string, productType?: string) {
    if (!business.value) {
      throw new Error('请先创建业务')
    }
    isProcessing.value = true
    try {
      logAlert('info', `加载话术: ${productId}`)
      const s = await scriptConfigApi.product(productId, productType)
      script.value = s
      logAlert('success', `✓ 话术已加载 (源: ${s.source || 'PRODUCT_SPECIFIC'})`)
      return s
    } catch (e: any) {
      lastError.value = e.message
      logAlert('error', `✗ 加载话术失败: ${e.message}`)
      throw e
    } finally {
      isProcessing.value = false
    }
  }

  async function assessRisk(customerIdHash: string) {
    if (!business.value) {
      throw new Error('请先创建业务')
    }
    isProcessing.value = true
    try {
      logAlert('info', '执行风险评估...')
      const result = await recordingApi.assessRisk(business.value.businessId, customerIdHash)
      riskMatch.value = result
      logAlert('success', `✓ 风险评估完成: ${JSON.stringify(result)}`)
      return result
    } catch (e: any) {
      lastError.value = e.message
      logAlert('error', `✗ 风险评估失败: ${e.message}`)
      throw e
    } finally {
      isProcessing.value = false
    }
  }

  async function beginRecording() {
    if (!business.value) {
      throw new Error('请先创建业务')
    }
    isProcessing.value = true
    try {
      await recordingApi.startRecording(business.value.businessId)
      logAlert('success', '✓ 录制已开始，进入 8 节点状态机')
    } catch (e: any) {
      lastError.value = e.message
      logAlert('error', `✗ 启动录制失败: ${e.message}`)
      throw e
    } finally {
      isProcessing.value = false
    }
  }

  async function completeCurrentNode(asrText: string) {
    if (!business.value) {
      throw new Error('请先创建业务并启动录制')
    }
    isProcessing.value = true
    try {
      const nodeCode = currentNode.value.code
      logAlert('info', `节点 ${currentNode.value.order} (${currentNode.value.displayName}) 开始...`)

      // 累积 ASR
      fullAsrText.value += (fullAsrText.value ? '\n' : '') + `[节点${currentNode.value.order}] ${asrText}`

      const result = await recordingApi.completeNode({
        businessId: business.value.businessId,
        recId: 'REC-' + business.value.businessId,
        node: nodeCode,
        asrText
      })

      if (result.hits && result.hits.length > 0) {
        for (const hit of result.hits) {
          logAlert(hit.severity === 'HIGH' ? 'error' : 'warning',
            `禁播词命中 [${hit.severity}]: "${hit.phrase}" (${hit.regulationRef})`)
        }
      }

      completedNodes.value.add(nodeCode)
      logAlert('success', `✓ 节点 ${currentNode.value.order} (${currentNode.value.displayName}) 完成`)

      // 前进到下一节点
      if (currentNodeIdx.value < 7) {
        currentNodeIdx.value++
        asrInput.value = ''
      }
      return result
    } catch (e: any) {
      lastError.value = e.message
      logAlert('error', `✗ 节点完成失败: ${e.message}`)
      throw e
    } finally {
      isProcessing.value = false
    }
  }

  async function finalize() {
    if (!business.value) {
      throw new Error('请先创建业务')
    }
    isProcessing.value = true
    try {
      logAlert('info', '执行 AI 终检...')
      const result = await recordingApi.finalize(
        business.value.businessId,
        'REC-' + business.value.businessId,
        fullAsrText.value
      )
      qaResult.value = result
      logAlert(result.aiQaResult === 'PASS' ? 'success' : 'warning',
        `AI 终检: ${result.aiQaResult} (分数: ${result.aiQaScore})`)
      return result
    } catch (e: any) {
      lastError.value = e.message
      logAlert('error', `✗ 终检失败: ${e.message}`)
      throw e
    } finally {
      isProcessing.value = false
    }
  }

  async function signAndArchive() {
    if (!business.value) {
      throw new Error('请先创建业务')
    }
    isProcessing.value = true
    try {
      await recordingApi.signAndArchive(business.value.businessId)
      logAlert('success', '✓ 业务已签字归档')
    } catch (e: any) {
      lastError.value = e.message
      logAlert('error', `✗ 归档失败: ${e.message}`)
      throw e
    } finally {
      isProcessing.value = false
    }
  }

  async function loadOverview(businessId: string) {
    isProcessing.value = true
    try {
      return await recordingApi.overview(businessId)
    } finally {
      isProcessing.value = false
    }
  }

  return {
    // state
    business, script, currentNodeIdx, completedNodes,
    asrInput, alerts, isProcessing, qaResult, riskMatch,
    fullAsrText, lastError,
    // computed
    currentNode, progress, isFinished, stateLabel,
    // actions
    logAlert, reset, startBusiness, loadScript, assessRisk,
    beginRecording, completeCurrentNode, finalize, signAndArchive, loadOverview
  }
})
