import { createRouter, createWebHashHistory } from 'vue-router'

const routes = [
  {
    path: '/',
    name: 'Layout',
    component: () => import('@/views/Layout.vue'),
    redirect: '/dashboard',
    children: [
      {
        path: 'dashboard',
        name: 'Dashboard',
        component: () => import('@/views/Dashboard.vue'),
        meta: { title: '仪表盘', icon: 'Odometer' }
      },
      {
        path: 'session/create',
        name: 'SessionCreate',
        component: () => import('@/views/session/Create.vue'),
        meta: { title: '创建会话', icon: 'Plus' }
      },
      {
        path: 'session/process',
        name: 'SessionProcess',
        component: () => import('@/views/session/Process.vue'),
        meta: { title: '话术引导', icon: 'ChatLineRound' }
      },
      {
        path: 'session/quality',
        name: 'QualityCheck',
        component: () => import('@/views/quality/Check.vue'),
        meta: { title: '质检中心', icon: 'CircleCheck' }
      },
      {
        path: 'session/script',
        name: 'ScriptManage',
        component: () => import('@/views/script/Manage.vue'),
        meta: { title: '话术模板', icon: 'Document' }
      },
      {
        path: 'session/risk',
        name: 'RiskManage',
        component: () => import('@/views/risk/Manage.vue'),
        meta: { title: '风险评估', icon: 'Warning' }
      },
      {
        path: 'session/events',
        name: 'EventTrace',
        component: () => import('@/views/event/Trace.vue'),
        meta: { title: '事件追溯', icon: 'Connection' }
      },
      {
        path: 'saga/dashboard',
        name: 'SagaDashboard',
        component: () => import('@/views/saga/Dashboard.vue'),
        meta: { title: 'Saga 监控', icon: 'DataLine' }
      },
      {
        path: 'saga/list',
        name: 'SagaList',
        component: () => import('@/views/saga/List.vue'),
        meta: { title: 'Saga 列表', icon: 'List' }
      },
      {
        path: 'saga/annotation-demo',
        name: 'SagaAnnotationDemo',
        component: () => import('@/views/saga/AnnotationDemo.vue'),
        meta: { title: 'Saga 注解演示', icon: 'MagicStick' }
      },
      {
        path: 'saga/detail/:sagaId',
        name: 'SagaDetail',
        component: () => import('@/views/saga/Detail.vue'),
        meta: { title: 'Saga 详情', icon: 'Document' },
        hidden: true
      }
    ]
  }
]

const router = createRouter({
  history: createWebHashHistory(),
  routes
})

router.beforeEach((to, from, next) => {
  document.title = `${to.meta?.title || '双录系统'} · AIRecord`
  next()
})

export default router
