<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import {
  ArrowForwardOutline,
  ClipboardOutline,
  EyeOutline,
  RefreshOutline,
} from '@vicons/ionicons5'
import { NButton, NCard, NEmpty, NIcon, NSpin, NTag } from 'naive-ui'
import AppPageHeader from '../components/AppPageHeader.vue'
import type { GenerationLogSummary } from '../types'
import { fetchGenerationLogSummaries } from '../api/articles'
import { errorMessage } from '../api/client'
import { formatDateTime } from '../utils/date'

const router = useRouter()
const summaries = ref<GenerationLogSummary[]>([])
const loading = ref(true)
const loadError = ref('')

function articleStatusType(status: string) {
  return ({ generated: 'success', generating: 'info', failed: 'error', draft: 'default' } as const)[status] ?? 'default'
}

function articleStatusLabel(status: string) {
  return ({ generated: '已生成', generating: '生成中', failed: '生成失败', draft: '草稿' } as const)[status] ?? status
}

function formatElapsed(ms: number | null): string {
  if (ms == null) return '—'
  if (ms < 1000) return `${ms}ms`
  if (ms < 60000) return `${(ms / 1000).toFixed(1)}s`
  return `${Math.floor(ms / 60000)}m${Math.round((ms % 60000) / 1000)}s`
}

onMounted(() => {
  void load()
})

async function load() {
  loading.value = true
  loadError.value = ''
  try {
    summaries.value = await fetchGenerationLogSummaries()
  } catch (error) {
    loadError.value = errorMessage(error)
  } finally {
    loading.value = false
  }
}
</script>

<template>
  <section class="aw-page generation-logs-page">
    <AppPageHeader title="生成日志" description="查看每篇文章的生成环节执行记录与产出。" eyebrow="执行记录">
      <template #actions>
        <n-button secondary @click="load">
          <template #icon><n-icon><RefreshOutline /></n-icon></template>
          刷新
        </n-button>
        <n-button type="primary" @click="router.push('/articles/create-wizard')">
          <template #icon><n-icon><ClipboardOutline /></n-icon></template>
          创建文章
        </n-button>
      </template>
    </AppPageHeader>

    <n-alert v-if="loadError" type="error" title="加载失败" closable @close="loadError = ''">{{ loadError }}</n-alert>

    <n-spin :show="loading">
      <n-empty
        v-if="!loading && !loadError && summaries.length === 0"
        description="暂无生成日志，创建文章并执行生成后会在此展示"
        style="padding: 48px 0"
      />

      <div v-else-if="!loading && !loadError" class="generation-logs-list">
        <n-card v-for="item in summaries" :key="item.articleId" :bordered="true" class="generation-logs-card">
          <div class="generation-logs-card__head">
            <div class="generation-logs-card__title" :title="item.title" @click="router.push(`/articles/${item.articleId}/logs`)">
              {{ item.title }}
            </div>
            <div class="generation-logs-card__meta">
              <n-tag size="small" :type="articleStatusType(item.articleStatus)">{{ articleStatusLabel(item.articleStatus) }}</n-tag>
            </div>
          </div>

          <div class="generation-logs-card__stats">
            <div class="generation-logs-card__stat">
              <span class="generation-logs-card__stat-label">环节数</span>
              <strong>{{ item.stepCount }}</strong>
            </div>
            <div class="generation-logs-card__stat" :class="{ 'generation-logs-card__stat--failed': item.failedCount > 0 }">
              <span class="generation-logs-card__stat-label">失败环节</span>
              <strong>{{ item.failedCount }}</strong>
            </div>
            <div class="generation-logs-card__stat">
              <span class="generation-logs-card__stat-label">总耗时</span>
              <strong>{{ formatElapsed(item.totalElapsedMs) }}</strong>
            </div>
            <div class="generation-logs-card__stat generation-logs-card__stat--time">
              <span class="generation-logs-card__stat-label">最近执行</span>
              <strong>{{ item.lastRunAt ? formatDateTime(item.lastRunAt) : '—' }}</strong>
            </div>
          </div>

          <div class="generation-logs-card__actions">
            <n-button size="small" secondary type="primary" @click="router.push(`/articles/${item.articleId}/logs`)">
              <template #icon><n-icon><EyeOutline /></n-icon></template>
              查看详情
              <n-icon size="14" style="margin-left: 4px"><ArrowForwardOutline /></n-icon>
            </n-button>
          </div>
        </n-card>
      </div>
    </n-spin>
  </section>
</template>

<style scoped>
.generation-logs-list {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.generation-logs-card {
  border-color: var(--aw-border);
  background: var(--aw-bg-elevated);
  box-shadow: var(--aw-shadow);
}

.generation-logs-card__head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

.generation-logs-card__title {
  color: var(--aw-text-primary);
  font-size: 15px;
  font-weight: 600;
  cursor: pointer;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.generation-logs-card__title:hover {
  color: var(--aw-accent);
}

.generation-logs-card__stats {
  display: flex;
  flex-wrap: wrap;
  gap: 28px;
  margin-top: 14px;
}

.generation-logs-card__stat {
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.generation-logs-card__stat strong {
  color: var(--aw-text-primary);
  font-size: 16px;
}

.generation-logs-card__stat--failed strong {
  color: var(--aw-danger);
}

.generation-logs-card__stat--time strong {
  font-size: 13px;
  font-weight: 500;
  color: var(--aw-text-secondary);
}

.generation-logs-card__stat-label {
  color: var(--aw-text-secondary);
  font-size: 12px;
}

.generation-logs-card__actions {
  margin-top: 14px;
  padding-top: 12px;
  border-top: 1px solid var(--aw-border);
  display: flex;
  justify-content: flex-end;
}
</style>
