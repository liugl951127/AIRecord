<template>
  <div class="dashboard">
    <!-- 顶部统计卡片 -->
    <el-row :gutter="16" class="stat-row">
      <el-col :span="6">
        <el-card class="stat-card" shadow="hover">
          <div class="stat-content">
            <div class="stat-icon" style="background:linear-gradient(135deg,#4f8cff,#6ba0ff)">
              <el-icon size="24"><Document /></el-icon>
            </div>
            <div>
              <div class="stat-value">{{ stats.scripts }}</div>
              <div class="stat-label">话术模板</div>
            </div>
          </div>
        </el-card>
      </el-col>
      <el-col :span="6">
        <el-card class="stat-card" shadow="hover">
          <div class="stat-content">
            <div class="stat-icon" style="background:linear-gradient(135deg,#5fd0a4,#7ee0bc)">
              <el-icon size="24"><CircleCheck /></el-icon>
            </div>
            <div>
              <div class="stat-value">{{ stats.rules }}</div>
              <div class="stat-label">质检规则</div>
            </div>
          </div>
        </el-card>
      </el-col>
      <el-col :span="6">
        <el-card class="stat-card" shadow="hover">
          <div class="stat-content">
            <div class="stat-icon" style="background:linear-gradient(135deg,#ffb454,#ffc77a)">
              <el-icon size="24"><Warning /></el-icon>
            </div>
            <div>
              <div class="stat-value">{{ stats.questions }}</div>
              <div class="stat-label">风评问卷</div>
            </div>
          </div>
        </el-card>
      </el-col>
      <el-col :span="6">
        <el-card class="stat-card" shadow="hover">
          <div class="stat-content">
            <div class="stat-icon" style="background:linear-gradient(135deg,#ff6b6b,#ff8e8e)">
              <el-icon size="24"><Connection /></el-icon>
            </div>
            <div>
              <div class="stat-value">{{ stats.events }}</div>
              <div class="stat-label">事件记录</div>
            </div>
          </div>
        </el-card>
      </el-col>
    </el-row>

    <!-- 主功能区 -->
    <el-row :gutter="16" style="margin-top:20px">
      <el-col :span="16">
        <el-card shadow="hover">
          <template #header>
            <div class="card-header">
              <span><el-icon><Aim /></el-icon> 系统架构</span>
              <el-tag size="small" type="info">Vue 3 + Spring Boot 3.2.5</el-tag>
            </div>
          </template>
          <div class="architecture">
            <div class="arch-layer">
              <div class="layer-title">客户端层</div>
              <div class="layer-items">
                <el-tag effect="plain">App</el-tag>
                <el-tag effect="plain">H5</el-tag>
                <el-tag effect="plain">小程序</el-tag>
                <el-tag effect="plain">PAD</el-tag>
                <el-tag effect="plain">柜面</el-tag>
              </div>
            </div>
            <div class="arch-arrow">↓</div>
            <div class="arch-layer highlight">
              <div class="layer-title">Vue 3 前端</div>
              <div class="layer-items">
                <el-tag type="success" effect="dark">Element Plus</el-tag>
                <el-tag type="success" effect="dark">Pinia</el-tag>
                <el-tag type="success" effect="dark">Vue Router</el-tag>
                <el-tag type="success" effect="dark">ECharts</el-tag>
              </div>
            </div>
            <div class="arch-arrow">↓ API</div>
            <div class="arch-layer">
              <div class="layer-title">Spring Boot 后端</div>
              <div class="layer-items">
                <el-tag type="warning" effect="dark">编排引擎</el-tag>
                <el-tag type="warning" effect="dark">话术引擎</el-tag>
                <el-tag type="warning" effect="dark">质检引擎</el-tag>
                <el-tag type="warning" effect="dark">Saga</el-tag>
                <el-tag type="warning" effect="dark">事件溯源</el-tag>
              </div>
            </div>
            <div class="arch-arrow">↓</div>
            <div class="arch-layer">
              <div class="layer-title">数据层</div>
              <div class="layer-items">
                <el-tag type="info" effect="plain">H2 / MySQL</el-tag>
                <el-tag type="info" effect="plain">MinIO / OSS</el-tag>
                <el-tag type="info" effect="plain">Redis</el-tag>
                <el-tag type="info" effect="plain">区块链</el-tag>
              </div>
            </div>
          </div>
        </el-card>
      </el-col>

      <el-col :span="8">
        <el-card shadow="hover">
          <template #header>
            <span><el-icon><Operation /></el-icon> 快捷操作</span>
          </template>
          <div class="quick-actions">
            <el-button type="primary" size="large" @click="$router.push('/session/create')" style="width:100%;margin-bottom:12px">
              <el-icon><Plus /></el-icon> 新建双录会话
            </el-button>
            <el-button size="large" @click="$router.push('/session/process')" style="width:100%;margin-bottom:12px" :disabled="!session.sessionId">
              <el-icon><ChatLineRound /></el-icon> 进入话术引导
            </el-button>
            <el-button size="large" @click="$router.push('/session/quality')" style="width:100%;margin-bottom:12px">
              <el-icon><CircleCheck /></el-icon> 质检中心
            </el-button>
            <el-button size="large" @click="$router.push('/session/script')" style="width:100%">
              <el-icon><Document /></el-icon> 话术模板管理
            </el-button>
          </div>
        </el-card>
      </el-col>
    </el-row>

    <!-- 关键指标 -->
    <el-row :gutter="16" style="margin-top:20px">
      <el-col :span="24">
        <el-card shadow="hover">
          <template #header>
            <span><el-icon><DataLine /></el-icon> 系统指标</span>
          </template>
          <el-row :gutter="16">
            <el-col :span="6" v-for="m in metrics" :key="m.label">
              <div class="metric-box">
                <div class="metric-label">{{ m.label }}</div>
                <div class="metric-value gradient-text">{{ m.value }}</div>
                <div class="metric-desc">{{ m.desc }}</div>
              </div>
            </el-col>
          </el-row>
        </el-card>
      </el-col>
    </el-row>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { getScriptTemplates, getQualityRules, getRiskAssessment, getEvents } from '@/api/doubleRecording'
import { useSessionStore } from '@/stores/session'

const session = useSessionStore()

const stats = ref({ scripts: 0, rules: 0, questions: 0, events: 0 })

const metrics = [
  { label: '合规风险降低', value: '95%+', desc: '全量实时质检 + 区块链存证' },
  { label: '质检人力成本', value: '-70%', desc: 'AI 自动质检替代人工' },
  { label: '销售转化率', value: '+30%', desc: '断点续录减少客户流失' },
  { label: '一次性办理', value: '98%+', desc: '客户体验全面提升' }
]

const loadStats = async () => {
  try {
    const [tpls, rules, cust, events] = await Promise.all([
      getScriptTemplates(),
      getQualityRules(),
      getRiskAssessment('CUST_2026_0001').catch(() => ({ data: null })),
      session.sessionId ? getEvents(session.sessionId) : Promise.resolve({ data: [] })
    ])
    stats.value = {
      scripts: tpls.data?.length || 0,
      rules: rules.data?.length || 0,
      questions: cust.data ? 1 : 0,
      events: events.data?.length || 0
    }
  } catch (e) {
    console.error('加载统计失败', e)
  }
}

onMounted(() => {
  loadStats()
})
</script>

<style lang="scss" scoped>
.dashboard {
  .stat-row { margin-bottom: 4px; }
}

.stat-card {
  background: #161d36 !important;
  border: 1px solid #2a3358 !important;
}

.stat-content {
  display: flex;
  align-items: center;
  gap: 16px;
}

.stat-icon {
  width: 56px;
  height: 56px;
  border-radius: 12px;
  display: flex;
  align-items: center;
  justify-content: center;
  color: #fff;
}

.stat-value {
  font-size: 28px;
  font-weight: 700;
  color: #e7ecf6;
  line-height: 1.2;
}

.stat-label {
  font-size: 13px;
  color: #99a4c2;
  margin-top: 4px;
}

.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.architecture {
  .arch-layer {
    background: rgba(79, 140, 255, 0.05);
    border: 1px solid #2a3358;
    border-radius: 8px;
    padding: 16px;
    margin-bottom: 4px;

    &.highlight {
      background: linear-gradient(90deg, rgba(95, 208, 164, 0.1), rgba(79, 140, 255, 0.1));
      border-color: #5fd0a4;
    }
  }

  .layer-title {
    color: #4f8cff;
    font-weight: 600;
    margin-bottom: 12px;
    font-size: 14px;
  }

  .layer-items {
    display: flex;
    gap: 8px;
    flex-wrap: wrap;
  }

  .arch-arrow {
    text-align: center;
    color: #5fd0a4;
    font-size: 18px;
    margin: 4px 0;
  }
}

.quick-actions {
  display: flex;
  flex-direction: column;
}

.metric-box {
  text-align: center;
  padding: 16px;
  background: rgba(79, 140, 255, 0.05);
  border-radius: 8px;
  border: 1px solid #2a3358;
}

.metric-label {
  font-size: 13px;
  color: #99a4c2;
  margin-bottom: 8px;
}

.metric-value {
  font-size: 32px;
  font-weight: 800;
  line-height: 1.2;
}

.metric-desc {
  font-size: 11px;
  color: #99a4c2;
  margin-top: 8px;
}
</style>
