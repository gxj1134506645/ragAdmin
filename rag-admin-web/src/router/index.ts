import { createRouter, createWebHistory } from 'vue-router'
import AdminLayout from '@/layouts/AdminLayout.vue'
import { useAuthStore } from '@/stores/auth'

const router = createRouter({
  history: createWebHistory(),
  routes: [
    {
      path: '/login',
      name: 'login',
      component: () => import('@/views/login/LoginView.vue'),
      meta: {
        public: true,
        title: '登录',
      },
    },
    {
      path: '/',
      component: AdminLayout,
      meta: {
        title: '管理台',
      },
      children: [
        {
          path: '',
          redirect: '/dashboard',
        },
        {
          path: 'dashboard',
          name: 'dashboard',
          component: () => import('@/views/dashboard/DashboardView.vue'),
          meta: {
            title: '概览',
          },
        },
        {
          path: 'chat',
          name: 'general-chat',
          component: () => import('@/views/chat/GeneralChatView.vue'),
          meta: {
            title: '智能问答',
          },
        },
        {
          path: 'knowledge-bases',
          name: 'knowledge-bases',
          component: () => import('@/views/knowledge-base/KnowledgeBaseListView.vue'),
          meta: {
            title: '知识库管理',
          },
        },
        {
          path: 'models',
          name: 'models',
          component: () => import('@/views/model/ModelManagementView.vue'),
          meta: {
            title: '模型管理',
          },
        },
        {
          path: 'knowledge-bases/create',
          name: 'knowledge-base-create',
          component: () => import('@/views/knowledge-base/KnowledgeBaseCreateView.vue'),
          meta: {
            title: '新建知识库',
          },
        },
        {
          path: 'knowledge-bases/:id/edit',
          name: 'knowledge-base-edit',
          component: () => import('@/views/knowledge-base/KnowledgeBaseEditView.vue'),
          meta: {
            title: '编辑知识库',
          },
        },
        {
          path: 'knowledge-bases/:id',
          name: 'knowledge-base-detail',
          component: () => import('@/views/knowledge-base/KnowledgeBaseDetailView.vue'),
          meta: {
            title: '知识库详情',
          },
        },
        {
          path: 'documents/:id',
          name: 'document-detail',
          component: () => import('@/views/document/DocumentDetailView.vue'),
          meta: {
            title: '文档详情',
          },
        },
        {
          path: 'tasks',
          name: 'tasks',
          component: () => import('@/views/task/TaskMonitorView.vue'),
          meta: {
            title: '任务监控',
          },
        },
        {
          path: 'vector-indexes',
          name: 'vector-indexes',
          component: () => import('@/views/statistics/VectorIndexOverviewView.vue'),
          meta: {
            title: '向量索引',
          },
        },
        {
          path: 'tasks/:id',
          name: 'task-detail',
          component: () => import('@/views/task/TaskDetailView.vue'),
          meta: {
            title: '任务详情',
          },
        },
      ],
    },
  ],
})

router.beforeEach(async (to) => {
  const authStore = useAuthStore()
  const isPublic = Boolean(to.meta.public)

  if (!isPublic && !authStore.isAuthenticated) {
    return {
      path: '/login',
      query: {
        redirect: to.fullPath,
      },
    }
  }

  if (to.path === '/login' && authStore.isAuthenticated) {
    return '/dashboard'
  }

  if (!isPublic && !authStore.bootstrapFinished) {
    await authStore.hydrateCurrentUser()
    if (!authStore.isAuthenticated) {
      return {
        path: '/login',
        query: {
          redirect: to.fullPath,
        },
      }
    }
  }

  return true
})

router.afterEach((to) => {
  const title = typeof to.meta.title === 'string' ? to.meta.title : '管理台'
  document.title = `${title} | ragAdmin`
})

export default router
