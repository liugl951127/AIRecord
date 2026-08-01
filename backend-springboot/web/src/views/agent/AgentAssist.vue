<template>
  <div class="agent-assist">
    <!-- 顶部状态栏 -->
    <div class="top-status">
      <div class="status-left">
        <h2>💼 坐席工作台</h2>
        <span class="agent-info">坐席: {{ agentName }} · 工号 {{ agentId }}</span>
      </div>
      <div class="status-right">
        <el-tag :type="customerOnline ? 'success' : 'info'" size="large">
          <el-icon><User /></el-icon>
          {{ customerOnline ? '客户在线' : '客户离线' }}
        </el-tag>
        <el-tag :type="churnLevelColor" size="large" effect="dark">
          流失风险: {{ dash.churnLevel || 'LOW' }}
        </el-tag>
        <el-tag :type="qualityScoreColor" size="large" effect="dark">
          服务质量: {{ dash.serviceQualityScore || 0 }}/100
        </el-tag>
        <el-tag type="warning" size="large">
          <el-icon><Clock /></el-icon>
          节点 {{ dash.currentNodeName }} · {{ formatTime(elapsed) }}
        </el-tag>
      </div>
    </div>

    <div class="main-grid">
      <!-- 左栏:客户全景 -->
      <el-card class="left-card" shadow="hover">
        <template #header>
          <span>👤 客户全景</span>
        </template>
        <div class="customer-info">
          <div class="info-row">
            <span class="info-label">会话ID</span>
            <span class="info-value">{{ sessionId }}</span>
          </div>
          <div class="info-row">
            <span class="info-label">加入时间</span>
            <span class="info-value">{{ formatDateTime(dash.customerJoinTime) }}</span>
          </div>
          <div class="info-row">
            <span class="info-label">已完成节点</span>
            <span class="info-value">{{ dash.stepsCompleted || 0 }} / 11</span>
          </div>
        </div>

        <!-- 情绪仪表盘 -->
        <div class="emotion-meter">
          <h4>😊 客户情绪</h4>
          <div class="emotion-bar">
            <div class="emotion-fill" :style="emotionStyle"></div>
            <div class="emotion-marker" :style="{ left: customerEmotion + '%' }">▼</div>
          </div>
          <div class="emotion-labels">
            <span>愤怒 😡</span>
            <span>不满 😟</span>
            <span>平静 😐</span>
            <span>满意 🙂</span>
            <span>愉悦 😄</span>
          </div>
        </div>

        <!-- 设备质量 -->
        <div class="device-info">
          <h4>📶 设备质量</h4>
          <div class="quality-ring" :class="getQualityClass(dash.deviceQualityScore)">
            {{ dash.deviceQualityScore || 0 }}
          </div>
          <p class="quality-label">评分</p>
          <ul v-if="dash.deviceSuggestions" class="suggestions">
            <li v-for="(s, i) in dash.deviceSuggestions" :key="i">· {{ s }}</li>
          </ul>
        </div>
      </el-card>

      <!-- 中栏:流程 + 计时器 + 流失预警 -->
      <el-card class="middle-card" shadow="hover">
        <template #header>
          <span>📋 流程进度</span>
          <el-button-group size="small" style="float:right">
            <el-button @click="urgeCustomer" :icon="Promotion">催促</el-button>
            <el-button @click="calmCustomer" :icon="MagicStick">安抚</el-button>
            <el-button @click="retentionAction" type="warning" :icon="Warning">挽回</el-button>
          </el-button-group>
        </template>

        <!-- 节点计时器 -->
        <div class="timer-section">
          <div class="timer-circle" :class="{ overtime: dash.isOvertime }">
            <div class="timer-value">{{ formatTime(elapsed) }}</div>
            <div class="timer-label">本节点已用时</div>
            <div class="timer-best">最佳: {{ formatTime(dash.nodeBestDuration || 0) }}</div>
          </div>
          <div class="timer-bar">
            <div class="timer-fill" :style="{ width: (dash.nodeProgress || 0) + '%' }"></div>
          </div>
          <p v-if="dash.isOvertime" class="overtime-warn">⚠ 已超过最佳时长</p>
        </div>

        <!-- 流失预警 -->
        <div v-if="(dash.churnRisk || 0) > 0.3" class="churn-warn" :class="'level-' + (dash.churnLevel || 'LOW').toLowerCase()">
          <h4>⚠ 流失预警({{ dash.churnLevel }})</h4>
          <p>风险评分: {{ Math.round((dash.churnRisk || 0) * 100) }}/100</p>
          <ul>
            <li v-for="(r, i) in dash.churnReasons" :key="i">· {{ r }}</li>
          </ul>
          <div class="retention-actions">
            <h5>🎯 建议行动:</h5>
            <ul>
              <li v-for="(a, i) in retentionActions" :key="i">{{ a }}</li>
            </ul>
          </div>
        </div>

        <!-- 流程节点 -->
        <div class="node-flow">
          <h4>流程节点</h4>
          <div class="node-list">
            <div v-for="n in 11" :key="n" class="node-item" :class="{
              done: n < (dash.currentNode || 1),
              current: n === (dash.currentNode || 1),
              pending: n > (dash.currentNode || 1)
            }">
              <span class="node-num">{{ n }}</span>
              <span class="node-name">{{ nodeNames[n - 1] }}</span>
              <span class="node-state">
                {{ n < (dash.currentNode || 1) ? '✓' : n === (dash.currentNode || 1) ? '●' : '○' }}
              </span>
            </div>
          </div>
        </div>
      </el-card>

      <!-- 右栏:话术推荐 + 一键操作 -->
      <el-card class="right-card" shadow="hover">
        <template #header>
          <span>💡 智能话术</span>
          <el-select v-model="mood" size="small" style="float:right;width:100px" @change="loadScripts">
            <el-option label="正常" value="NORMAL" />
            <el-option label="激动" value="AGITATED" />
            <el-option label="犹豫" value="HESITANT" />
          </el-select>
        </template>

        <div class="script-list">
          <div v-for="(s, i) in scripts" :key="i" class="script-card" :class="'style-' + s.style.toLowerCase()">
            <div class="script-header">
              <el-tag size="small" :type="getScriptTagType(s.style)">{{ s.style }}</el-tag>
              <span class="priority">优先级 {{ s.priority }}</span>
            </div>
            <div class="script-content">{{ s.template }}</div>
            <div class="script-actions">
              <el-button size="small" @click="copyScript(s)">📋 复制</el-button>
              <el-button size="small" type="primary" @click="useScript(s)">✓ 使用</el-button>
            </div>
          </div>
          <el-empty v-if="!scripts.length" description="暂无话术" />
        </div>
      </el-card>
    </div>
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted, onUnmounted } from 'vue'
import {
  ElMessage, ElMessageBox
} from 'element-plus'
import {
  User, Clock, Warning, Promotion, MagicStick
} from '@element-plus/icons-vue'
import axios from '@/utils/request'

const sessionId = ref('SESS_' + Date.now().toString().slice(-6))
const agentName = ref('李经理')
const agentId = ref('AGT_001')
const elapsed = ref(0)
const mood = ref('NORMAL')
let timer = null
let dashboardTimer = null

const dash = reactive({})
const scripts = ref([])
const retentionActions = ref([])
const customerEmotion = ref(70)
const customerOnline = ref(true)

const nodeNames = [
  '知情同意', '身份核验', '需求了解', '风险揭示', '产品介绍',
  '费用说明', '合同条款', '客户答疑', '信息确认', '客户决定', '签字确认'
]

const customerOnlineComputed = computed(() => customerOnline.value)

const churnLevelColor = computed(() => ({
  LOW: 'success', MEDIUM: 'warning', HIGH: 'danger'
}[dash.churnLevel] || 'info'))

const qualityScoreColor = computed(() => {
  const s = dash.serviceQualityScore || 0
  if (s >= 80) return 'success'
  if (s >= 60) return 'warning'
  return 'danger'
})

const emotionStyle = computed(() => {
  const e = customerEmotion.value
  let color = '#5fd0a4'
  if (e < 30) color = '#ff6b6b'
  else if (e < 50) color = '#ffb454'
  return {
    background: `linear-gradient(90deg, #ff6b6b 0%, #ffb454 30%, #4f8cff 50%, #5fd0a4 80%, #16a34a 100%)`,
    opacity: 0.3
  }
})

const formatTime = (sec) => {
  if (!sec && sec !== 0) return '0:00'
  const m = Math.floor(sec / 60)
  const s = sec % 60
  return `${m}:${String(s).padStart(2, '0')}`
}

const formatDateTime = (dt) => {
  if (!dt) return '-'
  return new Date(dt).toLocaleString('zh-CN')
}

const getQualityClass = (score) => {
  if (score >= 80) return 'excellent'
  if (score >= 60) return 'good'
  if (score >= 40) return 'fair'
  return 'poor'
}

const getScriptTagType = (style) => ({
  STANDARD: 'primary', FRIENDLY: 'success', PROFESSIONAL: 'info',
  WARNING: 'danger', EMPATHY: 'warning', EVIDENCE: 'info',
  RISK_FOCUS: 'danger', DETAILED: 'warning'
}[style] || 'info')

const loadDashboard = async () => {
  try {
    const res = await axios.get(`/customer-h5/agent/dashboard/${sessionId.value}`, {
      params: { currentNode: dash.currentNode || 1, elapsed: elapsed.value }
    })
    Object.assign(dash, res.data.data)
    customerOnline.value = dash.customerOnline !== false
    if (dash.churnRisk) {
      customerEmotion.value = Math.round((1 - dash.churnRisk) * 100)
    }
  } catch (e) {
    // 兜底
    Object.assign(dash, {
      sessionId: sessionId.value,
      currentNode: 1,
      currentNodeName: 'N01',
      nodeBestDuration: 60,
      nodeProgress: Math.round(elapsed.value / 60 * 100),
      isOvertime: elapsed.value > 60,
      customerOnline: true,
      stepsCompleted: 0,
      deviceQualityScore: 85,
      deviceSuggestions: [],
      churnRisk: 0.1,
      churnLevel: 'LOW',
      churnReasons: [],
      serviceQualityScore: 95
    })
  }
}

const loadScripts = async () => {
  try {
    const res = await axios.get('/customer-h5/agent/scripts', {
      params: {
        sessionId: sessionId.value,
        currentNode: 'N' + String(dash.currentNode || 1).padStart(2, '0'),
        mood: mood.value
      }
    })
    scripts.value = res.data.data || []
  } catch (e) {
    scripts.value = []
  }
}

const loadRetention = async () => {
  try {
    const res = await axios.get(`/customer-h5/agent/retention/${sessionId.value}`)
    retentionActions.value = res.data.data || []
  } catch (e) {
    retentionActions.value = []
  }
}

const urgeCustomer = async () => {
  try {
    const res = await axios.get('/customer-h5/agent/urge', {
      params: { sessionId: sessionId.value, idleSeconds: 90 }
    })
    ElMessageBox.confirm(res.data.data || '请继续操作', '催促话术', {
      confirmButtonText: '发送',
      cancelButtonText: '取消'
    }).then(() => {
      ElMessage.success('已发送')
    }).catch(() => {})
  } catch (e) {
    ElMessage.info('请继续操作,如有需要请告诉我')
  }
}

const calmCustomer = async () => {
  try {
    const res = await axios.get(`/customer-h5/agent/calm/${sessionId.value}`)
    await ElMessageBox.alert(res.data.data, '安抚话术', { type: 'info' })
  } catch (e) {
    ElMessage.info('非常理解您的心情,我会尽全力为您解决问题')
  }
}

const retentionAction = async () => {
  await loadRetention()
  if (retentionActions.value.length) {
    await ElMessageBox.alert(
      '🎯 流失挽回建议:\n\n' + retentionActions.value.map((a, i) => `${i + 1}. ${a}`).join('\n'),
      '挽回方案',
      { type: 'warning', confirmButtonText: '我知道了' }
    )
  } else {
    ElMessage.success('客户状态良好,继续按流程服务')
  }
}

const copyScript = async (s) => {
  try {
    await navigator.clipboard.writeText(s.template)
    ElMessage.success('已复制到剪贴板')
  } catch (e) {
    ElMessage.warning('复制失败')
  }
}

const useScript = (s) => {
  ElMessage.success('已使用此话术')
}

onMounted(() => {
  loadDashboard()
  loadScripts()
  // 计时器
  timer = setInterval(() => elapsed.value++, 1000)
  // 仪表盘刷新(每 3 秒)
  dashboardTimer = setInterval(() => {
    loadDashboard()
  }, 3000)
})

onUnmounted(() => {
  if (timer) clearInterval(timer)
  if (dashboardTimer) clearInterval(dashboardTimer)
})
</script>

<style lang="scss" scoped>
.agent-assist {
  padding: 16px;
  background: #f5f7fa;
  min-height: calc(100vh - 60px);
}

.top-status {
  display: flex;
  justify-content: space-between;
  align-items: center;
  background: linear-gradient(135deg, #1e3a8a, #4f8cff);
  padding: 16px 24px;
  border-radius: 12px;
  margin-bottom: 16px;

  .status-left h2 {
    margin: 0;
    color: white;
    font-size: 20px;
  }
  .agent-info {
    color: rgba(255, 255, 255, 0.8);
    font-size: 13px;
    margin-left: 12px;
  }
  .status-right {
    display: flex;
    gap: 8px;
  }
}

.main-grid {
  display: grid;
  grid-template-columns: 1fr 1.2fr 1fr;
  gap: 16px;
}

.customer-info {
  background: #f9fafb;
  border-radius: 8px;
  padding: 12px;
  margin-bottom: 16px;
  .info-row {
    display: flex;
    justify-content: space-between;
    padding: 6px 0;
    border-bottom: 1px dashed #e5e7eb;
    font-size: 13px;
    &:last-child { border-bottom: none; }
    .info-label { color: #6b7280; }
    .info-value { color: #1e3a8a; font-weight: 600; }
  }
}

.emotion-meter {
  margin: 16px 0;
  h4 { margin: 0 0 8px; color: #1e3a8a; }
}
.emotion-bar {
  position: relative;
  height: 12px;
  background: #e5e7eb;
  border-radius: 6px;
  overflow: visible;
  .emotion-fill {
    position: absolute;
    inset: 0;
    border-radius: 6px;
  }
  .emotion-marker {
    position: absolute;
    top: -4px;
    transform: translateX(-50%);
    color: #1e3a8a;
    font-size: 16px;
  }
}
.emotion-labels {
  display: flex;
  justify-content: space-between;
  margin-top: 8px;
  font-size: 11px;
  color: #6b7280;
}

.device-info {
  margin-top: 16px;
  text-align: center;
  h4 { color: #1e3a8a; margin: 0 0 8px; }
  .quality-ring {
    display: inline-flex;
    align-items: center;
    justify-content: center;
    width: 60px;
    height: 60px;
    border-radius: 50%;
    font-size: 20px;
    font-weight: 700;
    color: white;
    &.excellent { background: linear-gradient(135deg, #16a34a, #5fd0a4); }
    &.good { background: linear-gradient(135deg, #4f8cff, #93c5fd); }
    &.fair { background: linear-gradient(135deg, #f59e0b, #fbbf24); }
    &.poor { background: linear-gradient(135deg, #dc2626, #f87171); }
  }
  .quality-label {
    margin: 4px 0 8px;
    color: #6b7280;
    font-size: 12px;
  }
  .suggestions {
    list-style: none;
    padding: 0;
    margin: 0;
    text-align: left;
    background: #fef3c7;
    border-radius: 8px;
    padding: 8px 12px;
    li {
      color: #78350f;
      font-size: 12px;
      padding: 2px 0;
    }
  }
}

.timer-section {
  text-align: center;
  margin: 16px 0;
  .timer-circle {
    display: inline-flex;
    flex-direction: column;
    align-items: center;
    justify-content: center;
    width: 120px;
    height: 120px;
    border-radius: 50%;
    background: linear-gradient(135deg, #4f8cff, #1e3a8a);
    color: white;
    &.overtime {
      background: linear-gradient(135deg, #ff6b6b, #dc2626);
      animation: pulse 1.5s infinite;
    }
  }
  .timer-value {
    font-size: 28px;
    font-weight: 700;
    font-family: 'Consolas', monospace;
  }
  .timer-label {
    font-size: 10px;
    opacity: 0.9;
  }
  .timer-best {
    font-size: 10px;
    opacity: 0.7;
  }
  .timer-bar {
    margin: 8px 0;
    height: 6px;
    background: #e5e7eb;
    border-radius: 3px;
    .timer-fill {
      height: 100%;
      background: linear-gradient(90deg, #5fd0a4, #4f8cff);
      border-radius: 3px;
      transition: width 0.3s;
    }
  }
  .overtime-warn {
    color: #dc2626;
    font-weight: 600;
    font-size: 13px;
  }
}

@keyframes pulse {
  0%, 100% { transform: scale(1); }
  50% { transform: scale(1.05); }
}

.churn-warn {
  border-radius: 8px;
  padding: 12px;
  margin: 12px 0;
  &.level-medium { background: #fef3c7; border: 1px solid #f59e0b; }
  &.level-high { background: #fecaca; border: 1px solid #dc2626; }
  h4 { margin: 0 0 8px; color: #991b1b; }
  p { color: #7f1d1d; font-size: 13px; margin: 4px 0; }
  ul {
    list-style: none;
    padding: 0;
    margin: 8px 0;
    li { color: #7f1d1d; font-size: 12px; padding: 2px 0; }
  }
  .retention-actions {
    margin-top: 8px;
    background: rgba(255, 255, 255, 0.5);
    border-radius: 6px;
    padding: 8px;
    h5 { margin: 0 0 4px; color: #991b1b; font-size: 12px; }
  }
}

.node-flow {
  margin-top: 16px;
  h4 { color: #1e3a8a; margin: 0 0 8px; }
  .node-list {
    display: flex;
    flex-direction: column;
    gap: 4px;
  }
  .node-item {
    display: flex;
    align-items: center;
    padding: 6px 10px;
    border-radius: 6px;
    font-size: 13px;
    .node-num {
      width: 24px;
      height: 24px;
      border-radius: 50%;
      background: #e5e7eb;
      color: #6b7280;
      display: inline-flex;
      align-items: center;
      justify-content: center;
      font-weight: 600;
      font-size: 11px;
      margin-right: 8px;
    }
    .node-name { flex: 1; color: #6b7280; }
    .node-state { color: #6b7280; }
    &.done {
      background: #dcfce7;
      .node-num { background: #16a34a; color: white; }
      .node-name { color: #166534; }
      .node-state { color: #16a34a; font-weight: 700; }
    }
    &.current {
      background: #dbeafe;
      .node-num { background: #4f8cff; color: white; }
      .node-name { color: #1e3a8a; font-weight: 600; }
      .node-state { color: #4f8cff; }
    }
  }
}

.script-list {
  max-height: calc(100vh - 280px);
  overflow-y: auto;
}

.script-card {
  background: #f9fafb;
  border-radius: 8px;
  padding: 12px;
  margin-bottom: 8px;
  border-left: 4px solid #4f8cff;
  &.style-warning { border-left-color: #f59e0b; background: #fef3c7; }
  &.style-empathy { border-left-color: #8b5cf6; background: #f3e8ff; }
  &.style-danger { border-left-color: #dc2626; background: #fecaca; }
  &.style-friendly { border-left-color: #16a34a; background: #dcfce7; }
  .script-header {
    display: flex;
    justify-content: space-between;
    align-items: center;
    margin-bottom: 6px;
    .priority {
      font-size: 11px;
      color: #6b7280;
    }
  }
  .script-content {
    color: #374151;
    font-size: 13px;
    line-height: 1.6;
    margin: 6px 0;
  }
  .script-actions {
    display: flex;
    gap: 6px;
  }
}
</style>
