<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import type { TableInstance } from 'element-plus'
import type { FormInstance, FormRules } from 'element-plus'
import { useAuthStore } from '@/stores/auth'
import type { PageResult } from '@/api/types'
import { fetchAssets } from '@/api/asset'
import type { AssetItem } from '@/api/asset'
import {
  batchCapitalizeFinanceAssets,
  batchDisposeFinanceAssets,
  batchImpairFinanceAssets,
  batchRegisterFinanceCards,
  batchWriteOffFinanceAssets,
  capitalizeFinanceAsset,
  createFinanceCard,
  disposeFinanceAsset,
  fetchFinanceAssets,
  impairFinanceAsset,
  writeOffFinanceAsset,
} from '@/api/finance'
import type { FinanceAssetItem, FinanceBatchResult } from '@/api/finance'

const auth = useAuthStore()
const canWrite = computed(() => !!auth.modules['finance']?.write)

type TagType = 'primary' | 'success' | 'warning' | 'info' | 'danger'

/** 财务状态全集（与后端状态机一致） */
const FIN_STATUSES = [
  '未入账',
  '已入账',
  '转固计提',
  '减值',
  '折旧完毕',
  '处置中',
  '核销',
  '使用权资产确认',
  '留购',
]

/** 折旧方法选项 */
const DEP_METHODS = ['平均年限法', '工作量法', '双倍余额递减法', '年数总和法']

/** 财务状态 -> tag 色系（核销/处置红、减值橙、转固绿、已入账蓝、未入账灰） */
const FIN_TAG_MAP: Record<string, TagType> = {
  未入账: 'info',
  已入账: 'primary',
  转固计提: 'success',
  减值: 'warning',
  折旧完毕: 'info',
  处置中: 'danger',
  核销: 'danger',
  使用权资产确认: 'primary',
  留购: 'success',
}

function finTagType(status?: string): TagType {
  return FIN_TAG_MAP[status || ''] || 'info'
}

function bizTagType(state?: string): TagType {
  const s = state || ''
  if (!s) return 'info'
  if (s.includes('报废') || s.includes('退租')) return 'info'
  if (s.includes('维修') || s.includes('故障') || s.includes('下线')) return 'danger'
  if (s.includes('在用') || s.includes('上线') || s.includes('运行') || s.includes('使用')) return 'success'
  return 'warning'
}

/** 金额格式化（千分位 + 两位小数；空显示 -） */
function fmtMoney(v?: number | null): string {
  if (v === null || v === undefined) return '-'
  return v.toLocaleString('zh-CN', { minimumFractionDigits: 2, maximumFractionDigits: 2 })
}

function fmtRate(v?: number | null): string {
  if (v === null || v === undefined) return '-'
  return `${Number((v * 100).toFixed(2))}%`
}

/** 未建档：资产表左联 asset_finance，金额字段为 null */
function isUnfiled(row: FinanceAssetItem): boolean {
  return row.originValue === null || row.originValue === undefined
}

/* ---------------- 状态机驱动的行操作 ---------------- */
function canCapitalize(row: FinanceAssetItem): boolean {
  return !isUnfiled(row) && row.statusFin === '已入账'
}
function canImpair(row: FinanceAssetItem): boolean {
  return !isUnfiled(row) && row.statusFin === '转固计提'
}
function canDispose(row: FinanceAssetItem): boolean {
  return !isUnfiled(row) && row.statusFin === '折旧完毕'
}
function canWriteOff(row: FinanceAssetItem): boolean {
  return !isUnfiled(row) && row.statusFin === '处置中'
}
function hasAction(row: FinanceAssetItem): boolean {
  return canCapitalize(row) || canImpair(row) || canDispose(row) || canWriteOff(row)
}

/* ---------------- 勾选与批量操作（部分成功语义） ---------------- */
const selectedRows = ref<FinanceAssetItem[]>([])
const batchRunning = ref(false)
const tableRef = ref<TableInstance>()

function onSelectionChange(rows: FinanceAssetItem[]) {
  selectedRows.value = rows
}

function clearSelection() {
  selectedRows.value = []
  tableRef.value?.clearSelection()
}

/** 勾选行内按当前状态可执行各动作的子集（按钮可行动数 = disabled 判定） */
const selUnfiled = computed(() => selectedRows.value.filter(isUnfiled))
const selCapitalize = computed(() => selectedRows.value.filter(canCapitalize))
const selImpair = computed(() => selectedRows.value.filter(canImpair))
const selDispose = computed(() => selectedRows.value.filter(canDispose))
const selWriteOff = computed(() => selectedRows.value.filter(canWriteOff))

/** 后端 skipped 不带 name，用勾选行本地补全 */
function nameOf(assetNo: string): string {
  const row = selectedRows.value.find((r) => r.assetNo === assetNo)
  return row?.assetName || assetNo
}

/** 汇总提示：全成功一句话；有跳过列出前 5 条原因 */
function notifyBatch(action: string, res: FinanceBatchResult) {
  const changed = res.changed.length
  const skipped = res.skipped.length
  if (!skipped) {
    ElMessage.success(`${action}完成：成功 ${changed} 台`)
    return
  }
  const reasons = res.skipped
    .slice(0, 5)
    .map((s) => `${s.assetNo}：${s.reason}`)
    .join('；')
  ElMessage.warning(
    `${action}：成功 ${changed} 台，跳过 ${skipped} 台${skipped > 5 ? `（共 ${skipped} 条，前 5 条）` : ''}——${reasons}`,
  )
}

/** 批量执行骨架：调用 API → 汇总提示 → 清勾选并刷新 */
async function runBatch(action: string, runner: () => Promise<FinanceBatchResult>) {
  batchRunning.value = true
  try {
    const res = await runner()
    notifyBatch(action, res)
    clearSelection()
    await load()
  } catch {
    // 拦截器已提示（整体 400/403）
  } finally {
    batchRunning.value = false
  }
}

/* ---------------- 列表查询 ---------------- */
const query = reactive({
  page: 1,
  size: 20,
  keyword: '',
  statusFin: '',
  lease: '' as '' | 'true' | 'false',
})
const total = ref(0)
const records = ref<FinanceAssetItem[]>([])
const listLoading = ref(false)

async function load() {
  listLoading.value = true
  try {
    const res = await fetchFinanceAssets({
      page: query.page,
      size: query.size,
      keyword: query.keyword || undefined,
      statusFin: query.statusFin || undefined,
      leaseFlag: query.lease === '' ? undefined : query.lease === 'true',
    })
    total.value = res.total ?? 0
    records.value = res.records ?? []
  } catch {
    // 拦截器已提示
  } finally {
    listLoading.value = false
  }
}

function search() {
  query.page = 1
  void load()
}

function resetQuery() {
  query.keyword = ''
  query.statusFin = ''
  query.lease = ''
  search()
}

/* ---------------- 建档 ---------------- */
const cardVisible = ref(false)
const cardSaving = ref(false)
const cardFormRef = ref<FormInstance>()
const cardForm = reactive({
  assetNo: '',
  originValue: undefined as number | undefined,
  residualRate: undefined as number | undefined,
  lifeMonths: undefined as number | undefined,
  depMethod: '平均年限法',
  depStartDate: '',
  leaseFlag: false,
})

const cardRules = computed<FormRules>(() => ({
  assetNo: [{ required: true, message: '请选择资产', trigger: 'change' }],
  originValue: [{ required: true, message: '请输入资产原值', trigger: 'blur' }],
  residualRate: [
    {
      validator: (_rule, value: number | undefined, callback) => {
        if (value !== undefined && (value < 0 || value >= 1)) callback(new Error('残值率需在 [0,1) 之间（如 0.05 = 5%）'))
        else callback()
      },
      trigger: 'blur',
    },
  ],
  lifeMonths: [
    {
      validator: (_rule, value: number | undefined, callback) => {
        if (value !== undefined && (!Number.isInteger(value) || value < 1))
          callback(new Error('折旧期需为不小于 1 的整数月'))
        else callback()
      },
      trigger: 'blur',
    },
  ],
}))

/* 建档资产下拉：远程搜索 */
const assetOptions = ref<AssetItem[]>([])
const assetOptionsLoading = ref(false)
let assetSearchTimer: number | undefined

async function loadAssetOptions(keyword: string) {
  assetOptionsLoading.value = true
  try {
    const res = await fetchAssets({ page: 1, size: 50, keyword: keyword || undefined })
    assetOptions.value = res.records ?? []
  } catch {
    assetOptions.value = []
  } finally {
    assetOptionsLoading.value = false
  }
}

function onAssetSearch(keyword: string) {
  window.clearTimeout(assetSearchTimer)
  assetSearchTimer = window.setTimeout(() => void loadAssetOptions(keyword), 300)
}

function openCard() {
  Object.assign(cardForm, {
    assetNo: '',
    originValue: undefined,
    residualRate: undefined,
    lifeMonths: undefined,
    depMethod: '平均年限法',
    depStartDate: '',
    leaseFlag: false,
  })
  assetOptions.value = []
  cardVisible.value = true
  void loadAssetOptions('')
}

async function submitCard() {
  const valid = await cardFormRef.value?.validate().then(() => true).catch(() => false)
  if (!valid) return
  cardSaving.value = true
  try {
    await createFinanceCard({
      assetNo: cardForm.assetNo,
      originValue: cardForm.originValue as number,
      residualRate: cardForm.residualRate,
      lifeMonths: cardForm.lifeMonths,
      depMethod: cardForm.depMethod,
      depStartDate: cardForm.depStartDate || undefined,
      leaseFlag: cardForm.leaseFlag,
    })
    ElMessage.success('财务建档成功')
    cardVisible.value = false
    void load()
  } catch {
    // 拦截器已提示
  } finally {
    cardSaving.value = false
  }
}

/* ---------------- 转固 / 处置 / 核销（二次确认） ---------------- */
async function doCapitalize(row: FinanceAssetItem) {
  try {
    await ElMessageBox.confirm(
      `确定将资产「${row.assetNo} ${row.assetName || ''}」转入固计提并开始计提折旧吗？`,
      '转固确认',
      { type: 'warning', confirmButtonText: '确定转固', cancelButtonText: '取消' },
    )
  } catch {
    return
  }
  try {
    await capitalizeFinanceAsset(row.assetNo)
    ElMessage.success('转固成功')
    void load()
  } catch {
    // 拦截器已提示
  }
}

async function doDispose(row: FinanceAssetItem) {
  try {
    await ElMessageBox.confirm(
      `确定将资产「${row.assetNo} ${row.assetName || ''}」转入处置中吗？`,
      '处置确认',
      { type: 'warning', confirmButtonText: '确定处置', cancelButtonText: '取消' },
    )
  } catch {
    return
  }
  try {
    await disposeFinanceAsset(row.assetNo)
    ElMessage.success('已转入处置中')
    void load()
  } catch {
    // 拦截器已提示
  }
}

async function doWriteOff(row: FinanceAssetItem) {
  try {
    await ElMessageBox.confirm(
      `确定核销资产「${row.assetNo} ${row.assetName || ''}」吗？核销为终态，不可恢复。`,
      '核销确认',
      { type: 'error', confirmButtonText: '确定核销', cancelButtonText: '取消' },
    )
  } catch {
    return
  }
  try {
    await writeOffFinanceAsset(row.assetNo)
    ElMessage.success('核销成功')
    void load()
  } catch {
    // 拦截器已提示
  }
}

/* ---------------- 减值 ---------------- */
const impairVisible = ref(false)
const impairSaving = ref(false)
const impairAssetNo = ref('')
const impairAssetName = ref('')
const impairAmount = ref<number | undefined>(undefined)

function openImpair(row: FinanceAssetItem) {
  impairAssetNo.value = row.assetNo
  impairAssetName.value = row.assetName || ''
  impairAmount.value = undefined
  impairVisible.value = true
}

async function submitImpair() {
  if (impairAmount.value === undefined || impairAmount.value <= 0) {
    ElMessage.warning('请输入大于 0 的减值金额')
    return
  }
  impairSaving.value = true
  try {
    await impairFinanceAsset(impairAssetNo.value, { amount: impairAmount.value })
    ElMessage.success('减值计提成功')
    impairVisible.value = false
    void load()
  } catch {
    // 拦截器已提示
  } finally {
    impairSaving.value = false
  }
}

/* ---------------- 批量：转固 / 处置 / 核销（二次确认后执行） ---------------- */
async function doBatchCapitalize() {
  const nos = selCapitalize.value.map((r) => r.assetNo)
  if (!nos.length) return
  try {
    await ElMessageBox.confirm(
      `确定对选中的 ${nos.length} 台资产执行转固并开始计提折旧吗？状态不符的单台将自动跳过。`,
      '批量转固确认',
      { type: 'warning', confirmButtonText: '确定转固', cancelButtonText: '取消' },
    )
  } catch {
    return
  }
  await runBatch('批量转固', () => batchCapitalizeFinanceAssets(nos))
}

async function doBatchDispose() {
  const nos = selDispose.value.map((r) => r.assetNo)
  if (!nos.length) return
  try {
    await ElMessageBox.confirm(
      `确定将选中的 ${nos.length} 台资产转入处置中吗？状态不符的单台将自动跳过。`,
      '批量处置确认',
      { type: 'warning', confirmButtonText: '确定处置', cancelButtonText: '取消' },
    )
  } catch {
    return
  }
  await runBatch('批量处置', () => batchDisposeFinanceAssets(nos))
}

async function doBatchWriteOff() {
  const nos = selWriteOff.value.map((r) => r.assetNo)
  if (!nos.length) return
  try {
    await ElMessageBox.confirm(
      `确定核销选中的 ${nos.length} 台资产吗？核销为终态，不可恢复。状态不符的单台将自动跳过。`,
      '批量核销确认',
      { type: 'error', confirmButtonText: '确定核销', cancelButtonText: '取消' },
    )
  } catch {
    return
  }
  await runBatch('批量核销', () => batchWriteOffFinanceAssets(nos))
}

/* ---------------- 批量：减值（统一金额弹窗） ---------------- */
const batchImpairVisible = ref(false)
const batchImpairSaving = ref(false)
const batchImpairAmount = ref<number | undefined>(undefined)

function openBatchImpair() {
  if (!selImpair.value.length) return
  batchImpairAmount.value = undefined
  batchImpairVisible.value = true
}

async function submitBatchImpair() {
  if (batchImpairAmount.value === undefined || batchImpairAmount.value <= 0) {
    ElMessage.warning('请输入大于 0 的减值金额')
    return
  }
  batchImpairSaving.value = true
  try {
    await runBatch('批量减值', () =>
      batchImpairFinanceAssets({
        assetNos: selImpair.value.map((r) => r.assetNo),
        amount: batchImpairAmount.value as number,
      }),
    )
    batchImpairVisible.value = false
  } finally {
    batchImpairSaving.value = false
  }
}

/* ---------------- 批量：建档（统一参数弹窗，仅作用于勾选中未建档的行） ---------------- */
const batchCardVisible = ref(false)
const batchCardSaving = ref(false)
const batchCardFormRef = ref<FormInstance>()
const batchCardForm = reactive({
  originValue: undefined as number | undefined,
  residualRate: undefined as number | undefined,
  lifeMonths: undefined as number | undefined,
  depMethod: '平均年限法',
  depStartDate: '',
  leaseFlag: false,
})

const batchCardRules = computed<FormRules>(() => ({
  originValue: [{ required: true, message: '请输入资产原值', trigger: 'blur' }],
  residualRate: [
    {
      validator: (_rule, value: number | undefined, callback) => {
        if (value !== undefined && (value < 0 || value >= 1))
          callback(new Error('残值率需在 [0,1) 之间（如 0.05 = 5%）'))
        else callback()
      },
      trigger: 'blur',
    },
  ],
  lifeMonths: [
    {
      validator: (_rule, value: number | undefined, callback) => {
        if (value !== undefined && (!Number.isInteger(value) || value < 1))
          callback(new Error('折旧期需为不小于 1 的整数月'))
        else callback()
      },
      trigger: 'blur',
    },
  ],
  depStartDate: [{ required: true, message: '请选择折旧起始日', trigger: 'change' }],
}))

function openBatchCard() {
  if (!selUnfiled.value.length) return
  Object.assign(batchCardForm, {
    originValue: undefined,
    residualRate: undefined,
    lifeMonths: undefined,
    depMethod: '平均年限法',
    depStartDate: '',
    leaseFlag: false,
  })
  batchCardVisible.value = true
}

async function submitBatchCard() {
  const valid = await batchCardFormRef.value?.validate().then(() => true).catch(() => false)
  if (!valid) return
  batchCardSaving.value = true
  try {
    await runBatch('批量建档', () =>
      batchRegisterFinanceCards({
        assetNos: selUnfiled.value.map((r) => r.assetNo),
        originValue: batchCardForm.originValue as number,
        residualRate: batchCardForm.residualRate,
        lifeMonths: batchCardForm.lifeMonths,
        depMethod: batchCardForm.depMethod,
        depStartDate: batchCardForm.depStartDate || undefined,
        leaseFlag: batchCardForm.leaseFlag,
      }),
    )
    batchCardVisible.value = false
  } finally {
    batchCardSaving.value = false
  }
}

onMounted(() => {
  void load()
})
</script>

<template>
  <div class="page-card">
    <div class="page-title">财务台账</div>

    <!-- 顶部筛选 -->
    <div class="page-toolbar">
      <el-input
        v-model="query.keyword"
        placeholder="资产编号 / 名称"
        clearable
        style="width: 220px"
        @keyup.enter="search"
        @clear="search"
      />
      <el-select
        v-model="query.statusFin"
        placeholder="财务状态"
        clearable
        filterable
        style="width: 140px"
        @change="search"
      >
        <el-option v-for="s in FIN_STATUSES" :key="s" :label="s" :value="s" />
      </el-select>
      <el-select v-model="query.lease" placeholder="租赁标志" clearable style="width: 120px" @change="search">
        <el-option label="仅租赁" value="true" />
        <el-option label="仅自持" value="false" />
      </el-select>
      <el-button type="primary" plain @click="search">查询</el-button>
      <el-button @click="resetQuery">重置</el-button>
      <div style="flex: 1"></div>
      <!-- 批量操作（勾选后出现；可行动数为 0 的按钮禁用） -->
      <template v-if="canWrite && selectedRows.length">
        <el-button
          :disabled="!selUnfiled.length || batchRunning"
          @click="openBatchCard"
        >
          批量建档（{{ selUnfiled.length }}）
        </el-button>
        <el-button
          type="primary"
          plain
          :disabled="!selCapitalize.length || batchRunning"
          @click="doBatchCapitalize"
        >
          批量转固（{{ selCapitalize.length }}）
        </el-button>
        <el-button
          type="warning"
          plain
          :disabled="!selImpair.length || batchRunning"
          @click="openBatchImpair"
        >
          批量减值（{{ selImpair.length }}）
        </el-button>
        <el-button
          type="warning"
          plain
          :disabled="!selDispose.length || batchRunning"
          @click="doBatchDispose"
        >
          批量处置（{{ selDispose.length }}）
        </el-button>
        <el-button
          type="danger"
          plain
          :disabled="!selWriteOff.length || batchRunning"
          @click="doBatchWriteOff"
        >
          批量核销（{{ selWriteOff.length }}）
        </el-button>
      </template>
      <el-button v-if="canWrite" type="primary" @click="openCard">财务建档</el-button>
    </div>

    <!-- 列表 -->
    <el-table
      ref="tableRef"
      v-loading="listLoading"
      :data="records"
      border
      stripe
      row-key="assetNo"
      @selection-change="onSelectionChange"
    >
      <el-table-column type="selection" width="42" fixed="left" />
      <el-table-column prop="assetNo" label="资产编号" min-width="140" fixed="left" />
      <el-table-column prop="assetName" label="资产名称" min-width="140" show-overflow-tooltip>
        <template #default="{ row }">{{ row.assetName || '-' }}</template>
      </el-table-column>
      <el-table-column prop="categoryCode" label="分类" min-width="100" show-overflow-tooltip>
        <template #default="{ row }">{{ row.categoryCode || '-' }}</template>
      </el-table-column>
      <el-table-column label="业务状态" width="100">
        <template #default="{ row }">
          <el-tag :type="bizTagType(row.statusBiz)" size="small">{{ row.statusBiz || '-' }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="财务状态" width="110">
        <template #default="{ row }">
          <el-tag :type="finTagType(row.statusFin)" size="small">{{ row.statusFin || '-' }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="租赁" width="80">
        <template #default="{ row }">
          <el-tag v-if="row.leaseFlag" class="lease-tag" size="small">租赁</el-tag>
          <span v-else>-</span>
        </template>
      </el-table-column>
      <el-table-column label="原值" min-width="110" align="right">
        <template #default="{ row }">{{ fmtMoney(row.originValue) }}</template>
      </el-table-column>
      <el-table-column label="残值率" width="80" align="right">
        <template #default="{ row }">{{ fmtRate(row.residualRate) }}</template>
      </el-table-column>
      <el-table-column label="折旧期(月)" width="95" align="right">
        <template #default="{ row }">{{ row.lifeMonths ?? '-' }}</template>
      </el-table-column>
      <el-table-column label="折旧方法" width="110">
        <template #default="{ row }">{{ row.depMethod || '-' }}</template>
      </el-table-column>
      <el-table-column label="折旧起始日" width="105">
        <template #default="{ row }">{{ row.depStartDate || '-' }}</template>
      </el-table-column>
      <el-table-column label="累计折旧" min-width="110" align="right">
        <template #default="{ row }">{{ fmtMoney(row.accumulatedDep) }}</template>
      </el-table-column>
      <el-table-column label="净值" min-width="110" align="right">
        <template #default="{ row }">{{ fmtMoney(row.netValue) }}</template>
      </el-table-column>
      <el-table-column label="减值准备" min-width="105" align="right">
        <template #default="{ row }">{{ fmtMoney(row.impairment) }}</template>
      </el-table-column>
      <el-table-column label="操作" width="200" fixed="right">
        <template #default="{ row }">
          <template v-if="isUnfiled(row)">
            <el-button v-if="canWrite" link type="primary" @click="openCard()">建档</el-button>
            <span v-else class="action-muted">未建档</span>
          </template>
          <template v-else>
            <el-button v-if="canWrite && canCapitalize(row)" link type="primary" @click="doCapitalize(row)">
              转固
            </el-button>
            <el-button v-if="canWrite && canImpair(row)" link type="warning" @click="openImpair(row)">
              减值
            </el-button>
            <el-button v-if="canWrite && canDispose(row)" link type="warning" @click="doDispose(row)">
              处置
            </el-button>
            <el-button v-if="canWrite && canWriteOff(row)" link type="danger" @click="doWriteOff(row)">
              核销
            </el-button>
            <span v-if="!canWrite || !hasAction(row)" class="action-muted">-</span>
          </template>
        </template>
      </el-table-column>
    </el-table>

    <div class="page-pagination">
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

    <!-- 财务建档 -->
    <el-dialog v-model="cardVisible" title="财务建档" width="560px" destroy-on-close>
      <el-form ref="cardFormRef" :model="cardForm" :rules="cardRules" label-width="100px">
        <el-form-item label="资产" prop="assetNo">
          <el-select
            v-model="cardForm.assetNo"
            filterable
            remote
            clearable
            :remote-method="onAssetSearch"
            :loading="assetOptionsLoading"
            placeholder="输入资产编号 / 名称搜索"
            style="width: 100%"
          >
            <el-option
              v-for="a in assetOptions"
              :key="a.assetNo"
              :label="`${a.assetNo} ${a.name}`"
              :value="a.assetNo"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="原值" prop="originValue">
          <el-input-number
            v-model="cardForm.originValue"
            :min="0"
            :precision="2"
            controls-position="right"
            placeholder="必填"
            style="width: 100%"
          />
        </el-form-item>
        <el-form-item label="残值率(0-1)" prop="residualRate">
          <el-input-number
            v-model="cardForm.residualRate"
            :min="0"
            :max="0.99"
            :precision="2"
            :step="0.01"
            controls-position="right"
            placeholder="如 0.05 = 5%"
            style="width: 100%"
          />
        </el-form-item>
        <el-form-item label="折旧期(月)" prop="lifeMonths">
          <el-input-number
            v-model="cardForm.lifeMonths"
            :min="1"
            :step="12"
            controls-position="right"
            placeholder="如 60"
            style="width: 100%"
          />
        </el-form-item>
        <el-form-item label="折旧方法">
          <el-select v-model="cardForm.depMethod" style="width: 100%">
            <el-option v-for="m in DEP_METHODS" :key="m" :label="m" :value="m" />
          </el-select>
        </el-form-item>
        <el-form-item label="折旧起始日">
          <el-date-picker
            v-model="cardForm.depStartDate"
            type="date"
            value-format="YYYY-MM-DD"
            placeholder="选择日期"
            style="width: 100%"
          />
        </el-form-item>
        <el-form-item label="租赁">
          <el-switch v-model="cardForm.leaseFlag" active-text="租赁" inactive-text="自持" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="cardVisible = false">取消</el-button>
        <el-button type="primary" :loading="cardSaving" @click="submitCard">保存</el-button>
      </template>
    </el-dialog>

    <!-- 减值 -->
    <el-dialog v-model="impairVisible" title="计提减值" width="420px" destroy-on-close>
      <el-form label-width="100px">
        <el-form-item label="资产">
          <span>{{ impairAssetNo }} {{ impairAssetName }}</span>
        </el-form-item>
        <el-form-item label="减值金额" required>
          <el-input-number
            v-model="impairAmount"
            :min="0.01"
            :precision="2"
            controls-position="right"
            placeholder="大于 0"
            style="width: 100%"
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="impairVisible = false">取消</el-button>
        <el-button type="primary" :loading="impairSaving" @click="submitImpair">确认减值</el-button>
      </template>
    </el-dialog>

    <!-- 批量建档（对勾选中未建档的行按统一参数建档） -->
    <el-dialog v-model="batchCardVisible" title="批量财务建档" width="560px" destroy-on-close>
      <div class="batch-hint">
        将对勾选中未建档的 <b>{{ selUnfiled.length }}</b> 台资产按下方统一参数建档（已建档的自动跳过）。
      </div>
      <el-form ref="batchCardFormRef" :model="batchCardForm" :rules="batchCardRules" label-width="100px">
        <el-form-item label="原值" prop="originValue">
          <el-input-number
            v-model="batchCardForm.originValue"
            :min="0"
            :precision="2"
            controls-position="right"
            placeholder="同批设备统一原值"
            style="width: 100%"
          />
        </el-form-item>
        <el-form-item label="残值率(0-1)" prop="residualRate">
          <el-input-number
            v-model="batchCardForm.residualRate"
            :min="0"
            :max="0.99"
            :precision="2"
            :step="0.01"
            controls-position="right"
            placeholder="如 0.05 = 5%"
            style="width: 100%"
          />
        </el-form-item>
        <el-form-item label="折旧期(月)" prop="lifeMonths">
          <el-input-number
            v-model="batchCardForm.lifeMonths"
            :min="1"
            :step="12"
            controls-position="right"
            placeholder="如 60"
            style="width: 100%"
          />
        </el-form-item>
        <el-form-item label="折旧方法">
          <el-select v-model="batchCardForm.depMethod" style="width: 100%">
            <el-option v-for="m in DEP_METHODS" :key="m" :label="m" :value="m" />
          </el-select>
        </el-form-item>
        <el-form-item label="折旧起始日" prop="depStartDate">
          <el-date-picker
            v-model="batchCardForm.depStartDate"
            type="date"
            value-format="YYYY-MM-DD"
            placeholder="选择日期"
            style="width: 100%"
          />
        </el-form-item>
        <el-form-item label="租赁">
          <el-switch v-model="batchCardForm.leaseFlag" active-text="租赁" inactive-text="自持" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="batchCardVisible = false">取消</el-button>
        <el-button type="primary" :loading="batchCardSaving" @click="submitBatchCard">
          对 {{ selUnfiled.length }} 台建档
        </el-button>
      </template>
    </el-dialog>

    <!-- 批量减值（统一金额） -->
    <el-dialog v-model="batchImpairVisible" title="批量计提减值" width="420px" destroy-on-close>
      <el-form label-width="110px">
        <el-form-item label="选中可执行">
          <span>{{ selImpair.length }} 台（状态不符的自动跳过）</span>
        </el-form-item>
        <el-form-item label="统一减值金额" required>
          <el-input-number
            v-model="batchImpairAmount"
            :min="0.01"
            :precision="2"
            controls-position="right"
            placeholder="每台均按此金额计提，大于 0"
            style="width: 100%"
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="batchImpairVisible = false">取消</el-button>
        <el-button type="primary" :loading="batchImpairSaving" @click="submitBatchImpair">
          确认减值 {{ selImpair.length }} 台
        </el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.lease-tag {
  background-color: #f3e8ff;
  border-color: #d8b4fe;
  color: #7e22ce;
}

.action-muted {
  color: var(--el-text-color-placeholder);
  font-size: 12px;
}

.batch-hint {
  margin-bottom: 14px;
  padding: 8px 12px;
  font-size: 13px;
  color: #409eff;
  background: #ecf5ff;
  border-radius: 4px;
}
</style>
