<script setup lang="ts">
import { computed } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import type { Component } from 'vue'
import { Lock, SwitchButton, UserFilled, Monitor, Sunny, Moon } from '@element-plus/icons-vue'
import { useAuthStore } from '@/stores/auth'
import { useThemeStore } from '@/stores/theme'

const router = useRouter()
const auth = useAuthStore()
const theme = useThemeStore()

const rolesText = computed(() =>
  auth.user?.roles?.length ? auth.user.roles.join('、') : '未分配角色',
)

const themeLabels: Record<string, string> = {
  light: '浅色模式',
  dark: '深色模式',
  auto: '跟随系统',
}
const themeIcons: Record<string, Component> = {
  light: Sunny,
  dark: Moon,
  auto: Monitor,
}

async function onLogout() {
  try {
    await ElMessageBox.confirm('确定退出登录吗？', '退出登录', {
      confirmButtonText: '退出',
      cancelButtonText: '取消',
      type: 'warning',
    })
  } catch {
    return
  }
  await auth.logout()
  ElMessage.success('已退出登录')
  router.replace('/m/login')
}
</script>

<template>
  <div class="m-me">
    <div class="m-user-card">
      <el-avatar :size="56" :icon="UserFilled" />
      <div class="m-user-info">
        <div class="m-user-name">{{ auth.user?.name || auth.user?.username || '未登录' }}</div>
        <div class="m-user-account m-mono">{{ auth.user?.username || '-' }}</div>
        <div class="m-user-tags">
          <el-tag v-if="auth.user?.isAdmin" size="small" type="danger" effect="plain">
            管理员
          </el-tag>
          <el-tag v-for="role in auth.user?.roles || []" :key="role" size="small" effect="plain">
            {{ role }}
          </el-tag>
        </div>
      </div>
    </div>

    <div class="m-me-panel">
      <div class="m-panel-row">
        <span class="m-panel-label">角色</span>
        <span class="m-panel-value">{{ rolesText }}</span>
      </div>
      <div class="m-panel-row">
        <span class="m-panel-label">账号类型</span>
        <span class="m-panel-value">{{ auth.user?.isAdmin ? '管理员' : '普通用户' }}</span>
      </div>
      <div class="m-panel-row">
        <span class="m-panel-label">
          <el-icon :size="16"><component :is="themeIcons[theme.mode]" /></el-icon>
          界面主题
        </span>
        <el-select
          v-model="theme.mode"
          style="width: 140px"
          @change="theme.setMode($event)"
          :popper-append-to-body="true"
        >
          <el-option v-for="m in ['light', 'dark', 'auto']" :key="m" :label="themeLabels[m]" :value="m">
            <template #prefix>
              <el-icon :size="14"><component :is="themeIcons[m]" /></el-icon>
            </template>
          </el-option>
        </el-select>
      </div>
    </div>

    <el-button
      size="large"
      class="m-chpwd-btn"
      :icon="Lock"
      @click="router.push('/m/change-password')"
    >
      修改密码
    </el-button>

    <el-button
      type="danger"
      size="large"
      class="m-logout-btn"
      :icon="SwitchButton"
      @click="onLogout"
    >
      退出登录
    </el-button>
  </div>
</template>

<style scoped>
.m-me {
  padding: 16px 12px;
}

.m-user-card {
  background: var(--el-color-primary);
  border-radius: 12px;
  padding: 20px 16px;
  display: flex;
  align-items: center;
  gap: 14px;
  color: #fff;
}

.m-user-card :deep(.el-avatar) {
  background: rgba(255, 255, 255, 0.25);
  color: #fff;
  flex-shrink: 0;
}

.m-user-info {
  min-width: 0;
}

.m-user-name {
  font-size: 17px;
  font-weight: 600;
}

.m-user-account {
  font-size: 12px;
  opacity: 0.85;
  margin-top: 2px;
}

.m-user-tags {
  margin-top: 6px;
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
}

.m-me-panel {
  background: var(--el-bg-color);
  border-radius: 10px;
  padding: 6px 14px;
  margin-top: 12px;
}

.m-panel-row {
  display: flex;
  justify-content: space-between;
  align-items: center;
  font-size: 14px;
  line-height: 44px;
  border-bottom: 1px solid #f5f7fa;
}

.m-panel-row:last-child {
  border-bottom: none;
}

.m-panel-label {
  color: var(--el-text-color-secondary);
}

.m-panel-value {
  color: var(--el-text-color-primary);
  text-align: right;
  word-break: break-all;
}

.m-mono {
  font-family: Consolas, Menlo, monospace;
}

.m-chpwd-btn {
  width: 100%;
  margin-top: 20px;
}

.m-logout-btn {
  width: 100%;
  margin-top: 12px;
}
</style>
