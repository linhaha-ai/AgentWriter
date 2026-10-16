import type { Article, ArticleImage, ArticleImageRole, ArticleTemplate, GenerationEvent, ImageSuggestion, ProgressResponse, PublishResult, StepGenerateRequest, StepGenerateResult, WriterStyle } from '../types'
import { request } from './client'

export type ArticleQuery = {
  readonly page?: number
  readonly pageSize?: number
  readonly status?: string
}

export type ArticleCreateInput = {
  readonly title: string
  readonly contentMd?: string
  readonly source?: string
  readonly coverImage?: string
}

export type ArticleUpdateInput = {
  readonly title?: string
  readonly contentMd?: string
  readonly coverImage?: string
}

export type ArticleImageGenerateInput = {
  readonly role: ArticleImageRole
  readonly headingAnchor: string | null
  readonly prompt: string
  readonly altText: string
}

export async function fetchArticles(params?: ArticleQuery): Promise<Article[]> {
  const query = new URLSearchParams()
  if (params?.status) query.set('status', params.status)
  const queryString = query.toString()
  return request<Article[]>(`/articles${queryString ? `?${queryString}` : ''}`)
}

export async function fetchArticle(id: number): Promise<Article> {
  return request<Article>(`/articles/${id}`)
}

export async function generateArticle(topicId: number): Promise<Article> {
  return request<Article>('/articles/generate', {
    method: 'POST',
    body: JSON.stringify({ topicId }),
  })
}

export async function publishArticle(id: number): Promise<PublishResult> {
  return request<PublishResult>(`/articles/${id}/publish`, {
    method: 'POST',
  })
}

export async function createArticle(data: ArticleCreateInput): Promise<Article> {
  return request<Article>('/articles', {
    method: 'POST',
    body: JSON.stringify(data),
  })
}

export async function updateArticle(id: number, data: ArticleUpdateInput): Promise<Article> {
  return request<Article>(`/articles/${id}`, {
    method: 'PATCH',
    body: JSON.stringify(data),
  })
}

/** 分步生成文章 */
export async function stepGenerate(data: StepGenerateRequest): Promise<StepGenerateResult> {
  return request<StepGenerateResult>('/articles/step-generate', {
    method: 'POST',
    body: JSON.stringify(data),
  })
}

/** 查询生成进度 */
export async function getProgress(id: number): Promise<ProgressResponse> {
  return request<ProgressResponse>(`/articles/${id}/progress`)
}

export type GenerationEventHandlers = {
  readonly onOpen?: () => void
  readonly onSnapshot?: (snapshot: ProgressResponse) => void
  readonly onProgress?: (event: GenerationEvent) => void
  readonly onLog?: (event: GenerationEvent) => void
  readonly onCompleted?: (event: GenerationEvent) => void
  readonly onFailed?: (event: GenerationEvent) => void
  readonly onError?: () => void
}

/** 订阅文章生成事件，调用返回函数即可关闭连接。 */
export function subscribeGenerationEvents(id: number, handlers: GenerationEventHandlers): () => void {
  const source = new EventSource(`/api/articles/${id}/events`)

  source.onopen = () => handlers.onOpen?.()
  source.onerror = () => handlers.onError?.()
  bindGenerationEvent<ProgressResponse>(source, 'snapshot', handlers.onSnapshot)
  bindGenerationEvent<GenerationEvent>(source, 'progress', handlers.onProgress)
  bindGenerationEvent<GenerationEvent>(source, 'log', handlers.onLog)
  bindGenerationEvent<GenerationEvent>(source, 'completed', handlers.onCompleted)
  bindGenerationEvent<GenerationEvent>(source, 'failed', handlers.onFailed)

  return () => source.close()
}

function bindGenerationEvent<T>(source: EventSource, eventName: string, handler?: (event: T) => void) {
  if (!handler) return
  source.addEventListener(eventName, event => {
    if (!(event instanceof MessageEvent)) return
    try {
      handler(JSON.parse(event.data) as T)
    } catch {
      // 忽略格式异常的单条事件，浏览器会继续接收后续消息。
    }
  })
}

/** 获取文章的封面与正文插图建议。 */
export async function fetchImageSuggestions(id: number): Promise<ImageSuggestion[]> {
  return request<ImageSuggestion[]>(`/articles/${id}/image-suggestions`, {
    method: 'POST',
  })
}

/** 提交单张文章图片生成任务。 */
export async function generateArticleImage(id: number, data: ArticleImageGenerateInput): Promise<ArticleImage> {
  return request<ArticleImage>(`/articles/${id}/images`, {
    method: 'POST',
    body: JSON.stringify(data),
  })
}

/** 查询文章图片任务。 */
export async function fetchArticleImages(id: number): Promise<ArticleImage[]> {
  return request<ArticleImage[]>(`/articles/${id}/images`)
}

/** 将已完成的正文插图写入对应章节。 */
export async function insertArticleImage(id: number, imageId: number): Promise<Article> {
  return request<Article>(`/articles/${id}/images/${imageId}/insert`, {
    method: 'POST',
  })
}

/** 启用的模板列表（供创建文章时选择） */
export async function fetchEnabledTemplates(): Promise<ArticleTemplate[]> {
  return request<ArticleTemplate[]>('/admin/templates/enabled')
}

/** 启用的风格列表（供创建文章时选择） */
export async function fetchEnabledStyles(): Promise<WriterStyle[]> {
  return request<WriterStyle[]>('/admin/styles/enabled')
}
