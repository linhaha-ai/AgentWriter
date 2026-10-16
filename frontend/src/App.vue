<script setup lang="ts">
import { darkTheme, dateZhCN, zhCN } from 'naive-ui'
import type { GlobalThemeOverrides } from 'naive-ui'
import AppShell from './layouts/AppShell.vue'
import './styles/theme.css'

const themeOverrides: GlobalThemeOverrides = {
  common: {
    primaryColor: '#3bcf9b',
    primaryColorHover: '#5bdaaE',
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
</script>

<template>
  <n-config-provider :theme="darkTheme" :theme-overrides="themeOverrides" :locale="zhCN" :date-locale="dateZhCN">
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
