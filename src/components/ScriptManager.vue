<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { scriptConfigApi } from '@/api'
import { ElMessage } from 'element-plus'

const loading = ref(false)
const activeTab = ref('global')

const globalConfig = ref<any>(null)
const allScripts = ref<Record<string, any>>({})
const newPhrase = ref('')

async function loadGlobal() {
  loading.value = true
  try {
    globalConfig.value = await scriptConfigApi.global()
  } finally {
    loading.value = false
  }
}

async function loadAll() {
  loading.value = true
  try {
    allScripts.value = await scriptConfigApi.all()
  } finally {
    loading.value = false
  }
}

onMounted(() => {
  loadGlobal()
  loadAll()
})

async function addPhrase() {
  if (!newPhrase.value.trim()) return
  try {
    await scriptConfigApi.addForbiddenPhrase(newPhrase.value)
    ElMessage.success(`已新增禁播词: ${newPhrase.value}`)
    newPhrase.value = ''
    await loadGlobal()
  } catch (e: any) {
    ElMessage.error(e.message)
  }
}

const scriptKeys = Object.keys(allScripts.value)
</script>

<template>
  <div>
    <el-tabs v-model="activeTab">
      <el-tab-pane label="全局默认配置" name="global">
        <div v-if="globalConfig" class="card">
          <h3 class="card-title">
            <span>application.yml 全局默认 · {{ globalConfig.defaultForbiddenPhrases?.length || 0 }} 个禁播词</span>
            <span class="actions">
              <el-button size="small" @click="loadGlobal"><el-icon><Refresh /></el-icon></el-button>
            </span>
          </h3>
          <el-descriptions :column="2" border>
            <el-descriptions-item label="默认风险等级">
              <span class="risk-pill" :class="globalConfig.defaultRiskLevel">{{ globalConfig.defaultRiskLevel }}</span>
            </el-descriptions-item>
            <el-descriptions-item label="节点超时">{{ globalConfig.defaultNodeTimeoutSec }} 秒</el-descriptions-item>
            <el-descriptions-item label="必问问题数">{{ globalConfig.defaultRequiredQuestions?.length || 0 }}</el-descriptions-item>
            <el-descriptions-item label="必播项数">{{ globalConfig.defaultMandatoryPhrases?.length || 0 }}</el-descriptions-item>
          </el-descriptions>

          <h4 style="margin-top: 20px; font-size: 13px;">禁播词</h4>
          <div class="phrase-list">
            <div v-for="(p, i) in globalConfig.defaultForbiddenPhrases" :key="i" class="phrase-item forbidden">
              {{ p }}
            </div>
          </div>

          <h4 style="margin-top: 20px; font-size: 13px;">必问问题</h4>
          <div class="phrase-list">
            <div v-for="(q, i) in globalConfig.defaultRequiredQuestions" :key="i" class="phrase-item">
              <el-icon style="color: var(--blue);"><QuestionFilled /></el-icon>
              {{ q }}
            </div>
          </div>

          <h4 style="margin-top: 20px; font-size: 13px;">按产品族默认风险等级</h4>
          <el-table :data="Object.entries(globalConfig.productTypeRiskLevel || {}).map(([k, v]) => ({ type: k, level: v }))">
            <el-table-column prop="type" label="产品族" />
            <el-table-column label="风险等级">
              <template #default="{ row }">
                <span class="risk-pill" :class="row.level">{{ row.level }}</span>
              </template>
            </el-table-column>
          </el-table>

          <h4 style="margin-top: 20px; font-size: 13px;">运行时新增禁播词</h4>
          <div style="display: flex; gap: 8px;">
            <el-input v-model="newPhrase" placeholder="输入新禁播词（立即生效，重启后丢失）" style="width: 400px;" />
            <el-button type="primary" @click="addPhrase">
              <el-icon><Plus /></el-icon>
              新增
            </el-button>
          </div>
        </div>
      </el-tab-pane>

      <el-tab-pane label="所有话术模板" name="all">
        <div class="card">
          <h3 class="card-title">
            <span>已加载话术模板 · {{ Object.keys(allScripts).length }} 份</span>
            <span class="actions">
              <el-button size="small" @click="loadAll"><el-icon><Refresh /></el-icon></el-button>
            </span>
          </h3>

          <el-table :data="Object.entries(allScripts).map(([k, v]) => ({ key: k, ...v }))" stripe>
            <el-table-column prop="key" label="缓存 Key" width="280">
              <template #default="{ row }">
                <span class="mono" style="font-size: 11px;">{{ row.key }}</span>
              </template>
            </el-table-column>
            <el-table-column prop="productType" label="产品族" width="120" />
            <el-table-column prop="riskLevel" label="风险" width="80">
              <template #default="{ row }">
                <span v-if="row.riskLevel" class="risk-pill" :class="row.riskLevel">{{ row.riskLevel }}</span>
              </template>
            </el-table-column>
            <el-table-column prop="version" label="版本" width="120" />
            <el-table-column label="禁播词数" width="100">
              <template #default="{ row }">
                <el-tag size="small">{{ row.forbiddenPhrases?.length || row.forbidden_phrases?.length || 0 }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column label="节点数" width="80">
              <template #default="{ row }">
                <el-tag size="small" type="info">{{ row.nodes?.length || 0 }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column label="来源" width="120">
              <template #default="{ row }">
                <el-tag size="small" :type="row.source === 'GLOBAL_DEFAULT' ? 'info' : 'success'">
                  {{ row.source || 'YAML' }}
                </el-tag>
              </template>
            </el-table-column>
          </el-table>
        </div>
      </el-tab-pane>
    </el-tabs>
  </div>
</template>
