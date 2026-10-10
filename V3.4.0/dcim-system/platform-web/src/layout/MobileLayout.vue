<script setup lang="ts">
import { computed, onMounted, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import type { Component } from 'vue'
import { Search, List, Finished, User, Monitor, Sunny, Moon } from '@element-plus/icons-vue'
import { useAuthStore } from '@/stores/auth'
import { useThemeStore } from '@/stores/theme'

interface MobileTab {
  path: string
  title: string
  icon: Component
}

const route = useRoute()
const router = useRouter()
const auth = useAuthStore()
const theme = useThemeStore()

/** 底部 tab 按桌面端同源模块权限显隐（assets=查资产 / inventories=盘点 / workorders=审批） */
const tabs = computed<MobileTab[]>(() => {
  const list: MobileTab[] = []
  if (auth.canRead('assets')) list.push({ path: '/m/assets', title: '查资产', icon: Search })
  if (auth.canRead('inventories')) list.push({ path: '/m/inventory', title: '盘点', icon: List })
  if (auth.canRead('workorders')) list.push({ path: '/m/approvals', title: '审批', icon: Finished })
  list.push({ path: '/m/me', title: '我的', icon: User })
  return list
})

/** 登录页全屏展示，隐藏标题栏与 tabbar */
const isPublic = computed(() => !!route.meta.public)
const pageTitle = computed(() => (route.meta.title as string) || '')

function isActive(tab: MobileTab): boolean {
  return route.path === tab.path || route.path.startsWith(`${tab.path}/`)
}

onMounted(() => {
  theme.init()
})

watch(() => theme.mode, () => {
  // Theme changes are handled by the store's applyTheme
})
</script>

<template>
  <div class="m-root">
    <template v-if="!isPublic">
      <header class="m-header">{{ pageTitle }}</header>
      <main class="m-body">
        <router-view />
      </main>
      <nav class="m-tabbar">
        <div
          v-for="tab in tabs"
          :key="tab.path"
          class="m-tab"
          :class="{ 'is-active': isActive(tab) }"
          @click="tab.path !== route.path && $router.push(tab.path)"
        >
          <el-icon :size="22">
            <component :is="tab.icon" />
          </el-icon>
          <span class="m-tab-title">{{ tab.title }}</span>
        </div>
      </nav>
    </template>
    <template v-else>
      <router-view />
    </template>
  </div>
</template>

<style scoped>
.m-root {
  max-width: 560px;
  margin: 0 auto;
  min-height: 100vh;
  background: var(--el-fill-color-light);
}

.m-header {
  position: fixed;
  top: 0;
  left: 0;
  right: 0;
  max-width: 560px;
  margin: 0 auto;
  height: 48px;
  line-height: 48px;
  text-align: center;
  font-size: 16px;
  font-weight: 600;
  color: #fff;
  background: var(--el-color-primary);
  z-index: 100;
}

.m-body {
  padding-top: calc(48px + env(safe-area-inset-top, 0px));
  padding-bottom: calc(62px + env(safe-area-inset-bottom, 0px));
  min-height: 100vh;
}

.m-tabbar {
  position: fixed;
  bottom: 0;
  left: 0;
  right: 0;
  max-width: 560px;
  margin: 0 auto;
  display: flex;
  height: calc(56px + env(safe-area-inset-bottom, 0px));
  padding-bottom: env(safe-area-inset-bottom, 0px);
  background: var(--el-bg-color);
  border-top: 1px solid var(--el-border-color-lighter);
  z-index: 100;
}

.m-tab {
  flex: 1;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 2px;
  color: var(--el-text-color-secondary);
  cursor: pointer;
  -webkit-tap-highlight-color: transparent;
  user-select: none;
}

.m-tab.is-active {
  color: var(--el-color-primary);
}

.m-tab-title {
  font-size: 11px;
}
</style>
