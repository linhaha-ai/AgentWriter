<script setup lang="ts">
import { computed, onMounted, onUnmounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import {
  ArrowBackOutline,
  EyeOutline,
  ImageOutline,
  SaveOutline,
  SendOutline,
  SparklesOutline,
} from '@vicons/ionicons5'
import { MdEditor, MdPreview } from 'md-editor-v3'
import type { Themes } from 'md-editor-v3'
import 'md-editor-v3/lib/style.css'
import {
  fetchArticle,
  fetchArticleImages,
  fetchImageSuggestions,
  generateArticleImage,
  insertArticleImage,
  publishArticle,
  updateArticle,
} from '../api/articles'
import { errorMessage } from '../api/client'
import type { Article, ArticleImage, ImageSuggestion } from '../types'
import AppPageHeader from '../components/AppPageHeader.vue'
import { useThemeStore } from '../stores/theme'
import { formatDateTime } from '../utils/date'

const route = useRoute()
const router = useRouter()
const themeStore = useThemeStore()
const article = ref<Article | null>(null)
const title = ref('')
const contentMd = ref('')
const coverImage = ref('')
const articleImages = ref<ArticleImage[]>([])
const imageSuggestions = ref<ImageSuggestion[]>([])
const loading = ref(true)
const saving = ref(false)
const publishing = ref(false)
const loadingSuggestions = ref(false)
const previewOpen = ref(false)
const lastError = ref('')
const lastSuccess = ref('')
const imageActionIds = ref<number[]>([])
let imagePollTimer: ReturnType<typeof setInterval> | null = null

const articleId = computed(() => Number(route.params.id))
const editorTheme = computed<Themes>(() => (themeStore.isDark ? 'dark' : 'light'))
const hasActiveImageTask = computed(() => articleImages.value.some(image => image.status === 'QUEUED' || image.status === 'GENERATING'))
const canPublish = computed(() => article.value?.status === 'generated')

function imageStatusLabel(status: ArticleImage['status']) {
  return ({ QUEUED: '排队中', GENERATING: '生成中', READY: '已完成', FAILED: '失败' } as Record<ArticleImage['status'], string>)[status]
}

function imageStatusType(status: ArticleImage['status']) {
  return ({ QUEUED: 'warning', GENERATING: 'info', READY: 'success', FAILED: 'error' } as const)[status]
}

function imageFor(suggestion: ImageSuggestion) {
  return [...articleImages.value].reverse().find(image => image.role === suggestion.role && image.headingAnchor === suggestion.headingAnchor)
}

function isImageActioning(imageId: number) {
  return imageActionIds.value.includes(imageId)
}

async function loadImages() {
  if (!article.value) return
  const images = await fetchArticleImages(article.value.id)
  articleImages.value = images
  const latestCover = [...images].reverse().find(image => image.role === 'COVER' && image.status === 'READY' && image.contentUrl)
  if (latestCover?.contentUrl) coverImage.value = latestCover.contentUrl
}

async function loadArticle() {
  loading.value = true
  lastError.value = ''
  try {
    if (!Number.isInteger(articleId.value) || articleId.value < 1) throw new Error('文章编号无效。')
    const loaded = await fetchArticle(articleId.value)
    article.value = loaded
    title.value = loaded.title
    contentMd.value = loaded.contentMd || ''
    coverImage.value = loaded.coverImage || ''
    await loadImages()
  } catch (error) {
    lastError.value = errorMessage(error)
  } finally {
    loading.value = false
  }
}

async function saveArticle(showSuccess = true) {
  if (!article.value) return false
  saving.value = true
  lastError.value = ''
  try {
    const updated = await updateArticle(article.value.id, {
      title: title.value.trim(),
      contentMd: contentMd.value,
      coverImage: coverImage.value || undefined,
    })
    article.value = updated
    if (showSuccess) lastSuccess.value = '文章已保存'
    return true
  } catch (error) {
    lastError.value = errorMessage(error)
    return false
  } finally {
    saving.value = false
  }
}

async function loadSuggestions() {
  if (!article.value) return
  if (!await saveArticle(false)) return
  loadingSuggestions.value = true
  lastError.value = ''
  try {
    imageSuggestions.value = await fetchImageSuggestions(article.value.id)
  } catch (error) {
    lastError.value = errorMessage(error)
  } finally {
    loadingSuggestions.value = false
  }
}

async function generateImage(suggestion: ImageSuggestion) {
  if (!article.value) return
  lastError.value = ''
  try {
    const image = await generateArticleImage(article.value.id, suggestion)
    articleImages.value = [...articleImages.value, image]
    lastSuccess.value = '图片任务已提交'
  } catch (error) {
    lastError.value = errorMessage(error)
  }
}

async function insertImage(image: ArticleImage) {
  if (!article.value) return
  imageActionIds.value = [...imageActionIds.value, image.id]
  lastError.value = ''
  try {
    const updated = await insertArticleImage(article.value.id, image.id)
    article.value = updated
    contentMd.value = updated.contentMd || ''
    articleImages.value = articleImages.value.map(item => item.id === image.id ? { ...item, inserted: true } : item)
    lastSuccess.value = '插图已写入正文'
  } catch (error) {
    lastError.value = errorMessage(error)
  } finally {
    imageActionIds.value = imageActionIds.value.filter(id => id !== image.id)
  }
}

async function publish() {
  if (!article.value || !canPublish.value) return
  publishing.value = true
  lastError.value = ''
  try {
    await publishArticle(article.value.id)
    article.value = { ...article.value, status: 'publishing' }
    lastSuccess.value = '已提交公众号发布'
  } catch (error) {
    lastError.value = errorMessage(error)
  } finally {
    publishing.value = false
  }
}

onMounted(() => {
  void loadArticle()
  imagePollTimer = setInterval(() => {
    if (hasActiveImageTask.value) void loadImages().catch(() => undefined)
  }, 3000)
})

onUnmounted(() => {
  if (imagePollTimer) clearInterval(imagePollTimer)
})
</script>

<template>
  <section class="aw-page article-editor-page">
    <AppPageHeader :title="title || '编辑文章'" description="编辑 Markdown、组织图片素材，并在确认后提交公众号发布。" eyebrow="内容工作区">
      <template #actions>
        <n-button secondary @click="router.push('/articles')">
          <template #icon><n-icon><ArrowBackOutline /></n-icon></template>
          返回列表
        </n-button>
        <n-button secondary :disabled="!contentMd" @click="previewOpen = true">
          <template #icon><n-icon><EyeOutline /></n-icon></template>
          公众号预览
        </n-button>
        <n-button type="primary" :loading="saving" :disabled="!title.trim()" @click="saveArticle()">
          <template #icon><n-icon><SaveOutline /></n-icon></template>
          保存
        </n-button>
      </template>
    </AppPageHeader>

    <n-alert v-if="lastError" type="error" title="操作失败" closable @close="lastError = ''">{{ lastError }}</n-alert>
    <n-alert v-if="lastSuccess" type="success" title="操作完成" closable @close="lastSuccess = ''">{{ lastSuccess }}</n-alert>

    <n-spin :show="loading">
      <n-empty v-if="!loading && !article" description="文章不存在或无法读取">
        <template #extra><n-button @click="router.push('/articles')">返回文章列表</n-button></template>
      </n-empty>

      <div v-else-if="article" class="article-editor-layout">
        <n-card class="article-editor-main" :bordered="false">
          <div class="article-editor-main__heading">
            <div><p class="aw-section-label">正文编辑</p><span>支持 Markdown、封面图与自动保存前检查。</span></div>
            <n-tag :type="article.status === 'generated' ? 'success' : article.status === 'failed' ? 'error' : 'info'" size="small">{{ article.status }}</n-tag>
          </div>
          <div class="article-editor-field">
            <label for="article-title">标题</label>
            <n-input id="article-title" v-model:value="title" placeholder="输入文章标题" maxlength="200" show-count />
          </div>
          <div class="article-editor-field article-editor-field--markdown">
            <label>正文（Markdown）</label>
            <MdEditor
              v-model="contentMd"
              :theme="editorTheme"
              language="zh-CN"
              :toolbars="['bold', 'italic', 'title', 'strikeThrough', 'quote', 'code', 'codeRow', 'link', 'image', 'unorderedList', 'orderedList', 'table', 0, 'preview', 'previewOnly', 'fullscreen']"
            />
          </div>
        </n-card>

        <aside class="article-editor-aside">
          <n-card class="article-side-card" :bordered="false">
            <div class="article-side-card__title"><n-icon><ImageOutline /></n-icon><strong>封面与发布</strong></div>
            <n-input v-model:value="coverImage" size="small" placeholder="封面图片 URL" />
            <img v-if="coverImage" class="article-cover-preview" :src="coverImage" alt="文章封面预览">
            <div class="article-side-card__meta"><span>当前状态</span><strong>{{ article.status }}</strong></div>
            <n-button block secondary :disabled="!contentMd" @click="previewOpen = true">查看公众号预览</n-button>
            <n-button block type="success" :loading="publishing" :disabled="!canPublish || publishing" @click="publish">
              <template #icon><n-icon><SendOutline /></n-icon></template>
              {{ canPublish ? '提交公众号发布' : '生成完成后可发布' }}
            </n-button>
          </n-card>

          <n-card class="article-side-card" :bordered="false">
            <div class="article-side-card__title"><n-icon><SparklesOutline /></n-icon><strong>AI 图片素材</strong></div>
            <p class="article-side-card__hint">保存正文后生成 1 张封面和前两个章节的插图建议。</p>
            <n-button block :loading="loadingSuggestions" @click="loadSuggestions">生成图片建议</n-button>
            <div v-if="imageSuggestions.length" class="article-image-stack">
              <article v-for="suggestion in imageSuggestions" :key="`${suggestion.role}-${suggestion.headingAnchor}`" class="article-image-task">
                <div class="article-image-task__header">
                  <n-tag size="small" :type="suggestion.role === 'COVER' ? 'info' : 'default'">{{ suggestion.role === 'COVER' ? '封面' : '正文插图' }}</n-tag>
                  <span>{{ suggestion.headingAnchor || '文章封面' }}</span>
                </div>
                <n-input v-model:value="suggestion.prompt" type="textarea" :autosize="{ minRows: 2, maxRows: 4 }" placeholder="图片提示词" />
                <n-input v-model:value="suggestion.altText" size="small" placeholder="图片替代文本" />
                <div class="article-image-task__actions">
                  <n-button size="small" type="primary" :disabled="!suggestion.prompt || !suggestion.altText" @click="generateImage(suggestion)">
                    {{ imageFor(suggestion)?.status === 'READY' ? '重新生成' : '生成图片' }}
                  </n-button>
                  <n-tag v-if="imageFor(suggestion)" size="small" :type="imageStatusType(imageFor(suggestion)!.status)">{{ imageStatusLabel(imageFor(suggestion)!.status) }}</n-tag>
                </div>
                <n-button v-if="imageFor(suggestion)?.role === 'INLINE' && imageFor(suggestion)?.status === 'READY' && !imageFor(suggestion)?.inserted" size="small" secondary :loading="isImageActioning(imageFor(suggestion)!.id)" @click="insertImage(imageFor(suggestion)!)">插入正文</n-button>
                <img v-if="imageFor(suggestion)?.status === 'READY' && imageFor(suggestion)?.contentUrl" class="article-image-task__preview" :src="imageFor(suggestion)?.contentUrl || ''" :alt="imageFor(suggestion)?.altText || ''">
                <p v-if="imageFor(suggestion)?.status === 'FAILED'" class="article-image-task__error">{{ imageFor(suggestion)?.errorMessage || '图片生成失败' }}</p>
              </article>
            </div>
          </n-card>
        </aside>
      </div>
    </n-spin>
  </section>

  <n-modal v-model:show="previewOpen" preset="card" class="wechat-preview-modal" title="微信公众号预览"
    :style="{ maxWidth: '560px' }" :mask-style="{ backgroundColor: 'rgba(12, 18, 15, 0.78)' }">
    <div class="wechat-preview-phone">
      <article class="wechat-preview-article">
        <h1>{{ title || '未命名文章' }}</h1>
        <p class="wechat-preview-article__meta">AgentWriter · {{ formatDateTime(new Date()) }}</p>
        <MdPreview v-if="contentMd" :model-value="contentMd" :theme="editorTheme" language="zh-CN" />
        <n-empty v-else description="暂无文章内容" />
      </article>
    </div>
  </n-modal>
</template>

<style scoped>
.article-editor-layout {
  display: grid;
  grid-template-columns: minmax(0, 1fr) 352px;
  align-items: start;
  gap: var(--aw-spacing-4);
}

.article-editor-main,
.article-side-card {
  border: 1px solid var(--aw-border);
  background: var(--aw-bg-shell);
  box-shadow: var(--aw-shadow);
}

.article-editor-main__heading,
.article-side-card__title,
.article-side-card__meta,
.article-image-task__header,
.article-image-task__actions {
  display: flex;
  align-items: center;
}

.article-editor-main__heading {
  justify-content: space-between;
  gap: var(--aw-spacing-3);
  margin-bottom: 20px;
}

.article-editor-main__heading span,
.article-side-card__hint,
.article-side-card__meta span,
.article-image-task__header span {
  color: var(--aw-text-secondary);
  font-size: 12px;
}

.article-editor-main__heading span { display: block; margin-top: 4px; }
.article-editor-field + .article-editor-field { margin-top: 18px; }
.article-editor-field label { display: block; margin-bottom: 7px; color: var(--aw-text-secondary); font-size: 13px; font-weight: 600; }
.article-editor-field--markdown :deep(.md-editor) { min-height: 660px; border-color: var(--aw-border); }

.article-editor-aside { display: flex; flex-direction: column; gap: var(--aw-spacing-4); }
.article-side-card { display: flex; flex-direction: column; gap: 12px; }
.article-side-card__title { gap: 8px; }
.article-side-card__title :deep(.n-icon) { color: var(--aw-accent); }
.article-side-card__hint { margin: -4px 0 0; line-height: 1.5; }
.article-side-card__meta { justify-content: space-between; padding-top: 4px; border-top: 1px solid var(--aw-border); }
.article-side-card__meta strong { font-size: 13px; }
.article-cover-preview { width: 100%; aspect-ratio: 16 / 9; border: 1px solid var(--aw-border); border-radius: var(--aw-radius-sm); object-fit: cover; }

.article-image-stack { display: flex; flex-direction: column; gap: 10px; margin-top: 2px; }
.article-image-task { display: flex; flex-direction: column; gap: 8px; padding: 10px; border: 1px solid var(--aw-border); border-radius: var(--aw-radius-sm); background: var(--aw-bg-elevated); }
.article-image-task__header { justify-content: space-between; gap: 8px; }
.article-image-task__header span { overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.article-image-task__actions { flex-wrap: wrap; gap: 7px; }
.article-image-task__preview { width: 100%; aspect-ratio: 16 / 9; border-radius: 4px; object-fit: cover; }
.article-image-task__error { margin: 0; color: var(--aw-danger); font-size: 12px; line-height: 1.45; }

.wechat-preview-phone { max-height: 70vh; overflow-y: auto; padding: 12px; border-radius: var(--aw-radius); background: #e9eceb; }
.wechat-preview-article { padding: 28px 20px; border-radius: 4px; background: #fff; color: #202324; }
.wechat-preview-article h1 { margin: 0; color: #151716; font-size: 22px; line-height: 1.45; }
.wechat-preview-article__meta { margin: 10px 0 22px; color: #6c7470; font-size: 12px; }

@media (max-width: 1080px) {
  .article-editor-layout { grid-template-columns: minmax(0, 1fr) 300px; }
}

@media (max-width: 840px) {
  .article-editor-layout { grid-template-columns: 1fr; }
  .article-editor-aside { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); }
}

@media (max-width: 620px) {
  .article-editor-aside { display: flex; }
  .article-editor-field--markdown :deep(.md-editor) { min-height: 460px; }
}
</style>
