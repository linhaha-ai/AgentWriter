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
const llmApiKeyInput = ref('')
const llmApiKeyCleared = ref(false)
const wechatAppSecretInput = ref('')
const wechatAppSecretCleared = ref(false)

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
  const update: LlmConfigUpdate = {
    enabled: config.enabled,
    provider: config.provider,
    apiUrl: config.apiUrl,
    model: config.model,
    timeout: config.timeout,
  }
  if (llmApiKeyCleared.value) {
    update.apiKey = ''
  } else if (llmApiKeyInput.value.trim()) {
    update.apiKey = llmApiKeyInput.value.trim()
  }
  return update
}

function publisherUpdate(config: PublisherConfig): PublisherConfigUpdate {
  const update: PublisherConfigUpdate = {
    wechat: {
      enabled: config.wechat.enabled,
      appId: config.wechat.appId,
      author: config.wechat.author,
      thumbMediaId: config.wechat.thumbMediaId,
    },
  }
  if (wechatAppSecretCleared.value) {
    update.wechat.appSecret = ''
  } else if (wechatAppSecretInput.value.trim()) {
    update.wechat.appSecret = wechatAppSecretInput.value.trim()
  }
  return update
}

async function saveLlm() {
  if (!llmConfig.value) return
  savingLlm.value = true
  lastError.value = ''
  lastSuccess.value = ''
  try {
    llmConfig.value = await saveLlmConfig(llmUpdate(llmConfig.value))
    llmApiKeyInput.value = ''
    llmApiKeyCleared.value = false
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
    wechatAppSecretInput.value = ''
    wechatAppSecretCleared.value = false
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

function clearLlmApiKey() {
  llmApiKeyInput.value = ''
  llmApiKeyCleared.value = true
}

function clearWechatAppSecret() {
  wechatAppSecretInput.value = ''
  wechatAppSecretCleared.value = true
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
          <n-form-item label="API Key">
            <div class="settings-credential-state">
              <div class="settings-credential-row">
                <n-tag :type="llmConfig.credentialConfigured ? 'success' : 'warning'" size="small">
                  {{ llmConfig.credentialConfigured ? '凭据已配置' : '凭据未配置' }}
                </n-tag>
                <n-input
                  v-model:value="llmApiKeyInput"
                  type="password"
                  show-password-on="click"
                  class="settings-input-wide"
                  :placeholder="llmConfig.credentialConfigured ? '已保存凭据：输入新值覆盖，留空保持不变' : '请输入 API Key（AGNES/NVIDIA/OPENAI）'"
                />
                <n-button
                  v-if="llmConfig.credentialConfigured"
                  size="small"
                  quaternary
                  type="error"
                  @click="clearLlmApiKey"
                >
                  清除已保存凭据
                </n-button>
              </div>
              <span class="settings-helper-text">
                可在此手动填写 API Key 并保存；保存后优先使用界面凭据，环境变量与 yml 配置作为兜底。
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
          <n-form-item label="AppSecret">
            <div class="settings-credential-state">
              <div class="settings-credential-row">
                <n-tag :type="publisherConfig.wechat.credentialConfigured ? 'success' : 'warning'" size="small">
                  {{ publisherConfig.wechat.credentialConfigured ? '凭据已配置' : '凭据未配置' }}
                </n-tag>
                <n-input
                  v-model:value="wechatAppSecretInput"
                  type="password"
                  show-password-on="click"
                  class="settings-input-wide"
                  :placeholder="publisherConfig.wechat.credentialConfigured ? '已保存凭据：输入新值覆盖，留空保持不变' : '请输入微信公众号 AppSecret'"
                />
                <n-button
                  v-if="publisherConfig.wechat.credentialConfigured"
                  size="small"
                  quaternary
                  type="error"
                  @click="clearWechatAppSecret"
                >
                  清除已保存凭据
                </n-button>
              </div>
              <span class="settings-helper-text">
                可在此手动填写 AppSecret 并保存；保存后优先使用界面凭据，环境变量 WECHAT_APP_SECRET 与 yml 配置作为兜底。
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
