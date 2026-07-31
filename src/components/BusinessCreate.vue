<script setup lang="ts">
import { ref, reactive } from 'vue'
import { useRecordingStore } from '@/stores/recording'
import type { BusinessType, Channel, SellerType } from '@/types'

const emit = defineEmits<{ (e: 'created'): void }>()
const store = useRecordingStore()

const form = reactive({
  businessType: 'WEALTH' as BusinessType,
  productId: 'BNK-FIN-2026Q3-001',
  customerIdHash: 'cust-001',
  channel: 'OFFLINE' as Channel,
  sellerType: 'HUMAN' as SellerType,
  amount: 50000
})

const productPresets: Array<{
  type: BusinessType
  productId: string
  productName: string
  risk: string
  description: string
}> = [
  { type: 'INSURANCE', productId: 'LIC-INV-2026Q3-001', productName: '投连险稳健账户', risk: 'P5', description: '高风险投连险' },
  { type: 'WEALTH', productId: 'BNK-FIN-2026Q3-001', productName: '稳健型封闭式理财', risk: 'R2', description: '中低风险理财' },
  { type: 'FUND', productId: 'FND-BOND-2026Q3-001', productName: '纯债债券型基金', risk: 'R2', description: '中低风险基金' },
  { type: 'WEALTH', productId: 'NEW-PRODUCT-001', productName: '新产品（无专属 YAML）', risk: '?', description: '将自动用全局默认' }
]

function pickProduct(preset: typeof productPresets[0]) {
  form.businessType = preset.type
  form.productId = preset.productId
}

const loading = ref(false)
const created = ref<string>('')

async function handleCreate() {
  loading.value = true
  try {
    const b = await store.startBusiness({
      businessType: form.businessType,
      productId: form.productId,
      customerIdHash: form.customerIdHash,
      channel: form.channel,
      sellerType: form.sellerType,
      amount: form.amount
    })
    created.value = b.businessId

    // 自动加载话术 + 风险评估 + 启动录制
    await store.loadScript(form.productId, form.businessType)
    await store.assessRisk(form.customerIdHash)
    await store.beginRecording()

    setTimeout(() => emit('created'), 1000)
  } catch (e) {
    // 错误已在 store 中处理
  } finally {
    loading.value = false
  }
}
</script>

<template>
  <div>
    <!-- 成功提示 -->
    <el-alert
      v-if="created"
      type="success"
      :closable="false"
      :title="`✓ 业务已创建: ${created}`"
      description="话术已加载 + 风险评估完成 + 录制已启动。请切换到「录制工作台」继续完成 8 节点。"
      style="margin-bottom: 16px;"
    />

    <el-row :gutter="16">
      <!-- 左侧：表单 -->
      <el-col :span="14">
        <div class="card">
          <h3 class="card-title">
            <span>新建双录业务</span>
            <span class="actions">
              <el-tag size="small" type="info">Step 1 / 1</el-tag>
            </span>
          </h3>

          <el-form :model="form" label-width="100px" label-position="right">
            <el-form-item label="业务类型">
              <el-radio-group v-model="form.businessType">
                <el-radio-button value="INSURANCE">保险</el-radio-button>
                <el-radio-button value="WEALTH">银行理财</el-radio-button>
                <el-radio-button value="FUND">基金</el-radio-button>
              </el-radio-group>
            </el-form-item>

            <el-form-item label="产品 ID">
              <el-input v-model="form.productId" placeholder="例如 BNK-FIN-2026Q3-001" />
            </el-form-item>

            <el-form-item label="客户 ID">
              <el-input v-model="form.customerIdHash" placeholder="客户脱敏 ID" />
            </el-form-item>

            <el-form-item label="渠道">
              <el-select v-model="form.channel" style="width: 100%">
                <el-option value="OFFLINE" label="线下面对面" />
                <el-option value="REMOTE_VIDEO" label="远程视频" />
                <el-option value="SELF_AI" label="自助 AI 数字人" />
                <el-option value="INTERNET_TEXT" label="互联网文本" />
              </el-select>
            </el-form-item>

            <el-form-item label="销售方">
              <el-radio-group v-model="form.sellerType">
                <el-radio-button value="HUMAN">真人</el-radio-button>
                <el-radio-button value="AI_DIGITAL_HUMAN">AI 数字人</el-radio-button>
              </el-radio-group>
            </el-form-item>

            <el-form-item label="金额">
              <el-input-number v-model="form.amount" :min="0" :step="10000" />
            </el-form-item>

            <el-form-item>
              <el-button type="primary" :loading="loading" @click="handleCreate" size="large">
                <el-icon><Plus /></el-icon>
                创建业务并启动录制
              </el-button>
            </el-form-item>
          </el-form>
        </div>
      </el-col>

      <!-- 右侧：产品快捷选择 -->
      <el-col :span="10">
        <div class="card">
          <h3 class="card-title">
            <span>产品快捷选择</span>
          </h3>
          <div
            v-for="p in productPresets"
            :key="p.productId"
            class="product-card"
            :class="{ active: form.productId === p.productId }"
            @click="pickProduct(p)"
          >
            <div style="display: flex; align-items: center; justify-content: space-between;">
              <strong>{{ p.productName }}</strong>
              <span class="risk-pill" :class="p.risk">{{ p.risk }}</span>
            </div>
            <div style="font-size: 11px; color: var(--ink-3); margin-top: 4px; font-family: 'JetBrains Mono', monospace;">
              {{ p.productId }}
            </div>
            <div style="font-size: 12px; color: var(--ink-2); margin-top: 4px;">
              {{ p.description }}
            </div>
          </div>
        </div>

        <div class="card" v-if="store.alerts.length > 0">
          <h3 class="card-title">实时日志</h3>
          <div class="alert-list">
            <div
              v-for="(a, i) in store.alerts.slice(0, 10)"
              :key="i"
              class="alert-item"
              :class="a.type"
            >
              <span class="time">{{ a.time }}</span>
              <span class="message">{{ a.message }}</span>
            </div>
          </div>
        </div>
      </el-col>
    </el-row>
  </div>
</template>

<style scoped>
.product-card {
  padding: 12px 14px;
  border: 1px solid var(--line);
  border-radius: 8px;
  margin-bottom: 8px;
  cursor: pointer;
  transition: all 0.2s;
}
.product-card:hover { border-color: var(--accent); transform: translateY(-1px); }
.product-card.active { border-color: var(--primary); background: rgba(30, 42, 71, 0.04); }
</style>
