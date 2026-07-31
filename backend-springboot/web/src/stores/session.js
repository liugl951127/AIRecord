import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import {
  createSession,
  getSession,
  getCurrentNode,
  submitNode,
  startVideo,
  completeVideo,
  sign,
  pauseSession,
  resumeSession,
  getEvents
} from '@/api/doubleRecording'

export const useSessionStore = defineStore('session', () => {
  // 状态
  const sessionId = ref(null)
  const session = ref(null)
  const currentNode = ref(null)
  const events = ref([])
  const qualityResult = ref(null)
  const sagaResult = ref(null)
  const loading = ref(false)
  const logs = ref([])

  // 计算属性
  const stateLabel = computed(() => {
    const map = {
      CREATED: '已创建',
      RECORDING: '录制中',
      COMPLETED: '已完成',
      PAUSED: '已暂停',
      FAILED: '已失败',
      QUALITY_BLOCKED: '质检阻断',
      VIDEO_MERGING: '视频合成中',
      CHAINING: '存证中'
    }
    return map[session.value?.currentState] || session.value?.currentState
  })

  const isActive = computed(() => {
    return session.value && !['COMPLETED', 'FAILED', 'CANCELLED'].includes(session.value.currentState)
  })

  // 日志
  const addLog = (msg, type = 'info') => {
    const time = new Date().toLocaleTimeString('zh-CN', { hour12: false })
    logs.value.unshift({ time, msg, type, id: Date.now() + Math.random() })
    if (logs.value.length > 200) logs.value.pop()
  }

  const clearLogs = () => { logs.value = [] }

  // Actions
  const doCreate = async (data) => {
    loading.value = true
    try {
      const res = await createSession(data)
      sessionId.value = res.data.sessionId
      session.value = res.data
      addLog(`✓ 会话创建: ${sessionId.value}`, 'success')
      addLog(`  - 话术模板: ${res.data.scriptTemplateId} v${res.data.scriptVersion}`)
      addLog(`  - 风险等级: ${res.data.riskLevel}`)
      return res.data
    } catch (e) {
      addLog(`✗ 创建失败: ${e.message}`, 'error')
      throw e
    } finally {
      loading.value = false
    }
  }

  const loadCurrentNode = async () => {
    if (!sessionId.value) return null
    try {
      const res = await getCurrentNode(sessionId.value)
      currentNode.value = res.data
      return res.data
    } catch (e) {
      addLog(`无更多节点: ${e.message}`, 'warn')
      return null
    }
  }

  const doSubmitNode = async (data) => {
    if (!sessionId.value) throw new Error('会话未创建')
    loading.value = true
    try {
      const res = await submitNode(sessionId.value, data)
      qualityResult.value = res.data
      const status = res.data.status
      if (status === 'PASS') {
        addLog(`✓ N${data.nodeSeq} 节点质检通过`, 'success')
      } else if (status === 'BLOCKED') {
        addLog(`✗ N${data.nodeSeq} 节点质检阻断: ${res.data.message}`, 'error')
      } else {
        addLog(`⚠ N${data.nodeSeq} 节点质检告警: ${res.data.message}`, 'warn')
      }
      return res.data
    } catch (e) {
      addLog(`✗ 节点提交失败: ${e.message}`, 'error')
      throw e
    } finally {
      loading.value = false
    }
  }

  const doStartVideo = async () => {
    if (!sessionId.value) return
    const res = await startVideo(sessionId.value)
    addLog(`✓ 视频录制启动: ${res.data}`, 'success')
    return res.data
  }

  const doSign = async () => {
    if (!sessionId.value) return
    const res = await sign(sessionId.value)
    addLog(`✓ 客户签字完成: ${res.data.certNo}`, 'success')
    addLog(`  - 签名哈希: ${res.data.signHash.substring(0, 32)}...`)
    return res.data
  }

  const doCompleteVideo = async (duration = 300) => {
    if (!sessionId.value) return
    addLog('正在触发 Saga 分布式事务...', 'info')
    const res = await completeVideo(sessionId.value, duration)
    sagaResult.value = res.data
    addLog(`✓ Saga 执行完成`, 'success')
    addLog(`  - 订单: ${res.data.orderId}`)
    addLog(`  - 区块链存证: ${res.data.certNo}`)
    addLog(`  - 视频哈希: ${(res.data.sha256 || '').substring(0, 32)}...`)
    return res.data
  }

  const doPause = async () => {
    if (!sessionId.value) return
    await pauseSession(sessionId.value)
    addLog('⏸ 会话已暂停', 'info')
  }

  const doResume = async () => {
    if (!sessionId.value) return
    const res = await resumeSession(sessionId.value)
    addLog(`▶ 断点续录恢复: ${res.data.currentState}`, 'success')
    addLog(`  - 已完成节点: ${res.data.completedNodes?.length || 0}`)
    return res.data
  }

  const refresh = async () => {
    if (!sessionId.value) return
    const res = await getSession(sessionId.value)
    session.value = res.data
    return res.data
  }

  const loadEvents = async () => {
    if (!sessionId.value) return
    const res = await getEvents(sessionId.value)
    events.value = res.data
    addLog(`已加载 ${res.data.length} 个事件`, 'info')
    return res.data
  }

  const reset = () => {
    sessionId.value = null
    session.value = null
    currentNode.value = null
    events.value = []
    qualityResult.value = null
    sagaResult.value = null
    clearLogs()
  }

  return {
    sessionId, session, currentNode, events, qualityResult, sagaResult, loading, logs,
    stateLabel, isActive,
    addLog, clearLogs,
    doCreate, loadCurrentNode, doSubmitNode, doStartVideo, doSign, doCompleteVideo,
    doPause, doResume, refresh, loadEvents, reset
  }
})
