<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { errorMessage } from '../api/client'
import {
  fetchImageConfig,
  fetchLlmConfig,
  fetchPublisherConfig,
  fetchScheduleConfig,
  saveImageConfig,
  saveLlmConfig,
  savePublisherConfig,
  saveScheduleConfig,
} from '../api/settings'
import type { ImageConfig, ImageConfigUpdate, LlmConfig, LlmConfigUpdate, PublisherConfig, PublisherConfigUpdate, ScheduleConfig } from '../types'

type SettingsSection = 'llm' | 'publisher' | 'other'

const props = defineProps<{ section?: SettingsSection }>()
const section = computed<SettingsSection>(() => props.section ?? 'llm')
const sectionTitle = computed(() =>
  section.value === 'llm' ? '大模型配置' : section.value === 'publisher' ? '发布配置' : '其他配置',
)

const llmConfig = ref<LlmConfig | null>(null)
const publisherConfig = ref<PublisherConfig | null>(null)
const scheduleConfig = ref<ScheduleConfig | null>(null)
const imageConfig = ref<ImageConfig>({
  enabled: false,
  apiUrl: '',
  model: '',
  size: '1024x1024',
  credentialConfigured: false,
})
const loading = ref(true)
const savingLlm = ref(false)
const savingPublisher = ref(false)
const savingSchedule = ref(false)
const savingImage = ref(false)
const lastError = ref('')
const lastSuccess = ref('')
const compactForm = ref(false)
const llmApiKeyInput = ref('')
const llmApiKeyCleared = ref(false)
const wechatAppSecretInput = ref('')
const wechatAppSecretCleared = ref(false)
const imageApiKeyInput = ref('')
const imageApiKeyCleared = ref(false)

function updateCompactForm() {
  compactForm.value = window.innerWidth <= 768
}

async function loadSettings() {
  loading.value = true
  lastError.value = ''
  lastSuccess.value = ''
  try {
    if (section.value === 'llm') {
      llmConfig.value = await fetchLlmConfig()
    } else if (section.value === 'publisher') {
      publisherConfig.value = await fetchPublisherConfig()
    } else {
      const [sched, image] = await Promise.all([fetchScheduleConfig(), fetchImageConfig()])
      scheduleConfig.value = sched
      imageConfig.value = image
    }
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

watch(section, () => void loadSettings())

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

function imageUpdate(config: ImageConfig): ImageConfigUpdate {
  const update: ImageConfigUpdate = {
    enabled: config.enabled,
    apiUrl: config.apiUrl,
    model: config.model,
    size: config.size,
  }
  if (imageApiKeyCleared.value) {
    update.apiKey = ''
  } else if (imageApiKeyInput.value.trim()) {
    update.apiKey = imageApiKeyInput.value.trim()
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

async function saveImage() {
  savingImage.value = true
  lastError.value = ''
  lastSuccess.value = ''
  try {
    imageConfig.value = await saveImageConfig(imageUpdate(imageConfig.value))
    imageApiKeyInput.value = ''
    imageApiKeyCleared.value = false
    lastSuccess.value = '图像生成配置已保存'
  } catch (error: unknown) {
    lastError.value = errorMessage(error)
  } finally {
    savingImage.value = false
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

function clearImageApiKey() {
  imageApiKeyInput.value = ''
  imageApiKeyCleared.value = true
}
</script>

<template>
  <div class="settings-page">
    <h2 class="settings-page-title">{{ sectionTitle }}</h2>
    <n-alert v-if="lastError" title="设置操作失败" type="error" :show-icon="true">
      {{ lastError }}
    </n-alert>
    <n-alert v-if="lastSuccess" title="设置操作完成" type="success" :show-icon="true">
      {{ lastSuccess }}
    </n-alert>

    <n-spin :show="loading">
      <!-- 大模型配置 -->
      <template v-if="section === 'llm'">
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
      </template>

      <!-- 发布配置 -->
      <template v-if="section === 'publisher'">
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
      </template>

      <!-- 其他配置：图像生成 + 定时调度 -->
      <template v-if="section === 'other'">
      <!-- 图像生成配置 -->
      <n-card title="图像生成配置" size="small" class="settings-card" :bordered="true">
        <n-form class="settings-form" :label-placement="compactForm ? 'top' : 'left'" label-width="120" size="small">
          <n-form-item label="启用图像生成">
            <n-switch v-model:value="imageConfig.enabled" />
          </n-form-item>
          <n-form-item label="API URL">
            <n-input v-model:value="imageConfig.apiUrl" class="settings-input-wide" placeholder="https://apihub.example.com/v1" />
          </n-form-item>
          <n-form-item label="API Key">
            <div class="settings-credential-state">
              <div class="settings-credential-row">
                <n-tag :type="imageConfig.credentialConfigured ? 'success' : 'warning'" size="small">
                  {{ imageConfig.credentialConfigured ? '凭据已配置' : '凭据未配置' }}
                </n-tag>
                <n-input
                  v-model:value="imageApiKeyInput"
                  type="password"
                  show-password-on="click"
                  class="settings-input-wide"
                  :placeholder="imageConfig.credentialConfigured ? '已保存凭据：输入新值覆盖，留空保持不变' : '请输入图像生成 API Key'"
                />
                <n-button
                  v-if="imageConfig.credentialConfigured"
                  size="small"
                  quaternary
                  type="error"
                  @click="clearImageApiKey"
                >
                  清除已保存凭据
                </n-button>
              </div>
              <span class="settings-helper-text">
                资源图库的 AI 生成使用 OpenAI 兼容的 /images/generations 接口，请填写支持该协议的图像服务地址、Key 与模型。
              </span>
            </div>
          </n-form-item>
          <n-form-item label="模型">
            <n-input v-model:value="imageConfig.model" class="settings-input-wide" placeholder="如 gpt-image-1 / dall-e-3 / seedream-5.0-flash" />
          </n-form-item>
          <n-form-item label="默认尺寸">
            <n-input v-model:value="imageConfig.size" class="settings-input-compact" placeholder="1024x1024" />
          </n-form-item>
          <n-form-item>
            <n-button type="primary" size="small" :loading="savingImage" @click="saveImage">保存配置</n-button>
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
      </template>
    </n-spin>
  </div>
</template>

<style src="../styles/views/settings.css"></style>
