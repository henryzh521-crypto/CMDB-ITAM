import { fileURLToPath, URL } from 'node:url'
import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'
import AutoImport from 'unplugin-auto-import/vite'
import Components from 'unplugin-vue-components/vite'
import { ElementPlusResolver } from 'unplugin-vue-components/resolvers'
import { readFileSync } from 'node:fs'

const pkg = JSON.parse(readFileSync(new URL('./package.json', import.meta.url), 'utf-8')) as { version: string }

export default defineConfig({
  // v3.4：前端版本号（关于对话框里与后端版本对照，排查「前后端不是同一版本」）
  define: {
    __APP_VERSION__: JSON.stringify(pkg.version),
  },
  plugins: [
    vue(),
    AutoImport({
      resolvers: [ElementPlusResolver()],
    }),
    Components({
      resolvers: [ElementPlusResolver()],
    }),
  ],
  resolve: {
    alias: {
      '@': fileURLToPath(new URL('./src', import.meta.url)),
    },
  },
  server: {
    port: 5173,
    proxy: {
      '/api': {
        target: 'http://localhost:8081',
        changeOrigin: true,
      },
    },
  },
  // v3.4：vite preview（npm run preview，默认 4173）同样代理 /api，
  // 使生产构建可以直接对着后端做端到端验证（e2e 套件依赖）
  preview: {
    port: 4173,
    proxy: {
      '/api': {
        target: 'http://localhost:8081',
        changeOrigin: true,
      },
    },
  },
  build: {
    sourcemap: false,
    rollupOptions: {
      output: {
        // 函数形式：只把“实际被引用到的”模块归入对应 chunk。
        // v3.2 用数组形式 echarts: ['echarts'] 会把 echarts 全量入口强行打进来（634KB），
        // 抵消了 utils/echarts.ts 的按需引入。
        manualChunks(id: string) {
          if (!id.includes('node_modules')) return undefined
          if (/node_modules\/(echarts|zrender)\//.test(id)) return 'echarts'
          if (/node_modules\/three\//.test(id)) return 'three'
          if (/node_modules\/(@vue|vue|vue-router|pinia)\//.test(id)) return 'vue-vendor'
          return undefined
        },
      },
    },
  },
})
