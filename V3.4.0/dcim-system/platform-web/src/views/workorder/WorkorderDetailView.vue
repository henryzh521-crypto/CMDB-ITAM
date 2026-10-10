<script setup lang="ts">
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import type { UploadFile } from 'element-plus'
import { useAuthStore } from '@/stores/auth'
import { saveBlob } from '@/api/asset'
import {
  addApprovers,
  commentWorkorder,
  deleteWorkorderAttachment,
  deleteWorkorderItem,
  downloadWorkorderAttachment,
  fetchApproverOptions,
  fetchWorkorderAttachments,
  fetchWorkorderDetail,
  remindWorkorder,
  transferApprover,
  updateRepairCost,
  updateWorkorder,
  updateWorkorderItem,
  updateWorkorderReplacements,
  uploadWorkorderAttachment,
  WO_PRIORITIES,
} from '@/api/workorder'
import type {
  TransferSummary,
  WoActionMeta,
  WorkorderItem,
  WoAssetItem,
  WoAttachment,
  WoReplacement,
  WorkorderDetail,
} from '@/api/workorder'
import {
  actionBtnType,
  isSlaOverdue,
  priorityTag,
  scopeTag,
  splitList,
  statusTag,
} from './wo-shared'
import { isStagedTransfer } from '@/api/workorder'
import ReplacementEditor from './ReplacementEditor.vue'
import type { ReplacementRow } from './replacement'
import { newReplacementRow, toWoReplacements, validateReplacementRows } from './replacement'
import WorkorderTransitionDialog from './WorkorderTransitionDialog.vue'
import WorkorderCreateDialog from './WorkorderCreateDialog.vue'
import { createProblem, fetchProblemsByRef } from '@/api/itsm'

const auth = useAuthStore()
/** v3.4：字段级权限（后端同样校验） */
const wfv = (f: string) => auth.fieldVisible('workorders', f)
const wfe = (f: string) => auth.fieldEditable('workorders', f)
const route = useRoute()
const router = useRouter()

/** workorders 模块写权限（创建/编辑入口显隐） */
const canWrite = computed(() => !!auth.modules['workorders']?.write)

const detailLoading = ref(false)
const detail = ref<WorkorderDetail | null>(null)
const currentWoNo = ref('')

const flowsDesc = computed(() =>
  [...(detail.value?.flows || [])].sort((a, b) => (b.seq ?? 0) - (a.seq ?? 0)),
)

/** 关联替换子单（父单详情展示） */
const childWos = computed(() => detail.value?.childWos ?? [])

/** 是否展示关联替换子单区块（故障维修/部件更换父单，或已有子单） */
const showChildWos = computed(
  () =>
    detail.value?.woType === '故障维修' ||
    detail.value?.woType === '部件更换' ||
    childWos.value.length > 0,
)

/** 资产行项目（详情） */
const detailItems = computed(() => detail.value?.items ?? [])

/** 行项目可维护：有写权限且工单未终态 */
const canEditItems = computed(() => {
  if (!canWrite.value || !detail.value || !wfe('relatedAssets')) return false
  return detail.value.status !== '已完成' && detail.value.status !== '已关闭'
})

/** 详情行目标列仅上架部署/变更/调拨展示（与创建一致） */
const showItemDstColumns = computed(
  () =>
    detail.value?.woType === '上架部署' ||
    detail.value?.woType === '变更' ||
    detail.value?.woType === '调拨',
)

/** 详情用料明细（耗材申领扣料行 / 故障维修·部件更换展示行） */
const detailMaterialRows = computed(() => {
  const d = detail.value?.details
  if (!Array.isArray(d)) return []
  return d.filter(
    (x): x is { materialCode: string; qty: number } =>
      !!x && typeof x === 'object' && 'materialCode' in x && 'qty' in x,
  )
})

/** 详情调拨附加信息（details 中非物料对象） */
const detailTransferMeta = computed(() => {
  const d = detail.value?.details
  if (!Array.isArray(d)) return null
  const hit = d.find(
    (x) => !!x && typeof x === 'object' && !('materialCode' in x),
  ) as Record<string, unknown> | undefined
  return hit || null
})

/* ---------------- 子件更换（故障维修 / 部件更换）——编辑态 ---------------- */
const repEditing = ref(false)
const replacementRows = ref<ReplacementRow[]>([])
const replacementsSaving = ref(false)

/** 父资产候选 = 工单关联资产 */
const parentOptions = computed<string[]>(() => {
  const d = detail.value
  if (!d) return []
  const fromItems = (d.items ?? []).map((i) => i.assetNo)
  const fromCsv = splitList(d.relatedAssets)
  return [...new Set([...fromItems, ...fromCsv])]
})

/** 可编辑换件：有写权限 + 故障维修/部件更换 + 未终态 */
const canEditReplacements = computed(
  () =>
    canEditItems.value &&
    (detail.value?.woType === '故障维修' || detail.value?.woType === '部件更换'),
)

/** 是否展示换件区块 */
const showReplacements = computed(
  () => detail.value?.woType === '故障维修' || detail.value?.woType === '部件更换',
)

const detailReplacements = computed<WoReplacement[]>(() => detail.value?.replacements ?? [])

function enterRepEdit() {
  // 行模型与选项加载交给 ReplacementEditor（按父缓存子资产、新行继承父资产）
  replacementRows.value = detailReplacements.value.map((r) => ({
    ...newReplacementRow(r.parentAssetNo),
    parentAssetNo: r.parentAssetNo,
    oldAssetNo: r.oldAssetNo,
    newAssetNo: r.newAssetNo,
    oldStatus: r.oldStatus || '维修中',
  }))
  if (!replacementRows.value.length) replacementRows.value = [newReplacementRow()]
  repEditing.value = true
}

async function saveReplacements() {
  if (!detail.value) return
  const err = validateReplacementRows(replacementRows.value, parentOptions.value)
  if (err) {
    ElMessage.warning(err)
    return
  }
  replacementsSaving.value = true
  try {
    await updateWorkorderReplacements(detail.value.woNo, toWoReplacements(replacementRows.value))
    ElMessage.success('已保存更换部件')
    repEditing.value = false
    void loadDetail()
  } catch {
    // 拦截器已提示（409 终态/校验失败）
  } finally {
    replacementsSaving.value = false
  }
}

/** 行内编辑：目标机柜/U位 */
const itemEditing = ref(false)

async function saveItemRow(row: WoAssetItem) {
  if (!detail.value) return
  try {
    await updateWorkorderItem(detail.value.woNo, row.itemNo, {
      assetNo: row.assetNo,
      dstRoom: row.dstRoom || undefined,
      dstRack: row.dstRack || undefined,
      dstUnit: row.dstUnit ?? undefined,
    })
    ElMessage.success('已保存行目标位置')
    itemEditing.value = false
    void loadDetail()
  } catch {
    // 拦截器已提示
  }
}

async function removeItemRow(row: WoAssetItem) {
  if (!detail.value) return
  try {
    await deleteWorkorderItem(detail.value.woNo, row.itemNo)
    ElMessage.success('已删除行')
    void loadDetail()
  } catch {
    // 拦截器已提示
  }
}

/** 单调递增的详情请求序号：换单/刷新并发时丢弃过期响应 */
let detailSeq = 0

/* ---------------- 关联问题（ITIL：事件 → 问题） ---------------- */
const relatedProblems = ref<{ prbNo: string; title: string; status: string }[]>([])
const escalating = ref(false)
const PROBLEM_STATUS: Record<string, string> = {
  NEW: '新建', ANALYZING: '分析中', KNOWN_ERROR: '已知错误', RESOLVED: '已解决', CLOSED: '已关闭',
}

async function loadRelatedProblems() {
  const woNo = currentWoNo.value
  if (!woNo || !auth.canRead('problems')) {
    relatedProblems.value = []
    return
  }
  try {
    relatedProblems.value = (await fetchProblemsByRef('WORKORDER', woNo)) ?? []
  } catch {
    relatedProblems.value = []
  }
}

/** 一键升级为问题单：带上标题、描述、关联资产，并自动建立 工单 ↔ 问题 双向关联 */
async function escalateToProblem() {
  const d = detail.value
  if (!d) return
  try {
    await ElMessageBox.confirm(
      '将以本工单的标题、描述和关联资产创建问题单，并自动关联本工单，用于根因分析与知识沉淀。',
      '升级为问题单',
      { type: 'info', confirmButtonText: '创建问题单', cancelButtonText: '取消' },
    )
  } catch {
    return
  }
  escalating.value = true
  try {
    const items = detailItems.value
    const assets = items.length ? items.map((it) => it.assetNo) : splitList(d.relatedAssets)
    const p = await createProblem({
      title: `【来自工单 ${d.woNo}】${d.title}`.slice(0, 200),
      description: [d.description, d.rootCause ? `工单根因：${d.rootCause}` : '', d.process ? `处理过程：${d.process}` : '']
        .filter(Boolean)
        .join('\n\n'),
      priority: d.priority || '中',
      assetNos: assets,
      source: 'MANUAL',
      links: [{ type: 'WORKORDER', ref: d.woNo }],
    })
    ElMessage.success(`已创建问题单 ${p.prbNo}`)
    void loadRelatedProblems()
  } catch {
    // 拦截器已提示
  } finally {
    escalating.value = false
  }
}

async function loadDetail() {
  const seq = ++detailSeq
  const woNo = currentWoNo.value
  detailLoading.value = true
  try {
    const res = await fetchWorkorderDetail(woNo)
    if (seq !== detailSeq) return
    detail.value = res
    void loadAttachments()
    void loadRelatedProblems()
  } catch {
    // 拦截器已提示
  } finally {
    if (seq === detailSeq) detailLoading.value = false
  }
}

/* ---------------- 附件 ---------------- */

const attachments = ref<WoAttachment[]>([])
const attLoading = ref(false)
const attUploading = ref(false)

/**
 * 上传按钮显隐：后端 perm.uploadAtt（可见即可传 + 未关闭）。
 * 与控制器 @RequirePerm(write=true, action="update") 同源 —— 有写权限的人才下发 true，
 * 只读角色不下发，从根上消灭「按钮点了才 403」。评论 perm.comment 仍是关联方口径，两者分开。
 */
const canUploadAtt = computed(() => !!detail.value?.perm?.uploadAtt && wfe('attachments'))

/** 时间展示：去掉 ISO 的 T 与秒后小数（后端 LocalDateTime 原样下发） */
function fmtTime(v?: string | null): string {
  if (!v) return '-'
  return v.replace('T', ' ').replace(/\.\d+$/, '').slice(0, 19)
}

function fmtSize(bytes: number): string {
  if (!bytes && bytes !== 0) return '-'
  if (bytes < 1024) return `${bytes} B`
  if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(1)} KB`
  return `${(bytes / 1024 / 1024).toFixed(1)} MB`
}

/** 单调递增的附件请求序号：同上 */
let attSeq = 0

async function loadAttachments() {
  if (!wfv('attachments')) {
    attachments.value = []
    return
  }
  const woNo = currentWoNo.value
  if (!woNo) return
  const seq = ++attSeq
  attLoading.value = true
  try {
    const res = await fetchWorkorderAttachments(woNo)
    if (seq !== attSeq) return
    attachments.value = res
  } catch {
    // 拦截器已提示
  } finally {
    if (seq === attSeq) attLoading.value = false
  }
}

function onAttFileChange(f: UploadFile) {
  if (f.raw) void doUploadAtt(f.raw)
}

/** 附件前端预校验（与后端 WorkorderAttachmentService 对齐）：大小 ≤ 50MB + 扩展名白名单 */
const ATT_MAX_SIZE = 50 * 1024 * 1024
const ATT_OK_EXT = ['jpg', 'jpeg', 'png', 'gif', 'webp', 'pdf', 'zip', 'rar', 'doc', 'docx', 'xls', 'xlsx', 'txt']

async function doUploadAtt(file: File) {
  if (!currentWoNo.value) return
  if (file.size > ATT_MAX_SIZE) {
    ElMessage.error('附件大小不能超过 50MB')
    return
  }
  const ext = file.name.split('.').pop()?.toLowerCase() ?? ''
  if (!ATT_OK_EXT.includes(ext)) {
    ElMessage.error('不支持的附件类型，允许：图片 / PDF / zip / rar / Word / Excel / txt')
    return
  }
  attUploading.value = true
  try {
    await uploadWorkorderAttachment(currentWoNo.value, file)
    ElMessage.success('附件已上传')
    await loadAttachments()
  } catch {
    // 拦截器已提示（400 白名单/超限、403 非关联方、409 已关闭）
  } finally {
    attUploading.value = false
  }
}

function isPreviewable(a: WoAttachment): boolean {
  const t = (a.contentType || '').toLowerCase()
  return t.startsWith('image/') || t === 'application/pdf'
}

/** 点击文件名：图片/PDF 新标签预览（blob objectURL），其余直接下载 */
async function openAtt(a: WoAttachment) {
  try {
    const blob = await downloadWorkorderAttachment(currentWoNo.value, a.id)
    if (isPreviewable(a)) {
      const url = URL.createObjectURL(blob)
      window.open(url, '_blank')
      window.setTimeout(() => URL.revokeObjectURL(url), 60_000)
    } else {
      saveBlob(blob, a.originalName)
    }
  } catch {
    // 拦截器已提示
  }
}

async function doDownloadAtt(a: WoAttachment) {
  try {
    const blob = await downloadWorkorderAttachment(currentWoNo.value, a.id)
    saveBlob(blob, a.originalName)
  } catch {
    // 拦截器已提示
  }
}

function canDeleteAtt(a: WoAttachment): boolean {
  if (auth.user?.isAdmin) return true
  return !!a.uploadedBy && a.uploadedBy === auth.user?.username
}

async function doDeleteAtt(a: WoAttachment) {
  try {
    await ElMessageBox.confirm(`确认删除附件「${a.originalName}」？`, '删除附件', {
      confirmButtonText: '删除',
      cancelButtonText: '取消',
      type: 'warning',
    })
  } catch {
    return
  }
  try {
    await deleteWorkorderAttachment(currentWoNo.value, a.id)
    ElMessage.success('附件已删除')
    await loadAttachments()
  } catch {
    // 拦截器已提示（403 非上传者 / 404 已删除）
  }
}

/** 关联跳转：父单 / 子单各自持有独立深链，直接换路由即可 */
function openRelatedWo(woNo: string) {
  if (!woNo || woNo === currentWoNo.value) return
  void router.push(`/workorders/${encodeURIComponent(woNo)}`)
}

/* ---------------- 工单流转 ---------------- */

/** 详情调拨汇总（原在列表文件里，随详情迁出） */
const detailTransferSummary = computed<TransferSummary | null>(
  () => detail.value?.transferSummary ?? null,
)

/* ---------------- P2：基本字段编辑 / 催办 / 评论 ---------------- */

/** 动作即权限：只渲染后端判定当前登录者可执行的动作（与 403 文案同源） */
const visibleActions = computed<WoActionMeta[]>(() =>
  (detail.value?.myActions ?? []).filter((m) => m.visible !== false),
)

const transitionRef = ref<InstanceType<typeof WorkorderTransitionDialog> | null>(null)
const createDialogRef = ref<InstanceType<typeof WorkorderCreateDialog> | null>(null)

function openAction(action: string) {
  transitionRef.value?.open(action)
}

function onTransitionDone() {
  void loadDetail()
}

/* ---------------- 审批链展示（按单指定 / 配置按人 / 委托） ---------------- */

/** 审批链一行文本：本单指定 > 角色 ∪ 配置级指定人 */
const approvalChainText = computed(() => {
  const d = detail.value
  if (!d) return ''
  const custom = d.customApprovers ?? []
  if (custom.length) {
    const parts = custom.map((lvl, i) => {
      const names = d.customApproverNames?.[i] ?? lvl
      const who = names.length ? names.join('、') : '（空）'
      return custom.length > 1 ? `第${i + 1}级：${who}（或签）` : `${who}（或签·按单指定）`
    })
    return parts.join(' → ')
  }
  const roles = d.approvalRoleNames ?? []
  const users = d.approvalUserLevelNames ?? []
  const len = Math.max(roles.length, users.length)
  if (!len) return ''
  const segs: string[] = []
  for (let i = 0; i < len; i++) {
    const seg: string[] = []
    if (roles[i]) seg.push(roles[i])
    if (users[i]?.length) seg.push(`指定：${users[i].join('、')}`)
    segs.push(seg.join(' ∪ '))
  }
  return segs.join(' → ')
})

/* ---------------- 转办 / 加签 ---------------- */

interface ApproverOption {
  username: string
  name?: string
}
const approvalDlgVisible = ref(false)
const approvalMode = ref<'transfer' | 'add'>('transfer')
const approvalForm = reactive({ usernames: [] as string[], opinion: '' })
const approvalSubmitting = ref(false)
const approverPool = ref<ApproverOption[]>([])
const approverLoading = ref(false)
const approverLabels = reactive<Record<string, string>>({})

const approverOptions = computed(() => {
  const map = new Map<string, ApproverOption>()
  for (const u of approverPool.value) {
    map.set(u.username, u)
    if (u.name) approverLabels[u.username] = u.name
  }
  for (const u of approvalForm.usernames) {
    if (!map.has(u)) map.set(u, { username: u, name: approverLabels[u] })
  }
  return [...map.values()]
})

async function searchApprovers(q: string) {
  approverLoading.value = true
  try {
    approverPool.value = await fetchApproverOptions(q || undefined)
  } catch {
    // 拦截器已提示
  } finally {
    approverLoading.value = false
  }
}

function openApprovalDialog(mode: 'transfer' | 'add') {
  approvalMode.value = mode
  approvalForm.usernames = []
  approvalForm.opinion = ''
  approvalDlgVisible.value = true
  void searchApprovers('')
}

const approvalValid = computed(() => {
  if (approvalMode.value === 'transfer') {
    return approvalForm.usernames.length === 1 && !!approvalForm.opinion.trim()
  }
  return approvalForm.usernames.length >= 1
})

async function submitApprovalOp() {
  if (!approvalValid.value) return
  approvalSubmitting.value = true
  try {
    if (approvalMode.value === 'transfer') {
      await transferApprover(currentWoNo.value, {
        username: approvalForm.usernames[0],
        opinion: approvalForm.opinion.trim(),
      })
      ElMessage.success('已转办本级审批人')
    } else {
      await addApprovers(currentWoNo.value, {
        usernames: [...approvalForm.usernames],
        opinion: approvalForm.opinion.trim() || undefined,
      })
      ElMessage.success('已加签审批人')
    }
    approvalDlgVisible.value = false
    void loadDetail()
  } catch {
    // 拦截器已提示（403 非本级审批人 / 400 非待审批状态）
  } finally {
    approvalSubmitting.value = false
  }
}

function openCreateChild() {
  const d = detail.value
  if (!d) return
  const items = d.items ?? []
  const assetNos = items.length ? items.map((it) => it.assetNo) : splitList(d.relatedAssets)
  createDialogRef.value?.open({
    parentWo: d.woNo,
    woType: d.woType,
    title: `替换子单：${d.title}`,
    assetNos,
  })
}

function onChildCreated() {
  void loadDetail()
}

/* —— 编辑基本字段 —— */
const editVisible = ref(false)
const editSaving = ref(false)
const editForm = reactive({
  title: '',
  description: '',
  priority: '',
  expectFinish: '',
})

function openEdit() {
  const d = detail.value
  if (!d) return
  editForm.title = d.title
  editForm.description = d.description || ''
  editForm.priority = d.priority || ''
  editForm.expectFinish = d.expectFinish || ''
  editVisible.value = true
}

async function submitEdit() {
  const d = detail.value
  if (!d) return
  const title = editForm.title.trim()
  if (!title) {
    ElMessage.warning('标题必填')
    return
  }
  if (title.length > 200) {
    ElMessage.warning('工单标题超长（最多 200 字符）')
    return
  }
  editSaving.value = true
  try {
    await updateWorkorder(d.woNo, {
      title,
      description: editForm.description.trim() || undefined,
      priority: editForm.priority || undefined,
      expectFinish: editForm.expectFinish || undefined,
    })
    ElMessage.success('已保存；改动优先级会重算 SLA 时限')
    editVisible.value = false
    await loadDetail()
  } catch {
    // 拦截器已提示（403 非关联人 / 409 终态 / 400 校验）
  } finally {
    editSaving.value = false
  }
}

/* —— 维修费用（故障维修/部件更换；完成时可顺带登记，事后也可修正） —— */
const isRepairWo = computed(
  () => detail.value?.woType === '故障维修' || detail.value?.woType === '部件更换',
)

function fmtMoney(v?: number | null): string {
  return Number(v ?? 0).toLocaleString('zh-CN', {
    minimumFractionDigits: 2,
    maximumFractionDigits: 2,
  })
}

/** 费用四类中的三笔（备件费由出库流水反算，不在工单上存，故这里只展示人工/外委/其他） */
const costTotal = computed(() => {
  const d = detail.value
  if (!d) return 0
  return (d.costLabor ?? 0) + (d.costService ?? 0) + (d.costOther ?? 0)
})

const costVisible = computed(
  () => isRepairWo.value && ['costLabor', 'costService', 'costOther'].every((f) => wfv(f)),
)
const canEditCost = computed(
  () => canWrite.value && isRepairWo.value && ['costLabor', 'costService', 'costOther'].every((f) => wfe(f)),
)

const costDialogVisible = ref(false)
const costSaving = ref(false)
const costForm = reactive({ costLabor: 0, costService: 0, costOther: 0 })

function openCostEdit() {
  const d = detail.value
  if (!d) return
  costForm.costLabor = d.costLabor ?? 0
  costForm.costService = d.costService ?? 0
  costForm.costOther = d.costOther ?? 0
  costDialogVisible.value = true
}

async function submitCostEdit() {
  const d = detail.value
  if (!d) return
  costSaving.value = true
  try {
    await updateRepairCost(d.woNo, {
      costLabor: costForm.costLabor,
      costService: costForm.costService,
      costOther: costForm.costOther,
    })
    ElMessage.success('维修费用已保存')
    costDialogVisible.value = false
    await loadDetail()
  } catch {
    // 拦截器已提示（400 非维修类型 / 负数 / 超上限）
  } finally {
    costSaving.value = false
  }
}

/* —— 催办 —— */
const remindLoading = ref(false)

async function doRemind() {
  const d = detail.value
  if (!d) return
  remindLoading.value = true
  try {
    await remindWorkorder(d.woNo)
    ElMessage.success('已催办，相关人已收到通知')
    await loadDetail()
  } catch {
    // 409 = 10 分钟内已催办；拦截器已提示
  } finally {
    remindLoading.value = false
  }
}

/* —— 评论 —— */
const commentText = ref('')
const commentLoading = ref(false)

async function doComment() {
  const d = detail.value
  const content = commentText.value.trim()
  if (!d || !content) return
  commentLoading.value = true
  try {
    await commentWorkorder(d.woNo, content)
    commentText.value = ''
    ElMessage.success('评论已记录')
    await loadDetail()
  } catch {
    // 409 = 已关闭；拦截器已提示
  } finally {
    commentLoading.value = false
  }
}

function goBack() {
  void router.push('/workorders')
}

/** 详情页路由：/workorders/:woNo（深链直接进来，也支持从列表点跳转） */
watch(
  () => route.params.woNo,
  (v) => {
    const next = String(v || '')
    if (next && next !== currentWoNo.value) {
      currentWoNo.value = next
      detail.value = null
      repEditing.value = false
      replacementRows.value = []
      commentText.value = ''
      void loadDetail()
    }
  },
)

onMounted(() => {
  currentWoNo.value = String(route.params.woNo || '')
  if (currentWoNo.value) void loadDetail()
})
</script>

<template>
  <div class="wo-detail-page">
    <div class="detail-topbar">
      <el-button link type="primary" @click="goBack">← 返回列表</el-button>
      <span class="detail-topbar-title">
        工单详情{{ currentWoNo ? `（${currentWoNo}）` : '' }}
      </span>
      <span v-if="detail" class="detail-topbar-no">最后更新 {{ fmtTime(detail.updatedAt) }}</span>
    </div>

    <div v-loading="detailLoading" class="detail-body">
      <template v-if="detail">
          <div class="drawer-head">
            <div class="drawer-head-title">
              {{ detail.title }}
              <el-tag :type="statusTag(detail.status)" size="small" style="margin-left: 8px">
                {{ detail.status }}
              </el-tag>
              <el-tag :type="priorityTag(detail.priority)" size="small" style="margin-left: 6px">
                {{ detail.priority || '-' }}
              </el-tag>
            </div>
          </div>
          <el-descriptions :column="2" border size="small">
            <el-descriptions-item label="工单编号">{{ detail.woNo }}</el-descriptions-item>
            <el-descriptions-item label="类型">{{ detail.woType || '-' }}</el-descriptions-item>
            <el-descriptions-item v-if="wfv('applicant')" label="申请人">{{ detail.applicant || '-' }}</el-descriptions-item>
            <el-descriptions-item v-if="wfv('handler')" label="处理人">{{ detail.handler || '-' }}</el-descriptions-item>
            <el-descriptions-item v-if="wfv('expectFinish')" label="期望完成">{{ detail.expectFinish || '-' }}</el-descriptions-item>
            <el-descriptions-item label="申请时间">{{ fmtTime(detail.createdAt) }}</el-descriptions-item>
            <el-descriptions-item v-if="wfv('description')" label="描述" :span="2">
              <div style="white-space: pre-wrap">{{ detail.description || '-' }}</div>
            </el-descriptions-item>
            <el-descriptions-item v-if="wfv('relatedAssets')" label="关联资产" :span="2">
              <template v-if="detailItems.length || splitList(detail.relatedAssets).length">
                <el-tag
                  v-for="a in detailItems.length
                    ? detailItems.map((it) => it.assetNo)
                    : splitList(detail.relatedAssets)"
                  :key="a"
                  size="small"
                  style="margin: 0 6px 4px 0"
                >
                  {{ a }}
                </el-tag>
              </template>
              <template v-else>-</template>
            </el-descriptions-item>
            <el-descriptions-item v-if="wfv('relatedMaterials')" label="关联物料" :span="2">
              <template v-if="splitList(detail.relatedMaterials).length">
                <el-tag
                  v-for="m in splitList(detail.relatedMaterials)"
                  :key="m"
                  size="small"
                  type="warning"
                  style="margin: 0 6px 4px 0"
                >
                  {{ m }}
                </el-tag>
              </template>
              <template v-else>-</template>
            </el-descriptions-item>
            <el-descriptions-item label="申请数量">{{ detail.applyQty ?? '-' }}</el-descriptions-item>
            <el-descriptions-item label="目标位置">
              {{ detail.targetLocation || detail.targetRack || '-' }}
              <template v-if="detail.targetUnit"> / U{{ detail.targetUnit }}</template>
            </el-descriptions-item>
            <el-descriptions-item v-if="detail.beforeLocation" label="源位置快照" :span="2">
              <code class="mono-text">{{ detail.beforeLocation }}</code>
            </el-descriptions-item>
            <el-descriptions-item label="SLA响应截止">{{ fmtTime(detail.slaResponseDeadline) }}</el-descriptions-item>
            <el-descriptions-item label="SLA解决截止">
              <span :class="{ 'sla-overdue': isSlaOverdue(detail) }">
                {{ fmtTime(detail.slaResolveDeadline) }}
              </span>
            </el-descriptions-item>
            <el-descriptions-item label="首次响应">{{ fmtTime(detail.firstResponseAt) }}</el-descriptions-item>
            <el-descriptions-item label="升级次数">{{ detail.slaEscalations ?? 0 }}</el-descriptions-item>
            <el-descriptions-item label="最近升级">{{ fmtTime(detail.lastEscalationAt) }}</el-descriptions-item>
            <el-descriptions-item label="审批级别">
              {{ detail.approvalLevel ?? 0 }}
              <template v-if="detail.currentLevelName">（{{ detail.currentLevelName }}）</template>
            </el-descriptions-item>
            <el-descriptions-item label="审批链">
              <template v-if="approvalChainText">{{ approvalChainText }}</template>
              <template v-else>-</template>
            </el-descriptions-item>
            <el-descriptions-item v-if="detail.delegate" label="本级委托">
              <el-tag
                :type="detail.delegate.mode === 'transfer' ? 'warning' : 'success'"
                size="small"
              >
                {{ detail.delegate.modeLabel || (detail.delegate.mode === 'transfer' ? '转办' : '加签') }}
              </el-tag>
              {{
                (detail.delegate.memberNames?.length
                  ? detail.delegate.memberNames
                  : detail.delegate.members
                ).join('、')
              }}
              <template v-if="detail.delegate.byName || detail.delegate.by">
                （由 {{ detail.delegate.byName || detail.delegate.by }} 操作）
              </template>
              <template v-if="detail.delegate.opinion">：{{ detail.delegate.opinion }}</template>
            </el-descriptions-item>
            <el-descriptions-item label="处理过程" :span="2">
              {{ detail.process || '-' }}
            </el-descriptions-item>
            <el-descriptions-item label="完成说明">{{ detail.completeNote || '-' }}</el-descriptions-item>
            <el-descriptions-item label="根因">{{ detail.rootCause || '-' }}</el-descriptions-item>
            <el-descriptions-item v-if="auth.canRead('problems')" label="关联问题" :span="2">
              <template v-if="relatedProblems.length">
                <el-tag
                  v-for="p in relatedProblems"
                  :key="p.prbNo"
                  class="related-problem"
                  size="small"
                  effect="plain"
                  @click="router.push({ path: '/problems', query: { prb: p.prbNo } })"
                >
                  {{ p.prbNo }} · {{ PROBLEM_STATUS[p.status] || p.status }} · {{ p.title }}
                </el-tag>
              </template>
              <span v-else class="muted-text">无</span>
              <el-button
                v-if="auth.canWrite('problems')"
                link
                type="primary"
                :loading="escalating"
                style="margin-left: 8px"
                @click="escalateToProblem"
              >
                升级为问题单
              </el-button>
            </el-descriptions-item>
            <el-descriptions-item label="自动扣料">
              {{ detail.autoDeducted ? '是' : '否' }}
            </el-descriptions-item>
            <el-descriptions-item label="父工单">
              <el-link
                v-if="detail.parentWo"
                type="primary"
                :underline="false"
                @click="openRelatedWo(detail.parentWo)"
              >
                {{ detail.parentWo }}
              </el-link>
              <span v-else>-</span>
            </el-descriptions-item>
            <el-descriptions-item label="更换部件">
              {{ detail.replacedAsset || '-' }}
            </el-descriptions-item>
            <el-descriptions-item v-if="costVisible" label="维修费用">
              <span class="cost-line">
                人工 {{ fmtMoney(detail.costLabor) }} ｜ 外委 {{ fmtMoney(detail.costService) }}
                ｜ 其他 {{ fmtMoney(detail.costOther) }} ｜ 合计 <b>{{ fmtMoney(costTotal) }}</b> 元
              </span>
              <el-button
                v-if="canEditCost"
                link
                type="primary"
                size="small"
                @click="openCostEdit"
              >
                登记/修正
              </el-button>
            </el-descriptions-item>
            <el-descriptions-item label="更新时间">{{ fmtTime(detail.updatedAt) }}</el-descriptions-item>
          </el-descriptions>

          <!-- 附件（可见即可传：模块写权限 + 未关闭，不限关联方；后端白名单 + 50MB） -->
          <div v-if="wfv('attachments')" class="att-head">
            <span class="drawer-subtitle" style="margin: 0">附件</span>
            <el-upload
              v-if="canUploadAtt"
              :show-file-list="false"
              :auto-upload="false"
              :disabled="attUploading"
              :on-change="onAttFileChange"
            >
              <el-button
                size="small"
                plain
                :loading="attUploading"
                title="支持 jpg/jpeg/png/gif/webp/pdf/zip/rar/doc/docx/xls/xlsx/txt，单个 ≤50MB"
              >
                上传附件
              </el-button>
            </el-upload>
          </div>
          <div v-loading="attLoading">
            <div v-if="attachments.length">
              <div v-for="a in attachments" :key="a.id" class="att-row">
                <el-link
                  type="primary"
                  :underline="false"
                  class="att-name"
                  :title="isPreviewable(a) ? '点击预览' : '点击下载'"
                  @click="openAtt(a)"
                >
                  {{ a.originalName }}
                </el-link>
                <span class="att-meta">
                  {{ fmtSize(a.sizeBytes) }} ｜ {{ a.uploadedBy || '-' }} ｜ {{ a.createdAt || '' }}
                </span>
                <el-button link type="primary" size="small" @click="doDownloadAtt(a)">
                  下载
                </el-button>
                <el-button
                  v-if="canDeleteAtt(a)"
                  link
                  type="danger"
                  size="small"
                  @click="doDeleteAtt(a)"
                >
                  删除
                </el-button>
              </div>
            </div>
            <div v-else-if="!attLoading && wfv('attachments')" class="detail-hint" style="margin-left: 0">暂无附件</div>
          </div>

          <!-- 用料明细（耗材申领 / 故障维修·部件更换展示） -->
          <template v-if="detailMaterialRows.length">
            <div class="drawer-subtitle">用料明细</div>
            <el-table :data="detailMaterialRows" border size="small">
              <el-table-column prop="materialCode" label="物料编码" min-width="140" />
              <el-table-column prop="qty" label="数量" width="100" />
            </el-table>
            <div
              v-if="detail.woType === '故障维修' || detail.woType === '部件更换'"
              class="detail-hint"
              style="margin-left: 0; margin-top: 6px"
            >
              完成工单时自动出库扣减；库存不足将阻止完成工单
            </div>
          </template>

          <!-- 子件更换（故障维修 / 部件更换） -->
          <template v-if="showReplacements">
            <div class="drawer-subtitle">
              更换部件
              <el-button
                v-if="canEditReplacements && !repEditing"
                type="primary"
                link
                @click="enterRepEdit"
              >
                编辑
              </el-button>
              <template v-if="canEditReplacements && repEditing">
                <el-button type="success" link :loading="replacementsSaving" @click="saveReplacements">
                  保存
                </el-button>
                <el-button type="info" link @click="repEditing = false">取消</el-button>
              </template>
            </div>

            <!-- 编辑态：与创建工单弹窗共用同一套多行换件编辑器 -->
            <template v-if="repEditing">
              <ReplacementEditor
                v-model:rows="replacementRows"
                :parent-options="parentOptions"
              />
            </template>

            <!-- 只读态 -->
            <el-table v-else-if="detailReplacements.length" :data="detailReplacements" border size="small">
              <el-table-column prop="parentAssetNo" label="父资产" min-width="140" />
              <el-table-column prop="oldAssetNo" label="旧件（换下）" min-width="140" />
              <el-table-column prop="newAssetNo" label="新件（换上）" min-width="140" />
              <el-table-column label="换下后状态" width="110">
                <template #default="{ row }">{{ row.oldStatus || '维修中' }}</template>
              </el-table-column>
            </el-table>
            <div v-else class="detail-hint" style="margin-left: 0">暂无更换部件记录</div>
          </template>

          <!-- 调拨附加信息 -->
          <template v-if="detailTransferMeta && detail.woType === '调拨'">
            <div class="drawer-subtitle">
              调拨信息
              <el-tag
                v-if="detailTransferSummary?.scope"
                :type="scopeTag(detailTransferSummary.scope)"
                size="small"
                style="margin-left: 8px"
              >
                {{ detailTransferSummary.scope }}
              </el-tag>
              <el-tag
                v-if="detailTransferSummary?.needDowntime"
                type="warning"
                size="small"
                style="margin-left: 6px"
              >
                需停机
              </el-tag>
            </div>
            <el-descriptions :column="2" border size="small">
              <el-descriptions-item label="调拨范围">
                {{ detailTransferSummary?.scope || detailItems.find((i) => i.scope)?.scope || '-' }}
                <template v-if="detailTransferSummary?.itemCount != null">
                  （{{ detailTransferSummary.itemCount }} 项资产）
                </template>
              </el-descriptions-item>
              <el-descriptions-item label="到场暂存">
                {{ detailTransferSummary?.stagedCount ?? 0 }} / {{ detailTransferSummary?.itemCount ?? 0 }} 项
                <div
                  v-if="(detailTransferSummary?.stagedCount ?? 0) > 0"
                  class="detail-hint"
                  style="margin-left: 0"
                >
                  暂存行完工后只迁到目标机房、不占 U 位，需另行开「上架部署」工单上架
                </div>
              </el-descriptions-item>
              <el-descriptions-item label="是否停机">
                {{ detailTransferMeta.needDowntime ? '是' : '否' }}
              </el-descriptions-item>
              <el-descriptions-item label="调拨原因" :span="2">
                {{ detailTransferMeta.reason || detail.description || '-' }}
              </el-descriptions-item>
              <el-descriptions-item label="停机窗口">
                {{ detailTransferMeta.downtimeWindow || '-' }}
              </el-descriptions-item>
              <el-descriptions-item label="运输方式">
                {{ detailTransferMeta.transportMode || '-' }}
              </el-descriptions-item>
              <el-descriptions-item label="承运方">
                {{ detailTransferMeta.carrier || '-' }}
              </el-descriptions-item>
              <el-descriptions-item label="联系人" :span="2">
                {{ detailTransferMeta.contact || '-' }}
              </el-descriptions-item>
            </el-descriptions>
          </template>

          <!-- 资产行项目（源/目标位置） -->
          <template v-if="detailItems.length">
            <div class="drawer-subtitle">
              资产行项目
              <el-button
                v-if="canEditItems && !itemEditing"
                type="primary"
                link
                @click="itemEditing = true"
              >
                编辑目标
              </el-button>
              <el-button
                v-if="canEditItems && itemEditing"
                type="success"
                link
                @click="itemEditing = false"
              >
                完成编辑
              </el-button>
            </div>
            <el-table :data="detailItems" border size="small">
              <el-table-column prop="itemNo" label="行号" width="140" show-overflow-tooltip />
              <el-table-column prop="assetNo" label="资产" min-width="140" show-overflow-tooltip />
              <el-table-column label="源位置" min-width="160" show-overflow-tooltip>
                <template #default="{ row }">
                  <template v-if="row.srcRack || row.srcRoom">
                    {{ row.srcRoom || '-' }} / {{ row.srcRack || '-' }}
                    <template v-if="row.srcUnit != null"> / U{{ row.srcUnit }}</template>
                  </template>
                  <template v-else>-</template>
                </template>
              </el-table-column>
              <template v-if="showItemDstColumns">
                <el-table-column label="目标机房" min-width="120">
                  <template #default="{ row }">{{ row.dstRoom || '-' }}</template>
                </el-table-column>
                <el-table-column label="目标机柜" min-width="160">
                  <template #default="{ row }">
                    <template v-if="!itemEditing">
                      <el-tag v-if="isStagedTransfer(detail?.woType, row)" type="info" size="small">
                        到场暂存（未上架）
                      </el-tag>
                      <template v-else>{{ row.dstRack || '-' }}</template>
                    </template>
                    <el-input
                      v-else
                      v-model="row.dstRack"
                      size="small"
                      placeholder="目标机柜（留空=暂存）"
                    />
                  </template>
                </el-table-column>
                <el-table-column label="目标U位" width="120">
                  <template #default="{ row }">
                    <template v-if="!itemEditing">{{ row.dstUnit != null ? `U${row.dstUnit}` : '-' }}</template>
                    <el-input-number
                      v-else
                      v-model="row.dstUnit"
                      :min="1"
                      :max="999"
                      controls-position="right"
                      size="small"
                      style="width: 90px"
                    />
                  </template>
                </el-table-column>
                <el-table-column v-if="detail?.woType === '调拨'" label="范围" width="100">
                  <template #default="{ row }">
                    <el-tag v-if="row.scope" :type="scopeTag(row.scope)" size="small">
                      {{ row.scope }}
                    </el-tag>
                    <span v-else>-</span>
                  </template>
                </el-table-column>
                <el-table-column v-if="detail?.woType === '调拨'" label="区域" width="90">
                  <template #default="{ row }">
                    {{ row.dstRegion || row.srcRegion || '-' }}
                  </template>
                </el-table-column>
                <el-table-column v-if="canEditItems && itemEditing" label="操作" width="70">
                  <template #default="{ row }">
                    <el-button link type="primary" @click="saveItemRow(row)">保存</el-button>
                  </template>
                </el-table-column>
                <el-table-column v-if="canEditItems" label="删除" width="70">
                  <template #default="{ row }">
                    <el-button link type="danger" @click="removeItemRow(row)">删除</el-button>
                  </template>
                </el-table-column>
              </template>
            </el-table>
          </template>

          <div
            v-if="detail?.woType === '调拨' && detailItems.length"
            class="detail-hint"
            style="margin-left: 0; margin-top: 6px"
          >
            范围为跨数据中心/跨地域，或创建时勾选「是否停机」时，进入处理中会将资产临时停用，完工自动恢复在用(计费)
          </div>

          <!-- 关联替换子工单（故障维修/部件更换父单） -->
          <template v-if="showChildWos">
            <div class="drawer-subtitle">
              关联替换子单
              <el-button
                v-if="
                  canWrite &&
                  (detail.woType === '故障维修' || detail.woType === '部件更换')
                "
                type="primary"
                link
                @click="openCreateChild"
              >
                创建替换子单
              </el-button>
            </div>
            <el-table v-if="childWos.length" :data="childWos" border size="small">
              <el-table-column label="工单编号" min-width="150">
                <template #default="{ row }">
                  <el-link
                    type="primary"
                    :underline="false"
                    @click="openRelatedWo(row.woNo)"
                  >
                    {{ row.woNo }}
                  </el-link>
                </template>
              </el-table-column>
              <el-table-column prop="title" label="标题" min-width="140" show-overflow-tooltip />
              <el-table-column label="状态" width="90">
                <template #default="{ row }">
                  <el-tag :type="statusTag(row.status)" size="small">{{ row.status || '-' }}</el-tag>
                </template>
              </el-table-column>
              <el-table-column prop="handler" label="处理人" width="90">
                <template #default="{ row }">{{ row.handler || '-' }}</template>
              </el-table-column>
            </el-table>
            <el-empty
              v-else
              description="暂无关联替换子单"
              :image-size="48"
            />
          </template>

          <div class="drawer-subtitle">流转记录</div>
          <el-timeline v-if="flowsDesc.length" style="padding-left: 4px">
            <el-timeline-item
              v-for="f in flowsDesc"
              :key="f.seq"
              :timestamp="f.createdAt || ''"
              placement="top"
              type="primary"
            >
              <div class="flow-title">{{ f.action }}</div>
              <div class="flow-meta">
                操作人：{{ f.actor || '-' }}
                <template v-if="f.from || f.to">｜{{ f.from || '-' }} → {{ f.to || '-' }}</template>
              </div>
              <div v-if="f.comment" class="flow-comment">{{ f.comment }}</div>
            </el-timeline-item>
          </el-timeline>
          <el-empty v-else description="暂无流转记录" :image-size="60" />

          <div class="drawer-subtitle">操作</div>
          <div
            v-if="
              visibleActions.length ||
              detail.perm?.edit ||
              detail.perm?.remind ||
              detail.perm?.transfer ||
              detail.perm?.addApprover
            "
            class="action-bar"
          >
            <el-button
              v-for="m in visibleActions"
              :key="m.action"
              :type="actionBtnType(m.action)"
              :plain="m.action !== '审批通过' && m.action !== '驳回'"
              :title="m.hint || ''"
              @click="openAction(m.action)"
            >
              {{ m.action }}
            </el-button>
            <el-button v-if="detail.perm?.edit" plain @click="openEdit">编辑工单</el-button>
            <el-button v-if="detail.perm?.remind" plain :loading="remindLoading" @click="doRemind">
              催办
            </el-button>
            <el-button
              v-if="detail.perm?.transfer"
              plain
              title="把本级审批转给别人（原本级角色成员暂时不能批，直到委托解除）"
              @click="openApprovalDialog('transfer')"
            >
              转办
            </el-button>
            <el-button
              v-if="detail.perm?.addApprover"
              plain
              title="给本级追加审批人（与本级基础链并集或签）"
              @click="openApprovalDialog('add')"
            >
              加签
            </el-button>
          </div>
          <div v-else class="action-empty">暂无可用操作</div>

          <!-- 评论（P2） -->
          <div class="drawer-subtitle">评论</div>
          <template v-if="detail.perm?.comment">
            <el-input
              v-model="commentText"
              type="textarea"
              :rows="3"
              maxlength="1000"
              placeholder="添加评论（写入流转记录，不改状态、不发通知）"
            />
            <div style="margin: 8px 0 4px">
              <el-button
                type="primary"
                size="small"
                :loading="commentLoading"
                :disabled="!commentText.trim()"
                @click="doComment"
              >
                发表评论
              </el-button>
            </div>
          </template>
          <div v-else class="action-empty">当前账号不可评论（仅申请人 / 处理人 / 审批人 / 管理员）</div>
      </template>
      <el-empty
        v-else-if="!detailLoading"
        description="工单不存在或无权查看"
        :image-size="80"
      />
    </div>

    <WorkorderTransitionDialog
      ref="transitionRef"
      :wo-no="currentWoNo"
      :wo-type="detail?.woType"
      :status="detail?.status"
      :replaced-asset="detail?.replacedAsset"
      :replacements="detail?.replacements"
      @done="onTransitionDone"
    />
    <WorkorderCreateDialog ref="createDialogRef" @created="onChildCreated" />

    <!-- 转办 / 加签（状态机外的审批人调整，仅待审批且本人可批时按钮可见） -->
    <el-dialog
      v-model="approvalDlgVisible"
      :title="approvalMode === 'transfer' ? '转办本级审批人' : '加签审批人'"
      width="480px"
      destroy-on-close
    >
      <el-form label-width="92px" @submit.prevent>
        <el-form-item :label="approvalMode === 'transfer' ? '转办给' : '加签人'" required>
          <el-select
            v-model="approvalForm.usernames"
            :multiple="approvalMode === 'add'"
            filterable
            remote
            clearable
            :remote-method="searchApprovers"
            :loading="approverLoading"
            :placeholder="
              approvalMode === 'transfer' ? '选择一名审批人（可搜索用户名/姓名）' : '选择一名或多名审批人'
            "
            style="width: 100%"
            @visible-change="(v: boolean) => v && searchApprovers('')"
          >
            <el-option
              v-for="u in approverOptions"
              :key="u.username"
              :label="`${u.name || u.username}（${u.username}）`"
              :value="u.username"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="说明" :required="approvalMode === 'transfer'">
          <el-input
            v-model="approvalForm.opinion"
            type="textarea"
            :rows="3"
            maxlength="500"
            :placeholder="approvalMode === 'transfer' ? '必填，写入流转记录并通知对方' : '选填'"
          />
        </el-form-item>
        <div class="detail-hint" style="margin-left: 0">
          {{
            approvalMode === 'transfer'
              ? '转办后本级仅目标用户可批（角色成员暂停）；审批通过/驳回/重新提交后自动解除。'
              : '加签人与本级基础链并集或签，任一人可通过本级；审批通过/驳回/重新提交后自动解除。'
          }}
        </div>
      </el-form>
      <template #footer>
        <el-button @click="approvalDlgVisible = false">取消</el-button>
        <el-button
          type="primary"
          :loading="approvalSubmitting"
          :disabled="!approvalValid"
          @click="submitApprovalOp"
        >
          确定
        </el-button>
      </template>
    </el-dialog>

    <el-dialog
      v-model="editVisible"
      title="编辑工单"
      width="520px"
      append-to-body
      destroy-on-close
    >
      <el-form label-width="92px">
        <el-form-item label="标题" required>
          <el-input v-model="editForm.title" maxlength="200" show-word-limit />
        </el-form-item>
        <el-form-item v-if="wfv('description')" label="描述">
          <el-input
            :disabled="!wfe('description')"
            v-model="editForm.description"
            type="textarea"
            :rows="4"
            maxlength="1000"
            show-word-limit
          />
        </el-form-item>
        <el-form-item label="优先级">
          <el-radio-group v-model="editForm.priority">
            <el-radio v-for="p in WO_PRIORITIES" :key="p" :value="p">{{ p }}</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="期望完成">
          <el-date-picker
            v-model="editForm.expectFinish"
            type="date"
            value-format="YYYY-MM-DD"
            style="width: 100%"
          />
        </el-form-item>
        <div class="detail-hint" style="margin-left: 0">
          改动优先级会按 SLA 矩阵重算响应/解决时限；标题、描述对关联人（申请人/处理人/审批人/管理员）可见。
        </div>
      </el-form>
      <template #footer>
        <el-button @click="editVisible = false">取消</el-button>
        <el-button type="primary" :loading="editSaving" @click="submitEdit">保存</el-button>
      </template>
    </el-dialog>

    <!-- 维修费用登记/修正：三笔整体覆盖；备件费由出库流水反算，不在此录入 -->
    <el-dialog
      v-model="costDialogVisible"
      title="维修费用"
      width="460px"
      append-to-body
      destroy-on-close
    >
      <el-form label-width="92px">
        <el-form-item label="人工费">
          <el-input-number
            v-model="costForm.costLabor"
            :min="0"
            :max="99999999.99"
            :precision="2"
            :step="100"
            controls-position="right"
            style="width: 100%"
          />
        </el-form-item>
        <el-form-item label="外委费">
          <el-input-number
            v-model="costForm.costService"
            :min="0"
            :max="99999999.99"
            :precision="2"
            :step="100"
            controls-position="right"
            style="width: 100%"
          />
        </el-form-item>
        <el-form-item label="其他费">
          <el-input-number
            v-model="costForm.costOther"
            :min="0"
            :max="99999999.99"
            :precision="2"
            :step="100"
            controls-position="right"
            style="width: 100%"
          />
        </el-form-item>
        <div class="detail-hint" style="margin-left: 0">
          三笔为整体覆盖保存（不填即 0），结案后仍可修正；备件费按出库流水与物料单价自动反算，不在此录入。
        </div>
      </el-form>
      <template #footer>
        <el-button @click="costDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="costSaving" @click="submitCostEdit">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.wo-detail-page {
  padding: 4px 0 24px;
}

.detail-topbar {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 12px;
}

.detail-topbar-title {
  font-size: 16px;
  font-weight: 600;
  color: var(--el-text-color-primary);
}

.detail-topbar-no {
  margin-left: auto;
  font-size: 12px;
  color: var(--el-text-color-secondary);
}

.detail-body {
  background: var(--el-bg-color);
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 6px;
  padding: 16px 20px 20px;
}

.sla-overdue {
  color: #f56c6c;
  font-weight: 600;
}

.detail-block {
  margin: 0 0 4px 92px;
}

.detail-block-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 8px;
}

.detail-block-title {
  font-size: 13px;
  font-weight: 600;
  color: var(--el-text-color-primary);
}

.detail-hint {
  margin-left: 28px;
  font-size: 12px;
  color: var(--el-text-color-secondary);
  line-height: 1.5;
}

.mono-text {
  font-size: 12px;
  word-break: break-all;
  white-space: pre-wrap;
}

.drawer-head {
  margin-bottom: 10px;
}

.drawer-head-title {
  font-size: 15px;
  font-weight: 600;
  color: var(--el-text-color-primary);
}

.drawer-subtitle {
  font-size: 14px;
  font-weight: 600;
  color: var(--el-text-color-primary);
  margin: 16px 0 10px;
}

.flow-title {
  font-weight: 600;
  color: var(--el-text-color-primary);
}

.flow-meta {
  font-size: 12px;
  color: var(--el-text-color-secondary);
  margin-top: 2px;
}

.flow-comment {
  font-size: 12px;
  color: var(--el-text-color-regular);
  margin-top: 4px;
  background: var(--el-fill-color-light);
  border-radius: 4px;
  padding: 4px 8px;
}

.action-bar {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.action-empty {
  font-size: 12px;
  color: var(--el-text-color-secondary);
}

.att-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-top: 14px;
  margin-bottom: 6px;
}

.att-row {
  display: flex;
  align-items: center;
  gap: 10px;
  font-size: 13px;
  padding: 5px 0;
  border-bottom: 1px dashed var(--el-border-color-lighter);
}

.att-name {
  max-width: 260px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.att-meta {
  flex: 1;
  font-size: 12px;
  color: var(--el-text-color-secondary);
}

/* 维修费用行：三笔 + 合计（元），紧随其后是「登记/修正」链接按钮 */
.cost-line {
  font-size: 13px;
  color: var(--el-text-color-primary);
}

.cost-line b {
  color: var(--el-color-primary);
}
.related-problem {
  margin: 2px 6px 2px 0;
  cursor: pointer;
}
.muted-text {
  color: var(--el-text-color-secondary);
}
</style>
