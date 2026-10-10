<script setup lang="ts">
/**
 * 变更管理（ITIL Change Enablement）
 * - 列表：编号/标题检索、状态筛选、「我发起的」
 * - 新建/编辑：类型、风险、计划窗口、关联资产、回退方案（申请人取登录人）
 * - 详情抽屉：时间线、冲突变更、影响面预估、按状态与身份显示可执行操作
 */
import { computed, onMounted, reactive, ref } from 'vue'
import { useRoute } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import type { FormInstance, FormRules } from 'element-plus'
import AssetPicker from '@/components/AssetPicker.vue'
import { useAuthStore } from '@/stores/auth'
import {
  CHANGE_STATUS_LABEL,
  CHANGE_TYPE_LABEL,
  RISK_LABEL,
  cabApproveChangeRequest,
  cancelChangeRequest,
  completeChangeRequest,
  createChangeRequest,
  deleteChangeRequest,
  failChangeRequest,
  fetchChangeRequest,
  fetchChangeRequests,
  startChangeRequest,
  submitChangeRequest,
  updateChangeRequest,
} from '@/api/change'
import type { ChangeDetail, ChangeDraft, ChangeRequestItem } from '@/api/change'
import { CI_TYPE_LABEL } from '@/api/ci'

const auth = useAuthStore()
const route = useRoute()
const canWrite = computed(() => auth.canWrite('changes'))
const me = computed(() => auth.user?.username || '')
const isAdmin = computed(() => !!auth.user?.isAdmin)

const STATUS_TAG: Record<string, 'info' | 'warning' | 'success' | 'danger' | 'primary'> = {
  DRAFT: 'info',
  PENDING_CAB: 'warning',
  APPROVED: 'success',
  REJECTED: 'danger',
  IN_PROGRESS: 'primary',
  COMPLETED: 'success',
  FAILED: 'danger',
  CANCELLED: 'info',
}
const RISK_TAG: Record<string, 'success' | 'warning' | 'danger' | 'info'> = {
  LOW: 'success',
  MEDIUM: 'info',
  HIGH: 'warning',
  CRITICAL: 'danger',
}

function fmt(v?: string | null) {
  return v ? v.replace('T', ' ').slice(0, 16) : '-'
}

/* ---------------- 列表 ---------------- */
const query = reactive({ page: 1, size: 20, keyword: '', status: '', mine: false })
const records = ref<ChangeRequestItem[]>([])
const total = ref(0)
const loading = ref(false)

async function load() {
  loading.value = true
  try {
    const res = await fetchChangeRequests({
      page: query.page,
      size: query.size,
      keyword: query.keyword || undefined,
      status: query.status || undefined,
      mine: query.mine || undefined,
    })
    records.value = res.records ?? []
    total.value = res.total ?? 0
  } catch {
    // 拦截器已提示
  } finally {
    loading.value = false
  }
}

function search() {
  query.page = 1
  void load()
}

/* ---------------- 新建 / 编辑 ---------------- */
const dialogVisible = ref(false)
const editingId = ref<number | null>(null)
const saving = ref(false)
const formRef = ref<FormInstance>()
const emptyForm = (): ChangeDraft => ({
  title: '',
  description: '',
  changeType: 'NORMAL',
  riskLevel: 'MEDIUM',
  impactScope: '',
  rollbackPlan: '',
  scheduledStart: null,
  scheduledEnd: null,
  assetNos: [],
})
const form = reactive<ChangeDraft>(emptyForm())
const rules: FormRules = {
  title: [{ required: true, message: '请输入标题', trigger: 'blur' }],
  changeType: [{ required: true, message: '请选择变更类型', trigger: 'change' }],
}

function openCreate() {
  editingId.value = null
  Object.assign(form, emptyForm())
  dialogVisible.value = true
}

async function openEdit(id: number) {
  const d = await fetchChangeRequest(id)
  editingId.value = id
  Object.assign(form, {
    title: d.title,
    description: d.description || '',
    changeType: d.changeType,
    riskLevel: d.riskLevel,
    impactScope: d.impactScope || '',
    rollbackPlan: d.rollbackPlan || '',
    scheduledStart: d.scheduledStart || null,
    scheduledEnd: d.scheduledEnd || null,
    assetNos: d.assetNos || [],
  })
  dialogVisible.value = true
}

async function save() {
  if (!formRef.value) return
  const ok = await formRef.value.validate().catch(() => false)
  if (!ok) return
  saving.value = true
  try {
    if (editingId.value) {
      await updateChangeRequest(editingId.value, { ...form })
      ElMessage.success('已保存')
    } else {
      await createChangeRequest({ ...form })
      ElMessage.success('变更请求已创建（草稿）')
    }
    dialogVisible.value = false
    await load()
    if (editingId.value && detail.value?.id === editingId.value) await openDetail(editingId.value)
  } catch {
    // 拦截器已提示
  } finally {
    saving.value = false
  }
}

/* ---------------- 详情 / 流转 ---------------- */
const drawer = ref(false)
const detail = ref<ChangeDetail | null>(null)
const detailLoading = ref(false)

async function openDetail(id: number) {
  drawer.value = true
  detailLoading.value = true
  try {
    detail.value = await fetchChangeRequest(id)
  } catch {
    drawer.value = false
  } finally {
    detailLoading.value = false
  }
}

const isOwner = computed(() => !!detail.value && detail.value.requester === me.value)
const can = computed(() => {
  const d = detail.value
  if (!d || !canWrite.value) return {} as Record<string, boolean>
  const ownerOrAdmin = isOwner.value || isAdmin.value
  return {
    edit: ownerOrAdmin && (d.status === 'DRAFT' || d.status === 'REJECTED'),
    submit: ownerOrAdmin && (d.status === 'DRAFT' || d.status === 'REJECTED'),
    review: d.status === 'PENDING_CAB' && (isAdmin.value || !isOwner.value),
    start: d.status === 'APPROVED',
    finish: d.status === 'IN_PROGRESS',
    cancel: ownerOrAdmin && ['DRAFT', 'PENDING_CAB', 'APPROVED', 'REJECTED'].includes(d.status),
    remove: auth.canDelete('changes') && (d.status === 'DRAFT' || d.status === 'CANCELLED'),
  }
})

async function act(fn: () => Promise<unknown>, msg: string) {
  if (!detail.value) return
  try {
    await fn()
    ElMessage.success(msg)
    await Promise.all([openDetail(detail.value.id), load()])
  } catch {
    // 拦截器已提示 / 用户取消
  }
}

async function promptText(title: string, label: string, required: boolean): Promise<string | undefined> {
  const { value } = await ElMessageBox.prompt(label, title, {
    inputType: 'textarea',
    inputValidator: (v: string) => (!required || (v && v.trim().length > 0) ? true : '此项必填'),
  })
  return value?.trim() || undefined
}

const onSubmit = () => act(() => submitChangeRequest(detail.value!.id), '已提交')
const onStart = () => act(() => startChangeRequest(detail.value!.id), '已开始实施')
const onApprove = () =>
  act(async () => {
    const c = await promptText('批准变更', '审批意见（可选）', false)
    await cabApproveChangeRequest(detail.value!.id, { approved: true, comment: c })
  }, '已批准')
const onReject = () =>
  act(async () => {
    const c = await promptText('驳回变更', '驳回原因（必填）', true)
    await cabApproveChangeRequest(detail.value!.id, { approved: false, comment: c })
  }, '已驳回')
const onComplete = () =>
  act(async () => {
    const c = await promptText('实施完成', '实施结果说明（可选）', false)
    await completeChangeRequest(detail.value!.id, c)
  }, '变更已完成')
const onFail = () =>
  act(async () => {
    const c = await promptText('变更失败', '失败原因与回退情况（必填）', true)
    await failChangeRequest(detail.value!.id, c!)
  }, '已记录为失败回退')
const onCancel = () =>
  act(async () => {
    const c = await promptText('取消变更', '取消原因（可选）', false)
    await cancelChangeRequest(detail.value!.id, c)
  }, '已取消')
const onDelete = () =>
  act(async () => {
    await ElMessageBox.confirm('删除后不可恢复，确认删除该变更记录？', '删除', { type: 'warning' })
    await deleteChangeRequest(detail.value!.id)
    drawer.value = false
  }, '已删除')

onMounted(() => {
  void load()
  const id = Number(route.query.id)
  if (Number.isInteger(id) && id > 0) void openDetail(id)
})
</script>

<template>
  <div class="page-card">
    <div class="page-title">变更管理</div>

    <div class="page-toolbar">
      <el-input
        v-model="query.keyword"
        placeholder="变更编号 / 标题"
        clearable
        style="width: 220px"
        @keyup.enter="search"
        @clear="search"
      />
      <el-select v-model="query.status" placeholder="状态" clearable style="width: 140px" @change="search">
        <el-option v-for="(l, k) in CHANGE_STATUS_LABEL" :key="k" :label="l" :value="k" />
      </el-select>
      <el-checkbox v-model="query.mine" @change="search">只看我发起的</el-checkbox>
      <el-button type="primary" plain @click="search">查询</el-button>
      <div style="flex: 1"></div>
      <el-button v-if="canWrite" type="primary" @click="openCreate">新建变更</el-button>
    </div>

    <el-table v-loading="loading" :data="records" border stripe row-key="id" @row-dblclick="(r: ChangeRequestItem) => openDetail(r.id)">
      <el-table-column prop="crNo" label="变更编号" width="160">
        <template #default="{ row }">
          <el-link type="primary" @click="openDetail(row.id)">{{ row.crNo }}</el-link>
        </template>
      </el-table-column>
      <el-table-column prop="title" label="标题" min-width="200" show-overflow-tooltip />
      <el-table-column label="类型" width="100">
        <template #default="{ row }">{{ CHANGE_TYPE_LABEL[row.changeType] || row.changeType }}</template>
      </el-table-column>
      <el-table-column label="风险" width="80">
        <template #default="{ row }">
          <el-tag :type="RISK_TAG[row.riskLevel] || 'info'" size="small">{{ RISK_LABEL[row.riskLevel] || row.riskLevel }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="状态" width="110">
        <template #default="{ row }">
          <el-tag :type="STATUS_TAG[row.status] || 'info'" size="small">{{ CHANGE_STATUS_LABEL[row.status] || row.status }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="计划窗口" min-width="230">
        <template #default="{ row }">
          <span v-if="row.scheduledStart">{{ fmt(row.scheduledStart) }} ~ {{ fmt(row.scheduledEnd) }}</span>
          <span v-else style="color: var(--el-text-color-placeholder)">未排期</span>
        </template>
      </el-table-column>
      <el-table-column prop="requester" label="申请人" width="110" />
      <el-table-column label="创建时间" width="150">
        <template #default="{ row }">{{ fmt(row.createdAt) }}</template>
      </el-table-column>
    </el-table>

    <div class="page-pagination">
      <el-pagination
        v-model:current-page="query.page"
        v-model:page-size="query.size"
        :total="total"
        :page-sizes="[20, 50, 100]"
        layout="total, sizes, prev, pager, next"
        @current-change="load"
        @size-change="search"
      />
    </div>

    <!-- 新建 / 编辑 -->
    <el-dialog v-model="dialogVisible" :title="editingId ? '编辑变更' : '新建变更'" width="720px" destroy-on-close>
      <el-form ref="formRef" :model="form" :rules="rules" label-width="96px">
        <el-form-item label="标题" prop="title">
          <el-input v-model="form.title" maxlength="200" show-word-limit />
        </el-form-item>
        <el-row :gutter="12">
          <el-col :span="12">
            <el-form-item label="变更类型" prop="changeType">
              <el-select v-model="form.changeType" style="width: 100%">
                <el-option v-for="(l, k) in CHANGE_TYPE_LABEL" :key="k" :label="l" :value="k" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="风险等级">
              <el-select v-model="form.riskLevel" style="width: 100%">
                <el-option v-for="(l, k) in RISK_LABEL" :key="k" :label="l" :value="k" />
              </el-select>
            </el-form-item>
          </el-col>
        </el-row>
        <el-alert
          v-if="form.changeType === 'STANDARD'"
          type="info"
          :closable="false"
          show-icon
          title="标准变更为预授权的低风险例行操作，提交后自动批准，无需 CAB 审批"
          style="margin-bottom: 12px"
        />
        <el-form-item label="计划窗口">
          <el-date-picker
            v-model="form.scheduledStart"
            type="datetime"
            value-format="YYYY-MM-DDTHH:mm:ss"
            placeholder="开始"
            style="width: 48%"
          />
          <span style="margin: 0 6px">~</span>
          <el-date-picker
            v-model="form.scheduledEnd"
            type="datetime"
            value-format="YYYY-MM-DDTHH:mm:ss"
            placeholder="结束"
            style="width: 48%"
          />
        </el-form-item>
        <el-form-item label="关联资产">
          <AssetPicker
            :model-value="form.assetNos"
            multiple
            @update:model-value="(v) => (form.assetNos = Array.isArray(v) ? v : v ? [v] : [])"
          />
        </el-form-item>
        <el-form-item label="变更内容">
          <el-input v-model="form.description" type="textarea" :rows="3" />
        </el-form-item>
        <el-form-item label="影响范围">
          <el-input v-model="form.impactScope" type="textarea" :rows="2" placeholder="受影响的业务、用户与时长" />
        </el-form-item>
        <el-form-item label="回退方案">
          <el-input
            v-model="form.rollbackPlan"
            type="textarea"
            :rows="2"
            :placeholder="form.riskLevel === 'HIGH' || form.riskLevel === 'CRITICAL' ? '高风险变更必填' : '可选'"
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="save">保存</el-button>
      </template>
    </el-dialog>

    <!-- 详情 -->
    <el-drawer v-model="drawer" size="640px" :title="detail ? `${detail.crNo}　${detail.title}` : '变更详情'">
      <div v-loading="detailLoading">
        <template v-if="detail">
          <div class="actions">
            <el-button v-if="can.edit" @click="openEdit(detail.id)">编辑</el-button>
            <el-button v-if="can.submit" type="primary" @click="onSubmit">提交审批</el-button>
            <el-button v-if="can.review" type="success" @click="onApprove">批准</el-button>
            <el-button v-if="can.review" type="danger" plain @click="onReject">驳回</el-button>
            <el-button v-if="can.start" type="primary" @click="onStart">开始实施</el-button>
            <el-button v-if="can.finish" type="success" @click="onComplete">实施完成</el-button>
            <el-button v-if="can.finish" type="danger" plain @click="onFail">失败回退</el-button>
            <el-button v-if="can.cancel" plain @click="onCancel">取消变更</el-button>
            <el-button v-if="can.remove" type="danger" text @click="onDelete">删除</el-button>
          </div>

          <el-alert
            v-if="detail.conflicts.length"
            type="warning"
            show-icon
            :closable="false"
            :title="`发现 ${detail.conflicts.length} 个时间窗重叠且涉及相同资产的变更`"
            style="margin-bottom: 12px"
          >
            <div v-for="c in detail.conflicts" :key="c.id">
              {{ c.crNo }} {{ c.title }}（{{ CHANGE_STATUS_LABEL[c.status] }}，{{ fmt(c.scheduledStart) }} ~ {{ fmt(c.scheduledEnd) }}）
            </div>
          </el-alert>

          <el-descriptions :column="2" border size="small">
            <el-descriptions-item label="状态">
              <el-tag :type="STATUS_TAG[detail.status]" size="small">{{ CHANGE_STATUS_LABEL[detail.status] }}</el-tag>
            </el-descriptions-item>
            <el-descriptions-item label="类型 / 风险">
              {{ CHANGE_TYPE_LABEL[detail.changeType] }} / {{ RISK_LABEL[detail.riskLevel] }}
            </el-descriptions-item>
            <el-descriptions-item label="申请人">{{ detail.requester }}</el-descriptions-item>
            <el-descriptions-item label="审批">
              <template v-if="detail.approvedBy">
                {{ detail.approvedBy === 'STANDARD' ? '标准变更自动批准' : detail.approvedBy }}
                <span style="color: var(--el-text-color-secondary)">{{ fmt(detail.approvedAt) }}</span>
              </template>
              <span v-else>-</span>
            </el-descriptions-item>
            <el-descriptions-item label="计划窗口" :span="2">
              {{ fmt(detail.scheduledStart) }} ~ {{ fmt(detail.scheduledEnd) }}
            </el-descriptions-item>
            <el-descriptions-item label="实际执行" :span="2">
              {{ fmt(detail.actualStart) }} ~ {{ fmt(detail.actualEnd) }}
            </el-descriptions-item>
            <el-descriptions-item label="变更内容" :span="2">
              <div class="pre">{{ detail.description || '-' }}</div>
            </el-descriptions-item>
            <el-descriptions-item label="影响范围" :span="2">
              <div class="pre">{{ detail.impactScope || '-' }}</div>
            </el-descriptions-item>
            <el-descriptions-item label="回退方案" :span="2">
              <div class="pre">{{ detail.rollbackPlan || '-' }}</div>
            </el-descriptions-item>
            <el-descriptions-item v-if="detail.cabComment" label="CAB 意见" :span="2">{{ detail.cabComment }}</el-descriptions-item>
            <el-descriptions-item v-if="detail.closeNote" label="关闭说明" :span="2">{{ detail.closeNote }}</el-descriptions-item>
            <el-descriptions-item v-if="detail.cancelReason" label="取消原因" :span="2">{{ detail.cancelReason }}</el-descriptions-item>
          </el-descriptions>

          <h4 class="sec">关联资产（{{ detail.assetNos.length }}）</h4>
          <div v-if="detail.assetNos.length" class="chips">
            <el-tag v-for="a in detail.assetNos" :key="a" size="small" effect="plain">{{ a }}</el-tag>
          </div>
          <el-empty v-else :image-size="50" description="未关联资产" />

          <h4 class="sec">影响面预估（基于 CI 关系与物理拓扑，{{ detail.impact.count }} 项）</h4>
          <el-table v-if="detail.impact.count" :data="detail.impact.nodes" size="small" border max-height="260">
            <el-table-column label="类型" width="70">
              <template #default="{ row }">{{ CI_TYPE_LABEL[row.type] || row.type }}</template>
            </el-table-column>
            <el-table-column prop="id" label="编号" width="190" />
            <el-table-column prop="name" label="名称" show-overflow-tooltip />
            <el-table-column prop="depth" label="层级" width="60" />
          </el-table>
          <el-empty v-else :image-size="50" description="未发现下游受影响的配置项" />
        </template>
      </div>
    </el-drawer>
  </div>
</template>

<style scoped>
.actions {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-bottom: 14px;
}
.actions .el-button + .el-button {
  margin-left: 0;
}
.sec {
  margin: 18px 0 8px;
  font-size: 14px;
  color: var(--el-text-color-primary);
}
.chips {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
}
.pre {
  white-space: pre-wrap;
}
</style>
