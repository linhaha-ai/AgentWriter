<script setup lang="ts">
import { computed, h, onMounted, ref } from 'vue'
import {
  AddOutline,
  CloudUploadOutline,
  CreateOutline,
  ImageOutline,
  LinkOutline,
  PencilOutline,
  TrashOutline,
} from '@vicons/ionicons5'
import {
  NButton,
  NDataTable,
  NEmpty,
  NForm,
  NFormItem,
  NGrid,
  NGridItem,
  NIcon,
  NInput,
  NInputNumber,
  NModal,
  NPagination,
  NPopconfirm,
  NSelect,
  NTag,
  NText,
  useMessage,
} from 'naive-ui'
import type { DataTableColumns } from 'naive-ui'
import type { MediaAsset, MediaCategory, TagCount } from '../types'
import {
  createCategory,
  deleteAsset,
  deleteCategory,
  fetchCategories,
  fetchGallery,
  fetchTags,
  generateImage,
  saveImageUrl,
  updateAsset,
  updateCategory,
  uploadImage,
} from '../api/gallery'
import { errorMessage } from '../api/client'

const message = useMessage()

// ==================== 数据 ====================
const assets = ref<MediaAsset[]>([])
const total = ref(0)
const loading = ref(false)
const categories = ref<MediaCategory[]>([])
const tags = ref<TagCount[]>([])

const page = ref(1)
const pageSize = 24
const keyword = ref('')
const categoryFilter = ref<number | null>(null)
const tagFilter = ref<string | null>(null)

/** 来源徽章映射 */
const sourceLabel: Record<string, { text: string; type: 'success' | 'info' | 'default' }> = {
  GENERATED: { text: 'AI 生成', type: 'success' },
  UPLOADED: { text: '本地上传', type: 'info' },
  URL: { text: '站外链接', type: 'default' },
}

async function refreshAll() {
  await Promise.all([loadCategories(), loadTags()])
}

async function loadAssets() {
  loading.value = true
  try {
    const result = await fetchGallery({
      page: page.value - 1,
      pageSize,
      categoryId: categoryFilter.value,
      tag: tagFilter.value,
      keyword: keyword.value || null,
    })
    assets.value = result.items
    total.value = result.total
  } catch (err) {
    message.error(errorMessage(err))
  } finally {
    loading.value = false
  }
}

async function loadCategories() {
  try {
    categories.value = await fetchCategories()
  } catch (err) {
    message.error(errorMessage(err))
  }
}

async function loadTags() {
  try {
    tags.value = await fetchTags()
  } catch (err) {
    message.error(errorMessage(err))
  }
}

function applyFilter() {
  page.value = 1
  void loadAssets()
}

onMounted(() => {
  void loadAssets()
  void refreshAll()
})

// ==================== 弹窗状态 ====================
const generateOpen = ref(false)
const uploadOpen = ref(false)
const linkOpen = ref(false)
const editOpen = ref(false)
const categoryOpen = ref(false)
const previewUrl = ref<string | null>(null)

const generating = ref(false)
const uploading = ref(false)

// 生成表单
const genForm = ref({ prompt: '', size: '1024x1024', name: '', categoryId: null as number | null, tags: '' })
// 上传表单
const uploadForm = ref({ file: null as File | null, name: '', categoryId: null as number | null, tags: '' })
// 链接表单
const linkForm = ref({ url: '', name: '', categoryId: null as number | null, tags: '' })
// 编辑表单
const editForm = ref({ id: 0, name: '', categoryId: null as number | null, tags: '' })

const sizeOptions = [
  { label: '方形 1024×1024', value: '1024x1024' },
  { label: '竖版 3:4（768×1344）', value: '768x1344' },
  { label: '横版 16:9（1344×768）', value: '1344x768' },
  { label: '小图 512×512', value: '512x512' },
]

function categoryOptions() {
  return categories.value.map(c => ({ label: c.name, value: c.id }))
}

function resetForm(target: Record<string, boolean>) {
  if (target.prompt) genForm.value.prompt = ''
  if (target.size) genForm.value.size = '1024x1024'
  if (target.url) linkForm.value.url = ''
  if (target.file) uploadForm.value.file = null
  if (target.name) {
    genForm.value.name = ''
    uploadForm.value.name = ''
    linkForm.value.name = ''
  }
  if (target.categoryId) {
    genForm.value.categoryId = null
    uploadForm.value.categoryId = null
    linkForm.value.categoryId = null
  }
  if (target.tags) {
    genForm.value.tags = ''
    uploadForm.value.tags = ''
    linkForm.value.tags = ''
  }
}

async function submitGenerate() {
  if (!genForm.value.prompt.trim()) {
    message.warning('请输入图片生成提示词')
    return
  }
  generating.value = true
  try {
    await generateImage({
      prompt: genForm.value.prompt,
      size: genForm.value.size,
      name: genForm.value.name || undefined,
      categoryId: genForm.value.categoryId,
      tags: genForm.value.tags,
    })
    message.success('图片生成成功')
    generateOpen.value = false
    resetForm({ prompt: true, size: true, name: true, categoryId: true, tags: true })
    await Promise.all([loadAssets(), refreshAll()])
  } catch (err) {
    message.error(errorMessage(err))
  } finally {
    generating.value = false
  }
}

function pickUploadFile() {
  const input = document.createElement('input')
  input.type = 'file'
  input.accept = 'image/png,image/jpeg,image/gif,image/webp'
  input.onchange = () => {
    const file = input.files?.[0]
    if (!file) return
    uploadForm.value.file = file
    if (!uploadForm.value.name) uploadForm.value.name = file.name.replace(/\.[^.]+$/, '')
    uploadOpen.value = true
  }
  input.click()
}

async function submitUpload() {
  if (!uploadForm.value.file) {
    message.warning('请选择要上传的图片文件')
    return
  }
  uploading.value = true
  try {
    await uploadImage({
      file: uploadForm.value.file,
      name: uploadForm.value.name || undefined,
      categoryId: uploadForm.value.categoryId,
      tags: uploadForm.value.tags,
    })
    message.success('图片上传成功')
    uploadOpen.value = false
    resetForm({ file: true, name: true, categoryId: true, tags: true })
    await Promise.all([loadAssets(), refreshAll()])
  } catch (err) {
    message.error(errorMessage(err))
  } finally {
    uploading.value = false
  }
}

async function submitLink() {
  if (!linkForm.value.url.trim()) {
    message.warning('请输入图片链接')
    return
  }
  try {
    await saveImageUrl({
      url: linkForm.value.url,
      name: linkForm.value.name || undefined,
      categoryId: linkForm.value.categoryId,
      tags: linkForm.value.tags,
    })
    message.success('链接已保存')
    linkOpen.value = false
    resetForm({ url: true, name: true, categoryId: true, tags: true })
    await Promise.all([loadAssets(), refreshAll()])
  } catch (err) {
    message.error(errorMessage(err))
  }
}

function openEdit(asset: MediaAsset) {
  editForm.value = {
    id: asset.id,
    name: asset.name ?? '',
    categoryId: asset.categoryId,
    tags: asset.tags.join(', '),
  }
  editOpen.value = true
}

async function submitEdit() {
  try {
    await updateAsset(editForm.value.id, {
      name: editForm.value.name || undefined,
      categoryId: editForm.value.categoryId,
      tags: editForm.value.tags,
    })
    message.success('素材已更新')
    editOpen.value = false
    await Promise.all([loadAssets(), refreshAll()])
  } catch (err) {
    message.error(errorMessage(err))
  }
}

async function removeAsset(id: number) {
  try {
    await deleteAsset(id)
    message.success('素材已删除')
    await Promise.all([loadAssets(), refreshAll()])
  } catch (err) {
    message.error(errorMessage(err))
  }
}

function openPreview(asset: MediaAsset) {
  previewUrl.value = asset.url
}

function closePreview(show: boolean) {
  if (!show) previewUrl.value = null
}

// ==================== 分类管理 ====================
const catColumns = computed<DataTableColumns<MediaCategory>>(() => [
  { title: '名称', key: 'name', width: 160 },
  { title: '说明', key: 'description' },
  {
    title: '素材数', key: 'assetCount', width: 90,
    render: row => h(NTag, { size: 'small', bordered: false }, { default: () => String(row.assetCount) }),
  },
  {
    title: '操作', key: 'actions', width: 150,
    render: row => h('div', { style: 'display:flex;gap:8px' }, [
      h(NButton, { size: 'tiny', quaternary: true, onClick: () => startEditCategory(row) }, { default: () => '重命名' }),
      h(NPopconfirm, { onPositiveClick: () => removeCategory(row.id) }, {
        trigger: () => h(NButton, { size: 'tiny', quaternary: true, type: 'error' }, { default: () => '删除' }),
        default: () => '删除后该分类下的素材将变为未分类，确定？',
      }),
    ]),
  },
])

const newCategoryName = ref('')
const editingCategory = ref<{ id: number; name: string } | null>(null)

async function submitNewCategory() {
  if (!newCategoryName.value.trim()) {
    message.warning('请输入分类名称')
    return
  }
  try {
    await createCategory({ name: newCategoryName.value })
    message.success('分类已创建')
    newCategoryName.value = ''
    await loadCategories()
  } catch (err) {
    message.error(errorMessage(err))
  }
}

function startEditCategory(category: MediaCategory) {
  editingCategory.value = { id: category.id, name: category.name }
}

async function saveCategoryEdit() {
  if (!editingCategory.value) return
  if (!editingCategory.value.name.trim()) {
    message.warning('分类名称不能为空')
    return
  }
  try {
    await updateCategory(editingCategory.value.id, { name: editingCategory.value.name })
    message.success('分类已更新')
    editingCategory.value = null
    await loadCategories()
  } catch (err) {
    message.error(errorMessage(err))
  }
}

async function removeCategory(id: number) {
  try {
    await deleteCategory(id)
    message.success('分类已删除')
    await loadCategories()
    if (categoryFilter.value === id) {
      categoryFilter.value = null
      await loadAssets()
    }
  } catch (err) {
    message.error(errorMessage(err))
  }
}

// ==================== 来源与时间辅助 ====================
function sourceBadge(source: string) {
  return sourceLabel[source] ?? { text: source, type: 'default' as const }
}

function formatBytes(bytes: number | null | undefined): string {
  if (bytes == null) return ''
  if (bytes < 1024) return `${bytes} B`
  if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(1)} KB`
  return `${(bytes / 1024 / 1024).toFixed(1)} MB`
}
</script>

<template>
  <div class="gallery-page">
    <div class="gallery-toolbar">
      <div class="gallery-toolbar__filters">
        <n-input v-model:value="keyword" clearable placeholder="搜索名称或提示词" style="width: 220px" @keyup.enter="applyFilter" @clear="applyFilter">
          <template #prefix><n-icon><ImageOutline /></n-icon></template>
        </n-input>
        <n-select
          v-model:value="categoryFilter"
          :options="categoryOptions()"
          placeholder="全部分类"
          clearable
          style="width: 150px"
          @update:value="applyFilter"
        />
        <n-select
          v-model:value="tagFilter"
          :options="tags.map(t => ({ label: `${t.tag}（${t.count}）`, value: t.tag }))"
          placeholder="全部标签"
          clearable
          filterable
          style="width: 170px"
          @update:value="applyFilter"
        />
        <n-button v-if="categoryFilter || tagFilter || keyword" quaternary size="small" @click="categoryFilter = null; tagFilter = null; keyword = ''; applyFilter()">
          清除筛选
        </n-button>
      </div>
      <div class="gallery-toolbar__actions">
        <n-button type="primary" @click="generateOpen = true">
          <template #icon><n-icon><CreateOutline /></n-icon></template>
          生成图片
        </n-button>
        <n-button @click="pickUploadFile">
          <template #icon><n-icon><CloudUploadOutline /></n-icon></template>
          上传图片
        </n-button>
        <n-button @click="linkOpen = true; resetForm({ url: true, name: true, categoryId: true, tags: true })">
          <template #icon><n-icon><LinkOutline /></n-icon></template>
          保存链接
        </n-button>
        <n-button quaternary @click="categoryOpen = true">
          <template #icon><n-icon><AddOutline /></n-icon></template>
          分类管理
        </n-button>
      </div>
    </div>

    <n-empty v-if="!loading && assets.length === 0" description="图库为空，试试生成、上传或保存一张图片" style="margin: 80px 0" />

    <n-grid v-else :cols="'2 s:3 m:4 l:5 xl:6'" responsive="screen" :x-gap="14" :y-gap="14" class="gallery-grid">
      <n-grid-item v-for="asset in assets" :key="asset.id">
        <div class="gallery-card" :class="{ 'gallery-card--external': asset.url.startsWith('http') }">
          <div class="gallery-card__media" @click="openPreview(asset)">
            <img :src="asset.url" :alt="asset.name ?? '图片素材'" loading="lazy" class="gallery-card__img" />
            <span class="gallery-card__source">
              <n-tag :type="sourceBadge(asset.source).type" size="tiny" :bordered="false">{{ sourceBadge(asset.source).text }}</n-tag>
            </span>
          </div>
          <div class="gallery-card__body">
            <div class="gallery-card__title" :title="asset.name ?? '未命名'">{{ asset.name ?? '未命名' }}</div>
            <div class="gallery-card__meta">
              <n-tag v-if="asset.categoryName" size="tiny" round :bordered="false" type="info">{{ asset.categoryName }}</n-tag>
              <n-tag v-for="tag in asset.tags.slice(0, 3)" :key="tag" size="tiny" :bordered="false">{{ tag }}</n-tag>
              <span v-if="asset.tags.length > 3" class="gallery-card__more">+{{ asset.tags.length - 3 }}</span>
            </div>
            <div class="gallery-card__footer">
              <span class="gallery-card__time">{{ asset.createdAt ?? '--' }}</span>
              <div class="gallery-card__ops">
                <n-button size="tiny" quaternary circle title="编辑" @click="openEdit(asset)">
                  <template #icon><n-icon><PencilOutline /></n-icon></template>
                </n-button>
                <n-popconfirm :on-positive-click="() => removeAsset(asset.id)">
                  <template #trigger>
                    <n-button size="tiny" quaternary circle type="error" title="删除">
                      <template #icon><n-icon><TrashOutline /></n-icon></template>
                    </n-button>
                  </template>
                  删除该图片素材？
                </n-popconfirm>
              </div>
            </div>
          </div>
        </div>
      </n-grid-item>
    </n-grid>

    <div class="gallery-pagination">
      <n-pagination
        v-model:page="page"
        :page-count="Math.max(1, Math.ceil(total / pageSize))"
        :page-slot="7"
        @update:page="loadAssets"
      />
      <n-text depth="3" style="font-size: 12px">共 {{ total }} 张图片</n-text>
    </div>

    <!-- 生成图片弹窗 -->
    <n-modal v-model:show="generateOpen" preset="card" title="AI 生成图片" style="width: 480px" :mask-closable="false">
      <n-form label-placement="top">
        <n-form-item label="生成提示词（必填）">
          <n-input v-model:value="genForm.prompt" type="textarea" :rows="4" placeholder="描述你想要的画面，例如：一只戴着程序员帽子的橘猫，在写代码，插画风格" />
        </n-form-item>
        <n-form-item label="尺寸">
          <n-select v-model:value="genForm.size" :options="sizeOptions" />
        </n-form-item>
        <n-form-item label="素材名称（可选）">
          <n-input v-model:value="genForm.name" placeholder="默认取提示词前 20 字" />
        </n-form-item>
        <n-form-item label="分类（可选）">
          <n-select v-model:value="genForm.categoryId" :options="categoryOptions()" clearable placeholder="选择分类" />
        </n-form-item>
        <n-form-item label="标签（可选，逗号分隔）">
          <n-input v-model:value="genForm.tags" placeholder="例如：海报,科技,蓝色" />
        </n-form-item>
      </n-form>
      <template #footer>
        <div class="gallery-modal-footer">
          <n-button @click="generateOpen = false">取消</n-button>
          <n-button type="primary" :loading="generating" @click="submitGenerate">生成</n-button>
        </div>
      </template>
    </n-modal>

    <!-- 上传图片弹窗 -->
    <n-modal v-model:show="uploadOpen" preset="card" title="上传图片" style="width: 480px" :mask-closable="false">
      <n-form label-placement="top">
        <n-form-item label="文件">
          <div class="gallery-upload-file">
            <n-button size="small" @click="pickUploadFile">重新选择</n-button>
            <span>{{ uploadForm.file?.name ?? '未选择文件' }}</span>
          </div>
        </n-form-item>
        <n-form-item label="素材名称（可选）">
          <n-input v-model:value="uploadForm.name" placeholder="默认取文件名" />
        </n-form-item>
        <n-form-item label="分类（可选）">
          <n-select v-model:value="uploadForm.categoryId" :options="categoryOptions()" clearable placeholder="选择分类" />
        </n-form-item>
        <n-form-item label="标签（可选，逗号分隔）">
          <n-input v-model:value="uploadForm.tags" placeholder="例如：封面,产品图" />
        </n-form-item>
      </n-form>
      <template #footer>
        <div class="gallery-modal-footer">
          <n-button @click="uploadOpen = false">取消</n-button>
          <n-button type="primary" :loading="uploading" @click="submitUpload">上传</n-button>
        </div>
      </template>
    </n-modal>

    <!-- 保存链接弹窗 -->
    <n-modal v-model:show="linkOpen" preset="card" title="保存图片链接" style="width: 480px" :mask-closable="false">
      <n-form label-placement="top">
        <n-form-item label="图片链接（必填）">
          <n-input v-model:value="linkForm.url" placeholder="https://example.com/image.png" />
        </n-form-item>
        <n-form-item label="素材名称（可选）">
          <n-input v-model:value="linkForm.name" />
        </n-form-item>
        <n-form-item label="分类（可选）">
          <n-select v-model:value="linkForm.categoryId" :options="categoryOptions()" clearable placeholder="选择分类" />
        </n-form-item>
        <n-form-item label="标签（可选，逗号分隔）">
          <n-input v-model:value="linkForm.tags" placeholder="例如：引用,素材" />
        </n-form-item>
      </n-form>
      <template #footer>
        <div class="gallery-modal-footer">
          <n-button @click="linkOpen = false">取消</n-button>
          <n-button type="primary" @click="submitLink">保存</n-button>
        </div>
      </template>
    </n-modal>

    <!-- 编辑素材弹窗 -->
    <n-modal v-model:show="editOpen" preset="card" title="编辑素材" style="width: 440px" :mask-closable="false">
      <n-form label-placement="top">
        <n-form-item label="素材名称">
          <n-input v-model:value="editForm.name" />
        </n-form-item>
        <n-form-item label="分类">
          <n-select v-model:value="editForm.categoryId" :options="categoryOptions()" clearable placeholder="选择分类" />
        </n-form-item>
        <n-form-item label="标签（逗号分隔）">
          <n-input v-model:value="editForm.tags" placeholder="例如：海报,科技" />
        </n-form-item>
      </n-form>
      <template #footer>
        <div class="gallery-modal-footer">
          <n-button @click="editOpen = false">取消</n-button>
          <n-button type="primary" @click="submitEdit">保存</n-button>
        </div>
      </template>
    </n-modal>

    <!-- 图片预览弹窗 -->
    <n-modal
      :show="previewUrl !== null"
      preset="card"
      title="图片预览"
      style="width: min(720px, 90vw)"
      :mask-closable="true"
      @update:show="closePreview"
    >
      <div class="gallery-preview">
        <img v-if="previewUrl" :src="previewUrl" alt="预览" />
      </div>
    </n-modal>

    <!-- 分类管理弹窗 -->
    <n-modal v-model:show="categoryOpen" preset="card" title="分类管理" style="width: 560px">
      <div class="gallery-category-form">
        <n-input v-model:value="newCategoryName" placeholder="新分类名称" style="flex: 1" @keyup.enter="submitNewCategory" />
        <n-button type="primary" @click="submitNewCategory">新增分类</n-button>
      </div>
      <n-data-table
        :columns="catColumns"
        :data="categories"
        :pagination="false"
        :bordered="false"
        size="small"
        :max-height="360"
      />
    </n-modal>
  </div>
</template>

<style src="../styles/views/gallery.css"></style>
