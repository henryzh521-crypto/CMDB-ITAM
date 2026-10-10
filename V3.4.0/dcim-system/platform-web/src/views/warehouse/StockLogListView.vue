<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import type { FormInstance, FormRules } from 'element-plus'
import type { PageResult } from '@/api/types'
import {
  createStockLog,
  createStockLogsBatch,
  fetchMaterialOptions,
  fetchStockLogs,
  fetchWarehouseOptions,
} from '@/api/warehouse'
import type {
  BatchStockLogResult,
  MaterialOption,
  StockLogItem,
  StockLogType,
  WarehouseOption,
} from '@/api/warehouse'

type TagType = 'primary' | 'success' | 'warning' | 'info' | 'danger'

/* ---------------- 类型展示 ---------------- */
const LOG_TYPE_OPTIONS: { value: StockLogType; label: string }[] = [
  { value: 'IN', label: '采购入库' },
  { value: 'OUT', label: '领用出库' },
  { value: 'ADJUST', label: '调整' },
  { value: 'TRANSFER', label: '转仓' },
]

const LOG_TYPE_MAP: Record<StockLogType, string> = {
  IN: '采购入库',
  OUT: '领用出库',
  ADJUST: '调整',
  TRANSFER: '转仓',
}

const LOG_TYPE_TAG: Record<StockLogType, TagType> = {
  IN: 'success',
  OUT: 'warning',
  ADJUST: 'info',
  TRANSFER: 'primary',
}

/* ---------------- 仓库下拉（分仓出入库 / 转仓） ---------------- */
const warehouseOpts = ref<WarehouseOption[]>([])

async function loadWarehouseOpts() {
  try {
    // 含停用仓，便于历史流水回溯与转仓
    warehouseOpts.value = await fetchWarehouseOptions(true)
  } catch {
    warehouseOpts.value = []
  }
}

function warehouseLabel(code?: string): string {
  if (!code) return ''
  const hit = warehouseOpts.value.find((w) => w.warehouseCode === code)
  return hit ? `${hit.name}` : code
}

function logTypeLabel(t?: string): string {
  return t ? LOG_TYPE_MAP[t as StockLogType] || t : '-'
}

function todayStr(): string {
  const d = new Date()
  const m = String(d.getMonth() + 1).padStart(2, '0')
  const day = String(d.getDate()).padStart(2, '0')
  return `${d.getFullYear()}-${m}-${day}`
}

/* ---------------- 物料远程搜索下拉 ---------------- */
let materialSearchTimer: number | undefined

async function loadMaterialOptions(
  keyword: string,
  onLoaded: (list: MaterialOption[]) => void,
) {
  try {
    const list = await fetchMaterialOptions({ keyword: keyword || undefined, size: 50 })
    onLoaded(list || [])
  } catch {
    onLoaded([])
  }
}

function debouncedMaterialSearch(keyword: string, onLoaded: (list: MaterialOption[]) => void) {
  window.clearTimeout(materialSearchTimer)
  materialSearchTimer = window.setTimeout(() => void loadMaterialOptions(keyword, onLoaded), 300)
}

/* ---------------- 列表查询 ---------------- */
const query = reactive({
  page: 1,
  size: 20,
  logType: '' as StockLogType | '',
  materialCode: '',
  warehouseCode: '',
  keyword: '',
})
const bizDateRange = ref<[string, string] | null>(null)
const total = ref(0)
const records = ref<StockLogItem[]>([])
const listLoading = ref(false)

/** 筛选区物料编码下拉 */
const filterMaterialOptions = ref<MaterialOption[]>([])
const filterMaterialLoading = ref(false)

async function loadFilterMaterials(keyword: string) {
  filterMaterialLoading.value = true
  await loadMaterialOptions(keyword, (list) => {
    filterMaterialOptions.value = list
    filterMaterialLoading.value = false
  })
}

function onFilterMaterialSearch(keyword: string) {
  debouncedMaterialSearch(keyword, (list) => {
    filterMaterialOptions.value = list
    filterMaterialLoading.value = false
  })
}

async function load() {
  listLoading.value = true
  try {
    const res: PageResult<StockLogItem> = await fetchStockLogs({
      page: query.page,
      size: query.size,
      logType: query.logType || undefined,
      materialCode: query.materialCode || undefined,
      warehouseCode: query.warehouseCode || undefined,
      bizDateFrom: bizDateRange.value?.[0],
      bizDateTo: bizDateRange.value?.[1],
      keyword: query.keyword || undefined,
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
  query.logType = ''
  query.materialCode = ''
  query.warehouseCode = ''
  query.keyword = ''
  bizDateRange.value = null
  search()
}

/* ---------------- 单条出入库 ---------------- */
const singleVisible = ref(false)
const singleSaving = ref(false)
const singleFormRef = ref<FormInstance>()
const singleForm = reactive({
  logType: 'IN' as StockLogType,
  materialCode: '',
  qty: undefined as number | undefined,
  bizDate: '',
  department: '',
  refWo: '',
  remark: '',
  /** 发生仓（TRANSFER 为转入仓）；空 = 物料默认仓 */
  warehouseCode: '',
  /** 仅 TRANSFER 的转出仓 */
  fromWarehouseCode: '',
})
const singleMaterialOptions = ref<MaterialOption[]>([])
const singleMaterialLoading = ref(false)

const selectedSingleMaterial = computed(() =>
  singleMaterialOptions.value.find((m) => m.materialCode === singleForm.materialCode),
)

/** 该物料在所选仓的现存量（未选仓则退回全仓合计） */
const selectedSingleQty = computed<number | undefined>(() => {
  const m = selectedSingleMaterial.value
  if (!m) return undefined
  if (singleForm.warehouseCode && m.warehouseStocks) {
    const v = m.warehouseStocks[singleForm.warehouseCode]
    if (v !== undefined) return v
    return 0
  }
  return m.stock
})

const singleRules: FormRules = {
  logType: [{ required: true, message: '请选择类型', trigger: 'change' }],
  materialCode: [{ required: true, message: '请选择物料', trigger: 'change' }],
  qty: [{ required: true, message: '请输入数量', trigger: 'blur' }],
  fromWarehouseCode: [
    {
      required: true,
      message: '转仓必须选择转出仓库',
      trigger: 'change',
      validator: (_r, _v, cb) =>
        singleForm.logType === 'TRANSFER' && !singleForm.fromWarehouseCode
          ? cb(new Error('转仓必须选择转出仓库'))
          : cb(),
    },
  ],
  warehouseCode: [
    {
      required: true,
      message: '转仓必须选择转入仓库',
      trigger: 'change',
      validator: (_r, _v, cb) =>
        singleForm.logType === 'TRANSFER' && !singleForm.warehouseCode
          ? cb(new Error('转仓必须选择转入仓库'))
          : cb(),
    },
  ],
}

function onSingleMaterialSearch(keyword: string) {
  debouncedMaterialSearch(keyword, (list) => {
    singleMaterialOptions.value = list
    singleMaterialLoading.value = false
  })
}

function onSingleMaterialFocus() {
  void loadMaterialOptions('', (list) => {
    singleMaterialOptions.value = list
    singleMaterialLoading.value = false
  })
}

function openSingle() {
  Object.assign(singleForm, {
    logType: 'IN',
    materialCode: '',
    qty: undefined,
    bizDate: todayStr(),
    department: '',
    refWo: '',
    remark: '',
    warehouseCode: '',
    fromWarehouseCode: '',
  })
  singleMaterialOptions.value = []
  singleVisible.value = true
}

function onSingleTypeChange() {
  singleForm.qty = undefined
}

/** 选中物料后把发生仓默认为该物料的默认货位仓 */
function onSingleMaterialPicked() {
  const m = selectedSingleMaterial.value
  singleForm.warehouseCode = m?.warehouseCode || ''
  singleForm.fromWarehouseCode = ''
}

async function submitSingle() {
  const valid = await singleFormRef.value?.validate().then(() => true).catch(() => false)
  if (!valid) return
  if (singleForm.logType !== 'ADJUST' && (singleForm.qty ?? 0) <= 0) {
    ElMessage.warning('数量必须大于 0')
    return
  }
  if (
    singleForm.logType === 'TRANSFER' &&
    (!singleForm.fromWarehouseCode || !singleForm.warehouseCode)
  ) {
    ElMessage.warning('转仓必须选择转出与转入仓库')
    return
  }
  if (
    singleForm.logType === 'TRANSFER' &&
    singleForm.fromWarehouseCode === singleForm.warehouseCode
  ) {
    ElMessage.warning('转出仓库与转入仓库不能相同')
    return
  }
  singleSaving.value = true
  try {
    await createStockLog({
      logType: singleForm.logType,
      materialCode: singleForm.materialCode,
      qty: singleForm.qty as number,
      bizDate: singleForm.bizDate || undefined,
      department: singleForm.department || undefined,
      refWo: singleForm.refWo || undefined,
      remark: singleForm.remark || undefined,
      warehouseCode: singleForm.warehouseCode || undefined,
      fromWarehouseCode:
        singleForm.logType === 'TRANSFER' ? singleForm.fromWarehouseCode : undefined,
    })
    ElMessage.success('出入库成功')
    singleVisible.value = false
    void load()
  } catch {
    // 拦截器已提示（OUT 库存不足 409 中文消息）
  } finally {
    singleSaving.value = false
  }
}

/* ---------------- 批量出入库 ---------------- */
interface BatchRow {
  logType: StockLogType
  materialCode: string
  qty: number | undefined
  warehouseCode: string
  fromWarehouseCode: string
  options: MaterialOption[]
  optionsLoading: boolean
}

const batchVisible = ref(false)
const batchSaving = ref(false)
const batchRows = ref<BatchRow[]>([])
const batchResult = ref<BatchStockLogResult | null>(null)

function newBatchRow(): BatchRow {
  return {
    logType: 'IN',
    materialCode: '',
    qty: undefined,
    warehouseCode: '',
    fromWarehouseCode: '',
    options: [],
    optionsLoading: false,
  }
}

function openBatch() {
  batchRows.value = [newBatchRow()]
  batchResult.value = null
  batchVisible.value = true
}

function addBatchRow() {
  batchRows.value.push(newBatchRow())
}

function removeBatchRow(index: number) {
  batchRows.value.splice(index, 1)
}

function onBatchMaterialSearch(row: BatchRow, keyword: string) {
  row.optionsLoading = true
  debouncedMaterialSearch(keyword, (list) => {
    row.options = list
    row.optionsLoading = false
  })
}

function onBatchMaterialFocus(row: BatchRow) {
  row.optionsLoading = true
  void loadMaterialOptions('', (list) => {
    row.options = list
    row.optionsLoading = false
  })
}

function onBatchRowTypeChange(row: BatchRow) {
  row.qty = undefined
  row.fromWarehouseCode = ''
}

function onBatchMaterialPicked(row: BatchRow) {
  const hit = row.options.find((m) => m.materialCode === row.materialCode)
  row.warehouseCode = hit?.warehouseCode || ''
}

async function submitBatch() {
  if (!batchRows.value.length) {
    ElMessage.warning('请至少添加一行')
    return
  }
  for (let i = 0; i < batchRows.value.length; i++) {
    const row = batchRows.value[i]
    if (!row.materialCode) {
      ElMessage.warning(`第 ${i + 1} 行请选择物料`)
      return
    }
    if (row.qty === undefined || row.qty === null) {
      ElMessage.warning(`第 ${i + 1} 行请输入数量`)
      return
    }
    if (row.logType !== 'ADJUST' && row.qty <= 0) {
      ElMessage.warning(`第 ${i + 1} 行数量必须大于 0`)
      return
    }
    if (row.logType === 'TRANSFER') {
      if (!row.fromWarehouseCode || !row.warehouseCode) {
        ElMessage.warning(`第 ${i + 1} 行转仓必须选择转出与转入仓库`)
        return
      }
      if (row.fromWarehouseCode === row.warehouseCode) {
        ElMessage.warning(`第 ${i + 1} 行转出仓库与转入仓库不能相同`)
        return
      }
    }
  }
  batchSaving.value = true
  try {
    batchResult.value = await createStockLogsBatch({
      items: batchRows.value.map((r) => ({
        logType: r.logType,
        materialCode: r.materialCode,
        qty: r.qty as number,
        bizDate: todayStr(),
        warehouseCode: r.warehouseCode || undefined,
        fromWarehouseCode: r.logType === 'TRANSFER' ? r.fromWarehouseCode : undefined,
      })),
    })
    ElMessage.success('批量出入库成功')
    void load()
  } catch {
    // 拦截器已提示（任一行失败整单回滚，409/400 带行号中文）
  } finally {
    batchSaving.value = false
  }
}

onMounted(() => {
  void load()
  void loadFilterMaterials('')
  void loadWarehouseOpts()
})
</script>

<template>
  <div class="page-card">
    <div class="page-title">出入库管理</div>

    <!-- 顶部筛选 -->
    <div class="page-toolbar">
      <el-select
        v-model="query.logType"
        placeholder="类型"
        clearable
        style="width: 130px"
        @change="search"
      >
        <el-option v-for="o in LOG_TYPE_OPTIONS" :key="o.value" :label="o.label" :value="o.value" />
      </el-select>
      <el-select
        v-model="query.materialCode"
        placeholder="物料编码"
        clearable
        filterable
        remote
        :remote-method="onFilterMaterialSearch"
        :loading="filterMaterialLoading"
        style="width: 190px"
        @change="search"
        @focus="loadFilterMaterials('')"
      >
        <el-option
          v-for="m in filterMaterialOptions"
          :key="m.materialCode"
          :label="`${m.name}（${m.materialCode}）`"
          :value="m.materialCode"
        />
      </el-select>
      <el-select
        v-model="query.warehouseCode"
        placeholder="仓库"
        clearable
        filterable
        style="width: 170px"
        @change="search"
      >
        <el-option
          v-for="w in warehouseOpts"
          :key="w.warehouseCode"
          :label="`${w.warehouseCode} ${w.name}`"
          :value="w.warehouseCode"
        />
      </el-select>
      <el-date-picker
        v-model="bizDateRange"
        type="daterange"
        value-format="YYYY-MM-DD"
        range-separator="至"
        start-placeholder="业务日期起"
        end-placeholder="业务日期止"
        style="width: 260px"
        @change="search"
      />
      <el-input
        v-model="query.keyword"
        placeholder="流水号 / 经办人 / 关联工单"
        clearable
        style="width: 200px"
        @keyup.enter="search"
        @clear="search"
      />
      <el-button type="primary" plain @click="search">查询</el-button>
      <el-button @click="resetQuery">重置</el-button>
      <div style="flex: 1"></div>
      <el-button type="primary" @click="openSingle">单条出入库</el-button>
      <el-button type="success" plain @click="openBatch">批量出入库</el-button>
    </div>

    <!-- 列表 -->
    <el-table v-loading="listLoading" :data="records" border stripe>
      <el-table-column prop="logNo" label="流水号" min-width="160" fixed="left" show-overflow-tooltip />
      <el-table-column label="类型" width="100">
        <template #default="{ row }">
          <el-tag :type="LOG_TYPE_TAG[row.logType as StockLogType] || 'info'" size="small">
            {{ logTypeLabel(row.logType) }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="materialCode" label="物料编码" min-width="130" show-overflow-tooltip />
      <el-table-column prop="qty" label="数量" width="90" align="right" />
      <el-table-column label="仓库" min-width="150" show-overflow-tooltip>
        <template #default="{ row }">
          <template v-if="row.logType === 'TRANSFER'">
            {{ row.fromWarehouseCode }} {{ warehouseLabel(row.fromWarehouseCode) }}
            → {{ row.warehouseCode }} {{ warehouseLabel(row.warehouseCode) }}
          </template>
          <template v-else-if="row.warehouseCode">
            {{ row.warehouseCode }} {{ warehouseLabel(row.warehouseCode) }}
          </template>
          <template v-else>-</template>
        </template>
      </el-table-column>
      <el-table-column prop="bizDate" label="业务日期" width="110">
        <template #default="{ row }">{{ row.bizDate || '-' }}</template>
      </el-table-column>
      <el-table-column prop="operator" label="经办人" width="100">
        <template #default="{ row }">{{ row.operator || '-' }}</template>
      </el-table-column>
      <el-table-column prop="department" label="领用部门" min-width="120" show-overflow-tooltip>
        <template #default="{ row }">{{ row.department || '-' }}</template>
      </el-table-column>
      <el-table-column prop="refWo" label="关联工单" min-width="120" show-overflow-tooltip>
        <template #default="{ row }">{{ row.refWo || '-' }}</template>
      </el-table-column>
      <el-table-column prop="remark" label="用途" min-width="140" show-overflow-tooltip>
        <template #default="{ row }">{{ row.remark || '-' }}</template>
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

    <!-- 单条出入库 -->
    <el-dialog v-model="singleVisible" title="单条出入库" width="520px" destroy-on-close>
      <el-form ref="singleFormRef" :model="singleForm" :rules="singleRules" label-width="92px">
        <el-form-item label="类型" prop="logType">
          <el-radio-group v-model="singleForm.logType" @change="onSingleTypeChange">
            <el-radio v-for="o in LOG_TYPE_OPTIONS" :key="o.value" :value="o.value">
              {{ o.label }}
            </el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="物料" prop="materialCode">
          <el-select
            v-model="singleForm.materialCode"
            filterable
            remote
            clearable
            :remote-method="onSingleMaterialSearch"
            :loading="singleMaterialLoading"
            placeholder="输入编码 / 名称搜索物料"
            style="width: 100%"
            @focus="onSingleMaterialFocus"
            @change="onSingleMaterialPicked"
          >
            <el-option
              v-for="m in singleMaterialOptions"
              :key="m.materialCode"
              :label="`${m.name}（${m.materialCode}）`"
              :value="m.materialCode"
            >
              <span>{{ m.name }}（{{ m.materialCode }}）</span>
              <span class="option-stock">库存{{ m.stock }}{{ m.unit }}</span>
            </el-option>
          </el-select>
        </el-form-item>
        <el-form-item
          v-if="singleForm.logType === 'TRANSFER'"
          label="转出仓库"
          prop="fromWarehouseCode"
        >
          <el-select
            v-model="singleForm.fromWarehouseCode"
            clearable
            filterable
            placeholder="必选"
            style="width: 100%"
          >
            <el-option
              v-for="w in warehouseOpts"
              :key="w.warehouseCode"
              :label="`${w.warehouseCode} ${w.name}`"
              :value="w.warehouseCode"
            />
          </el-select>
        </el-form-item>
        <el-form-item
          :label="singleForm.logType === 'TRANSFER' ? '转入仓库' : '发生仓库'"
          :prop="singleForm.logType === 'TRANSFER' ? 'warehouseCode' : undefined"
        >
          <el-select
            v-model="singleForm.warehouseCode"
            clearable
            filterable
            :placeholder="singleForm.logType === 'TRANSFER' ? '必选' : '留空 = 物料默认仓'"
            style="width: 100%"
          >
            <el-option
              v-for="w in warehouseOpts"
              :key="w.warehouseCode"
              :label="`${w.warehouseCode} ${w.name}`"
              :value="w.warehouseCode"
            />
          </el-select>
        </el-form-item>
        <el-form-item v-if="selectedSingleMaterial" label="当前库存">
          <span class="stock-hint">
            {{ selectedSingleQty ?? '-' }}{{ selectedSingleMaterial.unit }}
            <span v-if="singleForm.warehouseCode" class="stock-scope">（该仓）</span>
            <span v-else class="stock-scope">（全仓合计）</span>
          </span>
        </el-form-item>
        <el-form-item label="数量" prop="qty">
          <el-input-number
            v-model="singleForm.qty"
            :min="singleForm.logType === 'ADJUST' ? 0 : 1"
            controls-position="right"
            style="width: 200px"
          />
          <span v-if="singleForm.logType === 'ADJUST'" class="adjust-tip">为校准后的目标库存值</span>
        </el-form-item>
        <el-form-item label="业务日期">
          <el-date-picker
            v-model="singleForm.bizDate"
            type="date"
            value-format="YYYY-MM-DD"
            placeholder="默认今天"
            style="width: 100%"
          />
        </el-form-item>
        <el-form-item label="领用部门">
          <el-input v-model="singleForm.department" />
        </el-form-item>
        <el-form-item label="关联工单">
          <el-input v-model="singleForm.refWo" />
        </el-form-item>
        <el-form-item label="用途 / 备注">
          <el-input v-model="singleForm.remark" type="textarea" :rows="2" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="singleVisible = false">取消</el-button>
        <el-button type="primary" :loading="singleSaving" @click="submitSingle">提交</el-button>
      </template>
    </el-dialog>

    <!-- 批量出入库 -->
    <el-dialog v-model="batchVisible" title="批量出入库" width="880px" destroy-on-close>
      <div class="batch-rows">
        <div v-for="(row, idx) in batchRows" :key="idx" class="batch-row">
          <span class="batch-row-no">{{ idx + 1 }}</span>
          <el-select
            v-model="row.logType"
            placeholder="类型"
            style="width: 130px"
            @change="onBatchRowTypeChange(row)"
          >
            <el-option v-for="o in LOG_TYPE_OPTIONS" :key="o.value" :label="o.label" :value="o.value" />
          </el-select>
          <el-select
            v-model="row.materialCode"
            filterable
            remote
            clearable
            :remote-method="(q: string) => onBatchMaterialSearch(row, q)"
            :loading="row.optionsLoading"
            placeholder="搜索物料"
            style="flex: 1"
            @focus="onBatchMaterialFocus(row)"
            @change="onBatchMaterialPicked(row)"
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
          <el-select
            v-if="row.logType === 'TRANSFER'"
            v-model="row.fromWarehouseCode"
            clearable
            filterable
            placeholder="转出仓"
            style="width: 150px"
          >
            <el-option
              v-for="w in warehouseOpts"
              :key="w.warehouseCode"
              :label="`${w.warehouseCode} ${w.name}`"
              :value="w.warehouseCode"
            />
          </el-select>
          <el-select
            v-model="row.warehouseCode"
            clearable
            filterable
            :placeholder="row.logType === 'TRANSFER' ? '转入仓' : '发生仓（空=默认仓）'"
            style="width: 170px"
          >
            <el-option
              v-for="w in warehouseOpts"
              :key="w.warehouseCode"
              :label="`${w.warehouseCode} ${w.name}`"
              :value="w.warehouseCode"
            />
          </el-select>
          <el-input-number
            v-model="row.qty"
            :min="row.logType === 'ADJUST' ? 0 : 1"
            :placeholder="row.logType === 'ADJUST' ? '目标库存' : '数量'"
            controls-position="right"
            style="width: 150px"
          />
          <el-button
            type="danger"
            link
            :disabled="batchRows.length <= 1"
            @click="removeBatchRow(idx)"
          >
            删除行
          </el-button>
        </div>
        <el-button class="batch-add" plain @click="addBatchRow">+ 添加行</el-button>
      </div>

      <template v-if="batchResult">
        <el-alert
          class="batch-result-alert"
          type="success"
          :closable="false"
          :title="`批量出入库完成，共生成 ${batchResult.count} 条流水`"
        />
        <el-table :data="batchResult.logs" border size="small">
          <el-table-column prop="logNo" label="流水号" min-width="170" show-overflow-tooltip />
          <el-table-column prop="materialCode" label="物料编码" min-width="130" />
          <el-table-column label="类型" width="100">
            <template #default="{ row }">{{ logTypeLabel(row.logType) }}</template>
          </el-table-column>
          <el-table-column prop="qty" label="数量" width="90" align="right" />
        </el-table>
      </template>

      <template #footer>
        <el-button @click="batchVisible = false">{{ batchResult ? '关闭' : '取消' }}</el-button>
        <el-button type="primary" :loading="batchSaving" @click="submitBatch">提交</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.option-stock {
  float: right;
  font-size: 12px;
  color: #8492a6;
}

.stock-hint {
  color: var(--el-text-color-primary);
  font-weight: 600;
}

.adjust-tip {
  margin-left: 10px;
  font-size: 12px;
  color: #e6a23c;
}

.stock-scope {
  margin-left: 4px;
  font-size: 12px;
  font-weight: 400;
  color: var(--el-text-color-secondary);
}

.batch-rows {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.batch-row {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 8px;
}

.batch-row-no {
  width: 22px;
  text-align: right;
  color: var(--el-text-color-secondary);
}

.batch-add {
  align-self: flex-start;
}

.batch-result-alert {
  margin: 14px 0 8px;
}
</style>
