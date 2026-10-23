<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { MdPreview } from 'md-editor-v3'
import type { Themes } from 'md-editor-v3'
import 'md-editor-v3/lib/style.css'
import { NAlert, NButton, NCard, NEmpty, NSpin, NTag, useMessage } from 'naive-ui'
import { useThemeStore } from '../stores/theme'
import type { Article, GenerationLogDetail } from '../types'
import { fetchArticle, fetchGenerationLogs } from '../api/articles'
import { errorMessage } from '../api/client'
import { formatDateTime } from '../utils/date'

const route = useRoute()
const router = useRouter()
const message = useMessage()
const themeStore = useThemeStore()

const articleId = computed(() => Number(route.params.id))
const previewTheme = computed<Themes>(() => (themeStore.isDark ? 'dark' : 'light'))

const article = ref<Article | null>(null)
const logs = ref<GenerationLogDetail[]>([])
const loading = ref(true)
const loadError = ref('')

/** 环节中文名称与说明 */
const stepMeta: Record<string, { label: string; description: string }> = {
  research: { label: '深度调研', description: '围绕文章主题检索资料、提炼要点，形成调研摘要' },
  outline: { label: '生成大纲', description: '基于调研结果规划文章结构，生成章节大纲' },
  writer: { label: '撰写正文', description: '按照大纲撰写完整文章正文（Markdown）' },
  review: { label: '内容审核', description: '审校技术准确性、逻辑完整性与表达质量' },
  seo: { label: 'SEO 优化', description: '优化标题、关键词与元描述，输出最终版本' },
  rag_store: { label: '知识入库', description: '将文章切块后写入 RAG 知识库' },
}

function stepLabel(step: string): string {
  return stepMeta[step]?.label ?? step
}

function stepDescription(step: string): string {
  return stepMeta[step]?.description ?? '执行生成环节'
}

function stepStatusType(status: string) {
  return ({ pending: 'default', running: 'info', success: 'success', succeeded: 'success', failed: 'error' } as const)[status] ?? 'default'
}

function stepStatusLabel(status: string) {
  return ({ pending: '等待执行', running: '执行中', success: '已完成', succeeded: '已完成', failed: '失败' } as const)[status] ?? status
}

/** 耗时格式化：小于 1 秒显示毫秒，否则显示 秒 */
function formatElapsed(ms: number | null): string {
  if (ms == null) return '—'
  if (ms < 1000) return `${ms}ms`
  return `${(ms / 1000).toFixed(1)}s`
}

const totalElapsed = computed(() => {
  const mainSteps = logs.value.filter(log => log.step !== 'rag_store')
  if (mainSteps.length === 0) return null
  return mainSteps.reduce((sum, log) => sum + (log.elapsedMs ?? 0), 0)
})

const failedCount = computed(() => logs.value.filter(log => log.status === 'failed').length)

onMounted(() => {
  void load()
})

async function load() {
  loading.value = true
  loadError.value = ''
  try {
    const [articleData, logData] = await Promise.all([
      fetchArticle(articleId.value),
      fetchGenerationLogs(articleId.value),
    ])
    article.value = articleData
    logs.value = logData
  } catch (error) {
    loadError.value = errorMessage(error)
  } finally {
    loading.value = false
  }
}

function goBack() {
  router.push('/articles')
}
</script>

<template>
  <NCard class="article-logs" :bordered="false" size="large">
    <template #header>
      <div class="article-logs__header">
        <div>
          <p class="article-logs__eyebrow">生成日志</p>
          <h1>{{ article?.title ?? '文章生成日志' }}</h1>
        </div>
        <div class="article-logs__actions">
          <NButton secondary size="small" @click="goBack">返回文章列表</NButton>
        </div>
      </div>
    </template>

    <NAlert v-if="loadError" type="error" :show-icon="true" class="article-logs__alert">
      <template #header>加载失败</template>
      {{ loadError }}
      <div class="article-logs__alert-actions">
        <NButton size="small" @click="load">重新加载</NButton>
      </div>
    </NAlert>

    <NSpin :show="loading">
      <template v-if="!loading && !loadError">
        <!-- 总览 -->
        <div class="article-logs__summary">
          <div class="article-logs__summary-item">
            <span class="article-logs__summary-label">文章状态</span>
            <NTag :type="article?.status === 'generated' ? 'success' : article?.status === 'failed' ? 'error' : 'info'">
              {{ article?.status ?? '—' }}
            </NTag>
          </div>
          <div class="article-logs__summary-item">
            <span class="article-logs__summary-label">环节数</span>
            <strong>{{ logs.filter(log => log.step !== 'rag_store').length }}</strong>
          </div>
          <div class="article-logs__summary-item">
            <span class="article-logs__summary-label">总耗时</span>
            <strong>{{ totalElapsed != null ? formatElapsed(totalElapsed) : '—' }}</strong>
          </div>
          <div v-if="failedCount > 0" class="article-logs__summary-item article-logs__summary-item--failed">
            <span class="article-logs__summary-label">失败环节</span>
            <strong>{{ failedCount }}</strong>
          </div>
        </div>

        <NEmpty
          v-if="logs.length === 0"
          description="暂无生成日志，文章可能尚未执行生成流程"
          class="article-logs__empty"
        >
          <template #extra>
            <NButton type="primary" size="small" @click="router.push('/articles/create-wizard')">去创建文章</NButton>
          </template>
        </NEmpty>

        <!-- 环节卡片：每个环节做了什么 + 产出什么 -->
        <ol v-else class="article-logs__cards">
          <li
            v-for="(log, index) in logs"
            :key="log.id"
            class="article-log-card"
            :class="log.status === 'failed' ? 'article-log-card--failed' : log.status === 'success' || log.status === 'succeeded' ? 'article-log-card--done' : ''"
          >
            <div class="article-log-card__head">
              <span class="article-log-card__badge">{{ index + 1 }}</span>
              <div class="article-log-card__title">
                <strong>{{ stepLabel(log.step) }}</strong>
                <span>{{ stepDescription(log.step) }}</span>
              </div>
              <div class="article-log-card__meta">
                <NTag size="small" :type="stepStatusType(log.status)">{{ stepStatusLabel(log.status) }}</NTag>
                <span class="article-log-card__elapsed">{{ formatElapsed(log.elapsedMs) }}</span>
                <span class="article-log-card__time">{{ log.createdAt ? formatDateTime(log.createdAt) : '' }}</span>
              </div>
            </div>

            <div class="article-log-card__body">
              <!-- 输入：本环节做了什么 -->
              <div v-if="log.input" class="article-log-card__section">
                <div class="article-log-card__section-title">输入（本环节基于什么）</div>
                <pre class="article-log-card__text">{{ log.input }}</pre>
              </div>

              <!-- 产出：本环节产出什么 -->
              <div v-if="log.output" class="article-log-card__section">
                <div class="article-log-card__section-title">产出</div>
                <MdPreview
                  :modelValue="log.output"
                  :theme="previewTheme"
                  language="zh-CN"
                  :autoFoldThreshold="999999"
                  class="article-log-card__md"
                />
              </div>

              <!-- 失败原因 -->
              <div v-if="log.status === 'failed' && log.errorMsg" class="article-log-card__section article-log-card__section--error">
                <div class="article-log-card__section-title">失败原因</div>
                <pre class="article-log-card__text article-log-card__text--error">{{ log.errorMsg }}</pre>
              </div>

              <p v-if="!log.input && !log.output && log.status !== 'failed'" class="article-log-card__hint">
                {{ log.status === 'pending' ? '等待执行' : log.status === 'running' ? '执行中…' : '该环节无内容产出' }}
              </p>
            </div>
          </li>
        </ol>
      </template>
    </NSpin>
  </NCard>
</template>

<style scoped>
.article-logs {
  border: 1px solid var(--aw-border);
  background: var(--aw-bg-shell);
  box-shadow: var(--aw-shadow);
}

.article-logs__header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
}

.article-logs__header h1 {
  margin: 0;
  color: var(--aw-text-primary);
  font-size: 22px;
  font-weight: 650;
}

.article-logs__eyebrow {
  margin: 0 0 6px;
  color: var(--aw-accent);
  font-size: 12px;
  font-weight: 600;
  letter-spacing: 0.08em;
  text-transform: uppercase;
}

.article-logs__actions {
  display: flex;
  align-items: center;
  gap: 8px;
}

.article-logs__alert {
  margin-bottom: 16px;
}

.article-logs__alert-actions {
  margin-top: 8px;
}

.article-logs__summary {
  display: flex;
  flex-wrap: wrap;
  gap: 24px;
  padding: 14px 18px;
  margin-bottom: 16px;
  border: 1px solid var(--aw-border);
  border-radius: 8px;
  background: var(--aw-bg-elevated);
}

.article-logs__summary-item {
  display: flex;
  align-items: center;
  gap: 8px;
}

.article-logs__summary-item strong {
  color: var(--aw-text-primary);
  font-size: 16px;
}

.article-logs__summary-label {
  color: var(--aw-text-secondary);
  font-size: 13px;
}

.article-logs__summary-item--failed strong {
  color: var(--aw-danger);
}

.article-logs__empty {
  padding: 40px 0;
}

.article-logs__cards {
  display: flex;
  flex-direction: column;
  gap: 16px;
  margin: 0;
  padding: 0;
  list-style: none;
}

.article-log-card {
  border: 1px solid var(--aw-border);
  border-radius: 8px;
  background: var(--aw-bg-elevated);
  overflow: hidden;
}

.article-log-card--done {
  border-color: color-mix(in srgb, var(--aw-success) 40%, var(--aw-border));
}

.article-log-card--failed {
  border-color: color-mix(in srgb, var(--aw-danger) 55%, var(--aw-border));
}

.article-log-card__head {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 12px 16px;
  border-bottom: 1px solid var(--aw-border);
  background: var(--aw-bg-muted);
}

.article-log-card__badge {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 26px;
  height: 26px;
  border-radius: 50%;
  background: var(--aw-accent);
  color: #fff;
  font-size: 13px;
  font-weight: 700;
  flex-shrink: 0;
}

.article-log-card__title {
  display: flex;
  flex-direction: column;
  gap: 2px;
  flex: 1;
  min-width: 0;
}

.article-log-card__title strong {
  color: var(--aw-text-primary);
  font-size: 14px;
  font-weight: 600;
}

.article-log-card__title span {
  color: var(--aw-text-secondary);
  font-size: 12px;
}

.article-log-card__meta {
  display: flex;
  align-items: center;
  gap: 12px;
  flex-shrink: 0;
}

.article-log-card__elapsed {
  color: var(--aw-accent);
  font-size: 13px;
  font-weight: 600;
}

.article-log-card__time {
  color: var(--aw-text-secondary);
  font-size: 12px;
}

.article-log-card__body {
  padding: 12px 16px;
  display: flex;
  flex-direction: column;
  gap: 14px;
}

.article-log-card__section-title {
  margin-bottom: 8px;
  color: var(--aw-text-secondary);
  font-size: 12px;
  font-weight: 600;
  letter-spacing: 0.04em;
}

.article-log-card__text {
  margin: 0;
  padding: 10px 12px;
  border-radius: 6px;
  background: var(--aw-bg-muted);
  color: var(--aw-text-secondary);
  font-family: "SF Mono", "Fira Code", "Consolas", monospace;
  font-size: 12px;
  line-height: 1.7;
  white-space: pre-wrap;
  word-break: break-word;
  max-height: 260px;
  overflow-y: auto;
}

.article-log-card__text--error {
  color: var(--aw-danger);
  background: color-mix(in srgb, var(--aw-danger) 8%, var(--aw-bg-muted));
}

.article-log-card__md {
  font-size: 13px;
  max-height: 520px;
  overflow-y: auto;
  padding-right: 4px;
}

.article-log-card__hint {
  margin: 0;
  color: var(--aw-text-secondary);
  font-size: 13px;
}

@media (max-width: 768px) {
  .article-log-card__meta {
    flex-wrap: wrap;
    gap: 6px;
  }
  .article-logs__summary {
    gap: 14px;
  }
}
</style>
