<script setup lang="ts">
import { computed, onMounted, onUnmounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import {
  AddOutline,
  CreateOutline,
  DocumentTextOutline,
  EyeOutline,
  RefreshOutline,
  SendOutline,
} from '@vicons/ionicons5'
import { useDashboardStore } from '../stores/dashboard'
import { getProgress, publishArticle } from '../api/articles'
import { errorMessage } from '../api/client'
import { formatDateTime } from '../utils/date'
import type { ArticleStatus, GenerationProgress } from '../types'
import AppPageHeader from '../components/AppPageHeader.vue'

type TagType = 'default' | 'error' | 'info' | 'success' | 'warning'

const router = useRouter()
const store = useDashboardStore()
const filterStatus = ref<string>('all')
const refreshing = ref(false)
const publishingId = ref<number | null>(null)
const lastError = ref('')
const lastSuccess = ref('')
const progressMap = ref<Record<number, GenerationProgress>>({})
let pollTimer: ReturnType<typeof setInterval> | null = null

const statusOptions = [
  { label: '全部状态', value: 'all' },
  { label: '草稿', value: 'draft' },
  { label: '生成中', value: 'generating' },
  { label: '撰写中', value: 'writing' },
  { label: '审核中', value: 'reviewing' },
  { label: '已生成', value: 'generated' },
  { label: '存草稿中', value: 'publishing' },
  { label: '失败', value: 'failed' },
]

const statusMap: Partial<Record<ArticleStatus, { label: string; type: TagType }>> = {
  draft: { label: '草稿', type: 'warning' },
  generating: { label: '生成中', type: 'info' },
  writing: { label: '撰写中', type: 'info' },
  reviewing: { label: '审核中', type: 'info' },
  generated: { label: '已生成', type: 'success' },
  publishing: { label: '存草稿中', type: 'info' },
  failed: { label: '失败', type: 'error' },
}

const filteredArticles = computed(() => filterStatus.value === 'all'
  ? store.articles
  : store.articles.filter(article => article.status === filterStatus.value))

const generatedCount = computed(() => store.articles.filter(article => article.status === 'generated').length)
const activeCount = computed(() => store.articles.filter(article => ['generating', 'writing', 'reviewing', 'publishing'].includes(article.status)).length)
const failedCount = computed(() => store.articles.filter(article => article.status === 'failed').length)

function sourceLabel(source: string) {
  if (source.startsWith('topic')) return '热点话题'
  return ({ github: 'GitHub', rss: 'RSS', openhot: 'OpenHot', manual: '手动' } as Record<string, string>)[source] || source
}

function articleStatus(articleStatus: string) {
  return statusMap[articleStatus as ArticleStatus] || { label: articleStatus, type: 'default' as TagType }
}

async function loadArticles(showSuccess = false) {
  refreshing.value = true
  lastError.value = ''
  try {
    await store.loadArticles()
    if (showSuccess) lastSuccess.value = '文章列表已刷新'
  } catch (error) {
    lastError.value = errorMessage(error)
  } finally {
    refreshing.value = false
  }
}

async function pollProgress() {
  const active = store.articles.filter(article => ['writing', 'generating', 'reviewing'].includes(article.status))
  for (const article of active) {
    try {
      const response = await getProgress(article.id)
      if (response.generationProgress) {
        progressMap.value = { ...progressMap.value, [article.id]: response.generationProgress }
      }
      if (response.status !== article.status) {
        store.articles = store.articles.map(item => item.id === article.id ? { ...item, status: response.status } : item)
      }
    } catch {
      // 轮询失败不影响编辑和发布操作。
    }
  }
}

async function handlePublish(articleId: number) {
  publishingId.value = articleId
  lastError.value = ''
  lastSuccess.value = ''
  try {
    await publishArticle(articleId)
    lastSuccess.value = '已保存到公众号草稿箱，请前往公众平台手动发布'
    await store.loadArticles()
  } catch (error) {
    lastError.value = errorMessage(error)
  } finally {
    publishingId.value = null
  }
}

onMounted(() => {
  void loadArticles()
  pollTimer = setInterval(() => void pollProgress(), 3000)
})

onUnmounted(() => {
  if (pollTimer) clearInterval(pollTimer)
})
</script>

<template>
  <section class="aw-page article-list-page">
    <AppPageHeader title="文章管理" description="查看生成进度、维护文章内容并保存公众号草稿。" eyebrow="内容资产">
      <template #actions>
        <n-button secondary :loading="refreshing" @click="loadArticles(true)">
          <template #icon><n-icon><RefreshOutline /></n-icon></template>
          刷新
        </n-button>
        <n-button type="primary" @click="router.push('/articles/create-wizard')">
          <template #icon><n-icon><AddOutline /></n-icon></template>
          创建文章
        </n-button>
      </template>
    </AppPageHeader>

    <n-alert v-if="lastError" type="error" title="操作失败" closable @close="lastError = ''">{{ lastError }}</n-alert>
    <n-alert v-if="lastSuccess" type="success" title="操作完成" closable @close="lastSuccess = ''">{{ lastSuccess }}</n-alert>

    <div class="article-summary-grid" aria-label="文章统计">
      <div class="article-summary aw-surface"><span>全部文章</span><strong>{{ store.articles.length }}</strong></div>
      <div class="article-summary aw-surface article-summary--active"><span>处理中</span><strong>{{ activeCount }}</strong></div>
      <div class="article-summary aw-surface article-summary--ready"><span>待发布</span><strong>{{ generatedCount }}</strong></div>
      <div class="article-summary aw-surface article-summary--failed"><span>需处理失败</span><strong>{{ failedCount }}</strong></div>
    </div>

    <section class="article-list-surface aw-surface">
      <div class="article-list-toolbar">
        <div>
          <p class="aw-section-label">文章队列</p>
          <strong>{{ filteredArticles.length }} 篇记录</strong>
        </div>
        <n-select v-model:value="filterStatus" :options="statusOptions" class="article-list-filter" />
      </div>

      <div v-if="filteredArticles.length" class="article-list-table-wrap">
        <n-table size="small" :single-line="false" class="article-list-table">
          <thead>
            <tr>
              <th>文章</th>
              <th class="article-list-col-status">状态与进度</th>
              <th>来源</th>
              <th class="article-list-col-time">更新时间</th>
              <th class="article-list-col-action">操作</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="article in filteredArticles" :key="article.id">
              <td class="article-list-title-cell">
                <strong :title="article.title">{{ article.title }}</strong>
                <span>#{{ article.id }} · 创建于 {{ formatDateTime(article.createdAt) }}</span>
              </td>
              <td class="article-list-status-cell">
                <n-tag :type="articleStatus(article.status).type" size="small">{{ articleStatus(article.status).label }}</n-tag>
                <div v-if="progressMap[article.id]" class="article-list-progress">
                  <n-progress :percentage="progressMap[article.id].progress" type="line" :show-indicator="false" :height="5" />
                  <span>{{ progressMap[article.id].currentStep }}</span>
                </div>
              </td>
              <td class="article-list-source-cell">
                <div v-if="article.topicTitle" class="article-list-source-topic">
                  <n-tag v-if="article.topicScore != null" size="tiny" type="warning">热度 {{ article.topicScore }}</n-tag>
                  <span class="article-list-source">{{ article.topicTitle }}</span>
                </div>
                <span v-else class="article-list-source">{{ sourceLabel(article.source) }}</span>
              </td>
              <td class="article-list-time-cell"><span class="article-list-time">{{ formatDateTime(article.updatedAt) }}</span></td>
              <td class="article-list-action-cell">
                <div class="article-list-actions">
                  <n-button size="small" quaternary @click="router.push(`/articles/${article.id}/edit`)">
                    <template #icon><n-icon><CreateOutline /></n-icon></template>
                    编辑
                  </n-button>
                  <n-button v-if="article.status === 'generated'" size="small" quaternary @click="router.push(`/articles/${article.id}/preview`)">
                    <template #icon><n-icon><EyeOutline /></n-icon></template>
                    预览
                  </n-button>
                  <n-button size="small" quaternary @click="router.push(`/articles/${article.id}/logs`)">
                    <template #icon><n-icon><DocumentTextOutline /></n-icon></template>
                    日志
                  </n-button>
                  <n-button
                    v-if="article.status === 'generated'"
                    size="small"
                    type="success"
                    secondary
                    :loading="publishingId === article.id"
                    :disabled="publishingId !== null"
                    @click="handlePublish(article.id)"
                  >
                    <template #icon><n-icon><SendOutline /></n-icon></template>
                    存草稿
                  </n-button>
                </div>
              </td>
            </tr>
          </tbody>
        </n-table>
      </div>
      <n-empty v-else description="暂无符合条件的文章">
        <template #extra><n-button type="primary" @click="router.push('/articles/create-wizard')">创建第一篇文章</n-button></template>
      </n-empty>
    </section>
  </section>
</template>

<style scoped>
.article-summary-grid {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: var(--aw-spacing-3);
}

.article-summary {
  display: flex;
  min-height: 92px;
  flex-direction: column;
  justify-content: space-between;
  padding: 16px;
}

.article-summary span,
.article-list-title-cell span,
.article-list-time,
.article-list-source,
.article-list-progress span {
  color: var(--aw-text-secondary);
  font-size: 12px;
}

.article-summary strong {
  font-size: 26px;
  font-weight: 650;
}

.article-summary--active strong { color: var(--aw-info); }
.article-summary--ready strong { color: var(--aw-accent); }
.article-summary--failed strong { color: var(--aw-danger); }

.article-list-surface {
  min-width: 0;
  padding: 18px;
}

.article-list-toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--aw-spacing-4);
  margin-bottom: 14px;
}

.article-list-toolbar strong {
  display: block;
  margin-top: 3px;
  font-size: 14px;
}

.article-list-filter { width: 168px; }
.article-list-table-wrap { overflow-x: auto; }
/* table-layout: fixed 下列宽由表头 th 决定，窄列宽度统一加在 th 上 */
.article-list-table { min-width: 980px; table-layout: fixed; width: 100%; }
.article-list-col-status { width: 150px; }
.article-list-col-time { width: 160px; }
.article-list-col-action { width: 340px; }

/* 文章/来源列完整换行显示，不再省略截断 */
.article-list-title-cell { min-width: 240px; word-break: break-word; }
.article-list-title-cell strong { display: block; white-space: normal; word-break: break-word; }
.article-list-title-cell span { display: block; margin-top: 5px; }
.article-list-source-cell { word-break: break-word; }
/* 行内流式布局：热度标签 + 标题同行，标题过长时在行内自然换行（flex 整项换行会把标题挤到第二行） */
.article-list-source-topic { display: block; line-height: 1.7; }
.article-list-source-topic :deep(.n-tag) { margin-right: 6px; vertical-align: middle; }
.article-list-source-topic .article-list-source { white-space: normal; word-break: break-word; }
.article-list-progress { display: grid; grid-template-columns: minmax(70px, 1fr) auto; align-items: center; gap: 7px; margin-top: 8px; }
.article-list-actions { display: flex; flex-wrap: wrap; gap: 4px; }

@media (max-width: 900px) {
  .article-summary-grid { grid-template-columns: repeat(2, minmax(0, 1fr)); }
}

@media (max-width: 560px) {
  .article-summary-grid { gap: var(--aw-spacing-2); }
  .article-summary { min-height: 82px; padding: 13px; }
  .article-summary strong { font-size: 22px; }
  .article-list-toolbar { align-items: stretch; flex-direction: column; }
  .article-list-filter { width: 100%; }
}
</style>
