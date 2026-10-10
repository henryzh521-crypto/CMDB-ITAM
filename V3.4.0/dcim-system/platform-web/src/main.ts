import { createApp } from 'vue'
import { createPinia } from 'pinia'
import { ElMessage } from 'element-plus'
import * as ElementPlusIconsVue from '@element-plus/icons-vue'
import 'element-plus/dist/index.css'
// Element Plus 暗色变量（html.dark 时生效）；v3.2 只切了自定义变量，表格/弹窗/输入框仍是浅色
import 'element-plus/theme-chalk/dark/css-vars.css'
import App from './App.vue'
import router from './router'
import { useThemeStore } from './stores/theme'
import './style.css'

const app = createApp(App)

// 全局错误边界：未捕获的组件错误只弹一次通用提示（防重复弹窗刷屏），
// 详细堆栈进 console；后续接入错误上报服务时在此扩展。
let globalErrorToastShown = false
app.config.errorHandler = (err, _instance, info) => {
  console.error('[global-error]', info, err)
  if (!globalErrorToastShown) {
    globalErrorToastShown = true
    ElMessage.error('页面发生异常，请刷新重试')
    window.setTimeout(() => {
      globalErrorToastShown = false
    }, 5000)
  }
}

// 未处理的 Promise 拒绝：仅记录日志，不打断用户操作（上报扩展点同上）
window.addEventListener('unhandledrejection', (e) => {
  console.error('[unhandled-rejection]', e.reason)
})

// 全局注册 Element Plus 图标
for (const [key, component] of Object.entries(ElementPlusIconsVue)) {
  app.component(key, component)
}

app.use(createPinia())
// 桌面端此前从未调用 init()：刷新后保存的主题不生效，只有手动切换时才应用
useThemeStore().init()
app.use(router)
app.mount('#app')
