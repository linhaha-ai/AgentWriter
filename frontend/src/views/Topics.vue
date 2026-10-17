<script setup lang="ts">
import { onMounted, ref, computed } from 'vue'
import { useDashboardStore } from '../stores/dashboard'
import { generateArticle } from '../api/articles'
import { errorMessage } from '../api/client'
import { selectTopic } from '../api/topics'
import type { Topic } from '../types'
import AppPageHeader from '../components/AppPageHeader.vue'
import { formatDateTime } from '../utils/date'

const store = useDashboardStore()
const refreshing = ref(false)
const generatingId = ref<number | null>(null)
const lastError = ref('')
const lastSuccess = ref('')

// 弹窗相关
const showModal = ref(false)
const selectedTopic = ref<Topic | null>(null)
const generatingInModal = ref(false)

onMounted(() => {
  store.loadTopics()
})

// 按天分组，最近的在前
const topicsByDay = computed(() => {
  const groups: Record<string, Topic[]> = {}
  for (const topic of store.topics) {
    const day = topic.createdAt.slice(0, 10) // 取 YYYY-MM-DD
    if (!groups[day]) groups[day] = []
    groups[day].push(topic)
  }
  // 按日期降序排列
  return Object.entries(groups)
    .sort(([a], [b]) => b.localeCompare(a))
    .map(([date, items]) => ({ date, items }))
})

async function handleRefresh() {
  refreshing.value = true
  lastError.value = ''
  lastSuccess.value = ''
  try {
    await store.collectTopics()
    lastSuccess.value = '话题已刷新'
  } catch (error: unknown) {
    lastError.value = errorMessage(error)
  } finally {
    refreshing.value = false
  }
}

async function handleSelectTopic(topicId: number) {
  lastError.value = ''
  lastSuccess.value = ''
  try {
    await selectTopic(topicId)
    lastSuccess.value = '话题已选定'
    store.loadTopics()
  } catch (error: unknown) {
    lastError.value = errorMessage(error)
  }
}

async function handleGenerateArticle(topicId: number) {
  generatingId.value = topicId
  lastError.value = ''
  lastSuccess.value = ''
  try {
    await generateArticle(topicId)
    await store.loadArticles()
    lastSuccess.value = '文章已生成'
  } catch (error: unknown) {
    lastError.value = errorMessage(error)
  } finally {
    generatingId.value = null
  }
}

function openDetail(topic: Topic) {
  selectedTopic.value = topic
  showModal.value = true
}

function closeModal() {
  showModal.value = false
  selectedTopic.value = null
}

async function handleGenerateInModal() {
  if (!selectedTopic.value) return
  generatingInModal.value = true
  lastError.value = ''
  lastSuccess.value = ''
  try {
    await generateArticle(selectedTopic.value.id)
    await store.loadArticles()
    lastSuccess.value = '文章已生成'
    closeModal()
  } catch (error: unknown) {
    lastError.value = errorMessage(error)
  } finally {
    generatingInModal.value = false
  }
}

async function handleSelectInModal() {
  if (!selectedTopic.value) return
  lastError.value = ''
  lastSuccess.value = ''
  try {
    await selectTopic(selectedTopic.value.id)
    lastSuccess.value = '话题已选定'
    store.loadTopics()
    closeModal()
  } catch (error: unknown) {
    lastError.value = errorMessage(error)
  }
}

function scoreClass(score: number): string {
  if (score >= 90) return 'topic-score--high'
  if (score >= 80) return 'topic-score--medium'
  if (score >= 70) return 'topic-score--low'
  return 'topic-score--muted'
}

const sourceMap: Record<string, string> = {
  github: 'GitHub',
  rss: 'RSS',
  openhot: 'OpenHot',
  manual: '手动',
}

const sourceMapDetail: Record<string, string> = {
  github: 'GitHub Trending',
  rss: 'RSS 订阅',
  openhot: 'OpenHot',
  manual: '手动添加',
}
</script>

<template>
  <section class="aw-page topics-page">
    <n-alert v-if="lastError" title="操作失败" type="error" :show-icon="true">
      {{ lastError }}
    </n-alert>
    <n-alert v-if="lastSuccess" title="操作完成" type="success" :show-icon="true">
      {{ lastSuccess }}
    </n-alert>
    <n-alert v-if="generatingId !== null" title="正在生成文章" type="info" :show-icon="true">
      文章正在生成，请勿重复提交。
    </n-alert>
    <AppPageHeader title="热点话题" description="从高优先级主题中选择下一篇内容的研究方向。" eyebrow="内容发现">
      <template #actions>
      <n-button size="small" type="primary" secondary :loading="refreshing" @click="handleRefresh">
        <template #icon>
          <n-icon><svg viewBox="0 0 24 24" width="16" height="16"><path fill="currentColor" d="M17.65 6.35A7.958 7.958 0 0012 4c-4.42 0-7.99 3.58-7.99 8s3.57 8 7.99 8c3.73 0 6.84-2.55 7.73-6h-2.08A5.99 5.99 0 0112 18c-3.31 0-6-2.69-6-6s2.69-6 6-6c1.66 0 3.14.69 4.22 1.78L13 11h7V4l-2.35 2.35z"/></svg></n-icon>
        </template>
        刷新
      </n-button>
      </template>
    </AppPageHeader>

    <div v-for="group in topicsByDay" :key="group.date" class="topics-day-group">
      <div class="topics-day-header">
        <span class="topics-day-label">{{ formatDateTime(group.date) }}</span>
        <span class="topics-day-count">{{ group.items.length }} 条</span>
      </div>
      <n-grid cols="1 m:2" responsive="screen" :x-gap="16" :y-gap="16">
        <n-grid-item v-for="topic in group.items" :key="topic.id">
          <n-card
            :bordered="true"
            size="small"
            class="topic-card"
          >
            <div class="topic-card-row">
              <!-- 热度评分 -->
              <div class="topic-score" :class="scoreClass(topic.score)">
                <span class="topic-score-value">{{ topic.score }}</span>
                <span class="topic-score-label">热度</span>
              </div>
              <!-- 话题内容 -->
              <div class="topic-content">
                <div class="topic-title topic-title--clickable" @click="openDetail(topic)">{{ topic.title.replace('如何', '如\u2060何') }}</div>
                <div class="topic-meta">
                  <n-tag size="tiny" type="info">{{ sourceMap[topic.source] }}</n-tag>
                  <span class="topic-created-at">{{ formatDateTime(topic.createdAt) }}</span>
                </div>
              </div>
              <!-- 话题操作 -->
              <div class="topic-actions">
                <n-button size="tiny" quaternary circle :disabled="generatingId !== null" @click="handleSelectTopic(topic.id)" title="选定话题">
                  <template #icon>
                    <n-icon><svg viewBox="0 0 24 24" width="14" height="14"><path fill="currentColor" d="M4 17.17V4h16v12H7.83L4 17.17zM4 2C2.9 2 2 2.9 2 4v18l4-4h14c1.1 0 2-.9 2-2V4c0-1.1-.9-2-2-2H4z"/></svg></n-icon>
                  </template>
                </n-button>
                <n-button
                  size="tiny"
                  quaternary
                  circle
                  :loading="generatingId === topic.id"
                  :disabled="generatingId !== null"
                  :title="generatingId === topic.id ? '正在生成文章' : '生成文章'"
                  @click="handleGenerateArticle(topic.id)"
                >
                  <template #icon>
                    <n-icon><svg viewBox="0 0 24 24" width="14" height="14"><path fill="currentColor" d="M19 13h-6v6h-2v-6H5v-2h6V5h2v6h6v2z"/></svg></n-icon>
                  </template>
                </n-button>
              </div>
            </div>
          </n-card>
        </n-grid-item>
      </n-grid>
    </div>

    <!-- 话题详情弹窗 -->
    <n-modal v-model:show="showModal" :mask-closable="true" preset="card" title="话题详情" style="max-width: 520px">
      <template v-if="selectedTopic">
        <div class="detail-modal">
          <div class="detail-top">
            <div class="detail-score" :class="scoreClass(selectedTopic.score)">
              <span class="detail-score-value">{{ selectedTopic.score }}</span>
              <span class="detail-score-label">热度</span>
            </div>
            <n-tag size="medium" type="info">{{ sourceMapDetail[selectedTopic.source] }}</n-tag>
          </div>

          <h3 class="detail-title">{{ selectedTopic.title }}</h3>

          <div class="detail-info">
            <div class="detail-info-row">
              <span class="detail-info-label">ID</span>
              <span class="detail-info-value">{{ selectedTopic.id }}</span>
            </div>
            <div class="detail-info-row">
              <span class="detail-info-label">来源</span>
              <span class="detail-info-value">{{ sourceMapDetail[selectedTopic.source] }}</span>
            </div>
            <div class="detail-info-row">
              <span class="detail-info-label">创建时间</span>
              <span class="detail-info-value">{{ formatDateTime(selectedTopic.createdAt) }}</span>
            </div>
            <div v-if="selectedTopic.link" class="detail-info-row">
              <span class="detail-info-label">原文链接</span>
              <n-a :href="selectedTopic.link" target="_blank" class="detail-info-link">{{ selectedTopic.link }}</n-a>
            </div>
            <div v-if="selectedTopic.reason" class="detail-info-row detail-info-row--block">
              <span class="detail-info-label">推荐理由</span>
              <span class="detail-info-reason">{{ selectedTopic.reason }}</span>
            </div>
          </div>
        </div>
      </template>

      <template #action>
        <n-space justify="end">
          <n-button @click="closeModal">关闭</n-button>
          <n-button :disabled="generatingInModal" @click="handleSelectInModal">选定话题</n-button>
          <n-button type="primary" :loading="generatingInModal" :disabled="generatingInModal" @click="handleGenerateInModal">生成文章</n-button>
        </n-space>
      </template>
    </n-modal>
  </section>
</template>

<style scoped>
.topics-day-group {
  margin-bottom: 24px;
}

.topics-day-header {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 12px;
  padding-bottom: 8px;
  border-bottom: 1px solid var(--aw-border);
}

.topics-day-label {
  font-size: 15px;
  font-weight: 600;
  color: var(--aw-text-primary);
}

.topics-day-count {
  font-size: 12px;
  color: var(--aw-text-secondary);
  background: var(--aw-bg-muted);
  padding: 2px 8px;
  border-radius: 10px;
}

.detail-modal {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.detail-top {
  display: flex;
  align-items: center;
  gap: 12px;
}

.detail-score {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  width: 56px;
  height: 56px;
  border-radius: 10px;
  font-weight: 700;
}

.detail-score-value {
  font-size: 22px;
  line-height: 1;
}

.detail-score-label {
  font-size: 10px;
  opacity: 0.8;
  margin-top: 2px;
}

.detail-score--high {
  background: rgba(24, 160, 88, 0.125);
  color: #18a058;
  border: 1px solid rgba(24, 160, 88, 0.3);
}

.detail-score--medium {
  background: rgba(32, 128, 240, 0.125);
  color: #2080f0;
  border: 1px solid rgba(32, 128, 240, 0.3);
}

.detail-score--low {
  background: rgba(240, 160, 32, 0.125);
  color: #f0a020;
  border: 1px solid rgba(240, 160, 32, 0.3);
}

.detail-score--muted {
  background: var(--aw-bg-muted);
  color: var(--aw-text-secondary);
}

.detail-title {
  margin: 0;
  font-size: 18px;
  font-weight: 600;
  line-height: 1.4;
}

.detail-info {
  display: flex;
  flex-direction: column;
  gap: 10px;
  padding: 12px;
  background: var(--aw-bg-elevated);
  border-radius: 8px;
  border: 1px solid var(--aw-border);
}

.detail-info-row {
  display: flex;
  align-items: baseline;
  gap: 10px;
}

.detail-info-row--block {
  flex-direction: column;
  gap: 6px;
}

.detail-info-label {
  min-width: 72px;
  color: var(--aw-text-secondary);
  font-size: 13px;
  flex-shrink: 0;
}

.detail-info-value {
  color: var(--aw-text-primary);
  font-size: 14px;
}

.detail-info-link {
  color: var(--aw-accent);
  text-decoration: none;
  font-size: 14px;
  word-break: break-all;
}

.detail-info-link:hover {
  text-decoration: underline;
}

.detail-info-reason {
  color: var(--aw-text-secondary);
  font-size: 14px;
  line-height: 1.6;
  white-space: pre-wrap;
  word-break: break-word;
}
</style>

<style src="../styles/views/topics.css"></style>
