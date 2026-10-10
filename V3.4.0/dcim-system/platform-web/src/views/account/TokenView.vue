<script setup lang="ts">
/** 个人访问令牌：供脚本 / 第三方系统以本人身份调用平台 API（Authorization: Bearer dcim_pat_…） */
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { useAuthStore } from '@/stores/auth'
import { createToken, fetchTokens, revokeToken } from '@/api/platform'
import type { ApiToken } from '@/api/platform'

const auth = useAuthStore()
const isAdmin = computed(() => !!auth.user?.isAdmin)
const all = ref(false)
const rows = ref<ApiToken[]>([])
const loading = ref(false)

async function load() {
  loading.value = true
  try {
    rows.value = await fetchTokens(all.value)
  } catch {
    // ignore
  } finally {
    loading.value = false
  }
}

const dialog = ref(false)
const form = reactive({ name: '', readOnly: true, expireDays: 90 as number | undefined })
const created = ref<string | null>(null)
async function create() {
  if (!form.name.trim()) return ElMessage.warning('请填写用途')
  try {
    const r = await createToken({ name: form.name.trim(), readOnly: form.readOnly, expireDays: form.expireDays || undefined })
    created.value = r.token
    void load()
  } catch {
    // ignore
  }
}
function openCreate() {
  Object.assign(form, { name: '', readOnly: true, expireDays: 90 })
  created.value = null
  dialog.value = true
}
function copy(t: string) {
  navigator.clipboard?.writeText(t).then(
    () => ElMessage.success('已复制'),
    () => ElMessage.warning('复制失败，请手工选择复制'),
  )
}
async function revoke(t: ApiToken) {
  try {
    await ElMessageBox.confirm(`吊销令牌「${t.name}」？使用该令牌的脚本将立即无法访问`, '吊销', { type: 'warning' })
    await revokeToken(t.id)
    ElMessage.success('已吊销')
    void load()
  } catch {
    // 取消
  }
}
const STATE: Record<string, { l: string; t: 'success' | 'info' | 'warning' }> = {
  ACTIVE: { l: '有效', t: 'success' },
  EXPIRED: { l: '已过期', t: 'warning' },
  REVOKED: { l: '已吊销', t: 'info' },
}
function fmt(v?: string) {
  return v ? v.replace('T', ' ').slice(0, 16) : '-'
}
const origin = window.location.origin
onMounted(load)
</script>

<template>
  <div class="page-card">
    <div class="page-title">个人访问令牌</div>
    <el-alert type="info" :closable="false" style="margin-bottom: 12px">
      令牌代表你的身份调用平台 API，权限与你的账号一致；建议默认只读并设置有效期。令牌不能用于登录、修改口令或管理令牌。
      用法：<code>curl -H "Authorization: Bearer dcim_pat_…" {{ origin }}/api/assets?page=1&amp;size=20</code>
    </el-alert>
    <div class="page-toolbar">
      <el-checkbox v-if="isAdmin" v-model="all" @change="load">查看所有用户的令牌</el-checkbox>
      <div style="flex: 1"></div>
      <el-button type="primary" :icon="'Plus'" @click="openCreate">新建令牌</el-button>
    </div>
    <el-table v-loading="loading" :data="rows" border size="small">
      <el-table-column prop="name" label="用途" min-width="160" />
      <el-table-column v-if="all" prop="username" label="所属用户" width="110" />
      <el-table-column label="令牌" width="170"><template #default="{ row }"><code>{{ row.tokenPrefix }}…</code></template></el-table-column>
      <el-table-column label="权限" width="80">
        <template #default="{ row }"><el-tag size="small" :type="row.readOnly ? 'info' : 'warning'">{{ row.readOnly ? '只读' : '读写' }}</el-tag></template>
      </el-table-column>
      <el-table-column label="状态" width="80">
        <template #default="{ row }"><el-tag size="small" :type="STATE[row.state].t">{{ STATE[row.state].l }}</el-tag></template>
      </el-table-column>
      <el-table-column label="到期" width="140"><template #default="{ row }">{{ row.expiresAt ? fmt(row.expiresAt) : '永久' }}</template></el-table-column>
      <el-table-column label="最近使用" width="200">
        <template #default="{ row }">{{ fmt(row.lastUsedAt) }} <span class="sub">{{ row.lastUsedIp || '' }}</span></template>
      </el-table-column>
      <el-table-column label="创建" width="140"><template #default="{ row }">{{ fmt(row.createdAt) }}</template></el-table-column>
      <el-table-column label="操作" width="80">
        <template #default="{ row }">
          <el-button v-if="row.state === 'ACTIVE'" link type="danger" @click="revoke(row)">吊销</el-button>
        </template>
      </el-table-column>
    </el-table>

    <el-dialog v-model="dialog" title="新建令牌" width="560px" :close-on-click-modal="!created">
      <template v-if="!created">
        <el-form label-width="80px">
          <el-form-item label="用途" required><el-input v-model="form.name" placeholder="如：资产同步脚本、BI 报表" /></el-form-item>
          <el-form-item label="权限">
            <el-radio-group v-model="form.readOnly">
              <el-radio :value="true">只读（推荐）</el-radio>
              <el-radio :value="false">读写</el-radio>
            </el-radio-group>
          </el-form-item>
          <el-form-item label="有效期">
            <el-select v-model="form.expireDays" style="width: 160px">
              <el-option :value="30" label="30 天" />
              <el-option :value="90" label="90 天" />
              <el-option :value="365" label="1 年" />
              <el-option :value="undefined" label="永久（不推荐）" />
            </el-select>
          </el-form-item>
        </el-form>
      </template>
      <template v-else>
        <el-alert type="warning" :closable="false" show-icon title="令牌只显示这一次，请立即复制保存" style="margin-bottom: 12px" />
        <el-input :model-value="created" readonly>
          <template #append><el-button @click="copy(created!)">复制</el-button></template>
        </el-input>
      </template>
      <template #footer>
        <el-button v-if="!created" @click="dialog = false">取消</el-button>
        <el-button v-if="!created" type="primary" @click="create">生成</el-button>
        <el-button v-else type="primary" @click="dialog = false">我已保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.sub {
  font-size: 12px;
  color: var(--el-text-color-secondary);
}
code {
  font-family: ui-monospace, Menlo, Consolas, monospace;
  font-size: 12px;
}
</style>
