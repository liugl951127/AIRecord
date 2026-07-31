<template>
  <div class="script-page">
    <el-card shadow="hover">
      <template #header>
        <div class="card-header">
          <span><el-icon><Document /></el-icon> 话术模板</span>
          <div>
            <el-select v-model="filterRisk" placeholder="风险等级" clearable size="small" style="width:140px;margin-right:8px">
              <el-option label="R1 低" value="R1" />
              <el-option label="R2 中低" value="R2" />
              <el-option label="R3 中" value="R3" />
              <el-option label="R4 中高" value="R4" />
              <el-option label="R5 高" value="R5" />
            </el-select>
            <el-button :icon="Refresh" size="small" @click="loadTemplates">刷新</el-button>
          </div>
        </div>
      </template>

      <el-table :data="filteredTemplates" stripe v-loading="loading" @row-click="onSelect">
        <el-table-column prop="templateId" label="模板ID" width="200" />
        <el-table-column prop="templateName" label="名称" min-width="240" />
        <el-table-column prop="productType" label="产品类型" width="120">
          <template #default="{ row }">
            <el-tag effect="plain" size="small">{{ productTypeName(row.productType) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="riskLevel" label="风险等级" width="100">
          <template #default="{ row }">
            <el-tag :type="riskType(row.riskLevel)" effect="dark" size="small">{{ row.riskLevel }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="version" label="版本" width="80" />
        <el-table-column prop="status" label="状态" width="100">
          <template #default="{ row }">
            <el-tag :type="row.status === 'PUBLISHED' ? 'success' : 'info'" effect="dark" size="small">
              {{ statusName(row.status) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="effectiveTime" label="生效时间" width="180">
          <template #default="{ row }">
            {{ dayjs(row.effectiveTime).format('YYYY-MM-DD HH:mm') }}
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <el-card v-if="selectedTemplate" shadow="hover" style="margin-top:16px">
      <template #header>
        <span>
          <el-icon><View /></el-icon> 模板详情 - {{ selectedTemplate.templateId }} v{{ selectedTemplate.version }}
        </span>
      </template>

      <h4>话术节点 ({{ nodes.length }})</h4>
      <el-timeline>
        <el-timeline-item
          v-for="node in nodes"
          :key="node.id"
          :timestamp="`N${String(node.nodeSeq).padStart(2, '0')}`"
          :type="nodeTypeColor(node.nodeType)"
          placement="top"
        >
          <el-card shadow="hover" class="node-item">
            <div class="node-header">
              <span><b>{{ node.nodeTitle }}</b></span>
              <el-tag effect="plain" size="small">{{ node.nodeType }}</el-tag>
              <el-tag type="info" effect="plain" size="small">{{ node.requiredDurationSec }}秒</el-tag>
            </div>
            <p class="node-content">{{ node.scriptContent }}</p>
            <div v-if="getKeywords(node.nodeSeq).length" class="node-keywords">
              <span style="color:#99a4c2;font-size:12px">合规词:</span>
              <el-tag
                v-for="kw in getKeywords(node.nodeSeq)"
                :key="kw.keyword"
                :type="kw.priority === 'P0' ? 'danger' : 'warning'"
                size="small"
                effect="plain"
                style="margin:2px"
              >
                {{ kw.keyword }} ({{ kw.priority }})
              </el-tag>
            </div>
          </el-card>
        </el-timeline-item>
      </el-timeline>
    </el-card>
  </div>
</template>

<script setup>
import { ref, computed, onMounted, watch } from 'vue'
import dayjs from 'dayjs'
import { Refresh, View } from '@element-plus/icons-vue'
import { getScriptTemplates, getTemplate } from '@/api/doubleRecording'

const templates = ref([])
const selectedTemplate = ref(null)
const nodes = ref([])
const keywordsMap = ref({})
const filterRisk = ref('')
const loading = ref(false)

const filteredTemplates = computed(() =>
  templates.value.filter(t => !filterRisk.value || t.riskLevel === filterRisk.value)
)

const productTypeName = (t) => ({
  BANK_FINANCE: '银行理财', FUND: '基金', INSURANCE: '保险', TRUST: '信托'
}[t] || t)

const riskType = (r) => ({ R1: 'info', R2: 'success', R3: 'warning', R4: 'danger', R5: 'danger' }[r] || '')

const statusName = (s) => ({
  DRAFT: '草稿', REVIEW: '审核中', PUBLISHED: '已发布', DEPRECATED: '已废弃'
}[s] || s)

const nodeTypeColor = (t) => {
  if (t?.includes('RISK')) return 'danger'
  if (t?.includes('SIGN')) return 'warning'
  if (t?.includes('PRODUCT')) return 'success'
  return 'primary'
}

const getKeywords = (seq) => keywordsMap.value[seq] || []

const onSelect = async (row) => {
  selectedTemplate.value = row
  const res = await getTemplate(row.templateId, row.version)
  nodes.value = res.data.nodes || []
  keywordsMap.value = res.data.keywords || {}
}

const loadTemplates = async () => {
  loading.value = true
  try {
    const res = await getScriptTemplates()
    templates.value = res.data || []
  } finally {
    loading.value = false
  }
}

onMounted(loadTemplates)
</script>

<style lang="scss" scoped>
.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.node-item {
  background: #1c2340 !important;
  border: 1px solid #2a3358 !important;
}

.node-header {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 8px;
}

.node-content {
  color: #99a4c2;
  font-size: 13px;
  line-height: 1.7;
  padding: 8px;
  background: #0a0a0a;
  border-radius: 4px;
  white-space: pre-wrap;
  margin: 0 0 8px 0;
}

.node-keywords {
  display: flex;
  align-items: center;
  gap: 4px;
  flex-wrap: wrap;
}
</style>
