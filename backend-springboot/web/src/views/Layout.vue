<template>
  <el-container class="layout-container">
    <el-aside :width="collapsed ? '64px' : '220px'" class="layout-aside">
      <div class="logo">
        <span class="logo-icon">🎬</span>
        <transition name="fade">
          <span v-if="!collapsed" class="logo-text">AIRecord</span>
        </transition>
      </div>
      <el-menu
        :default-active="route.path"
        :collapse="collapsed"
        :collapse-transition="false"
        background-color="#0b1020"
        text-color="#99a4c2"
        active-text-color="#4f8cff"
        router
      >
        <el-menu-item
          v-for="item in menuItems"
          :key="item.path"
          :index="item.path"
        >
          <el-icon><component :is="item.icon" /></el-icon>
          <template #title>{{ item.title }}</template>
        </el-menu-item>
      </el-menu>
    </el-aside>

    <el-container>
      <el-header class="layout-header">
        <div class="header-left">
          <el-button
            text
            :icon="collapsed ? Expand : Fold"
            @click="collapsed = !collapsed"
            size="large"
          />
          <el-breadcrumb separator="/">
            <el-breadcrumb-item :to="{ path: '/' }">首页</el-breadcrumb-item>
            <el-breadcrumb-item>{{ route.meta?.title }}</el-breadcrumb-item>
          </el-breadcrumb>
        </div>
        <div class="header-right">
          <el-tag :type="session.sessionId ? 'success' : 'info'" effect="dark">
            <el-icon><Connection /></el-icon>
            {{ session.sessionId ? '会话进行中' : '未开始' }}
          </el-tag>
          <el-tooltip content="查看 API 文档" placement="bottom">
            <el-button text circle @click="showAbout = true">
              <el-icon><InfoFilled /></el-icon>
            </el-button>
          </el-tooltip>
        </div>
      </el-header>

      <el-main class="layout-main">
        <router-view v-slot="{ Component }">
          <transition name="fade-slide" mode="out-in">
            <component :is="Component" />
          </transition>
        </router-view>
      </el-main>
    </el-container>
  </el-container>

  <el-dialog v-model="showAbout" title="关于 AIRecord" width="500px">
    <div style="line-height:1.8">
      <p><b>线上线下双录融合系统</b></p>
      <p style="color:#99a4c2">版本:V1.0.0</p>
      <p style="color:#99a4c2">前端:Vue 3 + Vite + Element Plus + Pinia</p>
      <p style="color:#99a4c2">后端:Spring Boot 3.2.5 + JDK 17 + H2</p>
      <el-divider />
      <p>GitHub: <a href="https://github.com/liugl951127/AIRecord" target="_blank">liugl951127/AIRecord</a></p>
      <p style="color:#99a4c2">一套话术 · 一套流程 · 一套质检 · 一份证据</p>
    </div>
  </el-dialog>
</template>

<script setup>
import { ref, computed } from 'vue'
import { useRoute } from 'vue-router'
import { useSessionStore } from '@/stores/session'
import {
  Expand, Fold, InfoFilled, Connection
} from '@element-plus/icons-vue'

const route = useRoute()
const session = useSessionStore()
const collapsed = ref(false)
const showAbout = ref(false)

const menuItems = computed(() => [
  { path: '/dashboard', title: '仪表盘', icon: 'Odometer' },
  { path: '/session/create', title: '创建会话', icon: 'Plus' },
  { path: '/session/process', title: '话术引导', icon: 'ChatLineRound' },
  { path: '/session/quality', title: '质检中心', icon: 'CircleCheck' },
  { path: '/session/script', title: '话术模板', icon: 'Document' },
  { path: '/session/risk', title: '风险评估', icon: 'Warning' },
  { path: '/session/events', title: '事件追溯', icon: 'Connection' }
])
</script>

<style lang="scss" scoped>
.layout-container {
  height: 100vh;
}

.layout-aside {
  background: #0b1020;
  border-right: 1px solid #2a3358;
  transition: width 0.3s;
  overflow: hidden;
}

.logo {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 20px 16px;
  font-size: 18px;
  font-weight: 700;
  color: #4f8cff;
  border-bottom: 1px solid #2a3358;
}

.logo-icon { font-size: 24px; }
.logo-text {
  background: linear-gradient(135deg, #4f8cff 0%, #5fd0a4 100%);
  -webkit-background-clip: text;
  background-clip: text;
  color: transparent;
}

.layout-header {
  background: #11172e;
  border-bottom: 1px solid #2a3358;
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 0 20px;
}

.header-left {
  display: flex;
  align-items: center;
  gap: 20px;
}

.header-right {
  display: flex;
  align-items: center;
  gap: 12px;
}

.layout-main {
  background: #0b1020;
  padding: 20px;
  overflow-y: auto;
}

:deep(.el-menu) {
  border-right: none;
}

:deep(.el-menu-item) {
  &.is-active {
    background: linear-gradient(90deg, rgba(79, 140, 255, 0.15), transparent) !important;
    border-left: 3px solid #4f8cff;
  }
}

.fade-slide-enter-active, .fade-slide-leave-active {
  transition: all 0.3s;
}
.fade-slide-enter-from {
  opacity: 0;
  transform: translateY(10px);
}
.fade-slide-leave-to {
  opacity: 0;
  transform: translateY(-10px);
}

.fade-enter-active, .fade-leave-active {
  transition: opacity 0.2s;
}
.fade-enter-from, .fade-leave-to {
  opacity: 0;
}
</style>
