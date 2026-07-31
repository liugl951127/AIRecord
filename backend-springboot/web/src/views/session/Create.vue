<template>
  <div class="create-page">
    <el-row :gutter="20">
      <el-col :span="14">
        <el-card shadow="hover">
          <template #header>
            <div class="card-header">
              <span><el-icon><Plus /></el-icon> 创建双录会话</span>
              <el-tag size="small" effect="dark" type="info">步骤 1/3</el-tag>
            </div>
          </template>

          <el-form
            ref="formRef"
            :model="form"
            :rules="rules"
            label-width="100px"
            label-position="right"
            size="large"
          >
            <el-form-item label="客户ID" prop="customerId">
              <el-select
                v-model="form.customerId"
                filterable
                allow-create
                placeholder="选择或输入客户ID"
                style="width:100%"
                @change="onCustomerChange"
              >
                <el-option
                  v-for="c in presetCustomers"
                  :key="c.id"
                  :label="`${c.id} - ${c.name} (${c.riskLevel})`"
                  :value="c.id"
                />
              </el-select>
            </el-form-item>

            <el-form-item label="客户姓名" prop="customerName">
              <el-input v-model="form.customerName" placeholder="请输入客户姓名" />
            </el-form-item>

            <el-form-item label="产品ID" prop="productId">
              <el-select
                v-model="form.productId"
                placeholder="选择产品"
                style="width:100%"
                @change="onProductChange"
              >
                <el-option
                  v-for="p in products"
                  :key="p.id"
                  :label="`${p.id} - ${p.name} [${p.risk}]`"
                  :value="p.id"
                />
              </el-select>
            </el-form-item>

            <el-form-item label="产品名称">
              <el-input v-model="form.productName" placeholder="自动从模板获取" disabled />
            </el-form-item>

            <el-form-item label="销售渠道">
              <el-radio-group v-model="form.channel">
                <el-radio-button value="APP">App</el-radio-button>
                <el-radio-button value="H5">H5</el-radio-button>
                <el-radio-button value="MINI">小程序</el-radio-button>
                <el-radio-button value="PAD">线下PAD</el-radio-button>
                <el-radio-button value="COUNTER">柜面</el-radio-button>
              </el-radio-group>
            </el-form-item>

            <el-form-item label="订单金额">
              <el-input-number
                v-model="form.orderAmount"
                :min="0"
                :step="1000"
                style="width:100%"
              />
            </el-form-item>

            <el-form-item>
              <el-button type="primary" size="large" :loading="loading" @click="onSubmit">
                <el-icon><Check /></el-icon> 创建会话并进入话术引导
              </el-button>
              <el-button size="large" @click="onReset">重置</el-button>
            </el-form-item>
          </el-form>
        </el-card>
      </el-col>

      <el-col :span="10">
        <el-card shadow="hover">
          <template #header>
            <span><el-icon><InfoFilled /></el-icon> 流程预览</span>
          </template>

          <el-steps direction="vertical" :active="0" space="80px">
            <el-step title="创建会话" description="客户 + 产品 + 渠道">
              <template #icon><el-icon><Plus /></el-icon></template>
            </el-step>
            <el-step title="话术引导" description="11 个标准节点 + 实时质检">
              <template #icon><el-icon><ChatLineRound /></el-icon></template>
            </el-step>
            <el-step title="视频录制" description="WebRTC 实时录制 + 加密">
              <template #icon><el-icon><VideoCamera /></el-icon></template>
            </el-step>
            <el-step title="电子签字" description="数字证书 + 时间戳">
              <template #icon><el-icon><EditPen /></el-icon></template>
            </el-step>
            <el-step title="Saga 存证" description="视频+订单+区块链 原子完成">
              <template #icon><el-icon><Connection /></el-icon></template>
            </el-step>
          </el-steps>
        </el-card>

        <el-card shadow="hover" style="margin-top:16px">
          <template #header>
            <span><el-icon><Warning /></el-icon> 合规要点</span>
          </template>
          <ul class="compliance-list">
            <li><el-icon color="#5fd0a4"><Check /></el-icon> 必须全程录音录像</li>
            <li><el-icon color="#5fd0a4"><Check /></el-icon> 风险揭示必含"本金损失""最不利"</li>
            <li><el-icon color="#5fd0a4"><Check /></el-icon> 客户必须明确同意</li>
            <li><el-icon color="#5fd0a4"><Check /></el-icon> 禁止"保本""稳赚不赔"等表述</li>
            <li><el-icon color="#5fd0a4"><Check /></el-icon> 风险揭示时长 ≥30 秒</li>
            <li><el-icon color="#5fd0a4"><Check /></el-icon> 区块链存证可追溯</li>
          </ul>
        </el-card>
      </el-col>
    </el-row>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { useSessionStore } from '@/stores/session'
import { getRiskAssessment } from '@/api/doubleRecording'

const router = useRouter()
const session = useSessionStore()
const formRef = ref(null)
const loading = ref(false)

const presetCustomers = [
  { id: 'CUST_2026_0001', name: '张三', riskLevel: 'R3' },
  { id: 'CUST_2026_0002', name: '李四', riskLevel: 'R1' },
  { id: 'CUST_2026_0003', name: '王五', riskLevel: 'R5' }
]

const products = [
  { id: 'PROD_FIN_R3_001', name: '稳赢系列-平衡型理财', risk: 'R3' },
  { id: 'PROD_FIN_R1_001', name: '现金管理-货币基金', risk: 'R1' }
]

const form = reactive({
  customerId: 'CUST_2026_0001',
  customerName: '张三',
  productId: 'PROD_FIN_R3_001',
  productName: '稳赢系列-平衡型理财',
  channel: 'APP',
  orderAmount: 100000
})

const rules = {
  customerId: [{ required: true, message: '请选择客户', trigger: 'change' }],
  customerName: [{ required: true, message: '请输入客户姓名', trigger: 'blur' }],
  productId: [{ required: true, message: '请选择产品', trigger: 'change' }]
}

const onCustomerChange = async (val) => {
  const c = presetCustomers.find(c => c.id === val)
  if (c) form.customerName = c.name
}

const onProductChange = (val) => {
  const p = products.find(p => p.id === val)
  if (p) form.productName = p.name
}

const onSubmit = async () => {
  if (!formRef.value) return
  try {
    await formRef.value.validate()
    loading.value = true
    await session.doCreate(form)
    ElMessage.success('会话创建成功!')
    router.push('/session/process')
  } catch (e) {
    if (e?.message) ElMessage.error('创建失败: ' + e.message)
  } finally {
    loading.value = false
  }
}

const onReset = () => {
  formRef.value?.resetFields()
}

onMounted(() => {
  // 尝试加载预置客户
})
</script>

<style lang="scss" scoped>
.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.compliance-list {
  list-style: none;
  padding: 0;
  margin: 0;

  li {
    display: flex;
    align-items: center;
    gap: 8px;
    padding: 8px 0;
    color: #99a4c2;
    font-size: 13px;
    border-bottom: 1px dashed #2a3358;

    &:last-child { border-bottom: none; }
  }
}
</style>
