import { createRouter, createWebHistory } from 'vue-router'
import AppChatLayout from '@/layouts/AppChatLayout.vue'
import { useAuthStore } from '@/stores/auth'

const router = createRouter({
  history: createWebHistory(),
  routes: [
    {
      path: '/login',
      name: 'app-login',
      component: () => import('@/views/login/AppLoginView.vue'),
      meta: {
        public: true,
        title: '登录',
      },
    },
    {
      path: '/',
      component: AppChatLayout,
      meta: {
        title: 'ragAdmin Chat',
      },
      children: [
        {
          path: '',
          redirect: '/chat',
        },
        {
          path: 'chat',
          name: 'app-chat-home',
          component: () => import('@/views/chat/AppChatHomeView.vue'),
          meta: {
            title: '通用问答',
          },
        },
        {
          path: 'knowledge-bases/:kbId/chat',
          name: 'app-knowledge-base-chat',
          component: () => import('@/views/chat/AppKnowledgeBaseChatView.vue'),
          meta: {
            title: '知识库问答',
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
    return '/chat'
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
  const title = typeof to.meta.title === 'string' ? to.meta.title : 'ragAdmin Chat'
  document.title = `${title} | ragAdmin`
})

export default router
