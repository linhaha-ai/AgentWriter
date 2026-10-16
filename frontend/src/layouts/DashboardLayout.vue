<script setup lang="ts">
import { computed, h, onBeforeUnmount, onMounted, ref } from 'vue'
import type { Component } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { NIcon } from 'naive-ui'
import {
  GridOutline,
  DocumentTextOutline,
  TrendingUpOutline,
  SettingsOutline,
  MenuOutline,
  CreateOutline,
  ColorPaletteOutline,
} from '@vicons/ionicons5'

const router = useRouter()
const route = useRoute()
const collapsed = ref(false)
const compactViewport = ref(false)
const sidebarCollapsed = computed(() => compactViewport.value || collapsed.value)

function updateCompactViewport() {
  compactViewport.value = window.innerWidth <= 768
}

onMounted(() => {
  updateCompactViewport()
  window.addEventListener('resize', updateCompactViewport)
})

onBeforeUnmount(() => {
  window.removeEventListener('resize', updateCompactViewport)
})

type MenuOption = {
  readonly key: string
  readonly label: string
  readonly icon: Component
}

const menuOptions = [
  { key: '/dashboard', label: '运营概览', icon: GridOutline },
  { key: '/articles', label: '文章管理', icon: DocumentTextOutline },
  { key: '/articles/create-wizard', label: '创建文章', icon: CreateOutline },
  { key: '/topics', label: '热点话题', icon: TrendingUpOutline },
  { key: '/admin/templates', label: '模板管理', icon: DocumentTextOutline },
  { key: '/admin/styles', label: '风格管理', icon: ColorPaletteOutline },
  { key: '/settings', label: '系统设置', icon: SettingsOutline },
] satisfies readonly MenuOption[]

function handleMenuClick(key: string) {
  router.push(key)
}

function renderIcon(icon: Component) {
  return () => h(NIcon, null, { default: () => h(icon) })
}
</script>

<template>
  <n-layout style="height: 100vh">
    <n-layout has-sider style="height: 100vh">
      <!-- 侧边栏 -->
      <n-layout-sider
        bordered
        :collapsed="sidebarCollapsed"
        collapse-mode="width"
        :collapsed-width="64"
        :width="240"
        :native-scrollbar="false"
        class="dashboard-sider"
        style="height: 100vh"
      >
        <!-- 品牌标识 -->
        <div
          :style="{
            height: '60px',
            display: 'flex',
            alignItems: 'center',
            justifyContent: sidebarCollapsed ? 'center' : 'flex-start',
            padding: sidebarCollapsed ? '0' : '0 20px',
            borderBottom: '1px solid rgba(255,255,255,0.09)',
            cursor: 'pointer',
          }"
          @click="collapsed = !collapsed"
        >
          <n-gradient-text type="primary" :size="collapsed ? 20 : 22" style="font-weight: 700; white-space: nowrap">
            {{ sidebarCollapsed ? 'AW' : 'AgentWriter' }}
          </n-gradient-text>
        </div>

        <!-- 导航菜单 -->
        <n-menu
          :collapsed="sidebarCollapsed"
          :collapsed-width="64"
          :collapsed-icon-size="22"
          :options="menuOptions.map(o => ({
            key: o.key,
            label: o.label,
            icon: renderIcon(o.icon),
          }))"
          :value="route.path"
          @update:value="handleMenuClick"
          style="margin-top: 8px"
        />
      </n-layout-sider>

      <!-- 主内容区 -->
      <n-layout class="dashboard-main" style="height: 100vh; min-width: 0">
        <!-- 顶部栏 -->
        <n-layout-header bordered style="height: 60px; display: flex; align-items: center; padding: 0 24px; gap: 16px">
          <n-button quaternary size="small" @click="collapsed = !collapsed">
            <template #icon>
              <n-icon><MenuOutline /></n-icon>
            </template>
          </n-button>
          <n-breadcrumb>
            <n-breadcrumb-item>AgentWriter</n-breadcrumb-item>
            <n-breadcrumb-item>
              {{ menuOptions.find(o => o.key === route.path)?.label || '未知' }}
            </n-breadcrumb-item>
          </n-breadcrumb>
          <div style="flex: 1" />
          <n-tag class="dashboard-version" type="info" size="small" bordered>v0.1.0</n-tag>
        </n-layout-header>

        <!-- 页面内容 -->
        <n-layout-content
          :native-scrollbar="false"
          class="dashboard-content"
          style="height: calc(100vh - 60px); padding: 24px"
        >
          <router-view />
        </n-layout-content>
      </n-layout>
    </n-layout>
  </n-layout>
</template>

<style>
@media (max-width: 768px) {
  .dashboard-sider {
    max-width: 64px !important;
    min-width: 64px !important;
    width: 64px !important;
  }

  .dashboard-main {
    min-width: 0;
  }

  .dashboard-content {
    padding: 16px !important;
  }

  .dashboard-main > .n-layout-header {
    gap: 8px !important;
    padding: 0 16px !important;
  }
}

@media (max-width: 480px) {
  .dashboard-version {
    display: none;
  }
}
</style>
