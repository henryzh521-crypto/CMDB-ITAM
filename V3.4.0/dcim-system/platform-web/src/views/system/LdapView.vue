<script setup lang="ts">
/** 目录集成（LDAP / AD）：查看当前配置、测试连通与用户映射 */
import { onMounted, reactive, ref } from 'vue'
import { fetchLdap, testLdap } from '@/api/platform'

const cfg = ref<Record<string, any> | null>(null)
const t = reactive({ username: '', password: '' })
const result = ref<Record<string, any> | null>(null)
const testing = ref(false)
async function doTest() {
  testing.value = true
  result.value = null
  try {
    result.value = await testLdap(t.username, t.password || undefined)
  } catch {
    // ignore
  } finally {
    testing.value = false
  }
}
onMounted(async () => {
  try {
    cfg.value = await fetchLdap()
  } catch {
    // ignore
  }
})
</script>

<template>
  <div class="page-card">
    <div class="page-title">目录集成（LDAP / AD）</div>
    <el-alert type="info" :closable="false" style="margin-bottom: 12px">
      登录顺序：本地账号（含内置 admin）优先；本地不存在的用户名交给目录认证，成功后自动开通账号（姓名 / 邮箱 / 部门随目录同步，
      配置了组映射时角色以目录组为准）。目录账号的密码在域控修改。配置项为部署环境变量 LDAP_*（见 docs/DEPLOY.md），修改后重启后端生效。
    </el-alert>
    <el-descriptions v-if="cfg" :column="1" border size="small" style="max-width: 860px">
      <el-descriptions-item label="状态">
        <el-tag :type="cfg.enabled ? 'success' : 'info'" size="small">{{ cfg.enabled ? '已启用' : '未启用' }}</el-tag>
      </el-descriptions-item>
      <el-descriptions-item label="服务器">{{ cfg.url || '-' }}</el-descriptions-item>
      <el-descriptions-item label="Base DN">{{ cfg.baseDn || '-' }}</el-descriptions-item>
      <el-descriptions-item label="服务账号">{{ cfg.bindDn || '（匿名搜索）' }} <span v-if="cfg.bindPasswordSet" class="sub">· 口令已配置</span></el-descriptions-item>
      <el-descriptions-item label="用户过滤器"><code>{{ cfg.userFilter }}</code></el-descriptions-item>
      <el-descriptions-item label="姓名属性">{{ cfg.nameAttr }}</el-descriptions-item>
      <el-descriptions-item label="组 → 角色">{{ cfg.groupRoleMap }}</el-descriptions-item>
    </el-descriptions>

    <h4 class="sec">连通性测试</h4>
    <el-form inline>
      <el-form-item label="用户名"><el-input v-model="t.username" style="width: 180px" /></el-form-item>
      <el-form-item label="口令"><el-input v-model="t.password" type="password" show-password placeholder="留空 = 仅搜索" style="width: 200px" /></el-form-item>
      <el-form-item><el-button type="primary" :loading="testing" :disabled="!cfg?.enabled || !t.username" @click="doTest">测试</el-button></el-form-item>
    </el-form>
    <el-descriptions v-if="result" :column="1" border size="small" style="max-width: 860px">
      <el-descriptions-item label="结果">
        <el-tag :type="result.found ? 'success' : 'danger'" size="small">
          {{ result.found ? (result.authenticated ? '认证成功' : '找到用户') : t.password ? '用户不存在或口令错误' : '未找到用户' }}
        </el-tag>
      </el-descriptions-item>
      <template v-if="result.found">
        <el-descriptions-item label="DN">{{ result.dn }}</el-descriptions-item>
        <el-descriptions-item label="姓名">{{ result.name }}</el-descriptions-item>
        <el-descriptions-item label="邮箱">{{ result.email || '-' }}</el-descriptions-item>
        <el-descriptions-item label="部门">{{ result.department || '-' }}</el-descriptions-item>
        <el-descriptions-item label="所属组"><div v-for="g in result.groups" :key="g">{{ g }}</div></el-descriptions-item>
        <el-descriptions-item label="映射角色">{{ (result.mappedRoles || []).join('、') || '（无，新用户将使用默认角色）' }}</el-descriptions-item>
      </template>
    </el-descriptions>
  </div>
</template>

<style scoped>
.sec {
  margin: 20px 0 8px;
  font-size: 14px;
}
.sub {
  font-size: 12px;
  color: var(--el-text-color-secondary);
}
code {
  font-family: ui-monospace, Menlo, Consolas, monospace;
  font-size: 12px;
}
</style>
