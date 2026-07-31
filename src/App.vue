<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { healthApi, stateMachineApi } from '@/api'
import BusinessCreate from '@/components/BusinessCreate.vue'
import RecordingWorkbench from '@/components/RecordingWorkbench.vue'
import OverviewPanel from '@/components/OverviewPanel.vue'
import ScriptManager from '@/components/ScriptManager.vue'
import StateMachineViewer from '@/components/StateMachineViewer.vue'

const activeTab = ref('workbench')
const backendOnline = ref(false)
const transitions = ref<Record<string, string[]>>({})

onMounted(async () => {
  try {
    await healthApi.check()
    backendOnline.value = true
    transitions.value = await stateMachineApi.transitions()
  } catch (e) {
    backendOnline.value = false
  }
})

const tabs = [
  { name: 'workbench', label: '录制工作台', icon: 'Microphone' },
  { name: 'create', label: '业务创建', icon: 'Plus' },
  { name: 'overview', label: '全景查询', icon: 'DataAnalysis' },
  { name: 'scripts', label: '话术管理', icon: 'Document' },
  { name: 'state', label: '状态机', icon: 'Connection' }
]
</script>

<template>
  <div class="app-layout">
    <!-- 顶栏 -->
    <header class="app-header">
      <div>
        <h1>智能双录工作台</h1>
        <div class="subtitle">SMART DUAL RECORD WORKBENCH · 8 节点状态机 + Saga + AI 实时质检</div>
      </div>
      <div class="badges">
        <span class="badge" :class="{ offline: !backendOnline }">
          <span class="dot"></span>
          {{ backendOnline ? '后端已连接' : '后端离线' }}
        </span>
        <span class="badge">v1.0.0</span>
        <span class="badge">Spring Boot 3 + Vue 3</span>
      </div>
    </header>

    <!-- 主内容 -->
    <main class="app-main">
      <el-tabs v-model="activeTab" type="border-card">
        <el-tab-pane
          v-for="t in tabs"
          :key="t.name"
          :name="t.name"
        >
          <template #label>
            <span style="display: inline-flex; align-items: center; gap: 6px;">
              <el-icon><component :is="t.icon" /></el-icon>
              {{ t.label }}
            </span>
          </template>

          <BusinessCreate v-if="activeTab === 'create'" @created="activeTab = 'workbench'" />
          <RecordingWorkbench v-else-if="activeTab === 'workbench'" />
          <OverviewPanel v-else-if="activeTab === 'overview'" />
          <ScriptManager v-else-if="activeTab === 'scripts'" />
          <StateMachineViewer v-else-if="activeTab === 'state'" :transitions="transitions" />
        </el-tab-pane>
      </el-tabs>
    </main>

    <footer class="app-footer">
      双录一体化中台 · v1.0 · 2026 · 一个中台 · 两条跑道
    </footer>
  </div>
</template>
