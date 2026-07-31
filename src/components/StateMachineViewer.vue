<script setup lang="ts">
defineProps<{ transitions: Record<string, string[]> }>()

const stateDescriptions: Record<string, string> = {
  INIT: '客户进入双录',
  IDENTITY_VERIFIED: '身份核验通过',
  RISK_ASSESSED: '风险评估完成',
  SCRIPT_LOADED: '话术加载完成',
  RECORDING: '录像中（8 节点）',
  RECORDED: '录像完成',
  AI_QA: 'AI 质检中',
  AI_QA_PASSED: 'AI 质检通过',
  AI_QA_FLAGGED: 'AI 标红待人工',
  HUMAN_REVIEW: '人工复核中',
  HUMAN_REVIEWED: '人工复核完成',
  SIGNED: '客户签字',
  ARCHIVED: '已归档（终态）',
  FAILED: '失败（可恢复）',
  ROLLED_BACK: '已回滚（终态）'
}

const stateColors: Record<string, string> = {
  INIT: '#94a3b8',
  RECORDING: '#3b6b8c',
  RECORDED: '#3b6b8c',
  AI_QA: '#6b4a8a',
  AI_QA_PASSED: '#2f6f5e',
  AI_QA_FLAGGED: '#c1453a',
  HUMAN_REVIEW: '#6b4a8a',
  HUMAN_REVIEWED: '#2f6f5e',
  SIGNED: '#2f6f5e',
  ARCHIVED: '#1e2a47',
  FAILED: '#c1453a',
  ROLLED_BACK: '#94a3b8',
  IDENTITY_VERIFIED: '#94a3b8',
  RISK_ASSESSED: '#94a3b8',
  SCRIPT_LOADED: '#94a3b8'
}

const states = Object.keys(stateDescriptions)
</script>

<template>
  <div>
    <div class="card">
      <h3 class="card-title">
        <span>8 节点双录状态机 · {{ Object.keys(transitions).length }} 个状态</span>
        <span class="actions">
          <el-tag size="small">实时同步自后端</el-tag>
        </span>
      </h3>

      <p style="color: var(--ink-2); margin: 0 0 16px; font-size: 13px;">
        任意状态变更都会查这张转移表。非法转移直接抛 409 异常，触发 Saga 自动补偿。
      </p>

      <div class="state-machine">
        <div
          v-for="state in states"
          :key="state"
          class="state-chip"
          :style="{ borderColor: stateColors[state], color: stateColors[state] }"
          :title="stateDescriptions[state]"
        >
          {{ state }}
        </div>
      </div>
    </div>

    <div class="card">
      <h3 class="card-title">合法转移明细</h3>
      <el-table :data="Object.entries(transitions).map(([k, v]) => ({ from: k, to: v }))" stripe>
        <el-table-column prop="from" label="源状态" width="220">
          <template #default="{ row }">
            <span class="mono" :style="{ color: stateColors[row.from] }">{{ row.from }}</span>
            <div style="font-size: 11px; color: var(--ink-3); margin-top: 2px;">
              {{ stateDescriptions[row.from] }}
            </div>
          </template>
        </el-table-column>
        <el-table-column prop="to" label="可转移至">
          <template #default="{ row }">
            <el-tag
              v-for="t in row.to"
              :key="t"
              size="small"
              :style="{ marginRight: '6px', marginBottom: '4px', background: stateColors[t] + '20', color: stateColors[t], border: 'none' }"
            >
              {{ t }}
            </el-tag>
            <span v-if="!row.to.length" style="color: var(--ink-3); font-style: italic;">终态</span>
          </template>
        </el-table-column>
      </el-table>
    </div>
  </div>
</template>
