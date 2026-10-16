import type { Activity, AgentStatus, DashboardStats } from '../types'
import { request } from './client'

export async function fetchDashboardStats(): Promise<DashboardStats> {
  return request<DashboardStats>('/dashboard/stats')
}

export async function fetchAgentStatuses(): Promise<AgentStatus[]> {
  return request<AgentStatus[]>('/agents/status')
}

export async function fetchRecentActivities(): Promise<Activity[]> {
  return request<Activity[]>('/activities')
}
