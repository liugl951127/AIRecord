<template>
  <div class="quality-page">
    <el-row :gutter="16">
      <el-col :span="14">
        <el-card shadow="hover">
          <template #header>
            <div class="card-header">
              <span><el-icon><Document /></el-icon> 节点执行明细</span>
              <el-tag v-if="session.sessionId" type="success" effect="dark">
                会话: {{ session.sessionId }}
              </el-tag>
            </div>
          </template>

          <el-alert v-if="!session.sessionId" type="info" :closable="false" show-icon
            title="提示" description="请先创建会话并走完流程,这里会显示所有节点的执行情况和质检结果" />

          <el-table
            v-else
            :data="nodes"
            stripe
            style="width:100%"
            max-height="600"
            v-loading="loading"
          >
            <el-table-column type="index" label="#" width="60" />
            <el-table-column prop="nodeSeq" label="节点" width="80">
              <template #default="{ row }">
                <el-tag effect="dark" type="primary">N{{ String(row.nodeSeq).padStart(2, '0') }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="nodeTitle" label="标题" width="120" />
            <el-table-column prop="nodeType" label="类型" width="140">
              <template #default="{ row }">
                <el-tag effect="plain" size="small">{{ row.nodeType }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="qualityStatus" label="质检" width="100">
              <template #default="{ row }">
                <el-tag :type="statusTag(row.qualityStatus)" effect="dark" size="small">
                  {{ statusText(row.qualityStatus) }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="qualityMessage" label="质检消息" show-overflow-tooltip />
            <el-table-column prop="startedAt" label="完成时间" width="180">
              <template #default="{ row }">
                {{ formatTime(row.completedAt) }}
              </template>
            </el-table-column>
          </el-table>
        </el-card>
      </el-col>

      <el-col :span="10">
        <el-card shadow="hover">
          <template #header>
            <span><el-icon><CircleCheck /></el-icon> 质检规则</span>
            <el-tag size="small" type="info" style="float:right">共 {{ rules.length }} 条</el-tag>
          </template>

          <el-collapse v-model="activeRule" accordion>
            <el-collapse-item
              v-for="rule in rules"
              :key="rule.ruleCode"
              :name="rule.ruleCode"
            >
              <template #title>
                <div class="rule-title">
                  <el-tag :type="severityTag(rule.severity)" effect="dark" size="small">
                    {{ rule.severity }}
                  </el-tag>
                  <el-tag effect="plain" size="small">{{ rule.ruleCode }}</el-tag>
                  <span style="margin-left:8px">{{ rule.ruleName }}</span>
                </div>
              </template>
              <div class="rule-detail">
                <p><b>类型:</b> {{ rule.ruleType }}</p>
                <p><b>描述:</b> {{ rule.description }}</p>
                <p v-if="rule.ruleConfig">
                  <b>配置:</b>
                  <pre>{{ formatConfig(rule.ruleConfig) }}</pre>
                </p>
                <p><b>状态:</b>
                  <el-tag :type="rule.enabled ? 'success' : 'info'" size="small">
                    {{ rule.enabled ? '启用' : '禁用' }}
                  </el-tag>
                </p>
              </div>
            </el-collapse-item>
          </el-collapse>
        </el-card>
      </el-col>
    </el-row>

    <!-- 质检报告 -->
    <el-card v-if="report" shadow="hover" style="margin-top:16px">
      <template #header>
        <span><el-icon><DataAnalysis /></el-icon> 最终质检报告</span>
        <el-tag :type="report.finalStatus === 'PASS' ? 'success' : 'danger'" effect="dark" size="large" style="float:right">
          {{ report.finalStatus }}
        </el-tag>
      </template>

      <el-row :gutter="16">
        <el-col :span="6">
          <el-statistic title="总节点数" :value="report.totalNodes" />
        </el-col>
        <el-col :span="6">
          <el-statistic title="通过" :value="report.passedNodes" :value-style="{ color: '#5fd0a4' }" />
        </el-col>
        <el-col :span="6">
          <el-statistic title="失败" :value="report.failedNodes" :value-style="{ color: '#ff6b6b' }" />
        </el-col>
        <el-col :span="6">
          <el-statistic title="规则版本" :value="report.ruleVersion" />
        </el-col>
      </el-row>

      <el-divider />

      <p><b>报告ID:</b> {{ report.reportId }}</p>
      <p><b>生成时间:</b> {{ formatTime(report.generatedAt) }}</p>
      <p v-if="report.p0Missing"><b>P0 缺失:</b> {{ report.p0Missing }}</p>
    </el-card>
  </div>
</template>

<script setup>
import { ref, watch, onMounted } from 'vue'
import dayjs from 'dayjs'
import { useSessionStore } from '@/stores/session'
import { getSessionNodes, getQualityRules, getQualityReport } from '@/api/doubleRecording'

const session = useSessionStore()
const nodes = ref([])
const rules = ref([])
const report = ref(null)
const loading = ref(false)
const activeRule = ref('')

const formatTime = (t) => t ? dayjs(t).format('YYYY-MM-DD HH:mm:ss') : '-'

const statusTag = (s) => ({
  PASS: 'success', FAIL: 'warning', BLOCKED: 'danger', null: 'info'
}[s] || 'info')

const statusText = (s) => ({
  PASS: '✓ 通过', FAIL: '⚠ 告警', BLOCKED: '✗ 阻断', null: '未执行'
}[s] || '未知')

const severityTag = (s) => ({
  P0: 'danger', P1: 'warning', P2: 'info'
}[s] || '')

const formatConfig = (cfg) => {
  try { return JSON.stringify(JSON.parse(cfg), null, 2) } catch { return cfg }
}

const loadData = async () => {
  loading.value = true
  try {
    if (session.sessionId) {
      const [nodesRes, reportRes] = await Promise.all([
        getSessionNodes(session.sessionId).catch(() => ({ data: [] })),
        getQualityReport(session.sessionId).catch(() => ({ data: null }))
      ])
      nodes.value = nodesRes.data || []
      report.value = reportRes.data
    }
    const rulesRes = await getQualityRules()
    rules.value = rulesRes.data || []
  } finally {
    loading.value = false
  }
}

watch(() => session.sessionId, () => loadData(), { immediate: true })
onMounted(loadData)
</script>

<style lang="scss" scoped>
.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.rule-title {
  display: flex;
  align-items: center;
  gap: 8px;
}

.rule-detail {
  p { margin: 8px 0; color: #99a4c2; font-size: 13px; }
  b { color: #e7ecf6; }
  pre {
    background: #0a0a0a;
    color: #5fd0a4;
    padding: 8px;
    border-radius: 4px;
    font-size: 11px;
    margin-top: 4px;
  }
}
</style>
