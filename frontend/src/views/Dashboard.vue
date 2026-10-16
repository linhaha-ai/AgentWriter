<script setup lang="ts">
import { onMounted } from 'vue'
import type { Component } from 'vue'
import {
  AlertCircleOutline,
  CheckmarkCircleOutline,
  CreateOutline,
  PaperPlaneOutline,
  SearchOutline,
} from '@vicons/ionicons5'
import { useDashboardStore } from '../stores/dashboard'
import type { Activity, AgentStatus, ArticleStatus } from '../types'
import AppPageHeader from '../components/AppPageHeader.vue'
import { formatDateTime } from '../utils/date'

const store = useDashboardStore()

onMounted(() => {
  store.loadAll()
})

type TagType = 'default' | 'error' | 'info' | 'success' | 'warning'

const statusColor: Record<AgentStatus['status'], TagType> = {
  idle: 'default',
  running: 'info',
  success: 'success',
  error: 'error',
}

const activityIcon: Record<Activity['type'], Component> = {
  topic: SearchOutline,
  write: CreateOutline,
  review: CheckmarkCircleOutline,
  publish: PaperPlaneOutline,
  error: AlertCircleOutline,
}

const articleStatusLabel: Record<ArticleStatus, string> = {
  draft: '草稿',
  generating: '生成中',
  writing: '撰写中',
  reviewing: '审核中',
  generated: '已生成',
  publishing: '发布中',
  failed: '失败',
}

const sourceLabel: Record<string, string> = {
  github: 'GitHub',
  rss: 'RSS',
  openhot: 'OpenHot',
  manual: '手动',
}

function agentStatusLabel(agent: AgentStatus): string {
  if (agent.status === 'running') return '运行中'
  if (agent.status === 'idle') return '待命'
  if (agent.status === 'success') return agent.duration
  return '错误'
}
</script>

<template>
  <section class="aw-page dashboard-page">
    <AppPageHeader title="运营概览" description="跟踪内容生产、智能体执行和发布节奏。" eyebrow="工作台" />

    <!-- 统计卡片 -->
    <n-grid cols="2 m:4" responsive="screen" :x-gap="16" :y-gap="16">
      <n-grid-item>
        <n-card size="small" class="dashboard-card">
          <n-statistic label="今日文章" :value="store.stats?.todayArticles ?? '-'">
            <template #prefix>
              <n-icon><svg viewBox="0 0 24 24" width="24" height="24"><path fill="currentColor" d="M3 3h18v2H3V3m0 4h18v2H3V7m0 4h18v2H3v-2m0 4h18v2H3v-2m0 4h10v2H3v-2z"/></svg></n-icon>
            </template>
          </n-statistic>
        </n-card>
      </n-grid-item>
      <n-grid-item>
        <n-card size="small" class="dashboard-card">
          <n-statistic label="今日提交" :value="store.stats?.publishedToday ?? '-'">
            <template #prefix>
              <n-icon><svg viewBox="0 0 24 24" width="24" height="24"><path fill="currentColor" d="M2 3l20 9L2 21l3-9-3-9zm4.5 9L4 6.8 9.4 12 4 17.2l2.5-5.2z"/></svg></n-icon>
            </template>
          </n-statistic>
        </n-card>
      </n-grid-item>
      <n-grid-item>
        <n-card size="small" class="dashboard-card">
          <n-statistic label="活跃智能体" :value="store.stats?.activeAgents ?? '-'">
            <template #prefix>
              <n-icon><svg viewBox="0 0 24 24" width="24" height="24"><path fill="currentColor" d="M12 2C6.48 2 2 6.48 2 12s4.48 10 10 10 10-4.48 10-10S17.52 2 12 2m0 3c1.66 0 3 1.34 3 3s-1.34 3-3 3-3-1.34-3-3 1.34-3 3-3m0 14.2c-2.5 0-4.71-1.28-6-3.22.03-1.99 4-3.08 6-3.08 2 0 5.97 1.09 6 3.08-1.29 1.94-3.5 3.22-6 3.22z"/></svg></n-icon>
            </template>
          </n-statistic>
        </n-card>
      </n-grid-item>
      <n-grid-item>
        <n-card size="small" class="dashboard-card">
          <n-statistic label="热点话题" :value="store.stats?.topicCount ?? '-'">
            <template #prefix>
              <n-icon><svg viewBox="0 0 24 24" width="24" height="24"><path fill="currentColor" d="M16 6l2.29 2.29-4.88 4.88-4-4L2 16.59 3.41 18l6-6 4 4 6.3-6.29L22 12V6z"/></svg></n-icon>
            </template>
          </n-statistic>
        </n-card>
      </n-grid-item>
    </n-grid>

    <section class="dashboard-pipeline aw-surface">
      <div>
        <p class="aw-section-label">内容生产管线</p>
        <strong>从热点发现到公众号发布</strong>
      </div>
      <div class="dashboard-pipeline__steps">
        <span><i class="aw-status-dot"></i>热点 {{ store.stats?.topicCount ?? '-' }}</span>
        <span><i class="aw-status-dot dashboard-pipeline__dot--info"></i>运行中 {{ store.stats?.activeAgents ?? '-' }}</span>
        <span><i class="aw-status-dot dashboard-pipeline__dot--accent"></i>今日发布 {{ store.stats?.publishedToday ?? '-' }}</span>
      </div>
    </section>

    <!-- 系统与智能体状态 -->
    <n-grid cols="1 m:3" responsive="screen" :x-gap="16" :y-gap="16">
      <!-- 系统状态 -->
      <n-grid-item span="1">
        <n-card title="系统状态" size="small" class="dashboard-card" :bordered="true">
          <n-descriptions :column="1" label-placement="left" size="small">
            <n-descriptions-item label="运行时长">{{ store.stats?.systemUptime }}</n-descriptions-item>
            <n-descriptions-item label="上次运行">{{ store.stats?.lastRunTime }}</n-descriptions-item>
            <n-descriptions-item label="下次运行">{{ store.stats?.nextRunTime }}</n-descriptions-item>
            <n-descriptions-item label="自动写作">
              <n-switch :value="store.stats?.writingEnabled" disabled size="small" />
            </n-descriptions-item>
          </n-descriptions>
        </n-card>
      </n-grid-item>

      <!-- 智能体状态 -->
      <n-grid-item span="1">
        <n-card title="智能体状态" size="small" class="dashboard-card" :bordered="true">
          <n-list>
            <n-list-item v-for="agent in store.agents" :key="agent.name" class="dashboard-agent-item">
              <div class="dashboard-agent-row">
                <div class="dashboard-agent-name">
                  <n-tag :type="statusColor[agent.status]" size="tiny" round />
                  <span class="dashboard-agent-label">{{ agent.label }}</span>
                </div>
                <n-tag :type="statusColor[agent.status]" size="tiny">
                  {{ agentStatusLabel(agent) }}
                </n-tag>
              </div>
            </n-list-item>
          </n-list>
        </n-card>
      </n-grid-item>

      <!-- 最近活动 -->
      <n-grid-item span="1">
        <n-card title="最近活动" size="small" class="dashboard-card" :bordered="true">
          <n-list>
            <n-list-item v-for="act in store.activities.slice(0, 6)" :key="act.id" class="dashboard-activity-item">
              <div class="dashboard-activity-row">
                <n-icon><component :is="activityIcon[act.type]" /></n-icon>
                <div class="dashboard-activity-content">
                  <div class="dashboard-activity-message">{{ act.message }}</div>
                  <div class="dashboard-activity-time">{{ act.time }}</div>
                </div>
              </div>
            </n-list-item>
          </n-list>
        </n-card>
      </n-grid-item>
    </n-grid>

    <!-- 待处理文章 -->
    <n-card title="待处理文章" size="small" class="dashboard-card" :bordered="true">
      <div class="dashboard-table-scroll">
        <n-table :single-line="false" size="small">
          <thead>
            <tr>
              <th>标题</th>
              <th class="dashboard-column-status">状态</th>
              <th class="dashboard-column-source">来源</th>
              <th class="dashboard-column-updated">更新时间</th>
              <th class="dashboard-column-actions">操作</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="article in store.articles.slice(0, 5)" :key="article.id">
              <td>{{ article.title }}</td>
              <td>
                <n-tag size="tiny" :type="article.status === 'failed' ? 'error' : article.status === 'publishing' || article.status === 'writing' || article.status === 'generating' ? 'info' : 'warning'">
                  {{ articleStatusLabel[article.status] }}
                </n-tag>
              </td>
              <td>
                <n-tag size="tiny" :type="article.source === 'github' ? 'info' : article.source === 'rss' ? 'success' : 'warning'">
                  {{ sourceLabel[article.source] || article.source }}
                </n-tag>
              </td>
              <td class="dashboard-article-time">{{ formatDateTime(article.updatedAt) }}</td>
              <td>
                <n-button size="tiny" quaternary disabled title="详情页尚未开放">查看</n-button>
              </td>
            </tr>
          </tbody>
        </n-table>
      </div>
    </n-card>
  </section>
</template>

<style src="../styles/views/dashboard.css"></style>
