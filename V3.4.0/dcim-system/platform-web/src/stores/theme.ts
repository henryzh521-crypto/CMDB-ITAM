import { defineStore } from 'pinia'

const THEME_KEY = 'dcim-theme'
type ThemeMode = 'light' | 'dark' | 'auto'

function readTheme(): ThemeMode {
  try {
    const raw = localStorage.getItem(THEME_KEY)
    if (raw === 'light' || raw === 'dark' || raw === 'auto') return raw
  } catch {
  }
  return 'auto'
}

function applyTheme(mode: ThemeMode) {
  const root = document.documentElement
  if (mode === 'auto') {
    const prefersDark = window.matchMedia('(prefers-color-scheme: dark)').matches
    root.classList.toggle('dark', prefersDark)
  } else {
    root.classList.toggle('dark', mode === 'dark')
  }
}

let initialized = false

export const useThemeStore = defineStore('theme', {
  state: (): { mode: ThemeMode } => ({
    mode: readTheme(),
  }),

  actions: {
    init() {
      applyTheme(this.mode)
      if (initialized) return
      initialized = true
      // 跟随系统：只注册一次监听；手动切到 light/dark 后监听里按当前模式判断，不会覆盖用户选择
      const mq = window.matchMedia('(prefers-color-scheme: dark)')
      mq.addEventListener?.('change', () => {
        if (this.mode === 'auto') applyTheme('auto')
      })
    },

    setMode(mode: ThemeMode) {
      this.mode = mode
      try {
        localStorage.setItem(THEME_KEY, mode)
      } catch {
        // 隐私模式等场景 localStorage 不可用：仅本次会话生效
      }
      applyTheme(mode)
      window.dispatchEvent(new CustomEvent('dcim-theme-change', { detail: mode }))
    },

    toggle() {
      const next: ThemeMode = this.mode === 'light' ? 'dark' : this.mode === 'dark' ? 'auto' : 'light'
      this.setMode(next)
    },
  },
})