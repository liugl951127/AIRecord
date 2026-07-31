<script setup lang="ts">
import { ref } from 'vue'
import { recordingApi } from '@/api'
import { STATE_COLORS, CHANNEL_LABELS, NODE_DEFINITIONS } from '@/utils/nodes'
import { ElMessage } from 'element-plus'

const businessId = ref('BNK20260801-000001')
const loading = ref(false)
const data = ref<any>(null)

async function query() {
  if (!businessId.value) {
    ElMessage.warning('请输入业务 ID')
    return
  }
  loading.value = true
  try {
    data.value = await recordingApi.overview(businessId.value)
  } catch (e: any) {
    ElMessage.error(e.message)
    data.value = null
  } finally {
    loading.value = false
  }
}

const nodeName = (code: string) => {
  return NODE_DEFINITIONS.find(n => n.code === code)?.displayName || code
}
</script>

<template>
  <div>
    <div class="card">
      <h3 class="card-title">
        <span>业务全景查询</span>
      </h3>
      <el-form :inline="true" @submit.prevent="query">
        <el-form-item label="业务 ID">
          <el-input v-model="businessId" placeholder="例如 BNK20260801-000001" style="width: 300px;" />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" :loading="loading" @click="query">
            <el-icon><Search /></el-icon>
            查询
          </el-button>
        </el-form-item>
      </el-form>
    </div>

    <div v-if="data" class="card">
      <h3 class="card-title">
        <span>业务概况</span>
        <span class="state-badge" :style="{ color: STATE_COLORS[data.business.state] }">
          {{ data.business.state }}
        </span>
      </h3>
      <el-descriptions :column="3" border>
        <el-descriptions-item label="业务 ID">{{ data.business.businessId }}</el-descriptions-item>
        <el-descriptions-item label="业务类型">{{ data.business.businessType }}</el-descriptions-item>
        <el-descriptions-item label="产品 ID">
          <span class="mono">{{ data.business.productId }}</span>
        </el-descriptions-item>
        <el-descriptions-item label="渠道">{{ CHANNEL_LABELS[data.business.channel] || data.business.channel }}</el-descriptions-item>
        <el-descriptions-item label="客户">
          <span class="mono">{{ data.business.customerIdHash }}</span>
        </el-descriptions-item>
        <el-descriptions-item label="金额">{{ data.business.amount || '-' }}</el-descriptions-item>
        <el-descriptions-item label="客户风险">
          <span v-if="data.business.riskLevel" class="risk-pill" :class="data.business.riskLevel">{{ data.business.riskLevel }}</span>
        </el-descriptions-item>
        <el-descriptions-item label="产品风险">
          <span v-if="data.business.productRiskLevel" class="risk-pill" :class="data.business.productRiskLevel">{{ data.business.productRiskLevel }}</span>
        </el-descriptions-item>
        <el-descriptions-item label="创建时间">{{ data.business.createdAt }}</el-descriptions-item>
        <el-descriptions-item label="完成节点">
          {{ data.completed_node_count }} / {{ data.node_count }}
        </el-descriptions-item>
        <el-descriptions-item label="更新时间">{{ data.business.updatedAt }}</el-descriptions-item>
        <el-descriptions-item v-if="data.business.archivedAt" label="归档时间">{{ data.business.archivedAt }}</el-descriptions-item>
      </el-descriptions>
    </div>

    <div v-if="data && data.nodes && data.nodes.length" class="card">
      <h3 class="card-title">8 节点明细</h3>
      <el-table :data="data.nodes" stripe>
        <el-table-column prop="nodeId" label="节点" width="180">
          <template #default="{ row }">
            <span class="mono" style="font-size: 11px;">{{ row.nodeId }}</span>
            <div style="font-size: 11px; color: var(--ink-3);">{{ nodeName(row.nodeId) }}</div>
          </template>
        </el-table-column>
        <el-table-column prop="startUtc" label="开始" width="200">
          <template #default="{ row }">
            <span class="mono" style="font-size: 11px;">{{ row.startUtc }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="endUtc" label="结束" width="200">
          <template #default="{ row }">
            <span class="mono" style="font-size: 11px;">{{ row.endUtc }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="durationMs" label="时长" width="100">
          <template #default="{ row }">
            <span class="mono">{{ row.durationMs }}ms</span>
          </template>
        </el-table-column>
        <el-table-column prop="completed" label="完成" width="100">
          <template #default="{ row }">
            <el-tag v-if="row.completed" type="success" size="small">已完成</el-tag>
            <el-tag v-else type="info" size="small">未开始</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="operatorId" label="操作员">
          <template #default="{ row }">
            <span class="mono">{{ row.operatorId }}</span>
          </template>
        </el-table-column>
      </el-table>
    </div>

    <div v-if="data && data.recordings && data.recordings.length" class="card">
      <h3 class="card-title">录像信息</h3>
      <el-table :data="data.recordings" stripe>
        <el-table-column prop="recId" label="录像 ID" width="200">
          <template #default="{ row }">
            <span class="mono" style="font-size: 11px;">{{ row.recId }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="channel" label="渠道" width="120" />
        <el-table-column prop="sellerType" label="销售方" width="120" />
        <el-table-column prop="durationMs" label="时长" width="120">
          <template #default="{ row }">
            <span class="mono">{{ row.durationMs }}ms</span>
          </template>
        </el-table-column>
        <el-table-column prop="encryption" label="加密" width="100" />
        <el-table-column prop="retentionUntil" label="留存至" width="150" />
        <el-table-column prop="watermarkVisible" label="数字水印" width="100">
          <template #default="{ row }">
            <el-tag v-if="row.watermarkVisible" type="success" size="small">已开启</el-tag>
            <el-tag v-else type="info" size="small">未开</el-tag>
          </template>
        </el-table-column>
      </el-table>
    </div>

    <div v-if="!data && !loading" class="card">
      <div style="text-align: center; padding: 60px; color: var(--ink-3);">
        <el-icon size="48" style="margin-bottom: 12px;"><Search /></el-icon>
        <p>输入业务 ID 查询</p>
      </div>
    </div>
  </div>
</template>
