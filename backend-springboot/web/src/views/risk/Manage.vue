<template>
  <div class="risk-page">
    <el-row :gutter="16">
      <el-col :span="12">
        <el-card shadow="hover">
          <template #header>
            <span><el-icon><Warning /></el-icon> 风险评估</span>
          </template>

          <el-form :model="form" label-width="100px" size="large">
            <el-form-item label="客户ID">
              <el-select v-model="form.customerId" filterable @change="onQuery" style="width:100%">
                <el-option
                  v-for="c in presetCustomers"
                  :key="c.id"
                  :label="`${c.id} - ${c.name}`"
                  :value="c.id"
                />
              </el-select>
            </el-form-item>

            <el-form-item label="评估分数">
              <el-input-number v-model="form.score" :min="0" :max="100" :step="5" style="width:100%" />
            </el-form-item>

            <el-form-item>
              <el-button type="primary" @click="onSubmit" :loading="loading">提交评估</el-button>
              <el-button @click="onQuery" :loading="loading">查询历史</el-button>
            </el-form-item>
          </el-form>

          <el-alert
            v-if="result"
            :type="result.score <= 40 ? 'info' : result.score <= 60 ? 'success' : result.score <= 75 ? 'warning' : 'danger'"
            :closable="false"
            show-icon
            style="margin-top:16px"
          >
            <template #title>
              评估结果:{{ getLevelName(result.riskLevel) }} (分数: {{ result.score }})
            </template>
            <div style="margin-top:8px">
              <p>客户风险承受等级:<b>{{ result.riskLevel }}</b></p>
              <p>可购买产品范围:R1 - {{ result.riskLevel }}</p>
              <p>评估时间:{{ dayjs(result.evaluatedAt).format('YYYY-MM-DD HH:mm:ss') }}</p>
            </div>
          </el-alert>
        </el-card>
      </el-col>

      <el-col :span="12">
        <el-card shadow="hover">
          <template #header>
            <span><el-icon><Aim /></el-icon> 产品风险匹配测试</span>
          </template>

          <el-form :model="matchForm" label-width="120px" size="large">
            <el-form-item label="客户风险等级">
              <el-select v-model="matchForm.customerLevel" style="width:100%">
                <el-option v-for="r in riskLevels" :key="r" :label="getLevelName(r)" :value="r" />
              </el-select>
            </el-form-item>

            <el-form-item label="产品风险等级">
              <el-select v-model="matchForm.productLevel" style="width:100%">
                <el-option v-for="r in riskLevels" :key="r" :label="getLevelName(r)" :value="r" />
              </el-select>
            </el-form-item>

            <el-form-item>
              <el-button @click="onMatch" :loading="loading">测试匹配</el-button>
            </el-form-item>
          </el-form>

          <el-alert
            v-if="matchResult"
            :type="matchResult.match ? 'success' : 'error'"
            :closable="false"
            show-icon
            style="margin-top:16px"
          >
            <template #title>
              {{ matchResult.match ? '✓ 风险匹配,可以购买' : '✗ 风险不匹配,禁止购买' }}
            </template>
            <div style="margin-top:8px">
              客户: {{ getLevelName(matchResult.customerLevel) }} /
              产品: {{ getLevelName(matchResult.productLevel) }}
            </div>
          </el-alert>
        </el-card>

        <el-card shadow="hover" style="margin-top:16px">
          <template #header>
            <span><el-icon><InfoFilled /></el-icon> 风险等级说明</span>
          </template>
          <el-table :data="riskDescriptions" size="small">
            <el-table-column prop="level" label="等级" width="80">
              <template #default="{ row }">
                <el-tag :type="row.type" effect="dark" size="small">{{ row.level }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="name" label="名称" width="120" />
            <el-table-column prop="score" label="分数范围" width="100" />
            <el-table-column prop="desc" label="特征" />
          </el-table>
        </el-card>
      </el-col>
    </el-row>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import dayjs from 'dayjs'
import { ElMessage } from 'element-plus'
import { getRiskAssessment, submitRisk, matchProduct } from '@/api/doubleRecording'

const form = reactive({ customerId: 'CUST_2026_0001', score: 75 })
const matchForm = reactive({ customerLevel: 'R3', productLevel: 'R3' })
const result = ref(null)
const matchResult = ref(null)
const loading = ref(false)

const presetCustomers = [
  { id: 'CUST_2026_0001', name: '张三' },
  { id: 'CUST_2026_0002', name: '李四' },
  { id: 'CUST_2026_0003', name: '王五' }
]

const riskLevels = ['R1', 'R2', 'R3', 'R4', 'R5']

const riskDescriptions = [
  { level: 'R1', name: '谨慎型', score: '0-40', type: 'info', desc: '保守,不愿承受任何损失' },
  { level: 'R2', name: '稳健型', score: '41-60', type: 'success', desc: '可承受较低波动' },
  { level: 'R3', name: '平衡型', score: '61-75', type: 'warning', desc: '追求稳健增长,可承受中等波动' },
  { level: 'R4', name: '进取型', score: '76-90', type: 'danger', desc: '追求高收益,可承受较大波动' },
  { level: 'R5', name: '激进型', score: '91-100', type: 'danger', desc: '追求最高收益,可承受极端损失' }
]

const getLevelName = (level) => riskDescriptions.find(r => r.level === level)?.name || level

const onQuery = async () => {
  loading.value = true
  try {
    const res = await getRiskAssessment(form.customerId)
    result.value = res.data
    form.score = res.data.score
  } catch (e) {
    result.value = null
  } finally {
    loading.value = false
  }
}

const onSubmit = async () => {
  loading.value = true
  try {
    const res = await submitRisk({
      customerId: form.customerId,
      score: form.score,
      answers: '{"source":"frontend"}'
    })
    result.value = res.data
    ElMessage.success('评估已保存')
  } catch (e) {
    ElMessage.error('提交失败: ' + e.message)
  } finally {
    loading.value = false
  }
}

const onMatch = async () => {
  loading.value = true
  try {
    const res = await matchProduct(matchForm.customerLevel, matchForm.productLevel)
    matchResult.value = res.data
  } catch (e) {
    ElMessage.error('匹配失败: ' + e.message)
  } finally {
    loading.value = false
  }
}

onMounted(() => {
  onQuery()
})
</script>

<style lang="scss" scoped>
.risk-page {
  :deep(.el-card) {
    background: #161d36 !important;
    border-color: #2a3358 !important;
  }
}
</style>
