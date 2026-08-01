<template>
  <div class="h5-customer">
    <div class="h5-container">
      <!-- 顶部品牌栏 -->
      <div class="brand-bar">
        <div class="logo">🛡</div>
        <div class="brand-name">AIRecord</div>
        <div class="brand-tag">安全双录</div>
      </div>

      <!-- 步骤 1: 接入会话 -->
      <div v-if="step === 'join'" class="card">
        <div class="card-icon">📱</div>
        <h2>欢迎使用双录服务</h2>
        <p class="desc">为保障您的合法权益,本次销售过程将进行全程录音录像</p>
        <div class="session-input">
          <label>会话码</label>
          <input v-model="sessionIdInput" placeholder="请输入 6 位会话码" maxlength="6" />
        </div>
        <button class="primary-btn" @click="joinSession" :disabled="!sessionIdInput">
          进入会话
        </button>
        <div class="trust-badges">
          <span>🔒 银行级加密</span>
          <span>⚖ 法律级证据</span>
          <span>🛡 隐私保护</span>
        </div>
      </div>

      <!-- 步骤 2: 设备检测 -->
      <div v-else-if="step === 'diagnose'" class="card">
        <div class="card-icon">🔧</div>
        <h2>设备检测</h2>
        <p class="desc">我们需要检测您的设备以保证录制质量</p>
        <div class="check-list">
          <div class="check-item" :class="{ pass: deviceDiag.cameraOk, fail: !deviceDiag.cameraOk }">
            <span class="icon">{{ deviceDiag.cameraOk ? '✓' : '✗' }}</span>
            <span class="name">摄像头</span>
            <span class="status">{{ deviceDiag.cameraOk ? '正常' : '未检测到' }}</span>
          </div>
          <div class="check-item" :class="{ pass: deviceDiag.microphoneOk, fail: !deviceDiag.microphoneOk }">
            <span class="icon">{{ deviceDiag.microphoneOk ? '✓' : '✗' }}</span>
            <span class="name">麦克风</span>
            <span class="status">{{ deviceDiag.microphoneOk ? '正常' : '未检测到' }}</span>
          </div>
          <div class="check-item" :class="{ pass: deviceDiag.speakerOk, fail: !deviceDiag.speakerOk }">
            <span class="icon">{{ deviceDiag.speakerOk ? '✓' : '✗' }}</span>
            <span class="name">扬声器</span>
            <span class="status">{{ deviceDiag.speakerOk ? '正常' : '未检测到' }}</span>
          </div>
          <div class="check-item info">
            <span class="icon">📶</span>
            <span class="name">网络</span>
            <span class="status">{{ deviceDiag.networkType }} · {{ deviceDiag.bandwidthKbps }}kbps</span>
          </div>
          <div class="check-item info">
            <span class="icon">🔋</span>
            <span class="name">电量</span>
            <span class="status">{{ deviceDiag.battery }}%</span>
          </div>
        </div>
        <div v-if="deviceDiag.suggestions && deviceDiag.suggestions.length" class="suggestions">
          <p class="suggest-title">💡 优化建议</p>
          <p v-for="(s, i) in deviceDiag.suggestions" :key="i" class="suggest-item">· {{ s }}</p>
        </div>
        <div class="quality-score">
          <div class="score-circle" :class="getQualityClass(deviceDiag.qualityScore)">
            {{ deviceDiag.qualityScore }}
          </div>
          <p>设备质量评分</p>
        </div>
        <button class="primary-btn" @click="confirmDevice" :disabled="!deviceDiag.cameraOk || !deviceDiag.microphoneOk">
          继续
        </button>
      </div>

      <!-- 步骤 3: 知情同意 -->
      <div v-else-if="step === 'consent'" class="card">
        <div class="card-icon">📋</div>
        <h2>知情同意</h2>
        <p class="desc">请仔细阅读以下条款,确认后开始录制</p>
        <div class="consent-list">
          <div class="consent-item">✓ 本次销售过程将全程录音录像</div>
          <div class="consent-item">✓ 录像内容将作为法律证据,严格保密</div>
          <div class="consent-item">✓ 您可随时要求暂停或终止录制</div>
          <div class="consent-item">✓ 仅用于本业务办理,不作其他用途</div>
        </div>
        <div class="checkbox-row">
          <input type="checkbox" id="consent-cb" v-model="consentChecked" />
          <label for="consent-cb">我已阅读并同意以上条款</label>
        </div>
        <button class="primary-btn" :disabled="!consentChecked" @click="consent">
          我同意,开始录制
        </button>
      </div>

      <!-- 步骤 4: 录制中(流程指引) -->
      <div v-else-if="step === 'recording'" class="recording-card">
        <!-- 进度条 -->
        <div class="progress-bar">
          <div class="progress-fill" :style="{ width: progress + '%' }"></div>
        </div>
        <p class="progress-text">流程进度: {{ completedSteps.length }} / 11</p>

        <!-- 当前节点 -->
        <div class="current-node">
          <div class="node-icon">🎙</div>
          <h3>{{ currentNodeName }}</h3>
          <p class="node-tip">{{ currentNodeTip }}</p>
        </div>

        <!-- 提示信息 -->
        <div class="tip-card">
          <p>📞 坐席: {{ agentName }}</p>
          <p>⏱ 已用时: {{ formatTime(elapsedSeconds) }}</p>
          <p>📶 网络: {{ deviceDiag.networkType }}</p>
        </div>

        <!-- 节点指引 -->
        <div class="step-guide">
          <div v-for="n in 11" :key="n" class="step-dot" :class="{
            done: completedSteps.includes('N' + String(n).padStart(2, '0')),
            current: currentNode === n
          }">
            {{ completedSteps.includes('N' + String(n).padStart(2, '0')) ? '✓' : n }}
          </div>
        </div>

        <!-- 操作按钮 -->
        <div class="action-bar">
          <button class="secondary-btn" @click="showHelp">❓ 帮助</button>
          <button class="secondary-btn" @click="showReport">⚠ 投诉</button>
          <button class="secondary-btn" @click="showLeave">🚪 离开</button>
        </div>

        <!-- 签字画板按钮 -->
        <button v-if="currentNode === 11" class="primary-btn signature-btn" @click="showSignature">
          ✍️  客户签字
        </button>
      </div>

      <!-- 步骤 5: 签字画板 -->
      <div v-else-if="step === 'signature'" class="signature-card">
        <h2>✍️ 请在下方签名</h2>
        <p class="desc">请用手指在白色区域内签字</p>
        <div class="signature-pad-wrapper">
          <canvas ref="signaturePadRef"
            class="signature-pad"
            @touchstart="startDraw"
            @touchmove="draw"
            @mousedown="startDrawMouse"
            @mousemove="drawMouse"
            @mouseup="endDraw"
          ></canvas>
          <div class="signature-line"></div>
          <p class="signature-hint">↑ 在此区域签字 ↑</p>
        </div>
        <div class="sig-actions">
          <button class="secondary-btn" @click="clearSignature">清空</button>
          <button class="primary-btn" @click="submitSignature" :disabled="!hasDrawn">
            确认提交
          </button>
        </div>
      </div>

      <!-- 步骤 6: 服务评价 -->
      <div v-else-if="step === 'rating'" class="card">
        <div class="card-icon">⭐</div>
        <h2>服务评价</h2>
        <p class="desc">您的反馈是我们改进的动力</p>
        <div class="stars">
          <span v-for="n in 5" :key="n" class="star" :class="{ active: ratingStars >= n }"
            @click="ratingStars = n">★</span>
        </div>
        <div class="tag-selector">
          <p class="tag-label">服务感受(可多选):</p>
          <div class="tags">
            <span v-for="tag in ratingTags" :key="tag"
              :class="['tag', { active: selectedTags.includes(tag) }]"
              @click="toggleTag(tag)">{{ tag }}</span>
          </div>
        </div>
        <textarea v-model="ratingComment" placeholder="其他建议(可选)..." class="comment-input" maxlength="200" />
        <button class="primary-btn" @click="submitRating">提交评价</button>
      </div>

      <!-- 步骤 7: 完成 -->
      <div v-else-if="step === 'done'" class="card">
        <div class="card-icon success">🎉</div>
        <h2>已完成</h2>
        <p class="desc">感谢您的配合,本次业务办理已完成</p>
        <div class="success-info">
          <p>📋 录像已加密保存</p>
          <p>🔗 存证已上链</p>
          <p>📧 报告将发送到您的邮箱</p>
        </div>
        <button class="primary-btn" @click="reset">完成</button>
      </div>

      <!-- 浮动提示 -->
      <div v-if="toast" class="toast" :class="toastType">{{ toast }}</div>
    </div>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted, onUnmounted, nextTick } from 'vue'
import { submitRating } from '@/api/customer'
import axios from '@/utils/request'

const step = ref('join')
const sessionIdInput = ref('')
const sessionId = ref('')
const agentName = ref('客户经理')
const consentChecked = ref(false)
const currentNode = ref(1)
const currentNodeName = ref('N01 - 知情同意')
const currentNodeTip = ref('请仔细阅读条款,确认后开始')
const completedSteps = ref([])
const elapsedSeconds = ref(0)
let timer = null
let signatureCtx = null
const signaturePadRef = ref(null)
const hasDrawn = ref(false)
let isDrawing = false
let lastX = 0
let lastY = 0

const deviceDiag = reactive({
  cameraOk: false,
  microphoneOk: false,
  speakerOk: false,
  networkType: 'WiFi',
  bandwidthKbps: 1500,
  latencyMs: 50,
  battery: 80,
  qualityScore: 0,
  suggestions: []
})

const ratingStars = ref(5)
const ratingTags = ['专业耐心', '清晰易懂', '高效便捷', '服务周到', '讲解细致']
const selectedTags = ref(['专业耐心', '高效便捷'])
const ratingComment = ref('')

const toast = ref('')
const toastType = ref('info')
let toastTimer = null

const showToast = (msg, type = 'info', duration = 2000) => {
  toast.value = msg
  toastType.value = type
  if (toastTimer) clearTimeout(toastTimer)
  toastTimer = setTimeout(() => toast.value = '', duration)
}

const progress = computed(() =>
  Math.round(completedSteps.value.length / 11 * 100))

const formatTime = (sec) => {
  const m = Math.floor(sec / 60)
  const s = sec % 60
  return `${m}:${String(s).padStart(2, '0')}`
}

const getQualityClass = (score) => {
  if (score >= 80) return 'excellent'
  if (score >= 60) return 'good'
  if (score >= 40) return 'fair'
  return 'poor'
}

// ========== 流程控制 ==========
const joinSession = async () => {
  try {
    const res = await axios.post('/customer-h5/join', {
      sessionId: sessionIdInput.value,
      customerId: 'CUST_' + sessionIdInput.value,
      deviceId: 'H5_' + Date.now()
    })
    sessionId.value = res.data.data.sessionId
    showToast('已接入会话', 'success')
    step.value = 'diagnose'
    runDeviceCheck()
  } catch (e) {
    showToast('接入失败: ' + e.message, 'error')
  }
}

const runDeviceCheck = async () => {
  try {
    // 浏览器 API 检测
    deviceDiag.cameraOk = false
    deviceDiag.microphoneOk = false
    deviceDiag.speakerOk = true
    try {
      const stream = await navigator.mediaDevices.getUserMedia({ video: true, audio: true })
      deviceDiag.cameraOk = stream.getVideoTracks().length > 0
      deviceDiag.microphoneOk = stream.getAudioTracks().length > 0
      // 检测后立即释放(避免占着)
      stream.getTracks().forEach(t => t.stop())
    } catch (e) {
      // 浏览器拒绝或不支持
    }
    // 网络类型
    const conn = navigator.connection || navigator.webkitConnection
    if (conn) {
      deviceDiag.networkType = conn.effectiveType?.toUpperCase() || 'WiFi'
      deviceDiag.bandwidthKbps = conn.downlink ? Math.round(conn.downlink * 1000) : 1500
    }
    // 延迟检测
    deviceDiag.latencyMs = 50
    // 电量
    if (navigator.getBattery) {
      const battery = await navigator.getBattery()
      deviceDiag.battery = Math.round(battery.level * 100)
    }
    // 提交后端
    const res = await axios.post('/customer-h5/diagnose', {
      sessionId: sessionId.value,
      cameraOk: deviceDiag.cameraOk,
      microphoneOk: deviceDiag.microphoneOk,
      speakerOk: deviceDiag.speakerOk,
      networkType: deviceDiag.networkType,
      bandwidthKbps: deviceDiag.bandwidthKbps,
      latencyMs: deviceDiag.latencyMs,
      battery: deviceDiag.battery
    })
    Object.assign(deviceDiag, res.data.data)
  } catch (e) {
    showToast('设备检测失败', 'error')
  }
}

const confirmDevice = () => {
  step.value = 'consent'
}

const consent = () => {
  step.value = 'recording'
  startTimer()
  // 模拟自动推进节点
  simulateSteps()
}

const startTimer = () => {
  stopTimer()
  timer = setInterval(() => {
    elapsedSeconds.value++
    updateProgress()
  }, 1000)
}

const stopTimer = () => {
  if (timer) clearInterval(timer)
  timer = null
}

const updateProgress = async () => {
  try {
    await axios.post('/customer-h5/progress', {
      sessionId: sessionId.value,
      currentStep: currentNode.value,
      stepName: 'N' + String(currentNode.value).padStart(2, '0')
    })
  } catch (e) { /* 静默 */ }
}

const simulateSteps = () => {
  // 演示:每 5 秒推进一个节点
  setInterval(() => {
    if (currentNode.value < 11) {
      completedSteps.value.push('N' + String(currentNode.value).padStart(2, '0'))
      currentNode.value++
      updateNodeDisplay()
      if (currentNode.value === 11) {
        showToast('请准备签字', 'info', 3000)
      }
    }
  }, 8000)
}

const updateNodeDisplay = () => {
  const names = {
    1: 'N01 - 知情同意',
    2: 'N02 - 客户身份核验',
    3: 'N03 - 需求了解',
    4: 'N04 - 风险揭示',
    5: 'N05 - 产品介绍',
    6: 'N06 - 费用说明',
    7: 'N07 - 合同条款',
    8: 'N08 - 客户答疑',
    9: 'N09 - 信息确认',
    10: 'N10 - 客户决定',
    11: 'N11 - 签字确认'
  }
  const tips = {
    1: '请配合坐席进行身份核验',
    2: '请出示您的身份证件',
    3: '请向坐席说明您的需求',
    4: '请认真听取风险提示',
    5: '请了解产品详情',
    6: '请了解相关费用',
    7: '请认真阅读合同条款',
    8: '请向坐席提出您的疑问',
    9: '请确认所有信息无误',
    10: '请做出您的最终决定',
    11: '请用手指在下方签字'
  }
  currentNodeName.value = names[currentNode.value] || `N${currentNode.value}`
  currentNodeTip.value = tips[currentNode.value] || '请按坐席指引操作'
}

// ========== 帮助/投诉/离开 ==========
const showHelp = () => {
  ElMessage?.alert?.('如需帮助,请联系客服: 400-888-8888') ||
    showToast('如需帮助,请联系客服 400-888-8888', 'info', 3000)
}
const showReport = () => {
  showToast('投诉渠道已开启,客服将与您联系', 'warn', 3000)
}
const showLeave = async () => {
  if (window.confirm('确定要离开会话吗?')) {
    await axios.post(`/customer-h5/leave/${sessionId.value}`)
    stopTimer()
    showToast('已离开会话', 'info')
    step.value = 'join'
  }
}

// ========== 签字画板 ==========
const showSignature = () => {
  step.value = 'signature'
  nextTick(() => initSignaturePad())
}

const initSignaturePad = () => {
  const canvas = signaturePadRef.value
  if (!canvas) return
  // 设置画布尺寸为实际像素
  const rect = canvas.getBoundingClientRect()
  canvas.width = rect.width * 2
  canvas.height = rect.height * 2
  signatureCtx = canvas.getContext('2d')
  signatureCtx.scale(2, 2)
  signatureCtx.strokeStyle = '#1e3a8a'
  signatureCtx.lineWidth = 3
  signatureCtx.lineCap = 'round'
  signatureCtx.lineJoin = 'round'
  // 触摸事件阻止滚动
  canvas.addEventListener('touchstart', e => e.preventDefault())
  canvas.addEventListener('touchmove', e => e.preventDefault())
}

const startDraw = (e) => {
  isDrawing = true
  const t = e.touches[0]
  const rect = signaturePadRef.value.getBoundingClientRect()
  lastX = t.clientX - rect.left
  lastY = t.clientY - rect.top
  hasDrawn = true
}
const draw = (e) => {
  if (!isDrawing) return
  const t = e.touches[0]
  const rect = signaturePadRef.value.getBoundingClientRect()
  const x = t.clientX - rect.left
  const y = t.clientY - rect.top
  signatureCtx.beginPath()
  signatureCtx.moveTo(lastX, lastY)
  signatureCtx.lineTo(x, y)
  signatureCtx.stroke()
  lastX = x
  lastY = y
}
const startDrawMouse = (e) => {
  isDrawing = true
  const rect = signaturePadRef.value.getBoundingClientRect()
  lastX = e.clientX - rect.left
  lastY = e.clientY - rect.top
  hasDrawn = true
}
const drawMouse = (e) => {
  if (!isDrawing) return
  const rect = signaturePadRef.value.getBoundingClientRect()
  const x = e.clientX - rect.left
  const y = e.clientY - rect.top
  signatureCtx.beginPath()
  signatureCtx.moveTo(lastX, lastY)
  signatureCtx.lineTo(x, y)
  signatureCtx.stroke()
  lastX = x
  lastY = y
}
const endDraw = () => {
  isDrawing = false
}

const clearSignature = () => {
  if (!signatureCtx) return
  const canvas = signaturePadRef.value
  signatureCtx.clearRect(0, 0, canvas.width, canvas.height)
  hasDrawn = false
}

const submitSignature = async () => {
  try {
    const base64 = signaturePadRef.value.toDataURL('image/png')
    await axios.post('/customer-h5/signature', {
      sessionId: sessionId.value,
      nodeId: 'N11',
      imageBase64: base64
    })
    showToast('签字已提交', 'success')
    step.value = 'rating'
  } catch (e) {
    showToast('提交失败', 'error')
  }
}

// ========== 评价 ==========
const toggleTag = (tag) => {
  const idx = selectedTags.value.indexOf(tag)
  if (idx >= 0) selectedTags.value.splice(idx, 1)
  else selectedTags.value.push(tag)
}

const submitRating = async () => {
  try {
    await axios.post('/customer-h5/rating', {
      sessionId: sessionId.value,
      stars: ratingStars.value,
      comment: ratingComment.value,
      tags: selectedTags.value
    })
    showToast('评价已提交', 'success')
    step.value = 'done'
  } catch (e) {
    showToast('提交失败', 'error')
  }
}

const reset = () => {
  step.value = 'join'
  sessionIdInput.value = ''
  completedSteps.value = []
  currentNode.value = 1
  elapsedSeconds.value = 0
  ratingStars.value = 5
  ratingComment.value = ''
  selectedTags.value = []
  stopTimer()
}

onMounted(() => {})
onUnmounted(() => {
  stopTimer()
  if (toastTimer) clearTimeout(toastTimer)
})
</script>

<style lang="scss" scoped>
.h5-customer {
  min-height: 100vh;
  background: linear-gradient(135deg, #1e3a8a 0%, #4f8cff 50%, #6fa3ff 100%);
  padding: 20px 16px;
  display: flex;
  align-items: flex-start;
  justify-content: center;
}

.h5-container {
  max-width: 420px;
  width: 100%;
}

.brand-bar {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  padding: 12px;
  color: white;

  .logo {
    font-size: 28px;
  }
  .brand-name {
    font-size: 18px;
    font-weight: 700;
  }
  .brand-tag {
    background: rgba(255, 255, 255, 0.2);
    padding: 2px 8px;
    border-radius: 4px;
    font-size: 12px;
  }
}

.card, .recording-card, .signature-card {
  background: white;
  border-radius: 16px;
  padding: 24px 20px;
  margin: 16px 0;
  box-shadow: 0 8px 24px rgba(0, 0, 0, 0.15);

  .card-icon {
    text-align: center;
    font-size: 48px;
    margin-bottom: 12px;

    &.success { color: #16a34a; }
  }
  h2 {
    text-align: center;
    margin: 0 0 8px;
    color: #1e3a8a;
    font-size: 22px;
  }
  .desc {
    text-align: center;
    color: #6b7280;
    margin: 0 0 20px;
    font-size: 14px;
    line-height: 1.5;
  }
}

.session-input {
  margin: 24px 0;
  label {
    display: block;
    color: #374151;
    font-size: 13px;
    margin-bottom: 6px;
  }
  input {
    width: 100%;
    padding: 14px;
    border: 2px solid #e5e7eb;
    border-radius: 8px;
    font-size: 24px;
    text-align: center;
    letter-spacing: 8px;
    font-weight: 600;
    color: #1e3a8a;
    box-sizing: border-box;
    &:focus { outline: none; border-color: #4f8cff; }
  }
}

.primary-btn, .secondary-btn {
  width: 100%;
  padding: 14px;
  border: none;
  border-radius: 8px;
  font-size: 16px;
  font-weight: 600;
  cursor: pointer;
  margin: 8px 0;
  transition: all 0.2s;
}
.primary-btn {
  background: linear-gradient(135deg, #1e3a8a, #4f8cff);
  color: white;
  &:disabled { opacity: 0.5; cursor: not-allowed; }
}
.secondary-btn {
  background: #f3f4f6;
  color: #374151;
  border: 1px solid #e5e7eb;
}

.trust-badges {
  display: flex;
  justify-content: space-around;
  margin-top: 16px;
  padding-top: 16px;
  border-top: 1px solid #e5e7eb;
  font-size: 12px;
  color: #6b7280;
}

.check-list {
  margin: 16px 0;
}
.check-item {
  display: flex;
  align-items: center;
  padding: 12px;
  margin: 6px 0;
  background: #f9fafb;
  border-radius: 8px;
  border-left: 4px solid #e5e7eb;

  &.pass { border-left-color: #16a34a; background: #dcfce7; }
  &.fail { border-left-color: #dc2626; background: #fecaca; }
  &.info { border-left-color: #4f8cff; background: #dbeafe; }

  .icon {
    font-size: 20px;
    width: 32px;
    text-align: center;
  }
  .name {
    flex: 1;
    font-weight: 600;
    color: #374151;
  }
  .status {
    color: #6b7280;
    font-size: 12px;
  }
}

.suggestions {
  background: #fef3c7;
  border-radius: 8px;
  padding: 12px;
  margin: 12px 0;
  .suggest-title {
    margin: 0 0 6px;
    color: #92400e;
    font-weight: 600;
  }
  .suggest-item {
    margin: 2px 0;
    color: #78350f;
    font-size: 13px;
  }
}

.quality-score {
  text-align: center;
  margin: 16px 0;
  .score-circle {
    display: inline-flex;
    align-items: center;
    justify-content: center;
    width: 80px;
    height: 80px;
    border-radius: 50%;
    font-size: 28px;
    font-weight: 700;
    color: white;
    margin-bottom: 8px;
    &.excellent { background: linear-gradient(135deg, #16a34a, #5fd0a4); }
    &.good { background: linear-gradient(135deg, #4f8cff, #93c5fd); }
    &.fair { background: linear-gradient(135deg, #f59e0b, #fbbf24); }
    &.poor { background: linear-gradient(135deg, #dc2626, #f87171); }
  }
  p { margin: 0; color: #6b7280; font-size: 13px; }
}

.consent-list {
  background: #f9fafb;
  border-radius: 8px;
  padding: 12px;
  margin: 12px 0;
  .consent-item {
    padding: 6px 0;
    color: #374151;
    font-size: 14px;
  }
}

.checkbox-row {
  display: flex;
  align-items: center;
  gap: 8px;
  margin: 16px 0;
  input { width: 18px; height: 18px; }
  label { color: #374151; font-size: 14px; }
}

.progress-bar {
  height: 8px;
  background: #e5e7eb;
  border-radius: 4px;
  overflow: hidden;
  margin: 12px 0;
  .progress-fill {
    height: 100%;
    background: linear-gradient(90deg, #1e3a8a, #4f8cff);
    transition: width 0.3s;
  }
}
.progress-text {
  text-align: center;
  color: #6b7280;
  font-size: 12px;
  margin: 4px 0 12px;
}

.current-node {
  text-align: center;
  margin: 20px 0;
  .node-icon {
    font-size: 48px;
    margin-bottom: 8px;
  }
  h3 {
    color: #1e3a8a;
    margin: 8px 0 4px;
    font-size: 20px;
  }
  .node-tip {
    color: #6b7280;
    font-size: 14px;
    margin: 0;
  }
}

.tip-card {
  background: #dbeafe;
  border-radius: 8px;
  padding: 12px;
  margin: 12px 0;
  p {
    margin: 4px 0;
    color: #1e3a8a;
    font-size: 13px;
  }
}

.step-guide {
  display: flex;
  justify-content: space-between;
  margin: 16px 0;
  flex-wrap: wrap;
  gap: 4px;
  .step-dot {
    width: 24px;
    height: 24px;
    border-radius: 50%;
    background: #e5e7eb;
    color: #6b7280;
    display: flex;
    align-items: center;
    justify-content: center;
    font-size: 11px;
    font-weight: 600;
    &.done { background: #16a34a; color: white; }
    &.current {
      background: #4f8cff;
      color: white;
      box-shadow: 0 0 0 4px rgba(79, 140, 255, 0.3);
    }
  }
}

.action-bar {
  display: flex;
  gap: 6px;
  margin: 12px 0;
  button { flex: 1; margin: 0; padding: 10px; font-size: 13px; }
}

.signature-btn {
  background: linear-gradient(135deg, #16a34a, #5fd0a4);
}

.signature-pad-wrapper {
  position: relative;
  margin: 16px 0;
}
.signature-pad {
  width: 100%;
  height: 200px;
  background: #fafafa;
  border: 2px dashed #4f8cff;
  border-radius: 8px;
  touch-action: none;
  cursor: crosshair;
}
.signature-line {
  position: absolute;
  bottom: 40px;
  left: 16px;
  right: 16px;
  border-bottom: 1px solid #9ca3af;
}
.signature-hint {
  text-align: center;
  color: #9ca3af;
  font-size: 12px;
  position: absolute;
  bottom: 12px;
  left: 0;
  right: 0;
  pointer-events: none;
}
.sig-actions {
  display: flex;
  gap: 8px;
  button { flex: 1; margin: 0; }
}

.stars {
  text-align: center;
  margin: 16px 0;
  .star {
    font-size: 48px;
    color: #e5e7eb;
    cursor: pointer;
    margin: 0 4px;
    &.active { color: #fbbf24; }
  }
}
.tag-selector {
  margin: 16px 0;
  .tag-label {
    color: #374151;
    font-size: 13px;
    margin-bottom: 8px;
  }
  .tags {
    display: flex;
    flex-wrap: wrap;
    gap: 6px;
  }
  .tag {
    padding: 6px 12px;
    background: #f3f4f6;
    color: #6b7280;
    border-radius: 16px;
    font-size: 13px;
    cursor: pointer;
    &.active { background: #4f8cff; color: white; }
  }
}
.comment-input {
  width: 100%;
  min-height: 60px;
  padding: 10px;
  border: 1px solid #e5e7eb;
  border-radius: 8px;
  font-size: 14px;
  resize: none;
  box-sizing: border-box;
}

.success-info {
  background: #dcfce7;
  border-radius: 8px;
  padding: 16px;
  margin: 16px 0;
  p {
    margin: 4px 0;
    color: #166534;
    text-align: center;
  }
}

.toast {
  position: fixed;
  top: 50%;
  left: 50%;
  transform: translate(-50%, -50%);
  background: rgba(0, 0, 0, 0.85);
  color: white;
  padding: 12px 20px;
  border-radius: 8px;
  z-index: 1000;
  font-size: 14px;
  &.success { background: rgba(22, 163, 74, 0.95); }
  &.error { background: rgba(220, 38, 38, 0.95); }
  &.warn { background: rgba(234, 88, 12, 0.95); }
}
</style>
