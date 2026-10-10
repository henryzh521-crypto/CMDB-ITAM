<script setup lang="ts">
/**
 * 巡检计划：按日/周/月自动生成「巡检」工单（含检查清单），执行人收到站内通知。
 */
import { computed, onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import type { FormInstance, FormRules } from 'element-plus'
import { useAuthStore } from '@/stores/auth'
import { fetchRacks, fetchRooms } from '@/api/room'
import { fetchApproverOptions } from '@/api/workorder'
import {
  createInspectionPlan,
  deleteInspectionPlan,
  fetchInspectionPlans,
  fetchInspectionRuns,
  runInspectionPlan,
  updateInspectionPlan,
} from '@/api/upgrade'
import type { InspectionPlan, InspectionPlanPayload, InspectionRun } from '@/api/upgrade'

const auth = useAuthStore()
/** v3.4：删除需角色显式授予（后端同样校验） */
const canDelete = computed(() => auth.canDelete('workorders'))
const router = useRouter()
const canWrite = computed(() => auth.canWrite('workorders'))

const FREQ_LABEL: Record<string, string> = { DAILY: '每天', WEEKLY: '每周', MONTHLY: '每月' }
const WEEKDAYS = ['周一', '周二', '周三', '周四', '周五', '周六', '周日']
const TEMPLATE = ['空调送/回风温度、湿度是否正常', 'UPS / 配电柜有无告警', '机柜门、盲板是否完好', '设备面板指示灯有无告警', '地面漏水、异味、异响检查', '消防与门禁设施状态']

function fmt(v?: string) {
  return v ? v.replace('T', ' ').slice(0, 16) : '-'
}

function cycleText(p: InspectionPlan) {
  const h = `${String(p.runHour).padStart(2, '0')}:00`
  if (p.frequency === 'WEEKLY') return `每${WEEKDAYS[(p.runWeekday || 1) - 1]} ${h}`
  if (p.frequency === 'MONTHLY') return `每月 ${p.runMonthDay} 日 ${h}`
  return `每天 ${h}`
}

/* ---------------- 列表 ---------------- */
const query = reactive({ page: 1, size: 20, keyword: '' })
const records = ref<InspectionPlan[]>([])
const total = ref(0)
const loading = ref(false)

async function load() {
  loading.value = true
  try {
    const res = await fetchInspectionPlans({ page: query.page, size: query.size, keyword: query.keyword || undefined })
    records.value = res.records ?? []
    total.value = res.total ?? 0
  } catch {
    // ignore
  } finally {
    loading.value = false
  }
}

/* ---------------- 编辑 ---------------- */
const dialog = ref(false)
const editingId = ref<number | null>(null)
const saving = ref(false)
const formRef = ref<FormInstance>()
const form = reactive({
  name: '',
  scopeType: 'ROOM',
  scopeCodes: [] as string[],
  frequency: 'WEEKLY',
  runHour: 9,
  runWeekday: 1,
  runMonthDay: 1,
  checklistText: '',
  priority: '中',
  assignee: '',
  enabled: true,
})
const rules: FormRules = {
  name: [{ required: true, message: '请输入计划名称', trigger: 'blur' }],
  scopeCodes: [{ required: true, type: 'array', min: 1, message: '请选择巡检对象', trigger: 'change' }],
}

const scopeOptions = ref<{ value: string; label: string }[]>([])
const scopeLoading = ref(false)
async function searchScope(kw: string) {
  scopeLoading.value = true
  try {
    if (form.scopeType === 'ROOM') {
      const r = await fetchRooms({ page: 1, size: 50, keyword: kw || undefined })
      scopeOptions.value = (r.records ?? []).map((x) => ({ value: x.roomCode, label: `${x.roomCode} ${x.name}` }))
    } else {
      const r = await fetchRacks({ page: 1, size: 50, keyword: kw || undefined })
      scopeOptions.value = (r.records ?? []).map((x) => ({ value: x.rackCode, label: `${x.rackCode} ${x.locationName || ''}` }))
    }
  } catch {
    scopeOptions.value = []
  } finally {
    scopeLoading.value = false
  }
}

const userOptions = ref<{ username: string; name?: string }[]>([])
async function searchUser(kw: string) {
  try {
    userOptions.value = await fetchApproverOptions(kw || undefined)
  } catch {
    userOptions.value = []
  }
}

function openCreate() {
  editingId.value = null
  Object.assign(form, {
    name: '',
    scopeType: 'ROOM',
    scopeCodes: [],
    frequency: 'WEEKLY',
    runHour: 9,
    runWeekday: 1,
    runMonthDay: 1,
    checklistText: TEMPLATE.join('\n'),
    priority: '中',
    assignee: '',
    enabled: true,
  })
  void searchScope('')
  void searchUser('')
  dialog.value = true
}

function openEdit(p: InspectionPlan) {
  editingId.value = p.id
  Object.assign(form, {
    name: p.name,
    scopeType: p.scopeType,
    scopeCodes: [...p.scopeCodes],
    frequency: p.frequency,
    runHour: p.runHour,
    runWeekday: p.runWeekday || 1,
    runMonthDay: p.runMonthDay || 1,
    checklistText: (p.checklist || []).join('\n'),
    priority: p.priority,
    assignee: p.assignee || '',
    enabled: p.enabled,
  })
  void searchScope('')
  void searchUser('')
  dialog.value = true
}

async function save() {
  if (!formRef.value) return
  const ok = await formRef.value.validate().catch(() => false)
  if (!ok) return
  const payload: InspectionPlanPayload = {
    name: form.name,
    scopeType: form.scopeType,
    scopeCodes: form.scopeCodes,
    frequency: form.frequency,
    runHour: form.runHour,
    runWeekday: form.frequency === 'WEEKLY' ? form.runWeekday : undefined,
    runMonthDay: form.frequency === 'MONTHLY' ? form.runMonthDay : undefined,
    checklist: form.checklistText.split('\n').map((s) => s.trim()).filter(Boolean),
    priority: form.priority,
    assignee: form.assignee || '',
    enabled: form.enabled,
  }
  saving.value = true
  try {
    if (editingId.value) await updateInspectionPlan(editingId.value, payload)
    else await createInspectionPlan(payload)
    ElMessage.success('已保存')
    dialog.value = false
    await load()
  } catch {
    // ignore
  } finally {
    saving.value = false
  }
}

async function toggle(p: InspectionPlan, enabled: boolean) {
  try {
    await updateInspectionPlan(p.id, { enabled })
  } catch {
    // ignore
  } finally {
    await load()
  }
}

async function runNow(p: InspectionPlan) {
  try {
    await ElMessageBox.confirm(`立即为「${p.name}」生成一张巡检工单？（不影响下次计划时间）`, '立即执行', { type: 'info' })
    const r = await runInspectionPlan(p.id)
    ElMessage.success(`已生成工单 ${r.woNo}`)
    await load()
  } catch {
    // 取消
  }
}

async function remove(p: InspectionPlan) {
  try {
    await ElMessageBox.confirm(`删除巡检计划「${p.name}」？已生成的工单不受影响。`, '删除', { type: 'warning' })
    await deleteInspectionPlan(p.id)
    ElMessage.success('已删除')
    await load()
  } catch {
    // 取消
  }
}

/* ---------------- 执行记录 ---------------- */
const drawer = ref(false)
const runs = ref<InspectionRun[]>([])
const current = ref<InspectionPlan | null>(null)
async function openRuns(p: InspectionPlan) {
  current.value = p
  drawer.value = true
  try {
    runs.value = await fetchInspectionRuns(p.id)
  } catch {
    runs.value = []
  }
}

onMounted(() => void load())
</script>

<template>
  <div class="page-card">
    <div class="page-title">巡检计划</div>

    <div class="page-toolbar">
      <el-input v-model="query.keyword" placeholder="计划编号 / 名称" clearable style="width: 220px" @keyup.enter="load" @clear="load" />
      <el-button type="primary" plain @click="load">查询</el-button>
      <div style="flex: 1"></div>
      <el-button v-if="canWrite" type="primary" @click="openCreate">新建计划</el-button>
    </div>

    <el-table v-loading="loading" :data="records" border stripe>
      <el-table-column prop="planNo" label="编号" width="100" />
      <el-table-column prop="name" label="计划名称" min-width="160" show-overflow-tooltip />
      <el-table-column label="巡检对象" min-width="180" show-overflow-tooltip>
        <template #default="{ row }">{{ row.scopeType === 'ROOM' ? '机房' : '机柜' }}：{{ row.scopeCodes.join('、') }}</template>
      </el-table-column>
      <el-table-column label="周期" width="140">
        <template #default="{ row }">{{ cycleText(row) }}</template>
      </el-table-column>
      <el-table-column label="检查项" width="80" align="center">
        <template #default="{ row }">{{ row.checklist?.length || 0 }}</template>
      </el-table-column>
      <el-table-column prop="assignee" label="执行人" width="100" />
      <el-table-column label="下次执行" width="140">
        <template #default="{ row }">{{ row.enabled ? fmt(row.nextRunAt) : '已停用' }}</template>
      </el-table-column>
      <el-table-column label="最近工单" width="170">
        <template #default="{ row }">
          <el-link v-if="row.lastWoNo" type="primary" @click="router.push(`/workorders/${row.lastWoNo}`)">{{ row.lastWoNo }}</el-link>
          <span v-else>-</span>
        </template>
      </el-table-column>
      <el-table-column label="启用" width="70" align="center">
        <template #default="{ row }">
          <el-switch :model-value="row.enabled" :disabled="!canWrite" @change="(v: string | number | boolean) => toggle(row, !!v)" />
        </template>
      </el-table-column>
      <el-table-column label="操作" width="230" fixed="right">
        <template #default="{ row }">
          <el-button link type="primary" @click="openRuns(row)">执行记录</el-button>
          <template v-if="canWrite">
            <el-button link type="primary" @click="runNow(row)">立即执行</el-button>
            <el-button link type="primary" @click="openEdit(row)">编辑</el-button>
            <el-button v-if="canDelete" link type="danger" @click="remove(row)">删除</el-button>
          </template>
        </template>
      </el-table-column>
    </el-table>

    <div class="page-pagination">
      <el-pagination v-model:current-page="query.page" :page-size="query.size" :total="total" layout="total, prev, pager, next" @current-change="load" />
    </div>

    <el-dialog v-model="dialog" :title="editingId ? '编辑巡检计划' : '新建巡检计划'" width="680px" destroy-on-close>
      <el-form ref="formRef" :model="form" :rules="rules" label-width="96px">
        <el-form-item label="计划名称" prop="name">
          <el-input v-model="form.name" maxlength="200" placeholder="如：A 机房周巡检" />
        </el-form-item>
        <el-form-item label="巡检对象" prop="scopeCodes">
          <el-radio-group v-model="form.scopeType" style="margin-right: 12px" @change="(form.scopeCodes = []), searchScope('')">
            <el-radio-button value="ROOM">机房</el-radio-button>
            <el-radio-button value="RACK">机柜</el-radio-button>
          </el-radio-group>
          <el-select
            v-model="form.scopeCodes"
            multiple
            filterable
            remote
            :remote-method="searchScope"
            :loading="scopeLoading"
            placeholder="搜索并选择"
            style="flex: 1"
          >
            <el-option v-for="o in scopeOptions" :key="o.value" :label="o.label" :value="o.value" />
          </el-select>
        </el-form-item>
        <el-form-item label="执行周期">
          <el-select v-model="form.frequency" style="width: 100px; margin-right: 8px">
            <el-option v-for="(l, k) in FREQ_LABEL" :key="k" :label="l" :value="k" />
          </el-select>
          <el-select v-if="form.frequency === 'WEEKLY'" v-model="form.runWeekday" style="width: 90px; margin-right: 8px">
            <el-option v-for="(d, i) in WEEKDAYS" :key="i" :label="d" :value="i + 1" />
          </el-select>
          <el-input-number v-if="form.frequency === 'MONTHLY'" v-model="form.runMonthDay" :min="1" :max="28" style="width: 110px; margin-right: 8px" />
          <span v-if="form.frequency === 'MONTHLY'" style="margin-right: 8px">日</span>
          <el-input-number v-model="form.runHour" :min="0" :max="23" style="width: 110px" />
          <span style="margin-left: 6px">点生成工单</span>
        </el-form-item>
        <el-form-item label="执行人">
          <el-select v-model="form.assignee" filterable remote clearable :remote-method="searchUser" placeholder="可选，生成后站内通知" style="width: 240px">
            <el-option v-for="u in userOptions" :key="u.username" :label="`${u.name || ''}（${u.username}）`" :value="u.username" />
          </el-select>
          <el-select v-model="form.priority" style="width: 90px; margin-left: 8px">
            <el-option v-for="p in ['高', '中', '低']" :key="p" :label="'优先级 ' + p" :value="p" />
          </el-select>
        </el-form-item>
        <el-form-item label="检查清单">
          <el-input v-model="form.checklistText" type="textarea" :rows="7" placeholder="每行一个检查项" />
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

    <el-drawer v-model="drawer" size="560px" :title="`执行记录 · ${current?.name || ''}`">
      <el-table :data="runs" border size="small">
        <el-table-column label="生成时间" width="150">
          <template #default="{ row }">{{ fmt(row.runAt) }}</template>
        </el-table-column>
        <el-table-column label="方式" width="80">
          <template #default="{ row }">{{ row.trigger === 'MANUAL' ? '手动' : '计划' }}</template>
        </el-table-column>
        <el-table-column label="工单" min-width="150">
          <template #default="{ row }">
            <el-link type="primary" @click="router.push(`/workorders/${row.woNo}`)">{{ row.woNo }}</el-link>
          </template>
        </el-table-column>
        <el-table-column prop="status" label="工单状态" width="90" />
      </el-table>
    </el-drawer>
  </div>
</template>
