import type { MediaAsset, MediaAssetPage, MediaCategory, TagCount } from '../types'
import { request } from './client'

/** 图库查询参数 */
export type GalleryQuery = {
  readonly page?: number
  readonly pageSize?: number
  readonly categoryId?: number | null
  readonly tag?: string | null
  readonly keyword?: string | null
}

/** 素材元数据载荷（名称/分类/标签） */
export type MediaMetaInput = {
  readonly name?: string
  readonly categoryId?: number | null
  readonly tags?: string
}

/** 分页查询图库素材 */
export async function fetchGallery(params?: GalleryQuery): Promise<MediaAssetPage> {
  const query = new URLSearchParams()
  if (params?.page != null) query.set('page', String(params.page))
  if (params?.pageSize != null) query.set('size', String(params.pageSize))
  if (params?.categoryId != null) query.set('categoryId', String(params.categoryId))
  if (params?.tag) query.set('tag', params.tag)
  if (params?.keyword) query.set('keyword', params.keyword)
  const queryString = query.toString()
  return request<MediaAssetPage>(`/gallery${queryString ? `?${queryString}` : ''}`)
}

/** 全部分类（含素材计数） */
export async function fetchCategories(): Promise<MediaCategory[]> {
  return request<MediaCategory[]>('/gallery/categories')
}

/** 全部标签（含使用计数） */
export async function fetchTags(): Promise<TagCount[]> {
  return request<TagCount[]>('/gallery/tags')
}

/** 新增分类 */
export async function createCategory(data: { name: string; description?: string; sortOrder?: number }): Promise<MediaCategory> {
  return request<MediaCategory>('/gallery/categories', {
    method: 'POST',
    body: JSON.stringify(data),
  })
}

/** 更新分类 */
export async function updateCategory(id: number, data: { name: string; description?: string; sortOrder?: number }): Promise<MediaCategory> {
  return request<MediaCategory>(`/gallery/categories/${id}`, {
    method: 'PUT',
    body: JSON.stringify(data),
  })
}

/** 删除分类（素材引用置空） */
export async function deleteCategory(id: number): Promise<void> {
  await request<null>(`/gallery/categories/${id}`, { method: 'DELETE' })
}

/** 保存站外图片链接 */
export async function saveImageUrl(data: MediaMetaInput & { url: string }): Promise<MediaAsset> {
  return request<MediaAsset>('/gallery/url', {
    method: 'POST',
    body: JSON.stringify(data),
  })
}

/** 本地上传图片 */
export async function uploadImage(data: MediaMetaInput & { file: File }): Promise<MediaAsset> {
  const form = new FormData()
  form.append('file', data.file)
  if (data.name) form.append('name', data.name)
  if (data.categoryId != null) form.append('categoryId', String(data.categoryId))
  if (data.tags) form.append('tags', data.tags)
  return request<MediaAsset>('/gallery/upload', { method: 'POST', body: form })
}

/** AI 生成图片 */
export async function generateImage(data: { prompt: string; size?: string } & MediaMetaInput): Promise<MediaAsset> {
  return request<MediaAsset>('/gallery/generate', {
    method: 'POST',
    body: JSON.stringify(data),
  })
}

/** 更新素材元数据 */
export async function updateAsset(id: number, data: MediaMetaInput): Promise<MediaAsset> {
  return request<MediaAsset>(`/gallery/${id}`, {
    method: 'PUT',
    body: JSON.stringify(data),
  })
}

/** 删除素材 */
export async function deleteAsset(id: number): Promise<void> {
  await request<null>(`/gallery/${id}`, { method: 'DELETE' })
}
