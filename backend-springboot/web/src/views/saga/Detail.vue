<template>
  <div class="saga-detail" v-loading="loading">
    <el-page-header :icon="ArrowLeft" content="返回列表" @back="goBack" style="margin-bottom:16px">
      <template #content>
        <span class="page-title">Saga 详情 - {{ sagaId }}</span>
      </template>
    </el-page-header>

    <template v-if="detail">
      <!-- 基础信息 -->
      <el-row :gutter="16">
        <el-col :span="16">
          <el-card shadow="hover">
            <template #header>
              <span><el-icon><InfoFilled /></el-icon> 基础信息</span>
              <el-tag :type="stateTagType(detail.saga.state)" effect="dark" size="large" style="float:right">
                {{ stateTagText(detail.saga.state) }}
              </el-tag>
            </template>

            <el-descriptions :column="2" border>
              <el-descriptions-item label="Saga ID">{{ detail.saga.sagaId }}</el-descriptions-item>
              <el-descriptions-item label="Session ID">
                <el-link type="primary" @click="goSession(detail.saga.sessionId)">{{ detail.saga.sessionId }}</el-link>
              </el-descriptions-item>
              <el-descriptions-item label="类型">{{ detail.saga.sagaType }}</el-descriptions-item>
              <el-descriptions-item label="当前步骤">{{ detail.saga.currentStep || '-' }}</el-descriptions-item>
              <el-descriptions-item label="开始时间">{{ formatTime(detail.saga.startedAt) }}</el-descriptions-item>
              <el-descriptions-item label="完成时间">{{ formatTime(detail.saga.completedAt) }}</el-descriptions-item>
              <el-descriptions-item label="耗时" :span="2">
                <span v-if="detail.durationMs" :class="durationClass(detail.durationMs)">
                  {{ formatDuration(detail.durationMs) }}
                </span>
                <span v-else>-</span>
              </el-descriptions-item>
              <el-descriptions-item v-if="detail.saga.errorMessage" label="错误信息/备注" :span="2">
                <el-alert :type="detail.saga.state === 'COMPLETED' ? 'success' : 'error'" :closable="false" show-icon>
                  <pre style="margin:0;white-space:pre-wrap;font-family:inherit">{{ detail.saga.errorMessage }}</pre>
                </el-alert>
              </el-descriptions-item>
            </el-descriptions>

            <!-- 上下文数据 -->
            <template v-if="detail.context && Object.keys(detail.context).length > 0">
              <el-divider content-position="left">📦 上下文数据</el-divider>
              <pre class="json-block">{{ JSON.stringify(detail.context, null, 2) }}</pre>
            </template>
          </el-card>
        </el-col>

        <el-col :span="8">
          <el-card shadow="hover">
            <template #header>
              <span><el-icon><Setting /></el-icon> 操作</span>
            </template>

            <div class="action-buttons">
              <el-button type="primary" :icon="Refresh" :disabled="!canRetry" 
                        @click="onRetry" style="width:100%;margin-bottom:12px" :loading="actionLoading">
                手动重试
              </el-button>
              <el-button type="warning" :icon="VideoPause" :disabled="!canCancel"
                        @click="onCancel" style="width:100%;margin-bottom:12px" :loading="actionLoading">
                取消 Saga
              </el-button>
              <el-button type="danger" :icon="WarningFilled" :disabled="!canForceComplete"
                        @click="onForceComplete" style="width:100%;margin-bottom:12px" :loading="actionLoading">
                强制完成(慎用)
              </el-button>
            </div>

            <el-alert
              v-if="!canRetry && !canCancel && !canForceComplete"
              type="info"
              :closable="false"
              title="无可用操作"
              description="该 Saga 已是终态,无法进行额外操作"
              show-icon
            />

            <el-divider />

            <h4>状态说明</h4>
            <ul class="legend-list">
              <li><el-tag effect="dark" type="success" size="small">COMPLETED</el-tag> 全部步骤执行成功</li>
              <li><el-tag effect="dark" type="danger" size="small">FAILED</el-tag> 步骤执行失败,可重试</li>
              <li><el-tag effect="dark" type="warning" size="small">COMPENSATED</el-tag> 已补偿完成</li>
              <li><el-tag effect="dark" type="warning" size="small">COMPENSATING</el-tag> 正在补偿</li>
              <li><el-tag effect="dark" type="info" size="small">CANCELLED</el-tag> 人工取消</li>
            </ul>
          </el-card>
        </el-col>
      </el-row>

      <!-- 时间线 -->
      <el-card shadow="hover" style="margin-top:16px">
        <template #header>
          <span><el-icon><Timer /></el-icon> 事件时间线</span>
          <el-tag size="small" type="info" style="float:right">共 {{ timeline.length }} 个事件</el-tag>
        </template>

        <el-timeline v-if="timeline.length > 0">
          <el-timeline-item
            v-for="evt in timeline"
            :key="evt.eventId"
            :timestamp="formatTime(evt.occurredAt)"
            :type="eventColor(evt.eventType)"
            placement="top"
          >
            <el-card shadow="hover" class="event-card">
              <div class="event-header">
                <span>
                  <el-tag effect="dark" :type="eventColor(evt.eventType)" size="small">
                    #{{ evt.sequenceNo }}
                  </el-tag>
                  <b style="margin-left:8px">{{ evt.eventType }}</b>
                </span>
                <el-tag effect="plain" size="small">{{ evt.aggregateId }}</el-tag>
              </div>
              <pre v-if="evt.payload" class="event-payload">{{ formatPayload(evt.payload) }}</pre>
            </el-card>
          </el-timeline-item>
        </el-timeline>
        <el-empty v-else description="暂无事件" />
      </el-card>
    </template>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import dayjs from 'dayjs'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  ArrowLeft, InfoFilled, Setting, Refresh, VideoPause, WarningFilled, Timer
} from '@element-plus/icons-vue'
import {
  getSagaDetail, getSagaTimeline, retrySaga, cancelSaga, forceCompleteSaga
} from '@/api/saga'

const route = useRoute()
const router = useRouter()
const sagaId = computed(() => route.params.sagaId)
const loading = ref(false)
const actionLoading = ref(false)
const detail = ref(null)
const timeline = ref([])

const formatTime = (t) => t ? dayjs(t).format('YYYY-MM-DD HH:mm:ss.SSS') : '-'

const formatDuration = (ms) => {
  if (ms < 1000) return ms + 'ms'
  if (ms < 60000) return (ms / 1000).toFixed(2) + 's'
  return (ms / 60000).toFixed(2) + 'min'
}

const durationClass = (ms) => {
  if (ms > 30000) return 'duration-slow'
  if (ms > 10000) return 'duration-medium'
  return 'duration-fast'
}

const stateTagType = (s) => ({
  COMPLETED: 'success', FAILED: 'danger', COMPENSATED: 'warning',
  COMPENSATING: 'warning', CANCELLED: 'info', STARTED: 'primary', STEP_EXECUTING: 'primary'
}[s] || '')

const stateTagText = (s) => ({
  COMPLETED: '已完成', FAILED: '失败', COMPENSATED: '已补偿',
  COMPENSATING: '补偿中', CANCELLED: '已取消', STARTED: '已启动', STEP_EXECUTING: '执行中'
}[s] || s)

const eventColor = (type) => {
  if (type === 'SagaCompleted') return 'success'
  if (type === 'SagaRetryRequested' || type === 'SagaForceCompleted') return 'primary'
  if (type === 'SagaCancelled') return 'info'
  if (type === 'CompensationFailed') return 'danger'
  return 'warning'
}

const formatPayload = (p) => {
  if (typeof p === 'string') {
    try { return JSON.stringify(JSON.parse(p), null, 2) } catch { return p }
  }
  return JSON.stringify(p, null, 2)
}

const canRetry = computed(() => ['FAILED', 'COMPENSATED'].includes(detail.value?.saga?.state))
const canCancel = computed(() => !['COMPLETED', 'COMPENSATED', 'CANCELLED'].includes(detail.value?.saga?.state))
const canForceComplete = computed(() => !['COMPLETED'].includes(detail.value?.saga?.state))

const goBack = () => router.push('/saga/list')
const goSession = (sid) => router.push(`/session/process?sessionId=${sid}`)

const loadData = async () => {
  loading.value = true
  try {
    const [d, t] = await Promise.all([
      getSagaDetail(sagaId.value),
      getSagaTimeline(sagaId.value)
    ])
    detail.value = d.data
    timeline.value = t.data || []
  } finally {
    loading.value = false
  }
}

const onRetry = async () => {
  try {
    const { value: operator } = await ElMessageBox.prompt('请输入操作员姓名', '手动重试', {
      confirmButtonText: '确认重试',
      cancelButtonText: '取消',
      inputPattern: /.+/,
      inputErrorMessage: '操作员不能为空'
    })
    actionLoading.value = true
    await retrySaga(sagaId.value, operator)
    ElMessage.success('已触发重试')
    await loadData()
  } catch (e) {
    if (e !== 'cancel') ElMessage.error('重试失败: ' + (e?.message || '未知错误'))
  } finally {
    actionLoading.value = false
  }
}

const onCancel = async () => {
  try {
    const { value: reason } = await ElMessageBox.prompt('请输入取消原因', '取消 Saga', {
      confirmButtonText: '确认取消',
      cancelButtonText: '不取消',
      inputType: 'textarea',
      inputPattern: /.+/,
      inputErrorMessage: '原因不能为空'
    })
    try {
      const { value: operator } = await ElMessageBox.prompt('请输入操作员姓名', '操作员', {
        inputPattern: /.+/,
        inputErrorMessage: '操作员不能为空'
      })
      actionLoading.value = true
      await cancelSaga(sagaId.value, reason, operator)
      ElMessage.warning('Saga 已取消')
      await loadData()
    } catch (e) {
      if (e !== 'cancel') throw e
    }
  } catch (e) {
    if (e !== 'cancel') ElMessage.error('取消失败: ' + (e?.message || '未知错误'))
  } finally {
    actionLoading.value = false
  }
}

const onForceComplete = async () => {
  try {
    await ElMessageBox.confirm(
      '强制完成会导致数据不一致,务必确认业务数据已正确处理。是否继续?',
      '⚠ 危险操作',
      { confirmButtonText: '我已确认,强制完成', cancelButtonText: '取消', type: 'error' }
    )
    const { value: reason } = await ElMessageBox.prompt('请输入强制完成原因', '强制完成', {
      inputType: 'textarea',
      inputPattern: /.+/,
      inputErrorMessage: '原因不能为空'
    })
    const { value: operator } = await ElMessageBox.prompt('请输入操作员姓名', '操作员', {
      inputPattern: /.+/,
      inputErrorMessage: '操作员不能为空'
    })
    actionLoading.value = true
    await forceCompleteSaga(sagaId.value, reason, operator)
    ElMessage.success('已强制完成')
    await loadData()
  } catch (e) {
    if (e !== 'cancel') ElMessage.error('操作失败: ' + (e?.message || '未知错误'))
  } finally {
    actionLoading.value = false
  }
}

onMounted(loadData)
</script>

<style lang="scss" scoped>
.page-title {
  font-size: 18px;
  font-weight: 600;
  color: #e7ecf6;
}

.action-buttons {
  display: flex;
  flex-direction: column;
}

.legend-list {
  list-style: none;
  padding: 0;
  margin: 0;
  font-size: 13px;
  color: #99a4c2;

  li {
    display: flex;
    align-items: center;
    gap: 8px;
    padding: 4px 0;
  }
}

.event-card {
  background: #1c2340 !important;
  border: 1px solid #2a3358 !important;
}

.event-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 6px;
}

.event-payload {
  background: #0a0a0a;
  color: #5fd0a4;
  padding: 8px;
  border-radius: 4px;
  font-size: 11px;
  max-height: 200px;
  overflow: auto;
  margin: 0;
}

.json-block {
  background: #0a0a0a;
  color: #5fd0a4;
  padding: 12px;
  border-radius: 4px;
  font-size: 12px;
  max-height: 300px;
  overflow: auto;
  margin: 0;
}

.duration-fast { color: #5fd0a4; font-weight: 600; }
.duration-medium { color: #ffb454; font-weight: 600; }
.duration-slow { color: #ff6b6b; font-weight: 600; }
</style>
