<script setup lang="ts">
import { computed } from 'vue'
import { darkTheme, dateZhCN, zhCN } from 'naive-ui'
import type { GlobalThemeOverrides } from 'naive-ui'
import AppShell from './layouts/AppShell.vue'
import { useThemeStore } from './stores/theme'
import './styles/theme.css'

const themeStore = useThemeStore()

const lightThemeOverrides: GlobalThemeOverrides = {
  common: {
    primaryColor: '#18a058',
    primaryColorHover: '#36ad6a',
    primaryColorPressed: '#0c7a43',
    infoColor: '#2080f0',
    warningColor: '#f0a020',
    errorColor: '#d64545',
    bodyColor: '#f3f5f7',
    cardColor: '#ffffff',
    modalColor: '#ffffff',
    popoverColor: '#ffffff',
    borderColor: 'rgba(20, 40, 33, 0.09)',
    textColorBase: 'rgba(23, 35, 31, 0.93)',
    textColor2: 'rgba(46, 62, 55, 0.72)',
    borderRadius: '8px',
  },
  Card: {
    borderRadius: '8px',
    paddingMedium: '18px',
  },
  Menu: {
    itemHeight: '40px',
    itemTextColor: 'rgba(46, 62, 55, 0.72)',
    itemTextColorActive: '#0f8a4d',
    itemTextColorActiveHover: '#0f8a4d',
    itemTextColorHover: '#0f8a4d',
    itemIconColor: 'rgba(46, 62, 55, 0.6)',
    itemIconColorActive: '#18a058',
    itemIconColorActiveHover: '#18a058',
    itemIconColorHover: '#0f8a4d',
    itemColorActive: 'rgba(24, 160, 88, 0.1)',
    itemColorActiveHover: 'rgba(24, 160, 88, 0.13)',
    itemColorHover: 'rgba(20, 40, 33, 0.05)',
  },
}

const darkThemeOverrides: GlobalThemeOverrides = {
  common: {
    primaryColor: '#3bcf9b',
    primaryColorHover: '#5bdab1',
    primaryColorPressed: '#24b985',
    infoColor: '#5b9dff',
    warningColor: '#f3b35b',
    errorColor: '#ed7777',
    bodyColor: '#111619',
    cardColor: '#151b1f',
    modalColor: '#1a2227',
    popoverColor: '#1a2227',
    borderColor: 'rgba(224, 238, 234, 0.1)',
    textColorBase: 'rgba(245, 250, 248, 0.94)',
    textColor2: 'rgba(221, 232, 227, 0.64)',
    borderRadius: '8px',
  },
  Card: {
    borderRadius: '8px',
    paddingMedium: '18px',
  },
  Menu: {
    itemHeight: '40px',
    itemTextColor: 'rgba(221, 232, 227, 0.64)',
    itemTextColorActive: '#dff9ed',
    itemTextColorActiveHover: '#dff9ed',
    itemTextColorHover: '#dff9ed',
    itemIconColor: 'rgba(221, 232, 227, 0.56)',
    itemIconColorActive: '#3bcf9b',
    itemIconColorActiveHover: '#3bcf9b',
    itemIconColorHover: '#dff9ed',
    itemColorActive: 'rgba(59, 207, 155, 0.13)',
    itemColorActiveHover: 'rgba(59, 207, 155, 0.16)',
    itemColorHover: 'rgba(255, 255, 255, 0.055)',
  },
}

const naiveTheme = computed(() => (themeStore.isDark ? darkTheme : null))
const themeOverrides = computed<GlobalThemeOverrides>(() =>
  themeStore.isDark ? darkThemeOverrides : lightThemeOverrides,
)
</script>

<template>
  <n-config-provider :theme="naiveTheme" :theme-overrides="themeOverrides" :locale="zhCN" :date-locale="dateZhCN">
    <n-message-provider>
      <AppShell />
    </n-message-provider>
  </n-config-provider>
</template>

<style>
body {
  margin: 0;
  padding: 0;
  overflow: hidden;
}
#app {
  min-height: 100dvh;
}
</style>
