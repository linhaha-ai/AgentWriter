<script setup lang="ts">
import { h, onMounted, ref } from 'vue'
import type { AdminStyleRequest, WriterStyle } from '../types'
import { fetchStyles, createStyle, updateStyle, deleteStyle } from '../api/admin'
import { errorMessage } from '../api/client'
import AppPageHeader from '../components/AppPageHeader.vue'
import {
  NButton,
  NCard,
  NDataTable,
  NDrawer,
  NDrawerContent,
  NForm,
  NFormItem,
  NInput,
  NPopconfirm,
  NSpace,
  NSwitch,
  NTag,
  useMessage,
} from 'naive-ui'

const message = useMessage()
const loading = ref(false)
const styles = ref<WriterStyle[]>([])
const showModal = ref(false)
const editingId = ref<number | null>(null)
const saving = ref(false)
const drawerWidth = Math.min(680, window.innerWidth - 24)

const form = ref<AdminStyleRequest>({
  name: '',
  description: '',
  tonePrompt: '',
  features: '',
  enabled: true,
})

const columns = [
  { title: 'ID', key: 'id', width: 60 },
  { title: '名称', key: 'name', width: 120 },
  { title: '描述', key: 'description', ellipsis: { tooltip: true } },
  { title: '特点', key: 'features', width: 260, ellipsis: { tooltip: true } },
  { title: '状态', key: 'enabled', width: 80, render: (row: WriterStyle) => h(NTag, { type: row.enabled ? 'success' : 'warning' }, { default: () => row.enabled ? '启用' : '禁用' }) },
  { title: '操作', key: 'actions', width: 160, render: (row: WriterStyle) => h(NSpace, null, {
    default: () => [
      h(NButton, { size: 'small', quaternary: true, onClick: () => edit(row) }, { default: () => '编辑' }),
      h(NPopconfirm, { onPositiveClick: () => remove(row.id) }, {
        trigger: () => h(NButton, { size: 'small', quaternary: true, type: 'error' }, { default: () => '删除' }),
        default: () => '删除后无法恢复，确认继续吗？',
      }),
    ],
  })},
]

async function load() {
  loading.value = true
  try {
    styles.value = await fetchStyles()
  } catch (e) {
    message.error(errorMessage(e))
  } finally {
    loading.value = false
  }
}

function openCreate() {
  editingId.value = null
  form.value = { name: '', description: '', tonePrompt: '', features: '', enabled: true }
  showModal.value = true
}

function edit(row: WriterStyle) {
  editingId.value = row.id
  form.value = {
    name: row.name,
    description: row.description ?? '',
    tonePrompt: row.tonePrompt ?? '',
    features: row.features ?? '',
    enabled: row.enabled,
  }
  showModal.value = true
}

async function save() {
  saving.value = true
  try {
    if (editingId.value) {
      await updateStyle(editingId.value, form.value)
      message.success('更新成功')
    } else {
      await createStyle(form.value)
      message.success('创建成功')
    }
    showModal.value = false
    await load()
  } catch (e) {
    message.error(errorMessage(e))
  } finally {
    saving.value = false
  }
}

async function remove(id: number) {
  try {
    await deleteStyle(id)
    message.success('删除成功')
    await load()
  } catch (e) {
    message.error(errorMessage(e))
  }
}

onMounted(load)
</script>

<template>
  <main class="aw-page admin-config-page">
    <AppPageHeader title="写作风格" description="定义语气、表达方式与文章写作特征。" eyebrow="内容资产">
      <template #actions>
      <NButton type="primary" @click="openCreate">新建风格</NButton>
      </template>
    </AppPageHeader>

    <section class="admin-config-surface aw-surface">
      <div class="admin-config-surface__heading"><div><p class="aw-section-label">风格库</p><strong>{{ styles.length }} 种风格</strong></div><span>启用的风格决定生成文章的表达语气</span></div>
      <NDataTable :columns="columns" :data="styles" :loading="loading" striped :bordered="false" />
    </section>
  </main>

  <NDrawer v-model:show="showModal" :width="drawerWidth" placement="right">
    <NDrawerContent :title="editingId ? '编辑写作风格' : '新建写作风格'" closable>
    <NForm label-placement="top">
      <NFormItem label="名称" required>
        <NInput v-model:value="form.name" placeholder="风格名称，如「技术深度型」" />
      </NFormItem>
      <NFormItem label="描述">
        <NInput v-model:value="form.description" placeholder="简要说明风格适用场景" type="textarea" :rows="2" />
      </NFormItem>
      <NFormItem label="语气提示词">
        <NInput v-model:value="form.tonePrompt" placeholder="LLM 语气提示词，用于指导 AI 写作语气" type="textarea" :rows="6" />
      </NFormItem>
      <NFormItem label="特点">
        <NInput v-model:value="form.features" placeholder="逗号分隔的特点列表，如：专业,严谨,数据驱动" />
      </NFormItem>
      <NFormItem label="启用">
        <NSwitch v-model:value="form.enabled!" />
      </NFormItem>
      <div style="display: flex; justify-content: flex-end; gap: 12px; margin-top: 16px">
        <NButton @click="showModal = false" quaternary>取消</NButton>
        <NButton @click="save" type="primary" :loading="saving">保存</NButton>
      </div>
    </NForm>
    </NDrawerContent>
  </NDrawer>
</template>

<style scoped>
.admin-config-page { gap: var(--aw-spacing-5); }
.admin-config-surface { min-width: 0; padding: 18px; }
.admin-config-surface__heading { display: flex; align-items: flex-end; justify-content: space-between; gap: 16px; margin-bottom: 14px; }
.admin-config-surface__heading strong { display: block; margin-top: 3px; font-size: 14px; }
.admin-config-surface__heading span { color: var(--aw-text-secondary); font-size: 12px; }
@media (max-width: 600px) { .admin-config-surface { padding: 12px; } .admin-config-surface__heading { align-items: flex-start; flex-direction: column; } }
</style>
