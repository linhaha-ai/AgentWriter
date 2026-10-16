<script setup lang="ts">
import { computed, h, onBeforeUnmount, onMounted, ref } from 'vue'
import type { Component } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import {
  AddOutline,
  BookOutline,
  BrushOutline,
  CreateOutline,
  DocumentTextOutline,
  GridOutline,
  MenuOutline,
  OptionsOutline,
  SparklesOutline,
  TrendingUpOutline,
} from '@vicons/ionicons5'
import { NButton, NDrawer, NDrawerContent, NIcon } from 'naive-ui'

type NavigationItem = {
  readonly key: string
  readonly label: string
  readonly icon: Component
  readonly group: '工作台' | '内容资产' | '系统'
}

const router = useRouter()
const route = useRoute()
const collapsed = ref(false)
const mobileMenuOpen = ref(false)
const viewport = ref(window.innerWidth)

const navigation: readonly NavigationItem[] = [
  { key: '/dashboard', label: '运营概览', icon: GridOutline, group: '工作台' },
  { key: '/articles/create-wizard', label: '创建文章', icon: CreateOutline, group: '工作台' },
  { key: '/articles', label: '文章管理', icon: DocumentTextOutline, group: '内容资产' },
  { key: '/topics', label: '热点话题', icon: TrendingUpOutline, group: '内容资产' },
  { key: '/admin/templates', label: '内容模板', icon: BookOutline, group: '内容资产' },
  { key: '/admin/styles', label: '写作风格', icon: BrushOutline, group: '内容资产' },
  { key: '/settings', label: '系统设置', icon: OptionsOutline, group: '系统' },
]

const isMobile = computed(() => viewport.value < 768)
const isTablet = computed(() => viewport.value >= 768 && viewport.value < 1120)
const siderCollapsed = computed(() => collapsed.value || isTablet.value)
const currentItem = computed(() => navigation.find(item => route.path === item.key || route.path.startsWith(`${item.key}/`)))
const groupedNavigation = computed(() => ['工作台', '内容资产', '系统'].map(group => ({
  group,
  items: navigation.filter(item => item.group === group),
})))

function updateViewport() {
  viewport.value = window.innerWidth
  if (!isMobile.value) mobileMenuOpen.value = false
}

function navigate(path: string) {
  mobileMenuOpen.value = false
  void router.push(path)
}

function renderIcon(icon: Component) {
  return () => h(NIcon, { size: 19 }, { default: () => h(icon) })
}

onMounted(() => window.addEventListener('resize', updateViewport))
onBeforeUnmount(() => window.removeEventListener('resize', updateViewport))
</script>

<template>
  <n-layout class="app-shell" has-sider>
    <n-layout-sider
      v-if="!isMobile"
      class="app-shell__sider"
      :collapsed="siderCollapsed"
      :collapsed-width="72"
      :width="248"
      :native-scrollbar="false"
      bordered
    >
      <div class="app-brand" :class="{ 'app-brand--compact': siderCollapsed }" @click="navigate('/dashboard')">
        <span class="app-brand__mark"><n-icon :size="20"><SparklesOutline /></n-icon></span>
        <div v-if="!siderCollapsed" class="app-brand__copy">
          <strong>AgentWriter</strong>
          <span>内容生产工作台</span>
        </div>
      </div>
      <nav class="app-navigation" aria-label="主导航">
        <section v-for="section in groupedNavigation" :key="section.group" class="app-navigation__section">
          <p v-if="!siderCollapsed" class="app-navigation__label">{{ section.group }}</p>
          <n-menu
            :collapsed="siderCollapsed"
            :collapsed-width="72"
            :collapsed-icon-size="20"
            :value="currentItem?.key"
            :options="section.items.map(item => ({ key: item.key, label: item.label, icon: renderIcon(item.icon) }))"
            @update:value="navigate"
          />
        </section>
      </nav>
    </n-layout-sider>

    <n-layout class="app-shell__main">
      <n-layout-header class="app-topbar" bordered>
        <n-button quaternary circle aria-label="切换导航" @click="isMobile ? mobileMenuOpen = true : collapsed = !collapsed">
          <template #icon><n-icon><MenuOutline /></n-icon></template>
        </n-button>
        <div class="app-topbar__context">
          <span>AgentWriter</span>
          <i>/</i>
          <strong>{{ currentItem?.label || '内容工作台' }}</strong>
        </div>
        <div class="app-topbar__spacer" />
        <n-button type="primary" size="small" @click="navigate('/articles/create-wizard')">
          <template #icon><n-icon><AddOutline /></n-icon></template>
          创建文章
        </n-button>
      </n-layout-header>
      <n-layout-content class="app-shell__content" :native-scrollbar="false">
        <main class="app-shell__page"><router-view /></main>
      </n-layout-content>
    </n-layout>
  </n-layout>

  <n-drawer v-model:show="mobileMenuOpen" placement="left" :width="292">
    <n-drawer-content :native-scrollbar="false" body-content-style="padding: 0">
      <div class="app-brand app-brand--drawer" @click="navigate('/dashboard')">
        <span class="app-brand__mark"><n-icon :size="20"><SparklesOutline /></n-icon></span>
        <div class="app-brand__copy"><strong>AgentWriter</strong><span>内容生产工作台</span></div>
      </div>
      <nav class="app-navigation app-navigation--drawer" aria-label="移动端主导航">
        <section v-for="section in groupedNavigation" :key="section.group" class="app-navigation__section">
          <p class="app-navigation__label">{{ section.group }}</p>
          <n-menu
            :value="currentItem?.key"
            :options="section.items.map(item => ({ key: item.key, label: item.label, icon: renderIcon(item.icon) }))"
            @update:value="navigate"
          />
        </section>
      </nav>
    </n-drawer-content>
  </n-drawer>
</template>

<style scoped>
.app-shell {
  min-height: 100dvh;
  background: var(--aw-bg-canvas);
}

.app-shell__sider {
  border-right-color: var(--aw-border) !important;
  background: var(--aw-bg-shell);
}

.app-brand {
  display: flex;
  height: 76px;
  align-items: center;
  gap: 11px;
  padding: 0 20px;
  border-bottom: 1px solid var(--aw-border);
  cursor: pointer;
}

.app-brand--compact {
  justify-content: center;
  padding: 0;
}

.app-brand--drawer {
  height: 82px;
}

.app-brand__mark {
  display: grid;
  width: 36px;
  height: 36px;
  flex: 0 0 36px;
  place-items: center;
  border-radius: var(--aw-radius-sm);
  background: var(--aw-accent);
  color: #102019;
}

.app-brand__copy {
  display: flex;
  min-width: 0;
  flex-direction: column;
  gap: 2px;
}

.app-brand__copy strong {
  color: var(--aw-text-primary);
  font-size: 15px;
  font-weight: 700;
}

.app-brand__copy span {
  color: var(--aw-text-muted);
  font-size: 11px;
}

.app-navigation {
  padding: 16px 10px;
}

.app-navigation--drawer {
  padding-top: 20px;
}

.app-navigation__section + .app-navigation__section {
  margin-top: 20px;
}

.app-navigation__label {
  margin: 0 10px 7px;
  color: var(--aw-text-muted);
  font-size: 11px;
  font-weight: 600;
}

.app-shell__main {
  min-width: 0;
  background: var(--aw-bg-canvas);
}

.app-topbar {
  display: flex;
  height: 64px;
  align-items: center;
  gap: 12px;
  padding: 0 24px;
  border-bottom-color: var(--aw-border) !important;
  background: rgba(21, 27, 31, 0.9);
}

.app-topbar__context {
  display: flex;
  min-width: 0;
  gap: 8px;
  color: var(--aw-text-muted);
  font-size: 13px;
}

.app-topbar__context i {
  color: var(--aw-border-strong);
  font-style: normal;
}

.app-topbar__context strong {
  overflow: hidden;
  color: var(--aw-text-secondary);
  font-weight: 600;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.app-topbar__spacer {
  flex: 1;
}

.app-shell__content {
  height: calc(100dvh - 64px);
}

.app-shell__page {
  width: min(1440px, 100%);
  min-height: 100%;
  margin: 0 auto;
  padding: 28px;
}

@media (max-width: 767px) {
  .app-topbar {
    height: 58px;
    padding: 0 16px;
  }

  .app-topbar__context span,
  .app-topbar__context i {
    display: none;
  }

  .app-shell__content {
    height: calc(100dvh - 58px);
  }

  .app-shell__page {
    padding: 20px 16px 28px;
  }
}
</style>
