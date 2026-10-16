import type { Topic } from '../types'
import { request } from './client'

export async function fetchTopics(): Promise<Topic[]> {
  return request<Topic[]>('/topics')
}

export async function selectTopic(id: number): Promise<Topic> {
  return request<Topic>(`/topics/${id}/select`, { method: 'POST' })
}

export async function collectTopics(): Promise<Topic[]> {
  return request<Topic[]>('/topics/collect', { method: 'POST' })
}
