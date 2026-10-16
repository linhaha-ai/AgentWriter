import type { AdminStyleRequest, AdminTemplateRequest, ArticleTemplate, WriterStyle } from '../types'
import { request } from './client'

/* ---- 模板管理 ---- */

export async function fetchTemplates(): Promise<ArticleTemplate[]> {
  return request<ArticleTemplate[]>('/admin/templates')
}

export async function createTemplate(data: AdminTemplateRequest): Promise<ArticleTemplate> {
  return request<ArticleTemplate>('/admin/templates', {
    method: 'POST',
    body: JSON.stringify(data),
  })
}

export async function updateTemplate(id: number, data: AdminTemplateRequest): Promise<ArticleTemplate> {
  return request<ArticleTemplate>(`/admin/templates/${id}`, {
    method: 'PUT',
    body: JSON.stringify(data),
  })
}

export async function deleteTemplate(id: number): Promise<void> {
  await request<void>(`/admin/templates/${id}`, { method: 'DELETE' })
}

/* ---- 风格管理 ---- */

export async function fetchStyles(): Promise<WriterStyle[]> {
  return request<WriterStyle[]>('/admin/styles')
}

export async function createStyle(data: AdminStyleRequest): Promise<WriterStyle> {
  return request<WriterStyle>('/admin/styles', {
    method: 'POST',
    body: JSON.stringify(data),
  })
}

export async function updateStyle(id: number, data: AdminStyleRequest): Promise<WriterStyle> {
  return request<WriterStyle>(`/admin/styles/${id}`, {
    method: 'PUT',
    body: JSON.stringify(data),
  })
}

export async function deleteStyle(id: number): Promise<void> {
  await request<void>(`/admin/styles/${id}`, { method: 'DELETE' })
}