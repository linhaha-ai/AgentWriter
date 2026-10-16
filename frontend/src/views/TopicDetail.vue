<script setup lang="ts">
import { onMounted, ref, computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useDashboardStore } from '../stores/dashboard'
import { generateArticle } from '../api/articles'
import { selectTopic } from '../api/topics'
import { errorMessage } from '../api/client'
import type { Topic } from '../types'
import { formatDateTime } from '../utils/date'

const route = useRoute()
const router = useRouter()
const store = useDashboardStore()

const topic = ref<Topic | null>(null)
const loading = ref(true)
const generating = ref(false)
const lastError = ref('')
const lastSuccess = ref('')

const topicId = computed(() => Number(route.params.id))

onMounted(async () => {
  // 如果 store 里没有话题数据，先加载
  if (store.topics.length === 0) {
    await store.loadTopics()
  }
  // 查找对应的话题
  topic.value = store.topics.find(t => t.id === topicId.value) ?? null
  loading.value = false
})

function goBack() {
  router.push('/topics')
}

async function handleSelectTopic() {
  if (!topic.value) return
  lastError.value = ''
  lastSuccess.value = ''
  try {
    await selectTopic(topic.value.id)
    lastSuccess.value = '话题已选定'
  } catch (error: unknown) {
    lastError.value = errorMessage(error)
  }
}

async function handleGenerateArticle() {
  if (!topic.value) return
  generating.value = true
  lastError.value = ''
  lastSuccess.value = ''
  try {
    await generateArticle(topic.value.id)
    await store.loadArticles()
    lastSuccess.value = '文章已生成'
    router.push('/articles')
  } catch (error: unknown) {
    lastError.value = errorMessage(error)
  } finally {
    generating.value = false
  }
}

function scoreClass(score: number): string {
  if (score >= 90) return 'topic-detail-score--high'
  if (score >= 80) return 'topic-detail-score--medium'
  if (score >= 70) return 'topic-detail-score--low'
  return 'topic-detail-score--muted'
}

const sourceMap: Record<string, string> = {
  github: 'GitHub Trending',
  rss: 'RSS 订阅',
  openhot: 'OpenHot',
  manual: '手动添加',
}
</script>

<template>
  <div class="topic-detail-page">
    <!-- 返回按钮 -->
    <div class="topic-detail-header">
      <n-button quaternary @click="goBack">
        <template #icon>
          <n-icon><svg viewBox="0 0 24 24" width="16" height="16"><path fill="currentColor" d="M20 11H7.83l5.59-5.59L12 4l-8 8 8 8 1.41-1.41L7.83 13H20v-2z"/></svg></n-icon>
        </template>
        返回话题列表
      </n-button>
    </div>

    <!-- 加载中 -->
    <n-spin v-if="loading" size="large" class="topic-detail-loading" />

    <!-- 话题不存在 -->
    <n-empty v-else-if="!topic" description="话题不存在或已被删除" class="topic-detail-empty">
      <template #extra>
        <n-button size="small" @click="goBack">返回列表</n-button>
      </template>
    </n-empty>

    <!-- 话题详情 -->
    <template v-else>
      <n-alert v-if="lastError" title="操作失败" type="error" :show-icon="true" style="margin-bottom: 16px">
        {{ lastError }}
      </n-alert>
      <n-alert v-if="lastSuccess" title="操作成功" type="success" :show-icon="true" style="margin-bottom: 16px">
        {{ lastSuccess }}
      </n-alert>

      <n-card class="topic-detail-card">
        <!-- 顶部：评分 + 来源 -->
        <div class="topic-detail-top">
          <div class="topic-detail-score" :class="scoreClass(topic.score)">
            <span class="topic-detail-score-value">{{ topic.score }}</span>
            <span class="topic-detail-score-label">热度评分</span>
          </div>
          <n-tag size="medium" type="info">{{ sourceMap[topic.source] }}</n-tag>
        </div>

        <!-- 标题 -->
        <h1 class="topic-detail-title">{{ topic.title }}</h1>

        <!-- 信息列表 -->
        <div class="topic-detail-info">
          <div class="topic-detail-info-item">
            <span class="topic-detail-info-label">话题 ID</span>
            <span class="topic-detail-info-value">{{ topic.id }}</span>
          </div>
          <div class="topic-detail-info-item">
            <span class="topic-detail-info-label">来源</span>
            <span class="topic-detail-info-value">{{ sourceMap[topic.source] }}</span>
          </div>
          <div class="topic-detail-info-item">
            <span class="topic-detail-info-label">热度评分</span>
            <span class="topic-detail-info-value">{{ topic.score }}</span>
          </div>
          <div class="topic-detail-info-item">
            <span class="topic-detail-info-label">创建时间</span>
            <span class="topic-detail-info-value">{{ formatDateTime(topic.createdAt) }}</span>
          </div>
          <div v-if="topic.link" class="topic-detail-info-item">
            <span class="topic-detail-info-label">原文链接</span>
            <n-a :href="topic.link" target="_blank" class="topic-detail-info-value topic-detail-info-link">
              {{ topic.link }}
            </n-a>
          </div>
          <div v-if="topic.reason" class="topic-detail-info-item topic-detail-info-item--block">
            <span class="topic-detail-info-label">推荐理由</span>
            <span class="topic-detail-info-value topic-detail-info-reason">{{ topic.reason }}</span>
          </div>
        </div>

        <!-- 操作按钮 -->
        <div class="topic-detail-actions">
          <n-button
            type="primary"
            :loading="generating"
            :disabled="generating"
            @click="handleGenerateArticle"
          >
            <template #icon>
              <n-icon><svg viewBox="0 0 24 24" width="16" height="16"><path fill="currentColor" d="M19 13h-6v6h-2v-6H5v-2h6V5h2v6h6v2z"/></svg></n-icon>
            </template>
            生成文章
          </n-button>
          <n-button @click="handleSelectTopic">
            <template #icon>
              <n-icon><svg viewBox="0 0 24 24" width="16" height="16"><path fill="currentColor" d="M4 17.17V4h16v12H7.83L4 17.17zM4 2C2.9 2 2 2.9 2 4v18l4-4h14c1.1 0 2-.9 2-2V4c0-1.1-.9-2-2-2H4z"/></svg></n-icon>
            </template>
            选定话题
          </n-button>
        </div>
      </n-card>
    </template>
  </div>
</template>

<style scoped>
.topic-detail-page {
  max-width: 800px;
  margin: 0 auto;
  padding: 24px;
}

.topic-detail-header {
  margin-bottom: 24px;
}

.topic-detail-loading,
.topic-detail-empty {
  display: flex;
  justify-content: center;
  align-items: center;
  min-height: 300px;
}

.topic-detail-card {
  background: rgba(255, 255, 255, 0.05);
  border: 1px solid rgba(255, 255, 255, 0.1);
}

.topic-detail-top {
  display: flex;
  align-items: center;
  gap: 16px;
  margin-bottom: 20px;
}

.topic-detail-score {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  width: 64px;
  height: 64px;
  border-radius: 12px;
  font-weight: 700;
}

.topic-detail-score-value {
  font-size: 24px;
  line-height: 1;
}

.topic-detail-score-label {
  font-size: 10px;
  opacity: 0.8;
  margin-top: 2px;
}

.topic-detail-score--high {
  background: rgba(76, 175, 80, 0.15);
  color: #4caf50;
  border: 1px solid rgba(76, 175, 80, 0.3);
}

.topic-detail-score--medium {
  background: rgba(255, 152, 0, 0.15);
  color: #ff9800;
  border: 1px solid rgba(255, 152, 0, 0.3);
}

.topic-detail-score--low {
  background: rgba(33, 150, 243, 0.15);
  color: #2196f3;
  border: 1px solid rgba(33, 150, 243, 0.3);
}

.topic-detail-score--muted {
  background: rgba(158, 158, 158, 0.15);
  color: #9e9e9e;
  border: 1px solid rgba(158, 158, 158, 0.3);
}

.topic-detail-title {
  font-size: 24px;
  font-weight: 600;
  color: rgba(255, 255, 255, 0.9);
  margin: 0 0 24px 0;
  line-height: 1.4;
}

.topic-detail-info {
  display: flex;
  flex-direction: column;
  gap: 12px;
  margin-bottom: 24px;
  padding: 16px;
  background: rgba(255, 255, 255, 0.03);
  border-radius: 8px;
  border: 1px solid rgba(255, 255, 255, 0.06);
}

.topic-detail-info-item {
  display: flex;
  align-items: baseline;
  gap: 12px;
}

.topic-detail-info-label {
  min-width: 80px;
  color: rgba(255, 255, 255, 0.5);
  font-size: 13px;
  flex-shrink: 0;
}

.topic-detail-info-value {
  color: rgba(255, 255, 255, 0.8);
  font-size: 14px;
  word-break: break-all;
}

.topic-detail-info-link {
  color: #63e2b7;
  text-decoration: none;
}

.topic-detail-info-link:hover {
  text-decoration: underline;
}

.topic-detail-info-item--block {
  flex-direction: column;
  gap: 8px;
}

.topic-detail-info-reason {
  line-height: 1.6;
  color: rgba(255, 255, 255, 0.7);
  white-space: pre-wrap;
  word-break: break-word;
}

.topic-detail-actions {
  display: flex;
  gap: 12px;
  padding-top: 16px;
  border-top: 1px solid rgba(255, 255, 255, 0.06);
}
</style>
