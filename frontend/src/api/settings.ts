import type {
  LlmConfig,
  LlmConfigUpdate,
  PublisherConfig,
  PublisherConfigUpdate,
  ScheduleConfig,
} from '../types'
import { request } from './client'

export async function fetchLlmConfig(): Promise<LlmConfig> {
  return request<LlmConfig>('/settings/llm')
}

export async function saveLlmConfig(config: LlmConfigUpdate): Promise<LlmConfig> {
  return request<LlmConfig>('/settings/llm', {
    method: 'PUT',
    body: JSON.stringify(config),
  })
}

export async function fetchPublisherConfig(): Promise<PublisherConfig> {
  return request<PublisherConfig>('/settings/publisher')
}

export async function savePublisherConfig(config: PublisherConfigUpdate): Promise<PublisherConfig> {
  return request<PublisherConfig>('/settings/publisher', {
    method: 'PUT',
    body: JSON.stringify(config),
  })
}

export async function fetchScheduleConfig(): Promise<ScheduleConfig> {
  return request<ScheduleConfig>('/settings/schedule')
}

export async function saveScheduleConfig(config: ScheduleConfig): Promise<ScheduleConfig> {
  return request<ScheduleConfig>('/settings/schedule', {
    method: 'PUT',
    body: JSON.stringify(config),
  })
}
