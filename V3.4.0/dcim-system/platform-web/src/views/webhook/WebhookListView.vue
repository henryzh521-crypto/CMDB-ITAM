<script setup lang="ts">
/**
 * Webhook / 消息推送管理（仅管理员可配置，其余有 system 读权限者只读）
 * - 推送格式：通用 JSON（HMAC 签名）/ 企业微信 / 钉钉 / 飞书群机器人
 * - 连通性测试、投递记录、失败重投
 */
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import type { FormInstance, FormRules } from 'element-plus'
import { useAuthStore } from '@/stores/auth'
import {
  createWebhookEndpoint,
  deleteWebhookEndpoint,
  fetchWebhookDeliveries,
  fetchWebhookEndpoints,
  fetchWebhookMeta,
  redeliverWebhook,
  testWebhookEndpoint,
  updateWebhookEndpoint,
} from '@/api/webhook'
import type { WebhookDeliveryItem, WebhookEndpointItem, WebhookMeta, WebhookPayload } from '@/api/webhook'

const auth = useAuthStore()
const isAdmin = computed(() => !!auth.user?.isAdmin)

const meta = ref<WebhookMeta>({ eventTypes: [], formats: [] })
const eventLabel = (v: string) => (v === '*' ? '全部事件' : meta.value.eventTypes.find((e) => e.value === v)?.label || v)
const formatLabel = (v: string) => meta.value.formats.find((f) => f.value === v)?.label || v

const DELIVERY_TAG: Record<string, 'success' | 'warning' | 'danger' | 'info'> = {
  SUCCESS: 'success',
  PENDING: 'info',
  RETRY: 'warning',
  DEAD: 'danger',
}
const DELIVERY_LABEL: Record<string, string> = { SUCCESS: '成功', PENDING: '待投递', RETRY: '重试中', DEAD: '失败' }

function fmt(v?: string | null) {
  return v ? v.replace('T', ' ').slice(0, 19) : '-'
}

/* ---------------- 列表 ---------------- */
const query = reactive({ page: 1, size: 20 })
const records = ref<WebhookEndpointItem[]>([])
const total = ref(0)
const loading = ref(false)

async function load() {
  loading.value = true
  try {
    const res = await fetchWebhookEndpoints(query)
    records.value = res.records ?? []
    total.value = res.total ?? 0
  } catch {
    // 拦截器已提示
  } finally {
    loading.value = false
  }
}

/* ---------------- 新增 / 编辑 ---------------- */
const dialog = ref(false)
const editing = ref<WebhookEndpointItem | null>(null)
const saving = ref(false)
const formRef = ref<FormInstance>()
const form = reactive({
  name: '',
  url: '',
  format: 'GENERIC',
  secret: '',
  clearSecret: false,
  eventTypes: [] as string[],
  enabled: true,
})
const rules = computed<FormRules>(() => ({
  name: [{ required: true, message: '请输入名称', trigger: 'blur' }],
  url: [{ required: !editing.value, message: '请输入推送地址', trigger: 'blur' }],
  eventTypes: [{ required: true, type: 'array', min: 1, message: '至少订阅一个事件', trigger: 'change' }],
}))

const secretHint = computed(() => {
  switch (form.format) {
    case 'DINGTALK_BOT':
      return '钉钉机器人「加签」密钥（SEC 开头），未启用加签可留空'
    case 'FEISHU_BOT':
      return '飞书机器人「签名校验」密钥，未启用可留空'
    case 'WECOM_BOT':
      return '企业微信机器人无需密钥（key 已包含在地址中）'
    default:
      return '用于 HMAC-SHA256 签名（请求头 X-DCIM-Signature），强烈建议设置'
  }
})

function openCreate() {
  editing.value = null
  Object.assign(form, { name: '', url: '', format: 'GENERIC', secret: '', clearSecret: false, eventTypes: [], enabled: true })
  dialog.value = true
}

function openEdit(row: WebhookEndpointItem) {
  editing.value = row
  // 地址里可能含机器人 key（列表已脱敏），编辑时留空表示不修改
  Object.assign(form, {
    name: row.name,
    url: '',
    format: row.format,
    secret: '',
    clearSecret: false,
    eventTypes: [...row.eventTypes],
    enabled: row.enabled,
  })
  dialog.value = true
}

async function save() {
  if (!formRef.value) return
  const ok = await formRef.value.validate().catch(() => false)
  if (!ok) return
  saving.value = true
  try {
    const payload: WebhookPayload = {
      name: form.name,
      format: form.format,
      eventTypes: form.eventTypes,
      enabled: form.enabled,
    }
    if (form.url.trim()) payload.url = form.url.trim()
    if (form.secret.trim()) payload.secret = form.secret.trim()
    if (editing.value && form.clearSecret) payload.clearSecret = true
    if (editing.value) {
      await updateWebhookEndpoint(editing.value.id, payload)
    } else {
      await createWebhookEndpoint(payload)
    }
    ElMessage.success('已保存')
    dialog.value = false
    await load()
  } catch {
    // 拦截器已提示
  } finally {
    saving.value = false
  }
}

async function toggle(row: WebhookEndpointItem, enabled: boolean) {
  try {
    await updateWebhookEndpoint(row.id, { enabled })
    ElMessage.success(enabled ? '已启用' : '已停用')
  } catch {
    // ignore
  } finally {
    await load()
  }
}

async function remove(row: WebhookEndpointItem) {
  try {
    await ElMessageBox.confirm(`删除推送「${row.name}」？`, '删除', { type: 'warning' })
    await deleteWebhookEndpoint(row.id)
    ElMessage.success('已删除')
    await load()
  } catch {
    // 取消
  }
}

const testing = ref<number | null>(null)
async function test(row: WebhookEndpointItem) {
  testing.value = row.id
  try {
    const d = await testWebhookEndpoint(row.id)
    if (d.status === 'SUCCESS') {
      ElMessage.success(`连通性测试成功（HTTP ${d.responseCode}，${d.durationMs} ms）`)
    } else {
      ElMessage.error(`测试失败：${d.lastError || 'HTTP ' + d.responseCode}`)
    }
    await load()
  } catch {
    // 拦截器已提示
  } finally {
    testing.value = null
  }
}

/* ---------------- 投递记录 ---------------- */
const drawer = ref(false)
const current = ref<WebhookEndpointItem | null>(null)
const dq = reactive({ page: 1, size: 20, status: '' })
const deliveries = ref<WebhookDeliveryItem[]>([])
const dTotal = ref(0)
const dLoading = ref(false)

async function openDeliveries(row: WebhookEndpointItem) {
  current.value = row
  dq.page = 1
  dq.status = ''
  drawer.value = true
  await loadDeliveries()
}

async function loadDeliveries() {
  if (!current.value) return
  dLoading.value = true
  try {
    const res = await fetchWebhookDeliveries(current.value.id, { page: dq.page, size: dq.size, status: dq.status || undefined })
    deliveries.value = res.records ?? []
    dTotal.value = res.total ?? 0
  } catch {
    // ignore
  } finally {
    dLoading.value = false
  }
}

async function redeliver(row: WebhookDeliveryItem) {
  try {
    await redeliverWebhook(row.id)
    ElMessage.success('已加入重投队列，几秒后自动投递')
    await loadDeliveries()
  } catch {
    // ignore
  }
}

onMounted(async () => {
  try {
    meta.value = await fetchWebhookMeta()
  } catch {
    // ignore
  }
  await load()
})
</script>

<template>
  <div class="page-card">
    <div class="page-title">消息推送 / Webhook</div>
    <el-alert
      type="info"
      :closable="false"
      show-icon
      style="margin-bottom: 12px"
      title="业务事件（资产变动、工单流转、库存预警、变更审批等）发生后自动推送到外部系统或企业微信/钉钉/飞书群；失败按 1分钟→5分钟→30分钟→2小时→6小时 自动重试。"
    />

    <div class="page-toolbar">
      <div style="flex: 1"></div>
      <el-button v-if="isAdmin" type="primary" @click="openCreate">新增推送</el-button>
    </div>

    <el-table v-loading="loading" :data="records" border stripe>
      <el-table-column prop="name" label="名称" min-width="140" />
      <el-table-column label="格式" width="130" show-overflow-tooltip>
        <template #default="{ row }">{{ formatLabel(row.format).replace(/（.*）/, '') }}</template>
      </el-table-column>
      <el-table-column prop="url" label="地址" min-width="200" show-overflow-tooltip />
      <el-table-column label="订阅事件" min-width="220">
        <template #default="{ row }">
          <el-tag v-for="e in row.eventTypes.slice(0, 3)" :key="e" size="small" style="margin: 0 4px 4px 0">{{ eventLabel(e) }}</el-tag>
          <el-tag v-if="row.eventTypes.length > 3" size="small" type="info">+{{ row.eventTypes.length - 3 }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="签名" width="70" align="center">
        <template #default="{ row }">
          <el-icon v-if="row.hasSecret" color="var(--el-color-success)"><Lock /></el-icon>
          <span v-else>-</span>
        </template>
      </el-table-column>
      <el-table-column label="最近投递" width="200">
        <template #default="{ row }">
          <template v-if="row.lastStatus">
            <el-tag :type="row.lastStatus === 'SUCCESS' ? 'success' : 'danger'" size="small">
              {{ row.lastStatus === 'SUCCESS' ? '成功' : `失败×${row.consecutiveFailures}` }}
            </el-tag>
            <span class="sub">{{ fmt(row.lastDeliveryAt).slice(5, 16) }}</span>
          </template>
          <span v-else class="sub">暂无</span>
        </template>
      </el-table-column>
      <el-table-column label="启用" width="80" align="center">
        <template #default="{ row }">
          <el-switch :model-value="row.enabled" :disabled="!isAdmin" @change="(v: string | number | boolean) => toggle(row, !!v)" />
        </template>
      </el-table-column>
      <el-table-column label="操作" width="230" fixed="right">
        <template #default="{ row }">
          <el-button link type="primary" @click="openDeliveries(row)">投递记录</el-button>
          <template v-if="isAdmin">
            <el-button link type="primary" :loading="testing === row.id" @click="test(row)">测试</el-button>
            <el-button link type="primary" @click="openEdit(row)">编辑</el-button>
            <el-button link type="danger" @click="remove(row)">删除</el-button>
          </template>
        </template>
      </el-table-column>
    </el-table>

    <div class="page-pagination">
      <el-pagination
        v-model:current-page="query.page"
        v-model:page-size="query.size"
        :total="total"
        layout="total, prev, pager, next"
        @current-change="load"
      />
    </div>

    <el-dialog v-model="dialog" :title="editing ? '编辑推送' : '新增推送'" width="640px" destroy-on-close>
      <el-form ref="formRef" :model="form" :rules="rules" label-width="96px">
        <el-form-item label="名称" prop="name">
          <el-input v-model="form.name" maxlength="100" />
        </el-form-item>
        <el-form-item label="推送格式">
          <el-select v-model="form.format" style="width: 100%">
            <el-option v-for="f in meta.formats" :key="f.value" :label="f.label" :value="f.value" />
          </el-select>
        </el-form-item>
        <el-form-item label="推送地址" prop="url">
          <el-input
            v-model="form.url"
            :placeholder="editing ? `当前：${editing.url}（留空不修改）` : 'https://...'"
          />
        </el-form-item>
        <el-form-item label="签名密钥">
          <el-input
            v-model="form.secret"
            type="password"
            show-password
            :placeholder="editing?.hasSecret ? '已设置（留空不修改）' : '可选'"
            :disabled="form.format === 'WECOM_BOT'"
          />
          <div class="tip">{{ secretHint }}</div>
          <el-checkbox v-if="editing?.hasSecret" v-model="form.clearSecret">清除已设置的密钥</el-checkbox>
        </el-form-item>
        <el-form-item label="订阅事件" prop="eventTypes">
          <el-checkbox-group v-model="form.eventTypes">
            <el-checkbox v-for="e in meta.eventTypes" :key="e.value" :value="e.value" style="width: 46%">
              {{ e.label }}
            </el-checkbox>
          </el-checkbox-group>
        </el-form-item>
        <el-form-item label="启用">
          <el-switch v-model="form.enabled" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialog = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="save">保存</el-button>
      </template>
    </el-dialog>

    <el-drawer v-model="drawer" size="760px" :title="`投递记录 · ${current?.name || ''}`">
      <div class="page-toolbar">
        <el-select v-model="dq.status" placeholder="状态" clearable style="width: 130px" @change="loadDeliveries">
          <el-option v-for="(l, k) in DELIVERY_LABEL" :key="k" :label="l" :value="k" />
        </el-select>
        <el-button @click="loadDeliveries">刷新</el-button>
      </div>
      <el-table v-loading="dLoading" :data="deliveries" border size="small">
        <el-table-column type="expand">
          <template #default="{ row }">
            <pre class="json">{{ JSON.stringify(row.payload, null, 2) }}</pre>
            <div v-if="row.responseSnippet" class="sub">响应：{{ row.responseSnippet }}</div>
          </template>
        </el-table-column>
        <el-table-column prop="id" label="#" width="70" />
        <el-table-column label="事件" min-width="140">
          <template #default="{ row }">{{ row.eventType === 'ping' ? '连通性测试' : eventLabel(row.eventType) }}</template>
        </el-table-column>
        <el-table-column label="状态" width="90">
          <template #default="{ row }">
            <el-tag :type="DELIVERY_TAG[row.status]" size="small">{{ DELIVERY_LABEL[row.status] || row.status }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="attempts" label="次数" width="60" />
        <el-table-column label="结果" min-width="150" show-overflow-tooltip>
          <template #default="{ row }">
            <span v-if="row.responseCode">HTTP {{ row.responseCode }}</span>
            <span v-if="row.lastError" class="err"> {{ row.lastError }}</span>
            <span v-if="row.durationMs != null" class="sub"> {{ row.durationMs }}ms</span>
          </template>
        </el-table-column>
        <el-table-column label="时间" width="150">
          <template #default="{ row }">{{ fmt(row.createdAt) }}</template>
        </el-table-column>
        <el-table-column v-if="isAdmin" label="" width="70">
          <template #default="{ row }">
            <el-button v-if="row.status !== 'PENDING'" link type="primary" @click="redeliver(row)">重投</el-button>
          </template>
        </el-table-column>
      </el-table>
      <div class="page-pagination">
        <el-pagination
          v-model:current-page="dq.page"
          :page-size="dq.size"
          :total="dTotal"
          layout="total, prev, pager, next"
          @current-change="loadDeliveries"
        />
      </div>
    </el-drawer>
  </div>
</template>

<style scoped>
.sub {
  color: var(--el-text-color-secondary);
  font-size: 12px;
  margin-left: 4px;
}
.err {
  color: var(--el-color-danger);
}
.tip {
  font-size: 12px;
  color: var(--el-text-color-secondary);
  line-height: 1.6;
  width: 100%;
}
.json {
  max-height: 260px;
  overflow: auto;
  background: var(--el-fill-color-light);
  padding: 8px;
  font-size: 12px;
  margin: 0 0 6px;
}
</style>
