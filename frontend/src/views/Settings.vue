<script setup lang="ts">
import { onBeforeUnmount, onMounted, ref } from 'vue'
import { errorMessage } from '../api/client'
import {
  fetchLlmConfig,
  fetchPublisherConfig,
  fetchScheduleConfig,
  saveLlmConfig,
  savePublisherConfig,
  saveScheduleConfig,
} from '../api/settings'
import type { LlmConfig, LlmConfigUpdate, PublisherConfig, PublisherConfigUpdate, ScheduleConfig } from '../types'

const llmConfig = ref<LlmConfig | null>(null)
const publisherConfig = ref<PublisherConfig | null>(null)
const scheduleConfig = ref<ScheduleConfig | null>(null)
const loading = ref(true)
const savingLlm = ref(false)
const savingPublisher = ref(false)
const savingSchedule = ref(false)
const lastError = ref('')
const lastSuccess = ref('')
const compactForm = ref(false)

function updateCompactForm() {
  compactForm.value = window.innerWidth <= 768
}

async function loadSettings() {
  loading.value = true
  lastError.value = ''
  lastSuccess.value = ''
  try {
    const [llm, pub, sched] = await Promise.all([
      fetchLlmConfig(),
      fetchPublisherConfig(),
      fetchScheduleConfig(),
    ])
    llmConfig.value = llm
    publisherConfig.value = pub
    scheduleConfig.value = sched
  } catch (error: unknown) {
    lastError.value = errorMessage(error)
  } finally {
    loading.value = false
  }
}

onMounted(() => {
  updateCompactForm()
  window.addEventListener('resize', updateCompactForm)
  void loadSettings()
})

onBeforeUnmount(() => {
  window.removeEventListener('resize', updateCompactForm)
})

function llmUpdate(config: LlmConfig): LlmConfigUpdate {
  return {
    enabled: config.enabled,
    provider: config.provider,
    apiUrl: config.apiUrl,
    model: config.model,
    timeout: config.timeout,
  }
}

function publisherUpdate(config: PublisherConfig): PublisherConfigUpdate {
  return {
    wechat: {
      enabled: config.wechat.enabled,
      appId: config.wechat.appId,
      author: config.wechat.author,
      thumbMediaId: config.wechat.thumbMediaId,
    },
  }
}

async function saveLlm() {
  if (!llmConfig.value) return
  savingLlm.value = true
  lastError.value = ''
  lastSuccess.value = ''
  try {
    llmConfig.value = await saveLlmConfig(llmUpdate(llmConfig.value))
    lastSuccess.value = 'LLM 配置已保存'
  } catch (error: unknown) {
    lastError.value = errorMessage(error)
  } finally {
    savingLlm.value = false
  }
}

async function savePublisher() {
  if (!publisherConfig.value) return
  savingPublisher.value = true
  lastError.value = ''
  lastSuccess.value = ''
  try {
    publisherConfig.value = await savePublisherConfig(publisherUpdate(publisherConfig.value))
    lastSuccess.value = '发布配置已保存'
  } catch (error: unknown) {
    lastError.value = errorMessage(error)
  } finally {
    savingPublisher.value = false
  }
}

async function saveSchedule() {
  if (!scheduleConfig.value) return
  savingSchedule.value = true
  lastError.value = ''
  lastSuccess.value = ''
  try {
    scheduleConfig.value = await saveScheduleConfig(scheduleConfig.value)
    lastSuccess.value = '调度配置已保存'
  } catch (error: unknown) {
    lastError.value = errorMessage(error)
  } finally {
    savingSchedule.value = false
  }
}
</script>

<template>
  <div class="settings-page">
    <h2 class="settings-page-title">系统设置</h2>
    <n-alert v-if="lastError" title="设置操作失败" type="error" :show-icon="true">
      {{ lastError }}
    </n-alert>
    <n-alert v-if="lastSuccess" title="设置操作完成" type="success" :show-icon="true">
      {{ lastSuccess }}
    </n-alert>

    <n-spin :show="loading">
      <!-- LLM 配置 -->
      <n-card title="LLM 配置" size="small" class="settings-card" :bordered="true">
        <n-form class="settings-form" :label-placement="compactForm ? 'top' : 'left'" label-width="120" size="small" v-if="llmConfig">
          <n-form-item label="启用">
            <n-switch v-model:value="llmConfig.enabled" />
          </n-form-item>
          <n-form-item label="服务商">
            <n-select
              v-model:value="llmConfig.provider"
              :options="[
                { label: 'Agnes AI', value: 'agnes' },
                { label: 'NVIDIA NIM', value: 'nvidia' },
                { label: 'OpenAI', value: 'openai' },
              ]"
              class="settings-select-compact"
            />
          </n-form-item>
          <n-form-item label="API URL">
            <n-input v-model:value="llmConfig.apiUrl" class="settings-input-wide" />
          </n-form-item>
          <n-form-item label="环境凭据">
            <div class="settings-credential-state">
              <n-tag :type="llmConfig.credentialConfigured ? 'success' : 'warning'" size="small">
                {{ llmConfig.credentialConfigured ? '凭据已配置' : '凭据未配置' }}
              </n-tag>
              <span class="settings-helper-text">
                请设置 AGNES_API_KEY、NVIDIA_API_KEY 或 OPENAI_API_KEY；开发环境也可配置 agentwriter.llm.api-key。
              </span>
            </div>
          </n-form-item>
          <n-form-item label="模型">
            <n-input v-model:value="llmConfig.model" class="settings-input-wide" />
          </n-form-item>
          <n-form-item label="超时(秒)">
            <n-input-number v-model:value="llmConfig.timeout" :min="10" :max="300" />
          </n-form-item>
          <n-form-item>
            <n-button type="primary" size="small" :loading="savingLlm" @click="saveLlm">保存配置</n-button>
          </n-form-item>
        </n-form>
      </n-card>

      <!-- 发布配置 -->
      <n-card title="发布配置" size="small" class="settings-card" :bordered="true">
        <n-form class="settings-form" :label-placement="compactForm ? 'top' : 'left'" label-width="120" size="small" v-if="publisherConfig">
          <n-form-item label="微信公众号">
            <n-switch v-model:value="publisherConfig.wechat.enabled" />
          </n-form-item>
          <n-form-item label="环境凭据">
            <div class="settings-credential-state">
              <n-tag :type="publisherConfig.wechat.credentialConfigured ? 'success' : 'warning'" size="small">
                {{ publisherConfig.wechat.credentialConfigured ? '凭据已配置' : '凭据未配置' }}
              </n-tag>
              <span class="settings-helper-text">
                请在环境变量中设置 WECHAT_APP_SECRET。
              </span>
            </div>
          </n-form-item>
          <n-form-item label="AppID">
            <n-input v-model:value="publisherConfig.wechat.appId" class="settings-input-wide" />
          </n-form-item>
          <n-form-item label="作者">
            <n-input v-model:value="publisherConfig.wechat.author" class="settings-input-compact" />
          </n-form-item>
          <n-form-item label="封面素材 ID">
            <n-input v-model:value="publisherConfig.wechat.thumbMediaId" class="settings-input-wide" />
          </n-form-item>
          <n-form-item>
            <n-button type="primary" size="small" :loading="savingPublisher" @click="savePublisher">保存配置</n-button>
          </n-form-item>
        </n-form>
      </n-card>

      <!-- 调度配置 -->
      <n-card title="定时调度" size="small" class="settings-card" :bordered="true">
        <n-form class="settings-form" :label-placement="compactForm ? 'top' : 'left'" label-width="120" size="small" v-if="scheduleConfig">
          <n-form-item label="启用自动写作">
            <n-switch v-model:value="scheduleConfig.enabled" />
          </n-form-item>
          <n-form-item label="Cron 表达式">
            <n-input v-model:value="scheduleConfig.cron" class="settings-input-compact" placeholder="0 0 8 * * ?" />
          </n-form-item>
          <n-form-item>
            <n-button type="primary" size="small" :loading="savingSchedule" @click="saveSchedule">保存配置</n-button>
          </n-form-item>
        </n-form>
      </n-card>
    </n-spin>
  </div>
</template>

<style src="../styles/views/settings.css"></style>
