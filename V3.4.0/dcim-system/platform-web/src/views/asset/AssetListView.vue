<script setup lang="ts">
import { computed, nextTick, onMounted, reactive, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import type { FormInstance, FormRules, UploadFile } from 'element-plus'
import { useAuthStore } from '@/stores/auth'
import type { PageResult } from '@/api/types'
import {
  batchDeleteAssets,
  batchTransitionAssets,
  batchUpdateAssets,
  createAsset,
  deleteAsset,
  exportAssets,
  fetchAssetDetail,
  fetchAssetEvents,
  fetchAssetMeta,
  fetchAssetModelCodes,
  fetchAssets,
  fetchAssetTree,
  fetchImportTemplate,
  importAssets,
  saveBlob,
  transitionAsset,
  updateAsset,
} from '@/api/asset'
import type {
  AssetBatchResult,
  AssetBatchTransitionPayload,
  AssetBatchUpdatePayload,
  AssetDetail,
  AssetEvent,
  AssetImportResult,
  AssetItem,
  AssetMeta,
  AssetTransitionPayload,
} from '@/api/asset'
import { fetchCategoryTree } from '@/api/masterdata'
import type { CategoryNode } from '@/api/masterdata'
import { fetchRackFreeUnits, fetchRacks, fetchRooms } from '@/api/room'
import type { RackItem, RoomItem } from '@/api/room'
import { downloadQrcode, openPrintPage, renderToCanvas } from '@/utils/qrcode'
import { assetEventLabel } from '@/utils/assetEvents'
import { checkCsvImportSize } from '@/utils/upload'

const auth = useAuthStore()
/** v3.4：字段级权限——隐藏字段不显示，只读字段不可编辑（后端同样校验） */
const fv = (f: string) => auth.fieldVisible('assets', f)
const fe = (f: string) => auth.fieldEditable('assets', f)

type TagType = 'primary' | 'success' | 'warning' | 'info' | 'danger'

/* ---------------- 元数据 / 分类 ---------------- */
const meta = ref<AssetMeta | null>(null)
const categoryTree = ref<CategoryNode[]>([])

interface CategoryFlatItem {
  categoryCode: string
  label: string
}

const categoryFlat = ref<CategoryFlatItem[]>([])
const categoryNameMap = computed(() => {
  const map = new Map<string, string>()
  for (const n of categoryTree.value) collectNames(n, map)
  return map
})

function collectNames(node: CategoryNode, map: Map<string, string>) {
  map.set(node.categoryCode, node.name)
  for (const c of node.children || []) collectNames(c, map)
}

function flattenCategories(nodes: CategoryNode[], level: number, out: CategoryFlatItem[]) {
  for (const n of nodes) {
    out.push({ categoryCode: n.categoryCode, label: '　'.repeat(level) + n.name })
    if (n.children?.length) flattenCategories(n.children, level + 1, out)
  }
}

async function loadMeta() {
  try {
    meta.value = await fetchAssetMeta()
  } catch {
    // 拦截器已提示
  }
}

async function loadCategories() {
  try {
    categoryTree.value = (await fetchCategoryTree()) || []
    const flat: CategoryFlatItem[] = []
    flattenCategories(categoryTree.value, 0, flat)
    categoryFlat.value = flat
  } catch {
    // 拦截器已提示
  }
}

/* ---------------- 状态展示 ---------------- */
function stateTagType(state?: string): TagType {
  const s = state || ''
  if (!s) return 'info'
  // 终态（已报废 / 已退租）灰色
  if (s.includes('报废') || s.includes('退租')) return 'info'
  if (s.includes('维修') || s.includes('故障') || s.includes('下线') || s.includes('处置')) return 'danger'
  if (s.includes('在用') || s.includes('上线') || s.includes('运行') || s.includes('使用')) return 'success'
  if (s.includes('待') || s.includes('库存') || s.includes('预') || s.includes('申请')) return 'warning'
  return 'primary'
}

function regionLabel(code?: string): string {
  if (!code) return '-'
  return meta.value?.regions.find((r) => r.key === code)?.label || code
}

function ownerTypeLabel(v?: string): string {
  if (v === 'SELF') return '自持'
  if (v === 'LEASE') return '租赁'
  return v || '-'
}

/* ---------------- 列表查询 ---------------- */
const query = reactive({
  page: 1,
  size: 20,
  keyword: '',
  statusBiz: '',
  regionCode: '',
  categoryCode: '',
  parentAssetNo: '',
})
const total = ref(0)
const records = ref<AssetItem[]>([])
const listLoading = ref(false)
/** 列表视图：flat 平铺 / tree 整机树 */
const viewMode = ref<'flat' | 'tree'>('flat')
const treeData = ref<import('@/api/asset').AssetTreeItem[]>([])
const treeLoading = ref(false)

/** 列表请求序号：快速切换筛选/翻页时仅接受最后一次请求的响应（丢弃过期响应） */
let listSeq = 0

async function load() {
  const seq = ++listSeq
  if (viewMode.value === 'tree') {
    await loadTree()
    return
  }
  listLoading.value = true
  try {
    const res = await fetchAssets({
      page: query.page,
      size: query.size,
      keyword: query.keyword || undefined,
      statusBiz: query.statusBiz || undefined,
      regionCode: query.regionCode || undefined,
      categoryCode: query.categoryCode || undefined,
      parentAssetNo: query.parentAssetNo || undefined,
    })
    if (seq !== listSeq) return
    total.value = res.total ?? 0
    records.value = res.records ?? []
  } catch {
    // 拦截器已提示
  } finally {
    if (seq === listSeq) listLoading.value = false
  }
}

/** 树请求序号：同上（与列表共用场景：视图模式来回切换） */
let treeSeq = 0

async function loadTree() {
  const seq = ++treeSeq
  treeLoading.value = true
  try {
    const t = (await fetchAssetTree()) || []
    if (seq !== treeSeq) return
    treeData.value = t
  } catch {
    if (seq === treeSeq) treeData.value = []
  } finally {
    if (seq === treeSeq) treeLoading.value = false
  }
}

function search() {
  query.page = 1
  void load()
}

function resetQuery() {
  query.keyword = ''
  query.statusBiz = ''
  query.regionCode = ''
  query.categoryCode = ''
  query.parentAssetNo = ''
  search()
}

function onViewModeChange(mode: 'flat' | 'tree') {
  viewMode.value = mode
  search()
}

/* 父资产筛选下拉（远程） */
const parentFilterOptions = ref<AssetItem[]>([])
let parentFilterTimer: number | undefined

async function loadParentFilterOptions(keyword = '') {
  try {
    const res = await fetchAssets({ page: 1, size: 30, keyword: keyword || undefined })
    parentFilterOptions.value = res.records || []
  } catch {
    parentFilterOptions.value = []
  }
}

function onParentFilterFocus() {
  void loadParentFilterOptions('')
}

function onParentFilterSearch(keyword: string) {
  window.clearTimeout(parentFilterTimer)
  parentFilterTimer = window.setTimeout(() => void loadParentFilterOptions(keyword), 300)
}

/** 列表「子资产」按钮：按父资产筛选 */
function filterChildren(row: AssetItem) {
  if (viewMode.value !== 'flat') {
    viewMode.value = 'flat'
  }
  query.parentAssetNo = row.assetNo
  search()
}

/** 从子资产跳到父资产（详情/列表） */
function openParentFromChild(row: AssetItem | AssetDetail) {
  const parentNo = row.parentAssetNo || (row as AssetDetail).parent?.assetNo
  if (!parentNo) return
  void fetchAssetDetail(parentNo)
    .then((p) => {
      openDetail(p)
    })
    .catch(() => {
      // 拦截器已提示
    })
}

function fmtPrice(v?: number): string {
  return v === undefined || v === null ? '-' : String(v)
}

/* ---------------- 详情抽屉 ---------------- */
const drawerVisible = ref(false)
const detailLoading = ref(false)
const detail = ref<AssetDetail | null>(null)
const currentAssetNo = ref('')
const events = ref<AssetEvent[]>([])
const eventTotal = ref(0)
const eventsLoading = ref(false)
const eventQuery = reactive({ page: 1, size: 10 })

/** 单调递增的请求序号：快速切换/翻页时丢弃过期响应，防止慢请求覆盖新数据 */
let eventsSeq = 0

async function loadEvents() {
  const no = currentAssetNo.value
  if (!no) return
  const seq = ++eventsSeq
  eventsLoading.value = true
  try {
    const res = await fetchAssetEvents(no, {
      page: eventQuery.page,
      size: eventQuery.size,
    })
    if (seq !== eventsSeq) return
    events.value = res.records ?? []
    eventTotal.value = res.total ?? 0
  } catch {
    // 拦截器已提示
  } finally {
    if (seq === eventsSeq) eventsLoading.value = false
  }
}

let detailSeq = 0

function openDetail(row: AssetItem) {
  currentAssetNo.value = row.assetNo
  detail.value = null
  eventQuery.page = 1
  drawerVisible.value = true
  void loadEvents()
  const no = row.assetNo
  const seq = ++detailSeq
  detailLoading.value = true
  fetchAssetDetail(no)
    .then((res) => {
      // 已切到别的资产（或又有新请求发出）→ 丢弃本次过期结果
      if (seq !== detailSeq) return
      detail.value = res
    })
    .catch(() => {
      // 拦截器已提示
    })
    .finally(() => {
      if (seq === detailSeq) detailLoading.value = false
    })
}

/* ---------------- 新增 / 编辑 ---------------- */
const dialogVisible = ref(false)
const editingAssetNo = ref('')
const editingVersion = ref(0)
const saving = ref(false)
const formRef = ref<FormInstance>()
const modelOptions = ref<string[]>([])
const modelLoading = ref(false)

/* ---------------- 登记模式与分段折叠 ---------------- */
type FormMode = 'quick' | 'full'
const SECTION_KEYS: readonly string[] = ['basic', 'position', 'purchase', 'extra']
/** 快速登记只展开基础段；完整信息 / 编辑态全展开 */
const formMode = ref<FormMode>('quick')
const activeSections = ref<string[]>(['basic'])
/** 创建后保留上下文字段继续录下一条 */
const keepCreating = ref(true)
const nameInputRef = ref<{ focus?: () => void } | null>(null)

watch(formMode, (m) => {
  activeSections.value = m === 'full' ? [...SECTION_KEYS] : ['basic']
})

const form = reactive({
  name: '',
  sn: '',
  categoryCode: '',
  modelCode: '',
  ownerType: '',
  regionCode: '',
  roomCode: '',
  rackCode: '',
  unitNo: undefined as number | undefined,
  uHeight: undefined as number | undefined,
  department: '',
  owner: '',
  mgmtIp: '',
  bizIp: '',
  purchaseDate: '',
  purchasePrice: undefined as number | undefined,
  warrantyEnd: '',
  rackDate: '',
  contractNo: '',
  ratedPowerW: undefined as number | undefined,
  parentAssetNo: '',
  attrsRemark: '',
})

/* 父资产远程搜索 */
const parentOptions = ref<AssetItem[]>([])
const parentOptionsLoading = ref(false)
let parentSearchTimer: number | undefined

async function loadParentOptions(keyword: string) {
  parentOptionsLoading.value = true
  try {
    const res = await fetchAssets({ page: 1, size: 50, keyword: keyword || undefined })
    // 编辑时排除自身，避免选到自己
    parentOptions.value = (res.records || []).filter((a) => a.assetNo !== editingAssetNo.value)
  } catch {
    parentOptions.value = []
  } finally {
    parentOptionsLoading.value = false
  }
}

function onParentSearch(keyword: string) {
  window.clearTimeout(parentSearchTimer)
  parentSearchTimer = window.setTimeout(() => void loadParentOptions(keyword), 300)
}

function onParentFocus() {
  void loadParentOptions('')
}

const rules = computed<FormRules>(() => ({
  name: [{ required: true, message: '请输入资产名称', trigger: 'blur' }],
}))

/** 型号建议值：型号库已下线，取库内已用过的 model_code（可按分类过滤），允许直接自填 */
async function loadModelOptions(categoryCode?: string) {
  modelLoading.value = true
  try {
    modelOptions.value = (await fetchAssetModelCodes({ categoryCode: categoryCode || undefined })) || []
  } catch {
    modelOptions.value = []
  } finally {
    modelLoading.value = false
  }
}

function onFormCategoryChange(code: string) {
  form.modelCode = ''
  void loadModelOptions(code)
}

/* ---------------- 位置级联：机房 → 机柜 → U 位 ---------------- */
const roomOptions = ref<RoomItem[]>([])
const roomOptionsLoading = ref(false)
let roomSearchTimer: number | undefined

async function loadRoomOptions(keyword?: string) {
  roomOptionsLoading.value = true
  try {
    const res = await fetchRooms({ page: 1, size: 50, keyword: keyword || undefined })
    roomOptions.value = res.records ?? []
  } catch {
    roomOptions.value = []
  } finally {
    roomOptionsLoading.value = false
  }
}

/** 机房下拉远程搜索（输入防抖） */
function onRoomSearch(keyword: string) {
  window.clearTimeout(roomSearchTimer)
  roomSearchTimer = window.setTimeout(() => void loadRoomOptions(keyword), 300)
}

function onRoomFocus() {
  void loadRoomOptions('')
}

/** 换机房 → 机柜与 U 位失效（机柜列表按新机房重新过滤） */
function onFormRoomChange(val: string) {
  form.rackCode = ''
  form.unitNo = undefined
  unitOptions.value = []
  rackOptions.value = []
  if (val) void loadRackOptions('')
}

/* ---------------- 表单位置区：机柜 / U位联动 ---------------- */
const rackOptions = ref<RackItem[]>([])
const rackOptionsLoading = ref(false)
let rackSearchTimer: number | undefined

async function loadRackOptions(keyword: string) {
  rackOptionsLoading.value = true
  try {
    const res = await fetchRacks({
      page: 1,
      size: 50,
      keyword: keyword || undefined,
      roomCode: form.roomCode || undefined,
    })
    rackOptions.value = res.records ?? []
  } catch {
    rackOptions.value = []
  } finally {
    rackOptionsLoading.value = false
  }
}

/** 机柜下拉远程搜索（输入防抖） */
function onRackSearch(keyword: string) {
  window.clearTimeout(rackSearchTimer)
  rackSearchTimer = window.setTimeout(() => void loadRackOptions(keyword), 300)
}

/** 聚焦时按当前关键字加载机柜选项 */
function onRackFocus() {
  void loadRackOptions('')
}

const unitOptions = ref<number[]>([])
const unitOptionsLoading = ref(false)

/** U 位下拉提示：是否已按 U高 过滤 */
const unitHint = computed(() => {
  if (!form.rackCode) return '先选机柜'
  return form.uHeight ? `已按 ${form.uHeight}U 高度过滤可用起始位` : '填 U高 可按高度精确过滤可用位'
})

/** 按所选机柜 + U高加载可用起始 U 位（编辑时排除自身占用） */
async function loadUnitOptions() {
  if (!form.rackCode) {
    unitOptions.value = []
    return
  }
  unitOptionsLoading.value = true
  try {
    unitOptions.value = await fetchRackFreeUnits(form.rackCode, {
      uHeight: form.uHeight || undefined,
      excludeAssetNo: editingAssetNo.value || undefined,
    })
  } catch {
    unitOptions.value = []
  } finally {
    unitOptionsLoading.value = false
  }
}

/** 切换 / 清空机柜：自动回填所属机房、清空起始U位（下架）并重查可选 U 位 */
function onFormRackChange(val: string) {
  form.unitNo = undefined
  unitOptions.value = []
  if (!val) return
  const rack = rackOptions.value.find((r) => r.rackCode === val)
  if (rack?.roomCode && rack.roomCode !== form.roomCode) {
    form.roomCode = rack.roomCode
    void loadRackOptions('')
  }
  void loadUnitOptions()
}

/** U高变化时重查可用起始 U 位 */
watch(
  () => form.uHeight,
  () => {
    if (dialogVisible.value && form.rackCode) void loadUnitOptions()
  },
)

function resetForm() {
  Object.assign(form, {
    name: '',
    sn: '',
    categoryCode: '',
    modelCode: '',
    ownerType: '',
    regionCode: '',
    roomCode: '',
    rackCode: '',
    unitNo: undefined,
    uHeight: undefined,
    department: '',
    owner: '',
    mgmtIp: '',
    bizIp: '',
    purchaseDate: '',
    purchasePrice: undefined,
    warrantyEnd: '',
    rackDate: '',
    contractNo: '',
    ratedPowerW: undefined,
    parentAssetNo: '',
    attrsRemark: '',
  })
}

function openCreate() {
  editingAssetNo.value = ''
  editingVersion.value = 0
  resetForm()
  formMode.value = 'quick'
  activeSections.value = ['basic']
  applyCreateDefaults()
  dialogVisible.value = true
  void loadModelOptions(form.categoryCode || '')
  void loadRoomOptions('')
}

function openEdit(row: AssetItem) {
  editingAssetNo.value = row.assetNo
  editingVersion.value = row.version ?? 0
  Object.assign(form, {
    name: row.name || '',
    sn: row.sn || '',
    categoryCode: row.categoryCode || '',
    modelCode: row.modelCode || '',
    ownerType: row.ownerType || '',
    regionCode: row.regionCode || '',
    roomCode: row.roomCode || '',
    rackCode: row.rackCode || '',
    unitNo: row.unitNo != null ? Number(row.unitNo) || undefined : undefined,
    uHeight: row.uHeight,
    department: row.department || '',
    owner: row.owner || '',
    mgmtIp: row.mgmtIp || '',
    bizIp: row.bizIp || '',
    purchaseDate: row.purchaseDate || '',
    purchasePrice: row.purchasePrice,
    warrantyEnd: row.warrantyEnd || '',
    rackDate: row.rackDate || '',
    contractNo: row.contractNo || '',
    ratedPowerW: row.ratedPowerW ?? undefined,
    parentAssetNo: row.parentAssetNo || '',
    attrsRemark:
      row.attrs && typeof row.attrs.remark === 'string' ? (row.attrs.remark as string) : '',
  })
  dialogVisible.value = true
  formMode.value = 'full'
  activeSections.value = [...SECTION_KEYS]
  void loadModelOptions(row.categoryCode)
  void loadRoomOptions('')
  if (form.roomCode) void loadRackOptions('')
  if (form.rackCode) void loadUnitOptions()
  void loadParentOptions(form.parentAssetNo || '')
}

const CREATE_DEFAULTS_KEY = 'dcim.asset.createDefaults'

/** 记住上次登记的上下文（分类/型号/区域/机房/机柜/部门/责任人/U高），下次打开自动带出 */
function applyCreateDefaults() {
  try {
    const raw = localStorage.getItem(CREATE_DEFAULTS_KEY)
    if (!raw) return
    const d = JSON.parse(raw) as Record<string, string | number | undefined>
    form.categoryCode = String(d.categoryCode || '')
    form.modelCode = String(d.modelCode || '')
    form.ownerType = String(d.ownerType || '')
    form.regionCode = String(d.regionCode || '')
    form.roomCode = String(d.roomCode || '')
    form.rackCode = String(d.rackCode || '')
    form.department = String(d.department || '')
    form.owner = String(d.owner || '')
    form.uHeight = d.uHeight ? Number(d.uHeight) : undefined
  } catch {
    // 本地偏好损坏则忽略，回到空白表单
  }
}

function persistCreateDefaults() {
  try {
    localStorage.setItem(
      CREATE_DEFAULTS_KEY,
      JSON.stringify({
        categoryCode: form.categoryCode,
        modelCode: form.modelCode,
        ownerType: form.ownerType,
        regionCode: form.regionCode,
        roomCode: form.roomCode,
        rackCode: form.rackCode,
        department: form.department,
        owner: form.owner,
        uHeight: form.uHeight,
      }),
    )
  } catch {
    // 本地存储不可用则忽略
  }
}

/** 连续录入：只清空本次填的，保留上下文字段，焦点回到名称 */
function resetForNextCreate() {
  Object.assign(form, {
    name: '',
    sn: '',
    purchaseDate: '',
    purchasePrice: undefined,
    warrantyEnd: '',
    rackDate: '',
    contractNo: '',
    parentAssetNo: '',
    attrsRemark: '',
    unitNo: undefined,
  })
  unitOptions.value = []
  if (form.rackCode) void loadUnitOptions()
  void nextTick(() => nameInputRef.value?.focus?.())
}

function buildPayload() {
  return {
    name: form.name.trim(),
    sn: form.sn || undefined,
    categoryCode: form.categoryCode || undefined,
    modelCode: form.modelCode || undefined,
    ownerType: form.ownerType || undefined,
    regionCode: form.regionCode || undefined,
    roomCode: form.roomCode || undefined,
    rackCode: form.rackCode || undefined,
    unitNo: form.unitNo ?? undefined,
    uHeight: form.uHeight,
    department: form.department || undefined,
    owner: form.owner || undefined,
    mgmtIp: form.mgmtIp || undefined,
    bizIp: form.bizIp || undefined,
    purchaseDate: form.purchaseDate || undefined,
    purchasePrice: form.purchasePrice,
    warrantyEnd: form.warrantyEnd || undefined,
    rackDate: form.rackDate || undefined,
    contractNo: form.contractNo || undefined,
    ratedPowerW: form.ratedPowerW ?? undefined,
    // 编辑时始终带上 parentAssetNo：空串=解除挂接；新建时空=不挂
    parentAssetNo: editingAssetNo.value ? form.parentAssetNo : form.parentAssetNo || undefined,
    attrs: form.attrsRemark ? { remark: form.attrsRemark } : undefined,
  }
}

async function save() {
  const valid = await formRef.value?.validate().then(() => true).catch(() => false)
  if (!valid) return
  saving.value = true
  try {
    if (editingAssetNo.value) {
      await updateAsset(editingAssetNo.value, {
        ...buildPayload(),
        version: editingVersion.value,
      })
      ElMessage.success('保存成功')
      dialogVisible.value = false
      void load()
      return
    }
    const res = await createAsset(buildPayload())
    persistCreateDefaults()
    void load()
    if (keepCreating.value) {
      ElMessage.success(`创建成功，资产编号：${res.assetNo}，可继续录入下一条`)
      resetForNextCreate()
    } else {
      ElMessage.success(`创建成功，资产编号：${res.assetNo}`)
      dialogVisible.value = false
    }
  } catch {
    // 拦截器已提示（含 409 并发冲突的中文提示）
  } finally {
    saving.value = false
  }
}

async function remove(row: AssetItem) {
  try {
    await ElMessageBox.confirm(
      `确定删除资产「${row.assetNo} ${row.name}」吗？该操作不可恢复。`,
      '删除确认',
      { type: 'warning', confirmButtonText: '删除', cancelButtonText: '取消' },
    )
  } catch {
    return
  }
  try {
    await deleteAsset(row.assetNo)
    ElMessage.success('删除成功')
    void load()
  } catch {
    // 拦截器已提示
  }
}

/* ---------------- 状态流转 ---------------- */
const transitionVisible = ref(false)
const transitionSaving = ref(false)
const transitionAssetNo = ref('')
const transitionForm = reactive({
  targetType: 'biz' as 'biz' | 'fin',
  target: '',
  reason: '',
  refDoc: '',
  cascade: false,
})

const transitionTargetOptions = computed(() => {
  if (!meta.value) return []
  return transitionForm.targetType === 'biz' ? meta.value.bizStates : meta.value.finStates
})

function openTransition(row: AssetItem) {
  transitionAssetNo.value = row.assetNo
  Object.assign(transitionForm, { targetType: 'biz', target: '', reason: '', refDoc: '', cascade: false })
  transitionVisible.value = true
}

function onTransitionTypeChange() {
  transitionForm.target = ''
}

async function submitTransition() {
  if (!transitionForm.target) {
    ElMessage.warning('请选择目标状态')
    return
  }
  const payload: AssetTransitionPayload =
    transitionForm.targetType === 'biz'
      ? {
          targetBiz: transitionForm.target,
          reason: transitionForm.reason || undefined,
          refDoc: transitionForm.refDoc || undefined,
          cascadeChildren: transitionForm.cascade || undefined,
        }
      : {
          targetFin: transitionForm.target,
          reason: transitionForm.reason || undefined,
          refDoc: transitionForm.refDoc || undefined,
          cascadeChildren: transitionForm.cascade || undefined,
        }
  transitionSaving.value = true
  try {
    const res = (await transitionAsset(transitionAssetNo.value, payload)) as unknown as {
      cascade?: { cascaded?: unknown[]; skipped?: unknown[] }
    } | null | void
    const casc = res?.cascade
    if (casc) {
      const okN = casc.cascaded?.length ?? 0
      const skipN = casc.skipped?.length ?? 0
      ElMessage.success(
        skipN
          ? `流转成功；级联子资产 ${okN} 个，跳过 ${skipN} 个（状态机不允许）`
          : `流转成功；已级联 ${okN} 个子资产`,
      )
    } else {
      ElMessage.success('状态流转成功')
    }
    transitionVisible.value = false
    void load()
  } catch {
    // 拦截器已提示（非法迁移等后端中文报错）
  } finally {
    transitionSaving.value = false
  }
}

/* ---------------- 导入 ---------------- */
const importVisible = ref(false)
const importFile = ref<File | null>(null)
const importing = ref(false)
const importResult = ref<AssetImportResult | null>(null)

function openImport() {
  importFile.value = null
  importResult.value = null
  importVisible.value = true
}

function onImportFileChange(uploadFile: UploadFile) {
  importFile.value = (uploadFile.raw as File) || null
}

function onImportFileRemove() {
  importFile.value = null
  importResult.value = null
}

async function downloadTemplate() {
  try {
    const blob = await fetchImportTemplate()
    saveBlob(blob, '资产导入模板.csv')
  } catch {
    // 拦截器已提示
  }
}

async function submitImport() {
  if (!importFile.value) {
    ElMessage.warning('请先选择 CSV 文件')
    return
  }
  if (!checkCsvImportSize(importFile.value)) return
  importing.value = true
  try {
    importResult.value = await importAssets(importFile.value)
    ElMessage.success('导入完成')
    void load()
  } catch {
    // 拦截器已提示
  } finally {
    importing.value = false
  }
}

/* ---------------- 导出 ---------------- */
const exporting = ref(false)

async function doExport() {
  exporting.value = true
  try {
    const blob = await exportAssets({
      keyword: query.keyword || undefined,
      statusBiz: query.statusBiz || undefined,
      regionCode: query.regionCode || undefined,
      categoryCode: query.categoryCode || undefined,
      parentAssetNo: query.parentAssetNo || undefined,
    })
    const d = new Date()
    const ymd = `${d.getFullYear()}${String(d.getMonth() + 1).padStart(2, '0')}${String(d.getDate()).padStart(2, '0')}`
    saveBlob(blob, `资产台账_${ymd}.csv`)
  } catch {
    // 拦截器已提示
  } finally {
    exporting.value = false
  }
}

/* ---------------- 表格多选 ---------------- */
const selectedRows = ref<AssetItem[]>([])

function onSelectionChange(rows: AssetItem[]) {
  selectedRows.value = rows
}

const selectedNos = computed(() => selectedRows.value.map((r) => r.assetNo))

function requireSelection(): boolean {
  if (!selectedRows.value.length) {
    ElMessage.warning('请先勾选资产')
    return false
  }
  return true
}

/** 批量结果摘要提示（部分成功时列出跳过原因） */
function notifyBatchResult(res: AssetBatchResult | null | void, okVerb: string) {
  if (!res) return
  const okN = res.changed?.length ?? 0
  const skipN = res.skipped?.length ?? 0
  if (skipN && okN) {
    const reasons = (res.skipped || [])
      .slice(0, 5)
      .map((s) => `${s.assetNo}：${s.reason || '跳过'}`)
      .join('；')
    ElMessage.warning(
      `${okVerb} ${okN} 台，跳过 ${skipN} 台${res.skipped.length > 5 ? ' 等' : ''}——${reasons}`,
    )
  } else if (skipN && !okN) {
    const first = res.skipped[0]
    ElMessage.error(`全部跳过（${skipN} 台）：${first?.reason || '原因见明细'}`)
  } else {
    ElMessage.success(`${okVerb} ${okN} 台`)
  }
}

/* ---------------- 批量状态流转 ---------------- */
const batchTransitionVisible = ref(false)
const batchTransitionSaving = ref(false)
const batchTransitionResult = ref<AssetBatchResult | null>(null)
const batchTransitionForm = reactive({
  targetType: 'biz' as 'biz' | 'fin',
  target: '',
  reason: '',
  refDoc: '',
})

const batchTransitionTargetOptions = computed(() => {
  if (!meta.value) return []
  return batchTransitionForm.targetType === 'biz' ? meta.value.bizStates : meta.value.finStates
})

function openBatchTransition() {
  if (!requireSelection()) return
  Object.assign(batchTransitionForm, { targetType: 'biz', target: '', reason: '', refDoc: '' })
  batchTransitionResult.value = null
  batchTransitionVisible.value = true
}

function onBatchTransitionTypeChange() {
  batchTransitionForm.target = ''
}

async function submitBatchTransition() {
  if (!batchTransitionForm.target) {
    ElMessage.warning('请选择目标状态')
    return
  }
  const payload: AssetBatchTransitionPayload = {
    assetNos: selectedNos.value,
    reason: batchTransitionForm.reason || undefined,
    refDoc: batchTransitionForm.refDoc || undefined,
    ...(batchTransitionForm.targetType === 'biz'
      ? { targetBiz: batchTransitionForm.target }
      : { targetFin: batchTransitionForm.target }),
  }
  batchTransitionSaving.value = true
  try {
    batchTransitionResult.value = await batchTransitionAssets(payload)
    notifyBatchResult(batchTransitionResult.value, '流转成功')
    if ((batchTransitionResult.value?.changed?.length ?? 0) > 0) {
      void load()
    }
  } catch {
    // 拦截器已提示
  } finally {
    batchTransitionSaving.value = false
  }
}

/* ---------------- 批量编辑 ---------------- */
const batchEditVisible = ref(false)
const batchEditSaving = ref(false)
const batchEditResult = ref<AssetBatchResult | null>(null)
const batchEditForm = reactive({
  categoryCode: '',
  modelCode: '',
  regionCode: '',
  ownerType: '',
  department: '',
  owner: '',
  contractNo: '',
})

function openBatchEdit() {
  if (!requireSelection()) return
  Object.assign(batchEditForm, {
    categoryCode: '',
    modelCode: '',
    regionCode: '',
    ownerType: '',
    department: '',
    owner: '',
    contractNo: '',
  })
  batchEditResult.value = null
  batchEditVisible.value = true
}

function onBatchEditCategoryChange(code: string) {
  batchEditForm.modelCode = ''
  void loadModelOptions(code)
}

async function submitBatchEdit() {
  // 仅提交非空字段（留空=不修改）
  const payload: AssetBatchUpdatePayload = { assetNos: selectedNos.value }
  if (batchEditForm.categoryCode) payload.categoryCode = batchEditForm.categoryCode
  if (batchEditForm.modelCode) payload.modelCode = batchEditForm.modelCode
  if (batchEditForm.regionCode) payload.regionCode = batchEditForm.regionCode
  if (batchEditForm.ownerType) payload.ownerType = batchEditForm.ownerType
  if (batchEditForm.department) payload.department = batchEditForm.department
  if (batchEditForm.owner) payload.owner = batchEditForm.owner
  if (batchEditForm.contractNo) payload.contractNo = batchEditForm.contractNo
  if (
    !payload.categoryCode &&
    !payload.modelCode &&
    !payload.regionCode &&
    !payload.ownerType &&
    !payload.department &&
    !payload.owner &&
    !payload.contractNo
  ) {
    ElMessage.warning('请至少填写一个要修改的字段（留空不修改）')
    return
  }
  batchEditSaving.value = true
  try {
    batchEditResult.value = await batchUpdateAssets(payload)
    notifyBatchResult(batchEditResult.value, '编辑成功')
    if ((batchEditResult.value?.changed?.length ?? 0) > 0) {
      void load()
    }
  } catch {
    // 拦截器已提示
  } finally {
    batchEditSaving.value = false
  }
}

/* ---------------- 批量删除 ---------------- */
async function batchDelete() {
  if (!requireSelection()) return
  const n = selectedRows.value.length
  try {
    await ElMessageBox.confirm(
      `确定删除勾选的 ${n} 台资产吗？仅「待上架」可删，有子资产的会跳过。该操作不可恢复。`,
      '批量删除确认',
      { type: 'warning', confirmButtonText: '删除', cancelButtonText: '取消' },
    )
  } catch {
    return
  }
  try {
    const res = await batchDeleteAssets(selectedNos.value)
    notifyBatchResult(res, '删除成功')
    void load()
  } catch {
    // 拦截器已提示
  }
}

/* ---------------- 导出选中 ---------------- */
async function exportSelected() {
  if (!requireSelection()) return
  exporting.value = true
  try {
    const blob = await exportAssets({ assetNos: selectedNos.value.join(',') })
    const d = new Date()
    const ymd = `${d.getFullYear()}${String(d.getMonth() + 1).padStart(2, '0')}${String(d.getDate()).padStart(2, '0')}`
    saveBlob(blob, `资产台账_选中_${ymd}.csv`)
  } catch {
    // 拦截器已提示
  } finally {
    exporting.value = false
  }
}

/* ---------------- 二维码 ---------------- */
const qrcodeVisible = ref(false)
const qrcodeAssetNo = ref('')
const qrcodeName = ref('')
const qrCanvasRef = ref<HTMLCanvasElement | null>(null)
const printingQrcode = ref(false)

/** 详情抽屉底部：打开单枚二维码弹窗 */
function openQrcode() {
  const d = detail.value
  if (!d) return
  qrcodeAssetNo.value = d.assetNo
  qrcodeName.value = d.name
  qrcodeVisible.value = true
}

/** 弹窗动画结束后渲染二维码（内容为资产编号纯文本） */
async function renderQrcode() {
  if (!qrCanvasRef.value) return
  try {
    await renderToCanvas(qrCanvasRef.value, qrcodeAssetNo.value)
  } catch {
    ElMessage.error('二维码生成失败')
  }
}

async function downloadQrcodePng() {
  try {
    await downloadQrcode(qrcodeAssetNo.value)
  } catch {
    ElMessage.error('二维码下载失败')
  }
}

async function printCurrentQrcode() {
  try {
    await openPrintPage([{ assetNo: qrcodeAssetNo.value, name: qrcodeName.value }])
  } catch (e) {
    ElMessage.error(e instanceof Error ? e.message : '打开打印窗口失败')
  }
}

/** 批量打印选中资产行的二维码 */
async function printSelectedQrcodes() {
  if (!selectedRows.value.length) {
    ElMessage.warning('请先勾选要打印的资产')
    return
  }
  printingQrcode.value = true
  try {
    await openPrintPage(
      selectedRows.value.map((r) => ({ assetNo: r.assetNo, name: r.name || '' })),
    )
  } catch (e) {
    ElMessage.error(e instanceof Error ? e.message : '打开打印窗口失败')
  } finally {
    printingQrcode.value = false
  }
}

const route = useRoute()

onMounted(() => {
  // 全局搜索深链：?keyword=xxx 直接带入筛选
  if (typeof route.query.keyword === 'string') query.keyword = route.query.keyword
  void load()
  void loadMeta()
  void loadCategories()
})
</script>

<template>
  <div class="page-card">
    <div class="page-title">资产台账</div>

    <!-- 顶部筛选 -->
    <div class="page-toolbar">
      <el-input
        v-model="query.keyword"
        placeholder="资产编号 / SN / 名称"
        clearable
        style="width: 220px"
        @keyup.enter="search"
        @clear="search"
      />
      <el-select
        v-model="query.statusBiz"
        placeholder="业务状态"
        clearable
        filterable
        style="width: 140px"
        @change="search"
      >
        <el-option v-for="s in meta?.bizStates || []" :key="s" :label="s" :value="s" />
      </el-select>
      <el-select
        v-model="query.regionCode"
        placeholder="区域"
        clearable
        style="width: 140px"
        @change="search"
      >
        <el-option v-for="r in meta?.regions || []" :key="r.key" :label="r.label" :value="r.key" />
      </el-select>
      <el-select
        v-model="query.categoryCode"
        placeholder="资产分类"
        clearable
        filterable
        style="width: 180px"
        @change="search"
      >
        <el-option v-for="c in categoryFlat" :key="c.categoryCode" :label="c.label" :value="c.categoryCode" />
      </el-select>
      <el-select
        v-model="query.parentAssetNo"
        placeholder="父资产"
        clearable
        filterable
        remote
        :remote-method="onParentFilterSearch"
        style="width: 170px"
        @change="search"
        @focus="onParentFilterFocus"
      >
        <el-option label="仅根资产（无父）" value="ROOT" />
        <el-option label="仅子资产（部件）" value="CHILD" />
        <el-option
          v-for="p in parentFilterOptions"
          :key="p.assetNo"
          :label="`${p.assetNo} ${p.name}`"
          :value="p.assetNo"
        />
      </el-select>
      <el-button type="primary" plain @click="search">查询</el-button>
      <el-button @click="resetQuery">重置</el-button>
      <el-radio-group
        :model-value="viewMode"
        size="small"
        style="margin-left: 4px"
        @update:model-value="onViewModeChange"
      >
        <el-radio-button value="flat">平铺</el-radio-button>
        <el-radio-button value="tree">整机树</el-radio-button>
      </el-radio-group>
      <div style="flex: 1"></div>
      <template v-if="selectedRows.length">
        <el-button
          v-if="auth.canWrite('assets')"
          type="warning"
          plain
          @click="openBatchTransition"
        >
          批量流转（{{ selectedRows.length }}）
        </el-button>
        <el-button v-if="auth.canWrite('assets')" plain @click="openBatchEdit">
          批量编辑
        </el-button>
        <el-button
          v-if="auth.canDelete('assets')"
          type="danger"
          plain
          @click="batchDelete"
        >
          批量删除
        </el-button>
        <el-button :loading="exporting" plain @click="exportSelected">导出选中</el-button>
      </template>
      <el-button type="primary" @click="openCreate">新增资产</el-button>
      <el-button v-if="auth.canWrite('assets') && !auth.fieldRestricted('assets')" @click="openImport">导入</el-button>
      <el-button :loading="exporting" @click="doExport">导出</el-button>
      <el-button
        type="success"
        plain
        :disabled="!selectedRows.length"
        :loading="printingQrcode"
        @click="printSelectedQrcodes"
      >
        打印二维码
      </el-button>
    </div>

    <!-- 列表（平铺） -->
    <el-table
      v-if="viewMode === 'flat'"
      v-loading="listLoading"
      :data="records"
      border
      stripe
      row-key="assetNo"
      @selection-change="onSelectionChange"
    >
      <el-table-column type="selection" width="42" fixed="left" />
      <el-table-column prop="assetNo" label="资产编号" min-width="130" fixed="left" />
      <el-table-column v-if="fv('parentAssetNo')" label="父资产" min-width="130" show-overflow-tooltip>
        <template #default="{ row }">
          <template v-if="row.parentAssetNo">
            <el-button link type="primary" @click="openParentFromChild(row)">{{ row.parentAssetNo }}</el-button>
          </template>
          <span v-else class="root-badge">整机</span>
        </template>
      </el-table-column>
      <el-table-column v-if="fv('sn')" prop="sn" label="SN" min-width="120" show-overflow-tooltip />
      <el-table-column prop="name" label="名称" min-width="140" show-overflow-tooltip />
      <el-table-column v-if="fv('categoryCode')" label="分类" min-width="110" show-overflow-tooltip>
        <template #default="{ row }">
          {{ categoryNameMap.get(row.categoryCode) || row.categoryCode || '-' }}
        </template>
      </el-table-column>
      <el-table-column v-if="fv('statusBiz')" label="业务状态" width="100">
        <template #default="{ row }">
          <el-tag :type="stateTagType(row.statusBiz)" size="small">{{ row.statusBiz || '-' }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column v-if="fv('statusFin')" label="财务状态" width="100">
        <template #default="{ row }">
          <el-tag :type="stateTagType(row.statusFin)" size="small">{{ row.statusFin || '-' }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column v-if="fv('regionCode')" label="区域" width="90">
        <template #default="{ row }">{{ regionLabel(row.regionCode) }}</template>
      </el-table-column>
      <el-table-column v-if="fv('rackCode')" prop="rackCode" label="机柜" min-width="100" show-overflow-tooltip>
        <template #default="{ row }">{{ row.rackCode || '-' }}</template>
      </el-table-column>
      <el-table-column v-if="fv('unitNo')" label="U位" width="70">
        <template #default="{ row }">{{ row.unitNo || '-' }}</template>
      </el-table-column>
      <el-table-column v-if="fv('department')" prop="department" label="使用部门" min-width="110" show-overflow-tooltip>
        <template #default="{ row }">{{ row.department || '-' }}</template>
      </el-table-column>
      <el-table-column v-if="fv('owner')" prop="owner" label="责任人" min-width="90">
        <template #default="{ row }">{{ row.owner || '-' }}</template>
      </el-table-column>
      <el-table-column v-if="fv('purchaseDate')" prop="purchaseDate" label="采购日期" width="105">
        <template #default="{ row }">{{ row.purchaseDate || '-' }}</template>
      </el-table-column>
      <el-table-column label="操作" width="240" fixed="right">
        <template #default="{ row }">
          <el-button link type="primary" @click="openDetail(row)">详情</el-button>
          <el-button link type="primary" @click="openEdit(row)">编辑</el-button>
          <el-button link type="warning" @click="openTransition(row)">流转</el-button>
          <el-button link type="info" @click="filterChildren(row)">子资产</el-button>
          <el-button v-if="auth.canDelete('assets')" link type="danger" @click="remove(row)">删除</el-button>
        </template>
      </el-table-column>
    </el-table>

    <!-- 整机树（父-子部件） -->
    <el-table
      v-else
      v-loading="treeLoading"
      :data="treeData"
      border
      stripe
      row-key="assetNo"
      default-expand-all
      :tree-props="{ children: 'children' }"
    >
      <el-table-column prop="assetNo" label="资产编号" min-width="150" fixed="left" />
      <el-table-column prop="name" label="名称" min-width="160" show-overflow-tooltip />
      <el-table-column label="层级" width="70">
        <template #default="{ row }">
          <el-tag v-if="!row.parentAssetNo" size="small">整机</el-tag>
          <el-tag v-else type="info" size="small">部件</el-tag>
        </template>
      </el-table-column>
      <el-table-column v-if="fv('categoryCode')" label="分类" min-width="110" show-overflow-tooltip>
        <template #default="{ row }">
          {{ categoryNameMap.get(row.categoryCode) || row.categoryCode || '-' }}
        </template>
      </el-table-column>
      <el-table-column v-if="fv('statusBiz')" label="业务状态" width="100">
        <template #default="{ row }">
          <el-tag :type="stateTagType(row.statusBiz)" size="small">{{ row.statusBiz || '-' }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column v-if="fv('statusFin')" label="财务状态" width="100">
        <template #default="{ row }">
          <el-tag :type="stateTagType(row.statusFin)" size="small">{{ row.statusFin || '-' }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column v-if="fv('rackCode')" label="机柜 / U" min-width="100">
        <template #default="{ row }">{{ row.rackCode || '-' }}{{ row.unitNo ? ' / U' + row.unitNo : '' }}</template>
      </el-table-column>
      <el-table-column v-if="fv('owner')" prop="owner" label="责任人" min-width="90">
        <template #default="{ row }">{{ row.owner || '-' }}</template>
      </el-table-column>
      <el-table-column label="操作" width="200" fixed="right">
        <template #default="{ row }">
          <el-button link type="primary" @click="openDetail(row)">详情</el-button>
          <el-button link type="primary" @click="openEdit(row)">编辑</el-button>
          <el-button link type="warning" @click="openTransition(row)">流转</el-button>
          <el-button v-if="auth.canDelete('assets')" link type="danger" @click="remove(row)">删除</el-button>
        </template>
      </el-table-column>
    </el-table>

    <div v-if="viewMode === 'flat'" class="page-pagination">
      <el-pagination
        v-model:current-page="query.page"
        v-model:page-size="query.size"
        :total="total"
        :page-sizes="[10, 20, 50, 100]"
        layout="total, sizes, prev, pager, next, jumper"
        @current-change="load"
        @size-change="() => { query.page = 1; load() }"
      />
    </div>

    <!-- 新增 / 编辑资产：快速登记（只录基础）/ 完整信息（四段全展开） -->
    <el-dialog
      v-model="dialogVisible"
      :title="editingAssetNo ? '编辑资产' : '新增资产'"
      width="820px"
      destroy-on-close
    >
      <div v-if="!editingAssetNo" class="form-mode-bar">
        <el-radio-group v-model="formMode" size="small">
          <el-radio-button value="quick">快速登记</el-radio-button>
          <el-radio-button value="full">完整信息</el-radio-button>
        </el-radio-group>
        <el-checkbox v-model="keepCreating">创建后继续录下一条</el-checkbox>
      </div>

      <el-form ref="formRef" :model="form" :rules="rules" label-width="92px">
        <el-collapse v-model="activeSections" class="form-collapse">
          <!-- ① 基础信息（快速登记默认只展开本段） -->
          <el-collapse-item name="basic" title="基础信息">
            <el-row :gutter="12">
              <el-col :span="12">
                <el-form-item label="名称" prop="name">
                  <el-input ref="nameInputRef" v-model="form.name" placeholder="必填" />
                </el-form-item>
              </el-col>
              <el-col v-if="fv('sn')" :span="12">
                <el-form-item label="SN">
                  <el-input :disabled="!fe('sn')" v-model="form.sn" />
                </el-form-item>
              </el-col>
              <el-col v-if="fv('categoryCode')" :span="12">
                <el-form-item label="分类">
                  <el-tree-select
                    :disabled="!fe('categoryCode')" v-model="form.categoryCode"
                    :data="categoryTree"
                    :props="{ label: 'name', children: 'children' }"
                    node-key="categoryCode"
                    check-strictly
                    clearable
                    filterable
                    default-expand-all
                    placeholder="选择分类"
                    style="width: 100%"
                    @change="onFormCategoryChange"
                  />
                </el-form-item>
              </el-col>
              <el-col v-if="fv('modelCode')" :span="12">
                <el-form-item label="型号">
                  <el-select
                    :disabled="!fe('modelCode')" v-model="form.modelCode"
                    clearable
                    filterable
                    allow-create
                    default-first-option
                    :loading="modelLoading"
                    placeholder="选择已有型号或直接输入，可留空"
                    style="width: 100%"
                  >
                    <el-option v-for="m in modelOptions" :key="m" :label="m" :value="m" />
                  </el-select>
                </el-form-item>
              </el-col>
              <el-col v-if="fv('regionCode')" :span="12">
                <el-form-item label="区域">
                  <el-select :disabled="!fe('regionCode')" v-model="form.regionCode" clearable placeholder="选择区域" style="width: 100%">
                    <el-option v-for="r in meta?.regions || []" :key="r.key" :label="r.label" :value="r.key" />
                  </el-select>
                </el-form-item>
              </el-col>
            </el-row>
            <div v-if="formMode === 'quick'" class="form-hint">
              位置与采购信息可稍后补录，先录这几项即可建档。
              <el-button link type="primary" size="small" @click="formMode = 'full'">填写完整信息</el-button>
            </div>
          </el-collapse-item>

          <!-- ② 位置与上架：区域→机房→机柜→U位 级联 -->
          <el-collapse-item name="position" title="位置与上架">
            <el-row :gutter="12">
              <el-col v-if="fv('roomCode')" :span="12">
                <el-form-item label="机房">
                  <el-select
                    :disabled="!fe('roomCode')" v-model="form.roomCode"
                    clearable
                    filterable
                    allow-create
                    default-first-option
                    remote
                    :remote-method="onRoomSearch"
                    :loading="roomOptionsLoading"
                    placeholder="选择机房，或直接输入编码"
                    style="width: 100%"
                    @focus="onRoomFocus"
                    @change="onFormRoomChange"
                  >
                    <el-option
                      v-for="r in roomOptions"
                      :key="r.roomCode"
                      :label="`${r.roomCode}${r.name ? ' ' + r.name : ''}`"
                      :value="r.roomCode"
                    />
                  </el-select>
                </el-form-item>
              </el-col>
              <el-col v-if="fv('rackCode')" :span="12">
                <el-form-item label="机柜">
                  <el-select
                    :disabled="!fe('rackCode')" v-model="form.rackCode"
                    clearable
                    filterable
                    remote
                    :remote-method="onRackSearch"
                    :loading="rackOptionsLoading"
                    :placeholder="form.roomCode ? '输入编码搜索该机房机柜' : '输入编码搜索机柜'"
                    style="width: 100%"
                    @focus="onRackFocus"
                    @change="onFormRackChange"
                  >
                    <el-option
                      v-for="r in rackOptions"
                      :key="r.rackCode"
                      :label="r.rackCode"
                      :value="r.rackCode"
                    />
                  </el-select>
                  <div class="form-hint">选中机柜会自动回填所属机房</div>
                </el-form-item>
              </el-col>
              <el-col v-if="fv('uHeight')" :span="8">
                <el-form-item label="U高">
                  <el-input-number
                    :disabled="!fe('uHeight')" v-model="form.uHeight"
                    :min="1"
                    :max="48"
                    controls-position="right"
                    style="width: 100%"
                  />
                </el-form-item>
              </el-col>
              <el-col v-if="fv('unitNo')" :span="8">
                <el-form-item label="U位">
                  <el-select
                    v-model="form.unitNo"
                    clearable
                    :disabled="(!form.rackCode) || !fe('unitNo')"
                    :loading="unitOptionsLoading"
                    :placeholder="form.rackCode ? '选择起始U位' : '先选择机柜'"
                    style="width: 100%"
                  >
                    <el-option v-for="u in unitOptions" :key="u" :label="`U${u}`" :value="u" />
                  </el-select>
                  <div class="form-hint">{{ unitHint }}</div>
                </el-form-item>
              </el-col>
              <el-col v-if="fv('rackDate')" :span="16">
                <el-form-item label="上架日期">
                  <el-date-picker
                    :disabled="!fe('rackDate')" v-model="form.rackDate"
                    type="date"
                    value-format="YYYY-MM-DD"
                    placeholder="选择日期"
                    style="width: 100%"
                  />
                </el-form-item>
              </el-col>
            </el-row>
          </el-collapse-item>

          <!-- ③ 采购与责任 -->
          <el-collapse-item name="purchase" title="采购与责任">
            <el-row :gutter="12">
              <el-col v-if="fv('purchaseDate')" :span="12">
                <el-form-item label="采购日期">
                  <el-date-picker
                    :disabled="!fe('purchaseDate')" v-model="form.purchaseDate"
                    type="date"
                    value-format="YYYY-MM-DD"
                    placeholder="选择日期"
                    style="width: 100%"
                  />
                </el-form-item>
              </el-col>
              <el-col v-if="fv('purchasePrice')" :span="12">
                <el-form-item label="采购单价">
                  <el-input-number
                    :disabled="!fe('purchasePrice')" v-model="form.purchasePrice"
                    :min="0"
                    :precision="2"
                    controls-position="right"
                    style="width: 100%"
                  />
                </el-form-item>
              </el-col>
              <el-col v-if="fv('warrantyEnd')" :span="12">
                <el-form-item label="保修到期">
                  <el-date-picker
                    :disabled="!fe('warrantyEnd')" v-model="form.warrantyEnd"
                    type="date"
                    value-format="YYYY-MM-DD"
                    placeholder="选择日期"
                    style="width: 100%"
                  />
                </el-form-item>
              </el-col>
              <el-col v-if="fv('contractNo')" :span="12">
                <el-form-item label="合同号">
                  <el-input :disabled="!fe('contractNo')" v-model="form.contractNo" />
                </el-form-item>
              </el-col>
              <el-col v-if="fv('ratedPowerW')" :span="12">
                <el-form-item label="额定功率">
                  <el-input-number
                    :disabled="!fe('ratedPowerW')" v-model="form.ratedPowerW"
                    :min="0"
                    :max="200000"
                    :controls="false"
                    placeholder="W，留空按型号/分类模板"
                    style="width: 100%"
                  />
                </el-form-item>
              </el-col>
              <el-col v-if="fv('department')" :span="12">
                <el-form-item label="使用部门">
                  <el-input :disabled="!fe('department')" v-model="form.department" />
                </el-form-item>
              </el-col>
              <el-col v-if="fv('owner')" :span="12">
                <el-form-item label="责任人">
                  <el-input :disabled="!fe('owner')" v-model="form.owner" />
                </el-form-item>
              </el-col>
            </el-row>
          </el-collapse-item>

          <!-- ④ 扩展信息 -->
          <el-collapse-item name="extra" title="扩展信息">
            <el-row :gutter="12">
              <el-col v-if="fv('ownerType')" :span="12">
                <el-form-item label="权属">
                  <el-select :disabled="!fe('ownerType')" v-model="form.ownerType" clearable placeholder="自持 / 租赁" style="width: 100%">
                    <el-option
                      v-for="t in meta?.ownerTypes || []"
                      :key="t"
                      :label="ownerTypeLabel(t)"
                      :value="t"
                    />
                  </el-select>
                </el-form-item>
              </el-col>
              <el-col v-if="fv('mgmtIp')" :span="12">
                <el-form-item label="管理IP">
                  <el-input :disabled="!fe('mgmtIp')" v-model="form.mgmtIp" />
                </el-form-item>
              </el-col>
              <el-col v-if="fv('bizIp')" :span="12">
                <el-form-item label="业务IP">
                  <el-input :disabled="!fe('bizIp')" v-model="form.bizIp" />
                </el-form-item>
              </el-col>
              <el-col v-if="fv('parentAssetNo')" :span="12">
                <el-form-item label="父资产">
                  <el-select
                    :disabled="!fe('parentAssetNo')" v-model="form.parentAssetNo"
                    clearable
                    filterable
                    remote
                    :remote-method="onParentSearch"
                    :loading="parentOptionsLoading"
                    placeholder="输入编号/名称搜索父资产（整机）"
                    style="width: 100%"
                    @focus="onParentFocus"
                  >
                    <el-option
                      v-for="p in parentOptions"
                      :key="p.assetNo"
                      :label="`${p.assetNo} ${p.name}`"
                      :value="p.assetNo"
                    />
                  </el-select>
                  <div class="form-hint">部件（GPU卡/内存/硬盘等）挂到整机下；清空=根资产</div>
                </el-form-item>
              </el-col>
              <el-col v-if="fv('attrs')" :span="24">
                <el-form-item label="备注">
                  <el-input :disabled="!fe('attrs')" v-model="form.attrsRemark" type="textarea" :rows="2" placeholder="存储于 attrs.remark" />
                </el-form-item>
              </el-col>
            </el-row>
          </el-collapse-item>
        </el-collapse>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="save">保存</el-button>
      </template>
    </el-dialog>

    <!-- 详情抽屉 -->
    <el-drawer
      v-model="drawerVisible"
      :title="detail ? `${detail.name}（${detail.assetNo}）` : '资产详情'"
      size="620px"
      destroy-on-close
    >
      <div v-loading="detailLoading">
        <template v-if="detail">
          <div class="drawer-subtitle">基本信息</div>
          <el-descriptions :column="2" border size="small">
            <el-descriptions-item label="资产编号">{{ detail.assetNo }}</el-descriptions-item>
            <el-descriptions-item label="SN">{{ detail.sn || '-' }}</el-descriptions-item>
            <el-descriptions-item label="名称">{{ detail.name }}</el-descriptions-item>
            <el-descriptions-item label="分类">
              {{ categoryNameMap.get(detail.categoryCode || '') || detail.categoryCode || '-' }}
            </el-descriptions-item>
            <el-descriptions-item label="型号">{{ detail.modelCode || '-' }}</el-descriptions-item>
            <el-descriptions-item label="权属">{{ ownerTypeLabel(detail.ownerType) }}</el-descriptions-item>
            <el-descriptions-item label="业务状态">
              <el-tag :type="stateTagType(detail.statusBiz)" size="small">{{ detail.statusBiz || '-' }}</el-tag>
            </el-descriptions-item>
            <el-descriptions-item label="财务状态">
              <el-tag :type="stateTagType(detail.statusFin)" size="small">{{ detail.statusFin || '-' }}</el-tag>
            </el-descriptions-item>
            <el-descriptions-item label="区域">{{ regionLabel(detail.regionCode) }}</el-descriptions-item>
            <el-descriptions-item label="机房">{{ detail.roomCode || '-' }}</el-descriptions-item>
            <el-descriptions-item label="机柜">{{ detail.rackCode || '-' }}</el-descriptions-item>
            <el-descriptions-item label="U位 / U高">
              {{ detail.unitNo || '-' }} / {{ detail.uHeight ?? '-' }}
            </el-descriptions-item>
            <el-descriptions-item label="使用部门">{{ detail.department || '-' }}</el-descriptions-item>
            <el-descriptions-item label="责任人">{{ detail.owner || '-' }}</el-descriptions-item>
            <el-descriptions-item label="管理IP">{{ detail.mgmtIp || '-' }}</el-descriptions-item>
            <el-descriptions-item label="业务IP">{{ detail.bizIp || '-' }}</el-descriptions-item>
            <el-descriptions-item label="采购日期">{{ detail.purchaseDate || '-' }}</el-descriptions-item>
            <el-descriptions-item label="采购单价">{{ fmtPrice(detail.purchasePrice) }}</el-descriptions-item>
            <el-descriptions-item label="保修到期">{{ detail.warrantyEnd || '-' }}</el-descriptions-item>
            <el-descriptions-item label="合同号">{{ detail.contractNo || '-' }}</el-descriptions-item>
            <el-descriptions-item label="额定功率">{{ detail.ratedPowerW ? detail.ratedPowerW + ' W' : '-' }}</el-descriptions-item>
            <el-descriptions-item label="父资产">
              <template v-if="detail.parent">
                <el-button link type="primary" @click="openParentFromChild(detail)">
                  {{ detail.parent.assetNo }} {{ detail.parent.name }}
                </el-button>
              </template>
              <span v-else-if="detail.parentAssetNo">{{ detail.parentAssetNo }}</span>
              <span v-else class="root-badge">整机（无父）</span>
            </el-descriptions-item>
            <el-descriptions-item label="子资产数">{{ detail.childCount ?? (detail.children?.length || 0) }}</el-descriptions-item>
            <el-descriptions-item label="备注">
              {{ detail.attrs && typeof detail.attrs.remark === 'string' ? (detail.attrs.remark as string) : '-' }}
            </el-descriptions-item>
            <el-descriptions-item label="版本">{{ detail.version ?? '-' }}</el-descriptions-item>
            <el-descriptions-item label="创建时间">{{ detail.createdAt || '-' }}</el-descriptions-item>
            <el-descriptions-item label="更新时间">{{ detail.updatedAt || '-' }}</el-descriptions-item>
          </el-descriptions>

          <div class="drawer-subtitle">财务信息</div>
          <el-descriptions v-if="detail.finance" :column="2" border size="small">
            <el-descriptions-item label="资产原值">{{ fmtPrice(detail.finance.originValue) }}</el-descriptions-item>
            <el-descriptions-item label="残值率">{{ detail.finance.residualRate ?? '-' }}</el-descriptions-item>
            <el-descriptions-item label="折旧期(月)">{{ detail.finance.lifeMonths ?? '-' }}</el-descriptions-item>
            <el-descriptions-item label="折旧方法">{{ detail.finance.depMethod || '-' }}</el-descriptions-item>
            <el-descriptions-item label="折旧起始">{{ detail.finance.depStartDate || '-' }}</el-descriptions-item>
            <el-descriptions-item label="累计折旧">{{ fmtPrice(detail.finance.accumulatedDep) }}</el-descriptions-item>
            <el-descriptions-item label="净值">{{ fmtPrice(detail.finance.netValue) }}</el-descriptions-item>
            <el-descriptions-item label="减值准备">{{ fmtPrice(detail.finance.impairment) }}</el-descriptions-item>
            <el-descriptions-item label="租赁标志">
              {{ detail.finance.leaseFlag ? '是' : '否' }}
            </el-descriptions-item>
          </el-descriptions>
          <el-empty v-else description="暂无财务信息" :image-size="60" />

          <div class="drawer-subtitle">
            子资产 / 部件
            <span class="drawer-count" v-if="(detail.childCount ?? 0) > 0">（{{ detail.childCount }}）</span>
          </div>
          <div v-if="detail.children?.length">
            <el-table :data="detail.children" size="small" border max-height="260">
              <el-table-column prop="assetNo" label="编号" min-width="120" show-overflow-tooltip />
              <el-table-column prop="name" label="名称" min-width="120" show-overflow-tooltip />
              <el-table-column label="分类" min-width="90" show-overflow-tooltip>
                <template #default="{ row }">
                  {{ categoryNameMap.get(row.categoryCode || '') || row.categoryCode || '-' }}
                </template>
              </el-table-column>
              <el-table-column label="状态" width="90">
                <template #default="{ row }">
                  <el-tag :type="stateTagType(row.statusBiz)" size="small">{{ row.statusBiz || '-' }}</el-tag>
                </template>
              </el-table-column>
              <el-table-column label="操作" width="70">
                <template #default="{ row }">
                  <el-button link type="primary" @click="openDetail(row)">查看</el-button>
                </template>
              </el-table-column>
            </el-table>
          </div>
          <el-empty v-else description="暂无子资产" :image-size="48" />

          <div class="drawer-subtitle">生命周期事件</div>
          <div v-loading="eventsLoading">
            <el-timeline v-if="events.length" style="padding-left: 4px">
              <el-timeline-item
                v-for="ev in events"
                :key="String(ev.id)"
                :timestamp="ev.eventTime || ''"
                placement="top"
                type="primary"
              >
                <div class="event-title">{{ assetEventLabel(ev.eventType) }}</div>
                <div class="event-meta">
                  操作人：{{ ev.operator || '-' }}
                  <template v-if="ev.refDoc">｜单据号：{{ ev.refDoc }}</template>
                </div>
              </el-timeline-item>
            </el-timeline>
            <el-empty v-else description="暂无事件" :image-size="60" />
            <div v-if="eventTotal > eventQuery.size" class="event-pagination">
              <el-pagination
                v-model:current-page="eventQuery.page"
                :page-size="eventQuery.size"
                :total="eventTotal"
                size="small"
                layout="total, prev, pager, next"
                @current-change="loadEvents"
              />
            </div>
          </div>
        </template>
      </div>
      <template #footer>
        <div class="drawer-footer">
          <el-button type="primary" plain @click="openQrcode">二维码</el-button>
        </div>
      </template>
    </el-drawer>

    <!-- 状态流转 -->
    <el-dialog v-model="transitionVisible" title="状态流转" width="480px" destroy-on-close>
      <el-form label-width="92px">
        <el-form-item label="流转类型">
          <el-radio-group v-model="transitionForm.targetType" @change="onTransitionTypeChange">
            <el-radio value="biz">业务状态</el-radio>
            <el-radio value="fin">财务状态</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="目标状态" required>
          <el-select v-model="transitionForm.target" filterable placeholder="选择目标状态" style="width: 100%">
            <el-option v-for="s in transitionTargetOptions" :key="s" :label="s" :value="s" />
          </el-select>
        </el-form-item>
        <el-form-item label="原因">
          <el-input v-model="transitionForm.reason" type="textarea" :rows="2" placeholder="流转原因（选填）" />
        </el-form-item>
        <el-form-item label="单据号">
          <el-input v-model="transitionForm.refDoc" placeholder="关联单据号（选填）" />
        </el-form-item>
        <el-form-item>
          <el-checkbox v-model="transitionForm.cascade">
            状态联动到全部子资产（状态机允许才改，不允许则跳过）
          </el-checkbox>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="transitionVisible = false">取消</el-button>
        <el-button type="primary" :loading="transitionSaving" @click="submitTransition">提交流转</el-button>
      </template>
    </el-dialog>

    <!-- 批量状态流转（部分成功：状态机不允许的逐台跳过） -->
    <el-dialog
      v-model="batchTransitionVisible"
      title="批量状态流转"
      width="560px"
      destroy-on-close
    >
      <el-alert
        type="info"
        :closable="false"
        show-icon
        title="将对勾选资产逐台按状态机流转；不合法或已到目标态的自动跳过并汇总。"
        style="margin-bottom: 12px"
      />
      <el-form label-width="92px">
        <el-form-item label="流转类型">
          <el-radio-group v-model="batchTransitionForm.targetType" @change="onBatchTransitionTypeChange">
            <el-radio value="biz">业务状态</el-radio>
            <el-radio value="fin">财务状态</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="目标状态" required>
          <el-select
            v-model="batchTransitionForm.target"
            filterable
            placeholder="选择目标状态"
            style="width: 100%"
          >
            <el-option
              v-for="s in batchTransitionTargetOptions"
              :key="s"
              :label="s"
              :value="s"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="原因">
          <el-input
            v-model="batchTransitionForm.reason"
            type="textarea"
            :rows="2"
            placeholder="流转原因（选填）"
          />
        </el-form-item>
        <el-form-item label="单据号">
          <el-input v-model="batchTransitionForm.refDoc" placeholder="关联单据号（选填）" />
        </el-form-item>
      </el-form>
      <template v-if="batchTransitionResult">
        <el-alert
          :type="(batchTransitionResult.skipped?.length ?? 0) > 0 ? 'warning' : 'success'"
          :closable="false"
          :title="`共 ${batchTransitionResult.total} 台：成功 ${batchTransitionResult.changed?.length ?? 0}，跳过 ${batchTransitionResult.skipped?.length ?? 0}`"
          style="margin-top: 8px"
        />
        <el-table
          v-if="batchTransitionResult.skipped?.length"
          :data="batchTransitionResult.skipped"
          border
          size="small"
          max-height="200"
          style="margin-top: 8px"
        >
          <el-table-column prop="assetNo" label="资产编号" width="150" />
          <el-table-column prop="name" label="名称" min-width="100" show-overflow-tooltip />
          <el-table-column prop="reason" label="跳过原因" min-width="200" show-overflow-tooltip />
        </el-table>
      </template>
      <template #footer>
        <el-button @click="batchTransitionVisible = false">关闭</el-button>
        <el-button type="primary" :loading="batchTransitionSaving" @click="submitBatchTransition">
          提交流转
        </el-button>
      </template>
    </el-dialog>

    <!-- 批量编辑（仅更新填写的字段） -->
    <el-dialog v-model="batchEditVisible" title="批量编辑资产" width="560px" destroy-on-close>
      <el-alert
        type="info"
        :closable="false"
        show-icon
        title="仅更新下方已填写的字段，留空字段不修改；不支持改状态与 SN。"
        style="margin-bottom: 12px"
      />
      <el-form label-width="92px">
        <el-form-item v-if="fe('categoryCode')" label="分类">
          <el-select
            v-model="batchEditForm.categoryCode"
            clearable
            filterable
            placeholder="不修改请留空"
            style="width: 100%"
            @change="onBatchEditCategoryChange"
          >
            <el-option v-for="c in categoryFlat" :key="c.categoryCode" :label="c.label" :value="c.categoryCode" />
          </el-select>
        </el-form-item>
        <el-form-item v-if="fe('modelCode')" label="型号">
          <el-select
            v-model="batchEditForm.modelCode"
            clearable
            filterable
            allow-create
            default-first-option
            placeholder="不修改请留空"
            style="width: 100%"
          >
            <el-option v-for="m in modelOptions" :key="m" :label="m" :value="m" />
          </el-select>
        </el-form-item>
        <el-form-item v-if="fe('regionCode')" label="区域">
          <el-select
            v-model="batchEditForm.regionCode"
            clearable
            filterable
            placeholder="不修改请留空"
            style="width: 100%"
          >
            <el-option
              v-for="r in meta?.regions || []"
              :key="r.key"
              :label="r.label"
              :value="r.key"
            />
          </el-select>
        </el-form-item>
        <el-form-item v-if="fe('ownerType')" label="权属">
          <el-select v-model="batchEditForm.ownerType" clearable placeholder="不修改请留空" style="width: 100%">
            <el-option label="自持 SELF" value="SELF" />
            <el-option label="租赁 LEASE" value="LEASE" />
          </el-select>
        </el-form-item>
        <el-form-item v-if="fe('department')" label="使用部门">
          <el-input v-model="batchEditForm.department" clearable placeholder="不修改请留空" />
        </el-form-item>
        <el-form-item v-if="fe('owner')" label="责任人">
          <el-input v-model="batchEditForm.owner" clearable placeholder="不修改请留空" />
        </el-form-item>
        <el-form-item v-if="fe('contractNo')" label="合同号">
          <el-input v-model="batchEditForm.contractNo" clearable placeholder="不修改请留空" />
        </el-form-item>
      </el-form>
      <template v-if="batchEditResult">
        <el-alert
          :type="(batchEditResult.skipped?.length ?? 0) > 0 ? 'warning' : 'success'"
          :closable="false"
          :title="`共 ${batchEditResult.total} 台：成功 ${batchEditResult.changed?.length ?? 0}，跳过 ${batchEditResult.skipped?.length ?? 0}`"
          style="margin-top: 8px"
        />
        <el-table
          v-if="batchEditResult.skipped?.length"
          :data="batchEditResult.skipped"
          border
          size="small"
          max-height="200"
          style="margin-top: 8px"
        >
          <el-table-column prop="assetNo" label="资产编号" width="150" />
          <el-table-column prop="name" label="名称" min-width="100" show-overflow-tooltip />
          <el-table-column prop="reason" label="跳过原因" min-width="200" show-overflow-tooltip />
        </el-table>
      </template>
      <template #footer>
        <el-button @click="batchEditVisible = false">关闭</el-button>
        <el-button type="primary" :loading="batchEditSaving" @click="submitBatchEdit">
          提交修改
        </el-button>
      </template>
    </el-dialog>

    <!-- 导入 -->
    <el-dialog v-model="importVisible" title="批量导入资产" width="620px" destroy-on-close>
      <div class="import-actions">
        <el-button @click="downloadTemplate">下载导入模板</el-button>
      </div>
      <el-upload
        drag
        accept=".csv"
        :auto-upload="false"
        :limit="1"
        :on-change="onImportFileChange"
        :on-remove="onImportFileRemove"
      >
        <el-icon style="font-size: 36px; color: #909399"><UploadFilled /></el-icon>
        <div class="el-upload__text">将 CSV 文件拖到此处，或<em>点击选择</em></div>
      </el-upload>
      <div class="import-hint">
        业务/财务状态列仅 admin 账号导入时生效（按数据字典校验，取值无效该行失败）；其他账号忽略，统一写入默认
        待上架/未入账。
      </div>
      <div class="import-actions">
        <el-button type="primary" :loading="importing" :disabled="!importFile" @click="submitImport">
          开始导入
        </el-button>
      </div>
      <template v-if="importResult">
        <el-alert
          :type="importResult.failed > 0 ? 'warning' : 'success'"
          :closable="false"
          :title="`共 ${importResult.total} 条，成功 ${importResult.success} 条，失败 ${importResult.failed} 条`"
          style="margin-top: 8px"
        />
        <el-table v-if="importResult.errors?.length" :data="importResult.errors" border size="small" style="margin-top: 8px">
          <el-table-column prop="row" label="行号" width="80" />
          <el-table-column prop="reason" label="失败原因" min-width="240" show-overflow-tooltip />
        </el-table>
      </template>
    </el-dialog>

    <!-- 二维码 -->
    <el-dialog v-model="qrcodeVisible" title="资产二维码" width="360px" destroy-on-close @opened="renderQrcode">
      <div class="qrcode-body">
        <canvas ref="qrCanvasRef" class="qrcode-canvas"></canvas>
        <div class="qrcode-no">{{ qrcodeAssetNo }}</div>
        <div class="qrcode-name">{{ qrcodeName }}</div>
      </div>
      <template #footer>
        <el-button @click="downloadQrcodePng">下载 PNG</el-button>
        <el-button type="primary" @click="printCurrentQrcode">打印</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.drawer-subtitle {
  font-size: 14px;
  font-weight: 600;
  color: var(--el-text-color-primary);
  margin: 16px 0 10px;
}

.drawer-subtitle:first-child {
  margin-top: 0;
}

.drawer-count {
  font-weight: 400;
  color: var(--el-text-color-secondary);
  font-size: 12px;
}

.root-badge {
  color: #67c23a;
  font-size: 12px;
  font-weight: 600;
}

.form-hint {
  font-size: 12px;
  color: var(--el-text-color-secondary);
  line-height: 1.4;
  margin-top: 2px;
}

/* 登记对话框：模式开关 + 分段折叠 */
.form-mode-bar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 12px;
  padding: 8px 10px;
  background: var(--el-fill-color-light);
  border-radius: 6px;
}

.form-collapse {
  border-top: none;
}

.form-collapse :deep(.el-collapse-item__header) {
  font-size: 13px;
  font-weight: 600;
}

.form-collapse :deep(.el-collapse-item__content) {
  padding-bottom: 4px;
}

.event-title {
  font-weight: 600;
  color: var(--el-text-color-primary);
}

.event-meta {
  font-size: 12px;
  color: var(--el-text-color-secondary);
  margin-top: 2px;
}

.event-pagination {
  display: flex;
  justify-content: flex-end;
  margin-top: 8px;
}

.import-actions {
  margin: 10px 0;
}

.import-hint {
  margin-top: 8px;
  font-size: 12px;
  color: var(--el-text-color-secondary);
  line-height: 1.6;
}

.drawer-footer {
  display: flex;
  justify-content: flex-end;
}

.qrcode-body {
  display: flex;
  flex-direction: column;
  align-items: center;
  padding: 4px 0;
}

.qrcode-canvas {
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 4px;
}

.qrcode-no {
  font-size: 20px;
  font-weight: 700;
  color: var(--el-text-color-primary);
  margin-top: 12px;
  letter-spacing: 0.5px;
}

.qrcode-name {
  font-size: 14px;
  color: var(--el-text-color-regular);
  margin-top: 4px;
  max-width: 300px;
  text-align: center;
}
</style>
