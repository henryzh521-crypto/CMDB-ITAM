<script setup lang="ts">
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import type { FormInstance, FormRules } from 'element-plus'
import { fetchAssets } from '@/api/asset'
import type { AssetItem } from '@/api/asset'
import { fetchMaterialOptions } from '@/api/warehouse'
import type { MaterialOption } from '@/api/warehouse'
import { fetchRackFreeUnits, fetchRacks } from '@/api/room'
import type { RackItem } from '@/api/room'
import {
  createWorkorder,
  fetchApproverOptions,
  fetchTransferLocations,
  fetchWorkflowConfig,
  SWAP_WO_TYPES,
  WO_PRIORITIES,
  WO_TYPES,
} from '@/api/workorder'
import type {
  TransferLocations,
  TransferRoomLocation,
  TransferRackLocation,
  TransferScopeLabel,
  WoAssetLineInput,
  WoReplacement,
  WorkflowConfigItem,
} from '@/api/workorder'
import ReplacementEditor from './ReplacementEditor.vue'
import { useAuthStore } from '@/stores/auth'
import type { ReplacementRow } from './replacement'
import {
  deriveReplacedAsset,
  toWoReplacements,
  validateReplacementRows,
} from './replacement'
import { scopeTag, splitList } from './wo-shared'

/** 预填（创建替换子单时由详情页传入） */
interface WoCreatePrefill {
  parentWo?: string
  woType?: string
  title?: string
  assetNos?: string[]
}

/** v3.4：字段级权限，受限字段不在新建表单出现（后端同样校验） */
const auth = useAuthStore()
const emit = defineEmits<{ (e: 'created', woNo: string): void }>()

const dialogVisible = ref(false)
const saving = ref(false)
const formRef = ref<FormInstance>()

const form = reactive({
  woType: '故障维修',
  title: '',
  description: '',
  priority: '中',
  expectFinish: '',
  assetRows: [] as AssetFormRow[],
  targetDstRoom: '',
  targetRack: '',
  targetUnit: undefined as number | undefined,
  details: [] as DetailRow[],
  parentWo: '',
  replacedAsset: '',
  // 调拨附加信息（落 details 单对象，完工不扣料）
  transferReason: '',
  needDowntime: false,
  downtimeWindow: '',
  transportMode: '',
  carrier: '',
  contact: '',
  /** 按单指定审批人（单级或签；空 = 走 workflow_config 角色链） */
  approvalApprovers: [] as string[],
})

const rules: FormRules = {
  title: [{ required: true, message: '请输入标题', trigger: 'blur' }],
}

/* ---------------- 按单指定审批人 ---------------- */

const workflowConfig = ref<WorkflowConfigItem[]>([])
let workflowLoaded = false

async function ensureWorkflowConfig() {
  if (workflowLoaded) return
  try {
    workflowConfig.value = await fetchWorkflowConfig()
    workflowLoaded = true
  } catch {
    // 拦截器已提示（极端情况下按免审处理，不展示选择器）
  }
}

/** 当前类型是否需审批（决定是否展示「指定审批人」） */
const needApprovalNow = computed(
  () => workflowConfig.value.find((c) => c.woType === form.woType)?.needApproval === true,
)

interface ApproverOption {
  username: string
  name?: string
}
const approverPool = ref<ApproverOption[]>([])
const approverLoading = ref(false)
/** username → 显示名缓存（搜索池刷新后已选项仍能显示中文名） */
const approverLabels = reactive<Record<string, string>>({})

const approverOptions = computed(() => {
  const map = new Map<string, ApproverOption>()
  for (const u of approverPool.value) {
    map.set(u.username, u)
    if (u.name) approverLabels[u.username] = u.name
  }
  for (const u of form.approvalApprovers) {
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

onMounted(() => {
  void ensureWorkflowConfig()
})

/** 按类型显隐：关联资产（耗材申领除外） */
const showRelatedAssets = computed(
  () => form.woType !== '耗材申领' && auth.fieldEditable('workorders', 'relatedAssets'),
)
/** 按类型显隐：用料明细（耗材申领必填扣料；故障维修/部件更换完工扣料） */
const showDetails = computed(
  () =>
    form.woType === '耗材申领' ||
    form.woType === '故障维修' ||
    form.woType === '部件更换',
)
/** 用料明细是否必填（仅耗材申领） */
const requireDetails = computed(() => form.woType === '耗材申领')
/** 按类型显隐：目标机柜 + 起始U位（上架部署 / 变更 / 调拨） */
const showTargetRack = computed(
  () => form.woType === '上架部署' || form.woType === '变更' || form.woType === '调拨',
)
/** 按类型显隐：更换部件（故障维修/部件更换）——单值字段与结构化明细共用同一判定 */
const showReplacedAsset = computed(() => (SWAP_WO_TYPES as readonly string[]).includes(form.woType))
/** 结构化换件明细区块（同一台设备换多个部件在这里加行） */
const showSwapReplacements = showReplacedAsset

/** 换件明细行（组件负责选项加载与互斥，这里只管行数据与提交） */
const replacementRows = ref<ReplacementRow[]>([])

/** 父资产候选 = 本单已添加的关联资产行（与后端「父资产须在关联资产中」同源） */
const replacementParentOptions = computed(() =>
  form.assetRows.map((r) => r.assetNo).filter(Boolean),
)

/** 明细派生的主表「更换部件」单值，保证与结构化明细一致 */
const derivedReplacedAsset = computed(() => deriveReplacedAsset(replacementRows.value))
const replacementDriven = computed(
  () => showSwapReplacements.value && derivedReplacedAsset.value.length > 0,
)

function deriveReplacementPayload() {
  return toWoReplacements(replacementRows.value)
}

/**
 * 主表「更换部件」单值：
 *  - 有结构化明细 → 由明细派生（旧件号逗号连接），输入框只读，杜绝两套各填各的
 *  - 无明细 → 保持原有自由文本（故障维修口头记录一个件号的轻量用法）
 */
function replacedAssetPayload(): string | undefined {
  if (!showReplacedAsset.value) return undefined
  if (replacementDriven.value) return derivedReplacedAsset.value
  return form.replacedAsset.trim() || undefined
}
/** 按类型显隐：调拨附加信息 */
const showTransferMeta = computed(() => form.woType === '调拨')
/** 行项目目标位置列仅「上架部署/变更/调拨」展示 */
const showItemDst = computed(() => showTargetRack.value)

interface DetailRow {
  materialCode: string
  qty: number | undefined
  options: MaterialOption[]
  optionsLoading: boolean
}

/** 创建表单资产行 */
interface AssetFormRow {
  assetNo: string
  name?: string
  /** 源位置（选中资产后回填，调拨用） */
  srcRoom?: string
  srcRack?: string
  srcUnit?: number
  srcRegion?: string
  /** 资产 U 高（选中资产后回填；空闲 U 位按此过滤，避免给出放不下的起始位） */
  uHeight?: number
  /** 目标机房（调拨级联：先选机房再筛机柜） */
  dstRoom: string
  dstRack: string
  dstUnit?: number
  unitOptions: number[]
  unitOptionsLoading: boolean
}

function newAssetRow(assetNo = '', name?: string): AssetFormRow {
  return {
    assetNo,
    name,
    srcRoom: undefined,
    srcRack: undefined,
    srcUnit: undefined,
    srcRegion: undefined,
    uHeight: undefined,
    dstRoom: '',
    dstRack: '',
    dstUnit: undefined,
    unitOptions: [],
    unitOptionsLoading: false,
  }
}

function newDetailRow(): DetailRow {
  return { materialCode: '', qty: undefined, options: [], optionsLoading: false }
}

function stockOf(row: DetailRow): number | undefined {
  return row.options.find((o) => o.materialCode === row.materialCode)?.stock
}

function addDetailRow() {
  form.details.push(newDetailRow())
}

function removeDetailRow(index: number) {
  form.details.splice(index, 1)
}

/* 关联资产远程搜索 */
const assetOptions = ref<AssetItem[]>([])
const assetLoading = ref(false)
let assetTimer: number | undefined

async function loadAssetOptions(keyword: string) {
  assetLoading.value = true
  try {
    const res = await fetchAssets({ page: 1, size: 50, keyword: keyword || undefined })
    assetOptions.value = res.records ?? []
  } catch {
    assetOptions.value = []
  } finally {
    assetLoading.value = false
  }
}

function onAssetSearch(keyword: string) {
  window.clearTimeout(assetTimer)
  assetTimer = window.setTimeout(() => void loadAssetOptions(keyword), 300)
}

function onAssetFocus() {
  void loadAssetOptions('')
}

function addAssetRow() {
  form.assetRows.push(newAssetRow())
}

function removeAssetRow(index: number) {
  form.assetRows.splice(index, 1)
}

/** 远程下拉选中资产 → 写入当前行（含源位置回填，调拨范围判定用） */
function onAssetPicked(row: AssetFormRow, assetNo: string) {
  const hit = assetOptions.value.find((a) => a.assetNo === assetNo)
  row.assetNo = assetNo
  row.name = hit?.name
  row.srcRoom = hit?.roomCode
  row.srcRack = hit?.rackCode
  row.srcUnit = hit?.unitNo
  row.srcRegion = hit?.regionCode
  row.uHeight = hit?.uHeight
  // 资产换了 → 该行已选机柜的可起始 U 位要按新高度重算
  if (row.dstRack) void loadRowUnitOptions(row)
}

/** 主表 U 位计算基准 = 首个已选资产（后端 insertAssetItems：主表 targetUnit 只回填首行）
 *  uHeight 用于过滤可起始位，assetNo 用于移位场景豁免自身占用 */
const firstAsset = computed(() => {
  const r = form.assetRows.find((x) => x.assetNo)
  return r ? { assetNo: r.assetNo, uHeight: r.uHeight || 1 } : null
})
watch(firstAsset, () => {
  if (form.targetRack) void loadUnitOptions()
})

/** 行目标机柜变更 → 重载该行空闲 U 位；调拨时同步回填 dstRoom */
async function loadRowUnitOptions(row: AssetFormRow) {
  row.dstUnit = undefined
  row.unitOptions = []
  if (!row.dstRack) return
  const rackLoc = transferRacks.value.find((r) => r.rackCode === row.dstRack)
  if (showTransferMeta.value && rackLoc?.roomCode) {
    row.dstRoom = rackLoc.roomCode
  }
  row.unitOptionsLoading = true
  try {
    row.unitOptions = await fetchRackFreeUnits(row.dstRack, {
      uHeight: row.uHeight || 1,
      excludeAssetNo: row.assetNo || undefined,
    })
  } catch {
    row.unitOptions = []
  } finally {
    row.unitOptionsLoading = false
  }
}

/* 用料明细物料远程搜索（同批量出入库写法） */
let detailTimer: number | undefined

async function searchMaterialOptions(keyword: string): Promise<MaterialOption[]> {
  try {
    return await fetchMaterialOptions({ keyword: keyword || undefined, size: 50 })
  } catch {
    return []
  }
}

function onDetailMaterialSearch(row: DetailRow, keyword: string) {
  row.optionsLoading = true
  window.clearTimeout(detailTimer)
  detailTimer = window.setTimeout(() => {
    searchMaterialOptions(keyword)
      .then((list) => {
        row.options = list
      })
      .finally(() => {
        row.optionsLoading = false
      })
  }, 300)
}

function onDetailMaterialFocus(row: DetailRow) {
  row.optionsLoading = true
  searchMaterialOptions('')
    .then((list) => {
      row.options = list
    })
    .finally(() => {
      row.optionsLoading = false
    })
}

/* 目标机柜远程搜索 + 空闲U位联动 */
const rackOptions = ref<RackItem[]>([])
const rackOptionsLoading = ref(false)
let rackSearchTimer: number | undefined

async function loadRackOptions(keyword: string) {
  rackOptionsLoading.value = true
  try {
    const res = await fetchRacks({ page: 1, size: 50, keyword: keyword || undefined })
    rackOptions.value = res.records ?? []
  } catch {
    rackOptions.value = []
  } finally {
    rackOptionsLoading.value = false
  }
}

function onRackSearch(keyword: string) {
  window.clearTimeout(rackSearchTimer)
  rackSearchTimer = window.setTimeout(() => void loadRackOptions(keyword), 300)
}

function onRackFocus() {
  void loadRackOptions('')
}

/* ---------------- 调拨目的地（机房 → 机柜级联） ---------------- */
const transferLocs = ref<TransferLocations | null>(null)
const transferLocsLoading = ref(false)
let transferLocsLoaded = false

async function ensureTransferLocations() {
  if (transferLocsLoaded && transferLocs.value) return
  transferLocsLoading.value = true
  try {
    transferLocs.value = await fetchTransferLocations()
    transferLocsLoaded = true
  } catch {
    transferLocs.value = { rooms: [], racks: [] }
  } finally {
    transferLocsLoading.value = false
  }
}

const transferRooms = computed<TransferRoomLocation[]>(() => transferLocs.value?.rooms ?? [])
const transferRacks = computed<TransferRackLocation[]>(() => transferLocs.value?.racks ?? [])

/** 按目标机房过滤的机柜选项（主表 + 行内共用） */
function racksOfRoom(roomCode?: string | null): TransferRackLocation[] {
  if (!roomCode) return transferRacks.value
  return transferRacks.value.filter((r) => r.roomCode === roomCode)
}

/** 机房展示：编码 + 名称 + DC */
function roomLabel(r: TransferRoomLocation): string {
  const parts = [r.roomCode]
  if (r.name) parts.push(r.name)
  if (r.dataCenter) parts.push(r.dataCenter)
  return parts.join(' / ')
}

/** 主表：选择目标机房后清空已选机柜/U位 */
function onMainDstRoomChange() {
  form.targetRack = ''
  form.targetUnit = undefined
  unitOptions.value = []
}

/** 行内：选择目标机房后清空该行机柜/U位 */
function onRowDstRoomChange(row: AssetFormRow) {
  row.dstRack = ''
  row.dstUnit = undefined
  row.unitOptions = []
}

const unitOptions = ref<number[]>([])
const unitOptionsLoading = ref(false)

async function loadUnitOptions() {
  if (!form.targetRack) {
    unitOptions.value = []
    return
  }
  unitOptionsLoading.value = true
  try {
    unitOptions.value = await fetchRackFreeUnits(form.targetRack, {
      uHeight: firstAsset.value?.uHeight || 1,
      excludeAssetNo: firstAsset.value?.assetNo,
    })
  } catch {
    unitOptions.value = []
  } finally {
    unitOptionsLoading.value = false
  }
}

function onFormRackChange(val: string) {
  form.targetUnit = undefined
  unitOptions.value = []
  if (val) {
    const hit = transferRacks.value.find((r) => r.rackCode === val)
    if (showTransferMeta.value && hit?.roomCode) {
      form.targetDstRoom = hit.roomCode
    }
    void loadUnitOptions()
  }
}


/** 行级调拨范围：按源机房/目标机房 + 目标机房元数据判定（缺任一侧不推断） */
function rowScope(row: AssetFormRow): TransferScopeLabel | null {
  const srcRoom = row.srcRoom?.trim()
  const dstRoom =
    row.dstRoom?.trim() ||
    racksOfRoom('').find((r) => r.rackCode === row.dstRack)?.roomCode ||
    form.targetDstRoom?.trim()
  if (!srcRoom || !dstRoom) return null
  if (srcRoom === dstRoom) return '同机房'
  const srcRoomMeta = transferRooms.value.find((r) => r.roomCode === srcRoom)
  const dstRoomMeta = transferRooms.value.find((r) => r.roomCode === dstRoom)
  if (
    srcRoomMeta?.regionCode &&
    dstRoomMeta?.regionCode &&
    srcRoomMeta.regionCode !== dstRoomMeta.regionCode
  ) {
    return '跨地域'
  }
  if (
    srcRoomMeta?.dataCenter &&
    dstRoomMeta?.dataCenter &&
    srcRoomMeta.dataCenter !== dstRoomMeta.dataCenter
  ) {
    return '跨数据中心'
  }
  return '跨机房'
}

/** 整单范围（取最高级别；缺位置行跳过） */
const formScope = computed<TransferScopeLabel | null>(() => {
  if (!showTransferMeta.value) return null
  const order: TransferScopeLabel[] = ['同机房', '跨机房', '跨数据中心', '跨地域']
  let worst: TransferScopeLabel | null = null
  for (const r of form.assetRows) {
    const sc = rowScope(r)
    if (!sc) continue
    if (!worst || order.indexOf(sc) > order.indexOf(worst)) worst = sc
  }
  return worst
})


/* 类型切换清理不适用字段 */
watch(
  () => form.woType,
  (t) => {
    if (!showTargetRack.value) {
      form.targetDstRoom = ''
      form.targetRack = ''
      form.targetUnit = undefined
      unitOptions.value = []
    }
    if (!showDetails.value) form.details = []
    if (!showRelatedAssets.value) form.assetRows = []
    if (!showTransferMeta.value) {
      form.transferReason = ''
      form.needDowntime = false
      form.downtimeWindow = ''
      form.transportMode = ''
      form.carrier = ''
      form.contact = ''
      form.targetDstRoom = ''
      for (const r of form.assetRows) {
        r.dstRoom = ''
        r.dstRack = ''
        r.dstUnit = undefined
        r.unitOptions = []
      }
    }
    if (t === '调拨') void ensureTransferLocations()
  },
)

function resetForm() {
  Object.assign(form, {
    woType: '故障维修',
    title: '',
    description: '',
    priority: '中',
    expectFinish: '',
    assetRows: [newAssetRow()],
    targetDstRoom: '',
    targetRack: '',
    targetUnit: undefined,
    details: [newDetailRow()],
    parentWo: '',
    replacedAsset: '',
    transferReason: '',
    needDowntime: false,
    downtimeWindow: '',
    transportMode: '',
    carrier: '',
    contact: '',
    approvalApprovers: [],
  })
  unitOptions.value = []
  assetOptions.value = []
  replacementRows.value = []
}

function open(prefill?: WoCreatePrefill) {
  resetForm()
  void ensureWorkflowConfig()
  void searchApprovers('')
  if (prefill) {
    if (prefill.parentWo) form.parentWo = prefill.parentWo
    if (prefill.woType) form.woType = prefill.woType
    if (prefill.title) form.title = prefill.title
    if (prefill.assetNos?.length) form.assetRows = prefill.assetNos.map((a) => newAssetRow(a))
    if (form.assetRows.length) void loadAssetOptions('')
  }
  dialogVisible.value = true
  void ensureTransferLocations()
}

defineExpose({ open })

async function save() {
  const valid = await formRef.value?.validate().then(() => true).catch(() => false)
  if (!valid) return
  if (showDetails.value) {
    const filled = form.details.filter((r) => r.materialCode || r.qty)
    if (requireDetails.value) {
      if (!form.details.length) {
        ElMessage.warning('请至少添加一行用料明细')
        return
      }
      for (let i = 0; i < form.details.length; i++) {
        const row = form.details[i]
        if (!row.materialCode) {
          ElMessage.warning(`第 ${i + 1} 行请选择物料`)
          return
        }
        if (!row.qty || row.qty <= 0) {
          ElMessage.warning(`第 ${i + 1} 行请输入大于 0 的数量`)
          return
        }
      }
    } else if (filled.length) {
      for (let i = 0; i < form.details.length; i++) {
        const row = form.details[i]
        if (!row.materialCode || !row.qty || row.qty <= 0) {
          if (row.materialCode || row.qty) {
            ElMessage.warning(`用料明细第 ${i + 1} 行不完整`)
            return
          }
        }
      }
    }
  }
  const assetRows = showRelatedAssets.value
    ? form.assetRows.filter((r) => r.assetNo)
    : []
  if (showRelatedAssets.value && form.assetRows.some((r) => !r.assetNo)) {
    ElMessage.warning('存在未选择资产的行，请补齐或删除空行')
    return
  }
  if (showItemDst.value) {
    const seen = new Set<string>()
    for (let i = 0; i < assetRows.length; i++) {
      const r = assetRows[i]
      if (seen.has(r.assetNo)) {
        ElMessage.warning(`第 ${i + 1} 行资产重复：${r.assetNo}`)
        return
      }
      seen.add(r.assetNo)
      // 调拨目标机柜可留空 = 到场暂存（只到机房、不占U位）；但目标机房必须有
      if (form.woType === '调拨' && !r.dstRoom && !form.targetDstRoom && !r.dstRack && !form.targetRack) {
        ElMessage.warning(`调拨第 ${i + 1} 行请先选择目标机房（可不选机柜 = 到场暂存）`)
        return
      }
    }
  }
  // 换件明细（故障维修/部件更换）：提交前先按后端同口径校验，给中文提示而非等 400/409
  let replacementsPayload: WoReplacement[] | undefined
  if (showSwapReplacements.value && replacementRows.value.some((r) => r.parentAssetNo || r.oldAssetNo || r.newAssetNo)) {
    const repErr = validateReplacementRows(replacementRows.value, assetRows.map((r) => r.assetNo))
    if (repErr) {
      ElMessage.warning(repErr)
      return
    }
    replacementsPayload = deriveReplacementPayload()
  }
  const assets: WoAssetLineInput[] = assetRows.map((r) => {
    const dstRack = showItemDst.value && r.dstRack ? r.dstRack : undefined
    const dstRoom =
      showTransferMeta.value && (r.dstRoom || (dstRack && racksOfRoom('').find((x) => x.rackCode === dstRack)?.roomCode) || undefined)
    return {
      assetNo: r.assetNo,
      dstRoom: dstRoom || (showTransferMeta.value && form.targetDstRoom ? form.targetDstRoom : undefined),
      dstRack,
      dstUnit: showItemDst.value && r.dstUnit ? r.dstUnit : undefined,
    }
  })
  let detailsPayload: { materialCode: string; qty: number }[] | undefined
  if (showDetails.value) {
    const rows = form.details.filter((r) => r.materialCode && r.qty)
    if (rows.length) {
      detailsPayload = rows.map((r) => ({ materialCode: r.materialCode, qty: r.qty as number }))
    }
  }
  if (showTransferMeta.value) {
    const meta: Record<string, unknown> = {}
    if (form.transferReason.trim()) meta.reason = form.transferReason.trim()
    meta.needDowntime = form.needDowntime
    if (form.downtimeWindow.trim()) meta.downtimeWindow = form.downtimeWindow.trim()
    if (form.transportMode) meta.transportMode = form.transportMode
    if (form.carrier.trim()) meta.carrier = form.carrier.trim()
    if (form.contact.trim()) meta.contact = form.contact.trim()
    detailsPayload = [...(detailsPayload || []), meta as never]
  }
  saving.value = true
  try {
    const res = await createWorkorder({
      woType: form.woType,
      title: form.title.trim(),
      description: form.description || undefined,
      priority: form.priority || undefined,
      assets: assets.length ? assets : undefined,
      targetRack: showTargetRack.value && form.targetRack ? form.targetRack : undefined,
      targetUnit: showTargetRack.value && form.targetUnit ? form.targetUnit : undefined,
      expectFinish: form.expectFinish || undefined,
      details: detailsPayload,
      parentWo: form.parentWo || undefined,
      replacedAsset: replacedAssetPayload(),
      replacements: replacementsPayload,
      approvalApprovers:
        needApprovalNow.value && form.approvalApprovers.length
          ? [...form.approvalApprovers]
          : undefined,
    })
    ElMessage.success('创建成功')
    dialogVisible.value = false
    emit('created', res.woNo)
  } catch {
    // 拦截器已提示
  } finally {
    saving.value = false
  }
}
</script>

<template>
    <el-dialog v-model="dialogVisible" title="创建工单" width="720px" destroy-on-close>
      <el-form ref="formRef" :model="form" :rules="rules" label-width="92px">
        <el-form-item label="类型" required>
          <el-radio-group v-model="form.woType">
            <el-radio v-for="t in WO_TYPES" :key="t" :value="t">{{ t }}</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-row :gutter="12">
          <el-col :span="12">
            <el-form-item label="标题" prop="title">
              <el-input v-model="form.title" placeholder="必填" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="优先级">
              <el-select v-model="form.priority" style="width: 100%">
                <el-option v-for="p in WO_PRIORITIES" :key="p" :label="p" :value="p" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item v-if="auth.fieldEditable('workorders', 'expectFinish')" label="期望完成">
              <el-date-picker
                v-model="form.expectFinish"
                type="date"
                value-format="YYYY-MM-DD"
                placeholder="选择日期"
                style="width: 100%"
              />
            </el-form-item>
          </el-col>
          <el-col
            v-if="needApprovalNow"
            :span="12"
            title="本单指定审批人：选了就由这些人本级或签审批（跳过角色链）；不选则按配置的角色链"
          >
            <el-form-item label="指定审批人">
              <el-select
                v-model="form.approvalApprovers"
                multiple
                filterable
                remote
                clearable
                :remote-method="searchApprovers"
                :loading="approverLoading"
                placeholder="可选；不选=按角色链"
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
          </el-col>
          <el-col v-if="showTransferMeta" :span="12">
            <el-form-item label="目标机房">
              <el-select
                v-model="form.targetDstRoom"
                clearable
                filterable
                :loading="transferLocsLoading"
                placeholder="选择目标机房"
                style="width: 100%"
                @change="onMainDstRoomChange"
              >
                <el-option
                  v-for="r in transferRooms"
                  :key="r.roomCode"
                  :label="roomLabel(r)"
                  :value="r.roomCode"
                />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col v-if="showTargetRack" :span="12">
            <el-form-item label="目标机柜">
              <el-select
                v-model="form.targetRack"
                clearable
                filterable
                :remote="!showTransferMeta"
                :remote-method="onRackSearch"
                :disabled="showTransferMeta && !form.targetDstRoom"
                :loading="transferLocsLoading || rackOptionsLoading"
                :placeholder="
                  showTransferMeta && !form.targetDstRoom
                    ? '先选择目标机房'
                    : showTransferMeta
                      ? '机柜（留空=暂存）'
                      : '输入编码搜索机柜'
                "
                style="width: 100%"
                @focus="!showTransferMeta && onRackFocus()"
                @change="onFormRackChange"
              >
                <el-option
                  v-for="r in showTransferMeta ? racksOfRoom(form.targetDstRoom) : rackOptions"
                  :key="r.rackCode"
                  :label="showTransferMeta && r.locationName ? `${r.rackCode}（${r.locationName}）` : r.rackCode"
                  :value="r.rackCode"
                />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col v-if="showTargetRack" :span="12">
            <el-form-item label="起始U位">
              <el-select
                v-model="form.targetUnit"
                clearable
                :disabled="!form.targetRack"
                :loading="unitOptionsLoading"
                :placeholder="form.targetRack ? '选择起始U位' : '先选择机柜'"
                style="width: 100%"
              >
                <el-option v-for="u in unitOptions" :key="u" :label="`U${u}`" :value="u" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col v-if="showReplacedAsset" :span="12">
            <el-form-item label="更换部件">
              <el-input
                :model-value="replacementDriven ? derivedReplacedAsset : form.replacedAsset"
                :disabled="replacementDriven"
                :placeholder="
                  replacementDriven
                    ? '由下方「更换部件明细」自动带出'
                    : '被更换部件编号（选填，多件用逗号分隔或用下方明细）'
                "
                @input="(v: string) => (form.replacedAsset = v)"
              />
            </el-form-item>
          </el-col>
          <el-col v-if="form.parentWo" :span="12">
            <el-form-item label="父工单">
              <el-input :model-value="form.parentWo" disabled />
            </el-form-item>
          </el-col>
          <template v-if="showTransferMeta">
            <el-col :span="24">
              <el-form-item label="调拨范围">
                <el-tag v-if="formScope" :type="scopeTag(formScope)" size="small">
                  {{ formScope }}
                </el-tag>
                <span v-else class="detail-hint" style="margin-left: 0">选择资产与目标机房后自动判定</span>
              </el-form-item>
            </el-col>
            <el-col :span="24">
              <el-form-item label="调拨原因">
                <el-input v-model="form.transferReason" type="textarea" :rows="2" placeholder="调拨原因（选填，跨地域建议填写）" />
              </el-form-item>
            </el-col>
            <el-col :span="12">
              <el-form-item label="是否停机">
                <el-switch v-model="form.needDowntime" />
              </el-form-item>
            </el-col>
            <el-col :span="12">
              <el-form-item label="停机窗口">
                <el-input v-model="form.downtimeWindow" placeholder="如 周六 02:00-06:00" />
              </el-form-item>
            </el-col>
            <el-col :span="12">
              <el-form-item label="运输方式">
                <el-select v-model="form.transportMode" clearable placeholder="选填" style="width: 100%">
                  <el-option label="自送" value="SELF" />
                  <el-option label="承运商" value="CARRIER" />
                  <el-option label="供应商" value="VENDOR" />
                </el-select>
              </el-form-item>
            </el-col>
            <el-col :span="12">
              <el-form-item label="承运方">
                <el-input v-model="form.carrier" placeholder="物流公司/司机（选填）" />
              </el-form-item>
            </el-col>
            <el-col :span="12">
              <el-form-item label="联系人">
                <el-input v-model="form.contact" placeholder="收货/交接联系人（选填）" />
              </el-form-item>
            </el-col>
          </template>
          <el-col :span="24">
            <el-form-item v-if="auth.fieldEditable('workorders', 'description')" label="描述">
              <el-input v-model="form.description" type="textarea" :rows="3" placeholder="工单描述（选填）" />
            </el-form-item>
          </el-col>
        </el-row>

        <!-- 资产行项目（多资产） -->
        <div v-if="showRelatedAssets" class="detail-block">
          <div class="detail-block-head">
            <span class="detail-block-title">关联资产</span>
            <el-button type="primary" link @click="addAssetRow">添加行</el-button>
          </div>
          <div v-for="(row, idx) in form.assetRows" :key="idx" class="detail-row">
            <span class="detail-row-no">{{ idx + 1 }}</span>
            <el-select
              v-model="row.assetNo"
              filterable
              remote
              clearable
              :remote-method="onAssetSearch"
              :loading="assetLoading"
              placeholder="搜索资产编号 / SN / 名称"
              style="flex: 1"
              @focus="onAssetFocus"
              @change="(v: string) => onAssetPicked(row, v)"
            >
              <el-option
                v-for="a in assetOptions"
                :key="a.assetNo"
                :label="`${a.assetNo} ${a.name}`"
                :value="a.assetNo"
              />
            </el-select>
            <span v-if="showTransferMeta && (row.srcRoom || row.srcRack)" class="src-loc">
              源：{{ row.srcRoom || '-' }} / {{ row.srcRack || '-' }}<template v-if="row.srcUnit != null"> / U{{ row.srcUnit }}</template>
            </span>
            <template v-if="showItemDst">
              <el-select
                v-if="showTransferMeta"
                v-model="row.dstRoom"
                clearable
                filterable
                :loading="transferLocsLoading"
                placeholder="目标机房"
                style="width: 150px"
                @change="onRowDstRoomChange(row)"
              >
                <el-option
                  v-for="r in transferRooms"
                  :key="r.roomCode"
                  :label="roomLabel(r)"
                  :value="r.roomCode"
                />
              </el-select>
              <el-select
                v-model="row.dstRack"
                clearable
                filterable
                :remote="!showTransferMeta"
                :remote-method="onRackSearch"
                :disabled="showTransferMeta && !row.dstRoom"
                :loading="transferLocsLoading || rackOptionsLoading"
                :placeholder="
                  showTransferMeta && !row.dstRoom
                    ? '先选机房'
                    : showTransferMeta
                      ? '机柜（留空=暂存）'
                      : '目标机柜'
                "
                style="width: 160px"
                @focus="!showTransferMeta && onRackFocus()"
                @change="() => loadRowUnitOptions(row)"
              >
                <el-option
                  v-for="r in showTransferMeta ? racksOfRoom(row.dstRoom) : rackOptions"
                  :key="r.rackCode"
                  :label="showTransferMeta && r.locationName ? `${r.rackCode}（${r.locationName}）` : r.rackCode"
                  :value="r.rackCode"
                />
              </el-select>
              <el-select
                v-model="row.dstUnit"
                clearable
                :disabled="!row.dstRack"
                :loading="row.unitOptionsLoading"
                :placeholder="row.dstRack ? 'U位' : '选机柜'"
                style="width: 100px"
              >
                <el-option v-for="u in row.unitOptions" :key="u" :label="`U${u}`" :value="u" />
              </el-select>
              <el-tag
                v-if="showTransferMeta && rowScope(row)"
                :type="scopeTag(rowScope(row))"
                size="small"
              >
                {{ rowScope(row) }}
              </el-tag>
              <el-tag
                v-if="showTransferMeta && (row.dstRoom || form.targetDstRoom) && !row.dstRack && !form.targetRack"
                type="info"
                size="small"
              >
                到场暂存
              </el-tag>
            </template>
            <el-button
              type="danger"
              link
              :disabled="form.assetRows.length <= 1"
              @click="removeAssetRow(idx)"
            >
              删除行
            </el-button>
          </div>
          <div v-if="showItemDst" class="detail-hint">
            <template v-if="form.woType === '调拨'">
              调拨每行须有目标机房（可用上方统一指定）；<b>目标机柜可留空 = 到场暂存</b>：完工后资产迁到目标机房、
              释放原机柜占位但不占 U 位，待后续「上架部署」工单再上架。跨数据中心/跨地域或勾选停机时，
              进入处理中自动临时停用，完工恢复在用
            </template>
            <template v-else>
              行内目标机柜/U位可留空，完工时回退上方「目标机柜 + 起始U位」并按行顺延
            </template>
          </div>
        </div>

        <!-- 更换部件明细（故障维修 / 部件更换）：一台设备换多个部件 = 多加几行 -->
        <div v-if="showSwapReplacements" class="detail-block">
          <div class="detail-block-head">
            <span class="detail-block-title">
              更换部件明细{{ replacementRows.length ? `（${replacementRows.length} 行）` : '（选填）' }}
            </span>
          </div>
          <ReplacementEditor
            v-model:rows="replacementRows"
            :parent-options="replacementParentOptions"
          />
          <div v-if="!replacementParentOptions.length" class="detail-hint">
            先在上方「关联资产」添加要维修的设备，才能在这里选父资产
          </div>
          <div v-else-if="!replacementRows.length" class="detail-hint">
            换几个部件就加几行：每行选 父资产 → 旧件 → 新件，父资产可相同（同一台设备连换多件）；
            也可以不填明细，只在上方「更换部件」写一个件号
          </div>
        </div>

        <!-- 用料明细（耗材申领扣料 / 故障维修·部件更换展示） -->
        <div v-if="showDetails" class="detail-block">
          <div class="detail-block-head">
            <span class="detail-block-title">
              用料明细{{
                form.woType === '故障维修' || form.woType === '部件更换'
                  ? '（选填，完成工单时自动出库扣减）'
                  : ''
              }}
            </span>
            <el-button type="primary" link @click="addDetailRow">添加行</el-button>
          </div>
          <div v-for="(row, idx) in form.details" :key="idx" class="detail-row">
            <span class="detail-row-no">{{ idx + 1 }}</span>
            <el-select
              v-model="row.materialCode"
              filterable
              remote
              clearable
              :remote-method="(q: string) => onDetailMaterialSearch(row, q)"
              :loading="row.optionsLoading"
              placeholder="搜索物料"
              style="flex: 1"
              @focus="onDetailMaterialFocus(row)"
            >
              <el-option
                v-for="m in row.options"
                :key="m.materialCode"
                :label="`${m.name}（${m.materialCode}）`"
                :value="m.materialCode"
              >
                <span>{{ m.name }}（{{ m.materialCode }}）</span>
                <span class="option-stock">库存{{ m.stock }}{{ m.unit }}</span>
              </el-option>
            </el-select>
            <el-input-number
              v-model="row.qty"
              :min="1"
              placeholder="数量"
              controls-position="right"
              style="width: 140px"
            />
            <el-tag
              v-if="stockOf(row) != null && row.qty != null && row.qty > (stockOf(row) ?? 0)"
              type="danger"
              size="small"
            >
              库存不足 {{ stockOf(row) }}
            </el-tag>
            <el-button type="danger" link :disabled="form.details.length <= 1" @click="removeDetailRow(idx)">
              删除行
            </el-button>
          </div>
        </div>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="save">创建</el-button>
      </template>
    </el-dialog>
</template>

<style scoped>
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

.detail-row {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 8px;
}

.detail-row-no {
  width: 20px;
  font-size: 12px;
  color: var(--el-text-color-secondary);
  text-align: center;
}

.detail-hint {
  margin-left: 28px;
  font-size: 12px;
  color: var(--el-text-color-secondary);
  line-height: 1.5;
}

.src-loc {
  font-size: 12px;
  color: var(--el-text-color-regular);
  white-space: nowrap;
}

.option-stock {
  float: right;
  font-size: 12px;
  color: var(--el-text-color-secondary);
}
</style>
