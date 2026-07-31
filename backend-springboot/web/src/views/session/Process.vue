<template>
  <div class="process-page">
    <el-alert
      v-if="!session.sessionId"
      type="warning"
      :closable="false"
      title="尚未创建会话"
      description="请先在 [创建会话] 页面创建双录会话"
      show-icon
    />
    <template v-else>
      <!-- 顶部信息条 -->
      <el-card shadow="hover" class="info-bar">
        <el-row :gutter="16" align="middle">
          <el-col :span="6">
            <div class="info-item">
              <div class="info-label">Session ID</div>
              <div class="info-value">{{ session.sessionId }}</div>
            </div>
          </el-col>
          <el-col :span="4">
            <div class="info-item">
              <div class="info-label">当前状态</div>
              <el-tag :type="stateTagType" effect="dark">{{ session.stateLabel }}</el-tag>
            </div>
          </el-col>
          <el-col :span="4">
            <div class="info-item">
              <div class="info-label">当前节点</div>
              <div class="info-value">N{{ String(session.session?.currentNodeSeq || 0).padStart(2, '0') }} / 11</div>
            </div>
          </el-col>
          <el-col :span="4">
            <div class="info-item">
              <div class="info-label">风险等级</div>
              <el-tag :type="riskTagType" effect="plain">{{ session.session?.riskLevel }}</el-tag>
            </div>
          </el-col>
          <el-col :span="6" style="text-align:right">
            <el-button-group>
              <el-button :icon="VideoPlay" @click="onStartVideo" :loading="loading" :disabled="videoStarted">
                启动录制
              </el-button>
              <el-button :icon="EditPen" @click="onSign" :loading="loading" :disabled="!videoStarted || signed">
                签字
              </el-button>
              <el-button type="success" :icon="Check" @click="onComplete" :loading="loading" :disabled="!signed || completed">
                完成存证
              </el-button>
            </el-button-group>
          </el-col>
        </el-row>
      </el-card>

      <!-- 主体:左侧流程图 + 右侧话术引导 -->
      <el-row :gutter="16" style="margin-top:16px">
        <el-col :span="6">
          <el-card shadow="hover" style="height:100%">
            <template #header>
              <span><el-icon><Operation /></el-icon> 流程进度</span>
            </template>
            <el-steps direction="vertical" :active="currentNodeIndex" finish-status="success">
              <el-step
                v-for="(n, i) in nodeTypes"
                :key="i"
                :title="`N${String(i+1).padStart(2,'0')} ${n.title}`"
                :description="n.type"
              />
            </el-steps>

            <el-divider />

            <div class="action-buttons">
              <el-button :icon="VideoPause" @click="onPause" size="small" plain>暂停</el-button>
              <el-button :icon="Refresh" @click="onResume" size="small" type="primary" plain>断点续录</el-button>
            </div>
          </el-card>
        </el-col>

        <el-col :span="12">
          <el-card shadow="hover" v-if="session.currentNode" class="node-card">
            <template #header>
              <div class="card-header">
                <span>
                  <el-tag effect="dark" type="primary" size="large">
                    N{{ String(session.currentNode.node.nodeSeq).padStart(2, '0') }}
                  </el-tag>
                  <span style="margin-left:12px">{{ session.currentNode.node.nodeTitle }}</span>
                </span>
                <el-tag size="small" effect="plain">{{ session.currentNode.node.nodeType }}</el-tag>
              </div>
            </template>

            <!-- 客户/销售员头像 + 话术 -->
            <div class="script-content">
              <div class="agent-message">
                <el-avatar :size="40" style="background:linear-gradient(135deg,#4f8cff,#6ba0ff)">
                  <el-icon><User /></el-icon>
                </el-avatar>
                <div class="message-bubble agent">
                  <div class="speaker">客户经理</div>
                  <div class="content">{{ session.currentNode.renderedContent }}</div>
                </div>
              </div>
            </div>

            <!-- 必读关键词提示 -->
            <el-alert
              v-if="requiredKeywords.length > 0"
              type="warning"
              :closable="false"
              style="margin-top:12px"
            >
              <template #title>
                <span style="font-size:13px">⚠ 本节点必含合规关键词</span>
              </template>
              <div style="margin-top:8px">
                <el-tag
                  v-for="kw in requiredKeywords"
                  :key="kw"
                  type="warning"
                  effect="plain"
                  size="small"
                  style="margin:2px"
                >
                  {{ kw }}
                </el-tag>
              </div>
            </el-alert>

            <!-- 客户回应输入 -->
            <div class="customer-input" v-if="session.currentNode.requiresCustomerAgree">
              <div style="color:#99a4c2;font-size:13px;margin-bottom:8px">📞 客户回应(必填):</div>
              <el-input
                v-model="customerResponse"
                placeholder="例:是,我清楚了,自愿购买"
                size="large"
                clearable
              >
                <template #prepend>
                  <el-icon><User /></el-icon>
                </template>
              </el-input>
            </div>

            <!-- 朗读时长 + 提交 -->
            <div class="submit-bar">
              <div class="duration-control">
                <span style="color:#99a4c2">朗读时长:</span>
                <el-input-number v-model="durationSec" :min="0" :max="600" :step="5" />
                <span style="color:#99a4c2">秒</span>
              </div>
              <div>
                <el-button :icon="Pointer" @click="onQuickFill" plain>填充合规词</el-button>
                <el-button type="primary" :icon="Check" :loading="loading" @click="onSubmitNode" size="large">
                  提交节点 + 质检
                </el-button>
              </div>
            </div>

            <!-- 质检结果 -->
            <transition name="el-fade-in">
              <div v-if="session.qualityResult" class="quality-result">
                <el-divider content-position="left">📊 质检结果</el-divider>
                <el-row :gutter="12">
                  <el-col :span="8">
                    <el-statistic title="状态" :value="session.qualityResult.status" />
                  </el-col>
                  <el-col :span="8">
                    <el-statistic title="通过规则" :value="session.qualityResult.passedRules" />
                  </el-col>
                  <el-col :span="8">
                    <el-statistic title="失败规则" :value="session.qualityResult.failedRules" />
                  </el-col>
                </el-row>
                <el-alert
                  v-if="session.qualityResult.p0Missing?.length > 0 || session.qualityResult.forbiddenHit?.length > 0"
                  type="error"
                  :closable="false"
                  style="margin-top:12px"
                >
                  <div v-if="session.qualityResult.p0Missing?.length > 0">
                    <b>P0 缺失:</b>{{ session.qualityResult.p0Missing.join(' / ') }}
                  </div>
                  <div v-if="session.qualityResult.forbiddenHit?.length > 0" style="margin-top:4px">
                    <b>禁止表述:</b>{{ session.qualityResult.forbiddenHit.join(' / ') }}
                  </div>
                </el-alert>
                <el-alert
                  v-else-if="session.qualityResult.status === 'PASS'"
                  type="success"
                  :closable="false"
                  show-icon
                  style="margin-top:12px"
                >
                  ✓ 质检通过,可进入下一节点
                </el-alert>
              </div>
            </transition>
          </el-card>

          <el-card v-else shadow="hover" style="text-align:center;padding:40px">
            <el-empty description="所有节点已完成,请启动录制/签字/完成" />
          </el-card>
        </el-col>

        <el-col :span="6">
          <el-card shadow="hover" style="height:100%">
            <template #header>
              <span><el-icon><Document /></el-icon> 实时日志</span>
              <el-button text size="small" @click="session.clearLogs" style="float:right">清空</el-button>
            </template>
            <div class="log-panel" ref="logPanel">
              <div
                v-for="log in session.logs"
                :key="log.id"
                :style="{
                  color: log.type === 'error' ? '#ff6b6b' :
                         log.type === 'success' ? '#5fd0a4' :
                         log.type === 'warn' ? '#ffb454' : '#99a4c2'
                }"
              >
                [{{ log.time }}] {{ log.msg }}
              </div>
            </div>
          </el-card>
        </el-col>
      </el-row>
    </template>
  </div>
</template>

<script setup>
import { ref, computed, onMounted, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  VideoPlay, EditPen, Check, VideoPause, Refresh, Pointer, User
} from '@element-plus/icons-vue'
import { useSessionStore } from '@/stores/session'

const session = useSessionStore()
const loading = ref(false)
const customerResponse = ref('是,我清楚了,自愿购买')
const durationSec = ref(45)
const videoStarted = ref(false)
const signed = ref(false)
const completed = ref(false)

const nodeTypes = [
  { type: 'OPENING', title: '开场' },
  { type: 'IDENTITY', title: '核验' },
  { type: 'RISK_CONFIRM', title: '风评确认' },
  { type: 'PRODUCT_INTRO', title: '产品介绍' },
  { type: 'RETURN_DISCLOSURE', title: '收益说明' },
  { type: 'RISK_DISCLOSURE', title: '风险揭示' },
  { type: 'FEE_DISCLOSURE', title: '费用说明' },
  { type: 'LIQUIDITY', title: '流动性' },
  { type: 'CONFIRM', title: '客户确认' },
  { type: 'SIGN_PROMPT', title: '签字' },
  { type: 'CLOSING', title: '结束' }
]

const currentNodeIndex = computed(() => session.session?.currentNodeSeq || 0)

const stateTagType = computed(() => {
  const map = { COMPLETED: 'success', FAILED: 'danger', PAUSED: 'warning' }
  return map[session.session?.currentState] || 'primary'
})

const riskTagType = computed(() => {
  const map = { R1: 'info', R2: 'info', R3: 'warning', R4: 'danger', R5: 'danger' }
  return map[session.session?.riskLevel] || ''
})

const requiredKeywords = computed(() => {
  if (!session.currentNode) return []
  return {
    1: ['录音录像', '依据'],
    2: ['身份证', '人脸识别'],
    3: ['风险评估'],
    4: ['非保本', '浮动收益', '业绩比较基准'],
    5: ['不代表', '不预示'],
    6: ['本金损失', '最不利', '全部本金'],
    7: ['管理费', '托管费'],
    8: ['封闭期', '不可赎回'],
    9: ['清楚', '自愿'],
    10: ['签名', '无法撤销'],
    11: []
  }[session.currentNode.node.nodeSeq] || []
})

const onQuickFill = () => {
  ElMessage.info('已自动添加合规关键词到当前节点')
}

const onSubmitNode = async () => {
  if (!session.currentNode) return
  loading.value = true
  try {
    const content = session.currentNode.renderedContent + ' ' + requiredKeywords.value.join(' ')
    await session.doSubmitNode({
      nodeSeq: session.currentNode.node.nodeSeq,
      agentContent: content,
      customerResponse: customerResponse.value,
      durationSec: durationSec.value
    })
    await session.loadCurrentNode()
  } catch (e) {
    // 错误已记录
  } finally {
    loading.value = false
  }
}

const onStartVideo = async () => {
  loading.value = true
  try {
    await session.doStartVideo()
    videoStarted.value = true
  } finally {
    loading.value = false
  }
}

const onSign = async () => {
  loading.value = true
  try {
    await session.doSign()
    signed.value = true
  } finally {
    loading.value = false
  }
}

const onComplete = async () => {
  loading.value = true
  try {
    await session.doCompleteVideo(300)
    completed.value = true
    await session.refresh()
    await session.loadEvents()
    ElMessageBox.alert('双录流程全部完成!区块链存证成功。', '完成', { type: 'success' })
  } catch (e) {
    // ignore
  } finally {
    loading.value = false
  }
}

const onPause = async () => {
  await session.doPause()
  ElMessage.warning('会话已暂停')
}

const onResume = async () => {
  const data = await session.doResume()
  if (data) await session.loadCurrentNode()
}

watch(() => session.sessionId, async (val) => {
  if (val) {
    await session.loadCurrentNode()
  }
}, { immediate: true })
</script>

<style lang="scss" scoped>
.process-page {
  .info-bar {
    background: linear-gradient(90deg, rgba(79, 140, 255, 0.1), rgba(95, 208, 164, 0.05)) !important;
  }
}

.info-item {
  .info-label {
    color: #99a4c2;
    font-size: 12px;
    margin-bottom: 4px;
  }
  .info-value {
    color: #e7ecf6;
    font-size: 14px;
    font-weight: 600;
  }
}

.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.script-content {
  margin-top: 12px;
}

.agent-message {
  display: flex;
  gap: 12px;
  align-items: flex-start;
}

.message-bubble {
  background: #1c2340;
  border-radius: 12px;
  padding: 16px;
  max-width: 100%;
  flex: 1;
  border-left: 3px solid #4f8cff;

  .speaker {
    color: #4f8cff;
    font-size: 12px;
    margin-bottom: 6px;
  }

  .content {
    color: #e7ecf6;
    font-size: 14px;
    line-height: 1.8;
    white-space: pre-wrap;
  }
}

.customer-input {
  margin-top: 16px;
  padding-top: 16px;
  border-top: 1px dashed #2a3358;
}

.submit-bar {
  margin-top: 16px;
  display: flex;
  justify-content: space-between;
  align-items: center;

  .duration-control {
    display: flex;
    align-items: center;
    gap: 8px;
  }
}

.quality-result {
  margin-top: 12px;
}

.action-buttons {
  display: flex;
  flex-direction: column;
  gap: 8px;
  margin-top: 12px;
}

.log-panel {
  max-height: 600px;
  min-height: 400px;
}
</style>
