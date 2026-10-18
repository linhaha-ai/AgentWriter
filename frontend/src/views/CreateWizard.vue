<script setup lang="ts">
import { computed, onMounted, onUnmounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { MdPreview } from 'md-editor-v3'
import type { Themes } from 'md-editor-v3'
import 'md-editor-v3/lib/style.css'
import { useThemeStore } from '../stores/theme'
import type { ArticleStatus, ArticleTemplate, GenerationEvent, GenerationLog, GenerationProgress, ProgressResponse, Topic, WriterStyle } from '../types'
import { fetchEnabledTemplates, fetchEnabledStyles, stepGenerate, subscribeGenerationEvents } from '../api/articles'
import { errorMessage } from '../api/client'
import { fetchTopics } from '../api/topics'
import SelectableCard from '../components/SelectableCard.vue'
import {
  NAlert,
  NButton,
  NCard,
  NEmpty,
  NForm,
  NFormItem,
  NInput,
  NProgress,
  NSpin,
  NStep,
  NSteps,
  NTag,
  useMessage,
} from 'naive-ui'

const router = useRouter()
const message = useMessage()
const themeStore = useThemeStore()

// Markdown 预览跟随亮/暗主题
const previewTheme = computed<Themes>(() => (themeStore.isDark ? 'dark' : 'light'))

const currentStep = ref(0)
const topicsLoading = ref(false)
const optionsLoading = ref(false)
const topicsError = ref('')
const submitting = ref(false)
const generationArticleId = ref<number | null>(null)
const generationStatus = ref<ArticleStatus | null>(null)
const generationProgress = ref<GenerationProgress | null>(null)
const generationLogs = ref<GenerationLog[]>([])
const connectionState = ref<'connecting' | 'connected' | 'reconnecting'>('connecting')
let closeGenerationEvents: (() => void) | null = null

const formData = ref({
  topicIds: [] as number[],
  title: '',
  templateId: null as number | null,
  styleId: null as number | null,
})

const topics = ref<Topic[]>([])
const templates = ref<ArticleTemplate[]>([])
const styles = ref<WriterStyle[]>([])

const selectedTopicCount = computed(() => formData.value.topicIds.length)
const isGenerating = computed(() => generationArticleId.value !== null)
const generationPercent = computed(() => generationProgress.value?.progress ?? 0)

// 已选话题对象列表（供已选区域展示与取消）
const selectedTopics = computed(() =>
  topics.value.filter(topic => formData.value.topicIds.includes(topic.id)),
)

// 话题来源：中文文案 + 标签配色（github 蓝 / rss 橙 / openhot 红 / manual 灰）
const sourceMap: Record<string, string> = {
  github: 'GitHub',
  rss: 'RSS',
  openhot: 'OpenHot',
  manual: '手动',
}
const sourceTagTypes: Record<string, 'info' | 'success' | 'warning' | 'error' | 'default'> = {
  github: 'info',
  rss: 'warning',
  openhot: 'error',
  manual: 'default',
}
function sourceLabel(source: string): string {
  return sourceMap[source] || source
}
function sourceTagType(source: string) {
  return sourceTagTypes[source] || 'default'
}

// 话题热度分级：≥90 高 / ≥80 中高 / ≥70 中 / 其余偏低
function scoreClass(score: number | null): string {
  const s = score ?? 0
  if (s >= 90) return 'topic-score--high'
  if (s >= 80) return 'topic-score--medium'
  if (s >= 70) return 'topic-score--low'
  return 'topic-score--muted'
}

const workflowSteps = [
  { key: 'research', label: '深度调研' },
  { key: 'outline', label: '生成大纲' },
  { key: 'writer', label: '撰写正文' },
  { key: 'review', label: '内容审核' },
  { key: 'seo', label: 'SEO 优化' },
] as const

const canContinue = computed(() => {
  if (currentStep.value === 0) return selectedTopicCount.value > 0
  if (currentStep.value === 1) return formData.value.title.trim().length > 0
  if (currentStep.value === 2) return true
  return formData.value.styleId !== null
})

onMounted(() => {
  void Promise.all([loadTopics(), loadOptions()])
})

onUnmounted(() => closeGenerationEvents?.())

async function loadTopics() {
  topicsLoading.value = true
  topicsError.value = ''
  try {
    topics.value = await fetchTopics()
  } catch (error) {
    topicsError.value = errorMessage(error)
  } finally {
    topicsLoading.value = false
  }
}

async function loadOptions() {
  optionsLoading.value = true
  try {
    const [loadedTemplates, loadedStyles] = await Promise.all([
      fetchEnabledTemplates(),
      fetchEnabledStyles(),
    ])
    templates.value = loadedTemplates
    styles.value = loadedStyles
  } catch (error) {
    message.error(errorMessage(error))
  } finally {
    optionsLoading.value = false
  }
}

function nextStep() {
  if (canContinue.value && currentStep.value < 3) currentStep.value++
}

function prevStep() {
  if (currentStep.value > 0) currentStep.value--
}

function toggleTopic(topicId: number) {
  const selectedIds = formData.value.topicIds
  const index = selectedIds.indexOf(topicId)
  if (index >= 0) {
    selectedIds.splice(index, 1)
    return
  }
  selectedIds.push(topicId)
}

async function handleSubmit() {
  if (formData.value.styleId === null) return

  submitting.value = true
  try {
    const result = await stepGenerate({
      topicIds: formData.value.topicIds,
      title: formData.value.title.trim(),
      templateId: formData.value.templateId ?? undefined,
      styleId: formData.value.styleId,
    })
    generationArticleId.value = result.id
    generationStatus.value = 'generating'
    generationProgress.value = null
    generationLogs.value = []
    connectionState.value = 'connecting'
    connectGenerationEvents(result.id)
  } catch (error) {
    message.error(errorMessage(error))
  } finally {
    submitting.value = false
  }
}

function connectGenerationEvents(articleId: number) {
  closeGenerationEvents?.()
  closeGenerationEvents = subscribeGenerationEvents(articleId, {
    onOpen: () => {
      connectionState.value = 'connected'
    },
    onSnapshot: applySnapshot,
    onProgress: applyGenerationEvent,
    onLog: applyGenerationEvent,
    onCompleted: event => {
      applyGenerationEvent(event)
      closeGenerationEvents?.()
      closeGenerationEvents = null
      void router.replace(`/articles/${articleId}/preview`)
    },
    onFailed: event => {
      applyGenerationEvent(event)
      closeGenerationEvents?.()
      closeGenerationEvents = null
    },
    onError: () => {
      if (generationStatus.value !== 'generated' && generationStatus.value !== 'failed') {
        connectionState.value = 'reconnecting'
      }
    },
  })
}

function applySnapshot(snapshot: ProgressResponse) {
  generationStatus.value = snapshot.status
  generationProgress.value = snapshot.generationProgress
  generationLogs.value = [...snapshot.logs]
  if (snapshot.status === 'generated') {
    closeGenerationEvents?.()
    closeGenerationEvents = null
    void router.replace(`/articles/${snapshot.articleId}/preview`)
  }
}

function applyGenerationEvent(event: GenerationEvent) {
  generationStatus.value = event.status
  generationProgress.value = event.generationProgress
  if (event.log) upsertGenerationLog(event.log)
}

function upsertGenerationLog(log: GenerationLog) {
  const index = generationLogs.value.findIndex(item => item.id === log.id)
  if (index < 0) {
    generationLogs.value = [...generationLogs.value, log]
    return
  }
  generationLogs.value = generationLogs.value.map(item => item.id === log.id ? log : item)
}

function workflowStepStatus(index: number): 'pending' | 'running' | 'success' | 'failed' {
  const progress = generationProgress.value
  if (!progress) return 'pending'
  if (index < progress.currentStepIndex) return 'success'
  if (index === progress.currentStepIndex && progress.currentStep !== 'done') return progress.stepStatus
  if (progress.currentStep === 'done') return 'success'
  return 'pending'
}

function stepStatusLabel(status: ReturnType<typeof workflowStepStatus>) {
  return ({ pending: '等待中', running: '进行中', success: '已完成', failed: '失败' } as const)[status]
}

function stepStatusType(status: ReturnType<typeof workflowStepStatus>) {
  return ({ pending: 'default', running: 'info', success: 'success', failed: 'error' } as const)[status]
}

// 各环节产出：按 step 聚合最新日志，供"环节产出"卡片展示
const logsByStep = computed(() => {
  const map = new Map<string, GenerationLog>()
  for (const log of generationLogs.value) {
    map.set(log.step, log)
  }
  return map
})

/**
 * 取某环节完成后的实际产出内容；只有成功状态且有内容时才展示。
 */
function outputOf(step: string): string | null {
  const log = logsByStep.value.get(step)
  const done = log?.status === 'success' || log?.status === 'succeeded'
  const output = log?.output?.trim()
  return done && output ? output : null
}

/** 环节卡片状态类：已完成高亮边框，进行中带脉冲，失败红边 */
function outputCardClass(step: string): string {
  const status = workflowStepStatus(workflowSteps.findIndex(item => item.key === step))
  if (status === 'success') return 'output-card--done'
  if (status === 'running') return 'output-card--running'
  if (status === 'failed') return 'output-card--failed'
  return ''
}
</script>

<template>
  <NCard class="create-wizard" :bordered="false" size="large">
    <template #header>
      <div class="create-wizard__header">
        <div>
          <p class="create-wizard__eyebrow">内容工作流</p>
          <h1>{{ isGenerating ? '正在创建文章' : '创建文章' }}</h1>
          <p>{{ isGenerating ? '系统将实时反馈生成进度和阶段日志。' : '依次选择话题、结构和写作风格，系统将自动生成完整文章。' }}</p>
        </div>
        <NTag :type="isGenerating ? 'info' : 'success'" size="small">{{ isGenerating ? '实时生成中' : `第 ${currentStep + 1} / 4 步` }}</NTag>
      </div>
    </template>
    <template v-if="!isGenerating">
    <div class="wizard-steps">
      <NSteps :current="currentStep + 1" :status="submitting ? 'process' : undefined">
          <NStep title="选择话题" description="一个或多个话题" />
        <NStep title="基本信息" description="文章标题" />
        <NStep title="选择模板" description="内容框架" />
        <NStep title="选择风格" description="写作语气" />
      </NSteps>
    </div>

    <section class="wizard-content">
      <!-- 步骤 0：选择话题 -->
      <div v-if="currentStep === 0">
        <div class="section-heading">
          <div>
            <h2>选择热门话题</h2>
            <p>可选择一个或多个话题，生成时会综合这些话题进行调研。</p>
          </div>
          <NTag type="success" round>已选择 {{ selectedTopicCount }} 个</NTag>
        </div>

        <NSpin :show="topicsLoading">
          <NAlert
            v-if="topicsError"
            type="error"
            title="热门话题加载失败"
            class="state-panel"
          >
            <p>{{ topicsError }}</p>
            <NButton size="small" @click="loadTopics">重新加载</NButton>
          </NAlert>

          <NEmpty
            v-else-if="!topicsLoading && topics.length === 0"
            description="暂无热门话题，请先采集话题"
            class="state-panel"
          >
            <template #extra>
              <NButton type="primary" @click="router.push('/topics')">前往热门话题</NButton>
            </template>
          </NEmpty>

          <div v-else class="selection-grid selection-grid--topics">
            <SelectableCard
              v-for="topic in topics"
              :key="topic.id"
              selection-type="checkbox"
              name="topics"
              :value="topic.id"
              :selected="formData.topicIds.includes(topic.id)"
              @select="toggleTopic(topic.id)"
            >
              <template #title>{{ topic.title }}</template>
              <div class="topic-meta">
                <NTag size="small" :type="sourceTagType(topic.source)" round>{{ sourceLabel(topic.source) }}</NTag>
                <span class="topic-score-badge" :class="scoreClass(topic.score)">热度 {{ topic.score }}</span>
              </div>
              <p v-if="topic.reason" class="card-description">{{ topic.reason }}</p>
            </SelectableCard>
          </div>

          <!-- 已选话题：话题列表下方展示，可点击取消 -->
          <div v-if="selectedTopics.length > 0" class="selected-topics-panel">
            <div class="selected-topics-title">已选话题（{{ selectedTopics.length }}）</div>
            <div class="selected-topics-list">
              <NTag
                v-for="topic in selectedTopics"
                :key="topic.id"
                size="small"
                closable
                @close="toggleTopic(topic.id)"
              >
                {{ topic.title }}
              </NTag>
            </div>
          </div>
        </NSpin>
      </div>

      <!-- 步骤 1：基本信息 -->
      <div v-if="currentStep === 1">
        <div class="section-heading">
          <div>
            <h2>填写文章标题</h2>
            <p>标题会作为最终文章标题，所选话题仅用于提供调研方向。</p>
          </div>
        </div>
        <NForm label-placement="top" class="title-form">
          <NFormItem label="文章标题" required>
            <NInput
              v-model:value="formData.title"
              placeholder="输入文章标题"
              clearable
              maxlength="200"
              show-count
            />
          </NFormItem>
        </NForm>

        <!-- 已选话题摘要：填写标题时可回顾已选话题 -->
        <div v-if="selectedTopics.length > 0" class="selected-topics-summary">
          <span class="selected-topics-summary-label">已选话题：</span>
          <NTag
            v-for="topic in selectedTopics"
            :key="topic.id"
            size="small"
            round
          >
            {{ topic.title }}
          </NTag>
        </div>
      </div>

      <!-- 步骤 2：选择模板 -->
      <div v-if="currentStep === 2">
        <div class="section-heading">
          <div>
            <h2>选择内容模板</h2>
            <p>模板为可选项，用于确定文章的内容框架。</p>
          </div>
        </div>
        <NSpin :show="optionsLoading">
          <div class="selection-grid" role="radiogroup" aria-label="内容模板">
            <SelectableCard
              v-for="template in templates"
              :key="template.id"
              selection-type="radio"
              name="article-template"
              :value="template.id"
              :selected="formData.templateId === template.id"
              @select="formData.templateId = template.id"
            >
              <template #title>{{ template.name }}</template>
              <p class="card-description">{{ template.description || '暂无描述' }}</p>
            </SelectableCard>
          </div>
          <NEmpty
            v-if="!optionsLoading && templates.length === 0"
            description="暂无可用模板，可跳过此步骤"
            class="state-panel"
          />
        </NSpin>
      </div>

      <!-- 步骤 3：选择风格 -->
      <div v-if="currentStep === 3">
        <div class="section-heading">
          <div>
            <h2>选择写作风格</h2>
            <p>请选择一种写作风格后开始生成文章。</p>
          </div>
        </div>
        <NSpin :show="optionsLoading">
          <div class="selection-grid" role="radiogroup" aria-label="写作风格">
            <SelectableCard
              v-for="style in styles"
              :key="style.id"
              selection-type="radio"
              name="writer-style"
              :value="style.id"
              :selected="formData.styleId === style.id"
              @select="formData.styleId = style.id"
            >
              <template #title>{{ style.name }}</template>
              <p class="card-description">{{ style.description || '暂无描述' }}</p>
              <p v-if="style.features" class="card-features">特点：{{ style.features }}</p>
            </SelectableCard>
          </div>
          <NEmpty
            v-if="!optionsLoading && styles.length === 0"
            description="暂无可用风格，请先在风格管理中启用"
            class="state-panel"
          />
        </NSpin>
      </div>
    </section>

    <div class="wizard-actions">
      <NButton :disabled="currentStep === 0 || submitting" quaternary @click="prevStep">
        上一步
      </NButton>
      <div class="wizard-actions__primary">
        <NButton
          v-if="currentStep < 3"
          type="primary"
          :disabled="!canContinue"
          @click="nextStep"
        >
          下一步
        </NButton>
        <NButton
          v-else
          type="primary"
          :loading="submitting"
          :disabled="!canContinue"
          @click="handleSubmit"
        >
          开始生成
        </NButton>
      </div>
    </div>
    </template>

    <section v-else class="generation-panel" aria-live="polite">
      <NAlert :type="generationStatus === 'failed' ? 'error' : 'info'" :show-icon="true" class="generation-panel__alert">
        <template #header>{{ generationStatus === 'failed' ? '文章生成失败' : connectionState === 'reconnecting' ? '连接正在恢复' : '文章正在生成' }}</template>
        {{ generationStatus === 'failed' ? '请根据失败阶段和日志调整配置后重新创建。' : connectionState === 'reconnecting' ? '正在重新建立实时连接，已收到的进度会保留。' : '生成完成后会自动打开文章预览。' }}
      </NAlert>

      <div class="generation-progress">
        <div class="generation-progress__heading">
          <strong>{{ generationProgress?.currentStep === 'done' ? '生成完成' : '当前进度' }}</strong>
          <span>{{ generationPercent }}%</span>
        </div>
        <NProgress type="line" :percentage="generationPercent" :show-indicator="false" :height="10" />
      </div>

      <ol class="generation-steps">
        <li v-for="(step, index) in workflowSteps" :key="step.key" class="generation-step">
          <span class="generation-step__index">{{ index + 1 }}</span>
          <strong>{{ step.label }}</strong>
          <NTag size="small" :type="stepStatusType(workflowStepStatus(index))">{{ stepStatusLabel(workflowStepStatus(index)) }}</NTag>
        </li>
      </ol>

      <section class="generation-outputs" aria-label="环节产出">
        <div class="generation-outputs__heading">
          <div><p class="create-wizard__eyebrow">环节产出</p><h2>各环节生成内容</h2></div>
          <span>{{ connectionState === 'connected' ? '实时连接已建立' : '等待连接' }}</span>
        </div>
        <NEmpty v-if="generationLogs.length === 0" description="正在等待第一个阶段开始" size="small" />

        <ol v-else class="output-cards">
          <li
            v-for="(step, index) in workflowSteps"
            :key="step.key"
            class="output-card"
            :class="outputCardClass(step.key)"
          >
            <div class="output-card__head">
              <span class="output-card__badge">{{ index + 1 }}</span>
              <strong>{{ step.label }}</strong>
              <NTag size="small" :type="stepStatusType(workflowStepStatus(index))">
                {{ stepStatusLabel(workflowStepStatus(index)) }}
              </NTag>
            </div>

            <div v-if="outputOf(step.key)" class="output-card__body">
              <!-- 调研：纯文本摘要；其余环节：Markdown 渲染 -->
              <pre v-if="step.key === 'research'" class="output-card__text">{{ outputOf(step.key) }}</pre>
              <MdPreview
                v-else
                :modelValue="outputOf(step.key)"
                :theme="previewTheme"
                language="zh-CN"
                class="output-card__md"
              />
            </div>
            <p v-else-if="workflowStepStatus(index) === 'running'" class="output-card__hint output-card__hint--running">
              正在生成中…
            </p>
            <p v-else class="output-card__hint">等待执行</p>
          </li>
        </ol>
      </section>

      <div class="generation-panel__actions">
        <NButton secondary @click="router.push('/articles')">查看文章列表</NButton>
      </div>
    </section>
  </NCard>
</template>

<style scoped>
.create-wizard {
  /* 选择卡片本地令牌：统一映射到全局主题变量，随亮/暗主题自动切换 */
  --aw-color-accent: var(--aw-accent);
  --aw-color-text-primary: var(--aw-text-primary);
  --aw-color-text-secondary: var(--aw-text-secondary);
  --aw-color-surface-hover: var(--aw-bg-muted);
  --aw-color-surface-selected: var(--aw-accent-subtle);
  --aw-color-border: var(--aw-border);
  --aw-color-accent-ring: var(--aw-accent-subtle);
  --aw-motion-fast: 180ms;
  border: 1px solid var(--aw-border);
  background: var(--aw-bg-shell);
  box-shadow: var(--aw-shadow);
}

.create-wizard__header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
}

.create-wizard__header h1 {
  margin: 0;
  color: var(--aw-text-primary);
  font-size: 22px;
  font-weight: 650;
}

.create-wizard__header p:not(.create-wizard__eyebrow) {
  margin: 7px 0 0;
  color: var(--aw-text-secondary);
  font-size: 13px;
}

.create-wizard__eyebrow {
  margin: 0 0 6px;
  color: var(--aw-accent);
  font-size: 12px;
  font-weight: 600;
}

.wizard-steps {
  min-width: 0;
  overflow-x: auto;
  padding-bottom: 4px;
}

.wizard-steps :deep(.n-steps) {
  min-width: 600px;
}

.wizard-content {
  min-height: 248px;
  margin-top: 32px;
}

.section-heading {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
  margin-bottom: 20px;
}

.section-heading h2 {
  margin: 0 0 6px;
  color: var(--aw-color-text-primary);
  font-size: 16px;
  font-weight: 600;
}

.section-heading p,
.state-panel p {
  margin: 0;
  color: var(--aw-color-text-secondary);
  font-size: 13px;
}

.selection-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 16px;
}

.selection-grid--topics {
  max-height: min(600px, 66vh);
  overflow-y: auto;
  padding: 2px 6px 2px 2px;
  scrollbar-gutter: stable;
}

.topic-meta,
.wizard-actions,
.wizard-actions__primary {
  display: flex;
  align-items: center;
}

.topic-meta {
  gap: 10px;
  color: var(--aw-color-text-secondary);
  font-size: 12px;
}

/* 热度分级徽章：胶囊底色 + 白字，区分度更高 */
.topic-score-badge {
  display: inline-flex;
  align-items: center;
  padding: 1px 10px;
  border-radius: 999px;
  font-size: 12px;
  font-weight: 600;
  line-height: 1.8;
  color: #fff;
}

.topic-score--high { background: #e64a4a; }
.topic-score--medium { background: #f08a2e; }
.topic-score--low { background: #4a7de0; }
.topic-score--muted { background: #9aa4b2; }

/* 已选话题面板（话题列表下方） */
.selected-topics-panel {
  margin-top: 14px;
  padding: 12px 14px;
  border-radius: 8px;
  background: var(--aw-color-bg-secondary, rgba(0, 0, 0, 0.03));
  border: 1px dashed var(--aw-color-border, rgba(0, 0, 0, 0.12));
}

.selected-topics-title {
  font-size: 13px;
  font-weight: 600;
  color: var(--aw-text-primary, #1a1a1a);
  margin-bottom: 8px;
}

.selected-topics-list {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

/* 已选话题摘要（基本信息步骤） */
.selected-topics-summary {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 8px;
  margin-top: 4px;
}

.selected-topics-summary-label {
  font-size: 13px;
  color: var(--aw-color-text-secondary);
}

.card-description,
.card-features {
  margin: 10px 0 0;
  color: var(--aw-color-text-secondary);
  font-size: 13px;
  line-height: 1.6;
}

.card-features {
  font-size: 12px;
}

.state-panel {
  padding: 32px;
  text-align: center;
}

.state-panel .n-button {
  margin-top: 16px;
}

.title-form {
  max-width: 640px;
}

.wizard-actions {
  justify-content: space-between;
  margin-top: 32px;
}

.wizard-actions__primary {
  gap: 12px;
}

.generation-panel {
  display: grid;
  gap: 24px;
  margin-top: 24px;
}

.generation-panel__alert { margin: 0; }

.generation-progress,
.generation-logs {
  padding: 18px;
  border: 1px solid var(--aw-border);
  border-radius: 6px;
  background: var(--aw-bg-elevated);
}

/* 环节产出卡片流 */
.generation-outputs {
  padding: 18px;
  border: 1px solid var(--aw-border);
  border-radius: 6px;
  background: var(--aw-bg-elevated);
}

.generation-outputs__heading {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  margin-bottom: 14px;
}

.generation-outputs__heading h2 {
  margin: 0;
  color: var(--aw-text-primary);
  font-size: 14px;
}

.generation-outputs__heading > span {
  color: var(--aw-text-secondary);
  font-size: 12px;
}

.output-cards {
  display: flex;
  flex-direction: column;
  gap: 14px;
  margin: 0;
  padding: 0;
  list-style: none;
}

.output-card {
  border: 1px solid var(--aw-border);
  border-radius: 8px;
  background: var(--aw-bg-shell);
  overflow: hidden;
  transition: border-color 180ms ease, box-shadow 180ms ease;
}

.output-card--done {
  border-color: color-mix(in srgb, var(--aw-success, #18a058) 45%, var(--aw-border));
}

.output-card--running {
  border-color: color-mix(in srgb, var(--aw-info, #2080f0) 55%, var(--aw-border));
  box-shadow: 0 0 0 3px color-mix(in srgb, var(--aw-info, #2080f0) 12%, transparent);
}

.output-card--failed {
  border-color: color-mix(in srgb, var(--aw-danger, #d03050) 55%, var(--aw-border));
}

.output-card__head {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 10px 14px;
  border-bottom: 1px solid var(--aw-border);
  background: var(--aw-bg-muted);
}

.output-card__badge {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 22px;
  height: 22px;
  border-radius: 50%;
  background: var(--aw-accent);
  color: #fff;
  font-size: 12px;
  font-weight: 700;
  flex-shrink: 0;
}

.output-card__head strong {
  color: var(--aw-text-primary);
  font-size: 14px;
  font-weight: 600;
}

.output-card__body {
  padding: 12px 14px;
  max-height: 420px;
  overflow-y: auto;
}

.output-card__text {
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
}

.output-card__md {
  font-size: 13px;
}

.output-card__hint {
  margin: 0;
  padding: 12px 14px;
  color: var(--aw-text-secondary);
  font-size: 13px;
}

.output-card__hint--running {
  color: var(--aw-info);
  animation: output-card-pulse 1.6s ease-in-out infinite;
}

@keyframes output-card-pulse {
  0%, 100% { opacity: 1; }
  50% { opacity: 0.45; }
}

.generation-progress__heading,
.generation-logs__heading,
.generation-step,
.generation-panel__actions {
  display: flex;
  align-items: center;
}

.generation-progress__heading,
.generation-logs__heading { justify-content: space-between; gap: 16px; margin-bottom: 12px; }
.generation-progress__heading strong,
.generation-logs__heading h2 { color: var(--aw-text-primary); font-size: 14px; }
.generation-progress__heading span,
.generation-logs__heading > span { color: var(--aw-text-secondary); font-size: 12px; }
.generation-logs__heading h2 { margin: 0; }

.generation-steps {
  display: grid;
  grid-template-columns: repeat(5, minmax(0, 1fr));
  gap: 10px;
  padding: 0;
  margin: 0;
  list-style: none;
}

.generation-step { min-width: 0; flex-wrap: wrap; gap: 7px; padding: 12px; border: 1px solid var(--aw-border); border-radius: 6px; background: var(--aw-bg-elevated); }
.generation-step__index { display: grid; width: 22px; height: 22px; place-items: center; border-radius: 50%; background: var(--aw-bg-shell); color: var(--aw-accent); font-size: 12px; font-weight: 700; }
.generation-step strong { overflow: hidden; color: var(--aw-text-primary); font-size: 13px; text-overflow: ellipsis; white-space: nowrap; }
.generation-panel__actions { justify-content: flex-end; }

@media (max-width: 767px) {
  .create-wizard__header {
    align-items: stretch;
    flex-direction: column;
  }

  .create-wizard__header .n-tag {
    align-self: flex-start;
  }

  .create-wizard > :deep(.n-card-header) {
    padding: 20px 20px 0;
  }

  .create-wizard > :deep(.n-card-content) {
    padding: 20px;
  }

  .wizard-content {
    margin-top: 24px;
  }

  .section-heading {
    align-items: stretch;
    flex-direction: column;
  }

  .section-heading .n-tag {
    align-self: flex-start;
  }

  .selection-grid {
    grid-template-columns: 1fr;
  }

  .generation-steps { grid-template-columns: 1fr; }

  .selection-grid--topics {
    max-height: min(440px, 56vh);
  }

  .state-panel {
    padding: 24px 12px;
  }
}

</style>
