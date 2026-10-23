<script setup lang="ts">
import { onMounted, onUnmounted, ref, computed } from 'vue'
import { useDashboardStore } from '../stores/dashboard'
import {
  createArticle,
  fetchArticleImages,
  fetchImageSuggestions,
  generateArticleImage,
  getProgress,
  insertArticleImage,
  publishArticle,
  updateArticle,
} from '../api/articles'
import { errorMessage } from '../api/client'
import { formatDateTime } from '../utils/date'
import type { Article, ArticleImage, ArticleStatus, GenerationProgress, ImageSuggestion } from '../types'
import { MdEditor, MdPreview } from 'md-editor-v3'
import type { Themes } from 'md-editor-v3'
import 'md-editor-v3/lib/style.css'

const store = useDashboardStore()
const filterStatus = ref<string>('all')
const creating = ref(false)
const refreshing = ref(false)
const publishingId = ref<number | null>(null)
const lastError = ref('')
const lastSuccess = ref('')

// 编辑弹窗状态
const showEditModal = ref(false)
const editingArticle = ref<Article | null>(null)
const editTitle = ref('')
const editContentMd = ref('')
const editCoverImage = ref('')
const saving = ref(false)

const imageSuggestions = ref<ImageSuggestion[]>([])
const articleImages = ref<ArticleImage[]>([])
const loadingImageSuggestions = ref(false)
const imageActionIds = ref<number[]>([])

// 微信预览弹窗
const showWechatPreview = ref(false)

// 编辑器主题跟随 Naive UI 暗黑模式
const editorTheme = computed<Themes>(() => 'dark')

const progressMap = ref<Record<number, GenerationProgress>>({})
let pollTimer: ReturnType<typeof setInterval> | null = null

type TagType = 'default' | 'error' | 'info' | 'success' | 'warning'

async function pollProgress() {
  const writing = store.articles.filter(a => a.status === 'writing')
  for (const article of writing) {
    try {
      const res = await getProgress(article.id)
      if (res.generationProgress) {
        progressMap.value = { ...progressMap.value, [article.id]: res.generationProgress }
      }
    } catch {
      // ignore poll errors
    }
  }
  await pollImages()
}

async function pollImages() {
  const articleId = editingArticle.value?.id
  if (!articleId || !articleImages.value.some(image => image.status === 'QUEUED' || image.status === 'GENERATING')) return
  try {
    await loadArticleImages(articleId)
  } catch {
    // 保持当前结果，下一轮轮询会重试。
  }
}

onMounted(() => {
  store.loadArticles()
  pollTimer = setInterval(pollProgress, 3000)
})

onUnmounted(() => {
  if (pollTimer) clearInterval(pollTimer)
})

const filtered = computed(() => {
  if (filterStatus.value === 'all') return store.articles
  return store.articles.filter(a => a.status === filterStatus.value)
})

const statusOptions = [
  { label: '全部', value: 'all' },
  { label: '草稿', value: 'draft' },
  { label: '撰写中', value: 'writing' },
  { label: '审核中', value: 'reviewing' },
  { label: '已生成', value: 'generated' },
  { label: '存草稿中', value: 'publishing' },
  { label: '失败', value: 'failed' },
]

const statusMap: Record<ArticleStatus, { label: string; type: TagType }> = {
  draft: { label: '草稿', type: 'warning' },
  generating: { label: '生成中', type: 'info' },
  writing: { label: '撰写中', type: 'info' },
  reviewing: { label: '审核中', type: 'info' },
  generated: { label: '已生成', type: 'success' },
  publishing: { label: '存草稿中', type: 'info' },
  failed: { label: '失败', type: 'error' },
}

const sourceMap: Record<string, { label: string; type: TagType }> = {
  github: { label: 'GitHub', type: 'info' },
  rss: { label: 'RSS', type: 'success' },
  openhot: { label: 'OpenHot', type: 'warning' },
  manual: { label: '手动', type: 'default' },
}

async function handleRefresh() {
  refreshing.value = true
  lastError.value = ''
  lastSuccess.value = ''
  try {
    await store.loadArticles()
    lastSuccess.value = '文章列表已刷新'
  } catch (error: unknown) {
    lastError.value = errorMessage(error)
  } finally {
    refreshing.value = false
  }
}

async function handleNewArticle() {
  creating.value = true
  lastError.value = ''
  lastSuccess.value = ''
  try {
    await createArticle({ title: '未命名文章', source: 'manual' })
    lastSuccess.value = '文章已创建'
    await store.loadArticles()
  } catch (error: unknown) {
    lastError.value = errorMessage(error)
  } finally {
    creating.value = false
  }
}

async function handlePublishArticle(id: number) {
  publishingId.value = id
  lastError.value = ''
  lastSuccess.value = ''
  try {
    await publishArticle(id)
    lastSuccess.value = '已保存到公众号草稿箱，请前往公众平台手动发布'
    await store.loadArticles()
  } catch (error: unknown) {
    lastError.value = errorMessage(error)
  } finally {
    publishingId.value = null
  }
}

function handleEditArticle(articleId: number) {
  const article = store.articles.find(a => a.id === articleId)
  if (!article) return
  editingArticle.value = article
  editTitle.value = article.title
  editContentMd.value = article.contentMd || ''
  editCoverImage.value = article.coverImage || ''
  imageSuggestions.value = []
  articleImages.value = []
  showEditModal.value = true
  void loadArticleImages(article.id).catch((error: unknown) => {
    lastError.value = errorMessage(error)
  })
}

async function loadArticleImages(articleId: number) {
  const images = await fetchArticleImages(articleId)
  articleImages.value = images
  const latestCover = [...images].reverse().find(image => image.role === 'COVER' && image.status === 'READY' && image.contentUrl)
  if (latestCover?.contentUrl) editCoverImage.value = latestCover.contentUrl
}

async function handleImageSuggestions() {
  if (!editingArticle.value) return
  loadingImageSuggestions.value = true
  lastError.value = ''
  try {
    imageSuggestions.value = await fetchImageSuggestions(editingArticle.value.id)
  } catch (error: unknown) {
    lastError.value = errorMessage(error)
  } finally {
    loadingImageSuggestions.value = false
  }
}

function imageFor(suggestion: ImageSuggestion): ArticleImage | undefined {
  return [...articleImages.value].reverse().find(image =>
    image.role === suggestion.role && image.headingAnchor === suggestion.headingAnchor,
  )
}

function isImageActioning(imageId: number): boolean {
  return imageActionIds.value.includes(imageId)
}

async function handleGenerateImage(suggestion: ImageSuggestion) {
  if (!editingArticle.value) return
  lastError.value = ''
  try {
    const image = await generateArticleImage(editingArticle.value.id, suggestion)
    articleImages.value = [...articleImages.value, image]
    lastSuccess.value = '图片任务已提交'
  } catch (error: unknown) {
    lastError.value = errorMessage(error)
  }
}

async function handleInsertImage(image: ArticleImage) {
  if (!editingArticle.value) return
  imageActionIds.value = [...imageActionIds.value, image.id]
  lastError.value = ''
  try {
    const article = await insertArticleImage(editingArticle.value.id, image.id)
    editingArticle.value = article
    editContentMd.value = article.contentMd || ''
    articleImages.value = articleImages.value.map(item => item.id === image.id ? { ...item, inserted: true } : item)
    lastSuccess.value = '插图已插入正文'
    await store.loadArticles()
  } catch (error: unknown) {
    lastError.value = errorMessage(error)
  } finally {
    imageActionIds.value = imageActionIds.value.filter(id => id !== image.id)
  }
}

async function handleSaveEdit() {
  if (!editingArticle.value) return
  saving.value = true
  lastError.value = ''
  lastSuccess.value = ''
  try {
    const id = editingArticle.value.id
    await updateArticle(id, {
      title: editTitle.value,
      contentMd: editContentMd.value,
      coverImage: editCoverImage.value || undefined,
    })
    lastSuccess.value = '文章已更新'
    showEditModal.value = false
    await store.loadArticles()
  } catch (error: unknown) {
    lastError.value = errorMessage(error)
  } finally {
    saving.value = false
  }
}
</script>

<template>
  <div class="articles-page">
    <n-alert v-if="lastError" title="操作失败" type="error" :show-icon="true">
      {{ lastError }}
    </n-alert>
    <n-alert v-if="lastSuccess" title="操作完成" type="success" :show-icon="true">
      {{ lastSuccess }}
    </n-alert>
    <n-alert v-if="publishingId !== null" title="正在保存草稿" type="info" :show-icon="true">
      正在保存到公众号草稿箱，请勿重复提交。
    </n-alert>
    <!-- 页面标题与操作区 -->
    <div class="articles-page-header">
      <h2 class="articles-page-title">文章管理</h2>
      <div class="articles-action-cluster">
        <n-select
          v-model:value="filterStatus"
          :options="statusOptions"
          size="small"
          class="articles-status-filter"
        />
        <n-button class="articles-refresh-button" size="small" quaternary title="刷新文章列表" aria-label="刷新文章列表" :loading="refreshing" @click="handleRefresh">
          <template #icon>
            <n-icon><svg viewBox="0 0 24 24" width="16" height="16"><path fill="currentColor" d="M17.65 6.35A7.958 7.958 0 0012 4c-4.42 0-7.99 3.58-7.99 8s3.57 8 7.99 8c3.73 0 6.84-2.55 7.73-6h-2.08A5.99 5.99 0 0112 18c-3.31 0-6-2.69-6-6s2.69-6 6-6c1.66 0 3.14.69 4.22 1.78L13 11h7V4l-2.35 2.35z"/></svg></n-icon>
          </template>
        </n-button>
        <n-button v-if="false" type="primary" size="small" :loading="creating" @click="handleNewArticle">
          <template #icon>
            <n-icon><svg viewBox="0 0 24 24" width="16" height="16"><path fill="currentColor" d="M19 13h-6v6h-2v-6H5v-2h6V5h2v6h6v2z"/></svg></n-icon>
          </template>
          新建文章
        </n-button>
      </div>
    </div>

    <!-- 文章表格 -->
    <n-card class="articles-table-card" :bordered="true" size="small">
      <n-table :single-line="false" size="small">
        <thead>
          <tr>
            <th>ID</th>
            <th>标题</th>
            <th class="articles-column-status">状态</th>
            <th class="articles-column-progress">进度</th>
            <th class="articles-column-source">来源</th>
            <th class="articles-column-created">创建时间</th>
            <th class="articles-column-updated">更新时间</th>
            <th class="articles-column-actions">操作</th>
          </tr>
        </thead>
        <tbody>
          <tr v-if="filtered.length === 0">
            <td colspan="8" class="articles-empty">暂无数据</td>
          </tr>
          <tr v-for="article in filtered" :key="article.id">
            <td class="article-id-cell">{{ article.id }}</td>
            <td class="article-title-cell">{{ article.title }}</td>
            <td>
              <n-tag :type="statusMap[article.status].type" size="tiny">
                {{ statusMap[article.status]?.label || article.status }}
              </n-tag>
            </td>
            <td class="articles-progress-cell">
              <template v-if="article.status === 'generating' && progressMap[article.id]">
                <n-progress
                  type="line"
                  :percentage="progressMap[article.id].progress"
                  :indicator-placement="'inside'"
                  :height="14"
                  size="small"
                />
                <span class="articles-step-label">
                  {{ progressMap[article.id].currentStep }}
                  ({{ progressMap[article.id].currentStepIndex }}/{{ progressMap[article.id].totalSteps }})
                </span>
              </template>
              <span v-else class="articles-no-progress">-</span>
            </td>
            <td>
              <n-tag :type="sourceMap[article.source]?.type ?? 'default'" size="tiny">
                {{ sourceMap[article.source]?.label || article.source }}
              </n-tag>
            </td>
            <td class="article-time-cell">{{ formatDateTime(article.createdAt) }}</td>
            <td class="article-time-cell">{{ formatDateTime(article.updatedAt) }}</td>
            <td class="article-actions">
              <n-button size="tiny" quaternary @click="handleEditArticle(article.id)">编辑</n-button>
              <n-button
                v-if="article.status === 'generated'"
                size="tiny"
                type="success"
                secondary
                :loading="publishingId === article.id"
                :disabled="publishingId !== null"
                :title="publishingId === article.id ? '正在保存草稿' : '保存草稿'"
                @click="handlePublishArticle(article.id)"
              >保存草稿</n-button>
            </td>
          </tr>
        </tbody>
      </n-table>
    </n-card>
  </div>

  <!-- 编辑文章弹窗 -->
  <n-modal v-model:show="showEditModal" :mask-closable="false" preset="card" class="articles-edit-modal" title="编辑文章" :bordered="true" size="small">
    <template #header>
      <span class="articles-modal-title">编辑文章</span>
    </template>
    <div class="articles-edit-form">
      <div>
        <label class="articles-field-label">标题</label>
        <n-input v-model:value="editTitle" placeholder="输入文章标题" />
      </div>
      <div class="articles-md-editor-wrapper">
        <label class="articles-field-label">正文 (Markdown)</label>
        <MdEditor
          v-model="editContentMd"
          :theme="editorTheme"
          :toolbars="['bold', 'italic', 'title', 'strikeThrough', 'quote', 'code', 'codeRow', 'link', 'image', 'unorderedList', 'orderedList', 'table', 0, 'preview', 'previewOnly', 'fullscreen', 'github']"
          language="zh-CN"
          style="min-height: 400px"
        />
      </div>
      <div>
        <label class="articles-field-label">封面图片 URL</label>
        <n-input v-model:value="editCoverImage" placeholder="输入封面图片 URL" size="small" />
        <img v-if="editCoverImage" :src="editCoverImage" class="articles-cover-preview" alt="封面预览" />
      </div>
      <section class="article-images-section" aria-label="AI 图片生成">
        <div class="article-images-heading">
          <div>
            <label class="articles-field-label">AI 图片</label>
            <p class="article-images-hint">基于已保存的正文生成封面和前两个章节的插图建议。</p>
          </div>
          <n-button size="small" :loading="loadingImageSuggestions" @click="handleImageSuggestions">
            生成图片建议
          </n-button>
        </div>
        <div v-if="imageSuggestions.length" class="article-image-grid">
          <article v-for="suggestion in imageSuggestions" :key="`${suggestion.role}-${suggestion.headingAnchor}`" class="article-image-card">
            <div class="article-image-card-title">
              <n-tag size="small" :type="suggestion.role === 'COVER' ? 'info' : 'default'">
                {{ suggestion.role === 'COVER' ? '封面' : '正文插图' }}
              </n-tag>
              <span v-if="suggestion.headingAnchor">{{ suggestion.headingAnchor }}</span>
            </div>
            <n-input v-model:value="suggestion.prompt" type="textarea" :autosize="{ minRows: 3, maxRows: 6 }" placeholder="图片提示词" />
            <n-input v-model:value="suggestion.altText" size="small" placeholder="图片替代文本" />
            <div class="article-image-card-actions">
              <n-button size="small" type="primary" :disabled="!suggestion.prompt || !suggestion.altText" @click="handleGenerateImage(suggestion)">
                {{ imageFor(suggestion)?.status === 'READY' ? '重新生成' : '生成图片' }}
              </n-button>
              <n-tag v-if="imageFor(suggestion)" size="small" :type="imageFor(suggestion)?.status === 'READY' ? 'success' : imageFor(suggestion)?.status === 'FAILED' ? 'error' : 'warning'">
                {{ imageFor(suggestion)?.status === 'QUEUED' ? '排队中' : imageFor(suggestion)?.status === 'GENERATING' ? '生成中' : imageFor(suggestion)?.status === 'READY' ? '已完成' : '失败' }}
              </n-tag>
              <n-button v-if="imageFor(suggestion)?.role === 'INLINE' && imageFor(suggestion)?.status === 'READY' && !imageFor(suggestion)?.inserted" size="small" secondary :loading="isImageActioning(imageFor(suggestion)!.id)" @click="handleInsertImage(imageFor(suggestion)!)">
                插入正文
              </n-button>
            </div>
            <img v-if="imageFor(suggestion)?.status === 'READY' && imageFor(suggestion)?.contentUrl" class="article-image-preview" :src="imageFor(suggestion)?.contentUrl || ''" :alt="imageFor(suggestion)?.altText || ''" />
            <p v-if="imageFor(suggestion)?.status === 'FAILED'" class="article-image-error">{{ imageFor(suggestion)?.errorMessage || '图片生成失败' }}</p>
          </article>
        </div>
      </section>
    </div>
    <template #footer>
      <div class="articles-modal-actions">
        <n-button @click="showEditModal = false" :disabled="saving">取消</n-button>
        <n-button type="primary" :loading="saving" @click="handleSaveEdit">保存</n-button>
        <n-button type="info" secondary @click="showWechatPreview = true" :disabled="!editContentMd">
          <template #icon>
            <n-icon><svg viewBox="0 0 24 24" width="16" height="16"><path fill="currentColor" d="M8.5 11a1.5 1.5 0 110-3 1.5 1.5 0 010 3zm7 0a1.5 1.5 0 110-3 1.5 1.5 0 010 3zM12 2C6.48 2 2 5.58 2 10c0 1.88.77 3.6 2.02 4.96L3 19l4.27-1.6c1.49.55 3.12.84 4.73.6 2.6-.39 4.85-1.77 6.28-3.7C19.58 12.6 20 11.36 20 10c0-4.42-4.48-8-10-8z"/></svg></n-icon>
          </template>
          微信预览
        </n-button>
      </div>
    </template>
  </n-modal>

  <!-- 微信预览弹窗 -->
  <n-modal v-model:show="showWechatPreview" preset="card" class="wechat-preview-modal" title="微信公众号预览" :bordered="true" size="small" :style="{ maxWidth: '520px' }">
    <template #header>
      <span class="wechat-preview-header">
        <n-icon size="18" color="#07C160">
          <svg viewBox="0 0 24 24" width="18" height="18"><path fill="currentColor" d="M8.5 11a1.5 1.5 0 110-3 1.5 1.5 0 010 3zm7 0a1.5 1.5 0 110-3 1.5 1.5 0 010 3zM12 2C6.48 2 2 5.58 2 10c0 1.88.77 3.6 2.02 4.96L3 19l4.27-1.6c1.49.55 3.12.84 4.73.6 2.6-.39 4.85-1.77 6.28-3.7C19.58 12.6 20 11.36 20 10c0-4.42-4.48-8-10-8z"/></svg>
        </n-icon>
        微信公众号预览
      </span>
    </template>
    <div class="wechat-preview-phone">
      <div class="wechat-preview-article">
        <div class="wechat-article-header">
          <h1 class="wechat-article-title">{{ editTitle || '未命名文章' }}</h1>
          <div class="wechat-article-meta">
            <span class="wechat-article-author">AgentWriter</span>
            <span class="wechat-article-date">{{ formatDateTime(new Date()) }}</span>
          </div>
        </div>
        <div class="wechat-article-divider"></div>
        <div class="wechat-article-body">
          <MdPreview
            v-if="editContentMd"
            :modelValue="editContentMd"
            :theme="editorTheme"
            language="zh-CN"
          />
          <p v-else class="wechat-article-empty">暂无内容</p>
        </div>
      </div>
    </div>
    <template #footer>
      <div class="articles-modal-actions">
        <n-button @click="showWechatPreview = false">关闭</n-button>
      </div>
    </template>
  </n-modal>
</template>

<style src="../styles/views/articles.css"></style>
