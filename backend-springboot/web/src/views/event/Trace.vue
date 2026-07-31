<template>
  <div class="event-page">
    <el-card shadow="hover">
      <template #header>
        <div class="card-header">
          <span><el-icon><Connection /></el-icon> 事件流(全流程审计)</span>
          <div>
            <el-input
              v-model="sessionIdInput"
              placeholder="输入 SessionID 查询"
              size="small"
              style="width:280px;margin-right:8px"
              clearable
            />
            <el-button type="primary" :icon="Search" size="small" @click="onLoad">查询</el-button>
            <el-button :icon="Refresh" size="small" @click="onLoad" v-if="session.sessionId">使用当前会话</el-button>
          </div>
        </div>
      </template>

      <el-alert v-if="!events.length" type="info" :closable="false" show-icon
        title="提示" description="输入 SessionID 或使用当前会话,查看完整事件流" />

      <div v-else>
        <el-row :gutter="16" style="margin-bottom:16px">
          <el-col :span="6">
            <el-statistic title="总事件数" :value="events.length" />
          </el-col>
          <el-col :span="6">
            <el-statistic title="会话创建" :value="countByType('SessionCreated')" />
          </el-col>
          <el-col :span="6">
            <el-statistic title="节点完成" :value="countByType('ScriptNodeCompleted')" :value-style="{ color: '#5fd0a4' }" />
          </el-col>
          <el-col :span="6">
            <el-statistic title="存证完成" :value="countByType('ChainCommitted')" :value-style="{ color: '#4f8cff' }" />
          </el-col>
        </el-row>

        <el-timeline>
          <el-timeline-item
            v-for="evt in events"
            :key="evt.id"
            :timestamp="formatTime(evt.occurredAt)"
            :type="eventTypeColor(evt.eventType)"
            placement="top"
          >
            <el-card shadow="hover" class="event-card">
              <div class="event-header">
                <span>
                  <el-tag effect="dark" :type="eventTypeColor(evt.eventType)" size="small">
                    #{{ evt.sequenceNo }}
                  </el-tag>
                  <b style="margin-left:8px">{{ evt.eventType }}</b>
                </span>
                <el-tag effect="plain" size="small">{{ evt.aggregateType }}</el-tag>
              </div>
              <div class="event-detail">
                <p><b>事件ID:</b> <code>{{ evt.eventId }}</code></p>
                <p><b>聚合根:</b> {{ evt.aggregateType }} / {{ evt.aggregateId }}</p>
                <p v-if="evt.payload"><b>Payload:</b></p>
                <pre class="event-payload">{{ formatPayload(evt.payload) }}</pre>
              </div>
            </el-card>
          </el-timeline-item>
        </el-timeline>
      </div>
    </el-card>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import dayjs from 'dayjs'
import { Search, Refresh } from '@element-plus/icons-vue'
import { useSessionStore } from '@/stores/session'
import { getEvents } from '@/api/doubleRecording'

const session = useSessionStore()
const events = ref([])
const sessionIdInput = ref('')
const loading = ref(false)

const formatTime = (t) => t ? dayjs(t).format('YYYY-MM-DD HH:mm:ss.SSS') : '-'

const eventTypeColor = (t) => {
  if (t === 'SessionCreated' || t === 'SessionCompleted') return 'success'
  if (t === 'ChainCommitted' || t === 'SagaCompleted') return 'primary'
  if (t === 'QualityCheckFailed' || t === 'CompensationFailed') return 'danger'
  if (t === 'ScriptNodeCompleted') return 'info'
  return 'warning'
}

const countByType = (type) => events.value.filter(e => e.eventType === type).length

const formatPayload = (p) => {
  try { return JSON.stringify(JSON.parse(p), null, 2) } catch { return p }
}

const onLoad = async () => {
  const sid = sessionIdInput.value || session.sessionId
  if (!sid) return
  loading.value = true
  try {
    const res = await getEvents(sid)
    events.value = res.data || []
  } catch (e) {
    events.value = []
  } finally {
    loading.value = false
  }
}

onMounted(() => {
  if (session.sessionId) {
    sessionIdInput.value = session.sessionId
    onLoad()
  }
})
</script>

<style lang="scss" scoped>
.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.event-card {
  background: #1c2340 !important;
  border: 1px solid #2a3358 !important;
  margin-bottom: 8px;
}

.event-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 8px;
}

.event-detail {
  font-size: 13px;
  color: #99a4c2;

  p { margin: 4px 0; }
  b { color: #e7ecf6; }
  code {
    background: #0a0a0a;
    color: #5fd0a4;
    padding: 2px 6px;
    border-radius: 3px;
    font-size: 11px;
  }
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
</style>
