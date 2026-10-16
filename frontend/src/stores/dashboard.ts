import { defineStore } from 'pinia'
import { ref } from 'vue'
import type {
  DashboardStats,
  AgentStatus,
  Activity,
  Article,
  Topic,
} from '../types'
import { fetchArticles } from '../api/articles'
import {
  fetchAgentStatuses,
  fetchDashboardStats,
  fetchRecentActivities,
} from '../api/dashboard'
import { collectTopics as apiCollectTopics, fetchTopics } from '../api/topics'

export const useDashboardStore = defineStore('dashboard', () => {
  const stats = ref<DashboardStats | null>(null)
  const agents = ref<AgentStatus[]>([])
  const activities = ref<Activity[]>([])
  const articles = ref<Article[]>([])
  const topics = ref<Topic[]>([])
  const loading = ref(false)

  async function loadStats() {
    const [s, a, act] = await Promise.all([
      fetchDashboardStats(),
      fetchAgentStatuses(),
      fetchRecentActivities(),
    ])
    stats.value = s
    agents.value = a
    activities.value = act
  }

  async function loadArticles() {
    articles.value = await fetchArticles()
  }

  async function loadTopics() {
    topics.value = await fetchTopics()
  }

  /** 手动触发话题采集，采集后自动重新加载话题列表 */
  async function collectTopics() {
    await apiCollectTopics()
    await loadTopics()
  }

  async function loadAll() {
    loading.value = true
    try {
      await Promise.all([loadStats(), loadArticles(), loadTopics()])
    } finally {
      loading.value = false
    }
  }

  return {
    stats,
    agents,
    activities,
    articles,
    topics,
    loading,
    loadStats,
    loadArticles,
    loadTopics,
    collectTopics,
    loadAll,
  }
})
