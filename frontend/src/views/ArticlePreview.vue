<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ArrowBackOutline, CreateOutline } from '@vicons/ionicons5'
import { MdPreview } from 'md-editor-v3'
import type { Themes } from 'md-editor-v3'
import 'md-editor-v3/lib/style.css'
import { fetchArticle } from '../api/articles'
import { errorMessage } from '../api/client'
import type { Article } from '../types'
import AppPageHeader from '../components/AppPageHeader.vue'
import { useThemeStore } from '../stores/theme'
import { formatDateTime } from '../utils/date'

const route = useRoute()
const router = useRouter()
const themeStore = useThemeStore()
const article = ref<Article | null>(null)
const loading = ref(true)
const loadError = ref('')

const articleId = computed(() => Number(route.params.id))
const previewTheme = computed<Themes>(() => (themeStore.isDark ? 'dark' : 'light'))
const canPreview = computed(() => article.value?.status === 'generated')

onMounted(() => {
  void loadArticle()
})

async function loadArticle() {
  loading.value = true
  loadError.value = ''
  try {
    if (!Number.isInteger(articleId.value) || articleId.value < 1) {
      throw new Error('文章编号无效。')
    }
    article.value = await fetchArticle(articleId.value)
  } catch (error) {
    loadError.value = errorMessage(error)
  } finally {
    loading.value = false
  }
}
</script>

<template>
  <section class="aw-page article-preview-page">
    <AppPageHeader title="文章预览" description="以微信公众号阅读样式查看生成结果。" eyebrow="内容预览">
      <template #actions>
        <n-button secondary @click="router.push('/articles')">
          <template #icon><n-icon><ArrowBackOutline /></n-icon></template>
          返回列表
        </n-button>
        <n-button v-if="article" type="primary" @click="router.push(`/articles/${article.id}/edit`)">
          <template #icon><n-icon><CreateOutline /></n-icon></template>
          编辑文章
        </n-button>
      </template>
    </AppPageHeader>

    <n-spin :show="loading">
      <n-alert v-if="loadError" type="error" title="文章加载失败" class="article-preview-state">
        {{ loadError }}
      </n-alert>

      <n-result
        v-else-if="!loading && article && !canPreview"
        status="info"
        title="文章尚未生成完成"
        description="请等待生成完成后再查看完整预览。"
        class="article-preview-state"
      >
        <template #footer><n-button @click="router.push('/articles')">返回文章列表</n-button></template>
      </n-result>

      <n-empty v-else-if="!loading && !article" description="文章不存在" class="article-preview-state">
        <template #extra><n-button @click="router.push('/articles')">返回文章列表</n-button></template>
      </n-empty>

      <article v-else-if="article" class="wechat-article-preview">
        <img v-if="article.coverImage" :src="article.coverImage" :alt="article.title" class="wechat-article-preview__cover">
        <header class="wechat-article-preview__header">
          <h1>{{ article.title }}</h1>
          <p>AgentWriter · {{ formatDateTime(article.updatedAt || article.createdAt) }}</p>
        </header>
        <div class="wechat-article-preview__divider" />
        <MdPreview
          v-if="article.contentMd"
          :model-value="article.contentMd"
          :theme="previewTheme"
          language="zh-CN"
          class="wechat-article-preview__content"
        />
        <n-empty v-else description="文章尚无可预览正文" class="article-preview-empty" />
      </article>
    </n-spin>
  </section>
</template>

<style scoped>
.article-preview-page { display: grid; gap: 22px; }

.article-preview-state {
  max-width: 760px;
  margin: 40px auto;
}

.wechat-article-preview {
  width: min(760px, 100%);
  box-sizing: border-box;
  margin: 0 auto;
  overflow: hidden;
  border: 1px solid var(--aw-border);
  border-radius: 6px;
  background: #fff;
  color: #202324;
  box-shadow: var(--aw-shadow);
}

.wechat-article-preview__cover {
  display: block;
  width: 100%;
  aspect-ratio: 16 / 9;
  object-fit: cover;
}

.wechat-article-preview__header { padding: 36px 42px 20px; }
.wechat-article-preview__header h1 { margin: 0; color: #161917; font-size: 28px; font-weight: 700; line-height: 1.4; }
.wechat-article-preview__header p { margin: 12px 0 0; color: #757d79; font-size: 13px; }
.wechat-article-preview__divider { height: 1px; margin: 0 42px; background: #e4e8e5; }
.wechat-article-preview__content { padding: 24px 42px 42px; }
.article-preview-empty { padding: 44px; }

@media (max-width: 640px) {
  .wechat-article-preview__header { padding: 28px 22px 18px; }
  .wechat-article-preview__header h1 { font-size: 24px; }
  .wechat-article-preview__divider { margin: 0 22px; }
  .wechat-article-preview__content { padding: 20px 22px 30px; }
}
</style>
