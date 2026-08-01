<template>
  <div class="recording-studio">
    <!-- 顶部信息栏 -->
    <div class="top-bar">
      <div class="top-left">
        <h2>🎬 双录开画录制工作台</h2>
        <span class="session-info">会话: {{ sessionId }}</span>
      </div>
      <div class="top-right">
        <el-tag :type="recordingState.recording ? 'success' : 'info'" effect="dark" size="large">
          <el-icon><VideoCamera /></el-icon>
          {{ recordingState.recording ? (recordingState.paused ? '已暂停' : '录制中') : '未录制' }}
        </el-tag>
        <el-tag type="warning" effect="plain" size="large">
          N{{ currentNode }} / 11
        </el-tag>
        <el-tag type="info" effect="plain" size="large">
          <el-icon><Clock /></el-icon>
          {{ formatDuration(elapsedSeconds) }}
        </el-tag>
        <el-tag :type="riskLevelColor" effect="dark" size="large">
          AI: {{ riskLevel }}
        </el-tag>
      </div>
    </div>

    <!-- 主体三栏布局 -->
    <div class="main-grid">
      <!-- 左栏:WebRTC 视频画面 -->
      <el-card class="left-panel" shadow="hover">
        <template #header>
          <span>
            <el-icon><VideoCamera /></el-icon>
            实时双录画面
          </span>
          <div class="video-controls">
            <el-button-group>
              <el-button
                :type="micEnabled ? 'success' : 'danger'"
                :icon="micEnabled ? 'Microphone' : 'Microphone'"
                @click="toggleMic"
                size="small"
                circle
              />
              <el-button
                :type="cameraEnabled ? 'success' : 'danger'"
                :icon="cameraEnabled ? 'VideoCamera' : 'VideoCamera'"
                @click="toggleCamera"
                size="small"
                circle
              />
            </el-button-group>
          </div>
        </template>

        <!-- 视频画面区域 -->
        <div class="video-area">
          <!-- 本地预览(自己) -->
          <div class="video-frame local">
            <video ref="localVideoRef" autoplay muted playsinline class="video-el"></video>
            <div class="video-label">
              <el-icon><User /></el-icon>
              客户经理(本地)
            </div>
            <div v-if="!cameraEnabled" class="video-mask">
              <el-icon size="60"><VideoCameraFilled /></el-icon>
              <p>摄像头已关闭</p>
            </div>
            <div v-if="!micEnabled" class="mic-off">
              <el-icon size="20"><Microphone /></el-icon>
            </div>
          </div>

          <!-- 远端画面(客户) -->
          <div class="video-frame remote">
            <video ref="remoteVideoRef" autoplay playsinline class="video-el"></video>
            <div class="video-label">
              <el-icon><User /></el-icon>
              客户(远端)
            </div>
            <div v-if="!peerConnected" class="video-mask">
              <el-icon size="60"><Connection /></el-icon>
              <p>等待客户加入...</p>
              <el-button type="primary" size="small" @click="simulatePeerJoin">
                模拟客户加入
              </el-button>
            </div>
          </div>

          <!-- 敏感信息遮罩演示 -->
          <div v-if="showMask" class="mask-overlay">
            <div class="mask-box">
              <el-icon><Lock /></el-icon>
              敏感信息已遮罩:<br/>
              身份证: 1101**********1234<br/>
              银行卡: 6222**********0123
            </div>
          </div>
        </div>

        <!-- 录制控制 -->
        <div class="control-bar">
          <template v-if="!recordingState.recording">
            <el-button type="success" :icon="VideoPlay" @click="startRecording" size="large">
              开始录制
            </el-button>
          </template>
          <template v-else>
            <el-button
              :type="recordingState.paused ? 'warning' : 'info'"
              :icon="recordingState.paused ? VideoPlay : VideoPause"
              @click="togglePause"
              size="large"
            >
              {{ recordingState.paused ? '恢复' : '暂停' }}
            </el-button>
            <el-button type="danger" :icon="VideoCameraFilled" @click="stopRecording" size="large">
              停止录制
            </el-button>
            <el-button :icon="Right" @click="nextNode" size="large" :disabled="currentNode >= 11">
              下一节点
            </el-button>
          </template>
        </div>

        <!-- 录制质量指标 -->
        <div v-if="recordingState.recording" class="quality-panel">
          <div class="quality-header">
            <span>📊 录制质量</span>
            <el-tag :type="qualityGradeColor" size="small">{{ quality.grade }}</el-tag>
          </div>
          <div class="quality-grid">
            <div class="quality-item">
              <div class="qi-label">视频码率</div>
              <div class="qi-value">{{ quality.videoBitrate }}<span> kbps</span></div>
            </div>
            <div class="quality-item">
              <div class="qi-label">帧率</div>
              <div class="qi-value">{{ quality.videoFramerate }}<span> fps</span></div>
            </div>
            <div class="quality-item">
              <div class="qi-label">丢帧率</div>
              <div class="qi-value">{{ quality.videoDroppedRate }}<span>%</span></div>
            </div>
            <div class="quality-item">
              <div class="qi-label">网络延迟</div>
              <div class="qi-value">{{ quality.networkLatency }}<span> ms</span></div>
            </div>
            <div class="quality-item">
              <div class="qi-label">带宽</div>
              <div class="qi-value">{{ quality.bandwidthUsage }}<span> kbps</span></div>
            </div>
            <div class="quality-item">
              <div class="qi-label">CPU</div>
              <div class="qi-value">{{ quality.clientCpuUsage }}<span>%</span></div>
            </div>
          </div>
        </div>
      </el-card>

      <!-- 中栏:时间轴 -->
      <el-card class="middle-panel" shadow="hover">
        <template #header>
          <span>
            <el-icon><Clock /></el-icon>
            录制流程时间轴
          </span>
          <el-tag size="small" type="info" style="float:right">
            共 {{ timelineEvents.length }} 个事件
          </el-tag>
        </template>

        <el-scrollbar class="timeline-scroll">
          <el-timeline class="timeline">
            <el-timeline-item
              v-for="(evt, idx) in displayTimeline"
              :key="idx"
              :timestamp="formatTime(evt.timestamp)"
              :type="getTimelineType(evt.level)"
              :icon="getTimelineIcon(evt.type)"
              :hollow="idx > 0"
              placement="top"
            >
              <div class="timeline-event" :class="'level-' + evt.level.toLowerCase()">
                <div class="event-title">{{ evt.title }}</div>
                <div class="event-desc">{{ evt.description }}</div>
                <div v-if="evt.data && Object.keys(evt.data).length" class="event-data">
                  <el-tag
                    v-for="(v, k) in evt.data"
                    :key="k"
                    size="small"
                    :type="getDataTagType(k)"
                    style="margin: 2px;"
                  >
                    {{ k }}: {{ v }}
                  </el-tag>
                </div>
              </div>
            </el-timeline-item>
          </el-timeline>
          <el-empty v-if="displayTimeline.length === 0" description="暂无事件,点击开始录制" />
        </el-scrollbar>
      </el-card>

      <!-- 右栏:风险告警 + 操作面板 -->
      <el-card class="right-panel" shadow="hover">
        <template #header>
          <span>
            <el-icon><Warning /></el-icon>
            AI 风险监控
          </span>
          <el-button-group size="small" style="float:right">
            <el-button @click="aiRiskActive = !aiRiskActive" :type="aiRiskActive ? 'success' : ''">
              {{ aiRiskActive ? '已启用' : '未启用' }}
            </el-button>
          </el-button-group>
        </template>

        <!-- 风险统计 -->
        <div class="risk-stats">
          <div class="stat-item stat-low">
            <div class="stat-num">{{ riskStats.low }}</div>
            <div class="stat-label">低风险</div>
          </div>
          <div class="stat-item stat-medium">
            <div class="stat-num">{{ riskStats.medium }}</div>
            <div class="stat-label">中风险</div>
          </div>
          <div class="stat-item stat-high">
            <div class="stat-num">{{ riskStats.high }}</div>
            <div class="stat-label">高风险</div>
          </div>
          <div class="stat-item stat-critical">
            <div class="stat-num">{{ riskStats.critical }}</div>
            <div class="stat-label">严重</div>
          </div>
        </div>

        <!-- 风险事件流 -->
        <el-scrollbar class="risk-scroll">
          <div
            v-for="(evt, idx) in riskEvents"
            :key="idx"
            class="risk-event"
            :class="'level-' + evt.level.toLowerCase()"
          >
            <div class="risk-header">
              <el-tag :type="getRiskTagType(evt.level)" size="small" effect="dark">
                {{ evt.levelName }}
              </el-tag>
              <span class="risk-title">{{ evt.typeName }}</span>
              <span class="risk-time">{{ formatTime(evt.timestamp) }}</span>
            </div>
            <div class="risk-content">{{ evt.content }}</div>
            <div class="risk-source">来源: {{ evt.source }}</div>
          </div>
          <el-empty v-if="riskEvents.length === 0" description="无风险事件" :image-size="60" />
        </el-scrollbar>

        <!-- 快速操作 -->
        <div class="quick-actions">
          <el-button-group>
            <el-button size="small" @click="simulateRisk('FORBIDDEN')">模拟禁用表述</el-button>
            <el-button size="small" @click="simulateRisk('EMOTION')">模拟情绪激动</el-button>
            <el-button size="small" @click="simulateRisk('SIGN')">模拟快速签字</el-button>
          </el-button-group>
        </div>
      </el-card>
    </div>
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted, onUnmounted, nextTick } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  VideoCamera, VideoCameraFilled, VideoPlay, VideoPause, Clock,
  Warning, Right, User, Microphone, Connection, Lock
} from '@element-plus/icons-vue'
import { submitOrder } from '@/api/sagaExample'
import axios from '@/utils/request'

const sessionId = ref('SESS_' + Date.now().toString().slice(-8))
const currentNode = ref(0)
const elapsedSeconds = ref(0)
const timer = ref(null)

// 录制状态
const recordingState = reactive({
  recording: false,
  paused: false,
  consentRecorded: false,
  customerAgreed: false
})

// 视频状态
const cameraEnabled = ref(true)
const micEnabled = ref(true)
const peerConnected = ref(false)
const showMask = ref(false)
const aiRiskActive = ref(true)

const localVideoRef = ref(null)
const remoteVideoRef = ref(null)
let localStream = null
let peerConnection = null

// 录制质量指标
const quality = reactive({
  videoBitrate: 0,
  videoFramerate: 0,
  videoDroppedRate: 0,
  networkLatency: 0,
  bandwidthUsage: 0,
  clientCpuUsage: 0,
  grade: 'EXCELLENT'
})
let qualityTimer = null

const qualityGradeColor = computed(() => ({
  EXCELLENT: 'success', FAIR: 'warning', POOR: 'danger'
}[quality.grade] || 'info'))

// 风险事件
const riskEvents = ref([])
const riskStats = reactive({ low: 0, medium: 0, high: 0, critical: 0 })
const riskLevel = computed(() => {
  if (riskStats.critical > 0) return '严重'
  if (riskStats.high > 0) return '高风险'
  if (riskStats.medium > 0) return '中风险'
  return '低风险'
})
const riskLevelColor = computed(() => {
  if (riskStats.critical > 0) return 'danger'
  if (riskStats.high > 0) return 'warning'
  return 'success'
})

// 时间轴事件
const timelineEvents = ref([])
const displayTimeline = computed(() =>
  [...timelineEvents.value].reverse().slice(0, 50)
)

// 工具函数
const formatTime = (ts) => {
  if (!ts) return ''
  const d = new Date(ts)
  return d.toTimeString().slice(0, 8)
}

const formatDuration = (sec) => {
  const h = Math.floor(sec / 3600)
  const m = Math.floor((sec % 3600) / 60)
  const s = sec % 60
  return h > 0 ? `${h}:${String(m).padStart(2, '0')}:${String(s).padStart(2, '0')}` :
    `${m}:${String(s).padStart(2, '0')}`
}

const getTimelineType = (level) => ({
  INFO: 'primary', DEBUG: 'info', WARN: 'warning',
  ERROR: 'danger', FATAL: 'danger'
}[level] || 'info')

const getTimelineIcon = (type) => ({
  NODE_SWITCH: 'Right',
  RISK_EVENT: 'Warning',
  HEARTBEAT: 'Clock',
  INFO: 'InfoFilled'
}[type] || 'CircleCheck')

const getRiskTagType = (level) => ({
  LOW: 'info', MEDIUM: 'warning', HIGH: 'warning', CRITICAL: 'danger'
}[level] || 'info')

const getDataTagType = (key) => ({
  level: 'danger', type: 'warning', node: 'success', content: 'info'
}[key] || '')

// 初始化摄像头
const initCamera = async () => {
  try {
    localStream = await navigator.mediaDevices.getUserMedia({
      video: { width: 640, height: 480, facingMode: 'user' },
      audio: true
    })
    if (localVideoRef.value) {
      localVideoRef.value.srcObject = localStream
    }
  } catch (e) {
    ElMessage.warning('无法访问摄像头: ' + e.message)
    cameraEnabled.value = false
  }
}

// 模拟远端流(用 canvas + animation)
const simulatePeerJoin = () => {
  peerConnected.value = true
  if (remoteVideoRef.value) {
    const canvas = document.createElement('canvas')
    canvas.width = 640
    canvas.height = 480
    const ctx = canvas.getContext('2d')
    let t = 0
    const draw = () => {
      t += 0.02
      // 背景
      const grad = ctx.createLinearGradient(0, 0, 640, 480)
      grad.addColorStop(0, '#1e3a8a')
      grad.addColorStop(1, '#4f8cff')
      ctx.fillStyle = grad
      ctx.fillRect(0, 0, 640, 480)

      // 模拟人脸
      ctx.fillStyle = 'rgba(255, 255, 255, 0.9)'
      ctx.beginPath()
      ctx.arc(320, 240, 80, 0, Math.PI * 2)
      ctx.fill()

      // 头部
      ctx.beginPath()
      ctx.arc(320, 180, 60, 0, Math.PI * 2)
      ctx.fill()

      // 文字
      ctx.fillStyle = 'white'
      ctx.font = 'bold 20px sans-serif'
      ctx.fillText('客户画面(模拟)', 230, 420)
      ctx.font = '14px sans-serif'
      ctx.fillText(new Date().toTimeString().slice(0, 8), 270, 450)

      // 模拟点头动作
      const offsetY = Math.sin(t * 2) * 5
      ctx.fillText('↑ ↓ 模拟动作', 250, 470 - offsetY)

      // 敏感信息遮罩演示(随机显示)
      if (Math.random() < 0.01) {
        ctx.fillStyle = 'rgba(0, 0, 0, 0.7)'
        ctx.fillRect(0, 0, 640, 480)
        ctx.fillStyle = '#ff6b6b'
        ctx.font = 'bold 24px sans-serif'
        ctx.fillText('⚠ 敏感信息已遮罩', 180, 240)
        setTimeout(() => {}, 100)
      }

      if (remoteVideoRef.value && peerConnected.value) {
        requestAnimationFrame(draw)
      }
    }
    draw()
    // 转换 canvas 为 stream 赋值给 video
    remoteVideoRef.value.srcObject = canvas.captureStream(30)
  }
  addTimelineEvent('INFO', '远端加入', '客户已加入录制房间', 'info', 'Connection', { sessionId: sessionId.value })
}

const toggleMic = () => {
  if (!localStream) return
  micEnabled.value = !micEnabled.value
  localStream.getAudioTracks().forEach(t => t.enabled = micEnabled.value)
  addTimelineEvent('INFO', micEnabled.value ? '麦克风开启' : '麦克风关闭', '', 'info', 'Microphone', {})
}

const toggleCamera = () => {
  if (!localStream) return
  cameraEnabled.value = !cameraEnabled.value
  localStream.getVideoTracks().forEach(t => t.enabled = cameraEnabled.value)
  addTimelineEvent('INFO', cameraEnabled.value ? '摄像头开启' : '摄像头关闭', '', 'info', 'VideoCamera', {})
}

// 时间轴事件操作
const addTimelineEvent = (level, title, description, tag, icon, data) => {
  timelineEvents.value.push({
    type: 'INFO',
    level: level || 'INFO',
    title: title,
    description: description,
    timestamp: new Date().toISOString(),
    icon: icon,
    data: data || {}
  })
}

// 风控事件
const addRiskEvent = (event) => {
  riskEvents.value.unshift(event)
  const level = event.level
  if (level === 'LOW') riskStats.low++
  else if (level === 'MEDIUM') riskStats.medium++
  else if (level === 'HIGH') riskStats.high++
  else if (level === 'CRITICAL') riskStats.critical++

  // 高危/严重事件写入时间轴
  if (level === 'HIGH' || level === 'CRITICAL') {
    addTimelineEvent(
      level === 'CRITICAL' ? 'FATAL' : 'WARN',
      `[${event.levelName}] ${event.typeName}`,
      event.content,
      level === 'CRITICAL' ? 'danger' : 'warning',
      'Warning',
      { level: event.levelName, type: event.typeName, source: event.source }
    )
    // 弹窗告警
    if (level === 'CRITICAL') {
      ElMessageBox.alert(
        `严重风险事件: ${event.content}`,
        'AI 风控告警',
        { type: 'error', confirmButtonText: '已确认' }
      )
    } else {
      ElMessage.warning(`风险告警: ${event.content}`)
    }
  }
}

// 录制控制
const startRecording = async () => {
  // 1. 启动 AI 风控
  try {
    await axios.post('/ai-risk/start', { sessionId: sessionId.value })
  } catch (e) {
    console.warn('AI 风控启动失败', e)
  }

  // 2. 通知客户并同意
  try {
    await ElMessageBox.confirm(
      '请向客户明确告知:本次销售过程将全程录音录像,录像内容将作为日后争议处理依据。客户是否明确同意?',
      '开始录制 - 知情同意',
      { confirmButtonText: '客户同意', cancelButtonText: '客户拒绝', type: 'warning' }
    )
  } catch {
    ElMessage.warning('客户未同意,不能开始录制')
    return
  }

  // 3. 调用后端 API 开启录制
  try {
    await axios.post('/chain/recording/start', {
      sessionId: sessionId.value,
      currentNodeSeq: 1,
      consentRecorded: true,
      customerAgreed: true,
      agentId: 'AGT_' + sessionId.value
    })
  } catch (e) {
    ElMessage.error('录制开启失败: ' + (e.response?.data?.message || e.message))
    return
  }

  recordingState.recording = true
  recordingState.paused = false
  recordingState.consentRecorded = true
  recordingState.customerAgreed = true
  currentNode.value = 1
  startTimer()
  addTimelineEvent('INFO', '录制开始', '客户已明确同意,开始 N01 节点录制', 'success', 'VideoPlay', { sessionId: sessionId.value })
  ElMessage.success('录制已开始')

  // 模拟客户加入
  setTimeout(() => simulatePeerJoin(), 1000)
}

const togglePause = async () => {
  if (recordingState.paused) {
    await axios.post('/chain/recording/resume', { sessionId: sessionId.value })
    recordingState.paused = false
    addTimelineEvent('INFO', '录制恢复', '', 'info', 'VideoPlay', {})
    ElMessage.success('录制已恢复')
  } else {
    try {
      await axios.post('/chain/recording/pause', {
        sessionId: sessionId.value,
        reason: '客户需要接电话'
      })
    } catch (e) {
      ElMessage.warning('暂停失败(可能已超过最大暂停次数)')
      return
    }
    recordingState.paused = true
    addTimelineEvent('WARN', '录制暂停', '客户需要接电话', 'warning', 'VideoPause', {})
    ElMessage.warning('录制已暂停')
  }
}

const stopRecording = async () => {
  try {
    await ElMessageBox.confirm('确定停止录制吗?', '停止录制', { type: 'warning' })
  } catch { return }

  try {
    const res = await axios.post('/chain/recording/stop', {
      sessionId: sessionId.value,
      currentNodeSeq: currentNode.value
    })
    addTimelineEvent('INFO', '录制停止',
      `总时长 ${res.data.data.totalDuration}秒, 节点数 ${res.data.data.nodeCount}`,
      'success', 'VideoCameraFilled', res.data.data)
  } catch (e) {
    ElMessage.error('停止失败: ' + (e.response?.data?.message || e.message))
  }

  recordingState.recording = false
  recordingState.paused = false
  stopTimer()
  ElMessage.success('录制已停止')

  // 停止 AI 风控
  try {
    await axios.post(`/ai-risk/stop/${sessionId.value}`)
  } catch {}
}

const nextNode = async () => {
  if (currentNode.value >= 11) {
    ElMessage.warning('已到最后节点')
    return
  }
  const newNode = currentNode.value + 1
  try {
    await axios.post('/chain/recording/switch-node', {
      sessionId: sessionId.value,
      newNodeSeq: newNode
    })
  } catch (e) {
    ElMessage.error('节点切换失败: ' + (e.response?.data?.message || e.message))
    return
  }
  addTimelineEvent('INFO',
    `N${String(currentNode.value).padStart(2, '0')} → N${String(newNode).padStart(2, '0')}`,
    '节点切换', 'success', 'Right',
    { oldNode: currentNode.value, newNode: newNode }
  )
  currentNode.value = newNode
}

const startTimer = () => {
  stopTimer()
  timer.value = setInterval(() => {
    if (!recordingState.paused) elapsedSeconds.value++
  }, 1000)
  // 质量指标每 2 秒采样
  startQualityMonitor()
}

const stopTimer = () => {
  if (timer.value) clearInterval(timer.value)
  timer.value = null
  stopQualityMonitor()
}

const startQualityMonitor = () => {
  stopQualityMonitor()
  // 初始一次
  axios.post(`/quality-metrics/start/${sessionId.value}`).then(r => {
    updateQualityLocal(r.data.data)
  })
  qualityTimer = setInterval(() => {
    axios.post(`/quality-metrics/${sessionId.value}/simulate-tick?elapsed=${elapsedSeconds.value}`)
      .then(r => updateQualityLocal(r.data.data))
      .catch(() => {})
  }, 2000)
}

const stopQualityMonitor = () => {
  if (qualityTimer) {
    clearInterval(qualityTimer)
    qualityTimer = null
  }
  if (recordingState.recording === false) {
    axios.delete(`/quality-metrics/${sessionId.value}`).catch(() => {})
  }
}

const updateQualityLocal = (q) => {
  quality.videoBitrate = q.videoBitrate || 0
  quality.videoFramerate = q.videoFramerate || 0
  quality.videoDroppedRate = ((q.videoDroppedRate || 0) * 100).toFixed(2)
  quality.networkLatency = q.networkLatency || 0
  quality.bandwidthUsage = q.bandwidthUsage || 0
  quality.clientCpuUsage = (q.clientCpuUsage || 0).toFixed(1)
  quality.grade = q.qualityGrade || 'EXCELLENT'
}

// 模拟风控
const simulateRisk = async (kind) => {
  if (!aiRiskActive.value) {
    ElMessage.warning('AI 风控未启用')
    return
  }
  let event = null
  if (kind === 'FORBIDDEN') {
    const res = await axios.post('/ai-risk/audio', {
      sessionId: sessionId.value,
      text: '这个理财保证您的本金绝对不会亏损,稳赚不赔',
      speaker: 'AGENT'
    })
    event = res.data.data?.[0]
  } else if (kind === 'EMOTION') {
    const res = await axios.post('/ai-risk/audio', {
      sessionId: sessionId.value,
      text: '你们骗人!我要投诉!退钱!',
      speaker: 'CUSTOMER'
    })
    event = res.data.data?.[0]
  } else if (kind === 'SIGN') {
    const res = await axios.post('/ai-risk/behavior', {
      sessionId: sessionId.value,
      currentNodeSeq: currentNode.value || 1,
      action: 'SIGN',
      durationMs: 1500
    })
    event = res.data.data?.[0]
  }
  if (event) {
    addRiskEvent(event)
  } else {
    ElMessage.info('未触发风险')
  }
}

onMounted(() => {
  initCamera()
  addTimelineEvent('INFO', '工作台就绪', '准备开始录制', 'info', 'InfoFilled', {})
})

onUnmounted(() => {
  stopTimer()
  if (localStream) {
    localStream.getTracks().forEach(t => t.stop())
  }
})
</script>

<style lang="scss" scoped>
.recording-studio {
  padding: 16px;
  background: #0a0e1a;
  min-height: calc(100vh - 60px);
  color: #e7ecf6;
}

.top-bar {
  display: flex;
  justify-content: space-between;
  align-items: center;
  background: linear-gradient(135deg, #1e3a8a 0%, #4f8cff 100%);
  padding: 16px 24px;
  border-radius: 12px;
  margin-bottom: 16px;

  .top-left h2 {
    margin: 0;
    color: white;
    font-size: 20px;
  }
  .session-info {
    color: rgba(255, 255, 255, 0.8);
    font-size: 13px;
    margin-left: 12px;
  }
  .top-right {
    display: flex;
    gap: 8px;
  }
}

.main-grid {
  display: grid;
  grid-template-columns: 1fr 380px 360px;
  gap: 16px;
  height: calc(100vh - 180px);
}

.left-panel {
  background: #1a1f3b !important;
  border-color: #2a3358 !important;

  :deep(.el-card__header) {
    background: rgba(79, 140, 255, 0.1);
    color: #e7ecf6;
    border-color: #2a3358;
    display: flex;
    justify-content: space-between;
  }
}

.video-area {
  position: relative;
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 8px;
  aspect-ratio: 4/3;
  background: #000;
  border-radius: 8px;
  overflow: hidden;
}

.video-frame {
  position: relative;
  background: #000;
  border-radius: 8px;
  overflow: hidden;
  display: flex;
  align-items: center;
  justify-content: center;
}

.video-el {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.video-label {
  position: absolute;
  top: 8px;
  left: 8px;
  background: rgba(0, 0, 0, 0.7);
  color: white;
  padding: 4px 10px;
  border-radius: 4px;
  font-size: 12px;
  display: flex;
  align-items: center;
  gap: 4px;
  z-index: 10;
}

.video-mask {
  position: absolute;
  inset: 0;
  background: rgba(0, 0, 0, 0.8);
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  color: #4f8cff;
  gap: 12px;
}

.mic-off {
  position: absolute;
  top: 8px;
  right: 8px;
  background: #ff6b6b;
  color: white;
  padding: 4px;
  border-radius: 50%;
  z-index: 10;
}

.mask-overlay {
  position: absolute;
  inset: 0;
  background: rgba(0, 0, 0, 0.6);
  display: flex;
  align-items: center;
  justify-content: center;
  z-index: 20;
}

.mask-box {
  background: rgba(255, 107, 107, 0.95);
  color: white;
  padding: 24px;
  border-radius: 12px;
  font-size: 14px;
  line-height: 2;
  text-align: center;
  max-width: 80%;
}

.control-bar {
  margin-top: 12px;
  display: flex;
  gap: 8px;
  justify-content: center;
}

.middle-panel, .right-panel {
  background: #1a1f3b !important;
  border-color: #2a3358 !important;
  display: flex;
  flex-direction: column;

  :deep(.el-card__header) {
    background: rgba(79, 140, 255, 0.1);
    color: #e7ecf6;
    border-color: #2a3358;
  }
  :deep(.el-card__body) {
    flex: 1;
    overflow: hidden;
    display: flex;
    flex-direction: column;
  }
}

.timeline-scroll {
  flex: 1;
}

.timeline {
  padding: 12px 0;
}

.timeline-event {
  background: rgba(79, 140, 255, 0.05);
  border-left: 3px solid #4f8cff;
  padding: 8px 12px;
  border-radius: 4px;
  margin-bottom: 4px;

  &.level-warn { border-left-color: #ffb454; background: rgba(255, 180, 84, 0.1); }
  &.level-error { border-left-color: #ff6b6b; background: rgba(255, 107, 107, 0.1); }
  &.level-fatal { border-left-color: #ff6b6b; background: rgba(255, 107, 107, 0.2); }
  &.level-debug { border-left-color: #2a3358; background: rgba(42, 51, 88, 0.3); }

  .event-title {
    font-weight: 600;
    color: #e7ecf6;
    font-size: 14px;
  }
  .event-desc {
    color: #99a4c2;
    font-size: 12px;
    margin: 4px 0;
  }
  .event-data {
    margin-top: 4px;
  }
}

.risk-stats {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 8px;
  margin-bottom: 12px;
}

.stat-item {
  text-align: center;
  padding: 8px 4px;
  border-radius: 6px;
  background: rgba(255, 255, 255, 0.05);

  &.stat-low { border-top: 2px solid #4f8cff; }
  &.stat-medium { border-top: 2px solid #ffb454; }
  &.stat-high { border-top: 2px solid #ff8c54; }
  &.stat-critical { border-top: 2px solid #ff6b6b; }

  .stat-num {
    font-size: 22px;
    font-weight: 700;
    color: #e7ecf6;
  }
  .stat-label {
    font-size: 11px;
    color: #99a4c2;
  }
}

.risk-scroll {
  flex: 1;
  min-height: 200px;
}

.risk-event {
  background: rgba(255, 255, 255, 0.05);
  padding: 8px 10px;
  border-radius: 6px;
  margin-bottom: 6px;
  border-left: 3px solid #4f8cff;

  &.level-low { border-left-color: #4f8cff; }
  &.level-medium { border-left-color: #ffb454; }
  &.level-high { border-left-color: #ff8c54; }
  &.level-critical { border-left-color: #ff6b6b; background: rgba(255, 107, 107, 0.1); }

  .risk-header {
    display: flex;
    align-items: center;
    gap: 6px;
    font-size: 12px;

    .risk-title {
      color: #e7ecf6;
      font-weight: 600;
      flex: 1;
    }
    .risk-time {
      color: #99a4c2;
      font-size: 11px;
    }
  }
  .risk-content {
    color: #99a4c2;
    font-size: 12px;
    margin: 4px 0;
  }
  .risk-source {
    color: #5fd0a4;
    font-size: 11px;
  }
}

.quick-actions {
  margin-top: 8px;
  display: flex;
  justify-content: center;
}

/* 录制质量指标面板 */
.quality-panel {
  margin-top: 12px;
  background: rgba(79, 140, 255, 0.05);
  border: 1px solid rgba(79, 140, 255, 0.2);
  border-radius: 8px;
  padding: 10px 12px;
}

.quality-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 8px;
  color: #e7ecf6;
  font-size: 13px;
  font-weight: 600;
}

.quality-grid {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 8px;
}

.quality-item {
  background: rgba(0, 0, 0, 0.3);
  border-radius: 6px;
  padding: 6px 8px;
  text-align: center;
}

.qi-label {
  color: #99a4c2;
  font-size: 11px;
  margin-bottom: 2px;
}

.qi-value {
  color: #5fd0a4;
  font-size: 16px;
  font-weight: 700;
  font-family: 'Consolas', 'Monaco', monospace;
}

.qi-value span {
  color: #99a4c2;
  font-size: 10px;
  font-weight: 400;
  margin-left: 2px;
}
</style>
