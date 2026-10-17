import { computed, ref } from 'vue'
import { defineStore } from 'pinia'

export type ThemeMode = 'light' | 'dark'

export const THEME_STORAGE_KEY = 'agentwriter-theme'

function readInitialMode(): ThemeMode {
  try {
    const saved = localStorage.getItem(THEME_STORAGE_KEY)
    if (saved === 'light' || saved === 'dark') return saved
  } catch {
    /* localStorage 不可用时回退默认 */
  }
  return 'light'
}

export const useThemeStore = defineStore('theme', () => {
  const mode = ref<ThemeMode>(readInitialMode())
  const isDark = computed(() => mode.value === 'dark')

  /** 将当前主题写入 <html data-theme> 与 color-scheme，驱动 CSS 变量与 Naive UI 原生控件 */
  function apply() {
    document.documentElement.dataset.theme = mode.value
    document.documentElement.style.colorScheme = mode.value
  }

  function persist() {
    try {
      localStorage.setItem(THEME_STORAGE_KEY, mode.value)
    } catch {
      /* 忽略持久化失败 */
    }
  }

  function setMode(next: ThemeMode) {
    mode.value = next
    persist()
    apply()
  }

  function toggle() {
    setMode(isDark.value ? 'light' : 'dark')
  }

  // 初始化时立即应用，保证与 index.html 内联脚本一致
  apply()

  return { mode, isDark, setMode, toggle }
})
