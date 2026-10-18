/** 文章状态 */
export type ArticleStatus =
  | 'draft'
  | 'generating'
  | 'writing'
  | 'reviewing'
  | 'generated'
  | 'publishing'
  | 'failed'

/** 文章 */
export interface Article {
  id: number
  title: string
  contentMd: string
  contentHtml: string
  status: ArticleStatus
  source: string
  coverImage?: string
  templateId?: number
  styleId?: number
  generationProgress?: string | null
  createdAt: string
  updatedAt: string
}

/** 文章图片用途 */
export type ArticleImageRole = 'COVER' | 'INLINE'

/** 文章图片生成状态 */
export type ArticleImageStatus = 'QUEUED' | 'GENERATING' | 'READY' | 'FAILED'

/** 图片生成前的可编辑建议 */
export interface ImageSuggestion {
  role: ArticleImageRole
  headingAnchor: string | null
  prompt: string
  altText: string
}

/** 应用托管的文章图片 */
export interface ArticleImage {
  id: number
  role: ArticleImageRole
  status: ArticleImageStatus
  prompt: string
  altText: string
  headingAnchor: string | null
  inserted: boolean
  contentUrl: string | null
  errorMessage: string | null
  createdAt: string | null
  updatedAt: string | null
}

/** 图库素材来源 */
export type MediaAssetSource = 'GENERATED' | 'UPLOADED' | 'URL'

/** 图库图片分类 */
export interface MediaCategory {
  id: number
  name: string
  description?: string | null
  sortOrder?: number
  assetCount: number
  createdAt: string | null
  updatedAt: string | null
}

/** 图库素材 */
export interface MediaAsset {
  id: number
  name: string | null
  url: string
  prompt: string | null
  source: MediaAssetSource
  categoryId: number | null
  categoryName: string | null
  tags: string[]
  width: number | null
  height: number | null
  fileSize: number | null
  contentType: string | null
  createdAt: string | null
  updatedAt: string | null
}

/** 图库标签聚合 */
export interface TagCount {
  tag: string
  count: number
}

/** 图库素材分页结果 */
export interface MediaAssetPage {
  items: MediaAsset[]
  page: number
  pageSize: number
  total: number
}

/** 生成进度 */
export interface GenerationProgress {
  currentStep: string
  totalSteps: number
  currentStepIndex: number
  stepStatus: 'pending' | 'running' | 'success' | 'failed'
  progress: number
}

/** 面向前端的安全生成日志。 */
export interface GenerationLog {
  id: number
  step: string
  status: 'pending' | 'running' | 'success' | 'succeeded' | 'failed'
  message: string
  /** 该阶段完成后的实际产出内容（调研摘要 / 大纲 / 正文等） */
  output?: string | null
  createdAt: string | null
}

/** 文章模板 */
export interface ArticleTemplate {
  id: number
  name: string
  description?: string
  titleTemplate?: string
  outlineTemplate?: string
  coverRule?: string
  enabled: boolean
  createdAt: string
  updatedAt: string
}

/** 写作风格 */
export interface WriterStyle {
  id: number
  name: string
  description?: string
  tonePrompt?: string
  features?: string
  enabled: boolean
  createdAt: string
  updatedAt: string
}

/** 分步生成请求 */
export interface StepGenerateRequest {
  readonly topicIds: readonly number[]
  readonly title: string
  readonly templateId?: number
  readonly styleId: number
}

export interface StepGenerateResult {
  readonly id: number
}

/** 进度响应 */
export interface ProgressResponse {
  articleId: number
  status: ArticleStatus
  generationProgress: GenerationProgress | null
  logs: GenerationLog[]
}

/** SSE 推送的单次生成进度或日志更新。 */
export interface GenerationEvent {
  articleId: number
  status: ArticleStatus
  generationProgress: GenerationProgress | null
  log: GenerationLog | null
}

/** 后台模板创建/更新请求 */
export interface AdminTemplateRequest {
  name: string
  description?: string
  titleTemplate?: string
  outlineTemplate?: string
  coverRule?: string
  enabled?: boolean
}

/** 后台风格创建/更新请求 */
export interface AdminStyleRequest {
  name: string
  description?: string
  tonePrompt?: string
  features?: string
  enabled?: boolean
}

/** 发布提交结果。提交成功不等于平台已最终发布。 */
export interface PublishResult {
  platform: string
  url: string | null
  publishedAt: string
  articleId: number
  coverImage: string | null
}

/** 话题 */
export interface Topic {
  id: number
  title: string
  score: number
  source: 'github' | 'rss' | 'openhot' | 'manual'
  reason: string
  link: string
  createdAt: string
}

/** 系统状态概览 */
export interface DashboardStats {
  todayArticles: number
  activeAgents: number
  topicCount: number
  publishedToday: number
  systemUptime: string
  lastRunTime: string
  nextRunTime: string
  writingEnabled: boolean
}

/** 智能体状态 */
export interface AgentStatus {
  name: string
  label: string
  status: 'idle' | 'running' | 'success' | 'error'
  lastRun: string
  duration: string
}

/** 最近活动 */
export interface Activity {
  id: number
  type: 'topic' | 'write' | 'review' | 'publish' | 'error'
  message: string
  articleId?: number
  time: string
}

/** LLM 配置 */
export interface LlmConfig {
  enabled: boolean
  provider: 'nvidia' | 'openai' | 'agnes'
  apiUrl: string
  model: string
  timeout: number
  credentialConfigured: boolean
}

/** 可保存的 LLM 配置；apiKey 可选：缺省=不修改已保存凭据，空字符串=清除已保存凭据，非空=覆盖 */
export interface LlmConfigUpdate {
  enabled: boolean
  provider: 'nvidia' | 'openai' | 'agnes'
  apiUrl: string
  model: string
  timeout: number
  apiKey?: string
}

/** 图像生成配置（OpenAI 兼容 /images/generations） */
export interface ImageConfig {
  enabled: boolean
  apiUrl: string
  model: string
  size: string
  credentialConfigured: boolean
}

/** 可保存的图像生成配置；apiKey 语义同 LLM（缺省=保留、空串=清除、非空=覆盖） */
export interface ImageConfigUpdate {
  enabled: boolean
  apiUrl: string
  model: string
  size: string
  apiKey?: string
}

/** 发布配置 */
export interface PublisherConfig {
  wechat: {
    enabled: boolean
    appId: string
    author: string
    thumbMediaId: string
    credentialConfigured: boolean
  }
}

/** 可保存的发布配置；appSecret 可选，语义同 apiKey（缺省=不变，空串=清除，非空=覆盖） */
export interface PublisherConfigUpdate {
  wechat: {
    enabled: boolean
    appId: string
    author: string
    thumbMediaId: string
    appSecret?: string
  }
}

/** 调度配置 */
export interface ScheduleConfig {
  enabled: boolean
  cron: string
}
