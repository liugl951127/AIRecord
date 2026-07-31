<template>
  <div class="saga-dashboard">
    <!-- 顶部统计 -->
    <el-row :gutter="16">
      <el-col :span="4">
        <el-card class="stat-card" shadow="hover">
          <el-statistic title="总 Saga 数" :value="stats.total || 0" />
        </el-card>
      </el-col>
      <el-col :span="4">
        <el-card class="stat-card" shadow="hover">
          <el-statistic title="已完成" :value="stats.completed || 0" :value-style="{ color: '#5fd0a4' }">
            <template #suffix>
              <span style="font-size:14px">次</span>
            </template>
          </el-statistic>
        </el-card>
      </el-col>
      <el-col :span="4">
        <el-card class="stat-card" shadow="hover">
          <el-statistic title="失败" :value="stats.failed || 0" :value-style="{ color: '#ff6b6b' }" />
        </el-card>
      </el-col>
      <el-col :span="4">
        <el-card class="stat-card" shadow="hover">
          <el-statistic title="已补偿" :value="stats.compensated || 0" :value-style="{ color: '#ffb454' }" />
        </el-card>
      </el-col>
      <el-col :span="4">
        <el-card class="stat-card" shadow="hover">
          <el-statistic title="成功率" :value="stats.successRate || '0'" suffix="%">
            <template #suffix>
              <span style="font-size:14px">%</span>
            </template>
          </el-statistic>
        </el-card>
      </el-col>
      <el-col :span="4">
        <el-card class="stat-card shadow-hover pending-card" :class="{ 'has-pending': stats.pendingManual > 0 }">
          <el-statistic title="待人工处理" :value="stats.pendingManual || 0" 
                        :value-style="{ color: stats.pendingManual > 0 ? '#ff6b6b' : '#99a4c2' }" />
        </el-card>
      </el-col>
    </el-row>

    <!-- 趋势 + 分布 -->
    <el-row :gutter="16" style="margin-top:16px">
      <el-col :span="16">
        <el-card shadow="hover">
          <template #header>
            <span><el-icon><TrendCharts /></el-icon> 24 小时趋势</span>
            <el-tag size="small" type="info" style="float:right">每小时统计</el-tag>
          </template>
          <div ref="trendChart" style="height:320px"></div>
        </el-card>
      </el-col>
      <el-col :span="8">
        <el-card shadow="hover">
          <template #header>
            <span><el-icon><PieChart /></el-icon> 24h 状态分布</span>
          </template>
          <div ref="stateChart" style="height:320px"></div>
        </el-card>
      </el-col>
    </el-row>

    <!-- 类型分布 -->
    <el-row :gutter="16" style="margin-top:16px">
      <el-col :span="12">
        <el-card shadow="hover">
          <template #header>
            <span><el-icon><DataAnalysis /></el-icon> 7 天类型分布</span>
          </template>
          <div ref="typeChart" style="height:300px"></div>
        </el-card>
      </el-col>
      <el-col :span="12">
        <el-card shadow="hover">
          <template #header>
            <span><el-icon><WarningFilled /></el-icon> 待人工处理({{ stats.pendingManual || 0 }})</span>
            <el-button text size="small" @click="goList" style="float:right">查看全部 →</el-button>
          </template>
          <el-table :data="pendingList" max-height="300" empty-text="暂无待处理项">
            <el-table-column prop="sagaId" label="SagaID" width="180" show-overflow-tooltip />
            <el-table-column prop="sagaType" label="类型" width="120" />
            <el-table-column prop="currentStep" label="步骤" width="100" />
            <el-table-column prop="errorMessage" label="错误" show-overflow-tooltip />
            <el-table-column label="操作" width="80">
              <template #default="{ row }">
                <el-button text type="primary" size="small" @click="goDetail(row.sagaId)">详情</el-button>
              </template>
            </el-table-column>
          </el-table>
        </el-card>
      </el-col>
    </el-row>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted, onUnmounted, nextTick } from 'vue'
import { useRouter } from 'vue-router'
import * as echarts from 'echarts'
import { TrendCharts, PieChart, DataAnalysis, WarningFilled } from '@element-plus/icons-vue'
import { getSagaStatistics, getPendingManual } from '@/api/saga'

const router = useRouter()
const stats = reactive({})
const pendingList = ref([])

const trendChart = ref(null)
const stateChart = ref(null)
const typeChart = ref(null)
let trendInstance = null
let stateInstance = null
let typeInstance = null

const goList = () => router.push('/saga/list')
const goDetail = (id) => router.push(`/saga/detail/${id}`)

const loadData = async () => {
  const [statsRes, pendingRes] = await Promise.all([
    getSagaStatistics(),
    getPendingManual()
  ])
  Object.assign(stats, statsRes.data)
  pendingList.value = pendingRes.data || []
  await nextTick()
  renderCharts()
}

const renderCharts = () => {
  // 24h 趋势
  if (trendChart.value) {
    trendInstance = trendInstance || echarts.init(trendChart.value)
    const hours = (stats.hourlyTrend24h || []).map(h => h.hour)
    const counts = (stats.hourlyTrend24h || []).map(h => h.count)
    const success = (stats.hourlyTrend24h || []).map(h => h.success)
    trendInstance.setOption({
      tooltip: { trigger: 'axis' },
      legend: { data: ['总执行数', '成功数'], textStyle: { color: '#99a4c2' } },
      grid: { left: '3%', right: '4%', bottom: '3%', containLabel: true },
      xAxis: {
        type: 'category',
        data: hours,
        axisLabel: { color: '#99a4c2' },
        axisLine: { lineStyle: { color: '#2a3358' } }
      },
      yAxis: { type: 'value', axisLabel: { color: '#99a4c2' }, splitLine: { lineStyle: { color: '#2a3358' } } },
      series: [
        {
          name: '总执行数', type: 'line', smooth: true, data: counts,
          itemStyle: { color: '#4f8cff' },
          areaStyle: { color: 'rgba(79,140,255,0.2)' }
        },
        {
          name: '成功数', type: 'line', smooth: true, data: success,
          itemStyle: { color: '#5fd0a4' },
          areaStyle: { color: 'rgba(95,208,164,0.2)' }
        }
      ]
    })
  }

  // 状态分布
  if (stateChart.value) {
    stateInstance = stateInstance || echarts.init(stateChart.value)
    const dist = stats.stateDistribution24h || []
    stateInstance.setOption({
      tooltip: { trigger: 'item' },
      legend: { bottom: 0, textStyle: { color: '#99a4c2' } },
      series: [{
        type: 'pie',
        radius: ['40%', '70%'],
        data: dist.map(d => ({ name: d.state, value: d.count })),
        label: { color: '#e7ecf6' },
        color: ['#5fd0a4', '#ff6b6b', '#ffb454', '#4f8cff', '#99a4c2', '#9b59b6']
      }]
    })
  }

  // 类型分布
  if (typeChart.value) {
    typeInstance = typeInstance || echarts.init(typeChart.value)
    const dist = stats.typeDistribution7d || []
    typeInstance.setOption({
      tooltip: { trigger: 'axis' },
      grid: { left: '3%', right: '4%', bottom: '3%', containLabel: true },
      xAxis: {
        type: 'category',
        data: dist.map(d => d.type),
        axisLabel: { color: '#99a4c2' },
        axisLine: { lineStyle: { color: '#2a3358' } }
      },
      yAxis: { type: 'value', axisLabel: { color: '#99a4c2' }, splitLine: { lineStyle: { color: '#2a3358' } } },
      series: [{
        type: 'bar',
        data: dist.map(d => d.count),
        itemStyle: { color: '#4f8cff', borderRadius: [4, 4, 0, 0] },
        label: { show: true, position: 'top', color: '#e7ecf6' }
      }]
    })
  }
}

const handleResize = () => {
  trendInstance?.resize()
  stateInstance?.resize()
  typeInstance?.resize()
}

onMounted(() => {
  loadData()
  window.addEventListener('resize', handleResize)
  setInterval(loadData, 30000)  // 30 秒自动刷新
})

onUnmounted(() => {
  window.removeEventListener('resize', handleResize)
  trendInstance?.dispose()
  stateInstance?.dispose()
  typeInstance?.dispose()
})
</script>

<style lang="scss" scoped>
.saga-dashboard {
  .stat-card {
    background: #161d36 !important;
    border: 1px solid #2a3358 !important;
    text-align: center;
  }
  .pending-card.has-pending {
    border-color: #ff6b6b !important;
    animation: pulse 2s infinite;
  }
  @keyframes pulse {
    0%, 100% { box-shadow: 0 0 0 0 rgba(255,107,107,0.4); }
    50% { box-shadow: 0 0 0 6px rgba(255,107,107,0); }
  }
}
</style>
