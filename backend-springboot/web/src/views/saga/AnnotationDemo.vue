<template>
  <div class="annotation-demo">
    <el-alert
      type="info"
      :closable="false"
      show-icon
      style="margin-bottom:16px"
    >
      <template #title>
        <b>Saga 自定义注解 + AOP 切面 - 声明式事务一致性</b>
      </template>
      <div style="margin-top:8px;line-height:1.7">
        通过 <code>@Saga</code> + <code>@SagaStep</code> 注解,Saga 切面自动:<br/>
        ✅ 收集所有 <code>@SagaStep</code> 步骤并按 <code>order</code> 排序<br/>
        ✅ 每个步骤强制 <code>REQUIRES_NEW</code> 独立事务<br/>
        ✅ 任一失败自动逆序调用 <code>compensate</code> 方法<br/>
        ✅ 写入 SagaLog 全审计
      </div>
    </el-alert>

    <el-row :gutter="16">
      <!-- 左侧:业务表单 + 执行 -->
      <el-col :span="10">
        <el-card shadow="hover">
          <template #header>
            <span><el-icon><EditPen /></el-icon> 业务参数</span>
          </template>

          <el-form :model="form" label-width="100px" size="default">
            <el-form-item label="订单ID">
              <el-input v-model="form.orderId" placeholder="订单唯一标识" />
            </el-form-item>
            <el-form-item label="用户ID">
              <el-input v-model="form.userId" />
            </el-form-item>
            <el-form-item label="商品ID">
              <el-input v-model="form.productId" />
            </el-form-item>
            <el-form-item label="数量">
              <el-input-number v-model="form.quantity" :min="1" :max="100000" style="width:100%" />
            </el-form-item>
            <el-form-item label="金额">
              <el-input-number v-model="form.amount" :min="0" :step="10" style="width:100%" />
            </el-form-item>
            <el-form-item>
              <el-button type="primary" :icon="VideoPlay" @click="onSubmit" :loading="loading" style="width:100%">
                执行 Saga(@Saga 注解)
              </el-button>
              <el-button :icon="WarningFilled" @click="onFailDemo" :loading="loading" style="width:100%;margin-top:8px">
                触发失败演示(库存不足)
              </el-button>
            </el-form-item>
          </el-form>
        </el-card>

        <el-card v-if="result" shadow="hover" style="margin-top:16px">
          <template #header>
            <span><el-icon><DataAnalysis /></el-icon> 执行结果</span>
            <el-tag :type="result.success ? 'success' : 'danger'" effect="dark" style="float:right">
              {{ result.success ? '成功' : '失败' }}
            </el-tag>
          </template>
          <el-alert :type="result.success ? 'success' : 'error'" :closable="false" show-icon>
            <pre style="margin:0;white-space:pre-wrap">{{ result.message }}</pre>
          </el-alert>
          <el-button type="primary" text size="small" @click="goToList" style="margin-top:8px">
            查看 Saga 列表 →
          </el-button>
        </el-card>
      </el-col>

      <!-- 右侧:执行流程图 -->
      <el-col :span="14">
        <el-card shadow="hover">
          <template #header>
            <span><el-icon><Operation /></el-icon> 4 个步骤流程图</span>
            <el-tag size="small" type="info" style="float:right">
              {{ completedSteps.length }} / {{ allSteps.length }}
            </el-tag>
          </template>

          <div class="flow">
            <div
              v-for="(s, idx) in allSteps"
              :key="s.name"
              class="flow-step"
              :class="{
                'done': completedSteps.includes(s.name),
                'failed': failedStep === s.name,
                'active': currentStep === s.name
              }"
            >
              <div class="step-num">N{{ String(idx+1).padStart(2,'0') }}</div>
              <div class="step-name">{{ s.name }}</div>
              <div class="step-label">{{ s.label }}</div>
              <div class="step-tx">REQUIRES_NEW</div>
              <div v-if="idx < allSteps.length - 1" class="step-arrow">→</div>
            </div>
          </div>

          <el-divider />

          <h4>🔄 补偿流程(逆序)</h4>
          <div class="compensate-flow">
            <div
              v-for="(s, idx) in [...allSteps].reverse()"
              :key="`comp-${s.name}`"
              class="comp-step"
              :class="{ 'will-comp': failedStep && allSteps.findIndex(x => x.name === s.name) >= allSteps.findIndex(x => x.name === failedStep) }"
            >
              <div class="comp-num">C{{ String(allSteps.length - idx).padStart(2,'0') }}</div>
              <div class="comp-name">compensate{{ s.methodName }}</div>
              <div class="comp-tx">REQUIRES_NEW</div>
              <div v-if="idx < allSteps.length - 1" class="comp-arrow">→</div>
            </div>
          </div>

          <el-divider />

          <h4>📜 执行日志</h4>
          <div class="log-panel">
            <div v-for="(log, i) in logs" :key="i" :style="{color: logColor(log.type)}">
              [{{ log.time }}] {{ log.msg }}
            </div>
            <div v-if="!logs.length" style="color:#99a4c2;text-align:center">暂无日志,点击"执行 Saga"开始</div>
          </div>
        </el-card>
      </el-col>
    </el-row>
  </div>
</template>

<script setup>
import { ref, reactive } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import {
  VideoPlay, WarningFilled, EditPen, DataAnalysis, Operation
} from '@element-plus/icons-vue'
import { submitOrder, submitOrderFailDemo } from '@/api/sagaExample'

const router = useRouter()
const loading = ref(false)
const result = ref(null)
const completedSteps = ref([])
const failedStep = ref('')
const currentStep = ref('')
const logs = ref([])

const form = reactive({
  orderId: 'ORD_' + Date.now().toString().slice(-6),
  userId: 'U001',
  productId: 'P001',
  quantity: 1,
  amount: 50
})

const allSteps = [
  { name: 'CREATE_ORDER', label: '创建订单', methodName: 'CreateOrder', critical: true },
  { name: 'DEDUCT_STOCK', label: '扣减库存', methodName: 'DeductStock', critical: true },
  { name: 'CHARGE', label: '扣款', methodName: 'Charge', critical: true },
  { name: 'NOTIFY', label: '发送通知', methodName: 'Notify', critical: false }
]

const addLog = (msg, type = 'info') => {
  const time = new Date().toTimeString().slice(0, 8)
  logs.value.unshift({ time, msg, type })
}

const logColor = (type) => ({
  success: '#5fd0a4', error: '#ff6b6b', warn: '#ffb454', info: '#99a4c2'
}[type] || '#99a4c2')

const resetState = () => {
  completedSteps.value = []
  failedStep.value = ''
  currentStep.value = ''
  result.value = null
}

const simulateExecution = (isFail) => {
  resetState()
  return new Promise((resolve) => {
    let i = 0
    const interval = setInterval(() => {
      if (i >= allSteps.length) {
        clearInterval(interval)
        resolve()
        return
      }
      const step = allSteps[i]
      currentStep.value = step.name
      addLog(`  → 步骤${i+1} ${step.name} 开始执行`, 'info')
      addLog(`  ✓ ${step.name} 独立事务提交 (REQUIRES_NEW)`, 'success')
      completedSteps.value.push(step.name)
      i++

      if (isFail && (step.name === 'DEDUCT_STOCK' || step.name === 'NOTIFY')) {
        clearInterval(interval)
        addLog(`  ✗ ${step.name} 执行失败`, 'error')
        failedStep.value = step.name
        setTimeout(() => {
          const executed = [...completedSteps.value]
          executed.reverse()
          executed.forEach(name => {
            if (allSteps.findIndex(s => s.name === name) >= allSteps.findIndex(s => s.name === failedStep.value)) {
              addLog(`  ← 补偿 ${name} 开始 (REQUIRES_NEW)`, 'warn')
              addLog(`  ✓ ${name} 补偿事务提交`, 'success')
            }
          })
          addLog(`Saga 最终状态: COMPENSATED`, 'warn')
          resolve()
        }, 800)
        return
      }
    }, 600)
  })
}

const onSubmit = async () => {
  loading.value = true
  try {
    resetState()
    addLog('开始调用 @Saga 注解方法 submitOrder()', 'info')
    addLog('SagaAspect 拦截 @Saga 注解,准备编排步骤', 'info')

    const apiCall = submitOrder({ ...form })
    const animation = simulateExecution(false)
    const [res] = await Promise.all([apiCall, animation])

    if (res.code === 200) {
      result.value = { success: true, message: 'Saga 执行成功!所有步骤提交,事务一致。' }
      addLog('✓ Saga 全部步骤成功,执行业务方法返回结果', 'success')
      addLog('Saga 状态: COMPLETED', 'success')
      ElMessage.success('Saga 执行成功!')
    } else {
      result.value = { success: false, message: res.message }
      addLog('✗ Saga 执行失败: ' + res.message, 'error')
    }
  } catch (e) {
    result.value = { success: false, message: e.message || JSON.stringify(e) }
    addLog('✗ 异常: ' + (e.message || JSON.stringify(e)), 'error')
    ElMessage.error('执行失败')
  } finally {
    loading.value = false
  }
}

const onFailDemo = async () => {
  loading.value = true
  try {
    resetState()
    form.quantity = 99999
    addLog('开始调用 @Saga 注解方法 (使用大数量触发失败)', 'info')

    const apiCall = submitOrderFailDemo({ ...form })
    const animation = simulateExecution(true)
    const [res] = await Promise.all([apiCall, animation])

    result.value = { success: false, message: res.message || 'Saga 失败,已自动补偿' }
    addLog('最终状态: COMPENSATED', 'warn')
  } catch (e) {
    result.value = { success: false, message: e.message || JSON.stringify(e) }
  } finally {
    loading.value = false
  }
}

const goToList = () => router.push('/saga/list')
</script>

<style lang="scss" scoped>
.flow {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  align-items: stretch;
}

.flow-step {
  flex: 1 1 130px;
  background: rgba(79, 140, 255, 0.05);
  border: 1px solid #2a3358;
  border-radius: 8px;
  padding: 12px;
  text-align: center;
  position: relative;
  transition: all 0.3s;

  &.active {
    background: rgba(79, 140, 255, 0.2);
    border-color: #4f8cff;
    box-shadow: 0 0 12px rgba(79, 140, 255, 0.4);
  }
  &.done {
    background: rgba(95, 208, 164, 0.15);
    border-color: #5fd0a4;
  }
  &.failed {
    background: rgba(255, 107, 107, 0.15);
    border-color: #ff6b6b;
    animation: shake 0.4s;
  }

  .step-num {
    font-size: 11px;
    color: #99a4c2;
  }
  .step-name {
    font-size: 14px;
    font-weight: 700;
    color: #e7ecf6;
    margin: 4px 0;
  }
  .step-label {
    font-size: 11px;
    color: #99a4c2;
  }
  .step-tx {
    margin-top: 4px;
    font-size: 10px;
    color: #5fd0a4;
    background: rgba(95, 208, 164, 0.1);
    padding: 2px 4px;
    border-radius: 3px;
  }
  .step-arrow {
    position: absolute;
    right: -10px;
    top: 50%;
    transform: translateY(-50%);
    color: #5fd0a4;
    z-index: 10;
  }
}

@keyframes shake {
  0%, 100% { transform: translateX(0); }
  25% { transform: translateX(-4px); }
  75% { transform: translateX(4px); }
}

.compensate-flow {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.comp-step {
  flex: 1 1 130px;
  background: rgba(255, 180, 84, 0.05);
  border: 1px dashed #ffb454;
  border-radius: 8px;
  padding: 8px;
  text-align: center;
  position: relative;
  opacity: 0.4;

  &.will-comp {
    background: rgba(255, 107, 107, 0.1);
    border-color: #ff6b6b;
    border-style: solid;
    opacity: 1;
  }
  .comp-num {
    font-size: 11px;
    color: #99a4c2;
  }
  .comp-name {
    font-size: 12px;
    color: #ffb454;
    margin: 2px 0;
  }
  .comp-tx {
    font-size: 10px;
    color: #5fd0a4;
  }
  .comp-arrow {
    position: absolute;
    right: -10px;
    top: 50%;
    transform: translateY(-50%);
    color: #ffb454;
  }
}

.log-panel {
  background: #0a0a0a;
  color: #5fd0a4;
  padding: 12px;
  border-radius: 4px;
  font-family: "SF Mono", "Monaco", monospace;
  font-size: 11px;
  max-height: 200px;
  overflow-y: auto;
  line-height: 1.6;
}
</style>
