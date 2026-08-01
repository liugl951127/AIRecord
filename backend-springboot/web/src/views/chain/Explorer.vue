<template>
  <div class="chain-explorer">
    <el-card shadow="hover">
      <template #header>
        <span>
          <el-icon><Connection /></el-icon>
          区块链浏览器
        </span>
        <div style="float:right">
          <el-button-group>
            <el-button @click="loadData" :icon="Refresh" size="small">刷新</el-button>
            <el-button @click="validateChain" :icon="CircleCheck" size="small" type="primary">验证链</el-button>
            <el-button @click="showAddDialog = true" :icon="Plus" size="small" type="success">新建交易</el-button>
          </el-button-group>
        </div>
      </template>

      <!-- 区块链统计 -->
      <div class="chain-stats">
        <div class="stat-card stat-primary">
          <div class="stat-num">{{ chain.length }}</div>
          <div class="stat-label">区块高度</div>
        </div>
        <div class="stat-card stat-success">
          <div class="stat-num">{{ totalTransactions }}</div>
          <div class="stat-label">交易总数</div>
        </div>
        <div class="stat-card stat-warning">
          <div class="stat-num">{{ pendingCount }}</div>
          <div class="stat-label">待打包</div>
        </div>
        <div class="stat-card stat-danger">
          <div class="stat-num">{{ isValid ? '有效' : '损坏' }}</div>
          <div class="stat-label">链状态</div>
        </div>
      </div>

      <!-- 区块列表 -->
      <el-table :data="chain" stripe border style="margin-top: 16px;" v-loading="loading">
        <el-table-column type="expand">
          <template #default="{ row }">
            <div class="block-detail">
              <h4>📦 区块 #{{ row.index }} 详情</h4>
              <el-descriptions :column="2" border>
                <el-descriptions-item label="区块哈希">{{ row.hash }}</el-descriptions-item>
                <el-descriptions-item label="前区块哈希">{{ row.previousHash }}</el-descriptions-item>
                <el-descriptions-item label="Merkle 根">{{ row.merkleRoot }}</el-descriptions-item>
                <el-descriptions-item label="时间戳">{{ row.timestamp }}</el-descriptions-item>
                <el-descriptions-item label="难度">{{ row.difficulty }}</el-descriptions-item>
                <el-descriptions-item label="Nonce (PoW)">{{ row.nonce }}</el-descriptions-item>
                <el-descriptions-item label="签名方">{{ row.signer || '-' }}</el-descriptions-item>
                <el-descriptions-item label="交易数">{{ row.transactions ? row.transactions.length : 0 }}</el-descriptions-item>
              </el-descriptions>
              <h4 style="margin-top: 12px">📝 交易列表</h4>
              <el-table :data="row.transactions || []" border size="small">
                <el-table-column prop="txId" label="交易ID" width="200" />
                <el-table-column prop="type" label="类型" width="180" />
                <el-table-column prop="from" label="From" width="120" />
                <el-table-column prop="to" label="To" width="120" />
                <el-table-column prop="hash" label="交易哈希" />
              </el-table>
            </div>
          </template>
        </el-table-column>
        <el-table-column prop="index" label="区块" width="80" />
        <el-table-column label="哈希" min-width="280">
          <template #default="{ row }">
            <code class="hash-code">{{ row.hash.substring(0, 32) }}...</code>
          </template>
        </el-table-column>
        <el-table-column prop="timestamp" label="时间" width="180" />
        <el-table-column label="交易数" width="100" align="center">
          <template #default="{ row }">
            <el-tag size="small">{{ (row.transactions || []).length }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="PoW" width="120" align="center">
          <template #default="{ row }">
            <span class="pow-info">{{ row.nonce }} 次</span>
          </template>
        </el-table-column>
        <el-table-column label="状态" width="100" align="center">
          <template #default="{ row }">
            <el-tag :type="validateBlock(row) ? 'success' : 'danger'" size="small">
              {{ validateBlock(row) ? '✓ 有效' : '✗ 失效' }}
            </el-tag>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <!-- 新建交易对话框 -->
    <el-dialog v-model="showAddDialog" title="新建交易" width="500px">
      <el-form :model="newTxForm" label-width="80px">
        <el-form-item label="类型">
          <el-select v-model="newTxForm.type">
            <el-option label="双录存证" value="DOUBLE_RECORDING_EVIDENCE" />
            <el-option label="视频哈希登记" value="VIDEO_HASH_REGISTER" />
            <el-option label="签名证书" value="SIGNATURE_CERT" />
            <el-option label="订单提交" value="ORDER_COMMIT" />
            <el-option label="质检报告" value="QUALITY_REPORT" />
            <el-option label="风险事件" value="RISK_EVENT" />
          </el-select>
        </el-form-item>
        <el-form-item label="会话ID">
          <el-input v-model="newTxForm.sessionId" placeholder="如: SESS_001" />
        </el-form-item>
        <el-form-item label="载荷">
          <el-input v-model="newTxForm.payload" type="textarea" :rows="3" placeholder='{"key":"value"}' />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showAddDialog = false">取消</el-button>
        <el-button type="primary" @click="submitTx">提交并挖矿</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, computed, onMounted, onUnmounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Connection, Refresh, CircleCheck, Plus } from '@element-plus/icons-vue'
import axios from '@/utils/request'

const chain = ref([])
const loading = ref(false)
const isValid = ref(true)
const pendingCount = ref(0)
const showAddDialog = ref(false)
const newTxForm = ref({
  type: 'DOUBLE_RECORDING_EVIDENCE',
  sessionId: '',
  payload: '{}'
})

const totalTransactions = computed(() =>
  chain.value.reduce((sum, b) => sum + (b.transactions?.length || 0), 0)
)

const loadData = async () => {
  loading.value = true
  try {
    const [chainRes, statRes] = await Promise.all([
      axios.get('/chain/list'),
      axios.get('/chain/statistics')
    ])
    chain.value = chainRes.data.data || []
    pendingCount.value = statRes.data.data?.pendingTransactions || 0
  } catch (e) {
    ElMessage.error('加载失败: ' + e.message)
  } finally {
    loading.value = false
  }
}

const validateChain = async () => {
  try {
    const res = await axios.post('/chain/validate')
    isValid.value = res.data.data?.valid || false
    if (isValid.value) {
      ElMessage.success('✅ 区块链完整性验证通过')
    } else {
      ElMessageBox.alert('链验证失败:\n' + (res.data.data?.errors?.join('\n') || ''), '错误', {
        type: 'error'
      })
    }
  } catch (e) {
    ElMessage.error('验证失败')
  }
}

const validateBlock = (block) => {
  if (!block.hash) return false
  // 简单验证:哈希以 0... 开头的个数 = 难度
  const zeros = block.hash.match(/^0*/)[0].length
  return zeros >= (block.difficulty || 0)
}

const submitTx = async () => {
  try {
    let payload = {}
    try {
      payload = JSON.parse(newTxForm.value.payload)
    } catch {
      payload = { data: newTxForm.value.payload }
    }
    payload.sessionId = newTxForm.value.sessionId
    await axios.post('/chain/add-transaction', {
      type: newTxForm.value.type,
      payload: payload
    })
    ElMessage.success('交易已上链')
    showAddDialog.value = false
    loadData()
  } catch (e) {
    ElMessage.error('提交失败: ' + (e.response?.data?.message || e.message))
  }
}

let timer = null
onMounted(() => {
  loadData()
  timer = setInterval(loadData, 5000)
})
onUnmounted(() => {
  if (timer) clearInterval(timer)
})
</script>

<style lang="scss" scoped>
.chain-explorer {
  padding: 16px;
}

.chain-stats {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 12px;
}

.stat-card {
  padding: 16px;
  border-radius: 8px;
  text-align: center;
  color: white;

  &.stat-primary { background: linear-gradient(135deg, #4f8cff, #1e3a8a); }
  &.stat-success { background: linear-gradient(135deg, #5fd0a4, #16a34a); }
  &.stat-warning { background: linear-gradient(135deg, #ffb454, #ea580c); }
  &.stat-danger { background: linear-gradient(135deg, #ff6b6b, #dc2626); }

  .stat-num { font-size: 24px; font-weight: 700; }
  .stat-label { font-size: 12px; opacity: 0.9; }
}

.hash-code {
  font-family: 'Consolas', 'Monaco', monospace;
  background: #f3f4f6;
  padding: 2px 6px;
  border-radius: 3px;
  font-size: 12px;
}

.pow-info {
  color: #16a34a;
  font-weight: 600;
}

.block-detail {
  padding: 0 16px;
  h4 { color: #1e3a8a; margin-bottom: 8px; }
}
</style>
