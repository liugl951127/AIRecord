<template>
  <div class="saga-list">
    <!-- 筛选器 -->
    <el-card shadow="hover" style="margin-bottom:16px">
      <el-form :inline="true" :model="filter" @submit.prevent>
        <el-form-item label="状态">
          <el-select v-model="filter.state" placeholder="全部" clearable style="width:160px">
            <el-option v-for="s in stateOptions" :key="s.value" :label="s.label" :value="s.value" />
          </el-select>
        </el-form-item>
        <el-form-item label="类型">
          <el-select v-model="filter.sagaType" placeholder="全部" clearable style="width:180px">
            <el-option v-for="t in sagaTypes" :key="t" :label="t" :value="t" />
          </el-select>
        </el-form-item>
        <el-form-item label="SessionID">
          <el-input v-model="filter.sessionId" placeholder="模糊搜索" clearable style="width:200px" />
        </el-form-item>
        <el-form-item label="起始时间">
          <el-date-picker
            v-model="filter.startTime"
            type="datetime"
            placeholder="选择起始时间"
            value-format="YYYY-MM-DDTHH:mm:ss"
            style="width:200px"
          />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" :icon="Search" @click="onSearch">查询</el-button>
          <el-button :icon="Refresh" @click="onReset">重置</el-button>
          <el-button :icon="Refresh" @click="loadData">刷新</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <!-- 列表 -->
    <el-card shadow="hover">
      <template #header>
        <div class="card-header">
          <span>
            <el-icon><List /></el-icon> Saga 列表
            <el-tag size="small" type="info" style="margin-left:8px">共 {{ total }} 条</el-tag>
          </span>
          <el-radio-group v-model="autoRefresh" size="small">
            <el-radio-button :value="0">不刷新</el-radio-button>
            <el-radio-button :value="10">10s</el-radio-button>
            <el-radio-button :value="30">30s</el-radio-button>
            <el-radio-button :value="60">60s</el-radio-button>
          </el-radio-group>
        </div>
      </template>

      <el-table :data="list" v-loading="loading" stripe max-height="700">
        <el-table-column type="index" label="#" width="50" />
        <el-table-column prop="sagaId" label="Saga ID" width="200" show-overflow-tooltip>
          <template #default="{ row }">
            <el-link type="primary" @click="goDetail(row.sagaId)">{{ row.sagaId }}</el-link>
          </template>
        </el-table-column>
        <el-table-column prop="sagaType" label="类型" width="140">
          <template #default="{ row }">
            <el-tag effect="plain" size="small">{{ row.sagaType }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="sessionId" label="Session" width="180" show-overflow-tooltip />
        <el-table-column prop="state" label="状态" width="110">
          <template #default="{ row }">
            <el-tag :type="stateTagType(row.state)" effect="dark" size="small">
              {{ stateTagText(row.state) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="currentStep" label="当前步骤" width="130" />
        <el-table-column prop="errorMessage" label="错误信息" show-overflow-tooltip min-width="200" />
        <el-table-column prop="startedAt" label="开始时间" width="170">
          <template #default="{ row }">{{ formatTime(row.startedAt) }}</template>
        </el-table-column>
        <el-table-column prop="completedAt" label="完成时间" width="170">
          <template #default="{ row }">{{ row.completedAt ? formatTime(row.completedAt) : '-' }}</template>
        </el-table-column>
        <el-table-column label="耗时" width="90">
          <template #default="{ row }">
            <span v-if="row.startedAt && row.completedAt">
              {{ calcDuration(row.startedAt, row.completedAt) }}
            </span>
            <span v-else>-</span>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="100" fixed="right">
          <template #default="{ row }">
            <el-button text type="primary" size="small" @click="goDetail(row.sagaId)">详情</el-button>
          </template>
        </el-table-column>
      </el-table>

      <el-pagination
        v-model:current-page="filter.page"
        v-model:page-size="filter.size"
        :total="total"
        :page-sizes="[20, 50, 100, 200]"
        layout="total, sizes, prev, pager, next, jumper"
        style="margin-top:16px;justify-content:flex-end"
        @current-change="loadData"
        @size-change="loadData"
      />
    </el-card>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted, onUnmounted, watch } from 'vue'
import { useRouter } from 'vue-router'
import dayjs from 'dayjs'
import { Search, Refresh, List } from '@element-plus/icons-vue'
import { getSagaList, getSagaTypes } from '@/api/saga'

const router = useRouter()
const loading = ref(false)
const list = ref([])
const total = ref(0)
const sagaTypes = ref([])
const autoRefresh = ref(0)
let refreshTimer = null

const filter = reactive({
  state: '',
  sagaType: '',
  sessionId: '',
  startTime: null,
  page: 1,
  size: 20
})

const stateOptions = [
  { value: 'STARTED', label: '已启动' },
  { value: 'STEP_EXECUTING', label: '执行中' },
  { value: 'COMPLETED', label: '已完成' },
  { value: 'FAILED', label: '失败' },
  { value: 'COMPENSATING', label: '补偿中' },
  { value: 'COMPENSATED', label: '已补偿' },
  { value: 'CANCELLED', label: '已取消' }
]

const stateTagType = (s) => ({
  COMPLETED: 'success', FAILED: 'danger', COMPENSATED: 'warning',
  COMPENSATING: 'warning', CANCELLED: 'info', STARTED: 'primary', STEP_EXECUTING: 'primary'
}[s] || '')

const stateTagText = (s) => ({
  COMPLETED: '已完成', FAILED: '失败', COMPENSATED: '已补偿',
  COMPENSATING: '补偿中', CANCELLED: '已取消', STARTED: '已启动', STEP_EXECUTING: '执行中'
}[s] || s)

const formatTime = (t) => t ? dayjs(t).format('YYYY-MM-DD HH:mm:ss') : '-'

const calcDuration = (start, end) => {
  const ms = dayjs(end).diff(dayjs(start))
  if (ms < 1000) return ms + 'ms'
  if (ms < 60000) return (ms / 1000).toFixed(1) + 's'
  return (ms / 60000).toFixed(1) + 'min'
}

const goDetail = (id) => router.push(`/saga/detail/${id}`)

const loadData = async () => {
  loading.value = true
  try {
    const params = {
      state: filter.state || undefined,
      sagaType: filter.sagaType || undefined,
      sessionId: filter.sessionId || undefined,
      startTime: filter.startTime || undefined,
      page: filter.page,
      size: filter.size
    }
    const res = await getSagaList(params)
    list.value = res.data.items || []
    total.value = res.data.total || 0
  } finally {
    loading.value = false
  }
}

const loadTypes = async () => {
  const res = await getSagaTypes()
  sagaTypes.value = res.data || []
}

const onSearch = () => {
  filter.page = 1
  loadData()
}

const onReset = () => {
  filter.state = ''
  filter.sagaType = ''
  filter.sessionId = ''
  filter.startTime = null
  filter.page = 1
  loadData()
}

watch(autoRefresh, (val) => {
  if (refreshTimer) {
    clearInterval(refreshTimer)
    refreshTimer = null
  }
  if (val > 0) {
    refreshTimer = setInterval(loadData, val * 1000)
  }
})

onMounted(() => {
  loadTypes()
  loadData()
})

onUnmounted(() => {
  if (refreshTimer) clearInterval(refreshTimer)
})
</script>

<style lang="scss" scoped>
.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}
</style>
