import { createRouter, createWebHistory } from 'vue-router'

const router = createRouter({
  history: createWebHistory(),
  routes: [
    {
      path: '/',
      redirect: '/dashboard',
    },
    {
      path: '/dashboard',
      name: 'Dashboard',
      component: () => import('../views/Dashboard.vue'),
      meta: { title: '运营概览', icon: 'dashboard' },
    },
    {
      path: '/articles',
      name: 'Articles',
      component: () => import('../views/ArticleList.vue'),
      meta: { title: '文章管理', icon: 'document' },
    },
    {
      path: '/articles/:id/edit',
      name: 'ArticleEditor',
      component: () => import('../views/ArticleEditor.vue'),
      meta: { title: '编辑文章', hidden: true },
    },
    {
      path: '/articles/:id/preview',
      name: 'ArticlePreview',
      component: () => import('../views/ArticlePreview.vue'),
      meta: { title: '文章预览', hidden: true },
    },
    {
      path: '/articles/create-wizard',
      name: 'CreateWizard',
      component: () => import('../views/CreateWizard.vue'),
      meta: { title: '创建文章', hidden: true },
    },
    {
      path: '/admin/templates',
      name: 'AdminTemplates',
      component: () => import('../views/AdminTemplates.vue'),
      meta: { title: '模板管理', icon: 'template' },
    },
    {
      path: '/admin/styles',
      name: 'AdminStyles',
      component: () => import('../views/AdminStyles.vue'),
      meta: { title: '风格管理', icon: 'style' },
    },
    {
      path: '/topics',
      name: 'Topics',
      component: () => import('../views/Topics.vue'),
      meta: { title: '热点话题', icon: 'trending' },
    },
    {
      path: '/gallery',
      name: 'Gallery',
      component: () => import('../views/Gallery.vue'),
      meta: { title: '资源图库', icon: 'image' },
    },
    {
      path: '/settings',
      redirect: '/settings/llm',
    },
    {
      path: '/settings/llm',
      name: 'SettingsLlm',
      component: () => import('../views/Settings.vue'),
      props: { section: 'llm' },
      meta: { title: '大模型配置', icon: 'settings' },
    },
    {
      path: '/settings/publisher',
      name: 'SettingsPublisher',
      component: () => import('../views/Settings.vue'),
      props: { section: 'publisher' },
      meta: { title: '发布配置', icon: 'settings' },
    },
    {
      path: '/settings/other',
      name: 'SettingsOther',
      component: () => import('../views/Settings.vue'),
      props: { section: 'other' },
      meta: { title: '其他配置', icon: 'settings' },
    },
  ],
})

export default router
