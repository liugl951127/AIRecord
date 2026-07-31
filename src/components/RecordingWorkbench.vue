<script setup lang="ts">
import { ref, computed, onMounted, nextTick } from 'vue'
import { useRecordingStore } from '@/stores/recording'
import { complianceApi } from '@/api'
import { NODE_DEFINITIONS, STATE_COLORS, STATE_LABELS, getNodeDefinition } from '@/utils/nodes'
import { ElMessage } from 'element-plus'

const store = useRecordingStore()

// ASR 输入
const asrInput = ref('')
const isAsrChecking = ref(false)
const asrHits = ref<Array<{ phrase: string; severity: string; regulationRef: string }>>([])

// 实时禁播词扫描（防抖）
let asrTimer: ReturnType<typeof setTimeout> | null = null
async function onAsrInput() {
  if (asrTimer) clearTimeout(asrTimer)
  if (!asrInput.value || asrInput.value.length < 4) {
    asrHits.value = []
    return
  }
  isAsrChecking.value = true
  asrTimer = setTimeout(async () => {
    try {
      asrHits.value = await complianceApi.scan(asrInput.value)
    } finally {
      isAsrChecking.value = false
    }
  }, 400)
}

// 快速填充（演示用）
function fillExample() {
  const ex = currentNodeDef.value
  if (ex.critical) {
    asrInput.value = '是的，我已了解本产品的风险特征。是的，清楚。明白。'
  } else {
    asrInput.value = ex.mandatoryPhrases[0] || '正常的双录对话内容，理财经理已告知相关风险。'
  }
  onAsrInput()
}

function fillForbidden() {
  asrInput.value = '本产品保本保息，绝对安全，肯定超过 3.8% 收益。'
  onAsrInput()
}

const currentNodeDef = computed(() => store.currentNode)

const isHighSeverity = computed(() => asrHits.value.some(h => h.severity === 'HIGH'))

async function handleCompleteNode() {
  if (!asrInput.value.trim()) {
    ElMessage.warning('请输入 ASR 转写文本')
    return
  }
  try {
    await store.completeCurrentNode(asrInput.value)
    asrInput.value = ''
    asrHits.value = []
  } catch (e: any) {
    ElMessage.error(e.message)
  }
}

async function handleFinalize() {
  try {
    await store.finalize()
    ElMessage.success('AI 终检完成')
  } catch (e: any) {
    ElMessage.error(e.message)
  }
}

async function handleSign() {
  try {
    await store.signAndArchive()
    ElMessage.success('业务已归档')
  } catch (e: any) {
    ElMessage.error(e.message)
  }
}

function handleReset() {
  store.reset()
  asrInput.value = ''
  asrHits.value = []
  ElMessage.info('已重置')
}

const business = computed(() => store.business)
const hasBusiness = computed(() => !!store.business)
const showRiskMatch = computed(() => !!store.riskMatch)
</script>

<template>
  <div>
    <!-- 顶部：业务概况 -->
    <div class="card">
      <h3 class="card-title">
        <span>当前业务</span>
        <span class="actions">
          <el-button v-if="hasBusiness" size="small" @click="handleReset" plain>
            <el-icon><Refresh /></el-icon>重置
          </el-button>
        </span>
      </h3>

      <div v-if="!hasBusiness" style="text-align: center; padding: 40px; color: var(--ink-3);">
        <el-icon size="48" style="margin-bottom: 12px;"><DocumentRemove /></el-icon>
        <p>尚未创建业务</p>
        <p style="font-size: 12px;">请先到「业务创建」标签页创建一个业务</p>
      </div>

      <el-row v-else :gutter="16">
        <el-col :span="6">
          <div class="info-cell">
            <div class="label">业务 ID</div>
            <div class="value mono">{{ business!.businessId }}</div>
          </div>
        </el-col>
        <el-col :span="4">
          <div class="info-cell">
            <div class="label">业务类型</div>
            <div class="value">{{ business!.businessType }}</div>
          </div>
        </el-col>
        <el-col :span="5">
          <div class="info-cell">
            <div class="label">产品</div>
            <div class="value mono" style="font-size: 12px;">{{ business!.productId }}</div>
          </div>
        </el-col>
        <el-col :span="4">
          <div class="info-cell">
            <div class="label">渠道</div>
            <div class="value">{{ business!.channel }}</div>
          </div>
        </el-col>
        <el-col :span="5">
          <div class="info-cell">
            <div class="label">当前状态</div>
            <div class="value">
              <span class="state-badge" :style="{ color: STATE_COLORS[business!.state] || '#666' }">
                {{ business!.state }}
              </span>
            </div>
          </div>
        </el-col>
      </el-row>

      <div v-if="hasBusiness" style="margin-top: 16px;">
        <div style="display: flex; align-items: center; justify-content: space-between; margin-bottom: 4px;">
          <span style="font-size: 12px; color: var(--ink-3);">录制进度</span>
          <span class="mono" style="font-size: 12px; font-weight: 600;">{{ store.progress }}% · {{ store.completedNodes.size }} / 8 节点</span>
        </div>
        <div class="progress-bar">
          <div class="progress-fill" :style="{ width: store.progress + '%' }"></div>
        </div>
      </div>
    </div>

    <!-- 8 节点流程图 -->
    <div class="card">
      <h3 class="card-title">
        <span>8 节点录制流程</span>
        <span class="actions">
          <el-tag size="small" type="info">关键节点 ⑥ ★</el-tag>
        </span>
      </h3>

      <div class="node-flow">
        <template v-for="(node, idx) in NODE_DEFINITIONS" :key="node.code">
          <div
            class="node-step"
            :class="{
              active: idx === store.currentNodeIdx && hasBusiness && business!.state === 'RECORDING',
              completed: store.completedNodes.has(node.code),
              critical: node.critical
            }"
          >
            <div class="node-circle">
              <span v-if="store.completedNodes.has(node.code)"><el-icon><Check /></el-icon></span>
              <span v-else>{{ node.order }}</span>
            </div>
            <div class="node-label">{{ node.displayName }}</div>
          </div>
          <div v-if="idx < NODE_DEFINITIONS.length - 1" class="node-connector"></div>
        </template>
      </div>
    </div>

    <!-- 当前节点操作区 -->
    <el-row v-if="hasBusiness" :gutter="16">
      <!-- 左侧：当前节点 + ASR 输入 -->
      <el-col :span="14">
        <div class="card">
          <h3 class="card-title">
            <span>
              节点 {{ currentNodeDef.order }} · {{ currentNodeDef.displayName }}
              <span v-if="currentNodeDef.critical" style="color: var(--accent); margin-left: 6px;">★ 关键节点</span>
            </span>
          </h3>

          <div v-if="currentNodeDef.critical" class="critical-banner">
            <div class="title">
              <el-icon><Warning /></el-icon>
              关键节点要求
            </div>
            <div>必须由 ASR 识别到"是的/清楚/明白"等肯定词 + 坐席人工双签。命中禁播词将立即阻断。</div>
          </div>

          <p style="color: var(--ink-2); font-size: 13px; margin: 0 0 8px;">
            {{ currentNodeDef.description }}
          </p>

          <el-form-item label="ASR 转写（模拟坐席/客户说的话）">
            <el-input
              v-model="asrInput"
              type="textarea"
              :rows="4"
              :placeholder="`模拟该节点的对话内容... 示例: ${currentNodeDef.mandatoryPhrases[0] || ''}`"
              @input="onAsrInput"
            />
          </el-form-item>

          <!-- 实时禁播词检测 -->
          <div v-if="asrHits.length > 0" class="critical-banner" style="border-left-color: var(--accent-2); background: rgba(193, 69, 58, 0.04);">
            <div class="title" style="color: var(--accent-2);">
              <el-icon><WarningFilled /></el-icon>
              实时检测：命中 {{ asrHits.length }} 个禁播词
              <span v-if="isHighSeverity" style="margin-left: 6px; font-weight: 700;">【将阻断】</span>
            </div>
            <div
              v-for="(hit, i) in asrHits"
              :key="i"
              class="phrase-item forbidden"
              style="margin-top: 6px;"
            >
              <strong>{{ hit.phrase }}</strong>
              <span class="reg-ref">[{{ hit.severity }}] {{ hit.regulationRef }}</span>
            </div>
          </div>

          <div v-if="isAsrChecking" style="font-size: 11px; color: var(--ink-3); margin-top: 6px;">
            <el-icon><Loading /></el-icon> 扫描禁播词中...
          </div>

          <div style="margin-top: 16px; display: flex; gap: 8px; flex-wrap: wrap;">
            <el-button type="primary" :loading="store.isProcessing" @click="handleCompleteNode" size="large">
              <el-icon><Check /></el-icon>
              完成本节点 · 进入下一节点
            </el-button>
            <el-button @click="fillExample" plain>
              <el-icon><EditPen /></el-icon>
              填充示例（合规）
            </el-button>
            <el-button @click="fillForbidden" plain type="warning">
              <el-icon><Warning /></el-icon>
              填充禁播词（演示阻断）
            </el-button>
            <el-button @click="asrInput = ''; asrHits = []" plain>
              <el-icon><Delete /></el-icon>
              清空
            </el-button>
          </div>
        </div>
      </el-col>

      <!-- 右侧：必播项 + 必问 + 告警 -->
      <el-col :span="10">
        <!-- 风险匹配结果 -->
        <div v-if="showRiskMatch" class="card">
          <h3 class="card-title">
            <span>适当性匹配结果</span>
            <span class="state-badge" :style="{ color: store.riskMatch.matchResult?.matched ? 'var(--green)' : 'var(--accent-2)' }">
              {{ store.riskMatch.matchResult?.matched ? '✓ 匹配' : '! 不匹配' }}
            </span>
          </h3>
          <pre style="background: var(--bg-2); padding: 10px; border-radius: 6px; font-size: 11px; margin: 0;">{{ JSON.stringify(store.riskMatch, null, 2) }}</pre>
        </div>

        <!-- 必播项 / 必问 -->
        <div class="card">
          <h3 class="card-title">本节点必播项 + 必问</h3>
          <div class="phrase-list" v-if="store.script">
            <div v-for="(p, i) in currentNodeDef.mandatoryPhrases" :key="'m' + i" class="phrase-item">
              <el-icon style="color: var(--green);"><Check /></el-icon>
              {{ p }}
            </div>
            <div v-for="(q, i) in currentNodeDef.requiredQuestions" :key="'q' + i" class="phrase-item" style="border-left-color: var(--blue);">
              <el-icon style="color: var(--blue);"><QuestionFilled /></el-icon>
              <strong>问：</strong>{{ q }}
            </div>
            <div v-if="currentNodeDef.mandatoryPhrases.length === 0 && currentNodeDef.requiredQuestions.length === 0" style="text-align: center; color: var(--ink-3); padding: 16px;">
              本节点无强制必播项/必问
            </div>
          </div>
          <div v-else style="text-align: center; color: var(--ink-3); padding: 16px; font-size: 12px;">
            尚未加载话术
          </div>
        </div>

        <!-- 实时日志 -->
        <div class="card">
          <h3 class="card-title">
            <span>实时日志</span>
            <span class="actions">
              <el-tag size="small">{{ store.alerts.length }}</el-tag>
            </span>
          </h3>
          <div class="alert-list">
            <div v-if="store.alerts.length === 0" style="text-align: center; color: var(--ink-3); padding: 16px; font-size: 12px;">
              暂无日志
            </div>
            <div
              v-for="(a, i) in store.alerts.slice(0, 20)"
              :key="i"
              class="alert-item"
              :class="a.type"
            >
              <span class="time">{{ a.time }}</span>
              <span class="message">{{ a.message }}</span>
            </div>
          </div>
        </div>
      </el-col>
    </el-row>

    <!-- AI 终检 + 签字 -->
    <div v-if="store.isFinished && !store.qaResult" class="card" style="margin-top: 16px;">
      <h3 class="card-title">全部 8 节点已完成</h3>
      <p>可执行 AI 终检（会调用 LLM 网关 + 反深伪检测 + 100% AI 预筛）</p>
      <el-button type="success" :loading="store.isProcessing" @click="handleFinalize" size="large">
        <el-icon><DataAnalysis /></el-icon>
        执行 AI 终检
      </el-button>
    </div>

    <div v-if="store.qaResult" class="card" style="margin-top: 16px;">
      <h3 class="card-title">
        <span>AI 终检结果</span>
        <span class="state-badge" :style="{ color: store.qaResult.aiQaResult === 'PASS' ? 'var(--green)' : 'var(--accent-2)' }">
          {{ store.qaResult.aiQaResult }}
        </span>
      </h3>
      <el-row :gutter="16">
        <el-col :span="6">
          <div class="info-cell">
            <div class="label">AI 模型版本</div>
            <div class="value mono" style="font-size: 12px;">{{ store.qaResult.aiModelVersion }}</div>
          </div>
        </el-col>
        <el-col :span="6">
          <div class="info-cell">
            <div class="label">质检分数</div>
            <div class="value mono" style="font-size: 24px; font-weight: 700; color: var(--accent);">
              {{ store.qaResult.aiQaScore }}
            </div>
          </div>
        </el-col>
        <el-col :span="6">
          <div class="info-cell">
            <div class="label">QA ID</div>
            <div class="value mono" style="font-size: 11px;">{{ store.qaResult.qaId }}</div>
          </div>
        </el-col>
        <el-col :span="6">
          <div class="info-cell">
            <div class="label">质检时间</div>
            <div class="value">{{ store.qaResult.checkTime }}</div>
          </div>
        </el-col>
      </el-row>
      <div style="margin-top: 16px;">
        <el-button type="primary" @click="handleSign" size="large">
          <el-icon><EditPen /></el-icon>
          客户签字 → 归档
        </el-button>
      </div>
    </div>
  </div>
</template>

<style scoped>
.info-cell {
  background: var(--bg-2);
  padding: 10px 14px;
  border-radius: 6px;
}
.info-cell .label {
  font-size: 10px;
  color: var(--ink-3);
  letter-spacing: 0.5px;
  text-transform: uppercase;
  margin-bottom: 4px;
}
.info-cell .value {
  font-size: 13px;
  font-weight: 500;
  color: var(--ink);
}
</style>
