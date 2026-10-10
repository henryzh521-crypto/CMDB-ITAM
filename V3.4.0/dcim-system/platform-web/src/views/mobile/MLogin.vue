<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { Box, User } from '@element-plus/icons-vue'
import { useAuthStore } from '@/stores/auth'
import { fetchWecomConfig } from '@/api/wecom'

const route = useRoute()
const router = useRouter()
const auth = useAuthStore()

const devMode = ref(false)
const username = ref('')
const submitting = ref(false)
const booting = ref(true)

function goAfterLogin() {
  const redirect = typeof route.query.redirect === 'string' ? route.query.redirect : ''
  // 仅允许站内移动端路径，防止开放跳转
  const target = redirect.startsWith('/m/') ? redirect : '/m/assets'
  router.replace(target)
}

async function doWecomLogin(code: string) {
  submitting.value = true
  try {
    await auth.loginWithWecom(code)
    // 清除地址栏中的 OAuth code，避免刷新后重复登录
    window.history.replaceState(null, '', window.location.pathname)
    goAfterLogin()
  } catch {
    // 拦截器已提示；停留本页，不再触发 OAuth 跳转避免死循环
    booting.value = false
  } finally {
    submitting.value = false
  }
}

function goWecomOauth(corpId: string, agentId: string) {
  const redirectUri = encodeURIComponent(`${window.location.origin}/m/login`)
  window.location.href =
    `https://open.weixin.qq.com/connect/oauth2/authorize?appid=${corpId}` +
    `&redirect_uri=${redirectUri}&response_type=code&scope=snsapi_base&agentid=${agentId}#wechat_redirect`
}

async function onDevLogin() {
  const name = username.value.trim()
  if (!name) {
    ElMessage.warning('请输入用户名')
    return
  }
  submitting.value = true
  try {
    await auth.loginWithDev(name)
    ElMessage.success('登录成功')
    goAfterLogin()
  } catch {
    // 拦截器已提示
  } finally {
    submitting.value = false
  }
}

onMounted(async () => {
  // 企微 OAuth 回跳：地址栏带 ?code=
  const code = new URLSearchParams(window.location.search).get('code')
  if (code) {
    void doWecomLogin(code)
    return
  }
  try {
    const config = await fetchWecomConfig()
    if (config.enabled && config.corpId && config.agentId) {
      goWecomOauth(config.corpId, config.agentId)
      return
    }
    // 未接入企微：仅当后端四重闸门判定 dev-login 可用时才展示免密登录。
    // 不再「拿不到配置/未接企微就当作开发模式」——那是免密入口泄漏到生产的主要来源。
    devMode.value = config.devLoginEnabled === true
  } catch {
    // 配置拉取失败一律不显示免密登录（fail-closed）
    devMode.value = false
  } finally {
    booting.value = false
  }
})
</script>

<template>
  <div class="m-login">
    <div class="m-login-hero">
      <div class="m-login-logo">
        <el-icon :size="34"><Box /></el-icon>
      </div>
      <h1 class="m-login-title">智算资产管理平台</h1>
      <p class="m-login-sub">移动端</p>
    </div>

    <div v-if="booting" class="m-login-tip">正在检查登录方式…</div>
    <div v-else-if="submitting" class="m-login-tip" v-loading="true" element-loading-text="登录中…" />

    <div v-if="!booting && devMode" class="m-login-panel">
      <el-alert
        title="开发模式"
        description="企业微信未接入，输入用户名直接登录（仅限开发环境）"
        type="warning"
        :closable="false"
        show-icon
      />
      <el-input
        v-model="username"
        size="large"
        placeholder="请输入用户名"
        :prefix-icon="User"
        clearable
        :disabled="submitting"
        @keyup.enter="onDevLogin"
      />
      <el-button
        type="primary"
        size="large"
        class="m-login-btn"
        :loading="submitting"
        @click="onDevLogin"
      >
        登录
      </el-button>
    </div>
    <div v-else-if="!booting" class="m-login-tip">
      当前未开放网页登录，请使用企业微信扫码，或联系管理员。
    </div>
  </div>
</template>

<style scoped>
.m-login {
  min-height: 100vh;
  display: flex;
  flex-direction: column;
  align-items: center;
  padding: 96px 28px 40px;
  background: linear-gradient(180deg, var(--el-color-primary) 0%, #f5f7fa 42%);
}

.m-login-hero {
  text-align: center;
  margin-bottom: 40px;
}

.m-login-logo {
  width: 72px;
  height: 72px;
  margin: 0 auto 16px;
  border-radius: 18px;
  background: rgba(255, 255, 255, 0.22);
  color: #fff;
  display: flex;
  align-items: center;
  justify-content: center;
}

.m-login-title {
  margin: 0;
  font-size: 20px;
  font-weight: 600;
  color: #fff;
}

.m-login-sub {
  margin: 6px 0 0;
  font-size: 13px;
  color: rgba(255, 255, 255, 0.85);
}

.m-login-tip {
  margin-top: 12px;
  min-height: 48px;
  font-size: 14px;
  color: var(--el-text-color-secondary);
  width: 100%;
}

.m-login-panel {
  width: 100%;
  background: var(--el-bg-color);
  border-radius: 12px;
  padding: 20px 16px;
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.m-login-btn {
  width: 100%;
}
</style>
