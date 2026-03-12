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
          path: 'knowledge-bases',
          name: 'knowledge-bases',
          component: () => import('@/views/knowledge-base/KnowledgeBaseListView.vue'),
          meta: {
            title: '知识库管理',
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
