<script setup lang="ts">
/**
 * 维护窗口：计划内停机 / 割接期间，范围内资产的告警只记录不派单、不推送。
 * 实施中的变更自动视同维护窗口（只读展示）。
 */
import { computed, onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { useAuthStore } from '@/stores/auth'
import AssetPicker from '@/components/AssetPicker.vue'
import {
  createWindow,
  deleteWindow,
  endWindow,
  fetchMaintenanceChanges,
  fetchWindows,
  updateWindow,
} from '@/api/monitoring'
import type { MaintenanceWindowItem, WindowPayload } from '@/api/monitoring'

const auth = useAuthStore()
/** v3.4：删除需角色显式授予（后端同样校验） */
const canDelete = computed(() => auth.canDelete('monitoring'))
const router = useRouter()
const canWrite = computed(() => auth.canWrite('monitoring'))

const query = reactive({ page: 1, size: 20, activeOnly: false })
const records = ref<MaintenanceWindowItem[]>([])
const total = ref(0)
const loading = ref(false)
const changes = ref<any[]>([])

const STATE: Record<string, { label: string; type: 'info' | 'success' | 'warning' }> = {
  PLANNED: { label: '计划中', type: 'warning' },
  ACTIVE: { label: '生效中', type: 'success' },
  ENDED: { label: '已结束', type: 'info' },
}

function fmt(v?: string) {
  return v ? v.replace('T', ' ').slice(0, 16) : '-'
}

async function load() {
  loading.value = true
  try {
    const res = await fetchWindows({ ...query })
    records.value = res.records ?? []
    total.value = res.total ?? 0
    changes.value = await fetchMaintenanceChanges()
  } catch {
    // ignore
  } finally {
    loading.value = false
  }
}

function scopeText(w: MaintenanceWindowItem) {
  const parts: string[] = []
  if (w.roomCodes?.length) parts.push(`机房 ${w.roomCodes.join('、')}`)
  if (w.rackCodes?.length) parts.push(`机柜 ${w.rackCodes.slice(0, 5).join('、')}${w.rackCodes.length > 5 ? ` 等 ${w.rackCodes.length} 个` : ''}`)
  if (w.assetNos?.length) parts.push(`资产 ${w.assetNos.slice(0, 3).join('、')}${w.assetNos.length > 3 ? ` 等 ${w.assetNos.length} 台` : ''}`)
  return parts.length ? parts.join('；') : '全局（所有资产）'
}

/* ---------------- 新建 / 编辑 ---------------- */
const dialog = ref(false)
const editingId = ref<number | null>(null)
const saving = ref(false)
const form = reactive({
  name: '',
  range: [] as string[],
  assetNos: [] as string[],
  rackText: '',
  roomText: '',
  alertText: '',
  reason: '',
  global: false,
})

function pad(n: number) {
  return String(n).padStart(2, '0')
}
function localIso(d: Date) {
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}T${pad(d.getHours())}:${pad(d.getMinutes())}:00`
}
function split(s: string) {
  return s
    .split(/[\s,，;；]+/)
    .map((x) => x.trim())
    .filter(Boolean)
}

function openCreate() {
  editingId.value = null
  const now = new Date()
  now.setSeconds(0, 0)
  Object.assign(form, {
    name: '',
    range: [localIso(now), localIso(new Date(now.getTime() + 2 * 3600000))],
    assetNos: [],
    rackText: '',
    roomText: '',
    alertText: '',
    reason: '',
    global: false,
  })
  dialog.value = true
}

function openEdit(w: MaintenanceWindowItem) {
  editingId.value = w.id
  Object.assign(form, {
    name: w.name,
    range: [w.startsAt, w.endsAt],
    assetNos: [...(w.assetNos || [])],
    rackText: (w.rackCodes || []).join('\n'),
    roomText: (w.roomCodes || []).join('\n'),
    alertText: (w.alertnames || []).join('\n'),
    reason: w.reason || '',
    global: !w.assetNos?.length && !w.rackCodes?.length && !w.roomCodes?.length,
  })
  dialog.value = true
}

async function save() {
  if (!form.name.trim()) return ElMessage.warning('请填写名称')
  if (form.range?.length !== 2) return ElMessage.warning('请选择时间范围')
  const p: WindowPayload = {
    name: form.name.trim(),
    startsAt: form.range[0],
    endsAt: form.range[1],
    assetNos: form.global ? [] : form.assetNos,
    rackCodes: form.global ? [] : split(form.rackText),
    roomCodes: form.global ? [] : split(form.roomText),
    alertnames: split(form.alertText),
    reason: form.reason || undefined,
    global: form.global,
  }
  if (!p.global && !p.assetNos.length && !p.rackCodes.length && !p.roomCodes.length) {
    return ElMessage.warning('请指定维护范围，或勾选「全局维护」')
  }
  saving.value = true
  try {
    if (editingId.value) await updateWindow(editingId.value, p)
    else await createWindow(p)
    ElMessage.success('已保存')
    dialog.value = false
    void load()
  } catch {
    // ignore
  } finally {
    saving.value = false
  }
}

async function doEnd(w: MaintenanceWindowItem) {
  try {
    await ElMessageBox.confirm(`提前结束「${w.name}」？结束后范围内的新告警将恢复派单。`, '提前结束', { type: 'warning' })
    await endWindow(w.id)
    ElMessage.success('已结束')
    void load()
  } catch {
    // 取消
  }
}

async function doDelete(w: MaintenanceWindowItem) {
  try {
    await ElMessageBox.confirm(`删除维护窗口「${w.name}」？`, '删除', { type: 'warning' })
    await deleteWindow(w.id)
    ElMessage.success('已删除')
    void load()
  } catch {
    // 取消
  }
}

onMounted(load)
</script>

<template>
  <div class="page-card">
    <div class="page-title">维护窗口</div>

    <el-alert type="info" :closable="false" show-icon style="margin-bottom: 12px">
      维护期内，范围内资产的告警仍会记录（标记为「维护抑制」），但不自动派单、不推送消息。状态为「维修中」的资产、实施中的变更涉及的资产也会自动抑制。
    </el-alert>

    <template v-if="changes.length">
      <h4 class="sec">当前视同维护的变更</h4>
      <el-table :data="changes" size="small" border style="margin-bottom: 16px">
        <el-table-column label="变更单" width="170">
          <template #default="{ row }">
            <el-link type="primary" @click="router.push({ path: '/changes', query: { id: String(row.id) } })">{{ row.crNo }}</el-link>
          </template>
        </el-table-column>
        <el-table-column prop="title" label="标题" min-width="200" />
        <el-table-column prop="status" label="状态" width="110" />
        <el-table-column label="计划窗口" width="260">
          <template #default="{ row }">{{ fmt(row.scheduledStart) }} ~ {{ fmt(row.scheduledEnd) }}</template>
        </el-table-column>
        <el-table-column prop="assetCount" label="涉及资产" width="90" align="right" />
      </el-table>
    </template>

    <div class="page-toolbar">
      <el-checkbox v-model="query.activeOnly" @change="(query.page = 1), load()">只看生效中</el-checkbox>
      <div style="flex: 1"></div>
      <el-button v-if="canWrite" type="primary" :icon="'Plus'" @click="openCreate">新建维护窗口</el-button>
    </div>

    <el-table v-loading="loading" :data="records" border stripe>
      <el-table-column prop="name" label="名称" min-width="180" />
      <el-table-column label="状态" width="90">
        <template #default="{ row }">
          <el-tag :type="STATE[row.state].type" size="small">{{ STATE[row.state].label }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="时间" width="270">
        <template #default="{ row }">{{ fmt(row.startsAt) }} ~ {{ fmt(row.endsAt) }}</template>
      </el-table-column>
      <el-table-column label="范围" min-width="240">
        <template #default="{ row }">
          {{ scopeText(row) }}
          <div v-if="row.alertnames?.length" class="sub">仅告警：{{ row.alertnames.join('、') }}</div>
        </template>
      </el-table-column>
      <el-table-column prop="reason" label="原因" min-width="160" show-overflow-tooltip />
      <el-table-column prop="createdBy" label="创建人" width="100" />
      <el-table-column v-if="canWrite" label="操作" width="200" fixed="right">
        <template #default="{ row }">
          <el-button v-if="row.state !== 'ENDED'" link type="primary" @click="openEdit(row)">编辑</el-button>
          <el-button v-if="row.state !== 'ENDED'" link type="warning" @click="doEnd(row)">提前结束</el-button>
          <el-button v-if="canDelete" link type="danger" @click="doDelete(row)">删除</el-button>
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

    <el-dialog v-model="dialog" :title="editingId ? '编辑维护窗口' : '新建维护窗口'" width="620px">
      <el-form label-width="100px">
        <el-form-item label="名称" required>
          <el-input v-model="form.name" maxlength="200" placeholder="如：A 机房 UPS 年度维保" />
        </el-form-item>
        <el-form-item label="时间" required>
          <el-date-picker
            v-model="form.range"
            type="datetimerange"
            value-format="YYYY-MM-DDTHH:mm:ss"
            format="YYYY-MM-DD HH:mm"
            start-placeholder="开始"
            end-placeholder="结束"
            style="width: 100%"
          />
        </el-form-item>
        <el-form-item label="全局维护">
          <el-switch v-model="form.global" />
          <span class="sub" style="margin-left: 8px">打开后对所有资产生效（慎用，如监控系统自身升级）</span>
        </el-form-item>
        <template v-if="!form.global">
          <el-form-item label="资产">
            <AssetPicker v-model="form.assetNos as any" multiple style="width: 100%" />
          </el-form-item>
          <el-form-item label="机柜编码">
            <el-input v-model="form.rackText" type="textarea" :rows="2" placeholder="多个用换行、逗号或空格分隔" />
          </el-form-item>
          <el-form-item label="机房编码">
            <el-input v-model="form.roomText" type="textarea" :rows="2" placeholder="多个用换行、逗号或空格分隔" />
          </el-form-item>
        </template>
        <el-form-item label="仅限告警">
          <el-input v-model="form.alertText" placeholder="留空 = 所有告警；如 HostDown, NodeExporterDown" />
        </el-form-item>
        <el-form-item label="原因">
          <el-input v-model="form.reason" type="textarea" :rows="2" maxlength="500" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialog = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="save">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.sub {
  font-size: 12px;
  color: var(--el-text-color-secondary);
}
.sec {
  margin: 4px 0 8px;
  font-size: 14px;
}
</style>
